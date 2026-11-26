<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { EntryGrant, EntryGrantBatchItem, EntryGrantType } from '../../types/team'
import { ENTRY_GRANT_TYPE_LABELS } from '../../types/team'
const props = withDefaults(defineProps<{ grants: EntryGrant[]; readOnly?: boolean }>(), { readOnly: false })
const emit = defineEmits<{ submit: [items: EntryGrantBatchItem[]] }>()
const mode = ref<'UPSERT' | 'REVOKE'>('UPSERT')
const queue = ref<EntryGrantBatchItem[]>([])
const activeGrants = computed(() => props.grants.filter((grant) => grant.status === 'ACTIVE'))
const upsert = reactive({ grant_type: 'QUALIFIED' as EntryGrantType, leader_user_id: '', leader_name: '', school_id: '', student_no: '', source_edition_id: '', source_team_code: '', reason: '' })
const revoke = reactive({ grant_id: '', version: 0, reason: '' })
function addUpsert(): void {
  if (!upsert.leader_name.trim() || !upsert.school_id.trim() || !upsert.student_no.trim()) { ElMessage.warning('请填写姓名、学校和学号'); return }
  queue.value.push({ action: 'UPSERT', grant_type: upsert.grant_type, leader_user_id: upsert.leader_user_id.trim() || null, leader_name: upsert.leader_name.trim(), school_id: upsert.school_id.trim(), student_no: upsert.student_no.trim().toUpperCase(), source_edition_id: upsert.source_edition_id.trim() || null, source_team_code: upsert.source_team_code.trim() || null, reason: upsert.reason.trim() || null })
  Object.assign(upsert, { leader_user_id: '', leader_name: '', school_id: '', student_no: '', source_edition_id: '', source_team_code: '', reason: '' })
}
function selectGrant(grantId: string): void { revoke.version = activeGrants.value.find((item) => item.id === grantId)?.version ?? 0 }
function addRevoke(): void {
  const grant = activeGrants.value.find((item) => item.id === revoke.grant_id)
  if (!grant) { ElMessage.warning('请选择要撤销的有效资格'); return }
  queue.value.push({ action: 'REVOKE', grant_id: grant.id, version: grant.version, reason: revoke.reason.trim() || null })
  revoke.grant_id = ''; revoke.version = 0; revoke.reason = ''
}
function removeQueue(index: number): void { queue.value.splice(index, 1) }
function submit(): void { if (!queue.value.length) { ElMessage.warning('请先添加至少一条维护操作'); return } emit('submit', queue.value.map((item) => ({ ...item }))) }
</script>
<template>
  <section class="grant-editor">
    <div class="heading"><div><span class="eyebrow">BATCH OPERATIONS</span><h3>批量维护资格</h3></div><el-tag type="info" effect="plain">待提交 {{ queue.length }} 条</el-tag></div>
    <el-radio-group v-model="mode" :disabled="readOnly"><el-radio-button value="UPSERT">新增 / 更新</el-radio-button><el-radio-button value="REVOKE">撤销资格</el-radio-button></el-radio-group>
    <div v-if="mode === 'UPSERT'" class="form-grid">
      <el-select v-model="upsert.grant_type" :disabled="readOnly"><el-option v-for="(label, value) in ENTRY_GRANT_TYPE_LABELS" :key="value" :value="value" :label="label" /></el-select>
      <el-input v-model="upsert.leader_name" maxlength="32" placeholder="队长姓名" :disabled="readOnly" />
      <el-input v-model="upsert.school_id" placeholder="学校 ID" :disabled="readOnly" />
      <el-input v-model="upsert.student_no" maxlength="20" placeholder="学号" :disabled="readOnly" />
      <el-input v-model="upsert.leader_user_id" placeholder="队长用户 ID（可选）" :disabled="readOnly" />
      <el-input v-model="upsert.source_edition_id" placeholder="来源届次 ID（可选）" :disabled="readOnly" />
      <el-input v-model="upsert.source_team_code" maxlength="16" placeholder="来源队伍编码（可选）" :disabled="readOnly" />
      <el-input v-model="upsert.reason" maxlength="500" placeholder="说明（可选）" :disabled="readOnly" />
      <el-button type="primary" plain :disabled="readOnly" @click="addUpsert">加入批次</el-button>
    </div>
    <div v-else class="form-grid revoke-grid">
      <el-select v-model="revoke.grant_id" filterable placeholder="选择有效资格" :disabled="readOnly" @change="selectGrant"><el-option v-for="grant in activeGrants" :key="grant.id" :value="grant.id" :label="`${grant.leader_name} · ${grant.student_no} · v${grant.version}`" /></el-select>
      <el-input-number v-model="revoke.version" :min="0" disabled />
      <el-input v-model="revoke.reason" maxlength="500" placeholder="撤销原因（可选）" :disabled="readOnly" />
      <el-button type="danger" plain :disabled="readOnly" @click="addRevoke">加入批次</el-button>
    </div>
    <el-table :data="queue" border size="small">
      <el-table-column prop="action" label="动作" width="100" />
      <el-table-column label="资格对象" min-width="220"><template #default="{ row }">{{ row.leader_name || row.grant_id }} {{ row.student_no || '' }}</template></el-table-column>
      <el-table-column prop="version" label="版本" width="80" />
      <el-table-column label="操作" width="80"><template #default="{ $index }"><el-button link type="danger" :disabled="readOnly" @click="removeQueue($index)">移除</el-button></template></el-table-column>
    </el-table>
    <div class="footer"><el-button type="primary" :disabled="readOnly || !queue.length" @click="submit">执行批次维护</el-button></div>
  </section>
</template>
<style scoped>
.grant-editor { display: grid; gap: 16px; padding: 20px; border: 1px solid #dfe8e3; border-radius: 16px; background: #fff; }
.heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }.heading h3 { margin: 4px 0 0; color: #18352e; }
.eyebrow { color: #6d8f84; font-size: 10px; letter-spacing: .16em; }
.form-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; }.revoke-grid { grid-template-columns: 2fr 100px 1.4fr auto; }
.footer { display: flex; justify-content: flex-end; }
@media (max-width: 900px) { .form-grid, .revoke-grid { grid-template-columns: 1fr 1fr; } }
</style>
