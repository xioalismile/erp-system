<template>
  <div class="material-requisition-edit">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div class="header-left">
        <div class="back-btn" @click="$router.push('/inventory/material-requisitions')">
          <el-icon><ArrowLeft /></el-icon>
          <span>{{ $tt("返回列表") }}</span>
        </div>
        <div class="title-area">
          <div class="title-row">
            <el-icon class="title-icon"><Document /></el-icon>
            <span class="page-title">{{ isEdit ? '生产领料单' : '新增生产领料单' }}</span>
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
        <BillActions bill-type="MATERIAL_REQUISITION"
          :supported-actions="supportedActions"
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
              <el-form-item :label="$tf('领料单号')">
                <el-input v-model="form.requisitionNo" :placeholder="docCodePlaceholder" :disabled="!isDraft" class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('领料日期')" prop="requisitionDate" required>
                <el-date-picker v-model="form.requisitionDate" type="date" value-format="YYYY-MM-DD" class="field-input" :disabled="readonly" />
              </el-form-item>
              <el-form-item :label="$tf('工单号')">
                <el-input v-model="form.workOrderNo" :placeholder="$tt('请输入工单号')" class="field-input" :disabled="readonly" />
              </el-form-item>
              <el-form-item :label="$tf('领料人')">
                <el-select v-model="form.employeeId" filterable :placeholder="$tt('选择领料人')" clearable class="field-input" :disabled="readonly">
                  <el-option v-for="e in employees" :key="e.employeeId" :label="e.employeeName" :value="e.employeeId" />
                </el-select>
              </el-form-item>
            </div>
          </div>

          <!-- 仓库与部门 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>仓库与部门
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('仓库')" prop="warehouseId" required>
                <el-select v-model="form.warehouseId" filterable :placeholder="$tt('选择仓库')" class="field-input" :disabled="readonly" @change="onWarehouseChange">
                  <el-option v-for="w in withFallbackOption(warehouses, 'warehouseId', 'warehouseName', form)" :key="w.stockId" :label="w.stockName" :value="w.stockId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('部门')">
                <el-select v-model="form.departmentId" filterable :placeholder="$tt('选择部门')" clearable class="field-input" :disabled="readonly">
                  <el-option v-for="d in departments" :key="d.deptId" :label="d.deptName" :value="d.deptId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('币种')" prop="currencyId" required>
                <el-select v-model="form.currencyId" :placeholder="$tt('选择币种')" filterable clearable class="field-input" :disabled="readonly || !foreignCurrencyEnabled" @change="onCurrencyChange">
                  <el-option v-for="c in currencies" :key="c.currencyId" :label="c.currencyName" :value="c.currencyId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('汇率')">
                <el-input-number v-model="form.exchangeRate" :precision="6" :step="0.1" :min="0" class="field-input" :disabled="readonly || !foreignCurrencyEnabled" />
              </el-form-item>
              <el-form-item v-if="taxEnabled" :label="$tf('计税方式')">
                <el-select v-model="form.taxCalcMethod" class="field-input" :disabled="readonly" @change="onTaxCalcMethodChange">
                  <el-option v-for="o in taxMethodOptions" :key="o" :label="$tf(o)" :value="o" />
                </el-select>
              </el-form-item>
            </div>
          </div>

          <!-- 金额信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>金额信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('合计金额')">
                <el-input-number v-model="form.totalAmount" :precision="2" :controls="false" class="field-input" disabled />
              </el-form-item>
            </div>
          </div>

          <div class="info-section" v-if="headerCustomSections.length > 0" v-for="section in headerCustomSections" :key="section.title">
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
                  :disabled="readonly"
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
                  :disabled="readonly"
                />
                <el-switch
                  v-else-if="field.type === 'switch'"
                  v-model="form[field.prop]"
                  :active-value="field.activeValue !== undefined ? field.activeValue : 1"
                  :inactive-value="field.inactiveValue !== undefined ? field.inactiveValue : 0"
                  :disabled="readonly"
                />
                <el-date-picker
                  v-else-if="field.type === 'date'"
                  v-model="form[field.prop]"
                  type="date"
                  value-format="YYYY-MM-DD"
                  :placeholder="field.placeholder || '选择日期'"
                  class="field-input"
                  :disabled="readonly"
                />
                <el-input
                  v-else-if="field.type === 'textarea'"
                  v-model="form[field.prop]"
                  type="textarea"
                  :rows="field.rows || 3"
                  :placeholder="field.placeholder || '请输入'"
                  class="field-input"
                  :disabled="readonly"
                />
                <el-input
                  v-else
                  v-model="form[field.prop]"
                  :placeholder="field.placeholder || '请输入'"
                  class="field-input"
                  :disabled="readonly"
                />
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
                <el-input v-model="form.remark" type="textarea" :rows="2" class="field-input" :disabled="readonly" :placeholder="$tt('请输入备注信息')" />
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
      page-key="MaterialRequisitionEdit_items"
      title="领料明细"
      col-setting-title="领料明细列设置"
      :columns="shownDetailColumns"
      :custom-columns="itemCustomColumns"
      :readonly="readonly"
      :total-qty="totalQty"
      :summary-items="summaryItems"
      @remove="calcTotal"
    >
      <template #actions>
        <!-- 选来源单：上游 = 生产工单，明细取工单用料 materials；数量取 requiredQuantity（剩余 = required - 已领） -->
        <SourcePicker
          target-type="MaterialRequisitions"
          items-field="materials"
          qty-field="requiredQuantity"
          :source-status="['已生效']"
          :allow-over-qty="allowOverQty"
          :over-qty-warn="overQtyWarn"
          :disabled="readonly"
          btn-type="success"
          size="small"
          @pick="onSourcePicked"
        />
        <el-button v-permission="perm('pick')" size="small" @click="pickerVisible = true" :disabled="readonly">
          <el-icon><Grid /></el-icon>从物料库选择
        </el-button>
        <el-button v-permission="perm('addRow')" size="small" type="primary" @click="addItem" :disabled="readonly">
          <el-icon><Plus /></el-icon>添加明细
        </el-button>
      </template>
      <template #cell-materialId="{ row }">
        <el-select
          v-model="row.materialId"
          filterable
          :placeholder="materialSelectPlaceholder"
          @change="(v) => onMaterialChange(row, v)"
          :disabled="readonly"
          :filter-method="(val) => materialFilterMethod(val, row)"
          class="table-select"
          size="small"
          :teleported="true"
          :popper-options="{ strategy: 'fixed' }"
         popper-class="material-select-popper">
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
      <template #cell-unitId="{ row }">
        <UnitPickerCell
          v-model="row.unitId"
          :units="units"
          :material="materialOf(row)"
          :disabled="readonly"
          @change="calcRow(row)"
        />
      </template>
      <template #cell-batchNo="{ row }">
        <template v-if="batchVisible(row)">
          <el-input v-model="row.batchNo" size="small" :disabled="batchDisabled(row, readonly)" :placeholder="row._isLotControl ? '必填' : ''" />
          <span v-if="row._isLotControl" class="required-star">*</span>
        </template>
      </template>
      <template #cell-productionDate="{ row }">
        <el-date-picker
          v-if="row._isExpiryControl"
          v-model="row.productionDate"
          type="date"
          value-format="YYYY-MM-DD"
          size="small"
          style="width:100%"
          :disabled="readonly"
          @change="(v) => onProductionDateChange(row, v)"
        />
        <span v-else class="muted-text">-</span>
      </template>
      <template #cell-expiryDate="{ row }">
        <el-date-picker
          v-if="row._isExpiryControl"
          v-model="row.expiryDate"
          type="date"
          value-format="YYYY-MM-DD"
          size="small"
          style="width:100%"
          disabled
        />
        <span v-else class="muted-text">-</span>
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
          type="material-requisition"
          :disabled="!isDraft"
          button-text="上传附件"
          tip-text="支持图片、PDF、Word、Excel 等文件"
        />
      </div>
    </div>

    <!-- 审核流转（真实审批进度，通用组件） -->
    <ApprovalFlowTimeline bill-type="MATERIAL_REQUISITION" :bill-id="form.requisitionId" :status="form.status" />

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

    <!-- 打印预览弹窗 -->
    <PrintPreviewDialog v-model="printVisible" bill-type="MATERIAL_REQUISITION" :bill-id="form.requisitionId" />

    <!-- 操作日志弹窗 -->
    <ChangeRecordDialog v-model="changeRecordVisible" bill-type="生产领料" :bill-no="form.requisitionNo || ''" />

      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('来源单据')" name="source" :disabled="!form.requisitionId">
        <BillLinkPanel v-if="topTab === 'source'" bill-type="MATERIAL_REQUISITION" :bill-id="form.requisitionId" mode="source" />
      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('去向单据')" name="target" :disabled="!form.requisitionId">
        <BillLinkPanel v-if="topTab === 'target'" bill-type="MATERIAL_REQUISITION" :bill-id="form.requisitionId" mode="target" />
      </el-tab-pane>
    </el-tabs>
  </div>

  </template>

