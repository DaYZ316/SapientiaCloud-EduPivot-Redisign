<template>
  <button
      :aria-label="ariaLabel"
      :class="{'is-dragging': isDragging, 'is-two-dimensional': appearance === 'two-dimensional'}"
      :disabled="isTransitioning"
      :style="launcherStyle"
      :title="ariaLabel"
      class="ai-trail-launcher"
      type="button"
      @click="handleClick"
      @pointercancel="handlePointerCancel"
      @pointerdown="handlePointerDown"
      @pointermove="handlePointerMove"
      @pointerup="handlePointerUp"
  >
    <span v-if="appearance === 'two-dimensional'" aria-hidden="true" class="launcher-icon">
      <MessageCircle :size="27" stroke-width="1.9"/>
      <Sparkles :size="13" class="launcher-sparkle" stroke-width="2.1"/>
    </span>
    <span v-else class="launcher-label">AI</span>
  </button>
</template>

<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {MessageCircle, Sparkles} from 'lucide-vue-next'

import LegendaryCursor from '@/vendor/legendary-cursor'
import type {AiLauncherAppearance} from '@/features/settings/stores/uiPreferences'

type LauncherPosition = {
  left: number
  top: number
}

type LauncherCenter = {
  x: number
  y: number
}

const props = withDefaults(defineProps<{
  ariaLabel?: string
  initialCenter?: LauncherCenter | null
  interactive?: boolean
  appearance?: AiLauncherAppearance
  snapInitialCenter?: boolean
  startAtCenter?: boolean
}>(), {
  ariaLabel: '天枢助手',
  initialCenter: null,
  interactive: true,
  appearance: 'two-dimensional',
  snapInitialCenter: false,
  startAtCenter: false,
})

const emit = defineEmits<{
  (event: 'click'): void
}>()

const STORAGE_KEY = 'sapientia-ai-trail-launcher-position'
const DRAG_THRESHOLD = 6
const DESKTOP_SIZE = 72
const MOBILE_SIZE = 64
const DESKTOP_MARGIN = 24
const MOBILE_MARGIN = 16
const MOBILE_QUERY = '(max-width: 640px)'
const CENTER_TRAVEL_MS = 940

type DragState = {
  pointerId: number
  startX: number
  startY: number
  dragged: boolean
}

type MomentumPath = {
  start: LauncherPosition
  controlOne: LauncherPosition
  controlTwo: LauncherPosition
  end: LauncherPosition
}

const position = ref<LauncherPosition>(getDefaultPosition())
const isDragging = ref(false)
const isTransitioning = ref(false)
const isBrushAppearance = computed(() => props.appearance === 'brush')
const launcherStyle = computed(() => ({
  left: `${position.value.left}px`,
  top: `${position.value.top}px`,
}))

let dragState: DragState | undefined
let suppressNextClick = false
let transitionFrame = 0
let transitionPromise: Promise<void> | undefined
let resolveTransition: (() => void) | undefined

onMounted(() => {
  position.value = getMountedPosition()
  syncBrushEffect()
  syncBrushEffectCenter()
  window.addEventListener('resize', handleResize)
})

watch(isBrushAppearance, () => {
  syncBrushEffect()
  syncBrushEffectCenter()
})

onBeforeUnmount(() => {
  finishCenterTransition()
  window.removeEventListener('resize', handleResize)
  LegendaryCursor.destroy()
})

function syncBrushEffect() {
  if (!isBrushAppearance.value) {
    LegendaryCursor.destroy()
    return
  }

  LegendaryCursor.init({
    lineSize: 0.038,
    lineExpFactor: 0.6,
    speedExpFactor: 0.8,
    opacityDecrement: 0.55,
    sparklesCount: 65,
    maxOpacity: 1.13,
    autoPilot: true,
    autoPilotCenter: getCenter(position.value),
    autoPilotRadius: 42,
    autoPilotSpeed: 3.45,
    zIndex: 2650,
  })
}

function syncBrushEffectCenter() {
  if (isBrushAppearance.value) {
    LegendaryCursor.setAutoPilotCenter(getCenter(position.value))
  }
}

