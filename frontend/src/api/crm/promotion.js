// 推广记录（朋友圈 / 产品图 / 车间视频 / RFID 素材）· 2026-10-07
// 后端：com.erp.crm.controller.PromotionRecordController @RequestMapping("/api/crm/promotion")
import request from '@/utils/request'

const BASE = '/crm/promotion'

export function listPromotion(params) {
  return request.get(`${BASE}/page`, { params })
}

export function getPromotion(id) {
  return request.get(`${BASE}/${id}`)
}

export function savePromotion(data) {
  return request.post(BASE, data)
}

export function updatePromotion(id, data) {
  return request.put(`${BASE}/${id}`, data)
}

export function deletePromotion(id) {
  return request.delete(`${BASE}/${id}`)
}

/** 本周推广统计（推广次数 / 覆盖客户数 / RFID 覆盖率 / 周目标） */
export function getWeekStats(params) {
  return request.get(`${BASE}/week-stats`, { params })
}
