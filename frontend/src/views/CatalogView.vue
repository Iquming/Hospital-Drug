<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { BookOpen, CirclePlus, Pencil, RefreshCw, Search, ShieldBan } from 'lucide-vue-next'
import { errorMessage } from '../api/client'
import AppDrawer from '../components/ui/AppDrawer.vue'

const props = defineProps({
  api: { type: Object, required: true },
  notify: { type: Function, required: true },
  confirmAction: { type: Function, required: true }
})

const emit = defineEmits(['synced'])
const loading = ref(true)
const saving = ref(false)
const catalog = ref([])
const search = ref('')
const statusFilter = ref('')
const controlCategoryFilter = ref('')
const drawerOpen = ref(false)
const drawerSnapshot = ref('')

const controlCategoryOptions = [
  { value: 'GENERAL', label: '无（普通药品）' },
  { value: 'NARCOTIC', label: '麻醉药品' },
  { value: 'PSYCHOTROPIC_I', label: '第一类精神药品' },
  { value: 'PSYCHOTROPIC_II', label: '第二类精神药品' },
  { value: 'MEDICAL_TOXIC', label: '医疗用毒性药品' }
]
const controlCategoryLabels = Object.fromEntries(controlCategoryOptions.map(item => [item.value, item.label]))
const isSpecialCategory = category => Boolean(category && category !== 'GENERAL')

const defaultForm = () => ({
  id: null,
  drugName: '',
  specification: '',
  dosageForm: '',
  manufacturer: '',
  controlCategory: 'GENERAL',
  isSplitAllowed: false,
  packageUnit: '盒',
  minUnit: '盒',
  minUnitsPerPackage: 1,
  lowStockThreshold: 50,
  status: 'ENABLED'
})

const form = reactive(defaultForm())
const drawerDirty = computed(() => drawerOpen.value && JSON.stringify(form) !== drawerSnapshot.value)
const filteredCatalog = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  return catalog.value.filter(item => {
    const matchesStatus = !statusFilter.value || item.status === statusFilter.value
    const matchesCategory = !controlCategoryFilter.value || item.controlCategory === controlCategoryFilter.value
    const matchesKeyword = !keyword || [
      item.drugName,
      item.specification,
      item.dosageForm,
      item.manufacturer,
      controlCategoryLabels[item.controlCategory]
    ]
      .some(value => String(value || '').toLowerCase().includes(keyword))
    return matchesStatus && matchesCategory && matchesKeyword
  })
})

const resetForm = values => {
  Object.assign(form, defaultForm(), values || {})
}

