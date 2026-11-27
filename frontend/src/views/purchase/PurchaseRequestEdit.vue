<template>
  <div class="purchase-request-edit">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div class="header-left">
        <div class="back-btn" @click="$router.push('/purchase/requests')">
          <el-icon><ArrowLeft /></el-icon>
          <span>{{ $tt("返回列表") }}</span>
        </div>
        <div class="title-area">
          <div class="title-row">
            <el-icon class="title-icon"><Document /></el-icon>
            <span class="page-title">{{ isEdit ? '采购申请单' : '新增采购申请单' }}</span>
            <span v-if="form.requestNo" class="order-no">
              <el-icon><Collection /></el-icon>
              {{ form.requestNo }}
            </span>
            <el-tag v-if="form.status" size="small" :type="statusTagType(form.status)" effect="plain" class="status-tag">{{ $dt(form.status) }}</el-tag>
            <el-tag v-if="form.isUrgent === 1" size="small" type="danger" effect="plain">{{ $tt("紧急") }}</el-tag>
            <el-tag v-if="form.isClosed === 1" size="small" type="success" effect="plain">{{ $tt("已关闭") }}</el-tag>
          </div>
        </div>
      </div>
      <div class="header-right">
        <el-button v-permission="perm('view')" v-if="!(!form.requestId && !form.id)" @click="changeRecordVisible = true">
          <el-icon><Memo /></el-icon>{{ $tt('操作日志') }}
        </el-button>
        <PushDownButton v-if="BillStatus.canPushDown(form.status, form.closeStatus)" source-type="PurchaseRequest" :source-doc="form" />
        <BillActions bill-type="PURCHASE_REQUEST"
        :supported-actions="supportedActions"
          :status="form.status"
          :close-status="form.closeStatus"
          :is-new="!form.requestId && !form.id"
          :show-print="false"
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
        <el-form :model="form" :rules="rules" ref="headerFormRef" label-width="80px" class="base-form">
          <!-- 单据信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>单据信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('申请单号')">
                <el-input v-model="form.requestNo" :placeholder="docCodePlaceholder" :disabled="!isDraft" class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('申请日期')" prop="requestDate" required>
                <el-date-picker v-model="form.requestDate" type="date" value-format="YYYY-MM-DD" class="field-input" :disabled="readOnly" />
              </el-form-item>
              <el-form-item :label="$tf('需求日期')" prop="demandDate" required>
                <el-date-picker v-model="form.demandDate" type="date" value-format="YYYY-MM-DD" class="field-input" :disabled="readOnly" />
              </el-form-item>
              <el-form-item :label="$tf('申请类型')" prop="applyType" required>
                <el-select v-model="form.applyType" :placeholder="$tt('选择申请类型')" class="field-input" :disabled="readOnly">
                  <el-option :label="$tf('普通采购')" value="普通采购" />
                  <el-option :label="$tf('紧急采购')" value="紧急采购" />
                  <el-option :label="$tf('生产采购')" value="生产采购" />
                  <el-option :label="$tf('研发采购')" value="研发采购" />
                  <el-option :label="$tf('设备采购')" value="设备采购" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('是否紧急')">
                <el-switch v-model="form.isUrgent" :active-value="1" :inactive-value="0" :disabled="readOnly" />
                <el-tag size="small" style="margin-left:8px;" :type="form.isUrgent ? 'danger' : 'info'">{{ form.isUrgent ? '紧急' : '普通' }}</el-tag>
              </el-form-item>
            </div>
          </div>

          <!-- 申请信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>申请信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('申请部门')" prop="departmentId" required>
                <el-select v-model="form.departmentId" filterable :placeholder="$tt('选择部门')" class="field-input" :disabled="readOnly" @change="onDepartmentChange">
                  <el-option v-for="d in departments" :key="d.deptId" :label="d.deptName" :value="d.deptId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('申请人')" prop="applicantId" required>
                <el-select v-model="form.applicantId" filterable :placeholder="$tt('选择申请人')" class="field-input" :disabled="readOnly" @change="onApplicantChange">
                  <el-option v-for="e in employees" :key="e.employeeId" :label="e.employeeName" :value="e.employeeId" />
                </el-select>
              </el-form-item>
            </div>
          </div>

          <!-- 交易信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>交易信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('付款条件')">
                <PaymentTermSelect v-model="form.paymentTermId" :disabled="readOnly" @change="onPaymentTermChange" />
              </el-form-item>
              <el-form-item :label="$tf('结算方式')">
                <el-select v-model="form.settleModeId" filterable clearable :placeholder="$tt('选择结算方式')" class="field-input" :disabled="readOnly" @change="onSettleChange">
                  <el-option v-for="s in settleTypes" :key="s.settleModeId" :label="s.settleModeName" :value="s.settleModeId" />
                </el-select>
              </el-form-item>
            </div>
          </div>

          <!-- 自定义字段 -->
          <div class="info-section" v-for="section in headerCustomSections" :key="section.title" v-if="headerCustomSections.length > 0">
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
                  :disabled="readOnly"
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
                  :disabled="readOnly"
                />
                <el-switch
                  v-else-if="field.type === 'switch'"
                  v-model="form[field.prop]"
                  :active-value="field.activeValue !== undefined ? field.activeValue : 1"
                  :inactive-value="field.inactiveValue !== undefined ? field.inactiveValue : 0"
                  :disabled="readOnly"
                />
                <el-date-picker
                  v-else-if="field.type === 'date'"
                  v-model="form[field.prop]"
                  type="date"
                  value-format="YYYY-MM-DD"
                  :placeholder="field.placeholder || '选择日期'"
                  class="field-input"
                  :disabled="readOnly"
                />
                <el-input
                  v-else-if="field.type === 'textarea'"
                  v-model="form[field.prop]"
                  type="textarea"
                  :rows="field.rows || 3"
                  :placeholder="field.placeholder || '请输入'"
                  class="field-input"
                  :disabled="readOnly"
                />
                <el-input
                  v-else
                  v-model="form[field.prop]"
                  :placeholder="field.placeholder || '请输入'"
                  class="field-input"
                  :disabled="readOnly"
                />
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
                <el-input v-model="form.remark" type="textarea" :rows="2" class="field-input" :disabled="readOnly" :placeholder="$tt('请输入备注信息')" />
              </el-form-item>
            </div>
          </div>
        </el-form>
      </div>
    </div>

    <!-- 明细卡片（通用组件 BillItemsTable：卡片/列设置/动态列/自定义字段列/汇总 全站共用一套） -->
    <BillItemsTable
      v-model:collapsed="collapsed.items"
      :items="form.items"
      page-key="PurchaseRequestEdit_items"
      title="申请明细"
      subtitle="物料明细"
      col-setting-title="物料明细列设置"
      :columns="detailColumns"
      :custom-columns="itemCustomColumns"
      :readonly="readOnly"
      :show-total-qty="false"
      :summary-items="summaryItems"
      :source-link-click="goToSourceBill"
      @row-change="calcTotal"
      @remove="calcTotal"
    >
      <template #actions>
        <el-button v-permission="perm('pick')" size="small" @click="pickerVisible = true" :disabled="readOnly">
          <el-icon><Grid /></el-icon>从物料库选择
        </el-button>
        <el-button v-permission="perm('addRow')" size="small" type="primary" @click="addItem" :disabled="readOnly">
          <el-icon><Plus /></el-icon>添加明细
        </el-button>
      </template>
      <template #cell-materialId="{ row }">
        <el-select
          v-model="row.materialId"
          filterable
          :placeholder="materialSelectPlaceholder"
          @change="(v) => onMaterialChange(row, v)"
          :disabled="readOnly"
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
      <template #cell-suggestedSupplierId="{ row }">
        <el-select v-model="row.suggestedSupplierId" filterable :placeholder="$tt('选择供应商')" style="width:100%" size="small" @change="(v) => onSupplierChange(row, v)" :disabled="readOnly">
          <el-option v-for="s in withFallbackOption(suppliers, 'suggestedSupplierId', 'supplierName', row)" :key="s.supplierId" :label="s.supplierName" :value="s.supplierId" />
        </el-select>
      </template>
      <template #cell-demandDate="{ row }">
        <el-date-picker v-model="row.demandDate" type="date" value-format="YYYY-MM-DD" style="width:100%" size="small" :disabled="readOnly" />
      </template>
      <template #cell-rowRemark="{ row }">
        <el-input v-model="row.rowRemark" size="small" :disabled="readOnly" />
      </template>
      <template #cell-batchNo="{ row }">
        <el-input v-model="row.batchNo" size="small" :placeholder="row._isLotControl ? '必填' : ''" :disabled="readOnly" />
        <span v-if="row._isLotControl" class="required-mark">*</span>
      </template>
      <template #cell-productionDate="{ row }">
        <el-date-picker v-if="row._isExpiryControl" v-model="row.productionDate" type="date" value-format="YYYY-MM-DD" size="small" style="width:100%" :disabled="readOnly" @change="(v) => onProductionDateChange(row, v)" />
        <span v-else class="thumb-placeholder">—</span>
      </template>
      <template #cell-expiryDate="{ row }">
        <el-date-picker v-if="row._isExpiryControl" v-model="row.expiryDate" type="date" value-format="YYYY-MM-DD" size="small" style="width:100%" disabled />
        <span v-else class="thumb-placeholder">—</span>
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
          type="purchase-request"
          :disabled="readOnly"
          button-text="上传附件"
          tip-text="支持图片、PDF、Word、Excel 等文件"
        />
      </div>
    </div>

    <!-- 审核流转（真实审批进度，通用组件） -->
    <ApprovalFlowTimeline bill-type="PURCHASE_REQUEST" :bill-id="form.requestId" :status="form.status" />

    <!-- 取消弹窗 -->
    <el-dialog v-model="cancelDialog" :title="$tf('取消采购申请单')" width="420px">
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
    <ChangeRecordDialog v-model="changeRecordVisible" bill-type="采购申请" :bill-no="form.requestNo || ''" />

      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('来源单据')" name="source" :disabled="!form.requestId">
        <BillLinkPanel v-if="topTab === 'source'" bill-type="PURCHASE_REQUEST" :bill-id="form.requestId" mode="source" />
      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('去向单据')" name="target" :disabled="!form.requestId">
        <BillLinkPanel v-if="topTab === 'target'" bill-type="PURCHASE_REQUEST" :bill-id="form.requestId" mode="target" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { withFallbackOption } from '@/utils/labelFallback'
