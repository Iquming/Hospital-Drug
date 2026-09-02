<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { AlertTriangle, X } from 'lucide-vue-next'

const props = defineProps({
  open: { type: Boolean, default: false },
  title: { type: String, default: '确认操作' },
  message: { type: String, default: '' },
  confirmLabel: { type: String, default: '确认' },
  tone: { type: String, default: 'danger' }
})

const emit = defineEmits(['confirm', 'cancel'])
const panel = ref(null)
const cancelButton = ref(null)
let previousFocus = null

const focusableSelector = [
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[href]',
  '[tabindex]:not([tabindex="-1"])'
].join(',')

const releaseBodyLock = () => {
  const hasOtherModal = [...document.querySelectorAll('[aria-modal="true"]')]
    .some(modal => modal !== panel.value)
  if (!hasOtherModal) document.body.classList.remove('overlay-open')
}

const onKeydown = event => {
  if (!props.open || !panel.value) return
  const modalStack = [...document.querySelectorAll('[aria-modal="true"]')]
  if (modalStack.at(-1) !== panel.value) return
  if (event.key === 'Escape') {
    event.preventDefault()
    emit('cancel')
    return
  }
  if (event.key !== 'Tab') return
  const focusable = [...panel.value.querySelectorAll(focusableSelector)]
  if (!focusable.length) return
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

watch(() => props.open, async open => {
  if (open) {
    previousFocus = document.activeElement
    document.body.classList.add('overlay-open')
    document.addEventListener('keydown', onKeydown)
    await nextTick()
    cancelButton.value?.focus()
  } else {
    document.removeEventListener('keydown', onKeydown)
    previousFocus?.focus?.()
    await nextTick()
    releaseBodyLock()
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  releaseBodyLock()
})
</script>

<template>
  <Teleport to="body">
    <Transition name="dialog">
      <div v-if="open" class="dialog-backdrop" @mousedown.self="$emit('cancel')">
        <section ref="panel" class="confirm-dialog" role="alertdialog" aria-modal="true" aria-labelledby="confirm-title" aria-describedby="confirm-message">
          <header>
            <span class="dialog-icon" :data-tone="tone"><AlertTriangle :size="20" /></span>
            <div>
              <h2 id="confirm-title">{{ title }}</h2>
              <p id="confirm-message">{{ message }}</p>
            </div>
            <button class="icon-button" type="button" aria-label="关闭" title="关闭" @click="$emit('cancel')"><X :size="18" /></button>
          </header>
          <footer>
            <button ref="cancelButton" type="button" class="button secondary" @click="$emit('cancel')">取消</button>
            <button type="button" class="button" :class="tone === 'danger' ? 'danger' : 'primary'" @click="$emit('confirm')">{{ confirmLabel }}</button>
          </footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>
