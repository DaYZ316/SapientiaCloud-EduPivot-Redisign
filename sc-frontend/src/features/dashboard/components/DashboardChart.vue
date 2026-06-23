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
    emptyText: '暂无可视化数据',
    ariaLabel: '仪表盘图表',
})

const chartEl = ref<HTMLDivElement | null>(null)
let chart: EChartsType | null = null
let resizeObserver: ResizeObserver | null = null

const themePalette = computed(() => {
    if (typeof window === 'undefined') {
        return {
            text: '#f5f5f5',
            muted: '#a0a0a0',
            primary: '#d6ff5f',
            surface: '#171717',
            outline: '#303030',
        }
    }

    const styles = getComputedStyle(document.documentElement)
    return {
        text: styles.getPropertyValue('--color-on-surface').trim() || '#f5f5f5',
        muted: styles.getPropertyValue('--color-muted').trim() || '#a0a0a0',
        primary: styles.getPropertyValue('--color-primary').trim() || '#d6ff5f',
        surface: styles.getPropertyValue('--color-surface-container').trim() || '#171717',
        outline: styles.getPropertyValue('--color-outline-light').trim() || '#303030',
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
    const grid = toPlainObject(option.grid)

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
            color: palette.text,
            fontFamily: 'var(--font-label)',
            ...textStyle,
        },
        tooltip: {
            trigger: 'item',
            backgroundColor: palette.surface,
            borderColor: palette.outline,
            textStyle: {
                color: palette.text,
                ...tooltipTextStyle,
            },
            ...tooltip,
        },
        legend: {
            textStyle: {
                color: palette.muted,
            },
            ...legend,
        },
        grid: {
            top: 24,
            right: 16,
            bottom: 28,
            left: 36,
            containLabel: true,
            ...grid,
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
