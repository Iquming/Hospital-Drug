<script setup>
import { onMounted, ref } from 'vue'
import { CheckCircle2, CircleX, RefreshCw, RotateCcw, Send, ServerCog } from 'lucide-vue-next'

const props = defineProps({ api: { type: Object, required: true } })

const integration = ref({ mode: 'mock', callbackUrl: '--', callbackPollSeconds: 5 })
const callbacks = ref([])
const sending = ref(false)
const message = ref({ text: '', type: 'success' })
const form = ref({
  patientId: 'P001',
  patientName: '张三',
  patientGender: '男',
  patientAge: 35,
  encounterNo: 'OP-DEMO-001',
  departmentCode: 'OPD',
  departmentName: '门诊部',
  prescriberId: 'D001',
  prescriberName: '演示医师',
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
  APPLICATION_RECEIVED: '申请单接收', APPLICATION_MAPPING_COMPLETED: '药品匹配完成',
  DISPENSE_STATUS_CHANGED: '发药状态变化', RETURN_STATUS_CHANGED: '退药状态变化',
  APPLICATION_CANCELLED: '申请单撤销', PRESCRIPTION_REVIEWED: '处方审核结果'
}

const showMessage = (text, type = 'success') => {
  message.value = { text, type }
  window.setTimeout(() => { if (message.value.text === text) message.value.text = '' }, 3500)
}
const errorText = (error) => error?.response?.data?.message || error?.response?.data || error?.message || '操作失败'

const load = async () => {
  try {
    const [statusResponse, callbackResponse] = await Promise.all([
      props.api.get('/api/his/integration/status'),
      props.api.get('/api/his/callbacks?limit=150')
    ])
    integration.value = statusResponse.data
    callbacks.value = callbackResponse.data || []
  } catch (error) {
    showMessage(errorText(error), 'error')
  }
}

const addItem = () => {
  form.value.items.push({ hisDrugCode: '', drugName: '', specification: '', quantity: 1, unit: '盒', dosage: '', frequency: '', administrationRoute: '', usageInstruction: '' })
}

const removeItem = (index) => {
  if (form.value.items.length > 1) form.value.items.splice(index, 1)
}

const sendApplication = async () => {
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
    const warningText = response.data.warnings?.length ? `，其中 ${response.data.warnings.length} 项待匹配` : ''
    showMessage(`模拟HIS申请已送达药房${warningText}`)
    await load()
  } catch (error) {
    showMessage(errorText(error), 'error')
  } finally {
    sending.value = false
  }
}

const retry = async (event) => {
  try {
    await props.api.post(`/api/his/callbacks/${event.eventId}/retry`)
    showMessage('回传事件已重新排队')
    await load()
  } catch (error) {
    showMessage(errorText(error), 'error')
  }
}

onMounted(load)
</script>

