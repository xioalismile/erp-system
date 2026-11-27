// 设备管理 - 设备分类 API
import request from '@/utils/request'

const BASE = '/device/category'

export function getCategoryTree() { return request.get(`${BASE}/tree`) }
export function getCategory(id) { return request.get(`${BASE}/${id}`) }
export function createCategory(data) { return request.post(BASE, data) }
export function updateCategory(id, data) { return request.put(`${BASE}/${id}`, data) }
export function deleteCategory(id) { return request.delete(`${BASE}/${id}`) }
