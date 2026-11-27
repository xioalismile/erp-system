/**
 * 下拉字段名体检脚本（防复发，2026-09-20 建立）
 *
 * 为什么需要：反查用户反馈发现 4 类系统性错配——前端下拉把后端实体字段名写成了"更啰嗦的变体"：
 *   部门 Department.deptId/deptName      → 被写成 departmentId/departmentName（4 处）
 *   仓库 Warehouse.stockId/stockName      → 被写成 warehouseId/warehouseName（4 处）
 *   结算方式 SettlementMode.settleModeId/settleModeName → 被写成 settleId/settleName（8 处 + 6 处回填）
 *   货位 Location.binId/binName           → 被写成 locationId/locationName（1 文件 2 处）
 * 后果统一是：el-option 的 label/value 全取到 undefined → **下拉能打开但选项文字空白、且选中后没有值**，
 * 编译期不报错、运行时也不报错，只能靠人眼发现。本脚本把这类错误变成可机械发现。
 *
 * 用法（在 frontend 目录下执行）：
 *   node scripts/check-dropdown-fields.cjs
 * 退出码：0 = 全部通过；1 = 发现疑似问题（可用于 CI / 提交前自查）
 *
 * 判定规则：
 *   规则①（强）：v-for="x in LIST" 且 LIST 在白名单内 → 该 el-option 标签内所有 `x.属性` 必须在白名单字段里
 *   规则②（弱）：任何位置出现已知的错误变体属性名（如 .warehouseName）→ 警告
 * 白名单的字段名 = 后端实体真实字段（对照 backend/src/main/java/com/erp/**\/entity/*.java）。
 * 新增主档下拉时，请把「下拉变量名 → 实体字段清单」补进下方 DROPDOWN_ENTITY_FIELDS。
 */
const fs = require('fs')
const path = require('path')

const SRC_ROOT = path.resolve(__dirname, '../src')

/** 下拉变量名 → 后端实体真实字段（唯一事实来源：实体类） */
const DROPDOWN_ENTITY_FIELDS = {
  // 部门 Department
  departments: ['deptId', 'deptName', 'deptCode', 'parentDeptId', 'deptType', 'managerId', 'parentDeptName', 'status', 'remark'],
  deptOptions: ['deptId', 'deptName', 'deptCode', 'parentDeptId', 'deptType', 'managerId', 'status', 'remark'],
  // 仓库 Warehouse
  warehouses: ['stockId', 'stockName', 'stockCode', 'stockType', 'address', 'managerId', 'status', 'remark'],
  warehouseList: ['stockId', 'stockName', 'stockCode', 'stockType', 'address', 'managerId', 'status', 'remark'],
  warehouseOptions: ['stockId', 'stockName', 'stockCode', 'stockType', 'address', 'status'],
  // 货位 Location
  locations: ['binId', 'binName', 'binCode', 'stockId', 'status', 'remark'],
  locationOptions: ['binId', 'binName', 'binCode', 'stockId', 'status'],
  // 结算方式 SettlementMode
  settleTypes: ['settleModeId', 'settleModeCode', 'settleModeName', 'settleType', 'sortOrder', 'status', 'remark'],
  settleModes: ['settleModeId', 'settleModeCode', 'settleModeName', 'settleType', 'sortOrder', 'status', 'remark'],
  settleModeOptions: ['settleModeId', 'settleModeCode', 'settleModeName', 'settleType', 'sortOrder', 'status'],
  // 员工 Employee
  employees: ['employeeId', 'employeeCode', 'employeeName', 'deptId', 'deptName', 'positionId', 'positionName', 'phone', 'status'],
  employeeOptions: ['employeeId', 'employeeCode', 'employeeName', 'deptId', 'deptName', 'positionId', 'status'],
  // 客户 Customer
  customers: ['customerId', 'customerCode', 'customerName', 'shortName', 'customerType', 'customerLevel', 'status'],
  customerOptions: ['customerId', 'customerCode', 'customerName', 'shortName', 'customerType', 'status'],
  // 供应商 Supplier
  suppliers: ['supplierId', 'supplierCode', 'supplierName', 'shortName', 'contactPerson', 'status'],
  supplierOptions: ['supplierId', 'supplierCode', 'supplierName', 'shortName', 'status'],
  // 物料 Material
  materials: ['materialId', 'materialCode', 'materialName', 'fullName', 'model', 'specification', 'categoryId', 'categoryName', 'baseUnitId', 'baseUnitName', 'status'],
  materialOptions: ['materialId', 'materialCode', 'materialName', 'fullName', 'model', 'specification', 'categoryId', 'categoryName', 'baseUnitId', 'baseUnitName', 'status'],
  // 计量单位 UnitOfMeasure
  units: ['unitId', 'unitCode', 'unitName', 'status'],
  unitOptions: ['unitId', 'unitCode', 'unitName', 'status'],
  // 币种 Currency
  currencies: ['currencyId', 'currencyCode', 'currencyName', 'exchangeRate', 'isBase', 'status'],
  // 价格级别 PriceLevel
  priceLevels: ['priceLevelId', 'priceLevelCode', 'priceLevelName', 'discountRate', 'status'],
  // 付款条件 PaymentTerm
  paymentTerms: ['paymentTermId', 'paymentTermCode', 'paymentTermName', 'status'],
  // 会计科目 Account
  accounts: ['accountId', 'accountCode', 'accountName', 'accountType', 'status'],
  accountOptions: ['accountId', 'accountCode', 'accountName', 'accountType', 'status'],
  // 物料分类 MaterialCategory
  categories: ['categoryId', 'categoryCode', 'categoryName', 'parentId', 'status'],
  categoryOptions: ['categoryId', 'categoryCode', 'categoryName', 'parentId', 'status'],
  // 银行账户 BankAccount
  bankAccounts: ['accountId', 'accountNo', 'accountName', 'bankName', 'status']
}

