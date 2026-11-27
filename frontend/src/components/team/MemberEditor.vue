<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { TeamMember, TeamMemberInput } from '../../types/team'

interface MemberRow extends TeamMemberInput { member_id?: string; persisted: boolean }

const props = withDefaults(defineProps<{ members: TeamMember[]; disabled?: boolean }>(), { disabled: false })
const emit = defineEmits<{ 'update:members': [members: TeamMemberInput[]]; remove: [memberId: string] }>()
const rows = ref<MemberRow[]>([])
const leader = computed(() => props.members.find((member) => member.is_leader) ?? null)
const canAdd = computed(() => rows.value.length < 4 && !props.disabled)

watch(() => props.members, (members) => {
  rows.value = members.filter((member) => !member.is_leader).map((member) => ({
    member_id: member.member_id,
    member_name: member.member_name,
    school_id: member.school_id,
    student_no: member.student_no,
    persisted: true,
  }))
  emitMembers()
}, { immediate: true, deep: true })

function emitMembers(): void {
  emit('update:members', rows.value.map((row) => ({
    member_id: row.member_id,
    member_name: row.member_name.trim(),
    school_id: row.school_id.trim(),
    student_no: row.student_no.trim().toUpperCase(),
  })))
}
function addMember(): void {
  if (!canAdd.value) return
  rows.value.push({ member_name: '', school_id: '', student_no: '', persisted: false })
  emitMembers()
}
function removeRow(row: MemberRow, index: number): void {
  if (props.disabled) return
  if (row.persisted && row.member_id) { emit('remove', row.member_id); return }
  rows.value.splice(index, 1)
  emitMembers()
}
function validate(): boolean {
  const keys = new Set<string>()
  for (const row of rows.value) {
    if (!row.member_name.trim() || !row.school_id.trim() || !row.student_no.trim()) return false
    const key = `${row.school_id.trim()}:${row.student_no.trim().toUpperCase()}`
    if (keys.has(key)) return false
    keys.add(key)
  }
  return rows.value.length <= 4
}
defineExpose({ validate })
</script>

<template>
  <section class="member-editor">
    <div class="section-title">
      <div><span class="eyebrow">TEAM MEMBERS</span><h3>队伍成员</h3></div>
      <el-button v-if="!disabled" type="primary" plain :disabled="!canAdd" @click="addMember">添加普通队员</el-button>
    </div>
    <el-table :data="leader ? [leader] : []" border>
      <el-table-column prop="member_name" label="队长" min-width="150" />
      <el-table-column prop="school_id" label="学校 ID" width="130" />
      <el-table-column prop="student_no" label="学号" min-width="150" />
      <el-table-column label="身份" width="90"><template #default><el-tag type="warning">队长</el-tag></template></el-table-column>
    </el-table>
    <el-table v-if="disabled && rows.length" :data="rows" border>
      <el-table-column prop="member_name" label="姓名" />
      <el-table-column prop="school_id" label="学校 ID" />
      <el-table-column prop="student_no" label="学号" />
      <el-table-column label="身份" width="90"><template #default><el-tag type="info">队员</el-tag></template></el-table-column>
    </el-table>
    <div v-else-if="rows.length" class="member-list">
      <div v-for="(row, index) in rows" :key="row.member_id || index" class="member-row">
        <el-input v-model="row.member_name" maxlength="32" placeholder="姓名" :disabled="disabled" @input="emitMembers" />
        <el-input v-model="row.school_id" placeholder="学校 ID" :disabled="disabled" @input="emitMembers" />
        <el-input v-model="row.student_no" maxlength="20" placeholder="学号" :disabled="disabled" @input="emitMembers" />
        <el-button link type="danger" :disabled="disabled" @click="removeRow(row, index)">移出</el-button>
      </div>
    </div>
    <el-empty v-else :description="disabled ? '暂无普通队员' : '暂无普通队员，可添加最多 4 人'" :image-size="64" />
    <p class="member-tip">队伍最多 5 人；同一届次同校同学号不能重复。普通队员无需平台账号。</p>
  </section>
</template>

<style scoped>
.member-editor { display: grid; gap: 14px; }
.section-title { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.eyebrow { color: #6d8f84; font-size: 10px; letter-spacing: .16em; }
h3 { margin: 4px 0 0; color: #18352e; font-size: 20px; }
.member-list { display: grid; gap: 10px; }
.member-row { display: grid; grid-template-columns: 1.1fr 1fr 1.15fr auto; gap: 10px; align-items: center; padding: 12px; border: 1px solid #e2e9e5; border-radius: 12px; background: #fbfdfc; }
.member-tip { margin: 0; color: #758880; font-size: 12px; line-height: 1.6; }
@media (max-width: 760px) { .member-row { grid-template-columns: 1fr; } }
</style>
