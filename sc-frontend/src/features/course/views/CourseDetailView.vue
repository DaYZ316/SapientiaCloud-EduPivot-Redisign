<template>
  <div class="course-detail-page">
    <button class="back-link" @click="router.push('/courses')">
      <ArrowLeft :size="16"/>
      {{ t('courseDetail.backToCourses') }}
    </button>

    <div v-if="loading" class="detail-grid" aria-label="Loading course">
      <section class="primary-column">
        <div class="skeleton-title shimmer"></div>
        <div class="skeleton-cover shimmer"></div>
        <div class="skeleton-line shimmer"></div>
        <div class="skeleton-line short shimmer"></div>
      </section>
      <aside class="action-rail skeleton-rail shimmer"></aside>
    </div>

    <div v-else-if="!course" class="empty-state">
      <BookOpen :size="36" stroke-width="1.4"/>
      <h1>{{ t('courseDetail.courseNotFound') }}</h1>
      <button class="secondary-button" @click="router.push('/courses')">{{ t('courseDetail.browseCourses') }}</button>
    </div>

    <div v-else class="detail-grid">
      <section class="primary-column">
        <h1>{{ course.title }}</h1>

        <div class="course-cover">
          <img :src="course.coverUrl || courseCoverFallbackUrl" alt="Course cover"/>
        </div>

        <p class="course-description">{{ course.description || t('courseDetail.noDescription') }}</p>

        <div class="metadata-chips">
          <span class="chip chip-solid">{{ getLevelLabel(course.level) }}</span>
          <span class="chip chip-muted">{{ getStatusLabel(course.status) }}</span>
        </div>

        <div class="teacher-row">
          <div class="teacher-avatar">
            <img :src="course.teacherAvatar || teacherFallbackUrl" alt="Instructor avatar"/>
          </div>
          <div>
            <p>{{ t('courseDetail.instructor') }}</p>
            <strong>{{ course.teacherName || t('courseDetail.unknownTeacher') }}</strong>
          </div>
        </div>
      </section>

      <aside class="action-rail">
        <section>
          <h2>{{ t('courseDetail.enrollment') }}</h2>
          <div class="capacity-header">
            <span>{{ t('courseDetail.capacity') }}</span>
            <strong>{{ course.currentStudents }}{{ capacitySuffix }} {{ t('courseDetail.students') }}</strong>
          </div>
          <div class="capacity-track">
            <span :style="{width: `${capacityPercent}%`}"></span>
          </div>
        </section>

        <div class="date-list">
          <div>
            <span>
              <CalendarDays :size="18"/>
              {{ t('courseDetail.created') }}
            </span>
            <strong>{{ formatDate(course.createdAt) }}</strong>
          </div>
          <div>
            <span>
              <RefreshCw :size="18"/>
              {{ t('courseDetail.updated') }}
            </span>
            <strong>{{ course.updatedAt ? formatDate(course.updatedAt) : t('courseDetail.noUpdates') }}</strong>
          </div>
        </div>

        <button
          v-if="canEnroll"
          class="enroll-button"
          :disabled="enrolling || isFull || course.enrolled"
          @click="handleEnroll"
        >
          {{ enrollLabel }}
        </button>
      </aside>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {ArrowLeft, BookOpen, CalendarDays, RefreshCw} from 'lucide-vue-next'

import {enroll, getCourse} from '@/features/course/api/course'
import {notify} from '@/shared/composables/useGlobalNotification'
import {useAuthStore} from '@/features/auth/stores/auth'
import type {CourseDetail} from '@/features/course/types/course'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const {t} = useI18n()

const course = ref<CourseDetail | null>(null)
const loading = ref(true)
const enrolling = ref(false)

const courseCoverFallbackUrl = '/assets/course-cover-default.png'
const teacherFallbackUrl = '/assets/avatar-teacher-default.png'