import { canSeeRoute } from '@/utils/versionGate'
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useBaseDataRefresh } from '@/composables/useBaseDataRefresh'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft, Document, Collection, Plus, Grid, Link,
  ArrowRight, Check, TopRight, ArrowDown, CircleCheck, CircleClose,
  RefreshLeft, Close, Memo
} from '@element-plus/icons-vue'
import { listAllDepartment } from '@/api/basedata/department'
import { listAllEmployee } from '@/api/basedata/employee'
import { getCurrentEmployee } from '@/composables/useCurrentEmployee'
import { listAllMaterial } from '@/api/basedata/material'
import { listAllSupplier } from '@/api/basedata/supplier'
import { listAllUnit } from '@/api/basedata/unit'
import { listAllPaymentTerm } from '@/api/basedata/payment-term'
import { listAllSettle } from '@/api/basedata/settle'
import FileUploader from '@/components/FileUploader.vue'
import ChangeRecordDialog from '@/components/ChangeRecordDialog.vue'
import PushDownButton from '@/components/PushDownButton.vue'
import ApprovalFlowTimeline from '@/components/bill/ApprovalFlowTimeline.vue'
// 2026-09-20 补漏：本页模板已在用「从物料库选择」弹窗与物料过滤工具，但这两个 import 一直缺失
// （既有缺陷：弹窗打不开、物料下拉过滤抛 ReferenceError），与本次明细表迁移无关，顺手补齐
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
import PaymentTermSelect from '@/components/PaymentTermSelect.vue'
import { filterMaterials, buildMaterialPlaceholder } from '@/utils/materialFilter'
import {
  loadCustomFieldDefinitions,
  loadCustomFieldValues,
  saveCustomFieldValues,
  batchLoadCustomFieldValues,
  customFieldsToTableColumns,
  injectMaterialCustomFields,
  copyCustomFieldValues,
  remapItemIdsAfterSave,
} from '@/composables/useCustomField'
// 明细通用组件（卡片折叠/列设置/动态列/自定义字段列/汇总 全站共用一套）
import BillItemsTable from '@/components/bill/BillItemsTable.vue'
import { useBillLineStock, BILL_STOCK_COLUMNS } from '@/composables/useBillLineStock'
import { triggerListRefresh } from '@/composables/useAutoRefresh'
import { useAutoDocCode } from '@/composables/useAutoDocCode'

