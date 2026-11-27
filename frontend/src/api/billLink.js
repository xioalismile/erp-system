// 单据关联 API（来源单据 / 去向单据）—— 通用，配置驱动
// 后端：com.erp.common.bill.link.BillLinkController (/api/bill-link)
import request from '@/utils/request'

const BASE = '/bill-link'

/** 该单据类型是否已登记来源/去向配置（未登记则不渲染来源/去向页签） */
export function isBillLinkRegistered (billType) {
  return request.get(`${BASE}/registered`, { params: { billType } })
}

/** 来源单据（表头 + 明细：下推数量/下推单价/累计已下推/未下推） */
export function getBillSources (billType, billId) {
  return request.get(`${BASE}/sources`, { params: { billType, billId } })
}

/** 去向单据（一跳直接下游：类型/单号/id/日期/状态/金额） */
export function getBillTargets (billType, billId) {
  return request.get(`${BASE}/targets`, { params: { billType, billId } })
}

/**
 * 批量下推状态汇总（列表页状态列，2026-10-10 新增）
 * 入参 ids：本页单据主键数组；返回 { "<主键>": { delivery/invoice/production: { state, billCount, ... } } }
 * state：NONE=无有效下游（未发货）/ PARTIAL=进行中（发货中）/ FULL=全部完成（发货完成）
 */
export function getBillPushStatus (billType, ids) {
  return request.get(`${BASE}/push-status`, { params: { billType, ids: (ids || []).join(',') } })
}
