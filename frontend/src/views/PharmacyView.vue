<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import {
  ArchiveRestore,
  Boxes,
  CheckCircle2,
  CircleAlert,
  PackageOpen,
  RefreshCw,
  ScanLine,
  Search,
  Split,
  Trash2
} from 'lucide-vue-next'
import { errorMessage } from '../api/client'
import AppDrawer from '../components/ui/AppDrawer.vue'

const props = defineProps({
  api: { type: Object, required: true },
  notify: { type: Function, required: true },
  confirmAction: { type: Function, required: true },
  user: { type: Object, required: true }
})

const emit = defineEmits(['synced'])
const loading = ref(true)
const saving = ref(false)
const drugs = ref([])
const records = ref([])
const catalog = ref([])
const search = ref('')
const tableMode = ref('stock')
const drawerTask = ref('')
const drawerSnapshot = ref('')
const lastSplitCode = ref(null)
const traceInput = ref(null)

const inboundForm = reactive({
  catalogId: '',
  drugName: '',
  traceCode: '',
  batchNumber: '',
  expireDate: '',
  isSplitAllowed: false,
  packageUnit: '盒',
  minUnit: '片',
  minUnitsPerPackage: 1
})
const outboundForm = reactive({ reason: '过期/破损报废', quantity: 1, traceCode: '' })
const splitForm = reactive({ parentTraceCode: '', splitUnits: 1 })

const controlCategoryLabels = {
  NARCOTIC: '麻醉药品',
  PSYCHOTROPIC_I: '第一类精神药品',
  PSYCHOTROPIC_II: '第二类精神药品',
  MEDICAL_TOXIC: '医疗用毒性药品'
}
const selectedInboundCatalog = computed(() => catalog.value.find(item => item.id === Number(inboundForm.catalogId)) || null)
const isSpecialCategory = category => Boolean(category && category !== 'GENERAL')
const applyInboundCatalog = () => {
  const entry = selectedInboundCatalog.value
  if (!entry) {
    inboundForm.drugName = ''
    return
  }
  inboundForm.drugName = entry.drugName
  inboundForm.isSplitAllowed = Boolean(entry.isSplitAllowed)
  inboundForm.packageUnit = entry.packageUnit || '盒'
  inboundForm.minUnit = entry.minUnit || entry.packageUnit || '盒'
  inboundForm.minUnitsPerPackage = entry.minUnitsPerPackage || 1
}

