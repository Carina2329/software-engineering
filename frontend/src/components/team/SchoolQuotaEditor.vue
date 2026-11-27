<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { SchoolQuotaItem } from '../../types/team'
interface SchoolOption { id: string; school_name: string; province_name: string }
const props = withDefaults(defineProps<{
  modelValue: SchoolQuotaItem[]
  schools?: SchoolOption[]
  readOnly?: boolean
}>(), { schools: () => [], readOnly: false })
const emit = defineEmits<{ 'update:modelValue': [value: SchoolQuotaItem[]] }>()
const rows = ref<SchoolQuotaItem[]>([])
watch(() => props.modelValue, (value) => { rows.value = value.map((item) => ({ ...item })) }, { immediate: true, deep: true })
function emitRows(): void { emit('update:modelValue', rows.value.map((item) => ({ ...item }))) }
function addRow(): void { if (props.readOnly) return; rows.value.push({ school_id: '', max_teams: null, used_teams: 0 }); emitRows() }
function removeRow(index: number): void { if (props.readOnly) return; rows.value.splice(index, 1); emitRows() }
function toggleUnlimited(row: SchoolQuotaItem, unlimited: boolean): void { row.max_teams = unlimited ? null : Math.max(row.used_teams ?? 0, 1); emitRows() }
function validate(): boolean {
  const ids = new Set<string>()
  for (const row of rows.value) {
    if (!row.school_id.trim()) { ElMessage.warning('请选择学校'); return false }
    if (ids.has(row.school_id.trim())) { ElMessage.warning('学校不能重复配置'); return false }
    ids.add(row.school_id.trim())
    const used = row.used_teams ?? 0
    const schoolName = props.schools.find((school) => school.id === row.school_id)?.school_name
      || `学校 ${row.school_id}`
    if (row.max_teams !== null && row.max_teams < used) { ElMessage.warning(`${schoolName} 的名额不能低于已占用数 ${used}`); return false }
    if (row.max_teams !== null && row.max_teams < 1) { ElMessage.warning('有限名额必须至少为 1'); return false }
  }
  return true
}
function clearAll(): void { if (props.readOnly) return; rows.value = []; emitRows() }
defineExpose({ validate })
</script>
<template>
  <section class="quota-editor">
    <div class="heading"><div><span class="eyebrow">SCHOOL QUOTAS</span><h3>学校名额配置</h3></div>
      <div class="heading-actions"><el-button :disabled="readOnly" @click="clearAll">清空配置</el-button><el-button type="primary" plain :disabled="readOnly" @click="addRow">添加学校名额</el-button></div>
    </div>
    <div v-if="rows.length" class="quota-list">
      <div v-for="(row, index) in rows" :key="index" class="quota-row">
        <el-select v-model="row.school_id" filterable placeholder="选择学校" :disabled="readOnly" @change="emitRows">
          <el-option
            v-for="school in schools"
            :key="school.id"
            :value="school.id"
            :label="`${school.school_name}（${school.province_name}）`"
          />
        </el-select>
        <el-checkbox :model-value="row.max_teams === null" :disabled="readOnly" @change="toggleUnlimited(row, Boolean($event))">不限量</el-checkbox>
        <el-input-number v-model="row.max_teams" :min="Math.max(row.used_teams ?? 0, 1)" :disabled="readOnly || row.max_teams === null" @change="emitRows" />
        <span class="used">已占用 {{ row.used_teams ?? 0 }}</span>
        <el-button link type="danger" :disabled="readOnly" @click="removeRow(index)">删除</el-button>
      </div>
    </div>
    <el-empty v-else description="未配置学校名额，空数组表示清除未占用配置" :image-size="58" />
    <p class="tip">max_teams=null 表示不限量；保存时采用完整替换语义，提交前会校验名额不能低于已占用数。</p>
  </section>
</template>
<style scoped>
.quota-editor { display: grid; gap: 14px; padding: 18px; border: 1px solid #dfe8e3; border-radius: 14px; background: #fbfdfc; }
.heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }.heading h3 { margin: 4px 0 0; color: #18352e; font-size: 19px; }
.heading-actions { display: flex; gap: 8px; }.eyebrow { color: #6d8f84; font-size: 10px; letter-spacing: .16em; }
.quota-list { display: grid; gap: 10px; }.quota-row { display: grid; grid-template-columns: 1fr auto 160px auto auto; gap: 10px; align-items: center; padding: 12px; border: 1px solid #e2e9e5; border-radius: 11px; background: #fff; }
.used { color: #778a82; font-size: 12px; white-space: nowrap; }.tip { margin: 0; color: #778a82; font-size: 12px; line-height: 1.6; }
@media (max-width: 760px) { .quota-row { grid-template-columns: 1fr; }.heading { align-items: flex-start; flex-direction: column; } }
</style>
