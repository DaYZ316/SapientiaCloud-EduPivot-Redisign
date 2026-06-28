<template>
  <Teleport to="body">
    <AiTrailLauncher
        v-if="!open"
        ref="launcherRef"
        :initial-center="launcherSpawnCenter"
        :interactive="!launcherLocked"
        aria-label="天枢助手"
        snap-initial-center
        @click="openDrawer"
    />

    <FloatingWindow
        v-if="open"
        ref="drawerWindowRef"
        v-model:position="drawerPosition"
        :default-height="DRAWER_DEFAULT_HEIGHT"
        :default-width="DRAWER_DEFAULT_WIDTH"
        :initial-center="launcherSpawnCenter"
        :margin="DRAWER_MARGIN"
        :min-height="DRAWER_MIN_HEIGHT"
        :min-width="DRAWER_MIN_WIDTH"
        :mobile-breakpoint="MOBILE_DRAWER_BREAKPOINT"
        backdrop
        drag-handle-selector=".drawer-topbar"
        layer-class="drawer-layer"
        window-class="ai-drawer"
        @backdrop="closeDrawer"
    >
      <div class="drawer-topbar">
        <div>
          <span>Celestial Hub</span>
          <h2>天枢助手</h2>
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
      <AiWorkspaceShell layout="drawer"/>
    </FloatingWindow>
  </Teleport>
</template>

<script lang="ts" setup>
import {computed, nextTick, ref, watch} from 'vue'
import {useRoute} from 'vue-router'
import {X} from 'lucide-vue-next'

import AiTrailLauncher from '@/features/ai/components/AiTrailLauncher.vue'
import AiWorkspaceShell from '@/features/ai/components/AiWorkspaceShell.vue'
import {useAiStore} from '@/features/ai/stores/ai'
import FloatingWindow from '@/shared/components/FloatingWindow.vue'

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

const route = useRoute()
const aiStore = useAiStore()
const open = ref(false)
const launcherLocked = ref(false)
const launcherRef = ref<InstanceType<typeof AiTrailLauncher> | null>(null)
const drawerWindowRef = ref<InstanceType<typeof FloatingWindow> | null>(null)
const launcherSpawnCenter = ref<Point | null>(null)
const drawerPosition = ref<DrawerPosition | null>(null)

const DRAWER_MARGIN = 16
const DRAWER_DEFAULT_WIDTH = 1040
const DRAWER_DEFAULT_HEIGHT = 780
const DRAWER_MIN_WIDTH = 720
const DRAWER_MIN_HEIGHT = 520
const MOBILE_DRAWER_BREAKPOINT = 1180

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

watch(routeContext, (context) => {
  aiStore.setContext(context)
}, {immediate: true})

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

function syncLauncherToDrawer() {
  const center = drawerWindowRef.value?.getWindowCenter()
  if (center) {
    launcherSpawnCenter.value = center
  }
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
</script>

<style scoped>
:deep(.drawer-layer) {
  --floating-window-font-family: var(--font-ai-sans);
  --floating-window-z-index: 2300;
  --floating-window-bg: var(--color-surface-card);
  --floating-window-border: 1px solid var(--color-outline);
  --floating-window-radius: 0;
  --floating-window-color: var(--color-on-surface);
  --floating-window-shadow: 0 24px 64px rgba(15, 23, 42, 0.3);
  --floating-window-active-shadow: 0 28px 76px rgba(15, 23, 42, 0.36);
  --font-ai-sans: MiSans, 'Noto Sans SC', 'Microsoft YaHei', DengXian, 'Segoe UI', system-ui, sans-serif;
  --font-ai-mono: 'Cascadia Code', 'Cascadia Mono', Consolas, monospace;
  --font-heading: 'Bodoni Moda', 'Georgia', serif;
  --font-body: var(--font-ai-sans);
  --font-label: var(--font-ai-sans);
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

:deep(.ai-drawer.is-dragging) .drawer-topbar {
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

:deep(.ai-drawer .monolith-ai-shell) {
  border: 0;
}

@media (max-width: 640px) {
  .drawer-topbar {
    cursor: default;
    touch-action: auto;
  }
}
</style>
