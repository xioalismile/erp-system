// 电商平台 - 平台订单 API（拉单 / 转销售订单）
import request from '@/utils/request'

const BASE = '/ecommerce/orders'

export function listEcomOrders(params) { return request.get(BASE, { params }) }
export function getEcomOrder(id) { return request.get(`${BASE}/${id}`) }
// 拉取平台订单：{ shopId, days }
export function pullEcomOrders(data) { return request.post(`${BASE}/pull`, data) }
// 转销售订单（生成草稿态销售订单）
export function convertEcomOrder(id) { return request.post(`${BASE}/${id}/convert`) }
