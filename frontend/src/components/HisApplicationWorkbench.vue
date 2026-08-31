<script setup>
import { computed, onMounted, ref } from 'vue'
import { AlertTriangle, ArrowDownToLine, CheckCircle2, ClipboardList, RefreshCw, RotateCcw, Search, XCircle } from 'lucide-vue-next'

const props = defineProps({
  api: { type: Object, required: true },
  userRole: { type: String, default: '' }
})

const applications = ref([])
const selected = ref(null)
const catalog = ref([])
const loading = ref(false)
const message = ref({ text: '', type: 'success' })
const filters = ref({ keyword: '', status: '', priority: '' })
const traceCodes = ref({})
const mappingCatalogIds = ref({})
const reviewComment = ref('')

const canOperate = computed(() => ['ADMIN', 'PHARMACIST'].includes(props.userRole))
const metrics = computed(() => ({
  pending: applications.value.filter(item => ['RECEIVED', 'REVIEW_PENDING', 'READY'].includes(item.status)).length,
  urgent: applications.value.filter(item => item.priority === 'URGENT'
    && ['RECEIVED', 'MAPPING_REQUIRED', 'REVIEW_PENDING', 'READY', 'PARTIALLY_DISPENSED', 'RETURN_REQUIRED'].includes(item.status)).length,
  mapping: applications.value.filter(item => item.status === 'MAPPING_REQUIRED').length,
  partial: applications.value.filter(item => item.status === 'PARTIALLY_DISPENSED').length,
  callbackFailed: applications.value.filter(item => item.callbackStatus === 'FAILED').length
}))

const statusLabels = {
  RECEIVED: '已接收',
  MAPPING_REQUIRED: '待匹配',
  REVIEW_PENDING: '待审方',
  REVIEW_REJECTED: '审方未通过',
  READY: '待发药',
  PARTIALLY_DISPENSED: '部分发药',
  DISPENSED: '已发药',
  RETURN_REQUIRED: '待退药',
  CANCELLED: '已撤销',
  RETURNED: '已退药',
  UNMAPPED: '待匹配',
  PENDING: '待发药',
  PARTIAL: '部分发药'
}

const callbackLabels = {
  PENDING: '待回传', PROCESSING: '回传中', SENT: '已回传', FAILED: '回传失败'
}

const showMessage = (text, type = 'success') => {
  message.value = { text, type }
  window.setTimeout(() => { if (message.value.text === text) message.value.text = '' }, 3500)
}

const errorText = (error) => error?.response?.data?.message || error?.response?.data || error?.message || '操作失败'

const loadApplications = async (keepSelection = true) => {
  loading.value = true
  try {
    const response = await props.api.get('/api/pharmacy/applications', { params: filters.value })
    applications.value = response.data || []
    if (keepSelection && selected.value) {
      const stillVisible = applications.value.find(item => item.id === selected.value.id)
      if (stillVisible) await selectApplication(stillVisible)
    }
  } catch (error) {
    showMessage(errorText(error), 'error')
  } finally {
    loading.value = false
  }
}

const loadCatalog = async () => {
  if (!canOperate.value) return
  try {
    const response = await props.api.get('/catalog')
    catalog.value = (response.data || []).filter(item => item.status === 'ENABLED')
  } catch (error) {
    showMessage(errorText(error), 'error')
  }
}

const selectApplication = async (application) => {
  try {
    const response = await props.api.get(`/api/pharmacy/applications/${application.id}`)
    selected.value = response.data
  } catch (error) {
    showMessage(errorText(error), 'error')
  }
}

const saveMapping = async (item) => {
  const catalogId = Number(mappingCatalogIds.value[item.id])
  if (!catalogId) return showMessage('请选择本地药品档案', 'error')
  try {
    await props.api.post('/api/pharmacy/his-drug-mappings', {
      sourceSystem: selected.value.sourceSystem,
      hisDrugCode: item.hisDrugCode,
      localCatalogId: catalogId
    })
    showMessage('药品编码映射已保存')
    await loadApplications()
  } catch (error) {
    showMessage(errorText(error), 'error')
  }
}