import * as BillStatus from '@/utils/bill-status'
import { useFieldPermission } from '@/composables/useFieldPermission'
import { useFieldRequiredMark } from '@/utils/fieldRequiredMark'
import { confirmSubmit, submitSuccessMessage } from '@/utils/submit-confirm'
import {
  getPurchaseRequest,
  savePurchaseRequest,
  updatePurchaseRequest,
  submitPurchaseRequest,
  approvePurchaseRequest,
  reverseApprovePurchaseRequest,
  cancelPurchaseRequest,
  rejectPurchaseRequest,
  closePurchaseRequest,
  closeOrderPurchaseRequest,
  reopenPurchaseRequest,
  voidPurchaseRequest,
  unvoidPurchaseRequest,
  deletePurchaseRequest
} from '@/api/purchase/request'
import BillActions from '@/components/bill/BillActions.vue'
import BillLinkPanel from '@/components/bill/BillLinkPanel.vue'
import { isBillLinkRegistered } from '@/api/billLink'
import { useBillActions } from '@/composables/useBillActions'
import { useCopyBill } from '@/composables/useCopyBill'
import { tt } from '@/i18n'
import { useMaterialCustomFields } from '@/composables/useMaterialCustomFields'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('purchase:requests')

// 单据编码自动生成
const { docCodeDisabled, docCodePlaceholder, autoFillDocCodeOnCreate } = useAutoDocCode('PURCHASE_REQUEST')

