<template>
  <div class="settings-page">
    <header class="settings-page-header">
      <p class="settings-kicker">SapientiaCloud</p>
      <h1>{{ t('common.menu.settings') }}</h1>
      <p class="settings-page-subtitle">{{ t('settings.pageSubtitle') }}</p>
    </header>

    <div class="settings-layout">
      <nav class="settings-nav" :aria-label="t('common.menu.settings')">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          class="nav-tab"
          :class="{ active: activeTab === tab.id }"
          type="button"
          @click="activeTab = tab.id"
        >
          <component :is="tab.icon" :size="18" stroke-width="1.8"/>
          <span>{{ tab.label }}</span>
        </button>
      </nav>

      <section class="settings-content">
        <component :is="activeTabConfig.page"/>
      </section>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {computed, ref, type Component} from 'vue'
import {useI18n} from 'vue-i18n'
import {Bell, LayoutDashboard, Shield, User} from 'lucide-vue-next'

import AccountSettingsPage from './AccountSettingsPage.vue'
import AppearanceSettingsPage from './AppearanceSettingsPage.vue'
import NotificationsSettingsPage from './NotificationsSettingsPage.vue'
import PrivacySettingsPage from './PrivacySettingsPage.vue'

type SettingsTabId = 'account' | 'appearance' | 'notifications' | 'privacy'

interface SettingsTab {
  id: SettingsTabId
  label: string
  icon: Component
  page: Component
}

const {t} = useI18n()
const activeTab = ref<SettingsTabId>('account')

const tabs = computed<SettingsTab[]>(() => [
  {
    id: 'account',
    label: t('common.settings.account'),
    icon: User,
    page: AccountSettingsPage,
  },
  {
    id: 'appearance',
    label: t('common.settings.appearance'),
    icon: LayoutDashboard,
    page: AppearanceSettingsPage,
  },
  {
    id: 'notifications',
    label: t('common.settings.notifications'),
    icon: Bell,
    page: NotificationsSettingsPage,
  },
  {
    id: 'privacy',
    label: t('common.settings.privacy'),
    icon: Shield,
    page: PrivacySettingsPage,
  },
])

const activeTabConfig = computed<SettingsTab>(() =>
  tabs.value.find((tab) => tab.id === activeTab.value) ?? tabs.value[0],
)
</script>

<style scoped>
.settings-page {
  max-width: 1180px;
  margin: 0 auto;
}

.settings-page-header {
  margin-bottom: 28px;
}

.settings-kicker {
  margin: 0;
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-muted);
}

.settings-page h1 {
  margin: 10px 0 0;
  font-family: var(--font-heading);
  font-size: clamp(40px, 6vw, 56px);
  font-weight: 400;
  line-height: 1.3;
  color: var(--color-on-surface);
}

.settings-page-subtitle {
  max-width: 560px;
  margin: 12px 0 0;
  font-size: 16px;
  line-height: 1.5;
  color: var(--color-muted);
}

.settings-layout {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 28px;
  align-items: start;
}

.settings-nav {
  position: sticky;
  top: 88px;
  display: grid;
  gap: 8px;
  padding: 10px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 24px;
}

.nav-tab {
  min-height: 52px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 14px;
  border: 1px solid transparent;
  border-radius: 16px;
  background: transparent;
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 14px;
  font-weight: 700;
  text-align: left;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s, color 0.2s;
}

.nav-tab:hover {
  background: var(--color-surface-canvas);
  border-color: var(--color-outline-light);
  color: var(--color-on-surface);
}

.nav-tab.active {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-on-primary);
}

.settings-content {
  min-width: 0;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 32px;
  overflow: visible;
}

@media (max-width: 1024px) {
  .settings-layout {
    grid-template-columns: 1fr;
  }

  .settings-nav {
    position: static;
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .nav-tab {
    justify-content: center;
  }
}

@media (max-width: 768px) {
  .settings-nav {
    display: flex;
    overflow-x: auto;
    border-radius: 20px;
  }

  .nav-tab {
    min-width: 128px;
  }

  .settings-content {
    border-radius: 24px;
  }
}
</style>
