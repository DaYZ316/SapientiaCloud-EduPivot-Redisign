<template>
  <section class="tab-panel">
    <div class="panel-header">
      <div>
        <span>{{ t('courseDetail.filesTab') }}</span>
        <h2>{{ t('courseDetail.files') }}</h2>
      </div>
      <div v-if="canManageCourse" class="header-actions">
        <BaseSelect v-model="uploadVisibility" :options="visibilityOptions" class="visibility-select"/>
        <BaseFileUploader
          :usage="uploadVisibility === 'PUBLIC' ? 'COURSE_PUBLIC_FILE' : 'COURSE_PRIVATE_FILE'"
          scope-type="COURSE"
          :scope-id="courseId"
          :button-label="t('courseDetail.uploadFile')"
          @uploaded="handleFileUploaded"
          @error="handleUploadError"
        />
      </div>
    </div>
    <div v-if="files.length === 0" class="empty-tab">
      <FolderOpen :size="28" stroke-width="1.4"/>
      <h3>{{ t('courseDetail.noFilesTitle') }}</h3>
      <p>{{ t('chapter.noAttachments') }}</p>
    </div>
    <div v-else class="file-list">
      <div
        v-for="file in files"
        :key="file.id"
        class="file-item"
      >
        <a
          :href="canAccessCourseContent && file.url ? file.url : undefined"
          :aria-disabled="!canAccessCourseContent || !file.url"
          target="_blank"
          rel="noreferrer"
          class="file-link"
          @click="handleFileClick"
        >
          <FileDown :size="16" stroke-width="1.8"/>
          <span>{{ file.displayName }}</span>
          <span class="file-visibility">{{ formatFileVisibility(file.visibility) }}</span>
        </a>
        <button
          v-if="canManageCourse"
          class="btn-icon danger"
          type="button"
          :title="t('courseDetail.deleteFile')"
          @click="handleDelete(file)"
        >
          <Trash2 :size="14" stroke-width="1.8"/>
        </button>
      </div>
    </div>
  </section>
</template>

<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {FileDown, FolderOpen, Trash2} from 'lucide-vue-next'
import {bindCourseFile, deleteCourseFile} from '@/features/course/api/course'
import {notify} from '@/shared/composables/useGlobalNotification'
import type {CourseFile} from '@/features/course/types/course'
import type {FileAsset} from '@/features/storage/types/storage'
import BaseFileUploader from '@/shared/components/BaseFileUploader.vue'
import BaseSelect from '@/shared/components/BaseSelect.vue'

const props = defineProps<{
  courseId: string
  files: CourseFile[]
  canAccessCourseContent: boolean
  canManageCourse?: boolean
}>()

const emit = defineEmits<{
  refresh: []
}>()

const {t} = useI18n()

const uploadVisibility = ref<'PUBLIC' | 'PRIVATE'>('PUBLIC')

const visibilityOptions = computed(() => [
  {label: t('courseDetail.filePublicLabel'), value: 'PUBLIC'},
  {label: t('courseDetail.filePrivateLabel'), value: 'PRIVATE'},
])

function formatFileVisibility(visibility: CourseFile['visibility']) {
  return visibility === 'PUBLIC' ? t('courseDetail.filePublic') : t('courseDetail.filePrivate')
}

function handleFileClick(event: MouseEvent) {
  const target = event.currentTarget as HTMLAnchorElement
  if (target.getAttribute('aria-disabled') === 'true') {
    event.preventDefault()
  }
}

async function handleFileUploaded(asset: FileAsset) {
  try {
    await bindCourseFile(props.courseId, {
      fileId: asset.id,
      visibility: uploadVisibility.value,
      displayName: asset.fileName,
    })
    notify.success(t('courseDetail.fileUploaded'))
    emit('refresh')
  } catch {
    notify.error(t('courseDetail.saveChapterFailed'))
  }
}

function handleUploadError() {
  notify.error(t('forum.imageUploadFailed'))
}

async function handleDelete(file: CourseFile) {
  if (!confirm(t('courseDetail.confirmDeleteFile'))) return
  try {
    await deleteCourseFile(props.courseId, file.id)
    notify.success(t('courseDetail.fileRemoved'))
    emit('refresh')
  } catch {
    notify.error(t('courseDetail.saveChapterFailed'))
  }
}
</script>

<style scoped>
.panel-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-outline-light);
}

.panel-header span {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.panel-header h2 {
  margin: 6px 0 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 400;
  line-height: 1.3;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.visibility-select {
  min-width: 120px;
}

.file-list {
  display: grid;
  gap: 10px;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.file-link {
  min-height: 52px;
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
  padding: 0 16px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  text-decoration: none;
  transition: background 0.2s ease, border-color 0.2s ease;
}

.file-link:hover {
  background: var(--color-surface-container);
  border-color: var(--color-outline);
}

.file-link[aria-disabled='true'] {
  cursor: not-allowed;
  opacity: 0.55;
}

.file-link span:first-of-type {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  font-family: var(--font-body);
  font-size: 14px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-visibility {
  padding: 3px 8px;
  background: var(--color-surface-container-high);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
  font-weight: 800;
}

.btn-icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  padding: 0;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-muted);
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.btn-icon:hover {
  background: var(--color-surface-container-high);
  border-color: var(--color-outline);
  color: var(--color-on-surface);
}

.btn-icon.danger:hover {
  color: #ef4444;
}

.empty-tab {
  display: grid;
  min-height: 220px;
  place-items: center;
  align-content: center;
  gap: 10px;
  padding: 42px 24px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-muted);
  text-align: center;
}

.empty-tab h3 {
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 600;
}

.empty-tab p {
  max-width: 44ch;
  margin: 0;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.55;
}

@media (max-width: 760px) {
  .panel-header {
    flex-direction: column;
    align-items: stretch;
  }

  .header-actions {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
