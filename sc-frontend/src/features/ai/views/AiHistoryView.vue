<template>
  <main class="ai-history-page">
    <section class="history-shell">
      <header class="history-header">
        <h1>{{ t('common.ai.history.title') }}</h1>
        <p>{{ t('common.ai.history.description') }}</p>
      </header>

      <div
          v-if="aiStore.loadingConversations"
          class="history-state"
      >
        {{ t('common.ai.history.loading') }}
      </div>

      <div
          v-else-if="visibleGroups.length === 0"
          class="history-state"
      >
        {{ t('common.ai.history.empty') }}
      </div>

      <div
          v-else
          class="history-groups"
      >
        <section
            v-for="group in visibleGroups"
            :key="group.kind"
            class="history-group"
        >
          <h2>{{ group.label }}</h2>
          <div class="history-list">
            <button
                v-for="(conversation, index) in group.items"
                :key="conversation.id"
                class="history-row"
                type="button"
                @click="openConversation(conversation.id)"
            >
              <component
                  :is="rowIcon(index)"
                  :size="14"
                  stroke-width="1.8"
              />
              <span>{{ conversation.title }}</span>
              <time>{{ formatConversationTime(conversation.updatedAt, group.kind) }}</time>
            </button>
          </div>
        </section>
      </div>

      <div
          v-if="visibleGroups.length > 0"
          ref="paginationSentinelRef"
          class="history-pagination"
      >
        <span v-if="aiStore.loadingMoreConversations">{{ t('common.ai.history.loadingMore') }}</span>
      </div>

      <footer class="history-footer">
        <span>{{ t('common.ai.chat.disclaimer') }}</span>
        <a href="#">{{ t('common.ai.history.terms') }}</a>
        <a href="#">{{ t('common.ai.history.privacy') }}</a>
      </footer>
    </section>
  </main>
</template>

<script lang="ts" setup>
import {computed, nextTick, onBeforeUnmount, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import {CalendarDays, Code2, FileText, Languages, MessageSquare, PencilLine} from 'lucide-vue-next'

import {useAiStore} from '@/features/ai/stores/ai'
import type {Conversation} from '@/features/ai/types/ai'
import {notify} from '@/shared/composables/useGlobalNotification'

type HistoryGroupKind = 'today' | 'yesterday' | 'recent'

interface HistoryGroup {
  label: string
  kind: HistoryGroupKind
  items: Conversation[]
}

const aiStore = useAiStore()
const router = useRouter()
const {t, locale} = useI18n()
const paginationSentinelRef = ref<HTMLElement | null>(null)
let paginationObserver: IntersectionObserver | null = null

const rowIcons = [MessageSquare, Code2, FileText, PencilLine, Languages, CalendarDays]

const visibleGroups = computed(() => {
  const groups: HistoryGroup[] = [
    {label: t('common.ai.history.today'), kind: 'today', items: []},
    {label: t('common.ai.history.yesterday'), kind: 'yesterday', items: []},
    {label: t('common.ai.history.recent'), kind: 'recent', items: []},
  ]

  for (const conversation of updatedConversations.value) {
    const date = new Date(conversation.updatedAt)
    if (Number.isNaN(date.getTime())) continue

    if (isToday(date)) {
      groups[0].items.push(conversation)
    } else if (isYesterday(date)) {
      groups[1].items.push(conversation)
    } else {
      groups[2].items.push(conversation)
    }
  }

  return groups.filter(group => group.items.length > 0)
})

const updatedConversations = computed(() =>
    [...aiStore.conversations].sort((left, right) =>
        new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime(),
    ),
)

onMounted(async () => {
  await loadConversations()
  await nextTick()
  observePaginationSentinel()
})

onBeforeUnmount(() => {
  paginationObserver?.disconnect()
})

async function loadConversations() {
  try {
    await aiStore.ensureConversationsLoaded()
  } catch {
    notify.error(t('common.ai.notify.loadFailed'))
  }
}

async function loadOlderConversations() {
  if (!aiStore.hasMoreConversations || aiStore.loadingMoreConversations) return

  try {
    await aiStore.loadMoreConversations()
  } catch {
    notify.error(t('common.ai.notify.loadFailed'))
  }
}

async function openConversation(id: string) {
  await aiStore.loadMessages(id)
  await router.push({name: 'ai-workspace'})
}

function observePaginationSentinel() {
  paginationObserver?.disconnect()
  const sentinel = paginationSentinelRef.value
  if (!sentinel) return

  paginationObserver = new IntersectionObserver((entries) => {
    if (entries.some(entry => entry.isIntersecting)) {
      loadOlderConversations()
    }
  }, {rootMargin: '240px 0px'})
  paginationObserver.observe(sentinel)
}

function rowIcon(index: number) {
  return rowIcons[index % rowIcons.length]
}

function isToday(date: Date) {
  const today = new Date()
  return isSameDay(date, today)
}

function isYesterday(date: Date) {
  const yesterday = new Date()
  yesterday.setDate(yesterday.getDate() - 1)
  return isSameDay(date, yesterday)
}

function isSameDay(left: Date, right: Date) {
  return left.getFullYear() === right.getFullYear()
      && left.getMonth() === right.getMonth()
      && left.getDate() === right.getDate()
}

function formatConversationTime(value: string, kind: HistoryGroupKind) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''

  if (kind === 'recent') {
    return date.toLocaleDateString(String(locale.value), {
      month: 'short',
      day: 'numeric',
    })
  }

  return date.toLocaleTimeString(String(locale.value), {
    hour: 'numeric',
    minute: '2-digit',
  })
}
</script>

