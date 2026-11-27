/**
 * 电脑版入口守卫（2026-09-27）：在任何业务代码之前执行，手机浏览器自动进入手机版。
 * 与 main.js 同为 module，按文档顺序先执行本文件，再执行 main.js。
 */
import { shouldRedirectToMobile, mobileHomeUrl, DESKTOP_FORCE_KEY } from '@/utils/mobileRedirect'

function setFlag(key, val) {
  try { if (typeof localStorage !== 'undefined') localStorage.setItem(key, val) } catch (e) { /* 忽略 */ }
}
function removeFlag(key) {
  try { if (typeof localStorage !== 'undefined') localStorage.removeItem(key) } catch (e) { /* 忽略 */ }
}

try {
  const params = new URLSearchParams(location.search || '')
  // ?desktop=1 记住"继续用电脑版"；?mobile=1 强制回手机版并清除记忆
  if (params.get('desktop') === '1') setFlag(DESKTOP_FORCE_KEY, '1')
  if (params.get('mobile') === '1') removeFlag(DESKTOP_FORCE_KEY)
  if (shouldRedirectToMobile(navigator.userAgent, location.pathname, location.search, location.hostname)) {
    location.replace(mobileHomeUrl())
  }
} catch (e) {
  // 跳转失败不阻断电脑版使用
}
