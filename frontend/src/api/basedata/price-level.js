// 价格等级 API
import request from '@/utils/request'

const BASE = '/basedata/price-level'

export function listPriceLevel(params) { return request.get(`${BASE}/page`, { params }) }
export function listAllPriceLevel(params) { return request.get(`${BASE}/list`, { params }) }
export function getPriceLevel(id) { return request.get(`${BASE}/${id}`) }
export function savePriceLevel(data) { return request.post(BASE, data) }
export function updatePriceLevel(data) { return request.put(BASE, data) }
export function deletePriceLevel(id) { return request.delete(`${BASE}/${id}`) }
export function batchDeletePriceLevel(ids) { return request.post(`${BASE}/batch-delete`, ids) }
