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
        @exit="exitClassroom"
        @loading-progress="handleClassroomProgress"
        @load-error="handleClassroomLoadError"
        @live-status-change="handleLiveStatusChange"
        @participants-change="handleParticipantsChange"
        @ready="handleClassroomReady"
    />

    <div v-if="session?.publishedAt" class="classroom-tool-actions">
      <button class="floating-action secondary" type="button" @click="openChapterPreviewPanel">
        <BookOpen :size="18" stroke-width="1.8"/>
        章节预览
      </button>
      <button class="floating-action secondary" type="button" @click="openSeatedStudentsPanel">
        <Users :size="18" stroke-width="1.8"/>
        在线学生
        <span class="action-count">{{ seatedStudentCount }}</span>
      </button>
      <button class="floating-action secondary" type="button" @click="openAiSummaryPanel">
        <Sparkles :size="18" stroke-width="1.8"/>
        AI 总结
      </button>
      <button v-if="canUseLivePracticePanel" class="floating-action" type="button" @click="openLivePracticePanel">
        <ClipboardList :size="18" stroke-width="1.8"/>
        {{ canManageSessionCourse ? '发布练习' : '随堂练习' }}
      </button>
      <button class="floating-action" type="button" @click="openClassroomLivePanel">
        <Video :size="18" stroke-width="1.8"/>
        课堂直播
      </button>
    </div>

    <LivePracticePanel
        v-if="session?.publishedAt && canUseLivePracticePanel && showLivePracticePanel"
        :initial-group-id="initialPracticeGroupId"
        :is-teacher="canManageSessionCourse"
        :session="session"
        @close="closeLivePracticePanel"
    />

    <ChapterPreviewPanel
        v-if="session?.publishedAt && showChapterPreviewPanel"
        :session="session"
        @close="closeChapterPreviewPanel"
    />

    <SeatedStudentsPanel
        v-if="session?.publishedAt && showSeatedStudentsPanel"
        :participants="seatedParticipants"
        :session="session"
        @close="closeSeatedStudentsPanel"
    />

    <AiLiveSummaryPanel
        v-if="session?.publishedAt && showAiSummaryPanel"
        :can-manage="canManageSessionCourse"
        :session="session"
        @close="closeAiSummaryPanel"
    />

    <ClassroomLivePanel
        v-if="session?.publishedAt && showClassroomLivePanel"
        :can-participate="canUseClassroomLive"
        :is-teacher="isSessionOpeningTeacher"
        :session="session"
        @close="closeClassroomLivePanel"
        @session-change="applySessionUpdate"
    />

    <CourseEntryTransition
        v-if="showEntryTransition"
        :label="classroomProgressLabel"
        :progress="classroomProgress"
    />
  </div>
</template>

