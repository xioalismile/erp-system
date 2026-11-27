<template>
  <div class="purchase-invoice-edit">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div class="header-left">
        <div class="back-btn" @click="$router.push('/purchase/invoices')">
          <el-icon><ArrowLeft /></el-icon>
          <span>{{ $tt("返回列表") }}</span>
        </div>
        <div class="title-area">
          <div class="title-row">
            <el-icon class="title-icon"><Document /></el-icon>
            <span class="page-title">{{ isEdit ? '采购发票' : '新增采购发票' }}</span>
            <span v-if="form.invoiceNo" class="order-no">
              <el-icon><Collection /></el-icon>
              {{ form.invoiceNo }}
            </span>
            <el-tag v-if="form.status" size="small" :type="statusTagType(form.status)" effect="plain" class="status-tag">{{ $dt(form.status) }}</el-tag>
            <el-tag v-if="form.isRedInvoice" size="small" type="danger" effect="plain">{{ $tt("红字发票") }}</el-tag>
            <el-tag v-if="form.isMatch" size="small" type="success" effect="plain">{{ $tt("已勾稽") }}</el-tag>
          </div>
        </div>
      </div>
      <div class="header-right">
        <el-button v-permission="perm('view')" v-if="!(!form.orderId && !form.deliveryId && !form.inboundId && !form.invoiceId && !form.voucherId && !form.contractId && !form.requisitionId && !form.stockCheckId && !form.transferId && !form.bomId && !form.sampleId && !form.freeSampleId && !form.workOrderId && !form.billId && !form.id)" @click="changeRecordVisible = true">
          <el-icon><Memo /></el-icon>{{ $tt('操作日志') }}
        </el-button>
        <BillActions bill-type="PURCHASE_INVOICE"
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
        <el-form :model="form" :rules="rules" ref="headerFormRef" label-width="90px" class="base-form">
          <!-- 单据信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>单据信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('发票号')">
                <el-input v-model="form.invoiceNo" :placeholder="docCodePlaceholder" :disabled="!isDraft" class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('发票类型')" prop="invoiceType" required>
                <el-select v-model="form.invoiceType" :placeholder="$tt('选择发票类型')" class="field-input" :disabled="readOnly">
                  <el-option :label="$tf('专票')" value="专票" />
                  <el-option :label="$tf('普票')" value="普票" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('发票日期')" prop="invoiceDate" required>
                <el-date-picker v-model="form.invoiceDate" type="date" value-format="YYYY-MM-DD" class="field-input" :disabled="readOnly" />
              </el-form-item>
              <el-form-item :label="$tf('发票代码')">
                <el-input v-model="form.invoiceCode" class="field-input" :placeholder="$tt('发票代码')" :disabled="readOnly" />
              </el-form-item>
              <el-form-item :label="$tf('发票号码')">
                <el-input v-model="form.invoiceNumber" class="field-input" :placeholder="$tt('发票号码')" :disabled="readOnly" />
              </el-form-item>
              <el-form-item :label="$tf('是否红字')">
                <el-switch v-model="form.isRedInvoice" :active-value="1" :inactive-value="0" :disabled="readOnly" />
                <el-tag size="small" style="margin-left:8px;" :type="form.isRedInvoice ? 'danger' : 'info'">{{ form.isRedInvoice ? '红字' : '蓝字' }}</el-tag>
              </el-form-item>
              <el-form-item :label="$tf('勾稽状态')">
                <el-tag size="small" :type="form.isMatch ? 'success' : 'info'">{{ form.isMatch ? '已勾稽' : '未勾稽' }}</el-tag>
              </el-form-item>
            </div>
          </div>

          <!-- 供应商信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>供应商信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('供应商')" prop="supplierId" required>
                <el-select v-model="form.supplierId" filterable :placeholder="$tt('选择供应商')" class="field-input" :disabled="readOnly" @change="onSupplierChange">
                  <el-option v-for="s in withFallbackOption(suppliers, 'supplierId', 'supplierName', form)" :key="s.supplierId" :label="s.supplierName" :value="s.supplierId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('供应商编号')">
                <el-input v-model="form.supplierCode" :disabled="!isDraft" readonly class="field-input"  />
              </el-form-item>
              <el-form-item :label="$tf('供应商税号')" prop="supplierTaxNo">
                <el-input v-model="form.supplierTaxNo" class="field-input" :placeholder="$tt('纳税人识别号（专票必填）')" :disabled="readOnly" />
              </el-form-item>
              <el-form-item :label="$tf('联系人')">
                <el-input v-model="form.contactPerson" class="field-input" :disabled="readOnly" />
              </el-form-item>
              <el-form-item :label="$tf('联系电话')">
                <el-input v-model="form.contactPhone" class="field-input" :disabled="readOnly" />
              </el-form-item>
              <el-form-item :label="$tf('收货地址')" class="span-all">
                <el-input v-model="form.deliveryAddress" class="field-input" :disabled="readOnly" />
              </el-form-item>
            </div>
          </div>

          <!-- 交易信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>交易信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('币种')" prop="currencyId" required>
                <el-select v-model="form.currencyId" :placeholder="$tt('选择币种')" class="field-input" :disabled="readOnly || !foreignCurrencyEnabled" @change="onCurrencyChange">
                  <el-option v-for="c in currencies" :key="c.currencyId" :label="c.currencyCode" :value="c.currencyId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('汇率')">
                <el-input-number v-model="form.exchangeRate" :precision="6" :step="0.1" :min="0" class="field-input" :disabled="readOnly || !foreignCurrencyEnabled" @change="calcTotal" />
              </el-form-item>
              <el-form-item v-if="taxEnabled" :label="$tf('计税方式')">
                <el-select v-model="form.taxCalcMethod" class="field-input" :disabled="readOnly" @change="onTaxCalcMethodChange">
                  <el-option v-for="o in taxMethodOptions" :key="o" :label="$tf(o)" :value="o" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('整单折扣')">
                <el-input-number v-model="form.discountRate" :precision="2" :step="1" :min="0" :max="100" class="field-input" :disabled="readOnly" />
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

          <!-- 备注 -->
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
      page-key="PurchaseInvoiceEdit_items"
      title="发票明细"
      subtitle="物料明细"
      col-setting-title="物料明细列设置"
      :columns="detailColumns"
      :custom-columns="itemCustomColumns"
      :readonly="readOnly"
      :total-qty="totalQty"
      :summary-items="summaryItems"
      :source-link-click="goToSourceBill"
      @row-change="onRowPriceChange"
      @remove="calcTotal"
    >
      <template #actions>
        <SourcePicker ref="sourcePickerRef" target-type="PurchaseInvoice" @pick="onSourcePicked" :disabled="readOnly" btn-type="success" size="small" />
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
      <template #cell-unit="{ row }">
        <UnitPickerCell
          v-model="row.unitId"
          :units="units"
          :material="materialOf(row)"
          :disabled="readOnly"
          @change="calcRow(row)"
        />
      </template>
      <template #cell-taxCalcMethod="{ row }">
        <el-select v-model="row.taxCalcMethod" size="small" style="width:100%" :disabled="readOnly" @change="calcRow(row)">
          <el-option :label="$tf('价外税')" value="价外税" />
          <el-option :label="$tf('价内税')" value="价内税" />
          <el-option :label="$tf('不计税')" value="不计税" />
        </el-select>
      </template>
      <template #cell-batchNo="{ row }">
        <el-input v-model="row.batchNo" size="small" :placeholder="row._isLotControl ? '必填' : ''" :disabled="readOnly" />
        <span v-if="row._isLotControl" style="color:#f56c6c;margin-left:2px;">*</span>
      </template>
      <template #cell-productionDate="{ row }">
        <el-date-picker v-if="row._isExpiryControl" v-model="row.productionDate" type="date" value-format="YYYY-MM-DD" size="small" style="width:100%" :disabled="readOnly" @change="(v) => onProductionDateChange(row, v)" />
        <span v-else class="thumb-placeholder">-</span>
      </template>
      <template #cell-expiryDate="{ row }">
        <el-date-picker v-if="row._isExpiryControl" v-model="row.expiryDate" type="date" value-format="YYYY-MM-DD" size="small" style="width:100%" disabled />
        <span v-else class="thumb-placeholder">-</span>
      </template>
      <template #cell-taxCategory="{ row }">
        <el-input v-model="row.taxCategory" size="small" style="width:100%" :disabled="readOnly" />
      </template>
      <template #summary-extra>
        <!-- 本位币折算（非人民币单据才显示） -->
        <div v-if="form.currencyCode && form.currencyCode !== 'CNY'" class="summary-block">
          <div class="block-title">{{ $tt("本位币折算") }}</div>
          <div class="sum-line">
            <span class="label">{{ $tt("折前金额：") }}</span>
            <span class="value">¥{{ Number(form.baseTotalAmount || 0).toFixed(2) }}</span>
          </div>
          <div class="sum-line">
            <span class="label">{{ $tt("税额：") }}</span>
            <span class="value">¥{{ Number(form.baseTaxAmount || 0).toFixed(2) }}</span>
          </div>
          <div class="sum-line total">
            <span class="label">{{ $tt("价税合计：") }}</span>
            <span class="value">¥{{ Number(form.baseNetAmount || 0).toFixed(2) }}</span>
          </div>
        </div>
        <!-- 单据信息 -->
        <div class="summary-block">
          <div class="block-title">{{ $tt("单据信息") }}</div>
          <div class="sum-line">
            <span class="label">{{ $tt("审核人：") }}</span>
            <span class="value">{{ form.approvedBy }}</span>
          </div>
          <div class="sum-line">
            <span class="label">{{ $tt("审核日期：") }}</span>
            <span class="value">{{ form.approvedAt || '—' }}</span>
          </div>
          <div class="sum-line">
            <span class="label">{{ $tt("制单人：") }}</span>
            <span class="value">{{ form.createdBy || '—' }}</span>
          </div>
          <div class="sum-line">
            <span class="label">{{ $tt("制单日期：") }}</span>
            <span class="value">{{ form.createdAt || '—' }}</span>
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
          type="purchase-invoice"
          :disabled="readOnly"
          button-text="上传附件"
          tip-text="支持图片、PDF、Word、Excel 等文件"
        />
      </div>
    </div>

    <!-- 审核流转（真实审批进度，通用组件） -->
    <ApprovalFlowTimeline bill-type="PURCHASE_INVOICE" :bill-id="form.invoiceId" :status="form.status" />

    <!-- 取消弹窗 -->
    <el-dialog v-model="cancelDialog" :title="$tf('取消采购发票')" width="420px">
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

    <!-- 打印预览弹窗 -->
    <PrintPreviewDialog v-model="printVisible" bill-type="PURCHASE_INVOICE" :bill-id="form.invoiceId" />
    <ChangeRecordDialog v-model="changeRecordVisible" bill-type="采购发票" :bill-no="form.invoiceNo || ''" />

      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('来源单据')" name="source" :disabled="!form.invoiceId">
        <BillLinkPanel v-if="topTab === 'source'" bill-type="PURCHASE_INVOICE" :bill-id="form.invoiceId" mode="source" />
      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('去向单据')" name="target" :disabled="!form.invoiceId">
        <BillLinkPanel v-if="topTab === 'target'" bill-type="PURCHASE_INVOICE" :bill-id="form.invoiceId" mode="target" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { withFallbackOption } from '@/utils/labelFallback'
