<template>
  <section class="tab-panel chapter-navigation-page">
    <header class="chapter-nav-header">
      <div>
        <span class="chapter-kicker">{{ t('courseDetail.chaptersTab') }}</span>
        <h2>{{ t('chapter.title') }}</h2>
        <p>{{ t('chapter.subtitle') }}</p>
      </div>
      <button v-if="canManageCourse" class="btn-add" type="button" @click="emit('openChapterEditor')">
        <Plus :size="14" stroke-width="2"/>
        {{ t('chapter.addChapter') }}
      </button>
    </header>

    <div v-if="chaptersLoading" aria-hidden="true" class="chapter-nav-shell chapter-loading-shell">
      <section class="chapter-ledger-panel">
        <div class="ledger-summary">
          <div v-for="n in 2" :key="n">
            <span class="skeleton-block skeleton-kicker shimmer"></span>
            <strong class="skeleton-block skeleton-count shimmer"></strong>
          </div>
        </div>

        <div class="chapter-ledger">
          <article v-for="n in 4" :key="n" class="chapter-section">
            <div class="chapter-section-head skeleton-row">
              <span class="skeleton-block skeleton-number shimmer"></span>
              <span class="skeleton-stack">
                <span class="skeleton-block skeleton-kicker shimmer"></span>
                <strong class="skeleton-block skeleton-title shimmer"></strong>
                <small class="skeleton-block skeleton-text shimmer"></small>
              </span>
              <span class="skeleton-block skeleton-date shimmer"></span>
            </div>
          </article>
        </div>
      </section>

      <aside class="lesson-preview-panel">
        <div class="preview-sticky">
          <span class="skeleton-block skeleton-kicker shimmer"></span>
          <span class="skeleton-block skeleton-heading shimmer"></span>
          <span class="skeleton-block skeleton-text wide shimmer"></span>
          <span class="skeleton-block skeleton-text shimmer"></span>

          <div class="preview-meta">
            <div v-for="n in 2" :key="n">
              <span class="skeleton-block skeleton-kicker shimmer"></span>
              <span class="skeleton-block skeleton-value shimmer"></span>
            </div>
          </div>

          <div class="resource-block">
            <span class="skeleton-block skeleton-kicker shimmer"></span>
            <span class="skeleton-block skeleton-text wide shimmer"></span>
          </div>

          <div class="preview-actions">
            <span v-for="n in 3" :key="n" class="skeleton-block skeleton-button shimmer"></span>
          </div>
        </div>
      </aside>

      <section class="learning-path">
        <div class="path-header">
          <span class="skeleton-block skeleton-kicker shimmer"></span>
          <strong class="skeleton-block skeleton-path-title shimmer"></strong>
        </div>
        <div class="path-rail">
          <div v-for="n in 4" :key="n" class="path-node skeleton-path-node">
            <span class="skeleton-block skeleton-number shimmer"></span>
            <strong class="skeleton-block skeleton-title shimmer"></strong>
            <small class="skeleton-block skeleton-date shimmer"></small>
          </div>
        </div>
      </section>

    </div>

    <div v-else-if="flatChapters.length > 0" class="chapter-nav-shell">
      <section :aria-label="t('chapter.title')" class="chapter-ledger-panel">
        <div class="ledger-summary">
          <div>
            <span>{{ t('chapter.title') }}</span>
            <strong>{{ chapterTree.length }}</strong>
          </div>
          <div>
            <span>{{ t('chapter.totalViews') }}</span>
            <strong>{{ totalViews }}</strong>
          </div>
        </div>

        <div class="chapter-ledger">
          <article
              v-for="(chapter, chapterIndex) in chapterTree"
              :key="chapter.id"
              :class="{active: isSelected(chapter)}"
              class="chapter-section"
          >
            <button class="chapter-section-head" type="button" @click="previewChapter(chapter)">
              <span class="chapter-index">{{ formatIndex(chapterIndex + 1) }}</span>
              <span class="chapter-heading-copy">
                <span>Chapter {{ chapterIndex + 1 }}</span>
                <strong>{{ chapter.chapterName }}</strong>
                <small v-if="chapter.description">{{ truncateText(chapter.description, 96) }}</small>
              </span>
              <span class="chapter-section-meta">
                <span>{{ formatDate(chapter.updatedAt || chapter.createdAt) }}</span>
                <span v-if="chapter.status !== 1">{{ t('chapter.draft') }}</span>
              </span>
            </button>

            <div v-if="canManageCourse" :aria-label="t('chapter.editChapter')" class="chapter-section-actions">
              <button :title="t('chapter.addSubChapter')" type="button" @click="emit('addChildChapter', chapter)">
                <Plus :size="14" stroke-width="1.8"/>
              </button>
              <button :title="t('chapter.editChapter')" type="button" @click="emit('editChapter', chapter)">
                <Pencil :size="14" stroke-width="1.8"/>
              </button>
              <button :title="t('chapter.deleteChapter')" type="button" @click="emit('deleteChapter', chapter)">
                <Trash2 :size="14" stroke-width="1.8"/>
              </button>
            </div>

            <div v-if="lessonsForChapter(chapter).length > 0" class="lesson-list">
              <div
                  v-for="(lesson, lessonIndex) in lessonsForChapter(chapter)"
                  :key="lesson.id"
                  class="lesson-row-wrap"
              >
                <button
                    :class="[chapterState(lesson), {selected: isSelected(lesson)}]"
                    class="lesson-row"
                    type="button"
                    @click="previewChapter(lesson)"
                >
                  <span class="lesson-state">{{ formatIndex(chapterIndex + 1) }}.{{
                      formatIndex(lessonIndex + 1)
                    }}</span>
                  <span class="lesson-copy">
                    <strong>{{ lesson.chapterName }}</strong>
                    <small v-if="lesson.description">{{ truncateText(lesson.description, 72) }}</small>
                  </span>
                  <span v-if="lesson.status !== 1" class="lesson-status">{{ t('chapter.draft') }}</span>
                </button>

                <div v-if="canManageCourse" class="lesson-actions">
                  <button :title="t('chapter.editChapter')" type="button" @click="emit('editChapter', lesson)">
                    <Pencil :size="13" stroke-width="1.8"/>
                  </button>
                  <button :title="t('chapter.deleteChapter')" type="button" @click="emit('deleteChapter', lesson)">
                    <Trash2 :size="13" stroke-width="1.8"/>
                  </button>
                </div>
              </div>
            </div>
          </article>
        </div>
      </section>

      <aside :aria-label="t('chapter.currentLesson')" class="lesson-preview-panel">
        <div class="preview-sticky">
          <span class="chapter-kicker">{{ t('chapter.currentLesson') }}</span>
          <h3>{{ selectedChapter?.chapterName }}</h3>
          <p>{{ selectedDescription }}</p>

          <dl class="preview-meta">
            <div>
              <dt>{{ t('chapter.updateTime') }}</dt>
              <dd>{{ selectedChapter ? formatDate(selectedChapter.updatedAt || selectedChapter.createdAt) : '-' }}</dd>
            </div>
            <div>
              <dt>{{ t('chapter.interaction') }}</dt>
              <dd>{{
                  selectedChapter ? t('chapter.interactionFormat', {
                    views: selectedChapter.viewCount,
                    likes: selectedChapter.likeCount
                  }) : '-'
                }}
              </dd>
            </div>
          </dl>

          <div class="resource-block">
            <span>{{ t('chapter.resources') }}</span>
            <template v-if="selectedAttachments.length">
              <router-link
                  v-for="attachment in storageAttachments"
                  :key="attachment.fileId"
                  :to="{ name: 'file-preview', query: { fileId: attachment.fileId, fileName: attachment.fileName || attachment.displayName } }"
              >
                <FileText :size="14" stroke-width="1.8"/>
                <span>{{ attachment.displayName || attachment.fileName }}</span>
                <small v-if="attachment.sizeBytes">{{ formatFileSize(attachment.sizeBytes) }}</small>
              </router-link>
              <a
                  v-for="(attachment, index) in legacyAttachments"
                  :key="attachment.url || index"
                  :href="attachment.url || '#'"
                  rel="noopener noreferrer"
                  target="_blank"
              >
                <FileText :size="14" stroke-width="1.8"/>
                <span>{{ attachment.displayName || attachment.fileName || `${t('chapter.attachments')} ${index + 1}` }}</span>
              </a>
            </template>
            <p v-else>{{ t('chapter.noAttachmentsHint') }}</p>
          </div>

          <div class="preview-actions">
            <button :disabled="!previousChapter" class="nav-step" type="button" @click="selectAdjacent(-1)">
              <ChevronLeft :size="14" stroke-width="1.8"/>
              {{ t('chapter.previousLesson') }}
            </button>
            <button
                :disabled="!canOpenSelectedChapter"
                class="open-lesson"
                type="button"
                @click="openSelectedChapter"
            >
              {{ openLessonLabel }}
            </button>
            <button :disabled="!nextChapter" class="nav-step" type="button" @click="selectAdjacent(1)">
              {{ t('chapter.nextLesson') }}
              <ChevronRight :size="14" stroke-width="1.8"/>
            </button>
          </div>
        </div>
      </aside>

      <section :aria-label="t('chapter.learningPath')" class="learning-path">
        <div class="path-header">
          <span class="chapter-kicker">Learning path</span>
          <strong>{{ t('chapter.learningPath') }}</strong>
        </div>
        <div class="path-rail">
          <button
              v-for="(chapter, index) in chapterTree"
              :key="chapter.id"
              :class="{active: isChapterGroupActive(chapter), locked: chapter.status !== 1}"
              class="path-node"
              type="button"
              @click="previewChapter(chapter)"
          >
            <span>{{ formatIndex(index + 1) }}</span>
            <strong>{{ chapter.chapterName }}</strong>
            <small>{{ formatPublishDate(chapter) }}</small>
          </button>
        </div>
      </section>

    </div>

    <div v-else class="empty-chapter-navigation">
      <BookOpen :size="32" stroke-width="1.4"/>
      <p>{{ t('chapter.noChapters') }}</p>
      <span>{{ t('chapter.noChaptersDesc') }}</span>
      <button v-if="canManageCourse" class="btn-add" type="button" @click="emit('openChapterEditor')">
        <Plus :size="14" stroke-width="2"/>
        {{ t('chapter.addChapter') }}
      </button>
    </div>
  </section>
