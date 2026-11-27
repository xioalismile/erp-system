<template>
  <div class="subcontract-return-edit">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div class="header-left">
        <div class="back-btn" @click="$router.push('/subcontract/returns')">
          <el-icon><ArrowLeft /></el-icon>
          <span>{{ $tt("返回列表") }}</span>
        </div>
        <div class="title-area">
          <div class="title-row">
            <el-icon class="title-icon"><Document /></el-icon>
            <span class="page-title">{{ isEdit ? '委外退料单' : '新增委外退料单' }}</span>
            <span v-if="form.returnNo" class="order-no">
              <el-icon><Collection /></el-icon>
              {{ form.returnNo }}
            </span>
            <el-tag v-if="form.status !== undefined && form.status !== null" size="small" :type="statusType(form.status)" effect="plain" class="status-tag">{{ $dt(statusText(form.status)) }}</el-tag>
          </div>
        </div>
      </div>
      <div class="header-right">
        <el-button v-permission="perm('view')" v-if="!(!form.orderId && !form.deliveryId && !form.inboundId && !form.invoiceId && !form.voucherId && !form.contractId && !form.requisitionId && !form.stockCheckId && !form.transferId && !form.bomId && !form.sampleId && !form.freeSampleId && !form.workOrderId && !form.billId && !form.id)" @click="changeRecordVisible = true">
          <el-icon><Memo /></el-icon>{{ $tt('操作日志') }}
        </el-button>
        <BillActions bill-type="SUBCONTRACT_RETURN"
          :status="form.status"
          :close-status="form.closeStatus"
          :is-new="!form.orderId && !form.deliveryId && !form.inboundId && !form.invoiceId && !form.voucherId && !form.contractId && !form.requisitionId && !form.stockCheckId && !form.transferId && !form.bomId && !form.sampleId && !form.freeSampleId && !form.workOrderId && !form.billId && !form.id"
          :supported-actions="supportedActions"
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

    <!-- 顶级页签：单据 / 来源单据 / 去向单据（来源去向为通用组件，未登记的单据类型不显示） -->
    <el-tabs v-model="topTab" class="bill-top-tabs">
      <el-tab-pane :label="$tf('单据')" name="doc">

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
              <el-form-item :label="$tf('退料单号')" v-if="isFieldVisible('returnNo')" :required="isFieldRequired('returnNo')">
                <el-input v-model="form.returnNo" :placeholder="docCodePlaceholder" :disabled="!isDraft" class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('退料日期')" prop="returnDate" required v-if="isFieldVisible('returnDate')">
                <el-date-picker v-model="form.returnDate" type="date" value-format="YYYY-MM-DD" class="field-input" :disabled="!isStatus(form.status, 0)" />
              </el-form-item>
              <el-form-item :label="$tf('状态')" v-if="isFieldVisible('status')" :required="isFieldRequired('status')">
                <el-input :value="statusText(form.status)" disabled class="field-input" />
              </el-form-item>
            </div>
          </div>

          <!-- 委外来源 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>委外来源
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('委外加工单')" prop="orderId" required v-if="isFieldVisible('orderId')">
                <el-select v-model="form.orderId" filterable :placeholder="$tt('选择委外加工单')" class="field-input" :disabled="!isStatus(form.status, 0)" @change="onOrderChange">
                  <el-option v-for="o in orderOptions" :key="o.id" :label="o.orderNo || o.billNo" :value="o.id" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('委外单号')" v-if="isFieldVisible('orderNo')" :required="isFieldRequired('orderNo')">
                <el-input v-model="form.orderNo" :placeholder="$tt('选择委外单后自动带出')" disabled class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('供应商')" v-if="isFieldVisible('supplierName')" :required="isFieldRequired('supplierName')">
                <el-input v-model="form.supplierName" :placeholder="$tt('选择委外单后自动带出')" disabled class="field-input" />
              </el-form-item>
            </div>
          </div>

          <!-- 其他 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>其他
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('备注')" class="span-all" v-if="isFieldVisible('remark')" :required="isFieldRequired('remark')">
                <el-input v-model="form.remark" type="textarea" :rows="2" class="field-input" :disabled="!isStatus(form.status, 0)" :placeholder="$tt('退料原因说明')" />
              </el-form-item>
            </div>
          </div>

          <!-- 自定义字段（来自设置中心自定义字段定义，按所属分区成组渲染） -->
          <template v-if="headerCustomSections.length > 0">
            <div class="info-section" v-for="section in headerCustomSections" :key="section.title">
              <div class="section-title">
                <span class="section-bar"></span>{{ section.title }}
              </div>
              <div class="section-fields">
                <el-form-item
                  v-for="field in section.fields"
                  :key="field.prop"
                  :label="$tf(field.label)"
                  :required="field.required || isFieldRequired(field.prop)"
                >
                  <el-select
                    v-if="field.type === 'select'"
                    v-model="form[field.prop]"
                    :placeholder="field.placeholder || '请选择'"
                    :filterable="field.filterable !== false"
                    class="field-input"
                    :disabled="!isDraft"
                  >
                    <el-option v-for="opt in (field.options || [])" :key="opt.value" :label="$tf(opt.label)" :value="opt.value" />
                  </el-select>
                  <el-input-number
                    v-else-if="field.type === 'number'"
                    v-model="form[field.prop]"
                    :precision="field.precision"
                    :min="field.min !== undefined ? field.min : 0"
                    :step="0.1"
                    controls-position="right"
                    class="field-input"
                    :disabled="!isDraft"
                  />
                  <el-switch
                    v-else-if="field.type === 'switch'"
                    v-model="form[field.prop]"
                    :active-value="field.activeValue !== undefined ? field.activeValue : 1"
                    :inactive-value="field.inactiveValue !== undefined ? field.inactiveValue : 0"
                    :disabled="!isDraft"
                  />
                  <el-date-picker
                    v-else-if="field.type === 'date'"
                    v-model="form[field.prop]"
                    type="date"
                    value-format="YYYY-MM-DD"
                    :placeholder="field.placeholder || '选择日期'"
                    class="field-input"
                    :disabled="!isDraft"
                  />
                  <el-input
                    v-else-if="field.type === 'textarea'"
                    v-model="form[field.prop]"
                    type="textarea"
                    :rows="field.rows || 3"
                    :placeholder="field.placeholder || '请输入'"
                    class="field-input"
                    :disabled="!isDraft"
                  />
                  <el-input
                    v-else
                    v-model="form[field.prop]"
                    :placeholder="field.placeholder || '请输入'"
                    class="field-input"
                    :disabled="!isDraft"
                  />
                </el-form-item>
              </div>
            </div>
          </template>
        </el-form>
      </div>
    </div>

    <!-- 明细卡片（通用组件 BillItemsTable：卡片/列设置/动态列/自定义字段列/汇总 全站共用一套） -->
    <BillItemsTable
      v-model:collapsed="collapsed.items"
      :items="form.items"
      page-key="SubcontractReturnEdit_items"
      title="退料明细"
      col-setting-title="退料明细列设置"
      :columns="detailColumns"
      :custom-columns="itemCustomColumns"
      :readonly="!isStatus(form.status, 0)"
      :show-total-qty="false"
      :summary-items="summaryItems"
      :source-link-click="goToSourceBill"
      @remove="calcTotal"
    >
      <template #actions>
        <!-- 源单是委外发料单，状态是数字（2=已生效），选单接口按数字状态过滤 -->
        <SourcePicker ref="sourcePickerRef" target-type="SubcontractReturn" :source-status="[2]" @pick="onSourcePicked" :disabled="!isStatus(form.status, 0)" btn-type="success" size="small" />
        <el-button v-permission="perm('addRow')" size="small" type="primary" @click="addLine" :disabled="!isStatus(form.status, 0)">
          <el-icon><Plus /></el-icon>添加明细
        </el-button>
      </template>
      <template #cell-materialId="{ row }">
        <el-select
          v-model="row.materialId"
          filterable
          :placeholder="materialSelectPlaceholder"
          @change="val => onLineMaterialChange(row, val)"
          :disabled="!isStatus(form.status, 0)"
          :filter-method="(val) => materialFilterMethod(val, row)"
          class="table-select"
          size="small"
          style="width:100%"
          :teleported="true"
          :popper-options="{ strategy: 'fixed' }"
          popper-class="material-select-popper">
          <el-option
            v-for="m in (row._filteredMaterials || materialOptions)"
            :key="m.materialId"
            :label="`${m.materialName} (${m.materialCode})`"
            :value="m.materialId">
            <span class="opt-code">{{ m.materialCode }}</span>
            <span class="opt-name">{{ m.materialName }}</span>
            <span v-for="cf in mcf.dropText(m)" :key="cf.key" class="opt-cf">{{ cf.label }}：{{ cf.value }}</span>
            <span v-if="m.model" class="opt-model">{{ m.model }}</span>
          </el-option>
        </el-select>
      </template>
      <!-- 单位列由明细通用组件默认渲染（统一单位单元格：候选=该物料换算单位），2026-11-11 统一 -->
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
          type="subcontract-return"
          :disabled="!isStatus(form.status, 0)"
          button-text="上传附件"
          tip-text="支持图片、PDF、Word、Excel 等文件"
        />
      </div>
    </div>

    <!-- 审核流转（真实审批进度，通用组件） -->
    <ApprovalFlowTimeline bill-type="SUBCONTRACT_RETURN" :bill-id="form.id" :status="form.status" />

    <!-- 打印预览弹窗 -->
    <PrintPreviewDialog v-model="printVisible" bill-type="SUBCONTRACT_RETURN" :bill-id="form.id" />

    <!-- 操作日志弹窗 -->
    <ChangeRecordDialog v-model="changeRecordVisible" bill-type="委外退料单" :bill-no="form.returnNo || ''" />
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
      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('来源单据')" name="source" :disabled="!form.id">
        <BillLinkPanel v-if="topTab === 'source'" bill-type="SUBCONTRACT_RETURN" :bill-id="form.id" mode="source" />
      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('去向单据')" name="target" :disabled="!form.id">
        <BillLinkPanel v-if="topTab === 'target'" bill-type="SUBCONTRACT_RETURN" :bill-id="form.id" mode="target" />
      </el-tab-pane>
    </el-tabs>
  </div>
  </template>

