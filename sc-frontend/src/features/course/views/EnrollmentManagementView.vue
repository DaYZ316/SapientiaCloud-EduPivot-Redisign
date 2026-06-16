<template>
  <div class="enrollment-management">
    <div class="page-header">
      <h1>{{ t('enrollmentManagement.title') }}</h1>
    </div>

    <div class="filter-bar">
      <BaseSelect
          v-model="selectedCourseId"
          :options="courseFilterOptions"
          min-width="240px"
          @change="resetAndLoad"
      />
    </div>

    <div v-if="enrollments.length > 0" class="enrollment-table-container">
      <table class="enrollment-table">
        <thead>
        <tr>
          <th>{{ t('enrollmentManagement.student') }}</th>
          <th>{{ t('enrollmentManagement.course') }}</th>
          <th>{{ t('enrollmentManagement.status') }}</th>
          <th>{{ t('enrollmentManagement.enrolledAt') }}</th>
          <th>{{ t('enrollmentManagement.completedAt') }}</th>
          <th>{{ t('enrollmentManagement.actions') }}</th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="enrollment in enrollments" :key="enrollment.id">
          <td>
            <div class="student-cell">
              <strong>{{ enrollment.studentName || '--' }}</strong>
            </div>
          </td>
          <td>
            <div class="course-cell">
              <div class="course-cover-sm">
                <img v-if="enrollment.courseCoverUrl" :src="enrollment.courseCoverUrl" alt=""/>
                <BookOpen v-else :size="16" stroke-width="1.5"/>
              </div>
              <span>{{ enrollment.courseTitle || '--' }}</span>
            </div>
          </td>
          <td>
              <span :class="getStatusClass(enrollment.status)" class="status-badge">
                {{ EnrollmentStatus[enrollment.status] }}
              </span>
          </td>
          <td>{{ formatDate(enrollment.enrolledAt) }}</td>
          <td>{{ enrollment.completedAt ? formatDate(enrollment.completedAt) : '-' }}</td>
          <td>
            <div class="action-buttons">
              <button
                  v-if="enrollment.status === 0"
                  :title="t('enrollmentManagement.statusUpdate')"
                  class="btn-icon approve"
                  @click="handleStatusUpdate(enrollment, 1)"
              >
                <Check :size="15" stroke-width="1.8"/>
              </button>
              <button
                  v-if="enrollment.status === 1"
                  :title="t('enrollmentManagement.statusUpdate')"
                  class="btn-icon complete"
                  @click="handleStatusUpdate(enrollment, 2)"
              >
                <GraduationCap :size="15" stroke-width="1.8"/>
              </button>
            </div>
          </td>
        </tr>
        </tbody>
      </table>
    </div>

    <div v-else-if="loading" class="enrollment-table-container">
      <table class="enrollment-table">
        <thead>
        <tr>
          <th>{{ t('enrollmentManagement.student') }}</th>
          <th>{{ t('enrollmentManagement.course') }}</th>
          <th>{{ t('enrollmentManagement.status') }}</th>
          <th>{{ t('enrollmentManagement.enrolledAt') }}</th>
          <th>{{ t('enrollmentManagement.completedAt') }}</th>
          <th>{{ t('enrollmentManagement.actions') }}</th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="n in 5" :key="n">
          <td>
            <div class="skeleton-line shimmer"></div>
          </td>
          <td>
            <div class="skeleton-line wide shimmer"></div>
          </td>
          <td>
            <div class="skeleton-pill shimmer"></div>
          </td>
          <td>
            <div class="skeleton-line date shimmer"></div>
          </td>
          <td>
            <div class="skeleton-line date shimmer"></div>
          </td>
          <td>
            <div class="skeleton-actions shimmer"></div>
          </td>
        </tr>
        </tbody>
      </table>
    </div>

    <div v-else class="empty-state">
      <ClipboardList :size="48" stroke-width="1.2"/>
      <h3>{{ t('enrollmentManagement.noEnrollments') }}</h3>
      <p>{{ t('enrollmentManagement.noEnrollmentsDesc') }}</p>
    </div>

    <div v-if="totalPages > 1" class="pagination">
      <button :disabled="currentPage === 1" class="btn-page" @click="changePage(currentPage - 1)">
        {{ t('enrollmentManagement.previous') }}
      </button>
      <div class="page-numbers">
        <button
            v-for="page in displayedPages"
            :key="page"
            :class="{active: currentPage === page}"
            class="btn-page"
            @click="changePage(page)"
        >
          {{ page }}
        </button>
      </div>
      <button :disabled="currentPage === totalPages" class="btn-page" @click="changePage(currentPage + 1)">
        {{ t('enrollmentManagement.next') }}
      </button>
    </div>
  </div>

  <BaseConfirmDialog
      :cancel-text="t('enrollmentManagement.cancel')"
      :close-label="t('enrollmentManagement.cancel')"
      :confirm-text="t('enrollmentManagement.confirm')"
      :message="t('enrollmentManagement.confirmMessage')"
      :title="t('enrollmentManagement.confirmTitle')"
      :visible="showConfirmDialog"
      @cancel="handleConfirmNo"
      @confirm="handleConfirmYes"
  >
    <label class="suppress-label">
      <input v-model="suppressConfirm" type="checkbox"/>
      {{ t('enrollmentManagement.suppressConfirm') }}
    </label>
  </BaseConfirmDialog>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {BookOpen, Check, ClipboardList, GraduationCap} from 'lucide-vue-next'

