// ====================================================================
// 主题配置 Store - 管理系统主题色（主色/背景色/侧边栏色/顶栏色等）
// 用户可在设置中心自行配置, 同步到后端 sys_config 表 + localStorage 持久化
// 通过 CSS 变量注入实现实时切换（--erp-primary / --el-color-primary 等）
// ====================================================================
import { defineStore } from 'pinia'
import request from '@/utils/request'
import { scopedGetJSON, scopedSetJSON } from '@/utils/userScopedStorage'

// 默认主题配置（与 variables.scss 保持一致）
export const DEFAULT_THEME = {
  preset: 'tech-blue',           // 预设主题
  primaryColor: '#409EFF',        // 主色
  successColor: '#67C23A',        // 成功色
  warningColor: '#E6A23C',        // 警告色
  dangerColor: '#F56C6C',         // 危险色
  infoColor: '#909399',           // 信息色
  bgPage: '#F5F7FA',              // 页面背景色
  sidebarBg: '#001529',           // 侧边栏背景色
  sidebarText: '#ffffff',          // 侧边栏文字色
  sidebarActiveBg: '#409EFF',     // 侧边栏选中项背景
  headerBg: '#ffffff',            // 顶栏背景色
  headerText: '#303133'           // 顶栏文字色
}

// 预设主题（6 套，覆盖常见风格）
export const THEME_PRESETS = [
  {
    key: 'tech-blue',
    name: '科技蓝',
    desc: '经典 ERP 蓝，沉稳专业',
    preview: '#409EFF',
    colors: {
      primaryColor: '#409EFF', successColor: '#67C23A', warningColor: '#E6A23C',
      dangerColor: '#F56C6C', infoColor: '#909399',
      bgPage: '#F5F7FA', sidebarBg: '#001529', sidebarText: '#ffffff',
      sidebarActiveBg: '#409EFF', headerBg: '#ffffff', headerText: '#303133'
    }
  },
  {
    key: 'business-deep',
    name: '商务深蓝',
    desc: '深色侧栏，商务大气',
    preview: '#2563EB',
    colors: {
      primaryColor: '#2563EB', successColor: '#16A34A', warningColor: '#EA580C',
      dangerColor: '#DC2626', infoColor: '#64748B',
      bgPage: '#F1F5F9', sidebarBg: '#1E293B', sidebarText: '#E2E8F0',
      sidebarActiveBg: '#2563EB', headerBg: '#ffffff', headerText: '#0F172A'
    }
  },
  {
    key: 'vitality-orange',
    name: '活力橙',
    desc: '明亮橙色，活力十足',
    preview: '#F97316',
    colors: {
      primaryColor: '#F97316', successColor: '#22C55E', warningColor: '#FACC15',
      dangerColor: '#EF4444', infoColor: '#94A3B8',
      bgPage: '#FFF7ED', sidebarBg: '#431407', sidebarText: '#FED7AA',
      sidebarActiveBg: '#F97316', headerBg: '#ffffff', headerText: '#7C2D12'
    }
  },
  {
    key: 'elegant-purple',
    name: '优雅紫',
    desc: '紫色调，优雅现代',
    preview: '#8B5CF6',
    colors: {
      primaryColor: '#8B5CF6', successColor: '#10B981', warningColor: '#F59E0B',
      dangerColor: '#EF4444', infoColor: '#6B7280',
      bgPage: '#F5F3FF', sidebarBg: '#2E1065', sidebarText: '#DDD6FE',
      sidebarActiveBg: '#8B5CF6', headerBg: '#ffffff', headerText: '#3B0764'
    }
  },
  {
    key: 'natural-green',
    name: '自然绿',
    desc: '清新绿色，护眼舒适',
    preview: '#10B981',
    colors: {
      primaryColor: '#10B981', successColor: '#22C55E', warningColor: '#F59E0B',
      dangerColor: '#EF4444', infoColor: '#94A3B8',
      bgPage: '#F0FDF4', sidebarBg: '#064E3B', sidebarText: '#A7F3D0',
      sidebarActiveBg: '#10B981', headerBg: '#ffffff', headerText: '#064E3B'
    }
  },
  {
    key: 'minimal-gray',
    name: '极简灰',
    desc: '低饱和度，极简风格',
    preview: '#475569',
    colors: {
      primaryColor: '#475569', successColor: '#16A34A', warningColor: '#D97706',
      dangerColor: '#DC2626', infoColor: '#94A3B8',
      bgPage: '#F8FAFC', sidebarBg: '#0F172A', sidebarText: '#CBD5E1',
      sidebarActiveBg: '#475569', headerBg: '#ffffff', headerText: '#0F172A'
    }
  },
  {
    key: 'dark-mode',
    name: '暗黑模式',
    desc: '深色主题，夜间护眼',
    preview: '#1F2937',
    colors: {
      primaryColor: '#3B82F6', successColor: '#10B981', warningColor: '#F59E0B',
      dangerColor: '#EF4444', infoColor: '#6B7280',
      bgPage: '#111827', sidebarBg: '#0F172A', sidebarText: '#E5E7EB',
      sidebarActiveBg: '#3B82F6', headerBg: '#1F2937', headerText: '#F3F4F6'
    }
  }
]

