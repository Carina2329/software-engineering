import type { AxiosRequestConfig } from 'axios'
import request from './request'
import type {
  PermissionResource,
  Role,
  RoleCreateRequest,
  RolePermissionData,
  RolePermissionUpdateRequest,
  RolePermissionUpdateResult,
  RoleUpdateRequest,
  RoleUpdateResult,
  UserItem,
  UserListData,
  UserListQuery,
  UserPatchRequest,
  UserPatchResult,
} from '../types/rbac'

/**
 * RBAC 接口封装。
 * 全部真实请求复用公共 request 实例；Mock 仅由环境变量 VITE_USE_MOCK 控制。
 */
const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

/** 业务错误：保留业务码、HTTP 状态、trace_id 与 data，便于页面区分 20001/40002 */
export class ApiError extends Error {
  readonly code: number | null
  readonly httpStatus: number | null
  readonly traceId: string | null
  readonly data: unknown

  constructor(
    message: string,
    code: number | null = null,
    httpStatus: number | null = null,
    traceId: string | null = null,
    data: unknown = null,
  ) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.httpStatus = httpStatus
    this.traceId = traceId
    this.data = data
  }
}

interface ErrorPayload {
  code?: unknown
  message?: unknown
  trace_id?: unknown
  data?: unknown
}

interface ErrorCandidate {
  message?: unknown
  response?: {
    status?: unknown
    data?: ErrorPayload
  }
}

const toNumberOrNull = (value: unknown): number | null => {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    if (!Number.isNaN(parsed)) return parsed
  }
  return null
}

/** 将 Axios 异常或未知异常统一为 ApiError */
export const normalizeApiError = (error: unknown, fallback = '操作失败'): ApiError => {
  if (error instanceof ApiError) return error

  const candidate = typeof error === 'object' && error !== null ? (error as ErrorCandidate) : null
  const payload = candidate?.response?.data
  const code = toNumberOrNull(payload?.code)
  const httpStatus = toNumberOrNull(candidate?.response?.status)
  const traceId = typeof payload?.trace_id === 'string' ? payload.trace_id : null
  const data = payload?.data ?? null
  const message =
    (typeof payload?.message === 'string' && payload.message) ||
    (typeof candidate?.message === 'string' && candidate.message) ||
    fallback

  return new ApiError(message, code, httpStatus, traceId, data)
}

/** 页面统一错误文案：20001 提示刷新，40002 提示不要重复提交 */
export const getErrorMessage = (error: unknown, fallback = '操作失败'): string => {
  const normalized = normalizeApiError(error, fallback)
  if (normalized.code === 20001) return '数据已被他人修改，请刷新后重试'
  if (normalized.code === 40002) return '请求正在处理中，请勿重复提交'
  return normalized.message
}

const get = <T>(url: string, config?: AxiosRequestConfig): Promise<T> =>
  request.get(url, config) as unknown as Promise<T>

const post = <T>(url: string, data: unknown, config?: AxiosRequestConfig): Promise<T> =>
  request.post(url, data, config) as unknown as Promise<T>

const patch = <T>(url: string, data: unknown, config?: AxiosRequestConfig): Promise<T> =>
  request.patch(url, data, config) as unknown as Promise<T>

const put = <T>(url: string, data: unknown, config?: AxiosRequestConfig): Promise<T> =>
  request.put(url, data, config) as unknown as Promise<T>

const respond = async <T>(action: () => T | Promise<T>): Promise<T> => {
  try {
    return await action()
  } catch (error) {
    throw normalizeApiError(error)
  }
}

/** 客户端幂等键，满足 Idempotency-Key 最长 64 字符约束 */
export const createIdempotencyKey = (): string => crypto.randomUUID()

const idempotencyConfig = (idempotencyKey: string): AxiosRequestConfig => ({
  headers: { 'Idempotency-Key': idempotencyKey },
})

const mockConflict = (message: string): never => {
  throw new ApiError(message, 20001, 409)
}

const mockNotFound = (message: string): never => {
  throw new ApiError(message, null, 404)
}

// ---------------------------------------------------------------------------
// Mock 数据
// ---------------------------------------------------------------------------

/** 切换 Mock 当前登录角色：ADMIN 可见全部菜单，STUDENT 只能看到文件中心 */
const MOCK_CURRENT_ROLE_CODE = 'ADMIN'

