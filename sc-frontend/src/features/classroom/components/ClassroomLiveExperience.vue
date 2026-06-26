<template>
  <aside class="classroom-live-experience" :class="modeClass">
    <header class="live-header">
      <button
        v-if="isFullscreenLayout"
        class="header-icon-button back-button"
        type="button"
        :title="t('courseDetail.live.backToWindow')"
        @click="handleBack"
      >
        <ArrowLeft :size="24" stroke-width="2" />
        <span class="header-back-label">{{ t('courseDetail.live.back') }}</span>
      </button>

      <div class="live-title-block">
        <h2 :title="headerTitle">
          <span class="title-marquee">
            <span>{{ headerTitle }}</span>
            <span aria-hidden="true">{{ headerTitle }}</span>
          </span>
        </h2>
        <div class="live-meta-row">
          <span class="live-status" :class="{active: isLive}">
            <span />
            {{ liveStatusLabel }}
          </span>
          <span class="meta-divider" />
          <span class="live-stat">
            <Eye v-if="!isFullscreenLayout" :size="15" stroke-width="2" />
            {{ audienceLabel }}
          </span>
          <span class="meta-divider" />
          <span>{{ latencyLabel }}</span>
        </div>
      </div>

      <div v-if="!isFullscreenLayout" class="header-actions">
        <button
          v-if="showTeacherControls"
          class="header-icon-button controls-header-toggle"
          type="button"
          :aria-expanded="!controlsCollapsed"
          :title="controlsPanelToggleTitle"
          @click="toggleControlsPanel"
        >
          <ChevronUp v-if="controlsCollapsed" :size="20" stroke-width="2" />
          <ChevronDown v-else :size="20" stroke-width="2" />
        </button>
        <button
          class="header-icon-button"
          type="button"
          :title="t('courseDetail.live.expandLive')"
          @click="expandInPage"
        >
          <Maximize2 :size="22" stroke-width="2" />
        </button>
        <button
          class="header-icon-button"
          type="button"
          :title="t('courseDetail.live.closeWindow')"
          @click="$emit('close')"
        >
          <X :size="22" stroke-width="2" />
        </button>
      </div>
    </header>

    <div class="live-layout">
      <main class="live-main-column">
        <section ref="stageElement" class="live-stage" :class="{paused: isPaused, 'screen-share-active': screenShareActive, loading: showStageLoading}">
          <video
            v-if="isTeacher"
            :ref="setLocalVideo"
            autoplay
            class="live-video"
            muted
            playsinline
          />
          <video
            v-else
            :ref="setRemoteVideo"
            autoplay
            class="live-video"
            playsinline
          />
          <video
            v-if="showLocalCameraOverlay"
            :ref="setLocalCameraVideo"
            autoplay
            class="camera-overlay"
            :class="cameraOverlayClass"
            muted
            playsinline
          />
          <video
            v-if="showRemoteCameraOverlay"
            :ref="setRemoteCameraVideo"
            autoplay
            class="camera-overlay"
            :class="cameraOverlayClass"
            playsinline
          />
          <audio v-if="!isTeacher" :ref="setRemoteAudio" autoplay />

          <div v-if="stageMessage" class="stage-overlay">
            <PlayCircle :size="isFullscreenLayout ? 54 : 44" stroke-width="1.7" />
            <span>{{ stageMessage }}</span>
          </div>

          <div v-if="showStageLoading" class="live-stage-loading" aria-hidden="true">
            <div class="stage-loading-frame">
              <span class="live-skeleton stage-loading-title"></span>
              <span class="live-skeleton stage-loading-line wide"></span>
              <span class="live-skeleton stage-loading-line"></span>
            </div>
            <div class="stage-loading-rail">
              <span v-for="item in 4" :key="item" class="live-skeleton stage-loading-pill"></span>
            </div>
          </div>

          <div class="danmaku-layer" aria-live="polite">
            <span
              v-for="item in danmakuItems"
              :key="item.id"
              class="danmaku-item"
              :style="{
                '--danmaku-lane': item.lane,
                '--danmaku-duration': `${item.duration}s`,
              }"
            >
              {{ item.text }}
            </span>
          </div>

          <div class="stage-hover-layer">
            <span>{{ t('courseDetail.live.renderView') }}</span>
            <button
              class="stage-expand-button"
              type="button"
              :title="isPlayerFullscreen ? t('courseDetail.live.exitPlayerFullscreen') : t('courseDetail.live.enterPlayerFullscreen')"
              :aria-label="isPlayerFullscreen ? t('courseDetail.live.exitPlayerFullscreen') : t('courseDetail.live.enterPlayerFullscreen')"
              @click.stop="togglePlayerFullscreen"
            >
              <Minimize2 v-if="isPlayerFullscreen" :size="18" stroke-width="2" />
              <Maximize2 v-else :size="18" stroke-width="2" />
            </button>
          </div>

          <form class="player-danmaku-form" @submit.prevent="submitMessage">
            <input
              v-model.trim="draft"
              :disabled="!canChat || sending"
              maxlength="300"
              :placeholder="t('courseDetail.live.fullscreenMessagePlaceholder')"
            >
            <button type="submit" :disabled="!canChat || sending || !draft" :aria-label="t('courseDetail.live.sendMessage')">
              <Send :size="18" stroke-width="2" />
            </button>
          </form>
        </section>

        <section v-if="showCameraPositionControls" class="overlay-position-controls">
          <span class="overlay-position-label">{{ t('courseDetail.live.cameraPosition') }}</span>
          <div class="position-options" role="group" :aria-label="t('courseDetail.live.cameraPositionGroup')">
            <button
              v-for="option in overlayPositionOptions"
              :key="option.value"
              class="position-option"
              :class="{active: live.cameraOverlayPosition.value === option.value}"
              type="button"
              :disabled="!live.connected.value"
              @click="chooseCameraOverlayPosition(option.value)"
            >
              {{ t(option.labelKey) }}
            </button>
          </div>
        </section>

        <p v-if="!isTeacher && !canParticipate" class="hint">
          {{ t('courseDetail.live.takeSeatHint') }}
        </p>

        <section v-if="!isFullscreenLayout" class="chat-panel">
          <h3>{{ t('courseDetail.live.discussion') }}</h3>
          <div class="chat-list">
            <div v-if="messagesLoading && messages.length === 0" class="chat-loading-list" :aria-label="t('courseDetail.live.loadingDiscussion')" aria-live="polite">
              <article v-for="item in 4" :key="item" class="chat-loading-row" aria-hidden="true">
                <span class="live-skeleton chat-loading-avatar"></span>
                <span class="chat-loading-copy">
                  <span class="live-skeleton chat-loading-name"></span>
                  <span class="live-skeleton chat-loading-text"></span>
                </span>
              </article>
            </div>
            <p v-else-if="messages.length === 0" class="empty">{{ t('courseDetail.live.noMessages') }}</p>
            <article v-for="message in messages" :key="message.id" class="chat-message">
              <UserAvatarLink
                :avatar-url="message.senderAvatarUrl"
                :display-name="senderName(message)"
                :linkable="false"
                :show-name="false"
                :user-id="message.senderId"
                class="chat-avatar-link"
                size="small"
              />
              <div class="chat-body">
                <div class="chat-meta">
                  <div class="sender-line">
                    <strong>{{ senderName(message) }}</strong>
                    <span class="sender-role-tag">{{ senderRoleLabel(message) }}</span>
                  </div>
                  <span>{{ formatTime(message.sentAt) }}</span>
                </div>
                <p>{{ message.content }}</p>
              </div>
            </article>
          </div>
          <form class="chat-form" @submit.prevent="submitMessage">
            <input
              v-model.trim="draft"
              :disabled="!canChat || sending"
              maxlength="300"
              :placeholder="t('courseDetail.live.messagePlaceholder')"
            >
            <button type="submit" :disabled="!canChat || sending || !draft" :aria-label="t('courseDetail.live.sendMessage')">
              <Send :size="18" stroke-width="2" />
            </button>
          </form>
        </section>
      </main>

      <aside v-if="isFullscreenLayout" class="live-side-panel">
        <div class="side-tabs">
          <button
            class="side-tab"
            :class="{active: activeSideTab === 'chat'}"
            type="button"
            @click="activeSideTab = 'chat'"
          >
            {{ t('courseDetail.live.chatTab') }}
          </button>
          <button
            class="side-tab"
            :class="{active: activeSideTab === 'notes'}"
            type="button"
            @click="activeSideTab = 'notes'"
          >
            {{ t('courseDetail.live.notesTab') }}
          </button>
          <button
            class="side-tab"
            :class="{active: activeSideTab === 'online'}"
            type="button"
            @click="activeSideTab = 'online'"
          >
            {{ t('courseDetail.live.onlineTab') }}
          </button>
        </div>

        <template v-if="activeSideTab === 'chat'">
          <section class="ai-summary-card sidebar-summary">
            <header>
              <Sparkles :size="22" stroke-width="1.8" />
              <h3>{{ t('courseDetail.live.aiSummaryTitle') }}</h3>
            </header>
            <p>{{ summaryText }}</p>
          </section>

          <div class="chat-list sidebar-chat-list">
            <div v-if="messagesLoading && messages.length === 0" class="chat-loading-list" :aria-label="t('courseDetail.live.loadingDiscussion')" aria-live="polite">
              <article v-for="item in 5" :key="item" class="chat-loading-row sidebar-loading-row" aria-hidden="true">
                <span class="live-skeleton chat-loading-avatar"></span>
                <span class="chat-loading-copy">
                  <span class="live-skeleton chat-loading-name"></span>
                  <span class="live-skeleton chat-loading-text"></span>
                </span>
              </article>
            </div>
            <p v-else-if="messages.length === 0" class="empty">{{ t('courseDetail.live.noMessages') }}</p>
            <article v-for="message in messages" :key="message.id" class="chat-message sidebar-message">
              <UserAvatarLink
                :avatar-url="message.senderAvatarUrl"
                :display-name="senderName(message)"
                :linkable="false"
                :show-name="false"
                :user-id="message.senderId"
                class="chat-avatar-link"
                size="small"
              />
              <div class="chat-body">
                <div class="chat-meta">
                  <div class="sender-line">
                    <strong>{{ senderName(message) }}</strong>
                    <span class="sender-role-tag">{{ senderRoleLabel(message) }}</span>
                  </div>
                  <span>{{ formatTime(message.sentAt) }}</span>
                </div>
                <p>{{ message.content }}</p>
              </div>
            </article>
          </div>

          <form class="chat-form sidebar-chat-form" @submit.prevent="submitMessage">
            <input
              v-model.trim="draft"
              :disabled="!canChat || sending"
              maxlength="300"
              :placeholder="t('courseDetail.live.fullscreenMessagePlaceholder')"
            >
            <button type="submit" :disabled="!canChat || sending || !draft" :aria-label="t('courseDetail.live.sendMessage')">
              <Send :size="18" stroke-width="2" />
            </button>
          </form>
        </template>

        <section v-else-if="activeSideTab === 'notes'" class="notes-panel">
          <h3>{{ t('courseDetail.live.classNotes') }}</h3>
          <p>{{ session.description || t('courseDetail.live.noNotes') }}</p>
          <dl>
            <div>
              <dt>{{ t('courseDetail.live.liveStatus') }}</dt>
              <dd>{{ session.liveStatusText || liveStatusLabel }}</dd>
            </div>
            <div>
              <dt>{{ t('courseDetail.live.starts') }}</dt>
              <dd>{{ formatSessionTime(session.scheduledStartAt) }}</dd>
            </div>
            <div>
              <dt>{{ t('courseDetail.live.ends') }}</dt>
              <dd>{{ formatSessionTime(session.scheduledEndAt) }}</dd>
            </div>
          </dl>
        </section>

        <section v-else class="online-panel">
          <header>
            <h3>{{ t('courseDetail.live.onlineTab') }}</h3>
            <span>{{ live.onlineCount.value }}</span>
          </header>
          <div class="online-list">
            <div v-if="participantsLoading && live.onlineParticipants.value.length === 0" class="online-loading-list" :aria-label="t('courseDetail.live.loadingOnline')" aria-live="polite">
              <article v-for="item in 4" :key="item" class="online-loading-row" aria-hidden="true">
                <span class="live-skeleton chat-loading-avatar"></span>
                <span class="chat-loading-copy">
                  <span class="live-skeleton chat-loading-name"></span>
                  <span class="live-skeleton chat-loading-text short"></span>
                </span>
                <span class="live-skeleton online-loading-chip"></span>
              </article>
            </div>
            <p v-else-if="live.onlineParticipants.value.length === 0" class="empty">{{ t('courseDetail.live.noOnline') }}</p>
            <article
              v-for="participant in live.onlineParticipants.value"
              :key="participant.identity"
              class="online-row"
            >
              <UserAvatarLink
                :avatar-url="participant.avatarUrl"
                :display-name="participant.displayName"
                :linkable="false"
                :show-name="false"
                :user-id="participant.userId"
                class="online-avatar-link"
                size="small"
              />
              <div class="online-body">
                <strong>{{ participant.displayName }}</strong>
                <span>{{ onlineParticipantMeta(participant) }}</span>
              </div>
              <span class="quality-chip" :class="qualityClass(participant.connectionQuality)">
                {{ qualityLabel(participant.connectionQuality) }}
              </span>
            </article>
          </div>
        </section>
      </aside>
    </div>

    <button
      v-if="showTeacherControls && isFullscreenLayout"
      class="dock-collapse-button"
      :class="{collapsed: controlsCollapsed}"
      type="button"
      :aria-expanded="!controlsCollapsed"
      :title="controlsPanelToggleTitle"
      @click="toggleControlsPanel"
    >
      <ChevronUp v-if="controlsCollapsed" :size="18" stroke-width="2" />
      <ChevronDown v-else :size="18" stroke-width="2" />
    </button>

    <nav
      v-if="showTeacherControls"
      class="live-bottom-dock"
      :class="{collapsed: controlsCollapsed}"
      :aria-hidden="controlsCollapsed"
      :inert="controlsCollapsed"
      :aria-label="t('courseDetail.live.controls')"
    >
      <button
        class="dock-button"
        :class="{active: live.microphoneEnabled.value}"
        type="button"
        :disabled="mediaControlsDisabled || isLoadingAction('microphone')"
        :title="live.microphoneEnabled.value ? t('courseDetail.live.muteMic') : t('courseDetail.live.enableMic')"
        @click="toggleMicrophone"
      >
        <span v-if="isLoadingAction('microphone')" class="button-spinner" aria-hidden="true" />
        <Mic v-else-if="live.microphoneEnabled.value" :size="22" stroke-width="2" />
        <MicOff v-else :size="22" stroke-width="2" />
      </button>

      <button
        class="dock-button"
        :class="{active: live.cameraEnabled.value}"
        type="button"
        :disabled="mediaControlsDisabled || isLoadingAction('camera')"
        :title="live.cameraEnabled.value ? t('courseDetail.live.turnOffCamera') : t('courseDetail.live.turnOnCamera')"
        @click="toggleCamera"
      >
        <span v-if="isLoadingAction('camera')" class="button-spinner" aria-hidden="true" />
        <Video v-else-if="live.cameraEnabled.value" :size="22" stroke-width="2" />
        <VideoOff v-else :size="22" stroke-width="2" />
      </button>

      <span class="dock-divider" />

      <button
        class="dock-button"
        :class="{active: live.screenShareEnabled.value}"
        type="button"
        :disabled="mediaControlsDisabled || isLoadingAction('screenShare')"
        :title="live.screenShareEnabled.value ? t('courseDetail.live.stopSharing') : t('courseDetail.live.shareScreen')"
        @click="toggleScreenShare"
      >
        <span v-if="isLoadingAction('screenShare')" class="button-spinner" aria-hidden="true" />
        <ScreenShare v-else :size="22" stroke-width="2" />
      </button>

      <button
        v-if="isTeacher && canStart"
        class="dock-button call-button"
        type="button"
        :disabled="busy || isLoadingAction('start')"
        :title="startButtonText"
        @click="startLive"
      >
        <span v-if="isLoadingAction('start')" class="button-spinner" aria-hidden="true" />
        <Phone v-else :size="22" stroke-width="2" />
      </button>
      <button
        v-else-if="isTeacher && isLive"
        class="dock-button"
        type="button"
        :disabled="busy || isLoadingAction('pause')"
        :title="t('courseDetail.live.pauseLive')"
        @click="pauseLive"
      >
        <span v-if="isLoadingAction('pause')" class="button-spinner" aria-hidden="true" />
        <Pause v-else :size="22" stroke-width="2" />
      </button>
      <button
        v-else-if="isTeacher && isPaused"
        class="dock-button"
        type="button"
        :disabled="busy || isLoadingAction('resume')"
        :title="t('courseDetail.live.resumeLive')"
        @click="resumeLive"
      >
        <span v-if="isLoadingAction('resume')" class="button-spinner" aria-hidden="true" />
        <Play v-else :size="22" stroke-width="2" />
      </button>
      <button v-else class="dock-button" type="button" disabled :title="t('courseDetail.live.raiseHand')">
        <Hand :size="22" stroke-width="2" />
      </button>

      <span class="dock-divider" />

      <button
        class="dock-button stop-live-button"
        :class="{danger: canStop}"
        type="button"
        :disabled="busy || !canStop || isLoadingAction('stop')"
        :title="stopButtonTitle"
        @click="endLive"
      >
        <span v-if="isLoadingAction('stop')" class="button-spinner" aria-hidden="true" />
        <PhoneOff v-else :size="22" stroke-width="2" />
        <span v-if="isFullscreenLayout">{{ t('courseDetail.live.end') }}</span>
      </button>
    </nav>
  </aside>
