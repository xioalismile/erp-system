// 操作日志 API
import request from '@/utils/request'

const BASE = '/system/log'

export function pageLog(params) {
  return request.get(`${BASE}/page`, { params })
}

/** 单条日志的字段级变更明细（字段/修改前/修改后） */
export function getLogDetail(logId) {
  return request.get(`${BASE}/detail/${logId}`)
}
