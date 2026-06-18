<template>
  <div v-if="visible" class="editor-overlay" @click.self="emit('close')">
    <div class="editor-dialog">
      <div class="editor-header">
        <h2>{{ isEditing ? t('chapter.editChapter') : t('chapter.addChapter') }}</h2>
        <button class="close-btn" @click="emit('close')">
          <X :size="18"/>
        </button>
      </div>

      <div class="editor-body">
        <div class="field">
          <label>{{ t('chapter.chapterName') }} *</label>
          <input v-model="form.chapterName" :placeholder="t('chapter.chapterName')" class="input" type="text"/>
        </div>

        <div class="field">
          <label>{{ t('chapter.description') }}</label>
          <textarea
              v-model="form.description"
              :placeholder="t('chapter.description')"
              class="textarea"
              rows="2"
          ></textarea>
        </div>

        <div class="field">
          <label>{{ t('chapter.content') }}</label>
          <BaseTextEditor
              v-model="form.content"
              :image-upload-options="imageUploadOptions"
              :placeholder="t('chapter.content')"
              :prepare-image-file="validateImageFile"
              :rows="8"
              aria-label="Chapter content"
              content-format="html"
              @image-upload-error="handleUploadError"
          />
        </div>

        <div class="field">
          <label>{{ t('chapter.attachments') }}</label>
          <BaseFileUploader
              :button-label="t('chapter.uploadAttachment')"
              :scope-id="courseId"
              scope-type="COURSE"
              usage="COURSE_FILE"
              @error="handleAttachmentUploadError"
              @uploaded="handleAttachmentUploaded"
          />
          <div v-if="formAttachments.length" class="attachment-editor-list">
            <div
                v-for="attachment in formAttachments"
                :key="attachment.fileId || attachment.url || attachment.displayName"
                class="attachment-editor-item"
            >
              <FileText :size="16" stroke-width="1.8"/>
              <span class="attachment-editor-name">{{ attachment.displayName || attachment.fileName }}</span>
              <span v-if="attachment.sizeBytes" class="attachment-editor-size">{{ formatFileSize(attachment.sizeBytes) }}</span>
              <button :title="t('chapter.removeAttachment')" type="button" @click="removeAttachment(attachment)">
                <Trash2 :size="14" stroke-width="1.8"/>
              </button>
            </div>
          </div>
          <p v-else class="attachment-empty-hint">{{ t('chapter.noAttachmentsHint') }}</p>
        </div>

        <div class="field-row">
          <div class="field">
            <label>{{ t('chapter.status') }}</label>
            <BaseSelect v-model="form.status" :options="statusOptions"/>
          </div>
          <div class="field">
            <label>{{ t('chapter.sortOrder') }}</label>
            <BaseNumberStepper v-model="form.sortOrder" :min="0"/>
          </div>
        </div>
      </div>

      <div class="editor-footer">
        <button class="btn-cancel" @click="emit('close')">{{ t('chapter.cancel') }}</button>
        <button :disabled="!form.chapterName.trim()" class="btn-save" @click="handleSave">
          {{ t('chapter.save') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {computed, reactive, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {FileText, Trash2, X} from 'lucide-vue-next'
import BaseNumberStepper from '@/shared/components/BaseNumberStepper.vue'
import BaseSelect from '@/shared/components/BaseSelect.vue'
import BaseTextEditor from '@/shared/components/BaseTextEditor.vue'
import BaseFileUploader from '@/shared/components/BaseFileUploader.vue'
import type {Chapter, ChapterAttachment, CreateChapterRequest, UpdateChapterRequest} from '@/features/course/types/chapter'
import type {FileAsset} from '@/features/storage/types/storage'
import {getStorageFile} from '@/features/storage/api/storage'
import {notify} from '@/shared/composables/useGlobalNotification'

const props = defineProps<{
  visible: boolean
  chapter?: Chapter | null
  courseId: string
  parentChapterId?: string | null
}>()

const emit = defineEmits<{
  close: []
  save: [data: CreateChapterRequest | UpdateChapterRequest]
}>()

const {t} = useI18n()

const isEditing = computed(() => Boolean(props.chapter))

const form = reactive({
  chapterName: '',
  description: '',
  content: '',
  status: 0,
  sortOrder: 0,
})
const formAttachments = ref<ChapterAttachment[]>([])
const attachmentsTouched = ref(false)

const CHAPTER_IMAGE_MAX_SIZE_MB = 5
const CHAPTER_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']

const statusOptions = computed(() => [
  {label: t('chapter.draft'), value: 0},
  {label: t('chapter.published'), value: 1},
])
const imageUploadOptions = computed(() => ({
  usage: 'FORUM_IMAGE' as const,
  scopeType: 'COURSE' as const,
  scopeId: props.courseId,
  accept: CHAPTER_IMAGE_TYPES.join(','),
  buttonLabel: t('chapter.uploadImage'),
  maxSizeMb: CHAPTER_IMAGE_MAX_SIZE_MB,
}))

watch(
    () => [props.visible, props.chapter?.id, props.parentChapterId],
    () => {
      if (!props.visible) return
      resetForm()
    },
)

function resetForm() {
  if (props.chapter) {
    form.chapterName = props.chapter.chapterName
    form.description = props.chapter.description || ''
    form.content = props.chapter.content || ''
    form.status = props.chapter.status
    form.sortOrder = props.chapter.sortOrder
    formAttachments.value = [...(props.chapter.attachments || [])]
  } else {
    form.chapterName = ''
    form.description = ''
    form.content = ''
    form.status = 0
    form.sortOrder = 0
    formAttachments.value = []
  }
  attachmentsTouched.value = false
}

function handleSave() {
  if (props.chapter) {
    const data: UpdateChapterRequest = {
      chapterName: form.chapterName,
      description: form.description || undefined,
      content: form.content || undefined,
      status: form.status,
      sortOrder: form.sortOrder,
    }
    if (attachmentsTouched.value) {
      data.attachments = attachmentRequests()
    }
    emit('save', data)
  } else {
    emit('save', {
      courseId: props.courseId,
      chapterName: form.chapterName,
      parentChapterId: props.parentChapterId || null,
      description: form.description || undefined,
      content: form.content || undefined,
      attachments: attachmentRequests(),
      status: form.status,
      sortOrder: form.sortOrder,
    } as CreateChapterRequest)
  }
}

function validateImageFile(file: File) {
  if (!CHAPTER_IMAGE_TYPES.includes(file.type)) {
    notify.error(t('chapter.imageTypeError'))
    return null
  }
  if (file.size > CHAPTER_IMAGE_MAX_SIZE_MB * 1024 * 1024) {
    notify.error(t('chapter.imageSizeError'))
    return null
  }
  return file
}

function handleUploadError() {
  notify.error(t('chapter.imageUploadFailed'))
}

async function handleAttachmentUploaded(asset: FileAsset) {
  let file = asset
  try {
    file = await getStorageFile(asset.id)
  } catch {
    file = asset
  }
  attachmentsTouched.value = true
  formAttachments.value = [
    ...formAttachments.value,
    {
      fileId: file.id,
      displayName: file.fileName,
      fileName: file.fileName,
      contentType: file.contentType,
      sizeBytes: file.sizeBytes,
      url: file.url,
    },
  ]
  notify.success(t('chapter.attachmentUploaded'))
}

function handleAttachmentUploadError() {
  notify.error(t('chapter.attachmentUploadFailed'))
}

function removeAttachment(attachment: ChapterAttachment) {
  attachmentsTouched.value = true
  formAttachments.value = formAttachments.value.filter(item => item !== attachment)
}

function attachmentRequests() {
  return formAttachments.value
      .filter(attachment => Boolean(attachment.fileId))
      .map(attachment => ({
        fileId: attachment.fileId as string,
        displayName: attachment.displayName || attachment.fileName,
      }))
}

function formatFileSize(sizeBytes: number) {
  if (sizeBytes < 1024) return `${sizeBytes} B`
  if (sizeBytes < 1024 * 1024) return `${Math.round(sizeBytes / 1024)} KB`
  return `${(sizeBytes / 1024 / 1024).toFixed(1)} MB`
}
</script>

<style scoped>
.editor-overlay {
  position: fixed;
  inset: 0;
  background: var(--color-overlay);
  display: grid;
  place-items: center;
  z-index: 1000;
  padding: 24px;
}

.editor-dialog {
  width: 100%;
  max-width: 600px;
  max-height: 90vh;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-lg);
  display: flex;
  flex-direction: column;
}

.editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24px 28px 16px;
}

.editor-header h2 {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 22px;
  font-weight: 400;
  color: var(--color-on-surface);
}

.close-btn {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: none;
  background: none;
  color: var(--color-muted);
  cursor: pointer;
  border-radius: 8px;
}

.close-btn:hover {
  background: var(--color-surface-container);
  color: var(--color-on-surface);
}

.editor-body {
  flex: 1;
  padding: 0 28px 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow-y: auto;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field label {
  font-family: var(--font-body);
  font-size: 12px;
  font-weight: 400;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--color-muted);
}

.input,
.textarea {
  width: 100%;
  padding: 10px 14px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
  outline: none;
  transition: border-color 0.15s;
}

.input:focus,
.textarea:focus {
  border-color: var(--color-on-surface);
}

.textarea {
  resize: vertical;
  min-height: 60px;
}

.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  align-items: end;
}

