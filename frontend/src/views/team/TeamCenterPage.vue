<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createTeam, createTeamIdempotencyKey, getTeamErrorMessage, listTeams } from '../../api/team'
import { TEAM_STATUS_LABELS, TEAM_STATUS_TAG_TYPES } from '../../types/team'
import type { TeamPageItem, TeamStatus } from '../../types/team'
import { useCurrentMenus } from '../../layouts/useCurrentMenus'
const route = useRoute(); const router = useRouter(); const { ensureCurrentMenus, hasPermission } = useCurrentMenus()
const editionId = computed(() => String(route.query.edition_id ?? ''))
const loading = ref(false); const creating = ref(false); const teams = ref<TeamPageItem[]>([]); const total = ref(0); const page = ref(1); const size = ref(10)
const filter = reactive<{ keyword: string; status: TeamStatus | '' }>({ keyword: '', status: '' })
const dialogVisible = ref(false); const teamName = ref(''); const idempotencyKey = ref('')
const canCreate = computed(() => hasPermission('team:create'))
async function load(): Promise<void> {
  if (!editionId.value) return
  loading.value = true
  try {
    const data = await listTeams(editionId.value, { page: page.value, size: size.value, sort: '-created_at', status: filter.status, keyword: filter.keyword.trim() || undefined })
    teams.value = data.items; total.value = data.total
  } catch (error) { ElMessage.error(getTeamErrorMessage(error, '队伍列表加载失败')) } finally { loading.value = false }
}
function openCreate(): void { teamName.value = ''; idempotencyKey.value = createTeamIdempotencyKey(); dialogVisible.value = true }
async function submitCreate(): Promise<void> {
  if (!teamName.value.trim()) { ElMessage.warning('请输入队伍名称'); return }
  creating.value = true
  try { const result = await createTeam(editionId.value, { team_name: teamName.value.trim() }, idempotencyKey.value); ElMessage.success(`队伍已创建：${result.team_code}`); dialogVisible.value = false; await router.push(`/teams/${result.id}`) }
  catch (error) { ElMessage.error(getTeamErrorMessage(error, '创建队伍失败')) } finally { creating.value = false }
}
function search(): void { page.value = 1; void load() }
function openTeam(id: string): void { void router.push(`/teams/${id}`) }
function backToEdition(): void {
  if (editionId.value) void router.push(`/contests/${editionId.value}`)
}
onMounted(async () => { await ensureCurrentMenus(); await load() })
</script>
<template>
  <div class="team-center-page">
    <section class="hero-card"><div><span class="eyebrow">STUDENT TEAM CENTER</span><h1>我的参赛队伍</h1><p>按届次管理队伍成员和指导教师确认流程。</p></div><div class="hero-actions page-actions"><el-button v-if="editionId" class="page-nav-button" plain @click="backToEdition">返回届次详情</el-button><el-button v-if="canCreate && editionId" type="primary" @click="openCreate">创建队伍</el-button></div></section>
    <el-alert v-if="!editionId" type="warning" :closable="false" show-icon title="缺少届次参数" description="请从赛事详情页进入，并在地址中携带 edition_id。" />
    <el-card v-else>
      <el-form inline class="filter-row">
        <el-form-item label="状态"><el-select v-model="filter.status" clearable placeholder="全部状态" class="status-select"><el-option v-for="(label, value) in TEAM_STATUS_LABELS" :key="value" :value="value" :label="label" /></el-select></el-form-item>
        <el-form-item label="队伍"><el-input v-model="filter.keyword" clearable placeholder="队伍名称或编码" @keyup.enter="search" /></el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button></el-form-item>
      </el-form>
      <el-table v-loading="loading" :data="teams" border row-key="id">
        <el-table-column prop="team_name" label="队伍名称" min-width="220" /><el-table-column prop="team_code" label="队伍编码" width="150"><template #default="{ row }"><code>{{ row.team_code }}</code></template></el-table-column>
        <el-table-column prop="member_count" label="人数" width="80" /><el-table-column label="状态" width="130"><template #default="{ row }"><el-tag :type="TEAM_STATUS_TAG_TYPES[row.status as TeamStatus]" effect="plain">{{ TEAM_STATUS_LABELS[row.status as TeamStatus] }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" @click="openTeam(row.id)">查看</el-button></template></el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" background @current-change="load" @size-change="search" /></div>
    </el-card>
    <el-dialog v-model="dialogVisible" title="创建队伍" width="480px" destroy-on-close>
      <el-form label-position="top"><el-form-item label="队伍名称" required><el-input v-model="teamName" maxlength="64" show-word-limit placeholder="请输入队伍名称" /></el-form-item></el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="creating" @click="submitCreate">创建 DRAFT 队伍</el-button></template>
    </el-dialog>
  </div>
</template>
<style scoped>
.team-center-page { display: grid; gap: 16px; padding: 18px; }
.hero-card { display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 28px 30px; border-radius: 18px 18px 18px 5px; color: #f0f5f1; background: radial-gradient(circle at 90% 10%, rgba(216, 186, 112, .34), transparent 16rem), linear-gradient(135deg, #143b31, #275b4b); }
.hero-card h1 { margin: 7px 0 8px; font-family: inherit; font-variant-numeric: tabular-nums; font-size: 32px; font-weight: 600; }.hero-card p { margin: 0; color: rgba(240,245,241,.74); }
.hero-actions { display: flex; flex-wrap: wrap; gap: 10px; justify-content: flex-end; }
.eyebrow { color: #d8ba70; font-size: 11px; font-weight: 700; letter-spacing: .16em; }.filter-row { margin-bottom: 4px; }.status-select { width: 150px; }.pagination { display: flex; justify-content: flex-end; margin-top: 18px; } code { color: #47665c; font-family: Consolas, monospace; }
@media (max-width: 760px) { .hero-card { align-items: flex-start; flex-direction: column; } }
</style>
