<script setup>
import { computed, defineAsyncComponent, nextTick, onMounted, ref } from 'vue'
import api, {
  errorMessage,
  setAuthToken,
  setUnauthorizedHandler
} from './api/client'
import AppShell from './components/AppShell.vue'
import LoginView from './components/LoginView.vue'
import ConfirmDialog from './components/ui/ConfirmDialog.vue'
import ToastRegion from './components/ui/ToastRegion.vue'
import { landingModuleByRole, modulesForRole } from './config/navigation'

const views = {
  dashboard: defineAsyncComponent(() => import('./views/DashboardView.vue')),
  hisApplications: defineAsyncComponent(() => import('./components/HisApplicationWorkbench.vue')),
  pharmacy: defineAsyncComponent(() => import('./views/PharmacyView.vue')),
  catalog: defineAsyncComponent(() => import('./views/CatalogView.vue')),
  inventory: defineAsyncComponent(() => import('./views/InventoryView.vue')),
  hisIntegration: defineAsyncComponent(() => import('./components/HisIntegrationConsole.vue')),
  audit: defineAsyncComponent(() => import('./views/AuditView.vue')),
  users: defineAsyncComponent(() => import('./views/UserManagementView.vue'))
}

const authToken = ref(localStorage.getItem('hospitalDrugToken') || '')
const authUser = ref(null)
const currentTab = ref('dashboard')
const sessionLoading = ref(Boolean(authToken.value))
const loginLoading = ref(false)
const loginError = ref('')
const apiOnline = ref(false)
const lastSyncedAt = ref('--')
const sidebarCollapsed = ref(false)
const toast = ref({ show: false, message: '', type: 'info' })
const confirmation = ref({
  open: false,
  title: '确认操作',
  message: '',
  confirmLabel: '确认',
  tone: 'danger'
})

let toastTimer
let confirmationResolver

const isAuthenticated = computed(() => Boolean(authToken.value && authUser.value))
const currentView = computed(() => views[currentTab.value] || views.dashboard)
const currentViewProps = computed(() => {
  const shared = { api, notify, confirmAction }
  const propsByModule = {
    dashboard: { api, notify },
    hisApplications: { ...shared, userRole: authUser.value?.role || '' },
    pharmacy: { ...shared, user: authUser.value },
    catalog: shared,
    inventory: shared,
    hisIntegration: shared,
    audit: { api, notify },
    users: { ...shared, user: authUser.value }
  }
  return propsByModule[currentTab.value] || propsByModule.dashboard
})
const currentViewEvents = computed(() => ({
  synced: handleSynced,
  ...(currentTab.value === 'dashboard' ? { navigate } : {})
}))

const formatTime = date => new Intl.DateTimeFormat('zh-CN', {
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false
}).format(date)

function notify(message, type = 'info') {
  window.clearTimeout(toastTimer)
  toast.value = { show: true, message: String(message || ''), type }
  toastTimer = window.setTimeout(() => {
    toast.value = { ...toast.value, show: false }
  }, type === 'error' ? 4800 : 3200)
}

function dismissToast() {
  window.clearTimeout(toastTimer)
  toast.value = { ...toast.value, show: false }
}

function confirmAction(options = {}) {
  if (confirmationResolver) confirmationResolver(false)
  confirmation.value = {
    open: true,
    title: options.title || '确认操作',
    message: options.message || '确认继续执行此操作吗？',
    confirmLabel: options.confirmLabel || '确认',
    tone: options.tone || 'danger'
  }
  return new Promise(resolve => {
    confirmationResolver = resolve
  })
}

function settleConfirmation(result) {
  confirmation.value = { ...confirmation.value, open: false }
  const resolve = confirmationResolver
  confirmationResolver = null
  resolve?.(result)
}

function sidebarPreferenceKey(username) {
  return `hospitalDrugSidebarCollapsed:${username || 'anonymous'}`
}

function applyUserSession(user) {
  authUser.value = user
  currentTab.value = landingModuleByRole[user?.role] || 'dashboard'
  sidebarCollapsed.value = localStorage.getItem(sidebarPreferenceKey(user?.username)) === 'true'
}

function clearSession() {
  localStorage.removeItem('hospitalDrugToken')
  authToken.value = ''
  authUser.value = null
  currentTab.value = 'dashboard'
  apiOnline.value = false
  lastSyncedAt.value = '--'
  setAuthToken('')
}

