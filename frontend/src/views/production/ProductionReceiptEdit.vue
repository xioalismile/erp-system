<template>
  <div class="production-receipt-edit">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div class="header-left">
        <div class="back-btn" @click="$router.push('/production/receipts')">
          <el-icon><ArrowLeft /></el-icon>
          <span>{{ $tt("返回列表") }}</span>
        </div>
        <div class="title-area">
          <div class="title-row">
            <el-icon class="title-icon"><Document /></el-icon>
            <span class="page-title">{{ isEdit ? '生产入库单' : '新增生产入库单' }}</span>
            <span v-if="form.receiptNo" class="order-no">
              <el-icon><Collection /></el-icon>
              {{ form.receiptNo }}
            </span>
            <el-tag v-if="form.status" size="small" :type="statusTagType(form.status)" effect="plain" class="status-tag">{{ $dt(form.status) }}</el-tag>
          </div>
        </div>
      </div>
      <div class="header-right">
        <el-button v-permission="perm('view')" v-if="!(!form.orderId && !form.deliveryId && !form.inboundId && !form.invoiceId && !form.voucherId && !form.contractId && !form.requisitionId && !form.stockCheckId && !form.transferId && !form.bomId && !form.sampleId && !form.freeSampleId && !form.workOrderId && !form.billId && !form.id)" @click="changeRecordVisible = true">
          <el-icon><Memo /></el-icon>{{ $tt('操作日志') }}
        </el-button>
        <BillActions bill-type="PRODUCTION_RECEIPT"
        :supported-actions="supportedActions"
          :show-approve="!(receiptApprovalOn && isDraft)"
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
        <el-form :model="form" :rules="rules" ref="headerFormRef" label-width="90px" class="base-form">
          <!-- 单据信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>单据信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('入库单号')" v-if="isFieldVisible('receiptNo')" :required="isFieldRequired('receiptNo')">
                <el-input v-model="form.receiptNo" :placeholder="docCodePlaceholder" :disabled="!isDraft" class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('入库日期')" prop="receiptDate" v-if="isFieldVisible('receiptDate')" :required="isFieldRequired('receiptDate')">
                <el-date-picker v-model="form.receiptDate" type="date" value-format="YYYY-MM-DD" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('生产订单')" v-if="isFieldVisible('productionOrderId')" :required="isFieldRequired('productionOrderId')">
                <el-select v-model="form.productionOrderId" filterable clearable :placeholder="$tt('选择生产工单')" class="field-input" :disabled="!isDraft" @change="onWorkOrderChange">
                  <el-option v-for="w in workOrders" :key="w.orderId" :label="`${w.orderNo}${w.materialName ? ' - ' + w.materialName : ''}`" :value="w.orderId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('工单号')" v-if="isFieldVisible('productionOrderNo')" :required="isFieldRequired('productionOrderNo')">
                <el-input v-model="form.productionOrderNo" :placeholder="$tt('生产订单号')" class="field-input" :disabled="!isDraft" />
              </el-form-item>
            </div>
          </div>

          <!-- 仓库与经办人 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>仓库与经办人
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('仓库')" prop="warehouseId" v-if="isFieldVisible('warehouseId')" :required="isFieldRequired('warehouseId')">
                <el-select v-model="form.warehouseId" filterable clearable :placeholder="$tt('统一选择仓库')" class="field-input" :disabled="!isDraft" @change="onWarehouseChange">
                  <el-option v-for="w in withFallbackOption(warehouses, 'warehouseId', 'warehouseName', form)" :key="w.stockId" :label="w.stockName" :value="w.stockId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('质检')" v-if="isFieldVisible('qcEnabled')" :required="isFieldRequired('qcEnabled')">
                <div class="qc-inline">
                  <el-switch v-model="form.qcEnabled" :active-value="1" :inactive-value="0" :disabled="!isDraft" />
                  <el-tag v-if="form.qcEnabled === 1" size="small" :type="form.qcStatus === '已质检' ? 'success' : 'warning'" effect="light">
                    {{ form.qcStatus || '未质检' }}
                  </el-tag>
                  <el-button size="small" type="primary" plain :disabled="!isDraft || !form.qcEnabled || !form.receiptId"
                             @click="qcDialogVisible = true">
                    填写质检
                  </el-button>
                </div>
              </el-form-item>
              <el-form-item :label="$tf('经办人')" v-if="isFieldVisible('handlerId')" :required="isFieldRequired('handlerId')">
                <el-select v-model="form.handlerId" filterable clearable :placeholder="$tt('选择经手人')" class="field-input" :disabled="!isDraft">
                  <el-option v-for="e in employees" :key="e.employeeId" :label="e.employeeName" :value="e.employeeId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('部门')" v-if="isFieldVisible('departmentId')" :required="isFieldRequired('departmentId')">
                <el-select v-model="form.departmentId" filterable clearable :placeholder="$tt('选择部门')" class="field-input" :disabled="!isDraft">
                  <el-option v-for="d in departments" :key="d.deptId" :label="d.deptName" :value="d.deptId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('币别')" v-if="isFieldVisible('currencyId')" :required="isFieldRequired('currencyId')">
                <el-select v-model="form.currencyId" filterable clearable :placeholder="$tt('选择币别')" class="field-input" :disabled="!isDraft || !foreignCurrencyEnabled" @change="onCurrencyChange">
                  <el-option v-for="c in currencies" :key="c.currencyId" :label="c.currencyCode" :value="c.currencyId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('汇率')" v-if="isFieldVisible('exchangeRate')" :required="isFieldRequired('exchangeRate')">
                <el-input-number v-model="form.exchangeRate" :precision="6" :step="0.1" :min="0" class="field-input" :disabled="!isDraft || !foreignCurrencyEnabled" />
              </el-form-item>
              <el-form-item v-if="taxEnabled && isFieldVisible('taxCalcMethod')" :label="$tf('计税方式')" :required="isFieldRequired('taxCalcMethod')">
                <el-select v-model="form.taxCalcMethod" class="field-input" :disabled="!isDraft" @change="onTaxCalcMethodChange">
                  <el-option v-for="o in taxMethodOptions" :key="o" :label="$tf(o)" :value="o" />
                </el-select>
              </el-form-item>
            </div>
          </div>

          <!-- 数量与金额汇总 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>数量与金额
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('总数量')" v-if="isFieldVisible('totalQuantity')" :required="isFieldRequired('totalQuantity')">
                <el-input-number v-model="form.totalQuantity" :precision="scaleFor('qty')" :controls="false" disabled class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('合格数量')" v-if="isFieldVisible('qualifiedQuantity')" :required="isFieldRequired('qualifiedQuantity')">
                <el-input-number v-model="form.qualifiedQuantity" :precision="scaleFor('qty')" :controls="false" disabled class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('不合格数量')" v-if="isFieldVisible('rejectedQuantity')" :required="isFieldRequired('rejectedQuantity')">
                <el-input-number v-model="form.rejectedQuantity" :precision="scaleFor('qty')" :controls="false" disabled class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('总金额')" v-if="isFieldVisible('totalAmount')" :required="isFieldRequired('totalAmount')">
                <el-input-number v-model="form.totalAmount" :precision="2" :controls="false" disabled class="field-input" />
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
                <el-input v-model="form.remark" type="textarea" :rows="2" class="field-input" :disabled="!isDraft" :placeholder="$tt('备注说明')" />
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
      :items="form.items"
      page-key="ProductionReceiptEdit_items"
      title="入库明细"
      subtitle="物料明细"
      col-setting-title="物料明细列设置"
      :columns="shownDetailColumns"
      :custom-columns="itemCustomColumns"
      :readonly="!isDraft"
      :show-total-qty="false"
      :summary-items="summaryItems"
      @remove="calcSummary"
    >
      <template #actions>
        <!-- 选来源单：上游 = 生产工单，明细取工单成品 products；剩余可入库 = quantity - 已入库(completedQuantity) -->
        <SourcePicker
          target-type="ProductionReceipts"
          items-field="products"
          :source-status="['已生效']"
          :disabled="!isDraft"
          btn-type="success"
          size="small"
          @pick="onSourcePicked"
        />
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
      <template #cell-batchNo="{ row }">
        <template v-if="batchVisible(row)">
          <el-input v-model="row.batchNo" size="small" :disabled="batchDisabled(row, !isDraft)" :placeholder="row._isLotControl ? '必填' : ''" />
          <span v-if="row._isLotControl" style="color:#f56c6c;margin-left:2px;">*</span>
        </template>
      </template>
      <template #cell-productionDate="{ row }">
        <el-date-picker v-if="row._isExpiryControl" v-model="row.productionDate" type="date" value-format="YYYY-MM-DD" size="small" style="width:100%" :disabled="!isDraft" @change="(v) => onProductionDateChange(row, v)" />
        <span v-else>-</span>
      </template>
      <template #cell-expiryDate="{ row }">
        <el-date-picker v-if="row._isExpiryControl" v-model="row.expiryDate" type="date" value-format="YYYY-MM-DD" size="small" style="width:100%" disabled />
        <span v-else>-</span>
      </template>
      <template #cell-sourceOperationId="{ row }">
        <el-select
          v-model="row.sourceOperationId"
          filterable
          clearable
          size="small"
          style="width:100%"
          :disabled="!isDraft || !sourceOperations.length"
          :placeholder="sourceOperations.length ? $tt('选择来源工序') : $tt('先选生产工单')"
        >
          <el-option
            v-for="op in sourceOperations"
            :key="op.id"
            :label="`${op.lineNo || ''} ${op.operationName || ''}`"
            :value="op.id"
          />
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
          type="production-receipt"
          :disabled="!isDraft"
          button-text="上传附件"
          tip-text="支持图片、PDF、Word、Excel 等文件"
        />
      </div>
    </div>

    <!-- 审核流转（真实审批进度，通用组件） -->
    <ApprovalFlowTimeline bill-type="PRODUCTION_RECEIPT" :bill-id="form.receiptId" :status="form.status" />

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
    <ChangeRecordDialog v-model="changeRecordVisible" bill-type="生产入库单" :bill-no="form.receiptNo || ''" />

    <!-- 打印预览弹窗 -->
    <PrintPreviewDialog v-model="printVisible" bill-type="PRODUCTION_RECEIPT" :bill-id="form.receiptId" />
    <!-- 入库质检（质检开关打开后可用） -->
    <QcInspectionDialog v-model="qcDialogVisible" bill-type="PRODUCTION_RECEIPT"
                        :bill-id="form.receiptId" :bill-no="form.receiptNo" @saved="onQcSaved" />
      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('来源单据')" name="source" :disabled="!form.receiptId">
        <BillLinkPanel v-if="topTab === 'source'" bill-type="PRODUCTION_RECEIPT" :bill-id="form.receiptId" mode="source" />
      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('去向单据')" name="target" :disabled="!form.receiptId">
        <BillLinkPanel v-if="topTab === 'target'" bill-type="PRODUCTION_RECEIPT" :bill-id="form.receiptId" mode="target" />
      </el-tab-pane>
    </el-tabs>
  </div>

  </template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { withFallbackOption } from '@/utils/labelFallback'
