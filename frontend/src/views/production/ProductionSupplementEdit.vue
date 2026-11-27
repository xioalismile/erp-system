<template>
  <div class="production-supplement-edit">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div class="header-left">
        <div class="back-btn" @click="$router.push('/production/supplements')">
          <el-icon><ArrowLeft /></el-icon>
          <span>{{ $tt("返回列表") }}</span>
        </div>
        <div class="title-area">
          <div class="title-row">
            <el-icon class="title-icon"><Document /></el-icon>
            <span class="page-title">{{ isEdit ? '生产补料单' : '新增生产补料单' }}</span>
            <span v-if="form.requisitionNo" class="order-no">
              <el-icon><Collection /></el-icon>
              {{ form.requisitionNo }}
            </span>
            <el-tag v-if="form.status" size="small" :type="statusTagType(form.status)" effect="plain" class="status-tag">{{ $dt(form.status) }}</el-tag>
          </div>
        </div>
      </div>
      <div class="header-right">
        <el-button v-permission="perm('view')" v-if="!(!form.orderId && !form.deliveryId && !form.inboundId && !form.invoiceId && !form.voucherId && !form.contractId && !form.requisitionId && !form.stockCheckId && !form.transferId && !form.bomId && !form.sampleId && !form.freeSampleId && !form.workOrderId && !form.billId && !form.id)" @click="changeRecordVisible = true">
          <el-icon><Memo /></el-icon>{{ $tt('操作日志') }}
        </el-button>
        <BillActions bill-type="MATERIAL_SUPPLEMENT"
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

    <!-- 顶级页签：单据 / 来源单据（来源去向为通用组件；补料单无下游，不显示去向页签） -->
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
        <el-form :model="form" :rules="rules" ref="headerFormRef" label-width="90px" class="base-form">
          <!-- 单据信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>单据信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('补料单号')" v-if="isFieldVisible('requisitionNo')" :required="isFieldRequired('requisitionNo')">
                <el-input v-model="form.requisitionNo" :disabled="!isDraft" :placeholder="docCodePlaceholder" class="field-input"  />
              </el-form-item>
              <el-form-item :label="$tf('补料日期')" prop="requisitionDate" required v-if="isFieldVisible('requisitionDate')">
                <el-date-picker v-model="form.requisitionDate" type="date" value-format="YYYY-MM-DD" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('工单号')" v-if="isFieldVisible('workOrderNo')" :required="isFieldRequired('workOrderNo')">
                <el-input v-model="form.workOrderNo" :placeholder="$tt('工单号')" class="field-input" :disabled="!isDraft" @change="onWorkOrderNoChange" />
              </el-form-item>
              <el-form-item :label="$tf('来源报工单')" v-if="isFieldVisible('sourceBillNo')" :required="isFieldRequired('sourceBillNo')">
                <el-select
                  v-model="form.sourceBillNo"
                  filterable
                  clearable
                  :placeholder="$tt('选择有报废的报工单（可选）')"
                  class="field-input"
                  :disabled="!isDraft || !form.workOrderNo"
                  :loading="reportLoading"
                  @change="onSourceReportChange"
                >
                  <el-option
                    v-for="r in sourceReports"
                    :key="r.reportId || r.reportNo"
                    :label="`${r.reportNo}${r.operationName ? ' / ' + r.operationName : ''}${r.rejectedQuantity ? ' (报废:' + r.rejectedQuantity + ')' : ''}`"
                    :value="r.reportNo"
                  />
                </el-select>
              </el-form-item>
            </div>
          </div>

          <!-- 仓库与经办人 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>仓库与经办人
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('仓库')" prop="warehouseId" required v-if="isFieldVisible('warehouseId')">
                <el-select v-model="form.warehouseId" filterable :placeholder="$tt('选择仓库')" class="field-input" :disabled="!isDraft" @change="onWarehouseChange">
                  <el-option v-for="w in withFallbackOption(warehouses, 'warehouseId', 'warehouseName', form)" :key="w.stockId" :label="w.stockName" :value="w.stockId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('经办人')" v-if="isFieldVisible('employeeId')" :required="isFieldRequired('employeeId')">
                <el-select v-model="form.employeeId" filterable :placeholder="$tt('选择补料人')" clearable class="field-input" :disabled="!isDraft" @change="onEmployeeChange">
                  <el-option v-for="e in employees" :key="e.employeeId" :label="e.employeeName" :value="e.employeeId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('部门')" v-if="isFieldVisible('departmentId')" :required="isFieldRequired('departmentId')">
                <el-select v-model="form.departmentId" filterable :placeholder="$tt('选择部门')" clearable class="field-input" :disabled="!isDraft">
                  <el-option v-for="d in departments" :key="d.deptId" :label="d.deptName" :value="d.deptId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('币别')" prop="currencyId" required v-if="isFieldVisible('currencyId')">
                <el-select v-model="form.currencyId" filterable :placeholder="$tt('选择币别')" class="field-input" :disabled="!isDraft || !foreignCurrencyEnabled" @change="onCurrencyChange">
                  <el-option v-for="c in currencies" :key="c.currencyId" :label="c.currencyCode" :value="c.currencyId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('汇率')" v-if="isFieldVisible('exchangeRate')" :required="isFieldRequired('exchangeRate')">
                <el-input-number v-model="form.exchangeRate" :precision="6" :step="0.1" :min="0" class="field-input" :disabled="!isDraft || !foreignCurrencyEnabled" />
              </el-form-item>
            </div>
          </div>

          <!-- 单据自定义字段（设置中心→自定义字段配置 中为「生产补料单」定义的字段） -->
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
                :required="field.required"
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

          <!-- 其他 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>其他
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('补料原因')" class="span-all" v-if="isFieldVisible('remark')" :required="isFieldRequired('remark')">
                <el-input v-model="form.remark" type="textarea" :rows="2" class="field-input" :disabled="!isDraft" :placeholder="$tt('请输入补料原因说明')" />
              </el-form-item>
            </div>
          </div>
        </el-form>
      </div>
    </div>

    <!-- 明细卡片（通用组件 BillItemsTable：卡片/列设置/动态列/汇总 全站共用一套） -->
    <BillItemsTable
      v-model:collapsed="collapsed.items"
      :items="form.items"
      page-key="ProductionSupplementEdit_items"
      title="补料明细"
      subtitle="物料明细"
      col-setting-title="物料明细列设置"
      :columns="detailColumns"
      :custom-columns="[]"
      :readonly="!isDraft"
      :total-qty="totalQty"
      :summary-items="summaryItems"
      :source-link-click="goToSourceBill"
      @remove="calcTotal"
    >
      <template #actions>
        <el-button v-permission="perm('pick')" size="small" @click="pickerVisible = true" :disabled="!isDraft">
          <el-icon><Grid /></el-icon>从物料库选择
        </el-button>
        <el-button v-permission="perm('addRow')" size="small" type="primary" @click="addItem" :disabled="!isDraft">
          <el-icon><Plus /></el-icon>添加明细
        </el-button>
      </template>
      <template #cell-materialId="{ row }">
        <el-select
          v-model="row.materialId"
          filterable
          :placeholder="materialSelectPlaceholder"
          @change="(v) => onMaterialChange(row, v)"
          :disabled="!isDraft"
          :filter-method="(val) => materialFilterMethod(val, row)"
          class="table-select"
          size="small"
          :teleported="true"
          :popper-options="{ strategy: 'fixed' }"
          popper-class="material-select-popper"
        >
          <el-option
            v-for="m in row._filteredMaterials || materials"
            :key="m.materialId"
            :label="`${m.materialCode} ${m.materialName}`"
            :value="m.materialId"
          >
            <span class="opt-code">{{ m.materialCode }}</span>
            <span class="opt-name">{{ m.materialName }}</span>
            <span v-for="cf in mcf.dropText(m)" :key="cf.key" class="opt-cf">{{ cf.label }}：{{ cf.value }}</span>
            <span v-if="m.model" class="opt-model">{{ m.model }}</span>
          </el-option>
        </el-select>
      </template>
      <template #cell-unit="{ row }">
        <UnitPickerCell
          v-model="row.unitId"
          :units="units"
          :material="materialOf(row)"
          :disabled="!isDraft"
          @change="calcRow(row)"
        />
      </template>
      <template #cell-remark="{ row }">
        <el-input v-model="row.remark" size="small" :disabled="!isDraft" :placeholder="$tt('补料原因')" />
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
          type="production-supplement"
          :disabled="!isDraft"
          button-text="上传附件"
          tip-text="支持图片、PDF、Word、Excel 等文件"
        />
      </div>
    </div>

    <!-- 审核流转（真实审批进度，通用组件） -->
    <ApprovalFlowTimeline bill-type="MATERIAL_SUPPLEMENT" :bill-id="form.requisitionId" :status="form.status" />

    <!-- 取消弹窗 -->
    <el-dialog v-model="cancelDialog" :title="$tf('取消单据')" width="420px">
      <el-form label-width="80px">
        <el-form-item :label="$tf('取消原因')">
          <el-input v-model="cancelReason" type="textarea" :rows="3" :placeholder="$tt('请输入取消原因')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="cancelDialog = false">{{ $tt("返回") }}</el-button>
        <el-button v-permission="perm('dlgConfirm')" type="danger" @click="doCancel">{{ $tt("确认取消") }}</el-button>
      </template>
    </el-dialog>

    <!-- 关闭弹窗 -->
    <el-dialog v-model="closeDialog" :title="$tf('关闭单据')" width="420px">
      <el-form label-width="80px">
        <el-form-item :label="$tf('关闭原因')">
          <el-input v-model="closeReason" type="textarea" :rows="3" :placeholder="$tt('请输入关闭原因')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="closeDialog = false">{{ $tt("返回") }}</el-button>
        <el-button v-permission="perm('dlgConfirm')" type="warning" @click="doClose">{{ $tt("确认关闭") }}</el-button>
      </template>
    </el-dialog>

    <!-- 物料库选择弹窗（通用组件：内置自定义字段搜索、全字段/自定义字段列、列设置全局共享 MaterialPicker） -->
    <MaterialPickerDialog v-model="pickerVisible" @confirm="onMaterialPicked" />

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

    <!-- 操作日志弹窗 -->
    <ChangeRecordDialog v-model="changeRecordVisible" bill-type="生产补料单" :bill-no="form.requisitionNo || ''" />

    <!-- 打印预览弹窗（生产补料单复用领料单详情） -->
    <PrintPreviewDialog v-model="printVisible" bill-type="MATERIAL_REQUISITION" :bill-id="form.requisitionId" />

      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('来源单据')" name="source" :disabled="!form.requisitionId">
        <BillLinkPanel v-if="topTab === 'source'" bill-type="MATERIAL_SUPPLEMENT" :bill-id="form.requisitionId" mode="source" />
      </el-tab-pane>
    </el-tabs>
  </div>

  </template>

