/**
 * 测试租户「基础数据」准备 / 清理脚本
 *
 * 为什么需要它：新租户库是空的（实测 sc_wudong：物料/客户/供应商/仓库/单位/币种/银行账户 全为 0），
 * 而端到端闭环用例要填"客户/物料/仓库/币种"等下拉，没有基础数据就全部报"下拉无选项"。
 * 本脚本通过 HTTP 接口（不直连库）按正确的依赖顺序造一批最小可用数据。
 *
 * 用法：
 *   node tests/smoke/seed-tenant-data.mjs             # 造数据（幂等：已存在则复用）
 *   node tests/smoke/seed-tenant-data.mjs --cleanup   # 清理（只删本脚本造的数据）
 *
 * 造数顺序（依赖决定，不能乱）：
 *   计量单位 → 币种 → 仓库 → 物料分类 → 物料 → 客户 → 供应商 → 银行账户 → 员工
 *
 * 字段与取值依据（源码核对，非猜测）：
 *   - 编码：单位/币种/物料分类/物料/客户/供应商/银行账户 均后端自动生成 → 脚本不传
 *   - 仓库 stockCode 后端【无兜底】（WarehouseServiceImpl 无 save 覆写，列 NOT NULL UNIQUE）→ 必须手填
 *   - status 取值分两套：单位/币种/仓库/物料分类/银行账户 = 启用/停用；物料/客户/供应商 = 正常/停用
 *   - 物料硬必填：materialName / materialType / baseUnitId / status
 */
import { cfg, bootSummary } from './smoke-env.mjs'
import { login, api, apiOrThrow, currentTenant } from './smoke-http.mjs'

const CLEANUP = process.argv.includes('--cleanup')
/** 所有造出来的数据都带这个前缀，便于识别与清理 */
const P = 'E2E-'

function log(step, msg) {
  console.log(`  [${step}] ${msg}`)
}

/** 分页查询，返回 records 数组 */
async function list(token, path, keyword = '') {
  const q = keyword ? `&keyword=${encodeURIComponent(keyword)}` : ''
  const r = await api('GET', `${path}/page?current=1&size=100${q}`, { token })
  if (!r.ok) return { ok: false, records: [], raw: r.raw, msg: r.message }
  const d = r.data || {}
  return { ok: true, records: d.records || [], total: d.total }
}

/** 详情（找不到返回 null） */
async function findOne(token, path, keyword) {
  const r = await list(token, path, keyword)
  return r.ok ? (r.records.find(x => JSON.stringify(x).includes(keyword)) || r.records[0] || null) : null
}

/** 创建；返回 {ok, msg, raw} */
async function create(token, path, body, label) {
  const r = await api('POST', path, { token, body })
  if (!r.ok) {
    console.log(`      ✗ 创建失败 ${label}: ${r.message || r.raw || `HTTP ${r.httpStatus}`}`)
  }
  return r
}

