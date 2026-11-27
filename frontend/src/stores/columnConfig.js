// ====================================================================
// 列设置 Store - 管理各页面表格列的显隐、顺序、固定方式
// 配置持久化到 localStorage，按 pageKey 维度存储
// 结构: { [pageKey]: { columns: { [prop]: { visible, fixed, order } } } }
// version: 全局版本号，每次 setPageConfig/batchUpdateOrder 等写入操作自增
//          业务页面通过 isColumnVisible getter 读取 version 触发响应式依赖收集
//          从而在保存配置后表格立即重新渲染
// ====================================================================
import { defineStore } from 'pinia'
import { scopedGetJSON, scopedSetJSON, scopedGet, scopedSet } from '@/utils/userScopedStorage'

// 存储基名；真实 key = `columnConfig::v1::u<用户>@t<公司>`（见 utils/userScopedStorage.js）
const STORAGE_KEY = 'columnConfig'

function loadFromStorage() {
  // 按「用户 + 公司」隔离读取：同一台电脑换用户 / 多租户切公司后，不得继承别人的列设置
  return scopedGetJSON(STORAGE_KEY, {})
}

/**
 * 全站默认视图（2026-10-18）：按「公司」本地缓存一份上次成功装载的服务端值，
 * 让列表页刷新时**首屏就按上次的默认视图渲染**（此前要等异步接口回来才切换，
 * 网络稍慢/接口失败时用户看到的就是「刷新后又回到汇总」）。
 * 只缓存默认值，个人逐页的显式选择仍优先（存储在各页页记录里）。
 */
const GLOBAL_VIEW_KEY = 'listDefaultView'
function loadGlobalDefaultView() {
  return scopedGet(GLOBAL_VIEW_KEY, '', false) === 'detail' ? 'detail' : 'summary'
}

