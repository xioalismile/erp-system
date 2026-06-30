// 货位 API
import request from '@/utils/request'

const BASE = '/basedata/location'

export function listLocation(params) { return request.get(`${BASE}/page`, { params }) }
export function listAllLocation(params) { return request.get(`${BASE}/list`, { params }) }
export function getLocation(id) { return request.get(`${BASE}/${id}`) }
export function saveLocation(data) { return request.post(BASE, data) }
export function updateLocation(data) { return request.put(BASE, data) }
export function deleteLocation(id) { return request.delete(`${BASE}/${id}`) }
export function batchDeleteLocation(ids) { return request.post(`${BASE}/batch-delete`, ids) }
