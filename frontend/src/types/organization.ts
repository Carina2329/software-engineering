export type OrganizationType =
  | 'SCHOOL'
  | 'PROVINCE_ORGANIZER'
  | 'NATIONAL_ORGANIZER'
  | 'PLATFORM'

export type OrganizationStatus = 0 | 1

export interface OrganizationItem {
  id: string
  org_code: string
  org_name: string
  org_type: OrganizationType
  province_code: string | null
  status: OrganizationStatus
  version: number
}

export interface OrganizationPageData {
  items: OrganizationItem[]
  total: number
  page: number
  size: number
  has_more: boolean
}

export interface OrganizationListQuery {
  page?: number
  size?: number
  org_type?: OrganizationType | ''
  province_code?: string
  keyword?: string
}

export interface OrganizationCreateRequest {
  org_code: string
  org_name: string
  org_type: OrganizationType
  province_code: string | null
}

export interface OrganizationUpdateRequest {
  org_name?: string
  province_code?: string | null
  status?: OrganizationStatus
  version: number
}

export interface OrganizationCreatedResult {
  id: string
  org_code: string
}

export interface SchoolItem {
  id: string
  school_code: string
  school_name: string
  province_code: string
  province_name: string
}

export interface SchoolPageData {
  items: SchoolItem[]
  total: number
  page: number
  size: number
  has_more: boolean
}

export interface SchoolListQuery {
  page?: number
  size?: number
  keyword?: string
  province_code?: string
}

export type SchoolAffiliationStatus = 'PENDING' | 'VERIFIED' | 'REJECTED'
export type SchoolAffiliationDecision = 'APPROVE' | 'REJECT'

export interface SchoolAffiliationItem {
  user_id: string
  username: string
  real_name: string
  student_no: string | null
  school_id: string
  school_name: string
  status: SchoolAffiliationStatus
  review_reason: string | null
  submitted_at: string
  version: number
}

export interface SchoolAffiliationPageData {
  items: SchoolAffiliationItem[]
  total: number
  page: number
  size: number
  has_more: boolean
}

export interface SchoolAffiliationListQuery {
  status?: SchoolAffiliationStatus
  school_id?: string
  keyword?: string
  page?: number
  size?: number
}

export interface SchoolAffiliationReviewRequest {
  decision: SchoolAffiliationDecision
  reason?: string | null
  version: number
}

export interface SchoolAffiliationReviewResult {
  user_id: string
  status: Exclude<SchoolAffiliationStatus, 'PENDING'>
  version: number
}

export type RoleScopeType = 'ORGANIZATION' | 'PROVINCE' | 'EVENT'
export type ScopedRoleCode =
  | 'SCHOOL_ADMIN'
  | 'PROVINCE_ADMIN'
  | 'NATIONAL_ADMIN'
  | 'EVENT_MANAGER'
  | 'REVIEWER'

export interface RoleAssignmentCreateRequest {
  user_id: string
  role_code: ScopedRoleCode
  scope_type: RoleScopeType
  scope_id: string
  valid_from?: string | null
  valid_to?: string | null
}

export interface RoleAssignmentUpdateRequest {
  scope_type?: RoleScopeType
  scope_id?: string
  active?: boolean
  valid_to?: string | null
  version: number
}

export interface IdVersionResult {
  id: string
  version: number
}

export const ORGANIZATION_TYPE_OPTIONS: Array<{
  value: OrganizationType
  label: string
}> = [
  { value: 'SCHOOL', label: '学校' },
  { value: 'PROVINCE_ORGANIZER', label: '省级主办方' },
  { value: 'NATIONAL_ORGANIZER', label: '国家级主办方' },
  { value: 'PLATFORM', label: '平台组织' },
]

export const ORGANIZATION_STATUS_LABELS: Record<OrganizationStatus, string> = {
  0: '停用',
  1: '启用',
}

export const AFFILIATION_STATUS_LABELS: Record<SchoolAffiliationStatus, string> = {
  PENDING: '待确认',
  VERIFIED: '已确认',
  REJECTED: '已驳回',
}

export const SCOPE_ROLE_OPTIONS: Array<{
  value: ScopedRoleCode
  label: string
  scopes: RoleScopeType[]
}> = [
  { value: 'SCHOOL_ADMIN', label: '学校管理员', scopes: ['ORGANIZATION'] },
  { value: 'PROVINCE_ADMIN', label: '省级管理员', scopes: ['PROVINCE'] },
  { value: 'NATIONAL_ADMIN', label: '国家级管理员', scopes: ['ORGANIZATION'] },
  {
    value: 'EVENT_MANAGER',
    label: '赛事负责人',
    scopes: ['ORGANIZATION', 'PROVINCE', 'EVENT'],
  },
  {
    value: 'REVIEWER',
    label: '评审专家',
    scopes: ['ORGANIZATION', 'PROVINCE', 'EVENT'],
  },
]

export const ROLE_SCOPE_LABELS: Record<RoleScopeType, string> = {
  ORGANIZATION: '组织',
  PROVINCE: '省份',
  EVENT: '赛事',
}
