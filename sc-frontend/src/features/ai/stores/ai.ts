import {computed, ref} from 'vue'
import {defineStore} from 'pinia'

import {
  deleteConversation,
  deleteKnowledgeDoc,
  ingestKnowledgeDoc,
  listConversationMessages,
  listConversations,
  listKnowledgeDocs,
  streamChat,
  updateConversation,
} from '@/features/ai/api/ai'
import type {
  AgentSearchEvent,
  AgentSearchItem,
  AgentSearchPayload,
  AgentSearchRecord,
  AiAgentMode,
  AiChatContextInfo,
  AiContext,
  ChatMessage,
  Conversation,
  GenerationResultEvent,
  GenerationStageEvent,
  GenerationTraceEntry,
  GenerationRequest,
  KnowledgeDoc,
} from '@/features/ai/types/ai'
import {createGenerationRequestPayload} from '@/features/ai/utils/generationRequestPayload'
import {notify} from '@/shared/composables/useGlobalNotification'

interface SendMessageOptions {
  agentMode?: AiAgentMode
  courseId?: string
  generation?: GenerationRequest
}

interface LoadConversationsOptions {
  silent?: boolean
  page?: number
  append?: boolean
}

const STREAM_FLUSH_INTERVAL_MS = 18
const CONVERSATION_PAGE_SIZE = 20

function messageTypeForMode(mode?: AiAgentMode) {
  if (mode === 'QUESTION') return 'QUESTION_SET'
  if (mode === 'PAPER') return 'PAPER'
  return 'TEXT'
}

