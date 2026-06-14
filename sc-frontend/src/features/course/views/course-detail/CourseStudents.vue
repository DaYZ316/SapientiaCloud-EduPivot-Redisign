<template>
  <section class="tab-panel">
    <div class="panel-header">
      <div>
        <span>{{ t('courseDetail.membersTab') }}</span>
        <h2>{{ t('courseDetail.studentsTab') }}</h2>
      </div>
      <span class="count-badge">{{ students.length }}</span>
    </div>
    <div v-if="students.length === 0" class="empty-tab">
      <Users :size="28" stroke-width="1.4"/>
      <h3>{{ t('courseDetail.noStudentsTitle') }}</h3>
      <p>{{ t('courseDetail.noStudents') }}</p>
    </div>
    <div v-else class="member-list">
      <div v-for="student in students" :key="student.id" class="member-item">
        <div class="member-avatar">
          <img :src="studentFallbackUrl" :alt="student.studentName || student.studentId"/>
        </div>
        <div class="member-info">
          <strong>{{ student.studentName || student.studentId }}</strong>
          <span>{{ formatDate(student.enrolledAt) }}</span>
        </div>
        <div class="member-actions">
          <BaseSelect
            v-if="canManageCourse && student.status !== 3"
            v-model="student.status"
            :options="statusOptions"
            class="status-select"
            @change="handleStatusChange(student, student.status)"
          />
          <span v-else class="member-status">{{ EnrollmentStatusLabel[student.status] || t('courseDetail.unknown') }}</span>
          <button
            v-if="canManageCourse && student.status !== 3"
            class="btn-icon danger"
            type="button"
            :title="t('courseDetail.removeStudent')"
            @click="handleRemove(student)"
          >
            <Trash2 :size="14" stroke-width="1.8"/>
          </button>
        </div>
      </div>
    </div>
  </section>
</template>

<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {Trash2, Users} from 'lucide-vue-next'
import {updateEnrollmentStatus} from '@/features/course/api/course'
import {notify} from '@/shared/composables/useGlobalNotification'
import BaseSelect from '@/shared/components/BaseSelect.vue'
import type {Enrollment} from '@/features/course/types/course'

const EnrollmentStatusLabel: Record<number, string> = {
  0: 'Pending',
  1: 'Active',
  2: 'Completed',
  3: 'Dropped',
}

const statusOptions = [
  {label: EnrollmentStatusLabel[0], value: 0},
  {label: EnrollmentStatusLabel[1], value: 1},
  {label: EnrollmentStatusLabel[2], value: 2},
]

const props = defineProps<{
  students: Enrollment[]
  formatDate: (dateStr?: string | null) => string
  canManageCourse?: boolean
}>()

const emit = defineEmits<{
  refresh: []
}>()

const {t} = useI18n()

const studentFallbackUrl = '/assets/avatar-student-default.png'

async function handleStatusChange(student: Enrollment, newStatus: number) {
  try {
    await updateEnrollmentStatus(student.id, newStatus)
    notify.success(t('courseDetail.statusUpdated'))
    emit('refresh')
  } catch {
    notify.error(t('courseDetail.saveChapterFailed'))
  }
}

async function handleRemove(student: Enrollment) {
  if (!confirm(t('courseDetail.confirmRemoveStudent'))) return
  try {
    await updateEnrollmentStatus(student.id, 3)
    notify.success(t('courseDetail.studentRemoved'))
    emit('refresh')
  } catch {
    notify.error(t('courseDetail.saveChapterFailed'))
  }
}
</script>

<style scoped>
.panel-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-outline-light);
}

.panel-header span {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.panel-header h2 {
  margin: 6px 0 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 400;
  line-height: 1.3;
}

.count-badge {
  padding: 3px 8px;
  background: var(--color-surface-container-high);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
  font-weight: 800;
}

.member-list {
  display: grid;
  gap: 10px;
}

.member-item {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 62px;
  padding: 10px 12px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
}

.member-avatar {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  overflow: hidden;
  background: var(--color-surface-container-high);
  border: 1px solid var(--color-outline-light);
  border-radius: 50%;
  color: var(--color-muted);
  flex: 0 0 auto;
}

.member-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.member-info {
  min-width: 0;
  flex: 1;
}

.member-info strong {
  display: block;
  overflow: hidden;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-info span {
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
  line-height: 1.45;
}

.member-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
}

.member-status {
  padding: 3px 8px;
  background: var(--color-surface-container-high);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
  font-weight: 800;
}

.status-select {
  min-width: 120px;
}

.btn-icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  padding: 0;
  background: transparent;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-muted);
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.btn-icon:hover {
  background: var(--color-surface-container-high);
  border-color: var(--color-outline);
  color: var(--color-on-surface);
}

.btn-icon.danger:hover {
  color: #ef4444;
}

.empty-tab {
  display: grid;
  min-height: 220px;
  place-items: center;
  align-content: center;
  gap: 10px;
  padding: 42px 24px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-muted);
  text-align: center;
}

.empty-tab h3 {
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 600;
}

.empty-tab p {
  max-width: 44ch;
  margin: 0;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.55;
}

@media (max-width: 760px) {
  .panel-header {
    flex-direction: column;
    align-items: stretch;
  }

  .member-item {
    flex-wrap: wrap;
  }

  .member-actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
