import { test } from '@playwright/test';
test('shot new login', async ({ browser }) => {
  const ctx = await browser.newContext({ viewport: { width: 1920, height: 1080 } });
  const page = await ctx.newPage();
  await page.goto('http://localhost:3000/');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(2500); // 让 canvas、光点、波纹动画跑稳定
  await page.screenshot({ path: 'D:/erp/login_v2_full.png', fullPage: false });

  // 点击手机号验证并截取通知
  await page.getByRole('button', { name: /手机号验证/ }).click();
  await page.waitForTimeout(400);
  await page.screenshot({ path: 'D:/erp/login_v2_phone_notice.png', fullPage: false });

  await ctx.close();
});
