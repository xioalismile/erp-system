/* eslint-disable no-console */
/**
 * 单据影响稽核（同类缺陷的自动扫描）
 *
 * 背景：单据审核/反审/作废/反作废/删除的业务影响（库存、回写数量、生成衍生单据）
 * 全靠各 ServiceImpl 自觉实现 BillImpactHandler 的钩子。默认钩子是空方法，
 * 漏实现不报错、不编译失败，只能在用户点出来之后才发现（销售退货不加库存即此类）。
 *
 * 本脚本只读扫描后端 ServiceImpl，按固定规则把「同类缺口」一次性点名：
 *   R1 有库存动作但四个钩子不齐（审核/反审/作废/反作废）
 *   R2 审核有实质业务影响，反审没有冲回
 *   R3 审核有实质业务影响，作废/反作废没有回收/恢复
 *   R4 库存动作写在了状态机钩子之外（独立动作，反审/作废碰不到）
 *   R5 审核会自动生成衍生单据，反审/作废没有清理
 *
 * 已知缺口放 bill-impact-baseline.json（带业务原因），脚本只对「新增缺口」返回非 0，
 * 这样先上防线、再按清单逐批清零，不会让今天的存量缺口把 CI 卡死。
 *
 * 用法：node scripts/check-bill-impact.cjs [--json]
 * 输出：docs/功能体检-单据影响.md（默认），--json 时另写同名 .json
 */
const fs = require('fs')
const path = require('path')

const ROOT = path.resolve(__dirname, '..')
const BE = path.resolve(ROOT, '..', 'backend', 'src', 'main', 'java', 'com', 'erp')
const DOCS = path.resolve(ROOT, '..', 'docs')
const BASELINE = path.join(__dirname, 'bill-impact-baseline.json')
const REGISTRY = path.join(__dirname, 'bill-impact-registry.json')
const WANT_JSON = process.argv.includes('--json')

function walk(dir, out = []) {
  if (!fs.existsSync(dir)) return out
  for (const name of fs.readdirSync(dir)) {
    const full = path.join(dir, name)
    const st = fs.statSync(full)
    if (st.isDirectory()) walk(full, out)
    else if (full.endsWith('ServiceImpl.java')) out.push(full)
  }
  return out
}

function extractMethod(txt, name) {
  const re = new RegExp('(?:public|protected|private)[^;{]*\\b' + name + '\\s*\\([^)]*\\)\\s*\\{', 'g')
  let m
  while ((m = re.exec(txt))) {
    let i = m.index + m[0].length
    let depth = 1
    while (i < txt.length && depth > 0) {
      const ch = txt[i]
      if (ch === '{') depth++
      else if (ch === '}') depth--
      i++
    }
    return txt.slice(m.index, i)
  }
  return ''
}

/** 去掉注释、日志、签名和纯括号后剩余的行数 —— 近似「这段钩子有没有干活」 */
function meaningful(body) {
  return body
    .split('\n')
    .map((l) => l.trim())
    .filter((l) => l && !l.startsWith('//') && !l.startsWith('*') && !l.startsWith('/*'))
    .filter((l) => !/^log\.(info|warn|error|debug)/.test(l))
    .filter((l) => !/^@Override/.test(l))
    .filter((l) => !/^(public|protected|private)\s/.test(l))
    .filter((l) => l !== '{' && l !== '}')
    .length
}

