import { defineStore } from 'pinia'
import { getPublicBasicConfig } from '@/api/system/sysConfig'

const LS_KEY = 'erp_app_config_v1'

export const DEFAULTS = Object.freeze({
  'system.name': '智能ERP系统',
  'system.logo': '',
  'system.subtitle': '支持多AI渠道接入 · 企业级管理平台',
  'system.copyright': '© 智能ERP 版权所有',
  'system.customer_service_phone': '400-1867472',
  // 登录页改版（2026-09-13）：system.login_bg 非空=用整张设计图当背景的新布局；login_title=表单标题
  'system.login_bg': '',
  'system.login_title': '',
  // 登录页样式尺寸——可在「设置中心→基本信息」改，改完刷新登录页即生效，无需发版
  'system.login_label_size': '35',    // 字段标签字号（px，设计图坐标系）
  'system.login_title_size': '125',   // 表单标题字号（px）
  'system.login_title_offset': '-100' // 标题上移量（px，负值=上移；0=标题顶边与表单卡片顶边齐平）
})

function safeParse(raw) {
  try {
    const o = JSON.parse(raw)
    if (o && typeof o === 'object') return o
  } catch (_) {}
  return null
}

export const useAppConfigStore = defineStore('appConfig', {
  state: () => ({
    'system.name': DEFAULTS['system.name'],
    'system.logo': DEFAULTS['system.logo'],
    'system.subtitle': DEFAULTS['system.subtitle'],
    'system.copyright': DEFAULTS['system.copyright'],
    'system.customer_service_phone': DEFAULTS['system.customer_service_phone'],
    'system.login_bg': DEFAULTS['system.login_bg'],
    'system.login_title': DEFAULTS['system.login_title'],
    'system.login_label_size': DEFAULTS['system.login_label_size'],
    'system.login_title_size': DEFAULTS['system.login_title_size'],
    'system.login_title_offset': DEFAULTS['system.login_title_offset'],
    _loaded: false,
    _loading: null
  }),
  getters: {
    systemName: (s) => s['system.name'] || DEFAULTS['system.name'],
    systemLogo: (s) => s['system.logo'] || '',
    systemSubtitle: (s) => s['system.subtitle'] || DEFAULTS['system.subtitle'],
    copyright: (s) => s['system.copyright'] || DEFAULTS['system.copyright'],
    servicePhone: (s) => s['system.customer_service_phone'] || DEFAULTS['system.customer_service_phone'],
    // 登录页改版：新布局开关（非空=整张设计图当背景）与表单标题
    loginBg: (s) => (s['system.login_bg'] || '').trim(),
    loginTitle: (s) => (s['system.login_title'] || '').trim(),
    // 登录页样式尺寸（原始字符串，由页面做数值兜底）
    loginLabelSize: (s) => (s['system.login_label_size'] || '').trim(),
    loginTitleSize: (s) => (s['system.login_title_size'] || '').trim(),
    loginTitleOffset: (s) => (s['system.login_title_offset'] || '').trim(),
    // logo URL 补全（相对路径 /uploads/xxx.png 直接原样返回，因为前端路由 base 在 /，浏览器能直接请求到；空串返回空让外层 fallback 到内置图标）
    resolvedLogoUrl: (s) => {
      const v = s['system.logo']
      if (!v) return ''
      const t = String(v).trim()
      if (!t) return ''
      // 本地占位符 /logo.png 视为未配置（历史初始化SQL遗留值），统一返回空 → 走内置图标兜底
      if (t === '/logo.png') return ''
      return t
    }
  },
  actions: {
    _mergeValues(obj, persist = true) {
      if (!obj) return
      for (const k of Object.keys(DEFAULTS)) {
        if (obj[k] !== undefined && obj[k] !== null) {
          this[k] = String(obj[k])
        }
      }
      if (persist) {
        try {
          localStorage.setItem(LS_KEY, JSON.stringify({
            'system.name': this['system.name'],
            'system.logo': this['system.logo'],
            'system.subtitle': this['system.subtitle'],
            'system.copyright': this['system.copyright'],
            'system.customer_service_phone': this['system.customer_service_phone'],
            'system.login_bg': this['system.login_bg'],
            'system.login_title': this['system.login_title'],
            'system.login_label_size': this['system.login_label_size'],
            'system.login_title_size': this['system.login_title_size'],
            'system.login_title_offset': this['system.login_title_offset']
          }))
        } catch (_) {}
      }
    },
    _restoreFromCache() {
      const raw = localStorage.getItem(LS_KEY)
      const o = safeParse(raw)
      if (o) this._mergeValues(o, false)
    },
    /**
     * 加载公开基础配置。
     * 优先级：接口返回 > localStorage缓存 > DEFAULTS
     * @param {boolean} force 为 true 时忽略缓存/已加载状态，强制重新拉接口（设置中心保存后调用）
     */
    async loadPublicConfig(force = false) {
      if (!force && this._loading) return this._loading
      if (!force && this._loaded) return
      this._restoreFromCache()
      const p = (async () => {
        try {
          const data = await getPublicBasicConfig()
          if (data) this._mergeValues(data, true)
        } finally {
          this._loaded = true
        }
      })()
      this._loading = p
      try { await p } finally { this._loading = null }
    },
    /**
     * 保存配置后调用：强制刷新 + 刷新浏览器标题
     */
    async refreshConfig() {
      await this.loadPublicConfig(true)
      try {
        const t = document.querySelector('title')
        if (t) {
          const cur = document.title
          const name = this.systemName
          if (!cur.endsWith(name)) document.title = (cur ? cur.split(' - ')[0] + ' - ' : '') + name
        }
      } catch (_) {}
    },
    /**
     * 设置中心提交时直接批量更新本地 state（无需等接口再次拉取）
     */
    applyPatches(patches) {
      this._mergeValues(patches, true)
    }
  }
})
