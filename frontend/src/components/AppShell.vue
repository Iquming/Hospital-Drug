<script setup>
import { computed } from 'vue'
import {
  Activity,
  Clock3,
  LogOut,
  PanelLeftClose,
  PanelLeftOpen,
  Pill
} from 'lucide-vue-next'
import { groupsForRole, moduleById, roleLabels } from '../config/navigation'
import SidebarShortcuts from './SidebarShortcuts.vue'

const props = defineProps({
  user: { type: Object, required: true },
  currentTab: { type: String, required: true },
  collapsed: { type: Boolean, default: false },
  apiOnline: { type: Boolean, default: false },
  lastSyncedAt: { type: String, default: '--' }
})

const emit = defineEmits(['navigate', 'logout', 'toggle-sidebar'])
const groups = computed(() => groupsForRole(props.user.role))
const currentModule = computed(() => moduleById(props.currentTab) || moduleById('dashboard'))
const displayName = computed(() => props.user.displayName || props.user.username || '未登录')
const initial = computed(() => displayName.value.slice(0, 1).toUpperCase())
</script>

<template>
  <div class="clinical-shell" :class="{ 'sidebar-collapsed': collapsed }">
    <a class="skip-link" href="#main-content">跳到主要内容</a>

    <aside class="clinical-sidebar">
      <div class="sidebar-brand">
        <span class="brand-icon"><Pill :size="20" /></span>
        <span v-if="!collapsed" class="brand-text">
          <strong>Hospital Drug</strong>
          <small>药品闭环管理</small>
        </span>
      </div>

      <nav class="primary-navigation" aria-label="主功能导航">
        <section v-for="group in groups" :key="group.id" class="nav-group">
          <h2 v-if="!collapsed">{{ group.label }}</h2>
          <div class="nav-items">
            <button
              v-for="item in group.modules"
              :key="item.id"
              type="button"
              class="nav-item"
              :class="{ active: currentTab === item.id }"
              :title="collapsed ? item.label : undefined"
              :aria-label="collapsed ? item.label : undefined"
              :aria-current="currentTab === item.id ? 'page' : undefined"
              @click="emit('navigate', item.id)"
            >
              <component :is="item.icon" :size="18" />
              <span v-if="!collapsed">
                <strong>{{ item.label }}</strong>
                <small>{{ item.description }}</small>
              </span>
            </button>
          </div>
        </section>
      </nav>

      <SidebarShortcuts
        :current-tab="currentTab"
        :role="user.role"
        :username="user.username"
        :collapsed="collapsed"
        @navigate="emit('navigate', $event)"
      />

      <div class="sidebar-footer">
        <button
          type="button"
          class="nav-item sidebar-toggle"
          :aria-label="collapsed ? '展开侧栏' : '收起侧栏'"
          :title="collapsed ? '展开侧栏' : '收起侧栏'"
          @click="emit('toggle-sidebar')"
        >
          <PanelLeftOpen v-if="collapsed" :size="18" />
          <PanelLeftClose v-else :size="18" />
          <span v-if="!collapsed"><strong>收起侧栏</strong></span>
        </button>
      </div>
    </aside>

    <div class="clinical-workspace">
      <header class="topbar">
        <div class="page-identity">
          <span>医院药品闭环管理系统</span>
          <div>
            <h1>{{ currentModule.label }}</h1>
            <p>{{ currentModule.description }}</p>
          </div>
        </div>

        <div class="topbar-actions">
          <span class="system-status" :data-online="apiOnline">
            <Activity :size="16" />{{ apiOnline ? '服务正常' : '服务未连接' }}
          </span>
          <span class="sync-time" title="最近同步时间"><Clock3 :size="15" />{{ lastSyncedAt }}</span>
          <span class="topbar-divider"></span>
          <div class="user-summary">
            <span class="user-avatar">{{ initial }}</span>
            <span>
              <strong>{{ displayName }}</strong>
              <small>{{ roleLabels[user.role] || user.role }} · {{ user.department || '未设置科室' }}</small>
            </span>
          </div>
          <button type="button" class="icon-button" title="退出登录" aria-label="退出登录" @click="emit('logout')">
            <LogOut :size="18" />
          </button>
        </div>
      </header>

      <main id="main-content" class="page-content" tabindex="-1">
        <slot />
      </main>
    </div>
  </div>
</template>
