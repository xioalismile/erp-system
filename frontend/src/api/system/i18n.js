import request from '@/utils/request'

/** 语言包（匿名接口，登录页可用）：返回 { key: text } */
export function getI18nPackage(lang) {
  return request.get(`/system/i18n/package/${lang}`)
}

/** 分页查询译文（管理员） */
export function pageI18n(params) {
  return request.get('/system/i18n/page', { params })
}

/** 批量保存译文（管理员） */
export function saveI18n(rows) {
  return request.post('/system/i18n/save', rows)
}