<script setup>
import { ref, reactive, computed, onMounted, watch, h } from 'vue'
import UnitPickerCell from '@/components/bill/UnitPickerCell.vue'
import { withFallbackOption } from '@/utils/labelFallback'
import { useBaseDataRefresh } from '@/composables/useBaseDataRefresh'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { ArrowLeft, Document, Collection, Plus, Grid, Printer, Link, ArrowRight, Check, ArrowDown, CircleCheck, CircleClose, Close, Clock, Memo } from '@element-plus/icons-vue'
import {
  getMaterialRequisition, saveMaterialRequisition, updateMaterialRequisition, deleteMaterialRequisition,
  submitMaterialRequisition, approveMaterialRequisition, closeMaterialRequisition, cancelMaterialRequisition,
  rejectMaterialRequisition, reverseApproveMaterialRequisition, voidMaterialRequisition, unvoidMaterialRequisition, reopenMaterialRequisition
} from '@/api/inventory/materialRequisition'
import { listAllMaterial } from '@/api/basedata/material'
import { listAllEmployee } from '@/api/basedata/employee'
import { getCurrentEmployee } from '@/composables/useCurrentEmployee'
import { listAllCurrency } from '@/api/basedata/currency'
import { listAllUnit } from '@/api/basedata/unit'
import { listAllWarehouse } from '@/api/basedata/warehouse'
import { listAllLocation } from '@/api/basedata/location'
import { listAllDepartment } from '@/api/basedata/department'
import { useAutoDocCode } from '@/composables/useAutoDocCode'
import { useBillActions } from '@/composables/useBillActions'
import { useCopyBill } from '@/composables/useCopyBill'
import PrintPreviewDialog from '@/components/PrintPreviewDialog.vue'
import ApprovalFlowTimeline from '@/components/bill/ApprovalFlowTimeline.vue'
import FileUploader from '@/components/FileUploader.vue'
import BillLinkPanel from '@/components/bill/BillLinkPanel.vue'
import { isBillLinkRegistered } from '@/api/billLink'
import ChangeRecordDialog from '@/components/ChangeRecordDialog.vue'
import {
  loadCustomFieldDefinitions,
  loadCustomFieldValues,
  saveCustomFieldValues,
  batchLoadCustomFieldValues,
  customFieldsToTableColumns,
  injectMaterialCustomFields,
  remapItemIdsAfterSave,
} from '@/composables/useCustomField'
import * as BillStatus from '@/utils/bill-status'
import { confirmSubmit, submitSuccessMessage } from '@/utils/submit-confirm'
import { validateBillRequiredFields } from '@/utils/billValidation'
import { useFieldPermission } from '@/composables/useFieldPermission'
import { useFieldRequiredMark } from '@/utils/fieldRequiredMark'
import BillActions from '@/components/bill/BillActions.vue'
import BillItemsTable from '@/components/bill/BillItemsTable.vue'
import { triggerListRefresh } from '@/composables/useAutoRefresh'
// 物料库选择弹窗（通用件）：内置自定义字段搜索、全字段/自定义字段列、列设置全局共享 MaterialPicker
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
// 选来源单（通用件）：上游=生产工单，明细字段名/数量字段名/源单状态由本页传入
import SourcePicker from '@/components/SourcePicker.vue'
// 物料下拉的输入过滤 + 背景提示文字（通用件）
import { filterMaterials, buildMaterialPlaceholder } from '@/utils/materialFilter'
import { useBillLineStock, BILL_STOCK_COLUMNS } from '@/composables/useBillLineStock'
import { useBatchField } from '@/composables/useBatchField'
import { tt } from '@/i18n'
import { useMaterialCustomFields } from '@/composables/useMaterialCustomFields'
import { usePagePerm } from '@/utils/pagePerm'
// 全站商业口径总开关（设置中心：启用含税业务 / 启用外币业务）
import { useCommercialSwitches } from '@/composables/useCommercialSwitches'
// 设置中心参数读取（「默认计税方式」）
import { useSysConfig } from '@/composables/useSysConfig'

