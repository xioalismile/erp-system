import request from '@/utils/request'
const BASE = '/customer/pool'

// 公海池列表
export function poolList(params) { return request.get(`${BASE}/list`, { params }) }
// 领取公海客户（后端从JWT取userId，前端无需传）
export function claimFromPool(id) { return request.post(`${BASE}/${id}/claim`) }
// 释放客户到公海
export function releaseToPool(id, reason) { return request.post(`${BASE}/${id}/release`, { reason }) }
// 批量分配
export function batchAssign(customerIds, salesId) { return request.post(`${BASE}/batch-assign`, { customerIds, salesId }) }
// 离职转移
export function transferByResignation(fromSalesId, toSalesId, toPool) { return request.post(`${BASE}/transfer-by-resignation`, { fromSalesId, toSalesId, toPool }) }
// 切换置顶
export function toggleTop(id, isTop) { return request.post(`${BASE}/${id}/toggle-top`, { isTop }) }
// 手动触发自动回收
export function triggerAutoRecycle() { return request.post(`${BASE}/auto-recycle`) }

// 公海池配置
export function listPoolConfig() { return request.get(`${BASE}/config`) }
export function savePoolConfig(data) { return request.post(`${BASE}/config`, data) }
export function deletePoolConfig(id) { return request.delete(`${BASE}/config/${id}`) }
export function getActivePoolConfig() { return request.get(`${BASE}/config/active`) }

// 预警
export function warningList() { return request.get(`${BASE}/warning/list`) }
export function listWarningConfig() { return request.get(`${BASE}/warning/config`) }
export function saveWarningConfig(data) { return request.post(`${BASE}/warning/config`, data) }
export function deleteWarningConfig(id) { return request.delete(`${BASE}/warning/config/${id}`) }

// 标签字典
export function listTagDict(params) { return request.get(`${BASE}/tag-dict`, { params }) }
export function saveTagDict(data) { return request.post(`${BASE}/tag-dict`, data) }
export function deleteTagDict(id) { return request.delete(`${BASE}/tag-dict/${id}`) }

// 分群
export function listSegment() { return request.get(`${BASE}/segment`) }
export function saveSegment(data) { return request.post(`${BASE}/segment`, data) }
export function deleteSegment(id) { return request.delete(`${BASE}/segment/${id}`) }

// 防造假
export function checkDuplicate(phone, taxNo, address) { return request.get(`${BASE}/check-duplicate`, { params: { phone, taxNo, address } }) }