import { calcTaxAmount } from '@/utils/billTax'
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useBaseDataRefresh } from '@/composables/useBaseDataRefresh'
import { useCommercialSwitches } from '@/composables/useCommercialSwitches'
import { useSysConfig } from '@/composables/useSysConfig'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Document, Collection, Plus, Grid, Printer, Link, ArrowRight, Check, TopRight, ArrowDown, CircleCheck, CircleClose, RefreshLeft, Close, Share, Clock, Memo } from '@element-plus/icons-vue'
import { listAllMaterial } from '@/api/basedata/material'
import { listAllSupplier } from '@/api/basedata/supplier'
import { listAllUnit } from '@/api/basedata/unit'
import { listAllCurrency } from '@/api/basedata/currency'
import FileUploader from '@/components/FileUploader.vue'
import SourcePicker from '@/components/SourcePicker.vue'
import ApprovalFlowTimeline from '@/components/bill/ApprovalFlowTimeline.vue'
import PrintPreviewDialog from '@/components/PrintPreviewDialog.vue'
import ChangeRecordDialog from '@/components/ChangeRecordDialog.vue'
import { applyPushSource } from '@/utils/pushSourceApplier'
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
import UnitPickerCell from '@/components/bill/UnitPickerCell.vue'
// 物料库选择弹窗（通用件）：内置自定义字段搜索、全字段/自定义字段列、列设置全局共享 MaterialPicker
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
// 物料下拉的输入过滤 + 背景提示文字（通用件）
import { filterMaterials, buildMaterialPlaceholder } from '@/utils/materialFilter'
import { useBillLineStock, BILL_STOCK_COLUMNS } from '@/composables/useBillLineStock'
import { triggerListRefresh } from '@/composables/useAutoRefresh'
// 单据取价（2026-09-20 价格策略修复）：按设置中心「取价优先级」取价，选物料/改数量时回填单价
import { resolveUnitPrice, applyFetchedUnitPrice } from '@/composables/usePriceFetch'
import { useAutoDocCode } from '@/composables/useAutoDocCode'

