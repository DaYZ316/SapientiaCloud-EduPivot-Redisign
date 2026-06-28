<template>
  <slot v-if="isWindowDisabled"/>
  <div
      v-else
      :class="[layerClass, {'is-mobile-window': isMobileWindow}]"
      class="floating-window-layer"
  >
    <button
        v-if="backdrop"
        class="floating-window-backdrop"
        type="button"
        @click="emit('backdrop')"
    />
    <section
        :class="[windowClass, {'is-dragging': isDraggingWindow, 'is-resizing': isResizingWindow}]"
        :style="windowStyle"
        class="floating-window"
        @pointerdown="startWindowDrag"
    >
      <slot/>
      <button
          v-if="resizable"
          v-for="handle in resizeHandles"
          :key="handle"
          :aria-label="`Resize window ${handle}`"
          :class="`resize-handle resize-${handle}`"
          type="button"
          @pointerdown="startWindowResize($event, handle)"
      />
    </section>
  </div>
</template>

<script lang="ts" setup>
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'

interface FloatingWindowPosition {
  width: number
  height: number
  left: number
  top: number
}

interface FloatingWindowPoint {
  x: number
  y: number
}

interface FloatingWindowDragStart extends FloatingWindowPosition {
  pointerX: number
  pointerY: number
}

interface FloatingWindowResizeStart extends FloatingWindowDragStart {
  edge: FloatingWindowResizeEdge
}

type FloatingWindowResizeEdge = 'n' | 'e' | 's' | 'w' | 'ne' | 'nw' | 'se' | 'sw'

const props = withDefaults(defineProps<{
  backdrop?: boolean
  defaultHeight?: number
  defaultWidth?: number
  disabled?: boolean
  disableOnMobile?: boolean
  dragHandleSelector?: string
  initialCenter?: FloatingWindowPoint | null
  layerClass?: string
  margin?: number
  minHeight?: number
  minWidth?: number
  mobileBreakpoint?: number
  position?: FloatingWindowPosition | null
  resizable?: boolean
  windowClass?: string
}>(), {
  backdrop: false,
  defaultHeight: 780,
  defaultWidth: 1040,
  disabled: false,
  disableOnMobile: false,
  dragHandleSelector: '',
  initialCenter: null,
  layerClass: '',
  margin: 16,
  minHeight: 520,
  minWidth: 720,
  mobileBreakpoint: 640,
  position: undefined,
  resizable: true,
  windowClass: '',
})

const emit = defineEmits<{
  'update:position': [position: FloatingWindowPosition | null]
  backdrop: []
}>()

const resizeHandles: FloatingWindowResizeEdge[] = ['n', 'e', 's', 'w', 'ne', 'nw', 'se', 'sw']

const viewportWidth = ref(window.innerWidth)
const viewportHeight = ref(window.innerHeight)
const hasCoarsePointer = ref(hasCoarsePrimaryPointer())
const internalPosition = ref<FloatingWindowPosition | null>(null)
const isDraggingWindow = ref(false)
const isResizingWindow = ref(false)
let dragStart: FloatingWindowDragStart | null = null
let resizeStart: FloatingWindowResizeStart | null = null

const isMobileWindow = computed(() => viewportWidth.value <= props.mobileBreakpoint || hasCoarsePointer.value)
const isWindowDisabled = computed(() => props.disabled || (props.disableOnMobile && isMobileWindow.value))
const windowStyle = computed(() => {
  const position = getWindowPosition()
  if (isMobileWindow.value || !position) return undefined

  return {
    top: `${position.top}px`,
    right: 'auto',
    bottom: 'auto',
    left: `${position.left}px`,
    width: `${position.width}px`,
    height: `${position.height}px`,
  }
})

onMounted(() => {
  window.addEventListener('resize', syncViewport)
  initWindowPosition()
})

onBeforeUnmount(() => {
  stopWindowInteraction()
  window.removeEventListener('resize', syncViewport)
})

watch(isWindowDisabled, async (disabled) => {
  stopWindowInteraction()
  if (disabled) {
    setWindowPosition(null)
    return
  }
  await nextTick()
  initWindowPosition()
})

function getWindowPosition() {
  return props.position === undefined ? internalPosition.value : props.position
}

function setWindowPosition(position: FloatingWindowPosition | null) {
  internalPosition.value = position
  emit('update:position', position)
}

function syncViewport() {
  viewportWidth.value = window.innerWidth
  viewportHeight.value = window.innerHeight
  hasCoarsePointer.value = hasCoarsePrimaryPointer()
  if (isWindowDisabled.value) {
    setWindowPosition(null)
    return
  }
  if (isMobileWindow.value) {
    setWindowPosition(null)
    return
  }

  const position = getWindowPosition()
  if (!position) {
    initWindowPosition()
    return
  }

  setWindowPosition(clampWindowPosition(position))
}

