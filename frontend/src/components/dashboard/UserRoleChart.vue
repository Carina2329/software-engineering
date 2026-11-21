<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { PieChart } from 'echarts/charts'
import { LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { ECharts, EChartsCoreOption } from 'echarts/core'
import type { DashboardUserRole } from '../../types/dashboard'

echarts.use([PieChart, LegendComponent, TooltipComponent, CanvasRenderer])

const props = defineProps<{
  data: DashboardUserRole[]
}>()

const chartElement = ref<HTMLDivElement | null>(null)
let chart: ECharts | null = null
let resizeObserver: ResizeObserver | null = null

function renderChart() {
  if (!chart) {
    return
  }

  const option: EChartsCoreOption = {
    animation: false,
    backgroundColor: 'transparent',
    color: ['#dfc37a', '#6d9f8a', '#8fb6c8', '#d68c76', '#a69acb'],
    tooltip: {
      trigger: 'item',
      formatter: '{b}<br/>用户数：{c}（{d}%）',
    },
    legend: {
      bottom: 0,
      left: 'center',
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: '#b8c4bf', fontSize: 12 },
    },
    series: [
      {
        name: '用户角色',
        type: 'pie',
        radius: ['48%', '70%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: true,
        itemStyle: {
          borderColor: '#102b26',
          borderWidth: 3,
          borderRadius: 4,
        },
        label: {
          color: '#d8e2de',
          fontSize: 12,
          formatter: '{b}\n{c}',
        },
        labelLine: {
          lineStyle: { color: 'rgba(223, 195, 122, 0.5)' },
        },
        data: props.data.map((item) => ({
          name: item.role_name,
          value: item.user_count,
        })),
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
        <span class="eyebrow">AUDIENCE MIX</span>
        <h2>用户角色构成</h2>
      </div>
      <span class="panel-meta">按有效角色授权统计</span>
    </header>
    <div class="chart-body">
      <div ref="chartElement" class="chart-canvas"></div>
      <div v-if="data.length === 0" class="empty-state">暂无有效角色数据</div>
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
