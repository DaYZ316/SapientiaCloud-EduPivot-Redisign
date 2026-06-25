<template>
  <aside class="chapter-preview-panel">
    <header class="panel-header">
      <div>
        <span>Chapter Preview</span>
        <h2>章节内容预览</h2>
      </div>
      <button class="icon-button" type="button" @click="$emit('close')">
        <X :size="18" stroke-width="1.8"/>
      </button>
    </header>

    <div v-if="loading" class="panel-state">加载中...</div>
    <div v-else-if="loadFailed" class="panel-state">
      <p>章节内容加载失败</p>
      <button class="text-button" type="button" @click="loadChapters">重试</button>
    </div>

    <template v-else>
      <section v-if="flatChapters.length > 0" class="chapter-preview-body">
        <div class="chapter-list">
          <button
              v-for="(chapter, index) in flatChapters"
              :key="chapter.id"
              :class="{active: chapter.id === selectedChapterId}"
              class="chapter-row"
              type="button"
              @click="selectChapter(chapter.id)"
          >
            <span>{{ formatIndex(index + 1) }}</span>
            <strong>{{ chapter.chapterName }}</strong>
            <small v-if="chapter.description">{{ chapter.description }}</small>
          </button>
        </div>

        <article class="chapter-detail">
          <div v-if="detailLoading" class="panel-state compact">正在加载章节...</div>
          <template v-else-if="selectedChapter">
            <span class="detail-kicker">当前章节</span>
            <h3>{{ selectedChapter.chapterName }}</h3>
            <p v-if="selectedChapter.description" class="chapter-description">{{ selectedChapter.description }}</p>
            <div v-if="selectedChapter.content" class="chapter-content" v-html="selectedChapter.content"></div>
            <p v-else class="content-placeholder">暂无内容</p>

            <section class="attachment-section">
              <span class="detail-kicker">附件</span>
              <template v-if="selectedAttachments.length">
                <router-link
                    v-for="attachment in storageAttachments"
                    :key="attachment.fileId"
                    :to="{ name: 'file-preview', query: { fileId: attachment.fileId, fileName: attachment.fileName || attachment.displayName } }"
                    class="attachment-link"
                >
                  <FileText :size="14" stroke-width="1.8"/>
                  <span>{{ attachment.displayName || attachment.fileName }}</span>
                  <small v-if="attachment.sizeBytes">{{ formatFileSize(attachment.sizeBytes) }}</small>
                </router-link>
                <a
                    v-for="(attachment, index) in legacyAttachments"
                    :key="attachment.url || index"
                    :href="attachment.url || '#'"
                    class="attachment-link"
                    rel="noopener noreferrer"
                    target="_blank"
                >
                  <FileText :size="14" stroke-width="1.8"/>
                  <span>{{ attachment.displayName || attachment.fileName || `附件 ${index + 1}` }}</span>
                </a>
              </template>
              <p v-else class="content-placeholder">当前章节暂无附件资料。</p>
            </section>

            <div class="chapter-meta">
              <span><Eye :size="14" stroke-width="1.8"/> {{ selectedChapter.viewCount }} 浏览</span>
              <span><Heart :size="14" stroke-width="1.8"/> {{ selectedChapter.likeCount }} 点赞</span>
            </div>
          </template>
        </article>
      </section>

      <div v-else class="panel-state">
        <p>暂无章节</p>
      </div>
    </template>
  </aside>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {Eye, FileText, Heart, X} from 'lucide-vue-next'

import {getChapter, getChapterTree, viewChapter} from '@/features/course/api/chapter'
import type {Chapter, ChapterAttachment} from '@/features/course/types/chapter'
import type {ClassSession} from '@/features/course/types/classSession'

const props = defineProps<{
  session: ClassSession
}>()

defineEmits<{ close: [] }>()

const chapterTree = ref<Chapter[]>([])
const selectedChapterId = ref<string | null>(null)
const selectedChapter = ref<Chapter | null>(null)
const loading = ref(false)
const loadFailed = ref(false)
const detailLoading = ref(false)

const flatChapters = computed(() => flattenChapters(chapterTree.value))
const selectedAttachments = computed(() => selectedChapter.value?.attachments || [])
const storageAttachments = computed(() => selectedAttachments.value.filter(hasFileId))
const legacyAttachments = computed(() => selectedAttachments.value.filter(attachment => !attachment.fileId && attachment.url))

onMounted(() => {
  void loadChapters()
})

