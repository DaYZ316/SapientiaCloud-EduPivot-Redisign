import {flushPromises, mount} from '@vue/test-utils'
import {createPinia, setActivePinia} from 'pinia'
import {describe, expect, it, vi} from 'vitest'
import {reactive} from 'vue'

import AiWorkspaceView from '@/features/ai/views/AiWorkspaceView.vue'
import {useAiStore} from '@/features/ai/stores/ai'

const route = reactive({
  fullPath: '/ai?conversationId=conversation-a',
  query: {
    conversationId: 'conversation-a',
  } as Record<string, unknown>,
})

const mocks = vi.hoisted(() => ({
  listConversationMessages: vi.fn(),
  listConversations: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => route,
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

vi.mock('@/app/i18n', () => ({
  i18n: {
    global: {
      t: (key: string) => key,
    },
  },
}))

vi.mock('@/features/ai/api/ai', () => ({
  deleteConversation: vi.fn(),
  deleteKnowledgeDoc: vi.fn(),
  ingestKnowledgeDoc: vi.fn(),
  listConversationMessages: mocks.listConversationMessages,
  listConversations: mocks.listConversations,
  listKnowledgeDocs: vi.fn(),
  streamChat: vi.fn(),
  subscribeGenerationProgress: vi.fn(),
  terminateGeneration: vi.fn(),
  updateConversation: vi.fn(),
}))

vi.mock('@/shared/composables/useGlobalNotification', () => ({
  notify: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

describe('AiWorkspaceView', () => {
  it('loads the conversation from the route query', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    mocks.listConversations.mockResolvedValue([])
    mocks.listConversationMessages.mockResolvedValue([
      {
        id: 'message-a',
        role: 'ASSISTANT',
        content: 'Previous answer',
        messageType: 'TEXT',
        createdAt: '2026-06-24T00:00:00.000Z',
      },
    ])

    mount(AiWorkspaceView, {
      global: {
        plugins: [pinia],
        stubs: {
          AiChatPanel: true,
          AiStudioPanel: true,
        },
      },
    })
    await flushPromises()

    const store = useAiStore()
    expect(mocks.listConversationMessages).toHaveBeenCalledWith('conversation-a')
    expect(store.activeConversationId).toBe('conversation-a')
    expect(store.messages).toHaveLength(1)
    expect(store.messages[0].content).toBe('Previous answer')
  })
})
