import {describe, expect, it} from 'vitest'
import {RouterLinkStub, mount} from '@vue/test-utils'
import {createPinia, setActivePinia} from 'pinia'

import AiAgentSearchEvidence from '@/features/ai/components/AiAgentSearchEvidence.vue'
import {useAuthStore} from '@/features/auth/stores/auth'
import type {AgentSearchPayload} from '@/features/ai/types/ai'

describe('AiAgentSearchEvidence', () => {
  function mountEvidence(payload: AgentSearchPayload, role = 1) {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setUser({
      id: 'user-a',
      email: null,
      emailVerified: false,
      displayName: 'User A',
      avatarUrl: null,
      avatarFileId: null,
      locale: null,
      status: 'ACTIVE',
      phone: null,
      bio: null,
      gender: null,
      birthday: null,
      theme: null,
      notificationEnabled: true,
      createdProvider: null,
      createdIp: null,
      lastLoginProvider: null,
      lastLoginIp: null,
      loginCount: 0,
      linkedProviders: [],
      role,
      studentInfo: null,
      teacherInfo: null,
    })
    return mount(AiAgentSearchEvidence, {
      props: {payload},
      global: {
        plugins: [pinia],
        stubs: {
          RouterLink: RouterLinkStub,
        },
      },
    })
  }

  it('renders multiple search records with full metadata and index info', () => {
    const wrapper = mountEvidence(
      {
          searches: [
            {
              searchId: 'search-course',
              domain: 'courses',
              query: 'AI',
              label: '找到 1 门课程',
              phase: 'results',
              status: 'OK',
              provider: 'platform',
              durationMs: 18,
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
      0,
    )

    expect(wrapper.text()).toContain('已参考 2 条参考资料')
    expect(wrapper.text()).toContain('完成')
    expect(wrapper.text()).toContain('来源 platform')
    expect(wrapper.text()).toContain('18ms')
    expect(wrapper.text()).toContain('找到 1 门课程')
    expect(wrapper.text()).toContain('读取平台资料')
    expect(wrapper.text()).toContain('AI Course')
    expect(wrapper.text()).toContain('Current user')
    expect(wrapper.text()).toContain('"status": 1')
    expect(wrapper.text()).toContain('"method": "GET"')
    expect(wrapper.text()).toContain('"page": 1')
    expect(wrapper.findAllComponents(RouterLinkStub)).toHaveLength(1)
    expect(wrapper.find('.agent-evidence__source-row').exists()).toBe(true)
  })

  it('hides index info for non-admin users', () => {
    const wrapper = mountEvidence({
      searches: [{
        searchId: 'search-course',
        domain: 'courses',
        query: 'AI',
        label: 'Found 1 course',
        phase: 'results',
        status: 'OK',
        provider: 'platform',
        items: [{
          sourceType: 'COURSE',
          sourceLabel: 'Course',
          sourceId: 'course-a',
          title: 'AI Course',
          metadata: {status: 1},
          indexInfo: {sourceType: 'COURSE', sourceId: 'course-a'},
        }],
      }],
    } satisfies AgentSearchPayload)

    expect(wrapper.text()).toContain('AI Course')
    expect(wrapper.text()).not.toContain('"status": 1')
    expect(wrapper.find('.agent-evidence__index').exists()).toBe(false)
  })

  it('groups legacy event-only payloads by search id', () => {
    const wrapper = mountEvidence({
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
              status: 'OK',
              provider: 'platform',
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
        } satisfies AgentSearchPayload)

    expect(wrapper.findAll('.agent-evidence__record')).toHaveLength(1)
    expect(wrapper.text()).toContain('找到 1 条课程资源')
    expect(wrapper.text()).toContain('第一章')
  })

  it('does not show a navigation link for unknown resources', () => {
    const wrapper = mountEvidence(
      {
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
      0,
    )

    expect(wrapper.text()).toContain('Unmapped resource')
    expect(wrapper.text()).toContain('"raw": true')
    expect(wrapper.findComponent(RouterLinkStub).exists()).toBe(false)
  })

  it('renders web search results as external links', () => {
    const wrapper = mountEvidence({
          searches: [{
            searchId: 'web',
            domain: 'web',
            query: 'AI news',
            label: '找到 1 条网页结果',
            phase: 'results',
            status: 'OK',
            provider: 'tavily-compatible',
            durationMs: 21,
            items: [{
              sourceType: 'WEB_SEARCH',
              sourceLabel: '网页',
              sourceId: 'https://example.com/ai-news',
              title: 'AI News',
              contextLabel: 'https://example.com/ai-news',
              snippet: 'Latest AI news snippet',
              relationLabel: '联网搜索',
              metadata: {
                url: 'https://example.com/ai-news',
                provider: 'tavily-compatible',
                favicon: 'https://example.com/favicon.ico',
              },
              indexInfo: {url: 'https://example.com/ai-news'},
            }],
          }],
        } satisfies AgentSearchPayload)

    expect(wrapper.text()).toContain('已参考 1 条参考资料')
    expect(wrapper.text()).toContain('网页')
    expect(wrapper.text()).toContain('完成')
    expect(wrapper.text()).toContain('来源 tavily-compatible')
    expect(wrapper.text()).toContain('21ms')
    expect(wrapper.text()).toContain('example.com')
    expect(wrapper.text()).toContain('AI News')
    expect(wrapper.text()).not.toContain('Latest AI news snippet')
    expect(wrapper.findComponent(RouterLinkStub).exists()).toBe(false)
    const link = wrapper.find('a.agent-evidence__source-row')
    expect(link.attributes('href')).toBe('https://example.com/ai-news')
    expect(link.attributes('target')).toBe('_blank')
    expect(link.attributes('rel')).toBe('noopener noreferrer')
    expect(link.text()).toContain('example.com')
    expect(link.text()).toContain('AI News')
    expect(link.find('img.agent-evidence__source-icon').attributes('src')).toBe('https://example.com/favicon.ico')
  })

  it('renders system time results without navigation links', () => {
    const wrapper = mountEvidence({
          searches: [{
            searchId: 'time',
            domain: 'time',
            query: '当前日期时间',
            label: '已读取当前日期',
            phase: 'results',
            status: 'OK',
            provider: 'server-clock',
            durationMs: 1,
            items: [{
              sourceType: 'SYSTEM_TIME',
              sourceLabel: '系统时间',
              sourceId: '2026-06-24',
              title: '当前日期：2026-06-24',
              contextLabel: 'Asia/Shanghai',
              snippet: '当前日期是 2026-06-24，当前时间是 12:00:00，时区 Asia/Shanghai。',
              metadata: {
                date: '2026-06-24',
                time: '12:00:00',
                zoneId: 'Asia/Shanghai',
                instant: '2026-06-24T04:00:00Z',
              },
              indexInfo: {date: '2026-06-24', zoneId: 'Asia/Shanghai'},
            }],
          }],
        } satisfies AgentSearchPayload)

    expect(wrapper.text()).toContain('已参考 1 条参考资料')
    expect(wrapper.text()).toContain('系统时间')
    expect(wrapper.text()).toContain('已读取当前日期')
    expect(wrapper.text()).toContain('来源 server-clock')
    expect(wrapper.text()).toContain('系统时间')
    expect(wrapper.text()).toContain('当前日期：2026-06-24')
    expect(wrapper.findComponent(RouterLinkStub).exists()).toBe(false)
    expect(wrapper.find('a.agent-evidence__source-row').exists()).toBe(false)
    expect(wrapper.find('.agent-evidence__source-row.is-static').exists()).toBe(true)
  })

  it('renders empty and failed searches without implying usable sources', () => {
    const wrapper = mountEvidence({
          searches: [
            {
              searchId: 'web-empty',
              domain: 'web',
              query: 'Palworld 1.0 release date',
              label: '未找到可引用网页结果',
              phase: 'empty',
              status: 'EMPTY',
              provider: 'tavily-compatible',
              durationMs: 42,
              items: [],
            },
            {
              searchId: 'web-misconfigured',
              domain: 'web',
              query: 'Palworld latest',
              label: '联网搜索未配置',
              phase: 'error',
              status: 'MISCONFIGURED',
              reason: '缺少联网搜索 endpoint 或 api key',
              provider: 'tavily-compatible',
              items: [],
            },
          ],
        } satisfies AgentSearchPayload)

    expect(wrapper.text()).toContain('检索 2 次，未获得可引用结果')
    expect(wrapper.text()).toContain('无结果')
    expect(wrapper.text()).toContain('没有匹配资料')
    expect(wrapper.text()).toContain('未配置')
    expect(wrapper.text()).toContain('缺少联网搜索 endpoint 或 api key')
    expect(wrapper.findComponent(RouterLinkStub).exists()).toBe(false)
    expect(wrapper.find('a.agent-evidence__source-row').exists()).toBe(false)
  })
})
