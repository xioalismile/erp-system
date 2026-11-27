<template>
  <div class="sale-pre-receipt-edit">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div class="header-left">
        <div class="back-btn" @click="$router.push('/finance/sale-pre-receipts')">
          <el-icon><ArrowLeft /></el-icon>
          <span>{{ $tt("返回列表") }}</span>
        </div>
        <div class="title-area">
          <div class="title-row">
            <el-icon class="title-icon"><Document /></el-icon>
            <span class="page-title">{{ isEdit ? '销售预收款单' : '新增销售预收款单' }}</span>
            <span v-if="form.receiptNo" class="order-no">
              <el-icon><Collection /></el-icon>
              YSK{{ form.receiptNo }}
            </span>
            <el-tag size="small" :type="statusType(form.status)" effect="plain" class="status-tag">{{ $dt(statusLabel(form.status)) }}</el-tag>
          </div>
        </div>
      </div>
      <div class="header-right">
        <el-button v-permission="perm('view')" v-if="!(!form.orderId && !form.deliveryId && !form.inboundId && !form.invoiceId && !form.voucherId && !form.contractId && !form.requisitionId && !form.stockCheckId && !form.transferId && !form.bomId && !form.sampleId && !form.freeSampleId && !form.workOrderId && !form.billId && !form.id)" @click="changeRecordVisible = true">
          <el-icon><Memo /></el-icon>{{ $tt('操作日志') }}
        </el-button>
        <BillActions bill-type="SALE_PRE_RECEIPT"
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
              <el-form-item :label="$tf('单号')">
                <el-input v-model="form.receiptNo" :placeholder="docCodePlaceholder" :disabled="!isDraft" class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('收款日期')" prop="receiptDate" required>
                <el-date-picker v-model="form.receiptDate" type="date" value-format="YYYY-MM-DD" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('关联订单')">
                <el-input v-model="form.orderNo" :placeholder="$tt('选择关联销售订单')" readonly class="field-input" :disabled="!isDraft" >
                  <template #append>
                    <el-button v-permission="perm('pick')" :icon="Search" @click="orderPickerVisible = true" :disabled="!isDraft" />
                  </template>
                </el-input>
              </el-form-item>
            </div>
          </div>

          <!-- 客户信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>客户信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('客户')" prop="customerId" required>
                <el-input v-model="form.customerName" :placeholder="$tt('选择客户')" readonly class="field-input" :disabled="!isDraft">
                  <template #append>
                    <el-button v-permission="perm('pick')" :icon="Search" @click="customerPickerVisible = true" :disabled="!isDraft" />
                  </template>
                </el-input>
              </el-form-item>
              <el-form-item :label="$tf('付款方式')">
                <el-input v-model="form.paymentMethod" :placeholder="$tt('如：现金/银行转账')" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('收款人')">
                <el-select v-model="form.cashierId" filterable :placeholder="$tt('选择收款人')" class="field-input" :disabled="!isDraft" @change="onCashierChange">
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
              <el-form-item :label="$tf('币别')">
                <el-select v-model="form.currencyCode" :placeholder="$tt('选择币别')" class="field-input" :disabled="!isDraft || !foreignCurrencyEnabled" @change="onCurrencyChange">
                  <el-option v-for="c in currencies" :key="c.currencyCode" :label="c.currencyCode" :value="c.currencyCode" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('汇率')">
                <el-input-number v-model="form.exchangeRate" :precision="6" :step="0.1" :min="0" class="field-input" :disabled="!isDraft || !foreignCurrencyEnabled" />
              </el-form-item>
              <el-form-item :label="$tf('收款金额')" prop="amount" required>
                <el-input-number v-model="form.amount" :precision="2" :step="1" :min="0" class="field-input" :disabled="!isDraft" @change="calcAmount" />
              </el-form-item>
              <el-form-item :label="$tf('本位币金额')">
                <el-input v-model="baseAmountDisplay" readonly class="field-input" />
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
                <el-input v-model="form.remark" type="textarea" :rows="2" class="field-input" :disabled="!isDraft" :placeholder="$tt('请输入备注信息')" />
              </el-form-item>
            </div>
          </div>
        </el-form>
      </div>
    </div>

    <!-- 核销明细卡片（通用组件 BillItemsTable：卡片/列设置/动态列/汇总 全站共用一套） -->
    <BillItemsTable
      v-model:collapsed="collapsed.items"
      :items="form.items"
      page-key="SalePreReceiptEdit_items"
      title="核销明细"
      subtitle="核销明细"
      col-setting-title="核销明细列设置"
      :columns="detailColumns"
      :custom-columns="[]"
      :readonly="!isDraft"
      :show-total-qty="false"
      :summary-items="summaryItems"
      @row-change="calcAmount"
    >
      <template #actions>
        <el-button v-permission="perm('addRow')" size="small" type="primary" @click="addItem" :disabled="!isDraft">
          <el-icon><Plus /></el-icon>添加明细
        </el-button>
      </template>
      <template #cell-accountName="{ row }">
        <el-select v-model="row.accountId" size="small" filterable clearable :placeholder="$tt('选择银行账户')" :disabled="!isDraft" style="width:100%" @change="(v) => onPickDetailAccount(row, v)">
          <el-option v-for="a in accountOptions" :key="a.accountId" :label="a.accountName" :value="a.accountId" />
        </el-select>
      </template>
      <template #cell-bankAccount="{ row }">
        <el-input v-model="row.accountNo" size="small" readonly :placeholder="$tt('选中账户自动带出')" />
      </template>
      <template #summary-extra>
        <!-- 本位币折算（非人民币单据才显示） -->
        <div v-if="form.currencyCode && form.currencyCode !== 'CNY'" class="summary-block">
          <div class="block-title">{{ $tt("本位币折算") }}</div>
          <div class="sum-line">
            <span class="label">{{ $tt("本位币金额：") }}</span>
            <span class="value">¥{{ Number(form.baseAmount || 0).toFixed(2) }}</span>
          </div>
        </div>
      </template>
    </BillItemsTable>

    <!-- 自定义字段卡片（设置中心-自定义字段 sale_pre_receipt 实体） -->
    <div class="collapsible-card" v-for="section in headerCustomSections" :key="section.title" v-show="headerCustomSections.length > 0">
      <div class="card-header">
        <div class="header-left">
          <el-icon class="collapse-icon active"><ArrowRight /></el-icon>
          <span class="card-title">{{ section.title }}</span>
        </div>
      </div>
      <div class="card-body">
        <el-form label-width="110px" class="custom-field-form">
          <el-row :gutter="16">
            <el-col :span="8" v-for="field in section.fields" :key="field.prop">
              <el-form-item :label="$tf(field.label)" :required="field.required || isFieldRequired(field.prop)">
                <el-input v-if="field.type === 'input'" v-model="form[field.prop]" :placeholder="field.placeholder || '请输入'" :disabled="!isDraft" />
                <el-input-number v-else-if="field.type === 'number'" v-model="form[field.prop]" :precision="field.precision" :min="0" :step="0.1" controls-position="right" style="width:100%" :disabled="!isDraft" />
                <el-input v-else-if="field.type === 'textarea'" v-model="form[field.prop]" type="textarea" :rows="2" :placeholder="field.placeholder || '请输入'" :disabled="!isDraft" />
                <el-select v-else-if="field.type === 'select'" v-model="form[field.prop]" :placeholder="$tt('请选择')" style="width:100%" :disabled="!isDraft">
                  <el-option v-for="opt in (field.options || [])" :key="opt.value" :label="$tf(opt.label)" :value="opt.value" />
                </el-select>
                <el-date-picker v-else-if="field.type === 'date'" v-model="form[field.prop]" type="date" value-format="YYYY-MM-DD" :placeholder="$tt('选择日期')" style="width:100%" :disabled="!isDraft" />
                <el-input v-else v-model="form[field.prop]" :placeholder="field.placeholder || '请输入'" :disabled="!isDraft" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
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
          type="sale-pre-receipt"
          :disabled="!isDraft"
          button-text="上传附件"
          tip-text="支持图片、PDF、Word、Excel 等文件"
        />
      </div>
    </div>

    <!-- 审核流转（真实审批进度，通用组件） -->
    <ApprovalFlowTimeline bill-type="SALE_PRE_RECEIPT" :bill-id="form.id" :status="form.status" />

    <!-- 客户选择弹窗 -->
    <el-dialog v-model="customerPickerVisible" :title="$tf('选择客户')" width="700px">
      <el-input v-model="customerKeyword" :placeholder="$tt('输入客户名称搜索')" clearable style="margin-bottom:10px;">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-table :data="filteredCustomers" height="380" border @row-dblclick="pickCustomer">
        <el-table-column prop="customerCode" :label="$tf('客户编号')" width="140" />
        <el-table-column prop="customerName" :label="$tf('客户名称')" min-width="200" />
      </el-table>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="customerPickerVisible = false">{{ $tt("取消") }}</el-button>
      </template>
    </el-dialog>

    <!-- 订单选择弹窗（占位：使用客户订单接口，若无则手动输入） -->
    <el-dialog v-model="orderPickerVisible" :title="$tf('选择销售订单')" width="700px" @open="searchOrders">
      <div class="muted-tip">{{ $tt("提示：从已生效的销售订单中选择关联订单") }}</div>
      <el-input v-model="orderKeyword" :placeholder="$tt('输入订单号搜索')" clearable style="margin:10px 0;" @keyup.enter="searchOrders">
        <template #prefix><el-icon><Search /></el-icon></template>
        <template #append><el-button v-permission="perm('query')" :icon="Search" @click="searchOrders" /></template>
      </el-input>
      <el-table :data="orderOptions" height="380" border @row-dblclick="pickOrder">
        <el-table-column prop="orderNo" :label="$tf('订单号')" width="180" />
        <el-table-column prop="customerName" :label="$tf('客户')" min-width="180" />
        <el-table-column prop="netAmount" :label="$tf('金额')" width="120" align="right">
          <template #default="{ row }">¥{{ Number(row.netAmount || 0).toFixed(2) }}</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="orderPickerVisible = false">{{ $tt("取消") }}</el-button>
      </template>
    </el-dialog>

    <!-- 核销弹窗 -->
    <el-dialog v-model="writeOffDialog" :title="$tf('核销预收款单')" width="700px" :close-on-click-modal="false">
      <div class="write-off-info">
        <div class="info-line"><span class="label">{{ $tt("单号：") }}</span>YSK{{ form.receiptNo }}</div>
        <div class="info-line"><span class="label">{{ $tt("客户：") }}</span>{{ form.customerName }}</div>
        <div class="info-line"><span class="label">{{ $tt("未核销金额：") }}</span><span class="remain-amount">¥{{ Number(form.remainAmount || 0).toFixed(2) }}</span></div>
      </div>
      <el-table :data="writeOffItems" border size="small">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column :label="$tf('来源单号')" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.sourceNo" :placeholder="$tt('来源单号')" size="small" />
          </template>
        </el-table-column>
        <el-table-column :label="$tf('来源类型')" width="140">
          <template #default="{ row }">
            <el-select v-model="row.sourceType" :placeholder="$tt('选择类型')" size="small" style="width:100%">
              <el-option :label="$tf('销售订单')" value="sale_order" />
              <el-option :label="$tf('销售出库单')" value="sale_delivery" />
              <el-option :label="$tf('销售发票')" value="sale_invoice" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column :label="$tf('核销金额')" width="140">
          <template #default="{ row }">
            <el-input-number v-model="row.amount" :min="0" :precision="2" size="small" style="width:100%" />
          </template>
        </el-table-column>
        <el-table-column :label="$tf('操作')" width="80" align="center">
          <template #default="{ $index }">
            <el-button v-permission="perm('deleteRow')" text type="danger" size="small" @click="writeOffItems.splice($index, 1)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button v-permission="perm('addRow')" size="small" type="primary" plain style="margin-top:8px;" @click="writeOffItems.push({ sourceId: null, sourceNo: '', sourceType: 'sale_order', amount: 0 })">
        <el-icon><Plus /></el-icon>添加核销行
      </el-button>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="writeOffDialog = false">{{ $tt("取消") }}</el-button>
        <el-button v-permission="perm('dlgConfirm')" type="primary" @click="confirmWriteOff">{{ $tt("确认核销") }}</el-button>
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
    <PrintPreviewDialog v-model="printVisible" bill-type="SALE_PRE_RECEIPT" :bill-id="form.id" />

    <!-- 操作日志弹窗 -->
    <ChangeRecordDialog v-model="changeRecordVisible" bill-type="销售预收款" :bill-no="form.receiptNo || ''" />
      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('来源单据')" name="source" :disabled="!form.id">
        <BillLinkPanel v-if="topTab === 'source'" bill-type="SALE_PRE_RECEIPT" :bill-id="form.id" mode="source" />
      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('去向单据')" name="target" :disabled="!form.id">
        <BillLinkPanel v-if="topTab === 'target'" bill-type="SALE_PRE_RECEIPT" :bill-id="form.id" mode="target" />
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
import { ArrowLeft, Document, Collection, Plus, Search, Delete, Printer, ArrowRight, Check, TopRight, CircleCheck, CircleClose, Share, Clock, Memo } from '@element-plus/icons-vue'
import { getSalePreReceipt, saveSalePreReceipt, submitSalePreReceipt, auditSalePreReceipt, voidSalePreReceipt, writeOffSalePreReceipt, rejectSalePreReceipt, reverseApproveSalePreReceipt, unvoidSalePreReceipt, closeOrderSalePreReceipt, reopenSalePreReceipt, deleteSalePreReceipt } from '@/api/finance/salePreReceipt'
import { listAllCustomer } from '@/api/basedata/customer'
import { employeeSelectOptions } from '@/api/basedata/employee'
import { listAllBankAccount } from '@/api/basedata/bankAccount'
import { listAllCurrency } from '@/api/basedata/currency'
import { listSaleOrder } from '@/api/sale/saleOrder'
import FileUploader from '@/components/FileUploader.vue'
import ApprovalFlowTimeline from '@/components/bill/ApprovalFlowTimeline.vue'
import PrintPreviewDialog from '@/components/PrintPreviewDialog.vue'
import ChangeRecordDialog from '@/components/ChangeRecordDialog.vue'
// 明细通用组件（卡片折叠/列设置/动态列/汇总 全站共用一套）
import BillItemsTable from '@/components/bill/BillItemsTable.vue'
import { triggerListRefresh } from '@/composables/useAutoRefresh'
import { useAutoDocCode } from '@/composables/useAutoDocCode'
import { useBillActions } from '@/composables/useBillActions'
import { useCopyBill } from '@/composables/useCopyBill'
import { useCommercialSwitches } from '@/composables/useCommercialSwitches'

