<template>
  <div class="tab-content">
    <div class="settings-workspace">
      <header class="settings-hero">
        <h2>{{ t('common.settings.appearance') }}</h2>
        <div class="settings-hero-actions">
          <button class="btn-secondary" type="button" @click="resetAppearanceDraft">
            {{ t('settings.resetChanges') }}
          </button>
          <button :disabled="savingAppearance" class="btn-primary" type="button" @click="saveAppearanceSettings">
            {{ savingAppearance ? t('settings.saving') : t('settings.saveChanges') }}
          </button>
        </div>
      </header>

      <section class="settings-panel">
        <header class="settings-panel-header">
          <h3>{{ t('common.settings.applicationLayout') }}</h3>
        </header>

        <div class="settings-panel-body">
          <div :aria-label="t('common.settings.applicationLayout')" class="layout-choice-grid" role="radiogroup">
            <button
                :aria-checked="uiPreferences.layoutMode === 'topbar'"
                :class="{ active: uiPreferences.layoutMode === 'topbar' }"
                class="layout-choice-card"
                role="radio"
                type="button"
                @click="uiPreferences.setLayoutMode('topbar')"
            >
              <span aria-hidden="true" class="layout-choice-preview topbar-preview">
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
                :aria-checked="uiPreferences.layoutMode === 'sidebar'"
                :class="{ active: uiPreferences.layoutMode === 'sidebar' }"
                class="layout-choice-card"
                role="radio"
                type="button"
                @click="uiPreferences.setLayoutMode('sidebar')"
            >
              <span aria-hidden="true" class="layout-choice-preview sidebar-preview">
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
                  :checked="uiPreferences.sidebarCollapsed"
                  type="checkbox"
                  @change="handleSidebarCollapsedToggle"
              />
              <span class="toggle-slider"></span>
            </label>
          </div>
        </div>
      </section>

      <section class="settings-panel">
        <header class="settings-panel-header settings-panel-header--with-description">
          <div>
            <h3>{{ t('settings.aiLauncher.title') }}</h3>
            <p class="setting-desc">{{ t('settings.aiLauncher.description') }}</p>
          </div>
        </header>

        <div class="settings-panel-body">
          <div :aria-label="t('settings.aiLauncher.title')" class="ai-launcher-choice-grid" role="radiogroup">
            <button
                v-for="option in aiLauncherOptions"
                :key="option.value"
                :aria-checked="uiPreferences.aiLauncherAppearance === option.value"
                :class="{ active: uiPreferences.aiLauncherAppearance === option.value }"
                class="ai-launcher-choice-card"
                role="radio"
                type="button"
                @click="uiPreferences.setAiLauncherAppearance(option.value)"
            >
              <span aria-hidden="true" class="ai-launcher-choice-icon">
                <component :is="option.icon" :size="24" stroke-width="1.8"/>
              </span>
              <span class="ai-launcher-choice-copy">
                <span class="ai-launcher-choice-label">{{ option.label }}</span>
                <span class="ai-launcher-choice-description">{{ option.description }}</span>
              </span>
              <span aria-hidden="true" class="ai-launcher-choice-indicator">
                <Check v-if="uiPreferences.aiLauncherAppearance === option.value" :size="13" stroke-width="2.5"/>
              </span>
            </button>
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
import {Check, EyeOff, MessageCircle, WandSparkles} from 'lucide-vue-next'

import {setLocale, type SupportedLocale} from '@/app/i18n'
import BaseSelect from '@/shared/components/BaseSelect.vue'
import {updateCurrentUser} from '@/features/user/api/user'
import {useAuthStore} from '@/features/auth/stores/auth'
import {notify} from '@/shared/composables/useGlobalNotification'
import {type AiLauncherAppearance, useUiPreferencesStore} from '@/features/settings/stores/uiPreferences'
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

const aiLauncherOptions = computed<Array<{
  label: string
  description: string
  value: AiLauncherAppearance
  icon: typeof MessageCircle
}>>(() => [
  {
    label: t('settings.aiLauncher.twoDimensional'),
    description: t('settings.aiLauncher.twoDimensionalDescription'),
    value: 'two-dimensional',
    icon: MessageCircle,
  },
  {
    label: t('settings.aiLauncher.brush'),
    description: t('settings.aiLauncher.brushDescription'),
    value: 'brush',
    icon: WandSparkles,
  },
  {
    label: t('settings.aiLauncher.hidden'),
    description: t('settings.aiLauncher.hiddenDescription'),
    value: 'hidden',
    icon: EyeOff,
  },
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

<style scoped>
.settings-panel-header--with-description > div {
  display: grid;
  gap: 7px;
}

.ai-launcher-choice-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.ai-launcher-choice-card {
  min-width: 0;
  min-height: 132px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 18px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-md);
  color: var(--color-on-surface);
  text-align: left;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s, box-shadow 0.2s, transform 0.2s;
}

.ai-launcher-choice-card:hover {
  border-color: var(--color-outline);
  box-shadow: 0 8px 20px color-mix(in srgb, var(--color-on-primary) 8%, transparent);
}

.ai-launcher-choice-card.active {
  background: var(--color-primary-container);
  border-color: var(--color-primary);
  box-shadow: 0 0 0 1px var(--color-primary);
}

.ai-launcher-choice-card:active {
  transform: translateY(1px);
}

.ai-launcher-choice-card:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 3px;
}

.ai-launcher-choice-icon {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border: 1px solid var(--color-outline-light);
  border-radius: 12px;
  background: var(--color-surface-canvas);
  color: var(--color-on-surface);
}

.ai-launcher-choice-card.active .ai-launcher-choice-icon {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.ai-launcher-choice-copy {
  min-width: 0;
  display: grid;
  gap: 6px;
}

.ai-launcher-choice-label {
  font-family: var(--font-label);
  font-size: 14px;
  font-weight: 800;
  color: var(--color-on-surface);
}

.ai-launcher-choice-description {
  font-family: var(--font-body);
  font-size: 12px;
  line-height: 1.45;
  color: var(--color-muted);
}

.ai-launcher-choice-indicator {
  width: 20px;
  height: 20px;
  display: grid;
  place-items: center;
  align-self: start;
  border: 1px solid var(--color-outline);
  border-radius: 50%;
  color: transparent;
}

.ai-launcher-choice-card.active .ai-launcher-choice-indicator {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: var(--color-on-primary);
}

@media (max-width: 900px) {
  .ai-launcher-choice-grid {
    grid-template-columns: 1fr;
  }

  .ai-launcher-choice-card {
    min-height: 92px;
  }
}
</style>
