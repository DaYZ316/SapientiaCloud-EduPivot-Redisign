<template>
  <div class="base-text-editor" :class="{ disabled }">
    <div v-if="toolbar" class="base-text-editor-toolbar" aria-label="Text formatting">
      <div class="base-text-editor-tools">
        <button
          v-for="action in toolbarActions"
          :key="action.id"
          class="base-text-editor-tool"
          type="button"
          :title="action.label"
          :aria-label="action.label"
          :disabled="disabled"
          @mousedown.prevent
          @click="applyAction(action.id)"
        >
          <component :is="action.icon" :size="15" stroke-width="1.9"/>
        </button>
        <BaseFileUploader
          v-if="imageUploadOptions"
          :usage="imageUploadOptions.usage"
          :scope-type="imageUploadOptions.scopeType"
          :scope-id="imageUploadOptions.scopeId ?? null"
          :bucket-type="imageUploadOptions.bucketType ?? null"
          :accept="imageUploadOptions.accept"
          :button-label="imageUploadOptions.buttonLabel || 'Upload image'"
          :disabled="disabled"
          :max-size-mb="imageUploadOptions.maxSizeMb"
          :prepare-file="prepareImageFile"
          @uploaded="handleImageUploaded"
          @error="emit('image-upload-error', $event)"
        >
          <template #trigger="{ openPicker, progress, uploading, disabled: uploadDisabled }">
            <button
              class="base-text-editor-tool"
              type="button"
              :title="uploading ? `${progress}%` : imageUploadOptions.buttonLabel || 'Upload image'"
              :aria-label="imageUploadOptions.buttonLabel || 'Upload image'"
              :disabled="uploadDisabled"
              @mousedown.prevent
              @click="openPicker"
            >
              <ImageIcon :size="15" stroke-width="1.9"/>
              <span v-if="uploading" class="base-text-editor-tool-progress">{{ progress }}</span>
            </button>
          </template>
        </BaseFileUploader>
      </div>
    </div>

    <div
      ref="editorRef"
      :id="id"
      class="base-text-editor-input"
      :class="{'is-empty': !hasContent}"
      :style="editorStyle"
      role="textbox"
      :contenteditable="disabled ? 'false' : 'true'"
      :data-placeholder="placeholder"
      :aria-label="ariaLabel"
      aria-multiline="true"
      :aria-required="required"
      :aria-disabled="disabled"
      @input="handleInput"
      @focus="emit('focus', $event)"
      @blur="emit('blur', $event)"
      @paste.prevent="handlePaste"
    ></div>

    <input v-if="name" type="hidden" :name="name" :value="modelValue"/>

    <div v-if="imagePreviews.length > 0" class="base-text-editor-images">
      <figure v-for="image in imagePreviews" :key="image.id" class="base-text-editor-image">
        <img :src="image.url" :alt="image.fileName"/>
        <button type="button" :aria-label="`Remove ${image.fileName}`" :disabled="disabled" @click="removeImage(image.id)">
          <X :size="13" stroke-width="2"/>
        </button>
      </figure>
    </div>

    <div v-if="maxLength != null" class="base-text-editor-footer">
      <span class="base-text-editor-count">{{ characterCount }} / {{ maxLength }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, nextTick, onMounted, ref, watch} from 'vue'
import {
  Bold, Eraser, Image as ImageIcon, Italic, Link, List, ListOrdered, Quote, X,
} from 'lucide-vue-next'
import type {FileAsset, StorageBucketType, StorageScopeType, StorageUsage} from '@/features/storage/types/storage'
import BaseFileUploader from '@/shared/components/BaseFileUploader.vue'

type ToolbarActionId = 'bold' | 'italic' | 'unorderedList' | 'orderedList' | 'quote' | 'link' | 'clear'
type ContentFormat = 'markdown' | 'html'
type UploadFilePreprocessor = (file: File) => File | null | Promise<File | null>
type ImageUploadOptions = {
  usage: StorageUsage
  scopeType: StorageScopeType
  scopeId?: string | null
  bucketType?: StorageBucketType | null
  accept?: string
  buttonLabel?: string
  maxSizeMb?: number
}

