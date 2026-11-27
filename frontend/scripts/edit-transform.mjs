// edit-transform.mjs — 批量改造单据 Edit.vue 页面
// 用法：node scripts/edit-transform.mjs [glob_path]
//   默认: src/views/**/*Edit.vue (排除SaleOrderEdit.vue样板)
import { globSync } from 'glob'
import fs from 'fs'

const ROOT = process.cwd()
const pattern = process.argv[2] || 'src/views/**/*Edit.vue'
const SKIP = [
  '/sale/SaleOrderEdit.vue',
  '/basedata/AccountEdit.vue',
  '/basedata/CurrencyEdit.vue',
  '/basedata/CustomerEdit.vue',
  '/basedata/DepartmentEdit.vue',
  '/basedata/EmployeeEdit.vue',
  '/basedata/LocationEdit.vue',
  '/basedata/MaterialCategoryEdit.vue',
  '/basedata/MaterialEdit.vue',
  '/basedata/PaymentTermEdit.vue',
  '/basedata/PriceLevelEdit.vue',
  '/basedata/SettleEdit.vue',
  '/basedata/SupplierEdit.vue',
  '/basedata/UnitEdit.vue',
  '/basedata/WarehouseEdit.vue',
  '/masterdata/BomEdit.vue'
]

const files = globSync(pattern, { cwd: ROOT, windowsPathsNoEscape: true })
  .filter(f => {
    const unix = f.replace(/\\/g, '/')
    for (const s of SKIP) if (unix.endsWith(s)) return false
    return true
  })

console.log(`待扫描 Edit 页面: ${files.length} 个`)

function injectImports(content) {
  if (content.includes("from '@/utils/bill-status'")) return content
  const lines = content.split('\n')
  const scriptStartIdx = lines.findIndex(l => /^\s*<script\s+setup/.test(l))
  if (scriptStartIdx < 0) return content
  let lastImportIdx = scriptStartIdx
  for (let i = scriptStartIdx + 1; i < lines.length; i++) {
    if (/^\s*import\s+/.test(lines[i])) lastImportIdx = i
  }
  const insertIdx = lastImportIdx + 1
  lines.splice(insertIdx, 0, '',
    "import { canEdit, canSave, canSubmit, canApprove, canReject, canReverseApprove, canClose, canReopen, canVoid, canUnvoid, canDelete } from '@/utils/bill-status'",
    "import BillActions from '@/components/bill/BillActions.vue'"
  )
  return lines.join('\n')
}