import * as BillStatus from '@/utils/bill-status'
import { useFieldPermission } from '@/composables/useFieldPermission'
import { useFieldRequiredMark } from '@/utils/fieldRequiredMark'
import { confirmSubmit, submitSuccessMessage } from '@/utils/submit-confirm'
import {
  getPurchaseInvoice,
  savePurchaseInvoice,
  updatePurchaseInvoice,
  submitPurchaseInvoice,
  approvePurchaseInvoice,
  reverseApprovePurchaseInvoice,
  cancelPurchaseInvoice,
  rejectPurchaseInvoice,
  closeOrderPurchaseInvoice,
  reopenPurchaseInvoice,
  voidPurchaseInvoice,
  unvoidPurchaseInvoice,
  deletePurchaseInvoice
} from '@/api/purchase/invoice'
import BillActions from '@/components/bill/BillActions.vue'
import BillLinkPanel from '@/components/bill/BillLinkPanel.vue'
import { isBillLinkRegistered } from '@/api/billLink'
import { useBillActions } from '@/composables/useBillActions'
import { useCopyBill } from '@/composables/useCopyBill'
import { tt } from '@/i18n'
import { useMaterialCustomFields } from '@/composables/useMaterialCustomFields'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('purchase:invoices')

// 全站商业口径总开关（设置中心：启用含税业务 / 启用外币业务）
const { taxEnabled, foreignCurrencyEnabled, ensureLoaded: ensureCommercialSwitches } = useCommercialSwitches()
const { loadSysConfig, getConfig } = useSysConfig()

// ===== 计税方式（含税价 / 不含税价 / 不计税）：采购发票为行级口径，表头值仅作新增行默认值 =====
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
    // 读取失败保持默认
  }
  if (!taxEnabled.value) {
    form.taxCalcMethod = '不计税'
  } else if (!form.invoiceId) {
    form.taxCalcMethod = normalizeTaxMethod(getConfig('sys.tax.method'))
  } else {
    form.taxCalcMethod = normalizeTaxMethod(form.taxCalcMethod)
  }
}

/** 行级口径：表头「计税方式」仅作为「新增明细行」的默认值，不覆盖用户逐行选择 */
function onTaxCalcMethodChange() {
  // 保留每行自己的计税方式（价内税/价外税/不计税）
}

// 单据编码自动生成
const { docCodeDisabled, docCodePlaceholder, autoFillDocCodeOnCreate } = useAutoDocCode('PURCHASE_INVOICE')

/** 发票金额录入方式（设置中心 → 系统参数 → 发票录入口径）：qty_price=按数量单价 / amount=按发票金额 */
const invoiceAmountMode = computed(() => String(getConfig('sys.invoice.entry.mode', 'qty_price')) === 'amount')
/** 金额模式下可录入的金额列：表头含税价→开票价税合计；不含税价/不计税→开票金额 */
const amountEntryProp = computed(() => {
  const method = taxEnabled.value ? (form.taxCalcMethod || '不含税价') : '不计税'
  return method === '含税价' ? 'netAmount' : 'amount'
})

