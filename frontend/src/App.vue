<script setup>
import { ref, onMounted, nextTick, computed, watch } from 'vue'
import axios from 'axios'
import * as echarts from 'echarts' 

// --- 0. 基础配置 ---
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8081',
  timeout: 5000
})

// --- 1. 全局状态 ---
const currentTab = ref('dashboard') 
const loading = ref(false)          
const notification = ref({ show: false, message: '', type: 'success' }) 

const currentUser = ref('李药师') 
const userOptions = ['王护士', '李药师', '张主任']

// --- 2. 核心数据 ---
const drugList = ref([])   
const recordList = ref([]) 
const localFixMap = ref([]) 

//  [批量退药] 用来存储每个药品想退的数量
const returnInputs = ref({})

// --- 3. 护士工作台数据 ---
const patientIdInput = ref('P001')
const todoPrescriptions = ref([]) 
const donePrescriptions = ref([])
const nurseScanCode = ref('')

const patientDb = { 'P001': '张三', 'P002': '李四', 'P003': '王五', 'P008': '钱八' }

// 待发药
const uniqueTodos = computed(() => {
  const map = new Map()
  todoPrescriptions.value.forEach(p => {
    if (!map.has(p.drugName)) {
      map.set(p.drugName, { ...p, count: 1 })
    } else {
      map.get(p.drugName).count++
    }
  })
  return Array.from(map.values())
})

// 已发药
const uniqueDones = computed(() => {
  const map = new Map()
  donePrescriptions.value.forEach(p => {
    if (!map.has(p.drugName)) {
      map.set(p.drugName, { ...p, count: 1 })
    } else {
      map.get(p.drugName).count++
    }
  })
  return Array.from(map.values())
})

// --- 4. 药房管理数据 ---
// ✅ 改动1：inboundForm 新增 expireDate 字段，用于入库时记录有效期
const inboundForm = ref({ drugName: '', traceCode: '', batchNumber: '', quantity: 1, expireDate: '' })
const isCaseMode = ref(false) 
const caseRatio = ref(20)     
const pharmacyScanCode = ref('') 
const outboundReason = ref('🔴 过期/破损报废')
const outboundQty = ref(1)

// --- 5. 图表逻辑 ---
let pieChartInstance = null
let barChartInstance = null

const totalStock = computed(() => drugList.value.reduce((acc, cur) => acc + cur.quantity, 0))
const lowStockCount = computed(() => drugList.value.filter(d => d.quantity < 50).length)
const totalInbound = computed(() => drugList.value.length)

// ✅ 改动2：新增近效期计算属性，90天内到期且库存>0的药品列表
const nearExpiryList = computed(() => {
  const today = new Date()
  return drugList.value.filter(d => {
    if (!d.expireDate || d.quantity <= 0) return false
    const diff = (new Date(d.expireDate) - today) / (1000 * 60 * 60 * 24)
    return diff >= 0 && diff <= 90
  }).sort((a, b) => new Date(a.expireDate) - new Date(b.expireDate))
})

const initCharts = async () => {
  if (currentTab.value !== 'dashboard') return
  await nextTick() 
  const pieDom = document.getElementById('stockPie')
  if (pieDom) {
    if (pieChartInstance) pieChartInstance.dispose()
    pieChartInstance = echarts.init(pieDom)
    pieChartInstance.setOption({
      color: ['#2454d6', '#22a6b3', '#12b76a', '#f79009', '#e5484d', '#7c3aed'],
      title: { text: '库存资产分布', left: 'center', top: 8, textStyle: { color: '#182230', fontSize: 16, fontWeight: 800 } },
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, type: 'scroll', textStyle: { color: '#667085' } },
      series: [{
        name: '库存量', type: 'pie', radius: ['42%', '68%'], center: ['50%', '50%'],
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: { color: '#344054', fontWeight: 700 },
        data: [...drugList.value].sort((a,b)=>b.quantity-a.quantity).slice(0, 6).map(d => ({ value: d.quantity, name: d.drugName }))
      }]
    })
  }
  const barDom = document.getElementById('trendBar')
  if (barDom) {
    if (barChartInstance) barChartInstance.dispose()
    barChartInstance = echarts.init(barDom)
    const lowStockDrugs = drugList.value.filter(d => d.quantity < 200).sort((a,b) => a.quantity - b.quantity).slice(0, 5)
    barChartInstance.setOption({
      title: { text: '急需补货药品', left: 'center', top: 8, textStyle: { color: '#182230', fontSize: 16, fontWeight: 800 } },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: lowStockDrugs.map(d => d.drugName), axisLabel: { interval: 0, rotate: 18, color: '#667085' }, axisLine: { lineStyle: { color: '#d0d5dd' } } },
      yAxis: { type: 'value', name: '剩余库存', nameTextStyle: { color: '#667085' }, axisLabel: { color: '#667085' }, splitLine: { lineStyle: { color: '#edf1f5' } } },
      grid: { left: 52, right: 24, top: 72, bottom: 88 },
      series: [{
        data: lowStockDrugs.map(d => d.quantity), type: 'bar', barWidth: '40%',
        label: { show: true, position: 'top', color: '#d92d20', fontWeight: 800 },
        itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: '#f97373' }, { offset: 1, color: '#d92d20' }]), borderRadius: [6, 6, 0, 0] }
      }]
    })
  }
}

