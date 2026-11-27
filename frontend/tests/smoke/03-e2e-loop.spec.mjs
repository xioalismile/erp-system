/**
 * 03 端到端闭环（★本套测试的核心，对应验收 A4，抓 D1–D7 / D9–D16）
 *
 * 每张单据固定走这条链路，全程真实点击、不调接口写数据：
 *   L1 新增保存   → 保存按钮可点、点击有反馈、保存后 URL 回写 id、刷新仍是同一单
 *   L2 列表回读   → 新单出现在列表第 1 行（抓 PG NULLS FIRST 排序坑）、状态非空非 '-'、无占位符
 *   L3 详情回显   → ★主表字段与填写值逐字一致 + 明细行数 == 填写行数 + 明细单元格值逐字一致
 *                    + [app.errorHandler] 报错为 0（抓"明细不持久化"与"composable 漏 import 整页空"）
 *   L4 改明细再存 → ★改数量后重开：新值生效、明细行数未减少、第 2 行原值未丢（抓"更新先删后插"）
 *   L5 状态检查   → 保存后应为草稿；不点"提交"推入审批（L6 里点一次但一律取消）
 *   L6 提交可达性 → ★点一次「提交」必须有可观察反馈（确认框/校验提示/跳转）+ 控制台 0 异常，
 *                   弹出确认框一律「取消」（抓"提交按钮点了没反应"：onSubmit 引用未定义变量等）
 *
 * 只开列表页、只看接口 200 的方式永远发现不了 L3/L4 这几类缺陷，这正是本用例存在的理由。
 */
import { test, expect } from '@playwright/test'
import fs from 'node:fs'
import path from 'node:path'
import { cfg, readJson, writeJson, OUT_DIR } from './smoke-env.mjs'
import { startMonitors, loginByUi, waitAppReady, readToasts, waitForObservableFeedback, findPlaceholders } from './smoke-browser.mjs'
import { BILLS, PENDING_BILLS } from './smoke-write-config.mjs'
import { BILL_API } from './bill-api-paths.mjs'

const accountsFile = readJson('accounts.json')
const enabled = accountsFile && Array.isArray(accountsFile.accounts) && accountsFile.accounts.length > 0
const accByModule = new Map((accountsFile?.accounts || []).map(a => [a.moduleKey, a]))

/**
 * 结果写入策略：**按单据分文件**。
 * 为什么不能用一个累积数组：Playwright 在每个测试前会重建模块状态，
 * 累积数组到写文件时往往只剩当前这一条（实测 6 张单据互相覆盖，只留下最后一张）。
 * 分文件写 + 汇总时扫目录，才能拿到全量结果。
 */
const SUMMARY_FILE = 'e2e-loop-summary.json'
function isLoopResultFile(f) {
  return /^e2e-loop-.*\.json$/.test(f) && f !== SUMMARY_FILE && f !== 'e2e-loop-result.json'
}
function writeLoopResult(rec) {
  writeJson(`e2e-loop-${rec.bill}.json`, {
    generatedAt: new Date().toISOString(),
    baseUrl: cfg.baseUrl,
    ...rec
  })
}
function writeLoopSummary() {
  let files = []
  try { files = fs.readdirSync(OUT_DIR).filter(isLoopResultFile) } catch (_) { files = [] }
  const results = []
  for (const f of files) {
    try { results.push(JSON.parse(fs.readFileSync(path.join(OUT_DIR, f), 'utf8'))) } catch (_) { /* 跳过坏文件 */ }
  }
  results.sort((a, b) => String(a.bill).localeCompare(String(b.bill)))
  const summary = {
    generatedAt: new Date().toISOString(),
    baseUrl: cfg.baseUrl,
    registered: BILLS.map(b => b.name),
    pending: PENDING_BILLS.map(b => b.name),
    total: results.length,
    passed: results.filter(r => r.ok).length,
    failed: results.filter(r => !r.ok).length,
    results: results.map(r => ({
      bill: r.bill,
      name: r.name,
      ok: r.ok,
      docCode: r.steps?.docCode,
      // 供 ⑤e 清理测试单使用（2026-11-11）：单据所在页面地址 + 接口基路径 + 主键
      detailUrl: r.steps?.detailUrl,
      apiBase: r.steps?.apiBase,
      detailId: r.steps?.detailId,
      effective: r.steps?.effective,
      effectiveStatus: r.steps?.effectiveStatus,
      effectiveNote: r.steps?.effectiveNote,
      problemCount: (r.problems || []).length,
      problems: r.problems || []
    }))
  }
  writeJson(SUMMARY_FILE, summary)
  return summary
}

const clean = (s) => String(s || '').trim().replace(/\s+/g, ' ')

/**
 * 断言比对用的"空白归一化"：去掉**所有**空白字符。
 * 同一物料在不同位置展示不同：下拉是「MAT000001E2E-成品A」，列表/详情单元格是「MAT000001 E2E-成品A」，
 * 直接用 includes 比对会因这一个空格误报"明细不持久化"（2026-09-20 实测）。
 */
const normText = (s) => String(s || '').replace(/\s+/g, '')

/** 按 el-form-item 的 label 精确定位该表单项 */
function formItem(page, label) {
  return page.locator('.el-form-item').filter({
    has: page.locator('.el-form-item__label', { hasText: new RegExp(`^\\s*\\*?\\s*${label}\\s*$`) })
  }).first()
}

/** 在表头下拉里选第一个可选项（兼容"点击后异步/远程加载选项"的下拉） */
async function pickFirstOption(page, label) {
  const item = formItem(page, label)
  const sel = item.locator('.el-select').first()
  if ((await sel.count()) === 0) return { ok: false, reason: `表单项「${label}」下找不到下拉控件` }
  // 2026-11-11 修复：设置中心关闭「启用外币业务」时，币种被锁定为本位币（下拉禁用且已预填）。
  // 这种情况不是缺陷，直接按“已锁定默认值”通过，不再误报“下拉没有可选项”。
  const cls = (await sel.getAttribute('class')) || ''
  const inputDisabled = await sel.locator('input').first().isDisabled().catch(() => false)
  if (cls.includes('is-disabled') || inputDisabled) {
    const cur = clean(await sel.innerText().catch(() => ''))
    if (cur && !/^选择/.test(cur)) return { ok: true, value: cur, locked: true }
    return { ok: false, reason: `表单项「${label}」被锁定但没有默认值（请检查设置中心开关）` }
  }
  await sel.click()
  await page.waitForTimeout(900)
  let opt = page.locator('.el-select-dropdown__item').filter({ visible: true }).first()
  if ((await opt.count()) === 0) {
    // 选项可能是 remote/过滤后才加载：在输入框里敲一下再删掉，触发一次加载
    const input = sel.locator('input').first()
    if ((await input.count()) > 0) {
      await input.type('a', { delay: 60 }).catch(() => {})
      await input.press('Backspace').catch(() => {})
      await page.waitForTimeout(1600)
      opt = page.locator('.el-select-dropdown__item').filter({ visible: true }).first()
    }
  }
  try {
    await opt.waitFor({ state: 'visible', timeout: 8000 })
  } catch (_) {
    return { ok: false, reason: `表单项「${label}」下拉没有可选项（基础数据为空或该账号无可见数据）` }
  }
  const text = clean(await opt.innerText())
  await opt.click()
  await page.waitForTimeout(250)
  return { ok: true, value: text }
}

/** 读表头字段当前值（用于 L3 逐字比对） */
async function readFormValue(page, label) {
  const item = formItem(page, label)
  if ((await item.count()) === 0) return null
  const sel = item.locator('.el-select').first()
  if ((await sel.count()) > 0) {
    // Element Plus 2.7：选中值渲染在 .el-select__selected-item 里（是文本，不是 input.value）。
    // 无选中值时才会渲染 .el-select__placeholder，且文案形如「选择客户」。
    // 故：读取整个 .el-select 的 innerText，命中占位文案或为空即视为未填。
    const t = clean(await sel.innerText().catch(() => ''))
    if (!t || /^选择/.test(t) || t === '—') return ''
    return t
  }
  const input = item.locator('input').first()
  if ((await input.count()) === 0) return null
  return clean(await input.inputValue().catch(() => ''))
}

