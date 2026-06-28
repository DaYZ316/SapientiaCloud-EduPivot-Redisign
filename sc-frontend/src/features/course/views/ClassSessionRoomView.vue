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
        :opening-teacher-name="openingTeacherName"
        :session="session"
        @exit="exitClassroom"
        @joined="markJoined"
        @left="markLeft"
        @ready="handleClassroomReady"
        @loading-progress="handleClassroomProgress"
        @load-error="handleClassroomLoadError"
        @live-status-change="handleLiveStatusChange"
        @participants-change="handleParticipantsChange"
    />

    <div v-if="session?.publishedAt" class="classroom-tool-actions">
      <button class="floating-action secondary" type="button" @click="openChapterPreviewPanel">
        <BookOpen :size="18" stroke-width="1.8"/>
        {{ t('courseDetail.classSession.toolChapters') }}
      </button>
      <button class="floating-action secondary" type="button" @click="openSeatedStudentsPanel">
        <Users :size="18" stroke-width="1.8"/>
        {{ t('courseDetail.classSession.toolStudents') }}
        <span class="action-count">{{ seatedStudentCount }}</span>
      </button>
      <button class="floating-action secondary" type="button" @click="openAiSummaryPanel">
        <Sparkles :size="18" stroke-width="1.8"/>
        {{ t('courseDetail.classSession.toolAiSummary') }}
      </button>
      <button v-if="canUseLivePracticePanel" class="floating-action" type="button" @click="openLivePracticePanel">
        <ClipboardList :size="18" stroke-width="1.8"/>
        {{
          canManageSessionCourse ? t('courseDetail.classSession.toolPublishPractice') : t('courseDetail.classSession.toolLivePractice')
        }}
      </button>
      <button class="floating-action" type="button" @click="openClassroomLivePanel">
        <Video :size="18" stroke-width="1.8"/>
        {{ t('courseDetail.classSession.toolLive') }}
      </button>
    </div>

    <FloatingWindow
        v-if="session?.publishedAt && canUseLivePracticePanel && showLivePracticePanel"
        :default-height="LIVE_PRACTICE_WINDOW.defaultHeight"
        :default-width="LIVE_PRACTICE_WINDOW.defaultWidth"
        :disable-on-mobile="true"
        :margin="CLASSROOM_WINDOW_MARGIN"
        :min-height="LIVE_PRACTICE_WINDOW.minHeight"
        :min-width="LIVE_PRACTICE_WINDOW.minWidth"
        :mobile-breakpoint="CLASSROOM_WINDOW_MOBILE_BREAKPOINT"
        drag-handle-selector=".panel-header"
        layer-class="classroom-window-layer"
        window-class="classroom-floating-window live-practice-floating-window"
    >
      <LivePracticePanel
          :initial-group-id="initialPracticeGroupId"
          :is-teacher="canManageSessionCourse"
          :session="session"
          @close="closeLivePracticePanel"
      />
    </FloatingWindow>

    <FloatingWindow
        v-if="session?.publishedAt && showChapterPreviewPanel"
        :default-height="CHAPTER_PREVIEW_WINDOW.defaultHeight"
        :default-width="CHAPTER_PREVIEW_WINDOW.defaultWidth"
        :disable-on-mobile="true"
        :margin="CLASSROOM_WINDOW_MARGIN"
        :min-height="CHAPTER_PREVIEW_WINDOW.minHeight"
        :min-width="CHAPTER_PREVIEW_WINDOW.minWidth"
        :mobile-breakpoint="CLASSROOM_WINDOW_MOBILE_BREAKPOINT"
        drag-handle-selector=".panel-header"
        layer-class="classroom-window-layer"
        window-class="classroom-floating-window chapter-preview-floating-window"
    >
      <ChapterPreviewPanel
          :session="session"
          @close="closeChapterPreviewPanel"
      />
    </FloatingWindow>

    <FloatingWindow
        v-if="session?.publishedAt && showSeatedStudentsPanel"
        :default-height="SEATED_STUDENTS_WINDOW.defaultHeight"
        :default-width="SEATED_STUDENTS_WINDOW.defaultWidth"
        :disable-on-mobile="true"
        :margin="CLASSROOM_WINDOW_MARGIN"
        :min-height="SEATED_STUDENTS_WINDOW.minHeight"
        :min-width="SEATED_STUDENTS_WINDOW.minWidth"
        :mobile-breakpoint="CLASSROOM_WINDOW_MOBILE_BREAKPOINT"
        drag-handle-selector=".panel-header"
        layer-class="classroom-window-layer"
        window-class="classroom-floating-window seated-students-floating-window"
    >
      <SeatedStudentsPanel
          :participants="seatedParticipants"
          :session="session"
          @close="closeSeatedStudentsPanel"
      />
    </FloatingWindow>

    <FloatingWindow
        v-if="session?.publishedAt && showAiSummaryPanel"
        :default-height="AI_SUMMARY_WINDOW.defaultHeight"
        :default-width="AI_SUMMARY_WINDOW.defaultWidth"
        :disable-on-mobile="true"
        :margin="CLASSROOM_WINDOW_MARGIN"
        :min-height="AI_SUMMARY_WINDOW.minHeight"
        :min-width="AI_SUMMARY_WINDOW.minWidth"
        :mobile-breakpoint="CLASSROOM_WINDOW_MOBILE_BREAKPOINT"
        drag-handle-selector=".panel-header"
        layer-class="classroom-window-layer"
        window-class="classroom-floating-window ai-summary-floating-window"
    >
      <AiLiveSummaryPanel
          :can-delete-history="isAdmin || isSessionOpeningTeacher"
          :can-manage="canManageSessionCourse"
          :session="session"
          @close="closeAiSummaryPanel"
      />
    </FloatingWindow>

    <FloatingWindow
        v-if="session?.publishedAt && showClassroomLivePanel && !classroomLivePanelCompact"
        :default-height="CLASSROOM_LIVE_WINDOW.defaultHeight"
        :default-width="CLASSROOM_LIVE_WINDOW.defaultWidth"
        :disable-on-mobile="true"
        :margin="CLASSROOM_WINDOW_MARGIN"
        :min-height="CLASSROOM_LIVE_WINDOW.minHeight"
        :min-width="CLASSROOM_LIVE_WINDOW.minWidth"
        :mobile-breakpoint="CLASSROOM_WINDOW_MOBILE_BREAKPOINT"
        :resizable="false"
        drag-handle-selector=".mode-popup .live-header"
        layer-class="classroom-window-layer"
        window-class="classroom-floating-window classroom-live-floating-window"
    >
      <ClassroomLivePanel
          :can-participate="canUseClassroomLive"
          :compact="classroomLivePanelCompact"
          :is-teacher="isSessionOpeningTeacher"
          :session="session"
          @close="closeClassroomLivePanel"
          @expand="expandClassroomLivePanel"
          @open-summary="openAiSummaryPanel"
          @session-change="applySessionUpdate"
      />
    </FloatingWindow>

    <ClassroomLivePanel
        v-else-if="session?.publishedAt && showClassroomLivePanel"
        :can-participate="canUseClassroomLive"
        :compact="classroomLivePanelCompact"
        :is-teacher="isSessionOpeningTeacher"
        :session="session"
        @close="closeClassroomLivePanel"
        @expand="expandClassroomLivePanel"
        @open-summary="openAiSummaryPanel"
        @session-change="applySessionUpdate"
    />

    <CourseEntryTransition
        v-if="showEntryTransition"
        :label="classroomProgressLabel"
        :progress="classroomProgress"
    />

    <GlobalAiDrawer v-if="!showEntryTransition"/>
  </div>
