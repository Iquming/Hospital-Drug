<script setup>
import { ref, onMounted, nextTick, computed, watch } from 'vue'
import axios from 'axios'
import * as echarts from 'echarts' 
import loginPharmacyHero from './assets/login-pharmacy-hero.png'

// --- 0. 基础配置 ---
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8081',
  timeout: 5000
})

// --- 1. 全局状态 ---
const currentTab = ref('dashboard') 
const loading = ref(false)          
const notification = ref({ show: false, message: '', type: 'success' }) 
const apiOnline = ref(false)
const lastSyncedAt = ref('--')

const authToken = ref(localStorage.getItem('hospitalDrugToken') || '')
const authUser = ref(null)
const loginForm = ref({ username: 'admin', password: '123456' })
const loginLoading = ref(false)

const roleLabels = { ADMIN: '管理员', PHARMACIST: '药师', NURSE: '护士' }
const statusLabels = { ENABLED: '启用', DISABLED: '禁用' }
const currentUser = computed(() => authUser.value?.displayName || authUser.value?.username || '未登录')
const isAuthenticated = computed(() => Boolean(authToken.value && authUser.value))
const isAdmin = computed(() => authUser.value?.role === 'ADMIN')
const canUsePharmacy = computed(() => ['ADMIN', 'PHARMACIST'].includes(authUser.value?.role))
const canUseNurse = computed(() => ['ADMIN', 'PHARMACIST', 'NURSE'].includes(authUser.value?.role))

const userList = ref([])
const userForm = ref({ id: null, username: '', password: '', displayName: '', role: 'NURSE', department: '', status: 'ENABLED' })
const passwordForm = ref({ userId: null, password: '' })

// --- 2. 核心数据 ---
const drugList = ref([])   
const recordList = ref([]) 
const localFixMap = ref([]) 
const dashboardSummary = ref({})
const stockStatus = ref({})

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
const inboundForm = ref({
  drugName: '',
  traceCode: '',
  batchNumber: '',
  quantity: 1,
  expireDate: '',
  isSplitAllowed: false,
  packageUnit: '盒',
  minUnit: '片',
  minUnitsPerPackage: 1
})
const isCaseMode = ref(false) 
const caseRatio = ref(20)     
const pharmacyScanCode = ref('') 
const outboundReason = ref('过期/破损报废')
const outboundQty = ref(1)
const splitForm = ref({ parentTraceCode: '', splitUnits: 1 })
const lastSplitCode = ref(null)

// --- 5. 图表逻辑 ---
let pieChartInstance = null
let barChartInstance = null

const totalStock = computed(() => dashboardSummary.value.totalStock ?? drugList.value.reduce((acc, cur) => acc + (cur.quantity || 0), 0))
const lowStockCount = computed(() => dashboardSummary.value.lowStockCount ?? drugList.value.filter(d => d.quantity > 0 && d.quantity < 50).length)
const totalInbound = computed(() => dashboardSummary.value.skuCount ?? drugList.value.length)
const inStockCount = computed(() => dashboardSummary.value.inStockCount ?? drugList.value.filter(d => d.quantity > 0).length)
const outStockCount = computed(() => dashboardSummary.value.outStockCount ?? drugList.value.filter(d => d.quantity <= 0).length)
const nearExpiryCount = computed(() => dashboardSummary.value.nearExpiryCount ?? nearExpiryList.value.length)
const recentRecordCount = computed(() => dashboardSummary.value.recordCount ?? recordList.value.length)
const operationHealth = computed(() => {
  if (!apiOnline.value) return '接口待连接'
  if (nearExpiryCount.value > 0 || lowStockCount.value > 0) return '需重点关注'
  return '运行平稳'
})

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

api.interceptors.request.use(config => {
  if (authToken.value) {
    config.headers.Authorization = `Bearer ${authToken.value}`
  }
  return config
})

api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      handleUnauthorized()
    }
    return Promise.reject(error)
  }
)

const roleText = (role) => roleLabels[role] || role || '--'
const statusText = (status) => statusLabels[status] || status || '--'

const handleUnauthorized = () => {
  localStorage.removeItem('hospitalDrugToken')
  authToken.value = ''
  authUser.value = null
  currentTab.value = 'dashboard'
  apiOnline.value = false
}

const login = async () => {
  if (!loginForm.value.username || !loginForm.value.password) {
    return showNotification('请输入用户名和密码', 'error')
  }
  loginLoading.value = true
  try {
    const res = await api.post('/auth/login', loginForm.value)
    authToken.value = res.data.token
    authUser.value = res.data.user
    localStorage.setItem('hospitalDrugToken', authToken.value)
    showNotification(`欢迎，${currentUser.value}`, 'success')
    await refreshData()
    if (isAdmin.value) await loadUsers()
  } catch (e) {
    showNotification(e.response?.data || '登录失败', 'error')
  } finally {
    loginLoading.value = false
  }
}

