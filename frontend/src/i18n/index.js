/**
 * 轻量 i18n 核心（L1 多语言切换，不引入 vue-i18n 依赖）
 *
 * 设计要点：
 * - 语言包来自后端 sys_i18n（key → text），前端按语言缓存，切换时懒加载；
 * - t(key, fallback)：缺词回退链 = 当前语言 → 中文 → fallback(调用方传的中文原文)，
 *   绝不显示裸 key、绝不因翻译缺失导致页面空白；
 * - 语言偏好存 localStorage.erp_lang，默认简体中文（zh-CN），与改造前行为一致。
 */
import { ref } from 'vue'
import { getI18nPackage } from '@/api/system/i18n'
import { getFieldNameMap } from '@/api/system/fieldLabel'
import { scopedGet, scopedSet } from '@/utils/userScopedStorage'
import { MORPHEMES } from './glossary'

export const DEFAULT_LANG = 'zh-CN'
/** 第一版支持：简体中文 + 英文（架构支持追加，新增语言只需加语言包数据 + 前端语言名） */
export const SUPPORTED_LANGS = ['zh-CN', 'en']
export const LANG_LABELS = { 'zh-CN': '简体中文', en: 'English' }

function readStoredLang() {
  try {
    // 按「用户 + 公司」隔离读：同一台电脑换用户/切公司不该继承别人的界面语言
    const l = scopedGet('erp_lang', '')
    return SUPPORTED_LANGS.includes(l) ? l : DEFAULT_LANG
  } catch (_) {
    return DEFAULT_LANG
  }
}

export const lang = ref(readStoredLang())

/** lang -> { key: text } 缓存 */
const cache = {}
/** 语言包版本号：语言包加载/切换完成后自增，驱动所有依赖 t() 的组件重新渲染 */
const packVersion = ref(0)
/** 加载中的 Promise，防并发重复请求 */
const inflight = {}
/** 代码内置静态文案兜底（极少用，主要用于极早期首屏） */
const memory = {}

export function setLang(l) {
  const next = SUPPORTED_LANGS.includes(l) ? l : DEFAULT_LANG
  lang.value = next
  try { scopedSet('erp_lang', next) } catch (_) {}
  return next
}

/**
 * 身份/公司变化时切到该用户自己的语言（登出后身份清空 → 回落默认中文）。
 * 由 utils/sessionCacheReset.js 在登出/登录/切公司时调用。
 */
export function reloadLangForScope() {
  const next = readStoredLang()
  lang.value = next
  ensureLangPack(next).catch(() => {})
  return next
}

/** 预加载语言包；失败静默（调用方继续走回退链，不阻塞页面） */
export async function ensureLangPack(l = lang.value) {
  const target = SUPPORTED_LANGS.includes(l) ? l : DEFAULT_LANG
  if (cache[target]) return cache[target]
  if (inflight[target]) return inflight[target]
  inflight[target] = (async () => {
    try {
      const data = await getI18nPackage(target)
      cache[target] = (data && typeof data === 'object') ? data : {}
    } catch (e) {
      // 接口失败：不缓存，留待下次重试；当前调用回退中文原文
      cache[target] = cache[target] || {}
    } finally {
      delete inflight[target]
    }
    packVersion.value++
    return cache[target]
  })()
  return inflight[target]
}

/** 翻译：t('common.save', '保存') */
export function t(key, fallback) {
  void packVersion.value // 建立响应式依赖：语言包异步加载完成后自动触发组件重渲染
  if (key === null || key === undefined || key === '') return fallback ?? key ?? ''
  if (memory[key] !== undefined) return memory[key]
  const cur = cache[lang.value]
  if (cur && cur[key]) return cur[key]
  const zh = cache[DEFAULT_LANG]
  if (zh && zh[key]) return zh[key]
  // 未命中：返回中文原文；若调用方没传 fallback 且 key 恰是中文（menu.销售管理 这类），
  // 则剥离前缀后返回，保证界面永远不出现裸 key
  if (fallback !== undefined && fallback !== null && fallback !== '') return fallback
  const stripped = String(key).replace(/^[a-zA-Z]+\./, '')
  return stripped
}

export function registerStatic(key, text) {
  memory[key] = text
}

/** 只查当前语言包（不回落中文），命中返回文本，未命中返回 undefined */
export function lookup(key) {
  void packVersion.value
  const cur = cache[lang.value]
  return cur && cur[key] ? cur[key] : undefined
}

// ==================== 租户字段显示名覆盖（2026-10-11 开账引导） ====================
// 每租户一份"原字段名 → 显示名"覆盖（后端 sys_field_label），登录/切公司时装载、登出清空。
// 只影响显示文字（表头/表单标签/明细列头），不影响校验、落库、导入导出、打印取数。
// 无覆盖时 tf() 行为与旧版逐字一致（守则 2.7.2-6 默认值=旧行为）。

const fieldLabelMap = ref({})
const fieldLabelVersion = ref(0)