</template>

<script lang="ts" setup>
import {computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {onBeforeRouteLeave, useRoute, useRouter} from 'vue-router'
import {BookOpen, CircleAlert, ClipboardList, Sparkles, Users, Video} from 'lucide-vue-next'

import {getClassSession} from '@/features/course/api/classSession'
import {getCourse} from '@/features/course/api/course'
import {ClassLiveStatus, type ClassParticipant, type ClassSession} from '@/features/course/types/classSession'
import type {SeatSyncMessage} from '@/features/classroom/types/classroom'
import type {CourseDetail} from '@/features/course/types/course'
import CourseEntryTransition from '@/features/course/components/CourseEntryTransition.vue'
import {useAuthStore} from '@/features/auth/stores/auth'
import {useClassroomLiveMiniStore} from '@/features/classroom/stores/classroomLiveMini'
import {stopLiveSummaryAudioUpload} from '@/features/classroom/composables/useClassroomLiveSummaryAudio'
import {mergeSeatSyncLiveStatus} from '@/features/classroom/composables/liveStatusSync'
import GlobalAiDrawer from '@/features/ai/components/GlobalAiDrawer.vue'
import FloatingWindow from '@/shared/components/FloatingWindow.vue'

const OPEN_CLASSROOM_LIVE_PANEL_EVENT = 'edupivot:open-classroom-live-panel'
const OPEN_AI_SUMMARY_PANEL_EVENT = 'edupivot:open-ai-summary-panel'
const CLASSROOM_WINDOW_MARGIN = 24
const CLASSROOM_WINDOW_MOBILE_BREAKPOINT = 1180
const LIVE_PRACTICE_WINDOW = {defaultWidth: 760, defaultHeight: 760, minWidth: 480, minHeight: 520}
const CHAPTER_PREVIEW_WINDOW = {defaultWidth: 860, defaultHeight: 760, minWidth: 520, minHeight: 480}
const SEATED_STUDENTS_WINDOW = {defaultWidth: 420, defaultHeight: 640, minWidth: 360, minHeight: 420}
const AI_SUMMARY_WINDOW = {defaultWidth: 1040, defaultHeight: 760, minWidth: 720, minHeight: 520}
const CLASSROOM_LIVE_WINDOW = {defaultWidth: 400, defaultHeight: 720, minWidth: 400, minHeight: 520}
type ClassroomSidePanelKey = 'livePractice' | 'chapterPreview' | 'seatedStudents' | 'aiSummary' | 'classroomLive'

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
const liveMini = useClassroomLiveMiniStore()

const sessionId = computed(() => route.params.sessionId as string)
const isAdmin = computed(() => authStore.user?.role === 0)
const isTeacher = computed(() => authStore.user?.role === 2)
const loading = ref(true)
const session = ref<ClassSession | null>(null)
const course = ref<CourseDetail | null>(null)
const classroomReady = ref(false)
const classroomLoadFailed = ref(false)
const classroomProgress = ref(0)
const classroomProgressLabel = ref(t('courseDetail.classSession.loadingSteps.resolvingSession'))
const showLivePracticePanel = ref(false)
const showChapterPreviewPanel = ref(false)
const showSeatedStudentsPanel = ref(false)
const showAiSummaryPanel = ref(false)
const showClassroomLivePanel = ref(false)
const classroomLivePanelCompact = ref(false)
const classroomViewportWidth = ref(window.innerWidth)
const hasClassroomCoarsePointer = ref(hasCoarsePrimaryPointer())
const lastOpenedSidePanel = ref<ClassroomSidePanelKey | null>(null)
const initialPracticeGroupId = ref<string | null>(null)
const seatedParticipants = ref<ClassParticipant[]>([])
const showEntryTransition = computed(() =>
    loading.value || Boolean(session.value?.publishedAt && !classroomReady.value && !classroomLoadFailed.value),
)
const seatedStudentCount = computed(() =>
    seatedParticipants.value.filter(participant => participant.role === 1 && participant.seatIndex != null).length,
)
const openingTeacherName = computed(() => {
  const sessionTeacherId = session.value?.teacherId
  if (!sessionTeacherId) return ''
  const matchedTeacher = course.value?.teacherInfos?.find(teacher => teacher.id === sessionTeacherId)
  return matchedTeacher?.displayName || course.value?.teacherName || sessionTeacherId
})
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
const canUseClassroomFloatingWindows = computed(() =>
    classroomViewportWidth.value > CLASSROOM_WINDOW_MOBILE_BREAKPOINT && !hasClassroomCoarsePointer.value,
)

onMounted(() => {
  window.addEventListener('resize', syncClassroomViewport)
  window.addEventListener(OPEN_CLASSROOM_LIVE_PANEL_EVENT, handleOpenClassroomLivePanelRequest)
  window.addEventListener(OPEN_AI_SUMMARY_PANEL_EVENT, handleOpenAiSummaryPanelRequest)
  syncClassroomViewport()
  void loadSession()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncClassroomViewport)
  window.removeEventListener(OPEN_CLASSROOM_LIVE_PANEL_EVENT, handleOpenClassroomLivePanelRequest)
  window.removeEventListener(OPEN_AI_SUMMARY_PANEL_EVENT, handleOpenAiSummaryPanelRequest)
})

