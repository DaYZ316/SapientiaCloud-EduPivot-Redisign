<template>
  <div class="course-table-container">
    <table class="course-table">
      <thead>
        <tr>
          <th>{{ t('courses.table.course') }}</th>
          <th>{{ t('courses.table.level') }}</th>
          <th>{{ t('courses.table.status') }}</th>
          <th>{{ t('courses.table.students') }}</th>
          <th>{{ t('courses.table.createdAt') }}</th>
          <th>{{ t('courses.table.updatedAt') }}</th>
          <th>{{ t('courses.table.actions') }}</th>
        </tr>
      </thead>
      <tbody v-if="loading">
        <tr v-for="row in 6" :key="row">
          <td>
            <div class="course-cell">
              <div class="course-cover skeleton shimmer"></div>
              <div class="course-summary">
                <div class="skeleton-line title shimmer"></div>
                <div class="skeleton-line text shimmer"></div>
              </div>
            </div>
          </td>
          <td><div class="skeleton-pill shimmer"></div></td>
          <td><div class="skeleton-pill shimmer"></div></td>
          <td><div class="skeleton-line short shimmer"></div></td>
          <td><div class="skeleton-line date shimmer"></div></td>
          <td><div class="skeleton-line date shimmer"></div></td>
          <td><div class="skeleton-actions shimmer"></div></td>
        </tr>
      </tbody>
      <tbody v-else>
        <tr v-for="course in courses" :key="course.id">
          <td>
            <div class="course-cell">
              <div class="course-cover">
                <img v-if="course.coverUrl" :src="course.coverUrl" alt=""/>
                <BookOpen v-else :size="22" stroke-width="1.5"/>
              </div>
              <div class="course-summary">
                <strong>{{ course.title }}</strong>
                <span>{{ course.description || t('courses.noDescription') }}</span>
              </div>
            </div>
          </td>
          <td>
            <span class="level-badge">{{ getLevelLabel(course.level) }}</span>
          </td>
          <td>
            <span class="status-badge" :class="getCourseStatusClass(course.status)">
              {{ getStatusLabel(course.status) }}
            </span>
          </td>
          <td>{{ formatStudents(course) }}</td>
          <td>{{ formatDate(course.createdAt) }}</td>
          <td>{{ course.updatedAt ? formatDate(course.updatedAt) : '-' }}</td>
          <td>
            <div class="action-buttons">
              <button
                class="btn-icon"
                type="button"
                :aria-label="t('courses.viewDetails')"
                :title="t('courses.viewDetails')"
                @click="$emit('view', course.id)"
              >
                <Eye :size="15" stroke-width="1.8"/>
              </button>
              <button
                v-if="editable"
                class="btn-icon"
                type="button"
                :aria-label="t('courses.edit')"
                :title="t('courses.edit')"
                @click="$emit('edit', course)"
              >
                <Pencil :size="15" stroke-width="1.8"/>
              </button>
              <button
                v-if="editable"
                class="btn-icon danger"
                type="button"
                :aria-label="t('courses.delete')"
                :title="t('courses.delete')"
                @click="$emit('delete', course)"
              >
                <Trash2 :size="15" stroke-width="1.8"/>
              </button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {BookOpen, Eye, Pencil, Trash2} from 'lucide-vue-next'

import type {Course} from '@/features/course/types/course'

withDefaults(defineProps<{
  courses: Course[]
  loading?: boolean
  editable?: boolean
}>(), {
  editable: true,
})

defineEmits<{
  view: [id: string]
  edit: [course: Course]
  delete: [course: Course]
}>()

const {t, locale} = useI18n()

function getLevelLabel(level: number): string {
  const map: Record<number, string> = {
    1: t('courses.level.beginner'),
    2: t('courses.level.intermediate'),
    3: t('courses.level.advanced'),
  }

  return map[level] ?? '-'
}

function getStatusLabel(status: number): string {
  const map: Record<number, string> = {
    0: t('courses.status.draft'),
    1: t('courses.status.published'),
    2: t('courses.status.archived'),
  }

  return map[status] ?? '-'
}

function getCourseStatusClass(status: number): string {
  const map: Record<number, string> = {0: 'draft', 1: 'published', 2: 'archived'}

  return map[status] ?? ''
}

function formatStudents(course: Course): string {
  return course.maxStudents > 0 ? `${course.currentStudents}/${course.maxStudents}` : String(course.currentStudents)
}

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  if (Number.isNaN(date.getTime())) {
    return '-'
  }

  return new Intl.DateTimeFormat(String(locale.value), {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}
</script>

<style scoped>
.course-table-container {
  width: 100%;
  margin-bottom: 32px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 16px;
  overflow-x: auto;
  overflow-y: hidden;
}

.course-table {
  width: 100%;
  min-width: 920px;
  border-collapse: collapse;
}

.course-table th {
  padding: 14px 18px;
  text-align: left;
  background: var(--color-surface-canvas);
  border-bottom: 1px solid var(--color-outline-light);
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  white-space: nowrap;
}

.course-table td {
  padding: 14px 18px;
  border-bottom: 1px solid var(--color-surface-canvas);
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 14px;
  vertical-align: middle;
  white-space: nowrap;
}

.course-table tr:last-child td {
  border-bottom: 0;
}

.course-table tbody tr {
  transition: background 0.2s;
}

.course-table tbody tr:hover {
  background: var(--color-surface-canvas);
}

.course-cell {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 280px;
}

.course-cover {
  width: 56px;
  height: 42px;
  display: grid;
  flex-shrink: 0;
  place-items: center;
  background: var(--color-surface-canvas);
  border-radius: 8px;
  color: var(--color-on-surface);
  overflow: hidden;
}

.course-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.course-summary {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.course-summary strong,
.course-summary span {
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.course-summary strong {
  color: var(--color-on-surface);
  font-size: 14px;
  font-weight: 800;
}

.course-summary span {
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 600;
}

.level-badge,
.status-badge {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 10px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 800;
}

.level-badge {
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
}

.status-badge.draft {
  background: var(--color-surface-canvas);
  color: var(--color-muted);
}

.status-badge.published {
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.status-badge.archived {
  background: var(--color-outline-light);
  color: var(--color-muted);
}

.action-buttons {
  display: flex;
  align-items: center;
  gap: 6px;
}

.btn-icon {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  background: transparent;
  border: 0;
  border-radius: 8px;
  color: var(--color-muted);
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}

.btn-icon:hover {
  background: var(--color-surface-container);
  color: var(--color-on-surface);
}

.btn-icon.danger:hover {
  color: var(--color-error);
}

.skeleton {
  position: relative;
  background: var(--color-surface-canvas);
  overflow: hidden;
}

.skeleton::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent 0%, var(--color-surface-card) 44%, transparent 100%);
  animation: shimmer 1.4s ease-in-out infinite;
}

.skeleton-line {
  height: 13px;
  border-radius: 6px;
}

.skeleton-line.title {
  width: 180px;
}

.skeleton-line.text {
  width: 240px;
}

.skeleton-line.short {
  width: 48px;
}

.skeleton-line.date {
  width: 92px;
}

.skeleton-pill {
  width: 64px;
  height: 24px;
  border-radius: 8px;
}

.skeleton-actions {
  width: 108px;
  height: 32px;
  border-radius: 8px;
}

@keyframes shimmer {
  0% {
    transform: translateX(-100%);
  }

  100% {
    transform: translateX(100%);
  }
}
</style>
