<template>
  <section class="ai-chat-surface">
    <header
      v-if="showHeader"
      class="ai-chat-header"
    >
      <div>
        <span>Celestial Hub</span>
        <h2>{{ title }}</h2>
      </div>
      <button
        class="icon-button"
        title="新建会话"
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
      v-if="aiStore.context"
      class="context-strip"
    >
      <Route
        :size="14"
        stroke-width="1.8"
      />
      <span>{{ contextLabel }}</span>
    </div>

    <div class="message-list">
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
          :class="[messageClass(message.role), {failed: message.failed}]"
          class="message-row"
        >
          <span class="message-role">{{ roleLabel(message.role) }}</span>
          <div class="message-bubble">
            <p v-if="message.content">
              {{ message.content }}
            </p>
            <p
              v-else
              class="muted"
            >
              正在思考...
            </p>
            <span
              v-if="message.pending"
              class="typing-bar"
            />
          </div>
        </article>
      </template>

      <div
        v-else
        class="empty-chat"
      >
        <Sparkles
          :size="28"
          stroke-width="1.5"
        />
        <h3>询问课程、题库或学习数据</h3>
        <p>当前阶段支持基于知识库的流式问答；出题、批卷和直播总结将在后续智能体能力接入后启用。</p>
      </div>
    </div>

    <div
      v-if="aiStore.streamError"
      class="inline-error"
    >
      {{ aiStore.streamError }}
    </div>

    <form
      class="composer"
      @submit.prevent="submit"
    >
      <textarea
        v-model="draft"
        :disabled="aiStore.streaming"
        maxlength="4000"
        placeholder="输入你的问题..."
        rows="3"
        @keydown.enter.exact.prevent="submit"
      />
      <div class="composer-actions">
        <span>{{ draft.length }}/4000</span>
        <button
          v-if="aiStore.streaming"
          class="secondary-action"
          type="button"
          @click="aiStore.stopStreaming"
        >
          停止
        </button>
        <button
          :disabled="!draft.trim() || aiStore.streaming"
          class="primary-action"
          type="submit"
        >
          <Send
            :size="15"
            stroke-width="1.8"
          />
          发送
        </button>
      </div>
    </form>
  </section>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {Plus, Route, Send, Sparkles} from 'lucide-vue-next'

import {useAiStore} from '@/features/ai/stores/ai'
import type {AiMessageRole} from '@/features/ai/types/ai'
import {notify} from '@/shared/composables/useGlobalNotification'

const props = withDefaults(defineProps<{
  title?: string
  showHeader?: boolean
}>(), {
  title: 'AI 教学助手',
  showHeader: true,
})

const aiStore = useAiStore()
const draft = ref('')

const contextLabel = computed(() => {
  const context = aiStore.context
  const parts = [context.sourceRoute]
  if (context.courseId) parts.push(`课程 ${context.courseId}`)
  if (context.questionBankId) parts.push(`题库 ${context.questionBankId}`)
  if (context.classSessionId) parts.push(`课堂 ${context.classSessionId}`)
  if (context.chapterId) parts.push(`章节 ${context.chapterId}`)
  return parts.join(' / ')
})

onMounted(async () => {
  if (aiStore.conversations.length === 0) {
    try {
      await aiStore.loadConversations()
    } catch {
      notify.error('AI 会话加载失败')
    }
  }
})

async function submit() {
  const message = draft.value.trim()
  if (!message) return
  draft.value = ''
  await aiStore.sendMessage(message)
}

async function createNewConversation() {
  try {
    await aiStore.startConversation(props.title)
  } catch {
    notify.error('创建 AI 会话失败')
  }
}

function messageClass(role: AiMessageRole) {
  return role.toLowerCase() === 'user' ? 'from-user' : 'from-assistant'
}

function roleLabel(role: AiMessageRole) {
  return role.toLowerCase() === 'user' ? 'You' : 'AI'
}
</script>

<style scoped>
.ai-chat-surface {
  display: flex;
  min-height: 0;
  height: 100%;
  flex-direction: column;
  background: var(--color-surface-card);
  color: var(--color-on-surface);
}

.ai-chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px;
  border-bottom: 1px solid var(--color-outline-light);
}

.ai-chat-header span,
.message-role,
.composer-actions span {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.ai-chat-header h2 {
  margin: 5px 0 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 26px;
  font-weight: 400;
  line-height: 1.2;
}

.icon-button {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  background: transparent;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  cursor: pointer;
}

.icon-button:hover {
  background: var(--color-surface-container-high);
}

.context-strip {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 38px;
  padding: 0 18px;
  border-bottom: 1px solid var(--color-outline-light);
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 12px;
}

.context-strip span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 18px;
}

.message-row {
  display: grid;
  gap: 7px;
  margin-bottom: 16px;
}

.message-row.from-user {
  justify-items: end;
}

.message-row.from-user .message-role {
  text-align: right;
}

.message-bubble {
  width: fit-content;
  max-width: min(68ch, 100%);
  padding: 12px 14px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
}

.from-user .message-bubble {
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.message-row.failed .message-bubble {
  border-color: var(--color-error);
}

.message-bubble p {
  margin: 0;
  white-space: pre-wrap;
  color: inherit;
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.6;
}

.message-bubble .muted {
  color: var(--color-muted);
}

.typing-bar {
  display: block;
  width: 64px;
  height: 2px;
  margin-top: 10px;
  overflow: hidden;
  background: var(--color-outline-light);
}

.typing-bar::after {
  content: '';
  display: block;
  width: 38%;
  height: 100%;
  background: var(--color-on-surface);
  animation: typingSlide 1s ease-in-out infinite;
}

.empty-chat,
.state-block {
  display: grid;
  min-height: 260px;
  place-items: center;
  align-content: center;
  gap: 10px;
  color: var(--color-muted);
  text-align: center;
}

.empty-chat h3 {
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 400;
}

.empty-chat p {
  max-width: 42ch;
  margin: 0;
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.55;
}

.skeleton-line {
  width: min(320px, 80%);
  height: 18px;
}

.skeleton-line.short {
  width: min(220px, 62%);
}

.shimmer {
  background: linear-gradient(110deg, var(--color-surface-container-high) 8%, var(--color-surface-canvas) 18%, var(--color-surface-container-high) 33%);
  background-size: 200% 100%;
  animation: shimmer 1.4s ease-in-out infinite;
}

.inline-error {
  margin: 0 18px 12px;
  padding: 10px 12px;
  border: 1px solid var(--color-error);
  color: var(--color-error);
  font-family: var(--font-body);
  font-size: 13px;
}

.composer {
  display: grid;
  gap: 10px;
  padding: 14px 18px 18px;
  border-top: 1px solid var(--color-outline-light);
}

.composer textarea {
  width: 100%;
  min-height: 88px;
  resize: vertical;
  padding: 12px;
  background: var(--color-surface-canvas);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  outline: none;
}

.composer textarea:focus {
  border-color: var(--color-outline);
}

.composer-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.primary-action,
.secondary-action {
  display: inline-flex;
  min-height: 38px;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 0 13px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 12px;
}

.primary-action {
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  color: var(--color-on-primary);
}

.primary-action:disabled {
  opacity: 0.5;
  cursor: not-allowed;
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

@keyframes typingSlide {
  0% {
    transform: translateX(-100%);
  }

  100% {
    transform: translateX(270%);
  }
}
</style>
