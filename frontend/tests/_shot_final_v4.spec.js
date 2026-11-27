import { test } from '@playwright/test';
test('final screenshot', async ({ browser }) => {
  const ctx = await browser.newContext({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1.5 });
  const page = await ctx.newPage();
  await page.goto('http://localhost:3000/');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(2600);
  // 点击勾选记住账号，展示选中状态（顺便验证）
  const label = page.locator('.remember-check').first();
  await label.click();
  await page.waitForTimeout(500);
  await page.screenshot({ path: 'D:/erp/login_v4_final.png' });
  await ctx.close();
});