const perm = usePagePerm('inventory:material-requisitions')

// 全站商业口径总开关：外币业务关闭时币种锁定本位币、汇率锁 1
const { taxEnabled, foreignCurrencyEnabled, ensureLoaded: ensureCommercialSwitches } = useCommercialSwitches()
// 设置中心参数读取（「默认计税方式」）
const { loadSysConfig, getConfig } = useSysConfig()
// 计税方式（含税价 / 不含税价 / 不计税）：表头驱动明细「含税价 / 不含税价」换算（照销售订单试点）
const taxMethodOptions = ['不含税价', '含税价', '不计税']

/** 历史值归一：价内税→含税价、价外税/空→不含税价；非法值兜底为不含税价 */
function normalizeTaxMethod(v) {
  if (v === '含税价' || v === '价内税') return '含税价'
  if (v === '不计税') return '不计税'
  return '不含税价'
}

/** 从设置中心取「默认计税方式」与两个总开关（含税 / 外币） */
async function initTaxSettings() {
  try {
    await ensureCommercialSwitches()
    await loadSysConfig()
  } catch (e) {
    // 读取失败保持默认（含税=开、外币=关）
  }
  if (!taxEnabled.value) {
    // 含税业务关闭：表头「计税方式」整项隐藏，一律按不计税计算
    form.taxCalcMethod = '不计税'
  } else if (!form.requisitionId) {
    form.taxCalcMethod = normalizeTaxMethod(getConfig('sys.tax.method'))
  } else {
    form.taxCalcMethod = normalizeTaxMethod(form.taxCalcMethod)
  }
}

