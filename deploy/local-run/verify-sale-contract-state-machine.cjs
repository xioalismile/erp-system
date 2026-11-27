// 销售合同接入统一状态机 · 生命周期回归（2026-11-09）
// 覆盖：提交/审核/结案/反结案/重置草稿/作废/反作废/删除 + 下游守卫 + 明细级联清理
// 依赖：本地后端 8080
const { execFileSync } = require('child_process')
const API = 'http://localhost:8080'
const USER = process.env.ERP_USER || 'admin'
const PWD = process.env.ERP_PWD || 'admin123'
const TAG = 'CONTRACT-SM-TEST-' + Date.now()
let pass = 0, fail = 0
const assert = (c, n, extra) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (extra ? ' | ' + extra : '')) } }
const j = async r => { const t = await r.text(); try { return JSON.parse(t) } catch (e) { return { raw: t } } }
const today = new Date().toISOString().slice(0, 10)
let H = {}
const req = async (m, p, b) => j(await fetch(API + p, { method: m, headers: b ? { ...H, 'Content-Type': 'application/json' } : H, body: b ? JSON.stringify(b) : undefined }))
const setCfg = (k, v) => req('PUT', '/api/sys-config/' + encodeURIComponent(k), { configValue: v })

async function createContract () {
  const r = await req('POST', '/api/sale/contracts', {
    contractNo: '', contractDate: today, contractType: '销售合同', customerId: 15, currencyId: 1,
    exchangeRate: 1, discountRate: 0, deliveryDate: today, remark: TAG,
    items: [{ materialId: 1683, materialCode: '0001', materialName: '小助手', quantity: 10, unitPrice: 100, taxRate: 13, discountRate: 0 }]
  })
  if (r.code !== 200) throw new Error('创建合同失败: ' + JSON.stringify(r).slice(0, 200))
  return r.data
}
const getContract = async id => (await req('GET', '/api/sale/contracts/' + id)).data
const getItems = async id => (await req('GET', '/api/sale/contracts/' + id)).data?.items || []
function cleanupBySql () {
  const sql = [
    "DELETE FROM sale_contract_item WHERE contract_id IN (SELECT contract_id FROM sale_contract WHERE remark LIKE '%" + TAG + "%');",
    "DELETE FROM sale_contract WHERE remark LIKE '%" + TAG + "%';"
  ].join(' ')
  try {
    execFileSync('psql', ['-h', 'localhost', '-p', '5432', '-U', 'erp_user', '-d', 'erp_db', '-v', 'ON_ERROR_STOP=1', '-c', sql], {
      env: { ...process.env, PGPASSWORD: process.env.PGPASSWORD || 'Erp@2026' },
      stdio: 'ignore'
    })
  } catch (e) { console.log('WARN cleanup sql failed: ' + e.message) }
}

