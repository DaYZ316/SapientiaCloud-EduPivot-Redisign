<template>
  <div class="base-file-uploader">
    <input
      ref="inputRef"
      class="base-file-uploader-input"
      type="file"
      :accept="accept"
      :disabled="disabled || uploading"
      @change="handleFileChange"
    />

    <slot
      name="trigger"
      :button-label="buttonLabel"
      :disabled="uploadDisabled"
      :open-picker="openPicker"
      :progress="progress"
      :uploading="uploading"
    >
      <button class="base-file-uploader-button" type="button" :disabled="uploadDisabled" @click="openPicker">
        <UploadCloud :size="16" stroke-width="1.8"/>
        <span>{{ uploading ? `${progress}%` : buttonLabel }}</span>
      </button>
    </slot>

    <div v-if="uploading" class="base-file-uploader-progress" aria-hidden="true">
      <span :style="{width: `${progress}%`}"></span>
    </div>

    <p v-if="message" class="base-file-uploader-message" :class="{error: hasError}">
      {{ message }}
    </p>
  </div>
</template>

<script setup lang="ts">
import {computed, ref} from 'vue'
import {UploadCloud} from 'lucide-vue-next'

import {completeUpload, createUploadTicket, uploadToMinio} from '@/features/storage/api/storage'
import type {FileAsset, StorageBucketType, StorageScopeType, StorageUsage} from '@/features/storage/types/storage'

type UploadFilePreprocessor = (file: File) => File | null | Promise<File | null>

const props = withDefaults(defineProps<{
  usage: StorageUsage
  scopeType: StorageScopeType
  scopeId?: string | null
  bucketType?: StorageBucketType | null
  accept?: string
  buttonLabel?: string
  disabled?: boolean
  maxSizeMb?: number
  prepareFile?: UploadFilePreprocessor
}>(), {
  accept: undefined,
  buttonLabel: 'Upload',
  disabled: false,
  scopeId: null,
  bucketType: null,
  prepareFile: undefined,
})

const emit = defineEmits<{
  uploaded: [asset: FileAsset]
  error: [message: string]
}>()

defineSlots<{
  trigger?: (props: {
    buttonLabel: string
    disabled: boolean
    openPicker: () => void
    progress: number
    uploading: boolean
  }) => unknown
}>()

const inputRef = ref<HTMLInputElement | null>(null)
const uploading = ref(false)
const progress = ref(0)
const message = ref('')
const hasError = ref(false)
const uploadDisabled = computed(() => props.disabled || uploading.value)

function openPicker() {
  inputRef.value?.click()
}

async function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  progress.value = 0
  message.value = ''
  hasError.value = false

  try {
    const uploadFile = props.prepareFile ? await props.prepareFile(file) : file
    if (!uploadFile) return

    if (props.maxSizeMb != null && uploadFile.size > props.maxSizeMb * 1024 * 1024) {
      const text = `File must be ${props.maxSizeMb}MB or smaller`
      message.value = text
      hasError.value = true
      emit('error', text)
      return
    }
    const normalizedScopeId = props.scopeId === '' ? null : (props.scopeId ?? null)

    // COURSE scope 需要有效的 scopeId
    if (props.scopeType === 'COURSE' && !normalizedScopeId) {
      const text = 'Course upload requires a valid scopeId'
      message.value = text
      hasError.value = true
      emit('error', text)
      return
    }

    uploading.value = true
    const ticket = await createUploadTicket({
      usage: props.usage,
      scopeType: props.scopeType,
      scopeId: normalizedScopeId,
      bucketType: props.bucketType ?? null,
      fileName: uploadFile.name,
      contentType: uploadFile.type || 'application/octet-stream',
      sizeBytes: uploadFile.size,
    })
    await uploadToMinio(ticket, uploadFile, (value) => {
      progress.value = value
    })
    const asset = await completeUpload(ticket.objectId)
    progress.value = 100
    emit('uploaded', asset)
  } catch (error) {
    const text = error instanceof Error ? error.message : 'Upload failed'
    message.value = text
    hasError.value = true
    emit('error', text)
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.base-file-uploader {
  display: grid;
  gap: 8px;
}

.base-file-uploader-input {
  display: none;
}

.base-file-uploader-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 36px;
  padding: 0 14px;
  border: 1px solid var(--color-outline);
  border-radius: 6px;
  background: var(--color-surface-card);
  color: var(--color-on-surface);
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 13px;
  font-weight: 400;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}

.base-file-uploader-button:hover:not(:disabled) {
  border-color: var(--color-on-surface);
  background: var(--color-surface-canvas);
}

.base-file-uploader-button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.base-file-uploader-progress {
  height: 3px;
  overflow: hidden;
  background: var(--color-outline-light);
  border-radius: 999px;
}

.base-file-uploader-progress span {
  display: block;
  height: 100%;
  background: var(--color-on-surface);
  transition: width 0.2s ease;
}

.base-file-uploader-message {
  margin: 0;
  font-size: 12px;
  color: var(--color-muted);
}

.base-file-uploader-message.error {
  color: var(--color-danger);
}
</style>
