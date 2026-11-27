// fix-isdraft-regex.mjs — 修复 isDraft 升级时由于正则 [^)]* 只匹配到第一个 ) 导致的双重 => 语法错误
// 症状：const isDraft = computed(() => canEdit(form.status, form.closeStatus)) => !form.status || form.status === '草稿')
// 修复：找到所有包含 "canEdit(form.status, form.closeStatus))" 的 isDraft 行，整行替换为正确的
import { globSync } from 'glob'
import fs from 'fs'

const ROOT = process.cwd()
const files = globSync('src/views/**/*Edit.vue', { cwd: ROOT, windowsPathsNoEscape: true })

const GOOD_LINE = "const isDraft = computed(() => canEdit(form.status, form.closeStatus))"
const BAD_PATTERN_1 = /const\s+isDraft\s*=\s*computed\s*\(\(\)\s*=>\s*canEdit\(form\.status,\s*form\.closeStatus\)\)\s*\)\s*=>/

let fixed = 0
for (const f of files) {
  let content = fs.readFileSync(f, 'utf8')
  if (!BAD_PATTERN_1.test(content) && !content.includes(") => !form.status")) {
    // 另一种情况：const isDraft = computed(() => canEdit(...)) => <something>
    const lines = content.split('\n')
    let changed = false
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i]
      if (line.includes('const isDraft = computed')) {
        // 如果行的最后是多余的：有两个 => 或结尾是 )
        // 简单处理：直接替换整行（不管后面是否还有其他代码，反正这行应该只有 isDraft）
        if (line !== GOOD_LINE) {
          // 保留原缩进
          const indent = line.match(/^\s*/)?.[0] || ''
          lines[i] = indent + GOOD_LINE
          // 如果后面还有多余的尾巴（如 => !form.status || form.status === '草稿')），
          // 它会被替换后下一行可能出现（如果在同一行就被替换掉了）
          // 但如果 split 是按 \n 分开的，split 不会在同一行有多个\n
          changed = true
        }
      }
    }
    if (changed) {
      content = lines.join('\n')
    } else {
      continue
    }
  } else {
    // 用正则直接修复：找到 "const isDraft = computed(...)" 这一行到行尾或多余尾巴的位置
    content = content.replace(
      /const\s+isDraft\s*=\s*computed\s*\(\(\)\s*=>\s*canEdit\(form\.status,\s*form\.closeStatus\)\)[^\n]*$/gm,
      GOOD_LINE
    )
  }
  // 最后的保险：如果文件中仍出现 "canEdit(form.status, form.closeStatus))"（多了一个)）+ 多余的尾巴，统一修
  content = content.replace(
    /const\s+isDraft\s*=\s*computed\s*\(\(\)\s*=>\s*canEdit\(form\.status,\s*form\.closeStatus\)\).*$/gm,
    GOOD_LINE
  )
  fs.writeFileSync(f, content, 'utf8')
  fixed++
}
console.log(`\n已扫描 ${fixed} 个文件，修复 isDraft 坏行`)