<script setup>
import { ref, reactive, computed, onMounted, watch, h} from 'vue'
import BillLinkPanel from '@/components/bill/BillLinkPanel.vue'
import { isBillLinkRegistered } from '@/api/billLink'
import { useBaseDataRefresh } from '@/composables/useBaseDataRefresh'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification} from 'element-plus'
import { ArrowLeft, Document, Collection, Plus, Link, ArrowRight, Check, TopRight, ArrowDown, CircleCheck, RefreshLeft, Delete, Printer, Share, Clock, Memo } from '@element-plus/icons-vue'
import {
  getSubcontractReturn, saveSubcontractReturn, updateSubcontractReturn,
  deleteSubcontractReturn, submitSubcontractReturn, approveSubcontractReturn,
  reverseApproveSubcontractReturn, listSubcontract,
  rejectSubcontractReturn, voidSubcontractReturn, unvoidSubcontractReturn
} from '@/api/production/subcontract'
import { listAllMaterial } from '@/api/basedata/material'
import { listAllWarehouse } from '@/api/basedata/warehouse'
import FileUploader from '@/components/FileUploader.vue'
import SourcePicker from '@/components/SourcePicker.vue'
import ApprovalFlowTimeline from '@/components/bill/ApprovalFlowTimeline.vue'
import PrintPreviewDialog from '@/components/PrintPreviewDialog.vue'
import ChangeRecordDialog from '@/components/ChangeRecordDialog.vue'
import { useAutoDocCode } from '@/composables/useAutoDocCode'
import { useBillActions } from '@/composables/useBillActions'
import { useCopyBill } from '@/composables/useCopyBill'