import { useBaseDataRefresh } from '@/composables/useBaseDataRefresh'
import { getCurrentEmployee } from '@/composables/useCurrentEmployee'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Document, Collection, Plus, Grid, Printer, ArrowRight, Check, ArrowDown, CircleCheck, CircleClose, Close, Clock, Memo } from '@element-plus/icons-vue'
import {
  getProductionReceipt, saveProductionReceipt, updateProductionReceipt,
  deleteProductionReceipt, approveProductionReceipt,
  closeProductionReceipt, cancelProductionReceipt,
  rejectProductionReceipt, reverseApproveProductionReceipt,
  unvoidProductionReceipt, reopenProductionReceipt
} from '@/api/production/productionReceipt'
import { useBillActions } from '@/composables/useBillActions'
import { listAllWarehouse } from '@/api/basedata/warehouse'
import { listAllLocation } from '@/api/basedata/location'
import { listAllMaterial } from '@/api/basedata/material'
import { listAllCurrency } from '@/api/basedata/currency'
import { listAllDepartment } from '@/api/basedata/department'
import { listAllEmployee } from '@/api/basedata/employee'
import { listAllUnit } from '@/api/basedata/unit'
import { pageWorkOrder } from '@/api/production/workOrder'
import { listWorkOrderOperation } from '@/api/production/workOrderOperation'
import ChangeRecordDialog from '@/components/ChangeRecordDialog.vue'
import QcInspectionDialog from '@/components/qc/QcInspectionDialog.vue'
import PrintPreviewDialog from '@/components/PrintPreviewDialog.vue'
import FileUploader from '@/components/FileUploader.vue'
import ApprovalFlowTimeline from '@/components/bill/ApprovalFlowTimeline.vue'
import BillLinkPanel from '@/components/bill/BillLinkPanel.vue'
import UnitPickerCell from '@/components/bill/UnitPickerCell.vue'
import { isBillLinkRegistered } from '@/api/billLink'
import { useAutoDocCode } from '@/composables/useAutoDocCode'

