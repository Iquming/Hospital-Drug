<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  CheckCircle2,
  CircleX,
  Clock3,
  Plus,
  RadioTower,
  RefreshCw,
  RotateCcw,
  Send,
  ServerCog,
  TriangleAlert
} from 'lucide-vue-next'
import { errorMessage } from '../api/client'

const props = defineProps({
  api: { type: Object, required: true },
  notify: { type: Function, required: true },
  confirmAction: { type: Function, required: true }
})

const emit = defineEmits(['synced'])
const integration = ref({})
const callbacks = ref([])
const loading = ref(true)
const sending = ref(false)
const callbackFilter = ref('')
const form = ref({
  patientId: 'P-DEMO-001',
  patientName: '测试患者',
  patientGender: '女',
  patientAge: 35,
  encounterNo: 'OP-DEMO-001',
  departmentCode: 'DEPT-OP',
  departmentName: '门诊内科',
  prescriberId: 'D-DEMO-001',
  prescriberName: '测试医师',
  diagnosis: '上呼吸道感染',
  allergyInfo: '未发现药物过敏史',
  priority: 'NORMAL',
  items: [
    { hisDrugCode: 'HIS-DRUG-001', drugName: '阿莫西林胶囊', specification: '0.25g*24粒', quantity: 1, unit: '盒', dosage: '0.5g', frequency: '每日3次', administrationRoute: '口服', usageInstruction: '饭后服用' },
    { hisDrugCode: 'HIS-DRUG-002', drugName: '布洛芬片', specification: '0.2g*20片', quantity: 2, unit: '盒', dosage: '0.2g', frequency: '必要时', administrationRoute: '口服', usageInstruction: '疼痛时服用' }
  ]
})

const statusLabels = { PENDING: '待发送', PROCESSING: '发送中', SENT: '已送达', FAILED: '发送失败' }
const eventLabels = {
  APPLICATION_RECEIVED: '申请单接收',
  APPLICATION_MAPPING_COMPLETED: '药品匹配完成',
  DISPENSE_STATUS_CHANGED: '发药状态变化',
  RETURN_STATUS_CHANGED: '退药状态变化',
  APPLICATION_CANCELLED: '申请单撤销',
  PRESCRIPTION_REVIEWED: '处方审核结果',
  CONTROLLED_DRUG_REVIEWED: '特殊药品复核结果',
  DRUG_CONTROL_CATEGORY_CHANGED: '特殊管理属性变更'
}

const metrics = computed(() => ({
  total: callbacks.value.length,
  sent: callbacks.value.filter(item => item.status === 'SENT').length,
  pending: callbacks.value.filter(item => ['PENDING', 'PROCESSING'].includes(item.status)).length,
  failed: callbacks.value.filter(item => item.status === 'FAILED').length
}))
const filteredCallbacks = computed(() => callbackFilter.value
  ? callbacks.value.filter(item => item.status === callbackFilter.value)
  : callbacks.value)

const statusTone = status => {
  if (status === 'SENT') return 'success'
  if (status === 'FAILED') return 'danger'
  if (['PENDING', 'PROCESSING'].includes(status)) return 'warning'
  return 'neutral'
}

const load = async () => {
  loading.value = true
  try {
    const [statusResponse, callbackResponse] = await Promise.all([
      props.api.get('/api/his/integration/status'),
      props.api.get('/api/his/callbacks?limit=150')
    ])
    integration.value = statusResponse.data || {}
    callbacks.value = callbackResponse.data || []
    emit('synced', new Date())
  } catch (error) {
    props.notify(errorMessage(error, 'HIS 联调状态加载失败'), 'error')
  } finally {
    loading.value = false
  }
}

const addItem = () => form.value.items.push({ hisDrugCode: '', drugName: '', specification: '', quantity: 1, unit: '盒', dosage: '', frequency: '', administrationRoute: '', usageInstruction: '' })
const removeItem = index => {
  if (form.value.items.length <= 1) return props.notify('申请单至少需要一项药品', 'error')
  form.value.items.splice(index, 1)
}

const validate = () => {
  if (!form.value.patientId.trim() || !form.value.patientName.trim()) return '请填写患者编号和姓名'
  if (!form.value.encounterNo.trim()) return '请填写就诊号'
  if (!form.value.items.length) return '申请单至少需要一项药品'
  if (form.value.items.some(item => !item.hisDrugCode.trim() || !item.drugName.trim() || Number(item.quantity) <= 0)) return '请完整填写每项药品的 HIS 编码、名称和数量'
  return ''
}

