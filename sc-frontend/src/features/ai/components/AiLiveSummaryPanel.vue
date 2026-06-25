<template>
  <aside class="ai-live-summary-panel">
    <header class="panel-header">
      <div>
        <span class="eyebrow">AI Live Summary</span>
        <h2>课堂实时总结</h2>
      </div>
      <button class="icon-button" title="关闭" type="button" @click="$emit('close')">
        <X :size="18" stroke-width="1.8" />
      </button>
    </header>

    <section class="status-strip" :class="statusClass">
      <div>
        <strong>{{ statusLabel }}</strong>
        <span>{{ statusHint }}</span>
      </div>
      <div v-if="canManage" class="status-actions">
        <button
          v-if="!isRunning"
          class="primary-action"
          type="button"
          :disabled="busy"
          @click="startSummary"
        >
          <Sparkles :size="16" stroke-width="1.8" />
          开启
        </button>
        <button v-else class="secondary-action" type="button" :disabled="busy" @click="stopSummary">
          <Square :size="15" stroke-width="1.9" />
          停止
        </button>
      </div>
    </section>

    <nav class="summary-tabs" aria-label="AI summary views">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        :class="{active: activeTab === tab.key}"
        @click="activeTab = tab.key"
      >
        <component :is="tab.icon" :size="15" stroke-width="1.8" />
        {{ tab.label }}
      </button>
    </nav>

    <main class="panel-body">
      <section v-if="activeTab === 'transcript'" class="transcript-view">
        <article v-if="partialTranscript" class="transcript-row partial">
          <span>识别中</span>
          <p>{{ partialTranscript.text }}</p>
        </article>
        <article v-for="segment in transcripts" :key="segmentKey(segment)" class="transcript-row">
          <span>{{ transcriptTime(segment) }}</span>
          <p>{{ segment.text }}</p>
        </article>
        <p v-if="!partialTranscript && transcripts.length === 0" class="empty-state">
          {{ canManage ? '开启后会实时转写教师麦克风内容。' : '等待教师开启 AI 总结。' }}
        </p>
      </section>

      <section v-else-if="activeTab === 'summary'" class="summary-view">
        <article class="overview-block">
          <span class="eyebrow">Overview</span>
          <p>{{ latestSnapshot?.overview || '摘要会在转写积累后自动更新。' }}</p>
        </article>
        <div class="keypoint-list">
          <article v-for="point in keyPoints" :key="point">
            <CheckCircle2 :size="16" stroke-width="1.8" />
            <span>{{ point }}</span>
          </article>
        </div>
        <section v-if="questions.length" class="question-block">
          <span class="eyebrow">Review Questions</span>
          <ol>
            <li v-for="question in questions" :key="question">{{ question }}</li>
          </ol>
        </section>
      </section>

      <section v-else-if="activeTab === 'timeline'" class="timeline-view">
        <article v-for="item in timeline" :key="`${item.time}-${item.title}`" class="timeline-row">
          <span>{{ item.time || '--:--' }}</span>
          <div>
            <strong>{{ item.title || '阶段摘要' }}</strong>
            <p>{{ item.detail || item.title }}</p>
          </div>
        </article>
        <p v-if="timeline.length === 0" class="empty-state">时间线会随阶段摘要生成。</p>
      </section>

      <section v-else class="mindmap-view">
        <div v-if="mindMap" ref="chartEl" class="mindmap-chart" role="img" aria-label="课堂思维导图"></div>
        <p v-else class="empty-state">思维导图会由结构化摘要生成。</p>
      </section>
    </main>
  </aside>
</template>

<script lang="ts" setup>
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {TreeChart} from 'echarts/charts'
import {TooltipComponent} from 'echarts/components'
import * as echarts from 'echarts/core'
import type {EChartsCoreOption, EChartsType} from 'echarts/core'
import {SVGRenderer} from 'echarts/renderers'
import {
  CheckCircle2,
  GitFork,
  ListTree,
  ScrollText,
  Sparkles,
  Square,
  X,
} from 'lucide-vue-next'

