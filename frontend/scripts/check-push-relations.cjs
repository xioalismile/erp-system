#!/usr/bin/env node
/**
 * 单据关系「两套登记表」一致性体检（第 8 项）。
 *
 * 背景：单据来源/去向关系目前有两处登记，长期存在口径漂移风险：
 *   ① 后端台账 frontend 无关 → backend/src/main/java/com/erp/common/bill/link/BillLinkRegistry.java
 *      （docType 编码 + 来源/去向注册，决定 /api/bill-link 与 AI 能否识别该单据）
 *   ② 前端下推关系 frontend/src/utils/pushRelations.js 的 DOC_TYPE
 *      （docType 编码 + 实体主键/单号字段，决定"选来源单/下推预填"能否取到数据）
 * 两处是**不同技术栈的两份定义**（前端那份还含 itemMapper 等 JS 函数，无法由后端 JSON 表达），
 * 不可能合并成单一数据源；因此改为**每次提交前可自动发现的差异清单**，替代"等用户点到才炸"。
 *
 * 判据：
 *   - 两边编码集合必须双向一致（allowlist 之外的不一致 = ERROR）
 *   - 每个编码在前端必须配 idField / noField（缺 = ERROR，会导致详情拉不到、单号列恒空）
 *
 * 用法：node scripts/check-push-relations.cjs   （在 frontend 目录执行）
 * 退出码：0 = 通过（或仅 allowlist 内的已知差异）；1 = 存在未受控差异
 */
const fs = require('fs')
const path = require('path')

const ROOT = path.resolve(__dirname, '..', '..')
const JS_FILE = path.join(ROOT, 'frontend', 'src', 'utils', 'pushRelations.js')
const JAVA_FILE = path.join(
  ROOT,
  'backend', 'src', 'main', 'java', 'com', 'erp', 'common', 'bill', 'link', 'BillLinkRegistry.java'
)

/**
 * 已知且**有意为之**的差异：{ 编码, side: 'frontend'|'backend', reason }
 * 新增前必须先确认业务上确实如此，不允许为了"让脚本变绿"而加。
 */
const ALLOWED = [
  // 委外发料单 / 委外入库单：前端这两个编码同时被**打印模板（print_template.doc_type）、
  // 单号规则（sys_serial_no_config.doc_type）、库存流水类型**引用，属"打印/单号体系"的标识；
  // 后端台账同两张单据用的是 SUBCONTRACT_DELIVERY / SUBCONTRACT_RECEIPT。
  // 两套域各自自洽，改名会打断打印模板与单号规则 → 记为已知差异，不再当作错配。
  { code: 'SUBCONTRACT_PICK', side: 'frontend', reason: '打印/单号体系编码（台账侧同单据为 SUBCONTRACT_DELIVERY）' },
  { code: 'SUBCONTRACT_INBOUND', side: 'frontend', reason: '打印/单号体系编码（台账侧同单据为 SUBCONTRACT_RECEIPT）' }
]

function extractFrontendDocTypes () {
  const src = fs.readFileSync(JS_FILE, 'utf8')
  const start = src.indexOf('export const DOC_TYPE')
  if (start < 0) throw new Error('未找到 DOC_TYPE 定义')
  // 从 DOC_TYPE 定义处向后找到第一个顶格 '}'（对象结束）
  const rest = src.slice(start)
  const endIdx = rest.search(/\n\}\s*\n/)
  const block = endIdx > 0 ? rest.slice(0, endIdx) : rest
  const codes = []
  const re = /^\s{2}([A-Z][A-Z0-9_]*)\s*:\s*\{/gm
  let m
  while ((m = re.exec(block)) !== null) codes.push(m[1])
  // 同时校验每个条目是否配了 idField / noField
  const missingFields = []
  for (const code of codes) {
    const one = new RegExp(`^\\s{2}${code}\\s*:\\s*\\{([^}]*)\\}`, 'm')
    const hit = block.match(one)
    if (!hit) continue
    const body = hit[1]
    if (!/idField\s*:/.test(body)) missingFields.push(`${code} 缺 idField`)
    if (!/noField\s*:/.test(body)) missingFields.push(`${code} 缺 noField`)
  }
  return { codes, missingFields }
}

function extractBackendDocTypes () {
  const src = fs.readFileSync(JAVA_FILE, 'utf8')
  const codes = []
  const re = /register\(\s*doc\(\s*"([A-Z0-9_]+)"/g
  let m
  while ((m = re.exec(src)) !== null) codes.push(m[1])
  return codes
}

function allowed (code, side) {
  return ALLOWED.some(a => a.code === code && a.side === side)
}

function main () {
  const fe = extractFrontendDocTypes()
  const be = extractBackendDocTypes()
  const feSet = new Set(fe.codes)
  const beSet = new Set(be)
  const problems = []
  const warnings = []

  // 后端有、前端无 → WARN：后端台账是"全量登记"（含应收/应付/收付款单这类**不承载前端下推/选单**
  // 的类型），而前端 DOC_TYPE 只覆盖"需要前端选单/下推预填"的子集，故缺项属正常，仅提示不判失败。
  for (const c of beSet) {
    if (!feSet.has(c) && !allowed(c, 'backend')) {
      warnings.push(`后端台账有「${c}」，前端未登记（若该单据需要前端"选单/下推预填"，必须补 DOC_TYPE）`)
    }
  }
  // 前端有、后端无 → ERROR：前端声明了能力，但后端台账不认识该编码
  for (const c of feSet) {
    if (!beSet.has(c) && !allowed(c, 'frontend')) {
      problems.push(`前端 pushRelations.DOC_TYPE 有「${c}」，后端 BillLinkRegistry 未登记（台账域不认识该编码；若它属打印/单号体系的其它编码，请加入本脚本 ALLOWED 并写明理由）`)
    }
  }
  for (const s of fe.missingFields) {
    problems.push(`前端字段缺失：${s}（详情接口字段名必须与实体 JSON 完全一致，写错不报错、只表现为恒空）`)
  }

  console.log('=== 单据关系登记表一致性体检 ===')
  console.log(`后端 BillLinkRegistry docType：${beSet.size} 个`)
  console.log(`前端 pushRelations.DOC_TYPE：${feSet.size} 个`)
  if (ALLOWED.length) {
    console.log(`allowlist（有意差异）：${ALLOWED.map(a => a.code + '/' + a.side).join(', ')}`)
  }
  if (warnings.length) {
    console.log(`提示（不判失败）${warnings.length} 条：`)
    warnings.forEach((w, i) => console.log(`  ${i + 1}. ${w}`))
  }
  if (problems.length === 0) {
    console.log('RESULT=PASS 未受控差异 0 条')
    process.exit(0)
  }
  console.log(`RESULT=FAIL 未受控差异 ${problems.length} 条：`)
  problems.forEach((p, i) => console.log(`  ${i + 1}. ${p}`))
  process.exit(1)
}

main()
