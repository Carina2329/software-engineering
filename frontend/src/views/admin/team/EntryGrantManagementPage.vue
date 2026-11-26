<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import EntryGrantBatchEditor from '../../../components/team/EntryGrantBatchEditor.vue'
import { batchEntryGrants, createTeamIdempotencyKey, getTeamErrorMessage, listEntryGrants } from '../../../api/team'
import { getContestEdition } from '../../../api/contest'
import type { EntryGrant, EntryGrantBatchItem, EntryGrantStatus, EntryGrantType } from '../../../types/team'
import { ENTRY_GRANT_STATUS_LABELS, ENTRY_GRANT_TYPE_LABELS } from '../../../types/team'
import type { EditionDetail } from '../../../types/contest'
import { EDITION_STATUS_LABELS } from '../../../types/contest'
const route = useRoute()
const editionId = computed(() => String(route.params.editionId ?? route.query.edition_id ?? ''))
const loading = ref(false); const mutating = ref(false); const grants = ref<EntryGrant[]>([]); const status = ref<EntryGrantStatus | ''>(''); const edition = ref<EditionDetail | null>(null); const editorRef = ref<{ clearQueue: () => void }>()
const archived = computed(() => edition.value?.status === 'ARCHIVED' || edition.value?.read_only === true)
async function load(): Promise<void> { if (!editionId.value) return; loading.value = true; try { const [editionData, grantData] = await Promise.all([getContestEdition(editionId.value), listEntryGrants(editionId.value, status.value || undefined)]); edition.value = editionData; grants.value = grantData.items } catch (error) { ElMessage.error(getTeamErrorMessage(error, '资格名单加载失败')) } finally { loading.value = false } }
async function submitBatch(items: EntryGrantBatchItem[]): Promise<void> { if (archived.value) { ElMessage.warning('归档届次的资格名单只读'); return } mutating.value = true; try { const result = await batchEntryGrants(editionId.value, { items }, createTeamIdempotencyKey()); editorRef.value?.clearQueue(); ElMessage.success(`维护完成：新增/更新 ${result.upserted_count} 条，撤销 ${result.revoked_count} 条`); await load() } catch (error) { ElMessage.error(getTeamErrorMessage(error, '资格批次维护失败')); await load() } finally { mutating.value = false } }
function search(): void { void load() }
onMounted(load)
</script>
<template>
  <div class="grant-page">
    <section class="hero-card"><div><span class="eyebrow">ENTRY GRANT MANAGEMENT</span><h1>比赛资格名单</h1><p>批量维护邀请、晋级资格，并保留完整撤销历史。</p></div><div class="hero-tags"><el-tag v-if="edition" effect="plain">{{ EDITION_STATUS_LABELS[edition.status] }}</el-tag><el-tag v-if="archived" type="warning" effect="plain">只读</el-tag></div></section>
    <el-alert v-if="archived" type="warning" :closable="false" show-icon title="归档届次只读" description="归档后不能新增、更新或撤销资格，但仍可查询历史。" />
    <el-alert v-if="!editionId" type="warning" :closable="false" show-icon title="缺少届次参数" description="请从届次管理进入资格名单页。" />
    <template v-else>
      <EntryGrantBatchEditor ref="editorRef" :grants="grants" :read-only="archived || mutating" @submit="submitBatch" />
      <el-card><div class="card-heading"><div><span class="eyebrow">GRANT HISTORY</span><h2>资格名单</h2></div><div class="filters"><el-select v-model="status" clearable placeholder="全部状态" @change="search"><el-option value="ACTIVE" label="有效" /><el-option value="REVOKED" label="已撤销" /></el-select><el-button :loading="loading" @click="search">刷新</el-button></div></div><el-table v-loading="loading" :data="grants" border row-key="id"><el-table-column prop="leader_name" label="队长" min-width="130" /><el-table-column prop="school_id" label="学校 ID" width="120" /><el-table-column prop="student_no" label="学号" min-width="140" /><el-table-column label="资格类型" width="120"><template #default="{ row }">{{ ENTRY_GRANT_TYPE_LABELS[row.grant_type as EntryGrantType] }}</template></el-table-column><el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" effect="plain">{{ ENTRY_GRANT_STATUS_LABELS[row.status as EntryGrantStatus] }}</el-tag></template></el-table-column><el-table-column prop="target_team_id" label="目标队伍" width="120"><template #default="{ row }">{{ row.target_team_id || '未绑定' }}</template></el-table-column><el-table-column prop="version" label="版本" width="80" /><el-table-column prop="reason" label="撤销/说明" min-width="180"><template #default="{ row }">{{ row.reason || '无' }}</template></el-table-column></el-table><el-empty v-if="!loading && !grants.length" description="暂无资格名单记录" /></el-card>
    </template>
  </div>
</template>
<style scoped>
.grant-page { display: grid; gap: 16px; padding: 18px; }.hero-card { display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 28px 30px; border-radius: 18px 18px 18px 5px; color: #f0f5f1; background: radial-gradient(circle at 90% 10%, rgba(216,186,112,.34), transparent 16rem), linear-gradient(135deg, #143b31, #275b4b); }.hero-card h1 { margin: 7px 0 8px; font-family: Georgia, "Songti SC", serif; font-size: 32px; font-weight: 500; }.hero-card p { margin: 0; color: rgba(240,245,241,.72); }.eyebrow { color: #6d8f84; font-size: 10px; letter-spacing: .16em; }.hero-card .eyebrow { color: #d8ba70; }.hero-tags { display: flex; gap: 8px; }.card-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 14px; }.card-heading h2 { margin: 4px 0 0; }.filters { display: flex; gap: 8px; }.filters .el-select { width: 150px; }
</style>
