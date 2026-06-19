<template>
  <section class="tab-panel">
    <div class="panel-header">
      <div>
        <span>{{ t('courseDetail.membersTab') }}</span>
        <h2>{{ t('courseDetail.studentsTab') }}</h2>
        <p>{{ t('courseDetail.studentsDescription') }}</p>
      </div>
      <span class="count-badge">{{ total }}</span>
    </div>
    <CourseTabLoadingSkeleton
        v-if="loading"
        :actions="canManageCourse ? 2 : 1"
        :count="4"
        avatar
    />
    <div v-else-if="students.length === 0" class="empty-tab">
      <Users :size="28" stroke-width="1.4"/>
      <h3>{{ t('courseDetail.noStudentsTitle') }}</h3>
      <p>{{ t('courseDetail.noStudents') }}</p>
    </div>
    <div v-else class="member-list">
      <div v-for="student in students" :key="student.id" class="member-item">
        <UserAvatarLink
            :display-name="student.studentName"
            :role="1"
            :show-name="false"
            :user-id="student.studentId"
            size="medium"
        />
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
          <span v-else class="member-status">{{
              enrollmentStatusLabel[student.status] || t('courseDetail.unknown')
            }}</span>
          <button
              v-if="canManageCourse && student.status !== 3"
              :title="t('courseDetail.removeStudent')"
              class="btn-icon danger"
              type="button"
              @click="handleRemove(student)"
          >
            <Trash2 :size="14" stroke-width="1.8"/>
          </button>
        </div>
      </div>
    </div>
    <BasePagination
        v-if="total > 0"
        :aria-label="t('courseDetail.pagination')"
        :disabled="loading"
        :next-title="t('courseDetail.nextPage')"
        :page="page"
        :previous-title="t('courseDetail.previousPage')"
        :size="size"
        :total="total"
        @change="emit('page-change', $event)"
    />
  </section>
</template>

<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {Trash2, Users} from 'lucide-vue-next'
import {updateEnrollmentStatus} from '@/features/course/api/course'
import {confirmDialog} from '@/shared/composables/useConfirmDialog'
import {notify} from '@/shared/composables/useGlobalNotification'
import BasePagination from '@/shared/components/BasePagination.vue'
import BaseSelect from '@/shared/components/BaseSelect.vue'
import CourseTabLoadingSkeleton from '@/features/course/components/CourseTabLoadingSkeleton.vue'
import UserAvatarLink from '@/shared/components/UserAvatarLink.vue'
import type {Enrollment} from '@/features/course/types/course'

const props = withDefaults(defineProps<{
  students: Enrollment[]
  page?: number
  size?: number
  total?: number
  loading?: boolean
  formatDate: (dateStr?: string | null) => string
  canManageCourse?: boolean
}>(), {
  page: 1,
  size: 10,
  total: 0,
  loading: false,
})

const emit = defineEmits<{
  refresh: []
  'page-change': [page: number]
}>()

const {t} = useI18n()

const enrollmentStatusLabel = computed<Record<number, string>>(() => ({
  0: t('courseDetail.enrollmentStatus.pending'),
  1: t('courseDetail.enrollmentStatus.active'),
  2: t('courseDetail.enrollmentStatus.completed'),
  3: t('courseDetail.enrollmentStatus.dropped'),
}))

const statusOptions = computed(() => [
  {label: t('courseDetail.enrollmentStatus.pending'), value: 0},
  {label: t('courseDetail.enrollmentStatus.active'), value: 1},
  {label: t('courseDetail.enrollmentStatus.completed'), value: 2},
])

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
  if (!(await confirmDialog({message: t('courseDetail.confirmRemoveStudent'), confirmVariant: 'danger'}))) return
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
  font-weight: 400;
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

.panel-header p {
  max-width: 62ch;
  margin: 8px 0 0;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.55;
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

.status-select :deep(.base-select-trigger) {
  min-height: 32px;
  height: 32px;
  padding: 0 10px 0 12px;
  border-radius: var(--radius-sm);
  font-size: 12px;
}

.status-select :deep(.base-select-menu) {
  padding: 4px;
  box-shadow: none;
}

.status-select :deep(.base-select-option) {
  min-height: 32px;
  padding: 8px 10px;
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
  color: var(--color-muted);
  text-align: center;
}

.empty-tab h3 {
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 400;
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
