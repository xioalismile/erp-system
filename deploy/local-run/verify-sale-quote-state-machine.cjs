// 销售报价单接入统一状态机 · 生命周期回归（2026-11-09）
const { execFileSync } = require('child_process')
const API = 'http://localhost:8080'
const USER = process.env.ERP_USER || 'admin'
const PWD = process.env.ERP_PWD || 'admin123'
const TAG = 'QUOTE-SM-TEST-' + Date.now()
let pass = 0, fail = 0
const assert = (c, n, extra) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (extra ? ' | ' + extra : '')) } }
const j = async r => { const t = await r.text(); try { return JSON.parse(t) } catch (e) { return { raw: t } } }
const today = new Date().toISOString().slice(0, 10)
let H = {}
const req = async (m, p, b) => j(await fetch(API + p, { method: m, headers: b ? { ...H, 'Content-Type': 'application/json' } : H, body: b ? JSON.stringify(b) : undefined }))
function cleanupBySql () {
  const sql = [
    "DELETE FROM sale_quote_item WHERE quote_id IN (SELECT quote_id FROM sale_quote WHERE remark LIKE '%" + TAG + "%');",
    "DELETE FROM sale_quote WHERE remark LIKE '%" + TAG + "%';"
  ].join(' ')
  try {
    execFileSync('psql', ['-h', 'localhost', '-p', '5432', '-U', 'erp_user', '-d', 'erp_db', '-v', 'ON_ERROR_STOP=1', '-c', sql], {
      env: { ...process.env, PGPASSWORD: process.env.PGPASSWORD || 'Erp@2026' }, stdio: 'ignore'
    })
  } catch (e) { console.log('WARN cleanup failed: ' + e.message) }
}
;(async () => {
  const login = await j(await fetch(API + '/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: USER, password: PWD }) }))
  H = { Authorization: 'Bearer ' + login.data.token, 'Content-Type': 'application/json' }
  assert(!!login.data?.token, '管理员登录')

  const cr = await req('POST', '/api/sale/quotes', {
    quoteNo: '', quoteDate: today, customerId: 15, currencyId: 1, exchangeRate: 1, discountRate: 0,
    remark: TAG,
    items: [{ materialId: 1683, materialCode: '0001', materialName: '小助手', quantity: 10, unitPrice: 100, taxRate: 13, discountRate: 0 }]
  })
  assert(cr.code === 200, '新建报价单成功', JSON.stringify(cr).slice(0, 200))
  const id = cr.data.quoteId
  assert(cr.data.status === '草稿', '新建为草稿', cr.data.status)

  const ap = await req('POST', '/api/sale/quotes/' + id + '/approve')
  let cur = (await req('GET', '/api/sale/quotes/' + id)).data
  assert(ap.code === 200 && cur.status === '已生效', '草稿直接审核生效', 'code=' + ap.code + ' status=' + cur.status)

  const rev = await req('POST', '/api/sale/quotes/' + id + '/reverse-approve')
  cur = (await req('GET', '/api/sale/quotes/' + id)).data
  assert(rev.code === 200 && cur.status === '草稿', '重置草稿成功', 'code=' + rev.code + ' status=' + cur.status)
  assert(!cur.approvedBy && !cur.approvedAt, '重置后审核人/时间已清空', JSON.stringify({ by: cur.approvedBy, at: cur.approvedAt }))

  await req('POST', '/api/sale/quotes/' + id + '/approve')
  const vd = await req('POST', '/api/sale/quotes/' + id + '/void', { reason: 'quote lifecycle' })
  cur = (await req('GET', '/api/sale/quotes/' + id)).data
  assert(vd.code === 200 && cur.status === '已作废', '作废成功', cur.status)
  assert(cur.voidedBy && cur.voidReason === 'quote lifecycle', '作废人/原因已落库', JSON.stringify({ by: cur.voidedBy, reason: cur.voidReason }))
  assert(cur.billBeforeStatus === 2, '记录作废前状态=已生效(2)', String(cur.billBeforeStatus))

  const uv = await req('POST', '/api/sale/quotes/' + id + '/unvoid')
  cur = (await req('GET', '/api/sale/quotes/' + id)).data
  assert(uv.code === 200 && cur.status === '已生效', '反作废恢复已生效', cur.status)

  await req('POST', '/api/sale/quotes/' + id + '/reverse-approve')
  const del = await req('DELETE', '/api/sale/quotes/' + id)
  assert(del.code === 200, '草稿状态删除成功', JSON.stringify(del).slice(0, 160))
  const gone = await req('GET', '/api/sale/quotes/' + id)
  assert(gone.code !== 200 || gone.data == null, '删除后查不到', JSON.stringify(gone).slice(0, 120))

  cleanupBySql()
  console.log('RESULT ' + pass + ' PASS / ' + fail + ' FAIL')
  process.exit(fail ? 1 : 0)
})().catch(e => { console.error(e); cleanupBySql(); process.exit(2) })
