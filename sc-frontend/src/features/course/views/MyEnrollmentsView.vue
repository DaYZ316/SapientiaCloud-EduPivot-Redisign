<template>
  <div class="enrollments-page">
    <div class="page-header">
      <h1>{{ t('myEnrollments.title') }}</h1>
    </div>

    <div v-if="enrollments.length > 0" class="enrollments-list">
      <div v-for="enrollment in enrollments" :key="enrollment.id" class="enrollment-card">
        <div class="enrollment-cover">
          <img v-if="enrollment.courseCoverUrl" :src="enrollment.courseCoverUrl" alt="Cover"/>
          <BookOpen v-else :size="24" stroke-width="1.5"/>
        </div>
        <div class="enrollment-info">
          <h3>{{ enrollment.courseTitle || t('myEnrollments.unknownCourse') }}</h3>
          <div class="enrollment-meta">
            <span class="enrollment-status-badge" :class="getEnrollStatusClass(enrollment.status)">
              {{ EnrollmentStatus[enrollment.status] }}
            </span>
            <span class="enrollment-date">
              {{ t('myEnrollments.enrolled') }}{{ formatDate(enrollment.enrolledAt) }}
            </span>
            <span v-if="enrollment.completedAt" class="completion-date">
              {{ t('myEnrollments.completed') }}{{ formatDate(enrollment.completedAt) }}
            </span>
          </div>
        </div>
        <div class="enrollment-actions">
          <button class="btn-text" @click="viewCourse(enrollment.courseId)">{{ t('myEnrollments.viewCourse') }}</button>
          <button
            v-if="enrollment.status === 1"
            class="btn-icon-sm btn-danger"
            title="Drop course"
            @click="confirmDrop(enrollment)"
          >
            <X :size="14"/>
          </button>
        </div>
      </div>
    </div>

    <div v-else-if="loading" class="enrollments-list">
      <div v-for="n in 5" :key="n" class="skeleton-enrollment">
        <div class="skeleton-enroll-cover shimmer"></div>
        <div class="skeleton-enroll-body">
          <div class="skeleton-title shimmer"></div>
          <div class="skeleton-meta">
            <div class="skeleton-tag shimmer"></div>
            <div class="skeleton-tag wide shimmer"></div>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="empty-state">
      <GraduationCap :size="48" stroke-width="1.2"/>
      <h3>{{ t('myEnrollments.noEnrollments') }}</h3>
      <p>{{ t('myEnrollments.noEnrollmentsDesc') }}</p>
      <button class="btn-primary" @click="router.push('/courses')">{{ t('myEnrollments.browseCourses') }}</button>
    </div>

    <div v-if="totalPages > 1" class="pagination">
      <button class="btn-page" :disabled="currentPage === 1" @click="changePage(currentPage - 1)">
        {{ t('myEnrollments.previous') }}
      </button>
      <div class="page-numbers">
        <button
          v-for="page in displayedPages"
          :key="page"
          class="btn-page"
          :class="{ active: currentPage === page }"
          @click="changePage(page)"
        >
          {{ page }}
        </button>
      </div>
      <button class="btn-page" :disabled="currentPage === totalPages" @click="changePage(currentPage + 1)">
        {{ t('myEnrollments.next') }}
      </button>
    </div>

    <Teleport to="body">
      <div v-if="showDropModal" class="modal-overlay">
        <div class="modal modal-sm">
          <div class="modal-header">
            <h2>{{ t('myEnrollments.dropModal.title') }}</h2>
            <button class="btn-close" @click="showDropModal = false">
              <X :size="20"/>
            </button>
          </div>
          <div class="modal-body">
            <p>{{ t('myEnrollments.dropModal.confirmMessage', {title: dropTarget?.courseTitle}) }}</p>
          </div>
          <div class="modal-footer">
            <button class="btn-secondary" @click="showDropModal = false">{{ t('myEnrollments.dropModal.cancel') }}</button>
            <button class="btn-danger" @click="handleDrop">{{ t('myEnrollments.dropModal.confirm') }}</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import {BookOpen, GraduationCap, X} from 'lucide-vue-next'

import {dropCourse, getMyEnrollments} from '@/features/course/api/course'
import {EnrollmentStatus} from '@/features/course/types/course'
import type {Enrollment} from '@/features/course/types/course'
import {notify} from '@/shared/composables/useGlobalNotification'

const {t} = useI18n()
const router = useRouter()

const currentPage = ref(1)
const totalPages = ref(1)
const pageSize = ref(12)
const loading = ref(false)
const enrollments = ref<Enrollment[]>([])
const showDropModal = ref(false)
const dropTarget = ref<Enrollment | null>(null)

const displayedPages = computed(() => {
  const pages: number[] = []
  const maxDisplay = 5
  let start = Math.max(1, currentPage.value - Math.floor(maxDisplay / 2))
  let end = Math.min(totalPages.value, start + maxDisplay - 1)
  if (end - start + 1 < maxDisplay) {
    start = Math.max(1, end - maxDisplay + 1)
  }
  for (let i = start; i <= end; i++) {
    pages.push(i)
  }
  return pages
})

function getEnrollStatusClass(status: number): string {
  const map: Record<number, string> = {0: 'pending', 1: 'active', 2: 'completed', 3: 'dropped'}
  return map[status] ?? ''
}

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('en-US', {month: 'short', day: 'numeric', year: 'numeric'})
}