const mockRoles: Role[] = [
  { id: 'r-admin', role_code: 'ADMIN', role_name: '系统管理员', data_scope: 'ALL', version: 1 },
  { id: 'r-student', role_code: 'STUDENT', role_name: '学生', data_scope: 'SELF', version: 1 },
  { id: 'r-teacher', role_code: 'TEACHER', role_name: '教师', data_scope: 'TEAM', version: 1 },
  { id: 'r-reviewer', role_code: 'REVIEWER', role_name: '评审专家', data_scope: 'ASSIGNED', version: 1 },
]

const mockUsers: UserItem[] = [
  { id: 'u-1', username: 'admin_demo', real_name: '演示管理员', user_type: 'ADMIN', status: 1, role_codes: ['ADMIN'], version: 1 },
  { id: 'u-2', username: 'student_a', real_name: '学生甲', user_type: 'STUDENT', status: 1, role_codes: ['STUDENT'], version: 1 },
  { id: 'u-3', username: 'student_b', real_name: '学生乙', user_type: 'STUDENT', status: 1, role_codes: ['STUDENT'], version: 1 },
  { id: 'u-4', username: 'student_c', real_name: '学生丙', user_type: 'STUDENT', status: 0, role_codes: ['STUDENT'], version: 1 },
  { id: 'u-5', username: 'student_d', real_name: '学生丁', user_type: 'STUDENT', status: 1, role_codes: ['STUDENT'], version: 1 },
  { id: 'u-6', username: 'student_e', real_name: '学生戊', user_type: 'STUDENT', status: 1, role_codes: ['STUDENT'], version: 1 },
  { id: 'u-7', username: 'student_f', real_name: '学生己', user_type: 'STUDENT', status: 1, role_codes: ['STUDENT'], version: 1 },
  { id: 'u-8', username: 'student_g', real_name: '学生庚', user_type: 'STUDENT', status: 1, role_codes: ['STUDENT'], version: 1 },
  { id: 'u-9', username: 'teacher_a', real_name: '教师甲', user_type: 'TEACHER', status: 1, role_codes: ['TEACHER'], version: 1 },
  { id: 'u-10', username: 'teacher_b', real_name: '教师乙', user_type: 'TEACHER', status: 0, role_codes: ['TEACHER'], version: 1 },
  { id: 'u-11', username: 'reviewer_a', real_name: '评审甲', user_type: 'REVIEWER', status: 1, role_codes: ['REVIEWER'], version: 1 },
  { id: 'u-12', username: 'reviewer_b', real_name: '评审乙', user_type: 'REVIEWER', status: 1, role_codes: ['REVIEWER'], version: 1 },
]

const mockMenuTree: PermissionResource[] = [
  {
    id: 'm-system',
    parent_id: null,
    menu_name: '系统管理',
    menu_type: 'DIRECTORY',
    path: '/admin',
    perms: null,
    sort_no: 10,
    visible: 1,
    status: 1,
    children: [
      {
        id: 'm-user',
        parent_id: 'm-system',
        menu_name: '用户管理',
        menu_type: 'MENU',
        path: '/admin/users',
        perms: 'system:user:list',
        sort_no: 10,
        visible: 1,
        status: 1,
        children: [
          { id: 'm-user-create', parent_id: 'm-user', menu_name: '创建用户', menu_type: 'BUTTON', path: null, perms: 'system:user:create', sort_no: 5, visible: 0, status: 1 },
          { id: 'm-user-update', parent_id: 'm-user', menu_name: '修改用户角色', menu_type: 'BUTTON', path: null, perms: 'system:user:update', sort_no: 10, visible: 0, status: 1 },
        ],
      },
      {
        id: 'm-role',
        parent_id: 'm-system',
        menu_name: '角色管理',
        menu_type: 'MENU',
        path: '/admin/roles',
        perms: 'system:role:list',
        sort_no: 20,
        visible: 1,
        status: 1,
        children: [
          { id: 'm-role-create', parent_id: 'm-role', menu_name: '创建角色', menu_type: 'BUTTON', path: null, perms: 'system:role:create', sort_no: 10, visible: 0, status: 1 },
          { id: 'm-role-update', parent_id: 'm-role', menu_name: '修改角色', menu_type: 'BUTTON', path: null, perms: 'system:role:update', sort_no: 20, visible: 0, status: 1 },
          { id: 'm-role-authorize', parent_id: 'm-role', menu_name: '角色授权', menu_type: 'BUTTON', path: null, perms: 'system:role:authorize', sort_no: 30, visible: 0, status: 1 },
        ],
      },
      {
        id: 'm-menu',
        parent_id: 'm-system',
        menu_name: '权限资源',
        menu_type: 'MENU',
        path: '/admin/menus',
        perms: 'system:menu:list',
        sort_no: 30,
        visible: 1,
        status: 1,
        children: [],
      },
    ],
  },
  {
    id: 'm-file',
    parent_id: null,
    menu_name: '文件中心',
    menu_type: 'DIRECTORY',
    path: '/file-center',
    perms: null,
    sort_no: 20,
    visible: 1,
    status: 1,
    children: [
      {
        id: 'm-file-center',
        parent_id: 'm-file',
        menu_name: '文件管理',
        menu_type: 'MENU',
        path: '/file-center',
        perms: null,
        sort_no: 10,
        visible: 1,
        status: 1,
        children: [
          { id: 'm-file-upload', parent_id: 'm-file-center', menu_name: '上传文件', menu_type: 'BUTTON', path: null, perms: 'file:upload', sort_no: 10, visible: 0, status: 1 },
          { id: 'm-file-download', parent_id: 'm-file-center', menu_name: '下载文件', menu_type: 'BUTTON', path: null, perms: 'file:download', sort_no: 20, visible: 0, status: 1 },
        ],
      },
    ],
  },
]