function handleClick(event: MouseEvent) {
  if (!props.interactive) return

  if (suppressNextClick) {
    event.preventDefault()
    event.stopPropagation()
    suppressNextClick = false
    return
  }

  emit('click')
}

function handlePointerDown(event: PointerEvent) {
  if (!props.interactive) return
  if (isTransitioning.value) return
  if (event.button !== 0) return

  dragState = {
    pointerId: event.pointerId,
    startX: event.clientX,
    startY: event.clientY,
    dragged: false,
  }
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
}

function handlePointerMove(event: PointerEvent) {
  if (isTransitioning.value) return
  if (!dragState || event.pointerId !== dragState.pointerId) return

  const distance = Math.hypot(event.clientX - dragState.startX, event.clientY - dragState.startY)
  if (!dragState.dragged && distance <= DRAG_THRESHOLD) return

  dragState.dragged = true
  isDragging.value = true
  event.preventDefault()
  position.value = clampPosition({
    left: event.clientX - getMetrics().size / 2,
    top: event.clientY - getMetrics().size / 2,
  })
  syncBrushEffectCenter()
}

function handlePointerUp(event: PointerEvent) {
  finishPointerInteraction(event, true)
}

function handlePointerCancel(event: PointerEvent) {
  finishPointerInteraction(event, false)
}

function finishPointerInteraction(event: PointerEvent, shouldSuppressDraggedClick: boolean) {
  if (!dragState || event.pointerId !== dragState.pointerId) return

  const shouldSuppressClick = dragState.dragged && shouldSuppressDraggedClick
  releasePointerCapture(event)
  dragState = undefined
  isDragging.value = false

  if (shouldSuppressClick) {
    suppressNextClick = true
    position.value = snapToNearestEdge(position.value)
    syncBrushEffectCenter()
    persistPosition(position.value)
  }
}

function releasePointerCapture(event: PointerEvent) {
  const target = event.currentTarget as HTMLElement
  if (target.hasPointerCapture(event.pointerId)) {
    target.releasePointerCapture(event.pointerId)
  }
}

function handleResize() {
  if (isTransitioning.value) {
    return
  }

  position.value = snapToNearestEdge(position.value)
  syncBrushEffectCenter()
}

function playCenterTransition() {
  if (transitionPromise) return transitionPromise

  const startPosition = {...position.value}
  const endPosition = getCenterPosition()
  return playPositionTransition(startPosition, endPosition)
}

function playReturnTransition() {
  if (transitionPromise) return transitionPromise

  const startPosition = getCenterPosition()
  const endPosition = getInitialPosition()
  position.value = startPosition
  syncBrushEffectCenter()

  return playPositionTransition(startPosition, endPosition)
}

function placeAtCenter(center: LauncherCenter, options: { persist?: boolean; snapToEdge?: boolean } = {}) {
  position.value = positionFromCenter(center, {snapToEdge: options.snapToEdge ?? false})
  syncBrushEffectCenter()
  if (options.persist) {
    persistPosition(position.value)
  }
}

function playPositionTransition(startPosition: LauncherPosition, endPosition: LauncherPosition) {
  const path = createMomentumPath(startPosition, endPosition)
  const startedAt = performance.now()
  isTransitioning.value = true
  isDragging.value = true

  transitionPromise = new Promise((resolve) => {
    resolveTransition = resolve

    const moveAlongPath = (now: number) => {
      const progress = clamp((now - startedAt) / CENTER_TRAVEL_MS, 0, 1)
      position.value = sampleMomentumPath(path, easeInOutCubic(progress))
      syncBrushEffectCenter()

      if (progress < 1) {
        transitionFrame = window.requestAnimationFrame(moveAlongPath)
        return
      }

      position.value = endPosition
      syncBrushEffectCenter()
      completeTransition()
    }

    transitionFrame = window.requestAnimationFrame(moveAlongPath)
  })

  return transitionPromise
}

function finishCenterTransition() {
  finishTransition()
}

function finishTransition() {
  if (transitionFrame) {
    window.cancelAnimationFrame(transitionFrame)
    transitionFrame = 0
  }

  completeTransition()
}

function completeTransition() {
  transitionFrame = 0
  isDragging.value = false
  isTransitioning.value = false
  resolveTransitionOnly()
}