/** 轮询等待列表里出现指定单号（列表"先渲染表头、后到达数据"，固定 sleep 会把慢加载误判成没落库） */
async function waitForListContains(page, code, timeout = 20000) {
  const t0 = Date.now()
  while (Date.now() - t0 < timeout) {
    const txt = await page.locator('.el-table').first().innerText().catch(() => '')
    if (txt && txt.includes(code)) return { ok: true, waitedMs: Date.now() - t0 }
    await page.waitForTimeout(500)
  }
  return { ok: false, waitedMs: Date.now() - t0 }
}

/** 读单据编号（优先 .order-no，其次按编号 label 取输入框） */
async function readDocCode(page, bill) {
  const noEl = page.locator('.order-no, .bill-no, .doc-no').first()
  if ((await noEl.count()) > 0) {
    const t = clean(await noEl.innerText())
    const m = t.match(/[A-Za-z]{1,6}[-_]?\d[\w-]*/)
    if (m) return m[0]
  }
  if (bill.codeLabel) {
    const v = await readFormValue(page, bill.codeLabel)
    if (v) return v
  }
  // 部分单据保存成功后跳的是【列表页】而非详情页（实测：销售报价单 → /sale/quotes）。
  // 此时单号只存在于列表首行，必须等表格渲染完再从行文本提取，
  // 否则读空并误报"L1 保存后读不到单据编号"（2026-09-20 实测 6 张单据同因）。
  // ★ 必须严格判定"当前就在列表页"：编辑页明细表同样是 .el-table__row，
  //   而详情页 URL（/sale/contracts/123）**也包含** listPath（/sale/contracts），
  //   用 includes 判断会误判成列表页、取到明细首行的物料编码（实测误读成 MAT000001）。
  //   因此按 pathname 精确相等判断。
  const pathname = (() => { try { return new URL(page.url()).pathname.replace(/\/+$/, '') } catch (_) { return '' } })()
  const listPathname = String(bill.listPath || '').replace(/\/+$/, '')
  const onListPage = !!listPathname && pathname === listPathname
  if (!onListPage) return ''
  try {
    await page.waitForSelector('.el-table__body-wrapper .el-table__row', { timeout: 8000 })
  } catch (_) { /* 表格未出现则按读不到处理 */ }
  const firstRow = page.locator('.el-table__body-wrapper .el-table__row').first()
  if ((await firstRow.count()) > 0) {
    const rowText = clean(await firstRow.innerText().catch(() => ''))
    // 单号形如 QUT-20260920-0003 / SO-20260920-0001
    const m = rowText.match(/\b[A-Za-z]{2,8}-\d{6,8}-\d{1,6}\b/) || rowText.match(/\b[A-Za-z]{1,8}[-_]?\d{3,}[\w-]*\b/)
    if (m) return m[0]
  }
  return ''
}

/**
 * 明细表定位锚点（**必须是单一选择器，不能出现逗号**）。
 *
 * 演进史（两代坑，都记下来防止回退）：
 *   [第一代 2026-09-13] 写成 `.table-wrap:has(.detail-subtable-header) .el-table, [data-onboarding="detail-table"]`。
 *     后续定位都是 `${DETAIL_TABLE} thead th` 这种"拼接后代选择器"，
 *     而 CSS 里逗号后面的后代选择器只作用于最后一个分支 → headerIndex 双双返回 0，连锁误报。
 *   [第二代 2026-09-20] 改为单一选择器 `.table-wrap:has(.detail-subtable-header) .el-table`，
 *     但把口径扩到 35 张单据时实测各页 DOM 并不统一，单一 CSS 选择器无法覆盖三种形态：
 *       · BillItemsTable 渲染 → .table-wrap 内含 .detail-subtable-header（19 页，标准形态）
 *       · 手写明细          → 只有 .table-wrap，没有 .detail-subtable-header
 *                             （生产工单 / 组装单 / 拆卸单 / 委外加工单 / 打样单）
 *       · 只有子表头       → 有 .detail-subtable-header 却没有 .table-wrap（免费样品单 / BOM）
 *     → 直接登记会 100% 在 L1（行数 0）与 L3（找不到明细表格）失败。
 *
 * 现在的做法：**先归一化、再定位** —— 进页面后调用 normalizeDetailAnchor()，
 * 由它按优先级找出真正的明细容器并打上 `data-e2e-detail="1"`，
 * 之后所有定位统一用 `[data-e2e-detail] .el-table`（单一选择器，无分支歧义）。
 */
const DETAIL_TABLE = '[data-e2e-detail] .el-table'

/**
 * 归一化明细表锚点：给"真正的明细容器"打 data-e2e-detail 标记。
 *
 * 选择优先级（与上面三种 DOM 形态一一对应）：
 *   ① .table-wrap 内同时含 .detail-subtable-header → 标准 BillItemsTable 页
 *   ② .detail-subtable-header 的父容器             → 只有子表头、没有 table-wrap 的页
 *   ③ 含 el-table 的 .table-wrap（可用 bill.detail.tableHint 按表内文案筛选）
 *                                                  → 手写明细页 / 多张明细表的页
 * 每次先清除旧标记，避免"双明细表"页面残留上一次的标记。
 *
 * @returns {Promise<boolean>} 是否成功标记（false = 该页确实找不到明细容器，由调用方报错）
 */
async function normalizeDetailAnchor(page, bill = null) {
  const hint = (bill && bill.detail && bill.detail.tableHint) || ''
  return await page.evaluate((hintText) => {
    document.querySelectorAll('[data-e2e-detail]').forEach(el => el.removeAttribute('data-e2e-detail'))
    let root = null
    // ① 标准形态：.table-wrap 内含 .detail-subtable-header（:has() 需较新内核，失败则降级到 ②）
    try {
      root = document.querySelector('.table-wrap:has(.detail-subtable-header)')
    } catch (_) { root = null }
    // ② 只有子表头：用它自己的父容器当明细容器
    if (!root) {
      const h = document.querySelector('.detail-subtable-header')
      if (h && h.parentElement) root = h.parentElement
    }
    // ③ 手写明细：取含 el-table 的 .table-wrap
    if (!root) {
      const wraps = Array.from(document.querySelectorAll('.table-wrap')).filter(w => w.querySelector('.el-table'))
      if (wraps.length) {
        // 双明细表页面（生产工单=成品+原料、组装单/拆卸单=成品+原料）必须只圈住一张表，
        // 否则 DT_ROWS 会把两张表的行数加在一起 → 必然报"行数不符"。
        // 启发式：优先"只含一张 el-table"的 wrap；再用 tableHint 按表内文案二次筛选。
        let pool = wraps.filter(w => w.querySelectorAll('.el-table').length === 1)
        if (!pool.length) pool = wraps
        if (hintText) {
          const hit = pool.filter(w => (w.innerText || '').includes(hintText))
          if (hit.length) pool = hit
        }
        root = pool[0]
        // 一屏多张明细表（生产工单=成品+原料；组装单/拆卸单=成品+原料）：
        // 若选中的容器里含不止一张表，收敛到**第一张表**的父容器，
        // 否则表头数(24)与单元格数(12)对不上 → 列定位全错（2026-09-20 实测生产工单）。
        if (root.querySelectorAll('.el-table').length > 1) {
          const tables = Array.from(root.querySelectorAll('.el-table'))
          // 一屏两张明细表时优先按表格内文案命中（如生产工单的「成品编码」），
          // 命中不到再退「第一张表」，绝不能把两张表的行/列并在一起（2026-09-20 实测 24 列 vs 12 单元格）
          let pick = null
          if (hintText) pick = tables.find(tb => (tb.innerText || '').includes(hintText)) || null
          if (!pick) pick = tables[0]
          if (pick && pick.parentElement) root = pick.parentElement
        }
      }
    }
    if (!root) return false
    root.setAttribute('data-e2e-detail', '1')
    return true
  }, hint).catch(() => false)
}

