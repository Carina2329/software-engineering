<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormItemRule, FormRules } from 'element-plus'
import {
  createProfileIdempotencyKey,
  fetchMyProfile,
  isDuplicateStudentNo,
  isProfileConflict,
  isSchoolConflict,
  isUnknownConflict,
  profileErrorMessage,
  updateMyProfile,
} from '../../../api/profile'
import { fetchSchools, schoolOptionLabel } from '../../../api/schools'
import {
  PROFILE_EMPTY_FORM,
  PROFILE_FIELD_DEFINITIONS,
  PROFILE_MESSAGES,
  PROFILE_ROLE_LABELS,
  SCHOOL_STATUS_META,
  canChangeSchool,
  profileFieldKeysFor,
  supportsSchool,
} from '../../../types/profile'
import type {
  MyProfile,
  ProfileFieldKey,
  ProfileUpdatePayload,
  SchoolOption,
  SchoolVerificationStatus,
} from '../../../types/profile'

/**
 * 个人档案卡片（BIZ-PROFILE-001）。
 *
 * 用于在登录后的首页直接展示与编辑“当前角色对应的档案字段”，
 * 不依赖公共路由：验收页面无法通过 router/index.ts 打开时，
 * 该组件本身即可完成“查看 → 编辑 → 版本校验保存 → 刷新确认”的闭环。
 */

const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const saveError = ref('')
const conflictNotice = ref(false)
const savedVersion = ref<number | null>(null)
const profile = ref<MyProfile | null>(null)
const formRef = ref<FormInstance>()

/** 表单只承载当前角色可见的字段，未出现的字段不会进入请求体 */
const form = reactive<Record<ProfileFieldKey, string>>({ ...PROFILE_EMPTY_FORM })

const fieldKeys = computed<ProfileFieldKey[]>(() =>
  profile.value ? profileFieldKeysFor(profile.value.user_type) : [],
)

const roleLabel = computed(() =>
  profile.value ? PROFILE_ROLE_LABELS[profile.value.user_type] ?? '个人档案' : '个人档案',
)

const versionLabel = computed(() => (profile.value ? `v${profile.value.version}` : '-'))

/** 学校归属（学生/教师才维护），选项来自组织与范围任务的 EP-085 学校主档 */
const schoolOptions = ref<SchoolOption[]>([])
const schoolLoading = ref(false)
const schoolOptionsLoaded = ref(false)
const selectedSchoolId = ref('')

const schoolSupported = computed(() =>
  profile.value ? supportsSchool(profile.value.user_type) : false,
)

const schoolEditable = computed(() =>
  profile.value ? canChangeSchool(profile.value.school_verification_status) : false,
)

const schoolStatusMeta = computed(() => {
  const status = profile.value?.school_verification_status
  if (!status) {
    return null
  }
  return SCHOOL_STATUS_META[status as SchoolVerificationStatus] ?? null
})

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

/**
 * 学校下拉：默认加载一页，支持按名称远程搜索。
 * 学校主档查询失败不能阻塞档案编辑，只提示不弹错。
 */
const loadSchoolOptions = async (keyword = ''): Promise<void> => {
  schoolLoading.value = true
  try {
    const page = await fetchSchools({ keyword })
    schoolOptions.value = page.items
    schoolOptionsLoaded.value = true
  } catch {
    schoolOptions.value = []
  } finally {
    schoolLoading.value = false
  }
}

const handleSchoolSearch = (keyword: string): void => {
  void loadSchoolOptions(keyword)
}

