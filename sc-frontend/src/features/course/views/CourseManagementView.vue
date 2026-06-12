<template>
  <div class="enrollments-page">
    <!-- Page Header -->
    <div class="page-header">
      <h1>{{ pageTitle }}</h1>
      <button v-if="canCreateCourse" class="btn-primary" @click="openCreateModal">
        <Plus :size="16"/>
        {{ t('courses.createCourse') }}
      </button>
    </div>

    <div v-if="isTeacherPage" class="teacher-course-tabs">
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

    <!-- Filter Bar -->
    <div v-if="isAdmin" class="filter-bar">
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
      <BaseSelect
        v-model="filterStatus"
        :options="statusFilterOptions"
        min-width="148px"
        @change="resetAndLoad"
      />
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
          <X :size="15" stroke-width="2"/>
          {{ t('courses.clearFilters') }}
        </button>
        <button class="btn-date-filter btn-date-filter-primary" type="button" @click="resetAndLoad">
          <Search :size="15" stroke-width="2"/>
          {{ t('courses.search') }}
        </button>
      </div>
    </div>

    <CourseManagementTable
      v-if="courses.length > 0 || loading"
      :courses="courses"
      :loading="loading"
      :editable="canManageCourses"
      @view="viewCourse"
      @edit="editCourse"
      @delete="confirmDeleteCourse"
    />

    <div v-else class="empty-state">
      <BookOpen :size="48" stroke-width="1.2"/>
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

    <!-- Create Course Modal -->
    <Teleport to="body">
      <div v-if="showCreateModal" class="modal-overlay">
        <div class="modal modal-lg course-editor-modal">
          <div class="modal-header">
            <h2>{{ t('courses.modal.createTitle') }}</h2>
            <button class="btn-close" @click="closeCreateModal">
              <X :size="20"/>
            </button>
          </div>
          <form class="modal-body course-editor-form" @submit.prevent="submitCourse">
            <section v-if="isAdmin" class="editor-section">
              <div class="editor-section-heading">
                <span>01</span>
                <h3>{{ t('courses.modal.sections.basic') }}</h3>
              </div>
              <div class="editor-grid basic-editor-grid">
                <div class="basic-fields">
                  <div class="form-group">
                    <label>{{ t('courses.modal.titleLabel') }}</label>
                    <input v-model="courseForm.title" class="input-field" type="text" :placeholder="t('courses.modal.titlePlaceholder')" required/>
                  </div>
                  <div class="form-group">
                    <label>{{ t('courses.modal.descriptionLabel') }}</label>
                    <textarea v-model="courseForm.description" class="input-field" :placeholder="t('courses.modal.descriptionPlaceholder')" rows="4"></textarea>
                  </div>
                </div>
                <div class="form-group basic-cover-field">
                  <label>{{ t('courses.modal.coverUrlLabel') }}</label>
                  <BaseImageUploader
                    v-model="courseForm.coverFileId"
                    usage="COURSE_COVER"
                    scope-type="COURSE"
                    :preview-url="courseForm.coverUrl"
                    :button-label="t('courses.modal.uploadCover')"
                    :uploaded-button-label="t('courses.modal.changeCover')"
                    :uploaded-preview-label="t('courses.modal.changeCover')"
                    uploaded-behavior="replace"
                    :allow-remove="false"
                    :help-text="t('courses.modal.coverUrlPlaceholder')"
                    size="cover"
                    @uploaded="handleCreateCoverUploaded"
                    @error="notify.error"
                    @removed="clearCreateCover"
                  />
                </div>
                <div class="basic-field-row">
                  <div class="form-group">
                    <label>{{ t('courses.modal.levelLabel') }}</label>
                    <BaseSelect
                      v-model="courseForm.level"
                      class="modal-select-control"
                      :options="courseLevelOptions"
                      min-width="100%"
                    />
                  </div>
                  <div class="form-group">
                    <label>{{ t('courses.modal.visibilityLabel') }} *</label>
                    <BaseSelect
                      v-model="courseForm.isPublic"
                      class="modal-select-control"
                      :options="courseVisibilityOptions"
                      min-width="100%"
                    />
                  </div>
                  <div class="form-group">
                    <label>{{ t('courses.modal.maxStudentsLabel') }}</label>
                    <BaseNumberStepper v-model="courseForm.maxStudents" :min="0" />
                  </div>
                </div>
              </div>
            </section>

            <section class="editor-section">
              <div class="editor-section-heading">
                <span>02</span>
                <h3>{{ t('courses.modal.sections.publishing') }}</h3>
              </div>
              <div class="editor-grid editor-grid-3">
                <div class="form-group">
                  <label>{{ t('courses.modal.courseTypeLabel') }}</label>
                  <BaseSelect
                    v-model="courseForm.courseType"
                    class="modal-select-control"
                    :options="courseTypeOptions"
                    min-width="100%"
                  />
                </div>
                <div class="form-group">
                  <label>{{ t('courses.modal.semesterLabel') }}</label>
                  <input v-model="courseForm.semester" class="input-field" type="text" :placeholder="t('courses.modal.semesterPlaceholder')"/>
                </div>
                <div class="form-group editor-span-2">
                  <label>{{ t('courses.modal.locationLabel') }}</label>
                  <input v-model="courseForm.location" class="input-field" type="text" :placeholder="t('courses.modal.locationPlaceholder')"/>
                </div>
              </div>
            </section>

            <section class="editor-section">
              <div class="editor-section-heading">
                <span>03</span>
                <h3>{{ t('courses.modal.sections.teacherTeam') }}</h3>
              </div>
              <div class="assistant-panel">
                <div class="assistant-panel-header">
                  <div>
                    <label>{{ t('courses.modal.assistantsLabel') }}</label>
                    <p>{{ t('courses.modal.assistantsSelected', {count: selectedCreateAssistantCount}) }}</p>
                  </div>
                  <div class="assistant-actions">
                    <button type="button" @click="selectAllCreateAssistants">{{ t('courses.modal.selectAllAssistants') }}</button>
                    <button type="button" @click="clearCreateAssistants">{{ t('courses.modal.clearAssistants') }}</button>
                  </div>
                </div>
                <div class="assistant-search">
                  <Search :size="16" stroke-width="1.8"/>
                  <input v-model="assistantKeyword" type="text" :placeholder="t('courses.modal.searchAssistants')"/>
                </div>
                <div v-if="teacherLoading" class="assistant-empty">{{ t('courses.modal.loadingTeachers') }}</div>
                <div v-else-if="filteredCreateAssistantCandidates.length === 0" class="assistant-empty">{{ t('courses.modal.noAssistants') }}</div>
                <div v-else class="assistant-list">
                  <label
                    v-for="teacher in filteredCreateAssistantCandidates"
                    :key="teacher.id"
                    class="assistant-option"
                    :class="{ selected: isCreateAssistantSelected(teacher.id) }"
                  >
                    <input
                      type="checkbox"
                      :checked="isCreateAssistantSelected(teacher.id)"
                      @change="toggleCreateAssistant(teacher.id)"
                    />
                    <span class="assistant-copy">
                      <strong>{{ formatTeacherName(teacher) }}</strong>
                      <small>{{ formatTeacherMeta(teacher) || teacher.id }}</small>
                    </span>
                    <span v-if="isCreateAssistantSelected(teacher.id)" class="assistant-state">{{ t('courses.modal.selectedAssistant') }}</span>
                  </label>
                </div>
              </div>
            </section>

            <div class="modal-footer editor-footer">
              <button type="button" class="btn-secondary" @click="closeCreateModal">{{ t('courses.modal.cancel') }}</button>
              <button type="submit" class="btn-primary" :disabled="submitting">
                {{ submitting ? t('courses.modal.saving') : t('courses.modal.save') }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>

    <!-- Edit Course Modal -->
    <Teleport to="body">
      <div v-if="showEditModal" class="modal-overlay">
        <div class="modal modal-lg course-editor-modal">
          <div class="modal-header">
            <h2>{{ t('courses.modal.editTitle') }}</h2>
            <button class="btn-close" @click="closeEditModal">
              <X :size="20"/>
            </button>
          </div>
          <form class="modal-body course-editor-form" @submit.prevent="submitEdit">
            <section class="editor-section">
              <div class="editor-section-heading">
                <span>01</span>
                <h3>{{ t('courses.modal.sections.basic') }}</h3>
              </div>
              <div class="editor-grid basic-editor-grid">
                <div class="basic-fields">
                  <div class="form-group">
                    <label>{{ t('courses.modal.titleLabel') }}</label>
                    <input v-model="editForm.title" class="input-field" type="text" :placeholder="t('courses.modal.titlePlaceholder')" required/>
                  </div>
                  <div class="form-group">
                    <label>{{ t('courses.modal.descriptionLabel') }}</label>
                    <textarea v-model="editForm.description" class="input-field" :placeholder="t('courses.modal.descriptionPlaceholder')" rows="4"></textarea>
                  </div>
                </div>
                <div class="form-group basic-cover-field">
                  <label>{{ t('courses.modal.coverUrlLabel') }}</label>
                  <BaseImageUploader
                    v-model="editForm.coverFileId"
                    usage="COURSE_COVER"
                    scope-type="COURSE"
                    :scope-id="editingCourse?.id"
                    :preview-url="editForm.coverUrl"
                    :button-label="t('courses.modal.uploadCover')"
                    :uploaded-button-label="t('courses.modal.changeCover')"
                    :uploaded-preview-label="t('courses.modal.changeCover')"
                    uploaded-behavior="replace"
                    :allow-remove="false"
                    :help-text="t('courses.modal.coverUrlPlaceholder')"
                    size="cover"
                    @uploaded="handleEditCoverUploaded"
                    @error="notify.error"
                    @removed="clearEditCover"
                  />
                </div>
                <div class="basic-field-row">
                  <div class="form-group">
                    <label>{{ t('courses.modal.levelLabel') }}</label>
                    <BaseSelect
                      v-model="editForm.level"
                      class="modal-select-control"
                      :options="courseLevelOptions"
                      min-width="100%"
                    />
                  </div>
                  <div class="form-group">
                    <label>{{ t('courses.modal.visibilityLabel') }} *</label>
                    <BaseSelect
                      v-model="editForm.isPublic"
                      class="modal-select-control"
                      :options="courseVisibilityOptions"
                      min-width="100%"
                    />
                  </div>
                  <div class="form-group">
                    <label>{{ t('courses.modal.maxStudentsLabel') }}</label>
                    <BaseNumberStepper v-model="editForm.maxStudents" :min="0" />
                  </div>
                </div>
              </div>
            </section>

            <section class="editor-section">
              <div class="editor-section-heading">
                <span>02</span>
                <h3>{{ t('courses.modal.sections.publishing') }}</h3>
              </div>
              <div class="editor-grid editor-grid-3">
                <div v-if="isAdmin" class="form-group">
                  <label>{{ t('courses.modal.statusLabel') }}</label>
                  <BaseSelect
                    v-model="editForm.status"
                    class="modal-select-control"
                    :options="courseStatusOptions"
                    min-width="100%"
                  />
                </div>
                <div class="form-group">
                  <label>{{ t('courses.modal.courseTypeLabel') }}</label>
                  <BaseSelect
                    v-model="editForm.courseType"
                    class="modal-select-control"
                    :options="courseTypeOptions"
                    min-width="100%"
                  />
                </div>
                <div class="form-group">
                  <label>{{ t('courses.modal.semesterLabel') }}</label>
                  <input v-model="editForm.semester" class="input-field" type="text" :placeholder="t('courses.modal.semesterPlaceholder')"/>
                </div>
                <div class="form-group editor-span-2">
                  <label>{{ t('courses.modal.locationLabel') }}</label>
                  <input v-model="editForm.location" class="input-field" type="text" :placeholder="t('courses.modal.locationPlaceholder')"/>
                </div>
              </div>
            </section>

            <section v-if="isAdmin" class="editor-section">
              <div class="editor-section-heading">
                <span>03</span>
                <h3>{{ t('courses.modal.sections.teacherTeam') }}</h3>
              </div>
              <div class="form-group">
                <label>{{ t('courses.modal.primaryTeacherLabel') }}</label>
                <BaseSelect
                  v-model="editForm.teacherId"
                  class="modal-select-control"
                  :options="teacherOptions"
                  :placeholder="teacherLoading ? t('courses.modal.loadingTeachers') : t('courses.modal.primaryTeacherPlaceholder')"
                  min-width="100%"
                  @change="handleMainTeacherChange"
                />
              </div>
              <div class="assistant-panel">
                <div class="assistant-panel-header">
                  <div>
                    <label>{{ t('courses.modal.assistantsLabel') }}</label>
                    <p>{{ t('courses.modal.assistantsSelected', {count: selectedAssistantCount}) }}</p>
                  </div>
                  <div class="assistant-actions">
                    <button type="button" @click="selectAllAssistants">{{ t('courses.modal.selectAllAssistants') }}</button>
                    <button type="button" @click="clearAssistants">{{ t('courses.modal.clearAssistants') }}</button>
                  </div>
                </div>
                <div class="assistant-search">
                  <Search :size="16" stroke-width="1.8"/>
                  <input v-model="assistantKeyword" type="text" :placeholder="t('courses.modal.searchAssistants')"/>
                </div>
                <div v-if="teacherLoading" class="assistant-empty">{{ t('courses.modal.loadingTeachers') }}</div>
                <div v-else-if="filteredAssistantCandidates.length === 0" class="assistant-empty">{{ t('courses.modal.noAssistants') }}</div>
                <div v-else class="assistant-list">
                  <label
                    v-for="teacher in filteredAssistantCandidates"
                    :key="teacher.id"
                    class="assistant-option"
                    :class="{ selected: isAssistantSelected(teacher.id) }"
                  >
                    <input
                      type="checkbox"
                      :checked="isAssistantSelected(teacher.id)"
                      @change="toggleAssistant(teacher.id)"
                    />
                    <span class="assistant-copy">
                      <strong>{{ formatTeacherName(teacher) }}</strong>
                      <small>{{ formatTeacherMeta(teacher) || teacher.id }}</small>
                    </span>
                    <span v-if="isAssistantSelected(teacher.id)" class="assistant-state">{{ t('courses.modal.selectedAssistant') }}</span>
                  </label>
                </div>
              </div>
            </section>

            <div class="modal-footer editor-footer">
              <button type="button" class="btn-secondary" @click="closeEditModal">{{ t('courses.modal.cancel') }}</button>
              <button type="submit" class="btn-primary" :disabled="submitting">
                {{ submitting ? t('courses.modal.saving') : t('courses.modal.update') }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>

    <!-- Delete Course Modal -->
    <Teleport to="body">
      <div v-if="showDeleteModal" class="modal-overlay">
        <div class="modal modal-sm">
          <div class="modal-header">
            <h2>{{ t('courses.deleteModal.title') }}</h2>
            <button class="btn-close" @click="showDeleteModal = false">
              <X :size="20"/>
            </button>
          </div>
          <div class="modal-body">
            <p>{{ t('courses.deleteModal.confirmMessage', {title: deleteTarget?.title}) }}</p>
          </div>
          <div class="modal-footer">
            <button class="btn-secondary" @click="showDeleteModal = false">{{ t('courses.deleteModal.cancel') }}</button>
            <button class="btn-danger" @click="handleDeleteCourse">{{ t('courses.deleteModal.confirm') }}</button>
          </div>
        </div>
      </div>
    </Teleport>

  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted, reactive, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {BookOpen, Plus, Search, X} from 'lucide-vue-next'

import CourseManagementTable from '@/features/course/components/CourseManagementTable.vue'
import BaseDateRangeFilter from '@/shared/components/BaseDateRangeFilter.vue'
import BaseImageUploader from '@/shared/components/BaseImageUploader.vue'
import BaseNumberStepper from '@/shared/components/BaseNumberStepper.vue'
import BaseSelect from '@/shared/components/BaseSelect.vue'
import {notify} from '@/shared/composables/useGlobalNotification'

import {createCourse, deleteCourse, getCourses, getTeacherCourses, updateCourse} from '@/features/course/api/course'
import {pageUsers} from '@/features/user/api/user'
import {useAuthStore} from '@/features/auth/stores/auth'
import type {Course, CreateCourseRequest, TeacherCourseRole, UpdateCourseRequest} from '@/features/course/types/course'
import type {FileAsset} from '@/features/storage/types/storage'
import type {UserProfile} from '@/features/user/types/user'

const {t} = useI18n()
const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const isAdmin = computed(() => authStore.user?.role === 0)
const isTeacher = computed(() => authStore.user?.role === 2)
const isTeacherPage = computed(() => route.name === 'teacher-courses')
const isPrimaryTeacherCourses = computed(() => teacherCourseRole.value === 'primary')
const canManageCourses = computed(() => isAdmin.value || (isTeacher.value && isTeacherPage.value && isPrimaryTeacherCourses.value))
const canCreateCourse = computed(() => canManageCourses.value)
const pageTitle = computed(() => isAdmin.value ? t('common.navigation.courseManagement') : t('myEnrollments.title'))

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

const levelFilterOptions = computed<SelectOption[]>(() => [
  {label: t('courses.allLevels'), value: undefined},
  {label: t('courses.level.beginner'), value: 1},
  {label: t('courses.level.intermediate'), value: 2},
  {label: t('courses.level.advanced'), value: 3},
])

const statusFilterOptions = computed<SelectOption[]>(() => [
  {label: t('courses.allStatuses'), value: undefined},
  {label: t('courses.status.draft'), value: 0},
  {label: t('courses.status.published'), value: 1},
  {label: t('courses.status.archived'), value: 2},
])

const courseLevelOptions = computed<SelectOption[]>(() => [
  {label: t('courses.level.beginner'), value: 1},
  {label: t('courses.level.intermediate'), value: 2},
  {label: t('courses.level.advanced'), value: 3},
])

const courseStatusOptions = computed<SelectOption[]>(() => [
  {label: t('courses.status.draft'), value: 0},
  {label: t('courses.status.published'), value: 1},
  {label: t('courses.status.archived'), value: 2},
])

const courseTypeOptions = computed<SelectOption[]>(() => [
  {label: t('courses.courseType.required'), value: 0},
  {label: t('courses.courseType.elective'), value: 1},
])

const courseVisibilityOptions = computed<SelectOption[]>(() => [
  {label: t('courses.visibility.private'), value: 0},
  {label: t('courses.visibility.public'), value: 1},
])

const teachers = ref<UserProfile[]>([])
const teacherLoading = ref(false)
const assistantKeyword = ref('')

const hasDateFilters = computed(() =>
  Boolean(createdAtStart.value || createdAtEnd.value || updatedAtStart.value || updatedAtEnd.value),
)

// Admin / Teacher: course list
const courses = ref<Course[]>([])
const teacherCourseRole = ref<TeacherCourseRole>('primary')

// Create course (teacher only)
const showCreateModal = ref(false)
const courseForm = reactive({
  title: '',
  description: '',
  level: 1,
  coverUrl: '',
  coverFileId: '',
  assistantIds: [] as string[],
  semester: '',
  location: '',
  courseType: 0,
  isPublic: 0,
  maxStudents: 0,
})

// Edit course (admin / teacher)
const showEditModal = ref(false)
const editingCourse = ref<Course | null>(null)
const editForm = reactive({
  title: '',
  description: '',
  level: 1,
  coverUrl: '',
  coverFileId: '',
  teacherId: '',
  assistantIds: [] as string[],
  semester: '',
  location: '',
  courseType: 0,
  isPublic: 0,
  maxStudents: 0,
  status: 0,
})

const teacherOptions = computed<SelectOption[]>(() => {
  const options = teachers.value.map((teacher) => ({
    label: formatTeacherName(teacher),
    value: teacher.id,
  }))

  if (editForm.teacherId && !options.some((option) => option.value === editForm.teacherId)) {
    options.unshift({
      label: editingCourse.value?.teacherName || editForm.teacherId,
      value: editForm.teacherId,
    })
  }

  return options
})

const assistantCandidates = computed(() =>
  teachers.value.filter((teacher) => teacher.id !== editForm.teacherId),
)

const filteredAssistantCandidates = computed(() => {
  const keyword = assistantKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return assistantCandidates.value
  }

  return assistantCandidates.value.filter((teacher) =>
    [
      formatTeacherName(teacher),
      formatTeacherMeta(teacher),
      teacher.email,
      teacher.teacherInfo?.employeeNo,
    ].filter(Boolean).join(' ').toLowerCase().includes(keyword),
  )
})

const selectedAssistantCount = computed(() => editForm.assistantIds.length)
const selectedCreateAssistantCount = computed(() => courseForm.assistantIds.length)

const filteredCreateAssistantCandidates = computed(() => {
  const keyword = assistantKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return teachers.value
  }

  return teachers.value.filter((teacher) =>
    [
      formatTeacherName(teacher),
      formatTeacherMeta(teacher),
      teacher.email,
      teacher.teacherInfo?.employeeNo,
    ].filter(Boolean).join(' ').toLowerCase().includes(keyword),
  )
})

