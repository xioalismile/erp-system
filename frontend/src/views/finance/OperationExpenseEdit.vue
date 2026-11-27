<template>
  <div class="operation-expense-edit">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div class="header-left">
        <div class="back-btn" @click="$router.push('/finance/operation-expense')">
          <el-icon><ArrowLeft /></el-icon>
          <span>{{ $tt("返回列表") }}</span>
        </div>
        <div class="title-area">
          <div class="title-row">
            <el-icon class="title-icon"><Document /></el-icon>
            <span class="page-title">{{ isEdit ? '费用分摊' : '新增费用分摊' }}</span>
            <span v-if="form.expenseNo" class="order-no">
              <el-icon><Collection /></el-icon>
              {{ form.expenseNo }}
            </span>
            <el-tag v-if="form.status" size="small" :type="statusTagType(form.status)" effect="plain" class="status-tag">{{ $dt(form.status) }}</el-tag>
          </div>
        </div>
      </div>
      <div class="header-right">
        <el-button v-permission="perm('view')" v-if="!(!form.orderId && !form.deliveryId && !form.inboundId && !form.invoiceId && !form.voucherId && !form.contractId && !form.requisitionId && !form.stockCheckId && !form.transferId && !form.bomId && !form.sampleId && !form.freeSampleId && !form.workOrderId && !form.billId && !form.id)" @click="changeRecordVisible = true">
          <el-icon><Memo /></el-icon>{{ $tt('操作日志') }}
        </el-button>
        <BillActions bill-type="OPERATION_EXPENSE"
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
        />
      </div>
    </div>

    <!-- 基本信息卡片（可折叠） -->
    <div class="collapsible-card" :class="{ collapsed: !collapsed.base }">
      <div class="card-header" @click="toggleCard('base')">
        <div class="header-left">
          <el-icon class="collapse-icon" :class="{ active: collapsed.base }"><ArrowRight /></el-icon>
          <span class="card-title">{{ $tt("基本信息") }}</span>
        </div>
      </div>
      <div v-show="collapsed.base" class="card-body">
        <el-form :model="form" :rules="rules" ref="headerFormRef" label-width="100px" class="base-form">
          <!-- 单据信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>单据信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('费用单号')">
                <el-input v-model="form.expenseNo" :placeholder="docCodePlaceholder" :disabled="!isDraft" class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('费用日期')" prop="expenseDate" required>
                <el-date-picker v-model="form.expenseDate" type="date" value-format="YYYY-MM-DD" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('状态')">
                <el-input :value="form.status" disabled class="field-input" />
              </el-form-item>
            </div>
          </div>

          <!-- 申请人信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>申请人信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('申请人')">
                <el-input v-model="form.applicantName" :placeholder="$tt('自动带出当前用户')" readonly class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('部门')">
                <el-input v-model="form.departmentName" :placeholder="$tt('选择部门')" readonly class="field-input" @click="showDeptSelect" style="cursor:pointer" />
              </el-form-item>
              <el-form-item :label="$tf('分摊方式')" prop="allocationMethod" required>
                <el-select v-model="form.allocationMethod" :placeholder="$tt('请选择')" class="field-input" :disabled="!isDraft">
                  <el-option v-for="m in allocationMethodOptions" :key="m" :label="m" :value="m" />
                </el-select>
              </el-form-item>
            </div>
          </div>

          <!-- 其他 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>其他
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('备注')" class="span-all">
                <el-input v-model="form.remark" type="textarea" :rows="2" class="field-input" :disabled="!isDraft" :placeholder="$tt('费用说明')" />
              </el-form-item>
            </div>
          </div>
        </el-form>
      </div>
    </div>

    <!-- 费用明细卡片（通用组件 BillItemsTable：卡片/列设置/动态列/汇总 全站共用一套） -->
    <BillItemsTable
      v-model:collapsed="collapsed.items"
      :items="form.expenseLines"
      page-key="OperationExpenseEdit_items"
      title="费用明细"
      col-setting-title="费用明细列设置"
      :columns="expenseLineColumns"
      :readonly="!isDraft"
      :show-total-qty="false"
      :summary-items="expenseSummaryItems"
      @row-change="calcExpenseTotal"
      @remove="calcExpenseTotal"
    >
      <template #actions>
        <el-button v-permission="perm('addRow')" size="small" type="primary" @click="addExpenseLine" :disabled="!isDraft">
          <el-icon><Plus /></el-icon>新增费用行
        </el-button>
      </template>
      <template #cell-remark="{ row }">
        <el-input v-model="row.remark" size="small" :disabled="!isDraft" :placeholder="$tt('费用说明')" />
      </template>
    </BillItemsTable>

    <!-- 分摊明细卡片（通用组件 BillItemsTable：卡片/列设置/动态列/汇总 全站共用一套） -->
    <BillItemsTable
      v-model:collapsed="collapsed.allocations"
      :items="form.allocations"
      page-key="OperationExpenseEdit_allocations"
      title="分摊明细"
      col-setting-title="分摊明细列设置"
      :columns="allocationColumns"
      :readonly="!isDraft"
      :show-total-qty="false"
      :summary-items="allocSummaryItems"
      @row-change="calcAllocTotal"
      @remove="calcAllocTotal"
    >
      <template #actions>
        <el-button v-permission="perm('allocateCost')" size="small" type="success" @click="autoAllocate" :disabled="!isDraft || !form.allocationMethod">{{ $tt("自动分摊") }}</el-button>
        <el-button v-permission="perm('addRow')" size="small" type="primary" @click="addAllocation" :disabled="!isDraft">
          <el-icon><Plus /></el-icon>新增分摊行
        </el-button>
      </template>
      <template #cell-remark="{ row }">
        <el-input v-model="row.remark" size="small" :disabled="!isDraft" :placeholder="$tt('说明')" />
      </template>
      <template #summary-extra>
        <!-- 差额提示 + 业务提示（原页在表格上方，通用组件统一把附加块放在汇总区，块内容与口径原样保留） -->
        <div class="alloc-summary-extra">
          <div class="diff-info-bar" :class="allocDiff === 0 ? 'balanced' : 'unbalanced'">
            <el-icon><InfoFilled /></el-icon>
            <span class="diff-label">{{ $tt("分摊合计：") }}</span>
            <span class="diff-value">¥{{ allocTotal.toFixed(2) }}</span>
            <span class="diff-sep">/</span>
            <span class="diff-label">{{ $tt("费用合计：") }}</span>
            <span class="diff-value">¥{{ expenseTotal.toFixed(2) }}</span>
            <el-tag v-if="allocDiff !== 0" type="danger" size="small" style="margin-left:8px;">差额 ¥{{ allocDiff.toFixed(2) }}</el-tag>
            <el-tag v-else-if="form.allocations.length > 0" type="success" size="small" style="margin-left:8px;">{{ $tt("已平") }}</el-tag>
          </div>
          <div class="tip-bar">
            提示：关联"入库类型单据"（生产入库/采购入库/销售退货）的费用，审核后将自动汇总到对应产品的成本核算单
          </div>
        </div>
      </template>
    </BillItemsTable>

    <!-- 附件卡片（可折叠） -->
    <div class="collapsible-card" :class="{ collapsed: !collapsed.attach }">
      <div class="card-header" @click="toggleCard('attach')">
        <div class="header-left">
          <el-icon class="collapse-icon" :class="{ active: collapsed.attach }"><ArrowRight /></el-icon>
          <span class="card-title">{{ $tt("附件资料") }}</span>
        </div>
      </div>
      <div v-show="collapsed.attach" class="card-body">
        <FileUploader
          v-model="form.attachmentUrls"
          type="operation-expense"
          :disabled="!isDraft"
          button-text="上传附件"
          tip-text="支持图片、PDF、Word、Excel 等文件"
        />
      </div>
    </div>

    <!-- 审核流转（真实审批进度，通用组件） -->
    <ApprovalFlowTimeline bill-type="OPERATION_EXPENSE" :bill-id="form.id" :status="form.status" />

    <!-- 驳回弹窗 -->
    <el-dialog v-model="rejectDialog" :title="$tf('驳回单据')" width="420px">
      <el-form label-width="80px">
        <el-form-item :label="$tf('驳回原因')">
          <el-input v-model="rejectReason" type="textarea" :rows="3" :placeholder="$tt('请输入驳回原因')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="rejectDialog = false">{{ $tt("返回") }}</el-button>
        <el-button v-permission="perm('dlgConfirm')" type="warning" @click="doReject">{{ $tt("确认驳回") }}</el-button>
      </template>
    </el-dialog>

    <!-- 作废弹窗 -->
    <el-dialog v-model="voidDialog" :title="$tf('作废单据')" width="420px">
      <el-form label-width="80px">
        <el-form-item :label="$tf('作废原因')">
          <el-input v-model="voidReason" type="textarea" :rows="3" :placeholder="$tt('请输入作废原因')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="voidDialog = false">{{ $tt("返回") }}</el-button>
        <el-button v-permission="perm('dlgConfirm')" type="danger" @click="doVoid">{{ $tt("确认作废") }}</el-button>
      </template>
    </el-dialog>

    <!-- 打印预览弹窗 -->
    <PrintPreviewDialog v-model="printVisible" bill-type="OPERATION_EXPENSE" :bill-id="form.id" />

    <!-- 操作日志弹窗 -->
    <ChangeRecordDialog v-model="changeRecordVisible" bill-type="制造费用分摊单" :bill-no="form.expenseNo || ''" />
  </div>

  </template>

