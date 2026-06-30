// 供应商 API
import request from '@/utils/request'

const BASE = '/basedata/supplier'

export function listSupplier(params) { return request.get(`${BASE}/page`, { params }) }
export function listAllSupplier(params) { return request.get(`${BASE}/list`, { params }) }
export function getSupplier(id) { return request.get(`${BASE}/${id}`) }
export function saveSupplier(data) { return request.post(BASE, data) }
export function updateSupplier(data) { return request.put(BASE, data) }
export function deleteSupplier(id) { return request.delete(`${BASE}/${id}`) }
export function batchDeleteSupplier(ids) { return request.post(`${BASE}/batch-delete`, ids) }
