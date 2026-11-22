<script setup lang="ts">
import { computed } from 'vue'
import type { DashboardConnectionState } from '../../types/dashboard'

const props = defineProps<{
  state: DashboardConnectionState
}>()

const status = computed(() => {
  const labels: Record<DashboardConnectionState, string> = {
    idle: '等待连接',
    loading: '数据加载中',
    connecting: '正在连接',
    connected: '实时更新中',
    reconnecting: '连接中断，正在重连',
    disconnected: '实时连接中断',
    forbidden: '无访问权限',
    error: '数据加载失败',
  }

  return labels[props.state]
})
</script>

<template>
  <div class="connection-status" :class="`state-${state}`">
    <span class="status-dot" aria-hidden="true"></span>
    <span>{{ status }}</span>
  </div>
</template>

<style scoped>
.connection-status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 30px;
  padding: 0 12px;
  border: 1px solid rgba(223, 195, 122, 0.3);
  border-radius: 999px;
  color: #d5ddda;
  font-size: 13px;
  white-space: nowrap;
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #899691;
}

.state-connected {
  color: #b7e3ca;
  border-color: rgba(101, 189, 139, 0.4);
}

.state-connected .status-dot {
  background: #65bd8b;
  box-shadow: 0 0 10px rgba(101, 189, 139, 0.8);
}

.state-connecting .status-dot,
.state-reconnecting .status-dot,
.state-loading .status-dot {
  background: #dfc37a;
  animation: pulse 1.2s ease-in-out infinite;
}

.state-error,
.state-forbidden {
  color: #f0b5a8;
  border-color: rgba(220, 116, 94, 0.45);
}

.state-error .status-dot,
.state-forbidden .status-dot {
  background: #dc745e;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 0.45;
  }

  50% {
    opacity: 1;
  }
}
</style>