const courseId = computed(() => String(route.params.id || ''))
const isStudent = computed(() => authStore.user?.role === 1)
const canEnroll = computed(() => isStudent.value && course.value?.status === 1)
const isFull = computed(() => {
  if (!course.value || course.value.maxStudents <= 0) {
    return false
  }
  return course.value.currentStudents >= course.value.maxStudents
})
const capacityPercent = computed(() => {
  if (!course.value || course.value.maxStudents <= 0) {
    return 0
  }
  return Math.min(100, Math.round((course.value.currentStudents / course.value.maxStudents) * 100))
})
const capacitySuffix = computed(() => {
  if (!course.value || course.value.maxStudents <= 0) {
    return ''
  }
  return ` / ${course.value.maxStudents}`
})
const enrollLabel = computed(() => {
  if (course.value?.enrolled) {
    return t('courseDetail.enrolled')
  }
  if (isFull.value) {
    return t('courseDetail.full')
  }
  return enrolling.value ? t('courseDetail.enrolling') : t('courseDetail.enrollNow')
})

function formatDate(dateStr: string): string {
  const timestamp = Date.parse(dateStr)
  if (Number.isNaN(timestamp)) {
    return t('courseDetail.unknown')
  }
  return new Intl.DateTimeFormat('en-US', {month: 'short', day: 'numeric', year: 'numeric'}).format(timestamp)
}

function getLevelLabel(level: number): string {
  const map: Record<number, string> = {
    1: t('courses.level.beginner'),
    2: t('courses.level.intermediate'),
    3: t('courses.level.advanced'),
  }
  return map[level] ?? t('courseDetail.levelUnknown')
}

function getStatusLabel(status: number): string {
  const map: Record<number, string> = {
    0: t('courses.status.draft'),
    1: t('courses.status.published'),
    2: t('courses.status.archived'),
  }
  return map[status] ?? t('courseDetail.statusUnknown')
}

async function loadCourse() {
  if (!courseId.value) {
    await router.replace('/courses')
    return
  }

  loading.value = true
  try {
    course.value = await getCourse(courseId.value)
  } catch (error) {
    console.error('Failed to load course:', error)
    course.value = null
  } finally {
    loading.value = false
  }
}

async function handleEnroll() {
  if (!course.value || isFull.value || course.value.enrolled) {
    return
  }

  enrolling.value = true
  try {
    await enroll({courseId: course.value.id})
    course.value = {
      ...course.value,
      currentStudents: course.value.currentStudents + 1,
      enrolled: true,
    }
  } catch (error) {
    console.error('Failed to enroll course:', error)
    notify.error(t('courseDetail.alert.enrollFailed'))
  } finally {
    enrolling.value = false
  }
}

onMounted(() => {
  loadCourse()
})
</script>

<style scoped>
.course-detail-page {
  max-width: 1280px;
  margin: 0 auto;
  padding: 8px 0 56px;
  color: var(--color-on-surface);
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 48px;
  padding: 0;
  background: transparent;
  border: none;
  color: var(--color-primary);
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  cursor: pointer;
  transition: opacity 0.2s;
}

.back-link:hover {
  opacity: 0.7;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 32px;
  align-items: start;
}

.primary-column {
  grid-column: span 8;
  display: flex;
  flex-direction: column;
  gap: 40px;
}

.primary-column h1 {
  max-width: 760px;
  margin: 0;
  color: var(--color-primary);
  font-family: 'Bodoni Moda', serif;
  font-size: clamp(48px, 5.625vw, 72px);
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: 0;
}

.course-cover {
  aspect-ratio: 16 / 9;
  width: 100%;
  overflow: hidden;
  border: 1px solid var(--color-primary);
  border-radius: 48px;
  background: var(--color-surface-container-high);
}

.course-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.course-description {
  max-width: 680px;
  margin: 0;
  color: var(--color-on-surface-variant);
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 18px;
  line-height: 1.6;
}

.metadata-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.chip {
  padding: 8px 16px;
  border-radius: var(--radius-sm);
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.1em;
  line-height: 1;
  text-transform: uppercase;
}

.chip-outline {
  border: 1px solid var(--color-primary);
  color: var(--color-primary);
}

