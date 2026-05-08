import { createI18n } from 'vue-i18n'

import enCommon from './en-US/common'
import enLogin from './en-US/login'
import enSuccess from './en-US/success'
import zhCommon from './zh-CN/common'
import zhLogin from './zh-CN/login'
import zhSuccess from './zh-CN/success'

const LOCALE_STORAGE_KEY = 'aeroverse.locale'

export const supportedLocales = ['zh-CN', 'en-US'] as const

export type SupportedLocale = (typeof supportedLocales)[number]

const messages = {
  'zh-CN': {
    common: zhCommon,
    login: zhLogin,
    success: zhSuccess,
  },
  'en-US': {
    common: enCommon,
    login: enLogin,
    success: enSuccess,
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