<script setup>
import { ref, reactive, computed, onMounted, watch, h } from 'vue'
import { withFallbackOption } from '@/utils/labelFallback'
import { useBaseDataRefresh } from '@/composables/useBaseDataRefresh'
import { useAutoDocCode } from '@/composables/useAutoDocCode'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { ArrowLeft, Document, Collection, Plus, Grid, Printer, ArrowRight, Check, ArrowDown, CircleCheck, CircleClose, Close, Clock, Memo } from '@element-plus/icons-vue'
import {
  getMaterialRequisition, saveMaterialRequisition, updateMaterialRequisition,
  deleteMaterialRequisition, submitMaterialRequisition, approveMaterialRequisition,
  closeMaterialRequisition, cancelMaterialRequisition,
  rejectMaterialRequisition, reverseApproveMaterialRequisition, unvoidMaterialRequisition, reopenMaterialRequisition
} from '@/api/inventory/materialRequisition'
import { useBillActions } from '@/composables/useBillActions'
import { listOperationReport } from '@/api/production/operationReport'
import { listAllWarehouse } from '@/api/basedata/warehouse'
import { listAllMaterial } from '@/api/basedata/material'
import { listAllCurrency } from '@/api/basedata/currency'
import { listAllDepartment } from '@/api/basedata/department'
import { listAllEmployee } from '@/api/basedata/employee'
import { listAllUnit } from '@/api/basedata/unit'
import ChangeRecordDialog from '@/components/ChangeRecordDialog.vue'
import PrintPreviewDialog from '@/components/PrintPreviewDialog.vue'
import ApprovalFlowTimeline from '@/components/bill/ApprovalFlowTimeline.vue'
import FileUploader from '@/components/FileUploader.vue'

