// 物料辅计量换算 API（2026-11-10 多单位换算重构第一阶段）
import request from '@/utils/request'

const BASE = '/basedata/material'

/** 按物料查辅计量换算明细列表（物料档案编辑页「辅计量换算」页签回显） */
export function listMaterialUnitConversions(materialId) {
  return request.get(`${BASE}/${materialId}/unit-conversions`)
}

/** 按物料整表保存辅计量换算明细（后端事务内先删后插；行单位去重、换算率>0 校验） */
export function saveMaterialUnitConversions(materialId, rows) {
  return request.post(`${BASE}/${materialId}/unit-conversions`, rows || [])
}

/**
 * 按多个物料批量查辅计量换算明细（单据编辑页整单一次性取回，避免逐物料请求）
 * @param {Array<number>} materialIds
 * @returns {Promise<Object>} 按物料ID分组：{ [materialId]: 换算行[] }（无配置的物料也返回空数组条目）
 */
export function listMaterialUnitConversionsBatch(materialIds) {
  const ids = [...new Set((materialIds || []).filter(id => id != null))]
  if (!ids.length) return Promise.resolve({})
  return request.get(`${BASE}/unit-conversions-batch`, { params: { ids: ids.join(',') } })
}
