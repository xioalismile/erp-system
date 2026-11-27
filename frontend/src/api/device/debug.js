// 设备管理 - 接入调试台 API
import request from '@/utils/request'

const BASE = '/device/debug'

export function getDebugOverview(deviceId) { return request.get(`${BASE}/${deviceId}/overview`) }
export function listDebugMessages(deviceId, limit = 50) { return request.get(`${BASE}/${deviceId}/messages`, { params: { limit } }) }
export function listDebugValues(deviceId) { return request.get(`${BASE}/${deviceId}/values`) }
export function simulateReport(deviceId, body) { return request.post(`${BASE}/${deviceId}/simulate`, body, { headers: { 'Content-Type': 'application/json' } }) }

// 接入引擎：查看常驻连接与轮询任务数量；配置变更后立即重扫
export function getEngineStatus() { return request.get('/device/engine/status') }
export function reconcileEngine() { return request.post('/device/engine/reconcile') }
