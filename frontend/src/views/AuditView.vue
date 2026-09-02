<script setup>
import { computed, onMounted, ref } from 'vue'
import { ChartNoAxesCombined, Download, RefreshCw, Search, ShieldCheck } from 'lucide-vue-next'
import { errorMessage } from '../api/client'
import { roleLabels } from '../config/navigation'
import { downloadReport } from '../utils/download'

const props = defineProps({
  api: { type: Object, required: true },
  notify: { type: Function, required: true }
})

const emit = defineEmits(['synced'])
const loading = ref(true)
const auditList = ref([])
const search = ref('')
const resultFilter = ref('')

const filteredAudits = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  return auditList.value.filter(item => {
    const matchesResult = !resultFilter.value || item.result === resultFilter.value
    const matchesKeyword = !keyword || [item.operatorName, item.operatorRole, item.action, item.targetType, item.targetId, item.message]
      .some(value => String(value || '').toLowerCase().includes(keyword))
    return matchesResult && matchesKeyword
  })
})

const failureCount = computed(() => auditList.value.filter(item => item.result !== 'SUCCESS').length)

const load = async () => {
  loading.value = true
  try {
    const response = await props.api.get('/audit/recent?limit=100')
    auditList.value = response.data || []
    emit('synced', new Date())
  } catch (error) {
    props.notify(errorMessage(error, '审计日志加载失败'), 'error')
  } finally {
    loading.value = false
  }
}

const exportReport = async path => {
  try {
    await downloadReport(props.api, path)
    props.notify('报表已开始下载', 'success')
  } catch (error) {
    props.notify(errorMessage(error, '报表下载失败'), 'error')
  }
}

onMounted(load)
</script>

<template>
  <section class="page-stack" aria-labelledby="audit-heading">
    <header class="page-header">
      <div><h2 id="audit-heading">审计与报表</h2><p>查询最近操作记录并导出业务留痕</p></div>
      <div class="page-actions">
        <button type="button" class="button secondary" :disabled="loading" @click="load"><RefreshCw :size="17" />刷新</button>
        <button type="button" class="button secondary" @click="exportReport('/reports/dispense.csv')"><Download :size="17" />出入库流水</button>
        <button type="button" class="button primary" @click="exportReport('/reports/audit.csv')"><Download :size="17" />审计日志</button>
      </div>
    </header>

    <div class="metric-grid audit-metrics">
      <div class="metric-card"><span>最近审计记录</span><strong>{{ auditList.length }}</strong><small>最近 100 条操作</small><span class="metric-icon"><ChartNoAxesCombined :size="19" /></span></div>
      <div class="metric-card"><span>成功操作</span><strong>{{ auditList.length - failureCount }}</strong><small>业务执行成功</small><span class="metric-icon success"><ShieldCheck :size="19" /></span></div>
      <div class="metric-card"><span>异常结果</span><strong>{{ failureCount }}</strong><small>需要人工复核</small><span class="metric-icon" :class="failureCount ? 'danger' : 'success'"><ShieldCheck :size="19" /></span></div>
    </div>

    <section class="data-panel">
      <div class="toolbar">
        <div class="toolbar-group">
          <label class="search-control"><Search :size="16" /><span class="sr-only">搜索审计日志</span><input v-model="search" aria-label="搜索审计日志" placeholder="搜索操作人、动作、对象或说明" /></label>
          <label><span class="sr-only">执行结果</span><select v-model="resultFilter" aria-label="审计执行结果"><option value="">全部结果</option><option value="SUCCESS">成功</option><option value="FAILED">失败</option></select></label>
          <span class="status-badge" data-tone="neutral">{{ filteredAudits.length }} 条</span>
        </div>
      </div>

      <div v-if="loading" class="loading-state"><span class="spinner"></span><span>正在加载审计日志</span></div>
      <div v-else-if="filteredAudits.length" class="table-scroll audit-table">
        <table>
          <caption>系统操作审计日志</caption>
          <thead><tr><th>时间</th><th>操作人</th><th>角色</th><th>动作</th><th>对象</th><th>结果</th><th>说明</th></tr></thead>
          <tbody>
            <tr v-for="item in filteredAudits" :key="item.id">
              <td class="nowrap">{{ item.createTime || '--' }}</td><td><strong>{{ item.operatorName || '--' }}</strong></td><td>{{ roleLabels[item.operatorRole] || item.operatorRole || '--' }}</td><td class="mono">{{ item.action || '--' }}</td><td>{{ item.targetType || '--' }} / {{ item.targetId || '--' }}</td><td><span class="status-badge" :data-tone="item.result === 'SUCCESS' ? 'success' : 'danger'">{{ item.result === 'SUCCESS' ? '成功' : item.result || '异常' }}</span></td><td class="truncate" :title="item.message">{{ item.message || '--' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state"><ShieldCheck :size="28" /><span>{{ search || resultFilter ? '没有匹配的审计记录' : '暂无审计记录' }}</span></div>
    </section>
  </section>
</template>

<style scoped>
.audit-metrics { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.toolbar select { width: 126px; }
.audit-table { max-height: calc(100vh - 330px); }
</style>
