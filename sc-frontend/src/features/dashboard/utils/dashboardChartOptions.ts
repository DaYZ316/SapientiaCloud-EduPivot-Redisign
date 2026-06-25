import type {EChartsCoreOption} from 'echarts/core'

import type {ChartDatum} from '@/features/dashboard/utils/dashboardFormatters'

export function donutOption(data: ChartDatum[], name: string): EChartsCoreOption {
    return {
        legend: {
            bottom: 0,
            left: 'center',
        },
        series: [
            {
                name,
                type: 'pie',
                radius: ['54%', '72%'],
                center: ['50%', '43%'],
                avoidLabelOverlap: true,
                label: {
                    formatter: '{b}\n{c}',
                },
                labelLine: {
                    length: 8,
                    length2: 6,
                },
                data: data.filter((item) => item.value > 0).map((item) => ({
                    name: item.label,
                    value: item.value,
                })),
            },
        ],
    }
}

export function barOption(labels: string[], values: number[], name: string, suffix = ''): EChartsCoreOption {
    return {
        tooltip: {
            trigger: 'axis',
            valueFormatter: (value: unknown) => `${value}${suffix}`,
        },
        xAxis: {
            type: 'category',
            data: labels,
            axisLabel: {
                interval: 0,
                rotate: 0,
            },
            axisTick: {
                show: false,
            },
        },
        yAxis: {
            type: 'value',
            axisLabel: {
                formatter: `{value}${suffix}`,
            },
        },
        series: [
            {
                name,
                type: 'bar',
                data: values,
                barMaxWidth: 24,
                barMinHeight: 3,
                itemStyle: {
                    borderRadius: [3, 3, 0, 0],
                },
            },
        ],
    }
}

export function lineOption(labels: string[], values: number[], name: string, suffix = ''): EChartsCoreOption {
    return {
        tooltip: {
            trigger: 'axis',
            valueFormatter: (value: unknown) => `${value}${suffix}`,
        },
        xAxis: {
            type: 'category',
            boundaryGap: false,
            data: labels,
            axisLabel: {
                interval: 0,
            },
            axisTick: {
                show: false,
            },
        },
        yAxis: {
            type: 'value',
            axisLabel: {
                formatter: `{value}${suffix}`,
            },
        },
        series: [
            {
                name,
                type: 'line',
                smooth: true,
                symbolSize: 6,
                areaStyle: {
                    opacity: 0.08,
                },
                data: values,
            },
        ],
    }
}