// Delete course (admin / teacher)
const showDeleteModal = ref(false)
const deleteTarget = ref<Course | null>(null)

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

// ── Data Loading ──

function formatTeacherName(teacher: UserProfile): string {
  return teacher.displayName || teacher.email || teacher.id
}

function formatTeacherMeta(teacher: UserProfile): string {
  return [
    teacher.teacherInfo?.department,
    teacher.teacherInfo?.title,
    teacher.email,
  ].filter(Boolean).join(' / ')
}

function isAssistantSelected(teacherId: string): boolean {
  return editForm.assistantIds.includes(teacherId)
}

function toggleAssistant(teacherId: string) {
  if (teacherId === editForm.teacherId) {
    return
  }

  if (isAssistantSelected(teacherId)) {
    editForm.assistantIds = editForm.assistantIds.filter((id) => id !== teacherId)
    return
  }

  editForm.assistantIds = [...editForm.assistantIds, teacherId]
}

function selectAllAssistants() {
  const mergedIds = new Set(editForm.assistantIds)
  filteredAssistantCandidates.value.forEach((teacher) => mergedIds.add(teacher.id))
  editForm.assistantIds = Array.from(mergedIds)
}

function clearAssistants() {
  editForm.assistantIds = []
}

function handleMainTeacherChange() {
  editForm.assistantIds = editForm.assistantIds.filter((id) => id !== editForm.teacherId)
}

