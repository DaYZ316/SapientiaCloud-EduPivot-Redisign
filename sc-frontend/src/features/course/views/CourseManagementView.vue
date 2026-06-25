<template>
  <div class="enrollments-page" :class="{ 'my-courses-page': isTeacherPage }">
    <template v-if="isTeacherPage">
      <section class="courses-command-bar" aria-labelledby="teacher-my-courses-title">
        <div class="courses-title-block">
          <p class="section-kicker">{{ t('myEnrollments.teacher.workspace') }}</p>
          <h1 id="teacher-my-courses-title">{{ t('myEnrollments.title') }}</h1>
          <p>{{ teacherRoleDescription }}</p>
        </div>

        <div class="courses-tools" role="search">
          <label class="search-field" for="teacher-course-search">
            <Search :size="16" stroke-width="1.7" />
            <input
              id="teacher-course-search"
              v-model.trim="searchKeyword"
              type="search"
              :placeholder="t('myEnrollments.searchPlaceholder')"
            />
          </label>
          <div class="teacher-course-tabs">
            <button
              type="button"
              class="teacher-course-tab"
              :class="{ active: teacherCourseRole === 'primary' }"
              @click="switchTeacherRole('primary')"
            >
              {{ t('courses.teacherTabs.primary') }}
            </button>
            <button
              type="button"
              class="teacher-course-tab"
              :class="{ active: teacherCourseRole === 'assistant' }"
              @click="switchTeacherRole('assistant')"
            >
              {{ t('courses.teacherTabs.assistant') }}
            </button>
          </div>
          <button v-if="canCreateCourse" class="continue-button" type="button" @click="openCreateModal">
            <Plus :size="16" stroke-width="1.8" />
            <span>{{ t('courses.createCourse') }}</span>
          </button>
          <div class="student-chip">
            <img v-if="authStore.user?.avatarUrl" :src="authStore.user.avatarUrl" :alt="t('myEnrollments.teacher.avatarAlt')" />
            <span v-else class="student-chip__avatar">{{ teacherInitials }}</span>
            <span>{{ authStore.user?.displayName || t('myEnrollments.teacher.defaultName') }}</span>
          </div>
        </div>
      </section>

      <section class="course-stats" :aria-label="t('myEnrollments.overviewAria')">
        <article v-for="stat in teacherStats" :key="stat.label" class="stat-cell">
          <span>{{ stat.label }}</span>
          <strong>{{ stat.value }}</strong>
          <small>{{ stat.note }}</small>
        </article>
      </section>

      <section class="courses-workbench">
        <div class="courses-primary">
          <div class="panel-heading">
            <div>
              <p class="section-kicker">{{ t('myEnrollments.teacher.teachingCourses') }}</p>
              <h2>{{ teacherCourseRoleLabel }}</h2>
            </div>
            <span>{{ t('myEnrollments.courseCount', { count: filteredTeacherCourses.length }) }}</span>
          </div>

          <div v-if="loading" class="course-list" :aria-label="t('myEnrollments.loadingAria')">
            <div v-for="n in 5" :key="n" class="course-row skeleton-row">
              <div class="skeleton-block course-mark"></div>
              <div class="skeleton-copy">
                <div class="skeleton-line title"></div>
                <div class="skeleton-line"></div>
              </div>
              <div class="skeleton-line action"></div>
            </div>
          </div>

          <div v-else-if="filteredTeacherCourses.length > 0" class="course-list">
            <article v-for="course in filteredTeacherCourses" :key="course.id" class="course-row">
              <div class="course-mark">
                <img
                  :src="getCourseCoverUrl(course.coverUrl)"
                  :alt="t('myEnrollments.courseCoverAlt', { title: course.title })"
                  @error="handleCourseCoverError"
                />
              </div>

              <div class="course-main">
                <div class="course-title-line">
                  <h3>{{ course.title }}</h3>
                  <span class="status-tag role-tag" :class="course.roleClass">{{ course.roleLabel }}</span>
                  <span v-if="course.statusLabel" class="status-tag" :class="course.statusClass">{{ course.statusLabel }}</span>
                </div>
                <div class="course-meta">
                  <span>{{ course.teacher }}</span>
                  <span>{{ course.schedule }}</span>
                  <span>{{ course.recentActivity }}</span>
                </div>
                <div class="progress-track" :aria-label="t('myEnrollments.classHourProgressAria', { title: course.title, progress: course.progress })">
                  <span :style="{ width: `${course.progress}%` }"></span>
                </div>
              </div>

              <div class="course-progress">
                <strong>{{ course.progress }}%</strong>
                <small>{{ t('myEnrollments.teacher.classHourProgress') }}</small>
              </div>

              <div class="course-actions">
                <button class="continue-button" type="button" @click="viewCourse(course.courseId)">
                  {{ t('myEnrollments.viewCourse') }}
                </button>
                <button
                  v-if="course.canEdit"
                  class="drop-button"
                  type="button"
                  :title="t('courses.edit')"
                  :aria-label="t('courses.edit')"
                  @click="editCourse(course.source)"
                >
                  <Pencil :size="14" stroke-width="1.8" />
                </button>
                <button
                  v-if="course.canInviteAssistant"
                  class="drop-button"
                  type="button"
                  :title="t('enrollmentManagement.inviteAssistant')"
                  :aria-label="t('enrollmentManagement.inviteAssistant')"
                  @click="openInviteModal(course.source)"
                >
                  <UserPlus :size="14" stroke-width="1.8" />
                </button>
                <button
                  v-if="course.canEdit"
                  class="drop-button danger"
                  type="button"
                  :title="t('courses.delete')"
                  :aria-label="t('courses.delete')"
                  @click="confirmDeleteCourse(course.source)"
                >
                  <Trash2 :size="14" stroke-width="1.8" />
                </button>
              </div>
            </article>
          </div>

          <div v-else class="empty-state">
            <GraduationCap :size="46" stroke-width="1.2" />
            <h3>{{ teacherEmptyTitle }}</h3>
            <p>{{ teacherEmptyDescription }}</p>
            <button v-if="canCreateCourse" class="continue-button" type="button" @click="openCreateModal">
              {{ t('courses.createCourse') }}
            </button>
          </div>

          <div v-if="!loading && totalPages > 1" class="pagination">
            <button class="page-button" :disabled="currentPage === 1" type="button" @click="changePage(currentPage - 1)">
              {{ t('myEnrollments.previous') }}
            </button>
            <button
              v-for="page in displayedPages"
              :key="page"
              class="page-button"
              :class="{ active: currentPage === page }"
              type="button"
              @click="changePage(page)"
            >
              {{ page }}
            </button>
            <button class="page-button" :disabled="currentPage === totalPages" type="button" @click="changePage(currentPage + 1)">
              {{ t('myEnrollments.next') }}
            </button>
          </div>
        </div>

        <aside class="courses-sidebar" :aria-label="t('myEnrollments.teacher.collaborationAria')">
          <section class="side-panel">
            <div class="panel-heading compact">
              <h2>{{ t('myEnrollments.teacher.roleDescriptionTitle') }}</h2>
              <CalendarDays :size="18" stroke-width="1.6" />
            </div>
            <div class="task-list">
              <div v-for="item in teacherRoleNotes" :key="item.title" class="task-item">
                <span class="task-time">{{ item.label }}</span>
                <div>
                  <strong>{{ item.title }}</strong>
                  <small>{{ item.note }}</small>
                </div>
              </div>
            </div>
          </section>

          <section class="side-panel">
            <div class="panel-heading compact">
              <h2>{{ t('myEnrollments.teacher.statusTitle') }}</h2>
              <Activity :size="18" stroke-width="1.6" />
            </div>
            <div class="deadline-list">
              <div v-for="summary in teacherStatusSummaries" :key="summary.title" class="deadline-item">
                <div>
                  <strong>{{ summary.title }}</strong>
                  <small>{{ summary.note }}</small>
                </div>
                <span :class="summary.tone">{{ summary.count }}</span>
              </div>
            </div>
          </section>

          <section class="side-panel">
            <div class="panel-heading compact">
              <h2>{{ t('myEnrollments.teacher.classHoursTitle') }}</h2>
              <BookOpen :size="18" stroke-width="1.6" />
            </div>
            <div class="rhythm-bars" :aria-label="t('myEnrollments.teacher.classHoursAria')">
              <span
                v-for="(value, index) in progressBars"
                :key="index"
                :style="{ height: `${value}%` }"
              ></span>
            </div>
            <p class="rhythm-note">{{ progressNote }}</p>
          </section>
        </aside>
      </section>

    </template>

    <template v-else>
      <!-- Page Header -->
      <div class="page-header">
        <h1>{{ pageTitle }}</h1>
        <button v-if="canCreateCourse" class="btn-primary" @click="openCreateModal">
          <Plus :size="16" />
          {{ t('courses.createCourse') }}
        </button>
      </div>

      <!-- Filter Bar -->
      <div v-if="isAdmin" class="filter-bar">
        <div class="search-input">
          <Search :size="18" />
          <input v-model="searchKeyword" :placeholder="t('courses.searchPlaceholder')" @keyup.enter="resetAndLoad" />
        </div>
        <BaseSelect v-model="filterLevel" :options="levelFilterOptions" min-width="148px" @change="resetAndLoad" />
        <BaseSelect v-model="filterStatus" :options="statusFilterOptions" min-width="148px" @change="resetAndLoad" />
      </div>

      <!-- Date Range Filters -->
      <div v-if="isAdmin" class="date-filter-bar">
        <BaseDateRangeFilter
          v-model:start="createdAtStart"
          v-model:end="createdAtEnd"
          id-prefix="course-created-at"
          :label="t('courses.createdAt')"
        />
        <BaseDateRangeFilter
          v-model:start="updatedAtStart"
          v-model:end="updatedAtEnd"
          id-prefix="course-updated-at"
          :label="t('courses.updatedAt')"
        />
        <div class="date-filter-actions">
          <button
            class="btn-date-filter btn-date-filter-secondary"
            type="button"
            :disabled="!hasDateFilters"
            @click="clearDateFilters"
          >
            <X :size="15" stroke-width="2" />
            {{ t('courses.clearFilters') }}
          </button>
          <button class="btn-date-filter btn-date-filter-primary" type="button" @click="resetAndLoad">
            <Search :size="15" stroke-width="2" />
            {{ t('courses.search') }}
          </button>
        </div>
      </div>

      <CourseManagementTable
        v-if="courses.length > 0 || loading"
        :courses="courses"
        :loading="loading"
        :editable="canManageCourses"
        :can-invite-assistant="canInviteAssistant"
        @view="viewCourse"
        @edit="editCourse"
        @delete="confirmDeleteCourse"
        @invite="openInviteModal"
      />

      <div v-else class="empty-state">
        <BookOpen :size="48" stroke-width="1.2" />
        <h3>{{ t('myEnrollments.noCourses') }}</h3>
      </div>

      <!-- Pagination -->
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
    </template>

    <CourseFormModal
      :visible="showCreateModal"
      mode="create"
      :submitting="submitting"
      @close="closeCreateModal"
      @created="submitCourse"
    />

    <CourseFormModal
      :visible="showEditModal"
      mode="edit"
      :course="editingCourse"
      :can-edit-course-status="canEditCourseStatus"
      :submitting="submitting"
      @close="closeEditModal"
      @updated="submitEditCourse"
    />

    <BaseConfirmDialog
      :visible="showDeleteModal"
      :title="t('courses.deleteModal.title')"
      :message="t('courses.deleteModal.confirmMessage', { title: deleteTarget?.title })"
      :cancel-text="t('courses.deleteModal.cancel')"
      :confirm-text="t('courses.deleteModal.confirm')"
      :close-label="t('courses.deleteModal.cancel')"
      confirm-variant="danger"
      @cancel="showDeleteModal = false"
      @confirm="handleDeleteCourse"
    />

    <!-- Invite Assistant Modal -->
    <InviteAssistantModal
      v-model="showInviteModal"
      :course-id="inviteTarget?.id ?? ''"
      :course-teacher-id="inviteTarget?.teacherId ?? ''"
      :assistants="inviteTarget?.teacherInfos ?? []"
      :is-admin="isAdmin"
      @invited="onInviteSuccess"
    />
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import {
  Activity,
  BookOpen,
  CalendarDays,
  GraduationCap,
  Pencil,
  Plus,
  Search,
  Trash2,
  UserPlus,
  X,
} from 'lucide-vue-next'

