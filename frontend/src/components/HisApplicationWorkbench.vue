<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import {
  AlertTriangle,
  ArrowDownToLine,
  CheckCircle2,
  ClipboardList,
  Link2,
  RefreshCw,
  RotateCcw,
  Search,
  ShieldCheck,
  UserRound,
  XCircle
} from 'lucide-vue-next'
import { errorMessage } from '../api/client'

const props = defineProps({
  api: { type: Object, required: true },
  userRole: { type: String, default: '' },
  notify: { type: Function, required: true },
  confirmAction: { type: Function, required: true }
})

const emit = defineEmits(['synced'])
const applications = ref([])
const selected = ref(null)
const catalog = ref([])
const queueLoading = ref(true)
const detailLoading = ref(false)
const activeAction = ref('')
const filters = ref({ keyword: '', status: '', priority: '' })
const traceCodes = ref({})
const mappingCatalogIds = ref({})
const reviewComment = ref('')
const scanInputs = new Map()

const controlCategoryLabels = {
  NARCOTIC: '麻醉药品',
  PSYCHOTROPIC_I: '第一类精神药品',
  PSYCHOTROPIC_II: '第二类精神药品',
  MEDICAL_TOXIC: '医疗用毒性药品'
}
const isSpecialCategory = category => Boolean(category && category !== 'GENERAL')
const catalogOptionLabel = entry => {
  const base = `${entry.drugName} · ${entry.specification || '--'}`
  return isSpecialCategory(entry.controlCategory)
    ? `${base} · ${controlCategoryLabels[entry.controlCategory] || entry.controlCategory}`
    : base
}

const canOperate = computed(() => ['ADMIN', 'PHARMACIST'].includes(props.userRole))
const sortedApplications = computed(() => [...applications.value].sort((a, b) => {
  const urgentDelta = Number(b.priority === 'URGENT') - Number(a.priority === 'URGENT')
  if (urgentDelta) return urgentDelta
  return String(b.prescribedAt || b.createTime || '').localeCompare(String(a.prescribedAt || a.createTime || ''))
}))

const metrics = computed(() => ({
  pending: applications.value.filter(item => ['RECEIVED', 'REVIEW_PENDING', 'READY'].includes(item.status)).length,
  urgent: applications.value.filter(item => item.priority === 'URGENT' && !['DISPENSED', 'CANCELLED', 'RETURNED'].includes(item.status)).length,
  mapping: applications.value.filter(item => item.status === 'MAPPING_REQUIRED').length,
  partial: applications.value.filter(item => item.status === 'PARTIALLY_DISPENSED').length,
  callbackFailed: applications.value.filter(item => item.callbackStatus === 'FAILED').length
}))

