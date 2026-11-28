/**
 * 前端冒烟测试专用 Playwright 配置（独立于项目现有 playwright.config.js，互不影响）
 *
 * 目标环境由 E2E_BASE_URL 决定，默认 8cloud（https://kaiyu.pro）。
 * 注意：8cloud 根路径 / 自 2026-10-18 起返回登录页（nginx index index.html promo.html，
 *      未登录由路由守卫送到 /login；官网宣传页保留在 /promo.html）。
 *      入口也不能用 /index.html —— SPA 拿到该路径后会被路由守卫判为无权并踢到 /403（已实测）。
 *      入口统一走 E2E_ENTRY_PATH（默认 /login，在守卫白名单内）。
 *
 * 立项依据：d:\erp\.trae\documents\前端冒烟测试_立项登记表.md
 */
import { defineConfig, devices } from '@playwright/test'

const BASE_URL = process.env.E2E_BASE_URL || 'https://kaiyu.pro'
const HEADLESS = process.env.E2E_HEADLESS !== 'false'
// 覆盖度报告需要保留失败现场：默认只在失败时截图 + 保留 trace
const SHOT = (process.env.E2E_SCREENSHOT || 'only-on-failure')

export default defineConfig({
  testDir: './tests/smoke',
  // 端到端闭环单条用例较长（新增→回读→重开→改→再回读），给足超时
  timeout: 150000,
  expect: { timeout: 15000 },
  // 必须串行：写入用例会真实落库，并发会产生互相干扰与脏数据
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [
    ['list'],
    ['html', { open: 'never', outputFolder: 'test-report-smoke' }],
    ['json', { outputFile: 'tests/smoke/.out/playwright-results.json' }]
  ],
  outputDir: 'tests/smoke/.out/artifacts',
  use: {
    baseURL: BASE_URL,
    headless: HEADLESS,
    viewport: { width: 1440, height: 900 },
    ignoreHTTPSErrors: true,
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    actionTimeout: 20000,
    navigationTimeout: 45000,
    screenshot: SHOT,
    trace: 'retain-on-failure',
    video: 'off'
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        // 本机离线环境可指定已有 Chromium，避免版本号不匹配时重新下载（如 PLAYWRIGHT_CHROMIUM_EXECUTABLE=...\chrome.exe）
        // 本地/内网联调常被系统代理拦截 localhost：统一禁用代理（2026-11-27 实测导航 45s 超时）
        launchOptions: {
          args: ['--no-proxy-server'],
          ...(process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE
            ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE }
            : {})
        }
      }
    }
  ]
})
