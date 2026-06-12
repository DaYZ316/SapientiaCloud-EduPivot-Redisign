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

    <div v-if="maxLength != null" class="base-text-editor-footer">
      <span class="base-text-editor-count">{{ characterCount }} / {{ maxLength }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, nextTick, onMounted, ref, watch} from 'vue'
import {
  Bold, Eraser, Italic, Link, List, ListOrdered, Quote,
} from 'lucide-vue-next'

type ToolbarActionId = 'bold' | 'italic' | 'unorderedList' | 'orderedList' | 'quote' | 'link' | 'clear'

type MarkdownBlock =
  | {type: 'paragraph' | 'quote'; text: string}
  | {type: 'list'; ordered: boolean; items: string[]}

const props = withDefaults(defineProps<{
  modelValue: string
  placeholder?: string
  id?: string
  name?: string
  rows?: number
  maxLength?: number
  required?: boolean
  disabled?: boolean
  ariaLabel?: string
  toolbar?: boolean
}>(), {
  placeholder: '',
  id: undefined,
  name: undefined,
  rows: 4,
  maxLength: undefined,
  required: false,
  disabled: false,
  ariaLabel: undefined,
  toolbar: true,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  input: [value: string]
  focus: [event: FocusEvent]
  blur: [event: FocusEvent]
}>()

const editorRef = ref<HTMLDivElement | null>(null)
const characterCount = computed(() => props.modelValue.length)
const hasContent = computed(() => props.modelValue.trim().length > 0)
const editorStyle = computed(() => ({
  minHeight: `${Math.max(props.rows, 4) * 28}px`,
}))

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
  if (!editor || editorToMarkdown(editor) === value) return
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

async function updateEditorValue(value: string) {
  setEditorHtml(markdownToEditorHtml(value))
  emit('update:modelValue', value)
  emit('input', value)
  await nextTick()
  editorRef.value?.focus()
}

function emitEditorValue() {
  const editor = editorRef.value
  if (!editor) return

  let value = editorToMarkdown(editor)
  if (props.maxLength != null && value.length > props.maxLength) {
    value = value.slice(0, props.maxLength)
    setEditorHtml(markdownToEditorHtml(value))
  }

  emit('update:modelValue', value)
  emit('input', value)
}

function syncEditorFromValue() {
  setEditorHtml(markdownToEditorHtml(props.modelValue))
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

.base-text-editor-tool {
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

.base-text-editor-footer {
  display: flex;
  justify-content: flex-end;
  padding: 0 12px 10px;
}

.base-text-editor-count {
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: var(--color-muted);
  font-variant-numeric: tabular-nums;
}
</style>