import {
  buildLiveSummaryAudioSocketUrl,
  getLiveSummary,
  issueLiveSummaryAudioToken,
  startLiveSummary,
  stopLiveSummary,
  subscribeLiveSummary,
} from '@/features/ai/api/ai'
import type {
  LiveSummaryMindMapNode,
  LiveSummarySession,
  LiveSummarySnapshot,
  LiveSummaryTimelineItem,
  LiveTranscriptSegment,
} from '@/features/ai/types/ai'
import type {ClassSession} from '@/features/course/types/classSession'
import type {SseSubscription} from '@/shared/api/sseManager'
import {notify} from '@/shared/composables/useGlobalNotification'

echarts.use([SVGRenderer, TooltipComponent, TreeChart])

const props = defineProps<{
  session: ClassSession
  canManage: boolean
}>()

defineEmits<{ close: [] }>()

type TabKey = 'transcript' | 'summary' | 'timeline' | 'mindmap'

const tabs = [
  {key: 'transcript' as const, label: '转写', icon: ScrollText},
  {key: 'summary' as const, label: '摘要', icon: Sparkles},
  {key: 'timeline' as const, label: '时间线', icon: ListTree},
  {key: 'mindmap' as const, label: '导图', icon: GitFork},
]

const activeTab = ref<TabKey>('summary')
const summary = ref<LiveSummarySession | null>(null)
const latestSnapshot = ref<LiveSummarySnapshot | null>(null)
const transcripts = ref<LiveTranscriptSegment[]>([])
const partialTranscript = ref<LiveTranscriptSegment | null>(null)
const busy = ref(false)
const errorMessage = ref('')
const chartEl = ref<HTMLDivElement | null>(null)

let subscription: SseSubscription | null = null
let audioSocket: WebSocket | null = null
let mediaStream: MediaStream | null = null
let audioContext: AudioContext | null = null
let sourceNode: MediaStreamAudioSourceNode | null = null
let processorNode: ScriptProcessorNode | null = null
let chart: EChartsType | null = null
let resizeObserver: ResizeObserver | null = null

const isRunning = computed(() => summary.value?.status === 'RUNNING')
const statusClass = computed(() => ({
  running: isRunning.value,
  stopped: summary.value?.status === 'STOPPED',
  failed: summary.value?.status === 'FAILED' || Boolean(errorMessage.value),
}))
const statusLabel = computed(() => {
  if (errorMessage.value) return 'AI 总结暂不可用'
  if (summary.value?.status === 'RUNNING') return props.canManage && audioSocket ? '正在采集教师语音' : '正在实时总结'
  if (summary.value?.status === 'STOPPED') return '已停止'
  if (summary.value?.status === 'FAILED') return '总结异常'
  return '未开启'
})
const statusHint = computed(() => {
  if (errorMessage.value) return errorMessage.value
  if (summary.value?.status === 'RUNNING') return '最终转写会进入摘要与思维导图。'
  if (summary.value?.status === 'STOPPED') return '可查看本次课堂已生成的摘要。'
  return props.canManage ? '教师可在直播开始后开启。' : '学生打开面板后会自动订阅最新摘要。'
})
const keyPoints = computed(() => stringList(latestSnapshot.value?.payload?.keyPoints))
const questions = computed(() => stringList(latestSnapshot.value?.payload?.questions))
const timeline = computed(() => Array.isArray(latestSnapshot.value?.payload?.timeline)
  ? latestSnapshot.value?.payload?.timeline as LiveSummaryTimelineItem[]
  : [])
const mindMap = computed(() => latestSnapshot.value?.payload?.mindMap || null)

onMounted(async () => {
  await loadSnapshot()
  subscribe()
})

onBeforeUnmount(() => {
  subscription?.close()
  stopAudioUpload()
  resizeObserver?.disconnect()
  chart?.dispose()
})

