/**
 * 个人档案前端类型定义（BIZ-PROFILE-001）。
 * 字段以 docs/api/openapi-v1.json 的 EP-003 / EP-004 为唯一事实源：
 * 学生：real_name、student_no、college、major、grade；
 * 教师：real_name、college、title；
 * version 只用于乐观锁，不是业务资料字段。
 */

export type ProfileUserType = 'STUDENT' | 'TEACHER' | 'REVIEWER' | 'ADMIN' | ''

/** EP-003 当前用户响应中本任务关心的档案字段 */
export interface MyProfile {
  id: string
  username: string
  real_name: string
  user_type: string
  college?: string | null
  major?: string | null
  grade?: string | null
  student_no?: string | null
  title?: string | null
  version: number
  /** 旧数据只读兼容，不提供编辑入口 */
  skill_tags: string[]
  roles: string[]
  data_scopes: string[]
  permissions: string[]
}

/** 个人档案允许出现的全部字段名 */
export type ProfileFieldKey =
  | 'real_name'
  | 'student_no'
  | 'college'
  | 'major'
  | 'grade'
  | 'title'

/** EP-004 请求体：未提交的字段保持库内原值 */
export interface ProfileUpdatePayload {
  real_name?: string
  student_no?: string
  college?: string
  major?: string
  grade?: string
  title?: string
  version: number
}

/** EP-004 响应体 */
export interface ProfileUpdateResult {
  id: string
  version: number
}

export interface ProfileFieldDefinition {
  key: ProfileFieldKey
  label: string
  maxLength: number
  required: boolean
  placeholder: string
  hint: string
}

/** 与服务端 ProfileServiceImpl 的长度和必填规则保持一致 */
export const PROFILE_FIELD_DEFINITIONS: Record<ProfileFieldKey, ProfileFieldDefinition> = {
  real_name: {
    key: 'real_name',
    label: '姓名',
    maxLength: 32,
    required: true,
    placeholder: '请输入真实姓名',
    hint: '学生和教师均可修改，保存后不可留空',
  },
  student_no: {
    key: 'student_no',
    label: '学号',
    maxLength: 20,
    required: true,
    placeholder: '请输入本人学号',
    hint: '仅学生档案可修改，全平台唯一，不能包含空格',
  },
  college: {
    key: 'college',
    label: '学院',
    maxLength: 64,
    required: true,
    placeholder: '请输入所在学院',
    hint: '学生和教师均可修改',
  },
  major: {
    key: 'major',
    label: '专业',
    maxLength: 64,
    required: true,
    placeholder: '请输入所学专业',
    hint: '仅学生档案可修改',
  },
  grade: {
    key: 'grade',
    label: '年级',
    maxLength: 8,
    required: true,
    placeholder: '如 2024',
    hint: '仅学生档案可修改',
  },
  title: {
    key: 'title',
    label: '职称',
    maxLength: 32,
    required: true,
    placeholder: '如 副教授',
    hint: '仅教师档案可修改，学生页面不会出现该字段',
  },
}

const STUDENT_FIELD_KEYS: ProfileFieldKey[] = ['real_name', 'student_no', 'college', 'major', 'grade']
const TEACHER_FIELD_KEYS: ProfileFieldKey[] = ['real_name', 'college', 'title']
const COMMON_FIELD_KEYS: ProfileFieldKey[] = ['real_name', 'college']

export const PROFILE_EMPTY_FORM: Record<ProfileFieldKey, string> = {
  real_name: '',
  student_no: '',
  college: '',
  major: '',
  grade: '',
  title: '',
}

/** 当前角色可编辑的字段；教师不能看到学生字段，学生不能看到职称 */
export function profileFieldKeysFor(userType: string): ProfileFieldKey[] {
  if (userType === 'STUDENT') {
    return [...STUDENT_FIELD_KEYS]
  }
  if (userType === 'TEACHER') {
    return [...TEACHER_FIELD_KEYS]
  }
  return [...COMMON_FIELD_KEYS]
}

export const PROFILE_MESSAGES = {
  conflict: '资料已被修改，请刷新后重试',
  duplicateStudentNo: '学号已存在，请使用本人唯一学号',
  unknownConflict: '保存失败：数据发生冲突（HTTP 409），请刷新后重试',
  emptyPatch: '没有需要保存的修改',
  saved: '档案已保存，版本号已更新',
} as const

export const PROFILE_ROLE_LABELS: Record<string, string> = {
  STUDENT: '学生档案',
  TEACHER: '教师档案',
  REVIEWER: '评审专家档案',
  ADMIN: '管理员档案',
}
