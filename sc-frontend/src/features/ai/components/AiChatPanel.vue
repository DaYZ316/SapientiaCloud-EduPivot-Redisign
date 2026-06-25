<template>
  <section
    :class="{'is-empty': isEmpty}"
    class="ai-chat-panel"
  >
    <header
      v-if="showHeader"
      class="chat-header"
    >
      <div>
        <span>{{ t('common.ai.chat.header') }}</span>
        <h2>{{ title }}</h2>
      </div>
      <button
        :title="t('common.ai.sidebar.newChat')"
        class="btn-close chat-new-button"
        type="button"
        @click="createNewConversation"
      >
        <Plus
          :size="17"
          stroke-width="1.8"
        />
      </button>
    </header>

    <div
      ref="messageListRef"
      class="message-list"
    >
      <div
        v-if="aiStore.loadingMessages"
        :aria-label="t('common.ai.history.loading')"
        class="state-block"
        role="status"
      >
        <div class="loading-track">
          <div class="loading-message-card loading-message-card--user loading-message-card--prompt">
            <span class="loading-user-bar shimmer" />
          </div>
          <div class="loading-message-card loading-message-card--assistant">
            <span class="loading-avatar shimmer" />
            <div class="loading-copy">
              <span class="loading-line loading-line--title shimmer" />
              <span class="loading-line shimmer" />
              <span class="loading-line loading-line--medium shimmer" />
            </div>
          </div>
          <div class="loading-message-card loading-message-card--user">
            <div class="loading-copy">
              <span class="loading-line loading-line--medium shimmer" />
              <span class="loading-line loading-line--short shimmer" />
            </div>
          </div>
          <div class="loading-message-card loading-message-card--assistant loading-message-card--compact">
            <span class="loading-avatar shimmer" />
            <div class="loading-copy">
              <span class="loading-line shimmer" />
              <span class="loading-line loading-line--short shimmer" />
            </div>
          </div>
        </div>
      </div>

      <template v-else-if="aiStore.messages.length > 0">
        <article
          v-for="message in aiStore.messages"
          :key="message.id"
          :class="[messageClass(message.role), {
            failed: message.failed,
            streaming: message.pending && !message.failed,
            'has-generation-card': isGenerationMessage(message),
            'has-generation-request': isGenerationRequestMessage(message),
          }]"
          class="message-row"
        >
          <div
            v-if="message.messageType && message.messageType !== 'TEXT' && !isGenerationMessage(message)"
            class="message-meta"
          >
            <small>
              {{ messageTypeLabel(message.messageType) }}
            </small>
          </div>
          <div class="message-bubble">
            <AiGenerationProgressCard
              v-if="isGenerationMessage(message)"
              :active="aiStore.activeGenerationMessageId === message.id"
              :message="message"
              @view-trace="openGenerationTrace"
            />
            <AiPendingBrushLoader
              v-else-if="message.pending && isAssistantMessage(message.role) && !message.content"
              class="pending-brush"
            />
            <AiGenerationRequestCard
              v-else-if="isGenerationRequestMessage(message)"
              :message="message"
            />
            <div
              v-if="message.content && !isGenerationMessage(message) && !isGenerationRequestMessage(message)"
              class="message-content"
            >
              <AiMarkdownMessage
                v-if="isAssistantMessage(message.role)"
                :content="message.content"
              />
              <template v-else>
                {{ message.content }}
              </template>
            </div>
            <AiAgentSearchEvidence
              v-if="!isGenerationMessage(message)"
              :payload="agentSearchPayload(message)"
            />
          </div>
        </article>
      </template>

      <div
        v-else
        class="empty-chat"
      >
        <h3>{{ emptyTitle }}</h3>
      </div>
    </div>

    <div
      v-if="aiStore.streamError"
      class="inline-error"
    >
      {{ aiStore.streamError }}
    </div>

    <form
      v-if="showComposer"
      class="composer"
      @submit.prevent="submit"
    >
      <div class="composer-shell">
        <div
          aria-hidden="true"
          class="composer-brand"
        >
          <span>SC</span>
        </div>

        <textarea
          ref="composerTextareaRef"
          v-model="draft"
          :disabled="aiStore.streaming"
          :placeholder="placeholder"
          maxlength="4000"
          rows="1"
          @input="resizeComposer"
          @keydown.enter.exact.prevent="submit"
        />

        <div class="tools-right">
          <button
            v-if="canStopTask"
            class="btn-secondary secondary-action"
            :title="t('common.ai.chat.stopGenerating')"
            type="button"
            @click="aiStore.stopStreaming"
          >
            <Square
              :size="14"
              stroke-width="1.9"
            />
          </button>
          <button
            v-if="draft.trim() && !isListening"
            :disabled="aiStore.streaming"
            class="btn-primary primary-action"
            :title="t('common.ai.chat.send')"
            type="submit"
          >
            <Send
              :size="15"
              stroke-width="1.9"
            />
          </button>
          <button
            v-else
            :aria-label="speechButtonLabel"
            :aria-pressed="isListening"
            :class="{'is-listening': isListening}"
            :disabled="aiStore.streaming"
            class="btn-primary primary-action voice-action"
            :title="speechButtonLabel"
            type="button"
            @click="toggleSpeechInput"
          >
            <Mic
              :size="21"
              stroke-width="2.2"
            />
          </button>
        </div>
      </div>
    </form>
    <p
      v-if="isEmpty && showComposer"
      class="composer-disclaimer"
    >
      {{ t('common.ai.chat.disclaimer') }}
    </p>
  </section>
