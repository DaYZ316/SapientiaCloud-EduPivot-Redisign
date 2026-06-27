<template>
  <header class="dashboard-panel-header">
    <h2>{{ title }}</h2>
    <RouterLink v-if="to" :to="to" class="dashboard-panel-link">
      {{ actionLabel }}
    </RouterLink>
  </header>
</template>

<script lang="ts" setup>
import {RouterLink} from 'vue-router'

defineProps<{
  title: string
  actionLabel?: string
  to?: string
}>()
</script>

