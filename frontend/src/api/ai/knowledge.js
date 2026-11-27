// @feature FEAT-AIKB-001
import request from '@/utils/request'

const BASE = '/ai/knowledge'

/**
 * AI知识库管理 API 封装
 * 后端对应：com.erp.ai.knowledge.controller.KnowledgeController
 */

// 文档列表
export function listKnowledgeDocs(module) {
  return request.get(`${BASE}/list`, { params: { module } })
}

// 上传文档
export function uploadKnowledgeDoc(formData) {
  return request.post(`${BASE}/upload`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 删除文档
export function deleteKnowledgeDoc(id) {
  return request.delete(`${BASE}/${id}`)
}

// 重试解析（failed 状态文档配好 Key 后重新向量化）
export function retryKnowledgeDoc(id) {
  return request.post(`${BASE}/${id}/retry`)
}