</template>

<script lang="ts" setup>
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {Mic, Plus, Send, Square} from 'lucide-vue-next'

import {useAiStore} from '@/features/ai/stores/ai'
import {useAuthStore} from '@/features/auth/stores/auth'
import AiAgentSearchEvidence from '@/features/ai/components/AiAgentSearchEvidence.vue'
import AiGenerationProgressCard from '@/features/ai/components/AiGenerationProgressCard.vue'
import AiGenerationRequestCard from '@/features/ai/components/AiGenerationRequestCard.vue'
import AiMarkdownMessage from '@/features/ai/components/AiMarkdownMessage.vue'
import AiPendingBrushLoader from '@/features/ai/components/AiPendingBrushLoader.vue'
import type {AgentSearchPayload, AiAgentMode, AiMessageRole, ChatMessage, GenerationRequest} from '@/features/ai/types/ai'
import {hasGenerationRequestPayload} from '@/features/ai/utils/generationRequestPayload'
import {isGenerationMessage} from '@/features/ai/utils/generationTrace'
import {notify} from '@/shared/composables/useGlobalNotification'

const props = withDefaults(defineProps<{
  title?: string
  showHeader?: boolean
  showComposer?: boolean
  modelValue?: AiAgentMode
  generation?: GenerationRequest
}>(), {
  title: '天枢教学助手',
  showHeader: true,
  showComposer: true,
  modelValue: undefined,
  generation: undefined,
})

const emit = defineEmits<{
  'view-generation': [message: ChatMessage]
}>()

const aiStore = useAiStore()
const authStore = useAuthStore()
const {locale, t} = useI18n()
const draft = ref('')
const internalMode = ref<AiAgentMode>('CHAT')
const messageListRef = ref<HTMLElement | null>(null)
const composerTextareaRef = ref<HTMLTextAreaElement | null>(null)
const isListening = ref(false)
const activeMode = computed(() => props.modelValue ?? internalMode.value)
const isEmpty = computed(() => !aiStore.loadingMessages && aiStore.messages.length === 0)
const hasConversationHistory = computed(() => aiStore.conversations.length > 0)
const userDisplayName = computed(() => authStore.user?.displayName || authStore.user?.email || 'User')
const streamFingerprint = computed(() =>
  aiStore.messages.map((message) => `${message.id}:${message.content.length}:${message.pending ? '1' : '0'}`).join('|'),
)
const canStopTask = computed(() => aiStore.streaming || Boolean(aiStore.activeGenerationMessage?.pending))
const speechButtonLabel = computed(() =>
  isListening.value ? t('common.ai.chat.stopVoiceInput') : t('common.ai.chat.startVoiceInput'),
)
let scrollFrame = 0
let activeSpeechRecognition: SpeechRecognitionLike | null = null
let speechBaseDraft = ''
let speechFinalTranscript = ''

