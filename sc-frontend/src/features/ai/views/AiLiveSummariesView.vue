<template>
  <main class="ai-live-summary-page">
    <section class="live-summary-shell">
      <header class="live-summary-header">
        <h1>{{ t('common.ai.liveSummaries.title') }}</h1>
        <p>{{ t('common.ai.liveSummaries.description') }}</p>
      </header>

      <div
          v-if="summaryLibrary.loading"
          class="summary-state"
      >
        {{ t('common.ai.liveSummaries.loading') }}
      </div>

      <div
          v-else-if="!activeRecord"
          class="summary-state"
      >
        {{ t('common.ai.liveSummaries.empty') }}
      </div>

      <section
          v-else
          class="summary-workspace"
      >
        <header class="summary-overview-band">
          <div>
            <span class="section-label">{{ statusLabel(activeRecord.status) }}</span>
            <h2
                :title="summaryTitle"
                class="summary-title-marquee"
            >
              <span class="summary-title-track">
                <span>{{ summaryTitle }}</span>
                <span aria-hidden="true">{{ summaryTitle }}</span>
              </span>
            </h2>
            <p>{{ currentSummaryInfo }}</p>
          </div>
          <dl class="summary-metrics">
            <div>
              <dt>{{ t('common.ai.liveSummaries.metrics.keyPoints') }}</dt>
              <dd>{{ keyPoints.length }}</dd>
            </div>
            <div>
              <dt>{{ t('common.ai.liveSummaries.metrics.timeline') }}</dt>
              <dd>{{ timelineRows.length }}</dd>
            </div>
            <div>
              <dt>{{ t('common.ai.liveSummaries.metrics.questions') }}</dt>
              <dd>{{ questions.length }}</dd>
            </div>
          </dl>
        </header>

        <nav
            :aria-label="t('common.ai.liveSummaries.tabsAria')"
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

        <section class="summary-detail">
          <div
              v-if="activeTab === 'summary'"
              class="summary-view"
          >
            <article class="overview-block">
              <span class="section-label">{{ t('common.ai.liveSummaries.summary.overview') }}</span>
              <p>{{ snapshot?.overview || t('common.ai.liveSummaries.summary.overviewFallback') }}</p>
            </article>

            <section class="keypoint-section">
              <div class="section-heading">
                <span class="section-label">{{ t('common.ai.liveSummaries.summary.keyPoints') }}</span>
                <strong>{{ keyPoints.length }}</strong>
              </div>
              <div
                  v-if="keyPoints.length"
                  class="keypoint-list"
              >
                <article
                    v-for="(point, index) in keyPoints"
                    :key="`${index}-${point}`"
                >
                  <span class="keypoint-index">{{ String(index + 1).padStart(2, '0') }}</span>
                  <span>{{ point }}</span>
                </article>
              </div>
              <p
                  v-else
                  class="inline-empty"
              >
                {{ t('common.ai.liveSummaries.summary.keyPointEmpty') }}
              </p>
            </section>
          </div>

          <div
              v-else-if="activeTab === 'timeline'"
              class="timeline-view"
          >
            <DashboardTimelineRows
                :empty-text="t('common.ai.liveSummaries.timeline.empty')"
                :items="timelineRows"
            />
          </div>

          <div
              v-else-if="activeTab === 'mindmap'"
              class="mindmap-view"
          >
            <div
                v-if="mindMap"
                ref="chartEl"
                :aria-label="t('common.ai.liveSummaries.mindmap.aria')"
                :style="{minHeight: mindMapHeight}"
                class="mindmap-chart"
                role="img"
            />
            <p
                v-else
                class="empty-state"
            >
              {{ t('common.ai.liveSummaries.mindmap.empty') }}
            </p>
          </div>

          <div
              v-else
              class="questions-view"
          >
            <ol v-if="questions.length">
              <li
                  v-for="question in questions"
                  :key="question"
              >
                {{ question }}
              </li>
            </ol>
            <p
                v-else
                class="empty-state"
            >
              {{ t('common.ai.liveSummaries.questions.empty') }}
            </p>
          </div>
        </section>
      </section>
    </section>
  </main>
</template>

<script lang="ts" setup>
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {TreeChart} from 'echarts/charts'
import {TooltipComponent} from 'echarts/components'
import type {EChartsCoreOption, EChartsType} from 'echarts/core'
import * as echarts from 'echarts/core'
import {SVGRenderer} from 'echarts/renderers'
import {GitFork, HelpCircle, ListTree, Sparkles} from 'lucide-vue-next'