watch(mindMap, () => renderMindMap(), {deep: true})
watch(activeTab, () => {
  if (activeTab.value === 'mindmap') {
    void renderMindMap()
  }
})

async function loadSnapshot() {
  try {
    const next = await getLiveSummary(props.session.id)
    applySession(next)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '无法读取 AI 总结状态'
  }
}

function subscribe() {
  subscription?.close()
  subscription = subscribeLiveSummary(props.session.id, {
    onStatus: applySession,
    onTranscript: applyTranscript,
    onSnapshot: applySnapshot,
    onError: event => {
      errorMessage.value = event.message || 'AI 总结暂不可用'
    },
    replayOnReconnect: loadSnapshot,
  })
}

async function startSummary() {
  if (!props.canManage || busy.value) return
  busy.value = true
  errorMessage.value = ''
  try {
    applySession(await startLiveSummary(props.session.id))
    await startAudioUpload()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '无法开启 AI 总结'
    notify.warn(errorMessage.value)
  } finally {
    busy.value = false
  }
}

async function stopSummary() {
  if (!props.canManage || busy.value) return
  busy.value = true
  try {
    stopAudioUpload()
    applySession(await stopLiveSummary(props.session.id))
  } catch (error) {
    const message = error instanceof Error ? error.message : '无法停止 AI 总结'
    notify.warn(message)
  } finally {
    busy.value = false
  }
}

async function startAudioUpload() {
  stopAudioUpload()
  const token = await issueLiveSummaryAudioToken(props.session.id)
  const socket = new WebSocket(buildLiveSummaryAudioSocketUrl(props.session.id, token.token))
  socket.binaryType = 'arraybuffer'
  audioSocket = socket
  await waitForSocketOpen(socket)
  mediaStream = await navigator.mediaDevices.getUserMedia({
    audio: {
      channelCount: 1,
      echoCancellation: true,
      noiseSuppression: true,
      autoGainControl: true,
    },
    video: false,
  })
  audioContext = new AudioContext()
  sourceNode = audioContext.createMediaStreamSource(mediaStream)
  processorNode = audioContext.createScriptProcessor(4096, 1, 1)
  processorNode.onaudioprocess = event => {
    if (audioSocket?.readyState !== WebSocket.OPEN || !audioContext) return
    const input = event.inputBuffer.getChannelData(0)
    audioSocket.send(floatTo16kPcm(input, audioContext.sampleRate))
  }
  sourceNode.connect(processorNode)
  processorNode.connect(audioContext.destination)
}

function stopAudioUpload() {
  processorNode?.disconnect()
  sourceNode?.disconnect()
  processorNode = null
  sourceNode = null
  void audioContext?.close()
  audioContext = null
  mediaStream?.getTracks().forEach(track => track.stop())
  mediaStream = null
  if (audioSocket && audioSocket.readyState <= WebSocket.OPEN) {
    audioSocket.close()
  }
  audioSocket = null
}

function waitForSocketOpen(socket: WebSocket) {
  return new Promise<void>((resolve, reject) => {
    socket.onopen = () => resolve()
    socket.onerror = () => reject(new Error('无法连接音频上传通道'))
    socket.onclose = event => {
      if (!event.wasClean && socket.readyState !== WebSocket.OPEN) {
        reject(new Error('音频上传通道已关闭'))
      }
    }
  })
}

function applySession(next: LiveSummarySession) {
  summary.value = next
  latestSnapshot.value = next.latestSnapshot || latestSnapshot.value
  transcripts.value = sortTranscripts(next.recentTranscripts || [])
  if (next.status !== 'RUNNING') {
    partialTranscript.value = null
    stopAudioUpload()
  }
}

function applySnapshot(snapshot: LiveSummarySnapshot) {
  latestSnapshot.value = snapshot
  activeTab.value = activeTab.value === 'transcript' ? 'transcript' : 'summary'
}

