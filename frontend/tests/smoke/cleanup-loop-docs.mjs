/**
 * ⑤e 界面闭环测试单清理（2026-11-11 新增）
 *
 * 为什么需要：03 端到端闭环每跑一次都会在测试租户里真实建一张单据，
 * 并在 L7 把它提交/审核到已生效（供 ⑤c 写链路体检取用）。
 * 体检结束后必须清掉，否则测试库会一轮轮堆积测试单（守则 12：不留测试残留）。
 *
 * 数据来源：tests/smoke/.out/e2e-loop-summary.json（03 落盘的每张单据 apiBase + detailId）
 * 清理顺序：反结案 → 反审核 → 删除，最多重试 3 轮（下游已先被清理时第二轮可成功）。
 *
 * 用法：node tests/smoke/cleanup-loop-docs.mjs
 * 退出码：0（清理尽力而为，失败明细写进报告与 .out/loop-cleanup.json）
 */
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { cfg } from './smoke-env.mjs'
import { login, api } from './smoke-http.mjs'

const HERE = dirname(fileURLToPath(import.meta.url))
const OUT_DIR = join(HERE, '.out')
const SUMMARY = join(OUT_DIR, 'e2e-loop-summary.json')
const REPORT = join(OUT_DIR, 'loop-cleanup.json')

if (!existsSync(SUMMARY)) {
  console.log('未找到 e2e-loop-summary.json（本轮未跑界面闭环），无需清理')
  process.exit(0)
}

const summary = JSON.parse(readFileSync(SUMMARY, 'utf8'))
const targets = (summary.results || []).filter((r) => r.apiBase && r.detailId)
if (!targets.length) {
  console.log('界面闭环没有留下可清理的单据（无 apiBase/detailId）')
  process.exit(0)
}

const { token } = await login()
const done = []
const failed = []

for (const t of targets) {
  const path = `${t.apiBase}/${t.detailId}`
  let ok = false
  let lastMsg = ''
  for (let attempt = 1; attempt <= 3 && !ok; attempt++) {
    await api('POST', `${path}/reopen`, { token })
    await api('POST', `${path}/reverse-approve`, { token })
    const dr = await api('DELETE', path, { token })
    ok = dr.ok
    lastMsg = dr.message || dr.raw || ''
    if (!ok) await new Promise((r) => setTimeout(r, 400))
  }
  if (ok) done.push({ name: t.name, id: t.detailId })
  else failed.push({ name: t.name, id: t.detailId, apiBase: t.apiBase, message: String(lastMsg).slice(0, 200) })
}

writeFileSync(REPORT, JSON.stringify({
  finishedAt: new Date().toISOString(),
  baseUrl: cfg.baseUrl,
  total: targets.length,
  deleted: done.length,
  failed: failed.length,
  done,
  failedItems: failed
}, null, 2))

console.log('\n===== ⑤e 界面闭环测试单清理 =====')
console.log(`  待清理 ${targets.length} 张 → 已删除 ${done.length} 张 / 未能删除 ${failed.length} 张`)
if (failed.length) {
  for (const f of failed) console.log(`  ✗ ${f.name} id=${f.id}：${f.message}`)
  console.log('  未删除的单据已写入 .out/loop-cleanup.json，需人工确认（多数是存在下游单据）')
}