.chip-solid {
  border: 1px solid var(--color-primary);
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.chip-muted {
  border: 1px solid var(--color-surface-container-highest);
  color: var(--color-on-surface-variant);
}

.teacher-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-top: 24px;
  border-top: 1px solid var(--color-outline-light);
}

.teacher-avatar {
  width: 64px;
  height: 64px;
  overflow: hidden;
  border: 1px solid var(--color-primary);
  border-radius: 50%;
  background: var(--color-surface-container-high);
}

.teacher-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.teacher-row p {
  margin: 0 0 4px;
  color: var(--color-on-surface-variant);
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.1em;
  line-height: 1;
  text-transform: uppercase;
}

.teacher-row strong {
  color: var(--color-primary);
  font-family: 'Bodoni Moda', serif;
  font-size: 24px;
  font-weight: 500;
  line-height: 1.3;
}

.action-rail {
  position: sticky;
  top: 96px;
  grid-column: span 4;
  display: flex;
  flex-direction: column;
  gap: 32px;
  padding: 40px;
  border: 1px solid var(--color-primary);
  border-radius: 32px;
  background: var(--color-surface-card);
}

.action-rail h2 {
  margin: 0 0 8px;
  color: var(--color-primary);
  font-family: 'Bodoni Moda', serif;
  font-size: 24px;
  font-weight: 500;
  line-height: 1.3;
}

.capacity-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.capacity-header span,
.date-list span {
  color: var(--color-on-surface-variant);
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 16px;
  line-height: 1.5;
}

.capacity-header strong {
  color: var(--color-primary);
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.1em;
  line-height: 1;
  text-transform: uppercase;
  text-align: right;
}

.capacity-track {
  height: 4px;
  overflow: hidden;
  background: var(--color-surface-container-high);
}

.capacity-track span {
  display: block;
  height: 100%;
  background: var(--color-primary);
  transition: width 0.2s ease;
}

.date-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 24px 0;
  border-top: 1px solid var(--color-outline-light);
  border-bottom: 1px solid var(--color-outline-light);
}

.date-list div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.date-list span {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.date-list strong {
  color: var(--color-primary);
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 16px;
  font-weight: 400;
  line-height: 1.5;
  text-align: right;
}

.enroll-button,
.secondary-button {
  min-height: 48px;
  border-radius: var(--radius-sm);
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  cursor: pointer;
  transition: background 0.2s, color 0.2s, opacity 0.2s;
}

.enroll-button {
  width: 100%;
  padding: 16px 24px;
  border: none;
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.enroll-button:hover:not(:disabled) {
  background: var(--color-outline);
}

.enroll-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.secondary-button {
  padding: 0 24px;
  border: 1px solid var(--color-primary);
  background: transparent;
  color: var(--color-primary);
}

.secondary-button:hover {
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.empty-state {
  min-height: 420px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 18px;
  color: var(--color-on-surface-variant);
}

.empty-state h1 {
  margin: 0;
  color: var(--color-primary);
  font-family: 'Bodoni Moda', serif;
  font-size: clamp(32px, 5vw, 48px);
  font-weight: 600;
}

.skeleton-title {
  width: min(760px, 90%);
  height: 86px;
  border-radius: 24px;
}

.skeleton-cover {
  aspect-ratio: 16 / 9;
  width: 100%;
  border-radius: 48px;
}

.skeleton-line {
  width: min(680px, 100%);
  height: 20px;
  border-radius: var(--radius-sm);
}

.skeleton-line.short {
  width: min(420px, 68%);
}

.skeleton-rail {
  min-height: 360px;
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

@media (max-width: 1024px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }

  .primary-column,
  .action-rail {
    grid-column: auto;
  }

  .action-rail {
    position: static;
  }
}

@media (max-width: 640px) {
  .course-detail-page {
    padding-bottom: 32px;
  }

  .back-link {
    margin-bottom: 32px;
  }

  .primary-column {
    gap: 28px;
  }

  .course-cover {
    border-radius: 28px;
  }

  .action-rail {
    padding: 24px;
    border-radius: 28px;
  }

  .date-list div,
  .capacity-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .date-list strong,
  .capacity-header strong {
    text-align: left;
  }
}
</style>
