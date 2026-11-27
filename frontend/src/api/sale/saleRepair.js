// 售后维修单 API
import request from '@/utils/request'

const BASE = '/sale/repairs'

export function listRepairs(params) { return request.get(BASE, { params }) }
export function getRepairNextNo() { return request.get(`${BASE}/next-no`) }
export function getRepairDetail(id) { return request.get(`${BASE}/${id}`) }

export function saveRepair(data) { return request.post(BASE, data) }
export function updateRepair(id, data) { return request.put(`${BASE}/${id}`, data) }
export function deleteRepair(id) { return request.delete(`${BASE}/${id}`) }

export function submitRepair(id) { return request.post(`${BASE}/${id}/submit`) }
export function approveRepair(id) { return request.post(`${BASE}/${id}/approve`) }
export function rejectRepair(id, reason) { return request.post(`${BASE}/${id}/reject`, { reason }) }
export function reverseApproveRepair(id) { return request.post(`${BASE}/${id}/reverse-approve`) }
export function voidRepair(id, reason) { return request.post(`${BASE}/${id}/void`, { reason }) }

// 维修过程动作
export function startRepair(id) { return request.post(`${BASE}/${id}/start`) }
export function completeRepair(id, result) { return request.post(`${BASE}/${id}/complete`, { result }) }

// 库存动作：后端只生成其他入库单 / 其他出库单草稿
export function receiveInRepair(id) { return request.post(`${BASE}/${id}/receive-in`) }
export function issuePartsRepair(id) { return request.post(`${BASE}/${id}/issue-parts`) }
export function returnToCustomerRepair(id) { return request.post(`${BASE}/${id}/return-to-customer`) }

// 转换新 / 转退货
export function transferRepairToExchange(id, lines) { return request.post(`${BASE}/${id}/transfer-exchange`, { lines }) }
export function transferRepairToReturn(id) { return request.post(`${BASE}/${id}/transfer-return`) }
