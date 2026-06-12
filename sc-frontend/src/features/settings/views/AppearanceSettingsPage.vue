<template>
  <div class="tab-content">
    <div class="settings-workspace">
      <header class="settings-hero">
        <h2>{{ t('common.settings.appearance') }}</h2>
        <div class="settings-hero-actions">
          <button class="btn-secondary" type="button" @click="resetAppearanceDraft">
            {{ t('settings.resetChanges') }}
          </button>
          <button class="btn-primary" :disabled="savingAppearance" type="button" @click="saveAppearanceSettings">
            {{ savingAppearance ? t('settings.saving') : t('settings.saveChanges') }}
          </button>
        </div>
      </header>

      <section class="settings-panel">
        <header class="settings-panel-header">
          <h3>{{ t('common.settings.applicationLayout') }}</h3>
        </header>

        <div class="settings-panel-body">
          <div class="layout-choice-grid" role="radiogroup" :aria-label="t('common.settings.applicationLayout')">
            <button
              type="button"
              class="layout-choice-card"
              :class="{ active: uiPreferences.layoutMode === 'topbar' }"
              role="radio"
              :aria-checked="uiPreferences.layoutMode === 'topbar'"
              @click="uiPreferences.setLayoutMode('topbar')"
            >
              <span class="layout-choice-preview topbar-preview" aria-hidden="true">
                <span class="preview-window">
                  <span class="preview-bar"></span>
                  <span class="preview-line short"></span>
                  <span class="preview-line long"></span>
                  <span class="preview-block"></span>
                </span>
              </span>
              <span class="layout-choice-footer">
                <span>{{ t('common.settings.topBar') }}</span>
                <span class="layout-choice-dot"></span>
              </span>
            </button>
            <button
              type="button"
              class="layout-choice-card"
              :class="{ active: uiPreferences.layoutMode === 'sidebar' }"
              role="radio"
              :aria-checked="uiPreferences.layoutMode === 'sidebar'"
              @click="uiPreferences.setLayoutMode('sidebar')"
            >
              <span class="layout-choice-preview sidebar-preview" aria-hidden="true">
                <span class="preview-window">
                  <span class="preview-sidebar">
                    <span></span>
                    <span></span>
                  </span>
                  <span class="preview-content">
                    <span class="preview-rule"></span>
                    <span class="preview-line long"></span>
                    <span class="preview-block"></span>
                  </span>
                </span>
              </span>
              <span class="layout-choice-footer">
                <span>{{ t('common.settings.sidebar') }}</span>
                <span class="layout-choice-dot"></span>
              </span>
            </button>
          </div>

          <div class="setting-item">
            <p class="setting-title">{{ t('common.layout.collapseSidebar') }}</p>
            <label class="toggle">
              <input
                type="checkbox"
                :checked="uiPreferences.sidebarCollapsed"
                @change="handleSidebarCollapsedToggle"
              />
              <span class="toggle-slider"></span>
            </label>
          </div>
        </div>
      </section>

      <div class="settings-grid two-column">
        <section class="settings-panel">
          <header class="settings-panel-header">
            <h3>{{ t('settings.theme') }}</h3>
          </header>
          <div class="settings-panel-body">
            <div class="setting-item">
              <p class="setting-title">{{ t('settings.theme') }}</p>
              <BaseSelect
                :model-value="selectedTheme"
                :options="themeOptions"
                min-width="172px"
                @update:model-value="handleThemeSelect"
              />
            </div>
          </div>
        </section>

        <section class="settings-panel">
          <header class="settings-panel-header">
            <h3>{{ t('common.settings.language') }}</h3>
          </header>
          <div class="settings-panel-body">
            <div class="setting-item">
              <p class="setting-title">{{ t('common.settings.language') }}</p>
              <BaseSelect
                :model-value="locale"
                :options="languageOptions"
                min-width="172px"
                @update:model-value="handleLocaleSelect"
              />
            </div>
          </div>
        </section>
      </div>

      <p v-if="appearanceMessage" class="form-message">{{ appearanceMessage }}</p>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'

import {setLocale, type SupportedLocale} from '@/app/i18n'
import BaseSelect from '@/shared/components/BaseSelect.vue'
import {updateCurrentUser} from '@/features/user/api/user'
import {useAuthStore} from '@/features/auth/stores/auth'
import {notify} from '@/shared/composables/useGlobalNotification'
import {useUiPreferencesStore} from '@/features/settings/stores/uiPreferences'
import type {ThemePreference} from '@/features/user/types/user'

const authStore = useAuthStore()
const uiPreferences = useUiPreferencesStore()
const {t, locale} = useI18n()

const selectedTheme = ref<ThemePreference>(authStore.user?.theme ?? uiPreferences.themePreference)
const savingAppearance = ref(false)
const appearanceMessage = ref('')

const languageOptions = computed(() => [
  {label: '简体中文', value: 'zh-CN'},
  {label: 'English', value: 'en-US'},
])

const themeOptions = computed(() => [
  {label: t('settings.themeSystem'), value: 'system'},
  {label: t('settings.themeLight'), value: 'light'},
  {label: t('settings.themeDark'), value: 'dark'},
])

function handleSidebarCollapsedToggle(event: Event) {
  uiPreferences.setSidebarCollapsed((event.target as HTMLInputElement).checked)
}

function handleLocaleSelect(value: string | number | undefined) {
  if (value === 'zh-CN' || value === 'en-US') {
    setLocale(value as SupportedLocale)
  }
}

function handleThemeSelect(value: string | number | undefined) {
  if (value === 'system' || value === 'light' || value === 'dark') {
    selectedTheme.value = value
    uiPreferences.setThemePreference(value)
  }
}

function resetAppearanceDraft() {
  selectedTheme.value = authStore.user?.theme ?? uiPreferences.themePreference
  uiPreferences.setThemePreference(selectedTheme.value)
  appearanceMessage.value = ''
}

async function saveAppearanceSettings() {
  savingAppearance.value = true
  appearanceMessage.value = ''

  try {
    const updatedUser = await updateCurrentUser({theme: selectedTheme.value})
    authStore.setUser(updatedUser)
    appearanceMessage.value = t('settings.saved')
    notify.success(t('settings.saved'))
  } catch (error) {
    const msg = error instanceof Error ? error.message : t('settings.saveFailed')
    appearanceMessage.value = msg
    notify.error(msg)
  } finally {
    savingAppearance.value = false
  }
}
</script>

<style scoped src="../styles/settings-page.css"></style>