watch([drugList, recordList, currentTab], () => { if(currentTab.value === 'dashboard') initCharts() })

const showNotification = (msg, type = 'success') => {
  notification.value = { show: true, message: msg, type }
  setTimeout(() => notification.value.show = false, 3000)
}

const getLogName = (r) => {
  if (r.dispenseTime) {
    const serverTime = r.dispenseTime.split(' ')[1] 
    const fix = localFixMap.value.find(f => f.drugName === r.drugName && Math.abs(timeToSeconds(serverTime) - timeToSeconds(f.timeStr)) < 10)
    if (fix) return fix.correctName
  }
  return r.patientName + (r.patientId ? ' ' + r.patientId : '')
}

const getLogClass = (name) => {
  if (!name) return 'blue'
  if (name.includes('退药')) return 'red'      
  if (name.includes('药房') || name.includes('质控')) return 'orange'   
  if (name.includes('👤') || name.includes('(') || name.includes('张三') || name.includes('李四')) return 'green' 
  return 'blue'
}

const timeToSeconds = (hms) => {
  if(!hms) return 0
  const [h, m, s] = hms.split(':').map(Number)
  return h * 3600 + m * 60 + s
}

//近效期相关函数，供库存明细表质控列和看板预警列表使用
const isNearExpiry = (expireDateStr) => {
  if (!expireDateStr) return false
  const diff = (new Date(expireDateStr) - new Date()) / (1000 * 60 * 60 * 24)
  return diff >= 0 && diff <= 90
}

// 计算距今剩余天数（整数），供近效期预警表格"剩余天数"列显示
const getDaysLeft = (expireDateStr) => {
  if (!expireDateStr) return '--'
  return Math.ceil((new Date(expireDateStr) - new Date()) / (1000 * 60 * 60 * 24))
}

// 根据剩余天数返回样式类名：30天内红色紧急、30-60天橙色警告、60-90天绿色提示
const getDaysClass = (expireDateStr) => {
  const days = getDaysLeft(expireDateStr)
  if (typeof days !== 'number') return ''
  if (days <= 30) return 'days-urgent'
  if (days <= 60) return 'days-warn'
  return 'days-ok'
}

const refreshData = async () => {
  loading.value = true
  try {
    const [resList, resRecords] = await Promise.all([api.get('/list'), api.get('/records')])
    drugList.value = resList.data
    recordList.value = resRecords.data
    if(currentTab.value === 'dashboard') initCharts()
  } catch (error) { showNotification('数据同步失败', 'error') } 
  finally { loading.value = false }
}

// --- 业务逻辑 ---

const checkPatient = async () => {
  if (!patientIdInput.value) return showNotification('请输入患者ID', 'error')
  loading.value = true
  try {
    const [resTodo, resDone] = await Promise.all([
      api.get(`/prescriptions?patientId=${patientIdInput.value}&status=待发药`),
      api.get(`/prescriptions?patientId=${patientIdInput.value}&status=已发药`)
    ])
    todoPrescriptions.value = resTodo.data
    donePrescriptions.value = resDone.data
    // 清空退药输入框
    returnInputs.value = {}
    if (resTodo.data.length === 0 && resDone.data.length === 0) showNotification('该患者无记录', 'info')
  } catch (e) { showNotification('查询失败', 'error') } 
  finally { loading.value = false }
}