// 物料明细列定义（通用组件 BillItemsTable 消费）：editor = 单元格控件，type = 纯展示格式；
// 物料/建议供应商/需求日期/行备注/批次号/生产日期/有效期至列由页面提供插槽（#cell-<prop>），来源单号列由组件的 sourceLinkClick 回调跳转来源单
const detailColumns = [
  { prop: 'materialMainImage', label: '图片', width: 60, align: 'center', type: 'image' },
  { prop: 'materialId', label: '商品名称', minWidth: 280 },
  { prop: 'materialCode', label: '商品编号', width: 130 },
  ...BILL_STOCK_COLUMNS,
  { prop: 'model', label: '规格', width: 110, type: 'text' },
  { prop: 'unit', label: '单位', width: 60, align: 'center', type: 'text' },
  { prop: 'applyQty', label: '申请数量', width: 100, editor: 'number', precision: 4 },
  { prop: 'approvedQty', label: '审批数量', width: 100, editor: 'number', precision: 4 },
  { prop: 'suggestedSupplierId', label: '建议供应商', width: 180 },
  { prop: 'demandDate', label: '需求日期', width: 140 },
  { prop: 'rowRemark', label: '行备注', width: 150 },
  { prop: 'batchNo', label: '批次号', width: 120 },
  { prop: 'productionDate', label: '生产日期', width: 140 },
  { prop: 'expiryDate', label: '有效期至', width: 140 },
  { prop: 'sourceBillNo', label: '来源单号', minWidth: 140, type: 'sourceLink' },
  { prop: 'sourceRowNo', label: '来源行号', width: 80, align: 'center', type: 'text' }
]
// 汇总区（通用组件 BillItemsTable 按此渲染；本页原本没有「合计数量」行 → showTotalQty=false，数量口径与迁移前一致）
const summaryItems = computed(() => [
  { label: '明细行数：', value: form.items.length },
  { label: '申请总数量：', value: Number(form.totalQty || 0).toFixed(4) },
  { label: '审批总数量：', value: approvedTotalQty.value, total: true }
])
useBillLineStock(() => form.items)
// ===== 物料库弹窗的列定义 / 列设置 / 图片渲染 已收进通用组件 MaterialPickerDialog =====
// ===== 明细列设置（显隐/排序/自定义字段列/合计行）已收进通用组件 BillItemsTable =====

const route = useRoute()
const router = useRouter()
const formRef = ref()
const headerFormRef = ref()

const isEdit = computed(() => !!route.params.id)

// 仅草稿态允许编辑（单号/业务字段/自定义字段）；已生效/待审批等只读
// 来源单据类型 -> 列表路由映射（用于明细行来源单号点击跳转）
const SOURCE_BILL_ROUTE_MAP = {
  '销售报价单': '/sale/quotes',
  '销售合同': '/sale/contracts',
  '销售订单': '/sale/orders',
  '销售出库单': '/sale/deliveries',
  '销售退货单': '/sale/returns',
  '销售发票': '/sale/invoices',
  '采购合同': '/purchase/contracts',
  '采购申请单': '/purchase/requests',
  '采购订单': '/purchase/orders',
  '采购入库单': '/purchase/inbounds',
  '采购退货单': '/purchase/returns',
  '采购发票': '/purchase/invoices',
  '生产工单': '/production/workorders',
  '生产入库单': '/production/receipts',
  '生产领料单': '/inventory/material-requisitions',

  '工序汇报单': '/production/operation-reports',
  '委外加工单': '/subcontract/orders',
  'MRP计划': '/mrp/plans',
  'MRP': '/mrp/plans',
  '打样单': '/sample/orders'
}

function goToSourceBill(row) {
  if (!row || !row.sourceBillNo) return
  const billType = row.sourceBillType || row.sourceType
  const routeBase = SOURCE_BILL_ROUTE_MAP[billType]
  if (routeBase && !canSeeRoute(routeBase)) {
    ElMessage.warning(`${tt("该来源单据不属于当前版本或无权限查看：")}${billType || ''}`)
    return
  }
  if (routeBase) {
    router.push(`${routeBase}?keyword=${encodeURIComponent(row.sourceBillNo)}`)
  } else {
    ElMessage.warning(`${tt("暂不支持跳转到 ")}${billType || tt('该来源单据')}`)
  }
}

const STORAGE_KEY = 'purchase-request-edit-collapsed'

const collapsed = reactive({
  base: true,
  items: true,
  attach: false,
  audit: false,
})

const headerCustomSections = ref([])
const headerCustomFieldMap = new Map()
const headerFormulaFields = ref([])
const itemCustomFields = ref([])
// 物料自定义字段：行内下拉附加展示 + 物料库弹窗全字段（逻辑集中在 composable）
const mcf = useMaterialCustomFields()
const itemCustomColumns = ref([])
const materialCustomValueMap = ref({})

async function loadCustomFields() {
  const headerResult = await loadCustomFieldDefinitions('purchase_request')
  headerCustomSections.value = headerResult.sections
  headerCustomFieldMap.clear()
  headerResult.fieldMap.forEach((v, k) => headerCustomFieldMap.set(k, v))
  headerFormulaFields.value = headerResult.formulaFields

  const itemResult = await loadCustomFieldDefinitions('purchase_request_item')
  itemCustomFields.value = Array.from(itemResult.fieldMap.values())
  itemCustomColumns.value = customFieldsToTableColumns(itemCustomFields.value, { editable: isDraft.value, listShowOnly: false })
// 草稿态变化时重新生成明细自定义字段列（editable 控制）
watch(isDraft, () => {
  if (itemCustomFields.value && itemCustomFields.value.length > 0) {
    itemCustomColumns.value = customFieldsToTableColumns(itemCustomFields.value, { editable: isDraft.value, listShowOnly: false });
  }
});

}

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

const departments = ref([])
const employees = ref([])
const materials = ref([])
const suppliers = ref([])
const paymentTerms = ref([])
const settleTypes = ref([])
const units = ref([])

const cancelDialog = ref(false)
const cancelReason = ref('')
const changeRecordVisible = ref(false)

const pickerVisible = ref(false)