import CourseFormModal from '@/features/course/components/CourseFormModal.vue'
import CourseManagementTable from '@/features/course/components/CourseManagementTable.vue'
import BaseConfirmDialog from '@/shared/components/BaseConfirmDialog.vue'
import BaseDateRangeFilter from '@/shared/components/BaseDateRangeFilter.vue'
import BaseSelect from '@/shared/components/BaseSelect.vue'
import { notify } from '@/shared/composables/useGlobalNotification'
import InviteAssistantModal from '@/features/course/components/InviteAssistantModal.vue'
import { getAvatarInitials } from '@/shared/utils/avatar'
import { getCourseCoverUrl, handleCourseCoverError } from '@/shared/utils/courseCover'

import { createCourse, deleteCourse, getCourses, getTeacherCourses, updateCourse } from '@/features/course/api/course'
import { useAuthStore } from '@/features/auth/stores/auth'
import type {
  Course,
  CreateCourseRequest,
  TeacherCourseRole,
  UpdateCourseRequest,
} from '@/features/course/types/course'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const isAdmin = computed(() => authStore.user?.role === 0)
const isTeacher = computed(() => authStore.user?.role === 2)
const isTeacherPage = computed(() => route.name === 'teacher-courses')
const isPrimaryTeacherCourses = computed(() => teacherCourseRole.value === 'primary')
const canManageCourses = computed(
  () => isAdmin.value || (isTeacher.value && isTeacherPage.value && isPrimaryTeacherCourses.value),
)
const canInviteAssistant = computed(() => canManageCourses.value)
const canCreateCourse = computed(() => canManageCourses.value)
const canEditCourseStatus = computed(() => canManageCourses.value)
const pageTitle = computed(() => (isAdmin.value ? t('common.navigation.courseManagement') : t('myEnrollments.title')))

