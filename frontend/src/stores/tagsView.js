import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'

/**
 * 多标签页 Store
 *
 * 职责：
 * 1. 维护已打开的标签页列表（affix 固定标签 + 动态标签）
 * 2. 维护当前激活的标签（fullPath）
 * 3. 维护 keep-alive 需要缓存的组件 name 列表（与打开的标签一一对应）
 *
 * 标签唯一标识：route.fullPath（区分 /sale/orders/edit/1 与 /sale/orders/edit/2）
 * 缓存唯一标识：route.name（同名组件复用一个缓存实例）
 *
 * 关闭策略：
 *   - 关闭当前激活标签：优先跳转右侧，无则左侧，再无则跳工作台
 *   - 关闭其他：保留当前激活标签 + affix 标签
 *   - 全部关闭：仅保留 affix 标签，跳转到首个 affix 标签
 */
export const useTagsViewStore = defineStore('tagsView', () => {
  /** 已打开标签列表，每项: { fullPath, name, title, affix } */
  const visitedViews = ref([])

  /** 当前激活的 fullPath */
  const activeFullPath = ref('')

  /** keep-alive include 列表（组件 name，对应 route.name） */
  const cachedViewNames = ref([])

  /**
   * 新增页"重开序号"：key=fullPath → 递增整数
   * 用途：MainLayout 把它拼进 keep-alive 的 key；序号变化 → 挂载全新组件实例。
   * 背景：新增页 fullPath 恒定（如 /basedata/material/new），二次点「新增」会命中
   *      同一个缓存 key → 复用上次实例 → 表单残留上次数据（bug）。
   */
  const viewNonce = ref({})

  /** 新增页"脏标记"：用户在该新增页内有输入/选择 → 记 true；重开前据此弹确认框，避免静默丢草稿 */
  const dirtyPaths = ref({})

  /** 最近一次"点标签页切换"的目标路径（区分：点标签切回=保留草稿 / 点新增按钮=要全新页） */
  let activating = { path: '', at: 0 }

  /** 固定标签（不可关闭） */
  const affixViews = ref([])

  /** 当前激活的 view 对象 */
  const activeView = computed(() =>
    visitedViews.value.find(v => v.fullPath === activeFullPath.value) || null
  )

  let router = null
  let route = null

  /**
   * 初始化：注入 router/route 实例，监听路由变化自动添加标签，扫描 affix 路由
   * 必须在 MainLayout 的 onMounted 中调用一次。
   */
  function init(routerInstance, routeInstance) {
    router = routerInstance
    route = routeInstance

    // 1. 扫描所有路由，收集 meta.affix=true 的固定标签
    const allRoutes = router.getRoutes()
    affixViews.value = allRoutes
      .filter(r => r.meta && r.meta.affix)
      .map(r => ({
        fullPath: r.path,
        name: r.name,
        title: (r.meta && r.meta.title) || r.name || r.path,
        affix: true
      }))
    // 去重并初始化固定标签
    affixViews.value.forEach(v => {
      if (!visitedViews.value.find(x => x.fullPath === v.fullPath)) {
        visitedViews.value.push(v)
      }
      if (v.name && !cachedViewNames.value.includes(v.name)) {
        cachedViewNames.value.push(v.name)
      }
    })

    // 2. 监听路由变化：自动添加新标签 + 更新激活项
    watch(
      () => route.fullPath,
      (fullPath) => {
        if (!fullPath || fullPath === '/login' || fullPath === '/tenant-select' || fullPath === '/403') {
          return
        }
        // 跳过 /redirect 中转页
        if (fullPath.startsWith('/redirect')) {
          return
        }
        activeFullPath.value = fullPath
        addView({
          fullPath,
          name: route.name,
          title: (route.meta && route.meta.title) || route.name || fullPath,
          affix: !!(route.meta && route.meta.affix)
        })
      },
      { immediate: true }
    )
  }

  /** 添加一个标签（若已存在则不重复添加），同步加入缓存列表 */
  function addView(view) {
    if (!view || !view.fullPath) return
    const existing = visitedViews.value.find(v => v.fullPath === view.fullPath)
    if (existing) {
      // 已存在：仅更新标题
      if (view.title && existing.title !== view.title) {
        existing.title = view.title
      }
    } else {
      visitedViews.value.push({ ...view })
    }
    // 加入缓存列表（按 route.name）
    if (view.name && !cachedViewNames.value.includes(view.name)) {
      cachedViewNames.value.push(view.name)
    }
  }

  /**
   * 关闭指定标签
   * @param {Object} view 要关闭的标签对象
   * @returns {Promise<boolean>} 若关闭的是激活标签则跳转并返回 true
   */
  async function closeView(view) {
    if (!view || view.affix) return false
    const idx = visitedViews.value.findIndex(v => v.fullPath === view.fullPath)
    if (idx < 0) return false

    const isActive = view.fullPath === activeFullPath.value
    // 先从列表移除
    visitedViews.value.splice(idx, 1)
    // 标签已关闭 → 其未保存标记一并清理，避免下次打开误弹"未保存"确认框
    clearDirty(view.fullPath)

    // 若该组件 name 已无其他打开标签引用，从缓存中移除
    if (view.name) {
      const stillUsed = visitedViews.value.some(v => v.name === view.name)
      if (!stillUsed) {
        const cnIdx = cachedViewNames.value.indexOf(view.name)
        if (cnIdx >= 0) cachedViewNames.value.splice(cnIdx, 1)
      }
    }

    if (isActive && router) {
      // 优先跳转右侧（idx 位置），无则左侧
      const next = visitedViews.value[idx] || visitedViews.value[idx - 1]
      const target = next ? next.fullPath : (affixViews.value[0] && affixViews.value[0].fullPath) || '/dashboard'
      // 关标签导致的跳转同样算"切标签"，不能触发新增页重开重建
      markTabActivate(target)
      await router.push(target)
      return true
    }
    return false
  }

  /** 关闭当前激活标签 */
  async function closeActive() {
    if (activeView.value) {
      return await closeView(activeView.value)
    }
    return false
  }

  /** 关闭其他标签：保留当前激活标签 + 所有 affix 标签 */
  function closeOthers() {
    const keep = new Set([
      activeFullPath.value,
      ...affixViews.value.map(v => v.fullPath)
    ])
    visitedViews.value = visitedViews.value.filter(v => keep.has(v.fullPath))
    // 缓存列表同步：只保留仍被引用的 name
    const usedNames = new Set(visitedViews.value.map(v => v.name).filter(Boolean))
    cachedViewNames.value = cachedViewNames.value.filter(n => usedNames.has(n))
  }

  /** 关闭全部：仅保留 affix 标签，并跳转到首个 affix 标签 */
  async function closeAll() {
    visitedViews.value = affixViews.value.map(v => ({ ...v }))
    cachedViewNames.value = affixViews.value.map(v => v.name).filter(Boolean)
    if (router) {
      const target = (affixViews.value[0] && affixViews.value[0].fullPath) || '/dashboard'
      // 关标签导致的跳转同样算"切标签"，不能触发新增页重开重建
      markTabActivate(target)
      await router.push(target)
    }
  }

  /** 切换激活标签（点击标签栏跳转） */
  function activate(fullPath) {
    // 打"点标签切换"标记：路由守卫据此判定"切回新增页=保留草稿"，不触发重开重建
    markTabActivate(fullPath)
    if (router) {
      router.push(fullPath)
    }
  }

  /** 记录"点标签页切换"的目标路径（3 秒内有效，避免标记残留误判后续导航） */
  function markTabActivate(fullPath) {
    activating = { path: fullPath || '', at: Date.now() }
  }

  /** 消费"点标签切换"标记：命中且未超时返回 true；无论是否命中都会清空标记 */
  function consumeTabActivate(fullPath) {
    const hit = !!fullPath && activating.path === fullPath && (Date.now() - activating.at) < 3000
    activating = { path: '', at: 0 }
    return hit
  }

  /** 新增页重开序号 +1：MainLayout 的 keep-alive key 随之变化 → 挂载全新空白实例 */
  function bumpViewNonce(fullPath) {
    if (!fullPath) return
    viewNonce.value[fullPath] = (viewNonce.value[fullPath] || 0) + 1
  }

  /** 取路径当前的重开序号（未重开过=0，行为与旧版完全一致） */
  function nonceOf(fullPath) {
    return (fullPath && viewNonce.value[fullPath]) || 0
  }

  /** 标记某新增页"有未保存内容"（MainLayout 监听用户在页面内输入/选择时写入） */
  function markDirty(fullPath) {
    if (!fullPath || dirtyPaths.value[fullPath]) return
    dirtyPaths.value[fullPath] = true
  }

  /** 该新增页是否有未保存内容 */
  function isDirty(fullPath) {
    return !!(fullPath && dirtyPaths.value[fullPath])
  }

  /** 清脏标记：保存成功 / 确认重开 / 关闭标签时调用 */
  function clearDirty(fullPath) {
    if (fullPath && dirtyPaths.value[fullPath]) delete dirtyPaths.value[fullPath]
  }

  /**
   * 清空全部页签与相关内存状态（登出 / 登录后身份变化时调用）。
   *
   * 【为什么必须清】本系统的"退出登录 / 登录"都是 SPA 内路由跳转（`router.push('/login')`），
   * **页面不刷新**，而 Pinia store 是进程内单例 → 上一个用户打开的单据/基础资料页签会原样
   * 留给下一个用户，连 keep-alive 的组件实例缓存、未保存草稿标记也一起留下
   * （用户实报缺陷；守则 03-3.2：全局单例缓存禁止跨用户共享）。
   *
   * 只保留 affix 固定标签（如工作台），与"全部关闭"口径一致。
   */
  function resetAll() {
    visitedViews.value = affixViews.value.map(v => ({ ...v }))
    cachedViewNames.value = affixViews.value.map(v => v.name).filter(Boolean)
    activeFullPath.value = ''
    viewNonce.value = {}
    dirtyPaths.value = {}
    activating = { path: '', at: 0 }
  }

  return {
    visitedViews,
    activeFullPath,
    cachedViewNames,
    activeView,
    init,
    addView,
    closeView,
    closeActive,
    closeOthers,
    closeAll,
    activate,
    markTabActivate,
    consumeTabActivate,
    bumpViewNonce,
    nonceOf,
    markDirty,
    isDirty,
    clearDirty,
    resetAll
  }
})
