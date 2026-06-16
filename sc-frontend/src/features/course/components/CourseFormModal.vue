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
                  <input v-model="form.title" :placeholder="t('courses.modal.titlePlaceholder')" class="input-field"
                         required type="text"/>
                </div>
                <div class="form-group">
                  <label>{{ t('courses.modal.descriptionLabel') }}</label>
                  <textarea v-model="form.description" :placeholder="t('courses.modal.descriptionPlaceholder')"
                            class="input-field" rows="4"></textarea>
                </div>
              </div>
              <div class="form-group basic-cover-field">
                <label>{{ t('courses.modal.coverUrlLabel') }}</label>
                <BaseImageUploader
                    v-model="form.coverFileId"
                    :allow-remove="false"
                    :button-label="t('courses.modal.uploadCover')"
                    :help-text="t('courses.modal.coverUrlPlaceholder')"
                    :preview-url="form.coverUrl"
                    :scope-id="course?.id"
                    :uploaded-button-label="t('courses.modal.changeCover')"
                    :uploaded-preview-label="t('courses.modal.changeCover')"
                    scope-type="COURSE"
                    size="cover"
                    uploaded-behavior="replace"
                    usage="COURSE_COVER"
                    @error="notify.error"
                    @removed="clearCover"
                    @uploaded="handleCoverUploaded"
                />
              </div>
              <div class="basic-field-row">
                <div class="form-group">
                  <label>{{ t('courses.modal.levelLabel') }}</label>
                  <BaseSelect
                      v-model="form.level"
                      :options="courseLevelOptions"
                      class="modal-select-control"
                      min-width="100%"
                  />
                </div>
                <div class="form-group">
                  <label>{{ t('courses.modal.visibilityLabel') }} *</label>
                  <BaseSelect
                      v-model="form.isPublic"
                      :disabled="mode === 'edit'"
                      :options="courseVisibilityOptions"
                      class="modal-select-control"
                      min-width="100%"
                  />
                </div>
                <div class="form-group">
                  <label>{{ t('courses.modal.maxStudentsLabel') }}</label>
                  <BaseNumberStepper v-model="form.maxStudents" :min="0"/>
                </div>
                <div class="form-group">
                  <label>{{ t('courseDetail.totalClassHours') }}</label>
                  <BaseNumberStepper v-model="form.totalClassHours" :min="0"/>
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
                    :options="courseStatusOptions"
                    class="modal-select-control"
                    min-width="100%"
                />
              </div>
              <div class="form-group">
                <label>{{ t('courses.modal.courseTypeLabel') }}</label>
                <BaseSelect
                    v-model="form.courseType"
                    :options="courseTypeOptions"
                    class="modal-select-control"
                    min-width="100%"
                />
              </div>
              <div class="form-group">
                <label>{{ t('courses.modal.semesterLabel') }}</label>
                <input v-model="form.semester" :placeholder="t('courses.modal.semesterPlaceholder')" class="input-field"
                       type="text"/>
              </div>
              <div class="form-group editor-span-2">
                <label>{{ t('courses.modal.locationLabel') }}</label>
                <input v-model="form.location" :placeholder="t('courses.modal.locationPlaceholder')" class="input-field"
                       type="text"/>
              </div>
            </div>
          </section>

          <div class="modal-footer editor-footer">
            <button class="btn-secondary" type="button" @click="emit('close')">{{ t('courses.modal.cancel') }}</button>
            <button :disabled="isSubmitting" class="btn-primary" type="submit">
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
import {X} from 'lucide-vue-next'

import BaseImageUploader from '@/shared/components/BaseImageUploader.vue'
import BaseNumberStepper from '@/shared/components/BaseNumberStepper.vue'
import BaseSelect from '@/shared/components/BaseSelect.vue'
import {notify} from '@/shared/composables/useGlobalNotification'

import type {Course, CreateCourseRequest, UpdateCourseRequest} from '@/features/course/types/course'
import type {FileAsset} from '@/features/storage/types/storage'

type SelectOption = { label: string; value: string | number | undefined }

interface Props {
  visible: boolean
  mode?: 'create' | 'edit'
  course?: Course | null
  canEditCourseStatus?: boolean
  submitting?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  mode: 'create',
  course: null,
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
  semester: '',
  location: '',
  courseType: 0,
  isPublic: 0,
  maxStudents: 0,
  totalClassHours: 0,
  status: 0,
})

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
  form.semester = ''
  form.location = ''
  form.courseType = 0
  form.isPublic = 0
  form.maxStudents = 0
  form.totalClassHours = 0
  form.status = 0
}

function syncFromCourse(course: Course) {
  form.title = course.title
  form.description = course.description || ''
  form.level = course.level
  form.coverUrl = course.coverUrl || ''
  form.coverFileId = course.coverFileId || ''
  form.semester = course.semester || ''
  form.location = course.location || ''
  form.courseType = course.courseType ?? 0
  form.isPublic = course.isPublic ?? 0
  form.maxStudents = course.maxStudents
  form.totalClassHours = course.totalClassHours ?? 0
  form.status = course.status
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
        semester: form.semester || undefined,
        location: form.location || undefined,
        courseType: form.courseType,
        isPublic: form.isPublic,
        maxStudents: form.maxStudents,
        totalClassHours: form.totalClassHours || undefined,
      })
      return
    }

    emit('updated', {
      title: form.title,
      description: form.description || undefined,
      level: form.level,
      coverUrl: form.coverFileId ? undefined : form.coverUrl || undefined,
      coverFileId: form.coverFileId || undefined,
      semester: form.semester || undefined,
      location: form.location || undefined,
      courseType: form.courseType,
      maxStudents: form.maxStudents,
      totalClassHours: form.totalClassHours || undefined,
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
  font-weight: 400;
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
  font-weight: 400;
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
  font-weight: 400;
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
  font-weight: 400;
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