import * as BillStatus from '@/utils/bill-status'
import { confirmSubmit, submitSuccessMessage } from '@/utils/submit-confirm'
import BillActions from '@/components/bill/BillActions.vue'
import { validateBillRequiredFields } from '@/utils/billValidation'
import { useFieldPermission } from '@/composables/useFieldPermission'
import { useFieldRequiredMark } from '@/utils/fieldRequiredMark'
import {
  loadCustomFieldDefinitions,
  loadCustomFieldValues,
  saveCustomFieldValues
} from '@/composables/useCustomField'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('finance:sale-pre-receipts')

// ===== 表头级自定义字段（设置中心-自定义字段 sale_pre_receipt 实体）=====
const headerCustomSections = ref([])
const headerCustomFieldMap = new Map()
const headerFormulaFields = ref([])

async function loadHeaderCustomFields() {
  try {
    const result = await loadCustomFieldDefinitions('sale_pre_receipt')
    headerCustomSections.value = result.sections
    headerCustomFieldMap.clear()
    result.fieldMap.forEach((v, k) => headerCustomFieldMap.set(k, v))
    headerFormulaFields.value = result.formulaFields
    result.fieldMap.forEach((def, key) => {
      if (!(key in form)) form[key] = def.defaultValue || ''
    })
  } catch (e) {
    console.warn('[SalePreReceiptEdit] 加载自定义字段失败:', e)
  }
}

