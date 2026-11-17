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

/** 版本冲突：HTTP 409 + 业务码 20001 */
export function isProfileConflict(error: unknown): boolean {
  return error instanceof ApiError && (error.code === 20001 || error.status === 409)
}

/** 学号重复：业务码 20002 */
export function isDuplicateStudentNo(error: unknown): boolean {
  return error instanceof ApiError && error.code === 20002
}

/**
 * 档案相关错误提示。
 * 版本冲突必须明确提示“资料已被修改，请刷新后重试”，不能静默覆盖。
 */
export function profileErrorMessage(error: unknown, fallback = '档案保存失败'): string {
  if (isProfileConflict(error)) {
    return PROFILE_MESSAGES.conflict
  }
  if (isDuplicateStudentNo(error)) {
    return PROFILE_MESSAGES.duplicateStudentNo
  }
  return getApiErrorMessage(error, fallback)
}
