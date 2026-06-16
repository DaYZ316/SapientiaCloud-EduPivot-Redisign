<template>
  <div class="class-sessions-page">
    <button class="back-link" type="button" @click="router.push({name: 'course-overview', params: {id: courseId}})">
      <ArrowLeft :size="16" stroke-width="1.8"/>
      {{ t('courseDetail.classSession.backToCourse') }}
    </button>

    <section v-if="loading" class="page-state">
      <LoaderCircle :size="24" stroke-width="1.7"/>
      <p>{{ t('courseDetail.loading') }}</p>
    </section>

    <section v-else-if="!course" class="page-state">
      <BookOpen :size="32" stroke-width="1.4"/>
      <h1>{{ t('courseDetail.courseNotFound') }}</h1>
      <button class="secondary-button" type="button" @click="router.push('/courses')">
        {{ t('courseDetail.browseCourses') }}
      </button>
    </section>

    <template v-else>
      <header class="page-header">
        <div>
          <span>{{ t('courseDetail.classSession.tabKicker') }}</span>
          <h1>{{ course.title }}</h1>
          <p>{{ course.description || t('courseDetail.noDescription') }}</p>
        </div>
      </header>

      <CourseClassSessions
        :course="course"
        :course-id="courseId"
        :can-manage-course="canManageCourse"
        :create-request-key="classSessionCreateRequestKey"
      />
    </template>
  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {ArrowLeft, BookOpen, LoaderCircle} from 'lucide-vue-next'

import {getCourse} from '@/features/course/api/course'
import CourseClassSessions from '@/features/course/views/course-detail/CourseClassSessions.vue'
import {useAuthStore} from '@/features/auth/stores/auth'
import type {CourseDetail} from '@/features/course/types/course'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const courseId = route.params.id as string
const loading = ref(true)
const course = ref<CourseDetail | null>(null)
const classSessionCreateRequestKey = ref(route.query.create === '1' ? 1 : 0)

const isAdmin = computed(() => authStore.user?.role === 0)
const canManageCourse = computed(() => {
  const userId = authStore.user?.id
  if (!userId || !course.value) return isAdmin.value
  return isAdmin.value || course.value.teacherId === userId || Boolean(course.value.teacherIds?.includes(userId))
})

watch(() => route.query.create, (value) => {
  if (value === '1') classSessionCreateRequestKey.value += 1
})

onMounted(loadCourse)

async function loadCourse() {
  loading.value = true
  try {
    course.value = await getCourse(courseId)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.class-sessions-page {
  display: grid;
  gap: 24px;
  width: 100%;
  max-width: 1120px;
  margin: 0 auto;
  padding: 10px 0 48px;
  color: var(--color-on-surface);
}

.back-link {
  display: inline-flex;
  align-items: center;
  justify-self: start;
  gap: 8px;
  min-height: 32px;
  padding: 0;
  background: none;
  border: 0;
  color: var(--color-muted);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 13px;
  font-weight: 700;
}

.back-link:hover,
.back-link:focus-visible {
  color: var(--color-on-surface);
  outline: none;
}

.page-header {
  padding-bottom: 20px;
  border-bottom: 1px solid var(--color-outline-light);
}

.page-header span {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.page-header h1 {
  margin: 8px 0 10px;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: clamp(32px, 4vw, 46px);
  font-weight: 400;
  line-height: 1.25;
}

.page-header p {
  max-width: 70ch;
  margin: 0;
  color: var(--color-on-surface-variant);
  font-family: var(--font-body);
  font-size: 16px;
  line-height: 1.6;
}

.page-state {
  display: grid;
  min-height: 320px;
  place-items: center;
  align-content: center;
  gap: 12px;
  color: var(--color-muted);
  text-align: center;
}

.page-state h1 {
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 400;
}

.page-state p {
  margin: 0;
}
</style>
