<template>
  <Teleport to="body">
    <AiTrailLauncher
      v-if="!open"
      ref="launcherRef"
      :initial-center="launcherSpawnCenter"
      :interactive="!launcherLocked"
      aria-label="打开 AI 教学助手"
      @click="openDrawer"
    />

    <div
      v-if="open"
      class="drawer-layer"
    >
      <button
        class="drawer-backdrop"
        type="button"
        @click="closeDrawer"
      />
      <aside
        :class="{'is-dragging': isDraggingDrawer, 'is-resizing': isResizingDrawer}"
        class="ai-drawer"
        :style="drawerStyle"
      >
        <div
          class="drawer-topbar"
          @pointerdown="startDrawerDrag"
        >
          <div>
            <span>Celestial Hub</span>
            <h2>全局助手</h2>
          </div>
          <button
            class="icon-button"
            title="关闭"
            type="button"
            @click="closeDrawer"
          >
            <X
              :size="18"
              stroke-width="1.8"
            />
          </button>
        </div>
        <AiWorkspaceShell layout="drawer" />
        <button
          v-for="handle in resizeHandles"
          :key="handle"
          :aria-label="`调整窗口${handle}`"
          :class="`resize-handle resize-${handle}`"
          type="button"
          @pointerdown="startDrawerResize($event, handle)"
        />
      </aside>
    </div>
  </Teleport>
</template>

<script lang="ts" setup>
import {computed, nextTick, onBeforeUnmount, ref, watch} from 'vue'
import {useRoute} from 'vue-router'
import {X} from 'lucide-vue-next'

import AiTrailLauncher from '@/features/ai/components/AiTrailLauncher.vue'
import AiWorkspaceShell from '@/features/ai/components/AiWorkspaceShell.vue'
import {useAiStore} from '@/features/ai/stores/ai'

const route = useRoute()
const aiStore = useAiStore()
const open = ref(false)
const launcherLocked = ref(false)
const launcherRef = ref<InstanceType<typeof AiTrailLauncher> | null>(null)
const launcherSpawnCenter = ref<Point | null>(null)
const isDraggingDrawer = ref(false)
const isResizingDrawer = ref(false)
const viewportWidth = ref(window.innerWidth)
const viewportHeight = ref(window.innerHeight)
const drawerPosition = ref<DrawerPosition | null>(null)
let dragStart: DrawerDragStart | null = null
let resizeStart: DrawerResizeStart | null = null

const DRAWER_MARGIN = 16
const DRAWER_DEFAULT_WIDTH = 1040
const DRAWER_DEFAULT_HEIGHT = 780
const DRAWER_MIN_WIDTH = 720
const DRAWER_MIN_HEIGHT = 520
const MOBILE_DRAWER_BREAKPOINT = 640
const resizeHandles: DrawerResizeEdge[] = ['n', 'e', 's', 'w', 'ne', 'nw', 'se', 'sw']

const routeContext = computed(() => {
  const params = route.params
  const routeName = typeof route.name === 'string' ? route.name : ''
  const questionBankId = routeName === 'question-bank-detail'
    ? stringParam(params.id)
    : undefined
  return {
    sourceRoute: route.fullPath,
    courseId: stringParam(params.courseId) || (questionBankId ? undefined : stringParam(params.id)),
    questionBankId,
    classSessionId: stringParam(params.sessionId),
    chapterId: routeName === 'chapter-detail' ? stringParam(params.courseId) : undefined,
  }
})
const isMobileDrawer = computed(() => viewportWidth.value <= MOBILE_DRAWER_BREAKPOINT)
const drawerStyle = computed(() => {
  const position = drawerPosition.value
  if (isMobileDrawer.value || !position) return undefined

  return {
    top: `${position.top}px`,
    right: 'auto',
    bottom: 'auto',
    left: `${position.left}px`,
    width: `${position.width}px`,
    height: `${position.height}px`,
  }
})

watch(routeContext, (context) => {
  aiStore.setContext(context)
}, {immediate: true})

watch(open, async (isOpen) => {
  if (!isOpen) {
    stopDrawerInteraction()
    return
  }

  await nextTick()
  initDrawerPosition()
})

window.addEventListener('resize', syncViewport)

onBeforeUnmount(() => {
  stopDrawerInteraction()
  window.removeEventListener('resize', syncViewport)
})

function openDrawer() {
  const launcherCenter = launcherRef.value?.getLauncherCenter()
  if (launcherCenter) {
    launcherSpawnCenter.value = launcherCenter
    drawerPosition.value = null
  }
  open.value = true
}