const dispenseByNurse = async () => {
  const code = nurseScanCode.value.trim() 
  if (!code) return showNotification('请扫码', 'error')
  
  if (todoPrescriptions.value.length === 0) {
    alert("⚠️ 请先点击【查询】按钮！")
    return
  }
  const drugInStock = drugList.value.find(d => d.traceCode === code)
  if (!drugInStock) {
    alert(`❌ 无效追溯码！\n库存中未找到此药盒。`)
    return
  }
  const prescription = todoPrescriptions.value.find(p => p.drugName === drugInStock.drugName)
  if (!prescription) {
    const needed = todoPrescriptions.value.map(p => p.drugName).join('、')
    alert(`⛔ 发药错误拦截！\n\n❌ 扫码药品：${drugInStock.drugName}\n✅ 患者医嘱：${needed || '无'}\n\n药名不一致，严禁发药！`)
    return
  }

  const pName = patientDb[patientIdInput.value] || '未知'
  const bindInfo = `👤 ${pName}(${patientIdInput.value}) [${currentUser.value}]`

  try {
    const res = await api.post('/dispense', {
      traceCode: code,
      patientId: bindInfo, 
      prescriptionId: prescription.id.toString(),
      quantity: "1"
    })
    
    const resStr = typeof res.data === 'object' ? JSON.stringify(res.data) : String(res.data)
    if (res.status === 200 || resStr.includes("成功")) {
      showNotification(`✅ 发药成功：${drugInStock.drugName}`, 'success')
      
      const now = new Date()
      const timeStr = `${now.getHours()}:${now.getMinutes()}:${now.getSeconds()}`.replace(/\b(\d)\b/g, '0$1')
      localFixMap.value.push({ drugName: drugInStock.drugName, correctName: bindInfo, timeStr: timeStr })

      nurseScanCode.value = ''
      await checkPatient()
      refreshData()
    } else { alert(`系统拒绝：${resStr}`) }
  } catch (e) { alert('系统错误') }
}

// 🔥 [批量退药核心逻辑]
const returnByNurse = async (group) => {
  // 1. 获取要退的数量（如果输入框没填，默认退1个）
  const qtyToReturn = parseInt(returnInputs.value[group.drugName]) || 1
  
  if (qtyToReturn <= 0 || qtyToReturn > group.count) {
    alert(`⚠️ 数量错误！最多只能退 ${group.count} 盒`)
    return
  }

  if (!confirm(`⚠️ 确认退回 ${qtyToReturn} 盒 【${group.drugName}】?`)) return
  
  const pName = patientDb[patientIdInput.value] || '未知'
  const returnLogName = `【退药】${pName} [${currentUser.value}]`
  
  // 2. 从已发列表里找出 N 个该药品（取前 N 个）
  const targets = donePrescriptions.value
    .filter(p => p.drugName === group.drugName)
    .slice(0, qtyToReturn)

  loading.value = true
  
  try {
    // 3. 循环发起退药请求（模拟批量）
    // 使用 Promise.all 并行发送，速度快
    await Promise.all(targets.map(p => {
      // 记录修正日志
      const now = new Date()
      const timeStr = `${now.getHours()}:${now.getMinutes()}:${now.getSeconds()}`.replace(/\b(\d)\b/g, '0$1')
      localFixMap.value.push({ drugName: p.drugName, correctName: returnLogName, timeStr: timeStr })

      return api.post('/return', {
        prescriptionId: p.id.toString(),
        traceCode: p.traceCodeDispensed,
        patientId: returnLogName, 
        drugName: p.drugName
      })
    }))

    showNotification(`✅ 成功退回 ${qtyToReturn} 盒`, 'success')
    // 重置输入框
    returnInputs.value[group.drugName] = 1
    await checkPatient()
    refreshData()
  } catch (e) { 
    showNotification('退药失败', 'error') 
  } finally {
    loading.value = false
  }
}/*  */