</template>

<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {BookOpen, ChevronLeft, ChevronRight, FileText, Pencil, Plus, Trash2,} from 'lucide-vue-next'
import type {Chapter, ChapterAttachment} from '@/features/course/types/chapter'

const props = defineProps<{
  chapterTree: Chapter[]
  chaptersLoading?: boolean
  canManageCourse: boolean
  canAccessCourseContent: boolean
}>()

const emit = defineEmits<{
  openChapterEditor: []
  editChapter: [chapter: Chapter]
  deleteChapter: [chapter: Chapter]
  addChildChapter: [chapter: Chapter]
  selectChapter: [chapter: Chapter]
}>()

const {t} = useI18n()
const selectedChapterId = ref<string | null>(null)

function flattenChapters(chapters: Chapter[]): Chapter[] {
  return chapters.flatMap(chapter => [chapter, ...flattenChapters(chapter.children || [])])
}

const flatChapters = computed(() => flattenChapters(props.chapterTree))
const totalViews = computed(() => flatChapters.value.reduce((sum, chapter) => sum + (chapter.viewCount || 0), 0))
const firstChapter = computed(() => flatChapters.value.find(chapter => chapter.status === 1) || flatChapters.value[0] || null)
const selectedChapter = computed(() => {
  return flatChapters.value.find(chapter => chapter.id === selectedChapterId.value) || firstChapter.value
})
const selectedIndex = computed(() => {
  if (!selectedChapter.value) return -1
  return flatChapters.value.findIndex(chapter => chapter.id === selectedChapter.value?.id)
})
const previousChapter = computed(() => selectedIndex.value > 0 ? flatChapters.value[selectedIndex.value - 1] : null)
const nextChapter = computed(() => selectedIndex.value >= 0 ? flatChapters.value[selectedIndex.value + 1] || null : null)
const canOpenSelectedChapter = computed(() => {
  if (!selectedChapter.value || !props.canAccessCourseContent) return false
  return selectedChapter.value.status === 1 || props.canManageCourse
})
const openLessonLabel = computed(() => {
  if (!props.canAccessCourseContent) return t('chapter.enrollToAccess')
  if (selectedChapter.value?.status !== 1 && !props.canManageCourse) return t('chapter.notOpen')
  return t('chapter.enterLesson')
})
const selectedDescription = computed(() => {
  if (!selectedChapter.value) return t('chapter.selectLessonHint')
  return selectedChapter.value.description || t('chapter.noDescriptionHint')
})
const selectedAttachments = computed(() => selectedChapter.value?.attachments || [])
const storageAttachments = computed(() => selectedAttachments.value.filter(hasFileId))
const legacyAttachments = computed(() => selectedAttachments.value.filter(attachment => !attachment.fileId && attachment.url))

