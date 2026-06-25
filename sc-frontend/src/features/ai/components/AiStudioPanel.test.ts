import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {createPinia, setActivePinia} from 'pinia'
import {nextTick} from 'vue'

import AiStudioPanel from '@/features/ai/components/AiStudioPanel.vue'
import {useAiStore} from '@/features/ai/stores/ai'
import type {ChatMessage} from '@/features/ai/types/ai'

const mocks = vi.hoisted(() => ({
  exportGeneratedArtifact: vi.fn(),
  getTeacherCourses: vi.fn(),
  getCourseQuestionBanks: vi.fn(),
  getQuestionBank: vi.fn(),
  batchCreateQuestions: vi.fn(),
  confirmDialog: vi.fn(),
  routerPush: vi.fn(),
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
  terminateGeneration: vi.fn(),
  updateConversation: vi.fn(),
}))

vi.mock('@/shared/composables/useGlobalNotification', () => ({
  notify: mocks.notify,
}))

vi.mock('@/shared/composables/useConfirmDialog', () => ({
  confirmDialog: mocks.confirmDialog,
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: mocks.routerPush,
  }),
}))

vi.mock('@/features/course/api/course', () => ({
  getTeacherCourses: mocks.getTeacherCourses,
}))

vi.mock('@/features/question-bank/api/questionBank', () => ({
  batchCreateQuestions: mocks.batchCreateQuestions,
  getCourseQuestionBanks: mocks.getCourseQuestionBanks,
  getQuestionBank: mocks.getQuestionBank,
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

describe('AiStudioPanel export actions', () => {
  beforeEach(() => {
    mocks.exportGeneratedArtifact.mockReset()
    mocks.getTeacherCourses.mockReset()
    mocks.getCourseQuestionBanks.mockReset()
    mocks.getQuestionBank.mockReset()
    mocks.batchCreateQuestions.mockReset()
    mocks.confirmDialog.mockReset()
    mocks.routerPush.mockReset()
    mocks.notify.success.mockReset()
    mocks.notify.error.mockReset()
    mocks.getTeacherCourses.mockResolvedValue({records: [course('course-1', 'Java Basics')]})
    mocks.getCourseQuestionBanks.mockResolvedValue([questionBank('bank-1', 'Java Bank', 'course-1')])
    mocks.getQuestionBank.mockResolvedValue(questionBank('bank-1', 'Java Bank', 'course-1'))
    mocks.batchCreateQuestions.mockResolvedValue({questionIds: ['question-1'], importedCount: 1})
    mocks.confirmDialog.mockResolvedValue(false)
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
    document.body.innerHTML = ''
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

  it('shows an export lock overlay while export is pending', async () => {
    let resolveExport!: (value: {blob: Blob; filename: string}) => void
    mocks.exportGeneratedArtifact.mockReturnValue(new Promise(resolve => {
      resolveExport = resolve
    }))
    const wrapper = mountPanel()

    await exportButtons(wrapper)[0].trigger('click')
    await nextTick()

    expect(wrapper.find('.export-lock-overlay').exists()).toBe(true)
    expect(wrapper.find('.export-lock-panel').text()).toContain('common.ai.studio.exportingPdf')
    expect(exportButtons(wrapper).slice(0, 2).every(button => button.attributes('disabled') !== undefined)).toBe(true)

    resolveExport({blob: new Blob(['pdf']), filename: 'paper.pdf'})
    await flushPromises()

    expect(wrapper.find('.export-lock-overlay').exists()).toBe(false)
  })

  it('shows the backend failure message when export fails', async () => {
    mocks.exportGeneratedArtifact.mockRejectedValue(new Error('PDF export failed: empty questions'))
    const wrapper = mountPanel()

    await exportButtons(wrapper)[1].trigger('click')
    await flushPromises()

    expect(mocks.exportGeneratedArtifact).toHaveBeenCalledWith('conversation-1', 'message-1', {
      format: 'docx',
      includeAnswers: true,
    })
    expect(mocks.notify.error).toHaveBeenCalledWith('PDF export failed: empty questions')
  })

  it('shows import action for generated question artifacts and opens with no questions selected', async () => {
    const wrapper = mountPanel(generatedMessage({
      messageType: 'QUESTION_SET',
      payload: {
        questions: [
          {questionTitle: 'Question A', questionType: 0, options: [{content: 'A', correct: true}]},
          {title: 'Question B', type: 4, answers: [{content: 'Answer B'}]},
        ],
      },
    }))

    await exportButtons(wrapper)[2].trigger('click')
    await flushPromises()

    expect(mocks.getTeacherCourses).toHaveBeenCalledWith(1, 100)
    expect(mocks.getCourseQuestionBanks).toHaveBeenCalledWith('course-1')
    expect(importDialog()).not.toBeNull()
    expect(importQuestionCheckboxes().map(input => input.checked)).toEqual([false, false])
    expect(importSubmitButton()?.disabled).toBe(true)
  })

  it('submits only selected generated questions to the target question bank', async () => {
    const wrapper = mountPanel(generatedMessage({
      payload: {
        questions: [
          {
            questionTitle: 'Question A',
            questionContent: 'Pick one.',
            questionType: 0,
            difficulty: 3,
            score: 2,
            options: [{label: 'A', content: 'Right', correct: true}],
          },
          {
            title: 'Question B',
            content: 'Explain briefly.',
            type: 4,
            answers: [{content: 'Because.'}],
          },
        ],
      },
    }))

    await exportButtons(wrapper)[2].trigger('click')
    await flushPromises()
    importQuestionCheckboxes()[1].click()
    await flushPromises()
    importSubmitButton()?.click()
    await flushPromises()

    expect(mocks.batchCreateQuestions).toHaveBeenCalledWith({
      questionBankId: 'bank-1',
      questions: [{
        questionTitle: 'Question B',
        questionContent: 'Explain briefly.',
        questionType: 4,
        difficulty: 2,
        score: 0,
        estimatedTime: 1,
        tags: [],
        imageUrls: [],
        allowPartialCredit: 0,
        options: [],
        answers: [{
          answerContent: 'Because.',
          explanation: undefined,
          score: 0,
          sortOrder: 1,
        }],
      }],
    })
    expect(mocks.confirmDialog).toHaveBeenCalledWith({
      title: 'common.ai.studio.importCompleteTitle',
      message: 'common.ai.studio.importCompleteMessage',
      cancelText: 'common.ai.studio.importStayHere',
      confirmText: 'common.ai.studio.importViewBank',
    })
    expect(mocks.routerPush).not.toHaveBeenCalled()
  })

  it('preselects the context question bank when it belongs to a teacher course', async () => {
    mocks.confirmDialog.mockResolvedValue(true)
    mocks.getTeacherCourses.mockResolvedValue({
      records: [
        course('course-1', 'Java Basics'),
        course('course-2', 'Data Structures'),
      ],
    })
    mocks.getQuestionBank.mockResolvedValue(questionBank('bank-2', 'Data Bank', 'course-2'))
    mocks.getCourseQuestionBanks.mockResolvedValue([
      questionBank('bank-3', 'Fallback Bank', 'course-2'),
      questionBank('bank-2', 'Data Bank', 'course-2'),
    ])
    const wrapper = mountPanel()
    useAiStore().setContext({sourceRoute: '/', courseId: 'course-2', questionBankId: 'bank-2'})

    await exportButtons(wrapper)[2].trigger('click')
    await flushPromises()
    importQuestionCheckboxes()[0].click()
    await flushPromises()
    importSubmitButton()?.click()
    await flushPromises()

    expect(mocks.getQuestionBank).toHaveBeenCalledWith('bank-2')
    expect(mocks.getCourseQuestionBanks).toHaveBeenCalledWith('course-2')
    expect(mocks.batchCreateQuestions).toHaveBeenCalledWith(expect.objectContaining({
      questionBankId: 'bank-2',
    }))
    expect(mocks.routerPush).toHaveBeenCalledWith({
      name: 'question-bank-detail',
      params: {id: 'bank-2'},
    })
  })

  it('keeps the import dialog open and shows an error when batch import fails', async () => {
    mocks.batchCreateQuestions.mockRejectedValue(new Error('Import failed'))
    const wrapper = mountPanel()

    await exportButtons(wrapper)[2].trigger('click')
    await flushPromises()
    importQuestionCheckboxes()[0].click()
    await flushPromises()
    importSubmitButton()?.click()
    await flushPromises()

    expect(importDialog()).not.toBeNull()
    expect(importQuestionCheckboxes()[0]?.checked).toBe(true)
    expect(mocks.confirmDialog).not.toHaveBeenCalled()
    expect(mocks.notify.error).toHaveBeenCalledWith('common.ai.studio.importFailed')
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
        BaseSelect: {
          props: ['modelValue', 'options', 'disabled'],
          emits: ['update:modelValue', 'change'],
          template: `
            <select
              class="base-select-stub"
              :disabled="disabled"
              :value="modelValue"
              @change="$emit('update:modelValue', $event.target.value); $emit('change')"
            >
              <option
                v-for="option in options"
                :key="String(option.value)"
                :value="option.value"
              >
                {{ option.label }}
              </option>
            </select>
          `,
        },
      },
    },
  })
}

function exportButtons(wrapper: ReturnType<typeof mountPanel>) {
  return wrapper.findAll('.export-actions button')
}

function importDialog() {
  return document.body.querySelector('.question-import-dialog')
}

function importQuestionCheckboxes() {
  return Array.from(document.body.querySelectorAll<HTMLInputElement>('.import-question-row input[type="checkbox"]'))
}

function importSubmitButton() {
  return document.body.querySelector<HTMLButtonElement>('.question-import-footer .btn-primary')
}

function course(id: string, title: string) {
  return {id, title}
}

function questionBank(id: string, bankName: string, courseId: string) {
  return {id, bankName, courseId}
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
