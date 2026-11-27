import request from '@/utils/request'

/**
 * 对账单 API（原标准台账子系统仅保留的能力）
 *
 * 查询返回结构：
 *   {
 *     code: 200,
 *     data: {
 *       records: [...],          // 当前页数据
 *       total: 100,              // 总记录数
 *       page: 1,                 // 当前页码
 *       size: 20                 // 每页条数
 *     }
 *   }
 */

// ==================== 客户对账单 ====================
export function queryCustomerStatementSummary(params = {}) {
  return request.get('/report/standard/sale/customer-statement/summary', { params })
}

export function queryCustomerStatementDetail(params = {}) {
  return request.get('/report/standard/sale/customer-statement/detail', { params })
}

export function exportCustomerStatement(params = {}) {
  return request.get('/report/standard/sale/customer-statement/export', {
    params,
    responseType: 'blob'
  })
}

// ==================== 供应商对账单 ====================
export function querySupplierStatementSummary(params = {}) {
  return request.get('/report/standard/purchase/supplier-statement/summary', { params })
}

export function querySupplierStatementDetail(params = {}) {
  return request.get('/report/standard/purchase/supplier-statement/detail', { params })
}

export function exportSupplierStatement(params = {}) {
  return request.get('/report/standard/purchase/supplier-statement/export', {
    params,
    responseType: 'blob'
  })
}
