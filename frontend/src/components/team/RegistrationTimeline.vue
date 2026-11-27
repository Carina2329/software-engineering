<script setup lang="ts">
import { computed } from 'vue'
import type { TeamDetail, TeamStatus } from '../../types/team'
import { TEAM_STATUS_LABELS } from '../../types/team'
const props = defineProps<{ detail: TeamDetail }>()
const activeStep = computed(() => {
  const index: Record<TeamStatus, number> = { DRAFT: 0, PENDING_TEACHER: 1, PENDING_ADMIN: 2, APPROVED: 4, REJECTED: 1, WITHDRAWN: 0 }
  return index[props.detail.status]
})
const hasProblem = computed(() => ['REJECTED', 'WITHDRAWN'].includes(props.detail.status))
</script>
<template>
  <section class="timeline-panel">
    <div class="heading"><span class="eyebrow">REGISTRATION FLOW</span><h3>报名进度</h3></div>
    <el-steps :active="activeStep" :process-status="hasProblem ? 'error' : 'process'" finish-status="success" align-center>
      <el-step title="填写队伍" description="成员与报名信息" />
      <el-step title="教师确认" description="指导教师审核" />
      <el-step title="管理员终审" description="资格与名额复核" />
      <el-step title="报名通过" description="成员冻结" />
    </el-steps>
    <div class="status-grid">
      <div><span>当前状态</span><strong>{{ TEAM_STATUS_LABELS[detail.status] }}</strong></div>
      <div><span>当前轮次</span><strong>{{ detail.current_registration_id ? `#${detail.current_registration_id}` : '尚未提交' }}</strong></div>
      <div><span>指导教师</span><strong>{{ detail.current_teacher_name || '未确认' }}</strong></div>
      <div><span>教师申请</span><strong>{{ detail.current_teacher_application_status || '无' }}</strong></div>
    </div>
    <el-alert v-if="detail.current_reject_stage" type="error" :closable="false" show-icon
      :title="`${detail.current_reject_stage === 'TEACHER' ? '教师' : '管理员'}驳回`"
      :description="detail.current_reject_reason || '未填写驳回原因'" />
    <el-alert v-if="detail.status === 'APPROVED'" type="success" :closable="false" show-icon
      title="报名已通过" description="成员和指导教师信息已冻结，无需再次提交。" />
    <el-alert v-else-if="detail.can_register" type="success" :closable="false" show-icon
      title="当前具备提交条件" :description="detail.register_reason || `资格状态：${detail.register_state || '可报名'}`" />
    <el-alert v-else type="warning" :closable="false" show-icon
      title="当前不可提交" :description="detail.register_reason || `资格状态：${detail.register_state || '不满足报名条件'}`" />
  </section>
</template>
<style scoped>
.timeline-panel { display: grid; gap: 18px; padding: 22px; border: 1px solid #dfe8e3; border-radius: 16px; background: #fff; }
.heading h3 { margin: 4px 0 0; color: #18352e; font-size: 20px; }
.eyebrow { color: #6d8f84; font-size: 10px; letter-spacing: .16em; }
.status-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; }
.status-grid div { padding: 12px; border-radius: 10px; background: #f4f8f6; }
.status-grid span { display: block; color: #7b8e86; font-size: 12px; }
.status-grid strong { display: block; margin-top: 5px; color: #1d3b33; }
@media (max-width: 760px) { .status-grid { grid-template-columns: 1fr 1fr; } }
</style>
