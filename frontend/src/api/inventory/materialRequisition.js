import request from '@/utils/request'

export function listMaterialRequisition(params) {
  return request({ url: '/inventory/material-requisitions/page', method: 'get', params })
}
export function listAllMaterialRequisition() {
  return request({ url: '/inventory/material-requisitions/list', method: 'get' })
}
export function getMaterialRequisition(id) {
  return request({ url: `/inventory/material-requisitions/${id}`, method: 'get' })
}
export function saveMaterialRequisition(data) {
  return request({ url: '/inventory/material-requisitions', method: 'post', data })
}
export function updateMaterialRequisition(id, data) {
  return request({ url: `/inventory/material-requisitions/${id}`, method: 'put', data })
}
export function deleteMaterialRequisition(id) {
  return request({ url: `/inventory/material-requisitions/${id}`, method: 'delete' })
}
export function approveMaterialRequisition(id, approver) {
  return request({ url: `/inventory/material-requisitions/${id}/approve`, method: 'post', params: { approver } })
}
export function closeMaterialRequisition(id, reason) {
  return request({ url: `/inventory/material-requisitions/${id}/close`, method: 'post', params: { reason } })
}
export function cancelMaterialRequisition(id, reason) {
  return request({ url: `/inventory/material-requisitions/${id}/cancel`, method: 'post', params: { reason } })
}
