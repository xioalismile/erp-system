// 工序 API
import request from '@/utils/request'

export function listWorkstation(params) {
  return request({ url: '/production/workstation/page', method: 'get', params })
}
export function listAllWorkstation() {
  return request({ url: '/production/workstation/list', method: 'get' })
}
// 顶部统计卡片数据（/api/production/workstation/dashboard：基础资料类，与列表一致不做数据权限过滤）
export function getWorkstationDashboard() {
  return request({ url: '/production/workstation/dashboard', method: 'get' })
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
/**
 * 【即输即存】工序（工位）下拉里直接输入名称后回车即保存：
 * 后端按名称去重（同名复用、不同名新建并自动生成编码），返回落库后的工序档案。
 */
export function quickSaveWorkstation(data) {
  return request({ url: '/production/workstation/quick-save', method: 'post', data })
}
