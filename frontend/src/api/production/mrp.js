// MRP 运算 API (baseURL 已含 /api 前缀, 此处不再拼 /api)
import request from '@/utils/request'

const BASE = '/production/mrp'

/** 单产品 MRP 计算 */
export function calculateMrp(data) {
  return request.post(`${BASE}/calculate`, data)
}

/** 批量 MRP 计算（config = 页面「怎么算」参数：库存/安全库存/采购在途/生产中/多级展开 + 下单数量规则） */
export function calculateMrpBatch(demands, config) {
  return request.post(`${BASE}/calculate-batch`, { demands, config })
}

/** BOM 树形展开 */
export function expandBom(data) {
  return request.post(`${BASE}/bom-expand`, data)
}

/**
 * 可分析的销售物料明细（MRP 需求来源选择用，2026-10-06 改版）
 * 默认只返回「已生效订单 + 未发货完成 + 未分析过 + 未全部转下游」的物料行，每行带 hasBom
 * @param {Object} params { current, size, keyword, startDate, endDate, includeAnalyzed }
 */
export function listAvailableSaleItems(params) {
  return request.get(`${BASE}/available-sale-items`, { params })
}
