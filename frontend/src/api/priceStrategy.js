// 价格策略 API
import request from '@/utils/request'

const SALE_BASE = '/price/sale-strategy'
const PURCHASE_BASE = '/price/purchase-strategy'

/**
 * 单据取价（2026-09-20 价格策略修复）
 * 按设置中心「取价优先级」逐档取价：
 *   销售 = 价格策略 → 价格等级 → 物料默认售价（顺序可在「销售参数-取价优先级」切换）
 *   采购 = 价格策略 → 物料采购价（顺序可在「采购参数-取价优先级」切换）
 * @param {{side?:'sale'|'purchase', partnerId?:number|string, materialId:number|string, quantity?:number, taxType?:string}} params
 * @returns {{price:number|null, source:'strategy'|'level'|'material'|'none', sourceLabel:string}}
 */
export function fetchPrice(params) {
  return request.get('/price/fetch', { params })
}

// ==================== 销售价格策略 ====================

export function listSalePriceStrategy(params) {
  return request.get(`${SALE_BASE}/page`, { params })
}

export function getSalePriceStrategy(id) {
  return request.get(`${SALE_BASE}/${id}`)
}

export function saveSalePriceStrategy(data) {
  return request.post(SALE_BASE, data)
}

export function updateSalePriceStrategy(data) {
  return request.put(SALE_BASE, data)
}

export function deleteSalePriceStrategy(id) {
  return request.delete(`${SALE_BASE}/${id}`)
}

// 启用/禁用：后端只有 toggle-status 接口，前端按当前状态决定是否调用
export function toggleSalePriceStrategyStatus(id) {
  return request.post(`${SALE_BASE}/toggle-status/${id}`)
}

// 旧 API 兼容包装：enable/disable 统一调用 toggle-status
export function enableSalePriceStrategy(id) {
  return request.post(`${SALE_BASE}/toggle-status/${id}`)
}

export function disableSalePriceStrategy(id) {
  return request.post(`${SALE_BASE}/toggle-status/${id}`)
}

// 价格计算测试（后端为 GET 接口）
export function calculateSalePrice(params) {
  return request.get(`${SALE_BASE}/calculate`, { params })
}

// 新增页默认优先级：下一个可用号（不与现有策略重复）
export function nextSalePriceStrategyPriority() {
  return request.get(`${SALE_BASE}/next-priority`)
}

// ==================== 采购价格策略 ====================

export function listPurchasePriceStrategy(params) {
  return request.get(`${PURCHASE_BASE}/page`, { params })
}

export function getPurchasePriceStrategy(id) {
  return request.get(`${PURCHASE_BASE}/${id}`)
}

export function savePurchasePriceStrategy(data) {
  return request.post(PURCHASE_BASE, data)
}

export function updatePurchasePriceStrategy(data) {
  return request.put(PURCHASE_BASE, data)
}

export function deletePurchasePriceStrategy(id) {
  return request.delete(`${PURCHASE_BASE}/${id}`)
}

export function togglePurchasePriceStrategyStatus(id) {
  return request.post(`${PURCHASE_BASE}/toggle-status/${id}`)
}

export function enablePurchasePriceStrategy(id) {
  return request.post(`${PURCHASE_BASE}/toggle-status/${id}`)
}

export function disablePurchasePriceStrategy(id) {
  return request.post(`${PURCHASE_BASE}/toggle-status/${id}`)
}

export function calculatePurchasePrice(params) {
  return request.get(`${PURCHASE_BASE}/calculate`, { params })
}

// 新增页默认优先级：下一个可用号（不与现有策略重复）
export function nextPurchasePriceStrategyPriority() {
  return request.get(`${PURCHASE_BASE}/next-priority`)
}

// ===== 以下为状态流转统一接口（与后端 Sale/PurchasePriceStrategyController 对齐）=====
// 后端仅有 reject(67) / reverse-approve(74) / void(81) / unvoid(88)，无 approve / close-order / reopen 路由
export function rejectSalePriceStrategy(id, reason) { return request.post(`${SALE_BASE}/${id}/reject`, { reason }) }
export function reverseApproveSalePriceStrategy(id) { return request.post(`${SALE_BASE}/${id}/reverse-approve`) }
export function voidSalePriceStrategy(id, reason) { return request.post(`${SALE_BASE}/${id}/void`, { reason }) }
export function unvoidSalePriceStrategy(id) { return request.post(`${SALE_BASE}/${id}/unvoid`) }

export function rejectPurchasePriceStrategy(id, reason) { return request.post(`${PURCHASE_BASE}/${id}/reject`, { reason }) }
export function reverseApprovePurchasePriceStrategy(id) { return request.post(`${PURCHASE_BASE}/${id}/reverse-approve`) }
export function voidPurchasePriceStrategy(id, reason) { return request.post(`${PURCHASE_BASE}/${id}/void`, { reason }) }
export function unvoidPurchasePriceStrategy(id) { return request.post(`${PURCHASE_BASE}/${id}/unvoid`) }
