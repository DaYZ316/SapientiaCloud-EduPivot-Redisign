<template>
  <div class="courses-page">
    <!-- Page Header -->
    <div class="page-header">
      <h1>{{ t('courses.title') }}</h1>
    </div>

    <!-- Filter Bar -->
    <div class="filter-bar">
      <div class="search-input">
        <Search :size="18"/>
        <input
          v-model="searchKeyword"
          :placeholder="t('courses.searchPlaceholder')"
          @keyup.enter="resetAndLoad"
        />
      </div>
      <BaseSelect
        v-model="filterLevel"
        :options="levelFilterOptions"
        min-width="148px"
        @change="resetAndLoad"
      />
    </div>

    <!-- Courses Grid -->
    <div v-if="courses.length > 0" class="courses-grid">
      <CourseRecommendationCard
        v-for="course in courses"
        :key="course.id"
        :course="course"
        @view="viewCourse"
      />
    </div>

    <!-- Skeleton Loading -->
    <div v-else-if="loading" class="courses-grid">
      <div v-for="n in 6" :key="n" class="skeleton-card">
        <div class="skeleton-cover shimmer"></div>
        <div class="skeleton-info">
          <div class="skeleton-title shimmer"></div>
          <div class="skeleton-desc shimmer"></div>
          <div class="skeleton-desc short shimmer"></div>
          <div class="skeleton-meta">
            <div class="skeleton-tag shimmer"></div>
            <div class="skeleton-tag shimmer"></div>
          </div>
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div v-else class="empty-state">
      <BookOpen :size="48" stroke-width="1.2"/>
      <h3>{{ t('courses.noCourses') }}</h3>
      <p>{{ isTeacher ? t('courses.noCoursesTeacher') : t('courses.noCoursesStudent') }}</p>
    </div>

    <!-- Infinite Scroll Sentinel -->
    <div ref="sentinelRef" class="scroll-sentinel">
      <div v-if="loading" class="loading-spinner"></div>
      <span v-else-if="!hasMore && courses.length > 0" class="no-more-text"></span>
    </div>

  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted, onUnmounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import {BookOpen, Search} from 'lucide-vue-next'

import {getCourses} from '@/features/course/api/course'
import {notify} from '@/shared/composables/useGlobalNotification'
import BaseSelect from '@/shared/components/BaseSelect.vue'
import CourseRecommendationCard from '@/features/course/components/CourseRecommendationCard.vue'
import {useAuthStore} from '@/features/auth/stores/auth'
import type {Course, CoursePageQuery} from '@/features/course/types/course'

type FilterOption = {
  label: string
  value: string | number | undefined
}

const router = useRouter()
const authStore = useAuthStore()
const {t} = useI18n()

const isTeacher = computed(() => authStore.user?.role === 2 || authStore.user?.role === 0)

const courses = ref<Course[]>([])
const currentPage = ref(1)
const totalPages = ref(1)
const pageSize = ref(12)
const loading = ref(false)
const hasMore = ref(true)
const searchKeyword = ref('')
const filterLevel = ref<number | undefined>(undefined)
const sentinelRef = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null

const levelFilterOptions = computed<FilterOption[]>(() => [
  {label: t('courses.allLevels'), value: undefined},
  {label: t('courses.level.beginner'), value: 1},
  {label: t('courses.level.intermediate'), value: 2},
  {label: t('courses.level.advanced'), value: 3},
])

async function loadCourses(append = false) {
  if (loading.value) return
  loading.value = true
  try {
    const query: CoursePageQuery = {
      page: currentPage.value,
      size: pageSize.value,
      keyword: searchKeyword.value || undefined,
      level: filterLevel.value,
      isPublic: 1,
    }
    const response = await getCourses(query)
    if (append) {
      courses.value.push(...response.records)
    } else {
      courses.value = response.records
    }
    totalPages.value = Math.ceil(response.total / pageSize.value)
    hasMore.value = currentPage.value < totalPages.value
  } catch (error) {
    console.error('Failed to load courses:', error)
    notify.error('Failed to load courses')
    if (!append) courses.value = []
    hasMore.value = false
  } finally {
    loading.value = false
  }
}

function resetAndLoad() {
  courses.value = []
  currentPage.value = 1
  hasMore.value = true
  loadCourses(false)
}

function loadMore() {
  if (!hasMore.value || loading.value) return
  currentPage.value++
  loadCourses(true)
}

function viewCourse(id: string) {
  router.push(`/courses/${id}`)
}

function initObserver() {
  if (!sentinelRef.value) return
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0].isIntersecting) {
        loadMore()
      }
    },
    {rootMargin: '200px'},
  )
  observer.observe(sentinelRef.value)
}

onMounted(() => {
  loadCourses()
  initObserver()
})

onUnmounted(() => {
  observer?.disconnect()
})
</script>

<style scoped>
.courses-page {
  max-width: 100%;
}

/* Page Header */
.page-header {
  margin-bottom: 24px;
}

.page-header h1 {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 48px;
  font-weight: 400;
  line-height: 1.3;
  color: var(--color-on-surface);
}

/* Filter Bar */
.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}

.search-input {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 200px;
  padding: 12px 16px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 16px;
}

.search-input svg {
  color: var(--color-muted);
  flex-shrink: 0;
}

.search-input input {
  flex: 1;
  border: none;
  outline: none;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 16px;
  color: var(--color-on-surface);
  background: transparent;
}

.search-input input::placeholder {
  color: var(--color-muted);
}

/* Courses Grid */
.courses-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
  margin-bottom: 32px;
}

/* Skeleton Loading */
.skeleton-card {
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 24px;
  overflow: hidden;
}

.skeleton-cover {
  height: 160px;
  background: var(--color-surface-canvas);
}

.skeleton-info {
  padding: 20px;
}

.skeleton-title {
  height: 20px;
  width: 70%;
  border-radius: 6px;
  background: var(--color-surface-canvas);
  margin-bottom: 12px;
}

.skeleton-desc {
  height: 14px;
  width: 100%;
  border-radius: 4px;
  background: var(--color-surface-canvas);
  margin-bottom: 8px;
}

.skeleton-desc.short {
  width: 60%;
}

.skeleton-meta {
  display: flex;
  gap: 8px;
  margin-top: 16px;
}

.skeleton-tag {
  height: 22px;
  width: 56px;
  border-radius: var(--radius-sm);
  background: var(--color-surface-canvas);
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
  0% { transform: translateX(-100%); }
  100% { transform: translateX(100%); }
}

/* Empty State */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 120px 32px;
  color: var(--color-muted);
}

.empty-state h3 {
  margin: 16px 0 8px;
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 400;
  color: var(--color-on-surface);
}

.empty-state p {
  margin: 0;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
}

/* Infinite Scroll Sentinel */
.scroll-sentinel {
  display: flex;
  justify-content: center;
  padding: 32px 0;
}

.loading-spinner {
  width: 24px;
  height: 24px;
  border: 3px solid var(--color-outline-light);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.no-more-text {
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  color: var(--color-muted);
}

/* Responsive */
@media (max-width: 768px) {
  .page-header h1 {
    font-size: 32px;
  }

  .filter-bar {
    flex-direction: column;
  }

  .courses-grid {
    grid-template-columns: 1fr;
  }
}
</style>
