<template>
  <main class="class-session-live-view">
    <section v-if="!loading && !session" class="live-state">
      <CircleAlert :size="32" stroke-width="1.5" />
      <h1>{{ t('courseDetail.live.liveClassNotFound') }}</h1>
      <button class="btn-secondary" type="button" @click="router.push('/courses')">{{ t('courseDetail.backToCourses') }}</button>
    </section>

    <section v-else-if="session && !session.publishedAt" class="live-state">
      <CircleAlert :size="32" stroke-width="1.5" />
      <h1>{{ t('courseDetail.live.sessionNotPublished') }}</h1>
      <p>{{ t('courseDetail.live.publishBeforeLive') }}</p>
      <button class="btn-secondary" type="button" @click="backToCourse">{{ t('courseDetail.classSession.backToCourse') }}</button>
    </section>

    <ClassroomLiveExperience
      v-else-if="session"
      :can-participate="canUseClassroomLive"
      :is-teacher="isSessionOpeningTeacher"
      mode="fullscreen"
      :session="session"
      @close="backToRoom"
      @session-change="applySessionUpdate"
    />
  </main>
</template>

<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {CircleAlert} from 'lucide-vue-next'

import ClassroomLiveExperience from '@/features/classroom/components/ClassroomLiveExperience.vue'
import {getClassSession, listClassSessionParticipants} from '@/features/course/api/classSession'
import {getCourse} from '@/features/course/api/course'
import {useAuthStore} from '@/features/auth/stores/auth'
import {ClassLiveStatus, type ClassParticipant, type ClassSession} from '@/features/course/types/classSession'
import type {CourseDetail} from '@/features/course/types/course'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const {t} = useI18n()

const sessionId = computed(() => route.params.sessionId as string)
const isAdmin = computed(() => authStore.user?.role === 0)
const isTeacher = computed(() => authStore.user?.role === 2)
const loading = ref(true)
const session = ref<ClassSession | null>(null)
const course = ref<CourseDetail | null>(null)
const participants = ref<ClassParticipant[]>([])

const canManageSessionCourse = computed(() => {
  const userId = authStore.user?.id
  if (isAdmin.value) return true
  if (!userId || !course.value) return false
  return course.value.teacherId === userId || Boolean(course.value.teacherIds?.includes(userId))
})
const isSessionOpeningTeacher = computed(() => {
  const userId = authStore.user?.id
  return Boolean(userId && session.value?.teacherId === userId)
})
const currentUserSeated = computed(() => {
  const userId = authStore.user?.id
  if (!userId) return false
  return participants.value.some(participant => participant.userId === userId && participant.seatIndex != null)
})
const canUseClassroomLive = computed(() => canManageSessionCourse.value || currentUserSeated.value)

onMounted(() => {
  void loadSession()
})

async function loadSession() {
  loading.value = true
  try {
    const sessionData = await getClassSession(sessionId.value)
    const courseData = await getCourse(sessionData.courseId)
    if (!canEnterClassroom(courseData)) {
      await router.replace({name: 'course-overview', params: {id: sessionData.courseId}})
      return
    }
    session.value = sessionData
    course.value = courseData
    participants.value = await listClassSessionParticipants(sessionData.id)
  } catch {
    session.value = null
  } finally {
    loading.value = false
  }
}

function canEnterClassroom(courseData: CourseDetail) {
  const userId = authStore.user?.id
  if (isAdmin.value) return true
  if (isTeacher.value) return true
  if (!userId) return false
  return courseData.teacherId === userId || Boolean(courseData.teacherIds?.includes(userId)) || Boolean(courseData.enrolled)
}

function applySessionUpdate(nextSession: ClassSession) {
  session.value = nextSession
}

function backToRoom() {
  router.push({
    name: 'class-session-room',
    params: {sessionId: sessionId.value},
    query: shouldMinimizeTeacherLive() ? {liveFloating: '1'} : undefined,
  })
}

function backToCourse() {
  if (session.value?.courseId) {
    router.push({name: 'course-class-sessions', params: {id: session.value.courseId}})
    return
  }
  router.push('/courses')
}

function shouldMinimizeTeacherLive() {
  return Boolean(isSessionOpeningTeacher.value
      && session.value
      && (session.value.liveStatus === ClassLiveStatus.LIVE || session.value.liveStatus === ClassLiveStatus.PAUSED))
}
</script>

<style scoped>
.class-session-live-view {
  min-height: 100dvh;
  background: #ffffff;
}

.live-state {
  display: grid;
  min-height: 100dvh;
  place-items: center;
  align-content: center;
  gap: 14px;
  padding: 40px;
  background: var(--color-surface-canvas);
  color: var(--color-muted);
  text-align: center;
}

.live-state h1 {
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 32px;
  font-weight: 400;
}

.live-state p {
  margin: 0;
}
</style>