function resolveTransitionOnly() {
  resolveTransition?.()
  resolveTransition = undefined
  transitionPromise = undefined
}

function getInitialPosition() {
  const storedPosition = readStoredPosition()
  return snapToNearestEdge(storedPosition ?? getDefaultPosition())
}

function getMountedPosition() {
  if (props.startAtCenter) return getCenterPosition()
  if (props.initialCenter) {
    return positionFromCenter(props.initialCenter, {snapToEdge: props.snapInitialCenter})
  }

  return getInitialPosition()
}

function getDefaultPosition(): LauncherPosition {
  const {margin, size} = getMetrics()
  return {
    left: Math.max(margin, window.innerWidth - size - margin),
    top: Math.max(margin, window.innerHeight - size - margin),
  }
}

function getCenterPosition(): LauncherPosition {
  const {size} = getMetrics()
  return clampPosition({
    left: window.innerWidth / 2 - size / 2,
    top: window.innerHeight / 2 - size / 2,
  })
}

function positionFromCenter(center: LauncherCenter, options: { snapToEdge?: boolean } = {}) {
  const {size} = getMetrics()
  const nextPosition = clampPosition({
    left: center.x - size / 2,
    top: center.y - size / 2,
  })
  return options.snapToEdge ? snapToNearestEdge(nextPosition) : nextPosition
}

function createMomentumPath(startPosition: LauncherPosition, endPosition: LauncherPosition): MomentumPath {
  const deltaLeft = endPosition.left - startPosition.left
  const deltaTop = endPosition.top - startPosition.top
  const distance = Math.hypot(deltaLeft, deltaTop)
  if (distance === 0) {
    return {
      start: startPosition,
      controlOne: startPosition,
      controlTwo: endPosition,
      end: endPosition,
    }
  }

  const directionLeft = deltaLeft / distance
  const directionTop = deltaTop / distance
  const bend = Math.min(Math.max(distance * 0.18, 42), 120)
  const throwDistance = Math.min(Math.max(distance * 0.16, 32), 96)
  const side = getBendSide(directionLeft, startPosition)
  const perpendicularLeft = -directionTop * side
  const perpendicularTop = directionLeft * side

  return {
    start: startPosition,
    controlOne: {
      left: startPosition.left + deltaLeft * 0.18 + perpendicularLeft * bend,
      top: startPosition.top + deltaTop * 0.18 + perpendicularTop * bend,
    },
    controlTwo: {
      left: endPosition.left + directionLeft * throwDistance + perpendicularLeft * bend * 0.32,
      top: endPosition.top + directionTop * throwDistance + perpendicularTop * bend * 0.32,
    },
    end: endPosition,
  }
}

function getBendSide(directionLeft: number, startPosition: LauncherPosition) {
  if (Math.abs(directionLeft) < 0.1) return 1

  const {size} = getMetrics()
  const startCenterY = startPosition.top + size / 2
  const preferredVerticalDirection = startCenterY > window.innerHeight / 2 ? -1 : 1
  return preferredVerticalDirection / Math.sign(directionLeft)
}

function sampleMomentumPath(path: MomentumPath, amount: number): LauncherPosition {
  const oneMinusAmount = 1 - amount
  const firstWeight = oneMinusAmount * oneMinusAmount * oneMinusAmount
  const secondWeight = 3 * oneMinusAmount * oneMinusAmount * amount
  const thirdWeight = 3 * oneMinusAmount * amount * amount
  const fourthWeight = amount * amount * amount

  return {
    left: path.start.left * firstWeight
        + path.controlOne.left * secondWeight
        + path.controlTwo.left * thirdWeight
        + path.end.left * fourthWeight,
    top: path.start.top * firstWeight
        + path.controlOne.top * secondWeight
        + path.controlTwo.top * thirdWeight
        + path.end.top * fourthWeight,
  }
}