function hasFileId(attachment: ChapterAttachment): attachment is ChapterAttachment & { fileId: string } {
  return Boolean(attachment.fileId)
}

watch(flatChapters, (chapters) => {
  if (chapters.length === 0) {
    selectedChapterId.value = null
    return
  }
  if (!selectedChapterId.value || !chapters.some(chapter => chapter.id === selectedChapterId.value)) {
    selectedChapterId.value = firstChapter.value?.id || null
  }
}, {immediate: true})

function previewChapter(chapter: Chapter) {
  selectedChapterId.value = chapter.id
}

function openSelectedChapter() {
  if (!selectedChapter.value || !canOpenSelectedChapter.value) return
  emit('selectChapter', selectedChapter.value)
}

function selectAdjacent(offset: -1 | 1) {
  const target = offset === -1 ? previousChapter.value : nextChapter.value
  if (!target) return
  selectedChapterId.value = target.id
}

function lessonsForChapter(chapter: Chapter) {
  return flattenChapters(chapter.children || [])
}


function isSelected(chapter: Chapter) {
  return selectedChapter.value?.id === chapter.id
}

function isChapterGroupActive(chapter: Chapter) {
  if (!selectedChapter.value) return false
  if (chapter.id === selectedChapter.value.id) return true
  return Boolean(flattenChapters(chapter.children || []).some(child => child.id === selectedChapter.value?.id))
}

