/**
 * 手机浏览器识别 与 「自动进手机版」判定（2026-09-27）
 *
 * 背景：系统有电脑版（根路径 /）与手机版（同域名 /mobile/）两套前端。用户用手机浏览器
 * 打开电脑版网址时应自动进入手机版；电脑浏览器应保持电脑版。
 *
 * 判定要点（避免误伤与死循环）：
 *  - 本地开发（localhost / 127.0.0.1 / *.local）不跳转，否则开发时手机调试会 404；
 *  - 已在 /mobile 下不跳转（防电脑版 ↔ 手机版互相跳转的死循环）；
 *  - 带 ?desktop=1 不跳转（并记住，后续不再跳）；带 ?mobile=1 强制回手机版并清除记忆；
 *  - iPad 等大屏平板按电脑版处理（不跳）。
 */

const MOBILE_UA = /Android|webOS|iPhone|iPod|BlackBerry|IEMobile|Opera Mini|Mobile|HarmonyOS|Windows Phone/i
/** 用户选择"继续用电脑版"的本地标记 */
export const DESKTOP_FORCE_KEY = 'erp_force_desktop'

function readFlag(key) {
  try {
    if (typeof localStorage === 'undefined') return null
    return localStorage.getItem(key)
  } catch (e) {
    return null
  }
}

/** 是否手机浏览器（iPad 视为电脑版） */
export function isMobileUserAgent(ua) {
  if (!ua) return false
  if (/iPad/i.test(ua)) return false
  return MOBILE_UA.test(ua)
}

/**
 * 是否需要跳转到手机版
 * @param {string} ua 浏览器标识 navigator.userAgent
 * @param {string} pathname 当前路径 location.pathname
 * @param {string} [search] 当前查询串 location.search
 * @param {string} [hostname] 当前主机名 location.hostname
 */
export function shouldRedirectToMobile(ua, pathname, search = '', hostname = '') {
  if (!isMobileUserAgent(ua)) return false
  const host = hostname || ''
  if (host === 'localhost' || host === '127.0.0.1' || host.endsWith('.local')) return false
  const path = pathname || '/'
  if (path === '/mobile' || path.startsWith('/mobile/')) return false
  let params
  try {
    params = new URLSearchParams(search || '')
  } catch (e) {
    params = new URLSearchParams('')
  }
  if (params.get('mobile') === '1') return true
  if (params.get('desktop') === '1') return false
  if (readFlag(DESKTOP_FORCE_KEY) === '1') return false
  return true
}

/** 手机版首页地址（与 PC 端同域名，nginx 以 /mobile/ 提供） */
export function mobileHomeUrl() {
  return '/mobile/'
}
