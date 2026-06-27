<template>
  <RichMathContent
      :content="content"
      class="ai-markdown-message"
  />
</template>

<script lang="ts" setup>
import RichMathContent from '@/shared/components/RichMathContent.vue'

defineProps<{
  content: string
}>()
</script>