function chapterState(chapter: Chapter) {
  if (chapter.status !== 1) return 'locked'
  if (chapter.id === selectedChapter.value?.id) return 'current'
  return 'available'
}


function truncateText(text: string, maxLength: number) {
  return text.length > maxLength ? `${text.slice(0, maxLength)}...` : text
}

function formatDate(dateStr: string) {
  const date = new Date(dateStr)
  if (Number.isNaN(date.getTime())) return '-'

  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function formatPublishDate(chapter: Chapter) {
  return formatDate(chapter.updatedAt || chapter.createdAt)
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
.chapter-navigation-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.chapter-nav-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-md);
  padding-bottom: var(--space-md);
  border-bottom: 1px solid var(--color-outline-light);
}

.chapter-nav-header h2 {
  margin: var(--space-xs) 0 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 400;
  line-height: 1.3;
}

.chapter-nav-header p {
  max-width: 58ch;
  margin: var(--space-xs) 0 0;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.5;
}

.chapter-kicker,
.ledger-summary span,
.chapter-heading-copy > span,
.chapter-section-meta,
.lesson-copy small,
.lesson-status,
.preview-meta dt,
.resource-block > span,
.path-header span,
.path-node small {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.05em;
  line-height: 1;
  text-transform: uppercase;
}

.chapter-nav-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: var(--space-md);
  align-items: start;
}

