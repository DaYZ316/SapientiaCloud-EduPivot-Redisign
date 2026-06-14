<template>
  <div class="chapter-view">
    <button class="back-link" @click="router.back()">
      <ArrowLeft :size="16"/>
      {{ t('courseDetail.backToCourses') }}
    </button>

    <div v-if="loading" class="loading-state">
      <div class="skeleton-line shimmer"></div>
      <div class="skeleton-block shimmer"></div>
    </div>

    <div v-else-if="chapter" class="chapter-layout">
      <div class="chapter-sidebar">
        <ChapterTree
          :chapters="chapterTree"
          :active-chapter-id="chapterId"
          @select="handleSelectChapter"
        />
      </div>
      <div class="chapter-main">
        <ChapterContent
          :chapter="chapter"
          @like-toggle="handleLikeToggle"
        />
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {ArrowLeft} from 'lucide-vue-next'
import {getChapter, getChapterTree, viewChapter, likeChapter, unlikeChapter} from '@/features/course/api/chapter'
import type {Chapter, ChapterInteraction} from '@/features/course/types/chapter'
import ChapterTree from '@/features/course/components/ChapterTree.vue'
import ChapterContent from '@/features/course/components/ChapterContent.vue'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()

const loading = ref(true)
const chapter = ref<Chapter | null>(null)
const chapterTree = ref<Chapter[]>([])

const courseId = ref(route.params.courseId as string)
const chapterId = ref(route.params.chapterId as string)

async function loadChapter(cid: string, chid: string) {
  loading.value = true
  try {
    const [chapterData, treeData] = await Promise.all([
      getChapter(chid),
      getChapterTree(cid),
    ])
    chapter.value = chapterData
    chapterTree.value = treeData
    recordView(chid)
  } finally {
    loading.value = false
  }
}

function recordView(chid: string) {
  viewChapter(chid).then((interaction: ChapterInteraction) => {
    if (chapter.value && chapter.value.id === chid) {
      chapter.value.viewCount = interaction.viewCount
      chapter.value.likeCount = interaction.likeCount
      chapter.value.likedByMe = interaction.likedByMe
    }
  }).catch(() => {})
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
    // 保持服务端结果为准，不做本地回滚
  }
}

function handleSelectChapter(ch: Chapter) {
  router.push('/courses/' + courseId.value + '/chapters/' + ch.id)
}

onMounted(() => {
  loadChapter(courseId.value, chapterId.value)
})

watch(
  () => [route.params.courseId, route.params.chapterId],
  ([newCourseId, newChapterId]) => {
    if (newCourseId && newChapterId) {
      courseId.value = newCourseId as string
      chapterId.value = newChapterId as string
      loadChapter(newCourseId as string, newChapterId as string)
    }
  }
)
</script>

<style scoped>
.chapter-view {
  max-width: 100%;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0;
  border: none;
  background: none;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
  cursor: pointer;
  margin-bottom: 32px;
  transition: color 0.15s;
}

.back-link:hover {
  color: var(--color-on-surface);
}

.chapter-layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 32px;
  min-height: 400px;
}

.chapter-sidebar {
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-lg);
  padding: 16px;
  height: fit-content;
  position: sticky;
  top: 24px;
}

.chapter-main {
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-lg);
  padding: 32px;
}

.loading-state {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 32px;
}

.skeleton-line {
  height: 20px;
  width: 200px;
  border-radius: var(--radius-sm);
}

.skeleton-block {
  height: 300px;
  width: 100%;
  border-radius: var(--radius-lg);
}

.shimmer {
  background: linear-gradient(110deg, var(--color-surface-container-high) 8%, var(--color-surface-canvas) 18%, var(--color-surface-container-high) 33%);
  background-size: 200% 100%;
  animation: shimmer 1.4s ease-in-out infinite;
}

@keyframes shimmer {
  to { background-position-x: -200%; }
}

@media (max-width: 768px) {
  .chapter-layout {
    grid-template-columns: 1fr;
  }

  .chapter-sidebar {
    position: static;
  }
}
</style>
