<template>
  <div :class="[`size-${size}`, `shape-${shape}`]" class="base-image-uploader">
    <BaseFileUploader
        :accept="accept"
        :button-label="currentButtonLabel"
        :disabled="disabled"
        :prepare-file="prepareImageFile"
        :scope-id="scopeId"
        :scope-type="scopeType"
        :usage="usage"
        @error="handleError"
        @uploaded="handleUploaded"
    >
      <template #trigger="{ disabled: pickerDisabled, openPicker, progress, uploading }">
        <div class="base-image-uploader-field">
          <button
              :aria-label="currentButtonLabel"
              :disabled="pickerDisabled"
              class="base-image-uploader-preview"
              type="button"
              @click="openPicker"
          >
            <template v-if="displayUrl">
              <img :alt="alt" :src="displayUrl"/>
              <span v-if="hasUploadedImage && currentPreviewLabel"
                    class="base-image-uploader-preview-overlay">
                <UploadCloud :size="18" stroke-width="1.8"/>
                <span>{{ currentPreviewLabel }}</span>
              </span>
            </template>
            <span v-else-if="fallbackLabel" class="base-image-uploader-avatar-fallback">
              {{ fallbackLabel }}
            </span>
            <span v-else class="base-image-uploader-placeholder">
              <ImageIcon :size="24" stroke-width="1.7"/>
              <span>{{ currentButtonLabel }}</span>
            </span>
            <span v-if="uploading" class="base-image-uploader-badge">{{ progress }}%</span>
          </button>

          <div class="base-image-uploader-actions">
            <button
                :disabled="pickerDisabled"
                class="base-image-uploader-action primary"
                type="button"
                @click="openPicker"
            >
              <UploadCloud :size="16" stroke-width="1.8"/>
              <span>{{ currentButtonLabel }}</span>
            </button>
            <button
                v-if="canRemove"
                :disabled="disabled || uploading"
                class="base-image-uploader-action"
                type="button"
                @click="removeImage"
            >
              <Trash2 :size="16" stroke-width="1.8"/>
              <span>Remove</span>
            </button>
            <p v-if="helpText" class="base-image-uploader-help">{{ helpText }}</p>
          </div>
        </div>
      </template>
    </BaseFileUploader>

    <Teleport to="body">
      <div v-if="cropOpen" class="image-crop-overlay">
        <section aria-labelledby="image-crop-title" aria-modal="true" class="image-crop-dialog" role="dialog">
          <header class="image-crop-header">
            <div>
              <p class="image-crop-eyebrow">IMAGE FIELD</p>
              <h2 id="image-crop-title">Crop image</h2>
            </div>
            <button class="image-crop-icon-button" type="button" @click="cancelCrop">
              <X :size="20" stroke-width="1.8"/>
            </button>
          </header>

          <div class="image-crop-body">
            <div :style="cropStageStyle" class="image-crop-stage">
              <canvas
                  ref="canvasRef"
                  class="image-crop-canvas"
                  @pointerdown="startDrag"
                  @pointerleave="stopDrag"
                  @pointermove="dragImage"
                  @pointerup="stopDrag"
              ></canvas>
              <div :class="{ circle: shape === 'circle' }" class="image-crop-frame"></div>
            </div>

            <aside class="image-crop-controls">
              <div class="image-crop-control-row">
                <button class="image-crop-tool" type="button" @click="rotateImage(-90)">
                  <RotateCcw :size="17" stroke-width="1.8"/>
                  <span>Left</span>
                </button>
                <button class="image-crop-tool" type="button" @click="rotateImage(90)">
                  <RotateCw :size="17" stroke-width="1.8"/>
                  <span>Right</span>
                </button>
                <button class="image-crop-tool" type="button" @click="resetCrop">
                  <RefreshCcw :size="17" stroke-width="1.8"/>
                  <span>Reset</span>
                </button>
              </div>

              <label class="image-crop-slider">
                <span>Zoom</span>
                <input v-model.number="zoom" max="3" min="1" step="0.05" type="range"/>
              </label>

              <div class="image-crop-footer">
                <button class="image-crop-secondary" type="button" @click="cancelCrop">Cancel</button>
                <button class="image-crop-primary" type="button" @click="confirmCrop">Confirm</button>
              </div>
            </aside>
          </div>
        </section>
      </div>
    </Teleport>
  </div>