async function main() {
  await bootSummary()
  const { token } = await login()
  const t = currentTenant()
  console.log(`\n测试租户：${t.tenantCode}（库 ${t.dbName}）`)

  if (CLEANUP) return await doCleanup(token)

  console.log('\n===== 基础数据准备（幂等）=====')
  const result = { created: [], reused: [], failed: [] }
  const mark = (label, kind, extra = '') => {
    result[kind].push(label + (extra ? ` (${extra})` : ''))
    log(kind === 'created' ? '新建' : (kind === 'reused' ? '复用' : '失败'), label + (extra ? ` ${extra}` : ''))
  }

  // ---------- 1. 计量单位 ----------
  let unit = await findOne(token, '/api/basedata/unit', P)
  if (unit) mark('计量单位', 'reused', unit.unitName)
  else {
    const r = await create(token, '/api/basedata/unit', {
      unitName: `${P}个`, unitGroup: 'E2E', conversionRate: 1, isBaseUnit: 1,
      status: '启用', remark: 'E2E自动造数'
    }, '计量单位')
    if (r.ok) { unit = await findOne(token, '/api/basedata/unit', `${P}个`); mark('计量单位', 'created', unit && unit.unitName) }
    else result.failed.push('计量单位: ' + (r.message || r.raw))
  }

  // ---------- 2. 币种（通常已有人民币，先查后造） ----------
  let currency = null
  const curList = await list(token, '/api/basedata/currency')
  if (curList.ok && curList.records.length) {
    currency = curList.records.find(c => c.isBaseCurrency === 1 || c.isBaseCurrency === true) || curList.records[0]
    mark('币种', 'reused', currency.currencyName)
  } else {
    const r = await create(token, '/api/basedata/currency', {
      // 2026-11-12：造数币种**不能**标成本位币 —— 实测测试租户出现两个本位币（人民币 + E2E测试币），
      // 页面"默认币种取本位币"的排序随库内顺序漂移，用户看到的就是"采购入库币种不是人民币"。
      currencyName: `${P}测试币`, currencySymbol: 'E', exchangeRate: 1, isBaseCurrency: 0,
      status: '启用', remark: 'E2E自动造数'
    }, '币种')
    if (r.ok) { currency = await findOne(token, '/api/basedata/currency', P); mark('币种', 'created', currency && currency.currencyName) }
    else result.failed.push('币种: ' + (r.message || r.raw))
  }

  // ---------- 3. 仓库（stockCode 必须手填） ----------
  let warehouse = await findOne(token, '/api/basedata/warehouse', 'E2E-WH')
  if (warehouse) mark('仓库', 'reused', warehouse.stockName)
  else {
    const r = await create(token, '/api/basedata/warehouse', {
      stockCode: 'E2E-WH-01', stockName: `${P}主仓`, stockType: '普通仓库',
      isBinControl: 0, isMrpAvailable: 1, status: '启用', remark: 'E2E自动造数'
    }, '仓库')
    if (r.ok) { warehouse = await findOne(token, '/api/basedata/warehouse', 'E2E-WH'); mark('仓库', 'created', warehouse && warehouse.stockName) }
    else result.failed.push('仓库: ' + (r.message || r.raw))
  }
  // 第二个仓库（调拨单要求调入/调出不同仓）
  let warehouse2 = null
  const whList = await list(token, '/api/basedata/warehouse')
  const others = (whList.records || []).filter(w => w.stockCode !== 'E2E-WH-01')
  if (others.length) { warehouse2 = others[0]; mark('第二仓库', 'reused', warehouse2.stockName) }
  else {
    const r = await create(token, '/api/basedata/warehouse', {
      stockCode: 'E2E-WH-02', stockName: `${P}备仓`, stockType: '普通仓库',
      isBinControl: 0, isMrpAvailable: 1, status: '启用', remark: 'E2E自动造数'
    }, '第二仓库')
    if (r.ok) { warehouse2 = await findOne(token, '/api/basedata/warehouse', 'E2E-WH-02'); mark('第二仓库', 'created', warehouse2 && warehouse2.stockName) }
    else result.failed.push('第二仓库: ' + (r.message || r.raw))
  }

  // ---------- 4. 物料分类 ----------
  let category = await findOne(token, '/api/basedata/material-category', P)
  if (category) mark('物料分类', 'reused', category.categoryName)
  else {
    const r = await create(token, '/api/basedata/material-category', {
      // 编码显式提供：该租户的编号规则可能关闭了"自动编号"（实测报错"已关闭自动编号，请手动输入编码"）
      categoryCode: 'E2E-CAT-01',
      categoryName: `${P}测试分类`, parentId: null, categoryLevel: 1, sortOrder: 0,
      status: '启用', remark: 'E2E自动造数'
    }, '物料分类')
    if (r.ok) { category = await findOne(token, '/api/basedata/material-category', P); mark('物料分类', 'created', category && category.categoryName) }
    else result.failed.push('物料分类: ' + (r.message || r.raw))
  }
  const categoryId = category ? (category.categoryId || category.id) : null
  const unitId = unit ? (unit.unitId || unit.id) : null

  // ---------- 5. 物料（3 个：成品/半成品/原材料，覆盖 BOM 与生产/委外场景） ----------
  const materialsSpec = [
    { name: `${P}成品A`, type: '成品', price: 200 },
    { name: `${P}半成品B`, type: '半成品', price: 120 },
    { name: `${P}原料C`, type: '原材料', price: 30 }
  ]
  for (const spec of materialsSpec) {
    const exist = await findOne(token, '/api/basedata/material', spec.name)
    if (exist) { mark(`物料${spec.type}`, 'reused', exist.materialName); continue }
    const r = await create(token, '/api/basedata/material', {
      materialName: spec.name,
      materialType: spec.type,
      categoryId,
      baseUnitId: unitId,
      salePrice: spec.price,
      purchasePrice: spec.price * 0.6,
      taxRate: 13,
      minStock: 0,
      maxStock: 0,
      status: '正常',
      remark: 'E2E自动造数'
    }, `物料${spec.type}`)
    if (r.ok) { const m = await findOne(token, '/api/basedata/material', spec.name); mark(`物料${spec.type}`, 'created', m && m.materialName) }
    else result.failed.push(`物料${spec.type}: ` + (r.message || r.raw))
  }

  // ---------- 6. 客户 ----------
  let customer = await findOne(token, '/api/basedata/customer', P)
  if (customer) mark('客户', 'reused', customer.customerName)
  else {
    const r = await create(token, '/api/basedata/customer', {
      customerName: `${P}客户甲`, customerType: '企业客户', customerLevel: '普通客户',
      paymentCategory: '现结', status: '正常', remark: 'E2E自动造数'
    }, '客户')
    if (r.ok) { customer = await findOne(token, '/api/basedata/customer', P); mark('客户', 'created', customer && customer.customerName) }
    else result.failed.push('客户: ' + (r.message || r.raw))
  }

  // ---------- 7. 供应商 ----------
  let supplier = await findOne(token, '/api/basedata/supplier', P)
  if (supplier) mark('供应商', 'reused', supplier.supplierName)
  else {
    const r = await create(token, '/api/basedata/supplier', {
      supplierName: `${P}供应商甲`, supplierType: '企业供应商',
      status: '正常', remark: 'E2E自动造数'
    }, '供应商')
    if (r.ok) { supplier = await findOne(token, '/api/basedata/supplier', P); mark('供应商', 'created', supplier && supplier.supplierName) }
    else result.failed.push('供应商: ' + (r.message || r.raw))
  }

  // ---------- 8. 银行账户（accountNo 显式传，绕开"编号规则未配置"风险） ----------
  let bank = await findOne(token, '/api/basedata/bank-account', 'E2E-ZH')
  if (bank) mark('银行账户', 'reused', bank.accountName)
  else {
    const currencyId = currency ? (currency.currencyId || currency.id) : null
    const r = await create(token, '/api/basedata/bank-account', {
      accountNo: 'E2E-ZH-001', accountName: `${P}测试账户`, bankName: `${P}测试银行`,
      accountType: '基本户', currencyId, openingBalance: 0, currentBalance: 0,
      isDefault: 0, isActive: 1, status: '启用', remark: 'E2E自动造数'
    }, '银行账户')
    if (r.ok) { bank = await findOne(token, '/api/basedata/bank-account', 'E2E-ZH'); mark('银行账户', 'created', bank && bank.accountName) }
    else result.failed.push('银行账户: ' + (r.message || r.raw))
  }

  // ---------- 9. 部门（"申请部门/所属部门"等下拉的数据来源） ----------
  // 实测新租户 department 表 0 条 → 采购申请单 L1 报「表单项「申请部门」下拉没有可选项」（2026-09-20）。
  const deptsSpec = [
    { code: 'E2E-DEPT-01', name: `${P}销售部`, type: '业务部门' },
    { code: 'E2E-DEPT-02', name: `${P}采购部`, type: '业务部门' },
    { code: 'E2E-DEPT-03', name: `${P}财务部`, type: '职能部门' }
  ]
  for (const spec of deptsSpec) {
    const exist = await findOne(token, '/api/basedata/department', spec.name)
    if (exist) { mark(`部门-${spec.name}`, 'reused', exist.deptName); continue }
    const r = await create(token, '/api/basedata/department', {
      deptCode: spec.code, deptName: spec.name, deptType: spec.type,
      parentDeptId: null, status: '启用', remark: 'E2E自动造数'
    }, `部门-${spec.name}`)
    if (r.ok) { const d = await findOne(token, '/api/basedata/department', spec.name); mark(`部门-${spec.name}`, 'created', d && d.deptName) }
    else result.failed.push(`部门-${spec.name}: ` + (r.message || r.raw))
  }

  // ---------- 10. 员工（表头"销售员/采购员"等下拉的数据来源） ----------
  // 实测新租户 employee 表 0 条 → 所有带"销售员"的单据 L1 都报
  // 「表单项「销售员」下拉没有可选项」。查库确认是**数据缺失**，不是权限问题（2026-09-20）。
  const employeesSpec = [
    { name: `${P}销售员甲`, dept: `${P}销售部`, position: '销售员' },
    { name: `${P}采购员甲`, dept: `${P}采购部`, position: '采购员' }
  ]
  for (const spec of employeesSpec) {
    const exist = await findOne(token, '/api/basedata/employee', spec.name)
    if (exist) { mark(`员工-${spec.position}`, 'reused', exist.employeeName); continue }
    // 员工档案现行字段：部门按 id（deptId）、手机号为 mobilePhone（EmployeeEdit.vue「部门」是选 id 的下拉）。
    // 旧的 departmentName / mobile / isEnabled 三个键实体已不存在，会被静默丢弃 → 员工无部门归属。
    // 这里按上面已建的「E2E-销售部 / E2E-采购部」反查 deptId 传入；查不到则留空（dept_id 可空）。
    const dept = await findOne(token, '/api/basedata/department', spec.dept)
    const deptId = dept ? (dept.deptId || dept.id) : null
    const r = await create(token, '/api/basedata/employee', {
      employeeName: spec.name,
      deptId,
      position: spec.position,
      mobilePhone: '13800000001',
      entryDate: '2026-01-01',
      status: '在职',            // EmployeeEdit.vue 的选项：在职 / 离职
      remark: 'E2E自动造数'
    }, `员工-${spec.position}`)
    if (r.ok) { const e = await findOne(token, '/api/basedata/employee', spec.name); mark(`员工-${spec.position}`, 'created', e && e.employeeName) }
    else result.failed.push(`员工-${spec.position}: ` + (r.message || r.raw))
  }

  console.log('\n===== 准备结果 =====')
  console.log(`  新建 ${result.created.length} 项 / 复用 ${result.reused.length} 项 / 失败 ${result.failed.length} 项`)
  if (result.failed.length) {
    result.failed.forEach(f => console.log('  ✗ ' + f))
    console.log('\n注意：失败的项会导致依赖它的用例报"下拉无选项"，请先修这些。')
    process.exit(1)
  }
  console.log('  基础数据齐备，可以跑端到端闭环。')
}