import BaseConfirmDialog from '@/shared/components/BaseConfirmDialog.vue'
import BaseSelect from '@/shared/components/BaseSelect.vue'
import {getCourseEnrollments, getTeacherCourses, updateEnrollmentStatus} from '@/features/course/api/course'
import type {Course, Enrollment} from '@/features/course/types/course'
import {EnrollmentStatus} from '@/features/course/types/course'
import {notify} from '@/shared/composables/useGlobalNotification'

const {t, locale} = useI18n()

const currentPage = ref(1)
const totalPages = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const enrollments = ref<Enrollment[]>([])
const courses = ref<Course[]>([])
const selectedCourseId = ref<string | undefined>(undefined)
const showConfirmDialog = ref(false)
const confirmPending = ref<{ enrollment: Enrollment; newStatus: number } | null>(null)
const suppressConfirm = ref(sessionStorage.getItem('enrollmentSuppressConfirm') === '1')

type SelectOption = { label: string; value: string | undefined }

const courseFilterOptions = computed<SelectOption[]>(() => {
  const options: SelectOption[] = [{label: t('enrollmentManagement.allCourses'), value: undefined}]
  for (const course of courses.value) {
    options.push({label: course.title, value: course.id})
  }
  return options
})

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

function getStatusClass(status: number): string {
  const map: Record<number, string> = {0: 'pending', 1: 'active', 2: 'completed', 3: 'dropped'}
  return map[status] ?? ''
}

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  if (Number.isNaN(date.getTime())) return '-'
  return new Intl.DateTimeFormat(String(locale.value), {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}

async function loadCourses() {
  try {
    const response = await getTeacherCourses(1, 100, 'primary')
    courses.value = response.records
  } catch (error) {
    console.error('Failed to load courses:', error)
  }
}

async function loadEnrollments() {
  loading.value = true
  try {
    if (selectedCourseId.value) {
      const response = await getCourseEnrollments(selectedCourseId.value, currentPage.value, pageSize.value)
      enrollments.value = response.records
      totalPages.value = Math.ceil(response.total / pageSize.value)
    } else {
      // Load enrollments for all teacher's courses
      const allEnrollments: Enrollment[] = []
      for (const course of courses.value) {
        try {
          const response = await getCourseEnrollments(course.id, 1, 100)
          allEnrollments.push(...response.records)
        } catch {
          // Skip courses that fail to load
        }
      }
      // Sort by enrolledAt descending
      allEnrollments.sort((a, b) => new Date(b.enrolledAt).getTime() - new Date(a.enrolledAt).getTime())
      // Client-side pagination
      const total = allEnrollments.length
      totalPages.value = Math.ceil(total / pageSize.value)
      const start = (currentPage.value - 1) * pageSize.value
      enrollments.value = allEnrollments.slice(start, start + pageSize.value)
    }
  } catch (error) {
    console.error('Failed to load enrollments:', error)
    notify.error(t('enrollmentManagement.alert.loadFailed'))
    enrollments.value = []
  } finally {
    loading.value = false
  }
}

function changePage(page: number) {
  currentPage.value = page
  loadEnrollments()
}

function resetAndLoad() {
  currentPage.value = 1
  loadEnrollments()
}

function handleStatusUpdate(enrollment: Enrollment, newStatus: number) {
  if (suppressConfirm.value) {
    executeStatusUpdate(enrollment, newStatus)
    return
  }
  confirmPending.value = {enrollment, newStatus}
  showConfirmDialog.value = true
}

function handleConfirmYes() {
  if (confirmPending.value) {
    executeStatusUpdate(confirmPending.value.enrollment, confirmPending.value.newStatus)
  }
  if (suppressConfirm.value) {
    sessionStorage.setItem('enrollmentSuppressConfirm', '1')
  } else {
    sessionStorage.removeItem('enrollmentSuppressConfirm')
  }
  showConfirmDialog.value = false
  confirmPending.value = null
}

function handleConfirmNo() {
  suppressConfirm.value = sessionStorage.getItem('enrollmentSuppressConfirm') === '1'
  showConfirmDialog.value = false
  confirmPending.value = null
}


async function executeStatusUpdate(enrollment: Enrollment, newStatus: number) {
  try {
    await updateEnrollmentStatus(enrollment.id, newStatus)
    notify.success(t('enrollmentManagement.alert.statusUpdated'))
    await loadEnrollments()
  } catch (error) {
    console.error('Failed to update enrollment status:', error)
    notify.error(t('enrollmentManagement.alert.statusUpdateFailed'))
  }
}

onMounted(async () => {
  await loadCourses()
  await loadEnrollments()
})
</script>

<style scoped>
.enrollment-management {
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
  font-family: var(--font-heading);
  font-size: 48px;
  font-weight: 400;
  line-height: 1.3;
  color: var(--color-on-surface);
}

.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}

