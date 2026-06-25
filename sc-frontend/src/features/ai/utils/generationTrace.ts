import type {ChatMessage, GenerationStage, GenerationTraceEntry} from '@/features/ai/types/ai'

export const GENERATION_STAGE_ORDER: GenerationStage[] = [
  'RECEIVED',
  'CONTEXT_READY',
  'PLANNED',
  'GENERATED',
  'VALIDATED',
  'REPAIRED',
  'ASSEMBLED',
  'RESPONDED',
]

const STAGE_PROGRESS: Record<GenerationStage, number> = {
  RECEIVED: 10,
  CONTEXT_READY: 24,
  PLANNED: 38,
  GENERATED: 62,
  VALIDATED: 76,
  REPAIRED: 86,
  ASSEMBLED: 94,
  RESPONDED: 100,
  FAILED: 100,
  TERMINATED: 100,
}

const STAGE_LABELS: Record<GenerationStage, string> = {
  RECEIVED: '接收请求',
  CONTEXT_READY: '联网搜索资料',
  PLANNED: '生成方案',
  GENERATED: '生成草稿',
  VALIDATED: '质量校验',
  REPAIRED: '修正题目',
  ASSEMBLED: '组装结果',
  RESPONDED: '生成完成',
  FAILED: '生成失败',
  TERMINATED: '已终止',
}

const TECHNICAL_DETAIL_TYPES = new Set(['section_attempt', 'repair_attempt', 'placeholder_result', 'raw_ai_output'])
const HIDDEN_ISSUE_CODES = new Set(['MISSING_QUESTION_BANK_ID'])
const EMPTY_RESULT_ISSUE_CODES = new Set(['EMPTY_RESULT'])

export function isGenerationMessage(message: ChatMessage) {
  return message.messageType === 'QUESTION_SET' || message.messageType === 'PAPER'
}

export function rawGenerationTrace(message: ChatMessage | null | undefined): GenerationTraceEntry[] {
  const value = message?.payload?.generationTrace
  return traceList(value)
}

export function generationDebugTrace(message: ChatMessage | null | undefined): GenerationTraceEntry[] {
  const explicitDebugTrace = traceList(message?.payload?.generationDebugTrace)
  if (explicitDebugTrace.length) return explicitDebugTrace

  const finalQuestionCount = resolvedQuestionCount(message)
  return rawGenerationTrace(message).filter(entry =>
    isTechnicalEntry(entry) || isLegacyEmptyNoise(entry, finalQuestionCount),
  )
}

export function generationTrace(message: ChatMessage | null | undefined): GenerationTraceEntry[] {
  const finalQuestionCount = resolvedQuestionCount(message)
  return collapseVisibleTrace(rawGenerationTrace(message)
    .filter(entry => !isTechnicalEntry(entry))
    .filter(entry => !isLegacyEmptyNoise(entry, finalQuestionCount))
    .map(sanitizeTraceEntry))
}

export function currentGenerationStage(message: ChatMessage | null | undefined) {
  const trace = generationTrace(message)
  const stageFromPayload = message?.payload?.generationStage
  const stage = trace.at(-1)?.stage || (typeof stageFromPayload === 'string' ? stageFromPayload : undefined)
  return stage || (message?.pending ? 'RECEIVED' : undefined)
}

export function generationProgress(message: ChatMessage | null | undefined) {
  const stage = currentGenerationStage(message)
  if (isGenerationStage(stage)) return STAGE_PROGRESS[stage]
  const traceCount = generationTrace(message).length
  return Math.min(92, Math.max(10, 10 + traceCount * 8))
}

export function generationStageLabel(stage?: string | null) {
  if (isGenerationStage(stage)) return STAGE_LABELS[stage]
  return stage || '准备中'
}

export function generationTitle(message: ChatMessage) {
  if (currentGenerationStage(message) === 'RESPONDED') {
    return message.messageType === 'PAPER' ? '出卷完成' : '出题完成'
  }
  if (currentGenerationStage(message) === 'TERMINATED' || message.terminated) {
    return message.messageType === 'PAPER' ? '出卷已终止' : '出题已终止'
  }

  const latest = generationTrace(message).at(-1)
  if (latest?.title) return latest.title
  if (message.messageType === 'PAPER') return '正在生成试卷'
  if (message.messageType === 'QUESTION_SET') return '正在生成题目'
  return '正在生成'
}

export function generationSummary(message: ChatMessage) {
  const latest = generationTrace(message).at(-1)
  if (latest?.summary) return latest.summary
  if (currentGenerationStage(message) === 'TERMINATED' || message.terminated) return '用户已终止本次生成任务。'
  if (message.pending) return '正在规划题目结构和资料检索步骤。'
  return '已保存结构化生成结果。'
}

export function generatedQuestionCount(message: ChatMessage) {
  const payload = message.payload || {}
  const questions = payload.questions
  if (Array.isArray(questions)) return questions.length

  const traceQuestions = generationTrace(message)
    .map(entry => entry.payload?.questions)
    .findLast(Array.isArray)
  return Array.isArray(traceQuestions) ? traceQuestions.length : 0
}

function isGenerationStage(stage?: string | null): stage is GenerationStage {
  return Boolean(stage && stage in STAGE_PROGRESS)
}

