// 打样单 API (baseURL 已含 /api 前缀)
import request from '@/utils/request'

const BASE = '/sample/orders'

export function pageSample(params) {
  return request.get(`${BASE}/page`, { params })
}
export function listSample(params) {
  return request.get(`${BASE}/list`, { params })
}
export function getSample(id) {
  return request.get(`${BASE}/${id}`)
}
export function createSample(data) {
  return request.post(BASE, data)
}
export function updateSample(id, data) {
  return request.put(`${BASE}/${id}`, data)
}
export function deleteSample(id) {
  return request.delete(`${BASE}/${id}`)
}
export function submitSample(id) {
  return request.post(`${BASE}/${id}/submit`)
}
export function approveSample(id) {
  return request.post(`${BASE}/${id}/approve`)
}
export function reverseApproveSample(id) {
  return request.post(`${BASE}/${id}/reverse-approve`)
}
export function startSample(id) {
  return request.post(`${BASE}/${id}/start`)
}
export function completeSample(id) {
  return request.post(`${BASE}/${id}/complete`)
}
export function confirmSample(id) {
  return request.post(`${BASE}/${id}/confirm`)
}
export function cancelSample(id, reason) {
  return request.post(`${BASE}/${id}/cancel`, { reason })
}
/** 打样转BOM：核心接口，已确认状态的打样单一键推送生成正式BOM */
export function convertToBom(id) {
  return request.post(`${BASE}/${id}/convert-to-bom`)
}
export function generateSampleNo() {
  return request.get(`${BASE}/generate-no`)
}
export function updateSampleProgress(id, progress) {
  return request.put(`${BASE}/${id}/progress`, { progress })
}
export function dashboard() {
  return request.get(`${BASE}/dashboard`)
}

// ===== 以下为状态流转统一接口（自动补齐） =====
export function rejectSample(id, reason) { return request.post(`${BASE}/${id}/reject`, { reason }) }
export function voidSample(id, reason) { return request.post(`${BASE}/${id}/void`, { reason }) }
export function unvoidSample(id) { return request.post(`${BASE}/${id}/unvoid`) }
export function closeOrderSample(id) { return request.post(`${BASE}/${id}/close-order`) }
export function reopenSample(id) { return request.post(`${BASE}/${id}/reopen`) }
