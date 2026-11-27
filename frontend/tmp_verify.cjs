// 临时验证脚本：验证采购合同「合同类型」加宽生效、其它字段未受影响（用完即删）
const fs = require('fs');
const crypto = require('crypto');
const secret = fs.readFileSync('d:/erp/prodsrc/智能ERP-项目/backend/erp-jwt-secret.key', 'utf8').trim();
function b64url(o) { return Buffer.from(JSON.stringify(o)).toString('base64url'); }
function sign() {
  const now = Math.floor(Date.now() / 1000);
  const head = b64url({ alg: 'HS256', typ: 'JWT' });
  const body = b64url({ sub: '1', username: 'admin', iat: now, exp: now + 7200 });
  const data = head + '.' + body;
  return data + '.' + crypto.createHmac('sha256', secret).update(data).digest('base64url');
}
const token = sign();
const { chromium } = require('d:/erp/prodsrc/智能ERP-项目/frontend/node_modules/playwright');

(async () => {
  const browser = await chromium.launch();
  let pass = 0, fail = 0;
  for (const vw of [1440, 1280]) {
    const ctx = await browser.newContext({ viewport: { width: vw, height: 900 } });
    await ctx.addInitScript((t) => localStorage.setItem('token', t), token);
    const page = await ctx.newPage();
    const errs = [];
    page.on('console', (m) => { if (m.type() === 'error') errs.push(m.text().slice(0, 160)); });
    page.on('pageerror', (e) => errs.push('PAGEERROR ' + e.message.slice(0, 160)));
    await page.goto('http://localhost:3000/purchase/contracts/new', { waitUntil: 'networkidle', timeout: 60000 }).catch(() => {});
    await page.waitForSelector('.base-form .section-fields', { timeout: 30000 }).catch(() => {});
    await page.waitForTimeout(1200);

    const m = await page.evaluate(() => {
      const grid = document.querySelector('.base-form .section-fields');
      const items = [...grid.children];
      const get = (label) => {
        const it = items.find((i) => (i.querySelector('.el-form-item__label')?.textContent || '').trim() === label);
        if (!it) return null;
        const ctl = it.querySelector('.el-select, .el-date-editor, .el-input');
        return { ctlW: ctl ? Math.round(ctl.getBoundingClientRect().width) : null, itemW: Math.round(it.getBoundingClientRect().width), x: Math.round(it.getBoundingClientRect().x) };
      };
      return {
        cols: getComputedStyle(grid).gridTemplateColumns,
        gridW: Math.round(grid.getBoundingClientRect().width),
        formW: Math.round(document.querySelector('.base-form').getBoundingClientRect().width),
        contractType: get('合同类型'), contractDate: get('合同日期'), contractNo: get('合同号'),
        addr: get('收货地址'),
        optText: (() => { const it = items.find((i) => (i.querySelector('.el-form-item__label')?.textContent || '').trim() === '合同类型'); return it ? it.querySelector('.el-select__placeholder, .el-select__selected-item')?.textContent?.trim() : null; })()
      };
    });
    const ok = (cond, msg) => { if (cond) { pass++; console.log('  PASS ' + msg); } else { fail++; console.log('  FAIL ' + msg); } };
    console.log('===== 采购合同 @ ' + vw + 'px  (表单区 ' + m.formW + 'px, 格子 ' + m.cols + ')');
    console.log('  合同类型框=' + m.contractType.ctlW + 'px  合同日期框=' + m.contractDate.ctlW + 'px  合同号框=' + m.contractNo.ctlW + 'px');
    console.log('  首行排布: 合同号(x=' + m.contractNo.x + ') | 合同日期(x=' + m.contractDate.x + ') | 合同类型(x=' + m.contractType.x + ', 占宽' + m.contractType.itemW + ')');
    console.log('  收货地址(整行字段)项宽=' + m.addr.itemW + ' / 网格宽=' + m.gridW + '  当前合同类型值="' + m.optText + '"');
    console.log('  控制台报错: ' + (errs.length ? JSON.stringify(errs) : 'NONE'));
    ok(m.contractType.ctlW >= 200, '合同类型框宽度 ' + m.contractType.ctlW + 'px >= 200px（改前仅36px）');
    ok(m.contractType.ctlW > m.contractDate.ctlW * 1.5, '合同类型明显宽于合同日期(' + m.contractDate.ctlW + 'px)');
    ok(m.contractDate.ctlW === 220 && m.contractNo.ctlW === 220, '其它字段宽度保持 220px 未变');
    ok(m.addr.itemW >= m.gridW - 2, '整行字段(收货地址)仍横跨整行 ' + m.addr.itemW + '/' + m.gridW);
    ok(m.contractType.itemW > 0, '合同类型占2格，项宽=' + m.contractType.itemW);
    ok(errs.length === 0, '控制台报错数为 0');

    // 销售合同：确认未被本次改动波及
    const errs2 = [];
    const p2 = await ctx.newPage();
    p2.on('console', (x) => { if (x.type() === 'error') errs2.push(x.text().slice(0, 160)); });
    p2.on('pageerror', (e) => errs2.push('PAGEERROR ' + e.message.slice(0, 160)));
    await p2.goto('http://localhost:3000/sale/contracts/new', { waitUntil: 'networkidle', timeout: 60000 }).catch(() => {});
    await p2.waitForSelector('.base-form .section-fields', { timeout: 30000 }).catch(() => {});
    await p2.waitForTimeout(800);
    const s = await p2.evaluate(() => {
      const items = [...document.querySelectorAll('.base-form .section-fields')[0].children];
      const it = items.find((i) => (i.querySelector('.el-form-item__label')?.textContent || '').trim() === '合同类型');
      return Math.round(it.querySelector('.el-select').getBoundingClientRect().width);
    });
    console.log('===== 销售合同 @ ' + vw + 'px  合同类型框=' + s + 'px（应与改前一致，未被波及）  报错:' + (errs2.length ? JSON.stringify(errs2) : 'NONE'));
    ok(s === 110, '销售合同页保持原样 110px');
    ok(errs2.length === 0, '销售合同页控制台报错数为 0');
    await ctx.close();
  }
  try { await browser.close(); } catch (e) {}
  console.log('\n结果汇总: PASS=' + pass + '  FAIL=' + fail);
})();
