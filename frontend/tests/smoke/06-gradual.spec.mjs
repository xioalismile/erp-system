/**
 * 06 逐条菜单累加验证（对应验收 A7，用户明确要求"一点一点给权限"的全模块版本）
 *
 * 做法：对每个模块，从 1 条开始，一条一条往上加授权，每加一条就：
 *   ① 用测试账号真实登录（后端 /api/auth/login）
 *   ② 回读 /api/auth/userinfo 的 menuRoutePaths
 *   ③ 断言它相对上一步「精确 +1」，且等于当前已授权的集合
 *
 * 为什么要在 API 层做而不是浏览器层：
 *   全模块累计约 100 个授权步骤，每步都开浏览器会跑到几十分钟；
 *   本用例验证的是"权限递加是否实时生效"这一机制，
 *   UI 层是否同步渲染由 02（隔离）与 05（页面覆盖）负责，职责不重复。
 *
 * 用例结束后会把授权恢复成完整集合，避免留下"半授权"状态影响后续用例。
 */
import { test, expect } from '@playwright/test'
import { cfg, readJson, writeJson } from './smoke-env.mjs'
import { login, userinfo, apiOrThrow } from './smoke-http.mjs'

const accountsFile = readJson('accounts.json')
const enabled = accountsFile && Array.isArray(accountsFile.accounts) && accountsFile.accounts.length > 0
const accounts = enabled
  ? accountsFile.accounts.filter(a => a.roleId && (!cfg.moduleFilter.length || cfg.moduleFilter.includes(a.moduleKey)))
  : []

const report = { startedAt: new Date().toISOString(), baseUrl: cfg.baseUrl, gradual: cfg.gradual, modules: [] }
const norm = (arr) => [...new Set(arr.map(p => String(p).split('?')[0]))].sort()

test.describe('06 逐条菜单累加验证', () => {
  test.skip(!enabled, '未找到 accounts.json，请先执行 npm run test:smoke:prepare')
  test.skip(!cfg.gradual, 'E2E_GRADUAL=false，跳过累加验证')

  for (const acc of accounts) {
    test(`@gradual @${acc.moduleKey} ${acc.moduleLabel} 权限逐条授予实时生效`, async () => {
      const admin = await login()
      const granted = norm(acc.granted)
      const steps = []
      const problems = []

      try {
        for (let i = 1; i <= granted.length; i++) {
          const nowGrant = granted.slice(0, i)
          await apiOrThrow('POST', `/api/system/role/${acc.roleId}/permissions`, { token: admin.token, body: nowGrant })

          const u = await login(acc.username, accountsFile.testUserPassword)
          const info = await userinfo(u.token)
          const actual = norm(info.menuRoutePaths)

          const ok = actual.length === i && actual.every((p, idx) => p === nowGrant[idx])
          steps.push({ step: i, expected: nowGrant.length, actual: actual.length, ok })
          if (!ok) {
            const extra = actual.filter(p => !nowGrant.includes(p))
            const miss = nowGrant.filter(p => !actual.includes(p))
            problems.push(`第 ${i} 步不一致：期望 ${i} 条、实际 ${actual.length} 条；多出[${extra.join(', ')}] 缺少[${miss.join(', ')}]`)
            break
          }
        }
      } finally {
        // 无论成功失败，都把授权恢复成完整集合，避免污染后续用例
        await apiOrThrow('POST', `/api/system/role/${acc.roleId}/permissions`, { token: admin.token, body: granted }).catch(e => {
          problems.push(`恢复完整授权失败: ${e.message}`)
        })
      }

      report.modules.push({ module: acc.moduleKey, label: acc.moduleLabel, total: granted.length, steps: steps.length, ok: problems.length === 0 })
      writeJson('gradual-result.json', report)

      expect(problems, `[${acc.moduleLabel}] 累加验证失败（共 ${granted.length} 条菜单，完成 ${steps.length} 步）：\n  - ${problems.join('\n  - ')}`).toEqual([])
    })
  }
})