/** 表头「计税方式」切换 → 全表明细按新口径重算 */
function onTaxCalcMethodChange() {
  form.items.forEach(row => calcRow(row))
  calcTotal()
}

const route = useRoute()
const router = useRouter()
const headerFormRef = ref()

const isEdit = computed(() => !!route.params.id)

// 仅草稿态允许编辑（单号/业务字段/自定义字段）；已生效/待审批等只读
const { docCodeDisabled, docCodePlaceholder, autoFillDocCodeOnCreate } = useAutoDocCode('MATERIAL_REQUISITION')

const STORAGE_KEY = 'material-requisition-edit-collapsed'

const collapsed = reactive({
  base: true,
  items: true,
  attach: true,
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

// 领料明细列定义（通用组件 BillItemsTable 消费）：editor = 单元格控件，type = 纯展示格式；
// 物料/单位/批次号/生产日期/有效期至 由页面提供插槽（#cell-<prop>）
const detailColumns = [
  { prop: 'materialMainImage', label: '图片', width: 60, align: 'center', type: 'image' },
  { prop: 'materialId', label: '商品名称', minWidth: 280 },
  { prop: 'materialCode', label: '商品编号', width: 130 },
  ...BILL_STOCK_COLUMNS,
  { prop: 'specification', label: '规格', width: 110, type: 'text' },
  { prop: 'unitId', label: '单位', width: 80, align: 'center' },
  { prop: 'quantity', label: '数量', width: 90, editor: 'number', precision: 4, change: (row) => calcRow(row) },
  { prop: 'unitPrice', label: '单价', width: 90, editor: 'number', precision: 4, change: (row) => calcRow(row) },
  { prop: 'priceWithTax', label: '含税价', width: 95, align: 'right', type: 'amount4' },
  { prop: 'priceWithoutTax', label: '不含税价', width: 95, align: 'right', type: 'amount4' },
  { prop: 'amount', label: '金额', width: 85, align: 'right', type: 'amount2' },
  { prop: 'avgCost', label: '成本', width: 85, align: 'right', type: 'amount4' },
  { prop: 'stockId', label: '仓库', width: 120, editor: 'select', placeholder: '仓库', clearable: false,
    options: () => (warehouses.value || []).map(w => ({ value: w.stockId, label: w.stockName })),
    change: (row) => onStockChange(row) },
  { prop: 'binId', label: '货位', width: 120, editor: 'select', placeholder: '货位',
    optionsFn: (row) => getLocationsByStock(row.stockId || form.warehouseId).map(l => ({ value: l.binId, label: l.binName })) },
  { prop: 'batchNo', label: '批次号', width: 120 },
  { prop: 'productionDate', label: '生产日期', width: 130 },
  { prop: 'expiryDate', label: '有效期至', width: 130 }
]
// 批次号列显隐由通用组件按明细物料统一判断（批次管理物料才出现）
const shownDetailColumns = computed(() => detailColumns)
// 汇总区（通用组件按此渲染；「合计数量：」行由组件按 totalQty 渲染）
const summaryItems = computed(() => [
  { label: '合计金额：', value: `¥${Number(form.totalAmount || 0).toFixed(2)}`, total: true }
])
useBillLineStock(() => form.items)
// 批次字段联动：按物料批次管理显隐，规则控制只读
const { loadBatchSetting, batchVisible, batchDisabled } = useBatchField()
// ===== 物料库弹窗的列定义 / 列设置 / 图片渲染 已收进通用组件 MaterialPickerDialog =====
// （列定义用共享的 materialPickerColumns()，列设置 page-key = 'MaterialPicker' 全局共享：
//   配一次，所有单据页的物料库弹窗都生效；本页不再重复维护这份列清单）

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

async function loadCustomFields() {
  const headerResult = await loadCustomFieldDefinitions('material_requisition')
  headerCustomSections.value = headerResult.sections
  headerCustomFieldMap.clear()
  headerResult.fieldMap.forEach((v, k) => headerCustomFieldMap.set(k, v))
  headerFormulaFields.value = headerResult.formulaFields

  const itemResult = await loadCustomFieldDefinitions('material_requisition_item')
  itemCustomFields.value = Array.from(itemResult.fieldMap.values())
  itemCustomColumns.value = customFieldsToTableColumns(itemCustomFields.value, { editable: isDraft.value, listShowOnly: false })
// 草稿态变化时重新生成明细自定义字段列（editable 控制）
watch(isDraft, () => {
  if (itemCustomFields.value && itemCustomFields.value.length > 0) {
    itemCustomColumns.value = customFieldsToTableColumns(itemCustomFields.value, { editable: isDraft.value, listShowOnly: false });
  }
});

}

const materials = ref([])
const employees = ref([])
const currencies = ref([])
const units = ref([])
const warehouses = ref([])
const locations = ref([])
const departments = ref([])

const cancelDialog = ref(false)
const cancelReason = ref('')
const closeDialog = ref(false)
const closeReason = ref('')
const printVisible = ref(false)
const changeRecordVisible = ref(false)

/** 物料库选择弹窗显隐（弹窗本体 = 通用组件 MaterialPickerDialog，搜索/分页/列设置都在组件内） */
const pickerVisible = ref(false)

const form = reactive({
  requisitionId: null,
  requisitionNo: '',
  requisitionDate: new Date().toISOString().slice(0, 10),
  workOrderNo: '',
  departmentId: null,
  employeeId: null,
  warehouseId: null,
  currencyId: null,
  exchangeRate: 1.0,
  taxCalcMethod: '不含税价',
  totalAmount: 0,
  remark: '',
  attachmentUrls: '',
  status: '',
  approvedBy: '',
  approvedAt: '',
  createdAt: '',
  items: []
})


const isDraft = computed(() => BillStatus.canEdit(form.status, form.closeStatus))
const unitOptions = computed(() =>
  (units.value || []).map(u => ({ value: u.unitId, label: u.unitName }))
)

const readonly = computed(() => !!(form.status && !isDraft.value))

const totalQty = computed(() => {
  return form.items.reduce((sum, row) => sum + Number(row.quantity || 0), 0).toFixed(2)
})

const rules = {
  requisitionDate: [{ required: true, message: tt('请选择领料日期'), trigger: 'change' }],
  warehouseId: [{ required: true, message: tt('请选择仓库'), trigger: 'change' }],
  currencyId: [{ required: true, message: tt('请选择币种'), trigger: 'change' }]
}

function statusTagType(s) {
  return { '草稿': 'info', '已生效': 'success', '已关闭': 'warning', '已取消': 'danger' }[s] || 'info'
}

function onCurrencyChange(id) {
  const c = currencies.value.find(x => x.currencyId === id)
  if (c && c.exchangeRate) form.exchangeRate = c.exchangeRate
  calcTotal()
}

function onWarehouseChange() {
  form.items.forEach(row => {
    if (!row.stockId) row.stockId = form.warehouseId
    row.binId = null
  })
}

function onStockChange(row) {
  row.binId = null
}

// 生产参数「超过来源单数量」：不控制 / 预警提示 / 不允许（默认不控制）
const overQtyPolicy = computed(() => String(getConfig('produce.over.qty', '不控制')))
const allowOverQty = computed(() => overQtyPolicy.value !== '不允许')
const overQtyWarn = computed(() => overQtyPolicy.value === '预警提示')

function getLocationsByStock(stockId) {
  if (!stockId) return []
  return locations.value.filter(l => l.stockId === stockId)
}

/** 当前行物料（多单位换算用：取基本单位、辅计量单位与换算率） */
function materialOf(row) {
  if (!row || row.materialId == null) return null
  return materials.value.find(x => x.materialId === row.materialId) || null
}

async function onMaterialChange(row, id) {
  const m = materials.value.find(x => x.materialId === id)
  if (m) {
    row.materialCode = m.materialCode
    row.materialName = m.materialName
    row.materialMainImage = m.mainImage || ''
    row.specification = m.model || ''
    row.unitId = m.baseUnitId || null
    row.unitPrice = m.purchasePrice || 0
    row.avgCost = m.avgCost || 0
    if (!row.stockId && form.warehouseId) row.stockId = form.warehouseId
    // 批次/保质期管理联动
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
    calcRow(row)
  }
  if (id && itemCustomFields.value.length > 0) {
    if (!materialCustomValueMap.value[id]) {
      const map = await batchLoadCustomFieldValues('material', [id])
      Object.assign(materialCustomValueMap.value, map)
    }
    injectMaterialCustomFields(row, id, materialCustomValueMap.value, itemCustomFields.value)
  }
}

function onProductionDateChange(row, date) {
  if (row._expiryDays && date) {
    const d = new Date(date)
    d.setDate(d.getDate() + Number(row._expiryDays))
    row.expiryDate = d.toISOString().slice(0, 10)
  }
}

function calcRow(row) {
  const qty = Number(row.quantity || 0)
  const price = Number(row.unitPrice || 0)
  // 表头「计税方式」决定「单价」是含税价还是不含税价 → 换算「含税价 / 不含税价」两列
  const method = form.taxCalcMethod || '不含税价'
  const factor = 1 + (Number(row.taxRate) || 0) / 100
  if (method === '含税价') {
    row.priceWithTax = price
    row.priceWithoutTax = factor ? Number((price / factor).toFixed(6)) : price
  } else if (method === '不计税') {
    row.priceWithTax = price
    row.priceWithoutTax = price
  } else {
    row.priceWithoutTax = price
    row.priceWithTax = Number((price * factor).toFixed(6))
  }
  row.amount = (qty * price).toFixed(2)
  calcTotal()
}

function calcTotal() {
  form.totalAmount = form.items.reduce((sum, row) => sum + Number(row.amount || 0), 0).toFixed(2)
}

function addItem() {
  form.items.push({
    materialId: null,
    materialCode: '',
    materialName: '',
    materialMainImage: '',
    specification: '',
    unitId: null,
    quantity: 1,
    unitPrice: 0,
    priceWithTax: 0,
    priceWithoutTax: 0,
    amount: 0,
    avgCost: 0,
    stockId: form.warehouseId || null,
    binId: null,
    batchNo: '',
    remark: '',
    _isLotControl: false,
    _isExpiryControl: false,
    _expiryDays: 0,
    productionDate: '',
    expiryDate: ''
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
  // 过滤用「已预取全量值」的 mcf.valueMap（本页 materialCustomValueMap 只含已选中的物料，会导致搜不到）
  row._filteredMaterials = filterMaterials(materials.value, val, mcf.valueMap.value, materialSearchableFields.value)
}

/**
 * 物料库弹窗「确认添加」回调（弹窗本体 = 通用组件 MaterialPickerDialog）
 * - 组件内已批量取好选中物料的「全部自定义字段值」，并随 confirm 回传 valueMap
 * - 这里把值并入本页值表（与行内下拉预取共用同一份，不再二次请求），再逐行组装明细行
 */
async function onMaterialPicked({ rows: picked, valueMap }) {
  if (!picked || picked.length === 0) return
  if (valueMap) {
    Object.assign(materialCustomValueMap.value, valueMap)
    // 同步给行内下拉展示用的值表（mcf.dropText 读取），并标记已请求，避免重复请求
    Object.assign(mcf.valueMap.value, valueMap)
    Object.keys(valueMap).forEach(id => mcf.loadedIds.value.add(Number(id)))
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
      materialMainImage: m.mainImage || '',
      specification: m.model || '',
      unitId: m.baseUnitId || null,
      quantity: 1,
      unitPrice: Number(m.purchasePrice || 0),
      priceWithTax: 0,
      priceWithoutTax: 0,
      avgCost: m.avgCost || 0,
      amount: Number(m.purchasePrice || 0),
      stockId: form.warehouseId || null,
      binId: null,
      batchNo: '',
      remark: '',
      _isLotControl: m.isLotControl || false,
      _isExpiryControl: m.isExpiryControl || false,
      _expiryDays: m.expiryDays || 0,
      productionDate: '',
      expiryDate: ''
    }
    if (m.isExpiryControl && m.expiryDays) {
      row.productionDate = new Date().toISOString().slice(0, 10)
      const d = new Date(row.productionDate)
      d.setDate(d.getDate() + Number(m.expiryDays))
      row.expiryDate = d.toISOString().slice(0, 10)
    }
    if (itemCustomFields.value.length > 0) {
      injectMaterialCustomFields(row, m.materialId, materialCustomValueMap.value, itemCustomFields.value)
    }
    calcRow(row)
    form.items.push(row)
  })
  calcTotal()
  pickerVisible.value = false
  ElMessage.success(`${tt("已添加 ")}${picked.length}${tt(" 条明细")}`)
}

/**
 * 选来源单回调：从生产工单「选单」带入（追加到已有明细，不覆盖）
 * - items 已由 SourcePicker 按 pushRelations 的 itemMapper(WorkOrders→MaterialRequisitions) 映射；
 *   源单没有的字段（图片/批次号/生产日期/有效期等）保持空值，不臆造
 * - 表头只回填本页已声明的字段（工单号/仓库等），来源单号等未声明的字段不动
 * - 仓库为空时按本页既有口径取单据仓库（表体 stock_id 为 NOT NULL）
 * - 复用本页 calcRow/calcTotal 重算金额与合计
 */
function onSourcePicked(formData, items) {
  if (formData) {
    Object.keys(formData).forEach(k => {
      const v = formData[k]
      if (v !== null && v !== undefined && k in form) form[k] = v
    })
  }
  const picked = items || []
  if (picked.length > 0) {
    picked.forEach(it => {
      const row = { ...it }
      if (!row.stockId) row.stockId = form.warehouseId || null
      if (row.unitPrice === undefined || row.unitPrice === null) row.unitPrice = 0
      form.items.push(row)
      calcRow(row)
    })
    ElMessage.success(`${tt("已从源单带入 ")}${picked.length}${tt(" 条明细")}`)
  }
  calcTotal()
}

function flushInputNumbers() {
  const list = form.items || form.value?.items || form.details || form.value?.details
  if (!list || list.length === 0) return
  list.forEach(row => {
    if (row.quantity !== undefined) row.quantity = Number(row.quantity || 0)
    if (row.unitPrice !== undefined) row.unitPrice = Number(row.unitPrice || 0)
    if (row.priceWithTax !== undefined) row.priceWithTax = Number(row.priceWithTax || 0)
    if (row.priceWithoutTax !== undefined) row.priceWithoutTax = Number(row.priceWithoutTax || 0)
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
  // 按当前表头「计税方式」把明细含税价/不含税价刷成最新
  form.items.forEach(row => calcRow(row))
  calcTotal()
  try {
    if (form.requisitionId) {
      await updateMaterialRequisition(form.requisitionId, form)
    } else {
      const res = await saveMaterialRequisition(form)
      if (res && res.requisitionId) form.requisitionId = res.requisitionId
      if (res && res.requisitionNo) form.requisitionNo = res.requisitionNo
      if (res && res.status) form.status = res.status
    
      if (!opts.skipRouteSync && !route.params.id && form.requisitionId) {
        try { await router.replace({ name: 'MaterialRequisitionEdit', params: { id: String(form.requisitionId) } }) } catch (_) {}
      }
    }
    if (headerCustomFieldMap.size > 0 && form.requisitionId) {
      const headerValues = {}
      for (const key of headerCustomFieldMap.keys()) {
        if (key in form) headerValues[key] = form[key]
      }
      await saveCustomFieldValues('material_requisition', form.requisitionId, headerValues, headerFormulaFields.value)
    }
    if (itemCustomFields.value.length > 0 && form.items && form.items.length > 0) {
      // 关键修复（2026-09-22，同销售订单 bug）：保存主表时后端"明细先删后插"，明细主键会变 →
      // 必须先按保存后的新主键重新对齐，否则明细级自定义字段会写到已删除的旧行上（报表永远空白）。
      try {
        const fresh = await getMaterialRequisition(form.requisitionId)
        remapItemIdsAfterSave(form.items, (fresh && fresh.items) || [], 'requisitionItemId')
      } catch (e) {
        console.warn('[materialRequisition] 保存后刷新明细主键失败，明细级自定义字段可能挂到旧行', e)
      }
      const itemKeys = new Set(itemCustomFields.value.map(f => f.fieldKey))
      for (let i = 0; i < form.items.length; i++) {
        const row = form.items[i]
        if (!row.itemId && !row.requisitionItemId) continue
        const itemId = row.itemId || row.requisitionItemId
        const values = {}
        for (const key of itemKeys) {
          if (key in row) values[key] = row[key]
        }
        await saveCustomFieldValues('material_requisition_item', itemId, values)
      }
    }
    triggerListRefresh('/inventory/requisitions')
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
  // 前端必填校验：表头必填（领料日期/仓库/币种）。仓库为空时不保存、不提交，单据保持草稿状态。
  const missing = await validateBillRequiredFields({ headerFormRef, rules, pageKey: 'material_requisition', model: form })
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
    const needApproval = await confirmSubmit({ billType: 'MATERIAL_REQUISITION', billLabel: '生产领料单' })
    await submitMaterialRequisition(form.requisitionId)
    ElMessage.success(submitSuccessMessage(needApproval))
    if (typeof loadDetail === 'function') await loadDetail()
    if (typeof load === 'function') await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('提交失败'))
  } finally {
    // 提交（或用户取消）后再把新单 id 写回地址栏：此时重建实例无副作用，新实例挂载时会重新拉取最新状态
    if (!route.params.id && form.requisitionId) {
      try { await router.replace({ name: 'MaterialRequisitionEdit', params: { id: String(form.requisitionId) } }) } catch (_) {}
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
    await closeMaterialRequisition(form.requisitionId, closeReason.value)
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
    await ElMessageBox.confirm(`${tt("确认删除领料单 ")}${form.requisitionNo}?`, tt('提示'), { type: 'warning' })
    await deleteMaterialRequisition(form.requisitionId)
    ElMessage.success(tt('删除成功'))
    router.push('/inventory/material-requisitions')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('删除失败'))
  }
}
function handlePrint() {
  if (!form.requisitionId) { ElMessage.warning(tt('请先保存单据再打印')); return }
  printVisible.value = true
}

async function loadDetail() {
  const __detailId = route.params.id || form.requisitionId || ''
  if (__detailId) {
    try {
      const res = await getMaterialRequisition(__detailId)
      Object.assign(form, res)
      if (headerCustomFieldMap.size > 0 && res.requisitionId) {
        const headerValues = await loadCustomFieldValues('material_requisition', res.requisitionId)
        Object.assign(form, headerValues)
      }
      if (!form.items) form.items = []
      if (form.items && materials.value && materials.value.length > 0) {
        form.items.forEach(row => {
          if (!row.materialMainImage && row.materialId) {
            const m = materials.value.find(x => x.materialId === row.materialId)
            row.materialMainImage = m?.mainImage || ''
          }
        })
      }
      if (itemCustomFields.value.length > 0 && res.items && res.items.length > 0) {
        const itemIds = res.items.map(it => it.itemId || it.requisitionItemId).filter(Boolean)
        if (itemIds.length > 0) {
          const itemValuesMap = await batchLoadCustomFieldValues('material_requisition_item', itemIds)
          form.items.forEach(row => {
            const itemId = row.itemId || row.requisitionItemId
            const values = itemValuesMap[itemId]
            if (values) Object.assign(row, values)
          })
        }
      }
      // 计税方式：老单据按设置中心默认/总开关归一，再按口径回算明细两列
      await initTaxSettings()
      form.items.forEach(row => calcRow(row))
      calcTotal()
    } catch (e) {
      ElMessage.error(e.message || tt('加载失败'))
    }
  } else {
    // 新建时默认当前用户为领料人（可修改）
    const me = await getCurrentEmployee()
    if (me && !form.employeeId) form.employeeId = me.employeeId
    // 新建：表头「计税方式」取设置中心「默认计税方式」
    await initTaxSettings()
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
    approve: approveMaterialRequisition,
    reject: rejectMaterialRequisition,
    reverseApprove: reverseApproveMaterialRequisition,
    close: closeMaterialRequisition,
    reopen: reopenMaterialRequisition,
    void: voidMaterialRequisition,
    unvoid: unvoidMaterialRequisition,
    delete: deleteMaterialRequisition
  },
  reload: loadDetail,
  router,
  listPath: '/inventory/material-requisitions',
  newPath: '/inventory/material-requisitions/edit',
  billLabel: '生产领料单'
})

// 复制：URL 带 ?copy=id 打开时自动把源单填入当前表单（复制按钮 handleCopy 由 useBillActions 负责跳到该地址）
const { applyCopy } = useCopyBill({
  route,
  loadFn: getMaterialRequisition,
  form,
  idField: 'requisitionId',
  noField: 'requisitionNo',
  itemIdField: 'requisitionItemId',
  itemsField: 'items',
  afterCopy: () => calcTotal(),
  customField: { entity: 'material_requisition', itemEntity: 'material_requisition_item', itemIdField: 'requisitionItemId' }
})

// 加载下拉基础数据——独立函数，供 onMounted 和切回标签页时复用
  async function loadBaseData() {
  try {
      const [matRes, empRes, curRes, unitRes, whRes, locRes, depRes] = await Promise.all([
        listAllMaterial(),
        listAllEmployee(),
        listAllCurrency(),
        listAllUnit(),
        listAllWarehouse(),
        listAllLocation(),
        listAllDepartment()
      ])
      materials.value = matRes || []
      // 行内下拉附加字段：物料到位后按批预取（不阻塞渲染）
      mcf.preloadAll(materials.value)
      employees.value = empRes || []
      currencies.value = curRes || []
      units.value = unitRes || []
      warehouses.value = whRes || []
      locations.value = locRes || []
      departments.value = depRes || []
      if (!form.currencyId && currencies.value.length > 0) {
        // 外币业务关闭：新建单据默认取本位币、汇率锁 1（老单据保持原币，仅供查看）
        if (!foreignCurrencyEnabled.value && !form.requisitionId) {
          const base = currencies.value.find(c => c.isBaseCurrency === 1 || c.isBaseCurrency === true) || currencies.value[0]
          form.currencyId = base.currencyId
          form.exchangeRate = 1
        } else {
          form.currencyId = currencies.value[0].currencyId
          onCurrencyChange(form.currencyId)
        }
      }
    } catch (e) {
      console.warn('下拉数据加载失败:', e)
    }
}

// ===== 顶级页签：单据 / 来源单据 / 去向单据 =====
const topTab = ref('doc')
const billLinkRegistered = ref(false)

const { isFieldRequired } = useFieldPermission('material_requisition')
useFieldRequiredMark('material_requisition', '.material-requisition-edit')

onMounted(async () => {
  // 全站商业口径总开关（含税/外币）优先装载：保证币种/汇率在基础数据加载前已就位
  await ensureCommercialSwitches()
  // 生产参数（来源单超量策略）一并先装载，避免转入弹窗用到旧值
  await loadSysConfig()
  // 批次功能总开关与默认规则（2026-11-06）
  await loadBatchSetting()
  // 物料自定义字段：定制开关 + 字段定义（失败静默降级）
  await mcf.init()
  // 该单据类型是否登记了「来源单据/去向单据」配置（未登记则不显示这两个页签）
  isBillLinkRegistered('MATERIAL_REQUISITION')
    .then(v => { billLinkRegistered.value = !!v })
    .catch(() => { billLinkRegistered.value = false })

  loadCollapsed()
  await loadCustomFields()
  await loadBaseData()
  if (!isEdit.value) {
    autoFillDocCodeOnCreate(form, 'requisitionNo')
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
.material-requisition-edit {
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
.required-star { color: #f56c6c; margin-left: 2px; }

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
  min-width: 1780px;
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
  .material-requisition-edit { padding: 8px; }
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
