// 设备管理 - 未关联数据 API
import request from '@/utils/request'

const BASE = '/device/unmatched'

export function pageUnmatched(params) { return request.get(`${BASE}/page`, { params }) }
export function claimUnmatched(id, operationRowId) { return request.post(`${BASE}/${id}/claim`, { operationRowId }) }
