<template>
  <div class="notification-content-editor">
    <BaseTextEditor
        :id="inputId"
        :disabled="disabled"
        :model-value="modelValue"
        :placeholder="t('notifications.modal.contentPlaceholder')"
        :rows="rows"
        name="content"
        required
        @update:model-value="emit('update:modelValue', $event)"
    />
  </div>
</template>

<script lang="ts" setup>
import {useI18n} from 'vue-i18n'

import BaseTextEditor from '@/shared/components/BaseTextEditor.vue'

withDefaults(defineProps<{
  modelValue: string
  disabled?: boolean
  rows?: number
  inputId?: string
}>(), {
  disabled: false,
  rows: 4,
  inputId: 'notification-content',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const {t} = useI18n()
</script>

<style scoped>
.notification-content-editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