function isCreateAssistantSelected(teacherId: string): boolean {
  return courseForm.assistantIds.includes(teacherId)
}

function toggleCreateAssistant(teacherId: string) {
  if (isCreateAssistantSelected(teacherId)) {
    courseForm.assistantIds = courseForm.assistantIds.filter((id) => id !== teacherId)
    return
  }

  courseForm.assistantIds = [...courseForm.assistantIds, teacherId]
}

function selectAllCreateAssistants() {
  const mergedIds = new Set(courseForm.assistantIds)
  filteredCreateAssistantCandidates.value.forEach((teacher) => mergedIds.add(teacher.id))
  courseForm.assistantIds = Array.from(mergedIds)
}

function clearCreateAssistants() {
  courseForm.assistantIds = []
}

async function loadTeacherOptions() {
  if (!isAdmin.value || teachers.value.length > 0 || teacherLoading.value) {
    return
  }

  teacherLoading.value = true
  try {
    const response = await pageUsers({page: 1, size: 100, role: 2})
    teachers.value = response.records
  } catch (error) {
    console.error('Failed to load teachers:', error)
    teachers.value = []
  } finally {
    teacherLoading.value = false
  }
}

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
    query: {role},
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

// ── Create Course (teacher) ──

function openCreateModal() {
  assistantKeyword.value = ''
  showCreateModal.value = true
  loadTeacherOptions()
}

