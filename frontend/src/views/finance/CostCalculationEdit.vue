<template>
  <div class="cost-calculation-edit">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div class="header-left">
        <div class="back-btn" @click="$router.push('/finance/cost-calculation')">
          <el-icon><ArrowLeft /></el-icon>
          <span>{{ $tt("返回列表") }}</span>
        </div>
        <div class="title-area">
          <div class="title-row">
            <el-icon class="title-icon"><DataAnalysis /></el-icon>
            <span class="page-title">{{ isEdit ? '成本核算单' : '新增成本核算单' }}</span>
            <span v-if="form.calcName" class="order-no">
              <el-icon><Collection /></el-icon>
              {{ form.calcName }}
            </span>
            <el-tag v-if="form.status" size="small" :type="statusType(form.status)" effect="plain" class="status-tag">{{ $dt(form.status) }}</el-tag>
          </div>
        </div>
      </div>
      <div class="header-right">
        <el-button v-permission="perm('view')" v-if="!(!form.orderId && !form.deliveryId && !form.inboundId && !form.invoiceId && !form.voucherId && !form.contractId && !form.requisitionId && !form.stockCheckId && !form.transferId && !form.bomId && !form.sampleId && !form.freeSampleId && !form.workOrderId && !form.billId && !form.id)" @click="changeRecordVisible = true">
          <el-icon><Memo /></el-icon>{{ $tt('操作日志') }}
        </el-button>
        <BillActions bill-type="COST_CALCULATION"
          :status="form.status"
          :close-status="form.closeStatus"
          :supported-actions="supportedActions"
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
          :show-copy="false"
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
        <el-form :model="form" :rules="rules" ref="headerFormRef" label-width="90px" class="base-form">
          <!-- 单据信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>单据信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('核算名称')" prop="calcName" required>
                <el-input v-model="form.calcName" :placeholder="$tt('请输入核算名称')" class="field-input" :disabled="!editable" />
              </el-form-item>
              <el-form-item :label="$tf('核算日期')" prop="calcDate" required>
                <el-date-picker v-model="form.calcDate" type="date" :placeholder="$tt('选择日期')" value-format="YYYY-MM-DD" class="field-input" :disabled="!editable" />
              </el-form-item>
              <el-form-item :label="$tf('核算方法')" prop="calcMethod" required>
                <el-select v-model="form.calcMethod" class="field-input" :disabled="!editable">
                  <el-option :label="$tf('实际成本法')" value="ACTUAL" />
                  <el-option :label="$tf('标准成本法')" value="STANDARD" />
                  <el-option :label="$tf('作业成本法')" value="ABC" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('状态')">
                <el-tag :type="statusType(form.status)" size="default">{{ $dt(form.status || '草稿') }}</el-tag>
              </el-form-item>
              <el-form-item v-if="taxEnabled" :label="$tf('计税方式')">
                <el-select v-model="form.taxCalcMethod" class="field-input" :disabled="!editable" @change="onTaxCalcMethodChange">
                  <el-option v-for="o in taxMethodOptions" :key="o" :label="$tf(o)" :value="o" />
                </el-select>
              </el-form-item>
            </div>
          </div>

          <!-- 商品信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>商品信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('商品')">
                <el-input v-model="form.materialName" :placeholder="$tt('请输入商品名称')" class="field-input" :disabled="!editable" />
              </el-form-item>
              <el-form-item :label="$tf('商品编码')">
                <el-input v-model="form.materialCode" :placeholder="$tt('商品编码')" class="field-input" :disabled="!editable" />
              </el-form-item>
              <el-form-item :label="$tf('数量')" prop="quantity" required>
                <el-input-number v-model="form.quantity" :min="0" :precision="scaleFor('qty')" class="field-input" :disabled="!editable" />
              </el-form-item>
            </div>
          </div>

          <!-- 备注 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>其他
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('备注')" class="span-all">
                <el-input v-model="form.remark" type="textarea" :rows="2" class="field-input" :disabled="!editable" :placeholder="$tt('请输入备注信息')" />
              </el-form-item>
            </div>
          </div>
        </el-form>
      </div>
    </div>

    <!-- 成本明细卡片（通用组件 BillItemsTable：卡片/列设置/动态列 全站共用一套）
         成本汇总块按原位置保留在卡片外，故此处 summary-items 传空、不显示「合计数量」行 -->
    <BillItemsTable
      v-model:collapsed="collapsed.items"
      :items="items"
      page-key="CostCalculationEdit_items"
      title="成本明细"
      subtitle="成本明细"
      col-setting-title="成本明细列设置"
      :columns="detailColumns"
      :custom-columns="[]"
      :readonly="!editable"
      :show-total-qty="false"
      :summary-items="[]"
      @remove="removeItem"
    >
      <template #actions>
        <el-button v-permission="perm('addRow')" size="small" type="primary" @click="addItem" :disabled="!editable">
          <el-icon><Plus /></el-icon>添加行
        </el-button>
      </template>
      <template #cell-amount="{ row }">
        <span class="net-amount">¥{{ calcRowAmount(row) }}</span>
      </template>
    </BillItemsTable>

    <!-- 成本汇总 -->
    <div class="summary-area" v-if="totalCost > 0">
      <div class="summary-block">
        <div class="block-title">{{ $tt("成本汇总") }}</div>
        <div class="sum-line">
          <span class="label">{{ $tt("直接材料：") }}</span>
          <span class="value material-color">¥{{ directMaterialCost.toFixed(2) }}</span>
        </div>
        <div class="sum-line">
          <span class="label">{{ $tt("直接人工：") }}</span>
          <span class="value labor-color">¥{{ directLaborCost.toFixed(2) }}</span>
        </div>
        <div class="sum-line">
          <span class="label">{{ $tt("制造费用：") }}</span>
          <span class="value overhead-color">¥{{ manufacturingOverhead.toFixed(2) }}</span>
        </div>
        <div class="sum-line" v-if="taxEnabled">
          <span class="label">{{ $tt("合计税额：") }}</span>
          <span class="value">¥{{ Number(form.taxAmount || 0).toFixed(2) }}</span>
        </div>
        <div class="sum-line" v-if="taxEnabled">
          <span class="label">{{ $tt("价税合计：") }}</span>
          <span class="value">¥{{ Number(form.netAmount || 0).toFixed(2) }}</span>
        </div>
        <div class="sum-line total">
          <span class="label">{{ $tt("总成本：") }}</span>
          <span class="value">¥{{ totalCost.toFixed(2) }}</span>
        </div>
        <div class="sum-line total">
          <span class="label">{{ $tt("单位成本：") }}</span>
          <span class="value unit-color">{{ unitCost.toFixed(4) }}</span>
        </div>
      </div>
    </div>

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
          type="cost-calculation"
          :disabled="!editable"
          button-text="上传附件"
          tip-text="支持图片、PDF、Word、Excel 等文件"
        />
      </div>
    </div>

    <!-- 审核流转（真实审批进度，通用组件） -->
    <ApprovalFlowTimeline bill-type="COST_CALCULATION" :bill-id="form.id" :status="form.status" />

    <!-- 打印预览弹窗 -->
    <PrintPreviewDialog v-model="printVisible" bill-type="COST_CALCULATION" :bill-id="form.id" />

    <!-- 操作日志弹窗 -->
    <ChangeRecordDialog v-model="changeRecordVisible" bill-type="成本核算" :bill-no="form.calcName || ''" />
  </div>

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
  </template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft, ArrowRight, DataAnalysis, Collection, Plus, Check, TopRight,
  CircleCheck, Printer, Clock, Memo
} from '@element-plus/icons-vue'
import {
  saveCostCalculation, getCostCalculationDetail, confirmCost, calculateCost, deleteCostCalculation,
  rejectCostCalculation, reverseApproveCostCalculation, voidCostCalculation, unvoidCostCalculation,
  closeOrderCostCalculation, reopenCostCalculation
} from '@/api/finance/costCalculation'
import PrintPreviewDialog from '@/components/PrintPreviewDialog.vue'
import ApprovalFlowTimeline from '@/components/bill/ApprovalFlowTimeline.vue'
import FileUploader from '@/components/FileUploader.vue'
import ChangeRecordDialog from '@/components/ChangeRecordDialog.vue'
// 明细通用组件（卡片折叠/列设置/动态列 全站共用一套）
import BillItemsTable from '@/components/bill/BillItemsTable.vue'
import { triggerListRefresh } from '@/composables/useAutoRefresh'
import { useBillActions } from '@/composables/useBillActions'
import { useCopyBill } from '@/composables/useCopyBill'

