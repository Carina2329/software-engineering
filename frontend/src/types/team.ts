export type TeamStatus = 'DRAFT' | 'PENDING_TEACHER' | 'PENDING_ADMIN' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN'
export type TeamRegistrationStatus = 'PENDING_TEACHER' | 'PENDING_ADMIN' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN'
export type TeacherApplicationStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'WITHDRAWN'
export type ApprovalStage = 'TEACHER' | 'ADMIN'
export type ApprovalDecision = 'APPROVE' | 'REJECT'
export type EntryGrantAction = 'UPSERT' | 'REVOKE'
export type EntryGrantType = 'INVITED' | 'QUALIFIED'
export type EntryGrantStatus = 'ACTIVE' | 'REVOKED'

export interface TeamPageQuery {
  page?: number
  size?: number
  sort?: string
  status?: TeamStatus | ''
  topic_id?: string
  teacher_id?: string
  keyword?: string
}

export interface TeamPageItem {
  id: string
  team_name: string
  team_code: string
  status: TeamStatus
  member_count: number
}

export interface TeamPage {
  items: TeamPageItem[]
  total: number
  page: number
  size: number
  has_more: boolean
}

export interface TeamCreateRequest { team_name: string; version?: number }
export interface TeamCreateResult { id: string; team_code: string; status: TeamStatus; leader_member_id: string }
export interface TeamMemberInput { member_id?: string; member_name: string; school_id: string; student_no: string }
export interface TeamMember {
  member_id: string
  user_id: string | null
  member_name: string
  school_id: string
  student_no: string
  is_leader: boolean
  join_time: string | null
  status: string
}
export interface TeamUpdateRequest { team_name?: string; version: number; members?: TeamMemberInput[] }
export interface TeamVersionResult { id: string; version: number }

export interface TeamDetail {
  id: string
  edition_id: string
  team_name: string
  team_code: string
  topic_id: string | null
  leader_id: string
  teacher_id: string | null
  status: TeamStatus
  member_count: number
  members: string[]
  version: number
  current_registration_id: string | null
  current_registration_version: number | null
  current_registration_status: TeamRegistrationStatus | null
  current_reject_stage: ApprovalStage | null
  current_reject_reason: string | null
  current_teacher_id: string | null
  current_teacher_name: string | null
  current_teacher_application_status: TeacherApplicationStatus | null
  can_register: boolean
  register_state: string | null
  register_reason: string | null
}

export interface TeamRegistrationSubmitRequest {
  version: number
  teacher_id: string
  remark?: string
}
export interface TeamRegistrationSubmitResult {
  team_id: string
  status: TeamStatus
  version: number
  registration_id: string
  round_no: number
}
export interface TeamRegistrationWithdrawResult {
  team_id: string
  registration_id: string
  status: TeamStatus
  version: number
}
export interface TeamApprovalRequest {
  stage: ApprovalStage
  decision: ApprovalDecision
  reason?: string
  version: number
}
export interface TeamApprovalResult {
  team_id: string
  status: TeamStatus
  version: number
  registration_id: string
}
export interface AvailableTeacherQuery { keyword?: string; page?: number; size?: number }
export interface AvailableTeacher {
  id: string
  real_name: string
  school_id: string
  school_name: string
  college: string | null
  title: string | null
}
export interface AvailableTeacherPage {
  items: AvailableTeacher[]
  total: number
  page: number
  size: number
  has_more: boolean
}

export interface EntryGrant {
  id: string
  grant_type: EntryGrantType
  leader_user_id: string | null
  leader_name: string
  school_id: string
  student_no: string
  source_edition_id: string | null
  source_team_code: string | null
  target_team_id: string | null
  status: EntryGrantStatus
  reason: string | null
  version: number
}
export interface EntryGrantList { items: EntryGrant[] }
export interface EntryGrantBatchItem {
  action: EntryGrantAction
  grant_id?: string
  version?: number
  grant_type?: EntryGrantType
  leader_user_id?: string | null
  leader_name?: string
  school_id?: string
  student_no?: string
  source_edition_id?: string | null
  source_team_code?: string | null
  reason?: string | null
}
export interface EntryGrantBatchRequest { items: EntryGrantBatchItem[] }
export interface EntryGrantBatchResult { upserted_count: number; revoked_count: number }
export interface SchoolQuotaInput { school_id: string; max_teams: number | null }
export interface SchoolQuotaItem extends SchoolQuotaInput { used_teams?: number; school_name?: string }

export const TEAM_STATUS_LABELS: Record<TeamStatus, string> = {
  DRAFT: '草稿', PENDING_TEACHER: '待教师确认', PENDING_ADMIN: '待管理员终审',
  APPROVED: '已通过', REJECTED: '已驳回', WITHDRAWN: '已撤回',
}
export const TEAM_STATUS_TAG_TYPES: Record<TeamStatus, 'info' | 'warning' | 'success' | 'danger' | 'primary'> = {
  DRAFT: 'info', PENDING_TEACHER: 'warning', PENDING_ADMIN: 'primary',
  APPROVED: 'success', REJECTED: 'danger', WITHDRAWN: 'info',
}
export const ENTRY_GRANT_TYPE_LABELS: Record<EntryGrantType, string> = {
  INVITED: '邀请资格', QUALIFIED: '晋级资格',
}
export const ENTRY_GRANT_STATUS_LABELS: Record<EntryGrantStatus, string> = {
  ACTIVE: '有效', REVOKED: '已撤销',
}
export const editableTeamStatuses: TeamStatus[] = ['DRAFT', 'REJECTED', 'WITHDRAWN']
export const formatTeamDateTime = (value: string | null | undefined): string => {
  if (!value) return '未记录'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(date)
}
