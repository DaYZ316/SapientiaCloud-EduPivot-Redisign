import {describe, expect, it} from 'vitest'

import en from '@/features/dashboard/i18n/en-US'
import zh from '@/features/dashboard/i18n/zh-CN'

function flattenKeys(value: unknown, prefix = ''): string[] {
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
        return prefix ? [prefix] : []
    }

    return Object.entries(value as Record<string, unknown>).flatMap(([key, child]) => {
        const nextPrefix = prefix ? `${prefix}.${key}` : key
        return flattenKeys(child, nextPrefix)
    })
}

describe('dashboard i18n', () => {
    it('keeps dashboard keys aligned between zh-CN and en-US', () => {
        expect(flattenKeys(en.dashboard).sort()).toEqual(flattenKeys(zh.dashboard).sort())
    })
})

