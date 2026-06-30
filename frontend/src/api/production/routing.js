import request from '@/utils/request'

export function listRouting(params) {
  return request({ url: '/production/routings/page', method: 'get', params })
}
export function listAllRouting() {
  return request({ url: '/production/routings/list', method: 'get' })
}
export function getRouting(id) {
  return request({ url: `/production/routings/${id}`, method: 'get' })
}
export function saveRouting(data) {
  return request({ url: '/production/routings', method: 'post', data })
}
export function updateRouting(id, data) {
  return request({ url: `/production/routings/${id}`, method: 'put', data })
}
export function deleteRouting(id) {
  return request({ url: `/production/routings/${id}`, method: 'delete' })
}
