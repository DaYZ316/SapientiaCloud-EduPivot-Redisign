<template>
  <aside class="classroom-live-panel">
    <header class="live-header">
      <div>
        <p class="eyebrow">课堂直播</p>
        <h2>{{ session.liveStatusText || liveStatusLabel }}</h2>
      </div>
      <button class="icon-button" type="button" @click="$emit('close')">
        <X :size="18" stroke-width="1.8"/>
      </button>
    </header>

    <div class="live-stage" :class="{paused: isPaused}">
      <video
          v-if="isTeacher"
          :ref="setLocalVideo"
          autoplay
          class="live-video"
          muted
          playsinline
      ></video>
      <video
          v-else
          :ref="setRemoteVideo"
          autoplay
          class="live-video"
          playsinline
      ></video>
      <audio v-if="!isTeacher" :ref="setRemoteAudio" autoplay></audio>
      <div v-if="stageMessage" class="stage-overlay">{{ stageMessage }}</div>
    </div>

    <div v-if="isTeacher" class="control-grid">
      <button v-if="isNotStarted" class="btn-primary" type="button" :disabled="busy" @click="startLive">
        开始直播
      </button>
      <button v-if="isLive" class="btn-secondary" type="button" :disabled="busy" @click="pauseLive">
        暂停直播
      </button>
      <button v-if="isPaused" class="btn-primary" type="button" :disabled="busy" @click="resumeLive">
        恢复直播
      </button>
      <button v-if="canStop" class="btn-danger" type="button" :disabled="busy" @click="stopLive">
        结束直播
      </button>
      <button class="btn-secondary" type="button" :disabled="!live.connected.value" @click="live.toggleCamera">
        {{ live.cameraEnabled.value ? '关闭摄像头' : '开启摄像头' }}
      </button>
      <button class="btn-secondary" type="button" :disabled="!live.connected.value" @click="live.toggleMicrophone">
        {{ live.microphoneEnabled.value ? '关闭麦克风' : '开启麦克风' }}
      </button>
      <button class="btn-secondary" type="button" :disabled="!live.connected.value" @click="live.toggleScreenShare">
        {{ live.screenShareEnabled.value ? '停止共享' : '屏幕共享' }}
      </button>
    </div>

    <p v-else-if="!canParticipate" class="hint">请先在 3D 教室入座，入座后才能观看直播和发送消息。</p>

    <section class="chat-panel">
      <div class="chat-title">
        <MessageCircle :size="17" stroke-width="1.8"/>
        课堂交流
      </div>
      <div class="chat-list">
        <p v-if="messages.length === 0" class="empty">暂无消息</p>
        <article v-for="message in messages" :key="message.id" class="chat-message">
          <span>{{ formatTime(message.sentAt) }}</span>
          <p>{{ message.content }}</p>
        </article>
      </div>
      <form class="chat-form" @submit.prevent="submitMessage">
        <input v-model.trim="draft" :disabled="!canChat || sending" maxlength="300" placeholder="输入课堂消息"/>
        <button type="submit" :disabled="!canChat || sending || !draft">发送</button>
      </form>
    </section>
  </aside>
</template>

<script setup lang="ts">
import {computed, onMounted, onUnmounted, ref, watch} from 'vue'
import {MessageCircle, X} from 'lucide-vue-next'

import {
  listClassBarrages,
  pauseClassLive,
  resumeClassLive,
  sendClassBarrage,
  startClassLive,
  stopClassLive,
  subscribeClassBarrages,
} from '@/features/course/api/classSession'
import {ClassLiveStatus, ClassSessionStatus, type ClassBarrage, type ClassSession} from '@/features/course/types/classSession'
import {useClassroomLive} from '@/features/classroom/composables/useClassroomLive'
import {notify} from '@/shared/composables/useGlobalNotification'

const props = defineProps<{
  session: ClassSession
  isTeacher: boolean
  canParticipate: boolean
}>()

