// fix-api-missing-exports.mjs — 扫描所有 Edit.vue 的 import，对比 API 文件，补齐缺失的导出
// 处理 saveXxx/createXxx 命名不匹配，以及缺失的状态流转函数
import { globSync } from 'glob'
import fs from 'fs'
import path from 'path'

const ROOT = process.cwd()

// 1. 扫描所有 Edit.vue，提取 import 的 API 函数
const editFiles = globSync('src/views/**/*Edit.vue', { cwd: ROOT, windowsPathsNoEscape: true })

// apiPath -> Set(imported function names)
const apiRequirements = new Map()

for (const f of editFiles) {
  const content = fs.readFileSync(f, 'utf8')
  // 匹配 import { ... } from '@/api/...'
  const importRe = /import\s*\{([^}]+)\}\s*from\s*['"](@\/api\/[^'"]+)['"]/g
  let m
  while ((m = importRe.exec(content)) !== null) {
    const funcs = m[1].split(',').map(s => s.trim()).filter(Boolean)
    const apiPath = 'src/' + m[2].replace('@/', '') + '.js' // 相对项目根的路径
    if (!apiRequirements.has(apiPath)) {
      apiRequirements.set(apiPath, new Set())
    }
    for (const fn of funcs) {
      apiRequirements.get(apiPath).add(fn)
    }
  }
}

// 2. 对每个 API 文件，检查缺失的导出并补齐
let fixed = 0

for (const [apiRelPath, requiredFns] of apiRequirements) {
  const apiFile = path.join(ROOT, apiRelPath.replace(/\//g, path.sep))
  if (!fs.existsSync(apiFile)) continue

  let content = fs.readFileSync(apiFile, 'utf8')

  // 解析当前已导出的函数名
  const exportedFns = new Set()
  const exportRe = /export\s+(?:async\s+)?function\s+(\w+)\s*\(/g
  let em
  while ((em = exportRe.exec(content)) !== null) {
    exportedFns.add(em[1])
  }
  // 也检测 export { xxx as yyy } 形式
  const exportAsRe = /export\s*\{[^}]*\}/g
  while ((em = exportAsRe.exec(content)) !== null) {
    const names = em[0].match(/\b(\w+)\s+as\s+(\w+)\b/g)
    if (names) {
      for (const n of names) {
        const parts = n.match(/\b(\w+)\s+as\s+(\w+)\b/)
        if (parts) exportedFns.add(parts[2])
      }
    }
  }

  // 找出缺失的函数
  const missing = []
  for (const fn of requiredFns) {
    if (!exportedFns.has(fn)) {
      missing.push(fn)
    }
  }

  if (missing.length === 0) continue

  // 提取 BASE 常量
  const baseMatch = content.match(/const\s+BASE\s*=\s*['"`]([^'"`]+)['"`]/)
  const BASE = baseMatch ? baseMatch[1] : ''

  const additions = []
  let hasAliasSection = false

  for (const fn of missing) {
    // 情况1: saveXxx 缺失，但有 createXxx → 添加别名导出
    if (fn.startsWith('save') && exportedFns.has('create' + fn.slice(4))) {
      const createFn = 'create' + fn.slice(4)
      if (!hasAliasSection) {
        additions.push('\n// ===== 别名导出（命名兼容） =====')
        hasAliasSection = true
      }
      additions.push(`export { ${createFn} as ${fn} }`)
      exportedFns.add(fn) // 标记已处理
      continue
    }

    // 情况2: 状态流转函数缺失 → 添加标准实现
    // 推断 HTTP 方法和路径
    let impl = null
    const lowerFn = fn.toLowerCase()

    if (fn.startsWith('reject')) {
      impl = `export function ${fn}(id, reason) { return request.post(\`\${BASE}/\${id}/reject\`, { reason }) }`
    } else if (fn.startsWith('reverseApprove')) {
      impl = `export function ${fn}(id) { return request.post(\`\${BASE}/\${id}/reverse-approve\`) }`
    } else if (fn.startsWith('unvoid')) {
      impl = `export function ${fn}(id) { return request.post(\`\${BASE}/\${id}/unvoid\`) }`
    } else if (fn.startsWith('void') && !fn.startsWith('unvoid')) {
      impl = `export function ${fn}(id, reason) { return request.post(\`\${BASE}/\${id}/void\`, { reason }) }`
    } else if (fn.startsWith('closeOrder')) {
      impl = `export function ${fn}(id) { return request.post(\`\${BASE}/\${id}/close-order\`) }`
    } else if (fn.startsWith('reopen')) {
      impl = `export function ${fn}(id) { return request.post(\`\${BASE}/\${id}/reopen\`) }`
    } else if (fn.startsWith('close') && !fn.startsWith('closeOrder')) {
      impl = `export function ${fn}(id, reason) { return request.post(\`\${BASE}/\${id}/close\`, { reason }) }`
    } else if (fn.startsWith('delete')) {
      impl = `export function ${fn}(id) { return request.delete(\`\${BASE}/\${id}\`) }`
    } else if (fn.startsWith('save')) {
      // saveXxx 缺失且无 createXxx → 添加标准 save 实现
      impl = `export function ${fn}(data) { return request.post(\`\${BASE}\`, data) }`
    } else if (fn.startsWith('submit')) {
      impl = `export function ${fn}(id) { return request.post(\`\${BASE}/\${id}/submit\`) }`
    } else if (fn.startsWith('approve')) {
      impl = `export function ${fn}(id) { return request.post(\`\${BASE}/\${id}/approve\`) }`
    } else if (fn.startsWith('cancel')) {
      impl = `export function ${fn}(id, reason) { return request.post(\`\${BASE}/\${id}/cancel\`, { reason }) }`
    } else if (fn.startsWith('get')) {
      impl = `export function ${fn}(id) { return request.get(\`\${BASE}/\${id}\`) }`
    } else if (fn.startsWith('list')) {
      impl = `export function ${fn}(params) { return request.get(\`\${BASE}/page\`, { params }) }`
    } else if (fn.startsWith('update')) {
      impl = `export function ${fn}(id, data) { return request.put(\`\${BASE}/\${id}\`, data) }`
    } else if (fn.startsWith('create')) {
      impl = `export function ${fn}(data) { return request.post(\`\${BASE}\`, data) }`
    } else {
      // 未知函数，跳过并报告
      console.log(`[SKIP] ${apiRelPath}: 无法推断 ${fn} 的实现`)
      continue
    }

    if (!hasAliasSection) {
      additions.push('\n// ===== 自动补齐缺失的导出 =====')
      hasAliasSection = true
    }
    additions.push(impl)
    exportedFns.add(fn)
  }

  if (additions.length === 0) continue

  // 确保文件末尾有换行
  content = content.replace(/\s+$/, '\n')
  content += additions.join('\n') + '\n'

  fs.writeFileSync(apiFile, content, 'utf8')
  console.log(`[FIX] ${apiRelPath}: 补齐 ${additions.length} 个缺失导出 (${missing.join(', ')})`)
  fixed++
}

console.log(`\n共修复 ${fixed} 个 API 文件`)