import * as BillStatus from '@/utils/bill-status'
import BillActions from '@/components/bill/BillActions.vue'
import { validateBillRequiredFields } from '@/utils/billValidation'
import { useFieldRequiredMark } from '@/utils/fieldRequiredMark'
import { confirmSubmit, submitSuccessMessage } from '@/utils/submit-confirm'
import { triggerListRefresh } from '@/composables/useAutoRefresh'
import { tt } from '@/i18n'
import { useMaterialCustomFields } from '@/composables/useMaterialCustomFields'
// 物料下拉的输入过滤 + 背景提示文字（通用件）
import { filterMaterials, buildMaterialPlaceholder } from '@/utils/materialFilter'
import { useBillLineStock, BILL_STOCK_COLUMNS } from '@/composables/useBillLineStock'
import { useCommercialSwitches } from '@/composables/useCommercialSwitches'
import { usePagePerm } from '@/utils/pagePerm'
import { useFieldPermission } from '@/composables/useFieldPermission'
// 明细通用组件（卡片折叠/列设置/动态列/自定义字段列/汇总 全站共用一套）
import BillItemsTable from '@/components/bill/BillItemsTable.vue'
// 自定义字段（表头 + 明细）：加载定义/回显值/保存值/明细主键对齐
import {
  loadCustomFieldDefinitions,
  loadCustomFieldValues,
  saveCustomFieldValues,
  batchLoadCustomFieldValues,
  customFieldsToTableColumns,
  remapItemIdsAfterSave
} from '@/composables/useCustomField'

const perm = usePagePerm('subcontract:returns')

// 字段权限（与销售订单同口径）：无配置时字段全可见、非必填，页面默认行为不变
const { isFieldVisible, isFieldRequired } = useFieldPermission('subcontract_return')

// 全站商业口径总开关（设置中心 → 启用含税业务 / 启用外币业务）
const { taxEnabled, foreignCurrencyEnabled, ensureLoaded: ensureCommercialSwitches } = useCommercialSwitches()

