<template>
  <div class="dashboard-empty-state">
    {{ text }}
  </div>
</template>

<script lang="ts" setup>
defineProps<{
    text: string
}>()
</script>

