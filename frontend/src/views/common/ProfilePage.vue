<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormItemRule, FormRules } from 'element-plus'
import {
  createProfileIdempotencyKey,
  fetchMyProfile,
  isDuplicateStudentNo,
  isProfileConflict,
  isUnknownConflict,
  profileErrorMessage,
  updateMyProfile,
} from '../../api/profile'
import {
  PROFILE_EMPTY_FORM,
  PROFILE_FIELD_DEFINITIONS,
  PROFILE_MESSAGES,
  PROFILE_ROLE_LABELS,
  profileFieldKeysFor,
} from '../../types/profile'
import type {
  MyProfile,
  ProfileFieldKey,
  ProfileUpdatePayload,
} from '../../types/profile'

const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const saveError = ref('')
const conflictNotice = ref(false)
const profile = ref<MyProfile | null>(null)
const formRef = ref<FormInstance>()

/** 表单只承载当前角色可见的字段，未出现的字段不会进入请求体 */
const form = reactive<Record<ProfileFieldKey, string>>({ ...PROFILE_EMPTY_FORM })

const fieldKeys = computed<ProfileFieldKey[]>(() =>
  profile.value ? profileFieldKeysFor(profile.value.user_type) : [],
)

const roleLabel = computed(() =>
  profile.value
    ? PROFILE_ROLE_LABELS[profile.value.user_type] ?? '个人档案'
    : '个人档案',
)

const versionLabel = computed(() => (profile.value ? `v${profile.value.version}` : '-'))

/** 与服务端长度规则、必填规则保持一致，避免前端放过、后端报 400 */
const buildRules = (): FormRules => {
  const rules: FormRules = {}
  for (const key of fieldKeys.value) {
    const definition = PROFILE_FIELD_DEFINITIONS[key]
    const fieldRules: FormItemRule[] = [
      {
        required: definition.required,
        message: `请输入${definition.label}`,
        trigger: 'blur',
      },
      {
        max: definition.maxLength,
        message: `${definition.label}长度不能超过 ${definition.maxLength} 个字符`,
        trigger: 'blur',
      },
    ]
    if (key === 'student_no') {
      fieldRules.push({
        pattern: /^\S+$/,
        message: '学号不能包含空格',
        trigger: 'blur',
      })
    }
    rules[key] = fieldRules
  }
  return rules
}

const rules = computed<FormRules>(buildRules)

const applyProfileToForm = (data: MyProfile): void => {
  for (const key of Object.keys(PROFILE_EMPTY_FORM) as ProfileFieldKey[]) {
    const value = data[key]
    form[key] = typeof value === 'string' ? value : ''
  }
  // 清空不可见字段，防止切换角色后残留学生/教师字段被提交
  const visible = profileFieldKeysFor(data.user_type)
  for (const key of Object.keys(PROFILE_EMPTY_FORM) as ProfileFieldKey[]) {
    if (!visible.includes(key)) {
      form[key] = ''
    }
  }
}

const loadProfile = async (): Promise<void> => {
  loading.value = true
  loadError.value = ''
  try {
    const data = await fetchMyProfile()
    profile.value = data
    applyProfileToForm(data)
    formRef.value?.clearValidate()
  } catch (error) {
    loadError.value = profileErrorMessage(error, '档案加载失败')
  } finally {
    loading.value = false
  }
}

/** 只提交发生变化的字段，未改动字段保持库内原值 */
const buildPayload = (): ProfileUpdatePayload | null => {
  if (!profile.value) {
    return null
  }
  const payload: ProfileUpdatePayload = { version: profile.value.version }
  let changed = false
  for (const key of fieldKeys.value) {
    const current = form[key].trim()
    const origin = (profile.value[key] ?? '') as string
    if (current !== origin) {
      payload[key] = current
      changed = true
    }
  }
  return changed ? payload : null
}

const handleSave = async (): Promise<void> => {
  if (!profile.value || !formRef.value) {
    return
  }
  saveError.value = ''
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  const payload = buildPayload()
  if (!payload) {
    ElMessage.info(PROFILE_MESSAGES.emptyPatch)
    return
  }

  saving.value = true
  try {
    const result = await updateMyProfile(payload, createProfileIdempotencyKey())
    // 保存成功后重新拉取档案与版本，确保页面显示的就是服务端结果
    await loadProfile()
    conflictNotice.value = false
    ElMessage.success(`${PROFILE_MESSAGES.saved}（v${result.version}）`)
  } catch (error) {
    // 学号重复与版本冲突都是 HTTP 409，必须先按业务码区分，否则会把“学号已存在”误报成版本冲突
    if (isDuplicateStudentNo(error)) {
      conflictNotice.value = false
      saveError.value = PROFILE_MESSAGES.duplicateStudentNo
      ElMessage.error(saveError.value)
    } else if (isProfileConflict(error)) {
      // 版本冲突：不静默覆盖，提示刷新后重试
      conflictNotice.value = true
      saveError.value = PROFILE_MESSAGES.conflict
      ElMessage.error(PROFILE_MESSAGES.conflict)
    } else if (isUnknownConflict(error)) {
      // 未知 409：既不是版本冲突也不是学号重复，明确提示而不是冒充版本冲突
      conflictNotice.value = false
      saveError.value = PROFILE_MESSAGES.unknownConflict
      ElMessage.error(saveError.value)
    } else {
      saveError.value = profileErrorMessage(error)
      ElMessage.error(saveError.value)
    }
  } finally {
    saving.value = false
  }
}