const sendApplication = async () => {
  const validationError = validate()
  if (validationError) return props.notify(validationError, 'error')
  const confirmed = await props.confirmAction({
    title: '发送模拟 HIS 申请',
    message: `${form.value.patientName} · ${form.value.encounterNo}\n共 ${form.value.items.length} 项药品，将生成新的门诊申请单。`,
    confirmLabel: '发送申请',
    tone: 'warning'
  })
  if (!confirmed) return
  sending.value = true
  const stamp = Date.now()
  try {
    const response = await props.api.post('/api/admin/his-simulator/applications', {
      eventId: `HIS-DEMO-EVENT-${stamp}`,
      sourceSystem: 'HIS',
      applicationNo: `HIS-OP-${stamp}`,
      revision: 1,
      patientId: form.value.patientId,
      patientName: form.value.patientName,
      patientGender: form.value.patientGender,
      patientAge: Number(form.value.patientAge),
      encounterNo: form.value.encounterNo,
      departmentCode: form.value.departmentCode,
      departmentName: form.value.departmentName,
      priority: form.value.priority,
      prescribedAt: new Date().toISOString().slice(0, 19),
      prescriberId: form.value.prescriberId,
      prescriberName: form.value.prescriberName,
      diagnosis: form.value.diagnosis,
      allergyInfo: form.value.allergyInfo,
      items: form.value.items.map((item, index) => ({
        itemNo: `ITEM-${index + 1}`,
        hisDrugCode: item.hisDrugCode,
        drugName: item.drugName,
        specification: item.specification,
        quantity: Number(item.quantity),
        unit: item.unit,
        dosage: item.dosage,
        frequency: item.frequency,
        administrationRoute: item.administrationRoute,
        usageInstruction: item.usageInstruction
      }))
    })
    const pendingMapping = response.data?.warnings?.length || 0
    props.notify(pendingMapping ? `申请已送达，其中 ${pendingMapping} 项待匹配` : '模拟 HIS 申请已送达药房', pendingMapping ? 'info' : 'success')
    await load()
  } catch (error) {
    props.notify(errorMessage(error, '模拟申请发送失败'), 'error')
  } finally {
    sending.value = false
  }
}

const retry = async event => {
  try {
    await props.api.post(`/api/his/callbacks/${event.eventId}/retry`)
    props.notify('回传事件已重新排队', 'success')
    await load()
  } catch (error) {
    props.notify(errorMessage(error, '回传补发失败'), 'error')
  }
}

onMounted(load)
</script>

