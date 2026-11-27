// fix-el-input-slot.mjs — 修复：对于下面有 <template #xxx> 插槽内容的 <el-input /> 自闭合标签
// 将 /> 改为 >（保留后续 template 与 </el-input> 结构合法）
import { globSync } from 'glob'
import fs from 'fs'

const ROOT = process.cwd()
const files = globSync('src/views/**/*Edit.vue', { cwd: ROOT, windowsPathsNoEscape: true })

let fixed = 0
for (const f of files) {
  const lines = fs.readFileSync(f, 'utf8').split('\n')
  let changed = false
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    // 匹配单行的 <el-input ... /> （这行是自闭合）
    if (/<el-input\b[^<]*?\/>\s*$/.test(line)) {
      // 往后找，如果在同一个缩进层级下，出现 <template # 或 slot= ，且最后有 </el-input>，说明不能自闭合
      // 简化：直接看这行之后，下一个同级（或更深）的第一个el-input结束是否是 </el-input>，且中间有<template
      let depth = 0
      let hasTemplate = false
      let foundClosed = false
      for (let j = i + 1; j < Math.min(i + 20, lines.length); j++) {
        if (/<template\s+#/.test(lines[j])) hasTemplate = true
        // 粗略：如果遇到 </el-input> 就说明这里是闭合区
        if (/<\/el-input>/.test(lines[j])) { foundClosed = true; break }
        // 遇到另一个 <el-input> 说明已出当前输入框（无闭合标签）
        if (/<el-input\b/.test(lines[j])) break
      }
      if (hasTemplate && foundClosed) {
        // 把这行的 /> 改为 >
        lines[i] = line.replace(/\/>\s*$/, '>')
        changed = true
        console.log(`  - L${i+1}: 去掉 / ，保留 template slot 可用`)
      }
    }
  }
  if (changed) {
    fs.writeFileSync(f, lines.join('\n'), 'utf8')
    console.log(`[FIXED+slot] ${f}`)
    fixed++
  }
}
console.log(`\n修复了 ${fixed} 个文件中含template的 <el-input /> 自闭合问题`)
