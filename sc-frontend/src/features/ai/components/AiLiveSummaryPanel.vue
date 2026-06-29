<template>
  <aside
      :aria-busy="isInitialLoading"
      :aria-label="t('courseDetail.classSession.liveSummary.ariaLabel')"
      class="ai-live-summary-panel"
      role="dialog"
  >
    <header class="panel-header">
      <div class="panel-title">
        <h2>{{ t('courseDetail.classSession.liveSummary.title') }}</h2>
        <span
            v-if="isInitialLoading"
            aria-hidden="true"
            class="summary-skeleton panel-title-loader"
        />
        <p v-else>
          {{ currentSummaryInfo }}
        </p>
      </div>
      <div class="panel-actions">
        <button
            :aria-label="t('courseDetail.classSession.liveSummary.close')"
            :title="t('courseDetail.classSession.liveSummary.close')"
            class="icon-button"
            type="button"
            @click="$emit('close')"
        >
          <X
              :size="18"
              stroke-width="1.8"
          />
        </button>
      </div>
    </header>

    <template v-if="isInitialLoading">
      <section
          aria-hidden="true"
          class="status-strip layout-loading-strip"
      >
        <div class="status-copy">
          <span class="summary-skeleton status-dot status-dot-loader"/>
          <div class="loading-copy-lines">
            <span class="summary-skeleton loading-line loading-line-short"/>
            <span class="summary-skeleton loading-line loading-line-long"/>
          </div>
        </div>
        <div class="status-meta loading-meta">
          <span>
            <span class="summary-skeleton loading-metric-number"/>
            <span class="summary-skeleton loading-metric-label"/>
          </span>
          <span>
            <span class="summary-skeleton loading-metric-number"/>
            <span class="summary-skeleton loading-metric-label"/>
          </span>
        </div>
      </section>

      <nav
          aria-hidden="true"
          class="summary-tabs layout-loading-tabs"
      >
        <span
            v-for="index in tabs.length"
            :key="`tab-loader-${index}`"
            class="summary-skeleton tab-loader"
        />
      </nav>

      <main class="panel-body">
        <section
            aria-hidden="true"
            class="summary-layout-loader"
        >
          <article class="overview-block loading-overview">
            <span class="summary-skeleton loading-heading"/>
            <div class="loading-paragraph">
              <span class="summary-skeleton loading-line"/>
              <span class="summary-skeleton loading-line loading-line-wide"/>
              <span class="summary-skeleton loading-line loading-line-medium"/>
            </div>
          </article>
          <div class="loading-summary-grid">
            <section class="keypoint-section loading-keypoints">
              <div class="section-heading">
                <span class="summary-skeleton loading-heading"/>
                <span class="summary-skeleton loading-count"/>
              </div>
              <div class="loading-keypoint-list">
                <article
                    v-for="index in 4"
                    :key="`keypoint-loader-${index}`"
                    class="loading-keypoint-row"
                >
                  <span class="summary-skeleton loading-index"/>
                  <span class="summary-skeleton loading-line"/>
                </article>
              </div>
            </section>
            <section class="question-block loading-questions">
              <span class="summary-skeleton loading-heading"/>
              <div class="loading-question-list">
                <span
                    v-for="index in 3"
                    :key="`question-loader-${index}`"
                    class="summary-skeleton loading-line"
                />
              </div>
            </section>
          </div>
        </section>
      </main>
    </template>

    <template v-else>
      <section
          :class="statusClass"
          class="status-strip"
      >
        <div class="status-copy">
          <span
              aria-hidden="true"
              class="status-dot"
          />
          <div>
            <strong>{{ statusLabel }}</strong>
            <span>{{ statusHint }}</span>
          </div>
        </div>
        <div class="status-meta">
          <span>
            <strong>{{ transcripts.length }}</strong>
            {{ t('courseDetail.classSession.liveSummary.metrics.transcripts') }}
          </span>
          <span>
            <strong>{{ keyPoints.length }}</strong>
            {{ t('courseDetail.classSession.liveSummary.metrics.keyPoints') }}
          </span>
        </div>
      </section>

      <nav
          :aria-label="t('courseDetail.classSession.liveSummary.navAria')"
          class="summary-tabs"
      >
        <button
            v-for="tab in tabs"
            :key="tab.key"
            :aria-current="activeTab === tab.key ? 'page' : undefined"
            :class="{active: activeTab === tab.key}"
            type="button"
            @click="activeTab = tab.key"
        >
          <component
              :is="tab.icon"
              :size="15"
              stroke-width="1.8"
          />
          {{ tab.label }}
        </button>
      </nav>

      <main class="panel-body">
        <section
            v-if="activeTab === 'transcript'"
            aria-live="polite"
            class="transcript-view"
        >
          <article
              v-if="partialTranscript"
              class="transcript-row partial"
          >
            <span>{{ t('courseDetail.classSession.liveSummary.transcript.partial') }}</span>
            <p>{{ partialTranscript.text }}</p>
          </article>
          <article
              v-for="segment in transcripts"
              :key="segmentKey(segment)"
              class="transcript-row"
          >
            <span>{{ transcriptTime(segment) }}</span>
            <p>{{ segment.text }}</p>
          </article>
          <p
              v-if="!partialTranscript && transcripts.length === 0"
              class="empty-state"
          >
            {{
              canManage
                  ? t('courseDetail.classSession.liveSummary.transcript.emptyTeacher')
                  : t('courseDetail.classSession.liveSummary.transcript.emptyStudent')
            }}
          </p>
        </section>

        <section
            v-else-if="activeTab === 'summary'"
            :class="{'has-questions': questions.length}"
            class="summary-view"
        >
          <button
              v-if="selectedHistoryRecord"
              class="latest-summary-button"
              type="button"
              @click="selectedHistoryRecordId = null"
          >
            {{ t('courseDetail.classSession.liveSummary.history.viewLatest') }}
          </button>
          <article class="overview-block">
            <span class="section-label">{{ t('courseDetail.classSession.liveSummary.summary.overview') }}</span>
            <p>{{
                displaySnapshot?.overview || t('courseDetail.classSession.liveSummary.summary.overviewFallback')
              }}</p>
          </article>
          <section class="keypoint-section">
            <div class="section-heading">
              <span class="section-label">{{ t('courseDetail.classSession.liveSummary.summary.keyPoints') }}</span>
              <strong>{{ keyPoints.length }}</strong>
            </div>
            <div
                v-if="keyPoints.length"
                class="keypoint-list"
            >
              <article
                  v-for="(point, index) in keyPoints"
                  :key="point"
              >
                <span class="keypoint-index">{{ String(index + 1).padStart(2, '0') }}</span>
                <span>{{ point }}</span>
              </article>
            </div>
            <p
                v-else
                class="inline-empty"
            >
              {{ t('courseDetail.classSession.liveSummary.summary.keyPointEmpty') }}
            </p>
          </section>
          <section
              v-if="questions.length"
              class="question-block"
          >
            <span class="section-label">{{ t('courseDetail.classSession.liveSummary.summary.questions') }}</span>
            <ol>
              <li
                  v-for="question in questions"
                  :key="question"
              >
                {{ question }}
              </li>
            </ol>
          </section>
        </section>

        <section
            v-else-if="activeTab === 'timeline'"
            class="timeline-view"
        >
          <DashboardTimelineRows
              :empty-text="t('courseDetail.classSession.liveSummary.timeline.empty')"
              :items="timelineRows"
          />
        </section>

        <section
            v-else-if="activeTab === 'history'"
            class="history-view"
        >
          <article
              v-for="record in historyRecords"
              :key="record.summarySessionId"
              :class="{active: selectedHistoryRecord?.summarySessionId === record.summarySessionId}"
              class="history-row"
          >
            <button
                :class="{'history-select-button--with-delete': canDeleteHistory}"
                class="history-select-button"
                type="button"
                @click="selectHistoryRecord(record.summarySessionId)"
            >
              <span>{{ historyRecordLabel(record) }}</span>
              <strong>{{
                  record.latestSnapshot.overview || t('courseDetail.classSession.liveSummary.history.untitled')
                }}</strong>
              <span class="history-meta-row">
                <small>
                  {{ t('courseDetail.classSession.liveSummary.history.updates', {count: record.snapshots.length}) }}
                </small>
                <small>
                  {{
                    t('courseDetail.classSession.liveSummary.history.transcriptUntil', {sequence: record.latestSnapshot.transcriptUntilSequenceNo})
                  }}
                </small>
              </span>
            </button>
            <button
                v-if="canDeleteHistory"
                :disabled="deletingHistoryRecordId === record.summarySessionId"
                class="history-delete-button"
                type="button"
                @click.stop="deleteHistoryRecord(record)"
            >
              <Trash2
                  :size="13"
                  stroke-width="1.8"
              />
              {{ t('courseDetail.classSession.liveSummary.history.delete') }}
            </button>
          </article>
          <p
              v-if="historyRecords.length === 0"
              class="empty-state"
          >
            {{ t('courseDetail.classSession.liveSummary.history.empty') }}
          </p>
        </section>

        <section
            v-else
            class="mindmap-view"
        >
          <div
              v-if="mindMap"
              ref="chartEl"
              :aria-label="t('courseDetail.classSession.liveSummary.mindmap.aria')"
              :style="{minHeight: mindMapHeight}"
              class="mindmap-chart"
              role="img"
          />
          <p
              v-else
              class="empty-state"
          >
            {{ t('courseDetail.classSession.liveSummary.mindmap.empty') }}
          </p>
        </section>
      </main>
    </template>
  </aside>
