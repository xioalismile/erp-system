import request from '@/utils/request'

export function listInventory(params) {
  return request({ url: '/inventory/list', method: 'get', params })
}

export function getInventoryTransactions(params) {
  return request({ url: '/inventory/transactions', method: 'get', params })
}

export function getInventoryFlowList(params) {
  return request({ url: '/inventory/flow/list', method: 'get', params })
}

// 库存流水列表页顶部统计卡片数据（与 /inventory/flow/list 同数据权限口径）
export function getStockFlowDashboard() {
  return request({ url: '/inventory/flow/dashboard', method: 'get' })
}

// 即时库存列表页顶部统计卡片数据（与 /inventory/list 同数据权限口径）
export function getInventoryDashboard() {
  return request({ url: '/inventory/dashboard', method: 'get' })
}

// 批次库存列表页顶部统计卡片数据
export function getBatchInventoryStats() {
  return request({ url: '/inventory/batch/stats', method: 'get' })
}

/**
 * 批次字段口径 + 自动生成预览（2026-11-06）
 * @param {Object} params { materialId?, used? } used=当前单据已用批次号（逗号分隔）
 */
export function getBatchRuleInfo(params) {
  return request({ url: '/inventory/batch/rule-info', method: 'get', params })
}

/**
 * 批次溯源（2026-11-06）：相关单据 + 物料信息 + 多层组成批次
 * @param {Object} params { batchNo, materialId, depth? }
 */
export function getBatchTrace(params) {
  return request({ url: '/inventory/batch/trace', method: 'get', params })
}

/**
 * 按物料批量查询库存汇总（库存量 / 可用量 / 预计可用量）
 * 一次批量查询，跨仓库合计，服务端按"可见仓库"数据权限过滤
 * @param {Array<number>} materialIds 物料ID集合
 */
export function getMaterialStockSummary(materialIds) {
  return request({
    url: '/inventory/material-stock-summary',
    method: 'get',
    params: { materialIds: (materialIds || []).join(',') }
  })
}
