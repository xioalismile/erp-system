// fix-dup-function.mjs — 修复批量注入 handler 导致原页面已有 onSave/onSubmit 等函数重复定义的问题
// 策略：找到 "// ======== 状态流转：通用占位 handler ..." 到 "// ======== 状态流转 END ========" 之间的内容
// 1) 列出块外 script 中已定义的函数名（function onSave(...)、const onSave = ...）
// 2) 对于块中重复的函数声明：要么整段跳过（如果函数已存在），要么从注入块中删除重复函数的定义
// 简化版：扫描 block，若 block 外已经有同名的 function XXX 声明，则把 block 中的该 function 用注释包起来（或删除）

import { globSync } from 'glob'
import fs from 'fs'

const ROOT = process.cwd()
const files = globSync('src/views/**/*Edit.vue', { cwd: ROOT, windowsPathsNoEscape: true })

const BLOCK_START = '// ======== 状态流转：通用占位 handler'
const BLOCK_END   = '// ======== 状态流转 END ========'

// 提取 script 代码部分（<script setup>...</script>）
function extractScript(content) {
  const sm = content.match(/<script\s+setup[^>]*>([\s\S]*?)<\/script>/)
  return sm ? sm[1] : ''
}

function listFunctionNames(code) {
  const names = new Set()
  // function onSave(
  code.replace(/function\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(/g, (m, name) => { names.add(name); return m })
  // const onSave = ( 或 const onSave = async (
  code.replace(/(?:const|let|var)\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(?:async\s*)?[\(=]/g, (m, name) => { names.add(name); return m })
  return names
}

let fixed = 0
for (const f of files) {
  let content = fs.readFileSync(f, 'utf8')
  if (!content.includes(BLOCK_START)) continue

  const scriptCode = extractScript(content)
  const outOfBlockCode = scriptCode.split(BLOCK_START)[0] + scriptCode.split(BLOCK_END)[1]
  const outsideFn = listFunctionNames(outOfBlockCode)

  const startIdx = content.indexOf(BLOCK_START)
  const endIdx   = content.indexOf(BLOCK_END, startIdx) + BLOCK_END.length
  const block    = content.slice(startIdx, endIdx)

  // 找出块内声明的函数名，与outsideFn比对
  const insideMatches = [...block.matchAll(/^\s*(?:async\s+)?function\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(/gm)]
  const dupFns = insideMatches.map(m => m[1]).filter(name => outsideFn.has(name))
  if (dupFns.length === 0) continue

  console.log(`[FIX] ${f}: 去重函数 ${dupFns.join(', ')}`)

  // 在 block 中，对每个 dupFn 名：删除从 "^async function dupFn(" 或 "^function dupFn(" 开始，直到下一个 "^\s*(?:async\s+)?function\s+" 或 "^  // =======" 之间的所有内容
  let newBlock = block
  for (const name of dupFns) {
    const re = new RegExp(`^\\s*(?:async\\s+)?function\\s+${name}\\s*\\([\\s\\S]*?(?=\\n\\s*(?:async\\s+)?function\\s+[A-Za-z_][A-Za-z0-9_]*\\s*\\(|\\n  \\/\\/ ======|\\n\\s*const\\s+${name}\\s*=|\\Z)`, 'gm')
    newBlock = newBlock.replace(re, '')
  }
  // 同时：如果 outside 里已经有 billId / rejectDialog / voidDialog / rejectReason / voidReason，也去除 block 内的重复声明
  for (const v of ['billId', 'rejectDialog', 'rejectReason', 'voidDialog', 'voidReason',
                   'TODO_saveBill', 'TODO_updateBill', 'TODO_submitBill', 'TODO_approveBill',
                   'TODO_rejectBill', 'TODO_reverseApproveBill', 'TODO_closeBill',
                   'TODO_reopenBill', 'TODO_voidBill', 'TODO_unvoidBill', 'TODO_deleteBill']) {
    if (outsideFn.has(v)) {
      const re = new RegExp(`^\\s*const\\s+${v}\\s*=.*$`, 'gm')
      newBlock = newBlock.replace(re, '')
    }
  }
  // 清理空行
  newBlock = newBlock.replace(/\n{3,}/g, '\n\n')

  content = content.slice(0, startIdx) + newBlock + content.slice(endIdx)
  fs.writeFileSync(f, content, 'utf8')
  fixed++
}
console.log(`\n共处理 ${fixed} 个文件的函数重复声明问题`)