const dispense = async (item) => {
  const traceCode = (traceCodes.value[item.id] || '').trim()
  if (!traceCode) return showMessage('请先扫描药品追溯码', 'error')
  loading.value = true
  try {
    const response = await props.api.post(`/api/pharmacy/application-items/${item.id}/dispense`, {
      requestId: `DISPENSE-${Date.now()}`,
      traceCode
    })
    selected.value = response.data
    traceCodes.value[item.id] = ''
    showMessage(`已完成 ${item.drugName} 本次发药`)
    await loadApplications(false)
  } catch (error) {
    showMessage(errorText(error), 'error')
  } finally {
    loading.value = false
  }
}

const returnDrug = async (item) => {
  const traceCode = (traceCodes.value[item.id] || '').trim()
  if (!traceCode) return showMessage('退药前请扫描原药品追溯码', 'error')
  loading.value = true
  try {
    const response = await props.api.post(`/api/pharmacy/application-items/${item.id}/return`, {
      requestId: `RETURN-${Date.now()}`,
      traceCode
    })
    selected.value = response.data
    traceCodes.value[item.id] = ''
    showMessage(`已完成 ${item.drugName} 本次退药`)
    await loadApplications(false)
  } catch (error) {
    showMessage(errorText(error), 'error')
  } finally {
    loading.value = false
  }
}

const review = async (decision) => {
  if (!selected.value) return
  if (decision === 'REJECTED' && !reviewComment.value.trim()) {
    return showMessage('审方不通过时必须填写原因', 'error')
  }
  loading.value = true
  try {
    const response = await props.api.post(`/api/pharmacy/applications/${selected.value.id}/review`, {
      decision,
      comment: reviewComment.value.trim()
    })
    selected.value = response.data
    reviewComment.value = ''
    showMessage(decision === 'APPROVED' ? '处方审核通过，可以进入调剂发药' : '处方已退回，禁止发药')
    await loadApplications(false)
  } catch (error) {
    showMessage(errorText(error), 'error')
  } finally {
    loading.value = false
  }
}

const progressText = (item) => `${item.dispensedQuantity || 0} / ${item.requestedQuantity} ${item.unit}`
const isDispensable = (item) => selected.value?.reviewStatus === 'APPROVED'
  && selected.value?.status !== 'RETURN_REQUIRED'
  && ['PENDING', 'PARTIAL'].includes(item.status)
const isReturnable = (item) => ['PARTIAL', 'DISPENSED'].includes(item.status) && (item.dispensedQuantity || 0) > 0

onMounted(async () => {
  await Promise.all([loadApplications(false), loadCatalog()])
})
</script>

