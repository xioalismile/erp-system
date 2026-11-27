// 工序工资核算 API
import request from '@/utils/request'

const BASE = '/finance/wage-settlements'

// 按员工聚合的汇总行（页面主表格数据源）：period 必填，deptId / employeeId 可选
export function getWageSummary(params) {
  return request.get(`${BASE}/summary`, { params })
}

// 已生成工资核算单分页
export function getWageSettlementPage(params) {
  return request.get(`${BASE}/page`, { params })
}

// 单据详情（含明细）
export function getWageSettlementDetail(id) {
  return request.get(`${BASE}/${id}`)
}

// 单据明细
export function getWageSettlementItems(id) {
  return request.get(`${BASE}/${id}/items`)
}

// 按期间生成工资核算单
export function generateWageSettlement(data) {
  return request.post(BASE, data)
}

// 提交：草稿 → 已生效
export function submitWageSettlement(id) {
  return request.post(`${BASE}/${id}/submit`)
}

// 作废：已生效 → 已作废
export function voidWageSettlement(id, reason) {
  return request.post(`${BASE}/${id}/void`, { reason })
}

// 反作废：已作废 → 已生效
export function unvoidWageSettlement(id) {
  return request.post(`${BASE}/${id}/unvoid`)
}
