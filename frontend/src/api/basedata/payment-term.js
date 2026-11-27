// 付款条件 API
import request from '@/utils/request'

const BASE = '/basedata/payment-term'

export function listPaymentTerm(params) { return request.get(`${BASE}/page`, { params }) }
export function listAllPaymentTerm(params) { return request.get(`${BASE}/list`, { params }) }
export function getPaymentTerm(id) { return request.get(`${BASE}/${id}`) }
export function savePaymentTerm(data) { return request.post(BASE, data) }
export function updatePaymentTerm(data) { return request.put(BASE, data) }
export function deletePaymentTerm(id) { return request.delete(`${BASE}/${id}`) }
export function batchDeletePaymentTerm(ids) { return request.post(`${BASE}/batch-delete`, ids) }

// 停用 / 启用（"下拉里删掉错误的值"走这里：停用后不再出现在下拉，但历史单据仍显示原值）
export function disablePaymentTerm(id) { return request.post(`${BASE}/disable/${id}`) }
export function enablePaymentTerm(id) { return request.post(`${BASE}/enable/${id}`) }
