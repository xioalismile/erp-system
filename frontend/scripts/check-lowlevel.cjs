/**
 * 低级缺陷静态自查（不需要启动服务，秒级）
 *
 * 只管四类"低级问题"，不碰业务逻辑：
 *   1) 模板里绑定的方法在 script 里不存在（按钮点了没反应 / 改名漏改）
 *   2) 同一文件里函数重复声明（批量改代码后常见的覆盖）
 *   3) 后端空实现 / 未实现占位（接口 200 但什么都不做）
 *   4) 模板中硬编码中文（不阻断，只提示，方便补 $tt）
 *
 * 用法：npm run check:lowlevel
 * 退出码：1 = 存在 1/2/3 类问题；0 = 只有提示项或全部通过
 */
const fs = require('fs')
const path = require('path')

const ROOT = path.resolve(__dirname, '..')
const SRC = path.join(ROOT, 'src')
const BACKEND = path.resolve(ROOT, '..', 'backend', 'src', 'main', 'java')

const GLOBALS = new Set([
  '$event', '$refs', '$router', '$route', '$emit', '$slots', '$attrs', '$props', '$nextTick',
  'Math', 'Number', 'String', 'Boolean', 'JSON', 'Object', 'Array', 'Date', 'Promise', 'RegExp',
  'Map', 'Set', 'Intl', 'URLSearchParams', 'parseInt', 'parseFloat', 'isNaN', 'isFinite',
  'encodeURIComponent', 'decodeURIComponent', 'setTimeout', 'clearTimeout', 'setInterval',
  'clearInterval', 'window', 'document', 'localStorage', 'sessionStorage', 'console',
  'alert', 'confirm', 'undefined', 'null', 'true', 'false', 'this', 'typeof', 'new',
  'if', 'else', 'return', 'await', 'async', 'function', 'in', 'of', 'instanceof', 'void', 'delete'
])

function walk(dir, out = []) {
  for (const name of fs.readdirSync(dir)) {
    const p = path.join(dir, name)
    const st = fs.statSync(p)
    if (st.isDirectory()) walk(p, out)
    else if (p.endsWith('.vue')) out.push(p)
  }
  return out
}

