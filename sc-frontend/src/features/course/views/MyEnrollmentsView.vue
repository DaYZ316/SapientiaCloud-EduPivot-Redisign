<template>
  <div class="my-courses-page">
    <section aria-labelledby="my-courses-title" class="courses-command-bar">
      <div class="courses-title-block">
        <h1 id="my-courses-title">{{ t('myEnrollments.title') }}</h1>
        <p>{{ t('myEnrollments.student.description') }}</p>
      </div>

      <div class="courses-tools" role="search">
        <label class="search-field" for="course-search">
          <Search :size="16" stroke-width="1.7"/>
          <input
              id="course-search"
              v-model.trim="keyword"
              :placeholder="t('myEnrollments.searchPlaceholder')"
              type="search"
          />
        </label>
        <button class="filter-button" type="button">
          <Filter :size="16" stroke-width="1.7"/>
          <span>{{ t('myEnrollments.filter') }}</span>
        </button>
        <div class="student-chip">
          <img v-if="authStore.user?.avatarUrl" :alt="t('myEnrollments.student.avatarAlt')"
               :src="authStore.user.avatarUrl"/>
          <span v-else class="student-chip__avatar">{{ studentInitials }}</span>
          <span>{{ authStore.user?.displayName || t('myEnrollments.student.defaultName') }}</span>
        </div>
      </div>
    </section>

    <section :aria-label="t('myEnrollments.overviewAria')" class="course-stats">
      <article v-for="stat in stats" :key="stat.label" class="stat-cell">
        <span>{{ stat.label }}</span>
        <strong>{{ stat.value }}</strong>
        <small>{{ stat.note }}</small>
      </article>
    </section>

    <section class="courses-workbench">
      <div class="courses-primary">
        <div class="panel-heading">
          <div>
            <h2>{{ t('myEnrollments.student.activeCourses') }}</h2>
          </div>
          <span>{{ t('myEnrollments.courseCount', {count: filteredCourses.length}) }}</span>
        </div>

        <div v-if="loading" :aria-label="t('myEnrollments.loadingAria')" class="course-list">
          <div v-for="n in 5" :key="n" class="course-row skeleton-row">
            <div class="skeleton-block course-mark"></div>
            <div class="skeleton-copy">
              <div class="skeleton-line title"></div>
              <div class="skeleton-line"></div>
            </div>
            <div class="skeleton-line action"></div>
          </div>
        </div>

        <div v-else-if="filteredCourses.length > 0" class="course-list">
          <article v-for="course in filteredCourses" :key="course.id" class="course-row">
            <div class="course-mark">
              <img
                  :alt="t('myEnrollments.courseCoverAlt', {title: course.title})"
                  :src="getCourseCoverUrl(course.coverUrl)"
                  @error="handleCourseCoverError"
              />
            </div>

            <div class="course-main">
              <div class="course-title-line">
                <h3>{{ course.title }}</h3>
                <span :class="course.statusClass" class="status-tag">{{ course.statusLabel }}</span>
              </div>
              <div class="course-meta">
                <span>{{ course.teacher }}</span>
                <span>{{ course.nextSession }}</span>
                <span>{{ course.recentActivity }}</span>
              </div>
              <div :aria-label="t('myEnrollments.progressAria', {title: course.title, progress: course.progress})"
                   class="progress-track">
                <span :style="{width: `${course.progress}%`}"></span>
              </div>
            </div>

            <div class="course-progress">
              <strong>{{ course.progress }}%</strong>
              <small>{{ t('myEnrollments.student.progressLabel') }}</small>
            </div>

            <div class="course-actions">
              <button class="continue-button" type="button" @click="viewCourse(course.courseId)">
                {{ t('myEnrollments.student.continueLearning') }}
              </button>
              <button
                  v-if="course.canDrop"
                  :aria-label="t('myEnrollments.student.dropCourse')"
                  :title="t('myEnrollments.student.dropCourse')"
                  class="drop-button"
                  type="button"
                  @click="confirmDrop(course.source)"
              >
                <X :size="14" stroke-width="1.8"/>
              </button>
            </div>
          </article>
        </div>

        <div v-else class="empty-state">
          <GraduationCap :size="46" stroke-width="1.2"/>
          <h3>{{ keyword ? t('myEnrollments.noMatchedCourses') : t('myEnrollments.noEnrollments') }}</h3>
          <p>{{ keyword ? t('myEnrollments.noMatchedCoursesDesc') : t('myEnrollments.noEnrollmentsDesc') }}</p>
          <button class="continue-button" type="button" @click="router.push('/courses')">
            {{ t('myEnrollments.browseCourses') }}
          </button>
        </div>

        <div v-if="!loading && totalPages > 1" class="pagination">
          <button :disabled="currentPage === 1" class="page-button" type="button" @click="changePage(currentPage - 1)">
            {{ t('myEnrollments.previous') }}
          </button>
          <button
              v-for="page in displayedPages"
              :key="page"
              :class="{active: currentPage === page}"
              class="page-button"
              type="button"
              @click="changePage(page)"
          >
            {{ page }}
          </button>
          <button :disabled="currentPage === totalPages" class="page-button" type="button"
                  @click="changePage(currentPage + 1)">
            {{ t('myEnrollments.next') }}
          </button>
        </div>
      </div>

      <aside :aria-label="t('myEnrollments.student.planAria')" class="courses-sidebar">
        <section class="side-panel">
          <div class="panel-heading compact">
            <h2>{{ t('myEnrollments.student.todayPlan') }}</h2>
            <CalendarDays :size="18" stroke-width="1.6"/>
          </div>
          <div class="task-list">
            <div v-for="task in todayPlan" :key="task.title" class="task-item">
              <span class="task-time">{{ task.time }}</span>
              <div>
                <strong>{{ task.title }}</strong>
                <small>{{ task.course }}</small>
              </div>
            </div>
          </div>
        </section>

        <section class="side-panel">
          <div class="panel-heading compact">
            <h2>{{ t('myEnrollments.student.deadlines') }}</h2>
            <AlertTriangle :size="18" stroke-width="1.6"/>
          </div>
          <div class="deadline-list">
            <div v-for="deadline in deadlines" :key="deadline.title" class="deadline-item">
              <div>
                <strong>{{ deadline.title }}</strong>
                <small>{{ deadline.course }}</small>
              </div>
              <span :class="deadline.tone">{{ deadline.due }}</span>
            </div>
          </div>
        </section>

        <section class="side-panel">
          <div class="panel-heading compact">
            <h2>{{ t('myEnrollments.student.rhythm') }}</h2>
            <Activity :size="18" stroke-width="1.6"/>
          </div>
          <div :aria-label="t('myEnrollments.student.rhythmAria')" class="rhythm-bars">
            <span
                v-for="(value, index) in rhythm"
                :key="index"
                :style="{height: `${value}%`}"
            ></span>
          </div>
          <p class="rhythm-note">{{ t('myEnrollments.student.rhythmNote') }}</p>
        </section>
      </aside>
    </section>

    <section class="resume-panel">
      <div>
        <h2>{{ t('myEnrollments.student.resumeTitle') }}</h2>
        <p>{{ resumeCourse.title }} · {{ resumeCourse.recentActivity }}</p>
      </div>
      <button class="outline-button" type="button" @click="viewCourse(resumeCourse.courseId)">
        <Timer :size="16" stroke-width="1.7"/>
        <span>{{ t('myEnrollments.student.resumeAction') }}</span>
      </button>
    </section>

    <BaseConfirmDialog
        :cancel-text="t('myEnrollments.dropModal.cancel')"
        :close-label="t('myEnrollments.dropModal.cancel')"
        :confirm-text="t('myEnrollments.dropModal.confirm')"
        :message="t('myEnrollments.dropModal.confirmMessage', {title: dropTarget?.courseTitle})"
        :title="t('myEnrollments.dropModal.title')"
        :visible="showDropModal"
        confirm-variant="danger"
        @cancel="showDropModal = false"
        @confirm="handleDrop"
    />
  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import {Activity, AlertTriangle, CalendarDays, Filter, GraduationCap, Search, Timer, X,} from 'lucide-vue-next'