// Shared state
const currentPage = ref(1)
const totalPages = ref(1)
const pageSize = ref(12)
const loading = ref(false)
const submitting = ref(false)

// Filters (admin / teacher)
const searchKeyword = ref('')
const filterLevel = ref<number | undefined>(undefined)
const filterStatus = ref<number | undefined>(undefined)
const createdAtStart = ref('')
const createdAtEnd = ref('')
const updatedAtStart = ref('')
const updatedAtEnd = ref('')

type SelectOption = { label: string; value: string | number | undefined }

interface TeacherCourseWorkspaceItem {
  id: string
  courseId: string
  title: string
  teacher: string
  coverUrl: string | null
  schedule: string
  recentActivity: string
  progress: number
  statusLabel: string
  statusClass: string
  roleLabel: string
  roleClass: string
  canEdit: boolean
  canInviteAssistant: boolean
  source: Course
}

const levelFilterOptions = computed<SelectOption[]>(() => [
  { label: t('courses.allLevels'), value: undefined },
  { label: t('courses.level.beginner'), value: 1 },
  { label: t('courses.level.intermediate'), value: 2 },
  { label: t('courses.level.advanced'), value: 3 },
])

const statusFilterOptions = computed<SelectOption[]>(() => [
  { label: t('courses.allStatuses'), value: undefined },
  { label: t('courses.status.draft'), value: 0 },
  { label: t('courses.status.published'), value: 1 },
  { label: t('courses.status.archived'), value: 2 },
])

const hasDateFilters = computed(() =>
  Boolean(createdAtStart.value || createdAtEnd.value || updatedAtStart.value || updatedAtEnd.value),
)

// Admin / Teacher: course list
const courses = ref<Course[]>([])
const teacherCourseRole = ref<TeacherCourseRole>('primary')

const showCreateModal = ref(false)

// Edit course (admin / teacher)
const showEditModal = ref(false)
const editingCourse = ref<Course | null>(null)

// Delete course (admin / teacher)
const showDeleteModal = ref(false)
const deleteTarget = ref<Course | null>(null)

// Invite assistant
const showInviteModal = ref(false)
const inviteTarget = ref<Course | null>(null)

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

const teacherCourseRoleLabel = computed(() =>
  teacherCourseRole.value === 'assistant' ? t('courses.teacherTabs.assistant') : t('courses.teacherTabs.primary'),
)

const teacherRoleDescription = computed(() =>
  teacherCourseRole.value === 'assistant'
    ? t('myEnrollments.teacher.assistantDescription')
    : t('myEnrollments.teacher.primaryDescription'),
)

const teacherInitials = computed(() => getAvatarInitials(authStore.user?.displayName))

const teacherCourseItems = computed<TeacherCourseWorkspaceItem[]>(() =>
  courses.value.map((course) => {
    const statusLabel = course.status === 1 ? '' : getStatusLabel(course.status)
    const roleLabel = teacherCourseRole.value === 'assistant' ? t('courses.teacherTabs.assistant') : t('courses.teacherTabs.primary')

    return {
      id: course.id,
      courseId: course.id,
      title: course.title,
      teacher: teacherCourseRole.value === 'assistant'
        ? t('myEnrollments.teacher.primaryTeacherPrefix', { name: course.teacherName || t('courses.card.unset') })
        : t('myEnrollments.teacher.primaryTeacherName', {
            name: course.teacherName || authStore.user?.displayName || t('myEnrollments.teacher.defaultName'),
          }),
      coverUrl: course.coverUrl,
      schedule: course.semester || course.location || t('courses.card.unset'),
      recentActivity: formatCourseHours(course),
      progress: course.courseProgress ?? 0,
      statusLabel,
      statusClass: getCourseStatusClass(course.status),
      roleLabel,
      roleClass: teacherCourseRole.value,
      canEdit: canManageCourses.value,
      canInviteAssistant: canInviteAssistant.value,
      source: course,
    }
  }),
)

