<template>
  <div
    ref="messageRef"
    class="ai-markdown-message"
    v-html="renderedContent"
  />
</template>

<script lang="ts" setup>
import {computed, nextTick, onMounted, ref, watch} from 'vue'
import 'katex/dist/katex.min.css'

import {
  formatAiFormulaRows,
  normalizeAiDisplayMath,
  renderAiMarkdown,
  renderAiMath,
} from '@/features/ai/components/aiMarkdownRenderer'

const props = defineProps<{
  content: string
}>()

const renderedContent = computed(() => renderAiMarkdown(props.content))
const messageRef = ref<HTMLElement | null>(null)

onMounted(renderMathAfterHtmlUpdate)

watch(renderedContent, renderMathAfterHtmlUpdate)

async function renderMathAfterHtmlUpdate() {
  await nextTick()
  if (messageRef.value) {
    normalizeAiDisplayMath(messageRef.value)
    renderAiMath(messageRef.value)
    formatAiFormulaRows(messageRef.value)
  }
}
</script>

<style scoped>
.ai-markdown-message {
  color: inherit;
  font-family: var(--font-body);
  font-size: 18px;
  line-height: 1.75;
  max-width: 860px;
  margin: 0 auto;
  overflow-wrap: break-word;
  white-space: normal;
  word-wrap: break-word;
}

.ai-markdown-message :deep(h1),
.ai-markdown-message :deep(h2),
.ai-markdown-message :deep(h3),
.ai-markdown-message :deep(h4),
.ai-markdown-message :deep(h5),
.ai-markdown-message :deep(h6) {
  margin-top: 1.4em;
  margin-bottom: 0.6em;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-weight: 400;
  line-height: 1.3;
}

.ai-markdown-message :deep(h1) {
  padding-bottom: 0.3em;
  border-bottom: 2px solid var(--color-outline-light);
  font-size: 2em;
}

.ai-markdown-message :deep(h2) {
  padding-bottom: 0.25em;
  border-bottom: 1px solid var(--color-outline-light);
  font-size: 1.6em;
}

.ai-markdown-message :deep(h3) {
  font-size: 1.3em;
}

.ai-markdown-message :deep(h4) {
  font-size: 1.15em;
}

.ai-markdown-message :deep(h5) {
  font-size: 1em;
}

.ai-markdown-message :deep(h6) {
  color: var(--color-muted);
  font-size: 0.9em;
}

.ai-markdown-message :deep(h1:first-child) {
  margin-top: 0;
}

.ai-markdown-message :deep(p) {
  margin: 0 0 1em;
}

.ai-markdown-message :deep(a) {
  border-bottom: 1px solid transparent;
  color: var(--color-primary);
  text-decoration: none;
  transition: border-color 0.2s;
}

.ai-markdown-message :deep(a:hover) {
  border-bottom-color: var(--color-primary);
}

.ai-markdown-message :deep(strong) {
  color: var(--color-on-surface);
  font-weight: 700;
}

.ai-markdown-message :deep(em) {
  font-style: italic;
}

.ai-markdown-message :deep(blockquote) {
  margin: 1em 0;
  padding: 0.5em 1.2em;
  background: transparent;
  border-left: 4px solid var(--color-outline);
  color: var(--color-muted);
}

.ai-markdown-message :deep(blockquote p:last-child) {
  margin-bottom: 0;
}

.ai-markdown-message :deep(hr) {
  margin: 2em 0;
  border: none;
  border-top: 1px solid var(--color-outline-light);
}

.ai-markdown-message :deep(ul),
.ai-markdown-message :deep(ol) {
  margin: 0 0 1em;
  padding-left: 2em;
}

.ai-markdown-message :deep(li) {
  margin-bottom: 0.3em;
}

.ai-markdown-message :deep(li > ul),
.ai-markdown-message :deep(li > ol) {
  margin-top: 0.3em;
  margin-bottom: 0;
}

.ai-markdown-message :deep(code) {
  padding: 0.15em 0.4em;
  background: var(--color-surface-container);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  font-family: 'Cascadia Code', 'Fira Code', 'JetBrains Mono', Consolas, monospace;
  font-size: 0.88em;
}

.ai-markdown-message :deep(pre) {
  margin: 1em 0;
  padding: 1em 1.2em;
  overflow-x: auto;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  line-height: 1.6;
}

.ai-markdown-message :deep(pre code) {
  padding: 0;
  background: transparent;
  border-radius: 0;
  color: var(--color-on-surface);
  font-size: 0.88em;
}

.ai-markdown-message :deep(table) {
  width: 100%;
  margin: 1em 0;
  border-collapse: collapse;
  border-spacing: 0;
}

.ai-markdown-message :deep(th),
.ai-markdown-message :deep(td) {
  padding: 0.5em 0.8em;
  border: 1px solid var(--color-outline-light);
  text-align: left;
}

.ai-markdown-message :deep(th) {
  background: var(--color-surface-container);
  font-family: var(--font-label);
  font-size: 0.92em;
  font-weight: 600;
}

.ai-markdown-message :deep(tr:nth-child(even)) {
  background: var(--color-surface-container);
}

.ai-markdown-message :deep(img) {
  max-width: 100%;
  height: auto;
  margin: 0.5em 0;
  border-radius: var(--radius-sm);
}

.ai-markdown-message :deep(input[type='checkbox']) {
  margin-right: 0.4em;
}

.ai-markdown-message :deep(li.task-list-item) {
  margin-left: -1.5em;
  list-style: none;
}

.ai-markdown-message :deep(.katex-display) {
  overflow-x: auto;
  overflow-y: hidden;
  padding: 0.25em 0;
}

.ai-markdown-message :deep(.katex) {
  font-size: 1.05em;
}

.ai-markdown-message :deep(.formula-row-list) {
  display: grid;
  gap: 0.45em;
}

.ai-markdown-message :deep(.formula-row) {
  display: block;
  overflow-x: auto;
  overflow-y: hidden;
  white-space: nowrap;
}

.ai-markdown-message :deep(.split-display-math) {
  display: grid;
  gap: 0.65em;
  margin: 0.75em 0;
}

.ai-markdown-message :deep(.split-display-math-row) {
  display: block;
  overflow-x: auto;
  overflow-y: hidden;
  padding: 0.08em 0;
}

.ai-markdown-message :deep(.split-display-math-row .katex-display) {
  margin: 0;
}
</style>
