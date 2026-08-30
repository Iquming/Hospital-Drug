<script setup>
import { computed, ref, watch } from 'vue'
import {
  BookOpen,
  ChartNoAxesCombined,
  Check,
  ChevronDown,
  ChevronUp,
  ClipboardCheck,
  ClipboardList,
  LayoutDashboard,
  PackageCheck,
  Plus,
  RadioTower,
  RotateCcw,
  Settings2,
  Trash2,
  Users,
  X
} from 'lucide-vue-next'

const props = defineProps({
  currentTab: { type: String, default: 'dashboard' },
  role: { type: String, default: '' },
  username: { type: String, default: '' }
})

const emit = defineEmits(['navigate'])

const modules = [
  { id: 'dashboard', label: '院内总览', icon: LayoutDashboard, roles: ['ADMIN', 'PHARMACIST', 'NURSE'] },
  { id: 'hisApplications', label: '处方调剂', icon: ClipboardList, roles: ['ADMIN', 'PHARMACIST', 'NURSE'] },
  { id: 'pharmacy', label: '药库质控', icon: PackageCheck, roles: ['ADMIN', 'PHARMACIST'] },
  { id: 'catalog', label: '药品档案', icon: BookOpen, roles: ['ADMIN', 'PHARMACIST'] },
  { id: 'inventory', label: '库存盘点', icon: ClipboardCheck, roles: ['ADMIN', 'PHARMACIST'] },
  { id: 'hisIntegration', label: 'HIS 联调', icon: RadioTower, roles: ['ADMIN'] },
  { id: 'audit', label: '审计报表', icon: ChartNoAxesCombined, roles: ['ADMIN'] },
  { id: 'users', label: '用户管理', icon: Users, roles: ['ADMIN'] }
]

const defaultIds = {
  ADMIN: ['dashboard', 'hisApplications', 'pharmacy', 'inventory', 'hisIntegration'],
  PHARMACIST: ['dashboard', 'hisApplications', 'pharmacy', 'catalog', 'inventory'],
  NURSE: ['dashboard', 'hisApplications']
}

const isEditing = ref(false)
const shortcuts = ref([])

const allowedModules = computed(() => modules.filter(item => item.roles.includes(props.role)))
const availableModules = computed(() => allowedModules.value.filter(
  item => !shortcuts.value.some(shortcut => shortcut.id === item.id)
))
const storageKey = computed(() => `hospitalDrugShortcuts:${props.username || props.role || 'guest'}`)

const moduleById = id => modules.find(item => item.id === id)
const normalizedLabel = (label, fallback) => String(label || '').trim().slice(0, 12) || fallback

const getDefaults = () => (defaultIds[props.role] || ['dashboard'])
  .map(id => moduleById(id))
  .filter(item => item?.roles.includes(props.role))
  .map(item => ({ id: item.id, label: item.label }))

const normalize = value => {
  if (!Array.isArray(value)) return []
  const seen = new Set()
  return value.flatMap(item => {
    const module = moduleById(item?.id)
    if (!module || !module.roles.includes(props.role) || seen.has(module.id)) return []
    seen.add(module.id)
    return [{ id: module.id, label: normalizedLabel(item.label, module.label) }]
  })
}

const load = () => {
  try {
    const saved = normalize(JSON.parse(localStorage.getItem(storageKey.value) || '[]'))
    shortcuts.value = saved.length ? saved : getDefaults()
  } catch {
    shortcuts.value = getDefaults()
  }
}

const save = () => {
  localStorage.setItem(storageKey.value, JSON.stringify(shortcuts.value))
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
  shortcuts.value = getDefaults()
  save()
}

watch(() => [props.username, props.role], load, { immediate: true })
</script>

