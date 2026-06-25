import {flushPromises, mount} from '@vue/test-utils'
import {beforeEach, describe, expect, it, vi} from 'vitest'

import LivePracticePanel from '@/features/live-practice/components/LivePracticePanel.vue'
import {createLivePractice, getLivePractice, listClassSessionLivePractices} from '@/features/live-practice/api/livePractice'
import type {LivePracticeGroup} from '@/features/live-practice/types/livePractice'
import {ClassLiveStatus, ClassSessionStatus, type ClassSession} from '@/features/course/types/classSession'
import {getQuestions} from '@/features/question-bank/api/questionBank'
import type {Question} from '@/features/question-bank/types/questionBank'

vi.mock('@/features/live-practice/api/livePractice', () => ({
  createLivePractice: vi.fn(),
  getLivePractice: vi.fn(),
  listClassSessionLivePractices: vi.fn(),
  submitLivePracticeAnswer: vi.fn(),
}))

vi.mock('@/features/question-bank/api/questionBank', () => ({
  getQuestion: vi.fn(),
  getQuestions: vi.fn(async () => ({records: [], total: 0, page: 1, size: 8})),
}))

vi.mock('@/shared/composables/useGlobalNotification', () => ({
  notify: {
    error: vi.fn(),
    success: vi.fn(),
    warn: vi.fn(),
  },
}))

const listClassSessionLivePracticesMock = vi.mocked(listClassSessionLivePractices)
const createLivePracticeMock = vi.mocked(createLivePractice)
const getLivePracticeMock = vi.mocked(getLivePractice)
const getQuestionsMock = vi.mocked(getQuestions)

