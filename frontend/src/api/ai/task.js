import request from '@/utils/request'

const BASE = '/ai/task'

/**
 * 小楷 · 多步骤任务 API 封装
 * 后端对应：com.erp.ai.controller.AiTaskController
 * 口径：只允许任务发起人查看/重跑/撤销。
 */

// 任务详情（任务卡刷新/回看）
export function getAiTask(taskId) {
  return request.get(`${BASE}/${taskId}`)
}

// 某个会话下最近的任务（刷新后恢复任务卡）
export function listAiTasksBySession(sessionId) {
  return request.get(`${BASE}/by-session/${encodeURIComponent(sessionId)}`)
}

// 只重跑失败/未执行的步骤
export function retryAiTaskFailed(taskId) {
  return request.post(`${BASE}/${taskId}/retry-failed`)
}

// 撤销本次新建（仅未被引用的记录）
export function undoAiTask(taskId) {
  return request.post(`${BASE}/${taskId}/undo`)
}
