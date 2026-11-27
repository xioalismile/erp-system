import { test } from '@playwright/test';
test('shot new login', async ({ browser }) => {
  const ctx = await browser.newContext({ viewport: { width: 1920, height: 1080 } });
  const page = await ctx.newPage();
  await page.goto('http://localhost:3000/');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(2500);
  await page.screenshot({ path: 'D:/erp/login_v3_full.png', fullPage: false });
  // 同时截按钮局部
  const btn = page.locator('.login-btn');
  try { await btn.first().screenshot({ path: 'D:/erp/login_v3_btn.png' }); } catch (_) {}
  await ctx.close();
});