<style scoped>
.ai-history-page {
  min-height: 100dvh;
  background: var(--color-surface-card);
  color: var(--color-on-surface);
}

.history-shell {
  display: flex;
  min-height: 100dvh;
  flex-direction: column;
  padding: 48px clamp(32px, 7vw, 144px) 28px;
}

.history-header {
  margin-bottom: 48px;
}

.history-header h1 {
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: clamp(48px, 6vw, 78px);
  font-weight: 700;
  line-height: 1;
}

.history-header p {
  max-width: 560px;
  margin: 14px 0 0;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.6;
}

.history-groups {
  display: grid;
  gap: 34px;
  width: min(100%, 980px);
}

.history-group h2 {
  margin: 0 0 12px;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 500;
  line-height: 1.2;
}

.history-list {
  border-top: 1px solid var(--color-outline-light);
}

.history-row {
  display: grid;
  width: 100%;
  grid-template-columns: 20px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  min-height: 48px;
  padding: 0;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
  cursor: pointer;
  text-align: left;
  transition: background 0.2s ease, color 0.2s ease, padding 0.2s ease;
}

.history-row:hover,
.history-row:focus-visible {
  padding-inline: 12px;
  background: var(--color-primary);
  color: var(--color-on-primary);
  outline: 0;
}

.history-row span {
  min-width: 0;
  overflow: hidden;
  font-family: var(--font-body);
  font-size: 14px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-row time {
  color: inherit;
  font-family: var(--font-label);
  font-size: 11px;
  letter-spacing: 0.04em;
}

.history-state {
  width: min(100%, 760px);
  padding: 42px 0;
  border-top: 1px solid var(--color-outline-light);
  border-bottom: 1px solid var(--color-outline-light);
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
}

.history-pagination {
  display: flex;
  min-height: 48px;
  align-items: center;
  margin: 20px 0 0;
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.history-footer {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: auto;
  padding-top: 48px;
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 10px;
  letter-spacing: 0.08em;
}

.history-footer a {
  color: inherit;
  text-decoration: none;
}

.history-footer a:hover {
  color: var(--color-on-surface);
}

@media (max-width: 760px) {
  .history-shell {
    padding: 32px 22px 24px;
  }

  .history-header {
    margin-bottom: 36px;
  }

  .history-row {
    grid-template-columns: 18px minmax(0, 1fr);
    padding-block: 10px;
  }

  .history-row time {
    grid-column: 2;
  }

  .history-footer {
    flex-wrap: wrap;
  }
}
</style>
