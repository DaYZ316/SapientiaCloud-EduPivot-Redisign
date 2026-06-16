<template>
  <div :class="`variant-${variant}`" class="notification-content-preview">
    <template v-if="blocks.length > 0">
      <component
          :is="block.tag"
          v-for="(block, index) in blocks"
          :key="`${block.tag}-${index}`"
          class="notification-content-preview-block"
      >
        <template v-if="block.type === 'list'">
          <li v-for="(item, itemIndex) in block.items" :key="itemIndex">
            <component :is="part.tag" v-for="(part, partIndex) in item" :key="partIndex">
              {{ part.text }}
            </component>
          </li>
        </template>
        <template v-else>
          <component :is="part.tag" v-for="(part, partIndex) in block.parts" :key="partIndex">
            {{ part.text }}
          </component>
        </template>
      </component>
    </template>
    <p v-else class="notification-content-preview-empty">{{ emptyText }}</p>
  </div>
</template>

<script lang="ts" setup>
import {computed} from 'vue'

type InlineTag = 'span' | 'strong' | 'em'

interface InlinePart {
  tag: InlineTag
  text: string
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
  emptyText?: string
  variant?: 'panel' | 'plain'
}>(), {
  emptyText: 'Preview will appear here',
  variant: 'panel',
})

const blocks = computed<Array<TextBlock | ListBlock>>(() => parseBlocks(props.content))

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
    return {tag: 'span', text: `${linkMatch[1]} (${linkMatch[2]})`}
  }

  return {tag: 'span', text: token}
}
</script>

<style scoped>
.notification-content-preview {
  color: var(--color-on-surface);
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  line-height: 1.65;
  white-space: pre-wrap;
}

.notification-content-preview.variant-panel {
  min-height: 112px;
  padding: 14px 16px;
  border: 1px solid var(--color-outline-variant);
  border-radius: var(--radius-sm);
  background: var(--color-surface-container);
}

.notification-content-preview-block {
  margin: 0 0 10px;
}

.notification-content-preview-block:last-child {
  margin-bottom: 0;
}

ul.notification-content-preview-block,
ol.notification-content-preview-block {
  padding-left: 20px;
}

blockquote.notification-content-preview-block {
  padding-left: 12px;
  border-left: 2px solid var(--color-outline);
  color: var(--color-secondary);
}

.notification-content-preview :deep(em) {
  font-style: italic;
}

.notification-content-preview-empty {
  margin: 0;
  color: var(--color-muted);
}
</style>