<script setup>
import { ref, reactive, computed, onMounted, watch, h} from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification} from 'element-plus'
import { ArrowLeft, Document, Collection, Plus, ArrowRight, Check, TopRight, ArrowDown, CircleCheck, Delete, Printer, Clock, InfoFilled, Memo } from '@element-plus/icons-vue'
import {
  getExpense,
  saveExpense,
  updateExpense,
  deleteExpense,
  submitExpense,
  approveExpense,
  rejectExpense,
  reverseApproveExpense,
  closeOrderExpense,
  reopenExpense,
  voidExpense,
  unvoidExpense
} from '@/api/finance/operationExpense'
import { listSaleOrder } from '@/api/sale/saleOrder'
import { listProductionReceipt } from '@/api/production/productionReceipt'
import { listPurchaseInbound } from '@/api/purchase/inbound'
import { listSaleReturn } from '@/api/sale/saleReturn'
import request from '@/utils/request'
import FileUploader from '@/components/FileUploader.vue'
import ApprovalFlowTimeline from '@/components/bill/ApprovalFlowTimeline.vue'
// 明细通用组件（卡片折叠/列设置/动态列/汇总 全站共用一套）
import BillItemsTable from '@/components/bill/BillItemsTable.vue'
import PrintPreviewDialog from '@/components/PrintPreviewDialog.vue'
import ChangeRecordDialog from '@/components/ChangeRecordDialog.vue'
import { useAutoDocCode } from '@/composables/useAutoDocCode'

import * as BillStatus from '@/utils/bill-status'
import { confirmSubmit, submitSuccessMessage } from '@/utils/submit-confirm'
import BillActions from '@/components/bill/BillActions.vue'
import { validateBillRequiredFields } from '@/utils/billValidation'
import { useFieldRequiredMark } from '@/utils/fieldRequiredMark'
import { triggerListRefresh } from '@/composables/useAutoRefresh'
import { useCopyBill } from '@/composables/useCopyBill'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('finance:operation-expense')

const route = useRoute()
const router = useRouter()
const headerFormRef = ref()

