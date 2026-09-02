<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { CirclePlus, KeyRound, Pencil, RefreshCw, Search, ShieldBan, Trash2, Users } from 'lucide-vue-next'
import { errorMessage } from '../api/client'
import { roleLabels } from '../config/navigation'
import AppDrawer from '../components/ui/AppDrawer.vue'

const props = defineProps({
  api: { type: Object, required: true },
  notify: { type: Function, required: true },
  confirmAction: { type: Function, required: true },
  user: { type: Object, required: true }
})

const emit = defineEmits(['synced'])
const loading = ref(true)
const saving = ref(false)
const users = ref([])
const search = ref('')
const statusFilter = ref('')
const drawerMode = ref('')
const drawerSnapshot = ref('')
const passwordUser = ref(null)
const password = ref('')

const defaultForm = () => ({ id: null, username: '', password: '', displayName: '', role: 'NURSE', department: '', status: 'ENABLED' })
const form = reactive(defaultForm())

const filteredUsers = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  return users.value.filter(item => {
    const matchesStatus = !statusFilter.value || item.status === statusFilter.value
    const matchesKeyword = !keyword || [item.username, item.displayName, item.department, item.role]
      .some(value => String(value || '').toLowerCase().includes(keyword))
    return matchesStatus && matchesKeyword
  })
})
const drawerDirty = computed(() => {
  if (!drawerMode.value) return false
  if (drawerMode.value === 'password') return Boolean(password.value)
  return JSON.stringify(form) !== drawerSnapshot.value
})
const drawerTitle = computed(() => drawerMode.value === 'password' ? '重置用户密码' : form.id ? '编辑用户' : '新增用户')
const enabledCount = computed(() => users.value.filter(item => item.status === 'ENABLED').length)

