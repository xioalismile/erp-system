/**
 * 按「公司(租户) + 用户」隔离的本地存储底座（2026-09-23 立）。
 *
 * 【为什么必须有】列设置 / 按钮显隐 / 语言 / AI 偏好 / 界面尺寸这些配置以前只用一个 key
 * 存在浏览器里（一份）→ 同一台电脑换用户、或多租户切公司后，新用户会继承上一个人的设置。
 * 用户明确要求「永远不能串」，且指出"都多租户了还共用一份设置中心的配置"是错的。
 *
 * 作用域（key 形态）：
 *   - 用户级偏好（列设置/按钮显隐/语言/AI 偏好）：`<base>::v1::u<用户>@t<公司>`
 *   - 公司级配置缓存（主题/界面尺寸，后端 sys_config 才是真相）：`<base>::v1::t<公司>`
 * 身份缺失时落到 `anon`，绝不写进别人的桶里。
 *
 * 【不做老 key 迁移】把老的无作用域数据搬给"当前登录人"= 把上一个人的设置送给新用户，
 * 正是本次要根治的问题。升级后每人第一次用会回到默认值，需各自重配一次。
 */
const SCOPE_VERSION = 'v1'

// 作用域片段清洗：只保留安全字符，避免 key 里出现分隔符/怪异字符
function part(v) {
  const s = String(v == null ? '' : v).trim()
  return s ? s.replace(/[^\w.@-]/g, '_') : ''
}

function ls() {
  try {
    return window.localStorage
  } catch (e) {
    return null
  }
}

/** 当前用户 id/名（优先 userId，退回 username） */
function userId() {
  const s = ls()
  if (!s) return 'anon'
  return part(s.getItem('userId')) || part(s.getItem('username')) || 'anon'
}

/** 当前公司标识（优先 tenantId，退回 tenantCode / tenantName） */
function tenantId() {
  const s = ls()
  if (!s) return 'anon'
  return part(s.getItem('tenantId')) || part(s.getItem('tenantCode')) || part(s.getItem('tenantName')) || 'anon'
}

/** 当前"用户 + 公司"作用域标识（列设置/按钮显隐/语言/AI 偏好用） */
export function currentUserScope() {
  return `u${userId()}@t${tenantId()}`
}

/** 当前"公司"作用域标识（主题/界面尺寸这类租户级配置缓存用） */
export function currentTenantScope() {
  return `t${tenantId()}`
}

/**
 * 组装真实存储 key
 * @param {string} base 基础名（如 columnConfig）
 * @param {boolean} byUser true=按用户+公司隔离（默认）；false=只按公司隔离
 */
export function scopedKey(base, byUser = true) {
  return `${base}::${SCOPE_VERSION}::${byUser ? currentUserScope() : currentTenantScope()}`
}

/** 读 JSON（不存在/解析失败返回 fallback） */
export function scopedGetJSON(base, fallback = null, byUser = true) {
  const s = ls()
  if (!s) return fallback
  try {
    const raw = s.getItem(scopedKey(base, byUser))
    if (raw == null) return fallback
    const v = JSON.parse(raw)
    return v === null || v === undefined ? fallback : v
  } catch (e) {
    return fallback
  }
}

/** 写 JSON */
export function scopedSetJSON(base, value, byUser = true) {
  const s = ls()
  if (!s) return
  try {
    s.setItem(scopedKey(base, byUser), JSON.stringify(value))
  } catch (e) {
    /* 配额满/隐私模式：静默忽略，不影响功能 */
  }
}

/** 读字符串 */
export function scopedGet(base, fallback = '', byUser = true) {
  const s = ls()
  if (!s) return fallback
  try {
    const v = s.getItem(scopedKey(base, byUser))
    return v == null ? fallback : v
  } catch (e) {
    return fallback
  }
}

/** 写字符串 */
export function scopedSet(base, value, byUser = true) {
  const s = ls()
  if (!s) return
  try {
    s.setItem(scopedKey(base, byUser), String(value))
  } catch (e) {
    /* ignore */
  }
}
