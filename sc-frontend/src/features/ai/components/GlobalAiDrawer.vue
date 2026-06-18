<template>
  <Teleport to="body">
    <button
      v-if="!open"
      class="ai-launcher"
      title="打开 AI 教学助手"
      type="button"
      @click="openDrawer"
    >
      <Sparkles
        :size="20"
        stroke-width="1.8"
      />
      <span>AI</span>
    </button>

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
        <AiChatSurface
          :show-header="false"
          title="全局助手"
        />
      </aside>
    </div>
  </Teleport>
</template>

<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useRoute} from 'vue-router'
import {Sparkles, X} from 'lucide-vue-next'

import AiChatSurface from '@/features/ai/components/AiChatSurface.vue'
import {useAiStore} from '@/features/ai/stores/ai'

const route = useRoute()
const aiStore = useAiStore()
const open = ref(false)

const routeContext = computed(() => {
  const params = route.params
  const routeName = typeof route.name === 'string' ? route.name : ''
  const questionBankId = routeName === 'question-bank-detail' || routeName === 'question-bank-practice'
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
</script>

<style scoped>
.ai-launcher {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 1900;
  display: inline-flex;
  min-height: 44px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 0 14px;
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-sm);
  color: var(--color-on-primary);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 13px;
  box-shadow: 0 14px 32px rgba(15, 23, 42, 0.22);
}

.drawer-layer {
  position: fixed;
  inset: 0;
  z-index: 2300;
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
  width: min(440px, calc(100vw - 32px));
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
  letter-spacing: 0.06em;
  text-transform: uppercase;
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

@media (max-width: 640px) {
  .ai-launcher {
    right: 16px;
    bottom: 16px;
  }

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
