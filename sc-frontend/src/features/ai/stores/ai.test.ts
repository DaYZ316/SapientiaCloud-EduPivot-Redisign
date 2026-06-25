import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {createPinia, setActivePinia} from 'pinia'

import {useAiStore} from '@/features/ai/stores/ai'
import type {AgentSearchEvent, Conversation, GenerationResultEvent, GenerationStageEvent} from '@/features/ai/types/ai'

const mocks = vi.hoisted(() => ({
  listConversationMessages: vi.fn(),
  listConversations: vi.fn(),
  streamChat: vi.fn(),
  subscribeGenerationProgress: vi.fn(),
  terminateGeneration: vi.fn(),
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
  subscribeGenerationProgress: mocks.subscribeGenerationProgress,
  terminateGeneration: mocks.terminateGeneration,
  updateConversation: vi.fn(),
}))

vi.mock('@/shared/composables/useGlobalNotification', () => ({
  notify: mocks.notify,
}))

function conversation(id: string): Conversation {
  return {
    id,
    title: id,
    pinned: false,
    favorited: false,
    createdAt: '2026-06-24T00:00:00.000Z',
    updatedAt: '2026-06-24T00:00:00.000Z',
  }
}

describe('useAiStore generation stream', () => {
  beforeEach(() => {
    vi.useRealTimers()
    setActivePinia(createPinia())
    mocks.listConversationMessages.mockReset()
    mocks.listConversations.mockReset()
    mocks.streamChat.mockReset()
    mocks.subscribeGenerationProgress.mockReset()
    mocks.terminateGeneration.mockReset()
    mocks.notify.success.mockReset()
    mocks.listConversations.mockResolvedValue([])
    mocks.listConversationMessages.mockResolvedValue([])
    mocks.subscribeGenerationProgress.mockResolvedValue(undefined)
    mocks.terminateGeneration.mockResolvedValue(undefined)
  })

  it('reloads conversations after session state is reset', async () => {
    const store = useAiStore()
    mocks.listConversations.mockResolvedValueOnce([conversation('previous-user-conversation')])

    await store.ensureConversationsLoaded()

    expect(store.conversations.map(item => item.id)).toEqual(['previous-user-conversation'])
    expect(store.conversationsLoaded).toBe(true)

    store.resetSessionState()
    mocks.listConversations.mockResolvedValueOnce([conversation('current-user-conversation')])

    await store.ensureConversationsLoaded()

    expect(store.conversations.map(item => item.id)).toEqual(['current-user-conversation'])
    expect(mocks.listConversations).toHaveBeenCalledTimes(2)
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

  it('subscribes restored pending generation messages', async () => {
    const store = useAiStore()
    let finishProgress: (() => void) | undefined
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
          generationTrace: [],
        },
      },
    ])
    mocks.subscribeGenerationProgress.mockImplementation(async (
      _conversationId: string,
      _messageId: string,
      handlers: {
        onGenerationStage?: (event: GenerationStageEvent) => void
      },
    ) => {
      handlers.onGenerationStage?.({
        requestId: 'request-1',
        mode: 'PAPER',
        stage: 'GENERATED',
        status: 'processing',
        payload: {
          questionDelta: true,
          questions: [{questionTitle: 'HashMap load factor'}],
        },
      })
      await new Promise<void>((resolve) => {
        finishProgress = resolve
      })
    })

    await store.loadMessages('conversation-1')
    await flushPromises()

    expect(mocks.listConversationMessages).toHaveBeenCalledTimes(1)
    expect(mocks.subscribeGenerationProgress).toHaveBeenCalledWith(
      'conversation-1',
      'message-1',
      expect.objectContaining({
        signal: expect.any(AbortSignal),
      }),
    )
    expect(store.messages[0].pending).toBe(true)
    expect(store.activeGenerationMessageId).toBe('message-1')
    expect(store.messages[0].payload?.questions).toEqual([{questionTitle: 'HashMap load factor'}])
    expect(store.messages[0].payload?.generationTrace).toHaveLength(1)
    finishProgress?.()
    store.openNewConversationDraft()
  })

  it('aborts restored generation subscription when switching conversations', async () => {
    const store = useAiStore()
    let firstSignal: AbortSignal | undefined
    mocks.listConversationMessages.mockResolvedValueOnce([
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
    ]).mockResolvedValueOnce([])
    mocks.subscribeGenerationProgress.mockImplementation(async (
      _conversationId: string,
      _messageId: string,
      handlers: {signal?: AbortSignal},
    ) => {
      firstSignal = handlers.signal
      await new Promise<void>(() => {})
    })

    await store.loadMessages('conversation-1')
    await flushPromises()
    await store.loadMessages('conversation-2')

    expect(firstSignal?.aborted).toBe(true)
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

  it('deduplicates raw AI output events in generation debug trace', async () => {
    let finishStream: (() => void) | undefined
    const rawOutput = '{"questions":[]}'
    const rawStage: GenerationStageEvent = {
      requestId: 'request-1',
      mode: 'PAPER',
      stage: 'GENERATED',
      status: 'processing',
      title: 'raw output',
      summary: 'model returned raw output',
      payload: {
        detailType: 'raw_ai_output',
        callType: 'section_generation',
        rawOutput,
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
      handlers.onGenerationStage?.(rawStage)
      handlers.onGenerationStage?.(rawStage)
      await new Promise<void>((resolve) => {
        finishStream = resolve
      })
    })
    const store = useAiStore()

    const pending = store.sendMessage('generate paper', {agentMode: 'PAPER'})
    await flushPromises()

    const artifact = store.messages.find(message => message.messageType === 'PAPER')
    const debugTrace = artifact?.payload?.generationDebugTrace as Array<{payload?: Record<string, unknown>}> | undefined
    const visibleTrace = artifact?.payload?.generationTrace as unknown[] | undefined
    expect(debugTrace).toHaveLength(1)
    expect(debugTrace?.[0]?.payload?.rawOutput).toBe(rawOutput)
    expect(visibleTrace ?? []).toHaveLength(0)

    finishStream?.()
    await pending
  })

  it('terminates generation and ignores a late generation_result', async () => {
    let handlersRef: {
      onConversation?: (conversation: Pick<Conversation, 'id' | 'title'>) => void
      onGenerationStage?: (event: GenerationStageEvent) => void
      onGenerationResult?: (event: GenerationResultEvent) => void
      signal?: AbortSignal
    } | undefined
    mocks.streamChat.mockImplementation(async (
      _data: unknown,
      handlers: NonNullable<typeof handlersRef>,
    ) => {
      handlersRef = handlers
      handlers.onConversation?.({id: 'conversation-1', title: 'Paper'})
      handlers.onGenerationStage?.({
        messageId: 'message-1',
        requestId: 'request-1',
        mode: 'PAPER',
        stage: 'GENERATED',
        status: 'processing',
        payload: {questions: [{questionTitle: 'Draft'}]},
      })
      await new Promise<void>((resolve) => {
        handlers?.signal?.addEventListener('abort', () => resolve(), {once: true})
      })
    })
    const store = useAiStore()

    void store.sendMessage('generate paper', {agentMode: 'PAPER'})
    await flushPromises()

    store.stopStreaming()
    handlersRef?.onGenerationResult?.({
      messageId: 'message-1',
      requestId: 'request-1',
      mode: 'PAPER',
      content: 'late paper content',
      messageType: 'PAPER',
      payload: {questions: [{questionTitle: 'Late'}]},
    })

    const artifact = store.messages.find(message => message.id === 'message-1')
    expect(mocks.terminateGeneration).toHaveBeenCalledWith('conversation-1', 'message-1')
    expect(artifact?.pending).toBe(false)
    expect(artifact?.terminated).toBe(true)
    expect(artifact?.content).toBe('用户已终止本次任务。')
    expect(artifact?.payload?.generationStage).toBe('TERMINATED')
    expect(artifact?.payload?.questions).toEqual([{questionTitle: 'Draft'}])
  })

  it('keeps agent search status metadata on pending chat messages', async () => {
    let finishStream: (() => void) | undefined
    const agentSearchEvent: AgentSearchEvent = {
      searchId: 'web-1',
      phase: 'error',
      domain: 'web',
      label: '联网搜索未配置',
      query: 'Palworld 1.0',
      total: 0,
      items: [],
      status: 'MISCONFIGURED',
      reason: '缺少联网搜索 endpoint 或 api key',
      provider: 'tavily-compatible',
      durationMs: 12,
      retryable: false,
    }
    mocks.streamChat.mockImplementation(async (
      _data: unknown,
      handlers: {
        onAgentSearch?: (event: AgentSearchEvent) => void
        onChunk?: (chunk: string) => void
        onConversation?: (conversation: Pick<Conversation, 'id' | 'title'>) => void
      },
    ) => {
      handlers.onConversation?.({id: 'conversation-1', title: 'Chat'})
      handlers.onAgentSearch?.(agentSearchEvent)
      handlers.onChunk?.('暂时无法联网搜索。')
      await new Promise<void>((resolve) => {
        finishStream = resolve
      })
    })
    const store = useAiStore()

    const pending = store.sendMessage('联网搜索幻兽帕鲁V1.0什么时候出')
    await flushPromises()

    const assistant = store.messages.find(message => message.role === 'ASSISTANT')
    const agentSearch = assistant?.payload?.agentSearch as {searches?: AgentSearchEvent[]}
    expect(agentSearch.searches?.[0]).toEqual(expect.objectContaining({
      status: 'MISCONFIGURED',
      reason: '缺少联网搜索 endpoint 或 api key',
      provider: 'tavily-compatible',
      durationMs: 12,
      retryable: false,
    }))
    expect(store.activeAgentSearchSteps[0]).toEqual(expect.objectContaining({
      status: 'MISCONFIGURED',
      reason: '缺少联网搜索 endpoint 或 api key',
    }))

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
        totalQuestionCount: 3,
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
        totalQuestionCount: 3,
        questionDelta: true,
        questions: [{questionTitle: 'ConcurrentHashMap segment'}],
      },
    }
    const thirdGeneratedStage: GenerationStageEvent = {
      ...firstGeneratedStage,
      payload: {
        questionCount: 1,
        generatedQuestionCount: 3,
        totalQuestionCount: 3,
        questionDelta: true,
        questions: [{questionTitle: 'TreeMap ordering'}],
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
      handlers.onGenerationStage?.(thirdGeneratedStage)
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
      {questionTitle: 'TreeMap ordering'},
    ])
    expect(artifact?.payload?.generationTrace).toHaveLength(3)

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
