<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { X } from 'lucide-vue-next'

const props = defineProps({
  open: { type: Boolean, default: false },
  title: { type: String, required: true },
  description: { type: String, default: '' },
  width: { type: String, default: '520px' }
})

const emit = defineEmits(['close'])
const panel = ref(null)
const titleId = `drawer-title-${Math.random().toString(36).slice(2)}`
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
  if (!props.open) return
  const modalStack = [...document.querySelectorAll('[aria-modal="true"]')]
  if (modalStack.at(-1) !== panel.value) return
  if (event.key === 'Escape') {
    event.preventDefault()
    emit('close')
    return
  }
  if (event.key !== 'Tab' || !panel.value) return
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
    const preferred = panel.value?.querySelector('[autofocus]')
      || panel.value?.querySelector(focusableSelector)
    preferred?.focus()
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
    <Transition name="drawer">
      <div v-if="open" class="drawer-backdrop" @mousedown.self="$emit('close')">
        <section
          ref="panel"
          class="app-drawer"
          :style="{ '--drawer-width': width }"
          role="dialog"
          aria-modal="true"
          :aria-labelledby="titleId"
        >
          <header class="drawer-header">
            <div>
              <h2 :id="titleId">{{ title }}</h2>
              <p v-if="description">{{ description }}</p>
            </div>
            <button type="button" class="icon-button" aria-label="关闭" title="关闭" @click="$emit('close')">
              <X :size="19" />
            </button>
          </header>
          <div class="drawer-body">
            <slot />
          </div>
          <footer v-if="$slots.footer" class="drawer-footer">
            <slot name="footer" />
          </footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>
