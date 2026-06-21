import {beforeEach, describe, expect, it, vi} from 'vitest'
import type {EventSourceMessage, FetchEventSourceInit} from '@microsoft/fetch-event-source'

import {ACCESS_TOKEN_KEY} from '@/shared/api/request'
import {streamChat} from '@/features/ai/api/ai'

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
})
