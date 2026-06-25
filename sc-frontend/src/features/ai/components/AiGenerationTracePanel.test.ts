import {flushPromises, mount} from '@vue/test-utils'
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {nextTick} from 'vue'
import {createPinia, setActivePinia} from 'pinia'

import AiGenerationTracePanel from '@/features/ai/components/AiGenerationTracePanel.vue'
import {i18n, setLocale} from '@/app/i18n'
import {useAuthStore} from '@/features/auth/stores/auth'
import type {ChatMessage} from '@/features/ai/types/ai'
import type {UserProfile} from '@/features/user/types/user'

describe('AiGenerationTracePanel', () => {
  beforeEach(() => {
    vi.useRealTimers()
    localStorage.clear()
    setLocale('zh-CN')
    setActivePinia(createPinia())
    Element.prototype.scrollTo = vi.fn()
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => {
      callback(0)
      return 1
    })
    vi.stubGlobal('cancelAnimationFrame', vi.fn())
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('renders web search sources as external source rows', () => {
    const wrapper = mountTracePanel(generationMessage(), {admin: false})

    const links = wrapper.findAll('a.trace-web-source')
    expect(links).toHaveLength(2)
    expect(links[0].attributes('href')).toBe('https://www.example.com/java-arrays')
    expect(links[0].attributes('target')).toBe('_blank')
    expect(links[0].attributes('rel')).toBe('noopener noreferrer')
    expect(links[0].text()).toContain('example.com')
    expect(links[0].text()).toContain('Java arrays guide')
    expect(links[0].find('img.trace-web-source__icon').attributes('src')).toBe('https://www.example.com/favicon.ico')
    expect(links[1].text()).toContain('docs.example.org')
    expect(links[1].text()).toContain('Array exercises')
    expect(wrapper.find('.trace-developer').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Duplicate raw evidence')
  })

  it('paces fast pending trace entries instead of rendering them all at once', async () => {
    vi.useFakeTimers()
    const wrapper = mountTracePanel(pendingGenerationMessage())

    expect(wrapper.findAll('.trace-step')).toHaveLength(1)
    expect(wrapper.text()).toContain('确认生成要求')
    expect(wrapper.text()).not.toContain('准备参考资�?)

    await vi.advanceTimersByTimeAsync(600)
    await nextTick()

    expect(wrapper.findAll('.trace-step')).toHaveLength(2)
    expect(wrapper.text()).toContain('准备参考资�?)
    expect(wrapper.text()).not.toContain('规划试卷结构')

    await vi.advanceTimersByTimeAsync(1200)
    await nextTick()

    expect(wrapper.findAll('.trace-step')).toHaveLength(4)
    expect(wrapper.text()).toContain('题目草稿已生�?)
  })

  it('flushes pending trace entries when a terminal stage is present', () => {
    const wrapper = mountTracePanel(pendingGenerationMessage('RESPONDED'))

    expect(wrapper.findAll('.trace-step')).toHaveLength(4)
    expect(wrapper.text()).toContain('生成完成')
    expect(wrapper.find('.trace-loading').exists()).toBe(false)
  })

  it('shows all trace entries immediately when reduced motion is preferred', async () => {
    vi.stubGlobal('matchMedia', vi.fn().mockReturnValue({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }))

    const wrapper = mountTracePanel(pendingGenerationMessage())
    await flushPromises()

    expect(wrapper.findAll('.trace-step')).toHaveLength(4)
  })

  it('renders raw output, quality issues, and full debug question details for admins', () => {
    const wrapper = mountTracePanel(debugGenerationMessage(), {admin: true})

    expect(wrapper.find('.trace-debug').exists()).toBe(false)
    expect(wrapper.findAll('.trace-step')).toHaveLength(2)
    expect(wrapper.find('.trace-developer').exists()).toBe(true)
    expect(wrapper.findAll('.trace-raw-output pre').some(item => item.text().includes('{"questions":[]}'))).toBe(true)
    expect(wrapper.text()).toContain('复核出卷质量')
    expect(wrapper.text()).toContain('判断题分值偏�?)
    expect(wrapper.find('.trace-issue').text()).not.toContain('JUDGE_SCORE_TOO_HIGH')
    expect(wrapper.text()).toContain('A')
    expect(wrapper.text()).toContain('选项 A')
    expect(wrapper.text()).toContain('正确')
    expect(wrapper.text()).toContain('单选题')
    expect(wrapper.text()).toContain('中等')
    expect(wrapper.text()).toContain('分�? 5')
    expect(wrapper.text()).toContain('答案')
    expect(wrapper.text()).toContain('参考答�?)
    expect(wrapper.text()).toContain('解析: 标准解析')
  })

  it('hides debug details from non-admin users', () => {
    const wrapper = mountTracePanel(debugGenerationMessage(), {admin: false})

    expect(wrapper.find('.trace-debug').exists()).toBe(false)
    expect(wrapper.findAll('.trace-step')).toHaveLength(0)
    expect(wrapper.text()).not.toContain('{"questions":[]}')
    expect(wrapper.text()).not.toContain('出卷质量复核')
  })
})

function mountTracePanel(message: ChatMessage, options: {admin?: boolean} = {}) {
  const authStore = useAuthStore()
  authStore.setUser(testUser(options.admin === false ? 2 : 0))

  return mount(AiGenerationTracePanel, {
    props: {
      embedded: true,
      message,
    },
    global: {
      plugins: [i18n],
      stubs: {
        AiMarkdownMessage: {
          props: ['content'],
          template: '<div class="markdown-stub">{{ content }}</div>',
        },
      },
    },
  })
}

function testUser(role: number): UserProfile {
  return {
    id: role === 0 ? 'admin' : 'teacher',
    email: null,
    emailVerified: true,
    displayName: null,
    avatarUrl: null,
    avatarFileId: null,
    locale: null,
    status: 'ACTIVE',
    phone: null,
    bio: null,
    gender: null,
    birthday: null,
    theme: 'system',
    notificationEnabled: true,
    createdProvider: null,
    createdIp: null,
    lastLoginProvider: null,
    lastLoginIp: null,
    loginCount: 1,
    linkedProviders: [],
    role,
    studentInfo: null,
    teacherInfo: null,
  }
}

function generationMessage(): ChatMessage {
  return {
    id: 'message-1',
    role: 'ASSISTANT',
    content: '',
    createdAt: '2026-06-24T10:00:00Z',
    messageType: 'PAPER',
    payload: {
      generationTrace: [{
        entryId: 'context',
        stage: 'CONTEXT_READY',
        detailType: 'web_sources',
        title: '联网搜索资料',
        summary: '已联网搜索到 2 条可参考网页资料�?,
        payload: {
          evidenceCount: 2,
          evidences: [{
            title: 'Duplicate raw evidence',
            snippet: 'This raw evidence should not be rendered twice.',
          }],
          webSources: [
            {
              site: 'example.com',
              title: 'Java arrays guide',
              url: 'https://www.example.com/java-arrays',
              favicon: 'https://www.example.com/favicon.ico',
              snippet: 'Array basics for exercises',
            },
            {
              site: 'docs.example.org',
              title: 'Array exercises',
              url: 'https://docs.example.org/array-exercises',
            },
          ],
        },
      }],
    },
  }
}

function pendingGenerationMessage(finalStage = 'GENERATED'): ChatMessage {
  const stages = [
    {
      entryId: 'received',
      stage: 'RECEIVED',
      detailType: 'request',
      title: '接收出卷请求',
      summary: '已收到试卷生成参数�?,
      payload: {questionCount: 10},
    },
    {
      entryId: 'context',
      stage: 'CONTEXT_READY',
      detailType: 'web_sources',
      title: '联网搜索资料',
      summary: '已联网搜索到 2 条资料�?,
      payload: {evidenceCount: 2},
    },
    {
      entryId: 'planned',
      stage: 'PLANNED',
      detailType: 'blueprint',
      title: '试卷蓝图已生�?,
      summary: '先制定试卷蓝图�?,
      payload: {sectionCount: 2},
    },
    {
      entryId: 'generated',
      stage: finalStage,
      detailType: finalStage === 'RESPONDED' ? 'responded' : 'drafts',
      title: finalStage === 'RESPONDED' ? '生成完成' : '题目草稿已生�?,
      summary: finalStage === 'RESPONDED' ? '试卷草稿已保存�? : '已生�?10 / 10 道题目草稿�?,
      payload: {questionCount: 10},
    },
  ]

  return {
    id: `message-${finalStage}`,
    role: 'ASSISTANT',
    content: '',
    createdAt: '2026-06-24T10:00:00Z',
    messageType: 'PAPER',
    pending: true,
    payload: {
      generationStage: finalStage,
      generationStatus: finalStage === 'RESPONDED' ? 'completed' : 'processing',
      generationTrace: stages,
    },
  }
}

function debugGenerationMessage(): ChatMessage {
  return {
    id: 'message-2',
    role: 'ASSISTANT',
    content: '',
    createdAt: '2026-06-24T10:00:00Z',
    messageType: 'PAPER',
    payload: {
      generationDebugTrace: [
        {
          entryId: 'raw',
          stage: 'GENERATED',
          detailType: 'raw_ai_output',
          title: '模型输出',
          summary: '模型已返回原始内容�?,
          payload: {
            detailType: 'raw_ai_output',
            callType: 'section_generation',
            rawOutput: '{"questions":[]}',
          },
        },
        {
          entryId: 'quality',
          stage: 'VALIDATED',
          detailType: 'quality_review',
          title: '出卷质量复核',
          summary: '发现 1 个出卷质量提示�?,
          payload: {
            issueCount: 1,
            issues: [{
              code: 'JUDGE_SCORE_TOO_HIGH',
              message: '判断题分值过�?,
              repairHint: '请检查总分或题型结构�?,
            }],
          },
        },
        {
          entryId: 'attempt',
          stage: 'GENERATED',
          detailType: 'section_attempt',
          title: '�?1 部分，第 1 次生�?,
          summary: '本次产出 1 道草稿题目�?,
          payload: {
            questionCount: 1,
            questions: [{
              questionTitle: '完整题目',
              questionContent: '请选择正确项�?,
              questionType: 0,
              difficulty: 2,
              score: 5,
              estimatedTime: 3,
              options: [
                {
                  optionLabel: 'A',
                  optionContent: '选项 A',
                  isCorrect: 1,
                  score: 5,
                  explanation: 'A^{-1} = \\frac{1}{\\det(A)}\\operatorname{adj}(A)',
                },
                {
                  optionLabel: 'B',
                  optionContent: '选项 B',
                  isCorrect: 0,
                  score: 0,
                },
              ],
              answers: [{
                answerContent: '参考答�?,
                explanation: 'A^{-1} = \\frac{1}{\\det(A)}\\operatorname{adj}(A)',
                score: 5,
                sortOrder: 1,
              }],
            }],
          },
        },
      ],
    },
  }
}

