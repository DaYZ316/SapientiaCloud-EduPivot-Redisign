<template>
  <button
    :aria-pressed="active"
    :class="{ 'is-complete': isComplete, 'is-failed': isFailed, 'is-active': active, 'is-pending': message.pending }"
    class="generation-card"
    type="button"
    @click="$emit('view-trace', message.id)"
  >
    <span class="generation-body">
      <span class="generation-title-row">
        <strong>{{ title }}</strong>
        <span v-if="!message.pending" class="generation-status">
          <time :datetime="message.createdAt" :title="generationTimeTitle" class="generation-time">
            {{ generationTime }}
          </time>
        </span>
      </span>
      <span class="generation-summary">{{ summary }}</span>
      <span aria-hidden="true" class="generation-progress">
        <span :style="{ width: `${progress}%` }" />
      </span>
      <span class="generation-footer">
        <span>{{ footerText }}</span>
        <ChevronRight :size="16" stroke-width="1.9" />
      </span>
    </span>
    <span
      v-if="message.pending"
      class="generation-pending-brush"
    >
      <AiPendingBrushLoader
        :anchor-size="92"
        :radius="32"
        :size="46"
        center-on-anchor
      />
    </span>
  </button>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronRight } from 'lucide-vue-next'

import AiPendingBrushLoader from '@/features/ai/components/AiPendingBrushLoader.vue'
import type { ChatMessage } from '@/features/ai/types/ai'
import {
  currentGenerationStage,
  generatedQuestionCount,
  generationProgress,
  generationSummary,
  generationTitle,
  generationTrace,
} from '@/features/ai/utils/generationTrace'

const props = withDefaults(
  defineProps<{
    message: ChatMessage
    active?: boolean
  }>(),
  {
    active: false,
  },
)

const { locale } = useI18n()

defineEmits<{
  'view-trace': [messageId: string]
}>()

const stage = computed(() => currentGenerationStage(props.message))
const progress = computed(() => (props.message.pending ? generationProgress(props.message) : 100))
const traceCount = computed(() => generationTrace(props.message).length)
const questionCount = computed(() => generatedQuestionCount(props.message))
const isFailed = computed(() => props.message.failed || stage.value === 'FAILED')
const isComplete = computed(() => !isFailed.value && !props.message.pending)
const title = computed(() => generationTitle(props.message))
const summary = computed(() => generationSummary(props.message))
const generationTime = computed(() =>
  formatGenerationTime(props.message.createdAt, {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }),
)
const generationTimeTitle = computed(
  () =>
    `出题时间：${formatGenerationTime(props.message.createdAt, {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    })}`,
)
const actionLabel = computed(() => (props.message.messageType === 'PAPER' ? '天枢出卷' : '天枢出题'))
const footerText = computed(() => {
  if (isFailed.value) return `${actionLabel.value}失败`
  if (props.message.pending)
    return traceCount.value > 0
      ? `${actionLabel.value}中 · 已记录 ${traceCount.value} 个步骤`
      : `${actionLabel.value}中`
  if (questionCount.value > 0) return `${actionLabel.value}完成 · ${questionCount.value} 道题`
  return `${actionLabel.value}完成`
})

function formatGenerationTime(value: string, options: Intl.DateTimeFormatOptions) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''

  return new Intl.DateTimeFormat(String(locale.value), options).format(date)
}
</script>

<style scoped>
.generation-card {
  display: grid;
  position: relative;
  width: min(100%, 620px);
  padding: 18px;
  overflow: hidden;
  background: transparent;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-lg);
  color: var(--color-on-surface);
  cursor: pointer;
  font: inherit;
  text-align: left;
  transition:
    background 0.18s ease,
    border-color 0.18s ease,
    transform 0.18s ease;
}

.generation-card:hover {
  background: color-mix(in srgb, var(--color-on-surface) 4%, transparent);
  border-color: var(--color-primary);
}

.generation-card.is-active {
  background: color-mix(in srgb, var(--color-primary) 8%, transparent);
  border-color: var(--color-primary);
  box-shadow: inset 0 0 0 1px var(--color-primary);
}

.generation-card:focus-visible {
  border-color: var(--color-primary);
  outline: 3px solid color-mix(in srgb, var(--color-primary) 18%, transparent);
  outline-offset: 2px;
}

.generation-card:active {
  transform: translateY(1px);
}

.generation-card.is-pending {
  padding-right: 148px;
}

.generation-status {
  display: grid;
  min-width: 86px;
  height: 30px;
  flex: 0 0 auto;
  align-items: center;
  justify-items: end;
}

.generation-pending-brush {
  position: absolute;
  top: 12px;
  right: 42px;
  display: block;
  width: 92px;
  height: 92px;
  pointer-events: none;
}

.generation-time {
  overflow: hidden;
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.generation-body {
  display: grid;
  min-width: 0;
  gap: 10px;
}

.generation-title-row {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.generation-footer {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
  letter-spacing: 0.08em;
  line-height: 1.35;
  text-transform: uppercase;
}

.generation-body strong {
  min-width: 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 600;
  line-height: 1.18;
}

.generation-summary {
  color: var(--color-on-surface-variant);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.55;
}

.generation-progress {
  display: block;
  width: 100%;
  height: 6px;
  overflow: hidden;
  background: var(--color-surface-container-high);
  border-radius: var(--radius-sm);
}

.generation-progress span {
  display: block;
  height: 100%;
  background: var(--color-primary);
  border-radius: inherit;
  transition: width 0.3s ease;
}

.generation-footer {
  justify-content: space-between;
  padding-top: 2px;
  letter-spacing: 0;
  text-transform: none;
}

@media (prefers-reduced-motion: reduce) {
  .generation-progress span {
    transition: none;
  }
}

@media (max-width: 720px) {
  .generation-card {
    width: 100%;
    padding: 14px;
  }

  .generation-card.is-pending {
    padding-right: 104px;
  }

  .generation-pending-brush {
    top: 10px;
    right: 18px;
    transform: scale(0.78);
    transform-origin: top right;
  }

  .generation-status {
    min-width: 72px;
    height: 28px;
  }

  .generation-body strong {
    font-size: 19px;
  }
}
</style>
