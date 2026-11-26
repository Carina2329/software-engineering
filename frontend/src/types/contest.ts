export type ContestLevel = 'SCHOOL' | 'PROVINCE' | 'NATIONAL'

export type EditionStatus =
  | 'DRAFT'
  | 'REGISTERING'
  | 'SUBMITTING'
  | 'REVIEWING'
  | 'PUBLISHING'
  | 'ARCHIVED'

export type TeamSizeMode = 'FIXED' | 'RANGE'

export type TopicMode =
  | 'PRESET_UNLIMITED'
  | 'PRESET_LIMITED'
  | 'SELF_PROPOSED'
  | 'NO_TOPIC'

export type TopicAllocationMode = 'REVIEW' | 'FIRST_COME'

export type EditionVisibility = 'DRAFT' | 'UNLISTED' | 'SCHOOL' | 'PROVINCE' | 'PUBLIC'

export type EntryMode = 'OPEN' | 'INVITED' | 'QUALIFIED'

export type MilestoneNodeType =
  | 'REGISTER_END'
  | 'WORK_END'
  | 'REVIEW'
  | 'PUBLISH'

export interface PageData<T> {
  items: T[]
  total: number
  page: number
  size: number
  has_more: boolean
}

export interface ContestItem {
  id: string
  contest_name: string
  level: ContestLevel
  organizer: string | null
  series_code: string
}

export interface ContestListQuery {
  page?: number
  size?: number
  sort?: string
  level?: ContestLevel | ''
  keyword?: string
}

export interface ContestDetail {
  id: string
  contest_name: string
  level: ContestLevel
  organizer: string | null
  intro: string | null
  version: number
  editions: string[]
  series_code: string
}

export interface ContestCreateRequest {
  contest_name: string
  level: ContestLevel
  series_code: string
  organizer?: string
  intro?: string
}

export interface ContestUpdateRequest {
  contest_name?: string
  level?: ContestLevel
  series_code?: string
  organizer?: string
  intro?: string
  version: number
}

export interface ContestCreateResult {
  id: string
  contest_name: string
}

export interface VersionResult {
  id: string
  version: number
}

export interface EditionItem {
  id: string
  edition_name: string
  year: number
  status: EditionStatus
  owner_org_id: string
  owner_org_name: string | null
  level: ContestLevel
  school_id: string | null
  province_code: string | null
  visibility: EditionVisibility
}

export interface EditionListQuery {
  page?: number
  size?: number
  sort?: string
  year?: number
  status?: EditionStatus | ''
  level?: ContestLevel | ''
  owner_org_id?: string
  province_code?: string
  school_id?: string
  visibility?: EditionVisibility | ''
}

export interface EditionDetail {
  id: string
  contest_id: string
  edition_name: string
  year: number
  status: EditionStatus
  topic_mode: TopicMode | null
  topic_review_required: boolean
  topic_allocation_mode: TopicAllocationMode
  topic_change_limit: number
  register_deadline: string | null
  work_deadline: string | null
  min_team_size: number
  max_team_size: number
  version: number
  team_size_mode: TeamSizeMode
  max_teams_per_teacher: number
  review_start: string | null
  publish_start: string | null
  publish_end: string | null
  registration_open: boolean
  submission_open: boolean
  read_only: boolean
  owner_org_id: string
  owner_org_name: string | null
  level: ContestLevel
  school_id: string | null
  province_code: string | null
  visibility: EditionVisibility
  entry_mode: EntryMode
  cross_school_team_allowed: boolean
  school_quotas: EditionSchoolQuota[]
}

export interface EditionSchoolQuota {
  school_id: string
  max_teams: number | null
  used_teams: number
}

export interface EditionCreateRequest {
  edition_name: string
  year: number
  register_deadline?: string | null
  work_deadline?: string | null
  review_start?: string | null
  publish_start?: string | null
  publish_end?: string | null
  min_team_size: number
  max_team_size: number
  topic_mode?: TopicMode | null
  topic_review_required?: boolean
  topic_allocation_mode?: TopicAllocationMode
  topic_change_limit?: number
  team_size_mode: TeamSizeMode
  max_teams_per_teacher?: number
  owner_org_id?: string
  level: ContestLevel
  school_id?: string | null
  province_code?: string | null
  visibility?: EditionVisibility
  entry_mode?: EntryMode
  cross_school_team_allowed?: boolean
}

export interface EditionUpdateRequest {
  edition_name?: string
  register_deadline?: string | null
  work_deadline?: string | null
  review_start?: string | null
  publish_start?: string | null
  publish_end?: string | null
  min_team_size?: number
  max_team_size?: number
  topic_mode?: TopicMode | null
  topic_review_required?: boolean
  topic_allocation_mode?: TopicAllocationMode
  topic_change_limit?: number
  version: number
  team_size_mode?: TeamSizeMode
  max_teams_per_teacher?: number
  owner_org_id?: string
  level?: ContestLevel
  school_id?: string | null
  province_code?: string | null
  visibility?: EditionVisibility
  entry_mode?: EntryMode
  cross_school_team_allowed?: boolean
  school_quotas?: Array<Pick<EditionSchoolQuota, 'school_id' | 'max_teams'>>
}

