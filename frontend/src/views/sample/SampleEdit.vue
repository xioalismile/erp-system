<template>
  <div class="sample-edit">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div class="header-left">
        <div class="back-btn" @click="$router.go(-1)">
          <el-icon><ArrowLeft /></el-icon>
          <span>{{ $tt("返回列表") }}</span>
        </div>
        <div class="title-area">
          <div class="title-row">
            <el-icon class="title-icon"><Document /></el-icon>
            <span class="page-title">{{ isEdit ? '打样单' : '新增打样单' }}</span>
            <span v-if="form.sampleNo" class="order-no">
              <el-icon><Collection /></el-icon>
              {{ form.sampleNo }}
            </span>
            <el-tag v-if="form.status" size="small" :type="statusTagType(form.status)" effect="plain" class="status-tag">{{ $dt(form.status) }}</el-tag>
          </div>
        </div>
      </div>
      <div class="header-right">
        <PushDownButton v-if="canPushDown(form.status, form.closeStatus)" source-type="SampleOrder" :source-doc="form" />
        <BillActions bill-type="SAMPLE_ORDER"
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
              <el-form-item :label="$tf('打样单号')">
                <el-input v-model="form.sampleNo" :placeholder="docCodePlaceholder" :disabled="!isDraft" class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('样品名称')" prop="sampleName" required>
                <div style="display:flex; gap:6px; width:100%;">
                  <el-select v-model="form.sampleName" filterable allow-create default-first-option :placeholder="materialSelectPlaceholder" style="flex:1" :disabled="!isDraft" @change="onSampleNameChange" :filter-method="(val) => materialFilterMethod(val, sampleMaterialFilterRow, sampleMaterialOptions)" popper-class="material-select-popper">
                    <el-option v-for="m in (sampleMaterialFilterRow._filteredMaterials || sampleMaterialOptions)" :key="m.materialId" :label="m.materialName + (m.materialCode ? ' (' + m.materialCode + ')' : '')" :value="m.materialName">
                  <span class="opt-code">{{ m.materialCode }}</span>
                  <span class="opt-name">{{ m.materialName }}</span>
                  <span v-for="cf in mcf.dropText(m)" :key="cf.key" class="opt-cf">{{ cf.label }}：{{ cf.value }}</span>
                  <span v-if="m.model" class="opt-model">{{ m.model }}</span>
                </el-option>
                  </el-select>
                  <el-button v-permission="perm('pick')" type="primary" @click="mainMaterialSelectorVisible = true" :disabled="!isDraft">
                    <el-icon><Grid /></el-icon>
                  </el-button>
                </div>
              </el-form-item>
              <el-form-item :label="$tf('样品类型')" prop="sampleType" required>
                <el-select v-model="form.sampleType" style="width: 100%" :disabled="!isDraft">
                  <el-option :label="$tf('新产品')" value="新产品" />
                  <el-option :label="$tf('改进产品')" value="改进产品" />
                  <el-option :label="$tf('来料打样')" value="来料打样" />
                  <el-option :label="$tf('内部研发')" value="内部研发" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('打样阶段')" prop="sampleStage" required>
                <el-select v-model="form.sampleStage" style="width: 100%" :disabled="!isDraft">
                  <el-option :label="$tf('原样')" value="原样" />
                  <el-option :label="$tf('初样')" value="初样" />
                  <el-option :label="$tf('确认样')" value="确认样" />
                  <el-option :label="$tf('大货样')" value="大货样" />
                </el-select>
              </el-form-item>
            </div>
          </div>

          <!-- 客户与物料 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>客户与物料
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('客户')" prop="customerId">
                <el-select v-model="form.customerId" filterable clearable :placeholder="$tt('选择客户')" class="field-input" :disabled="!isDraft" @change="onCustomerChange">
                  <el-option v-for="c in withFallbackOption(customerOptions, 'customerId', 'customerName', form)" :key="c.customerId" :label="c.customerName" :value="c.customerId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('联系人')">
                <el-input v-model="form.contactPerson" :placeholder="$tt('客户联系人')" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('联系电话')">
                <el-input v-model="form.contactPhone" :placeholder="$tt('客户联系电话')" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('物料编码')">
                <el-input v-model="form.materialCode" :disabled="!isDraft" :placeholder="$tt('选择样品名称后自动填充')" disabled class="field-input"  />
              </el-form-item>
              <el-form-item :label="$tf('规格型号')">
                <el-input v-model="form.specification" :placeholder="$tt('样品规格描述')" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('单位')" prop="unitId" required>
                <el-select v-model="form.unitId" filterable :placeholder="$tt('选择单位')" class="field-input" :disabled="!isDraft">
                  <el-option v-for="u in unitOptions" :key="u.unitId" :label="u.unitName" :value="u.unitId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('打样数量')" prop="sampleQty" required>
                <el-input-number v-model="form.sampleQty" :min="0.0001" :precision="scaleFor('qty')" controls-position="right" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('优先级')" prop="priority" required>
                <el-select v-model="form.priority" class="field-input" :disabled="!isDraft">
                  <el-option :label="$tf('紧急')" value="紧急" />
                  <el-option :label="$tf('高')" value="高" />
                  <el-option :label="$tf('普通')" value="普通" />
                  <el-option :label="$tf('低')" value="低" />
                </el-select>
              </el-form-item>
            </div>
          </div>

          <!-- 进度与安排 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>进度与安排
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('打样进度')">
                <el-select :model-value="form.progress" filterable allow-create default-first-option :placeholder="$tt('选择或输入进度')" class="field-input" :disabled="!canEditProgress" @change="onProgressChange">
                  <el-option v-for="p in progressOptions" :key="p" :label="p" :value="p" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('需求交期')" prop="demandDate" required>
                <el-date-picker v-model="form.demandDate" type="date" value-format="YYYY-MM-DD" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('计划完成')" prop="planFinishDate" required>
                <el-date-picker v-model="form.planFinishDate" type="date" value-format="YYYY-MM-DD" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('责任部门')">
                <el-select v-model="form.departmentId" filterable clearable :placeholder="$tt('选择责任部门')" class="field-input" :disabled="!isDraft">
                  <el-option v-for="d in deptOptions" :key="d.deptId" :label="d.deptName" :value="d.deptId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('责任工程师')">
                <el-select v-model="form.engineerId" filterable clearable :placeholder="$tt('选择工程师')" class="field-input" :disabled="!isDraft">
                  <el-option v-for="e in employeeOptions" :key="e.employeeId" :label="e.employeeName" :value="e.employeeId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('成本金额')">
                <el-input-number v-model="form.costAmount" :precision="2" :controls="false" disabled class="field-input" />
              </el-form-item>
              <el-form-item v-if="taxEnabled" :label="$tf('计税方式')">
                <el-select v-model="form.taxCalcMethod" class="field-input" :disabled="!isDraft" @change="onTaxCalcMethodChange">
                  <el-option v-for="o in taxMethodOptions" :key="o" :label="$tf(o)" :value="o" />
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
                <el-input v-model="form.remark" type="textarea" :rows="2" class="field-input" :disabled="!isDraft" :placeholder="$tt('备注说明')" />
              </el-form-item>
            </div>
          </div>

          <!-- 自定义字段分区（设置中心「自定义字段设置 → 打样单」配置；未配置时不渲染任何节点） -->
          <div class="info-section" v-for="section in headerCustomSections" :key="section.title">
            <div class="section-title">
              <span class="section-bar"></span>{{ section.title }}
            </div>
            <div class="section-fields">
              <el-form-item v-for="field in section.fields" :key="field.prop" :label="$tf(field.label)" :required="field.required">
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
        </el-form>
      </div>
    </div>

    <!-- 样品BOM（子件清单）卡片（通用组件 BillItemsTable：卡片/列设置/动态列/汇总 全站共用一套） -->
    <BillItemsTable
      v-model:collapsed="collapsed.items"
      :items="form.items"
      page-key="SampleEdit_items"
      title="样品BOM（子件清单）"
      col-setting-title="样品BOM列设置"
      :columns="itemColumns"
      :custom-columns="itemCustomColumns"
      :readonly="!isDraft"
      :total-qty="totalBaseQty"
      :summary-items="itemSummaryItems"
      @remove="calcTotal"
    >
      <template #actions>
        <el-button v-permission="perm('pick')" size="small" @click="openMaterialSelector" :disabled="!isDraft">
          <el-icon><Grid /></el-icon>从物料库选择
        </el-button>
        <el-button v-permission="perm('addRow')" size="small" type="primary" @click="addItem" :disabled="!isDraft">
          <el-icon><Plus /></el-icon>添加子件
        </el-button>
      </template>
      <template #cell-childMaterialId="{ row }">
        <el-select v-model="row.childMaterialId" filterable :placeholder="materialSelectPlaceholder" style="width: 100%" size="small" :disabled="!isDraft" @change="(v) => onItemMaterialChange(row, v)" :filter-method="(val) => materialFilterMethod(val, row, childMaterialOptions)" popper-class="material-select-popper">
          <el-option v-for="m in (row._filteredMaterials || childMaterialOptions)" :key="m.materialId" :label="`${m.materialName} (${m.materialCode})`" :value="m.materialId">
            <span class="opt-code">{{ m.materialCode }}</span>
            <span class="opt-name">{{ m.materialName }}</span>
            <span v-for="cf in mcf.dropText(m)" :key="cf.key" class="opt-cf">{{ cf.label }}：{{ cf.value }}</span>
            <span v-if="m.model" class="opt-model">{{ m.model }}</span>
          </el-option>
        </el-select>
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
          type="sample"
          :disabled="!isDraft"
          button-text="上传附件"
          tip-text="支持图片、PDF、Word、Excel 等文件"
        />
      </div>
    </div>

    <!-- 审核流转（真实审批进度，通用组件） -->
    <ApprovalFlowTimeline bill-type="SAMPLE_ORDER" :bill-id="form.sampleId" :status="form.status" />

    <!-- 取消弹窗 -->
    <el-dialog v-model="cancelDialog" :title="$tf('取消打样单')" width="420px">
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

    <!-- 子件物料库选择弹窗（通用组件：内置自定义字段搜索、全字段/自定义字段列、列设置全局共享 MaterialPicker） -->
    <!-- 子件选择：沿用原弹窗标题；排除「成品」（成品不能做子件），与明细行下拉 childMaterialOptions 同口径 -->
    <MaterialPickerDialog
      v-model="materialSelectorVisible"
      :title="$tf('从物料库选择子件')"
      :exclude-fn="(m) => m.materialType === '成品'"
      @confirm="onMaterialPicked"
    />

    <!-- 主物料选择弹窗（通用组件：内置自定义字段搜索、全字段/自定义字段列、列设置全局共享 MaterialPicker） -->
    <!-- 主物料为单选：@confirm 回调取 rows[0] 写回表单，业务含义与旧弹窗一致 -->
    <MaterialPickerDialog
      v-model="mainMaterialSelectorVisible"
      :title="$tf('从物料库选择主物料')"
      @confirm="onMainMaterialPicked"
    />

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
    <PrintPreviewDialog v-model="printVisible" bill-type="SAMPLE" :bill-id="form.sampleId" />
      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('来源单据')" name="source" :disabled="!form.sampleId">
        <BillLinkPanel v-if="topTab === 'source'" bill-type="SAMPLE_ORDER" :bill-id="form.sampleId" mode="source" />
      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('去向单据')" name="target" :disabled="!form.sampleId">
        <BillLinkPanel v-if="topTab === 'target'" bill-type="SAMPLE_ORDER" :bill-id="form.sampleId" mode="target" />
      </el-tab-pane>
    </el-tabs>
  </div>
  </template>