</template>

<script lang="ts" setup>
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {TreeChart} from 'echarts/charts'
import {TooltipComponent} from 'echarts/components'
import type {EChartsCoreOption, EChartsType} from 'echarts/core'
import * as echarts from 'echarts/core'
import {SVGRenderer} from 'echarts/renderers'
import {GitFork, History, ListTree, ScrollText, Sparkles, Trash2, X,} from 'lucide-vue-next'

import {
  deleteLiveSummaryHistoryRecord,
  getLiveSummary,
  listLiveSummarySnapshots,
  subscribeLiveSummary,
} from '@/features/ai/api/ai'
import {useLiveSummaryStore} from '@/features/ai/stores/liveSummary'
import DashboardTimelineRows from '@/features/dashboard/components/DashboardTimelineRows.vue'
import type {
  LiveSummaryMindMapNode,
  LiveSummarySession,
  LiveSummarySnapshot,
  LiveSummaryTimelineItem,
  LiveTranscriptSegment,
} from '@/features/ai/types/ai'
import type {ClassSession} from '@/features/course/types/classSession'
import type {SseSubscription} from '@/shared/api/sseManager'
import {confirmDialog} from '@/shared/composables/useConfirmDialog'
import {notify} from '@/shared/composables/useGlobalNotification'

echarts.use([SVGRenderer, TooltipComponent, TreeChart])

const props = defineProps<{
  session: ClassSession
  canManage: boolean
  canDeleteHistory: boolean
}>()

defineEmits<{ close: [] }>()

type TabKey = 'transcript' | 'summary' | 'timeline' | 'mindmap' | 'history'
type LiveSummaryHistoryRecord = {
  summarySessionId: string
  latestSnapshot: LiveSummarySnapshot
  snapshots: LiveSummarySnapshot[]
}
type StyledMindMapNode = Omit<LiveSummaryMindMapNode, 'children'> & {
  value?: string
  symbolSize?: number
  itemStyle?: Record<string, unknown>
  lineStyle?: Record<string, unknown>
  label?: Record<string, unknown>
  children?: StyledMindMapNode[]
}

const {t} = useI18n()
const liveSummaryStore = useLiveSummaryStore()

const activeTab = ref<TabKey>('summary')
const selectedHistoryRecordId = ref<string | null>(null)
const hasAppliedInitialHistorySelection = ref(false)
const errorMessage = ref('')
const isInitialLoading = ref(true)
const deletingHistoryRecordId = ref<string | null>(null)
const chartEl = ref<HTMLDivElement | null>(null)

let subscription: SseSubscription | null = null
let chart: EChartsType | null = null
let chartHost: HTMLDivElement | null = null
let resizeObserver: ResizeObserver | null = null