</template>

<script lang="ts" setup>
import {computed, nextTick, onBeforeUnmount, ref, watch} from 'vue'
import {Image as ImageIcon, RefreshCcw, RotateCcw, RotateCw, Trash2, UploadCloud, X,} from 'lucide-vue-next'

import BaseFileUploader from '@/shared/components/BaseFileUploader.vue'
import type {FileAsset, StorageScopeType, StorageUsage} from '@/features/storage/types/storage'

type ImageShape = 'rectangle' | 'circle'
type ImageSize = 'cover' | 'avatar'
type UploadedBehavior = 'replace' | 'continue'
type CropResolver = (file: File | null) => void

const props = withDefaults(defineProps<{
  modelValue?: string | null
  usage: StorageUsage
  scopeType: StorageScopeType
  scopeId?: string | null
  previewUrl?: string | null
  fallbackUrl?: string | null
  fallbackLabel?: string | null
  alt?: string
  buttonLabel?: string
  uploadedButtonLabel?: string
  uploadedPreviewLabel?: string
  uploadedBehavior?: UploadedBehavior
  allowRemove?: boolean
  helpText?: string
  disabled?: boolean
  shape?: ImageShape
  size?: ImageSize
  accept?: string
  maxSizeMb?: number
}>(), {
  modelValue: '',
  scopeId: null,
  previewUrl: '',
  fallbackUrl: '',
  fallbackLabel: '',
  alt: 'Uploaded image',
  buttonLabel: 'Upload image',
  uploadedButtonLabel: '',
  uploadedPreviewLabel: '',
  uploadedBehavior: 'replace',
  allowRemove: true,
  helpText: '',
  disabled: false,
  shape: 'rectangle',
  size: 'cover',
  accept: 'image/jpeg,image/png,image/webp',
  maxSizeMb: 10,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  uploaded: [asset: FileAsset]
  error: [message: string]
  removed: []
}>()

const canvasRef = ref<HTMLCanvasElement | null>(null)
const cropOpen = ref(false)
const zoom = ref(1)
const rotation = ref(0)
const offsetX = ref(0)
const offsetY = ref(0)
const sourceImage = ref<HTMLImageElement | null>(null)
const sourceFile = ref<File | null>(null)
const sourceUrl = ref('')
const cropResolver = ref<CropResolver | null>(null)
const dragging = ref(false)
const dragStartX = ref(0)
const dragStartY = ref(0)
const dragOriginX = ref(0)
const dragOriginY = ref(0)

const aspectRatio = computed(() => props.shape === 'circle' ? 1 : 16 / 9)
const hasUploadedImage = computed(() => Boolean(props.modelValue || props.previewUrl))
const displayUrl = computed(() => props.previewUrl || props.fallbackUrl || '')
const currentButtonLabel = computed(() => {
  if (!hasUploadedImage.value) {
    return props.buttonLabel
  }

  if (props.uploadedButtonLabel) {
    return props.uploadedButtonLabel
  }

  return props.buttonLabel
})
const currentPreviewLabel = computed(() => props.uploadedPreviewLabel || props.uploadedButtonLabel || '')
const canRemove = computed(() =>
    props.allowRemove && props.uploadedBehavior === 'replace' && props.shape !== 'circle' && hasUploadedImage.value,
)
const cropStageStyle = computed(() => ({
  '--image-crop-aspect': String(aspectRatio.value),
}))

watch([zoom, rotation, offsetX, offsetY], () => {
  renderPreview()
})

onBeforeUnmount(() => {
  revokeSourceUrl()
  resolveCrop(null)
})

async function prepareImageFile(file: File): Promise<File | null> {
  const validationError = validateImage(file)
  if (validationError) {
    emit('error', validationError)
    return null
  }

  return openCropDialog(file)
}

function validateImage(file: File): string {
  if (!file.type.startsWith('image/')) {
    return 'Please choose an image file.'
  }

  const maxBytes = props.maxSizeMb * 1024 * 1024
  if (file.size > maxBytes) {
    return `Image must be ${props.maxSizeMb}MB or smaller.`
  }

  return ''
}

function openCropDialog(file: File) {
  revokeSourceUrl()
  sourceFile.value = file
  sourceUrl.value = URL.createObjectURL(file)
  resetCrop()

  return new Promise<File | null>((resolve) => {
    cropResolver.value = resolve
    const image = new Image()
    image.onload = async () => {
      sourceImage.value = image
      cropOpen.value = true
      await nextTick()
      renderPreview()
    }
    image.onerror = () => {
      emit('error', 'Unable to load selected image.')
      revokeSourceUrl()
      resolveCrop(null)
    }
    image.src = sourceUrl.value
  })
}

function handleUploaded(asset: FileAsset) {
  emit('update:modelValue', asset.id)
  emit('uploaded', asset)
}

function handleError(message: string) {
  emit('error', message)
}

function removeImage() {
  emit('update:modelValue', '')
  emit('removed')
}

function rotateImage(degrees: number) {
  rotation.value = (rotation.value + degrees + 360) % 360
}

function resetCrop() {
  zoom.value = 1
  rotation.value = 0
  offsetX.value = 0
  offsetY.value = 0
}

function startDrag(event: PointerEvent) {
  if (!sourceImage.value) return

  dragging.value = true
  dragStartX.value = event.clientX
  dragStartY.value = event.clientY
  dragOriginX.value = offsetX.value
  dragOriginY.value = offsetY.value
  if (event.currentTarget instanceof HTMLElement) {
    event.currentTarget.setPointerCapture(event.pointerId)
  }
}

function dragImage(event: PointerEvent) {
  if (!dragging.value) return

  offsetX.value = dragOriginX.value + event.clientX - dragStartX.value
  offsetY.value = dragOriginY.value + event.clientY - dragStartY.value
}

function stopDrag(event: PointerEvent) {
  if (!dragging.value) return

  dragging.value = false
  if (event.currentTarget instanceof HTMLElement) {
    event.currentTarget.releasePointerCapture(event.pointerId)
  }
}

function renderPreview() {
  const canvas = canvasRef.value
  const image = sourceImage.value
  if (!canvas || !image) return

  const rect = canvas.getBoundingClientRect()
  if (!rect.width || !rect.height) return

  const pixelRatio = window.devicePixelRatio || 1
  canvas.width = Math.round(rect.width * pixelRatio)
  canvas.height = Math.round(rect.height * pixelRatio)

  const context = canvas.getContext('2d')
  if (!context) return

  context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0)
  drawImageToContext(context, image, rect.width, rect.height, 1)
}

