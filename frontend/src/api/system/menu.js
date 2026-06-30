// 菜单管理 API
import request from '@/utils/request'

const BASE = '/system/menu'

export function listMenu() {
  return request.get(`${BASE}/list`)
}
export function getMenuTree() {
  return request.get(`${BASE}/tree`)
}
export function getMenu(id) {
  return request.get(`${BASE}/${id}`)
}
export function createMenu(data) {
  return request.post(BASE, data)
}
export function updateMenu(id, data) {
  return request.put(`${BASE}/${id}`, data)
}
export function deleteMenu(id) {
  return request.delete(`${BASE}/${id}`)
}
