import { test } from '@playwright/test';
test('button final shot', async ({ browser }) => {
  const ctx = await browser.newContext({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 2 });
  const page = await ctx.newPage();
  await page.goto('http://localhost:3000/');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(2500);
  const loginBtn = page.getByRole('button', { name: '登 录' }).first();
  // 读一下 DOM 的 style 属性和 computed color
  const info = await loginBtn.evaluate(el => {
    const cs = getComputedStyle(el);
    return {
      bg: cs.backgroundColor,
      bgImg: cs.backgroundImage,
      color: cs.color,
      width: cs.width,
      height: cs.height,
      border: cs.border,
    };
  });
  console.log('loginBtn computed:', JSON.stringify(info));
  await loginBtn.screenshot({ path: 'D:/erp/login_btn_closeup.png', scale: 'device' });
  // Slogan 样式检查
  const slogan = page.locator('.value-stmt .lead').first();
  const slogInfo = await slogan.evaluate(el => {
    const cs = getComputedStyle(el);
    return { color: cs.color, opacity: cs.opacity, fill: cs.webkitTextFillColor || '', textShadow: cs.textShadow };
  });
  console.log('slogan computed:', JSON.stringify(slogInfo));
  await page.screenshot({ path: 'D:/erp/login_final_full.png' });
  await ctx.close();
});
