<template>
  <div class="subcontract-order-edit">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div class="header-left">
        <div class="back-btn" @click="$router.push('/subcontract/orders')">
          <el-icon><ArrowLeft /></el-icon>
          <span>{{ $tt("返回列表") }}</span>
        </div>
        <div class="title-area">
          <div class="title-row">
            <el-icon class="title-icon"><Document /></el-icon>
            <span class="page-title">{{ isEdit ? '委外加工单' : '新增委外加工单' }}</span>
            <span v-if="form.billNo" class="order-no">
              <el-icon><Collection /></el-icon>
              {{ form.billNo }}
            </span>
            <el-tag v-if="form.status !== undefined && form.status !== null" size="small" :type="statusType(form.status)" effect="plain" class="status-tag">{{ $dt(statusText(form.status)) }}</el-tag>
          </div>
        </div>
      </div>
      <div class="header-right">
        <el-button v-permission="perm('view')" v-if="!(!form.orderId && !form.deliveryId && !form.inboundId && !form.invoiceId && !form.voucherId && !form.contractId && !form.requisitionId && !form.stockCheckId && !form.transferId && !form.bomId && !form.sampleId && !form.freeSampleId && !form.workOrderId && !form.billId && !form.id)" @click="changeRecordVisible = true">
          <el-icon><Memo /></el-icon>{{ $tt('操作日志') }}
        </el-button>
        <!-- 下推：与销售订单同款统一组件，固定放在操作日志之后、保存/提交之前
             本页状态是数字（3=已下达），下推规则按中文态配置 → 这里传中文态，否则永远提示"状态不满足" -->
        <PushDownButton v-if="isEdit" source-type="SubcontractOrder" :source-doc="form" :status="statusText(form.status)" />
        <BillActions bill-type="SUBCONTRACT_ORDER"
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
              <el-form-item :label="$tf('委外单号')" v-if="isFieldVisible('orderNo')" :required="isFieldRequired('orderNo')">
                <el-input v-model="form.billNo" :placeholder="docCodePlaceholder" :disabled="!isDraft" class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('委外日期')" prop="orderDate" required v-if="isFieldVisible('orderDate')">
                <el-date-picker v-model="form.orderDate" type="date" value-format="YYYY-MM-DD" class="field-input" :disabled="!isStatus(form.status, 0)" />
              </el-form-item>
              <el-form-item :label="$tf('状态')" v-if="isFieldVisible('status')" :required="isFieldRequired('status')">
                <el-input :value="statusText(form.status)" disabled class="field-input" />
              </el-form-item>
            </div>
          </div>

          <!-- 物料信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>物料信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('物料')" prop="materialId" required v-if="isFieldVisible('materialId')">
                <el-select v-model="form.materialId" filterable :placeholder="materialSelectPlaceholder" class="field-input" :disabled="!isStatus(form.status, 0)" @change="onMaterialChange" :filter-method="(val) => materialFilterMethod(val, headerMaterialFilterRow)" popper-class="material-select-popper">
                  <el-option v-for="m in (headerMaterialFilterRow._filteredMaterials || materialOptions)" :key="m.materialId" :label="`${m.materialName} (${m.materialCode})`" :value="m.materialId">
                  <span class="opt-code">{{ m.materialCode }}</span>
                  <span class="opt-name">{{ m.materialName }}</span>
                  <span v-for="cf in mcf.dropText(m)" :key="cf.key" class="opt-cf">{{ cf.label }}：{{ cf.value }}</span>
                  <span v-if="m.model" class="opt-model">{{ m.model }}</span>
                </el-option>
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('单位')" v-if="isFieldVisible('unitName')" :required="isFieldRequired('unitName')">
                <el-input v-model="form.unitName" :placeholder="$tt('选择物料后自动带出')" disabled class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('委外数量')" prop="qty" required v-if="isFieldVisible('totalQuantity')">
                <el-input-number v-model="form.qty" :min="0" :precision="scaleFor('qty')" controls-position="right" class="field-input" :disabled="!isStatus(form.status, 0)" @change="calcTotal" />
              </el-form-item>
            </div>
          </div>

          <!-- 供应商与工序 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>供应商与工序
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('委外供应商')" prop="supplierId" required v-if="isFieldVisible('supplierId')">
                <el-select v-model="form.supplierId" filterable :placeholder="$tt('选择供应商')" class="field-input" :disabled="!isStatus(form.status, 0)" @change="onSupplierChange">
                  <el-option v-for="s in withFallbackOption(supplierOptions, 'supplierId', 'supplierName', form)" :key="s.supplierId" :label="s.supplierName" :value="s.supplierId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('委外工序号')" v-if="isFieldVisible('processItem')" :required="isFieldRequired('processItem')">
                <el-input v-model="form.processNo" :placeholder="$tt('如 30')" class="field-input" :disabled="!isStatus(form.status, 0)" />
              </el-form-item>
              <el-form-item :label="$tf('加工单价')" prop="unitPrice" required v-if="isFieldVisible('unitPrice')">
                <el-input-number v-model="form.unitPrice" :min="0" :precision="4" controls-position="right" class="field-input" :disabled="!isStatus(form.status, 0)" @change="calcTotal" />
              </el-form-item>
              <el-form-item :label="$tf('加工费总额')" v-if="isFieldVisible('totalAmount')" :required="isFieldRequired('totalAmount')">
                <el-input-number v-model="form.totalPrice" :precision="2" controls-position="right" class="field-input" disabled />
              </el-form-item>
              <el-form-item v-if="taxEnabled && isFieldVisible('taxCalcMethod')" :label="$tf('计税方式')" :required="isFieldRequired('taxCalcMethod')">
                <el-select v-model="form.taxCalcMethod" class="field-input" :disabled="!isStatus(form.status, 0)" @change="calcTotal">
                  <el-option v-for="o in taxMethodOptions" :key="o" :label="$tf(o)" :value="o" />
                </el-select>
              </el-form-item>
              <el-form-item v-if="taxEnabled && isFieldVisible('taxRate')" :label="$tf('税率%')" :required="isFieldRequired('taxRate')">
                <el-input-number v-model="form.taxRate" :min="0" :max="100" :precision="2" controls-position="right" class="field-input" :disabled="!isStatus(form.status, 0)" @change="calcTotal" />
              </el-form-item>
              <el-form-item :label="$tf('税额')" v-if="isFieldVisible('taxAmount')" :required="isFieldRequired('taxAmount')">
                <el-input-number v-model="form.taxAmount" :precision="2" controls-position="right" class="field-input" disabled />
              </el-form-item>
              <el-form-item :label="$tf('价税合计')" v-if="isFieldVisible('netAmount')" :required="isFieldRequired('netAmount')">
                <el-input-number v-model="form.netAmount" :precision="2" controls-position="right" class="field-input" disabled />
              </el-form-item>
            </div>
          </div>

          <!-- 计划信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>计划信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('计划开工日期')" prop="planStartDate" required v-if="isFieldVisible('expectDate')">
                <el-date-picker v-model="form.planStartDate" type="date" value-format="YYYY-MM-DD" class="field-input" :disabled="!isStatus(form.status, 0)" />
              </el-form-item>
              <el-form-item :label="$tf('计划完工日期')" prop="planFinishDate" required v-if="isFieldVisible('actualDate')">
                <el-date-picker v-model="form.planFinishDate" type="date" value-format="YYYY-MM-DD" class="field-input" :disabled="!isStatus(form.status, 0)" />
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
                <el-input v-model="form.remark" type="textarea" :rows="2" class="field-input" :disabled="!isStatus(form.status, 0)" :placeholder="$tt('请输入备注信息')" />
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

    <!-- 明细卡片（通用组件 BillItemsTable：卡片/列设置/动态列/汇总 全站共用一套） -->
    <BillItemsTable
      v-model:collapsed="collapsed.items"
      :items="form.lines"
      page-key="SubcontractOrderEdit_items"
      title="委外发料清单"
      col-setting-title="委外发料清单列设置"
      :columns="detailColumns"
      :custom-columns="itemCustomColumns"
      :readonly="!isStatus(form.status, 0)"
      :show-total-qty="false"
      :summary-items="summaryItems"
      :source-link-click="goToSourceBill"
    >
      <template #actions>
        <SourcePicker ref="sourcePickerRef" target-type="SubcontractOrder" @pick="onSourcePicked" :disabled="!isStatus(form.status, 0)" btn-type="success" size="small" />
        <el-button v-permission="perm('addRow')" size="small" type="primary" @click="addLine" :disabled="!isStatus(form.status, 0)">
          <el-icon><Plus /></el-icon>添加明细
        </el-button>
      </template>
      <template #cell-materialId="{ row }">
        <el-select v-model="row.materialId" filterable :placeholder="materialSelectPlaceholder" style="width:100%" size="small" :disabled="!isStatus(form.status, 0)" @change="val => onLineMaterialChange(row, val)" :filter-method="(val) => materialFilterMethod(val, row)" popper-class="material-select-popper">
          <el-option v-for="m in (row._filteredMaterials || materialOptions)" :key="m.materialId" :label="`${m.materialName} (${m.materialCode})`" :value="m.materialId">
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
          type="subcontract-order"
          :disabled="!isStatus(form.status, 0)"
          button-text="上传附件"
          tip-text="支持图片、PDF、Word、Excel 等文件"
        />
      </div>
    </div>

    <!-- 审核流转（真实审批进度，通用组件） -->
    <ApprovalFlowTimeline bill-type="SUBCONTRACT_ORDER" :bill-id="form.id" :status="form.status" />

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
    <PrintPreviewDialog v-model="printVisible" bill-type="SUBCONTRACT_ORDER" :bill-id="form.id" />

    <!-- 操作日志弹窗 -->
    <ChangeRecordDialog v-model="changeRecordVisible" bill-type="委外加工单" :bill-no="form.billNo || ''" />
      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('来源单据')" name="source" :disabled="!form.id">
        <BillLinkPanel v-if="topTab === 'source'" bill-type="SUBCONTRACT_ORDER" :bill-id="form.id" mode="source" />
      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('去向单据')" name="target" :disabled="!form.id">
        <BillLinkPanel v-if="topTab === 'target'" bill-type="SUBCONTRACT_ORDER" :bill-id="form.id" mode="target" />
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
import { ArrowLeft, Document, Collection, Plus, Link, ArrowRight, Check, TopRight, CircleCheck, CircleClose, RefreshLeft, Close, Delete, Printer, Share, Clock, VideoPlay, Memo } from '@element-plus/icons-vue'
import {
  getSubcontract, saveSubcontract, updateSubcontract, deleteSubcontract,
  releaseSubcontract, completeSubcontract, closeSubcontract, pushPick, pushInbound,
  approveSubcontract, reverseApproveSubcontract,
  rejectSubcontract, voidSubcontract, unvoidSubcontract, reopenSubcontract
} from '@/api/production/subcontract'
import { listAllMaterial } from '@/api/basedata/material'
import { listAllSupplier } from '@/api/basedata/supplier'
import { listAllBom, getBom } from '@/api/basedata/bom'
import FileUploader from '@/components/FileUploader.vue'
import SourcePicker from '@/components/SourcePicker.vue'
import ApprovalFlowTimeline from '@/components/bill/ApprovalFlowTimeline.vue'
import PrintPreviewDialog from '@/components/PrintPreviewDialog.vue'
import ChangeRecordDialog from '@/components/ChangeRecordDialog.vue'
import { useAutoDocCode } from '@/composables/useAutoDocCode'
import { useBillActions } from '@/composables/useBillActions'