const logout = async () => {
  try {
    if (authToken.value) await api.post('/auth/logout')
  } catch (e) {
    // 前端清理 token 即可完成退出。
  }
  handleUnauthorized()
  showNotification('已退出登录', 'info')
}

const restoreSession = async () => {
  if (!authToken.value) return
  try {
    const res = await api.get('/auth/me')
    authUser.value = res.data.user
    await refreshData()
    if (isAdmin.value) await loadUsers()
  } catch (e) {
    handleUnauthorized()
  }
}

const loadUsers = async () => {
  if (!isAdmin.value) return
  const res = await api.get('/users')
  userList.value = res.data
}

const resetUserForm = () => {
  userForm.value = { id: null, username: '', password: '', displayName: '', role: 'NURSE', department: '', status: 'ENABLED' }
}

const editUser = (user) => {
  userForm.value = {
    id: user.id,
    username: user.username,
    password: '',
    displayName: user.displayName,
    role: user.role,
    department: user.department || '',
    status: user.status
  }
}

const saveUser = async () => {
  try {
    if (userForm.value.id) {
      await api.put(`/users/${userForm.value.id}`, userForm.value)
      showNotification('用户已更新', 'success')
    } else {
      await api.post('/users', userForm.value)
      showNotification('用户已创建', 'success')
    }
    resetUserForm()
    await loadUsers()
  } catch (e) {
    showNotification(e.response?.data || '用户保存失败', 'error')
  }
}

const resetPassword = async (user) => {
  if (!passwordForm.value.password || passwordForm.value.userId !== user.id) {
    passwordForm.value = { userId: user.id, password: '' }
    return
  }
  try {
    await api.put(`/users/${user.id}/password`, { password: passwordForm.value.password })
    passwordForm.value = { userId: null, password: '' }
    showNotification('密码已重置', 'success')
  } catch (e) {
    showNotification(e.response?.data || '密码重置失败', 'error')
  }
}

const disableUser = async (user) => {
  if (!confirm(`确认禁用用户 ${user.displayName || user.username}？`)) return
  try {
    await api.delete(`/users/${user.id}`)
    showNotification('用户已禁用', 'success')
    await loadUsers()
  } catch (e) {
    showNotification(e.response?.data || '用户禁用失败', 'error')
  }
}