const loadProfile = async (): Promise<void> => {
  loading.value = true
  loadError.value = ''
  try {
    const data = await fetchMyProfile()
    profile.value = data
    applyProfileToForm(data)
    selectedSchoolId.value = data.school_id ?? ''
    formRef.value?.clearValidate()
    if (supportsSchool(data.user_type) && !schoolOptionsLoaded.value) {
      void loadSchoolOptions()
    }
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
  // 选校/改校：只有当前角色维护学校、且选项与库内不同才提交 school_id
  if (
    schoolSupported.value
    && schoolEditable.value
    && selectedSchoolId.value
    && selectedSchoolId.value !== (profile.value.school_id ?? '')
  ) {
    payload.school_id = selectedSchoolId.value
    changed = true
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
    // 保存成功后重新拉取档案与版本，确保卡片显示的就是服务端结果
    await loadProfile()
    conflictNotice.value = false
    savedVersion.value = result.version
    ElMessage.success(`${PROFILE_MESSAGES.saved}（v${result.version}）`)
  } catch (error) {
    // 学号重复与版本冲突都是 HTTP 409，必须先按业务码区分，否则会把“学号已存在”误报成版本冲突
    if (isDuplicateStudentNo(error)) {
      conflictNotice.value = false
      saveError.value = PROFILE_MESSAGES.duplicateStudentNo
      ElMessage.error(saveError.value)
    } else if (isSchoolConflict(error)) {
      // 学校归属冲突与版本冲突共用 20001，必须展示服务端的具体原因
      conflictNotice.value = false
      saveError.value = profileErrorMessage(error)
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
  <article class="summary-card profile-editor-card">
    <div class="card-heading">
      <p class="card-label">个人档案</p>
      <span class="heading-meta">
        <el-tag v-if="profile" type="success" effect="plain" size="small">{{ roleLabel }}</el-tag>
        <span class="version-pill">档案版本 {{ versionLabel }}</span>
      </span>
    </div>

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

    <el-skeleton v-if="loading && !profile" :rows="4" animated />

    <template v-else-if="profile">
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

      <div v-if="schoolSupported" class="school-block">
        <p class="card-label">学校归属</p>
        <div class="school-state">
          <span class="school-name">{{ profile.school_name || '未选择学校' }}</span>
          <el-tag
            v-if="schoolStatusMeta"
            :type="schoolStatusMeta.tagType"
            effect="plain"
            size="small"
          >
            {{ schoolStatusMeta.label }}
          </el-tag>
          <span v-else class="school-plain">尚未提交审核</span>
        </div>
        <p v-if="profile.school_verification_status === 'REJECTED' && profile.school_review_reason" class="school-reason">
          驳回原因：{{ profile.school_review_reason }}
        </p>

        <el-select
          v-if="schoolEditable"
          v-model="selectedSchoolId"
          class="school-select"
          filterable
          remote
          reserve-keyword
          :remote-method="handleSchoolSearch"
          :loading="schoolLoading"
          placeholder="输入学校名称搜索并选择"
        >
          <el-option
            v-for="school in schoolOptions"
            :key="school.id"
            :label="schoolOptionLabel(school)"
            :value="school.id"
          />
        </el-select>

        <p class="field-hint">
          {{ schoolEditable
            ? '选择或更换学校后将提交管理员审核，审核通过前不能再次更换。'
            : (schoolStatusMeta ? schoolStatusMeta.description : '') }}
        </p>
      </div>

      <div v-if="profile.skill_tags.length > 0" class="readonly-block">
        <p class="card-label">技能标签（旧数据只读兼容）</p>
        <div class="tag-list">
          <el-tag v-for="tag in profile.skill_tags" :key="tag" type="info" effect="plain">
            {{ tag }}
          </el-tag>
        </div>
      </div>

      <div class="footer">
        <span class="version-note">
          修改将使用 version {{ versionLabel }} 做乐观锁校验并写入审计日志
        </span>
        <div class="footer-actions">
          <el-button plain size="small" :loading="loading" @click="handleReload">刷新档案</el-button>
          <el-button type="primary" size="small" :loading="saving" @click="handleSave">
            保存档案
          </el-button>
        </div>
      </div>

      <p v-if="savedVersion !== null && !saveError" class="save-result">
        最近一次保存成功，服务端当前版本 v{{ savedVersion }}
      </p>
      <p v-if="saveError" class="save-error">{{ saveError }}</p>
    </template>
  </article>
</template>

<style scoped>
/* 作为首页 dashboard-grid 的直接子元素时整行占满，不需要改首页样式 */
.profile-editor-card {
  grid-column: 1 / -1;
  min-height: 0;
  padding: 24px;
  border: 1px solid rgba(23, 63, 53, 0.1);
  border-radius: 15px 15px 15px 4px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 14px 36px rgba(22, 52, 44, 0.045);
  color: #18332c;
}

.card-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.card-label {
  margin: 0;
  color: #7f8c87;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.heading-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.version-pill {
  color: #345b50;
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 11px;
}

.profile-alert {
  margin-top: 14px;
}

.profile-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 20px;
  margin-top: 18px;
}

.profile-form :deep(.el-form-item) {
  margin-bottom: 10px;
}

.field-hint {
  margin: 2px 0 0;
  color: #94a09b;
  font-size: 11px;
  line-height: 1.5;
}

.readonly-block {
  margin-top: 6px;
  padding-top: 14px;
  border-top: 1px dashed rgba(23, 63, 53, 0.12);
}

.school-block {
  margin-top: 8px;
  padding-top: 14px;
  border-top: 1px dashed rgba(23, 63, 53, 0.12);
}

.school-state {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}

.school-name {
  color: #24443b;
  font-size: 14px;
  font-weight: 600;
}

.school-plain {
  color: #8b9692;
  font-size: 12px;
}

.school-reason {
  margin: 8px 0 0;
  color: #c45656;
  font-size: 12px;
}

.school-select {
  width: 100%;
  margin-top: 10px;
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
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid rgba(23, 63, 53, 0.1);
}

.footer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.version-note {
  color: #8b9692;
  font-size: 12px;
}

.save-result {
  margin: 10px 0 0;
  color: #3d8a70;
  font-size: 12px;
}

.save-error {
  margin: 10px 0 0;
  color: #c45656;
  font-size: 12px;
}

@media (max-width: 760px) {
  .profile-form {
    grid-template-columns: 1fr;
  }
}
</style>