function initWindowPosition() {
  if (isWindowDisabled.value || isMobileWindow.value) {
    setWindowPosition(null)
    return
  }

  const width = Math.min(props.defaultWidth, Math.max(0, viewportWidth.value - props.margin * 2))
  const height = Math.min(props.defaultHeight, Math.max(0, viewportHeight.value - props.margin * 2))
  const currentPosition = getWindowPosition()
  setWindowPosition(currentPosition
      ? clampWindowPosition(currentPosition)
      : createInitialWindowPosition(width, height, props.initialCenter))
}

function createInitialWindowPosition(width: number, height: number, center: FloatingWindowPoint | null) {
  if (!center) {
    return clampWindowPosition({
      width,
      height,
      left: viewportWidth.value - width - props.margin,
      top: props.margin,
    })
  }

  const prefersRightSide = center.x <= viewportWidth.value / 2
  const prefersBelow = center.y <= viewportHeight.value / 2
  return clampWindowPosition({
    width,
    height,
    left: prefersRightSide
        ? center.x + props.margin
        : center.x - width - props.margin,
    top: prefersBelow
        ? center.y + props.margin
        : center.y - height - props.margin,
  })
}

function startWindowDrag(event: PointerEvent) {
  if (isMobileWindow.value || event.button !== 0) return
  const target = event.target as HTMLElement
  if (target.closest('button, a, input, textarea, select, [data-no-drag]')) return
  if (props.dragHandleSelector && !target.closest(props.dragHandleSelector)) return
  if (!getWindowPosition()) {
    initWindowPosition()
  }
  const position = getWindowPosition()
  if (!position) return

  event.preventDefault()
  dragStart = {
    pointerX: event.clientX,
    pointerY: event.clientY,
    left: position.left,
    top: position.top,
    width: position.width,
    height: position.height,
  }
  isDraggingWindow.value = true
  window.addEventListener('pointermove', handleWindowDrag)
  window.addEventListener('pointerup', stopWindowInteraction)
  window.addEventListener('pointercancel', stopWindowInteraction)
}

function handleWindowDrag(event: PointerEvent) {
  if (!dragStart) return

  setWindowPosition(clampWindowPosition({
    width: dragStart.width,
    height: dragStart.height,
    left: dragStart.left + event.clientX - dragStart.pointerX,
    top: dragStart.top + event.clientY - dragStart.pointerY,
  }))
}

function startWindowResize(event: PointerEvent, edge: FloatingWindowResizeEdge) {
  if (!props.resizable || isMobileWindow.value || event.button !== 0) return
  if (!getWindowPosition()) {
    initWindowPosition()
  }
  const position = getWindowPosition()
  if (!position) return

  event.preventDefault()
  event.stopPropagation()
  resizeStart = {
    ...position,
    edge,
    pointerX: event.clientX,
    pointerY: event.clientY,
  }
  isResizingWindow.value = true
  window.addEventListener('pointermove', handleWindowResize)
  window.addEventListener('pointerup', stopWindowInteraction)
  window.addEventListener('pointercancel', stopWindowInteraction)
}

function handleWindowResize(event: PointerEvent) {
  if (!resizeStart) return

  const deltaX = event.clientX - resizeStart.pointerX
  const deltaY = event.clientY - resizeStart.pointerY
  const startRight = resizeStart.left + resizeStart.width
  const startBottom = resizeStart.top + resizeStart.height
  let left = resizeStart.left
  let top = resizeStart.top
  let width = resizeStart.width
  let height = resizeStart.height

  if (resizeStart.edge.includes('e')) {
    width = resizeStart.width + deltaX
  }
  if (resizeStart.edge.includes('s')) {
    height = resizeStart.height + deltaY
  }
  if (resizeStart.edge.includes('w')) {
    width = resizeStart.width - deltaX
    left = resizeStart.left + deltaX
  }
  if (resizeStart.edge.includes('n')) {
    height = resizeStart.height - deltaY
    top = resizeStart.top + deltaY
  }

  const clamped = clampWindowPosition({width, height, left, top})
  if (resizeStart.edge.includes('w')) {
    clamped.left = Math.max(props.margin, startRight - clamped.width)
  }
  if (resizeStart.edge.includes('n')) {
    clamped.top = Math.max(props.margin, startBottom - clamped.height)
  }
  setWindowPosition(clampWindowPosition(clamped))
}

