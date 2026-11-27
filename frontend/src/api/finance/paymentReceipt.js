// 收款单 API
import request from '@/utils/request'

const BASE = '/finance'

// 分页查询收款单（原方法，保留向后兼容）
export function pageReceipt(params) {
  return request.get(`${BASE}/receipts`, { params })
}

// 查询单张收款单详情
export function getReceipt(id) {
  return request.get(`${BASE}/receipts/${id}`)
}

// 新增/保存收款单（原方法，保留向后兼容）
export function createReceipt(data) {
  return request.post(`${BASE}/receipts`, data)
}

// 确认收款
export function confirmReceipt(id) {
  return request.post(`${BASE}/receipts/${id}/confirm`)
}

// 提交收款单（草稿→开审批则待审核/免审批则直接生效）
export function submitReceipt(id) {
  return request.post(`${BASE}/receipts/${id}/submit`)
}

// ===== 任务规范要求的方法别名 =====
// 分页查询收款单
export function listReceipt(params) {
  return request.get(`${BASE}/receipts`, { params })
}

// 收款单列表统计卡（数值口径与列表一致，含数据权限）
export function getPaymentReceiptDashboard() {
  return request.get(`${BASE}/receipts/dashboard`)
}

// 保存收款单（新增/更新）
export function saveReceipt(data) {
  return request.post(`${BASE}/receipts`, data)
}

// ===== 以下为状态流转统一接口（自动补齐） =====
export function rejectReceipt(id, reason) { return request.post(`${BASE}/receipts/${id}/reject`, { reason }) }
export function reverseApproveReceipt(id) { return request.post(`${BASE}/receipts/${id}/reverse-approve`) }
export function voidReceipt(id, reason) { return request.post(`${BASE}/receipts/${id}/void`, { reason }) }
export function unvoidReceipt(id) { return request.post(`${BASE}/receipts/${id}/unvoid`) }
export function closeOrderReceipt(id) { return request.post(`${BASE}/receipts/${id}/close-order`) }
export function reopenReceipt(id) { return request.post(`${BASE}/receipts/${id}/reopen`) }
export function deleteReceipt(id) { return request.delete(`${BASE}/receipts/${id}`) }

// 按来源销售订单号查询收款记录（销售订单列表「收款详情」弹窗用）
// 含直接订单收款 + 发货路径（沿应收单来源订单号关联）两条路径
export function listReceiptsBySourceOrder(orderNo) {
  return request.get(`${BASE}/receipts/by-source-order`, { params: { orderNo } })
}

// 已核销（已收）金额汇总：按来源单据 + 来源明细行统计已生效收款单的核销金额
// 选单弹窗判断「已收 / 未收 / 能否再次收款」用（销售出库单、销售发票无已收列，必须靠核销明细反算）
export function getWriteoffReceived(params) {
  return request.get(`${BASE}/receipts/writeoff-received`, { params })
}

// 某来源单类型下已存在「未作废收款单」的来源单号
// 选单弹窗据此排除已开过收款单的来源单；收款单已作废的来源单重新可选
export function listActiveReceiptSourceNos(sourceType) {
  return request.get(`${BASE}/receipts/active-source-nos`, { params: { sourceType } })
}