</template>

<script setup lang="ts">
import {computed, onMounted, onUnmounted, ref, toRef, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {
    ArrowLeft,
    ChevronDown,
    ChevronUp,
    Eye,
    Hand,
    Maximize2,
    Minimize2,
    Mic,
    MicOff,
    Pause,
    Phone,
    PhoneOff,
    Play,
    PlayCircle,
    ScreenShare,
    Send,
    Sparkles,
    Video,
    VideoOff,
    X,
} from 'lucide-vue-next'

import {
    listClassBarrages,
    listClassSessionParticipants,
    pauseClassLive,
    resumeClassLive,
    sendClassBarrage,
    startClassLive,
    stopClassLive,
    subscribeClassBarrages,
} from '@/features/course/api/classSession'
import {
    ClassLiveStatus,
    ClassSessionStatus,
    type ClassBarrage,
    type ClassParticipant,
    type ClassSession,
} from '@/features/course/types/classSession'
import {type CameraOverlayPosition, useClassroomLive} from '@/features/classroom/composables/useClassroomLive'
import {
    TEACHER_LIVE_UNEXPECTED_PAUSED_EVENT,
    useTeacherLiveSessionGuard,
} from '@/features/classroom/composables/useTeacherLiveSessionGuard'
import type {LiveOnlineParticipant} from '@/features/classroom/composables/livePresence'
import UserAvatarLink from '@/shared/components/UserAvatarLink.vue'
import {notify} from '@/shared/composables/useGlobalNotification'

const LIVE_CONTROLS_COLLAPSED_STORAGE_KEY = 'edupivot.classroom-live.controls-collapsed'
const DANMAKU_LANE_COUNT = 6
const MAX_DANMAKU_ITEMS = 14

const props = withDefaults(defineProps<{
    session: ClassSession
    isTeacher: boolean
    canParticipate: boolean
    mode?: 'popup' | 'fullscreen'
}>(), {
    mode: 'popup',
})

const emit = defineEmits<{
    close: []
    'session-change': [session: ClassSession]
}>()

const {t, locale} = useI18n()
const live = useClassroomLive(toRef(props, 'session'), toRef(props, 'isTeacher'))
useTeacherLiveSessionGuard({
    session: toRef(props, 'session'),
    isTeacher: toRef(props, 'isTeacher'),
})
const activeSideTab = ref<'chat' | 'notes' | 'online'>('chat')
const messages = ref<ClassBarrage[]>([])
const liveDanmakuMessages = ref<ClassBarrage[]>([])
const participantDirectory = ref<ClassParticipant[]>([])
const stageElement = ref<HTMLElement | null>(null)
const draft = ref('')
const busy = ref(false)
const sending = ref(false)
const expanded = ref(false)
const controlsCollapsed = ref(readControlsCollapsedPreference())
const loadingAction = ref<LiveControlAction | null>(null)
const messagesLoading = ref(false)
const participantsLoading = ref(false)
const isPlayerFullscreen = ref(false)
const overlayPositionOptions: {labelKey: string; value: CameraOverlayPosition}[] = [
    {labelKey: 'courseDetail.live.overlayTopLeft', value: 'top-left'},
    {labelKey: 'courseDetail.live.overlayTopRight', value: 'top-right'},
    {labelKey: 'courseDetail.live.overlayBottomLeft', value: 'bottom-left'},
    {labelKey: 'courseDetail.live.overlayBottomRight', value: 'bottom-right'},
]
let chatSubscription: {close: () => void} | null = null
type LiveControlAction = 'microphone' | 'camera' | 'screenShare' | 'start' | 'pause' | 'resume' | 'stop'
type DanmakuItem = {
    id: string
    text: string
    lane: number
    duration: number
}

const isFullscreenLayout = computed(() => props.mode === 'fullscreen' || expanded.value)
const modeClass = computed(() => [
    isFullscreenLayout.value ? 'mode-fullscreen' : 'mode-popup',
    expanded.value ? 'mode-expanded-inline' : '',
    props.isTeacher ? '' : 'controls-hidden',
])
const isNotStarted = computed(() => props.session.liveStatus === ClassLiveStatus.NOT_STARTED)
const isLive = computed(() => props.session.liveStatus === ClassLiveStatus.LIVE)
const isPaused = computed(() => props.session.liveStatus === ClassLiveStatus.PAUSED)
const isEnded = computed(() => props.session.liveStatus === ClassLiveStatus.ENDED)
const isClassOngoing = computed(() => props.session.status === ClassSessionStatus.LIVE)
const canStart = computed(() => (isNotStarted.value || isEnded.value) && isClassOngoing.value)
const canStop = computed(() => isLive.value || isPaused.value)
const canChat = computed(() => props.session.status === ClassSessionStatus.LIVE && props.canParticipate)
const mediaControlsDisabled = computed(() => !props.isTeacher || !live.connected.value || isPaused.value)
const showLocalCameraOverlay = computed(() => props.isTeacher && live.screenShareEnabled.value && live.cameraEnabled.value)
const showRemoteCameraOverlay = computed(() => !props.isTeacher && live.remoteScreenShareVisible.value && live.remoteCameraVisible.value)
const showCameraPositionControls = computed(() => props.isTeacher && showLocalCameraOverlay.value)
const cameraOverlayClass = computed(() => `position-${live.cameraOverlayPosition.value}`)
const screenShareActive = computed(() => props.isTeacher ? live.screenShareEnabled.value : live.remoteScreenShareVisible.value)
const headerTitle = computed(() => props.session.title)
const showTeacherControls = computed(() => props.isTeacher)
const startButtonText = computed(() => isEnded.value ? t('courseDetail.live.restartLive') : t('courseDetail.live.startLive'))
const stopButtonTitle = computed(() => canStop.value ? t('courseDetail.live.endLiveStream') : t('courseDetail.live.streamNotRunning'))
const controlsPanelToggleTitle = computed(() => controlsCollapsed.value ? t('courseDetail.live.showControls') : t('courseDetail.live.hideControls'))
const liveStatusLabel = computed(() => {
    if (isFullscreenLayout.value) {
        if (isLive.value) return t('courseDetail.live.statusLiveShort')
        if (isPaused.value) return t('courseDetail.live.statusPausedShort')
        if (isEnded.value) return t('courseDetail.live.statusEndedShort')
        return t('courseDetail.live.statusReadyShort')
    }
    if (isLive.value) return t('courseDetail.live.statusLive')
    if (isPaused.value) return t('courseDetail.live.statusPaused')
    if (isEnded.value) return t('courseDetail.live.statusEnded')
    return t('courseDetail.live.statusReady')
})
const audienceLabel = computed(() => isFullscreenLayout.value
    ? t('courseDetail.live.onlineCount', {count: live.onlineCount.value})
    : String(live.onlineCount.value))
const latencyLabel = computed(() => formatLiveLatencyLabel())
const showStageLoading = computed(() => Boolean(props.canParticipate && live.connecting.value))
const danmakuItems = computed<DanmakuItem[]>(() =>
    liveDanmakuMessages.value.map((message, index) => ({
        id: message.id,
        text: `${senderName(message)}: ${message.content}`,
        lane: index % DANMAKU_LANE_COUNT,
        duration: 11 + (index % 4),
    })),
)
const summaryText = computed(() => {
    if (props.session.description) {
        return props.session.description
    }
    if (isFullscreenLayout.value) {
        return t('courseDetail.live.summaryFallbackFullscreen')
    }
    return t('courseDetail.live.summaryFallback')
})
const stageMessage = computed(() => {
    if (!props.canParticipate) return props.isTeacher ? '' : t('courseDetail.live.takeSeatWatch')
    if (isPaused.value) return t('courseDetail.live.streamPaused')
    if (isEnded.value && props.isTeacher && isClassOngoing.value) return t('courseDetail.live.streamEndedRestart')
    if (isEnded.value) return t('courseDetail.live.streamEnded')
    if (isNotStarted.value) return props.isTeacher ? t('courseDetail.live.pressPlayStart') : t('courseDetail.live.waitingTeacher')
    if (live.connecting.value) return t('courseDetail.live.connecting')
    if (live.errorMessage.value) return live.errorMessage.value
    if (isLive.value && !live.hasVideoTrack.value) return t('courseDetail.live.waitingForVideo')
    return ''
})

watch(() => props.session.liveStatus, handleLiveStatus, {immediate: true})
watch(() => props.canParticipate, (canParticipate) => {
    if (!canParticipate) {
        void live.disconnect()
        return
    }
    void loadParticipants()
    handleLiveStatus(props.session.liveStatus)
})
watch(() => props.session.id, () => {
    participantDirectory.value = []
    liveDanmakuMessages.value = []
    live.setSessionParticipants([])
    void loadParticipants()
})
watch(canChat, (enabled) => {
    if (enabled) {
        connectChat()
        return
    }
    disconnectChat()
}, {immediate: true})
watch(controlsCollapsed, writeControlsCollapsedPreference)

onMounted(async () => {
    if (typeof document !== 'undefined') {
        updatePlayerFullscreenState()
        document.addEventListener('fullscreenchange', updatePlayerFullscreenState)
        window.addEventListener(TEACHER_LIVE_UNEXPECTED_PAUSED_EVENT, handleUnexpectedPaused)
    }
    await loadMessages()
    await loadParticipants()
})

onUnmounted(() => {
    disconnectChat()
    if (typeof document !== 'undefined') {
        document.removeEventListener('fullscreenchange', updatePlayerFullscreenState)
        window.removeEventListener(TEACHER_LIVE_UNEXPECTED_PAUSED_EVENT, handleUnexpectedPaused)
    }
})

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

function handleUnexpectedPaused(event: Event) {
    const session = (event as CustomEvent<{session?: ClassSession}>).detail?.session
    if (!session || session.id !== props.session.id) {
        return
    }
    emit('session-change', session)
}

async function startLive() {
    await runControlAction('start', async () => {
        await updateLiveStatus(() => startClassLive(props.session.id))
        await live.connect()
        await live.publishDefaults()
    })
}

async function pauseLive() {
    await runControlAction('pause', async () => {
        await live.pausePublishing()
        await updateLiveStatus(() => pauseClassLive(props.session.id))
    })
}

async function resumeLive() {
    await runControlAction('resume', async () => {
        await updateLiveStatus(() => resumeClassLive(props.session.id))
        await live.connect()
        await live.publishDefaults()
    })
}

async function stopLive() {
    await live.disconnect()
    await updateLiveStatus(() => stopClassLive(props.session.id))
}

async function endLive() {
    if (!canStop.value) {
        return
    }
    await runControlAction('stop', stopLive)
}

function handleBack() {
    if (expanded.value) {
        expanded.value = false
        return
    }
    emit('close')
}

function expandInPage() {
    if (isFullscreenLayout.value) {
        return
    }
    expanded.value = true
}

async function togglePlayerFullscreen() {
    if (typeof document === 'undefined') {
        return
    }
    try {
        if (isPlayerFullscreen.value) {
            await document.exitFullscreen?.()
            return
        }
        await stageElement.value?.requestFullscreen?.()
    } catch {
        notify.error(t('courseDetail.live.controlActionFailed'))
    }
}

function updatePlayerFullscreenState() {
    if (typeof document === 'undefined') {
        isPlayerFullscreen.value = false
        return
    }
    isPlayerFullscreen.value = document.fullscreenElement === stageElement.value
}

function toggleControlsPanel() {
    controlsCollapsed.value = !controlsCollapsed.value
}

function readControlsCollapsedPreference() {
    if (typeof window === 'undefined') return false
    try {
        return window.localStorage.getItem(LIVE_CONTROLS_COLLAPSED_STORAGE_KEY) === 'true'
    } catch {
        return false
    }
}

function writeControlsCollapsedPreference(collapsed: boolean) {
    if (typeof window === 'undefined') return
    try {
        window.localStorage.setItem(LIVE_CONTROLS_COLLAPSED_STORAGE_KEY, String(collapsed))
    } catch {
        // Ignore storage failures so private mode or quota issues do not affect live controls.
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

function isLoadingAction(action: LiveControlAction) {
    return loadingAction.value === action
}

async function runControlAction(action: LiveControlAction, task: () => Promise<void>) {
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

async function chooseCameraOverlayPosition(position: CameraOverlayPosition) {
    try {
        await live.setCameraOverlayPosition(position)
    } catch {
        notify.error(t('courseDetail.live.cameraPositionSyncFailed'))
    }
}

async function updateLiveStatus(action: () => Promise<ClassSession>) {
    busy.value = true
    try {
        emit('session-change', await action())
    } catch {
        notify.error(t('courseDetail.live.statusUpdateFailed'))
    } finally {
        busy.value = false
    }
}

function handleLiveStatus(status: number) {
    if (!props.canParticipate) {
        return
    }
    if (status === ClassLiveStatus.LIVE || status === ClassLiveStatus.PAUSED) {
        void (async () => {
            await live.connect()
        })()
        return
    }
    void live.disconnect()
}

async function loadMessages() {
    if (!canChat.value) {
        return
    }
    messagesLoading.value = true
    try {
        const page = await listClassBarrages(props.session.id, 1, 30)
        messages.value = [...page.records].reverse()
    } catch {
        messages.value = []
    } finally {
        messagesLoading.value = false
    }
}

async function loadParticipants() {
    if (!props.canParticipate) {
        live.setSessionParticipants([])
        return
    }
    participantsLoading.value = true
    try {
        participantDirectory.value = await listClassSessionParticipants(props.session.id)
        live.setSessionParticipants(participantDirectory.value)
    } catch {
        participantDirectory.value = []
        live.setSessionParticipants([])
    } finally {
        participantsLoading.value = false
    }
}

function connectChat() {
    if (chatSubscription) {
        return
    }
    liveDanmakuMessages.value = []
    void loadMessages()
    chatSubscription = subscribeClassBarrages(props.session.id, (message) => {
        messages.value = [...messages.value, message].slice(-80)
        liveDanmakuMessages.value = [...liveDanmakuMessages.value, message].slice(-MAX_DANMAKU_ITEMS)
    }, loadMessages)
}

function disconnectChat() {
    chatSubscription?.close()
    chatSubscription = null
    liveDanmakuMessages.value = []
}

async function submitMessage() {
    if (!canChat.value || !draft.value) {
        return
    }
    sending.value = true
    try {
        await sendClassBarrage(props.session.id, draft.value)
        draft.value = ''
    } catch {
        notify.error(t('courseDetail.live.messageSendFailed'))
    } finally {
        sending.value = false
    }
}

function formatTime(value: string) {
    return new Intl.DateTimeFormat(String(locale.value), {
        hour: '2-digit',
        minute: '2-digit',
    }).format(new Date(value))
}

function formatSessionTime(value: string) {
    return new Intl.DateTimeFormat(String(locale.value), {
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
    }).format(new Date(value))
}

function senderName(message: ClassBarrage) {
    return message.senderDisplayName || t('courseDetail.live.unknownUser')
}

function senderRoleLabel(message: ClassBarrage) {
    if (message.senderRoleLabel) {
        return message.senderRoleLabel
    }
    return message.senderId === props.session.teacherId ? t('courseDetail.live.teacherRole') : t('courseDetail.live.studentRole')
}
function onlineParticipantMeta(participant: LiveOnlineParticipant) {
    const role = participant.role === 0 ? t('courseDetail.live.teacherRole') : t('courseDetail.live.studentRole')
    return participant.isLocal ? t('courseDetail.live.youRole', {role}) : role
}

function qualityLabel(quality: string) {
    const normalized = quality.toLowerCase()
    if (normalized === 'excellent') return t('courseDetail.live.qualityExcellent')
    if (normalized === 'good') return t('courseDetail.live.qualityGood')
    if (normalized === 'poor') return t('courseDetail.live.qualityPoor')
    if (normalized === 'lost') return t('courseDetail.live.qualityLost')
    return t('courseDetail.live.qualityUnknown')
}

function qualityClass(quality: string) {
    return `quality-${quality.toLowerCase()}`
}

function formatLiveLatencyLabel() {
    const rtt = live.networkStats.value.rttMs
    if (rtt != null) {
        return isFullscreenLayout.value
            ? t('courseDetail.live.rttLabel', {value: rtt})
            : t('courseDetail.live.rttCompact', {value: rtt})
    }
    return isFullscreenLayout.value ? t('courseDetail.live.latencyUnknown') : t('courseDetail.live.latencyUnknownCompact')
}
</script>

<style scoped>
.classroom-live-experience {
  --live-bg: var(--color-surface-card);
  --live-ink: var(--color-on-surface);
  --live-muted: var(--color-muted);
  --live-border: var(--color-primary);
  --live-soft-border: var(--color-outline-light);
  --live-surface: var(--color-surface-container);
  --live-raised: var(--color-surface-container-high);
  --live-error: var(--color-error);
  --live-good: #16a34a;
  --live-overlay: var(--color-overlay);
  --live-contrast: var(--color-on-primary);
  position: fixed;
  z-index: 1900;
  display: flex;
  overflow: hidden;
  background: var(--live-bg);
  color: var(--live-ink);
  font-family: var(--font-body);
}

.mode-fullscreen {
  inset: 0;
  flex-direction: column;
}

.mode-popup {
  top: var(--space-md);
  right: var(--space-md);
  bottom: var(--space-md);
  width: min(400px, calc(100vw - (var(--space-md) * 2)));
  flex-direction: column;
  border: 1px solid var(--live-soft-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}

.mode-expanded-inline {
  z-index: 2400;
}

.live-header {
  position: relative;
  z-index: 2;
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  border-bottom: 1px solid var(--live-border);
  background: var(--live-bg);
}

.mode-fullscreen .live-header {
  min-height: 78px;
  padding: 14px var(--space-xl);
}

.mode-popup .live-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  min-height: 82px;
  padding: 12px var(--space-md);
  border-color: var(--live-soft-border);
}

.header-icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-sm);
  border: 0;
  background: transparent;
  color: var(--live-ink);
  cursor: pointer;
  font-family: var(--font-label);
  transition: opacity 0.2s, transform 0.2s;
}

.header-icon-button:hover {
  opacity: 0.72;
}

.header-icon-button:active {
  transform: scale(0.97);
}

.header-back-label {
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.header-actions {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-xs);
}

.mode-popup .header-actions {
  align-self: start;
}

.mode-popup .header-icon-button {
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  border: 1px solid var(--live-soft-border);
  border-radius: var(--radius-sm);
}

.mode-popup .controls-header-toggle {
  border-color: color-mix(in srgb, var(--live-border) 62%, var(--live-soft-border));
  color: var(--live-border);
}

.live-title-block {
  min-width: 0;
}

.mode-fullscreen .live-title-block {
  display: flex;
  flex: 1 1 auto;
  align-items: center;
  justify-content: space-between;
  gap: 22px;
  border-left: 1px solid var(--live-soft-border);
  padding-left: var(--space-lg);
}

.mode-popup .live-title-block {
  align-self: start;
  max-width: 240px;
  padding-right: 0;
  text-align: left;
}

.live-title-block h2 {
  margin: 0;
  overflow: hidden;
  color: var(--live-ink);
  font-family: var(--font-heading);
  font-size: clamp(24px, 2.1vw, 34px);
  font-weight: 600;
  line-height: 1.16;
  letter-spacing: 0;
  text-wrap: balance;
}

.title-marquee {
  display: inline-flex;
  max-width: 100%;
  gap: 28px;
  white-space: nowrap;
}

.mode-popup .title-marquee {
  min-width: max-content;
  animation: liveTitleMarquee 10s linear infinite;
}

.title-marquee > span {
  flex: 0 0 auto;
}

.mode-fullscreen .title-marquee > span[aria-hidden='true'] {
  display: none;
}

.mode-popup .live-title-block h2 {
  overflow: hidden;
  font-size: 19px;
  text-overflow: ellipsis;
  text-transform: none;
  white-space: nowrap;
}

.live-meta-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  color: var(--live-muted);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  white-space: nowrap;
}

.mode-popup .live-meta-row {
  justify-content: flex-start;
  margin-top: 6px;
  font-size: 11px;
  letter-spacing: 0.05em;
  text-transform: none;
}

.live-status,
.live-stat {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
}

.live-status {
  color: var(--live-error);
}

.live-status.active {
  color: var(--live-good);
}

.live-status > span {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--live-error);
}