function traceList(value: unknown): GenerationTraceEntry[] {
  if (!Array.isArray(value)) return []

  return value.filter((entry): entry is GenerationTraceEntry =>
    Boolean(entry) && typeof entry === 'object',
  )
}

function collapseVisibleTrace(entries: GenerationTraceEntry[]) {
  const collapsed: GenerationTraceEntry[] = []
  for (const entry of entries) {
    if (isGeneratedQuestionEntry(entry)) {
      const existingIndex = collapsed.findIndex(isGeneratedQuestionEntry)
      if (existingIndex >= 0) {
        collapsed[existingIndex] = mergeGeneratedEntry(collapsed[existingIndex], entry)
      } else {
        collapsed.push(entry)
      }
      continue
    }

    const existingIndex = collapsed.findIndex(item => traceKey(item) === traceKey(entry))
    if (existingIndex >= 0) {
      collapsed[existingIndex] = entry
      continue
    }
    collapsed.push(entry)
  }
  return collapsed
}

function mergeGeneratedEntry(current: GenerationTraceEntry, next: GenerationTraceEntry): GenerationTraceEntry {
  const currentPayload = recordValue(current.payload) || {}
  const nextPayload = recordValue(next.payload) || {}
  const nextQuestions = Array.isArray(nextPayload.questions) ? nextPayload.questions : []
  const currentQuestions = Array.isArray(currentPayload.questions) ? currentPayload.questions : []
  const questions = nextPayload.questionDelta === true
    ? currentQuestions.concat(nextQuestions)
    : nextQuestions.length ? nextQuestions : currentQuestions
  const payload = {
    ...currentPayload,
    ...nextPayload,
  }
  if (questions.length) {
    payload.questions = questions
    payload.questionCount = questions.length
  }

  const generatedCount = numberValue(
    nextPayload.generatedQuestionCount
      ?? payload.generatedQuestionCount
      ?? payload.questionCount
      ?? questions.length,
  )
  const totalCount = numberValue(nextPayload.totalQuestionCount ?? currentPayload.totalQuestionCount)
  const summary = totalCount && generatedCount !== null
    ? `已生成 ${generatedCount} / ${totalCount} 道题目草稿。`
    : next.summary || current.summary

  return {
    ...current,
    ...next,
    title: next.title || current.title,
    summary,
    payload,
  }
}

function sanitizeTraceEntry(entry: GenerationTraceEntry): GenerationTraceEntry {
  const payload = recordValue(entry.payload)
  if (!payload || !Array.isArray(payload.issues)) return entry

  const issues = payload.issues.filter(issue => !isHiddenIssue(issue))
  if (issues.length === payload.issues.length) return entry

  const nextPayload = {...payload, issues}
  if ('issueCount' in nextPayload) nextPayload.issueCount = issues.length
  if ('remainingIssueCount' in nextPayload) nextPayload.remainingIssueCount = issues.length
  return {...entry, payload: nextPayload}
}

function isGeneratedQuestionEntry(entry: GenerationTraceEntry) {
  return entry.stage === 'GENERATED' && (entry.detailType === 'draft_progress' || entry.detailType === 'drafts')
}

function isTechnicalEntry(entry: GenerationTraceEntry) {
  return typeof entry.detailType === 'string' && TECHNICAL_DETAIL_TYPES.has(entry.detailType)
}

function isLegacyEmptyNoise(entry: GenerationTraceEntry, finalQuestionCount: number) {
  if (finalQuestionCount <= 0 || (entry.stage !== 'GENERATED' && entry.stage !== 'VALIDATED')) return false

  const payload = recordValue(entry.payload)
  if (!payload) return false
  const questionCount = numberValue(payload.questionCount)
  const hasEmptyQuestions = questionCount === 0 || (Array.isArray(payload.questions) && payload.questions.length === 0)
  if (!hasEmptyQuestions) return false

  const issueCodes = Array.isArray(payload.issues)
    ? payload.issues.map(issueCode).filter(Boolean)
    : []
  return issueCodes.some(code => EMPTY_RESULT_ISSUE_CODES.has(code))
    || entry.detailType === 'drafts'
    || entry.detailType === 'validation'
}

function isHiddenIssue(issue: unknown) {
  const code = issueCode(issue)
  return Boolean(code && HIDDEN_ISSUE_CODES.has(code))
}

function issueCode(issue: unknown) {
  const record = recordValue(issue)
  const code = record?.code
  return typeof code === 'string' ? code : ''
}

function traceKey(entry: GenerationTraceEntry) {
  return `${entry.stage || ''}:${entry.detailType || ''}`
}

function payloadQuestionCount(value: unknown) {
  return Array.isArray(value) ? value.length : 0
}

function resolvedQuestionCount(message: ChatMessage | null | undefined) {
  const payloadCount = payloadQuestionCount(message?.payload?.questions)
  if (payloadCount > 0) return payloadCount

  return rawGenerationTrace(message)
    .map(entry => payloadQuestionCount(entry.payload?.questions))
    .findLast(count => count > 0) || 0
}

function recordValue(value: unknown): Record<string, unknown> | null {
  return value && typeof value === 'object' ? value as Record<string, unknown> : null
}

function numberValue(value: unknown) {
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}
