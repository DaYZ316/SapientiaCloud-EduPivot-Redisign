<template>
  <button
      v-if="hiddenEdge"
      :class="`hidden-${hiddenEdge}`"
      :style="restoreButtonStyle"
      :title="t('courseDetail.live.showMiniWindow')"
      class="mini-restore-button"
      type="button"
      @click="restoreFromEdge"
  >
    <ChevronRight
        v-if="hiddenEdge === 'left'"
        :size="18"
        stroke-width="2.3"
    />
    <ChevronLeft
        v-else-if="hiddenEdge === 'right'"
        :size="18"
        stroke-width="2.3"
    />
    <ChevronDown
        v-else-if="hiddenEdge === 'top'"
        :size="18"
        stroke-width="2.3"
    />
    <ChevronUp
        v-else
        :size="18"
        stroke-width="2.3"
    />
  </button>

  <aside
      ref="miniWindowElement"
      :class="{paused: isPaused, dragging: isDragging, hidden: Boolean(hiddenEdge)}"
      :style="miniWindowStyle"
      class="classroom-live-mini"
  >
    <section
        :class="{'screen-share-active': screenShareActive}"
        class="mini-stage"
    >
      <video
          v-if="isTeacher"
          :ref="setLocalVideo"
          autoplay
          class="mini-video"
          muted
          playsinline
      />
      <video
          v-else
          :ref="setRemoteVideo"
          autoplay
          class="mini-video"
          playsinline
      />
      <video
          v-if="showLocalCameraOverlay"
          :ref="setLocalCameraVideo"
          autoplay
          class="mini-camera"
          muted
          playsinline
      />
      <video
          v-if="showRemoteCameraOverlay"
          :ref="setRemoteCameraVideo"
          autoplay
          class="mini-camera"
          playsinline
      />
      <audio
          v-if="!isTeacher"
          :ref="setRemoteAudio"
          autoplay
      />

      <div
          v-if="stageMessage"
          class="mini-state"
      >
        <span>{{ stageMessage }}</span>
      </div>

      <div class="mini-hover-layer">
        <div class="mini-top-actions">
          <button
              class="mini-return-button"
              type="button"
              @click="$emit('expand')"
          >
            <Maximize2
                :size="16"
                stroke-width="2"
            />
            {{ t('courseDetail.live.backToWindow') }}
          </button>

          <button
              :aria-label="t('courseDetail.live.dragMiniWindow')"
              :title="t('courseDetail.live.dragMiniWindow')"
              class="mini-drag-handle"
              type="button"
              @click.prevent
              @pointerdown.stop.prevent="startDragFromHandle"
          >
            <GripHorizontal
                :size="18"
                stroke-width="2.2"
            />
          </button>
        </div>

        <div
            v-if="showTeacherControls"
            :aria-label="t('courseDetail.live.controls')"
            class="mini-controls"
        >
          <button
              :class="{active: live.microphoneEnabled.value}"
              :disabled="mediaControlsDisabled || isLoadingAction('microphone')"
              :title="live.microphoneEnabled.value ? t('courseDetail.live.muteMic') : t('courseDetail.live.enableMic')"
              class="mini-control"
              type="button"
              @click.stop="toggleMicrophone"
          >
            <Mic
                v-if="live.microphoneEnabled.value"
                :size="17"
                stroke-width="2"
            />
            <MicOff
                v-else
                :size="17"
                stroke-width="2"
            />
          </button>

          <button
              :class="{active: live.cameraEnabled.value}"
              :disabled="mediaControlsDisabled || isLoadingAction('camera')"
              :title="live.cameraEnabled.value ? t('courseDetail.live.turnOffCamera') : t('courseDetail.live.turnOnCamera')"
              class="mini-control"
              type="button"
              @click.stop="toggleCamera"
          >
            <Video
                v-if="live.cameraEnabled.value"
                :size="17"
                stroke-width="2"
            />
            <VideoOff
                v-else
                :size="17"
                stroke-width="2"
            />
          </button>

          <button
              :class="{active: live.screenShareEnabled.value}"
              :disabled="mediaControlsDisabled || isLoadingAction('screenShare')"
              :title="live.screenShareEnabled.value ? t('courseDetail.live.stopSharing') : t('courseDetail.live.shareScreen')"
              class="mini-control"
              type="button"
              @click.stop="toggleScreenShare"
          >
            <ScreenShare
                :size="17"
                stroke-width="2"
            />
          </button>

          <button
              :title="t('courseDetail.live.hideMiniWindow')"
              class="mini-control"
              type="button"
              @click.stop="hideToNearestEdge"
          >
            <Minus
                :size="17"
                stroke-width="2"
            />
          </button>

          <button
              :disabled="busy || isLoadingAction('close')"
              :title="t('courseDetail.live.closeMiniWindowAndPause')"
              class="mini-control danger"
              type="button"
              @click.stop="closeAsInterrupted"
          >
            <X
                :size="17"
                stroke-width="2"
            />
          </button>
        </div>
      </div>
    </section>
  </aside>
