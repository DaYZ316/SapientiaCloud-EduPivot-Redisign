<template>
  <article class="course-card">
    <figure class="course-cover">
      <img
        :src="coverSrc"
        :alt="course.title"
        @error="useFallbackImage($event, COURSE_COVER_FALLBACK)"
      />
    </figure>

    <div class="course-info">
      <div class="course-heading">
        <h3>{{ course.title }}</h3>
        <p>{{ course.description || t('courses.noDescription') }}</p>
      </div>

      <div class="course-tags" aria-label="Course metadata">
        <span v-for="tag in metadataTags" :key="tag" class="course-tag">{{ tag }}</span>
      </div>

      <div class="teacher-row">
        <img
          class="teacher-avatar"
          :src="teacherAvatar"
          :alt="teacherName"
          @error="useFallbackImage($event, TEACHER_AVATAR_FALLBACK)"
        />
        <div class="teacher-copy">
          <strong>{{ teacherName }}</strong>
          <span>{{ t('courses.card.instructor') }}</span>
        </div>
      </div>

      <dl class="course-facts">
        <div>
          <dt>{{ t('courses.card.schedule') }}</dt>
          <dd>{{ semesterLabel }}</dd>
        </div>
        <div>
          <dt>{{ t('courses.card.location') }}</dt>
          <dd>{{ locationLabel }}</dd>
        </div>
        <div>
          <dt>{{ t('courses.card.capacity') }}</dt>
          <dd>{{ capacityLabel }}</dd>
        </div>
        <div>
          <dt>{{ t('courses.card.updatedAt') }}</dt>
          <dd>{{ updatedLabel }}</dd>
        </div>
      </dl>

      <div class="course-actions">
        <button type="button" class="view-button" @click="$emit('view', course.id)">
          <span>{{ t('courses.viewDetails') }}</span>
          <ArrowRight :size="15" stroke-width="1.8"/>
        </button>
      </div>
    </div>
  </article>
</template>

<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {ArrowRight} from 'lucide-vue-next'

import type {Course} from '@/features/course/types/course'

const COURSE_COVER_FALLBACK = '/assets/course-cover-default.png'
const TEACHER_AVATAR_FALLBACK = '/assets/avatar-teacher-default.png'

const props = defineProps<{
  course: Course
}>()

defineEmits<{
  view: [id: string]
}>()

const {t, locale} = useI18n()

const coverSrc = computed(() => props.course.coverUrl || COURSE_COVER_FALLBACK)
const teacherAvatar = computed(() => props.course.teacherAvatar || TEACHER_AVATAR_FALLBACK)
const teacherName = computed(() => props.course.teacherName || t('courseDetail.unknownTeacher'))

const statusLabel = computed(() => {
  const map: Record<number, string> = {
    0: t('courses.status.draft'),
    1: t('courses.status.published'),
    2: t('courses.status.archived'),
  }

  return map[props.course.status] ?? '-'
})

const levelLabel = computed(() => {
  const map: Record<number, string> = {
    1: t('courses.level.beginner'),
    2: t('courses.level.intermediate'),
    3: t('courses.level.advanced'),
  }

  return map[props.course.level] ?? ''
})

const courseTypeLabel = computed(() => {
  if (props.course.courseType === null || props.course.courseType === undefined) {
    return ''
  }

  const map: Record<number, string> = {
    0: t('courses.courseType.required'),
    1: t('courses.courseType.elective'),
  }

  return map[props.course.courseType] ?? ''
})

const metadataTags = computed(() => [
  statusLabel.value,
  levelLabel.value,
  courseTypeLabel.value,
  props.course.semester || '',
].filter((tag): tag is string => Boolean(tag)))

const semesterLabel = computed(() => props.course.semester || t('courses.card.unset'))
const locationLabel = computed(() => props.course.location || t('courses.card.unset'))
const capacityLabel = computed(() => {
  if (props.course.maxStudents > 0) {
    return `${props.course.currentStudents}/${props.course.maxStudents}`
  }

  return `${props.course.currentStudents} / ${t('courses.card.unlimited')}`
})
const updatedLabel = computed(() => formatDate(props.course.updatedAt || props.course.createdAt))

function formatDate(dateStr?: string | null): string {
  if (!dateStr) {
    return t('courses.card.unset')
  }

  const date = new Date(dateStr)
  if (Number.isNaN(date.getTime())) {
    return t('courses.card.unset')
  }

  return new Intl.DateTimeFormat(String(locale.value), {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}

function useFallbackImage(event: Event, fallback: string) {
  const image = event.target as HTMLImageElement
  if (image.dataset.fallbackApplied === 'true') {
    return
  }

  image.dataset.fallbackApplied = 'true'
  image.src = fallback
}
</script>

<style scoped>
.course-card {
  position: relative;
  display: flex;
  min-height: 100%;
  flex-direction: column;
  overflow: hidden;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  transition: border-color 0.2s ease, background 0.2s ease;
}

.course-card:hover {
  border-color: var(--color-on-surface);
  background: var(--color-surface-container);
}

.course-cover {
  position: relative;
  display: grid;
  margin: 0;
  aspect-ratio: 16 / 9;
  place-items: center;
  overflow: hidden;
  background: var(--color-surface-canvas);
  border-bottom: 1px solid var(--color-outline-light);
}

.course-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.course-info {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 16px;
  padding: 18px;
}

.course-heading {
  display: grid;
  gap: 6px;
}

.course-heading h3 {
  display: -webkit-box;
  overflow: hidden;
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 23px;
  font-weight: 600;
  line-height: 1.15;
  text-wrap: balance;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.course-heading p {
  display: -webkit-box;
  overflow: hidden;
  margin: 0;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.course-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.course-tag {
  padding: 4px 7px;
  background: var(--color-surface-canvas);
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
}

.teacher-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-top: 1px solid var(--color-outline-light);
  border-bottom: 1px solid var(--color-outline-light);
}

.teacher-avatar {
  width: 40px;
  height: 40px;
  flex: 0 0 auto;
  object-fit: cover;
  border: 1px solid var(--color-outline);
  border-radius: 4px;
}

.teacher-copy {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.teacher-copy strong,
.teacher-copy span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.teacher-copy strong {
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
  font-weight: 800;
}

.teacher-copy span {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
  font-weight: 700;
}

.course-facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}

.course-facts div {
  min-width: 0;
}

.course-facts dt {
  margin-bottom: 4px;
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.course-facts dd {
  overflow: hidden;
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 700;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.course-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: auto;
  padding-top: 2px;
}

.view-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--color-on-surface);
  color: var(--color-on-surface);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.04em;
  transition: gap 0.2s ease, color 0.2s ease, border-color 0.2s ease;
}

.view-button:hover,
.view-button:focus-visible {
  gap: 9px;
  color: var(--color-muted);
  border-color: var(--color-muted);
  outline: none;
}

@media (max-width: 520px) {
  .course-info {
    padding: 16px;
  }

  .course-heading h3 {
    font-size: 21px;
  }

  .course-facts {
    grid-template-columns: 1fr;
  }
}
</style>
