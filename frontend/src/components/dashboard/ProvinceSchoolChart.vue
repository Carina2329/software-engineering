<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { ECharts, EChartsCoreOption } from 'echarts/core'
import type { DashboardProvinceSchool } from '../../types/dashboard'

echarts.use([BarChart, GridComponent, TooltipComponent, CanvasRenderer])

const props = defineProps<{
  data: DashboardProvinceSchool[]
}>()

const chartElement = ref<HTMLDivElement | null>(null)
let chart: ECharts | null = null
let resizeObserver: ResizeObserver | null = null

function renderChart() {
  if (!chart) {
    return
  }

  const rows = [...props.data].reverse()
  const option: EChartsCoreOption = {
    animation: false,
    backgroundColor: 'transparent',
    grid: { top: 16, right: 22, bottom: 24, left: 74 },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
    },
    xAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#95a69f', fontSize: 12 },
      splitLine: { lineStyle: { color: 'rgba(223, 195, 122, 0.1)' } },
    },
    yAxis: {
      type: 'category',
      data: rows.map((item) => item.province_name),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#d7dfdc', fontSize: 13 },
    },
    series: [
      {
        name: '学校数量',
        type: 'bar',
        data: rows.map((item) => item.school_count),
        barWidth: 14,
        itemStyle: {
          borderRadius: [0, 8, 8, 0],
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 1,
            y2: 0,
            colorStops: [
              { offset: 0, color: '#6d9f8a' },
              { offset: 1, color: '#dfc37a' },
            ],
          },
        },
      },
    ],
  }

  chart.setOption(option, true)
}

onMounted(() => {
  if (!chartElement.value) {
    return
  }

  chart = echarts.init(chartElement.value)
  renderChart()
  resizeObserver = new ResizeObserver(() => chart?.resize())
  resizeObserver.observe(chartElement.value)
})

watch(() => props.data, renderChart, { deep: true })

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  chart?.dispose()
  chart = null
})
</script>

<template>
  <section class="chart-panel">
    <header class="panel-heading">
      <div>
        <span class="eyebrow">GEOGRAPHIC COVERAGE</span>
        <h2>省份学校分布</h2>
      </div>
      <span class="panel-meta">按有效学校数排序</span>
    </header>
    <div class="chart-body">
      <div ref="chartElement" class="chart-canvas"></div>
      <div v-if="data.length === 0" class="empty-state">暂无有效学校数据</div>
    </div>
  </section>
</template>

<style scoped>
.chart-panel {
  min-width: 0;
  padding: clamp(16px, 1.3vw, 24px);
  border: 1px solid rgba(223, 195, 122, 0.18);
  border-radius: 18px;
  background: rgba(10, 30, 27, 0.7);
}

.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.eyebrow {
  color: #8aa99c;
  font-size: 10px;
  letter-spacing: 0.16em;
}

h2 {
  margin: 5px 0 0;
  color: #f5f0e4;
  font-size: clamp(16px, 1.15vw, 21px);
  font-weight: 600;
}

.panel-meta {
  color: #91a19a;
  font-size: 12px;
  white-space: nowrap;
}

.chart-body {
  position: relative;
  height: clamp(240px, 24vh, 360px);
  margin-top: 12px;
}

.chart-canvas {
  width: 100%;
  height: 100%;
}

.empty-state {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: #91a19a;
  font-size: 14px;
}
</style>
