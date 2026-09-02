<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { CheckCircle2, ClipboardCheck, Download, FilePlus2, RefreshCw, ScanLine, Search } from 'lucide-vue-next'
import { errorMessage } from '../api/client'
import AppDrawer from '../components/ui/AppDrawer.vue'
import { downloadReport } from '../utils/download'

const props = defineProps({
  api: { type: Object, required: true },
  notify: { type: Function, required: true },
  confirmAction: { type: Function, required: true }
})

const emit = defineEmits(['synced'])
const loading = ref(true)
const scanning = ref(false)
const inventoryList = ref([])
const inventoryItems = ref([])
const selectedId = ref(null)
const search = ref('')
const drawerOpen = ref(false)
const createForm = reactive({ title: '月度库存盘点' })
const scanCode = ref('')
const scanInput = ref(null)

const selectedCheck = computed(() => inventoryList.value.find(item => item.id === selectedId.value) || null)
const filteredChecks = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  if (!keyword) return inventoryList.value
  return inventoryList.value.filter(item => [item.checkNo, item.title, item.createdBy, item.status]
    .some(value => String(value || '').toLowerCase().includes(keyword)))
})
const differenceCount = computed(() => inventoryItems.value.filter(item => item.differenceType && item.differenceType !== 'MATCH').length)

const load = async () => {
  loading.value = true
  try {
    const response = await props.api.get('/inventory')
    inventoryList.value = response.data || []
    if (selectedId.value && !inventoryList.value.some(item => item.id === selectedId.value)) {
      selectedId.value = null
      inventoryItems.value = []
    }
    emit('synced', new Date())
  } catch (error) {
    props.notify(errorMessage(error, '盘点单加载失败'), 'error')
  } finally {
    loading.value = false
  }
}

const loadItems = async id => {
  if (!id) return
  selectedId.value = id
  try {
    const response = await props.api.get(`/inventory/${id}/items`)
    inventoryItems.value = response.data || []
    await nextTick()
    if (selectedCheck.value?.status === 'OPEN') scanInput.value?.focus()
  } catch (error) {
    props.notify(errorMessage(error, '盘点明细加载失败'), 'error')
  }
}

const createInventory = async () => {
  if (!createForm.title.trim()) return props.notify('请输入盘点标题', 'error')
  try {
    const response = await props.api.post('/inventory', { title: createForm.title.trim() })
    props.notify('盘点单已创建', 'success')
    drawerOpen.value = false
    await load()
    const created = inventoryList.value.find(item => item.checkNo === response.data?.checkNo) || inventoryList.value[0]
    if (created) await loadItems(created.id)
  } catch (error) {
    props.notify(errorMessage(error, '盘点单创建失败'), 'error')
  }
}

const scan = async () => {
  if (!selectedCheck.value) return props.notify('请先选择盘点单', 'error')
  if (selectedCheck.value.status !== 'OPEN') return props.notify('该盘点单已完成，不能继续扫描', 'error')
  if (!scanCode.value.trim()) return props.notify('请扫描母码或子码', 'error')
  scanning.value = true
  try {
    await props.api.post(`/inventory/${selectedId.value}/scan`, { traceCode: scanCode.value.trim() })
    scanCode.value = ''
    await loadItems(selectedId.value)
    props.notify('盘点扫描已记录', 'success')
  } catch (error) {
    props.notify(errorMessage(error, '盘点扫描失败'), 'error')
  } finally {
    scanning.value = false
  }
}

const complete = async item => {
  const confirmed = await props.confirmAction({
    title: '完成库存盘点',
    message: `确认完成“${item.title}”吗？完成后将不能继续扫描实物追溯码。`,
    confirmLabel: '确认完成',
    tone: 'danger'
  })
  if (!confirmed) return
  try {
    await props.api.post(`/inventory/${item.id}/complete`)
    props.notify('盘点单已完成', 'success')
    await load()
    if (selectedId.value === item.id) await loadItems(item.id)
  } catch (error) {
    props.notify(errorMessage(error, '盘点完成失败'), 'error')
  }
}