.live-status.active > span {
  background: var(--live-good);
  animation: livePulse 1.4s ease-in-out infinite;
}

.meta-divider {
  width: 1px;
  height: 18px;
  background: var(--live-soft-border);
}

.live-layout {
  min-height: 0;
  flex: 1 1 auto;
}

.mode-fullscreen .live-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 480px;
}

.mode-fullscreen.controls-hidden .live-layout {
  grid-template-columns: minmax(0, 1fr) minmax(320px, 380px);
}

.mode-popup .live-layout {
  display: flex;
  overflow: hidden;
  flex-direction: column;
  padding-bottom: 0;
}

.mode-popup.controls-hidden .live-layout {
  padding-bottom: 0;
}

.live-main-column {
  min-width: 0;
  min-height: 0;
}

.mode-fullscreen .live-main-column {
  display: flex;
  min-height: 0;
  flex-direction: column;
  overflow-y: auto;
  justify-content: center;
  padding: var(--space-lg) var(--space-lg) 116px;
}

.mode-fullscreen.controls-hidden .live-main-column {
  padding: var(--space-md) var(--space-lg);
}

.mode-popup .live-main-column {
  display: flex;
  min-height: 0;
  flex: 1 1 auto;
  flex-direction: column;
  overflow: hidden;
}

