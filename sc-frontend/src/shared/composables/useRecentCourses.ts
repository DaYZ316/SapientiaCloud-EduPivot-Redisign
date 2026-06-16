/**
 * 当前用户近期打开的课程，按 MRU 顺序存储在 localStorage，上限 5 门。
 * key 按 userId 隔离，切换账号时自动切换数据源。
 *
 * @author DaYZ
 * @since 2026-06-16
 */

import {ref, watch} from 'vue'
import {useAuthStore} from '@/features/auth/stores/auth'

const MAX_RECENT = 5
const STORAGE_PREFIX = 'edupivot.recentCourses:'

export interface RecentCourseEntry {
    id: string
    title: string
    coverUrl: string | null
    teacherName: string | null
    visitedAt: number
}

/** 模块级缓存，所有组件共享同一份 reactive ref */
const recentCourses = ref<RecentCourseEntry[]>([])
let currentUserId: string | null = null

function storageKey(userId: string): string {
    return `${STORAGE_PREFIX}${userId}`
}

function loadFromStorage(userId: string): RecentCourseEntry[] {
    try {
        const raw = localStorage.getItem(storageKey(userId))
        if (!raw) return []
        const parsed = JSON.parse(raw) as RecentCourseEntry[]
        return Array.isArray(parsed) ? parsed.slice(0, MAX_RECENT) : []
    } catch {
        return []
    }
}

function saveToStorage(userId: string, entries: RecentCourseEntry[]) {
    try {
        localStorage.setItem(storageKey(userId), JSON.stringify(entries))
    } catch {
        // Storage full or unavailable — silently ignore
    }
}

function ensureUser(userId: string | null) {
    if (userId === currentUserId) return
    currentUserId = userId
    recentCourses.value = userId ? loadFromStorage(userId) : []
}

/**
 * 记录一次课程访问。若已存在则移到最前并更新 title/coverUrl，否则插入头部。
 */
export function recordCourseVisit(course: {
    id: string;
    title: string;
    coverUrl?: string | null;
    teacherName?: string | null
}) {
    const userId = currentUserId
    if (!userId) return

    const now = Date.now()
    const filtered = recentCourses.value.filter(e => e.id !== course.id)
    filtered.unshift({
        id: course.id,
        title: course.title,
        coverUrl: course.coverUrl ?? null,
        teacherName: course.teacherName ?? null,
        visitedAt: now,
    })
    recentCourses.value = filtered.slice(0, MAX_RECENT)
    saveToStorage(userId, recentCourses.value)
}

/**
 * 移除单条近期课程记录。
 */
export function removeRecentCourse(courseId: string) {
    const userId = currentUserId
    if (!userId) return
    recentCourses.value = recentCourses.value.filter(e => e.id !== courseId)
    saveToStorage(userId, recentCourses.value)
}

/**
 * 组合式函数：返回当前用户的近期课程列表（reactive）。
 * 内部自动 watch 用户身份变化。
 */
export function useRecentCourses() {
    const authStore = useAuthStore()

    // 初始化 + watch 用户变化
    ensureUser(authStore.user?.id ?? null)
    watch(
        () => authStore.user?.id,
        (newId) => ensureUser(newId ?? null),
    )

    return {recentCourses, recordCourseVisit, removeRecentCourse}
}
