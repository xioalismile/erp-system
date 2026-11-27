// 设备管理 - 接入通道 API
import request from '@/utils/request'

const BASE = '/device/channel'

export function listChannels(deviceId) { return request.get(`${BASE}/${deviceId}`) }
export function saveChannel(data) { return request.post(BASE, data) }
export function updateChannel(id, data) { return request.put(`${BASE}/${id}`, data) }
export function toggleChannel(id, enabled) { return request({ url: `${BASE}/${id}/toggle`, method: 'post', params: { enabled } }) }
export function deleteChannel(id) { return request.delete(`${BASE}/${id}`) }
