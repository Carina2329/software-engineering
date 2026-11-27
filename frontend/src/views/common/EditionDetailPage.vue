<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getContest,
  getContestEdition,
  getContestErrorMessage,
  listEditionMilestones,
} from '../../api/contest'
import {
  EDITION_STATUS_LABELS,
  MILESTONE_NODE_OPTIONS,
  TOPIC_MODE_OPTIONS,
  formatContestDateTime,
} from '../../types/contest'
import type { ContestDetail, EditionDetail, Milestone } from '../../types/contest'
import { useContextualReturn } from '../../composables/useContextualReturn'
import { useAuthStore } from '../../stores/auth'
import { useCurrentMenus } from '../../layouts/useCurrentMenus'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { ensureCurrentMenus, hasPermission } = useCurrentMenus()
const { returnLabel, handleReturn } = useContextualReturn({
  defaultLabel: '返回竞赛中心',
  defaultFallback: '/contests',
  matchesPrevious: (path) => path.startsWith('/contests'),
})
const loading = ref(false)
const edition = ref<EditionDetail | null>(null)
const contest = ref<ContestDetail | null>(null)
const milestones = ref<Milestone[]>([])
const editionId = computed(() => {
  const value = route.params.editionId
  return Array.isArray(value) ? String(value[0] ?? '') : String(value ?? '')
})
const canOpenTeamCenter = computed(
  () => auth.hasRole('STUDENT') && hasPermission('team:list'),
)
const canOpenTeacherApproval = computed(
  () => auth.hasRole('TEACHER') && hasPermission('team:approve'),
)
const canOpenAdminApproval = computed(
  () => auth.hasRole('ADMIN') && hasPermission('team:approve'),
)
const canManageEligibility = computed(
  () => auth.hasRole('ADMIN') && hasPermission('team:eligibility:manage'),
)

const load = async (): Promise<void> => {
  if (!editionId.value) return
  loading.value = true
  try {
    const detail = await getContestEdition(editionId.value)
    edition.value = detail
    const [contestDetail, milestoneList] = await Promise.all([
      getContest(detail.contest_id),
      listEditionMilestones(detail.id),
    ])
    contest.value = contestDetail
    milestones.value = milestoneList
  } catch (error) {
    ElMessage.error(getContestErrorMessage(error, '届次详情加载失败'))
  } finally {
    loading.value = false
  }
}

const topicModeLabel = computed(() => {
  if (!edition.value?.topic_mode) return '尚未配置'
  return TOPIC_MODE_OPTIONS.find((item) => item.value === edition.value?.topic_mode)?.label
    ?? edition.value.topic_mode
})

const teamRuleText = computed(() => {
  if (!edition.value) return ''
  return edition.value.team_size_mode === 'FIXED'
    ? `固定 ${edition.value.min_team_size} 人`
    : `${edition.value.min_team_size}–${edition.value.max_team_size} 人`
})

onMounted(async () => {
  await ensureCurrentMenus().catch(() => undefined)
  await load()
})
</script>

