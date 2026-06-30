// 工作中心 API
import request from '@/utils/request'

export function listWorkCenter(params) {
  return request({ url: '/production/work-center/page', method: 'get', params })
}
export function listAllWorkCenter() {
  return request({ url: '/production/work-center/list', method: 'get' })
}
export function getWorkCenter(id) {
  return request({ url: `/production/work-center/${id}`, method: 'get' })
}
export function saveWorkCenter(data) {
  return request({ url: '/production/work-center', method: 'post', data })
}
export function updateWorkCenter(id, data) {
  return request({ url: `/production/work-center/${id}`, method: 'put', data })
}
export function deleteWorkCenter(id) {
  return request({ url: `/production/work-center/${id}`, method: 'delete' })
}
