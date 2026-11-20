<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  createContest,
  createContestIdempotencyKey,
  getContest,
  getContestErrorMessage,
  listContests,
  updateContest,
} from '../../api/contest'
import {
  CONTEST_LEVEL_LABELS,
  CONTEST_LEVEL_OPTIONS,
} from '../../types/contest'
import type {
  ContestCreateRequest,
  ContestItem,
  ContestLevel,
  ContestListQuery,
} from '../../types/contest'
import { useCurrentMenus } from '../../layouts/useCurrentMenus'

interface ContestForm {
  contest_name: string
  series_code: string
  level: ContestLevel
  organizer: string
  intro: string
  version: number
}

const router = useRouter()
const { ensureCurrentMenus, hasPermission } = useCurrentMenus()
const canCreate = computed(() => hasPermission('contest:create'))
const canUpdate = computed(() => hasPermission('contest:update'))

const loading = ref(false)
const contests = ref<ContestItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const filters = reactive<{ level: ContestLevel | ''; keyword: string }>({
  level: '',
  keyword: '',
})

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const saving = ref(false)
const formError = ref('')
const formRef = ref<FormInstance>()
const editingId = ref('')
const idempotencyKey = ref('')
const form = reactive<ContestForm>({
  contest_name: '',
  series_code: '',
  level: 'SCHOOL',
  organizer: '',
  intro: '',
  version: 0,
})
const rules: FormRules = {
  contest_name: [
    { required: true, message: '请输入竞赛名称', trigger: 'blur' },
    { max: 128, message: '竞赛名称不能超过 128 个字符', trigger: 'blur' },
  ],
  series_code: [
    { required: true, message: '请输入竞赛系列编码', trigger: 'blur' },
    {
      pattern: /^[A-Z0-9][A-Z0-9_-]{1,31}$/,
      message: '系列编码需为 2-32 位大写字母、数字、下划线或短横线',
      trigger: 'blur',
    },
  ],
  level: [{ required: true, message: '请选择竞赛级别', trigger: 'change' }],
  organizer: [{ max: 128, message: '主办方不能超过 128 个字符', trigger: 'blur' }],
  intro: [{ max: 1000, message: '简介不能超过 1000 个字符', trigger: 'blur' }],
}

const loadContests = async (): Promise<void> => {
  loading.value = true
  try {
    const query: ContestListQuery = {
      page: page.value,
      size: size.value,
      sort: '-created_at',
    }
    if (filters.level) query.level = filters.level
    if (filters.keyword.trim()) query.keyword = filters.keyword.trim()
    const data = await listContests(query)
    contests.value = data.items
    total.value = data.total
  } catch (error) {
    ElMessage.error(getContestErrorMessage(error, '竞赛列表加载失败'))
  } finally {
    loading.value = false
  }
}

const resetForm = (): void => {
  form.contest_name = ''
  form.series_code = ''
  form.level = 'SCHOOL'
  form.organizer = ''
  form.intro = ''
  form.version = 0
  formError.value = ''
  idempotencyKey.value = createContestIdempotencyKey()
}

const openCreate = (): void => {
  dialogMode.value = 'create'
  editingId.value = ''
  resetForm()
  dialogVisible.value = true
}

const openEdit = async (row: ContestItem): Promise<void> => {
  dialogMode.value = 'edit'
  editingId.value = row.id
  resetForm()
  try {
    const detail = await getContest(row.id)
    form.contest_name = detail.contest_name
    form.series_code = detail.series_code
    form.level = detail.level
    form.organizer = detail.organizer ?? ''
    form.intro = detail.intro ?? ''
    form.version = detail.version
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error(getContestErrorMessage(error, '竞赛详情加载失败'))
  }
}

const submit = async (): Promise<void> => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  formError.value = ''
  try {
    if (dialogMode.value === 'create') {
      const payload: ContestCreateRequest = {
        contest_name: form.contest_name,
        level: form.level,
        series_code: form.series_code,
        organizer: form.organizer || undefined,
        intro: form.intro || undefined,
      }
      await createContest(payload, idempotencyKey.value)
      ElMessage.success('竞赛已创建')
    } else {
      await updateContest(
        editingId.value,
        {
          contest_name: form.contest_name,
          level: form.level,
          series_code: form.series_code,
          organizer: form.organizer,
          intro: form.intro,
          version: form.version,
        },
        idempotencyKey.value,
      )
      ElMessage.success('竞赛主档已更新')
    }
    dialogVisible.value = false
    await loadContests()
  } catch (error) {
    formError.value = getContestErrorMessage(error, '保存竞赛失败')
  } finally {
    saving.value = false
  }
}

