// 物料 API
import request from '@/utils/request'

const BASE = '/basedata/material'

export function listMaterial(params) { return request.get(`${BASE}/page`, { params }) }
export function pageMaterial(params) { return request.get(`${BASE}/page`, { params }) }
export function listAllMaterial(params) { return request.get(`${BASE}/list`, { params }) }
export function getMaterial(id) { return request.get(`${BASE}/${id}`) }
export function saveMaterial(data) { return request.post(BASE, data) }
export function updateMaterial(data) { return request.put(BASE, data) }
export function deleteMaterial(id) { return request.delete(`${BASE}/${id}`) }
export function batchDeleteMaterial(ids) { return request.post(`${BASE}/batch-delete`, ids) }
/**
 * 批量删除预检（只看不删）：返回 { deletable: [id], blocked: [{ id, reason }] }
 * 供"批量删除确认"弹窗展示"哪些可删、哪些不可删（含原因）"，由用户自己决定是否继续。
 */
export function checkBatchDeleteMaterial(ids) { return request.post(`${BASE}/batch-delete-check`, ids) }
// 预检物料名称是否重复（是否生效取决于设置中心「货品查重字段」是否勾选「物料名称」）
export function checkMaterialName(materialName, excludeId) { 
  return request.get(`${BASE}/check-name`, { params: { materialName, ...(excludeId ? { excludeId } : {}) } }) 
}
// 设置中心「货品查重字段」可选字段清单（物料系统字段 + 全部已启用自定义字段）
export function getMaterialDedupFieldOptions() {
  return request.get(`${BASE}/dedup-field-options`)
}

