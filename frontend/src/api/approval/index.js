// 审批引擎 API（单据审批进度 / 审批历史 / 流程定义）
// 后端：com.erp.approval.controller.ApprovalController (/api/approval)
import request from '@/utils/request'

const BASE = '/approval'

/**
 * 某单据的审批任务（节点级进度：node_order / task_status / current_approver / assignee_ids / comment / submit_time / finish_time）
 * @param {string} billType 单据审批编码（与设置中心「单据审批开关」一致，如 SALE_ORDER）
 * @param {number} billId 单据主键
 */
export function getBillTasks (billType, billId) {
  return request.get(`${BASE}/tasks/bill/${billType}/${billId}`)
}

/**
 * 某单据的审批历史（node_name / approver / action / comment / action_time）
 */
export function getBillApprovalLogs (billType, billId) {
  return request.get(`${BASE}/logs/bill/${billType}/${billId}`)
}

/**
 * 审批流程详情（含节点定义与候选审批人）
 */
export function getApprovalFlow (flowId) {
  return request.get(`${BASE}/flows/${flowId}`)
}