import * as BillStatus from '@/utils/bill-status'
import { confirmSubmit, submitSuccessMessage } from '@/utils/submit-confirm'
import { validateBillRequiredFields } from '@/utils/billValidation'
import BillActions from '@/components/bill/BillActions.vue'
import BillLinkPanel from '@/components/bill/BillLinkPanel.vue'
import UnitPickerCell from '@/components/bill/UnitPickerCell.vue'
import { isBillLinkRegistered } from '@/api/billLink'
import { triggerListRefresh } from '@/composables/useAutoRefresh'
import { useCopyBill } from '@/composables/useCopyBill'
import { getCurrentEmployee } from '@/composables/useCurrentEmployee'
import { tt } from '@/i18n'
import { useMaterialCustomFields } from '@/composables/useMaterialCustomFields'
import { loadCustomFieldDefinitions, loadCustomFieldValues, saveCustomFieldValues } from '@/composables/useCustomField'
// 明细通用组件（卡片折叠/列设置/动态列/汇总 全站共用一套）
import BillItemsTable from '@/components/bill/BillItemsTable.vue'
// 物料库选择弹窗（通用件）：内置自定义字段搜索、全字段/自定义字段列、列设置全局共享 MaterialPicker
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
// 物料下拉的输入过滤 + 背景提示文字（通用件）
import { filterMaterials, buildMaterialPlaceholder } from '@/utils/materialFilter'
import { useBillLineStock, BILL_STOCK_COLUMNS } from '@/composables/useBillLineStock'
import { usePagePerm } from '@/utils/pagePerm'
// 全站商业口径总开关（设置中心：启用含税业务 / 启用外币业务）
import { useCommercialSwitches } from '@/composables/useCommercialSwitches'
import { useFieldPermission } from '@/composables/useFieldPermission'

