/**
 * 菜单与用例对账（"加了新功能，脚本怎么办"的机器答案）
 *
 * 解决什么问题：
 *   01 全页面覆盖网只查"打开不白屏/不报错"，不查业务；
 *   03 端到端闭环只跑「登记表 BILLS」里的单据，**新单据忘了登记 = 永远不测**，而且没人报警。
 *   本脚本按"角色实际拿到的菜单"反推单据页（有 /new 或 /edit 兄弟路由的可建单页面），
 *   与登记表逐条比对：有菜单、没登记 → 直接点名，不再靠人记得。
 *
 * 数据来源（不猜）：
 *   - 菜单：frontend/tests/smoke/.out/accounts.json（prepare.mjs 实测登录后拿到的 actualPaths）
 *   - 登记表：frontend/tests/smoke/smoke-write-config.mjs 的 BILLS / PENDING_BILLS
 *
 * 用法：node tests/smoke/menu-coverage.mjs
 * 输出：控制台清单 + .out/menu-coverage.json
 */
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { BILLS, PENDING_BILLS } from './smoke-write-config.mjs'

const HERE = dirname(fileURLToPath(import.meta.url))
const OUT_DIR = join(HERE, '.out')
const ACCOUNTS = join(OUT_DIR, 'accounts.json')
const REPORT = join(OUT_DIR, 'menu-coverage.json')

const norm = (p) => String(p || '').split('?')[0].replace(/\/+$/, '')

if (!existsSync(ACCOUNTS)) {
  console.error('未找到 .out/accounts.json：请先执行 npm run test:smoke:prepare（登录并采集菜单）')
  process.exit(2)
}

const accounts = JSON.parse(readFileSync(ACCOUNTS, 'utf8')).accounts || []
const allPaths = new Set()
for (const a of accounts) {
  for (const p of a.actualPaths || []) allPaths.add(norm(p))
}

/** 登记表里已覆盖的列表页/新增页 */
const registered = new Set()
for (const b of [...BILLS, ...PENDING_BILLS]) {
  if (b.listPath) registered.add(norm(b.listPath))
  if (b.newPath) registered.add(norm(b.newPath).replace(/\/(new|edit)$/, ''))
}

/** 确认"不是单据"的页面（报表/配置/主档等，有 /new 或 /edit 兄弟路由但无需建单闭环） */
const KNOWN_NON_BILL = []

/** 判定"可建单页面"：存在 /new 或 /edit 兄弟路由 */
const billLike = []
for (const p of allPaths) {
  if (registered.has(p) || KNOWN_NON_BILL.includes(p)) continue
  const hasNew = allPaths.has(`${p}/new`) || allPaths.has(`${p}/edit`)
  if (hasNew) billLike.push(p)
}

const report = {
    generatedAt: new Date().toISOString(),
    menuPaths: allPaths.size,
    registeredBills: BILLS.length,
    pendingBills: PENDING_BILLS.map((b) => b.name),
    unregisteredBillPages: billLike.sort()
  }
  writeFileSync(REPORT, JSON.stringify(report, null, 2))

console.log('\n===== 菜单与用例对账 =====')
console.log(`  菜单页面：${allPaths.size} 个`)
console.log(`  已登记闭环单据：${BILLS.length} 张（另 ${PENDING_BILLS.length} 张形态不匹配，已显式登记）`)
if (billLike.length) {
  console.log(`  ★ 有菜单但未登记闭环用例：${billLike.length} 个 —— 这些页面不会被"建单→保存→回读"深度检查`)
  for (const p of billLike.sort()) console.log(`    · ${p}`)
  console.log('  处理：确属单据的，在 smoke-write-config.mjs 的 BILLS 里补一条（带源码证据）；')
  console.log('        确属报表/配置/主档的，加到本脚本 KNOWN_NON_BILL 白名单。')
} else {
  console.log('  ✓ 所有可建单页面都已登记闭环用例')
}
console.log(`  报告：${REPORT}`)
// 门禁口径：有菜单却没登记闭环 → 不通过（漏登记必须当场暴露，不能靠人记得）
process.exit(billLike.length ? 1 : 0)