export const useColumnConfigStore = defineStore('columnConfig', {
  state: () => ({
    // { [pageKey]: { columns: { [prop]: { visible, fixed, order } } } }
    config: loadFromStorage(),
    // 全局版本号：每次配置变更自增，用于触发 getter 响应式更新
    version: 0,
    // 全站默认视图（2026-10-14 双视图二期）：设置中心 → 系统参数 → 基础设置「列表默认视图」。
    // 只作"个人从未切换过的页面"的默认值；2026-10-18 起按公司缓存上次服务端值，
    // 刷新后首屏即按该值渲染，不再先汇总再异步切换（原实现网络慢时会看到回落到汇总）。
    globalDefaultView: loadGlobalDefaultView()
  }),
  getters: {
    // 获取某页面的列配置对象 { [prop]: { visible, fixed, order } }
    // 未配置时返回空对象（调用方应使用默认值兜底）
    getPageConfig: (state) => (pageKey) => {
      // 访问 version 触发响应式依赖收集，保证保存后 getter 重新计算
      void state.version
      if (!pageKey) return {}
      const pageCfg = state.config[pageKey]
      return pageCfg?.columns || {}
    },
    // 判断某页面某列是否可见（未配置默认可见）
    isColumnVisible: (state) => (pageKey, prop) => {
      // 访问 version 触发响应式依赖收集
      void state.version
      const pageCfg = state.config[pageKey]
      if (!pageCfg?.columns) return true
      const colCfg = pageCfg.columns[prop]
      return colCfg?.visible !== false
    },
    // 获取某列的固定方式（未配置返回 null，由调用方使用默认值）
    getColumnFixed: (state) => (pageKey, prop) => {
      void state.version
      const pageCfg = state.config[pageKey]
      if (!pageCfg?.columns) return null
      const colCfg = pageCfg.columns[prop]
      return colCfg?.fixed ?? null
    },
    // 视图模式（2026-10-12；2026-10-14 三级回退）：
    // 个人在该页切换过 → 个人选择；从未切换过 → 全站默认视图（设置中心「列表默认视图」）；
    // 全站默认也未装载 → 'summary'（汇总，与升级前完全一致）
    getViewMode: (state) => (pageKey) => {
      void state.version
      const own = state.config[pageKey]?.viewMode
      if (own === 'detail' || own === 'summary') return own
      return state.globalDefaultView === 'detail' ? 'detail' : 'summary'
    }
  },
  actions: {
    // 触发响应式更新（内部调用）
    _bumpVersion() {
      this.version++
    },
    // 设置某页面完整的列配置
    // columnsCfg: { [prop]: { visible, fixed, order } }
    //
    // ⚠ 列宽（width）由用户拖拽表头单独维护，**不经过本方法**：保存列设置时必须把已保存的列宽带过来，
    // 否则用户在列设置里点一次「保存」就会把辛苦拖好的列宽冲掉（2026-09-22 新增列宽持久化时特意处理）。
    setPageConfig(pageKey, columnsCfg) {
      if (!pageKey) return
      const prev = this.config[pageKey]?.columns || {}
      const merged = {}
      Object.keys(columnsCfg || {}).forEach(prop => {
        merged[prop] = { ...columnsCfg[prop] }
        if (prev[prop] && prev[prop].width !== undefined) merged[prop].width = prev[prop].width
      })
      // 视图模式（2026-10-12 列表页明细模式）：与列配置同存一条记录，保存列设置时不能把它冲掉
      this.config[pageKey] = { columns: merged, viewMode: this.config[pageKey]?.viewMode }
      this._bumpVersion()
      this.persist()
    },
    // 视图模式（2026-10-12）：'summary' 汇总（默认，= 改版前列表） / 'detail' 明细（一行一条明细）
    // 与列设置同走「用户+公司」隔离存储；两种模式各存一套列配置（明细列存 pageKey::detail 下）
    setViewMode(pageKey, mode) {
      if (!pageKey) return
      const normalized = mode === 'detail' ? 'detail' : 'summary'
      if (!this.config[pageKey]) this.config[pageKey] = { columns: {} }
      this.config[pageKey].viewMode = normalized
      this._bumpVersion()
      this.persist()
    },
    // 全站默认视图（2026-10-14 双视图二期）：设置中心「列表默认视图」装载后写入。
    // 2026-10-18：同时按「公司」缓存到本地 —— 刷新后首屏直接使用该值（不必等接口），
    // 服务端值仍为真相：每次进入列表页会重新拉取并覆盖缓存。
    setGlobalDefaultView(mode) {
      const normalized = mode === 'detail' ? 'detail' : 'summary'
      scopedSet(GLOBAL_VIEW_KEY, normalized, false)
      if (this.globalDefaultView === normalized) return
      this.globalDefaultView = normalized
      this._bumpVersion()
    },
    // 设置单列宽度（用户拖拽表头边界后调用；width 传 null 表示清除该列的宽度记忆）
    //
    // ⚠ 这里**故意不 bumpVersion**（与其它写操作不同）：
    //   各表格容器都把 configVersion 绑在 el-table 的 :key 上（列显隐/顺序变化时强制重建表格）。
    //   若列宽也自增版本号，用户每拖一下表头就会触发一次表格重建 → 刚拖好的宽度当场被抹回默认
    //   （2026-09-22 实测：拖完宽度不变，刷新后才生效）。列宽由 el-table 自身布局维护，
    //   不需要 Vue 侧重渲染；下次打开页面时由 applySavedWidths() 统一套用即可。
    setColumnWidth(pageKey, prop, width) {
      if (!pageKey || !prop) return
      if (!this.config[pageKey]) this.config[pageKey] = { columns: {} }
      if (!this.config[pageKey].columns[prop]) this.config[pageKey].columns[prop] = {}
      if (width === null || width === undefined) delete this.config[pageKey].columns[prop].width
      else this.config[pageKey].columns[prop].width = width
      this.persist()
    },
    // 清除某页面全部列宽记忆（列设置里的「恢复默认列宽」）
    resetColumnWidths(pageKey) {
      if (!pageKey) return
      const cols = this.config[pageKey]?.columns
      if (!cols) return
      Object.keys(cols).forEach(prop => { delete cols[prop].width })
      this._bumpVersion()
      this.persist()
    },
    // 设置单列显隐
    setColumnVisible(pageKey, prop, visible) {
      if (!pageKey || !prop) return
      if (!this.config[pageKey]) this.config[pageKey] = { columns: {} }
      if (!this.config[pageKey].columns[prop]) this.config[pageKey].columns[prop] = {}
      this.config[pageKey].columns[prop].visible = visible
      this._bumpVersion()
      this.persist()
    },
    // 设置单列固定方式（false | 'left' | 'right'）
    setColumnFixed(pageKey, prop, fixed) {
      if (!pageKey || !prop) return
      if (!this.config[pageKey]) this.config[pageKey] = { columns: {} }
      if (!this.config[pageKey].columns[prop]) this.config[pageKey].columns[prop] = {}
      this.config[pageKey].columns[prop].fixed = fixed
      this._bumpVersion()
      this.persist()
    },
    // 设置单列顺序
    setColumnOrder(pageKey, prop, order) {
      if (!pageKey || !prop) return
      if (!this.config[pageKey]) this.config[pageKey] = { columns: {} }
      if (!this.config[pageKey].columns[prop]) this.config[pageKey].columns[prop] = {}
      this.config[pageKey].columns[prop].order = order
      this._bumpVersion()
      this.persist()
    },
    // 批量更新列顺序（拖拽完成后调用）
    // orderedProps: ['orderNo', 'orderDate', ...] 按新顺序排列的 prop 数组
    batchUpdateOrder(pageKey, orderedProps) {
      if (!pageKey || !Array.isArray(orderedProps)) return
      if (!this.config[pageKey]) this.config[pageKey] = { columns: {} }
      orderedProps.forEach((prop, idx) => {
        if (!this.config[pageKey].columns[prop]) this.config[pageKey].columns[prop] = {}
        this.config[pageKey].columns[prop].order = idx
      })
      this._bumpVersion()
      this.persist()
    },
    // 重置某页面为默认配置
    resetPage(pageKey) {
      if (!pageKey) return
      delete this.config[pageKey]
      this._bumpVersion()
      this.persist()
    },
    // 重置全部配置
    resetAll() {
      this.config = {}
      this._bumpVersion()
      this.persist()
    },
    persist() {
      scopedSetJSON(STORAGE_KEY, this.config)
    },
    /**
     * 身份/公司变化时，按新作用域把配置重新装载进内存（登出 → 空；换用户/换公司 → 对方自己的那份）。
     * 必须做：store 是进程内单例，只换存储 key 而不改内存，页面上仍是上一个用户的值。
     * 由 utils/sessionCacheReset.js 在登出/登录/切公司时统一调用。
     */
    reloadForScope() {
      this.config = loadFromStorage()
      // 全站默认视图按「公司」缓存：切公司后必须先回到新公司的缓存值，
      // 再等列表页从后端装载（否则会短暂沿用上一家公司/上一个默认值）
      this.globalDefaultView = loadGlobalDefaultView()
      this._bumpVersion()
    }
  }
})
