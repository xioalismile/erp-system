import request from '@/utils/request'

/**
 * 小楷助手「服务协议与免责声明」同意状态 API 封装
 * （守则2.8.2-4：禁止在 .vue 内硬写 URL）
 * 后端对应：com.erp.ai.controller.AiDisclaimerController
 *   GET  /api/ai/disclaimer/status?version=x.x  → {currentVersion, accepted, acceptedVersion, acceptedAt}
 *   POST /api/ai/disclaimer/accept              → 记录同意 {version}
 */

const BASE = '/ai/disclaimer'

/** 查询当前账号的同意状态（version = 前端常量里的当前协议版本号） */
export function getDisclaimerStatus(version) {
  return request.get(`${BASE}/status`, { params: { version } })
}

/** 记录同意（勾选"我已阅读并同意"后调用） */
export function acceptDisclaimer(version) {
  return request.post(`${BASE}/accept`, { version })
}
