import {describe, expect, it} from 'vitest'

import type {ChatMessage, GenerationTraceEntry} from '@/features/ai/types/ai'
import {generationDebugTrace, generationTrace} from '@/features/ai/utils/generationTrace'

describe('generationTrace display normalization', () => {
  it('moves legacy empty draft noise into debug trace', () => {
    const message = generationMessage([
      traceEntry('PLANNED', 'blueprint', '试卷蓝图已生成', {}, '已规划 5 道题。'),
      traceEntry('GENERATED', 'section_attempt', '第 1 部分，第 1 次生成', {
        questionCount: 0,
        issues: [issue('EMPTY_RESULT', '未生成任何题目')],
      }),
      traceEntry('GENERATED', 'drafts', '题目草稿已生成', {
        questionCount: 0,
        questions: [],
        issues: [issue('EMPTY_RESULT', '未生成任何题目')],
      }, '已按蓝图生成 0 道题目草稿。'),
      traceEntry('VALIDATED', 'validation', '草稿校验结果', {
        questionCount: 0,
        issueCount: 1,
        issues: [issue('EMPTY_RESULT', '未生成任何题目')],
      }),
      traceEntry('REPAIRED', 'repair_attempt', '第 1 轮修复', {
        questionCount: 1,
        questions: [{questionTitle: '修复题'}],
      }),
      traceEntry('ASSEMBLED', 'final_questions', '题目集已组装', {
        questionCount: 1,
        questions: [{questionTitle: '最终题'}],
      }),
    ])

    const visible = generationTrace(message)
    const debug = generationDebugTrace(message)

    expect(visible.map(entry => entry.detailType)).toEqual(['blueprint', 'final_questions'])
    expect(JSON.stringify(visible)).not.toContain('未生成任何题目')
    expect(debug.map(entry => entry.detailType)).toEqual([
      'section_attempt',
      'drafts',
      'validation',
      'repair_attempt',
    ])
  })

  it('merges generated question deltas into one visible progress step', () => {
    const message = generationMessage([
      traceEntry('GENERATED', 'draft_progress', '题目草稿生成中', {
        questionDelta: true,
        generatedQuestionCount: 1,
        totalQuestionCount: 2,
        questions: [{questionTitle: '题目 1'}],
      }),
      traceEntry('GENERATED', 'draft_progress', '题目草稿生成中', {
        questionDelta: true,
        generatedQuestionCount: 2,
        totalQuestionCount: 2,
        questions: [{questionTitle: '题目 2'}],
      }),
    ])

    const generated = generationTrace(message)[0]

    expect(generated.summary).toBe('已生成 2 / 2 道题目草稿。')
    expect(generated.payload?.questions).toEqual([
      {questionTitle: '题目 1'},
      {questionTitle: '题目 2'},
    ])
  })

  it('hides question bank save prerequisites from visible issues', () => {
    const message = generationMessage([
      traceEntry('VALIDATED', 'validation', '题目质量校验完成', {
        issueCount: 2,
        remainingIssueCount: 2,
        issues: [
          issue('MISSING_QUESTION_BANK_ID', '请选择题库'),
          issue('INVALID_SCORE', '分值无效'),
        ],
      }),
    ])

    const payload = generationTrace(message)[0].payload

    expect(payload?.issues).toEqual([issue('INVALID_SCORE', '分值无效')])
    expect(payload?.issueCount).toBe(1)
    expect(payload?.remainingIssueCount).toBe(1)
  })
})

function generationMessage(generationTrace: GenerationTraceEntry[], questions: unknown[] = []): ChatMessage {
  return {
    id: 'message-1',
    role: 'ASSISTANT',
    content: '',
    createdAt: '2026-06-22T10:00:00Z',
    messageType: 'PAPER',
    payload: {
      questions,
      generationTrace,
    },
  }
}

function traceEntry(
  stage: string,
  detailType: string,
  title: string,
  payload: Record<string, unknown>,
  summary = 'summary',
): GenerationTraceEntry {
  return {
    entryId: `${stage}-${detailType}-${title}`,
    stage,
    detailType,
    title,
    summary,
    payload,
    timestamp: '2026-06-22T10:00:00Z',
  }
}

function issue(code: string, message: string) {
  return {code, message}
}