const liveSummaryEntry = computed(() => liveSummaryStore.entryFor(props.session.id))
const summary = computed(() => liveSummaryEntry.value?.session ?? null)
const latestSnapshot = computed(() => liveSummaryEntry.value?.latestSnapshot ?? null)
const snapshotHistory = computed(() => liveSummaryEntry.value?.snapshotHistory ?? [])
const historyRecords = computed<LiveSummaryHistoryRecord[]>(() => {
  const grouped = new Map<string, LiveSummarySnapshot[]>()
  const snapshots = [...snapshotHistory.value].sort((left, right) =>
      snapshotTimestamp(right) - snapshotTimestamp(left) || right.sequenceNo - left.sequenceNo)
  for (const snapshot of snapshots) {
    const items = grouped.get(snapshot.summarySessionId) ?? []
    items.push(snapshot)
    grouped.set(snapshot.summarySessionId, items)
  }
  return [...grouped.entries()].map(([summarySessionId, snapshotsForRecord]) => ({
    summarySessionId,
    latestSnapshot: snapshotsForRecord[0],
    snapshots: snapshotsForRecord,
  }))
})
const selectedHistoryRecord = computed(() =>
    historyRecords.value.find(record => record.summarySessionId === selectedHistoryRecordId.value) ?? null)
const selectedSnapshot = computed(() =>
    selectedHistoryRecord.value?.latestSnapshot ?? null)
const displaySnapshot = computed(() => selectedSnapshot.value ?? latestSnapshot.value)
const historyRecordCount = computed(() =>
    summary.value?.historyRecordCount ?? summary.value?.snapshotCount ?? historyRecords.value.length)
const historyRecordLimit = computed(() =>
    summary.value?.historyRecordLimit ?? summary.value?.snapshotLimit ?? 5)
const transcripts = computed(() => liveSummaryEntry.value?.recentTranscripts ?? [])
const partialTranscript = computed(() => liveSummaryEntry.value?.partialTranscript ?? null)
const isRunning = computed(() => summary.value?.status === 'RUNNING')
const showTranscript = computed(() => isRunning.value)
const tabs = computed(() => [
  ...(showTranscript.value
      ? [{
        key: 'transcript' as const,
        label: t('courseDetail.classSession.liveSummary.tabs.transcript'),
        icon: ScrollText
      }]
      : []),
  {key: 'summary' as const, label: t('courseDetail.classSession.liveSummary.tabs.summary'), icon: Sparkles},
  {key: 'timeline' as const, label: t('courseDetail.classSession.liveSummary.tabs.timeline'), icon: ListTree},
  {key: 'mindmap' as const, label: t('courseDetail.classSession.liveSummary.tabs.mindmap'), icon: GitFork},
  {key: 'history' as const, label: t('courseDetail.classSession.liveSummary.tabs.history'), icon: History},
])
const statusClass = computed(() => ({
  running: isRunning.value,
  stopped: summary.value?.status === 'STOPPED',
  failed: summary.value?.status === 'FAILED' || Boolean(errorMessage.value),
}))
const statusLabel = computed(() => {
  if (errorMessage.value) return t('courseDetail.classSession.liveSummary.status.unavailable')
  if (summary.value?.status === 'RUNNING') {
    return t('courseDetail.classSession.liveSummary.status.running')
  }
  if (summary.value?.status === 'STOPPED') return t('courseDetail.classSession.liveSummary.status.stopped')
  if (summary.value?.status === 'FAILED') return t('courseDetail.classSession.liveSummary.status.failed')
  return t('courseDetail.classSession.liveSummary.status.idle')
})
const statusHint = computed(() => {
  if (errorMessage.value) return errorMessage.value
  if (summary.value?.status === 'RUNNING') return t('courseDetail.classSession.liveSummary.status.runningHint')
  if (summary.value?.status === 'STOPPED') return t('courseDetail.classSession.liveSummary.status.stoppedHint')
  return props.canManage
      ? t('courseDetail.classSession.liveSummary.status.teacherHint')
      : t('courseDetail.classSession.liveSummary.status.studentHint')
})
const currentSummaryInfo = computed(() => {
  const snapshot = displaySnapshot.value
  if (!snapshot) {
    return t('courseDetail.classSession.liveSummary.currentInfo.empty', {
      status: statusLabel.value,
      transcripts: transcripts.value.length,
    })
  }
  const snapshotLabelKey = selectedSnapshot.value
      ? 'courseDetail.classSession.liveSummary.currentInfo.historySnapshot'
      : 'courseDetail.classSession.liveSummary.currentInfo.latestSnapshot'
  return [
    t(snapshotLabelKey, {sequence: snapshot.sequenceNo}),
    t('courseDetail.classSession.liveSummary.history.transcriptUntil', {sequence: snapshot.transcriptUntilSequenceNo}),
    t('courseDetail.classSession.liveSummary.limit.progress', {
      count: historyRecordCount.value,
      limit: historyRecordLimit.value,
    }),
    t('courseDetail.classSession.liveSummary.currentInfo.generatedAt', {time: formatSnapshotTime(snapshot.createdAt)}),
  ].join(' / ')
})
const keyPoints = computed(() => stringList(displaySnapshot.value?.payload?.keyPoints))
const questions = computed(() => stringList(displaySnapshot.value?.payload?.questions))
const timeline = computed(() => Array.isArray(displaySnapshot.value?.payload?.timeline)
    ? displaySnapshot.value?.payload?.timeline as LiveSummaryTimelineItem[]
    : [])
const timelineRows = computed(() => timeline.value.map((item, index) => ({
  title: item.title || t('courseDetail.classSession.liveSummary.timeline.fallbackTitle'),
  detail: item.detail || item.title || '',
  time: timelineTime(item, index),
})))
const mindMap = computed(() => displaySnapshot.value?.payload?.mindMap || null)
const mindMapHeight = computed(() => {
  const nodeCount = mindMap.value ? countMindMapNodes(mindMap.value) : 0
  return `${Math.min(Math.max(520, nodeCount * 38), 820)}px`
})

onMounted(async () => {
  try {
    await Promise.all([
      loadSnapshot(),
      loadSnapshotHistory(),
      waitForLayoutLoader(),
    ])
  } finally {
    isInitialLoading.value = false
  }
  subscribe()
})

onBeforeUnmount(() => {
  subscription?.close()
  disposeMindMapChart()
})

