// 采购预付款单 API
import request from '@/utils/request'

const BASE = '/purchase-pre-payment'

// 列表页顶部统计卡片数据（与列表同数据权限口径）
export function getPurchasePrePaymentDashboard() { return request.get(BASE + '/dashboard') }

// 分页查询采购预付款单
export function listPurchasePrePayment(params) { return request.get(BASE, { params }) }
// 查询单张采购预付款单详情
export function getPurchasePrePayment(id) { return request.get(`${BASE}/${id}`) }
// 新增/保存采购预付款单
export function savePurchasePrePayment(data) { return request.post(BASE, data) }
// 审核采购预付款单
export function auditPurchasePrePayment(id) { return request.put(`${BASE}/${id}/audit`) }
// 提交采购预付款单（草稿 → 待审核/已生效）
export function submitPurchasePrePayment(id) { return request.post(`${BASE}/${id}/submit`) }
// 作废采购预付款单
export function voidPurchasePrePayment(id) { return request.put(`${BASE}/${id}/void`) }
// 核销采购预付款单（items 为核销明细数组）
export function writeOffPurchasePrePayment(id, items) { return request.post(`${BASE}/${id}/write-off`, items) }
// 删除采购预付款单
export function deletePurchasePrePayment(id) { return request.delete(`${BASE}/${id}`) }

// ===== 以下为状态流转统一接口（自动补齐） =====
export function rejectPurchasePrePayment(id, reason) { return request.post(`${BASE}/${id}/reject`, { reason }) }
export function reverseApprovePurchasePrePayment(id) { return request.post(`${BASE}/${id}/reverse-approve`) }
export function unvoidPurchasePrePayment(id) { return request.post(`${BASE}/${id}/unvoid`) }
export function closeOrderPurchasePrePayment(id) { return request.post(`${BASE}/${id}/close-order`) }
export function reopenPurchasePrePayment(id) { return request.post(`${BASE}/${id}/reopen`) }
