<template>
  <aside
    :class="{'is-embedded': embedded}"
    class="generation-trace-panel"
  >
    <header
      v-if="!embedded"
      class="trace-header"
    >
      <div>
        <span>{{ kindLabel }}</span>
        <h2>{{ panelTitle }}</h2>
      </div>
      <button
        class="trace-close"
        title="关闭"
        type="button"
        @click="$emit('close')"
      >
        <X
          :size="16"
          stroke-width="1.9"
        />
      </button>
    </header>

    <div
      ref="bodyRef"
      class="trace-body"
    >
      <div
        v-if="entries.length"
        class="trace-flow"
      >
        <article
          v-for="(entry, index) in entries"
          :key="entry.entryId || `${entry.stage}-${index}`"
          :class="{'is-last': index === entries.length - 1}"
          class="trace-step"
        >
          <div
            aria-hidden="true"
            class="trace-rail"
          >
            <span />
          </div>

          <div class="trace-content">
            <span class="trace-stage">{{ generationStageLabel(entry.stage) }}</span>
            <h3>{{ entry.title || generationStageLabel(entry.stage) }}</h3>
            <AiMarkdownMessage
              v-if="entry.summary"
              :content="entry.summary"
              class="trace-markdown trace-summary"
            />

            <div
              v-if="webSources(entry).length"
              class="trace-web-sources"
            >
              <a
                v-for="(source, sourceIndex) in webSources(entry)"
                :key="source.url || `${source.site}-${source.title}-${sourceIndex}`"
                :href="source.url"
                :title="source.title"
                class="trace-web-source"
                rel="noopener noreferrer"
                target="_blank"
              >
                <img
                  v-if="source.favicon"
                  :src="source.favicon"
                  alt=""
                  class="trace-web-source__icon"
                  loading="lazy"
                >
                <span
                  v-else
                  aria-hidden="true"
                  class="trace-web-source__icon trace-web-source__icon--fallback"
                >
                  {{ sourceFallback(source) }}
                </span>
                <strong>{{ source.site || source.url }}</strong>
                <span>{{ source.title || source.url }}</span>
              </a>
            </div>

            <div
              v-if="buildEntryBlocks(entry).length"
              class="trace-blocks"
            >
              <AiMarkdownMessage
                v-for="(block, blockIndex) in buildEntryBlocks(entry)"
                :key="blockIndex"
                :content="markdownBlock(block)"
                class="trace-block"
              />
            </div>

            <div
              v-if="shouldShowSkeleton(index)"
              class="trace-loading"
              aria-hidden="true"
            >
              <span class="line line-short" />
              <span class="line" />
              <span class="line line-medium" />
            </div>
          </div>
        </article>
      </div>

      <div
        v-else
        class="trace-empty"
      >
        <FileClock
          :size="28"
          stroke-width="1.6"
        />
        <h3>等待详细输出</h3>
        <p>出题步骤开始后，会在这里显示 AI 的实时过程。</p>
      </div>

      <details
        v-if="debugEntries.length"
        class="trace-debug"
        open
      >
        <summary>技术明细 {{ debugEntries.length }} 条</summary>
        <div class="trace-debug-list">
          <article
            v-for="(entry, index) in debugEntries"
            :key="entry.entryId || `debug-${entry.stage}-${index}`"
            class="trace-debug-step"
          >
            <span>{{ generationStageLabel(entry.stage) }}</span>
            <h4>{{ entry.title || generationStageLabel(entry.stage) }}</h4>
            <AiMarkdownMessage
              v-if="entry.summary"
              :content="entry.summary"
              class="trace-block"
            />
            <div
              v-if="rawAiOutput(entry)"
              class="trace-raw-output"
            >
              <span>{{ rawOutputLabel(entry) }}</span>
              <pre>{{ rawAiOutput(entry) }}</pre>
            </div>
            <AiMarkdownMessage
              v-for="(block, blockIndex) in buildEntryBlocks(entry)"
              :key="blockIndex"
              :content="markdownBlock(block)"
              class="trace-block"
            />
          </article>
        </div>
      </details>
    </div>
  </aside>
</template>

<script lang="ts" setup>
import {computed, nextTick, onMounted, onUnmounted, ref, watch} from 'vue'
import {FileClock, X} from 'lucide-vue-next'

