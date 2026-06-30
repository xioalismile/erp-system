// 角色管理 API
import request from '@/utils/request'

const BASE = '/system/role'

export function pageRole(params) {
  return request.get(`${BASE}/page`, { params })
}
export function listAllRole() {
  return request.get(`${BASE}/list`)
}
export function getRole(id) {
  return request.get(`${BASE}/${id}`)
}
export function createRole(data) {
  return request.post(BASE, data)
}
export function updateRole(id, data) {
  return request.put(`${BASE}/${id}`, data)
}
export function deleteRole(id) {
  return request.delete(`${BASE}/${id}`)
}
export function assignPermissions(id, menuIds) {
  return request.post(`${BASE}/${id}/permissions`, menuIds)
}
