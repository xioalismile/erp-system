import request from '@/utils/request'

/**
 * SPC 质量看板（数据看板 → SPC质量看板）
 * 全部 SPC 标准公式（控制限 / Cp / Cpk / 八条判异）由服务端计算，前端只呈现。
 */

/** 检验项候选（仅数值型实测值可入 SPC） */
export function getSpcItems(params) {
  return request({ url: '/spc/items', method: 'get', params })
}

/** 全量分析：KPI + X̄-R / I-MR / P 图 + Cp/Cpk + 判异明细 + 实测值序列 */
export function getSpcAnalysis(params) {
  return request({ url: '/spc/analysis', method: 'get', params })
}
