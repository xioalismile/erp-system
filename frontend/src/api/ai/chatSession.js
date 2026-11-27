import request from '@/utils/request'

const BASE = '/ai/chat/sessions'

/**
 * 小楷 · 历史会话 API 封装
 * 后端对应：com.erp.ai.controller.AiChatSessionController
 * 口径：只返回当前登录人自己的会话，删除也只对本人生效。
 */

// 我的历史会话列表（按最近对话时间倒序）
export function listChatSessions() {
  return request.get(BASE)
}

// 某个会话的全部消息
export function getChatSessionMessages(sessionId) {
  return request.get(`${BASE}/${encodeURIComponent(sessionId)}/messages`)
}

// 删除某个会话
export function deleteChatSession(sessionId) {
  return request.delete(`${BASE}/${encodeURIComponent(sessionId)}`)
}
