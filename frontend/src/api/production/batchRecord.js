// 化妆品批记录 API（生产管理 → 批记录中心）
// 覆盖：批次总览 / 留样台账 / 追溯树 / 批生产记录聚合 / 环境记录 / 记录更正
//         批次检验（建检/提交签名/复核签名）/ 放行单（状态机）/ 出库放行预检
import request from '@/utils/request'

// ---------------- 批记录中心 ----------------

/** 批次总览分页：{ current, size, keyword, qcStatus, materialId, dateFrom, dateTo } */
export function listBatchRecords(params) {
  return request({ url: '/production/batch-record-center/list', method: 'get', params })
}

/** 留样台账分页：{ current, size, keyword, remindStatus } */
export function listSampleRetentions(params) {
  return request({ url: '/production/batch-record-center/samples', method: 'get', params })
}

/** 批次状态统计（页头统计卡） */
export function getBatchStatusSummary() {
  return request({ url: '/production/batch-record-center/status-summary', method: 'get' })
}

/** 追溯树：direction = up / down / both */
export function getBatchTrace(batchId, direction = 'both') {
  return request({ url: `/production/batch-record-center/trace/${batchId}`, method: 'get', params: { direction } })
}

// ---------------- 批生产记录 ----------------

/** 批生产记录全量聚合（工单详情页签） */
export function getBatchRecordByWorkOrder(workOrderId) {
  return request({ url: `/production/batch-records/by-work-order/${workOrderId}`, method: 'get' })
}

/** 补偿生成批生产记录头 */
export function ensureBatchRecord(workOrderId) {
  return request({ url: `/production/batch-records/ensure/${workOrderId}`, method: 'post' })
}

/** 环境记录（温湿度）保存 */
export function saveBatchRecordEnv(workOrderId, data) {
  return request({ url: `/production/batch-records/${workOrderId}/env`, method: 'post', data })
}

/** 新增记录更正（含电子签名）：{ targetTable, targetId, targetField, newValue, oldValue, reason, password } */
export function addRecordCorrection(data) {
  return request({ url: '/production/batch-records/corrections', method: 'post', data })
}

/** 某条记录的更正链 */
export function listRecordCorrections(targetTable, targetId) {
  return request({ url: '/production/batch-records/corrections', method: 'get', params: { targetTable, targetId } })
}

/** 某目标的电子签名留痕（含手写签名图） */
export function listSignatures(targetTable, targetId) {
  return request({ url: '/production/batch-records/signatures', method: 'get', params: { targetTable, targetId } })
}

// ---------------- 批次检验 ----------------

/** 按批次建检：qcStage = 来料检 / 成品检 */
export function createBatchInspection(batchId, qcStage) {
  return request({
    url: '/qc/batch/create',
    method: 'post',
    params: { batchId, qcStage }
  })
}

/** 检验记录 + 检项明细（录入弹窗回显） */
export function getBatchInspection(recordId) {
  return request({ url: `/qc/batch/${recordId}`, method: 'get' })
}

/** 提交检验（手写签名）：payload = { result, remark, signImage, items: [{itemId, measuredValue, result, remark}] } */
export function submitBatchInspection(recordId, payload) {
  return request({
    url: `/qc/batch/${recordId}/submit`,
    method: 'post',
    data: payload
  })
}

/** 检验复核（手写签名）：signImage = 手写签名图（PNG DataURL） */
export function reviewBatchInspection(recordId, signImage) {
  return request({ url: `/qc/batch/${recordId}/review`, method: 'post', data: { signImage } })
}

/** 某批次最近一条检验记录（批记录中心「查看检验」） */
export function getBatchInspectionByBatch(batchId) {
  return request({ url: `/qc/batch/by-batch/${batchId}`, method: 'get' })
}

// ---------------- 放行单 ----------------

/** 从批次一键生成放行单草稿 */
export function createReleaseFromBatch(batchId) {
  return request({ url: `/production/release-orders/create-from-batch/${batchId}`, method: 'post' })
}

/** 放行单详情 */
export function getReleaseOrder(id) {
  return request({ url: `/production/release-orders/${id}`, method: 'get' })
}

/** 提交（审批开=待审核；关=直接生效并回写批次+自动留样） */
export function submitReleaseOrder(id) {
  return request({ url: `/production/release-orders/${id}/submit`, method: 'post' })
}

/** 审核生效 */
export function approveReleaseOrder(id) {
  return request({ url: `/production/release-orders/${id}/approve`, method: 'post' })
}

/** 重置草稿（批次回退合格） */
export function reverseReleaseOrder(id) {
  return request({ url: `/production/release-orders/${id}/reverse-approve`, method: 'post' })
}

/** 作废（批次回退合格） */
export function voidReleaseOrder(id, reason) {
  return request({ url: `/production/release-orders/${id}/void`, method: 'post', data: { reason } })
}

// ---------------- 出库放行预检 ----------------

/** 当前未放行出库控制档位：允许 / 预警提示 / 不允许 */
export function getReleaseOutboundMode() {
  return request({ url: '/inventory/batch-release/mode', method: 'get' })
}

/** 出库前预检：items = [{ materialId, batchNo, lineLabel }] → { mode, warnings } */
export function checkReleaseOutbound(items) {
  return request({ url: '/inventory/batch-release/check', method: 'post', data: { items } })
}
