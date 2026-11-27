// 银行账户（财务账户） API
import request from '@/utils/request'

const BASE = '/basedata/bank-account'

export function listBankAccount(params) { return request.get(`${BASE}/page`, { params }) }
export function listAllBankAccount(params) { return request.get(`${BASE}/list`, { params }) }
export function getBankAccount(id) { return request.get(`${BASE}/${id}`) }
export function saveBankAccount(data) { return request.post(BASE, data) }
export function updateBankAccount(data) { return request.put(BASE, data) }
export function deleteBankAccount(id) { return request.delete(`${BASE}/${id}`) }
export function batchDeleteBankAccount(ids) { return request.post(`${BASE}/batch-delete`, ids) }
