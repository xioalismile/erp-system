import request from '@/utils/request'

const BASE = '/system/commission-rate'

// 当前生效版本
export function getCommissionRateCurrent() {
  return request.get(`${BASE}/current`)
}

// 某天生效的版本（按订单日期匹配历史提成率）
export function getCommissionRateEffective(date) {
  return request.get(`${BASE}/effective`, { params: { date } })
}

// 区间内所有生效的版本（用于跨月分段计算）
export function getCommissionRateRange(startDate, endDate) {
  return request.get(`${BASE}/range`, { params: { startDate, endDate } })
}

// 历史版本分页列表
export function getCommissionRateHistory(page = 1, size = 20, sortParams = {}) {
  return request.get(`${BASE}/history`, { params: { page, size, ...sortParams } })
}

// 新增一版（自动关闭上一版，留痕保存）
export function createCommissionRateVersion(data) {
  return request.post(BASE, data)
}
