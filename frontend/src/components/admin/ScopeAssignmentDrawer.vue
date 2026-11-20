<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  createRoleAssignment,
  updateRoleAssignment,
} from '../../api/organization'
import { createIdempotencyKey, getErrorMessage } from '../../api/adminRbac'
import {
  ROLE_SCOPE_LABELS,
  SCOPE_ROLE_OPTIONS,
} from '../../types/organization'
import type {
  RoleScopeType,
  ScopedRoleCode,
} from '../../types/organization'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    userId?: string
    userLabel?: string
  }>(),
  {
    userId: '',
    userLabel: '',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

const activeTab = ref<'create' | 'update'>('create')
const createLoading = ref(false)
const updateLoading = ref(false)
const createFormRef = ref<FormInstance>()
const updateFormRef = ref<FormInstance>()

const createForm = reactive({
  user_id: '',
  role_code: 'EVENT_MANAGER' as ScopedRoleCode,
  scope_type: 'ORGANIZATION' as RoleScopeType,
  scope_id: '',
  valid_from: '',
  valid_to: '',
})

const updateForm = reactive({
  assignment_id: '',
  version: 0,
  active: true,
  valid_to: '',
  change_scope: false,
  scope_type: 'ORGANIZATION' as RoleScopeType,
  scope_id: '',
})

const selectedRole = computed(
  () => SCOPE_ROLE_OPTIONS.find((role) => role.value === createForm.role_code)
    ?? SCOPE_ROLE_OPTIONS[0],
)

const createScopeOptions = computed(() =>
  selectedRole.value.scopes.map((value) => ({
    value,
    label: ROLE_SCOPE_LABELS[value],
  })),
)

const createRules: FormRules = {
  user_id: [{ required: true, message: '请输入目标用户 ID', trigger: 'blur' }],
  role_code: [{ required: true, message: '请选择范围角色', trigger: 'change' }],
  scope_type: [{ required: true, message: '请选择范围类型', trigger: 'change' }],
  scope_id: [{ required: true, message: '请输入范围 ID', trigger: 'blur' }],
}

const updateRules: FormRules = {
  assignment_id: [{ required: true, message: '请输入授权 ID', trigger: 'blur' }],
  version: [{ required: true, message: '请输入当前版本', trigger: 'blur' }],
}

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const resetForms = (): void => {
  createForm.user_id = props.userId
  createForm.role_code = 'EVENT_MANAGER'
  createForm.scope_type = 'ORGANIZATION'
  createForm.scope_id = ''
  createForm.valid_from = ''
  createForm.valid_to = ''
  updateForm.assignment_id = ''
  updateForm.version = 0
  updateForm.active = true
  updateForm.valid_to = ''
  updateForm.change_scope = false
  updateForm.scope_type = 'ORGANIZATION'
  updateForm.scope_id = ''
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      resetForms()
      activeTab.value = 'create'
    }
  },
)

watch(
  () => createForm.role_code,
  () => {
    if (!selectedRole.value.scopes.includes(createForm.scope_type)) {
      createForm.scope_type = selectedRole.value.scopes[0]
    }
  },
)

const handleCreate = async (): Promise<void> => {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  createLoading.value = true
  try {
    await createRoleAssignment(
      {
        user_id: createForm.user_id.trim(),
        role_code: createForm.role_code,
        scope_type: createForm.scope_type,
        scope_id: createForm.scope_id.trim(),
        valid_from: createForm.valid_from || null,
        valid_to: createForm.valid_to || null,
      },
      createIdempotencyKey(),
    )
    ElMessage.success('范围授权已创建，目标用户旧会话已失效')
    emit('saved')
    visible.value = false
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '创建范围授权失败'))
  } finally {
    createLoading.value = false
  }
}