import * as BillStatus from '@/utils/bill-status'
import BillActions from '@/components/bill/BillActions.vue'
// 2026-11-07：下推统一组件（与销售订单同款，替代页面手写 el-dropdown）
import PushDownButton from '@/components/PushDownButton.vue'
import { validateBillRequiredFields } from '@/utils/billValidation'
import { useFieldRequiredMark } from '@/utils/fieldRequiredMark'
import { confirmSubmit, submitSuccessMessage } from '@/utils/submit-confirm'
import { triggerListRefresh } from '@/composables/useAutoRefresh'
import { useCopyBill } from '@/composables/useCopyBill'
import { tt } from '@/i18n'
import { useMaterialCustomFields } from '@/composables/useMaterialCustomFields'
// 物料下拉的输入过滤 + 背景提示文字（通用件）
import { filterMaterials, buildMaterialPlaceholder } from '@/utils/materialFilter'
import { createAutoFillToken, hasFilledRows } from '@/utils/bomAutoFill'
import { useBillLineStock, BILL_STOCK_COLUMNS } from '@/composables/useBillLineStock'
import { useCommercialSwitches } from '@/composables/useCommercialSwitches'
import { usePagePerm } from '@/utils/pagePerm'
import { useFieldPermission } from '@/composables/useFieldPermission'
// 计税方式（含税价 / 不含税价 / 不计税）：表头驱动加工费税额 / 价税合计（口径对齐销售订单试点）
import { useSysConfig } from '@/composables/useSysConfig'
// 明细通用组件（卡片折叠/列设置/动态列/汇总 全站共用一套）
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
import { usePrecision } from '@/composables/usePrecision'