<script lang="ts" setup>
import {computed, defineAsyncComponent, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {BookOpen, CircleAlert, ClipboardList, Sparkles, Users, Video} from 'lucide-vue-next'

import {getClassSession} from '@/features/course/api/classSession'
import {getCourse} from '@/features/course/api/course'
import type {ClassParticipant, ClassSession} from '@/features/course/types/classSession'
import type {SeatSyncMessage} from '@/features/classroom/types/classroom'
import type {CourseDetail} from '@/features/course/types/course'
import CourseEntryTransition from '@/features/course/components/CourseEntryTransition.vue'
import {useAuthStore} from '@/features/auth/stores/auth'

const AiLiveSummaryPanel = defineAsyncComponent(() => import('@/features/ai/components/AiLiveSummaryPanel.vue'))
const Classroom3D = defineAsyncComponent(() => import('@/features/classroom/components/Classroom3D.vue'))
const ClassroomLivePanel = defineAsyncComponent(() => import('@/features/classroom/components/ClassroomLivePanel.vue'))
const ChapterPreviewPanel = defineAsyncComponent(() => import('@/features/classroom/components/ChapterPreviewPanel.vue'))
const LivePracticePanel = defineAsyncComponent(() => import('@/features/live-practice/components/LivePracticePanel.vue'))
const SeatedStudentsPanel = defineAsyncComponent(() => import('@/features/classroom/components/SeatedStudentsPanel.vue'))

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const sessionId = computed(() => route.params.sessionId as string)
const isAdmin = computed(() => authStore.user?.role === 0)
const isTeacher = computed(() => authStore.user?.role === 2)
const loading = ref(true)
const session = ref<ClassSession | null>(null)
const course = ref<CourseDetail | null>(null)
const classroomReady = ref(false)
const classroomLoadFailed = ref(false)
const classroomProgress = ref(0)
const classroomProgressLabel = ref('\u6b63\u5728\u83b7\u53d6\u8bfe\u5802\u4fe1\u606f')
const showLivePracticePanel = ref(false)
const showChapterPreviewPanel = ref(false)
const showSeatedStudentsPanel = ref(false)
const showAiSummaryPanel = ref(false)
const showClassroomLivePanel = ref(false)
const initialPracticeGroupId = ref<string | null>(null)
const seatedParticipants = ref<ClassParticipant[]>([])
const showEntryTransition = computed(() =>
    loading.value || Boolean(session.value?.publishedAt && !classroomReady.value && !classroomLoadFailed.value),
)
const seatedStudentCount = computed(() =>
    seatedParticipants.value.filter(participant => participant.role === 1 && participant.seatIndex != null).length,
)
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
const canUseLivePracticePanel = computed(() => !isTeacher.value || canManageSessionCourse.value)
const currentUserSeated = computed(() => {
  const userId = authStore.user?.id
  if (!userId) return false
  return seatedParticipants.value.some(participant => participant.userId === userId && participant.seatIndex != null)
})
const canUseClassroomLive = computed(() => canManageSessionCourse.value || currentUserSeated.value)

onMounted(() => {
  void loadSession()
})

async function loadSession() {
  loading.value = true
  classroomReady.value = false
  classroomLoadFailed.value = false
  classroomProgress.value = 0
  classroomProgressLabel.value = '\u6b63\u5728\u83b7\u53d6\u8bfe\u5802\u4fe1\u606f'
  try {
    const sessionData = await getClassSession(sessionId.value)
    classroomProgressLabel.value = '\u6b63\u5728\u6821\u9a8c\u8bfe\u7a0b\u8bbf\u95ee\u6743\u9650'
    const courseData = await getCourse(sessionData.courseId)
    if (!canEnterClassroom(courseData)) {
      await router.replace({name: 'course-overview', params: {id: sessionData.courseId}})
      return
    }
    course.value = courseData
    session.value = sessionData
    if (typeof route.query.practice === 'string' && canUseLivePracticePanel.value) {
      initialPracticeGroupId.value = route.query.practice
      showLivePracticePanel.value = true
    }
    classroomProgress.value = 10
    classroomProgressLabel.value = '\u6b63\u5728\u521d\u59cb\u5316 WebGL \u753b\u5e03\u4e0e\u955c\u5934'
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

function applySessionUpdate(nextSession: ClassSession) {
  session.value = nextSession
}

function handleClassroomReady() {
  classroomProgress.value = 100
  classroomProgressLabel.value = '\u5373\u5c06\u8fdb\u5165\u6559\u5ba4'
  classroomReady.value = true
}

function openLivePracticePanel() {
  if (!canUseLivePracticePanel.value) return
  closeSidePanels()
  initialPracticeGroupId.value = typeof route.query.practice === 'string' ? route.query.practice : null
  showLivePracticePanel.value = true
}

function closeLivePracticePanel() {
  showLivePracticePanel.value = false
  initialPracticeGroupId.value = null
}

function openChapterPreviewPanel() {
  closeSidePanels()
  showChapterPreviewPanel.value = true
}

function closeChapterPreviewPanel() {
  showChapterPreviewPanel.value = false
}

function openSeatedStudentsPanel() {
  closeSidePanels()
  showSeatedStudentsPanel.value = true
}

function closeSeatedStudentsPanel() {
  showSeatedStudentsPanel.value = false
}

function openAiSummaryPanel() {
  closeSidePanels()
  showAiSummaryPanel.value = true
}

function closeAiSummaryPanel() {
  showAiSummaryPanel.value = false
}

function openClassroomLivePanel() {
  closeSidePanels()
  showClassroomLivePanel.value = true
}

function closeClassroomLivePanel() {
  showClassroomLivePanel.value = false
}

function closeSidePanels() {
  closeLivePracticePanel()
  closeChapterPreviewPanel()
  closeSeatedStudentsPanel()
  closeAiSummaryPanel()
  closeClassroomLivePanel()
}

function handleParticipantsChange(participants: ClassParticipant[]) {
  seatedParticipants.value = participants
}

function handleLiveStatusChange(message: SeatSyncMessage) {
  if (!session.value || message.sessionId !== session.value.id || message.liveStatus == null) {
    return
  }
  session.value = {
    ...session.value,
    liveStatus: message.liveStatus,
    liveStatusText: message.liveStatusText ?? session.value.liveStatusText,
    liveStartedAt: message.liveStartedAt ?? session.value.liveStartedAt,
    livePausedAt: message.livePausedAt ?? null,
    liveEndedAt: message.liveEndedAt ?? session.value.liveEndedAt,
  }
}

function handleClassroomLoadError() {
  classroomLoadFailed.value = true
}

function handleClassroomProgress(payload: number | {progress: number; label?: string}) {
  const progress = typeof payload === 'number' ? payload : payload.progress
  if (!Number.isFinite(progress)) {
    return
  }
  classroomProgress.value = Math.max(classroomProgress.value, Math.min(Math.max(progress, 0), 100))
  if (typeof payload !== 'number' && payload.label) {
    classroomProgressLabel.value = payload.label
  }
}

function exitClassroom() {
  router.back()
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
  background: var(--color-surface-canvas);
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

.classroom-tool-actions {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 1800;
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.floating-action {
  display: inline-flex;
  min-height: 42px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 0 14px;
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-sm);
  color: var(--color-on-primary);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 14px;
  box-shadow: var(--shadow-card);
}

.floating-action.secondary {
  background: color-mix(in srgb, var(--color-surface-card) 86%, transparent);
  border-color: var(--color-outline-light);
  color: var(--color-on-surface);
}

.action-count {
  display: inline-grid;
  min-width: 22px;
  height: 22px;
  place-items: center;
  padding: 0 6px;
  border-radius: var(--radius-pill);
  background: var(--color-surface-container);
  color: inherit;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 640px) {
  .classroom-tool-actions {
    right: 12px;
    bottom: 12px;
    left: 12px;
  }

  .floating-action {
    flex: 1 1 120px;
  }
}
</style>
