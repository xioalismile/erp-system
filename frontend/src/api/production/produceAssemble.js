// 生产组装单 API
import request from '@/utils/request'
import { exportData } from '@/utils/exportUtil'
import { createBillDetailReportApi } from '@/api/report/billDetailReport'

const BASE = '/produce/assemble'

// 生产组装单明细报表 API（列表页「明细模式」取数/导出 + 筛选条件元数据；基路径与后端控制器一致）
export const produceAssembleDetailApi = createBillDetailReportApi('/report/production/assemble-detail')

export function pageProduceAssemble(params) {
  return request.get(BASE, { params })
}

// 列表页顶部统计卡片数据（与列表同数据权限口径）
export function getProduceAssembleDashboard() {
  return request.get(`${BASE}/dashboard`)
}

export function getProduceAssemble(id) {
  return request.get(`${BASE}/${id}`)
}

export function createProduceAssemble(data) {
  return request.post(BASE, data)
}

export function updateProduceAssemble(id, data) {
  return request.put(`${BASE}/${id}`, data)
}

export function deleteProduceAssemble(id) {
  return request.delete(`${BASE}/${id}`)
}

export function submitProduceAssemble(id) {
  return request.post(`${BASE}/${id}/submit`)
}

export function approveProduceAssemble(id, data) {
  return request.post(`${BASE}/${id}/approve`, data)
}

export function rejectProduceAssemble(id, data) {
  return request.post(`${BASE}/${id}/reject`, data)
}

export function revokeProduceAssemble(id) {
  return request.post(`${BASE}/${id}/revoke`)
}

export function cancelProduceAssemble(id, reason) {
  return request.post(`${BASE}/${id}/cancel`, { reason })
}

export function resetDraftProduceAssemble(id) {
  // 后端 /reset 端点即"重置草稿"语义(生效 → 草稿, 逆向库存), 与 reset-draft 预期一致
  return request.post(`${BASE}/${id}/reset`)
}

export function copyProduceAssemble(id) {
  return request.post(`${BASE}/${id}/copy`)
}

/**
 * 导出组装单：2026-09-20 起改走通用导出链路。
 * 原先调 `${BASE}/export`（= /api/produce/assemble/export），但后端 ProduceAssembleController 无该映射 → 点了必 404。
 * 现统一走 POST /api/export/data/produceAssemble（表头 + 明细行，套数据权限）。
 * 口径变化：由「按列表筛选条件导出」改为「按数据权限导全量」，与销售订单等 30 个单据一致。
 */
export function exportProduceAssemble() {
  return exportData('produceAssemble')
}

// ===== 以下为状态流转统一接口（自动补齐） =====
export function reverseApproveProduceAssemble(id) { return request.post(`${BASE}/${id}/reverse-approve`) }
export function unvoidProduceAssemble(id) { return request.post(`${BASE}/${id}/unvoid`) }
export function closeOrderProduceAssemble(id) { return request.post(`${BASE}/${id}/close-order`) }
export function reopenProduceAssemble(id) { return request.post(`${BASE}/${id}/reopen`) }

// ===== 别名导出（命名兼容） =====
export { createProduceAssemble as saveProduceAssemble }
