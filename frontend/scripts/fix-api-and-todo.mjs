// fix-api-and-todo.mjs — 综合脚本：
// Part A: 扫描所有 API 文件，补齐缺失的 reject/reverseApprove/closeOrder/reopen/void/unvoid/delete 函数
// Part B: 扫描所有 Edit.vue，将 TODO_* 占位替换为真实 API 调用
import { globSync } from 'glob'
import fs from 'fs'
import path from 'path'

const ROOT = process.cwd()

// ============ Part A: 补齐 API 文件 ============

// 每个API文件需要补齐的函数模板（统一用 request.post 风格）
const API_FNS = [
  { key: 'reject',         suffix: 'reject',         pattern: /export\s+function\s+reject(\w+)\s*\(/,     url: '/reject',         method: 'post', hasReason: true,  exportName: 'reject' },
  { key: 'reverseApprove', suffix: 'reverseApprove', pattern: /export\s+function\s+reverseApprove(\w+)\s*\(/, url: '/reverse-approve', method: 'post', hasReason: false, exportName: 'reverseApprove' },
  { key: 'void',           suffix: 'void',           pattern: /export\s+function\s+void(\w+)\s*\(/,       url: '/void',           method: 'post', hasReason: true,  exportName: 'void' },
  { key: 'unvoid',         suffix: 'unvoid',         pattern: /export\s+function\s+unvoid(\w+)\s*\(/,     url: '/unvoid',         method: 'post', hasReason: false, exportName: 'unvoid' },
  { key: 'closeOrder',     suffix: 'closeOrder',     pattern: /export\s+function\s+closeOrder(\w+)\s*\(/,  url: '/close-order',    method: 'post', hasReason: false, exportName: 'closeOrder' },
  { key: 'reopen',         suffix: 'reopen',         pattern: /export\s+function\s+reopen(\w+)\s*\(/,     url: '/reopen',         method: 'post', hasReason: false, exportName: 'reopen' },
  { key: 'delete',         suffix: 'delete',         pattern: /export\s+function\s+delete(\w+)\s*\(/,     url: '',                method: 'delete', hasReason: false, exportName: 'delete' },
]

const apiFiles = globSync('src/api/**/*.js', { cwd: ROOT, windowsPathsNoEscape: true })
  .filter(f => !f.includes('basedata') && !f.includes('system') && !f.includes('dashboard') && !f.includes('crm'))

// 手动映射表：文件名 → 命名前缀（用于补齐函数名）
// 从已有 export function save{Prefix} 或 export function {prefix}List 等推断
function inferPrefix(content) {
  // 尝试从 save{Prefix} 推断
  const m1 = content.match(/export\s+function\s+save(\w+)\s*\(/)
  if (m1) return m1[1]
  // 尝试从 create{Prefix} 推断
  const m2 = content.match(/export\s+function\s+create(\w+)\s*\(/)
  if (m2) return m2[2] || m2[1]
  // 尝试从 list{Prefix} 推断
  const m3 = content.match(/export\s+function\s+list(\w+)\s*\(/)
  if (m3) return m3[1]
  return null
}

let apiFixed = 0
const apiMap = {} // 记录 每个API文件 → { prefix, base, existingFns }

for (const f of apiFiles) {
  let content = fs.readFileSync(f, 'utf8')
  const baseMatch = content.match(/const\s+BASE\s*=\s*['"]([^'"]+)['"]/)
  if (!baseMatch) continue
  const BASE = baseMatch[1]
  
  const prefix = inferPrefix(content)
  if (!prefix) { console.warn(`[SKIP-API] ${f}: 无法推断命名前缀`); continue }
  
  // 检测已有函数
  const existingFns = new Set()
  const fnRe = /export\s+function\s+(\w+)\s*\(/g
  let m
  while ((m = fnRe.exec(content)) !== null) existingFns.add(m[1])

  // 检测调用风格
  const useRequestStyle = /request\(\s*\{\s*url:/.test(content) // request({ url: ... }) 风格
  const useDirectStyle = /request\.(post|get|put|delete)\s*\(/.test(content) // request.post(...) 风格

  const toAdd = []
  for (const fn of API_FNS) {
    // 检查是否已有（以任何名字命名的同类函数）
    const existingName = [...existingFns].find(n => n.toLowerCase().includes(fn.suffix.toLowerCase()) && !n.includes('cancel'))
    if (existingName) {
      apiMap[f] = apiMap[f] || { prefix, base: BASE, existingFns: {} }
      apiMap[f].existingFns[fn.key] = existingName
      continue
    }
    // 特殊：cancel 替代 void
    const cancelName = [...existingFns].find(n => n.toLowerCase().includes('cancel'))
    if (cancelName && fn.key === 'void') {
      apiMap[f] = apiMap[f] || { prefix, base: BASE, existingFns: {} }
      apiMap[f].existingFns[fn.key] = cancelName
      continue
    }
    // 特殊：close 已有但名字不是 closeOrder
    const closeName = [...existingFns].find(n => n.toLowerCase().startsWith('close') && !n.includes('closeOrder'))
    if (closeName && fn.key === 'closeOrder') {
      apiMap[f] = apiMap[f] || { prefix, base: BASE, existingFns: {} }
      apiMap[f].existingFns[fn.key] = closeName
      continue
    }

    // 需要补齐
    const fnName = fn.suffix + prefix  // 如 rejectSaleDelivery
    let fnBody
    if (fn.method === 'delete') {
      if (useRequestStyle) {
        fnBody = `export function ${fnName}(id) {\n  return request({ url: \`\${BASE}/\${id}\`, method: 'delete' })\n}`
      } else {
        fnBody = `export function ${fnName}(id) { return request.delete(\`\${BASE}/\${id}\`) }`
      }
    } else if (fn.hasReason) {
      if (useRequestStyle) {
        fnBody = `export function ${fnName}(id, reason) {\n  return request({ url: \`\${BASE}/\${id}${fn.url}\`, method: 'post', data: { reason } })\n}`
      } else {
        fnBody = `export function ${fnName}(id, reason) { return request.post(\`\${BASE}/\${id}${fn.url}\`, { reason }) }`
      }
    } else {
      if (useRequestStyle) {
        fnBody = `export function ${fnName}(id) {\n  return request({ url: \`\${BASE}/\${id}${fn.url}\`, method: 'post' })\n}`
      } else {
        fnBody = `export function ${fnName}(id) { return request.post(\`\${BASE}/\${id}${fn.url}\`) }`
      }
    }
    toAdd.push(fnBody)
    apiMap[f] = apiMap[f] || { prefix, base: BASE, existingFns: {} }
    apiMap[f].existingFns[fn.key] = fnName
  }

  if (toAdd.length > 0) {
    content = content.trimEnd() + '\n\n// ===== 以下为状态流转统一接口（自动补齐） =====\n' + toAdd.join('\n') + '\n'
    fs.writeFileSync(f, content, 'utf8')
    console.log(`[API-FIX] ${f}: 补齐 ${toAdd.length} 个函数 (${toAdd.map(t => t.match(/function\s+(\w+)/)[1]).join(', ')})`)
    apiFixed++
  } else {
    console.log(`[API-OK] ${f}: 全部函数已存在`)
  }
}
console.log(`\nPart A 完成：${apiFixed} 个 API 文件被补齐\n`)

// ============ Part B: 替换 Edit.vue 中的 TODO_* 占位 ============

// 建立 模块→API函数 映射表
// 从 apiMap 中提取
const moduleApiMap = {}
for (const [apiFile, info] of Object.entries(apiMap)) {
  // 从 API 文件名推断模块关键字
  const baseName = path.basename(apiFile, '.js')
  moduleApiMap[baseName] = info
}

// 特殊映射：某些 Edit.vue 引用的 API 文件名与实际不同
const editApiAlias = {
  'PurchaseOrderEdit': 'purchaseOrder',  // 需要特殊处理
  'PurchaseInboundEdit': 'inbound',
  'PurchaseReturnEdit': 'return',
  'PurchaseInvoiceEdit': 'invoice',
  'PurchaseRequestEdit': 'request',
  'PurchaseContractEdit': 'purchaseContract',
  'SubcontractOrderEdit': 'subcontract',
  'SubcontractPickEdit': 'subcontract',
  'SubcontractInboundEdit': 'subcontract',
  'SubcontractReturnEdit': 'subcontract',
  'SubcontractSupplementEdit': 'subcontract',
  'SalePriceStrategyEdit': 'priceStrategy',
  'PurchasePriceStrategyEdit': 'priceStrategy',
}

const editFiles = globSync('src/views/**/*Edit.vue', { cwd: ROOT, windowsPathsNoEscape: true })
  .filter(f => {
    const unix = f.replace(/\\/g, '/')
    return !unix.includes('/basedata/') && !unix.includes('/SaleOrderEdit')
  })

let editFixed = 0
for (const f of editFiles) {
  let content = fs.readFileSync(f, 'utf8')
  if (!content.includes('TODO_saveBill')) { continue }

  // 从 import 语句中找出 API 文件路径
  const importMatch = content.match(/import\s*\{[^}]+\}\s*from\s*['"]@\/api\/([^'"]+)['"]/)
  if (!importMatch) { console.warn(`[SKIP-EDIT] ${f}: 无 API import`); continue }
  const apiPath = importMatch[1] // 如 'sale/saleDelivery'
  const apiBaseName = path.basename(apiPath) // 如 'saleDelivery'

  const info = moduleApiMap[apiBaseName]
  if (!info) {
    console.warn(`[SKIP-EDIT] ${f}: 未找到 ${apiBaseName} 的 API 映射`)
    continue
  }

  const { prefix, existingFns } = info

  // 替换 TODO_* 占位
  const replacements = {
    'TODO_saveBill': existingFns.save || `save${prefix}`,
    'TODO_updateBill': existingFns.update || `update${prefix}`,
    'TODO_submitBill': existingFns.submit || `submit${prefix}`,
    'TODO_approveBill': existingFns.approve || `approve${prefix}`,
    'TODO_rejectBill': existingFns.reject || `reject${prefix}`,
    'TODO_reverseApproveBill': existingFns.reverseApprove || `reverseApprove${prefix}`,
    'TODO_closeBill': existingFns.closeOrder || `closeOrder${prefix}`,
    'TODO_reopenBill': existingFns.reopen || `reopen${prefix}`,
    'TODO_voidBill': existingFns.void || `void${prefix}`,
    'TODO_unvoidBill': existingFns.unvoid || `unvoid${prefix}`,
    'TODO_deleteBill': existingFns.delete || `delete${prefix}`,
  }

  for (const [todo, realFn] of Object.entries(replacements)) {
    // 替换 TODO_saveBill = async (form) => { throw ... } 为 const TODO_saveBill = (form) => saveXxx(form)
    // 但更简单：直接替换调用点 TODO_saveBill(form) → saveXxx(form)
    const re = new RegExp(`\\b${todo}\\b`, 'g')
    content = content.replace(re, realFn)
  }

  // 确保 import 中包含这些新函数
  const importBlock = content.match(/import\s*\{([^}]+)\}\s*from\s*['"]@\/api\/([^'"]+)['"]/)
  if (importBlock) {
    const importedNames = importBlock[1].split(',').map(s => s.trim()).filter(Boolean)
    const needAdd = Object.values(replacements).filter(fn => !importedNames.includes(fn))
    if (needAdd.length > 0) {
      const newImportBlock = `import {\n  ${[...importedNames, ...needAdd].join(',\n  ')}\n} from '@/api/${apiPath}'`
      content = content.replace(importBlock[0], newImportBlock)
    }
  }

  // 删除 TODO_* 函数定义（它们现在只是声明，已不再被调用）
  content = content.replace(/const\s+TODO_\w+\s*=\s*async\s*\([^)]*\)\s*=>\s*\{[^}]*\}/g, '')
  // 清理多余空行
  content = content.replace(/\n{3,}/g, '\n\n')

  fs.writeFileSync(f, content, 'utf8')
  console.log(`[EDIT-FIX] ${f}: TODO_* 已替换为真实 API 调用 (${prefix})`)
  editFixed++
}
console.log(`\nPart B 完成：${editFixed} 个 Edit.vue 的 TODO_* 占位已替换\n`)
