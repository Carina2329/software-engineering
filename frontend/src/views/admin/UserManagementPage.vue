<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { createIdempotencyKey, getErrorMessage, listRoles, listUsers, updateUser } from '../../api/adminRbac'
import { USER_STATUS_LABELS, USER_TYPE_OPTIONS } from '../../types/rbac'
import type { Role, UserItem, UserListQuery, UserStatusValue } from '../../types/rbac'
import { useCurrentMenus } from '../../layouts/useCurrentMenus'

const { ensureCurrentMenus, hasPermission } = useCurrentMenus()
const canUpdate = computed(() => hasPermission('system:user:update'))

const loading = ref(false)
const saving = ref(false)
const users = ref<UserItem[]>([])
const roles = ref<Role[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

const filters = reactive<{ username: string; user_type: string; status: number | '' }>({
  username: '',
  user_type: '',
  status: '',
})

const editVisible = ref(false)
const editingUser = ref<UserItem | null>(null)
const editFormRef = ref<FormInstance>()
const idempotencyKey = ref('')
const editForm = reactive<{ status: UserStatusValue; role_codes: string[]; version: number }>({
  status: 1,
  role_codes: [],
  version: 0,
})

const editRules: FormRules = {
  role_codes: [{ required: true, message: '请至少选择一个角色', trigger: 'change' }],
}

const userTypeLabel = (value: string): string =>
  USER_TYPE_OPTIONS.find((option) => option.value === value)?.label ?? value

const statusLabel = (value: number): string => USER_STATUS_LABELS[value] ?? '未知'

const loadUsers = async (): Promise<void> => {
  loading.value = true
  try {
    const query: UserListQuery = { page: page.value, size: size.value }
    if (filters.username.trim()) query.username = filters.username.trim()
    if (filters.user_type) query.user_type = filters.user_type
    if (filters.status !== '') query.status = filters.status
    const data = await listUsers(query)
    users.value = data.items
    total.value = data.total
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '用户列表加载失败'))
  } finally {
    loading.value = false
  }
}

const loadRoles = async (): Promise<void> => {
  try {
    roles.value = await listRoles()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '角色列表加载失败'))
  }
}

const handleSearch = (): void => {
  page.value = 1
  void loadUsers()
}

const handleReset = (): void => {
  filters.username = ''
  filters.user_type = ''
  filters.status = ''
  page.value = 1
  void loadUsers()
}

const handlePageChange = (value: number): void => {
  page.value = value
  void loadUsers()
}

const handleSizeChange = (value: number): void => {
  size.value = value
  page.value = 1
  void loadUsers()
}

const openEdit = (row: UserItem): void => {
  editingUser.value = row
  editForm.status = row.status === 1 ? 1 : 0
  editForm.role_codes = [...row.role_codes]
  editForm.version = row.version
  idempotencyKey.value = createIdempotencyKey()
  editVisible.value = true
}

const handleSave = async (): Promise<void> => {
  if (!editingUser.value || !editFormRef.value) return
  const valid = await editFormRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const result = await updateUser(
      editingUser.value.id,
      { status: editForm.status, role_codes: [...editForm.role_codes], version: editForm.version },
      idempotencyKey.value,
    )
    const row = users.value.find((item) => item.id === result.id)
    if (row) {
      row.version = result.version
      row.status = editForm.status
      row.role_codes = [...editForm.role_codes]
    }
    editVisible.value = false
    ElMessage.success('用户信息已更新')
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '更新用户失败'))
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  void loadUsers()
  void loadRoles()
  void ensureCurrentMenus().catch(() => undefined)
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">用户管理</span>
          <span class="card-hint">仅展示后端允许的菜单和操作，真实权限由后端校验</span>
        </div>
      </template>

      <el-form :inline="true" class="filter-form" @submit.prevent>
        <el-form-item label="账号">
          <el-input v-model="filters.username" placeholder="请输入账号" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="用户类型">
          <el-select v-model="filters.user_type" placeholder="全部" clearable class="filter-select">
            <el-option v-for="option in USER_TYPE_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable class="filter-select">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="users" border row-key="id">
        <el-table-column prop="username" label="账号" min-width="140" />
        <el-table-column prop="real_name" label="姓名" min-width="110" />
        <el-table-column label="用户类型" min-width="110">
          <template #default="{ row }">{{ userTypeLabel(row.user_type) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="200">
          <template #default="{ row }">
            <el-tag v-for="code in row.role_codes" :key="code" class="role-tag" type="primary" effect="plain">
              {{ code }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="版本" prop="version" width="80" />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canUpdate" link type="primary" @click="openEdit(row)">编辑</el-button>
            <span v-else class="readonly-text">只读</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          :current-page="page"
          :page-size="size"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="editVisible" title="编辑用户" width="480px" destroy-on-close>
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="90px">
        <el-form-item label="账号">
          <span>{{ editingUser?.username }}</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="editForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
        </el-form-item>
        <el-form-item label="角色" prop="role_codes">
          <el-select v-model="editForm.role_codes" multiple placeholder="请选择角色" class="role-select">
            <el-option v-for="role in roles" :key="role.id" :label="`${role.role_name}（${role.role_code}）`" :value="role.role_code" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
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
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
}

.card-hint {
  font-size: 12px;
  color: #909399;
}

.filter-form {
  margin-bottom: 4px;
}

.filter-select {
  width: 150px;
}

.role-select {
  width: 100%;
}

.role-tag {
  margin-right: 6px;
  margin-bottom: 4px;
}

.readonly-text {
  color: #c0c4cc;
  font-size: 12px;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
