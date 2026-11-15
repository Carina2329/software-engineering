<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  createIdempotencyKey,
  createRole,
  getErrorMessage,
  getRolePermissions,
  listRoles,
  updateRole,
  updateRolePermissions,
} from '../../api/adminRbac'
import { DATA_SCOPE_LABELS, DATA_SCOPE_OPTIONS } from '../../types/rbac'
import type { DataScope, Role, RoleCreateRequest, RolePermissionData } from '../../types/rbac'
import { useCurrentMenus } from '../../layouts/useCurrentMenus'
import PermissionTree from './components/PermissionTree.vue'

interface PermissionTreeInstance {
  getSubmitKeys: () => string[]
}

const { ensureCurrentMenus, hasPermission } = useCurrentMenus()
const canCreate = computed(() => hasPermission('system:role:create'))
const canUpdate = computed(() => hasPermission('system:role:update'))
/** 只读查看授权依赖 system:role:list，保存授权依赖 system:role:authorize */
const canViewAuthorize = computed(() => hasPermission('system:role:list'))
const canAuthorize = computed(() => hasPermission('system:role:authorize'))

const loading = ref(false)
const roles = ref<Role[]>([])

const createVisible = ref(false)
const createSaving = ref(false)
const createFormRef = ref<FormInstance>()
const createKey = ref('')
const createForm = reactive<RoleCreateRequest>({ role_code: '', role_name: '', data_scope: 'SELF' })
const createRules: FormRules = {
  role_code: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    { min: 2, max: 16, message: '角色编码长度需为 2~16 个字符', trigger: 'blur' },
    { pattern: /^[A-Z][A-Z0-9_]*$/, message: '需以大写字母开头，仅包含大写字母、数字和下划线', trigger: 'blur' },
  ],
  role_name: [
    { required: true, message: '请输入角色名称', trigger: 'blur' },
    { max: 32, message: '角色名称不能超过 32 个字符', trigger: 'blur' },
  ],
  data_scope: [{ required: true, message: '请选择数据范围', trigger: 'change' }],
}

const editVisible = ref(false)
const editSaving = ref(false)
const editFormRef = ref<FormInstance>()
const editKey = ref('')
const editingRole = ref<Role | null>(null)
const editForm = reactive<{ role_name: string; data_scope: DataScope; version: number }>({
  role_name: '',
  data_scope: 'SELF',
  version: 0,
})
const editRules: FormRules = {
  role_name: [
    { required: true, message: '请输入角色名称', trigger: 'blur' },
    { max: 32, message: '角色名称不能超过 32 个字符', trigger: 'blur' },
  ],
  data_scope: [{ required: true, message: '请选择数据范围', trigger: 'change' }],
}

const authorizeVisible = ref(false)
const authorizeLoading = ref(false)
const authorizeSaving = ref(false)
const authorizeRole = ref<Role | null>(null)
const authorizeData = ref<RolePermissionData | null>(null)
const selectedMenuIds = ref<string[]>([])
const authorizeKey = ref('')
const permissionTreeRef = ref<PermissionTreeInstance | null>(null)

const dataScopeLabel = (value: DataScope): string => DATA_SCOPE_LABELS[value] ?? value

const loadRoles = async (): Promise<void> => {
  loading.value = true
  try {
    roles.value = await listRoles()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '角色列表加载失败'))
  } finally {
    loading.value = false
  }
}

const openCreate = (): void => {
  createForm.role_code = ''
  createForm.role_name = ''
  createForm.data_scope = 'SELF'
  createKey.value = createIdempotencyKey()
  createVisible.value = true
}

const handleCreate = async (): Promise<void> => {
  if (!createFormRef.value) return
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) return
  createSaving.value = true
  try {
    await createRole({ ...createForm }, createKey.value)
    createVisible.value = false
    ElMessage.success('角色已创建')
    await loadRoles()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '创建角色失败'))
  } finally {
    createSaving.value = false
  }
}

const openEdit = (row: Role): void => {
  editingRole.value = row
  editForm.role_name = row.role_name
  editForm.data_scope = row.data_scope
  editForm.version = row.version
  editKey.value = createIdempotencyKey()
  editVisible.value = true
}

const handleEdit = async (): Promise<void> => {
  if (!editingRole.value || !editFormRef.value) return
  const valid = await editFormRef.value.validate().catch(() => false)
  if (!valid) return
  editSaving.value = true
  try {
    const result = await updateRole(
      editingRole.value.id,
      { role_name: editForm.role_name, data_scope: editForm.data_scope, version: editForm.version },
      editKey.value,
    )
    const row = roles.value.find((item) => item.id === result.id)
    if (row) {
      row.role_name = editForm.role_name
      row.data_scope = editForm.data_scope
      row.version = result.version
    }
    editVisible.value = false
    ElMessage.success('角色已更新')
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '更新角色失败'))
  } finally {
    editSaving.value = false
  }
}