const STOCK = /\.(addStock|deductStock|deductStockAtCost)\s*\(/
const AUTO_GEN = /(autoPush\w*|createReceiptDraftFromSource|createPaymentDraftFromSource|createFrom\w+|generateRedInvoice\w*|saveInvoiceWithItems|saveDeliveryWithItems)\s*\(/
const CLEANUP = /(removeById|\.delete\s*\(|rollbackAuto\w*|cancelByOrder|releaseGenerated|deleteGenerated|\.set\(\s*"[^"]*"\s*,\s*null)/

/** 找到每个库存调用所在的方法名 */
function stockCallSites(txt) {
  const sites = []
  for (const c of txt.matchAll(/(\w+)\.(addStock|deductStock|deductStockAtCost)\s*\(/g)) {
    const before = txt.slice(0, c.index)
    const m = [...before.matchAll(/(?:public|protected|private)[^;{]*?\b(\w+)\s*\([^)]*\)\s*\{/g)].pop()
    sites.push({ method: m ? m[1] : '?', fn: c[2] })
  }
  return sites
}

function serviceLabel(txt) {
  const m = txt.match(/getBillTypeLabel\s*\(\)\s*\{\s*return\s+"([^"]+)"/)
  return m ? m[1] : ''
}

const HOOKS = ['onApproved', 'onReverseApproved', 'onVoided', 'onUnvoided']

const rows = []
for (const file of walk(BE)) {
  const txt = fs.readFileSync(file, 'utf8')
  if (!/extends AbstractBillService/.test(txt)) continue
  const service = path.basename(file, 'ServiceImpl.java')
  const label = serviceLabel(txt)
  const bodies = {}
  for (const h of HOOKS) bodies[h] = extractMethod(txt, h)
  const stats = {}
  for (const h of HOOKS) stats[h] = meaningful(bodies[h])
  const sites = stockCallSites(txt)
  const stock = sites.length > 0
  // 库存方法若被四个钩子之一直接调用，视为已接入状态机；否则是独立动作（反审/作废碰不到）
  const hookBodies = HOOKS.map((h) => bodies[h]).join('\n')
  const stockInHooks = sites.some((s) => HOOKS.includes(s.method) || new RegExp('\\b' + s.method + '\\s*\\(').test(hookBodies))

  const issues = []
  // R1：库存动作在，但钩子不齐
  if (stock) {
    const missing = HOOKS.filter((h) => !bodies[h])
    if (missing.length) {
      issues.push({ rule: 'R1', level: '高', text: `有库存动作但缺少钩子：${missing.join('、')}` })
    }
  }
  // R2：审核有实质影响，反审空
  if (stats.onApproved > 0 && stats.onReverseApproved === 0) {
    issues.push({ rule: 'R2', level: '高', text: '审核有业务影响，反审没有冲回实现' })
  }
  // R3：作废/反作废缺失（任一）
  if (stats.onApproved > 0 && (stats.onVoided === 0 || stats.onUnvoided === 0)) {
    const miss = []
    if (stats.onVoided === 0) miss.push('作废')
    if (stats.onUnvoided === 0) miss.push('反作废')
    issues.push({ rule: 'R3', level: '中', text: `审核有业务影响，${miss.join('/')}没有对应钩子` })
  }
  // R4：库存动作不在状态机钩子里
  if (stock && !stockInHooks) {
    const methods = [...new Set(sites.map((s) => s.method))].join('、')
    issues.push({ rule: 'R4', level: '高', text: `库存动作写在独立方法里（${methods}），反审/作废无法冲回` })
  }
  // R5：审核自动生成衍生单据，反审/作废不清理
  if (AUTO_GEN.test(bodies.onApproved) && !CLEANUP.test(bodies.onReverseApproved) && !CLEANUP.test(bodies.onVoided)) {
    issues.push({ rule: 'R5', level: '中', text: '审核自动生成衍生单据，反审/作废没有清理' })
  }
  rows.push({ service, label, stats, stock, stockMethods: [...new Set(sites.map((s) => s.method))], hasAdd: /\.addStock\s*\(/.test(txt), hasDeduct: /\.(deductStock|deductStockAtCost)\s*\(/.test(txt), issues })
}

// ==================== 登记表对照（业务口径 × 代码实现） ====================
let registry = []
if (fs.existsSync(REGISTRY)) {
  try {
    registry = JSON.parse(fs.readFileSync(REGISTRY, 'utf8')).bills || []
  } catch (e) {
    console.error('登记表解析失败：', e.message)
    process.exit(2)
  }
}
const rowByService = new Map(rows.map((r) => [r.service, r]))
for (const b of registry) {
  const r = rowByService.get(b.service)
  if (!r) {
    rows.push({ service: b.service, label: b.label, stats: {}, stock: false, stockMethods: [], hasAdd: false, hasDeduct: false, issues: [{ rule: 'E0', level: '高', text: '登记表里的单据在代码中找不到对应服务' }] })
    continue
  }
  if (b.stock === 'in' && !r.hasAdd) {
    r.issues.push({ rule: 'E1', level: '高', text: `登记表要求「审核增加库存」，代码里没有 addStock（依据：${b.source}）` })
  }
  if (b.stock === 'out' && !r.hasDeduct) {
    r.issues.push({ rule: 'E1', level: '高', text: `登记表要求「审核扣减库存」，代码里没有 deductStock（依据：${b.source}）` })
  }
  if (b.stock === 'both' && (!r.hasAdd || !r.hasDeduct)) {
    r.issues.push({ rule: 'E1', level: '高', text: `登记表要求「一增一减」，代码里缺少 ${!r.hasAdd ? 'addStock' : 'deductStock'}（依据：${b.source}）` })
  }
  if ((b.stock === 'none' || b.stock === 'via') && (r.hasAdd || r.hasDeduct)) {
    r.issues.push({ rule: 'E2', level: '中', text: `登记表标注「${b.stock === 'none' ? '无库存影响' : '库存随其他单据落地'}」，代码里直接调用了库存服务，请确认口径` })
  }
}

// 读基线（已知缺口的业务原因）
let baseline = []
if (fs.existsSync(BASELINE)) {
  try {
    baseline = JSON.parse(fs.readFileSync(BASELINE, 'utf8'))
  } catch (e) {
    console.error('基线文件解析失败：', e.message)
    process.exit(2)
  }
}
const baseKey = (x) => `${x.service}#${x.rule}`
const baseMap = new Map(baseline.map((b) => [baseKey(b), b]))

let known = 0
let todo = 0
let accepted = 0
let fresh = 0
for (const r of rows) {
  r.issues = r.issues.map((it) => {
    const b = baseMap.get(`${r.service}#${it.rule}`)
    if (b) {
      known++
      if (b.status === 'accepted') accepted++
      else todo++
      return { ...it, known: true, status: b.status || 'todo', reason: b.reason || '' }
    }
    fresh++
    return { ...it, known: false, status: 'fresh' }
  })
}

const withIssues = rows.filter((r) => r.issues.length > 0).sort((a, b) => a.service.localeCompare(b.service))

const lines = []
lines.push('# 功能体检 · 单据影响（同类缺陷自动扫描）')
lines.push('')
lines.push(`> 生成时间：${new Date().toISOString().replace('T', ' ').slice(0, 19)}（由 \`frontend/scripts/check-bill-impact.cjs\` 自动生成）`)
lines.push('> 口径：扫描后端单据 ServiceImpl 的 BillImpactHandler 钩子实现，按 R1~R5 规则点名同类缺口；只读扫描，不改业务数据。')
lines.push('')
lines.push('## 汇总')
lines.push('')
lines.push(`- 扫描单据服务：**${rows.length}**（其中带库存动作 **${rows.filter((r) => r.stock).length}**）`)
lines.push(`- 影响登记表：**${registry.length}** 张单据（`+'`frontend/scripts/bill-impact-registry.json`'+`）`)
lines.push(`- 已知缺口（基线内）：**${known}**（待修 **${todo}** / 口径确认 **${accepted}**）`)
lines.push(`- 新增缺口（本次扫描新发现）：**${fresh}**`)
lines.push('')
lines.push('## 怎么用')
lines.push('')
lines.push('- 跑检查：`npm run check:bill-impact`（在 frontend 目录）。发现**新增**缺口时退出码为 1，可挂到提交前或发布前检查。')
lines.push('- 改口径：先改 `frontend/scripts/bill-impact-registry.json`（单据影响登记表是唯一事实来源），再改代码。')
lines.push('- 修完一个缺口：从 `frontend/scripts/bill-impact-baseline.json` 删掉对应条目，缺口会自动从「已知待修」消失；不删则会被当成已接受。')
lines.push('- 新单据上线：先在登记表登记库存口径，再写 ServiceImpl；漏登记会被 E0 点名，登记了没实现会被 E1 点名。')
lines.push('')
if (withIssues.length) {
  lines.push('## 缺口清单')
  lines.push('')
  lines.push('| 单据服务 | 业务名称 | 规则 | 级别 | 缺口 | 状态 |')
  lines.push('|---|---|---|---|---|---|')
  for (const r of withIssues) {
    for (const it of r.issues) {
      const badge = it.status === 'accepted' ? '口径确认' : it.status === 'todo' ? '已知待修' : '**新增**'
      lines.push(`| ${r.service} | ${r.label || '—'} | ${it.rule} | ${it.level} | ${it.text} | ${badge} |`)
    }
  }
} else {
  lines.push('## 缺口清单')
  lines.push('')
  lines.push('未发现缺口。')
}
lines.push('')
lines.push('## 登记表对照（业务口径 × 代码实现）')
lines.push('')
lines.push('| 实体 | 业务名称 | 登记口径 | 代码 addStock/deductStock | 结论 |')
lines.push('|---|---|---|---|---|')
for (const b of registry) {
  const r = rowByService.get(b.service)
  const actual = r ? `${r.hasAdd ? 'add' : '—'} / ${r.hasDeduct ? 'deduct' : '—'}` : '未找到服务'
  let verdict = '✅ 一致'
  if (!r) verdict = '❌ 缺服务'
  else if (b.stock === 'in' && !r.hasAdd) verdict = '❌ 缺增加库存'
  else if (b.stock === 'out' && !r.hasDeduct) verdict = '❌ 缺扣减库存'
  else if (b.stock === 'both' && (!r.hasAdd || !r.hasDeduct)) verdict = '❌ 缺一增一减'
  lines.push(`| ${b.service} | ${b.label} | ${b.stock} | ${actual} | ${verdict} |`)
}
lines.push('')
lines.push('## 全量钩子矩阵')
lines.push('| 单据服务 | 业务名称 | 审核 | 反审 | 作废 | 反作废 | 库存动作所在方法 |')
lines.push('|---|---|---|---|---|---|---|')
for (const r of rows.sort((a, b) => a.service.localeCompare(b.service))) {
  const st = (h) => (r.stats[h] > 0 ? '✓' : '—')
  lines.push(`| ${r.service} | ${r.label || '—'} | ${st('onApproved')} | ${st('onReverseApproved')} | ${st('onVoided')} | ${st('onUnvoided')} | ${r.stockMethods.join('、') || '—'} |`)
}
lines.push('')

const out = lines.join('\n')
fs.mkdirSync(DOCS, { recursive: true })
fs.writeFileSync(path.join(DOCS, '功能体检-单据影响.md'), out, 'utf8')
if (WANT_JSON) {
  fs.writeFileSync(
    path.join(DOCS, '功能体检-单据影响.json'),
    JSON.stringify({ generatedAt: new Date().toISOString(), rows, known, fresh }, null, 2),
    'utf8'
  )
}
console.log(out)
console.log(`\n已知缺口 ${known} 个，新增缺口 ${fresh} 个。报告：docs/功能体检-单据影响.md`)
process.exit(fresh > 0 ? 1 : 0)
