export const BILL_STATUS = Object.freeze({
  // ===== 草稿族 (code=0) =====
  DRAFT: '草稿',
  REJECTED: '已驳回',
  CANCELED: '已取消',
  // ===== 待审核 (code=1) =====
  PENDING: '待审核',
  // ===== 已生效族 (code=2) =====
  APPROVED: '已生效',
  CONFIRMED: '已确认',
  ISSUED: '已下达',
  IN_PROGRESS: '执行中',
  COMPLETED: '已完工',
  PARTIAL_INBOUND: '部分入库',
  FULL_INBOUND: '全部入库',
  CLOSED: '已关闭',
  // ===== 已作废 (code=7) =====
  VOIDED: '已作废'
})

export const BILL_STATUS_CODE = Object.freeze({
  DRAFT: 0,
  PENDING: 1,
  APPROVED: 2,
  VOIDED: 7
})

export const APPROVED_CLASS_SET = new Set([
  BILL_STATUS.APPROVED, BILL_STATUS.CONFIRMED, BILL_STATUS.ISSUED,
  BILL_STATUS.IN_PROGRESS, BILL_STATUS.COMPLETED,
  BILL_STATUS.PARTIAL_INBOUND, BILL_STATUS.FULL_INBOUND, BILL_STATUS.CLOSED,
  '已审核', // 历史别名（2026-09-19 终态改名前的存量数据/旧接口回包），勿删
  // 售后维修单（SALE_REPAIR）业务态：提交即进入「已受理」，之后是维修中/维修完成/已返还。
  // 后端 BillStatusUtils 对未登记的中文态统一按「已生效类」处理（可作废/可重置草稿），
  // 前端若不登记，这三个按钮会被状态机判成不可用而消失，单据提交后无法撤销。
  '已受理', '维修中', '维修完成', '已返还'
])
export const DRAFT_CLASS_SET = new Set([
  BILL_STATUS.DRAFT, BILL_STATUS.REJECTED, BILL_STATUS.CANCELED
])
export const PENDING_CLASS_SET = new Set([
  BILL_STATUS.PENDING, '待审批', '审批中'
])
export const VOIDED_CLASS_SET = new Set([BILL_STATUS.VOIDED, '作废', '已废弃'])

export const CLOSE_STATUS = Object.freeze({
  OPEN: 0,
  CLOSED: 1,
  CLOSED_LABEL: '已结案'
})

export const FINALIZED_STATUS = new Set([...VOIDED_CLASS_SET])

export function normalizeStatus (status) {
  if (status == null || status === '') return BILL_STATUS.DRAFT
  if (typeof status === 'number') {
    switch (status) {
      case 0: return BILL_STATUS.DRAFT
      case 1: return BILL_STATUS.PENDING
      case 2: return BILL_STATUS.APPROVED
      case 3: return BILL_STATUS.ISSUED
      case 4: return BILL_STATUS.IN_PROGRESS
      case 5: return BILL_STATUS.COMPLETED
      case 6: return CLOSE_STATUS.CLOSED_LABEL
      case 7: return BILL_STATUS.VOIDED
      default: return String(status)
    }
  }
  return String(status)
}

export function isDraft (status) {
  const s = normalizeStatus(status)
  return !s || DRAFT_CLASS_SET.has(s)
}
export function isPending (status) { return PENDING_CLASS_SET.has(normalizeStatus(status)) }
export function isApproved (status) { return APPROVED_CLASS_SET.has(normalizeStatus(status)) }
export function isRejected (status) { return normalizeStatus(status) === BILL_STATUS.REJECTED }
export function isVoided (status) { return VOIDED_CLASS_SET.has(normalizeStatus(status)) }
export function isCanceled (status) { return normalizeStatus(status) === BILL_STATUS.CANCELED }