import * as BillStatus from '@/utils/bill-status'
import { confirmSubmit, submitSuccessMessage } from '@/utils/submit-confirm'
import { isBillApprovalEnabled } from '@/utils/approval-gate'
import BillActions from '@/components/bill/BillActions.vue'
import { triggerListRefresh } from '@/composables/useAutoRefresh'
import { useCopyBill } from '@/composables/useCopyBill'
import { tt } from '@/i18n'
import { useMaterialCustomFields } from '@/composables/useMaterialCustomFields'
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
// 物料库选择弹窗（通用件）：内置自定义字段搜索、全字段/自定义字段列、列设置全局共享 MaterialPicker
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
// 选来源单（通用件）：上游=生产工单，明细字段名/源单状态由本页传入
import SourcePicker from '@/components/SourcePicker.vue'
// 物料下拉的输入过滤 + 背景提示文字（通用件）
import { filterMaterials, buildMaterialPlaceholder } from '@/utils/materialFilter'
import { useBillLineStock, BILL_STOCK_COLUMNS } from '@/composables/useBillLineStock'
import { useBatchField } from '@/composables/useBatchField'
import { usePagePerm } from '@/utils/pagePerm'
// 全站商业口径总开关（设置中心：启用含税业务 / 启用外币业务）
import { useCommercialSwitches } from '@/composables/useCommercialSwitches'
// 设置中心参数读取（「默认计税方式」）
import { useSysConfig } from '@/composables/useSysConfig'
import { useFieldPermission } from '@/composables/useFieldPermission'
import { usePrecision } from '@/composables/usePrecision'