const emptyTitle = computed(() => {
  if (!hasConversationHistory.value) {
    return t('common.ai.chat.emptyTitle')
  }

  return t(greetingKey(), {name: userDisplayName.value})
})

const placeholder = computed(() => {
  if (activeMode.value === 'QUESTION') return t('common.ai.chat.questionPlaceholder')
  if (activeMode.value === 'PAPER') return t('common.ai.chat.paperPlaceholder')
  return t('common.ai.chat.placeholder')
})

onMounted(async () => {
  try {
    await aiStore.ensureConversationsLoaded()
  } catch {
    notify.error(t('common.ai.notify.loadFailed'))
  }
  await nextTick()
  resizeComposer()
  queueScrollToBottom()
})

watch(() => aiStore.messages.length, async () => {
  await nextTick()
  queueScrollToBottom('smooth')
})

watch(streamFingerprint, async () => {
  await nextTick()
  queueScrollToBottom()
})

watch(draft, async () => {
  await nextTick()
  resizeComposer()
})

onBeforeUnmount(() => {
  if (scrollFrame) {
    cancelAnimationFrame(scrollFrame)
  }
  cancelSpeechInput()
})

async function submit() {
  const message = draft.value.trim()
  if (!message) return
  const generationRequest = activeMode.value === 'CHAT'
    ? undefined
    : normalizedGeneration({
        ...props.generation,
        requirement: message,
      }, activeMode.value)

  cancelSpeechInput()
  draft.value = ''
  await nextTick()
  resizeComposer()
  await aiStore.sendMessage(message, {
    agentMode: activeMode.value,
    courseId: aiStore.context.courseId,
    generation: generationRequest,
  })
}

function toggleSpeechInput() {
  if (isListening.value) {
    stopSpeechInput()
    return
  }

  startSpeechInput()
}

function startSpeechInput() {
  const SpeechRecognitionConstructor = getSpeechRecognitionConstructor()
  if (!SpeechRecognitionConstructor) {
    notify.warn(t('common.ai.chat.voiceUnsupported'))
    return
  }

  cancelSpeechInput()
  speechBaseDraft = draft.value.trim()
  speechFinalTranscript = ''

  const recognition = new SpeechRecognitionConstructor()
  recognition.continuous = true
  recognition.interimResults = true
  recognition.lang = locale.value.startsWith('zh') ? 'zh-CN' : 'en-US'
  recognition.onresult = handleSpeechResult
  recognition.onerror = handleSpeechError
  recognition.onend = () => {
    if (activeSpeechRecognition === recognition) {
      activeSpeechRecognition = null
      isListening.value = false
      void nextTick(resizeComposer)
    }
  }

  activeSpeechRecognition = recognition
  try {
    recognition.start()
    isListening.value = true
  } catch {
    activeSpeechRecognition = null
    isListening.value = false
    notify.warn(t('common.ai.chat.voiceStartFailed'))
  }
}

function stopSpeechInput() {
  if (!activeSpeechRecognition) {
    isListening.value = false
    return
  }

  isListening.value = false
  activeSpeechRecognition.stop()
}

function cancelSpeechInput() {
  if (!activeSpeechRecognition) {
    isListening.value = false
    return
  }

  activeSpeechRecognition.onresult = null
  activeSpeechRecognition.onerror = null
  activeSpeechRecognition.onend = null
  activeSpeechRecognition.abort()
  activeSpeechRecognition = null
  isListening.value = false
}

function handleSpeechResult(event: SpeechRecognitionEventLike) {
  let interimTranscript = ''

  for (let index = event.resultIndex; index < event.results.length; index += 1) {
    const result = event.results[index]
    const transcript = result[0]?.transcript ?? ''
    if (result.isFinal) {
      speechFinalTranscript += transcript
    } else {
      interimTranscript += transcript
    }
  }

  const voiceText = `${speechFinalTranscript}${interimTranscript}`.trim()
  draft.value = [speechBaseDraft, voiceText].filter(Boolean).join(' ')
}

