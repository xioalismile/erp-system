import { defineStore } from 'pinia'
import { getSysConfigByGroup, batchUpdateSysConfig, refreshSysConfig } from '@/api/system/sysConfig'
import { scopedGetJSON, scopedSetJSON } from '@/utils/userScopedStorage'

/**
 * 界面尺寸设置（设置中心 → 自定义 → 界面尺寸设置）
 *
 * 存储：后端 sys_config 表，config_group = 'ui'，键前缀 ui.size.
 *      （与主题配置的 ui.theme. 严格分开 —— 主题 store 只认 ui.theme. 前缀，两边互不干扰）
 * 生效：把配置注入 :root 的 CSS 变量，页面 CSS 用 var(--erp-xxx, 默认值) 读取；
 *      变量未注入时页面走 var() 兜底默认值 → 与改造前界面完全一致。
 *
 * 【重要】以下 5 项数值的默认值 = 2026-09-15 改造前无头浏览器(Playwright)实测的现存值：
 *      菜单文字14px / 列表字段14px / 编辑页字段12px / 搜索框280px / 区块间距10px。
 *      改默认值前必须先实测页面现值，否则会静默改变客户界面（守则 2.7.2-6）。
 *
 * 【界面密度（2026-09-28 新增）】紧凑 / 标准 / 宽松 三档，非数值项：
 *      生效方式 = 在 <html> 上打 data-density 属性，具体尺寸在 styles/index.scss 里按档位定义，
 *      一处切换即改全站 列表页 + 编辑页 的内容留白 / 卡片间距 / 表格行高 / 表单每行字段数。
 *      默认 = 紧凑（用户口径：全站排得太宽、左右留白多、一屏看的东西少）。
 */

// sys_config 中的键前缀
export const UI_SIZE_PREFIX = 'ui.size.'

// 默认值（= 改造前的实测现存值）
export const UI_SIZE_DEFAULTS = {
  menuFontSize: 14,   // 左侧菜单文字大小（实测 14px）
  listFontSize: 14,   // 列表字段文字大小（实测 14px）
  formFontSize: 12,   // 编辑页字段文字大小（实测 12px）
  searchWidth: 280,   // 列表搜索框宽度（实测 280px）
  sectionGap: 10      // 编辑页两个区块之间的高度（实测 10px）
}

// 取值范围（防止填 0/负数/超大值把界面弄坏）
export const UI_SIZE_RANGE = {
  menuFontSize: [10, 24],
  listFontSize: [10, 24],
  formFontSize: [10, 24],
  searchWidth: [120, 800],
  sectionGap: [0, 60]
}

// 注入到 :root 的 CSS 变量名（页面 CSS 读取）
export const UI_SIZE_CSS_VARS = {
  menuFontSize: '--erp-menu-font-size',
  listFontSize: '--erp-list-font-size',
  formFontSize: '--erp-form-font-size',
  searchWidth: '--erp-list-search-width',
  sectionGap: '--erp-form-section-gap'
}

// ---------- 界面密度（非数值项：不参与上表的数值收敛，单独处理）----------
export const UI_SIZE_DENSITY_KEY = 'density'
export const UI_SIZE_DENSITY_LEVELS = ['compact', 'standard', 'loose']
export const UI_SIZE_DENSITY_DEFAULT = 'compact'
// 界面密度文案（设置页下拉/单选展示用）
export const UI_SIZE_DENSITY_LABELS = {
  compact: '紧凑',
  standard: '标准',
  loose: '宽松'
}

const STORAGE_KEY = 'erpUiSizeConfig'

// 单项数值收敛：空/非法 → 默认值；超范围 → 夹到边界；小数 → 取整
function clampOne(key, raw) {
  const def = UI_SIZE_DEFAULTS[key]
  if (raw === null || raw === undefined || raw === '') return def
  const n = Number(raw)
  if (!Number.isFinite(n)) return def
  const [min, max] = UI_SIZE_RANGE[key]
  return Math.min(max, Math.max(min, Math.round(n)))
}

// 界面密度收敛：空/非法 → 默认档（紧凑）
function normalizeDensity(raw) {
  const v = String(raw === null || raw === undefined ? '' : raw).trim().toLowerCase()
  return UI_SIZE_DENSITY_LEVELS.indexOf(v) >= 0 ? v : UI_SIZE_DENSITY_DEFAULT
}

// 全量收敛，保证 store 里永远是 5 个合法数值 + 1 个合法密度档
function normalize(cfg) {
  const out = {}
  Object.keys(UI_SIZE_DEFAULTS).forEach(k => { out[k] = clampOne(k, cfg ? cfg[k] : null) })
  out[UI_SIZE_DENSITY_KEY] = normalizeDensity(cfg ? cfg[UI_SIZE_DENSITY_KEY] : null)
  return out
}