<template>
  <div class="shortcut-area" aria-label="常用功能">
    <div class="shortcut-list">
      <button
        v-for="shortcut in shortcuts"
        :key="shortcut.id"
        class="shortcut-button"
        :class="{ active: currentTab === shortcut.id }"
        :title="shortcut.label"
        :aria-label="shortcut.label"
        @click="emit('navigate', shortcut.id)"
      >
        <component :is="moduleById(shortcut.id)?.icon" :size="19" />
      </button>
    </div>

    <button
      class="shortcut-button settings-button"
      title="编辑常用功能"
      aria-label="编辑常用功能"
      @click="isEditing = true"
    >
      <Settings2 :size="19" />
    </button>
  </div>

  <Teleport to="body">
    <div v-if="isEditing" class="shortcut-modal-backdrop" @click.self="isEditing = false">
      <section class="shortcut-modal" role="dialog" aria-modal="true" aria-labelledby="shortcut-title">
        <header class="shortcut-modal-header">
          <div>
            <span>个性化工作台</span>
            <h2 id="shortcut-title">编辑常用功能</h2>
          </div>
          <button class="icon-button" title="关闭" aria-label="关闭" @click="isEditing = false"><X :size="20" /></button>
        </header>

        <div class="shortcut-modal-body">
          <section class="editor-section">
            <div class="section-heading">
              <h3>已添加</h3>
              <span>{{ shortcuts.length }} 项</span>
            </div>
            <div v-if="shortcuts.length" class="selected-list">
              <div v-for="(shortcut, index) in shortcuts" :key="shortcut.id" class="selected-row">
                <span class="module-icon"><component :is="moduleById(shortcut.id)?.icon" :size="18" /></span>
                <input
                  :value="shortcut.label"
                  :aria-label="`修改${moduleById(shortcut.id)?.label}名称`"
                  maxlength="12"
                  @input="updateLabel(shortcut, $event.target.value)"
                />
                <button class="icon-button" title="上移" :disabled="index === 0" @click="moveShortcut(index, -1)"><ChevronUp :size="18" /></button>
                <button class="icon-button" title="下移" :disabled="index === shortcuts.length - 1" @click="moveShortcut(index, 1)"><ChevronDown :size="18" /></button>
                <button class="icon-button danger" title="删除" @click="removeShortcut(index)"><Trash2 :size="18" /></button>
              </div>
            </div>
            <div v-else class="empty-state">暂未添加常用功能，可从下方选择。</div>
          </section>

          <section class="editor-section available-section">
            <div class="section-heading">
              <h3>可添加功能</h3>
              <span>仅显示当前账号可用模块</span>
            </div>
            <div v-if="availableModules.length" class="available-grid">
              <button v-for="item in availableModules" :key="item.id" @click="addShortcut(item)">
                <span class="module-icon"><component :is="item.icon" :size="18" /></span>
                <span>{{ item.label }}</span>
                <Plus :size="18" />
              </button>
            </div>
            <div v-else class="empty-state">当前可用功能已全部添加。</div>
          </section>
        </div>

        <footer class="shortcut-modal-footer">
          <button class="secondary-button" @click="resetDefaults"><RotateCcw :size="17" />恢复默认</button>
          <button class="primary-button" @click="isEditing = false"><Check :size="17" />完成</button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.shortcut-area {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  min-height: 0;
  margin-top: 30px;
}

.shortcut-list {
  display: grid;
  gap: 8px;
  max-height: calc(100vh - 360px);
  overflow-y: auto;
  padding: 2px 6px;
  scrollbar-width: none;
}

.shortcut-list::-webkit-scrollbar { display: none; }

.shortcut-button {
  display: grid;
  width: 40px;
  height: 40px;
  padding: 0;
  place-items: center;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: rgba(255, 255, 255, 0.84);
  cursor: pointer;
  transition: color 180ms ease, background-color 180ms ease, border-color 180ms ease;
}

.shortcut-button:hover,
.shortcut-button.active,
.settings-button[aria-expanded="true"] {
  border-color: rgba(255, 255, 255, 0.24);
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
}

.settings-button {
  position: relative;
  margin-top: 3px;
}

.settings-button::before {
  position: absolute;
  top: -6px;
  width: 24px;
  height: 1px;
  background: rgba(255, 255, 255, 0.24);
  content: '';
}

.shortcut-modal-backdrop {
  position: fixed;
  z-index: 1000;
  inset: 0;
  display: grid;
  padding: 24px;
  place-items: center;
  background: rgba(20, 38, 55, 0.48);
}

.shortcut-modal {
  overflow: hidden;
  width: min(680px, 100%);
  max-height: min(760px, calc(100vh - 48px));
  border: 1px solid #d8e3ec;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 24px 64px rgba(15, 49, 78, 0.24);
}

.shortcut-modal-header,
.shortcut-modal-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 22px;
}

