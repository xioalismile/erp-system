import request from '@/utils/request'

/**
 * 系统通知 API
 * 对应后端 SysNotificationController (/api/notifications)
 */

/** 分页查询通知列表 */
export function getNotifications(params) {
  return request.get('/notifications/page', { params })
}

/** 未读数量统计 (返回 { total, byType: { approval, overdue, soon_overdue, draft, ar_ap } }) */
export function getUnreadCount() {
  return request.get('/notifications/unread-count')
}

/** 标记单条已读 (点击通知时调用) */
export function markRead(id) {
  return request.post(`/notifications/${id}/read`)
}

/** 标记全部已读 */
export function markAllRead() {
  return request.post('/notifications/read-all')
}

/** 批量标记已读 */
export function batchMarkRead(ids) {
  return request.post('/notifications/batch-read', { ids })
}

/** 删除单条 */
export function deleteNotification(id) {
  return request.delete(`/notifications/${id}`)
}

/** 清空已读通知 */
export function clearReadNotifications() {
  return request.post('/notifications/clear-read')
}
