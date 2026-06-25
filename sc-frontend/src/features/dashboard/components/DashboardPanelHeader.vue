<template>
  <header class="dashboard-panel-header">
    <h2>{{ title }}</h2>
    <RouterLink v-if="to" class="dashboard-panel-link" :to="to">
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

