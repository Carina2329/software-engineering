import type { AxiosRequestConfig } from 'axios'
import request from './request'
import { createIdempotencyKey } from './adminRbac'
import type {
  IdVersionResult,
  OrganizationCreateRequest,
  OrganizationCreatedResult,
  OrganizationItem,
  OrganizationListQuery,
  OrganizationPageData,
  OrganizationUpdateRequest,
  RoleAssignmentCreateRequest,
  RoleAssignmentUpdateRequest,
  SchoolAffiliationListQuery,
  SchoolAffiliationPageData,
  SchoolAffiliationReviewRequest,
  SchoolAffiliationReviewResult,
  SchoolListQuery,
  SchoolPageData,
} from '../types/organization'

const idempotencyConfig = (key: string): AxiosRequestConfig => ({
  headers: { 'Idempotency-Key': key },
})

export const listOrganizations = (params: OrganizationListQuery): Promise<OrganizationPageData> =>
  request.get('/organizations', { params }) as Promise<OrganizationPageData>

export const getOrganization = (organizationId: string): Promise<OrganizationItem> =>
  request.get(`/organizations/${organizationId}`) as Promise<OrganizationItem>

export const createOrganization = (
  payload: OrganizationCreateRequest,
  idempotencyKey = createIdempotencyKey(),
): Promise<OrganizationCreatedResult> =>
  request.post(
    '/organizations',
    payload,
    idempotencyConfig(idempotencyKey),
  ) as Promise<OrganizationCreatedResult>

export const updateOrganization = (
  organizationId: string,
  payload: OrganizationUpdateRequest,
  idempotencyKey = createIdempotencyKey(),
): Promise<IdVersionResult> =>
  request.patch(
    `/organizations/${organizationId}`,
    payload,
    idempotencyConfig(idempotencyKey),
  ) as Promise<IdVersionResult>

export const listSchools = (params: SchoolListQuery): Promise<SchoolPageData> =>
  request.get('/schools', { params }) as Promise<SchoolPageData>

export const listSchoolAffiliations = (
  params: SchoolAffiliationListQuery,
): Promise<SchoolAffiliationPageData> =>
  request.get('/school-affiliations', { params }) as Promise<SchoolAffiliationPageData>

export const reviewSchoolAffiliation = (
  userId: string,
  payload: SchoolAffiliationReviewRequest,
  idempotencyKey = createIdempotencyKey(),
): Promise<SchoolAffiliationReviewResult> =>
  request.patch(
    `/school-affiliations/${userId}`,
    payload,
    idempotencyConfig(idempotencyKey),
  ) as Promise<SchoolAffiliationReviewResult>

export const createRoleAssignment = (
  payload: RoleAssignmentCreateRequest,
  idempotencyKey = createIdempotencyKey(),
): Promise<IdVersionResult> =>
  request.post(
    '/role-assignments',
    payload,
    idempotencyConfig(idempotencyKey),
  ) as Promise<IdVersionResult>

export const updateRoleAssignment = (
  assignmentId: string,
  payload: RoleAssignmentUpdateRequest,
  idempotencyKey = createIdempotencyKey(),
): Promise<IdVersionResult> =>
  request.patch(
    `/role-assignments/${assignmentId}`,
    payload,
    idempotencyConfig(idempotencyKey),
  ) as Promise<IdVersionResult>
