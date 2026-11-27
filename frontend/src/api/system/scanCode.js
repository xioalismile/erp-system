/**
 * 扫码相关 API（2026-09-27，D4）
 */
import request from '@/utils/request'

export default {
  /**
   * 扫码解析：把"类型 + 主键"解析成标题/跳转目标（商品码会额外返回商品明细）
   * @param {String} t  MAT / BILL / OP
   * @param {String} dt 单据类型（仅 BILL 需要，如 SALE_ORDER）
   * @param {Number|String} id 主键
   */
  resolve(t, dt, id, s) {
    return request.get('/qrcode/resolve', { params: { t, dt, id, s } })
  },

  /**
   * 取二维码内联图（data:image/png;base64,...）
   * 走 request 会自动带登录令牌 —— 后端 /api/qrcode 需登录，直接用 <img src="/api/qrcode"> 会 401。
   * @param {String} t  MAT / BILL / OP
   * @param {String} dt 单据类型（仅 BILL 需要）
   * @param {Number|String} id 主键
   * @param {Number} size 像素（60~600），默认后端取值
   */
  dataUri(t, dt, id, size) {
    return request.get('/qrcode/data-uri', { params: { t, dt, id, size } })
  },

  /** 取二维码内容文本（供页面显示/复制） */
  content(t, dt, id) {
    return request.get('/qrcode/content', { params: { t, dt, id } })
  }
}
