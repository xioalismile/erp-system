import request from '@/utils/request'

/**
 * 自定义报表 API
 * 后端：CustomReportController @RequestMapping("/api/report/custom")
 * 统一返回结构由 axios 拦截器解包（直接拿到 data 字段）
 */

const BASE = '/report/custom'

// ==================== 设计器元数据 ====================

// 数据源列表（模块分组，设计器第一步选择）
export function listDataSources() {
  return request.get(`${BASE}/datasources`)
}

// 数据源完整元数据（主表字段 + 可用关联及字段）
export function describeDataSource(key) {
  return request.get(`${BASE}/datasources/${key}`)
}

// 跨单据关联候选列（双方树中同名的可关联外键列）
// 返回: { targetKey, targetName, joinables: [{column, columnLabel, leftSource, leftField, rightSource, rightField}] }
export function joinableColumns(key, targetKey) {
  return request.get(`${BASE}/datasources/${key}/joinables/${targetKey}`)
}

// ==================== 报表管理 ====================

// 我的报表列表（含共享给我的）
export function listCustomReports(params) {
  return request.get(`${BASE}/list`, { params })
}

// 报表详情（含 configJson）
export function getCustomReport(id) {
  return request.get(`${BASE}/${id}`)
}

// 新建报表
export function createCustomReport(data) {
  return request.post(BASE, data)
}

// 更新报表
export function updateCustomReport(id, data) {
  return request.put(`${BASE}/${id}`, data)
}

// 删除报表
export function deleteCustomReport(id) {
  return request.delete(`${BASE}/${id}`)
}

// 复制报表（副本为私有）
export function copyCustomReport(id) {
  return request.post(`${BASE}/${id}/copy`)
}

// ==================== 运行与导出 ====================

// 执行报表查询
// body: { startDate, endDate, keyword, status, page, size }
// 返回: { records, total, page, size, columns: [{field, label, type}] }
export function runCustomReport(id, data) {
  return request.post(`${BASE}/${id}/run`, data)
}

// 导出 Excel（返回 blob 流）
export function exportCustomReport(id, params) {
  return request.get(`${BASE}/${id}/export`, {
    params,
    responseType: 'blob'
  })
}