.live-stage {
  position: relative;
  overflow: hidden;
  background:
    linear-gradient(var(--live-overlay), var(--live-overlay)),
    url('https://lh3.googleusercontent.com/aida-public/AB6AXuA09kapa7PNhQw7Goup7SnoRmuOMBmbPCLfAQXaSPmUcExJfHBYHZvGmKO_b8AFGn_kvAzKExBVte7IkrSkGzxnVfaiI_5tt_5-8-7ZdC3nk9dBfuVuAclh2wxU3joswa5wgMRMrWji5aGeGmlZ40-lUA_k6wQj6IOeb-bgKHSn9K2l1-kZT_GCJyACpF2OdRZD16QNOu_eEUnrDHXyBJrltVs2Gpt2s51Gs0bZpoxpFEa4Eow45v6T_S29kJT7fD5rSPWg9qtk5JRf') center / cover;
}

.mode-fullscreen .live-stage {
  width: min(100%, 1180px);
  margin: 0 auto;
  aspect-ratio: 16 / 9;
  border: 1px solid var(--live-border);
  border-radius: 34px;
}

.mode-fullscreen.controls-hidden .live-stage {
  width: min(100%, 1480px);
}

.mode-popup .live-stage {
  width: 100%;
  flex: 0 0 auto;
  aspect-ratio: 16 / 9;
  background: var(--color-surface-container-high);
}