const statusLabels = {
  RECEIVED: '已接收',
  MAPPING_REQUIRED: '待匹配',
  REVIEW_PENDING: '待特殊复核',
  REVIEW_REJECTED: '特殊复核未通过',
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

const callbackLabels = { PENDING: '待回传', PROCESSING: '回传中', SENT: '已回传', FAILED: '回传失败' }
const reviewTitle = application => {
  if (!application?.specialReviewRequired && application?.reviewStatus === 'APPROVED') return '普通处方，无需特殊审批'
  if (application?.specialReviewRequired && application?.reviewStatus === 'APPROVED') return '特殊药品人工复核通过'
  if (application?.specialReviewRequired && application?.reviewStatus === 'REJECTED') return '特殊药品人工复核未通过'
  if (application?.specialReviewRequired) return '等待特殊药品人工复核'
  return application?.reviewStatus === 'REJECTED' ? '通用处方审核未通过' : '等待通用处方审核'
}
const reviewDetail = application => {
  if (!application?.specialReviewRequired && application?.reviewStatus === 'APPROVED') {
    return application.reviewComment || '通用处方审核已通过；调剂发药仍执行四查十对'
  }
  return application?.reviewComment || '未填写复核意见'
}

const statusTone = status => {
  if (['DISPENSED', 'SENT', 'APPROVED'].includes(status)) return 'success'
  if (['READY', 'PENDING', 'REVIEW_PENDING', 'PROCESSING'].includes(status)) return 'warning'
  if (['MAPPING_REQUIRED', 'UNMAPPED', 'PARTIALLY_DISPENSED', 'PARTIAL'].includes(status)) return 'info'
  if (['REVIEW_REJECTED', 'RETURN_REQUIRED', 'FAILED', 'REJECTED'].includes(status)) return 'danger'
  return 'neutral'
}

const progressText = item => `${item.dispensedQuantity || 0} / ${item.requestedQuantity} ${item.unit}`
const progressPercent = item => Math.min(100, Math.round(((item.dispensedQuantity || 0) / Math.max(1, item.requestedQuantity)) * 100))
const isDispensable = item => selected.value?.reviewStatus === 'APPROVED'
  && selected.value?.status !== 'RETURN_REQUIRED'
  && ['PENDING', 'PARTIAL'].includes(item.status)
const isReturnable = item => ['PARTIAL', 'DISPENSED'].includes(item.status) && (item.dispensedQuantity || 0) > 0
const setScanInput = (id, element) => {
  if (element) scanInputs.set(id, element)
  else scanInputs.delete(id)
}

const focusNextPendingItem = async () => {
  await nextTick()
  const nextItem = selected.value?.items?.find(isDispensable)
  if (nextItem) scanInputs.get(nextItem.id)?.focus()
}

const loadApplications = async ({ keepSelection = true, quiet = false } = {}) => {
  if (!quiet) queueLoading.value = true
  try {
    const response = await props.api.get('/api/pharmacy/applications', { params: filters.value })
    applications.value = response.data || []
    if (keepSelection && selected.value) {
      const visible = applications.value.find(item => item.id === selected.value.id)
      if (visible) await selectApplication(visible, { focus: false })
      else selected.value = null
    }
    emit('synced', new Date())
  } catch (error) {
    props.notify(errorMessage(error, '申请单队列加载失败'), 'error')
  } finally {
    queueLoading.value = false
  }
}

const loadCatalog = async () => {
  if (!canOperate.value) return
  try {
    const response = await props.api.get('/catalog')
    catalog.value = (response.data || []).filter(item => item.status === 'ENABLED')
  } catch (error) {
    props.notify(errorMessage(error, '本地药品档案加载失败'), 'error')
  }
}

const selectApplication = async (application, options = { focus: true }) => {
  detailLoading.value = true
  try {
    const response = await props.api.get(`/api/pharmacy/applications/${application.id}`)
    selected.value = response.data
    reviewComment.value = ''
    if (options.focus !== false) await focusNextPendingItem()
  } catch (error) {
    props.notify(errorMessage(error, '申请单详情加载失败'), 'error')
  } finally {
    detailLoading.value = false
  }
}

const saveMapping = async item => {
  const catalogId = Number(mappingCatalogIds.value[item.id])
  if (!catalogId) return props.notify('请选择本地药品档案', 'error')
  activeAction.value = `mapping-${item.id}`
  try {
    await props.api.post('/api/pharmacy/his-drug-mappings', {
      sourceSystem: selected.value.sourceSystem,
      hisDrugCode: item.hisDrugCode,
      localCatalogId: catalogId
    })
    props.notify(`${item.drugName} 药品编码映射已保存`, 'success')
    await loadApplications({ keepSelection: true, quiet: true })
  } catch (error) {
    props.notify(errorMessage(error, '药品编码映射失败'), 'error')
  } finally {
    activeAction.value = ''
  }
}

const dispense = async item => {
  const traceCode = (traceCodes.value[item.id] || '').trim()
  if (!traceCode) {
    scanInputs.get(item.id)?.focus()
    return props.notify('请先扫描药品追溯码', 'error')
  }
  activeAction.value = `dispense-${item.id}`
  try {
    const response = await props.api.post(`/api/pharmacy/application-items/${item.id}/dispense`, {
      requestId: `DISPENSE-${crypto?.randomUUID ? crypto.randomUUID() : Date.now()}`,
      traceCode
    })
    selected.value = response.data
    traceCodes.value[item.id] = ''
    props.notify(`已完成 ${item.drugName} 本次发药`, 'success')
    await loadApplications({ keepSelection: false, quiet: true })
    await focusNextPendingItem()
  } catch (error) {
    props.notify(errorMessage(error, '发药失败'), 'error')
    await nextTick()
    scanInputs.get(item.id)?.focus()
  } finally {
    activeAction.value = ''
  }
}

const returnDrug = async item => {
  const traceCode = (traceCodes.value[item.id] || '').trim()
  if (!traceCode) {
    scanInputs.get(item.id)?.focus()
    return props.notify('退药前请扫描原药品追溯码', 'error')
  }
  const confirmed = await props.confirmAction({
    title: '确认退药',
    message: `${selected.value.patientName} · ${item.drugName}\n确认将扫描药品退回库存吗？`,
    confirmLabel: '确认退药',
    tone: 'danger'
  })
  if (!confirmed) return
  activeAction.value = `return-${item.id}`
  try {
    const response = await props.api.post(`/api/pharmacy/application-items/${item.id}/return`, {
      requestId: `RETURN-${crypto?.randomUUID ? crypto.randomUUID() : Date.now()}`,
      traceCode
    })
    selected.value = response.data
    traceCodes.value[item.id] = ''
    props.notify(`已完成 ${item.drugName} 本次退药`, 'success')
    await loadApplications({ keepSelection: false, quiet: true })
  } catch (error) {
    props.notify(errorMessage(error, '退药失败'), 'error')
  } finally {
    activeAction.value = ''
  }
}

const review = async decision => {
  if (!selected.value) return
  if (!selected.value.specialReviewRequired) return props.notify('普通药品无需特殊药品人工复核', 'info')
  if (decision === 'REJECTED' && !reviewComment.value.trim()) return props.notify('复核不通过时必须填写原因', 'error')
  if (decision === 'REJECTED') {
    const confirmed = await props.confirmAction({
      title: '特殊药品复核不通过',
      message: `确认退回 ${selected.value.patientName} 的特殊药品处方吗？\n原因：${reviewComment.value.trim()}`,
      confirmLabel: '确认退回',
      tone: 'danger'
    })
    if (!confirmed) return
  }
  activeAction.value = 'review'
  try {
    const response = await props.api.post(`/api/pharmacy/applications/${selected.value.id}/review`, {
      decision,
      comment: reviewComment.value.trim()
    })
    selected.value = response.data
    reviewComment.value = ''
    props.notify(decision === 'APPROVED' ? '特殊药品人工复核通过，可以进入调剂发药' : '特殊药品处方已退回，禁止发药', decision === 'APPROVED' ? 'success' : 'info')
    await loadApplications({ keepSelection: false, quiet: true })
    if (decision === 'APPROVED') await focusNextPendingItem()
  } catch (error) {
    props.notify(errorMessage(error, '特殊药品复核失败'), 'error')
  } finally {
    activeAction.value = ''
  }
}

onMounted(async () => {
  await Promise.all([loadApplications({ keepSelection: false }), loadCatalog()])
})
</script>

<template>
  <section class="page-stack his-workbench" aria-labelledby="applications-heading">
    <header class="page-header">
      <div><h2 id="applications-heading">门诊处方申请单</h2><p>按急诊优先顺序匹配、复核特殊药品、扫码发药并回传 HIS</p></div>
      <div class="page-actions">
        <span v-if="!canOperate" class="status-badge" data-tone="info"><UserRound :size="14" />只读查看</span>
        <button type="button" class="button secondary" :disabled="queueLoading" @click="loadApplications()"><RefreshCw :size="17" />刷新队列</button>
      </div>
    </header>

    <div class="metric-grid five application-metrics">
      <div class="metric-card"><span>待处理</span><strong>{{ metrics.pending }}</strong><small>待特殊复核或待发药</small><span class="metric-icon"><ClipboardList :size="19" /></span></div>
      <div class="metric-card"><span>急诊优先</span><strong>{{ metrics.urgent }}</strong><small>未完成急诊处方</small><span class="metric-icon danger"><AlertTriangle :size="19" /></span></div>
      <div class="metric-card"><span>待匹配</span><strong>{{ metrics.mapping }}</strong><small>缺少本地药品映射</small><span class="metric-icon info"><Link2 :size="19" /></span></div>
      <div class="metric-card"><span>部分发药</span><strong>{{ metrics.partial }}</strong><small>仍有明细待完成</small><span class="metric-icon warning"><ArrowDownToLine :size="19" /></span></div>
      <div class="metric-card"><span>回传异常</span><strong>{{ metrics.callbackFailed }}</strong><small>HIS 未成功接收状态</small><span class="metric-icon" :class="metrics.callbackFailed ? 'danger' : 'success'"><RotateCcw :size="19" /></span></div>
    </div>

    <div class="application-toolbar toolbar">
      <label class="search-control"><Search :size="16" /><span class="sr-only">搜索申请单</span><input v-model="filters.keyword" aria-label="搜索申请单" placeholder="申请单号、患者姓名或患者编号" @keyup.enter="loadApplications({ keepSelection: false })" /></label>
      <label><span class="sr-only">申请单状态</span><select v-model="filters.status" aria-label="申请单状态" @change="loadApplications({ keepSelection: false })"><option value="">全部状态</option><option value="MAPPING_REQUIRED">待匹配</option><option value="REVIEW_PENDING">待特殊复核</option><option value="REVIEW_REJECTED">特殊复核未通过</option><option value="READY">待发药</option><option value="PARTIALLY_DISPENSED">部分发药</option><option value="DISPENSED">已发药</option><option value="RETURN_REQUIRED">待退药</option><option value="RETURNED">已退药</option><option value="CANCELLED">已撤销</option></select></label>
      <label><span class="sr-only">优先级</span><select v-model="filters.priority" aria-label="申请单优先级" @change="loadApplications({ keepSelection: false })"><option value="">全部优先级</option><option value="URGENT">急诊</option><option value="NORMAL">普通</option></select></label>
      <button type="button" class="button primary" @click="loadApplications({ keepSelection: false })"><Search :size="16" />查询</button>
    </div>

    <div class="application-workspace">
      <aside class="application-queue" aria-label="处方申请单队列">
        <div v-if="queueLoading" class="loading-state"><span class="spinner"></span><span>正在加载申请单</span></div>
        <div v-else-if="sortedApplications.length" class="queue-list">
          <button v-for="application in sortedApplications" :key="application.id" type="button" :class="{ selected: selected?.id === application.id }" @click="selectApplication(application)">
            <span class="priority-mark" :data-urgent="application.priority === 'URGENT'"></span>
            <span class="queue-patient"><strong>{{ application.patientName }}</strong><small>{{ application.hisApplicationNo }} · {{ application.patientId }}</small></span>
            <span class="queue-state"><b class="status-badge" :data-tone="statusTone(application.status)">{{ statusLabels[application.status] || application.status }}</b><small>{{ application.departmentName || '--' }}</small></span>
            <span v-if="application.callbackStatus === 'FAILED'" class="callback-alert" title="HIS 回传失败"><RotateCcw :size="13" /></span>
          </button>
        </div>
        <div v-else class="empty-state"><ClipboardList :size="28" /><span>当前筛选条件下没有申请单</span></div>
      </aside>

      <section class="application-detail" aria-label="申请单详情">
        <div v-if="detailLoading" class="loading-state detail-loading"><span class="spinner"></span><span>正在读取申请单详情</span></div>
        <div v-else-if="!selected" class="empty-state detail-empty"><ClipboardList :size="32" /><strong>选择一张申请单开始处理</strong><span>左侧队列已按急诊优先排列</span></div>
        <template v-else>
          <header class="patient-context">
            <span class="patient-avatar"><UserRound :size="21" /></span>
            <div><span>患者</span><strong>{{ selected.patientName }}</strong><small>{{ selected.patientGender || '未知' }} · {{ selected.patientAge ?? '--' }} 岁 · {{ selected.patientId }}</small></div>
            <div><span>就诊信息</span><strong>{{ selected.encounterNo || '--' }}</strong><small>{{ selected.departmentName || '--' }} · {{ selected.prescribedAt || '--' }}</small></div>
            <div class="patient-status"><span class="status-badge" :data-tone="selected.priority === 'URGENT' ? 'danger' : 'neutral'">{{ selected.priority === 'URGENT' ? '急诊' : '普通' }}</span><span v-if="selected.specialReviewRequired" class="status-badge" data-tone="danger">特殊管理药品</span><span class="status-badge" :data-tone="statusTone(selected.status)">{{ statusLabels[selected.status] || selected.status }}</span></div>
          </header>

          <div class="clinical-context">
            <div><span>临床诊断</span><strong>{{ selected.diagnosis || '未提供' }}</strong></div>
            <div><span>过敏史</span><strong :class="{ allergy: selected.allergyInfo && !selected.allergyInfo.includes('未发现') }">{{ selected.allergyInfo || '未提供' }}</strong></div>
            <div><span>处方医师</span><strong>{{ selected.prescriberName || '--' }} · {{ selected.prescriberId || '--' }}</strong></div>
            <div><span>HIS 回传</span><strong><span class="status-badge" :data-tone="statusTone(selected.callbackStatus)">{{ callbackLabels[selected.callbackStatus] || selected.callbackStatus || '暂无任务' }}</span></strong></div>
          </div>

          <div v-if="canOperate && selected.specialReviewRequired && selected.status === 'REVIEW_PENDING'" class="review-panel">
            <div><ShieldCheck :size="19" /><span><strong>特殊药品人工复核</strong><small>核对处方权限、专用处方、诊断、剂量及限量要求</small></span></div>
            <label><span class="sr-only">特殊药品复核意见</span><input v-model="reviewComment" aria-label="特殊药品复核意见" placeholder="复核通过可填写注意事项；不通过必须填写原因" /></label>
            <button type="button" class="button success" :disabled="activeAction === 'review'" @click="review('APPROVED')"><CheckCircle2 :size="16" />复核通过</button>
            <button type="button" class="button danger" :disabled="activeAction === 'review'" @click="review('REJECTED')"><XCircle :size="16" />复核不通过</button>
          </div>
          <div v-else class="review-result" :data-tone="statusTone(selected.reviewStatus)"><ShieldCheck :size="17" /><span><strong>{{ reviewTitle(selected) }}</strong><small>{{ reviewDetail(selected) }}<template v-if="selected.reviewedBy"> · {{ selected.reviewedBy }}<template v-if="selected.reviewedAt"> · {{ selected.reviewedAt }}</template></template></small></span></div>

          <div class="medicine-list">
            <article v-for="item in selected.items" :key="item.id" class="medicine-item">
              <div class="medicine-main">
                <div><span class="item-number">{{ item.itemNo }}</span><small class="mono">HIS {{ item.hisDrugCode }}</small><h3>{{ item.drugName }}</h3><span v-if="isSpecialCategory(item.controlCategory)" class="status-badge medicine-control-badge" :data-tone="item.controlCategory === 'PSYCHOTROPIC_II' ? 'warning' : 'danger'">{{ controlCategoryLabels[item.controlCategory] || item.controlCategory }}</span><p>{{ item.specification || '--' }} · {{ item.dosage || '剂量未提供' }} · {{ item.frequency || '频次未提供' }} · {{ item.administrationRoute || '途径未提供' }}</p><small v-if="item.usageInstruction" class="usage-note">{{ item.usageInstruction }}</small></div>
                <div class="medicine-progress"><span>{{ progressText(item) }}</span><div><i :style="{ width: `${progressPercent(item)}%` }"></i></div></div>
                <span class="status-badge" :data-tone="statusTone(item.status)">{{ statusLabels[item.status] || item.status }}</span>
              </div>

              <div v-if="item.status === 'UNMAPPED'" class="mapping-action">
                <Link2 :size="17" /><span>该 HIS 药品编码尚未映射到本地档案</span>
                <template v-if="canOperate"><label><span class="sr-only">选择本地药品档案</span><select v-model="mappingCatalogIds[item.id]" aria-label="选择本地药品档案"><option value="">选择本地药品档案</option><option v-for="entry in catalog" :key="entry.id" :value="entry.id">{{ catalogOptionLabel(entry) }}</option></select></label><button type="button" class="button primary" :disabled="activeAction === `mapping-${item.id}`" @click="saveMapping(item)">保存映射</button></template>
              </div>

              <div v-if="canOperate && (isDispensable(item) || isReturnable(item))" class="scan-action">
                <label><ScanLine :size="18" /><span class="sr-only">{{ isDispensable(item) ? '扫描发药追溯码' : '扫描退药追溯码' }}</span><input aria-label="药品追溯码" :ref="element => setScanInput(item.id, element)" v-model="traceCodes[item.id]" class="mono" :placeholder="isDispensable(item) ? '扫描药品追溯码后按回车发药' : '退药时扫描原追溯码'" @keyup.enter="isDispensable(item) ? dispense(item) : undefined" /></label>
                <button v-if="isDispensable(item)" type="button" class="button success" :disabled="activeAction === `dispense-${item.id}`" @click="dispense(item)"><ArrowDownToLine :size="16" />{{ activeAction === `dispense-${item.id}` ? '正在发药' : '确认发药' }}</button>
                <button v-if="isReturnable(item)" type="button" class="button secondary return-button" :disabled="activeAction === `return-${item.id}`" @click="returnDrug(item)"><RotateCcw :size="16" />退药</button>
              </div>
            </article>
          </div>
        </template>
      </section>
    </div>
  </section>
</template>

<style scoped>
.application-metrics .metric-card { min-height: 92px; }
.application-toolbar { display: grid; grid-template-columns: minmax(280px, 1fr) 150px 126px auto; }
.application-toolbar .search-control { min-width: 0; }
.application-workspace { display: grid; min-height: 600px; grid-template-columns: 360px minmax(0, 1fr); overflow: hidden; border: 1px solid var(--line); border-radius: var(--radius); background: #fff; }
.application-queue { min-width: 0; border-right: 1px solid var(--line); background: var(--panel-subtle); }
.queue-list { overflow-y: auto; max-height: calc(100vh - 324px); }
.queue-list > button { position: relative; display: grid; width: 100%; min-height: 72px; grid-template-columns: 5px minmax(0, 1fr) auto; align-items: center; gap: 11px; padding: 9px 11px; border: 0; border-bottom: 1px solid var(--line); background: #fff; color: inherit; text-align: left; }
.queue-list > button:hover, .queue-list > button.selected { background: #eef6f8; }
.queue-list > button.selected { box-shadow: inset 3px 0 var(--primary); }
.priority-mark { width: 5px; height: 38px; border-radius: 2px; background: #a8b5bd; }
.priority-mark[data-urgent="true"] { background: var(--danger); }
.queue-patient, .queue-state { display: grid; min-width: 0; gap: 4px; }
.queue-patient strong, .queue-patient small, .queue-state small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.queue-patient strong { font-size: 13px; }
.queue-patient small, .queue-state small { color: var(--muted); font-size: 10px; }
.queue-state { justify-items: end; }
.callback-alert { position: absolute; right: 8px; bottom: 5px; color: var(--danger); }
.application-detail { min-width: 0; max-height: calc(100vh - 324px); overflow-y: auto; }
.detail-empty, .detail-loading { min-height: 560px; }
.detail-empty strong { color: var(--ink-soft); }
.detail-empty span { font-size: 11px; }
.patient-context { position: sticky; z-index: 4; top: 0; display: grid; grid-template-columns: 42px minmax(170px, 0.7fr) minmax(260px, 1.2fr) auto; align-items: center; gap: 13px; padding: 13px 16px; border-bottom: 1px solid var(--line); background: rgba(248, 250, 251, 0.98); }
.patient-avatar { display: grid; width: 42px; height: 42px; place-items: center; border-radius: 50%; background: var(--primary-soft); color: var(--primary); }
.patient-context > div { display: grid; min-width: 0; gap: 2px; }
.patient-context span, .patient-context small { color: var(--muted); font-size: 10px; }
.patient-context strong { overflow: hidden; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.patient-status { display: flex !important; align-items: center; flex-direction: row; gap: 6px !important; }
.clinical-context { display: grid; grid-template-columns: 1.2fr 1fr 1fr 0.8fr; border-bottom: 1px solid var(--line); }
.clinical-context > div { display: grid; min-width: 0; gap: 3px; padding: 10px 13px; border-right: 1px solid var(--line); }
.clinical-context > div:last-child { border-right: 0; }
.clinical-context span { color: var(--muted); font-size: 10px; }
.clinical-context strong { overflow: hidden; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.clinical-context strong.allergy { color: var(--danger); }
.review-panel { display: grid; grid-template-columns: minmax(220px, 0.8fr) minmax(280px, 1.2fr) auto auto; align-items: center; gap: 8px; padding: 10px 13px; border-bottom: 1px solid #ead4a7; background: var(--warning-soft); }
.review-panel > div { display: flex; align-items: center; gap: 9px; color: var(--warning); }
.review-panel > div > span { display: grid; }
.review-panel strong { font-size: 12px; }
.review-panel small { color: #805c24; font-size: 9px; }
.review-result { display: flex; align-items: center; gap: 9px; padding: 9px 13px; border-bottom: 1px solid var(--line); background: var(--panel-subtle); }
.review-result[data-tone="success"] { background: var(--success-soft); color: var(--success); }
.review-result[data-tone="danger"] { background: var(--danger-soft); color: var(--danger); }
.review-result > span { display: grid; }
.review-result strong { font-size: 11px; }
.review-result small { color: var(--muted); font-size: 10px; }
.medicine-list { display: grid; }
.medicine-item { padding: 15px 16px; border-bottom: 1px solid var(--line); }
.medicine-item:last-child { border-bottom: 0; }
.medicine-main { display: grid; grid-template-columns: minmax(320px, 1fr) 190px auto; align-items: center; gap: 18px; }
.medicine-main > div:first-child { min-width: 0; }
.medicine-main h3 { display: inline; margin: 0 0 0 8px; font-size: 14px; }
.medicine-control-badge { margin-left: 8px; vertical-align: 1px; }
.medicine-main p { overflow: hidden; margin: 4px 0 0; color: var(--ink-soft); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.medicine-main small { color: var(--muted); font-size: 9px; }
.item-number { display: inline-flex; min-height: 22px; align-items: center; padding: 0 6px; border-radius: var(--radius-sm); background: var(--panel-muted); color: var(--muted); font-size: 10px; font-weight: 700; }
.usage-note { display: block; margin-top: 4px; color: var(--teal) !important; }
.medicine-progress { display: grid; gap: 5px; }
.medicine-progress > span { color: var(--ink-soft); font-size: 11px; }
.medicine-progress > div { overflow: hidden; height: 6px; border-radius: 3px; background: var(--panel-muted); }
.medicine-progress i { display: block; height: 100%; background: var(--teal); }
.mapping-action, .scan-action { display: flex; align-items: center; gap: 9px; margin-top: 12px; padding-top: 12px; border-top: 1px dashed var(--line-strong); }
.mapping-action > svg { flex: 0 0 auto; color: var(--info); }
.mapping-action > span { color: var(--muted); font-size: 11px; }
.mapping-action label { min-width: 240px; margin-left: auto; }
.scan-action label { display: flex; align-items: center; min-width: 0; flex: 1; gap: 8px; padding-left: 10px; border: 1px solid var(--line-strong); border-radius: var(--radius); color: var(--primary); }
.scan-action label:focus-within { border-color: var(--primary); box-shadow: 0 0 0 3px rgba(11, 111, 159, 0.1); }
.scan-action input { border: 0; box-shadow: none; }
.scan-action input:focus { box-shadow: none; }
.return-button { border-color: #d7aaa6; color: var(--danger); }
@media (max-width: 1280px) {
  .application-workspace { grid-template-columns: 320px minmax(0, 1fr); }
  .clinical-context { grid-template-columns: 1fr 1fr; }
  .clinical-context > div:nth-child(2) { border-right: 0; }
  .clinical-context > div:nth-child(n + 3) { border-top: 1px solid var(--line); }
  .review-panel { grid-template-columns: 1fr auto auto; }
  .review-panel > div { display: none; }
  .medicine-main { grid-template-columns: minmax(260px, 1fr) 160px auto; }
}
</style>