const exportInventory = async item => {
  try {
    await downloadReport(props.api, `/reports/inventory/${item.id}.csv`)
    props.notify('盘点报表已开始下载', 'success')
  } catch (error) {
    props.notify(errorMessage(error, '盘点报表下载失败'), 'error')
  }
}

onMounted(load)
</script>

<template>
  <section class="page-stack" aria-labelledby="inventory-heading">
    <header class="page-header">
      <div><h2 id="inventory-heading">库存盘点</h2><p>按盘点单扫描实物追溯码并核对系统差异</p></div>
      <div class="page-actions">
        <button type="button" class="button secondary" :disabled="loading" @click="load"><RefreshCw :size="17" />刷新</button>
        <button type="button" class="button primary" @click="drawerOpen = true"><FilePlus2 :size="17" />新建盘点</button>
      </div>
    </header>

    <div class="inventory-layout">
      <section class="data-panel inventory-list-panel">
        <div class="toolbar inventory-search">
          <label class="search-control"><Search :size="16" /><span class="sr-only">搜索盘点单</span><input v-model="search" aria-label="搜索盘点单" placeholder="搜索单号、标题或创建人" /></label>
          <span class="status-badge" data-tone="neutral">{{ filteredChecks.length }} 单</span>
        </div>
        <div v-if="loading" class="loading-state"><span class="spinner"></span><span>正在加载盘点单</span></div>
        <div v-else-if="filteredChecks.length" class="check-list">
          <button v-for="item in filteredChecks" :key="item.id" type="button" :class="{ selected: selectedId === item.id }" @click="loadItems(item.id)">
            <span class="check-state" :data-open="item.status === 'OPEN'"></span>
            <span><strong>{{ item.title }}</strong><small class="mono">{{ item.checkNo }}</small></span>
            <span><b class="status-badge" :data-tone="item.status === 'OPEN' ? 'warning' : 'success'">{{ item.status === 'OPEN' ? '盘点中' : '已完成' }}</b><small>{{ item.createdBy || '--' }}</small></span>
          </button>
        </div>
        <div v-else class="empty-state"><ClipboardCheck :size="27" /><span>暂无盘点单</span></div>
      </section>

      <section class="data-panel inventory-detail-panel">
        <template v-if="selectedCheck">
          <div class="data-panel-header inventory-detail-header">
            <div><h3>{{ selectedCheck.title }}</h3><p>{{ selectedCheck.checkNo }} · {{ selectedCheck.createdBy || '--' }} · {{ selectedCheck.createTime || '--' }}</p></div>
            <div class="row-actions">
              <span class="status-badge" :data-tone="differenceCount ? 'danger' : 'success'">差异 {{ differenceCount }} 项</span>
              <button type="button" class="button secondary" @click="exportInventory(selectedCheck)"><Download :size="16" />导出</button>
              <button v-if="selectedCheck.status === 'OPEN'" type="button" class="button danger" @click="complete(selectedCheck)"><CheckCircle2 :size="16" />完成盘点</button>
            </div>
          </div>

          <form v-if="selectedCheck.status === 'OPEN'" class="scan-bar" @submit.prevent="scan">
            <label><ScanLine :size="18" /><span class="sr-only">扫描实物追溯码</span><input ref="scanInput" v-model="scanCode" class="mono" aria-label="扫描实物追溯码" placeholder="扫描母码或拆零子码" /></label>
            <button type="submit" class="button primary" :disabled="scanning">{{ scanning ? '正在记录' : '记录扫描' }}</button>
          </form>

          <div v-if="inventoryItems.length" class="table-scroll inventory-items-table">
            <table>
              <caption>{{ selectedCheck.title }}盘点明细</caption>
              <thead><tr><th>追溯码</th><th>码类型</th><th>药品</th><th>系统状态</th><th>实际状态</th><th>差异</th><th>扫描人</th><th>扫描时间</th></tr></thead>
              <tbody>
                <tr v-for="item in inventoryItems" :key="item.id || item.traceCode">
                  <td class="mono">{{ item.traceCode }}</td><td>{{ item.codeType || '--' }}</td><td><strong>{{ item.drugName || '--' }}</strong></td><td>{{ item.expectedStatus || '--' }}</td><td>{{ item.actualStatus || '--' }}</td><td><span class="status-badge" :data-tone="item.differenceType === 'MATCH' ? 'success' : 'danger'">{{ item.differenceType === 'MATCH' ? '一致' : item.differenceType || '--' }}</span></td><td>{{ item.scannedBy || '--' }}</td><td class="nowrap">{{ item.scanTime || '--' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-else class="empty-state"><ScanLine :size="27" /><span>{{ selectedCheck.status === 'OPEN' ? '扫描第一件实物开始盘点' : '该盘点单没有扫描明细' }}</span></div>
        </template>
        <div v-else class="empty-state detail-empty"><ClipboardCheck :size="30" /><span>从左侧选择盘点单查看明细</span></div>
      </section>
    </div>

    <AppDrawer :open="drawerOpen" title="新建盘点单" description="创建后即可在盘点工作区连续扫描实物追溯码" width="460px" @close="drawerOpen = false">
      <form class="form-stack" @submit.prevent="createInventory">
        <label class="field"><span>盘点标题</span><input v-model="createForm.title" aria-label="盘点标题" autofocus placeholder="如：2026 年 9 月月度盘点" /></label>
      </form>
      <template #footer>
        <button type="button" class="button secondary" @click="drawerOpen = false">取消</button>
        <button type="button" class="button primary" @click="createInventory">创建盘点单</button>
      </template>
    </AppDrawer>
  </section>
</template>

<style scoped>
.inventory-layout { display: grid; grid-template-columns: 360px minmax(0, 1fr); min-height: calc(100vh - 150px); gap: 12px; }
.inventory-list-panel, .inventory-detail-panel { min-height: 0; }
.inventory-search { border: 0; border-bottom: 1px solid var(--line); border-radius: 0; box-shadow: none; }
.inventory-search .search-control { min-width: 0; flex: 1; }
.check-list { overflow-y: auto; max-height: calc(100vh - 215px); }
.check-list > button { display: grid; width: 100%; min-height: 66px; grid-template-columns: 5px minmax(0, 1fr) auto; align-items: center; gap: 11px; padding: 9px 11px; border: 0; border-bottom: 1px solid #e8edf0; background: #fff; color: inherit; text-align: left; }
.check-list > button:hover, .check-list > button.selected { background: #f0f7f9; }
.check-list > button.selected { box-shadow: inset 3px 0 var(--primary); }
.check-state { width: 5px; height: 34px; border-radius: 2px; background: var(--success); }
.check-state[data-open="true"] { background: var(--warning); }
.check-list button > span:nth-child(2), .check-list button > span:nth-child(3) { display: grid; min-width: 0; gap: 3px; }
.check-list strong, .check-list small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.check-list strong { font-size: 12px; }
.check-list small { color: var(--muted); font-size: 10px; }
.check-list button > span:nth-child(3) { justify-items: end; }
.inventory-detail-header { min-height: 62px; }
.scan-bar { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; padding: 10px 12px; border-bottom: 1px solid var(--line); background: var(--panel-subtle); }
.scan-bar label { display: flex; align-items: center; gap: 8px; padding-left: 11px; border: 1px solid var(--line-strong); border-radius: var(--radius); background: #fff; color: var(--primary); }
.scan-bar label:focus-within { border-color: var(--primary); box-shadow: 0 0 0 3px rgba(11, 111, 159, 0.1); }
.scan-bar input { border: 0; box-shadow: none; }
.scan-bar input:focus { box-shadow: none; }
.inventory-items-table { max-height: calc(100vh - 280px); }
.detail-empty { min-height: 520px; }
</style>