const isEdit = computed(() => !!route.params.id)
// 单据编码自动生成
const { docCodeDisabled, docCodePlaceholder, autoFillDocCodeOnCreate } = useAutoDocCode('OPERATION_EXPENSE')

const STORAGE_KEY = 'operation-expense-edit-collapsed'

const collapsed = reactive({
  base: true,
  items: true,
  allocations: true,
  attach: false,
  audit: false
})

function loadCollapsed() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) {
      const parsed = JSON.parse(saved)
      Object.keys(parsed).forEach(k => {
        if (k in collapsed) collapsed[k] = parsed[k]
      })
    }
  } catch (e) {}
}

function saveCollapsed() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(collapsed))
  } catch (e) {}
}

function toggleCard(key) {
  collapsed[key] = !collapsed[key]
  saveCollapsed()
}

// 两张明细卡片的折叠由通用组件 v-model 驱动，这里监听后持久化（与页头卡片共用同一份 localStorage）
watch(() => [collapsed.items, collapsed.allocations], () => saveCollapsed())

const printVisible = ref(false)
const changeRecordVisible = ref(false)

// 常量
const expenseTypeOptions = ['人工费用', '制造费用', '水电费', '办公费', '出差', '宴请', '快递', '财务费用', '其他']
const allocationMethodOptions = ['平均分摊', '按数量分摊', '按金额分摊', '自定义']
const orderTypeOptions = [
  { label: '生产入库单', value: 'PRODUCTION_RECEIPT' },
  { label: '采购入库单', value: 'PURCHASE_INBOUND' },
  { label: '销售退货单', value: 'SALE_RETURN' },
  { label: '销售订单', value: 'SALE_ORDER' },
  { label: '采购订单', value: 'PURCHASE_ORDER' }
]

// 费用明细列定义（通用组件 BillItemsTable 消费）：editor = 单元格控件；备注列由页面插槽渲染
// 列声明顺序 = 迁移前模板的渲染顺序；费用类型下拉口径与迁移前一致（不可清空）
const expenseLineColumns = [
  { prop: 'expenseType', label: '费用类型', width: 160, editor: 'select', placeholder: '选择类型', clearable: false,
    options: expenseTypeOptions.map(t => ({ value: t, label: t })) },
  { prop: 'amount', label: '费用金额', width: 160, editor: 'number', precision: 2 },
  { prop: 'remark', label: '备注', minWidth: 180 }
]

// 分摊明细列定义（通用组件 BillItemsTable 消费）：比例↔金额联动与基数联动照迁移前原样接到 change 上
const allocationColumns = [
  { prop: 'orderTypeLabel', label: '分摊对象类型', width: 160, editor: 'select', placeholder: '选择类型', clearable: false,
    options: orderTypeOptions, change: (row) => onOrderTypeChange(row) },
  { prop: 'orderNo', label: '分摊对象', minWidth: 240, editor: 'select', placeholder: '选择单号',
    optionsFn: (row) => getOrdersByType(row.orderTypeLabel), change: (row) => onOrderSelect(row) },
  { prop: 'expenseType', label: '费用类型', width: 140, editor: 'select', placeholder: '费用类型', clearable: false,
    options: expenseTypeOptions.map(t => ({ value: t, label: t })) },
  { prop: 'allocationBase', label: '分摊基数', width: 120, editor: 'number', precision: 2, change: (row) => recalcRatio(row) },
  { prop: 'allocationRatio', label: '比例%', width: 100, editor: 'number', precision: 2, min: 0, max: 100, change: (row) => recalcAmount(row) },
  { prop: 'amount', label: '分摊金额', width: 140, editor: 'number', precision: 2, change: () => calcAllocTotal() },
  { prop: 'remark', label: '备注', minWidth: 120 }
]

// 关联订单下拉数据
const saleOrders = ref([])
const purchaseOrders = ref([])
const productionReceipts = ref([])
const purchaseInbounds = ref([])
const saleReturns = ref([])

const form = reactive({
  id: null,
  expenseId: null,
  expenseNo: '',
  expenseType: '',
  expenseDate: new Date().toISOString().slice(0, 10),
  amount: 0,
  relatedOrderNo: '',
  relatedOrderType: '',
  allocationMethod: '平均分摊',
  remark: '',
  status: '草稿',
  applicantName: '',
  departmentId: null,
  departmentName: '',
  attachmentUrls: '',
  createdAt: '',
  createTime: '',
  expenseLines: [],
  allocations: []
})


const isDraft = computed(() => BillStatus.canEdit(form.status, form.closeStatus))
const rules = {
  expenseDate: [{ required: true, message: tt('请选择日期'), trigger: 'change' }],
  allocationMethod: [{ required: true, message: tt('请选择分摊方式'), trigger: 'change' }]
}

function statusTagType(s) {
  const map = { '草稿': 'info', '已提交': 'warning', '已生效': 'success', '已取消': 'danger' }
  return map[s] || 'info'
}

// 费用明细行合计
const expenseTotal = ref(0)
const calcExpenseTotal = () => {
  expenseTotal.value = (form.expenseLines || []).reduce((s, l) => s + Number(l.amount || 0), 0)
  form.amount = expenseTotal.value
  // 费用类型取第一个费用行
  if (form.expenseLines && form.expenseLines.length > 0) {
    const firstType = form.expenseLines.find(l => l.expenseType)
    form.expenseType = firstType ? firstType.expenseType : ''
  }
}

// 分摊明细合计
const allocTotal = ref(0)
const allocDiff = computed(() => expenseTotal.value - allocTotal.value)
const calcAllocTotal = () => {
  allocTotal.value = (form.allocations || []).reduce((s, a) => s + Number(a.amount || 0), 0)
}

