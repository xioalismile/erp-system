// 电商平台 - 商品映射 API（平台货品 ↔ 系统物料）
import request from '@/utils/request'

const BASE = '/ecommerce/product-mappings'

export function listProductMappings(params) { return request.get(BASE, { params }) }
export function saveProductMapping(data) { return request.post(BASE, data) }
export function updateProductMapping(id, data) { return request.put(`${BASE}/${id}`, data) }
export function deleteProductMapping(id) { return request.delete(`${BASE}/${id}`) }