function handleSpeechError(event: SpeechRecognitionErrorEventLike) {
  if (event.error === 'aborted') return

  if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
    notify.warn(t('common.ai.chat.voicePermissionDenied'))
    return
  }

  notify.warn(t(event.error === 'no-speech' ? 'common.ai.chat.voiceNoSpeech' : 'common.ai.chat.voiceStartFailed'))
}

function getSpeechRecognitionConstructor() {
  const speechWindow = window as SpeechRecognitionWindow
  return speechWindow.SpeechRecognition ?? speechWindow.webkitSpeechRecognition
}

function normalizedGeneration(generation?: GenerationRequest, mode: AiAgentMode = 'QUESTION'): GenerationRequest {
  const value = generation ?? {}
  return {
    ...value,
    questionCount: Math.max(1, Math.min(50, Number(value.questionCount) || (mode === 'PAPER' ? 10 : 5))),
    paperName: value.paperName?.trim() || null,
    paperType: value.paperType?.trim() || null,
    requirement: value.requirement?.trim() || null,
  }
}

function createNewConversation() {
  aiStore.openNewConversationDraft()
}

function openGenerationTrace(messageId: string) {
  const message = aiStore.messages.find(item => item.id === messageId)
  if (message) {
    emit('view-generation', message)
  }
}

function queueScrollToBottom(behavior: ScrollBehavior = 'auto') {
  if (scrollFrame) {
    cancelAnimationFrame(scrollFrame)
  }
  scrollFrame = requestAnimationFrame(() => {
    const list = messageListRef.value
    if (!list) return

    list.scrollTo({
      top: list.scrollHeight,
      behavior,
    })
  })
}

function resizeComposer() {
  const textarea = composerTextareaRef.value
  if (!textarea) return

  textarea.style.height = 'auto'
  textarea.style.height = `${Math.min(textarea.scrollHeight, maxComposerTextareaHeight(textarea))}px`
}

function maxComposerTextareaHeight(textarea: HTMLTextAreaElement) {
  const lineHeight = Number.parseFloat(window.getComputedStyle(textarea).lineHeight) || 24
  return Math.ceil(lineHeight * 6)
}

function messageClass(role: AiMessageRole) {
  return isAssistantMessage(role) ? 'from-assistant' : 'from-user'
}

function isAssistantMessage(role: AiMessageRole) {
  return role.toLowerCase() !== 'user'
}

function isGenerationRequestMessage(message: ChatMessage) {
  return !isAssistantMessage(message.role) && hasGenerationRequestPayload(message)
}

function agentSearchPayload(message: ChatMessage): AgentSearchPayload | null {
  const value = message.payload?.agentSearch
  return value && typeof value === 'object' ? value as AgentSearchPayload : null
}

function messageTypeLabel(type: string) {
  if (type === 'QUESTION_SET') return t('common.ai.chat.questionSet')
  if (type === 'PAPER') return t('common.ai.chat.paper')
  if (type === 'GRADING_RESULT') return t('common.ai.chat.grading')
  return type
}

function greetingKey() {
  const hour = new Date().getHours()
  if (hour >= 5 && hour < 11) return 'common.ai.chat.greetingMorning'
  if (hour >= 11 && hour < 14) return 'common.ai.chat.greetingNoon'
  if (hour >= 14 && hour < 18) return 'common.ai.chat.greetingAfternoon'
  return 'common.ai.chat.greetingEvening'
}

type SpeechRecognitionConstructor = new () => SpeechRecognitionLike

interface SpeechRecognitionWindow extends Window {
  SpeechRecognition?: SpeechRecognitionConstructor
  webkitSpeechRecognition?: SpeechRecognitionConstructor
}

interface SpeechRecognitionLike {
  continuous: boolean
  interimResults: boolean
  lang: string
  onend: (() => void) | null
  onerror: ((event: SpeechRecognitionErrorEventLike) => void) | null
  onresult: ((event: SpeechRecognitionEventLike) => void) | null
  abort: () => void
  start: () => void
  stop: () => void
}

interface SpeechRecognitionEventLike {
  resultIndex: number
  results: SpeechRecognitionResultListLike
}