// 汇总区（通用组件 BillItemsTable 按此渲染；原页表格自带合计行 → 口径原样搬进汇总区，
// 「合计数量」行原页没有 → 组件侧 show-total-qty=false）
const expenseSummaryItems = computed(() => [
  { label: '费用合计：', value: `¥${expenseTotal.value.toFixed(2)}`, total: true }
])
const allocSummaryItems = computed(() => [
  { label: '分摊合计：', value: `¥${allocTotal.value.toFixed(2)}`, total: true }
])

// 获取某类型的订单列表
const getOrdersByType = (type) => {
  const list = []
  if (type === 'PRODUCTION_RECEIPT') {
    productionReceipts.value.forEach(r => {
      list.push({ value: r.receiptNo, label: `${r.receiptNo} | 生产入库 | ¥${Number(r.totalAmount || 0).toFixed(2)}`, data: r })
    })
  } else if (type === 'PURCHASE_INBOUND') {
    purchaseInbounds.value.forEach(i => {
      list.push({ value: i.receiptNo, label: `${i.receiptNo} | 采购入库 | ¥${Number(i.totalAmount || 0).toFixed(2)}`, data: i })
    })
  } else if (type === 'SALE_RETURN') {
    saleReturns.value.forEach(r => {
      list.push({ value: r.returnNo, label: `${r.returnNo} | 销售退货 | ¥${Number(r.totalAmount || 0).toFixed(2)}`, data: r })
    })
  } else if (type === 'SALE_ORDER') {
    saleOrders.value.forEach(o => {
      list.push({ value: o.orderNo, label: `${o.orderNo} | ${o.customerName} | ¥${o.totalAmount}`, data: o })
    })
  } else if (type === 'PURCHASE_ORDER') {
    purchaseOrders.value.forEach(o => {
      list.push({ value: o.orderNo, label: `${o.orderNo} | ${o.supplierName} | ¥${o.totalAmount}`, data: o })
    })
  }
  return list
}

// 分摊对象类型改变时清空已选单号
const onOrderTypeChange = (row) => {
  row.orderNo = ''
  row.orderId = null
  row.allocationBase = 0
}

// 选择分摊对象后自动填充分摊基数
const onOrderSelect = (row) => {
  if (!row.orderNo) return
  const orders = getOrdersByType(row.orderTypeLabel)
  const found = orders.find(o => o.value === row.orderNo)
  if (found) {
    const d = found.data
    row.orderId = d.orderId || d.receiptId || d.returnId || null
    // 根据分摊方式自动填充分摊基数
    if (form.allocationMethod === '按数量分摊') {
      row.allocationBase = d.quantity || d.totalQuantity || 0
    } else if (form.allocationMethod === '按金额分摊') {
      row.allocationBase = d.totalAmount || d.amount || 0
    } else {
      row.allocationBase = 0
    }
  }
}

// 重新计算分摊比例（基数变化时）
const recalcRatio = (row) => {
  const totalBase = (form.allocations || []).reduce((s, a) => s + Number(a.allocationBase || 0), 0)
  if (totalBase > 0 && row.allocationBase > 0) {
    row.allocationRatio = Number(((Number(row.allocationBase) / totalBase) * 100).toFixed(2))
  }
  recalcAmount(row)
}

// 按比例重新计算金额
const recalcAmount = (row) => {
  if (row.allocationRatio > 0 && expenseTotal.value > 0) {
    row.amount = Number(((expenseTotal.value * row.allocationRatio) / 100).toFixed(2))
  }
  calcAllocTotal()
}

// 新增费用行
const addExpenseLine = () => {
  if (!form.expenseLines) form.expenseLines = []
  form.expenseLines.push({ expenseType: '', amount: 0, remark: '' })
}

// 新增分摊行
const addAllocation = () => {
  if (!form.allocations) form.allocations = []
  form.allocations.push({
    orderTypeLabel: 'PRODUCTION_RECEIPT',
    orderType: 'PRODUCTION_RECEIPT',
    orderId: null,
    orderNo: '',
    expenseType: '',
    allocationBase: 0,
    allocationRatio: 0,
    amount: 0,
    remark: ''
  })
}

// 自动分摊
const autoAllocate = () => {
  if (!form.allocations || form.allocations.length === 0) {
    ElMessage.warning(tt('请先添加分摊行'))
    return
  }
  const total = expenseTotal.value
  if (total <= 0) {
    ElMessage.warning(tt('费用合计为0，无法分摊'))
    return
  }

  const method = form.allocationMethod

  if (method === '平均分摊') {
    const perAmount = Number((total / form.allocations.length).toFixed(2))
    let remainder = Number((total - perAmount * form.allocations.length).toFixed(2))
    form.allocations.forEach((a, i) => {
      a.allocationBase = 1
      a.allocationRatio = Number(((1 / form.allocations.length) * 100).toFixed(2))
      a.amount = perAmount
      if (i === 0) a.amount = Number((a.amount + remainder).toFixed(2))
    })
  } else if (method === '按数量分摊' || method === '按金额分摊') {
    const totalBase = form.allocations.reduce((s, a) => s + Number(a.allocationBase || 0), 0)
    if (totalBase <= 0) {
      ElMessage.warning(tt('分摊基数合计为0，请检查分摊对象的数量/金额'))
      return
    }
    let allocated = 0
    form.allocations.forEach((a, i) => {
      const base = Number(a.allocationBase || 0)
      a.allocationRatio = Number(((base / totalBase) * 100).toFixed(2))
      if (i === form.allocations.length - 1) {
        a.amount = Number((total - allocated).toFixed(2))
      } else {
        a.amount = Number(((base / totalBase) * total).toFixed(2))
        allocated += a.amount
      }
    })
  } else if (method === '自定义') {
    ElMessage.info(tt('自定义分摊请手动输入分摊金额'))
    return
  }

  calcAllocTotal()
  ElMessage.success(tt('自动分摊完成'))
}