async function confirmCrop() {
  const image = sourceImage.value
  const file = sourceFile.value
  if (!image || !file) {
    resolveCrop(null)
    return
  }

  const outputWidth = props.shape === 'circle' ? 512 : 1280
  const outputHeight = Math.round(outputWidth / aspectRatio.value)
  const canvas = document.createElement('canvas')
  canvas.width = outputWidth
  canvas.height = outputHeight

  const context = canvas.getContext('2d')
  if (!context) {
    emit('error', 'Unable to crop selected image.')
    resolveCrop(null)
    return
  }

  const previewRect = canvasRef.value?.getBoundingClientRect()
  const previewWidth = previewRect?.width || outputWidth
  drawImageToContext(context, image, outputWidth, outputHeight, outputWidth / previewWidth)

  const type = getOutputType(file.type)
  const blob = await canvasToBlob(canvas, type)
  if (!blob) {
    emit('error', 'Unable to crop selected image.')
    resolveCrop(null)
    return
  }

  resolveCrop(new File([blob], buildCroppedFileName(file.name, type), {type}))
}

function drawImageToContext(
    context: CanvasRenderingContext2D,
    image: HTMLImageElement,
    width: number,
    height: number,
    offsetScale: number,
) {
  const angle = rotation.value * Math.PI / 180
  const rotatedWidth = rotation.value % 180 === 0 ? image.naturalWidth : image.naturalHeight
  const rotatedHeight = rotation.value % 180 === 0 ? image.naturalHeight : image.naturalWidth
  const scale = Math.max(width / rotatedWidth, height / rotatedHeight) * zoom.value

  context.clearRect(0, 0, width, height)
  context.fillStyle = getComputedStyle(document.documentElement).getPropertyValue('--color-surface-container') || '#f3f3f3'
  context.fillRect(0, 0, width, height)
  context.save()
  context.translate(width / 2 + offsetX.value * offsetScale, height / 2 + offsetY.value * offsetScale)
  context.rotate(angle)
  context.scale(scale, scale)
  context.drawImage(image, -image.naturalWidth / 2, -image.naturalHeight / 2)
  context.restore()
}

