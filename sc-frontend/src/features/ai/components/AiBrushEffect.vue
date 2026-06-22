<template>
  <span
    ref="anchorRef"
    aria-hidden="true"
    class="ai-brush-effect"
  />
</template>

<script lang="ts" setup>
import {nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'

import LegendaryCursor from '@/vendor/legendary-cursor'

const props = withDefaults(defineProps<{
  centerX?: number
  centerY?: number
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
let initialized = false

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
  initialized = true
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
  initialized = false
  LegendaryCursor.pause()
})

watch(() => [props.centerX, props.centerY], updateBrushCenter)

function updateBrushCenter() {
  if (!initialized) return

  LegendaryCursor.setAutoPilotCenter(getAnchorCenter())
}

function getAnchorCenter() {
  if (props.centerX !== undefined && props.centerY !== undefined) {
    return {
      x: props.centerX,
      y: props.centerY,
    }
  }

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
