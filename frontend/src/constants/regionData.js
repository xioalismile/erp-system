/**
 * 全国省 / 市 / 区三级行政区划（获客采集「地区筛选」专用）
 *
 * 数据源：constants/region-pca.json（31 省 / 342 市 / 3056 区县，仅名称无编码）
 * 加载策略：三级数据约 47KB，采用动态 import 懒加载——只有打开采集任务弹窗时才拉取，不拖慢首屏。
 *
 * 用途：
 *  1. loadRegionOptions() → el-cascader 的 :options
 *  2. joinAreaPath()       → 级联路径数组转存储文本（'广东省广州市白云区'）
 *  3. parseAreaPath()      → 历史手输文本回显成级联路径（解析不出返回 []，调用方必须保留原文本，不允许清空）
 */
let regionTree = null
let loadingPromise = null

const toTree = (raw) =>
  Object.keys(raw).map((prov) => ({
    value: prov,
    label: prov,
    children: Object.keys(raw[prov] || {}).map((city) => ({
      value: city,
      label: city,
      children: (raw[prov][city] || []).map((dist) => ({ value: dist, label: dist }))
    }))
  }))

/** 懒加载省市区级联树（结果缓存，重复打开弹窗不重复请求） */
export const loadRegionOptions = () => {
  if (regionTree) return Promise.resolve(regionTree)
  if (!loadingPromise) {
    loadingPromise = import('./region-pca.json').then((mod) => {
      regionTree = toTree(mod.default || mod)
      return regionTree
    })
  }
  return loadingPromise
}

/** 已加载的级联树（未加载时为 null，调用方需先 await loadRegionOptions） */
export const getRegionOptions = () => regionTree

/** 级联路径数组 → 存储文本：['广东省','广州市','白云区'] → '广东省广州市白云区' */
export const joinAreaPath = (path) => (Array.isArray(path) ? path.filter(Boolean).join('') : '')

/** 在候选列表里挑最匹配的一项：先全等，再「被 text 包含」（取最长名，避免短名误命中） */
const pickLevel = (list, text) => {
  const exact = list.find((x) => x.value === text)
  if (exact) return exact
  const contained = list.filter((x) => text.includes(x.value))
  if (contained.length) return contained.sort((a, b) => b.value.length - a.value.length)[0]
  return null
}

/** 从已命中的层级继续向下细化（如已命中「广东省」，再补出「广州市」「白云区」） */
const refineDown = (path, text, tree) => {
  let children = null
  if (path.length === 1) {
    children = tree.find((p) => p.value === path[0])?.children
  } else if (path.length === 2) {
    children = tree.find((p) => p.value === path[0])?.children?.find((c) => c.value === path[1])?.children
  }
  if (!children || !children.length) return path
  const hit = pickLevel(children, text)
  if (!hit) return path
  path.push(hit.value)
  return refineDown(path, text, tree)
}

/**
 * 把历史手输的地区文本解析成级联路径（尽力而为）
 * 可解析示例：'广东省广州市白云区' / '广州白云区' / '白云区' / '深圳市' / '广东省'
 * 解析不出时返回 []，此时调用方必须保留原文本原样提交，严禁清空
 */
export const parseAreaPath = (text, tree) => {
  const t = String(text || '').trim()
  const options = tree || regionTree
  if (!t || !options || !options.length) return []

  // 1) 区县全等（最精确，优先）
  for (const prov of options) {
    for (const city of prov.children || []) {
      for (const dist of city.children || []) {
        if (dist.value === t) return [prov.value, city.value, dist.value]
      }
    }
  }
  // 2) 省 → 市 → 区 逐级向下匹配
  const provHit = pickLevel(options, t)
  if (provHit) return refineDown([provHit.value], t, options)

  // 3) 兜底：只给了市名/区县名（如 '白云区'、'深圳市'），反向定位其所属省
  for (const prov of options) {
    for (const city of prov.children || []) {
      for (const dist of city.children || []) {
        if (t.includes(dist.value)) return [prov.value, city.value, dist.value]
      }
      if (t.includes(city.value)) return [prov.value, city.value]
    }
  }
  return []
}
