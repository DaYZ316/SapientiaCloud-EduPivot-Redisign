import {request} from '@/shared/api/request'
import type {ClassSession} from '@/features/course/types/classSession'
import type {Course, Enrollment} from '@/features/course/types/course'
import type {PracticeSession} from '@/features/question-bank/types/practiceSession'

export interface DashboardChartPoint {
    label: string
    value: number
}

export interface DashboardNotificationItem {
    id: string
    type: number | null
    title: string
    createdAt: string
    read: boolean
}

export interface DashboardNotificationSummary {
    unreadTotal: number
    unreadSystem: number
    unreadTeaching: number
    recent: DashboardNotificationItem[]
    distribution: DashboardChartPoint[]
}

export interface DashboardUserActivity {
    id: string
    name: string | null
    email: string | null
    createdAt: string | null
    lastLoginAt: string | null
}

export interface DashboardUserSummary {
    totalUsers: number
    students: number
    teachers: number
    admins: number
    disabledUsers: number
    todayUsers: number
    incompleteProfiles: number
    oauthPercent: number
    roleDistribution: DashboardChartPoint[]
    recentUsers: DashboardUserActivity[]
}

export interface DashboardSummaryItem {
    label: string
    value: string
    meta: string
}

export interface DashboardTimelineItem {
    title: string
    detail: string
    time: string
    level: string | null
}

export interface DashboardRiskItem {
    title: string
    detail: string
    status: string
    level: string
    icon: string
}

export interface DashboardCapacityItem {
    id: string
    title: string
    value: number
    detail: string
}

export interface DashboardQuestionCoverage {
    courseId: string
    courseTitle: string
    bankCount: number
    questionCount: number
}

export interface DashboardAction {
    title: string
    detail: string
    to: string
    icon: string
}

export interface AdminDashboard {
    users: DashboardUserSummary
    notifications: DashboardNotificationSummary
    courses: number
    publishedCourses: number
    courseStatus: DashboardChartPoint[]
    courseCapacity: DashboardCapacityItem[]
    resources: DashboardSummaryItem[]
    activities: DashboardTimelineItem[]
    risks: DashboardRiskItem[]
}

export interface TeacherDashboard {
    notifications: DashboardNotificationSummary
    primaryCourses: Course[]
    assistantCourses: Course[]
    courses: Course[]
    sessions: ClassSession[]
    liveSession: ClassSession | null
    pending: DashboardTimelineItem[]
    insights: DashboardSummaryItem[]
    questionCoverage: DashboardQuestionCoverage[]
    capacityRanking: DashboardCapacityItem[]
}

export interface StudentDashboard {
    notifications: DashboardNotificationSummary
    enrollments: Enrollment[]
    sessions: ClassSession[]
    recommendations: Course[]
    ongoingSessions: ClassSession[]
    liveSession: ClassSession | null
    continueCourse: Enrollment | null
    practiceFocus: DashboardAction
    practiceSessions: PracticeSession[]
    todos: DashboardTimelineItem[]
}

export type DashboardResponse =
    | { role: 0; admin: AdminDashboard; student: null; teacher: null }
    | { role: 1; admin: null; student: StudentDashboard; teacher: null }
    | { role: 2; admin: null; student: null; teacher: TeacherDashboard }

export function getMyDashboard() {
    return request<DashboardResponse>({method: 'GET', url: '/api/dashboard/me'})
}