async function loadData() {
  loading.value = true
  try {
    const response = await getMyEnrollments(currentPage.value, pageSize.value)
    enrollments.value = response.records
    totalPages.value = Math.ceil(response.total / pageSize.value)
  } catch (error) {
    console.error('Failed to load enrollments:', error)
    notify.error('Failed to load enrollments')
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
.enrollments-page {
  max-width: 100%;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
}

.page-header h1 {
  margin: 0;
  font-family: 'Bodoni Moda', serif;
  font-size: 48px;
  font-weight: 600;
  color: var(--color-on-surface);
}

.enrollments-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 32px;
}

.enrollment-card,
.skeleton-enrollment {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 20px 24px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 16px;
}

.enrollment-card {
  transition: all 0.2s;
}

.enrollment-card:hover {
  border-color: var(--color-on-surface);
}

.enrollment-cover,
.skeleton-enroll-cover {
  width: 80px;
  height: 60px;
  border-radius: 8px;
  flex-shrink: 0;
}

.enrollment-cover {
  display: grid;
  place-items: center;
  background: var(--color-surface-canvas);
  color: var(--color-on-surface);
  overflow: hidden;
}

.enrollment-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.enrollment-info,
.skeleton-enroll-body {
  flex: 1;
  min-width: 0;
}

.enrollment-info h3 {
  margin: 0 0 8px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-on-surface);
}

.enrollment-meta,
.skeleton-meta {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.skeleton-meta {
  margin-top: 16px;
}

.enrollment-status-badge {
  padding: 4px 10px;
  border-radius: var(--radius-sm);
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 11px;
  font-weight: 600;
}

.enrollment-status-badge.pending {
  background: var(--color-surface-canvas);
  color: var(--color-muted);
}

.enrollment-status-badge.active {
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.enrollment-status-badge.completed {
  background: var(--color-surface-container);
  color: var(--color-on-surface);
}

.enrollment-status-badge.dropped {
  background: var(--color-surface-container-high);
  color: var(--color-error);
}

.enrollment-date,
.completion-date {
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 12px;
  color: var(--color-muted);
}

.enrollment-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.btn-text {
  padding: 0;
  background: none;
  border: none;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-on-surface);
  cursor: pointer;
}

.btn-text:hover {
  text-decoration: underline;
}

.btn-icon-sm {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  background: none;
  border: none;
  border-radius: 8px;
  color: var(--color-muted);
  cursor: pointer;
  transition: all 0.2s;
}

.btn-icon-sm:hover {
  background: var(--color-surface-canvas);
  color: var(--color-on-surface);
}

.btn-icon-sm.btn-danger:hover {
  background: var(--color-surface-container-high);
  color: var(--color-error);
}

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
  font-family: 'Bodoni Moda', serif;
  font-size: 24px;
  font-weight: 600;
  color: var(--color-on-surface);
}

.empty-state p {
  margin: 0 0 24px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
}

.page-numbers {
  display: flex;
  gap: 4px;
}

.btn-page {
  padding: 10px 16px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 8px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-on-surface);
  cursor: pointer;
  transition: all 0.2s;
}

.btn-page:hover:not(:disabled) {
  background: var(--color-surface-container);
}

.btn-page.active,
.btn-page.active:hover:not(:disabled) {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-on-primary);
}

.btn-page:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.modal-overlay {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: var(--color-overlay);
  z-index: 1000;
}

.modal {
  width: 100%;
  max-width: 400px;
  max-height: calc(100dvh - 48px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 24px;
  box-shadow: 0 24px 80px rgba(0, 0, 0, 0.16);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 28px 0;
}

.modal-header h2 {
  margin: 0;
  font-family: 'Bodoni Moda', serif;
  font-size: 24px;
  font-weight: 600;
  color: var(--color-on-surface);
}

.btn-close {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  background: none;
  border: none;
  border-radius: 8px;
  color: var(--color-muted);
  cursor: pointer;
  transition: all 0.2s;
}

.btn-close:hover {
  background: var(--color-surface-canvas);
  color: var(--color-on-surface);
}

.modal-body {
  padding: 24px 28px;
  overflow-y: auto;
}

.modal-body p {
  margin: 0;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  color: var(--color-on-surface);
  line-height: 1.6;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 0 28px 24px;
  flex-shrink: 0;
}

.btn-primary,
.btn-secondary,
.btn-danger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px 28px;
  border-radius: var(--radius-sm);
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary {
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  color: var(--color-on-primary);
}

.btn-primary:hover {
    background: var(--color-primary-soft);
  border-color: var(--color-primary-soft);
}

.btn-secondary {
  background: transparent;
  border: 1px solid var(--color-on-surface);
  color: var(--color-on-surface);
}

.btn-secondary:hover {
  background: var(--color-on-surface);
  color: var(--color-on-primary);
}

.btn-danger {
  background: var(--color-error);
  color: #fff;
  border: none;
}

.btn-danger:hover {
  background: var(--color-on-surface);
  color: var(--color-on-primary);
}

.skeleton-title {
  height: 20px;
  width: 50%;
  border-radius: 6px;
  background: var(--color-surface-canvas);
  margin-bottom: 12px;
}

.skeleton-tag {
  height: 22px;
  width: 56px;
  border-radius: var(--radius-sm);
  background: var(--color-surface-canvas);
}

.skeleton-tag.wide {
  width: 80px;
}

.skeleton-enroll-cover {
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

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }

  .page-header h1 {
    font-size: 32px;
  }

  .enrollment-card,
  .skeleton-enrollment {
    align-items: flex-start;
    flex-direction: column;
  }

  .enrollment-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .pagination {
    flex-wrap: wrap;
  }
}
</style>
