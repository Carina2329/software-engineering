import type { AxiosRequestConfig } from 'axios'
import request, { ApiError, getApiErrorMessage } from './request'
import type {
  AvailableTeacherPage,
  AvailableTeacherQuery,
  EntryGrantBatchRequest,
  EntryGrantBatchResult,
  EntryGrantList,
  EntryGrantStatus,
  TeamApprovalRequest,
  TeamApprovalResult,
  TeamCreateRequest,
  TeamCreateResult,
  TeamDetail,
  TeamMember,
  TeamPage,
  TeamPageQuery,
  TeamRegistrationSubmitRequest,
  TeamRegistrationSubmitResult,
  TeamRegistrationWithdrawResult,
  TeamUpdateRequest,
  TeamVersionResult,
} from '../types/team'

const get = <T>(url: string, config?: AxiosRequestConfig): Promise<T> => request.get(url, config) as unknown as Promise<T>
const post = <T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> => request.post(url, data, config) as unknown as Promise<T>
const patch = <T>(url: string, data: unknown, config?: AxiosRequestConfig): Promise<T> => request.patch(url, data, config) as unknown as Promise<T>
const remove = <T>(url: string, config?: AxiosRequestConfig): Promise<T> => request.delete(url, config) as unknown as Promise<T>
const idempotencyConfig = (key: string): AxiosRequestConfig => ({ headers: { 'Idempotency-Key': key } })

export const createTeamIdempotencyKey = (): string => crypto.randomUUID()

export const getTeamErrorMessage = (error: unknown, fallback = '报名操作失败'): string => {
  if (error instanceof ApiError) {
    if (error.status === 401) return '登录状态已失效，请重新登录'
    if (error.status === 403) return error.message || '当前账号无权执行该操作'
    if (error.status === 404) return error.message || '队伍或届次不存在，或不在可见范围内'
    if (error.status === 429) return '操作过于频繁，请稍后再试'
    if (error.status === 503) return '服务暂时不可用，请稍后重试'
    if (error.status === 409) {
      const message = error.message || ''
      if (error.code === 40002 || /幂等|重复请求/.test(message)) return '重复请求正在处理中，请勿重复提交'
      if (/名额.*满|名额已满/.test(message)) return '学校报名名额已满，请联系管理员'
      if (/重复|唯一/.test(message)) return '存在重复资格或重复成员学号'
      if (/version|版本|已被他人修改/.test(message)) return '数据已更新，请刷新后重试'
      if (/状态|阶段/.test(message)) return '当前报名状态已变化，请刷新页面'
      return message || '业务状态冲突，请刷新后重试'
    }
    if (error.status === 400) return error.message || '提交内容不合法'
  }
  return getApiErrorMessage(error, fallback)
}

export const listTeams = (editionId: string, params: TeamPageQuery): Promise<TeamPage> =>
  get(`/editions/${encodeURIComponent(editionId)}/teams`, { params })
export const listAvailableTeachers = (editionId: string, params: AvailableTeacherQuery): Promise<AvailableTeacherPage> =>
  get(`/editions/${encodeURIComponent(editionId)}/available-teachers`, { params })
export const listApprovalTeachers = (editionId: string, params: AvailableTeacherQuery): Promise<AvailableTeacherPage> =>
  get(`/editions/${encodeURIComponent(editionId)}/approval-teachers`, { params })
export const createTeam = (editionId: string, payload: TeamCreateRequest, key: string): Promise<TeamCreateResult> =>
  post(`/editions/${encodeURIComponent(editionId)}/teams`, payload, idempotencyConfig(key))
export const getTeam = (teamId: string): Promise<TeamDetail> => get(`/teams/${encodeURIComponent(teamId)}`)
export const updateTeam = (teamId: string, payload: TeamUpdateRequest, key: string): Promise<TeamVersionResult> =>
  patch(`/teams/${encodeURIComponent(teamId)}`, payload, idempotencyConfig(key))
export const listTeamMembers = (teamId: string): Promise<TeamMember[]> =>
  get(`/teams/${encodeURIComponent(teamId)}/members`)
export const removeTeamMember = (teamId: string, memberId: string, version: number, key: string): Promise<void> =>
  remove(`/teams/${encodeURIComponent(teamId)}/members/${encodeURIComponent(memberId)}`, {
    params: { version }, headers: { 'Idempotency-Key': key },
  })
export const submitTeamRegistration = (teamId: string, payload: TeamRegistrationSubmitRequest, key: string): Promise<TeamRegistrationSubmitResult> =>
  post(`/teams/${encodeURIComponent(teamId)}/registration`, payload, idempotencyConfig(key))
export const approveTeamRegistration = (teamId: string, payload: TeamApprovalRequest, key: string): Promise<TeamApprovalResult> =>
  post(`/teams/${encodeURIComponent(teamId)}/approval`, payload, idempotencyConfig(key))
export const withdrawTeamRegistration = (teamId: string, version: number, key: string): Promise<TeamRegistrationWithdrawResult> =>
  remove(`/teams/${encodeURIComponent(teamId)}/registration`, {
    params: { version }, headers: { 'Idempotency-Key': key },
  })

export const listEntryGrants = (editionId: string, status?: EntryGrantStatus): Promise<EntryGrantList> =>
  get(`/editions/${encodeURIComponent(editionId)}/entry-grants`, {
    params: status ? { status } : undefined,
  })
export const batchEntryGrants = (editionId: string, payload: EntryGrantBatchRequest, key: string): Promise<EntryGrantBatchResult> =>
  post(`/editions/${encodeURIComponent(editionId)}/entry-grants/batch`, payload, idempotencyConfig(key))
