// fix-nested-import.mjs — 修复 "多行 import { ... } from" 中间被注入了嵌套 import 的问题
// 症状：
// import {
//   import { canEdit, ... } from '@/utils/bill-status'   <-- 错误嵌套在另一个import块中
//   import BillActions from '...'                           <-- 同上
//   SaveSaleOrder,
// } from '@/api/...'
import { globSync } from 'glob'
import fs from 'fs'

const ROOT = process.cwd()
const files = globSync('src/views/**/*Edit.vue', { cwd: ROOT, windowsPathsNoEscape: true })

const BILl_STATUS_IMPORT = "import { canEdit, canSave, canSubmit, canApprove, canReject, canReverseApprove, canClose, canReopen, canVoid, canUnvoid, canDelete } from '@/utils/bill-status'"
const BILL_ACTIONS_IMPORT = "import BillActions from '@/components/bill/BillActions.vue'"

let fixed = 0
for (const f of files) {
  const lines = fs.readFileSync(f, 'utf8').split('\n')
  // 第一遍：找出 "import { ... } from ..." 跨多行块的起始和结束行
  const scriptStartIdx = lines.findIndex(l => /^\s*<script\s+setup/.test(l))
  if (scriptStartIdx < 0) continue
  const scriptEndIdx = lines.indexOf('</script>', scriptStartIdx)

  // 找所有 import 块（包括单行和多行）的最后一行
  let lastImportEndLine = -1
  let inMultiLineImport = false
  for (let i = scriptStartIdx + 1; i < (scriptEndIdx > 0 ? scriptEndIdx : lines.length); i++) {
    const line = lines[i]
    if (inMultiLineImport) {
      if (/}\s*from\s*['"]/.test(line) || /^\s*}/.test(line)) { // 多行结束
        lastImportEndLine = i
        inMultiLineImport = false
      }
    } else {
      if (/^\s*import\s+\{[^{}]*$/.test(line)) {
        // 多行import开始（这一行只有 import {，后面没有闭合的 }）
        inMultiLineImport = true
      } else if (/^\s*import\s+/.test(line)) {
        lastImportEndLine = i
      }
    }
  }
  if (lastImportEndLine < 0) continue

  // 第二遍：移除在 多行import块内部（between 多行 start 与 end 之间）出现的：
  //   BILl_STATUS_IMPORT 行 或 BILL_ACTIONS_IMPORT 行
  let removed = 0
  inMultiLineImport = false
  let multiStart = -1
  for (let i = scriptStartIdx + 1; i < (scriptEndIdx > 0 ? scriptEndIdx : lines.length); i++) {
    const line = lines[i]
    if (inMultiLineImport) {
      if (line.includes(BILl_STATUS_IMPORT) || line.includes(BILL_ACTIONS_IMPORT)) {
        lines.splice(i, 1); i--; removed++  // 删掉这行
      } else if (/}\s*from\s*['"]/.test(line) || /^\s*}/.test(line)) {
        inMultiLineImport = false
      }
    } else {
      if (/^\s*import\s+\{[^{}]*$/.test(line)) {
        inMultiLineImport = true
        multiStart = i
      }
    }
  }

  if (removed > 0) {
    // 然后在 lastImportEndLine（可能在多行import结束之后）后面追加两行正确的 import
    let insertAfter = lastImportEndLine
    // 跳过可能已在后面相同的 import（避免重复，去重）
    while (insertAfter > 0 && 
      (lines[insertAfter].includes(BILl_STATUS_IMPORT) || lines[insertAfter].includes(BILL_ACTIONS_IMPORT))) {
      insertAfter--
    }
    const toAdd = []
    const joined = lines.join('\n')
    if (!joined.includes("from '@/utils/bill-status'")) toAdd.push(BILl_STATUS_IMPORT)
    if (!joined.includes("BillActions from")) toAdd.push(BILL_ACTIONS_IMPORT)
    lines.splice(insertAfter + 1, 0, ...toAdd)
    fs.writeFileSync(f, lines.join('\n'), 'utf8')
    console.log(`[FIX] ${f}: 移除 ${removed} 处嵌套import，在尾部追加 ${toAdd.length} 行正确import`)
    fixed++
  }
}
console.log(`\n共修复 ${fixed} 个文件的 import 嵌套问题`)