// 数量输入/显示的位数统一读设置中心「数量小数位数」（2026-11-10）
const { scaleFor } = usePrecision()

const perm = usePagePerm('subcontract:orders')

// 字段权限（与销售订单同口径）：无配置时字段全可见、非必填，页面默认行为不变
const { isFieldVisible, isFieldRequired } = useFieldPermission('subcontract_order')

// 全站商业口径总开关（设置中心 → 启用含税业务 / 启用外币业务）
const { taxEnabled, foreignCurrencyEnabled, ensureLoaded: ensureCommercialSwitches } = useCommercialSwitches()

// ===== 计税方式（含税价 / 不含税价 / 不计税）：表头驱动「加工费税额 / 价税合计」换算（照抄销售订单试点）=====
const { loadSysConfig, getConfig } = useSysConfig()
const taxMethodOptions = ['不含税价', '含税价', '不计税']

/** 历史值归一：价内税→含税价、价外税/空→不含税价；非法值兜底为不含税价 */
function normalizeTaxMethod(v) {
  if (v === '含税价' || v === '价内税') return '含税价'
  if (v === '不计税') return '不计税'
  return '不含税价'
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
    if (form.taxRate == null || form.taxRate === 0) {
      const r = Number(getConfig('sys.tax.rate', '13'))
      form.taxRate = isNaN(r) ? 13 : r
    }
  } else {
    form.taxCalcMethod = normalizeTaxMethod(form.taxCalcMethod)
  }
}

// 发料清单列定义（通用组件 BillItemsTable 消费）：editor = 单元格控件，type = 纯展示格式；
// 库存三列用共享 BILL_STOCK_COLUMNS；物料/单位列由页面提供插槽（#cell-<prop>），来源单号列由组件的 sourceLinkClick 回调跳转来源单
const detailColumns = [
  { prop: 'materialId', label: '商品名称', minWidth: 220 },
  { prop: 'materialCode', label: '商品编号', width: 130 },
  ...BILL_STOCK_COLUMNS,
  { prop: 'sourceBillNo', label: '来源单号', minWidth: 140, type: 'sourceLink' },
  { prop: 'unitName', label: '单位', width: 120 },
  { prop: 'baseQty', label: '应发数量', width: 140, editor: 'number', precision: 6 },
  { prop: 'actualQty', label: '实发数量', width: 140, editor: 'number', precision: 6 },
  { prop: 'supplyType', label: '供应方式', width: 140, editor: 'select', placeholder: '请选择', clearable: false,
    options: [
      { value: '我方供料', label: '我方供料' },
      { value: '客供料', label: '客供料' },
      { value: '不供料', label: '不供料' }
    ] },
  { prop: 'remark', label: '备注', minWidth: 140, editor: 'text' }
]
// 汇总区（通用组件按此渲染）：委外数量 / 加工单价 / 加工费总额；启用含税时追加「合计税额 / 价税合计」
const summaryItems = computed(() => {
  const arr = [
    { label: '委外数量：', value: Number(form.qty || 0) },
    { label: '加工单价：', value: `¥${Number(form.unitPrice || 0).toFixed(4)}` },
    { label: '加工费总额：', value: `¥${Number(form.totalPrice || 0).toFixed(2)}` }
  ]
  if (taxEnabled.value) {
    arr.push({ label: '合计税额：', value: `¥${Number(form.taxAmount || 0).toFixed(2)}` })
    arr.push({ label: '价税合计：', value: `¥${Number(form.netAmount || 0).toFixed(2)}`, total: true })
  } else {
    arr[2].total = true
  }
  return arr
})
// 2026-09-20 修复（既有笔误）：本页明细数据变量是 form.lines（同模块其余 4 页才是 form.items），
// 原写法 () => form.items 恒为 undefined → 库存量/可用量/预计可用量三列一直是空。
useBillLineStock(() => form.lines)