import AiMarkdownMessage from '@/features/ai/components/AiMarkdownMessage.vue'
import type {ChatMessage, GenerationTraceEntry} from '@/features/ai/types/ai'
import {
  currentGenerationStage,
  generationDebugTrace,
  generationStageLabel,
  generationTrace,
} from '@/features/ai/utils/generationTrace'

interface WebSource {
  site: string
  title: string
  url: string
  favicon: string
}

const props = withDefaults(defineProps<{
  message: ChatMessage | null
  embedded?: boolean
}>(), {
  embedded: false,
})

defineEmits<{
  close: []
}>()

const bodyRef = ref<HTMLElement | null>(null)
const autoFollow = ref(true)
let scrollFrame = 0
let programmaticScroll = false
let scrollElement: HTMLElement | null = null

const entries = computed(() => generationTrace(props.message))
const debugEntries = computed(() => generationDebugTrace(props.message))
const stage = computed(() => currentGenerationStage(props.message))
const kindLabel = computed(() => props.message?.messageType === 'PAPER' ? 'AI 出卷详细输出' : 'AI 出题详细输出')
const panelTitle = computed(() => {
  const latest = entries.value.at(-1)
  if (latest?.title) return latest.title
  if (props.message?.messageType === 'PAPER') return '试卷生成过程'
  return '题目生成过程'
})
const isStreaming = computed(() =>
  props.message?.pending && stage.value !== 'RESPONDED' && stage.value !== 'FAILED' && stage.value !== 'TERMINATED',
)

watch(() => [entries.value.length, entries.value.at(-1)?.timestamp], async () => {
  await nextTick()
  if (autoFollow.value || isNearBottom()) {
    scrollToBottom('smooth')
  }
})

onMounted(async () => {
  await nextTick()
  scrollElement = resolveScrollTarget()
  scrollElement?.addEventListener('scroll', handleScroll, {passive: true})
  scrollToBottom()
})

onUnmounted(() => {
  if (scrollFrame) cancelAnimationFrame(scrollFrame)
  scrollElement?.removeEventListener('scroll', handleScroll)
})

function shouldShowSkeleton(index: number) {
  return isStreaming.value && index === entries.value.length - 1
}

function handleScroll() {
  if (programmaticScroll) return
  autoFollow.value = isNearBottom()
}

function isNearBottom() {
  const target = currentScrollTarget()
  if (!target) return true
  return target.scrollHeight - target.scrollTop - target.clientHeight < 56
}

function scrollToBottom(behavior: ScrollBehavior = 'auto') {
  if (scrollFrame) cancelAnimationFrame(scrollFrame)
  scrollFrame = requestAnimationFrame(() => {
    scrollFrame = 0
    const target = currentScrollTarget()
    if (!target) return

    programmaticScroll = true
    target.scrollTo({top: target.scrollHeight, behavior})
    window.setTimeout(() => {
      if (autoFollow.value) {
        target.scrollTo({top: target.scrollHeight, behavior: 'auto'})
      }
      programmaticScroll = false
    }, behavior === 'smooth' ? 280 : 0)
  })
}

function currentScrollTarget() {
  return scrollElement || resolveScrollTarget()
}

function resolveScrollTarget() {
  const body = bodyRef.value
  if (!body || !props.embedded) return body
  return closestScrollableParent(body) || body
}

function closestScrollableParent(element: HTMLElement) {
  let parent = element.parentElement
  while (parent) {
    const style = window.getComputedStyle(parent)
    if (/(auto|scroll)/.test(`${style.overflowY}${style.overflow}`)) {
      return parent
    }
    parent = parent.parentElement
  }
  return null
}

function buildEntryBlocks(entry: GenerationTraceEntry) {
  const payload = asRecord(entry.payload)
  if (!payload) return []
  if (rawAiOutput(entry)) return []
  const hasWebSources = webSources(entry).length > 0
  const showQuestionDetails = isTechnicalQuestionEntry(entry)

  return [
    hasWebSources ? '' : buildMetricLine(payload),
    ...(hasWebSources ? [] : listRecords(payload.evidences).slice(0, 4).map(buildEvidenceLine)),
    ...listRecords(payload.sections).slice(0, 6).map(buildSectionLine),
    ...listRecords(payload.questions).slice(0, 8).map((question, index) =>
      buildQuestionLine(question, index, showQuestionDetails),
    ),
    ...listRecords(payload.issues).slice(0, 6).map(buildIssueLine),
    textValue(payload.error),
  ].filter(Boolean)
}