const STORAGE_KEY = 'erpThemeConfig'

// sys_config 中的 key 前缀
const SYS_CONFIG_KEY_PREFIX = 'ui.theme.'

function loadFromStorage() {
  try {
    // 按"公司"作用域读缓存（主题是租户级配置：后端 sys_config 才是真相，本地只是防闪/防串）
    const parsed = scopedGetJSON(STORAGE_KEY, null, false)
    if (!parsed) return { ...DEFAULT_THEME }
    return { ...DEFAULT_THEME, ...parsed }
  } catch (e) {
    return { ...DEFAULT_THEME }
  }
}

// 将 hex 颜色转为 rgb 数组
function hexToRgb(hex) {
  if (!hex || typeof hex !== 'string') return [64, 158, 255]
  const clean = hex.replace('#', '').trim()
  if (clean.length !== 6) return [64, 158, 255]
  const r = parseInt(clean.substring(0, 2), 16)
  const g = parseInt(clean.substring(2, 4), 16)
  const b = parseInt(clean.substring(4, 6), 16)
  return [r, g, b]
}

// 生成 Element Plus 衍生色（浅色 light-3/5/7/9, 深色 dark-2）
function generateElColorVariants(hex) {
  const [r, g, b] = hexToRgb(hex)
  // 与白色混合，比例越大越浅
  const mix = (rgb, white = 255, ratio) => {
    return rgb.map(c => Math.round(c * (1 - ratio) + white * ratio))
  }
  const toHex = (rgb) => '#' + rgb.map(c => c.toString(16).padStart(2, '0')).join('')
  return {
    light3: toHex(mix([r, g, b], 255, 0.3)),
    light5: toHex(mix([r, g, b], 255, 0.5)),
    light7: toHex(mix([r, g, b], 255, 0.7)),
    light8: toHex(mix([r, g, b], 255, 0.8)),
    light9: toHex(mix([r, g, b], 255, 0.9)),
    dark2: toHex(mix([r, g, b], 0, 0.2))
  }
}

