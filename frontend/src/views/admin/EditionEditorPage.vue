<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  createContestEdition,
  createContestIdempotencyKey,
  createEditionMilestone,
  getContest,
  getContestEdition,
  getContestErrorMessage,
  listContestEditions,
  listEditionMilestones,
  publishContestEdition,
  updateContestEdition,
  updateEditionMilestone,
} from '../../api/contest'
import {
  EDITION_STATUS_LABELS,
  EDITION_VISIBILITY_OPTIONS,
  ENTRY_MODE_OPTIONS,
  CONTEST_LEVEL_OPTIONS,
  MILESTONE_NODE_OPTIONS,
  TEAM_SIZE_OPTIONS,
  TOPIC_ALLOCATION_OPTIONS,
  TOPIC_MODE_OPTIONS,
  formatContestDateTime,
  toApiDateTime,
  toDateTimeInput,
} from '../../types/contest'
import type {
  ContestDetail,
  ContestLevel,
  EditionDetail,
  EditionItem,
  EditionStatus,
  EditionVisibility,
  EntryMode,
  Milestone,
  MilestoneNodeType,
  TeamSizeMode,
  TopicAllocationMode,
  TopicMode,
} from '../../types/contest'
import { listOrganizations, listSchools } from '../../api/organization'
import type { OrganizationItem, SchoolItem } from '../../types/organization'
import { useCurrentMenus } from '../../layouts/useCurrentMenus'
import SchoolQuotaEditor from '../../components/team/SchoolQuotaEditor.vue'
import type { SchoolQuotaItem } from '../../types/team'

interface EditionForm {
  edition_name: string
  year: number
  owner_org_id: string
  level: ContestLevel
  school_id: string
  province_code: string
  visibility: EditionVisibility
  entry_mode: EntryMode
  cross_school_team_allowed: boolean
  register_deadline: string
  work_deadline: string
  review_start: string
  publish_start: string
  publish_end: string
  team_size_mode: TeamSizeMode
  fixed_team_size: number
  min_team_size: number
  max_team_size: number
  max_teams_per_teacher: number
  topic_mode: TopicMode | ''
  topic_review_required: boolean
  topic_allocation_mode: TopicAllocationMode
  topic_change_limit: number
  version: number | null
}

interface MilestoneForm {
  node_type: MilestoneNodeType
  node_name: string
  start_time: string
  end_time: string
  enabled: boolean
  version: number | null
}

const route = useRoute()
const router = useRouter()
const { ensureCurrentMenus, hasPermission } = useCurrentMenus()
const canCreate = computed(() => hasPermission('edition:create'))
const canUpdate = computed(() => hasPermission('edition:update'))
const canPublish = computed(() => hasPermission('edition:publish'))
const canCreateMilestone = computed(() => hasPermission('edition:milestone:create'))
const canUpdateMilestone = computed(() => hasPermission('edition:milestone:update'))
const canReviewTeams = computed(() => hasPermission('team:approve'))
const canManageEligibility = computed(() => hasPermission('team:eligibility:manage'))

const contestId = computed(() => {
  const value = route.params.contestId
  return Array.isArray(value) ? String(value[0] ?? '') : String(value ?? '')
})

const loading = ref(false)
const contest = ref<ContestDetail | null>(null)
const editions = ref<EditionItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const yearFilter = ref<number | undefined>()
const statusFilter = ref<EditionStatus | ''>('')
const organizations = ref<OrganizationItem[]>([])
const schools = ref<SchoolItem[]>([])

const editorVisible = ref(false)
const editorMode = ref<'create' | 'edit'>('create')
const saving = ref(false)
const formError = ref('')
const formRef = ref<FormInstance>()
const selectedEdition = ref<EditionDetail | null>(null)
const schoolQuotas = ref<SchoolQuotaItem[]>([])
const schoolQuotaEditorRef = ref<{ validate: () => boolean }>()
const idempotencyKey = ref('')
const form = reactive<EditionForm>(emptyEditionForm())
const rules: FormRules = {
  edition_name: [
    { required: true, message: '请输入届次名称', trigger: 'blur' },
    { max: 128, message: '届次名称不能超过 128 个字符', trigger: 'blur' },
  ],
}

