/**
 * 05 全量页面覆盖网（对应验收 A8 / 缺陷 D15 D16）
 *
 * 每个模块用它自己的角色登录，逐页打开该模块**全部已授权菜单页**，断言：
 *   1) 没有被踢到 /403
 *   2) 不是白屏（DOM 真的有内容）
 *   3) 页面正文没有 [object Object] / undefined / NaN 这类占位符
 *   4) console.error / pageerror / HTTP 5xx 为 0
 *
 * 说明：这是"基础覆盖网"，负责兜住"整页打不开/白屏/报错"；
 *      真正抓"明细不持久化"的是 03-e2e-loop（只开列表页永远抓不到那类缺陷）。
 */
import { test, expect } from '@playwright/test'
import { cfg, readJson, writeJson } from './smoke-env.mjs'
import { startMonitors, loginByUi, waitAppReady, detectBlank, findPlaceholders } from './smoke-browser.mjs'

const accountsFile = readJson('accounts.json')
const enabled = accountsFile && Array.isArray(accountsFile.accounts) && accountsFile.accounts.length > 0
const accounts = enabled
  ? accountsFile.accounts.filter(a => a.roleId && (!cfg.moduleFilter.length || cfg.moduleFilter.includes(a.moduleKey)))
  : []

/** 跨用例累计结果，最后一次性落盘 */
const report = { startedAt: new Date().toISOString(), baseUrl: cfg.baseUrl, modules: [], pages: [] }

test.describe('05 全量页面覆盖网', () => {
  test.skip(!enabled, '未找到 accounts.json，请先执行 npm run test:smoke:prepare')

  for (const acc of accounts) {
    test(`@pages @${acc.moduleKey} ${acc.moduleLabel} 全部菜单页可正常打开`, async ({ page }) => {
      const mon = startMonitors(page)
      await loginByUi(page, acc.username, accountsFile.testUserPassword)
      await waitAppReady(page)

      const problems = []
      const pages = []
      // 用实际拿到的 menuRoutePaths（而非配置值），保证测的是"这个角色真正有权访问的页面"
      const paths = [...new Set((await page.evaluate(() => {
        try { return JSON.parse(localStorage.getItem('menuRoutePaths') || '[]') } catch (_) { return [] }
      })))]

      for (const p of paths) {
        mon.reset()
        let entry = { module: acc.moduleKey, path: p, ok: true, reasons: [] }
        try {
          await page.goto(p, { waitUntil: 'domcontentloaded' })
          await page.waitForTimeout(900)
          const pathname = new URL(page.url()).pathname
          if (pathname.startsWith('/403')) {
            entry.ok = false
            entry.reasons.push(`被踢到 ${pathname}（授权了却无权访问）`)
          } else {
            const blank = await detectBlank(page)
            if (blank.blank) { entry.ok = false; entry.reasons.push(`白屏(${blank.reason})`) }
            const ph = await findPlaceholders(page)
            if (ph.length) { entry.ok = false; entry.reasons.push(`出现占位符 ${ph.join('/')}`) }
            const errs = mon.problems('')
            if (errs.length) { entry.ok = false; entry.reasons.push(...errs) }
          }
        } catch (e) {
          entry.ok = false
          entry.reasons.push(`打开异常: ${e.message}`)
        }
        entry.url = page.url()
        pages.push(entry)
        if (!entry.ok) problems.push(`${p} → ${entry.reasons.join('；')}`)
      }

      mon.stop()
      report.modules.push({ module: acc.moduleKey, label: acc.moduleLabel, role: acc.roleCode, pageCount: paths.length, failed: pages.filter(x => !x.ok).length })
      report.pages.push(...pages)
      writeJson('pages-result.json', report)

      expect(problems, `[${acc.moduleLabel}] 页面覆盖发现 ${problems.length} 处问题：\n  - ${problems.join('\n  - ')}`).toEqual([])
    })
  }
})