import * as BillStatus from '@/utils/bill-status'
import { useFieldRequiredMark } from '@/utils/fieldRequiredMark'
import { confirmSubmit, submitSuccessMessage } from '@/utils/submit-confirm'
import BillActions from '@/components/bill/BillActions.vue'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'
// 计税方式（含税价 / 不含税价 / 不计税）：表头驱动明细「含税价 / 不含税价」换算（口径对齐销售订单试点）
import { useSysConfig } from '@/composables/useSysConfig'
import { useCommercialSwitches } from '@/composables/useCommercialSwitches'
import { usePrecision } from '@/composables/usePrecision'

// 数量输入/显示的位数统一读设置中心「数量小数位数」（2026-11-10）
const { scaleFor } = usePrecision()

const perm = usePagePerm('finance:cost-calculation')

// ===== 计税方式（含税价 / 不含税价 / 不计税）：表头驱动明细「含税价 / 不含税价」换算（照抄销售订单试点）=====
const { loadSysConfig, getConfig } = useSysConfig()
const { taxEnabled, ensureLoaded: ensureCommercialSwitches } = useCommercialSwitches()
const taxMethodOptions = ['不含税价', '含税价', '不计税']

/** 历史值归一：价内税→含税价、价外税/空→不含税价；非法值兜底为不含税价 */
function normalizeTaxMethod(v) {
  if (v === '含税价' || v === '价内税') return '含税价'
  if (v === '不计税') return '不计税'
  return '不含税价'
}