/** 清理：按前缀删掉本脚本造的数据（顺序与造相反，先删被引用的下游） */
async function doCleanup(token) {
  console.log('\n===== 清理（只删 E2E- 前缀的数据）=====')
  const targets = [
    ['银行账户', '/api/basedata/bank-account', 'accountNo'],
    ['供应商', '/api/basedata/supplier', 'supplierCode'],
    ['客户', '/api/basedata/customer', 'customerCode'],
    ['物料', '/api/basedata/material', 'materialCode'],
    ['物料分类', '/api/basedata/material-category', 'categoryCode'],
    ['仓库', '/api/basedata/warehouse', 'stockCode'],
    ['计量单位', '/api/basedata/unit', 'unitCode']
  ]
  let deleted = 0, skipped = 0
  for (const [label, path, idField] of targets) {
    const r = await list(token, path, P)
    if (!r.ok) { log(label, `查询失败，跳过: ${r.msg || r.raw}`); continue }
    // 只删"名字里带 E2E-"的记录（避免误删真实数据）
    const mine = r.records.filter(x => JSON.stringify(x).includes(P))
    for (const rec of mine) {
      const id = rec.id || rec[idField.replace('Code', 'Id')] || rec[Object.keys(rec).find(k => /^(.+)Id$/.test(k)) || 'id']
      if (id == null) { skipped++; continue }
      const del = await api('DELETE', `${path}/${id}`, { token })
      if (del.ok) { deleted++; log(label, `已删 ${rec[idField] || id}`) }
      else { skipped++; log(label, `删除失败 ${rec[idField] || id}: ${del.message || del.raw}`) }
    }
    if (!mine.length) log(label, '无需清理')
  }
  console.log(`\n清理完成：删除 ${deleted} 条，跳过 ${skipped} 条`)
}

main().catch(e => {
  console.error('\n执行失败：' + e.message)
  process.exit(1)
})