const filteredTeacherCourses = computed(() => {
  const value = searchKeyword.value.trim().toLowerCase()
  if (!value) return teacherCourseItems.value

  return teacherCourseItems.value.filter((course) =>
    [course.title, course.teacher, course.schedule, course.statusLabel].some((field) =>
      field.toLowerCase().includes(value),
    ),
  )
})

const draftCourseCount = computed(() => courses.value.filter((course) => course.status === 0).length)
const archivedCourseCount = computed(() => courses.value.filter((course) => course.status === 2).length)
const averageCourseProgress = computed(() => {
  if (courses.value.length === 0) return 0
  const total = courses.value.reduce((sum, course) => sum + (course.courseProgress ?? 0), 0)
  return Math.round(total / courses.value.length)
})

const teacherStats = computed(() => [
  {
    label: t('myEnrollments.teacher.stats.courseRole'),
    value: teacherCourseRoleLabel.value,
    note: teacherCourseRole.value === 'assistant'
      ? t('myEnrollments.teacher.stats.assistantNote')
      : t('myEnrollments.teacher.stats.primaryNote'),
  },
  {
    label: t('myEnrollments.teacher.stats.courseCount'),
    value: courses.value.length,
    note: t('myEnrollments.teacher.stats.currentList'),
  },
  {
    label: t('myEnrollments.teacher.stats.averageProgress'),
    value: `${averageCourseProgress.value}%`,
    note: t('myEnrollments.teacher.stats.byClassHours'),
  },
])

const teacherRoleNotes = computed(() =>
  teacherCourseRole.value === 'assistant'
    ? [
        {
          label: t('myEnrollments.teacher.roleNotes.role'),
          title: t('myEnrollments.teacher.roleNotes.assistantCourse'),
          note: t('myEnrollments.teacher.roleNotes.assistantCourseNote'),
        },
        {
          label: t('myEnrollments.teacher.roleNotes.scope'),
          title: t('myEnrollments.teacher.roleNotes.viewCourse'),
          note: t('myEnrollments.teacher.roleNotes.viewCourseNote'),
        },
        {
          label: t('myEnrollments.teacher.roleNotes.distinction'),
          title: t('myEnrollments.teacher.roleNotes.noManagementActions'),
          note: t('myEnrollments.teacher.roleNotes.noManagementActionsNote'),
        },
      ]
    : [
        {
          label: t('myEnrollments.teacher.roleNotes.role'),
          title: t('myEnrollments.teacher.roleNotes.primaryCourse'),
          note: t('myEnrollments.teacher.roleNotes.primaryCourseNote'),
        },
        {
          label: t('myEnrollments.teacher.roleNotes.scope'),
          title: t('myEnrollments.teacher.roleNotes.courseMaintenance'),
          note: t('myEnrollments.teacher.roleNotes.courseMaintenanceNote'),
        },
        {
          label: t('myEnrollments.teacher.roleNotes.collaboration'),
          title: t('myEnrollments.teacher.roleNotes.assistantManagement'),
          note: t('myEnrollments.teacher.roleNotes.assistantManagementNote'),
        },
      ],
)

const teacherStatusSummaries = computed(() => [
  {
    title: t('courses.status.draft'),
    note: t('myEnrollments.teacher.statusNotes.draft'),
    count: draftCourseCount.value,
    tone: 'muted',
  },
  {
    title: t('courses.status.archived'),
    note: t('myEnrollments.teacher.statusNotes.archived'),
    count: archivedCourseCount.value,
    tone: 'muted',
  },
])

const progressBars = computed(() => {
  const bars = teacherCourseItems.value.slice(0, 7).map((course) => Math.max(12, course.progress))
  while (bars.length < 7) {
    bars.push(12)
  }
  return bars
})

const progressNote = computed(() =>
  courses.value.length > 0
    ? t('myEnrollments.teacher.progressNote', { progress: averageCourseProgress.value })
    : t('myEnrollments.teacher.noProgressData'),
)

const teacherEmptyTitle = computed(() => {
  if (searchKeyword.value.trim()) return t('myEnrollments.noMatchedCourses')
  return teacherCourseRole.value === 'assistant' ? t('myEnrollments.teacher.noAssistantCourses') : t('myEnrollments.noCourses')
})

const teacherEmptyDescription = computed(() => {
  if (searchKeyword.value.trim()) return t('myEnrollments.noMatchedCoursesDesc')
  return teacherCourseRole.value === 'assistant'
    ? t('myEnrollments.teacher.noAssistantCoursesDesc')
    : t('myEnrollments.teacher.noPrimaryCoursesDesc')
})

function getStatusLabel(status: number): string {
  const map: Record<number, string> = {
    0: t('courses.status.draft'),
    1: t('courses.status.published'),
    2: t('courses.status.archived'),
  }

  return map[status] ?? '-'
}

function getCourseStatusClass(status: number): string {
  const map: Record<number, string> = { 0: 'draft', 1: 'active', 2: 'completed' }

  return map[status] ?? 'draft'
}

function formatCourseHours(course: Course): string {
  const publishedCount = course.publishedClassSessionCount ?? 0
  const totalClassHours = course.totalClassHours ?? 0
  if (totalClassHours <= 0) {
    return t('myEnrollments.classHoursUnset', { publishedCount })
  }
  return t('myEnrollments.classHoursProgress', { publishedCount, totalClassHours })
}

// ���� Data Loading ����

async function loadData() {
  loading.value = true
  try {
    const response = isTeacherPage.value
      ? await getTeacherCourses(currentPage.value, pageSize.value, teacherCourseRole.value)
      : await getCourses({
          page: currentPage.value,
          size: pageSize.value,
          keyword: searchKeyword.value || undefined,
          level: filterLevel.value,
          status: filterStatus.value,
          createdAtStart: createdAtStart.value ? createdAtStart.value + 'T00:00:00' : undefined,
          createdAtEnd: createdAtEnd.value ? createdAtEnd.value + 'T23:59:59' : undefined,
          updatedAtStart: updatedAtStart.value ? updatedAtStart.value + 'T00:00:00' : undefined,
          updatedAtEnd: updatedAtEnd.value ? updatedAtEnd.value + 'T23:59:59' : undefined,
        })
    courses.value = response.records
    totalPages.value = Math.ceil(response.total / pageSize.value)
  } catch (error) {
    console.error('Failed to load data:', error)
    notify.error(t('myEnrollments.alert.loadFailed'))
    courses.value = []
  } finally {
    loading.value = false
  }
}

