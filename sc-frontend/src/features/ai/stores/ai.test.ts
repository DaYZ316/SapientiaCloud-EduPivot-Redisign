import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {createPinia, setActivePinia} from 'pinia'

import {useAiStore} from '@/features/ai/stores/ai'
import type {Conversation, GenerationResultEvent, GenerationStageEvent} from '@/features/ai/types/ai'

const mocks = vi.hoisted(() => ({
  listConversationMessages: vi.fn(),
  listConversations: vi.fn(),
  streamChat: vi.fn(),
  notify: {
    success: vi.fn(),
  },
}))

vi.mock('@/features/ai/api/ai', () => ({
  deleteConversation: vi.fn(),
  deleteKnowledgeDoc: vi.fn(),
  ingestKnowledgeDoc: vi.fn(),
  listConversationMessages: mocks.listConversationMessages,
  listConversations: mocks.listConversations,
  listKnowledgeDocs: vi.fn(),
  streamChat: mocks.streamChat,
  updateConversation: vi.fn(),
}))

vi.mock('@/shared/composables/useGlobalNotification', () => ({
  notify: mocks.notify,
}))

describe('useAiStore generation stream', () => {
  beforeEach(() => {
    vi.useRealTimers()
    setActivePinia(createPinia())
    mocks.listConversationMessages.mockReset()
    mocks.listConversations.mockReset()
    mocks.streamChat.mockReset()
    mocks.notify.success.mockReset()
    mocks.listConversations.mockResolvedValue([])
    mocks.listConversationMessages.mockResolvedValue([])
  })

  it('restores pending generation message from persisted payload', async () => {
    const store = useAiStore()
    mocks.listConversationMessages.mockResolvedValue([
      {
        id: 'message-1',
        role: 'ASSISTANT',
        content: '',
        createdAt: new Date().toISOString(),
        messageType: 'PAPER',
        payload: {
          generationRequestId: 'request-1',
          generationStatus: 'processing',
          generationStage: 'PLANNED',
        },
      },
    ])

    await store.loadMessages('conversation-1')

    expect(store.messages[0].pending).toBe(true)
    expect(store.messages[0].failed).toBe(false)
    expect(store.activeGenerationMessageId).toBe('message-1')
    expect(store.activeGenerationMessage?.pending).toBe(true)
    store.openNewConversationDraft()
  })

  it('does not poll restored pending generation messages', async () => {
    vi.useFakeTimers()
    const store = useAiStore()
    mocks.listConversationMessages.mockResolvedValue([
      {
        id: 'message-1',
        role: 'ASSISTANT',
        content: '',
        createdAt: new Date().toISOString(),
        messageType: 'PAPER',
        payload: {
          generationRequestId: 'request-1',
          generationStatus: 'processing',
          generationStage: 'PLANNED',
        },
      },
    ])

    await store.loadMessages('conversation-1')
    await vi.advanceTimersByTimeAsync(5000)
    await flushPromises()

    expect(mocks.listConversationMessages).toHaveBeenCalledTimes(1)
    expect(store.messages[0].pending).toBe(true)
    expect(store.activeGenerationMessageId).toBe('message-1')
    store.openNewConversationDraft()
    vi.useRealTimers()
  })

  it('updates PAPER artifact payload from generation_result before message reload', async () => {
    let finishStream: (() => void) | undefined
    const result: GenerationResultEvent = {
      requestId: 'request-1',
      mode: 'PAPER',
      content: 'paper content',
      messageType: 'PAPER',
      payload: {
        questions: [{questionTitle: 'HashMap load factor'}],
      },
    }
    mocks.streamChat.mockImplementation(async (
      _data: unknown,
      handlers: {
        onChunk?: (chunk: string) => void
        onConversation?: (conversation: Pick<Conversation, 'id' | 'title'>) => void
        onGenerationResult?: (event: GenerationResultEvent) => void
      },
    ) => {
      handlers.onConversation?.({id: 'conversation-1', title: 'Paper'})
      handlers.onGenerationResult?.(result)
      handlers.onChunk?.('paper content')
      await new Promise<void>((resolve) => {
        finishStream = resolve
      })
    })
    const store = useAiStore()

    const pending = store.sendMessage('generate paper', {agentMode: 'PAPER'})
    await flushPromises()

    const artifact = store.messages.find(message => message.messageType === 'PAPER')
    expect(artifact?.content).toBe('paper content')
    expect(artifact?.pending).toBe(false)
    expect(artifact?.payload?.questions).toEqual([{questionTitle: 'HashMap load factor'}])
    expect(artifact?.payload?.generationRequestId).toBe('request-1')
    expect(mocks.listConversationMessages).not.toHaveBeenCalled()

    finishStream?.()
    await pending
  })

  it('promotes generated stage questions before generation_result arrives', async () => {
    let finishStream: (() => void) | undefined
    const firstGeneratedStage: GenerationStageEvent = {
      requestId: 'request-1',
      mode: 'PAPER',
      stage: 'GENERATED',
      status: 'processing',
      title: '题目草稿生成中',
      summary: '已生成 1 / 2 道题目草稿。',
      payload: {
        questionCount: 1,
        generatedQuestionCount: 1,
        totalQuestionCount: 2,
        questionDelta: true,
        questions: [{questionTitle: 'HashMap load factor'}],
      },
    }
    const secondGeneratedStage: GenerationStageEvent = {
      ...firstGeneratedStage,
      summary: '已生成 2 / 2 道题目草稿。',
      payload: {
        questionCount: 1,
        generatedQuestionCount: 2,
        totalQuestionCount: 2,
        questionDelta: true,
        questions: [{questionTitle: 'ConcurrentHashMap segment'}],
      },
    }
    mocks.streamChat.mockImplementation(async (
      _data: unknown,
      handlers: {
        onConversation?: (conversation: Pick<Conversation, 'id' | 'title'>) => void
        onGenerationStage?: (event: GenerationStageEvent) => void
      },
    ) => {
      handlers.onConversation?.({id: 'conversation-1', title: 'Paper'})
      handlers.onGenerationStage?.(firstGeneratedStage)
      handlers.onGenerationStage?.(secondGeneratedStage)
      await new Promise<void>((resolve) => {
        finishStream = resolve
      })
    })
    const store = useAiStore()

    const pending = store.sendMessage('generate paper', {agentMode: 'PAPER'})
    await flushPromises()

    const artifact = store.messages.find(message => message.messageType === 'PAPER')
    expect(artifact?.pending).toBe(true)
    expect(artifact?.payload?.questions).toEqual([
      {questionTitle: 'HashMap load factor'},
      {questionTitle: 'ConcurrentHashMap segment'},
    ])
    expect(artifact?.payload?.generationTrace).toHaveLength(2)

    finishStream?.()
    await pending
  })

  it('promotes assembled full questions after an empty generated stage', async () => {
    let finishStream: (() => void) | undefined
    const emptyGeneratedStage: GenerationStageEvent = {
      requestId: 'request-1',
      mode: 'PAPER',
      stage: 'GENERATED',
      status: 'processing',
      payload: {
        questionCount: 0,
        questionDelta: true,
        questions: [],
      },
    }
    const assembledStage: GenerationStageEvent = {
      requestId: 'request-1',
      mode: 'PAPER',
      stage: 'ASSEMBLED',
      status: 'processing',
      payload: {
        questionCount: 1,
        questions: [{questionTitle: 'Recovered final question'}],
      },
    }
    mocks.streamChat.mockImplementation(async (
      _data: unknown,
      handlers: {
        onConversation?: (conversation: Pick<Conversation, 'id' | 'title'>) => void
        onGenerationStage?: (event: GenerationStageEvent) => void
      },
    ) => {
      handlers.onConversation?.({id: 'conversation-1', title: 'Paper'})
      handlers.onGenerationStage?.(emptyGeneratedStage)
      handlers.onGenerationStage?.(assembledStage)
      await new Promise<void>((resolve) => {
        finishStream = resolve
      })
    })
    const store = useAiStore()

    const pending = store.sendMessage('generate paper', {agentMode: 'PAPER'})
    await flushPromises()

    const artifact = store.messages.find(message => message.messageType === 'PAPER')
    expect(artifact?.payload?.questions).toEqual([{questionTitle: 'Recovered final question'}])

    finishStream?.()
    await pending
  })
})