const quickAddDrug = async () => {
  if (!inboundForm.value.drugName || !inboundForm.value.traceCode) {
      return showNotification('信息不全，请输入药名和追溯码', 'error')
  }
  
  const batchWithUser = `${inboundForm.value.batchNumber} (入:${currentUser.value})`
  
  try {
    // 1. 发送请求给后端
    const res = await api.post('/add', { 
        ...inboundForm.value, 
        batchNumber: batchWithUser, 
        quantity: 1 // 前端也主动锁定为1，配合后端的单品架构
    })
    
    // 2. 【核心修复】拦截后端的业务报错文字
    // 如果返回的字符串里包含 "失败" 两个字，立刻转为报错弹窗！
    if (typeof res.data === 'string' && res.data.includes('失败')) {
        showNotification(res.data, 'error'); // 显示后端的真实报错（如"追溯码已存在"）
        return; // 终止后续操作，不刷新页面，不清空输入框
    }
    
    // 3. 只有后端真正返回 "入库成功" 时，才走成功逻辑
    showNotification(`✅ 单品建档成功`, 'success')
    
    // 清空追溯码，方便录入下一盒
    inboundForm.value.traceCode = ''
    inboundForm.value.expireDate = ''
    
    // 4. 等待表格数据刷新完成
    await refreshData() 
    
    // 焦点回到输入框
    nextTick(() => document.getElementById('traceInput')?.focus())
    
  } catch (e) { 
    showNotification('网络或服务器内部错误', 'error') 
  }
}
onMounted(() => refreshData())
</script>

