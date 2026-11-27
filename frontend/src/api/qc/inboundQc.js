// 入库单质检 API（采购入库 / 生产入库 / 委外入库内嵌质检）
// 一张入库单一条质检记录，明细按入库明细行一一对应
import request from '@/utils/request'

const BASE = '/qc/inbound'

/** 质检上下文：可用单据级模板、已保存质检记录、入库明细行、默认报废仓 */
export function getInboundQcContext(params) {
  return request({ url: `${BASE}/context`, method: 'get', params })
}

/** 保存质检（回写入库单合格/不合格数量与应付金额） */
export function saveInboundQc(data) {
  return request({ url: `${BASE}/save`, method: 'post', data })
}