import BaseConfirmDialog from '@/shared/components/BaseConfirmDialog.vue'
import {dropCourse, getMyEnrollments} from '@/features/course/api/course'
import type {Enrollment} from '@/features/course/types/course'
import {useAuthStore} from '@/features/auth/stores/auth'
import {notify} from '@/shared/composables/useGlobalNotification'
import {getAvatarInitials} from '@/shared/utils/avatar'
import {getCourseCoverUrl, handleCourseCoverError} from '@/shared/utils/courseCover'

interface CourseWorkspaceItem {
  id: string
  courseId: string
  title: string
  teacher: string
  coverUrl: string | null
  nextSession: string
  recentActivity: string
  progress: number
  statusLabel: string
  statusClass: string
  canDrop: boolean
  source: Enrollment
}

const {t} = useI18n()
const router = useRouter()
const authStore = useAuthStore()

const currentPage = ref(1)
const totalPages = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const enrollments = ref<Enrollment[]>([])
const keyword = ref('')
const showDropModal = ref(false)
const dropTarget = ref<Enrollment | null>(null)

const fallbackCourseKeys = ['math', 'english', 'dataStructures', 'writing', 'cloud'] as const

const displayedPages = computed(() => {
  const pages: number[] = []
  const maxDisplay = 5
  let start = Math.max(1, currentPage.value - Math.floor(maxDisplay / 2))
  const end = Math.min(totalPages.value, start + maxDisplay - 1)
  if (end - start + 1 < maxDisplay) {
    start = Math.max(1, end - maxDisplay + 1)
  }
  for (let i = start; i <= end; i += 1) {
    pages.push(i)
  }
  return pages
})

