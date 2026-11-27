import request from '@/utils/request'

export function getOverview() {
  return request.get('/dashboard/overview')
}

export function getSaleTrend(days = 30) {
  return request.get('/dashboard/sale-trend', { params: { days } })
}

export function getPurchaseTrend(days = 30) {
  return request.get('/dashboard/purchase-trend', { params: { days } })
}

export function getTopSaleMaterials(limit = 10) {
  return request.get('/dashboard/top-sale', { params: { limit } })
}

export function getTopPurchaseMaterials(limit = 10) {
  return request.get('/dashboard/top-purchase', { params: { limit } })
}

export function getInventoryWarningCount() {
  return request.get('/dashboard/warning-count')
}

export function getTodoList() {
  return request.get('/dashboard/todo-list')
}

export function getEmployeeRanking(month) {
  return request.get('/dashboard/employee-ranking', { params: { month } })
}

// ========== 个人工作台 ==========

export function getMyOverview() {
  return request.get('/dashboard/my/overview')
}

export function getMyPendingApprovals() {
  return request.get('/dashboard/my/pending-approvals')
}

export function getMyOverdueTasks() {
  return request.get('/dashboard/my/overdue')
}

export function getMyDrafts() {
  return request.get('/dashboard/my/drafts')
}

export function getMyArApFollowUp() {
  return request.get('/dashboard/my/ar-ap-followup')
}

// ========== 工作台审批 ==========

/** 单条审批 */
export function approveBill(type, id) {
  return request.post('/dashboard/my/approve', { type, id })
}

/** 单条审批驳回（2026-11-19：与通过对齐，供工作台卡片与居中弹窗直接驳回） */
export function rejectBill(type, id, comment) {
  return request.post('/dashboard/my/reject', { type, id, comment })
}

/** 单据摘要（2026-11-19 弹窗明细）：头信息+明细行，供弹窗展示「什么单据、什么内容」 */
export function getBillSummary(type, id) {
  return request.get('/dashboard/my/bill-summary', { params: { type, id } })
}

/** 当前用户是否为该单据的审批人（2026-11-19）：编辑页审核/驳回按钮可见性依据；无审批任务=true */
export function isBillApprover(type, id) {
  return request.get('/dashboard/my/is-bill-approver', { params: { type, id } })
}

/** 批量审批（一键审批） */
export function batchApproveBills(items) {
  return request.post('/dashboard/my/batch-approve', { items })
}