const milestones = ref<Milestone[]>([])
const milestoneLoading = ref(false)
const milestoneVisible = ref(false)
const milestoneMode = ref<'create' | 'edit'>('create')
const milestoneSaving = ref(false)
const milestoneError = ref('')
const milestoneFormRef = ref<FormInstance>()
const milestoneId = ref('')
const milestoneKey = ref('')
const milestoneForm = reactive<MilestoneForm>(emptyMilestoneForm())
const milestoneRules: FormRules = {
  node_type: [{ required: true, message: '请选择节点类型', trigger: 'change' }],
  node_name: [
    { required: true, message: '请输入节点名称', trigger: 'blur' },
    { max: 64, message: '节点名称不能超过 64 个字符', trigger: 'blur' },
  ],
  start_time: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  end_time: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
}

const editable = computed(() => {
  if (editorMode.value === 'create') return canCreate.value
  return canUpdate.value && !(selectedEdition.value?.read_only ?? false)
})

const availableOwners = computed(() => {
  const expectedType = form.level === 'SCHOOL'
    ? 'SCHOOL'
    : form.level === 'PROVINCE'
      ? 'PROVINCE_ORGANIZER'
      : 'NATIONAL_ORGANIZER'
  return organizations.value.filter((item) => item.org_type === expectedType)
})

const provinceOptions = computed(() => {
  const values = new Map<string, string>()
  for (const school of schools.value) {
    values.set(school.province_code, school.province_name)
  }
  for (const organization of organizations.value) {
    if (organization.province_code) {
      values.set(organization.province_code, organization.province_code)
    }
  }
  if (form.province_code && !values.has(form.province_code)) {
    values.set(form.province_code, form.province_code)
  }
  return [...values.entries()].map(([value, label]) => ({ value, label }))
})

const loadContest = async (): Promise<void> => {
  if (!contestId.value) return
  contest.value = await getContest(contestId.value)
}

const loadScopeOptions = async (): Promise<void> => {
  try {
    const [organizationPage, schoolPage] = await Promise.all([
      listOrganizations({ page: 1, size: 100 }),
      listSchools({ page: 1, size: 100 }),
    ])
    organizations.value = organizationPage.items
    schools.value = schoolPage.items
  } catch (error) {
    formError.value = getContestErrorMessage(error, '组织与学校选项加载失败')
  }
}