const deleteUser = async (user) => {
  if (!user?.id) return
  if (user.id === authUser.value?.id) {
    return showNotification('不能删除当前登录用户', 'error')
  }
  if (!confirm(`确认永久删除用户 ${user.displayName || user.username}？删除后不可恢复。`)) return
  try {
    await api.delete(`/users/${user.id}/hard`)
    if (userForm.value.id === user.id) resetUserForm()
    showNotification('用户已删除', 'success')
    await loadUsers()
  } catch (e) {
    showNotification(e.response?.data || '用户删除失败', 'error')
  }
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

const packageText = (d) => {
  const packageUnit = d.packageUnit || d.unit || '盒'
  const minUnit = d.minUnit || packageUnit
  const perPackage = d.minUnitsPerPackage || 1
  if (perPackage <= 1 || packageUnit === minUnit) return packageUnit
  return `1${packageUnit}=${perPackage}${minUnit}`
}

const splitStockText = (d) => {
  if (!d.isSplitAllowed) return '整包装'
  return `${d.remainingMinUnits ?? 0}/${d.minUnitsPerPackage ?? 1}${d.minUnit || '单位'}`
}

const verifyDispenseCode = async (code, expectedDrugName = '') => {
  const res = await api.post('/device/scan/verify', {
    scene: 'DISPENSE',
    traceCode: code,
    expectedDrugName
  })
  return res.data
}

const refreshData = async () => {
  if (!authToken.value) return
  loading.value = true
  try {
    const [resList, resRecords, resSummary, resStatus] = await Promise.all([
      api.get('/list'),
      api.get('/records/recent?limit=50').catch(() => api.get('/records')),
      api.get('/dashboard/summary').catch(() => ({ data: {} })),
      api.get('/stock/status').catch(() => ({ data: {} }))
    ])
    drugList.value = resList.data
    recordList.value = resRecords.data
    dashboardSummary.value = resSummary.data || {}
    stockStatus.value = resStatus.data || {}
    apiOnline.value = true
    lastSyncedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    if(currentTab.value === 'dashboard') initCharts()
  } catch (error) {
    apiOnline.value = false
    showNotification('数据同步失败，请检查后端服务和数据库', 'error')
  } 
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
  let verifyResult
  try {
    verifyResult = await verifyDispenseCode(code)
  } catch (e) {
    alert(e.response?.data || '追溯码核对失败')
    return
  }
  if (verifyResult.suggestion === 'BLOCK') {
    alert(`发药拦截：${verifyResult.message}`)
    return
  }

  const scannedDrugName = verifyResult.codeType === 'CHILD' ? verifyResult.drug?.drugName : verifyResult.drug?.drugName
  const prescription = todoPrescriptions.value.find(p => p.drugName === scannedDrugName)
  if (!prescription) {
    const needed = todoPrescriptions.value.map(p => p.drugName).join('、')
    alert(`发药错误拦截！\n\n扫码药品：${scannedDrugName || '未知'}\n患者医嘱：${needed || '无'}\n\n药名不一致，严禁发药！`)
    return
  }

  const pName = patientDb[patientIdInput.value] || '未知'
  const bindInfo = `👤 ${pName}(${patientIdInput.value}) [${currentUser.value}]`

  try {
    const res = await api.post('/dispense', {
      traceCode: code,
      patientId: bindInfo, 
      prescriptionId: prescription.id.toString(),
      quantity: "1",
      dispenseUnits: verifyResult.splitUnits ? String(verifyResult.splitUnits) : "1"
    })
    
    const resStr = typeof res.data === 'object' ? JSON.stringify(res.data) : String(res.data)
    if (res.status === 200 || resStr.includes("成功")) {
      const unitText = verifyResult.codeType === 'CHILD' ? `（拆零 ${verifyResult.splitUnits}${verifyResult.minUnit}）` : ''
      showNotification(`✅ 发药成功：${scannedDrugName}${unitText}`, 'success')
      
      const now = new Date()
      const timeStr = `${now.getHours()}:${now.getMinutes()}:${now.getSeconds()}`.replace(/\b(\d)\b/g, '0$1')
      localFixMap.value.push({ drugName: scannedDrugName, correctName: bindInfo, timeStr: timeStr })

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

const directOutbound = async () => {
  const code = pharmacyScanCode.value.trim()
  if (!code) return showNotification('请扫描需要处理的药品追溯码', 'error')

  const drugInStock = drugList.value.find(d => d.traceCode === code)
  if (!drugInStock) {
    showNotification('库存中未找到该追溯码，请先完成入库建档', 'error')
    return
  }
  if ((drugInStock.quantity || 0) <= 0) {
    showNotification('该单品已出库或库存状态异常', 'error')
    return
  }

  const reason = outboundReason.value || '质控处理'
  const operatorNote = `【质控】${reason} [${currentUser.value}]`

  loading.value = true
  try {
    await api.post('/dispense', {
      traceCode: code,
      patientId: operatorNote,
      quantity: String(outboundQty.value || 1)
    })

    const now = new Date()
    const timeStr = `${now.getHours()}:${now.getMinutes()}:${now.getSeconds()}`.replace(/\b(\d)\b/g, '0$1')
    localFixMap.value.push({ drugName: drugInStock.drugName, correctName: operatorNote, timeStr })
    showNotification(`质控处理完成：${drugInStock.drugName}`, 'success')
    pharmacyScanCode.value = ''
    outboundQty.value = 1
    await refreshData()
  } catch (e) {
    const message = e.response?.data || '质控处理失败'
    showNotification(message, 'error')
  } finally {
    loading.value = false
  }
}

const createSplitCode = async () => {
  const parentTraceCode = splitForm.value.parentTraceCode.trim()
  const splitUnits = parseInt(splitForm.value.splitUnits)
  if (!parentTraceCode) return showNotification('请扫描母包装追溯码', 'error')
  if (!splitUnits || splitUnits <= 0) return showNotification('请输入正确的拆零最小单位数量', 'error')

  loading.value = true
  try {
    const res = await api.post('/split/create', {
      parentTraceCode,
      splitUnits: String(splitUnits)
    })
    lastSplitCode.value = res.data
    splitForm.value.parentTraceCode = ''
    splitForm.value.splitUnits = 1
    showNotification(`拆零子码已生成：${res.data.childTraceCode}`, 'success')
    await refreshData()
  } catch (e) {
    showNotification(e.response?.data || '拆零建码失败', 'error')
  } finally {
    loading.value = false
  }
}

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
onMounted(() => restoreSession())
</script>

<template>
  <div>
    <div v-if="loading" class="loading-mask"><div class="spinner"></div></div>
    <div :class="['toast', notification.type, { show: notification.show }]">{{ notification.message }}</div>

    <div v-if="!isAuthenticated" class="login-shell">
      <div class="login-panel">
        <div class="login-brand">
          <span class="brand-kicker">Hospital Pharmacy Console</span>
          <h1>医院药品闭环管理系统</h1>
          <p>请输入院内账号进入药品闭环管理工作台。</p>
          <div class="login-image-frame">
            <img :src="loginPharmacyHero" alt="医院药房药品货架与扫码工作台" />
          </div>
        </div>
        <div class="login-form">
          <label>
            <span>用户名</span>
            <input v-model="loginForm.username" placeholder="admin" @keyup.enter="login" />
          </label>
          <label>
            <span>密码</span>
            <input v-model="loginForm.password" type="password" placeholder="123456" @keyup.enter="login" />
          </label>
          <button @click="login" class="btn-primary login-button" :disabled="loginLoading">
            {{ loginLoading ? '正在登录...' : '登录系统' }}
          </button>
        </div>
      </div>
    </div>

    <div v-else class="container">
    <div class="header">
      <div class="title-row">
        <div class="brand-block">
          <span class="brand-kicker">Hospital Pharmacy Console</span>
          <h1>医院药品闭环管理系统 <small>院内版 v4</small></h1>
        </div>
        <div class="top-actions">
          <span :class="['connection-pill', { offline: !apiOnline }]">● {{ apiOnline ? '数据已连接' : '接口待连接' }}</span>
          <div class="user-profile">
            <span>{{ roleText(authUser.role) }}</span>
            <strong>{{ currentUser }}</strong>
            <small>{{ authUser.department || '未设置科室' }}</small>
          </div>
          <button @click="logout" class="btn-logout">退出</button>
        </div>
      </div>
      <div class="tabs nav-rail">
        <button :class="{ active: currentTab === 'dashboard' }" @click="currentTab = 'dashboard'">院内总览</button>
        <button v-if="canUsePharmacy" :class="{ active: currentTab === 'pharmacy' }" @click="currentTab = 'pharmacy'">药库质控</button>
        <button v-if="canUseNurse" :class="{ active: currentTab === 'nurse' }" @click="currentTab = 'nurse'">调剂发药</button>
        <button v-if="isAdmin" :class="{ active: currentTab === 'users' }" @click="currentTab = 'users'; loadUsers()">用户管理</button>
      </div>
    </div>

    <div class="command-strip">
      <div class="command-item">
        <span>运行状态</span>
        <strong>{{ operationHealth }}</strong>
      </div>
      <div class="command-item">
        <span>最近同步</span>
        <strong>{{ lastSyncedAt }}</strong>
      </div>
      <div class="command-item">
        <span>库存分层</span>
        <strong>正常 {{ stockStatus.normal ?? '--' }} / 预警 {{ lowStockCount + nearExpiryCount }}</strong>
      </div>
      <div class="command-item">
        <span>流水总数</span>
        <strong>{{ recentRecordCount }}</strong>
      </div>
    </div>

    <div v-if="currentTab === 'dashboard'" class="dashboard-layout">
      <div class="stat-cards">
        <div class="card stat-blue"><h3>药品总库存</h3><div class="num">{{ totalStock }} <small>盒</small></div><p>覆盖 {{ totalInbound }} 条单品档案</p></div>
        <div class="card stat-green"><h3>在库可调剂</h3><div class="num">{{ inStockCount }} <small>件</small></div><p>已出库 {{ outStockCount }} 件</p></div>
        <div class="card stat-amber"><h3>近效期预警</h3><div class="num">{{ nearExpiryCount }} <small>件</small></div><p>90 天内到期需复核</p></div>
        <div class="card stat-red"><h3>低库存预警</h3><div class="num">{{ lowStockCount }} <small>种</small></div><p>低于院内补货阈值</p></div>
      </div>
      <div class="charts-row">
        <div class="chart-box"><div id="stockPie" class="echart-container"></div></div>
        <div class="chart-box"><div id="trendBar" class="echart-container"></div></div>
      </div>
      <!-- 近效期预警列表，对应论文3.2.2节效期预警功能 -->
      <div class="risk-board">
        <div class="expiry-alert-box">
          <h3>近效期重点复核（90天内到期）</h3>
          <div v-if="nearExpiryList.length === 0" class="empty">暂无近效期预警</div>
          <table v-else>
            <thead>
              <tr>
                <th>药品名称</th>
                <th>追溯码</th>
                <th>批号</th>
                <th>库存</th>
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
        <div class="recent-card">
          <h3>最近出入库流水</h3>
          <div class="compact-log" v-for="r in recordList.slice(0, 8)" :key="r.id">
            <span class="mono">{{r.dispenseTime?.split(' ')[1] || '--'}}</span>
            <strong :class="getLogClass(getLogName(r))">{{ getLogName(r) }}</strong>
            <span>{{r.drugName}}</span>
          </div>
          <div v-if="recordList.length === 0" class="empty">暂无流水记录</div>
        </div>
      </div>
    </div>

    <div v-else-if="currentTab === 'pharmacy'" class="work-layout">
      <div class="action-panel">
        <div class="box in-box">
          <div class="box-header"><h3>药品扫码入库 ({{currentUser}})</h3><label class="toggle"><input type="checkbox" v-model="isCaseMode"><span>整箱模式</span></label></div>
          <div class="form-grid">
            <input v-model="inboundForm.drugName" placeholder="药品名称" />
            <input v-model="inboundForm.batchNumber" placeholder="生产批号" />
            <!-- 入库表单新增有效期输入框，解决 expire_date 写 NOW() 的 Bug -->
            <input v-model="inboundForm.expireDate" type="date" placeholder="有效期" />
            <label class="toggle split-toggle"><input type="checkbox" v-model="inboundForm.isSplitAllowed"><span>允许拆零</span></label>
            <div class="row">
              <input v-model="inboundForm.packageUnit" placeholder="整包装单位，如盒" />
              <input v-model="inboundForm.minUnit" placeholder="最小单位，如片" />
              <input v-model="inboundForm.minUnitsPerPackage" type="number" min="1" placeholder="每盒最小单位数" />
            </div>
            <div class="row">
              <input v-model="inboundForm.quantity" type="number" placeholder="数量" />
              <input v-if="isCaseMode" v-model="caseRatio" type="number" placeholder="1箱=?" class="highlight-input"/>
            </div>
            <input id="traceInput" v-model="inboundForm.traceCode" :placeholder="isCaseMode?'扫箱码':'扫盒码'" @keyup.enter="quickAddDrug" class="scan-input"/>
            <button @click="quickAddDrug" class="btn-primary">确认入库</button>
          </div>
        </div>
        <div class="box out-box">
          <h3>质量控制与损耗登记 ({{currentUser}})</h3>
          <div class="form-grid">
            <select v-model="outboundReason"><option>过期/破损报废</option><option>科室基数药领用</option><option>库存盘点修正</option></select>
            <div class="row" style="display: flex; gap: 10px;">
              <input v-model="outboundQty" type="number" min="1" placeholder="数量" style="width: 100px; flex: none; text-align: center;"/>
              <input v-model="pharmacyScanCode" placeholder="扫码登记..." @keyup.enter="directOutbound" class="scan-input" style="flex: 1;"/>
            </div>
            <button @click="directOutbound" class="btn-warning">确认处理</button>
          </div>
        </div>
        <div class="box split-box">
          <h3>拆零建码 ({{currentUser}})</h3>
          <div class="form-grid">
            <input v-model="splitForm.parentTraceCode" placeholder="扫描母包装追溯码" @keyup.enter="createSplitCode" class="scan-input"/>
            <div class="row" style="display: flex; gap: 10px;">
              <input v-model="splitForm.splitUnits" type="number" min="1" placeholder="拆零数量" style="width: 120px; flex: none; text-align: center;"/>
              <button @click="createSplitCode" class="btn-primary" style="flex: 1;">生成子码</button>
            </div>
            <div v-if="lastSplitCode" class="split-result">
              <span>子码</span>
              <strong class="mono">{{ lastSplitCode.childTraceCode }}</strong>
              <small>{{ lastSplitCode.drugName }} · {{ lastSplitCode.splitUnits }}{{ lastSplitCode.minUnit }} · 母包装剩余 {{ lastSplitCode.remainingParentUnits }}{{ lastSplitCode.minUnit }}</small>
            </div>
          </div>
        </div>
      </div>
      <div class="table-card">
        <h3>库存与质控明细</h3>
        <table>
          <thead><tr><th>ID</th><th>药名</th><th>追溯码</th><th>包装规格</th><th>拆零库存</th><th>货位</th><th>批号/操作人</th><th>货数</th><th>质控状态</th><th>更新时间</th></tr></thead>
          <tbody>
            <tr v-for="d in drugList" :key="d.id">
              <td>{{d.id}}</td><td>{{d.drugName}}</td><td class="mono">{{d.traceCode}}</td><td>{{ packageText(d) }}</td><td>{{ splitStockText(d) }}</td><td style="font-weight: bold; color: #8e44ad;">{{d.locationCode || '待上架'}}</td><td>{{d.batchNumber}}</td>
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

    <div v-else-if="currentTab === 'nurse'" class="work-layout nurse-layout">
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

    <div v-else-if="currentTab === 'users' && isAdmin" class="work-layout">
      <div class="user-admin-layout">
        <div class="box in-box">
          <div class="box-header">
            <h3>{{ userForm.id ? '编辑用户' : '新增用户' }}</h3>
            <button v-if="userForm.id" @click="resetUserForm" class="btn-subtle">新建</button>
          </div>
          <div class="form-grid">
            <input v-model="userForm.username" :disabled="Boolean(userForm.id)" placeholder="登录用户名" />
            <input v-if="!userForm.id" v-model="userForm.password" type="password" placeholder="初始密码，至少 6 位" />
            <input v-model="userForm.displayName" placeholder="姓名" />
            <div class="row">
              <select v-model="userForm.role">
                <option value="ADMIN">管理员</option>
                <option value="PHARMACIST">药师</option>
                <option value="NURSE">护士</option>
              </select>
              <select v-model="userForm.status">
                <option value="ENABLED">启用</option>
                <option value="DISABLED">禁用</option>
              </select>
            </div>
            <input v-model="userForm.department" placeholder="科室" />
            <button @click="saveUser" class="btn-primary">{{ userForm.id ? '保存修改' : '创建用户' }}</button>
            <button
              v-if="userForm.id"
              @click="deleteUser(userForm)"
              class="btn-danger-wide"
              :disabled="userForm.id === authUser.id"
            >
              删除该用户
            </button>
          </div>
        </div>

        <div class="table-card">
          <h3>用户列表</h3>
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>用户名</th>
                <th>姓名</th>
                <th>角色</th>
                <th>科室</th>
                <th>状态</th>
                <th>最近登录</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="u in userList" :key="u.id">
                <td>{{ u.id }}</td>
                <td class="mono">{{ u.username }}</td>
                <td>{{ u.displayName }}</td>
                <td><span class="badge blue-info">{{ roleText(u.role) }}</span></td>
                <td>{{ u.department || '--' }}</td>
                <td>
                  <span :class="u.status === 'ENABLED' ? 'tag-ok' : 'tag-warn'">{{ statusText(u.status) }}</span>
                </td>
                <td class="time">{{ u.lastLoginTime || '--' }}</td>
                <td>
                  <div class="user-actions">
                    <button @click="editUser(u)" class="btn-mini">编辑</button>
                    <input v-if="passwordForm.userId === u.id" v-model="passwordForm.password" type="password" placeholder="新密码" class="mini-password" />
                    <button @click="resetPassword(u)" class="btn-mini">{{ passwordForm.userId === u.id ? '确认' : '重置密码' }}</button>
                    <button @click="disableUser(u)" class="btn-mini-danger" :disabled="u.id === authUser.id">禁用</button>
                    <button @click="deleteUser(u)" class="btn-mini-danger hard" :disabled="u.id === authUser.id">删除</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
    </div>
  </div>
</template>


<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap');

:global(*) {
  box-sizing: border-box;
}

:global(:root) {
  --ink: #172033;
  --muted: #607087;
  --line: #dce6ef;
  --page: #f2f6f8;
  --panel: #ffffff;
  --blue: #2454d6;
  --blue-quiet: #e8efff;
  --mint: #91cfc1;
  --sage: #5a7c6a;
  --rose: #e8a5b7;
  --amber: #f2b95e;
  --shadow: 0 18px 45px rgba(54, 74, 101, 0.12);
}

:global(body) {
  margin: 0;
  min-height: 100vh;
  background:
    linear-gradient(135deg, rgba(168, 212, 196, 0.22) 0 17%, transparent 17% 100%),
    linear-gradient(90deg, rgba(36, 84, 214, 0.035) 1px, transparent 1px),
    linear-gradient(0deg, rgba(36, 84, 214, 0.028) 1px, transparent 1px),
    var(--page);
  background-size: auto, 36px 36px, 36px 36px, auto;
  color: var(--ink);
  font-family: "Plus Jakarta Sans", "PingFang SC", "Microsoft YaHei", sans-serif;
}

:global(#app) {
  min-height: 100vh;
}

.container {
  width: min(1440px, calc(100vw - 40px));
  margin: 0 auto;
  padding: 30px 0 44px;
}

.login-shell {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: 32px;
}

.login-panel {
  display: grid;
  grid-template-columns: minmax(0, 1.18fr) minmax(420px, 0.82fr);
  width: min(1220px, calc(100vw - 96px));
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: #fff;
  box-shadow: var(--shadow);
}

.login-brand {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 620px;
  padding: 56px;
  background:
    linear-gradient(135deg, rgba(36, 84, 214, 0.12), rgba(145, 207, 193, 0.24)),
    #f8fbff;
}

.login-brand h1 {
  margin: 22px 0 12px;
  color: var(--ink);
  font-size: 42px;
  line-height: 1.18;
  font-weight: 850;
}

.login-brand p {
  max-width: 560px;
  margin: 0;
  color: var(--muted);
  font-size: 17px;
  font-weight: 650;
}

.login-image-frame {
  position: relative;
  overflow: hidden;
  margin-top: 34px;
  border: 1px solid rgba(36, 84, 214, 0.14);
  border-radius: 12px;
  box-shadow: 0 18px 38px rgba(54, 74, 101, 0.16);
}

.login-image-frame img {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 10;
  object-fit: cover;
}

.login-form {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 20px;
  padding: 56px 48px;
}

.login-form label {
  display: grid;
  gap: 10px;
  color: var(--muted);
  font-size: 14px;
  font-weight: 800;
}

.login-form input {
  min-height: 52px;
  padding: 12px 14px;
  font-size: 15px;
}

.login-button {
  width: 100%;
  min-height: 50px;
  margin-top: 8px;
  font-size: 15px;
}

.header {
  position: sticky;
  top: 0;
  z-index: 20;
  margin-bottom: 24px;
  padding: 18px 0 22px;
  background: rgba(238, 245, 247, 0.86);
  backdrop-filter: blur(16px) saturate(1.1);
}

.title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 18px;
}

.brand-block {
  display: grid;
  gap: 6px;
}

.brand-kicker {
  width: max-content;
  padding: 5px 10px;
  border: 1px solid rgba(36, 84, 214, 0.16);
  border-radius: 999px;
  background: rgba(255, 253, 249, 0.72);
  color: var(--blue);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.title-row h1 {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0;
  color: var(--ink);
  font-size: 30px;
  font-weight: 800;
  letter-spacing: 0;
}

.title-row h1 small {
  color: var(--muted);
  font-size: 13px;
  font-weight: 600;
}

.top-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

.connection-pill {
  display: inline-flex;
  align-items: center;
  min-height: 42px;
  padding: 0 14px;
  border: 1px solid rgba(18, 183, 106, 0.28);
  border-radius: 999px;
  background: #ecfdf3;
  color: #067647;
  font-size: 13px;
  font-weight: 800;
  white-space: nowrap;
}

.connection-pill.offline {
  border-color: rgba(217, 45, 32, 0.25);
  background: #fff1f0;
  color: #b42318;
}

.user-select,
.user-profile {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 220px;
  padding: 12px 16px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--panel);
  box-shadow: 0 12px 28px rgba(54, 74, 101, 0.08);
  color: var(--muted);
  font-size: 14px;
}

.user-profile {
  min-width: 260px;
}

.user-profile span,
.user-profile small {
  color: var(--muted);
  font-size: 12px;
  font-weight: 800;
}

.user-profile strong {
  color: var(--ink);
  font-size: 14px;
  font-weight: 850;
}

.user-select select {
  width: auto;
  min-width: 88px;
  padding: 4px 22px 4px 4px;
  border: 0;
  background: transparent;
  color: var(--blue);
  font-weight: 700;
  cursor: pointer;
}

.tabs {
  display: inline-flex;
  gap: 6px;
  padding: 6px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--panel);
  box-shadow: 0 10px 24px rgba(54, 74, 101, 0.08);
}

.nav-rail {
  position: relative;
}

.nav-rail::after {
  content: "";
  position: absolute;
  right: -22px;
  top: 6px;
  width: 10px;
  height: calc(100% - 12px);
  border-radius: 999px;
  background: linear-gradient(180deg, var(--rose), var(--mint), #9b8dc4);
}

.tabs button {
  min-height: 42px;
  padding: 0 20px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--muted);
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0;
  cursor: pointer;
  transition: background 0.18s ease, color 0.18s ease, box-shadow 0.18s ease;
}

.tabs button:hover {
  background: #f4f8fb;
  color: var(--ink);
}

.tabs button.active {
  background: var(--blue);
  color: #fff;
  box-shadow: 0 8px 18px rgba(36, 84, 214, 0.24);
}

.command-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin: -8px 0 22px;
}

.command-item {
  display: grid;
  gap: 6px;
  min-height: 76px;
  padding: 14px 16px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--panel);
  box-shadow: 0 10px 22px rgba(54, 74, 101, 0.06);
}

