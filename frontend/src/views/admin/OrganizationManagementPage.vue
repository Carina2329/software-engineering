<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  createOrganization,
  listOrganizations,
  updateOrganization,
} from '../../api/organization'
import { getErrorMessage } from '../../api/adminRbac'
import { useCurrentMenus } from '../../layouts/useCurrentMenus'
import ScopeAssignmentDrawer from '../../components/admin/ScopeAssignmentDrawer.vue'
import {
  ORGANIZATION_STATUS_LABELS,
  ORGANIZATION_TYPE_OPTIONS,
} from '../../types/organization'
import type {
  OrganizationItem,
  OrganizationListQuery,
  OrganizationStatus,
  OrganizationType,
} from '../../types/organization'

const { ensureCurrentMenus, hasPermission } = useCurrentMenus()
const canCreate = computed(() => hasPermission('organization:create'))
const canUpdate = computed(() => hasPermission('organization:update'))
const canAssignRole = computed(() => hasPermission('organization:role:assign'))

const loading = ref(false)
const saving = ref(false)
const organizations = ref<OrganizationItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const createVisible = ref(false)
const editVisible = ref(false)
const scopeVisible = ref(false)
const editing = ref<OrganizationItem | null>(null)
const createFormRef = ref<FormInstance>()
const editFormRef = ref<FormInstance>()

const filters = reactive<{
  org_type: OrganizationType | ''
  province_code: string
  keyword: string
}>({
  org_type: '',
  province_code: '',
  keyword: '',
})

const createForm = reactive({
  org_code: '',
  org_name: '',
  org_type: 'SCHOOL' as OrganizationType,
  province_code: '',
})

const editForm = reactive({
  org_name: '',
  province_code: '',
  status: 1 as OrganizationStatus,
  version: 0,
})

const createRules: FormRules = {
  org_code: [{ required: true, message: '请输入组织编码', trigger: 'blur' }],
  org_name: [{ required: true, message: '请输入组织名称', trigger: 'blur' }],
  org_type: [{ required: true, message: '请选择组织类型', trigger: 'change' }],
}

const editRules: FormRules = {
  org_name: [{ required: true, message: '请输入组织名称', trigger: 'blur' }],
}

const typeLabel = (value: OrganizationType): string =>
  ORGANIZATION_TYPE_OPTIONS.find((option) => option.value === value)?.label ?? value

const requiresProvince = (type: OrganizationType): boolean =>
  type === 'SCHOOL' || type === 'PROVINCE_ORGANIZER'

const loadOrganizations = async (): Promise<void> => {
  loading.value = true
  try {
    const query: OrganizationListQuery = { page: page.value, size: size.value }
    if (filters.org_type) query.org_type = filters.org_type
    if (filters.province_code.trim()) query.province_code = filters.province_code.trim()
    if (filters.keyword.trim()) query.keyword = filters.keyword.trim()
    const data = await listOrganizations(query)
    organizations.value = data.items
    total.value = data.total
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '组织列表加载失败'))
  } finally {
    loading.value = false
  }
}

const handleSearch = (): void => {
  page.value = 1
  void loadOrganizations()
}

const handleReset = (): void => {
  filters.org_type = ''
  filters.province_code = ''
  filters.keyword = ''
  page.value = 1
  void loadOrganizations()
}

const openCreate = (): void => {
  createForm.org_code = ''
  createForm.org_name = ''
  createForm.org_type = 'SCHOOL'
  createForm.province_code = ''
  createVisible.value = true
}

const handleCreate = async (): Promise<void> => {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  if (requiresProvince(createForm.org_type) && !createForm.province_code.trim()) {
    ElMessage.warning('学校和省级主办方必须填写省份编码')
    return
  }
  saving.value = true
  try {
    await createOrganization({
      org_code: createForm.org_code.trim(),
      org_name: createForm.org_name.trim(),
      org_type: createForm.org_type,
      province_code: requiresProvince(createForm.org_type)
        ? createForm.province_code.trim()
        : null,
    })
    createVisible.value = false
    ElMessage.success('组织已创建')
    await loadOrganizations()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '创建组织失败'))
  } finally {
    saving.value = false
  }
}

const openEdit = (row: OrganizationItem): void => {
  editing.value = row
  editForm.org_name = row.org_name
  editForm.province_code = row.province_code ?? ''
  editForm.status = row.status
  editForm.version = row.version
  editVisible.value = true
}

