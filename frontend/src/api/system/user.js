// 用户管理 API
import request from '@/utils/request'

const BASE = '/system/user'

export function pageUser(params) {
  return request.get(`${BASE}/page`, { params })
}
export function getUser(id) {
  return request.get(`${BASE}/${id}`)
}
export function createUser(data) {
  return request.post(BASE, data)
}
export function updateUser(id, data) {
  return request.put(`${BASE}/${id}`, data)
}
export function deleteUser(id) {
  return request.delete(`${BASE}/${id}`)
}
export function resetPassword(id) {
  return request.post(`${BASE}/${id}/reset-password`)
}
export function assignRoles(id, roleIds) {
  return request.post(`${BASE}/${id}/roles`, roleIds)
}
