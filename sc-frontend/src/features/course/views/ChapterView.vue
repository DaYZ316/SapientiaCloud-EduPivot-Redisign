<template>
  <div class="chapter-view">
    <header class="chapter-command-row">
      <button class="back-link" type="button" @click="router.push('/courses/' + courseId + '/chapters')">
        <ArrowLeft :size="16" stroke-width="1.8"/>
        {{ t('courseDetail.backToCourses') }}
      </button>

      <div class="chapter-breadcrumb">
        <span>{{ t('courseDetail.chaptersTab') }}</span>
        <span>/</span>
        <strong>{{ chapter?.chapterName || t('chapter.chapterContent') }}</strong>
      </div>

      <div class="chapter-command-tools">
        <div v-if="canManageCourse" class="chapter-command-actions">
          <button type="button" @click="openChapterEditor">
            <Plus :size="14" stroke-width="1.8"/>
            {{ t('chapter.addChapter') }}
          </button>
          <button v-if="chapter" type="button" @click="handleEditChapter(chapter)">
            <Pencil :size="14" stroke-width="1.8"/>
            {{ t('chapter.editChapter') }}
          </button>
        </div>

        <div v-if="chapter" class="command-metrics">
          <span :class="{draft: chapter.status !== 1}" class="status-chip">{{ statusLabel }}</span>
          <span><Eye :size="14" stroke-width="1.8"/> {{ chapter.viewCount }} {{ t('chapter.viewCount') }}</span>
          <span><Heart :size="14" stroke-width="1.8"/> {{ chapter.likeCount }} {{ t('chapter.likeCount') }}</span>
        </div>
      </div>
    </header>

    <div v-if="loading" class="loading-state">
      <aside aria-hidden="true" class="skeleton-panel sidebar-skeleton">
        <span class="skeleton-line short shimmer"></span>
        <span v-for="n in 6" :key="n" class="skeleton-row shimmer"></span>
      </aside>
      <main aria-hidden="true" class="skeleton-panel article-skeleton">
        <span class="skeleton-line short shimmer"></span>
        <span class="skeleton-title shimmer"></span>
        <span class="skeleton-line shimmer"></span>
        <span class="skeleton-line shimmer"></span>
        <span class="skeleton-block shimmer"></span>
      </main>
      <aside aria-hidden="true" class="skeleton-panel rail-skeleton">
        <span class="skeleton-line short shimmer"></span>
        <span class="skeleton-row shimmer"></span>
        <span class="skeleton-row shimmer"></span>
        <span class="skeleton-row shimmer"></span>
      </aside>
    </div>

    <div v-else-if="chapter" class="chapter-layout">
      <aside :aria-label="t('chapter.directory')" class="chapter-sidebar">
        <div class="panel-heading">
          <span>{{ t('chapter.directory') }}</span>
          <strong>{{ flatChapters.length }}</strong>
        </div>
        <ChapterTree
            :active-chapter-id="chapterId"
            :chapters="chapterTree"
            @select="handleSelectChapter"
        />
      </aside>

      <main class="chapter-main">
        <ChapterContent
            :chapter="chapter"
            @like-toggle="handleLikeToggle"
        />
      </main>

      <aside :aria-label="t('chapter.studyTools')" class="study-rail">
        <div class="study-rail-sticky">
          <section class="rail-section">
            <h2>{{ chapter.chapterName }}</h2>
            <p>{{ chapter.description || t('chapter.noDescriptionHint') }}</p>
          </section>

          <section class="rail-section">
            <div class="progress-header">
              <span class="rail-kicker">{{ t('chapter.readingProgress') }}</span>
              <strong>{{ chapterPosition }}</strong>
            </div>
            <div class="progress-track">
              <span :style="{width: readingProgress + '%'}"></span>
            </div>
          </section>

          <dl class="rail-metadata">
            <div>
              <dt>{{ t('chapter.updatedAt') }}</dt>
              <dd>{{ updatedLabel }}</dd>
            </div>
            <div>
              <dt>{{ t('chapter.attachments') }}</dt>
              <dd>{{ attachmentCount }}</dd>
            </div>
          </dl>

          <div class="rail-actions">
            <button v-if="canManageCourse" type="button" @click="handleAddChildChapter(chapter)">
              <Plus :size="16" stroke-width="1.8"/>
              {{ t('chapter.addSubChapter') }}
            </button>
            <button v-if="canManageCourse" type="button" @click="handleEditChapter(chapter)">
              <Pencil :size="16" stroke-width="1.8"/>
              {{ t('chapter.editChapter') }}
            </button>
            <button type="button" @click="goToDiscussion">
              <MessageCircle :size="16" stroke-width="1.8"/>
              {{ t('chapter.discussionArea') }}
            </button>
            <button :disabled="attachmentCount === 0" type="button" @click="scrollToAttachments">
              <Paperclip :size="16" stroke-width="1.8"/>
              {{ t('chapter.jumpToAttachments') }}
            </button>
            <button class="primary-rail-action" type="button" @click="handleLikeToggle">
              <Heart :fill="chapter.likedByMe ? 'currentColor' : 'none'" :size="16" stroke-width="1.8"/>
              {{ chapter.likedByMe ? t('chapter.liked') : t('chapter.likeLesson') }}
            </button>
          </div>
        </div>
      </aside>
    </div>

    <div v-else class="empty-chapter-detail">
      <BookOpen :size="34" stroke-width="1.4"/>
      <p>{{ t('chapter.noContent') }}</p>
      <span>{{ t('chapter.selectLessonHint') }}</span>
      <button v-if="canManageCourse" class="empty-action" type="button" @click="openChapterEditor">
        <Plus :size="15" stroke-width="1.8"/>
        {{ t('chapter.addChapter') }}
      </button>
    </div>

    <ChapterEditor
        :chapter="editingChapter"
        :course-id="courseId"
        :parent-chapter-id="parentChapterId"
        :visible="showChapterEditor"
        @close="closeChapterEditor"
        @save="handleSaveChapter"
    />
  </div>