<template>
  <section class="page-stack" aria-labelledby="integration-heading">
    <header class="page-header">
      <div><h2 id="integration-heading">HIS 联调中心</h2><p>生成模拟门诊申请并监测药房状态回传</p></div>
      <div class="page-actions"><button type="button" class="button secondary" :disabled="loading" @click="load"><RefreshCw :size="17" />刷新联调状态</button></div>
    </header>

    <section class="integration-status panel" aria-label="HIS 连接配置">
      <span class="integration-icon"><ServerCog :size="21" /></span>
      <div><span>当前模式</span><strong>{{ integration.mode === 'rest' ? '真实 REST HIS' : '本地模拟 HIS' }}</strong></div>
      <div><span>状态回传目标</span><strong class="mono">{{ integration.callbackUrl || '--' }}</strong></div>
      <div><span>队列轮询</span><strong>{{ integration.callbackPollSeconds ?? '--' }} 秒</strong></div>
      <span class="status-badge" data-tone="success"><CheckCircle2 :size="14" />连接配置有效</span>
    </section>

    <div class="metric-grid integration-metrics">
      <div class="metric-card"><span>回传总数</span><strong>{{ metrics.total }}</strong><small>最近 150 条记录</small><span class="metric-icon"><RadioTower :size="19" /></span></div>
      <div class="metric-card"><span>已送达</span><strong>{{ metrics.sent }}</strong><small>HIS 已确认接收</small><span class="metric-icon success"><CheckCircle2 :size="19" /></span></div>
      <div class="metric-card"><span>等待发送</span><strong>{{ metrics.pending }}</strong><small>队列处理中</small><span class="metric-icon warning"><Clock3 :size="19" /></span></div>
      <div class="metric-card"><span>发送失败</span><strong>{{ metrics.failed }}</strong><small>需要人工补发</small><span class="metric-icon" :class="metrics.failed ? 'danger' : 'success'"><TriangleAlert :size="19" /></span></div>
    </div>

    <div class="integration-grid">
      <section class="data-panel simulator-panel">
        <div class="data-panel-header"><div><h3>模拟门诊处方申请</h3><p>仅用于本地 HIS 闭环联调</p></div><Send :size="18" /></div>
        <div class="simulator-body">
          <fieldset class="form-section">
            <legend>患者与就诊信息</legend>
            <div class="form-grid">
              <label class="field"><span>患者编号</span><input v-model="form.patientId" aria-label="患者编号" /></label>
              <label class="field"><span>患者姓名</span><input v-model="form.patientName" aria-label="患者姓名" /></label>
              <label class="field"><span>患者性别</span><select v-model="form.patientGender" aria-label="患者性别"><option>男</option><option>女</option><option>未知</option></select></label>
              <label class="field"><span>患者年龄</span><input v-model.number="form.patientAge" aria-label="患者年龄" type="number" min="0" max="150" /></label>
              <label class="field"><span>就诊号</span><input v-model="form.encounterNo" aria-label="就诊号" /></label>
              <label class="field"><span>优先级</span><select v-model="form.priority" aria-label="申请单优先级"><option value="NORMAL">普通</option><option value="URGENT">急诊</option></select></label>
              <label class="field"><span>科室编码</span><input v-model="form.departmentCode" aria-label="科室编码" /></label>
              <label class="field"><span>科室名称</span><input v-model="form.departmentName" aria-label="科室名称" /></label>
              <label class="field"><span>处方医师编号</span><input v-model="form.prescriberId" aria-label="处方医师编号" /></label>
              <label class="field"><span>处方医师姓名</span><input v-model="form.prescriberName" aria-label="处方医师姓名" /></label>
              <label class="field"><span>临床诊断</span><input v-model="form.diagnosis" aria-label="临床诊断" /></label>
              <label class="field"><span>过敏史</span><input v-model="form.allergyInfo" aria-label="过敏史" /></label>
            </div>
          </fieldset>

          <fieldset class="form-section medicine-editor">
            <legend>药品明细</legend>
            <div v-for="(item, index) in form.items" :key="index" class="medicine-editor-item">
              <header><span>药品 {{ index + 1 }}</span><button type="button" class="icon-button danger-ghost" title="删除药品" :aria-label="`删除药品 ${index + 1}`" @click="removeItem(index)"><CircleX :size="17" /></button></header>
              <div class="medicine-fields">
                <label><span class="sr-only">HIS 药品编码</span><input v-model="item.hisDrugCode" aria-label="HIS 药品编码" placeholder="HIS 药品编码" /></label>
                <label><span class="sr-only">药品名称</span><input v-model="item.drugName" aria-label="药品名称" placeholder="药品名称" /></label>
                <label><span class="sr-only">规格</span><input v-model="item.specification" aria-label="药品规格" placeholder="规格" /></label>
                <label><span class="sr-only">申请数量</span><input v-model.number="item.quantity" aria-label="申请数量" type="number" min="1" placeholder="数量" /></label>
                <label><span class="sr-only">申请单位</span><select v-model="item.unit" aria-label="申请单位"><option>盒</option><option>瓶</option><option>支</option><option>片</option><option>粒</option></select></label>
                <label><span class="sr-only">单次剂量</span><input v-model="item.dosage" aria-label="单次剂量" placeholder="单次剂量" /></label>
                <label><span class="sr-only">用药频次</span><input v-model="item.frequency" aria-label="用药频次" placeholder="用药频次" /></label>
                <label><span class="sr-only">给药途径</span><input v-model="item.administrationRoute" aria-label="给药途径" placeholder="给药途径" /></label>
                <label><span class="sr-only">补充用药说明</span><input v-model="item.usageInstruction" aria-label="补充用药说明" placeholder="补充用药说明" /></label>
              </div>
            </div>
            <button type="button" class="button secondary add-medicine" @click="addItem"><Plus :size="16" />添加药品</button>
          </fieldset>
        </div>
        <div class="simulator-footer"><button type="button" class="button primary" :disabled="sending" @click="sendApplication"><Send :size="17" />{{ sending ? '正在发送' : '发送到药房系统' }}</button></div>
      </section>

      <section class="data-panel callback-panel">
        <div class="data-panel-header callback-header"><div><h3>状态回传时间线</h3><p>按创建时间显示回传任务</p></div><label><span class="sr-only">回传状态筛选</span><select v-model="callbackFilter" aria-label="回传状态筛选"><option value="">全部状态</option><option value="PENDING">待发送</option><option value="PROCESSING">发送中</option><option value="SENT">已送达</option><option value="FAILED">发送失败</option></select></label></div>
        <div v-if="loading" class="loading-state"><span class="spinner"></span><span>正在读取回传记录</span></div>
        <div v-else-if="filteredCallbacks.length" class="callback-list">
          <article v-for="event in filteredCallbacks" :key="event.id">
            <span class="timeline-mark" :data-tone="statusTone(event.status)"></span>
            <div class="callback-main"><strong>{{ eventLabels[event.eventType] || event.eventType }}</strong><span>{{ event.patientName }} · {{ event.hisApplicationNo }}</span><small>{{ event.createTime }} · {{ event.applicationStatus }}</small><small v-if="event.lastError" class="callback-error">{{ event.lastError }}</small></div>
            <div class="callback-state"><span class="status-badge" :data-tone="statusTone(event.status)">{{ statusLabels[event.status] || event.status }}</span><small>已尝试 {{ event.attemptCount }} 次</small><button v-if="event.status === 'FAILED'" type="button" class="button secondary retry-button" @click="retry(event)"><RotateCcw :size="14" />补发</button></div>
          </article>
        </div>
        <div v-else class="empty-state"><RadioTower :size="28" /><span>当前筛选条件下没有回传记录</span></div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.integration-status { display: grid; grid-template-columns: 42px 0.7fr 1.5fr 0.6fr auto; align-items: center; gap: 16px; padding: 11px 14px; }
