// 工作中心 API
import request from '@/utils/request'

export function listWorkCenter(params) {
  return request({ url: '/production/work-center/page', method: 'get', params })
}
export function listAllWorkCenter() {
  return request({ url: '/production/work-center/list', method: 'get' })
}
// 顶部统计卡片数据（/api/production/work-center/dashboard：基础资料类，与列表一致不做数据权限过滤）
export function getWorkCenterDashboard() {
  return request({ url: '/production/work-center/dashboard', method: 'get' })
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
/**
 * 【即输即存】工作中心下拉里直接输入名称后回车即保存：
 * 后端按名称去重（同名复用、不同名新建并自动生成编码），返回落库后的工作中心档案。
 */
export function quickSaveWorkCenter(data) {
  return request({ url: '/production/work-center/quick-save', method: 'post', data })
}