function handleUnauthorized() {
  if (!authToken.value) return
  clearSession()
  loginError.value = '登录状态已失效，请重新登录。'
  notify('登录状态已失效，请重新登录', 'error')
}

async function login(credentials) {
  loginError.value = ''
  if (!credentials.username || !credentials.password) {
    loginError.value = '请输入用户名和密码。'
    return
  }

  loginLoading.value = true
  try {
    const response = await api.post('/auth/login', credentials)
    const token = response.data?.token
    const user = response.data?.user
    if (!token || !user) throw new Error('登录服务返回的数据不完整')

    authToken.value = token
    localStorage.setItem('hospitalDrugToken', token)
    setAuthToken(token)
    applyUserSession(user)
    apiOnline.value = true
    lastSyncedAt.value = formatTime(new Date())
    notify(`欢迎，${user.displayName || user.username}`, 'success')
  } catch (error) {
    clearSession()
    loginError.value = errorMessage(error, '登录失败，请稍后重试。')
  } finally {
    loginLoading.value = false
  }
}

async function logout() {
  try {
    await api.post('/auth/logout')
  } catch {
    // 本地清除令牌即可结束当前会话。
  }
  clearSession()
  loginError.value = ''
  notify('已安全退出登录', 'info')
}

async function restoreSession() {
  if (!authToken.value) {
    sessionLoading.value = false
    return
  }

  setAuthToken(authToken.value)
  try {
    const response = await api.get('/auth/me')
    applyUserSession(response.data?.user)
    apiOnline.value = true
    lastSyncedAt.value = formatTime(new Date())
  } catch (error) {
    if (authToken.value) {
      clearSession()
      loginError.value = errorMessage(error, '无法恢复登录状态，请重新登录。')
    }
  } finally {
    sessionLoading.value = false
  }
}

async function navigate(moduleId) {
  const allowed = modulesForRole(authUser.value?.role).some(module => module.id === moduleId)
  if (!allowed) {
    notify('当前账号无权访问该功能', 'error')
    return
  }
  currentTab.value = moduleId
  await nextTick()
  document.getElementById('main-content')?.focus({ preventScroll: true })
}

function handleSynced(date = new Date()) {
  const syncedAt = date instanceof Date ? date : new Date(date)
  apiOnline.value = true
  lastSyncedAt.value = formatTime(Number.isNaN(syncedAt.getTime()) ? new Date() : syncedAt)
}

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
  localStorage.setItem(
    sidebarPreferenceKey(authUser.value?.username),
    String(sidebarCollapsed.value)
  )
}

setAuthToken(authToken.value)
setUnauthorizedHandler(handleUnauthorized)
onMounted(restoreSession)
</script>

<template>
  <div class="app-root">
    <ToastRegion :toast="toast" @dismiss="dismissToast" />

    <main v-if="sessionLoading" class="loading-state app-bootstrap" aria-busy="true">
      <span class="spinner" aria-hidden="true"></span>
      <span>正在恢复工作台会话</span>
    </main>

    <LoginView
      v-else-if="!isAuthenticated"
      :loading="loginLoading"
      :error="loginError"
      @submit="login"
    />

    <AppShell
      v-else
      :user="authUser"
      :current-tab="currentTab"
      :collapsed="sidebarCollapsed"
      :api-online="apiOnline"
      :last-synced-at="lastSyncedAt"
      @navigate="navigate"
      @logout="logout"
      @toggle-sidebar="toggleSidebar"
    >
      <Suspense>
        <component
          :is="currentView"
          :key="currentTab"
          v-bind="currentViewProps"
          v-on="currentViewEvents"
        />
        <template #fallback>
          <div class="loading-state" aria-busy="true">
            <span class="spinner" aria-hidden="true"></span>
            <span>正在加载业务模块</span>
          </div>
        </template>
      </Suspense>
    </AppShell>

    <ConfirmDialog
      :open="confirmation.open"
      :title="confirmation.title"
      :message="confirmation.message"
      :confirm-label="confirmation.confirmLabel"
      :tone="confirmation.tone"
      @confirm="settleConfirmation(true)"
      @cancel="settleConfirmation(false)"
    />
  </div>
</template>