const route = useRoute()
const router = useRouter()
const headerFormRef = ref()
const sourcePickerRef = ref()

const isEdit = computed(() => !!route.params.id)

const form = reactive({
  id: null,
  billNo: '',
  orderDate: new Date().toISOString().slice(0, 10),
  materialId: null,
  materialCode: '',
  materialName: '',
  unitId: null,
  unitName: '',
  qty: 0,
  supplierId: null,
  supplierName: '',
  processNo: '',
  unitPrice: 0,
  totalPrice: 0,
  // 计税方式（含税价/不含税价/不计税）：表头驱动加工费税额、价税合计、本位币折算
  taxCalcMethod: '不含税价',
  taxRate: 13,
  taxAmount: 0,
  netAmount: 0,
  baseTotalAmount: 0,
  baseTaxAmount: 0,
  baseNetAmount: 0,
  planStartDate: new Date().toISOString().slice(0, 10),
  planFinishDate: '',
  status: 0,
  remark: '',
  attachmentUrls: '',
  sourceBillType: '',
  sourceBillNo: '',
  createdAt: '',
  createTime: '',
  lines: []
})

// 仅草稿态允许编辑（单号/业务字段/自定义字段）；已生效/待审批等只读（数字状态体系：0=草稿）
// 注意 bill-status 基于文本状态；委外订单使用数字状态，这里直接用 status===0 判断草稿态
const isDraft = computed(() => Number(form.status) === 0)

// ===== 自定义字段（表头 subcontract_order / 明细 subcontract_order_item）=====
const headerCustomSections = ref([])
const headerCustomFieldMap = new Map()
const headerFormulaFields = ref([])
const itemCustomFields = ref([])
const itemCustomColumns = ref([])

async function loadCustomFields() {
  const headerResult = await loadCustomFieldDefinitions('subcontract_order')
  headerCustomSections.value = headerResult.sections
  headerCustomFieldMap.clear()
  headerResult.fieldMap.forEach((v, k) => headerCustomFieldMap.set(k, v))
  headerFormulaFields.value = headerResult.formulaFields

  const itemResult = await loadCustomFieldDefinitions('subcontract_order_item')
  itemCustomFields.value = Array.from(itemResult.fieldMap.values())
  itemCustomColumns.value = customFieldsToTableColumns(itemCustomFields.value, { editable: isDraft.value, listShowOnly: false })
}

// 草稿态变化时重新生成明细自定义字段列（editable 控制）
watch(isDraft, () => {
  if (itemCustomFields.value.length > 0) {
    itemCustomColumns.value = customFieldsToTableColumns(itemCustomFields.value, { editable: isDraft.value, listShowOnly: false })
  }
})

// 单据编码自动生成
const { docCodeDisabled, docCodePlaceholder, autoFillDocCodeOnCreate } = useAutoDocCode('SUBCONTRACT_ORDER')

const STORAGE_KEY = 'subcontract-order-edit-collapsed'

