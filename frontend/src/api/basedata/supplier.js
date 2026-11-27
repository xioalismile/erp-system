// 供应商 API
import request from '@/utils/request'

const BASE = '/basedata/supplier'

export function listSupplier(params) { return request.get(`${BASE}/page`, { params }) }
export function listAllSupplier(params) { return request.get(`${BASE}/list`, { params }) }
// 供应商下拉（通用搜索栏「供应商」维度）：支持关键词模糊搜索
// 走采购模块既有的供应商联想接口（按 supplier_name/supplier_code 模糊匹配，最多 50 条）
export function supplierSelectOptions(keyword) {
  return request.get('/purchase/suppliers', { params: { keyword: keyword || undefined } })
}
export function getSupplier(id) { return request.get(`${BASE}/${id}`) }
export function saveSupplier(data) { return request.post(BASE, data) }
export function updateSupplier(data) { return request.put(BASE, data) }
export function deleteSupplier(id) { return request.delete(`${BASE}/${id}`) }
export function batchDeleteSupplier(ids) { return request.post(`${BASE}/batch-delete`, ids) }