import {useLiveSummaryLibraryStore} from '@/features/ai/stores/liveSummaryLibrary'
import type {LiveSummaryMindMapNode, LiveSummaryTimelineItem,} from '@/features/ai/types/ai'
import DashboardTimelineRows from '@/features/dashboard/components/DashboardTimelineRows.vue'
import {notify} from '@/shared/composables/useGlobalNotification'

echarts.use([SVGRenderer, TooltipComponent, TreeChart])

type TabKey = 'summary' | 'timeline' | 'mindmap' | 'questions'
type StyledMindMapNode = Omit<LiveSummaryMindMapNode, 'children'> & {
  value?: string
  symbolSize?: number
  itemStyle?: Record<string, unknown>
  lineStyle?: Record<string, unknown>
  label?: Record<string, unknown>
  children?: StyledMindMapNode[]
}

const {t, locale} = useI18n()
const summaryLibrary = useLiveSummaryLibraryStore()
const activeTab = ref<TabKey>('summary')
const chartEl = ref<HTMLDivElement | null>(null)

let chart: EChartsType | null = null
let chartHost: HTMLDivElement | null = null
let resizeObserver: ResizeObserver | null = null

const activeRecord = computed(() => summaryLibrary.activeRecord)
const snapshot = computed(() => activeRecord.value?.latestSnapshot ?? null)
const tabs = computed(() => [
  {key: 'summary' as const, label: t('common.ai.liveSummaries.tabs.summary'), icon: Sparkles},
  {key: 'timeline' as const, label: t('common.ai.liveSummaries.tabs.timeline'), icon: ListTree},
  {key: 'mindmap' as const, label: t('common.ai.liveSummaries.tabs.mindmap'), icon: GitFork},
  {key: 'questions' as const, label: t('common.ai.liveSummaries.tabs.questions'), icon: HelpCircle},
])
const keyPoints = computed(() => stringList(snapshot.value?.payload?.keyPoints))
const questions = computed(() => stringList(snapshot.value?.payload?.questions))
const timeline = computed(() => Array.isArray(snapshot.value?.payload?.timeline)
    ? snapshot.value?.payload?.timeline as LiveSummaryTimelineItem[]
    : [])
const timelineRows = computed(() => timeline.value.map((item, index) => ({
  title: item.title || t('common.ai.liveSummaries.timeline.fallbackTitle'),
  detail: item.detail || item.title || '',
  time: timelineTime(item, index),
})))
const mindMap = computed(() => snapshot.value?.payload?.mindMap || null)
const mindMapHeight = computed(() => {
  const nodeCount = mindMap.value ? countMindMapNodes(mindMap.value) : 0
  return `${Math.min(Math.max(520, nodeCount * 38), 820)}px`
})
const summaryTitle = computed(() => snapshot.value?.overview || t('common.ai.liveSummaries.untitled'))
const currentSummaryInfo = computed(() => {
  if (!activeRecord.value || !snapshot.value) {
    return ''
  }
  return [
    t('common.ai.liveSummaries.generatedAt', {time: formatDateTime(snapshot.value.createdAt)}),
    t('common.ai.liveSummaries.transcriptUntil', {sequence: snapshot.value.transcriptUntilSequenceNo}),
  ].join(' / ')
})

onMounted(async () => {
  try {
    await summaryLibrary.ensureLoaded()
  } catch {
    notify.error(t('common.ai.liveSummaries.loadFailed'))
  }
})

