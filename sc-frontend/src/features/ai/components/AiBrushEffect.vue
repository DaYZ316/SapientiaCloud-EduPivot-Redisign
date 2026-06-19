<template>
  <span
    ref="anchorRef"
    aria-hidden="true"
    class="ai-brush-effect"
  />
</template>

<script lang="ts" setup>
import {nextTick, onBeforeUnmount, onMounted, ref} from 'vue'

import LegendaryCursor from '@/vendor/legendary-cursor'

const props = withDefaults(defineProps<{
  radius?: number
  size?: number
  speed?: number
  zIndex?: number
}>(), {
  radius: 42,
  size: 86,
  speed: 3.45,
  zIndex: 2600,
})

const anchorRef = ref<HTMLElement | null>(null)
let resizeObserver: ResizeObserver | undefined

onMounted(async () => {
  await nextTick()
  LegendaryCursor.init({
    lineSize: 0.038,
    lineExpFactor: 0.6,
    speedExpFactor: 0.8,
    opacityDecrement: 0.55,
    sparklesCount: 65,
    maxOpacity: 1.13,
    autoPilot: true,
    autoPilotCenter: getAnchorCenter(),
    autoPilotRadius: props.radius,
    autoPilotSpeed: props.speed,
    zIndex: props.zIndex,
  })
  updateBrushCenter()
  window.addEventListener('resize', updateBrushCenter)
  resizeObserver = new ResizeObserver(updateBrushCenter)
  if (anchorRef.value) {
    resizeObserver.observe(anchorRef.value)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateBrushCenter)
  resizeObserver?.disconnect()
  LegendaryCursor.destroy()
})

function updateBrushCenter() {
  LegendaryCursor.setAutoPilotCenter(getAnchorCenter())
}

function getAnchorCenter() {
  const rect = anchorRef.value?.getBoundingClientRect()
  if (!rect) {
    return {
      x: window.innerWidth / 2,
      y: window.innerHeight / 2,
    }
  }

  return {
    x: rect.left + rect.width / 2,
    y: rect.top + rect.height / 2,
  }
}
</script>

<style scoped>
.ai-brush-effect {
  display: block;
  width: v-bind('`${props.size}px`');
  height: v-bind('`${props.size}px`');
  pointer-events: none;
}
</style>