watch(mindMap, () => renderMindMap(), {deep: true})
watch(activeTab, () => {
  if (activeTab.value === 'mindmap') {
    void renderMindMap()
  } else {
    disposeMindMapChart()
  }
})
watch(historyRecords, (next) => {
  if (selectedHistoryRecordId.value && !next.some(record => record.summarySessionId === selectedHistoryRecordId.value)) {
    selectedHistoryRecordId.value = null
  }
  if (!hasAppliedInitialHistorySelection.value && next.length) {
    selectedHistoryRecordId.value = next[0].summarySessionId
    hasAppliedInitialHistorySelection.value = true
  }
}, {immediate: true})
watch(showTranscript, (visible) => {
  if (!visible && activeTab.value === 'transcript') {
    activeTab.value = 'summary'
  }
}, {immediate: true})

async function loadSnapshot() {
  try {
    const next = await getLiveSummary(props.session.id)
    applySession(next)
  } catch (error) {
    errorMessage.value = error instanceof Error
        ? error.message
        : t('courseDetail.classSession.liveSummary.errors.loadFailed')
  }
}

async function refreshSnapshotStatus() {
  try {
    applySession(await getLiveSummary(props.session.id))
  } catch {
    // Keep the updated history even if the quota status refresh fails.
  }
}

async function loadSnapshotHistory() {
  try {
    liveSummaryStore.applySnapshots(props.session.id, await listLiveSummarySnapshots(props.session.id))
  } catch {
    // The live summary panel can still show the latest snapshot from status/SSE.
  }
}

function waitForLayoutLoader() {
  return new Promise(resolve => {
    window.setTimeout(resolve, 420)
  })
}

function subscribe() {
  subscription?.close()
  subscription = subscribeLiveSummary(props.session.id, {
    onStatus: applySession,
    onTranscript: applyTranscript,
    onSnapshot: applySnapshot,
    onError: event => {
      errorMessage.value = event.message || t('courseDetail.classSession.liveSummary.status.unavailable')
    },
    replayOnReconnect: loadSnapshot,
  })
}

async function deleteHistoryRecord(record: LiveSummaryHistoryRecord) {
  if (!props.canDeleteHistory || deletingHistoryRecordId.value) {
    return
  }
  if (!(await confirmDialog({
    message: t('courseDetail.classSession.liveSummary.history.confirmDelete'),
    confirmText: t('courseDetail.classSession.liveSummary.history.delete'),
    confirmVariant: 'danger',
  }))) {
    return
  }
  deletingHistoryRecordId.value = record.summarySessionId
  try {
    const snapshots = await deleteLiveSummaryHistoryRecord(props.session.id, record.summarySessionId)
    liveSummaryStore.replaceSnapshots(props.session.id, snapshots)
    if (selectedHistoryRecordId.value === record.summarySessionId) {
      selectedHistoryRecordId.value = null
    }
    await refreshSnapshotStatus()
    notify.success(t('courseDetail.classSession.liveSummary.history.deleteSuccess'))
  } catch (error) {
    notify.error(error instanceof Error
        ? error.message
        : t('courseDetail.classSession.liveSummary.history.deleteFailed'))
  } finally {
    deletingHistoryRecordId.value = null
  }
}

function applySession(next: LiveSummarySession) {
  liveSummaryStore.applySession(next, {transcriptLimit: 80})
}

function applySnapshot(snapshot: LiveSummarySnapshot) {
  liveSummaryStore.applySnapshot(snapshot)
  activeTab.value = activeTab.value === 'transcript' ? 'transcript' : 'summary'
}

function applyTranscript(segment: LiveTranscriptSegment) {
  liveSummaryStore.applyTranscript(segment, {transcriptLimit: 80})
}

async function renderMindMap() {
  await nextTick()
  const element = chartEl.value
  if (activeTab.value !== 'mindmap' || !element || !mindMap.value) {
    disposeMindMapChart()
    return
  }
  if (!chart || chartHost !== element) {
    disposeMindMapChart()
    chart = echarts.init(element, undefined, {renderer: 'svg'})
    chartHost = element
    resizeObserver = new ResizeObserver(() => {
      requestAnimationFrame(() => chart?.resize())
    })
    resizeObserver.observe(element)
  }
  chart.setOption(mindMapOption(mindMap.value), true)
  requestAnimationFrame(() => chart?.resize())
}

function disposeMindMapChart() {
  resizeObserver?.disconnect()
  resizeObserver = null
  chart?.dispose()
  chart = null
  chartHost = null
}

function mindMapOption(data: LiveSummaryMindMapNode): EChartsCoreOption {
  const palette = readPalette()
  const treeData = decorateMindMapNode(data, 0, 0, palette)
  return {
    color: palette.branchColors,
    tooltip: {
      trigger: 'item',
      triggerOn: 'mousemove',
      confine: true,
      backgroundColor: palette.tooltipBackground,
      borderColor: palette.outline,
      textStyle: {
        color: palette.text,
        fontSize: 12,
        lineHeight: 18,
      },
      extraCssText: 'max-width: 320px; white-space: normal; word-break: break-word;',
      formatter: (params: { data?: { name?: string; value?: string } }) =>
          escapeHtml(params.data?.value || params.data?.name || ''),
    },
    series: [{
      type: 'tree',
      data: [treeData],
      top: 36,
      left: 36,
      bottom: 36,
      right: 176,
      symbol: 'circle',
      orient: 'LR',
      roam: true,
      scaleLimit: {
        min: 0.55,
        max: 2.4,
      },
      edgeShape: 'polyline',
      edgeForkPosition: '52%',
      label: {
        position: 'left',
        verticalAlign: 'middle',
        align: 'right',
        color: palette.text,
        fontSize: 11,
        fontWeight: 600,
        lineHeight: 16,
        width: 132,
        overflow: 'truncate',
        ellipsis: '...',
        backgroundColor: palette.nodeBackground,
        borderColor: palette.nodeBorder,
        borderWidth: 1,
        borderRadius: 6,
        padding: [4, 7],
      },
      leaves: {
        label: {
          position: 'right',
          align: 'left',
          width: 172,
          overflow: 'truncate',
          ellipsis: '...',
          backgroundColor: palette.leafBackground,
          borderColor: palette.nodeBorder,
          borderWidth: 1,
          borderRadius: 6,
          padding: [4, 7],
        },
      },
      lineStyle: {
        color: palette.outline,
        width: 1.4,
        opacity: 0.72,
      },
      itemStyle: {
        color: palette.primary,
        borderColor: palette.surface,
        borderWidth: 2,
      },
      emphasis: {
        focus: 'descendant',
        label: {color: palette.text},
        lineStyle: {width: 2.4, opacity: 1},
      },
      blur: {
        itemStyle: {opacity: 0.28},
        label: {opacity: 0.36},
        lineStyle: {opacity: 0.18},
      },
      expandAndCollapse: true,
      animationDuration: 360,
      animationDurationUpdate: 520,
    }],
  }
}