/** 默认税率（本页明细无「税率%」列，取设置中心「税率」，默认 13） */
function defaultTaxRate() {
  const r = Number(getConfig('sys.tax.rate', '13'))
  return isNaN(r) ? 13 : r
}

/** 从设置中心取「默认计税方式」与含税总开关 */
async function initTaxSettings() {
  try {
    await ensureCommercialSwitches()
    await loadSysConfig()
  } catch (e) { /* 读取失败保持默认 */ }
  if (!taxEnabled.value) {
    form.taxCalcMethod = '不计税'
  } else if (!form.id) {
    form.taxCalcMethod = normalizeTaxMethod(getConfig('sys.tax.method'))
  } else {
    form.taxCalcMethod = normalizeTaxMethod(form.taxCalcMethod)
  }
}

/** 表头「计税方式」切换 → 全表明细按新口径重算 */
function onTaxCalcMethodChange() {
  ;(items.value || []).forEach(row => calcRowAmount(row))
}

const route = useRoute()
const router = useRouter()
const headerFormRef = ref()
const changeRecordVisible = ref(false)

// 成本明细列定义（通用组件 BillItemsTable 消费）：editor = 单元格控件，type = 纯展示格式；
// 列声明顺序 = 改造前模板的渲染顺序（成本类型/成本项目/商品/单位/数量/单价 + 金额）；
// 金额列为按行实时计算值（calcRowAmount），由页面提供插槽（#cell-amount）
const detailColumns = [
  { prop: 'costType', label: '成本类型', width: 130, editor: 'select', clearable: false,
    options: [
      { value: 'DIRECT_MATERIAL', label: '直接材料' },
      { value: 'DIRECT_LABOR', label: '直接人工' },
      { value: 'OVERHEAD', label: '制造费用' }
    ] },
  { prop: 'costName', label: '成本项目', width: 130, editor: 'text' },
  { prop: 'materialName', label: '商品', width: 120, editor: 'text' },
  { prop: 'unit', label: '单位', width: 80, editor: 'text' },
  { prop: 'quantity', label: '数量', width: 110, align: 'right', editor: 'number', precision: 4, min: 0, change: (row) => calcRowAmount(row) },
  { prop: 'unitPrice', label: '单价', width: 110, align: 'right', editor: 'number', precision: 4, min: 0, change: (row) => calcRowAmount(row) },
  { prop: 'priceWithTax', label: '含税价', width: 95, align: 'right', type: 'amount4' },
  { prop: 'priceWithoutTax', label: '不含税价', width: 95, align: 'right', type: 'amount4' },
  { prop: 'amount', label: '金额', width: 120, align: 'right', type: 'calcAmount' },
  { prop: 'netAmount', label: '价税合计', width: 120, align: 'right', type: 'amount2', cellClass: 'net-amount' }
]