// 退料明细列定义（通用组件 BillItemsTable 消费）：editor = 单元格控件，type = 纯展示格式；
// 库存三列用共享 BILL_STOCK_COLUMNS；物料/单位列由页面提供插槽（#cell-<prop>）
const detailColumns = [
  { prop: 'materialId', label: '商品名称', minWidth: 220 },
  { prop: 'materialCode', label: '商品编号', width: 130 },
  ...BILL_STOCK_COLUMNS,
  { prop: 'sourceBillNo', label: '来源单号', minWidth: 140, type: 'sourceLink' },
  { prop: 'unit', label: '单位', width: 120 },
  { prop: 'quantity', label: '数量', width: 140, editor: 'number', precision: 6, change: () => calcTotal() },
  { prop: 'warehouseId', label: '仓库', width: 180, editor: 'select', placeholder: '选择仓库', clearable: false,
    options: () => (warehouseOptions.value || []).map(w => ({ value: w.stockId, label: w.stockName })) },
  { prop: 'remark', label: '备注', minWidth: 140, editor: 'text' }
]
// 汇总区（通用组件按此渲染，口径与迁移前逐项一致：明细行数 + 总数量）
const summaryItems = computed(() => [
  { label: '明细行数：', value: form.items.length },
  { label: '总数量：', value: Number(form.totalQuantity || 0), total: true }
])
useBillLineStock(() => form.items)

const route = useRoute()
const router = useRouter()
const headerFormRef = ref()
const sourcePickerRef = ref()

const isEdit = computed(() => !!route.params.id)

// 仅草稿态允许编辑（单号/业务字段/自定义字段）；已生效/待审批等只读（数字状态体系：0=草稿）
const { docCodeDisabled, docCodePlaceholder, autoFillDocCodeOnCreate } = useAutoDocCode('SUBCONTRACT_RETURN')

const STORAGE_KEY = 'subcontract-return-edit-collapsed'

