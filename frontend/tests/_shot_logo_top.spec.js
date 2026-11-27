import { test } from '@playwright/test';
test('shot logo top', async ({ browser }) => {
  const ctx = await browser.newContext({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1.5 });
  const page = await ctx.newPage();
  await page.goto('http://localhost:3000/');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(2500);
  const logo = page.locator('img.brand-logo').first();
  const box = await logo.boundingBox();
  console.log('logo bounding:', box);
  await page.screenshot({ path: 'D:/erp/login_v4_logo_top.png' });
  await ctx.close();
});
