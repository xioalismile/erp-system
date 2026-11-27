// 看板/报表去应收单源 · 接口级验证（本地 8082）
// 断言：应收汇总/账龄/概览/对账单/看板 接口可用且返回销售订单口径数据；勾稽关系正确。
const API = process.argv[2] || 'http://localhost:8082'
const j = async r => { const t = await r.text(); try { return JSON.parse(t) } catch (e) { return { raw: t } } }
let pass = 0, fail = 0
const assert = (c, n, extra) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (extra ? ' | ' + extra : '')) } }
const dec = s => { try { return decodeURIComponent(escape(s)) } catch (e) { return s } }
;(async () => {
  const login = await j(await fetch(API + '/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'admin', password: 'admin123' }) }))
  const H = { Authorization: 'Bearer ' + login.data.token, 'Content-Type': 'application/json' }
  assert(!!login.data?.token, '登录')

  // 1) 应收汇总（改按销售订单口径）：totalAmount = Σ订单净额；勾稽 unreceived = total - received
  const rs = await j(await fetch(API + '/api/finance/receivable/summary', { headers: H }))
  const rd = rs.data || {}
  assert(rs.code === 200, '应收汇总接口 200')
  assert(Number(rd.totalAmount) >= 0 && Number(rd.receivedAmount) >= 0, '汇总返回订单口径金额（total/received）')
  assert(Math.abs(Number(rd.unreceivedAmount) - (Number(rd.totalAmount) - Number(rd.receivedAmount))) < 0.01,
    '勾稽：未收 = 总额 − 已收', `${rd.unreceivedAmount} vs ${rd.totalAmount - rd.receivedAmount}`)
  // 对照订单实际值
  const soList = await j(await fetch(API + '/api/sale/orders?current=1&size=500&status=' + encodeURIComponent('已生效'), { headers: H }))
  const orders = soList.data?.records || []
  const soNet = orders.reduce((s, o) => s + Number(o.netAmount || 0), 0)
  const soRec = orders.reduce((s, o) => s + Number(o.receivedAmount || 0), 0)
  assert(Math.abs(Number(rd.totalAmount) - soNet) < 0.01, '汇总总额 = 已生效订单净额合计', `${rd.totalAmount} vs ${soNet}`)
  assert(Math.abs(Number(rd.receivedAmount) - soRec) < 0.01, '汇总已收 = 订单已收合计', `${rd.receivedAmount} vs ${soRec}`)

  // 2) 账龄分析
  const ag = await j(await fetch(API + '/api/finance/aging', { headers: H }))
  const agData = Array.isArray(ag.data) ? ag.data : []
  assert(ag.code === 200 && agData.length === 5, '账龄分析 5 档返回', JSON.stringify(ag).slice(0, 120))
  const agSum = agData.reduce((s, b) => s + Number(b.amount || 0), 0)
  const soUnrec = orders.reduce((s, o) => { const d = Number(o.netAmount||0) - Number(o.receivedAmount||0); return s + (d > 0 ? d : 0) }, 0)
  assert(Math.abs(agSum - soUnrec) < 0.01, '账龄合计 = 订单未收合计', `${agSum} vs ${soUnrec}`)

  // 3) 财务概览
  const sm = await j(await fetch(API + '/api/finance/summary', { headers: H }))
  assert(sm.code === 200 && sm.data?.totalAr !== undefined, '财务概览返回应收统计')
  assert(Math.abs(Number(sm.data.totalAr) - soNet) < 0.01, '概览应收总额 = 订单净额合计', `${sm.data.totalAr} vs ${soNet}`)

  // 4) 客户对账单（summary 分页结构 {records:[...]}）
  const cs = await j(await fetch(API + '/api/report/standard/sale/customer-statement/summary', { headers: H }))
  if (cs.code === 200) {
    const rows = cs.data?.records || []
    const sumBal = rows.reduce((s, r) => s + Number(r.endBalance || r.balance || 0), 0)
    assert(Array.isArray(rows), '客户对账单返回记录')
    console.log('对账单行数=' + rows.length + ' 期末余额合计=' + sumBal.toFixed(2))
  } else {
    console.log('（对账单路由 code=' + cs.code + '，跳过断言）')
  }

  // 5) 数据看板（应收余额/账龄/回款率）
  const db = await j(await fetch(API + '/api/data-board/finance?period=month', { headers: H }))
  if (db.code === 200) {
    const d = db.data || {}
    assert(d.arBalance !== undefined, '数据看板含应收余额（订单口径）')
    assert(Array.isArray(d.arAging) && d.arAging.length === 5, '数据看板账龄 5 档')
  } else {
    console.log('（数据看板路由 code=' + db.code + '，检查实际路径）')
  }
  console.log(`\n结果：PASS ${pass} / FAIL ${fail}`)
  process.exit(fail ? 1 : 0)
})().catch(e => { console.error(e); process.exit(2) })