const load = async () => {
  loading.value = true
  try {
    const response = await props.api.get('/catalog')
    catalog.value = response.data || []
    emit('synced', new Date())
  } catch (error) {
    props.notify(errorMessage(error, '药品档案加载失败'), 'error')
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  resetForm()
  drawerSnapshot.value = JSON.stringify(form)
  drawerOpen.value = true
}

const openEdit = item => {
  resetForm(item)
  drawerSnapshot.value = JSON.stringify(form)
  drawerOpen.value = true
}

const closeDrawer = async () => {
  if (drawerDirty.value) {
    const confirmed = await props.confirmAction({
      title: '放弃档案修改',
      message: '当前药品档案有未保存内容，确认关闭吗？',
      confirmLabel: '放弃修改',
      tone: 'warning'
    })
    if (!confirmed) return
  }
  drawerOpen.value = false
}

const save = async () => {
  if (!form.drugName.trim()) return props.notify('请输入药品名称', 'error')
  if (!form.packageUnit.trim() || !form.minUnit.trim()) return props.notify('请填写包装单位和最小单位', 'error')
  saving.value = true
  try {
    if (form.id) {
      await props.api.put(`/catalog/${form.id}`, form)
      props.notify('药品档案已更新', 'success')
    } else {
      await props.api.post('/catalog', form)
      props.notify('药品档案已创建', 'success')
    }
    drawerOpen.value = false
    await load()
  } catch (error) {
    props.notify(errorMessage(error, '药品档案保存失败'), 'error')
  } finally {
    saving.value = false
  }
}

const disable = async item => {
  const confirmed = await props.confirmAction({
    title: '停用药品档案',
    message: `确认停用“${item.drugName}”吗？停用后不会出现在新的药品映射选项中。`,
    confirmLabel: '确认停用',
    tone: 'danger'
  })
  if (!confirmed) return
  try {
    await props.api.delete(`/catalog/${item.id}`)
    props.notify('药品档案已停用', 'success')
    await load()
  } catch (error) {
    props.notify(errorMessage(error, '药品档案停用失败'), 'error')
  }
}

onMounted(load)
</script>

<template>
  <section class="page-stack" aria-labelledby="catalog-heading">
    <header class="page-header">
      <div><h2 id="catalog-heading">药品基础档案</h2><p>维护药品属性、包装换算、特殊管理分类与库存阈值</p></div>
      <div class="page-actions">
        <button type="button" class="button secondary" :disabled="loading" @click="load"><RefreshCw :size="17" />刷新</button>
        <button type="button" class="button primary" @click="openCreate"><CirclePlus :size="17" />新增档案</button>
      </div>
    </header>

    <section class="data-panel">
      <div class="toolbar">
        <div class="toolbar-group">
          <label class="search-control"><Search :size="16" /><span class="sr-only">搜索药品档案</span><input v-model="search" aria-label="搜索药品档案" placeholder="搜索药品、规格、剂型或厂家" /></label>
          <label><span class="sr-only">特殊管理属性</span><select v-model="controlCategoryFilter" class="category-filter" aria-label="特殊管理属性筛选"><option value="">全部管理属性</option><option v-for="option in controlCategoryOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select></label>
          <label><span class="sr-only">档案状态</span><select v-model="statusFilter" aria-label="药品档案状态"><option value="">全部状态</option><option value="ENABLED">启用</option><option value="DISABLED">停用</option></select></label>
          <span class="status-badge" data-tone="neutral">{{ filteredCatalog.length }} 条</span>
        </div>
      </div>

      <div v-if="loading" class="loading-state"><span class="spinner"></span><span>正在加载药品档案</span></div>
      <div v-else-if="filteredCatalog.length" class="table-scroll catalog-table">
        <table>
          <caption>药品基础档案列表</caption>
          <thead><tr><th>药品</th><th>规格</th><th>剂型</th><th>生产厂家</th><th>特殊管理属性</th><th>拆零</th><th>包装换算</th><th>低库存阈值</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="item in filteredCatalog" :key="item.id">
              <td><strong>{{ item.drugName }}</strong></td>
              <td>{{ item.specification || '--' }}</td>
              <td>{{ item.dosageForm || '--' }}</td>
              <td class="truncate" :title="item.manufacturer">{{ item.manufacturer || '--' }}</td>
              <td><span v-if="isSpecialCategory(item.controlCategory)" class="status-badge" :data-tone="item.controlCategory === 'PSYCHOTROPIC_II' ? 'warning' : 'danger'">{{ controlCategoryLabels[item.controlCategory] || item.controlCategory }}</span><span v-else class="empty-cell">--</span></td>
              <td><span class="status-badge" :data-tone="item.isSplitAllowed ? 'success' : 'neutral'">{{ item.isSplitAllowed ? '允许拆零' : '整包装' }}</span></td>
              <td>{{ item.packageUnit }} / {{ item.minUnitsPerPackage }}{{ item.minUnit }}</td>
              <td>{{ item.lowStockThreshold }}</td>
              <td><span class="status-badge" :data-tone="item.status === 'ENABLED' ? 'success' : 'neutral'">{{ item.status === 'ENABLED' ? '启用' : '停用' }}</span></td>
              <td>
                <div class="row-actions">
                  <button type="button" class="text-button" @click="openEdit(item)"><Pencil :size="14" />编辑</button>
                  <button v-if="item.status === 'ENABLED'" type="button" class="text-button danger" @click="disable(item)"><ShieldBan :size="14" />停用</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state"><BookOpen :size="28" /><span>{{ search || statusFilter || controlCategoryFilter ? '没有匹配的药品档案' : '暂无药品档案' }}</span><button v-if="!search && !statusFilter && !controlCategoryFilter" type="button" class="button primary" @click="openCreate">新增第一条档案</button></div>
    </section>

    <AppDrawer :open="drawerOpen" :title="form.id ? '编辑药品档案' : '新增药品档案'" description="特殊管理属性将决定处方是否进入特殊药品人工复核" width="540px" @close="closeDrawer">
      <form class="form-stack" @submit.prevent="save">
        <div class="form-grid">
          <label class="field span-2"><span>药品名称</span><input v-model="form.drugName" aria-label="药品名称" required autofocus placeholder="请输入药品通用名称" /></label>
          <label class="field"><span>规格</span><input v-model="form.specification" aria-label="药品规格" placeholder="如：0.5g×20片" /></label>
          <label class="field"><span>剂型</span><input v-model="form.dosageForm" aria-label="药品剂型" placeholder="如：片剂" /></label>
          <label class="field span-2"><span>生产厂家</span><input v-model="form.manufacturer" aria-label="生产厂家" placeholder="请输入生产厂家" /></label>
          <label class="field span-2"><span>特殊管理属性</span><select v-model="form.controlCategory" aria-label="特殊管理属性"><option v-for="option in controlCategoryOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select><small>普通药品选择“无”，界面不显示特殊标识，也不触发特殊药品人工复核。</small></label>
          <label class="field"><span>整包装单位</span><input v-model="form.packageUnit" aria-label="整包装单位" required placeholder="如：盒" /></label>
          <label class="field"><span>最小单位</span><input v-model="form.minUnit" aria-label="最小单位" required placeholder="如：片" /></label>
          <label class="field"><span>每包装最小单位数</span><input v-model.number="form.minUnitsPerPackage" aria-label="每包装最小单位数" type="number" min="1" /></label>
          <label class="field"><span>低库存阈值</span><input v-model.number="form.lowStockThreshold" aria-label="低库存阈值" type="number" min="1" /></label>
          <fieldset class="checkbox-fieldset">
            <legend class="sr-only">拆零设置</legend>
            <label class="checkbox-field"><input v-model="form.isSplitAllowed" aria-label="允许拆零调剂" type="checkbox" /><span>允许拆零调剂</span></label>
          </fieldset>
          <label class="field"><span>档案状态</span><select v-model="form.status" aria-label="档案状态"><option value="ENABLED">启用</option><option value="DISABLED">停用</option></select></label>
        </div>
      </form>
      <template #footer>
        <button type="button" class="button secondary" @click="closeDrawer">取消</button>
        <button type="button" class="button primary" :disabled="saving" @click="save">{{ saving ? '正在保存' : '保存档案' }}</button>
      </template>
    </AppDrawer>
  </section>
</template>

<style scoped>
.toolbar select { width: 128px; }
.toolbar .category-filter { width: 180px; }
.catalog-table { max-height: calc(100vh - 220px); }
.empty-cell { color: var(--muted); }
</style>