<template>
  <div class="container">
    <div v-if="loading" class="loading-mask"><div class="spinner"></div></div>
    <div :class="['toast', notification.type, { show: notification.show }]">{{ notification.message }}</div>

    <div class="header">
      <div class="title-row">
        <h1>🏥 医院药品闭环管理系统 <small>v3</small></h1>
        <div class="user-select">
          <span>当前操作员：</span>
          <select v-model="currentUser"><option v-for="u in userOptions" :key="u">{{u}}</option></select>
        </div>
      </div>
      <div class="tabs">
        <button :class="{ active: currentTab === 'dashboard' }" @click="currentTab = 'dashboard'">📊 药库物资保障</button>
        <button :class="{ active: currentTab === 'pharmacy' }" @click="currentTab = 'pharmacy'">💊 药房库存管理</button>
        <button :class="{ active: currentTab === 'nurse' }" @click="currentTab = 'nurse'">👩‍⚕️ 药房医师</button>
      </div>
    </div>

    <div v-if="currentTab === 'dashboard'" class="dashboard-layout">
      <div class="stat-cards">
        <div class="card stat-blue"><h3>📦 药品总库存</h3><div class="num">{{ totalStock }} <small>盒</small></div></div>
        <div class="card stat-green"><h3>📥 在库批次数</h3><div class="num">{{ totalInbound }} <small>批</small></div></div>
        <div class="card stat-red"><h3>🚨 低库存预警</h3><div class="num">{{ lowStockCount }} <small>种</small></div></div>
      </div>
      <div class="charts-row">
        <div class="chart-box"><div id="stockPie" class="echart-container"></div></div>
        <div class="chart-box"><div id="trendBar" class="echart-container"></div></div>
      </div>
      <!-- 近效期预警列表，对应论文3.2.2节效期预警功能 -->
      <div v-if="nearExpiryList.length > 0" class="expiry-alert-box">
        <h3>⏰ 近效期预警（90天内到期）</h3>
        <table>
          <thead>
            <tr>
              <th>药品名称</th>
              <th>追溯码</th>
              <th>批号</th>
              <th>库存</th>
              <th>货数</th>
              <th>有效期</th>
              <th>剩余天数</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="d in nearExpiryList" :key="d.id">
              <td>{{ d.drugName }}</td>
              <td class="mono">{{ d.traceCode }}</td>
              <td>{{ d.batchNumber }}</td>
              <td :class="d.quantity < 50 ? 'low-stock' : 'normal-stock'">{{ d.quantity }}</td>
              <td>{{ d.expireDate?.split('T')[0] || d.expireDate }}</td>
              <td>
                <span :class="getDaysClass(d.expireDate)">
                  {{ getDaysLeft(d.expireDate) }} 天
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-else-if="currentTab === 'pharmacy'" class="work-layout">
      <div class="action-panel">
        <div class="box in-box">
          <div class="box-header"><h3>📥 快速入库 ({{currentUser}})</h3><label class="toggle"><input type="checkbox" v-model="isCaseMode"><span>整箱模式</span></label></div>
          <div class="form-grid">
            <input v-model="inboundForm.drugName" placeholder="药品名称" />
            <input v-model="inboundForm.batchNumber" placeholder="生产批号" />
            <!-- 入库表单新增有效期输入框，解决 expire_date 写 NOW() 的 Bug -->
            <input v-model="inboundForm.expireDate" type="date" placeholder="有效期" />
            <div class="row">
              <input v-model="inboundForm.quantity" type="number" placeholder="数量" />
              <input v-if="isCaseMode" v-model="caseRatio" type="number" placeholder="1箱=?" class="highlight-input"/>
            </div>
            <input id="traceInput" v-model="inboundForm.traceCode" :placeholder="isCaseMode?'扫箱码':'扫盒码'" @keyup.enter="quickAddDrug" class="scan-input"/>
            <button @click="quickAddDrug" class="btn-primary">确认入库</button>
          </div>
        </div>
        <div class="box out-box">
          <h3>🛡️ 质控与损耗 ({{currentUser}})</h3>
          <div class="form-grid">
            <select v-model="outboundReason"><option>🔴 过期/破损报废</option><option>🟠 科室基数药领用</option><option>🔵 库存盘点修正</option></select>
            <div class="row" style="display: flex; gap: 10px;">
              <input v-model="outboundQty" type="number" min="1" placeholder="数量" style="width: 100px; flex: none; text-align: center;"/>
              <input v-model="pharmacyScanCode" placeholder="扫码登记..." @keyup.enter="directOutbound" class="scan-input" style="flex: 1;"/>
            </div>
            <button @click="directOutbound" class="btn-warning">确认处理</button>
          </div>
        </div>
      </div>
      <div class="table-card">
        <h3>📦 库存与质控明细</h3>
        <table>
          <thead><tr><th>ID</th><th>药名</th><th>追溯码</th><th>货位</th><th>批号/操作人</th><th>货数</th><th>质控状态</th><th>更新时间</th></tr></thead>
          <tbody>
            <tr v-for="d in drugList" :key="d.id">
              <td>{{d.id}}</td><td>{{d.drugName}}</td><td class="mono">{{d.traceCode}}</td><td style="font-weight: bold; color: #8e44ad;">{{d.locationCode || '待上架'}}</td><td>{{d.batchNumber}}</td>
              <td :class="d.quantity<50?'low-stock':'normal-stock'">{{d.quantity}}</td>
              <!-- 近效期相关函数，供库存明细表质控列和看板预警列表使用 -->
              <td>
                <span v-if="isNearExpiry(d.expireDate)" class="tag-warn">⚠️ 近效期</span>
                <span v-else class="tag-ok">✅ 合格</span>
              </td>
              <td class="time">{{d.updateTime || '--'}}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-else class="work-layout nurse-layout">
      <div class="left-col">
        <div class="patient-search">
          <input v-model="patientIdInput" placeholder="患者ID (如 P001)" />
          <button @click="checkPatient">查询</button>
        </div>
        
        <div class="task-list">
          <h4>待发药任务 ({{currentUser}})</h4>
          <div v-if="todoPrescriptions.length===0" class="empty">请先查询患者，否则无法核对医嘱</div>
          
          <div v-for="p in uniqueTodos" :key="p.id" class="task-card todo">
            <span>{{p.drugName}}</span>
            <span v-if="p.count > 1" class="badge red-alert">待发 x{{ p.count }}</span>
            <span v-else class="badge orange">待发</span>
          </div>

          <div class="scan-wrapper" v-if="todoPrescriptions.length">
            <div class="scan-row">
              <input v-model="nurseScanCode" placeholder="🔫 扫描药盒核对..." @keyup.enter="dispenseByNurse" class="scan-input-lg"/>
              <button @click="dispenseByNurse" class="btn-scan-confirm">确认发药</button>
            </div>
          </div>
        </div>

        <div class="task-list">
          <h4>已发药记录</h4>
          <div v-for="p in uniqueDones" :key="p.id" class="task-card done">
            <div class="task-info">
              <span>{{p.drugName}}</span>
              <span v-if="p.count > 1" class="badge blue-info">已发 x{{ p.count }}</span>
            </div>
            
            <div class="action-row">
              <input 
                v-if="p.count > 1" 
                v-model="returnInputs[p.drugName]" 
                type="number" min="1" :max="p.count"
                placeholder="数量" 
                class="mini-input"
              />
              <button @click="returnByNurse(p)" class="btn-mini-danger">退药</button>
            </div>
          </div>
        </div>
      </div>
      
      <div class="right-col">
        <h3>📜 实时流水 (含操作员)</h3>
        <div class="logs">
          <div v-for="r in recordList" :key="r.id" class="log-item">
            <span class="mono">{{r.dispenseTime?.split(' ')[1]}}</span>
            <span :class="getLogClass(getLogName(r))">{{ getLogName(r) }}</span>
            <span>{{r.drugName}}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>