describe('LivePracticePanel loading layout', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listClassSessionLivePracticesMock.mockResolvedValue([])
    getLivePracticeMock.mockResolvedValue(group())
    getQuestionsMock.mockResolvedValue({records: [], total: 0, page: 1, size: 8})
  })

  it('renders the teacher skeleton while the initial practice data is loading', async () => {
    let resolveGroups!: (groups: LivePracticeGroup[]) => void
    listClassSessionLivePracticesMock.mockReturnValue(new Promise<LivePracticeGroup[]>((resolve) => {
      resolveGroups = resolve
    }))

    const wrapper = mount(LivePracticePanel, {
      props: {
        session: session(),
        isTeacher: true,
      },
      global: {
        stubs: {
          BaseSelect: true,
          RichMathContent: true,
        },
      },
    })
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.panel-loading-layout').exists()).toBe(true)
    expect(wrapper.find('.practice-loading-teacher').exists()).toBe(true)
    expect(wrapper.findAll('.practice-skeleton').length).toBeGreaterThan(0)

    resolveGroups([])
    await flushPromises()
  })

  it('sends group AI grading settings when publishing a short answer practice', async () => {
    listClassSessionLivePracticesMock.mockResolvedValue([])
    createLivePracticeMock.mockResolvedValue('group-id')

    const wrapper = mount(LivePracticePanel, {
      props: {
        session: session(),
        isTeacher: true,
      },
      global: {
        stubs: {
          BaseSelect: true,
          RichMathContent: true,
        },
      },
    })
    await flushPromises()

    await wrapper.findComponent({name: 'BaseSelect'}).vm.$emit('update:modelValue', 4)
    await wrapper.find('.quick-question input[type="text"]').setValue('解释封装')
    await wrapper.find('.quick-question textarea').setValue('按要点给分')
    await wrapper.find('.quick-question .text-button').trigger('click')
    await wrapper.find('.ai-grading-box input[type="checkbox"]').setValue(true)
    await wrapper.find('.ai-grading-box textarea').setValue('按封装定义和作用给分')
    await wrapper.find('.primary-button').trigger('click')
    await flushPromises()

    expect(createLivePracticeMock).toHaveBeenCalledWith('session-id', expect.objectContaining({
      aiGradingEnabled: 1,
      aiGradingRequirement: '按封装定义和作用给分',
    }))
  })

  it('keeps AI grading available after paging away from a selected short answer question', async () => {
    listClassSessionLivePracticesMock.mockResolvedValue([])
    createLivePracticeMock.mockResolvedValue('group-id')
    getQuestionsMock
        .mockResolvedValueOnce({records: [bankQuestion('short-question', 4)], total: 16, page: 1, size: 8})
        .mockResolvedValueOnce({records: [bankQuestion('choice-question', 0)], total: 16, page: 2, size: 8})

    const wrapper = mount(LivePracticePanel, {
      props: {
        session: session(),
        isTeacher: true,
      },
      global: {
        stubs: {
          BaseSelect: true,
          RichMathContent: true,
        },
      },
    })
    await flushPromises()

    await wrapper.find('.question-check input[type="checkbox"]').setValue(true)
    await wrapper.findAll('.question-pagination .text-button')[1].trigger('click')
    await flushPromises()
    await wrapper.find('.ai-grading-box input[type="checkbox"]').setValue(true)
    await wrapper.find('.ai-grading-box textarea').setValue('按参考答案给分')
    await wrapper.find('.primary-button').trigger('click')
    await flushPromises()

    expect(createLivePracticeMock).toHaveBeenCalledWith('session-id', expect.objectContaining({
      aiGradingEnabled: 1,
      aiGradingRequirement: '按参考答案给分',
      selectedQuestionIds: ['short-question'],
    }))
  })

  it('renders AI grading status for a submitted answer', async () => {
    listClassSessionLivePracticesMock.mockResolvedValue([group()])

    const wrapper = mount(LivePracticePanel, {
      props: {
        session: session(),
        isTeacher: false,
      },
      global: {
        stubs: {
          BaseSelect: true,
          RichMathContent: true,
        },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('AI 已批改')
    expect(wrapper.text()).toContain('8 / 10 分')
    expect(wrapper.text()).toContain('要点较完整')
  })

  it('loads submitted text answer into the disabled answer field', async () => {
    listClassSessionLivePracticesMock.mockResolvedValue([group()])

    const wrapper = mount(LivePracticePanel, {
      props: {
        session: session(),
        isTeacher: false,
      },
      global: {
        stubs: {
          BaseSelect: true,
          RichMathContent: true,
        },
      },
    })
    await flushPromises()

    const textarea = wrapper.find('textarea')
    expect((textarea.element as HTMLTextAreaElement).value).toBe('封装隐藏内部实现。')
    expect(textarea.attributes('disabled')).toBeDefined()
  })
})

function session(): ClassSession {
  return {
    id: 'session-id',
    courseId: 'course-id',
    teacherId: 'teacher-id',
    title: 'Live Session',
    description: null,
    scheduledStartAt: '2026-06-24T10:00:00Z',
    scheduledEndAt: '2026-06-24T11:00:00Z',
    publishedAt: '2026-06-24T09:50:00Z',
    roomSize: 1,
    liveRoomName: 'class-session-session-id',
    liveStatus: ClassLiveStatus.LIVE,
    liveStatusText: 'live',
    liveStartedAt: '2026-06-24T10:00:00Z',
    livePausedAt: null,
    liveEndedAt: null,
    status: ClassSessionStatus.LIVE,
    statusText: 'live',
    joined: true,
    createdAt: '2026-06-24T09:00:00Z',
    updatedAt: '2026-06-24T10:00:00Z',
  }
}

function bankQuestion(id: string, questionType: number): Question {
  return {
    id,
    questionBankId: 'bank-id',
    courseId: 'course-id',
    sysUserId: 'teacher-id',
    questionTitle: questionType === 4 ? '解释封装' : '选择题',
    questionContent: null,
    questionType,
    difficulty: 2,
    score: 10,
    estimatedTime: null,
    tags: null,
    imageUrls: null,
    allowPartialCredit: 0,
    viewCount: 0,
    status: 1,
    options: null,
    answers: null,
    createdAt: '2026-06-24T09:00:00Z',
    updatedAt: '2026-06-24T09:00:00Z',
  }
}

function group(): LivePracticeGroup {
  return {
    id: 'group-id',
    courseId: 'course-id',
    classSessionId: 'session-id',
    teacherId: 'teacher-id',
    title: 'Live Practice',
    availableStartAt: '2026-06-24T10:00:00Z',
    availableEndAt: '2026-06-24T10:30:00Z',
    allowLateSubmission: 0,
    aiGradingEnabled: 1,
    aiGradingRequirement: '按封装定义和作用给分',
    publishOrder: 1,
    publishedAt: '2026-06-24T09:50:00Z',
    totalQuestions: 1,
    submittedStudents: 1,
    totalStudents: 1,
    questions: [{
      id: 'question-id',
      groupId: 'group-id',
      sourceQuestionId: null,
      questionOrder: 1,
      questionTitle: '解释封装',
      questionContent: '请解释封装。',
      questionType: 4,
      difficulty: 2,
      score: 10,
      estimatedTime: null,
      tags: null,
      imageUrls: null,
      allowPartialCredit: 0,
      options: null,
      answers: null,
      aiGradingEnabled: 1,
      mySubmission: {
        id: 'submission-id',
        groupId: 'group-id',
        questionSnapshotId: 'question-id',
        courseId: 'course-id',
        classSessionId: 'session-id',
        studentId: 'student-id',
        studentName: 'Student',
        studentAvatarUrl: null,
        selectedOptionIds: null,
        textAnswer: '封装隐藏内部实现。',
        submitStatus: 1,
        submitStatusText: 'submitted',
        isCorrect: 0,
        earnedScore: 8,
        aiGradingStatus: 'COMPLETED',
        aiGradingFeedback: '要点较完整',
        aiGradingError: null,
        aiGradedAt: '2026-06-24T10:02:00Z',
        submittedAt: '2026-06-24T10:01:00Z',
      },
      analysis: null,
      createdAt: '2026-06-24T10:00:00Z',
    }],
  }
}