function webSources(entry: GenerationTraceEntry): WebSource[] {
  const payload = asRecord(entry.payload)
  return listRecords(payload?.webSources)
    .map(source => ({
      site: textValue(source.site),
      title: textValue(source.title),
      url: textValue(source.url),
      favicon: textValue(source.favicon),
    }))
    .filter(source => Boolean(source.url))
}

function sourceFallback(source: WebSource) {
  return (source.site || source.title || source.url).trim().slice(0, 1).toUpperCase()
}

function rawAiOutput(entry: GenerationTraceEntry) {
  const payload = asRecord(entry.payload)
  return textValue(payload?.rawOutput)
}

function rawOutputLabel(entry: GenerationTraceEntry) {
  const payload = asRecord(entry.payload)
  const parts = [
    textValue(payload?.callType) || 'model_output',
    metricValue(payload, 'sectionNo', 'section'),
    metricValue(payload, 'attemptNo', 'attempt'),
  ].filter(Boolean)
  return parts.join(' / ')
}

function buildMetricLine(payload: Record<string, unknown>) {
  const metrics = [
    metric(payload, 'query', '检索'),
    metric(payload, 'evidenceCount', '资料'),
    metric(payload, 'sectionCount', '分区'),
    metric(payload, 'questionCount', '题目'),
    metric(payload, 'totalQuestionCount', '题目'),
    metric(payload, 'targetCount', '目标题数'),
    metric(payload, 'totalScore', '总分'),
    metric(payload, 'totalEstimatedTime', '预计用时'),
    metric(payload, 'attemptNo', '修正轮次'),
    metric(payload, 'issueCount', '问题'),
    metric(payload, 'remainingIssueCount', '剩余问题'),
    metric(payload, 'generationStrategy', '策略'),
  ].filter(Boolean)

  return metrics.join(' · ')
}

function buildEvidenceLine(item: Record<string, unknown>) {
  const title = textValue(item.title) || textValue(item.sourceLabel) || '参考资料'
  const snippet = textValue(item.excerpt) || textValue(item.snippet)
  return [title, snippet].filter(Boolean).join('\n')
}

function buildSectionLine(item: Record<string, unknown>, index: number) {
  const title = textValue(item.sectionTitle) || `分区 ${textValue(item.sectionNo) || index + 1}`
  const meta = [
    metric(item, 'targetCount', '题数'),
    metric(item, 'questionType', '题型'),
    metric(item, 'difficulty', '难度'),
    metric(item, 'sectionTotalEstimatedTime', '预计用时'),
  ].filter(Boolean).join(' · ')
  const knowledgePoints = listValues(item.knowledgePoints)
  return [title, meta, knowledgePoints.length ? `知识点: ${knowledgePoints.join('、')}` : ''].filter(Boolean).join('\n')
}

function buildQuestionLine(item: Record<string, unknown>, index: number, showDetails: boolean) {
  const title = textValue(item.questionTitle) || `题目 ${index + 1}`
  const meta = [
    metric(item, 'questionType', '题型'),
    metric(item, 'difficulty', '难度'),
    metric(item, 'score', '分值'),
    metric(item, 'estimatedTime', '预计用时'),
  ].filter(Boolean).join(' · ')
  const content = textValue(item.questionContent)
  if (!showDetails) {
    return [title, meta, content].filter(Boolean).join('\n')
  }
  const options = listRecords(item.options).map(buildOptionLine)
  const answers = listRecords(item.answers).map(buildAnswerLine)
  return [
    title,
    meta,
    content,
    options.length ? `选项:\n${options.join('\n')}` : '',
    answers.length ? `答案:\n${answers.join('\n')}` : '',
  ].filter(Boolean).join('\n')
}

function isTechnicalQuestionEntry(entry: GenerationTraceEntry) {
  return ['section_attempt', 'repair_attempt', 'repair_summary', 'quality_review'].includes(entry.detailType || '')
}

function buildOptionLine(item: Record<string, unknown>, index: number) {
  const label = textValue(item.optionLabel) || String.fromCharCode(65 + index)
  const correctness = Number(item.isCorrect) === 1 ? '正确' : '错误'
  const score = metric(item, 'score', '分值')
  const explanation = textValue(item.explanation)
  return [
    `${label}. ${textValue(item.optionContent)}（${correctness}${score ? ` · ${score}` : ''}）`,
    explanation ? `解析: ${explanation}` : '',
  ].filter(Boolean).join('\n')
}

