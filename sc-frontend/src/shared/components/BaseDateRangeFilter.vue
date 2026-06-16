<template>
  <div class="base-date-range-filter">
    <label v-if="label" class="base-date-range-label">{{ label }}</label>
    <div class="base-date-range-fields">
      <BaseDatePicker
          :id="startId"
          :model-value="start"
          :placeholder="startPlaceholder"
          @update:model-value="updateStart"
      />
      <span aria-hidden="true" class="base-date-range-separator">—</span>
      <BaseDatePicker
          :id="endId"
          :model-value="end"
          :placeholder="endPlaceholder"
          @update:model-value="updateEnd"
      />
    </div>
  </div>
</template>

<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'

import BaseDatePicker from '@/shared/components/BaseDatePicker.vue'

const props = withDefaults(defineProps<{
  label?: string
  start?: string
  end?: string
  startPlaceholder?: string
  endPlaceholder?: string
  idPrefix?: string
}>(), {
  label: '',
  start: '',
  end: '',
  startPlaceholder: '',
  endPlaceholder: '',
  idPrefix: 'date-range',
})

const emit = defineEmits<{
  'update:start': [value: string]
  'update:end': [value: string]
  change: []
}>()

const {t} = useI18n()

const startId = computed(() => `${props.idPrefix}-start`)
const endId = computed(() => `${props.idPrefix}-end`)
const startPlaceholder = computed(() => props.startPlaceholder || t('settings.datePlaceholder'))
const endPlaceholder = computed(() => props.endPlaceholder || t('settings.datePlaceholder'))

function updateStart(value: string) {
  if (value !== props.start) {
    emit('update:start', value)
    emit('change')
  }
}

function updateEnd(value: string) {
  if (value !== props.end) {
    emit('update:end', value)
    emit('change')
  }
}
</script>

<style scoped>
.base-date-range-filter {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.base-date-range-label {
  font-family: var(--font-label);
  font-size: 13px;
  font-weight: 700;
  color: var(--color-muted);
  white-space: nowrap;
}

.base-date-range-fields {
  display: grid;
  grid-template-columns: minmax(140px, 1fr) auto minmax(140px, 1fr);
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.base-date-range-separator {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 14px;
  font-weight: 700;
  line-height: 1;
}

.base-date-range-filter :deep(.base-date-picker) {
  min-width: 0;
}

.base-date-range-filter :deep(.base-date-picker:has(.base-date-picker-popover)) {
  z-index: 130;
}

.base-date-range-filter :deep(.base-date-picker-trigger) {
  min-height: 40px;
  padding: 0 12px 0 14px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 8px;
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 14px;
  font-weight: 700;
  line-height: 1;
  transition: border-color 0.2s, background 0.2s, box-shadow 0.2s;
}

.base-date-range-filter :deep(.base-date-picker-trigger:hover) {
  background: var(--color-surface-container);
  border-color: var(--color-outline-variant);
}

.base-date-range-filter :deep(.base-date-picker-trigger:focus) {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(26, 28, 28, 0.08);
}

.base-date-range-filter :deep(.base-date-picker-trigger.placeholder span) {
  color: var(--color-muted);
}

.base-date-range-filter :deep(.base-date-picker-trigger svg) {
  width: 16px;
  height: 16px;
  color: var(--color-muted);
}

.base-date-range-filter :deep(.base-date-picker-popover) {
  top: calc(100% + 8px);
}

@media (max-width: 640px) {
  .base-date-range-filter {
    width: 100%;
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }

  .base-date-range-fields {
    width: 100%;
    grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  }
}
</style>