// 数量输入/显示的位数统一读设置中心「数量小数位数」（2026-11-10）
const { scaleFor, formatWith, trimZero } = usePrecision()

const perm = usePagePerm('production:receipts')
const { isFieldVisible, isFieldRequired } = useFieldPermission('production_receipt')

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
  } else if (!form.receiptId) {
    form.taxCalcMethod = normalizeTaxMethod(getConfig('sys.tax.method'))
  } else {
    form.taxCalcMethod = normalizeTaxMethod(form.taxCalcMethod)
  }
}

/** 表头「计税方式」切换 → 全表明细按新口径重算 */
function onTaxCalcMethodChange() {
  form.items.forEach(row => calcRow(row))
  calcSummary()
}

// 入库明细列定义（通用组件 BillItemsTable 消费）：editor = 单元格控件，type = 纯展示格式；
// 物料/单位/批次号/生产日期/有效期至 由页面提供插槽（#cell-<prop>）
const detailColumns = [
  { prop: 'materialId', label: '商品名称', minWidth: 280 },
  { prop: 'materialCode', label: '商品编号', width: 130 },
  ...BILL_STOCK_COLUMNS,
  { prop: 'specification', label: '规格', width: 120 },
  { prop: 'unit', label: '单位', width: 70, align: 'center' },
  { prop: 'quantity', label: '数量', width: 100, editor: 'number', precision: 4, change: (row) => calcRow(row) },
  { prop: 'qualifiedQuantity', label: '合格数量', width: 100, editor: 'number', precision: 4, change: () => calcSummary() },
  { prop: 'rejectedQuantity', label: '不合格数量', width: 100, editor: 'number', precision: 4, change: () => calcSummary() },
  { prop: 'unitPrice', label: '单价', width: 100, editor: 'number', precision: 4, change: (row) => calcRow(row) },
  { prop: 'priceWithTax', label: '含税价', width: 95, align: 'right', type: 'amount4' },
  { prop: 'priceWithoutTax', label: '不含税价', width: 95, align: 'right', type: 'amount4' },
  { prop: 'amount', label: '金额', width: 100, align: 'right', type: 'amount2' },
  { prop: 'stockId', label: '仓库', width: 130, editor: 'select', placeholder: '仓库', clearable: false,
    optionsFn: () => (warehouses.value || []).map(w => ({ value: w.stockId, label: w.stockName })),
    change: (row) => { row.binId = null } },
  { prop: 'binId', label: '货位', width: 110, editor: 'select', placeholder: '货位',
    optionsFn: (row) => getLocationsByStock(row.stockId).map(l => ({ value: l.binId, label: l.binName })) },
  { prop: 'batchNo', label: '批次号', width: 110 },
  // 2026-11-06：行级来源工序（入库类型工序完工自动生成的单据会自动带出；手工建单可逐行选）
  { prop: 'sourceOperationId', label: '来源工序', width: 150 },
  { prop: 'productionDate', label: '生产日期', width: 130 },
  { prop: 'expiryDate', label: '有效期至', width: 130 }
]
// 批次字段联动：按物料批次管理显隐，规则控制自动生成与只读
const { loadBatchSetting, applyBatchRule, batchVisible, batchDisabled, backfillLotControl } = useBatchField()
// 批次号列显隐由通用组件按明细物料统一判断
const shownDetailColumns = computed(() => detailColumns
  .map(c => {
    // 开启质检后合格/不合格数量由质检回写，明细里不再允许手改
    if (form.qcEnabled === 1 && (c.prop === 'qualifiedQuantity' || c.prop === 'rejectedQuantity')) {
      return { ...c, editor: undefined, change: undefined, align: 'right' }
    }
    return c
  })
)
// 汇总区（通用组件 BillItemsTable 按此渲染）：4 行标签/精度与迁移前的汇总块逐项一致，
// 因此本页不启用组件内置的「合计数量」行（show-total-qty=false），「总数量」行按原页原样保留
const summaryItems = computed(() => [
  { label: '总数量：', value: formatWith(form.totalQuantity, scaleFor('qty'), trimZero.value) },
  { label: '合格数量：', value: formatWith(form.qualifiedQuantity, scaleFor('qty'), trimZero.value) },
  { label: '不合格数量：', value: formatWith(form.rejectedQuantity, scaleFor('qty'), trimZero.value) },
  { label: '合计金额：', value: `¥${Number(form.totalAmount || 0).toFixed(2)}`, total: true }
])
useBillLineStock(() => form.items)