function stopWindowInteraction() {
  dragStart = null
  resizeStart = null
  isDraggingWindow.value = false
  isResizingWindow.value = false
  window.removeEventListener('pointermove', handleWindowDrag)
  window.removeEventListener('pointermove', handleWindowResize)
  window.removeEventListener('pointerup', stopWindowInteraction)
  window.removeEventListener('pointercancel', stopWindowInteraction)
}

function clampWindowPosition(position: FloatingWindowPosition): FloatingWindowPosition {
  const maxWidth = Math.max(0, viewportWidth.value - props.margin * 2)
  const maxHeight = Math.max(0, viewportHeight.value - props.margin * 2)
  const minWidth = Math.min(props.minWidth, maxWidth)
  const minHeight = Math.min(props.minHeight, maxHeight)
  const width = Math.min(Math.max(position.width, minWidth), maxWidth)
  const height = Math.min(Math.max(position.height, minHeight), maxHeight)
  const maxLeft = Math.max(props.margin, viewportWidth.value - width - props.margin)
  const maxTop = Math.max(props.margin, viewportHeight.value - height - props.margin)
  return {
    width,
    height,
    left: Math.min(Math.max(position.left, props.margin), maxLeft),
    top: Math.min(Math.max(position.top, props.margin), maxTop),
  }
}

function getWindowCenter() {
  if (isMobileWindow.value) return null
  const position = getWindowPosition()
  if (!position) return null

  return {
    x: position.left + position.width / 2,
    y: position.top + position.height / 2,
  }
}

function hasCoarsePrimaryPointer() {
  if (typeof window.matchMedia !== 'function') return false
  return window.matchMedia('(pointer: coarse)').matches
}

defineExpose({
  getWindowCenter,
  isMobileWindow,
})
</script>

<style scoped>
.floating-window-layer {
  position: fixed;
  inset: 0;
  z-index: var(--floating-window-z-index, 2300);
  font-family: var(--floating-window-font-family, inherit);
  pointer-events: none;
}

.floating-window-backdrop {
  position: absolute;
  inset: 0;
  border: 0;
  background: var(--floating-window-backdrop-bg, rgba(0, 0, 0, 0.28));
  pointer-events: auto;
}

.floating-window {
  position: absolute;
  top: var(--floating-window-top, 16px);
  right: var(--floating-window-right, 16px);
  bottom: var(--floating-window-bottom, 16px);
  display: flex;
  width: min(var(--floating-window-css-width, 1040px), calc(100vw - 32px));
  flex-direction: column;
  overflow: hidden;
  background: var(--floating-window-bg, var(--color-surface-card));
  border: var(--floating-window-border, 1px solid var(--color-outline));
  border-radius: var(--floating-window-radius, var(--radius-sm));
  color: var(--floating-window-color, var(--color-on-surface));
  pointer-events: auto;
  box-shadow: var(--floating-window-shadow, 0 24px 64px rgba(15, 23, 42, 0.3));
}

.floating-window.is-dragging,
.floating-window.is-resizing {
  box-shadow: var(--floating-window-active-shadow, 0 28px 76px rgba(15, 23, 42, 0.36));
}

.resize-handle {
  position: absolute;
  z-index: 10;
  padding: 0;
  background: transparent;
  border: 0;
  pointer-events: auto;
}

.resize-n,
.resize-s {
  right: 12px;
  left: 12px;
  height: 10px;
  cursor: ns-resize;
}

.resize-n {
  top: -5px;
}

.resize-s {
  bottom: -5px;
}

.resize-e,
.resize-w {
  top: 12px;
  bottom: 12px;
  width: 10px;
  cursor: ew-resize;
}

.resize-e {
  right: -5px;
}

.resize-w {
  left: -5px;
}

.resize-ne,
.resize-nw,
.resize-se,
.resize-sw {
  width: 16px;
  height: 16px;
}

.resize-ne {
  top: -5px;
  right: -5px;
  cursor: nesw-resize;
}

.resize-nw {
  top: -5px;
  left: -5px;
  cursor: nwse-resize;
}

.resize-se {
  right: -5px;
  bottom: -5px;
  cursor: nwse-resize;
}

.resize-sw {
  bottom: -5px;
  left: -5px;
  cursor: nesw-resize;
}

.floating-window-layer.is-mobile-window .floating-window {
  inset: 0;
  width: 100vw;
  height: 100dvh;
  border: 0;
  border-radius: 0;
}

.floating-window-layer.is-mobile-window .resize-handle {
  display: none;
}

@media (max-width: 640px) {
  .floating-window {
    inset: 0;
    width: 100vw;
    height: 100dvh;
    border: 0;
    border-radius: 0;
  }

  .resize-handle {
    display: none;
  }
}
</style>
