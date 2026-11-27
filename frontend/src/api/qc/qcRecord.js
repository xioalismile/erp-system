// 生产工序质检报表 API（生产管理 → 工序质检报表）
// 数据源 = 工序检验记录（qc_record）：分页列表 + 汇总（与「经营看板 → 质量」块同口径）
import request from '@/utils/request'

const BASE = '/qc/record'

/**
 * 分页查询工序检验记录
 * { current, size, dateFrom, dateTo, qcType, result, orderNo, operationName, inspectorName, keyword }
 */
export function pageQcRecords(params) {
  return request({ url: `${BASE}/page`, method: 'get', params })
}

/** 汇总（与列表同一套筛选条件）：检验批次 / 合格批次 / 不合格批次 / 合格率 / 检验总数 / 合格数 / 不合格数 */
export function summaryQcRecords(params) {
  return request({ url: `${BASE}/summary`, method: 'get', params })
}

/** 【第 19 期】某条检验记录的实测明细：逐项实测值 / 单项判定 / 留证图片（逗号分隔 URL） */
export function getQcRecordItems(recordId) {
  return request({ url: `${BASE}/${recordId}/items`, method: 'get' })
}