const collapsed = reactive({
  base: true,
  items: true,
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

// 明细卡片折叠由通用组件 v-model 驱动，这里监听后持久化
watch(() => collapsed.items, () => saveCollapsed())

const materialOptions = ref([])
// 物料自定义字段：行内下拉附加展示（逻辑集中在 composable）
const mcf = useMaterialCustomFields()
// 参与搜索的物料自定义字段：勾了「可搜索」的 ∪（下拉显示开关开启且勾了「下拉显示」的）
const materialSearchableFields = computed(() => (mcf.allFields.value || []).filter(f =>
  f && (f.isSearch === 1 || (mcf.enabled.value && f.isDropShow === 1))))
// 物料下拉的背景提示文字：声明可搜索范围；没有可搜索的自定义字段时原样返回「选择物料」（零回归）
const materialSelectPlaceholder = computed(() => buildMaterialPlaceholder(tt('选择物料'), materialSearchableFields.value))
/** 明细行「物料」下拉：输入即过滤（编码/名称/规格 + 自定义字段值/字段名） */
function materialFilterMethod(val, row) {
  if (!val) { row._filteredMaterials = null; return }
  // 用「已预取全量值」的 mcf.valueMap，不能只用已选中物料的值表，否则搜不到
  row._filteredMaterials = filterMaterials(materialOptions.value, val, mcf.valueMap.value, materialSearchableFields.value)
}
const warehouseOptions = ref([])
const orderOptions = ref([])

const printVisible = ref(false)
const changeRecordVisible = ref(false)

const form = reactive({
  id: null,
  returnNo: '',
  orderId: null,
  orderNo: '',
  supplierId: null,
  supplierName: '',
  returnDate: new Date().toISOString().slice(0, 10),
  totalQuantity: 0,
  status: 0,
  remark: '',
  pickId: null,
  pickNo: '',
  attachmentUrls: '',
  sourceBillType: '',
  sourceBillNo: '',
  createdAt: '',
  createTime: '',
  items: []
})


const isDraft = computed(() => BillStatus.canEdit(form.status, form.closeStatus))

// ===== 自定义字段（表头 subcontract_return / 明细 subcontract_return_item）=====
const headerCustomSections = ref([])
const headerCustomFieldMap = new Map()
const headerFormulaFields = ref([])
const itemCustomFields = ref([])
const itemCustomColumns = ref([])

async function loadCustomFields() {
  const headerResult = await loadCustomFieldDefinitions('subcontract_return')
  headerCustomSections.value = headerResult.sections
  headerCustomFieldMap.clear()
  headerResult.fieldMap.forEach((v, k) => headerCustomFieldMap.set(k, v))
  headerFormulaFields.value = headerResult.formulaFields

  const itemResult = await loadCustomFieldDefinitions('subcontract_return_item')
  itemCustomFields.value = Array.from(itemResult.fieldMap.values())
  itemCustomColumns.value = customFieldsToTableColumns(itemCustomFields.value, { editable: isDraft.value, listShowOnly: false })
}

// 草稿态变化时重新生成明细自定义字段列（editable 控制）
watch(isDraft, () => {
  if (itemCustomFields.value.length > 0) {
    itemCustomColumns.value = customFieldsToTableColumns(itemCustomFields.value, { editable: isDraft.value, listShowOnly: false })
  }
})
const rules = {
  returnDate: [{ required: true, message: tt('请选择退料日期'), trigger: 'change' }],
  orderId: [{ required: true, message: tt('请选择委外加工单'), trigger: 'change' }]
}

// 状态映射: 0草稿 1待审核 2已生效
function statusText(s) {
  return ({ 0: '草稿', 1: '待审核', 2: '已生效' })[Number(s)] || String(s)
}

function statusType(s) {
  return ({ 0: 'info', 1: 'warning', 2: 'primary', 3: 'success' })[Number(s)] || 'info'
}

function isStatus(val, target) {
  return Number(val) === target
}

function onOrderChange(orderId) {
  const o = orderOptions.value.find(x => x.id === orderId)
  if (o) {
    form.orderNo = o.orderNo || o.billNo || ''
    form.supplierId = o.supplierId
    form.supplierName = o.supplierName || ''
  } else {
    form.orderNo = ''
    form.supplierId = null
    form.supplierName = ''
  }
}

function onLineMaterialChange(row, materialId) {
  const m = materialOptions.value.find(x => x.materialId === materialId)
  if (m) {
    row.materialName = m.materialName
    row.materialCode = m.materialCode
    row.unitId = m.baseUnitId
    row.unit = m.baseUnitName || ''
  } else {
    row.materialName = ''
    row.materialCode = ''
    row.unitId = null
    row.unit = ''
  }
}

function addLine() {
  form.items.push({
    materialId: null,
    materialName: '',
    materialCode: '',
    unitId: null,
    unit: '',
    quantity: 0,
    warehouseId: null,
    warehouseName: '',
    remark: ''
  })
}

function calcTotal() {
  const sum = form.items.reduce((s, i) => s + (Number(i.quantity) || 0), 0)
  form.totalQuantity = Number(sum.toFixed(6))
}

function flushInputNumbers() {
  const list = form.items || form.value?.items || form.details || form.value?.details
  if (!list || list.length === 0) return
  list.forEach(row => {
    if (row.quantity !== undefined) row.quantity = Number(row.quantity || 0)
    if (row.unitPrice !== undefined) row.unitPrice = Number(row.unitPrice || 0)
    if (row.taxRate !== undefined) row.taxRate = Number(row.taxRate || 0)
    if (row.discountRate !== undefined) row.discountRate = Number(row.discountRate || 0)
    if (row.discountAmount !== undefined) row.discountAmount = Number(row.discountAmount || 0)
    if (row.amount !== undefined) row.amount = Number(row.amount || 0)
    if (row.taxAmount !== undefined) row.taxAmount = Number(row.taxAmount || 0)
    if (row.netAmount !== undefined) row.netAmount = Number(row.netAmount || 0)
  })
  if (form.items) form.items = [...form.items]
  else if (form.value?.items) form.value.items = [...form.value.items]
  else if (form.details) form.details = [...form.details]
  else if (form.value?.details) form.value.details = [...form.value.details]
}

async function onSave(silent, opts = {}) {
  flushInputNumbers()
  if (!isStatus(form.status, 0)) {
    ElMessage.warning(`${tt("当前状态为「")}${statusText(form.status)}${tt("」, 不可修改")}`)
    return false
  }
  // 宽松草稿保存：不校验必填，允许空委外加工单/无明细（可后续补全再提交）
  // 保存宽松：丢弃未选物料的空行（后端这些字段 NOT NULL，无法落库）
  form.items = (form.items || []).filter(r => r && r.materialId)
  calcTotal()
  try {
    const payload = {
      orderId: form.orderId,
      orderNo: form.orderNo,
      supplierId: form.supplierId,
      supplierName: form.supplierName,
      returnDate: form.returnDate,
      totalQuantity: form.totalQuantity,
      remark: form.remark,
      pickId: form.pickId,
      pickNo: form.pickNo,
      attachmentUrls: form.attachmentUrls,
      sourceBillType: form.sourceBillType,
      sourceBillNo: form.sourceBillNo,
      items: form.items.map(it => ({
        materialId: it.materialId,
        materialName: it.materialName,
        materialCode: it.materialCode,
        unitId: it.unitId,
        unit: it.unit,
        quantity: it.quantity,
        warehouseId: it.warehouseId,
        warehouseName: it.warehouseName,
        remark: it.remark
      }))
    }
    if (isEdit.value) {
      payload.id = form.id
      await updateSubcontractReturn(form.id, payload)
    } else {
      const res = await saveSubcontractReturn(payload)
      if (res && res.id) form.id = res.id
      if (res && res.returnNo) form.returnNo = res.returnNo
      if (res && res.status !== undefined) form.status = res.status
      // 新建保存后必须把 id 写回地址栏：否则 loadDetail() 拿不到 id → 提交/保存后页面状态不刷新（历史缺陷）
      if (!opts.skipRouteSync && form.id) { try { await router.replace({ name: 'SubcontractReturnEdit', params: { id: String(form.id) } }) } catch (_) {} }
    }
    if (headerCustomFieldMap.size > 0 && form.id) {
      const headerValues = {}
      for (const key of headerCustomFieldMap.keys()) {
        if (key in form) headerValues[key] = form[key]
      }
      await saveCustomFieldValues('subcontract_return', form.id, headerValues, headerFormulaFields.value)
    }
    if (itemCustomFields.value.length > 0 && form.items.length > 0) {
      // 保存主表时后端走「明细先删后插」，明细主键已变，必须先按新主键对齐再写明细自定义字段
      try {
        const fresh = await getSubcontractReturn(form.id)
        remapItemIdsAfterSave(form.items, (fresh && fresh.items) || [], 'id')
      } catch (e) {
        console.warn('[subcontractReturn] 保存后刷新明细主键失败，明细级自定义字段可能挂到旧行', e)
      }
      const itemKeys = new Set(itemCustomFields.value.map(f => f.fieldKey))
      for (const row of form.items) {
        if (!row.id) continue
        const values = {}
        for (const key of itemKeys) {
          if (key in row) values[key] = row[key]
        }
        await saveCustomFieldValues('subcontract_return_item', row.id, values)
      }
    }
    triggerListRefresh('/subcontract/returns')
    if (!silent) ElMessage.success(tt('保存成功'))

    await loadDetail()
    return true
  } catch (e) {
    ElMessage.error(e.message || tt('保存失败'))
  return false
  }
}

async function onSubmit() {
  flushInputNumbers()
    // 前端必填校验（B功能）
    // 统一校验：头表单 + 明细内表单 + 任何 formRef；失败时精确列出缺失字段名
  const missing = await validateBillRequiredFields({ headerFormRef, rules, $el: document, pageKey: 'subcontract_return', model: form })
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
      const target = headerFormRef.value || null
      if (target && typeof target.scrollToField === 'function') target.scrollToField(missing[0].prop)
    } catch(_) {}
    return
  }
  // 无论新建还是已有草稿，提交前一律先静默保存（把当前页面所见落库，避免提交旧数据）
  const saved = await onSave(true, { skipRouteSync: true })
  if (!saved || !form.id) return

  try {
    const needApproval = await confirmSubmit({ billType: 'SUBCONTRACT_RETURN', billLabel: '委外退料单' })
    await submitSubcontractReturn(form.id)
    ElMessage.success(submitSuccessMessage(needApproval))
    triggerListRefresh('/subcontract/returns')
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('提交失败'))
  } finally {
    // 提交（或用户取消）后再把新单 id 写回地址栏：此时重建实例无副作用，新实例挂载时会重新拉取最新状态
    if (!route.params.id && form.id) {
      try { await router.replace({ name: 'SubcontractReturnEdit', params: { id: String(form.id) } }) } catch (_) {}
    }
  }
}

