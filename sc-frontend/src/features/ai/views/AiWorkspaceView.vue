<template>
  <main class="monolith-ai-page">
    <section
      :class="{'with-question-panel': isRightPanelVisible}"
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
            v-if="isPreviewPanelVisible"
            class="workspace-preview-tab active"
            type="button"
            @click="activatePreviewPanel"
          >
            {{ t('common.ai.workspace.preview') }}
          </button>
          <button
            :class="{active: chatMode === 'CHAT' && !isPreviewPanelVisible}"
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
            @view-generation="openGenerationPanel"
          />
        </div>

        <div
          :class="{'is-visible': isRightPanelVisible}"
          class="question-panel-wrapper"
        >
          <AiStudioPanel
            v-model:artifact-tab="artifactTab"
            v-model:generation="generation"
            :mode="toolPanelMode"
            :trace-message="aiStore.activeGenerationMessage"
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
import type {AiAgentMode, ChatMessage, GenerationRequest} from '@/features/ai/types/ai'
import {isGenerationMessage} from '@/features/ai/utils/generationTrace'

const route = useRoute()
const aiStore = useAiStore()
const {t} = useI18n()
const chatMode = ref<AiAgentMode>('CHAT')
const generation = ref<GenerationRequest>(createGenerationDefaults('QUESTION'))
const artifactTab = ref<ArtifactTab>('single')
const panelDismissed = ref(false)
let loadingRouteConversationId = ''

const activeTitle = computed(() => aiStore.activeConversation?.title || t('common.ai.workspace.newInquiry'))
const latestGeneratedArtifact = computed(() =>
  [...aiStore.messages].reverse().find(message => isGenerationMessage(message)) || null,
)
const visibleArtifact = computed(() => aiStore.activeGenerationMessage || latestGeneratedArtifact.value || aiStore.latestArtifact)
const completedArtifact = computed(() =>
  visibleArtifact.value && !visibleArtifact.value.pending && !visibleArtifact.value.failed && !visibleArtifact.value.terminated,
)
const isQuestionPanelVisible = computed(() =>
  chatMode.value !== 'CHAT'
  || Boolean(aiStore.activeGenerationMessage)
  || (Boolean(completedArtifact.value) && !panelDismissed.value),
)
const isRightPanelVisible = computed(() =>
  isQuestionPanelVisible.value || Boolean(aiStore.activeGenerationMessage),
)
const toolPanelMode = computed(() =>
  chatMode.value === 'CHAT' ? undefined : chatMode.value,
)
const isPreviewPanelVisible = computed(() =>
  !toolPanelMode.value
  && Boolean(visibleArtifact.value)
  && !panelDismissed.value
)

onMounted(() => {
  aiStore.setContext({sourceRoute: route.fullPath})
  syncModeFromRoute()
})

watch(
  () => route.fullPath,
  () => aiStore.setContext({sourceRoute: route.fullPath}),
)

watch(
  () => route.query.mode,
  () => syncModeFromRoute(),
)

watch(
  () => route.query.conversationId,
  () => {
    void loadConversationFromRoute()
  },
  {immediate: true},
)

watch(chatMode, (mode) => {
  if (mode === 'QUESTION' || mode === 'PAPER') {
    panelDismissed.value = false
    artifactTab.value = 'single'
    generation.value = {
      ...createGenerationDefaults(mode),
      requirement: generation.value.requirement,
    }
  }
})

watch(() => aiStore.activeGenerationMessageId, (messageId) => {
  if (messageId) {
    const message = aiStore.activeGenerationMessage
    panelDismissed.value = false
    artifactTab.value = message && !message.pending && !message.failed && !message.terminated ? 'single' : 'trace'
  }
})

watch(() => aiStore.loadingMessages, (loading, wasLoading) => {
  if (!loading && (wasLoading || aiStore.messages.length > 0)) {
    syncDefaultGenerationPanel()
  }
}, {immediate: true})

watch(() => [
  latestGeneratedArtifact.value?.id,
  latestGeneratedArtifact.value?.pending,
  latestGeneratedArtifact.value?.failed,
], ([artifactId]) => {
  const artifact = latestGeneratedArtifact.value
  if (artifactId && artifact) {
    openArtifactPanel(artifact)
  }
})