<template>
  <section class="integration-console">
    <header class="console-heading">
      <div>
        <span class="eyebrow">HIS INTEGRATION</span>
        <h2>HIS 联调中心</h2>
      </div>
      <button class="icon-button" title="刷新联调状态" @click="load"><RefreshCw /></button>
    </header>

    <div class="connection-band">
      <ServerCog />
      <div><span>当前模式</span><strong>{{ integration.mode === 'rest' ? '真实 REST HIS' : '本地模拟 HIS' }}</strong></div>
      <div><span>状态回传目标</span><strong>{{ integration.callbackUrl }}</strong></div>
      <div><span>队列轮询</span><strong>{{ integration.callbackPollSeconds }} 秒</strong></div>
      <b class="online"><CheckCircle2 />连接配置有效</b>
    </div>

    <div v-if="message.text" class="notice" :class="message.type">{{ message.text }}</div>

    <div class="console-grid">
      <section class="simulator-panel">
        <div class="panel-title"><div><span>INBOUND</span><h3>模拟门诊处方申请</h3></div><Send /></div>
        <div class="form-grid">
          <label><span>患者编号</span><input v-model="form.patientId" /></label>
          <label><span>患者姓名</span><input v-model="form.patientName" /></label>
          <label><span>患者性别</span><select v-model="form.patientGender"><option>男</option><option>女</option><option>未知</option></select></label>
          <label><span>患者年龄</span><input v-model="form.patientAge" type="number" min="0" max="150" /></label>
          <label><span>就诊号</span><input v-model="form.encounterNo" /></label>
          <label><span>优先级</span><select v-model="form.priority"><option value="NORMAL">普通</option><option value="URGENT">急诊</option></select></label>
          <label><span>科室编码</span><input v-model="form.departmentCode" /></label>
          <label><span>科室名称</span><input v-model="form.departmentName" /></label>
          <label><span>处方医师编号</span><input v-model="form.prescriberId" /></label>
          <label><span>处方医师姓名</span><input v-model="form.prescriberName" /></label>
          <label><span>临床诊断</span><input v-model="form.diagnosis" /></label>
          <label><span>过敏史</span><input v-model="form.allergyInfo" /></label>
        </div>

        <div class="medicine-editor">
          <div class="editor-heading"><strong>药品明细</strong><button @click="addItem">添加药品</button></div>
          <div v-for="(item, index) in form.items" :key="index" class="medicine-editor-row">
            <span class="line-number">{{ index + 1 }}</span>
            <div class="medicine-fields">
              <input v-model="item.hisDrugCode" placeholder="HIS药品编码" />
              <input v-model="item.drugName" placeholder="药品名称" />
              <input v-model="item.specification" placeholder="规格" />
              <input v-model="item.quantity" type="number" min="1" aria-label="申请数量" />
              <select v-model="item.unit" aria-label="申请单位"><option>盒</option><option>瓶</option><option>支</option><option>片</option><option>粒</option></select>
              <input v-model="item.dosage" placeholder="单次剂量，如 0.5g" />
              <input v-model="item.frequency" placeholder="频次，如 每日3次" />
              <input v-model="item.administrationRoute" placeholder="给药途径，如 口服" />
              <input v-model="item.usageInstruction" placeholder="补充用药说明" />
            </div>
            <button class="remove-button" title="删除药品" @click="removeItem(index)"><CircleX /></button>
          </div>
        </div>
        <button class="send-button" :disabled="sending" @click="sendApplication"><Send />{{ sending ? '正在发送' : '发送到药房系统' }}</button>
      </section>

      <section class="callback-panel">
        <div class="panel-title"><div><span>OUTBOUND</span><h3>状态回传时间线</h3></div><RefreshCw /></div>
        <div v-if="callbacks.length === 0" class="empty">尚无状态回传记录</div>
        <div v-else class="callback-list">
          <article v-for="event in callbacks" :key="event.id" class="callback-row">
            <span class="timeline-mark" :class="event.status?.toLowerCase()"></span>
            <div class="callback-main">
              <strong>{{ eventLabels[event.eventType] || event.eventType }}</strong>
              <span>{{ event.patientName }} · {{ event.hisApplicationNo }}</span>
              <small>{{ event.createTime }} · {{ event.applicationStatus }}</small>
              <small v-if="event.lastError" class="error-text">{{ event.lastError }}</small>
            </div>
            <div class="callback-state">
              <b :data-status="event.status">{{ statusLabels[event.status] || event.status }}</b>
              <small>尝试 {{ event.attemptCount }} 次</small>
              <button v-if="event.status === 'FAILED'" @click="retry(event)"><RotateCcw />补发</button>
            </div>
          </article>
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.integration-console { display: grid; gap: 16px; color: #172033; }
.console-heading { display: flex; justify-content: space-between; align-items: center; }.console-heading h2 { margin: 4px 0 0; font-size: 24px; letter-spacing: 0; }.eyebrow { color: #557080; font-size: 11px; font-weight: 800; }
.icon-button { width: 40px; height: 40px; display: grid; place-items: center; border: 1px solid #d5dfe5; border-radius: 6px; background: #fff; cursor: pointer; }.icon-button svg { width: 18px; }
.connection-band { display: grid; grid-template-columns: auto 0.8fr 1.5fr 0.6fr auto; gap: 18px; align-items: center; padding: 15px 18px; border: 1px solid #d9e2e7; background: #f7fafb; }.connection-band > svg { width: 26px; color: #1d6675; }.connection-band div { display: grid; gap: 4px; min-width: 0; }.connection-band span { color: #6b7881; font-size: 11px; }.connection-band strong { font-size: 13px; overflow-wrap: anywhere; }.online { color: #147554; font-size: 12px; display: flex; gap: 6px; align-items: center; white-space: nowrap; }.online svg { width: 16px; }
.notice { padding: 10px 14px; border-left: 4px solid #2f8068; background: #eaf6f1; font-size: 13px; }.notice.error { border-color: #b42318; background: #fff0ee; color: #8e231a; }
.console-grid { display: grid; grid-template-columns: minmax(560px, 1.2fr) minmax(360px, 0.8fr); border: 1px solid #d9e2e7; background: #fff; min-height: 590px; }.simulator-panel, .callback-panel { padding: 20px; min-width: 0; }.simulator-panel { border-right: 1px solid #d9e2e7; }
.panel-title { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }.panel-title div { display: grid; gap: 4px; }.panel-title span { font-size: 10px; color: #6d7b85; font-weight: 800; }.panel-title h3 { margin: 0; font-size: 17px; letter-spacing: 0; }.panel-title > svg { width: 20px; color: #58717e; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }.form-grid label { display: grid; gap: 6px; }.form-grid span { font-size: 11px; color: #61717c; }
input, select { min-width: 0; border: 1px solid #cfd9df; border-radius: 4px; background: #fff; padding: 10px; color: inherit; font: inherit; }
.medicine-editor { margin-top: 20px; border-top: 1px solid #dfe6ea; padding-top: 16px; display: grid; gap: 8px; }.editor-heading { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }.editor-heading strong { font-size: 13px; }.editor-heading button { border: 1px solid #aac5cd; background: #eef7f8; color: #1d6675; border-radius: 4px; padding: 6px 10px; cursor: pointer; }
.medicine-editor-row { display: grid; grid-template-columns: 26px 1fr 34px; gap: 7px; align-items: start; padding: 10px 0; border-bottom: 1px solid #edf1f3; }.medicine-fields { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 7px; }.medicine-fields input, .medicine-fields select { width: 100%; padding: 9px 8px; }.line-number { width: 24px; height: 24px; display: grid; place-items: center; background: #e8eef1; font-size: 11px; font-weight: 800; }.remove-button { width: 32px; height: 32px; display: grid; place-items: center; border: 0; background: transparent; color: #a23b32; cursor: pointer; }.remove-button svg { width: 17px; }
.send-button { margin-top: 18px; min-height: 42px; border: 0; border-radius: 4px; background: #176b5b; color: #fff; padding: 0 18px; font-weight: 800; display: inline-flex; align-items: center; gap: 8px; cursor: pointer; }.send-button svg { width: 17px; }.send-button:disabled { opacity: 0.6; }
.callback-panel { max-height: 650px; overflow: auto; }.callback-list { display: grid; }.callback-row { display: grid; grid-template-columns: 10px 1fr auto; gap: 12px; padding: 13px 0; border-bottom: 1px solid #e4eaed; }.timeline-mark { width: 8px; height: 8px; margin-top: 5px; border-radius: 50%; background: #9caab2; }.timeline-mark.sent { background: #19815f; }.timeline-mark.failed { background: #bd3d31; }.timeline-mark.pending, .timeline-mark.processing { background: #c78924; }
.callback-main, .callback-state { display: grid; gap: 4px; }.callback-main strong { font-size: 13px; }.callback-main span, .callback-main small, .callback-state small { font-size: 11px; color: #6a7882; }.callback-state { justify-items: end; }.callback-state b { padding: 4px 7px; border-radius: 4px; background: #e8edef; font-size: 10px; }.callback-state b[data-status="SENT"] { background: #dff3e9; color: #176349; }.callback-state b[data-status="FAILED"] { background: #fee4e1; color: #992d24; }.callback-state button { border: 1px solid #d59b95; background: #fff; color: #9a3027; padding: 5px 7px; display: flex; align-items: center; gap: 4px; cursor: pointer; }.callback-state button svg { width: 13px; }.error-text { color: #a43127 !important; }.empty { min-height: 220px; display: grid; place-items: center; color: #7d8a93; }
@media (max-width: 1100px) { .console-grid { grid-template-columns: 1fr; }.simulator-panel { border-right: 0; border-bottom: 1px solid #d9e2e7; }.connection-band { grid-template-columns: auto 1fr 1fr; }.connection-band .online { grid-column: 2 / -1; }.medicine-editor-row { grid-template-columns: 26px 1fr 1fr 70px 34px; }.medicine-editor-row input:nth-of-type(3) { grid-column: 2 / 4; } }
@media (max-width: 680px) { .connection-band, .form-grid { grid-template-columns: 1fr; }.connection-band > svg { display: none; }.connection-band .online { grid-column: auto; }.medicine-fields { grid-template-columns: 1fr; }.callback-row { grid-template-columns: 8px 1fr; }.callback-state { grid-column: 2; justify-items: start; } }
</style>
