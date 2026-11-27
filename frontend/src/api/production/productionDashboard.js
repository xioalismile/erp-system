import request from '@/utils/request'

const BASE = '/production-dashboard'

export function getOverview() {
  return request.get(`${BASE}/overview`)
}

export function getOrderStatusStats() {
  return request.get(`${BASE}/order-status-stats`)
}

export function getProductionProgress(current, size) {
  return request.get(`${BASE}/production-progress`, { params: { current, size } })
}

export function getWorkshopOutputStats() {
  return request.get(`${BASE}/workshop-output-stats`)
}

export function getEmployeeRanking(month) {
  return request.get(`${BASE}/employee-ranking`, { params: { month } })
}

export function getTodayOutputTrend() {
  return request.get(`${BASE}/today-output-trend`)
}

export function getWeekOutputTrend() {
  return request.get(`${BASE}/week-output-trend`)
}