.chapter-loading-shell {
  pointer-events: none;
}

.chapter-ledger-panel,
.lesson-preview-panel,
.learning-path {
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 0;
}

.chapter-ledger-panel {
  min-width: 0;
}

.ledger-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(112px, 148px));
  justify-content: start;
  border-bottom: 1px solid var(--color-outline-light);
}

.ledger-summary div {
  min-width: 0;
  padding: var(--space-sm) var(--space-md);
  border-right: 1px solid var(--color-outline-light);
}

.ledger-summary div:last-child {
  border-right: 0;
}

.ledger-summary span {
  display: block;
  margin-bottom: var(--space-xs);
  white-space: nowrap;
}

.ledger-summary strong {
  display: block;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 24px;
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  line-height: 1;
}

.chapter-ledger {
  display: flex;
  flex-direction: column;
}

.chapter-section {
  position: relative;
  border-bottom: 1px solid var(--color-outline-light);
}

.chapter-section:last-child {
  border-bottom: 0;
}

.chapter-section.active::before {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 2px;
  background: var(--color-primary);
  content: '';
}

.chapter-section.active::after {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 2px;
  background: var(--color-primary);
  content: '';
}

.chapter-section-head,
.lesson-row,
.path-node {
  width: 100%;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.chapter-section-head {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr) auto;
  gap: var(--space-sm);
  align-items: start;
  padding: var(--space-md) var(--space-md) var(--space-sm);
  transition: background 0.2s ease;
}

.chapter-section-head:hover,
.chapter-section-head:focus-visible,
.lesson-row:hover,
.lesson-row:focus-visible,
.path-node:hover,
.path-node:focus-visible {
  background: var(--color-surface-container);
  outline: none;
}

.chapter-index {
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 28px;
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.chapter-heading-copy {
  min-width: 0;
}

.chapter-heading-copy strong {
  display: block;
  margin-top: var(--space-xs);
  overflow-wrap: anywhere;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 17px;
  font-weight: 700;
  line-height: 1.25;
}

.chapter-heading-copy small {
  display: block;
  margin-top: var(--space-xs);
  color: var(--color-on-surface-variant);
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 400;
  letter-spacing: 0;
  line-height: 1.45;
  text-transform: none;
}

.chapter-section-meta {
  display: grid;
  gap: var(--space-xs);
  justify-items: end;
  min-width: 82px;
  text-align: right;
}

.chapter-section-actions,
.lesson-actions {
  display: flex;
  gap: var(--space-xs);
}

.chapter-section-actions {
  position: absolute;
  top: var(--space-sm);
  right: var(--space-md);
  opacity: 0;
  transition: opacity 0.2s ease;
}

.chapter-section-head:hover + .chapter-section-actions,
.chapter-section-head:focus-visible + .chapter-section-actions,
.chapter-section-actions:hover,
.chapter-section-actions:focus-within,
.lesson-row-wrap:hover .lesson-actions,
.lesson-row-wrap:focus-within .lesson-actions {
  opacity: 1;
}

.chapter-section-actions button,
.lesson-actions button {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  padding: 0;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-muted);
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.chapter-section-actions button:hover,
.lesson-actions button:hover {
  background: var(--color-surface-container-high);
  color: var(--color-on-surface);
}

.skeleton-block {
  display: block;
  border-radius: var(--radius-sm);
  background: var(--color-surface-container-high);
}

.skeleton-kicker {
  width: 96px;
  height: 12px;
}

.skeleton-count {
  width: 56px;
  height: 24px;
}

.skeleton-row {
  align-items: center;
  cursor: default;
}

.skeleton-stack {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: var(--space-xs);
}

.skeleton-number {
  width: 32px;
  height: 32px;
  border-radius: 50%;
}

.skeleton-title {
  width: min(240px, 100%);
  height: 20px;
}

.skeleton-heading {
  width: min(260px, 100%);
  height: 28px;
}

.skeleton-text {
  width: min(300px, 100%);
  height: 14px;
}

.skeleton-text.wide {
  width: 100%;
}

.skeleton-date {
  width: 72px;
  height: 12px;
  justify-self: end;
}

.skeleton-value,
.skeleton-path-title {
  width: min(140px, 100%);
  height: 16px;
}

.skeleton-button {
  width: 100%;
  height: 38px;
}

.skeleton-path-node {
  cursor: default;
  gap: var(--space-xs);
}

.lesson-list {
  display: flex;
  flex-direction: column;
  padding: 0 var(--space-md) var(--space-md) calc(var(--space-2xl) + var(--space-md));
}

.lesson-row-wrap {
  position: relative;
  min-width: 0;
  border-top: 1px solid var(--color-outline-light);
}

.lesson-row {
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr) auto;
  gap: var(--space-sm);
  align-items: center;
  min-height: 48px;
  padding: var(--space-xs) 72px var(--space-xs) var(--space-sm);
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}