async function onApprove() {
  if (!form.id) { ElMessage.warning(tt('请先保存单据')); return }
  try {
    await ElMessageBox.confirm(tt('确认审核该退料单？'), tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    const approver = localStorage.getItem('username') || 'system'
    await approveSubcontractReturn(form.id, approver)
    ElMessage.success(tt('已生效'))
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('审核失败'))
  }
}

async function onReverseApprove() {
  if (!form.id) { ElMessage.warning(tt('请先保存单据')); return }
  try {
    await ElMessageBox.confirm(tt('确认重置草稿？'), tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await reverseApproveSubcontractReturn(form.id)
    ElMessage.success(tt('已重置草稿'))
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('重置草稿失败'))
  }
}

async function onDelete() {
  if (!form.id) { ElMessage.warning(tt('请先保存单据')); return }
  try {
    await ElMessageBox.confirm(`${tt("确认删除退料单 ")}${form.returnNo}?`, tt('提示'), { type: 'warning' })
    await deleteSubcontractReturn(form.id)
    ElMessage.success(tt('删除成功'))
    router.push('/subcontract/returns')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('删除失败'))
  }
}
function handlePrint() {
  if (!form.id) { ElMessage.warning(tt('请先保存单据再打印')); return }
  printVisible.value = true
}

async function loadDetail() {
  const __detailId = route.params.id || form.id || ''
  if (__detailId) {
    try {
      const res = await getSubcontractReturn(__detailId)
      Object.assign(form, {
        id: res.id,
        returnNo: res.returnNo || '',
        orderId: res.orderId,
        orderNo: res.orderNo || '',
        supplierId: res.supplierId,
        supplierName: res.supplierName || '',
        returnDate: res.returnDate,
        totalQuantity: res.totalQuantity || 0,
        status: res.status,
        remark: res.remark || '',
        pickId: res.pickId || null,
        pickNo: res.pickNo || '',
        attachmentUrls: res.attachmentUrls || '',
        sourceBillType: res.sourceBillType || '',
        sourceBillNo: res.sourceBillNo || '',
        createdAt: res.createdAt || '',
        createTime: res.createTime || '',
        items: (res.items || []).map(it => ({
          materialId: it.materialId,
          materialName: it.materialName || '',
          materialCode: it.materialCode || '',
          unitId: it.unitId,
          unit: it.unit || it.unitName || '',
          quantity: it.quantity || 0,
          warehouseId: it.warehouseId,
          warehouseName: it.warehouseName || '',
          remark: it.remark || '',
          sourceBillNo: it.sourceBillNo || '',
          sourceBillType: it.sourceBillType || '',
          sourceDocId: it.sourceDocId || null
        }))
      })
      if (headerCustomFieldMap.size > 0 && res.id) {
        const headerValues = await loadCustomFieldValues('subcontract_return', res.id)
        Object.assign(form, headerValues)
      }
      if (itemCustomFields.value.length > 0 && form.items.length > 0) {
        const rawItems = (res.items || [])
        const itemIds = rawItems.map(it => it.id).filter(Boolean)
        if (itemIds.length > 0) {
          const itemValuesMap = await batchLoadCustomFieldValues('subcontract_return_item', itemIds)
          form.items.forEach((row, i) => {
            const values = rawItems[i] ? itemValuesMap[rawItems[i].id] : null
            if (values) Object.assign(row, values)
          })
        }
      }
      calcTotal()
    } catch (e) {
      ElMessage.error(e.message || tt('加载失败'))
    }
  }
}

