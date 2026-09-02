<script setup>
import { CircleAlert, CircleCheck, Info, X } from 'lucide-vue-next'

defineProps({
  toast: {
    type: Object,
    default: () => ({ show: false, message: '', type: 'info' })
  }
})

defineEmits(['dismiss'])

const icons = { success: CircleCheck, error: CircleAlert, info: Info }
</script>

<template>
  <div class="toast-region" aria-live="polite" aria-atomic="true">
    <Transition name="toast">
      <div v-if="toast.show" class="app-toast" :data-type="toast.type" :role="toast.type === 'error' ? 'alert' : 'status'">
        <component :is="icons[toast.type] || Info" :size="18" />
        <span>{{ toast.message }}</span>
        <button type="button" aria-label="关闭通知" title="关闭通知" @click="$emit('dismiss')"><X :size="16" /></button>
      </div>
    </Transition>
  </div>
</template>