async function loadOrders() {
  // 每个API独立调用，互不阻塞，任一失败不影响其他
  const safeFetch = async (fn, label) => {
    try {
      const res = await fn()
      return res.records || res || []
    } catch (e) {
      console.warn(`[${label}] 加载失败:`, e.message || e)
      return []
    }
  }

  saleOrders.value = await safeFetch(
    () => listSaleOrder({ current: 1, size: 100 }),
    '销售订单'
  )
  // 采购订单直接用后端分页接口
  purchaseOrders.value = await safeFetch(
    () => request.get('/purchase/orders', { params: { current: 1, size: 100 } }).catch(() => []),
    '采购订单'
  )
  productionReceipts.value = await safeFetch(
    () => listProductionReceipt({ current: 1, size: 100 }),
    '生产入库'
  )
  purchaseInbounds.value = await safeFetch(
    () => listPurchaseInbound({ current: 1, size: 100 }),
    '采购入库'
  )
  saleReturns.value = await safeFetch(
    () => listSaleReturn({ current: 1, size: 100 }),
    '销售退货'
  )
}

async function handleSave() {
  if (!isDraft.value) {
    ElMessage.warning(`${tt("当前状态为「")}${form.status}${tt("」, 不可修改")}`)
    return
  }
  try {
    await headerFormRef.value?.validate()
  } catch (e) {
    ElMessage.warning(tt('请填写必填项后再保存'))
    return
  }
  // 校验费用明细
  if (!form.expenseLines || form.expenseLines.length === 0) {
    ElMessage.warning(tt('请至少添加一行费用明细'))
    return
  }
  const validLines = form.expenseLines.filter(l => l.expenseType && l.amount > 0)
  if (validLines.length === 0) {
    ElMessage.warning(tt('请填写费用类型和金额'))
    return
  }

  // 计算费用合计
  calcExpenseTotal()

  // 如果有分摊明细，校验分摊金额合计必须等于费用合计
  if (form.allocations && form.allocations.length > 0) {
    calcAllocTotal()
    if (Math.abs(allocDiff.value) > 0.01) {
      ElMessage.warning(`${tt("分摊金额合计与费用金额不一致，差额 ￥")}${allocDiff.value.toFixed(2)}`)
      return
    }
    // 过滤空行并填充orderType
    form.allocations = form.allocations.filter(a => a.orderNo && a.amount > 0).map(a => ({
      ...a,
      orderType: a.orderTypeLabel || a.orderType
    }))
  }

  try {
    if (isEdit.value) {
      await updateExpense(form.id, { ...form })
    } else {
      const res = await saveExpense({ ...form })
      if (res && res.id) form.id = res.id
      if (res && res.expenseNo) form.expenseNo = res.expenseNo
      if (res && res.status) form.status = res.status
    }
    ElMessage.success(tt('保存成功'))
    await loadDetail()
  } catch (e) {
    ElMessage.error(e.message || tt('保存失败'))
  }
}

async function handleSubmit() {
    // 前端必填校验（B功能）
    // 统一校验：头表单 + 明细内表单 + 任何 formRef；失败时精确列出缺失字段名
  const missing = await validateBillRequiredFields({ headerFormRef, rules, $el: document, pageKey: 'operation_expense', model: form })
  if (missing.length) {
    ElNotification.error({
      title: tt('请完善必填字段后再提交'),
      message: h('div', { style: { fontSize: '13px', lineHeight: '1.6' } }, [
        h('div', { style: { marginBottom: '6px', fontWeight: 600, color: '#f56c6c' } }, tt('共 ') + missing.length + tt(' 个字段未填写：')),
        h('ul', { style: { margin: 0, paddingLeft: '18px', color: '#606266' } },
          missing.map(m => h('li', { style: { marginBottom: '2px' } }, [
            h('span', { style: { color: '#303133', fontWeight: 600 } }, m.label || m.prop || tt('未命名字段')),
            m.message && m.message !== tt('该字段不能为空') ? h('span', { style: { color: '#909399', marginLeft: '4px' } }, '（' + m.message + '）') : null
          ].filter(Boolean)))
        )
      ]),
      duration: 5000,
      offset: 60
    })
    // 自动滚动到第一个错误字段（headerFormRef 优先）
    try {
      const target = headerFormRef && headerFormRef.value ? headerFormRef.value : null
      if (target && typeof target.scrollToField === 'function') target.scrollToField(missing[0].prop)
    } catch(_) {}
    return
  }

  if (!form.id) {
    try {
      await handleSave()
      if (!form.id) return
    } catch (_) {
      return
    }
  }

  try {
    const needApproval = await confirmSubmit({ billType: 'OPERATION_EXPENSE', billLabel: '费用分摊单' })
    await submitExpense(form.id)
    ElMessage.success(submitSuccessMessage(needApproval))
    triggerListRefresh('/finance/expenses')
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('提交失败'))
  }
}

async function onApprove() {
  if (!form.id) { ElMessage.warning(tt('请先保存单据')); return }
  try {
    await ElMessageBox.confirm(tt('确认审核该费用单？'), tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await approveExpense(form.id)
    ElMessage.success(tt('已生效'))
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('审核失败'))
  }
}

async function onDelete() {
  if (!form.id) { ElMessage.warning(tt('请先保存单据')); return }
  try {
    await ElMessageBox.confirm(`${tt("确认删除费用单 ")}${form.expenseNo}?`, tt('提示'), { type: 'warning' })
    await deleteExpense(form.id)
    ElMessage.success(tt('删除成功'))
    router.push('/finance/operation-expense')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('删除失败'))
  }
}



function handlePrint() {
  if (!form.id) { ElMessage.warning(tt('请先保存单据再打印')); return }
  printVisible.value = true
}

function showDeptSelect() {
  // 简化处理：部门字段只读展示
  ElMessage.info(tt('部门选择功能待接入'))
}

