/**
 * 列表/报表「露出数字主键」体检脚本（防复发，2026-09-27 建立）
 *
 * 为什么需要：系统很多表格列直接绑定了 xxxId（如 settleModeId / currencyId / warehouseId /
 * materialId / supplierId），页面上会显示成数字编号，而业务上应显示名称。这类问题编译期不报错、
 * 运行时也不报错，只能靠人眼或本脚本发现。本脚本把它变成可机械发现的回归基线。
 *
 * 用法（在 frontend 目录下执行）：
 *   node scripts/check-id-columns.cjs
 * 退出码：0 = 无"必然/疑似"问题（或仅剩需人工确认项）；1 = 发现列名里直接写了「ID」的必然问题。
 *
 * 判定规则：
 *   规则①（el-table-column 标签）：prop 以 Id 结尾且标签体内没有 `#default` 自定义渲染、
 *                                 标签上也没有 `:formatter` → WARN（疑似直接显示编号）
 *   规则②（列定义对象）：`{ prop: 'xxxId', label: '...ID...' }` → ERROR（列名写死「ID」，必然露出编号）
 *   规则③（列定义对象）：`{ prop: 'xxxId' }` 且既无 slot/type/format 也无 formatter → INFO（需人工确认）
 */

const fs = require('fs')
const path = require('path')

const SRC_ROOT = path.resolve(__dirname, '../src')

/** 已知豁免：本地库无菜单入口的遗留孤儿页（待下线），命中后降级为 INFO 不计入失败 */
const IGNORE_FILES = [
  /views[\\/]inventory[\\/](WarehouseList|LocationList)\.vue$/,
  /views[\\/]basedata[\\/]LocationList\.vue$/
]

function walk(dir, out = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) walk(full, out)
    else if (entry.name.endsWith('.vue')) out.push(full)
  }
  return out
}

const problems = []

/** 规则①：<el-table-column prop="xxxId" ...> 且无自定义渲染 */
function checkElTableColumns(text, rel) {
  const tagRe = /<el-table-column\b([\s\S]*?)(\/>|>)/g
  let m
  while ((m = tagRe.exec(text)) !== null) {
    const attrs = m[1]
    const selfClose = m[2] === '/>'
    const propM = /(?:^|\s):?prop\s*=\s*(?:"([^"]+)"|'([^']+)')/.exec(attrs)
    if (!propM) continue
    const prop = (propM[1] || propM[2] || '').trim()
    if (!/Id$/.test(prop)) continue
    let body = ''
    if (!selfClose) {
      const closeIdx = text.indexOf('</el-table-column>', tagRe.lastIndex)
      body = closeIdx === -1 ? '' : text.slice(tagRe.lastIndex, closeIdx)
    }
    const hasDefault = /<template\s+#default/.test(body)
    const hasFormatter = /:formatter\s*=/.test(attrs) || /(?:^|\s)formatter\s*=/.test(attrs)
    if (hasDefault || hasFormatter) continue
    const line = text.slice(0, m.index).split('\n').length
    problems.push({ level: 'WARN', file: rel, line, msg: `表格列 prop="${prop}" 未提供自定义渲染/格式化，可能直接显示数字主键` })
  }
}

/** 规则②③：列定义对象 `{ prop: 'xxxId', ... }` */
function checkColumnDefs(text, rel) {
  const objRe = /\{[^{}]*?prop\s*:\s*['"]([A-Za-z0-9_]*Id)['"][^{}]*?\}/gs
  let m
  while ((m = objRe.exec(text)) !== null) {
    const body = m[0]
    const prop = m[1]
    const line = text.slice(0, m.index).split('\n').length
    const labelHasID = /label\s*:\s*['"][^'"]*\bID\b[^'"]*['"]/.test(body)
    // 明细列改由通用表格组件渲染时，页面会提供 `#cell-<prop>` 插槽显示名称 → 视为已渲染
    const hasCellSlot = text.indexOf('#cell-' + prop) !== -1
    const hasRender = /(slot|type|format|formatter|render|options|optionsFn|editor|editable)\s*:/.test(body) || hasCellSlot
    if (labelHasID) {
      problems.push({ level: 'ERROR', file: rel, line, msg: `列定义 prop:'${prop}' 的列名里写了「ID」，必然显示编号` })
    } else if (!hasRender) {
      problems.push({ level: 'INFO', file: rel, line, msg: `列定义 prop:'${prop}' 无 slot/type/format/formatter，疑似直接显示编号` })
    }
  }
}

const files = walk(SRC_ROOT)
for (const file of files) {
  const rel = path.relative(path.resolve(__dirname, '..'), file).replace(/\\/g, '/')
  const ignored = IGNORE_FILES.some(re => re.test(file))
  const text = fs.readFileSync(file, 'utf8')
  if (!/prop\s*[:=]/.test(text)) continue
  const before = problems.length
  checkElTableColumns(text, rel)
  checkColumnDefs(text, rel)
  if (ignored) {
    for (let i = before; i < problems.length; i++) problems[i].msg += '｜已豁免：遗留孤儿页'
    for (let i = before; i < problems.length; i++) if (problems[i].level === 'ERROR') problems[i].level = 'WARN'
  }
}

const errors = problems.filter(p => p.level === 'ERROR')
const warns = problems.filter(p => p.level === 'WARN')
const infos = problems.filter(p => p.level === 'INFO')

if (errors.length === 0 && warns.length === 0) {
  console.log(`[列表主键体检] PASS —— 扫描 ${files.length} 个 .vue，未发现必然/疑似露出数字主键的列。`)
  if (infos.length) console.log(`（另有 ${infos.length} 条 INFO 需人工确认，不阻断）`)
  process.exit(0)
}

console.log(`[列表主键体检] 共 ${problems.length} 处（扫描 ${files.length} 个 .vue）：ERROR ${errors.length} / WARN ${warns.length} / INFO ${infos.length}\n`)
for (const p of [...errors, ...warns, ...infos]) {
  console.log(`  ${p.level}  ${p.file}:${p.line}\n        ${p.msg}`)
}
console.log('\n提示：ERROR = 列名写死「ID」，必然显示编号，必须修；WARN = 疑似直接显示编号，需人工确认；INFO = 待人工判定是否已有其它渲染。')
process.exit(errors.length > 0 ? 1 : 0)