const handleEdit = async (): Promise<void> => {
  if (!editing.value) return
  const valid = await editFormRef.value?.validate().catch(() => false)
  if (!valid) return
  if (requiresProvince(editing.value.org_type) && !editForm.province_code.trim()) {
    ElMessage.warning('学校和省级主办方必须填写省份编码')
    return
  }
  saving.value = true
  try {
    const result = await updateOrganization(editing.value.id, {
      org_name: editForm.org_name.trim(),
      province_code: requiresProvince(editing.value.org_type)
        ? editForm.province_code.trim()
        : null,
      status: editForm.status,
      version: editForm.version,
    })
    const row = organizations.value.find((item) => item.id === editing.value?.id)
    if (row) {
      row.org_name = editForm.org_name.trim()
      row.province_code = requiresProvince(editing.value.org_type)
        ? editForm.province_code.trim()
        : null
      row.status = editForm.status
      row.version = result.version
    }
    editVisible.value = false
    ElMessage.success('组织已更新')
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '更新组织失败'))
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  void loadOrganizations()
  void ensureCurrentMenus().catch(() => undefined)
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <div class="card-title">组织与学校</div>
            <div class="card-hint">省份编码使用 CN_PROVINCE，如四川省为 510000</div>
          </div>
          <div class="header-actions">
            <el-button v-if="canAssignRole" plain @click="scopeVisible = true">
              范围授权
            </el-button>
            <el-button v-if="canCreate" type="primary" @click="openCreate">
              创建组织
            </el-button>
          </div>
        </div>
      </template>

      <el-form :inline="true" class="filter-form" @submit.prevent>
        <el-form-item label="组织类型">
          <el-select v-model="filters.org_type" placeholder="全部" clearable class="filter-select">
            <el-option
              v-for="option in ORGANIZATION_TYPE_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="省份">
          <el-input v-model="filters.province_code" placeholder="如 510000" clearable />
        </el-form-item>
        <el-form-item label="关键字">
          <el-input
            v-model="filters.keyword"
            placeholder="组织名称或编码"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="organizations" border row-key="id">
        <el-table-column prop="org_code" label="组织编码" min-width="160" />
        <el-table-column prop="org_name" label="组织名称" min-width="180" />
        <el-table-column label="类型" min-width="120">
          <template #default="{ row }">
            <el-tag effect="plain">{{ typeLabel(row.org_type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="province_code" label="省份编码" width="110" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ ORGANIZATION_STATUS_LABELS[row.status as OrganizationStatus] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="80" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canUpdate" link type="primary" @click="openEdit(row)">
              编辑
            </el-button>
            <span v-else class="readonly-text">只读</span>
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
          @current-change="loadOrganizations"
          @size-change="handleSearch"
        />
      </div>
    </el-card>

    <el-dialog v-model="createVisible" title="创建组织" width="560px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
        <el-form-item label="组织编码" prop="org_code">
          <el-input v-model="createForm.org_code" maxlength="32" show-word-limit />
        </el-form-item>
        <el-form-item label="组织名称" prop="org_name">
          <el-input v-model="createForm.org_name" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="组织类型" prop="org_type">
          <el-select v-model="createForm.org_type" class="full-width">
            <el-option
              v-for="option in ORGANIZATION_TYPE_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="requiresProvince(createForm.org_type)" label="省份编码">
          <el-input v-model="createForm.province_code" placeholder="如 510000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑组织" width="560px" destroy-on-close>
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="100px">
        <el-form-item label="组织编码">
          <span>{{ editing?.org_code }}</span>
        </el-form-item>
        <el-form-item label="组织类型">
          <span>{{ editing ? typeLabel(editing.org_type) : '-' }}</span>
        </el-form-item>
        <el-form-item label="组织名称" prop="org_name">
          <el-input v-model="editForm.org_name" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item
          v-if="editing && requiresProvince(editing.org_type)"
          label="省份编码"
        >
          <el-input v-model="editForm.province_code" placeholder="如 510000" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="editForm.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleEdit">保存</el-button>
      </template>
    </el-dialog>

    <ScopeAssignmentDrawer v-model="scopeVisible" @saved="loadOrganizations" />
  </div>
</template>

<style scoped>
.page-container {
  padding: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
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

.header-actions {
  display: flex;
  gap: 10px;
}

.filter-form {
  margin-bottom: 4px;
}

.filter-select {
  width: 170px;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

.full-width {
  width: 100%;
}

.readonly-text {
  color: #909399;
  font-size: 12px;
}
</style>
