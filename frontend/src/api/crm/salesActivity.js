// 销售过程 · 动作量化（2026-10-07）
// 后端：com.erp.crm.controller.SalesActivityController @RequestMapping("/api/crm/sales-activity")
import request from '@/utils/request'

const BASE = '/crm/sales-activity'

/** 每日任务 + 本周任务 + 今日任务清单 */
export function getDaily(params) {
  return request.get(`${BASE}/daily`, { params })
}

/** 过程奖金核算（月度） */
export function getBonus(params) {
  return request.get(`${BASE}/bonus`, { params })
}
