// 全局搜索（万能搜索框）API
import request from '@/utils/request'

/**
 * 按单号搜单据（后端复用全站单据注册表，覆盖全部登记单据类型，自带数据权限过滤）。
 * 返回数组：[{ docType, name, id, no, date, status }]
 */
export function searchBills(keyword) {
  return request.get('/common/global-search/bills', { params: { keyword: keyword || '' } })
}