/** 已知的"错误变体"属性名 → 正确字段（弱规则；这些名字一旦出现在下拉取值处，几乎必然是错配） */
const KNOWN_WRONG_ALIASES = {
  departmentId: 'deptId',
  departmentName: 'deptName',
  departmentCode: 'deptCode',
  warehouseId: 'stockId',
  warehouseName: 'stockName',
  warehouseCode: 'stockCode',
  locationId: 'binId',
  locationName: 'binName',
  locationCode: 'binCode',
  settleId: 'settleModeId',
  settleName: 'settleModeName',
  settleCode: 'settleModeCode',
  unitOfMeasureId: 'unitId',
  unitOfMeasureName: 'unitName',
  materialCategoryId: 'categoryId',
  materialCategoryName: 'categoryName'
}

/** 白名单之外允许的"前端构造"属性（不是后端字段，但在下拉里出现属正常） */
const FRONTEND_ONLY_PROPS = new Set([
  'value', 'label', 'key', 'text', 'raw', 'children', 'disabled', 'options', 'props',
  '_filteredMaterials', 'type', 'name', 'desc', 'id', 'code', 'checked', 'selected'
])

function walk(dir, out = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) walk(full, out)
    else if (/\.(vue|js)$/.test(entry.name)) out.push(full)
  }
  return out
}

const problems = []

/**
 * 已知豁免：命中文件时其 ERROR 降级为 WARN（不计入失败退出码），并注明原因。
 * 用于「本地库无菜单入口的遗留页」这类非当前风险项，避免体检常态红灯而被人忽略。
 */
const IGNORE_PATTERNS = [
  {
    match: /views\/inventory\/(WarehouseList|LocationList)\.vue/,
    reason: '遗留孤儿页：本地 sys_menu 无该菜单入口（25_cloud_init_data.sql 有定义但未生效），待下线或整页修复'
  }
]

