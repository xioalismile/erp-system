// fix-todo-throw.mjs — 删除被错误替换的 TODO 定义（const realFnName = async ... => { throw new Error('[TODO]...') }）
import { globSync } from 'glob'
import fs from 'fs'

const ROOT = process.cwd()
const files = globSync('src/views/**/*Edit.vue', { cwd: ROOT, windowsPathsNoEscape: true })

let fixed = 0
for (const f of files) {
  let content = fs.readFileSync(f, 'utf8')
  // 匹配：const xxx = async (yyy) => { throw new Error('[TODO]...') }
  // 这些是被错误替换的 TODO 定义
  const re = /^\s*const\s+\w+\s*=\s*async\s*\([^)]*\)\s*=>\s*\{\s*throw\s+new\s+Error\('\[TODO\][^']*'\)\s*\}\s*$/gm
  const matches = content.match(re)
  if (matches && matches.length > 0) {
    content = content.replace(re, '')
    // 清理多余空行
    content = content.replace(/\n{3,}/g, '\n\n')
    fs.writeFileSync(f, content, 'utf8')
    console.log(`[FIX] ${f}: 删除 ${matches.length} 个残留 TODO throw 定义`)
    fixed++
  }
}
console.log(`\n共修复 ${fixed} 个文件`)
