<template>
  <div class="tab-content">
    <div class="settings-workspace">
      <header class="settings-hero">
        <h2>{{ t('settings.desktop.title') }}</h2>
        <p class="settings-hero-copy">{{ t('settings.desktop.description') }}</p>
      </header>

      <section class="settings-panel">
        <header class="settings-panel-header">
          <h3>{{ t('settings.desktop.environmentTitle') }}</h3>
        </header>
        <div class="settings-panel-body">
          <p class="help-text">{{ t('settings.desktop.environmentHelp') }}</p>
          <div class="environment-list">
            <label v-for="profile in profiles" :key="profile.id" class="environment-option">
              <input
                  :checked="profile.id === currentProfile?.id"
                  :disabled="selecting"
                  name="desktop-profile"
                  type="radio"
                  @change="selectProfile(profile.id)"
              >
              <span>
                <strong>{{ profile.name }}</strong>
                <small>{{ profile.apiOrigin }}</small>
              </span>
            </label>
          </div>
          <p v-if="message" :class="{error: isError}" class="form-message">{{ message }}</p>
        </div>
      </section>

      <section class="settings-panel">
        <header class="settings-panel-header">
          <h3>{{ t('settings.desktop.importTitle') }}</h3>
        </header>
        <div class="settings-panel-body">
          <p class="help-text">{{ t('settings.desktop.importHelp') }}</p>
          <label class="btn-secondary import-control">
            {{ importing ? t('settings.saving') : t('settings.desktop.importAction') }}
            <input accept="application/json,.json" :disabled="importing" type="file" @change="importProfile">
          </label>
        </div>
      </section>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'

import type {DesktopEnvironmentProfile} from '@/shared/platform/desktop'
import {desktopBridge} from '@/shared/platform/desktop'

const {t} = useI18n()
const profiles = ref<DesktopEnvironmentProfile[]>([])
const currentProfile = ref<DesktopEnvironmentProfile | null>(null)
const importing = ref(false)
const selecting = ref(false)
const message = ref('')
const isError = ref(false)

onMounted(loadProfiles)

async function loadProfiles() {
  const desktop = desktopBridge()
  if (!desktop) return

  try {
    const [available, current] = await Promise.all([
      desktop.profiles.list(),
      desktop.profiles.current(),
    ])
    profiles.value = available
    currentProfile.value = current
  } catch (error) {
    setMessage(error, true)
  }
}

async function importProfile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  importing.value = true
  try {
    await desktopBridge()?.profiles.import(await file.text())
    await loadProfiles()
    message.value = t('settings.desktop.importSuccess')
    isError.value = false
  } catch (error) {
    setMessage(error, true)
  } finally {
    importing.value = false
  }
}

async function selectProfile(id: string) {
  if (id === currentProfile.value?.id || selecting.value) return
  if (!window.confirm(t('settings.desktop.restartConfirm'))) return

  selecting.value = true
  try {
    await desktopBridge()?.profiles.select(id)
  } catch (error) {
    selecting.value = false
    setMessage(error, true)
  }
}

function setMessage(error: unknown, errorState: boolean) {
  message.value = error instanceof Error ? error.message : t('settings.desktop.operationFailed')
  isError.value = errorState
}
</script>

<style scoped src="../styles/settings-page.css"></style>

<style scoped>
.settings-hero-copy {
  max-width: 680px;
  margin: 10px 0 0;
  color: var(--color-muted);
  line-height: 1.6;
}

.environment-list {
  display: grid;
  gap: 12px;
}

.environment-option {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--color-outline-light);
  border-radius: 10px;
  cursor: pointer;
}

.environment-option:has(input:checked) {
  border-color: var(--color-primary);
  background: color-mix(in srgb, var(--color-primary) 8%, transparent);
}

.environment-option input {
  margin-top: 4px;
  accent-color: var(--color-primary);
}

.environment-option span {
  display: grid;
  gap: 4px;
}

.environment-option small {
  color: var(--color-muted);
  word-break: break-all;
}

.import-control {
  display: inline-flex;
  cursor: pointer;
}

.import-control input {
  display: none;
}

.form-message.error {
  color: var(--color-danger, #b42318);
}
</style>
