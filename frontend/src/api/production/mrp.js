// MRP 运算 API (baseURL 已含 /api 前缀, 此处不再拼 /api)
import request from '@/utils/request'

const BASE = '/production/mrp'

/** 单产品 MRP 计算 */
export function calculateMrp(data) {
  return request.post(`${BASE}/calculate`, data)
}

/** 批量 MRP 计算 */
export function calculateMrpBatch(demands) {
  return request.post(`${BASE}/calculate-batch`, { demands })
}

/** BOM 树形展开 */
export function expandBom(data) {
  return request.post(`${BASE}/bom-expand`, data)
}
