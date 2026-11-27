// 自动下推单据 · 接口级回归（本地 8080）
// 覆盖：开关开/关持久化、销售合同→销售订单、销售订单→销售合同、防循环、防重复、来源关联、失败回滚不适用
// 用法：node deploy/local-run/verify-sale-auto-push.cjs [API]
const API = process.argv[2] || 'http://localhost:8080'
const CFG_C2O = 'sale.auto.push.contract.to.order'
const CFG_O2C = 'sale.auto.push.order.to.contract'
let pass = 0, fail = 0
const assert = (c, n, extra) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (extra ? ' | ' + extra : '')) } }
const today = new Date().toISOString().slice(0, 10)
const plusDays = n => { const d = new Date(); d.setDate(d.getDate() + n); return d.toISOString().slice(0, 10) }
let H = {}
const j = async r => { const t = await r.text(); try { return JSON.parse(t) } catch (e) { return { raw: t } } }
const req = async (method, path, body) => j(await fetch(API + path, {
  method,
  headers: body ? { ...H, 'Content-Type': 'application/json' } : H,
  body: body ? JSON.stringify(body) : undefined
}))
const getCfg = async key => (await req('GET', '/api/sys-config/' + encodeURIComponent(key)))?.data?.configValue
const setCfg = async (key, value) => req('PUT', '/api/sys-config/' + encodeURIComponent(key), { configValue: value })
const created = { contracts: [], orders: [] }

async function approveContract(id) {
  const d = await req('GET', '/api/sale/contracts/' + id)
  if (d.data?.status === '已生效') return d.data
  if (d.data?.status !== '待审核') {
    const s = await req('POST', '/api/sale/contracts/' + id + '/submit')
    if (s.code !== 200) {
      // 审批开启但无可用流程时提交会报错，直接走审核（草稿允许直接审核）
    }
  }
  const after = await req('GET', '/api/sale/contracts/' + id)
  if (after.data?.status === '已生效') return after.data
  await req('POST', '/api/sale/contracts/' + id + '/approve')
  return (await req('GET', '/api/sale/contracts/' + id)).data
}

async function approveOrder(id) {
  const d = await req('GET', '/api/sale/orders/' + id)
  if (d.data?.status === '已生效') return d.data
  if (d.data?.status !== '待审核') {
    const s = await req('POST', '/api/sale/orders/' + id + '/submit')
    if (s.code !== 200) {
      // 同上：无审批流程时直接审核
    }
  }
  const after = await req('GET', '/api/sale/orders/' + id)
  if (after.data?.status === '已生效') return after.data
  await req('POST', '/api/sale/orders/' + id + '/approve')
  return (await req('GET', '/api/sale/orders/' + id)).data
}

async function createContract(tag, materialId = 1683, unitPrice = 100) {
  const r = await req('POST', '/api/sale/contracts', {
    contractDate: today,
    contractType: '销售合同',
    customerId: 15,
    currencyId: 1,
    exchangeRate: 1,
    discountRate: 0,
    deliveryDate: plusDays(7),
    remark: 'AUTO-PUSH-TEST-' + tag,
    items: [{
      materialId, materialCode: materialId === 1683 ? '0001' : 'WL001', materialName: materialId === 1683 ? '小助手' : '测试物料',
      quantity: 10, unitPrice, taxRate: 13, discountRate: 0
    }]
  })
  if (r.code !== 200) throw new Error('创建销售合同失败: ' + JSON.stringify(r).slice(0, 300))
  created.contracts.push(r.data.contractId)
  return r.data
}

async function createOrder(tag) {
  const r = await req('POST', '/api/sale/orders', {
    orderDate: today,
    customerId: 15,
    currencyId: 1,
    exchangeRate: 1,
    discountRate: 0,
    deliveryDate: plusDays(7),
    remark: 'AUTO-PUSH-TEST-' + tag,
    items: [{
      materialId: 1683, materialCode: '0001', materialName: '小助手',
      quantity: 10, unitPrice: 100, taxRate: 13, discountRate: 0
    }]
  })
  if (r.code !== 200) throw new Error('创建销售订单失败: ' + JSON.stringify(r).slice(0, 300))
  created.orders.push(r.data.orderId)
  return r.data
}

