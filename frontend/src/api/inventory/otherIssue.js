import request from '@/utils/request'

export function listOtherIssue(params) {
  return request({ url: '/inventory/other-issues/page', method: 'get', params })
}
export function listAllOtherIssue() {
  return request({ url: '/inventory/other-issues/list', method: 'get' })
}
export function getOtherIssue(id) {
  return request({ url: `/inventory/other-issues/${id}`, method: 'get' })
}
export function saveOtherIssue(data) {
  return request({ url: '/inventory/other-issues', method: 'post', data })
}
export function updateOtherIssue(id, data) {
  return request({ url: `/inventory/other-issues/${id}`, method: 'put', data })
}
export function deleteOtherIssue(id) {
  return request({ url: `/inventory/other-issues/${id}`, method: 'delete' })
}
export function approveOtherIssue(id, approver) {
  return request({ url: `/inventory/other-issues/${id}/approve`, method: 'post', params: { approver } })
}
export function closeOtherIssue(id, reason) {
  return request({ url: `/inventory/other-issues/${id}/close`, method: 'post', params: { reason } })
}
export function cancelOtherIssue(id, reason) {
  return request({ url: `/inventory/other-issues/${id}/cancel`, method: 'post', params: { reason } })
}