interface SpeechRecognitionResultListLike {
  length: number
  [index: number]: SpeechRecognitionResultLike
}

interface SpeechRecognitionResultLike {
  isFinal: boolean
  [index: number]: {
    transcript: string
  }
}

interface SpeechRecognitionErrorEventLike {
  error: string
}
</script>

<style scoped>
.ai-chat-panel {
  display: flex;
  position: relative;
  min-height: 0;
  height: 100%;
  flex-direction: column;
  overflow: hidden;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  padding: 18px var(--space-md);
  border-bottom: 1px solid var(--color-outline-light);
}

.chat-header span,
.message-meta {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.chat-header h2 {
  margin: 4px 0 0;
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 600;
  line-height: 1.12;
}

.primary-action,
.secondary-action {
  display: grid;
  place-items: center;
  padding: 0;
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.chat-new-button {
  width: 36px;
  height: 36px;
}

.secondary-action:hover {
  border-color: var(--color-primary);
}

.chat-new-button:active,
.primary-action:active,
.secondary-action:active {
  transform: scale(0.97);
}

.message-list {
  display: flex;
  min-height: 0;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  overflow-x: hidden;
  overflow-y: auto;
  padding: var(--space-md) clamp(var(--space-md), 6vw, 120px) var(--space-md);
  scroll-behavior: smooth;
}

.ai-chat-panel.is-empty .message-list {
  justify-content: center;
  padding-bottom: 248px;
}

.message-row {
  display: grid;
  gap: var(--space-xs);
  min-width: 0;
  width: 60%;
  max-width: 100%;
  margin-inline: auto;
  margin-bottom: var(--space-md);
  animation: messageEnter 0.18s ease-out;
}

.message-row.from-user {
  justify-items: end;
}

.message-row.has-generation-card .message-bubble :deep(.generation-card) {
  width: 80%;
}

.message-meta {
  display: inline-flex;
  align-items: center;
  gap: var(--space-sm);
}

.from-user .message-meta {
  justify-content: flex-end;
}

.message-meta small {
  color: var(--color-muted);
  font: inherit;
  letter-spacing: inherit;
}

.message-bubble {
  min-width: 0;
  width: fit-content;
  max-width: 80%;
  padding: 14px var(--space-md);
  background: var(--color-surface-container-lowest);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-lg);
}

.from-assistant .message-bubble {
  width: 100%;
  max-width: 100%;
  border-color: transparent;
  border-radius: 0;
  padding: 0;
  background: transparent;
}

.from-user .message-bubble {
  max-width: 60%;
  padding: 10px 16px;
  background: var(--color-primary);
  border-color: transparent;
  color: var(--color-on-primary);
}

.from-user.has-generation-request {
  justify-items: end;
}

.from-user.has-generation-request .message-bubble {
  width: 80%;
  max-width: 620px;
  padding: 16px;
  background: var(--color-surface-container-lowest);
  border-color: var(--color-outline-light);
  color: var(--color-on-surface);
}

.from-user .message-content {
  font-size: 18px;
  line-height: 1.55;
}

.message-row.failed .message-bubble {
  border-color: var(--color-error);
}

.message-content {
  min-width: 0;
  max-width: 100%;
  margin: 0;
  white-space: pre-wrap;
  color: inherit;
  font-family: var(--font-body);
  font-size: 15px;
  line-height: 1.68;
}

.message-content {
  overflow-x: hidden;
  overflow-wrap: anywhere;
}

.message-content :deep(.ai-markdown-message) {
  width: 100%;
  max-width: 100%;
}

.message-row.streaming .message-content {
  display: inline;
}

.pending-brush {
  margin: 0;
}

.agent-search-disclosure {
  display: grid;
  width: min(100%, 520px);
  margin: 8px 0 4px;
  padding: 0;
}

.agent-search-status,
.agent-search-steps span,
.agent-search-card span,
.agent-search-card small {
  font-family: var(--font-label);
  letter-spacing: 0;
}

.agent-search-toggle {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: fit-content;
  min-height: 28px;
  padding: 0 24px 0 0;
  background: transparent;
  border: 0;
  cursor: pointer;
  color: var(--color-muted);
  list-style: none;
  transition: color 0.18s ease;
  user-select: none;
}

.agent-search-toggle:hover {
  color: var(--color-on-surface);
}

.agent-search-toggle::-webkit-details-marker {
  display: none;
}

.agent-search-status {
  color: var(--color-muted);
  font-size: 15px;
  font-weight: 500;
  line-height: 1.68;
}

.agent-search-toggle-icon {
  position: absolute;
  right: 0;
  flex: 0 0 auto;
  opacity: 0.72;
  transition: transform 0.2s;
}

.agent-search-disclosure[open] .agent-search-toggle-icon {
  transform: rotate(180deg);
}

.agent-search-detail {
  display: grid;
  gap: 8px;
  padding: 8px 0 0;
}

.agent-search-steps {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.agent-search-steps span {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 0;
  background: transparent;
  border: 0;
  color: var(--color-muted);
  font-size: 11px;
  line-height: 1.2;
}

.agent-search-steps .phase-results {
  color: var(--color-primary);
}

.agent-search-steps .phase-empty,
.agent-search-steps .phase-error {
  color: var(--color-error);
}

.agent-search-cards {
  display: grid;
  gap: 8px;
}

.agent-search-card {
  display: grid;
  gap: 3px;
  min-width: 0;
  padding: 0;
  background: transparent;
  border: 0;
}

.agent-search-card span {
  color: var(--color-primary);
  font-size: 11px;
  font-weight: 600;
  line-height: 1.25;
}

.agent-search-card strong {
  min-width: 0;
  overflow: hidden;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 15px;
  font-weight: 600;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-search-card small {
  color: var(--color-muted);
  font-size: 12px;
  line-height: 1.35;
}

.agent-search-card p {
  display: -webkit-box;
  margin: 3px 0 0;
  overflow: hidden;
  color: var(--color-muted);
  font-size: 14px;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.empty-chat {
  display: grid;
  flex: 1;
  place-items: center;
  align-content: center;
  justify-items: center;
  color: var(--color-muted);
  text-align: center;
}

.state-block {
  display: grid;
  width: 60%;
  min-width: 0;
  flex: 0 0 auto;
  gap: 0;
  align-content: start;
  justify-items: stretch;
  margin: clamp(var(--space-md), 8vh, 72px) auto var(--space-lg);
  padding: 0;
  color: var(--color-muted);
  text-align: left;
  animation: messageEnter 0.22s ease-out;
}

.loading-track {
  display: grid;
  position: relative;
  gap: 16px;
  width: 100%;
}

.loading-track::before {
  content: '';
  position: absolute;
  top: 58px;
  bottom: 12px;
  left: 16px;
  width: 1px;
  background: linear-gradient(
    180deg,
    transparent,
    color-mix(in srgb, var(--color-outline-light) 82%, transparent) 16%,
    color-mix(in srgb, var(--color-outline-light) 42%, transparent) 72%,
    transparent
  );
}

.loading-message-card {
  display: grid;
  position: relative;
  width: 100%;
  align-items: start;
  gap: 14px;
  padding: 0;
  background: transparent;
  border: 0;
  border-radius: 0;
}

.loading-message-card--assistant {
  grid-template-columns: 34px minmax(0, 1fr);
  justify-self: start;
}

.loading-message-card--user {
  width: min(60%, 420px);
  justify-self: end;
  padding: 12px 16px;
  background: color-mix(in srgb, var(--color-primary) 90%, var(--color-surface-card));
  border-radius: var(--radius-lg);
}

.loading-message-card--prompt {
  width: min(48%, 360px);
  padding: 0;
  background: transparent;
}

.loading-message-card--compact .loading-copy {
  width: min(72%, 520px);
}

.loading-user-bar {
  display: block;
  width: 100%;
  height: 42px;
  border-radius: var(--radius-lg);
}

.loading-avatar {
  width: 34px;
  height: 34px;
  border-radius: var(--radius-sm);
}

.loading-copy {
  display: grid;
  gap: 10px;
  width: 100%;
  min-width: 0;
  padding-top: 5px;
}

.loading-line {
  display: block;
  width: 100%;
  height: 10px;
  border-radius: var(--radius-sm);
}

.loading-line--title {
  width: 34%;
}

.loading-line--medium {
  width: 72%;
}

.loading-line--short {
  width: 58%;
}

.loading-message-card--user .loading-line {
  justify-self: end;
}

.loading-message-card--user .loading-copy {
  gap: 9px;
  padding-top: 0;
}

.empty-chat {
  width: min(100%, 768px);
  margin: 0 auto;
  gap: 0;
  padding-bottom: 0;
}

.empty-chat h3 {
  max-width: 11em;
  margin: 0 auto;
  color: var(--color-primary);
  font-family: var(--font-heading);
  font-size: clamp(42px, 6vw, 76px);
  font-weight: 400;
  line-height: 1.1;
  letter-spacing: 0;
  text-wrap: balance;
}

.empty-chat i {
  display: none;
}

.shimmer {
  background: linear-gradient(
    110deg,
    var(--color-surface-container-high) 8%,
    color-mix(in srgb, var(--color-on-surface) 10%, var(--color-surface-canvas)) 18%,
    var(--color-surface-container-high) 33%
  );
  background-size: 200% 100%;
  animation: shimmer 1.55s ease-in-out infinite;
}

.loading-message-card--user .shimmer {
  background: linear-gradient(
    110deg,
    color-mix(in srgb, var(--color-on-primary) 13%, transparent) 8%,
    color-mix(in srgb, var(--color-on-primary) 24%, transparent) 18%,
    color-mix(in srgb, var(--color-on-primary) 13%, transparent) 33%
  );
  background-size: 200% 100%;
}

.loading-message-card--prompt .loading-user-bar {
  background: linear-gradient(
    110deg,
    color-mix(in srgb, var(--color-primary) 88%, var(--color-surface-card)) 8%,
    var(--color-primary) 18%,
    color-mix(in srgb, var(--color-primary) 88%, var(--color-surface-card)) 33%
  );
  background-size: 200% 100%;
}

.inline-error {
  margin: 0 clamp(20px, 5vw, 84px) 12px;
  padding: 10px var(--space-sm);
  border: 1px solid var(--color-error);
  color: var(--color-error);
  font-family: var(--font-body);
  font-size: 13px;
}

.composer {
  padding: 14px clamp(var(--space-md), 6vw, 120px) var(--space-md);
  border-top: 0;
  background: linear-gradient(180deg, transparent, var(--color-surface-card) 34%);
}

.ai-chat-panel.is-empty .composer {
  position: absolute;
  top: calc(50% + 54px);
  left: 50%;
  width: min(920px, calc(100% - var(--space-xl) - var(--space-xl)));
  margin: 0;
  padding: 0;
  border-top: 0;
  background: transparent;
  transform: translate(-50%, -50%);
}

.ai-chat-panel.is-empty .composer-shell {
  width: 100%;
  border-radius: 34px;
}

.composer-shell {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-xs);
  width: 60%;
  min-height: 68px;
  margin-inline: auto;
  padding: var(--space-xs) var(--space-xs) var(--space-xs) var(--space-md);
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-variant);
  border-radius: 34px;
  transition: background 0.2s ease, border-color 0.2s ease, transform 0.2s ease;
}

.composer-shell:focus-within {
  background: var(--color-surface-card);
  border-color: var(--color-primary);
}

.composer-brand {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  color: var(--color-primary);
  font-family: var(--font-label);
  font-size: 17px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: 0;
  text-transform: uppercase;
}

.composer-disclaimer {
  position: absolute;
  bottom: var(--space-lg);
  left: 50%;
  width: min(760px, calc(100% - var(--space-lg) - var(--space-lg)));
  margin: 0;
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  line-height: 1.4;
  text-align: center;
  transform: translateX(-50%);
}

.tools-right {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  padding-right: var(--space-xs);
}

.composer textarea {
  width: 100%;
  height: 44px;
  min-height: 44px;
  max-height: calc(1.55em * 6);
  resize: none;
  padding: 10px var(--space-xs);
  overflow-y: auto;
  background: transparent;
  border: 0;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 16px;
  line-height: 1.55;
  outline: none;
  scrollbar-width: none;
}

.composer textarea::-webkit-scrollbar {
  display: none;
}

.composer textarea::placeholder {
  color: var(--color-muted);
}

.primary-action,
.secondary-action {
  width: 40px;
  height: 40px;
  min-height: 40px;
  padding: 0;
  border-radius: var(--radius-lg);
}

.primary-action {
  background: transparent;
  border: 0;
  color: var(--color-on-surface-variant);
}

.primary-action:hover:not(:disabled) {
  background: var(--color-surface-container);
  color: var(--color-on-surface);
}

.primary-action:disabled {
  cursor: pointer;
  opacity: 1;
}

.secondary-action {
  background: transparent;
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
}

.voice-action {
  width: 46px;
  height: 46px;
  min-height: 46px;
  border-radius: 50%;
  color: var(--color-primary);
}

.voice-action:hover:not(:disabled) {
  background: color-mix(in srgb, var(--color-primary) 10%, transparent);
  color: var(--color-primary);
  transform: translateY(-1px);
}

.voice-action:focus-visible {
  outline: 2px solid color-mix(in srgb, var(--color-primary) 42%, transparent);
  outline-offset: 3px;
}

.voice-action.is-listening {
  background: var(--color-primary);
  color: var(--color-on-primary);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--color-primary) 18%, transparent);
}

