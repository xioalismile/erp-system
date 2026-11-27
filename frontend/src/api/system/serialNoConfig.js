import request from '@/utils/request'

const BASE = '/serial-no-config'

export function getSerialNoConfigList() {
  return request.get(BASE)
}

export function getSerialNoConfig(id) {
  return request.get(`${BASE}/${id}`)
}

export function addSerialNoConfig(data) {
  return request.post(BASE, data)
}

export function updateSerialNoConfig(data) {
  return request.put(BASE, data)
}

export function deleteSerialNoConfig(id) {
  return request.delete(`${BASE}/${id}`)
}

export function generateSerialNo(docType) {
  return request.get(`${BASE}/generate/${docType}`)
}

export function batchGenerateSerialNo(docTypes) {
  return request.post(`${BASE}/batch-generate`, { docTypes })
}

export function getSerialNoConfigByDocType(docType) {
  return request.get(`${BASE}/by-doc-type/${docType}`)
}

export function isAutoGenerate(docType) {
  return request.get(`${BASE}/is-auto-generate/${docType}`)
}