function cancelCrop() {
  resolveCrop(null)
}

function resolveCrop(file: File | null) {
  cropOpen.value = false
  sourceImage.value = null
  sourceFile.value = null
  const resolver = cropResolver.value
  cropResolver.value = null
  revokeSourceUrl()
  resolver?.(file)
}

function revokeSourceUrl() {
  if (!sourceUrl.value) return

  URL.revokeObjectURL(sourceUrl.value)
  sourceUrl.value = ''
}

function getOutputType(inputType: string) {
  return ['image/jpeg', 'image/png', 'image/webp'].includes(inputType) ? inputType : 'image/png'
}

function buildCroppedFileName(fileName: string, type: string) {
  const extension = type === 'image/jpeg' ? 'jpg' : type.replace('image/', '')
  const baseName = fileName.replace(/\.[^.]+$/, '') || 'image'
  return `${baseName}-cropped.${extension}`
}

function canvasToBlob(canvas: HTMLCanvasElement, type: string) {
  return new Promise<Blob | null>((resolve) => {
    canvas.toBlob(resolve, type, 0.92)
  })
}
</script>

<style scoped>
.base-image-uploader {
  display: grid;
  gap: 8px;
}

.base-image-uploader-field {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 14px;
}

.base-image-uploader-preview {
  position: relative;
  width: 152px;
  aspect-ratio: 16 / 9;
  display: grid;
  place-items: center;
  overflow: hidden;
  padding: 0;
  background: var(--color-surface-canvas);
  border: 1px dashed var(--color-outline-light);
  border-radius: 12px;
  color: var(--color-muted);
  cursor: pointer;
}

.base-image-uploader-preview:hover:not(:disabled) {
  border-color: var(--color-outline);
}

.base-image-uploader-preview:disabled {
  cursor: not-allowed;
  opacity: 0.64;
}

.base-image-uploader-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.base-image-uploader-preview-overlay {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  gap: 8px;
  padding: 14px;
  background: color-mix(in srgb, var(--color-on-surface) 64%, transparent);
  color: var(--color-on-primary);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 800;
  text-align: center;
  opacity: 0;
  transition: opacity 0.2s;
}

.base-image-uploader-preview:hover .base-image-uploader-preview-overlay,
.base-image-uploader-preview:focus-visible .base-image-uploader-preview-overlay {
  opacity: 1;
}

.base-image-uploader-placeholder {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 14px;
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 700;
}

.base-image-uploader-avatar-fallback {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-family: var(--font-heading);
  font-size: 40px;
  font-weight: 700;
  line-height: 1;
}

.base-image-uploader-badge {
  position: absolute;
  right: 8px;
  bottom: 8px;
  min-width: 42px;
  padding: 3px 8px;
  background: var(--color-primary);
  border-radius: var(--radius-sm);
  color: var(--color-on-primary);
  font-family: var(--font-label);
  font-size: 11px;
  font-weight: 800;
  text-align: center;
}

.base-image-uploader-actions {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.base-image-uploader-action {
  min-height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 0 12px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s;
}

.base-image-uploader-action.primary {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-on-primary);
}

.base-image-uploader-action:hover:not(:disabled) {
  background: var(--color-surface-container);
  border-color: var(--color-outline);
}