function createLocalMessage(
  role: 'USER' | 'ASSISTANT',
  content: string,
  messageType = 'TEXT',
  payload?: Record<string, unknown> | null,
): ChatMessage {
  const message: ChatMessage = {
    id: `local-${role.toLowerCase()}-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    role,
    content,
    messageType,
    createdAt: new Date().toISOString(),
    pending: role === 'ASSISTANT',
  }
  if (payload) {
    message.payload = payload
  }
  return message
}

export const useAiStore = defineStore('ai', () => {
  const conversations = ref<Conversation[]>([])
  const messages = ref<ChatMessage[]>([])
  const knowledgeDocs = ref<KnowledgeDoc[]>([])
  const latestChatContext = ref<AiChatContextInfo | null>(null)
  const activeAgentSearchSteps = ref<AgentSearchEvent[]>([])
  const latestAgentSearchItems = ref<AgentSearchItem[]>([])
  const activeConversationId = ref<string | null>(null)
  const context = ref<AiContext>({sourceRoute: '/'})
  const loadingConversations = ref(false)
  const loadingMoreConversations = ref(false)
  const loadingMessages = ref(false)
  const loadingKnowledgeDocs = ref(false)
  const conversationsLoaded = ref(false)
  const conversationPage = ref(1)
  const hasMoreConversations = ref(true)
  const streaming = ref(false)
  const streamError = ref('')
  const abortController = ref<AbortController | null>(null)
  const draftConversationOpen = ref(false)
  const activeGenerationMessageId = ref<string | null>(null)
  const activeGenerationRequestId = ref<string | null>(null)
  const activeGenerationConversationId = ref<string | null>(null)
  let conversationsLoadPromise: Promise<void> | null = null
  let streamFlushTimer: number | null = null
  let activeStreamBuffer: ReturnType<typeof createDisplayStreamBuffer> | null = null

  const activeConversation = computed(() =>
    conversations.value.find((conversation) => conversation.id === activeConversationId.value) || null,
  )

  const sortedConversations = computed(() =>
    [...conversations.value].sort((left, right) => {
      if (left.pinned !== right.pinned) return left.pinned ? -1 : 1
      return new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime()
    }),
  )

  const latestArtifact = computed(() =>
    [...messages.value]
      .reverse()
      .find((message) => message.role.toLowerCase() !== 'user' && message.messageType && message.messageType !== 'TEXT') || null,
  )
  const activeGenerationMessage = computed(() =>
    messages.value.find((message) => message.id === activeGenerationMessageId.value) || null,
  )

  function upsertConversationSummary(conversation: Pick<Conversation, 'id' | 'title'>) {
    const existing = conversations.value.find((item) => item.id === conversation.id)
    if (existing) {
      existing.title = conversation.title
      existing.updatedAt = new Date().toISOString()
      return
    }
    const now = new Date().toISOString()
    conversations.value.unshift({
      id: conversation.id,
      title: conversation.title,
      pinned: false,
      favorited: false,
      createdAt: now,
      updatedAt: now,
    })
  }

  function setContext(nextContext: AiContext) {
    context.value = nextContext
  }

  async function loadConversations(options: LoadConversationsOptions = {}) {
    const page = options.page ?? 1
    if (!options.silent) {
      loadingConversations.value = true
    }
    try {
      const nextConversations = await listConversations(page, CONVERSATION_PAGE_SIZE)
      conversations.value = options.append
        ? appendConversations(conversations.value, nextConversations)
        : nextConversations
      conversationPage.value = page
      hasMoreConversations.value = nextConversations.length === CONVERSATION_PAGE_SIZE
      conversationsLoaded.value = true
      if (!options.append && !draftConversationOpen.value && !activeConversationId.value && conversations.value.length > 0) {
        activeConversationId.value = sortedConversations.value[0].id
        await loadMessages(activeConversationId.value)
      }
    } finally {
      if (!options.silent) {
        loadingConversations.value = false
      }
    }
  }

  async function loadMoreConversations() {
    if (loadingMoreConversations.value || !hasMoreConversations.value) return

    loadingMoreConversations.value = true
    try {
      await loadConversations({
        append: true,
        page: conversationPage.value + 1,
        silent: true,
      })
    } finally {
      loadingMoreConversations.value = false
    }
  }

  function appendConversations(current: Conversation[], next: Conversation[]) {
    const existingIds = new Set(current.map((conversation) => conversation.id))
    return current.concat(next.filter((conversation) => !existingIds.has(conversation.id)))
  }

  async function ensureConversationsLoaded() {
    if (conversationsLoaded.value) return

    conversationsLoadPromise ??= loadConversations().finally(() => {
      conversationsLoadPromise = null
    })
    await conversationsLoadPromise
  }

  async function loadMessages(conversationId = activeConversationId.value) {
    if (!conversationId) {
      messages.value = []
      return
    }
    loadingMessages.value = true
    try {
      draftConversationOpen.value = false
      activeConversationId.value = conversationId
      activeGenerationMessageId.value = null
      messages.value = await listConversationMessages(conversationId)
      reconcileGenerationState()
      clearAgentSearchState()
    } finally {
      loadingMessages.value = false
    }
  }

  function openNewConversationDraft() {
    draftConversationOpen.value = true
    activeConversationId.value = null
    messages.value = []
    streamError.value = ''
    activeGenerationMessageId.value = null
    activeGenerationRequestId.value = null
    activeGenerationConversationId.value = null
    clearAgentSearchState()
  }

  async function sendMessage(content: string, options: SendMessageOptions = {}) {
    const message = content.trim()
    if (!message || streaming.value) return

    let conversationId = activeConversationId.value
    const userMessage = createLocalMessage(
      'USER',
      message,
      'TEXT',
      createGenerationRequestPayload(options.agentMode, options.generation),
    )
    const assistantMessage = createLocalMessage('ASSISTANT', '', messageTypeForMode(options.agentMode))

    messages.value.push(userMessage, assistantMessage)
    streaming.value = true
    streamError.value = ''
    latestChatContext.value = null
    clearAgentSearchState()
    activeGenerationMessageId.value = null
    abortController.value?.abort()
    abortController.value = new AbortController()
    const streamBuffer = createDisplayStreamBuffer(assistantMessage)
    activeStreamBuffer = streamBuffer
    let receivedGenerationResult = false

    try {
      await streamChat(
        {
          ...(conversationId ? {conversationId} : {}),
          message,
          ...(options.agentMode ? {agentMode: options.agentMode} : {}),
          ...(options.courseId ? {courseId: options.courseId} : {}),
          ...(options.generation ? {generation: options.generation} : {}),
        },
        {
          signal: abortController.value.signal,
          onChunk(chunk) {
            if (assistantMessage.messageType === 'TEXT') {
              streamBuffer.enqueue(chunk)
              return
            }
            if (receivedGenerationResult) return
            appendLocalMessageContent(assistantMessage.id, chunk)
          },
          onAgentSearch(event) {
            applyAgentSearchEvent(event)
          },
          onGenerationStage(event) {
            applyGenerationStageEvent(assistantMessage, event)
          },
          onGenerationResult(event) {
            receivedGenerationResult = true
            applyGenerationResultEvent(assistantMessage, event)
          },
          onContext(contextInfo) {
            latestChatContext.value = contextInfo
          },
          onConversation(conversation) {
            conversationId = conversation.id
            activeConversationId.value = conversation.id
            draftConversationOpen.value = false
            upsertConversationSummary(conversation)
          },
          onError(error) {
            streamError.value = error.message
          },
        },
      )
      await streamBuffer.drain()
      patchLocalMessage(assistantMessage.id, {pending: false})
      await loadConversations({silent: true})
      if (conversationId) {
        activeConversationId.value = conversationId
        activeGenerationConversationId.value = conversationId
      }
      if (assistantMessage.messageType !== 'TEXT' && conversationId) {
        await loadMessages(conversationId)
        activeGenerationMessageId.value = null
      }
    } catch (error) {
      streamBuffer.clear()
      const errorMessage = error instanceof Error ? error.message : 'AI response failed'
      patchLocalMessage(assistantMessage.id, {
        content: errorMessage,
        failed: true,
        pending: false,
      })
      streamError.value = errorMessage
      await refreshActiveGeneration()
    } finally {
      streamBuffer.clear()
      if (activeStreamBuffer === streamBuffer) {
        activeStreamBuffer = null
      }
      streaming.value = false
      abortController.value = null
    }
  }

  function stopStreaming() {
    abortController.value?.abort()
    abortController.value = null
    activeStreamBuffer?.clear()
    activeStreamBuffer = null
    if (streamFlushTimer) {
      window.clearInterval(streamFlushTimer)
      streamFlushTimer = null
    }
    streaming.value = false
    activeGenerationRequestId.value = null
    activeGenerationConversationId.value = null
  }

  function isGenerationMessage(message: ChatMessage) {
    return message.messageType === 'QUESTION_SET' || message.messageType === 'PAPER'
  }

  function applyAgentSearchEvent(event: AgentSearchEvent) {
    const normalizedEvent: AgentSearchEvent = {
      ...event,
      searchId: event.searchId || fallbackAgentSearchId(event),
      label: event.label || agentSearchFallbackLabel(event.phase),
      items: sanitizeAgentSearchItems(event.items),
    }

    if (normalizedEvent.phase !== 'completed') {
      activeAgentSearchSteps.value = activeAgentSearchSteps.value
        .filter((step) => step.domain !== normalizedEvent.domain)
        .concat(normalizedEvent)
        .slice(-5)
    }

    if (normalizedEvent.items?.length) {
      latestAgentSearchItems.value = normalizedEvent.items
    }
    attachAgentSearchToPendingMessage(normalizedEvent)
  }

  function sanitizeAgentSearchItems(items?: AgentSearchItem[]) {
    if (!items?.length) return []

    return items
      .filter(Boolean)
      .map((item) => ({
        sourceType: item.sourceType,
        sourceLabel: item.sourceLabel,
        sourceId: item.sourceId,
        courseId: item.courseId,
        title: item.title,
        contextLabel: item.contextLabel,
        snippet: item.snippet,
        relationLabel: item.relationLabel,
        metadata: item.metadata ?? null,
        indexInfo: item.indexInfo ?? null,
      }))
      .filter((item) => item.title || item.sourceLabel || item.snippet)
  }

  function attachAgentSearchToPendingMessage(event: AgentSearchEvent) {
    const message = [...messages.value]
      .reverse()
      .find((item) => item.pending && item.role.toLowerCase() !== 'user')
    if (!message) return

    const payload = {...(message.payload || {})}
    const agentSearch = normalizeAgentSearchPayload(payload.agentSearch)
    agentSearch.events = [...(agentSearch.events || []), event]
    agentSearch.searches = mergeAgentSearchRecord(agentSearch.searches || [], event)
    if (event.items?.length) {
      agentSearch.items = [...(agentSearch.items || []), ...event.items]
    }
    payload.agentSearch = agentSearch
    message.payload = payload
  }

  function normalizeAgentSearchPayload(value: unknown): AgentSearchPayload {
    if (!value || typeof value !== 'object') {
      return {events: [], searches: [], items: []}
    }
    const payload = value as AgentSearchPayload
    return {
      events: Array.isArray(payload.events) ? payload.events : [],
      searches: Array.isArray(payload.searches) ? payload.searches : [],
      items: Array.isArray(payload.items) ? payload.items : [],
    }
  }

  function mergeAgentSearchRecord(records: AgentSearchRecord[], event: AgentSearchEvent) {
    if (event.phase === 'completed') return records

    const searchId = event.searchId || fallbackAgentSearchId(event)
    const nextRecords = [...records]
    const index = nextRecords.findIndex((record) =>
      (record.searchId || fallbackAgentSearchId(record)) === searchId,
    )
    const nextRecord: AgentSearchRecord = {
      ...(index >= 0 ? nextRecords[index] : {}),
      searchId,
      domain: event.domain,
      query: event.query,
      label: event.label,
      phase: event.phase,
      total: event.total,
      occurredAt: event.occurredAt,
      items: event.items?.length ? event.items : index >= 0 ? nextRecords[index].items : [],
    }
    if (index >= 0) {
      nextRecords[index] = nextRecord
      return nextRecords
    }
    return [...nextRecords, nextRecord]
  }

  function fallbackAgentSearchId(event: Pick<AgentSearchRecord, 'domain' | 'query'>) {
    return `${event.domain || 'agentSearch'}:${event.query || ''}`
  }

  function agentSearchFallbackLabel(phase: string) {
    if (phase === 'empty') return '未找到匹配内容'
    if (phase === 'error') return '检索失败'
    return '正在搜集信息…'
  }

  function clearAgentSearchState() {
    activeAgentSearchSteps.value = []
    latestAgentSearchItems.value = []
  }

  function openGenerationTrace(messageId: string) {
    activeGenerationMessageId.value = messageId
  }

  function closeGenerationTrace() {
    activeGenerationMessageId.value = null
  }

  function applyGenerationStageEvent(message: ChatMessage, event: GenerationStageEvent) {
    const payload = {...(message.payload || {})}
    const trace = normalizeGenerationTrace(payload.generationTrace)
    const timestamp = event.timestamp || new Date().toISOString()
    const entryId = event.requestId
      ? `${event.requestId}-${event.stage}-${trace.length}`
      : `${message.id}-${event.stage}-${trace.length}`

    payload.generationTrace = trace.concat({
      entryId,
      stage: event.stage,
      source: 'question-generation',
      detailType: String(event.stage).toLowerCase(),
      title: event.title,
      summary: event.summary,
      payload: event.payload ?? null,
      timestamp,
    })
    payload.generationStage = event.stage
    payload.generationStatus = event.status
    payload.generationRequestId = event.requestId
    payload.generationMode = event.mode
    message.payload = payload
    activeGenerationRequestId.value = event.requestId || null
    if (activeConversationId.value) {
      activeGenerationConversationId.value = activeConversationId.value
    }
  }

  function applyGenerationResultEvent(message: ChatMessage, event: GenerationResultEvent) {
    const payload = {...(event.payload || {})}
    if (event.requestId) {
      payload.generationRequestId = event.requestId
    }
    if (event.mode) {
      payload.generationMode = event.mode
    }
    payload.generationStage ??= 'RESPONDED'
    payload.generationStatus ??= 'completed'
    patchLocalMessage(message.id, {
      content: event.content,
      messageType: event.messageType,
      payload,
      pending: false,
    })
    activeGenerationRequestId.value = event.requestId || null
    if (activeConversationId.value) {
      activeGenerationConversationId.value = activeConversationId.value
    }
  }

  async function refreshActiveGeneration() {
    if (!activeGenerationConversationId.value) return

    const keepTraceRequestId = activeGenerationRequestId.value
    await loadMessages(activeGenerationConversationId.value)
    const refreshedMessage = [...messages.value].reverse().find((message) =>
      isGenerationMessage(message)
      && (!keepTraceRequestId || message.payload?.generationRequestId === keepTraceRequestId),
    )
    activeGenerationMessageId.value = refreshedMessage?.id || null
    if (refreshedMessage && !refreshedMessage.pending) {
      activeGenerationRequestId.value = null
      activeGenerationConversationId.value = null
    }
  }

  function reconcileGenerationState() {
    if (streaming.value) return

    const pendingGeneration = [...messages.value].reverse().find((message) =>
      isGenerationMessage(message) && message.pending,
    )
    if (!pendingGeneration) {
      activeGenerationRequestId.value = null
      activeGenerationConversationId.value = null
      return
    }
    activeGenerationMessageId.value = pendingGeneration.id
    activeGenerationRequestId.value = typeof pendingGeneration.payload?.generationRequestId === 'string'
      ? pendingGeneration.payload.generationRequestId
      : null
    activeGenerationConversationId.value = activeConversationId.value
  }

  function normalizeGenerationTrace(value: unknown): GenerationTraceEntry[] {
    if (!Array.isArray(value)) return []

    return value.filter((entry): entry is GenerationTraceEntry =>
      Boolean(entry) && typeof entry === 'object',
    )
  }

  function patchLocalMessage(messageId: string, patch: Partial<ChatMessage>) {
    const message = messages.value.find((item) => item.id === messageId)
    if (!message) return

    Object.assign(message, patch)
  }

  function appendLocalMessageContent(messageId: string, content: string) {
    const message = messages.value.find((item) => item.id === messageId)
    if (!message) return

    message.content += content
  }

  function createDisplayStreamBuffer(message: ChatMessage) {
    let buffer = ''
    let drainResolve: (() => void) | null = null

    function flush() {
      if (!buffer) {
        if (drainResolve) {
          drainResolve()
          drainResolve = null
        }
        return
      }

      if (isGenerationMessage(message)) {
        appendLocalMessageContent(message.id, buffer)
        buffer = ''
        return
      }

      const take = Math.min(buffer.length, buffer.startsWith('\n') ? 1 : 3)
      appendLocalMessageContent(message.id, buffer.slice(0, take))
      buffer = buffer.slice(take)
    }

    function ensureTimer() {
      if (streamFlushTimer) return
      streamFlushTimer = window.setInterval(flush, STREAM_FLUSH_INTERVAL_MS)
    }

    return {
      enqueue(chunk: string) {
        buffer += chunk
        flush()
        ensureTimer()
      },
      drain() {
        if (!buffer) return Promise.resolve()

        ensureTimer()
        return new Promise<void>((resolve) => {
          drainResolve = resolve
        })
      },
      clear() {
        buffer = ''
        drainResolve?.()
        drainResolve = null
        if (streamFlushTimer) {
          window.clearInterval(streamFlushTimer)
          streamFlushTimer = null
        }
      },
    }
  }

  async function togglePinned(conversation: Conversation) {
    await updateConversation(conversation.id, {pinned: !conversation.pinned})
    await loadConversations()
  }

  async function toggleFavorited(conversation: Conversation) {
    await updateConversation(conversation.id, {favorited: !conversation.favorited})
    await loadConversations()
  }

  async function removeConversation(conversationId: string) {
    await deleteConversation(conversationId)
    if (activeConversationId.value === conversationId) {
      activeConversationId.value = null
      messages.value = []
    }
    await loadConversations()
  }

  async function loadKnowledgeDocs() {
    loadingKnowledgeDocs.value = true
    try {
      knowledgeDocs.value = await listKnowledgeDocs()
    } finally {
      loadingKnowledgeDocs.value = false
    }
  }

  async function ingestDoc(storageObjectId: string) {
    await ingestKnowledgeDoc({storageObjectId})
    notify.success('Document submitted to the AI knowledge base')
    await loadKnowledgeDocs()
  }

  async function removeKnowledgeDoc(docId: string) {
    await deleteKnowledgeDoc(docId)
    await loadKnowledgeDocs()
  }

  return {
    conversations,
    messages,
    knowledgeDocs,
    latestChatContext,
    activeAgentSearchSteps,
    latestAgentSearchItems,
    activeConversationId,
    activeConversation,
    context,
    loadingConversations,
    loadingMoreConversations,
    loadingMessages,
    loadingKnowledgeDocs,
    conversationsLoaded,
    hasMoreConversations,
    streaming,
    streamError,
    activeGenerationMessageId,
    activeGenerationMessage,
    sortedConversations,
    latestArtifact,
    setContext,
    ensureConversationsLoaded,
    loadConversations,
    loadMoreConversations,
    loadMessages,
    refreshActiveGeneration,
    openNewConversationDraft,
    sendMessage,
    stopStreaming,
    openGenerationTrace,
    closeGenerationTrace,
    togglePinned,
    toggleFavorited,
    removeConversation,
    loadKnowledgeDocs,
    ingestDoc,
    removeKnowledgeDoc,
  }
})