.lesson-row.selected {
  background: var(--color-surface-container);
}

.lesson-row.current {
  box-shadow: inset 2px 0 0 var(--color-primary), inset 0 2px 0 var(--color-primary);
}

.lesson-row.locked {
  color: var(--color-muted);
  cursor: default;
  opacity: 0.68;
}

.lesson-state {
  display: grid;
  place-items: center;
  color: var(--color-muted);
  font-family: var(--font-heading);
  font-size: 20px;
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.lesson-row.available .lesson-state,
.lesson-row.current .lesson-state {
  color: var(--color-on-surface);
}

.lesson-copy {
  min-width: 0;
}

.lesson-copy strong {
  display: block;
  overflow: hidden;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
  font-weight: 400;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lesson-copy small {
  display: block;
  margin-top: var(--space-xs);
  overflow: hidden;
  color: var(--color-on-surface-variant);
  font-family: var(--font-body);
  font-size: 12px;
  font-weight: 400;
  letter-spacing: 0;
  line-height: 1.35;
  text-overflow: ellipsis;
  text-transform: none;
  white-space: nowrap;
}

.lesson-status {
  text-align: right;
  white-space: nowrap;
}

.lesson-actions {
  position: absolute;
  top: 50%;
  right: var(--space-xs);
  transform: translateY(-50%);
  opacity: 0;
}

.lesson-preview-panel {
  min-width: 0;
}

.preview-sticky {
  position: sticky;
  top: 88px;
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  padding: var(--space-md);
}

.preview-sticky h3 {
  margin: var(--space-xs) 0 0;
  overflow-wrap: anywhere;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 26px;
  font-weight: 400;
  line-height: 1.25;
}

.preview-sticky p {
  margin: 0;
  color: var(--color-on-surface-variant);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.6;
}

.preview-meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  margin: 0;
  border-top: 1px solid var(--color-outline-light);
  border-left: 1px solid var(--color-outline-light);
}

.preview-meta div {
  min-width: 0;
  padding: var(--space-sm);
  border-right: 1px solid var(--color-outline-light);
  border-bottom: 1px solid var(--color-outline-light);
}

.preview-meta dt,
.preview-meta dd {
  margin: 0;
}

.preview-meta dd {
  margin-top: var(--space-xs);
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  line-height: 1.35;
}

.resource-block {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  padding-top: var(--space-md);
  border-top: 1px solid var(--color-outline-light);
}

.resource-block a {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  min-height: 36px;
  padding: 0 var(--space-sm);
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 13px;
  text-decoration: none;
  transition: background 0.2s ease, border-color 0.2s ease;
}

.resource-block a span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resource-block a small {
  margin-left: auto;
  color: var(--color-muted);
  font-size: 11px;
}

.resource-block a:hover {
  background: var(--color-surface-container);
  border-color: var(--color-outline);
}

.preview-actions {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--space-sm);
}

