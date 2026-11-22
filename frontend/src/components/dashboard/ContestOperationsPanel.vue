<script setup lang="ts">
import { computed } from 'vue'
import type { ContestLevel, DashboardContestOperations } from '../../types/dashboard'

const props = defineProps<{
  data: DashboardContestOperations
}>()

const levelLabels: Record<ContestLevel, string> = {
  SCHOOL: '校级',
  PROVINCE: '省级',
  NATIONAL: '国家级',
}

const levelRows = computed(() =>
  props.data.level_distribution.map((item) => ({
    ...item,
    label: levelLabels[item.level],
  })),
)
</script>

<template>
  <section class="operation-panel">
    <header class="panel-heading">
      <div>
        <span class="eyebrow">CONTEST OPERATIONS</span>
        <h2>赛事运行摘要</h2>
      </div>
      <span class="panel-meta">全部未删除届次（包含草稿）</span>
    </header>

    <div class="operation-summary">
      <div class="operation-number">
        <span>竞赛系列</span>
        <strong>{{ data.contest_series_count }}</strong>
      </div>
      <div class="operation-number">
        <span>已发布届次</span>
        <strong>{{ data.published_edition_count }}</strong>
      </div>
    </div>

    <div class="level-list">
      <div v-for="item in levelRows" :key="item.level" class="level-row">
        <span>{{ item.label }}</span>
        <div class="level-track">
          <span
            class="level-fill"
            :style="{ width: `${data.published_edition_count > 0 ? (item.count / data.published_edition_count) * 100 : 0}%` }"
          ></span>
        </div>
        <strong>{{ item.count }}</strong>
      </div>
    </div>
  </section>
</template>

<style scoped>
.operation-panel {
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

.operation-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.operation-number {
  padding: 14px;
  border: 1px solid rgba(223, 195, 122, 0.14);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.035);
}

.operation-number span {
  display: block;
  color: #aebdb6;
  font-size: 12px;
}

.operation-number strong {
  display: block;
  margin-top: 8px;
  color: #dfc37a;
  font-family: Georgia, 'Times New Roman', serif;
  font-size: clamp(26px, 2vw, 38px);
  line-height: 1;
}

.level-list {
  display: grid;
  gap: 15px;
  margin-top: 22px;
}

.level-row {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr) 24px;
  align-items: center;
  gap: 10px;
  color: #b9c6c1;
  font-size: 13px;
}

.level-track {
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
}

.level-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #6d9f8a, #dfc37a);
}

.level-row strong {
  color: #f1eadb;
  text-align: right;
}
</style>
