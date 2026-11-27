/**
 * 工序状态口径（8 值）——与后端 WorkOperationStatus 一一对应。
 *
 * 用户口径（2026-09-23）：不同状态颜色必须不一样，看板与列表都按这里的映射着色，
 * 避免每页各写一套颜色导致"同一状态在两个页面颜色不同"。
 */
export const OP_STATUS = {
  PENDING_ASSIGN: '待派工',
  ASSIGNED: '已派工',
  STARTED: '已开工',
  RUNNING: '进行中',
  PAUSED: '暂停',
  WAIT_QC: '完工待检',
  FINISHED: '已完工',
  CANCELLED: '已取消'
}

/** 状态 → 展示样式：color 主色 / tag 组件类型 / dot 圆点类名 / progress 用于线路图与进度条 */
const STYLE = {
  待派工: { color: '#9ca3af', tag: 'info', dot: 'dot-info', progress: 0 },
  已派工: { color: '#3b82f6', tag: 'primary', dot: 'dot-primary', progress: 15 },
  已开工: { color: '#06b6d4', tag: 'primary', dot: 'dot-primary', progress: 35 },
  进行中: { color: '#10b981', tag: 'success', dot: 'dot-success', progress: 65 },
  暂停: { color: '#f59e0b', tag: 'warning', dot: 'dot-warning', progress: 45 },
  完工待检: { color: '#8b5cf6', tag: 'warning', dot: 'dot-warning', progress: 90 },
  已完工: { color: '#16a34a', tag: 'success', dot: 'dot-success', progress: 100 },
  已取消: { color: '#ef4444', tag: 'danger', dot: 'dot-danger', progress: 0 }
}

/** 取状态样式（未知状态按"待派工"兜底，保证老数据也能正常显示） */
export function opStatusStyle (status) {
  return STYLE[status] || STYLE[OP_STATUS.PENDING_ASSIGN]
}

/** 状态主色（看板线路图圆点/文字用） */
export function opStatusColor (status) {
  return opStatusStyle(status).color
}

/** 状态进度百分比（进度条用） */
export function opStatusProgress (status) {
  return opStatusStyle(status).progress
}

/** 是否终态（终态不再提供任何操作按钮） */
export function isOpTerminal (status) {
  return status === OP_STATUS.FINISHED || status === OP_STATUS.CANCELLED
}

/** 全部状态按流程顺序（筛选下拉/图例用） */
export const OP_STATUS_LIST = [
  OP_STATUS.PENDING_ASSIGN, OP_STATUS.ASSIGNED, OP_STATUS.STARTED, OP_STATUS.RUNNING,
  OP_STATUS.PAUSED, OP_STATUS.WAIT_QC, OP_STATUS.FINISHED, OP_STATUS.CANCELLED
]