/** 装载/替换覆盖映射（设置中心保存后立即调用 → 全站即时生效） */
export function setFieldLabelOverrides(map) {
  fieldLabelMap.value = (map && typeof map === 'object' && !Array.isArray(map)) ? map : {}
  fieldLabelVersion.value++
}

/** 清空（登出；防串租户/串用户） */
export function clearFieldLabelOverrides() {
  setFieldLabelOverrides({})
}

/** 按当前身份/公司重新装载覆盖映射（登录成功/切公司后由 sessionCacheReset 调用；失败按无覆盖处理） */
export async function reloadFieldLabelsForScope() {
  try {
    const res = await getFieldNameMap()
    setFieldLabelOverrides(res)
  } catch (_) {
    setFieldLabelOverrides({})
  }
}

// ==================== 字段标题翻译（L2） ====================
// 词素表按长度倒序，保证“未开票”优先于“开票”匹配
const MORPHEME_KEYS = Object.keys(MORPHEMES).sort((a, b) => b.length - a.length)
const composedCache = {}

/** 中文标题 → 英文（词素最长匹配组合），未登记片段保持原文，不产生空白 */
function composeTitle(zh) {
  let out = ''
  let i = 0
  while (i < zh.length) {
    let matched = ''
    for (const k of MORPHEME_KEYS) {
      if (zh.startsWith(k, i)) { matched = k; break }
    }
    if (matched) {
      const en = MORPHEMES[matched]
      if (en) out += (out && !out.endsWith(' ') ? ' ' : '') + en // 空映射=中文助词，直接吞掉
      i += matched.length
    } else {
      out += zh[i]
      i++
    }
  }
  return out.replace(/\s+/g, ' ').trim()
}

/**
 * tf(label) 字段标题翻译：
 * 1. 非中文（已是英文/数字/空）原样返回；
 * 2. 语言包 field.<中文标题> 有完整覆盖 → 用它（管理页可人工修正）；
 * 3. 否则词素组合翻译（结果缓存）；
 * 4. 组合失败仍含中文 → 保持原样（不白屏、不显示裸 key）。
 */
export function tf(label) {
  void packVersion.value // 语言切换后重新计算
  void fieldLabelVersion.value // 覆盖映射装载/修改后全站重新渲染
  if (label === null || label === undefined || label === '') return label ?? ''
  const s = String(label)
  if (!/[\u4e00-\u9fa5]/.test(s)) return s
  // 租户字段显示名覆盖（最高优先级，2026-10-11 开账引导）：按"原字段名"全站统一改显示
  const custom = fieldLabelMap.value[s]
  if (custom && typeof custom === 'string' && custom !== '') return custom
  const override = lookup(`field.${s}`)
  if (override) return override
  if (lang.value === DEFAULT_LANG) return s // 中文态直接回显原文
  if (composedCache[s] !== undefined) return composedCache[s]
  const composed = composeTitle(s)
  composedCache[s] = composed
  return composed
}

// ==================== 界面文案翻译（L3-a） ====================
// tt(中文原文)：提示语 / 按钮 / 标题 / placeholder / 校验语。
// 回退链：语言包 text.<原文> 覆盖 → 词素组合（仅当结果不含中文，避免中式英文混排）→ 原样中文。
// 中文态直接回显原文；缺词绝不白屏、不显示裸 key。
/** 词素组合结果（带缓存）：仅在结果"零中文残留"时才被 tt/dictT 采用 */
function composeSafe(zh) {
  if (composedCache[zh] !== undefined) return composedCache[zh]
  const composed = composeTitle(zh)
  composedCache[zh] = composed
  return composed
}

/** 组合翻译的可用判定：零中文残留 且 原文长度 ≤ 16 字（长句组合易成中式英文，交语言包覆盖） */
function canUseComposed(zh, composed) {
  return !/[\u4e00-\u9fa5]/.test(composed) && [...zh].length <= 16
}

export function tt(zh) {
  void packVersion.value
  if (zh === null || zh === undefined || zh === '') return zh ?? ''
  const s = String(zh)
  if (!/[\u4e00-\u9fa5]/.test(s)) return s
  const override = lookup(`text.${s}`)
  if (override) return override
  if (lang.value === DEFAULT_LANG) return s
  const composed = composeSafe(s)
  if (canUseComposed(s, composed)) return composed
  return s
}

// dictT(中文值)：状态 / 枚举 / 写死下拉的「显示值」翻译。
// 真实值（后端返回的枚举/中文）一律不变，只改渲染文本；无覆盖时先试词素组合，再回退原文。
export function dictT(v) {
  void packVersion.value
  if (v === null || v === undefined || v === '') return v ?? ''
  const s = String(v)
  if (!/[\u4e00-\u9fa5]/.test(s)) return s
  const override = lookup(`dict.${s}`)
  if (override) return override
  if (lang.value === DEFAULT_LANG) return s
  const composed = composeSafe(s)
  if (canUseComposed(s, composed)) return composed
  return s
}
