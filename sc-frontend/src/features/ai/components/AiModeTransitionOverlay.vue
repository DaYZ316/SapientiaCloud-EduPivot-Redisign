<template>
  <Teleport to="body">
    <div
      v-if="visible"
      ref="overlayRef"
      :class="phaseClass"
      :style="overlayStyle"
      aria-hidden="true"
      class="ai-mode-transition-overlay"
    />
  </Teleport>
</template>

<script lang="ts" setup>
import {computed, nextTick, onBeforeUnmount, ref} from 'vue'

const TRANSITION_MS = 620
const REDUCED_MOTION_MS = 1
const SAFETY_DELAY_MS = 80

const visible = ref(false)
const phase = ref<'idle' | 'outward' | 'inward'>('idle')
const radius = ref(0)
const duration = ref(TRANSITION_MS)
const overlayRef = ref<HTMLElement | null>(null)

let fallbackTimer: number | undefined

const phaseClass = computed(() => ({
  'is-inward': phase.value === 'inward',
  'is-outward': phase.value === 'outward',
}))

const overlayStyle = computed(() => ({
  '--ai-mask-diameter': `${radius.value * 2}px`,
  '--ai-mask-radius': `${radius.value}px`,
  '--ai-mask-duration': `${duration.value}ms`,
}))

onBeforeUnmount(() => {
  hide()
})

async function playOutward() {
  prepareTransition('outward')
  await waitForAnimation()
}

async function playInward() {
  prepareTransition('inward')
  await waitForAnimation()
}

function hide() {
  clearFallbackTimer()
  visible.value = false
  phase.value = 'idle'
}

function prepareTransition(nextPhase: 'outward' | 'inward') {
  clearFallbackTimer()
  radius.value = getCoverRadius()
  duration.value = getMotionDuration()
  phase.value = nextPhase
  visible.value = true
}

async function waitForAnimation() {
  await nextTick()

  const element = overlayRef.value
  if (!element) return

  await new Promise<void>((resolve) => {
    let resolved = false
    const finish = () => {
      if (resolved) return
      resolved = true
      clearFallbackTimer()
      element.removeEventListener('animationend', finish)
      resolve()
    }

    element.addEventListener('animationend', finish)
    fallbackTimer = window.setTimeout(finish, duration.value + SAFETY_DELAY_MS)
  })
}

function getCoverRadius() {
  return Math.ceil(Math.hypot(window.innerWidth / 2, window.innerHeight / 2)) + 24
}

function getMotionDuration() {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
    ? REDUCED_MOTION_MS
    : TRANSITION_MS
}

function clearFallbackTimer() {
  if (fallbackTimer === undefined) return

  window.clearTimeout(fallbackTimer)
  fallbackTimer = undefined
}

defineExpose({
  hide,
  playInward,
  playOutward,
})
</script>

<style scoped>
.ai-mode-transition-overlay {
  position: fixed;
  inset: 0;
  z-index: 2550;
  overflow: hidden;
  pointer-events: auto;
}

.ai-mode-transition-overlay.is-outward {
  background:
    radial-gradient(
      circle at center,
      color-mix(in srgb, var(--color-primary) 14%, var(--color-surface-card)) 0,
      var(--color-surface-card) 42%
    );
  clip-path: circle(0 at center);
  will-change: clip-path;
}

.ai-mode-transition-overlay.is-outward {
  animation: aiModeMaskExpand var(--ai-mask-duration) cubic-bezier(0.76, 0, 0.24, 1) both;
}

.ai-mode-transition-overlay.is-inward::before {
  position: absolute;
  top: 50%;
  left: 50%;
  width: var(--ai-mask-diameter);
  height: var(--ai-mask-diameter);
  border-radius: 50%;
  box-shadow: 0 0 0 var(--ai-mask-radius) color-mix(in srgb, var(--color-primary) 8%, var(--color-surface-card));
  content: '';
  transform: translate(-50%, -50%);
  will-change: width, height;
  animation: aiModeMaskContract var(--ai-mask-duration) cubic-bezier(0.76, 0, 0.24, 1) both;
}

@keyframes aiModeMaskExpand {
  from {
    clip-path: circle(0 at center);
  }

  to {
    clip-path: circle(var(--ai-mask-radius) at center);
  }
}

@keyframes aiModeMaskContract {
  from {
    width: var(--ai-mask-diameter);
    height: var(--ai-mask-diameter);
  }

  to {
    width: 0;
    height: 0;
  }
}
</style>