const emit = defineEmits<{
  close: []
  'session-change': [session: ClassSession]
}>()

const live = useClassroomLive(props.session, props.isTeacher)
const messages = ref<ClassBarrage[]>([])
const draft = ref('')
const busy = ref(false)
const sending = ref(false)
let chatSubscription: {close: () => void} | null = null

const isNotStarted = computed(() => props.session.liveStatus === ClassLiveStatus.NOT_STARTED)
const isLive = computed(() => props.session.liveStatus === ClassLiveStatus.LIVE)
const isPaused = computed(() => props.session.liveStatus === ClassLiveStatus.PAUSED)
const isEnded = computed(() => props.session.liveStatus === ClassLiveStatus.ENDED)
const canStop = computed(() => isLive.value || isPaused.value)
const canChat = computed(() => props.session.status === ClassSessionStatus.LIVE && props.canParticipate)
const liveStatusLabel = computed(() => {
  if (isLive.value) return '直播中'
  if (isPaused.value) return '已暂停'
  if (isEnded.value) return '已结束'
  return '未开播'
})
const stageMessage = computed(() => {
  if (!props.canParticipate) return props.isTeacher ? '' : '入座后可观看直播'
  if (isPaused.value) return '直播已暂停'
  if (isEnded.value) return '直播已结束'
  if (isNotStarted.value) return '等待教师开始直播'
  if (live.connecting.value) return '正在连接直播'
  if (live.errorMessage.value) return live.errorMessage.value
  return ''
})

watch(() => props.session.liveStatus, handleLiveStatus, {immediate: true})
watch(() => props.canParticipate, (canParticipate) => {
  if (!canParticipate) {
    void live.disconnect()
  } else {
    handleLiveStatus(props.session.liveStatus)
  }
})
watch(canChat, (enabled) => {
  if (enabled) {
    connectChat()
  } else {
    disconnectChat()
  }
}, {immediate: true})

onMounted(async () => {
  await loadMessages()
})

onUnmounted(() => {
  disconnectChat()
})

function setLocalVideo(element: unknown) {
  live.localVideoEl.value = element instanceof HTMLVideoElement ? element : null
  live.attachLocalTracks()
}

function setRemoteVideo(element: unknown) {
  live.remoteVideoEl.value = element instanceof HTMLVideoElement ? element : null
  live.attachRemoteTracks()
}

function setRemoteAudio(element: unknown) {
  live.remoteAudioEl.value = element instanceof HTMLAudioElement ? element : null
  live.attachRemoteAudioTracks()
}

async function startLive() {
  await updateLiveStatus(() => startClassLive(props.session.id))
  await live.connect()
  await live.publishDefaults()
}

async function pauseLive() {
  await live.pausePublishing()
  await updateLiveStatus(() => pauseClassLive(props.session.id))
}

async function resumeLive() {
  await updateLiveStatus(() => resumeClassLive(props.session.id))
  await live.connect()
  await live.publishDefaults()
}

async function stopLive() {
  await live.disconnect()
  await updateLiveStatus(() => stopClassLive(props.session.id))
}

async function updateLiveStatus(action: () => Promise<ClassSession>) {
  busy.value = true
  try {
    emit('session-change', await action())
  } catch {
    notify.error('直播状态更新失败')
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
      if (props.isTeacher && status === ClassLiveStatus.LIVE) {
        await live.publishDefaults()
      }
    })()
    return
  }
  void live.disconnect()
}

async function loadMessages() {
  if (!canChat.value) {
    return
  }
  try {
    const page = await listClassBarrages(props.session.id, 1, 30)
    messages.value = [...page.records].reverse()
  } catch {
    messages.value = []
  }
}

function connectChat() {
  if (chatSubscription) {
    return
  }
  void loadMessages()
  chatSubscription = subscribeClassBarrages(props.session.id, (message) => {
    messages.value = [...messages.value, message].slice(-80)
  }, loadMessages)
}

