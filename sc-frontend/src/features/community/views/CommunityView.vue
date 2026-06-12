<template>
  <div class="community-page">
    <div class="page-header">
      <h1>{{ t('community.title') }}</h1>
      <button class="btn-primary">
        <Plus :size="16"/>
        {{ t('community.newDiscussion') }}
      </button>
    </div>

    <div class="community-content">
      <div class="discussions-list">
        <div class="empty-state">
          <h3>{{ t('community.noDiscussions') }}</h3>
          <p>{{ t('community.noDiscussionsDesc') }}</p>
        </div>
      </div>

      <aside class="community-sidebar">
        <div class="card">
          <h3 class="card-title">{{ t('community.trendingTopics') }}</h3>
          <p class="empty-text">{{ t('community.noTrendingTopics') }}</p>
        </div>
      </aside>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {Plus} from 'lucide-vue-next'
import {useI18n} from 'vue-i18n'

const {t} = useI18n()
</script>

<style scoped>
.community-page {
  max-width: 100%;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
}

.page-header h1 {
  margin: 0;
  font-family: 'Bodoni Moda', serif;
  font-size: 48px;
  font-weight: 600;
  color: var(--color-on-surface);
}

.community-content {
  display: grid;
  grid-template-columns: 1fr 280px;
  gap: 32px;
}

.discussions-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.discussion-card {
  display: flex;
  gap: 16px;
  padding: 20px 24px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 16px;
  transition: all 0.2s;
}

.discussion-card:hover {
  border-color: var(--color-on-surface);
}

.discussion-avatar {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  background: var(--color-surface-canvas);
  border-radius: 50%;
  color: var(--color-on-surface);
  flex-shrink: 0;
}

.discussion-body {
  flex: 1;
  min-width: 0;
}

.discussion-body h3 {
  margin: 0 0 8px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-on-surface);
}

.discussion-body p {
  margin: 0 0 12px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  color: var(--color-muted);
  line-height: 1.5;
}

.discussion-meta {
  display: flex;
  gap: 8px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 12px;
  color: var(--color-muted);
}

.community-sidebar {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.card {
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 24px;
  padding: 24px;
}

.card-title {
  margin: 0 0 16px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  color: var(--color-muted);
}

.topic-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.topic-list li {
  padding: 10px 0;
  border-bottom: 1px solid var(--color-surface-canvas);
}

.topic-list li:last-child {
  border-bottom: none;
}

.topic-link {
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  color: var(--color-on-surface);
  text-decoration: none;
}

.topic-link:hover {
  text-decoration: underline;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 120px 32px;
}

.empty-state h3 {
  margin: 0 0 8px;
  font-family: 'Bodoni Moda', serif;
  font-size: 24px;
  font-weight: 600;
  color: var(--color-on-surface);
}

.empty-state p {
  margin: 0;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 16px;
  color: var(--color-secondary);
}

.empty-text {
  margin: 0;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  color: var(--color-muted);
}

@media (max-width: 1024px) {
  .community-content {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .page-header h1 {
    font-size: 32px;
  }
}
</style>