/**
 * 行/表头定位必须**限定在原生 thead/tbody 内**。
 *
 * 实测证据（2026-09-13 销售报价单 /new 页面）：
 *   用 `${DETAIL_TABLE} .el-table__body-wrapper .el-table__row` 数行时只匹配到 1 个元素，
 *   而该元素的 `td` 有 51 个（= 17 列 × 3 行）→ 三行被当成一行；
 *   同时 `${DETAIL_TABLE} .el-table__header-wrapper th` 的首个元素文本里同时含"物料"和"数量"，
 *   导致 headerIndex 对物料/数量双双返回 0 → 后续取单元格必然错位。
 * 结论：不能依赖 .el-table__head/body-wrapper 这类包装类名，改用 table 原生结构限定。
 */
const DT_ROWS = `${DETAIL_TABLE} tbody tr.el-table__row`
const DT_HEADS = `${DETAIL_TABLE} thead th`

function rowLoc(page, idx) {
  return page.locator(DT_ROWS).nth(idx)
}

async function detailRowCount(page) {
  return await page.locator(DT_ROWS).count()
}

/**
 * 结构一致性自检：表头列数必须等于每行单元格数。
 * 不相等说明定位错位，必须立即判失败并说明 —— 否则会继续产出"物料单元格没有下拉控件"这类连带误报。
 */
async function detailStructure(page) {
  const thCount = await page.locator(DT_HEADS).count()
  const rowCount = await page.locator(DT_ROWS).count()
  const tdCount = rowCount > 0 ? await page.locator(DT_ROWS).first().locator('td').count() : 0
  return { thCount, rowCount, tdCount, consistent: thCount > 0 && rowCount > 0 && thCount === tdCount }
}

/**
 * 轮询等待明细行数达到期望值。
 * 为什么必须轮询而不是固定 sleep：详情页是"表格先渲染、数据后到达"，
 * 固定等待会把"还没加载完"误判成"明细被清空"（本项目真实踩过同类假失败）。
 * 只有超时仍未到齐，才判定为真的没数据。
 */
async function waitForDetailRows(page, expected, timeout = 20000) {
  const t0 = Date.now()
  let last = 0
  while (Date.now() - t0 < timeout) {
    last = await detailRowCount(page)
    if (last >= expected) return { ok: true, rows: last, waitedMs: Date.now() - t0 }
    await page.waitForTimeout(500)
  }
  return { ok: false, rows: last, waitedMs: Date.now() - t0 }
}

/** 读"数量"列的表头索引（列顺序由列设置驱动，不能写死列号） */
async function headerIndex(page, headerText) {
  const ths = page.locator(DT_HEADS)
  const n = await ths.count()
  // 2026-11-11 修复：多单位改造后明细自动注入「副单位数量 / 辅助数量」等列，
  // 用“包含”匹配会先命中这些只读列（单元格里没有输入框），导致误报「数量单元格没有输入框」。
  // 口径：先按“完全等于列名”找，找不到再退回“包含”。
  let fuzzy = -1
  for (let i = 0; i < n; i++) {
    const t = clean(await ths.nth(i).innerText())
    if (t === headerText) return i
    if (fuzzy < 0 && t.includes(headerText)) fuzzy = i
  }
  return fuzzy
}

/**
 * 给明细某行选物料。
 * 定位方式必须按列头 → 单元格 → .el-select：
 *   Element Plus 2.7 的 select 把占位/选中文字渲染在 .el-select__placeholder 里，
 *   `input[placeholder="选择物料"]` 这种写法命中不到（项目已踩过同类坑）。
 */
async function fillRowMaterial(page, rowIdx, colIdx) {
  const row = rowLoc(page, rowIdx)
  if (colIdx < 0) return { ok: false, reason: '明细表找不到物料列' }
  const cell = row.locator('td').nth(colIdx)
  const sel = cell.locator('.el-select').first()
  if ((await sel.count()) === 0) {
    return { ok: false, reason: `第${rowIdx + 1}行物料单元格里没有下拉控件` }
  }
  await sel.click()
  const opt = page.locator('.el-select-dropdown__item').filter({ visible: true }).first()
  try {
    await opt.waitFor({ state: 'visible', timeout: 8000 })
  } catch (_) {
    return { ok: false, reason: `第${rowIdx + 1}行物料下拉无选项（物料档案为空？）` }
  }
  const text = clean(await opt.innerText())
  await opt.click()
  await page.waitForTimeout(500)
  await page.keyboard.press('Escape')
  // 提取"物料编码"用于回读断言。
  // 注意：下拉 innerText 有「编码 名称」(MAT000001 E2E-成品A) 与「编码名称」(MAT000001E2E-成品A) 两种拼接。
  // 旧实现 /^[A-Za-z0-9][A-Za-z0-9\-_.]*/ 会一路吃到中文，把名称前缀也吞成编码（得到 MAT000001E2E-），
  // 于是与页面上「MAT000001 E2E-成品A」比对必然失败 → 误报"明细不持久化"（2026-09-20 实测）。
  // 改为优先按「字母+数字」取真正的编码（MAT000001 / CUS000001 / CUR001），取不到再回退原逻辑。
  const codeOnly = (text.match(/^[A-Za-z]+\d+/) || text.match(/^[A-Za-z0-9][A-Za-z0-9\-_.]*/) || [text])[0]
  return { ok: true, code: codeOnly, label: text }
}

/** 表头“弹窗选择器”字段（客户/供应商这类只读框+放大镜）：点按钮 → 弹窗里双击第一行 */
async function pickHeaderDialogRow(page, label) {
  const item = formItem(page, label)
  if (!(await item.count())) return { ok: false, reason: `找不到表单项「${label}」` }
  const btn = item.locator('.el-input-group__append button, .el-input__append button').first()
  if (!(await btn.count())) return { ok: false, reason: `表单项「${label}」没有选择按钮` }
  await btn.click()
  await page.waitForTimeout(1500)
  const row = page.locator('.el-dialog:visible .el-table__body-wrapper tbody tr').first()
  if (!(await row.count())) return { ok: false, reason: `「${label}」弹窗里没有可选数据` }
  const text = clean(await row.innerText())
  await row.dblclick()
  await page.waitForTimeout(900)
  return { ok: true, value: text }
}

/** 给明细某行填数量 */
async function fillRowQty(page, rowIdx, colIdx, value) {
  const row = rowLoc(page, rowIdx)
  const cell = row.locator('td').nth(colIdx)
  const input = cell.locator('input').first()
  if ((await input.count()) === 0) return { ok: false, reason: `第${rowIdx + 1}行「数量」单元格没有输入框` }
  // 2026-09-20：输入框被禁用/只读时直接给出原因。
  // 原先直接 fill()，遇到禁用框要等 20 秒超时才抛错，而超时异常会**中断整条用例**，
  // 真实原因（单据不是草稿态 / 行未选物料）被"定位超时"的假象盖住（生产入库单实测）。
  if (await input.isDisabled().catch(() => false)) {
    return { ok: false, reason: `第${rowIdx + 1}行「数量」输入框为禁用状态（单据可能不是草稿态）` }
  }
  if ((await input.getAttribute('readonly').catch(() => null)) !== null) {
    return { ok: false, reason: `第${rowIdx + 1}行「数量」输入框为只读` }
  }
  try {
    await input.fill(String(value), { timeout: 8000 })
    await input.press('Tab')
  } catch (e) {
    return { ok: false, reason: `第${rowIdx + 1}行「数量」填充失败: ${String(e.message || e).split('\n')[0]}` }
  }
  await page.waitForTimeout(350)
  const actual = clean(await input.inputValue())
  return { ok: true, actual }
}

