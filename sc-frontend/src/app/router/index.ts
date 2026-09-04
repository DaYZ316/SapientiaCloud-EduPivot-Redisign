import {createRouter, createWebHistory} from 'vue-router'

import {useAuthStore} from '@/features/auth/stores/auth'
import {notify} from '@/shared/composables/useGlobalNotification'
import {i18n} from '@/app/i18n'
import MainLayout from '@/layouts/MainLayout.vue'
import LoginView from '@/features/auth/views/LoginView.vue'
import GitHubCallbackView from '@/features/auth/views/GitHubCallbackView.vue'
import SuccessView from '@/features/dashboard/views/SuccessView.vue'

export const router = createRouter({
    history: createWebHistory(),
    routes: [
        {
            path: '/',
            redirect: (to) => ({
                path: '/login',
                query: to.query,
                hash: to.hash,
            }),
        },
        {
            path: '/login',
            name: 'login',
            component: LoginView,
            meta: {
                guestOnly: true,
            },
        },
        {
            path: '/oauth/github/callback',
            name: 'github-oauth-callback',
            component: GitHubCallbackView,
            meta: {
                guestOnly: true,
            },
        },
        {
            path: '/oauth/github/mobile/callback',
            name: 'github-mobile-oauth-callback',
            component: GitHubCallbackView,
            meta: {
                guestOnly: true,
            },
        },
        {
            path: '/onboarding',
            name: 'onboarding',
            component: () => import('@/features/auth/views/OnboardingView.vue'),
            meta: {
                requiresAuth: true,
            },
        },
        {
            path: '/',
            component: MainLayout,
            meta: {
                requiresAuth: true,
            },
            children: [
                {
                    path: 'dashboard',
                    name: 'dashboard',
                    component: SuccessView,
                },
                {
                    path: 'success',
                    redirect: '/dashboard',
                },
                {
                    path: 'notifications',
                    name: 'notifications',
                    component: () => import('@/features/notification/views/NotificationsView.vue'),
                },
                {
                    path: 'ai',
                    name: 'ai-workspace',
                    component: () => import('@/features/ai/views/AiWorkspaceView.vue'),
                },
                {
                    path: 'ai/history',
                    name: 'ai-history',
                    component: () => import('@/features/ai/views/AiHistoryView.vue'),
                },
                {
                    path: 'ai/favorites',
                    name: 'ai-favorites',
                    component: () => import('@/features/ai/views/AiFavoritesView.vue'),
                },
                {
                    path: 'ai/live-summaries',
                    name: 'ai-live-summaries',
                    component: () => import('@/features/ai/views/AiLiveSummariesView.vue'),
                },
                {
                    path: 'courses',
                    name: 'courses',
                    component: () => import('@/features/course/views/CoursesView.vue'),
                },
                {
                    path: 'courses/:courseId/chapter-detail',
                    name: 'chapter-detail',
                    component: () => import('@/features/course/views/ChapterView.vue'),
                },
                {
                    path: 'courses/:id/class-sessions',
                    name: 'course-class-sessions',
                    component: () => import('@/features/course/views/CourseClassSessionsView.vue'),
                },
                {
                    path: 'courses/:courseId/live-practices/:groupId',
                    name: 'course-live-practice-detail',
                    component: () => import('@/features/course/views/CourseLivePracticeDetailView.vue'),
                },
                {
                    path: 'courses/:id',
                    component: () => import('@/features/course/views/CourseDetailView.vue'),
                    children: [
                        {
                            path: '',
                            redirect: () => ({name: 'course-overview'}),
                        },
                        {
                            path: 'overview',
                            name: 'course-overview',
                            component: () => import('@/features/course/views/course-detail/CourseOverview.vue'),
                        },
                        {
                            path: 'chapters',
                            name: 'course-chapters',
                            component: () => import('@/features/course/views/course-detail/CourseChapters.vue'),
                        },
                        {
                            path: 'forums',
                            name: 'course-forums',
                            component: () => import('@/features/course/views/course-detail/CourseForums.vue'),
                        },
                        {
                            path: 'banks',
                            name: 'course-banks',
                            component: () => import('@/features/course/views/course-detail/CourseBanks.vue'),
                        },
                        {
                            path: 'live-practices',
                            name: 'course-live-practices',
                            component: () => import('@/features/course/views/course-detail/CourseLivePractices.vue'),
                        },
                        {
                            path: 'files',
                            name: 'course-files',
                            component: () => import('@/features/course/views/course-detail/CourseFiles.vue'),
                        },
                        {
                            path: 'students',
                            name: 'course-students',
                            component: () => import('@/features/course/views/course-detail/CourseStudents.vue'),
                        },
                        {
                            path: 'assistants',
                            name: 'course-assistants',
                            component: () => import('@/features/course/views/course-detail/CourseAssistants.vue'),
                        },
                    ],
                },
                {
                    path: 'courses/:courseId/question-banks',
                    name: 'course-question-banks',
                    component: () => import('@/features/question-bank/views/QuestionBankListView.vue'),
                },
                {
                    path: 'question-banks/:id',
                    name: 'question-bank-detail',
                    component: () => import('@/features/question-bank/views/QuestionBankDetailView.vue'),
                },
                {
                    path: 'my-enrollments',
                    name: 'my-enrollments',
                    component: () => import('@/features/course/views/MyEnrollmentsView.vue'),
                    meta: {requiredRole: 1},
                },
                {
                    path: 'teacher/courses',
                    name: 'teacher-courses',
                    component: () => import('@/features/course/views/CourseManagementView.vue'),
                    meta: {requiredRole: 2},
                },
                {
                    path: 'invitations',
                    name: 'invitations',
                    component: () => import('@/features/course/views/InvitationsView.vue'),
                    meta: {requiredRole: 2},
                },
                {
                    path: 'enrollment-management',
                    name: 'enrollment-management',
                    component: () => import('@/features/course/views/EnrollmentManagementView.vue'),
                    meta: {requiredRole: 2},
                },
                {
                    path: 'course-management',
                    name: 'course-management',
                    component: () => import('@/features/course/views/CourseManagementView.vue'),
                    meta: {requiredRole: 0},
                },
                {
                    path: 'profile',
                    name: 'profile',
                    component: () => import('@/features/user/views/ProfileView.vue'),
                },
                {
                    path: 'profile/:userId',
                    name: 'user-profile',
                    component: () => import('@/features/user/views/UserProfileView.vue'),
                },
                {
                    path: 'settings',
                    name: 'settings',
                    component: () => import('@/features/settings/views/SettingsView.vue'),
                },
                {
                    path: 'admin/users',
                    name: 'admin-users',
                    component: () => import('@/features/admin/views/UserManagementView.vue'),
                    meta: {requiredRole: 0},
                },
                {
                    path: 'admin/students',
                    name: 'admin-students',
                    component: () => import('@/features/admin/views/StudentListView.vue'),
                    meta: {requiredRole: 0},
                },
                {
                    path: 'admin/teachers',
                    name: 'admin-teachers',
                    component: () => import('@/features/admin/views/TeacherListView.vue'),
                    meta: {requiredRole: 0},
                },
                {
                    path: 'file-preview',
                    name: 'file-preview',
                    component: () => import('@/features/file-preview/views/FilePreviewView.vue'),
                },
            ],
        },
        {
            path: '/class-sessions/:sessionId',
            name: 'class-session-room',
            component: () => import('@/features/course/views/ClassSessionRoomView.vue'),
            meta: {
                requiresAuth: true,
            },
        },
        {
            path: '/class-sessions/:sessionId/live',
            name: 'class-session-live',
            component: () => import('@/features/classroom/views/ClassSessionLiveView.vue'),
            meta: {
                requiresAuth: true,
            },
        },
        {
            path: '/:pathMatch(.*)*',
            name: 'not-found',
            redirect: '/dashboard',
        },
    ],
})