const newRequestId = action => {
  const unique = crypto?.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`
  return `${action}-${unique}`
}

const activeForm = () => {
  if (drawerTask.value === 'inbound') return inboundForm
  if (drawerTask.value === 'outbound') return outboundForm
  if (drawerTask.value === 'split') return splitForm
  return {}
}

const drawerDirty = computed(() => Boolean(drawerTask.value) && JSON.stringify(activeForm()) !== drawerSnapshot.value)
const drawerTitle = computed(() => ({ inbound: '药品扫码入库', outbound: '质量控制与损耗登记', split: '拆零建码' }[drawerTask.value] || '办理药事任务'))
const drawerDescription = computed(() => ({
  inbound: '从药品档案选择品种，特殊管理属性与包装规则将自动带入',
  outbound: '库存核对通过后登记损耗或科室领用',
  split: '根据母包装追溯码生成可独立调剂的拆零子码'
}[drawerTask.value] || ''))

const filteredDrugs = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  if (!keyword) return drugs.value
  return drugs.value.filter(item => [item.drugName, item.traceCode, item.batchNumber, item.locationCode]
    .some(value => String(value || '').toLowerCase().includes(keyword)))
})

const stockMetrics = computed(() => ({
  total: drugs.value.length,
  available: drugs.value.filter(item => item.quantity > 0).length,
  low: drugs.value.filter(item => item.quantity > 0 && item.quantity < 50).length,
  expired: drugs.value.filter(item => item.expireDate && new Date(item.expireDate) < new Date() && item.quantity > 0).length
}))

const dateOnly = value => value ? String(value).split('T')[0] : '--'
const isNearExpiry = value => {
  if (!value) return false
  const days = (new Date(value) - new Date()) / 86400000
  return days >= 0 && days <= 90
}
const packageText = item => {
  const packageUnit = item.packageUnit || item.unit || '盒'
  const minUnit = item.minUnit || packageUnit
  const perPackage = item.minUnitsPerPackage || 1
  return perPackage <= 1 || packageUnit === minUnit ? packageUnit : `1${packageUnit}=${perPackage}${minUnit}`
}
const splitStockText = item => item.isSplitAllowed
  ? `${item.remainingMinUnits ?? 0}/${item.minUnitsPerPackage ?? 1}${item.minUnit || '单位'}`
  : '整包装'

const load = async () => {
  loading.value = true
  try {
    const [drugResponse, recordResponse, catalogResponse] = await Promise.all([
      props.api.get('/list'),
      props.api.get('/records/recent?limit=50').catch(() => props.api.get('/records')),
      props.api.get('/catalog')
    ])
    drugs.value = drugResponse.data || []
    records.value = recordResponse.data || []
    catalog.value = (catalogResponse.data || []).filter(item => item.status === 'ENABLED')
    emit('synced', new Date())
  } catch (error) {
    props.notify(errorMessage(error, '药库数据加载失败'), 'error')
  } finally {
    loading.value = false
  }
}

const openTask = async task => {
  if (drawerTask.value && drawerDirty.value) {
    const proceed = await props.confirmAction({
      title: '切换药事任务',
      message: '当前表单有未提交内容，切换后这些内容将被保留，但不会自动保存。',
      confirmLabel: '继续切换',
      tone: 'warning'
    })
    if (!proceed) return
  }
  drawerTask.value = task
  drawerSnapshot.value = JSON.stringify(activeForm())
  await nextTick()
  traceInput.value?.focus()
}

const closeDrawer = async () => {
  if (drawerDirty.value) {
    const proceed = await props.confirmAction({
      title: '关闭任务表单',
      message: '表单中有尚未提交的内容，确认关闭吗？',
      confirmLabel: '放弃并关闭',
      tone: 'warning'
    })
    if (!proceed) return
  }
  drawerTask.value = ''
  lastSplitCode.value = null
}

const saveInbound = async () => {
  if (!Number(inboundForm.catalogId) || !inboundForm.traceCode.trim()) {
    return props.notify('请选择药品档案并扫描追溯码', 'error')
  }
  saving.value = true
  try {
    const response = await props.api.post('/add', {
      requestId: newRequestId('inbound'),
      ...inboundForm,
      catalogId: Number(inboundForm.catalogId),
      drugName: inboundForm.drugName.trim(),
      traceCode: inboundForm.traceCode.trim(),
      batchNumber: `${inboundForm.batchNumber.trim()} (入:${props.user.displayName || props.user.username})`,
      quantity: 1
    })
    if (typeof response.data === 'string' && response.data.includes('失败')) {
      return props.notify(response.data, 'error')
    }
    props.notify(`已完成 ${inboundForm.drugName} 单品入库`, 'success')
    inboundForm.traceCode = ''
    inboundForm.expireDate = ''
    drawerSnapshot.value = JSON.stringify(inboundForm)
    await load()
    await nextTick()
    traceInput.value?.focus()
  } catch (error) {
    props.notify(errorMessage(error, '入库失败'), 'error')
  } finally {
    saving.value = false
  }
}

const saveOutbound = async () => {
  const traceCode = outboundForm.traceCode.trim()
  if (!traceCode) return props.notify('请扫描需要处理的药品追溯码', 'error')
  const stock = drugs.value.find(item => item.traceCode === traceCode)
  if (!stock) return props.notify('库存中未找到该追溯码', 'error')
  if ((stock.quantity || 0) <= 0) return props.notify('该单品已出库或库存状态异常', 'error')
  const confirmed = await props.confirmAction({
    title: '确认库存处置',
    message: `${stock.drugName}\n数量：${outboundForm.quantity}\n原因：${outboundForm.reason}`,
    confirmLabel: '确认处理',
    tone: 'danger'
  })
  if (!confirmed) return
  saving.value = true
  try {
    await props.api.post('/dispense', {
      requestId: newRequestId('qc'),
      traceCode,
      patientId: `【质控】${outboundForm.reason} [${props.user.displayName || props.user.username}]`,
      quantity: String(outboundForm.quantity || 1)
    })
    props.notify(`质控处理完成：${stock.drugName}`, 'success')
    outboundForm.traceCode = ''
    outboundForm.quantity = 1
    drawerSnapshot.value = JSON.stringify(outboundForm)
    await load()
    await nextTick()
    traceInput.value?.focus()
  } catch (error) {
    props.notify(errorMessage(error, '质控处理失败'), 'error')
  } finally {
    saving.value = false
  }
}

const saveSplit = async () => {
  const parentTraceCode = splitForm.parentTraceCode.trim()
  const splitUnits = Number(splitForm.splitUnits)
  if (!parentTraceCode) return props.notify('请扫描母包装追溯码', 'error')
  if (!Number.isInteger(splitUnits) || splitUnits <= 0) return props.notify('请输入正确的拆零数量', 'error')
  saving.value = true
  try {
    const response = await props.api.post('/split/create', {
      requestId: newRequestId('split'),
      parentTraceCode,
      splitUnits: String(splitUnits)
    })
    lastSplitCode.value = response.data
    splitForm.parentTraceCode = ''
    splitForm.splitUnits = 1
    drawerSnapshot.value = JSON.stringify(splitForm)
    props.notify(`拆零子码已生成：${response.data.childTraceCode}`, 'success')
    await load()
    await nextTick()
    traceInput.value?.focus()
  } catch (error) {
    props.notify(errorMessage(error, '拆零建码失败'), 'error')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="page-stack" aria-labelledby="pharmacy-heading">
    <header class="page-header">
      <div><h2 id="pharmacy-heading">药库质控工作台</h2><p>执行单品入库、库存处置和拆零建码</p></div>
      <div class="page-actions"><button type="button" class="button secondary" :disabled="loading" @click="load"><RefreshCw :size="17" />刷新库存</button></div>
    </header>

    <div class="metric-grid">
      <div class="metric-card"><span>库存单品</span><strong>{{ stockMetrics.total }}</strong><small>全部追溯码记录</small><span class="metric-icon"><Boxes :size="19" /></span></div>
      <div class="metric-card"><span>在库可用</span><strong>{{ stockMetrics.available }}</strong><small>可继续调剂</small><span class="metric-icon success"><CheckCircle2 :size="19" /></span></div>
      <div class="metric-card"><span>低库存</span><strong>{{ stockMetrics.low }}</strong><small>低于 50 个单位</small><span class="metric-icon warning"><CircleAlert :size="19" /></span></div>
      <div class="metric-card"><span>过期在库</span><strong>{{ stockMetrics.expired }}</strong><small>必须停止发放</small><span class="metric-icon danger"><Trash2 :size="19" /></span></div>
    </div>

    <section class="task-launcher" aria-labelledby="task-title">
      <div><h3 id="task-title">办理药事任务</h3><p>选择任务后在右侧工作区连续扫码处理</p></div>
      <div class="task-buttons">
        <button type="button" @click="openTask('inbound')"><span><ArchiveRestore :size="18" /></span><strong>扫码入库</strong><small>登记批号、效期和包装</small></button>
        <button type="button" @click="openTask('outbound')"><span class="warning"><PackageOpen :size="18" /></span><strong>库存处置</strong><small>报废、领用和盘点修正</small></button>
        <button type="button" @click="openTask('split')"><span class="teal"><Split :size="18" /></span><strong>拆零建码</strong><small>生成可调剂拆零子码</small></button>
      </div>
    </section>

    <section class="data-panel">
      <div class="toolbar inventory-toolbar">
        <div class="toolbar-group">
          <label class="search-control"><Search :size="16" /><span class="sr-only">搜索库存</span><input v-model="search" aria-label="搜索药库库存" placeholder="搜索药品、追溯码、批号或货位" /></label>
          <span v-if="tableMode === 'stock'" class="status-badge" data-tone="neutral">{{ filteredDrugs.length }} 条</span>
        </div>
        <div class="segmented-control" aria-label="数据视图">
          <button type="button" :class="{ active: tableMode === 'stock' }" @click="tableMode = 'stock'">库存明细</button>
          <button type="button" :class="{ active: tableMode === 'records' }" @click="tableMode = 'records'">最近流水</button>
        </div>
      </div>

      <div v-if="loading" class="loading-state"><span class="spinner"></span><span>正在加载药库数据</span></div>
      <div v-else-if="tableMode === 'stock' && filteredDrugs.length" class="table-scroll stock-table">
        <table>
          <caption>药库库存与质控明细</caption>
          <thead><tr><th>药品</th><th>追溯码</th><th>包装</th><th>拆零库存</th><th>货位</th><th>批号/操作人</th><th>库存</th><th>效期状态</th><th>有效期</th></tr></thead>
          <tbody>
            <tr v-for="item in filteredDrugs" :key="item.id">
              <td><strong>{{ item.drugName }}</strong><span v-if="isSpecialCategory(item.controlCategory)" class="status-badge stock-control-badge" :data-tone="item.controlCategory === 'PSYCHOTROPIC_II' ? 'warning' : 'danger'">{{ controlCategoryLabels[item.controlCategory] || item.controlCategory }}</span></td>
              <td class="mono">{{ item.traceCode }}</td>
              <td>{{ packageText(item) }}</td>
              <td>{{ splitStockText(item) }}</td>
              <td>{{ item.locationCode || '待上架' }}</td>
              <td class="truncate" :title="item.batchNumber">{{ item.batchNumber || '--' }}</td>
              <td><strong>{{ item.quantity }}</strong></td>
              <td><span class="status-badge" :data-tone="isNearExpiry(item.expireDate) ? 'warning' : 'success'">{{ isNearExpiry(item.expireDate) ? '近效期' : '合格' }}</span></td>
              <td class="nowrap">{{ dateOnly(item.expireDate) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="tableMode === 'records' && records.length" class="table-scroll">
        <table>
          <caption>最近药品出入库流水</caption>
          <thead><tr><th>时间</th><th>药品</th><th>患者或业务对象</th><th>申请编号</th><th>追溯码</th></tr></thead>
          <tbody>
            <tr v-for="record in records" :key="record.id">
              <td class="nowrap">{{ record.dispenseTime || '--' }}</td><td><strong>{{ record.drugName }}</strong></td><td>{{ record.patientName || record.patientId || '--' }}</td><td class="mono">{{ record.prescriptionId || '--' }}</td><td class="mono">{{ record.traceCode || record.traceCodeDispensed || '--' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state"><Boxes :size="26" /><span>{{ search ? '没有匹配的库存记录' : '暂无业务数据' }}</span></div>
    </section>

    <AppDrawer :open="Boolean(drawerTask)" :title="drawerTitle" :description="drawerDescription" width="520px" @close="closeDrawer">
      <form v-if="drawerTask === 'inbound'" class="form-stack" @submit.prevent="saveInbound">
        <div class="form-grid">
          <label class="field span-2"><span>药品档案</span><select v-model="inboundForm.catalogId" aria-label="入库药品档案" required @change="applyInboundCatalog"><option value="">请选择已启用的药品档案</option><option v-for="entry in catalog" :key="entry.id" :value="entry.id">{{ entry.drugName }} · {{ entry.specification || '--' }}</option></select></label>
          <div v-if="selectedInboundCatalog" class="inbound-catalog-summary span-2"><div><strong>{{ selectedInboundCatalog.drugName }}</strong><small>{{ selectedInboundCatalog.specification || '--' }} · {{ selectedInboundCatalog.dosageForm || '--' }} · {{ packageText(selectedInboundCatalog) }}</small></div><span v-if="isSpecialCategory(selectedInboundCatalog.controlCategory)" class="status-badge" :data-tone="selectedInboundCatalog.controlCategory === 'PSYCHOTROPIC_II' ? 'warning' : 'danger'">{{ controlCategoryLabels[selectedInboundCatalog.controlCategory] || selectedInboundCatalog.controlCategory }}</span></div>
          <label class="field"><span>生产批号</span><input v-model="inboundForm.batchNumber" aria-label="生产批号" placeholder="请输入生产批号" /></label>
          <label class="field"><span>有效期</span><input v-model="inboundForm.expireDate" aria-label="药品有效期" type="date" /></label>
          <label class="field span-2"><span>药品追溯码</span><input ref="traceInput" v-model="inboundForm.traceCode" class="mono" aria-label="入库药品追溯码" required placeholder="扫描或输入单品追溯码" @keyup.enter="saveInbound" /></label>
        </div>
      </form>

      <form v-else-if="drawerTask === 'outbound'" class="form-stack" @submit.prevent="saveOutbound">
        <label class="field"><span>处置原因</span><select v-model="outboundForm.reason" aria-label="库存处置原因"><option>过期/破损报废</option><option>科室基数药领用</option><option>库存盘点修正</option></select></label>
        <label class="field"><span>处置数量</span><input v-model.number="outboundForm.quantity" aria-label="库存处置数量" type="number" min="1" /></label>
        <label class="field"><span>药品追溯码</span><input ref="traceInput" v-model="outboundForm.traceCode" class="mono" aria-label="处置药品追溯码" placeholder="扫描需要处置的药品" @keyup.enter="saveOutbound" /></label>
        <div class="notice warning"><CircleAlert :size="17" /><span>确认后将立即扣减库存并写入审计流水。</span></div>
      </form>

      <form v-else-if="drawerTask === 'split'" class="form-stack" @submit.prevent="saveSplit">
        <label class="field"><span>母包装追溯码</span><input ref="traceInput" v-model="splitForm.parentTraceCode" class="mono" aria-label="母包装追溯码" placeholder="扫描允许拆零的母包装" @keyup.enter="saveSplit" /></label>
        <label class="field"><span>拆零最小单位数量</span><input v-model.number="splitForm.splitUnits" aria-label="拆零最小单位数量" type="number" min="1" /></label>
        <div v-if="lastSplitCode" class="split-result">
          <span>新生成子码</span><strong class="mono">{{ lastSplitCode.childTraceCode }}</strong><small>{{ lastSplitCode.drugName }} · {{ lastSplitCode.splitUnits }}{{ lastSplitCode.minUnit }} · 母包装剩余 {{ lastSplitCode.remainingParentUnits }}{{ lastSplitCode.minUnit }}</small>
        </div>
      </form>

      <template #footer>
        <button type="button" class="button secondary" @click="closeDrawer">关闭</button>
        <button v-if="drawerTask === 'inbound'" type="button" class="button primary" :disabled="saving" @click="saveInbound"><ScanLine :size="17" />{{ saving ? '正在入库' : '确认入库' }}</button>
        <button v-else-if="drawerTask === 'outbound'" type="button" class="button danger" :disabled="saving" @click="saveOutbound"><PackageOpen :size="17" />{{ saving ? '正在处理' : '确认处理' }}</button>
        <button v-else-if="drawerTask === 'split'" type="button" class="button primary" :disabled="saving" @click="saveSplit"><Split :size="17" />{{ saving ? '正在生成' : '生成子码' }}</button>
      </template>
    </AppDrawer>
  </section>
</template>

<style scoped>
.task-launcher { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 13px 15px; }
.task-launcher h3 { margin: 0; font-size: 14px; }
.task-launcher p { margin: 2px 0 0; color: var(--muted); font-size: 11px; }
.task-buttons { display: flex; align-items: stretch; gap: 8px; }
.task-buttons button { display: grid; min-width: 190px; grid-template-columns: 36px minmax(0, 1fr); grid-template-rows: auto auto; align-items: center; gap: 0 9px; padding: 7px 10px; border: 1px solid var(--line); border-radius: var(--radius); background: #fff; text-align: left; }
.task-buttons button:hover { border-color: #9cb8c7; background: var(--panel-subtle); }
.task-buttons button > span:first-child { display: grid; width: 36px; height: 36px; grid-row: 1 / 3; place-items: center; border-radius: var(--radius); background: var(--primary-soft); color: var(--primary); }
.task-buttons button > span.warning { background: var(--warning-soft); color: var(--warning); }
.task-buttons button > span.teal { background: var(--teal-soft); color: var(--teal); }
.task-buttons strong { font-size: 12px; }
.task-buttons small { color: var(--muted); font-size: 10px; }
.inventory-toolbar { border: 0; border-bottom: 1px solid var(--line); border-radius: 0; box-shadow: none; }
.stock-table { max-height: calc(100vh - 430px); }
.stock-control-badge { margin-left: 7px; vertical-align: 1px; }
.inbound-catalog-summary { display: flex; min-width: 0; align-items: center; justify-content: space-between; gap: 12px; padding: 10px 11px; border: 1px solid var(--line); border-radius: var(--radius); background: var(--panel-subtle); }
.inbound-catalog-summary > div { display: grid; min-width: 0; gap: 3px; }
.inbound-catalog-summary strong { font-size: 12px; }
.inbound-catalog-summary small { overflow: hidden; color: var(--muted); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.split-result { display: grid; gap: 5px; padding: 13px; border: 1px solid #beded5; border-radius: var(--radius); background: var(--success-soft); }
.split-result > span, .split-result small { color: var(--success); font-size: 11px; }
.split-result strong { font-size: 15px; overflow-wrap: anywhere; }
@media (max-width: 1280px) {
  .task-buttons button { min-width: 150px; }
  .task-buttons small { display: none; }
}
</style>