;(async () => {
  const login = await j(await fetch(API + '/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: USER, password: PWD }) }))
  H = { Authorization: 'Bearer ' + login.data.token, 'Content-Type': 'application/json' }
  assert(!!login.data?.token, '管理员登录')
  await setCfg('sale.auto.push.contract.to.order', 'false')
  await setCfg('sale.auto.push.order.to.contract', 'false')

  const c = await createContract()
  assert(c?.status === '草稿', '新建合同为草稿', c?.status)

  // 提交：审批开关关了则直接生效；开了则待审核再审核
  const sub = await req('POST', '/api/sale/contracts/' + c.contractId + '/submit')
  assert(sub.code === 200, '提交接口成功', JSON.stringify(sub).slice(0, 200))
  let cur = await getContract(c.contractId)
  if (cur.status === '待审核') {
    const ap = await req('POST', '/api/sale/contracts/' + c.contractId + '/approve')
    assert(ap.code === 200, '待审核状态审核成功', JSON.stringify(ap).slice(0, 200))
    cur = await getContract(c.contractId)
  }
  assert(cur.status === '已生效', '提交后合同已生效', cur.status)

  // 结案 / 反结案
  const close = await req('POST', '/api/sale/contracts/' + c.contractId + '/close-order')
  cur = await getContract(c.contractId)
  assert(close.code === 200 && cur.closeStatus === 1, '结案后关闭状态=1', 'close=' + close.code + ' closeStatus=' + cur.closeStatus)
  const reopen = await req('POST', '/api/sale/contracts/' + c.contractId + '/reopen')
  cur = await getContract(c.contractId)
  assert(reopen.code === 200 && cur.closeStatus === 0, '反结案后关闭状态=0', 'reopen=' + reopen.code + ' closeStatus=' + cur.closeStatus)

  // 重置草稿：审核人/时间清空
  const rev = await req('POST', '/api/sale/contracts/' + c.contractId + '/reverse-approve')
  cur = await getContract(c.contractId)
  assert(rev.code === 200 && cur.status === '草稿', '重置草稿成功', 'code=' + rev.code + ' status=' + cur.status)
  assert(!cur.approvedBy && !cur.approvedAt, '重置草稿后审核人/审核时间已清空', JSON.stringify({ by: cur.approvedBy, at: cur.approvedAt }))

  // 再次审核生效
  const ap2 = await req('POST', '/api/sale/contracts/' + c.contractId + '/approve')
  cur = await getContract(c.contractId)
  assert(ap2.code === 200 && cur.status === '已生效', '草稿再次审核生效', cur.status)

  // 作废：记录作废前状态，反作废恢复
  const vd = await req('POST', '/api/sale/contracts/' + c.contractId + '/void', { reason: 'lifecycle test' })
  cur = await getContract(c.contractId)
  assert(vd.code === 200 && cur.status === '已作废', '作废成功', cur.status)
  assert(cur.voidedBy && cur.voidReason === 'lifecycle test', '作废人与原因已落库', JSON.stringify({ by: cur.voidedBy, reason: cur.voidReason }))
  assert(cur.billBeforeStatus === 2, '记录了作废前状态=已生效(2)', String(cur.billBeforeStatus))
  const uv = await req('POST', '/api/sale/contracts/' + c.contractId + '/unvoid')
  cur = await getContract(c.contractId)
  assert(uv.code === 200 && cur.status === '已生效', '反作废恢复为已生效', cur.status)

  // 下游守卫：重置草稿前有已生效下游必须拦截
  const o = await req('POST', '/api/sale/orders', {
    orderNo: '', orderDate: today, customerId: 15, currencyId: 1, exchangeRate: 1, discountRate: 0,
    deliveryDate: today, remark: TAG, sourceBillType: '销售合同', sourceBillNo: cur.contractNo,
    items: [{ materialId: 1683, materialCode: '0001', materialName: '小助手', quantity: 1, unitPrice: 100, taxRate: 13, discountRate: 0 }]
  })
  if (o.code === 200) {
    await req('POST', '/api/sale/orders/' + o.data.orderId + '/approve')
    const revBlocked = await req('POST', '/api/sale/contracts/' + c.contractId + '/reverse-approve')
    assert(revBlocked.code !== 200 && String(revBlocked.message || '').includes('下游'),
      '存在已生效下游时重置草稿被拦截', JSON.stringify(revBlocked).slice(0, 200))
    const voidBlocked = await req('POST', '/api/sale/contracts/' + c.contractId + '/void', { reason: 'should block' })
    assert(voidBlocked.code !== 200 && String(voidBlocked.message || '').includes('下游'),
      '存在已生效下游时作废被拦截', JSON.stringify(voidBlocked).slice(0, 200))
  }

  // 删除：先作废下游订单再重置草稿，最后删除整单（明细级联清理）
  if (o.code === 200) {
    await req('POST', '/api/sale/orders/' + o.data.orderId + '/void', { reason: 'cleanup' })
  }
  await req('POST', '/api/sale/contracts/' + c.contractId + '/reverse-approve')
  const del = await req('DELETE', '/api/sale/contracts/' + c.contractId)
  assert(del.code === 200, '草稿状态删除成功', JSON.stringify(del).slice(0, 200))
  const gone = await req('GET', '/api/sale/contracts/' + c.contractId)
  assert(gone.code !== 200 || gone.data == null, '删除后合同查不到', JSON.stringify(gone).slice(0, 160))

  cleanupBySql()
  console.log('RESULT ' + pass + ' PASS / ' + fail + ' FAIL')
  process.exit(fail ? 1 : 0)
})().catch(e => { console.error(e); cleanupBySql(); process.exit(2) })