const loadEditions = async (): Promise<void> => {
  if (!contestId.value) return
  loading.value = true
  try {
    const data = await listContestEditions(contestId.value, {
      page: page.value,
      size: size.value,
      sort: '-created_at',
      year: yearFilter.value,
      status: statusFilter.value || undefined,
    })
    editions.value = data.items
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const loadAll = async (): Promise<void> => {
  try {
    await Promise.all([loadContest(), loadEditions(), loadScopeOptions()])
  } catch (error) {
    ElMessage.error(getContestErrorMessage(error, '竞赛届次加载失败'))
  }
}

const openCreate = (): void => {
  editorMode.value = 'create'
  selectedEdition.value = null
  schoolQuotas.value = []
  milestones.value = []
  Object.assign(form, emptyEditionForm())
  formError.value = ''
  idempotencyKey.value = createContestIdempotencyKey()
  editorVisible.value = true
}

const openEdit = async (row: EditionItem): Promise<void> => {
  editorMode.value = 'edit'
  formError.value = ''
  idempotencyKey.value = createContestIdempotencyKey()
  try {
    const detail = await getContestEdition(row.id)
    selectedEdition.value = detail
    schoolQuotas.value = (detail.school_quotas ?? []).map((quota) => ({
      school_id: quota.school_id,
      max_teams: quota.max_teams,
      used_teams: quota.used_teams,
    }))
    Object.assign(form, editionToForm(detail))
    editorVisible.value = true
    await loadMilestones()
  } catch (error) {
    ElMessage.error(getContestErrorMessage(error, '届次详情加载失败'))
  }
}

const normalizeTeamSize = (): void => {
  if (form.team_size_mode === 'FIXED') {
    form.min_team_size = form.fixed_team_size
    form.max_team_size = form.fixed_team_size
  }
}

const handleLevelChange = (): void => {
  form.school_id = ''
  form.province_code = ''
  form.owner_org_id = ''
  if (form.level === 'SCHOOL') {
    form.visibility = form.visibility === 'PUBLIC' ? 'DRAFT' : form.visibility
  }
}

const handleSchoolChange = (): void => {
  const school = schools.value.find((item) => item.id === form.school_id)
  form.owner_org_id = form.school_id
  form.province_code = school?.province_code ?? ''
}

const handleProvinceChange = (): void => {
  const owner = availableOwners.value.find(
    (item) => item.province_code === form.province_code,
  )
  form.owner_org_id = owner?.id ?? ''
}

const validateEditionForm = (): string => {
  if (!form.edition_name.trim()) return '届次名称不能为空'
  if (form.year < 2000 || form.year > 2100) return '举办年份必须在 2000 到 2100 之间'
  normalizeTeamSize()
  if (form.team_size_mode === 'FIXED') {
    if (form.fixed_team_size < 1 || form.fixed_team_size > 5) {
      return '固定队伍人数必须在 1 到 5 之间'
    }
  } else if (
    form.min_team_size < 1
    || form.max_team_size > 5
    || form.min_team_size > form.max_team_size
  ) {
    return '范围人数需满足 1 ≤ 最少人数 ≤ 最多人数 ≤ 5'
  }
  if (form.max_teams_per_teacher < 1 || form.max_teams_per_teacher > 10) {
    return '每位教师指导队伍上限必须在 1 到 10 之间'
  }
  if (!form.owner_org_id) return '请选择主办组织'
  if (form.level === 'SCHOOL' && !form.school_id) return '校级比赛必须选择所属学校'
  if (form.level === 'PROVINCE' && !form.province_code) return '省级比赛必须选择省份'
  if (
    form.topic_allocation_mode === 'FIRST_COME'
    && form.topic_mode !== 'PRESET_LIMITED'
  ) {
    return '先到先得只能用于“统一赛题（限额）”模式'
  }
  return ''
}

const saveEdition = async (): Promise<void> => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  const validationError = validateEditionForm()
  if (validationError) {
    formError.value = validationError
    return
  }
  if (editorMode.value === 'edit' && !schoolQuotaEditorRef.value?.validate()) return
  if (editorMode.value === 'edit' && form.version === null) {
    formError.value = '未取得届次版本号，请刷新后重试'
    return
  }

  saving.value = true
  formError.value = ''
  try {
    const payload = {
      edition_name: form.edition_name.trim(),
      owner_org_id: form.owner_org_id,
      level: form.level,
      school_id: form.level === 'SCHOOL' ? form.school_id : null,
      province_code: form.level === 'PROVINCE' ? form.province_code : null,
      visibility: form.visibility,
      entry_mode: form.entry_mode,
      cross_school_team_allowed: form.cross_school_team_allowed,
      register_deadline: toApiDateTime(form.register_deadline),
      work_deadline: toApiDateTime(form.work_deadline),
      review_start: toApiDateTime(form.review_start),
      publish_start: toApiDateTime(form.publish_start),
      publish_end: toApiDateTime(form.publish_end),
      min_team_size: form.min_team_size,
      max_team_size: form.max_team_size,
      team_size_mode: form.team_size_mode,
      max_teams_per_teacher: form.max_teams_per_teacher,
      topic_mode: form.topic_mode || null,
      topic_review_required: form.topic_review_required,
      topic_allocation_mode: form.topic_allocation_mode,
      topic_change_limit: form.topic_change_limit,
    }
    if (editorMode.value === 'create') {
      const result = await createContestEdition(
        contestId.value,
        { ...payload, year: form.year },
        idempotencyKey.value,
      )
      ElMessage.success('届次草稿已创建')
      editorVisible.value = false
      await loadEditions()
      const created = editions.value.find((item) => item.id === result.id)
      if (created) await openEdit(created)
    } else {
      if (!selectedEdition.value || form.version === null) return
      await updateContestEdition(
        selectedEdition.value.id,
        {
          ...payload,
          version: form.version,
          school_quotas: schoolQuotas.value.map((quota) => ({
            school_id: quota.school_id.trim(),
            max_teams: quota.max_teams,
          })),
        },
        idempotencyKey.value,
      )
      ElMessage.success('届次配置已保存')
      const id = selectedEdition.value.id
      await loadEditions()
      const row = editions.value.find((item) => item.id === id)
      if (row) await openEdit(row)
    }
  } catch (error) {
    formError.value = getContestErrorMessage(error, '保存届次失败')
  } finally {
    saving.value = false
  }
}

const publish = async (row?: EditionItem): Promise<void> => {
  const edition = row ? await getContestEdition(row.id) : selectedEdition.value
  if (!edition) return
  try {
    await ElMessageBox.confirm(
      '发布前将校验完整时间、人数、教师指导上限和赛题规则。发布后进入报名中，是否继续？',
      '发布届次',
      { type: 'warning', confirmButtonText: '校验并发布', cancelButtonText: '取消' },
    )
    await publishContestEdition(
      edition.id,
      edition.version,
      createContestIdempotencyKey(),
    )
    ElMessage.success('届次已发布为报名中')
    await loadEditions()
    if (selectedEdition.value?.id === edition.id) {
      const refreshed = await getContestEdition(edition.id)
      selectedEdition.value = refreshed
      schoolQuotas.value = refreshed.school_quotas.map((quota) => ({
        school_id: quota.school_id,
        max_teams: quota.max_teams,
        used_teams: quota.used_teams,
      }))
      Object.assign(form, editionToForm(refreshed))
    }
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(getContestErrorMessage(error, '届次发布失败'))
  }
}

const loadMilestones = async (): Promise<void> => {
  if (!selectedEdition.value) return
  milestoneLoading.value = true
  try {
    milestones.value = await listEditionMilestones(selectedEdition.value.id)
  } catch (error) {
    ElMessage.error(getContestErrorMessage(error, '赛程节点加载失败'))
  } finally {
    milestoneLoading.value = false
  }
}

const openMilestoneCreate = (): void => {
  milestoneMode.value = 'create'
  milestoneId.value = ''
  Object.assign(milestoneForm, emptyMilestoneForm())
  milestoneError.value = ''
  milestoneKey.value = createContestIdempotencyKey()
  milestoneVisible.value = true
}

const openMilestoneEdit = (row: Milestone): void => {
  milestoneMode.value = 'edit'
  milestoneId.value = row.id
  milestoneForm.node_type = row.node_type
  milestoneForm.node_name = row.node_name
  milestoneForm.start_time = toDateTimeInput(row.start_time)
  milestoneForm.end_time = toDateTimeInput(row.end_time)
  milestoneForm.enabled = row.enabled
  milestoneForm.version = row.version
  milestoneError.value = ''
  milestoneKey.value = createContestIdempotencyKey()
  milestoneVisible.value = true
}

const saveMilestone = async (): Promise<void> => {
  if (!milestoneFormRef.value || !selectedEdition.value) return
  const valid = await milestoneFormRef.value.validate().catch(() => false)
  if (!valid) return
  if (!milestoneForm.start_time || !milestoneForm.end_time) return
  if (milestoneForm.start_time >= milestoneForm.end_time) {
    milestoneError.value = '节点开始时间必须早于结束时间'
    return
  }
  if (milestoneMode.value === 'edit' && milestoneForm.version === null) {
    milestoneError.value = '未取得节点版本号，请刷新后重试'
    return
  }

  milestoneSaving.value = true
  milestoneError.value = ''
  try {
    const payload = {
      node_name: milestoneForm.node_name.trim(),
      start_time: toApiDateTime(milestoneForm.start_time) ?? '',
      end_time: toApiDateTime(milestoneForm.end_time) ?? '',
      enabled: milestoneForm.enabled,
    }
    if (milestoneMode.value === 'create') {
      await createEditionMilestone(
        selectedEdition.value.id,
        { ...payload, node_type: milestoneForm.node_type },
        milestoneKey.value,
      )
      ElMessage.success('赛程节点已新增')
    } else {
      if (milestoneForm.version === null) return
      await updateEditionMilestone(
        milestoneId.value,
        { ...payload, version: milestoneForm.version },
        milestoneKey.value,
      )
      ElMessage.success('赛程节点已更新')
    }
    milestoneVisible.value = false
    await loadMilestones()
  } catch (error) {
    milestoneError.value = getContestErrorMessage(error, '保存赛程节点失败')
  } finally {
    milestoneSaving.value = false
  }
}

const statusTagType = (status: EditionStatus): 'info' | 'success' | 'warning' | 'danger' => {
  if (status === 'DRAFT') return 'info'
  if (status === 'REGISTERING' || status === 'SUBMITTING') return 'success'
  if (status === 'REVIEWING' || status === 'PUBLISHING') return 'warning'
  return 'danger'
}

const windowText = (open: boolean): string => (open ? '进行中' : '未开放或已结束')

onMounted(() => {
  void loadAll()
  void ensureCurrentMenus().catch(() => undefined)
})

function emptyEditionForm(): EditionForm {
  const now = new Date()
  return {
    edition_name: '',
    year: now.getFullYear(),
    owner_org_id: '',
    level: 'SCHOOL',
    school_id: '',
    province_code: '',
    visibility: 'DRAFT',
    entry_mode: 'OPEN',
    cross_school_team_allowed: false,
    register_deadline: toDateTimeInput(
      new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000).toISOString(),
    ),
    work_deadline: toDateTimeInput(
      new Date(now.getTime() + 21 * 24 * 60 * 60 * 1000).toISOString(),
    ),
    review_start: toDateTimeInput(
      new Date(now.getTime() + 28 * 24 * 60 * 60 * 1000).toISOString(),
    ),
    publish_start: toDateTimeInput(
      new Date(now.getTime() + 35 * 24 * 60 * 60 * 1000).toISOString(),
    ),
    publish_end: toDateTimeInput(
      new Date(now.getTime() + 42 * 24 * 60 * 60 * 1000).toISOString(),
    ),
    team_size_mode: 'RANGE',
    fixed_team_size: 3,
    min_team_size: 2,
    max_team_size: 5,
    max_teams_per_teacher: 3,
    topic_mode: 'PRESET_UNLIMITED',
    topic_review_required: true,
    topic_allocation_mode: 'REVIEW',
    topic_change_limit: 1,
    version: null,
  }
}

