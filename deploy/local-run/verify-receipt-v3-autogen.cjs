// 收款单 v3：新建干净订单 → 审核 → 自动生成收款单草稿 → 提交 → 核销回写 → 清理
const API = process.argv[2] || 'http://localhost:8082'
const j = async r => { const t = await r.text(); try { return JSON.parse(t) } catch (e) { return { raw: t } } }
let pass = 0, fail = 0
const assert = (c, n, extra) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (extra ? ' | ' + extra : '')) } }
;(async () => {
  const login = await j(await fetch(API + '/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'admin', password: 'admin123' }) }))
  const H = { Authorization: 'Bearer ' + login.data.token, 'Content-Type': 'application/json' }
  const all = await j(await fetch(API + '/api/sys-config', { headers: H }))
  const orig = (all.data || []).find(x => x.configKey === 'finance.ar.source_bill_type')?.configValue || 'none'

  // 取一张样例订单的客户/币种/物料/单位
  const samples = await j(await fetch(API + '/api/sale/orders?current=1&size=1&status=' + encodeURIComponent('已生效'), { headers: H }))
  const sample = (samples.data?.records || [])[0]
  const sd = await j(await fetch(API + '/api/sale/orders/' + sample.orderId, { headers: H }))
  const s = sd.data || sd
  const si = (s.items || [])[0]

  let orderId = null, receiptId = null
  try {
    const payload = {
      customerId: s.customerId, customerName: s.customerName, salerId: s.salerId,
      currencyId: s.currencyId, exchangeRate: 1, orderDate: new Date().toISOString().slice(0, 10),
      taxCalcMethod: '含税价', status: '草稿', remark: '收款单v3自动生成验证（临时单）',
      totalAmount: 100, taxAmount: 11.5, netAmount: 100, discountAmount: 0, discountRate: 0,
      items: [{
        lineNo: 1, materialId: si.materialId, materialCode: si.materialCode, materialName: si.materialName,
        unitId: si.unitId, unit: si.unit, quantity: 1, unitPrice: 100, priceWithTax: 100, priceWithoutTax: 88.50,
        taxRate: 13, amount: 88.50, taxAmount: 11.50, netAmount: 100, discountRate: 0, discountAmount: 0, receivedAmount: 0
      }]
    }
    const created = await j(await fetch(API + '/api/sale/orders', { method: 'POST', headers: H, body: JSON.stringify(payload) }))
    orderId = created.data?.orderId
    assert(created.code === 200 && orderId, '新建干净测试订单（1 行，100 元）', created.message)
    if (!orderId) return

    await fetch(API + '/api/sys-config/batch', { method: 'PUT', headers: H, body: JSON.stringify([{ configKey: 'finance.ar.source_bill_type', configValue: 'sale_order' }]) })
    await fetch(API + '/api/sys-config/refresh', { method: 'POST', headers: H })
    const appr = await j(await fetch(API + `/api/sale/orders/${orderId}/approve`, { method: 'POST', headers: H }))
    assert(appr.code === 200, '订单审核成功', appr.message)

    const od = await j(await fetch(API + `/api/sale/orders/${orderId}`, { headers: H }))
    const orderNo = (od.data || od).orderNo
    const rl = await j(await fetch(API + '/api/finance/receipts?current=1&size=200', { headers: H }))
    const gen = (rl.data?.records || []).find(r => r.sourceBillNo === orderNo && r.sourceBillType === '销售订单')
    assert(!!gen, '订单审核 → 直接生成收款单（不再生成应收单）')
    if (gen) {
      receiptId = gen.receiptId
      assert(gen.status === '草稿', '生成的是草稿', gen.status)
      assert(gen.sourceType === '系统自动生成', '来源=系统自动生成', gen.sourceType)
      assert(Math.abs(Number(gen.amount) - 100) < 0.01, '草稿金额=来源单未收 100', gen.amount)
      const det = await j(await fetch(API + `/api/finance/receipts/${receiptId}`, { headers: H }))
      assert((det.data?.items || []).length === 1, '收款明细 1 行（账户/金额）')
      assert((det.data?.writeOffItems || []).length === 1, '核销明细 1 行（按来源明细行）')
      assert((det.data?.writeOffItems || [])[0]?.sourceType === '销售订单', '核销来源=销售订单')

      // 提交（免审批 → 直接生效）并验证订单已收回写
      const sub = await j(await fetch(API + `/api/finance/receipts/${receiptId}/submit`, { method: 'POST', headers: H }))
      assert(sub.code === 200, '收款单提交成功', sub.message)
      const after = await j(await fetch(API + `/api/finance/receipts/${receiptId}`, { headers: H }))
      assert(['已确认', '待审核'].includes(after.data?.status), '提交后状态推进', after.data?.status)
      if (after.data?.status === '已确认') {
        const o2 = await j(await fetch(API + `/api/sale/orders/${orderId}`, { headers: H }))
        assert(Math.abs(Number((o2.data || o2).receivedAmount || 0) - 100) < 0.01, '订单已收回写=100', (o2.data || o2).receivedAmount)
        const wo = await j(await fetch(API + '/api/finance/receipts/writeoff-received?sourceTypes=' + encodeURIComponent('销售订单'), { headers: H }))
        const hit = (wo.data || []).find(x => x.sourceId === orderId)
        assert(!!hit && Math.abs(Number(hit.amount) - 100) < 0.01, '已核销汇总可查到该订单 100')
      }
    }
  } finally {
    if (receiptId) await fetch(API + `/api/finance/receipts/${receiptId}`, { method: 'DELETE', headers: H }).catch(() => {})
    if (orderId) {
      await fetch(API + `/api/sale/orders/${orderId}/reverse-approve`, { method: 'POST', headers: H }).catch(() => {})
      await fetch(API + `/api/sale/orders/${orderId}`, { method: 'DELETE', headers: H }).catch(() => {})
    }
    await fetch(API + '/api/sys-config/batch', { method: 'PUT', headers: H, body: JSON.stringify([{ configKey: 'finance.ar.source_bill_type', configValue: orig }]) })
    await fetch(API + '/api/sys-config/refresh', { method: 'POST', headers: H })
    const cfg = await j(await fetch(API + '/api/sys-config', { headers: H }))
    assert((cfg.data || []).find(x => x.configKey === 'finance.ar.source_bill_type')?.configValue === orig, '清理：配置已还原')
  }
  console.log(`\n结果：PASS ${pass} / FAIL ${fail}`)
  process.exit(fail ? 1 : 0)
})().catch(e => { console.error(e); process.exit(2) })