<template>
  <main v-loading="loading" class="edition-detail-page">
    <header class="detail-header">
      <div>
        <p class="eyebrow">{{ contest?.contest_name || '竞赛届次' }}</p>
        <h1>{{ edition?.edition_name || '届次详情' }}</h1>
        <div v-if="edition" class="header-tags">
          <el-tag type="success" effect="dark">{{ EDITION_STATUS_LABELS[edition.status] }}</el-tag>
          <el-tag effect="plain">{{ edition.year }} 年</el-tag>
          <el-tag v-if="edition.read_only" type="danger">只读</el-tag>
        </div>
      </div>
      <div v-if="edition" class="header-actions page-actions">
        <el-button class="page-nav-button" plain @click="handleReturn">{{ returnLabel }}</el-button>
        <el-button
          v-if="canOpenTeamCenter"
          type="primary"
          plain
          @click="router.push({ path: '/teams', query: { edition_id: edition.id } })"
        >
          报名组队
        </el-button>
        <el-button
          v-if="canOpenTeacherApproval"
          type="warning"
          plain
          @click="router.push({ path: '/team-approvals', query: { edition_id: edition.id } })"
        >
          待我确认
        </el-button>
        <el-button
          v-if="canOpenAdminApproval"
          type="warning"
          plain
          @click="router.push({ path: '/admin/team-approvals', query: { edition_id: edition.id } })"
        >
          报名终审
        </el-button>
        <el-button
          v-if="canManageEligibility"
          type="success"
          plain
          @click="router.push(`/admin/editions/${edition.id}/entry-grants`)"
        >
          资格名单
        </el-button>
      </div>
    </header>

    <template v-if="edition">
      <section class="window-grid">
        <article :class="['window-card', { open: edition.registration_open }]">
          <span class="window-index">01</span>
          <p>报名窗口</p>
          <h2>{{ edition.registration_open ? '正在报名' : '未开放或已截止' }}</h2>
          <dl>
            <div>
              <dt>报名截止</dt>
              <dd>{{ formatContestDateTime(edition.register_deadline) }}</dd>
            </div>
          </dl>
        </article>
        <article :class="['window-card', { open: edition.submission_open }]">
          <span class="window-index">02</span>
          <p>作品提交窗口</p>
          <h2>{{ edition.submission_open ? '可以提交或修改' : '未开放或已截止' }}</h2>
          <dl>
            <div>
              <dt>作品截止</dt>
              <dd>{{ formatContestDateTime(edition.work_deadline) }}</dd>
            </div>
          </dl>
        </article>
        <article class="window-card rule-card">
          <span class="window-index">03</span>
          <p>本届组队规则</p>
          <h2>{{ teamRuleText }}</h2>
          <dl>
            <div>
              <dt>教师指导上限</dt>
              <dd>每人最多 {{ edition.max_teams_per_teacher }} 支队伍</dd>
            </div>
          </dl>
        </article>
      </section>

      <section class="detail-grid">
        <el-card shadow="never" class="detail-card">
          <template #header>
            <h2>完整赛程</h2>
          </template>
          <div class="timeline">
            <div>
              <span>报名截止</span>
              <strong>{{ formatContestDateTime(edition.register_deadline) }}</strong>
            </div>
            <div>
              <span>作品截止</span>
              <strong>{{ formatContestDateTime(edition.work_deadline) }}</strong>
            </div>
            <div>
              <span>评审开始</span>
              <strong>{{ formatContestDateTime(edition.review_start) }}</strong>
            </div>
            <div>
              <span>公示开始</span>
              <strong>{{ formatContestDateTime(edition.publish_start) }}</strong>
            </div>
            <div>
              <span>公示结束</span>
              <strong>{{ formatContestDateTime(edition.publish_end) }}</strong>
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="detail-card">
          <template #header>
            <h2>赛题与审核规则</h2>
          </template>
          <dl class="rule-list">
            <div>
              <dt>赛题模式</dt>
              <dd>{{ topicModeLabel }}</dd>
            </div>
            <div>
              <dt>选题或项目审核</dt>
              <dd>{{ edition.topic_review_required ? '需要审核' : '无需审核' }}</dd>
            </div>
            <div>
              <dt>限额赛题分配</dt>
              <dd>{{ edition.topic_allocation_mode === 'FIRST_COME' ? '先到先得' : '审核分配' }}</dd>
            </div>
            <div>
              <dt>报名截止前更换次数</dt>
              <dd>{{ edition.topic_change_limit }} 次</dd>
            </div>
          </dl>
        </el-card>
      </section>

      <el-card shadow="never" class="detail-card ownership-card">
        <template #header>
          <h2>比赛归属</h2>
        </template>
        <dl class="rule-list">
          <div>
            <dt>主办组织</dt>
            <dd>{{ edition.owner_org_name || edition.owner_org_id }}</dd>
          </div>
          <div>
            <dt>比赛级别</dt>
            <dd>{{ edition.level }}</dd>
          </div>
          <div>
            <dt>学校范围</dt>
            <dd>{{ edition.school_id || '不限学校' }}</dd>
          </div>
          <div>
            <dt>省份范围</dt>
            <dd>{{ edition.province_code || '不限省份' }}</dd>
          </div>
          <div>
            <dt>可见范围</dt>
            <dd>{{ edition.visibility }}</dd>
          </div>
          <div>
            <dt>参赛资格</dt>
            <dd>{{ edition.entry_mode }}</dd>
          </div>
          <div>
            <dt>跨校组队</dt>
            <dd>{{ edition.cross_school_team_allowed ? '允许' : '不允许' }}</dd>
          </div>
        </dl>
      </el-card>

      <el-card shadow="never" class="milestone-card">
        <template #header>
          <div class="milestone-heading">
            <h2>补充赛程</h2>
            <span>仅用于日程展示，不改变报名和作品资格</span>
          </div>
        </template>
        <div v-if="milestones.length" class="milestone-list">
          <div v-for="item in milestones" :key="item.id" class="milestone-item">
            <el-tag effect="plain">
              {{ MILESTONE_NODE_OPTIONS.find((option) => option.value === item.node_type)?.label }}
            </el-tag>
            <strong>{{ item.node_name }}</strong>
            <span>{{ formatContestDateTime(item.start_time) }}</span>
            <span>至</span>
            <span>{{ formatContestDateTime(item.end_time) }}</span>
            <el-tag v-if="!item.enabled" type="info">未启用</el-tag>
          </div>
        </div>
        <el-empty v-else description="暂无补充赛程节点" :image-size="72" />
      </el-card>

      <el-alert
        class="rule-note"
        type="info"
        :closable="false"
        show-icon
        title="报名截止后不能继续报名或加入队伍；作品截止后不能继续修改作品。"
        description="本页面展示的是届次公共动作窗口，队伍、作品和评审模块仍需叠加各自的业务前置条件。"
      />
    </template>
  </main>