.voice-action.is-listening:hover:not(:disabled) {
  background: color-mix(in srgb, var(--color-primary) 88%, var(--color-on-surface));
  color: var(--color-on-primary);
}

@keyframes shimmer {
  to {
    background-position-x: -200%;
  }
}

@keyframes messageEnter {
  from {
    opacity: 0;
    transform: translateY(6px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .message-row,
  .state-block,
  .shimmer {
    animation: none;
  }
}

@media (max-width: 720px) {
  .message-row {
    width: 100%;
  }

  .message-bubble {
    max-width: min(72ch, 100%);
  }

  .from-user .message-bubble {
    max-width: min(58ch, 88%);
  }

  .from-user.has-generation-request .message-bubble {
    width: 100%;
    max-width: 100%;
  }

  .message-list {
    padding: var(--space-md) 18px var(--space-sm);
  }

  .state-block {
    width: 100%;
  }

  .loading-message-card--user {
    width: min(88%, 420px);
  }

  .loading-message-card--prompt {
    width: min(74%, 360px);
  }

  .ai-chat-panel.is-empty .message-list {
    padding-bottom: 200px;
  }

  .empty-chat h3 {
    font-size: 32px;
    font-weight: 400;
    line-height: 1.2;
  }

  .composer {
    padding: var(--space-sm) var(--space-sm) var(--space-md);
  }

  .ai-chat-panel.is-empty .composer {
    top: calc(50% + 42px);
    width: calc(100% - var(--space-md) - var(--space-md));
  }

  .ai-chat-panel.is-empty .composer-shell {
    border-radius: 30px;
  }

  .composer-disclaimer {
    bottom: var(--space-md);
    width: calc(100% - var(--space-md) - var(--space-md));
  }

  .composer-shell {
    grid-template-columns: 28px minmax(0, 1fr) auto;
    align-items: center;
    width: 100%;
    min-height: 60px;
    gap: 8px;
    padding: 6px 8px 6px 12px;
    border-radius: 30px;
  }

  .composer-brand {
    align-self: center;
    min-width: 28px;
    justify-content: flex-start;
    font-size: 13px;
  }

  .composer textarea {
    min-width: 0;
    height: 42px;
    min-height: 42px;
    max-height: calc(1.55em * 6);
    padding: 9px 0;
    font-size: 14px;
  }

  .tools-right {
    align-self: center;
    gap: 4px;
    padding-right: 0;
  }

  .primary-action,
  .secondary-action {
    width: 36px;
    height: 36px;
    min-height: 36px;
    border-radius: var(--radius-sm);
  }

  .voice-action {
    width: 40px;
    height: 40px;
    min-height: 40px;
    border-radius: 50%;
  }
}
</style>
