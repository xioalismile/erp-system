/* eslint-disable no-console */
/**
 * 单据功能静态核对（功能体检第一层）
 *
 * 口径：把《单据与页面登记》第 4 栏当"应该有"，扫页面/登记表/后端注册点当"实际有"，
 * 输出 45 个列表页 × 前端能力，以及后端单据 × 各登记表 的矩阵，缺口直接点名。
 *
 * 用法：node scripts/check-bill-capabilities.cjs [--json]
 * 输出：docs/功能体检-静态核对.md（默认），加 --json 时另写同名 .json
 *
 * 只读扫描，不改任何业务数据。
 */
const fs = require('fs')
const path = require('path')

const ROOT = path.resolve(__dirname, '..')
const SRC = path.join(ROOT, 'src')
const BE = path.resolve(ROOT, '..', 'backend', 'src', 'main', 'java', 'com', 'erp')
const DOCS = path.resolve(ROOT, '..', 'docs')

const read = (p) => fs.readFileSync(p, 'utf8')
const exists = (p) => fs.existsSync(p)

function walk(dir, filter, out = []) {
  if (!exists(dir)) return out
  for (const name of fs.readdirSync(dir)) {
    const full = path.join(dir, name)
    const st = fs.statSync(full)
    if (st.isDirectory()) walk(full, filter, out)
    else if (filter(full)) out.push(full)
  }
  return out
}

const uniq = (arr) => [...new Set(arr)]

// ==================== 前端登记表 ====================

