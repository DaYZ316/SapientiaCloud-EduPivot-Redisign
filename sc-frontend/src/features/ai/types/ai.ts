export type AiMessageRole = 'USER' | 'ASSISTANT' | 'SYSTEM' | 'user' | 'assistant' | 'system'

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
  pending?: boolean
  failed?: boolean
}

export interface KnowledgeDoc {
  id: string
  filename: string
  status: string
  chunkCount: number
  createdAt: string
}

export interface CreateConversationRequest {
  title: string
}

export interface UpdateConversationRequest {
  title?: string
  pinned?: boolean
  favorited?: boolean
}

export interface ChatRequest {
  conversationId: string
  message: string
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
