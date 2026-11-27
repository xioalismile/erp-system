// 自动下推 · 手动编号 + 自动合同禁止再下推 · 回归（2026-11-09）
// 覆盖：
//   ① 销售订单、销售合同都关闭自动编号（手动编号）时，源单手工填号，自动下推的目标单仍能按编号规则取号并生效；
//   ② 由销售订单自动生成的销售合同，服务端拒绝再次作为来源下推生成销售订单；
//   ③ 该自动生成的销售合同编辑页不显示「下推」按钮。
const Path = require('path')
const { chromium } = require(Path.resolve(__dirname, '../../frontend/node_modules/playwright'))
const API = process.argv[2] || 'http://localhost:8080'
const BASE = process.argv[3] || 'http://localhost:3000'
const USER = process.env.ERP_USER || 'admin'
const PWD = process.env.ERP_PWD || 'admin123'
let pass = 0, fail = 0
const assert = (c, n, extra) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (extra ? ' | ' + extra : '')) } }
const j = async r => { const t = await r.text(); try { return JSON.parse(t) } catch (e) { return { raw: t } } }
const today = new Date().toISOString().slice(0, 10)
let H = {}
const created = { contracts: [], orders: [] }
const req = async (m, p, b) => j(await fetch(API + p, { method: m, headers: b ? { ...H, 'Content-Type': 'application/json' } : H, body: b ? JSON.stringify(b) : undefined }))
const setCfg = (k, v) => req('PUT', '/api/sys-config/' + encodeURIComponent(k), { configValue: v })

async function getSerial (docType) {
  return (await req('GET', '/api/serial-no-config/by-doc-type/' + docType))?.data
}
async function setSerialAuto (docType, auto) {
  const cfg = await getSerial(docType)
  if (!cfg) return null
  const original = cfg.autoGenerate
  await req('PUT', '/api/serial-no-config', { ...cfg, autoGenerate: auto })
  return original
}
async function createContract (tag, manualNo) {
  const r = await req('POST', '/api/sale/contracts', {
    contractNo: manualNo, contractDate: today, contractType: '销售合同', customerId: 15, currencyId: 1,
    exchangeRate: 1, discountRate: 0, deliveryDate: today, remark: 'AUTO-PUSH-MANUAL-' + tag,
    items: [{ materialId: 1683, materialCode: '0001', materialName: '小助手', quantity: 10, unitPrice: 100, taxRate: 13, discountRate: 0 }]
  })
  if (r.code !== 200) throw new Error('创建销售合同失败: ' + JSON.stringify(r).slice(0, 300))
  created.contracts.push(r.data.contractId)
  return r.data
}
async function createOrder (tag, manualNo) {
  const r = await req('POST', '/api/sale/orders', {
    orderNo: manualNo, orderDate: today, customerId: 15, currencyId: 1, exchangeRate: 1, discountRate: 0,
    deliveryDate: today, remark: 'AUTO-PUSH-MANUAL-' + tag,
    items: [{ materialId: 1683, materialCode: '0001', materialName: '小助手', quantity: 10, unitPrice: 100, taxRate: 13, discountRate: 0 }]
  })
  if (r.code !== 200) throw new Error('创建销售订单失败: ' + JSON.stringify(r).slice(0, 300))
  created.orders.push(r.data.orderId)
  return r.data
}
async function approveContract (id) {
  const d = (await req('GET', '/api/sale/contracts/' + id)).data
  if (d?.status === '已生效') return d
  await req('POST', '/api/sale/contracts/' + id + '/approve')
  return (await req('GET', '/api/sale/contracts/' + id)).data
}
async function approveOrder (id) {
  const d = (await req('GET', '/api/sale/orders/' + id)).data
  if (d?.status === '已生效') return d
  await req('POST', '/api/sale/orders/' + id + '/approve')
  return (await req('GET', '/api/sale/orders/' + id)).data
}
async function cleanup () {
  for (const id of created.orders.slice().reverse()) {
    try { await req('POST', '/api/sale/orders/' + id + '/reverse-approve'); await req('DELETE', '/api/sale/orders/' + id) } catch (e) {}
  }
  for (const id of created.contracts.slice().reverse()) {
    try { await req('POST', '/api/sale/contracts/' + id + '/reverse-approve'); await req('DELETE', '/api/sale/contracts/' + id) } catch (e) {}
  }
}

