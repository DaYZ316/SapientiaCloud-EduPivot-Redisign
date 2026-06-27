<template>
  <span
      ref="anchorRef"
      aria-label="AI is generating a response"
      class="pending-brush-loader"
      role="status"
  >
    <Teleport to="body">
      <AiBrushEffect
          v-if="brushCenter"
          :center-x="brushCenter.x"
          :center-y="brushCenter.y"
          :radius="radius"
          :size="size"
          :speed="speed"
          :z-index="zIndex"
          class="pending-brush-overlay"
      />
    </Teleport>
  </span>
</template>

<script lang="ts" setup>
import {nextTick, onBeforeUnmount, onMounted, ref} from 'vue'

import AiBrushEffect from '@/features/ai/components/AiBrushEffect.vue'

const props = withDefaults(defineProps<{
  anchorSize?: number
  centerOnAnchor?: boolean
  radius?: number
  size?: number
  speed?: number
  zIndex?: number
}>(), {
  anchorSize: 1,
  centerOnAnchor: false,
  radius: 30,
  size: 40,
  speed: 4.1,
  zIndex: 2600,
})

type BrushCenter = {
  x: number
  y: number
}

const anchorRef = ref<HTMLElement | null>(null)
const brushCenter = ref<BrushCenter | null>(null)
let resizeObserver: ResizeObserver | undefined
let scrollParent: HTMLElement | Window | undefined
let frame = 0
let trackingFrame = 0

onMounted(async () => {
  await nextTick()
  updateBrushCenter()
  window.addEventListener('resize', updateBrushCenter)
  scrollParent = findScrollParent(anchorRef.value)
  scrollParent?.addEventListener('scroll', updateBrushCenter, {passive: true})
  resizeObserver = new ResizeObserver(updateBrushCenter)
  if (anchorRef.value) {
    resizeObserver.observe(anchorRef.value)
  }
  if (props.centerOnAnchor) {
    startAnchorTracking()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateBrushCenter)
  scrollParent?.removeEventListener('scroll', updateBrushCenter)
  resizeObserver?.disconnect()
  if (frame) {
    cancelAnimationFrame(frame)
  }
  if (trackingFrame) {
    cancelAnimationFrame(trackingFrame)
  }
})

function updateBrushCenter() {
  if (frame) {
    cancelAnimationFrame(frame)
  }

  frame = requestAnimationFrame(() => {
    frame = 0
    measureBrushCenter()
  })
}

function measureBrushCenter() {
  const rect = anchorRef.value?.getBoundingClientRect()
  if (!rect) return

  if (props.centerOnAnchor) {
    brushCenter.value = {
      x: rect.left + rect.width / 2,
      y: rect.top + rect.height / 2,
    }
    return
  }

  brushCenter.value = {
    x: rect.left - 34,
    y: rect.top + 10,
  }
}

function startAnchorTracking() {
  const track = () => {
    measureBrushCenter()
    trackingFrame = requestAnimationFrame(track)
  }

  trackingFrame = requestAnimationFrame(track)
}

function findScrollParent(element: HTMLElement | null) {
  let parent = element?.parentElement
  while (parent) {
    const style = window.getComputedStyle(parent)
    if (/(auto|scroll|overlay)/.test(`${style.overflow}${style.overflowY}`)) {
      return parent
    }
    parent = parent.parentElement
  }

  return window
}
</script>

<style scoped>
.pending-brush-loader {
  display: block;
  width: v-bind('`${props.anchorSize}px`');
  height: v-bind('`${props.anchorSize}px`');
  pointer-events: none;
}

.pending-brush-overlay {
  position: fixed;
  top: 0;
  left: 0;
  pointer-events: none;
}
</style>