async function saveHeaderCustomFields() {
  if (headerCustomFieldMap.size > 0 && form.id) {
    const values = {}
    for (const key of headerCustomFieldMap.keys()) {
      if (key in form) values[key] = form[key]
    }
    await saveCustomFieldValues('sale_pre_receipt', form.id, values, headerFormulaFields.value)
  }
}

// 核销明细列定义（通用组件 BillItemsTable 消费）：editor = 单元格控件，type = 纯展示格式；
// 列声明顺序 = 改造前模板的渲染顺序（账户/银行账号/收款金额/退回金额/备注 静态列 + 收款本位币/已核金额/已核本位币/退回本位币 动态列）
// 账户（银行账户下拉）、银行账号（只读回显）由页面提供插槽（#cell-accountName / #cell-bankAccount）
const detailColumns = [
  { prop: 'accountName', label: '账户', minWidth: 140 },
  { prop: 'bankAccount', label: '银行账号', minWidth: 160 },
  { prop: 'amount', label: '收款金额', width: 120, align: 'right', editor: 'number', precision: 2, min: 0 },
  { prop: 'returnAmount', label: '退回金额', width: 110, align: 'right', editor: 'number', precision: 2, min: 0 },
  { prop: 'remark', label: '备注', minWidth: 140, editor: 'text' },
  { prop: 'baseAmount', label: '收款本位币', width: 120, align: 'right', type: 'amount2' },
  { prop: 'usedAmount', label: '已核金额', width: 110, align: 'right', type: 'amount2' },
  { prop: 'usedBaseAmount', label: '已核本位币', width: 120, align: 'right', type: 'amount2' },
  { prop: 'returnBaseAmount', label: '退回本位币', width: 120, align: 'right', type: 'amount2' }
]
// 汇总区（通用组件 BillItemsTable 按此渲染）：原页无「合计数量」行 → show-total-qty=false；
// 三行标签/口径与迁移前的汇总块逐项一致，本位币折算块走 #summary-extra 插槽
const summaryItems = computed(() => [
  { label: '收款金额：', value: `¥${Number(form.amount || 0).toFixed(2)}` },
  { label: '已核销：', value: `¥${Number(form.usedAmount || 0).toFixed(2)}` },
  { label: '未核销：', value: `¥${Number(form.remainAmount || 0).toFixed(2)}`, total: true }
])