type MarkdownBlock =
  | {type: 'paragraph' | 'quote'; text: string}
  | {type: 'list'; ordered: boolean; items: string[]}

const props = withDefaults(defineProps<{
  modelValue: string
  placeholder?: string
  id?: string
  name?: string
  rows?: number
  minRows?: number
  maxLength?: number
  required?: boolean
  disabled?: boolean
  ariaLabel?: string
  toolbar?: boolean
  contentFormat?: ContentFormat
  imageUploadOptions?: ImageUploadOptions
  imageAssets?: FileAsset[]
  prepareImageFile?: UploadFilePreprocessor
}>(), {
  placeholder: '',
  id: undefined,
  name: undefined,
  rows: 4,
  minRows: undefined,
  maxLength: undefined,
  required: false,
  disabled: false,
  ariaLabel: undefined,
  toolbar: true,
  contentFormat: 'markdown',
  imageUploadOptions: undefined,
  imageAssets: () => [],
  prepareImageFile: undefined,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'update:imageAssets': [value: FileAsset[]]
  'image-upload-error': [message: string]
  input: [value: string]
  focus: [event: FocusEvent]
  blur: [event: FocusEvent]
}>()

const editorRef = ref<HTMLDivElement | null>(null)
const characterCount = computed(() => props.modelValue.length)
const hasContent = computed(() => props.modelValue.trim().length > 0)
const editorStyle = computed(() => ({
  minHeight: `${Math.max(props.rows, props.minRows ?? 4) * 28}px`,
}))
const imagePreviews = computed(() => props.contentFormat === 'html' ? [] : props.imageAssets
  .map(image => ({
    id: image.id,
    fileName: image.fileName,
    url: image.url || '',
  }))
  .filter(image => image.url))

const toolbarActions = [
  {id: 'bold', label: 'Bold', icon: Bold},
  {id: 'italic', label: 'Italic', icon: Italic},
  {id: 'unorderedList', label: 'Bullet list', icon: List},
  {id: 'orderedList', label: 'Numbered list', icon: ListOrdered},
  {id: 'quote', label: 'Quote', icon: Quote},
  {id: 'link', label: 'Link', icon: Link},
  {id: 'clear', label: 'Clear content', icon: Eraser},
] as const

onMounted(() => {
  syncEditorFromValue()
})

watch(() => props.modelValue, (value) => {
  const editor = editorRef.value
  if (!editor || editorToValue(editor) === value) return
  syncEditorFromValue()
})

function handleInput() {
  emitEditorValue()
}

function handlePaste(event: ClipboardEvent) {
  if (props.disabled) return
  const text = event.clipboardData?.getData('text/plain') ?? ''
  document.execCommand('insertText', false, text)
  emitEditorValue()
}

function applyAction(action: ToolbarActionId) {
  if (props.disabled) return

  editorRef.value?.focus()

  if (action === 'clear') {
    updateEditorValue('')
    return
  }

  switch (action) {
    case 'bold':
      document.execCommand('bold')
      break
    case 'italic':
      document.execCommand('italic')
      break
    case 'unorderedList':
      document.execCommand('insertUnorderedList')
      break
    case 'orderedList':
      document.execCommand('insertOrderedList')
      break
    case 'quote':
      document.execCommand('formatBlock', false, isSelectionInside('blockquote') ? 'p' : 'blockquote')
      break
    case 'link':
      applyLink()
      break
  }

  emitEditorValue()
}

function handleImageUploaded(asset: FileAsset) {
  emit('update:imageAssets', [...props.imageAssets, asset])
  if (props.contentFormat === 'html') {
    insertImageAsset(asset)
  }
}

function removeImage(imageId: string) {
  emit('update:imageAssets', props.imageAssets.filter(image => image.id !== imageId))
}

async function updateEditorValue(value: string) {
  setEditorHtml(valueToEditorHtml(value))
  emit('update:modelValue', value)
  emit('input', value)
  await nextTick()
  editorRef.value?.focus()
}

function emitEditorValue() {
  const editor = editorRef.value
  if (!editor) return

  let value = editorToValue(editor)
  if (props.maxLength != null && value.length > props.maxLength) {
    value = value.slice(0, props.maxLength)
    setEditorHtml(valueToEditorHtml(value))
  }

  emit('update:modelValue', value)
  emit('input', value)
}

function syncEditorFromValue() {
  setEditorHtml(valueToEditorHtml(props.modelValue))
}

function setEditorHtml(html: string) {
  if (editorRef.value) {
    editorRef.value.innerHTML = html
  }
}

function applyLink() {
  const rawUrl = window.prompt('Link URL', 'https://')
  const url = normalizeUrl(rawUrl)
  if (!url) return

  const selection = window.getSelection()
  if (selection?.isCollapsed) {
    document.execCommand('insertHTML', false, `<a href="${escapeAttribute(url)}">${escapeHtml(url)}</a>`)
    return
  }

  document.execCommand('createLink', false, url)
}

function insertImageAsset(asset: FileAsset) {
  if (!asset.url) return
  editorRef.value?.focus()
  const imageHtml = `<figure class="chapter-image"><img src="${escapeAttribute(asset.url)}" data-storage-file-id="${escapeAttribute(asset.id)}" alt="${escapeAttribute(asset.fileName)}" loading="lazy"></figure><p><br></p>`
  document.execCommand('insertHTML', false, imageHtml)
  emitEditorValue()
}

function normalizeUrl(value: string | null) {
  const trimmed = value?.trim() ?? ''
  if (!trimmed) return ''
  if (/^(https?:\/\/|mailto:)/i.test(trimmed)) return trimmed
  return `https://${trimmed}`
}

function isSelectionInside(tagName: string) {
  const selection = window.getSelection()
  let node = selection?.anchorNode ?? null
  const target = tagName.toUpperCase()

  while (node && node !== editorRef.value) {
    if (node instanceof HTMLElement && node.tagName === target) return true
    node = node.parentNode
  }

  return false
}

function valueToEditorHtml(content: string) {
  return props.contentFormat === 'html' ? sanitizeEditorHtml(content, false) : markdownToEditorHtml(content)
}

function editorToValue(editor: HTMLElement) {
  return props.contentFormat === 'html' ? editorToHtml(editor) : editorToMarkdown(editor)
}

function markdownToEditorHtml(content: string) {
  return parseMarkdownBlocks(content)
    .map(block => blockToHtml(block))
    .join('')
}

function parseMarkdownBlocks(content: string) {
  const lines = content.replace(/\r\n/g, '\n').split('\n')
  const blocks: MarkdownBlock[] = []
  let paragraph: string[] = []
  let listBlock: Extract<MarkdownBlock, {type: 'list'}> | null = null

  function flushParagraph() {
    if (paragraph.length === 0) return
    blocks.push({type: 'paragraph', text: paragraph.join('\n')})
    paragraph = []
  }

  function flushList() {
    if (!listBlock) return
    blocks.push(listBlock)
    listBlock = null
  }

  for (const line of lines) {
    if (!line.trim()) {
      flushParagraph()
      flushList()
      continue
    }

    const unorderedMatch = line.match(/^\s*-\s+(.+)$/)
    const orderedMatch = line.match(/^\s*\d+\.\s+(.+)$/)
    const quoteMatch = line.match(/^\s*>\s+(.+)$/)

    if (unorderedMatch || orderedMatch) {
      flushParagraph()
      const ordered = Boolean(orderedMatch)
      const text = unorderedMatch?.[1] ?? orderedMatch?.[1] ?? ''
      if (!listBlock || listBlock.ordered !== ordered) {
        flushList()
        listBlock = {type: 'list', ordered, items: []}
      }
      listBlock.items.push(text)
      continue
    }

    flushList()

    if (quoteMatch) {
      flushParagraph()
      blocks.push({type: 'quote', text: quoteMatch[1]})
      continue
    }

    paragraph.push(line)
  }

  flushParagraph()
  flushList()
  return blocks
}

function blockToHtml(block: MarkdownBlock) {
  if (block.type === 'list') {
    const tag = block.ordered ? 'ol' : 'ul'
    const items = block.items.map(item => `<li>${inlineMarkdownToHtml(item)}</li>`).join('')
    return `<${tag}>${items}</${tag}>`
  }

  if (block.type === 'quote') {
    return `<blockquote>${inlineMarkdownToHtml(block.text)}</blockquote>`
  }

  return `<p>${inlineMarkdownToHtml(block.text)}</p>`
}

function inlineMarkdownToHtml(text: string) {
  const pattern = /(\*\*[^*]+\*\*|_[^_]+_|\[[^\]]+\]\([^)]+\))/g
  let result = ''
  let lastIndex = 0
  let match: RegExpExecArray | null

  while ((match = pattern.exec(text)) !== null) {
    result += escapeHtml(text.slice(lastIndex, match.index)).replace(/\n/g, '<br>')
    result += inlineTokenToHtml(match[0])
    lastIndex = match.index + match[0].length
  }

  result += escapeHtml(text.slice(lastIndex)).replace(/\n/g, '<br>')
  return result
}