</template>

<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref, toRef, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ChevronUp,
  GripHorizontal,
  Maximize2,
  Mic,
  MicOff,
  Minus,
  ScreenShare,
  Video,
  VideoOff,
  X,
} from 'lucide-vue-next'

import {ClassLiveStatus, type ClassSession} from '@/features/course/types/classSession'
import {useClassroomLive} from '@/features/classroom/composables/useClassroomLive'
import {
  pauseTeacherLiveBecauseOfUnexpectedDisconnect,
  useTeacherLiveSessionGuard,
} from '@/features/classroom/composables/useTeacherLiveSessionGuard'
import {notify} from '@/shared/composables/useGlobalNotification'

const props = defineProps<{
  session: ClassSession
  isTeacher: boolean
  canParticipate: boolean
}>()

const emit = defineEmits<{
  close: [forceClose?: boolean]
  expand: []
  'session-change': [session: ClassSession]
}>()

const {t} = useI18n()
const live = useClassroomLive(toRef(props, 'session'), toRef(props, 'isTeacher'))
useTeacherLiveSessionGuard({
  session: toRef(props, 'session'),
  isTeacher: toRef(props, 'isTeacher'),
})

type MiniControlAction = 'microphone' | 'camera' | 'screenShare' | 'close'
type HiddenEdge = 'left' | 'right' | 'top' | 'bottom'

const MINI_WINDOW_MARGIN = 12
const busy = ref(false)
const loadingAction = ref<MiniControlAction | null>(null)
const miniWindowElement = ref<HTMLElement | null>(null)
const dragPosition = ref<{ left: number; top: number } | null>(null)
const restorePosition = ref<{ left: number; top: number } | null>(null)
const dragOffset = ref<{ x: number; y: number } | null>(null)
const isDragging = ref(false)
const hiddenEdge = ref<HiddenEdge | null>(null)
let previousDocumentCursor = ''