const route = useRoute()
const router = useRouter()
const headerFormRef = ref()
const changeRecordVisible = ref(false)

const isEdit = computed(() => !!route.params.id)

// 单据编码自动生成
const { docCodeDisabled, docCodePlaceholder, autoFillDocCodeOnCreate } = useAutoDocCode('SALE_PRE_RECEIPT')

const STORAGE_KEY = 'sale-pre-receipt-edit-collapsed'

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

// 下拉数据
const customers = ref([])
const employees = ref([])
const currencies = ref([])
const accountOptions = ref([])
const orderOptions = ref([])

// 弹窗
const customerPickerVisible = ref(false)
const customerKeyword = ref('')
const orderPickerVisible = ref(false)
const orderKeyword = ref('')
const writeOffDialog = ref(false)
const writeOffItems = ref([])
const printVisible = ref(false)

// 表单
const form = reactive({
  id: null,
  receiptNo: '',
  customerId: null,
  customerName: '',
  cashierId: null,
  cashierName: '',
  orderId: null,
  orderNo: '',
  receiptDate: new Date().toISOString().slice(0, 10),
  amount: 0,
  baseAmount: 0,
  currencyCode: 'CNY',
  exchangeRate: 1,
  paymentMethod: '',
  accountId: null,
  accountName: '',
  status: 0,
  usedAmount: 0,
  remainAmount: 0,
  remark: '',
  attachmentUrls: '',
  createBy: '',
  createTime: '',
  items: []
})