// 物料明细列定义（通用组件 BillItemsTable 消费）：editor = 单元格控件，type = 纯展示格式；
// 物料/计税方式/批次号/生产日期/有效期至/税收分类编码列由页面提供插槽（#cell-<prop>），来源单号列由组件的 sourceLinkClick 回调跳转来源单
// 金额模式（按发票金额）下：数量/单价/折扣率只读，金额列可录入（哪一列可录入由表头计税方式决定）
const detailColumns = computed(() => {
  const amtMode = invoiceAmountMode.value
  const entryProp = amountEntryProp.value
  const amountEditor = { editor: 'number', precision: 2, min: 0 }
  return [
    { prop: 'materialMainImage', label: '图片', width: 60, align: 'center', type: 'image' },
    { prop: 'materialId', label: '商品名称', minWidth: 280 },
    { prop: 'materialCode', label: '商品编号', width: 130 },
    ...BILL_STOCK_COLUMNS,
    { prop: 'model', label: '规格', width: 110, type: 'text' },
    { prop: 'unit', label: '单位', width: 60, align: 'center', type: 'text' },
    { prop: 'quantity', label: '数量', width: 90, ...(amtMode ? {} : { editor: 'number', precision: 4 }) },
    { prop: 'unitPrice', label: '单价', width: 90, ...(amtMode ? {} : { editor: 'number', precision: 4 }) },
    { prop: 'priceWithTax', label: '含税价', width: 95, align: 'right', type: 'amount4' },
    { prop: 'priceWithoutTax', label: '不含税价', width: 95, align: 'right', type: 'amount4' },
    { prop: 'taxRate', label: '税率%', width: 70, editor: 'number', precision: 0, min: 0, max: 100 },
    { prop: 'taxCalcMethod', label: '计税方式', width: 100 },
    { prop: 'discountRate', label: '折扣率%', width: 80, ...(amtMode ? {} : { editor: 'number', precision: 2, min: 0, max: 100 }) },
    { prop: 'discountAmount', label: '开票折扣额', width: 95, align: 'right', type: 'amount2', cellClass: 'discount-text' },
    { prop: 'amount', label: '开票金额', width: 100, align: 'right',
      ...(amtMode && entryProp === 'amount' ? amountEditor : { type: 'amount2' }) },
    { prop: 'taxAmount', label: '开票税额', width: 95, align: 'right', type: 'amount2' },
    { prop: 'netAmount', label: '开票价税合计', width: 110, align: 'right', cellClass: 'net-amount',
      ...(amtMode && entryProp === 'netAmount' ? amountEditor : { type: 'amount2' }) },
    { prop: 'avgCost', label: '成本', width: 85, align: 'right', type: 'amount4' },
    { prop: 'batchNo', label: '批次号', width: 120 },
    { prop: 'productionDate', label: '生产日期', width: 130 },
    { prop: 'expiryDate', label: '有效期至', width: 130 },
    { prop: 'taxCategory', label: '税收分类编码', width: 150 },
    { prop: 'sourceBillNo', label: '来源单号', minWidth: 140, type: 'sourceLink' }
  ]
})
// 汇总区（通用组件 BillItemsTable 按此渲染；「合计数量」行由组件按 totalQty 渲染，金额口径与迁移前一致）
const summaryItems = computed(() => {
  const items = [
    { label: '开票折前金额：', value: `¥${Number(form.totalAmount || 0).toFixed(2)}` },
    { label: '开票折扣金额：', value: `- ¥${Number(form.discountAmount || 0).toFixed(2)}`, class: 'discount' }
  ]
  // 含税业务关闭：不显示「合计开票税额」
  if (taxEnabled.value) {
    items.push({ label: '合计开票税额：', value: `¥${Number(form.taxAmount || 0).toFixed(2)}` })
  }
  items.push({ label: '开票价税合计：', value: `¥${Number(form.taxTotalAmount || 0).toFixed(2)}`, total: true })
  return items
})
useBillLineStock(() => form.items)
// ===== 物料库弹窗的列定义 / 列设置 / 图片渲染 已收进通用组件 MaterialPickerDialog =====
// ===== 明细列设置（显隐/排序/自定义字段列/合计行）已收进通用组件 BillItemsTable =====

const route = useRoute()
const router = useRouter()
const formRef = ref()
const headerFormRef = ref()

const isEdit = computed(() => !!route.params.id)

// 仅草稿态允许编辑（单号/业务字段/自定义字段）；已生效/待审批等只读
const STORAGE_KEY = 'purchase-invoice-edit-collapsed'

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
  const headerResult = await loadCustomFieldDefinitions('purchase_invoice')
  headerCustomSections.value = headerResult.sections
  headerCustomFieldMap.clear()
  headerResult.fieldMap.forEach((v, k) => headerCustomFieldMap.set(k, v))
  headerFormulaFields.value = headerResult.formulaFields

  const itemResult = await loadCustomFieldDefinitions('purchase_invoice_item')
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

const materials = ref([])
const suppliers = ref([])
const units = ref([])
/** 当前行物料（多单位换算用：取基本单位、辅计量单位与换算率） */
function materialOf(row) {
  if (!row || row.materialId == null) return null
  return materials.value.find(x => x.materialId === row.materialId) || null
}
const currencies = ref([])

const cancelDialog = ref(false)
const cancelReason = ref('')
const printVisible = ref(false)
const sourcePickerVisible = ref(false)
const sourcePickerRef = ref()
const changeRecordVisible = ref(false)

const pickerVisible = ref(false)

const form = reactive({
  invoiceId: null,
  invoiceNo: '',
  invoiceType: '专票',
  invoiceDate: new Date().toISOString().slice(0, 10),
  supplierId: null,
  supplierName: '',
  supplierCode: '',
  supplierTaxNo: '',
  contactPerson: '',
  contactPhone: '',
  deliveryAddress: '',
  sourceType: '采购入库单',
  sourceBillNo: '',
  amount: 0,
  totalAmount: 0,
  discountRate: 0,
  discountAmount: 0,
  taxCalcMethod: '不含税价',
  taxAmount: 0,
  taxTotalAmount: 0,
  currencyId: null,
  currencyCode: 'CNY',
  exchangeRate: 1.0,
  baseTotalAmount: 0,
  baseTaxAmount: 0,
  baseNetAmount: 0,
  invoiceCode: '',
  invoiceNumber: '',
  status: '草稿',
  isRedInvoice: 0,
  isMatch: 0,
  attachmentUrls: '',
  approvedBy: '',
  approvedAt: '',
  createdBy: '',
  createdAt: '',
  updatedBy: '',
  updatedAt: '',
  remark: '',
  items: []
})


const isDraft = computed(() => BillStatus.canEdit(form.status, form.closeStatus))
const totalQty = computed(() => {
  return form.items.reduce((sum, row) => sum + Number(row.quantity || 0), 0).toFixed(2)
})