const isPaused = computed(() => props.session.liveStatus === ClassLiveStatus.PAUSED)
const showTeacherControls = computed(() => props.isTeacher)
const mediaControlsDisabled = computed(() => !props.isTeacher || !live.connected.value || isPaused.value)
const showLocalCameraOverlay = computed(() => props.isTeacher && live.screenShareEnabled.value && live.cameraEnabled.value)
const showRemoteCameraOverlay = computed(() => !props.isTeacher && live.remoteScreenShareVisible.value && live.remoteCameraVisible.value)
const screenShareActive = computed(() => props.isTeacher ? live.screenShareEnabled.value : live.remoteScreenShareVisible.value)
const miniWindowStyle = computed(() => {
  if (!dragPosition.value) {
    return undefined
  }
  return {
    left: `${dragPosition.value.left}px`,
    top: `${dragPosition.value.top}px`,
  }
})
const restoreButtonStyle = computed(() => {
  if (!hiddenEdge.value || !restorePosition.value || !miniWindowElement.value) {
    return undefined
  }
  const rect = miniWindowElement.value.getBoundingClientRect()
  if (hiddenEdge.value === 'left' || hiddenEdge.value === 'right') {
    return {
      top: `${restorePosition.value.top + (rect.height / 2)}px`,
    }
  }
  return {
    left: `${restorePosition.value.left + (rect.width / 2)}px`,
  }
})
const stageMessage = computed(() => {
  if (!props.canParticipate) return props.isTeacher ? '' : t('courseDetail.live.takeSeatWatch')
  if (props.session.liveStatus === ClassLiveStatus.NOT_STARTED) return props.isTeacher ? '' : t('courseDetail.live.waitingTeacher')
  if (isPaused.value) return t('courseDetail.live.streamPaused')
  if (props.session.liveStatus === ClassLiveStatus.ENDED) return t('courseDetail.live.streamEnded')
  if (live.connecting.value) return t('courseDetail.live.connecting')
  if (live.errorMessage.value) return live.errorMessage.value
  if (props.session.liveStatus === ClassLiveStatus.LIVE && !live.hasVideoTrack.value) return t('courseDetail.live.waitingForVideo')
  if (!live.connected.value) return t('courseDetail.live.connectionMaintaining')
  return ''
})

watch(() => props.session.liveStatus, connectWhenNeeded, {immediate: true})
watch(() => props.canParticipate, connectWhenNeeded)

onMounted(connectWhenNeeded)
onBeforeUnmount(stopDrag)

function connectWhenNeeded() {
  if (!props.canParticipate) {
    return
  }
  if (props.session.liveStatus === ClassLiveStatus.LIVE || props.session.liveStatus === ClassLiveStatus.PAUSED) {
    void live.connect()
  }
}

function setLocalVideo(element: unknown) {
  live.localVideoEl.value = element instanceof HTMLVideoElement ? element : null
  live.attachLocalTracks()
}

function setLocalCameraVideo(element: unknown) {
  live.localCameraVideoEl.value = element instanceof HTMLVideoElement ? element : null
  live.attachLocalTracks()
}

function setRemoteVideo(element: unknown) {
  live.remoteVideoEl.value = element instanceof HTMLVideoElement ? element : null
  live.attachRemoteTracks()
}

function setRemoteCameraVideo(element: unknown) {
  live.remoteCameraVideoEl.value = element instanceof HTMLVideoElement ? element : null
  live.attachRemoteTracks()
}

function setRemoteAudio(element: unknown) {
  live.remoteAudioEl.value = element instanceof HTMLAudioElement ? element : null
  live.attachRemoteAudioTracks()
}

function startDragFromHandle(event: PointerEvent) {
  if (event.button !== 0) {
    return
  }
  beginDrag(event)
}

function beginDrag(event: PointerEvent) {
  const element = miniWindowElement.value
  if (!element) {
    return
  }
  const rect = element.getBoundingClientRect()
  hiddenEdge.value = null
  dragOffset.value = {
    x: event.clientX - rect.left,
    y: event.clientY - rect.top,
  }
  dragPosition.value = clampDragPosition(rect.left, rect.top, rect.width, rect.height)
  isDragging.value = true
  previousDocumentCursor = document.documentElement.style.cursor
  document.documentElement.style.cursor = 'grabbing'
  element.setPointerCapture?.(event.pointerId)
  window.addEventListener('pointermove', moveDrag)
  window.addEventListener('pointerup', stopDrag)
}

function moveDrag(event: PointerEvent) {
  const element = miniWindowElement.value
  const offset = dragOffset.value
  if (!element || !offset) {
    return
  }
  const rect = element.getBoundingClientRect()
  dragPosition.value = clampDragPosition(event.clientX - offset.x, event.clientY - offset.y, rect.width, rect.height)
}

function stopDrag() {
  dragOffset.value = null
  isDragging.value = false
  if (typeof document !== 'undefined') {
    document.documentElement.style.cursor = previousDocumentCursor
    previousDocumentCursor = ''
  }
  window.removeEventListener('pointermove', moveDrag)
  window.removeEventListener('pointerup', stopDrag)
}