.live-stage:fullscreen {
  display: grid;
  width: 100vw;
  height: 100vh;
  aspect-ratio: auto;
  border: 0;
  border-radius: 0;
  background: #000;
  place-items: center;
}

.live-stage:fullscreen .live-video {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.live-stage:fullscreen:hover .player-danmaku-form,
.live-stage:fullscreen:focus-within .player-danmaku-form {
  display: grid;
}

.live-video {
  position: relative;
  z-index: 1;
  width: 100%;
  height: 100%;
  background: transparent;
  object-fit: cover;
}

.live-skeleton {
  position: relative;
  overflow: hidden;
  display: block;
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--live-soft-border) 58%, transparent);
}

.live-skeleton::after {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent, color-mix(in srgb, var(--live-bg) 72%, transparent), transparent);
  content: '';
  transform: translateX(-100%);
  animation: liveSkeletonShimmer 1.35s ease-in-out infinite;
}

.live-stage-loading {
  position: absolute;
  z-index: 2;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: var(--space-lg);
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--live-bg) 88%, transparent), transparent 58%),
    color-mix(in srgb, var(--live-raised) 72%, transparent);
  pointer-events: none;
}

.stage-loading-frame {
  display: grid;
  width: min(460px, 78%);
  gap: var(--space-sm);
  margin-top: auto;
  padding-bottom: var(--space-lg);
}

.stage-loading-title {
  width: 42%;
  height: 22px;
}

.stage-loading-line {
  width: 58%;
  height: 12px;
}

.stage-loading-line.wide {
  width: 86%;
}

