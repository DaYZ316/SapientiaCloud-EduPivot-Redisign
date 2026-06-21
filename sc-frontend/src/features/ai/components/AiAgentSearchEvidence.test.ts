import {describe, expect, it} from 'vitest'
import {RouterLinkStub, mount} from '@vue/test-utils'

import AiAgentSearchEvidence from '@/features/ai/components/AiAgentSearchEvidence.vue'
import type {AgentSearchPayload} from '@/features/ai/types/ai'

describe('AiAgentSearchEvidence', () => {
  it('renders multiple search records with full metadata and index info', () => {
    const wrapper = mount(AiAgentSearchEvidence, {
      props: {
        payload: {
          searches: [
            {
              searchId: 'search-course',
              domain: 'courses',
              query: 'AI',
              label: '找到 1 门课程',
              phase: 'results',
              items: [{
                sourceType: 'COURSE',
                sourceLabel: '课程',
                sourceId: 'course-a',
                courseId: 'course-a',
                title: 'AI Course',
                contextLabel: '主讲课程',
                snippet: '课程简介',
                relationLabel: '主讲课程',
                metadata: {status: 1},
                indexInfo: {sourceType: 'COURSE', sourceId: 'course-a', courseId: 'course-a'},
              }],
            },
            {
              searchId: 'search-api',
              domain: 'platform',
              query: 'auth /api/auth/users/me',
              label: '读取平台资料',
              phase: 'results',
              items: [{
                sourceType: 'PLATFORM_API',
                sourceLabel: '平台接口',
                title: 'Current user',
                contextLabel: 'auth',
                snippet: '{"displayName":"Li Wenhao"}',
                metadata: {service: 'auth', path: '/api/auth/users/me'},
                indexInfo: {
                  service: 'auth',
                  path: '/api/auth/users/me',
                  method: 'GET',
                  queryParams: {page: 1},
                },
              }],
            },
          ],
        } satisfies AgentSearchPayload,
      },
      global: {
        stubs: {
          RouterLink: RouterLinkStub,
        },
      },
    })

    expect(wrapper.text()).toContain('已参考 2 条平台内容')
    expect(wrapper.text()).toContain('找到 1 门课程')
    expect(wrapper.text()).toContain('读取平台资料')
    expect(wrapper.text()).toContain('AI Course')
    expect(wrapper.text()).toContain('Current user')
    expect(wrapper.text()).toContain('"status": 1')
    expect(wrapper.text()).toContain('"method": "GET"')
    expect(wrapper.text()).toContain('"page": 1')
    expect(wrapper.findAllComponents(RouterLinkStub)).toHaveLength(1)
  })

  it('groups legacy event-only payloads by search id', () => {
    const wrapper = mount(AiAgentSearchEvidence, {
      props: {
        payload: {
          events: [
            {
              searchId: 'same-search',
              phase: 'started',
              domain: 'resources',
              label: '正在检索课程资源',
              query: 'chapter',
            },
            {
              searchId: 'same-search',
              phase: 'results',
              domain: 'resources',
              label: '找到 1 条课程资源',
              query: 'chapter',
              total: 1,
              items: [{
                sourceType: 'CHAPTER',
                sourceLabel: '章节',
                sourceId: 'chapter-a',
                courseId: 'course-a',
                title: '第一章',
              }],
            },
          ],
        } satisfies AgentSearchPayload,
      },
      global: {
        stubs: {
          RouterLink: RouterLinkStub,
        },
      },
    })

    expect(wrapper.findAll('.agent-evidence__record')).toHaveLength(1)
    expect(wrapper.text()).toContain('找到 1 条课程资源')
    expect(wrapper.text()).toContain('第一章')
  })

  it('does not show a navigation link for unknown resources', () => {
    const wrapper = mount(AiAgentSearchEvidence, {
      props: {
        payload: {
          searches: [{
            searchId: 'unknown',
            domain: 'unknown',
            query: 'x',
            label: '找到 1 条资料',
            phase: 'results',
            items: [{
              sourceType: 'UNKNOWN_RESOURCE',
              sourceLabel: '外部资料',
              sourceId: 'resource-a',
              title: 'Unmapped resource',
              metadata: {raw: true},
              indexInfo: {sourceType: 'UNKNOWN_RESOURCE', sourceId: 'resource-a'},
            }],
          }],
        } satisfies AgentSearchPayload,
      },
      global: {
        stubs: {
          RouterLink: RouterLinkStub,
        },
      },
    })

    expect(wrapper.text()).toContain('Unmapped resource')
    expect(wrapper.text()).toContain('"raw": true')
    expect(wrapper.findComponent(RouterLinkStub).exists()).toBe(false)
  })
})
