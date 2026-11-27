// fix-api-dedup.mjs — 去除 API 文件中重复的 export function / export async function 声明
// 支持单行和多行函数定义，通过花括号匹配识别完整函数块
import { globSync } from 'glob'
import fs from 'fs'

const ROOT = process.cwd()
const files = globSync('src/api/**/*.js', { cwd: ROOT, windowsPathsNoEscape: true })

// 匹配 export function name( 或 export async function name(
const FN_RE = /^export\s+(?:async\s+)?function\s+(\w+)\s*\(/

let fixed = 0
for (const f of files) {
  const content = fs.readFileSync(f, 'utf8')
  const lines = content.split('\n')
  const seen = new Set()
  const deduped = []
  let removed = 0
  let i = 0

  while (i < lines.length) {
    const line = lines[i]
    const m = line.match(FN_RE)

    if (m) {
      const fnName = m[1]

      if (seen.has(fnName)) {
        // 重复定义，需要删除整个函数块
        // 从当前行开始，用花括号计数找到函数结束位置
        let braceCount = 0
        let started = false
        let j = i
        while (j < lines.length) {
          const cur = lines[j]
          for (const ch of cur) {
            if (ch === '{') { braceCount++; started = true }
            else if (ch === '}') { braceCount-- }
          }
          j++
          if (started && braceCount === 0) break
        }
        // 跳过从 i 到 j-1 的所有行（整个函数块）
        // 同时跳过紧跟的空行，避免留下多余空行
        while (j < lines.length && lines[j].trim() === '') j++
        removed++
        i = j
        continue
      } else {
        seen.add(fnName)
      }
    }

    deduped.push(line)
    i++
  }

  if (removed > 0) {
    let result = deduped.join('\n').replace(/\n{3,}/g, '\n\n')
    fs.writeFileSync(f, result, 'utf8')
    console.log(`[FIX] ${f}: 去除 ${removed} 个重复 export function`)
    fixed++
  }
}
console.log(`\n共修复 ${fixed} 个 API 文件`)
