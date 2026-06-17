<template>
  <div class="classroom-page">
    <section v-if="!loading && !session" class="classroom-state">
      <CircleAlert :size="32" stroke-width="1.5"/>
      <h1>{{ t('courseDetail.classSession.roomNotFound') }}</h1>
      <button class="btn-secondary" type="button" @click="router.push('/courses')">
        {{ t('courseDetail.browseCourses') }}
      </button>
    </section>

    <section v-else-if="session && !session.publishedAt" class="classroom-state">
      <CircleAlert :size="32" stroke-width="1.5"/>
      <h1>{{ t('courseDetail.classSession.statusPreparing') }}</h1>
      <p>{{ t('courseDetail.classSession.notPublishedDesc') }}</p>
      <button class="btn-secondary" type="button" @click="backToCourse">
        {{ t('courseDetail.classSession.backToCourse') }}
      </button>
    </section>

    <Classroom3D
        v-else-if="session"
        :session="session"
        @joined="markJoined"
        @left="markLeft"
        @load-error="handleClassroomLoadError"
        @ready="handleClassroomReady"
    />

    <CourseEntryTransition v-if="showEntryTransition" :label="t('courseDetail.classSession.modelLoading')"/>
  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {CircleAlert} from 'lucide-vue-next'

import {getClassSession} from '@/features/course/api/classSession'
import {getCourse} from '@/features/course/api/course'
import type {ClassSession} from '@/features/course/types/classSession'
import type {CourseDetail} from '@/features/course/types/course'
import Classroom3D from '@/features/classroom/components/Classroom3D.vue'
import CourseEntryTransition from '@/features/course/components/CourseEntryTransition.vue'
import {useAuthStore} from '@/features/auth/stores/auth'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const sessionId = computed(() => route.params.sessionId as string)
const isAdmin = computed(() => authStore.user?.role === 0)
const loading = ref(true)
const session = ref<ClassSession | null>(null)
const classroomReady = ref(false)
const classroomLoadFailed = ref(false)
const entryProgressComplete = ref(false)
const showEntryTransition = computed(() =>
    loading.value || Boolean(session.value?.publishedAt && (!classroomReady.value || !entryProgressComplete.value) && !classroomLoadFailed.value),
)

onMounted(() => {
  window.setTimeout(() => {
    entryProgressComplete.value = true
  }, 3000)
  void loadSession()
})

async function loadSession() {
  loading.value = true
  classroomReady.value = false
  classroomLoadFailed.value = false
  try {
    const sessionData = await getClassSession(sessionId.value)
    const courseData = await getCourse(sessionData.courseId)
    if (!canEnterClassroom(courseData)) {
      await router.replace({name: 'course-overview', params: {id: sessionData.courseId}})
      return
    }
    session.value = sessionData
  } catch {
    session.value = null
  } finally {
    loading.value = false
  }
}

function canEnterClassroom(courseData: CourseDetail) {
  const userId = authStore.user?.id
  if (isAdmin.value) return true
  if (!userId) return false
  return courseData.teacherId === userId || Boolean(courseData.teacherIds?.includes(userId)) || Boolean(courseData.enrolled)
}

function markJoined() {
  if (session.value) {
    session.value.joined = true
  }
}

function markLeft() {
  if (session.value) {
    session.value.joined = false
  }
}

function handleClassroomReady() {
  classroomReady.value = true
}

function handleClassroomLoadError() {
  classroomLoadFailed.value = true
}

function backToCourse() {
  if (session.value?.courseId) {
    router.push({name: 'course-class-sessions', params: {id: session.value.courseId}})
    return
  }
  router.push('/courses')
}
</script>

<style scoped>
.classroom-page {
  position: fixed;
  inset: 0;
  z-index: 0;
  width: 100%;
  height: 100dvh;
  margin: 0;
  overflow: hidden;
  padding: 0;
  background: #0b1020;
  color: var(--color-on-surface);
}

.classroom-state {
  display: grid;
  width: 100vw;
  min-height: 100dvh;
  place-items: center;
  align-content: center;
  gap: 12px;
  padding: 40px;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  background: var(--color-surface-card);
  color: var(--color-muted);
  text-align: center;
}

.classroom-state h1 {
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 32px;
  font-weight: 400;
}

.classroom-state p {
  max-width: 56ch;
  margin: 0;
  font-family: var(--font-body);
  line-height: 1.6;
}
</style>
