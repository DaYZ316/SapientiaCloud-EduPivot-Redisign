<template>
  <Teleport to="body">
    <AiTrailLauncher
      ref="launcherRef"
      v-if="!open"
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
      <aside class="ai-drawer">
        <div class="drawer-topbar">
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
        <AiChatPanel
          :show-header="false"
          title="全局助手"
        />
      </aside>
    </div>
  </Teleport>
</template>

<script lang="ts" setup>
import {computed, nextTick, ref, watch} from 'vue'
import {useRoute} from 'vue-router'
import {X} from 'lucide-vue-next'

import AiChatPanel from '@/features/ai/components/AiChatPanel.vue'
import AiTrailLauncher from '@/features/ai/components/AiTrailLauncher.vue'
import {useAiStore} from '@/features/ai/stores/ai'

const route = useRoute()
const aiStore = useAiStore()
const open = ref(false)
const launcherLocked = ref(false)
const launcherRef = ref<InstanceType<typeof AiTrailLauncher> | null>(null)

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
  open.value = true
}

function closeDrawer() {
  open.value = false
}

function stringParam(value: unknown) {
  return typeof value === 'string' ? value : undefined
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
  width: min(520px, calc(100vw - 32px));
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

.ai-drawer :deep(.ai-chat-panel) {
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
}
</style>