</template>

<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {ArrowLeft, BookOpen, Eye, Heart, MessageCircle, Paperclip, Pencil, Plus} from 'lucide-vue-next'
import {
  createChapter,
  getChapter,
  getChapterTree,
  likeChapter,
  unlikeChapter,
  updateChapter,
  viewChapter,
} from '@/features/course/api/chapter'
import {getCourse} from '@/features/course/api/course'
import type {
  Chapter,
  ChapterInteraction,
  CreateChapterRequest,
  UpdateChapterRequest
} from '@/features/course/types/chapter'
import type {CourseDetail} from '@/features/course/types/course'
import {useAuthStore} from '@/features/auth/stores/auth'
import ChapterContent from '@/features/course/components/ChapterContent.vue'
import ChapterEditor from '@/features/course/components/ChapterEditor.vue'
import ChapterTree from '@/features/course/components/ChapterTree.vue'
import {notify} from '@/shared/composables/useGlobalNotification'

const {t, locale} = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const loading = ref(true)
const chapter = ref<Chapter | null>(null)
const chapterTree = ref<Chapter[]>([])
const course = ref<CourseDetail | null>(null)
const courseId = ref(String(route.params.courseId || ''))
const chapterId = ref<string | null>(normalizeQueryValue(route.query.chapterId))
const showChapterEditor = ref(false)
const editingChapter = ref<Chapter | null>(null)
const parentChapterId = ref<string | null>(null)
const handledCreateQueryKey = ref<string | null>(null)

function flattenChapters(chapters: Chapter[]): Chapter[] {
  return chapters.flatMap(item => [item, ...flattenChapters(item.children || [])])
}

const flatChapters = computed(() => flattenChapters(chapterTree.value))
const currentIndex = computed(() => flatChapters.value.findIndex(item => item.id === chapterId.value))
const attachmentCount = computed(() => chapter.value?.attachments?.length || 0)
const statusLabel = computed(() => chapter.value?.status === 1 ? t('chapter.published') : t('chapter.draft'))
const isAdmin = computed(() => authStore.user?.role === 0)
const canManageCourse = computed(() => {
  const userId = authStore.user?.id
  if (!userId || !course.value) return isAdmin.value
  return isAdmin.value || course.value.teacherId === userId || Boolean(course.value.teacherIds?.includes(userId))
})
const chapterPosition = computed(() => {
  if (currentIndex.value < 0 || flatChapters.value.length === 0) return '-'
  return `${currentIndex.value + 1}/${flatChapters.value.length}`
})
const readingProgress = computed(() => {
  if (currentIndex.value < 0 || flatChapters.value.length === 0) return 0
  return Math.round(((currentIndex.value + 1) / flatChapters.value.length) * 100)
})
const updatedLabel = computed(() => formatDate(chapter.value?.updatedAt || chapter.value?.createdAt))