const isEdit = computed(() => !!route.params.id)

// 仅草稿态允许编辑（单号/业务字段/自定义字段）；已生效/待审批等只读
// 折叠状态
const STORAGE_KEY = 'cost-calculation-edit-collapsed'
const collapsed = reactive({
  base: true,
  items: true,
  attach: true,
  audit: false
})

function loadCollapsed() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) {
      const parsed = JSON.parse(saved)
      Object.keys(parsed).forEach(k => { if (k in collapsed) collapsed[k] = parsed[k] })
    }
  } catch (e) {}
}
function saveCollapsed() {
  try { localStorage.setItem(STORAGE_KEY, JSON.stringify(collapsed)) } catch (e) {}
}
function toggleCard(key) {
  collapsed[key] = !collapsed[key]
  saveCollapsed()
}

// 明细卡片折叠由通用组件 v-model 驱动，这里监听后持久化
watch(() => collapsed.items, () => saveCollapsed())

// 表单数据
const form = reactive({
  id: null,
  calcName: '',
  calcDate: new Date().toISOString().split('T')[0],
  materialId: null,
  materialCode: '',
  materialName: '',
  quantity: 1,
  calcMethod: 'ACTUAL',
  // 计税方式：表头驱动明细「含税价 / 不含税价」换算；taxAmount/netAmount 为汇总（本页无落库列，仅界面口径）
  taxCalcMethod: '不含税价', taxAmount: 0, netAmount: 0,
  remark: '',
  status: '',
  attachmentUrls: '',
  createdAt: '',
  confirmedAt: ''
})


const isDraft = computed(() => BillStatus.canEdit(form.status, form.closeStatus))
const items = ref([
  { costType: 'DIRECT_MATERIAL', costName: '', materialName: '', unit: '', quantity: null, unitPrice: null, priceWithTax: null, priceWithoutTax: null },
  { costType: 'DIRECT_LABOR', costName: '', materialName: '', unit: '', quantity: null, unitPrice: null, priceWithTax: null, priceWithoutTax: null },
  { costType: 'OVERHEAD', costName: '', materialName: '', unit: '', quantity: null, unitPrice: null, priceWithTax: null, priceWithoutTax: null }
])

const rules = {
  calcName: [{ required: true, message: tt('请输入核算名称'), trigger: 'blur' }],
  calcDate: [{ required: true, message: tt('请选择核算日期'), trigger: 'change' }],
  calcMethod: [{ required: true, message: tt('请选择核算方法'), trigger: 'change' }],
  quantity: [{ required: true, message: tt('请输入数量'), trigger: 'blur' }]
}

// 是否可编辑（草稿或空状态可编辑）
const editable = computed(() => !form.status || form.status === '草稿')

function statusType(s) {
  return { '草稿': 'info', '已核算': 'warning', '已确认': 'success' }[s] || 'info'
}

/** 纯函数：按表头「计税方式」把「单价」换算为含税价 / 不含税价，并给出不含税金额（不改状态） */
function rowTaxSplit(row) {
  const qty = Number(row.quantity || 0)
  const price = Number(row.unitPrice || 0)
  const taxRate = defaultTaxRate()
  const method = form.taxCalcMethod || '不含税价'
  const factor = 1 + taxRate / 100
  const noTax = method === '不计税'
  const inclusive = method === '含税价'
  let priceWithTax = price
  let priceWithoutTax = price
  if (inclusive) {
    priceWithTax = price
    priceWithoutTax = factor ? price / factor : price
  } else if (noTax) {
    priceWithTax = price
    priceWithoutTax = price
  } else {
    priceWithoutTax = price
    priceWithTax = price * factor
  }
  // 金额一律按「不含税」口径参与（与销售订单试点一致）
  const amount = inclusive ? (price * qty) / factor : price * qty
  return { priceWithTax, priceWithoutTax, amount }
}