.integration-icon { display: grid; width: 42px; height: 42px; place-items: center; border-radius: var(--radius); background: var(--primary-soft); color: var(--primary); }
.integration-status > div { display: grid; min-width: 0; gap: 2px; }
.integration-status > div > span { color: var(--muted); font-size: 10px; }
.integration-status strong { overflow: hidden; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.integration-metrics .metric-card { min-height: 92px; }
.integration-grid { display: grid; grid-template-columns: minmax(620px, 1.2fr) minmax(390px, 0.8fr); gap: 12px; align-items: start; }
.simulator-panel { min-width: 0; }
.simulator-body { display: grid; gap: 18px; max-height: calc(100vh - 400px); overflow-y: auto; padding: 16px; }
.form-section { min-width: 0; padding: 14px; border: 1px solid var(--line); border-radius: var(--radius); }
.form-section legend { padding: 0 7px; color: var(--ink-soft); font-size: 12px; font-weight: 700; }
.medicine-editor { display: grid; gap: 10px; }
.medicine-editor-item { overflow: hidden; border: 1px solid var(--line); border-radius: var(--radius); background: var(--panel-subtle); }
.medicine-editor-item header { display: flex; align-items: center; justify-content: space-between; min-height: 40px; padding: 3px 7px 3px 11px; border-bottom: 1px solid var(--line); }
.medicine-editor-item header > span { font-size: 11px; font-weight: 700; }
.medicine-fields { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 7px; padding: 9px; }
.medicine-fields input, .medicine-fields select { min-height: 36px; height: 36px; font-size: 11px; }
.add-medicine { justify-self: start; }
.simulator-footer { display: flex; justify-content: flex-end; padding: 11px 16px; border-top: 1px solid var(--line); background: var(--panel-subtle); }
.callback-panel { min-width: 0; }
.callback-header select { width: 122px; }
.callback-list { overflow-y: auto; max-height: calc(100vh - 334px); }
.callback-list article { display: grid; grid-template-columns: 10px minmax(0, 1fr) auto; gap: 11px; padding: 12px 13px; border-bottom: 1px solid var(--line); }
.callback-list article:last-child { border-bottom: 0; }
.timeline-mark { width: 8px; height: 8px; margin-top: 5px; border-radius: 50%; background: #9aabb5; }
.timeline-mark[data-tone="success"] { background: var(--success); }
.timeline-mark[data-tone="warning"] { background: var(--warning); }
.timeline-mark[data-tone="danger"] { background: var(--danger); }
.callback-main, .callback-state { display: grid; min-width: 0; gap: 3px; }
.callback-main strong { font-size: 12px; }
.callback-main span, .callback-main small, .callback-state small { overflow: hidden; color: var(--muted); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.callback-error { color: var(--danger) !important; white-space: normal !important; }
.callback-state { justify-items: end; }
.retry-button { min-height: 32px; padding: 0 9px; color: var(--danger); }
@media (max-width: 1280px) {
  .integration-grid { grid-template-columns: minmax(560px, 1.1fr) minmax(350px, 0.9fr); }
  .medicine-fields { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