function loadFromStorage() {
  try {
    // 按"公司"作用域读缓存（界面尺寸是租户级配置，后端 sys_config 才是真相，本地只是防闪）
    return normalize(scopedGetJSON(STORAGE_KEY, {}, false))
  } catch (e) {
    return normalize({})
  }
}

export const useUiSizeStore = defineStore('uiSize', {
  state: () => ({
    config: loadFromStorage(),
    loaded: false
  }),
  getters: {
    getSize: (state) => state.config,
    // 当前各项是否全部等于系统默认值（设置页用来提示"当前为系统默认值"）
    isAllDefault: (state) =>
      Object.keys(UI_SIZE_DEFAULTS).every(k => state.config[k] === UI_SIZE_DEFAULTS[k]) &&
      state.config[UI_SIZE_DENSITY_KEY] === UI_SIZE_DENSITY_DEFAULT
  },
  actions: {
    // 注入 CSS 变量到根节点（这是唯一生效入口）；界面密度打在 data-density 属性上
    apply() {
      const root = document.documentElement
      Object.keys(UI_SIZE_CSS_VARS).forEach(k => {
        root.style.setProperty(UI_SIZE_CSS_VARS[k], this.config[k] + 'px')
      })
      root.setAttribute('data-density', normalizeDensity(this.config[UI_SIZE_DENSITY_KEY]))
    },

    // 本地持久化（换页面/刷新不闪）；按"公司"作用域写，切公司不会把上一家的尺寸带过去
    persist() {
      try {
        scopedSetJSON(STORAGE_KEY, this.config, false)
      } catch (e) {
        console.warn('[界面尺寸] 本地缓存写入失败:', e?.message || e)
      }
    },

    /** 身份/公司变化时按当前公司作用域重装本地缓存并立即套用（服务器值是最终口径，随后由 loadFromServer 覆盖） */
    reloadForScope() {
      this.config = loadFromStorage()
      this.apply()
    },

    // 表单改动即时预览（不落库，点保存才落库）
    updateConfig(patch) {
      this.config = normalize({ ...this.config, ...patch })
      this.apply()
      this.persist()
    },

    // 从服务器读取（配置缺失 → 用默认值，界面与改造前一致）
    async loadFromServer() {
      let readCount = 0
      try {
        const res = await getSysConfigByGroup('ui')
        if (Array.isArray(res) && res.length > 0) {
          const map = {}
          res.forEach(item => {
            if (item && item.configKey && item.configKey.indexOf(UI_SIZE_PREFIX) === 0) {
              map[item.configKey.substring(UI_SIZE_PREFIX.length)] = item.configValue
              readCount++
            }
          })
          if (Object.keys(map).length > 0) this.config = normalize({ ...this.config, ...map })
        }
      } catch (e) {
        console.warn('[界面尺寸] 读取服务器配置失败，使用本地缓存/系统默认值:', e?.message || e)
      }
      this.loaded = true
      this.apply()
      this.persist()
      // 启动自检日志（守则2.7.1-3）：明确打印读到了几项、没读到用什么值
      console.info(
        '[界面尺寸] 配置来源=sys_config(group=ui) 读到 ' + readCount + ' 项，' +
        '未配置项使用系统默认值：' +
        '菜单文字' + this.config.menuFontSize + 'px / 列表字段' + this.config.listFontSize + 'px / ' +
        '编辑页字段' + this.config.formFontSize + 'px / 搜索框' + this.config.searchWidth + 'px / ' +
        '区块间距' + this.config.sectionGap + 'px / 界面密度' +
        (UI_SIZE_DENSITY_LABELS[this.config[UI_SIZE_DENSITY_KEY]] || this.config[UI_SIZE_DENSITY_KEY])
      )
    },

    // 保存到服务器（batch 是 upsert，无需先建配置行）
    async save() {
      const items = Object.keys(UI_SIZE_DEFAULTS).map(k => ({
        configKey: UI_SIZE_PREFIX + k,
        configValue: String(this.config[k]),
        configGroup: 'ui',
        configSection: '界面尺寸',
        configType: 'number',
        configName: k
      }))
      // 界面密度是文本档位，单独一项
      items.push({
        configKey: UI_SIZE_PREFIX + UI_SIZE_DENSITY_KEY,
        configValue: String(this.config[UI_SIZE_DENSITY_KEY]),
        configGroup: 'ui',
        configSection: '界面尺寸',
        configType: 'string',
        configName: UI_SIZE_DENSITY_KEY
      })
      await batchUpdateSysConfig(items)
      this.persist()
      this.apply()
      // 清后端 sys_config 缓存，保证其它页面/其它用户读到新值（失败不影响本次已生效）
      try { await refreshSysConfig() } catch (e) { /* 忽略 */ }
    },

    // 恢复默认值（只改内存/本地，需再点保存才落库）
    reset() {
      this.config = normalize({})
      this.apply()
      this.persist()
    }
  }
})
