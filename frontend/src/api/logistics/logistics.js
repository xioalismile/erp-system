// 物流查询 API
import request from '@/utils/request'

const BASE = '/logistics'

/**
 * 【一键查询】仅靠单号自动识别承运商（不实际查询轨迹）
 * 前端查询流程：先调此接口 → 根据 matched / candidates 决定下一步
 *
 * 返回：{
 *   matched: boolean,              // 是否唯一识别成功（true时可直接用下面3个字段）
 *   matchedCode: string,           // 承运商编码 jd / shunfeng / yuantong ...
 *   matchedName: string,           // 承运商中文名 京东物流 / 顺丰速运 ...
 *   needPhone: boolean,            // 这个承运商要不要手机号后四位
 *   candidates: [{code,name,needPhone}],  // 多候选，matched=false时用
 *   source: string                 // 识别来源 local_prefix[SF] / autonumber_api_single / ...
 * }
 */
export function detectCarrier(trackingNo) {
  return request.get(`${BASE}/detect-carrier`, { params: { trackingNo } })
}

/**
 * 查询单条物流轨迹（新版：对象传参）
 *
 * 推荐只传 trackingNo 就能查：一个单号就能查模式。
 * 若后端 detectCarrier 返回 needPhone=true，再传 phoneLast4 重查。
 * 若返回 needUserSelectCarrier=true，让用户从 candidates 选一个后传 carrierCode 重查。
 *
 * @param {object} payload
 * @param {string} payload.trackingNo   物流单号（必填）
 * @param {string} [payload.carrier]    承运商中文名（旧接口兼容，优先级低于carrierCode）
 * @param {string} [payload.phone]      完整手机号（自动取后四位）
 * @param {string} [payload.carrierCode] 承运商代码（jd/shunfeng/...，推荐传这个）
 * @param {string} [payload.phoneLast4] 手机号后四位（4位，推荐直接传这个）
 */
export function queryLogistics(payload) {
  return request.get(`${BASE}/query`, { params: payload || {} })
}

/** 兼容旧调用： queryLogistics(trackingNo, carrier, phone) */
export function queryLogisticsLegacy(trackingNo, carrier, phone) {
  return queryLogistics({ trackingNo, carrier, phone })
}

/**
 * 按出库单ID查询物流轨迹
 * @param {number} deliveryId 出库单ID
 */
export function queryLogisticsByDelivery(deliveryId) {
  return request.get(`${BASE}/query/${deliveryId}`)
}

/**
 * 获取在途出库单列表（看板用）
 * @param {object} params { keyword, limit }
 */
export function listInTransit(params) {
  return request.get(`${BASE}/in-transit`, { params })
}

/**
 * 批量查询物流（看板自动刷新用）
 * @param {Array<{trackingNo, carrier?, phone?}>} items
 */
export function batchQueryLogistics(items) {
  return request.post(`${BASE}/batch-query`, { items })
}

/**
 * 获取物流看板配置（刷新间隔、最大数量、自动刷新开关）
 */
export function getRefreshConfig() {
  return request.get(`${BASE}/refresh-interval`)
}

// ============ 快递100 授权配置（敏感信息接口） ============

/**
 * 查询快递100 / 17TRACK 授权配置状态（脱敏返回，不含明文）
 * 返回: {
 *   enabled, configured, customerMasked, updatedAt,                 // 快递100
 *   track17Enabled, track17Configured, track17TokenMasked,          // 17TRACK 国际物流
 *   track17UpdatedAt, track17Carriers, track17CacheLevel
 * }
 */
export function getCredentials() {
  return request.get(`${BASE}/credentials`)
}

/**
 * 保存快递100 / 17TRACK 授权配置
 * @param {object} payload { customer, key, enabled,                    // 快递100
 *                           track17Token, track17Enabled,              // 17TRACK（token 为空=不更新）
 *                           track17Carriers, track17CacheLevel }
 */
export function saveCredentials(payload) {
  return request.post(`${BASE}/credentials`, payload)
}

/**
 * 测试当前授权配置是否可用
 * @param {string} [channel] 不传/KD100=测快递100；'17TRACK'=测17TRACK（调 getquota，不消耗额度）
 * 返回: { success, message, quotaRemain? }
 */
export function testCredentials(channel) {
  return request.post(`${BASE}/credentials/test`, null, {
    params: channel ? { channel } : {}
  })
}
