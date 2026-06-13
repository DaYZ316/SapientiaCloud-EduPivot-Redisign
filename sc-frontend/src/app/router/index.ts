import {createRouter, createWebHistory} from 'vue-router'

import {useAuthStore} from '@/features/auth/stores/auth'
import {notify} from '@/shared/composables/useGlobalNotification'
import {i18n} from '@/app/i18n'
import MainLayout from '@/layouts/MainLayout.vue'
import LoginView from '@/features/auth/views/LoginView.vue'
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
                    path: 'courses',
                    name: 'courses',
                    component: () => import('@/features/course/views/CoursesView.vue'),
                },
                {
                    path: 'courses/:id',
                    name: 'course-detail',
                    component: () => import('@/features/course/views/CourseDetailView.vue'),
                },
                {
                    path: 'courses/:courseId/chapters/:chapterId',
                    name: 'chapter-detail',
                    component: () => import('@/features/course/views/ChapterView.vue'),
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
                    path: 'question-banks/:id/practice',
                    name: 'question-bank-practice',
                    component: () => import('@/features/question-bank/views/PracticeView.vue'),
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
                    meta: {requiredRole: 2},
                },
                {
                    path: 'community',
                    name: 'community',
                    component: () => import('@/features/forum/views/ForumListView.vue'),
                },
                {
                    path: 'community/forums/:id',
                    name: 'forum-detail',
                    component: () => import('@/features/forum/views/ForumDetailView.vue'),
                },
                {
                    path: 'community/posts/:id',
                    name: 'post-detail',
                    component: () => import('@/features/forum/views/PostDetailView.vue'),
                },
                {
                    path: 'profile',
                    name: 'profile',
                    component: () => import('@/features/user/views/ProfileView.vue'),
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
            ],
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

    const requiredRole = to.meta.requiredRole as number | undefined
    if (requiredRole !== undefined && authStore.user) {
        const userRole = authStore.user.role
        if (userRole !== 0 && userRole !== requiredRole) {
            return {name: 'dashboard'}
        }
    }

    return true
})
