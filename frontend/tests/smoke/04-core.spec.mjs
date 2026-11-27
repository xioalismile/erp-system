/**
 * 04 核心列表页深化断言（对应验收 A6）
 *
 * 05 只验"页面能不能打开"，本用例进一步验"列表页该有的东西在不在"：
 *   1) 有数据表格（.el-table 渲染出来了）
 *   2) 有分页控件
 *   3) 顶部主操作按钮存在（抓 D13：v-permission 把按钮误从 DOM 移除）
 *   4) 控制台无报错
 *
 * 为什么单独一层：本项目授权模型是"菜单级"，sys_menu 里没有 :add/:delete 操作码，
 * 历史事故是"角色有菜单权限但页面按钮被 v-permission 整颗删掉"，05 的遍历断言抓不到这个。
 *
 * 覆盖范围：各模块的"实体列表页"。看板/报表/手册/设置中心不做表格断言（它们本就没有表格）。
 */
import { test, expect } from '@playwright/test'
import { cfg, readJson, writeJson, loadMenuGroups, groupLeafItems, filterModules } from './smoke-env.mjs'
import { startMonitors, loginByUi, waitAppReady } from './smoke-browser.mjs'

const accountsFile = readJson('accounts.json')
const enabled = accountsFile && Array.isArray(accountsFile.accounts) && accountsFile.accounts.length > 0
const accByModule = new Map((accountsFile?.accounts || []).map(a => [a.moduleKey, a]))

const groups = filterModules(await loadMenuGroups())

/** 非"实体列表页"的路径特征（这些页面本来就没有数据表格，断言表格会是假失败） */
const NON_LIST_PATTERNS = [
  /\/help$/,            // 操作手册
  /\/report\//,         // 报表
  /dashboard/i,         // 各类看板（含 /databoard/*）
  /\/databoard\//,      // 数据看板（图形页，无表格）
  /schedule-board/,     // 排程看板
  /progress-tracking/,  // 车间进度跟踪（工序圆环看板，无 el-table）
  /\/funnel$/,          // 销售漏斗（图形）
  /\/mrp$/,             // MRP 分析
  /\/ledger$/,          // 库存流水（流水台账，无分页主表）
  /\/current$/,         // 即时库存
  /\/batches$/,         // 批次库存
  /\/settings$/,        // 设置中心
  /\/cost-config$/,     // 成本核算配置
  /\/login$/            // 获客-企查查登录（配置页，无表格）
]

const CORE_BY_MODULE = new Map()
for (const g of groups) {
  const paths = groupLeafItems(g)
    .map(i => i.path.split('?')[0])
    .filter(p => !NON_LIST_PATTERNS.some(re => re.test(p)))
  CORE_BY_MODULE.set(g.key, [...new Set(paths)])
}

const report = { startedAt: new Date().toISOString(), baseUrl: cfg.baseUrl, modules: [] }

test.describe('04 核心列表页深化断言', () => {
  test.skip(!enabled, '未找到 accounts.json，请先执行 npm run test:smoke:prepare')

  for (const [moduleKey, corePaths] of CORE_BY_MODULE.entries()) {
    test(`@core @${moduleKey} 核心列表页表格/分页/主按钮齐备`, async ({ page }) => {
      const acc = accByModule.get(moduleKey)
      test.skip(!acc, `账号清单里没有模块 ${moduleKey} 的测试账号`)
      // 过滤后没有实体列表页的模块（看板/系统管理）显式跳过并说明，不产生"空跑绿灯"
      test.skip(corePaths.length === 0, `模块 ${moduleKey} 没有实体列表页（看板/设置中心类），本用例不适用`)

      const mon = startMonitors(page)
      await loginByUi(page, acc.username, accountsFile.testUserPassword)
      await waitAppReady(page)

      const problems = []
      const details = []

      for (const p of corePaths) {
        mon.reset()
        const item = { module: moduleKey, path: p, ok: true, reasons: [], buttons: [] }
        try {
          await page.goto(p, { waitUntil: 'domcontentloaded' })
          await page.waitForTimeout(1500)

          if (new URL(page.url()).pathname.startsWith('/403')) {
            item.ok = false
            item.reasons.push('被踢到 /403')
          } else {
            // 1) 数据表格（等它渲染，避免抢跑误判）
            const hasTable = await page.locator('.el-table').first()
              .waitFor({ state: 'visible', timeout: 15000 }).then(() => true).catch(() => false)
            if (!hasTable) { item.ok = false; item.reasons.push('未渲染出数据表格(.el-table)') }

            // 2) 分页控件（部分页面记录少时可能不渲染，故记为提示而非失败）
            item.hasPagination = await page.locator('.el-pagination').count() > 0

            // 3) 顶部主操作按钮：至少要有一个按钮，且记录文案用于人工核对
            const btns = page.locator('button')
            const n = await btns.count()
            for (let i = 0; i < Math.min(n, 12); i++) {
              const t = (await btns.nth(i).innerText().catch(() => '')).trim().replace(/\s+/g, ' ')
              if (t) item.buttons.push(t)
            }
            if (n === 0) { item.ok = false; item.reasons.push('页面上找不到任何按钮（疑似 v-permission 全部移除）') }

            // 4) 控制台
            const errs = mon.problems('')
            if (errs.length) { item.ok = false; item.reasons.push(...errs) }
          }
        } catch (e) {
          item.ok = false
          item.reasons.push(`异常: ${e.message}`)
        }
        details.push(item)
        if (!item.ok) problems.push(`${p} → ${item.reasons.join('；')}`)
      }

      mon.stop()
      report.modules.push({
        module: moduleKey,
        pageCount: corePaths.length,
        failed: details.filter(d => !d.ok).length,
        pages: details
      })
      writeJson('core-result.json', report)

      expect(problems, `[${moduleKey}] 核心列表页断言未通过：\n  - ${problems.join('\n  - ')}`).toEqual([])
    })
  }
})