function changePage(page: number) {
  currentPage.value = page
  loadData()
}

function resetAndLoad() {
  currentPage.value = 1
  loadData()
}

function switchTeacherRole(role: TeacherCourseRole) {
  if (teacherCourseRole.value === role) {
    return
  }

  router.replace({
    name: 'teacher-courses',
    query: { role },
  })
}

function clearDateFilters() {
  if (!hasDateFilters.value) {
    return
  }

  createdAtStart.value = ''
  createdAtEnd.value = ''
  updatedAtStart.value = ''
  updatedAtEnd.value = ''
  resetAndLoad()
}

function viewCourse(id: string) {
  router.push(`/courses/${id}`)
}

// ���� Create Course (teacher) ����

function openCreateModal() {
  showCreateModal.value = true
}

function closeCreateModal() {
  showCreateModal.value = false
}

async function submitCourse(request: CreateCourseRequest) {
  submitting.value = true
  try {
    await createCourse(request)
    closeCreateModal()
    notify.success(t('courses.alert.createSuccess'))
    await loadData()
  } catch (error) {
    console.error('Failed to create course:', error)
    notify.error(t('courses.alert.saveFailed'))
  } finally {
    submitting.value = false
  }
}

// ���� Edit Course (admin / teacher) ����

function editCourse(course: Course) {
  editingCourse.value = course
  showEditModal.value = true
}

function closeEditModal() {
  showEditModal.value = false
  editingCourse.value = null
}

async function submitEditCourse(request: UpdateCourseRequest) {
  if (!editingCourse.value) return
  submitting.value = true
  try {
    await updateCourse(editingCourse.value.id, request)
    closeEditModal()
    notify.success(t('courses.alert.updateSuccess'))
    await loadData()
  } catch (error) {
    console.error('Failed to update course:', error)
    notify.error(t('courses.alert.saveFailed'))
  } finally {
    submitting.value = false
  }
}

// ���� Delete Course (admin / teacher) ����

function confirmDeleteCourse(course: Course) {
  deleteTarget.value = course
  showDeleteModal.value = true
}

async function handleDeleteCourse() {
  if (!deleteTarget.value) return
  try {
    await deleteCourse(deleteTarget.value.id)
    showDeleteModal.value = false
    deleteTarget.value = null
    notify.success(t('courses.alert.deleteSuccess'))
    await loadData()
  } catch (error) {
    console.error('Failed to delete course:', error)
    notify.error(t('courses.alert.deleteFailed'))
  }
}

// ���� Invite Assistant ����

async function openInviteModal(course: Course) {
  inviteTarget.value = course
  showInviteModal.value = true
}

function onInviteSuccess() {
  showInviteModal.value = false
  notify.success(t('enrollmentManagement.alert.inviteSuccess'))
  void loadData()
}
function syncTeacherCourseRole() {
  if (!isTeacherPage.value) {
    return
  }

  teacherCourseRole.value = route.query.role === 'assistant' ? 'assistant' : 'primary'
}

watch(
  () => [route.name, route.query.role],
  () => {
    syncTeacherCourseRole()
    resetAndLoad()
  },
)

onMounted(() => {
  syncTeacherCourseRole()
  loadData()
})
</script>

<style scoped>
.enrollments-page {
  max-width: 100%;
}

.my-courses-page {
  --course-border: var(--color-outline-light);
  --course-surface: var(--color-surface-card);
  --course-surface-raised: var(--color-surface-container);

  display: flex;
  flex-direction: column;
  gap: 24px;
  color: var(--color-on-surface);
}

.my-courses-page .courses-command-bar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--course-border);
}

.my-courses-page .courses-title-block {
  max-width: 620px;
}

.my-courses-page .section-kicker {
  margin: 0 0 10px;
  font-family: var(--font-label);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.12em;
  color: var(--color-muted);
  text-transform: uppercase;
}

.my-courses-page .courses-title-block h1,
.my-courses-page .panel-heading h2,
.my-courses-page .empty-state h3 {
  margin: 0;
  font-family: var(--font-heading);
  font-weight: 500;
  color: var(--color-on-surface);
  text-wrap: balance;
}

.my-courses-page .courses-title-block h1 {
  font-size: clamp(36px, 5vw, 56px);
  line-height: 1.05;
}

.my-courses-page .courses-title-block p,
.my-courses-page .rhythm-note {
  margin: 12px 0 0;
  max-width: 62ch;
  font-size: 15px;
  line-height: 1.6;
  color: var(--color-muted);
}

.my-courses-page .courses-tools {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
  min-width: 360px;
}

.my-courses-page .search-field,
.my-courses-page .student-chip,
.my-courses-page .outline-button,
.my-courses-page .continue-button,
.my-courses-page .drop-button,
.my-courses-page .page-button {
  height: 44px;
  display: inline-flex;
  align-items: center;
  border-radius: var(--radius-sm);
  font-family: var(--font-body);
  transition: background 0.2s, border-color 0.2s, color 0.2s, transform 0.2s;
}

.my-courses-page .search-field {
  width: min(280px, 100%);
  gap: 10px;
  padding: 0 14px;
  background: var(--color-surface-canvas);
  border: 1px solid var(--course-border);
  color: var(--color-muted);
}

.my-courses-page .search-field:focus-within {
  border-color: var(--color-on-surface);
}

.my-courses-page .search-field input {
  min-width: 0;
  width: 100%;
  background: transparent;
  border: 0;
  outline: 0;
  color: var(--color-on-surface);
}

.my-courses-page .search-field input::placeholder {
  color: var(--color-muted);
}

.my-courses-page .outline-button,
.my-courses-page .page-button {
  gap: 8px;
  padding: 0 16px;
  background: transparent;
  border: 1px solid var(--course-border);
  color: var(--color-on-surface);
  cursor: pointer;
}