const calcRowAmount = (row) => {
  const s = rowTaxSplit(row)
  row.priceWithTax = Number(s.priceWithTax.toFixed(6))
  row.priceWithoutTax = Number(s.priceWithoutTax.toFixed(6))
  // 行级价税合计 = 含税价 × 数量；税额 = 价税合计 − 金额（2026-11-23 补齐）
  const qty = Number(row.quantity || 0)
  row.netAmount = Number((s.priceWithTax * qty).toFixed(2))
  row.taxAmount = Number((row.netAmount - s.amount).toFixed(2))
  return s.amount.toFixed(4)
}

/** 汇总税额 / 价税合计（保存与重开回显共用，避免只在保存时算一次） */
function recalcSummary() {
  let taxAmount = 0
  let netAmount = 0
  ;(items.value || []).forEach(row => {
    calcRowAmount(row)
    taxAmount += Number(row.taxAmount || 0)
    netAmount += Number(row.netAmount || 0)
  })
  form.taxAmount = Number(taxAmount.toFixed(2))
  form.netAmount = Number(netAmount.toFixed(2))
}

/**
 * 由落库的「含税价 / 不含税价」两列反推表头「计税方式」（本表无 tax_calc_method 列，用于重开回显）。
 * 单价与两列的关系：含税价 → 含税价=单价；不含税价 → 不含税价=单价；不计税 → 两列都=单价。
 */
function inferTaxMethodFromItems(list) {
  const rows = (list || []).filter(r => r && r.unitPrice != null && Number(r.unitPrice) !== 0)
  if (!rows.length) return null
  const r = rows[0]
  const p = Number(r.unitPrice || 0)
  const wp = Number(r.priceWithTax != null ? r.priceWithTax : p)
  const wo = Number(r.priceWithoutTax != null ? r.priceWithoutTax : p)
  const eq = (a, b) => Math.abs(a - b) < 1e-6
  if (eq(wp, p) && eq(wo, p)) return '不计税'
  if (eq(wp, p)) return '含税价'
  if (eq(wo, p)) return '不含税价'
  return null
}

const directMaterialCost = computed(() => {
  return items.value
    .filter(item => item.costType === 'DIRECT_MATERIAL')
    .reduce((sum, item) => sum + rowTaxSplit(item).amount, 0)
})

const directLaborCost = computed(() => {
  return items.value
    .filter(item => item.costType === 'DIRECT_LABOR')
    .reduce((sum, item) => sum + rowTaxSplit(item).amount, 0)
})

const manufacturingOverhead = computed(() => {
  return items.value
    .filter(item => item.costType === 'OVERHEAD')
    .reduce((sum, item) => sum + rowTaxSplit(item).amount, 0)
})

const totalCost = computed(() => {
  return directMaterialCost.value + directLaborCost.value + manufacturingOverhead.value
})

const unitCost = computed(() => {
  const qty = Number(form.quantity || 1)
  return qty > 0 ? totalCost.value / qty : totalCost.value
})

const addItem = () => {
  items.value.push({
    costType: 'DIRECT_MATERIAL',
    costName: '',
    materialName: '',
    unit: '',
    quantity: null,
    unitPrice: null,
    priceWithTax: null,
    priceWithoutTax: null
  })
}

// 删行由通用组件负责（组件先 splice 再 emit remove）：在此兜底原页「至少保留一行明细」约束
const removeItem = (index) => {
  if (items.value.length === 0) {
    items.value.splice(index, 0, { costType: 'DIRECT_MATERIAL', costName: '', materialName: '', unit: '', quantity: null, unitPrice: null, priceWithTax: null, priceWithoutTax: null })
    ElMessage.warning(tt('至少保留一行明细'))
  }
}