router.onError((error) => {
    if (error.message?.includes('Failed to fetch dynamically imported module')
        || error.message?.includes('Importing a module script failed')
        || error.message?.includes('ChunkLoadError')) {
        notify.error(i18n.global.t('common.request.networkError'))
    } else {
        console.error('[Router Error]', error)
    }
})

router.beforeEach((to) => {
    const authStore = useAuthStore()

    if (to.meta.requiresAuth && !authStore.isAuthenticated) {
        return {
            name: 'login',
            query: {
                redirect: to.fullPath,
            },
        }
    }

    if (to.meta.guestOnly && authStore.isAuthenticated) {
        return {
            name: 'dashboard',
        }
    }

    if (authStore.isAuthenticated && needsOnboarding()) {
        if (to.name !== 'onboarding') {
            return {
                name: 'onboarding',
                query: {
                    redirect: to.fullPath,
                },
            }
        }
        return true
    }

    if (to.name === 'onboarding' && authStore.isAuthenticated && !needsOnboarding()) {
        return {
            name: 'dashboard',
        }
    }

    if (to.name === 'course-management' && authStore.user?.role === 2) {
        return {
            name: 'teacher-courses',
            query: {role: 'primary'},
        }
    }

    const requiredRole = to.meta.requiredRole as number | undefined
    if (requiredRole !== undefined && authStore.user) {
        const userRole = authStore.user.role
        if (userRole !== 0 && userRole !== requiredRole) {
            return {name: 'dashboard'}
        }
    }

    return true
})

function needsOnboarding() {
    const user = useAuthStore().user
    return Boolean(user && (user.role == null || !user.displayName?.trim()))
}
