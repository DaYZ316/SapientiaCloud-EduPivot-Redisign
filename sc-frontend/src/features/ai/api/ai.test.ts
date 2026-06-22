import {beforeEach, describe, expect, it, vi} from 'vitest'
import type {EventSourceMessage, FetchEventSourceInit} from '@microsoft/fetch-event-source'
import type {AxiosResponse} from 'axios'

import {ACCESS_TOKEN_KEY, http} from '@/shared/api/request'
import {exportGeneratedArtifact, streamChat} from '@/features/ai/api/ai'

const mocks = vi.hoisted(() => ({
  fetchEventSource: vi.fn(),
}))

vi.mock('@microsoft/fetch-event-source', () => ({
  fetchEventSource: mocks.fetchEventSource,
}))

vi.mock('@/shared/composables/useGlobalNotification', () => ({
  notify: {
    error: vi.fn(),
    warn: vi.fn(),
  },
}))

vi.mock('@/shared/composables/useSessionExpiredDialog', () => ({
  showSessionExpiredDialog: vi.fn(),
}))

vi.mock('@/app/i18n', () => ({
  i18n: {
    global: {
      t: (key: string) => key,
    },
  },
}))

describe('streamChat', () => {
  beforeEach(() => {
    vi.unstubAllEnvs()
    mocks.fetchEventSource.mockReset()
    localStorage.clear()
    localStorage.setItem(ACCESS_TOKEN_KEY, 'token')
  })

  it('parses agent_search events', async () => {
    mocks.fetchEventSource.mockImplementation(async (_url: string, init: FetchEventSourceInit) => {
      init.onmessage?.({
        event: 'agent_search',
        data: JSON.stringify({
          phase: 'results',
          domain: 'profile',
          label: '已识别当前角色：教师',
          total: 1,
          items: [{
            sourceLabel: '课程',
            title: '高中物理',
            contextLabel: '主讲课程',
            snippet: '2026 春季 · 进行中',
          }],
        }),
        id: '',
        retry: undefined,
      } satisfies EventSourceMessage)
    })
    const onAgentSearch = vi.fn()

    await streamChat({message: '我是谁'}, {
      onChunk: vi.fn(),
      onAgentSearch,
    })

    expect(onAgentSearch).toHaveBeenCalledWith(expect.objectContaining({
      label: '已识别当前角色：教师',
      items: [expect.objectContaining({
        sourceLabel: '课程',
        title: '高中物理',
      })],
    }))
  })

  it('parses generation_stage events', async () => {
    mocks.fetchEventSource.mockImplementation(async (_url: string, init: FetchEventSourceInit) => {
      init.onmessage?.({
        event: 'generation_stage',
        data: JSON.stringify({
          requestId: 'request-1',
          mode: 'QUESTION',
          stage: 'GENERATED',
          status: 'processing',
          title: 'Draft generated',
          summary: 'Generated 5 draft questions.',
          payload: {questionCount: 5},
          timestamp: '2026-06-21T10:00:00Z',
        }),
        id: '',
        retry: undefined,
      } satisfies EventSourceMessage)
    })
    const onGenerationStage = vi.fn()

    await streamChat({message: 'generate 5 questions'}, {
      onChunk: vi.fn(),
      onGenerationStage,
    })

    expect(onGenerationStage).toHaveBeenCalledWith(expect.objectContaining({
      requestId: 'request-1',
      stage: 'GENERATED',
      payload: {questionCount: 5},
    }))
  })

  it('parses generation_result events', async () => {
    mocks.fetchEventSource.mockImplementation(async (_url: string, init: FetchEventSourceInit) => {
      init.onmessage?.({
        event: 'generation_result',
        data: JSON.stringify({
          requestId: 'request-1',
          mode: 'PAPER',
          content: 'paper content',
          messageType: 'PAPER',
          payload: {
            questions: [{questionTitle: 'HashMap load factor'}],
          },
        }),
        id: '',
        retry: undefined,
      } satisfies EventSourceMessage)
    })
    const onGenerationResult = vi.fn()

    await streamChat({message: 'generate paper'}, {
      onChunk: vi.fn(),
      onGenerationResult,
    })

    expect(onGenerationResult).toHaveBeenCalledWith(expect.objectContaining({
      requestId: 'request-1',
      mode: 'PAPER',
      messageType: 'PAPER',
      payload: {
        questions: [{questionTitle: 'HashMap load factor'}],
      },
    }))
  })

  it('uses configured api base url for the chat stream', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:39080/')
    mocks.fetchEventSource.mockResolvedValue(undefined)

    await streamChat({message: 'generate 5 questions'}, {
      onChunk: vi.fn(),
    })

    expect(mocks.fetchEventSource).toHaveBeenCalledWith(
      'http://localhost:39080/api/ai/chat',
      expect.any(Object),
    )
  })
})

describe('exportGeneratedArtifact', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('requests the generated artifact as a blob and reads the response filename', async () => {
    const blob = new Blob(['pdf'], {type: 'application/pdf'})
    const requestSpy = vi.spyOn(http, 'request').mockResolvedValue({
      data: blob,
      headers: {
        'content-disposition': "attachment; filename*=UTF-8''Midterm%20Practice.pdf",
      },
    } as unknown as AxiosResponse<Blob>)

    const result = await exportGeneratedArtifact('conversation-1', 'message-1', {
      format: 'pdf',
      includeAnswers: true,
    })

    expect(requestSpy).toHaveBeenCalledWith({
      method: 'GET',
      url: '/api/ai/conversations/conversation-1/messages/message-1/export',
      params: {
        format: 'pdf',
        includeAnswers: true,
      },
      responseType: 'blob',
    })
    expect(result).toEqual({
      blob,
      filename: 'Midterm Practice.pdf',
    })
  })

  it('defaults includeAnswers to false and uses a fallback filename', async () => {
    const blob = new Blob(['docx'])
    const requestSpy = vi.spyOn(http, 'request').mockResolvedValue({
      data: blob,
      headers: {},
    } as unknown as AxiosResponse<Blob>)

    const result = await exportGeneratedArtifact('conversation-1', 'message-1', {
      format: 'docx',
    })

    expect(requestSpy).toHaveBeenCalledWith(expect.objectContaining({
      params: {
        format: 'docx',
        includeAnswers: false,
      },
      responseType: 'blob',
    }))
    expect(result).toEqual({
      blob,
      filename: 'generated-artifact.docx',
    })
  })
})