;(async () => {
  const login = await j(await fetch(API + '/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: USER, password: PWD }) }))
  H = { Authorization: 'Bearer ' + login.data.token, 'Content-Type': 'application/json' }
  assert(!!login.data?.token, '管理员登录')

  const origOrderAuto = await setSerialAuto('SALE_ORDER', 0)
  const origContractAuto = await setSerialAuto('SALE_CONTRACT', 0)
  assert(origOrderAuto !== null && origContractAuto !== null, '两个单据均切换为手动编号')
  assert((await getSerial('SALE_ORDER'))?.autoGenerate === 0 && (await getSerial('SALE_CONTRACT'))?.autoGenerate === 0, '手动编号设置已落库')

  try {
    const stamp = Date.now()
    // ① 合同手动编号 → 自动订单仍按编号规则取号
    await setCfg('sale.auto.push.contract.to.order', 'true')
    await setCfg('sale.auto.push.order.to.contract', 'false')
    const cNo = 'SC-MAN-' + stamp
    const c = await createContract('C2O', cNo)
    assert(c.contractNo === cNo, '销售合同使用人工填写编号', c.contractNo)
    const cAfter = await approveContract(c.contractId)
    assert(cAfter?.status === '已生效', '手动编号合同审核后已生效', cAfter?.status)
    const orders = (await req('GET', '/api/sale/orders?current=1&size=500')).data?.records || []
    const autoOrder = orders.find(o => o.sourceBillNo === cNo)
    assert(!!autoOrder, '手动编号模式下仍自动生成销售订单')
    if (autoOrder) {
      created.orders.push(autoOrder.orderId)
      assert(autoOrder.status === '已生效', '自动订单状态为已生效', autoOrder.status)
      assert(!!autoOrder.orderNo && autoOrder.orderNo.startsWith('SO'), '自动订单由系统按编号规则取号', autoOrder.orderNo)
    }

    // ② 订单手动编号 → 自动合同仍按编号规则取号
    await setCfg('sale.auto.push.contract.to.order', 'false')
    await setCfg('sale.auto.push.order.to.contract', 'true')
    const oNo = 'SO-MAN-' + stamp
    const o = await createOrder('O2C', oNo)
    assert(o.orderNo === oNo, '销售订单使用人工填写编号', o.orderNo)
    const oAfter = await approveOrder(o.orderId)
    assert(oAfter?.status === '已生效', '手动编号订单审核后已生效', oAfter?.status)
    const contracts = (await req('GET', '/api/sale/contracts?current=1&size=500')).data?.records || []
    const autoContract = contracts.find(x => x.sourceBillNo === oNo)
    assert(!!autoContract, '手动编号模式下仍自动生成销售合同')
    if (autoContract) {
      created.contracts.push(autoContract.contractId)
      assert(autoContract.status === '已生效', '自动合同状态为已生效', autoContract.status)
      assert(!!autoContract.contractNo && autoContract.contractNo.startsWith('SCON'), '自动合同由系统按编号规则取号', autoContract.contractNo)

      // ③ 服务端：自动合同不能再作为来源下推订单
      const bad = await req('POST', '/api/sale/orders', {
        sourceBillType: '销售合同', sourceBillNo: autoContract.contractNo,
        orderDate: today, customerId: 15, currencyId: 1, exchangeRate: 1, discountRate: 0,
        items: [{ materialId: 1683, materialCode: '0001', materialName: '小助手', quantity: 1, unitPrice: 100, taxRate: 13, discountRate: 0 }]
      })
      assert(bad.code !== 200 && String(bad.message || bad.raw || '').includes('自动生成'), '自动生成的合同再次下推订单被服务端拦截', JSON.stringify(bad).slice(0, 240))

      // ④ 前端：自动合同编辑页不显示下推按钮
      const browser = await chromium.launch({ headless: true })
      const page = await browser.newPage({ viewport: { width: 1680, height: 950 } })
      const errs = []
      page.on('console', m => { if (m.type() === 'error') errs.push(m.text().slice(0, 200)) })
      page.on('pageerror', e => errs.push('pageerror: ' + String(e).slice(0, 200)))
      await page.addInitScript(t => {
        localStorage.setItem('token', t)
        localStorage.setItem('username', 'admin'); localStorage.setItem('realName', 'admin')
        localStorage.setItem('userId', '1'); localStorage.setItem('tenantId', '1'); localStorage.setItem('tenantName', 'A公司-完整模式')
        localStorage.setItem('roles', JSON.stringify(['ADMIN'])); localStorage.setItem('permissions', JSON.stringify(['*']))
      }, login.data.token)
      await page.goto(BASE + '/sale/contracts/' + autoContract.contractId, { waitUntil: 'domcontentloaded', timeout: 120000 })
      await page.waitForTimeout(6000)
      const pushCount = await page.locator('button:visible', { hasText: '下推' }).count()
      assert(pushCount === 0, '自动生成的销售合同编辑页不显示下推按钮', '实际=' + pushCount)
      assert(errs.length === 0, '自动合同编辑页控制台 0 报错')
      await browser.close()
    }
  } finally {
    await setSerialAuto('SALE_ORDER', origOrderAuto)
    await setSerialAuto('SALE_CONTRACT', origContractAuto)
    await setCfg('sale.auto.push.contract.to.order', 'false')
    await setCfg('sale.auto.push.order.to.contract', 'false')
  }

  await cleanup()
  console.log('RESULT ' + pass + ' PASS / ' + fail + ' FAIL')
  process.exit(fail ? 1 : 0)
})().catch(e => { console.error(e); process.exit(2) })
