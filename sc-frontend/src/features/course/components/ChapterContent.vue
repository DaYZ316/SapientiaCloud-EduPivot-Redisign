<template>
  <article class="chapter-content">
    <div v-if="!chapter" class="empty-content">
      <FileText :size="32" stroke-width="1.4"/>
      <p>{{ t('chapter.noContent') }}</p>
    </div>
    <div v-else class="content-body">
      <header class="content-header">
        <span>{{ t('chapter.chapterContent') }}</span>
        <h1 class="content-title">{{ chapter.chapterName }}</h1>
      </header>

      <div v-if="chapter.description" class="content-description">
        {{ chapter.description }}
      </div>

      <div v-if="chapter.content" class="content-html" v-html="chapter.content"></div>
      <div v-else class="content-placeholder">{{ t('chapter.noContent') }}</div>

      <section v-if="chapter.attachmentUrls && chapter.attachmentUrls.length" id="chapter-attachments" class="attachments-section">
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
      </section>

      <div class="content-meta">
        <span class="meta-item"><Eye :size="14"/> {{ chapter.viewCount }} {{ t('chapter.viewCount') }}</span>
        <button
          class="meta-item like-button"
          type="button"
          :class="{ liked: chapter.likedByMe }"
          @click="$emit('likeToggle')"
        >
          <Heart :size="14" :fill="chapter.likedByMe ? 'currentColor' : 'none'"/>
          {{ chapter.likeCount }} {{ t('chapter.likeCount') }}
        </button>
      </div>
    </div>
  </article>
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
  min-width: 0;
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
  gap: 28px;
}

.content-header {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--color-outline-light);
}

.content-header span,
.attachments-section h3 {
  margin: 0;
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 400;
  letter-spacing: 0.05em;
  line-height: 1;
  text-transform: uppercase;
}

.content-title {
  margin: 0;
  font-family: var(--font-heading);
  font-size: clamp(34px, 4.2vw, 48px);
  font-weight: 400;
  color: var(--color-on-surface);
  letter-spacing: 0;
  line-height: 1.16;
  text-wrap: balance;
}

.content-description {
  max-width: 68ch;
  font-family: var(--font-body);
  font-size: 17px;
  color: var(--color-on-surface-variant);
  line-height: 1.6;
}

.content-html {
  max-width: 76ch;
  font-family: var(--font-body);
  font-size: 16px;
  color: var(--color-on-surface);
  line-height: 1.85;
}

.content-html :deep(h1),
.content-html :deep(h2),
.content-html :deep(h3),
.content-html :deep(h4) {
  margin: 32px 0 12px;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-weight: 700;
  line-height: 1.3;
}

.content-html :deep(h2) {
  padding-top: 20px;
  border-top: 1px solid var(--color-outline-light);
  font-size: 22px;
}

.content-html :deep(h3) {
  font-size: 18px;
}

.content-html :deep(p) {
  margin: 0 0 18px;
}

.content-html :deep(ul),
.content-html :deep(ol) {
  margin: 0 0 20px;
  padding-left: 22px;
}

.content-html :deep(li) {
  margin-bottom: 8px;
}

.content-html :deep(blockquote),
.content-html :deep(pre) {
  margin: 24px 0;
  padding: 18px 20px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  border-radius: 0;
}

.content-html :deep(blockquote) {
  color: var(--color-on-surface-variant);
}

.content-html :deep(pre),
.content-html :deep(code) {
  font-family: 'JetBrains Mono', 'SFMono-Regular', Consolas, monospace;
  font-size: 13px;
}

.content-html :deep(a) {
  color: var(--color-on-surface);
  text-decoration-color: var(--color-outline);
  text-underline-offset: 4px;
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
  border-radius: 0;
  background: var(--color-surface-container);
}

.content-placeholder {
  font-family: var(--font-body);
  font-size: 14px;
  color: var(--color-muted);
  padding: 24px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  border-radius: 0;
}

.attachments-section h3 {
  margin-bottom: 12px;
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
  border-radius: 0;
  color: var(--color-on-surface);
  text-decoration: none;
  font-family: var(--font-body);
  font-size: 13px;
  transition: background 0.2s ease, border-color 0.2s ease, transform 0.2s ease;
}

.attachment-item:hover {
  background: var(--color-surface-container-high);
  border-color: var(--color-on-surface);
}

.attachment-item:active {
  transform: translateY(1px);
}

.content-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  padding-top: 20px;
  border-top: 1px solid var(--color-outline-light);
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
  border-radius: 0;
  background: none;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.like-button:hover {
  border-color: var(--color-on-surface);
  color: var(--color-on-surface);
}

.like-button.liked {
  color: var(--color-primary);
  border-color: var(--color-primary);
}

.like-button:active {
  transform: translateY(1px);
}

@media (max-width: 760px) {
  .content-title {
    font-size: 32px;
  }

  .content-description,
  .content-html {
    font-size: 15px;
  }
}
</style>