export function isClosedStatus (status) {
  const s = normalizeStatus(status)
  return s === BILL_STATUS.CLOSED || s === CLOSE_STATUS.CLOSED_LABEL
}
export function isClosed (closeStatus) { return Number(closeStatus) === CLOSE_STATUS.CLOSED }

export function isFinalized (status, closeStatus) {
  if (isClosed(closeStatus)) return true
  return FINALIZED_STATUS.has(normalizeStatus(status))
}

export function canEdit (status, closeStatus) {
  if (isFinalized(status, closeStatus)) return false
  if (isApproved(status)) return false
  if (isPending(status)) return false
  // 已取消是终态：不允许再编辑（历史口径把它并入草稿族，导致取消后字段仍可输入）
  if (isCanceled(status)) return false
  return true
}
export function canSave (status, closeStatus) {
  if (isFinalized(status, closeStatus)) return false
  const s = normalizeStatus(status)
  return s === BILL_STATUS.DRAFT || s === BILL_STATUS.REJECTED
}
export function canSubmit (status, closeStatus) {
  if (isFinalized(status, closeStatus)) return false
  return DRAFT_CLASS_SET.has(normalizeStatus(status))
}
export function canApprove (status, closeStatus) {
  if (isFinalized(status, closeStatus)) return false
  const s = normalizeStatus(status)
  return isPending(status) || s === BILL_STATUS.DRAFT
}
export function canReject (status, closeStatus) {
  if (isFinalized(status, closeStatus)) return false
  return isPending(status)
}
export function canReverseApprove (status, closeStatus) {
  if (isFinalized(status, closeStatus)) return false
  if (isClosed(closeStatus)) return false
  return isApproved(status)
}
export function canClose (status, closeStatus) {
  if (isClosed(closeStatus)) return false
  return isApproved(status)
}
export function canReopen (_status, closeStatus) { return isClosed(closeStatus) }
export function canVoid (status, closeStatus) {
  if (isFinalized(status, closeStatus)) return false
  if (isVoided(status)) return false
  return isDraft(status) || isPending(status) || isApproved(status)
}
export function canUnvoid (status) { return isVoided(status) }
export function canDelete (status, closeStatus) {
  if (isFinalized(status, closeStatus)) return false
  return DRAFT_CLASS_SET.has(normalizeStatus(status))
}

// 下推(单据转换)允许的状态：属于"已生效类"且未结案
// 覆盖新增别名：已生效/已确认/已下达/执行中/已完工/部分入库/全部入库/已关闭等
export function canPushDown (status, closeStatus) {
  if (Number(closeStatus) === CLOSE_STATUS.CLOSED) return false
  return APPROVED_CLASS_SET.has(normalizeStatus(status))
}

export function statusTagType (status, closeStatus) {
  if (isClosed(closeStatus)) return 'info'
  const s = normalizeStatus(status)
  if (VOIDED_CLASS_SET.has(s)) return 'info'
  if (DRAFT_CLASS_SET.has(s)) return 'info'
  if (PENDING_CLASS_SET.has(s)) return 'warning'
  if (APPROVED_CLASS_SET.has(s)) return 'success'
  if (s === BILL_STATUS.REJECTED) return 'danger'
  if (s === CLOSE_STATUS.CLOSED_LABEL) return 'info'
  return ''
}

export function buttonPermit (action, status, closeStatus) {
  switch (action) {
    case 'edit': return canEdit(status, closeStatus)
    case 'save': return canSave(status, closeStatus)
    case 'submit': return canSubmit(status, closeStatus)
    case 'approve': return canApprove(status, closeStatus)
    case 'reject': return canReject(status, closeStatus)
    case 'reverseApprove': return canReverseApprove(status, closeStatus)
    case 'close': return canClose(status, closeStatus)
    case 'reopen': return canReopen(status, closeStatus)
    case 'void': return canVoid(status, closeStatus)
    case 'unvoid': return canUnvoid(status)
    case 'delete': return canDelete(status, closeStatus)
    default: return true
  }
}
