import request from '@/utils/request'

/**
 * AI 模型服务商 / 渠道配置 API 封装
 * （守则2.8.2-4：禁止在 .vue 内硬写 URL）
 * 后端对应：com.erp.ai.controller.AiConfigController
 *   GET    /api/ai/config/channels         → 内置渠道列表（脱敏）
 *   POST   /api/ai/config/apikey           → 保存内置渠道平台 API Key
 *   POST   /api/ai/config/channel-config   → 保存内置渠道调用地址/模型
 *   POST   /api/ai/config/test             → 测试内置渠道连通性
 *   POST   /api/ai/config/tenant-apikey    → 保存租户自有 Key
 *   DELETE /api/ai/config/tenant-apikey    → 清除租户自有 Key
 *   GET    /api/ai/config/providers        → 自定义服务商列表（脱敏）
 *   POST   /api/ai/config/providers        → 新增/更新自定义服务商
 *   DELETE /api/ai/config/providers        → 删除自定义服务商
 *   POST   /api/ai/config/providers/test   → 测试自定义服务商连通性
 */

const BASE = '/ai/config'

// ===== 内置服务商 =====
export function listChannels() {
  return request.get(`${BASE}/channels`)
}

export function saveChannelApiKey(channel, apiKey) {
  return request.post(`${BASE}/apikey`, { channel, apiKey })
}

export function saveChannelConfig(channel, baseUrl, model) {
  return request.post(`${BASE}/channel-config`, { channel, baseUrl, model })
}

export function testChannel(channel) {
  return request.post(`${BASE}/test`, { channel })
}

// ===== 租户自有 Key =====
export function saveTenantApiKey(channel, apiKey) {
  return request.post(`${BASE}/tenant-apikey`, { channel, apiKey })
}

export function clearTenantApiKey(channel) {
  return request.delete(`${BASE}/tenant-apikey`, { params: { channel } })
}

// ===== 自定义服务商（多厂商） =====
export function listProviders() {
  return request.get(`${BASE}/providers`)
}

export function saveProvider(payload) {
  return request.post(`${BASE}/providers`, payload)
}

export function deleteProvider(id) {
  return request.delete(`${BASE}/providers`, { params: { id } })
}

export function testProvider(payload) {
  return request.post(`${BASE}/providers/test`, payload)
}

// ===== 对话能力（联网搜索 / 回答严谨度 / 回答详细度 / 会话记忆轮数） =====
export function getChatCapabilities() {
  return request.get(`${BASE}/chat-capabilities`)
}

export function saveChatCapabilities(payload) {
  return request.post(`${BASE}/chat-capabilities`, payload)
}

// ===== 语音运行参数（内部：等待指令时长；全局统一、客户界面无入口） =====
// 返回 { cmdWaitSec }；取不到时前端按设计默认 5 秒运行
export function getVoiceRuntimeConfig() {
  return request.get(`${BASE}/voice-runtime`)
}
