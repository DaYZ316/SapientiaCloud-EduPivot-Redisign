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
        :title="t('common.ai.generationTrace.close')"
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
        v-if="visibleEntries.length"
        class="trace-flow"
      >
        <article
          v-for="(entry, index) in visibleEntries"
          :key="entry.entryId || `${entry.stage}-${index}`"
          :class="{'is-last': index === visibleEntries.length - 1}"
          class="trace-step"
        >
          <div
            aria-hidden="true"
            class="trace-rail"
          >
            <span />
          </div>

          <div class="trace-content">
            <span class="trace-stage">{{ stageLabel(entry.stage) }}</span>
            <h3>{{ entryTitle(entry) }}</h3>
            <AiMarkdownMessage
              v-if="entry.summary"
              :content="entry.summary"
              class="trace-markdown trace-summary"
            />

            <div
              v-if="entryMetrics(entry).length"
              class="trace-metrics"
            >
              <span
                v-for="metric in entryMetrics(entry)"
                :key="metric.label"
                class="trace-metric"
              >
                <small>{{ metric.label }}</small>
                <strong>{{ metric.value }}</strong>
              </span>
            </div>

            <p
              v-if="strategyText(entry)"
              class="trace-note"
            >
              {{ strategyText(entry) }}
            </p>

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
              v-if="entrySections(entry).length"
              class="trace-section-grid"
            >
              <article
                v-for="section in entrySections(entry)"
                :key="section.key"
                class="trace-mini-card"
              >
                <h4>{{ section.title }}</h4>
                <div
                  v-if="section.metrics.length"
                  class="trace-chip-row"
                >
                  <span
                    v-for="metric in section.metrics"
                    :key="metric.label"
                    class="trace-chip"
                  >
                    {{ metric.label }}: {{ metric.value }}
                  </span>
                </div>
                <p v-if="section.knowledgePoints.length">
                  {{ t('common.ai.generationTrace.labels.knowledgePoints') }}: {{ formatList(section.knowledgePoints) }}
                </p>
              </article>
            </div>

            <div
              v-if="entryDistributions(entry).length"
              class="trace-distributions"
            >
              <article
                v-for="distribution in entryDistributions(entry)"
                :key="distribution.title"
                class="trace-mini-card"
              >
                <h4>{{ distribution.title }}</h4>
                <div class="trace-chip-row">
                  <span
                    v-for="item in distribution.items"
                    :key="item.label"
                    class="trace-chip"
                  >
                    {{ item.label }}: {{ item.value }}
                  </span>
                </div>
              </article>
            </div>

            <div
              v-if="knowledgeCoverage(entry)"
              class="trace-mini-card"
            >
              <h4>{{ t('common.ai.generationTrace.labels.topicCoverage') }}</h4>
              <p v-if="knowledgeCoverage(entry)?.requested.length">
                {{ t('common.ai.generationTrace.labels.requested') }}: {{ formatList(knowledgeCoverage(entry)?.requested || []) }}
              </p>
              <p v-if="knowledgeCoverage(entry)?.generated.length">
                {{ t('common.ai.generationTrace.labels.generated') }}: {{ formatList(knowledgeCoverage(entry)?.generated || []) }}
              </p>
            </div>

            <div
              v-if="entryIssues(entry).length"
              class="trace-issue-list"
            >
              <article
                v-for="issue in entryIssues(entry)"
                :key="issue.key"
                class="trace-issue"
              >
                <div>
                  <span v-if="issue.code">{{ issue.code }}</span>
                  <strong>{{ issue.message }}</strong>
                </div>
                <p v-if="issue.repairHint">{{ issue.repairHint }}</p>
              </article>
            </div>

            <div
              v-if="entryQuestions(entry).length"
              class="trace-question-list"
            >
              <article
                v-for="question in entryQuestions(entry)"
                :key="question.key"
                class="trace-question-card"
              >
                <header>
                  <h4>{{ question.title }}</h4>
                  <div class="trace-chip-row">
                    <span
                      v-for="metric in question.metrics"
                      :key="metric.label"
                      class="trace-chip"
                    >
                      {{ metric.label }}: {{ metric.value }}
                    </span>
                  </div>
                </header>
                <div
                  v-if="question.tags.length"
                  class="trace-tag-row"
                >
                  <span
                    v-for="tag in question.tags"
                    :key="tag"
                  >
                    {{ tag }}
                  </span>
                </div>
                <AiMarkdownMessage
                  v-if="question.content"
                  :content="question.content"
                  class="trace-markdown trace-question-content"
                />
                <ol
                  v-if="question.options.length"
                  class="trace-option-list"
                >
                  <li
                    v-for="option in question.options"
                    :key="option.key"
                    :class="{'is-correct': option.correct}"
                  >
                    <strong>{{ option.label }}</strong>
                    <AiMarkdownMessage
                      :content="option.content"
                      class="trace-markdown trace-option-content"
                    />
                    <span>{{ option.correct ? t('common.ai.generationTrace.labels.correct') : t('common.ai.generationTrace.labels.incorrect') }}</span>
                    <small v-if="option.score">{{ t('common.ai.generationTrace.labels.score') }}: {{ option.score }}</small>
                    <div
                      v-if="option.explanation"
                      class="trace-explanation-row"
                    >
                      <span class="trace-explanation-label">{{ t('common.ai.generationTrace.labels.explanation') }}:</span>
                      <AiMarkdownMessage
                        :content="option.explanation"
                        class="trace-markdown trace-option-explanation"
                      />
                    </div>
                  </li>
                </ol>
                <div
                  v-if="question.answers.length"
                  class="trace-answer-list"
                >
                  <strong>{{ t('common.ai.generationTrace.labels.answer') }}</strong>
                  <article
                    v-for="answer in question.answers"
                    :key="answer.key"
                  >
                    <AiMarkdownMessage
                      :content="answer.content"
                      class="trace-markdown trace-answer-content"
                    />
                    <span v-if="answer.score">{{ t('common.ai.generationTrace.labels.score') }}: {{ answer.score }}</span>
                    <span v-if="answer.sortOrder">{{ t('common.ai.generationTrace.labels.sortOrder') }}: {{ answer.sortOrder }}</span>
                    <div
                      v-if="answer.explanation"
                      class="trace-explanation-row"
                    >
                      <span class="trace-explanation-label">{{ t('common.ai.generationTrace.labels.explanation') }}:</span>
                      <AiMarkdownMessage
                        :content="answer.explanation"
                        class="trace-markdown trace-answer-explanation"
                      />
                    </div>
                  </article>
                </div>
              </article>
            </div>

            <details
              v-if="isAdmin && developerPayload(entry)"
              class="trace-developer"
            >
              <summary>{{ t('common.ai.generationTrace.developerData') }}</summary>
              <div
                v-for="rawOutput in rawAiOutputs(entry)"
                :key="rawOutput.key"
                class="trace-raw-output"
              >
                <span>{{ rawOutput.label }}</span>
                <pre>{{ rawOutput.content }}</pre>
              </div>
              <div class="trace-raw-output">
                <span>{{ t('common.ai.generationTrace.payload') }}</span>
                <pre>{{ developerPayload(entry) }}</pre>
              </div>
            </details>

            <div
              v-if="fallbackBlocks(entry).length"
              class="trace-blocks"
            >
              <AiMarkdownMessage
                v-for="(block, blockIndex) in fallbackBlocks(entry)"
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
        <h3>{{ t('common.ai.generationTrace.emptyTitle') }}</h3>
        <p>{{ t('common.ai.generationTrace.emptyBody') }}</p>
      </div>
    </div>
  </aside>
