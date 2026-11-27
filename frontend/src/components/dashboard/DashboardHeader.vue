<script setup lang="ts">
import ConnectionStatus from './ConnectionStatus.vue'
import type { DashboardConnectionState } from '../../types/dashboard'

defineProps<{
  connectionState: DashboardConnectionState
  returnLabel: string
}>()

defineEmits<{
  return: []
}>()
</script>

<template>
  <header class="dashboard-header">
    <div class="title-block">
      <div class="brand-line">
        <span class="brand-mark">AI</span>
        <span>学科竞赛平台 · 数字大屏</span>
      </div>
      <h1>学科竞赛平台运行总览</h1>
    </div>
    <div class="header-side page-actions">
      <el-button class="return-button page-nav-button" plain @click="$emit('return')">
        {{ returnLabel }}
      </el-button>
      <span class="scope-label">统计范围：全平台</span>
      <ConnectionStatus :state="connectionState" />
    </div>
  </header>
</template>

<style scoped>
.dashboard-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding-bottom: clamp(18px, 1.4vw, 28px);
  border-bottom: 1px solid rgba(223, 195, 122, 0.2);
}

.brand-line {
  display: flex;
  align-items: center;
  gap: 9px;
  color: #93a79f;
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.brand-mark {
  display: inline-grid;
  width: 24px;
  height: 24px;
  place-items: center;
  border: 1px solid rgba(223, 195, 122, 0.55);
  border-radius: 6px;
  color: #dfc37a;
  font-family: Georgia, 'Times New Roman', serif;
  font-size: 11px;
  letter-spacing: 0;
}

h1 {
  margin: 12px 0 0;
  color: #f8f2e5;
  font-family: Georgia, 'Times New Roman', 'Songti SC', serif;
  font-size: clamp(24px, 2.3vw, 42px);
  font-weight: 500;
  letter-spacing: 0.02em;
}

.header-side {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
}

.return-button {
  color: #e7eee9;
  border-color: rgba(231, 238, 233, 0.3);
  background: rgba(255, 255, 255, 0.05);
}

.scope-label {
  color: #9aacA4;
  font-size: 13px;
  white-space: nowrap;
}

@media (max-width: 900px) {
  .dashboard-header,
  .header-side {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
