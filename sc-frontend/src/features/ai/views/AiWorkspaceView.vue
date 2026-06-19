<template>
  <main class="monolith-ai-page">
    <section
      :class="{'with-question-panel': isQuestionPanelVisible}"
      class="monolith-ai-shell"
    >
      <header class="workspace-topbar">
        <div class="thread-label">
          <strong>{{ activeTitle }}</strong>
        </div>

        <nav
          :aria-label="t('common.ai.workspace.modesAria')"
          class="workspace-tabs"
        >
          <button
            :class="{active: chatMode === 'CHAT'}"
            type="button"
            @click="setChatMode('CHAT')"
          >
            {{ t('common.ai.workspace.ask') }}
          </button>
          <button
            :class="{active: chatMode === 'QUESTION'}"
            type="button"
            @click="setChatMode('QUESTION')"
          >
            {{ t('common.ai.workspace.questionSet') }}
          </button>
          <button
            :class="{active: chatMode === 'PAPER'}"
            type="button"
            @click="setChatMode('PAPER')"
          >
            {{ t('common.ai.workspace.paper') }}
          </button>
        </nav>
      </header>

      <div class="workspace-content">
        <div class="chat-main-column">
          <AiChatPanel
            v-model="chatMode"
            :generation="generation"
            :show-composer="!isQuestionPanelVisible"
            :show-header="false"
            :title="t('common.ai.workspace.title')"
          />
        </div>

        <div
          :class="{'is-visible': isQuestionPanelVisible}"
          class="question-panel-wrapper"
        >
          <AiStudioPanel
            v-model:generation="generation"
            :mode="toolPanelMode"
            @close="closeTools"
            @generate="generateFromPanel"
          />
        </div>
      </div>
    </section>
  </main>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'

import AiChatPanel from '@/features/ai/components/AiChatPanel.vue'
import AiStudioPanel from '@/features/ai/components/AiStudioPanel.vue'
import {useAiStore} from '@/features/ai/stores/ai'
import type {AiAgentMode, GenerationRequest} from '@/features/ai/types/ai'

const route = useRoute()
const aiStore = useAiStore()
const {t} = useI18n()
const chatMode = ref<AiAgentMode>('CHAT')
const generation = ref<GenerationRequest>(createGenerationDefaults('QUESTION'))

const activeTitle = computed(() => aiStore.activeConversation?.title || t('common.ai.workspace.newInquiry'))
const isQuestionPanelVisible = computed(() =>
  chatMode.value !== 'CHAT' || Boolean(aiStore.latestArtifact),
)

const toolPanelMode = computed(() =>
  chatMode.value === 'CHAT' ? undefined : chatMode.value,
)

onMounted(() => {
  aiStore.setContext({sourceRoute: route.fullPath})
})

watch(chatMode, (mode) => {
  if (mode === 'QUESTION' || mode === 'PAPER') {
    generation.value = {
      ...createGenerationDefaults(mode),
      requirement: generation.value.requirement,
    }
  }
})

function setChatMode(mode: AiAgentMode) {
  chatMode.value = mode
}

function closeTools() {
  chatMode.value = 'CHAT'
}

async function generateFromPanel() {
  if (chatMode.value !== 'QUESTION' && chatMode.value !== 'PAPER') return

  const message = generation.value.requirement?.trim()
    || (chatMode.value === 'PAPER'
      ? t('common.ai.workspace.defaultPaperPrompt')
      : t('common.ai.workspace.defaultQuestionPrompt'))
  await aiStore.sendMessage(message, {
    agentMode: chatMode.value,
    courseId: aiStore.context.courseId,
    generation: normalizeGeneration(generation.value),
  })
}

function createGenerationDefaults(mode: Exclude<AiAgentMode, 'CHAT'>): GenerationRequest {
  return {
    questionCount: mode === 'PAPER' ? 10 : 5,
    questionType: mode === 'PAPER' ? 5 : 0,
    difficulty: 2,
    scorePerQuestion: null,
    totalScore: mode === 'PAPER' ? 100 : null,
    totalEstimatedTime: mode === 'PAPER' ? 60 : null,
    paperName: '',
    paperType: '',
    requirement: '',
    knowledgePoints: null,
    abilityGoals: null,
  }
}

function normalizeGeneration(value: GenerationRequest): GenerationRequest {
  return {
    ...value,
    questionCount: Math.max(1, Math.min(50, Number(value.questionCount) || 1)),
    paperName: value.paperName?.trim() || null,
    paperType: value.paperType?.trim() || null,
    requirement: value.requirement?.trim() || null,
  }
}
</script>

<style scoped>
.monolith-ai-page {
  min-height: 100dvh;
}

.monolith-ai-shell {
  display: flex;
  height: 100dvh;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  background: var(--color-surface-card);
  border: 0;
  color: var(--color-on-surface);
}

.workspace-topbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  min-height: 48px;
  border-bottom: 1px solid var(--color-outline-light);
}

.thread-label {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
  padding: 0 22px;
}

.workspace-tabs button {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.thread-label strong {
  min-width: 0;
  overflow: hidden;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-tabs {
  display: inline-flex;
  height: 100%;
  border-left: 1px solid var(--color-outline-light);
}

.workspace-tabs button {
  min-width: 112px;
  padding: 0 18px;
  background: transparent;
  border: 0;
  border-right: 1px solid var(--color-outline-light);
  color: var(--color-muted);
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}

.workspace-tabs button:hover,
.workspace-tabs button.active {
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.workspace-content {
  display: flex;
  min-height: 0;
  flex: 1;
  overflow: hidden;
}

.chat-main-column {
  display: flex;
  min-width: 0;
  flex: 0 0 auto;
  flex-basis: 100%;
  max-width: 100%;
  flex-direction: column;
  overflow: hidden;
  transition: flex-basis 0.35s ease, max-width 0.35s ease;
}

.chat-main-column :deep(.ai-chat-panel) {
  border: 0;
  background: transparent;
}

.question-panel-wrapper {
  display: flex;
  height: 100%;
  min-width: 0;
  flex: 0 0 auto;
  flex-basis: 0%;
  max-width: 0%;
  overflow: hidden;
  opacity: 0;
  pointer-events: none;
  transform: translateX(24px);
  transition: flex-basis 0.35s ease, max-width 0.35s ease, opacity 0.3s ease, transform 0.35s ease;
}

.question-panel-wrapper.is-visible {
  flex-basis: 62%;
  max-width: 62%;
  opacity: 1;
  transform: none;
  pointer-events: auto;
}

.question-panel-wrapper :deep(.ai-studio-panel) {
  width: 100%;
  height: 100%;
  border-width: 0 0 0 1px;
}

.monolith-ai-shell.with-question-panel .chat-main-column {
  flex-basis: 38%;
  max-width: 38%;
}

@media (max-width: 900px) {
  .workspace-topbar {
    grid-template-columns: 1fr;
  }

  .workspace-tabs {
    width: 100%;
    border-top: 1px solid var(--color-outline-light);
    border-left: 0;
  }

  .workspace-tabs button {
    flex: 1;
    min-width: 0;
    min-height: 42px;
  }

  .monolith-ai-shell.with-question-panel .chat-main-column,
  .chat-main-column {
    flex-basis: 100%;
    max-width: none;
  }

  .question-panel-wrapper.is-visible {
    position: absolute;
    inset: 94px 0 0;
    z-index: 5;
    max-width: none;
    background: var(--color-surface-card);
  }
}

@media (max-width: 640px) {
  .monolith-ai-shell {
    height: 100dvh;
    min-height: 560px;
  }

  .thread-label {
    padding: 0 16px;
  }
}
</style>