.stage-loading-rail {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.stage-loading-pill {
  width: 76px;
  height: 26px;
  border-radius: var(--radius-pill);
}

.stage-hover-layer {
  position: absolute;
  z-index: 2;
  inset: 0;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: var(--space-md);
  background: linear-gradient(to top, var(--live-overlay), transparent 58%);
  color: var(--live-ink);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.24s ease;
}

.stage-hover-layer span {
  font-family: var(--font-heading);
  font-size: clamp(15px, 1.7vw, 20px);
  line-height: 1.15;
}

.stage-expand-button {
  display: inline-grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, 0.32);
  border-radius: var(--radius-sm);
  background: rgba(0, 0, 0, 0.34);
  color: #fff;
  cursor: pointer;
  pointer-events: auto;
  transition: background 0.18s ease, transform 0.18s ease;
}

.stage-expand-button:hover {
  background: rgba(0, 0, 0, 0.52);
}

.stage-expand-button:active {
  transform: scale(0.96);
}

.live-stage:hover .stage-hover-layer,
.live-stage.screen-share-active .stage-hover-layer {
  opacity: 1;
}

.danmaku-layer {
  position: absolute;
  z-index: 3;
  inset: 12px 0 24%;
  overflow: hidden;
  pointer-events: none;
}

.danmaku-item {
  position: absolute;
  top: calc(var(--danmaku-lane) * 34px);
  left: 100%;
  max-width: min(72%, 620px);
  overflow: hidden;
  padding: 6px 12px;
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-radius: var(--radius-pill);
  background: rgba(0, 0, 0, 0.38);
  color: #fff;
  font-family: var(--font-label);
  font-size: 14px;
  line-height: 1.2;
  text-overflow: ellipsis;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.55);
  white-space: nowrap;
  animation: danmakuMove var(--danmaku-duration) linear forwards;
}

.mode-fullscreen .danmaku-layer {
  top: 20px;
}

.mode-fullscreen .danmaku-item {
  top: calc(var(--danmaku-lane) * 42px);
  font-size: 16px;
}

.camera-overlay {
  position: absolute;
  z-index: 4;
  width: clamp(112px, 30%, 172px);
  aspect-ratio: 16 / 9;
  border: 1px solid var(--live-border);
  border-radius: var(--radius-md);
  background: var(--live-raised);
  object-fit: cover;
}

.position-top-left {
  top: 14px;
  left: 14px;
}

.position-top-right {
  top: 14px;
  right: 14px;
}

.position-bottom-left {
  bottom: 14px;
  left: 14px;
}

.position-bottom-right {
  right: 14px;
  bottom: 14px;
}

.stage-overlay {
  position: absolute;
  z-index: 6;
  inset: 0;
  display: grid;
  place-items: center;
  align-content: center;
  gap: var(--space-sm);
  padding: var(--space-md);
  background: var(--live-overlay);
  color: var(--color-on-surface-variant);
  font-family: var(--font-label);
  text-align: center;
}

.stage-overlay svg {
  opacity: 0.42;
}

.overlay-position-controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  margin: var(--space-sm) 0 0;
  padding: var(--space-xs);
  border: 1px solid var(--live-soft-border);
  border-radius: var(--radius-md);
  background: var(--live-surface);
}

.mode-popup .overlay-position-controls {
  position: absolute;
  z-index: 8;
  top: calc(82px + var(--space-sm));
  right: var(--space-sm);
  left: var(--space-sm);
  margin: 0;
  background: color-mix(in srgb, var(--live-surface) 92%, transparent);
  box-shadow: var(--shadow-card);
}

.overlay-position-label {
  flex: 0 0 auto;
  color: var(--live-muted);
  font-family: var(--font-label);
  font-size: 12px;
}

.position-options {
  display: grid;
  flex: 1 1 auto;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-xs);
}

.position-option {
  min-height: 30px;
  border: 1px solid var(--live-soft-border);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--live-ink);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 12px;
}

.position-option.active {
  border-color: var(--live-border);
  background: var(--live-ink);
  color: var(--live-contrast);
}

.ai-summary-card {
  color: var(--live-ink);
}

.mode-popup .ai-summary-card {
  display: none;
}

.ai-summary-card header {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.ai-summary-card h3 {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 20px;
  line-height: 1.2;
  letter-spacing: 0;
}

.ai-summary-card p {
  margin: var(--space-md) 0 0;
  color: var(--color-on-surface-variant);
  font-size: 15px;
  line-height: 1.58;
}

.ai-summary-card ul {
  display: grid;
  gap: var(--space-sm);
  margin: var(--space-md) 0 0;
  padding: 0;
  list-style: none;
}

.ai-summary-card li {
  position: relative;
  padding-left: var(--space-md);
  color: var(--color-on-surface-variant);
  font-size: 14px;
  line-height: 1.38;
}

.ai-summary-card li::before {
  position: absolute;
  top: 0.62em;
  left: 0;
  width: 6px;
  height: 6px;
  background: var(--live-ink);
  content: '';
}

.hint {
  margin: 0 var(--space-md) var(--space-md);
  color: var(--live-muted);
  font-size: 13px;
  line-height: 1.6;
}

.chat-panel {
  padding: 0 var(--space-md) var(--space-md);
}

.mode-popup .chat-panel {
  display: flex;
  min-height: 0;
  flex: 1 1 auto;
  flex-direction: column;
  overflow: hidden;
  padding: var(--space-sm) var(--space-md) var(--space-md);
}

.chat-panel h3 {
  margin: 0 0 var(--space-md);
  padding-bottom: var(--space-sm);
  border-bottom: 1px solid var(--live-soft-border);
  color: var(--live-ink);
  font-family: var(--font-label);
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.chat-list {
  min-height: 120px;
}

.mode-popup .chat-list {
  display: grid;
  min-height: 0;
  flex: 1 1 auto;
  overflow-y: auto;
  gap: 0;
  scrollbar-width: none;
}

.mode-popup .chat-list::-webkit-scrollbar {
  display: none;
}

.empty {
  margin: var(--space-md) 0;
  color: var(--live-muted);
}

.chat-loading-list {
  display: grid;
  gap: var(--space-sm);
  padding: var(--space-sm) 0;
}

.chat-loading-row,
.online-loading-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--space-sm);
  align-items: center;
  padding: var(--space-sm) 0;
  border-bottom: 1px solid var(--live-soft-border);
}

.online-loading-row {
  grid-template-columns: auto minmax(0, 1fr) 60px;
}

.chat-loading-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
}

.chat-loading-copy {
  display: grid;
  min-width: 0;
  gap: 8px;
}

.chat-loading-name {
  width: 34%;
  height: 12px;
}

.chat-loading-text {
  width: 84%;
  height: 14px;
}

.chat-loading-text.short {
  width: 54%;
}

.online-loading-chip {
  width: 54px;
  height: 24px;
}

.chat-message {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--space-sm);
  padding: var(--space-sm) 0;
  border-bottom: 1px solid var(--live-soft-border);
}

.chat-avatar-link {
  align-self: start;
}

.chat-body {
  min-width: 0;
}

.chat-meta {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-bottom: var(--space-xs);
}

.chat-meta strong {
  overflow: hidden;
  color: var(--live-ink);
  font-family: var(--font-label);
  font-size: 14px;
  letter-spacing: 0.04em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sender-line {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-xs);
}

.chat-meta .sender-role-tag {
  flex: 0 0 auto;
  padding: 2px 6px;
  border: 1px solid var(--live-soft-border);
  border-radius: var(--radius-sm);
  background: var(--live-raised);
  color: var(--live-muted);
  font-family: var(--font-label);
  font-size: 11px;
  line-height: 1.2;
}

.chat-meta span {
  flex: 0 0 auto;
  color: var(--live-muted);
  font-size: 12px;
}

.chat-message p {
  margin: 0;
  color: var(--color-on-surface-variant);
  font-size: 14px;
  line-height: 1.5;
}

.chat-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-sm);
  margin-top: var(--space-md);
}

.mode-popup .chat-form {
  flex: 0 0 auto;
  margin-top: var(--space-sm);
  padding-top: var(--space-sm);
  border-top: 1px solid var(--live-soft-border);
  background: var(--live-bg);
}

.chat-form input {
  min-width: 0;
  height: 42px;
  padding: 0 var(--space-md);
  border: 1px solid var(--live-soft-border);
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--live-ink);
  outline: none;
}

.chat-form input:focus {
  border-color: var(--live-border);
}

.chat-form button {
  display: inline-grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border: 0;
  border-radius: var(--radius-md);
  background: var(--live-ink);
  color: var(--live-contrast);
  cursor: pointer;
}