function setChatMode(mode: AiAgentMode) {
  panelDismissed.value = mode === 'CHAT'
  artifactTab.value = 'single'
  aiStore.closeGenerationTrace()
  chatMode.value = mode
}

function syncModeFromRoute() {
  const mode = normalizeRouteMode(route.query.mode)
  if (mode) setChatMode(mode)
}

async function loadConversationFromRoute() {
  const conversationId = normalizeRouteText(route.query.conversationId)
  if (!conversationId || conversationId === aiStore.activeConversationId || conversationId === loadingRouteConversationId) {
    return
  }

  loadingRouteConversationId = conversationId
  try {
    await aiStore.loadMessages(conversationId)
  } catch {
    // Request handling already shows the load failure notification.
  } finally {
    if (loadingRouteConversationId === conversationId) {
      loadingRouteConversationId = ''
    }
  }
}

function normalizeRouteMode(value: unknown): Exclude<AiAgentMode, 'CHAT'> | null {
  const mode = normalizeRouteText(value)
  return mode === 'QUESTION' || mode === 'PAPER' ? mode : null
}

function normalizeRouteText(value: unknown) {
  const text = Array.isArray(value) ? value[0] : value
  return typeof text === 'string' ? text.trim() : ''
}

function activatePreviewPanel() {
  panelDismissed.value = false
  chatMode.value = 'CHAT'
}

function syncDefaultGenerationPanel() {
  const artifact = latestGeneratedArtifact.value
  if (artifact) {
    openArtifactPanel(artifact)
    return
  }

  panelDismissed.value = true
  artifactTab.value = 'single'
  aiStore.closeGenerationTrace()
}

function openArtifactPanel(message: ChatMessage) {
  panelDismissed.value = false
  artifactTab.value = message.pending || message.failed || message.terminated ? 'trace' : 'single'
  aiStore.openGenerationTrace(message.id)
}

function closeTools() {
  panelDismissed.value = true
  artifactTab.value = 'single'
  chatMode.value = 'CHAT'
  aiStore.closeGenerationTrace()
}

function openGenerationPanel(message: ChatMessage) {
  openArtifactPanel(message)
}

async function generateFromPanel() {
  if (chatMode.value !== 'QUESTION' && chatMode.value !== 'PAPER') return

  const mode = chatMode.value
  const generationRequest = {
    ...normalizeGeneration(generation.value),
    questionBankId: generation.value.questionBankId || aiStore.context.questionBankId || null,
  }
  const message = generationRequest.requirement
    || (mode === 'PAPER'
      ? t('common.ai.workspace.defaultPaperPrompt')
      : t('common.ai.workspace.defaultQuestionPrompt'))
  closeTools()
  await aiStore.sendMessage(message, {
    agentMode: mode,
    courseId: aiStore.context.courseId,
    generation: generationRequest,
  })
}

function createGenerationDefaults(mode: Exclude<AiAgentMode, 'CHAT'>): GenerationRequest {
  return {
    questionCount: mode === 'PAPER' ? 10 : 5,
    questionType: 5,
    difficulty: 0,
    scorePerQuestion: 0,
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

type ArtifactTab = 'single' | 'overall' | 'trace'
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
  --question-panel-width: 60%;

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
  height: calc(100% - 16px);
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
  flex-basis: var(--question-panel-width);
  max-width: var(--question-panel-width);
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
  flex-basis: calc(100% - var(--question-panel-width));
  max-width: calc(100% - var(--question-panel-width));
}

.monolith-ai-shell.with-question-panel .chat-main-column :deep(.message-list) {
  padding-right: 2.5%;
  padding-left: 2.5%;
}

.monolith-ai-shell.with-question-panel .chat-main-column :deep(.composer) {
  padding-right: 2.5%;
  padding-left: 2.5%;
}

.monolith-ai-shell.with-question-panel .chat-main-column :deep(.message-row) {
  width: 100%;
}

.monolith-ai-shell.with-question-panel .chat-main-column :deep(.message-row.has-generation-card .generation-card) {
  width: 100%;
}

.monolith-ai-shell.with-question-panel .chat-main-column :deep(.state-block) {
  width: 100%;
}

.monolith-ai-shell.with-question-panel .chat-main-column :deep(.composer-shell) {
  width: 100%;
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
    height: auto;
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
