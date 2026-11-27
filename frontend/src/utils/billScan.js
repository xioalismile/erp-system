/**
 * 单据扫码工具（2026-09-27，D4）
 *
 * 二维码内容协议（与后端 QrCodeContent 一致）：
 *   {域名}/mobile/#/pages/scan/scan?t=MAT&id=123                商品码
 *   {域名}/mobile/#/pages/scan/scan?t=BILL&dt=SALE_ORDER&id=456 单据码
 *   {域名}/mobile/#/pages/scan/scan?t=OP&id=789                 工序码
 *
 * 电脑端扫码来源：USB 扫码枪（等同键盘输入）+ 粘贴。手机端扫码走手机页面。
 */
import { DOC_TYPE } from '@/utils/pushRelations'

/** 解析二维码内容 → { type, docType, id }；不是本系统的码返回 null */
export function parseScanCode(raw) {
  if (!raw) return null
  let text = String(raw).trim()
  if (!text) return null

  // 兼容"手机扫码页二次传入"：/pages/scan/scan?c=<urlencoded 原始内容>
  const wrap = text.match(/[?&]c=([^&]+)/)
  if (wrap) {
    try {
      text = decodeURIComponent(wrap[1])
    } catch (e) {
      /* 忽略解析异常 */
    }
  }

  const qIndex = text.indexOf('?')
  if (qIndex < 0) return null
  const params = {}
  text.substring(qIndex + 1).split('&').forEach(kv => {
    if (!kv) return
    const i = kv.indexOf('=')
    const k = decodeURIComponent(i >= 0 ? kv.substring(0, i) : kv)
    const v = i >= 0 ? decodeURIComponent(kv.substring(i + 1)) : ''
    if (k) params[k] = v
  })
  if (!params.t) return null

  let id = null
  if (params.id != null && params.id !== '') {
    const n = Number(params.id)
    id = Number.isNaN(n) ? null : n
  }
  return {
    type: String(params.t).trim().toUpperCase(),
    docType: params.dt || '',
    id,
    /** 防伪造签名（P1）；旧码没有该参数 → 空串 */
    signature: params.s || '',
    /** 协议版本号（便于以后升级） */
    version: params.v || ''
  }
}

/** 码里的单据类型（SALE_ORDER）→ 下推关系里的类型键（SaleOrder） */
export function toRelationKey(docType) {
  if (!docType) return ''
  const upper = String(docType).trim().toUpperCase()
  return DOC_TYPE[upper] ? DOC_TYPE[upper].key : docType
}
