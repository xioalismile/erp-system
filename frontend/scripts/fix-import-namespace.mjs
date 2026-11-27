// fix-import-namespace.mjs — 为避免 canEdit/canSave/... 与页面本地常量/函数重名冲突
// 将 import { canEdit, canSave, ...canXxx } from '@/utils/bill-status' 全部改为命名空间导入：
//   import * as BillStatus from '@/utils/bill-status'
// 同时把代码中用到的 canEdit( → BillStatus.canEdit(，canSave( → BillStatus.canSave( 等
import { globSync } from 'glob'
import fs from 'fs'

const ROOT = process.cwd()
const files = globSync('src/views/**/*Edit.vue', { cwd: ROOT, windowsPathsNoEscape: true })

const OLD_IMPORT_RE = /import\s*\{\s*canEdit[^}]*\}\s*from\s*['"]@\/utils\/bill-status['"]\s*;?\s*\n?/g
const NEW_IMPORT = "import * as BillStatus from '@/utils/bill-status'\n"

// 需要替换的函数调用
const FN_NAMES = ['canEdit', 'canSave', 'canSubmit', 'canApprove', 'canReject',
  'canReverseApprove', 'canClose', 'canReopen', 'canVoid', 'canUnvoid', 'canDelete',
  'isFinalized', 'isDraft', 'isPending', 'isApproved', 'isRejected', 'isClosed', 'isVoided',
  'normalizeStatus', 'isSameStatus', 'normalizeCloseStatus']

let fixed = 0
for (const f of files) {
  let content = fs.readFileSync(f, 'utf8')
  if (!OLD_IMPORT_RE.test(content)) continue
  // 1) 替换 import 语句
  const newContent = content.replace(OLD_IMPORT_RE, NEW_IMPORT)
  if (newContent === content) continue
  content = newContent

  // 2) 替换函数调用：把 FN_NAMES 中的函数名的调用： Xxx( → BillStatus.Xxx(
  // 但避免误伤字符串字面量内部 → 用正则：不替换 "'xxx(" 或 `"xxx(" 中的 xxx
  for (const fn of FN_NAMES) {
    // 用 \b 边界：前面是 非 word 字符（如空格、逗号、括号、=、!、?:），然后是 fn + (
    const re = new RegExp(`([^A-Za-z0-9_$'"\\.])${fn}\\s*\\(`, 'g')
    content = content.replace(re, (m, prefix) => `${prefix}BillStatus.${fn}(`)
  }
  fs.writeFileSync(f, content, 'utf8')
  fixed++
  console.log(`[FIX] ${f}`)
}
console.log(`\n共将 ${fixed} 个文件改为 BillStatus 命名空间导入（消除与本地 canEdit/canSave 同名冲突）`)