/** 给明细某行的下拉列选第一个可选项（用于"仓库"这类放在明细列、但整单共用的字段） */
async function pickRowFirstOption(page, rowIdx, colIdx) {
  const cell = rowLoc(page, rowIdx).locator('td').nth(colIdx)
  const sel = cell.locator('.el-select').first()
  if ((await sel.count()) === 0) return { ok: false, reason: `第${rowIdx + 1}行该列里没有下拉控件` }
  const cls = (await sel.getAttribute('class')) || ''
  if (cls.includes('is-disabled')) {
    const cur = clean(await sel.innerText().catch(() => ''))
    if (cur && !/^选择/.test(cur)) return { ok: true, value: cur, locked: true }
    return { ok: false, reason: `第${rowIdx + 1}行该列下拉被禁用且无默认值` }
  }
  await sel.click()
  await page.waitForTimeout(600)
  const opt = page.locator('.el-select-dropdown__item').filter({ visible: true }).first()
  try {
    await opt.waitFor({ state: 'visible', timeout: 8000 })
  } catch (_) {
    await page.keyboard.press('Escape').catch(() => {})
    return { ok: false, reason: `第${rowIdx + 1}行该列下拉没有可选项` }
  }
  const text = clean(await opt.innerText())
  await opt.click()
  await page.waitForTimeout(300)
  await page.keyboard.press('Escape').catch(() => {})
  return { ok: true, value: text }
}

/** 读明细全部行的文本快照（用于 L3/L4 比对） */
/** 按列头填文本单元格：只用于既没有下拉、也没有数字控件的文本列（如工序转移单的"转出/转入工序号"） */
async function fillRowText(page, rowIdx, colIdx, value) {
  if (colIdx < 0) return { ok: false, reason: '找不到该列' }
  const cell = rowLoc(page, rowIdx).locator('td').nth(colIdx)
  const input = cell.locator('input').first()
  if ((await input.count()) === 0) return { ok: false, reason: `第${rowIdx + 1}行第${colIdx + 1}列没有输入框` }
  if (await input.isDisabled().catch(() => false)) {
    return { ok: false, reason: `第${rowIdx + 1}行第${colIdx + 1}列输入框为禁用状态` }
  }
  try {
    await input.fill(String(value), { timeout: 8000 })
  } catch (e) {
    return { ok: false, reason: `第${rowIdx + 1}行第${colIdx + 1}列填充失败: ${String(e.message || e).split('\n')[0]}` }
  }
  await page.waitForTimeout(150)
  return { ok: true, actual: await input.inputValue().catch(() => '') }
}

async function readDetailRows(page) {
  const rows = page.locator(DT_ROWS)
  const n = await rows.count()
  const out = []
  for (let i = 0; i < n; i++) out.push(clean(await rows.nth(i).innerText()))
  return out
}

/**
 * 精确读某行某列单元格的文本。
 * 不能用"整行文本 includes"来判断字段值 —— 单元格之间的拼接会带来误判
 * （实测：物料下拉取到的是 `编码名称` 拼接串，而单元格里是 `编码 名称` 带空格，
 *   用整行 includes 会误报"明细丢失"）。
 */
async function readCellText(page, rowIdx, colIdx) {
  const row = rowLoc(page, rowIdx)
  if ((await row.count()) === 0) return ''
  const cell = row.locator('td').nth(colIdx)
  if ((await cell.count()) === 0) return ''
  const input = cell.locator('input').first()
  if ((await input.count()) > 0) {
    const v = await input.inputValue().catch(() => '')
    if (v !== '') return clean(v)
  }
  return clean(await cell.innerText().catch(() => ''))
}

function describeProblems(list) {
  return list.length ? `\n  - ${list.join('\n  - ')}` : ''
}