const route = useRoute()
const router = useRouter()
const headerFormRef = ref()

const isEdit = computed(() => !!route.params.id)

// 仅草稿态允许编辑（单号/业务字段/自定义字段）；已生效/待审批等只读
// 单据编码自动生成
const { docCodeDisabled, docCodePlaceholder, autoFillDocCodeOnCreate } = useAutoDocCode('PRODUCTION_RECEIPT')

const STORAGE_KEY = 'production-receipt-edit-collapsed'

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
const locations = ref([])
const workOrders = ref([])

const cancelDialog = ref(false)
const cancelReason = ref('')
const closeDialog = ref(false)
const closeReason = ref('')
const changeRecordVisible = ref(false)

const pickerVisible = ref(false)

const form = reactive({
  receiptId: null,
  receiptNo: '',
  receiptDate: new Date().toISOString().slice(0, 10),
  warehouseId: null,
  productionOrderId: null,
  productionOrderNo: '',
  // 2026-11-07：来源绑定（选单/手工选工单都要写，来源单据与去向单据两个页签据此互查）
  workOrderNo: '',
  sourceBillType: '',
  sourceBillNo: '',
  departmentId: null,
  handlerId: null,
  currencyId: null,
  exchangeRate: 1,
  taxCalcMethod: '不含税价',
  totalAmount: 0,
  totalQuantity: 0,
  qualifiedQuantity: 0,
  rejectedQuantity: 0,
  qcEnabled: 0,
  qcStatus: '未质检',
  qcTemplateId: null,
  remark: '',
  attachmentUrls: '',
  status: '草稿',
  createdAt: '',
  items: []
})


const isDraft = computed(() => BillStatus.canEdit(form.status, form.closeStatus))

// ===== 自定义字段（表头 production_receipt / 明细 production_receipt_item）=====
const headerCustomSections = ref([])
const headerCustomFieldMap = new Map()
const headerFormulaFields = ref([])
const itemCustomFields = ref([])
const itemCustomColumns = ref([])

async function loadCustomFields() {
  const headerResult = await loadCustomFieldDefinitions('production_receipt')
  headerCustomSections.value = headerResult.sections
  headerCustomFieldMap.clear()
  headerResult.fieldMap.forEach((v, k) => headerCustomFieldMap.set(k, v))
  headerFormulaFields.value = headerResult.formulaFields

  const itemResult = await loadCustomFieldDefinitions('production_receipt_item')
  itemCustomFields.value = Array.from(itemResult.fieldMap.values())
  itemCustomColumns.value = customFieldsToTableColumns(itemCustomFields.value, { editable: isDraft.value, listShowOnly: false })
}

// 草稿态变化时重新生成明细自定义字段列（editable 控制）
watch(isDraft, () => {
  if (itemCustomFields.value.length > 0) {
    itemCustomColumns.value = customFieldsToTableColumns(itemCustomFields.value, { editable: isDraft.value, listShowOnly: false })
  }
})

/**
 * 本单据无独立「提交」接口：草稿态下后端 approve 接口承担的就是「提交」
 * （开关开 → 落待审核+建待办；开关关 → 提交即生效）。
 * 因此开关开启时，草稿态的通用按钮栏「审核」在语义上其实是「提交」→ 本页隐藏该按钮，
 * 由同排的「提交」按钮完成该动作（待审核态的「审核」仍正常显示）。
 */
const receiptApprovalOn = ref(false)
isBillApprovalEnabled('PRODUCTION_RECEIPT')
  .then(v => { receiptApprovalOn.value = v === true })
  .catch(() => { receiptApprovalOn.value = false })
const rules = {
  receiptDate: [{ required: true, message: tt('请选择入库日期'), trigger: 'change' }],
  currencyId: [{ required: true, message: tt('请选择币种'), trigger: 'change' }]
}
/** 质检弹窗（2026-11-07）：表头仓库只作统一选择器，入库以明细行仓库为准 */
const qcDialogVisible = ref(false)
function handlingLabel (v) {
  return { RELEASE: '放行', RETURN: '自动退货', SCRAP: '进报废仓' }[v] || ''
}

function statusTagType(s) {
  return { '草稿': 'info', '已生效': 'success', '已关闭': 'warning', '已取消': 'danger' }[s] || 'info'
}

/** 当前行物料（多单位换算用：取基本单位、辅计量单位与换算率） */
function materialOf(row) {
  if (!row || row.materialId == null) return null
  return materials.value.find(x => x.materialId === row.materialId) || null
}