onBeforeRouteLeave(() => {
  showMiniWindowIfLive()
})

watch(() => route.query.livePanel, (livePanel) => {
  if (livePanel === '1') {
    openClassroomLivePanel()
  }
})

watch(() => route.query.summaryPanel, (summaryPanel) => {
  if (summaryPanel === '1') {
    openAiSummaryPanel()
  }
})

watch(canUseClassroomFloatingWindows, (canUseFloatingWindows) => {
  if (!canUseFloatingWindows) {
    closeExtraSidePanelsForSingleWindowMode()
  }
})

async function loadSession() {
  loading.value = true
  classroomReady.value = false
  classroomLoadFailed.value = false
  classroomProgress.value = 0
  classroomProgressLabel.value = t('courseDetail.classSession.loadingSteps.resolvingSession')
  try {
    const sessionData = await getClassSession(sessionId.value)
    classroomProgressLabel.value = t('courseDetail.classSession.loadingSteps.checkingAccess')
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
      lastOpenedSidePanel.value = 'livePractice'
    }
    if (route.query.livePanel === '1') {
      openClassroomLivePanel()
    } else if (route.query.summaryPanel === '1') {
      openAiSummaryPanel()
    } else if (route.query.liveFloating === '1' && shouldMinimizeTeacherLive()) {
      void minimizeClassroomLivePanel()
    }
    classroomProgress.value = 10
    classroomProgressLabel.value = t('courseDetail.classSession.loadingSteps.preparingScene')
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
  liveMini.updateSession(nextSession)
}

