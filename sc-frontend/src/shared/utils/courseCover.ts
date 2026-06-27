export const DEFAULT_COURSE_COVER_URL = '/assets/course-cover-default.png'

export function getCourseCoverUrl(coverUrl?: string | null): string {
    return coverUrl || DEFAULT_COURSE_COVER_URL
}

export function handleCourseCoverError(event: Event) {
    const image = event.currentTarget as HTMLImageElement | null
    if (!image || image.src.endsWith(DEFAULT_COURSE_COVER_URL)) return

    image.src = DEFAULT_COURSE_COVER_URL
}
