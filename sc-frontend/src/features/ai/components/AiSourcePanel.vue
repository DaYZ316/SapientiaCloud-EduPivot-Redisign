<template>
  <aside class="ai-source-panel">
    <nav class="quick-actions">
      <button
          :class="{active: route.name === 'ai-workspace'}"
          class="quick-action"
          type="button"
          @click="newConversation"
      >
        <Plus
            :size="16"
            stroke-width="1.9"
        />
        <span>{{ t('common.ai.sidebar.newChat') }}</span>
      </button>
      <button
          :class="{active: route.name === 'ai-history'}"
          class="quick-action"
          type="button"
          @click="openHistory"
      >
        <History
            :size="16"
            stroke-width="1.9"
        />
        <span>{{ t('common.ai.sidebar.history') }}</span>
      </button>
      <button
          :class="{active: route.name === 'ai-favorites'}"
          class="quick-action"
          type="button"
          @click="openFavorites"
      >
        <Star
            :size="16"
            stroke-width="1.9"
        />
        <span>{{ t('common.ai.sidebar.star') }}</span>
      </button>
    </nav>

    <section class="session-list">
      <div class="section-title">
        {{ t('common.ai.sidebar.recent') }}
      </div>
      <div
          v-if="aiStore.loadingConversations"
          :aria-label="t('common.ai.sidebar.loading')"
          aria-busy="true"
          class="session-loading-skeleton"
          role="status"
      >
        <article
            v-for="row in 6"
            :key="row"
            class="session-skeleton-item"
        >
          <span class="session-skeleton-line"/>
          <span class="session-skeleton-action"/>
        </article>
      </div>
      <div
          v-else-if="aiStore.sortedConversations.length === 0"
          class="panel-state"
      >
        {{ t('common.ai.sidebar.empty') }}
      </div>
      <div
          v-else
          class="session-items"
      >
        <article
            v-for="conversation in aiStore.sortedConversations"
            :key="conversation.id"
            :class="{active: conversation.id === aiStore.activeConversationId}"
            class="session-item"
        >
          <button
              class="session-link"
              type="button"
              @click="openConversation(conversation.id)"
          >
            <span>{{ conversation.title }}</span>
          </button>
          <button
              :aria-expanded="openMenuId === conversation.id"
              :class="{'menu-open': openMenuId === conversation.id, pinned: conversation.pinned}"
              :title="t('common.ai.sidebar.deleteTitle')"
              class="session-more"
              type="button"
              @click.stop="toggleMenu(conversation.id)"
          >
            <Pin
                v-if="conversation.pinned"
                :size="15"
                class="session-pin-icon"
                stroke-width="1.9"
            />
            <MoreVertical
                :size="16"
                class="session-more-icon"
                stroke-width="2"
            />
          </button>
          <div
              v-if="openMenuId === conversation.id"
              class="session-menu"
              @click.stop
          >
            <button
                type="button"
                @click="togglePinned(conversation)"
            >
              <Pin
                  :size="14"
                  stroke-width="1.8"
              />
              <span>{{ conversation.pinned ? t('common.ai.sidebar.unpin') : t('common.ai.sidebar.pin') }}</span>
            </button>
            <button
                type="button"
                @click="toggleFavorited(conversation)"
            >
              <Star
                  :fill="conversation.favorited ? 'currentColor' : 'none'"
                  :size="14"
                  stroke-width="1.8"
              />
              <span>{{ conversation.favorited ? t('common.ai.sidebar.unstar') : t('common.ai.sidebar.star') }}</span>
            </button>
            <button
                class="danger"
                type="button"
                @click="removeConversation(conversation.id)"
            >
              <Trash2
                  :size="14"
                  stroke-width="1.8"
              />
              <span>{{ t('common.ai.sidebar.delete') }}</span>
            </button>
          </div>
        </article>
      </div>
    </section>
  </aside>
</template>