function closeCreateModal() {
  showCreateModal.value = false
  courseForm.title = ''
  courseForm.description = ''
  courseForm.level = 1
  courseForm.coverUrl = ''
  courseForm.coverFileId = ''
  courseForm.assistantIds = []
  courseForm.semester = ''
  courseForm.location = ''
  courseForm.courseType = 0
  courseForm.isPublic = 0
  courseForm.maxStudents = 0
  assistantKeyword.value = ''
}

function handleCreateCoverUploaded(asset: FileAsset) {
  courseForm.coverFileId = asset.id
  courseForm.coverUrl = asset.url || ''
}

function clearCreateCover() {
  courseForm.coverFileId = ''
  courseForm.coverUrl = ''
}

async function submitCourse() {
  if (!courseForm.title) return
  submitting.value = true
  try {
    const request: CreateCourseRequest = {
      title: courseForm.title,
      description: courseForm.description || undefined,
      level: courseForm.level,
      coverUrl: courseForm.coverFileId ? undefined : courseForm.coverUrl || undefined,
      coverFileId: courseForm.coverFileId || undefined,
      assistantIds: courseForm.assistantIds,
      semester: courseForm.semester || undefined,
      location: courseForm.location || undefined,
      courseType: courseForm.courseType,
      isPublic: courseForm.isPublic,
      maxStudents: courseForm.maxStudents,
    }
    await createCourse(request)
    closeCreateModal()
    await loadData()
  } catch (error) {
    console.error('Failed to create course:', error)
    notify.error(t('courses.alert.saveFailed'))
  } finally {
    submitting.value = false
  }
}

