// 会计科目 API
import request from '@/utils/request'

const BASE = '/basedata/account'

export function listAccount(params) { return request.get(`${BASE}/page`, { params }) }
export function listAllAccount(params) { return request.get(`${BASE}/list`, { params }) }
export function getAccount(id) { return request.get(`${BASE}/${id}`) }
export function saveAccount(data) { return request.post(BASE, data) }
export function updateAccount(data) { return request.put(BASE, data) }
export function deleteAccount(id) { return request.delete(`${BASE}/${id}`) }
export function batchDeleteAccount(ids) { return request.post(`${BASE}/batch-delete`, ids) }
export function treeAccount(params) { return request.get(`${BASE}/tree`, { params }) }
