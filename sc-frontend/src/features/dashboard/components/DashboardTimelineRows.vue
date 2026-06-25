<template>
  <div v-if="items.length" class="timeline-rows">
    <div
      v-for="(item, index) in items"
      :key="`${item.title}-${index}`"
      class="timeline-row"
      :class="normalizeDashboardLevel(item.level)"
    >
      <span class="timeline-marker"></span>
      <div>
        <strong>{{ item.title }}</strong>
        <small>{{ item.detail }}</small>
      </div>
      <time>{{ item.time }}</time>
    </div>
  </div>
  <DashboardEmptyState v-else :text="emptyText" />
</template>

<script lang="ts" setup>
import DashboardEmptyState from '@/features/dashboard/components/DashboardEmptyState.vue'
import {normalizeDashboardLevel} from '@/features/dashboard/utils/dashboardFormatters'

defineProps<{
    items: Array<{
        title: string
        detail: string
        time: string
        level?: string | null
    }>
    emptyText: string
}>()
</script>

