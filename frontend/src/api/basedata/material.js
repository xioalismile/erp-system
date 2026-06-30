// 物料 API
import request from '@/utils/request'

const BASE = '/basedata/material'

export function listMaterial(params) { return request.get(`${BASE}/page`, { params }) }
export function listAllMaterial(params) { return request.get(`${BASE}/list`, { params }) }
export function getMaterial(id) { return request.get(`${BASE}/${id}`) }
export function saveMaterial(data) { return request.post(BASE, data) }
export function updateMaterial(data) { return request.put(BASE, data) }
export function deleteMaterial(id) { return request.delete(`${BASE}/${id}`) }
export function batchDeleteMaterial(ids) { return request.post(`${BASE}/batch-delete`, ids) }
