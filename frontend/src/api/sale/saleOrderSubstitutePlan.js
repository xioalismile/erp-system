// 替换发货指示 API（销售在订单行上预先指定替代品）
import request from '@/utils/request'

const BASE = '/sale/order-substitute-plans'

export function listSubstitutePlans(orderId) {
  return request.get(BASE, { params: { orderId } })
}

export function saveSubstitutePlan(data) {
  return request.post(BASE, data)
}

export function updateSubstitutePlan(id, data) {
  return request.put(`${BASE}/${id}`, data)
}

export function cancelSubstitutePlan(id) {
  return request.post(`${BASE}/${id}/cancel`)
}
