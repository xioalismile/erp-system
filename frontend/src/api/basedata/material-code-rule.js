import request from '@/utils/request'
const BASE = '/basedata/material-code-rule'

export function listCodeRule() { return request.get(`${BASE}/list`) }
export function saveCodeRule(data) { return request.post(BASE, data) }
export function deleteCodeRule(id) { return request.delete(`${BASE}/${id}`) }
export function generateMaterialCode(categoryId) { return request.get(`${BASE}/generate-code`, { params: { categoryId } }) }