const courseItems = computed<CourseWorkspaceItem[]>(() => {
  return enrollments.value.map((enrollment, index) => {
    const fallback = getFallbackCourse(index)
    const statusLabel = getEnrollmentStatusLabel(enrollment.status)
    const progress = enrollment.courseProgress ?? 0

    return {
      id: enrollment.id,
      courseId: enrollment.courseId,
      title: enrollment.courseTitle || fallback.title,
      teacher: t('myEnrollments.student.teacherSuffix', {name: fallback.teacher}),
      coverUrl: enrollment.courseCoverUrl,
      nextSession: fallback.next,
      recentActivity: formatCourseHours(enrollment),
      progress,
      statusLabel,
      statusClass: getEnrollStatusClass(enrollment.status),
      canDrop: enrollment.status === 1,
      source: enrollment,
    }
  })
})

const filteredCourses = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  if (!value) return courseItems.value

  return courseItems.value.filter((course) =>
      [course.title, course.teacher, course.recentActivity].some((field) => field.toLowerCase().includes(value)),
  )
})

const activeCourses = computed(() => courseItems.value.filter((course) => course.source.status === 1))
const averageProgress = computed(() => {
  if (courseItems.value.length === 0) return 0
  const total = courseItems.value.reduce((sum, course) => sum + course.progress, 0)
  return Math.round(total / courseItems.value.length)
})

const stats = computed(() => [
  {
    label: t('myEnrollments.student.stats.activeCourses'),
    value: activeCourses.value.length || courseItems.value.length,
    note: t('myEnrollments.student.stats.joinedThisTerm'),
  },
  {
    label: t('myEnrollments.student.stats.dueThisWeek'),
    value: Math.min(3, Math.max(1, courseItems.value.length)),
    note: t('myEnrollments.student.stats.assignmentsAndQuizzes'),
  },
  {
    label: t('myEnrollments.student.stats.averageProgress'),
    value: `${averageProgress.value}%`,
    note: t('myEnrollments.student.stats.byCourseProgress'),
  },
  {
    label: t('myEnrollments.student.stats.unreadDiscussions'),
    value: courseItems.value.length ? 12 : 0,
    note: t('myEnrollments.student.stats.fromCourseDiscussions'),
  },
])

