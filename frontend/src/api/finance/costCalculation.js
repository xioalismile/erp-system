import request from '@/utils/request'

const BASE = '/cost-calculation'

export function getCostCalculationList(current, size, keyword, status) {
  return request.get(BASE, { params: { current, size, keyword, status } })
}

export function getCostCalculationDetail(id) {
  return request.get(`${BASE}/${id}`)
}

export function saveCostCalculation(data) {
  return request.post(BASE, data)
}

export function calculateCost(id) {
  return request.put(`${BASE}/${id}/calculate`)
}

export function confirmCost(id) {
  return request.put(`${BASE}/${id}/confirm`)
}

export function deleteCostCalculation(id) {
  return request.delete(`${BASE}/${id}`)
}

export function getUnitCost(materialId) {
  return request.get(`${BASE}/unit-cost/${materialId}`)
}

export function periodCalculate(period) {
  return request.post(`${BASE}/period-calculate`, { period })
}

export function getCostReport(period, keyword) {
  return request.get(`${BASE}/report`, { params: { period, keyword } })
}

// ===== 以下为状态流转统一接口（自动补齐） =====
export function rejectCostCalculation(id, reason) { return request.post(`${BASE}/${id}/reject`, { reason }) }
export function reverseApproveCostCalculation(id) { return request.post(`${BASE}/${id}/reverse-approve`) }
export function voidCostCalculation(id, reason) { return request.post(`${BASE}/${id}/void`, { reason }) }
export function unvoidCostCalculation(id) { return request.post(`${BASE}/${id}/unvoid`) }
export function closeOrderCostCalculation(id) { return request.post(`${BASE}/${id}/close-order`) }
export function reopenCostCalculation(id) { return request.post(`${BASE}/${id}/reopen`) }