function collectDefined(script, tpl) {
  const defined = new Set()
  const add = (n) => { if (n && /^[A-Za-z_$][\w$]*$/.test(n)) defined.add(n) }

  // 去掉行注释，避免解构块里的说明文字干扰（不影响字符串内的 //，解构块里不会有 URL）
  const code = script.replace(/^\s*\/\/.*$/gm, '')

  for (const m of code.matchAll(/\bfunction\s+([A-Za-z_$][\w$]*)/g)) add(m[1])
  for (const m of code.matchAll(/\b(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=/g)) add(m[1])
  for (const m of code.matchAll(/\b(?:async\s+)?([A-Za-z_$][\w$]*)\s*:\s*(?:async\s*)?\(/g)) add(m[1])
  // Options API / 对象方法简写：`onAction (a) {`（有缩进、后接参数与花括号，不会是普通调用）
  for (const m of code.matchAll(/^\s{2,}(?:async\s+)?([A-Za-z_$][\w$]*)\s*\([^)]*\)\s*\{/gm)) add(m[1])
  for (const m of code.matchAll(/\b(?:const|let|var)\s*\{([^}]+)\}\s*=/g)) {
    m[1].split(',').forEach((x) => add(x.split(':').pop().replace(/=.*$/, '').replace(/\/\/.*$/, '').trim()))
  }
  // defineProps 的属性在模板里可直接按名字使用
  for (const m of code.matchAll(/defineProps\s*(?:<[^(]*>)?\s*\(\s*\{([\s\S]*?)\}\s*\)/g)) {
    for (const k of m[1].matchAll(/^\s*([A-Za-z_$][\w$]*)\s*:/gm)) add(k[1])
  }
  // defineProps(['a','b'])
  for (const m of code.matchAll(/defineProps\s*\(\s*\[([^\]]+)\]/g)) {
    m[1].split(',').forEach((x) => add(x.trim().replace(/['"]/g, '')))
  }
  for (const m of code.matchAll(/import\s+([^;]+?)\s+from\s+['"]/g)) {
    const seg = m[1]
    const named = seg.match(/\{([^}]+)\}/)
    if (named) named[1].split(',').forEach((x) => add(x.split(/\s+as\s+/).pop().trim()))
    const def = seg.replace(/\{[^}]*\}/, '').replace(/,/g, ' ').trim()
    def.split(/\s+/).filter(Boolean).forEach(add)
    for (const star of seg.matchAll(/\*\s+as\s+([A-Za-z_$][\w$]*)/g)) add(star[1])
  }
  // 模板作用域变量：v-for="row in list"、#default="{ row }"、v-slot="{ row }"
  for (const m of tpl.matchAll(/v-for\s*=\s*"\(?\s*([A-Za-z_$][\w$]*)/g)) add(m[1])
  for (const m of tpl.matchAll(/(?:#default|v-slot(?::[\w-]+)?)\s*=\s*"\{([^}]+)\}"/g)) {
    m[1].split(',').forEach((x) => add(x.split(':').pop().trim()))
  }
  return defined
}

function extractHandlerName(expr) {
  const src = expr.trim()
  if (!src || src.includes('=>')) return null
  if (/^['"`]/.test(src)) return null
  if (/^[(!]/.test(src)) return null
  if (/^[A-Za-z_$][\w$]*\s*=(?!=)/.test(src)) return null
  const m = src.match(/^([A-Za-z_$][\w$]*)\s*(\(|\.|$)/)
  return m ? m[1] : null
}

const problems = []
const hints = []

for (const file of walk(SRC)) {
  const rel = path.relative(ROOT, file).replace(/\\/g, '/')
  const text = fs.readFileSync(file, 'utf8')
  const rawTpl = (text.match(/<template>([\s\S]*)<\/template>/) || [])[1] || ''
  // 注释里的用法示例会包含 @query="handleSearch" 这类绑定，先剔除，避免误报
  const tpl = rawTpl.replace(/<!--[\s\S]*?-->/g, '')
  const script = (text.match(/<script[^>]*>([\s\S]*?)<\/script>/) || [])[1] || ''
  if (!script) continue

  // 1) 模板事件处理函数未定义
  const defined = collectDefined(script, tpl)
  const seen = new Set()
  for (const m of tpl.matchAll(/@[A-Za-z:.-]+\s*=\s*"([^"]*)"/g)) {
    const name = extractHandlerName(m[1])
    if (!name || seen.has(name)) continue
    seen.add(name)
    if (GLOBALS.has(name) || defined.has(name)) continue
    problems.push({ type: '未定义的事件处理函数', file: rel, name, expr: m[1].trim().slice(0, 80) })
  }

  // 2) 同一文件函数重复声明
  const counts = new Map()
  for (const m of script.matchAll(/\bfunction\s+([A-Za-z_$][\w$]*)\s*\(/g)) {
    counts.set(m[1], (counts.get(m[1]) || 0) + 1)
  }
  for (const [name, n] of counts) {
    if (n > 1) problems.push({ type: '函数重复声明', file: rel, name, count: n })
  }

  // 4) 模板裸中文（提示）
  const stripped = tpl
    .replace(/\{\{[\s\S]*?\}\}/g, '')
    .replace(/:[\w-]+\s*=\s*"[^"]*"/g, '')
    .replace(/@[\w:.-]+\s*=\s*"[^"]*"/g, '')
    .replace(/<!--[\s\S]*?-->/g, '')
  const cn = stripped.match(/[一-龥]{2,}/g) || []
  if (cn.length) hints.push({ type: '模板硬编码中文', file: rel, count: cn.length, sample: [...new Set(cn)].slice(0, 3).join('/') })
}

// 3) 后端空实现 / 未实现占位
function walkJava(dir, out = []) {
  if (!fs.existsSync(dir)) return out
  for (const name of fs.readdirSync(dir)) {
    const p = path.join(dir, name)
    const st = fs.statSync(p)
    if (st.isDirectory()) walkJava(p, out)
    else if (p.endsWith('.java')) out.push(p)
  }
  return out
}
for (const file of walkJava(BACKEND)) {
  const rel = path.relative(path.resolve(ROOT, '..'), file).replace(/\\/g, '/')
  const text = fs.readFileSync(file, 'utf8')
  const lines = text.split(/\r?\n/)
  lines.forEach((line, i) => {
    if (/UnsupportedOperationException|暂未实现|尚未实现|未实现，|待实现/.test(line)) {
      hints.push({ type: '后端未实现占位（确认是否设计如此）', file: rel, line: i + 1, text: line.trim().slice(0, 100) })
    }
  })
  // 空方法体（无注释、无语句）：排除 record、接口/抽象类、@Override 钩子
  const isAbstractFile = /\b(?:abstract\s+class|interface)\b/.test(text)
  if (!isAbstractFile) {
    for (const m of text.matchAll(/(?:public|protected|private)\s+(?:static\s+)?[\w<>\[\], ?]+\s+([A-Za-z_$][\w$]*)\s*\([^)]*\)\s*(?:throws[^{]+)?\{\s*\}/g)) {
      const name = m[1]
      if (/^(get|set|is)/.test(name)) continue
      if (new RegExp('\\brecord\\s+' + name + '\\s*\\(').test(text)) continue
      const before = text.slice(Math.max(0, m.index - 220), m.index)
      if (/@Override\s*$/.test(before.trimEnd()) || /@Override[^@]*$/.test(before)) continue
      problems.push({ type: '后端空方法', file: rel, name })
    }
  }
}

// 输出
const byType = {}
for (const p of problems) (byType[p.type] = byType[p.type] || []).push(p)

console.log('\n===== 低级缺陷静态自查 =====\n')
if (!problems.length) console.log('通过：未发现未定义处理器 / 重复函数 / 空实现占位。')
for (const [type, list] of Object.entries(byType)) {
  console.log(`\n[${type}] ${list.length} 处`)
  for (const p of list.slice(0, 40)) {
    console.log('  - ' + [p.file, p.name, p.line ? `第${p.line}行` : '', p.expr || p.text || '', p.count ? `×${p.count}` : ''].filter(Boolean).join(' | '))
  }
  if (list.length > 40) console.log(`  …… 其余 ${list.length - 40} 处省略`)
}
if (hints.length) {
  console.log(`\n[提示] ${hints.length} 项（不阻断，逐条确认是否设计如此）`)
  for (const h of hints.slice(0, 20)) {
    console.log('  - ' + [h.file, h.line ? `第${h.line}行` : '', h.text || '', h.type === '模板硬编码中文' ? `${h.count} 处` : ''].filter(Boolean).join(' | '))
  }
}
console.log('')
process.exit(problems.length ? 1 : 0)
