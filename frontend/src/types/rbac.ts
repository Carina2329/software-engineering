/**
 * RBAC 前端类型定义。
 * 字段以 docs/api/openapi-v1.json（EP-005/007/008/073~078）为唯一事实源。
 */

export type DataScope = 'ALL' | 'SELF' | 'TEAM' | 'GUIDED' | 'ASSIGNED'

/** 契约统一后的用户状态：整数 0=禁用，1=启用 */
export type UserStatusValue = 0 | 1

/** EP-005 用户列表项 */
export interface UserItem {
  id: string
  username: string
  real_name: string
  user_type: string
  status: number
  role_codes: string[]
  version: number
}

/** EP-005 查询参数 */
export interface UserListQuery {
  page?: number
  size?: number
  sort?: string
  username?: string
  user_type?: string
  status?: number
}

/** EP-005 分页结果 */
export interface UserListData {
  items: UserItem[]
  total: number
  page: number
  size: number
  has_more: boolean
}

/** EP-007 修改用户状态和角色 */
export interface UserPatchRequest {
  status?: UserStatusValue
  role_codes?: string[]
  version: number
}

export interface UserPatchResult {
  id: string
  version: number
}

/** EP-073 角色列表项 / EP-074 创建结果 */
export interface Role {
  id: string
  role_code: string
  role_name: string
  data_scope: DataScope
  version: number
}

/** EP-074 创建角色 */
export interface RoleCreateRequest {
  role_code: string
  role_name: string
  data_scope: DataScope
}

/** EP-075 修改角色 */
export interface RoleUpdateRequest {
  role_name?: string
  data_scope?: DataScope
  version: number
}

export interface RoleUpdateResult {
  id: string
  version: number
}

/** EP-008 与 EP-076 统一后的菜单类型 */
export type PermissionMenuType = 'DIRECTORY' | 'MENU' | 'BUTTON'

/** EP-008 / EP-076 权限资源节点，children 统一为嵌套结构 */
export interface PermissionResource {
  id: string
  parent_id: string | null
  menu_name: string
  menu_type: PermissionMenuType
  path: string | null
  perms: string | null
  sort_no: number
  visible: number
  status: number
  children?: PermissionResource[]
}

/** EP-077 角色权限 */
export interface RolePermissionData {
  role_id: string
  version: number
  menu_ids: string[]
  resources: PermissionResource[]
}

/** EP-078 替换角色权限 */
export interface RolePermissionUpdateRequest {
  menu_ids: string[]
  version: number
}

export interface RolePermissionUpdateResult {
  role_id: string
  version: number
}

export const DATA_SCOPE_LABELS: Record<DataScope, string> = {
  ALL: '全部数据',
  SELF: '本人数据',
  TEAM: '团队数据',
  GUIDED: '指导范围',
  ASSIGNED: '指派范围',
}

export const DATA_SCOPE_OPTIONS: Array<{ value: DataScope; label: string }> = [
  { value: 'ALL', label: DATA_SCOPE_LABELS.ALL },
  { value: 'SELF', label: DATA_SCOPE_LABELS.SELF },
  { value: 'TEAM', label: DATA_SCOPE_LABELS.TEAM },
  { value: 'GUIDED', label: DATA_SCOPE_LABELS.GUIDED },
  { value: 'ASSIGNED', label: DATA_SCOPE_LABELS.ASSIGNED },
]

export const USER_TYPE_OPTIONS: Array<{ value: string; label: string }> = [
  { value: 'STUDENT', label: '学生' },
  { value: 'TEACHER', label: '教师' },
  { value: 'REVIEWER', label: '评审专家' },
  { value: 'ADMIN', label: '管理员' },
]

export const USER_STATUS_LABELS: Record<number, string> = {
  0: '禁用',
  1: '启用',
}

export const MENU_TYPE_LABELS: Record<PermissionMenuType, string> = {
  DIRECTORY: '目录',
  MENU: '菜单',
  BUTTON: '按钮',
}