<script setup>
import { withFallbackOption } from '@/utils/labelFallback'
import { calcTaxAmount } from '@/utils/billTax'
import { ref, reactive, computed, onMounted, watch, h} from 'vue'
import BillLinkPanel from '@/components/bill/BillLinkPanel.vue'
import { isBillLinkRegistered } from '@/api/billLink'
import { useBaseDataRefresh } from '@/composables/useBaseDataRefresh'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification} from 'element-plus'
import {
  ArrowLeft, Document, Collection, Plus, Grid, Printer,
  ArrowRight, Check, ArrowDown, CircleCheck, Close, RefreshLeft, VideoPlay, Select, Connection
} from '@element-plus/icons-vue'
import {
  getSample, createSample, updateSample, deleteSample,
  submitSample, approveSample, reverseApproveSample,
  startSample, completeSample, confirmSample, cancelSample, convertToBom,
  updateSampleProgress,
  rejectSample, voidSample, unvoidSample, closeOrderSample, reopenSample
} from '@/api/sample/sample'
import { listAllMaterial } from '@/api/basedata/material'
import { listAllCustomer } from '@/api/basedata/customer'
import { listAllUnit } from '@/api/basedata/unit'
import { listAllDepartment } from '@/api/basedata/department'
import { listAllEmployee } from '@/api/basedata/employee'
import PushDownButton from '@/components/PushDownButton.vue'
import ApprovalFlowTimeline from '@/components/bill/ApprovalFlowTimeline.vue'
import { canPushDown } from '@/utils/bill-status'
import { useAutoDocCode } from '@/composables/useAutoDocCode'

