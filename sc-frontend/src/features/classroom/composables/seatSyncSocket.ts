export function buildSeatSyncSocketUrl(sessionId: string, token: string) {
    const baseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')
    const protocol = baseUrl.startsWith('https') ? 'wss:' : 'ws:'

    let apiUrl: URL
    if (baseUrl) {
        apiUrl = new URL(baseUrl)
        apiUrl.protocol = protocol
    } else {
        apiUrl = new URL(window.location.origin)
        apiUrl.protocol = protocol
    }

    apiUrl.pathname = '/api/class-sessions/seats/ws'
    apiUrl.search = new URLSearchParams({
        sessionId,
        token,
    }).toString()
    return apiUrl.toString()
}