.live-side-panel {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  border-left: 1px solid var(--live-border);
  background: var(--live-bg);
}

.side-tabs {
  display: grid;
  height: 58px;
  flex: 0 0 auto;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border-bottom: 1px solid var(--live-border);
}

.side-tab {
  border: 0;
  background: var(--live-bg);
  color: var(--live-ink);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.side-tab + .side-tab {
  border-left: 1px solid var(--live-border);
}

.side-tab.active {
  background: var(--live-ink);
  color: var(--live-contrast);
}

.player-danmaku-form {
  position: absolute;
  z-index: 8;
  bottom: 28px;
  left: 50%;
  display: none;
  grid-template-columns: minmax(220px, 520px) 44px;
  width: min(620px, calc(100vw - 48px));
  gap: var(--space-sm);
  padding: 8px;
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: var(--radius-md);
  background: rgba(0, 0, 0, 0.54);
  backdrop-filter: blur(12px);
  transform: translateX(-50%);
}

.player-danmaku-form input {
  min-width: 0;
  height: 44px;
  padding: 0 var(--space-md);
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  outline: none;
}

.player-danmaku-form input::placeholder {
  color: rgba(255, 255, 255, 0.68);
}

.player-danmaku-form input:focus {
  border-color: rgba(255, 255, 255, 0.54);
}

.player-danmaku-form button {
  display: inline-grid;
  width: 44px;
  height: 44px;
  place-items: center;
  border: 0;
  border-radius: var(--radius-sm);
  background: #fff;
  color: #111;
  cursor: pointer;
}

.player-danmaku-form input:disabled,
.player-danmaku-form button:disabled {
  cursor: not-allowed;
  opacity: 0.48;
}

.sidebar-summary {
  margin: 30px;
  padding: 38px 32px 34px;
  border: 1px solid var(--live-border);
  border-radius: 40px;
  background: var(--live-surface);
}

.sidebar-summary h3 {
  font-family: var(--font-label);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.sidebar-summary p {
  margin-top: 26px;
  font-size: 20px;
  line-height: 1.62;
}

.sidebar-chat-list {
  flex: 1 1 auto;
  overflow-y: auto;
  padding: 0 30px 20px;
}

.sidebar-message {
  display: grid;
  padding: 22px 0;
}

.sidebar-message .chat-meta strong {
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 500;
  letter-spacing: 0;
}

.sidebar-message .chat-message p,
.sidebar-message p {
  font-size: 18px;
}

.sidebar-chat-form {
  flex: 0 0 auto;
  margin: auto 20px 20px;
  padding-top: 20px;
  border-top: 1px solid var(--live-border);
}

.notes-panel {
  padding: 34px 30px;
}

.notes-panel h3 {
  margin: 0 0 18px;
  font-family: var(--font-heading);
  font-size: 30px;
  font-weight: 500;
}

.notes-panel p {
  margin: 0 0 var(--space-md);
  color: var(--color-on-surface-variant);
  line-height: 1.7;
}

.notes-panel dl {
  display: grid;
  gap: 14px;
  margin: 0;
}

.notes-panel div {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding-top: 14px;
  border-top: 1px solid var(--live-soft-border);
}

.notes-panel dt {
  color: var(--live-muted);
}

.notes-panel dd {
  margin: 0;
  text-align: right;
}

.online-panel {
  display: flex;
  min-height: 0;
  flex: 1 1 auto;
  flex-direction: column;
  padding: 34px 30px;
}

.online-panel header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-md);
  padding-bottom: 18px;
  border-bottom: 1px solid var(--live-soft-border);
}

.online-panel h3 {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 30px;
  font-weight: 500;
}

.online-panel header span {
  color: var(--live-muted);
  font-family: var(--font-label);
  font-size: 13px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.online-list {
  display: grid;
  overflow-y: auto;
  gap: var(--space-sm);
  padding-top: var(--space-md);
}

.online-loading-list {
  display: grid;
  gap: var(--space-sm);
}

.online-row {
  display: grid;
  min-width: 0;
  align-items: center;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: var(--space-sm);
  padding: var(--space-sm) 0;
  border-bottom: 1px solid var(--live-soft-border);
}

.online-avatar-link {
  align-self: center;
}

.online-body {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.online-body strong {
  overflow: hidden;
  color: var(--live-ink);
  font-family: var(--font-label);
  font-size: 14px;
  letter-spacing: 0.04em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.online-body span {
  color: var(--live-muted);
  font-size: 12px;
}

.quality-chip {
  padding: 4px 8px;
  border: 1px solid var(--live-soft-border);
  border-radius: var(--radius-sm);
  color: var(--live-muted);
  font-family: var(--font-label);
  font-size: 11px;
}

.quality-excellent,
.quality-good {
  border-color: color-mix(in srgb, var(--live-good) 48%, var(--live-soft-border));
  color: var(--live-good);
}

.quality-poor,
.quality-lost {
  border-color: color-mix(in srgb, var(--live-error) 48%, var(--live-soft-border));
  color: var(--live-error);
}

.live-bottom-dock {
  position: absolute;
  z-index: 6;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  border: 1px solid var(--live-border);
  background: var(--live-bg);
  transition: opacity 0.24s ease, transform 0.24s ease;
}

.dock-collapse-button {
  position: absolute;
  z-index: 7;
  display: inline-flex;
  width: 64px;
  height: 28px;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--live-border);
  background: var(--live-bg);
  color: var(--live-ink);
  cursor: pointer;
  transition: background 0.2s ease, bottom 0.24s ease, color 0.2s ease, transform 0.24s ease;
}

.dock-collapse-button:hover {
  background: var(--live-ink);
  color: var(--live-contrast);
}

.dock-collapse-button:active {
  transform: scale(0.96);
}

.mode-fullscreen .dock-collapse-button:active {
  transform: translateX(-50%) scale(0.96);
}

.mode-popup .dock-collapse-button:active {
  transform: translateX(50%) scale(0.96);
}

.mode-fullscreen .live-bottom-dock {
  bottom: 26px;
  left: calc((100% - 480px) / 2);
  transform: translateX(-50%);
  padding: 16px 28px;
  border-radius: 999px;
}

.mode-fullscreen .live-bottom-dock.collapsed {
  opacity: 0;
  transform: translateX(-50%) translateY(calc(100% + 44px));
  pointer-events: none;
}

.mode-fullscreen .dock-collapse-button {
  bottom: 112px;
  left: calc((100% - 480px) / 2);
  transform: translateX(-50%);
  border-radius: 999px;
}

.mode-fullscreen .dock-collapse-button.collapsed {
  bottom: 18px;
}

.mode-popup .live-bottom-dock {
  top: calc(82px + var(--space-sm));
  right: var(--space-sm);
  bottom: auto;
  left: auto;
  width: auto;
  height: auto;
  flex-direction: row;
  justify-content: center;
  gap: 6px;
  padding: 6px;
  border-width: 1px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--live-bg) 90%, transparent);
  box-shadow: var(--shadow-card);
}

.mode-popup .live-bottom-dock.collapsed {
  opacity: 0;
  transform: translateY(-8px);
  pointer-events: none;
}

.dock-button {
  display: inline-flex;
  width: 48px;
  height: 48px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px solid var(--live-border);
  border-radius: var(--radius-md);
  background: var(--live-bg);
  color: var(--live-ink);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  transition: background 0.2s, color 0.2s, transform 0.2s;
}

.dock-button.active,
.dock-button:hover:not(:disabled) {
  background: var(--live-ink);
  color: var(--live-contrast);
}

.dock-button:active:not(:disabled) {
  transform: scale(0.96);
}

.dock-button.call-button {
  border-color: color-mix(in srgb, var(--live-good) 58%, var(--live-border));
  background: color-mix(in srgb, var(--live-good) 12%, var(--live-bg));
  color: var(--live-good);
}

.dock-button.call-button:hover:not(:disabled) {
  border-color: var(--live-good);
  background: var(--live-good);
  color: var(--color-surface-card);
}

.dock-button.danger {
  border-color: var(--live-error);
  background: color-mix(in srgb, var(--live-error) 12%, var(--live-bg));
  color: var(--live-error);
}

.dock-button.danger:hover:not(:disabled) {
  background: var(--live-error);
  color: var(--color-surface-card);
}

.mode-popup .dock-button {
  width: 30px;
  height: 30px;
  border-radius: 999px;
}

.mode-popup .dock-divider {
  width: 1px;
  height: 20px;
}