async function loadDetail() {
  const __detailId = route.params.id || form.id || ''
  if (__detailId) {
    try {
      const res = await getExpense(__detailId)
      Object.assign(form, {
        // 接口主键字段是 expenseId（无 id）→ 必须兼容映射，否则 billId 恒空：
        // 表现为复制提示"请先保存"、保存变成新建重复单、提交/审批按钮不可用（2026-09-28 实测）
        id: res.id || res.expenseId,
        expenseNo: res.expenseNo || '',
        expenseType: res.expenseType || '',
        expenseDate: res.expenseDate,
        amount: res.amount || 0,
        relatedOrderNo: res.relatedOrderNo || '',
        relatedOrderType: res.relatedOrderType || '',
        allocationMethod: res.allocationMethod || '平均分摊',
        remark: res.remark || '',
        status: res.status || '草稿',
        applicantName: res.applicantName || '',
        departmentId: res.departmentId || null,
        departmentName: res.departmentName || '',
        attachmentUrls: res.attachmentUrls || '',
        createdAt: res.createdAt || '',
        createTime: res.createTime || '',
        expenseLines: res.expenseLines || [],
        allocations: res.allocations || []
      })
      // 兼容旧数据
      if ((!form.expenseLines || form.expenseLines.length === 0) && form.expenseType) {
        form.expenseLines = [{ expenseType: form.expenseType, amount: form.amount || 0, remark: '' }]
      }
      if (!form.expenseLines) form.expenseLines = []
      if (!form.allocations) form.allocations = []
      // 为allocations补上orderTypeLabel
      form.allocations.forEach(a => {
        if (!a.orderTypeLabel && a.orderType) {
          a.orderTypeLabel = a.orderType
        }
        if (!a.expenseType && form.expenseType) {
          a.expenseType = form.expenseType
        }
      })
      calcExpenseTotal()
      calcAllocTotal()
    } catch (e) {
      ElMessage.error(e.message || tt('加载失败'))
    }
  } else {
    // 新增时初始化一行费用明细
    form.expenseLines = [{ expenseType: '', amount: 0, remark: '' }]
    // 设置当前用户为申请人
    form.applicantName = localStorage.getItem('username') || ''
    expenseTotal.value = 0
    allocTotal.value = 0
    autoFillDocCodeOnCreate(form, 'expenseNo')
  }
}

// ===== 复制功能 =====
const { applyCopy } = useCopyBill({
  route,
  loadFn: getExpense,
  form,
  noField: 'expenseNo'
})

useFieldRequiredMark('operation_expense', '.operation-expense-edit')