.field-row :deep(.base-select-trigger) {
  min-height: 42px;
  border-radius: var(--radius-sm);
  background: var(--color-surface-container);
  font-family: var(--font-body);
}

.field-row :deep(.base-number-stepper) {
  min-height: 42px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
}

.field-row :deep(.base-number-stepper:focus-within) {
  border-color: var(--color-on-surface);
  box-shadow: none;
}

.field-row :deep(.base-number-stepper-button) {
  height: 42px;
  color: var(--color-muted);
}

.field-row :deep(.base-number-stepper-button:hover:not(:disabled)) {
  color: var(--color-on-surface);
}

.field-row :deep(.base-number-stepper-input) {
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
}

.attachment-editor-list {
  display: grid;
  gap: 8px;
}

.attachment-editor-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto auto;
  gap: 10px;
  align-items: center;
  min-height: 40px;
  padding: 0 10px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
}

.attachment-editor-name {
  overflow: hidden;
  font-family: var(--font-body);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-editor-size,
.attachment-empty-hint {
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 12px;
}

.attachment-empty-hint {
  margin: 0;
}

.attachment-editor-item button {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  padding: 0;
  background: transparent;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-muted);
  cursor: pointer;
}

.attachment-editor-item button:hover {
  background: var(--color-surface-container-high);
  color: #ef4444;
}

.editor-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 28px 24px;
  border-top: 1px solid var(--color-outline-light);
}

.btn-cancel,
.btn-save {
  height: 42px;
  padding: 0 20px;
  border: none;
  border-radius: var(--radius-sm);
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 400;
  cursor: pointer;
  transition: background 0.15s;
}

.btn-cancel {
  background: var(--color-surface-container);
  color: var(--color-on-surface);
}

.btn-cancel:hover {
  background: var(--color-surface-container-high);
}

.btn-save {
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.btn-save:hover:not(:disabled) {
  background: var(--color-primary-soft);
}

.btn-save:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