</template>

<style scoped>
.edition-detail-page {
  min-height: 100vh;
  padding: 30px max(24px, calc((100vw - 1120px) / 2));
  color: #183b32;
  background:
    radial-gradient(circle at 100% 0, rgba(216, 186, 112, 0.2), transparent 27rem),
    #f3f6f3;
}

.detail-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 18px;
  padding: 30px 32px;
  border-radius: 20px 20px 20px 6px;
  color: #eff5f1;
  background:
    linear-gradient(135deg, rgba(13, 49, 40, 0.98), rgba(42, 91, 77, 0.94)),
    #173f35;
}

.header-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.detail-header :deep(.el-button.is-link) {
  color: #d8ba70;
}

.eyebrow {
  margin: 18px 0 0;
  color: #d8ba70;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.detail-header h1 {
  margin: 8px 0 16px;
  font-family: inherit;
  font-variant-numeric: tabular-nums;
  font-size: clamp(34px, 5vw, 52px);
  font-weight: 500;
}

.header-tags {
  display: flex;
  gap: 8px;
}

.window-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 15px;
  margin-bottom: 16px;
}

.window-card {
  min-height: 190px;
  padding: 23px;
  border: 1px solid rgba(23, 63, 53, 0.1);
  border-radius: 16px 16px 16px 5px;
  background: #fff;
}

.window-card.open {
  color: #edf5ef;
  background: linear-gradient(145deg, #245b49, #397863);
}

.window-index {
  color: #c6a760;
  font-family: Georgia, serif;
  font-size: 12px;
}

.window-card p {
  margin: 18px 0 6px;
  color: #7a8983;
  font-size: 12px;
}

.window-card.open p {
  color: rgba(237, 245, 239, 0.62);
}

.window-card h2 {
  margin: 0 0 24px;
  font-family: inherit;
  font-variant-numeric: tabular-nums;
  font-size: 25px;
  font-weight: 500;
}

dl {
  margin: 0;
}

.window-card dl div,
.rule-list div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
}

dt {
  color: #87948f;
  font-size: 11px;
}

.window-card.open dt {
  color: rgba(237, 245, 239, 0.56);
}

dd {
  margin: 0;
  font-size: 12px;
  font-weight: 600;
  text-align: right;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1.15fr 0.85fr;
  gap: 16px;
}

.detail-card h2,
.milestone-card h2 {
  margin: 0;
  font-family: inherit;
  font-variant-numeric: tabular-nums;
  font-size: 21px;
  font-weight: 500;
}

.timeline {
  display: grid;
  gap: 0;
}

.timeline div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 0;
  border-bottom: 1px solid #edf1ef;
}

.timeline div:last-child {
  border-bottom: 0;
}

.timeline span {
  color: #7f8d87;
  font-size: 12px;
}

.timeline strong {
  font-size: 13px;
  font-weight: 600;
}

.rule-list div {
  align-items: flex-start;
  padding: 12px 0;
  border-bottom: 1px solid #edf1ef;
}

.rule-list div:last-child {
  border-bottom: 0;
}

.milestone-card {
  margin-top: 16px;
}

.ownership-card {
  margin-top: 16px;
}

.milestone-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.milestone-heading span {
  color: #8c9994;
  font-size: 11px;
}

.milestone-list {
  display: grid;
  gap: 10px;
}

.milestone-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border-radius: 9px;
  background: #f7faf8;
  font-size: 12px;
}

.milestone-item strong {
  margin-right: auto;
}

.rule-note {
  margin-top: 16px;
}

@media (max-width: 820px) {
  .detail-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .header-actions {
    justify-content: flex-start;
  }

  .window-grid,
  .detail-grid {
    grid-template-columns: 1fr;
  }

  .milestone-heading,
  .milestone-item {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
