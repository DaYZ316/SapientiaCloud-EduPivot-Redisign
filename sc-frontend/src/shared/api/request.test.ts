import {beforeEach, describe, expect, it, vi} from 'vitest'

import {
    ApiError,
    http,
    markVoluntaryLogoutInProgress,
    request,
    resetSessionExpiredHandling,
    refreshSession,
} from '@/shared/api/request'
import {showSessionExpiredDialog} from '@/shared/composables/useSessionExpiredDialog'

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

describe('request session expiration handling', () => {
    beforeEach(() => {
        vi.restoreAllMocks()
        vi.clearAllMocks()
        localStorage.clear()
        resetSessionExpiredHandling()
    })

    it('shows the session expired dialog for regular 401 responses', async () => {
        vi.spyOn(http, 'request').mockRejectedValueOnce({
            response: {
                status: 401,
                data: {message: 'Session expired'},
            },
        })

        await expect(request({method: 'GET', url: '/api/protected'})).rejects.toBeInstanceOf(ApiError)

        expect(showSessionExpiredDialog).toHaveBeenCalledTimes(1)
    })

    it('does not show the session expired dialog while voluntarily logging out', async () => {
        markVoluntaryLogoutInProgress()
        vi.spyOn(http, 'request').mockRejectedValueOnce({
            response: {
                status: 401,
                data: {message: 'Session expired'},
            },
        })

        await expect(request({method: 'POST', url: '/api/auth/logout'})).rejects.toBeInstanceOf(ApiError)

        expect(showSessionExpiredDialog).not.toHaveBeenCalled()
    })

    it('does not refresh the session while voluntarily logging out', async () => {
        markVoluntaryLogoutInProgress()
        localStorage.setItem('edupivot.refreshToken', 'refresh-token')

        await expect(refreshSession()).resolves.toBe(false)
    })
})