const collapsed = reactive({
  base: true,
  items: true,
  attach: false,
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

// 明细卡片折叠由通用组件 v-model 驱动，这里监听后持久化（与页头卡片共用同一份 localStorage）
watch(() => collapsed.items, () => saveCollapsed())

const materialOptions = ref([])
// BOM 全量列表：选委外物料后按「默认BOM」自动带出发料清单（2026-10-26）
const bomOptions = ref([])
// 物料自定义字段：行内下拉附加展示（逻辑集中在 composable）
const mcf = useMaterialCustomFields()

// 表头物料下拉没有明细行 row 作用域：给过滤结果一个载体
const headerMaterialFilterRow = reactive({ _filteredMaterials: null })
// 参与搜索的物料自定义字段：勾了「可搜索」的 ∪（下拉显示开关开启且勾了「下拉显示」的）
const materialSearchableFields = computed(() => (mcf.allFields.value || []).filter(f =>
  f && (f.isSearch === 1 || (mcf.enabled.value && f.isDropShow === 1))))
// 物料下拉的背景提示文字：声明可搜索范围（没有可搜索的自定义字段时原样返回「选择物料」）
const materialSelectPlaceholder = computed(() => buildMaterialPlaceholder(tt('选择物料'), materialSearchableFields.value))

/** 物料下拉：输入即过滤（编码/名称/规格 + 自定义字段值/字段名） */
function materialFilterMethod(val, row) {
  if (!val) { row._filteredMaterials = null; return }
  // 用「已预取全量值」的 mcf.valueMap，不能只用已选中物料的值表，否则搜不到
  row._filteredMaterials = filterMaterials(materialOptions.value, val, mcf.valueMap.value, materialSearchableFields.value)
}

const supplierOptions = ref([])

const cancelDialog = ref(false)
const cancelReason = ref('')
const printVisible = ref(false)
const changeRecordVisible = ref(false)

const rules = {
  orderDate: [{ required: true, message: tt('请选择委外日期'), trigger: 'change' }],
  materialId: [{ required: true, message: tt('请选择物料'), trigger: 'change' }],
  qty: [{ required: true, message: tt('请输入委外数量'), trigger: 'blur' }],
  supplierId: [{ required: true, message: tt('请选择委外供应商'), trigger: 'change' }],
  unitPrice: [{ required: true, message: tt('请输入加工单价'), trigger: 'blur' }],
  planStartDate: [{ required: true, message: tt('请选择计划开工日期'), trigger: 'change' }],
  planFinishDate: [{ required: true, message: tt('请选择计划完工日期'), trigger: 'change' }]
}

// 状态映射: 0草稿 1待审核 2已生效 3已下达 4执行中 5已完工 6已结案 7已作废
function statusText(s) {
  return ({
    0: '草稿', 1: '待审核', 2: '已生效',
    3: '已下达', 4: '执行中',
    5: '已完工', 6: '已结案', 7: '已作废'
  })[Number(s)] || String(s)
}

function statusType(s) {
  return ({
    0: 'info', 1: 'warning', 2: 'primary',
    3: 'warning', 4: 'primary',
    5: 'success', 6: 'info', 7: 'danger'
  })[Number(s)] || 'info'
}

function isStatus(val, target) {
  return Number(val) === target
}

function isReleasedOrRunning(s) {
  const n = Number(s)
  return n === 3 || n === 4
}

function onMaterialChange(materialId) {
  const m = materialOptions.value.find(x => x.materialId === materialId)
  if (m) {
    form.materialCode = m.materialCode
    form.materialName = m.materialName
    form.unitId = m.baseUnitId
    form.unitName = m.baseUnitName || ''
  } else {
    form.materialCode = ''
    form.materialName = ''
    form.unitId = null
    form.unitName = ''
  }
  // 跟着物料自动带出默认BOM的发料清单，并自动展开（2026-10-26）
  applyDefaultBomForHeader(materialId)
}

/** BOM 自动带出的并发令牌：只认最后一次选择，避免两套明细叠加 */
const bomAutoFill = createAutoFillToken()

/** 按物料取默认BOM：默认BOM优先 → 已生效 → 最新 */
function pickDefaultBom(materialId) {
  const list = (bomOptions.value || []).filter(b => b && String(b.materialId) === String(materialId))
  if (!list.length) return null
  return list.find(b => b.isDefault === 1 && b.status === '已生效')
    || list.find(b => b.isDefault === 1)
    || list.find(b => b.status === '已生效')
    || list[0]
}

/**
 * 选完委外物料（2026-10-26）：按该物料的默认BOM自动带出「委外发料清单」，并自动展开明细卡片。
 * 只在发料清单为空时带出，不覆盖/不删除用户已维护的行。
 */
async function applyDefaultBomForHeader(materialId) {
  if (!isStatus(form.status, 0) || materialId == null) return
  if (hasFilledRows(form.lines, 'materialId')) return
  const bom = pickDefaultBom(materialId)
  if (!bom) return
  const token = bomAutoFill.begin()
  try {
    const detail = await getBom(bom.bomId)
    // 并发防护（2026-10-26）：等BOM期间又改选了委外物料 / 物料已被清空 → 丢弃本次结果，
    // 否则两套BOM会先后追加，重复发料行在领料出库时会重复扣料。
    if (!bomAutoFill.isLatest(token)) return
    if (form.materialId == null || String(form.materialId) !== String(materialId)) return
    if (hasFilledRows(form.lines, 'materialId')) return
    const items = (detail && detail.items) || []
    if (!items.length) return
    if (!form.lines) form.lines = []
    items.forEach(it => {
      const m = materialOptions.value.find(x => x.materialId === it.materialId)
      form.lines.push({
        materialId: it.materialId,
        materialName: it.materialName || '',
        materialCode: it.materialCode || '',
        unitId: it.unitId || m?.baseUnitId || null,
        unitName: m?.baseUnitName || '',
        baseQty: it.quantity != null ? Number(it.quantity) : 1,
        actualQty: 0,
        supplyType: '我方供料',
        remark: ''
      })
    })
    collapsed.items = true
  } catch (e) { /* 带出失败不影响已选物料 */ }
}

function onSupplierChange(supplierId) {
  const s = supplierOptions.value.find(x => x.supplierId === supplierId)
  form.supplierName = s ? s.supplierName : ''
}

function onLineMaterialChange(row, materialId) {
  const m = materialOptions.value.find(x => x.materialId === materialId)
  if (m) {
    row.materialName = m.materialName
    row.materialCode = m.materialCode
    row.unitId = m.baseUnitId
    row.unitName = m.baseUnitName || ''
  } else {
    row.materialName = ''
    row.materialCode = ''
    row.unitId = null
    row.unitName = ''
  }
}

function addLine() {
  form.lines.push({
    materialId: null,
    materialName: '',
    materialCode: '',
    unitId: null,
    unitName: '',
    baseQty: 0,
    actualQty: 0,
    supplyType: '我方供料',
    remark: ''
  })
}

function calcTotal() {
  const qty = Number(form.qty || 0)
  const price = Number(form.unitPrice || 0)
  const taxRate = Number(form.taxRate || 0)
  const method = form.taxCalcMethod || '不含税价'
  const factor = 1 + taxRate / 100
  const noTax = method === '不计税'
  const inclusive = method === '含税价'
  // 加工费总额一律按「不含税」口径（口径对齐销售订单试点）
  const gross = qty * price
  const totalAmount = inclusive ? gross / factor : gross
  form.totalPrice = Number(totalAmount.toFixed(2))
  // 税额：不计税恒 0；含税价按「含税合计 − 不含税金额」倒算（统一口径，见 utils/billTax）
  const taxAmount = calcTaxAmount({ amount: totalAmount, unitPrice: price, quantity: qty, taxRate, discountRate: 0, inclusive, noTax })
  form.taxAmount = Number(taxAmount.toFixed(2))
  form.netAmount = Number((totalAmount + taxAmount).toFixed(2))
}

async function onSave(silent, opts = {}) {
  if (!isStatus(form.status, 0)) {
    ElMessage.warning(`${tt("当前状态为「")}${statusText(form.status)}${tt("」, 不可修改")}`)
    return false
  }
  // 宽松草稿保存：不校验必填，允许空物料/空供应商/无明细（可后续补全再提交）
  // 保存宽松：丢弃未选物料的空行（后端这些字段 NOT NULL，无法落库）
  form.lines = (form.lines || []).filter(r => r && r.materialId)
  calcTotal()
  try {
    // 前端 form 字段映射为后端 entity 字段
    const payload = {
      orderNo: form.billNo,
      supplierId: form.supplierId,
      supplierName: form.supplierName,
      processItem: form.processNo,
      orderDate: form.orderDate,
      expectDate: form.planStartDate,
      actualDate: form.planFinishDate,
      totalQuantity: form.qty,
      unitPrice: form.unitPrice,
      totalAmount: form.totalPrice,
      taxCalcMethod: form.taxCalcMethod,
      taxRate: form.taxRate,
      taxAmount: form.taxAmount,
      netAmount: form.netAmount,
      materialId: form.materialId,
      materialCode: form.materialCode,
      materialName: form.materialName,
      unitId: form.unitId,
      unitName: form.unitName,
      remark: form.remark,
      attachmentUrls: form.attachmentUrls,
      items: (form.lines || []).map(line => ({
        materialId: line.materialId,
        materialName: line.materialName,
        materialCode: line.materialCode,
        unit: line.unitName,
        unitId: line.unitId,
        quantity: line.baseQty,
        finishedQty: line.actualQty,
        supplyType: line.supplyType,
        remark: line.remark
      }))
    }
    if (isEdit.value) {
      payload.id = form.id
      await updateSubcontract(form.id, payload)
    } else {
      const res = await saveSubcontract(payload)
      if (res && res.id) form.id = res.id
      if (res && res.orderNo) form.billNo = res.orderNo
      if (res && res.status !== undefined) form.status = res.status
      // 新建保存后必须把 id 写回地址栏：否则 loadDetail() 拿不到 id → 提交/保存后页面状态不刷新（历史缺陷）
      if (!opts.skipRouteSync && form.id) { try { await router.replace({ name: 'SubcontractOrderEdit', params: { id: String(form.id) } }) } catch (_) {} }
    }
    if (headerCustomFieldMap.size > 0 && form.id) {
      const headerValues = {}
      for (const key of headerCustomFieldMap.keys()) {
        if (key in form) headerValues[key] = form[key]
      }
      await saveCustomFieldValues('subcontract_order', form.id, headerValues, headerFormulaFields.value)
    }
    if (itemCustomFields.value.length > 0 && form.lines.length > 0) {
      // 保存主表时后端走「明细先删后插」，明细主键已变，必须先按新主键对齐再写明细自定义字段
      try {
        const fresh = await getSubcontract(form.id)
        remapItemIdsAfterSave(form.lines, (fresh && fresh.items) || [], 'id')
      } catch (e) {
        console.warn('[subcontractOrder] 保存后刷新明细主键失败，明细级自定义字段可能挂到旧行', e)
      }
      const itemKeys = new Set(itemCustomFields.value.map(f => f.fieldKey))
      for (const row of form.lines) {
        if (!row.id) continue
        const values = {}
        for (const key of itemKeys) {
          if (key in row) values[key] = row[key]
        }
        await saveCustomFieldValues('subcontract_order_item', row.id, values)
      }
    }
    triggerListRefresh('/subcontract/orders')
    if (!silent) ElMessage.success(tt('保存成功'))

    await loadDetail()
    return true
  } catch (e) {
    ElMessage.error(e.message || tt('保存失败'))
  return false
  }
}

async function onSubmit() {
    // 前端必填校验（B功能）
    // 统一校验：头表单 + 明细内表单 + 任何 formRef；失败时精确列出缺失字段名
  const missing = await validateBillRequiredFields({ headerFormRef, rules, $el: document, pageKey: 'subcontract_order', model: form })
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
    const needApproval = await confirmSubmit({ billType: 'SUBCONTRACT_ORDER', billLabel: '委外订单' })
    // 委外加工单后端没有专门的 submit 接口，使用 release(下达) 作为审核流程的下一步
    // 实际状态流转: 0草稿 → 直接下达(3已下达)
    // 这里若需走完整审核流程，需要后端补充 submit 接口
    // 当前简化: 草稿直接通过 release 下达
    await releaseSubcontract(form.id)
    ElMessage.success(submitSuccessMessage(needApproval))
    triggerListRefresh('/subcontract/orders')
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('提交失败'))
  } finally {
    // 提交（或用户取消）后再把新单 id 写回地址栏：此时重建实例无副作用，新实例挂载时会重新拉取最新状态
    if (!route.params.id && form.id) {
      try { await router.replace({ name: 'SubcontractOrderEdit', params: { id: String(form.id) } }) } catch (_) {}
    }
  }
}