import * as BillStatus from '@/utils/bill-status'
import BillActions from '@/components/bill/BillActions.vue'
import PrintPreviewDialog from '@/components/PrintPreviewDialog.vue'
import FileUploader from '@/components/FileUploader.vue'
import { useBillActions } from '@/composables/useBillActions'
import { useCopyBill } from '@/composables/useCopyBill'
import { validateBillRequiredFields } from '@/utils/billValidation'
import { useFieldRequiredMark } from '@/utils/fieldRequiredMark'
import { confirmSubmit, submitSuccessMessage } from '@/utils/submit-confirm'
import { triggerListRefresh } from '@/composables/useAutoRefresh'
import { tt } from '@/i18n'
import { useMaterialCustomFields } from '@/composables/useMaterialCustomFields'
// 自定义字段（打样单表头/明细）：定义 → 表单分区 / 明细补充列 → 取值 / 存值
import {
  loadCustomFieldDefinitions,
  loadCustomFieldValues,
  saveCustomFieldValues,
  batchLoadCustomFieldValues,
  customFieldsToTableColumns,
  remapItemIdsAfterSave,
} from '@/composables/useCustomField'
// 物料下拉的输入过滤 + 背景提示文字（通用件）
import { filterMaterials, buildMaterialPlaceholder } from '@/utils/materialFilter'
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
// 明细通用组件（卡片折叠/列设置/动态列/汇总 全站共用一套）
import BillItemsTable from '@/components/bill/BillItemsTable.vue'
import { useBillLineStock, BILL_STOCK_COLUMNS } from '@/composables/useBillLineStock'
import { usePagePerm } from '@/utils/pagePerm'
// 计税方式（含税价 / 不含税价 / 不计税）：表头驱动明细「含税价 / 不含税价」换算（口径对齐销售订单试点）
import { useSysConfig } from '@/composables/useSysConfig'
import { useCommercialSwitches } from '@/composables/useCommercialSwitches'
import { usePrecision } from '@/composables/usePrecision'

// 数量输入/显示的位数统一读设置中心「数量小数位数」（2026-11-10）
const { scaleFor } = usePrecision()

const perm = usePagePerm('sample:list')
useBillLineStock(() => form.items, { materialField: 'childMaterialId' })

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
    // 含税业务关闭：表头「计税方式」整项隐藏，一律按不计税计算
    form.taxCalcMethod = '不计税'
  } else if (!form.sampleId) {
    form.taxCalcMethod = normalizeTaxMethod(getConfig('sys.tax.method'))
  } else {
    form.taxCalcMethod = normalizeTaxMethod(form.taxCalcMethod)
  }
}

