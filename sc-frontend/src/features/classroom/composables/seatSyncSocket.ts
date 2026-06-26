export function buildSeatSyncSocketUrl(sessionId: string, token: string) {
    const baseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

    let apiUrl: URL
    if (baseUrl) {
        apiUrl = new URL(baseUrl)
    } else {
        apiUrl = new URL(window.location.origin)
    }
    apiUrl.protocol = apiUrl.protocol === 'https:' ? 'wss:' : 'ws:'

    apiUrl.pathname = '/api/class-sessions/seats/ws'
    apiUrl.search = new URLSearchParams({
        sessionId,
        token,
    }).toString()
    return apiUrl.toString()
}
