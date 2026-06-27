<template>
  <main class="class-session-live-view">
    <section v-if="!loading && !session" class="live-state">
      <CircleAlert :size="32" stroke-width="1.5"/>
      <h1>{{ t('courseDetail.live.liveClassNotFound') }}</h1>
      <button class="btn-secondary" type="button" @click="router.push('/courses')">{{
          t('courseDetail.backToCourses')
        }}
      </button>
    </section>

    <section v-else-if="session && !session.publishedAt" class="live-state">
      <CircleAlert :size="32" stroke-width="1.5"/>
      <h1>{{ t('courseDetail.live.sessionNotPublished') }}</h1>
      <p>{{ t('courseDetail.live.publishBeforeLive') }}</p>
      <button class="btn-secondary" type="button" @click="backToCourse">{{
          t('courseDetail.classSession.backToCourse')
        }}
      </button>
    </section>

    <ClassroomLiveExperience
        v-else-if="session"
        :can-participate="canUseClassroomLive"
        :is-teacher="isSessionOpeningTeacher"
        :session="session"
        mode="fullscreen"
        @close="backToRoom"
        @session-change="applySessionUpdate"
    />
  </main>
</template>

<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {onBeforeRouteLeave, useRoute, useRouter} from 'vue-router'
import {CircleAlert} from 'lucide-vue-next'

import ClassroomLiveExperience from '@/features/classroom/components/ClassroomLiveExperience.vue'
import {
  getClassSession,
  issueClassSessionSeatSyncToken,
  listClassSessionParticipants
} from '@/features/course/api/classSession'
import {getCourse} from '@/features/course/api/course'
import {useAuthStore} from '@/features/auth/stores/auth'
import {ClassLiveStatus, type ClassParticipant, type ClassSession} from '@/features/course/types/classSession'
import type {CourseDetail} from '@/features/course/types/course'
import {useClassroomLiveMiniStore} from '@/features/classroom/stores/classroomLiveMini'
import type {SeatSyncMessage} from '@/features/classroom/types/classroom'
import {mergeSeatSyncLiveStatus} from '@/features/classroom/composables/liveStatusSync'
import {buildSeatSyncSocketUrl} from '@/features/classroom/composables/seatSyncSocket'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const liveMini = useClassroomLiveMiniStore()
const {t} = useI18n()

const sessionId = computed(() => route.params.sessionId as string)
const isAdmin = computed(() => authStore.user?.role === 0)
const isTeacher = computed(() => authStore.user?.role === 2)
const loading = ref(true)
const session = ref<ClassSession | null>(null)
const course = ref<CourseDetail | null>(null)
const participants = ref<ClassParticipant[]>([])
let seatSocket: WebSocket | null = null
let seatSocketStarted = false
let seatSocketReconnectTimer: number | null = null
let seatSocketReconnectAttempts = 0
let destroyed = false

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

onBeforeUnmount(() => {
  destroyed = true
  disconnectSeatSocket()
  clearSeatSocketReconnectTimer()
})

onBeforeRouteLeave(() => {
  showMiniWindowIfLive()
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
    course.value = courseData
    participants.value = await listClassSessionParticipants(sessionData.id)
    liveMini.clearSession(sessionData.id)
    session.value = sessionData
    void connectSeatSocket()
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
  liveMini.updateSession(nextSession)
}

async function connectSeatSocket() {
  if (!session.value || destroyed || seatSocketStarted || isSeatSocketActive()) {
    return
  }
  seatSocketStarted = true
  try {
    const token = await issueClassSessionSeatSyncToken(session.value.id)
    if (destroyed || !session.value) {
      return
    }
    const socket = new WebSocket(buildSeatSyncSocketUrl(session.value.id, token.token))
    seatSocket = socket
    socket.onopen = () => {
      seatSocketReconnectAttempts = 0
    }
    socket.onmessage = (event) => handleSeatSyncMessage(event.data)
    socket.onclose = () => {
      if (seatSocket === socket) {
        seatSocket = null
        seatSocketStarted = false
        scheduleSeatSocketReconnect()
      }
    }
    socket.onerror = () => {
      if (seatSocket === socket) {
        socket.close()
      }
    }
  } catch {
    seatSocketStarted = false
    scheduleSeatSocketReconnect()
  }
}

function disconnectSeatSocket() {
  const socket = seatSocket
  seatSocket = null
  seatSocketStarted = false
  socket?.close()
}

function scheduleSeatSocketReconnect() {
  if (destroyed || seatSocketReconnectTimer != null) {
    return
  }
  seatSocketReconnectAttempts += 1
  const delay = Math.min(1000 * seatSocketReconnectAttempts, 8000)
  seatSocketReconnectTimer = window.setTimeout(() => {
    seatSocketReconnectTimer = null
    void connectSeatSocket()
  }, delay)
}

function clearSeatSocketReconnectTimer() {
  if (seatSocketReconnectTimer == null) {
    return
  }
  window.clearTimeout(seatSocketReconnectTimer)
  seatSocketReconnectTimer = null
}

function isSeatSocketActive() {
  return seatSocket?.readyState === WebSocket.OPEN || seatSocket?.readyState === WebSocket.CONNECTING
}

function handleSeatSyncMessage(raw: string) {
  try {
    const message = JSON.parse(raw) as SeatSyncMessage
    if (!session.value || message.sessionId !== session.value.id) {
      return
    }
    if (message.type === 'seat_snapshot') {
      participants.value = message.participants || []
      applyLiveStatusMessage(message)
      return
    }
    if (message.type === 'seat_upsert' && message.participant) {
      upsertParticipant(message.participant)
      return
    }
    if (message.type === 'seat_remove') {
      removeParticipant(message.userId || '', message.seatIndex)
      return
    }
    if (
        message.type === 'live_started' ||
        message.type === 'live_paused' ||
        message.type === 'live_resumed' ||
        message.type === 'live_stopped'
    ) {
      applyLiveStatusMessage(message)
    }
  } catch {
    // Ignore malformed WebSocket payloads.
  }
}

function applyLiveStatusMessage(message: SeatSyncMessage) {
  if (!session.value || message.liveStatus == null) {
    return
  }
  applySessionUpdate(mergeSeatSyncLiveStatus(session.value, message))
}

function upsertParticipant(participant: ClassParticipant) {
  participants.value = [
    ...participants.value.filter(item => item.userId !== participant.userId),
    participant,
  ]
}

function removeParticipant(userId: string, seatIndex?: number | null) {
  participants.value = participants.value.filter(participant => {
    if (participant.userId === userId) {
      return false
    }
    return seatIndex == null || participant.seatIndex !== seatIndex
  })
}

function backToRoom() {
  showMiniWindowIfLive()
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
      && session.value.liveStatus === ClassLiveStatus.LIVE)
}

function showMiniWindowIfLive() {
  if (!session.value) {
    return
  }
  if (!shouldMinimizeTeacherLive()) {
    liveMini.updateSession(session.value)
    return
  }
  liveMini.show({
    session: session.value,
    isTeacher: isSessionOpeningTeacher.value,
    canParticipate: canUseClassroomLive.value,
  })
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
