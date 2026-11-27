/**
 * 单据状态映射公共函数（从各销售列表/编辑页逐字迁移）
 * 状态枚举：草稿/待审核/已生效/已关闭/已取消
 */

/** 状态 → el-tag type */
export function statusType(s) {
  return { '草稿': 'info', '待审核': 'warning', '已生效': 'success', '已关闭': 'info', '已取消': 'danger' }[s] || 'info'
}

/** 状态 → 状态点样式类 */
export function statusDotClass(s) {
  return { '草稿': 'dot-info', '待审核': 'dot-warning', '已生效': 'dot-success', '已关闭': 'dot-info', '已取消': 'dot-danger' }[s] || 'dot-info'
}

/** 审批状态 → el-tag type */
export function approvalStatusType(s) {
  return { '待审核': 'warning', '已生效': 'success', '已取消': 'danger' }[s] || 'info'
}
