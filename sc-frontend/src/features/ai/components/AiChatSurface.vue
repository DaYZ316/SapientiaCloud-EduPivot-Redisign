<template>
  <AiChatPanel
    :show-header="showHeader"
    :title="title"
  />
</template>

<script lang="ts" setup>
import AiChatPanel from '@/features/ai/components/AiChatPanel.vue'

withDefaults(defineProps<{
  title?: string
  showHeader?: boolean
}>(), {
  title: 'AI 教学助手',
  showHeader: true,
})
</script>