function applyTranscript(segment: LiveTranscriptSegment) {
  if (segment.final === false) {
    partialTranscript.value = segment
    return
  }
  partialTranscript.value = null
  if (!segment.text) return
  const next = transcripts.value.filter(item => item.id !== segment.id)
  next.push(segment)
  transcripts.value = sortTranscripts(next).slice(-80)
}

async function renderMindMap() {
  await nextTick()
  if (activeTab.value !== 'mindmap' || !chartEl.value || !mindMap.value) {
    return
  }
  if (!chart) {
    chart = echarts.init(chartEl.value, undefined, {renderer: 'svg'})
    resizeObserver = new ResizeObserver(() => chart?.resize())
    resizeObserver.observe(chartEl.value)
  }
  chart.setOption(mindMapOption(mindMap.value), true)
}

function mindMapOption(data: LiveSummaryMindMapNode): EChartsCoreOption {
  const palette = readPalette()
  return {
    tooltip: {trigger: 'item', triggerOn: 'mousemove'},
    series: [{
      type: 'tree',
      data: [data],
      top: 16,
      left: 8,
      bottom: 16,
      right: 96,
      symbolSize: 8,
      orient: 'LR',
      roam: true,
      label: {
        position: 'left',
        verticalAlign: 'middle',
        align: 'right',
        color: palette.text,
        fontSize: 12,
        width: 96,
        overflow: 'break',
      },
      leaves: {
        label: {
          position: 'right',
          align: 'left',
          width: 120,
          overflow: 'break',
        },
      },
      lineStyle: {color: palette.outline},
      itemStyle: {color: palette.primary, borderColor: palette.primary},
      emphasis: {focus: 'descendant'},
      expandAndCollapse: true,
      animationDuration: 300,
      animationDurationUpdate: 450,
    }],
  }
}

function readPalette() {
  const styles = getComputedStyle(document.documentElement)
  return {
    text: styles.getPropertyValue('--color-on-surface').trim() || '#f8fafc',
    primary: styles.getPropertyValue('--color-primary').trim() || '#93c5fd',
    outline: styles.getPropertyValue('--color-outline-light').trim() || '#334155',
  }
}

function floatTo16kPcm(input: Float32Array, inputSampleRate: number) {
  const targetRate = 16000
  const ratio = inputSampleRate / targetRate
  const outputLength = Math.max(1, Math.floor(input.length / ratio))
  const buffer = new ArrayBuffer(outputLength * 2)
  const view = new DataView(buffer)
  for (let i = 0; i < outputLength; i += 1) {
    const sample = input[Math.floor(i * ratio)] || 0
    const clamped = Math.max(-1, Math.min(1, sample))
    view.setInt16(i * 2, clamped < 0 ? clamped * 0x8000 : clamped * 0x7fff, true)
  }
  return buffer
}

function sortTranscripts(items: LiveTranscriptSegment[]) {
  return [...items].sort((a, b) => (a.sequenceNo ?? 0) - (b.sequenceNo ?? 0))
}

function transcriptTime(segment: LiveTranscriptSegment) {
  if (segment.beginTimeMs == null) return segment.sequenceNo ? `#${segment.sequenceNo}` : '--:--'
  const seconds = Math.floor(segment.beginTimeMs / 1000)
  return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
}

function segmentKey(segment: LiveTranscriptSegment) {
  return segment.id || `${segment.sequenceNo}-${segment.text}`
}

function stringList(value: unknown) {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string' && item.trim().length > 0) : []
}
</script>

<style scoped>
.ai-live-summary-panel {
  position: fixed;
  top: 24px;
  right: 24px;
  bottom: 84px;
  z-index: 2150;
  display: flex;
  width: min(480px, calc(100vw - 32px));
  flex-direction: column;
  overflow: hidden;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline);
  border-radius: var(--radius-md);
  color: var(--color-on-surface);
  box-shadow: var(--shadow-card);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 16px;
  border-bottom: 1px solid var(--color-outline-light);
}

