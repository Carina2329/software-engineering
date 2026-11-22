<script setup lang="ts">
import { computed, onMounted } from 'vue'
import ContestOperationsPanel from '../../components/dashboard/ContestOperationsPanel.vue'
import ConnectionStatus from '../../components/dashboard/ConnectionStatus.vue'
import DashboardHeader from '../../components/dashboard/DashboardHeader.vue'
import MetricCard from '../../components/dashboard/MetricCard.vue'
import ProvinceSchoolChart from '../../components/dashboard/ProvinceSchoolChart.vue'
import UpcomingDeadlineList from '../../components/dashboard/UpcomingDeadlineList.vue'
import UserRoleChart from '../../components/dashboard/UserRoleChart.vue'
import type { DashboardContestOperations } from '../../types/dashboard'
import { formatShanghaiTime } from './dashboard/formatShanghaiTime'
import { useDashboardSocket } from './dashboard/useDashboardSocket'

const { snapshot, connectionState, errorMessage, start, retry } = useDashboardSocket()

const summary = computed(() => snapshot.value?.summary)
const contestOperations = computed<DashboardContestOperations>(
  () =>
    snapshot.value?.contest_operations ?? {
      contest_series_count: 0,
      published_edition_count: 0,
      level_distribution: [],
    },
)
const generatedAt = computed(() => snapshot.value?.generated_at ?? '')
const dataSource = computed(() => snapshot.value?.data_source ?? '平台组织、用户及竞赛届次业务库')
const provinceSchools = computed(() => snapshot.value?.province_schools ?? [])
const userRoles = computed(() => snapshot.value?.user_roles ?? [])
const upcomingDeadlines = computed(() => snapshot.value?.upcoming_deadlines ?? [])
const initialLoading = computed(
  () => !snapshot.value && (connectionState.value === 'idle' || connectionState.value === 'loading'),
)
const showInitialError = computed(() => !snapshot.value && connectionState.value !== 'loading')
const isForbidden = computed(() => connectionState.value === 'forbidden')

function handleRetry() {
  void retry()
}

onMounted(() => {
  void start()
})
</script>

<template>
  <div class="dashboard-shell">
    <div class="ambient ambient-one"></div>
    <div class="ambient ambient-two"></div>

    <main class="dashboard-frame">
      <DashboardHeader :connection-state="connectionState" />

      <section v-if="initialLoading" class="initial-state" aria-live="polite">
        <div class="loading-orbit"><span></span><span></span><span></span></div>
        <p>正在建立平台运行数据视图</p>
        <small>首次快照加载完成后将自动建立实时连接</small>
      </section>

      <section v-else-if="showInitialError" class="initial-state error-state" aria-live="polite">
        <div class="state-mark">!</div>
        <p>{{ isForbidden ? '当前账号无权查看平台运行总览' : errorMessage || '数据加载失败' }}</p>
        <small v-if="!isForbidden">请检查后端服务和数据库连接后重试。</small>
        <el-button v-if="!isForbidden" class="retry-button" plain @click="handleRetry">
          重新加载
        </el-button>
      </section>

      <template v-else>
        <section class="metrics-grid" aria-label="核心指标">
          <MetricCard
            label="有效学校数"
            :value="summary?.effective_school_count ?? '—'"
            hint="已启用且归属省份有效"
            tone="gold"
          />
          <MetricCard
            label="有效用户数"
            :value="summary?.active_user_count ?? '—'"
            hint="启用且未删除用户"
            tone="green"
          />
          <MetricCard
            label="当前开放报名届次"
            :value="summary?.registering_edition_count ?? '—'"
            hint="状态 REGISTERING 且未截止"
            tone="blue"
          />
          <MetricCard
            label="待审核学校归属"
            :value="summary?.pending_affiliation_count ?? '—'"
            hint="等待管理员处理"
            tone="red"
          />
        </section>

        <section class="charts-grid" aria-label="分布图表">
          <ProvinceSchoolChart :data="provinceSchools" />
          <UserRoleChart :data="userRoles" />
        </section>

        <section class="operations-grid" aria-label="赛事运行">
          <ContestOperationsPanel :data="contestOperations" />
          <UpcomingDeadlineList :data="upcomingDeadlines" />
        </section>

        <div v-if="errorMessage" class="inline-alert">
          <ConnectionStatus :state="connectionState" />
          <span>{{ errorMessage }}</span>
          <el-button link type="warning" @click="handleRetry">重试</el-button>
        </div>

        <footer class="dashboard-footer">
          <span>数据更新时间：{{ formatShanghaiTime(generatedAt) }}</span>
          <span>数据来源：{{ dataSource }}</span>
        </footer>
      </template>
    </main>
  </div>
