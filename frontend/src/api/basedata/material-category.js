// 物料分类 API
import request from '@/utils/request'

const BASE = '/basedata/material-category'

export function listMaterialCategory(params) { return request.get(`${BASE}/page`, { params }) }
export function listAllMaterialCategory(params) { return request.get(`${BASE}/list`, { params }) }
export function getMaterialCategory(id) { return request.get(`${BASE}/${id}`) }
export function saveMaterialCategory(data) { return request.post(BASE, data) }
export function updateMaterialCategory(data) { return request.put(BASE, data) }
export function deleteMaterialCategory(id) { return request.delete(`${BASE}/${id}`) }
export function batchDeleteMaterialCategory(ids) { return request.post(`${BASE}/batch-delete`, ids) }
export function treeMaterialCategory(params) { return request.get(`${BASE}/tree`, { params }) }
