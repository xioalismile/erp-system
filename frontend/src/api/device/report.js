// 设备管理 - 待确认报工 API
import request from '@/utils/request'

const BASE = '/device/report-pending'

export function pageReportPending(params) { return request.get(`${BASE}/page`, { params }) }
export function confirmReportPending(id) { return request.post(`${BASE}/${id}/confirm`) }
export function rejectReportPending(id, reason) { return request.post(`${BASE}/${id}/reject`, { reason }) }
