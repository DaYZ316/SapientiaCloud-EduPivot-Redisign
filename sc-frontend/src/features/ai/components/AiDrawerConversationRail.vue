<template>
  <aside
      :aria-label="t('common.ai.sidebar.history')"
      class="drawer-conversation-rail"
  >
    <div class="rail-actions">
      <button
          :title="t('common.ai.sidebar.newChat')"
          class="rail-new-chat"
          type="button"
          @click="newConversation"
      >
        <Plus
            :size="15"
            stroke-width="1.9"
        />
        <span>{{ t('common.ai.sidebar.newChat') }}</span>
      </button>
    </div>

    <section class="rail-list">
      <div class="rail-heading">
        {{ t('common.ai.sidebar.recent') }}
      </div>

      <div
          v-if="aiStore.loadingConversations"
          :aria-label="t('common.ai.sidebar.loading')"
          aria-busy="true"
          class="rail-loading"
          role="status"
      >
        <span
            v-for="row in 6"
            :key="row"
        />
      </div>

      <p
          v-else-if="conversations.length === 0"
          class="rail-empty"
      >
        {{ t('common.ai.sidebar.empty') }}
      </p>

      <div
          v-else
          class="rail-items"
      >
        <button
            v-for="conversation in conversations"
            :key="conversation.id"
            :class="{active: conversation.id === aiStore.activeConversationId}"
            :title="conversation.title"
            class="rail-conversation"
            type="button"
            @click="openConversation(conversation.id)"
        >
          <span>{{ conversation.title }}</span>
        </button>
      </div>
    </section>
  </aside>
</template>

<script lang="ts" setup>
import {computed, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {Plus} from 'lucide-vue-next'

import {useAiStore} from '@/features/ai/stores/ai'
import {notify} from '@/shared/composables/useGlobalNotification'

const aiStore = useAiStore()
const {t} = useI18n()

const conversations = computed(() => aiStore.sortedConversations)

onMounted(() => {
  void loadConversations()
})

async function loadConversations() {
  try {
    await aiStore.ensureConversationsLoaded()
  } catch {
    notify.error(t('common.ai.notify.loadFailed'))
  }
}

function newConversation() {
  aiStore.openNewConversationDraft()
}

async function openConversation(id: string) {
  if (id === aiStore.activeConversationId) return

  try {
    await aiStore.loadMessages(id)
  } catch {
    notify.error(t('common.ai.notify.loadFailed'))
  }
}
</script>

<style scoped>
.drawer-conversation-rail {
  display: flex;
  width: 178px;
  min-width: 0;
  min-height: 0;
  flex: 0 0 178px;
  flex-direction: column;
  overflow: hidden;
  background: var(--color-surface-canvas);
  border-right: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
}

.rail-actions {
  padding: 12px;
  border-bottom: 1px solid var(--color-outline-light);
}

.rail-new-chat {
  display: inline-flex;
  width: 100%;
  min-width: 0;
  min-height: 34px;
  align-items: center;
  gap: 8px;
  justify-content: center;
  padding: 0 11px;
  background: var(--color-on-surface);
  border: 0;
  border-radius: var(--radius-sm);
  color: var(--color-surface-card);
  cursor: pointer;
  font-family: var(--font-body);
  font-size: 12px;
  font-weight: 600;
  transition: background 0.2s ease, transform 0.2s ease;
}

.rail-new-chat:hover {
  background: var(--color-primary);
}

.rail-new-chat:active {
  transform: scale(0.98);
}

.rail-list {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
  padding: 13px 8px 12px;
  scrollbar-width: none;
}

.rail-list::-webkit-scrollbar {
  display: none;
}

.rail-heading {
  padding: 0 8px 9px;
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0;
}

.rail-items,
.rail-loading {
  display: grid;
  gap: 4px;
}

.rail-conversation {
  display: flex;
  position: relative;
  width: 100%;
  min-width: 0;
  min-height: 39px;
  align-items: center;
  padding: 8px 10px 8px 12px;
  background: transparent;
  border: 0;
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  cursor: pointer;
  text-align: left;
  transition: background 0.2s ease, color 0.2s ease;
}

.rail-conversation:hover {
  background: var(--color-surface-container);
}

.rail-conversation:focus-visible {
  outline: none;
  background: var(--color-surface-container);
}

.rail-conversation.active {
  background: var(--color-surface-container-high);
  color: var(--color-primary);
}

.rail-conversation span {
  display: -webkit-box;
  min-width: 0;
  overflow: hidden;
  font-family: var(--font-body);
  font-size: 12px;
  font-weight: 600;
  line-height: 1.32;
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.rail-empty {
  margin: 0;
  padding: 8px;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 12px;
  line-height: 1.55;
}

.rail-loading span {
  height: 35px;
  border-radius: var(--radius-sm);
  background: linear-gradient(
      110deg,
      var(--color-surface-container-high) 8%,
      color-mix(in srgb, var(--color-on-surface) 9%, var(--color-surface-canvas)) 18%,
      var(--color-surface-container-high) 33%
  );
  background-size: 200% 100%;
  animation: rail-skeleton-shimmer 1.45s ease-in-out infinite;
}

@keyframes rail-skeleton-shimmer {
  to {
    background-position-x: -200%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .rail-loading span {
    animation: none;
  }
}

@media (max-width: 760px) {
  .drawer-conversation-rail {
    width: 100%;
    min-height: 66px;
    flex: 0 0 auto;
    flex-direction: row;
    border-right: 0;
    border-bottom: 1px solid var(--color-outline-light);
  }

  .rail-actions {
    display: flex;
    align-items: center;
    padding: 10px 8px 10px 10px;
    border-right: 1px solid var(--color-outline-light);
    border-bottom: 0;
  }

  .rail-new-chat {
    width: 34px;
    height: 34px;
    min-height: 34px;
    padding: 0;
  }

  .rail-new-chat span,
  .rail-heading {
    display: none;
  }

  .rail-list {
    display: flex;
    min-width: 0;
    flex: 1;
    align-items: center;
    overflow-x: auto;
    overflow-y: hidden;
    padding: 10px 10px 10px 8px;
  }

  .rail-items,
  .rail-loading {
    display: flex;
    width: max-content;
    gap: 7px;
  }

  .rail-loading span {
    width: 128px;
    flex: 0 0 128px;
  }

  .rail-conversation {
    width: 132px;
    flex: 0 0 132px;
    min-height: 42px;
    padding: 7px 10px;
    border: 1px solid var(--color-outline-light);
  }

  .rail-conversation span {
    display: block;
    font-size: 12px;
    line-height: 1.25;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .rail-empty {
    width: 220px;
    padding: 0 4px;
    white-space: nowrap;
  }
}
</style>
