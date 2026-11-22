<script setup lang="ts">
withDefaults(
  defineProps<{
    label: string
    value: number | string
    hint?: string
    tone?: 'green' | 'gold' | 'blue' | 'red'
  }>(),
  {
    hint: '',
    tone: 'green',
  },
)
</script>

<template>
  <article class="metric-card" :class="`tone-${tone}`">
    <div class="metric-label">{{ label }}</div>
    <div class="metric-value">{{ value }}</div>
    <div v-if="hint" class="metric-hint">{{ hint }}</div>
  </article>
</template>

<style scoped>
.metric-card {
  position: relative;
  min-width: 0;
  padding: clamp(16px, 1.2vw, 24px);
  overflow: hidden;
  border: 1px solid rgba(223, 195, 122, 0.2);
  border-radius: 18px;
  background: linear-gradient(145deg, rgba(25, 57, 49, 0.86), rgba(13, 35, 31, 0.9));
  box-shadow: 0 18px 45px rgba(0, 0, 0, 0.16);
}

.metric-card::after {
  position: absolute;
  right: -22px;
  bottom: -28px;
  width: 110px;
  height: 110px;
  border: 1px solid rgba(223, 195, 122, 0.12);
  border-radius: 50%;
  content: '';
}

.metric-label {
  color: #b7c5c0;
  font-size: clamp(12px, 0.9vw, 16px);
}

.metric-value {
  margin-top: 10px;
  color: #f7f2e7;
  font-family: Georgia, 'Times New Roman', serif;
  font-size: clamp(30px, 2.6vw, 52px);
  line-height: 1;
}

.metric-hint {
  margin-top: 10px;
  color: #899b94;
  font-size: 12px;
}

.tone-gold .metric-value {
  color: #dfc37a;
}

.tone-blue .metric-value {
  color: #9bc8dd;
}

.tone-red .metric-value {
  color: #edb4a6;
}
</style>