const mockRoleMenuIds: Record<string, string[]> = {
  'r-admin': [
    'm-system', 'm-user', 'm-user-create', 'm-user-update', 'm-role', 'm-role-create', 'm-role-update', 'm-role-authorize', 'm-menu',
    'm-file', 'm-file-center', 'm-file-upload', 'm-file-download',
  ],
  'r-student': ['m-file', 'm-file-center', 'm-file-upload', 'm-file-download'],
  'r-teacher': ['m-file', 'm-file-center', 'm-file-upload', 'm-file-download'],
  'r-reviewer': ['m-file', 'm-file-center', 'm-file-download'],
}

let roleCounter = 100

const cloneRole = (role: Role): Role => ({ ...role })

const cloneUser = (user: UserItem): UserItem => ({ ...user, role_codes: [...user.role_codes] })

const cloneResource = (resource: PermissionResource): PermissionResource => ({
  ...resource,
  children: resource.children?.map(cloneResource),
})

const cloneMenuTree = (): PermissionResource[] => mockMenuTree.map(cloneResource)

const filterAllowedTree = (nodes: PermissionResource[], allowed: Set<string>): PermissionResource[] =>
  nodes.flatMap((node) => {
    if (!allowed.has(node.id)) return []
    const children = filterAllowedTree(node.children ?? [], allowed)
    return [{ ...node, children }]
  })

const buildCurrentMenus = (roleCode: string): PermissionResource[] => {
  const role = mockRoles.find((item) => item.role_code === roleCode) ?? mockRoles[1]
  const allowed = new Set(mockRoleMenuIds[role.id] ?? [])
  return filterAllowedTree(mockMenuTree, allowed)
}

const mockListUsers = (params: UserListQuery): UserListData => {
  const page = params.page && params.page > 0 ? params.page : 1
  const size = params.size && params.size > 0 ? params.size : 20
  const filtered = mockUsers.filter((user) => {
    if (params.username && !user.username.includes(params.username)) return false
    if (params.user_type && user.user_type !== params.user_type) return false
    if (params.status !== undefined && user.status !== params.status) return false
    return true
  })
  const start = (page - 1) * size
  const items = filtered.slice(start, start + size).map(cloneUser)
  return { items, total: filtered.length, page, size, has_more: start + items.length < filtered.length }
}

const mockUpdateUser = (userId: string, payload: UserPatchRequest): UserPatchResult => {
  const user = mockUsers.find((item) => item.id === userId)
  if (!user) return mockNotFound('用户不存在')
  if (payload.version !== user.version) mockConflict('数据已被他人修改，请刷新后重试')
  if (payload.status !== undefined) user.status = payload.status
  if (payload.role_codes) user.role_codes = [...payload.role_codes]
  user.version += 1
  return { id: user.id, version: user.version }
}