const isDraft = computed(() => BillStatus.canEdit(form.status, form.closeStatus))

// 全站商业口径总开关（设置中心：启用外币业务）→ 关闭时单据只用本位币、汇率锁 1
const { foreignCurrencyEnabled, ensureLoaded: ensureCommercialSwitches } = useCommercialSwitches()
const rules = {
  receiptDate: [{ required: true, message: tt('请选择收款日期'), trigger: 'change' }],
  customerId: [{ required: true, message: tt('请选择客户'), trigger: 'change' }],
  amount: [{ required: true, message: tt('请输入收款金额'), trigger: 'change' }]
}

const filteredCustomers = computed(() => {
  if (!customerKeyword.value) return customers.value
  const kw = customerKeyword.value.toLowerCase()
  return customers.value.filter(c =>
    (c.customerName && c.customerName.toLowerCase().includes(kw)) ||
    (c.customerCode && c.customerCode.toLowerCase().includes(kw))
  )
})

const baseAmountDisplay = computed(() => Number(form.baseAmount || 0).toFixed(2))

// 状态映射
function statusLabel(s) {
  return { 0: '草稿', 1: '待审核', 2: '已生效', 3: '已核销', 7: '已作废' }[Number(s)] || '草稿'
}
function statusType(s) {
  return { 0: 'info', 1: 'warning', 2: 'success', 3: 'success', 7: 'danger' }[Number(s)] || 'info'
}

