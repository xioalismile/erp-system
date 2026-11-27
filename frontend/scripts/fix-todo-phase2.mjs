// fix-todo-phase2.mjs — 改进版：正确匹配 Edit.vue 的 API import，替换 TODO_* 占位
import { globSync } from 'glob'
import fs from 'fs'
import path from 'path'

const ROOT = process.cwd()

// 从 apiMap 重建映射（与上一致脚本同逻辑，但只在内存中）
const apiFiles = globSync('src/api/**/*.js', { cwd: ROOT, windowsPathsNoEscape: true })
  .filter(f => !f.includes('basedata') && !f.includes('system') && !f.includes('dashboard') && !f.includes('crm'))

function inferPrefix(content) {
  const m1 = content.match(/export\s+function\s+save(\w+)\s*\(/)
  if (m1) return m1[1]
  const m2 = content.match(/export\s+function\s+create(\w+)\s*\(/)
  if (m2) return m2[1]
  const m3 = content.match(/export\s+function\s+list(\w+)\s*\(/)
  if (m3) return m3[1]
  return null
}

const moduleApiMap = {}
for (const f of apiFiles) {
  const content = fs.readFileSync(f, 'utf8')
  const baseMatch = content.match(/const\s+BASE\s*=\s*['"]([^'"]+)['"]/)
  if (!baseMatch) continue
  const prefix = inferPrefix(content)
  if (!prefix) continue
  const baseName = path.basename(f, '.js')
  
  const existingFns = new Set()
  const fnRe = /export\s+function\s+(\w+)\s*\(/g
  let m
  while ((m = fnRe.exec(content)) !== null) existingFns.add(m[1])
  
  moduleApiMap[baseName] = { prefix, base: baseMatch[1], existingFns: [...existingFns] }
}

// 手动映射：Edit.vue 文件名关键字 → API 文件 basename
const editToApiMap = {
  'PurchaseOrder': 'purchaseOrder',
  'PurchaseInbound': 'inbound',
  'PurchaseReturn': 'return',
  'PurchaseInvoice': 'invoice',
  'PurchaseRequest': 'request',
  'PurchaseContract': 'purchaseContract',
  'SaleDelivery': 'saleDelivery',
  'SaleReturn': 'saleReturn',
  'SaleInvoice': 'saleInvoice',
  'SaleQuote': 'saleQuote',
  'SaleContract': 'saleContract',
  'SubcontractOrder': 'subcontract',
  'SubcontractPick': 'subcontract',
  'SubcontractInbound': 'subcontract',
  'SubcontractReturn': 'subcontract',
  'SubcontractSupplement': 'subcontract',
  'WorkOrder': 'workOrder',
  'ProductionReceipt': 'productionReceipt',
  'ProductionReturn': 'productionReceipt',  // 复用
  'ProductionSupplement': 'productionReceipt',
  'ProduceAssemble': 'produceAssemble',
  'ProduceDisassemble': 'produceDisassemble',
  'ProcessTransfer': 'processTransfer',
  'OperationReport': 'operationReport',
  'MrpPlan': 'mrpPlan',
  'MaterialRequisition': 'materialRequisition',
  'OtherIssue': 'otherIssue',
  'OtherReceipt': 'otherReceipt',
  'StockCheck': 'stockCheck',
  'StockTransfer': 'stockTransfer',
  'SalePreReceipt': 'salePreReceipt',
  'PurchasePrePayment': 'purchasePrePayment',
  'PaymentVoucher': 'paymentVoucher',
  'PaymentReceipt': 'paymentReceipt',
  'OperationExpense': 'operationExpense',
  'CostCalculation': 'costCalculation',
  'Sample': 'sample',
  'FreeSample': 'freeSample',
  'SalePriceStrategy': 'priceStrategy',
  'PurchasePriceStrategy': 'priceStrategy',
}

const editFiles = globSync('src/views/**/*Edit.vue', { cwd: ROOT, windowsPathsNoEscape: true })
  .filter(f => {
    const unix = f.replace(/\\/g, '/')
    return !unix.includes('/basedata/') && !unix.includes('/SaleOrderEdit.vue')
  })

let editFixed = 0
for (const f of editFiles) {
  let content = fs.readFileSync(f, 'utf8')
  if (!content.includes('TODO_saveBill') && !content.includes('TODO_updateBill')) continue

  // 找文件名关键字
  const baseName = path.basename(f, '.vue')
  let apiBaseName = null
  for (const [editKey, apiKey] of Object.entries(editToApiMap)) {
    if (baseName.includes(editKey)) { apiBaseName = apiKey; break }
  }
  
  if (!apiBaseName) {
    // 从 import 语句推断
    const allImports = [...content.matchAll(/import\s*\{[^}]+\}\s*from\s*['"]@\/api\/([^'"]+)['"]/g)]
    for (const imp of allImports) {
      const impBase = path.basename(imp[1])
      if (moduleApiMap[impBase]) { apiBaseName = impBase; break }
    }
  }

  if (!apiBaseName || !moduleApiMap[apiBaseName]) {
    console.warn(`[SKIP] ${f}: 无法匹配 API (baseName=${baseName})`)
    continue
  }

  const { prefix, existingFns } = moduleApiMap[apiBaseName]

  // 构建替换映射
  const findFn = (keywords) => existingFns.find(n => {
    const lower = n.toLowerCase()
    return keywords.some(k => lower.includes(k.toLowerCase()))
  })

  const replacements = {
    'TODO_saveBill': findFn(['save']) || `save${prefix}`,
    'TODO_updateBill': findFn(['update']) || `update${prefix}`,
    'TODO_submitBill': findFn(['submit']) || `submit${prefix}`,
    'TODO_approveBill': findFn(['approve', 'audit', 'confirm']) || `approve${prefix}`,
    'TODO_rejectBill': findFn(['reject']) || `reject${prefix}`,
    'TODO_reverseApproveBill': findFn(['reverseApprove', 'revoke']) || `reverseApprove${prefix}`,
    'TODO_closeBill': findFn(['closeOrder', 'close']) || `closeOrder${prefix}`,
    'TODO_reopenBill': findFn(['reopen']) || `reopen${prefix}`,
    'TODO_voidBill': findFn(['void', 'cancel']) || `void${prefix}`,
    'TODO_unvoidBill': findFn(['unvoid']) || `unvoid${prefix}`,
    'TODO_deleteBill': findFn(['delete']) || `delete${prefix}`,
  }

  // 替换调用点
  for (const [todo, realFn] of Object.entries(replacements)) {
    const re = new RegExp(`\\b${todo}\\b`, 'g')
    content = content.replace(re, realFn)
  }

  // 删除 TODO_* 函数定义
  content = content.replace(/^\s*const\s+TODO_\w+\s*=\s*async\s*\([^)]*\)\s*=>\s*\{[^}]*\}\s*$/gm, '')
  content = content.replace(/\n{3,}/g, '\n\n')

  // 确保 import 中包含新函数
  // 找到从 @/api/ 导入的语句（可能跨多行）
  const apiImportRe = /import\s*\{([^}]+)\}\s*from\s*['"]@\/api\/([^'"]+)['"]/g
  let apiImportMatch
  while ((apiImportMatch = apiImportRe.exec(content)) !== null) {
    const impPath = apiImportMatch[2]
    const impBase = path.basename(impPath)
    if (impBase === apiBaseName || impBase === 'subcontract') {
      const importedNames = apiImportMatch[1].split(',').map(s => s.trim()).filter(Boolean)
      const needAdd = Object.values(replacements).filter(fn => !importedNames.includes(fn))
      if (needAdd.length > 0) {
        const newImport = `import {\n  ${[...importedNames, ...needAdd].join(',\n  ')}\n} from '@/api/${impPath}'`
        content = content.replace(apiImportMatch[0], newImport)
      }
      break
    }
  }

  fs.writeFileSync(f, content, 'utf8')
  console.log(`[FIX] ${f}: TODO_* → ${prefix} API`)
  editFixed++
}
console.log(`\n共替换 ${editFixed} 个 Edit.vue 的 TODO_* 占位`)
