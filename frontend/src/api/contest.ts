import type { AxiosRequestConfig } from 'axios'
import request, { getApiErrorMessage } from './request'
import type {
  ContestCreateRequest,
  ContestCreateResult,
  ContestDetail,
  ContestItem,
  ContestListQuery,
  ContestUpdateRequest,
  EditionCreateRequest,
  EditionCreateResult,
  EditionDetail,
  EditionItem,
  EditionListQuery,
  EditionPublishResult,
  EditionUpdateRequest,
  Milestone,
  MilestoneCreateRequest,
  MilestoneCreateResult,
  MilestoneUpdateRequest,
  MilestoneVersionResult,
  PageData,
  VersionResult,
} from '../types/contest'

const get = <T>(url: string, config?: AxiosRequestConfig): Promise<T> =>
  request.get(url, config) as unknown as Promise<T>

const post = <T>(url: string, data: unknown, config?: AxiosRequestConfig): Promise<T> =>
  request.post(url, data, config) as unknown as Promise<T>

const patch = <T>(url: string, data: unknown, config?: AxiosRequestConfig): Promise<T> =>
  request.patch(url, data, config) as unknown as Promise<T>

const idempotencyConfig = (idempotencyKey: string): AxiosRequestConfig => ({
  headers: { 'Idempotency-Key': idempotencyKey },
})

export const createContestIdempotencyKey = (): string => crypto.randomUUID()
export const getContestErrorMessage = getApiErrorMessage

export const listContests = (params: ContestListQuery): Promise<PageData<ContestItem>> =>
  get('/contests', { params })

export const getContest = (contestId: string): Promise<ContestDetail> =>
  get(`/contests/${contestId}`)

export const createContest = (
  payload: ContestCreateRequest,
  idempotencyKey: string,
): Promise<ContestCreateResult> =>
  post('/contests', payload, idempotencyConfig(idempotencyKey))

export const updateContest = (
  contestId: string,
  payload: ContestUpdateRequest,
  idempotencyKey: string,
): Promise<VersionResult> =>
  patch(`/contests/${contestId}`, payload, idempotencyConfig(idempotencyKey))

export const listContestEditions = (
  contestId: string,
  params: EditionListQuery,
): Promise<PageData<EditionItem>> =>
  get(`/contests/${contestId}/editions`, { params })

export const createContestEdition = (
  contestId: string,
  payload: EditionCreateRequest,
  idempotencyKey: string,
): Promise<EditionCreateResult> =>
  post(`/contests/${contestId}/editions`, payload, idempotencyConfig(idempotencyKey))

export const getContestEdition = (editionId: string): Promise<EditionDetail> =>
  get(`/editions/${editionId}`)

export const updateContestEdition = (
  editionId: string,
  payload: EditionUpdateRequest,
  idempotencyKey: string,
): Promise<VersionResult> =>
  patch(`/editions/${editionId}`, payload, idempotencyConfig(idempotencyKey))

export const publishContestEdition = (
  editionId: string,
  version: number,
  idempotencyKey: string,
): Promise<EditionPublishResult> =>
  post(
    `/editions/${editionId}/publish`,
    { version },
    idempotencyConfig(idempotencyKey),
  )

export const listEditionMilestones = (editionId: string): Promise<Milestone[]> =>
  get(`/editions/${editionId}/milestones`)

export const createEditionMilestone = (
  editionId: string,
  payload: MilestoneCreateRequest,
  idempotencyKey: string,
): Promise<MilestoneCreateResult> =>
  post(
    `/editions/${editionId}/milestones`,
    payload,
    idempotencyConfig(idempotencyKey),
  )

export const updateEditionMilestone = (
  milestoneId: string,
  payload: MilestoneUpdateRequest,
  idempotencyKey: string,
): Promise<MilestoneVersionResult> =>
  patch(`/milestones/${milestoneId}`, payload, idempotencyConfig(idempotencyKey))
