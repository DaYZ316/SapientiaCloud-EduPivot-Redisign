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
        class="state-block"
      >
        <div class="skeleton-line shimmer" />
        <div class="skeleton-line short shimmer" />
      </div>

      <template v-else-if="aiStore.messages.length > 0">
        <article
          v-for="message in aiStore.messages"
          :key="message.id"
          :class="[messageClass(message.role), {failed: message.failed, streaming: message.pending && !message.failed}]"
          class="message-row"
        >
          <div
            v-if="message.messageType && message.messageType !== 'TEXT'"
            class="message-meta"
          >
            <small>
              {{ messageTypeLabel(message.messageType) }}
            </small>
          </div>
          <div class="message-bubble">
            <div
              v-if="message.content"
              class="message-content"
            >
              {{ message.content }}
              <span
                v-if="message.pending"
                class="stream-caret"
              />
            </div>
            <AiPendingBrushLoader
              v-else-if="message.pending"
              class="pending-brush"
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
            v-if="aiStore.streaming"
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
            :disabled="!draft.trim() || aiStore.streaming"
            class="btn-primary primary-action"
            :title="t('common.ai.chat.send')"
            type="submit"
          >
            <Send
              v-if="draft.trim()"
              :size="15"
              stroke-width="1.9"
            />
            <Mic
              v-else
              :size="15"
              stroke-width="1.9"
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
import AiPendingBrushLoader from '@/features/ai/components/AiPendingBrushLoader.vue'
import type {AiAgentMode, AiMessageRole, GenerationRequest} from '@/features/ai/types/ai'
import {notify} from '@/shared/composables/useGlobalNotification'

const props = withDefaults(defineProps<{
  title?: string
  showHeader?: boolean
  showComposer?: boolean
  modelValue?: AiAgentMode
  generation?: GenerationRequest
}>(), {
  title: 'AI 教学助手',
  showHeader: true,
  showComposer: true,
  modelValue: undefined,
  generation: undefined,
})

const aiStore = useAiStore()
const authStore = useAuthStore()
const {t} = useI18n()
const draft = ref('')
const internalMode = ref<AiAgentMode>('CHAT')
const messageListRef = ref<HTMLElement | null>(null)
const composerTextareaRef = ref<HTMLTextAreaElement | null>(null)
const activeMode = computed(() => props.modelValue ?? internalMode.value)
const isEmpty = computed(() => !aiStore.loadingMessages && aiStore.messages.length === 0)
const hasConversationHistory = computed(() => aiStore.conversations.length > 0)
const userDisplayName = computed(() => authStore.user?.displayName || authStore.user?.email || 'User')
const streamFingerprint = computed(() =>
  aiStore.messages.map((message) => `${message.id}:${message.content.length}:${message.pending ? '1' : '0'}`).join('|'),
)
let scrollFrame = 0

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
})

async function submit() {
  const message = draft.value.trim()
  if (!message) return

  draft.value = ''
  await nextTick()
  resizeComposer()
  await aiStore.sendMessage(message, {
    agentMode: activeMode.value,
    courseId: aiStore.context.courseId,
    generation: activeMode.value === 'CHAT' ? undefined : normalizedGeneration(props.generation),
  })
}

function normalizedGeneration(generation?: GenerationRequest): GenerationRequest {
  const value = generation ?? {}
  return {
    ...value,
    questionCount: Math.max(1, Math.min(50, Number(value.questionCount) || 5)),
    paperName: value.paperName?.trim() || null,
    paperType: value.paperType?.trim() || null,
    requirement: value.requirement?.trim() || null,
  }
}

function createNewConversation() {
  aiStore.openNewConversationDraft()
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
  textarea.style.height = `${Math.min(textarea.scrollHeight, 160)}px`
}

function messageClass(role: AiMessageRole) {
  return role.toLowerCase() === 'user' ? 'from-user' : 'from-assistant'
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
</script>

<style scoped>
.ai-chat-panel {
  display: flex;
  position: relative;
  min-width: 0;
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
  flex: 1;
  flex-direction: column;
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
  margin-bottom: var(--space-md);
  animation: messageEnter 0.18s ease-out;
}

.message-row.from-user {
  justify-items: end;
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
  width: fit-content;
  max-width: min(72ch, 100%);
  padding: 14px var(--space-md);
  background: var(--color-surface-container-lowest);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-lg);
}

.from-assistant .message-bubble {
  border-color: transparent;
  border-radius: 0;
  padding: 0;
  background: transparent;
}

.from-user .message-bubble {
  max-width: min(58ch, 88%);
  background: var(--color-surface-container-high);
  border-color: transparent;
  color: var(--color-on-surface);
}

.message-row.failed .message-bubble {
  border-color: var(--color-error);
}

.message-content {
  margin: 0;
  white-space: pre-wrap;
  color: inherit;
  font-family: var(--font-body);
  font-size: 15px;
  line-height: 1.68;
}

.message-content {
  overflow-wrap: anywhere;
}

.message-row.streaming .message-content {
  display: inline;
}

.stream-caret {
  display: inline-block;
  width: 7px;
  height: 1.1em;
  margin-left: 2px;
  background: var(--color-on-surface);
  vertical-align: -0.18em;
  animation: caretBlink 1s steps(2, start) infinite;
}

.pending-brush {
  margin: 4px 0;
}

.empty-chat,
.state-block {
  display: grid;
  flex: 1;
  place-items: center;
  align-content: center;
  justify-items: center;
  color: var(--color-muted);
  text-align: center;
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
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: 0;
  text-wrap: balance;
}

.empty-chat i {
  display: none;
}

.skeleton-line {
  width: min(360px, 80%);
  height: 18px;
}

.skeleton-line.short {
  width: min(240px, 62%);
  margin-top: 10px;
}

.shimmer {
  background: linear-gradient(110deg, var(--color-surface-container-high) 8%, var(--color-surface-canvas) 18%, var(--color-surface-container-high) 33%);
  background-size: 200% 100%;
  animation: shimmer 1.4s ease-in-out infinite;
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
  width: min(760px, calc(100% - var(--space-lg) - var(--space-lg)));
  margin: 0;
  padding: 0;
  border-top: 0;
  background: transparent;
  transform: translate(-50%, -50%);
}

.composer-shell {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-xs);
  min-height: 68px;
  padding: var(--space-xs) var(--space-xs) var(--space-xs) var(--space-md);
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-variant);
  border-radius: 28px;
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
  max-height: 160px;
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

@keyframes shimmer {
  to {
    background-position-x: -200%;
  }
}

@keyframes caretBlink {
  0%,
  45% {
    opacity: 1;
  }

  46%,
  100% {
    opacity: 0;
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

@media (max-width: 720px) {
  .message-list {
    padding: var(--space-md) 18px var(--space-sm);
  }

  .ai-chat-panel.is-empty .message-list {
    padding-bottom: 200px;
  }

  .empty-chat h3 {
    font-size: 32px;
    font-weight: 600;
    line-height: 1.2;
  }

  .composer {
    padding: var(--space-sm) var(--space-sm) var(--space-md);
  }

  .ai-chat-panel.is-empty .composer {
    top: calc(50% + 42px);
    width: calc(100% - var(--space-md) - var(--space-md));
  }

  .composer-disclaimer {
    bottom: var(--space-md);
    width: calc(100% - var(--space-md) - var(--space-md));
  }

  .composer-shell {
    grid-template-columns: auto minmax(0, 1fr) auto;
    min-height: 60px;
    padding-left: var(--space-sm);
    border-radius: var(--radius-lg);
  }
}
</style>