function emptyMilestoneForm(): MilestoneForm {
  const now = new Date()
  return {
    node_type: 'WORK_END',
    node_name: '',
    start_time: toDateTimeInput(
      new Date(now.getTime() + 2 * 24 * 60 * 60 * 1000).toISOString(),
    ),
    end_time: toDateTimeInput(
      new Date(now.getTime() + 2 * 24 * 60 * 60 * 1000 + 60 * 60 * 1000).toISOString(),
    ),
    enabled: true,
    version: null,
  }
}

function editionToForm(detail: EditionDetail): EditionForm {
  return {
    edition_name: detail.edition_name,
    year: detail.year,
    owner_org_id: detail.owner_org_id,
    level: detail.level,
    school_id: detail.school_id ?? '',
    province_code: detail.province_code ?? '',
    visibility: detail.visibility,
    entry_mode: detail.entry_mode,
    cross_school_team_allowed: detail.cross_school_team_allowed,
    register_deadline: toDateTimeInput(detail.register_deadline),
    work_deadline: toDateTimeInput(detail.work_deadline),
    review_start: toDateTimeInput(detail.review_start),
    publish_start: toDateTimeInput(detail.publish_start),
    publish_end: toDateTimeInput(detail.publish_end),
    team_size_mode: detail.team_size_mode,
    fixed_team_size: detail.min_team_size,
    min_team_size: detail.min_team_size,
    max_team_size: detail.max_team_size,
    max_teams_per_teacher: detail.max_teams_per_teacher,
    topic_mode: detail.topic_mode ?? '',
    topic_review_required: detail.topic_review_required,
    topic_allocation_mode: detail.topic_allocation_mode,
    topic_change_limit: detail.topic_change_limit,
    version: detail.version,
  }
}
</script>

