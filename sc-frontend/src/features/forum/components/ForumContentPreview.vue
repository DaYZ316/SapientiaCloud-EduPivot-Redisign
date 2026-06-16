<template>
  <div :class="{compact}" class="forum-content-preview">
    <template v-if="blocks.length > 0">
      <component
          :is="block.tag"
          v-for="(block, index) in blocks"
          :key="`${block.tag}-${index}`"
          class="forum-content-block"
      >
        <template v-if="block.type === 'list'">
          <li v-for="(item, itemIndex) in block.items" :key="itemIndex">
            <component
                :is="part.tag"
                v-for="(part, partIndex) in item"
                :key="partIndex"
                v-bind="part.attrs"
            >
              {{ part.text }}
            </component>
          </li>
        </template>
        <template v-else>
          <component
              :is="part.tag"
              v-for="(part, partIndex) in block.parts"
              :key="partIndex"
              v-bind="part.attrs"
          >
            {{ part.text }}
          </component>
        </template>
      </component>
    </template>

    <div v-if="images.length > 0" class="forum-content-images">
      <a
          v-for="(image, index) in images"
          :key="`${image}-${index}`"
          :href="image"
          class="forum-content-image"
          rel="noreferrer"
          target="_blank"
      >
        <img :alt="imageAlt(index)" :src="image" loading="lazy"/>
      </a>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {computed} from 'vue'

type InlineTag = 'span' | 'strong' | 'em' | 'a'

interface InlinePart {
  tag: InlineTag
  text: string
  attrs?: Record<string, string>
}

interface TextBlock {
  type: 'text'
  tag: 'p' | 'blockquote'
  parts: InlinePart[]
}

interface ListBlock {
  type: 'list'
  tag: 'ul' | 'ol'
  items: InlinePart[][]
}

const props = withDefaults(defineProps<{
  content: string
  imageUrls?: string[] | null
  compact?: boolean
}>(), {
  imageUrls: null,
  compact: false,
})

const blocks = computed<Array<TextBlock | ListBlock>>(() => parseBlocks(stripImageMarkdown(props.content)))
const images = computed(() => {
  const fromContent = extractImageUrls(props.content)
  const fromField = props.imageUrls || []
  return Array.from(new Set([...fromField, ...fromContent].filter(Boolean)))
})

function parseBlocks(content: string): Array<TextBlock | ListBlock> {
  const lines = content.replace(/\r\n/g, '\n').split('\n')
  const result: Array<TextBlock | ListBlock> = []
  let paragraph: string[] = []
  let listBlock: ListBlock | null = null

  function flushParagraph() {
    if (paragraph.length === 0) return
    result.push({
      type: 'text',
      tag: 'p',
      parts: parseInline(paragraph.join('\n')),
    })
    paragraph = []
  }

  function flushList() {
    if (!listBlock) return
    result.push(listBlock)
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
      const tag = unorderedMatch ? 'ul' : 'ol'
      const text = unorderedMatch?.[1] ?? orderedMatch?.[1] ?? ''
      if (!listBlock || listBlock.tag !== tag) {
        flushList()
        listBlock = {type: 'list', tag, items: []}
      }
      listBlock.items.push(parseInline(text))
      continue
    }

    flushList()

    if (quoteMatch) {
      flushParagraph()
      result.push({
        type: 'text',
        tag: 'blockquote',
        parts: parseInline(quoteMatch[1]),
      })
      continue
    }

    paragraph.push(line)
  }

  flushParagraph()
  flushList()
  return result
}

function parseInline(text: string): InlinePart[] {
  const parts: InlinePart[] = []
  const pattern = /(\*\*[^*]+\*\*|_[^_]+_|\[[^\]]+\]\([^)]+\))/g
  let lastIndex = 0
  let match: RegExpExecArray | null

  while ((match = pattern.exec(text)) !== null) {
    if (match.index > lastIndex) {
      parts.push({tag: 'span', text: text.slice(lastIndex, match.index)})
    }
    parts.push(formatInline(match[0]))
    lastIndex = match.index + match[0].length
  }

  if (lastIndex < text.length) {
    parts.push({tag: 'span', text: text.slice(lastIndex)})
  }

  return parts.length > 0 ? parts : [{tag: 'span', text}]
}

function formatInline(token: string): InlinePart {
  if (token.startsWith('**') && token.endsWith('**')) {
    return {tag: 'strong', text: token.slice(2, -2)}
  }
  if (token.startsWith('_') && token.endsWith('_')) {
    return {tag: 'em', text: token.slice(1, -1)}
  }

  const linkMatch = token.match(/^\[([^\]]+)\]\(([^)]+)\)$/)
  if (linkMatch) {
    const href = normalizeLink(linkMatch[2])
    return {
      tag: 'a',
      text: linkMatch[1],
      attrs: href ? {href, target: '_blank', rel: 'noreferrer'} : undefined,
    }
  }

  return {tag: 'span', text: token}
}

function stripImageMarkdown(content: string) {
  return content.replace(/!\[[^\]]*\]\([^)]+\)/g, '').trim()
}

function extractImageUrls(content: string) {
  const urls: string[] = []
  const pattern = /!\[[^\]]*\]\(([^)]+)\)/g
  let match: RegExpExecArray | null
  while ((match = pattern.exec(content)) !== null) {
    urls.push(match[1])
  }
  return urls
}

function normalizeLink(value: string) {
  const trimmed = value.trim()
  if (/^(https?:\/\/|mailto:)/i.test(trimmed)) return trimmed
  return ''
}

function imageAlt(index: number) {
  return `Comment image ${index + 1}`
}
</script>

<style scoped>
.forum-content-preview {
  display: grid;
  gap: 10px;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.7;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.forum-content-preview.compact {
  font-size: 13px;
  line-height: 1.6;
}

.forum-content-block {
  margin: 0;
}

ul.forum-content-block,
ol.forum-content-block {
  padding-left: 20px;
}

blockquote.forum-content-block {
  padding-left: 12px;
  border-left: 2px solid var(--color-outline-light);
  color: var(--color-muted);
}

.forum-content-preview :deep(a) {
  color: var(--color-primary);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.forum-content-preview :deep(em) {
  font-style: italic;
}

.forum-content-images {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(132px, 220px));
  gap: 10px;
}

.forum-content-image {
  display: block;
  overflow: hidden;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  background: var(--color-surface-container);
}

.forum-content-image img {
  width: 100%;
  aspect-ratio: 4 / 3;
  display: block;
  object-fit: cover;
}
</style>
