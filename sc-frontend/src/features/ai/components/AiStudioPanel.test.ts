import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {createPinia, setActivePinia} from 'pinia'

import AiStudioPanel from '@/features/ai/components/AiStudioPanel.vue'
import {useAiStore} from '@/features/ai/stores/ai'
import type {ChatMessage} from '@/features/ai/types/ai'

const mocks = vi.hoisted(() => ({
  exportGeneratedArtifact: vi.fn(),
  notify: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

vi.mock('@/features/ai/api/ai', () => ({
  deleteConversation: vi.fn(),
  deleteKnowledgeDoc: vi.fn(),
  exportGeneratedArtifact: mocks.exportGeneratedArtifact,
  ingestKnowledgeDoc: vi.fn(),
  listConversationMessages: vi.fn(),
  listConversations: vi.fn(),
  listKnowledgeDocs: vi.fn(),
  streamChat: vi.fn(),
  updateConversation: vi.fn(),
}))

vi.mock('@/shared/composables/useGlobalNotification', () => ({
  notify: mocks.notify,
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

describe('AiStudioPanel export actions', () => {
  beforeEach(() => {
    mocks.exportGeneratedArtifact.mockReset()
    mocks.notify.success.mockReset()
    mocks.notify.error.mockReset()
    vi.stubGlobal('URL', {
      ...URL,
      createObjectURL: vi.fn(() => 'blob:generated-artifact'),
      revokeObjectURL: vi.fn(),
    })
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('exports PDF with includeAnswers following the answer toggle', async () => {
    const blob = new Blob(['pdf'])
    mocks.exportGeneratedArtifact.mockResolvedValue({blob, filename: 'paper.pdf'})
    const wrapper = mountPanel()

    await wrapper.findAll('.answer-toggle button')[0].trigger('click')
    await exportButtons(wrapper)[0].trigger('click')
    await flushPromises()

    expect(mocks.exportGeneratedArtifact).toHaveBeenCalledWith('conversation-1', 'message-1', {
      format: 'pdf',
      includeAnswers: false,
    })
    expect(mocks.notify.success).toHaveBeenCalledWith('common.ai.studio.exportSuccess')
  })

  it('keeps export buttons disabled while generated content cannot be exported', () => {
    for (const message of [
      generatedMessage({pending: true}),
      generatedMessage({failed: true}),
      generatedMessage({payload: {questions: []}}),
    ]) {
      const wrapper = mountPanel(message)

      expect(wrapper.find('.export-actions').exists()).toBe(true)
      expect(exportButtons(wrapper).every(button => button.attributes('disabled') !== undefined)).toBe(true)

      wrapper.unmount()
    }
  })

  it('does not show export buttons for non-generated artifact messages', () => {
    const wrapper = mountPanel(generatedMessage({messageType: 'GRADING_RESULT'}))

    expect(wrapper.find('.export-actions').exists()).toBe(false)
  })

  it('shows the existing failure notification when export fails', async () => {
    mocks.exportGeneratedArtifact.mockRejectedValue(new Error('Export failed'))
    const wrapper = mountPanel()

    await exportButtons(wrapper)[1].trigger('click')
    await flushPromises()

    expect(mocks.exportGeneratedArtifact).toHaveBeenCalledWith('conversation-1', 'message-1', {
      format: 'docx',
      includeAnswers: true,
    })
    expect(mocks.notify.error).toHaveBeenCalledWith('common.ai.studio.exportFailed')
  })
})

function mountPanel(message = generatedMessage()) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = useAiStore()
  store.activeConversationId = 'conversation-1'

  return mount(AiStudioPanel, {
    props: {
      generation: {},
      traceMessage: message,
    },
    global: {
      plugins: [pinia],
      stubs: {
        AiGenerationTracePanel: true,
        AiMarkdownMessage: {
          props: ['content'],
          template: '<div class="ai-markdown-stub">{{ content }}</div>',
        },
        BaseNumberStepper: true,
        BaseSelect: true,
      },
    },
  })
}

function exportButtons(wrapper: ReturnType<typeof mountPanel>) {
  return wrapper.findAll('.export-actions button')
}

function generatedMessage(overrides: Partial<ChatMessage> = {}): ChatMessage {
  const payload = {
    questions: [{
      questionTitle: 'HashMap load factor',
      questionContent: 'Choose the correct answer.',
      options: [
        {optionLabel: 'A', optionContent: '0.75', isCorrect: true},
      ],
      answers: [
        {answerContent: 'A'},
      ],
      explanation: 'The default load factor is 0.75.',
    }],
  }

  return {
    id: 'message-1',
    role: 'ASSISTANT',
    content: '',
    messageType: 'PAPER',
    createdAt: '2026-06-21T10:00:00Z',
    ...overrides,
    payload: Object.hasOwn(overrides, 'payload') ? overrides.payload : payload,
  }
}