<template>
  <section class="his-workbench">
    <header class="section-heading">
      <div>
        <span class="eyebrow">HIS OUTPATIENT QUEUE</span>
        <h2>门诊处方申请单</h2>
      </div>
      <button class="icon-button" title="刷新申请单" :disabled="loading" @click="loadApplications()">
        <RefreshCw :class="{ spinning: loading }" />
      </button>
    </header>

    <div class="metric-strip">
      <div><span>待处理</span><strong>{{ metrics.pending }}</strong></div>
      <div><span>急诊优先</span><strong class="urgent-text">{{ metrics.urgent }}</strong></div>
      <div><span>待匹配</span><strong>{{ metrics.mapping }}</strong></div>
      <div><span>部分发药</span><strong>{{ metrics.partial }}</strong></div>
      <div><span>回传异常</span><strong :class="{ 'danger-text': metrics.callbackFailed }">{{ metrics.callbackFailed }}</strong></div>
    </div>

    <div class="filter-bar">
      <label class="search-field"><Search /><input v-model="filters.keyword" placeholder="申请单号、患者姓名或患者编号" @keyup.enter="loadApplications(false)" /></label>
      <select v-model="filters.status" @change="loadApplications(false)">
        <option value="">全部状态</option>
        <option value="MAPPING_REQUIRED">待匹配</option>
        <option value="REVIEW_PENDING">待审方</option>
        <option value="REVIEW_REJECTED">审方未通过</option>
        <option value="READY">待发药</option>
        <option value="PARTIALLY_DISPENSED">部分发药</option>
        <option value="DISPENSED">已发药</option>
        <option value="RETURN_REQUIRED">待退药</option>
        <option value="RETURNED">已退药</option>
        <option value="CANCELLED">已撤销</option>
      </select>
      <select v-model="filters.priority" @change="loadApplications(false)">
        <option value="">全部优先级</option>
        <option value="URGENT">急诊</option>
        <option value="NORMAL">普通</option>
      </select>
      <button class="command-button" @click="loadApplications(false)"><Search />查询</button>
    </div>

    <div v-if="message.text" class="notice" :class="message.type">{{ message.text }}</div>

    <div class="workspace-grid">
      <div class="queue-panel">
        <div v-if="applications.length === 0" class="empty-state">
          <ClipboardList />
          <span>当前筛选条件下没有申请单</span>
        </div>
        <button
          v-for="application in applications"
          :key="application.id"
          class="queue-row"
          :class="{ selected: selected?.id === application.id }"
          @click="selectApplication(application)"
        >
          <span class="priority-mark" :class="application.priority?.toLowerCase()"></span>
          <span class="queue-main">
            <strong>{{ application.patientName }}</strong>
            <small>{{ application.hisApplicationNo }} · {{ application.patientId }}</small>
          </span>
          <span class="queue-meta">
            <b class="status-chip" :data-status="application.status">{{ statusLabels[application.status] || application.status }}</b>
            <small>{{ application.departmentName || '未标注科室' }}</small>
          </span>
        </button>
      </div>

      <div class="detail-panel">
        <div v-if="!selected" class="empty-state detail-empty">
          <ClipboardList />
          <span>选择一张申请单查看药品明细</span>
        </div>
        <template v-else>
          <div class="patient-band">
            <div>
              <span>患者</span>
              <strong>{{ selected.patientName }}</strong>
              <small>{{ selected.patientId }} · {{ selected.patientGender || '--' }} · {{ selected.patientAge ?? '--' }}岁 · 就诊号 {{ selected.encounterNo || '--' }}</small>
            </div>
            <div>
              <span>申请单</span>
              <strong class="mono">{{ selected.hisApplicationNo }}</strong>
              <small>版本 {{ selected.revisionNo }} · {{ selected.departmentName || '--' }}</small>
            </div>
            <div class="state-stack">
              <b class="status-chip large" :data-status="selected.status">{{ statusLabels[selected.status] || selected.status }}</b>
              <small :class="['callback-state', selected.callbackStatus?.toLowerCase()]">{{ callbackLabels[selected.callbackStatus] || '尚无回传' }}</small>
            </div>
          </div>

          <div class="clinical-band">
            <div><span>临床诊断</span><strong>{{ selected.diagnosis || '--' }}</strong></div>
            <div><span>处方医师</span><strong>{{ selected.prescriberName || '--' }} · {{ selected.prescriberId || '--' }}</strong></div>
            <div><span>过敏史</span><strong>{{ selected.allergyInfo || '--' }}</strong></div>
          </div>

          <div v-if="canOperate && selected.status === 'REVIEW_PENDING'" class="review-panel">
            <label><span>审方意见</span><input v-model="reviewComment" placeholder="审核通过可填写注意事项；不通过必须填写原因" /></label>
            <button class="approve-button" :disabled="loading" @click="review('APPROVED')"><CheckCircle2 />审核通过</button>
            <button class="reject-button" :disabled="loading" @click="review('REJECTED')"><XCircle />审核不通过</button>
          </div>
          <div v-else-if="selected.reviewedBy" class="review-result" :data-review="selected.reviewStatus">
            <strong>{{ selected.reviewStatus === 'APPROVED' ? '审方已通过' : '审方未通过' }}</strong>
            <span>{{ selected.reviewedBy }} · {{ selected.reviewedAt || '--' }} · {{ selected.reviewComment || '无补充意见' }}</span>
          </div>

          <div class="item-list">
            <article v-for="item in selected.items" :key="item.id" class="medicine-line">
              <div class="medicine-summary">
                <div>
                  <span class="item-code">{{ item.hisItemNo }} · {{ item.hisDrugCode }}</span>
                  <h3>{{ item.drugName }}</h3>
                  <small>{{ item.specification || '未标注规格' }} · 本地档案：{{ item.localDrugName || '未匹配' }}</small>
                  <small class="usage-line">{{ item.dosage }} · {{ item.frequency }} · {{ item.administrationRoute }}<template v-if="item.usageInstruction"> · {{ item.usageInstruction }}</template></small>
                </div>
                <div class="progress-block">
                  <span>{{ progressText(item) }}</span>
                  <div class="progress-track"><i :style="{ width: `${Math.min(100, ((item.dispensedQuantity || 0) / item.requestedQuantity) * 100)}%` }"></i></div>
                  <small v-if="item.returnedQuantity">累计退药 {{ item.returnedQuantity }} {{ item.unit }}</small>
                </div>
                <b class="status-chip" :data-status="item.status">{{ statusLabels[item.status] || item.status }}</b>
              </div>

              <div v-if="item.status === 'UNMAPPED'" class="mapping-row">
                <AlertTriangle />
                <span>需要先绑定本地药品档案</span>
                <select v-if="canOperate" v-model="mappingCatalogIds[item.id]">
                  <option value="">选择药品档案</option>
                  <option v-for="drug in catalog" :key="drug.id" :value="drug.id">{{ drug.drugName }} · {{ drug.specification || '无规格' }}</option>
                </select>
                <button v-if="canOperate" class="command-button" @click="saveMapping(item)">保存映射</button>
              </div>

              <div v-else-if="canOperate && (isDispensable(item) || isReturnable(item))" class="scan-action-row">
                <label><ArrowDownToLine /><input v-model="traceCodes[item.id]" placeholder="扫描药品追溯码" @keyup.enter="isDispensable(item) && dispense(item)" /></label>
                <button v-if="isDispensable(item)" class="dispense-button" :disabled="loading" @click="dispense(item)"><ArrowDownToLine />确认发药</button>
                <button v-if="isReturnable(item)" class="return-button" :disabled="loading" @click="returnDrug(item)"><RotateCcw />退药</button>
              </div>
            </article>
          </div>
        </template>
      </div>
    </div>
  </section>
