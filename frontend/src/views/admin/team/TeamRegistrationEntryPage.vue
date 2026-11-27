<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getContestErrorMessage, listContestEditions, listContests } from '../../../api/contest'
import type { ContestItem, EditionItem, EditionStatus } from '../../../types/contest'
import { EDITION_STATUS_LABELS } from '../../../types/contest'
import { useCurrentMenus } from '../../../layouts/useCurrentMenus'

const router = useRouter()
const { ensureCurrentMenus, hasPermission } = useCurrentMenus()
const contests = ref<ContestItem[]>([])
const editions = ref<EditionItem[]>([])
const contestId = ref('')
const loadingContests = ref(false)
const loadingEditions = ref(false)
const canReview = computed(() => hasPermission('team:approve'))
const canManageEligibility = computed(() => hasPermission('team:eligibility:manage'))
const selectedContest = computed(
  () => contests.value.find((item) => item.id === contestId.value) ?? null,
)

async function loadContests(): Promise<void> {
  loadingContests.value = true
  try {
    const data = await listContests({ page: 1, size: 100, sort: '-created_at' })
    contests.value = data.items
    if (!contestId.value || !contests.value.some((item) => item.id === contestId.value)) {
      contestId.value = contests.value[0]?.id ?? ''
    }
    await loadEditions()
  } catch (error) {
    ElMessage.error(getContestErrorMessage(error, '竞赛列表加载失败'))
  } finally {
    loadingContests.value = false
  }
}

async function loadEditions(): Promise<void> {
  if (!contestId.value) {
    editions.value = []
    return
  }
  loadingEditions.value = true
  try {
    const data = await listContestEditions(contestId.value, {
      page: 1,
      size: 100,
      sort: '-year',
    })
    editions.value = data.items
  } catch (error) {
    ElMessage.error(getContestErrorMessage(error, '届次列表加载失败'))
  } finally {
    loadingEditions.value = false
  }
}

function openApproval(editionId: string): void {
  void router.push({ path: '/admin/team-approvals', query: { edition_id: editionId } })
}

function openGrants(editionId: string): void {
  void router.push(`/admin/editions/${editionId}/entry-grants`)
}

function statusType(status: EditionStatus): 'info' | 'success' | 'warning' | 'danger' {
  if (status === 'DRAFT') return 'info'
  if (status === 'REGISTERING' || status === 'SUBMITTING') return 'success'
  if (status === 'REVIEWING' || status === 'PUBLISHING') return 'warning'
  return 'danger'
}

onMounted(async () => {
  await ensureCurrentMenus().catch(() => undefined)
  await loadContests()
})
</script>

<template>
  <div class="team-registration-entry">
    <section class="hero-card">
      <div>
        <span class="eyebrow">TEAM REGISTRATION OPERATIONS</span>
        <h1>报名组织工作台</h1>
        <p>选择竞赛和具体届次，再进入报名终审或比赛资格名单。</p>
      </div>
      <el-tag size="large" effect="plain">{{ editions.length }} 个届次</el-tag>
    </section>

    <el-card shadow="never">
      <div class="workspace-heading">
        <div>
          <span class="eyebrow">WORKSPACE</span>
          <h2>{{ selectedContest?.contest_name || '选择竞赛' }}</h2>
          <p>{{ selectedContest?.series_code || '请选择要处理报名组织的竞赛系列。' }}</p>
        </div>
        <el-select
          v-model="contestId"
          filterable
          placeholder="选择竞赛"
          :loading="loadingContests"
          class="contest-select"
          @change="loadEditions"
        >
          <el-option
            v-for="contest in contests"
            :key="contest.id"
            :value="contest.id"
            :label="contest.contest_name"
          />
        </el-select>
      </div>

      <el-table v-loading="loadingEditions" :data="editions" border row-key="id">
        <el-table-column prop="edition_name" label="届次名称" min-width="240" />
        <el-table-column prop="year" label="年份" width="90" />
        <el-table-column label="级别" min-width="120">
          <template #default="{ row }">
            {{ row.level === 'SCHOOL' ? '校级' : row.level === 'PROVINCE' ? '省级' : '国家级' }}
          </template>
        </el-table-column>
        <el-table-column prop="owner_org_name" label="主办组织" min-width="180" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status as EditionStatus)">
              {{ EDITION_STATUS_LABELS[row.status as EditionStatus] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="报名组织" width="210" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canReview" link type="warning" @click="openApproval(row.id)">
              报名终审
            </el-button>
            <el-button v-if="canManageEligibility" link type="success" @click="openGrants(row.id)">
              资格名单
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loadingEditions && !editions.length" description="当前竞赛暂无可见届次" />
    </el-card>
  </div>
</template>

<style scoped>
.team-registration-entry { display: grid; gap: 16px; padding: 18px; }
.hero-card { display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 28px 30px; border-radius: 18px 18px 18px 5px; color: #f0f5f1; background: radial-gradient(circle at 90% 10%, rgba(216,186,112,.34), transparent 16rem), linear-gradient(135deg, #143b31, #275b4b); }
.hero-card h1 { margin: 7px 0 8px; font-family: Georgia, "Songti SC", serif; font-size: 32px; font-weight: 500; }
.hero-card p { margin: 0; color: rgba(240,245,241,.74); }
.eyebrow { color: #d8ba70; font-size: 10px; font-weight: 700; letter-spacing: .16em; }
.workspace-heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 18px; }
.workspace-heading h2 { margin: 5px 0; color: #18352e; font-family: Georgia, "Songti SC", serif; font-size: 24px; font-weight: 500; }
.workspace-heading p { margin: 0; color: #7c8c85; font-size: 12px; }
.contest-select { width: min(360px, 45vw); }
@media (max-width: 760px) {
  .hero-card, .workspace-heading { align-items: flex-start; flex-direction: column; }
  .contest-select { width: 100%; }
}
</style>