<script lang="ts" setup>
import {onMounted, onUnmounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {History, MoreVertical, Pin, Plus, Star, Trash2} from 'lucide-vue-next'

import {useAiStore} from '@/features/ai/stores/ai'
import type {Conversation} from '@/features/ai/types/ai'
import {confirmDialog} from '@/shared/composables/useConfirmDialog'
import {notify} from '@/shared/composables/useGlobalNotification'

const aiStore = useAiStore()
const router = useRouter()
const route = useRoute()
const {t} = useI18n()
const openMenuId = ref<string | null>(null)

onMounted(() => {
  loadConversations()
  document.addEventListener('click', closeMenu)
})

onUnmounted(() => {
  document.removeEventListener('click', closeMenu)
})

async function loadConversations() {
  try {
    await aiStore.ensureConversationsLoaded()
  } catch {
    notify.error(t('common.ai.notify.loadFailed'))
  }
}

async function newConversation() {
  closeMenu()
  aiStore.openNewConversationDraft()
  await router.push({name: 'ai-workspace', query: {new: '1'}})
}

async function openHistory() {
  closeMenu()
  await router.push({name: 'ai-history'})
}

async function openFavorites() {
  closeMenu()
  await router.push({name: 'ai-favorites'})
}

async function openConversation(id: string) {
  closeMenu()
  await aiStore.loadMessages(id)
  if (route.name !== 'ai-workspace') {
    await router.push({name: 'ai-workspace'})
  }
}

function toggleMenu(id: string) {
  openMenuId.value = openMenuId.value === id ? null : id
}

function closeMenu() {
  openMenuId.value = null
}

async function togglePinned(conversation: Conversation) {
  try {
    closeMenu()
    await aiStore.togglePinned(conversation)
  } catch {
    notify.error(t('common.ai.notify.updateFailed'))
  }
}

async function toggleFavorited(conversation: Conversation) {
  try {
    closeMenu()
    await aiStore.toggleFavorited(conversation)
  } catch {
    notify.error(t('common.ai.notify.updateFailed'))
  }
}

async function removeConversation(id: string) {
  closeMenu()
  if (!(await confirmDialog({
    title: t('common.ai.sidebar.deleteTitle'),
    message: t('common.ai.sidebar.deleteConfirm'),
    confirmText: t('common.ai.sidebar.delete'),
    confirmVariant: 'danger',
  }))) return

  try {
    await aiStore.removeConversation(id)
  } catch {
    notify.error(t('common.ai.notify.deleteFailed'))
  }
}

</script>

<style scoped>
.ai-source-panel {
  display: flex;
  width: 100%;
  min-width: 0;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
  background: transparent;
  color: var(--color-on-surface);
}

.panel-state,
.section-title {
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 11px;
}

.quick-actions {
  display: grid;
  gap: 6px;
  padding: 0 0 16px;
  border-bottom: 1px solid var(--color-outline-light);
}

.quick-action {
  display: flex;
  align-items: center;
  gap: 10px;
  justify-content: flex-start;
  padding: 12px 14px;
  background: transparent;
  border: 0;
  border-radius: 14px;
  color: var(--color-muted);
  cursor: pointer;
  font-family: var(--font-body);
  font-size: 14px;
  font-weight: 400;
  text-align: left;
  transition: all 0.2s;
}

.quick-action:hover {
  background: var(--color-surface-canvas);
  color: var(--color-on-surface);
}

.quick-action.active {
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.quick-action:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.session-list {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
  padding: 18px 0 var(--space-xs);
  scrollbar-width: none;
}

.session-list::-webkit-scrollbar {
  display: none;
}

.section-title {
  padding: 0 10px 12px;
  font-family: var(--font-label);
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0;
  text-transform: none;
}

.session-items {
  display: grid;
  gap: 2px;
}

.session-loading-skeleton {
  display: grid;
  gap: 2px;
}

.session-skeleton-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 32px;
  align-items: center;
  min-height: 34px;
  border-radius: 10px;
}

.session-skeleton-line {
  width: min(100%, 152px);
  height: 13px;
  margin-left: 10px;
  border-radius: var(--radius-sm);
}

.session-skeleton-action {
  width: 18px;
  height: 18px;
  justify-self: center;
  border-radius: var(--radius-sm);
}

.session-skeleton-line,
.session-skeleton-action {
  background: linear-gradient(
      110deg,
      var(--color-surface-container-high) 8%,
      color-mix(in srgb, var(--color-on-surface) 9%, var(--color-surface-canvas)) 18%,
      var(--color-surface-container-high) 33%
  );
  background-size: 200% 100%;
  animation: session-skeleton-shimmer 1.45s ease-in-out infinite;
}

.session-item {
  display: grid;
  position: relative;
  grid-template-columns: minmax(0, 1fr) 32px;
  gap: 0;
  align-items: center;
  border-radius: 10px;
  transition: background 0.2s ease, color 0.2s ease;
}

.session-item:hover,
.session-item.active {
  background: var(--color-surface-container);
}

.session-link {
  display: flex;
  min-width: 0;
  min-height: 34px;
  align-items: center;
  padding: 7px 2px 7px 10px;
  background: transparent;
  border: 0;
  border-radius: 10px 0 0 10px;
  color: var(--color-on-surface);
  cursor: pointer;
  text-align: left;
  transition: color 0.2s ease;
}

.session-item span {
  min-width: 0;
  overflow: hidden;
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-more {
  display: grid;
  width: 32px;
  height: 34px;
  place-items: center;
  background: transparent;
  border: 0;
  border-radius: 0 10px 10px 0;
  color: var(--color-on-surface);
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s ease, color 0.2s ease, background 0.2s ease;
}

.session-more svg {
  transition: transform 0.2s ease;
}

.session-more.pinned {
  color: var(--color-primary);
  opacity: 1;
}

.session-more.pinned .session-more-icon {
  display: none;
}

.session-more:hover {
  background: var(--color-surface-canvas);
  color: var(--color-primary);
}

.session-more.menu-open {
  background: var(--color-surface-canvas);
  color: var(--color-primary);
  opacity: 1;
}

.session-more.menu-open svg {
  transform: rotate(90deg);
}

.session-item:hover .session-more.pinned .session-pin-icon,
.session-item:focus-within .session-more.pinned .session-pin-icon,
.session-more.pinned.menu-open .session-pin-icon {
  display: none;
}

.session-item:hover .session-more.pinned .session-more-icon,
.session-item:focus-within .session-more.pinned .session-more-icon,
.session-more.pinned.menu-open .session-more-icon {
  display: block;
}

.session-item:hover .session-more,
.session-item:focus-within .session-more,
.session-item.active .session-more {
  opacity: 1;
}

.session-menu {
  position: absolute;
  top: calc(100% + 4px);
  right: 0;
  z-index: 20;
  display: grid;
  min-width: 132px;
  padding: 6px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-md);
  box-shadow: 0 14px 32px rgba(26, 28, 28, 0.14);
}

.session-menu button {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 32px;
  padding: 0 10px;
  background: transparent;
  border: 0;
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  cursor: pointer;
  font-family: var(--font-body);
  font-size: 13px;
  text-align: left;
  transition: background 0.2s ease, color 0.2s ease;
}

.session-menu button:hover {
  background: var(--color-surface-canvas);
}

.session-menu button.danger {
  color: var(--color-error);
}

.panel-state {
  padding: var(--space-md) var(--space-xs);
  line-height: 1.55;
  text-align: left;
}

@keyframes session-skeleton-shimmer {
  to {
    background-position-x: -200%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .session-skeleton-line,
  .session-skeleton-action {
    animation: none;
  }
}

</style>
