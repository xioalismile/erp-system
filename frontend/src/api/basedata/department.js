// 部门 API
import request from '@/utils/request'

const BASE = '/basedata/department'

export function listDepartment(params) { return request.get(`${BASE}/page`, { params }) }
export function listAllDepartment(params) { return request.get(`${BASE}/list`, { params }) }
export function getDepartment(id) { return request.get(`${BASE}/${id}`) }
export function saveDepartment(data) { return request.post(BASE, data) }
export function updateDepartment(data) { return request.put(BASE, data) }
export function deleteDepartment(id) { return request.delete(`${BASE}/${id}`) }
export function batchDeleteDepartment(ids) { return request.post(`${BASE}/batch-delete`, ids) }
export function treeDepartment(params) { return request.get(`${BASE}/tree`, { params }) }