function checkFile(file) {
  const text = fs.readFileSync(file, 'utf8')
  const rel = path.relative(path.resolve(__dirname, '..'), file).replace(/\\/g, '/')
  const ignored = IGNORE_PATTERNS.find(p => p.match.test(rel))

  // 规则①：el-option 的 v-for 变量属性核对
  const optionRe = /<el-option\b([^>]*?)(\/?)>/gs
  let m
  while ((m = optionRe.exec(text)) !== null) {
    const attrs = m[1]
    const varMatch = /v-for\s*=\s*"\s*(\w+)\s+in\s+([\w.?]+)\s*"/.exec(attrs)
    if (!varMatch) continue
    const [, itemVar, listVarName] = varMatch
    const listVar = listVarName.split('?')[0].trim()
    const allowed = DROPDOWN_ENTITY_FIELDS[listVar]
    if (!allowed) continue
    const line = text.slice(0, m.index).split('\n').length
    // 标签内所有 itemVar.attr 引用（含模板字符串 ${x.y}）
    const refRe = new RegExp(`\\b${itemVar}\\.([A-Za-z_$][\\w$]*)`, 'g')
    let r
    const seen = new Set()
    while ((r = refRe.exec(attrs)) !== null) {
      const attr = r[1]
      if (seen.has(attr)) continue
      seen.add(attr)
      if (FRONTEND_ONLY_PROPS.has(attr)) continue
      if (allowed.includes(attr)) continue
      const fix = KNOWN_WRONG_ALIASES[attr]
      // 有意兜底：同一标签里已写正确字段（如 w.stockId || w.warehouseId），属防御性写法，不算错
      if (fix && new RegExp(`\\b${itemVar}\\.${fix}\\b`).test(attrs)) continue
      problems.push({
        level: ignored ? 'WARN' : 'ERROR',
        file: rel,
        line,
        msg: `下拉「${listVar}」引用了实体不存在的属性 ${itemVar}.${attr}` + (fix ? `（应为 ${itemVar}.${fix}）` : '')
          + (ignored ? `｜已豁免：${ignored.reason}` : '')
      })
    }
  }

  // 规则②：已知错误变体出现在"下拉取值"处（:key/:label/:value 或 .map 映射）
  const lines = text.split('\n')
  lines.forEach((ln, i) => {
    const isDropdownBinding = /:(?:key|label|value)\s*=|\.map\s*\(/.test(ln)
    if (!isDropdownBinding) return
    for (const [wrong, right] of Object.entries(KNOWN_WRONG_ALIASES)) {
      const re = new RegExp(`(?:^|[^\\w$.])([A-Za-z_$][\\w$]*)\\.${wrong}\\b`)
      const hit = re.exec(ln)
      if (!hit) continue
      // 排除表单/明细行的字段容器：form.warehouseId、row.stockId 等是前端 state 字段名，
      // 由各自的单据/明细实体决定，与"下拉数据源对象"无关（如 inventory 各页 form.warehouseId 保存时映射为 stockId）
      if (/^(form|formData|row|item|it|data|workOrder|order)$/.test(hit[1])) continue
      // 同行已写正确字段 = 兜底写法（如 w.stockId || w.warehouseId），属防御性写法不算错
      if (new RegExp(`\\.${right}\\b`).test(ln)) continue
      {
        // 已由规则①报过的同一行不重复报
        const dup = problems.some(p => p.file === rel && p.line === i + 1 && p.msg.includes(wrong))
        if (!dup) {
          problems.push({
            level: 'WARN',
            file: rel,
            line: i + 1,
            msg: `下拉取值处出现疑似错配属性 .${wrong}（实体字段应为 .${right}）`
          })
        }
      }
    }
  })
}

const files = walk(SRC_ROOT)
files.forEach(checkFile)

const errors = problems.filter(p => p.level === 'ERROR')

if (problems.length === 0) {
  console.log(`[下拉字段体检] PASS —— 扫描 ${files.length} 个文件，未发现字段名错配。`)
  process.exit(0)
}

console.log(`[下拉字段体检] 共 ${problems.length} 处（扫描 ${files.length} 个文件）：ERROR ${errors.length} / WARN ${problems.length - errors.length}\n`)
for (const p of problems) {
  console.log(`  ${p.level}  ${p.file}:${p.line}\n        ${p.msg}`)
}
console.log('\n提示：ERROR = 下拉取值必然为 undefined（选项空白/选中无值），必须修；WARN = 需人工确认（含已豁免项）。')
process.exit(errors.length > 0 ? 1 : 0)