function inlineTokenToHtml(token: string) {
  if (token.startsWith('**') && token.endsWith('**')) {
    return `<strong>${escapeHtml(token.slice(2, -2))}</strong>`
  }
  if (token.startsWith('_') && token.endsWith('_')) {
    return `<em>${escapeHtml(token.slice(1, -1))}</em>`
  }

  const linkMatch = token.match(/^\[([^\]]+)\]\(([^)]+)\)$/)
  if (linkMatch) {
    const url = normalizeUrl(linkMatch[2])
    return `<a href="${escapeAttribute(url)}">${escapeHtml(linkMatch[1])}</a>`
  }

  return escapeHtml(token)
}

function editorToMarkdown(editor: HTMLElement) {
  return Array.from(editor.childNodes)
    .map(node => serializeBlockNode(node))
    .filter(Boolean)
    .join('\n')
    .trim()
}

function editorToHtml(editor: HTMLElement) {
  return Array.from(editor.childNodes)
    .map(node => serializeHtmlNode(node, true))
    .join('')
    .trim()
}

function sanitizeEditorHtml(content: string, normalizeImages = true) {
  const template = document.createElement('template')
  template.innerHTML = content
  return Array.from(template.content.childNodes)
    .map(node => serializeHtmlNode(node, normalizeImages))
    .join('')
}

