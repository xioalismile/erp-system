// 计量单位 API
import request from '@/utils/request'

const BASE = '/basedata/unit'

export function listUnit(params) { return request.get(`${BASE}/page`, { params }) }
export function listAllUnit(params) { return request.get(`${BASE}/list`, { params }) }
export function getUnit(id) { return request.get(`${BASE}/${id}`) }
export function saveUnit(data) { return request.post(BASE, data) }
export function updateUnit(data) { return request.put(BASE, data) }
export function deleteUnit(id) { return request.delete(`${BASE}/${id}`) }
export function batchDeleteUnit(ids) { return request.post(`${BASE}/batch-delete`, ids) }