function handleClassroomReady() {
  classroomProgress.value = 100
  classroomProgressLabel.value = t('courseDetail.classSession.loadingSteps.ready')
  classroomReady.value = true
}

function openLivePracticePanel() {
  if (!canUseLivePracticePanel.value) return
  closeSidePanelsForSingleWindowMode()
  initialPracticeGroupId.value = typeof route.query.practice === 'string' ? route.query.practice : null
  showLivePracticePanel.value = true
  lastOpenedSidePanel.value = 'livePractice'
}

function closeLivePracticePanel() {
  showLivePracticePanel.value = false
  initialPracticeGroupId.value = null
  clearLastOpenedSidePanel('livePractice')
}

function openChapterPreviewPanel() {
  closeSidePanelsForSingleWindowMode()
  showChapterPreviewPanel.value = true
  lastOpenedSidePanel.value = 'chapterPreview'
}

function closeChapterPreviewPanel() {
  showChapterPreviewPanel.value = false
  clearLastOpenedSidePanel('chapterPreview')
}

function openSeatedStudentsPanel() {
  closeSidePanelsForSingleWindowMode()
  showSeatedStudentsPanel.value = true
  lastOpenedSidePanel.value = 'seatedStudents'
}

function closeSeatedStudentsPanel() {
  showSeatedStudentsPanel.value = false
  clearLastOpenedSidePanel('seatedStudents')
}

