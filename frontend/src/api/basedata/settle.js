// 结算方式 API
import request from '@/utils/request'

const BASE = '/basedata/settle'

export function listSettle(params) { return request.get(`${BASE}/page`, { params }) }
export function listAllSettle(params) { return request.get(`${BASE}/list`, { params }) }
export function getSettle(id) { return request.get(`${BASE}/${id}`) }
export function saveSettle(data) { return request.post(BASE, data) }
export function updateSettle(data) { return request.put(BASE, data) }
export function deleteSettle(id) { return request.delete(`${BASE}/${id}`) }
export function batchDeleteSettle(ids) { return request.post(`${BASE}/batch-delete`, ids) }