function clampDragPosition(left: number, top: number, width: number, height: number) {
  const maxLeft = Math.max(MINI_WINDOW_MARGIN, window.innerWidth - width - MINI_WINDOW_MARGIN)
  const maxTop = Math.max(MINI_WINDOW_MARGIN, window.innerHeight - height - MINI_WINDOW_MARGIN)
  return {
    left: Math.min(Math.max(left, MINI_WINDOW_MARGIN), maxLeft),
    top: Math.min(Math.max(top, MINI_WINDOW_MARGIN), maxTop),
  }
}

async function toggleMicrophone() {
  await runControlAction('microphone', live.toggleMicrophone)
}

async function toggleCamera() {
  await runControlAction('camera', live.toggleCamera)
}

async function toggleScreenShare() {
  await runControlAction('screenShare', live.toggleScreenShare)
}

function hideToNearestEdge() {
  const element = miniWindowElement.value
  if (!element) {
    return
  }
  const rect = element.getBoundingClientRect()
  const current = clampDragPosition(rect.left, rect.top, rect.width, rect.height)
  const nearestEdge = [
    {edge: 'left', distance: rect.left},
    {edge: 'right', distance: window.innerWidth - rect.right},
    {edge: 'top', distance: rect.top},
    {edge: 'bottom', distance: window.innerHeight - rect.bottom},
  ].reduce((nearest, currentEdge) => currentEdge.distance < nearest.distance ? currentEdge : nearest) as {
    edge: HiddenEdge
    distance: number
  }

  restorePosition.value = current
  hiddenEdge.value = nearestEdge.edge
  if (nearestEdge.edge === 'left') {
    dragPosition.value = {left: -rect.width - MINI_WINDOW_MARGIN, top: current.top}
  } else if (nearestEdge.edge === 'right') {
    dragPosition.value = {left: window.innerWidth + MINI_WINDOW_MARGIN, top: current.top}
  } else if (nearestEdge.edge === 'top') {
    dragPosition.value = {left: current.left, top: -rect.height - MINI_WINDOW_MARGIN}
  } else {
    dragPosition.value = {left: current.left, top: window.innerHeight + MINI_WINDOW_MARGIN}
  }
}

function restoreFromEdge() {
  hiddenEdge.value = null
  if (restorePosition.value) {
    dragPosition.value = restorePosition.value
  }
}

async function closeAsInterrupted() {
  await runControlAction('close', async () => {
    await live.disconnect()
    const pausedSession = await pauseTeacherLiveBecauseOfUnexpectedDisconnect(props.session.id)
    if (pausedSession) {
      emit('session-change', pausedSession)
    }
    emit('close', true)
  })
}

function isLoadingAction(action: MiniControlAction) {
  return loadingAction.value === action
}

async function runControlAction(action: MiniControlAction, task: () => Promise<void>) {
  if (loadingAction.value) {
    return
  }
  loadingAction.value = action
  try {
    await task()
  } catch {
    notify.error(t('courseDetail.live.controlActionFailed'))
  } finally {
    loadingAction.value = null
  }
}

</script>

<style scoped>
.classroom-live-mini {
  position: fixed;
  z-index: 1900;
  top: var(--space-md);
  right: var(--space-md);
  overflow: hidden;
  width: min(360px, calc(100vw - (var(--space-md) * 2)));
  border: 1px solid color-mix(in srgb, var(--color-outline-light) 72%, transparent);
  border-radius: var(--radius-lg);
  background: var(--color-surface-card);
  box-shadow: var(--shadow-card);
  touch-action: none;
  transition: box-shadow 0.18s ease, left 0.22s ease, top 0.22s ease;
  user-select: none;
}

.classroom-live-mini.dragging {
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.28);
  cursor: grabbing;
  transition: box-shadow 0.18s ease !important;
}

.classroom-live-mini.hidden {
  transform: none;
}