function openAiSummaryPanel() {
  closeSidePanelsForSingleWindowMode()
  showAiSummaryPanel.value = true
  lastOpenedSidePanel.value = 'aiSummary'
}

function closeAiSummaryPanel() {
  showAiSummaryPanel.value = false
  clearLastOpenedSidePanel('aiSummary')
}

function openClassroomLivePanel() {
  closeSidePanelsForSingleWindowMode()
  liveMini.clear()
  classroomLivePanelCompact.value = false
  showClassroomLivePanel.value = true
  lastOpenedSidePanel.value = 'classroomLive'
  replaceClassroomLiveQuery('panel')
}

function handleOpenClassroomLivePanelRequest(event: Event) {
  const detail = (event as CustomEvent<{ sessionId?: string }>).detail
  if (!session.value || detail?.sessionId !== session.value.id || !session.value.publishedAt) {
    return
  }
  openClassroomLivePanel()
}

function handleOpenAiSummaryPanelRequest(event: Event) {
  const detail = (event as CustomEvent<{ sessionId?: string }>).detail
  if (!session.value || detail?.sessionId !== session.value.id || !session.value.publishedAt) {
    return
  }
  openAiSummaryPanel()
}

async function closeClassroomLivePanel(forceClose = false) {
  if (forceClose) {
    showClassroomLivePanel.value = false
    classroomLivePanelCompact.value = false
    clearLastOpenedSidePanel('classroomLive')
    return
  }
  if (shouldMinimizeTeacherLive()) {
    await minimizeClassroomLivePanel()
    return
  }
  showClassroomLivePanel.value = false
  classroomLivePanelCompact.value = false
  clearLastOpenedSidePanel('classroomLive')
  replaceClassroomLiveQuery(null)
}

function expandClassroomLivePanel() {
  showClassroomLivePanel.value = true
  classroomLivePanelCompact.value = false
  replaceClassroomLiveQuery('panel')
}

async function minimizeClassroomLivePanel() {
  if (!session.value) return
  showMiniWindowIfLive()
  classroomLivePanelCompact.value = false
  showClassroomLivePanel.value = false
  clearLastOpenedSidePanel('classroomLive')
  replaceClassroomLiveQuery('floating')
}