function readPalette() {
  const styles = getComputedStyle(document.documentElement)
  const text = styles.getPropertyValue('--color-on-surface').trim() || styles.color
  const surface = styles.getPropertyValue('--color-surface-card').trim() || '#141414'
  const surfaceContainer = styles.getPropertyValue('--color-surface-container').trim() || surface
  const surfaceHigh = styles.getPropertyValue('--color-surface-container-high').trim() || surfaceContainer
  const outline = styles.getPropertyValue('--color-outline-light').trim() || text
  return {
    text,
    primary: styles.getPropertyValue('--color-primary').trim() || text,
    primaryContrast: styles.getPropertyValue('--color-surface').trim() || surface,
    surface,
    outline,
    nodeBackground: surfaceHigh,
    leafBackground: surfaceContainer,
    nodeBorder: styles.getPropertyValue('--color-outline-variant').trim() || outline,
    tooltipBackground: styles.getPropertyValue('--color-surface-container-highest').trim() || surfaceHigh,
    branchColors: ['#8dd3c7', '#80b1d3', '#fdb462', '#bebada', '#fb8072', '#b3de69'],
  }
}

function decorateMindMapNode(
    node: LiveSummaryMindMapNode,
    depth: number,
    branchIndex: number,
    palette: ReturnType<typeof readPalette>,
): StyledMindMapNode {
  const branchColor = depth === 0 ? palette.primary : palette.branchColors[branchIndex % palette.branchColors.length]
  return {
    name: node.name,
    value: node.name,
    symbolSize: depth === 0 ? 12 : depth === 1 ? 8 : 6,
    itemStyle: {
      color: branchColor,
      borderColor: depth === 0 ? palette.primaryContrast : palette.surface,
      borderWidth: depth === 0 ? 2.5 : 2,
    },
    lineStyle: {
      color: branchColor,
      width: depth <= 1 ? 1.8 : 1.2,
      opacity: depth <= 1 ? 0.86 : 0.58,
    },
    label: mindMapNodeLabel(depth, branchColor, palette),
    children: node.children?.map((child, index) =>
        decorateMindMapNode(child, depth + 1, depth === 0 ? index : branchIndex, palette)),
  }
}

function mindMapNodeLabel(depth: number, branchColor: string, palette: ReturnType<typeof readPalette>) {
  if (depth === 0) {
    return {
      position: 'right',
      align: 'left',
      color: palette.primaryContrast,
      backgroundColor: palette.text,
      borderColor: palette.text,
      borderWidth: 1,
      borderRadius: 8,
      padding: [6, 10],
      width: 156,
      overflow: 'truncate',
      ellipsis: '...',
      fontSize: 12,
      fontWeight: 800,
      lineHeight: 18,
    }
  }

  if (depth === 1) {
    return {
      color: palette.text,
      backgroundColor: palette.nodeBackground,
      borderColor: branchColor,
      borderWidth: 1,
      borderRadius: 6,
      padding: [4, 7],
      width: 138,
      overflow: 'truncate',
      ellipsis: '...',
      fontSize: 11,
      fontWeight: 700,
      lineHeight: 16,
    }
  }

  return {
    color: palette.text,
    backgroundColor: palette.leafBackground,
    borderColor: palette.nodeBorder,
    borderWidth: 1,
    borderRadius: 6,
    padding: [4, 7],
    width: 172,
    overflow: 'truncate',
    ellipsis: '...',
    fontSize: 11,
    fontWeight: 600,
    lineHeight: 16,
  }
}

function countMindMapNodes(node: LiveSummaryMindMapNode): number {
  return 1 + (node.children?.reduce((total, child) => total + countMindMapNodes(child), 0) ?? 0)
}

function escapeHtml(value: string) {
  return value
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#39;')
}

function transcriptTime(segment: LiveTranscriptSegment) {
  if (segment.beginTimeMs == null) return segment.sequenceNo ? `#${segment.sequenceNo}` : '--:--'
  const seconds = Math.floor(segment.beginTimeMs / 1000)
  return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
}

function timelineTime(item: LiveSummaryTimelineItem, index: number) {
  const value = typeof item.time === 'string' ? item.time.trim() : ''
  if (value && value !== '00:00' && value !== '00.00') {
    return value
  }
  return displaySnapshot.value ? formatSnapshotTime(displaySnapshot.value.createdAt) : `#${index + 1}`
}

function segmentKey(segment: LiveTranscriptSegment) {
  return segment.id || `${segment.sequenceNo}-${segment.text}`
}

function selectHistoryRecord(summarySessionId: string) {
  if (selectedHistoryRecordId.value === summarySessionId) {
    return
  }
  selectedHistoryRecordId.value = summarySessionId
}

function historyRecordLabel(record: LiveSummaryHistoryRecord) {
  return `${formatSnapshotTime(record.latestSnapshot.createdAt)} / #${record.latestSnapshot.sequenceNo}`
}

function snapshotTimestamp(snapshot: LiveSummarySnapshot) {
  const timestamp = new Date(snapshot.createdAt).getTime()
  return Number.isFinite(timestamp) ? timestamp : 0
}

function formatSnapshotTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '--:--'
  }
  return new Intl.DateTimeFormat(undefined, {
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

function stringList(value: unknown) {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string' && item.trim().length > 0) : []
}
</script>

<style scoped>
.ai-live-summary-panel {
  position: fixed;
  top: clamp(20px, 4dvh, 48px);
  left: 50%;
  z-index: 2150;
  display: flex;
  width: min(1040px, calc(100vw - 48px));
  height: min(760px, calc(100dvh - 128px));
  flex-direction: column;
  overflow: hidden;
  background: color-mix(in srgb, var(--color-surface-card) 94%, transparent);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  box-shadow: var(--shadow-card);
  font-family: var(--font-body);
  transform: translateX(-50%);
  backdrop-filter: blur(20px);
}

.panel-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
  gap: 24px;
  padding: 22px 24px 18px;
  border-bottom: 1px solid var(--color-outline-light);
  background: color-mix(in srgb, var(--color-surface-container) 58%, transparent);
}

.panel-title {
  min-width: 0;
}

.panel-title p {
  max-width: 78ch;
  margin: 8px 0 0;
  overflow: hidden;
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  font-weight: 500;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.panel-title-loader {
  display: block;
  width: min(520px, 72%);
  height: 15px;
  margin-top: 12px;
}

.panel-actions {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.panel-header h2 {
  margin: 0;
  overflow-wrap: anywhere;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 32px;
  font-weight: 500;
  letter-spacing: 0;
  line-height: 1.08;
}

.icon-button,
.summary-tabs button {
  cursor: pointer;
  font-family: var(--font-label);
}

.icon-button {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  flex: 0 0 auto;
  transition: background 0.2s,
  border-color 0.2s,
  color 0.2s,
  transform 0.2s;
}

.icon-button:hover {
  background: var(--color-surface-container);
  border-color: var(--color-outline-variant);
}

.icon-button:active,
.summary-tabs button:active {
  transform: translateY(1px);
}

.status-strip {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(260px, 320px);
  gap: 0;
  border-bottom: 1px solid var(--color-outline-light);
  background: var(--color-surface-container);
}

.layout-loading-strip {
  color: var(--color-muted);
}

.status-copy {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 12px;
  min-width: 0;
  align-items: start;
  padding: 16px 24px;
  border-right: 1px solid var(--color-outline-light);
}

.status-dot {
  width: 9px;
  height: 9px;
  margin-top: 5px;
  background: var(--color-muted);
  border-radius: 50%;
  outline: 4px solid color-mix(in srgb, var(--color-muted) 14%, transparent);
}

.status-dot-loader {
  outline: 0;
}

.status-strip.running .status-dot {
  background: var(--color-primary);
  outline-color: color-mix(in srgb, var(--color-primary) 16%, transparent);
}

.status-strip.failed .status-dot {
  background: var(--color-error);
  outline-color: color-mix(in srgb, var(--color-error) 18%, transparent);
}

.status-copy strong,
.status-copy span,
.status-meta span,
.status-meta strong {
  display: block;
}

.status-copy strong {
  margin-bottom: 4px;
  font-size: 14px;
  font-weight: 600;
}

.status-copy span {
  color: var(--color-muted);
  font-size: 12px;
  line-height: 1.4;
}

.status-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0;
  overflow: hidden;
  align-self: stretch;
  background: var(--color-surface-card);
}

.status-meta span {
  display: grid;
  align-content: center;
  min-height: 100%;
  padding: 12px 10px;
  background: var(--color-surface-card);
  border-left: 1px solid var(--color-outline-light);
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
  line-height: 1.25;
  text-align: center;
}

.status-meta strong {
  margin-bottom: 3px;
  color: var(--color-on-surface);
  font-size: 18px;
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.loading-copy-lines,
.loading-paragraph,
.loading-question-list {
  display: grid;
  gap: 8px;
}

.loading-meta span {
  gap: 7px;
  justify-items: center;
}

.loading-metric-number {
  width: 34px;
  height: 18px;
}

.loading-metric-label {
  width: 54px;
  height: 10px;
}

.summary-tabs {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  padding: 0 16px;
  border-bottom: 1px solid var(--color-outline-light);
  background: color-mix(in srgb, var(--color-surface-container) 68%, transparent);
}

.summary-tabs button {
  display: inline-flex;
  position: relative;
  min-width: 0;
  height: 44px;
  align-items: center;
  justify-content: center;
  gap: 6px;
  background: transparent;
  border: 0;
  border-right: 1px solid var(--color-outline-light);
  border-radius: 0;
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 500;
  transition: background 0.2s,
  color 0.2s,
  transform 0.2s;
}

.summary-tabs button:first-child {
  border-left: 1px solid var(--color-outline-light);
}

.summary-tabs button.active {
  background: var(--color-surface-card);
  color: var(--color-on-surface);
}

.summary-tabs button.active::after {
  position: absolute;
  right: 12px;
  bottom: 0;
  left: 12px;
  height: 2px;
  background: var(--color-primary);
  content: '';
}

.summary-tabs button:hover:not(.active) {
  background: var(--color-surface-container-high);
  color: var(--color-on-surface-variant);
}

.layout-loading-tabs {
  align-items: center;
  min-height: 45px;
}

.tab-loader {
  height: 14px;
  margin: 0 auto;
  width: min(74px, 62%);
}

.panel-body {
  min-height: 0;
  flex: 1 1 auto;
  overflow: hidden;
  background: color-mix(in srgb, var(--color-surface-canvas) 18%, transparent);
}

.transcript-view,
.summary-view,
.timeline-view,
.history-view {
  display: grid;
  align-content: start;
  gap: 16px;
  height: 100%;
  overflow-y: auto;
  padding: 20px 24px 24px;
}

.summary-layout-loader {
  display: grid;
  align-content: start;
  gap: 16px;
  height: 100%;
  overflow: hidden;
  padding: 20px 24px 24px;
}

.loading-overview {
  display: grid;
  gap: 14px;
}

.loading-summary-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.12fr) minmax(280px, 0.88fr);
  gap: 16px;
  min-height: 0;
}

.loading-heading {
  width: 112px;
  height: 13px;
}

.loading-count {
  width: 28px;
  height: 13px;
}

.loading-line {
  display: block;
  width: 100%;
  height: 14px;
}

.loading-line-short {
  width: 132px;
}

.loading-line-long {
  width: min(520px, 78%);
}

.loading-line-wide {
  width: 92%;
}

.loading-line-medium {
  width: 64%;
}

.loading-keypoints,
.loading-questions {
  min-height: 220px;
}

.loading-keypoint-list {
  display: grid;
  gap: 0;
  border-top: 1px solid var(--color-outline-light);
}

.loading-keypoint-row {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid var(--color-outline-light);
}

.loading-index {
  width: 24px;
  height: 12px;
}

.summary-view.has-questions {
  grid-template-columns: minmax(0, 1.12fr) minmax(280px, 0.88fr);
}

.summary-view.has-questions .latest-summary-button,
.summary-view.has-questions .overview-block {
  grid-column: 1 / -1;
}

.transcript-row,
.overview-block,
.question-block,
.keypoint-section {
  border: 1px solid var(--color-outline-light);
  border-radius: 0;
  background: color-mix(in srgb, var(--color-surface-container) 88%, transparent);
}

.transcript-view {
  gap: 0;
}

.transcript-row {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
  padding: 14px 0;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--color-outline-light);
  border-radius: 0;
}

