// CRM数据仪表盘 API - 聚合客户/线索/商机/合同/回款统计
import request from '@/utils/request'

// 客户CRM仪表盘（按CRM状态/销售阶段/意向等级统计客户分布）
export function customerDashboard() {
  return request.get('/customer/crm/dashboard')
}

// 商机仪表盘（阶段分布、成交数、预估金额合计、逾期跟进）
export function opportunityDashboard() {
  return request.get('/crm/opportunity/dashboard')
}
