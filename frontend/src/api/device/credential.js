// 设备管理 - 接入凭证 API
import request from '@/utils/request'

const BASE = '/device/credential'

export function pageCredentials(params) { return request.get(BASE, { params }) }
export function getCurrentCredential(deviceId) { return request.get(`${BASE}/current`, { params: { deviceId } }) }
export function generateCredential(deviceId) { return request.post(`${BASE}/${deviceId}/generate`) }
export function rotateCredential(id) { return request.post(`${BASE}/${id}/rotate`) }
export function revokeCredential(id) { return request.post(`${BASE}/${id}/revoke`) }
