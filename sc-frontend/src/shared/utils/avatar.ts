export function getAvatarInitials(displayName?: string | null): string {
    const source = displayName || 'User'
    const chineseCharacter = source.match(/[\u3400-\u9FFF]/u)?.[0]

    if (chineseCharacter) {
        return chineseCharacter
    }

    const words = source.trim().split(/\s+/).filter(Boolean)

    if (words.length >= 2) {
        return `${words[0][0]}${words[1][0]}`.toUpperCase()
    }

    return source.slice(0, 2).toUpperCase()
}