function snapToNearestEdge(nextPosition: LauncherPosition) {
  const {margin, size} = getMetrics()
  const centerX = nextPosition.left + size / 2
  const centerY = nextPosition.top + size / 2
  const rightDistance = window.innerWidth - centerX
  const bottomDistance = window.innerHeight - centerY
  const nearestDistance = Math.min(centerX, rightDistance, centerY, bottomDistance)

  if (nearestDistance === centerX) {
    return clampPosition({
      left: margin,
      top: nextPosition.top,
    })
  }

  if (nearestDistance === rightDistance) {
    return clampPosition({
      left: Math.max(margin, window.innerWidth - size - margin),
      top: nextPosition.top,
    })
  }

  if (nearestDistance === centerY) {
    return clampPosition({
      left: nextPosition.left,
      top: margin,
    })
  }

  return clampPosition({
    left: nextPosition.left,
    top: Math.max(margin, window.innerHeight - size - margin),
  })
}

function clampPosition(nextPosition: LauncherPosition) {
  const {margin, size} = getMetrics()
  return {
    left: clamp(nextPosition.left, margin, Math.max(margin, window.innerWidth - size - margin)),
    top: clamp(nextPosition.top, margin, Math.max(margin, window.innerHeight - size - margin)),
  }
}

function getCenter(nextPosition: LauncherPosition) {
  const {size} = getMetrics()
  return {
    x: nextPosition.left + size / 2,
    y: nextPosition.top + size / 2,
  }
}

function getMetrics() {
  const isMobile = window.matchMedia(MOBILE_QUERY).matches
  return {
    margin: isMobile ? MOBILE_MARGIN : DESKTOP_MARGIN,
    size: isMobile ? MOBILE_SIZE : DESKTOP_SIZE,
  }
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max)
}

function easeInOutCubic(value: number) {
  return value < 0.5
      ? 4 * value * value * value
      : 1 - Math.pow(-2 * value + 2, 3) / 2
}

function readStoredPosition() {
  try {
    const rawPosition = window.localStorage.getItem(STORAGE_KEY)
    if (!rawPosition) return undefined

    const parsedPosition: unknown = JSON.parse(rawPosition)
    if (isLauncherPosition(parsedPosition)) return parsedPosition
  } catch {
    return undefined
  }

  return undefined
}

function persistPosition(nextPosition: LauncherPosition) {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(nextPosition))
  } catch {
    return
  }
}

function isLauncherPosition(value: unknown): value is LauncherPosition {
  if (!value || typeof value !== 'object') return false

  const maybePosition = value as { left?: unknown; top?: unknown }
  return typeof maybePosition.left === 'number'
      && Number.isFinite(maybePosition.left)
      && typeof maybePosition.top === 'number'
      && Number.isFinite(maybePosition.top)
}

defineExpose({
  finishCenterTransition,
  finishTransition,
  getLauncherCenter: () => getCenter(position.value),
  placeAtCenter,
  playCenterTransition,
  playReturnTransition,
})
</script>

<style scoped>
.ai-trail-launcher {
  position: fixed;
  z-index: 1900;
  display: grid;
  width: 72px;
  height: 72px;
  place-items: center;
  padding: 0;
  background: transparent;
  border: 0;
  color: transparent;
  cursor: pointer;
  touch-action: none;
  user-select: none;
}

.ai-trail-launcher.is-two-dimensional {
  border: 1px solid var(--color-primary);
  border-radius: 18px;
  background: var(--color-primary);
  color: var(--color-on-primary);
  box-shadow: 0 10px 24px color-mix(in srgb, var(--color-on-primary) 16%, transparent);
  transition: background 0.2s, border-color 0.2s, color 0.2s, transform 0.2s;
}

.ai-trail-launcher.is-two-dimensional:hover:not(:disabled) {
  background: var(--color-primary-soft);
  border-color: var(--color-primary-soft);
}

.ai-trail-launcher.is-two-dimensional:active:not(:disabled) {
  transform: translateY(1px);
}

.ai-trail-launcher.is-dragging {
  cursor: grabbing;
}

.launcher-label {
  width: 1px;
  height: 1px;
  overflow: hidden;
}

.launcher-icon {
  position: relative;
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
}

.launcher-sparkle {
  position: absolute;
  top: -1px;
  right: -3px;
}

.ai-trail-launcher:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 4px;
}

@media (max-width: 640px) {
  .ai-trail-launcher {
    width: 64px;
    height: 64px;
  }
}
</style>
