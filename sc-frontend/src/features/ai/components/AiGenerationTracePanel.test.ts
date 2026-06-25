import {mount} from '@vue/test-utils'
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'

import AiGenerationTracePanel from '@/features/ai/components/AiGenerationTracePanel.vue'
import type {ChatMessage} from '@/features/ai/types/ai'

describe('AiGenerationTracePanel', () => {
  beforeEach(() => {
    Element.prototype.scrollTo = vi.fn()
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => {
      callback(0)
      return 1
    })
    vi.stubGlobal('cancelAnimationFrame', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('renders web search sources as external source rows', () => {
    const wrapper = mount(AiGenerationTracePanel, {
      props: {
        embedded: true,
        message: generationMessage(),
      },
      global: {
        stubs: {
          AiMarkdownMessage: {
            props: ['content'],
            template: '<div class="markdown-stub">{{ content }}</div>',
          },
        },
      },
    })

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
    expect(wrapper.text()).not.toContain('Duplicate raw evidence')
  })

  it('renders raw output, quality issues, and full debug question details', () => {
    const wrapper = mount(AiGenerationTracePanel, {
      props: {
        embedded: true,
        message: debugGenerationMessage(),
      },
      global: {
        stubs: {
          AiMarkdownMessage: {
            props: ['content'],
            template: '<div class="markdown-stub">{{ content }}</div>',
          },
        },
      },
    })

    expect(wrapper.find('.trace-raw-output pre').text()).toContain('{"questions":[]}')
    expect(wrapper.text()).toContain('出卷质量复核')
    expect(wrapper.text()).toContain('判断题分值过高')
    expect(wrapper.text()).toContain('A. 选项 A（正确 · 分值: 5）')
    expect(wrapper.text()).toContain('答案:')
    expect(wrapper.text()).toContain('参考答案 · 分值: 5 · 顺序: 1')
    expect(wrapper.text()).toContain('解析: 标准解析')
  })
})

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
        summary: '已联网搜索到 2 条可参考网页资料。',
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
          summary: '模型已返回原始内容。',
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
          summary: '发现 1 个出卷质量提示。',
          payload: {
            issueCount: 1,
            issues: [{
              code: 'JUDGE_SCORE_TOO_HIGH',
              message: '判断题分值过高',
              repairHint: '请检查总分或题型结构。',
            }],
          },
        },
        {
          entryId: 'attempt',
          stage: 'GENERATED',
          detailType: 'section_attempt',
          title: '第 1 部分，第 1 次生成',
          summary: '本次产出 1 道草稿题目。',
          payload: {
            questionCount: 1,
            questions: [{
              questionTitle: '完整题目',
              questionContent: '请选择正确项。',
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
                  explanation: '标准解析',
                },
                {
                  optionLabel: 'B',
                  optionContent: '选项 B',
                  isCorrect: 0,
                  score: 0,
                },
              ],
              answers: [{
                answerContent: '参考答案',
                explanation: '标准解析',
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
