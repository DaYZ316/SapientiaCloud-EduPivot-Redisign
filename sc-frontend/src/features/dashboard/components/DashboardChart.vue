<template>
  <div class="dashboard-chart-shell">
    <div
        v-if="hasData"
        ref="chartEl"
        class="dashboard-chart"
        :style="{height}"
        role="img"
        :aria-label="ariaLabel"
    ></div>
    <div v-else class="chart-empty" :style="{minHeight: height}">
      {{ emptyText }}
    </div>
  </div>
</template>

<script lang="ts" setup>
import {BarChart, GaugeChart, LineChart, PieChart} from 'echarts/charts'
import {
    GridComponent,
    LegendComponent,
    TooltipComponent,
} from 'echarts/components'
import * as echarts from 'echarts/core'
import type {EChartsCoreOption, EChartsType} from 'echarts/core'
import {SVGRenderer} from 'echarts/renderers'
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'

echarts.use([
    BarChart,
    GaugeChart,
    GridComponent,
    LegendComponent,
    LineChart,
    PieChart,
    SVGRenderer,
    TooltipComponent,
])

const props = withDefaults(defineProps<{
    option: EChartsCoreOption
    hasData?: boolean
    height?: string
    emptyText?: string
    ariaLabel?: string
}>(), {
    hasData: true,
    height: '240px',
    emptyText: undefined,
    ariaLabel: undefined,
})

const {t} = useI18n()
const chartEl = ref<HTMLDivElement | null>(null)
let chart: EChartsType | null = null
let resizeObserver: ResizeObserver | null = null
const emptyText = computed(() => props.emptyText || t('success.dashboard.empty.chart'))
const ariaLabel = computed(() => props.ariaLabel || t('success.dashboard.chart.defaultAria'))

const themePalette = computed(() => {
    if (typeof window === 'undefined') {
        return {
            text: '#f5f5f5',
            muted: '#a0a0a0',
            primary: '#d6ff5f',
            surface: '#171717',
            outline: '#303030',
            grid: '#262626',
        }
    }

    const styles = getComputedStyle(document.documentElement)
    return {
        text: styles.getPropertyValue('--color-on-surface').trim() || '#f5f5f5',
        muted: styles.getPropertyValue('--color-muted').trim() || '#a0a0a0',
        primary: styles.getPropertyValue('--color-primary').trim() || '#d6ff5f',
        surface: styles.getPropertyValue('--color-surface-container').trim() || '#171717',
        outline: styles.getPropertyValue('--color-outline-light').trim() || '#303030',
        grid: styles.getPropertyValue('--color-outline-light').trim() || '#303030',
    }
})

onMounted(() => {
    renderChart()
})

onBeforeUnmount(() => {
    resizeObserver?.disconnect()
    chart?.dispose()
})

watch(
    () => [props.option, props.hasData],
    () => renderChart(),
    {deep: true},
)

async function renderChart() {
    await nextTick()
    if (!props.hasData || !chartEl.value) {
        chart?.dispose()
        chart = null
        return
    }

    if (!chart) {
        chart = echarts.init(chartEl.value, undefined, {renderer: 'svg'})
        resizeObserver = new ResizeObserver(() => chart?.resize())
        resizeObserver.observe(chartEl.value)
    }

    chart.setOption(applyTheme(props.option), true)
}

function applyTheme(option: EChartsCoreOption): EChartsCoreOption {
    const palette = themePalette.value
    const textStyle = toPlainObject(option.textStyle)
    const tooltip = toPlainObject(option.tooltip)
    const tooltipTextStyle = toPlainObject(tooltip.textStyle)
    const legend = toPlainObject(option.legend)

    return {
        ...option,
        color: [
            palette.primary,
            '#8fb7ff',
            '#ffd166',
            '#7bd88f',
            '#ff9aa2',
            '#c7a6ff',
        ],
        textStyle: {
            ...textStyle,
            fontFamily: 'var(--font-label)',
            fontSize: 12,
            color: palette.text,
        },
        tooltip: {
            ...tooltip,
            trigger: 'item',
            backgroundColor: palette.surface,
            borderColor: palette.outline,
            borderWidth: 1,
            padding: [8, 10],
            textStyle: {
                ...tooltipTextStyle,
                fontFamily: 'var(--font-label)',
                fontSize: 12,
                color: palette.text,
            },
        },
        legend: {
            ...legend,
            itemGap: 14,
            itemWidth: 16,
            itemHeight: 8,
            textStyle: {
                fontFamily: 'var(--font-label)',
                fontSize: 12,
                color: palette.muted,
                ...toPlainObject(legend.textStyle),
            },
        },
        grid: applyGridTheme(option.grid),
        series: applySeriesTheme(option.series),
        xAxis: applyAxisTheme(option.xAxis, 'x'),
        yAxis: applyAxisTheme(option.yAxis, 'y'),
    }
}

function applyGridTheme(value: unknown) {
    const grid = toPlainObject(value)
    return {
        top: 18,
        right: 16,
        bottom: 34,
        left: 34,
        containLabel: true,
        ...grid,
    }
}

function applySeriesTheme(value: unknown) {
    if (!Array.isArray(value)) return value

    const palette = themePalette.value
    return value.map((item) => {
        const series = toPlainObject(item)
        const label = toPlainObject(series.label)
        return {
            ...series,
            label: {
                ...label,
                fontFamily: 'var(--font-label)',
                fontSize: 11,
                color: palette.text,
            },
        }
    })
}

function applyAxisTheme(value: unknown, direction: 'x' | 'y') {
    if (Array.isArray(value)) return value.map((item) => applySingleAxisTheme(item, direction))
    if (!value) return value
    return applySingleAxisTheme(value, direction)
}

function applySingleAxisTheme(value: unknown, direction: 'x' | 'y') {
    const palette = themePalette.value
    const axis = toPlainObject(value)
    const axisLabel = toPlainObject(axis.axisLabel)
    const axisLine = toPlainObject(axis.axisLine)
    const axisLineStyle = toPlainObject(axisLine.lineStyle)
    const splitLine = toPlainObject(axis.splitLine)
    const splitLineStyle = toPlainObject(splitLine.lineStyle)

    return {
        ...axis,
        axisLabel: {
            ...axisLabel,
            color: palette.muted,
            fontFamily: 'var(--font-label)',
            fontSize: 11,
            hideOverlap: true,
            margin: direction === 'x' ? 12 : 8,
            overflow: direction === 'x' ? 'truncate' : undefined,
            width: direction === 'x' ? 92 : undefined,
        },
        axisLine: {
            ...axisLine,
            lineStyle: {
                ...axisLineStyle,
                color: palette.outline,
                width: 1,
            },
        },
        axisTick: {
            show: false,
            ...toPlainObject(axis.axisTick),
        },
        splitLine: {
            ...splitLine,
            show: direction === 'y',
            lineStyle: {
                ...splitLineStyle,
                color: palette.grid,
                opacity: 0.68,
                width: 1,
            },
        },
    }
}

function toPlainObject(value: unknown): Record<string, unknown> {
    return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
}
</script>

<style scoped>
.dashboard-chart-shell,
.dashboard-chart {
  min-width: 0;
  width: 100%;
}

.chart-empty {
  display: grid;
  place-items: center;
  border: 1px dashed var(--color-outline-light);
  color: var(--color-muted);
  font-size: 13px;
  text-align: center;
}
</style>