</template>

<style scoped>
.dashboard-shell {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  color: #e7efeb;
  background:
    radial-gradient(circle at 8% 0%, rgba(68, 127, 105, 0.2), transparent 32rem),
    radial-gradient(circle at 92% 100%, rgba(189, 145, 62, 0.14), transparent 30rem),
    #081c19;
}

.ambient {
  position: absolute;
  pointer-events: none;
  border: 1px solid rgba(223, 195, 122, 0.1);
  border-radius: 50%;
}

.ambient-one {
  top: -22rem;
  right: 8%;
  width: 42rem;
  height: 42rem;
}

.ambient-two {
  bottom: -27rem;
  left: 20%;
  width: 50rem;
  height: 50rem;
}

.dashboard-frame {
  position: relative;
  width: min(100%, 2400px);
  min-height: 100vh;
  margin: 0 auto;
  padding: clamp(24px, 2vw, 44px) clamp(20px, 2.2vw, 48px) 20px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: clamp(12px, 1vw, 18px);
  margin-top: 20px;
}

.charts-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(320px, 0.85fr);
  gap: clamp(12px, 1vw, 18px);
  margin-top: 16px;
}

.operations-grid {
  display: grid;
  grid-template-columns: minmax(300px, 0.75fr) minmax(0, 1.25fr);
  gap: clamp(12px, 1vw, 18px);
  margin-top: 16px;
}

.initial-state {
  display: grid;
  min-height: calc(100vh - 190px);
  place-content: center;
  justify-items: center;
  gap: 12px;
  color: #b5c6be;
  text-align: center;
}

.initial-state p {
  margin: 0;
  color: #e8efe9;
  font-size: 17px;
}

.initial-state small {
  color: #7f958b;
  font-size: 12px;
}

.loading-orbit {
  display: flex;
  gap: 8px;
  align-items: center;
  height: 24px;
}

.loading-orbit span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #dfc37a;
  animation: orbit 1.1s ease-in-out infinite;
}

.loading-orbit span:nth-child(2) {
  animation-delay: 0.12s;
}

.loading-orbit span:nth-child(3) {
  animation-delay: 0.24s;
}

.state-mark {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border: 1px solid rgba(220, 116, 94, 0.6);
  border-radius: 50%;
  color: #edb4a6;
  font-family: Georgia, 'Times New Roman', serif;
  font-size: 24px;
}

.retry-button {
  margin-top: 8px;
  color: #dfc37a;
  border-color: rgba(223, 195, 122, 0.5);
  background: transparent;
}

.inline-alert {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
  padding: 9px 12px;
  border: 1px solid rgba(220, 116, 94, 0.25);
  border-radius: 8px;
  color: #d9b0a6;
  background: rgba(95, 38, 31, 0.22);
  font-size: 12px;
}

.inline-alert :deep(.connection-status) {
  flex: 0 0 auto;
}

.dashboard-footer {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-top: 20px;
  padding-top: 14px;
  border-top: 1px solid rgba(223, 195, 122, 0.14);
  color: #788e85;
  font-size: 12px;
}

.dashboard-footer span:last-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@keyframes orbit {
  0%,
  100% {
    opacity: 0.35;
    transform: translateY(0);
  }

  50% {
    opacity: 1;
    transform: translateY(-5px);
  }
}

@media (max-width: 1300px) {
  .metrics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .charts-grid,
  .operations-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 720px) {
  .dashboard-frame {
    padding: 18px 14px;
  }

  .metrics-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .dashboard-footer {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
