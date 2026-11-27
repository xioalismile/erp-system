/**
 * 02 权限隔离（本套测试的核心用例之一，对应验收 A3 / 缺陷 D11）
 *
 * 每个模块用一个**独立角色 + 独立账号**登录（不用 admin），做三层断言：
 *   第1层 数据层：localStorage.menuRoutePaths 必须 == 阶段A 授权集合（差异 0 条）
 *   第2层 渲染层：左侧菜单"用户肉眼看到的文字"必须 == 按 menuConfig 精确算出的期望
 *   第3层 越权层：访问其它模块的代表性页面，必须被路由守卫踢到 /403
 *
 * 为什么必须三层：
 *   - 只看接口 200 → 抓不到"授权了但菜单不显示"
 *   - 只看菜单显示 → 抓不到"菜单不显示但 URL 能直接进"（越权）
 */
import { test, expect } from '@playwright/test'
import {
  cfg, readJson, loadMenuGroups, expectedSidebarDom, firstPathOfGroup, groupMap
} from './smoke-env.mjs'
import { startMonitors, loginByUi, waitAppReady, readStoreAuth, readSidebar, expectForbiddenOrDashboard } from './smoke-browser.mjs'

const accountsFile = readJson('accounts.json')
const groups = await loadMenuGroups()
const gmap = groupMap(groups)

const enabled = accountsFile && Array.isArray(accountsFile.accounts) && accountsFile.accounts.length > 0
const accounts = enabled
  ? accountsFile.accounts.filter(a => a.roleId && (!cfg.moduleFilter.length || cfg.moduleFilter.includes(a.moduleKey)))
  : []

// guard 对 /dashboard 有豁免（所有登录用户都能进），故不能作为越权样本
const FOREIGN_SKIP = new Set(['/dashboard'])

test.describe('02 权限隔离', () => {
  test.skip(!enabled, '未找到 accounts.json，请先执行 npm run test:smoke:prepare')

  for (const acc of accounts) {
    test(`@isolation @${acc.moduleKey} 角色 ${acc.roleCode} 只能看到本模块菜单，其它模块被拦`, async ({ page }) => {
      const mon = startMonitors(page)
      const problems = []

      // ---------- 登录 ----------
      await loginByUi(page, acc.username, accountsFile.testUserPassword)
      await waitAppReady(page)

      // ---------- 第1层：数据层（最精确）----------
      const auth = await readStoreAuth(page)
      const actualPaths = [...new Set(auth.menuRoutePaths)].sort()
      const expectPaths = [...new Set(acc.granted)].sort()
      const onlyActual = actualPaths.filter(p => !expectPaths.includes(p))
      const onlyExpect = expectPaths.filter(p => !actualPaths.includes(p))
      if (onlyActual.length || onlyExpect.length) {
        problems.push(
          `第1层 menuRoutePaths 与授权不一致：多出 [${onlyActual.join(', ')}] 缺少 [${onlyExpect.join(', ')}]`
        )
      }

      // ---------- 第2层：渲染层（用户肉眼可见）----------
      const sidebar = await readSidebar(page)
      expect(sidebar.present, '侧边栏未渲染').toBeTruthy()

      const exp = expectedSidebarDom(groups, auth.menuRoutePaths)
      const expLabels = [...new Set([...exp.groupLabels, ...exp.itemLabels])].sort()
      const actLabels = [...new Set(sidebar.allLabels)].sort()
      const extraInDom = actLabels.filter(l => !expLabels.includes(l))
      const missingInDom = expLabels.filter(l => !actLabels.includes(l))
      if (extraInDom.length || missingInDom.length) {
        problems.push(
          `第2层 侧边栏文案与期望不一致：多出 [${extraInDom.join(', ')}] 缺少 [${missingInDom.join(', ')}]`
        )
      }
      // 顶层分组数量（单项菜单也算一个顶层入口）
      const actTop = sidebar.groups.length + sidebar.singleItems.length
      if (actTop !== exp.groupLabels.length) {
        problems.push(`第2层 顶层菜单入口数 ${actTop} ≠ 期望 ${exp.groupLabels.length}（期望: ${exp.groupLabels.join(', ')}；实际: ${[...sidebar.singleItems, ...sidebar.groups.map(g => g.label)].join(', ')}）`)
      }

      // ---------- 第3层：越权访问 ----------
      const foreign = groups
        .filter(g => g.key !== acc.moduleKey)
        .map(g => ({ key: g.key, path: firstPathOfGroup(g) }))
        .filter(x => x.path && !FOREIGN_SKIP.has(x.path))
      const leaked = []
      for (const f of foreign) {
        const r = await expectForbiddenOrDashboard(page, f.path)
        if (!r.kickedTo403) leaked.push(`${f.key}(${f.path}) → 实际停在 ${r.pathname}`)
      }
      if (leaked.length) {
        problems.push(`第3层 越权未拦截 ${leaked.length} 处：${leaked.join('；')}`)
      }

      // ---------- 控制台洁净 ----------
      mon.stop()
      problems.push(...mon.problems('02-隔离'))

      expect(
        problems,
        `[${acc.moduleKey}] 权限隔离未通过：\n  - ${problems.join('\n  - ')}\n` +
        `（期望模块: ${exp.groupLabels.join(', ') || '(无)'}；授权路径 ${expectPaths.length} 条）`
      ).toEqual([])
    })
  }
})
