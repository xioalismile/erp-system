// 销售预收款单 API
import request from '@/utils/request'

const BASE = '/sale-pre-receipt'

// 列表页顶部统计卡片数据（与列表同数据权限口径）
export function getSalePreReceiptDashboard() { return request.get(`${BASE}/dashboard`) }

// 分页查询销售预收款单
export function listSalePreReceipt(params) { return request.get(BASE, { params }) }
// 查询单张销售预收款单详情
export function getSalePreReceipt(id) { return request.get(`${BASE}/${id}`) }
// 新增/保存销售预收款单
export function saveSalePreReceipt(data) { return request.post(BASE, data) }
// 审核销售预收款单
export function auditSalePreReceipt(id) { return request.put(`${BASE}/${id}/audit`) }
// 提交销售预收款单（草稿 → 待审核/已生效）
export function submitSalePreReceipt(id) { return request.post(`${BASE}/${id}/submit`) }
// 作废销售预收款单
export function voidSalePreReceipt(id) { return request.put(`${BASE}/${id}/void`) }
// 核销销售预收款单（items 为核销明细数组）
export function writeOffSalePreReceipt(id, items) { return request.post(`${BASE}/${id}/write-off`, items) }
// 删除销售预收款单
export function deleteSalePreReceipt(id) { return request.delete(`${BASE}/${id}`) }

// ===== 以下为状态流转统一接口（自动补齐） =====
export function rejectSalePreReceipt(id, reason) { return request.post(`${BASE}/${id}/reject`, { reason }) }
export function reverseApproveSalePreReceipt(id) { return request.post(`${BASE}/${id}/reverse-approve`) }
export function unvoidSalePreReceipt(id) { return request.post(`${BASE}/${id}/unvoid`) }
export function closeOrderSalePreReceipt(id) { return request.post(`${BASE}/${id}/close-order`) }
export function reopenSalePreReceipt(id) { return request.post(`${BASE}/${id}/reopen`) }