// 来源单据跳转映射
const SOURCE_BILL_ROUTE_MAP = {
  '委外加工单': '/subcontract/orders',
  '委外发料单': '/subcontract/picks',
  '委外入库单': '/subcontract/inbounds',
  '委外退料单': '/subcontract/returns',
  '委外补料单': '/subcontract/supplements',
  '生产工单': '/production/workorders'
}

function goToSourceBill(row) {
  if (!row.sourceBillNo) return
  const routeBase = SOURCE_BILL_ROUTE_MAP[row.sourceBillType]
  if (routeBase) {
    router.push(`${routeBase}?keyword=${encodeURIComponent(row.sourceBillNo)}`)
  } else {
    ElMessage.warning(`${tt("暂不支持跳转到 ")}${row.sourceBillType || tt('该来源单据')}`)
  }
}

// 选单回调 - 从委外发料单带入表头和明细
function onSourcePicked(sourceData, items) {
  if (sourceData) {
    if (sourceData.orderId) form.orderId = sourceData.orderId
    if (sourceData.orderNo) form.orderNo = sourceData.orderNo
    if (sourceData.supplierId !== undefined) form.supplierId = sourceData.supplierId
    if (sourceData.supplierName) form.supplierName = sourceData.supplierName
    if (sourceData.pickId !== undefined) form.pickId = sourceData.pickId
    if (sourceData.pickNo) form.pickNo = sourceData.pickNo
    if (sourceData.sourceBillType) form.sourceBillType = sourceData.sourceBillType
    if (sourceData.sourceBillNo) form.sourceBillNo = sourceData.sourceBillNo
    if (sourceData.remark) form.remark = sourceData.remark
  }
  if (items && items.length > 0) {
    // 2026-11-23：选单带入追加到现有明细末尾，不再覆盖原有明细
    const newRows = items.map(it => ({
      materialId: it.materialId,
      materialName: it.materialName || '',
      materialCode: it.materialCode || '',
      unitId: it.unitId,
      unit: it.unit || it.unitName || '',
      quantity: it.quantity || 0,
      warehouseId: null,
      warehouseName: '',
      remark: '',
      sourceBillNo: it.sourceBillNo || '',
      sourceBillType: it.sourceBillType || '',
      sourceDocId: it.sourceDocId || null
    }))
    form.items = [...(form.items || []), ...newRows]
    calcTotal()
  }
}