// ── Edit Course (admin / teacher) ──

function editCourse(course: Course) {
  editingCourse.value = course
  editForm.title = course.title
  editForm.description = course.description || ''
  editForm.level = course.level
  editForm.coverUrl = course.coverUrl || ''
  editForm.coverFileId = course.coverFileId || ''
  editForm.teacherId = course.teacherId || ''
  editForm.assistantIds = (course.teacherIds || []).filter((teacherId) => teacherId !== course.teacherId)
  editForm.semester = course.semester || ''
  editForm.location = course.location || ''
  editForm.courseType = course.courseType ?? 0
  editForm.isPublic = course.isPublic ?? 0
  editForm.maxStudents = course.maxStudents
  editForm.status = course.status
  assistantKeyword.value = ''
  showEditModal.value = true
  loadTeacherOptions()
}

function closeEditModal() {
  showEditModal.value = false
  editingCourse.value = null
  assistantKeyword.value = ''
}

function handleEditCoverUploaded(asset: FileAsset) {
  editForm.coverFileId = asset.id
  editForm.coverUrl = asset.url || ''
}

function clearEditCover() {
  editForm.coverFileId = ''
  editForm.coverUrl = ''
}

async function submitEdit() {
  if (!editForm.title || !editingCourse.value) return
  submitting.value = true
  try {
    const request: UpdateCourseRequest = {
      title: editForm.title,
      description: editForm.description || undefined,
      level: editForm.level,
      coverUrl: editForm.coverFileId ? undefined : editForm.coverUrl || undefined,
      coverFileId: editForm.coverFileId || undefined,
      teacherId: isAdmin.value ? editForm.teacherId || undefined : undefined,
      assistantIds: isAdmin.value ? editForm.assistantIds : undefined,
      semester: editForm.semester || undefined,
      location: editForm.location || undefined,
      courseType: editForm.courseType,
      isPublic: editForm.isPublic,
      maxStudents: editForm.maxStudents,
      status: isAdmin.value ? editForm.status : undefined,
    }
    await updateCourse(editingCourse.value.id, request)
    closeEditModal()
    await loadData()
  } catch (error) {
    console.error('Failed to update course:', error)
    notify.error(t('courses.alert.saveFailed'))
  } finally {
    submitting.value = false
  }
}