async function cleanup() {
  // 先处理自动生成的目标单，再处理源单；全部尽力而为，不因清理失败影响结论
  for (const id of created.orders.slice().reverse()) {
    try {
      await req('POST', '/api/sale/orders/' + id + '/reverse-approve')
      await req('DELETE', '/api/sale/orders/' + id)
    } catch (e) {}
  }
  for (const id of created.contracts.slice().reverse()) {
    try {
      await req('POST', '/api/sale/contracts/' + id + '/reverse-approve')
      await req('DELETE', '/api/sale/contracts/' + id)
    } catch (e) {}
  }
}

;(async () => {
  // 0) 登录
  const login = await j(await fetch(API + '/api/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin123' })
  }))
  H = { Authorization: 'Bearer ' + login.data.token, 'Content-Type': 'application/json' }
  assert(!!login.data?.token, '管理员登录')

  // 1) 开关默认关闭 + 双向持久化
  await setCfg(CFG_C2O, 'false')
  await setCfg(CFG_O2C, 'false')
  assert((await getCfg(CFG_C2O)) === 'false', '开关一保存为关闭并回读一致')
  assert((await getCfg(CFG_O2C)) === 'false', '开关二保存为关闭并回读一致')

  // 2) 合同 → 订单
  await setCfg(CFG_C2O, 'true')
  await setCfg(CFG_O2C, 'false')
  assert((await getCfg(CFG_C2O)) === 'true', '开关一开启后回读为 true')

  const c1 = await createContract('C2O')
  const c1After = await approveContract(c1.contractId)
  assert(c1After?.status === '已生效', '销售合同审核后状态为已生效', c1After?.status)

  const orderList1 = await req('GET', '/api/sale/orders?current=1&size=500')
  const autoOrder = (orderList1.data?.records || []).find(o => o.sourceBillNo === c1.contractNo)
  assert(!!autoOrder, '合同生效后自动生成销售订单')
  if (autoOrder) {
    created.orders.push(autoOrder.orderId)
    assert(autoOrder.status === '已生效', '自动生成的销售订单状态为已生效', autoOrder.status)
    assert(autoOrder.sourceBillType === '销售合同', '自动订单记录来源单据类型=销售合同', autoOrder.sourceBillType)
    const od = await req('GET', '/api/sale/orders/' + autoOrder.orderId)
    assert((od.data?.items || []).length === 1, '自动订单明细已落库 1 行', String((od.data?.items || []).length))
    assert(Number(od.data?.items?.[0]?.quantity) === 10, '自动订单明细数量=10', String(od.data?.items?.[0]?.quantity))
    assert(Math.abs(Number(od.data?.netAmount || 0) - 1130) < 0.01, '自动订单价税合计=1130.00', String(od.data?.netAmount))

    // 防循环：自动订单生效不会再生成销售合同
    const cList1 = await req('GET', '/api/sale/contracts?current=1&size=500')
    const loopContract = (cList1.data?.records || []).find(c => c.sourceBillNo === autoOrder.orderNo)
    assert(!loopContract, '自动生成的订单不会反向生成销售合同')

    // 来源/去向页签关联
    const t1 = await req('GET', '/api/bill-link/targets?billType=SALE_CONTRACT&billId=' + c1.contractId)
    assert(t1.code === 200 && JSON.stringify(t1.data || {}).includes(autoOrder.orderNo),
      '合同去向页签能查到自动生成的销售订单', JSON.stringify(t1.data || {}).slice(0, 200))
    const s1 = await req('GET', '/api/bill-link/sources?billType=SALE_ORDER&billId=' + autoOrder.orderId)
    assert(s1.code === 200 && JSON.stringify(s1.data || {}).includes(c1.contractNo),
      '自动订单来源页签能查到销售合同', JSON.stringify(s1.data || {}).slice(0, 200))
  }

  // 3) 订单 → 合同
  await setCfg(CFG_C2O, 'false')
  await setCfg(CFG_O2C, 'true')
  assert((await getCfg(CFG_O2C)) === 'true' && (await getCfg(CFG_C2O)) === 'false', '开关切换后回读一致')

  const o1 = await createOrder('O2C')
  const o1After = await approveOrder(o1.orderId)
  assert(o1After?.status === '已生效', '销售订单审核后状态为已生效', o1After?.status)

  const cList2 = await req('GET', '/api/sale/contracts?current=1&size=500')
  const autoContract = (cList2.data?.records || []).find(c => c.sourceBillNo === o1.orderNo)
  assert(!!autoContract, '订单生效后自动生成销售合同')
  if (autoContract) {
    created.contracts.push(autoContract.contractId)
    assert(autoContract.status === '已生效', '自动生成的销售合同状态为已生效', autoContract.status)
    assert(autoContract.sourceBillType === '销售订单', '自动合同记录来源单据类型=销售订单', autoContract.sourceBillType)
    const cd = await req('GET', '/api/sale/contracts/' + autoContract.contractId)
    assert((cd.data?.items || []).length === 1, '自动合同明细已落库 1 行', String((cd.data?.items || []).length))
    assert(Number(cd.data?.items?.[0]?.quantity) === 10, '自动合同明细数量=10', String(cd.data?.items?.[0]?.quantity))
    assert(Math.abs(Number(cd.data?.netAmount || 0) - 1130) < 0.01, '自动合同价税合计=1130.00', String(cd.data?.netAmount))

    // 防循环：自动合同生效不会再生成销售订单
    const oList2 = await req('GET', '/api/sale/orders?current=1&size=500')
    const loopOrder = (oList2.data?.records || []).find(o => o.sourceBillNo === autoContract.contractNo)
    assert(!loopOrder, '自动生成的合同不会反向生成销售订单')

    // 来源/去向页签关联
    const t2 = await req('GET', '/api/bill-link/targets?billType=SALE_ORDER&billId=' + o1.orderId)
    assert(t2.code === 200 && JSON.stringify(t2.data || {}).includes(autoContract.contractNo),
      '订单去向页签能查到自动生成的销售合同', JSON.stringify(t2.data || {}).slice(0, 200))
    const s2 = await req('GET', '/api/bill-link/sources?billType=SALE_CONTRACT&billId=' + autoContract.contractId)
    assert(s2.code === 200 && JSON.stringify(s2.data || {}).includes(o1.orderNo),
      '自动合同来源页签能查到销售订单', JSON.stringify(s2.data || {}).slice(0, 200))
  }

  // 4) 防重复：回滚自动订单后重新审核合同，不应生成第二张订单
  if (autoOrder) {
    await req('POST', '/api/sale/orders/' + autoOrder.orderId + '/reverse-approve')
    await req('POST', '/api/sale/contracts/' + c1.contractId + '/reverse-approve')
    await req('POST', '/api/sale/contracts/' + c1.contractId + '/approve')
    const orderList3 = await req('GET', '/api/sale/orders?current=1&size=500')
    const sameSource = (orderList3.data?.records || []).filter(o => o.sourceBillNo === c1.contractNo)
    assert(sameSource.length === 1, '同一合同重复生效不会生成第二张订单', '实际=' + sameSource.length)
  }

  // 5) 失败回滚：目标单校验不通过时，源单生效整体回滚
  await setCfg(CFG_C2O, 'true')
  await setCfg(CFG_O2C, 'false')
  const cBad = await createContract('FAIL', 1754, 1)
  await approveContract(cBad.contractId)
  const cBadAfter = (await req('GET', '/api/sale/contracts/' + cBad.contractId)).data
  assert(cBadAfter?.status === '草稿', '自动生成失败时合同生效整体回滚为草稿', cBadAfter?.status)
  const orderListBad = await req('GET', '/api/sale/orders?current=1&size=500')
  const badOrder = (orderListBad.data?.records || []).find(o => o.sourceBillNo === cBad.contractNo)
  assert(!badOrder, '自动生成失败时不会留下半张订单')

  // 6) 全关后不再自动生成
  await setCfg(CFG_C2O, 'false')
  await setCfg(CFG_O2C, 'false')
  const c2 = await createContract('OFF')
  await approveContract(c2.contractId)
  const orderList4 = await req('GET', '/api/sale/orders?current=1&size=500')
  const offOrder = (orderList4.data?.records || []).find(o => o.sourceBillNo === c2.contractNo)
  assert(!offOrder, '两个开关关闭后合同生效不自动生成订单')

  console.log(`\n结果：PASS ${pass} / FAIL ${fail}`)
  await cleanup()
  process.exit(fail ? 1 : 0)
})().catch(async e => { console.error(e); try { await cleanup() } catch (_) {} process.exit(2) })
