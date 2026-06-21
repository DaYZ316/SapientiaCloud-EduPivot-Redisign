import {describe, expect, it} from 'vitest'

import {resolveAgentSearchNavigation} from '@/features/ai/utils/agentSearchNavigation'
import type {AgentSearchItem} from '@/features/ai/types/ai'

describe('resolveAgentSearchNavigation', () => {
  it('resolves known course resource routes', () => {
    expect(nav({sourceType: 'COURSE', sourceId: 'course-a'})?.to).toEqual({
      name: 'course-overview',
      params: {id: 'course-a'},
    })
    expect(nav({sourceType: 'CHAPTER', sourceId: 'chapter-a', courseId: 'course-a'})?.to).toEqual({
      name: 'chapter-detail',
      params: {courseId: 'course-a'},
      query: {chapterId: 'chapter-a'},
    })
    expect(nav({sourceType: 'QUESTION_BANK', sourceId: 'bank-a'})?.to).toEqual({
      name: 'question-bank-detail',
      params: {id: 'bank-a'},
    })
  })

  it('resolves question file live practice and chat memory routes', () => {
    expect(nav({
      sourceType: 'QUESTION',
      sourceId: 'question-a',
      metadata: {questionBankId: 'bank-a'},
    })?.to).toEqual({
      name: 'question-bank-detail',
      params: {id: 'bank-a'},
      query: {questionId: 'question-a'},
    })
    expect(nav({
      sourceType: 'COURSE_FILE',
      title: 'slides.pdf',
      metadata: {storageObjectId: 'file-a'},
    })?.to).toEqual({
      name: 'file-preview',
      query: {fileId: 'file-a', fileName: 'slides.pdf'},
    })
    expect(nav({
      sourceType: 'LIVE_PRACTICE',
      sourceId: 'group-a',
      courseId: 'course-a',
    })?.to).toEqual({
      name: 'course-live-practice-detail',
      params: {courseId: 'course-a', groupId: 'group-a'},
    })
    expect(nav({
      sourceType: 'CHAT_MEMORY',
      indexInfo: {conversationId: 'conversation-a'},
    })?.to).toEqual({
      name: 'ai-workspace',
      query: {conversationId: 'conversation-a'},
    })
  })

  it('does not invent navigation for unknown or incomplete resources', () => {
    expect(nav({sourceType: 'PLATFORM_API', metadata: {path: '/api/courses'}})).toBeNull()
    expect(nav({sourceType: 'QUESTION'})).toBeNull()
    expect(nav({sourceType: 'UNKNOWN', sourceId: 'x'})).toBeNull()
  })
})

function nav(item: AgentSearchItem) {
  return resolveAgentSearchNavigation(item)
}