const perm = usePagePerm('production:supplements')
const { isFieldVisible, isFieldRequired } = useFieldPermission('material_requisition')

// 全站商业口径总开关：外币业务关闭时币种锁定本位币、汇率锁 1
const { taxEnabled, foreignCurrencyEnabled, ensureLoaded: ensureCommercialSwitches } = useCommercialSwitches()

// 补料明细列定义（通用组件 BillItemsTable 消费）：editor = 单元格控件，type = 纯展示格式；
// 物料/单位/备注 由页面提供插槽（#cell-<prop>），来源单号列由组件的 sourceLinkClick 回调跳转来源单
const detailColumns = [
  { prop: 'materialId', label: '商品名称', minWidth: 280 },
  { prop: 'materialCode', label: '商品编号', width: 130 },
  ...BILL_STOCK_COLUMNS,
  { prop: 'specification', label: '规格', width: 120 },
  { prop: 'unit', label: '单位', width: 70, align: 'center' },
  { prop: 'sourceBillNo', label: '来源单号', minWidth: 140, type: 'sourceLink' },
  { prop: 'quantity', label: '补料数量', width: 120, editor: 'number', precision: 4, change: (row) => calcRow(row) },
  { prop: 'unitPrice', label: '单价', width: 110, editor: 'number', precision: 4, change: (row) => calcRow(row) },
  { prop: 'amount', label: '金额', width: 100, align: 'right', type: 'amount2' },
  { prop: 'stockId', label: '仓库', width: 140, editor: 'select', placeholder: '仓库', clearable: false,
    optionsFn: () => (warehouses.value || []).map(w => ({ value: w.stockId, label: w.stockName })) },
  { prop: 'batchNo', label: '批次号', width: 120, editor: 'text' },
  { prop: 'remark', label: '备注', minWidth: 140 }
]
// 汇总区（通用组件 BillItemsTable 按此渲染；「合计数量：」行由组件按 totalQty 渲染，金额口径与迁移前一致）
const summaryItems = computed(() => [
  { label: '合计金额：', value: `¥${Number(form.totalAmount || 0).toFixed(2)}`, total: true }
])
useBillLineStock(() => form.items)

const route = useRoute()
const router = useRouter()
const headerFormRef = ref()

const isEdit = computed(() => !!route.params.id)

// ===== 顶级页签：单据 / 来源单据 =====
const topTab = ref('doc')
const billLinkRegistered = ref(false)

// 单据编号：按设置中心自动编号规则（MATERIAL_REQUISITION）决定自动带出或手动输入
const { docCodePlaceholder, autoFillDocCodeOnCreate } = useAutoDocCode('MATERIAL_REQUISITION')

// 仅草稿态允许编辑（单号/业务字段/自定义字段）；已生效/待审批等只读
const STORAGE_KEY = 'production-supplement-edit-collapsed'

// 补料单的 billType 固定为 SUPPLEMENT
const BILL_TYPE = 'SUPPLEMENT'

// 单据自定义字段（生产补料单）：设置中心「自定义字段配置」中为「生产补料单」定义的字段
const headerCustomSections = ref([])
const headerCustomFieldMap = new Map()
const headerFormulaFields = ref([])