watch(
    () => [route.params.courseId, route.query.chapterId],
    () => {
      const nextCourseId = String(route.params.courseId || '')
      if (!nextCourseId) return
      courseId.value = nextCourseId
      void loadChapterDetail(nextCourseId, normalizeQueryValue(route.query.chapterId))
    },
    {immediate: true}
)

watch(
    () => route.query.create,
    () => {
      if (!loading.value) openChapterEditorFromQuery()
    }
)

async function loadChapterDetail(nextCourseId: string, requestedChapterId: string | null) {
  loading.value = true
  try {
    const [courseData, treeData] = await Promise.all([
      getCourse(nextCourseId),
      getChapterTree(nextCourseId),
    ])
    course.value = courseData
    chapterTree.value = treeData || []
    openChapterEditorFromQuery()

    const targetChapterId = requestedChapterId || flatChapters.value[0]?.id || null
    chapterId.value = targetChapterId

    if (!targetChapterId) {
      chapter.value = null
      return
    }

    chapter.value = await getChapter(targetChapterId)
    recordView(targetChapterId)
  } catch {
    course.value = null
    chapter.value = null
  } finally {
    loading.value = false
  }
}

function recordView(targetChapterId: string) {
  viewChapter(targetChapterId).then((interaction: ChapterInteraction) => {
    if (chapter.value && chapter.value.id === targetChapterId) {
      chapter.value.viewCount = interaction.viewCount
      chapter.value.likeCount = interaction.likeCount
      chapter.value.likedByMe = interaction.likedByMe
    }
  }).catch(() => {
  })
}

async function handleLikeToggle() {
  if (!chapter.value) return
  try {
    const interaction = chapter.value.likedByMe
        ? await unlikeChapter(chapter.value.id)
        : await likeChapter(chapter.value.id)
    chapter.value.likeCount = interaction.likeCount
    chapter.value.likedByMe = interaction.likedByMe
  } catch {
    // Keep the server response as the source of truth.
  }
}

function handleSelectChapter(nextChapter: Chapter) {
  if (nextChapter.id === chapterId.value) return
  void loadChapter(nextChapter.id)
}

async function loadChapter(targetChapterId: string) {
  chapterId.value = targetChapterId
  try {
    chapter.value = await getChapter(targetChapterId)
    recordView(targetChapterId)
  } catch {
    chapter.value = null
  }
}

function openChapterEditor() {
  editingChapter.value = null
  parentChapterId.value = null
  showChapterEditor.value = true
}

function openChapterEditorFromQuery() {
  const createQuery = normalizeQueryValue(route.query.create)
  if (createQuery !== '1') {
    handledCreateQueryKey.value = null
    return
  }

  const requestKey = `${courseId.value}:create`
  if (!canManageCourse.value || handledCreateQueryKey.value === requestKey) return
  handledCreateQueryKey.value = requestKey
  openChapterEditor()
}

function handleEditChapter(targetChapter: Chapter) {
  editingChapter.value = targetChapter
  parentChapterId.value = null
  showChapterEditor.value = true
}

function handleAddChildChapter(targetChapter: Chapter) {
  parentChapterId.value = targetChapter.id
  editingChapter.value = null
  showChapterEditor.value = true
}

function closeChapterEditor() {
  showChapterEditor.value = false
  editingChapter.value = null
  parentChapterId.value = null
}

async function handleSaveChapter(data: CreateChapterRequest | UpdateChapterRequest) {
  try {
    let targetChapterId = editingChapter.value?.id || chapterId.value
    if (editingChapter.value) {
      await updateChapter(editingChapter.value.id, data as UpdateChapterRequest)
      notify.success(t('courseDetail.alert.updateChapterSuccess'))
    } else {
      targetChapterId = await createChapter(data as CreateChapterRequest)
      notify.success(t('courseDetail.alert.createChapterSuccess'))
    }
    closeChapterEditor()
    await refreshChapterTree(targetChapterId)
  } catch {
    notify.error(t('courseDetail.alert.saveChapterFailed'))
  }
}

async function refreshChapterTree(preferredChapterId: string | null = chapterId.value) {
  const treeData = await getChapterTree(courseId.value)
  chapterTree.value = treeData || []
  const targetChapterId = preferredChapterId && flatChapters.value.some(item => item.id === preferredChapterId)
      ? preferredChapterId
      : flatChapters.value[0]?.id || null
  chapterId.value = targetChapterId
  chapter.value = targetChapterId ? await getChapter(targetChapterId) : null
  if (targetChapterId) recordView(targetChapterId)
}