.transcript-row.partial {
  margin-bottom: 14px;
  padding: 14px 16px;
  border: 1px dashed var(--color-outline-variant);
  border-style: dashed;
  background: color-mix(in srgb, var(--color-primary-container) 34%, var(--color-surface-container));
}

.transcript-row span {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  font-weight: 500;
}

.transcript-row p,
.overview-block p {
  margin: 0;
  color: var(--color-on-surface);
  font-size: 14px;
  line-height: 1.7;
  text-wrap: pretty;
}

.overview-block {
  padding: 20px;
  border-left-color: var(--color-primary);
}

.overview-block p {
  margin-top: 10px;
  font-size: 16px;
  line-height: 1.72;
}

.section-label,
.section-heading {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 600;
}

.section-label {
  display: inline-block;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-heading strong {
  color: var(--color-on-surface);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.keypoint-section {
  display: grid;
  align-content: start;
  gap: 12px;
  padding: 16px;
}

.keypoint-list {
  display: grid;
  gap: 0;
  border-top: 1px solid var(--color-outline-light);
}

.keypoint-list article {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
  padding: 12px 0;
  border-bottom: 1px solid var(--color-outline-light);
  font-size: 14px;
  line-height: 1.6;
}

.keypoint-index {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.inline-empty {
  margin: 0;
  padding: 22px 0;
  border: 0;
  color: var(--color-muted);
  font-size: 17px;
  font-weight: 500;
  line-height: 1.6;
  text-align: center;
}

.question-block {
  align-self: start;
  padding: 16px;
}

.question-block ol {
  display: grid;
  gap: 10px;
  margin: 10px 0 0;
  padding-left: 22px;
  font-size: 14px;
  line-height: 1.62;
}

.latest-summary-button {
  justify-self: start;
  min-height: 32px;
  padding: 0;
  background: transparent;
  border: 0;
  color: var(--color-muted);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 600;
  text-decoration: underline;
  text-decoration-color: transparent;
  text-underline-offset: 3px;
  transition: color 0.18s,
  text-decoration-color 0.18s;
}

.latest-summary-button:hover,
.latest-summary-button:focus-visible {
  color: var(--color-on-surface-variant);
  outline: none;
  text-decoration-color: currentColor;
}

.history-row {
  display: grid;
  position: relative;
  border: 1px solid var(--color-outline-light);
  border-radius: 0;
  background: color-mix(in srgb, var(--color-surface-container) 88%, transparent);
  transition: background 0.18s,
  border-color 0.18s;
}

.history-row.active {
  border-color: color-mix(in srgb, var(--color-primary) 52%, var(--color-outline-light));
  background: color-mix(in srgb, var(--color-primary-container) 28%, var(--color-surface-container));
}

.history-row:hover,
.history-row:focus-within {
  background: var(--color-surface-container-high);
}

.history-select-button {
  display: grid;
  width: 100%;
  gap: 7px;
  padding: 13px 14px;
  background: transparent;
  border: 0;
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.history-select-button--with-delete {
  padding-right: 88px;
}

.history-meta-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.history-meta-row small {
  min-width: 0;
}

.history-delete-button {
  position: absolute;
  right: 10px;
  bottom: 9px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin: 0;
  padding: 5px 7px;
  background: transparent;
  border: 0;
  color: var(--color-muted);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 600;
  opacity: 0;
  pointer-events: none;
  transition: color 0.18s,
  opacity 0.18s;
}

.history-row:hover .history-delete-button,
.history-row:focus-within .history-delete-button {
  opacity: 1;
  pointer-events: auto;
}

.history-delete-button:hover,
.history-delete-button:focus-visible {
  color: var(--color-error);
  outline: none;
}

.history-delete-button:disabled {
  cursor: progress;
}

.history-row:hover .history-delete-button:disabled,
.history-row:focus-within .history-delete-button:disabled {
  opacity: 0.55;
}

.history-row span,
.history-row small {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 500;
}

.history-row strong {
  display: -webkit-box;
  overflow: hidden;
  color: var(--color-on-surface);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.timeline-view {
  gap: 0;
}

.timeline-view :deep(.timeline-rows) {
  display: grid;
  gap: 0;
}

.timeline-view :deep(.timeline-row) {
  position: relative;
  display: grid;
  grid-template-columns: 12px minmax(0, 1fr) minmax(74px, auto);
  gap: 14px;
  align-items: start;
  min-height: 58px;
  padding: 14px 0;
  border-bottom: 1px solid var(--color-outline-light);
}

.timeline-view :deep(.timeline-row::before) {
  position: absolute;
  top: 26px;
  bottom: -20px;
  left: 4px;
  width: 1px;
  background: var(--color-outline-light);
  content: '';
}

.timeline-view :deep(.timeline-row:last-child) {
  border-bottom: 0;
}

.timeline-view :deep(.timeline-row:last-child::before) {
  display: none;
}

.timeline-view :deep(.timeline-marker) {
  z-index: 1;
  width: 9px;
  height: 9px;
  margin-top: 6px;
  border: 1px solid var(--color-primary);
  background: var(--color-surface-card);
}

.timeline-view :deep(.timeline-row > div) {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.timeline-view :deep(.timeline-row strong) {
  display: block;
  min-width: 0;
  color: var(--color-on-surface);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.35;
}

.timeline-view :deep(.timeline-row small) {
  color: var(--color-on-surface);
  font-size: 14px;
  line-height: 1.7;
  text-wrap: pretty;
}

.timeline-view :deep(.timeline-row time) {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  font-weight: 500;
  text-align: right;
  white-space: nowrap;
}

.timeline-view :deep(.dashboard-empty-state) {
  padding: 56px 0;
  color: var(--color-muted);
  font-size: 17px;
  font-weight: 500;
  line-height: 1.6;
  text-align: center;
}

.mindmap-view {
  display: grid;
  height: 100%;
  min-height: 0;
  overflow-x: auto;
  padding: 18px;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  background: var(--color-surface-card);
}

.mindmap-chart {
  width: max(100%, 760px);
  min-height: 520px;
  overflow: hidden;
  border-radius: var(--radius-sm);
  background: linear-gradient(90deg, color-mix(in srgb, var(--color-outline-light) 42%, transparent) 1px, transparent 1px),
  linear-gradient(color-mix(in srgb, var(--color-outline-light) 42%, transparent) 1px, transparent 1px),
  var(--color-surface-canvas);
  background-size: 48px 48px;
}

.mindmap-view .empty-state {
  align-self: center;
  justify-self: center;
}

.empty-state {
  margin: auto;
  max-width: 32ch;
  padding: 32px 0;
  border: 0;
  color: var(--color-muted);
  font-size: 17px;
  font-weight: 500;
  line-height: 1.6;
  text-align: center;
}

button:focus-visible {
  outline: 1px solid var(--color-primary);
  outline-offset: 3px;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

.summary-skeleton {
  overflow: hidden;
  background: linear-gradient(
      90deg,
      transparent,
      color-mix(in srgb, var(--color-primary) 10%, transparent),
      transparent
  ),
  color-mix(in srgb, var(--color-surface-container-highest) 74%, transparent);
  background-size: 220% 100%, 100% 100%;
  border-radius: 0;
  animation: summary-skeleton-scan 1.4s ease-in-out infinite;
}

@keyframes summary-skeleton-scan {
  from {
    background-position: 120% 0, 0 0;
  }

  to {
    background-position: -120% 0, 0 0;
  }
}

@media (max-width: 920px) {
  .ai-live-summary-panel {
    top: 16px;
    width: calc(100vw - 32px);
    height: calc(100dvh - 104px);
  }

  .status-strip,
  .loading-summary-grid,
  .summary-view.has-questions {
    grid-template-columns: minmax(0, 1fr);
  }

  .status-copy {
    border-right: 0;
    border-bottom: 1px solid var(--color-outline-light);
  }

  .summary-view.has-questions .latest-summary-button,
  .summary-view.has-questions .overview-block {
    grid-column: auto;
  }
}

@media (max-width: 640px) {
  .ai-live-summary-panel {
    inset: 8px;
    width: auto;
    height: calc(100dvh - 16px);
    max-height: calc(100dvh - 16px);
    transform: none;
    border-radius: 0;
    backdrop-filter: blur(14px);
  }

  .panel-header {
    align-items: center;
    gap: 10px;
    padding: 13px 14px 12px;
  }

  .panel-header h2 {
    font-size: 22px;
    line-height: 1.12;
  }

  .panel-title p {
    display: -webkit-box;
    margin-top: 5px;
    font-size: 11px;
    line-height: 1.35;
    white-space: normal;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
  }

  .icon-button {
    width: 34px;
    height: 34px;
  }

  .panel-actions {
    gap: 4px;
  }

  .panel-title-loader {
    width: 78%;
  }

  .panel-body {
    min-height: 0;
    flex: 1 1 0;
  }

  .status-strip {
    grid-template-columns: minmax(0, 1fr);
  }

  .status-copy {
    gap: 10px;
    padding: 11px 14px;
  }

  .status-copy strong {
    margin-bottom: 2px;
    font-size: 13px;
  }

  .status-copy span {
    font-size: 11px;
  }

  .status-dot {
    width: 8px;
    height: 8px;
    margin-top: 4px;
    outline-width: 3px;
  }

  .status-meta {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .status-meta span {
    min-height: 36px;
    padding: 6px 8px;
    border-left: 0;
    border-top: 1px solid var(--color-outline-light);
    font-size: 10px;
    line-height: 1.15;
  }

  .status-meta span + span {
    border-left: 1px solid var(--color-outline-light);
  }

  .status-meta strong {
    margin-bottom: 1px;
    font-size: 14px;
  }

  .summary-tabs {
    display: flex;
    min-height: 40px;
    overflow-x: auto;
    overflow-y: hidden;
    padding: 0;
    scrollbar-width: none;
  }

  .summary-tabs::-webkit-scrollbar {
    display: none;
  }

  .summary-tabs button {
    flex: 0 0 auto;
    gap: 5px;
    height: 40px;
    min-width: 82px;
    padding: 0 12px;
    border-left: 0;
    font-size: 11px;
    line-height: 1;
    white-space: nowrap;
  }

  .summary-tabs button svg {
    flex: 0 0 auto;
  }

  .summary-tabs button:first-child {
    border-left: 0;
  }

  .summary-tabs button.active::after {
    right: 10px;
    left: 10px;
  }

  .transcript-view,
  .summary-view,
  .timeline-view,
  .history-view {
    gap: 12px;
    padding: 14px 12px 12px;
  }

  .summary-layout-loader {
    padding: 12px;
  }

  .overview-block {
    padding: 15px;
  }

  .overview-block p {
    font-size: 14px;
    line-height: 1.66;
  }

  .keypoint-section,
  .question-block {
    padding: 13px;
  }

  .keypoint-list article {
    grid-template-columns: 30px minmax(0, 1fr);
    gap: 8px;
    padding: 10px 0;
    font-size: 13px;
  }

  .question-block ol {
    gap: 8px;
    padding-left: 18px;
    font-size: 13px;
  }

  .transcript-row {
    grid-template-columns: 54px minmax(0, 1fr);
    gap: 10px;
    padding: 12px 0;
  }

  .transcript-row p {
    font-size: 13px;
    line-height: 1.62;
  }

  .timeline-view :deep(.timeline-row) {
    grid-template-columns: 12px minmax(0, 1fr);
    gap: 10px;
    min-height: 0;
    padding: 12px 0;
  }

  .timeline-view :deep(.timeline-row time) {
    grid-column: 2;
    text-align: left;
  }

  .timeline-view :deep(.timeline-row small) {
    font-size: 13px;
    line-height: 1.58;
  }

  .history-select-button {
    gap: 6px;
    padding: 12px 12px 40px;
  }

  .history-select-button--with-delete {
    padding-right: 12px;
  }

  .history-meta-row {
    grid-template-columns: minmax(0, 1fr);
    gap: 4px;
  }

  .history-delete-button {
    right: 8px;
    bottom: 7px;
    padding: 4px 6px;
    font-size: 11px;
    opacity: 1;
    pointer-events: auto;
  }

  .mindmap-view {
    padding: 12px;
  }

  .mindmap-chart {
    width: max(100%, 680px);
    min-height: 480px;
  }

  .inline-empty,
  .timeline-view :deep(.dashboard-empty-state),
  .empty-state {
    padding: 26px 0;
    font-size: 16px;
  }
}
</style>