function buildAnswerLine(item: Record<string, unknown>) {
  const score = metric(item, 'score', '分值')
  const sortOrder = metric(item, 'sortOrder', '顺序')
  const explanation = textValue(item.explanation)
  return [
    [textValue(item.answerContent), score, sortOrder].filter(Boolean).join(' · '),
    explanation ? `解析: ${explanation}` : '',
  ].filter(Boolean).join('\n')
}

function buildIssueLine(item: Record<string, unknown>, index: number) {
  const questionIndex = item.questionIndex === undefined ? index + 1 : Number(item.questionIndex) + 1
  const message = textValue(item.message) || textValue(item.code) || '需要检查'
  const repairHint = textValue(item.repairHint)
  return [`题目 ${questionIndex}: ${message}`, repairHint].filter(Boolean).join('\n')
}

function markdownBlock(block: string) {
  return block.split('\n').filter(Boolean).join('\n\n')
}

function metric(payload: Record<string, unknown>, key: string, label: string) {
  const value = payload[key]
  if (value === null || value === undefined || value === '') return ''
  return `${label}: ${formatValue(value)}`
}

function metricValue(payload: Record<string, unknown> | null, key: string, label: string) {
  const value = payload?.[key]
  if (value === null || value === undefined || value === '') return ''
  return `${label} ${formatValue(value)}`
}

function formatValue(value: unknown): string {
  if (Array.isArray(value)) return value.map(formatValue).join('、')
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function listRecords(value: unknown): Record<string, unknown>[] {
  if (!Array.isArray(value)) return []
  return value.filter((item): item is Record<string, unknown> =>
    Boolean(item) && typeof item === 'object' && !Array.isArray(item),
  )
}

function listValues(value: unknown) {
  return Array.isArray(value)
    ? value.map(item => formatValue(item)).filter(Boolean)
    : []
}

function textValue(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

function asRecord(value: unknown): Record<string, unknown> | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null
  return value as Record<string, unknown>
}
</script>

<style scoped>
.generation-trace-panel {
  display: flex;
  min-width: 0;
  min-height: 0;
  width: 100%;
  height: 100%;
  flex-direction: column;
  overflow: hidden;
  background: var(--color-surface-card);
  border-left: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
}

.generation-trace-panel.is-embedded {
  height: auto;
  border-left: 0;
  background: transparent;
  overflow: visible;
}

.trace-header {
  display: flex;
  min-height: 48px;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 0 0 0 28px;
  border-bottom: 1px solid var(--color-outline-light);
}

.trace-header span,
.trace-stage {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
  letter-spacing: 0.08em;
  line-height: 1.35;
  text-transform: uppercase;
}

.trace-header h2 {
  min-width: 0;
  margin: 2px 0 0;
  overflow: hidden;
  font-family: var(--font-heading);
  font-size: 23px;
  font-weight: 650;
  line-height: 1.12;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-close {
  display: grid;
  width: 48px;
  height: 48px;
  flex: 0 0 auto;
  align-self: stretch;
  place-items: center;
  background: transparent;
  border: 0;
  border-radius: 0;
  color: var(--color-on-surface);
  cursor: pointer;
  transition: background 0.18s ease, color 0.18s ease;
}

.trace-close:hover {
  background: var(--color-on-surface);
  color: var(--color-surface-card);
}

.trace-body {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
  padding: 28px;
}

.generation-trace-panel.is-embedded .trace-body {
  overflow: visible;
  padding: 0;
}

.trace-flow {
  display: grid;
  gap: 22px;
}

.trace-step {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr);
  gap: 14px;
}

.trace-rail {
  position: relative;
  display: flex;
  justify-content: center;
}

.trace-rail::after {
  position: absolute;
  top: 18px;
  bottom: -22px;
  width: 1px;
  background: var(--color-outline-light);
  content: '';
}

.trace-step.is-last .trace-rail::after {
  display: none;
}

.trace-rail span {
  width: 11px;
  height: 11px;
  margin-top: 6px;
  background: var(--color-primary);
  border-radius: 999px;
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--color-primary) 14%, transparent);
}

.trace-content {
  display: grid;
  gap: 8px;
  min-width: 0;
  padding-bottom: 2px;
}

