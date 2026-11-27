/**
 * 来源/去向 注册表【实调审计】：对每类单据调用 /registered、/sources、/targets
 * 用法：node tests/_audit-billlink.mjs
 * 依赖：tests/_billlink_ids.json（各类单据的最大主键）
 *
 * 注意：后端对查询异常是 try/catch + log.warn（不抛 500），
 * 因此本脚本只能发现"HTTP 层失败"；静默失败需另外查后端日志里的
 * "查询来源单据失败 / 查询去向单据失败" warn。
 */
import fs from 'fs'

const BASE = 'http://localhost:8080/api'
const ids = JSON.parse(fs.readFileSync(new URL('./_billlink_ids.json', import.meta.url), 'utf8').replace(/^\uFEFF/, ''))

let token = ''
async function api (path) {
  const res = await fetch(BASE + path, { headers: token ? { Authorization: 'Bearer ' + token } : {} })
  const text = await res.text()
  let json
  try { json = text ? JSON.parse(text) : {} } catch (e) { throw new Error('非JSON: ' + text.slice(0, 200)) }
  if (json.code !== 200) throw new Error(`${json.code} ${json.message}`)
  return json.data
}

const login = await fetch(BASE + '/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: 'admin123' })
}).then(r => r.json())
token = login.data.token
console.log('登录成功\n')

const types = Object.keys(ids).sort()
let ok = 0, fail = 0, skipped = 0
const rows = []
for (const t of types) {
  const id = ids[t]
  let reg
  try { reg = await api(`/bill-link/registered?billType=${t}`) } catch (e) { reg = 'ERR:' + e.message }
  if (id <= 0) {
    skipped++
    rows.push(`${t.padEnd(24)} id=0(无数据,跳过实调) registered=${reg}`)
    continue
  }
  let srcInfo, tgtInfo
  try {
    const s = await api(`/bill-link/sources?billType=${t}&billId=${id}`)
    const l = (s && s.list) || []
    srcInfo = `${l.length}张来源单` + (l.length ? `[${l.map(x => x.docName + ':' + (x.items || []).length + '行').join(',')}]` : '')
  } catch (e) { srcInfo = 'ERR:' + e.message }
  try {
    const g = await api(`/bill-link/targets?billType=${t}&billId=${id}`)
    const l = (g && g.list) || []
    tgtInfo = `${l.length}张去向单` + (l.length ? `[${l.map(x => x.docName).join(',')}]` : '')
  } catch (e) { tgtInfo = 'ERR:' + e.message }
  const bad = String(srcInfo).startsWith('ERR') || String(tgtInfo).startsWith('ERR') || reg !== true
  bad ? fail++ : ok++
  rows.push(`${bad ? '[!!]' : '[ok]'} ${t.padEnd(24)} id=${String(id).padEnd(3)} registered=${String(reg).padEnd(5)} 来源=${srcInfo} | 去向=${tgtInfo}`)
}
rows.forEach(r => console.log(r))
console.log(`\n实调通过 ${ok} / 异常 ${fail} / 无数据跳过 ${skipped}`)