const todayPlan = computed(() => [
  {time: '14:00', title: t('myEnrollments.student.todayPlanItems.dataStructureVideo'), course: findCourseTitle(2)},
  {time: '16:30', title: t('myEnrollments.student.todayPlanItems.englishPractice'), course: findCourseTitle(1)},
  {time: '20:00', title: t('myEnrollments.student.todayPlanItems.labReportOutline'), course: findCourseTitle(4)},
])

const deadlines = computed(() => [
  {
    title: t('myEnrollments.student.deadlineItems.cloudReport'),
    course: findCourseTitle(4),
    due: t('myEnrollments.student.deadlineItems.twoDaysLeft'),
    tone: 'urgent',
  },
  {
    title: t('myEnrollments.student.deadlineItems.writingDraft'),
    course: findCourseTitle(3),
    due: t('myEnrollments.student.deadlineItems.friday'),
    tone: 'normal',
  },
  {
    title: t('myEnrollments.student.deadlineItems.mathQuiz'),
    course: findCourseTitle(0),
    due: t('myEnrollments.student.deadlineItems.tonight'),
    tone: 'urgent',
  },
])

const rhythm = [28, 52, 74, 46, 64, 88, 58]

const resumeCourse = computed(() => courseItems.value[0] ?? {
  courseId: '',
  title: t('myEnrollments.student.resumeEmptyTitle'),
  recentActivity: t('myEnrollments.student.resumeEmptyActivity'),
})

const studentInitials = computed(() => getAvatarInitials(authStore.user?.displayName))

function findCourseTitle(index: number): string {
  return courseItems.value[index]?.title || getFallbackCourse(index).title
}

function getEnrollStatusClass(status: number): string {
  const map: Record<number, string> = {0: 'pending', 1: 'active', 2: 'completed', 3: 'dropped'}
  return map[status] ?? 'pending'
}

function formatCourseHours(enrollment: Enrollment): string {
  const publishedCount = enrollment.coursePublishedClassSessionCount ?? 0
  const totalClassHours = enrollment.courseTotalClassHours ?? 0
  if (totalClassHours <= 0) {
    return t('myEnrollments.classHoursUnset', {publishedCount})
  }
  return t('myEnrollments.classHoursProgress', {publishedCount, totalClassHours})
}

function getFallbackCourse(index: number) {
  const key = fallbackCourseKeys[index % fallbackCourseKeys.length]
  return {
    title: t(`myEnrollments.student.fallbackCourses.${key}.title`),
    teacher: t(`myEnrollments.student.fallbackCourses.${key}.teacher`),
    next: t(`myEnrollments.student.fallbackCourses.${key}.next`),
    activity: t(`myEnrollments.student.fallbackCourses.${key}.activity`),
  }
}

function getEnrollmentStatusLabel(status: number): string {
  const map: Record<number, string> = {
    0: t('myEnrollments.student.status.pending'),
    1: t('myEnrollments.student.status.active'),
    2: t('myEnrollments.student.status.completed'),
    3: t('myEnrollments.student.status.dropped'),
  }
  return map[status] ?? t('myEnrollments.unknownStatus')
}

async function loadData() {
  loading.value = true
  try {
    const response = await getMyEnrollments(currentPage.value, pageSize.value)
    enrollments.value = response.records
    totalPages.value = Math.ceil(response.total / pageSize.value)
  } catch (error) {
    console.error('Failed to load enrollments:', error)
    notify.error(t('myEnrollments.alert.loadFailed'))
    enrollments.value = []
  } finally {
    loading.value = false
  }
}

function changePage(page: number) {
  currentPage.value = page
  loadData()
}

