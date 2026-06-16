<template>
  <div class="tab-content">
    <div class="settings-workspace">
      <header class="settings-hero">
        <h2>{{ t('settings.privacy.title') }}</h2>
        <div class="settings-hero-actions">
          <button class="btn-secondary" type="button" @click="resetPrivacySettings">
            {{ t('settings.resetChanges') }}
          </button>
          <button class="btn-primary" type="button" @click="savePrivacySettings">
            {{ t('settings.privacy.saveSettings') }}
          </button>
        </div>
      </header>

      <section class="settings-panel">
        <div class="settings-panel-body">
          <div v-for="item in privacyItems" :key="item.key" class="setting-item">
            <div class="setting-info">
              <p class="setting-title">{{ item.title }}</p>
              <p class="setting-desc">{{ item.description }}</p>
            </div>
            <label class="toggle">
              <input v-model="privacySettings[item.key]" type="checkbox"/>
              <span class="toggle-slider"></span>
            </label>
          </div>

          <p v-if="privacyMessage" class="form-message">{{ privacyMessage }}</p>
        </div>
      </section>

      <section class="settings-panel">
        <header class="settings-panel-header">
          <h3>{{ t('settings.dataExport.title') }}</h3>
        </header>
        <div class="settings-panel-body">
          <div class="setting-item">
            <div class="setting-info">
              <p class="setting-title">{{ t('settings.dataExport.title') }}</p>
              <p class="setting-desc">{{ t('settings.dataExport.description') }}</p>
            </div>
            <button class="btn-secondary" type="button" @click="requestDataExport">
              <Download :size="16" stroke-width="1.8"/>
              {{ t('settings.dataExport.button') }}
            </button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {computed, reactive, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {Download} from 'lucide-vue-next'

type PrivacyKey = 'publicProfile' | 'showOnline' | 'showProgress'

const {t} = useI18n()

const defaultPrivacySettings: Record<PrivacyKey, boolean> = {
  publicProfile: true,
  showOnline: true,
  showProgress: true,
}

const privacySettings = reactive<Record<PrivacyKey, boolean>>({...defaultPrivacySettings})
const privacyMessage = ref('')

const privacyItems = computed(() => [
  {
    key: 'publicProfile' as PrivacyKey,
    title: t('settings.privacy.publicProfile'),
    description: t('settings.privacy.publicProfileDesc'),
  },
  {
    key: 'showOnline' as PrivacyKey,
    title: t('settings.privacy.showOnline'),
    description: t('settings.privacy.showOnlineDesc'),
  },
  {
    key: 'showProgress' as PrivacyKey,
    title: t('settings.privacy.showProgress'),
    description: t('settings.privacy.showProgressDesc'),
  },
])

function savePrivacySettings() {
  privacyMessage.value = t('settings.privacy.saved')
}

function resetPrivacySettings() {
  Object.assign(privacySettings, defaultPrivacySettings)
  privacyMessage.value = ''
}

function requestDataExport() {
  privacyMessage.value = t('settings.dataExport.submitted')
}
</script>

<style scoped src="../styles/settings-page.css"></style>