/** 表头「计税方式」切换 → 全表明细按新口径重算 */
function onTaxCalcMethodChange() {
  ;(form.items || []).forEach(row => calcItemAmount(row))
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

const route = useRoute()
const router = useRouter()
const headerFormRef = ref()
const { docCodeDisabled, docCodePlaceholder, autoFillDocCodeOnCreate } = useAutoDocCode('SAMPLE_ORDER')

const isEdit = computed(() => !!route.params.id)

// 仅草稿态允许编辑（单号/业务字段/自定义字段）；已生效/待审批等只读
const STORAGE_KEY = 'sample-edit-collapsed'

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

// 明细卡片的折叠由通用组件 v-model 驱动，这里监听后持久化（与页头卡片共用同一份 localStorage）
watch(() => collapsed.items, () => saveCollapsed())

const materialOptions = ref([])
// 物料自定义字段：行内下拉附加展示 + 物料库弹窗全字段（逻辑集中在 composable）
const mcf = useMaterialCustomFields()
// 表头样品物料：黑名单排除原材料属性，其余（成品/半成品/包材/辅料及未来新增类型）全部可选
const sampleMaterialOptions = computed(() =>
  materialOptions.value.filter(m => m.materialType !== '原材料')
)
// 子件物料：与BOM子件一致，黑名单排除成品，其它属性（原材料/半成品/包材/辅料等）全部放行
const childMaterialOptions = computed(() =>
  materialOptions.value.filter(m => m.materialType !== '成品')
)

// 表头样品物料下拉没有明细行 row 作用域：给过滤结果一个载体
const sampleMaterialFilterRow = reactive({ _filteredMaterials: null })
// 参与搜索的物料自定义字段：勾了「可搜索」的 ∪（下拉显示开关开启且勾了「下拉显示」的）
const materialSearchableFields = computed(() => (mcf.allFields.value || []).filter(f =>
  f && (f.isSearch === 1 || (mcf.enabled.value && f.isDropShow === 1))))
// 物料下拉的背景提示文字：声明可搜索范围（没有可搜索的自定义字段时原样返回「选择物料」）
const materialSelectPlaceholder = computed(() => buildMaterialPlaceholder(tt('选择物料'), materialSearchableFields.value))

/**
 * 物料下拉：输入即过滤（编码/名称/规格 + 自定义字段值/字段名）
 * @param {Array} list 候选物料列表（表头样品物料/子件各有类型黑名单，传各自候选列表；不传则用全量物料列表）
 */
function materialFilterMethod(val, row, list) {
  if (!val) { row._filteredMaterials = null; return }
  // 用「已预取全量值」的 mcf.valueMap，不能只用已选中物料的值表，否则搜不到
  row._filteredMaterials = filterMaterials(list || materialOptions.value, val, mcf.valueMap.value, materialSearchableFields.value)
}
const customerOptions = ref([])
const unitOptions = ref([])
const deptOptions = ref([])
const employeeOptions = ref([])

const cancelDialog = ref(false)
const cancelReason = ref('')

const materialSelectorVisible = ref(false)

const mainMaterialSelectorVisible = ref(false)

const progressOptions = ['图纸设计', '物料准备', '加工中', '组装调试', '待验收', '已完成']

// ===== 自定义字段（打样单表头 sample_order + 明细细样单 sample_order_item）=====
// 表头：loadCustomFieldDefinitions → 分区渲染；明细：转成通用明细组件的补充列
const headerCustomSections = ref([])
const headerCustomFieldMap = new Map()
const headerFormulaFields = ref([])
const itemCustomFields = ref([])
const itemCustomColumns = ref([])

async function loadCustomFields() {
  const headerResult = await loadCustomFieldDefinitions('sample_order')
  headerCustomSections.value = headerResult.sections
  headerCustomFieldMap.clear()
  headerResult.fieldMap.forEach((v, k) => headerCustomFieldMap.set(k, v))
  headerFormulaFields.value = headerResult.formulaFields

  const itemResult = await loadCustomFieldDefinitions('sample_order_item')
  itemCustomFields.value = Array.from(itemResult.fieldMap.values())
  itemCustomColumns.value = customFieldsToTableColumns(itemCustomFields.value, { editable: isDraft.value, listShowOnly: false })
  // 草稿态变化时重新生成明细自定义字段列（editable 控制可编辑/只读）
  watch(isDraft, () => {
    if (itemCustomFields.value && itemCustomFields.value.length > 0) {
      itemCustomColumns.value = customFieldsToTableColumns(itemCustomFields.value, { editable: isDraft.value, listShowOnly: false })
    }
  })
}

const form = reactive({
  sampleId: null, sampleNo: '', sampleName: '', sampleType: '新产品', sampleStage: '原样',
  customerId: null, customerName: '', contactPerson: '', contactPhone: '',
  materialId: null, materialCode: '', materialName: '', specification: '',
  unitId: null, sampleQty: 1, demandDate: '', planFinishDate: '',
  departmentId: null, engineerId: null, priority: '普通', costAmount: 0,
  // 计税方式：表头驱动明细「含税价 / 不含税价」换算；taxAmount/netAmount 为汇总（本页无落库列，仅界面口径）
  taxCalcMethod: '不含税价', taxAmount: 0, netAmount: 0,
  status: '草稿', progress: '', remark: '', attachmentUrls: '', items: []
})


const isDraft = computed(() => BillStatus.canEdit(form.status, form.closeStatus))
const rules = {
  sampleName: [{ required: true, message: tt('请选择或输入样品名称'), trigger: 'change' }],
  sampleType: [{ required: true, message: tt('请选择样品类型'), trigger: 'change' }],
  sampleStage: [{ required: true, message: tt('请选择打样阶段'), trigger: 'change' }],
  unitId: [{ required: true, message: tt('请选择单位'), trigger: 'change' }],
  sampleQty: [{ required: true, message: tt('请输入打样数量'), trigger: 'blur' }],
  demandDate: [{ required: true, message: tt('请选择需求交期'), trigger: 'change' }],
  planFinishDate: [{ required: true, message: tt('请选择计划完成日期'), trigger: 'change' }],
  priority: [{ required: true, message: tt('请选择优先级'), trigger: 'change' }]
}

// 进度在草稿/已生效(已生效)/打样中/已完成阶段允许更新
const canEditProgress = computed(() => {
  return ['草稿', '已生效', '打样中', '已完成'].includes(form.status)
})

function statusTagType(s) {
  return {
    '草稿': 'info', '待审核': 'warning', '已生效': 'primary', '打样中': 'warning',
    '已完成': 'success', '已确认': 'success', '已转BOM': 'success', '已取消': 'danger'
  }[s] || 'info'
}

function canCancel(s) {
  return s && !['已转BOM', '已取消'].includes(s)
}

function calcItemAmount(row) {
  const qty = Number(row.baseQty || 0)
  const price = Number(row.unitPrice || 0)
  const taxRate = defaultTaxRate()
  // 表头「计税方式」决定「单价」是含税价还是不含税价 → 换算另一列（含税价 / 不含税价）
  const method = form.taxCalcMethod || '不含税价'
  const factor = 1 + taxRate / 100
  const noTax = method === '不计税'
  const inclusive = method === '含税价'
  if (inclusive) {
    row.priceWithTax = price
    row.priceWithoutTax = factor ? Number((price / factor).toFixed(6)) : price
  } else if (noTax) {
    row.priceWithTax = price
    row.priceWithoutTax = price
  } else {
    row.priceWithoutTax = price
    row.priceWithTax = Number((price * factor).toFixed(6))
  }
  // 折前金额一律按「不含税」口径参与（与销售订单试点一致）
  const originalAmount = inclusive ? (price * qty) / factor : price * qty
  row.amount = Number(originalAmount.toFixed(2))
  // 行级税额与价税合计（2026-11-23 补齐，口径与全站一致）
  const tax = calcTaxAmount({ amount: row.amount, unitPrice: price, quantity: qty, taxRate, discountRate: 0, inclusive, noTax })
  row.taxAmount = Number(tax.toFixed(2))
  row.netAmount = Number((row.amount + row.taxAmount).toFixed(2))
  calcTotal()
}

function calcTotal() {
  const totalAmount = (form.items || []).reduce((sum, r) => sum + Number(r.amount || 0), 0)
  form.costAmount = totalAmount.toFixed(2)
  // 汇总税额 / 价税合计（本页无对应落库列，仅界面口径）：
  // 含税价按每一行「含税合计 − 不含税金额」倒算后汇总，避免整体乘税率产生 1 分尾差
  const taxRate = defaultTaxRate()
  const method = form.taxCalcMethod || '不含税价'
  const noTax = method === '不计税'
  const inclusive = method === '含税价'
  const taxAmount = (form.items || []).reduce((sum, r) => sum + calcTaxAmount({
    amount: r.amount, unitPrice: r.unitPrice, quantity: r.baseQty, taxRate, discountRate: 0, inclusive, noTax
  }), 0)
  form.taxAmount = Number(taxAmount.toFixed(2))
  form.netAmount = Number((totalAmount + taxAmount).toFixed(2))
}

// 样品BOM（子件清单）列定义（通用组件 BillItemsTable 消费）：editor = 单元格控件，type = 纯展示格式；
// 子件物料由页面提供插槽（#cell-childMaterialId）；库存量/可用量/预计可用量用全站统一的 BILL_STOCK_COLUMNS
const itemColumns = [
  { prop: 'childMaterialId', label: '子件物料', minWidth: 220 },
  ...BILL_STOCK_COLUMNS,
  { prop: 'model', label: '规格型号', width: 140, type: 'text', materialArchive: true },
  { prop: 'unitId', label: '单位', width: 100, editor: 'select', clearable: false,
    optionsFn: () => unitOptions.value.map(u => ({ value: u.unitId, label: u.unitName })) },
  { prop: 'baseQty', label: '基本用量', width: 120, editor: 'number', precision: 6, change: (row) => calcItemAmount(row) },
  { prop: 'qtyType', label: '用量类型', width: 100, editor: 'select', clearable: false,
    options: [{ value: '固定', label: '固定' }, { value: '变动', label: '变动' }] },
  { prop: 'scrapRate', label: '损耗率%', width: 100, editor: 'number', precision: 4 },
  { prop: 'supplyType', label: '供应类型', width: 100, editor: 'select', clearable: false,
    options: [{ value: '领料', label: '领料' }, { value: '倒冲', label: '倒冲' }, { value: '虚拟件', label: '虚拟件' }, { value: '委外', label: '委外' }] },
  { prop: 'isKeyPart', label: '关键件', width: 70, editor: 'switch' },
  { prop: 'unitPrice', label: '单价', width: 110, editor: 'number', precision: 4, change: (row) => calcItemAmount(row) },
  { prop: 'priceWithTax', label: '含税价', width: 95, align: 'right', type: 'amount4' },
  { prop: 'priceWithoutTax', label: '不含税价', width: 95, align: 'right', type: 'amount4' },
  { prop: 'amount', label: '金额', width: 110, align: 'right', type: 'amount2' },
  { prop: 'netAmount', label: '价税合计', width: 110, align: 'right', type: 'amount2', cellClass: 'net-amount' },
  { prop: 'remark', label: '备注', minWidth: 140, editor: 'text' }
]

// 合计数量（原页表格底部合计行的「合计」数量，与 getSummary 的 baseQty 合计同口径）→ 组件内置「合计数量」行
const totalBaseQty = computed(() => (form.items || []).reduce((s, r) => s + Number(r.baseQty || 0), 0).toFixed(4))

// 汇总区（通用组件 BillItemsTable 按此渲染）：原页卡片底部「成本合计」行（= getSummary 的金额合计）口径原样保留
const itemSummaryItems = computed(() => {
  const arr = []
  if (taxEnabled.value) {
    arr.push({ label: '合计税额：', value: `¥${Number(form.taxAmount || 0).toFixed(2)}` })
    arr.push({ label: '价税合计：', value: `¥${Number(form.netAmount || 0).toFixed(2)}` })
  }
  arr.push({ label: '成本合计：', value: `¥${Number(form.costAmount || 0).toFixed(2)}`, total: true })
  return arr
})

function onCustomerChange(customerId) {
  const c = customerOptions.value.find(x => x.customerId === customerId)
  form.customerName = c ? c.customerName : ''
}

function onSampleNameChange(selectedName) {
  const m = sampleMaterialOptions.value.find(x => x.materialName === selectedName)
  if (m) {
    form.materialId = m.materialId
    form.materialCode = m.materialCode
    form.materialName = m.materialName
    if (m.baseUnitId) form.unitId = m.baseUnitId
    if (m.model) form.specification = m.model
  } else {
    // 新产品：手动输入名称，清除物料关联
    form.materialId = null
    form.materialCode = ''
    form.materialName = ''
  }
}

function onItemMaterialChange(row, materialId) {
  const m = childMaterialOptions.value.find(x => x.materialId === materialId)
  if (m) {
    row.childMaterialCode = m.materialCode
    row.childMaterialName = m.materialName
    row.unitId = m.baseUnitId
    row.specification = m.model || ''
  } else {
    row.childMaterialCode = ''
    row.childMaterialName = ''
    row.unitId = null
  }
}

function openMaterialSelector() {
  materialSelectorVisible.value = true
}

/**
 * 子件物料库弹窗「确认添加」回调（弹窗本体 = 通用组件 MaterialPickerDialog）
 * 弹窗内已多选并带回行数据（自带自定义字段搜索与全字段列），这里只做本页明细行组装
 */
async function onMaterialPicked({ rows: picked }) {
  if (!picked || picked.length === 0) return
  picked.forEach(m => {
    form.items.push({
      childMaterialId: m.materialId,
      childMaterialCode: m.materialCode,
      childMaterialName: m.materialName,
      specification: m.model || '',
      unitId: m.baseUnitId,
      baseQty: 0,
      qtyType: '固定',
      scrapRate: 0,
      supplyType: '领料',
      isKeyPart: 0,
      unitPrice: 0,
      priceWithTax: 0,
      priceWithoutTax: 0,
      amount: 0,
      remark: ''
    })
  })
  materialSelectorVisible.value = false
  ElMessage.success(`${tt("已添加 ")}${picked.length}${tt(" 个子件")}`)
}

/**
 * 主物料物料库弹窗「确认」回调（弹窗本体 = 通用组件 MaterialPickerDialog）
 * 弹窗内已批量取好自定义字段值并随 confirm 回传；主物料为单选，取第一行写回表单
 * （业务含义与旧弹窗一致：样品名称/物料编码/名称/单位/规格型号）
 */
function onMainMaterialPicked({ rows }) {
  if (!rows || rows.length === 0) return
  const m = rows[0]
  form.sampleName = m.materialName
  form.materialId = m.materialId
  form.materialCode = m.materialCode
  form.materialName = m.materialName
  if (m.baseUnitId) form.unitId = m.baseUnitId
  if (m.model) form.specification = m.model
  mainMaterialSelectorVisible.value = false
}

function addItem() {
  form.items.push({
    childMaterialId: null, childMaterialCode: '', childMaterialName: '',
    specification: '', unitId: null, baseQty: 0, qtyType: '固定',
    scrapRate: 0, supplyType: '领料', isKeyPart: 0, unitPrice: 0,
    priceWithTax: 0, priceWithoutTax: 0, amount: 0, remark: ''
  })
  calcTotal()
}

function flushInputNumbers() {
  const list = form.items || form.value?.items || form.details || form.value?.details
  if (!list || list.length === 0) return
  list.forEach(row => {
    if (row.quantity !== undefined) row.quantity = Number(row.quantity || 0)
    if (row.baseQty !== undefined) row.baseQty = Number(row.baseQty || 0)
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
  // 宽松草稿保存：不校验必填，允许后续补全再提交（与销售订单等标准单据一致）
  // 按当前表头「计税方式」把明细含税价/不含税价/金额刷成最新
  ;(form.items || []).forEach(row => calcItemAmount(row))
  calcTotal()
  const payload = { ...form }
  payload.costAmount = form.costAmount
  try {
    if (form.sampleId) {
      await updateSample(form.sampleId, payload)
    } else {
      const res = await createSample(payload)
      if (res && res.sampleId) form.sampleId = res.sampleId
      if (res && res.sampleNo) form.sampleNo = res.sampleNo
      if (res && res.status) form.status = res.status
    
      if (!opts.skipRouteSync && !route.params.id && form.sampleId) {
        try { await router.replace({ name: 'SampleEdit', params: { id: String(form.sampleId) } }) } catch (_) {}
      }
    }
    // 自定义字段保存：表头按打样单主键写值；明细因后端保存走"先删后插"，行主键每次保存都会变，
    // 必须先按保存后的新主键回填行主键，再写明细值，否则值会挂到已删除的旧行上（表现为重开单据读不到）
    if (headerCustomFieldMap.size > 0 && form.sampleId) {
      const headerValues = {}
      for (const key of headerCustomFieldMap.keys()) {
        if (key in form) headerValues[key] = form[key]
      }
      await saveCustomFieldValues('sample_order', form.sampleId, headerValues, headerFormulaFields.value)
    }
    if (itemCustomFields.value.length > 0 && form.items && form.items.length > 0) {
      try {
        const fresh = await getSample(form.sampleId)
        remapItemIdsAfterSave(form.items, (fresh && fresh.items) || [], 'itemId')
      } catch (e) {
        console.warn('[sample] 保存后刷新明细主键失败，明细级自定义字段可能挂到旧行', e)
      }
      const itemKeys = new Set(itemCustomFields.value.map(f => f.fieldKey))
      for (let i = 0; i < form.items.length; i++) {
        const row = form.items[i]
        if (!row.itemId) continue
        const values = {}
        for (const key of itemKeys) {
          if (key in row) values[key] = row[key]
        }
        await saveCustomFieldValues('sample_order_item', row.itemId, values)
      }
    }
    triggerListRefresh('/sample/samples')
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
    // 统一校验头表单（本页仅有 headerFormRef 一个 el-form）；失败时精确列出缺失字段名
  const missing = await validateBillRequiredFields({ headerFormRef, rules, $el: document, pageKey: 'sample_order', model: form })
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
    // 自动滚动到第一个错误字段
    try {
      const target = headerFormRef && headerFormRef.value ? headerFormRef.value : null
      if (target && typeof target.scrollToField === 'function') target.scrollToField(missing[0].prop)
    } catch(_) {}
    return
  }
  // 无论新建还是已有草稿，提交前一律先静默保存（把当前页面所见落库，避免提交旧数据）
  const saved = await onSave(true, { skipRouteSync: true })
  if (!saved || !form.sampleId) return

  try {
    const needApproval = await confirmSubmit({ billType: 'SAMPLE_ORDER', billLabel: '打样单', billNo: form.sampleNo })
    await submitSample(form.sampleId)
    ElMessage.success(submitSuccessMessage(needApproval))
    triggerListRefresh('/sample/samples')
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('提交失败'))
  } finally {
    // 提交（或用户取消）后再把新单 id 写回地址栏：此时重建实例无副作用，新实例挂载时会重新拉取最新状态
    if (!route.params.id && form.sampleId) {
      try { await router.replace({ name: 'SampleEdit', params: { id: String(form.sampleId) } }) } catch (_) {}
    }
  }
}

async function onApprove() {
  if (!form.sampleId) { ElMessage.warning(tt('请先保存单据')); return }
  try {
    await ElMessageBox.confirm(`${tt("确认审核通过 ")}${form.sampleNo}？`, tt('审核确认'), {
      confirmButtonText: tt('确定审核'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await approveSample(form.sampleId)
    ElMessage.success(tt('已生效'))
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('审核失败'))
  }
}

async function onReverseApprove() {
  try {
    await ElMessageBox.confirm(`${tt("确认重置草稿 ")}${form.sampleNo}？`, tt('重置草稿确认'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await reverseApproveSample(form.sampleId)
    ElMessage.success(tt('已重置草稿'))
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('重置草稿失败'))
  }
}

async function onStart() {
  try {
    await ElMessageBox.confirm(`${tt("确认开始打样 ")}${form.sampleNo}？`, tt('开始打样'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await startSample(form.sampleId)
    ElMessage.success(tt('已开始打样'))
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('开始打样失败'))
  }
}

async function onComplete() {
  try {
    await ElMessageBox.confirm(`${tt("确认完成打样 ")}${form.sampleNo}？`, tt('完成打样'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await completeSample(form.sampleId)
    ElMessage.success(tt('已完成'))
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('完成失败'))
  }
}

async function onConfirm() {
  try {
    await ElMessageBox.confirm(`${tt("确认样品 ")}${form.sampleNo}${tt(" 合格？确认后可转BOM")}`, tt('确认样品'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await confirmSample(form.sampleId)
    ElMessage.success(tt('已确认'))
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('确认失败'))
  }
}

async function onConvertBom() {
  try {
    await ElMessageBox.confirm(
      `${tt("确认将打样单 ")}${form.sampleNo}${tt(" 转化为正式BOM？\\n\\n转化后：\\n1. 系统将以样品为父项，子件清单为BOM子项生成新BOM（草稿状态）\\n2. 打样单状态变更为\"已转BOM\"\\n3. 该操作不可重复执行")}`,
      tt('打样转BOM'),
      { type: 'warning', confirmButtonText: tt('确认转BOM'), cancelButtonText: tt('取消') }
    )
    const res = await convertToBom(form.sampleId)
    ElMessage.success(`${tt("转BOM成功！BOM编号: ")}${res.bomCode}`)
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('转BOM失败'))
  }
}

async function doCancel() {
  if (!form.sampleId) { ElMessage.warning(tt('请先保存单据')); return }
  if (!cancelReason.value || !cancelReason.value.trim()) {
    ElMessage.warning(tt('请输入取消原因'))
    return
  }
  try {
    await cancelSample(form.sampleId, cancelReason.value)
    ElMessage.success(tt('已取消'))
    cancelDialog.value = false
    cancelReason.value = ''
    await loadDetail()
  } catch (e) {
    ElMessage.error(e.message || tt('取消失败'))
  }
}

async function onDelete() {
  if (!form.sampleId) { ElMessage.warning(tt('请先保存单据')); return }
  try {
    await ElMessageBox.confirm(`${tt("确认删除打样单 ")}${form.sampleNo}？`, tt('提示'), { type: 'warning' })
    await deleteSample(form.sampleId)
    ElMessage.success(tt('删除成功'))
    router.go(-1)
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('删除失败'))
  }
}
// 进度变化同步到后端：成功才落地新值；已生成BOM时后端拒绝并提示先删除BOM
async function onProgressChange(val) {
  if (!form.sampleId) return
  try {
    const msg = await updateSampleProgress(form.sampleId, val)
    form.progress = val
    ElMessage.success(msg || tt('进度已更新'))
    // 自动生成BOM后刷新详情（回显BOM关联状态）
    if (msg && msg.includes('BOM')) await loadDetail()
  } catch (e) {
    ElMessage.error(e.message || tt('进度更新失败'))
  }
}

async function loadDetail() {
  const __detailId = route.params.id || form.sampleId || ''
  if (__detailId) {
    try {
      const res = await getSample(__detailId)
      Object.assign(form, res)
      if (!form.items) form.items = []
      // 重开回显：先由落库的两列反推表头计税方式，再按设置中心默认/总开关归一，最后按口径回算
      const inferred = inferTaxMethodFromItems(form.items)
      if (inferred) form.taxCalcMethod = inferred
      await initTaxSettings()
      ;(form.items || []).forEach(row => calcItemAmount(row))
      calcTotal()
      // 自定义字段回显：表头按打样单主键取、明细按行主键批量取（值表为 EAV，与业务表主键对齐）
      if (headerCustomFieldMap.size > 0 && form.sampleId) {
        const headerValues = await loadCustomFieldValues('sample_order', form.sampleId)
        Object.assign(form, headerValues)
      }
      if (itemCustomFields.value.length > 0 && form.items && form.items.length > 0) {
        const itemIds = form.items.map(it => it.itemId).filter(Boolean)
        if (itemIds.length > 0) {
          const itemValuesMap = await batchLoadCustomFieldValues('sample_order_item', itemIds)
          form.items.forEach(row => {
            const values = itemValuesMap[row.itemId]
            if (values) Object.assign(row, values)
          })
        }
      }
    } catch (e) {
      ElMessage.error(e.message || tt('加载失败'))
    }
  } else {
    autoFillDocCodeOnCreate(form, 'sampleNo')
    // 新建：表头「计税方式」取设置中心「默认计税方式」
    await initTaxSettings()
  }
}

async function loadOptions() {
  try {
    const [mats, custs, units, depts, emps] = await Promise.all([
      listAllMaterial(), listAllCustomer(), listAllUnit(),
      listAllDepartment(), listAllEmployee()
    ])
    materialOptions.value = mats || []
    // 行内下拉附加字段：物料到位后按批预取（不阻塞渲染）
    mcf.preloadAll(materialOptions.value)
    customerOptions.value = custs || []
    unitOptions.value = units || []
    deptOptions.value = depts || []
    employeeOptions.value = emps || []
  } catch (e) {
    console.warn('下拉数据加载失败:', e)
  }
}

// ===== 统一单据操作（驳回/重置草稿/结案/作废/删除/复制/打印）=====
const printVisible = ref(false)
function handlePrint() {
  if (!form.sampleId) { ElMessage.warning(tt('请先保存单据再打印')); return }
  printVisible.value = true
}

const {
  supportedActions,
  rejectDialog, rejectReason, doReject,
  voidDialog, voidReason, doVoid,
  handleReject, handleReverseApprove,
  handleClose, handleReopen, handleVoid, handleUnvoid,
  handleDelete, handleCopy
} = useBillActions({
  form,
  getId: () => form.sampleId,
  api: {
    approve: approveSample,
    reject: rejectSample,
    reverseApprove: reverseApproveSample,
    close: closeOrderSample,
    reopen: reopenSample,
    void: voidSample,
    unvoid: unvoidSample,
    delete: deleteSample
  },
  reload: loadDetail,
  router,
  listPath: '/sample/list',
  newPath: '/sample/edit',
  billLabel: '打样单'
})

// 复制：URL 带 ?copy=id 打开时自动把源单填入当前表单（复制按钮 handleCopy 由 useBillActions 负责跳到该地址）
const { applyCopy } = useCopyBill({
  route,
  loadFn: getSample,
  form,
  idField: 'sampleId',
  noField: 'sampleNo',
  itemIdField: 'itemId',
  itemsField: 'items',
  afterCopy: () => calcTotal(),
  customField: { entity: 'sample_order', itemEntity: 'sample_order_item', itemIdField: 'itemId' }
})

// ===== 顶级页签：单据 / 来源单据 / 去向单据 =====
const topTab = ref('doc')
const billLinkRegistered = ref(false)

useFieldRequiredMark('sample_order', '.sample-edit')

onMounted(async () => {
  // 全站商业口径总开关（含税业务）：打开页面先装载一次，决定「计税方式」是否显示
  await ensureCommercialSwitches()
  // 物料自定义字段：定制开关 + 字段定义（失败静默降级）
  await mcf.init()
  // 该单据类型是否登记了「来源单据/去向单据」配置（未登记则不显示这两个页签）
  isBillLinkRegistered('SAMPLE_ORDER')
    .then(v => { billLinkRegistered.value = !!v })
    .catch(() => { billLinkRegistered.value = false })
  loadCollapsed()
  await loadOptions()
  // 打样单自定义字段定义（表头 + 明细）：必须先于 loadDetail，loadDetail 依赖它取自定义字段值
  await loadCustomFields()
  const copied = await applyCopy()
  if (!copied) {
    await loadDetail()
  }
})

// 切回标签页时自动刷新下拉基础数据（只刷下拉，不动表单草稿），避免档案改过后这里还是旧数据
useBaseDataRefresh(loadOptions)


  </script>

<style scoped lang="scss">
.sample-edit {
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
  min-width: 1480px;
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
  .sample-edit { padding: 8px; }
  .page-header { flex-direction: column; gap: 8px; align-items: stretch; }
  .base-form .section-fields { grid-template-columns: 1fr; }
  .summary-area { flex-direction: column; }
}
</style>


<style scoped>
/* 物料下拉选项内容（与其它单据页保持一致） */
.opt-code { color: #409EFF; font-weight: 500; }
.opt-name { margin-left: 8px; }
.opt-cf { margin-left: 8px; color: #909399; font-size: 11px; white-space: normal; word-break: break-word; }
.opt-model { float: right; color: #909399; font-size: 12px; }
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