// ===== unified bill actions - actions without backend API show a hint =====
const {
  supportedActions,
  rejectDialog, rejectReason, doReject,
  voidDialog, voidReason, doVoid,
  handleReject, handleReverseApprove,
  handleClose, handleReopen, handleVoid, handleUnvoid,
  handleDelete, handleCopy
} = useBillActions({
  form,
  getId: () => form.id,
  api: {
    approve: approveSubcontractReturn,
    reverseApprove: reverseApproveSubcontractReturn,
    reject: rejectSubcontractReturn,
    void: voidSubcontractReturn,
    unvoid: unvoidSubcontractReturn,
    delete: deleteSubcontractReturn
  },
  reload: loadDetail,
  router,
  newPath: '/subcontract/returns/new',
  listPath: '/subcontract/returns',
  billLabel: '委外退料单'
})

// ===== 复制功能 =====
const { applyCopy } = useCopyBill({
  route,
  loadFn: getSubcontractReturn,
  form,
  noField: 'returnNo'
})

// 加载下拉基础数据——独立函数，供 onMounted 和切回标签页时复用
  async function loadBaseData() {
  try {
      const [mats, whs, orders] = await Promise.all([
        listAllMaterial(),
        listAllWarehouse(),
        listSubcontract({ current: 1, size: 1000 })
      ])
      materialOptions.value = mats || []
      // 行内下拉附加字段：物料到位后按批预取（不阻塞渲染）
      mcf.init().then(() => mcf.preloadAll(materialOptions.value)).catch(() => {})
      warehouseOptions.value = whs || []
      // 仅显示已下达及以后状态的委外单（status >= 3）
      const allOrders = (orders.records || orders || [])
      orderOptions.value = allOrders.filter(o => Number(o.status) >= 3)
    } catch (e) {
      console.warn('下拉数据加载失败:', e)
    }
}

// ===== 顶级页签：单据 / 来源单据 / 去向单据 =====
const topTab = ref('doc')
const billLinkRegistered = ref(false)

useFieldRequiredMark('subcontract_return', '.subcontract-return-edit')

onMounted(async () => {
  // 全站商业口径总开关（含税业务 / 外币业务）：打开页面先装载一次
  await ensureCommercialSwitches()
  // 该单据类型是否登记了「来源单据/去向单据」配置（未登记则不显示这两个页签）
  isBillLinkRegistered('SUBCONTRACT_RETURN')
    .then(v => { billLinkRegistered.value = !!v })
    .catch(() => { billLinkRegistered.value = false })
  await loadCustomFields()
  loadCollapsed()
  await loadBaseData()
  if (!isEdit.value) {
    autoFillDocCodeOnCreate(form, 'returnNo')
  }
  const copied = await applyCopy()
  if (!copied) {
    await loadDetail()
  }
})

// 切回标签页时自动刷新下拉基础数据（只刷下拉，不动表单草稿），避免档案改过后这里还是旧数据
useBaseDataRefresh(loadBaseData)


  </script>

<style scoped lang="scss">
.subcontract-return-edit {
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

.muted-text { color: #c0c4cc; font-size: 12px; }

// ===== 源单信息栏 =====
.source-info-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: #ecf5ff;
  border-bottom: 1px solid #d9ecff;
  font-size: 12px;
  color: #409eff;

  &.muted {
    background: #f5f7fa;
    border-bottom-color: #ebeef5;
    color: #909399;
  }

  .source-label {
    color: #606266;
    font-weight: 500;
  }
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
  .subcontract-return-edit { padding: 8px; }
  .page-header { flex-direction: column; gap: 8px; align-items: stretch; }
  .base-form .section-fields { grid-template-columns: 1fr; }
  .summary-area { flex-direction: column; }
}

// ===== 明细「物料」下拉：选项内附加展示（编码 + 名称 + 自定义字段 + 规格）=====
.table-select {
  :deep(.el-select__wrapper) { box-shadow: none; }
  :deep(.el-input__inner) { font-size: 12px; }
}
.opt-code {
  color: #409eff;
  font-weight: 600;
  font-family: 'SF Mono', Monaco, Consolas, monospace;
  font-size: 11px;
  margin-right: 6px;
}
.opt-name { color: #303133; font-size: 12px; }
.opt-model { float: right; color: #909399; font-size: 11px; }
// 行内下拉附加字段（自定义字段「字段名：值」）：灰色小字，仅在有值时渲染
.opt-cf {
  margin-left: 8px;
  color: #909399;
  font-size: 11px;
  white-space: normal;
  word-break: break-word;
}
</style>

<!-- 非 scoped：物料下拉的 popper 被 teleport 到 body，scoped 选择器无法命中，只能配全局类名（popper-class="material-select-popper"） -->
<style>
.material-select-popper {
  min-width: 640px;
  max-width: 90vw;
}
.material-select-popper .el-select-dropdown__item {
  height: auto;
  min-height: 34px;
  line-height: 1.6;
  padding-top: 6px;
  padding-bottom: 6px;
  white-space: normal;
  overflow: visible;
}
</style>