onMounted(async () => {
  loadCollapsed()
  await loadOrders()
  const copied = await applyCopy()
  if (!copied) {
    await loadDetail()
  }
})


  // ======== 状态流转：通用占位 handler（样板：与 SaleOrderEdit 对齐） ========
  // 重要：请将下方 TODO_* 占位函数替换为当前模块实际 API 调用（或在上方 API 导入后重命名为这些函数）
  // 通用：取单据ID（覆盖各模块常见字段，兜底 form.id）
  const billId = computed(() => {
    return form.orderId || form.deliveryId || form.inboundId || form.invoiceId || form.contractId
        || form.voucherId || form.requisitionId || form.stockCheckId || form.transferId
        || form.bomId || form.sampleId || form.freeSampleId || form.workOrderId || form.billId || form.id || form.expenseId
  })

  // 弹窗与 reason
  const rejectDialog = ref(false)
  const rejectReason = ref('')
  const voidDialog = ref(false)
  const voidReason = ref('')

  async function onSave(silent, opts = {}) {
    if (form.status && !isDraft.value) { ElMessage.warning(`${tt("当前状态为「")}${form.status}${tt("」, 不可修改")}`); return false }
    if (typeof calcTotal === 'function') calcTotal()
    // 分摊对象类型：编辑器里存的是选项值（PURCHASE_INBOUND 等），落库字段是 orderType；
    // 不在这里回写的话前端会带上新增行时的默认值 PRODUCTION_RECEIPT（与 handleSave 同口径）
    if (Array.isArray(form.allocations) && form.allocations.length > 0) {
      form.allocations = form.allocations
        .filter(a => a.orderNo && Number(a.amount || 0) > 0)
        .map(a => ({ ...a, orderType: a.orderTypeLabel || a.orderType }))
    }
    try {
      if (billId.value) {
        await updateExpense(billId.value, form)
        if (!form.expenseId && form.id) form.expenseId = form.id
      } else {
        const res = await saveExpense(form)
        if (res) {
          // 新建保存返回的是 expenseId（不是 id）：必须回填，否则提交时 billId 为空会直接返回，
          // 且第二次保存会再插一条重复单据
          if (res.expenseId != null) { form.expenseId = res.expenseId; form.id = res.expenseId }
          for (const k of ['orderId','deliveryId','inboundId','invoiceId','contractId','voucherId','requisitionId','stockCheckId','transferId','bomId','sampleId','freeSampleId','workOrderId','billId','id']) {
            if (res[k] != null && form[k] == null) form[k] = res[k]
          }
          const noKeys = ['orderNo','deliveryNo','inboundNo','invoiceNo','contractNo','voucherNo','requisitionNo','stockCheckNo','transferNo','sampleNo','billNo']
          for (const k of noKeys) if (res[k] != null) form[k] = res[k]
          if (res.status) form.status = res.status
          if (res.approvalStatus) form.approvalStatus = res.approvalStatus
        }
        // 新建保存后必须把 id 写回地址栏：否则 loadDetail() 拿不到 id → 提交/保存后页面状态不刷新（历史缺陷）
        if (!opts.skipRouteSync && form.id) { try { await router.replace({ name: 'OperationExpenseEditPage', params: { id: String(form.id) } }) } catch (_) {} }
      }
      triggerListRefresh('/finance/expenses')
    if (!silent) ElMessage.success(tt('保存成功'))
      if (typeof loadDetail === 'function') await loadDetail()
      return true
    } catch (e) { ElMessage.error(e.message || tt('保存失败')); return false }
  }

  async function onSubmit() {
  // 无论新建还是已有草稿，提交前一律先静默保存（把当前页面所见落库，避免提交旧数据）
  const saved = await onSave(true, { skipRouteSync: true })
  if (!saved || !billId.value) return
    try {
      const needApproval = await confirmSubmit({ billType: 'OPERATION_EXPENSE', billLabel: '费用分摊单' })
      await submitExpense(billId.value)
      ElMessage.success(submitSuccessMessage(needApproval))
      await loadDetail()
    } catch (e) {
      if (e !== 'cancel') ElMessage.error(e.message || tt('提交失败'))
    } finally {
      // 提交（或用户取消）后再把新单 id 写回地址栏：此时重建实例无副作用，新实例挂载时会重新拉取最新状态
      if (!route.params.id && billId.value) {
        try { await router.replace({ name: 'OperationExpenseEditPage', params: { id: String(billId.value) } }) } catch (_) {}
      }
    }
  }

  function handleReject() { rejectReason.value = ''; rejectDialog.value = true }
  async function doReject() {
    try {
      await rejectExpense(billId.value, rejectReason.value)
      ElMessage.success(tt('已驳回')); rejectDialog.value = false
      if (typeof loadDetail === 'function') await loadDetail()
    } catch (e) { ElMessage.error(e.message || tt('驳回失败')) }
  }

  async function handleReverseApprove() {
    try {
      await ElMessageBox.confirm(tt('确认重置草稿？重置草稿后单据回到草稿态可修改，需下游未生效'), tt('提示'), { confirmButtonText: tt('确定'), cancelButtonText: tt('取消'), type: 'warning' })
      await reverseApproveExpense(billId.value)
      ElMessage.success(tt('重置草稿成功'))
      if (typeof loadDetail === 'function') await loadDetail()
    } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || tt('重置草稿失败')) }
  }

  async function handleClose() {
    try {
      await ElMessageBox.confirm(tt('确认结案？结案后进入完结态不可修改'), tt('提示'), { confirmButtonText: tt('确定'), cancelButtonText: tt('取消'), type: 'warning' })
      await closeOrderExpense(billId.value)
      ElMessage.success(tt('已结案'))
      if (typeof loadDetail === 'function') await loadDetail()
    } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || tt('结案失败')) }
  }

  async function handleReopen() {
    try {
      await ElMessageBox.confirm(tt('确认反结案？反结案后返回已生效态'), tt('提示'), { confirmButtonText: tt('确定'), cancelButtonText: tt('取消'), type: 'warning' })
      await reopenExpense(billId.value)
      ElMessage.success(tt('反结案成功'))
      if (typeof loadDetail === 'function') await loadDetail()
    } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || tt('反结案失败')) }
  }

  function handleVoid() { voidReason.value = ''; voidDialog.value = true }
  async function doVoid() {
    try {
      await voidExpense(billId.value, voidReason.value)
      ElMessage.success(tt('已作废')); voidDialog.value = false
      if (typeof loadDetail === 'function') await loadDetail()
    } catch (e) { ElMessage.error(e.message || tt('作废失败')) }
  }

  async function handleUnvoid() {
    try {
      await ElMessageBox.confirm(tt('确认反作废？将恢复到作废前状态'), tt('提示'), { confirmButtonText: tt('确定'), cancelButtonText: tt('取消'), type: 'warning' })
      await unvoidExpense(billId.value)
      ElMessage.success(tt('反作废成功'))
      if (typeof loadDetail === 'function') await loadDetail()
    } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || tt('反作废失败')) }
  }

  async function handleDelete() {
    try {
      await ElMessageBox.confirm(tt('确认删除？删除后不可恢复'), tt('警告'), { confirmButtonText: tt('删除'), cancelButtonText: tt('取消'), type: 'error' })
      await deleteExpense(billId.value)
      ElMessage.success(tt('删除成功'))
      if (typeof router !== 'undefined' && router.back) router.back()
    } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || tt('删除失败')) }
  }

  function handleCopy() {
    // 全站统一口径：跳到本单据「新增」页并带上源单，由新增页自动装入源单数据成新草稿
    const id = billId.value
    if (!id) { ElMessage.warning(tt('请先保存单据再复制')); return }
    router.push({ path: '/finance/operation-expense/new', query: { copy: id } })
  }
  // ======== 状态流转 END ========

</script>

<style scoped lang="scss">
.operation-expense-edit {
  padding: 12px 16px;
  background: #f5f7fa;
  min-height: calc(100vh - 60px);
  font-size: 12px;
}

// ===== 顶部标题栏 =====
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding: 10px 14px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .back-btn {
    display: flex;
    align-items: center;
    gap: 4px;
    color: #606266;
    font-size: 12px;
    cursor: pointer;
    padding: 4px 10px;
    border-radius: 4px;
    background: #f5f7fa;
    border: 1px solid #dcdfe6;
    transition: all .2s;
    &:hover {
      color: #409eff;
      border-color: #b3d8ff;
      background: #ecf5ff;
    }
  }

  .title-area {
    .title-row {
      display: flex;
      align-items: center;
      gap: 6px;
    }
  }

  .title-icon { color: #409eff; font-size: 16px; }

  .page-title {
    font-size: 15px;
    font-weight: 600;
    color: #303133;
  }

  .order-no {
    display: flex;
    align-items: center;
    gap: 3px;
    font-size: 11px;
    color: #409eff;
    background: #ecf5ff;
    padding: 2px 8px;
    border-radius: 3px;
    font-family: 'SF Mono', Monaco, monospace;
    font-weight: 500;
  }

  .status-tag {
    transform: scale(0.88);
    transform-origin: left center;
  }

  .header-right { display: flex; gap: 6px; align-items: center; }
}

