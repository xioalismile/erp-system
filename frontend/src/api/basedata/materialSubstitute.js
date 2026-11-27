// 货品替代清单 API（货品档案「替代货品」+ 换货弹窗共用）
import request from '@/utils/request'

const BASE = '/material-substitutes'

export function listSubstitutes(materialId) { return request.get(BASE, { params: { materialId } }) }

// 整单保存：data = { materialId, substituteMaterialIds: [...] }（全删全插，排除自身与重复）
export function batchSaveSubstitutes(data) { return request.post(`${BASE}/batch-save`, data) }