function shouldMinimizeTeacherLive() {
  return Boolean(session.value
      && isSessionOpeningTeacher.value
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

function replaceClassroomLiveQuery(mode: 'panel' | 'floating' | null) {
  if (mode === 'panel' && route.query.livePanel === '1' && route.query.liveFloating == null) {
    return
  }
  if (mode === 'floating' && route.query.liveFloating === '1' && route.query.livePanel == null) {
    return
  }
  if (mode == null && route.query.livePanel == null && route.query.liveFloating == null) {
    return
  }
  const query = {...route.query}
  delete query.livePanel
  delete query.liveFloating
  if (mode === 'panel') {
    query.livePanel = '1'
  } else if (mode === 'floating') {
    query.liveFloating = '1'
  }
  void router.replace({
    name: 'class-session-room',
    params: {sessionId: sessionId.value},
    query,
  })
}

function closeSidePanels() {
  closeLivePracticePanel()
  closeChapterPreviewPanel()
  closeSeatedStudentsPanel()
  closeAiSummaryPanel()
  if (showClassroomLivePanel.value && !classroomLivePanelCompact.value) {
    void closeClassroomLivePanel()
  }
}

function closeSidePanelsForSingleWindowMode() {
  if (canUseClassroomFloatingWindows.value) return
  closeSidePanels()
}

function closeExtraSidePanelsForSingleWindowMode() {
  const openPanels = getOpenSidePanels()
  if (openPanels.length <= 1) return

  const currentPanel = lastOpenedSidePanel.value
  const panelToKeep = currentPanel && openPanels.includes(currentPanel)
      ? currentPanel
      : openPanels[openPanels.length - 1]

  openPanels.forEach((panel) => {
    if (panel !== panelToKeep) {
      closeSidePanel(panel)
    }
  })
}

function getOpenSidePanels(): ClassroomSidePanelKey[] {
  const openPanels: ClassroomSidePanelKey[] = []
  if (showLivePracticePanel.value) openPanels.push('livePractice')
  if (showChapterPreviewPanel.value) openPanels.push('chapterPreview')
  if (showSeatedStudentsPanel.value) openPanels.push('seatedStudents')
  if (showAiSummaryPanel.value) openPanels.push('aiSummary')
  if (showClassroomLivePanel.value && !classroomLivePanelCompact.value) openPanels.push('classroomLive')
  return openPanels
}

function closeSidePanel(panel: ClassroomSidePanelKey) {
  if (panel === 'livePractice') closeLivePracticePanel()
  if (panel === 'chapterPreview') closeChapterPreviewPanel()
  if (panel === 'seatedStudents') closeSeatedStudentsPanel()
  if (panel === 'aiSummary') closeAiSummaryPanel()
  if (panel === 'classroomLive') void closeClassroomLivePanel()
}

function clearLastOpenedSidePanel(panel: ClassroomSidePanelKey) {
  if (lastOpenedSidePanel.value === panel) {
    lastOpenedSidePanel.value = null
  }
}

function syncClassroomViewport() {
  classroomViewportWidth.value = window.innerWidth
  hasClassroomCoarsePointer.value = hasCoarsePrimaryPointer()
}

function hasCoarsePrimaryPointer() {
  if (typeof window.matchMedia !== 'function') return false
  return window.matchMedia('(pointer: coarse)').matches
}

function handleParticipantsChange(participants: ClassParticipant[]) {
  seatedParticipants.value = participants
}

function handleLiveStatusChange(message: SeatSyncMessage) {
  if (!session.value || message.sessionId !== session.value.id || message.liveStatus == null) {
    return
  }
  session.value = mergeSeatSyncLiveStatus(session.value, message)
  if (message.liveStatus !== ClassLiveStatus.LIVE && message.liveStatus !== ClassLiveStatus.PAUSED) {
    classroomLivePanelCompact.value = false
    stopLiveSummaryAudioUpload(message.sessionId)
  }
  if (session.value) {
    liveMini.updateSession(session.value)
  }
}

function handleClassroomLoadError() {
  classroomLoadFailed.value = true
}

function handleClassroomProgress(payload: number | { progress: number; label?: string }) {
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
  if (session.value?.courseId) {
    router.push({name: 'course-class-sessions', params: {id: session.value.courseId}})
    return
  }
  router.push('/courses')
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

:deep(.classroom-window-layer) {
  --floating-window-z-index: 2200;
  --floating-window-bg: var(--color-surface-card);
  --floating-window-border: 1px solid var(--color-outline);
  --floating-window-radius: var(--radius-md);
  --floating-window-shadow: var(--shadow-card);
  --floating-window-active-shadow: 0 28px 76px rgba(15, 23, 42, 0.34);
}

:deep(.classroom-floating-window) {
  overflow: visible;
}

:deep(.classroom-floating-window > .chapter-preview-panel),
:deep(.classroom-floating-window > .seated-students-panel),
:deep(.classroom-floating-window > .live-practice-panel),
:deep(.classroom-floating-window > .ai-live-summary-panel),
:deep(.classroom-floating-window > .classroom-live-experience.mode-popup) {
  position: static;
  inset: auto;
  z-index: auto;
  width: 100%;
  height: 100%;
  max-height: none;
  border: 0;
  border-radius: inherit;
  box-shadow: none;
  transform: none;
}

:deep(.classroom-floating-window .panel-header),
:deep(.classroom-floating-window .mode-popup .live-header) {
  cursor: move;
  touch-action: none;
  user-select: none;
}

:deep(.classroom-floating-window.is-dragging .panel-header),
:deep(.classroom-floating-window.is-dragging .mode-popup .live-header) {
  cursor: grabbing;
}

:deep(.live-practice-floating-window .preview-overlay) {
  position: fixed;
  z-index: 2300;
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
