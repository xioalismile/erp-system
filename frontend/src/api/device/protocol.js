// 设备管理 - 协议模板 API
import request from '@/utils/request'

const BASE = '/device/protocol'

export function pageProtocols(params) { return request.get(`${BASE}/page`, { params }) }
export function listProtocols() { return request.get(`${BASE}/list`) }
export function createProtocol(data) { return request.post(BASE, data) }
export function updateProtocol(id, data) { return request.put(`${BASE}/${id}`, data) }
export function copyProtocol(id) { return request.post(`${BASE}/${id}/copy`) }
export function deleteProtocol(id) { return request.delete(`${BASE}/${id}`) }
