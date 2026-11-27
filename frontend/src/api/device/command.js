// 设备管理 - 双向下发 API（任务指令 / 参数写入）
import request from '@/utils/request'

export function dispatchTaskCommand(taskId) { return request.post(`/device/command/task/${taskId}/dispatch`) }
export function listWritablePoints(deviceId) { return request.get(`/device/command/device/${deviceId}/writable-points`) }
export function writeDeviceParams(deviceId, params) { return request.post(`/device/command/device/${deviceId}/params`, { params }) }