function viewCourse(id: string) {
  if (!id) {
    router.push('/courses')
    return
  }
  router.push(`/courses/${id}`)
}

function confirmDrop(enrollment: Enrollment) {
  dropTarget.value = enrollment
  showDropModal.value = true
}

async function handleDrop() {
  if (!dropTarget.value) return
  try {
    await dropCourse(dropTarget.value.id)
    showDropModal.value = false
    dropTarget.value = null
    await loadData()
  } catch (error) {
    console.error('Failed to drop course:', error)
    notify.error(t('myEnrollments.alert.dropFailed'))
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.my-courses-page {
  --course-border: var(--color-outline-light);
  --course-surface: var(--color-surface-card);
  --course-surface-raised: var(--color-surface-container);

  display: flex;
  flex-direction: column;
  gap: 24px;
  color: var(--color-on-surface);
}

.courses-command-bar,
.resume-panel {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--course-border);
}

.courses-title-block {
  max-width: 620px;
}

.courses-title-block h1,
.panel-heading h2,
.resume-panel h2,
.empty-state h3 {
  margin: 0;
  font-family: var(--font-heading);
  font-weight: 500;
  color: var(--color-on-surface);
  text-wrap: balance;
}

.courses-title-block h1 {
  font-size: clamp(36px, 5vw, 56px);
  line-height: 1.05;
}

.courses-title-block p,
.resume-panel p,
.rhythm-note {
  margin: 12px 0 0;
  max-width: 62ch;
  font-size: 15px;
  line-height: 1.6;
  color: var(--color-muted);
}

.courses-tools {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
  min-width: 360px;
}

.search-field,
.filter-button,
.student-chip,
.outline-button,
.continue-button,
.drop-button,
.page-button {
  height: 44px;
  display: inline-flex;
  align-items: center;
  border-radius: 0;
  font-family: var(--font-body);
  transition: background 0.2s, border-color 0.2s, color 0.2s, transform 0.2s;
}

.search-field {
  width: min(280px, 100%);
  gap: 10px;
  padding: 0 14px;
  background: var(--color-surface-canvas);
  border: 1px solid var(--course-border);
  color: var(--color-muted);
}

.search-field:focus-within {
  border-color: var(--color-on-surface);
}

.search-field input {
  min-width: 0;
  width: 100%;
  background: transparent;
  border: 0;
  outline: 0;
  color: var(--color-on-surface);
}

.search-field input::placeholder {
  color: var(--color-muted);
}

.filter-button,
.outline-button,
.page-button {
  gap: 8px;
  padding: 0 16px;
  background: transparent;
  border: 1px solid var(--course-border);
  color: var(--color-on-surface);
  cursor: pointer;
}

.filter-button:hover,
.outline-button:hover,
.page-button:hover:not(:disabled) {
  background: var(--course-surface-raised);
  border-color: var(--color-on-surface);
}

.filter-button:active,
.outline-button:active,
.continue-button:active,
.drop-button:active,
.page-button:active {
  transform: translateY(1px);
}

.student-chip {
  gap: 10px;
  padding: 0 12px 0 6px;
  background: var(--course-surface);
  border: 1px solid var(--course-border);
  color: var(--color-on-surface);
}

.student-chip img,
.student-chip__avatar {
  width: 30px;
  height: 30px;
}

.student-chip img {
  object-fit: cover;
}

.student-chip__avatar {
  display: grid;
  place-items: center;
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-family: var(--font-heading);
  font-size: 12px;
  font-weight: 700;
}

.course-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border: 1px solid var(--course-border);
  background: var(--course-surface);
}

.stat-cell {
  min-width: 0;
  padding: 22px 24px;
  border-right: 1px solid var(--course-border);
}

.stat-cell:last-child {
  border-right: 0;
}

.stat-cell span,
.stat-cell small,
.course-meta,
.course-progress small,
.task-item small,
.deadline-item small {
  color: var(--color-muted);
}

