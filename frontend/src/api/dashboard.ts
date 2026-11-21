import request from './request'
import type { DashboardOverview, DashboardTicket } from '../types/dashboard'

export function getDashboardOverview(): Promise<DashboardOverview> {
  return request.get<DashboardOverview>('/dashboard/overview')
}

export function createDashboardTicket(): Promise<DashboardTicket> {
  return request.post<DashboardTicket>('/dashboard/ws-ticket')
}