function goToDiscussion() {
  router.push('/courses/' + courseId.value + '/forums')
}

function scrollToAttachments() {
  document.getElementById('chapter-attachments')?.scrollIntoView({
    behavior: 'smooth',
    block: 'start',
  })
}

function normalizeQueryValue(value: unknown) {
  if (Array.isArray(value)) return typeof value[0] === 'string' ? value[0] : null
  return typeof value === 'string' && value.length > 0 ? value : null
}

function formatDate(dateStr?: string | null) {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  if (Number.isNaN(date.getTime())) return '-'
  return new Intl.DateTimeFormat(String(locale.value), {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}
</script>

<style scoped>
.chapter-view {
  width: min(100%, 1440px);
  margin: 0 auto;
  color: var(--color-on-surface);
}

.chapter-command-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 18px;
  align-items: center;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-outline-light);
}

.back-link,
.chapter-command-actions button,
.command-metrics span,
.empty-action,
.status-chip,
.rail-actions button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  font-family: var(--font-label);
  line-height: 1;
}

.back-link {
  min-height: 36px;
  padding: 0 12px;
  border: 1px solid var(--color-outline-light);
  border-radius: 0;
  background: transparent;
  color: var(--color-on-surface);
  font-size: 12px;
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease, transform 0.2s ease;
}

.back-link:hover,
.back-link:focus-visible {
  background: var(--color-surface-container);
  border-color: var(--color-outline);
  color: var(--color-on-surface);
  outline: none;
}

.back-link:active,
.chapter-command-actions button:active,
.empty-action:active,
.rail-actions button:active {
  transform: translateY(1px);
}

.chapter-breadcrumb {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
}