const load = async () => {
  loading.value = true
  try {
    const response = await props.api.get('/users')
    users.value = response.data || []
    emit('synced', new Date())
  } catch (error) {
    props.notify(errorMessage(error, '用户列表加载失败'), 'error')
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  Object.assign(form, defaultForm())
  drawerSnapshot.value = JSON.stringify(form)
  drawerMode.value = 'user'
}

const openEdit = item => {
  Object.assign(form, defaultForm(), item, { password: '' })
  drawerSnapshot.value = JSON.stringify(form)
  drawerMode.value = 'user'
}

const openPassword = item => {
  passwordUser.value = item
  password.value = ''
  drawerMode.value = 'password'
}

const closeDrawer = async () => {
  if (drawerDirty.value) {
    const confirmed = await props.confirmAction({
      title: '放弃用户修改',
      message: '当前表单有未保存内容，确认关闭吗？',
      confirmLabel: '放弃修改',
      tone: 'warning'
    })
    if (!confirmed) return
  }
  drawerMode.value = ''
  passwordUser.value = null
  password.value = ''
}

const saveUser = async () => {
  if (!form.username.trim() || !form.displayName.trim()) return props.notify('请填写用户名和姓名', 'error')
  if (!form.id && form.password.length < 6) return props.notify('初始密码至少 6 位', 'error')
  saving.value = true
  try {
    if (form.id) {
      await props.api.put(`/users/${form.id}`, form)
      props.notify('用户信息已更新', 'success')
    } else {
      await props.api.post('/users', form)
      props.notify('用户已创建', 'success')
    }
    drawerMode.value = ''
    await load()
  } catch (error) {
    props.notify(errorMessage(error, '用户保存失败'), 'error')
  } finally {
    saving.value = false
  }
}

const savePassword = async () => {
  if (!passwordUser.value) return
  if (password.value.length < 6) return props.notify('新密码至少 6 位', 'error')
  saving.value = true
  try {
    await props.api.put(`/users/${passwordUser.value.id}/password`, { password: password.value })
    props.notify('密码已重置', 'success')
    drawerMode.value = ''
    passwordUser.value = null
    password.value = ''
  } catch (error) {
    props.notify(errorMessage(error, '密码重置失败'), 'error')
  } finally {
    saving.value = false
  }
}

const disable = async item => {
  if (item.id === props.user.id) return props.notify('不能禁用当前登录用户', 'error')
  const confirmed = await props.confirmAction({
    title: '禁用用户',
    message: `确认禁用“${item.displayName || item.username}”吗？该账号将无法继续登录。`,
    confirmLabel: '确认禁用',
    tone: 'danger'
  })
  if (!confirmed) return
  try {
    await props.api.delete(`/users/${item.id}`)
    props.notify('用户已禁用', 'success')
    await load()
  } catch (error) {
    props.notify(errorMessage(error, '用户禁用失败'), 'error')
  }
}

const remove = async item => {
  if (item.id === props.user.id) return props.notify('不能删除当前登录用户', 'error')
  const confirmed = await props.confirmAction({
    title: '永久删除用户',
    message: `确认永久删除“${item.displayName || item.username}”吗？此操作不可恢复。`,
    confirmLabel: '永久删除',
    tone: 'danger'
  })
  if (!confirmed) return
  try {
    await props.api.delete(`/users/${item.id}/hard`)
    props.notify('用户已删除', 'success')
    await load()
  } catch (error) {
    props.notify(errorMessage(error, '用户删除失败'), 'error')
  }
}

onMounted(load)
</script>

<template>
  <section class="page-stack" aria-labelledby="users-heading">
    <header class="page-header">
      <div><h2 id="users-heading">用户与权限</h2><p>维护管理员、药师和护士账号</p></div>
      <div class="page-actions">
        <button type="button" class="button secondary" :disabled="loading" @click="load"><RefreshCw :size="17" />刷新</button>
        <button type="button" class="button primary" @click="openCreate"><CirclePlus :size="17" />新增用户</button>
      </div>
    </header>

    <div class="metric-grid user-metrics">
      <div class="metric-card"><span>用户总数</span><strong>{{ users.length }}</strong><small>全部院内账号</small><span class="metric-icon"><Users :size="19" /></span></div>
      <div class="metric-card"><span>启用账号</span><strong>{{ enabledCount }}</strong><small>当前允许登录</small><span class="metric-icon success"><Users :size="19" /></span></div>
      <div class="metric-card"><span>停用账号</span><strong>{{ users.length - enabledCount }}</strong><small>已停止访问</small><span class="metric-icon warning"><ShieldBan :size="19" /></span></div>
    </div>

    <section class="data-panel">
      <div class="toolbar">
        <div class="toolbar-group">
          <label class="search-control"><Search :size="16" /><span class="sr-only">搜索用户</span><input v-model="search" aria-label="搜索用户" placeholder="搜索用户名、姓名、科室或角色" /></label>
          <label><span class="sr-only">用户状态</span><select v-model="statusFilter" aria-label="用户状态"><option value="">全部状态</option><option value="ENABLED">启用</option><option value="DISABLED">停用</option></select></label>
          <span class="status-badge" data-tone="neutral">{{ filteredUsers.length }} 人</span>
        </div>
      </div>

      <div v-if="loading" class="loading-state"><span class="spinner"></span><span>正在加载用户列表</span></div>
      <div v-else-if="filteredUsers.length" class="table-scroll users-table">
        <table>
          <caption>系统用户与角色列表</caption>
          <thead><tr><th>用户名</th><th>姓名</th><th>角色</th><th>科室</th><th>状态</th><th>最近登录</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="item in filteredUsers" :key="item.id">
              <td class="mono"><strong>{{ item.username }}</strong></td><td>{{ item.displayName || '--' }}</td><td><span class="status-badge" data-tone="info">{{ roleLabels[item.role] || item.role }}</span></td><td>{{ item.department || '--' }}</td><td><span class="status-badge" :data-tone="item.status === 'ENABLED' ? 'success' : 'neutral'">{{ item.status === 'ENABLED' ? '启用' : '停用' }}</span></td><td class="nowrap">{{ item.lastLoginTime || '--' }}</td>
              <td>
                <div class="row-actions">
                  <button type="button" class="text-button" @click="openEdit(item)"><Pencil :size="14" />编辑</button>
                  <button type="button" class="text-button" @click="openPassword(item)"><KeyRound :size="14" />重置密码</button>
                  <button v-if="item.status === 'ENABLED'" type="button" class="text-button danger" :disabled="item.id === user.id" @click="disable(item)"><ShieldBan :size="14" />禁用</button>
                  <button type="button" class="text-button danger" :disabled="item.id === user.id" @click="remove(item)"><Trash2 :size="14" />删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state"><Users :size="28" /><span>{{ search || statusFilter ? '没有匹配的用户' : '暂无用户' }}</span></div>
    </section>

    <AppDrawer :open="Boolean(drawerMode)" :title="drawerTitle" :description="drawerMode === 'password' ? `为 ${passwordUser?.displayName || passwordUser?.username || ''} 设置新密码` : '角色决定账号可访问的业务模块'" width="500px" @close="closeDrawer">
      <form v-if="drawerMode === 'user'" class="form-stack" @submit.prevent="saveUser">
        <div class="form-grid">
          <label class="field"><span>登录用户名</span><input v-model="form.username" aria-label="登录用户名" :disabled="Boolean(form.id)" required autofocus autocomplete="off" /></label>
          <label v-if="!form.id" class="field"><span>初始密码</span><input v-model="form.password" aria-label="初始密码" type="password" minlength="6" autocomplete="new-password" placeholder="至少 6 位" /></label>
          <label class="field"><span>姓名</span><input v-model="form.displayName" aria-label="用户姓名" required /></label>
          <label class="field"><span>所属科室</span><input v-model="form.department" aria-label="所属科室" placeholder="如：门诊药房" /></label>
          <label class="field"><span>角色</span><select v-model="form.role" aria-label="用户角色"><option value="ADMIN">系统管理员</option><option value="PHARMACIST">药师</option><option value="NURSE">护士</option></select></label>
          <label class="field"><span>账号状态</span><select v-model="form.status" aria-label="账号状态"><option value="ENABLED">启用</option><option value="DISABLED">禁用</option></select></label>
        </div>
      </form>
      <form v-else-if="drawerMode === 'password'" class="form-stack" @submit.prevent="savePassword">
        <label class="field"><span>新密码</span><input v-model="password" aria-label="新密码" type="password" minlength="6" autofocus autocomplete="new-password" placeholder="至少 6 位" /></label>
        <div class="notice warning"><KeyRound :size="17" /><span>重置后请通过安全渠道通知账号本人，不要在页面或文档中记录密码。</span></div>
      </form>
      <template #footer>
        <button type="button" class="button secondary" @click="closeDrawer">取消</button>
        <button v-if="drawerMode === 'user'" type="button" class="button primary" :disabled="saving" @click="saveUser">{{ saving ? '正在保存' : '保存用户' }}</button>
        <button v-else type="button" class="button primary" :disabled="saving" @click="savePassword">{{ saving ? '正在重置' : '确认重置' }}</button>
      </template>
    </AppDrawer>
  </section>
</template>

<style scoped>
.user-metrics { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.toolbar select { width: 126px; }
.users-table { max-height: calc(100vh - 330px); }
</style>
