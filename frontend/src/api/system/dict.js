// 数据字典 API
import request from '@/utils/request'

const TYPE_BASE = '/system/dict/type'
const DATA_BASE = '/system/dict/data'

// ===== 字典类型 =====
export function pageDictType(params) {
  return request.get(`${TYPE_BASE}/page`, { params })
}
export function getDictType(id) {
  return request.get(`${TYPE_BASE}/${id}`)
}
export function createDictType(data) {
  return request.post(TYPE_BASE, data)
}
export function updateDictType(id, data) {
  return request.put(`${TYPE_BASE}/${id}`, data)
}
export function deleteDictType(id) {
  return request.delete(`${TYPE_BASE}/${id}`)
}

// ===== 字典数据 =====
export function listDictData(typeId) {
  return request.get(`${DATA_BASE}/${typeId}`)
}
export function createDictData(data) {
  return request.post(DATA_BASE, data)
}
export function updateDictData(id, data) {
  return request.put(`${DATA_BASE}/${id}`, data)
}
export function deleteDictData(id) {
  return request.delete(`${DATA_BASE}/${id}`)
}