const form = reactive({
  requestId: null,
  requestNo: '',
  requestDate: new Date().toISOString().slice(0, 10),
  departmentId: null,
  applyDeptName: '',
  applicantId: null,
  applierName: '',
  applyType: '普通采购',
  demandDate: '',
  status: '草稿',
  totalQty: 0,
  sourceType: '手动',
  sourceBillNo: '',
  isUrgent: 0,
  paymentTermId: null,
  paymentTermName: '',
  settleModeId: null,
  settleName: '',
  prepaidRate: 0,
  prepaidAmount: 0,
  discountRate: 0,
  discountAmount: 0,
  approvedBy: '',
  approvedAt: '',
  isClosed: 0,
  closeDate: '',
  createdBy: '',
  createdAt: '',
  updatedBy: '',
  updatedAt: '',
  remark: '',
  attachmentUrls: '',
  items: []
})


const isDraft = computed(() => BillStatus.canEdit(form.status, form.closeStatus))
const rules = {
  requestDate: [{ required: true, message: tt('请选择申请日期'), trigger: 'change' }],
  departmentId: [{ required: true, message: tt('请选择申请部门'), trigger: 'change' }],
  applicantId: [{ required: true, message: tt('请选择申请人'), trigger: 'change' }],
  applyType: [{ required: true, message: tt('请选择申请类型'), trigger: 'change' }],
  demandDate: [{ required: true, message: tt('请选择需求日期'), trigger: 'change' }]
}

const readOnly = computed(() => form.status === '已审批' || form.status === '已关闭' || !BillStatus.canEdit(form.status, form.closeStatus))
const stSubmitted = computed(() => ['待审批', '待审核', '已审批', '已生效'].includes(form.status))
const stApproved = computed(() => ['已审批', '已生效'].includes(form.status))

const approvedTotalQty = computed(() => {
  return form.items.reduce((s, i) => s + Number(i.approvedQty || 0), 0).toFixed(4)
})

function statusTagType(s) {
  return { '草稿': 'info', '待审批': 'warning', '待审核': 'warning', '已审批': 'success', '已生效': 'success', '已关闭': 'danger' }[s] || 'info'
}
function onDepartmentChange(id) {
  const d = departments.value.find(x => x.deptId === id)
  if (d) form.applyDeptName = d.deptName
}

function onApplicantChange(id) {
  const e = employees.value.find(x => x.employeeId === id)
  if (e) form.applierName = e.employeeName
}

// 付款条件联动：回填名称、计算现金折扣与预付金额
function onPaymentTermChange(id) {
  const p = (id && typeof id === 'object') ? id : paymentTerms.value.find(x => x.paymentTermId === id)
  if (!p) {
    form.paymentTermName = ''
    form.prepaidRate = 0
    form.prepaidAmount = 0
    return
  }
  form.paymentTermName = p.paymentTermName
  const baseAmount = Number(form.netAmount || form.totalAmount || 0)
  if (p.discountRate) {
    form.discountRate = p.discountRate
    form.discountAmount = (baseAmount * p.discountRate / 100).toFixed(2)
  }
  if (p.prepaidRate) {
    form.prepaidRate = p.prepaidRate
    form.prepaidAmount = Number((baseAmount * p.prepaidRate / 100).toFixed(2))
  } else {
    form.prepaidRate = 0
    form.prepaidAmount = 0
  }
}

// 结算方式联动：回填名称并兼容原有 paymentMethod 字段
function onSettleChange(id) {
  const s = settleTypes.value.find(x => x.settleModeId === id)
  if (s) {
    form.settleName = s.settleModeName
    form.paymentMethod = s.settleModeName  // 兼容原有字段
  } else {
    form.settleName = ''
  }
}

async function onMaterialChange(row, id) {
  const m = materials.value.find(x => x.materialId === id)
  if (m) {
    row.materialCode = m.materialCode
    row.materialName = m.materialName
    row.materialMainImage = m.mainImage || ''
    row.model = m.model || m.specification
    // Material 实体没有 unitName 字段，根据 unitId 从计量单位列表查找单位名称
    const unitId = m.purchaseUnitId || m.baseUnitId || null
    row.unitId = unitId
    if (unitId && units.value.length > 0) {
      const u = units.value.find(x => x.unitId === unitId)
      row.unit = u ? u.unitName : ''
    } else {
      row.unit = ''
    }
    // 物料批次/保质期管理联动
    row._isLotControl = m.isLotControl || false
    row._isExpiryControl = m.isExpiryControl || false
    row._expiryDays = m.expiryDays || 0
    if (m.isExpiryControl && m.expiryDays) {
      if (!row.productionDate) {
        row.productionDate = new Date().toISOString().slice(0, 10)
      }
      const d = new Date(row.productionDate)
      d.setDate(d.getDate() + Number(m.expiryDays))
      row.expiryDate = d.toISOString().slice(0, 10)
    }
    if (m && m.materialId && itemCustomFields.value.length > 0) {
      if (!materialCustomValueMap.value[m.materialId]) {
        const map = await batchLoadCustomFieldValues('material', [m.materialId])
        Object.assign(materialCustomValueMap.value, map)
      }
      injectMaterialCustomFields(row, m.materialId, materialCustomValueMap.value, itemCustomFields.value)
    }
  }
}

