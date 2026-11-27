// CRM客户档案 API - 第七部分 7.1 客户档案（CRM扩展字段）
// 后端：com.erp.masterdata.controller.CustomerController @RequestMapping("/api/customer")
// baseURL 已配置为 /api，所以这里使用 /customer 作为前缀
import request from '@/utils/request'

const BASE = '/customer'

// CRM客户分页查询：支持按CRM状态/销售阶段/意向等级/黑名单/分配销售员筛选
export function listCrmCustomer(params) {
  return request.get(`${BASE}/crm`, { params })
}

// CRM仪表盘：按CRM状态/销售阶段/意向等级统计客户分布
export function crmDashboard() {
  return request.get(`${BASE}/crm/dashboard`)
}

// 获取客户详情
export function getCustomer(id) {
  return request.get(`${BASE}/${id}`)
}

// 新增客户（含CRM扩展字段）
export function saveCustomer(data) {
  return request.post(BASE, data)
}

// 修改客户（含CRM扩展字段）
export function updateCustomer(id, data) {
  return request.put(`${BASE}/${id}`, data)
}

// 删除客户
export function deleteCustomer(id) {
  return request.delete(`${BASE}/${id}`)
}

// 更新CRM状态: 潜在客户/意向客户/正式客户/休眠客户/已流失
export function updateCrmStatus(id, crmStatus) {
  return request.put(`${BASE}/${id}/crm-status`, { crmStatus })
}

// 更新销售阶段: 初步接触/需求确认/方案报价/商务谈判/成交/已流失
export function updateSalesStage(id, salesStage) {
  return request.put(`${BASE}/${id}/sales-stage`, { salesStage })
}

// 更新意向等级: A/B/C/D
export function updateIntentionLevel(id, intentionLevel) {
  return request.put(`${BASE}/${id}/intention-level`, { intentionLevel })
}

// 切换黑名单状态: 0-否, 1-是
export function toggleBlacklist(id, isBlacklist) {
  return request.put(`${BASE}/${id}/blacklist`, { isBlacklist })
}

// 客户跟进登记：更新最后联系日期/下次联系日期/跟进次数
export function registerFollowUp(id, data) {
  return request.put(`${BASE}/${id}/follow-up`, data)
}

// 切换客户置顶状态（私海客户置顶）
export function toggleCustomerTop(id, isTop) {
  return request.post(`/customer/pool/${id}/toggle-top`, { isTop })
}
