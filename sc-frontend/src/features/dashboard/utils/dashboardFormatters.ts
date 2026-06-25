import type {Component} from 'vue'

import type {ClassSession} from '@/features/course/types/classSession'
import type {Course} from '@/features/course/types/course'
import type {PracticeSession} from '@/features/question-bank/types/practiceSession'

export interface Metric {
    label: string
    value: string
    meta: string
}

export interface ActionLink {
    label: string
    to: string
    icon: Component
}

export interface ChartDatum {
    label: string
    value: number
}

export type DashboardLevel = 'normal' | 'warning' | 'critical' | 'live'

export function clampPercent(value?: number | null) {
    if (value == null || Number.isNaN(value)) return 0
    return Math.max(0, Math.min(100, Math.round(value)))
}

export function capacityPercent(course: Course) {
    if (!course.maxStudents) return 0
    return clampPercent((course.currentStudents / course.maxStudents) * 100)
}

export function accuracyPercent(session: PracticeSession) {
    if (!session.answeredCount) return 0
    return clampPercent((session.correctCount / session.answeredCount) * 100)
}

export function shortLabel(value: string, maxLength = 8) {
    return value.length > maxLength ? `${value.slice(0, maxLength)}...` : value
}

export function formatDashboardNumber(value: number, locale: string) {
    return new Intl.NumberFormat(locale).format(value)
}

export function formatDashboardDateTime(value: string | null | undefined, locale: string) {
    if (!value) return '-'
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return '-'
    return new Intl.DateTimeFormat(locale, {
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
    }).format(date)
}

export function formatDashboardMonthDay(value: string | null | undefined, locale: string) {
    if (!value) return '-'
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return '-'
    return new Intl.DateTimeFormat(locale, {
        month: '2-digit',
        day: '2-digit',
    }).format(date)
}

export function formatDashboardRelativeTime(timestamp: number, locale: string, now = Date.now()) {
    const diff = now - timestamp
    const minute = 60 * 1000
    const hour = 60 * minute
    const day = 24 * hour
    const formatter = new Intl.RelativeTimeFormat(locale, {numeric: 'auto'})

    if (diff < hour) return formatter.format(-Math.max(1, Math.round(diff / minute)), 'minute')
    if (diff < day) return formatter.format(-Math.round(diff / hour), 'hour')
    return formatter.format(-Math.round(diff / day), 'day')
}

export function formatSessionRange(session: ClassSession, locale: string) {
    return `${formatDashboardDateTime(session.scheduledStartAt, locale)} - ${formatDashboardDateTime(session.scheduledEndAt, locale)}`
}

export function progressBuckets(values: Array<number | null | undefined>): ChartDatum[] {
    return [
        {label: '0-30%', value: values.filter((value) => clampPercent(value) < 30).length},
        {label: '30-70%', value: values.filter((value) => clampPercent(value) >= 30 && clampPercent(value) < 70).length},
        {label: '70-100%', value: values.filter((value) => clampPercent(value) >= 70).length},
    ]
}

export function toChartData(items: Array<{ label: string; value: number }> = []): ChartDatum[] {
    return items.map((item) => ({label: item.label, value: item.value}))
}

export function normalizeDashboardLevel(level?: string | null): DashboardLevel {
    if (level === 'warning' || level === 'critical' || level === 'live') return level
    return 'normal'
}

