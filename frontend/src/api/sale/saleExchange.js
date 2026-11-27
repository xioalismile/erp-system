// 销售换货单 API
import request from '@/utils/request'

const BASE = '/sale/exchanges'

export function listExchanges(params) { return request.get(BASE, { params }) }
export function getExchangeDetail(id) { return request.get(`${BASE}/${id}`) }

// 销售订单页「销售替代出库」弹窗：可替换行（台账七个数 + 替代清单 + 可关联退货额度）
export function getReplaceableLines(orderId) { return request.get(`${BASE}/replaceable-lines`, { params: { orderId } }) }

// 由销售订单生成换货单草稿：data = { orderId, lines: [...] }
export function createExchangeFromOrder(data) { return request.post(`${BASE}/from-order`, data) }

export function saveExchange(data) { return request.post(BASE, data) }
export function updateExchange(id, data) { return request.put(`${BASE}/${id}`, data) }

export function submitExchange(id) { return request.post(`${BASE}/${id}/submit`) }
export function approveExchange(id) { return request.post(`${BASE}/${id}/approve`) }
export function rejectExchange(id, reason) { return request.post(`${BASE}/${id}/reject`, { reason }) }
export function reverseApproveExchange(id) { return request.post(`${BASE}/${id}/reverse-approve`) }
export function voidExchange(id, reason) { return request.post(`${BASE}/${id}/void`, { reason }) }
export function deleteExchange(id) { return request.delete(`${BASE}/${id}`) }