.mode-popup .stop-live-button {
  border-color: color-mix(in srgb, var(--live-error) 42%, var(--live-border));
  color: var(--live-error);
}

.mode-popup .stop-live-button:hover:not(:disabled) {
  border-color: var(--live-error);
  background: color-mix(in srgb, var(--live-error) 12%, var(--live-bg));
  color: var(--live-error);
}

.button-spinner {
  display: inline-block;
  width: 18px;
  height: 18px;
  border: 2px solid currentColor;
  border-right-color: transparent;
  border-radius: 50%;
  animation: liveButtonSpin 0.72s linear infinite;
}

.mode-fullscreen .stop-live-button {
  width: auto;
  min-width: 114px;
  padding: 0 26px;
  border-color: color-mix(in srgb, var(--live-error) 42%, var(--live-border));
  border-radius: 999px;
  background: color-mix(in srgb, var(--live-error) 10%, var(--live-bg));
  color: var(--live-error);
}

.mode-fullscreen .stop-live-button:hover:not(:disabled) {
  background: var(--live-error);
  border-color: var(--live-error);
  color: var(--color-surface-card);
}

.dock-divider {
  width: 1px;
  height: 32px;
  background: var(--live-soft-border);
}

button:disabled,
input:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

@keyframes livePulse {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.44;
    transform: scale(1.35);
  }
}

@keyframes liveButtonSpin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes liveTitleMarquee {
  0%,
  18% {
    transform: translateX(0);
  }
  82%,
  100% {
    transform: translateX(calc(-50% - 14px));
  }
}

@keyframes liveSkeletonShimmer {
  to {
    transform: translateX(100%);
  }
}

@keyframes danmakuMove {
  from {
    transform: translateX(0);
  }
  to {
    transform: translateX(calc(-100vw - 100%));
  }
}

@media (max-width: 1100px) {
  .mode-fullscreen .live-header {
    padding: 18px 28px;
  }

  .mode-fullscreen .live-layout {
    grid-template-columns: 1fr;
    overflow-y: auto;
    padding-bottom: 112px;
  }

  .mode-fullscreen.controls-hidden .live-layout {
    grid-template-columns: 1fr;
    padding-bottom: 0;
  }

  .mode-fullscreen .live-main-column {
    overflow: visible;
    padding: 28px 24px 0;
  }

  .mode-fullscreen.controls-hidden .live-main-column {
    padding: 28px 24px 0;
  }

  .mode-fullscreen .live-stage {
    width: 100%;
    border-radius: 32px;
  }

  .mode-fullscreen .live-side-panel {
    border-left: 0;
    border-top: 1px solid var(--live-border);
  }

  .mode-fullscreen .live-bottom-dock {
    right: 0;
    bottom: 0;
    left: 0;
    height: 104px;
    transform: none;
    justify-content: space-around;
    border-width: 1px 0 0;
    border-radius: 0;
  }
}

@media (max-width: 720px) {
  .mode-popup,
  .mode-fullscreen {
    inset: 0;
    width: auto;
    border: 0;
    border-radius: 0;
  }

  .mode-fullscreen .live-header,
  .mode-popup .live-header {
    min-height: 96px;
    padding: var(--space-md);
    border-color: var(--live-soft-border);
  }

  .mode-popup .live-header {
    min-height: 82px;
    padding: 12px var(--space-md);
  }

  .mode-fullscreen .live-title-block {
    position: absolute;
    top: var(--space-md);
    right: calc(var(--space-xl) + var(--space-md));
    left: calc(var(--space-xl) + var(--space-md));
    display: block;
    border-left: 0;
    padding-left: 0;
    text-align: center;
  }

  .mode-popup .live-title-block {
    position: static;
    display: block;
    max-width: 220px;
    border-left: 0;
    padding-right: var(--space-sm);
    padding-left: 0;
    text-align: left;
  }

  .mode-fullscreen .live-title-block h2,
  .mode-popup .live-title-block h2 {
    overflow: hidden;
    font-size: 20px;
    line-height: 1.22;
    text-overflow: ellipsis;
    text-transform: none;
    white-space: nowrap;
  }

  .mode-popup .live-title-block h2 {
    font-size: 19px;
  }

  .mode-fullscreen .live-meta-row {
    justify-content: center;
    margin-top: var(--space-sm);
    font-size: 13px;
    letter-spacing: 0.05em;
    text-transform: none;
  }

  .mode-popup .live-meta-row {
    justify-content: flex-start;
    margin-top: var(--space-sm);
    font-size: 13px;
    letter-spacing: 0.05em;
    text-transform: none;
  }

  .header-back-label {
    display: none;
  }

  .mode-fullscreen .live-main-column {
    padding: 0 0 122px;
  }

  .mode-fullscreen.controls-hidden .live-main-column {
    padding: 0;
  }

  .mode-fullscreen .live-stage,
  .mode-popup .live-stage {
    border: 0;
    border-radius: 0;
    background: var(--color-surface-container-high);
  }

  .live-stage-loading {
    padding: var(--space-md);
  }

  .stage-loading-frame {
    width: 88%;
    padding-bottom: var(--space-md);
  }

  .mode-fullscreen .live-side-panel {
    display: none;
  }

  .mode-fullscreen .ai-summary-card {
    display: block;
  }

  .mode-fullscreen .live-layout,
  .mode-popup .live-layout {
    display: block;
    overflow-y: auto;
    padding-bottom: 0;
  }

  .mode-popup .live-layout {
    display: flex;
    overflow: hidden;
    flex-direction: column;
  }

  .mode-popup .live-main-column {
    display: flex;
    min-height: 0;
    flex: 1 1 auto;
    flex-direction: column;
    overflow: hidden;
  }

  .mode-fullscreen .live-main-column .ai-summary-card,
  .mode-popup .ai-summary-card {
    margin: var(--space-md);
    padding: var(--space-md);
    border: 1px solid var(--live-soft-border);
    border-radius: var(--radius-lg);
    background: var(--live-surface);
  }

  .mode-popup .ai-summary-card {
    display: none;
  }

  .ai-summary-card h3 {
    font-size: 20px;
  }

  .ai-summary-card p,
  .ai-summary-card li {
    font-size: 15px;
  }

  .mode-fullscreen .chat-panel {
    display: block;
  }

  .chat-panel {
    padding: 0 var(--space-md) var(--space-md);
  }

  .mode-popup .chat-panel {
    display: flex;
    min-height: 0;
    flex: 1 1 auto;
    flex-direction: column;
    overflow: hidden;
    padding: var(--space-sm) var(--space-md) var(--space-md);
  }

  .mode-popup .chat-list {
    min-height: 0;
    flex: 1 1 auto;
    overflow-y: auto;
  }

  .live-bottom-dock {
    right: 0;
    bottom: 0;
    left: 0;
    height: 84px;
    justify-content: space-around;
    gap: var(--space-sm);
    padding: var(--space-sm) var(--space-md);
    border-width: 1px 0 0;
    border-radius: 0;
  }

  .mode-popup .live-bottom-dock {
    top: calc(82px + var(--space-sm));
    right: var(--space-sm);
    bottom: auto;
    left: auto;
    width: auto;
    height: auto;
    flex-direction: row;
    gap: 6px;
    padding: 6px;
    border-width: 1px;
    border-radius: 999px;
  }

  .dock-divider {
    display: none;
  }
}

@media (max-width: 520px) {
  .mode-fullscreen .live-header,
  .mode-popup .live-header {
    padding-right: var(--space-md);
    padding-left: var(--space-md);
  }

  .mode-fullscreen .live-title-block {
    right: calc(var(--space-xl) + var(--space-sm));
    left: calc(var(--space-xl) + var(--space-sm));
  }

  .mode-fullscreen .live-title-block h2,
  .mode-popup .live-title-block h2 {
    font-size: 19px;
  }

  .mode-fullscreen .live-meta-row,
  .mode-popup .live-meta-row {
    gap: var(--space-xs);
    font-size: 12px;
  }

  .mode-fullscreen .live-main-column .ai-summary-card,
  .mode-popup .ai-summary-card {
    margin: var(--space-sm);
    padding: var(--space-md);
    border-radius: var(--radius-md);
  }

  .mode-popup .ai-summary-card {
    display: none;
  }

  .ai-summary-card h3 {
    font-size: 20px;
  }

  .ai-summary-card p,
  .ai-summary-card li {
    font-size: 14px;
  }

  .chat-panel {
    padding: 0 var(--space-sm) var(--space-sm);
  }

  .dock-button {
    width: 44px;
    height: 44px;
  }
}
</style>

