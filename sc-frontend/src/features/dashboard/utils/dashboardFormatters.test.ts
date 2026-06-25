import {describe, expect, it} from 'vitest'

import {
    formatDashboardDateTime,
    formatDashboardNumber,
    formatDashboardRelativeTime,
} from '@/features/dashboard/utils/dashboardFormatters'

describe('dashboardFormatters', () => {
    it('formats numbers with the active locale', () => {
        expect(formatDashboardNumber(1234567, 'en-US')).toBe('1,234,567')
        expect(formatDashboardNumber(1234567, 'zh-CN')).toBe('1,234,567')
    })

    it('formats invalid dates as a dash', () => {
        expect(formatDashboardDateTime(null, 'en-US')).toBe('-')
        expect(formatDashboardDateTime('not-a-date', 'zh-CN')).toBe('-')
    })

    it('formats relative time with the active locale', () => {
        const now = Date.UTC(2026, 5, 23, 12, 0, 0)
        const thirtyMinutesAgo = now - 30 * 60 * 1000

        expect(formatDashboardRelativeTime(thirtyMinutesAgo, 'en-US', now)).toBe('30 minutes ago')
        expect(formatDashboardRelativeTime(thirtyMinutesAgo, 'zh-CN', now)).toBe('30分钟前')
    })
})