.shortcut-modal-header {
  border-bottom: 1px solid #e2eaf1;
}

.shortcut-modal-header span {
  color: #577086;
  font-size: 12px;
  font-weight: 700;
}

.shortcut-modal-header h2 {
  margin: 4px 0 0;
  color: #172b3d;
  font-size: 20px;
  letter-spacing: 0;
}

.shortcut-modal-body {
  max-height: calc(100vh - 220px);
  overflow-y: auto;
  padding: 20px 22px;
}

.editor-section + .editor-section {
  margin-top: 24px;
  padding-top: 22px;
  border-top: 1px solid #e2eaf1;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section-heading h3 {
  margin: 0;
  color: #233b50;
  font-size: 15px;
  letter-spacing: 0;
}

.section-heading span {
  color: #71879a;
  font-size: 12px;
}

.selected-list { display: grid; gap: 8px; }

.selected-row {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) repeat(3, 34px);
  gap: 6px;
  align-items: center;
  min-height: 48px;
  padding: 5px 7px;
  border: 1px solid #dfe8ef;
  border-radius: 6px;
  background: #f8fafc;
}

.module-icon {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 6px;
  background: #e7f3fc;
  color: #087fda;
}

.selected-row input {
  min-width: 0;
  height: 34px;
  padding: 0 10px;
  border: 1px solid transparent;
  border-radius: 5px;
  background: transparent;
  color: #233b50;
  font-size: 14px;
  font-weight: 650;
}

.selected-row input:hover,
.selected-row input:focus {
  border-color: #b8d6eb;
  background: #fff;
  outline: none;
}

.icon-button {
  display: grid;
  width: 34px;
  height: 34px;
  padding: 0;
  place-items: center;
  border: 1px solid #d7e2eb;
  border-radius: 6px;
  background: #fff;
  color: #466176;
  cursor: pointer;
}

.icon-button:hover:not(:disabled) { border-color: #8bbfe2; color: #087fda; }
.icon-button:disabled { cursor: not-allowed; opacity: 0.38; }
.icon-button.danger:hover { border-color: #efb4b4; background: #fff6f6; color: #c53e3e; }

.available-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.available-grid button {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) 24px;
  gap: 9px;
  align-items: center;
  min-height: 48px;
  padding: 6px 9px;
  border: 1px solid #dfe8ef;
  border-radius: 6px;
  background: #fff;
  color: #233b50;
  text-align: left;
  cursor: pointer;
}

.available-grid button:hover { border-color: #86bfe5; background: #f5fbff; }
.available-grid button > svg { color: #087fda; }

.empty-state {
  padding: 22px;
  border: 1px dashed #c9d7e2;
  border-radius: 6px;
  color: #71879a;
  text-align: center;
}

.shortcut-modal-footer {
  border-top: 1px solid #e2eaf1;
  background: #f8fafc;
}

.secondary-button,
.primary-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 38px;
  padding: 0 15px;
  border-radius: 6px;
  font-weight: 700;
  cursor: pointer;
}

.secondary-button { border: 1px solid #cedbe5; background: #fff; color: #405b70; }
.primary-button { border: 1px solid #087fda; background: #087fda; color: #fff; }

@media (max-width: 760px) {
  .shortcut-area {
    flex-direction: row;
    gap: 4px;
    width: auto;
    margin: 0;
  }

  .shortcut-list {
    display: flex;
    gap: 4px;
    max-width: min(42vw, 220px);
    max-height: none;
    overflow-x: auto;
    padding: 1px;
  }

  .shortcut-button { width: 34px; height: 34px; flex: 0 0 34px; }
  .settings-button { margin: 0; }
  .settings-button::before { display: none; }
}

@media (max-width: 560px) {
  .shortcut-modal-backdrop { align-items: end; padding: 0; }
  .shortcut-modal { width: 100%; max-height: 92vh; border-radius: 8px 8px 0 0; }
  .shortcut-modal-body { max-height: calc(92vh - 150px); padding: 16px; }
  .shortcut-modal-header, .shortcut-modal-footer { padding: 15px 16px; }
  .available-grid { grid-template-columns: 1fr; }
  .selected-row { grid-template-columns: 34px minmax(0, 1fr) repeat(3, 32px); gap: 4px; }
  .selected-row .icon-button { width: 32px; height: 32px; }
}
</style>
