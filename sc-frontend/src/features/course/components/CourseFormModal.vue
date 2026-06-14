<template>
  <Teleport to="body">
    <div v-if="visible" class="modal-overlay">
      <div class="modal modal-lg course-editor-modal">
        <div class="modal-header">
          <h2>{{ mode === 'create' ? t('courses.modal.createTitle') : t('courses.modal.editTitle') }}</h2>
          <button class="btn-close" @click="emit('close')">
            <X :size="20"/>
          </button>
        </div>
        <form class="modal-body course-editor-form" @submit.prevent="handleSubmit">
          <section class="editor-section">
            <div class="editor-section-heading">
              <span>01</span>
              <h3>{{ t('courses.modal.sections.basic') }}</h3>
            </div>
            <div class="editor-grid basic-editor-grid">
              <div class="basic-fields">
                <div class="form-group">
                  <label>{{ t('courses.modal.titleLabel') }}</label>
                  <input v-model="form.title" class="input-field" type="text" :placeholder="t('courses.modal.titlePlaceholder')" required/>
                </div>
                <div class="form-group">
                  <label>{{ t('courses.modal.descriptionLabel') }}</label>
                  <textarea v-model="form.description" class="input-field" :placeholder="t('courses.modal.descriptionPlaceholder')" rows="4"></textarea>
                </div>
              </div>
              <div class="form-group basic-cover-field">
                <label>{{ t('courses.modal.coverUrlLabel') }}</label>
                <BaseImageUploader
                  v-model="form.coverFileId"
                  usage="COURSE_COVER"
                  scope-type="COURSE"
                  :scope-id="course?.id"
                  :preview-url="form.coverUrl"
                  :button-label="t('courses.modal.uploadCover')"
                  :uploaded-button-label="t('courses.modal.changeCover')"
                  :uploaded-preview-label="t('courses.modal.changeCover')"
                  uploaded-behavior="replace"
                  :allow-remove="false"
                  :help-text="t('courses.modal.coverUrlPlaceholder')"
                  size="cover"
                  @uploaded="handleCoverUploaded"
                  @error="notify.error"
                  @removed="clearCover"
                />
              </div>
              <div class="basic-field-row">
                <div class="form-group">
                  <label>{{ t('courses.modal.levelLabel') }}</label>
                  <BaseSelect
                    v-model="form.level"
                    class="modal-select-control"
                    :options="courseLevelOptions"
                    min-width="100%"
                  />
                </div>
                <div class="form-group">
                  <label>{{ t('courses.modal.visibilityLabel') }} *</label>
                  <BaseSelect
                    v-model="form.isPublic"
                    class="modal-select-control"
                    :options="courseVisibilityOptions"
                    min-width="100%"
                  />
                </div>
                <div class="form-group">
                  <label>{{ t('courses.modal.maxStudentsLabel') }}</label>
                  <BaseNumberStepper v-model="form.maxStudents" :min="0" />
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
              <div v-if="showStatusField" class="form-group">
                <label>{{ t('courses.modal.statusLabel') }}</label>
                <BaseSelect
                  v-model="form.status"
                  class="modal-select-control"
                  :options="courseStatusOptions"
                  min-width="100%"
                />
              </div>
              <div class="form-group">
                <label>{{ t('courses.modal.courseTypeLabel') }}</label>
                <BaseSelect
                  v-model="form.courseType"
                  class="modal-select-control"
                  :options="courseTypeOptions"
                  min-width="100%"
                />
              </div>
              <div class="form-group">
                <label>{{ t('courses.modal.semesterLabel') }}</label>
                <input v-model="form.semester" class="input-field" type="text" :placeholder="t('courses.modal.semesterPlaceholder')"/>
              </div>
              <div class="form-group editor-span-2">
                <label>{{ t('courses.modal.locationLabel') }}</label>
                <input v-model="form.location" class="input-field" type="text" :placeholder="t('courses.modal.locationPlaceholder')"/>
              </div>
            </div>
          </section>

          <section v-if="showTeacherSection" class="editor-section">
            <div class="editor-section-heading">
              <span>03</span>
              <h3>{{ t('courses.modal.sections.teacherTeam') }}</h3>
            </div>
            <div v-if="mode === 'edit' && isAdmin" class="form-group">
              <label>{{ t('courses.modal.primaryTeacherLabel') }}</label>
              <BaseSelect
                v-model="form.teacherId"
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
                  <p>{{ t('courses.modal.assistantsSelected', {count: form.assistantIds.length}) }}</p>
                </div>
                <div class="assistant-actions">
                  <button type="button" @click="selectAllAssistants">{{ t('courses.modal.selectAllAssistants') }}</button>
                  <button type="button" @click="clearAssistants">{{ t('courses.modal.clearAssistants') }}</button>
                </div>
              </div>
              <div class="assistant-search">
                <Search :size="16" stroke-width="1.8"/>
                <input v-model="assistantKeyword" type="text" :placeholder="t('courses.modal.searchAssistants')" @input="debouncedSearchTeachers(($event.target as HTMLInputElement).value)"/>
              </div>
              <div v-if="teacherLoading" class="assistant-empty">{{ t('courses.modal.loadingTeachers') }}</div>
              <div v-else-if="filteredAssistantCandidates.length === 0" class="assistant-empty">{{ t('courses.modal.noAssistants') }}</div>
              <div v-else class="assistant-list" @scroll="handleTeacherListScroll">
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
            <button type="button" class="btn-secondary" @click="emit('close')">{{ t('courses.modal.cancel') }}</button>
            <button type="submit" class="btn-primary" :disabled="isSubmitting">
              {{ submitLabel }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </Teleport>
</template>

<script lang="ts" setup>
import {computed, reactive, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {Search, X} from 'lucide-vue-next'

import {listTeachers} from '@/features/user/api/user'
import BaseImageUploader from '@/shared/components/BaseImageUploader.vue'
import BaseNumberStepper from '@/shared/components/BaseNumberStepper.vue'
import BaseSelect from '@/shared/components/BaseSelect.vue'
import {notify} from '@/shared/composables/useGlobalNotification'

import type {Course, CreateCourseRequest, UpdateCourseRequest} from '@/features/course/types/course'
import type {FileAsset} from '@/features/storage/types/storage'
import type {UserProfile} from '@/features/user/types/user'

type SelectOption = { label: string; value: string | number | undefined }

interface Props {
  visible: boolean
  mode?: 'create' | 'edit'
  course?: Course | null
  showTeacherSection?: boolean
  isAdmin?: boolean
  canEditCourseStatus?: boolean
  submitting?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  mode: 'create',
  course: null,
  showTeacherSection: false,
  isAdmin: false,
  canEditCourseStatus: false,
  submitting: false,
})

const emit = defineEmits<{
  close: []
  created: [request: CreateCourseRequest]
  updated: [request: UpdateCourseRequest]
}>()

const {t} = useI18n()
const localSubmitting = ref(false)

const form = reactive({
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

const teachers = ref<UserProfile[]>([])
const teacherLoading = ref(false)
const assistantKeyword = ref('')
let teacherSearchTimer: ReturnType<typeof setTimeout> | null = null
let teacherPage = 1
let teacherLoadingMore = false
let teacherHasMore = true
let teacherLoadMoreArmed = true
const scrollLoadThreshold = 50
const teacherListPageSize = 20

const isSubmitting = computed(() => props.submitting || localSubmitting.value)
const showStatusField = computed(() => props.mode === 'edit' && props.canEditCourseStatus)
const submitLabel = computed(() => {
  if (isSubmitting.value) return t('courses.modal.saving')
  return props.mode === 'create' ? t('courses.modal.save') : t('courses.modal.update')
})

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

const teacherOptions = computed<SelectOption[]>(() => {
  const options = teachers.value.map((teacher) => ({
    label: formatTeacherName(teacher),
    value: teacher.id,
  }))

  if (form.teacherId && !options.some((option) => option.value === form.teacherId)) {
    options.unshift({
      label: props.course?.teacherName || form.teacherId,
      value: form.teacherId,
    })
  }

  return options
})

const assistantCandidates = computed(() =>
  teachers.value.filter((teacher) => teacher.id !== form.teacherId),
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

function hasNextPage(response: {page: number; size: number; total: number}): boolean {
  return response.page * response.size < response.total
}

function handleTeacherListScroll(event: Event) {
  const element = event.target as HTMLElement | null
  if (!element) return

  const isNearBottom = element.scrollHeight - element.scrollTop - element.clientHeight < scrollLoadThreshold
  if (!isNearBottom) {
    teacherLoadMoreArmed = true
    return
  }

  if (!teacherLoadMoreArmed) return
  teacherLoadMoreArmed = false
  loadMoreTeachers()
}

async function loadMoreTeachers() {
  if (teacherLoadingMore || !teacherHasMore) return
  teacherLoadingMore = true
  try {
    const nextPage = teacherPage + 1
    const response = await listTeachers({page: nextPage, size: teacherListPageSize, keyword: assistantKeyword.value.trim() || undefined})
    if (!response.records?.length) {
      teacherHasMore = false
      return
    }
    teacherPage = nextPage
    teacherHasMore = hasNextPage(response)
    teachers.value = [...teachers.value, ...response.records]
  } finally {
    teacherLoadingMore = false
  }
}

async function searchTeachers(keyword: string) {
  teacherPage = 1
  teacherHasMore = true
  teacherLoadMoreArmed = true
  teacherLoading.value = true
  try {
    const response = await listTeachers({page: 1, size: teacherListPageSize, keyword: keyword || undefined})
    teachers.value = response.records
    teacherHasMore = hasNextPage(response)
  } catch {
    teachers.value = []
    teacherHasMore = false
  } finally {
    teacherLoading.value = false
  }
}

function debouncedSearchTeachers(keyword: string) {
  if (teacherSearchTimer) clearTimeout(teacherSearchTimer)
  teacherSearchTimer = setTimeout(() => searchTeachers(keyword), 300)
}

function loadTeacherOptions() {
  if (teachers.value.length === 0) debouncedSearchTeachers('')
}

function isAssistantSelected(teacherId: string): boolean {
  return form.assistantIds.includes(teacherId)
}

function toggleAssistant(teacherId: string) {
  if (teacherId === form.teacherId) return

  if (isAssistantSelected(teacherId)) {
    form.assistantIds = form.assistantIds.filter((id) => id !== teacherId)
    return
  }

  form.assistantIds = [...form.assistantIds, teacherId]
}

function selectAllAssistants() {
  const mergedIds = new Set(form.assistantIds)
  filteredAssistantCandidates.value.forEach((teacher) => mergedIds.add(teacher.id))
  form.assistantIds = Array.from(mergedIds)
}

function clearAssistants() {
  form.assistantIds = []
}

function handleMainTeacherChange() {
  form.assistantIds = form.assistantIds.filter((id) => id !== form.teacherId)
}

function handleCoverUploaded(asset: FileAsset) {
  form.coverFileId = asset.id
  form.coverUrl = asset.url || ''
}

function clearCover() {
  form.coverFileId = ''
  form.coverUrl = ''
}

function resetForm() {
  form.title = ''
  form.description = ''
  form.level = 1
  form.coverUrl = ''
  form.coverFileId = ''
  form.teacherId = ''
  form.assistantIds = []
  form.semester = ''
  form.location = ''
  form.courseType = 0
  form.isPublic = 0
  form.maxStudents = 0
  form.status = 0
  assistantKeyword.value = ''
}

function syncFromCourse(course: Course) {
  form.title = course.title
  form.description = course.description || ''
  form.level = course.level
  form.coverUrl = course.coverUrl || ''
  form.coverFileId = course.coverFileId || ''
  form.teacherId = course.teacherId || ''
  form.assistantIds = (course.teacherIds || []).filter((teacherId) => teacherId !== course.teacherId)
  form.semester = course.semester || ''
  form.location = course.location || ''
  form.courseType = course.courseType ?? 0
  form.isPublic = course.isPublic ?? 0
  form.maxStudents = course.maxStudents
  form.status = course.status
  assistantKeyword.value = ''
}

function handleSubmit() {
  if (!form.title || isSubmitting.value) return
  localSubmitting.value = true
  try {
    if (props.mode === 'create') {
      emit('created', {
        title: form.title,
        description: form.description || undefined,
        level: form.level,
        coverUrl: form.coverFileId ? undefined : form.coverUrl || undefined,
        coverFileId: form.coverFileId || undefined,
        assistantIds: form.assistantIds,
        semester: form.semester || undefined,
        location: form.location || undefined,
        courseType: form.courseType,
        isPublic: form.isPublic,
        maxStudents: form.maxStudents,
      })
      return
    }

    emit('updated', {
      title: form.title,
      description: form.description || undefined,
      level: form.level,
      coverUrl: form.coverFileId ? undefined : form.coverUrl || undefined,
      coverFileId: form.coverFileId || undefined,
      teacherId: props.isAdmin ? form.teacherId || undefined : undefined,
      assistantIds: props.isAdmin ? form.assistantIds : undefined,
      semester: form.semester || undefined,
      location: form.location || undefined,
      courseType: form.courseType,
      isPublic: form.isPublic,
      maxStudents: form.maxStudents,
      status: props.canEditCourseStatus ? form.status : undefined,
    })
  } finally {
    localSubmitting.value = false
  }
}

watch(() => props.visible, (isVisible) => {
  if (!isVisible) return

  if (props.mode === 'create') {
    resetForm()
  } else if (props.course) {
    syncFromCourse(props.course)
  }

  if (props.showTeacherSection) {
    loadTeacherOptions()
  }
})

watch(() => props.course, (course) => {
  if (props.visible && props.mode === 'edit' && course) {
    syncFromCourse(course)
  }
})
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: var(--color-overlay);
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

.modal-lg,
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

.form-group {
  min-width: 0;
  display: grid;
  gap: 8px;
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-on-surface);
}

.form-group .input-field {
  width: 100%;
  padding: 12px 0;
  box-sizing: border-box;
  background: transparent;
  border: none;
  border-bottom: 1px solid var(--login-field-border);
  border-radius: 0;
  color: var(--login-text);
  font-family: var(--font-body);
  font-size: 16px;
  font-weight: 400;
  line-height: 1.5;
  outline: none;
  transition: border-color 0.2s;
}

.form-group .input-field:focus {
  border-bottom-color: var(--login-text);
}

.form-group .input-field::placeholder {
  color: var(--login-muted);
}

.form-group textarea.input-field {
  min-height: 108px;
  padding: 14px 16px;
  resize: vertical;
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
  color: var(--color-on-surface);
  font-size: 16px;
  font-weight: 400;
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
  right: auto;
  left: 0;
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
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 800;
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
  background: transparent;
  border: 0;
  color: var(--color-on-surface);
  outline: 0;
}

.assistant-list {
  max-height: 260px;
  display: grid;
  gap: 8px;
  overflow-y: auto;
}

.assistant-option {
  min-height: 58px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 10px;
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
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
  font-weight: 700;
}

.assistant-copy small {
  overflow: hidden;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.assistant-state {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
  font-weight: 800;
}

.assistant-empty {
  padding: 18px 12px;
  border: 1px dashed var(--color-outline-light);
  border-radius: 10px;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
  text-align: center;
}

.modal-footer.editor-footer {
  position: sticky;
  bottom: 0;
  z-index: 2;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 28px 20px;
  background: var(--color-surface-card);
  border-top: 1px solid var(--color-outline-light);
}

.btn-primary,
.btn-secondary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px 28px;
  border-radius: var(--radius-sm);
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary {
  background: var(--color-primary);
  color: var(--color-on-primary);
  border: 1px solid var(--color-primary);
}

.btn-primary:hover:not(:disabled) {
  background: var(--color-primary-soft);
  border-color: var(--color-primary-soft);
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
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

@media (max-width: 768px) {
  .basic-editor-grid,
  .basic-field-row,
  .editor-grid,
  .editor-grid-3 {
    grid-template-columns: 1fr;
  }

  .editor-span-2 {
    grid-column: auto;
  }
}
</style>