async function onApprove() {
  if (!form.id) { ElMessage.warning(tt('请先保存单据')); return }
  try {
    await ElMessageBox.confirm(tt('确认审核该委外单？'), tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await releaseSubcontract(form.id)
    ElMessage.success(tt('已生效'))
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('审核失败'))
  }
}

async function onRelease() {
  if (!form.id) { ElMessage.warning(tt('请先保存单据')); return }
  try {
    await ElMessageBox.confirm(tt('确认下达该委外单？下达后不可修改'), tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await releaseSubcontract(form.id)
    ElMessage.success(tt('已下达'))
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('下达失败'))
  }
}

async function onStartExecute() {
  if (!form.id) { ElMessage.warning(tt('请先保存单据')); return }
  try {
    await ElMessageBox.confirm(tt('确认开始执行？'), tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    // 后端无单独的开始执行接口，复用 complete 接口推进状态
    await completeSubcontract(form.id)
    ElMessage.success(tt('已开始执行'))
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('操作失败'))
  }
}

async function onComplete() {
  if (!form.id) { ElMessage.warning(tt('请先保存单据')); return }
  try {
    await ElMessageBox.confirm(tt('确认完工委外单？'), tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await completeSubcontract(form.id)
    ElMessage.success(tt('已完工'))
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('完工失败'))
  }
}