function onCashierChange(id) {
  const e = employees.value.find(x => x.employeeId === id)
  if (e) form.cashierName = e.employeeName
}

function onCurrencyChange() {
  const c = currencies.value.find(x => x.currencyCode === form.currencyCode)
  if (c && c.exchangeRate) form.exchangeRate = c.exchangeRate
  calcAmount()
}

function calcAmount() {
  // 收款本位币 = 收款金额 * 汇率
  form.baseAmount = Number((Number(form.amount || 0) * Number(form.exchangeRate || 1)).toFixed(2))
  // 明细行的本位币金额同步
  if (form.items && form.items.length > 0) {
    form.items.forEach(row => {
      row.baseAmount = Number((Number(row.amount || 0) * Number(form.exchangeRate || 1)).toFixed(2))
    })
  }
  // 未核销 = 收款金额 - 已核销
  form.remainAmount = Number((Number(form.amount || 0) - Number(form.usedAmount || 0)).toFixed(2))
}

function addItem() {
  form.items.push({
    accountId: null,
    accountName: '',
    accountNo: '',
    amount: 0,
    baseAmount: 0,
    usedAmount: 0,
    usedBaseAmount: 0,
    returnAmount: 0,
    returnBaseAmount: 0,
    remark: ''
  })
}

function pickCustomer(row) {
  form.customerId = row.customerId
  form.customerName = row.customerName
  customerPickerVisible.value = false
}

async function searchOrders() {
  try {
    const res = await listSaleOrder({ current: 1, size: 50, keyword: orderKeyword.value, status: '已生效' })
    orderOptions.value = res.records || []
  } catch (e) {
    // 占位：若加载失败保持空列表
    orderOptions.value = []
  }
}

function pickOrder(row) {
  form.orderId = row.orderId
  form.orderNo = row.orderNo
  // 自动带出客户
  if (!form.customerId && row.customerId) {
    form.customerId = row.customerId
    form.customerName = row.customerName
  }
  orderPickerVisible.value = false
}