.trace-content h3 {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 23px;
  font-weight: 650;
  line-height: 1.2;
}

.trace-markdown,
.trace-block {
  max-width: none;
  margin: 0;
  color: var(--color-on-surface-variant);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.72;
  overflow-wrap: anywhere;
}

.trace-markdown {
  color: var(--color-on-surface);
}

.trace-markdown :deep(.ai-markdown-message),
.trace-block :deep(.ai-markdown-message) {
  max-width: none;
  margin: 0;
  font-size: inherit;
  line-height: inherit;
}

.trace-markdown :deep(p:last-child),
.trace-block :deep(p:last-child) {
  margin-bottom: 0;
}

.trace-blocks {
  display: grid;
  gap: 10px;
  padding-top: 4px;
}

.trace-web-sources {
  display: grid;
  gap: 4px;
  padding-top: 2px;
}

.trace-web-source {
  display: grid;
  min-width: 0;
  min-height: 36px;
  grid-template-columns: 20px max-content minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 7px;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.35;
  text-decoration: none;
  transition: background 0.18s ease, color 0.18s ease;
}

.trace-web-source:hover {
  background: var(--color-surface-container-high);
}

.trace-web-source:focus-visible {
  outline: 2px solid color-mix(in srgb, var(--color-primary) 54%, transparent);
  outline-offset: 2px;
}

.trace-web-source__icon {
  width: 18px;
  height: 18px;
  border-radius: 5px;
  object-fit: cover;
}

.trace-web-source__icon--fallback {
  display: grid;
  place-items: center;
  background: var(--color-surface-container-high);
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
  font-weight: 650;
}

.trace-web-source strong,
.trace-web-source span:last-child {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-web-source strong {
  font-weight: 650;
}

.trace-web-source span:last-child {
  color: var(--color-on-surface-variant);
}

.trace-block {
  padding: 10px 0;
  border-top: 1px solid var(--color-outline-light);
}

.trace-debug {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--color-outline-light);
}

.trace-debug summary {
  color: var(--color-muted);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 12px;
  line-height: 1.4;
}

.trace-debug-list {
  display: grid;
  gap: 14px;
  padding-top: 14px;
}

.trace-debug-step {
  display: grid;
  gap: 6px;
}

.trace-debug-step span {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
  line-height: 1.35;
}

.trace-debug-step h4 {
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 17px;
  line-height: 1.25;
}

.trace-raw-output {
  display: grid;
  gap: 6px;
  min-width: 0;
  padding: 10px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
}

.trace-raw-output span {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
  line-height: 1.35;
}

.trace-raw-output pre {
  max-height: 420px;
  margin: 0;
  overflow: auto;
  color: var(--color-on-surface);
  font-family: ui-monospace, SFMono-Regular, Consolas, 'Liberation Mono', monospace;
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.trace-loading {
  display: grid;
  gap: 10px;
  padding-top: 6px;
}

.line {
  display: block;
  width: 100%;
  height: 10px;
  background: linear-gradient(
    110deg,
    var(--color-surface-container-high) 8%,
    color-mix(in srgb, var(--color-primary) 12%, var(--color-surface-canvas)) 18%,
    var(--color-surface-container-high) 33%
  );
  background-size: 200% 100%;
  border-radius: 999px;
  animation: shimmer 1.55s ease-in-out infinite;
}

.line-short {
  width: 48%;
}

.line-medium {
  width: 68%;
}

.trace-empty {
  display: grid;
  height: 100%;
  place-items: center;
  align-content: center;
  gap: 12px;
  color: var(--color-muted);
  text-align: center;
}

.generation-trace-panel.is-embedded .trace-empty {
  min-height: 320px;
}

.trace-empty h3 {
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 30px;
  line-height: 1.1;
}

.trace-empty p {
  max-width: 28ch;
  margin: 0;
  font-size: 14px;
  line-height: 1.55;
}

@keyframes shimmer {
  to {
    background-position-x: -200%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .line {
    animation: none;
  }
}

@media (max-width: 760px) {
  .trace-body {
    padding: 22px;
  }

  .trace-content h3 {
    font-size: 20px;
  }

  .trace-web-source {
    grid-template-columns: 20px minmax(0, 1fr);
  }

  .trace-web-source span:last-child {
    grid-column: 2;
  }
}
</style>
