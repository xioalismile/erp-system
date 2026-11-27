// 设备管理 - 设备台账 API
import request from '@/utils/request'

const BASE = '/device/ledger'

export function listDevices(params) { return request.get(BASE, { params }) }
export function listEnabledDevices() { return request.get(`${BASE}/enabled`) }
export function getDevice(id) { return request.get(`${BASE}/${id}`) }
export function createDevice(data) { return request.post(BASE, data) }
export function updateDevice(id, data) { return request.put(`${BASE}/${id}`, data) }
export function deleteDevice(id) { return request.delete(`${BASE}/${id}`) }
export function enableDevice(id) { return request.post(`${BASE}/${id}/enable`) }
export function disableDevice(id) { return request.post(`${BASE}/${id}/disable`) }
export function scrapDevice(id) { return request.post(`${BASE}/${id}/scrap`) }
export function getDeviceQrcode(id) { return request.get(`${BASE}/${id}/qrcode-base64`) }
