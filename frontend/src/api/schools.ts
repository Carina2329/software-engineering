import request from './request'
import type { SchoolOption } from '../types/profile'

/**
 * 学校主档查询（EP-085，GET /schools）。
 *
 * 学校主档与范围权限归 BIZ-ORG-SCOPE-001（罗同学）维护，
 * 这里只封装读取，不实现学校新增/审核等管理能力；
 * 请求统一走公共 request 实例，不创建第二套 Axios。
 */

export interface SchoolPage {
  items: SchoolOption[]
  total: number
  page: number
  size: number
  has_more: boolean
}

export interface SchoolQuery {
  keyword?: string
  provinceCode?: string
  page?: number
  size?: number
}

/** 个人档案选校下拉的选项上限，避免一次拉全量 */
export const SCHOOL_OPTION_PAGE_SIZE = 20

export function fetchSchools(query: SchoolQuery = {}): Promise<SchoolPage> {
  return request.get('/schools', {
    params: {
      page: query.page ?? 1,
      size: query.size ?? SCHOOL_OPTION_PAGE_SIZE,
      keyword: query.keyword?.trim() || undefined,
      province_code: query.provinceCode || undefined,
    },
  })
}

/** 下拉展示用：学校名称后附省份，避免同名学校混淆 */
export function schoolOptionLabel(school: SchoolOption): string {
  return school.province_name
    ? `${school.school_name}（${school.province_name}）`
    : school.school_name
}
