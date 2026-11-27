// BOM API
import request from '@/utils/request'

const BASE = '/masterdata/bom'

export function listAllBom() { return request.get(`${BASE}/list`) }
/** BOM 详情（含子件明细 items）：工单选 BOM 后带出用料、工艺路线带出主 BOM 时用 */
export function getBom(bomId) { return request.get(`${BASE}/${bomId}`) }
export function batchDeleteBom(ids) { return request.delete(`${BASE}/batch`, { data: ids }) }
