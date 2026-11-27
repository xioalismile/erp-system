/**
 * 01 登录链路（基础覆盖网的第一道）
 *
 * 抓的缺陷：
 *   R1  8cloud 根路径 / 自 2026-10-18 起返回登录页（官网在 /promo.html）→ 入口仍统一走 /login
 *   D15 页面白屏 / 卡死
 *   —— 未登录访问受保护页必须被守卫送走（鉴权链路是否真的生效）
 *
 * 本文件用**测试租户管理员**验证"登录机制本身"（含选公司页处理），
 * 功能与权限验证分别在 02/03 用模块角色做。
 * 禁止使用平台超管 admin：其 token 不带 tenantId，请求会落到主库 erp_db。
 */
import { test, expect } from '@playwright/test'
import { cfg } from './smoke-env.mjs'
import { startMonitors, loginByUi, waitAppReady, readSidebar, detectBlank, gotoApp } from './smoke-browser.mjs'

test.describe('01 登录链路', () => {
  test('@login 未登录访问系统 → 被送到登录页（且入口页不是官网宣传页）', async ({ page }) => {
    const mon = startMonitors(page)
    await gotoApp(page)
    await page.waitForTimeout(1500)

    // 8cloud 根路径 / 自 2026-10-18 起返回登录页（官网在 /promo.html）；入口仍统一用 /login
    const hasAppRoot = await page.locator('#app').count()
    expect(hasAppRoot, `入口 ${cfg.entryPath} 未渲染出 #app（入口请用 /login；8cloud 根路径 / 自 2026-10-18 起是登录页，官网在 /promo.html）`).toBeGreaterThan(0)

    // 无 token 时守卫应把用户送到 /login
    await page.evaluate(() => localStorage.removeItem('token'))
    await page.goto('/sale/orders', { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(1500)
    const path = new URL(page.url()).pathname
    expect(path, '未登录访问受保护页应跳转 /login').toContain('/login')

    const blank = await detectBlank(page)
    expect(blank.blank, `登录页白屏: ${blank.reason}`).toBeFalsy()

    mon.stop()
    expect(mon.problems('01-未登录')).toEqual([])
  })

  test('@login 租户管理员登录成功 → token 落盘、侧边栏渲染、控制台干净', async ({ page }) => {
    const mon = startMonitors(page)
    const token = await loginByUi(page, cfg.adminUser, cfg.adminPassword)
    expect(token.length).toBeGreaterThan(20)
    await waitAppReady(page)

    const sidebar = await readSidebar(page)
    expect(sidebar.present, '侧边栏未渲染').toBeTruthy()
    // 租户管理员（ADMIN_ULTIMATE）应能看到全部业务分组；
    // 注意模板库建租户时会剥离平台专属菜单（/admin%），故"平台管理"组在租户里不存在
    expect(sidebar.groups.length + sidebar.singleItems.length, '租户管理员侧边栏分组数异常偏少').toBeGreaterThanOrEqual(10)

    const blank = await detectBlank(page)
    expect(blank.blank, `登录后页面白屏: ${blank.reason}`).toBeFalsy()

    mon.stop()
    expect(mon.problems('01-租户管理员登录')).toEqual([])
  })

  test('@login 各模块测试账号均可用既定口令登录（阶段A 产物有效性）', async ({ page }) => {
    const { readJson } = await import('./smoke-env.mjs')
    const acc = readJson('accounts.json')
    expect(acc, '未找到 accounts.json，请先执行 npm run test:smoke:prepare').toBeTruthy()
    const list = acc.accounts.filter(a => a.roleId)
    expect(list.length).toBeGreaterThan(0)

    const failed = []
    for (const a of list) {
      try {
        await loginByUi(page, a.username, acc.testUserPassword)
        await waitAppReady(page)
      } catch (e) {
        failed.push(`${a.username}: ${e.message}`)
      }
    }
    expect(failed, `以下测试账号登录失败:\n${failed.join('\n')}`).toEqual([])
  })
})
