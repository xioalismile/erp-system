import request from '@/utils/request'
const BASE = '/finance/cost-config'

export function listCostConfig() { return request.get(`${BASE}/list`) }
export function saveCostConfig(data) { return request.post(BASE, data) }
export function deleteCostConfig(id) { return request.delete(`${BASE}/${id}`) }
export function getActiveCostConfig() { return request.get(`${BASE}/active`) }