.my-courses-page .outline-button:hover,
.my-courses-page .page-button:hover:not(:disabled) {
  background: var(--course-surface-raised);
  border-color: var(--color-on-surface);
}

.my-courses-page .outline-button:active,
.my-courses-page .continue-button:active,
.my-courses-page .drop-button:active,
.my-courses-page .page-button:active {
  transform: translateY(1px);
}

.my-courses-page .student-chip {
  gap: 10px;
  padding: 0 12px 0 6px;
  background: var(--course-surface);
  border: 1px solid var(--course-border);
  color: var(--color-on-surface);
}

.my-courses-page .student-chip img,
.my-courses-page .student-chip__avatar {
  width: 30px;
  height: 30px;
}

.my-courses-page .student-chip img {
  object-fit: cover;
}

.my-courses-page .student-chip__avatar {
  display: grid;
  place-items: center;
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-family: var(--font-heading);
  font-size: 12px;
  font-weight: 700;
}

.my-courses-page .teacher-course-tabs {
  display: inline-flex;
  gap: 0;
  height: 44px;
  padding: 0;
  margin-bottom: 0;
  background: var(--course-surface);
  border: 1px solid var(--course-border);
  border-radius: 0;
}

.my-courses-page .teacher-course-tab {
  min-height: 42px;
  padding: 0 14px;
  background: transparent;
  border: 0;
  border-radius: 0;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}

.my-courses-page .teacher-course-tab + .teacher-course-tab {
  border-left: 1px solid var(--course-border);
}

.my-courses-page .teacher-course-tab:hover,
.my-courses-page .teacher-course-tab.active {
  background: var(--color-on-surface);
  color: var(--color-on-primary);
}

.my-courses-page .course-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border: 1px solid var(--course-border);
  background: var(--course-surface);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.my-courses-page .stat-cell {
  min-width: 0;
  padding: 22px 24px;
  border-right: 1px solid var(--course-border);
}

.my-courses-page .stat-cell:last-child {
  border-right: 0;
}

.my-courses-page .stat-cell span,
.my-courses-page .stat-cell small,
.my-courses-page .course-meta,
.my-courses-page .course-progress small,
.my-courses-page .task-item small,
.my-courses-page .deadline-item small {
  color: var(--color-muted);
}

.my-courses-page .stat-cell span,
.my-courses-page .stat-cell small,
.my-courses-page .course-progress small,
.my-courses-page .task-time,
.my-courses-page .deadline-item > span {
  display: block;
  font-size: 12px;
}

.my-courses-page .stat-cell strong {
  display: block;
  margin: 8px 0 4px;
  overflow: hidden;
  font-size: 32px;
  line-height: 1;
  font-variant-numeric: tabular-nums;
  color: var(--color-on-surface);
  text-overflow: ellipsis;
}

.my-courses-page .courses-workbench {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(300px, 0.95fr);
  gap: 24px;
  align-items: start;
}

.my-courses-page .courses-primary,
.my-courses-page .side-panel {
  background: var(--course-surface);
  border: 1px solid var(--course-border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.my-courses-page .courses-primary {
  min-width: 0;
  border-radius: 0;
}

.my-courses-page .panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 22px 24px;
  border-bottom: 1px solid var(--course-border);
}

.my-courses-page .panel-heading h2 {
  font-size: 26px;
  line-height: 1.2;
}

.my-courses-page .panel-heading > span {
  flex-shrink: 0;
  color: var(--color-muted);
}

.my-courses-page .panel-heading.compact {
  padding: 18px 20px;
}

.my-courses-page .panel-heading.compact h2 {
  font-family: var(--font-body);
  font-size: 16px;
  font-weight: 600;
}

.my-courses-page .course-list {
  display: flex;
  flex-direction: column;
}

.my-courses-page .course-row {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr) 86px auto;
  gap: 18px;
  align-items: center;
  min-height: 116px;
  padding: 20px 24px;
  border-bottom: 1px solid var(--course-border);
}

.my-courses-page .course-row:last-child {
  border-bottom: 0;
}

.my-courses-page .course-row:hover {
  background: var(--course-surface-raised);
}

.my-courses-page .course-mark {
  width: 64px;
  height: 64px;
  display: grid;
  place-items: center;
  background: var(--color-surface-canvas);
  border: 1px solid var(--course-border);
  color: var(--color-on-surface);
  overflow: hidden;
}

.my-courses-page .course-mark img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.my-courses-page .course-main {
  min-width: 0;
}