const mockCreateRole = (payload: RoleCreateRequest): Role => {
  if (mockRoles.some((role) => role.role_code === payload.role_code)) mockConflict('角色编码已存在')
  roleCounter += 1
  const role: Role = {
    id: `r-${roleCounter}`,
    role_code: payload.role_code,
    role_name: payload.role_name,
    data_scope: payload.data_scope,
    version: 0,
  }
  mockRoles.push(role)
  mockRoleMenuIds[role.id] = []
  return cloneRole(role)
}

const mockUpdateRole = (roleId: string, payload: RoleUpdateRequest): RoleUpdateResult => {
  const role = mockRoles.find((item) => item.id === roleId)
  if (!role) return mockNotFound('角色不存在')
  if (payload.version !== role.version) mockConflict('数据已被他人修改，请刷新后重试')
  if (payload.role_name !== undefined) role.role_name = payload.role_name
  if (payload.data_scope !== undefined) role.data_scope = payload.data_scope
  role.version += 1
  return { id: role.id, version: role.version }
}

const mockGetRolePermissions = (roleId: string): RolePermissionData => {
  const role = mockRoles.find((item) => item.id === roleId)
  if (!role) return mockNotFound('角色不存在')
  return {
    role_id: role.id,
    version: role.version,
    menu_ids: [...(mockRoleMenuIds[role.id] ?? [])],
    resources: cloneMenuTree(),
  }
}

const mockUpdateRolePermissions = (roleId: string, payload: RolePermissionUpdateRequest): RolePermissionUpdateResult => {
  const role = mockRoles.find((item) => item.id === roleId)
  if (!role) return mockNotFound('角色不存在')
  if (payload.version !== role.version) mockConflict('数据已被他人修改，请刷新后重试')
  mockRoleMenuIds[role.id] = [...payload.menu_ids]
  role.version += 1
  return { role_id: role.id, version: role.version }
}

// ---------------------------------------------------------------------------
// 对外接口
// ---------------------------------------------------------------------------

export const listUsers = (params: UserListQuery): Promise<UserListData> =>
  respond(() => (USE_MOCK ? mockListUsers(params) : get<UserListData>('/users', { params })))

export const updateUser = (userId: string, payload: UserPatchRequest, idempotencyKey: string): Promise<UserPatchResult> =>
  respond(() =>
    USE_MOCK
      ? mockUpdateUser(userId, payload)
      : patch<UserPatchResult>(`/users/${userId}`, payload, idempotencyConfig(idempotencyKey)),
  )

export const listRoles = (): Promise<Role[]> =>
  respond(() => (USE_MOCK ? mockRoles.map(cloneRole) : get<Role[]>('/roles')))

export const createRole = (payload: RoleCreateRequest, idempotencyKey: string): Promise<Role> =>
  respond(() =>
    USE_MOCK ? mockCreateRole(payload) : post<Role>('/roles', payload, idempotencyConfig(idempotencyKey)),
  )

export const updateRole = (roleId: string, payload: RoleUpdateRequest, idempotencyKey: string): Promise<RoleUpdateResult> =>
  respond(() =>
    USE_MOCK
      ? mockUpdateRole(roleId, payload)
      : patch<RoleUpdateResult>(`/roles/${roleId}`, payload, idempotencyConfig(idempotencyKey)),
  )

export const getMenuTree = (): Promise<PermissionResource[]> =>
  respond(() => (USE_MOCK ? cloneMenuTree() : get<PermissionResource[]>('/menus')))

export const getRolePermissions = (roleId: string): Promise<RolePermissionData> =>
  respond(() =>
    USE_MOCK ? mockGetRolePermissions(roleId) : get<RolePermissionData>(`/roles/${roleId}/menus`),
  )

export const updateRolePermissions = (
  roleId: string,
  payload: RolePermissionUpdateRequest,
  idempotencyKey: string,
): Promise<RolePermissionUpdateResult> =>
  respond(() =>
    USE_MOCK
      ? mockUpdateRolePermissions(roleId, payload)
      : put<RolePermissionUpdateResult>(`/roles/${roleId}/menus`, payload, idempotencyConfig(idempotencyKey)),
  )

export const getCurrentMenus = (): Promise<PermissionResource[]> =>
  respond(() => (USE_MOCK ? buildCurrentMenus(MOCK_CURRENT_ROLE_CODE) : get<PermissionResource[]>('/menus/current')))