.more-btn {
  color: #606266;
  &:hover { color: #409eff; border-color: #b3d8ff; }
}

.btn-primary-glow {
  background: linear-gradient(135deg, #409eff 0%, #337ecc 100%);
  border: none;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.3);
  &:hover {
    box-shadow: 0 3px 12px rgba(64, 158, 255, 0.4);
  }
}

// ===== 可折叠卡片 =====
.collapsible-card {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  margin-bottom: 10px;

  &.collapsed {
    .card-header { border-bottom: none; }
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 14px;
    background: #fafbfc;
    border-bottom: 1px solid #e4e7ed;
    cursor: pointer;
    user-select: none;

    .header-left {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .header-right {
      display: flex;
      gap: 6px;
    }

    .collapse-icon {
      color: #909399;
      font-size: 12px;
      transition: transform .25s ease;
      &.active {
        transform: rotate(90deg);
        color: #409eff;
      }
    }

    .card-title {
      font-size: 13px;
      font-weight: 600;
      color: #303133;
    }

    .item-count {
      font-size: 11px;
      color: #909399;
      font-weight: 400;
    }
  }

  .card-body {
    padding: 12px 14px;
    &.no-padding { padding: 0; }
  }
}

// ===== 基本信息 - 上下分组 =====
.base-form {
  .info-section {
    margin-bottom: 10px;
    padding-bottom: 10px;
    border-bottom: 1px dashed #ebeef5;

    &:last-child {
      margin-bottom: 0;
      padding-bottom: 0;
      border-bottom: none;
    }
  }

  .section-title {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    font-weight: 600;
    color: #409eff;
    margin-bottom: 8px;

    .section-bar {
      width: 3px;
      height: 12px;
      background: linear-gradient(180deg, #409eff, #66b1ff);
      border-radius: 2px;
    }
  }

  .section-fields {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 0 12px;
  }

  :deep(.el-form-item) {
    margin-bottom: 6px;
  }

  :deep(.el-form-item__label) {
    font-size: 12px;
    color: #606266;
    line-height: 28px;
  }

  :deep(.el-form-item__content) {
    line-height: 28px;
  }

  .span-all {
    grid-column: span 4;
  }
}

.field-input {
  width: 100%;
  :deep(.el-input__wrapper),
  :deep(.el-select__wrapper) {
    box-shadow: 0 0 0 1px #dcdfe6 inset;
    border-radius: 4px;
    min-height: 28px;
    font-size: 12px;
    padding: 0 8px;
    .el-input__inner { font-size: 12px; line-height: 28px; }
    &:hover { box-shadow: 0 0 0 1px #c6e2ff inset; }
  }
  :deep(.el-input__wrapper.is-focus),
  :deep(.el-select__wrapper.is-focused) {
    box-shadow: 0 0 0 1px #409eff inset;
  }
  :deep(.el-input-number) {
    width: 100%;
    .el-input__wrapper {
      min-height: 28px;
      padding: 0 6px;
    }
  }
  :deep(.el-date-editor) {
    width: 100%;
  }
}

// ===== 差额提示栏 =====
.diff-info-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  font-size: 12px;

  &.balanced {
    background: #f0f9eb;
    border-bottom: 1px solid #e1f3d8;
    color: #67c23a;
  }

  &.unbalanced {
    background: #fef0f0;
    border-bottom: 1px solid #fde2e2;
    color: #f56c6c;
  }

  .diff-label {
    color: #606266;
    font-weight: 500;
  }

  .diff-value {
    font-weight: 600;
  }

  .diff-sep {
    color: #c0c4cc;
    margin: 0 4px;
  }
}

.tip-bar {
  padding: 8px 14px;
  color: #909399;
  font-size: 12px;
  background: #fafbfc;
  border-top: 1px solid #ebeef5;
}

// 分摊明细汇总区里的附加块（差额提示 + 业务提示）：汇总区是 flex 行 → 整块占满一行
.alloc-summary-extra {
  width: 100%;
}

// ===== 明细表格 =====
.table-wrap {
  overflow-x: auto;
  padding: 10px 12px 0;
}

.compact-table {
  min-width: 1180px;
  font-size: 12px;

  :deep(.el-table__header th) {
    background: #f5f7fa !important;
    color: #606266;
    font-weight: 600;
    font-size: 12px;
    padding: 6px 4px;
  }
  :deep(.el-table__body td) {
    padding: 6px 4px;
  }
  :deep(.el-table__row:hover > td) {
    background: #f5f7fa !important;
  }
}

// ===== 金额汇总区 =====
.summary-area {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding: 12px 14px;
  background: #fafbfc;
  border-top: 1px solid #ebeef5;
}

.summary-block {
  flex: 1;
  min-width: 200px;

  .sum-line {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 3px 0;
    font-size: 12px;

    .label { color: #606266; }
    .value {
      color: #303133;
      font-weight: 500;
      font-variant-numeric: tabular-nums;
    }

    &.total {
      padding-top: 5px;
      margin-top: 3px;
      border-top: 1px dashed #dcdfe6;
      font-size: 13px;

      .label { font-weight: 600; color: #303133; }
      .value {
        color: #409eff;
        font-weight: 700;
        font-size: 14px;
      }
    }
  }
}

@media (max-width: 1400px) {
  .base-form .section-fields { grid-template-columns: repeat(3, 1fr); }
}

@media (max-width: 1200px) {
  .base-form .section-fields { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 768px) {
  .operation-expense-edit { padding: 8px; }
  .page-header { flex-direction: column; gap: 8px; align-items: stretch; }
  .base-form .section-fields { grid-template-columns: 1fr; }
  .summary-area { flex-direction: column; }
}
</style>
