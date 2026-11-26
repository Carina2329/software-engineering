<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getTeamErrorMessage, listAvailableTeachers } from '../../api/team'
import type { AvailableTeacher } from '../../types/team'

const props = withDefaults(defineProps<{
  editionId: string; initialTeacherId?: string | null
  disabled?: boolean; canRegister?: boolean; registerState?: string | null; registerReason?: string | null; submitting?: boolean
}>(), { initialTeacherId: null, disabled: false, canRegister: false, registerState: null, registerReason: null, submitting: false })
const emit = defineEmits<{ submit: [payload: { teacherId: string; remark: string }] }>()
const teacherKeyword = ref('')
const teachers = ref<AvailableTeacher[]>([])
const teacherLoading = ref(false)
const teacherPage = ref(1)
const teacherTotal = ref(0)
const teacherId = ref(props.initialTeacherId ?? '')
const remark = ref('')

watch(() => props.initialTeacherId, (value) => { teacherId.value = value ?? '' })
onMounted(() => { void loadTeachers(true) })

async function loadTeachers(reset = false): Promise<void> {
  if (!props.editionId) return
  if (reset) teacherPage.value = 1
  teacherLoading.value = true
  try {
    const data = await listAvailableTeachers(props.editionId, { keyword: teacherKeyword.value.trim() || undefined, page: teacherPage.value, size: 20 })
    teachers.value = data.items
    teacherTotal.value = data.total
  } catch (error) { ElMessage.error(getTeamErrorMessage(error, '教师列表加载失败')) }
  finally { teacherLoading.value = false }
}
function submit(): void {
  if (props.disabled || props.submitting) return
  if (!teacherId.value) { ElMessage.warning('请选择指导教师'); return }
  emit('submit', { teacherId: teacherId.value, remark: remark.value.trim() })
}
</script>

<template>
  <section class="submit-panel">
    <div class="heading"><div><span class="eyebrow">SUBMIT REGISTRATION</span><h3>提交报名</h3></div>
      <el-tag :type="canRegister ? 'success' : 'warning'" effect="plain">{{ registerState || (canRegister ? '可报名' : '待确认') }}</el-tag>
    </div>
    <el-alert :type="canRegister ? 'success' : 'warning'" :closable="false" show-icon
      :title="canRegister ? '后端已确认当前满足报名条件' : '当前暂不能提交报名'"
      :description="registerReason || '可报名结论完全以后端返回为准'" />
    <div class="teacher-picker">
      <label>指导教师</label>
      <div class="teacher-tools">
        <el-input v-model="teacherKeyword" clearable placeholder="搜索教师姓名或用户名" :disabled="disabled" @keyup.enter="loadTeachers(true)" />
        <el-button :loading="teacherLoading" :disabled="disabled" @click="loadTeachers(true)">搜索教师</el-button>
      </div>
      <el-select v-model="teacherId" class="full-width" filterable :loading="teacherLoading" :disabled="disabled" placeholder="选择指导教师">
        <el-option v-for="teacher in teachers" :key="teacher.id" :label="`${teacher.real_name} · ${teacher.school_name}`" :value="teacher.id">
          <span>{{ teacher.real_name }}</span><small>{{ teacher.school_name }} {{ teacher.title || '' }}</small>
        </el-option>
      </el-select>
      <div class="teacher-footer">
        <span>共 {{ teacherTotal }} 位可选教师</span>
        <el-pagination v-model:current-page="teacherPage" small layout="prev, pager, next" :page-size="20" :total="teacherTotal" :disabled="disabled" @current-change="loadTeachers(false)" />
      </div>
    </div>
    <div class="remark-row">
      <label>报名说明</label>
      <el-input v-model="remark" type="textarea" :rows="3" maxlength="500" show-word-limit :disabled="disabled || submitting" placeholder="可选，说明项目背景或补充信息" />
    </div>
    <div class="actions">
      <el-button type="primary" size="large" :loading="submitting" :disabled="disabled || !canRegister" @click="submit">提交报名</el-button>
      <span>提交成功后成员进入冻结状态，教师驳回或管理员驳回后可修改重提。</span>
    </div>
  </section>
</template>

<style scoped>
.submit-panel { display: grid; gap: 18px; padding: 22px; border: 1px solid #dfe8e3; border-radius: 16px; background: #fff; }
.heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.heading h3 { margin: 4px 0 0; color: #18352e; font-size: 20px; }
.eyebrow { color: #6d8f84; font-size: 10px; letter-spacing: .16em; }
.teacher-picker, .remark-row { display: grid; gap: 8px; }
.teacher-picker > label, .remark-row > label { color: #29473f; font-weight: 600; }
.teacher-tools { display: grid; grid-template-columns: 1fr auto; gap: 8px; }
.teacher-footer { display: flex; align-items: center; justify-content: space-between; color: #7c8f87; font-size: 12px; }
.teacher-footer small { color: #7c8f87; }
.full-width { width: 100%; }
.actions { display: flex; align-items: center; gap: 16px; }
.actions span { color: #7a8c84; font-size: 12px; line-height: 1.6; }
@media (max-width: 700px) { .teacher-tools { grid-template-columns: 1fr; } .actions { align-items: flex-start; flex-direction: column; } }
</style>