function serializeHtmlNode(node: Node, normalizeImages: boolean): string {
  if (node.nodeType === Node.TEXT_NODE) return escapeHtml(node.textContent ?? '')
  if (!(node instanceof HTMLElement)) return ''

  const tag = node.tagName.toLowerCase()
  if (tag === 'br') return '<br>'
  if (tag === 'strong' || tag === 'b') return `<strong>${serializeHtmlChildren(node, normalizeImages)}</strong>`
  if (tag === 'em' || tag === 'i') return `<em>${serializeHtmlChildren(node, normalizeImages)}</em>`
  if (tag === 'a') return serializeHtmlLink(node, normalizeImages)
  if (tag === 'ul' || tag === 'ol') return `<${tag}>${serializeHtmlChildren(node, normalizeImages)}</${tag}>`
  if (tag === 'li') return `<li>${serializeHtmlChildren(node, normalizeImages)}</li>`
  if (tag === 'blockquote') return `<blockquote>${serializeHtmlChildren(node, normalizeImages)}</blockquote>`
  if (tag === 'figure') return serializeHtmlFigure(node, normalizeImages)
  if (tag === 'img') return serializeHtmlImage(node, normalizeImages)
  if (tag === 'div' || tag === 'p') return `<p>${serializeHtmlChildren(node, normalizeImages)}</p>`

  return serializeHtmlChildren(node, normalizeImages)
}

function serializeHtmlChildren(node: Node, normalizeImages: boolean) {
  return Array.from(node.childNodes).map(child => serializeHtmlNode(child, normalizeImages)).join('')
}