function closeDrawer() {
  syncLauncherToDrawer()
  open.value = false
}

function stringParam(value: unknown) {
  return typeof value === 'string' ? value : undefined
}

function syncViewport() {
  viewportWidth.value = window.innerWidth
  viewportHeight.value = window.innerHeight
  if (!open.value) return

  if (isMobileDrawer.value) {
    drawerPosition.value = null
    return
  }

  if (!drawerPosition.value) {
    initDrawerPosition()
    return
  }

  drawerPosition.value = clampDrawerPosition(drawerPosition.value)
}

function initDrawerPosition() {
  if (isMobileDrawer.value) {
    drawerPosition.value = null
    return
  }

  const width = Math.min(DRAWER_DEFAULT_WIDTH, Math.max(0, viewportWidth.value - DRAWER_MARGIN * 2))
  const height = Math.min(DRAWER_DEFAULT_HEIGHT, Math.max(0, viewportHeight.value - DRAWER_MARGIN * 2))
  drawerPosition.value = drawerPosition.value
    ? clampDrawerPosition(drawerPosition.value)
    : createInitialDrawerPosition(width, height, launcherSpawnCenter.value)
}

function createInitialDrawerPosition(width: number, height: number, center: Point | null) {
  if (!center) {
    return clampDrawerPosition({
      width,
      height,
      left: viewportWidth.value - width - DRAWER_MARGIN,
      top: DRAWER_MARGIN,
    })
  }

  const prefersRightSide = center.x <= viewportWidth.value / 2
  const prefersBelow = center.y <= viewportHeight.value / 2
  return clampDrawerPosition({
    width,
    height,
    left: prefersRightSide
      ? center.x + DRAWER_MARGIN
      : center.x - width - DRAWER_MARGIN,
    top: prefersBelow
      ? center.y + DRAWER_MARGIN
      : center.y - height - DRAWER_MARGIN,
  })
}

function startDrawerDrag(event: PointerEvent) {
  if (isMobileDrawer.value || event.button !== 0) return
  if ((event.target as HTMLElement).closest('button, a, input, textarea, select, [data-no-drag]')) return
  if (!drawerPosition.value) {
    initDrawerPosition()
  }
  const position = drawerPosition.value
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
  isDraggingDrawer.value = true
  window.addEventListener('pointermove', handleDrawerDrag)
  window.addEventListener('pointerup', stopDrawerInteraction)
  window.addEventListener('pointercancel', stopDrawerInteraction)
}

function handleDrawerDrag(event: PointerEvent) {
  if (!dragStart) return

  drawerPosition.value = clampDrawerPosition({
    width: dragStart.width,
    height: dragStart.height,
    left: dragStart.left + event.clientX - dragStart.pointerX,
    top: dragStart.top + event.clientY - dragStart.pointerY,
  })
}

function startDrawerResize(event: PointerEvent, edge: DrawerResizeEdge) {
  if (isMobileDrawer.value || event.button !== 0) return
  if (!drawerPosition.value) {
    initDrawerPosition()
  }
  const position = drawerPosition.value
  if (!position) return

  event.preventDefault()
  event.stopPropagation()
  resizeStart = {
    ...position,
    edge,
    pointerX: event.clientX,
    pointerY: event.clientY,
  }
  isResizingDrawer.value = true
  window.addEventListener('pointermove', handleDrawerResize)
  window.addEventListener('pointerup', stopDrawerInteraction)
  window.addEventListener('pointercancel', stopDrawerInteraction)
}

function handleDrawerResize(event: PointerEvent) {
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

  const clamped = clampDrawerPosition({width, height, left, top})
  if (resizeStart.edge.includes('w')) {
    clamped.left = Math.max(DRAWER_MARGIN, startRight - clamped.width)
  }
  if (resizeStart.edge.includes('n')) {
    clamped.top = Math.max(DRAWER_MARGIN, startBottom - clamped.height)
  }
  drawerPosition.value = clampDrawerPosition(clamped)
}

function stopDrawerInteraction() {
  dragStart = null
  resizeStart = null
  isDraggingDrawer.value = false
  isResizingDrawer.value = false
  window.removeEventListener('pointermove', handleDrawerDrag)
  window.removeEventListener('pointermove', handleDrawerResize)
  window.removeEventListener('pointerup', stopDrawerInteraction)
  window.removeEventListener('pointercancel', stopDrawerInteraction)
}