.nav-step,
.open-lesson,
.btn-add {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 38px;
  padding: 0 var(--space-sm);
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 800;
  text-decoration: none;
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.btn-add,
.nav-step {
  background: transparent;
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
}

.open-lesson {
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  color: var(--color-on-primary);
}

.btn-add:hover,
.nav-step:hover:not(:disabled) {
  background: var(--color-surface-container-high);
  border-color: var(--color-outline);
}

.open-lesson:hover:not(:disabled) {
  background: var(--color-primary-soft);
  border-color: var(--color-primary-soft);
}

.btn-add:active,
.nav-step:active,
.open-lesson:active,
.chapter-section-actions button:active,
.lesson-actions button:active {
  transform: translateY(1px);
}

.nav-step:disabled,
.open-lesson:disabled {
  cursor: not-allowed;
  opacity: 0.46;
}

.learning-path {
  grid-column: 1 / -1;
  padding: var(--space-md);
}

.path-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-md);
  margin-bottom: var(--space-sm);
}

.path-header strong {
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
  font-weight: 700;
}

.path-rail {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(136px, 1fr));
  border-top: 1px solid var(--color-outline-light);
  border-left: 1px solid var(--color-outline-light);
}

.path-node {
  display: grid;
  gap: var(--space-xs);
  min-height: 108px;
  padding: var(--space-sm);
  border-right: 1px solid var(--color-outline-light);
  border-bottom: 1px solid var(--color-outline-light);
  transition: background 0.2s ease, box-shadow 0.2s ease;
}

.path-node.active {
  background: var(--color-surface-container);
  box-shadow: inset 0 2px 0 var(--color-primary);
}

.path-node.locked {
  opacity: 0.64;
}

.path-node span {
  color: var(--color-muted);
  font-family: var(--font-heading);
  font-size: 20px;
  font-variant-numeric: tabular-nums;
}

.path-node strong {
  overflow: hidden;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 700;
  line-height: 1.3;
  text-overflow: ellipsis;
}

.empty-chapter-navigation {
  display: flex;
  min-height: 320px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-sm);
  padding: var(--space-xl) var(--space-md);
  border: 1px solid var(--color-outline-light);
  color: var(--color-muted);
  text-align: center;
}

.empty-chapter-navigation p,
.empty-chapter-navigation span {
  margin: 0;
  font-family: var(--font-body);
  font-size: 15px;
}

.empty-chapter-navigation p {
  color: var(--color-on-surface);
  font-weight: 700;
}

.shimmer {
  position: relative;
  overflow: hidden;
}

.shimmer::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(
      90deg,
      transparent 0%,
      var(--color-surface-card) 40%,
      var(--color-surface-card) 60%,
      transparent 100%
  );
  animation: shimmer 1.4s ease-in-out infinite;
}

@keyframes shimmer {
  0% {
    transform: translateX(-100%);
  }

  100% {
    transform: translateX(100%);
  }
}

@media (max-width: 1180px) {
  .chapter-nav-shell {
    grid-template-columns: 1fr;
  }

  .preview-sticky {
    position: static;
  }
}

@media (max-width: 760px) {
  .chapter-nav-header {
    align-items: stretch;
    flex-direction: column;
  }

  .ledger-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .chapter-section-head {
    grid-template-columns: 42px minmax(0, 1fr);
  }

  .chapter-section-meta {
    grid-column: 2;
    justify-items: start;
    text-align: left;
  }

  .chapter-section-actions {
    position: static;
    padding: 0 var(--space-md) var(--space-sm) calc(var(--space-xl) + var(--space-sm));
    opacity: 1;
  }

  .lesson-list {
    padding-left: var(--space-md);
  }

  .lesson-row {
    grid-template-columns: 58px minmax(0, 1fr);
  }

  .lesson-status {
    grid-column: 2;
    justify-content: flex-start;
    text-align: left;
  }

  .lesson-actions {
    opacity: 1;
  }

  .preview-meta {
    grid-template-columns: 1fr;
  }

}
</style>
