// 电商平台 - 店铺授权 API
import request from '@/utils/request'

const BASE = '/ecommerce/shops'

export function listEcomShops(params) { return request.get(BASE, { params }) }
export function listEnabledShops() { return request.get(`${BASE}/enabled`) }
export function getEcomShop(id) { return request.get(`${BASE}/${id}`) }
export function saveEcomShop(data) { return request.post(BASE, data) }
export function updateEcomShop(id, data) { return request.put(`${BASE}/${id}`, data) }
export function deleteEcomShop(id) { return request.delete(`${BASE}/${id}`) }
// 授权测试：向卖家平台换取访问令牌
export function testEcomShopAuth(id) { return request.post(`${BASE}/${id}/test-auth`) }