function clampDrawerPosition(position: DrawerPosition): DrawerPosition {
  const maxWidth = Math.max(0, viewportWidth.value - DRAWER_MARGIN * 2)
  const maxHeight = Math.max(0, viewportHeight.value - DRAWER_MARGIN * 2)
  const minWidth = Math.min(DRAWER_MIN_WIDTH, maxWidth)
  const minHeight = Math.min(DRAWER_MIN_HEIGHT, maxHeight)
  const width = Math.min(Math.max(position.width, minWidth), maxWidth)
  const height = Math.min(Math.max(position.height, minHeight), maxHeight)
  const maxLeft = Math.max(DRAWER_MARGIN, viewportWidth.value - width - DRAWER_MARGIN)
  const maxTop = Math.max(DRAWER_MARGIN, viewportHeight.value - height - DRAWER_MARGIN)
  return {
    width,
    height,
    left: Math.min(Math.max(position.left, DRAWER_MARGIN), maxLeft),
    top: Math.min(Math.max(position.top, DRAWER_MARGIN), maxTop),
  }
}

function syncLauncherToDrawer() {
  if (isMobileDrawer.value) return

  const position = drawerPosition.value
  if (!position) return

  const center = {
    x: position.left + position.width / 2,
    y: position.top + position.height / 2,
  }
  launcherSpawnCenter.value = launcherRef.value?.getSnappedCenter(center) ?? center
  launcherRef.value?.placeAtCenter(center, {persist: true, snapToEdge: true})
}

async function playLauncherCenterTransition() {
  launcherLocked.value = true
  if (open.value) {
    open.value = false
    await nextTick()
  }

  return launcherRef.value?.playCenterTransition() ?? Promise.resolve()
}

function finishLauncherCenterTransition() {
  launcherLocked.value = false
  launcherRef.value?.finishCenterTransition()
}

defineExpose({
  finishLauncherCenterTransition,
  playLauncherCenterTransition,
})

interface DrawerPosition {
  width: number
  height: number
  left: number
  top: number
}

interface Point {
  x: number
  y: number
}

interface DrawerDragStart extends DrawerPosition {
  pointerX: number
  pointerY: number
}

interface DrawerResizeStart extends DrawerDragStart {
  edge: DrawerResizeEdge
}

type DrawerResizeEdge = 'n' | 'e' | 's' | 'w' | 'ne' | 'nw' | 'se' | 'sw'
</script>

<style scoped>
.drawer-layer {
  --font-ai-sans: MiSans, 'Noto Sans SC', 'Microsoft YaHei', DengXian, 'Segoe UI', system-ui, sans-serif;
  --font-ai-mono: 'Cascadia Code', 'Cascadia Mono', Consolas, monospace;
  --font-heading: 'Bodoni Moda', 'Georgia', serif;
  --font-body: var(--font-ai-sans);
  --font-label: var(--font-ai-sans);
  position: fixed;
  inset: 0;
  z-index: 2300;
  font-family: var(--font-ai-sans);
  pointer-events: none;
}

.drawer-backdrop {
  position: absolute;
  inset: 0;
  border: 0;
  background: rgba(0, 0, 0, 0.28);
  pointer-events: auto;
}

.ai-drawer {
  position: absolute;
  top: 16px;
  right: 16px;
  bottom: 16px;
  display: flex;
  width: min(1040px, calc(100vw - 32px));
  flex-direction: column;
  overflow: hidden;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline);
  color: var(--color-on-surface);
  pointer-events: auto;
  box-shadow: 0 24px 64px rgba(15, 23, 42, 0.3);
}

.drawer-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 16px 18px;
  border-bottom: 1px solid var(--color-outline-light);
  cursor: move;
  touch-action: none;
  user-select: none;
}

.ai-drawer.is-dragging,
.ai-drawer.is-resizing {
  box-shadow: 0 28px 76px rgba(15, 23, 42, 0.36);
}

.ai-drawer.is-dragging .drawer-topbar {
  cursor: grabbing;
}

.drawer-topbar span {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
}

.drawer-topbar h2 {
  margin: 5px 0 0;
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 400;
}

.icon-button {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  background: transparent;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  cursor: pointer;
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

.ai-drawer :deep(.monolith-ai-shell) {
  border: 0;
}

@media (max-width: 640px) {
  .ai-drawer {
    inset: auto 0 0;
    width: 100vw;
    height: min(86dvh, 760px);
    border-right: 0;
    border-bottom: 0;
    border-left: 0;
  }

  .drawer-topbar {
    cursor: default;
    touch-action: auto;
  }

  .resize-handle {
    display: none;
  }
}
</style>
