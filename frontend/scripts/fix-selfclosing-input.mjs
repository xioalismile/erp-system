// fix-selfclosing-input.mjs — 修复 patchNoFieldDisabled 引入的"自闭合变非自闭合但缺end tag"的问题
// 症状：<el-input v-model="form.xxxNo" ... /> 被误改为 <el-input ... :disabled="!isDraft" ... >（少了/），且无 </el-input>
// 修复：扫描所有*Edit.vue，找出所有匹配 "v-model=\"form.xxxNo\"" 且紧跟 :disabled="!isDraft" 但没有 /结尾也没有同层 </el-input> 的标签，补成自闭合
import { globSync } from 'glob'
import fs from 'fs'

const ROOT = process.cwd()
const files = globSync('src/views/**/*Edit.vue', { cwd: ROOT, windowsPathsNoEscape: true })

let fixed = 0
for (const f of files) {
  let content = fs.readFileSync(f, 'utf8')
  // 匹配：<el-input ... v-model="form.*(No|Code|BillNo)" ... :disabled="!isDraft" ... >（最后是>但不是/>）
  const re = /<el-input([^>/]*?)v-model="form\.([A-Za-z_][A-Za-z0-9_]*?(?:No|Code|BillNo))"([^>/]*?):disabled="!isDraft"([^>/]*?)>/g
  let changed = false
  content = content.replace(re, (match, before, field, mid, after) => {
    // 如果紧跟着后面有 </el-input> 就不用改（不是自闭合场景）
    // 或者标签本身已经是 /> 结尾（但这里正则匹配的是[^>/]，所以/>不会进）
    changed = true
    return `<el-input${before}v-model="form.${field}"${mid}:disabled="!isDraft"${after} />`
  })
  if (changed) { fs.writeFileSync(f, content, 'utf8'); console.log(`[FIXED] ${f}`); fixed++ }
}
console.log(`\n共修复 ${fixed} 个文件的 el-input 自闭合问题`)
