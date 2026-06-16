<template>
  <nav v-if="totalPages > 1" class="base-pagination" :aria-label="ariaLabel">
    <button
      class="btn-page icon"
      type="button"
      :disabled="disabled || page <= 1"
      :title="previousTitle"
      @click="changePage(page - 1)"
    >
      <ChevronLeft :size="15" stroke-width="1.8"/>
    </button>
    <div class="page-numbers">
      <button
        v-for="pageNumber in displayedPages"
        :key="pageNumber"
        class="btn-page"
        type="button"
        :disabled="disabled"
        :class="{active: page === pageNumber}"
        @click="changePage(pageNumber)"
      >
        {{ pageNumber }}
      </button>
    </div>
    <button
      class="btn-page icon"
      type="button"
      :disabled="disabled || page >= totalPages"
      :title="nextTitle"
      @click="changePage(page + 1)"
    >
      <ChevronRight :size="15" stroke-width="1.8"/>
    </button>
  </nav>
</template>

<script lang="ts" setup>
import {computed} from 'vue'
import {ChevronLeft, ChevronRight} from 'lucide-vue-next'

const props = withDefaults(defineProps<{
  page: number
  total: number
  size: number
  disabled?: boolean
  ariaLabel?: string
  previousTitle?: string
  nextTitle?: string
}>(), {
  disabled: false,
  ariaLabel: 'Pagination',
  previousTitle: 'Previous page',
  nextTitle: 'Next page',
})

const emit = defineEmits<{
  change: [page: number]
}>()

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.size)))

const displayedPages = computed(() => {
  const pages: number[] = []
  const maxDisplay = 5
  let start = Math.max(1, props.page - Math.floor(maxDisplay / 2))
  let end = Math.min(totalPages.value, start + maxDisplay - 1)
  if (end - start + 1 < maxDisplay) {
    start = Math.max(1, end - maxDisplay + 1)
  }
  for (let page = start; page <= end; page++) {
    pages.push(page)
  }
  return pages
})

function changePage(nextPage: number) {
  const clamped = Math.min(Math.max(nextPage, 1), totalPages.value)
  if (clamped === props.page || props.disabled) return
  emit('change', clamped)
}
</script>

<style scoped>
.base-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 4px;
  flex-wrap: wrap;
}

.page-numbers {
  display: flex;
  gap: 4px;
}

.btn-page {
  display: inline-grid;
  min-width: 36px;
  height: 36px;
  place-items: center;
  padding: 0 10px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 13px;
  font-weight: 700;
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.btn-page.icon {
  padding: 0;
}

.btn-page:hover:not(:disabled) {
  background: var(--color-surface-container-high);
  border-color: var(--color-outline);
}

.btn-page.active,
.btn-page.active:hover:not(:disabled) {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-on-primary);
}

.btn-page:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}
</style>
