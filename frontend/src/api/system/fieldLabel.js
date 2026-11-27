import request from '@/utils/request'

/** 全站字段显示名覆盖映射：{ 原字段名: 显示名 }（登录后装载，翻译出口最高优先级消费） */
export function getFieldNameMap() {
  return request.get('/system/field-label/map')
}

/** 覆盖清单（管理页）：[{ field_key, display_name, updated_at, updated_by }] */
export function listFieldNames() {
  return request.get('/system/field-label/list')
}

/**
 * 批量保存：displayName 为空 = 该字段恢复默认
 * items: [{ fieldKey, displayName }]
 */
export function saveFieldNames(items) {
  return request.post('/system/field-label/save', { items })
}

/** 全部恢复默认（清空覆盖） */
export function resetFieldNames() {
  return request.post('/system/field-label/reset')
}
