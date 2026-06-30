// 工序 API
import request from '@/utils/request'

export function listWorkstation(params) {
  return request({ url: '/production/workstation/page', method: 'get', params })
}
export function listAllWorkstation() {
  return request({ url: '/production/workstation/list', method: 'get' })
}
export function getWorkstation(id) {
  return request({ url: `/production/workstation/${id}`, method: 'get' })
}
export function saveWorkstation(data) {
  return request({ url: '/production/workstation', method: 'post', data })
}
export function updateWorkstation(id, data) {
  return request({ url: `/production/workstation/${id}`, method: 'put', data })
}
export function deleteWorkstation(id) {
  return request({ url: `/production/workstation/${id}`, method: 'delete' })
}
