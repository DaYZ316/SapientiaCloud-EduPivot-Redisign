<template>
  <ClassroomLiveMiniWindow
      v-if="liveMini.active && liveMini.session"
      :can-participate="liveMini.canParticipate"
      :is-teacher="liveMini.isTeacher"
      :session="liveMini.session"
      @close="handleClose"
      @expand="returnToLivePage"
      @open-summary="returnToSummaryPanel"
      @session-change="liveMini.updateSession"
  />
</template>

<script lang="ts" setup>
import {onMounted, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'

import ClassroomLiveMiniWindow from '@/features/classroom/components/ClassroomLiveMiniWindow.vue'
import {stopLiveSummaryAudioUpload} from '@/features/classroom/composables/useClassroomLiveSummaryAudio'
import {useClassroomLiveMiniStore} from '@/features/classroom/stores/classroomLiveMini'
import {getClassSession} from '@/features/course/api/classSession'
import {ClassLiveStatus} from '@/features/course/types/classSession'
import {useAuthStore} from '@/features/auth/stores/auth'

const OPEN_CLASSROOM_LIVE_PANEL_EVENT = 'edupivot:open-classroom-live-panel'
const OPEN_AI_SUMMARY_PANEL_EVENT = 'edupivot:open-ai-summary-panel'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const liveMini = useClassroomLiveMiniStore()
let restoring = false

onMounted(() => {
  void restoreMiniWindow()
})

watch(() => liveMini.restoreSessionId, () => {
  void restoreMiniWindow()
})

function handleClose() {
  liveMini.clear()
}

function returnToLivePage() {
  const sessionId = liveMini.session?.id
  if (!sessionId) {
    return
  }
  liveMini.clear()
  if (route.name === 'class-session-room' && route.params.sessionId === sessionId) {
    window.dispatchEvent(new CustomEvent(OPEN_CLASSROOM_LIVE_PANEL_EVENT, {
      detail: {sessionId},
    }))
    return
  }
  void router.push({
    name: 'class-session-room',
    params: {sessionId},
    query: {livePanel: '1'},
  })
}

function returnToSummaryPanel() {
  const sessionId = liveMini.session?.id
  if (!sessionId) {
    return
  }
  liveMini.clear()
  if (route.name === 'class-session-room' && route.params.sessionId === sessionId) {
    window.dispatchEvent(new CustomEvent(OPEN_AI_SUMMARY_PANEL_EVENT, {
      detail: {sessionId},
    }))
    return
  }
  void router.push({
    name: 'class-session-room',
    params: {sessionId},
    query: {summaryPanel: '1'},
  })
}

async function restoreMiniWindow() {
  const sessionId = liveMini.restoreSessionId
  if (!sessionId || liveMini.session || restoring) {
    return
  }
  if (route.name === 'class-session-live' && route.params.sessionId === sessionId) {
    return
  }
  restoring = true
  try {
    const session = await getClassSession(sessionId)
    const isOpeningTeacher = Boolean(authStore.user?.id && session.teacherId === authStore.user.id)
    if (!isOpeningTeacher || session.liveStatus !== ClassLiveStatus.LIVE) {
      stopLiveSummaryAudioUpload(sessionId)
      liveMini.clearSession(sessionId)
      return
    }
    liveMini.show({
      session,
      isTeacher: true,
      canParticipate: true,
    })
  } catch {
    stopLiveSummaryAudioUpload(sessionId)
    liveMini.clearSession(sessionId)
  } finally {
    restoring = false
  }
}
</script>