const handleReload = async (): Promise<void> => {
  conflictNotice.value = false
  saveError.value = ''
  await loadProfile()
  ElMessage.success('已刷新为服务端最新档案')
}

onMounted(() => {
  void loadProfile()
})
</script>

<template>
  <main class="profile-page">
    <header class="profile-header">
      <div>
        <p class="eyebrow">Personal profile</p>
        <h1>个人档案</h1>
        <p class="subtitle">
          只维护当前角色对应的档案字段；账号、角色、权限与账号状态不在此页面开放修改。
        </p>
      </div>
      <div class="header-actions">
        <el-tag v-if="profile" type="success" effect="plain">{{ roleLabel }}</el-tag>
        <el-button plain :loading="loading" @click="handleReload">刷新档案</el-button>
      </div>
    </header>

    <el-alert
      v-if="loadError"
      class="profile-alert"
      type="error"
      :title="loadError"
      show-icon
      :closable="false"
    />

    <el-alert
      v-if="conflictNotice"
      class="profile-alert"
      type="warning"
      :title="PROFILE_MESSAGES.conflict"
      show-icon
      :closable="false"
    >
      <template #default>
        <span>服务端档案版本已变化，本次修改未写入。请刷新后基于最新资料重新编辑。</span>
      </template>
    </el-alert>

    <section v-if="profile" class="profile-card">
      <div class="identity">
        <div class="identity-line">
          <span class="label">账号</span>
          <span class="value">{{ profile.username }}</span>
        </div>
        <div class="identity-line">
          <span class="label">用户类型</span>
          <span class="value">{{ profile.user_type || '-' }}</span>
        </div>
        <div class="identity-line">
          <span class="label">档案版本</span>
          <span class="value">{{ versionLabel }}</span>
        </div>
        <div class="identity-line">
          <span class="label">角色</span>
          <span class="value">
            <el-tag
              v-for="role in profile.roles"
              :key="role"
              size="small"
              effect="plain"
              class="role-tag"
            >
              {{ role }}
            </el-tag>
            <span v-if="profile.roles.length === 0">-</span>
          </span>
        </div>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="profile-form"
        @submit.prevent
      >
        <el-form-item
          v-for="key in fieldKeys"
          :key="key"
          :label="PROFILE_FIELD_DEFINITIONS[key].label"
          :prop="key"
        >
          <el-input
            v-model="form[key]"
            :maxlength="PROFILE_FIELD_DEFINITIONS[key].maxLength"
            :placeholder="PROFILE_FIELD_DEFINITIONS[key].placeholder"
            clearable
          />
          <p class="field-hint">{{ PROFILE_FIELD_DEFINITIONS[key].hint }}</p>
        </el-form-item>
      </el-form>

      <div v-if="profile.skill_tags.length > 0" class="readonly-block">
        <p class="label">技能标签（旧数据只读兼容）</p>
        <div class="tag-list">
          <el-tag v-for="tag in profile.skill_tags" :key="tag" type="info" effect="plain">
            {{ tag }}
          </el-tag>
        </div>
        <p class="field-hint">技能标签、研究方向与教师指导上限不属于本阶段个人档案，页面不提供编辑入口。</p>
      </div>

      <div class="footer">
        <span class="version-note">修改将使用 version {{ versionLabel }} 做乐观锁校验并写入审计日志</span>
        <el-button type="primary" :loading="saving" @click="handleSave">保存档案</el-button>
      </div>

      <p v-if="saveError" class="save-error">{{ saveError }}</p>
    </section>

    <el-skeleton v-else-if="loading" :rows="5" animated />
  </main>
</template>

<style scoped>
.profile-page {
  max-width: 860px;
  margin: 0 auto;
  padding: 40px 24px 72px;
  color: #18332c;
}

.profile-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.eyebrow {
  margin: 0;
  color: #7f8c87;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.profile-header h1 {
  margin: 8px 0 6px;
  font-family: Georgia, "Songti SC", serif;
  font-size: 32px;
  font-weight: 500;
}

.subtitle {
  max-width: 520px;
  margin: 0;
  color: #6d7c77;
  font-size: 13px;
  line-height: 1.7;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.profile-alert {
  margin-bottom: 16px;
}

.profile-card {
  padding: 28px;
  border: 1px solid rgba(23, 63, 53, 0.1);
  border-radius: 16px 16px 16px 4px;
  background: #ffffff;
  box-shadow: 0 16px 40px rgba(22, 52, 44, 0.06);
}

.identity {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 24px;
  padding-bottom: 20px;
  border-bottom: 1px dashed rgba(23, 63, 53, 0.12);
}

.identity-line {
  display: flex;
  gap: 10px;
  font-size: 13px;
}

.label {
  color: #8b9692;
  font-size: 12px;
  white-space: nowrap;
}

.value {
  color: #24443b;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
}

.role-tag {
  margin-right: 6px;
}

.profile-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px 20px;
  margin-top: 22px;
}

.profile-form :deep(.el-form-item) {
  margin-bottom: 12px;
}

.field-hint {
  margin: 4px 0 0;
  color: #94a09b;
  font-size: 11px;
  line-height: 1.5;
}

.readonly-block {
  margin-top: 12px;
  padding-top: 16px;
  border-top: 1px dashed rgba(23, 63, 53, 0.12);
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px solid rgba(23, 63, 53, 0.1);
}

.version-note {
  color: #8b9692;
  font-size: 12px;
}

.save-error {
  margin: 12px 0 0;
  color: #c45656;
  font-size: 12px;
}

@media (max-width: 720px) {
  .identity,
  .profile-form {
    grid-template-columns: 1fr;
  }
}
</style>
