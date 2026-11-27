// fix-missing-billactions.mjs — 修复无 BillActions 组件和无 reject/void 弹窗的 Edit.vue
// 策略：
// 1. 如果页面没有 <BillActions，在 </template> 前注入 BillActions 组件
// 2. 如果页面没有 rejectDialog/voidDialog 弹窗，在最后一个 </el-dialog> 后（或 </template> 前）注入
import { globSync } from 'glob'
import fs from 'fs'

const ROOT = process.cwd()
const files = globSync('src/views/**/*Edit.vue', { cwd: ROOT, windowsPathsNoEscape: true })
  .filter(f => {
    const unix = f.replace(/\\/g, '/')
    return !unix.includes('/basedata/') && !unix.includes('/SaleOrderEdit.vue')
  })

const BILL_ACTIONS_TPL = `
    <!-- 统一操作按钮区 -->
    <BillActions
      :status="form.status"
      :close-status="form.closeStatus"
      :is-new="!form.orderId && !form.deliveryId && !form.inboundId && !form.invoiceId && !form.voucherId && !form.contractId && !form.requisitionId && !form.stockCheckId && !form.transferId && !form.bomId && !form.sampleId && !form.freeSampleId && !form.workOrderId && !form.billId && !form.id"
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

const DIALOGS_TPL = `

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

let fixedBillActions = 0, fixedDialogs = 0

for (const f of files) {
  let content = fs.readFileSync(f, 'utf8')
  let changed = false

  // 1. 检查是否有 BillActions 组件
  if (!content.includes('<BillActions')) {
    // 在 </template> 前注入
    const templateEnd = content.lastIndexOf('</template>')
    if (templateEnd > 0) {
      // 找到 </div> 或其他闭合标签在 </template> 之前的位置
      // 在最后一个顶层闭合标签前注入
      content = content.slice(0, templateEnd) + BILL_ACTIONS_TPL + '\n  ' + content.slice(templateEnd)
      changed = true
      fixedBillActions++
      console.log(`[BillActions] ${f}: 已注入`)
    }
  }

  // 2. 检查是否有 rejectDialog 弹窗
  if (!content.includes('rejectDialog') && !content.includes('title="驳回单据"')) {
    // 在最后一个 </el-dialog> 后注入，如果没有 </el-dialog> 就在 </template> 前注入
    const lastDialog = content.lastIndexOf('</el-dialog>')
    if (lastDialog > 0) {
      const insertPos = lastDialog + '</el-dialog>'.length
      content = content.slice(0, insertPos) + DIALOGS_TPL + content.slice(insertPos)
    } else {
      const templateEnd = content.lastIndexOf('</template>')
      if (templateEnd > 0) {
        content = content.slice(0, templateEnd) + DIALOGS_TPL + '\n  ' + content.slice(templateEnd)
      }
    }
    changed = true
    fixedDialogs++
    console.log(`[Dialogs] ${f}: reject/void 弹窗已注入`)
  }

  if (changed) {
    fs.writeFileSync(f, content, 'utf8')
  }
}
console.log(`\n共修复：${fixedBillActions} 个文件注入了 BillActions，${fixedDialogs} 个文件注入了 reject/void 弹窗`)