// ===== 保存 =====
async function onSave(targetStatus, silent, opts = {}) {
  // 宽松草稿保存：不校验必填，允许空商品名（可后续补全再提交）

  // 先按当前口径重算明细（含税价/不含税价/金额/税额/价税合计），再汇总
  recalcSummary()
  const itemList = items.value.filter(item => item).map(item => ({
    costType: item.costType,
    costName: item.costName,
    materialName: item.materialName,
    unit: item.unit,
    quantity: item.quantity,
    unitPrice: item.unitPrice,
    // 含税价/不含税价/金额/价税合计（按表头「计税方式」口径换算，与销售订单试点一致）
    priceWithTax: item.priceWithTax,
    priceWithoutTax: item.priceWithoutTax,
    amount: Number(calcRowAmount(item)),
    netAmount: Number(item.netAmount || 0)
  }))

  try {
    const payload = { ...form, items: itemList, status: targetStatus || '草稿' }
    const res = await saveCostCalculation(payload)
    if (res && res.id) form.id = res.id
    if (res && res.status) form.status = res.status
    // 新增态保存（草稿）后必须把新单 id 写回地址栏：否则地址栏仍停在 /new，用户按 F5 会丢掉刚保存的草稿。
    // skipRouteSync：提交链路里先跳过（写回会改变布局 keep-alive key(fullPath) → 组件实例重建），由 onSubmit 的 finally 统一写回。
    if (targetStatus !== '已核算' && !opts.skipRouteSync && !route.params.id && form.id) {
      try { await router.replace({ name: 'CostCalculationEdit', params: { id: String(form.id) } }) } catch (_) {}
    }
    if (!silent) ElMessage.success(targetStatus === tt('已核算') ? tt('已提交核算') : tt('保存成功'))
    // 保存成功后跳转回列表
    if (targetStatus === '已核算') {
      router.push('/finance/cost-calculation')
    }
    return true
  } catch (e) {
    ElMessage.error(e.message || tt('保存失败'))
    return false
  }
}

async function onSubmit() {
  // 无论新建还是已有草稿，提交前一律先静默保存（把当前页面所见落库，避免提交旧数据）
  // skipRouteSync：提交链路里先跳过写回地址栏，避免 keep-alive key 变化重建组件实例（见本函数 finally）
  const saved = await onSave(undefined, true, { skipRouteSync: true })
  if (!saved || !form.id) return
  try {
    const needApproval = await confirmSubmit({ billType: '', billLabel: '成本核算单', billNo: form.calcNo })
    // 后端状态机：草稿 →(calculate) 已核算 →(confirm) 已确认，两步串行
    if (form.status === '草稿') await calculateCost(form.id)
    await confirmCost(form.id)
    form.status = '已确认'
    ElMessage.success(submitSuccessMessage(needApproval))
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('提交失败'))
  } finally {
    // 新建单据（URL=/new）提交（或用户取消）后再跳到编辑URL：此时实例重建无副作用，新实例挂载时会按 id 重新拉取最新状态
    if (!route.params.id && form.id) {
      try { await router.replace({ name: 'CostCalculationEdit', params: { id: String(form.id) } }) } catch (_) {}
    }
  }
}



// ===== 确认 =====
async function onConfirm() {
  if (!form.id) { ElMessage.warning(tt('请先保存单据')); return }
  try {
    await ElMessageBox.confirm(tt('确认该成本核算单？确认后不可修改'), tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await confirmCost(form.id)
    form.status = '已确认'
    ElMessage.success(tt('确认成功'))
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('确认失败'))
  }
}

// ===== 打印 =====
const printVisible = ref(false)
function handlePrint() {
  if (!form.id) { ElMessage.warning(tt('请先保存单据再打印')); return }
  printVisible.value = true
}