.stat-cell span,
.stat-cell small,
.course-progress small,
.task-time,
.deadline-item > span {
  display: block;
  font-size: 12px;
}

.stat-cell strong {
  display: block;
  margin: 8px 0 4px;
  font-size: 32px;
  line-height: 1;
  font-variant-numeric: tabular-nums;
  color: var(--color-on-surface);
}

.courses-workbench {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(300px, 0.95fr);
  gap: 24px;
  align-items: start;
}

.courses-primary,
.side-panel,
.resume-panel {
  background: var(--course-surface);
  border: 1px solid var(--course-border);
}

.courses-primary {
  min-width: 0;
}

.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 22px 24px;
  border-bottom: 1px solid var(--course-border);
}

.panel-heading h2 {
  font-size: 26px;
  line-height: 1.2;
}

.panel-heading > span {
  flex-shrink: 0;
  color: var(--color-muted);
}

.panel-heading.compact {
  padding: 18px 20px;
}

.panel-heading.compact h2 {
  font-family: var(--font-body);
  font-size: 16px;
  font-weight: 600;
}

.course-list {
  display: flex;
  flex-direction: column;
}

.course-row {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr) 86px auto;
  gap: 18px;
  align-items: center;
  min-height: 116px;
  padding: 20px 24px;
  border-bottom: 1px solid var(--course-border);
}

.course-row:last-child {
  border-bottom: 0;
}

.course-row:hover {
  background: var(--course-surface-raised);
}

.course-mark {
  width: 64px;
  height: 64px;
  display: grid;
  place-items: center;
  background: var(--color-surface-canvas);
  border: 1px solid var(--course-border);
  color: var(--color-on-surface);
  overflow: hidden;
}

.course-mark img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.course-main {
  min-width: 0;
}