const onProductionDateChange = (row, date) => {
  if (row._expiryDays && date) {
    const d = new Date(date)
    d.setDate(d.getDate() + Number(row._expiryDays))
    row.expiryDate = d.toISOString().slice(0, 10)
  }
}

function onSupplierChange(row, id) {
  const s = suppliers.value.find(x => x.supplierId === id)
  if (s) {
    row.suggestedSupplierName = s.supplierName
    row.suggestedSupplierCode = s.supplierCode || ''
  }
}

function calcTotal() {
  const total = form.items.reduce((s, i) => s + Number(i.applyQty || 0), 0)
  form.totalQty = total.toFixed(4)
}

function addItem() {
  form.items.push({
    materialId: null, materialCode: '', materialName: '', model: '', unit: '',
    applyQty: 1, approvedQty: 0,
    suggestedSupplierId: null, suggestedSupplierName: '',
    sourceType: '手动', sourceBillNo: '', sourceRowNo: '',
    demandDate: form.demandDate, rowRemark: '',
    _isLotControl: false, _isExpiryControl: false, _expiryDays: 0, productionDate: '', expiryDate: '', batchNo: ''
  })
  calcTotal()
}

/** 参与「物料」下拉搜索的物料自定义字段：勾了「下拉显示」（受定制总开关控制）或勾了「可搜索」的字段 */
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
  if (!val) {
    row._filteredMaterials = null
    return
  }
  // 过滤用「已预取全量值」的 mcf.valueMap（本页 materialCustomValueMap 只含已选中的物料，会导致搜不到）
  row._filteredMaterials = filterMaterials(materials.value, val, mcf.valueMap.value, materialSearchableFields.value)
}

/**
 * 物料库弹窗「确认添加」回调（弹窗本体 = 通用组件 MaterialPickerDialog）
 * - 组件内已批量取好选中物料的「全部自定义字段值」，并随 confirm 回传 valueMap
 * - 这里把值并入本页值表（与行内下拉取值共用同一份，不再二次请求），再逐行组装明细行
 */
async function onMaterialPicked({ rows: picked, valueMap }) {
  if (!picked || picked.length === 0) return
  if (valueMap) {
    Object.assign(materialCustomValueMap.value, valueMap)
  }
  if (itemCustomFields.value.length > 0) {
    const matIds = picked.map(m => m.materialId).filter(id => !materialCustomValueMap.value[id])
    if (matIds.length > 0) {
      const map = await batchLoadCustomFieldValues('material', matIds)
      Object.assign(materialCustomValueMap.value, map)
    }
  }
  picked.forEach(m => {
    const row = {
      materialId: m.materialId,
      materialCode: m.materialCode || '',
      materialName: m.materialName || '',
      model: m.model || '',
      unit: '',
      applyQty: 1,
      approvedQty: 0,
      suggestedSupplierId: null,
      suggestedSupplierName: '',
      sourceType: '手动',
      sourceBillNo: '',
      sourceRowNo: '',
      demandDate: form.demandDate,
      rowRemark: ''
    }
    const unitId = m.purchaseUnitId || m.baseUnitId || null
    if (unitId) {
      const u = units.value.find(x => x.unitId === unitId)
      row.unit = u ? u.unitName : ''
    }
    if (itemCustomFields.value.length > 0) {
      injectMaterialCustomFields(row, m.materialId, materialCustomValueMap.value, itemCustomFields.value)
    }
    form.items.push(row)
  })
  calcTotal()
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
  // 宽松草稿保存：不校验必填，允许空供应商、空物料、无明细（用户可以后续补全再提交）
  // 保存宽松：丢弃未选物料的空行（后端这些字段 NOT NULL，无法落库）
  form.items = (form.items || []).filter(r => r && r.materialId)
  calcTotal()
  try {
    if (form.requestId) {
      await updatePurchaseRequest(form.requestId, form)
    } else {
      const res = await savePurchaseRequest(form)
      if (res && res.requestId) form.requestId = res.requestId
      if (res && res.requestNo) form.requestNo = res.requestNo
      // 新建保存后必须把 id 写回地址栏：否则 load() 拿不到 id → 提交/保存后页面状态不刷新（历史缺陷）
      if (!opts.skipRouteSync && form.requestId) { try { await router.replace({ name: 'PurchaseRequestEdit', params: { id: String(form.requestId) } }) } catch (_) {} }
    }
    if (headerCustomFieldMap.size > 0 && form.requestId) {
      const headerValues = {}
      for (const key of headerCustomFieldMap.keys()) {
        if (key in form) headerValues[key] = form[key]
      }
      await saveCustomFieldValues('purchase_request', form.requestId, headerValues, headerFormulaFields.value)
    }
    if (itemCustomFields.value.length > 0 && form.items && form.items.length > 0) {
      // 关键修复（2026-09-22，同销售订单 bug）：保存主表时后端"明细先删后插"，明细主键会变 →
      // 必须先按保存后的新主键重新对齐，否则明细级自定义字段会写到已删除的旧行上（报表永远空白）。
      try {
        const fresh = await getPurchaseRequest(form.requestId)
        remapItemIdsAfterSave(form.items, (fresh && fresh.items) || [], 'requestItemId')
      } catch (e) {
        console.warn('[purchaseRequest] 保存后刷新明细主键失败，明细级自定义字段可能挂到旧行', e)
      }
      const itemKeys = new Set(itemCustomFields.value.map(f => f.fieldKey))
      for (let i = 0; i < form.items.length; i++) {
        const row = form.items[i]
        if (!row.requestItemId && !row.itemId) continue
        const itemId = row.requestItemId || row.itemId
        const values = {}
        for (const key of itemKeys) {
          if (key in row) values[key] = row[key]
        }
        await saveCustomFieldValues('purchase_request_item', itemId, values)
      }
    }
    triggerListRefresh('/purchase/requests')
    if (!silent) ElMessage.success(tt('保存成功'))

    return true
  } catch (e) {
    ElMessage.error(e.message || tt('保存失败'))
  return false
  }
}