</template>

<style scoped>
.his-workbench { display: grid; gap: 16px; color: #172033; }
.section-heading { display: flex; align-items: center; justify-content: space-between; padding: 4px 0; }
.section-heading h2 { margin: 4px 0 0; font-size: 24px; letter-spacing: 0; }
.eyebrow { color: #557080; font-size: 11px; font-weight: 800; }
.icon-button { width: 40px; height: 40px; border: 1px solid #d5dfe5; background: #fff; display: grid; place-items: center; cursor: pointer; border-radius: 6px; }
.icon-button svg, .command-button svg, .search-field svg, .scan-action-row svg { width: 17px; height: 17px; }
.spinning { animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.metric-strip { display: grid; grid-template-columns: repeat(5, minmax(110px, 1fr)); border: 1px solid #dce4e8; background: #fff; }
.metric-strip div { padding: 14px 16px; border-right: 1px solid #e4eaed; display: flex; align-items: baseline; justify-content: space-between; gap: 8px; }
.metric-strip div:last-child { border-right: 0; }
.metric-strip span { font-size: 12px; color: #687783; }
.metric-strip strong { font-size: 22px; }
.urgent-text { color: #b42318; }.danger-text { color: #b42318; }
.filter-bar { display: grid; grid-template-columns: minmax(260px, 1fr) 150px 130px auto; gap: 10px; }
.search-field, .scan-action-row label { border: 1px solid #cfd9df; background: #fff; display: flex; align-items: center; gap: 8px; padding: 0 12px; min-height: 40px; }
input, select { min-width: 0; border: 1px solid #cfd9df; background: #fff; color: #172033; padding: 10px 12px; font: inherit; border-radius: 4px; }
.search-field input, .scan-action-row input { border: 0; padding: 0; outline: 0; flex: 1; }
.command-button, .dispense-button, .return-button, .approve-button, .reject-button { min-height: 40px; border: 0; padding: 0 15px; border-radius: 4px; font-weight: 700; cursor: pointer; display: inline-flex; align-items: center; justify-content: center; gap: 7px; }
.command-button { background: #1d6675; color: #fff; }.dispense-button { background: #0e7557; color: #fff; }.return-button { background: #fff; color: #a4372b; border: 1px solid #d8a29c; }
.notice { padding: 10px 14px; border-left: 4px solid #2f8068; background: #eaf6f1; font-size: 13px; }.notice.error { border-color: #b42318; background: #fff0ee; color: #8e231a; }
.workspace-grid { display: grid; grid-template-columns: minmax(280px, 0.72fr) minmax(520px, 1.5fr); min-height: 560px; border: 1px solid #d8e1e6; background: #fff; }
.queue-panel { border-right: 1px solid #d8e1e6; overflow: auto; max-height: 68vh; }
.queue-row { width: 100%; display: grid; grid-template-columns: 4px 1fr auto; align-items: center; gap: 12px; padding: 14px 12px; border: 0; border-bottom: 1px solid #e7ecef; background: #fff; text-align: left; cursor: pointer; color: inherit; }
.queue-row:hover, .queue-row.selected { background: #eef7f7; }.queue-row.selected { box-shadow: inset 3px 0 #1d6675; }
.priority-mark { width: 4px; height: 34px; background: #aebbc2; }.priority-mark.urgent { background: #d04435; }
.queue-main, .queue-meta { display: grid; gap: 5px; }.queue-main strong { font-size: 15px; }.queue-main small, .queue-meta small { color: #71808b; font-size: 11px; }.queue-meta { justify-items: end; }
.detail-panel { min-width: 0; overflow: auto; max-height: 68vh; }
.empty-state { min-height: 180px; display: grid; place-items: center; align-content: center; gap: 10px; color: #82909a; }.empty-state svg { width: 28px; }.detail-empty { min-height: 520px; }
.patient-band { display: grid; grid-template-columns: 1fr 1.2fr auto; gap: 24px; padding: 18px 20px; border-bottom: 1px solid #dce4e8; background: #f7fafb; }
.patient-band > div { display: grid; gap: 4px; }.patient-band span, .patient-band small { color: #6b7a85; font-size: 11px; }.patient-band strong { font-size: 15px; }
.clinical-band { display: grid; grid-template-columns: 1.2fr 1fr 1fr; gap: 18px; padding: 13px 20px; border-bottom: 1px solid #dce4e8; }.clinical-band div { display: grid; gap: 4px; }.clinical-band span { color: #6b7a85; font-size: 11px; }.clinical-band strong { font-size: 13px; font-weight: 650; }
.review-panel { display: grid; grid-template-columns: minmax(260px, 1fr) auto auto; gap: 10px; align-items: end; padding: 14px 20px; border-bottom: 1px solid #e1e7ea; background: #fff9e8; }.review-panel label { display: grid; gap: 5px; }.review-panel label span { font-size: 11px; color: #6b7881; }.approve-button { background: #0e7557; color: #fff; }.reject-button { background: #fff; color: #a4372b; border: 1px solid #d8a29c; }.review-panel svg { width: 17px; }.review-result { display: flex; gap: 12px; align-items: center; padding: 11px 20px; border-bottom: 1px solid #dce4e8; background: #edf8f3; font-size: 12px; }.review-result[data-review="REJECTED"] { background: #fff0ee; color: #8e231a; }.review-result span { color: #65737c; }
.state-stack { justify-items: end; align-content: center; }.callback-state.failed { color: #b42318; }.callback-state.sent { color: #087a57; }
.item-list { display: grid; }.medicine-line { padding: 18px 20px; border-bottom: 1px solid #e2e8eb; }.medicine-line:last-child { border-bottom: 0; }
.medicine-summary { display: grid; grid-template-columns: minmax(220px, 1.3fr) minmax(160px, 0.7fr) auto; gap: 20px; align-items: center; }.medicine-summary h3 { margin: 4px 0; font-size: 16px; letter-spacing: 0; }.medicine-summary small, .item-code { color: #6b7881; font-size: 11px; }
.progress-block { display: grid; gap: 5px; font-size: 12px; }.progress-track { height: 6px; background: #e5ebee; overflow: hidden; }.progress-track i { display: block; height: 100%; background: #1d7a67; }
.status-chip { display: inline-flex; align-items: center; width: max-content; padding: 4px 8px; border-radius: 4px; background: #e7edf0; color: #4c5c66; font-size: 11px; white-space: nowrap; }.status-chip.large { padding: 6px 10px; }
.status-chip[data-status="READY"], .status-chip[data-status="PENDING"] { background: #fff2cc; color: #785900; }.status-chip[data-status="MAPPING_REQUIRED"], .status-chip[data-status="UNMAPPED"] { background: #ffe8d7; color: #944d16; }.status-chip[data-status="PARTIALLY_DISPENSED"], .status-chip[data-status="PARTIAL"] { background: #dcecf8; color: #225e86; }.status-chip[data-status="DISPENSED"] { background: #dff3e9; color: #176349; }.status-chip[data-status="CANCELLED"], .status-chip[data-status="RETURNED"] { background: #eceff1; color: #59656d; }
.status-chip[data-status="REVIEW_PENDING"] { background: #e8eef9; color: #38588c; }.status-chip[data-status="REVIEW_REJECTED"], .status-chip[data-status="RETURN_REQUIRED"] { background: #fde4e1; color: #982d24; }.usage-line { display: block; margin-top: 5px; color: #355f6e !important; }
.mapping-row, .scan-action-row { margin-top: 14px; display: flex; align-items: center; gap: 10px; padding-top: 14px; border-top: 1px dashed #d6dfe4; }.mapping-row svg { width: 18px; color: #b56a20; flex: none; }.mapping-row span { font-size: 12px; }.mapping-row select { margin-left: auto; min-width: 220px; }.scan-action-row label { flex: 1; }
.mono { font-family: Consolas, monospace; }
button:disabled { opacity: 0.55; cursor: wait; }
@media (max-width: 1050px) { .metric-strip { grid-template-columns: repeat(3, 1fr); }.metric-strip div { border-bottom: 1px solid #e4eaed; }.workspace-grid { grid-template-columns: 1fr; }.queue-panel { border-right: 0; border-bottom: 1px solid #d8e1e6; max-height: 320px; }.detail-panel { max-height: none; }.filter-bar { grid-template-columns: 1fr 1fr; } }
@media (max-width: 680px) { .metric-strip { grid-template-columns: 1fr 1fr; }.filter-bar, .patient-band, .clinical-band, .medicine-summary, .review-panel { grid-template-columns: 1fr; }.state-stack { justify-items: start; }.mapping-row, .scan-action-row { align-items: stretch; flex-direction: column; }.mapping-row select { margin-left: 0; min-width: 0; width: 100%; }.queue-row { grid-template-columns: 4px 1fr; }.queue-meta { grid-column: 2; justify-items: start; }.review-result { align-items: flex-start; flex-direction: column; } }
</style>
