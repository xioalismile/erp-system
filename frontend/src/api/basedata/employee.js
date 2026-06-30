// 员工 API
import request from '@/utils/request'

const BASE = '/basedata/employee'

export function listEmployee(params) { return request.get(`${BASE}/page`, { params }) }
export function listAllEmployee(params) { return request.get(`${BASE}/list`, { params }) }
export function getEmployee(id) { return request.get(`${BASE}/${id}`) }
export function saveEmployee(data) { return request.post(BASE, data) }
export function updateEmployee(data) { return request.put(BASE, data) }
export function deleteEmployee(id) { return request.delete(`${BASE}/${id}`) }
export function batchDeleteEmployee(ids) { return request.post(`${BASE}/batch-delete`, ids) }
