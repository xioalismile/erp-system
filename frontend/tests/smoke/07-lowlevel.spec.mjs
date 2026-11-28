/**
 * 07 低级缺陷防线（交互层）
 *
 * 与 05（整页打不开/白屏/500）互补，专门抓"页面上能看见但不正常"的低级问题：
 *   1) 列表页表格有列头却渲染不出数据，也不显示空态
 *   2) 点「新增」没反应（地址不变、也没有弹窗）
 *   3) 新增页打开后表单控件过少（字段没渲染）
 *   4) 上述过程中出现 console.error / pageerror / 5xx
 *
 * 安全边界：本用例**只读**——只打开页面、点开新增页，不点保存、不提交、不删改。
 *          默认只在 localhost 上点「新增」；对 8cloud 等远端只做列表渲染检查，
 *          除非显式设置 E2E_ALLOW_REMOTE_CLICK=1。
 */
import { test, expect } from '@playwright/test'
import { cfg, readJson, writeJson } from './smoke-env.mjs'
import { startMonitors, loginByUi, waitAppReady } from './smoke-browser.mjs'

const accountsFile = readJson('accounts.json')
const enabled = accountsFile && Array.isArray(accountsFile.accounts) && accountsFile.accounts.length > 0
const accounts = enabled
  ? accountsFile.accounts.filter((a) => a.roleId && (!cfg.moduleFilter.length || cfg.moduleFilter.includes(a.moduleKey)))
  : []

const isLocal = /^(localhost|127\.0\.0\.1|\[::1\])$/.test(new URL(cfg.baseUrl).hostname)
const allowClick = isLocal || process.env.E2E_ALLOW_REMOTE_CLICK === '1'

const report = { startedAt: new Date().toISOString(), baseUrl: cfg.baseUrl, allowClick, modules: [], findings: [] }

// 全量约 99 个页面，默认 150s 不够；单独给这条用例 15 分钟
test.describe.configure({ timeout: 900000 })

test.describe('07 低级缺陷防线（交互）', () => {
  test.skip(!enabled, '未找到 accounts.json，请先执行 npm run test:smoke:prepare')

  for (const acc of accounts) {
    test(`@lowlevel @${acc.moduleKey} ${acc.moduleLabel} 列表渲染与新增页可用`, async ({ page }) => {
      const mon = startMonitors(page)
      await loginByUi(page, acc.username, accountsFile.testUserPassword)
      await waitAppReady(page)

      const paths = [...new Set(await page.evaluate(() => {
        try { return JSON.parse(localStorage.getItem('menuRoutePaths') || '[]') } catch (_) { return [] }
      }))].filter((p) => !/\/(new|edit)(\/|$)/.test(p))

      // 调试用：E2E_LOWLEVEL_MAX_PAGES=8 只查前 8 个页面，快速验证用例本身
      const maxPages = Number(process.env.E2E_LOWLEVEL_MAX_PAGES || 0)
      const targets = maxPages > 0 ? paths.slice(0, maxPages) : paths

      const problems = []
      let checked = 0
      let addChecked = 0

      for (const p of targets) {
        mon.reset()
        try {
          await page.goto(p, { waitUntil: 'domcontentloaded' })
          await page.waitForTimeout(1100)

          const tableCount = await page.locator('.el-table').count()
          if (!tableCount) continue // 非列表页（仪表盘、设置页等）
          checked++

          const rows = await page.locator('.el-table__row').count()
          const headers = await page.locator('.el-table__header th').count()
          if (headers < 2) problems.push(`${p} 表格列头不足（${headers} 列），可能没渲染`)

          if (rows === 0) {
            const emptyText = await page.locator('.el-table__empty-text, .el-empty').count()
            const bodyText = await page.locator('#app').innerText().catch(() => '')
            const hasEmptyWords = /暂无|无数据|没有数据|空数据/.test(bodyText)
            if (!emptyText && !hasEmptyWords) problems.push(`${p} 表格无数据行也不显示空态（用户看到一片空白）`)
          }

          if (allowClick) {
            const addBtn = page.getByRole('button', { name: /^(新增|新建|添加)/ }).first()
            if (await addBtn.count()) {
              addChecked++
              const beforeUrl = page.url()
              const beforeInputs = await page.locator('input, textarea').count()
              await addBtn.click().catch(() => {})

              // 三种正常反应：地址变化 / 弹出弹窗抽屉 / 同页展开表单（输入框明显增多）
              const reacted = await page
                .waitForFunction(
                  ({ u, n }) =>
                    location.href !== u ||
                    document.querySelectorAll('.el-dialog, .el-drawer').length > 0 ||
                    document.querySelectorAll('input, textarea').length > n + 2,
                  { u: beforeUrl, n: beforeInputs },
                  { timeout: 6000 }
                )
                .then(() => true)
                .catch(() => false)

              if (!reacted) {
                problems.push(`${p} 点「新增」无反应（地址未变、无弹窗、表单未展开）`)
              } else {
                const navigated = page.url() !== beforeUrl
                const dialog = await page.locator('.el-dialog:visible, .el-drawer:visible').count()
                const scope = dialog ? '.el-dialog:visible, .el-drawer:visible' : '#app'
                const inputs = await page.locator(`${scope} input, ${scope} textarea`).count()
                // 弹窗表单可能只有一个字段（如"新增配置"），整页新增才要求多个字段
                const minInputs = dialog ? 1 : 3
                if (inputs < minInputs) problems.push(`${p} 新增页表单控件过少（${inputs} 个），字段可能没渲染`)
                const title = (await page.locator('.page-title').first().innerText().catch(() => '')).trim()
                if (navigated && title && !/新增|新建|添加/.test(title)) problems.push(`${p} 新增页标题异常：${title}`)

                if (navigated) {
                  await page.goBack().catch(() => {})
                  await page.waitForTimeout(400)
                } else if (dialog) {
                  await page.keyboard.press('Escape').catch(() => {})
                  await page.waitForTimeout(300)
                }
              }
            }
          }

          const errs = mon.problems('')
          if (errs.length) problems.push(`${p} 运行时报错：${errs.slice(0, 3).join('；')}`)
        } catch (e) {
          problems.push(`${p} 检查异常：${e.message.split('\n')[0]}`)
        }
      }

      mon.stop()
      report.modules.push({ module: acc.moduleKey, label: acc.moduleLabel, listPages: checked, addChecked, failed: problems.length })
      report.findings.push(...problems.map((x) => ({ module: acc.moduleKey, detail: x })))
      writeJson('07-lowlevel-result.json', report)

      expect(problems, `[${acc.moduleLabel}] 低级缺陷 ${problems.length} 处：\n  - ${problems.join('\n  - ')}`).toEqual([])
    })
  }
})
