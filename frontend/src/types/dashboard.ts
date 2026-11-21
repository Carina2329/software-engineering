export type ContestLevel = 'SCHOOL' | 'PROVINCE' | 'NATIONAL'

export type DashboardConnectionState =
  | 'idle'
  | 'loading'
  | 'connecting'
  | 'connected'
  | 'reconnecting'
  | 'disconnected'
  | 'forbidden'
  | 'error'

export interface DashboardSummary {
  effective_school_count: number
  active_user_count: number
  registering_edition_count: number
  pending_affiliation_count: number
}

export interface DashboardLevelDistribution {
  level: ContestLevel
  count: number
}

export interface DashboardContestOperations {
  contest_series_count: number
  published_edition_count: number
  level_distribution: DashboardLevelDistribution[]
}

export interface DashboardProvinceSchool {
  province_code: string
  province_name: string
  school_count: number
}

export interface DashboardUserRole {
  role_code: string
  role_name: string
  user_count: number
}

export interface DashboardDeadline {
  edition_id: string
  edition_name: string
  contest_name: string
  level: ContestLevel
  register_deadline: string
}

export interface DashboardOverview {
  generated_at: string
  data_source: string
  summary: DashboardSummary
  contest_operations: DashboardContestOperations
  province_schools: DashboardProvinceSchool[]
  user_roles: DashboardUserRole[]
  upcoming_deadlines: DashboardDeadline[]
}

export interface DashboardTicket {
  ticket: string
  expires_at: string
}

export interface DashboardConnectedMessage {
  type: 'CONNECTED'
  version: 1
  generated_at: string
  data: null
}

export interface DashboardOverviewMessage {
  type: 'DASHBOARD_OVERVIEW'
  version: 1
  generated_at: string
  data: DashboardOverview
}

export type DashboardWebSocketMessage =
  | DashboardConnectedMessage
  | DashboardOverviewMessage
