// 质检模板 API（基础资料 → 质检模板）
// 模板 = 模板头（名称/适用物料/适用工序/质检类型/抽样比例/允许不良率）+ 检验项列表（items）
import request from '@/utils/request'

const BASE = '/qc/templates'

/** 分页查询模板：{ current, size, keyword, qcType, isEnabled } */
export function listQcTemplates(params) {
  return request({ url: BASE, method: 'get', params })
}

/** 启用中的模板全量列表（工单编辑页/工序面板选择用） */
export function listAllQcTemplates() {
  return request({ url: `${BASE}/list`, method: 'get' })
}

/** 模板详情（含检验项明细 items） */
export function getQcTemplate(id) {
  return request({ url: `${BASE}/${id}`, method: 'get' })
}

/** 新增模板（编码留空自动生成） */
export function createQcTemplate(data) {
  return request({ url: BASE, method: 'post', data })
}

/** 编辑模板（检验项整单覆盖） */
export function updateQcTemplate(id, data) {
  return request({ url: `${BASE}/${id}`, method: 'put', data })
}

/** 删除模板（级联删除检验项明细） */
export function deleteQcTemplate(id) {
  return request({ url: `${BASE}/${id}`, method: 'delete' })
}
