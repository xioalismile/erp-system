// 客户 API
import request from '@/utils/request'

const BASE = '/basedata/customer'

export function listCustomer(params) { return request.get(`${BASE}/page`, { params }) }
export function listAllCustomer(params) { return request.get(`${BASE}/list`, { params }) }
export function getCustomer(id) { return request.get(`${BASE}/${id}`) }
export function saveCustomer(data) { return request.post(BASE, data) }
export function updateCustomer(data) { return request.put(BASE, data) }
export function deleteCustomer(id) { return request.delete(`${BASE}/${id}`) }
export function batchDeleteCustomer(ids) { return request.post(`${BASE}/batch-delete`, ids) }