export const useThemeStore = defineStore('theme', {
  state: () => ({
    config: loadFromStorage(),
    loaded: false   // 是否已从后端加载
  }),
  getters: {
    // 获取当前主题配置
    getTheme: (state) => state.config,
    // 获取当前预设
    currentPreset: (state) => state.config.preset,
    // 是否暗黑模式
    isDarkMode: (state) => state.config.preset === 'dark-mode'
  },
  actions: {
    // 应用主题色到 CSS 变量
    applyTheme() {
      const cfg = this.config
      const root = document.documentElement

      // 1. 注入 ERP 业务变量
      root.style.setProperty('--erp-primary', cfg.primaryColor)
      root.style.setProperty('--erp-success', cfg.successColor)
      root.style.setProperty('--erp-warning', cfg.warningColor)
      root.style.setProperty('--erp-danger', cfg.dangerColor)
      root.style.setProperty('--erp-info', cfg.infoColor)
      root.style.setProperty('--erp-bg-page', cfg.bgPage)
      root.style.setProperty('--erp-sidebar-bg', cfg.sidebarBg)
      root.style.setProperty('--erp-sidebar-text', cfg.sidebarText)
      root.style.setProperty('--erp-sidebar-active-bg', cfg.sidebarActiveBg)
      root.style.setProperty('--erp-header-bg', cfg.headerBg)
      root.style.setProperty('--erp-header-text', cfg.headerText)

      // 2. 注入 Element Plus 主色变量
      root.style.setProperty('--el-color-primary', cfg.primaryColor)
      const variants = generateElColorVariants(cfg.primaryColor)
      root.style.setProperty('--el-color-primary-light-3', variants.light3)
      root.style.setProperty('--el-color-primary-light-5', variants.light5)
      root.style.setProperty('--el-color-primary-light-7', variants.light7)
      root.style.setProperty('--el-color-primary-light-8', variants.light8)
      root.style.setProperty('--el-color-primary-light-9', variants.light9)
      root.style.setProperty('--el-color-primary-dark-2', variants.dark2)

      root.style.setProperty('--el-color-success', cfg.successColor)
      root.style.setProperty('--el-color-warning', cfg.warningColor)
      root.style.setProperty('--el-color-danger', cfg.dangerColor)
      root.style.setProperty('--el-color-info', cfg.infoColor)

      // 3. 设置 body 背景
      document.body.style.backgroundColor = cfg.bgPage

      // 4. 暗黑模式标记
      if (cfg.preset === 'dark-mode') {
        root.setAttribute('data-theme', 'dark')
      } else {
        root.removeAttribute('data-theme')
      }
    },

    // 持久化到 localStorage（按"公司"作用域，避免切公司后先闪上一家的主题）
    persist() {
      try {
        scopedSetJSON(STORAGE_KEY, this.config, false)
      } catch (e) {
        console.warn('主题配置持久化失败:', e)
      }
    },

    /** 身份/公司变化时按当前公司作用域重装本地缓存并立即套用（随后由 loadFromServer 覆盖为服务器值） */
    reloadForScope() {
      this.config = loadFromStorage()
      this.applyTheme()
    },

    // 从后端加载主题配置
    async loadFromServer() {
      try {
        const res = await request.get('/sys-config/group/ui')
        if (Array.isArray(res) && res.length > 0) {
          const map = {}
          res.forEach(item => {
            if (item.configKey && item.configKey.startsWith(SYS_CONFIG_KEY_PREFIX)) {
              const field = item.configKey.substring(SYS_CONFIG_KEY_PREFIX.length)
              map[field] = item.configValue
            }
          })
          if (Object.keys(map).length > 0) {
            // 类型转换：preset/颜色字段都是字符串，直接覆盖
            this.config = { ...DEFAULT_THEME, ...this.config, ...map }
          }
        }
        this.loaded = true
        this.applyTheme()
      } catch (e) {
        // 后端未配置时使用本地默认
        this.loaded = true
        this.applyTheme()
      }
    },

    // 保存到后端 + localStorage
    async saveToServer() {
      try {
        const items = Object.entries(this.config).map(([key, value]) => ({
          configKey: SYS_CONFIG_KEY_PREFIX + key,
          configValue: String(value),
          configGroup: 'ui',
          configType: 'string'
        }))
        await request.put('/sys-config/batch', items)
        this.persist()
        this.applyTheme()
        return true
      } catch (e) {
        // 后端保存失败时仍持久化到本地
        this.persist()
        this.applyTheme()
        return false
      }
    },

    // 更新单个字段
    updateField(field, value) {
      this.config[field] = value
      this.applyTheme()
      this.persist()
    },

    // 批量更新
    updateConfig(newConfig) {
      this.config = { ...this.config, ...newConfig }
      this.applyTheme()
      this.persist()
    },

    // 应用预设主题
    applyPreset(presetKey) {
      const preset = THEME_PRESETS.find(p => p.key === presetKey)
      if (!preset) return
      this.config = {
        ...this.config,
        preset: preset.key,
        ...preset.colors
      }
      this.applyTheme()
      this.persist()
    },

    // 重置为默认主题
    reset() {
      this.config = { ...DEFAULT_THEME }
      this.applyTheme()
      this.persist()
    }
  }
})
