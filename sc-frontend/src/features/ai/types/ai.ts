export type AiMessageRole = 'USER' | 'ASSISTANT' | 'SYSTEM' | 'user' | 'assistant' | 'system'
export type AiAgentMode = 'CHAT' | 'QUESTION' | 'PAPER'
export type AiMessageType = 'TEXT' | 'QUESTION_SET' | 'PAPER' | 'GRADING_RESULT'
export type GenerationStage =
  | 'RECEIVED'
  | 'CONTEXT_READY'
  | 'PLANNED'
  | 'GENERATED'
  | 'VALIDATED'
  | 'REPAIRED'
  | 'ASSEMBLED'
  | 'RESPONDED'
  | 'FAILED'
  | 'TERMINATED'

export interface GenerationRequest {
  questionBankId?: string | null
  questionCount?: number | null
  questionType?: number | null
  difficulty?: number | null
  scorePerQuestion?: number | null
  totalScore?: number | null
  totalEstimatedTime?: number | null
  paperName?: string | null
  paperType?: string | null
  requirement?: string | null
  chapterIds?: string[] | null
  knowledgePoints?: string[] | null
  abilityGoals?: string[] | null
}

export interface GenerationStageEvent {
  messageId?: string
  requestId?: string
  mode?: AiAgentMode | string
  stage: GenerationStage | string
  status?: 'processing' | 'completed' | 'failed' | 'error' | string
  title?: string
  summary?: string
  payload?: Record<string, unknown> | null
  timestamp?: string
}

export interface GenerationResultEvent {
  messageId?: string
  requestId?: string
  mode?: AiAgentMode | string
  content: string
  messageType: AiMessageType | string
  payload?: Record<string, unknown> | null
}

export interface GenerationTraceEntry {
  entryId?: string
  stage?: GenerationStage | string
  source?: string
  detailType?: string
  title?: string
  summary?: string
  payload?: Record<string, unknown> | null
  timestamp?: string
}

export interface Conversation {
  id: string
  title: string
  pinned: boolean
  favorited: boolean
  createdAt: string
  updatedAt: string
}

export interface ChatMessage {
  id: string
  role: AiMessageRole
  content: string
  createdAt: string
  messageType?: AiMessageType | string
  payload?: Record<string, unknown> | null
  pending?: boolean
  failed?: boolean
  terminated?: boolean
}

export type AgentSearchPhase = 'started' | 'results' | 'empty' | 'error' | 'completed'

export interface AgentSearchItem {
  sourceType?: string
  sourceLabel?: string
  sourceId?: string | null
  courseId?: string | null
  title?: string
  contextLabel?: string
  snippet?: string
  relationLabel?: string
  metadata?: Record<string, unknown> | null
  indexInfo?: Record<string, unknown> | null
}

export interface AgentSearchEvent {
  searchId?: string
  phase: AgentSearchPhase | string
  domain: string
  label: string
  query?: string
  occurredAt?: string
  total?: number
  items?: AgentSearchItem[]
  status?: string
  reason?: string
  provider?: string
  durationMs?: number
  retryable?: boolean
}

export interface AgentSearchRecord {
  searchId?: string
  domain?: string
  query?: string
  label?: string
  phase?: AgentSearchPhase | string
  total?: number
  occurredAt?: string
  items?: AgentSearchItem[]
  status?: string
  reason?: string
  provider?: string
  durationMs?: number
  retryable?: boolean
}

export interface AgentSearchPayload {
  events?: AgentSearchEvent[]
  searches?: AgentSearchRecord[]
  items?: AgentSearchItem[]
}

export interface AiChatContextInfo {
  conversationId: string
  memoryMessageCount: number
  ragStrategy: string
  matchedChunkCount: number
  matchedDocIds: string[]
}

export interface KnowledgeDoc {
  id: string
  filename: string
  status: string
  chunkCount: number
  createdAt: string
}

export interface UpdateConversationRequest {
  title?: string
  pinned?: boolean
  favorited?: boolean
}

export interface ChatRequest {
  conversationId?: string | null
  message: string
  agentMode?: AiAgentMode
  courseId?: string
  generation?: GenerationRequest
}

export interface IngestDocumentRequest {
  storageObjectId: string
}

export interface AiContext {
  sourceRoute: string
  courseId?: string
  questionBankId?: string
  classSessionId?: string
  chapterId?: string
  selectedText?: string
}

export interface LiveSummaryMindMapNode {
  name: string
  children?: LiveSummaryMindMapNode[]
}

export interface LiveSummaryTimelineItem {
  time?: string
  title?: string
  detail?: string
}

export interface LiveSummaryPayload {
  overview?: string
  keyPoints?: string[]
  timeline?: LiveSummaryTimelineItem[]
  questions?: string[]
  mindMap?: LiveSummaryMindMapNode
  [key: string]: unknown
}

export interface LiveSummarySnapshot {
  id: string
  summarySessionId: string
  classSessionId: string
  sequenceNo: number
  transcriptUntilSequenceNo: number
  overview: string
  payload: LiveSummaryPayload
  createdAt: string
}

export interface LiveTranscriptSegment {
  id: string | null
  summarySessionId: string | null
  classSessionId: string
  sequenceNo: number | null
  speakerId: string | null
  text: string
  beginTimeMs: number | null
  endTimeMs: number | null
  createdAt: string
  final?: boolean
}

export interface LiveSummarySession {
  id: string | null
  classSessionId: string
  courseId: string
  teacherId: string
  status: 'NOT_STARTED' | 'RUNNING' | 'STOPPED' | 'FAILED' | string
  startedAt: string | null
  stoppedAt: string | null
  latestSnapshot: LiveSummarySnapshot | null
  recentTranscripts: LiveTranscriptSegment[]
}

export interface LiveSummaryAudioToken {
  token: string
  expiresInSeconds: number
}

export interface LiveSummaryErrorEvent {
  message?: string
}