// ===== 加载详情 =====
async function loadDetail() {
  if (!route.params.id) return
  try {
    const data = await getCostCalculationDetail(route.params.id)
    form.id = data.id
    form.calcName = data.calcName
    form.calcDate = data.calcDate
    form.materialId = data.materialId
    form.materialCode = data.materialCode
    form.materialName = data.materialName
    form.quantity = data.quantity
    form.calcMethod = data.calcMethod
    form.remark = data.remark
    form.status = data.status
    form.attachmentUrls = data.attachmentUrls || ''
    form.createdAt = data.createdAt || ''
    form.confirmedAt = data.confirmedAt || ''
    items.value = (data.items || []).filter(it => it)
    // 重开回显：先由落库的两列反推表头计税方式，再按设置中心默认/总开关归一，最后按口径回算
    const inferred = inferTaxMethodFromItems(items.value)
    if (inferred) form.taxCalcMethod = inferred
    await initTaxSettings()
    // 重开回显：明细逐行重算并同步汇总税额/价税合计（后端已落库 net_amount，此处保证界面一致）
    recalcSummary()
    // 如果是已核算或已确认状态，自动只读
    if (data.status === '已核算' || data.status === '已确认') {
      // editable 已基于 status 计算，无需额外处理
    }
  } catch (e) {
    ElMessage.error(e.message || tt('加载详情失败'))
  }
}

// ===== 统一单据操作（审核/驳回/作废/删除等）=====
const {
  supportedActions,
  rejectDialog, rejectReason, doReject,
  voidDialog, voidReason, doVoid,
  onApprove, handleReject, handleReverseApprove,
  handleClose, handleReopen, handleVoid, handleUnvoid,
  handleDelete, handleCopy
} = useBillActions({
  form,
  getId: () => form.id,
  api: {
    approve: confirmCost,
    reject: rejectCostCalculation,
    reverseApprove: reverseApproveCostCalculation,
    close: closeOrderCostCalculation,
    reopen: reopenCostCalculation,
    void: voidCostCalculation,
    unvoid: unvoidCostCalculation,
    delete: deleteCostCalculation
  },
  reload: loadDetail,
  router,
  listPath: '/finance/cost-calculation',
  billLabel: '成本核算单'
})

// ===== 复制功能（URL 带 ?copy=id 时自动带入源单数据）=====
const { applyCopy } = useCopyBill({
  route,
  loadFn: getCostCalculationDetail,
  form,
  idField: 'id'
})

useFieldRequiredMark('cost_calculation', '.cost-calculation-edit')

onMounted(async () => {
  // 全站商业口径总开关（含税业务）：打开页面先装载一次，决定「计税方式」是否显示
  await ensureCommercialSwitches()
  loadCollapsed()
  const copied = await applyCopy()
  if (!copied) {
    loadDetail()
  }
})


  </script>

<style scoped lang="scss">
.cost-calculation-edit {
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
    max-width: 280px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .status-tag {
    transform: scale(0.88);
    transform-origin: left center;
  }

  .header-right { display: flex; gap: 6px; align-items: center; }
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
      align-items: center;
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

// ===== 基本信息 =====
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

// ===== 明细表格 =====
.table-wrap {
  overflow-x: auto;
  padding: 10px 12px 0;
}

.compact-table {
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

.net-amount {
  color: #409eff;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

// ===== 明细子表标题与列设置按钮 =====
.detail-subtable-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  padding: 0 4px;

  .subtable-title {
    font-size: 14px;
    font-weight: bold;
    color: #303133;

    &::before {
      content: '';
      display: inline-block;
      width: 3px;
      height: 14px;
      background: #409eff;
      margin-right: 6px;
      vertical-align: middle;
    }
  }
}

// ===== 成本汇总区 =====
.summary-area {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  margin-bottom: 10px;
}

.summary-block {
  flex: 1;
  min-width: 280px;

  .block-title {
    font-size: 11px;
    font-weight: 600;
    color: #409eff;
    margin-bottom: 6px;
    padding-bottom: 4px;
    border-bottom: 1px solid #ebeef5;
  }

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

.material-color { color: #409eff !important; }
.labor-color { color: #67c23a !important; }
.overhead-color { color: #e6a23c !important; }
.unit-color { color: #409eff !important; }

@media (max-width: 1400px) {
  .base-form .section-fields { grid-template-columns: repeat(3, 1fr); }
}

@media (max-width: 1200px) {
  .base-form .section-fields { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 768px) {
  .cost-calculation-edit { padding: 8px; }
  .page-header { flex-direction: column; gap: 8px; align-items: stretch; }
  .base-form .section-fields { grid-template-columns: 1fr; }
  .summary-area { flex-direction: column; }
}
</style>