function disconnectChat() {
  chatSubscription?.close()
  chatSubscription = null
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
    notify.error('消息发送失败')
  } finally {
    sending.value = false
  }
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}
</script>

<style scoped>
.classroom-live-panel {
  position: fixed;
  top: 24px;
  right: 24px;
  z-index: 1900;
  display: grid;
  width: min(420px, calc(100vw - 32px));
  max-height: calc(100dvh - 48px);
  overflow: hidden;
  border: 1px solid rgb(255 255 255 / 14%);
  border-radius: 18px;
  background: rgb(8 13 27 / 92%);
  box-shadow: 0 24px 70px rgb(0 0 0 / 38%);
  color: #f8fafc;
  backdrop-filter: blur(18px);
}

.live-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 18px 12px;
}

.eyebrow {
  margin: 0 0 4px;
  color: #93c5fd;
  font-family: var(--font-label);
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.live-header h2 {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 500;
}

.icon-button {
  display: inline-grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border: 1px solid rgb(255 255 255 / 14%);
  border-radius: 999px;
  background: rgb(255 255 255 / 8%);
  color: inherit;
  cursor: pointer;
}

.live-stage {
  position: relative;
  aspect-ratio: 16 / 9;
  margin: 0 18px 14px;
  overflow: hidden;
  border: 1px solid rgb(255 255 255 / 12%);
  border-radius: 14px;
  background: linear-gradient(135deg, #111827, #020617);
}

.live-video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.stage-overlay {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgb(2 6 23 / 62%);
  color: #cbd5e1;
  text-align: center;
}

.control-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  padding: 0 18px 16px;
}

.btn-primary,
.btn-secondary,
.btn-danger,
.chat-form button {
  min-height: 38px;
  border-radius: 10px;
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 14px;
}

.btn-primary {
  border: 1px solid #60a5fa;
  background: #2563eb;
  color: white;
}

.btn-secondary {
  border: 1px solid rgb(255 255 255 / 14%);
  background: rgb(255 255 255 / 8%);
  color: white;
}

.btn-danger {
  border: 1px solid rgb(248 113 113 / 55%);
  background: rgb(127 29 29 / 62%);
  color: white;
}

.hint {
  margin: 0 18px 14px;
  color: #fde68a;
  font-size: 13px;
  line-height: 1.5;
}

.chat-panel {
  display: grid;
  min-height: 0;
  border-top: 1px solid rgb(255 255 255 / 10%);
}

.chat-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 14px 18px 8px;
  color: #dbeafe;
  font-family: var(--font-label);
}

.chat-list {
  display: grid;
  max-height: 260px;
  min-height: 120px;
  align-content: start;
  gap: 8px;
  overflow: auto;
  padding: 0 18px 14px;
}

.empty {
  margin: 18px 0;
  color: #94a3b8;
}

.chat-message {
  display: grid;
  gap: 3px;
  padding: 8px 10px;
  border-radius: 10px;
  background: rgb(255 255 255 / 7%);
}

.chat-message span {
  color: #93c5fd;
  font-size: 11px;
}

.chat-message p {
  margin: 0;
  color: #f8fafc;
  line-height: 1.45;
}

.chat-form {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
  padding: 0 18px 18px;
}

.chat-form input {
  min-width: 0;
  height: 40px;
  padding: 0 12px;
  border: 1px solid rgb(255 255 255 / 14%);
  border-radius: 10px;
  background: rgb(255 255 255 / 8%);
  color: white;
}

.chat-form button {
  padding: 0 14px;
  border: 1px solid #60a5fa;
  background: #2563eb;
  color: white;
}

button:disabled,
input:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

@media (max-width: 640px) {
  .classroom-live-panel {
    inset: 12px;
    width: auto;
    max-height: calc(100dvh - 24px);
  }
}
</style>
