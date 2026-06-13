<template>
  <div class="practice-progress">
    <div class="progress-bar">
      <div class="progress-fill" :style="{'width': percent + '%'}"></div>
    </div>
    <div class="progress-info">
      <span>{{ current }} / {{ total }}</span>
      <span>{{ Math.round(percent) }}%</span>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {computed} from 'vue'

const props = defineProps<{
  current: number
  total: number
}>()

const percent = computed(() => props.total > 0 ? (props.current / props.total) * 100 : 0)
</script>

<style scoped>
.practice-progress {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.progress-bar {
  height: 6px;
  background: var(--color-surface-container-high);
  border-radius: 3px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: var(--color-primary);
  border-radius: 3px;
  transition: width 0.3s ease;
}

.progress-info {
  display: flex;
  justify-content: space-between;
  font-family: var(--font-body);
  font-size: 12px;
  color: var(--color-muted);
}
</style>
