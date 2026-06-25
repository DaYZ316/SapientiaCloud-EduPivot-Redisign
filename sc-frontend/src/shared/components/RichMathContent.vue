<template>
  <div
    ref="contentRef"
    class="rich-math-content"
    v-html="renderedContent"
  />
</template>

<script lang="ts" setup>
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import 'katex/dist/katex.min.css'

import {
  fitRichMathToContainer,
  formatRichFormulaRows,
  normalizeRichDisplayMath,
  renderRichMath,
  renderRichMathMarkdown,
} from '@/shared/utils/richMathRenderer'

const props = defineProps<{
  content: string
}>()

const renderedContent = computed(() => renderRichMathMarkdown(props.content))
const contentRef = ref<HTMLElement | null>(null)
let resizeObserver: ResizeObserver | null = null
let resizeFrame = 0

onMounted(() => {
  renderMathAfterHtmlUpdate()
  if (!contentRef.value || typeof ResizeObserver === 'undefined') {
    return
  }

  resizeObserver = new ResizeObserver(queueFitMathToContainer)
  resizeObserver.observe(contentRef.value)
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  if (resizeFrame) {
    cancelAnimationFrame(resizeFrame)
  }
})

watch(renderedContent, renderMathAfterHtmlUpdate)

async function renderMathAfterHtmlUpdate() {
  await nextTick()
  if (contentRef.value) {
    normalizeRichDisplayMath(contentRef.value)
    renderRichMath(contentRef.value)
    formatRichFormulaRows(contentRef.value)
    queueFitMathToContainer()
  }
}

function queueFitMathToContainer() {
  if (resizeFrame) {
    cancelAnimationFrame(resizeFrame)
  }
  resizeFrame = requestAnimationFrame(() => {
    resizeFrame = 0
    if (contentRef.value) {
      fitRichMathToContainer(contentRef.value)
    }
  })
}
</script>

<style scoped>
.rich-math-content {
  color: inherit;
  font-family: var(--font-body);
  font-size: 18px;
  line-height: 1.75;
  min-width: 0;
  max-width: 860px;
  margin: 0 auto;
  overflow-x: hidden;
  overflow-wrap: break-word;
  white-space: normal;
  word-wrap: break-word;
}

.rich-math-content :deep(h1),
.rich-math-content :deep(h2),
.rich-math-content :deep(h3),
.rich-math-content :deep(h4),
.rich-math-content :deep(h5),
.rich-math-content :deep(h6) {
  margin-top: 1.4em;
  margin-bottom: 0.6em;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-weight: 400;
  line-height: 1.3;
}

.rich-math-content :deep(h1) {
  padding-bottom: 0.3em;
  border-bottom: 2px solid var(--color-outline-light);
  font-size: 2em;
}

.rich-math-content :deep(h2) {
  padding-bottom: 0.25em;
  border-bottom: 1px solid var(--color-outline-light);
  font-size: 1.6em;
}

.rich-math-content :deep(h3) {
  font-size: 1.3em;
}

.rich-math-content :deep(h4) {
  font-size: 1.15em;
}

.rich-math-content :deep(h5) {
  font-size: 1em;
}

.rich-math-content :deep(h6) {
  color: var(--color-muted);
  font-size: 0.9em;
}

.rich-math-content :deep(h1:first-child) {
  margin-top: 0;
}

.rich-math-content :deep(p) {
  margin: 0 0 1em;
}

.rich-math-content :deep(a) {
  border-bottom: 1px solid transparent;
  color: var(--color-primary);
  text-decoration: none;
  transition: border-color 0.2s;
}

.rich-math-content :deep(a:hover) {
  border-bottom-color: var(--color-primary);
}

.rich-math-content :deep(strong) {
  color: var(--color-on-surface);
  font-weight: 700;
}

.rich-math-content :deep(em) {
  font-style: italic;
}

