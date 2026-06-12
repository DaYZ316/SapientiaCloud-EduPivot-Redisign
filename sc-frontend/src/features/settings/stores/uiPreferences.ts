import {computed, ref} from 'vue'
import {defineStore} from 'pinia'

import type {ThemePreference} from '@/features/user/types/user'

const LAYOUT_MODE_KEY = 'edupivot.layoutMode'
const SIDEBAR_COLLAPSED_KEY = 'edupivot.sidebarCollapsed'
const THEME_PREFERENCE_KEY = 'edupivot.themePreference'
const DARK_THEME_MEDIA_QUERY = '(prefers-color-scheme: dark)'

export type LayoutMode = 'topbar' | 'sidebar'
type ResolvedTheme = 'light' | 'dark'

function readLayoutMode(): LayoutMode {
  const storedMode = localStorage.getItem(LAYOUT_MODE_KEY)
  return storedMode === 'sidebar' ? 'sidebar' : 'topbar'
}

function readSidebarCollapsed() {
  return localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === 'true'
}

function readThemePreference(): ThemePreference {
  const storedTheme = localStorage.getItem(THEME_PREFERENCE_KEY)
  return storedTheme === 'light' || storedTheme === 'dark' ? storedTheme : 'system'
}

function resolveTheme(preference: ThemePreference): ResolvedTheme {
  if (preference === 'light' || preference === 'dark') {
    return preference
  }

  return window.matchMedia?.(DARK_THEME_MEDIA_QUERY).matches ? 'dark' : 'light'
}

export const useUiPreferencesStore = defineStore('uiPreferences', () => {
  const layoutMode = ref<LayoutMode>(readLayoutMode())
  const sidebarCollapsed = ref(readSidebarCollapsed())
  const themePreference = ref<ThemePreference>(readThemePreference())
  const resolvedTheme = ref<ResolvedTheme>(resolveTheme(themePreference.value))
  const isSidebarLayout = computed(() => layoutMode.value === 'sidebar')
  let systemThemeListenerInitialized = false

  function applyTheme() {
    const nextTheme = resolveTheme(themePreference.value)
    resolvedTheme.value = nextTheme
    document.documentElement.dataset.theme = nextTheme
    document.documentElement.dataset.themePreference = themePreference.value
    document.documentElement.style.colorScheme = nextTheme
  }

  function handleSystemThemeChange() {
    if (themePreference.value === 'system') {
      applyTheme()
    }
  }

  function initializeTheme() {
    applyTheme()
    if (!systemThemeListenerInitialized && window.matchMedia) {
      window.matchMedia(DARK_THEME_MEDIA_QUERY).addEventListener('change', handleSystemThemeChange)
      systemThemeListenerInitialized = true
    }
  }

  function setThemePreference(preference: ThemePreference) {
    themePreference.value = preference
    localStorage.setItem(THEME_PREFERENCE_KEY, preference)
    applyTheme()
  }

  function setLayoutMode(mode: LayoutMode) {
    layoutMode.value = mode
    localStorage.setItem(LAYOUT_MODE_KEY, mode)
  }

  function toggleLayoutMode() {
    setLayoutMode(isSidebarLayout.value ? 'topbar' : 'sidebar')
  }

  function setSidebarCollapsed(collapsed: boolean) {
    sidebarCollapsed.value = collapsed
    localStorage.setItem(SIDEBAR_COLLAPSED_KEY, String(collapsed))
  }

  function toggleSidebarCollapsed() {
    setSidebarCollapsed(!sidebarCollapsed.value)
  }

  return {
    layoutMode,
    sidebarCollapsed,
    themePreference,
    resolvedTheme,
    isSidebarLayout,
    initializeTheme,
    setThemePreference,
    setLayoutMode,
    toggleLayoutMode,
    setSidebarCollapsed,
    toggleSidebarCollapsed,
  }
})
