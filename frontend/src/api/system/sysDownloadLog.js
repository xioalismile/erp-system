// 下载日志 API
import request from '@/utils/request'

const BASE = '/system/log'

export function pageDownloadLog(params) {
  return request.get(`${BASE}/downloadPage`, { params })
}

export function clearDownloadLog() {
  return request.delete(`${BASE}/download`)
}