async function onClose() {
  if (!form.id) { ElMessage.warning(tt('请先保存单据')); return }
  try {
    await ElMessageBox.confirm(tt('确认结案？'), tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await closeSubcontract(form.id)
    ElMessage.success(tt('已结案'))
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('结案失败'))
  }
}

async function onReopen() {
  if (!form.id) { ElMessage.warning(tt('请先保存单据')); return }
  try {
    await ElMessageBox.confirm(tt('确认反结案？'), tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await releaseSubcontract(form.id)
    ElMessage.success(tt('反结案成功'))
    triggerListRefresh('/subcontract/orders')
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('反结案失败'))
  }
}

async function onPushPick() {
  if (!form.id) { ElMessage.warning(tt('请先保存单据')); return }
  try {
    await ElMessageBox.confirm(`${tt("确认将委外单 ")}${form.billNo}${tt(" 下推生成发料单？")}`, tt('提示'), { type: 'warning' })
    await pushPick(form.id)
    ElMessage.success(tt('已下推发料单'))
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('下推失败'))
  }
}

async function onPushInbound() {
  if (!form.id) { ElMessage.warning(tt('请先保存单据')); return }
  try {
    await ElMessageBox.confirm(`${tt("确认将委外单 ")}${form.billNo}${tt(" 下推生成入库单？")}`, tt('提示'), { type: 'warning' })
    await pushInbound(form.id)
    ElMessage.success(tt('已下推入库单'))
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('下推失败'))
  }
}

async function doCancel() {
  if (!form.id) { ElMessage.warning(tt('请先保存单据')); return }
  if (!cancelReason.value || !cancelReason.value.trim()) {
    ElMessage.warning(tt('请输入取消原因'))
    return
  }
  try {
    await closeSubcontract(form.id)
    ElMessage.success(tt('取消成功'))
    cancelDialog.value = false
    cancelReason.value = ''
    await loadDetail()
  } catch (e) {
    ElMessage.error(e.message || tt('取消失败'))
  }
}

