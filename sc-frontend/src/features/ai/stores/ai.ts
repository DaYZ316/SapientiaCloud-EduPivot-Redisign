import {computed, ref} from 'vue'
import {defineStore} from 'pinia'

import {
  createConversation,
  deleteConversation,
  ingestKnowledgeDoc,
  listConversationMessages,
  listConversations,
  listKnowledgeDocs,
  streamChat,
  updateConversation,
} from '@/features/ai/api/ai'
import type {AiContext, ChatMessage, Conversation, KnowledgeDoc} from '@/features/ai/types/ai'
import {notify} from '@/shared/composables/useGlobalNotification'

function createLocalMessage(role: 'USER' | 'ASSISTANT', content: string): ChatMessage {
  return {
    id: `local-${role.toLowerCase()}-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    role,
    content,
    createdAt: new Date().toISOString(),
    pending: role === 'ASSISTANT',
  }
}

export const useAiStore = defineStore('ai', () => {
  const conversations = ref<Conversation[]>([])
  const messages = ref<ChatMessage[]>([])
  const knowledgeDocs = ref<KnowledgeDoc[]>([])
  const activeConversationId = ref<string | null>(null)
  const context = ref<AiContext>({sourceRoute: '/'})
  const loadingConversations = ref(false)
  const loadingMessages = ref(false)
  const loadingKnowledgeDocs = ref(false)
  const streaming = ref(false)
  const streamError = ref('')
  const abortController = ref<AbortController | null>(null)

  const activeConversation = computed(() =>
    conversations.value.find((conversation) => conversation.id === activeConversationId.value) || null,
  )

  const sortedConversations = computed(() =>
    [...conversations.value].sort((left, right) => {
      if (left.pinned !== right.pinned) return left.pinned ? -1 : 1
      return new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime()
    }),
  )

  function setContext(nextContext: AiContext) {
    context.value = nextContext
  }

  async function loadConversations() {
    loadingConversations.value = true
    try {
      conversations.value = await listConversations()
      if (!activeConversationId.value && conversations.value.length > 0) {
        activeConversationId.value = sortedConversations.value[0].id
        await loadMessages(activeConversationId.value)
      }
    } finally {
      loadingConversations.value = false
    }
  }

  async function loadMessages(conversationId = activeConversationId.value) {
    if (!conversationId) {
      messages.value = []
      return
    }
    loadingMessages.value = true
    try {
      activeConversationId.value = conversationId
      messages.value = await listConversationMessages(conversationId)
    } finally {
      loadingMessages.value = false
    }
  }

  async function ensureConversation() {
    if (activeConversationId.value) return activeConversationId.value
    const title = context.value.courseId ? '课程助手' : 'Celestial Hub'
    const id = await createConversation({title})
    await loadConversations()
    activeConversationId.value = id
    messages.value = []
    return id
  }

  async function startConversation(title = 'Celestial Hub') {
    const id = await createConversation({title})
    await loadConversations()
    activeConversationId.value = id
    messages.value = []
    return id
  }

  async function sendMessage(content: string) {
    const message = content.trim()
    if (!message || streaming.value) return

    const conversationId = await ensureConversation()
    const userMessage = createLocalMessage('USER', message)
    const assistantMessage = createLocalMessage('ASSISTANT', '')

    messages.value.push(userMessage, assistantMessage)
    streaming.value = true
    streamError.value = ''
    abortController.value?.abort()
    abortController.value = new AbortController()

    try {
      await streamChat(
        {conversationId, message},
        {
          signal: abortController.value.signal,
          onChunk(chunk) {
            assistantMessage.content += chunk
          },
          onError(error) {
            streamError.value = error.message
          },
        },
      )
      assistantMessage.pending = false
      await loadConversations()
    } catch (error) {
      assistantMessage.pending = false
      assistantMessage.failed = true
      streamError.value = error instanceof Error ? error.message : 'AI response failed'
    } finally {
      streaming.value = false
      abortController.value = null
    }
  }

  function stopStreaming() {
    abortController.value?.abort()
    abortController.value = null
    streaming.value = false
  }

  async function togglePinned(conversation: Conversation) {
    await updateConversation(conversation.id, {pinned: !conversation.pinned})
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
    notify.success('文档已提交入库')
    await loadKnowledgeDocs()
  }

  return {
    conversations,
    messages,
    knowledgeDocs,
    activeConversationId,
    activeConversation,
    context,
    loadingConversations,
    loadingMessages,
    loadingKnowledgeDocs,
    streaming,
    streamError,
    sortedConversations,
    setContext,
    loadConversations,
    loadMessages,
    startConversation,
    sendMessage,
    stopStreaming,
    togglePinned,
    removeConversation,
    loadKnowledgeDocs,
    ingestDoc,
  }
})