function getLocationsByStock(stockId) {
  if (!stockId) return []
  return locations.value.filter(l => l.stockId === stockId)
}

function onCurrencyChange(id) {
  const c = currencies.value.find(x => x.currencyId === id)
  if (c && c.exchangeRate) form.exchangeRate = c.exchangeRate
}

function onWarehouseChange() {
  // 表头仓库 = 统一选择器：选定后覆盖全部明细行（仍可逐行再改）
  form.items.forEach(row => {
    row.stockId = form.warehouseId || null
    row.binId = null
  })
}

/** 质检保存后：重新拉取单据，回显合格/不合格数量与质检状态 */
async function onQcSaved () {
  if (form.receiptId) await load()
}

function onWorkOrderChange(id) {
  const w = workOrders.value.find(x => x.orderId === id)
  if (w) {
    form.productionOrderNo = w.orderNo || ''
    // 2026-11-07：表头手工选生产工单也要写来源绑定，否则来源/去向单据两个页签互相查不到
    form.workOrderNo = w.orderNo || ''
    form.sourceBillType = '生产工单'
    form.sourceBillNo = w.orderNo || ''
  } else {
    form.workOrderNo = ''
    form.sourceBillType = ''
    form.sourceBillNo = ''
  }
  loadSourceOperations(id)
}

/** 明细行「来源工序」下拉：取该生产工单的工序（已取消的不列，无法再入库） */
const sourceOperations = ref([])
async function loadSourceOperations(orderId) {
  sourceOperations.value = []
  if (!orderId) return
  try {
    const res = await listWorkOrderOperation(orderId)
    const list = Array.isArray(res) ? res : (res?.records || [])
    sourceOperations.value = list.filter(o => o && o.operationStatus !== '已取消')
  } catch (e) {
    sourceOperations.value = []
  }
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
    // 物料批次/保质期管理联动
    row._isLotControl = m.isLotControl || false
    // 批次号规则：开启自动生成时带出可用批次号；不允许修改时字段只读
    await applyBatchRule(row, form.items)
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
  calcSummary()
}

function calcSummary() {
  form.totalAmount = form.items.reduce((s, r) => s + Number(r.amount || 0), 0).toFixed(2)
  form.totalQuantity = form.items.reduce((s, r) => s + Number(r.quantity || 0), 0).toFixed(4)
  form.qualifiedQuantity = form.items.reduce((s, r) => s + Number(r.qualifiedQuantity || 0), 0).toFixed(4)
  form.rejectedQuantity = form.items.reduce((s, r) => s + Number(r.rejectedQuantity || 0), 0).toFixed(4)
}

function addItem() {
  form.items.push({
    materialId: null,
    materialCode: '',
    materialName: '',
    specification: '',
    unitId: null,
    quantity: 0,
    qualifiedQuantity: 0,
    rejectedQuantity: 0,
    unitPrice: 0,
    priceWithTax: 0,
    priceWithoutTax: 0,
    amount: 0,
    stockId: form.warehouseId || null,
    binId: null,
    batchNo: '',
    productionDate: '',
    expiryDate: '',
    _isLotControl: false,
    _isExpiryControl: false,
    _expiryDays: 0
  })
  calcSummary()
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
      qualifiedQuantity: 1,
      rejectedQuantity: 0,
      unitPrice: Number(m.purchasePrice || 0),
      priceWithTax: 0,
      priceWithoutTax: 0,
      amount: Number(m.purchasePrice || 0),
      stockId: form.warehouseId || null,
      binId: null,
      batchNo: '',
      productionDate: '',
      expiryDate: '',
      _isLotControl: m.isLotControl || false,
      _isExpiryControl: m.isExpiryControl || false,
      _expiryDays: m.expiryDays || 0
    }
    calcRow(row)
    form.items.push(row)
  })
  pickerVisible.value = false
  ElMessage.success(`${tt("已添加 ")}${picked.length}${tt(" 条明细")}`)
}