.course-title-line {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.course-title-line h3 {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  font-size: 18px;
  font-weight: 600;
  color: var(--color-on-surface);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-tag {
  flex-shrink: 0;
  padding: 4px 8px;
  border: 1px solid var(--course-border);
  font-size: 11px;
  color: var(--color-muted);
}

.status-tag.active {
  border-color: var(--color-on-surface);
  color: var(--color-on-surface);
}

.status-tag.completed {
  background: var(--color-on-surface);
  color: var(--color-on-primary);
}

.status-tag.dropped {
  color: var(--color-error);
}

.course-meta {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 8px;
  font-size: 13px;
}

.progress-track {
  height: 6px;
  margin-top: 14px;
  background: var(--color-surface-canvas);
  border: 1px solid var(--course-border);
}

.progress-track span {
  display: block;
  height: 100%;
  background: var(--color-on-surface);
}

.course-progress {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.course-progress strong {
  display: block;
  font-size: 22px;
  line-height: 1;
}

.course-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.continue-button {
  justify-content: center;
  padding: 0 18px;
  background: var(--color-on-surface);
  border: 1px solid var(--color-on-surface);
  color: var(--color-on-primary);
  cursor: pointer;
}

.continue-button:hover {
  background: var(--color-primary-soft);
  border-color: var(--color-primary-soft);
}

.drop-button {
  width: 36px;
  justify-content: center;
  background: transparent;
  border: 1px solid var(--course-border);
  color: var(--color-muted);
  cursor: pointer;
}

.drop-button:hover {
  border-color: var(--color-error);
  color: var(--color-error);
}

.empty-state {
  display: grid;
  place-items: center;
  padding: 96px 24px;
  text-align: center;
  color: var(--color-muted);
}

.empty-state h3 {
  margin-top: 18px;
  font-size: 28px;
}

.empty-state p {
  margin: 10px 0 24px;
  color: var(--color-muted);
}

.pagination {
  display: flex;
  justify-content: center;
  gap: 8px;
  padding: 20px 24px;
  border-top: 1px solid var(--course-border);
}

.page-button {
  min-width: 44px;
  justify-content: center;
}

.page-button.active {
  background: var(--color-on-surface);
  border-color: var(--color-on-surface);
  color: var(--color-on-primary);
}

.page-button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.courses-sidebar {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.task-list,
.deadline-list {
  display: flex;
  flex-direction: column;
}

.task-item,
.deadline-item {
  display: flex;
  gap: 14px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--course-border);
}

.task-item:last-child,
.deadline-item:last-child {
  border-bottom: 0;
}

.task-time {
  width: 44px;
  flex-shrink: 0;
  color: var(--color-on-surface);
  font-variant-numeric: tabular-nums;
}

.task-item strong,
.deadline-item strong {
  display: block;
  margin-bottom: 5px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-on-surface);
}

.deadline-item {
  justify-content: space-between;
}

.deadline-item > span {
  flex-shrink: 0;
  align-self: flex-start;
  padding: 5px 8px;
  border: 1px solid var(--course-border);
  color: var(--color-muted);
}

.deadline-item > span.urgent {
  border-color: var(--color-on-surface);
  color: var(--color-on-surface);
}

.rhythm-bars {
  height: 126px;
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  align-items: end;
  gap: 8px;
  padding: 20px 20px 0;
}

.rhythm-bars span {
  min-height: 18px;
  background: var(--color-on-surface);
}

.rhythm-note {
  padding: 0 20px 20px;
  font-size: 13px;
}

.resume-panel {
  padding: 22px 24px;
}

.resume-panel h2 {
  font-size: 26px;
  line-height: 1.2;
}

.skeleton-row {
  pointer-events: none;
}

.skeleton-block,
.skeleton-line {
  position: relative;
  overflow: hidden;
  background: var(--color-surface-canvas);
}

.skeleton-copy {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.skeleton-line {
  height: 14px;
  width: 66%;
}

.skeleton-line.title {
  height: 18px;
  width: 44%;
}

.skeleton-line.action {
  width: 110px;
  height: 44px;
}

.skeleton-block::after,
.skeleton-line::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent, rgba(245, 245, 245, 0.08), transparent);
  animation: shimmer 1.3s ease-in-out infinite;
}

@keyframes shimmer {
  from {
    transform: translateX(-100%);
  }

  to {
    transform: translateX(100%);
  }
}

@media (max-width: 1180px) {
  .courses-command-bar,
  .resume-panel {
    flex-direction: column;
  }

  .courses-tools {
    width: 100%;
    min-width: 0;
    justify-content: flex-start;
  }

  .courses-workbench {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 860px) {
  .course-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .stat-cell:nth-child(2) {
    border-right: 0;
  }

  .stat-cell:nth-child(-n + 2) {
    border-bottom: 1px solid var(--course-border);
  }

  .course-row {
    grid-template-columns: 56px minmax(0, 1fr);
  }

  .course-mark {
    width: 56px;
    height: 56px;
  }

  .course-progress,
  .course-actions {
    grid-column: 2;
  }

  .course-progress {
    text-align: left;
  }
}

@media (max-width: 640px) {
  .courses-tools,
  .course-actions,
  .pagination {
    align-items: stretch;
    flex-direction: column;
  }

  .search-field,
  .filter-button,
  .student-chip,
  .continue-button,
  .outline-button,
  .page-button {
    width: 100%;
    justify-content: center;
  }

  .course-stats {
    grid-template-columns: 1fr;
  }

  .stat-cell,
  .stat-cell:nth-child(2) {
    border-right: 0;
    border-bottom: 1px solid var(--course-border);
  }

  .stat-cell:last-child {
    border-bottom: 0;
  }

  .panel-heading,
  .course-row,
  .resume-panel {
    padding-right: 16px;
    padding-left: 16px;
  }

  .course-title-line {
    align-items: flex-start;
    flex-direction: column;
  }

  .course-title-line h3 {
    white-space: normal;
  }

  .task-item,
  .deadline-item {
    padding-right: 16px;
    padding-left: 16px;
  }
}
</style>
