// 浏览器截图预览所有单据打印效果
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const SCREENSHOT_DIR = 'd:/erp/screenshots_all_templates';
if (!fs.existsSync(SCREENSHOT_DIR)) fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });

const BILLS = [
  { type: 'SALE_QUOTE',       id: 1, name: '01_销售报价单' },
  { type: 'SALE_ORDER',       id: 1, name: '02_销售订单(送货单)' },
  { type: 'SALE_DELIVERY',    id: 1, name: '03_销售出库单' },
  { type: 'SALE_INVOICE',     id: 1, name: '04_销售发票' },
  { type: 'PURCHASE_ORDER',   id: 1, name: '05_采购订单' },
  { type: 'PURCHASE_INBOUND', id: 1, name: '06_采购入库单' },
  { type: 'STOCK_TRANSFER',   id: 2, name: '07_库存调拨单' },
  { type: 'STOCK_CHECK',      id: 2, name: '08_库存盘点单' },
  { type: 'OTHER_RECEIPT',    id: 1, name: '09_其他入库单' },
  { type: 'BOM',              id: 1, name: '10_BOM物料清单' }
];

(async () => {
  const browser = await chromium.launch({ headless: false, slowMo: 100 });
  const context = await browser.newContext({ viewport: { width: 1000, height: 1400 } });
  const page = await context.newPage();

  // 登录
  const loginResp = await fetch('http://localhost:8080/api/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin123' })
  }).then(r => r.json());
  const token = loginResp.data.token;

  for (const bill of BILLS) {
    try {
      // 获取打印HTML
      const resp = await fetch(`http://localhost:8080/api/print-template/print/${bill.type}/${bill.id}`, {
        headers: { 'Authorization': 'Bearer ' + token }
      }).then(r => r.json());
      const html = resp.data || '';
      if (!html) { console.log(`[SKIP] ${bill.name} - 无数据`); continue; }

      // 保存为本地文件
      const filePath = path.join(SCREENSHOT_DIR, `${bill.name}.html`);
      fs.writeFileSync(filePath, html, 'utf8');

      // 在浏览器中打开
      await page.goto('file:///' + filePath.replace(/\\/g, '/'), { waitUntil: 'networkidle' });
      await page.waitForTimeout(800);

      // 截图
      await page.screenshot({ path: path.join(SCREENSHOT_DIR, `${bill.name}.png`), fullPage: true });
      console.log(`[PASS] ${bill.name} - 截图完成 (${html.length}字节)`);
    } catch (e) {
      console.log(`[FAIL] ${bill.name} - ${e.message}`);
    }
  }

  console.log(`\n截图保存于: ${SCREENSHOT_DIR}`);
  console.log('浏览器保持打开10秒...');
  await page.waitForTimeout(10000);
  await browser.close();
})();
