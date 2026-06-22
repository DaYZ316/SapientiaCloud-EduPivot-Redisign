<template>
  <div v-if="events.length > 0" class="live-practice-popups" aria-live="polite">
    <article v-for="event in events" :key="event.groupId" class="live-practice-popup">
      <button class="popup-close" type="button" aria-label="关闭" @click="dismiss(event.groupId)">
        <X :size="16" stroke-width="1.8"/>
      </button>
      <div class="popup-kicker">随堂练习</div>
      <h2>{{ event.title }}</h2>
      <p>{{ event.totalQuestions }} 题 · {{ formatTime(event.availableEndAt) }} 截止</p>
      <button class="popup-action" type="button" @click="openPractice(event)">
        <ClipboardList :size="16" stroke-width="1.8"/>
        开始作答
      </button>
    </article>
  </div>
</template>

<script lang="ts" setup>
import {useRouter} from 'vue-router'
import {ClipboardList, X} from 'lucide-vue-next'

import {useLivePracticeEvents} from '@/features/live-practice/composables/useLivePracticeEvents'
import type {LivePracticeEvent} from '@/features/live-practice/types/livePractice'

const router = useRouter()
const {events, dismiss} = useLivePracticeEvents()

async function openPractice(event: LivePracticeEvent) {
  dismiss(event.groupId)
  await router.push({
    name: 'class-session-room',
    params: {sessionId: event.classSessionId},
    query: {practice: event.groupId},
  })
}

function formatTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}
</script>

<style scoped>
.live-practice-popups {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 3000;
  display: grid;
  width: min(360px, calc(100vw - 32px));
  gap: 12px;
}

.live-practice-popup {
  position: relative;
  display: grid;
  gap: 10px;
  padding: 18px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline);
  border-radius: var(--radius-sm);
  box-shadow: 0 18px 44px rgba(15, 23, 42, 0.16);
  color: var(--color-on-surface);
}

.popup-close {
  position: absolute;
  top: 10px;
  right: 10px;
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  padding: 0;
  background: transparent;
  border: 1px solid var(--color-outline-light);
  color: var(--color-muted);
  cursor: pointer;
}

.popup-kicker {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  letter-spacing: 0.05em;
}

.live-practice-popup h2 {
  margin: 0;
  padding-right: 32px;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 400;
  line-height: 1.2;
}

.live-practice-popup p {
  margin: 0;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
}

.popup-action {
  display: inline-flex;
  min-height: 40px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-sm);
  color: var(--color-on-primary);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 14px;
}

@media (max-width: 640px) {
  .live-practice-popups {
    right: 16px;
    bottom: 16px;
  }
}
</style>