.eyebrow {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.panel-header h2 {
  margin: 5px 0 0;
  font-family: var(--font-heading);
  font-size: 25px;
  font-weight: 400;
}

.icon-button,
.summary-tabs button,
.primary-action,
.secondary-action {
  cursor: pointer;
  font-family: var(--font-label);
}

.icon-button {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  background: transparent;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
}

.status-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--color-outline-light);
  background: var(--color-surface-container);
}

.status-strip strong,
.status-strip span {
  display: block;
}

.status-strip strong {
  margin-bottom: 4px;
  font-size: 14px;
}

.status-strip span {
  color: var(--color-muted);
  font-size: 12px;
  line-height: 1.4;
}

.status-strip.running {
  border-left: 3px solid var(--color-primary);
}

.status-strip.failed {
  border-left: 3px solid #ef4444;
}

.status-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
}

.primary-action,
.secondary-action {
  display: inline-flex;
  height: 34px;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0 11px;
  border-radius: var(--radius-sm);
  font-size: 13px;
}

.primary-action {
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  color: var(--color-on-primary);
}

.secondary-action {
  background: transparent;
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
}

.summary-tabs {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border-bottom: 1px solid var(--color-outline-light);
}

.summary-tabs button {
  display: inline-flex;
  min-width: 0;
  height: 42px;
  align-items: center;
  justify-content: center;
  gap: 6px;
  background: transparent;
  border: 0;
  border-right: 1px solid var(--color-outline-light);
  color: var(--color-muted);
  font-size: 13px;
}

.summary-tabs button:last-child {
  border-right: 0;
}

.summary-tabs button.active {
  background: var(--color-surface-container);
  color: var(--color-on-surface);
}

.panel-body {
  min-height: 0;
  flex: 1 1 auto;
  overflow: hidden;
}

.transcript-view,
.summary-view,
.timeline-view,
.mindmap-view {
  display: grid;
  align-content: start;
  gap: 12px;
  height: 100%;
  overflow-y: auto;
  padding: 16px;
}

.transcript-row,
.timeline-row,
.overview-block,
.question-block {
  border: 1px solid var(--color-outline-light);
  background: var(--color-surface-container);
}

.transcript-row {
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr);
  gap: 10px;
  padding: 12px;
}

.transcript-row.partial {
  border-style: dashed;
}

.transcript-row span,
.timeline-row > span {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
}

.transcript-row p,
.timeline-row p,
.overview-block p {
  margin: 0;
  color: var(--color-on-surface);
  font-size: 14px;
  line-height: 1.58;
}

.overview-block {
  padding: 14px;
}

.overview-block p {
  margin-top: 8px;
  font-size: 15px;
}

.keypoint-list {
  display: grid;
  gap: 8px;
}

.keypoint-list article {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 8px;
  align-items: start;
  padding: 10px 0;
  border-bottom: 1px solid var(--color-outline-light);
  font-size: 14px;
  line-height: 1.5;
}

.question-block {
  padding: 14px;
}

.question-block ol {
  display: grid;
  gap: 8px;
  margin: 10px 0 0;
  padding-left: 20px;
  font-size: 14px;
  line-height: 1.5;
}

.timeline-row {
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr);
  gap: 12px;
  padding: 12px;
}

.timeline-row strong {
  display: block;
  margin-bottom: 5px;
  font-size: 14px;
}

.mindmap-view {
  padding: 0;
}

.mindmap-chart {
  min-height: 100%;
  width: 100%;
}

.empty-state {
  margin: auto;
  max-width: 32ch;
  color: var(--color-muted);
  font-size: 14px;
  line-height: 1.6;
  text-align: center;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

@media (max-width: 640px) {
  .ai-live-summary-panel {
    inset: 12px;
    width: auto;
  }
}
</style>
