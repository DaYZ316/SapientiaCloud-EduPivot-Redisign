<template>
  <button
    v-if="compact"
    class="classroom-live-compact"
    type="button"
    @click="$emit('expand')"
  >
    <span class="compact-live-dot" />
    <span class="compact-live-copy">
      <strong>{{ session.title }}</strong>
      <span>{{ isTeacher ? '直播仍在进行' : '返回直播' }}</span>
    </span>
    <Maximize2 :size="18" stroke-width="2" />
  </button>
  <ClassroomLiveExperience
    v-else
    :can-participate="canParticipate"
    :is-teacher="isTeacher"
    mode="popup"
    :session="session"
    @close="$emit('close')"
    @session-change="$emit('session-change', $event)"
  />
</template>

<script setup lang="ts">
import {Maximize2} from 'lucide-vue-next'

import ClassroomLiveExperience from '@/features/classroom/components/ClassroomLiveExperience.vue'
import type {ClassSession} from '@/features/course/types/classSession'

defineProps<{
  session: ClassSession
  isTeacher: boolean
  canParticipate: boolean
  compact?: boolean
}>()

defineEmits<{
  close: []
  expand: []
  'session-change': [session: ClassSession]
}>()
</script>

<style scoped>
.classroom-live-compact {
  position: fixed;
  z-index: 1900;
  right: var(--space-md);
  bottom: var(--space-md);
  display: grid;
  width: min(320px, calc(100vw - (var(--space-md) * 2)));
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: var(--space-sm);
  align-items: center;
  padding: 12px 14px;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-md);
  background: var(--color-surface-card);
  box-shadow: var(--shadow-card);
  color: var(--color-on-surface);
  cursor: pointer;
  font-family: var(--font-body);
  text-align: left;
}

.classroom-live-compact:hover {
  border-color: var(--color-primary);
}

.compact-live-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #16a34a;
  animation: compactLivePulse 1.4s ease-in-out infinite;
}

.compact-live-copy {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.compact-live-copy strong,
.compact-live-copy span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.compact-live-copy strong {
  font-size: 14px;
}

.compact-live-copy span {
  color: var(--color-muted);
  font-size: 12px;
}

@keyframes compactLivePulse {
  50% {
    opacity: 0.44;
    transform: scale(1.35);
  }
}
</style>
