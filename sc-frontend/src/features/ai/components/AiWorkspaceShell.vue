<template>
  <section
      :class="[`is-${props.layout}`, {'with-question-panel': isRightPanelVisible}]"
      class="monolith-ai-shell"
  >
    <AiDrawerConversationRail v-if="isDrawerLayout"/>

    <div class="workspace-main">
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
              :layout="props.layout"
              :show-composer="!isGenerationDialogOpen"
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
    </div>
  </section>
</template>

<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'

import AiChatPanel from '@/features/ai/components/AiChatPanel.vue'
import AiDrawerConversationRail from '@/features/ai/components/AiDrawerConversationRail.vue'
import AiStudioPanel from '@/features/ai/components/AiStudioPanel.vue'
import {useAiStore} from '@/features/ai/stores/ai'
import type {AiAgentMode, ChatMessage, GenerationRequest} from '@/features/ai/types/ai'
import {isGenerationMessage} from '@/features/ai/utils/generationTrace'

const props = withDefaults(defineProps<{
  layout?: 'page' | 'drawer'
}>(), {
  layout: 'page',
})

const aiStore = useAiStore()
const {t} = useI18n()
const MOBILE_PREVIEW_PANEL_QUERY = '(max-width: 900px)'
const chatMode = ref<AiAgentMode>('CHAT')
const generation = ref<GenerationRequest>(createGenerationDefaults('QUESTION'))
const artifactTab = ref<ArtifactTab>('single')
const panelDismissed = ref(false)
const isMobilePreviewPanel = ref(matchesMobilePreviewPanel())
const requestedPreviewMessageId = ref<string | null>(null)

let mobilePreviewPanelQuery: MediaQueryList | null = null

const isDrawerLayout = computed(() => props.layout === 'drawer')
const activeTitle = computed(() => aiStore.activeConversation?.title || t('common.ai.workspace.newInquiry'))
const latestGeneratedArtifact = computed(() =>
    [...aiStore.messages].reverse().find(message => isGenerationMessage(message)) || null,
)
const activePanelMessage = computed(() => {
  const message = aiStore.activeGenerationMessage
  if (!message || !isMobilePreviewPanel.value || requestedPreviewMessageId.value === message.id) return message

  return null
})
const visibleArtifact = computed(() => activePanelMessage.value || latestGeneratedArtifact.value || aiStore.latestArtifact)
const completedArtifact = computed(() =>
    visibleArtifact.value && !visibleArtifact.value.pending && !visibleArtifact.value.failed && !visibleArtifact.value.terminated,
)
const isRequestedPreviewArtifact = computed(() => Boolean(
    visibleArtifact.value?.id
    && requestedPreviewMessageId.value === visibleArtifact.value.id,
))
const isQuestionPanelVisible = computed(() =>
    chatMode.value !== 'CHAT'
    || Boolean(activePanelMessage.value)
    || (Boolean(completedArtifact.value) && !panelDismissed.value && (!isMobilePreviewPanel.value || isRequestedPreviewArtifact.value)),
)
const isGenerationDialogOpen = computed(() =>
    chatMode.value === 'QUESTION' || chatMode.value === 'PAPER',
)
const isRightPanelVisible = computed(() =>
    isQuestionPanelVisible.value || Boolean(activePanelMessage.value),
)
const toolPanelMode = computed(() =>
    chatMode.value === 'CHAT' ? undefined : chatMode.value,
)
const isPreviewPanelVisible = computed(() =>
    !toolPanelMode.value
    && Boolean(visibleArtifact.value)
    && !panelDismissed.value,
)

onMounted(() => {
  if (typeof window.matchMedia !== 'function') return

  mobilePreviewPanelQuery = window.matchMedia(MOBILE_PREVIEW_PANEL_QUERY)
  syncMobilePreviewPanel(mobilePreviewPanelQuery)
  mobilePreviewPanelQuery.addEventListener('change', syncMobilePreviewPanel)
})

onBeforeUnmount(() => {
  mobilePreviewPanelQuery?.removeEventListener('change', syncMobilePreviewPanel)
})