const rules = {
  invoiceType: [{ required: true, message: tt('请选择发票类型'), trigger: 'change' }],
  invoiceDate: [{ required: true, message: tt('请选择发票日期'), trigger: 'change' }],
  supplierId: [{ required: true, message: tt('请选择供应商'), trigger: 'change' }],
  currencyId: [{ required: true, message: tt('请选择币种'), trigger: 'change' }],
  supplierTaxNo: [
    {
      validator: (rule, value, callback) => {
        if (form.invoiceType === '专票' && (!value || !value.trim())) {
          callback(new Error('专票必须填写供应商税号'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

const readOnly = computed(() => !BillStatus.canEdit(form.status, form.closeStatus))

function statusTagType(s) {
  return { '草稿': 'info', '待审核': 'warning', '已生效': 'success', '已取消': 'danger' }[s] || 'info'
}

function onSupplierChange(id) {
  const s = suppliers.value.find(x => x.supplierId === id)
  if (s) {
    form.supplierName = s.supplierName
    form.supplierCode = s.supplierCode || ''
    form.contactPerson = s.contactPerson || s.linkman || ''
    form.contactPhone = s.contactPhone || s.phone || ''
    form.deliveryAddress = s.deliveryAddress || s.address || ''
    if (s.taxNo) form.supplierTaxNo = s.taxNo
    if (s.paymentMethod) form.paymentMethod = s.paymentMethod
    // 供应商默认税率带入明细行（2026-11-08：数值化比较，兼容输入框回传的字符串税率）
    const supTaxRate = Number(s.taxRate)
    if (supTaxRate > 0) {
      form.items.forEach(row => {
        const rowRate = Number(row.taxRate)
        if (!rowRate || rowRate === 13) {
          row.taxRate = supTaxRate
          calcRow(row)
        }
      })
    }
    if (s.paymentTermId) {
      form.paymentTermId = s.paymentTermId
      if (typeof onPaymentTermChange === 'function') onPaymentTermChange(s.paymentTermId)
    }
    if (s.deliveryDays || s.leadTimeDays) {
      const days = s.deliveryDays || s.leadTimeDays || 7
      form.expectedDeliveryDate = new Date(Date.now() + days * 86400000).toISOString().slice(0, 10)
    }
    calcTotal()
  }
}

async function onMaterialChange(row, id) {
  const m = materials.value.find(x => x.materialId === id)
  if (m) {
    row.materialCode = m.materialCode
    row.materialName = m.materialName
    row.materialMainImage = m.mainImage || ''
    row.model = m.model
    // Material 实体没有 unitName 字段，根据 unitId 从计量单位列表查找单位名称
    const unitId = m.purchaseUnitId || m.baseUnitId || null
    row.unitId = unitId
    if (unitId && units.value.length > 0) {
      const u = units.value.find(x => x.unitId === unitId)
      row.unit = u ? u.unitName : ''
    } else {
      row.unit = ''
    }
    // 2026-09-20 价格策略修复：单价按设置中心「取价优先级」取价（价格策略 → 物料采购价）。
    // baseUnitPrice 仍取物料档案采购价。
    applyFetchedUnitPrice(row, {
      side: 'purchase',
      partnerId: form.supplierId,
      materialId: m.materialId,
      quantity: row.quantity
    }, m.purchasePrice || 0, (r) => calcRow(r))
    row.avgCost = m.avgCost || 0
    row.taxRate = m.taxRate || 13
    if (!row.taxCalcMethod) row.taxCalcMethod = '价外税'
    row.discountRate = 0
    row.discountAmount = 0
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
    calcRow(row)
  }
}

const onProductionDateChange = (row, date) => {
  if (row._expiryDays && date) {
    const d = new Date(date)
    d.setDate(d.getDate() + Number(row._expiryDays))
    row.expiryDate = d.toISOString().slice(0, 10)
  }
}

function calcRow(row) {
  const qty = Number(row.quantity || 0)
  const price = Number(row.unitPrice || 0)
  const taxRate = Number(row.taxRate || 0)
  const discountRate = Number(row.discountRate || 0)
  // 行级「计税方式」决定「单价」是含税价还是不含税价 → 换算另一列；含税业务关闭时一律按不计税
  const raw = row.taxCalcMethod
  const method = !taxEnabled.value ? '不计税'
    : (raw === '含税价' || raw === '价内税' ? '含税价' : (raw === '不计税' ? '不计税' : '不含税价'))
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
  // 金额模式（设置中心「发票录入口径」=按发票金额）：金额说了算，数量/单价只读并由金额反算
  if (invoiceAmountMode.value) {
    if (inclusive) {
      const netV = Number(row.netAmount || 0)
      row.netAmount = netV.toFixed(2)
      row.amount = (netV / factor).toFixed(2)
      row.taxAmount = (netV - Number(row.amount)).toFixed(2)
    } else if (noTax) {
      const amtV = Number(row.amount || 0)
      row.amount = amtV.toFixed(2)
      row.taxAmount = '0.00'
      row.netAmount = amtV.toFixed(2)
    } else {
      const amtV = Number(row.amount || 0)
      row.amount = amtV.toFixed(2)
      row.taxAmount = (amtV * taxRate / 100).toFixed(2)
      row.netAmount = (amtV + Number(row.taxAmount)).toFixed(2)
    }
    const originalAmount = Number(row.amount) + Number(row.discountAmount || 0)
    if (qty > 0) {
      const up = originalAmount / qty
      row.unitPrice = Number(up.toFixed(6))
      if (inclusive) {
        row.priceWithTax = Number(up.toFixed(6))
        row.priceWithoutTax = Number((up / factor).toFixed(6))
      } else if (noTax) {
        row.priceWithTax = Number(up.toFixed(6))
        row.priceWithoutTax = Number(up.toFixed(6))
      } else {
        row.priceWithoutTax = Number(up.toFixed(6))
        row.priceWithTax = Number((up * factor).toFixed(6))
      }
    }
    calcTotal()
    return
  }
  // 折前金额一律按「不含税」口径参与折扣与税额计算
  const originalAmount = inclusive ? (price * qty) / factor : price * qty
  row.discountAmount = (originalAmount * discountRate / 100).toFixed(2)
  row.amount = (originalAmount - Number(row.discountAmount)).toFixed(2)
  // 税额：不计税恒 0；含税价按「折后含税合计 − 折后不含税金额」倒算（统一口径，见 utils/billTax）
  row.taxAmount = calcTaxAmount({ amount: row.amount, unitPrice: price, quantity: qty, taxRate, discountRate, inclusive, noTax }).toFixed(2)
  row.netAmount = (Number(row.amount) + Number(row.taxAmount)).toFixed(2)
  calcTotal()
}

function onRowPriceChange(row, prop) {
  calcRow(row)
  if (prop === 'quantity') scheduleRowReprice(row)
}

const _repriceTimers = new Map()
function scheduleRowReprice(row) {
  if (!row || !row.materialId) return
  const key = row.itemId || row.inboundItemId || row.invoiceItemId || row.contractItemId || row.returnItemId || row.materialId
  if (_repriceTimers.has(key)) clearTimeout(_repriceTimers.get(key))
  _repriceTimers.set(key, setTimeout(async () => {
    _repriceTimers.delete(key)
    if (!row.materialId) return
    if (row._autoUnitPrice !== undefined && Number(row.unitPrice) !== Number(row._autoUnitPrice)) return
    const priced = await resolveUnitPrice({ side: 'purchase', partnerId: form.supplierId, materialId: row.materialId, quantity: row.quantity })
    if (!priced) return
    row.unitPrice = priced.price
    row._autoUnitPrice = priced.price
    calcRow(row)
  }, 400))
}

function onCurrencyChange(id) {
  const c = currencies.value.find(x => x.currencyId === id)
  if (c) {
    form.currencyCode = c.currencyCode
    if (c.exchangeRate) form.exchangeRate = c.exchangeRate
    calcTotal()
  }
}

function calcTotal() {
  let totalAmount = 0
  let discountAmount = 0
  let taxAmount = 0
  form.items.forEach(i => {
    const amt = Number(i.amount || 0)
    const disc = Number(i.discountAmount || 0)
    totalAmount += amt + disc
    discountAmount += disc
    taxAmount += Number(i.taxAmount || 0)
  })
  form.totalAmount = totalAmount.toFixed(2)
  form.discountAmount = discountAmount.toFixed(2)
  form.amount = (totalAmount - discountAmount).toFixed(2)
  form.taxAmount = taxAmount.toFixed(2)
  form.taxTotalAmount = (Number(form.amount) + taxAmount).toFixed(2)
  // 本位币金额换算：原币金额 × 汇率
  const rate = Number(form.exchangeRate || 1)
  form.baseTotalAmount = (Number(form.totalAmount || 0) * rate).toFixed(2)
  form.baseTaxAmount = (Number(form.taxAmount || 0) * rate).toFixed(2)
  form.baseNetAmount = (Number(form.taxTotalAmount || 0) * rate).toFixed(2)
}

function addItem() {
  form.items.push({
    materialId: null, materialCode: '', materialName: '', model: '', unitId: null, unit: '',
    materialMainImage: '',
    quantity: 1, unitPrice: 0, priceWithTax: 0, priceWithoutTax: 0, baseUnitPrice: 0, avgCost: 0,
    taxRate: 13, taxCalcMethod: form.taxCalcMethod === '含税价' ? '价内税' : (form.taxCalcMethod === '不计税' ? '不计税' : '价外税'),
    discountRate: 0, discountAmount: 0,
    amount: 0, taxAmount: 0, netAmount: 0,
    taxCategory: '',
    sourceType: form.sourceType, sourceBillNo: '', sourceRowNo: '',
    _isLotControl: false, _isExpiryControl: false, _expiryDays: 0,
    productionDate: '', expiryDate: '', batchNo: ''
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
      materialMainImage: m.mainImage || '',
      model: m.model || '',
      unitId: m.purchaseUnitId || m.baseUnitId || null,
      unit: '',
      quantity: 1,
      unitPrice: Number(m.purchasePrice || 0),
      priceWithTax: 0,
      priceWithoutTax: 0,
      baseUnitPrice: Number(m.purchasePrice || 0),
      avgCost: m.avgCost || 0,
      taxRate: Number(m.taxRate || 13),
      taxCalcMethod: form.taxCalcMethod === '含税价' ? '价内税' : (form.taxCalcMethod === '不计税' ? '不计税' : '价外税'),
      discountRate: 0,
      discountAmount: 0,
      amount: Number(m.purchasePrice || 0),
      taxAmount: 0,
      netAmount: 0,
      taxCategory: '',
      sourceType: form.sourceType,
      sourceBillNo: '',
      sourceRowNo: '',
      _isLotControl: m.isLotControl || false,
      _isExpiryControl: m.isExpiryControl || false,
      _expiryDays: m.expiryDays || 0,
      productionDate: '',
      expiryDate: '',
      batchNo: ''
    }
    if (row.unitId) {
      const u = units.value.find(x => x.unitId === row.unitId)
      row.unit = u ? u.unitName : ''
    }
    if (row._isExpiryControl && row._expiryDays) {
      row.productionDate = new Date().toISOString().slice(0, 10)
      const d = new Date(row.productionDate)
      d.setDate(d.getDate() + Number(row._expiryDays))
      row.expiryDate = d.toISOString().slice(0, 10)
    }
    if (itemCustomFields.value.length > 0) {
      injectMaterialCustomFields(row, m.materialId, materialCustomValueMap.value, itemCustomFields.value)
    }
    calcRow(row)
    form.items.push(row)
    applyFetchedUnitPrice(row, { side: 'purchase', partnerId: form.supplierId, materialId: m.materialId, quantity: row.quantity }, m.purchasePrice || 0, (r) => calcRow(r))
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
  // 宽松草稿保存：不校验必填，允许空供应商、空物料、无明细（用户可以后续补全再提交）
  // 保存宽松：丢弃未选物料的空行（后端这些字段 NOT NULL，无法落库）
  form.items = (form.items || []).filter(r => r && r.materialId)
  // 按行级「计税方式」把明细含税价/不含税价/金额/税额/价税合计刷成最新
  form.items.forEach(row => calcRow(row))
  calcTotal()
  try {
    if (form.invoiceId) {
      await updatePurchaseInvoice(form.invoiceId, form)
    } else {
      const res = await savePurchaseInvoice(form)
      if (res && res.invoiceId) form.invoiceId = res.invoiceId
      if (res && res.invoiceNo) form.invoiceNo = res.invoiceNo
      if (res && res.status) form.status = res.status
    
      if (!opts.skipRouteSync && !route.params.id && form.invoiceId) {
        try { await router.replace({ name: 'PurchaseInvoiceEdit', params: { id: String(form.invoiceId) } }) } catch (_) {}
      }
    }
    // 保存表头自定义字段值
    if (headerCustomFieldMap.size > 0 && form.invoiceId) {
      const headerValues = {}
      for (const key of headerCustomFieldMap.keys()) {
        if (key in form) headerValues[key] = form[key]
      }
      await saveCustomFieldValues('purchase_invoice', form.invoiceId, headerValues, headerFormulaFields.value)
    }
    // 保存明细自定义字段值
    if (itemCustomFields.value.length > 0 && form.items && form.items.length > 0) {
      // 关键修复（2026-09-22，同销售订单 bug）：保存主表时后端"明细先删后插"，明细主键会变 →
      // 必须先按保存后的新主键重新对齐，否则明细级自定义字段会写到已删除的旧行上（报表永远空白）。
      try {
        const fresh = await getPurchaseInvoice(form.invoiceId)
        remapItemIdsAfterSave(form.items, (fresh && fresh.items) || [], 'invoiceItemId')
      } catch (e) {
        console.warn('[purchaseInvoice] 保存后刷新明细主键失败，明细级自定义字段可能挂到旧行', e)
      }
      const itemKeys = new Set(itemCustomFields.value.map(f => f.fieldKey))
      for (let i = 0; i < form.items.length; i++) {
        const row = form.items[i]
        if (!row.invoiceItemId && !row.itemId) continue
        const itemId = row.invoiceItemId || row.itemId
        const values = {}
        for (const key of itemKeys) { if (key in row) values[key] = row[key] }
        await saveCustomFieldValues('purchase_invoice_item', itemId, values)
      }
    }
    triggerListRefresh('/purchase/invoices')
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
  for (let i = 0; i < form.items.length; i++) {
    if (!form.items[i].materialId) {
      ElMessage.warning(`${tt("第 ")}${i + 1}${tt(" 行未选择物料")}`)
      return
    }
  }
  // 无论新建还是已有草稿，提交前一律先静默保存（把当前页面所见落库，避免提交旧数据）
  const saved = await onSave(true, { skipRouteSync: true })
  if (!saved || !form.invoiceId) return
  try {
    const needApproval = await confirmSubmit({ billType: 'PURCHASE_INVOICE', billLabel: '采购发票' })
    await submitPurchaseInvoice(form.invoiceId)
    ElMessage.success(submitSuccessMessage(needApproval))
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('提交失败'))
  } finally {
    // 提交（或用户取消）后再把新单 id 写回地址栏：此时重建实例无副作用，新实例挂载时会重新拉取最新状态
    if (!route.params.id && form.invoiceId) {
      try { await router.replace({ name: 'PurchaseInvoiceEdit', params: { id: String(form.invoiceId) } }) } catch (_) {}
    }
  }
}

async function confirmApprove() {
  if (!form.invoiceId) {
    ElMessage.warning(tt('请先保存发票'))
    return
  }
  try {
    await ElMessageBox.confirm(tt('确认审核该发票? 审核后不可修改'), tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await approvePurchaseInvoice(form.invoiceId)
    ElMessage.success(tt('审核成功'))
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('审核失败'))
  }
}

async function confirmReverseApprove() {
  if (!form.invoiceId) {
    ElMessage.warning(tt('请先保存发票'))
    return
  }
  try {
    await ElMessageBox.confirm(tt('确认重置草稿该发票?'), tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await reverseApprovePurchaseInvoice(form.invoiceId)
    ElMessage.success(tt('重置草稿成功'))
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('重置草稿失败'))
  }
}

async function doCancel() {
  if (!form.invoiceId) {
    ElMessage.warning(tt('请先保存发票'))
    return
  }
  if (!cancelReason.value || !cancelReason.value.trim()) {
    ElMessage.warning(tt('请输入取消原因'))
    return
  }
  try {
    await cancelPurchaseInvoice(form.invoiceId, cancelReason.value)
    ElMessage.success(tt('取消成功'))
    cancelDialog.value = false
    cancelReason.value = ''
    await load()
  } catch (e) {
    ElMessage.error(e.message || tt('取消失败'))
  }
}

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
  '打样单': '/sample/orders'
}

function goToSourceBill(row) {
  if (!row || !row.sourceBillNo) return
  const billType = row.sourceBillType || row.sourceType
  const routeBase = SOURCE_BILL_ROUTE_MAP[billType]
  if (routeBase) {
    router.push(`${routeBase}?keyword=${encodeURIComponent(row.sourceBillNo)}`)
  } else {
    ElMessage.warning(`${tt("暂不支持跳转到 ")}${billType || tt('该来源单据')}`)
  }
}

function onSourcePicked(sourceData, items) {
  if (sourceData) {
    Object.keys(sourceData).forEach(key => {
      if (key in form && key !== 'items' && key !== 'invoiceId' && key !== 'invoiceNo') {
        form[key] = sourceData[key]
      }
    })
    if (headerCustomFieldMap.size > 0) {
      copyCustomFieldValues(sourceData, form, headerCustomFieldMap)
    }
  }
  if (items && items.length > 0) {
    // 2026-11-23：选单带入追加到现有明细末尾，不再覆盖原有明细
    const newRows = items.map(item => {
      const row = { ...item }
      delete row.invoiceItemId
      delete row.itemId
      calcRow(row)
      return row
    })
    form.items = [...(form.items || []), ...newRows]
  }
  calcTotal()
  ElMessage.success(tt('已从源单带入数据'))
}
function handlePrint() {
  if (!form.invoiceId) {
    ElMessage.warning(tt('请先保存发票再打印'))
    return
  }
  printVisible.value = true
}

async function load() {
  const __detailId = route.params.id || form.invoiceId || ''
  if (__detailId) {
    try {
      const res = await getPurchaseInvoice(__detailId)
      Object.assign(form, res)
      if (!form.items) form.items = []
      if (itemCustomFields.value.length > 0 && res.items && res.items.length > 0) {
        const itemIds = res.items.map(it => it.itemId || it.invoiceItemId).filter(Boolean)
        if (itemIds.length > 0) {
          const itemValuesMap = await batchLoadCustomFieldValues('purchase_invoice_item', itemIds)
          form.items.forEach(row => {
            const itemId = row.itemId || row.invoiceItemId
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
      // 后端明细只有 unitId，回填单位名称用于展示
      form.items.forEach(row => {
        if (row.unitId && !row.unit && units.value.length > 0) {
          const u = units.value.find(x => x.unitId === row.unitId)
          row.unit = u ? u.unitName : ''
        }
        if (!row.taxCalcMethod) row.taxCalcMethod = '价外税'
      })
      // 加载表头自定义字段值
      if (headerCustomFieldMap.size > 0 && form.invoiceId) {
        const headerValues = await loadCustomFieldValues('purchase_invoice', form.invoiceId)
        if (headerValues) {
          Object.keys(headerValues).forEach(k => {
            if (headerCustomFieldMap.has(k)) form[k] = headerValues[k]
          })
        }
      }
      // 加载明细自定义字段值
      if (itemCustomFields.value.length > 0 && form.items.length > 0) {
        const itemIds = form.items.map(r => r.invoiceItemId || r.itemId).filter(id => !!id)
        if (itemIds.length > 0) {
          const itemMap = await batchLoadCustomFieldValues('purchase_invoice_item', itemIds)
          if (itemMap) {
            form.items.forEach(row => {
              const itemId = row.invoiceItemId || row.itemId
              const v = itemMap[itemId]
              if (v) {
                Object.keys(v).forEach(k => {
                  if (itemCustomFields.value.some(f => f.fieldKey === k)) row[k] = v[k]
                })
              }
            })
          }
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
  getId: () => form.invoiceId,
  api: {
    approve: approvePurchaseInvoice,
    reject: rejectPurchaseInvoice,
    reverseApprove: reverseApprovePurchaseInvoice,
    close: closeOrderPurchaseInvoice,
    reopen: reopenPurchaseInvoice,
    void: voidPurchaseInvoice,
    unvoid: unvoidPurchaseInvoice,
    delete: deletePurchaseInvoice
  },
  reload: load,
  router,
  newPath: '/purchase/invoices/new',
  listPath: '/purchase/invoices',
  billLabel: '采购发票'
})

// 加载下拉基础数据——独立函数，供 onMounted 和切回标签页时复用
  async function loadBaseData() {
  try {
      const [matRes, supRes, unitRes, curRes] = await Promise.all([
        listAllMaterial(),
        listAllSupplier(),
        listAllUnit(),
        listAllCurrency()
      ])
      materials.value = matRes || []
      // 行内下拉附加字段：物料到位后按批预取（不阻塞渲染）
      mcf.preloadAll(materials.value)
      suppliers.value = supRes || []
      units.value = unitRes || []
      currencies.value = curRes || []
      if (!form.currencyId && currencies.value.length > 0) {
        // 外币业务关闭且为新增单据：币种默认本位币、汇率锁定为 1（编辑单据保留原币，仅供查看）
        if (!foreignCurrencyEnabled.value && !route.params.id && !form.invoiceId && !form.id) {
          const list = currencies.value || []
          const base = list.find(c => c.isBaseCurrency === 1 || c.isBaseCurrency === true) || list[0]
          if (base) {
            form.currencyId = base.currencyId
            form.currencyCode = base.currencyCode
            form.exchangeRate = 1
          }
        } else {
          form.currencyId = currencies.value[0].currencyId
          onCurrencyChange(form.currencyId)
        }
      }
    } catch (e) {
      console.warn('下拉数据加载失败:', e)
    }
}

// ===== 复制功能（URL 带 ?copy=id 时自动带入源单数据）=====
const { applyCopy } = useCopyBill({
  route,
  loadFn: getPurchaseInvoice,
  form,
  idField: 'invoiceId',
  noField: 'invoiceNo',
  itemIdField: 'invoiceItemId',
  itemsField: 'items',
  afterCopy: () => calcTotal(),
  customField: { entity: 'purchase_invoice', itemEntity: 'purchase_invoice_item', itemIdField: 'invoiceItemId' }
})

// ===== 顶级页签：单据 / 来源单据 / 去向单据 =====
const topTab = ref('doc')
const billLinkRegistered = ref(false)

const { isFieldRequired } = useFieldPermission('purchase_invoice')
useFieldRequiredMark('purchase_invoice', '.purchase-invoice-edit')

onMounted(async () => {
  // 全站商业口径总开关：取设置中心「启用含税业务 / 启用外币业务」最新值
  await ensureCommercialSwitches()
  // 设置中心参数（含「发票录入口径」与默认计税方式）：明细列可编辑性与金额反算依赖它；读取失败按按数量单价
  try { await loadSysConfig() } catch (e) { /* 忽略，走默认口径 */ }
  // 物料自定义字段：定制开关 + 字段定义（失败静默降级）
  await mcf.init()
  // 该单据类型是否登记了「来源单据/去向单据」配置（未登记则不显示这两个页签）
  isBillLinkRegistered('PURCHASE_INVOICE')
    .then(v => { billLinkRegistered.value = !!v })
    .catch(() => { billLinkRegistered.value = false })

  loadCollapsed()
  await loadCustomFields()
  await loadBaseData()
  const copied = await applyCopy()
  if (!copied) {
    await load()
    const applied = await applyPushSource(route, 'PurchaseInvoice', form, (items) => {
      form.items = items
      form.items.forEach(row => calcRow(row))
    })
    if (applied) ElMessage.info(tt('已从源单带入数据，请确认后保存'))
  }
  if (!isEdit.value) {
    autoFillDocCodeOnCreate(form, 'invoiceNo')
  }
})

// 切回标签页时自动刷新下拉基础数据（只刷下拉，不动表单草稿），避免档案改过后这里还是旧数据
useBaseDataRefresh(loadBaseData)


  </script>

<style scoped lang="scss">
.purchase-invoice-edit {
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

  .source-hint {
    margin-left: auto;
    color: #c0c4cc;
    font-size: 11px;
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
  min-width: 2080px;
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
.discount-text { color: #e6a23c; }
.net-amount { color: #409eff; font-weight: 600; }

// ===== 金额汇总区（放在明细下面） =====
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
      &.discount { color: #e6a23c; }
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
  .purchase-invoice-edit { padding: 8px; }
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