async function loadChapters() {
  loading.value = true
  loadFailed.value = false
  selectedChapter.value = null
  try {
    chapterTree.value = await getChapterTree(props.session.courseId)
    const firstChapter = flatChapters.value.find(chapter => chapter.status === 1) || flatChapters.value[0] || null
    selectedChapterId.value = firstChapter?.id || null
    if (firstChapter) {
      await loadChapter(firstChapter.id)
    }
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

async function selectChapter(chapterId: string) {
  if (chapterId === selectedChapterId.value && selectedChapter.value) {
    return
  }
  selectedChapterId.value = chapterId
  await loadChapter(chapterId)
}

async function loadChapter(chapterId: string) {
  detailLoading.value = true
  try {
    selectedChapter.value = await getChapter(chapterId)
    recordView(chapterId)
  } catch {
    selectedChapter.value = null
  } finally {
    detailLoading.value = false
  }
}

function recordView(chapterId: string) {
  viewChapter(chapterId).then((interaction) => {
    if (selectedChapter.value?.id !== chapterId) {
      return
    }
    selectedChapter.value.viewCount = interaction.viewCount
    selectedChapter.value.likeCount = interaction.likeCount
    selectedChapter.value.likedByMe = interaction.likedByMe
  }).catch(() => {
  })
}

function flattenChapters(chapters: Chapter[]): Chapter[] {
  return chapters.flatMap(chapter => [chapter, ...flattenChapters(chapter.children || [])])
}

function hasFileId(attachment: ChapterAttachment): attachment is ChapterAttachment & { fileId: string } {
  return Boolean(attachment.fileId)
}

function formatIndex(index: number) {
  return String(index).padStart(2, '0')
}

function formatFileSize(sizeBytes: number) {
  if (sizeBytes < 1024) return `${sizeBytes} B`
  if (sizeBytes < 1024 * 1024) return `${Math.round(sizeBytes / 1024)} KB`
  return `${(sizeBytes / 1024 / 1024).toFixed(1)} MB`
}
</script>

<style scoped>
.chapter-preview-panel {
  position: fixed;
  top: 24px;
  right: 24px;
  bottom: 24px;
  z-index: 2200;
  display: flex;
  width: min(560px, calc(100vw - 32px));
  flex-direction: column;
  overflow: hidden;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline);
  border-radius: var(--radius-md);
  color: var(--color-on-surface);
  box-shadow: var(--shadow-card);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  border-bottom: 1px solid var(--color-outline-light);
}

.panel-header span,
.detail-kicker {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
}

.panel-header h2 {
  margin: 4px 0 0;
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 400;
}

.icon-button,
.text-button,
.chapter-row {
  cursor: pointer;
}

.icon-button {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  background: transparent;
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
}

.panel-state {
  display: grid;
  min-height: 140px;
  place-items: center;
  gap: 10px;
  padding: 16px;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
  text-align: center;
}

.panel-state.compact {
  min-height: 80px;
}

.panel-state p {
  margin: 0;
}

.text-button {
  min-height: 32px;
  padding: 0 10px;
  background: transparent;
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
}

.chapter-preview-body {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  min-height: 0;
  overflow: hidden;
}

.chapter-list {
  display: grid;
  max-height: 220px;
  overflow-y: auto;
  border-bottom: 1px solid var(--color-outline-light);
}

.chapter-row {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
  padding: 12px 16px;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
  text-align: left;
}

.chapter-row:last-child {
  border-bottom: 0;
}

.chapter-row:hover,
.chapter-row:focus-visible,
.chapter-row.active {
  background: var(--color-surface-container);
  outline: none;
}

.chapter-row span {
  color: var(--color-muted);
  font-family: var(--font-heading);
  font-size: 18px;
  font-variant-numeric: tabular-nums;
}

.chapter-row strong {
  min-width: 0;
  overflow: hidden;
  font-family: var(--font-body);
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chapter-row small {
  grid-column: 2;
  overflow: hidden;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 12px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chapter-detail {
  display: flex;
  min-height: 0;
  flex-direction: column;
  gap: 14px;
  overflow-y: auto;
  padding: 16px;
}

.chapter-detail h3 {
  margin: 0;
  overflow-wrap: anywhere;
  font-family: var(--font-heading);
  font-size: 26px;
  font-weight: 400;
  line-height: 1.25;
}

.chapter-description,
.content-placeholder {
  margin: 0;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.6;
}

.content-placeholder {
  padding: 12px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
}

.chapter-content {
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.75;
}

.chapter-content :deep(h1),
.chapter-content :deep(h2),
.chapter-content :deep(h3),
.chapter-content :deep(h4) {
  margin: 20px 0 10px;
  font-family: var(--font-body);
  line-height: 1.3;
}

.chapter-content :deep(p) {
  margin: 0 0 14px;
}

.chapter-content :deep(ul),
.chapter-content :deep(ol) {
  margin: 0 0 14px;
  padding-left: 20px;
}

.chapter-content :deep(img) {
  display: block;
  max-width: 100%;
  max-height: 320px;
  object-fit: contain;
  border: 1px solid var(--color-outline-light);
}

.attachment-section {
  display: grid;
  gap: 8px;
  padding-top: 14px;
  border-top: 1px solid var(--color-outline-light);
}

.attachment-link {
  display: inline-flex;
  min-height: 36px;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 13px;
  text-decoration: none;
}

.attachment-link span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-link small {
  margin-left: auto;
  color: var(--color-muted);
  font-size: 11px;
}

.chapter-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding-top: 14px;
  border-top: 1px solid var(--color-outline-light);
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
}

.chapter-meta span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

@media (max-width: 640px) {
  .chapter-preview-panel {
    inset: 12px;
    width: auto;
  }
}
</style>
