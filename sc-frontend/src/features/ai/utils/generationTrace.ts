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
}

const STAGE_LABELS: Record<GenerationStage, string> = {
  RECEIVED: '接收请求',
  CONTEXT_READY: '整理资料',
  PLANNED: '生成方案',
  GENERATED: '生成草稿',
  VALIDATED: '质量校验',
  REPAIRED: '修正题目',
  ASSEMBLED: '组装结果',
  RESPONDED: '生成完成',
  FAILED: '生成失败',
}

export function isGenerationMessage(message: ChatMessage) {
  return message.messageType === 'QUESTION_SET' || message.messageType === 'PAPER'
}

export function generationTrace(message: ChatMessage | null | undefined): GenerationTraceEntry[] {
  const value = message?.payload?.generationTrace
  if (!Array.isArray(value)) return []

  return value.filter((entry): entry is GenerationTraceEntry =>
    Boolean(entry) && typeof entry === 'object',
  )
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

  const latest = generationTrace(message).at(-1)
  if (latest?.title) return latest.title
  if (message.messageType === 'PAPER') return '正在生成试卷'
  if (message.messageType === 'QUESTION_SET') return '正在生成题目'
  return '正在生成'
}

export function generationSummary(message: ChatMessage) {
  const latest = generationTrace(message).at(-1)
  if (latest?.summary) return latest.summary
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