const handleUpdate = async (): Promise<void> => {
  const valid = await updateFormRef.value?.validate().catch(() => false)
  if (!valid) return
  if (updateForm.change_scope && !updateForm.scope_id.trim()) {
    ElMessage.warning('变更范围时必须填写新范围 ID')
    return
  }
  updateLoading.value = true
  try {
    await updateRoleAssignment(
      updateForm.assignment_id.trim(),
      {
        active: updateForm.active,
        valid_to: updateForm.valid_to || null,
        ...(updateForm.change_scope
          ? {
              scope_type: updateForm.scope_type,
              scope_id: updateForm.scope_id.trim(),
            }
          : {}),
        version: updateForm.version,
      },
      createIdempotencyKey(),
    )
    ElMessage.success('范围授权已更新，目标用户旧会话已失效')
    emit('saved')
    visible.value = false
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '更新范围授权失败'))
  } finally {
    updateLoading.value = false
  }
}
</script>

<template>
  <el-drawer v-model="visible" title="范围授权" size="560px" destroy-on-close>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="范围授权决定管理员能管哪个组织、省份或赛事，与基础角色的菜单权限分开维护。"
    />
    <el-alert
      v-if="userLabel"
      class="user-alert"
      type="success"
      :closable="false"
      :title="`目标用户：${userLabel}（${userId}）`"
    />

    <el-tabs v-model="activeTab" class="assignment-tabs">
      <el-tab-pane label="新建授权" name="create">
        <el-form
          ref="createFormRef"
          :model="createForm"
          :rules="createRules"
          label-width="110px"
        >
          <el-form-item label="目标用户 ID" prop="user_id">
            <el-input
              v-model="createForm.user_id"
              :disabled="Boolean(userId)"
              placeholder="输入用户数字 ID"
            />
          </el-form-item>
          <el-form-item label="范围角色" prop="role_code">
            <el-select v-model="createForm.role_code" class="full-width">
              <el-option
                v-for="role in SCOPE_ROLE_OPTIONS"
                :key="role.value"
                :label="`${role.label}（${role.value}）`"
                :value="role.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="范围类型" prop="scope_type">
            <el-select v-model="createForm.scope_type" class="full-width">
              <el-option
                v-for="option in createScopeOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="范围 ID" prop="scope_id">
            <el-input
              v-model="createForm.scope_id"
              placeholder="组织 ID、省份编码或赛事 ID"
            />
          </el-form-item>
          <el-form-item label="生效时间">
            <el-date-picker
              v-model="createForm.valid_from"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="留空表示立即生效"
              class="full-width"
            />
          </el-form-item>
          <el-form-item label="失效时间">
            <el-date-picker
              v-model="createForm.valid_to"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="留空表示长期有效"
              class="full-width"
            />
          </el-form-item>
          <el-button type="primary" :loading="createLoading" @click="handleCreate">
            创建范围授权
          </el-button>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="修改已有授权" name="update">
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="当前接口未提供授权列表。请从创建授权结果或后端管理数据中获取授权 ID 和 version。"
        />
        <el-form
          ref="updateFormRef"
          :model="updateForm"
          :rules="updateRules"
          label-width="110px"
          class="update-form"
        >
          <el-form-item label="授权 ID" prop="assignment_id">
            <el-input v-model="updateForm.assignment_id" />
          </el-form-item>
          <el-form-item label="当前版本" prop="version">
            <el-input-number v-model="updateForm.version" :min="0" />
          </el-form-item>
          <el-form-item label="启用状态">
            <el-switch
              v-model="updateForm.active"
              active-text="启用"
              inactive-text="停用"
            />
          </el-form-item>
          <el-form-item label="失效时间">
            <el-date-picker
              v-model="updateForm.valid_to"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="留空表示长期有效"
              class="full-width"
            />
          </el-form-item>
          <el-form-item label="变更范围">
            <el-switch v-model="updateForm.change_scope" />
          </el-form-item>
          <template v-if="updateForm.change_scope">
            <el-form-item label="新范围类型">
              <el-select v-model="updateForm.scope_type" class="full-width">
                <el-option
                  v-for="(label, value) in ROLE_SCOPE_LABELS"
                  :key="value"
                  :label="label"
                  :value="value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="新范围 ID">
              <el-input v-model="updateForm.scope_id" />
            </el-form-item>
          </template>
          <el-button type="primary" :loading="updateLoading" @click="handleUpdate">
            更新范围授权
          </el-button>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </el-drawer>
</template>

<style scoped>
.user-alert,
.assignment-tabs,
.update-form {
  margin-top: 16px;
}

.full-width {
  width: 100%;
}
</style>
