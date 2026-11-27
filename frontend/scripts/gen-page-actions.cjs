/* eslint-disable no-console */
/**
 * 页面动作注册表扫描脚本
 * 扫描 src/views/**\/*.vue，收集每个页面的 perm('xxx') 调用，
 * 递归合并该页面 import 的组件里的 perm('xxx') 调用，
 * 按「模块:对象」前缀聚合，生成 src/config/pageActions.generated.js。
 *
 * 前缀来源：
 *   1. 优先从 usePagePerm('prefix') 提取显式前缀
 *   2. 若 usePagePerm() 不传参，从文件路径推断（如 views/production/MrpPlanList.vue → production:mrp-plan）
 */
const fs = require('fs')
const path = require('path')

const ROOT = path.resolve(__dirname, '..')
const SRC = path.join(ROOT, 'src')
const VIEWS_DIR = path.join(SRC, 'views')
const COMPONENTS_DIR = path.join(SRC, 'components')
const OUTPUT = path.join(SRC, 'config', 'pageActions.generated.js')

/** 动作码别名归一：页面/组件里用 approve，设置中心词表里叫 audit（与 billPermCode.ACTION_BUTTON_SUFFIX 对齐） */
const ACTION_ALIAS = { approve: 'audit' }

/** 收集一个文件里所有 perm('xxx') / permCode('xxx') 的动作码 */
function collectPerms(content) {
  const set = new Set()
  // 模式1：perm('add') / this.perm('add') 函数调用
  const re1 = /perm\(\s*['"]([a-zA-Z][a-zA-Z0-9_-]*)['"]\s*\)/g
  let m
  while ((m = re1.exec(content)) !== null) set.add(m[1])
  // 模式2：permCode('submit') —— 单据动作组件（BillActions/BillRowActions）按 billType 动态拼码，
  //        但动作名是字面量，可静态提取
  const re1b = /permCode\(\s*['"]([a-zA-Z][a-zA-Z0-9_-]*)['"]\s*\)/g
  while ((m = re1b.exec(content)) !== null) set.add(m[1])
  // 模式3：模板属性 perm-add="basedata:customer:add" / :perm-add="..."
  // 提取 perm- 后面的动作名（kebab-case 转 camelCase）
  const re2 = /:?perm-([a-z][a-z0-9-]*)\s*=\s*["'][^"']*["']/g
  while ((m = re2.exec(content)) !== null) {
    const raw = m[1]
    const action = raw.replace(/-([a-z])/g, (_, c) => c.toUpperCase())
    set.add(action)
  }
  // 别名归一（approve → audit）
  const normalized = new Set()
  for (const a of set) normalized.add(ACTION_ALIAS[a] || a)
  return normalized
}

/** 从 perm-xxx="模块:对象:动作" 属性中提取前缀（取第一段:第二段） */
function extractPrefixFromPermProps(content) {
  const re = /:?perm-[a-z][a-z0-9-]*\s*=\s*["']([a-zA-Z][a-zA-Z0-9_-]*):([a-zA-Z][a-zA-Z0-9_-]*):/g
  const prefixes = new Set()
  let m
  while ((m = re.exec(content)) !== null) {
    prefixes.add(`${m[1]}:${m[2]}`)
  }
  return prefixes.size === 1 ? Array.from(prefixes)[0] : null
}

/** 解析文件 import 的 .vue 组件路径（支持相对路径和 @/ 别名） */
function collectImports(content) {
  const paths = []
  const re = /import\s+\w+\s+from\s+['"](\.{1,2}\/[^'"]+\.vue|@\/[^'"]+\.vue)['"]/g
  let m
  while ((m = re.exec(content)) !== null) paths.push(m[1])
  return paths
}

/** 将 import 路径转为绝对路径（支持 @/ → src/） */
function resolveImport(impPath, fromFile) {
  if (impPath.startsWith('@/')) {
    return path.join(SRC, impPath.slice(2))
  }
  return path.resolve(path.dirname(fromFile), impPath)
}

/** 组件文件 → 动作集合（带缓存） */
const componentCache = new Map()
function getComponentActions(compAbsPath) {
  if (componentCache.has(compAbsPath)) return componentCache.get(compAbsPath)
  if (!fs.existsSync(compAbsPath)) {
    componentCache.set(compAbsPath, new Set())
    return new Set()
  }
  let content
  try {
    content = fs.readFileSync(compAbsPath, 'utf-8')
  } catch (e) {
    componentCache.set(compAbsPath, new Set())
    return new Set()
  }
  const actions = collectPerms(content)
  const imports = collectImports(content)
  for (const imp of imports) {
    const sub = resolveImport(imp, compAbsPath)
    const subActions = getComponentActions(sub)
    subActions.forEach(a => actions.add(a))
  }
  componentCache.set(compAbsPath, actions)
  return actions
}

/** 从文件路径推断前缀：views/production/MrpPlanList.vue → production:mrp-plan */
function inferPrefixFromPath(filePath) {
  const rel = path.relative(VIEWS_DIR, filePath).replace(/\\/g, '/')
  const parts = rel.split('/')
  if (parts.length < 2) return null
  const module = parts[0]
  let obj = parts[parts.length - 1].replace(/\.vue$/, '')
  // 去掉常见后缀
  obj = obj.replace(/List$/, '').replace(/Detail$/, '').replace(/Form$/, '')
  // CamelCase → kebab-case
  obj = obj.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase()
  return `${module}:${obj}`
}

/** 提取 usePagePerm('prefix') 的显式前缀 */
function extractExplicitPrefix(content) {
  const m = content.match(/usePagePerm\(\s*['"]([^'"]+)['"]\s*\)/)
  return m ? m[1] : null
}

/** 遍历目录下所有 .vue 文件 */
function walk(dir) {
  const out = []
  if (!fs.existsSync(dir)) return out
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) out.push(...walk(full))
    else if (entry.name.endsWith('.vue')) out.push(full)
  }
  return out
}

/** 主流程 */
function main() {
  const pageActions = {}
  const viewFiles = walk(VIEWS_DIR)

  for (const file of viewFiles) {
    let content
    try {
      content = fs.readFileSync(file, 'utf-8')
    } catch (e) {
      continue
    }
    let prefix = extractPrefixFromPermProps(content)
    if (!prefix) prefix = extractExplicitPrefix(content)
    if (!prefix) prefix = inferPrefixFromPath(file)
    if (!prefix) continue

    const actions = collectPerms(content)
    const imports = collectImports(content)
    for (const imp of imports) {
      const compAbs = resolveImport(imp, file)
      const compActions = getComponentActions(compAbs)
      compActions.forEach(a => actions.add(a))
    }

    if (!pageActions[prefix]) pageActions[prefix] = new Set()
    actions.forEach(a => pageActions[prefix].add(a))
  }

  const lines = []
  lines.push('/* AUTO-GENERATED by scripts/gen-page-actions.cjs — DO NOT EDIT MANUALLY */')
  lines.push('/* eslint-disable */')
  lines.push('export const PAGE_ACTIONS = {')
  Object.keys(pageActions).sort().forEach(prefix => {
    const acts = Array.from(pageActions[prefix]).sort()
    lines.push(`  '${prefix}': ${JSON.stringify(acts)},`)
  })
  lines.push('}')
  lines.push('')
  lines.push('export default PAGE_ACTIONS')
  lines.push('')

  // 确保 config 目录存在
  const configDir = path.dirname(OUTPUT)
  if (!fs.existsSync(configDir)) fs.mkdirSync(configDir, { recursive: true })

  fs.writeFileSync(OUTPUT, lines.join('\n'), 'utf-8')
  const count = Object.keys(pageActions).length
  const totalActions = Object.values(pageActions).reduce((s, set) => s + set.size, 0)
  console.log(`[gen-page-actions] 扫描完成：${count} 个页面，共 ${totalActions} 个动作条目，已写入 src/config/pageActions.generated.js`)
}

// 扫描失败不阻断 dev/build：捕获异常后仅告警，沿用上次生成的文件
try {
  main()
} catch (e) {
  console.warn(`[gen-page-actions] 扫描失败，沿用上次生成的注册表：${e && e.message ? e.message : e}`)
}