function serializeHtmlLink(node: HTMLElement, normalizeImages: boolean) {
  const url = normalizeUrl(node.getAttribute('href'))
  if (!url) return serializeHtmlChildren(node, normalizeImages)
  return `<a href="${escapeAttribute(url)}">${serializeHtmlChildren(node, normalizeImages)}</a>`
}

function serializeHtmlFigure(node: HTMLElement, normalizeImages: boolean) {
  const content = serializeHtmlChildren(node, normalizeImages)
  if (!content.trim()) return ''
  return `<figure class="chapter-image">${content}</figure>`
}

function serializeHtmlImage(node: HTMLElement, normalizeImages: boolean) {
  const fileId = normalizeStorageFileId(node.getAttribute('data-storage-file-id') || storageFileIdFromSrc(node.getAttribute('src')))
  if (!fileId) return ''

  const src = normalizeImages ? `sc-storage-file:${fileId}` : normalizeImageSrc(node.getAttribute('src'), fileId)
  const alt = node.getAttribute('alt') ?? ''
  return `<img src="${escapeAttribute(src)}" data-storage-file-id="${escapeAttribute(fileId)}" alt="${escapeAttribute(alt)}" loading="lazy">`
}

function storageFileIdFromSrc(value: string | null) {
  const match = value?.match(/^sc-storage-file:([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$/i)
  return match?.[1] ?? ''
}

function normalizeStorageFileId(value: string | null) {
  const trimmed = value?.trim() ?? ''
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(trimmed) ? trimmed : ''
}

function normalizeImageSrc(value: string | null, fileId: string) {
  const trimmed = value?.trim() ?? ''
  return trimmed ? trimmed : `sc-storage-file:${fileId}`
}

function serializeBlockNode(node: Node): string {
  if (node.nodeType === Node.TEXT_NODE) return node.textContent ?? ''
  if (!(node instanceof HTMLElement)) return ''

  const tag = node.tagName.toLowerCase()
  if (tag === 'br') return '\n'
  if (tag === 'ul') return serializeList(node, false)
  if (tag === 'ol') return serializeList(node, true)
  if (tag === 'blockquote') {
    return serializeInlineChildren(node)
      .split('\n')
      .map(line => `> ${line}`)
      .join('\n')
  }

  return serializeInlineChildren(node).trim()
}

function serializeList(node: HTMLElement, ordered: boolean) {
  return Array.from(node.children)
    .filter(child => child.tagName.toLowerCase() === 'li')
    .map((item, index) => {
      const prefix = ordered ? `${index + 1}. ` : '- '
      return `${prefix}${serializeInlineChildren(item).trim()}`
    })
    .join('\n')
}

function serializeInlineChildren(node: Node) {
  return Array.from(node.childNodes).map(child => serializeInlineNode(child)).join('')
}

function serializeInlineNode(node: Node): string {
  if (node.nodeType === Node.TEXT_NODE) return node.textContent ?? ''
  if (!(node instanceof HTMLElement)) return ''

  const tag = node.tagName.toLowerCase()
  const content = serializeInlineChildren(node)

  if (tag === 'br') return '\n'
  if (tag === 'strong' || tag === 'b') return `**${content}**`
  if (tag === 'em' || tag === 'i') return `_${content}_`
  if (tag === 'a') return `[${content}](${node.getAttribute('href') ?? ''})`
  if (tag === 'div' || tag === 'p') return `${content}\n`
  if (tag === 'ul') return `\n${serializeList(node, false)}`
  if (tag === 'ol') return `\n${serializeList(node, true)}`
  if (tag === 'blockquote') {
    return `\n${content.split('\n').map(line => `> ${line}`).join('\n')}`
  }

  return content
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function escapeAttribute(value: string) {
  return escapeHtml(value).replace(/"/g, '&quot;')
}
</script>

<style scoped>
.base-text-editor {
  width: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--color-outline-variant);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  transition: border-color 0.2s, box-shadow 0.2s, background 0.2s;
}

.base-text-editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 12px;
  padding: 8px 10px;
  border-bottom: 1px solid var(--color-outline-variant);
  background: var(--color-surface-container-high);
}

.base-text-editor-tools {
  display: flex;
  align-items: center;
  gap: 4px;
}

.base-text-editor-tools :deep(.base-file-uploader) {
  display: contents;
}

.base-text-editor-tools :deep(.base-file-uploader-progress),
.base-text-editor-tools :deep(.base-file-uploader-message) {
  display: none;
}

.base-text-editor-tool {
  position: relative;
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--color-on-surface);
  cursor: pointer;
  transition: background 0.2s, color 0.2s, transform 0.2s;
}

.base-text-editor-tool:hover:not(:disabled) {
  background: var(--color-surface-container-high);
  color: var(--color-on-surface);
}

.base-text-editor-tool:active:not(:disabled) {
  transform: translateY(1px);
}

.base-text-editor-tool:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}

