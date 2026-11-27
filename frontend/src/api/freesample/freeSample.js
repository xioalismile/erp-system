import request from '@/utils/request'
const BASE = '/free-sample'

export function pageFreeSample(params) { return request.get(`${BASE}/page`, { params }) }
export function getFreeSample(id) { return request.get(`${BASE}/${id}`) }
export function saveFreeSample(data) { return request.post(BASE, data) }
export function updateFreeSample(id, data) { return request.put(`${BASE}/${id}`, data) }
export function deleteFreeSample(id) { return request.delete(`${BASE}/${id}`) }
export function submitFreeSample(id) { return request.post(`${BASE}/${id}/submit`) }
export function approveFreeSample(id) { return request.post(`${BASE}/${id}/approve`) }
export function shipFreeSample(id, expressNo, expressCompany) { return request.post(`${BASE}/${id}/ship`, { expressNo, expressCompany }) }
export function completeFreeSample(id) { return request.post(`${BASE}/${id}/complete`) }
export function generateFreeSampleNo() { return request.get(`${BASE}/generate-no`) }

// ===== 以下为状态流转统一接口（自动补齐） =====
export function rejectFreeSample(id, reason) { return request.post(`${BASE}/${id}/reject`, { reason }) }
export function reverseApproveFreeSample(id) { return request.post(`${BASE}/${id}/reverse-approve`) }
export function voidFreeSample(id, reason) { return request.post(`${BASE}/${id}/void`, { reason }) }
export function unvoidFreeSample(id) { return request.post(`${BASE}/${id}/unvoid`) }
export function closeOrderFreeSample(id) { return request.post(`${BASE}/${id}/close-order`) }
export function reopenFreeSample(id) { return request.post(`${BASE}/${id}/reopen`) }