<template>
  <div class="edition-admin-page">
    <header class="page-heading">
      <div>
        <p class="eyebrow">Edition configuration</p>
        <h1>{{ contest?.contest_name || '竞赛届次' }}</h1>
        <p>每届独立配置时间、人数、教师指导上限和赛题模式；草稿只有管理员可见。</p>
      </div>
      <div class="page-actions">
        <el-button class="page-nav-button" plain @click="router.back()">返回竞赛列表</el-button>
        <el-button v-if="canCreate" type="primary" @click="openCreate">
          创建草稿届次
        </el-button>
      </div>
    </header>

    <el-card shadow="never">
      <div class="filter-row">
        <el-select v-model="statusFilter" clearable placeholder="全部状态" class="status-select">
          <el-option
            v-for="(label, value) in EDITION_STATUS_LABELS"
            :key="value"
            :label="label"
            :value="value"
          />
        </el-select>
        <el-input-number
          v-model="yearFilter"
          :min="2000"
          :max="2100"
          controls-position="right"
          placeholder="举办年份"
        />
        <el-button type="primary" @click="loadEditions">查询</el-button>
        <el-button @click="yearFilter = undefined; statusFilter = ''; loadEditions()">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="editions" border row-key="id">
        <el-table-column prop="edition_name" label="届次名称" min-width="220" />
        <el-table-column prop="year" label="年份" width="100" />
        <el-table-column label="级别/范围" min-width="180">
          <template #default="{ row }">
            {{ CONTEST_LEVEL_OPTIONS.find((item) => item.value === row.level)?.label }}
            <span class="muted"> · {{ EDITION_VISIBILITY_OPTIONS.find((item) => item.value === row.visibility)?.label }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="owner_org_name" label="主办组织" min-width="160" />
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">
              {{ EDITION_STATUS_LABELS[row.status as EditionStatus] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="470" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canUpdate" link type="primary" @click="openEdit(row)">配置</el-button>
            <el-button
              v-if="canPublish && row.status === 'DRAFT'"
              link
              type="success"
              @click="publish(row)"
            >
              发布
            </el-button>
            <el-button
              v-if="row.status !== 'DRAFT'"
              link
              type="primary"
              @click="router.push(`/contests/${row.id}`)"
            >
              学生端预览
            </el-button>
            <el-button
              v-if="canReviewTeams"
              link
              type="warning"
              @click="router.push({ path: '/admin/team-approvals', query: { edition_id: row.id } })"
            >
              报名终审
            </el-button>
            <el-button
              v-if="canManageEligibility"
              link
              type="success"
              @click="router.push(`/admin/editions/${row.id}/entry-grants`)"
            >
              资格名单
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="loadEditions"
          @size-change="loadEditions"
        />
      </div>
    </el-card>

    <el-drawer
      v-model="editorVisible"
      :title="editorMode === 'create' ? '创建草稿届次' : '届次配置'"
      size="760px"
      destroy-on-close
    >
      <div v-if="selectedEdition" class="edition-state-strip">
        <el-tag :type="statusTagType(selectedEdition.status)">
          {{ EDITION_STATUS_LABELS[selectedEdition.status] }}
        </el-tag>
        <span>版本 {{ selectedEdition.version }}</span>
        <span>报名窗口：{{ windowText(selectedEdition.registration_open) }}</span>
        <span>作品窗口：{{ windowText(selectedEdition.submission_open) }}</span>
        <el-tag v-if="selectedEdition.read_only" type="danger">只读</el-tag>
      </div>

      <el-alert
        v-if="formError"
        :title="formError"
        type="error"
        show-icon
        :closable="false"
        class="form-alert"
      />

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        :disabled="!editable"
        label-position="top"
      >
        <section class="form-section">
          <h2>基础信息</h2>
          <div class="two-column">
            <el-form-item label="届次名称" prop="edition_name">
              <el-input v-model="form.edition_name" maxlength="128" show-word-limit />
            </el-form-item>
            <el-form-item label="举办年份">
              <el-input-number
                v-model="form.year"
                :min="2000"
                :max="2100"
                controls-position="right"
                class="full-width"
                :disabled="editorMode === 'edit'"
              />
            </el-form-item>
          </div>
        </section>

        <section class="form-section">
          <h2>比赛归属与范围</h2>
          <p class="section-hint">
            校级比赛绑定学校，省级比赛绑定省份，国家级比赛不绑定学校或省份。草稿仅管理范围内管理员可见。
          </p>
          <div class="two-column">
            <el-form-item label="比赛级别">
              <el-select v-model="form.level" class="full-width" @change="handleLevelChange">
                <el-option
                  v-for="option in CONTEST_LEVEL_OPTIONS"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="主办组织">
              <el-select
                v-model="form.owner_org_id"
                class="full-width"
                filterable
                placeholder="请选择授权范围内的主办组织"
              >
                <el-option
                  v-for="item in availableOwners"
                  :key="item.id"
                  :label="`${item.org_name}（${item.org_code}）`"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item v-if="form.level === 'SCHOOL'" label="所属学校">
              <el-select
                v-model="form.school_id"
                class="full-width"
                filterable
                @change="handleSchoolChange"
              >
                <el-option
                  v-for="school in schools"
                  :key="school.id"
                  :label="`${school.school_name}（${school.province_name}）`"
                  :value="school.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item v-if="form.level === 'PROVINCE'" label="所属省份">
              <el-select
                v-model="form.province_code"
                class="full-width"
                filterable
                @change="handleProvinceChange"
              >
                <el-option
                  v-for="province in provinceOptions"
                  :key="province.value"
                  :label="province.label"
                  :value="province.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="可见范围">
              <el-select v-model="form.visibility" class="full-width">
                <el-option
                  v-for="option in EDITION_VISIBILITY_OPTIONS"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="参赛资格模式">
              <el-select v-model="form.entry_mode" class="full-width">
                <el-option
                  v-for="option in ENTRY_MODE_OPTIONS"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>
          </div>
          <el-form-item label="允许跨校组队">
            <el-switch v-model="form.cross_school_team_allowed" />
          </el-form-item>
        </section>

        <section class="form-section">
          <h2>核心时间</h2>
          <p class="section-hint">
            报名资格只读取报名截止；作品资格只读取作品截止。赛程节点不会参与这里的时间判断。
          </p>
          <div class="two-column">
            <el-form-item label="报名截止">
              <el-date-picker
                v-model="form.register_deadline"
                type="datetime"
                value-format="YYYY-MM-DDTHH:mm"
                class="full-width"
              />
            </el-form-item>
            <el-form-item label="作品截止">
              <el-date-picker
                v-model="form.work_deadline"
                type="datetime"
                value-format="YYYY-MM-DDTHH:mm"
                class="full-width"
              />
            </el-form-item>
            <el-form-item label="评审开始">
              <el-date-picker
                v-model="form.review_start"
                type="datetime"
                value-format="YYYY-MM-DDTHH:mm"
                class="full-width"
              />
            </el-form-item>
            <el-form-item label="公示开始">
              <el-date-picker
                v-model="form.publish_start"
                type="datetime"
                value-format="YYYY-MM-DDTHH:mm"
                class="full-width"
              />
            </el-form-item>
            <el-form-item label="公示结束">
              <el-date-picker
                v-model="form.publish_end"
                type="datetime"
                value-format="YYYY-MM-DDTHH:mm"
                class="full-width"
              />
            </el-form-item>
          </div>
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="发布时必须满足：报名截止 < 作品截止 < 评审开始 < 公示开始 < 公示结束。"
          />
        </section>

        <section class="form-section">
          <h2>队伍与指导</h2>
          <div class="two-column">
            <el-form-item label="人数模式">
              <el-radio-group v-model="form.team_size_mode" @change="normalizeTeamSize">
                <el-radio-button
                  v-for="option in TEAM_SIZE_OPTIONS"
                  :key="option.value"
                  :value="option.value"
                >
                  {{ option.label }}
                </el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="每位教师最多确认指导队伍数">
              <el-input-number
                v-model="form.max_teams_per_teacher"
                :min="1"
                :max="10"
                controls-position="right"
              />
            </el-form-item>
          </div>
          <el-form-item v-if="form.team_size_mode === 'FIXED'" label="固定队伍人数">
            <el-input-number
              v-model="form.fixed_team_size"
              :min="1"
              :max="5"
              controls-position="right"
              @change="normalizeTeamSize"
            />
          </el-form-item>
          <div v-else class="two-column">
            <el-form-item label="最少人数">
              <el-input-number
                v-model="form.min_team_size"
                :min="1"
                :max="5"
                controls-position="right"
              />
            </el-form-item>
            <el-form-item label="最多人数">
              <el-input-number
                v-model="form.max_team_size"
                :min="1"
                :max="5"
                controls-position="right"
              />
            </el-form-item>
          </div>
        </section>

        <section v-if="selectedEdition" class="form-section quota-section">
          <h2>学校报名名额</h2>
          <p class="section-hint">
            未配置学校或不限量时不限制报名队伍数；有限名额不能低于当前已占用数。
          </p>
          <SchoolQuotaEditor
            ref="schoolQuotaEditorRef"
            v-model="schoolQuotas"
            :schools="schools"
            :read-only="!editable"
          />
        </section>

        <section class="form-section">
          <h2>赛题与审核</h2>
          <el-form-item label="赛题模式">
            <el-select v-model="form.topic_mode" clearable class="full-width">
              <el-option
                v-for="option in TOPIC_MODE_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              >
                <div class="option-copy">
                  <strong>{{ option.label }}</strong>
                  <small>{{ option.description }}</small>
                </div>
              </el-option>
            </el-select>
          </el-form-item>
          <div class="two-column">
            <el-form-item label="限额赛题分配方式">
              <el-select v-model="form.topic_allocation_mode" class="full-width">
                <el-option
                  v-for="option in TOPIC_ALLOCATION_OPTIONS"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="报名截止前允许更换次数">
              <el-input-number
                v-model="form.topic_change_limit"
                :min="0"
                :max="3"
                controls-position="right"
              />
            </el-form-item>
          </div>
          <el-form-item label="选题或自拟项目需要审核">
            <el-switch v-model="form.topic_review_required" />
          </el-form-item>
          <el-alert
            v-if="form.topic_mode === 'NO_TOPIC'"
            type="success"
            :closable="false"
            show-icon
            title="无赛题环节不要求选题或项目申报。"
          />
        </section>
      </el-form>

      <section v-if="selectedEdition" class="milestone-section">
        <div class="section-heading-row">
          <div>
            <h2>补充赛程节点</h2>
            <p class="section-hint">仅用于展示，不作为报名或作品资格判断依据。</p>
          </div>
          <el-button
            v-if="canCreateMilestone && editable"
            type="primary"
            plain
            @click="openMilestoneCreate"
          >
            新增节点
          </el-button>
        </div>
        <el-table v-loading="milestoneLoading" :data="milestones" border size="small">
          <el-table-column label="类型" width="100">
            <template #default="{ row }">
              {{ MILESTONE_NODE_OPTIONS.find((item) => item.value === row.node_type)?.label }}
            </template>
          </el-table-column>
          <el-table-column prop="node_name" label="名称" min-width="140" />
          <el-table-column label="时间" min-width="230">
            <template #default="{ row }">
              {{ formatContestDateTime(row.start_time) }} 至 {{ formatContestDateTime(row.end_time) }}
            </template>
          </el-table-column>
          <el-table-column label="启用" width="80">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'">
                {{ row.enabled ? '是' : '否' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="版本" prop="version" width="70" />
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button
                v-if="canUpdateMilestone && editable"
                link
                type="primary"
                @click="openMilestoneEdit(row)"
              >
                编辑
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <template #footer>
        <div class="drawer-footer">
          <el-button @click="editorVisible = false">关闭</el-button>
          <el-button
            v-if="selectedEdition?.status === 'DRAFT' && canPublish"
            type="success"
            plain
            @click="publish()"
          >
            校验并发布
          </el-button>
          <el-button v-if="editable" type="primary" :loading="saving" @click="saveEdition">
            保存配置
          </el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog
      v-model="milestoneVisible"
      :title="milestoneMode === 'create' ? '新增赛程节点' : '编辑赛程节点'"
      width="500px"
      destroy-on-close
    >
      <el-alert
        v-if="milestoneError"
        :title="milestoneError"
        type="error"
        show-icon
        :closable="false"
        class="form-alert"
      />
      <el-form
        ref="milestoneFormRef"
        :model="milestoneForm"
        :rules="milestoneRules"
        label-position="top"
      >
        <el-form-item label="节点类型" prop="node_type">
          <el-select
            v-model="milestoneForm.node_type"
            class="full-width"
            :disabled="milestoneMode === 'edit'"
          >
            <el-option
              v-for="option in MILESTONE_NODE_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="节点名称" prop="node_name">
          <el-input v-model="milestoneForm.node_name" maxlength="64" show-word-limit />
        </el-form-item>
        <div class="two-column">
          <el-form-item label="开始时间" prop="start_time">
            <el-date-picker
              v-model="milestoneForm.start_time"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm"
              class="full-width"
            />
          </el-form-item>
          <el-form-item label="结束时间" prop="end_time">
            <el-date-picker
              v-model="milestoneForm.end_time"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm"
              class="full-width"
            />
          </el-form-item>
        </div>
        <el-form-item label="启用">
          <el-switch v-model="milestoneForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="milestoneVisible = false">取消</el-button>
        <el-button type="primary" :loading="milestoneSaving" @click="saveMilestone">
          保存节点
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.edition-admin-page {
  padding: 18px;
}

.page-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 16px;
  padding: 24px 26px;
  border-radius: 18px 18px 18px 5px;
  color: #eff5f1;
  background:
    radial-gradient(circle at 92% 15%, rgba(216, 186, 112, 0.3), transparent 15rem),
    linear-gradient(135deg, #173f35, #2e6352);
}

.page-heading :deep(.el-button.is-link) {
  color: #d8ba70;
}

.page-heading h1 {
  margin: 7px 0;
  font-family: inherit;
  font-variant-numeric: tabular-nums;
  font-size: 31px;
  font-weight: 600;
}

.page-heading p {
  margin: 0;
  color: rgba(239, 245, 241, 0.72);
}

.eyebrow {
  margin-top: 14px !important;
  color: #d8ba70 !important;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.filter-row {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}

.status-select {
  width: 150px;
}

.pagination-row,
.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}

.edition-state-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  padding: 12px 14px;
  border: 1px solid #dfe8e3;
  border-radius: 10px;
  color: #53645e;
  background: #f7faf8;
  font-size: 12px;
}

.form-section,
.milestone-section {
  margin-bottom: 18px;
  padding: 18px;
  border: 1px solid #e5ebe7;
  border-radius: 12px;
}

.form-section h2,
.milestone-section h2 {
  margin: 0 0 12px;
  color: #183f35;
  font-family: inherit;
  font-variant-numeric: tabular-nums;
  font-size: 19px;
  font-weight: 600;
}

.section-hint {
  margin: -5px 0 14px;
  color: #8a9792;
  font-size: 12px;
  line-height: 1.6;
}

.two-column {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.full-width {
  width: 100%;
}

.form-alert {
  margin-bottom: 16px;
}

.option-copy {
  display: flex;
  flex-direction: column;
  padding: 4px 0;
}

.option-copy small {
  color: #8d9995;
}

.muted {
  color: #8d9995;
}

.section-heading-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

@media (max-width: 760px) {
  .page-heading,
  .filter-row {
    align-items: stretch;
    flex-direction: column;
  }

  .two-column {
    grid-template-columns: 1fr;
  }
}
</style>