.chapter-breadcrumb strong {
  min-width: 0;
  overflow: hidden;
  color: var(--color-on-surface);
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chapter-command-tools {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.chapter-command-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.chapter-command-actions button,
.empty-action {
  min-height: 30px;
  padding: 0 10px;
  border: 1px solid var(--color-outline-light);
  border-radius: 0;
  background: transparent;
  color: var(--color-on-surface);
  cursor: pointer;
  font-size: 12px;
  transition: background 0.2s ease, border-color 0.2s ease, transform 0.2s ease;
}

.chapter-command-actions button:hover,
.chapter-command-actions button:focus-visible,
.empty-action:hover,
.empty-action:focus-visible {
  background: var(--color-surface-container);
  border-color: var(--color-outline);
  outline: none;
}

.command-metrics {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.command-metrics span,
.status-chip {
  min-height: 30px;
  padding: 0 10px;
  border: 1px solid var(--color-outline-light);
  color: var(--color-muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.status-chip {
  color: var(--color-on-surface);
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.status-chip.draft {
  color: var(--color-muted);
  background: var(--color-surface-container);
}

.chapter-layout,
.loading-state {
  display: grid;
  grid-template-columns: minmax(240px, 280px) minmax(0, 1fr) minmax(240px, 280px);
  gap: 24px;
  align-items: start;
}

.chapter-sidebar,
.chapter-main,
.study-rail,
.skeleton-panel,
.empty-chapter-detail {
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 0;
}

.chapter-sidebar {
  position: sticky;
  top: 24px;
  min-width: 0;
  height: fit-content;
  overflow: hidden;
}

.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
}

.panel-heading span,
.rail-metadata dt {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
  font-weight: 400;
  letter-spacing: 0.08em;
  line-height: 1;
  text-transform: uppercase;
}

.panel-heading strong {
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 18px;
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.chapter-main {
  min-width: 0;
  padding: 32px;
}

.study-rail {
  min-width: 0;
}

.study-rail-sticky {
  position: sticky;
  top: 24px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 18px;
}

.rail-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.rail-section + .rail-section {
  padding-top: 18px;
  border-top: 1px solid var(--color-outline-light);
}

.rail-section h2 {
  margin: 0;
  overflow-wrap: anywhere;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 400;
  line-height: 1.25;
}

.rail-section p {
  margin: 0;
  color: var(--color-on-surface-variant);
  font-family: var(--font-body);
  font-size: 13px;
  line-height: 1.6;
}

.progress-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.progress-header strong {
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.progress-track {
  height: 3px;
  overflow: hidden;
  background: var(--color-surface-container-high);
}

.progress-track span {
  display: block;
  height: 100%;
  background: var(--color-primary);
}

.rail-metadata {
  display: grid;
  grid-template-columns: 1fr 1fr;
  margin: 0;
  border-top: 1px solid var(--color-outline-light);
  border-left: 1px solid var(--color-outline-light);
}

.rail-metadata div {
  min-width: 0;
  padding: 12px;
  border-right: 1px solid var(--color-outline-light);
  border-bottom: 1px solid var(--color-outline-light);
}

.rail-metadata dt,
.rail-metadata dd {
  margin: 0;
}

.rail-metadata dd {
  margin-top: 8px;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.rail-actions {
  display: grid;
  gap: 10px;
}

.rail-actions button {
  min-height: 40px;
  width: 100%;
  padding: 0 12px;
  border: 1px solid var(--color-outline-light);
  border-radius: 0;
  background: transparent;
  color: var(--color-on-surface);
  cursor: pointer;
  font-size: 13px;
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease, opacity 0.2s ease, transform 0.2s ease;
}

.rail-actions button:hover:not(:disabled),
.rail-actions button:focus-visible:not(:disabled) {
  background: var(--color-surface-container);
  border-color: var(--color-outline);
  outline: none;
}

.rail-actions button:disabled {
  cursor: not-allowed;
  opacity: 0.42;
}

.rail-actions .primary-rail-action {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-on-primary);
}

.rail-actions .primary-rail-action:hover:not(:disabled),
.rail-actions .primary-rail-action:focus-visible:not(:disabled) {
  background: var(--color-primary-soft);
  border-color: var(--color-primary-soft);
  color: var(--color-on-primary);
}

.skeleton-panel {
  display: flex;
  min-height: 320px;
  flex-direction: column;
  gap: 14px;
  padding: 18px;
}

.article-skeleton {
  min-height: 560px;
  padding: 32px;
}

.rail-skeleton {
  min-height: 260px;
}

.skeleton-line,
.skeleton-row,
.skeleton-title,
.skeleton-block {
  display: block;
  background: var(--color-surface-container-high);
  border-radius: 0;
}

.skeleton-line {
  height: 18px;
  width: min(100%, 680px);
}

.skeleton-line.short {
  width: 132px;
}

.skeleton-row {
  height: 48px;
}

.skeleton-block {
  width: 100%;
  height: 300px;
}

.skeleton-title {
  width: min(100%, 680px);
  height: 92px;
}

.empty-chapter-detail {
  display: flex;
  min-height: 360px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 48px 24px;
  color: var(--color-muted);
  text-align: center;
}

.empty-chapter-detail p,
.empty-chapter-detail span {
  margin: 0;
  font-family: var(--font-body);
}

.empty-chapter-detail p {
  color: var(--color-on-surface);
  font-size: 16px;
  font-weight: 600;
}

.empty-chapter-detail span {
  max-width: 42ch;
  font-size: 14px;
  line-height: 1.5;
}

.empty-action {
  margin-top: 8px;
  min-height: 38px;
  padding: 0 14px;
}

.shimmer {
  background: linear-gradient(110deg, var(--color-surface-container-high) 8%, var(--color-surface-canvas) 18%, var(--color-surface-container-high) 33%);
  background-size: 200% 100%;
  animation: shimmer 1.4s ease-in-out infinite;
}

@keyframes shimmer {
  to {
    background-position-x: -200%;
  }
}

@media (max-width: 1180px) {
  .chapter-layout,
  .loading-state {
    grid-template-columns: minmax(220px, 280px) minmax(0, 1fr);
  }

  .study-rail,
  .rail-skeleton {
    grid-column: 1 / -1;
  }

  .study-rail-sticky {
    position: static;
  }
}

@media (max-width: 768px) {
  .chapter-command-row {
    grid-template-columns: 1fr;
    align-items: stretch;
  }

  .chapter-command-actions,
  .chapter-command-tools,
  .command-metrics {
    justify-content: flex-start;
  }

  .chapter-layout,
  .loading-state {
    grid-template-columns: 1fr;
  }

  .chapter-sidebar {
    position: static;
  }

  .chapter-main {
    padding: 22px;
  }

  .rail-metadata {
    grid-template-columns: 1fr;
  }
}
</style>