const openAuthorize = async (row: Role): Promise<void> => {
  authorizeRole.value = row
  authorizeVisible.value = true
  authorizeLoading.value = true
  authorizeData.value = null
  selectedMenuIds.value = []
  authorizeKey.value = createIdempotencyKey()
  try {
    const data = await getRolePermissions(row.id)
    authorizeData.value = data
    selectedMenuIds.value = [...data.menu_ids]
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '角色权限加载失败'))
    authorizeVisible.value = false
  } finally {
    authorizeLoading.value = false
  }
}

const handleAuthorize = async (): Promise<void> => {
  if (!canAuthorize.value) return
  if (!authorizeRole.value || !authorizeData.value) return
  const menuIds = permissionTreeRef.value ? permissionTreeRef.value.getSubmitKeys() : selectedMenuIds.value
  authorizeSaving.value = true
  try {
    const result = await updateRolePermissions(
      authorizeRole.value.id,
      { menu_ids: menuIds, version: authorizeData.value.version },
      authorizeKey.value,
    )
    const row = roles.value.find((item) => item.id === result.role_id)
    if (row) row.version = result.version
    authorizeData.value.version = result.version
    selectedMenuIds.value = [...menuIds]
    ElMessage.success('角色授权已保存')
    authorizeVisible.value = false
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '保存角色授权失败'))
  } finally {
    authorizeSaving.value = false
  }
}

onMounted(() => {
  void loadRoles()
  void ensureCurrentMenus().catch(() => undefined)
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">角色管理</span>
          <el-button v-if="canCreate" type="primary" @click="openCreate">创建角色</el-button>
          <span v-else class="card-hint">无创建角色权限</span>
        </div>
      </template>

      <el-table v-loading="loading" :data="roles" border row-key="id">
        <el-table-column prop="role_name" label="角色名称" min-width="140" />
        <el-table-column prop="role_code" label="角色编码" min-width="130" />
        <el-table-column label="数据范围" min-width="120">
          <template #default="{ row }">{{ dataScopeLabel(row.data_scope) }}</template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="80" />
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canUpdate" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="canViewAuthorize" link type="primary" @click="openAuthorize(row)">
              {{ canAuthorize ? '授权' : '查看授权' }}
            </el-button>
            <span v-if="!canUpdate && !canViewAuthorize" class="readonly-text">只读</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="createVisible" title="创建角色" width="480px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="90px">
        <el-form-item label="角色编码" prop="role_code">
          <el-input v-model="createForm.role_code" placeholder="如 OPERATOR" />
        </el-form-item>
        <el-form-item label="角色名称" prop="role_name">
          <el-input v-model="createForm.role_name" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="数据范围" prop="data_scope">
          <el-select v-model="createForm.data_scope" class="full-width">
            <el-option v-for="option in DATA_SCOPE_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSaving" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑角色" width="480px" destroy-on-close>
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="90px">
        <el-form-item label="角色编码">
          <span>{{ editingRole?.role_code }}</span>
        </el-form-item>
        <el-form-item label="角色名称" prop="role_name">
          <el-input v-model="editForm.role_name" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="数据范围" prop="data_scope">
          <el-select v-model="editForm.data_scope" class="full-width">
            <el-option v-for="option in DATA_SCOPE_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="handleEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="authorizeVisible" title="角色授权" size="520px" destroy-on-close>
      <div v-loading="authorizeLoading" class="authorize-panel">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="角色名称">{{ authorizeRole?.role_name }}</el-descriptions-item>
          <el-descriptions-item label="角色编码">{{ authorizeRole?.role_code }}</el-descriptions-item>
          <el-descriptions-item label="数据范围">{{ authorizeRole ? dataScopeLabel(authorizeRole.data_scope) : '' }}</el-descriptions-item>
          <el-descriptions-item label="当前版本">{{ authorizeData?.version ?? authorizeRole?.version }}</el-descriptions-item>
        </el-descriptions>
        <el-alert
          class="authorize-alert"
          type="info"
          :closable="false"
          show-icon
          title="勾选目录或菜单会联动选中子节点；保存时同时提交选中节点和半选父节点。"
        />
        <el-alert
          v-if="!canAuthorize"
          class="authorize-alert"
          type="warning"
          :closable="false"
          show-icon
          title="当前账号仅可查看授权，保存授权需要 system:role:authorize 权限。"
        />
        <PermissionTree
          v-if="authorizeData"
          ref="permissionTreeRef"
          :readonly="!canAuthorize"
          :model-value="selectedMenuIds"
          :resources="authorizeData.resources"
        />
      </div>
      <template #footer>
        <el-button @click="authorizeVisible = false">{{ canAuthorize ? '取消' : '关闭' }}</el-button>
        <el-button v-if="canAuthorize" type="primary" :loading="authorizeSaving" @click="handleAuthorize">保存授权</el-button>
      </template>
    </el-drawer>
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

.full-width {
  width: 100%;
}

.authorize-panel {
  min-height: 200px;
}

.authorize-alert {
  margin: 16px 0;
}

.readonly-text {
  color: #c0c4cc;
  font-size: 12px;
}
</style>
