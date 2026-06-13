import {createI18n} from 'vue-i18n'

import enAdmin from '@/features/admin/i18n/en-US'
import enLogin from '@/features/auth/i18n/en-US'
import enCommunity from '@/features/community/i18n/en-US'
import enCourseDetail from '@/features/course/i18n/en-US/courseDetail'
import enCourses from '@/features/course/i18n/en-US/courses'
import enInvitations from '@/features/course/i18n/en-US/invitations'
import enEnrollmentManagement from '@/features/course/i18n/en-US/enrollmentManagement'
import enMyEnrollments from '@/features/course/i18n/en-US/myEnrollments'
import enChapter from '@/features/course/i18n/en-US/chapter'
import enForum from '@/features/forum/i18n/en-US/forum'
import enQuestionBank from '@/features/question-bank/i18n/en-US/questionBank'
import enSuccess from '@/features/dashboard/i18n/en-US'
import enNotifications from '@/features/notification/i18n/en-US'
import enSettings from '@/features/settings/i18n/en-US'
import enProfile from '@/features/user/i18n/en-US'
import enCommon from '@/shared/i18n/en-US'
import zhAdmin from '@/features/admin/i18n/zh-CN'
import zhLogin from '@/features/auth/i18n/zh-CN'
import zhCommunity from '@/features/community/i18n/zh-CN'
import zhCourseDetail from '@/features/course/i18n/zh-CN/courseDetail'
import zhCourses from '@/features/course/i18n/zh-CN/courses'
import zhInvitations from '@/features/course/i18n/zh-CN/invitations'
import zhEnrollmentManagement from '@/features/course/i18n/zh-CN/enrollmentManagement'
import zhMyEnrollments from '@/features/course/i18n/zh-CN/myEnrollments'
import zhChapter from '@/features/course/i18n/zh-CN/chapter'
import zhForum from '@/features/forum/i18n/zh-CN/forum'
import zhQuestionBank from '@/features/question-bank/i18n/zh-CN/questionBank'
import zhSuccess from '@/features/dashboard/i18n/zh-CN'
import zhNotifications from '@/features/notification/i18n/zh-CN'
import zhSettings from '@/features/settings/i18n/zh-CN'
import zhProfile from '@/features/user/i18n/zh-CN'
import zhCommon from '@/shared/i18n/zh-CN'

const LOCALE_STORAGE_KEY = 'edupivot.locale'

export const supportedLocales = ['zh-CN', 'en-US'] as const

export type SupportedLocale = (typeof supportedLocales)[number]

const messages = {
    'zh-CN': {
        common: zhCommon,
        login: zhLogin,
        success: zhSuccess,
        courses: zhCourses,
        courseDetail: zhCourseDetail,
        invitations: zhInvitations,
        enrollmentManagement: zhEnrollmentManagement,
        notifications: zhNotifications,
        myEnrollments: zhMyEnrollments,
        profile: zhProfile,
        settings: zhSettings,
        community: zhCommunity,
        admin: zhAdmin,
        chapter: zhChapter.chapter,
        forum: zhForum.forum,
        questionBank: zhQuestionBank.questionBank,
    },
    'en-US': {
        common: enCommon,
        login: enLogin,
        success: enSuccess,
        courses: enCourses,
        courseDetail: enCourseDetail,
        invitations: enInvitations,
        enrollmentManagement: enEnrollmentManagement,
        notifications: enNotifications,
        myEnrollments: enMyEnrollments,
        profile: enProfile,
        settings: enSettings,
        community: enCommunity,
        admin: enAdmin,
        chapter: enChapter.chapter,
        forum: enForum.forum,
        questionBank: enQuestionBank.questionBank,
    },
}

function isSupportedLocale(locale: string): locale is SupportedLocale {
    return supportedLocales.includes(locale as SupportedLocale)
}

function normalizeLocale(locale: string | null | undefined): SupportedLocale | null {
    if (!locale) {
        return null
    }

    if (isSupportedLocale(locale)) {
        return locale
    }

    const language = locale.toLowerCase()
    if (language.startsWith('zh')) {
        return 'zh-CN'
    }
    if (language.startsWith('en')) {
        return 'en-US'
    }

    return null
}

function resolveInitialLocale(): SupportedLocale {
    const storedLocale = normalizeLocale(localStorage.getItem(LOCALE_STORAGE_KEY))
    if (storedLocale) {
        return storedLocale
    }

    return normalizeLocale(navigator.language) ?? 'zh-CN'
}

export const i18n = createI18n({
    legacy: false,
    locale: resolveInitialLocale(),
    fallbackLocale: 'zh-CN',
    messages,
})

export function setLocale(locale: SupportedLocale) {
    i18n.global.locale.value = locale
    localStorage.setItem(LOCALE_STORAGE_KEY, locale)
    document.documentElement.lang = locale
}

setLocale(i18n.global.locale.value as SupportedLocale)
