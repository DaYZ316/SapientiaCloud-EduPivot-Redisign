<template>
  <div v-if="notifications.length" class="notification-rows">
    <div
      v-for="item in notifications"
      :key="item.id"
      class="notification-row"
      :class="{unread: !item.read}"
    >
      <span>{{ item.type === 1 ? t('success.dashboard.labels.teaching') : t('success.dashboard.labels.system') }}</span>
      <strong>{{ item.title }}</strong>
      <small>{{ formatDashboardDateTime(item.createdAt, String(locale)) }}</small>
    </div>
  </div>
  <DashboardEmptyState v-else :text="t('success.dashboard.empty.notifications')" />
</template>

<script lang="ts" setup>
import {useI18n} from 'vue-i18n'

import type {DashboardNotificationItem} from '@/features/dashboard/api/dashboard'
import DashboardEmptyState from '@/features/dashboard/components/DashboardEmptyState.vue'
import {formatDashboardDateTime} from '@/features/dashboard/utils/dashboardFormatters'

defineProps<{
    notifications: DashboardNotificationItem[]
}>()

const {t, locale} = useI18n()
</script>