onBeforeUnmount(() => {
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
watch(activeRecord, () => {
  activeTab.value = 'summary'
  disposeMindMapChart()
})

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

function timelineTime(item: LiveSummaryTimelineItem, index: number) {
  const value = typeof item.time === 'string' ? item.time.trim() : ''
  if (value && value !== '00:00' && value !== '00.00') {
    return value
  }
  return snapshot.value ? formatTime(snapshot.value.createdAt) : `#${index + 1}`
}

function statusLabel(status: string) {
  if (status === 'RUNNING') return t('common.ai.liveSummaries.status.running')
  if (status === 'STOPPED') return t('common.ai.liveSummaries.status.stopped')
  if (status === 'FAILED') return t('common.ai.liveSummaries.status.failed')
  return t('common.ai.liveSummaries.status.idle')
}

function formatDateTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '--'
  return date.toLocaleString(String(locale.value), {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function formatTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '--:--'
  return date.toLocaleTimeString(String(locale.value), {
    hour: '2-digit',
    minute: '2-digit',
  })
}

function stringList(value: unknown) {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string' && item.trim().length > 0) : []
}
</script>

<style scoped>
.ai-live-summary-page {
  min-height: 100dvh;
  background: var(--color-surface-card);
  color: var(--color-on-surface);
}

.live-summary-shell {
  display: flex;
  min-height: 100dvh;
  flex-direction: column;
  padding: 48px clamp(28px, 6vw, 96px) 32px;
}

.live-summary-header {
  margin-bottom: 32px;
}

.live-summary-header h1 {
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: clamp(44px, 5vw, 72px);
  font-weight: 700;
  line-height: 1;
}

.live-summary-header p {
  max-width: 620px;
  margin: 14px 0 0;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.6;
}

.summary-state {
  width: min(100%, 760px);
  padding: 42px 0;
  border-top: 1px solid var(--color-outline-light);
  border-bottom: 1px solid var(--color-outline-light);
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
}

.summary-workspace {
  display: grid;
  gap: 18px;
  width: min(100%, 1120px);
}

.summary-overview-band {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 360px);
  gap: 28px;
  align-items: end;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--color-outline-light);
}

.summary-overview-band h2 {
  max-width: 100%;
  margin: 6px 0 0;
  overflow: hidden;
  font-family: var(--font-heading);
  font-size: clamp(28px, 3vw, 44px);
  font-weight: 600;
  line-height: 1.14;
  white-space: nowrap;
}

.summary-title-track {
  display: inline-flex;
  min-width: max-content;
  animation: summary-title-marquee 18s linear infinite;
}

.summary-title-track span {
  flex: 0 0 auto;
  padding-right: 56px;
  white-space: nowrap;
}

@keyframes summary-title-marquee {
  to {
    transform: translateX(-50%);
  }
}

.summary-overview-band p {
  margin: 12px 0 0;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
  line-height: 1.6;
}

.section-label {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
  font-weight: 700;
}

.summary-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1px;
  margin: 0;
  overflow: hidden;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  background: var(--color-outline-light);
}

.summary-metrics div {
  display: grid;
  gap: 4px;
  min-width: 0;
  padding: 14px;
  background: var(--color-surface-card);
}

.summary-metrics dt,
.summary-metrics dd {
  margin: 0;
}

.summary-metrics dt {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
}

.summary-metrics dd {
  font-family: var(--font-heading);
  font-size: 28px;
  line-height: 1;
}

.summary-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.summary-tabs button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 38px;
  padding: 0 14px;
  background: transparent;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-muted);
  cursor: pointer;
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 700;
}

.summary-tabs button.active {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-on-primary);
}

.summary-tabs button:hover:not(.active),
.summary-tabs button:focus-visible:not(.active) {
  background: var(--color-surface-canvas);
  color: var(--color-on-surface);
  outline: 0;
}

.summary-detail {
  min-height: 360px;
  padding-top: 8px;
}

.summary-view {
  display: grid;
  grid-template-columns: minmax(0, 0.92fr) minmax(320px, 1.08fr);
  gap: 18px;
}

.overview-block,
.keypoint-section,
.questions-view,
.timeline-view,
.mindmap-view {
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  background: var(--color-surface-card);
}

.overview-block,
.keypoint-section {
  padding: 22px;
}

.overview-block p {
  margin: 14px 0 0;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 16px;
  line-height: 1.75;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.section-heading strong {
  color: var(--color-primary);
  font-family: var(--font-heading);
  font-size: 24px;
  line-height: 1;
}

.keypoint-list {
  display: grid;
  border-top: 1px solid var(--color-outline-light);
}

.keypoint-list article {
  display: grid;
  grid-template-columns: 46px minmax(0, 1fr);
  gap: 14px;
  padding: 14px 0;
  border-bottom: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.65;
}

.keypoint-index {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 800;
}

.timeline-view,
.questions-view,
.mindmap-view {
  padding: 18px;
}

.mindmap-view {
  overflow-x: auto;
}

.questions-view ol {
  display: grid;
  gap: 12px;
  margin: 0;
  padding-left: 22px;
}

.questions-view li {
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 15px;
  line-height: 1.65;
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

.empty-state,
.inline-empty {
  margin: 0;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.6;
}

.inline-empty {
  padding-top: 12px;
  border-top: 1px solid var(--color-outline-light);
}

@media (max-width: 980px) {
  .summary-overview-band,
  .summary-view {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .live-summary-shell {
    padding: 32px 20px 24px;
  }

  .summary-metrics {
    grid-template-columns: 1fr;
  }

  .keypoint-list article {
    grid-template-columns: 36px minmax(0, 1fr);
  }

  .mindmap-view {
    padding: 12px;
  }

  .mindmap-chart {
    width: max(100%, 680px);
    min-height: 480px;
  }
}
</style>
