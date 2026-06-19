export type AiMessageRole = 'USER' | 'ASSISTANT' | 'SYSTEM' | 'user' | 'assistant' | 'system'
export type AiAgentMode = 'CHAT' | 'QUESTION' | 'PAPER'
export type AiMessageType = 'TEXT' | 'QUESTION_SET' | 'PAPER' | 'GRADING_RESULT'

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