.my-courses-page .course-title-line {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.my-courses-page .course-title-line h3 {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  font-size: 18px;
  font-weight: 600;
  color: var(--color-on-surface);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.my-courses-page .status-tag {
  flex-shrink: 0;
  padding: 4px 8px;
  border: 1px solid var(--course-border);
  font-size: 11px;
  color: var(--color-muted);
}

.my-courses-page .status-tag.active,
.my-courses-page .status-tag.primary {
  border-color: var(--color-on-surface);
  color: var(--color-on-surface);
}

.my-courses-page .status-tag.completed {
  background: var(--color-on-surface);
  color: var(--color-on-primary);
}

.my-courses-page .status-tag.assistant {
  background: var(--color-surface-canvas);
  color: var(--color-muted);
}

.my-courses-page .status-tag.danger,
.my-courses-page .status-tag.dropped {
  color: var(--color-error);
}

.my-courses-page .course-meta {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 8px;
  font-size: 13px;
}

.my-courses-page .progress-track {
  height: 6px;
  margin-top: 14px;
  background: var(--color-surface-canvas);
  border: 1px solid var(--course-border);
}

.my-courses-page .progress-track span {
  display: block;
  height: 100%;
  background: var(--color-on-surface);
}

.my-courses-page .course-progress {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.my-courses-page .course-progress strong {
  display: block;
  font-size: 22px;
  line-height: 1;
}

.my-courses-page .course-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.my-courses-page .continue-button {
  justify-content: center;
  gap: 8px;
  padding: 0 18px;
  background: var(--color-on-surface);
  border: 1px solid var(--color-on-surface);
  color: var(--color-on-primary);
  cursor: pointer;
}

.my-courses-page .continue-button:hover {
  background: var(--color-primary-soft);
  border-color: var(--color-primary-soft);
}

.my-courses-page .drop-button {
  width: 36px;
  justify-content: center;
  background: transparent;
  border: 1px solid var(--course-border);
  color: var(--color-muted);
  cursor: pointer;
}

.my-courses-page .drop-button:hover {
  border-color: var(--color-on-surface);
  color: var(--color-on-surface);
}

.my-courses-page .drop-button.danger:hover {
  border-color: var(--color-error);
  color: var(--color-error);
}

.my-courses-page .empty-state {
  display: grid;
  place-items: center;
  padding: 96px 24px;
  text-align: center;
  color: var(--color-muted);
}

.my-courses-page .empty-state h3 {
  margin-top: 18px;
  font-size: 28px;
}

.my-courses-page .empty-state p {
  margin: 10px 0 24px;
  color: var(--color-muted);
}

.my-courses-page .pagination {
  display: flex;
  justify-content: center;
  gap: 8px;
  padding: 20px 24px;
  border-top: 1px solid var(--course-border);
}

.my-courses-page .page-button {
  min-width: 44px;
  justify-content: center;
}

.my-courses-page .page-button.active {
  background: var(--color-on-surface);
  border-color: var(--color-on-surface);
  color: var(--color-on-primary);
}

.my-courses-page .page-button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.my-courses-page .courses-sidebar {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.my-courses-page .task-list,
.my-courses-page .deadline-list {
  display: flex;
  flex-direction: column;
}

.my-courses-page .task-item,
.my-courses-page .deadline-item {
  display: flex;
  gap: 14px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--course-border);
}

.my-courses-page .task-item:last-child,
.my-courses-page .deadline-item:last-child {
  border-bottom: 0;
}

.my-courses-page .task-time {
  width: 44px;
  flex-shrink: 0;
  color: var(--color-on-surface);
  font-variant-numeric: tabular-nums;
}

.my-courses-page .task-item strong,
.my-courses-page .deadline-item strong {
  display: block;
  margin-bottom: 5px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-on-surface);
}

.my-courses-page .deadline-item {
  justify-content: space-between;
}

.my-courses-page .deadline-item > span {
  flex-shrink: 0;
  align-self: flex-start;
  padding: 5px 8px;
  border: 1px solid var(--course-border);
  color: var(--color-muted);
}

.my-courses-page .deadline-item > span.normal {
  border-color: var(--color-on-surface);
  color: var(--color-on-surface);
}

.my-courses-page .deadline-item > span.muted {
  background: var(--color-surface-canvas);
}

.my-courses-page .rhythm-bars {
  height: 126px;
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  align-items: end;
  gap: 8px;
  padding: 20px 20px 0;
}

.my-courses-page .rhythm-bars span {
  min-height: 18px;
  background: var(--color-on-surface);
}

.my-courses-page .rhythm-note {
  padding: 0 20px 20px;
  font-size: 13px;
}

.my-courses-page .skeleton-row {
  pointer-events: none;
}

.my-courses-page .skeleton-block,
.my-courses-page .skeleton-line {
  position: relative;
  overflow: hidden;
  background: var(--color-surface-canvas);
}

.my-courses-page .skeleton-copy {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.my-courses-page .skeleton-line {
  height: 14px;
  width: 66%;
}

.my-courses-page .skeleton-line.title {
  height: 18px;
  width: 44%;
}

.my-courses-page .skeleton-line.action {
  width: 110px;
  height: 44px;
}

.my-courses-page .skeleton-block::after,
.my-courses-page .skeleton-line::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent, rgba(245, 245, 245, 0.08), transparent);
  animation: shimmer 1.3s ease-in-out infinite;
}

/* Page Header */
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

.teacher-course-tabs {
  display: inline-flex;
  gap: 6px;
  padding: 6px;
  margin-bottom: 24px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-md);
}

.teacher-course-tab {
  min-height: 36px;
  padding: 0 16px;
  background: transparent;
  border: 0;
  border-radius: var(--radius-sm);
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
  transition:
    background 0.2s,
    color 0.2s;
}

.teacher-course-tab:hover,
.teacher-course-tab.active {
  background: var(--color-primary);
  color: var(--color-on-primary);
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
  border-radius: var(--radius-md);
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

.date-filter-bar {
  display: flex;
  align-items: center;
  gap: 24px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}

.date-filter-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.btn-date-filter {
  min-height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0 14px;
  border-radius: var(--radius-sm);
  font-family: var(--font-label);
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
  transition:
    background 0.2s,
    border-color 0.2s,
    color 0.2s,
    opacity 0.2s;
}

.btn-date-filter svg {
  flex-shrink: 0;
}

.btn-date-filter-primary {
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  color: var(--color-on-primary);
}

.btn-date-filter-primary:hover {
  background: var(--color-primary-soft);
  border-color: var(--color-primary-soft);
}

.btn-date-filter-secondary {
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
}

.btn-date-filter-secondary:hover:not(:disabled) {
  background: var(--color-surface-container);
  border-color: var(--color-outline-variant);
}

.btn-date-filter:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

/* Skeleton Loading */
.skeleton-title {
  height: 20px;
  width: 70%;
  border-radius: var(--radius-sm);
  background: var(--color-surface-canvas);
  margin-bottom: 12px;
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

.skeleton-tag.wide {
  width: 80px;
}

.skeleton-enrollment {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 20px 24px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-md);
}

.skeleton-enroll-cover {
  width: 80px;
  height: 60px;
  border-radius: var(--radius-sm);
  background: var(--color-surface-canvas);
  flex-shrink: 0;
}

.skeleton-enroll-body {
  flex: 1;
}

.skeleton-enroll-body .skeleton-title {
  width: 50%;
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

/* Enrollments List */
.enrollments-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 32px;
}

.enrollment-card {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 20px 24px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-md);
  transition: all 0.2s;
}

.enrollment-card:hover {
  border-color: var(--color-on-surface);
}

.enrollment-cover {
  width: 80px;
  height: 60px;
  display: grid;
  place-items: center;
  background: var(--color-surface-canvas);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  overflow: hidden;
  flex-shrink: 0;
}