.command-item span {
  color: var(--muted);
  font-size: 12px;
  font-weight: 800;
}

.command-item strong {
  color: var(--ink);
  font-size: 17px;
  font-weight: 850;
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
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
  margin-bottom: 18px;
}

.stat-cards .card {
  position: relative;
  overflow: hidden;
  min-height: 150px;
  padding: 24px;
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 10px;
  color: #fff;
  box-shadow: var(--shadow);
}

.stat-cards .card::after {
  content: "";
  position: absolute;
  right: 20px;
  top: 18px;
  width: 88px;
  height: 18px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.22);
  box-shadow: 0 30px 0 rgba(255, 255, 255, 0.14), 0 60px 0 rgba(255, 255, 255, 0.10);
}

.card h3 {
  margin: 0 0 20px;
  font-size: 15px;
  font-weight: 700;
  opacity: 1;
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

.stat-cards .card p {
  position: relative;
  z-index: 1;
  margin: 16px 0 0;
  color: rgba(255, 255, 255, 0.84);
  font-size: 13px;
  font-weight: 700;
}

.stat-blue { background: linear-gradient(135deg, #1745c8 0%, #0d8fa0 100%); }
.stat-green { background: linear-gradient(135deg, #06724f 0%, #36ae8d 100%); }
.stat-amber { background: linear-gradient(135deg, #b55f00 0%, #e7a848 100%); }
.stat-red { background: linear-gradient(135deg, #c92f4f 0%, #ec819a 100%); }

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
.expiry-alert-box,
.recent-card {
  border: 1px solid #dfe7f0;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 14px 34px rgba(54, 74, 101, 0.08);
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
  padding: 20px;
  border-left: 4px solid #d94860;
}

.risk-board {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(340px, 0.75fr);
  gap: 18px;
  margin-top: 18px;
}

.recent-card {
  padding: 20px;
  border-left: 4px solid var(--blue);
}

.expiry-alert-box h3,
.table-card h3,
.right-col h3,
.recent-card h3,
.box h3,
.task-list h4 {
  margin: 0 0 16px;
  color: var(--ink);
  font-size: 16px;
  font-weight: 800;
}

.compact-log {
  display: grid;
  grid-template-columns: 78px minmax(90px, 1fr) minmax(110px, 1fr);
  gap: 10px;
  align-items: center;
  padding: 11px 0;
  border-bottom: 1px solid #edf1f5;
  font-size: 13px;
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

.in-box { border-top: 4px solid var(--blue); }
.out-box { border-top: 4px solid var(--amber); }
.split-box { border-top: 4px solid #12b76a; }

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
  color: var(--ink);
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
  border-color: var(--blue);
  box-shadow: 0 0 0 3px rgba(36, 84, 214, 0.13);
}

.scan-input,
.scan-input-lg,
.highlight-input {
  border-color: #9bb4ff;
  background: linear-gradient(180deg, #fbfcff, #f3f7ff);
  font-weight: 650;
}

button {
  letter-spacing: 0;
}

.btn-primary,
.btn-warning,
.btn-scan-confirm,
.patient-search button,
.btn-logout,
.btn-subtle,
.btn-mini {
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
  background: var(--blue);
  box-shadow: 0 8px 16px rgba(36, 84, 214, 0.22);
}

.btn-warning {
  background: #c96f00;
  box-shadow: 0 8px 16px rgba(217, 119, 6, 0.22);
}

.btn-logout,
.btn-subtle,
.btn-mini {
  padding: 0 14px;
  border: 1px solid #d9e2ec;
  background: #fff;
  color: var(--ink);
  box-shadow: none;
}

.btn-mini {
  min-height: 30px;
  padding: 0 10px;
  font-size: 12px;
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
  color: var(--muted);
  font-size: 13px;
  font-weight: 700;
}

.toggle input:checked + span {
  border-color: var(--blue);
  background: var(--blue-quiet);
  color: var(--blue);
}

.split-toggle {
  width: max-content;
}

.split-result {
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 1px solid #abefc6;
  border-radius: 8px;
  background: #ecfdf3;
}

.split-result span {
  color: #067647;
  font-size: 12px;
  font-weight: 800;
}

.split-result small {
  color: #344054;
  font-weight: 700;
}

.table-card {
  overflow: hidden;
}

.user-admin-layout {
  display: grid;
  grid-template-columns: minmax(320px, 0.55fr) minmax(0, 1.45fr);
  gap: 18px;
}

.user-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.mini-password {
  width: 120px;
  min-height: 30px;
  padding: 5px 8px;
  font-size: 12px;
}

button:disabled,
input:disabled {
  cursor: not-allowed;
  opacity: 0.58;
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
  color: var(--muted);
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
  color: var(--blue);
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
  color: var(--blue);
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
  background: var(--blue-quiet);
  color: var(--blue);
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
  background: linear-gradient(135deg, #f8fbff, #f1f7f4);
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

.btn-mini-danger.hard {
  background: #7f1d1d;
}

.btn-danger-wide {
  min-height: 42px;
  border: 0;
  border-radius: 7px;
  background: #7f1d1d;
  color: #fff;
  font-size: 14px;
  font-weight: 800;
  cursor: pointer;
  box-shadow: 0 8px 16px rgba(127, 29, 29, 0.18);
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
  color: var(--muted);
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
  background: rgba(238, 245, 247, 0.68);
  backdrop-filter: blur(4px);
}

.spinner {
  width: 44px;
  height: 44px;
  border: 4px solid rgba(36, 84, 214, 0.14);
  border-top-color: var(--blue);
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
  .command-strip,
  .risk-board,
  .user-admin-layout,
  .nurse-layout {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .charts-row,
  .action-panel,
  .risk-board,
  .user-admin-layout,
  .nurse-layout {
    grid-template-columns: 1fr;
  }

  .stat-cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
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

  .brand-kicker {
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .top-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .user-select,
  .user-profile,
  .connection-pill,
  .tabs {
    width: 100%;
  }

  .connection-pill {
    justify-content: center;
  }

  .nav-rail::after {
    display: none;
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

  .command-strip {
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

  .compact-log {
    grid-template-columns: 1fr;
  }

  .login-panel {
    grid-template-columns: 1fr;
    width: min(100%, 720px);
  }

  .login-brand {
    min-height: auto;
    padding: 28px;
  }

  .login-brand h1 {
    font-size: 26px;
  }

  .login-form {
    padding: 28px;
  }

  .login-form input {
    min-height: 46px;
  }

  .user-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .mini-password {
    width: 100%;
  }
}
</style>