async function onSubmit() {
  flushInputNumbers()
  // 提交严格校验（保存宽松、提交严格）：表单必填 + 明细完整性
  try {
    await Promise.all([
      formRef.value?.validate(),
      headerFormRef.value?.validate()
    ])
  } catch (e) {
    ElMessage.warning(tt('请填写必填项后再提交'))
    return
  }
  if (!form.items || form.items.length === 0) {
    ElMessage.warning(tt('请至少添加一条明细'))
    return
  }
  // 无论新建还是已有草稿，提交前一律先静默保存（把当前页面所见落库，避免提交旧数据）
  const saved = await onSave(true, { skipRouteSync: true })
  if (!saved || !form.requestId) return
  try {
    const needApproval = await confirmSubmit({ billType: 'PURCHASE_REQUEST', billLabel: '采购申请单' })
    await submitPurchaseRequest(form.requestId)
    ElMessage.success(submitSuccessMessage(needApproval))
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('提交失败'))
  } finally {
    // 提交（或用户取消）后再把新单 id 写回地址栏：此时重建实例无副作用，新实例挂载时会重新拉取最新状态
    if (!route.params.id && form.requestId) {
      try { await router.replace({ name: 'PurchaseRequestEdit', params: { id: String(form.requestId) } }) } catch (_) {}
    }
  }
}

async function confirmApprove() {
  try {
    await ElMessageBox.confirm(tt('确认审批通过该申请单?'), tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await approvePurchaseRequest(form.requestId)
    ElMessage.success(tt('审批成功'))
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('审批失败'))
  }
}

async function confirmReverseApprove() {
  try {
    await ElMessageBox.confirm(tt('确认重置草稿该申请单? 重置草稿后可继续修改'), tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await reverseApprovePurchaseRequest(form.requestId)
    ElMessage.success(tt('重置草稿成功'))
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('重置草稿失败'))
  }
}

async function confirmClose() {
  try {
    await ElMessageBox.confirm(tt('确认关闭该申请单? 关闭后不可再操作'), tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await closePurchaseRequest(form.requestId)
    ElMessage.success(tt('已关闭'))
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('关闭失败'))
  }
}

async function doCancel() {
  if (!cancelReason.value || !cancelReason.value.trim()) {
    ElMessage.warning(tt('请输入取消原因'))
    return
  }
  try {
    await cancelPurchaseRequest(form.requestId, cancelReason.value)
    ElMessage.success(tt('取消成功'))
    cancelDialog.value = false
    cancelReason.value = ''
    await load()
  } catch (e) {
    ElMessage.error(e.message || tt('取消失败'))
  }
}

async function load() {
  const __detailId = route.params.id || form.requestId || ''
  if (__detailId) {
    try {
      const res = await getPurchaseRequest(__detailId)
      Object.assign(form, res)
      if (headerCustomFieldMap.size > 0 && form.requestId) {
        const headerValues = await loadCustomFieldValues('purchase_request', form.requestId)
        Object.assign(form, headerValues)
      }
      // 结算方式名称回填：后端只落库 settle_mode_id，名称从结算方式下拉列表反查，保证只读/详情区能显示名称
      if (form.settleModeId != null) {
        const st = settleTypes.value.find(x => x.settleModeId === form.settleModeId)
        if (st) form.settleName = st.settleModeName
      }
      if (!form.items) form.items = []
      if (itemCustomFields.value.length > 0 && res.items && res.items.length > 0) {
        const itemIds = res.items.map(it => it.itemId || it.requestItemId).filter(Boolean)
        if (itemIds.length > 0) {
          const itemValuesMap = await batchLoadCustomFieldValues('purchase_request_item', itemIds)
          form.items.forEach(row => {
            const itemId = row.itemId || row.requestItemId
            const values = itemValuesMap[itemId]
            if (values) Object.assign(row, values)
          })
        }
      }
      // 回填物料图片（加载已有单据时后端不返回图片字段，从物料列表回填）
      if (form.items && materials.value && materials.value.length > 0) {
        form.items.forEach(row => {
          if (!row.materialMainImage && row.materialId) {
            const m = materials.value.find(x => x.materialId === row.materialId)
            row.materialMainImage = m?.mainImage || ''
          }
        })
      }
      if (itemCustomFields.value.length > 0 && form.items && form.items.length > 0) {
        const itemIds = form.items.map(it => it.requestItemId || it.itemId).filter(Boolean)
        if (itemIds.length > 0) {
          const itemValuesMap = await batchLoadCustomFieldValues('purchase_request_item', itemIds)
          form.items.forEach(row => {
            const itemId = row.requestItemId || row.itemId
            const values = itemValuesMap[itemId]
            if (values) Object.assign(row, values)
          })
        }
      }
      calcTotal()
    } catch (e) {
      ElMessage.error(e.message || tt('加载失败'))
    }
  } else {
    // 新建时默认当前用户为申请人（可修改）
    const me = await getCurrentEmployee()
    if (me && !form.applicantId) form.applicantId = me.employeeId
  }
}

