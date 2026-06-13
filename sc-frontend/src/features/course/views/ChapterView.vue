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
        <ChapterContent :chapter="chapter"/>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {ArrowLeft} from 'lucide-vue-next'
import {getChapter, getChapterTree} from '@/features/course/api/chapter'
import type {Chapter} from '@/features/course/types/chapter'
import ChapterTree from '@/features/course/components/ChapterTree.vue'
import ChapterContent from '@/features/course/components/ChapterContent.vue'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()

const courseId = route.params.courseId as string
const chapterId = route.params.chapterId as string

const loading = ref(true)
const chapter = ref<Chapter | null>(null)
const chapterTree = ref<Chapter[]>([])

onMounted(async () => {
  try {
    const [chapterData, treeData] = await Promise.all([
      getChapter(chapterId),
      getChapterTree(courseId),
    ])
    chapter.value = chapterData
    chapterTree.value = treeData
  } finally {
    loading.value = false
  }
})

function handleSelectChapter(ch: Chapter) {
  router.push('/courses/' + courseId + '/chapters/' + ch.id)
}
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
