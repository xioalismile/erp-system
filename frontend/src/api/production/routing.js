import request from '@/utils/request'

export function listRouting(params) {
  return request({ url: '/production/routings/page', method: 'get', params })
}
export function listAllRouting() {
  return request({ url: '/production/routings/list', method: 'get' })
}
// 【2026-11-26】按物料取适用的工艺路线：工单成品行选路线时只列绑定了该物料的路线
export function listApplicableRouting(materialId) {
  return request({ url: '/production/routings/applicable', method: 'get', params: { materialId } })
}
// 顶部统计卡片数据（/api/production/routings/dashboard：基础资料类，与列表一致不做数据权限过滤）
export function getRoutingDashboard() {
  return request({ url: '/production/routings/dashboard', method: 'get' })
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
// 工艺路线里配置过的工序名（去重）—— 质检模板「适用工序」下拉等取候选用
export function listRoutingOperationNames() {
  return request({ url: '/production/routings/operation-names', method: 'get' })
}
// 工艺路线是「主档」不是「单据」（2026-09-23）：状态只有 启用/停用，不再有审核动作
export function enableRouting(id) {
  return request({ url: `/production/routings/${id}/enable`, method: 'post' })
}
export function disableRouting(id) {
  return request({ url: `/production/routings/${id}/disable`, method: 'post' })
}