async function onDelete() {
  if (!form.id) { ElMessage.warning(tt('请先保存单据')); return }
  try {
    await ElMessageBox.confirm(`${tt("确认删除委外单 ")}${form.billNo}?`, tt('提示'), { type: 'warning' })
    await deleteSubcontract(form.id)
    ElMessage.success(tt('删除成功'))
    router.push('/subcontract/orders')
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
      const res = await getSubcontract(__detailId)
      // 后端字段映射到前端 form 字段
      Object.assign(form, {
        id: res.id,
        billNo: res.orderNo || res.billNo || '',
        orderDate: res.orderDate,
        materialId: res.materialId,
        materialCode: res.materialCode || '',
        materialName: res.materialName || '',
        unitId: res.unitId,
        unitName: res.unitName || '',
        qty: res.totalQuantity || res.qty || 0,
        supplierId: res.supplierId,
        supplierName: res.supplierName || '',
        processNo: res.processItem || res.processNo || '',
        unitPrice: res.unitPrice || 0,
        totalPrice: res.totalAmount || res.totalPrice || 0,
        taxRate: res.taxRate != null ? Number(res.taxRate) : 13,
        taxAmount: res.taxAmount != null ? Number(res.taxAmount) : 0,
        netAmount: res.netAmount != null ? Number(res.netAmount) : 0,
        baseTotalAmount: res.baseTotalAmount != null ? Number(res.baseTotalAmount) : 0,
        baseTaxAmount: res.baseTaxAmount != null ? Number(res.baseTaxAmount) : 0,
        baseNetAmount: res.baseNetAmount != null ? Number(res.baseNetAmount) : 0,
        planStartDate: res.expectDate || res.planStartDate || '',
        planFinishDate: res.actualDate || res.planFinishDate || '',
        status: res.status,
        remark: res.remark || '',
        attachmentUrls: res.attachmentUrls || '',
        sourceBillType: res.sourceBillType || '',
        sourceBillNo: res.sourceBillNo || '',
        createdAt: res.createdAt || '',
        createTime: res.createTime || '',
        lines: (res.items || res.lines || []).map(it => ({
          materialId: it.materialId,
          materialName: it.materialName,
          materialCode: it.materialCode,
          unitId: it.unitId,
          unitName: it.unit || it.unitName || '',
          baseQty: it.quantity || it.baseQty || 0,
          actualQty: it.finishedQty || it.actualQty || 0,
          supplyType: it.supplyType || '我方供料',
          remark: it.remark || '',
          sourceBillNo: it.sourceBillNo || '',
          sourceBillType: it.sourceBillType || '',
          sourceDocId: it.sourceDocId || null
        }))
      })
      if (headerCustomFieldMap.size > 0 && res.id) {
        const headerValues = await loadCustomFieldValues('subcontract_order', res.id)
        Object.assign(form, headerValues)
      }
      if (itemCustomFields.value.length > 0 && form.lines.length > 0) {
        const rawItems = (res.items || res.lines || [])
        const itemIds = rawItems.map(it => it.id).filter(Boolean)
        if (itemIds.length > 0) {
          const itemValuesMap = await batchLoadCustomFieldValues('subcontract_order_item', itemIds)
          form.lines.forEach((row, i) => {
            const values = rawItems[i] ? itemValuesMap[rawItems[i].id] : null
            if (values) Object.assign(row, values)
          })
        }
      }
      // 重开回显：由落库金额反推表头计税方式（本表无 tax_calc_method 列）。
      // 不计税→价税合计=折前金额；不含税价→金额=数量×单价；含税价→金额已含税(≠数量×单价)
      const _gross = Number(form.qty || 0) * Number(form.unitPrice || 0)
      const _total = Number(form.totalPrice || 0)
      const _net = Number(form.netAmount || 0)
      if (_gross > 0 && _total > 0) {
        const _eq = (a, b) => Math.abs(a - b) < 0.01
        if (_eq(_net, _gross)) form.taxCalcMethod = '不计税'
        else if (_eq(_total, _gross)) form.taxCalcMethod = '不含税价'
        else form.taxCalcMethod = '含税价'
      }
      // 计税方式：按设置中心默认/总开关归一，再按口径回算加工费税额/价税合计
      await initTaxSettings()
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

// 选单回调 - 从生产工单带入加工内容
function onSourcePicked(sourceData, items) {
  if (sourceData) {
    if (sourceData.sourceBillType) form.sourceBillType = sourceData.sourceBillType
    if (sourceData.sourceBillNo) form.sourceBillNo = sourceData.sourceBillNo
    if (sourceData.remark) form.remark = sourceData.remark
    if (sourceData.expectDate) form.planFinishDate = sourceData.expectDate
    if (sourceData.totalQuantity) form.qty = sourceData.totalQuantity
  }
  if (items && items.length > 0) {
    const first = items[0]
    form.materialId = first.materialId
    form.materialCode = first.materialCode
    form.materialName = first.materialName
    if (first.quantity) form.qty = first.quantity
    if (first.unitId) form.unitId = first.unitId
    // 委外发料清单（2026-11-23：追加到现有清单末尾，不再覆盖原有明细）
    const newLines = items.map(it => ({
      materialId: it.materialId,
      materialName: it.materialName || '',
      materialCode: it.materialCode || '',
      unitId: it.unitId,
      unitName: it.unit || it.unitName || '',
      baseQty: it.quantity || 0,
      actualQty: 0,
      supplyType: '我方供料',
      remark: '',
      sourceBillNo: it.sourceBillNo || '',
      sourceBillType: it.sourceBillType || '',
      sourceDocId: it.sourceDocId || null
    }))
    form.lines = [...(form.lines || []), ...newLines]
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
    approve: approveSubcontract,
    reverseApprove: reverseApproveSubcontract,
    reject: rejectSubcontract,
    close: closeSubcontract,
    reopen: reopenSubcontract,
    void: voidSubcontract,
    unvoid: unvoidSubcontract,
    delete: deleteSubcontract
  },
  reload: loadDetail,
  router,
  newPath: '/subcontract/orders/new',
  listPath: '/subcontract/orders',
  billLabel: '委外加工单'
})

// 加载下拉基础数据——独立函数，供 onMounted 和切回标签页时复用
  async function loadBaseData() {
  try {
      const [mats, sups, boms] = await Promise.all([
        listAllMaterial(),
        listAllSupplier(),
        listAllBom()
      ])
      materialOptions.value = mats || []
      // 行内下拉附加字段：物料到位后按批预取（不阻塞渲染）
      mcf.preloadAll(materialOptions.value)
      supplierOptions.value = sups || []
      bomOptions.value = boms || []
    } catch (e) {
      console.warn('下拉数据加载失败:', e)
    }
}

// ===== 复制功能 =====
const { applyCopy } = useCopyBill({
  route,
  loadFn: getSubcontract,
  form,
  afterCopy: () => {
    // 本页明细数据变量是 form.lines（同模块其余 4 页才是 form.items）：把装入的 items 映射成发料清单
    const items = form.items || []
    form.lines = items.map(it => ({
      materialId: it.materialId,
      materialName: it.materialName || '',
      materialCode: it.materialCode || '',
      unitId: it.unitId,
      unitName: it.unit || it.unitName || '',
      baseQty: it.quantity || 0,
      actualQty: 0,
      supplyType: '我方供料',
      remark: '',
      sourceBillNo: it.sourceBillNo || '',
      sourceBillType: it.sourceBillType || '',
      sourceDocId: it.sourceDocId || null
    }))
    if (items.length > 0) {
      const first = items[0]
      form.materialId = first.materialId
      form.materialCode = first.materialCode
      form.materialName = first.materialName
      if (first.quantity) form.qty = first.quantity
      if (first.unitId) form.unitId = first.unitId
    }
    calcTotal()
  },
  noField: 'billNo'
})

// ===== 顶级页签：单据 / 来源单据 / 去向单据 =====
const topTab = ref('doc')
const billLinkRegistered = ref(false)

useFieldRequiredMark('subcontract_order', '.subcontract-order-edit')

onMounted(async () => {
  // 全站商业口径总开关（含税业务 / 外币业务）：打开页面先装载一次
  await ensureCommercialSwitches()
  // 物料自定义字段：定制开关 + 字段定义（失败静默降级）
  await mcf.init()
  // 该单据类型是否登记了「来源单据/去向单据」配置（未登记则不显示这两个页签）
  isBillLinkRegistered('SUBCONTRACT_ORDER')
    .then(v => { billLinkRegistered.value = !!v })
    .catch(() => { billLinkRegistered.value = false })
  await loadCustomFields()
  loadCollapsed()
  await loadBaseData()
  if (!isEdit.value) {
    autoFillDocCodeOnCreate(form, 'billNo')
    // 新建：表头「计税方式」取设置中心「默认计税方式」
    await initTaxSettings()
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
.subcontract-order-edit {
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
  .subcontract-order-edit { padding: 8px; }
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
