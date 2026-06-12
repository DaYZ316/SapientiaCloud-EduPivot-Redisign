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
                    path: 'my-enrollments',
                    name: 'my-enrollments',
                    component: () => import('@/features/course/views/MyEnrollmentsView.vue'),
                },
                {
                    path: 'teacher/courses',
                    name: 'teacher-courses',
                    component: () => import('@/features/course/views/CourseManagementView.vue'),
                },
                {
                    path: 'course-management',
                    name: 'course-management',
                    component: () => import('@/features/course/views/CourseManagementView.vue'),
                },
                {
                    path: 'community',
                    name: 'community',
                    component: () => import('@/features/community/views/CommunityView.vue'),
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
                },
                {
                    path: 'admin/students',
                    name: 'admin-students',
                    component: () => import('@/features/admin/views/StudentListView.vue'),
                },
                {
                    path: 'admin/teachers',
                    name: 'admin-teachers',
                    component: () => import('@/features/admin/views/TeacherListView.vue'),
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

    return true
})
