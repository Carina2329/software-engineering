<script setup lang="ts">
import type { ContestLevel, DashboardDeadline } from '../../types/dashboard'
import { formatShanghaiTime } from '../../views/admin/dashboard/formatShanghaiTime'

defineProps<{
  data: DashboardDeadline[]
}>()

const levelLabels: Record<ContestLevel, string> = {
  SCHOOL: '校级',
  PROVINCE: '省级',
  NATIONAL: '国家级',
}
</script>

<template>
  <section class="deadline-panel">
    <header class="panel-heading">
      <div>
        <span class="eyebrow">NEXT DEADLINES</span>
        <h2>最近 7 天报名截止</h2>
      </div>
      <span class="panel-meta">最多显示 5 条</span>
    </header>

    <div v-if="data.length > 0" class="deadline-list">
      <article v-for="item in data" :key="item.edition_id" class="deadline-item">
        <div class="deadline-main">
          <strong>{{ item.edition_name }}</strong>
          <span>{{ item.contest_name }}</span>
        </div>
        <div class="deadline-side">
          <span class="level-tag">{{ levelLabels[item.level] }}</span>
          <time :datetime="item.register_deadline">
            {{ formatShanghaiTime(item.register_deadline) }}
          </time>
        </div>
      </article>
    </div>
    <div v-else class="empty-state">最近 7 天没有报名截止的届次</div>
  </section>
</template>

<style scoped>
.deadline-panel {
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

.deadline-list {
  display: grid;
  gap: 10px;
  margin-top: 18px;
}

.deadline-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  min-width: 0;
  padding: 12px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.07);
}

.deadline-item:last-child {
  border-bottom: 0;
}

.deadline-main {
  min-width: 0;
}

.deadline-main strong,
.deadline-main span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.deadline-main strong {
  color: #e8eee9;
  font-size: 14px;
  font-weight: 500;
}

.deadline-main span {
  margin-top: 5px;
  color: #91a19a;
  font-size: 12px;
}

.deadline-side {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}

.level-tag {
  padding: 3px 7px;
  border: 1px solid rgba(223, 195, 122, 0.24);
  border-radius: 4px;
  color: #dfc37a;
  font-size: 11px;
}

.deadline-side time {
  color: #d4ded9;
  font-size: 12px;
  white-space: nowrap;
}

.empty-state {
  display: grid;
  min-height: 160px;
  place-items: center;
  color: #91a19a;
  font-size: 14px;
}
</style>
