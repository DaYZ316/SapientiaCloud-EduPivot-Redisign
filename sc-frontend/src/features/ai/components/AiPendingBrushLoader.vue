<template>
  <div
    aria-label="AI is generating"
    class="pending-brush-loader"
    role="status"
  >
    <AiBrushEffect />
  </div>
</template>

<script lang="ts" setup>
import AiBrushEffect from '@/features/ai/components/AiBrushEffect.vue'
</script>

<style scoped>
.pending-brush-loader {
  display: grid;
  width: 86px;
  height: 86px;
  place-items: center;
  pointer-events: none;
}
</style>
