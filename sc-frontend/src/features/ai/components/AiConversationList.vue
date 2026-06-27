<template>
  <aside class="conversation-list">
    <div class="list-header">
      <div>
        <span>Conversations</span>
        <h2>AI 会话</h2>
      </div>
      <button
          title="新建会话"
          type="button"
          @click="newConversation"
      >
        <Plus
            :size="16"
            stroke-width="1.8"
        />
      </button>
    </div>

    <div
        v-if="aiStore.loadingConversations"
        class="list-state"
    >
      加载会话中...
    </div>
    <div
        v-else-if="aiStore.sortedConversations.length === 0"
        class="list-state"
    >
      暂无会话
    </div>
    <div
        v-else
        class="conversation-rows"
    >
      <button
          v-for="conversation in aiStore.sortedConversations"
          :key="conversation.id"
          :class="{active: conversation.id === aiStore.activeConversationId}"
          class="conversation-row"
          type="button"
          @click="aiStore.loadMessages(conversation.id)"
      >
        <span>{{ conversation.title }}</span>
        <small>{{ formatTime(conversation.updatedAt) }}</small>
      </button>
    </div>
  </aside>
</template>

<script lang="ts" setup>
import {onMounted} from 'vue'
import {Plus} from 'lucide-vue-next'

import {useAiStore} from '@/features/ai/stores/ai'
import {notify} from '@/shared/composables/useGlobalNotification'

const aiStore = useAiStore()

onMounted(async () => {
  try {
    await aiStore.loadConversations()
  } catch {
    notify.error('AI 会话加载失败')
  }
})

function newConversation() {
  aiStore.openNewConversationDraft()
}

function formatTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleDateString()
}
</script>

<style scoped>
.conversation-list {
  display: flex;
  min-height: 0;
  flex-direction: column;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
}

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px;
  border-bottom: 1px solid var(--color-outline-light);
}

.list-header span {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.list-header h2 {
  margin: 5px 0 0;
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 400;
}

.list-header button {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  background: transparent;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  cursor: pointer;
}

.conversation-rows {
  min-height: 0;
  overflow-y: auto;
}

.conversation-row {
  display: grid;
  width: 100%;
  gap: 6px;
  padding: 13px 16px;
  border: 0;
  border-bottom: 1px solid var(--color-outline-light);
  background: transparent;
  color: var(--color-on-surface);
  cursor: pointer;
  text-align: left;
}

.conversation-row:hover,
.conversation-row.active {
  background: var(--color-surface-container);
}

.conversation-row span {
  overflow: hidden;
  font-family: var(--font-body);
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-row small,
.list-state {
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 12px;
}

.list-state {
  padding: 24px 16px;
}
</style>
