// 币种 API
import request from '@/utils/request'

const BASE = '/basedata/currency'

export function listCurrency(params) { return request.get(`${BASE}/page`, { params }) }
export function listAllCurrency(params) { return request.get(`${BASE}/list`, { params }) }
export function getCurrency(id) { return request.get(`${BASE}/${id}`) }
export function saveCurrency(data) { return request.post(BASE, data) }
export function updateCurrency(data) { return request.put(BASE, data) }
export function deleteCurrency(id) { return request.delete(`${BASE}/${id}`) }
export function batchDeleteCurrency(ids) { return request.post(`${BASE}/batch-delete`, ids) }