.rich-math-content :deep(blockquote) {
  margin: 1em 0;
  padding: 0.5em 1.2em;
  background: transparent;
  border-left: 4px solid var(--color-outline);
  color: var(--color-muted);
}

.rich-math-content :deep(blockquote p:last-child) {
  margin-bottom: 0;
}

.rich-math-content :deep(hr) {
  margin: 2em 0;
  border: none;
  border-top: 1px solid var(--color-outline-light);
}

.rich-math-content :deep(ul),
.rich-math-content :deep(ol) {
  margin: 0 0 1em;
  padding-left: 2em;
}

.rich-math-content :deep(li) {
  margin-bottom: 0.3em;
}

.rich-math-content :deep(li > ul),
.rich-math-content :deep(li > ol) {
  margin-top: 0.3em;
  margin-bottom: 0;
}

.rich-math-content :deep(code) {
  padding: 0.15em 0.4em;
  background: var(--color-surface-container);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  font-family: 'Cascadia Code', 'Fira Code', 'JetBrains Mono', Consolas, monospace;
  font-size: 0.88em;
}

.rich-math-content :deep(pre) {
  margin: 1em 0;
  padding: 1em 1.2em;
  overflow-x: auto;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  line-height: 1.6;
}

.rich-math-content :deep(pre code) {
  padding: 0;
  background: transparent;
  border-radius: 0;
  color: var(--color-on-surface);
  font-size: 0.88em;
}

.rich-math-content :deep(table) {
  width: 100%;
  margin: 1em 0;
  border-collapse: collapse;
  border-spacing: 0;
}

.rich-math-content :deep(th),
.rich-math-content :deep(td) {
  padding: 0.5em 0.8em;
  border: 1px solid var(--color-outline-light);
  text-align: left;
}

.rich-math-content :deep(th) {
  background: var(--color-surface-container);
  font-family: var(--font-label);
  font-size: 0.92em;
  font-weight: 600;
}

.rich-math-content :deep(tr:nth-child(even)) {
  background: var(--color-surface-container);
}

.rich-math-content :deep(img) {
  max-width: 100%;
  height: auto;
  margin: 0.5em 0;
  border-radius: var(--radius-sm);
}

.rich-math-content :deep(input[type='checkbox']) {
  margin-right: 0.4em;
}

.rich-math-content :deep(li.task-list-item) {
  margin-left: -1.5em;
  list-style: none;
}

.rich-math-content :deep(.katex-display) {
  max-width: 100%;
  overflow-x: hidden;
  overflow-y: hidden;
  padding: 0.25em 0;
}

.rich-math-content :deep(.katex) {
  max-width: 100%;
  font-size: 1.05em;
}

.rich-math-content :deep(.rich-math-scaled) {
  display: block;
}

.rich-math-content :deep(.rich-math-scaled > .katex),
.rich-math-content :deep(.rich-math-scaled .katex-display > .katex),
.rich-math-content :deep(.rich-math-scaled > span > .katex) {
  display: inline-block;
  max-width: none;
  transform: scale(var(--rich-math-scale));
  transform-origin: left center;
}

.rich-math-content :deep(.katex-display.rich-math-scaled) {
  text-align: left;
}

.rich-math-content :deep(.formula-row-list) {
  display: grid;
  gap: 0.45em;
}

.rich-math-content :deep(.formula-row) {
  display: block;
  max-width: 100%;
  overflow-x: hidden;
  overflow-y: hidden;
  white-space: nowrap;
}

.rich-math-content :deep(.split-display-math) {
  display: grid;
  gap: 0.65em;
  margin: 0.75em 0;
}

.rich-math-content :deep(.split-display-math-row) {
  display: block;
  max-width: 100%;
  overflow-x: hidden;
  overflow-y: hidden;
  padding: 0.08em 0;
}

.rich-math-content :deep(.split-display-math-row .katex-display) {
  margin: 0;
}
</style>