</template>

<script lang="ts" setup>
import {computed, nextTick, onMounted, onUnmounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {FileClock, X} from 'lucide-vue-next'

import AiMarkdownMessage from '@/features/ai/components/AiMarkdownMessage.vue'
import {useAuthStore} from '@/features/auth/stores/auth'
import type {ChatMessage, GenerationTraceEntry} from '@/features/ai/types/ai'
import {
  currentGenerationStage,
  generationDebugTrace,
  generationStageI18nKey,
  generationTrace,
} from '@/features/ai/utils/generationTrace'

interface WebSource {
  site: string
  title: string
  url: string
  favicon: string
}

interface DisplayMetric {
  label: string
  value: string
}

interface DisplaySection {
  key: string
  title: string
  metrics: DisplayMetric[]
  knowledgePoints: string[]
}

interface DisplayDistribution {
  title: string
  items: DisplayMetric[]
}

interface DisplayIssue {
  key: string
  code: string
  message: string
  repairHint: string
}

interface DisplayQuestion {
  key: string
  title: string
  content: string
  metrics: DisplayMetric[]
  tags: string[]
  options: DisplayOption[]
  answers: DisplayAnswer[]
}

interface DisplayOption {
  key: string
  label: string
  content: string
  correct: boolean
  score: string
  explanation: string
}

interface DisplayAnswer {
  key: string
  content: string
  explanation: string
  score: string
  sortOrder: string
}

interface KnowledgeCoverage {
  requested: string[]
  generated: string[]
}

const TRACE_REVEAL_INTERVAL_MS = 600
const TERMINAL_STAGES = new Set(['RESPONDED', 'FAILED', 'TERMINATED'])
const STAGE_FALLBACK_KEY = 'common.ai.generationTrace.stages.pending'
const TRACE_LABEL_KEY_PREFIX = 'common.ai.generationTrace.labels'
const DETAIL_TYPE_LABEL_KEYS: Record<string, string> = {
  request: 'common.ai.generationTrace.detailTypes.request',
  web_sources: 'common.ai.generationTrace.detailTypes.webSources',
  blueprint: 'common.ai.generationTrace.detailTypes.blueprint',
  drafts: 'common.ai.generationTrace.detailTypes.drafts',
  draft_progress: 'common.ai.generationTrace.detailTypes.draftProgress',
  validation: 'common.ai.generationTrace.detailTypes.validation',
  final_questions: 'common.ai.generationTrace.detailTypes.finalQuestions',
  responded: 'common.ai.generationTrace.detailTypes.responded',
  quality_review: 'common.ai.generationTrace.detailTypes.qualityReview',
  section_attempt: 'common.ai.generationTrace.detailTypes.sectionAttempt',
  repair_attempt: 'common.ai.generationTrace.detailTypes.repairAttempt',
  repair_summary: 'common.ai.generationTrace.detailTypes.repairSummary',
  raw_ai_output: 'common.ai.generationTrace.detailTypes.rawAiOutput',
}
const METRIC_LABEL_KEYS: Record<string, string> = {
  query: `${TRACE_LABEL_KEY_PREFIX}.query`,
  evidenceCount: `${TRACE_LABEL_KEY_PREFIX}.evidenceCount`,
  sectionCount: `${TRACE_LABEL_KEY_PREFIX}.sectionCount`,
  questionCount: `${TRACE_LABEL_KEY_PREFIX}.questionCount`,
  generatedQuestionCount: `${TRACE_LABEL_KEY_PREFIX}.generatedQuestionCount`,
  totalQuestionCount: `${TRACE_LABEL_KEY_PREFIX}.totalQuestionCount`,
  targetCount: `${TRACE_LABEL_KEY_PREFIX}.targetCount`,
  totalScore: `${TRACE_LABEL_KEY_PREFIX}.totalScore`,
  totalEstimatedTime: `${TRACE_LABEL_KEY_PREFIX}.totalEstimatedTime`,
  attemptNo: `${TRACE_LABEL_KEY_PREFIX}.attemptNo`,
  issueCount: `${TRACE_LABEL_KEY_PREFIX}.issueCount`,
  remainingIssueCount: `${TRACE_LABEL_KEY_PREFIX}.remainingIssueCount`,
  promptEvidenceCount: `${TRACE_LABEL_KEY_PREFIX}.promptEvidenceCount`,
  discardedEvidenceCount: `${TRACE_LABEL_KEY_PREFIX}.discardedEvidenceCount`,
  scorePerQuestion: `${TRACE_LABEL_KEY_PREFIX}.scorePerQuestion`,
  estimatedTimePerQuestion: `${TRACE_LABEL_KEY_PREFIX}.estimatedTimePerQuestion`,
  sectionTotalEstimatedTime: `${TRACE_LABEL_KEY_PREFIX}.sectionTotalEstimatedTime`,
  score: `${TRACE_LABEL_KEY_PREFIX}.score`,
  estimatedTime: `${TRACE_LABEL_KEY_PREFIX}.estimatedTime`,
  total: `${TRACE_LABEL_KEY_PREFIX}.total`,
}
const QUESTION_TYPE_LABEL_KEYS: Record<string, string> = {
  '0': 'common.ai.generationTrace.questionTypes.singleChoice',
  '1': 'common.ai.generationTrace.questionTypes.multipleChoice',
  '2': 'common.ai.generationTrace.questionTypes.trueFalse',
  '3': 'common.ai.generationTrace.questionTypes.fillBlank',
  '4': 'common.ai.generationTrace.questionTypes.shortAnswer',
  '5': 'common.ai.generationTrace.questionTypes.mixed',
}
const DIFFICULTY_LABEL_KEYS: Record<string, string> = {
  '0': 'common.ai.generationTrace.difficulties.random',
  '1': 'common.ai.generationTrace.difficulties.easy',
  '2': 'common.ai.generationTrace.difficulties.medium',
  '3': 'common.ai.generationTrace.difficulties.hard',
}
const ISSUE_LABEL_KEYS: Record<string, string> = {
  PAPER_TOO_FEW_QUESTIONS: 'common.ai.generationTrace.issueCodes.PAPER_TOO_FEW_QUESTIONS',
  PAPER_OBJECTIVE_ONLY: 'common.ai.generationTrace.issueCodes.PAPER_OBJECTIVE_ONLY',
  WEAK_MULTI_SELECT_DISTRACTOR: 'common.ai.generationTrace.issueCodes.WEAK_MULTI_SELECT_DISTRACTOR',
  LOW_TOPIC_DIVERSITY: 'common.ai.generationTrace.issueCodes.LOW_TOPIC_DIVERSITY',
  OBJECTIVE_ANSWER_CONFLICT: 'common.ai.generationTrace.issueCodes.OBJECTIVE_ANSWER_CONFLICT',
  SINGLE_SCORE_TOO_HIGH: 'common.ai.generationTrace.issueCodes.SINGLE_SCORE_TOO_HIGH',
  MULTI_SCORE_TOO_HIGH: 'common.ai.generationTrace.issueCodes.MULTI_SCORE_TOO_HIGH',
  JUDGE_SCORE_TOO_HIGH: 'common.ai.generationTrace.issueCodes.JUDGE_SCORE_TOO_HIGH',
  BLANK_SCORE_TOO_HIGH: 'common.ai.generationTrace.issueCodes.BLANK_SCORE_TOO_HIGH',
  SHORT_SCORE_TOO_HIGH: 'common.ai.generationTrace.issueCodes.SHORT_SCORE_TOO_HIGH',
  QUESTION_SCORE_TOO_HIGH: 'common.ai.generationTrace.issueCodes.QUESTION_SCORE_TOO_HIGH',
  EMPTY_RESULT: 'common.ai.generationTrace.issueCodes.EMPTY_RESULT',
  QUESTION_COUNT_MISMATCH: 'common.ai.generationTrace.issueCodes.QUESTION_COUNT_MISMATCH',
  TOTAL_SCORE_MISMATCH: 'common.ai.generationTrace.issueCodes.TOTAL_SCORE_MISMATCH',
  TOTAL_ESTIMATED_TIME_MISMATCH: 'common.ai.generationTrace.issueCodes.TOTAL_ESTIMATED_TIME_MISMATCH',
  EMPTY_CONTENT: 'common.ai.generationTrace.issueCodes.EMPTY_CONTENT',
  INVALID_TYPE: 'common.ai.generationTrace.issueCodes.INVALID_TYPE',
  INVALID_DIFFICULTY: 'common.ai.generationTrace.issueCodes.INVALID_DIFFICULTY',
  INVALID_SCORE: 'common.ai.generationTrace.issueCodes.INVALID_SCORE',
  INVALID_TIME: 'common.ai.generationTrace.issueCodes.INVALID_TIME',
  REFERENCE_DUPLICATE: 'common.ai.generationTrace.issueCodes.REFERENCE_DUPLICATE',
  DUPLICATE_QUESTION: 'common.ai.generationTrace.issueCodes.DUPLICATE_QUESTION',
  MISSING_OPTIONS: 'common.ai.generationTrace.issueCodes.MISSING_OPTIONS',
  INVALID_CORRECT_COUNT: 'common.ai.generationTrace.issueCodes.INVALID_CORRECT_COUNT',
  INVALID_MULTI_CORRECT_COUNT: 'common.ai.generationTrace.issueCodes.INVALID_MULTI_CORRECT_COUNT',
  INVALID_JUDGE_OPTION_COUNT: 'common.ai.generationTrace.issueCodes.INVALID_JUDGE_OPTION_COUNT',
  UNEXPECTED_OPTIONS: 'common.ai.generationTrace.issueCodes.UNEXPECTED_OPTIONS',
  MISSING_ANSWERS: 'common.ai.generationTrace.issueCodes.MISSING_ANSWERS',
  EMPTY_ANSWER: 'common.ai.generationTrace.issueCodes.EMPTY_ANSWER',
  INVALID_SORT_ORDER: 'common.ai.generationTrace.issueCodes.INVALID_SORT_ORDER',
  DUPLICATE_SORT_ORDER: 'common.ai.generationTrace.issueCodes.DUPLICATE_SORT_ORDER',
  SECTION_TYPE_MISMATCH: 'common.ai.generationTrace.issueCodes.SECTION_TYPE_MISMATCH',
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

const {t, locale} = useI18n()
const authStore = useAuthStore()
const bodyRef = ref<HTMLElement | null>(null)
const autoFollow = ref(true)
const visibleEntries = ref<GenerationTraceEntry[]>([])
const queuedEntries = ref<GenerationTraceEntry[]>([])
const prefersReducedMotion = ref(false)
let scrollFrame = 0
let revealTimer = 0
let programmaticScroll = false
let scrollElement: HTMLElement | null = null
let renderedMessageId: string | null = null
let motionQuery: MediaQueryList | null = null

const rawEntries = computed(() => generationTrace(props.message))
const debugEntries = computed(() => generationDebugTrace(props.message))
const isAdmin = computed(() => authStore.user?.role === 0)
const displayEntries = computed(() =>
  isAdmin.value ? mergeAdminTrace(rawEntries.value, debugEntries.value) : rawEntries.value,
)
const stage = computed(() => currentGenerationStage(props.message))
const kindLabel = computed(() =>
  props.message?.messageType === 'PAPER'
    ? t('common.ai.generationTrace.paperKind')
    : t('common.ai.generationTrace.questionKind'),
)
const panelTitle = computed(() => {
  const latest = visibleEntries.value.at(-1) || displayEntries.value.at(-1)
  if (latest) return entryTitle(latest)
  if (props.message?.messageType === 'PAPER') return t('common.ai.generationTrace.paperTitle')
  return t('common.ai.generationTrace.questionTitle')
})
const isStreaming = computed(() =>
  props.message?.pending && stage.value !== 'RESPONDED' && stage.value !== 'FAILED' && stage.value !== 'TERMINATED',
)

watch(displayEntries, syncVisibleEntries, {deep: true, immediate: true})

watch(() => [
  visibleEntries.value.length,
  visibleEntries.value.at(-1)?.timestamp,
  visibleEntries.value.at(-1)?.summary,
], async () => {
  await nextTick()
  if (autoFollow.value || isNearBottom()) {
    scrollToBottom('smooth')
  }
})

onMounted(async () => {
  setupMotionPreference()
  await nextTick()
  scrollElement = resolveScrollTarget()
  scrollElement?.addEventListener('scroll', handleScroll, {passive: true})
  scrollToBottom()
})

onUnmounted(() => {
  if (scrollFrame) cancelAnimationFrame(scrollFrame)
  clearRevealTimer()
  scrollElement?.removeEventListener('scroll', handleScroll)
  motionQuery?.removeEventListener?.('change', handleMotionPreferenceChange)
})

function shouldShowSkeleton(index: number) {
  return isStreaming.value && index === visibleEntries.value.length - 1
}

function syncVisibleEntries() {
  const messageId = props.message?.id || null
  if (messageId !== renderedMessageId) {
    renderedMessageId = messageId
    visibleEntries.value = []
    queuedEntries.value = []
    clearRevealTimer()
  }

  const source = displayEntries.value
  if (!source.length) {
    visibleEntries.value = []
    queuedEntries.value = []
    clearRevealTimer()
    return
  }

  if (!props.message?.pending || prefersReducedMotion.value || isTerminalStage(stage.value)) {
    visibleEntries.value = source
    queuedEntries.value = []
    clearRevealTimer()
    return
  }

  if (!visibleEntries.value.length) {
    visibleEntries.value = source.slice(0, 1)
    queuedEntries.value = source.slice(1)
    scheduleReveal()
    return
  }

  const visibleCount = Math.min(visibleEntries.value.length, source.length)
  visibleEntries.value = source.slice(0, visibleCount)
  queuedEntries.value = source.slice(visibleCount)
  scheduleReveal()
}

function scheduleReveal() {
  if (revealTimer || !queuedEntries.value.length) return
  revealTimer = window.setTimeout(() => {
    revealTimer = 0
    const [nextEntry, ...remainingEntries] = queuedEntries.value
    queuedEntries.value = remainingEntries
    if (nextEntry) {
      visibleEntries.value = visibleEntries.value.concat(nextEntry)
    }
    scheduleReveal()
  }, TRACE_REVEAL_INTERVAL_MS)
}

function clearRevealTimer() {
  if (!revealTimer) return
  window.clearTimeout(revealTimer)
  revealTimer = 0
}

function isTerminalStage(value?: string | null) {
  return Boolean(value && TERMINAL_STAGES.has(value))
}

function mergeAdminTrace(visibleTrace: GenerationTraceEntry[], debugTrace: GenerationTraceEntry[]) {
  const merged = visibleTrace
    .concat(debugTrace)
    .slice()
    .sort(compareTraceEntry)
  const entries: GenerationTraceEntry[] = []
  const pendingRawOutputs: GenerationTraceEntry[] = []

  for (const entry of merged) {
    if (entry.detailType === 'raw_ai_output') {
      const target = findRawOutputTarget(entries, entry)
      if (target) {
        attachRawOutput(target, entry)
      } else {
        pendingRawOutputs.push(entry)
      }
      continue
    }

    const pendingMatches = pendingRawOutputs.filter(rawEntry =>
      isRawOutputTarget(entry, rawEntry),
    )
    const remainingRawOutputs = pendingRawOutputs.filter(rawEntry =>
      !isRawOutputTarget(entry, rawEntry),
    )
    pendingRawOutputs.splice(0, pendingRawOutputs.length, ...remainingRawOutputs)

    entries.push(withRawOutputs(entry, pendingMatches))
  }

  return entries.concat(pendingRawOutputs)
}

function compareTraceEntry(left: GenerationTraceEntry, right: GenerationTraceEntry) {
  const leftTime = traceTime(left)
  const rightTime = traceTime(right)
  if (leftTime !== rightTime) return leftTime - rightTime
  if (left.detailType === 'raw_ai_output' && right.detailType !== 'raw_ai_output') return -1
  if (left.detailType !== 'raw_ai_output' && right.detailType === 'raw_ai_output') return 1
  return 0
}

function traceTime(entry: GenerationTraceEntry) {
  if (!entry.timestamp) return 0
  const date = new Date(entry.timestamp)
  return Number.isNaN(date.getTime()) ? 0 : date.getTime()
}

function findRawOutputTarget(entries: GenerationTraceEntry[], rawEntry: GenerationTraceEntry) {
  return entries.findLast(entry => isRawOutputTarget(entry, rawEntry))
}

function isRawOutputTarget(entry: GenerationTraceEntry, rawEntry: GenerationTraceEntry) {
  if (!entry.payload?.questions && entry.detailType !== 'repair_attempt' && entry.detailType !== 'section_attempt') {
    return false
  }
  const rawPayload = asRecord(rawEntry.payload)
  const entryPayload = asRecord(entry.payload)
  const callType = textValue(rawPayload?.callType)
  if (callType === 'repair_generation') {
    return entry.detailType === 'repair_attempt'
      && sameMetric(rawPayload, entryPayload, 'attemptNo')
  }
  if (callType === 'section_generation') {
    return entry.detailType === 'section_attempt'
      && sameMetric(rawPayload, entryPayload, 'sectionNo')
      && sameMetric(rawPayload, entryPayload, 'attemptNo')
  }
  return false
}

function sameMetric(left: Record<string, unknown> | null, right: Record<string, unknown> | null, key: string) {
  const leftValue = metricValue(left, key)
  const rightValue = metricValue(right, key)
  return !leftValue || !rightValue || leftValue === rightValue
}

function attachRawOutput(target: GenerationTraceEntry, rawEntry: GenerationTraceEntry) {
  Object.assign(target, withRawOutputs(target, [rawEntry]))
}

function withRawOutputs(entry: GenerationTraceEntry, rawEntries: GenerationTraceEntry[]) {
  if (!rawEntries.length) return entry
  const payload = asRecord(entry.payload) || {}
  const rawOutputs = listRecords(payload.rawAiOutputs).concat(rawEntries.map(rawEntry => ({
    ...asRecord(rawEntry.payload),
    title: rawEntry.title,
    summary: rawEntry.summary,
    timestamp: rawEntry.timestamp,
  })))
  return {
    ...entry,
    payload: {
      ...payload,
      rawAiOutputs: rawOutputs,
    },
  }
}

function setupMotionPreference() {
  if (typeof window.matchMedia !== 'function') return
  motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  prefersReducedMotion.value = motionQuery.matches
  motionQuery.addEventListener?.('change', handleMotionPreferenceChange)
  syncVisibleEntries()
}

function handleMotionPreferenceChange(event: MediaQueryListEvent) {
  prefersReducedMotion.value = event.matches
  syncVisibleEntries()
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

function stageLabel(stage?: string | null) {
  const key = generationStageI18nKey(stage)
  if (key) return t(key)
  return textValue(stage) || t(STAGE_FALLBACK_KEY)
}

function detailTypeLabel(detailType?: string | null) {
  if (!detailType) return ''
  const key = DETAIL_TYPE_LABEL_KEYS[detailType]
  return key ? t(key) : ''
}

function entryTitle(entry: GenerationTraceEntry) {
  return detailTypeLabel(entry.detailType) || textValue(entry.title) || stageLabel(entry.stage)
}

function entryMetrics(entry: GenerationTraceEntry): DisplayMetric[] {
  const payload = asRecord(entry.payload)
  if (!payload) return []
  return [
    metricItem(payload, 'query'),
    metricItem(payload, 'evidenceCount'),
    metricItem(payload, 'sectionCount'),
    metricItem(payload, 'questionCount'),
    metricItem(payload, 'generatedQuestionCount'),
    metricItem(payload, 'totalQuestionCount'),
    metricItem(payload, 'targetCount'),
    metricItem(payload, 'totalScore'),
    metricItem(payload, 'totalEstimatedTime'),
    metricItem(payload, 'attemptNo'),
    metricItem(payload, 'issueCount'),
    metricItem(payload, 'remainingIssueCount'),
    metricItem(payload, 'promptEvidenceCount'),
    metricItem(payload, 'discardedEvidenceCount'),
  ].filter((item): item is DisplayMetric => Boolean(item))
}

function strategyText(entry: GenerationTraceEntry) {
  const payload = asRecord(entry.payload)
  return textValue(payload?.generationStrategy)
}

function entrySections(entry: GenerationTraceEntry): DisplaySection[] {
  const payload = asRecord(entry.payload)
  if (!payload) return []
  const sections = listRecords(payload.sections).length
    ? listRecords(payload.sections)
    : listRecords(payload.blueprintSections)
  return sections.map((section, index) => {
    const title = textValue(section.sectionTitle) || t(
      'common.ai.generationTrace.labels.sectionNumber',
      {index: textValue(section.sectionNo) || index + 1},
    )
    const metrics = [
      metricItem(section, 'targetCount'),
      questionTypeMetric(section),
      difficultyMetric(section),
      metricItem(section, 'scorePerQuestion'),
      metricItem(section, 'estimatedTimePerQuestion'),
      metricItem(section, 'sectionTotalEstimatedTime'),
    ].filter((item): item is DisplayMetric => Boolean(item))
    return {
      key: `${title}-${index}`,
      title,
      metrics,
      knowledgePoints: listValues(section.knowledgePoints),
    }
  })
}

function entryDistributions(entry: GenerationTraceEntry): DisplayDistribution[] {
  const payload = asRecord(entry.payload)
  if (!payload) return []
  const distributions: DisplayDistribution[] = []
  const questionTypes = asRecord(payload.questionTypes)
  if (questionTypes) {
    distributions.push({
      title: t('common.ai.generationTrace.distributions.questionTypes'),
      items: recordEntries(questionTypes).map(([key, value]) => ({
        label: questionTypeLabel(key),
        value: formatValue(value),
      })),
    })
  }
  const scores = asRecord(payload.scores)
  if (scores) {
    distributions.push(distributionFromValues(t('common.ai.generationTrace.distributions.scores'), scores))
  }
  const estimatedTimes = asRecord(payload.estimatedTimes)
  if (estimatedTimes) {
    distributions.push(distributionFromValues(t('common.ai.generationTrace.distributions.estimatedTimes'), estimatedTimes))
  }
  return distributions.filter(distribution => distribution.items.length)
}

function distributionFromValues(title: string, payload: Record<string, unknown>): DisplayDistribution {
  const values = listValues(payload.values)
  const items = [
    metricItem(payload, 'total'),
    ...values.map((value, index) => ({
      label: t('common.ai.generationTrace.labels.questionNumber', {index: index + 1}),
      value,
    })),
  ].filter((item): item is DisplayMetric => Boolean(item))
  return {title, items}
}

function knowledgeCoverage(entry: GenerationTraceEntry): KnowledgeCoverage | null {
  const payload = asRecord(entry.payload)
  const coverage = asRecord(payload?.knowledgePoints)
  if (!coverage) return null
  const requested = listValues(coverage.requested)
  const generated = listValues(coverage.generated)
  if (!requested.length && !generated.length) return null
  return {requested, generated}
}

function entryIssues(entry: GenerationTraceEntry): DisplayIssue[] {
  const payload = asRecord(entry.payload)
  return listRecords(payload?.issues).map((issue, index) => {
    const questionIndex = issue.questionIndex === undefined || issue.questionIndex === null
      ? ''
      : t('common.ai.generationTrace.labels.questionNumber', {index: Number(issue.questionIndex)})
    const code = textValue(issue.code)
    const message = issueCodeLabel(code) || textValue(issue.message) || t('common.ai.generationTrace.labels.checkNeeded')
    return {
      key: `${code || message}-${index}`,
      code: questionIndex,
      message,
      repairHint: textValue(issue.repairHint),
    }
  })
}

function entryQuestions(entry: GenerationTraceEntry, debug = false): DisplayQuestion[] {
  const payload = asRecord(entry.payload)
  const questions = listRecords(payload?.questions)
  const visibleQuestions = debug ? questions : questions.slice(0, 8)
  return visibleQuestions.map((question, index) => {
    const title = textValue(question.questionTitle)
      || t('common.ai.generationTrace.labels.questionNumber', {index: index + 1})
    return {
      key: `${title}-${index}`,
      title,
      content: textValue(question.questionContent),
      metrics: [
        questionTypeMetric(question),
        difficultyMetric(question),
        metricItem(question, 'score'),
        metricItem(question, 'estimatedTime'),
      ].filter((item): item is DisplayMetric => Boolean(item)),
      tags: listValues(question.tags),
      options: listRecords(question.options).map((option, optionIndex) =>
        displayOption(option, optionIndex),
      ),
      answers: listRecords(question.answers).map((answer, answerIndex) =>
        displayAnswer(answer, answerIndex),
      ),
    }
  })
}

function displayOption(option: Record<string, unknown>, index: number): DisplayOption {
  const label = textValue(option.optionLabel) || String.fromCharCode(65 + index)
  return {
    key: `${label}-${index}`,
    label,
    content: textValue(option.optionContent),
    correct: Number(option.isCorrect) === 1,
    score: metricValue(option, 'score'),
    explanation: textValue(option.explanation),
  }
}

function displayAnswer(answer: Record<string, unknown>, index: number): DisplayAnswer {
  return {
    key: `${textValue(answer.answerContent) || 'answer'}-${index}`,
    content: textValue(answer.answerContent),
    explanation: textValue(answer.explanation),
    score: metricValue(answer, 'score'),
    sortOrder: metricValue(answer, 'sortOrder'),
  }
}

function fallbackBlocks(entry: GenerationTraceEntry) {
  const payload = asRecord(entry.payload)
  if (!payload) return []
  const hasWebSources = webSources(entry).length > 0
  return [
    ...(hasWebSources ? [] : listRecords(payload.evidences).slice(0, 4).map(buildEvidenceLine)),
    textValue(payload.error),
    textValue(payload.errorMessage),
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

function rawAiOutputs(entry: GenerationTraceEntry) {
  const payload = asRecord(entry.payload)
  const rawRecords = listRecords(payload?.rawAiOutputs)
  const records = rawRecords.length ? rawRecords : textValue(payload?.rawOutput) ? [payload || {}] : []
  return records
    .map((record, index) => ({
      key: `${textValue(record.callType) || 'raw'}-${index}`,
      label: rawOutputLabel(record),
      content: textValue(record.rawOutput),
    }))
    .filter(item => Boolean(item.content))
}

function rawOutputLabel(payload: Record<string, unknown>) {
  const parts = [
    callTypeLabel(textValue(payload.callType)),
    metricValue(payload, 'sectionNo'),
    metricValue(payload, 'attemptNo'),
  ].filter(Boolean)
  return parts.join(' / ')
}

function buildEvidenceLine(item: Record<string, unknown>) {
  const title = textValue(item.title)
    || textValue(item.sourceLabel)
    || t('common.ai.generationTrace.labels.reference')
  const snippet = textValue(item.excerpt) || textValue(item.snippet)
  return [title, snippet].filter(Boolean).join('\n')
}

function markdownBlock(block: string) {
  return block.split('\n').filter(Boolean).join('\n\n')
}

function metricItem(payload: Record<string, unknown>, key: string): DisplayMetric | null {
  const value = payload[key]
  if (value === null || value === undefined || value === '') return null
  return {label: metricLabel(key), value: formatValue(value)}
}

function metricValue(payload: Record<string, unknown> | null, key: string) {
  const value = payload?.[key]
  if (value === null || value === undefined || value === '') return ''
  return formatValue(value)
}

function questionTypeMetric(payload: Record<string, unknown>): DisplayMetric | null {
  const value = payload.questionType
  if (value === null || value === undefined || value === '') return null
  return {label: t('common.ai.generationTrace.labels.questionType'), value: questionTypeLabel(value)}
}

function difficultyMetric(payload: Record<string, unknown>): DisplayMetric | null {
  const value = payload.difficulty
  if (value === null || value === undefined || value === '') return null
  return {label: t('common.ai.generationTrace.labels.difficulty'), value: difficultyLabel(value)}
}

function questionTypeLabel(value: unknown) {
  const key = String(value)
  const labelKey = QUESTION_TYPE_LABEL_KEYS[key]
  return labelKey ? t(labelKey) : t('common.ai.generationTrace.questionTypes.unknown')
}

function difficultyLabel(value: unknown) {
  const key = String(value)
  const labelKey = DIFFICULTY_LABEL_KEYS[key]
  return labelKey ? t(labelKey) : t('common.ai.generationTrace.difficulties.unknown')
}

function metricLabel(key: string) {
  const labelKey = METRIC_LABEL_KEYS[key]
  return labelKey ? t(labelKey) : key
}

function issueCodeLabel(code: string) {
  const labelKey = ISSUE_LABEL_KEYS[code]
  return labelKey ? t(labelKey) : ''
}

function callTypeLabel(value: string) {
  if (value === 'section_generation') return t('common.ai.generationTrace.callTypes.sectionGeneration')
  if (value === 'repair_generation') return t('common.ai.generationTrace.callTypes.repairGeneration')
  return value || t('common.ai.generationTrace.callTypes.modelOutput')
}

function developerPayload(entry: GenerationTraceEntry) {
  const payload = asRecord(entry.payload)
  if (!payload) return ''
  return JSON.stringify(payload, null, 2)
}

function formatValue(value: unknown): string {
  if (Array.isArray(value)) return formatList(value.map(formatValue))
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function formatList(values: string[]) {
  return values.join(locale.value === 'zh-CN' ? '、' : ', ')
}

function listRecords(value: unknown): Record<string, unknown>[] {
  if (!Array.isArray(value)) return []
  return value.filter((item): item is Record<string, unknown> =>
    Boolean(item) && typeof item === 'object' && !Array.isArray(item),
  )
}

function recordEntries(value: Record<string, unknown>) {
  return Object.entries(value).filter(([, item]) =>
    item !== null && item !== undefined && item !== '',
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

.trace-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.trace-metric,
.trace-chip,
.trace-tag-row span {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 5px;
  padding: 4px 7px;
  background: var(--color-surface-container-high);
  border-radius: 6px;
  color: var(--color-on-surface-variant);
  font-family: var(--font-label);
  font-size: 12px;
  line-height: 1.3;
}

.trace-metric small {
  color: var(--color-muted);
  font-size: inherit;
}

.trace-metric strong {
  color: var(--color-on-surface);
  font-weight: 650;
}

.trace-note {
  margin: 0;
  color: var(--color-on-surface-variant);
  font-size: 14px;
  line-height: 1.62;
}

.trace-section-grid,
.trace-distributions,
.trace-question-list,
.trace-issue-list {
  display: grid;
  gap: 10px;
}

.trace-mini-card,
.trace-question-card,
.trace-issue {
  display: grid;
  gap: 8px;
  min-width: 0;
  padding: 10px;
  background: color-mix(in srgb, var(--color-surface-container) 72%, transparent);
  border: 1px solid var(--color-outline-light);
  border-radius: 7px;
}

.trace-mini-card h4,
.trace-question-card h4 {
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 16px;
  font-weight: 650;
  line-height: 1.28;
}

.trace-mini-card p,
.trace-issue p,
.trace-option-list p,
.trace-answer-list p {
  margin: 0;
  color: var(--color-on-surface-variant);
  font-size: 13px;
  line-height: 1.55;
}

.trace-chip-row,
.trace-tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.trace-chip {
  background: var(--color-surface-container);
}

.trace-tag-row span {
  background: transparent;
  border: 1px solid var(--color-outline-light);
}

.trace-issue {
  border-left: 3px solid var(--color-primary);
}

.trace-issue div {
  display: grid;
  gap: 4px;
}

.trace-issue span {
  width: fit-content;
  padding: 3px 6px;
  background: color-mix(in srgb, var(--color-primary) 11%, transparent);
  border-radius: 5px;
  color: var(--color-primary);
  font-family: var(--font-label);
  font-size: 11px;
  line-height: 1.3;
}

.trace-issue strong {
  color: var(--color-on-surface);
  font-size: 14px;
  line-height: 1.45;
}

.trace-question-card header {
  display: grid;
  gap: 6px;
}

.trace-question-content,
.trace-option-content,
.trace-answer-content,
.trace-option-explanation,
.trace-answer-explanation {
  color: var(--color-on-surface);
}

.trace-option-list {
  display: grid;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.trace-option-list li {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) auto auto;
  align-items: start;
  gap: 8px;
  min-width: 0;
  padding: 7px;
  background: var(--color-surface-container);
  border-radius: 6px;
}

.trace-option-list li.is-correct {
  background: color-mix(in srgb, var(--color-primary) 10%, var(--color-surface-container));
}

.trace-option-list li > strong {
  display: grid;
  width: 24px;
  height: 24px;
  place-items: center;
  background: var(--color-surface-card);
  border-radius: 5px;
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 12px;
}

.trace-option-list li > span,
.trace-option-list li > small,
.trace-answer-list article > span {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  line-height: 1.45;
}

.trace-option-list li > p {
  grid-column: 2 / -1;
}

.trace-explanation-row {
  grid-column: 2 / -1;
  display: grid;
  gap: 4px;
}

.trace-explanation-label {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  line-height: 1.4;
}

.trace-answer-list {
  display: grid;
  gap: 6px;
}

.trace-answer-list > strong {
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 12px;
  line-height: 1.3;
}

.trace-answer-list article {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 6px;
  padding: 7px;
  background: var(--color-surface-container);
  border-radius: 6px;
}

.trace-answer-list article .trace-answer-content {
  flex: 1 1 100%;
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

.trace-developer {
  display: grid;
  gap: 8px;
  padding-top: 4px;
}

.trace-developer summary {
  display: inline-flex;
  width: fit-content;
  color: var(--color-muted);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 12px;
  line-height: 1.4;
}

.trace-developer[open] {
  gap: 8px;
}

.trace-developer[open] summary {
  margin-bottom: 8px;
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