<style scoped>
:global(*) {
  box-sizing: border-box;
}

:global(body) {
  margin: 0;
  min-height: 100vh;
  background: #eef3f8;
  color: #182230;
  font-family: Inter, "Segoe UI", "PingFang SC", "Microsoft YaHei", Arial, sans-serif;
}

:global(#app) {
  min-height: 100vh;
}

.container {
  width: min(1440px, calc(100vw - 40px));
  margin: 0 auto;
  padding: 28px 0 40px;
}

.header {
  position: sticky;
  top: 0;
  z-index: 20;
  margin-bottom: 22px;
  padding: 18px 0 20px;
  background: rgba(238, 243, 248, 0.9);
  backdrop-filter: blur(14px);
}

.title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 18px;
}

.title-row h1 {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0;
  color: #101828;
  font-size: 28px;
  font-weight: 800;
  letter-spacing: 0;
}

.title-row h1 small {
  color: #667085;
  font-size: 13px;
  font-weight: 600;
}

.user-select {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 220px;
  padding: 12px 16px;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 10px 24px rgba(16, 24, 40, 0.06);
  color: #475467;
  font-size: 14px;
}

.user-select select {
  width: auto;
  min-width: 88px;
  padding: 4px 22px 4px 4px;
  border: 0;
  background: transparent;
  color: #2454d6;
  font-weight: 700;
  cursor: pointer;
}

.tabs {
  display: inline-flex;
  gap: 6px;
  padding: 6px;
  border: 1px solid #d9e2ec;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 8px 22px rgba(16, 24, 40, 0.05);
}

.tabs button {
  min-height: 42px;
  padding: 0 20px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: #475467;
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0;
  cursor: pointer;
  transition: background 0.18s ease, color 0.18s ease, box-shadow 0.18s ease;
}

.tabs button:hover {
  background: #f2f5f9;
  color: #182230;
}

.tabs button.active {
  background: #2454d6;
  color: #fff;
  box-shadow: 0 8px 18px rgba(36, 84, 214, 0.24);
}

.dashboard-layout,
.work-layout,
.nurse-layout {
  animation: fadeIn 0.25s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

.stat-cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
  margin-bottom: 18px;
}

.stat-cards .card {
  min-height: 150px;
  padding: 24px;
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 8px;
  color: #fff;
  box-shadow: 0 16px 34px rgba(16, 24, 40, 0.10);
}

.card h3 {
  margin: 0 0 20px;
  font-size: 15px;
  font-weight: 700;
  opacity: 0.92;
}

.card .num {
  font-size: 42px;
  line-height: 1;
  font-weight: 850;
  letter-spacing: 0;
}

.card .num small {
  margin-left: 6px;
  font-size: 18px;
  font-weight: 700;
  opacity: 0.85;
}

