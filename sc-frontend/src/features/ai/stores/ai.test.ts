import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {createPinia, setActivePinia} from 'pinia'

import {useAiStore} from '@/features/ai/stores/ai'
import type {Conversation, GenerationResultEvent} from '@/features/ai/types/ai'

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
    setActivePinia(createPinia())
    mocks.listConversationMessages.mockReset()
    mocks.listConversations.mockReset()
    mocks.streamChat.mockReset()
    mocks.notify.success.mockReset()
    mocks.listConversations.mockResolvedValue([])
    mocks.listConversationMessages.mockResolvedValue([])
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
})