.base-text-editor-tool:disabled {
  cursor: not-allowed;
  opacity: 0.4;
}

.base-text-editor-tool-progress {
  position: absolute;
  inset: auto 3px 3px auto;
  min-width: 14px;
  padding: 1px 3px;
  border-radius: 999px;
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-size: 8px;
  font-weight: 700;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.base-text-editor:hover:not(.disabled) {
  border-color: var(--color-outline);
}

.base-text-editor:focus-within {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px var(--color-surface-container-high);
}

.base-text-editor.disabled {
  cursor: not-allowed;
  opacity: 0.56;
}

.base-text-editor-input {
  width: 100%;
  padding: 12px 16px;
  border: 0;
  background: transparent;
  color: var(--color-on-surface);
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  line-height: 1.6;
  outline: none;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.base-text-editor-input.is-empty::before {
  content: attr(data-placeholder);
  color: var(--color-outline);
  pointer-events: none;
}

.base-text-editor-input[contenteditable='false'] {
  cursor: not-allowed;
}

.base-text-editor-input :deep(p),
.base-text-editor-input :deep(blockquote),
.base-text-editor-input :deep(ul),
.base-text-editor-input :deep(ol) {
  margin: 0 0 10px;
}

.base-text-editor-input :deep(p:last-child),
.base-text-editor-input :deep(blockquote:last-child),
.base-text-editor-input :deep(ul:last-child),
.base-text-editor-input :deep(ol:last-child) {
  margin-bottom: 0;
}

.base-text-editor-input :deep(ul),
.base-text-editor-input :deep(ol) {
  padding-left: 20px;
}

.base-text-editor-input :deep(blockquote) {
  padding-left: 12px;
  border-left: 2px solid var(--color-outline);
  color: var(--color-secondary);
}

.base-text-editor-input :deep(em),
.base-text-editor-input :deep(i) {
  font-style: italic;
}

.base-text-editor-input :deep(a) {
  color: var(--color-primary);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.base-text-editor-input :deep(figure.chapter-image) {
  display: flex;
  justify-content: center;
  margin: 14px 0;
}

.base-text-editor-input :deep(figure.chapter-image img) {
  display: block;
  max-width: min(100%, 560px);
  max-height: 360px;
  width: auto;
  height: auto;
  object-fit: contain;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  background: var(--color-surface-container);
}

.base-text-editor-images {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(72px, 96px));
  gap: 8px;
  padding: 0 12px 12px;
}

.base-text-editor-image {
  position: relative;
  margin: 0;
  aspect-ratio: 1;
  overflow: hidden;
  border: 1px solid var(--color-outline-light);
  border-radius: 6px;
  background: var(--color-surface-container);
}

.base-text-editor-image img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.base-text-editor-image button {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: color-mix(in srgb, var(--color-surface) 88%, transparent);
  color: var(--color-on-surface);
  cursor: pointer;
}

.base-text-editor-image button:hover:not(:disabled) {
  background: var(--color-surface);
}

.base-text-editor-image button:disabled {
  cursor: not-allowed;
  opacity: 0.56;
}

.base-text-editor-footer {
  display: flex;
  justify-content: flex-end;
  padding: 0 12px 10px;
}

.base-text-editor-count {
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 11px;
  font-weight: 400;
  letter-spacing: 0.08em;
  color: var(--color-muted);
  font-variant-numeric: tabular-nums;
}
</style>