.enrollment-table-container {
  width: 100%;
  margin-bottom: 32px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-md);
  overflow-x: auto;
  overflow-y: hidden;
}

.enrollment-table {
  width: 100%;
  min-width: 800px;
  border-collapse: collapse;
}

.enrollment-table th {
  padding: 14px 18px;
  text-align: left;
  background: var(--color-surface-canvas);
  border-bottom: 1px solid var(--color-outline-light);
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  white-space: nowrap;
}

.enrollment-table td {
  padding: 14px 18px;
  border-bottom: 1px solid var(--color-surface-canvas);
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 14px;
  vertical-align: middle;
  white-space: nowrap;
}

.enrollment-table tr:last-child td {
  border-bottom: 0;
}

.enrollment-table tbody tr {
  transition: background 0.2s;
}

.enrollment-table tbody tr:hover {
  background: var(--color-surface-canvas);
}

.student-cell strong {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-on-surface);
}

.course-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 200px;
}

.course-cover-sm {
  width: 40px;
  height: 30px;
  display: grid;
  flex-shrink: 0;
  place-items: center;
  background: var(--color-surface-canvas);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  overflow: hidden;
}

.course-cover-sm img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.course-cell span {
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 200px;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 10px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 800;
}

.status-badge.pending {
  background: var(--color-surface-canvas);
  color: var(--color-muted);
}

.status-badge.active {
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.status-badge.completed {
  background: var(--color-surface-container);
  color: var(--color-on-surface);
}

.status-badge.dropped {
  background: var(--color-surface-container-high);
  color: var(--color-error);
}

.action-buttons {
  display: flex;
  align-items: center;
  gap: 6px;
}

.btn-icon {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  background: transparent;
  border: 0;
  border-radius: var(--radius-sm);
  color: var(--color-muted);
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}

.btn-icon:hover {
  background: var(--color-surface-container);
  color: var(--color-on-surface);
}

.btn-icon.approve:hover {
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.btn-icon.complete:hover {
  background: var(--color-surface-container);
  color: var(--color-on-surface);
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
  border-radius: var(--radius-sm);
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  font-weight: 400;
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

.skeleton-line {
  height: 16px;
  border-radius: var(--radius-sm);
  background: var(--color-surface-canvas);
  width: 120px;
}

.skeleton-line.wide {
  width: 180px;
}

.skeleton-line.date {
  width: 90px;
}

.skeleton-pill {
  width: 64px;
  height: 24px;
  border-radius: var(--radius-sm);
  background: var(--color-surface-canvas);
}

.skeleton-actions {
  width: 48px;
  height: 32px;
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
  0% {
    transform: translateX(-100%);
  }
  100% {
    transform: translateX(100%);
  }
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

  .filter-bar {
    flex-direction: column;
  }

  .pagination {
    flex-wrap: wrap;
  }
}

.suppress-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-body);
  font-size: 13px;
  color: var(--color-muted);
  cursor: pointer;
  user-select: none;
}

.suppress-label input[type="checkbox"] {
  width: 16px;
  height: 16px;
  accent-color: var(--color-primary);
  cursor: pointer;
}</style>
