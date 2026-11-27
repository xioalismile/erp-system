// 销售订单 API
import request from '@/utils/request'

const BASE = '/sale/orders'
// 列表页顶部统计卡片数据（与列表同数据权限口径）
// 注意：dashboard 挂在控制器的 /api/sale 下（不是 /sale/orders 下），故这里写死绝对路径
export function getSaleOrderDashboard() { return request.get('/sale/dashboard') }

export function listSaleOrder(params) { return request.get(BASE, { params }) }
export function getSaleOrder(id) { return request.get(`${BASE}/${id}`) }
export function saveSaleOrder(data) { return request.post(BASE, data) }
export function updateSaleOrder(id, data) { return request.put(`${BASE}/${id}`, data) }
export function submitSaleOrder(id) { return request.post(`${BASE}/${id}/submit`) }
export function approveSaleOrder(id) { return request.post(`${BASE}/${id}/approve`) }
export function rejectSaleOrder(id, reason) { return request.post(`${BASE}/${id}/reject`, { reason }) }
export function reverseApproveSaleOrder(id) { return request.post(`${BASE}/${id}/reverse-approve`) }
export function closeSaleOrder(id, reason) { return request.post(`${BASE}/${id}/close`, { reason }) }
export function cancelSaleOrder(id, reason) { return request.post(`${BASE}/${id}/cancel`, { reason }) }
export function closeOrder(id) { return request.post(`${BASE}/${id}/close-order`) }
export function reopenOrder(id) { return request.post(`${BASE}/${id}/reopen`) }
export function voidSaleOrder(id, reason) { return request.post(`${BASE}/${id}/void`, { reason }) }
export function unvoidSaleOrder(id) { return request.post(`${BASE}/${id}/unvoid`) }
export function deleteSaleOrder(id) { return request.delete(`${BASE}/${id}`) }

// ==================== 下推接口 ====================
// 下推采购订单（后端端点为 /sale/orders/{id}/push-to-purchase，服务端生成采购订单并返回新单）
export function pushSaleOrderToPurchase(id) { return request.post(`${BASE}/${id}/push-to-purchase`) }

// 统一命名别名
export const closeOrderSaleOrder = closeOrder
export const reopenSaleOrder = reopenOrder
