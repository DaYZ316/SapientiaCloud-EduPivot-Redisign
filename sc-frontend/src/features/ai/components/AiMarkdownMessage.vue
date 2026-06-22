<template>
  <RichMathContent
    class="ai-markdown-message"
    :content="content"
  />
</template>

<script lang="ts" setup>
import RichMathContent from '@/shared/components/RichMathContent.vue'

defineProps<{
  content: string
}>()
</script>
