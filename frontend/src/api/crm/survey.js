// 客户满意度调查 API - 文档 7.6
import request from '@/utils/request'

const BASE = '/crm/survey'

export function listSurvey(params) {
  return request.get(`${BASE}/page`, { params })
}

export function getSurvey(id) {
  return request.get(`${BASE}/${id}`)
}

export function saveSurvey(data) {
  return request.post(BASE, data)
}

export function updateSurvey(id, data) {
  return request.put(`${BASE}/${id}`, data)
}

export function deleteSurvey(id) {
  return request.delete(`${BASE}/${id}`)
}

export function updateSurveyStatus(id, status) {
  return request.put(`${BASE}/${id}/status`, { status })
}

export function completeSurvey(id) {
  return request.put(`${BASE}/${id}/complete`)
}

export function analyzeSurvey(id, analyzeResult) {
  return request.put(`${BASE}/${id}/analyze`, { analyzeResult })
}

export function surveyDashboard() {
  return request.get(`${BASE}/dashboard`)
}