function patchIsDraft(content) {
  if (/const\s+isDraft\s*=\s*computed\s*\(/.test(content)) {
    const newContent = content.replace(
      /const\s+isDraft\s*=\s*computed\s*\([^)]*\)/g,
      "const isDraft = computed(() => canEdit(form.status, form.closeStatus))"
    )
    if (newContent !== content) { console.log('   [patch] isDraft 已升级'); return newContent }
  }
  const lines = content.split('\n')
  const formReactiveIdx = lines.findIndex(l => /const\s+form\s*=\s*reactive\s*\(/.test(l))
  if (formReactiveIdx >= 0) {
    let insertIdx = formReactiveIdx + 1
    if (!lines[formReactiveIdx].includes(')')) {
      for (let i = formReactiveIdx + 1; i < lines.length; i++) {
        if (lines[i].includes(')')) { insertIdx = i + 1; break }
      }
    }
    lines.splice(insertIdx, 0, '', 'const isDraft = computed(() => canEdit(form.status, form.closeStatus))')
    console.log('   [patch] isDraft computed 已新增')
    return lines.join('\n')
  }
  console.warn('   [WARN] 未找到 form/isDraft 定义')
  return content
}

function patchFooter(content) {
  const footerRegexes = [
    /<div class="action-btns"[^>]*>[\s\S]*?<\/div>\s*<\/div>/,
    /<div class="footer"[^>]*>[\s\S]*?<\/div>\s*<\/div>/,
    /<div class="form-footer"[^>]*>[\s\S]*?<\/div>\s*<\/div>/,
    /<div class="btn-group"[^>]*>[\s\S]*?<\/div>\s*<\/div>/,
    /<div class="action-btns"[^>]*>[\s\S]*?<\/div>/,
    /<div class="footer"[^>]*>[\s\S]*?<\/div>/,
    /<div class="form-footer"[^>]*>[\s\S]*?<\/div>/,
    /<div class="btn-group"[^>]*>[\s\S]*?<\/div>/
  ]
  let bestMatch = null, bestMatchLen = 0
  for (const re of footerRegexes) {
    const m = content.match(re)
    if (m && m[0].length > bestMatchLen) { bestMatch = m; bestMatchLen = m[0].length }
  }
  if (!bestMatch) { console.warn('   [WARN] 未找到底部操作按钮容器'); return content }
  const tpl = `
    <BillActions
      :status="form.status"
      :close-status="form.closeStatus"
      :is-new="!form.id && !form.orderId && !form.deliveryId && !form.inboundId && !form.invoiceId && !form.voucherId && !form.contractId && !form.requisitionId && !form.stockCheckId && !form.transferId && !form.bomId && !form.sampleId && !form.freeSampleId && !form.workOrderId && !form.billId"
      @save="onSave"
      @submit="onSubmit"
      @approve="onApprove"
      @reject="handleReject"
      @reverse-approve="handleReverseApprove"
      @close="handleClose"
      @reopen="handleReopen"
      @void="handleVoid"
      @unvoid="handleUnvoid"
      @delete="handleDelete"
      @print="handlePrint"
      @copy="handleCopy"
    />`
  console.log('   [patch] 底部按钮已替换为 BillActions')
  return content.replace(bestMatch[0], tpl)
}

function patchNoFieldDisabled(content) {
  let changed = false
  const re = /<el-input([^>]*?)v-model="form\.([A-Za-z_][A-Za-z0-9_]*?(?:No|Code|BillNo))"([^>/]*?)\/?\s*>/g
  content = content.replace(re, (match, before, field, after) => {
    if (/:\s*disabled\s*=/.test(match) || /\bdisabled\s*=/.test(match)) return match
    changed = true
    return `<el-input${before}v-model="form.${field}" :disabled="!isDraft"${after}>`
  })
  if (changed) console.log('   [patch] 单号字段 :disabled="!isDraft" 已加')
  return content
}

function patchDialogs(content) {
  const lastDialogClose = content.lastIndexOf('</el-dialog>')
  if (lastDialogClose < 0) { console.warn('   [WARN] 无</el-dialog>，跳过弹窗注入'); return content }
  if (content.includes('title="驳回单据"')) { console.log('   [skip] 已有驳回/作废弹窗'); return content }
  const insertPos = lastDialogClose + '</el-dialog>'.length
  const tpl = `

    <!-- 驳回弹窗 -->
    <el-dialog v-model="rejectDialog" title="驳回单据" width="420px">
      <el-form label-width="80px">
        <el-form-item label="驳回原因">
          <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="请输入驳回原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialog = false">返回</el-button>
        <el-button type="warning" @click="doReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <!-- 作废弹窗 -->
    <el-dialog v-model="voidDialog" title="作废单据" width="420px">
      <el-form label-width="80px">
        <el-form-item label="作废原因">
          <el-input v-model="voidReason" type="textarea" :rows="3" placeholder="请输入作废原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="voidDialog = false">返回</el-button>
        <el-button type="danger" @click="doVoid">确认作废</el-button>
      </template>
    </el-dialog>`
  console.log('   [patch] reject/void 弹窗已加')
  return content.slice(0, insertPos) + tpl + content.slice(insertPos)
}

function patchScriptHandlers(content) {
  const scriptEnd = content.lastIndexOf('</script>')
  if (scriptEnd < 0) { console.warn('   [WARN] 无</script>'); return content }
  if (content.includes('function handleReject(')) { console.log('   [skip] handler 已存在'); return content }

  const tpl = `

  // ======== 状态流转：通用占位 handler（样板：与 SaleOrderEdit 对齐） ========
  // 重要：请将下方 TODO_* 占位函数替换为当前模块实际 API 调用（或在上方 API 导入后重命名为这些函数）
  const TODO_saveBill = async (form) => { throw new Error('[TODO] 请替换为实际 save* API，例如 savePurchaseOrder(form)') }
  const TODO_updateBill = async (id, form) => { throw new Error('[TODO] 请替换为实际 update* API，例如 updatePurchaseOrder(id, form)') }
  const TODO_submitBill = async (id) => { throw new Error('[TODO] 请替换为实际 submit* API，例如 submitPurchaseOrder(id)') }
  const TODO_approveBill = async (id) => { throw new Error('[TODO] 请替换为实际 approve* API，例如 approvePurchaseOrder(id)') }
  const TODO_rejectBill = async (id, reason) => { throw new Error('[TODO] 请替换为实际 reject* API，例如 rejectPurchaseOrder(id, reason)') }
  const TODO_reverseApproveBill = async (id) => { throw new Error('[TODO] 请替换为实际 reverseApprove* API，例如 reverseApprovePurchaseOrder(id)') }
  const TODO_closeBill = async (id) => { throw new Error('[TODO] 请替换为实际 close* API，例如 closePurchaseOrder(id) 或 closeOrder*') }
  const TODO_reopenBill = async (id) => { throw new Error('[TODO] 请替换为实际 reopen* API，例如 reopenPurchaseOrder(id)') }
  const TODO_voidBill = async (id, reason) => { throw new Error('[TODO] 请替换为实际 void* API，例如 voidPurchaseOrder(id, reason)') }
  const TODO_unvoidBill = async (id) => { throw new Error('[TODO] 请替换为实际 unvoid* API，例如 unvoidPurchaseOrder(id)') }
  const TODO_deleteBill = async (id) => { throw new Error('[TODO] 请替换为实际 delete* API，例如 deletePurchaseOrder(id)') }

  // 通用：取单据ID（覆盖各模块常见字段，兜底 form.id）
  const billId = computed(() => {
    return form.orderId || form.deliveryId || form.inboundId || form.invoiceId || form.contractId
        || form.voucherId || form.requisitionId || form.stockCheckId || form.transferId
        || form.bomId || form.sampleId || form.freeSampleId || form.workOrderId || form.billId || form.id
  })

  // 弹窗与 reason
  const rejectDialog = ref(false)
  const rejectReason = ref('')
  const voidDialog = ref(false)
  const voidReason = ref('')

  async function onSave() {
    if (form.status && !isDraft.value) { ElMessage.warning(\`当前状态为「\${form.status}」, 不可修改\`); return }
    if (typeof calcTotal === 'function') calcTotal()
    try {
      if (billId.value) {
        await TODO_updateBill(billId.value, form)
      } else {
        const res = await TODO_saveBill(form)
        if (res) {
          const idKeys = ['orderId','deliveryId','inboundId','invoiceId','contractId','voucherId','requisitionId','stockCheckId','transferId','bomId','sampleId','freeSampleId','workOrderId','billId','id']
          for (const k of idKeys) if (res[k] != null && form[k] == null) form[k] = res[k]
          const noKeys = ['orderNo','deliveryNo','inboundNo','invoiceNo','contractNo','voucherNo','requisitionNo','stockCheckNo','transferNo','sampleNo','billNo']
          for (const k of noKeys) if (res[k] != null) form[k] = res[k]
          if (res.status) form.status = res.status
          if (res.approvalStatus) form.approvalStatus = res.approvalStatus
        }
      }
      ElMessage.success('保存成功')
      if (typeof loadDetail === 'function') await loadDetail()
    } catch (e) { ElMessage.error(e.message || '保存失败') }
  }

  async function onSubmit() {
    if (!billId.value) { try { await onSave(); if (!billId.value) return } catch (_) { return } }
    try {
      await ElMessageBox.confirm('确认提交？提交后将进入审批流程', '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
      await TODO_submitBill(billId.value)
      ElMessage.success('已提交')
      if (typeof loadDetail === 'function') await loadDetail()
    } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || '提交失败') }
  }

  async function onApprove() {
    if (!billId.value) { ElMessage.warning('请先保存单据'); return }
    try {
      await ElMessageBox.confirm('确认审核？审核后将不可直接修改', '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
      await TODO_approveBill(billId.value)
      ElMessage.success('审核成功')
      if (typeof loadDetail === 'function') await loadDetail()
    } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || '审核失败') }
  }

  function handleReject() { rejectReason.value = ''; rejectDialog.value = true }
  async function doReject() {
    try {
      await TODO_rejectBill(billId.value, rejectReason.value)
      ElMessage.success('已驳回'); rejectDialog.value = false
      if (typeof loadDetail === 'function') await loadDetail()
    } catch (e) { ElMessage.error(e.message || '驳回失败') }
  }

  async function handleReverseApprove() {
    try {
      await ElMessageBox.confirm('确认反审批？反审批后单据回到草稿态可修改，需下游未生效', '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
      await TODO_reverseApproveBill(billId.value)
      ElMessage.success('反审批成功')
      if (typeof loadDetail === 'function') await loadDetail()
    } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || '反审批失败') }
  }

  async function handleClose() {
    try {
      await ElMessageBox.confirm('确认结案？结案后进入完结态不可修改', '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
      await TODO_closeBill(billId.value)
      ElMessage.success('已结案')
      if (typeof loadDetail === 'function') await loadDetail()
    } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || '结案失败') }
  }

  async function handleReopen() {
    try {
      await ElMessageBox.confirm('确认反结案？反结案后返回已审核态', '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
      await TODO_reopenBill(billId.value)
      ElMessage.success('反结案成功')
      if (typeof loadDetail === 'function') await loadDetail()
    } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || '反结案失败') }
  }

  function handleVoid() { voidReason.value = ''; voidDialog.value = true }
  async function doVoid() {
    try {
      await TODO_voidBill(billId.value, voidReason.value)
      ElMessage.success('已作废'); voidDialog.value = false
      if (typeof loadDetail === 'function') await loadDetail()
    } catch (e) { ElMessage.error(e.message || '作废失败') }
  }

  async function handleUnvoid() {
    try {
      await ElMessageBox.confirm('确认反作废？将恢复到作废前状态', '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
      await TODO_unvoidBill(billId.value)
      ElMessage.success('反作废成功')
      if (typeof loadDetail === 'function') await loadDetail()
    } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || '反作废失败') }
  }

  async function handleDelete() {
    try {
      await ElMessageBox.confirm('确认删除？删除后不可恢复', '警告', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'error' })
      await TODO_deleteBill(billId.value)
      ElMessage.success('删除成功')
      if (typeof router !== 'undefined' && router.back) router.back()
    } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || '删除失败') }
  }

  function handlePrint() { window.print() }
  function handleCopy() {
    const idKeys = ['orderId','deliveryId','inboundId','invoiceId','contractId','voucherId','requisitionId','stockCheckId','transferId','bomId','sampleId','freeSampleId','workOrderId','billId','id']
    for (const k of idKeys) if (form[k] !== undefined) form[k] = undefined
    const noKeys = ['orderNo','deliveryNo','inboundNo','invoiceNo','contractNo','voucherNo','requisitionNo','stockCheckNo','transferNo','sampleNo','billNo']
    for (const k of noKeys) if (form[k] !== undefined) form[k] = ''
    form.status = '草稿'
    if (form.approvalStatus) form.approvalStatus = '草稿'
    if (form.closeStatus) form.closeStatus = 0
    ElMessage.success('已复制为新单草稿，请修改后保存')
  }
  // ======== 状态流转 END ========
`
  console.log('   [patch] script handler 已注入（含 TODO_* API 占位）')
  return content.slice(0, scriptEnd) + tpl + '\n' + content.slice(scriptEnd)
}

let ok = 0, fail = 0, skip = 0
for (const f of files) {
  console.log(`\n== 处理: ${f} ==`)
  try {
    let content = fs.readFileSync(f, 'utf8')
    const isBillPage = content.includes('form.status')
                    || content.includes('approvalStatus')
                    || /function\s+onSubmit\s*\(/.test(content)
                    || /提交审核|提交审批/.test(content)
    if (!isBillPage) { console.log('   [SKIP] 非单据页面（无 form.status / onSubmit）'); skip++; continue }
    content = injectImports(content)
    content = patchIsDraft(content)
    content = patchFooter(content)
    content = patchNoFieldDisabled(content)
    content = patchDialogs(content)
    content = patchScriptHandlers(content)
    fs.writeFileSync(f, content, 'utf8')
    ok++
    console.log('   [OK] 写入完毕')
  } catch (e) {
    fail++
    console.error('   [FAIL]', e.message, e.stack)
  }
}
console.log(`\n==== 批量处理完成：${ok} 成功 / ${skip} 跳过 / ${fail} 失败 / ${files.length} 总文件 ====`)
