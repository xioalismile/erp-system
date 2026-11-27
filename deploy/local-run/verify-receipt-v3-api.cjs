// 收款单 v3 接口级验证（本地 8082）
const API = process.argv[2] || 'http://localhost:8082'
const USER = process.env.ERP_USER || 'admin'
const PWD = process.env.ERP_PWD || 'admin123'
let pass = 0, fail = 0
const assert = (c, n, extra) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (extra ? ' | ' + extra : '')) } }
const j = async (r) => { const t = await r.text(); try { return JSON.parse(t) } catch (e) { return { raw: t } } }
;(async () => {
  const login = await j(await fetch(API + '/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: USER, password: PWD }) }))
  const TOKEN = login?.data?.token
  assert(login?.code === 200 && !!TOKEN, '登录成功')
  const H = { Authorization: 'Bearer ' + TOKEN, 'Content-Type': 'application/json' }

  // 1) 列表带「来源」字段
  const list = await j(await fetch(API + '/api/finance/receipts?current=1&size=5', { headers: H }))
  assert(list.code === 200 && Array.isArray(list.data?.records), '收款单列表接口可用')
  const recs = list.data?.records || []
  assert(recs.length === 0 || recs.every(r => r.sourceType !== undefined), '列表记录包含「来源」字段')
  assert(recs.length === 0 || recs.every(r => r.sourceType), '历史单据「来源」已回填（非空）')

  // 2) 已核销汇总接口
  const wo = await j(await fetch(API + '/api/finance/receipts/writeoff-received?sourceTypes=' + encodeURIComponent('销售订单,销售出库单,销售发票'), { headers: H }))
  assert(wo.code === 200 && Array.isArray(wo.data), '已核销金额汇总接口可用')
  assert((wo.data || []).every(x => x.sourceType && x.amount !== undefined), '汇总行包含 类型/金额')

  // 3) 三类来源都有已生效数据可供选单
  const deliveries = await j(await fetch(API + '/api/sale/deliveries?current=1&size=5&status=' + encodeURIComponent('已生效'), { headers: H }))
  const invoices = await j(await fetch(API + '/api/sale/invoices?current=1&size=5&status=' + encodeURIComponent('已生效'), { headers: H }))
  const orders = await j(await fetch(API + '/api/sale/orders?current=1&size=5&status=' + encodeURIComponent('已生效'), { headers: H }))
  const norm = (r) => (r.data?.records || r.records || [])
  const dRows = norm(deliveries), iRows = norm(invoices), oRows = norm(orders)
  console.log('数据量：订单', oRows.length, '出库单', dRows.length, '发票', iRows.length)
  assert(deliveries.code === 200, '销售出库单列表可查')
  assert(invoices.code === 200, '销售发票列表可查')

  // 4) 拿一张出库单 + 一张发票 → 建草稿收款单（两张来源混排，验证「一个明细列表」）
  let picked = []
  if (dRows[0]) picked.push({ type: '销售出库单', row: dRows[0] })
  if (iRows[0]) picked.push({ type: '销售发票', row: iRows[0] })
  if (picked.length === 0 && oRows[0]) picked.push({ type: '销售订单', row: oRows[0] })
  if (picked.length > 0) {
    const writeOffItems = []
    for (const p of picked) {
      const no = p.row.deliveryNo || p.row.invoiceNo || p.row.orderNo
      const id = p.row.deliveryId || p.row.invoiceId || p.row.orderId
      const detailUrl = p.type === '销售出库单' ? `/api/sale/deliveries/${id}`
        : p.type === '销售发票' ? `/api/sale/invoices/${id}` : `/api/sale/orders/${id}`
      const detail = await j(await fetch(API + detailUrl, { headers: H }))
      const d = detail.data || detail
      const items = d.items || []
      if (!items.length) continue
      const it = items[0]
      const itemId = it.deliveryItemId || it.invoiceItemId || it.orderItemId
      const net = Number(it.netAmount || 0)
      if (net <= 0) continue
      writeOffItems.push({
        sourceType: p.type, sourceId: id, sourceNo: no,
        sourceAmount: net, usedAmount: 0, writeoffAmount: Math.min(10, net),
        sourceItemId: itemId, sourceItemName: it.materialName || '',
        settledQty: '未冲销', waiveRemainder: 0
      })
    }
    assert(writeOffItems.length >= 1, '能取到可收款的来源明细行')
    if (writeOffItems.length) {
      const total = writeOffItems.reduce((s, r) => s + r.writeoffAmount, 0)
      const payload = {
        receiptDate: new Date().toISOString().slice(0, 10),
        customerId: picked[0].row.customerId, customerName: picked[0].row.customerName,
        sourceType: '手工新增',
        status: '草稿', approvalStatus: '草稿', exchangeRate: 1,
        totalAmount: total, amount: total, receivedAmount: total, discountAmount: 0,
        items: [{ amount: total, baseAmount: total, remark: '接口验证' }],
        writeOffItems
      }
      const created = await j(await fetch(API + '/api/finance/receipts', { method: 'POST', headers: H, body: JSON.stringify(payload) }))
      assert(created.code === 200 && created.data?.receiptId, '创建草稿收款单（两类来源混排）', created.message)
      const id = created.data?.receiptId
      if (id) {
        const det = await j(await fetch(API + `/api/finance/receipts/${id}`, { headers: H }))
        const rows = det.data?.writeOffItems || []
        assert(rows.length === writeOffItems.length, '核销明细落库行数一致（唯一列表）', '期望' + writeOffItems.length + '实际' + rows.length)
        assert(rows.every(r => ['销售订单', '销售出库单', '销售发票'].includes(r.sourceType)), '核销明细来源类型正确')
        assert(det.data?.sourceType === '手工新增', '来源标识落库=手工新增')
        // 清理：删除草稿
        const del = await j(await fetch(API + `/api/finance/receipts/${id}`, { method: 'DELETE', headers: H }))
        assert(del.code === 200, '删除验证草稿')
      }
    }
  }
  console.log(`\n结果：PASS ${pass} / FAIL ${fail}`)
  process.exit(fail ? 1 : 0)
})().catch(e => { console.error(e); process.exit(2) })
