<template>
  <article class="course-card">
    <figure class="course-cover">
      <img
        :src="coverSrc"
        :alt="course.title"
        @error="useFallbackImage($event, COURSE_COVER_FALLBACK)"
      />
      <div class="cover-gradient"></div>
      <a
        class="teacher-chip"
        :href="`/profile/${course.teacherId}`"
        @click.prevent="navigateToTeacher"
      >
        <img
          class="teacher-avatar"
          :src="teacherAvatar"
          :alt="teacherName"
          @error="useFallbackImage($event, TEACHER_AVATAR_FALLBACK)"
        />
        <span>{{ teacherName }}</span>
      </a>
    </figure>

    <div class="course-info">
      <div class="course-heading">
        <h3>{{ course.title }}</h3>
        <p>{{ course.description || t('courses.noDescription') }}</p>
      </div>

      <div class="course-tags" aria-label="Course metadata">
        <span v-for="tag in metadataTags" :key="tag" class="course-tag">{{ tag }}</span>
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
          <ArrowRight :size="14" stroke-width="2"/>
        </button>
      </div>
    </div>
  </article>
</template>

<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import {ArrowRight} from 'lucide-vue-next'

import {useAuthStore} from '@/features/auth/stores/auth'
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
const router = useRouter()
const authStore = useAuthStore()

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

function navigateToTeacher(event: Event) {
  event.stopPropagation()
  if (!props.course.teacherId) {
    return
  }

  if (authStore.user?.id === props.course.teacherId) {
    router.push({name: 'profile'})
  } else {
    router.push({name: 'user-profile', params: {userId: props.course.teacherId}})
  }
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
  border-radius: var(--radius-md);
  box-shadow:
    0 1px 2px rgba(0, 0, 0, 0.04),
    0 4px 16px rgba(0, 0, 0, 0.03);
  transition:
    box-shadow 0.4s cubic-bezier(0.22, 1, 0.36, 1),
    border-color 0.4s cubic-bezier(0.22, 1, 0.36, 1);
}

.course-card:hover {
  border-color: var(--color-on-surface);
  box-shadow:
    inset 0 0 0 1px var(--color-on-surface),
    0 0 0 3px color-mix(in srgb, var(--color-on-surface) 8%, transparent),
    0 2px 4px rgba(0, 0, 0, 0.06),
    0 12px 40px rgba(0, 0, 0, 0.08);
}

/* cover */

.course-cover {
  position: relative;
  display: grid;
  margin: 0;
  aspect-ratio: 16 / 9;
  place-items: center;
  overflow: hidden;
  background: var(--color-surface-canvas);
  border-radius: calc(var(--radius-md) - 1px) calc(var(--radius-md) - 1px) 0 0;
}

.course-cover::after {
  position: absolute;
  inset: 0;
  border: 1px solid transparent;
  border-radius: inherit;
  content: '';
  pointer-events: none;
  transition: border-color 0.4s cubic-bezier(0.22, 1, 0.36, 1);
}

.course-card:hover .course-cover::after {
  border-color: var(--color-on-surface);
}

.course-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.5s cubic-bezier(0.22, 1, 0.36, 1);
}

.course-card:hover .course-cover img {
  transform: scale(1.04);
}

.cover-gradient {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 50%;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.55), transparent);
  pointer-events: none;
}

/* teacher chip overlaid on cover */

.teacher-chip {
  position: absolute;
  right: 14px;
  bottom: 14px;
  left: 14px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px 8px 8px;
  background: rgba(0, 0, 0, 0.36);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  backdrop-filter: blur(12px) saturate(140%);
  -webkit-backdrop-filter: blur(12px) saturate(140%);
  cursor: pointer;
  text-decoration: none;
  transition: background 0.2s ease, border-color 0.2s ease;
}

.teacher-chip:hover {
  background: rgba(0, 0, 0, 0.5);
  border-color: rgba(255, 255, 255, 0.25);
}

.teacher-chip .teacher-avatar {
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  object-fit: cover;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.15);
}

.teacher-chip span {
  overflow: hidden;
  color: #fff;
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
  text-overflow: ellipsis;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.4);
}

/* info section */

.course-info {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 14px;
  padding: 18px 20px 20px;
}

.course-heading {
  display: grid;
  gap: 4px;
}

.course-heading h3 {
  display: -webkit-box;
  overflow: hidden;
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 22px;
  font-weight: 700;
  line-height: 1.2;
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
  font-size: 13.5px;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

/* tags */

.course-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.course-tag {
  padding: 3px 8px;
  background: var(--color-surface-container);
  border: 1px solid transparent;
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
  font-weight: 400;
  line-height: 1;
  border-radius: 6px;
}

/* facts */

.course-facts {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px 16px;
  margin: 0;
}

.course-facts div {
  min-width: 0;
}

.course-facts dt {
  margin-bottom: 2px;
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 10.5px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.course-facts dd {
  overflow: hidden;
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 400;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* actions */

.course-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: auto;
  padding-top: 2px;
}

.view-button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 0;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--color-on-surface);
  color: var(--color-on-surface);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.04em;
  transition: gap 0.3s cubic-bezier(0.22, 1, 0.36, 1), color 0.3s cubic-bezier(0.22, 1, 0.36, 1);
}

.view-button:hover,
.view-button:focus-visible {
  gap: 10px;
  color: var(--color-muted);
  outline: none;
}

/* mobile */

@media (max-width: 520px) {
  .course-info {
    padding: 16px;
  }

  .course-heading h3 {
    font-size: 20px;
  }

  .course-facts {
    grid-template-columns: 1fr;
  }

  .teacher-chip {
    left: 10px;
    right: 10px;
    bottom: 10px;
  }
}
</style>