/**
 * 选来源单回调：从生产工单「选单」带入（追加到已有明细，不覆盖）
 * - items 已由 SourcePicker 按 pushRelations 的 itemMapper(WorkOrders→ProductionReceipts) 映射；
 *   源单没有的字段（批次号/生产日期/有效期等）保持空值，不臆造
 * - 合格数量与入库数量同口径（对齐后端 pushToProductionReceipt：本次入库全部记合格）
 * - 表头只回填本页已声明的字段（生产订单/工单号/仓库等）
 * - 复用本页 calcRow/calcSummary 重算数量与金额汇总
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
      row.qualifiedQuantity = Number(row.quantity || 0)
      if (row.rejectedQuantity === undefined || row.rejectedQuantity === null) row.rejectedQuantity = 0
      if (!row.stockId) row.stockId = form.warehouseId || null
      if (row.unitPrice === undefined || row.unitPrice === null) row.unitPrice = 0
      form.items.push(row)
      calcRow(row)
    })
    ElMessage.success(`${tt("已从源单带入 ")}${picked.length}${tt(" 条明细")}`)
  }
  loadSourceOperations(form.productionOrderId)
  calcSummary()
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
  calcSummary()
  try {
    if (form.receiptId) {
      await updateProductionReceipt(form.receiptId, form)
    } else {
      const res = await saveProductionReceipt(form)
      if (res && res.receiptId) form.receiptId = res.receiptId
      if (res && res.receiptNo) form.receiptNo = res.receiptNo
      if (res && res.status) form.status = res.status
    
      if (!opts.skipRouteSync && !route.params.id && form.receiptId) {
        try { await router.replace({ name: 'ProductionReceiptEdit', params: { id: String(form.receiptId) } }) } catch (_) {}
      }
    }
    if (headerCustomFieldMap.size > 0 && form.receiptId) {
      const headerValues = {}
      for (const key of headerCustomFieldMap.keys()) {
        if (key in form) headerValues[key] = form[key]
      }
      await saveCustomFieldValues('production_receipt', form.receiptId, headerValues, headerFormulaFields.value)
    }
    if (itemCustomFields.value.length > 0 && form.items.length > 0) {
      // 保存主表时后端走「明细先删后插」，明细主键已变，必须先按新主键对齐再写明细自定义字段
      try {
        const fresh = await getProductionReceipt(form.receiptId)
        remapItemIdsAfterSave(form.items, (fresh && fresh.items) || [], 'receiptItemId')
      } catch (e) {
        console.warn('[productionReceipt] 保存后刷新明细主键失败，明细级自定义字段可能挂到旧行', e)
      }
      const itemKeys = new Set(itemCustomFields.value.map(f => f.fieldKey))
      for (const row of form.items) {
        if (!row.receiptItemId) continue
        const values = {}
        for (const key of itemKeys) {
          if (key in row) values[key] = row[key]
        }
        await saveCustomFieldValues('production_receipt_item', row.receiptItemId, values)
      }
    }
    triggerListRefresh('/production/receipts')
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
  // 2026-11-07：入库以明细行仓库为准，逐行精确提示
  for (let i = 0; i < form.items.length; i++) {
    const row = form.items[i]
    if (!row.stockId) {
      ElMessage.warning(`${tt('第')}${i + 1}${tt('行')}${row.materialCode || ''} ${row.materialName || ''}${tt('未选择仓库，请选择仓库')}`)
      return
    }
  }
  if (form.qcEnabled === 1 && form.qcStatus !== '已质检') {
    ElMessage.warning(tt('本单据已开启质检，请先填写并保存质检再提交'))
    return
  }
  // 无论新建还是已有草稿，提交前一律先静默保存（把当前页面所见落库，避免提交旧数据）
  const saved = await onSave(true, { skipRouteSync: true })
  if (!saved || !form.receiptId) return
  try {
    const needApproval = await confirmSubmit({ billType: 'PRODUCTION_RECEIPT', billLabel: '生产入库单' })
    await approveProductionReceipt(form.receiptId)
    ElMessage.success(submitSuccessMessage(needApproval))
    triggerListRefresh('/production/receipts')
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('提交失败'))
  } finally {
    // 提交（或用户取消）后再把新单 id 写回地址栏：此时重建实例无副作用，新实例挂载时会重新拉取最新状态
    if (!route.params.id && form.receiptId) {
      try { await router.replace({ name: 'ProductionReceiptEdit', params: { id: String(form.receiptId) } }) } catch (_) {}
    }
  }
}



async function doCancel() {
  if (!form.receiptId) { ElMessage.warning(tt('请先保存单据')); return }
  if (!cancelReason.value || !cancelReason.value.trim()) {
    ElMessage.warning(tt('请输入取消原因'))
    return
  }
  try {
    await cancelProductionReceipt(form.receiptId, cancelReason.value)
    ElMessage.success(tt('取消成功'))
    cancelDialog.value = false
    cancelReason.value = ''
    await loadDetail()
  } catch (e) {
    ElMessage.error(e.message || tt('取消失败'))
  }
}

async function doClose() {
  if (!form.receiptId) { ElMessage.warning(tt('请先保存单据')); return }
  try {
    await closeProductionReceipt(form.receiptId, closeReason.value || '')
    ElMessage.success(tt('关闭成功'))
    closeDialog.value = false
    closeReason.value = ''
    await loadDetail()
  } catch (e) {
    ElMessage.error(e.message || tt('关闭失败'))
  }
}

async function onDelete() {
  if (!form.receiptId) { ElMessage.warning(tt('请先保存单据')); return }
  try {
    await ElMessageBox.confirm(`${tt("确认删除生产入库单 ")}${form.receiptNo}?`, tt('提示'), { type: 'warning' })
    await deleteProductionReceipt(form.receiptId)
    ElMessage.success(tt('删除成功'))
    router.push('/production/receipts')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('删除失败'))
  }
}

const printVisible = ref(false)
function handlePrint() {
  if (!form.receiptId) { ElMessage.warning(tt('请先保存单据后再打印')); return }
  printVisible.value = true
}
async function loadDetail() {
  const __detailId = route.params.id || form.receiptId || ''
  if (__detailId) {
    try {
      const res = await getProductionReceipt(__detailId)
      Object.assign(form, res)
      if (!form.items) form.items = []
      if (headerCustomFieldMap.size > 0 && res.receiptId) {
        const headerValues = await loadCustomFieldValues('production_receipt', res.receiptId)
        Object.assign(form, headerValues)
      }
      if (itemCustomFields.value.length > 0 && form.items.length > 0) {
        const itemIds = form.items.map(it => it.receiptItemId).filter(Boolean)
        if (itemIds.length > 0) {
          const itemValuesMap = await batchLoadCustomFieldValues('production_receipt_item', itemIds)
          form.items.forEach(row => {
            const values = itemValuesMap[row.receiptItemId]
            if (values) Object.assign(row, values)
          })
        }
      }
      // 计税方式：老单据按设置中心默认/总开关归一，再按口径回算明细两列
      await initTaxSettings()
      form.items.forEach(row => calcRow(row))
      // 批次字段联动：重新打开单据时回填「批次管理」标志，必填星号不再丢失
      backfillLotControl(form.items, materials.value)
      calcSummary()
      loadSourceOperations(form.productionOrderId)
    } catch (e) {
      ElMessage.error(e.message || tt('加载失败'))
    }
  } else {
    // 新建时默认当前用户为经手人（可修改）
    const me = await getCurrentEmployee()
    if (me && !form.handlerId) form.handlerId = me.employeeId
    // 新建：表头「计税方式」取设置中心「默认计税方式」
    await initTaxSettings()
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
  getId: () => form.receiptId,
  api: {
    approve: approveProductionReceipt,
    reject: rejectProductionReceipt,
    reverseApprove: reverseApproveProductionReceipt,
    close: (id) => closeProductionReceipt(id, ''),
    reopen: reopenProductionReceipt,
    void: cancelProductionReceipt,
    unvoid: unvoidProductionReceipt,
    delete: deleteProductionReceipt
  },
  reload: loadDetail,
  router,
  listPath: '/production/receipts',
  newPath: '/production/receipts/edit',
  billLabel: '生产入库单'
})

// 加载下拉基础数据——独立函数，供 onMounted 和切回标签页时复用
  async function loadBaseData() {
  // 全站商业口径总开关（启用含税业务 / 启用外币业务）：优先拉取最新值
  await ensureCommercialSwitches()
  try {
      const [wh, mat, cur, dep, emp, unitRes, locRes, woRes] = await Promise.all([
        listAllWarehouse(), listAllMaterial(), listAllCurrency(),
        listAllDepartment(), listAllEmployee(), listAllUnit(),
        listAllLocation(), pageWorkOrder({ current: 1, size: 100 })
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
      locations.value = locRes || []
      workOrders.value = woRes?.records || woRes || []
    } catch (e) {
      console.warn('下拉数据加载失败:', e)
    }
}

// ===== 复制功能 =====
const { applyCopy } = useCopyBill({
  route,
  loadFn: getProductionReceipt,
  form,
  idField: 'receiptId',
  noField: 'receiptNo',
  itemIdField: 'receiptItemId',
  itemsField: 'items',
  afterCopy: () => calcSummary()
})

// ===== 顶级页签：单据 / 来源单据 / 去向单据 =====
const topTab = ref('doc')
const billLinkRegistered = ref(false)

onMounted(async () => {
  // 物料自定义字段：定制开关 + 字段定义（失败静默降级）
  await loadBatchSetting()
  await mcf.init()
  await loadCustomFields()
  // 该单据类型是否登记了「来源单据/去向单据」配置（未登记则不显示这两个页签）
  isBillLinkRegistered('PRODUCTION_RECEIPT')
    .then(v => { billLinkRegistered.value = !!v })
    .catch(() => { billLinkRegistered.value = false })

  loadCollapsed()
  await loadBaseData()
  if (!isEdit.value) {
    autoFillDocCodeOnCreate(form, 'receiptNo')
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
.production-receipt-edit {
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
  .production-receipt-edit { padding: 8px; }
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
.qc-inline {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
</style>
