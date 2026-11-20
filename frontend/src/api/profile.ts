import request, { ApiError, getApiErrorMessage } from './request'
import { PROFILE_MESSAGES } from '../types/profile'
import type { MyProfile, ProfileUpdatePayload, ProfileUpdateResult } from '../types/profile'

/**
 * 个人档案接口（EP-003 / EP-004）。
 * 全部请求复用公共 request 实例，不创建第二套 Axios。
 */

/** 查询当前登录用户档案和最新 version */
export function fetchMyProfile(): Promise<MyProfile> {
  return request.get('/users/me')
}

/** 修改当前登录用户档案，version 由服务端做乐观锁校验 */
export function updateMyProfile(
  payload: ProfileUpdatePayload,
  idempotencyKey?: string,
): Promise<ProfileUpdateResult> {
  return request.patch('/users/me', payload, {
    headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined,
  })
}

/** 客户端幂等键，满足 Idempotency-Key 最长 64 字符约束 */
export function createProfileIdempotencyKey(): string {
  return crypto.randomUUID()
}

/** 版本冲突：只认业务码 20001（409 只是 HTTP 层面的冲突语义，不能代表版本冲突） */
export function isProfileConflict(error: unknown): boolean {
  return error instanceof ApiError && error.code === 20001
}

/** 学号重复：业务码 20002（与版本冲突同样是 HTTP 409，必须靠业务码区分） */
export function isDuplicateStudentNo(error: unknown): boolean {
  return error instanceof ApiError && error.code === 20002
}

/**
 * 学校归属冲突：公共校验（SchoolAffiliationService.validateSelection）复用业务码 20001，
 * 因此不能只看业务码，还要看服务端 message 是否在讲学校（已确认学校不能换、待审核不能换等）。
 */
export function isSchoolConflict(error: unknown): boolean {
  return error instanceof ApiError && error.code === 20001 && error.message.includes('学校')
}

/**
 * 未识别的 409：服务端返回了 HTTP 409，但业务码既不是 20001（版本冲突）也不是 20002（学号重复）。
 * 这类响应既不能当版本冲突提示，也不能静默吞掉，必须给出“冲突但原因未知”的明确提示。
 */
export function isUnknownConflict(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    error.status === 409 &&
    error.code !== 20001 &&
    error.code !== 20002
  )
}

/**
 * 档案相关错误提示。
 * 先判重复学号，再判学校冲突，最后判版本冲突：
 * 三者都可能是 HTTP 409 / 业务码 20001，只按状态码判断会把具体原因误导成“资料已被修改”。
 */
export function profileErrorMessage(error: unknown, fallback = '档案保存失败'): string {
  if (isDuplicateStudentNo(error)) {
    return PROFILE_MESSAGES.duplicateStudentNo
  }
  if (isSchoolConflict(error)) {
    // 学校冲突的文案由服务端给出（已确认学校不能换 / 待审核不能换 / 学校不存在等）
    return (error as ApiError).message
  }
  if (isProfileConflict(error)) {
    return PROFILE_MESSAGES.conflict
  }
  if (isUnknownConflict(error)) {
    return PROFILE_MESSAGES.unknownConflict
  }
  return getApiErrorMessage(error, fallback)
}