watch(chatMode, (mode) => {
  if (mode === 'QUESTION' || mode === 'PAPER') {
    requestedPreviewMessageId.value = null
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
    panelDismissed.value = isMobilePreviewPanel.value && requestedPreviewMessageId.value !== messageId
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
  requestedPreviewMessageId.value = null
  panelDismissed.value = mode === 'CHAT'
  artifactTab.value = 'single'
  aiStore.closeGenerationTrace()
  chatMode.value = mode
}

function activatePreviewPanel() {
  requestedPreviewMessageId.value = visibleArtifact.value?.id || null
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
  requestedPreviewMessageId.value = null
  artifactTab.value = 'single'
  aiStore.closeGenerationTrace()
}

function openArtifactPanel(message: ChatMessage, options: { manual?: boolean } = {}) {
  if (options.manual) {
    requestedPreviewMessageId.value = message.id
  }

  const shouldOpen = !isMobilePreviewPanel.value || requestedPreviewMessageId.value === message.id
  panelDismissed.value = !shouldOpen
  artifactTab.value = message.pending || message.failed || message.terminated ? 'trace' : 'single'
  if (!shouldOpen) return

  aiStore.openGenerationTrace(message.id)
}

function closeTools() {
  panelDismissed.value = true
  requestedPreviewMessageId.value = null
  artifactTab.value = 'single'
  chatMode.value = 'CHAT'
  aiStore.closeGenerationTrace()
}

function openGenerationPanel(message: ChatMessage) {
  openArtifactPanel(message, {manual: true})
}

function matchesMobilePreviewPanel() {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return false

  return window.matchMedia(MOBILE_PREVIEW_PANEL_QUERY).matches
}

function syncMobilePreviewPanel(event: MediaQueryList | MediaQueryListEvent) {
  isMobilePreviewPanel.value = event.matches
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

defineExpose({
  setChatMode,
})

type ArtifactTab = 'single' | 'overall' | 'trace'
</script>

<style scoped>
.monolith-ai-shell {
  display: flex;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  background: var(--color-surface-card);
  border: 0;
  color: var(--color-on-surface);
}

.monolith-ai-shell.is-page {
  height: 100dvh;
}

.monolith-ai-shell.is-drawer {
  height: 100%;
  flex-direction: row;
}

.workspace-main {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
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

.monolith-ai-shell.is-drawer .workspace-content {
  position: relative;
}

.monolith-ai-shell.is-drawer .workspace-topbar {
  min-height: 44px;
}

.monolith-ai-shell.is-drawer .thread-label {
  padding: 0 16px;
}

.monolith-ai-shell.is-drawer .workspace-tabs button {
  min-width: 86px;
  padding: 0 12px;
}

.monolith-ai-shell.is-drawer .question-panel-wrapper {
  position: absolute;
  inset: 0;
  z-index: 5;
  height: 100%;
  max-width: none;
  background: var(--color-surface-card);
}

.monolith-ai-shell.is-drawer .question-panel-wrapper :deep(.ai-studio-panel) {
  border-width: 0;
}

.monolith-ai-shell.is-drawer.with-question-panel .chat-main-column {
  flex-basis: 100%;
  max-width: none;
}

.monolith-ai-shell.is-drawer .question-panel-wrapper.is-visible {
  flex-basis: 100%;
  max-width: none;
}

.monolith-ai-shell.is-drawer .question-panel-wrapper :deep(.studio-header.has-artifact-tabs) {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 48px;
  align-items: stretch;
  min-height: 0;
  padding: 0;
}

.monolith-ai-shell.is-drawer .question-panel-wrapper :deep(.artifact-toolbar) {
  width: 100%;
  max-width: none;
  grid-template-columns: 1fr;
}

.monolith-ai-shell.is-drawer .question-panel-wrapper :deep(.artifact-toolbar.has-answer-toggle),
.monolith-ai-shell.is-drawer .question-panel-wrapper :deep(.artifact-toolbar.has-question-stepper),
.monolith-ai-shell.is-drawer .question-panel-wrapper :deep(.artifact-toolbar.has-export-actions),
.monolith-ai-shell.is-drawer .question-panel-wrapper :deep(.artifact-toolbar.has-question-stepper.has-export-actions) {
  width: 100%;
  grid-template-columns: minmax(96px, 1fr) minmax(104px, 1fr) minmax(132px, auto);
}

.monolith-ai-shell.is-drawer .question-panel-wrapper :deep(.artifact-tabs) {
  display: flex;
  min-width: 0;
  min-height: 44px;
  overflow-x: auto;
  border-width: 0 0 1px;
  scrollbar-width: none;
}

.monolith-ai-shell.is-drawer .question-panel-wrapper :deep(.artifact-tabs::-webkit-scrollbar) {
  display: none;
}

.monolith-ai-shell.is-drawer .question-panel-wrapper :deep(.artifact-tabs button) {
  flex: 0 0 auto;
  min-width: 76px;
  padding: 0 12px;
  white-space: nowrap;
}

.monolith-ai-shell.is-drawer .question-panel-wrapper :deep(.artifact-toolbar.has-answer-toggle .artifact-tabs),
.monolith-ai-shell.is-drawer .question-panel-wrapper :deep(.artifact-toolbar.has-question-stepper .artifact-tabs),
.monolith-ai-shell.is-drawer .question-panel-wrapper :deep(.artifact-toolbar.has-export-actions .artifact-tabs) {
  grid-column: 1 / -1;
  border-right: 0;
}

.monolith-ai-shell.is-drawer .question-panel-wrapper :deep(.answer-toggle),
.monolith-ai-shell.is-drawer .question-panel-wrapper :deep(.question-stepper),
.monolith-ai-shell.is-drawer .question-panel-wrapper :deep(.export-actions) {
  min-height: 44px;
  border-top: 0;
  border-left: 0;
}

.monolith-ai-shell.is-drawer .question-panel-wrapper :deep(.answer-toggle) {
  grid-column: 1;
  min-width: 96px;
}

.monolith-ai-shell.is-drawer .question-panel-wrapper :deep(.question-stepper) {
  grid-column: 2;
  grid-template-columns: 40px minmax(44px, 1fr) 40px;
  min-width: 104px;
}

.monolith-ai-shell.is-drawer .question-panel-wrapper :deep(.export-actions) {
  grid-column: 3;
  grid-template-columns: repeat(3, minmax(44px, auto));
  min-width: 132px;
}

.monolith-ai-shell.is-drawer .question-panel-wrapper :deep(.answer-toggle button),
.monolith-ai-shell.is-drawer .question-panel-wrapper :deep(.question-stepper button),
.monolith-ai-shell.is-drawer .question-panel-wrapper :deep(.question-stepper span),
.monolith-ai-shell.is-drawer .question-panel-wrapper :deep(.export-actions button) {
  padding: 0 9px;
  font-size: 11px;
  line-height: 1.15;
  white-space: nowrap;
}

.monolith-ai-shell.is-drawer .question-panel-wrapper :deep(.studio-header .btn-close) {
  width: 48px;
  height: auto;
}

@media (max-width: 980px) {
  .monolith-ai-shell.is-drawer.with-question-panel .chat-main-column,
  .monolith-ai-shell.is-drawer .chat-main-column {
    flex-basis: 100%;
    max-width: none;
  }

  .monolith-ai-shell.is-drawer .question-panel-wrapper.is-visible {
    position: absolute;
    inset: 0;
    z-index: 5;
    height: auto;
    max-width: none;
    background: var(--color-surface-card);
  }
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

  .monolith-ai-shell.is-drawer .question-panel-wrapper.is-visible {
    inset: 0;
  }
}

@media (max-width: 760px) {
  .monolith-ai-shell.is-drawer {
    flex-direction: column;
  }
}

@media (max-width: 560px) {
  .monolith-ai-shell.is-drawer .question-panel-wrapper :deep(.artifact-toolbar.has-answer-toggle),
  .monolith-ai-shell.is-drawer .question-panel-wrapper :deep(.artifact-toolbar.has-question-stepper),
  .monolith-ai-shell.is-drawer .question-panel-wrapper :deep(.artifact-toolbar.has-export-actions),
  .monolith-ai-shell.is-drawer .question-panel-wrapper :deep(.artifact-toolbar.has-question-stepper.has-export-actions) {
    grid-template-columns: 1fr;
  }

  .monolith-ai-shell.is-drawer .question-panel-wrapper :deep(.answer-toggle),
  .monolith-ai-shell.is-drawer .question-panel-wrapper :deep(.question-stepper),
  .monolith-ai-shell.is-drawer .question-panel-wrapper :deep(.export-actions) {
    grid-column: 1;
  }
}

@media (max-width: 640px) {
  .monolith-ai-shell {
    height: 100dvh;
    min-height: 560px;
  }

  .monolith-ai-shell.is-drawer {
    min-height: 0;
  }

  .thread-label {
    padding: 0 16px;
  }
}
</style>
