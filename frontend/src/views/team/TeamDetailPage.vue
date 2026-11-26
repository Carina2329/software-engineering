<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import MemberEditor from '../../components/team/MemberEditor.vue'
import RegistrationSubmitPanel from '../../components/team/RegistrationSubmitPanel.vue'
import RegistrationTimeline from '../../components/team/RegistrationTimeline.vue'
import { createTeamIdempotencyKey, getTeam, getTeamErrorMessage, listTeamMembers, removeTeamMember, submitTeamRegistration, updateTeam, withdrawTeamRegistration } from '../../api/team'
import type { TeamDetail, TeamMember, TeamMemberInput } from '../../types/team'
import { TEAM_STATUS_LABELS, TEAM_STATUS_TAG_TYPES, editableTeamStatuses } from '../../types/team'
import { useCurrentMenus } from '../../layouts/useCurrentMenus'
const route = useRoute(); const router = useRouter(); const { ensureCurrentMenus, hasPermission } = useCurrentMenus()
const teamId = computed(() => String(route.params.teamId ?? ''))
const detail = ref<TeamDetail | null>(null); const members = ref<TeamMember[]>([]); const memberDraft = ref<TeamMemberInput[]>([])
const teamName = ref(''); const loading = ref(false); const saving = ref(false); const submitting = ref(false); const withdrawing = ref(false)
const memberEditorRef = ref<{ validate: () => boolean }>()
const canEdit = computed(() => Boolean(detail.value && editableTeamStatuses.includes(detail.value.status) && hasPermission('team:update')))
const canSubmit = computed(() => Boolean(detail.value?.can_register && canEdit.value))
const canWithdraw = computed(() => Boolean(detail.value && ['PENDING_TEACHER', 'PENDING_ADMIN'].includes(detail.value.status) && detail.value.current_registration_version !== null && hasPermission('team:registration:submit')))
async function loadAll(): Promise<void> {
  if (!teamId.value) return
  loading.value = true
  try { const [team, memberList] = await Promise.all([getTeam(teamId.value), listTeamMembers(teamId.value)]); detail.value = team; members.value = memberList; teamName.value = team.team_name }
  catch (error) { ElMessage.error(getTeamErrorMessage(error, '队伍详情加载失败')) } finally { loading.value = false }
}
async function saveTeam(): Promise<void> {
  if (!detail.value || !canEdit.value) return
  if (!memberEditorRef.value?.validate()) { ElMessage.warning('请检查成员信息：姓名、学校、学号必填且学号不能重复'); return }
  saving.value = true
  try { const result = await updateTeam(detail.value.id, { team_name: teamName.value.trim(), version: detail.value.version, members: memberDraft.value }, createTeamIdempotencyKey()); ElMessage.success('队伍信息已保存'); detail.value.version = result.version; await loadAll() }
  catch (error) { ElMessage.error(getTeamErrorMessage(error, '保存队伍失败')); await loadAll() } finally { saving.value = false }
}
async function removeMember(memberId: string): Promise<void> {
  if (!detail.value) return
  try { await ElMessageBox.confirm('确认移出该普通队员？', '移出成员', { type: 'warning' }) } catch { return }
  try { await removeTeamMember(detail.value.id, memberId, detail.value.version, createTeamIdempotencyKey()); ElMessage.success('成员已移出'); await loadAll() }
  catch (error) { ElMessage.error(getTeamErrorMessage(error, '移出成员失败')); await loadAll() }
}
async function submitRegistration(payload: { teacherId: string; fileIds: string[]; remark: string }): Promise<void> {
  if (!detail.value || !canSubmit.value) return
  submitting.value = true
  try { await submitTeamRegistration(detail.value.id, { version: detail.value.version, teacher_id: payload.teacherId, attachment_file_ids: payload.fileIds, remark: payload.remark || undefined }, createTeamIdempotencyKey()); ElMessage.success('报名已提交，等待教师确认'); await loadAll() }
  catch (error) { ElMessage.error(getTeamErrorMessage(error, '提交报名失败')); await loadAll() } finally { submitting.value = false }
}
async function withdraw(): Promise<void> {
  if (!detail.value || detail.value.current_registration_version === null) return
  try { await ElMessageBox.confirm('撤回后需修改并重新提交，确认继续？', '撤回报名', { type: 'warning' }) } catch { return }
  withdrawing.value = true
  try { await withdrawTeamRegistration(detail.value.id, detail.value.current_registration_version, createTeamIdempotencyKey()); ElMessage.success('报名已撤回'); await loadAll() }
  catch (error) { ElMessage.error(getTeamErrorMessage(error, '撤回报名失败')); await loadAll() } finally { withdrawing.value = false }
}
function back(): void { router.back() }
onMounted(async () => { await ensureCurrentMenus(); await loadAll() })
</script>
<template>
  <div v-loading="loading" class="team-detail-page">
    <section class="detail-hero"><div><el-button link class="back" @click="back">← 返回队伍列表</el-button><span class="eyebrow">TEAM REGISTRATION</span><h1>{{ detail?.team_name || '队伍详情' }}</h1><p v-if="detail"><code>{{ detail.team_code }}</code> · {{ detail.member_count }} 人 <el-tag :type="TEAM_STATUS_TAG_TYPES[detail.status]" effect="plain">{{ TEAM_STATUS_LABELS[detail.status] }}</el-tag></p></div><div class="hero-actions"><el-button v-if="canWithdraw" type="danger" plain :loading="withdrawing" @click="withdraw">撤回报名</el-button></div></section>
    <template v-if="detail">
      <RegistrationTimeline :detail="detail" />
      <el-card class="section-card"><div class="card-heading"><div><span class="eyebrow">TEAM PROFILE</span><h2>队伍资料</h2></div><el-button v-if="canEdit" type="primary" :loading="saving" @click="saveTeam">保存资料</el-button></div><el-form label-position="top"><el-form-item label="队伍名称"><el-input v-model="teamName" maxlength="64" :disabled="!canEdit" /></el-form-item></el-form><MemberEditor ref="memberEditorRef" :members="members" :disabled="!canEdit" @update:members="memberDraft = $event" @remove="removeMember" /></el-card>
      <RegistrationSubmitPanel v-if="detail.status !== 'APPROVED'" :edition-id="detail.edition_id" :initial-teacher-id="detail.current_teacher_id" :attachments="detail.attachments" :disabled="!canEdit" :can-register="detail.can_register" :register-state="detail.register_state" :register-reason="detail.register_reason" :submitting="submitting" @submit="submitRegistration" />
      <el-alert v-else type="success" :closable="false" show-icon title="报名已通过，队伍当前只读" description="成员、附件和指导教师信息已冻结。" />
    </template>
    <el-empty v-else-if="!loading" description="未找到队伍或当前账号无权查看" />
  </div>
</template>
<style scoped>
.team-detail-page { display: grid; gap: 16px; padding: 18px; }
.detail-hero { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; padding: 26px 30px; border-radius: 18px 18px 18px 5px; color: #f3f7f4; background: radial-gradient(circle at 88% 8%, rgba(216,186,112,.3), transparent 14rem), linear-gradient(135deg, #143b31, #275b4b); }
.detail-hero h1 { margin: 8px 0 7px; font-family: Georgia, "Songti SC", serif; font-size: 34px; font-weight: 500; }.detail-hero p { display: flex; align-items: center; gap: 8px; margin: 0; color: rgba(243,247,244,.72); }.detail-hero code { font-family: Consolas, monospace; }
.back { padding: 0; color: #d8ba70; }.eyebrow { color: #d8ba70; font-size: 11px; font-weight: 700; letter-spacing: .16em; }.hero-actions { display: flex; gap: 10px; }
.section-card { border-radius: 16px; }.card-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }.card-heading h2 { margin: 4px 0 0; color: #18352e; }.card-heading .eyebrow { color: #6d8f84; }
@media (max-width: 760px) { .detail-hero { align-items: flex-start; flex-direction: column; }.hero-actions { width: 100%; } }
</style>