.stat-blue { background: linear-gradient(135deg, #2563eb 0%, #22a6b3 100%); }
.stat-green { background: linear-gradient(135deg, #08966f 0%, #44bd87 100%); }
.stat-red { background: linear-gradient(135deg, #e5484d 0%, #f97373 100%); }

.charts-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 18px;
  min-height: 430px;
}

.chart-box,
.table-card,
.box,
.left-col,
.right-col,
.expiry-alert-box {
  border: 1px solid #dfe7f0;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 12px 28px rgba(16, 24, 40, 0.06);
}

.chart-box {
  min-height: 430px;
  padding: 18px;
}

.echart-container {
  width: 100%;
  height: 100%;
  min-height: 390px;
}

.expiry-alert-box {
  margin-top: 18px;
  padding: 20px;
  border-left: 4px solid #e5484d;
}

.expiry-alert-box h3,
.table-card h3,
.right-col h3,
.box h3,
.task-list h4 {
  margin: 0 0 16px;
  color: #182230;
  font-size: 16px;
  font-weight: 800;
}

.work-layout {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.action-panel {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.box,
.table-card,
.left-col,
.right-col {
  padding: 20px;
}

.in-box { border-top: 4px solid #2454d6; }
.out-box { border-top: 4px solid #f79009; }

.box-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.form-grid {
  display: grid;
  gap: 12px;
}

.row,
.scan-row,
.patient-search,
.action-row {
  display: flex;
  gap: 10px;
  align-items: center;
}

input,
select {
  width: 100%;
  min-height: 42px;
  padding: 10px 12px;
  border: 1px solid #cfd8e3;
  border-radius: 7px;
  background: #fff;
  color: #182230;
  font-size: 14px;
  outline: none;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, background 0.16s ease;
}

input:hover,
select:hover {
  border-color: #aebdcc;
}

input:focus,
select:focus {
  border-color: #2454d6;
  box-shadow: 0 0 0 3px rgba(36, 84, 214, 0.13);
}

.scan-input,
.scan-input-lg,
.highlight-input {
  border-color: #9bb4ff;
  background: #f7f9ff;
  font-weight: 650;
}

button {
  letter-spacing: 0;
}

.btn-primary,
.btn-warning,
.btn-scan-confirm,
.patient-search button {
  min-height: 42px;
  border: 0;
  border-radius: 7px;
  color: #fff;
  font-size: 14px;
  font-weight: 800;
  cursor: pointer;
  transition: transform 0.16s ease, box-shadow 0.16s ease, filter 0.16s ease;
}

.btn-primary,
.btn-scan-confirm,
.patient-search button {
  background: #2454d6;
  box-shadow: 0 8px 16px rgba(36, 84, 214, 0.22);
}

.btn-warning {
  background: #d97706;
  box-shadow: 0 8px 16px rgba(217, 119, 6, 0.22);
}

.btn-primary:hover,
.btn-warning:hover,
.btn-scan-confirm:hover,
.patient-search button:hover,
.btn-mini-danger:hover {
  transform: translateY(-1px);
  filter: brightness(1.03);
}

.btn-primary:active,
.btn-warning:active,
.btn-scan-confirm:active,
.patient-search button:active,
.btn-mini-danger:active {
  transform: translateY(0);
}

.toggle {
  display: inline-flex;
  align-items: center;
  cursor: pointer;
  user-select: none;
}

.toggle input {
  display: none;
}

.toggle span {
  min-height: 32px;
  padding: 7px 12px;
  border: 1px solid #d9e2ec;
  border-radius: 999px;
  background: #f6f8fb;
  color: #475467;
  font-size: 13px;
  font-weight: 700;
}

.toggle input:checked + span {
  border-color: #2454d6;
  background: #e8efff;
  color: #2454d6;
}

.table-card {
  overflow: hidden;
}

table {
  width: 100%;
  border-collapse: collapse;
  overflow: hidden;
  border-radius: 8px;
  font-size: 14px;
}

th {
  position: sticky;
  top: 0;
  z-index: 1;
  padding: 13px 14px;
  background: #f4f7fb;
  color: #475467;
  text-align: left;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

td {
  padding: 13px 14px;
  border-top: 1px solid #edf1f5;
  color: #344054;
  vertical-align: middle;
}

tbody tr:hover {
  background: #f8fbff;
}

.mono {
  display: inline-flex;
  max-width: 240px;
  padding: 4px 8px;
  border: 1px solid #dfe7f0;
  border-radius: 6px;
  background: #f8fafc;
  color: #2454d6;
  font-family: "Cascadia Mono", Consolas, monospace;
  font-size: 12px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.time {
  color: #667085;
  font-family: "Cascadia Mono", Consolas, monospace;
  font-size: 12px;
}

.low-stock,
.red {
  color: #d92d20;
  font-weight: 800;
}

.normal-stock,
.green {
  color: #079455;
  font-weight: 800;
}

.blue {
  color: #2454d6;
  font-weight: 800;
}

.orange {
  color: #b54708;
  font-weight: 800;
}

.tag-warn,
.tag-ok,
.days-urgent,
.days-warn,
.days-ok,
.badge {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

.tag-warn,
.days-urgent,
.badge.red-alert {
  border: 1px solid #fecdca;
  background: #fff1f0;
  color: #d92d20;
}

.tag-ok,
.days-ok {
  border: 1px solid #abefc6;
  background: #ecfdf3;
  color: #067647;
}

.days-warn,
.badge.orange {
  border: 1px solid #fedf89;
  background: #fffaeb;
  color: #b54708;
}

.badge.blue-info {
  border: 1px solid #b2ccff;
  background: #eff4ff;
  color: #2454d6;
}

.nurse-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(360px, 0.8fr);
  gap: 18px;
}

.left-col,
.right-col {
  height: min(680px, calc(100vh - 170px));
  min-height: 520px;
  overflow: auto;
}

.patient-search {
  margin-bottom: 18px;
}

.patient-search input {
  flex: 1;
}

.patient-search button {
  width: 96px;
}

.task-list {
  margin-bottom: 22px;
}

.task-list h4 {
  padding-bottom: 10px;
  border-bottom: 1px solid #edf1f5;
}

.task-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  padding: 14px;
  border: 1px solid #dfe7f0;
  border-radius: 8px;
  background: #fff;
}

.task-card.todo {
  border-left: 4px solid #f79009;
}

.task-card.done {
  border-left: 4px solid #12b76a;
}

.task-info {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.scan-wrapper {
  margin-top: 14px;
  padding: 14px;
  border: 1px dashed #9bb4ff;
  border-radius: 8px;
  background: #f7f9ff;
}

.scan-input-lg {
  flex: 1;
  min-height: 46px;
}

.btn-scan-confirm {
  min-width: 112px;
  padding: 0 18px;
}

.mini-input {
  width: 70px;
  min-height: 32px;
  padding: 6px 8px;
  text-align: center;
}

.btn-mini-danger {
  min-height: 32px;
  padding: 0 13px;
  border: 0;
  border-radius: 999px;
  background: #d92d20;
  color: #fff;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
  box-shadow: 0 6px 12px rgba(217, 45, 32, 0.18);
}

.logs {
  max-height: calc(100% - 48px);
  overflow: auto;
}

.log-item {
  display: grid;
  grid-template-columns: 78px minmax(110px, 1fr) minmax(120px, 1fr);
  gap: 10px;
  align-items: center;
  padding: 11px 0;
  border-bottom: 1px solid #edf1f5;
  font-size: 13px;
}

.empty {
  padding: 28px 16px;
  border: 1px dashed #cfd8e3;
  border-radius: 8px;
  background: #f8fafc;
  color: #667085;
  text-align: center;
  font-weight: 700;
}

.loading-mask {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(238, 243, 248, 0.62);
  backdrop-filter: blur(4px);
}

.spinner {
  width: 44px;
  height: 44px;
  border: 4px solid rgba(36, 84, 214, 0.14);
  border-top-color: #2454d6;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.toast {
  position: fixed;
  top: 24px;
  right: 24px;
  z-index: 120;
  max-width: min(420px, calc(100vw - 40px));
  padding: 14px 18px;
  border-radius: 8px;
  color: #fff;
  font-weight: 800;
  box-shadow: 0 14px 32px rgba(16, 24, 40, 0.18);
  transform: translateX(calc(100% + 32px));
  transition: transform 0.25s ease;
}

.toast.show {
  transform: translateX(0);
}

.toast.success { background: #079455; }
.toast.error { background: #d92d20; }
.toast.info { background: #2454d6; }

::-webkit-scrollbar {
  width: 10px;
  height: 10px;
}

::-webkit-scrollbar-track {
  background: #edf1f5;
}

::-webkit-scrollbar-thumb {
  background: #b8c5d4;
  border: 2px solid #edf1f5;
  border-radius: 999px;
}

@media (max-width: 1180px) {
  .container {
    width: min(100% - 28px, 1440px);
  }

  .charts-row,
  .action-panel,
  .nurse-layout {
    grid-template-columns: 1fr;
  }

  .left-col,
  .right-col {
    height: auto;
    min-height: 420px;
  }
}

@media (max-width: 760px) {
  .container {
    width: min(100% - 20px, 1440px);
    padding-top: 14px;
  }

  .header {
    position: static;
    padding-top: 0;
  }

  .title-row {
    align-items: stretch;
    flex-direction: column;
  }

  .title-row h1 {
    font-size: 22px;
  }

  .user-select,
  .tabs {
    width: 100%;
  }

  .tabs {
    display: grid;
    grid-template-columns: 1fr;
  }

  .tabs button {
    width: 100%;
  }

  .stat-cards {
    grid-template-columns: 1fr;
  }

  .stat-cards .card {
    min-height: 118px;
  }

  .chart-box,
  .echart-container {
    min-height: 330px;
  }

  .row,
  .scan-row,
  .patient-search,
  .action-row {
    align-items: stretch;
    flex-direction: column;
  }

  .patient-search button,
  .btn-scan-confirm {
    width: 100%;
  }

  .table-card,
  .expiry-alert-box {
    overflow-x: auto;
  }

  table {
    min-width: 760px;
  }

  .log-item {
    grid-template-columns: 1fr;
  }
}
</style>

