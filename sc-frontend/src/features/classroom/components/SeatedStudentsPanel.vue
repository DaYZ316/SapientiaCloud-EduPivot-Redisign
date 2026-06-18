<template>
  <aside class="seated-students-panel">
    <header class="panel-header">
      <div>
        <span>Online Students</span>
        <h2>在线学生汇总</h2>
      </div>
      <button class="icon-button" type="button" @click="$emit('close')">
        <X :size="18" stroke-width="1.8"/>
      </button>
    </header>

    <section class="summary-grid">
      <div>
        <span>已落座</span>
        <strong>{{ seatedStudents.length }}</strong>
      </div>
      <div>
        <span>座位容量</span>
        <strong>{{ roomSpec.seatCount }}</strong>
      </div>
    </section>

    <section v-if="seatedStudents.length > 0" class="student-list">
      <article v-for="student in seatedStudents" :key="student.userId" class="student-row">
        <UserAvatarLink
            :avatar-url="student.avatarUrl"
            :display-name="student.displayName || student.userId"
            :role="student.role"
            :user-id="student.userId"
            size="medium"
        />
        <div class="student-meta">
          <strong>{{ student.displayName || student.userId }}</strong>
          <span>座位 {{ (student.seatIndex ?? 0) + 1 }}</span>
          <small>{{ formatDateTime(student.joinedAt) }}</small>
        </div>
      </article>
    </section>

    <div v-else class="panel-state">
      <Users :size="28" stroke-width="1.6"/>
      <p>暂无学生落座</p>
    </div>
  </aside>
</template>

<script lang="ts" setup>
import {computed} from 'vue'
import {Users, X} from 'lucide-vue-next'

import UserAvatarLink from '@/shared/components/UserAvatarLink.vue'
import {getRoomSpec} from '@/features/classroom/types/classroom'
import type {ClassParticipant, ClassSession} from '@/features/course/types/classSession'

const STUDENT_ROLE = 1

const props = defineProps<{
  participants: ClassParticipant[]
  session: ClassSession
}>()

defineEmits<{ close: [] }>()

const roomSpec = computed(() => getRoomSpec(props.session.roomSize))
const seatedStudents = computed(() =>
    props.participants
        .filter(participant => participant.role === STUDENT_ROLE && participant.seatIndex != null)
        .slice()
        .sort((a, b) => (a.seatIndex ?? 0) - (b.seatIndex ?? 0)),
)

function formatDateTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '-'
  }
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}
</script>

<style scoped>
.seated-students-panel {
  position: fixed;
  top: 24px;
  right: 24px;
  bottom: 24px;
  z-index: 2200;
  display: flex;
  width: min(420px, calc(100vw - 32px));
  flex-direction: column;
  overflow: hidden;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline);
  color: var(--color-on-surface);
  box-shadow: 0 24px 64px rgba(15, 23, 42, 0.24);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  border-bottom: 1px solid var(--color-outline-light);
}

.panel-header span,
.summary-grid span,
.student-meta small {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
}

.panel-header h2 {
  margin: 4px 0 0;
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 400;
}

.icon-button {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  background: transparent;
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
  cursor: pointer;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  border-bottom: 1px solid var(--color-outline-light);
}

.summary-grid div {
  min-width: 0;
  padding: 14px 16px;
  border-right: 1px solid var(--color-outline-light);
}

.summary-grid div:last-child {
  border-right: 0;
}

.summary-grid span,
.summary-grid strong {
  display: block;
}

.summary-grid strong {
  margin-top: 8px;
  font-family: var(--font-body);
  font-size: 28px;
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.student-list {
  display: grid;
  gap: 10px;
  min-height: 0;
  overflow-y: auto;
  padding: 16px;
}

.student-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 12px;
  align-items: center;
  padding: 12px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
}

.student-row :deep(.user-avatar-link__name) {
  display: none;
}

.student-meta {
  display: grid;
  min-width: 0;
  gap: 5px;
}

.student-meta strong,
.student-meta span {
  overflow: hidden;
  font-family: var(--font-body);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.student-meta strong {
  font-size: 14px;
}

.student-meta span {
  color: var(--color-on-surface-variant);
  font-size: 13px;
}

.panel-state {
  display: grid;
  min-height: 220px;
  place-items: center;
  align-content: center;
  gap: 10px;
  padding: 16px;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
  text-align: center;
}

.panel-state p {
  margin: 0;
}

@media (max-width: 640px) {
  .seated-students-panel {
    inset: 12px;
    width: auto;
  }
}
</style>
