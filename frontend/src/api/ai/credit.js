import request from '@/utils/request'

/**
 * 小楷助手积分（员工端，2026-10-24）
 *   GET /api/ai/credit/wallet → 当前登录人积分钱包（个人优先，未分配则公司共享）
 *   GET /api/ai/credit/ledger → 最近积分流水
 */

/** 当前登录人积分钱包 + 最近流水 */
export function getMyCreditWallet() {
  return request.get('/ai/credit/wallet')
}

/** 当前登录人最近积分流水 */
export function getMyCreditLedger(limit = 20) {
  return request.get('/ai/credit/ledger', { params: { limit } })
}
