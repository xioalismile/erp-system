// 设备管理 - 数据点 API
import request from '@/utils/request'

const BASE = '/device/point'

export function listPoints(deviceId) { return request.get(`${BASE}/${deviceId}`) }
export function createPoint(data) { return request.post(BASE, data) }
export function updatePoint(id, data) { return request.put(`${BASE}/${id}`, data) }
export function deletePoint(id) { return request.delete(`${BASE}/${id}`) }
