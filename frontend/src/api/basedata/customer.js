// 客户 API
import request from '@/utils/request'

const BASE = '/basedata/customer'

export function listCustomer(params) { return request.get(`${BASE}/page`, { params }) }
export function listAllCustomer(params) { return request.get(`${BASE}/list`, { params }) }
// 客户下拉（通用搜索栏「客户」维度）：支持关键词模糊搜索（masterdata 轻量接口，最多 50 条）
export function customerSelectOptions(keyword) {
  return request.get('/masterdata/customers', { params: { keyword: keyword || undefined, limit: 50 } })
}
export function getCustomer(id) { return request.get(`${BASE}/${id}`) }
export function saveCustomer(data) { return request.post(BASE, data) }
export function updateCustomer(data) { return request.put(BASE, data) }
export function deleteCustomer(id) { return request.delete(`${BASE}/${id}`) }
export function batchDeleteCustomer(ids) { return request.post(`${BASE}/batch-delete`, ids) }

// 客户信用额度信息（客户档案只读展示：授信状态/额度/已用/剩余）。接口归属 masterdata：/api/customer/{id}/credit-summary
export function getCustomerCredit(id) { return request.get(`/customer/${id}/credit-summary`) }
