// 自定义字段系统 API
// 后端：com.erp.customfield.controller.CustomFieldController @RequestMapping("/api/custom-field")
import request from '@/utils/request'

const BASE = '/custom-field'

// ========== 字段定义管理 ==========

/** 查询某实体的所有字段定义 */
export function listDefinition(entityType) {
  return request.get(`${BASE}/definition/${entityType}`)
}

/** 查询某实体启用的字段定义（前端表单渲染用） */
export function listEnabledDefinition(entityType) {
  return request.get(`${BASE}/definition/${entityType}/enabled`)
}

/** 批量查询多实体的启用字段定义 */
export function batchDefinition(entityTypes) {
  return request.post(`${BASE}/definition/batch`, entityTypes)
}

/** 新增/修改字段定义 */
export function saveDefinition(data) {
  return request.post(`${BASE}/definition`, data)
}

/** 删除字段定义 */
export function deleteDefinition(fieldId) {
  return request.delete(`${BASE}/definition/${fieldId}`)
}

// ========== 字段值管理 ==========

/** 获取单个实体实例的所有字段值 */
export function getFieldValues(entityType, entityId) {
  return request.get(`${BASE}/value/${entityType}/${entityId}`)
}

/** 批量获取多个实体实例的字段值（列表显示用） */
export function batchGetFieldValues(entityType, ids) {
  return request.get(`${BASE}/batch-value/${entityType}`, { params: { ids: ids.join(',') } })
}

/** 保存实体实例的字段值 */
export function saveFieldValues(entityType, entityId, values) {
  return request.post(`${BASE}/value/${entityType}/${entityId}`, values)
}

/** 删除实体实例的所有字段值 */
export function deleteFieldValues(entityType, entityId) {
  return request.delete(`${BASE}/value/${entityType}/${entityId}`)
}

// ========== 缓存管理 ==========

export function refreshCache() {
  return request.post(`${BASE}/refresh`)
}

// ========== 定制功能开关 ==========

/**
 * 「自定义字段下拉显示」定制功能总开关是否开启
 * （设置中心 → 系统参数 → 界面显示 可配；关闭后该定制功能在界面上不出现）
 * 取不到时按 true 处理，避免因配置/网络问题让功能消失
 */
export function getDropDisplayEnabled() {
  return request.get(`${BASE}/drop-display/enabled`)
}