export interface EditionCreateResult {
  id: string
  status: EditionStatus
}

export interface EditionPublishResult {
  id: string
  status: EditionStatus
}

export interface Milestone {
  id: string
  version: number
  node_type: MilestoneNodeType
  node_name: string
  start_time: string
  end_time: string
  enabled: boolean
}

export interface MilestoneCreateRequest {
  node_type: MilestoneNodeType
  node_name: string
  start_time: string
  end_time: string
  enabled?: boolean
}

export interface MilestoneUpdateRequest {
  node_name?: string
  start_time?: string
  end_time?: string
  enabled?: boolean
  version: number
}

export interface MilestoneCreateResult {
  id: string
}

export interface MilestoneVersionResult {
  id: string
  version: number
}

export const CONTEST_LEVEL_OPTIONS: Array<{ value: ContestLevel; label: string }> = [
  { value: 'SCHOOL', label: '校级' },
  { value: 'PROVINCE', label: '省级' },
  { value: 'NATIONAL', label: '国家级' },
]

export const CONTEST_LEVEL_LABELS: Record<ContestLevel, string> = {
  SCHOOL: '校级',
  PROVINCE: '省级',
  NATIONAL: '国家级',
}

export const EDITION_STATUS_LABELS: Record<EditionStatus, string> = {
  DRAFT: '草稿',
  REGISTERING: '报名中',
  SUBMITTING: '作品提交中',
  REVIEWING: '评审中',
  PUBLISHING: '公示中',
  ARCHIVED: '已归档',
}

export const EDITION_VISIBILITY_LABELS: Record<EditionVisibility, string> = {
  DRAFT: '仅管理员',
  UNLISTED: '定向可见',
  SCHOOL: '本校可见',
  PROVINCE: '本省可见',
  PUBLIC: '公开可见',
}

export const EDITION_VISIBILITY_OPTIONS: Array<{
  value: EditionVisibility
  label: string
}> = Object.entries(EDITION_VISIBILITY_LABELS).map(([value, label]) => ({
  value: value as EditionVisibility,
  label,
}))

export const ENTRY_MODE_LABELS: Record<EntryMode, string> = {
  OPEN: '公开报名',
  INVITED: '邀请参加',
  QUALIFIED: '晋级名单',
}

export const ENTRY_MODE_OPTIONS: Array<{ value: EntryMode; label: string }> = [
  { value: 'OPEN', label: ENTRY_MODE_LABELS.OPEN },
  { value: 'INVITED', label: ENTRY_MODE_LABELS.INVITED },
  { value: 'QUALIFIED', label: ENTRY_MODE_LABELS.QUALIFIED },
]

export const TEAM_SIZE_OPTIONS: Array<{ value: TeamSizeMode; label: string }> = [
  { value: 'RANGE', label: '范围人数' },
  { value: 'FIXED', label: '固定人数' },
]

export const TOPIC_MODE_OPTIONS: Array<{
  value: TopicMode | ''
  label: string
  description: string
}> = [
  {
    value: 'PRESET_UNLIMITED',
    label: '统一赛题（不限量）',
    description: '所有队伍可选择预设赛题，不限制队伍容量。',
  },
  {
    value: 'PRESET_LIMITED',
    label: '统一赛题（限额）',
    description: '预设赛题配置名额，支持审核或先到先得。',
  },
  {
    value: 'SELF_PROPOSED',
    label: '自拟项目',
    description: '队伍提交自拟项目，按届次规则决定是否审核。',
  },
  {
    value: 'NO_TOPIC',
    label: '无赛题环节',
    description: '不要求选题或项目申报，可直接进入后续作品阶段。',
  },
]

export const TOPIC_ALLOCATION_OPTIONS: Array<{
  value: TopicAllocationMode
  label: string
}> = [
  { value: 'REVIEW', label: '审核分配' },
  { value: 'FIRST_COME', label: '先到先得（仅限额赛题）' },
]

export const MILESTONE_NODE_OPTIONS: Array<{
  value: MilestoneNodeType
  label: string
}> = [
  { value: 'REGISTER_END', label: '报名结束' },
  { value: 'WORK_END', label: '作品结束' },
  { value: 'REVIEW', label: '评审' },
  { value: 'PUBLISH', label: '公示' },
]

export const formatContestDateTime = (value: string | null | undefined): string => {
  if (!value) return '未配置'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}

/** 将 datetime-local 值按平台业务时区转为带偏移量的 RFC 3339 时间。 */
export const toApiDateTime = (value: string): string | null => {
  const trimmed = value.trim()
  if (!trimmed) return null
  if (/[zZ]|[+-]\d{2}:\d{2}$/.test(trimmed)) return trimmed
  return `${trimmed.length === 16 ? `${trimmed}:00` : trimmed}+08:00`
}

/** 将服务端时间转换为 Asia/Shanghai 的 datetime-local 输入值。 */
export const toDateTimeInput = (value: string | null | undefined): string => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const parts = new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).formatToParts(date)
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]))
  return `${values.year}-${values.month}-${values.day}T${values.hour}:${values.minute}`
}
