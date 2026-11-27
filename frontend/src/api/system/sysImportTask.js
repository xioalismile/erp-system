// 导入任务 API
import request from '@/utils/request'

const BASE = '/system/log'

export function listImportTask() {
  return request.get(`${BASE}/importTasks`)
}