.mini-restore-button {
  position: fixed;
  z-index: 1901;
  display: inline-grid;
  width: 36px;
  height: 36px;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, 0.32);
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.72);
  color: #fff;
  cursor: pointer;
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.32);
}

.mini-restore-button.hidden-left {
  left: 0;
  transform: translateY(-50%);
}

.mini-restore-button.hidden-right {
  right: 0;
  transform: translateY(-50%);
}

.mini-restore-button.hidden-top {
  top: 0;
  transform: translateX(-50%);
}

.mini-restore-button.hidden-bottom {
  bottom: 0;
  transform: translateX(-50%);
}

.mini-stage {
  position: relative;
  overflow: hidden;
  aspect-ratio: 16 / 9;
  background: linear-gradient(rgba(0, 0, 0, 0.28), rgba(0, 0, 0, 0.28)),
  var(--color-surface-container-high);
}

.mini-video {
  position: relative;
  z-index: 1;
  width: 100%;
  height: 100%;
  background: #111;
  object-fit: cover;
}

.mini-stage.screen-share-active .mini-video {
  object-fit: contain;
}

.mini-camera {
  position: absolute;
  z-index: 3;
  right: 12px;
  bottom: 12px;
  width: 92px;
  aspect-ratio: 16 / 10;
  border: 1px solid rgba(255, 255, 255, 0.36);
  border-radius: var(--radius-sm);
  background: #111;
  box-shadow: 0 12px 26px rgba(0, 0, 0, 0.24);
  object-fit: cover;
}

.mini-state {
  position: absolute;
  z-index: 4;
  inset: 0;
  display: grid;
  gap: 8px;
  place-content: center;
  background: rgba(0, 0, 0, 0.34);
  color: #fff;
  font-size: 13px;
  text-align: center;
}

.mini-hover-layer {
  position: absolute;
  z-index: 5;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 10px;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.48), transparent 38%, rgba(0, 0, 0, 0.62));
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.18s ease;
}

.mini-top-actions {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.classroom-live-mini:hover .mini-hover-layer,
.classroom-live-mini:focus-within .mini-hover-layer {
  opacity: 1;
  pointer-events: auto;
}

.mini-return-button {
  display: inline-flex;
  width: fit-content;
  align-items: center;
  gap: 6px;
  padding: 7px 10px;
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-radius: var(--radius-sm);
  background: rgba(0, 0, 0, 0.52);
  color: #fff;
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
}

.mini-drag-handle {
  display: inline-grid;
  width: 36px;
  height: 30px;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-radius: var(--radius-sm);
  background: rgba(0, 0, 0, 0.52);
  color: #fff;
  cursor: grab;
}

.mini-drag-handle:hover,
.mini-drag-handle:focus-visible {
  background: rgba(255, 255, 255, 0.18);
}

.classroom-live-mini.dragging .mini-drag-handle {
  background: #fff;
  color: #111;
  cursor: grabbing;
}

.mini-controls {
  display: flex;
  align-self: center;
  gap: 8px;
  padding: 7px;
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.58);
  backdrop-filter: blur(10px);
}

.mini-control {
  display: inline-grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
  cursor: pointer;
  transition: background 0.18s ease, transform 0.18s ease;
}

.mini-control:hover:not(:disabled),
.mini-control.active {
  background: #fff;
  color: #111;
}

.mini-control.danger {
  color: #fca5a5;
}

.mini-control.danger:hover:not(:disabled) {
  border-color: #ef4444;
  background: #ef4444;
  color: #fff;
}

.mini-control:active:not(:disabled) {
  transform: scale(0.94);
}

.mini-control:disabled {
  cursor: not-allowed;
  opacity: 0.46;
}

@media (max-width: 520px) {
  .classroom-live-mini {
    top: var(--space-sm);
    right: var(--space-sm);
    width: calc(100vw - (var(--space-sm) * 2));
  }

  .mini-controls {
    gap: 6px;
  }
}
</style>
