<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { BarChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import * as echarts from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import {
  AlertTriangle,
  Boxes,
  CheckCircle2,
  CircleAlert,
  ClipboardList,
  PackageSearch,
  RefreshCw,
  SendHorizontal
} from 'lucide-vue-next'
import { errorMessage } from '../api/client'

echarts.use([PieChart, BarChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const props = defineProps({
  api: { type: Object, required: true },
  notify: { type: Function, required: true }
})

const emit = defineEmits(['navigate', 'synced'])
const loading = ref(true)
const drugs = ref([])
const records = ref([])
const applications = ref([])
const summary = ref({})
const stockStatus = ref({})
const alerts = ref({ lowStock: [], expired: [], availableSplitCodes: [], longIdleStock: [] })
const pieElement = ref(null)
const barElement = ref(null)
let pieChart = null
let barChart = null
let resizeObserver = null

const totalStock = computed(() => summary.value.totalStock ?? drugs.value.reduce((sum, item) => sum + (item.quantity || 0), 0))
const inStockCount = computed(() => summary.value.inStockCount ?? drugs.value.filter(item => item.quantity > 0).length)
const lowStockCount = computed(() => summary.value.lowStockCount ?? drugs.value.filter(item => item.quantity > 0 && item.quantity < 50).length)
const pendingApplications = computed(() => applications.value.filter(item => ['RECEIVED', 'MAPPING_REQUIRED', 'REVIEW_PENDING', 'READY', 'PARTIALLY_DISPENSED'].includes(item.status)).length)
const urgentApplications = computed(() => applications.value.filter(item => item.priority === 'URGENT' && !['DISPENSED', 'CANCELLED', 'RETURNED'].includes(item.status)).length)
const callbackFailures = computed(() => applications.value.filter(item => item.callbackStatus === 'FAILED').length)

const nearExpiry = computed(() => {
  const today = Date.now()
  return drugs.value
    .filter(item => {
      if (!item.expireDate || item.quantity <= 0) return false
      const days = (new Date(item.expireDate).getTime() - today) / 86400000
      return days >= 0 && days <= 90
    })
    .sort((a, b) => new Date(a.expireDate) - new Date(b.expireDate))
})

const operationHealth = computed(() => {
  if (callbackFailures.value || alerts.value.expired?.length) return { label: '存在高风险事项', tone: 'danger' }
  if (urgentApplications.value || lowStockCount.value || nearExpiry.value.length) return { label: '需要优先处理', tone: 'warning' }
  return { label: '运行平稳', tone: 'success' }
})

const daysLeft = date => Math.max(0, Math.ceil((new Date(date) - new Date()) / 86400000))
const dateOnly = value => value ? String(value).split('T')[0] : '--'
const timeOnly = value => value ? String(value).split(' ')[1] || String(value) : '--'
const recordOwner = record => record.patientName || record.patientId || '系统业务'

const renderCharts = async () => {
  await nextTick()
  if (!pieElement.value || !barElement.value) return
  pieChart?.dispose()
  barChart?.dispose()
  pieChart = echarts.init(pieElement.value)
  barChart = echarts.init(barElement.value)

  const distribution = [...drugs.value]
    .filter(item => item.quantity > 0)
    .sort((a, b) => b.quantity - a.quantity)
    .slice(0, 6)
  const replenishment = [...drugs.value]
    .filter(item => item.quantity >= 0)
    .sort((a, b) => a.quantity - b.quantity)
    .slice(0, 6)

  pieChart.setOption({
    color: ['#0b6f9f', '#197d70', '#5b7f8e', '#a9680c', '#667a98', '#8b6e8e'],
    tooltip: { trigger: 'item' },
    legend: { bottom: 2, type: 'scroll', textStyle: { color: '#637887', fontSize: 11 } },
    series: [{
      name: '库存量',
      type: 'pie',
      radius: ['46%', '70%'],
      center: ['50%', '44%'],
      itemStyle: { borderColor: '#fff', borderWidth: 2 },
      label: { color: '#334b5c', fontSize: 11 },
      data: distribution.map(item => ({ value: item.quantity, name: item.drugName }))
    }]
  })

  barChart.setOption({
    color: ['#a9680c'],
    tooltip: { trigger: 'axis' },
    grid: { left: 42, right: 16, top: 24, bottom: 70 },
    xAxis: {
      type: 'category',
      data: replenishment.map(item => item.drugName),
      axisLabel: { interval: 0, rotate: 20, color: '#637887', fontSize: 10 },
      axisLine: { lineStyle: { color: '#c3d0d9' } }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#637887', fontSize: 10 },
      splitLine: { lineStyle: { color: '#e8edf0' } }
    },
    series: [{
      name: '当前库存',
      type: 'bar',
      barMaxWidth: 28,
      data: replenishment.map(item => item.quantity),
      label: { show: true, position: 'top', color: '#8c570c', fontSize: 10 }
    }]
  })
}

const load = async () => {
  loading.value = true
  try {
    const [drugResponse, recordResponse, summaryResponse, statusResponse, alertResponse, applicationResponse] = await Promise.all([
      props.api.get('/list'),
      props.api.get('/records/recent?limit=50').catch(() => props.api.get('/records')),
      props.api.get('/dashboard/summary').catch(() => ({ data: {} })),
      props.api.get('/stock/status').catch(() => ({ data: {} })),
      props.api.get('/alerts/enhanced').catch(() => ({ data: alerts.value })),
      props.api.get('/api/pharmacy/applications').catch(() => ({ data: [] }))
    ])
    drugs.value = drugResponse.data || []
    records.value = recordResponse.data || []
    summary.value = summaryResponse.data || {}
    stockStatus.value = statusResponse.data || {}
    alerts.value = alertResponse.data || alerts.value
    applications.value = applicationResponse.data || []
    emit('synced', new Date())
    await renderCharts()
  } catch (error) {
    props.notify(errorMessage(error, '运营数据加载失败'), 'error')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await load()
  resizeObserver = new ResizeObserver(() => {
    pieChart?.resize()
    barChart?.resize()
  })
  if (pieElement.value) resizeObserver.observe(pieElement.value)
  if (barElement.value) resizeObserver.observe(barElement.value)
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  pieChart?.dispose()
  barChart?.dispose()
})
</script>

<template>
  <section class="page-stack dashboard-view" aria-labelledby="dashboard-heading">
    <header class="page-header">
      <div>
        <h2 id="dashboard-heading">今日药事运行</h2>
        <p>优先处理处方、库存风险和 HIS 回传异常</p>
      </div>
      <div class="page-actions">
        <span class="status-badge" :data-tone="operationHealth.tone">{{ operationHealth.label }}</span>
        <button type="button" class="button secondary" :disabled="loading" @click="load"><RefreshCw :size="17" />刷新数据</button>
      </div>
    </header>

    <div v-if="loading" class="loading-state"><span class="spinner"></span><span>正在汇总业务数据</span></div>

    <template v-else>
      <div class="metric-grid dashboard-metrics">
        <button type="button" class="metric-card actionable" @click="emit('navigate', 'hisApplications')">
          <span>待处理申请</span><strong>{{ pendingApplications }}</strong><small>进入处方调剂队列</small>
          <span class="metric-icon"><ClipboardList :size="19" /></span>
        </button>
        <button type="button" class="metric-card actionable" @click="emit('navigate', 'hisApplications')">
          <span>急诊优先</span><strong>{{ urgentApplications }}</strong><small>需要优先核对与发药</small>
          <span class="metric-icon danger"><AlertTriangle :size="19" /></span>
        </button>
        <button type="button" class="metric-card actionable" @click="emit('navigate', 'pharmacy')">
          <span>低库存药品</span><strong>{{ lowStockCount }}</strong><small>低于补货阈值</small>
          <span class="metric-icon warning"><PackageSearch :size="19" /></span>
        </button>
        <button type="button" class="metric-card actionable" @click="emit('navigate', 'hisApplications')">
          <span>回传异常</span><strong>{{ callbackFailures }}</strong><small>HIS 状态未成功送达</small>
          <span class="metric-icon" :class="callbackFailures ? 'danger' : 'success'"><SendHorizontal :size="19" /></span>
        </button>
      </div>

      <div class="operational-strip">
        <div><span>药品总库存</span><strong>{{ totalStock }}</strong><small>最小可发单位</small></div>
        <div><span>在库单品</span><strong>{{ inStockCount }}</strong><small>正常 {{ stockStatus.normal ?? '--' }}</small></div>
        <div><span>近效期</span><strong>{{ nearExpiry.length }}</strong><small>90 天内到期</small></div>
        <div><span>过期在库</span><strong>{{ alerts.expired?.length || 0 }}</strong><small>禁止继续发放</small></div>
        <div><span>长期未动销</span><strong>{{ alerts.longIdleStock?.length || 0 }}</strong><small>建议复核周转</small></div>
      </div>

      <div class="dashboard-grid">
        <section class="data-panel chart-panel" aria-labelledby="distribution-title">
          <div class="data-panel-header"><div><h3 id="distribution-title">库存资产分布</h3><p>当前库存量最高的药品</p></div><Boxes :size="18" /></div>
          <div ref="pieElement" class="chart-canvas" role="img" aria-label="库存资产分布饼图"></div>
        </section>
        <section class="data-panel chart-panel" aria-labelledby="replenishment-title">
          <div class="data-panel-header"><div><h3 id="replenishment-title">补货关注</h3><p>当前库存量最低的药品</p></div><CircleAlert :size="18" /></div>
          <div ref="barElement" class="chart-canvas" role="img" aria-label="低库存药品柱状图"></div>
        </section>
      </div>

      <div class="risk-grid">
        <section class="data-panel">
          <div class="data-panel-header"><div><h3>近效期重点复核</h3><p>按剩余有效天数升序排列</p></div><span class="status-badge" data-tone="warning">{{ nearExpiry.length }} 项</span></div>
          <div v-if="nearExpiry.length" class="table-scroll risk-table-scroll">
            <table>
              <caption>90 天内到期药品列表</caption>
              <thead><tr><th>药品</th><th>追溯码</th><th>批号</th><th>库存</th><th>有效期</th><th>剩余</th></tr></thead>
              <tbody>
                <tr v-for="item in nearExpiry" :key="item.id">
                  <td><strong>{{ item.drugName }}</strong></td>
                  <td class="mono">{{ item.traceCode }}</td>
                  <td>{{ item.batchNumber || '--' }}</td>
                  <td>{{ item.quantity }}</td>
                  <td class="nowrap">{{ dateOnly(item.expireDate) }}</td>
                  <td><span class="status-badge" :data-tone="daysLeft(item.expireDate) <= 30 ? 'danger' : 'warning'">{{ daysLeft(item.expireDate) }} 天</span></td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-else class="empty-state"><CheckCircle2 :size="26" /><span>当前没有近效期药品</span></div>
        </section>

        <section class="data-panel recent-events">
          <div class="data-panel-header"><div><h3>最近业务流水</h3><p>最近 8 条出入库记录</p></div></div>
          <div v-if="records.length" class="event-list">
            <article v-for="record in records.slice(0, 8)" :key="record.id">
              <span class="event-mark"></span>
              <div><strong>{{ record.drugName }}</strong><small>{{ recordOwner(record) }}</small></div>
              <time class="mono">{{ timeOnly(record.dispenseTime) }}</time>
            </article>
          </div>
          <div v-else class="empty-state"><ClipboardList :size="26" /><span>暂无业务流水</span></div>
        </section>
      </div>
    </template>
  </section>
</template>

<style scoped>
.dashboard-metrics .metric-card { text-align: left; }
.metric-card.actionable { color: inherit; cursor: pointer; }
.metric-card.actionable:hover { border-color: #9bb8c7; box-shadow: 0 4px 14px rgba(23, 43, 58, 0.08); }
.operational-strip { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); border: 1px solid var(--line); border-radius: var(--radius); background: var(--panel); }
.operational-strip > div { display: grid; gap: 2px; padding: 11px 14px; border-right: 1px solid var(--line); }
.operational-strip > div:last-child { border-right: 0; }
.operational-strip span, .operational-strip small { color: var(--muted); font-size: 10px; }
.operational-strip strong { font-size: 18px; }
.dashboard-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.chart-panel { min-height: 340px; }
.chart-panel .data-panel-header > svg { color: var(--muted); }
.chart-canvas { width: 100%; height: 286px; }
.risk-grid { display: grid; grid-template-columns: minmax(620px, 1.45fr) minmax(320px, 0.55fr); gap: 12px; }
.risk-table-scroll { max-height: 334px; }
.recent-events { min-width: 0; }
.event-list { display: grid; }
.event-list article { display: grid; min-height: 48px; grid-template-columns: 8px minmax(0, 1fr) auto; align-items: center; gap: 10px; padding: 7px 13px; border-bottom: 1px solid #e8edf0; }
.event-list article:last-child { border-bottom: 0; }
.event-mark { width: 7px; height: 7px; border-radius: 50%; background: var(--teal); }
.event-list article > div { display: grid; min-width: 0; }
.event-list strong, .event-list small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.event-list strong { font-size: 12px; }
.event-list small { color: var(--muted); font-size: 10px; }
.event-list time { color: var(--muted); }
@media (max-width: 1280px) {
  .risk-grid { grid-template-columns: 1fr; }
  .operational-strip { grid-template-columns: repeat(3, 1fr); }
  .operational-strip > div:nth-child(3) { border-right: 0; }
  .operational-strip > div:nth-child(n + 4) { border-top: 1px solid var(--line); }
}
</style>
