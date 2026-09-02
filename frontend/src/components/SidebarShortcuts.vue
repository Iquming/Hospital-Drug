<script setup>
import { computed, ref, watch } from 'vue'
import { Check, ChevronDown, ChevronUp, Plus, RotateCcw, Settings2, Trash2 } from 'lucide-vue-next'
import { moduleById, modulesForRole } from '../config/navigation'
import AppDrawer from './ui/AppDrawer.vue'

const props = defineProps({
  currentTab: { type: String, default: 'dashboard' },
  role: { type: String, default: '' },
  username: { type: String, default: '' },
  collapsed: { type: Boolean, default: false }
})

const emit = defineEmits(['navigate'])
const isEditing = ref(false)
const shortcuts = ref([])

const defaultIds = {
  ADMIN: ['dashboard', 'hisApplications', 'pharmacy', 'inventory', 'hisIntegration'],
  PHARMACIST: ['hisApplications', 'pharmacy', 'catalog', 'inventory'],
  NURSE: ['hisApplications', 'dashboard']
}

const allowedModules = computed(() => modulesForRole(props.role))
const availableModules = computed(() => allowedModules.value.filter(
  item => !shortcuts.value.some(shortcut => shortcut.id === item.id)
))
const storageKey = computed(() => `hospitalDrugShortcuts:${props.username || props.role || 'guest'}`)
const normalizedLabel = (label, fallback) => String(label || '').trim().slice(0, 12) || fallback

const defaults = () => (defaultIds[props.role] || ['dashboard'])
  .map(id => moduleById(id))
  .filter(Boolean)
  .map(item => ({ id: item.id, label: item.label }))

const normalize = value => {
  if (!Array.isArray(value)) return []
  const allowed = new Set(allowedModules.value.map(item => item.id))
  const seen = new Set()
  return value.flatMap(item => {
    const module = moduleById(item?.id)
    if (!module || !allowed.has(module.id) || seen.has(module.id)) return []
    seen.add(module.id)
    return [{ id: module.id, label: normalizedLabel(item.label, module.label) }]
  })
}

const save = () => localStorage.setItem(storageKey.value, JSON.stringify(shortcuts.value))
const load = () => {
  try {
    const saved = normalize(JSON.parse(localStorage.getItem(storageKey.value) || '[]'))
    shortcuts.value = saved.length ? saved : defaults()
  } catch {
    shortcuts.value = defaults()
  }
}

const addShortcut = item => {
  shortcuts.value.push({ id: item.id, label: item.label })
  save()
}

const removeShortcut = index => {
  shortcuts.value.splice(index, 1)
  save()
}

const moveShortcut = (index, direction) => {
  const target = index + direction
  if (target < 0 || target >= shortcuts.value.length) return
  const [item] = shortcuts.value.splice(index, 1)
  shortcuts.value.splice(target, 0, item)
  save()
}

const updateLabel = (shortcut, value) => {
  shortcut.label = normalizedLabel(value, moduleById(shortcut.id)?.label || '常用功能')
  save()
}

const resetDefaults = () => {
  shortcuts.value = defaults()
  save()
}

watch(() => [props.username, props.role], load, { immediate: true })
</script>

<template>
  <section class="shortcut-section" aria-labelledby="shortcut-heading">
    <div class="shortcut-heading">
      <h2 v-if="!collapsed" id="shortcut-heading">常用功能</h2>
      <button type="button" class="shortcut-settings" title="编辑常用功能" aria-label="编辑常用功能" @click="isEditing = true">
        <Settings2 :size="16" />
      </button>
    </div>
    <div class="shortcut-list">
      <button
        v-for="shortcut in shortcuts"
        :key="shortcut.id"
        type="button"
        class="shortcut-item"
        :class="{ active: currentTab === shortcut.id }"
        :title="collapsed ? shortcut.label : undefined"
        :aria-label="collapsed ? shortcut.label : undefined"
        @click="emit('navigate', shortcut.id)"
      >
        <component :is="moduleById(shortcut.id)?.icon" :size="17" />
        <span v-if="!collapsed">{{ shortcut.label }}</span>
      </button>
      <p v-if="!shortcuts.length && !collapsed" class="shortcut-empty">暂未添加</p>
    </div>
  </section>

  <AppDrawer
    :open="isEditing"
    title="编辑常用功能"
    description="仅显示当前账号有权访问的模块"
    width="560px"
    @close="isEditing = false"
  >
    <div class="shortcut-editor">
      <section>
        <div class="section-title-row"><h3>已添加</h3><span>{{ shortcuts.length }} 项</span></div>
        <div v-if="shortcuts.length" class="shortcut-editor-list">
          <div v-for="(shortcut, index) in shortcuts" :key="shortcut.id" class="shortcut-editor-row">
            <span class="module-tile"><component :is="moduleById(shortcut.id)?.icon" :size="17" /></span>
            <label>
              <span class="sr-only">修改{{ moduleById(shortcut.id)?.label }}名称</span>
              <input :value="shortcut.label" aria-label="重命名常用功能" maxlength="12" @change="updateLabel(shortcut, $event.target.value)" />
            </label>
            <button type="button" class="icon-button" :disabled="index === 0" aria-label="上移" title="上移" @click="moveShortcut(index, -1)"><ChevronUp :size="17" /></button>
            <button type="button" class="icon-button" :disabled="index === shortcuts.length - 1" aria-label="下移" title="下移" @click="moveShortcut(index, 1)"><ChevronDown :size="17" /></button>
            <button type="button" class="icon-button danger-ghost" aria-label="删除" title="删除" @click="removeShortcut(index)"><Trash2 :size="17" /></button>
          </div>
        </div>
        <div v-else class="empty-state compact">暂未添加常用功能</div>
      </section>

      <section>
        <div class="section-title-row"><h3>可添加功能</h3><span>{{ availableModules.length }} 项</span></div>
        <div v-if="availableModules.length" class="available-modules">
          <button v-for="item in availableModules" :key="item.id" type="button" @click="addShortcut(item)">
            <span class="module-tile"><component :is="item.icon" :size="17" /></span>
            <span><strong>{{ item.label }}</strong><small>{{ item.description }}</small></span>
            <Plus :size="17" />
          </button>
        </div>
        <div v-else class="empty-state compact">全部可用功能均已添加</div>
      </section>
    </div>

    <template #footer>
      <button type="button" class="button secondary" @click="resetDefaults"><RotateCcw :size="17" />恢复默认</button>
      <button type="button" class="button primary" @click="isEditing = false"><Check :size="17" />完成</button>
    </template>
  </AppDrawer>
</template>