test.describe('03 端到端闭环', () => {
  test.skip(!enabled, '未找到 accounts.json，请先执行 npm run test:smoke:prepare')
  test.skip(!cfg.writeLoop, 'E2E_WRITE_LOOP=false，跳过写入闭环')

  // 把"未覆盖"的单据显式登记进报告，避免"看起来全覆盖"
  test('@loop 覆盖度报告（已登记 / 未覆盖 单据清单）', async () => {
    // 先清掉上一轮遗留的分单据结果文件：汇总逻辑是"扫目录"，不清会把历史失败单（如已下线单据）
    // 当成本轮结果带进汇总，出现"本轮没跑却有失败"的假红（2026-11-11 实锤）。
    const keep = new Set(BILLS.map(b => `e2e-loop-${b.key}.json`))
    try {
      for (const f of fs.readdirSync(OUT_DIR)) {
        if (isLoopResultFile(f) && !keep.has(f)) fs.unlinkSync(path.join(OUT_DIR, f))
      }
    } catch (_) { /* 目录不存在等忽略 */ }
    const s = writeLoopSummary()
    console.log(`\n已登记闭环的单据: ${BILLS.length} 张 → ${BILLS.map(b => b.name).join('、')}`)
    console.log(`未覆盖（形态不匹配）: ${PENDING_BILLS.length} 张 → ${PENDING_BILLS.map(b => b.name).join('、')}`)
    for (const p of PENDING_BILLS) {
      if (p.reason) console.log(`  · 「${p.name}」未覆盖原因：${p.reason}`)
    }
    console.log(`本轮已有结果的文件  : ${s.total} 个（通过 ${s.passed} / 失败 ${s.failed}）`)
    // 口径总数必须恒为 35（17 首批 + 17 第二批 + 1 未覆盖）；不等即说明清单被改漏/改重
    expect(BILLS.length + PENDING_BILLS.length).toBe(35)
  })

  for (const bill of BILLS) {
    test(`@loop @${bill.module} ${bill.name} 新增→回读→重开→改明细→再回读`, async ({ page }) => {
      const acc = accByModule.get(bill.module)
      expect(acc, `账号清单里没有模块 ${bill.module} 的测试账号`).toBeTruthy()

      const mon = startMonitors(page)
      const problems = []
      const rec = { bill: bill.key, name: bill.name, module: bill.module, steps: {} }

      await loginByUi(page, acc.username, accountsFile.testUserPassword)
      await waitAppReady(page)

      // ================= L1 新增保存 =================
      await page.goto(bill.newPath, { waitUntil: 'domcontentloaded' })
      await page.waitForTimeout(1200)
      // 归一化明细表锚点（各页 DOM 形态不同，见 normalizeDetailAnchor 注释）。
      // 必须在"点添加明细按钮"之前完成，否则后续 DT_ROWS/DT_HEADS 全部定位不到。
      const anchorOk = await normalizeDetailAnchor(page, bill)
      rec.steps.detailAnchorResolved = anchorOk
      if (bill.detail && !anchorOk) {
        problems.push('L1 新增页未能标记明细表容器（页面无 .table-wrap/.detail-subtable-header，normalizeDetailAnchor 返回 false）')
      }

      const saveBtn = page.locator('button:has-text("保存")').first()
      if ((await saveBtn.count()) === 0) {
        problems.push('L1 新增页找不到「保存」按钮（可能被 v-permission 从 DOM 移除，或页面未渲染）')
      } else {
        const disabled = await saveBtn.isDisabled().catch(() => true)
        if (disabled) problems.push('L1「保存」按钮处于禁用状态')
      }

      // 表头必填
      for (const label of (bill.headerSelects || [])) {
        const r = await pickFirstOption(page, label)
        if (!r.ok) problems.push(`L1 表头「${label}」填充失败: ${r.reason}`)
        else rec.steps[`header_${label}`] = r.value
      }

      // 表头“弹窗选择器”字段（客户/供应商）：2026-11-11 新增。
      // 销售预收款单/采购预付款单的客户/供应商是“只读输入框 + 放大镜弹窗”，不是 el-select，
      // 原先无法自动填 → 提交被必填校验拦住 → 被误判成“提交按钮点了没反应”。
      for (const label of (bill.headerPickers || [])) {
        const r = await pickHeaderDialogRow(page, label)
        if (!r.ok) problems.push(`L1 表头「${label}」弹窗选择失败: ${r.reason}`)
        else rec.steps[`header_${label}`] = r.value
      }

      // 部分表头下拉必须取"非第一项"，用于满足跨字段校验。
      // 实测场景：调拨单 toStockId 的 validator 要求 调入仓库 ≠ 调出仓库，
      // 若两个都取第一项会直接触发校验失败（导致保存被拦、后面全是连带误报）。
      // 实现方式与 fillRowMaterial 选物料一致（点开 select → 选可见项），已验证可用。
      for (const label of (bill.pickSecondOptions || [])) {
        const item = formItem(page, label)
        const sel = item.locator('.el-select').first()
        if ((await sel.count()) === 0) {
          problems.push(`L1 表头「${label}」找不到下拉控件（需取非第一项）`)
          continue
        }
        await sel.click()
        await page.waitForTimeout(400)
        const opts = page.locator('.el-select-dropdown__item').filter({ visible: true })
        const n = await opts.count()
        if (n < 2) {
          problems.push(`L1 表头「${label}」可见选项仅 ${n} 个，无法取到与前一字段不同的值`)
          await page.keyboard.press('Escape')
          continue
        }
        await opts.nth(1).click()
        await page.waitForTimeout(400)
        rec.steps[`header_${label}`] = clean(await item.innerText().catch(() => ''))
        await page.keyboard.press('Escape')
      }
      for (const label of (bill.headerDates || [])) {
        const item = formItem(page, label)
        if ((await item.count()) > 0) {
          const input = item.locator('input').first()
          const cur = clean(await input.inputValue().catch(() => ''))
          if (!cur) {
            const today = new Date().toISOString().slice(0, 10)
            await input.fill(today)
            await input.press('Enter')
            await page.waitForTimeout(300)
            rec.steps[`header_${label}`] = today
            await page.keyboard.press('Escape')
          } else {
            rec.steps[`header_${label}`] = cur
          }
        }
      }

      // 必填"文本框"表头（如采购退货单的「退货原因」returnReason）：
      // 这类字段不是下拉也不是日期，只填 select/date 会被必填校验拦住保存。
      for (const ht of (bill.headerTexts || [])) {
        const item = formItem(page, ht.label)
        if ((await item.count()) === 0) {
          problems.push(`L1 找不到表单项「${ht.label}」（需填 ${ht.value}）`)
          continue
        }
        const input = item.locator('textarea, input').first()
        const cur = clean(await input.inputValue().catch(() => ''))
        if (!cur) {
          await input.fill(ht.value)
          await page.waitForTimeout(200)
          rec.steps[`headerText_${ht.label}`] = ht.value
        } else {
          rec.steps[`headerText_${ht.label}`] = cur
        }
      }

      // 明细：加 2 行
      const d = bill.detail
      const filledRows = []
      const numbersFilled = []
      // 页面自带空行数（盘点单/委外加工单等）；供 L3/L4 的行数断言复用（2026-11-11）
      let preRows = 0
      const textsFilled = []
      if (d) {
        // ---- 诊断（先取证再改代码，禁止凭猜）----
        // 1) 进页面时是否已自带行（区分"我点出来的"与"页面自动注入的"）
        rec.steps.diagRowsOnEnter = await detailRowCount(page)
        // 2) 页面上共有几张表 / 明细锚点解析到几张
        rec.steps.diagElTableCount = await page.locator('.el-table').count()
        rec.steps.diagAnchorCount = await page.locator(DETAIL_TABLE).count()
        rec.steps.diagAnchorCountFirst = await page.locator(DETAIL_TABLE).first().count()

        const addBtn = page.locator(`button:has-text("${d.addButtonText}")`).first()
        rec.steps.diagAddBtnCount = await addBtn.count()
        if ((await addBtn.count()) === 0) {
          problems.push(`L1 找不到「${d.addButtonText}」按钮`)
        }
        rec.steps.diagAddBtnDisabled = (await addBtn.count()) > 0 ? await addBtn.isDisabled().catch(() => null) : null

        const perClick = []
        for (let i = 0; i < d.qtyValues.length; i++) {
          try {
            await addBtn.click({ timeout: 8000 })
          } catch (e) {
            perClick.push({ click: i + 1, error: e.message })
            break
          }
          await page.waitForTimeout(700)
          perClick.push({ click: i + 1, rows: await detailRowCount(page) })
        }
        rec.steps.diagRowsPerClick = perClick

        const rows = await detailRowCount(page)
        rec.steps.detailRowsAfterAdd = rows
        // 2026-11-11：盘点单 / 委外加工单 等页面**自带若干空行**，再点「添加明细」会多出行数。
        // 配置 allowPreRows=true 时，只填「本次新增的那几行」（行尾），不再按总行数判失败。
        preRows = d.allowPreRows ? Math.max(0, rows - d.qtyValues.length) : 0
        rec.steps.detailPreRows = preRows
        if (rows !== d.qtyValues.length + preRows) {
          problems.push(`L1 加明细后行数 ${rows} ≠ 期望 ${d.qtyValues.length + preRows}` + (preRows ? `（页面自带 ${preRows} 行）` : ''))
        }

        const qtyIdx = await headerIndex(page, d.qtyHeader)
        // 部分单据的明细里**没有"物料"列**（2026-09-20 扩口径时实测）：
        //   · 工序转移单：明细是工序维度（转出/转入工序号、转移数量），唯一的下拉是"转移类型"
        //   · 财务单据（预收/预付/收付款）：明细是账户/金额维度，唯一的可填列是"账户"+"金额"
        // 这类单据由配置 detail.skipMaterialFill = true 显式声明，此时不再要求物料列存在，
        // 也不执行物料填充（但仍会校验数量列 —— 配置里把 qtyHeader 指向真正的金额/数量列）。
        const needMaterial = !d.skipMaterialFill
        const matIdx = needMaterial
          ? await headerIndex(page, d.materialHeader || d.materialPlaceholder || '物料')
          : -1
        if (qtyIdx < 0) problems.push(`L1 明细表找不到「${d.qtyHeader}」列`)
        if (needMaterial && matIdx < 0) problems.push(`L1 明细表找不到「${d.materialHeader || '物料'}」列`)

        // ---- 诊断 3) 列索引与第一行单元格结构 + 结构一致性自检 ----
        rec.steps.diagMatIdx = matIdx
        rec.steps.diagQtyIdx = qtyIdx
        rec.steps.diagHeaders = await page.locator(DT_HEADS)
          .allInnerTexts().then(a => a.map(x => clean(x))).catch(() => [])
        const row0 = rowLoc(page, 0)
        rec.steps.diagTdCount = (await row0.count()) > 0 ? await row0.locator('td').count() : 0
        const struct = await detailStructure(page)
        rec.steps.diagStructure = struct
        if (!struct.consistent) {
          problems.push(
            `L1 明细表定位结构异常：表头 ${struct.thCount} 列、行 ${struct.rowCount} 行、每行 ${struct.tdCount} 个单元格，` +
            `列数与单元格数不一致 → 位置定位不可信（这是测试侧问题，需修定位，不代表功能异常）`
          )
        }
        const cellDump = []
        for (let k = 0; k < rec.steps.diagTdCount; k++) {
          const td = row0.locator('td').nth(k)
          cellDump.push({
            i: k,
            sel: await td.locator('.el-select').count(),
            input: await td.locator('input').count(),
            text: (await readCellText(page, 0, k)).slice(0, 24)
          })
        }
        rec.steps.diagRow0Cells = cellDump

        for (let i = preRows; i < rows; i++) {
          // 无物料列的单据（skipMaterialFill）跳过物料填充，直接填数量/金额列
          let m = { ok: true, code: '', label: '' }
          if (needMaterial) {
            m = await fillRowMaterial(page, i, matIdx)
            if (!m.ok) { problems.push(`L1 ${m.reason}`); continue }
          }
          let qtyActual = null
          if (qtyIdx >= 0) {
            const q = await fillRowQty(page, i, qtyIdx, d.qtyValues[i - preRows])
            if (!q.ok) problems.push(`L1 ${q.reason}`)
            else qtyActual = q.actual
          }
          // 其余数值列（如"单价"）：销售出库单有【设置中心-单价校验】"单价不能为 0"，
          // 不填会被业务校验拦住保存（实测 2026-09-13），所以按配置补填。
          // fillRowQty 本身是"按列索引填数字输入框"的通用实现，这里复用它。
          if (Array.isArray(d.fillNumbers)) {
            const filledNumbers = []
            for (const fn of d.fillNumbers) {
              const idx = await headerIndex(page, fn.header)
              if (idx < 0) { problems.push(`L1 明细表找不到「${fn.header}」列（需填 ${fn.value}）`); continue }
              const r = await fillRowQty(page, i, idx, fn.value)
              if (!r.ok) problems.push(`L1 ${r.reason}`)
              else filledNumbers.push({ header: fn.header, actual: r.actual })
            }
            if (filledNumbers.length) numbersFilled.push({ row: i + 1, values: filledNumbers })
          }
          // 明细里的下拉列（如生产工单把「仓库」做成明细列、整单共用）：按列头选第一个可选项。
          // 2026-11-11 新增：原先配置把「仓库」写成表头下拉，但该页表头没有仓库控件 → 误报找不到下拉。
          if (Array.isArray(d.fillSelects)) {
            for (const fs of d.fillSelects) {
              const idx = await headerIndex(page, fs.header)
              if (idx < 0) { problems.push(`L1 明细表找不到「${fs.header}」列（需选择）`); continue }
              const r = await pickRowFirstOption(page, i, idx)
              if (!r.ok) problems.push(`L1 ${r.reason}`)
            }
          }
          // 文本列（工序号等）：页面保存时会丢弃这两列为空的行，不填就会"明细为空"
          if (Array.isArray(d.fillTexts)) {
            const filledTexts = []
            for (const ft of d.fillTexts) {
              const idx = await headerIndex(page, ft.header)
              if (idx < 0) { problems.push(`L1 明细表找不到「${ft.header}」列（需填 ${ft.value}）`); continue }
              const r = await fillRowText(page, i, idx, ft.value)
              if (!r.ok) problems.push(`L1 ${r.reason}`)
              else filledTexts.push({ header: ft.header, actual: r.actual })
            }
            if (filledTexts.length) textsFilled.push({ row: i + 1, values: filledTexts })
          }
          filledRows.push({ row: i + 1, code: m.code, label: m.label, qty: d.qtyValues[i], qtyActual })
        }
        rec.steps.filledRows = filledRows
        rec.steps.numbersFilled = numbersFilled
        rec.steps.textsFilled = textsFilled
      }

      // 保存
      const urlBefore = page.url()
      await saveBtn.click().catch(e => problems.push(`L1 点击保存失败: ${e.message}`))
      const fb = await waitForObservableFeedback(page, { fromUrl: urlBefore, timeout: 15000 })
      rec.steps.saveFeedback = fb.kind
      if (fb.kind === 'none') {
        problems.push('L1 点保存后无任何可观测反馈（无 toast / 无弹窗 / URL 未变）→ 疑似"点了没反应"')
      }
      if (fb.kind === 'toast') {
        const errs = fb.toasts.filter(t => t.kind === 'error')
        rec.steps.saveToasts = fb.toasts
        if (errs.length) problems.push(`L1 保存报错: ${errs.map(e => e.text).join(' | ')}`)
      }

      // 保存后页面可能仍在过渡态（跳转中/重渲染），此时读到的是"旧内容"——
      // 实测销售合同在这一刻从明细首行读到了物料编码 MAT000001，刷新后才拿到真单号 SCON-...。
      // 因此先等网络空闲 + 短暂稳定，再读单号。
      await page.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => {})
      await page.waitForTimeout(800)
      const code = await readDocCode(page, bill)
      rec.steps.docCode = code
      if (!code) problems.push('L1 保存后读不到单据编号')

      if (bill.detailUrlPattern) {
        const re = new RegExp(bill.detailUrlPattern)
        if (!re.test(page.url())) {
          problems.push(`L1 保存后 URL 未回写 id（抓 D6）: 当前 ${page.url()} 不匹配 ${bill.detailUrlPattern}`)
        } else {
          // 刷新后必须仍是同一单
          const urlAfterSave = page.url()
          rec.steps.detailUrl = urlAfterSave
          await page.reload({ waitUntil: 'domcontentloaded' })
          await page.waitForTimeout(1500)
          if (page.url() !== urlAfterSave) problems.push(`L1 保存后刷新地址发生变化（${urlAfterSave} → ${page.url()}）`)
          const codeAfterReload = await readDocCode(page, bill)
          rec.steps.docCodeAfterReload = codeAfterReload
          if (codeAfterReload && code && codeAfterReload !== code) {
            problems.push(`L1 刷新后单号变了（${code} → ${codeAfterReload}）`)
          }
        }
      }

      // ================= L2 列表回读 =================
      await page.goto(bill.listPath, { waitUntil: 'domcontentloaded' })
      const listWait = code ? await waitForListContains(page, code, 20000) : { ok: false, waitedMs: 0 }
      rec.steps.listWait = listWait
      rec.steps.listHasCode = listWait.ok
      if (code && !listWait.ok) {
        problems.push(`L2 列表里搜不到刚保存的单号 ${code}（等待 ${listWait.waitedMs}ms 仍无）→ 保存未真正落库`)
      }
      // 首行是否就是新单（抓 PG NULLS FIRST 默认排序坑）
      const firstRow = clean(await page.locator('.el-table__body-wrapper .el-table__row').first().innerText().catch(() => ''))
      rec.steps.listFirstRow = firstRow.slice(0, 160)
      if (code && firstRow && !firstRow.includes(code)) {
        problems.push(`L2 新单不在列表第 1 行（首行: ${firstRow.slice(0, 80)}）→ 疑似默认排序问题（抓 D9）`)
      }
      const listPlaceholders = await findPlaceholders(page)
      if (listPlaceholders.length) problems.push(`L2 列表出现占位符 ${listPlaceholders.join('/')}`)

      // ================= L3 详情回显（★核心）=================
      // 包住 L3+L4：Playwright 异常（典型如"输入框 disabled 导致 fill 超时 20s"）
      // 若不捕获会直接中断测试 → 连报告文件都写不出来（实测 2026-09-14 采购合同/销售退货），
      // 导致该单据状态变成"未定性"、凭空多出一堆连带误报。捕获后仍照常写报告并如实记录原因。
      try {
      // 不能用"保存后 URL 回写的 id"来进详情 —— 本项目确有单据存在该缺陷（见 D6/L1），
      // 统一从列表页点单号链接进入，保证 L3 仍然能执行（缺陷由 L1 单独报出，不互相掩盖）。
      if (code) {
        // 单号入口在各列表页形态不一（2026-09-20 扩到 35 张单据时实测）：
        //   ① span.order-no-text（SaleOrderList 等，L57-L60）
        //   ② span.bill-no-text
        //   ③ el-link（免费样品单 / BOM 列表走 CrudTable 的 el-link）
        //   ④ 兜底：表格内包含该单号的任意 span
        // 点击后必须确认"真的离开了列表页"，否则视为未生效（避免点空元素却记成成功）。
        const clickCandidates = [
          `.order-no-text:has-text("${code}")`,
          `.bill-no-text:has-text("${code}")`,
          `.el-table .el-link:has-text("${code}")`,
          `.el-table a:has-text("${code}")`,
          `.el-table td span:has-text("${code}")`
        ]
        let clicked = false
        for (const sel of clickCandidates) {
          const el = page.locator(sel).first()
          if ((await el.count()) === 0) continue
          const urlBeforeClick = page.url()
          await el.click({ timeout: 8000 }).catch(() => {})
          await page.waitForTimeout(1800)
          if (page.url() !== urlBeforeClick) {
            clicked = true
            rec.steps.l3EntrySelector = sel
            break
          }
        }
        if (!clicked) {
          problems.push(`L3 列表里找不到单号 ${code} 的可点击入口（试过 order-no-text/bill-no-text/el-link/a/td span），无法进入详情页`)
        }
      } else {
        problems.push('L3 读不到单号，无法进入详情页')
      }
      // 详情页必须重新归一化锚点：页面已重新导航，之前的 data-e2e-detail 标记不会保留。
      // 与 L4 同理：先等明细表渲染出来再打锚点，否则会命中 0 行。
      await page.locator('.el-table').first().waitFor({ state: 'visible', timeout: 20000 }).catch(() => {})
      await page.waitForTimeout(800)
      let l3Anchored = await normalizeDetailAnchor(page, bill)
      for (let i = 0; i < 12 && !l3Anchored; i++) {
        await page.waitForTimeout(500)
        l3Anchored = await normalizeDetailAnchor(page, bill)
      }
      rec.steps.detailAnchorResolved = l3Anchored
      rec.steps.detailUrl = page.url()

      const detailRows = await readDetailRows(page)
      // 关键证据：重开详情后的单据状态。"保存=草稿"是本项目定死的状态机制，
      // 若保存后不再是草稿，明细输入框会因 :disabled="!isDraft" 变灰而无法编辑（实测采购合同）。
      // 状态在页面顶部是标签（el-tag）而非 form-item，故直接抓标签文本。
      rec.steps.statusAfterReopen = await readFormValue(page, '状态')
      rec.steps.tagsAfterReopen = await page.locator('.el-tag').allInnerTexts().catch(() => [])
      rec.steps.qtyInputDisabledAtL3 = (await headerIndex(page, bill.detail?.qtyHeader || '数量')) >= 0
        ? await rowLoc(page, 0).locator('td').nth(await headerIndex(page, bill.detail?.qtyHeader || '数量'))
            .locator('input').first().isDisabled().catch(() => null)
        : null
      rec.steps.detailRowsAfterReopen = detailRows.length
      rec.steps.detailRowsText = detailRows
      if ((await page.locator(DETAIL_TABLE).count()) > 0) {
        if (detailRows.length !== filledRows.length + (preRows || 0)) {
          problems.push(`L3 重开详情后明细行数 ${detailRows.length} ≠ 填写行数 ${filledRows.length + (preRows || 0)}` + ((preRows || 0) ? `（含页面自带 ${preRows} 行）` : '') + ' →★明细不持久化（抓 D2/D4）')
        }
        // 逐行、按列精确比对（不用整行 includes，避免单元格拼接造成误报）
        const matIdxL3 = await headerIndex(page, bill.detail?.materialHeader || '物料')
        const qtyIdxL3 = await headerIndex(page, bill.detail?.qtyHeader || '数量')
        rec.steps.l3ColumnIdx = { material: matIdxL3, qty: qtyIdxL3 }
        for (const f of filledRows) {
          const rIdx = f.row - 1
          if (matIdxL3 >= 0 && f.code) {
            const cell = await readCellText(page, rIdx, matIdxL3)
            f.l3MaterialCell = cell
            // 比对前统一去掉空白：下拉显示「MAT000001E2E-成品A」，列表/详情单元格显示「MAT000001 E2E-成品A」，
            // 直接 includes 会因一个空格误报"明细不持久化"（2026-09-20 实测）。
            if (!normText(cell).includes(normText(f.code))) {
              problems.push(`L3 第${f.row}行物料回显不符：期望含 ${f.code}，实际单元格「${cell}」→★明细不持久化（抓 D4）`)
            }
          }
          if (qtyIdxL3 >= 0 && f.qty != null) {
            const cell = await readCellText(page, rIdx, qtyIdxL3)
            f.l3QtyCell = cell
            if (Number(cell) !== Number(f.qty)) {
              problems.push(`L3 第${f.row}行数量回显不符：期望 ${f.qty}，实际单元格「${cell}」→★明细数量未持久化`)
            }
          }
        }
      } else {
        problems.push('L3 详情页找不到明细表格（normalizeDetailAnchor 未标记成功：该页既无 .table-wrap 也无 .detail-subtable-header）')
      }
      // 表头回填校验：L1 填过的必填表头，详情页必须回显
      // （抓"打开已有单据后表头空"这类缺陷，与明细回显同等重要）
      const headerBack = {}
      for (const label of (bill.headerSelects || [])) {
        const v = await readFormValue(page, label)
        headerBack[label] = v
        if (!v) {
          problems.push(`L3 重开详情后表头「${label}」为空（保存时已选「${rec.steps['header_' + label] || '?'}」）→★详情未回填`)
        }
      }
      rec.steps.l3HeaderBack = headerBack
      // 诊断：把"客户"表单项的真实 DOM 存下来 —— 用于区分"读取器没读对"与"页面确实显示占位符"
      rec.steps.l3CustomerItemHtml = (await formItem(page, '客户').innerHTML().catch(() => '')).slice(0, 700)

      const detailPlaceholders = await findPlaceholders(page)
      if (detailPlaceholders.length) problems.push(`L3 详情页出现占位符 ${detailPlaceholders.join('/')}`)

      // ================= L4 改明细再存 =================
      if (d && filledRows.length > 0) {
        const qtyIdx = await headerIndex(page, d.qtyHeader)
        const newQty = Number(d.qtyValues[0]) + 5
        if (qtyIdx >= 0) {
          const r = await fillRowQty(page, (filledRows[0].row - 1), qtyIdx, newQty)
          if (!r.ok) problems.push(`L4 ${r.reason}`)
          rec.steps.l4QtyAfterFill = r.actual
          const urlB4 = page.url()
          rec.steps.l4UrlBeforeSave = urlB4
          rec.steps.l4RowsBeforeSave = await detailRowCount(page)
          const saveBtns = page.locator('button:has-text("保存")')
          rec.steps.l4SaveBtnCount = await saveBtns.count()
          await saveBtns.first().click().catch(e => problems.push(`L4 点击保存失败: ${e.message}`))
          const fb2 = await waitForObservableFeedback(page, { fromUrl: urlB4, timeout: 15000 })
          rec.steps.l4SaveFeedback = fb2.kind
          if (fb2.kind === 'toast') {
            rec.steps.l4Toasts = fb2.toasts
            const errs = fb2.toasts.filter(t => t.kind === 'error')
            if (errs.length) problems.push(`L4 二次保存报错: ${errs.map(e => e.text).join(' | ')}`)
          }
          await page.waitForTimeout(1500)
          rec.steps.l4UrlAfterSave = page.url()
          rec.steps.l4UrlChangedBySave = page.url() !== urlB4
          rec.steps.l4RowsAfterSave = await detailRowCount(page)

          // 重新打开（刷新即可，URL 已是该单详情）
          const urlDetail = page.url()
          rec.steps.l4UrlBeforeReload = urlDetail
          await page.reload({ waitUntil: 'domcontentloaded' })
          // 刷新后必须重新归一化锚点（data-e2e-detail 标记不随页面重载保留）。
          // 2026-09-20 实测坑：锚点依赖 .table-wrap / .detail-subtable-header **已渲染**，
          // 打早了会打不到 → 明细选择器命中 0 行 → 误报「更新把既有明细清空」
          // （组装单 / BOM / 采购预付款单各命中一次，查库确认数据都在）。
          // 因此先等明细表渲染，再打锚点；仍未命中就轮询重试。
          await page.locator('.el-table').first().waitFor({ state: 'visible', timeout: 20000 }).catch(() => {})
          await page.waitForTimeout(800)
          let l4Anchored = await normalizeDetailAnchor(page, bill)
          for (let i = 0; i < 12 && !l4Anchored; i++) {
            await page.waitForTimeout(500)
            l4Anchored = await normalizeDetailAnchor(page, bill)
          }
          rec.steps.l4AnchorResolved = l4Anchored
          // 等明细表渲染 + 数据到齐（轮询，不用固定 sleep）
          const tableAppeared = await page.locator(DETAIL_TABLE).first()
            .waitFor({ state: 'visible', timeout: 20000 }).then(() => true).catch(() => false)
          const waited = await waitForDetailRows(page, filledRows.length + (preRows || 0), 20000)
          rec.steps.l4UrlAfterReload = page.url()
          rec.steps.l4DetailTableVisible = tableAppeared
          rec.steps.l4DetailTableCount = await page.locator(DETAIL_TABLE).count()
          rec.steps.l4ElTableCount = await page.locator('.el-table').count()
          rec.steps.l4WaitDetailRows = waited
          // 表头字段是否回填（用于区分"数据没到"与"真的丢了"）
          rec.steps.l4HeaderAfterReload = {
            customer: await readFormValue(page, '客户'),
            saler: await readFormValue(page, '销售员'),
            currency: await readFormValue(page, '币种')
          }
          rec.steps.l4PageTextHead = clean(await page.locator('#app').innerText().catch(() => '')).slice(0, 200)

          const rows2 = await readDetailRows(page)
          rec.steps.detailRowsAfterEdit = rows2.length
          rec.steps.detailUrlAfterEdit = urlDetail
          if (rows2.length !== filledRows.length + (preRows || 0)) {
            problems.push(`L4 二次保存后明细行数 ${rows2.length} ≠ 原 ${filledRows.length + (preRows || 0)} →★更新时把既有明细清空（抓 D3）`)
          }
          const idxQ = await headerIndex(page, d.qtyHeader)
          const cellQ = await readCellText(page, (filledRows[0].row - 1), idxQ)
          rec.steps.l4QtyCell = cellQ
          if (Number(cellQ) !== Number(newQty)) {
            problems.push(`L4 修改后的数量未生效：期望 ${newQty}，实际单元格「${cellQ}」`)
          }
          const idxM = await headerIndex(page, d.materialHeader || '物料')
          if (filledRows[1] && filledRows[1].code) {
            const cellM2 = await readCellText(page, (filledRows[1].row - 1), idxM)
            rec.steps.l4Row2MaterialCell = cellM2
            if (!normText(cellM2).includes(normText(filledRows[1].code))) {
              problems.push(`L4 第2行原明细丢失：期望含 ${filledRows[1].code}，实际单元格「${cellM2}」→★抓 D3`)
            }
          }
        }
      }

      // ================= L6 提交按钮可达性（2026-09-21 新增）=================
      // 背景：本用例原先「绝不点提交」，于是"提交按钮点了没反应"这类缺陷 100% 漏检 ——
      //       实测 10 张单据的 onSubmit 里引用了页面中不存在的变量，点击即抛 ReferenceError，
      //       既不提示也不发请求（客户在 121 上手动点才暴露）。
      // 口径：点一次「提交」后必须出现"可观察反馈"（确认框 或 校验提示 或 跳转），
      //       且控制台 0 异常（由 mon 统一判）；若弹出确认框一律「取消」，
      //       绝不把单据推进审批在途，保持本用例"不污染在途流程"的既有约束。
      try {
        const submitBtn = page.locator('button:has-text("提交")').first()
        const submitCount = await submitBtn.count()
        rec.steps.l6SubmitBtnCount = submitCount
        if (submitCount === 0) {
          rec.steps.l6SubmitSkipped = '页面无「提交」按钮（非草稿态或该页无此动作）'
        } else {
          rec.steps.l6SubmitDisabled = await submitBtn.isDisabled().catch(() => null)
          if (rec.steps.l6SubmitDisabled === true) {
            problems.push('L6「提交」按钮处于禁用状态（草稿态不应禁用）')
          }
          const urlB6 = page.url()
          await submitBtn.click().catch(e => problems.push(`L6 点击提交失败: ${e.message}`))
          const fb6 = await waitForObservableFeedback(page, { fromUrl: urlB6, timeout: 15000 })
          rec.steps.l6SubmitFeedback = fb6.kind
          rec.steps.l6SubmitToasts = (fb6.toasts || []).map(t => t.kind + ':' + t.text)
          if (fb6.kind === 'none') {
            problems.push('L6 点「提交」后无任何可观察反馈（无确认框/无提示/无跳转）→★提交按钮疑似不可用（提交链路 JS 异常）')
          }
          if (fb6.kind === 'dialog') {
            rec.steps.l6DialogText = clean(await page.locator('.el-overlay-message-box').first().innerText().catch(() => '')).slice(0, 150)
            const cancel6 = page.locator('.el-overlay-message-box button:has-text("取消")').last()
            if ((await cancel6.count()) > 0) await cancel6.click().catch(() => {})
            else await page.keyboard.press('Escape').catch(() => {})
            await page.waitForTimeout(800)
          }
        }
      } catch (e) {
        rec.steps.l6Error = String(e.message).slice(0, 300)
        problems.push(`L6 提交可达性执行中断: ${String(e.message).split('\n')[0]}`)
      }

      // ================= L7 置为已生效（2026-11-11 新增，供 ⑤c 写链路体检取数）=================
      // 背景：⑤c 需要"列表里有一张单据"才能点 复制/提交/审核/作废；此前只有主链 11 张有数据，
      //       其余单据 SKIP「列表页没有可点的单号」。
      // 用户决策（2026-11-11）：不重写其余单据的造数字段表，改为复用本用例通过界面建好的单据，
      //       用接口把它提交/审核到已生效；门禁最后一步（⑤e）统一清理，不在库里留测试单。
      try {
        const seg = new URL(page.url()).pathname.split('/').filter(Boolean)
        const lastSeg = seg[seg.length - 1] || ''
        const docId = /^\d+$/.test(lastSeg) ? lastSeg : ''
        const apiCfg = BILL_API[bill.key]
        rec.steps.apiBase = apiCfg?.base || null
        rec.steps.detailId = docId
        if (!docId || !apiCfg || !apiCfg.base) {
          rec.steps.effective = false
          rec.steps.effectiveNote = !docId
            ? '地址上取不到单据主键（未回写 id），无法置生效'
            : (apiCfg?.note || '该单据未登记接口基路径，无法置生效')
        } else {
          // 用页面自身登录态发接口请求（token 在 localStorage），不引入第二套登录
          const token = await page.evaluate(() => localStorage.getItem('token') || '')
          const call = (method, urlPath) => page.evaluate(async ({ token, urlPath, method }) => {
            try {
              const r = await fetch(urlPath, {
                method,
                headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }
              })
              const t = await r.text()
              let j = null
              try { j = t ? JSON.parse(t) : null } catch (_) { /* 非 JSON 响应 */ }
              return { http: r.status, code: j?.code ?? null, message: j?.message || '', status: j?.data?.status ?? null }
            } catch (e) { return { http: 0, code: null, message: String(e.message), status: null } }
          }, { token, urlPath, method })

          const base = apiCfg.base
          for (const p of (apiCfg.pre || [])) await call('POST', `${base}/${docId}/${p}`)
          let sr = { message: '' }
          if (!apiCfg.noSubmit) sr = await call('POST', `${base}/${docId}/submit`)
          const ar = await call(apiCfg.approveMethod || 'POST', `${base}/${docId}/${apiCfg.approvePath || 'approve'}`)
          // 部分单据生效后还需补一步（如委外加工单要「下达」下游才能选），按登记表顺序补调
          for (const p of (apiCfg.afterEffective || [])) await call('POST', `${base}/${docId}/${p}`)
          const dr = await call('GET', `${base}/${docId}`)
          const st = String(dr.status || '')
          rec.steps.effectiveStatus = st
          const effectiveValues = apiCfg.effectiveValues || ['已生效', '已审核', '生效']
          rec.steps.effective = effectiveValues.includes(st)
          if (!rec.steps.effective) {
            rec.steps.effectiveNote = `提交=${sr.message || 'ok'}；审核=${ar.message || 'ok'}；回读状态=${st || '空'}`
          }
        }
      } catch (e) {
        rec.steps.effective = false
        rec.steps.effectiveNote = '置生效执行中断: ' + String(e.message).split('\n')[0]
      }

      mon.stop()
      problems.push(...mon.problems('03-闭环'))

      } catch (e) {
        // 如实记录：这是测试侧执行中断，不是产品缺陷。报告仍会写出，避免"文件缺失=未定性"。
        rec.steps.l3l4Error = String(e.message).slice(0, 600)
        problems.push(`L3/L4 执行中断（测试侧异常，需修用例，不代表功能异常）: ${String(e.message).split('\n')[0]}`)
      }

      rec.ok = problems.length === 0
      rec.problems = problems
      writeLoopResult(rec)
      writeLoopSummary()

      expect(problems, `[${bill.name}] 端到端闭环未通过：${describeProblems(problems)}`).toEqual([])
    })
  }
})