const openEditions = (row: ContestItem): void => {
  void router.push(`/admin/contests/${row.id}/editions`)
}

const handleSearch = (): void => {
  page.value = 1
  void loadContests()
}

const handleReset = (): void => {
  filters.level = ''
  filters.keyword = ''
  page.value = 1
  void loadContests()
}

onMounted(() => {
  void loadContests()
  void ensureCurrentMenus().catch(() => undefined)
})
</script>

<template>
  <div class="contest-admin-page">
    <section class="hero-card">
      <div>
        <p class="eyebrow">Contest master data</p>
        <h1>竞赛与届次</h1>
        <p>竞赛主档记录赛事的稳定信息；每一届的时间、人数与赛题规则在届次下独立配置。</p>
      </div>
      <el-button v-if="canCreate" type="primary" size="large" @click="openCreate">
        创建竞赛
      </el-button>
    </section>

    <el-card shadow="never">
      <el-form :inline="true" class="filter-row" @submit.prevent>
        <el-form-item label="竞赛名称/主办方">
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="输入关键字"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="filters.level" clearable placeholder="全部" class="level-select">
            <el-option
              v-for="option in CONTEST_LEVEL_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="contests" border row-key="id">
        <el-table-column prop="contest_name" label="竞赛名称" min-width="220" />
        <el-table-column prop="series_code" label="系列编码" min-width="150" />
        <el-table-column label="级别" width="110">
          <template #default="{ row }">
            <el-tag effect="plain">{{ CONTEST_LEVEL_LABELS[row.level as ContestLevel] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="organizer" label="主办方" min-width="180">
          <template #default="{ row }">{{ row.organizer || '未填写' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canUpdate" link type="primary" @click="openEdit(row)">编辑主档</el-button>
            <el-button link type="primary" @click="openEditions(row)">管理届次</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="loadContests"
          @size-change="handleSearch"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '创建竞赛' : '编辑竞赛主档'"
      width="560px"
      destroy-on-close
    >
      <el-alert
        v-if="formError"
        :title="formError"
        type="error"
        show-icon
        :closable="false"
        class="form-alert"
      />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="竞赛名称" prop="contest_name">
          <el-input v-model="form.contest_name" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="系列编码" prop="series_code">
          <el-input
            v-model="form.series_code"
            maxlength="32"
            show-word-limit
            placeholder="例如 SOFTWARE_ENGINEERING"
          />
        </el-form-item>
        <el-form-item label="竞赛级别" prop="level">
          <el-select v-model="form.level" class="full-width">
            <el-option
              v-for="option in CONTEST_LEVEL_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="主办方" prop="organizer">
          <el-input v-model="form.organizer" maxlength="128" />
        </el-form-item>
        <el-form-item label="竞赛简介" prop="intro">
          <el-input
            v-model="form.intro"
            type="textarea"
            :rows="5"
            maxlength="1000"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.contest-admin-page {
  padding: 18px;
}

.hero-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 16px;
  padding: 28px 30px;
  border-radius: 18px 18px 18px 5px;
  color: #f0f5f1;
  background:
    radial-gradient(circle at 90% 10%, rgba(216, 186, 112, 0.34), transparent 16rem),
    linear-gradient(135deg, #143b31, #275b4b);
}

.hero-card h1 {
  margin: 7px 0 8px;
  font-family: Georgia, "Songti SC", serif;
  font-size: 32px;
  font-weight: 500;
}

.hero-card p {
  max-width: 720px;
  margin: 0;
  color: rgba(240, 245, 241, 0.72);
  line-height: 1.7;
}

.eyebrow {
  color: #d8ba70 !important;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.filter-row {
  margin-bottom: 4px;
}

.level-select {
  width: 140px;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

.full-width {
  width: 100%;
}

.form-alert {
  margin-bottom: 16px;
}
</style>
