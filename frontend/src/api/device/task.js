// 设备管理 - 设备任务 API
import request from '@/utils/request'

const BASE = '/device/task'

export function pageTasks(params) { return request.get(`${BASE}/page`, { params }) }
export function getTask(id) { return request.get(`${BASE}/${id}`) }
export function operationOptions(keyword) { return request.get(`${BASE}/operation-options`, { params: { keyword } }) }
export function createTask(data) { return request.post(BASE, data) }
export function dispatchTask(id) { return request.post(`${BASE}/${id}/dispatch`) }
export function rebindTask(id, operationRowId) { return request({ url: `${BASE}/${id}/rebind`, method: 'post', params: { operationRowId } }) }
export function releaseTask(id, reason) { return request({ url: `${BASE}/${id}/release`, method: 'post', params: { reason } }) }