.enrollment-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.enrollment-info {
  flex: 1;
  min-width: 0;
}

.enrollment-info h3 {
  margin: 0 0 8px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 16px;
  font-weight: 400;
  color: var(--color-on-surface);
}

.enrollment-meta {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.enrollment-status-badge {
  padding: 4px 10px;
  border-radius: var(--radius-sm);
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 11px;
  font-weight: 400;
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
  background: none;
  border: none;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  font-weight: 400;
  color: var(--color-on-surface);
  cursor: pointer;
  padding: 0;
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
  border-radius: var(--radius-sm);
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
  margin: 0 0 24px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
}

/* Pagination */
.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
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

.btn-page.active {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-on-primary);
}

.btn-page.active:hover:not(:disabled) {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-on-primary);
}

.btn-page:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-numbers {
  display: flex;
  gap: 4px;
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px 28px;
  background: var(--color-primary);
  color: var(--color-on-primary);
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-sm);
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  font-weight: 400;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary:hover:not(:disabled) {
  background: var(--color-primary-soft);
  border-color: var(--color-primary-soft);
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

@media (max-width: 1180px) {
  .my-courses-page .courses-command-bar {
    flex-direction: column;
  }

  .my-courses-page .courses-tools {
    width: 100%;
    min-width: 0;
    justify-content: flex-start;
  }

  .my-courses-page .courses-workbench {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 860px) {
  .my-courses-page .course-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .my-courses-page .stat-cell:nth-child(2) {
    border-right: 0;
  }

  .my-courses-page .stat-cell:nth-child(-n + 2) {
    border-bottom: 1px solid var(--course-border);
  }

  .my-courses-page .course-row {
    grid-template-columns: 56px minmax(0, 1fr);
  }

  .my-courses-page .course-mark {
    width: 56px;
    height: 56px;
  }

  .my-courses-page .course-progress,
  .my-courses-page .course-actions {
    grid-column: 2;
  }

  .my-courses-page .course-progress {
    text-align: left;
  }
}

@media (max-width: 640px) {
  .my-courses-page .courses-tools,
  .my-courses-page .course-actions,
  .my-courses-page .pagination {
    align-items: stretch;
    flex-direction: column;
  }

  .my-courses-page .search-field,
  .my-courses-page .teacher-course-tabs,
  .my-courses-page .student-chip,
  .my-courses-page .continue-button,
  .my-courses-page .outline-button,
  .my-courses-page .page-button {
    width: 100%;
    justify-content: center;
  }

  .my-courses-page .teacher-course-tab {
    flex: 1;
  }

  .my-courses-page .course-stats {
    grid-template-columns: 1fr;
  }

  .my-courses-page .stat-cell,
  .my-courses-page .stat-cell:nth-child(2) {
    border-right: 0;
    border-bottom: 1px solid var(--course-border);
  }

  .my-courses-page .stat-cell:last-child {
    border-bottom: 0;
  }

  .my-courses-page .panel-heading,
  .my-courses-page .course-row {
    padding-right: 16px;
    padding-left: 16px;
  }

  .my-courses-page .course-title-line {
    align-items: flex-start;
    flex-direction: column;
  }

  .my-courses-page .course-title-line h3 {
    white-space: normal;
  }

  .my-courses-page .task-item,
  .my-courses-page .deadline-item {
    padding-right: 16px;
    padding-left: 16px;
  }
}

/* Responsive */
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

  .date-filter-bar {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }

  .date-filter-actions {
    width: 100%;
    margin-left: 0;
  }

  .btn-date-filter {
    flex: 1;
  }

  .enrollment-card {
    flex-direction: column;
    align-items: flex-start;
  }

  .enrollment-cover {
    width: 100%;
    height: 120px;
  }

  .enrollment-actions {
    width: 100%;
    justify-content: space-between;
  }
}

/* Invite Modal */
.modal-md {
  max-width: 560px;
}

.invite-modal-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.invite-course-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-body);
  font-size: 14px;
  color: var(--color-on-surface);
}

.invite-course-label span {
  color: var(--color-muted);
}

.invite-search {
  min-height: 42px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  background: var(--color-surface-canvas);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-muted);
}

.invite-search input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  font-family: var(--font-body);
  font-size: 14px;
  color: var(--color-on-surface);
}

.invite-teacher-list {
  max-height: 320px;
  overflow-y: auto;
  display: grid;
  gap: 6px;
  max-height: 220px;
  overflow-y: auto;
}

.invite-teacher-option {
  min-height: 50px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  background: var(--color-surface-card);
  cursor: pointer;
  transition:
    background 0.2s,
    border-color 0.2s;
}

.invite-teacher-option:hover,
.invite-teacher-option.selected {
  background: var(--color-surface-container);
  border-color: var(--color-outline-variant);
}

.invite-teacher-option input {
  width: 18px;
  height: 18px;
  accent-color: var(--color-on-surface);
}

.invite-teacher-copy {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.invite-teacher-copy strong {
  font-family: var(--font-body);
  font-size: 14px;
  font-weight: 700;
  color: var(--color-on-surface);
}

.invite-teacher-copy small {
  overflow: hidden;
  font-family: var(--font-body);
  font-size: 12px;
  color: var(--color-muted);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.invite-empty {
  padding: 18px 12px;
  border: 1px dashed var(--color-outline-light);
  border-radius: var(--radius-sm);
  font-family: var(--font-body);
  font-size: 14px;
  color: var(--color-muted);
  text-align: center;
}

.invite-message-group {
  margin-bottom: 0;
}

.invite-message-group label {
  display: block;
  margin-bottom: 8px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  font-weight: 400;
  color: var(--color-on-surface);
}

.invite-message-group textarea {
  width: 100%;
  padding: 14px 16px;
  background: var(--color-surface-canvas);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-md);
  font-family: var(--font-body);
  font-size: 14px;
  color: var(--color-on-surface);
  resize: vertical;
  min-height: 80px;
  outline: none;
  transition: border-color 0.2s;
  box-sizing: border-box;
}

.invite-message-group textarea:focus {
  border-color: var(--color-on-surface);
  background: var(--color-surface-card);
}
</style>
