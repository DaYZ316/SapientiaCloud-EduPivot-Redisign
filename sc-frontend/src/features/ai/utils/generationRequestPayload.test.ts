import {describe, expect, it} from 'vitest'

import {
  buildGenerationRequestDisplay,
  createGenerationRequestPayload,
} from '@/features/ai/utils/generationRequestPayload'

describe('generation request payload', () => {
  it('builds a field display for question generation requests', () => {
    const payload = createGenerationRequestPayload('QUESTION', {
      questionBankId: 'bank-1',
      questionCount: 5,
      questionType: 0,
      difficulty: 2,
      scorePerQuestion: 4,
      requirement: 'Cover HashMap default load factor.',
      knowledgePoints: ['HashMap', '集合框架'],
      abilityGoals: ['理解底层原理'],
    })

    const display = buildGenerationRequestDisplay(payload)

    expect(display?.mode).toBe('QUESTION')
    expect(display?.fields).toContainEqual({key: 'questionCount', kind: 'number', value: 5})
    expect(display?.fields).toContainEqual({key: 'questionType', kind: 'questionType', value: 0})
    expect(display?.fields).toContainEqual({key: 'difficulty', kind: 'difficulty', value: 2})
    expect(display?.fields).toContainEqual({key: 'questionBankId', kind: 'questionBank', value: 'bank-1'})
    expect(display?.requirement).toBe('Cover HashMap default load factor.')
  })

  it('omits unspecified paper generation request fields', () => {
    const payload = createGenerationRequestPayload('PAPER', {
      questionCount: 10,
      questionType: 5,
      difficulty: 3,
      totalScore: 100,
      totalEstimatedTime: 60,
    })

    const display = buildGenerationRequestDisplay(payload)

    expect(display?.mode).toBe('PAPER')
    expect(display?.fields).toContainEqual({key: 'totalScore', kind: 'number', value: 100})
    expect(display?.fields).toContainEqual({key: 'totalEstimatedTime', kind: 'minutes', value: 60})
    expect(display?.fields.some(field => field.key === 'paperName')).toBe(false)
    expect(display?.fields.some(field => field.key === 'paperType')).toBe(false)
    expect(display?.requirement).toBe('')
  })
})