.base-image-uploader-action.primary:hover:not(:disabled) {
  background: var(--color-primary-soft);
  border-color: var(--color-primary-soft);
}

.base-image-uploader-action:disabled {
  cursor: not-allowed;
  opacity: 0.56;
}

.base-image-uploader-help {
  flex-basis: 100%;
  margin: 0;
  font-family: var(--font-body);
  font-size: 12px;
  line-height: 1.45;
  color: var(--color-muted);
}

.size-cover .base-image-uploader-field {
  grid-template-columns: 1fr;
}

.size-cover .base-image-uploader-preview {
  width: 100%;
  min-width: 0;
}

.size-avatar .base-image-uploader-field {
  grid-template-columns: 1fr;
  justify-items: start;
}

.size-avatar .base-image-uploader-preview {
  width: 116px;
  aspect-ratio: 1;
}

.shape-circle .base-image-uploader-preview {
  border-radius: 50%;
}

.image-crop-overlay {
  position: fixed;
  inset: 0;
  z-index: 1100;
  display: grid;
  place-items: center;
  padding: 24px;
  background: var(--color-overlay);
}

.image-crop-dialog {
  width: min(980px, 100%);
  max-height: calc(100dvh - 48px);
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  overflow: hidden;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 20px;
}

.image-crop-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 18px;
  padding: 22px 24px;
  border-bottom: 1px solid var(--color-outline-light);
}

.image-crop-eyebrow {
  margin: 0 0 8px;
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.image-crop-header h2 {
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 400;
}

.image-crop-icon-button {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  background: transparent;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-muted);
  cursor: pointer;
}

.image-crop-icon-button:hover {
  color: var(--color-on-surface);
  border-color: var(--color-outline);
}

.image-crop-body {
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
  gap: 20px;
  padding: 24px;
  overflow-y: auto;
}

.image-crop-stage {
  --image-crop-aspect: 16 / 9;

  position: relative;
  width: 100%;
  aspect-ratio: var(--image-crop-aspect);
  overflow: hidden;
  background: var(--color-surface-canvas);
  border: 1px solid var(--color-outline-light);
  border-radius: 14px;
}

.image-crop-canvas {
  width: 100%;
  height: 100%;
  display: block;
  touch-action: none;
  cursor: grab;
}

.image-crop-canvas:active {
  cursor: grabbing;
}

.image-crop-frame {
  position: absolute;
  inset: 0;
  pointer-events: none;
  border: 2px solid var(--color-primary);
  box-shadow: inset 0 0 0 1px var(--color-on-primary);
}

.image-crop-frame.circle {
  border-radius: 50%;
}

.image-crop-controls {
  display: grid;
  align-content: start;
  gap: 18px;
}

.image-crop-control-row {
  display: grid;
  gap: 8px;
}

.image-crop-tool {
  min-height: 40px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 9px;
  padding: 0 12px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.image-crop-tool:hover {
  background: var(--color-surface-container);
  border-color: var(--color-outline);
}

.image-crop-slider {
  display: grid;
  gap: 10px;
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 800;
  color: var(--color-on-surface);
}

.image-crop-slider input {
  width: 100%;
  accent-color: var(--color-primary);
}

.image-crop-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.image-crop-primary,
.image-crop-secondary {
  min-height: 40px;
  padding: 0 16px;
  border-radius: var(--radius-sm);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.image-crop-primary {
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  color: var(--color-on-primary);
}

.image-crop-secondary {
  background: transparent;
  border: 1px solid var(--color-outline);
  color: var(--color-on-surface);
}

.image-crop-primary:hover {
  background: var(--color-primary-soft);
  border-color: var(--color-primary-soft);
}

.image-crop-secondary:hover {
  background: var(--color-surface-container);
}

@media (max-width: 760px) {
  .base-image-uploader-field,
  .image-crop-body {
    grid-template-columns: 1fr;
  }

  .image-crop-footer {
    flex-direction: column-reverse;
  }

  .image-crop-primary,
  .image-crop-secondary {
    width: 100%;
  }
}
</style>
