<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  listSchoolAffiliations,
  listSchools,
  reviewSchoolAffiliation,
} from '../../api/organization'
import { getErrorMessage } from '../../api/adminRbac'
import { useCurrentMenus } from '../../layouts/useCurrentMenus'
import { useAuthStore } from '../../stores/auth'
import {
  AFFILIATION_STATUS_LABELS,
} from '../../types/organization'
import type {
  SchoolAffiliationDecision,
  SchoolAffiliationItem,
  SchoolAffiliationListQuery,
  SchoolAffiliationStatus,
  SchoolItem,
} from '../../types/organization'

const auth = useAuthStore()
const { ensureCurrentMenus, hasPermission } = useCurrentMenus()
const canReview = computed(() => hasPermission('school:affiliation:review'))
const isPlatformAdmin = computed(
  () => auth.hasRole('ADMIN') && hasPermission('school:affiliation:list'),
)

const loading = ref(false)
const reviewLoading = ref(false)
const items = ref<SchoolAffiliationItem[]>([])
const schools = ref<SchoolItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const reviewVisible = ref(false)
const reviewTarget = ref<SchoolAffiliationItem | null>(null)
const reviewFormRef = ref<FormInstance>()

const filters = reactive<{
  status: SchoolAffiliationStatus
  school_id: string
  keyword: string
}>({
  status: 'PENDING',
  school_id: '',
  keyword: '',
})

const reviewForm = reactive<{
  decision: SchoolAffiliationDecision
  reason: string
  version: number
}>({
  decision: 'APPROVE',
  reason: '',
  version: 0,
})

const reviewRules: FormRules = {
  reason: [
    {
      validator: (_rule, value: string, callback) => {
        if (reviewForm.decision === 'REJECT' && !value.trim()) {
          callback(new Error('驳回时必须填写原因'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

const loadAffiliations = async (): Promise<void> => {
  loading.value = true
  try {
    const query: SchoolAffiliationListQuery = {
      status: filters.status,
      page: page.value,
      size: size.value,
    }
    if (filters.school_id) query.school_id = filters.school_id
    if (filters.keyword.trim()) query.keyword = filters.keyword.trim()
    const data = await listSchoolAffiliations(query)
    items.value = data.items
    total.value = data.total
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '学校归属申请加载失败'))
  } finally {
    loading.value = false
  }
}

const loadSchools = async (): Promise<void> => {
  if (!isPlatformAdmin.value) return
  try {
    const data = await listSchools({ page: 1, size: 100 })
    schools.value = data.items
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '学校列表加载失败'))
  }
}

const handleSearch = (): void => {
  page.value = 1
  void loadAffiliations()
}

const handleReset = (): void => {
  filters.status = 'PENDING'
  filters.school_id = ''
  filters.keyword = ''
  page.value = 1
  void loadAffiliations()
}

const openReview = (
  row: SchoolAffiliationItem,
  decision: SchoolAffiliationDecision,
): void => {
  reviewTarget.value = row
  reviewForm.decision = decision
  reviewForm.reason = ''
  reviewForm.version = row.version
  reviewVisible.value = true
}

const handleReview = async (): Promise<void> => {
  if (!reviewTarget.value) return
  const valid = await reviewFormRef.value?.validate().catch(() => false)
  if (!valid) return
  reviewLoading.value = true
  try {
    const result = await reviewSchoolAffiliation(reviewTarget.value.user_id, {
      decision: reviewForm.decision,
      reason: reviewForm.reason.trim() || null,
      version: reviewForm.version,
    })
    const row = items.value.find((item) => item.user_id === result.user_id)
    if (row) {
      row.status = result.status
      row.version = result.version
      row.review_reason = reviewForm.reason.trim() || null
    }
    reviewVisible.value = false
    ElMessage.success(reviewForm.decision === 'APPROVE' ? '学校归属已通过' : '学校归属已驳回')
    await loadAffiliations()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '学校归属审核失败'))
  } finally {
    reviewLoading.value = false
  }
}

const formatTime = (value: string): string =>
  value ? new Date(value).toLocaleString('zh-CN') : '-'

onMounted(() => {
  void loadAffiliations()
  void loadSchools()
  void ensureCurrentMenus().catch(() => undefined)
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <div class="card-title">学校归属审核</div>
            <div class="card-hint">
              学校管理员只能查看本校申请；平台管理员可按学校筛选
            </div>
          </div>
        </div>
      </template>

      <el-form :inline="true" class="filter-form" @submit.prevent>
        <el-form-item label="状态">
          <el-select v-model="filters.status" class="filter-select">
            <el-option
              v-for="(label, value) in AFFILIATION_STATUS_LABELS"
              :key="value"
              :label="label"
              :value="value"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="isPlatformAdmin" label="学校">
          <el-select v-model="filters.school_id" placeholder="全部学校" clearable class="school-select">
            <el-option
              v-for="school in schools"
              :key="school.id"
              :label="school.school_name"
              :value="school.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input
            v-model="filters.keyword"
            placeholder="账号、姓名或学号"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="items" border row-key="user_id">
        <el-table-column prop="username" label="账号" min-width="130" />
        <el-table-column prop="real_name" label="姓名" min-width="110" />
        <el-table-column prop="student_no" label="学号" min-width="130">
          <template #default="{ row }">{{ row.student_no || '-' }}</template>
        </el-table-column>
        <el-table-column prop="school_name" label="学校" min-width="180" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag
              :type="row.status === 'VERIFIED' ? 'success' : row.status === 'REJECTED' ? 'danger' : 'warning'"
            >
              {{ AFFILIATION_STATUS_LABELS[row.status as SchoolAffiliationStatus] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="review_reason" label="审核说明" min-width="180">
          <template #default="{ row }">{{ row.review_reason || '-' }}</template>
        </el-table-column>
        <el-table-column label="提交时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.submitted_at) }}</template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="80" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <template v-if="canReview && row.status === 'PENDING'">
              <el-button link type="success" @click="openReview(row, 'APPROVE')">
                通过
              </el-button>
              <el-button link type="danger" @click="openReview(row, 'REJECT')">
                驳回
              </el-button>
            </template>
            <span v-else class="readonly-text">无需处理</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="loadAffiliations"
          @size-change="handleSearch"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="reviewVisible"
      :title="reviewForm.decision === 'APPROVE' ? '通过学校归属' : '驳回学校归属'"
      width="520px"
      destroy-on-close
    >
      <el-alert
        v-if="reviewTarget"
        :type="reviewForm.decision === 'APPROVE' ? 'success' : 'warning'"
        :closable="false"
        :title="`${reviewTarget.real_name}（${reviewTarget.username}）→ ${reviewTarget.school_name}`"
      />
      <el-form
        ref="reviewFormRef"
        :model="reviewForm"
        :rules="reviewRules"
        label-width="90px"
        class="review-form"
      >
        <el-form-item v-if="reviewForm.decision === 'REJECT'" label="驳回原因" prop="reason">
          <el-input
            v-model="reviewForm.reason"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button
          :type="reviewForm.decision === 'APPROVE' ? 'success' : 'danger'"
          :loading="reviewLoading"
          @click="handleReview"
        >
          {{ reviewForm.decision === 'APPROVE' ? '确认通过' : '确认驳回' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-container {
  padding: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
}

.card-title {
  color: #303133;
  font-size: 16px;
  font-weight: 600;
}

.card-hint {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}

.filter-form {
  margin-bottom: 4px;
}

.filter-select {
  width: 130px;
}

.school-select {
  width: 220px;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

.review-form {
  margin-top: 18px;
}

.readonly-text {
  color: #909399;
  font-size: 12px;
}
</style>