const collapsed = reactive({
  base: true,
  items: true,
  attach: true,
  audit: false,
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

const materials = ref([])
// 物料自定义字段：行内下拉附加展示 + 物料库弹窗全字段（逻辑集中在 composable）
const mcf = useMaterialCustomFields()
const warehouses = ref([])
const currencies = ref([])
const departments = ref([])
const employees = ref([])
const units = ref([])

const cancelDialog = ref(false)
const cancelReason = ref('')
const closeDialog = ref(false)
const closeReason = ref('')
const changeRecordVisible = ref(false)

const pickerVisible = ref(false)

// 来源报工单选择相关
const sourceReports = ref([])
const reportLoading = ref(false)
const sourceReportMap = reactive({})

const form = reactive({
  requisitionId: null,
  requisitionNo: '',
  billType: BILL_TYPE,
  requisitionDate: new Date().toISOString().slice(0, 10),
  sourceBillNo: '',
  workOrderNo: '',
  warehouseId: null,
  departmentId: null,
  employeeId: null,
  employeeName: '',
  currencyId: null,
  exchangeRate: 1,
  totalAmount: 0,
  remark: '',
  attachmentUrls: '',
  status: '草稿',
  createdAt: '',
  items: []
})


const isDraft = computed(() => BillStatus.canEdit(form.status, form.closeStatus))
const totalQty = computed(() => {
  return form.items.reduce((sum, row) => sum + Number(row.quantity || 0), 0).toFixed(2)
})

const rules = {
  requisitionDate: [{ required: true, message: tt('请选择补料日期'), trigger: 'change' }],
  warehouseId: [{ required: true, message: tt('请选择仓库'), trigger: 'change' }],
  currencyId: [{ required: true, message: tt('请选择币别'), trigger: 'change' }]
}

function statusTagType(s) {
  return { '草稿': 'info', '已生效': 'success', '已关闭': 'warning', '已取消': 'danger' }[s] || 'info'
}

/** 当前行物料（多单位换算用：取基本单位、辅计量单位与换算率） */
function materialOf(row) {
  if (!row || row.materialId == null) return null
  return materials.value.find(x => x.materialId === row.materialId) || null
}

// 来源单据跳转映射
const SOURCE_BILL_ROUTE_MAP = {
  '生产领料单': '/inventory/material-requisitions',
  '生产工单': '/production/workorders',
  '生产入库单': '/production/receipts'
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

function onEmployeeChange(id) {
  const e = employees.value.find(x => x.employeeId === id)
  if (e) form.employeeName = e.employeeName
}

function onCurrencyChange(id) {
  const c = currencies.value.find(x => x.currencyId === id)
  if (c && c.exchangeRate) form.exchangeRate = c.exchangeRate
}

function onWarehouseChange() {
  form.items.forEach(row => {
    if (!row.stockId) row.stockId = form.warehouseId
  })
}

async function onMaterialChange(row, id) {
  const m = materials.value.find(x => x.materialId === id)
  if (m) {
    row.materialCode = m.materialCode
    row.materialName = m.materialName
    row.specification = m.model || ''
    row.unitId = m.baseUnitId
    row.unitPrice = m.purchasePrice || 0
    if (!row.stockId && form.warehouseId) row.stockId = form.warehouseId
    calcRow(row)
  }
}

// 工单号变更后，加载该工单下有报废数量的报工单
async function onWorkOrderNoChange() {
  // 工单号变化时清空来源报工单选择
  form.sourceBillNo = ''
  sourceReports.value = []
  Object.keys(sourceReportMap).forEach(k => delete sourceReportMap[k])
  if (!form.workOrderNo) return
  await loadSourceReports()
}

// 加载当前工单下的报工单列表（有报废数量的优先）
async function loadSourceReports() {
  if (!form.workOrderNo) return
  reportLoading.value = true
  try {
    const res = await listOperationReport({
      current: 1, size: 200,
      workOrderNo: form.workOrderNo
    })
    const list = res?.records || (Array.isArray(res) ? res : [])
    // 优先展示有报废数量的报工单
    sourceReports.value = list.filter(r => Number(r.rejectedQuantity || 0) > 0)
      .concat(list.filter(r => !Number(r.rejectedQuantity || 0)))
    // 缓存到 map 便于选择时获取明细
    sourceReports.value.forEach(r => {
      if (r.reportNo) sourceReportMap[r.reportNo] = r
    })
  } catch (e) {
    sourceReports.value = []
  } finally {
    reportLoading.value = false
  }
}

// 选择来源报工单后，补料原因自动填充为"报工报废补料"（可手动编辑）
function onSourceReportChange(reportNo) {
  if (!reportNo) return
  const rep = sourceReportMap[reportNo]
  if (!rep) return
  // 仅在补料原因为空或为默认值时自动填充，避免覆盖用户已编辑内容
  if (!form.remark || !form.remark.trim()) {
    const parts = ['报工报废补料']
    if (rep.operationName) parts.push(`工序：${rep.operationName}`)
    if (rep.rejectedQuantity) parts.push(`报废数量：${rep.rejectedQuantity}`)
    form.remark = parts.join('，')
  }
}

function calcRow(row) {
  const qty = Number(row.quantity || 0)
  const price = Number(row.unitPrice || 0)
  row.amount = (qty * price).toFixed(2)
  calcTotal()
}

function calcTotal() {
  const total = form.items.reduce((s, i) => s + (Number(i.amount) || 0), 0)
  form.totalAmount = total.toFixed(2)
}

function addItem() {
  form.items.push({
    materialId: null,
    materialCode: '',
    materialName: '',
    specification: '',
    unitId: null,
    quantity: 1,
    unitPrice: 0,
    amount: 0,
    stockId: form.warehouseId || null,
    binId: null,
    batchNo: '',
    remark: ''
  })
  calcTotal()
}

/** 参与「物料」下拉搜索的自定义字段：勾了「下拉显示」（受定制总开关控制）或勾了「可搜索」的字段 */
const materialSearchableFields = computed(() => (mcf.allFields.value || []).filter(f =>
  f && (f.isSearch === 1 || (mcf.enabled.value && f.isDropShow === 1))
))

/** 物料下拉的背景提示文字：声明可搜索范围；没有可搜索的自定义字段时原样返回「选择物料」（零回归） */
const materialSelectPlaceholder = computed(() => buildMaterialPlaceholder(tt('选择物料'), materialSearchableFields.value))

/**
 * 明细行「物料」下拉的输入过滤（用户口径：不加额外搜索框，统一用下拉本身）
 * 过滤范围 = 物料编码/名称/规格 ＋ 自定义字段值 ＋ 自定义字段名，详见 utils/materialFilter.js
 */
function materialFilterMethod(val, row) {
  if (!val) { row._filteredMaterials = null; return }
  row._filteredMaterials = filterMaterials(materials.value, val, mcf.valueMap.value, materialSearchableFields.value)
}

async function onMaterialPicked({ rows: picked, valueMap }) {
  if (!picked || picked.length === 0) return
  if (valueMap) {
    Object.assign(mcf.valueMap.value, valueMap)
    Object.keys(valueMap).forEach(id => mcf.loadedIds.value.add(Number(id)))
  }
  picked.forEach(m => {
    const row = {
      materialId: m.materialId,
      materialCode: m.materialCode || '',
      materialName: m.materialName || '',
      specification: m.model || '',
      unitId: m.baseUnitId || null,
      quantity: 1,
      unitPrice: m.purchasePrice || 0,
      amount: 0,
      stockId: form.warehouseId || null,
      binId: null,
      batchNo: '',
      remark: ''
    }
    calcRow(row)
    form.items.push(row)
  })
  pickerVisible.value = false
  ElMessage.success(`${tt("已添加 ")}${picked.length}${tt(" 条明细")}`)
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
  if (form.status && !isDraft.value) {
    ElMessage.warning(`${tt("当前状态为「")}${form.status}${tt("」, 不可修改")}`)
    return false
  }
  // 宽松草稿保存：不校验必填，允许无明细（可后续补全再提交）
  // 保存宽松：丢弃未选物料的空行（后端这些字段 NOT NULL，无法落库）
  form.items = (form.items || []).filter(r => r && r.materialId)
  calcTotal()
  form.billType = BILL_TYPE
  try {
    if (form.requisitionId) {
      await updateMaterialRequisition(form.requisitionId, form)
    } else {
      const res = await saveMaterialRequisition(form)
      if (res && res.requisitionId) form.requisitionId = res.requisitionId
      if (res && res.requisitionNo) form.requisitionNo = res.requisitionNo
      if (res && res.status) form.status = res.status
      // 新建保存后必须把 id 写回地址栏：否则 loadDetail() 拿不到 id → 提交/保存后页面状态不刷新（历史缺陷）
      if (!opts.skipRouteSync && form.requisitionId) { try { await router.replace({ name: 'ProductionSupplementEdit', params: { id: String(form.requisitionId) } }) } catch (_) {} }
    }
    // 单据自定义字段：主表已落库、单据ID已知，写入通用自定义字段表
    await saveHeaderCustomFields()
    triggerListRefresh('/production/supplements')
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
  // 前端必填校验：表头必填（补料日期/仓库/币种）。仓库为空时不保存、不提交，单据保持草稿状态。
  const missing = await validateBillRequiredFields({ headerFormRef, rules })
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
    try {
      const target = (headerFormRef && headerFormRef.value) ? headerFormRef.value : null
      if (target && typeof target.scrollToField === 'function') target.scrollToField(missing[0].prop)
    } catch (_) {}
    return
  }
  // 无论新建还是已有草稿，提交前一律先静默保存（把当前页面所见落库，避免提交旧数据）
  const saved = await onSave(true, { skipRouteSync: true })
  if (!saved || !form.requisitionId) return
  try {
    const needApproval = await confirmSubmit({ billType: 'MATERIAL_SUPPLEMENT', billLabel: '生产补料单' })
    await submitMaterialRequisition(form.requisitionId)
    ElMessage.success(submitSuccessMessage(needApproval))
    triggerListRefresh('/production/supplements')
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('提交失败'))
  } finally {
    // 提交（或用户取消）后再把新单 id 写回地址栏：此时重建实例无副作用，新实例挂载时会重新拉取最新状态
    if (!route.params.id && form.requisitionId) {
      try { await router.replace({ name: 'ProductionSupplementEdit', params: { id: String(form.requisitionId) } }) } catch (_) {}
    }
  }
}



async function doCancel() {
  if (!form.requisitionId) { ElMessage.warning(tt('请先保存单据')); return }
  if (!cancelReason.value || !cancelReason.value.trim()) {
    ElMessage.warning(tt('请输入取消原因'))
    return
  }
  try {
    await cancelMaterialRequisition(form.requisitionId, cancelReason.value)
    ElMessage.success(tt('取消成功'))
    cancelDialog.value = false
    cancelReason.value = ''
    await loadDetail()
  } catch (e) {
    ElMessage.error(e.message || tt('取消失败'))
  }
}

async function doClose() {
  if (!form.requisitionId) { ElMessage.warning(tt('请先保存单据')); return }
  try {
    await closeMaterialRequisition(form.requisitionId, closeReason.value || '')
    ElMessage.success(tt('关闭成功'))
    closeDialog.value = false
    closeReason.value = ''
    await loadDetail()
  } catch (e) {
    ElMessage.error(e.message || tt('关闭失败'))
  }
}

async function onDelete() {
  if (!form.requisitionId) { ElMessage.warning(tt('请先保存单据')); return }
  try {
    await ElMessageBox.confirm(`${tt("确认删除补料单 ")}${form.requisitionNo}?`, tt('提示'), { type: 'warning' })
    await deleteMaterialRequisition(form.requisitionId)
    ElMessage.success(tt('删除成功'))
    router.push('/production/supplements')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('删除失败'))
  }
}
async function loadDetail() {
  const __detailId = route.params.id || form.requisitionId || ''
  if (__detailId) {
    try {
      const res = await getMaterialRequisition(__detailId)
      Object.assign(form, res, { billType: BILL_TYPE })
      // 单据自定义字段：读取已保存的值并回填到表单（只覆盖自定义字段键）
      if (headerCustomFieldMap.size > 0 && form.requisitionId) {
        try {
          const headerValues = await loadCustomFieldValues('material_requisition', form.requisitionId)
          Object.assign(form, headerValues)
        } catch (e) {
          console.warn('[ProductionSupplementEdit] 加载单据自定义字段值失败:', e)
        }
      }
      if (!form.items) form.items = []
      calcTotal()
      // 若存在工单号，预加载该工单下的报工单供选择
      if (form.workOrderNo) {
        await loadSourceReports()
      }
    } catch (e) {
      ElMessage.error(e.message || tt('加载失败'))
    }
  } else {
    // 新建时默认当前用户为补料人（可修改）
    const me = await getCurrentEmployee()
    if (me && !form.employeeId) {
      form.employeeId = me.employeeId
      form.employeeName = me.employeeName
    }
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
  getId: () => form.requisitionId,
  api: {
    approve: (id) => approveMaterialRequisition(id, localStorage.getItem('username') || 'system'),
    reject: rejectMaterialRequisition,
    reverseApprove: reverseApproveMaterialRequisition,
    close: (id) => closeMaterialRequisition(id, ''),
    reopen: reopenMaterialRequisition,
    void: cancelMaterialRequisition,
    unvoid: unvoidMaterialRequisition,
    delete: deleteMaterialRequisition
  },
  reload: loadDetail,
  router,
  listPath: '/production/supplements',
  newPath: '/production/supplements/edit',
  billLabel: '生产补料单'
})

// 加载单据自定义字段定义（生产补料单）。失败静默降级：只打印告警，绝不阻塞页面。
async function loadCustomFields() {
  try {
    const result = await loadCustomFieldDefinitions('material_requisition')
    headerCustomSections.value = result.sections
    headerCustomFieldMap.clear()
    result.fieldMap.forEach((v, k) => headerCustomFieldMap.set(k, v))
    headerFormulaFields.value = result.formulaFields
  } catch (e) {
    console.warn('[ProductionSupplementEdit] 加载单据自定义字段定义失败:', e)
  }
}

// 保存单据自定义字段值（生产补料单）。保存失败向上抛出，由 onSave 统一提示，避免出现"假成功"。
async function saveHeaderCustomFields() {
  if (headerCustomFieldMap.size === 0) return
  const id = form.requisitionId
  if (!id) return
  const headerValues = {}
  for (const key of headerCustomFieldMap.keys()) {
    if (key in form) headerValues[key] = form[key]
  }
  await saveCustomFieldValues('material_requisition', id, headerValues, headerFormulaFields.value)
}

const printVisible = ref(false)
function handlePrint() {
  if (!form.requisitionId) { ElMessage.warning(tt('请先保存单据后再打印')); return }
  printVisible.value = true
}

// 加载下拉基础数据——独立函数，供 onMounted 和切回标签页时复用
  async function loadBaseData() {
  // 全站商业口径总开关（启用含税业务 / 启用外币业务）：优先拉取最新值
  await ensureCommercialSwitches()
  try {
      const [wh, mat, cur, dep, emp, unitRes] = await Promise.all([
        listAllWarehouse(), listAllMaterial(), listAllCurrency(),
        listAllDepartment(), listAllEmployee(), listAllUnit()
      ])
      warehouses.value = wh || []
      materials.value = mat || []
      // 行内下拉附加字段：物料到位后按批预取（不阻塞渲染）
      mcf.preloadAll(materials.value)
      currencies.value = cur || []
      // 外币业务关闭且为新增单据：默认本位币、汇率锁 1（老单据保持原币，仅供查看）
      if (!foreignCurrencyEnabled.value && !isEdit.value) {
        const base = (cur || []).find(c => c.isBaseCurrency === 1 || c.isBaseCurrency === true) || (cur || [])[0]
        if (base) {
          form.currencyId = base.currencyId
          form.exchangeRate = Number(base.exchangeRate || 1) || 1
        }
      }
      departments.value = dep || []
      employees.value = emp || []
      units.value = unitRes || []
    } catch (e) {
      console.warn('下拉数据加载失败:', e)
    }
}

// ===== 复制功能 =====
const { applyCopy } = useCopyBill({
  route,
  loadFn: getMaterialRequisition,
  form,
  idField: 'requisitionId',
  noField: 'requisitionNo',
  itemIdField: 'requisitionItemId',
  itemsField: 'items',
  afterCopy: () => calcTotal(),
  // 表头自定义字段随复制带出（生产补料单自定义字段为表头级 ext_data）
  customField: { entity: 'material_requisition' }
})

onMounted(async () => {
  // 物料自定义字段：定制开关 + 字段定义（失败静默降级）
  await mcf.init()
  // 该单据类型是否登记了「来源单据」配置（未登记则不显示页签）
  isBillLinkRegistered('MATERIAL_SUPPLEMENT')
    .then(v => { billLinkRegistered.value = !!v })
    .catch(() => { billLinkRegistered.value = false })
  // 单据自定义字段定义（失败静默降级，不阻塞页面）
  await loadCustomFields()
  loadCollapsed()
  await loadBaseData()
  const copied = await applyCopy()
  if (!copied) {
    await loadDetail()
  }
  if (!isEdit.value) {
    autoFillDocCodeOnCreate(form, 'requisitionNo')
  }
})

// 切回标签页时自动刷新下拉基础数据（只刷下拉，不动表单草稿），避免档案改过后这里还是旧数据
useBaseDataRefresh(loadBaseData)


  </script>

<style scoped lang="scss">
.production-supplement-edit {
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
  min-width: 1280px;
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

.table-select {
  :deep(.el-select__wrapper) {
    box-shadow: none;
  }
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
.opt-model {
  float: right;
  color: #909399;
  font-size: 11px;
}

/* 行内下拉附加字段（自定义字段「字段名：值」）：灰色小字，仅在有值时渲染 */
.opt-cf {
  margin-left: 8px;
  color: #909399;
  font-size: 11px;
  white-space: normal;
  word-break: break-word;
}
// ===== 金额汇总 =====
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
  .production-supplement-edit { padding: 8px; }
  .page-header { flex-direction: column; gap: 8px; align-items: stretch; }
  .base-form .section-fields { grid-template-columns: 1fr; }
  .summary-area { flex-direction: column; }
}
</style>


<!-- 非 scoped：物料下拉的 popper 被 teleport 到 body，scoped 选择器无法命中，只能配全局类名（popper-class="material-select-popper"） -->
<style>
/* 面板加宽：Element Plus 给 .el-select-dropdown 设的是行内 min-width = 触发框宽度-2px，覆盖不了，
   因此在 popper 根节点设 min-width；选项改为换行，避免内容被 ellipsis 截断 */
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
