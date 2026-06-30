// 仓库 API
import request from '@/utils/request'

const BASE = '/basedata/warehouse'

export function listWarehouse(params) { return request.get(`${BASE}/page`, { params }) }
export function listAllWarehouse(params) { return request.get(`${BASE}/list`, { params }) }
export function getWarehouse(id) { return request.get(`${BASE}/${id}`) }
export function saveWarehouse(data) { return request.post(BASE, data) }
export function updateWarehouse(data) { return request.put(BASE, data) }
export function deleteWarehouse(id) { return request.delete(`${BASE}/${id}`) }
export function batchDeleteWarehouse(ids) { return request.post(`${BASE}/batch-delete`, ids) }