function onPickDetailAccount(row, accountId) {
  const acc = accountOptions.value.find(a => a.accountId === accountId)
  if (acc) {
    row.accountId = acc.accountId
    row.accountName = acc.accountName
    row.accountNo = acc.accountNo
  } else {
    row.accountId = null
    row.accountName = ''
    row.accountNo = ''
  }
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
  // 宽松草稿保存：不校验必填，允许空客户、空日期（提交时才校验必填，与销售订单一致）
  calcAmount()
  try {
    const res = await saveSalePreReceipt(form)
    if (res && res.id) form.id = res.id
    if (res && res.receiptNo) form.receiptNo = res.receiptNo
    if (res && res.status !== undefined) form.status = res.status
    // 保存表头级自定义字段值
    await saveHeaderCustomFields().catch(() => {})
    triggerListRefresh('/finance/sale-prereceipts')
    if (!silent) ElMessage.success(tt('保存成功'))
    if (!opts.skipRouteSync && res && res.id && route.params.id !== String(res.id)) {
      router.replace(`/finance/sale-pre-receipts/edit/${res.id}`)
    }
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
  const missing = await validateBillRequiredFields({ headerFormRef, rules, $el: document, pageKey: 'sale_pre_receipt', model: form,
    // 2026-11-11：客户是“只读框 + 弹窗选择”，不在字段权限元数据里，缺中文名兜底会让提示显示英文 customerId
    labels: { customerId: tt('客户'), receiptDate: tt('收款日期'), amount: tt('收款金额') } })
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

  // 无论新建还是已有草稿，提交前一律先静默保存（把当前页面所见落库，避免提交旧数据）
  const saved = await onSave(true, { skipRouteSync: true })
  if (!saved || !form.id) return

  try {
    const needApproval = await confirmSubmit({ billType: 'SALE_PRE_RECEIPT', billLabel: '销售预收款单' })
    // 提交走独立 submit 接口：草稿 → 待审核(需审批) / 已生效(免审批)
    await submitSalePreReceipt(form.id)
    ElMessage.success(submitSuccessMessage(needApproval))
    triggerListRefresh('/finance/sale-prereceipts')
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('提交失败'))
  } finally {
    // 提交（或用户取消）后再把新单 id 写回地址栏：此时重建实例无副作用，新实例挂载时会重新拉取最新状态
    if (!route.params.id && form.id) {
      try { await router.replace(`/finance/sale-pre-receipts/edit/${form.id}`) } catch (_) {}
    }
  }
}

async function onAudit() {
  if (!form.id) { ElMessage.warning(tt('请先保存单据')); return }
  try {
    await ElMessageBox.confirm(tt('确认审核该单据？'), tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await auditSalePreReceipt(form.id)
    ElMessage.success(tt('审核成功'))
    triggerListRefresh('/finance/sale-prereceipts')
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('审核失败'))
  }
}

async function onVoid() {
  if (!form.id) { ElMessage.warning(tt('请先保存单据')); return }
  try {
    await ElMessageBox.confirm(tt('确认作废该单据？'), tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await voidSalePreReceipt(form.id)
    ElMessage.success(tt('作废成功'))
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('作废失败'))
  }
}

function openWriteOff() {
  if (!form.id) { ElMessage.warning(tt('请先保存单据')); return }
  writeOffItems.value = [{ sourceId: null, sourceNo: '', sourceType: 'sale_order', amount: Number(form.remainAmount || 0) }]
  writeOffDialog.value = true
}

async function confirmWriteOff() {
  const totalWriteOff = writeOffItems.value.reduce((s, i) => s + Number(i.amount || 0), 0)
  if (totalWriteOff > Number(form.remainAmount || 0)) {
    ElMessage.warning(tt('核销总额不能超过未核销金额'))
    return
  }
  try {
    await writeOffSalePreReceipt(form.id, writeOffItems.value)
    ElMessage.success(tt('核销成功'))
    writeOffDialog.value = false
    await loadDetail()
  } catch (e) {
    ElMessage.error(e.message || tt('核销失败'))
  }
}

function handlePrint() {
  if (!form.id) { ElMessage.warning(tt('请先保存单据再打印')); return }
  printVisible.value = true
}