// ── Delete Course (admin / teacher) ──

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
    await loadData()
  } catch (error) {
    console.error('Failed to delete course:', error)
    notify.error(t('courses.alert.deleteFailed'))
  }
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

/* Page Header */
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

.teacher-course-tabs {
  display: inline-flex;
  gap: 6px;
  padding: 6px;
  margin-bottom: 24px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 12px;
}

.teacher-course-tab {
  min-height: 36px;
  padding: 0 16px;
  background: transparent;
  border: 0;
  border-radius: 8px;
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
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
  border-radius: 8px;
  font-family: var(--font-label);
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s, color 0.2s, opacity 0.2s;
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
  border-radius: 6px;
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
  border-radius: 16px;
}

.skeleton-enroll-cover {
  width: 80px;
  height: 60px;
  border-radius: 8px;
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
  0% { transform: translateX(-100%); }
  100% { transform: translateX(100%); }
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
  border-radius: 16px;
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
  border-radius: 8px;
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
  font-weight: 600;
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
  background: none;
  border: none;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  font-weight: 600;
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

/* Modal */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: var(--color-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  z-index: 1000;
}

.modal {
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 24px;
  width: 100%;
  max-width: 400px;
  box-shadow: 0 24px 80px rgba(0, 0, 0, 0.16);
  max-height: calc(100dvh - 48px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.modal-lg {
  max-width: 560px;
}

.course-editor-modal {
  max-width: 920px;
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

.course-editor-form {
  padding: 0;
}

.editor-section {
  padding: 28px;
  border-top: 1px solid var(--color-outline-light);
}

.editor-section:first-child {
  border-top: 0;
}

.editor-section-heading {
  display: flex;
  align-items: baseline;
  gap: 18px;
  margin-bottom: 22px;
}

.editor-section-heading span {
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 800;
  color: var(--color-outline);
}

.editor-section-heading h3 {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 600;
  color: var(--color-on-surface);
}

.editor-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 24px;
  row-gap: 4px;
}

.editor-grid-3 {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.editor-span-2 {
  grid-column: span 2;
}

.editor-grid:not(.basic-editor-grid) > .form-group:has(.base-image-uploader) {
  grid-column: span 2;
}

.basic-editor-grid {
  grid-template-columns: minmax(0, 1fr) minmax(280px, 340px);
  column-gap: 28px;
  row-gap: 24px;
  align-items: start;
}

.basic-fields {
  display: grid;
  gap: 18px;
  min-width: 0;
}

.basic-field-row {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: minmax(150px, 0.85fr) minmax(190px, 1fr) minmax(190px, 1fr);
  column-gap: 18px;
  row-gap: 18px;
  align-items: end;
}

.basic-fields > .form-group,
.basic-field-row > .form-group,
.basic-cover-field {
  margin-bottom: 0;
}

.basic-editor-grid > .basic-cover-field {
  grid-column: auto;
}

.basic-cover-field {
  display: grid;
  align-content: start;
  gap: 10px;
}

.basic-cover-field > label {
  margin-bottom: 0;
}

.basic-cover-field :deep(.base-image-uploader),
.basic-cover-field :deep(.base-image-uploader-actions),
.basic-cover-field :deep(.base-image-uploader-preview) {
  width: 100%;
}

.basic-cover-field :deep(.base-image-uploader-field) {
  gap: 12px;
}

.basic-cover-field :deep(.base-image-uploader-actions) {
  display: none;
}

.basic-cover-field :deep(.base-image-uploader-preview) {
  max-width: none;
}

.basic-cover-field :deep(.base-image-uploader-action.primary) {
  min-width: 116px;
}

.basic-cover-field :deep(.size-cover .base-image-uploader-preview) {
  width: 100%;
  max-width: none;
}

.assistant-panel {
  margin-top: 18px;
  padding: 18px;
  background: var(--color-surface-canvas);
  border: 1px solid var(--color-outline-light);
  border-radius: 14px;
}

.assistant-panel-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.assistant-panel-header label {
  display: block;
  margin-bottom: 4px;
  font-family: var(--font-label);
  font-size: 13px;
  font-weight: 700;
  color: var(--color-on-surface);
}

.assistant-panel-header p {
  margin: 0;
  font-family: var(--font-body);
  font-size: 13px;
  color: var(--color-muted);
}

.assistant-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.assistant-actions button {
  padding: 0;
  background: transparent;
  border: 0;
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 800;
  color: var(--color-muted);
  cursor: pointer;
  transition: color 0.2s;
}

.assistant-actions button:hover {
  color: var(--color-on-surface);
}

.assistant-search {
  min-height: 42px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  margin-bottom: 12px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 10px;
  color: var(--color-muted);
}

.assistant-search input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--color-on-surface);
}

.assistant-list {
  display: grid;
  gap: 8px;
  max-height: 260px;
  overflow-y: auto;
}

.assistant-option {
  min-height: 58px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--color-outline-light);
  border-radius: 10px;
  background: var(--color-surface-card);
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s;
}

.assistant-option:hover,
.assistant-option.selected {
  background: var(--color-surface-container);
  border-color: var(--color-outline-variant);
}

.assistant-option input {
  width: 18px;
  height: 18px;
  accent-color: var(--color-on-surface);
}

.assistant-copy {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.assistant-copy strong {
  font-family: var(--font-body);
  font-size: 14px;
  font-weight: 700;
  color: var(--color-on-surface);
}

.assistant-copy small {
  overflow: hidden;
  font-family: var(--font-body);
  font-size: 12px;
  color: var(--color-muted);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.assistant-state {
  font-family: var(--font-label);
  font-size: 11px;
  font-weight: 800;
  color: var(--color-muted);
}

.assistant-empty {
  padding: 18px 12px;
  border: 1px dashed var(--color-outline-light);
  border-radius: 10px;
  font-family: var(--font-body);
  font-size: 14px;
  color: var(--color-muted);
  text-align: center;
}

.modal-footer.editor-footer {
  position: sticky;
  bottom: 0;
  z-index: 2;
  padding: 16px 28px 20px;
  background: var(--color-surface-card);
  border-top: 1px solid var(--color-outline-light);
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
  font-weight: 600;
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

/* Form */
.form-group {
  margin-bottom: 20px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-on-surface);
}

.form-group .input-field {
  width: 100%;
  padding: 12px 0;
  background: transparent;
  border: none;
  border-bottom: 1px solid var(--login-field-border);
  border-radius: 0;
  font-family: var(--font-body);
  font-size: 16px;
  font-weight: 400;
  line-height: 1.5;
  color: var(--login-text);
  outline: none;
  transition: border-color 0.2s;
  box-sizing: border-box;
}

.form-group .input-field:focus {
  border-bottom-color: var(--login-text);
}

.form-group .input-field::placeholder {
  color: var(--login-muted);
}

.form-group textarea.input-field {
  resize: vertical;
  min-height: 108px;
  padding: 14px 16px;
  background: var(--color-surface-canvas);
  border: 1px solid var(--color-outline-light);
  border-radius: 14px;
  line-height: 1.6;
  box-shadow: none;
}

.form-group textarea.input-field:hover:not(:disabled) {
  border-color: var(--color-outline-variant);
}

.form-group textarea.input-field:focus {
  background: var(--color-surface-card);
  border-color: var(--color-on-surface);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-on-surface) 8%, transparent);
}

.form-group textarea.input-field:focus:hover {
  border-color: var(--color-on-surface);
}

.form-group :deep(.modal-select-control) {
  width: 100%;
  min-width: 0;
}

.form-group :deep(.modal-select-control .base-select-trigger) {
  min-height: 49px;
  padding: 0;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--login-field-border);
  border-radius: 0;
  font-size: 16px;
  font-weight: 400;
  color: var(--color-on-surface);
  box-shadow: none;
}

.form-group :deep(.modal-select-control .base-select-trigger:hover),
.form-group :deep(.modal-select-control.open .base-select-trigger) {
  background: transparent;
  border-bottom-color: var(--color-on-surface);
  box-shadow: 0 1px 0 var(--color-on-surface);
}

.form-group :deep(.modal-select-control .base-select-menu) {
  top: calc(100% + 8px);
  left: 0;
  right: auto;
  min-width: 100%;
  margin-top: 0;
  padding: 6px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 14px;
  box-shadow: 0 18px 40px rgba(0, 0, 0, 0.22);
}

.form-group :deep(.modal-select-control .base-select-option) {
  min-height: 38px;
  font-size: 15px;
}

.form-group :deep(.modal-select-control .base-select-option:hover),
.form-group :deep(.modal-select-control .base-select-option.selected) {
  background: var(--color-surface-container);
}

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

.btn-danger:hover:not(:disabled) {
  background: var(--color-on-surface);
  color: var(--color-on-primary);
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

  .form-row {
    grid-template-columns: 1fr;
  }

  .basic-editor-grid,
  .basic-field-row {
    grid-template-columns: 1fr;
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
</style>