// ===== 统一单据操作（审核/驳回/作废/删除等）=====
const {
  supportedActions,  rejectDialog, rejectReason, doReject,
  voidDialog, voidReason, doVoid,
  onApprove, handleReject, handleReverseApprove,
  handleClose, handleReopen, handleVoid, handleUnvoid,
  handleDelete, handleCopy
} = useBillActions({
  form,
  getId: () => form.requestId,
  api: {
    approve: approvePurchaseRequest,
    reject: rejectPurchaseRequest,
    reverseApprove: reverseApprovePurchaseRequest,
    close: closeOrderPurchaseRequest,
    reopen: reopenPurchaseRequest,
    void: voidPurchaseRequest,
    unvoid: unvoidPurchaseRequest,
    delete: deletePurchaseRequest
  },
  reload: load,
  router,
  newPath: '/purchase/requests/new',
  listPath: '/purchase/requests',
  billLabel: '采购申请单'
})

// 加载下拉基础数据——独立函数，供 onMounted 和切回标签页时复用
  async function loadBaseData() {
  try {
      const [deptRes, empRes, matRes, supRes, ptRes, stRes, unitRes] = await Promise.all([
        listAllDepartment(),
        listAllEmployee(),
        listAllMaterial(),
        listAllSupplier(),
        listAllPaymentTerm(),
        listAllSettle(),
        listAllUnit()
      ])
      departments.value = deptRes || []
      employees.value = empRes || []
      materials.value = matRes || []
      // 行内下拉附加字段：物料到位后按批预取（不阻塞渲染）
      mcf.preloadAll(materials.value)
      suppliers.value = supRes || []
      paymentTerms.value = ptRes || []
      settleTypes.value = stRes || []
      units.value = unitRes || []
    } catch (e) {
      console.warn('下拉数据加载失败:', e)
    }
}

// ===== 复制功能（URL 带 ?copy=id 时自动带入源单数据）=====
const { applyCopy } = useCopyBill({
  route,
  loadFn: getPurchaseRequest,
  form,
  idField: 'requestId',
  noField: 'requestNo',
  itemIdField: 'requestItemId',
  itemsField: 'items',
  afterCopy: () => calcTotal(),
  customField: { entity: 'purchase_request', itemEntity: 'purchase_request_item', itemIdField: 'requestItemId' }
})

// ===== 顶级页签：单据 / 来源单据 / 去向单据 =====
const topTab = ref('doc')
const billLinkRegistered = ref(false)

const { isFieldRequired } = useFieldPermission('purchase_request')
useFieldRequiredMark('purchase_request', '.purchase-request-edit')

onMounted(async () => {
  // 物料自定义字段：定制开关 + 字段定义（失败静默降级）
  await mcf.init()
  // 该单据类型是否登记了「来源单据/去向单据」配置（未登记则不显示这两个页签）
  isBillLinkRegistered('PURCHASE_REQUEST')
    .then(v => { billLinkRegistered.value = !!v })
    .catch(() => { billLinkRegistered.value = false })

  loadCollapsed()
  await loadCustomFields()
  await loadBaseData()
  const copied = await applyCopy()
  if (!copied) {
    await load()
  }
  if (!isEdit.value) {
    autoFillDocCodeOnCreate(form, 'requestNo')
  }
})

// 切回标签页时自动刷新下拉基础数据（只刷下拉，不动表单草稿），避免档案改过后这里还是旧数据
useBaseDataRefresh(loadBaseData)


  </script>

<style scoped lang="scss">
.purchase-request-edit {
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

    .custom-hint {
      font-size: 11px;
      font-weight: 400;
      color: #c0c4cc;
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

  .empty-custom {
    grid-column: span 4;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    padding: 12px 0;
    color: #c0c4cc;
    font-size: 12px;
    background: #fafbfc;
    border-radius: 4px;
    border: 1px dashed #ebeef5;
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

.material-thumb {
  width: 32px;
  height: 32px;
  border-radius: 4px;
  border: 1px solid #ebeef5;
  cursor: pointer;
}

.thumb-placeholder {
  color: #c0c4cc;
  font-size: 12px;
}

.required-mark {
  color: #f56c6c;
  margin-left: 2px;
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
// ===== 数量汇总区（放在明细下面） =====
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

@media (max-width: 1400px) {
  .form-groups { grid-template-columns: repeat(3, 1fr); }
}

@media (max-width: 1200px) {
  .form-groups { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 768px) {
  .purchase-request-edit { padding: 8px; }
  .page-header { flex-direction: column; gap: 8px; align-items: stretch; }
  .form-groups { grid-template-columns: 1fr; }
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
