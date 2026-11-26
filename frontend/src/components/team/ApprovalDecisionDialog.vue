<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ApprovalDecision, ApprovalStage } from '../../types/team'
const props = defineProps<{ modelValue: boolean; stage: ApprovalStage; decision: ApprovalDecision; teamName: string; version: number; submitting?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; confirm: [reason: string] }>()
const reason = ref('')
const approved = computed(() => props.decision === 'APPROVE')
const title = computed(() => `${props.stage === 'TEACHER' ? '教师确认' : '管理员终审'} · ${approved.value ? '通过' : '驳回'}`)
watch(() => props.modelValue, (visible) => { if (visible) reason.value = '' })
function close(): void { emit('update:modelValue', false) }
function confirm(): void { if (!approved.value && !reason.value.trim()) return; emit('confirm', reason.value.trim()) }
</script>
<template>
  <el-dialog :model-value="modelValue" :title="title" width="520px" destroy-on-close @update:model-value="emit('update:modelValue', $event)">
    <div class="summary"><span>队伍</span><strong>{{ teamName }}</strong><span>报名轮次版本</span><strong>{{ version }}</strong></div>
    <el-alert v-if="approved" type="success" :closable="false" show-icon title="确认通过后将进入下一审核阶段" description="请确认成员快照、报名附件和指导教师信息无误。" />
    <el-alert v-else type="warning" :closable="false" show-icon title="驳回后队伍需修改并重新提交" description="驳回原因将展示给队长，教师驳回会释放学校名额。" />
    <el-form label-position="top" class="reason-form">
      <el-form-item :label="approved ? '审核说明（可选）' : '驳回原因（必填）'" :required="!approved">
        <el-input v-model="reason" type="textarea" :rows="4" maxlength="500" show-word-limit :placeholder="approved ? '可填写审核说明' : '请说明具体驳回原因'" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button :type="approved ? 'primary' : 'danger'" :loading="submitting" :disabled="!approved && !reason.trim()" @click="confirm">{{ approved ? '确认通过' : '确认驳回' }}</el-button>
    </template>
  </el-dialog>
</template>
<style scoped>
.summary { display: grid; grid-template-columns: 100px 1fr; gap: 10px 14px; margin-bottom: 16px; padding: 14px; border-radius: 12px; background: #f4f8f6; }
.summary span { color: #7b8d85; }.summary strong { color: #1c3a32; }.reason-form { margin-top: 18px; }
</style>
