<template>
  <main class="ai-favorites-page">
    <section class="favorites-shell">
      <header class="favorites-header">
        <div>
          <h1>{{ t('common.ai.favorites.title') }}</h1>
          <p>{{ t('common.ai.favorites.description') }}</p>
        </div>
        <div class="favorites-count" aria-hidden="true">
          <strong>{{ filteredConversations.length }}</strong>
          <span>{{ t('common.ai.favorites.countLabel') }}</span>
        </div>
      </header>

      <label class="favorites-search">
        <Search :size="16" stroke-width="1.8"/>
        <input v-model="keyword" :placeholder="t('common.ai.favorites.searchPlaceholder')" type="search"/>
      </label>

      <div v-if="aiStore.loadingConversations" class="favorites-state">
        {{ t('common.ai.history.loading') }}
      </div>

      <div v-else-if="filteredConversations.length === 0" class="favorites-state">
        <Star :size="22" stroke-width="1.7"/>
        <span>{{ emptyMessage }}</span>
      </div>

      <div v-else class="favorites-list">
        <article
          v-for="(conversation, index) in filteredConversations"
          :key="conversation.id"
          class="favorite-row"
        >
          <button class="favorite-link" type="button" @click="openConversation(conversation.id)">
            <component :is="rowIcon(index)" :size="15" stroke-width="1.8"/>
            <span>{{ conversation.title }}</span>
            <time>{{ formatConversationTime(conversation.updatedAt) }}</time>
          </button>
          <button
            class="favorite-remove"
            :title="t('common.ai.favorites.remove')"
            type="button"
            @click="removeFavorite(conversation)"
          >
            <StarOff :size="15" stroke-width="1.8"/>
          </button>
        </article>
      </div>

      <footer class="favorites-footer">
        <span>{{ t('common.ai.chat.disclaimer') }}</span>
        <a href="#">{{ t('common.ai.history.terms') }}</a>
        <a href="#">{{ t('common.ai.history.privacy') }}</a>
      </footer>
    </section>
  </main>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import {BookMarked, FileText, MessageSquare, PencilLine, Search, Star, StarOff} from 'lucide-vue-next'

import {useAiStore} from '@/features/ai/stores/ai'
import type {Conversation} from '@/features/ai/types/ai'
import {notify} from '@/shared/composables/useGlobalNotification'

const aiStore = useAiStore()
const router = useRouter()
const {t, locale} = useI18n()
const keyword = ref('')
const rowIcons = [Star, MessageSquare, FileText, PencilLine, BookMarked]

const normalizedKeyword = computed(() => keyword.value.trim().toLowerCase())
const favoriteConversations = computed(() =>
  aiStore.sortedConversations.filter(conversation => conversation.favorited),
)
const filteredConversations = computed(() => {
  if (!normalizedKeyword.value) return favoriteConversations.value
  return favoriteConversations.value.filter(conversation =>
    conversation.title.toLowerCase().includes(normalizedKeyword.value),
  )
})
const emptyMessage = computed(() =>
  favoriteConversations.value.length === 0
    ? t('common.ai.favorites.empty')
    : t('common.ai.favorites.noResults'),
)

onMounted(loadConversations)

async function loadConversations() {
  try {
    await aiStore.loadConversations()
  } catch {
    notify.error(t('common.ai.notify.loadFailed'))
  }
}

async function openConversation(id: string) {
  await aiStore.loadMessages(id)
  await router.push({name: 'ai-workspace'})
}

async function removeFavorite(conversation: Conversation) {
  try {
    await aiStore.toggleFavorited(conversation)
  } catch {
    notify.error(t('common.ai.notify.updateFailed'))
  }
}

function rowIcon(index: number) {
  return rowIcons[index % rowIcons.length]
}

function formatConversationTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''

  return new Intl.DateTimeFormat(String(locale.value), {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(date)
}
</script>

<style scoped>
.ai-favorites-page {
  min-height: 100dvh;
  background: var(--color-surface-card);
  color: var(--color-on-surface);
}

.favorites-shell {
  display: flex;
  min-height: 100dvh;
  flex-direction: column;
  padding: 48px clamp(32px, 7vw, 144px) 28px;
}

.favorites-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 28px;
  align-items: end;
  margin-bottom: 30px;
}

.favorites-header h1 {
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: clamp(48px, 6vw, 78px);
  font-weight: 700;
  line-height: 1;
  letter-spacing: 0;
}

.favorites-header p {
  max-width: 560px;
  margin: 14px 0 0;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.6;
}

.favorites-count {
  min-width: 128px;
  padding: 16px;
  border: 1px solid var(--color-outline-light);
  background: var(--color-surface-container);
}

.favorites-count strong,
.favorites-count span {
  display: block;
}

.favorites-count strong {
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 38px;
  font-weight: 600;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.favorites-count span {
  margin-top: 8px;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 11px;
}

.favorites-search {
  width: min(100%, 420px);
  min-height: 44px;
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 28px;
  padding: 0 14px;
  border: 1px solid var(--color-outline-light);
  background: var(--color-surface-container);
  color: var(--color-muted);
}

.favorites-search input {
  width: 100%;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
}

.favorites-search input::placeholder {
  color: var(--color-muted);
}

.favorites-list {
  display: grid;
  width: min(100%, 980px);
  border-top: 1px solid var(--color-outline-light);
}

.favorite-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 42px;
  align-items: center;
  border-bottom: 1px solid var(--color-outline-light);
}

.favorite-link {
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  min-height: 54px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-on-surface);
  cursor: pointer;
  text-align: left;
  transition: background 0.2s ease, color 0.2s ease, padding 0.2s ease;
}

.favorite-link:hover,
.favorite-link:focus-visible {
  padding-inline: 12px;
  background: var(--color-primary);
  color: var(--color-on-primary);
  outline: 0;
}

.favorite-link span {
  min-width: 0;
  overflow: hidden;
  font-family: var(--font-body);
  font-size: 14px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.favorite-link time {
  color: inherit;
  font-family: var(--font-label);
  font-size: 11px;
  letter-spacing: 0.04em;
}

.favorite-remove {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  justify-self: end;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-muted);
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.favorite-remove:hover {
  background: var(--color-surface-canvas);
  color: var(--color-primary);
}

.favorite-remove:active {
  transform: translateY(1px);
}

.favorites-state {
  width: min(100%, 760px);
  min-height: 154px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 42px 0;
  border-top: 1px solid var(--color-outline-light);
  border-bottom: 1px solid var(--color-outline-light);
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
}

.favorites-footer {
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

.favorites-footer a {
  color: inherit;
  text-decoration: none;
}

.favorites-footer a:hover {
  color: var(--color-on-surface);
}

@media (max-width: 760px) {
  .favorites-shell {
    padding: 32px 22px 24px;
  }

  .favorites-header,
  .favorite-link {
    grid-template-columns: 1fr;
  }

  .favorites-count {
    width: 100%;
  }

  .favorite-row {
    grid-template-columns: minmax(0, 1fr) 40px;
    align-items: start;
    padding: 10px 0;
  }

  .favorite-link {
    gap: 8px;
  }

  .favorite-link time {
    grid-column: 1;
  }
}
</style>
