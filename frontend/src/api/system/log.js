// 操作日志 API
import request from '@/utils/request'

const BASE = '/system/log'

export function pageLog(params) {
  return request.get(`${BASE}/page`, { params })
}