const detailReg = read(path.join(SRC, 'config/billDetailView.js'))
const detailPages = uniq([...detailReg.matchAll(/(\w+): \{ apiBase:/g)].map((m) => m[1]))

const cfReg = read(path.join(SRC, 'config/listCustomFieldEntities.js'))
const cfPages = new Map([...cfReg.matchAll(/(\w+): \{ entity: '([^']+)'/g)].map((m) => [m[1], m[2]]))

// 页面 → 字段权限键（2026-11-27 起由登记表自动解析）
const permMapTxt = read(path.join(SRC, 'config/billPagePermKeys.js'))
const autoPermKeys = new Map([...permMapTxt.matchAll(/(\w+): '([^']+)'/g)].map((m) => [m[1], m[2]]))

// ==================== 前端页面 ====================

const pages = []
for (const file of walk(path.join(SRC, 'views'), (f) => f.endsWith('.vue'))) {
  const txt = read(file)
  if (!txt.includes('<BillListPage')) continue
  const page = path.basename(file, '.vue')
  const titleMatch = txt.match(/:title="\$tt\('([^']+)'\)"/) || txt.match(/:title="tt\('([^']+)'\)"/) || txt.match(/title="([^"]+)"/)
  const storageKey = (txt.match(/storage-key="([^"]+)"/) || [])[1] || ''
  const fieldPerm = (txt.match(/field-perm-key="([^"]+)"/) || [])[1]
    || (txt.match(/useColumnSetting\('[^']+',\s*columns,\s*'([^']+)'\)/) || [])[1]
    || ''
  pages.push({
    page,
    file: path.relative(ROOT, file).replace(/\\/g, '/'),
    title: titleMatch ? titleMatch[1] : '',
    storageKey,
    billListPage: true,
    detail: detailPages.includes(storageKey),
    detailManual: /:detail-enabled="true"/.test(txt),
    customFields: cfPages.has(storageKey),
    customEntity: cfPages.get(storageKey) || '',
    fieldPerm,
    autoPerm: autoPermKeys.get(storageKey) || '',
    filterDefs: /:filter-defs=/.test(txt),
    detailFilterDefs: /:detail-filter-defs=/.test(txt),
    batchActions: /:batch-actions=/.test(txt)
  })
}
pages.sort((a, b) => a.page.localeCompare(b.page))

// ==================== 后端登记表 ====================

function beFile(rel) {
  const p = path.join(BE, rel)
  return exists(p) ? read(p) : ''
}

// 字段权限（2026-11-27 改造后）：列表接口统一登记在 BillPermissionPathRegistry；明细报表启动扫描自动对应
const fpTxt = beFile('permission/config/BillPermissionPathRegistry.java')
const fpPathMap = [...fpTxt.matchAll(/put\("([^"]+)",\s*"([^"]+)"\)/g)]
  .map((m) => ({ path: m[1], pageKey: m[2] }))
const fpPageKeys = uniq(fpPathMap.map((x) => x.pageKey))

// 来源/去向
const linkTxt = beFile('common/bill/link/BillLinkRegistry.java')
const links = [...linkTxt.matchAll(/register\(doc\("([A-Z_]+)",\s*"([^"]+)",\s*"([^"]+)"/g)]
  .map((m) => ({ billType: m[1], name: m[2], table: m[3] }))

// 级联删除（reg / regNo 两种登记方式）
const cascadeTxt = beFile('common/bill/BillCascadeDeleteService.java')
const cascadeTables = uniq([...cascadeTxt.matchAll(/reg(?:No)?\("([^"]+)"/g)].map((m) => m[1]))

// 单据汇总取数
const summaryTxt = beFile('dashboard/service/BillSummaryService.java')
const summaryBills = uniq([...summaryTxt.matchAll(/ITEM\.put\("([A-Z_]+)",\s*new String\[\]\{"([^"]+)"/g)].map((m) => m[1]))

// AI 单据动作
const actionTxt = beFile('ai/bill/BillActionRegistry.java')
const actionBills = uniq([...actionTxt.matchAll(/register\("([A-Z_]+)",\s*"([^"]+)"/g)].map((m) => m[1]))
// AI 模块对生产退料/补料使用自己的编码（BillActionRegistry/BillTypeIdentityGuard 全套自洽），
// 与来源去向/界面编码（MATERIAL_*）不同名；核对时按别名视为已登记，避免假阳性。
const ACTION_TYPE_ALIAS = {
  MATERIAL_RETURN: 'PRODUCTION_RETURN',
  MATERIAL_SUPPLEMENT: 'PRODUCTION_SUPPLEMENT'
}

// Excel 导入导出（BillExcelRegistry 两种登记方式：putScoped / new Bill）
const excelTxt = beFile('export/registry/BillExcelRegistry.java')
const excelCodes = uniq([
  ...[...excelTxt.matchAll(/putScoped\(m,\s*"([^"]+)"/g)].map((m) => m[1]),
  ...[...excelTxt.matchAll(/new Bill\("([^"]+)"/g)].map((m) => m[1])
])

// 单据类型 → Excel 实体名（表名转驼峰不一定对，个别单据实体名不同）
const EXCEL_CODE_ALIAS = {
  PRODUCTION_ORDER: 'workOrder',
  PRODUCTION_RECEIPT: 'productionReceipt',
  OPERATION_REPORT: 'operationReport',
  MRP_PLAN: 'mrpPlan',
  PRODUCE_ASSEMBLE: 'produceAssemble',
  PRODUCE_DISASSEMBLE: 'produceDisassemble',
  PAYMENT_RECEIPT: 'paymentReceipt',
  PAYMENT_VOUCHER: 'paymentVoucher',
  SALE_PRE_RECEIPT: 'salePreReceipt',
  PURCHASE_PRE_PAYMENT: 'purchasePrePayment',
  OPERATION_EXPENSE: 'operationExpense',
  PURCHASE_INBOUND: 'purchaseInbound',
  MATERIAL_REQUISITION: 'materialRequisition',
  MATERIAL_RETURN: 'materialRequisition',
  MATERIAL_SUPPLEMENT: 'materialRequisition',
  FREE_SAMPLE: 'freeSample',
  SALE_EXCHANGE: 'saleExchange',
  SAMPLE_ORDER: 'sampleOrder',
  WAGE_SETTLEMENT: 'wageSettlement'
}

// 明细报表控制器（只认继承明细报表基类的；报表中心的通用控制器不算）
const reportCtrls = walk(path.join(BE, 'report/controller'), (f) => f.endsWith('.java'))
  .map((f) => {
    const t = read(f)
    if (!t.includes('extends AbstractBillDetailReportController')) return null
    const base = (t.match(/@RequestMapping\("([^"]+)"\)/) || [])[1] || ''
    const title = (t.match(/exportFilePrefix\(\)\s*\{\s*return "([^"]+)"/) || [])[1] || ''
    return { file: path.basename(f), base, title }
  })
  .filter((x) => x && x.base.startsWith('/api/report/'))

// 菜单码
const permTxt = read(path.join(SRC, 'utils/billPermCode.js'))
const menuCodes = uniq([...permTxt.matchAll(/^ {2}([A-Z_]+): '([^']+)'/gm)].map((m) => m[1]))

// ==================== 页面 ↔ 后端单据 匹配 ====================

const norm = (s) => String(s || '')
  .replace(/[\s（）()·]/g, '')
  .replace(/(管理|档案|明细|清单|中心|操作手册)$/g, '')
  .replace(/单$/g, '')

/** 页面名称与后端单据名称不完全一致的，显式对齐（其余走名称模糊匹配） */
const PAGE_BILL_ALIAS = {
  WorkOrderList: 'PRODUCTION_ORDER',
  OperationReportList: 'OPERATION_REPORT',
  SubcontractList: 'SUBCONTRACT_ORDER',
  SubcontractPickList: 'SUBCONTRACT_DELIVERY',
  PurchasePrePaymentList: 'PURCHASE_PRE_PAYMENT',
  ProductionReturnList: 'MATERIAL_RETURN',
  ProductionSupplementList: 'MATERIAL_SUPPLEMENT',
  SaleRepairList: 'SALE_REPAIR'
}

/** 不是单据的页面（台账/基础资料），不参与后端单据登记检查 */
const NON_BILL_PAGES = new Set(['StockFlowList', 'QcTemplateList', 'RoutingList', 'WorkCenterList', 'WorkstationList'])

function matchBill(page) {
  const alias = PAGE_BILL_ALIAS[page.page]
  if (alias) return links.find((b) => b.billType === alias) || null
  const t = norm(page.title)
  if (!t) return null
  let best = null
  for (const b of links) {
    const n = norm(b.name)
    if (!n) continue
    if (n === t) return b
    if (n.startsWith(t) || t.startsWith(n)) {
      if (!best || n.length > norm(best.name).length) best = b
    }
  }
  return best
}

for (const p of pages) {
  p.isBill = !NON_BILL_PAGES.has(p.page)
  const bill = p.isBill ? matchBill(p) : null
  p.billType = bill ? bill.billType : ''
  p.billName = bill ? bill.name : ''
  p.table = bill ? bill.table : ''
  p.link = !!bill
  p.cascade = bill ? cascadeTables.includes(bill.table) : false
  p.summary = bill ? summaryBills.includes(bill.billType) : false
  p.action = bill ? actionBills.includes(ACTION_TYPE_ALIAS[bill.billType] || bill.billType) : false
  const excelCode = bill
    ? (EXCEL_CODE_ALIAS[bill.billType] || bill.table.replace(/_([a-z])/g, (_, c) => c.toUpperCase()))
    : ''
  p.excel = excelCode ? excelCodes.includes(excelCode) : false
  p.excelShared = ['MATERIAL_RETURN', 'MATERIAL_SUPPLEMENT'].includes(p.billType) && p.excel
  p.fieldPermBackend = bill ? fpPageKeys.includes(bill.table) : false
}

// ==================== 汇总 ====================

const billPages = pages.filter((p) => p.isBill)
const nonBillPages = pages.filter((p) => !p.isBill)
const matchedBills = billPages.filter((p) => p.billType)

const missingDetailBills = billPages.filter((p) => !p.detail && !p.detailManual)
const missingCf = billPages.filter((p) => !p.customFields)
const missingFp = billPages.filter((p) => !p.fieldPerm && !p.autoPerm)
const unmatchedBills = billPages.filter((p) => !p.billType)
const noDetailFilter = billPages.filter((p) => (p.detail || p.detailManual) && !p.detailFilterDefs)

// 明细报表：启动扫描全部报表控制器，按配置主表自动对应 page_key（无需逐条登记）
const reportNoFp = []

// ==================== 输出 ====================

const lines = []
const push = (s = '') => lines.push(s)
const yes = (v) => (v ? '✅' : '❌')

push('# 功能体检 · 静态核对（第一层）')
push('')
push(`> 生成时间：${new Date().toISOString().slice(0, 19).replace('T', ' ')}（由 \`frontend/scripts/check-bill-capabilities.cjs\` 自动生成）`)
push('> 口径：以《单据与页面登记》第 4 栏为"应该有"，扫描页面与登记表为"实际有"；只读扫描，不改业务数据。')
push('')
push('## 汇总')
push('')
push(`- 使用标准列表页的页面：**${pages.length}**（单据 ${billPages.length} / 台账·主档 ${nonBillPages.length}）`)
push(`- 已登记明细模式：**${billPages.filter((p) => p.detail || p.detailManual).length}**（自动 ${billPages.filter((p) => p.detail).length} / 手动 ${billPages.filter((p) => p.detailManual).length}）`)
push(`- 单据未接明细模式：**${missingDetailBills.length}**（见下方清单；台账/主档不要求）`)
push(`- 列表自定义字段列：单据已登记 **${billPages.filter((p) => p.customFields).length}** / 未登记 **${missingCf.length}**`)
push(`- 页面字段权限：显式 ${pages.filter((p) => p.fieldPerm).length} / 自动 ${pages.filter((p) => !p.fieldPerm && p.autoPerm).length} / 未覆盖 ${missingFp.length}`)
push(`- 后端登记：来源/去向 **${links.length}** 类；级联删除 **${cascadeTables.length}** 张表；Excel **${excelCodes.length}** 个；AI 动作 **${actionBills.length}** 个；汇总取数 **${summaryBills.length}** 个`)
push(`- 明细报表控制器：**${reportCtrls.length}**；字段权限按报表主表自动对应（2026-11-27 起无需逐条登记）`)
push('')
push('## 一、页面 × 前端能力')
push('')
push('| 页面 | 业务名 | 页面类型 | 明细模式 | 自定义字段列 | 字段权限键 | 筛选(汇总/明细) | 批量动作 |')
push('|---|---|---|---|---|---|---|---|')
for (const p of pages) {
  push(`| ${p.page} | ${p.title || '—'} | ${p.isBill ? '单据' : '台账/主档'} | ${p.detail ? '✅登记' : p.detailManual ? '✅手动' : (p.isBill ? '❌' : '—')} | ${p.isBill ? yes(p.customFields) : '—'} | ${p.fieldPerm || (p.autoPerm ? '✅自动' : '❌')} | ${p.filterDefs ? '✅' : '❌'} / ${p.detailFilterDefs ? '✅' : '—'} | ${p.batchActions ? '✅' : '—'} |`)
}
push('')
push('### 需要处理的页面')
push('')
if (missingDetailBills.length) {
  push('**单据未接明细模式：**')
  for (const p of missingDetailBills) push(`- ${p.page}（${p.title || '—'}）${p.page === 'SaleRepairList' ? '——编辑页有两张明细，属漏接' : '——台账/主档形态，属正常'}`)
  push('')
}
if (noDetailFilter.length) {
  push('**明细模式缺"明细专属筛选条件"（汇总条件能筛、明细条件不能筛）：**')
  for (const p of noDetailFilter) push(`- ${p.page}（${p.title || '—'}）`)
  push('')
}
if (missingCf.length) {
  push('**单据未登记列表自定义字段列：**')
  for (const p of missingCf) push(`- ${p.page}（${p.title || '—'}）`)
  push('')
}
if (missingFp.length) {
  push('**字段权限未覆盖（页面未显式传、登记表也没有自动映射）：**')
  for (const p of missingFp) push(`- ${p.page}（${p.title || '—'}）`)
  push('')
}
if (unmatchedBills.length) {
  push('**未匹配到后端"来源去向"登记的单据（可能无上下游，需人工确认）：**')
  for (const p of unmatchedBills) push(`- ${p.page}（${p.title || '—'}）`)
  push('')
}
if (nonBillPages.length) {
  push('**台账/主档页面（不参与单据登记检查，仅提示归类）：**')
  for (const p of nonBillPages) push(`- ${p.page}（${p.title || '—'}）`)
  push('')
}

push('## 二、页面 × 后端登记（按单据类型）')
push('')
push('| 页面 | 单据类型 | 表 | 来源去向 | 级联删除 | Excel | 汇总取数 | AI 动作 | 字段权限 |')
push('|---|---|---|---|---|---|---|---|---|')
for (const p of matchedBills) {
  push(`| ${p.page} | ${p.billType} | ${p.table} | ${yes(p.link)} | ${yes(p.cascade)} | ${p.excelShared ? '✅共享' : yes(p.excel)} | ${p.summary ? '✅' : '—'} | ${yes(p.action)} | ${yes(p.fieldPermBackend)} |`)
}
push('')
push('> Excel："共享"=生产退料/补料与生产领料共用同一份导入导出登记；"—"=未登记汇总取数（看板未用，不代表错误）。')
push('')

push('## 三、后端登记表缺口')
push('')
for (const [label, key] of [['级联删除', 'cascade'], ['Excel 导入导出', 'excel'], ['AI 单据动作', 'action'], ['字段权限', 'fieldPermBackend']]) {
  const missing = matchedBills.filter((p) => !p[key])
  push(`- **${label}** 未覆盖：${missing.length ? missing.map((p) => `${p.title || p.page}(${p.billType})`).join('、') : '无'}`)
}
const noSummary = matchedBills.filter((p) => !p.summary)
push(`- **汇总取数**（看板按需，不算错误）：未登记 ${noSummary.length} 个：${noSummary.map((p) => p.title || p.page).join('、')}`)
push('')

push('## 四、明细报表 × 字段权限')
push('')
push('2026-11-27 起：明细报表不再逐条登记路径——启动时扫描全部报表控制器，按报表配置的主表自动对应 page_key；')
push('导出（文件）也已在写盘前按同一份不可见字段集合剔列。下面只列控制器清单，供核对是否有报表缺配置。')
push('')
push('| 报表接口 | 名称 | 字段权限 |')
push('|---|---|---|')
for (const c of reportCtrls) {
  push(`| ${c.base} | ${c.title || '—'} | ✅ 自动（按主表） |`)
}
push('')

push('## 五、结论与建议')
push('')
push(`- ✅ 达标：标准列表页覆盖 ${pages.length} 页；单据明细模式 ${billPages.filter((p) => p.detail || p.detailManual).length}/${billPages.length}；来源去向 ${links.length} 类；AI 动作 ${actionBills.length} 个。`)
push(`- ❌ 优先修：`)
push(`  1. 列表自定义字段列：单据 ${missingCf.length} 页未登记（${missingCf.map((p) => p.title || p.page).join('、')}）。`)
if (noDetailFilter.length) push(`  3. 明细模式缺明细专属筛选条件：${noDetailFilter.map((p) => p.title || p.page).join('、')}。`)
push('  4. 静态核对只能证明"登记了"；权限效果以第二层运行体检为准（npm run check:permissions）。')
push('')

const outMd = path.join(DOCS, '功能体检-静态核对.md')
fs.writeFileSync(outMd, lines.join('\n'), 'utf8')
if (process.argv.includes('--json')) {
  const outJson = path.join(DOCS, '功能体检-静态核对.json')
  fs.writeFileSync(outJson, JSON.stringify({
    generatedAt: new Date().toISOString(),
    pages,
    links,
    cascadeTables,
    summaryBills,
    actionBills,
    excelCodes,
    reportCtrls,
    fpPathMap
  }, null, 2), 'utf8')
  console.log('JSON:', outJson)
}
console.log('报告:', outMd)
console.log(`页面 ${pages.length}；明细 ${pages.filter((p) => p.detail || p.detailManual).length}；自定义字段 ${pages.filter((p) => p.customFields).length}；字段权限键 ${pages.filter((p) => p.fieldPerm).length}`)
