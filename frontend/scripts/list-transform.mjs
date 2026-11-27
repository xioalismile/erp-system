// list-transform.mjs — 批量改造单据 List.vue 页面：替换操作列为 BillRowActions 组件
// 用法：node scripts/list-transform.mjs [glob_path]
import { globSync } from 'glob'
import fs from 'fs'

const ROOT = process.cwd()
const pattern = process.argv[2] || 'src/views/**/*List.vue'
const SKIP = [
  '/basedata/', '/approval/', '/dashboard/', '/system/', '/crm/', '/acquisition/', '/logistics/'
]

const files = globSync(pattern, { cwd: ROOT, windowsPathsNoEscape: true })
  .filter(f => {
    const unix = f.replace(/\\/g, '/')
    for (const s of SKIP) if (unix.includes(s)) return false
    return true
  })

console.log(`待扫描 List 页面: ${files.length} 个`)

function injectImports(content) {
  if (content.includes("BillRowActions")) return content
  const lines = content.split('\n')
  const scriptStartIdx = lines.findIndex(l => /^\s*<script\s+setup/.test(l))
  if (scriptStartIdx < 0) return content
  let lastImportIdx = scriptStartIdx
  for (let i = scriptStartIdx + 1; i < lines.length; i++) {
    if (/^\s*import\s+/.test(lines[i])) lastImportIdx = i
  }
  const insertIdx = lastImportIdx + 1
  lines.splice(insertIdx, 0, '',
    "import BillRowActions from '@/components/bill/BillRowActions.vue'"
  )
  return lines.join('\n')
}

/** 定位"操作"列 el-table-column，将其 #default 模板内容替换为 BillRowActions */
function patchActionColumn(content, file) {
  // 策略：匹配 <el-table-column ... label="操作" ...> 到其闭合 </el-table-column> 之间的内容
  const opColRegex = /<el-table-column([^>]*?)\slabel=['"]操作['"]([^>]*)>([\s\S]*?)<\/el-table-column>/g
  let found = false
  content = content.replace(opColRegex, (match, before1, before2, inner) => {
    // 如果 inner 里已经有 BillRowActions 就跳过
    if (inner.includes('BillRowActions')) return match
    found = true
    // 提取列其他属性（保留 width / fixed / align）
    const attrs = (before1 + ' ' + before2).replace(/^\s+/, '').replace(/\s+$/, '')
    const newInner = `
      <template #default="scope">
        <BillRowActions
          :status="scope.row.status"
          :close-status="scope.row.closeStatus"
          :row-id="scope.row.id || scope.row.orderId || scope.row.deliveryId || scope.row.inboundId || scope.row.invoiceId || scope.row.contractId || scope.row.voucherId || scope.row.requisitionId || scope.row.stockCheckId || scope.row.transferId || scope.row.bomId || scope.row.sampleId || scope.row.freeSampleId || scope.row.workOrderId || scope.row.billId"
          @view="handleView(scope.row)"
          @edit="handleEdit(scope.row)"
          @delete="handleDelete(scope.row)"
        />
      </template>`
    return `<el-table-column ${attrs} label="操作">${newInner}\n    </el-table-column>`
  })
  if (found) console.log('   [patch] 操作列已替换为 BillRowActions')
  else console.warn('   [WARN] 未找到「操作」列（label=操作）')
  return content
}

let ok = 0, fail = 0, skip = 0
for (const f of files) {
  console.log(`\n== 处理: ${f} ==`)
  try {
    let content = fs.readFileSync(f, 'utf8')
    // 非单据列表页：无 el-table 且无 status 字段则跳过
    if (!content.includes('<el-table')) { console.log('   [SKIP] 非列表页（无el-table）'); skip++; continue }
    if (!/status.*待审核|草稿.*status|label=['"]状态['"]|form\.status|scope\.row\.status/.test(content)) {
      console.log('   [SKIP] 非单据列表（无状态字段）'); skip++; continue
    }
    content = injectImports(content)
    content = patchActionColumn(content)
    fs.writeFileSync(f, content, 'utf8')
    ok++
    console.log('   [OK] 写入完毕')
  } catch (e) {
    fail++
    console.error('   [FAIL]', e.message)
  }
}
console.log(`\n==== 批量处理完成：${ok} 成功 / ${skip} 跳过 / ${fail} 失败 / ${files.length} 总文件 ====`)