async function loadDetail() {
  const __detailId = route.params.id || form.preReceiptId || ''
  if (__detailId) {
    try {
      const res = await getSalePreReceipt(__detailId)
      Object.assign(form, res)
      if (!form.items) form.items = []
      // 回填表头级自定义字段值
      await loadHeaderCustomFields()
      if (form.id) {
        const cfValues = await loadCustomFieldValues('sale_pre_receipt', form.id).catch(() => ({}))
        Object.assign(form, cfValues)
      }
      calcAmount()
    } catch (e) {
      ElMessage.error(e.message || tt('加载失败'))
    }
  } else {
    autoFillDocCodeOnCreate(form, 'receiptNo')
    // 新建模式也加载自定义字段定义（分区渲染）
    await loadHeaderCustomFields()
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
  getId: () => form.id,
  api: {
    approve: auditSalePreReceipt,
    reject: rejectSalePreReceipt,
    reverseApprove: reverseApproveSalePreReceipt,
    close: closeOrderSalePreReceipt,
    reopen: reopenSalePreReceipt,
    void: voidSalePreReceipt,
    unvoid: unvoidSalePreReceipt,
    delete: deleteSalePreReceipt
  },
  reload: loadDetail,
  router,
  listPath: '/finance/sale-pre-receipts',
  newPath: '/finance/sale-pre-receipts/new',
  billLabel: '销售预收款单'
})

// ===== 复制功能 =====
const { applyCopy } = useCopyBill({
  route,
  loadFn: getSalePreReceipt,
  form,
  noField: 'receiptNo',
  customField: { entity: 'sale_pre_receipt' }
})

// 加载下拉基础数据——独立函数，供 onMounted 和切回标签页时复用
  async function loadBaseData() {
  try {
      const [custRes, curRes, accRes, empRes] = await Promise.all([
        listAllCustomer(),
        listAllCurrency(),
        listAllBankAccount(),
        employeeSelectOptions()
      ])
      customers.value = custRes || []
      currencies.value = curRes || []
      accountOptions.value = accRes || []
      employees.value = empRes || []
      // 默认币别：外币业务关闭时新建单据只用本位币（无本位币标记则退回 CNY）
      const baseCur = currencies.value.find(c => c.isBaseCurrency === 1 || c.isBaseCurrency === true)
      if (!form.currencyCode) form.currencyCode = (!foreignCurrencyEnabled.value && baseCur ? baseCur.currencyCode : 'CNY')
      onCurrencyChange()
    } catch (e) {
      // 占位：下拉数据加载失败时不阻塞页面
      customers.value = []
      currencies.value = [{ currencyCode: 'CNY', exchangeRate: 1 }]
      accountOptions.value = []
      employees.value = []
    }
}

// ===== 顶级页签：单据 / 来源单据 / 去向单据 =====
const topTab = ref('doc')
const billLinkRegistered = ref(false)

const { isFieldRequired } = useFieldPermission('sale_pre_receipt')
useFieldRequiredMark('sale_pre_receipt', '.sale-pre-receipt-edit')

onMounted(async () => {
  // 全站商业口径总开关（设置中心：启用外币业务）——最先装载，失败不阻断页面
  await ensureCommercialSwitches()
  // 该单据类型是否登记了「来源单据/去向单据」配置（未登记则不显示这两个页签）
  isBillLinkRegistered('SALE_PRE_RECEIPT')
    .then(v => { billLinkRegistered.value = !!v })
    .catch(() => { billLinkRegistered.value = false })
  loadCollapsed()
  await loadBaseData()
  const copied = await applyCopy()
  if (!copied) {
    await loadDetail()
  }
})

// 切回标签页时自动刷新下拉基础数据（只刷下拉，不动表单草稿），避免档案改过后这里还是旧数据
useBaseDataRefresh(loadBaseData)


  </script>

<style scoped lang="scss">
.sale-pre-receipt-edit {
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
  min-width: 1100px;
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

.muted-tip {
  font-size: 12px;
  color: #909399;
}

.write-off-info {
  background: #f8faff;
  border: 1px solid #e0e7ff;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 12px;
  display: flex;
  gap: 24px;
  flex-wrap: wrap;

  .info-line {
    font-size: 13px;
    color: #374151;
    .label { color: #6b7280; }
  }
}

.remain-amount {
  color: #6366f1;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 1200px) {
  .base-form .section-fields { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 768px) {
  .sale-pre-receipt-edit { padding: 8px; }
  .page-header { flex-direction: column; gap: 8px; align-items: stretch; }
  .base-form .section-fields { grid-template-columns: 1fr; }
  .summary-area { flex-direction: column; }
}
</style>
