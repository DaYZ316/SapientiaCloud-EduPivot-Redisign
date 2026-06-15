<template>
  <div class="chapter-content">
    <div v-if="!chapter" class="empty-content">
      <FileText :size="32" stroke-width="1.4"/>
      <p>{{ t('chapter.noContent') }}</p>
    </div>
    <div v-else class="content-body">
      <h2 class="content-title">{{ chapter.chapterName }}</h2>

      <div v-if="chapter.description" class="content-description">
        {{ chapter.description }}
      </div>

      <div v-if="chapter.content" class="content-html" v-html="chapter.content"></div>
      <div v-else class="content-placeholder">{{ t('chapter.noContent') }}</div>

      <div v-if="chapter.attachmentUrls && chapter.attachmentUrls.length" class="attachments-section">
        <h3>{{ t('chapter.attachments') }}</h3>
        <div class="attachment-list">
          <a
            v-for="(url, index) in chapter.attachmentUrls"
            :key="index"
            :href="url"
            target="_blank"
            rel="noopener noreferrer"
            class="attachment-item"
          >
            <FileDown :size="16"/>
            <span>{{ t('chapter.download') }} {{ index + 1 }}</span>
          </a>
        </div>
      </div>

      <div class="content-meta">
        <span class="meta-item"><Eye :size="14"/> {{ chapter.viewCount }} {{ t('chapter.viewCount') }}</span>
        <button
          class="meta-item like-button"
          :class="{ liked: chapter.likedByMe }"
          @click="$emit('likeToggle')"
        >
          <Heart :size="14" :fill="chapter.likedByMe ? 'currentColor' : 'none'"/>
          {{ chapter.likeCount }} {{ t('chapter.likeCount') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {FileText, FileDown, Eye, Heart} from 'lucide-vue-next'
import type {Chapter} from '@/features/course/types/chapter'

defineProps<{
  chapter: Chapter | null
}>()

defineEmits<{
  likeToggle: []
}>()

const {t} = useI18n()
</script>

<style scoped>
.chapter-content {
  display: flex;
  flex-direction: column;
}

.empty-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 48px 24px;
  color: var(--color-muted);
}

.empty-content p {
  margin: 0;
  font-family: var(--font-body);
  font-size: 16px;
}

.content-body {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.content-title {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 400;
  line-height: 1.3;
  color: var(--color-on-surface);
}

.content-description {
  font-family: var(--font-body);
  font-size: 15px;
  color: var(--color-muted);
  line-height: 1.6;
}

.content-html {
  font-family: var(--font-body);
  font-size: 15px;
  color: var(--color-on-surface);
  line-height: 1.8;
}

.content-html :deep(figure.chapter-image) {
  display: flex;
  justify-content: center;
  margin: 20px 0;
}

.content-html :deep(img) {
  max-width: min(100%, 720px);
  max-height: 520px;
  width: auto;
  height: auto;
  display: block;
  object-fit: contain;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  background: var(--color-surface-container);
}

.content-placeholder {
  font-family: var(--font-body);
  font-size: 14px;
  color: var(--color-muted);
  padding: 24px;
  background: var(--color-surface-container);
  border-radius: var(--radius-md);
}

.attachments-section h3 {
  margin: 0 0 12px;
  font-family: var(--font-body);
  font-size: 14px;
  font-weight: 400;
  color: var(--color-on-surface);
}

.attachment-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.attachment-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  text-decoration: none;
  font-family: var(--font-body);
  font-size: 13px;
  transition: border-color 0.15s;
}

.attachment-item:hover {
  border-color: var(--color-on-surface);
}

.content-meta {
  display: flex;
  gap: 20px;
  font-family: var(--font-body);
  font-size: 13px;
  color: var(--color-muted);
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.like-button {
  padding: 4px 8px;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}

.like-button:hover {
  border-color: var(--color-on-surface);
  color: var(--color-on-surface);
}

.like-button.liked {
  color: var(--color-primary);
  border-color: var(--color-primary);
}
</style>
