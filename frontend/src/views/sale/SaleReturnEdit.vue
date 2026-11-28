<template>
  <div class="sale-return-edit">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div class="header-left">
        <div class="back-btn" @click="$router.push('/sale/returns')">
          <el-icon><ArrowLeft /></el-icon>
          <span>{{ $tt("返回列表") }}</span>
        </div>
        <div class="title-area">
          <div class="title-row">
            <el-icon class="title-icon"><Document /></el-icon>
            <span class="page-title">{{ isEdit ? '销售退货单' : '新增销售退货单' }}</span>
            <span v-if="form.returnNo" class="order-no">
              <el-icon><Collection /></el-icon>
              {{ form.returnNo }}
            </span>
            <el-tag v-if="form.approvalStatus" size="small" :type="approvalStatusType(form.approvalStatus)" effect="plain" class="status-tag">{{ $dt(form.approvalStatus) }}</el-tag>
            <el-tag v-if="form.status && !isDraft" size="small" :type="statusType(form.status)" effect="plain" class="status-tag">{{ $dt(form.status) }}</el-tag>
          </div>
        </div>
      </div>
      <div class="header-right">
        <el-button v-permission="perm('view')" v-if="!(!form.returnId)" @click="changeRecordVisible = true">
          <el-icon><Memo /></el-icon>{{ $tt('操作日志') }}
        </el-button>
        <PushDownButton v-if="canPushDown(form.status, form.closeStatus)" source-type="SaleReturn" :source-doc="form" />
        <BillActions bill-type="SALE_RETURN"
        :supported-actions="supportedActions"
          :status="form.status"
          :close-status="form.closeStatus"
          :is-new="!form.returnId"
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
        <el-form :model="form" :rules="rules" ref="headerFormRef" label-width="70px" class="base-form">
          <!-- 单据信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>单据信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('退货单号')">
                <el-input v-model="form.returnNo" :placeholder="docCodePlaceholder" :disabled="!isDraft" class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('退货日期')" prop="returnDate" required>
                <el-date-picker v-model="form.returnDate" type="date" value-format="YYYY-MM-DD" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('退货处理')">
                <el-radio-group v-model="form.stockEffect" :disabled="!isDraft">
                  <el-radio :label="1">{{ $tt('回库') }}</el-radio>
                  <el-radio :label="0">{{ $tt('不回库') }}</el-radio>
                </el-radio-group>
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
                <el-select v-model="form.customerId" filterable :placeholder="$tt('选择客户')" class="field-input" :disabled="!isDraft" @change="onCustomerChange">
                  <el-option v-for="c in withFallbackOption(customers, 'customerId', 'customerName', form)" :key="c.customerId" :label="c.customerName" :value="c.customerId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('仓库')" prop="warehouseId">
                <el-select v-model="form.warehouseId" filterable :placeholder="$tt('选择仓库')" class="field-input" :disabled="!isDraft" @change="onWarehouseChange">
                  <el-option v-for="w in withFallbackOption(warehouses, 'warehouseId', 'warehouseName', form)" :key="w.stockId" :label="w.stockName" :value="w.stockId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('销售员')" prop="salerId" required>
                <el-select v-model="form.salerId" filterable :placeholder="$tt('选择销售员')" class="field-input" :disabled="!isDraft">
                  <el-option v-for="e in employees" :key="e.employeeId" :label="e.employeeName" :value="e.employeeId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('客户编号')">
                <el-input v-model="form.customerCode" :disabled="!isDraft" readonly class="field-input"  />
              </el-form-item>
              <el-form-item :label="$tf('联系人')">
                <el-input v-model="form.contactPerson" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('联系电话')">
                <el-input v-model="form.contactPhone" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('收货地址')" class="span-all">
                <el-input v-model="form.deliveryAddress" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('退货原因')" class="span-all">
                <el-input v-model="form.returnReason" class="field-input" :disabled="!isDraft" :placeholder="$tt('请输入退货原因')" />
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
                <el-select v-model="form.currencyId" :placeholder="$tt('选择币种')" class="field-input" :disabled="!isDraft || !foreignCurrencyEnabled" @change="onCurrencyChange">
                  <el-option v-for="c in currencies" :key="c.currencyId" :label="c.currencyName" :value="c.currencyId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('汇率')">
                <el-input-number v-model="form.exchangeRate" :precision="6" :step="0.1" :min="0" class="field-input" :disabled="!isDraft || !foreignCurrencyEnabled" />
              </el-form-item>
              <el-form-item v-if="taxEnabled" :label="$tf('计税方式')">
                <el-select v-model="form.taxCalcMethod" class="field-input" :disabled="!isDraft" @change="onTaxCalcMethodChange">
                  <el-option v-for="o in taxMethodOptions" :key="o" :label="$tf(o)" :value="o" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('价格等级')">
                <el-select v-model="form.priceLevelId" filterable clearable :placeholder="$tt('选择价格等级')" class="field-input" :disabled="!isDraft" @change="onPriceLevelChange">
                  <el-option v-for="p in priceLevels" :key="p.priceLevelId" :label="p.priceLevelName" :value="p.priceLevelId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('付款条件')">
                <PaymentTermSelect v-model="form.paymentTermId" :disabled="!isDraft" @change="onPaymentTermChange" />
              </el-form-item>
              <el-form-item :label="$tf('结算方式')">
                <el-select v-model="form.settleModeId" filterable clearable :placeholder="$tt('选择结算方式')" class="field-input" :disabled="!isDraft" @change="onSettleChange">
                  <el-option v-for="s in settleTypes" :key="s.settleModeId" :label="s.settleModeName" :value="s.settleModeId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('预付比例%')">
                <el-input-number v-model="form.prepaidRate" :precision="2" :step="1" :min="0" :max="100" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('预付金额')">
                <el-input-number v-model="form.prepaidAmount" :precision="2" :step="1" :min="0" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('整单折扣%')">
                <el-input-number v-model="form.discountRate" :precision="2" :step="1" :min="0" :max="100" class="field-input" :disabled="!isDraft" />
              </el-form-item>
            </div>
          </div>

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

    <!-- 明细卡片（通用组件 BillItemsTable：卡片/列设置/动态列/自定义字段列/汇总 全站共用一套） -->
    <BillItemsTable
      v-model:collapsed="collapsed.items"
      :items="form.items"
      page-key="SaleReturnEdit_items"
      title="退货明细"
      subtitle="物料明细"
      col-setting-title="物料明细列设置"
      :columns="detailColumns"
      :custom-columns="itemCustomColumns"
      :readonly="!isDraft"
      :total-qty="totalQty"
      :summary-items="summaryItems"
      :source-link-click="goToSourceBill"
      @row-change="onRowPriceChange"
      @remove="calcTotal"
    >
      <template #actions>
        <SourcePicker ref="sourcePickerRef" target-type="SaleReturn" @pick="onSourcePicked" :disabled="!isDraft" btn-type="success" size="small" />
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
      <template #cell-unit="{ row }">
        <UnitPickerCell
          v-model="row.unitId"
          :units="units"
          :material="materialOf(row)"
          :disabled="!isDraft"
          @change="calcRow(row)"
        />
      </template>
      <template #cell-stockId="{ row }">
                  <el-select v-model="row.stockId" filterable :placeholder="$tt('仓库')" size="small" :disabled="!isDraft" style="width:100%">
                    <el-option v-for="w in withFallbackOption(warehouses, 'stockId', 'warehouseName', row)" :key="w.stockId" :label="w.stockName" :value="w.stockId" />
                  </el-select>
      </template>
      <template #cell-binId="{ row }">
                  <el-select v-model="row.binId" filterable :placeholder="$tt('请选择货位')" size="small" style="width:100%" :disabled="!isDraft">
                    <el-option v-for="l in getLocationsByStock(row.stockId)" :key="l.binId" :label="l.binName" :value="l.binId" />
                  </el-select>
      </template>
      <template #cell-batchNo="{ row }">
                  <el-input v-model="row.batchNo" size="small" :placeholder="row._isLotControl ? '必填' : ''" :disabled="!isDraft" />
                  <span v-if="row._isLotControl" class="lot-flag">*</span>
      </template>
      <template #cell-productionDate="{ row }">
                  <el-date-picker v-if="row._isExpiryControl" v-model="row.productionDate" type="date" value-format="YYYY-MM-DD" size="small" style="width:100%" :disabled="!isDraft" @change="(v) => onProductionDateChange(row, v)" />
                  <span v-else class="thumb-placeholder">-</span>
      </template>
      <template #cell-expiryDate="{ row }">
                  <el-date-picker v-if="row._isExpiryControl" v-model="row.expiryDate" type="date" value-format="YYYY-MM-DD" size="small" style="width:100%" disabled />
                  <span v-else class="thumb-placeholder">-</span>
      </template>
      <template #cell-returnReason="{ row }">
        <el-input v-model="row.returnReason" size="small" :placeholder="$tt('退货原因')" :disabled="!isDraft" />
      </template>
      <template #summary-extra>
        <div v-if="form.currencyCode && form.currencyCode !== 'CNY'" class="summary-block">
          <div class="block-title">{{ $tt("外币信息") }}</div>
          <div class="sum-line">
            <span class="label">{{ $tt("币种：") }}</span>
            <span class="value">{{ form.currencyCode }}</span>
          </div>
          <div class="sum-line">
            <span class="label">{{ $tt("汇率：") }}</span>
            <span class="value">{{ Number(form.exchangeRate || 0).toFixed(6) }}</span>
          </div>
        </div>
        <div class="summary-block">
          <div class="block-title">{{ $tt("预付信息") }}</div>
          <div class="sum-line">
            <span class="label">{{ $tt("预付比例：") }}</span>
            <span class="value">{{ Number(form.prepaidRate || 0).toFixed(2) }}%</span>
          </div>
          <div class="sum-line">
            <span class="label">{{ $tt("预付金额：") }}</span>
            <span class="value">¥{{ Number(form.prepaidAmount || 0).toFixed(2) }}</span>
          </div>
          <div class="sum-line">
            <span class="label">{{ $tt("结算方式：") }}</span>
            <span class="value">{{ form.settleName || '—' }}</span>
          </div>
          <div class="sum-line">
            <span class="label">{{ $tt("付款条件：") }}</span>
            <span class="value">{{ form.paymentTermName || '—' }}</span>
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
          type="sale-return"
          :disabled="!isDraft"
          button-text="上传附件"
          tip-text="支持图片、PDF、Word、Excel 等文件, 下推时附件会带到后续单据"
        />
      </div>
    </div>

    <!-- 审核流转（真实审批进度，通用组件） -->
    <ApprovalFlowTimeline bill-type="SALE_RETURN" :bill-id="form.returnId" :status="form.status" />

    <!-- 取消弹窗 -->
    <el-dialog v-model="cancelDialog" :title="$tf('取消退货单')" width="420px">
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

    <!-- 操作日志弹窗 -->
    <ChangeRecordDialog v-model="changeRecordVisible" bill-type="销售退货" :bill-no="form.returnNo || ''" />

    <!-- 打印预览（统一打印组件，与其余单据一致） -->
    <PrintPreviewDialog v-model="printVisible" bill-type="SALE_RETURN" :bill-id="form.returnId" />

    <!-- 物料明细列设置抽屉（已收进通用组件 BillItemsTable，页内不再保留） -->
      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('来源单据')" name="source" :disabled="!form.returnId">
        <BillLinkPanel v-if="topTab === 'source'" bill-type="SALE_RETURN" :bill-id="form.returnId" mode="source" />
      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('去向单据')" name="target" :disabled="!form.returnId">
        <BillLinkPanel v-if="topTab === 'target'" bill-type="SALE_RETURN" :bill-id="form.returnId" mode="target" />
      </el-tab-pane>
    </el-tabs>
  </div>

  </template>

<script setup>
import { withFallbackOption } from '@/utils/labelFallback'
import { calcTaxAmount } from '@/utils/billTax'
import { ref, reactive, computed, onMounted, watch, h } from 'vue'
import { useBaseDataRefresh } from '@/composables/useBaseDataRefresh'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { ArrowLeft, Document, Collection, Plus, Grid, Printer, Link, ArrowRight, Check, TopRight, ArrowDown, CircleCheck, Close, Memo } from '@element-plus/icons-vue'
import { getSaleReturn, saveSaleReturn, updateSaleReturn, approveSaleReturn, submitSaleReturn, cancelSaleReturn, rejectSaleReturn, reverseApproveSaleReturn, closeOrderSaleReturn, reopenSaleReturn, voidSaleReturn, unvoidSaleReturn, deleteSaleReturn } from '@/api/sale/saleReturn'
import { listAllCustomer } from '@/api/basedata/customer'
import { listAllMaterial } from '@/api/basedata/material'
import { listAllWarehouse } from '@/api/basedata/warehouse'
import { listAllEmployee } from '@/api/basedata/employee'
import { getCurrentEmployee } from '@/composables/useCurrentEmployee'
import { listAllLocation } from '@/api/basedata/location'
import { listAllCurrency } from '@/api/basedata/currency'
import { listAllPaymentTerm } from '@/api/basedata/payment-term'
import { listAllPriceLevel } from '@/api/basedata/price-level'
import { listAllSettle } from '@/api/basedata/settle'
import { listAllUnit } from '@/api/basedata/unit'
import PushDownButton from '@/components/PushDownButton.vue'
import ApprovalFlowTimeline from '@/components/bill/ApprovalFlowTimeline.vue'
import BillLinkPanel from '@/components/bill/BillLinkPanel.vue'
import { isBillLinkRegistered } from '@/api/billLink'
import { canPushDown } from '@/utils/bill-status'
import SourcePicker from '@/components/SourcePicker.vue'
import FileUploader from '@/components/FileUploader.vue'
import PaymentTermSelect from '@/components/PaymentTermSelect.vue'
import { applyPushSource } from '@/utils/pushSourceApplier'
import {
  loadCustomFieldDefinitions,
  loadCustomFieldValues,
  saveCustomFieldValues,
  batchLoadCustomFieldValues,
  customFieldsToTableColumns,
  injectMaterialCustomFields,
  copyCustomFieldValues,
  validateItemCustomRequired,
  remapItemIdsAfterSave,
} from '@/composables/useCustomField'
import ChangeRecordDialog from '@/components/ChangeRecordDialog.vue'
import PrintPreviewDialog from '@/components/PrintPreviewDialog.vue'
// 物料库选择弹窗（通用件）：内置自定义字段搜索、全字段/自定义字段列、列设置全局共享 MaterialPicker
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
// 明细通用组件（卡片折叠/列设置/动态列/自定义字段列/汇总 全站共用一套）
import BillItemsTable from '@/components/bill/BillItemsTable.vue'
import UnitPickerCell from '@/components/bill/UnitPickerCell.vue'
import { LINE_BOM_COLUMNS, useLineBom } from '@/composables/useLineBom'
const { fillRowBom } = useLineBom()
// 物料下拉的输入过滤 + 背景提示文字（通用件）
import { filterMaterials, buildMaterialPlaceholder } from '@/utils/materialFilter'
import { useBillLineStock, BILL_STOCK_COLUMNS } from '@/composables/useBillLineStock'
import { triggerListRefresh } from '@/composables/useAutoRefresh'
// 单据取价（2026-09-20 价格策略修复）：按设置中心「取价优先级」取价，选物料/改数量时回填单价
import { resolveUnitPrice, applyFetchedUnitPrice } from '@/composables/usePriceFetch'
import { statusType, approvalStatusType } from '@/composables/useBillStatus'
import { useAutoDocCode } from '@/composables/useAutoDocCode'
import { validateBillRequiredFields } from '@/utils/billValidation'
import { useFieldPermission } from '@/composables/useFieldPermission'
import { useFieldRequiredMark } from '@/utils/fieldRequiredMark'
import { useCommercialSwitches } from '@/composables/useCommercialSwitches'
import { useSysConfig } from '@/composables/useSysConfig'

import * as BillStatus from '@/utils/bill-status'
import { confirmSubmit, submitSuccessMessage } from '@/utils/submit-confirm'
import BillActions from '@/components/bill/BillActions.vue'
import { useBillActions } from '@/composables/useBillActions'
import { useCopyBill } from '@/composables/useCopyBill'
import { tt } from '@/i18n'
import { useMaterialCustomFields } from '@/composables/useMaterialCustomFields'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('sale:returns')

// 全站商业口径总开关（设置中心：启用含税业务 / 启用外币业务）
const { taxEnabled, foreignCurrencyEnabled, ensureLoaded: ensureCommercialSwitches } = useCommercialSwitches()
const { loadSysConfig, getConfig } = useSysConfig()

// ===== 计税方式（含税价 / 不含税价 / 不计税）：表头驱动明细「含税价 / 不含税价」换算 =====
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
  } else if (!form.returnId) {
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

// 单据编码自动生成
const { docCodeDisabled, docCodePlaceholder, autoFillDocCodeOnCreate } = useAutoDocCode('SALE_RETURN')

// 物料明细列定义（通用组件 BillItemsTable 消费）：editor = 单元格控件，type = 纯展示格式；
// 物料/仓库/货位/批次号/生产日期/有效期至/退货原因列由页面提供插槽，来源单号列由组件的 sourceLinkClick 回调跳转来源单
const detailColumns = [
  { prop: 'materialMainImage', label: '图片', width: 60, align: 'center', type: 'image' },
  { prop: 'materialId', label: '商品名称', minWidth: 280 },
  { prop: 'materialCode', label: '商品编号', width: 130 },
  ...BILL_STOCK_COLUMNS,
  { prop: 'specification', label: '规格', width: 110, type: 'text' },
  // 行级 BOM（2026-11-06）：按物料主 BOM 自动带出，只读
  ...LINE_BOM_COLUMNS,
  { prop: 'unit', label: '单位', width: 60, align: 'center', type: 'text' },
  { prop: 'quantity', label: '数量', width: 90, editor: 'number', precision: 4 },
  { prop: 'unitPrice', label: '单价', width: 90, editor: 'number', precision: 4 },
  { prop: 'priceWithTax', label: '含税价', width: 95, align: 'right', type: 'amount4' },
  { prop: 'priceWithoutTax', label: '不含税价', width: 95, align: 'right', type: 'amount4' },
  { prop: 'taxRate', label: '税率%', width: 70, editor: 'number', precision: 0, min: 0, max: 100 },
  { prop: 'discountRate', label: '折扣率%', width: 80, editor: 'number', precision: 2, min: 0, max: 100 },
  { prop: 'discountAmount', label: '折扣额', width: 85, align: 'right', type: 'amount2', cellClass: 'discount-text' },
  { prop: 'amount', label: '金额', width: 85, align: 'right', type: 'amount2' },
  { prop: 'taxAmount', label: '税额', width: 85, align: 'right', type: 'amount2' },
  { prop: 'netAmount', label: '价税合计', width: 95, align: 'right', type: 'amount2', cellClass: 'net-amount' },
  { prop: 'stockId', label: '仓库', width: 140, editor: 'select', nameProp: 'stockName', placeholder: '选择仓库', clearable: false,
    optionsFn: () => (warehouses.value || []).map(w => ({ value: w.stockId, label: w.stockName })),
    change: (row) => { row.binId = null; row.binName = '' } },
  { prop: 'binId', label: '货位', width: 120 },
  { prop: 'batchNo', label: '批次号', width: 120 },
  { prop: 'sourceBillNo', label: '来源单号', minWidth: 140, type: 'sourceLink' },
  { prop: 'productionDate', label: '生产日期', width: 130 },
  { prop: 'expiryDate', label: '有效期至', width: 130 },
  { prop: 'returnReason', label: '退货原因', width: 140 }
]
// 汇总区（通用组件按此渲染；「合计数量」行由组件用 totalQty 渲染，口径同迁移前的汇总区）；含税业务关闭时不显示「合计税额」
const summaryItems = computed(() => {
  const items = [
    { label: '折前金额：', value: `¥${Number(form.totalAmount || 0).toFixed(2)}` },
    { label: '折扣金额：', value: `- ¥${Number(form.discountAmount || 0).toFixed(2)}`, class: 'discount' }
  ]
  if (taxEnabled.value) {
    items.push({ label: '合计税额：', value: `¥${Number(form.taxAmount || 0).toFixed(2)}` })
  }
  items.push({ label: '价税合计：', value: `¥${Number(form.netAmount || 0).toFixed(2)}`, total: true })
  return items
})
useBillLineStock(() => form.items)
// ===== 物料库弹窗的列定义 / 列设置 / 图片渲染 已收进通用组件 MaterialPickerDialog =====
// （列定义用共享的 materialPickerColumns()，列设置 page-key = 'MaterialPicker' 全局共享，本页不再维护这份列清单）

const route = useRoute()
const router = useRouter()
const formRef = ref()
const headerFormRef = ref()

const isEdit = computed(() => !!route.params.id)

// 仅草稿态允许编辑（单号/业务字段/自定义字段）；已生效/待审批等只读
const STORAGE_KEY = 'sale-return-edit-collapsed'

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

// 明细卡片折叠由通用组件 v-model:collapsed 驱动，这里监听后持久化
watch(() => collapsed.items, () => saveCollapsed())

async function loadCustomFields() {
  const headerResult = await loadCustomFieldDefinitions('sale_return')
  headerCustomSections.value = headerResult.sections
  headerCustomFieldMap.clear()
  headerResult.fieldMap.forEach((v, k) => headerCustomFieldMap.set(k, v))
  headerFormulaFields.value = headerResult.formulaFields

  const itemResult = await loadCustomFieldDefinitions('sale_return_item')
  itemCustomFields.value = Array.from(itemResult.fieldMap.values())
  itemCustomColumns.value = customFieldsToTableColumns(itemCustomFields.value, { editable: isDraft.value, listShowOnly: false })
// 草稿态变化时重新生成明细自定义字段列（editable 控制）
watch(isDraft, () => {
  if (itemCustomFields.value && itemCustomFields.value.length > 0) {
    itemCustomColumns.value = customFieldsToTableColumns(itemCustomFields.value, { editable: isDraft.value, listShowOnly: false });
  }
});

}

const customers = ref([])
const materials = ref([])
/** 当前行物料（多单位换算用：取基本单位、辅计量单位与换算率） */
function materialOf(row) {
  if (!row || row.materialId == null) return null
  return materials.value.find(x => x.materialId === row.materialId) || null
}
const warehouses = ref([])
const employees = ref([])
const currencies = ref([])
const paymentTerms = ref([])
const priceLevels = ref([])
const settleTypes = ref([])
const units = ref([])
const locationOptions = ref([])

const getLocationsByStock = (stockId) => {
  if (!stockId) return []
  return locationOptions.value.filter(l => l.stockId === stockId)
}

const cancelDialog = ref(false)
const changeRecordVisible = ref(false)
const printVisible = ref(false)
const cancelReason = ref('')

/** 物料库选择弹窗显隐（弹窗本体 = 通用组件 MaterialPickerDialog，搜索/分页/列设置都在组件内） */
const pickerVisible = ref(false)

const form = reactive({
  returnId: null,
  returnNo: '',
  returnDate: new Date().toISOString().slice(0, 10),
  customerId: null,
  customerName: '',
  customerCode: '',
  contactPerson: '',
  contactPhone: '',
  deliveryAddress: '',
  warehouseId: null,
  warehouseName: '',
  stockEffect: 1,
  salerId: null,
  currencyId: null,
  currencyCode: 'CNY',
  exchangeRate: 1.0,
  priceLevelId: null,
  paymentTermId: null,
  paymentTermName: '',
  settleModeId: null,
  settleName: '',
  prepaidRate: 0,
  prepaidAmount: 0,
  sourceBillType: '无来源',
  sourceBillNo: '',
  deliveryNo: '',
  returnReason: '',
  remark: '',
  attachmentUrls: '',   // 附件URL(逗号分隔)
  discountRate: 0,
  discountAmount: 0,
  totalAmount: 0,
  taxAmount: 0,
  netAmount: 0,
  taxCalcMethod: '不含税价',
  status: '草稿',
  approvalStatus: '',
  createTime: '',
  auditTime: '',
  items: []
})


const isDraft = computed(() => BillStatus.canEdit(form.status, form.closeStatus))
const totalQty = computed(() => {
  return form.items.reduce((sum, row) => sum + Number(row.quantity || 0), 0).toFixed(2)
})

const rules = {
  returnDate: [{ required: true, message: tt('请选择退货日期'), trigger: 'change' }],
  customerId: [{ required: true, message: tt('请选择客户'), trigger: 'change' }],
  warehouseId: [],
  salerId: [{ required: true, message: tt('请选择销售员'), trigger: 'change' }],
  currencyId: [{ required: true, message: tt('请选择币种'), trigger: 'change' }]
}

function onCustomerChange(id) {
  const c = customers.value.find(x => x.customerId === id)
  if (c) {
    form.customerName = c.customerName
    form.customerCode = c.customerCode || ''
    form.contactPerson = c.contactPerson || c.linkman || ''
    form.contactPhone = c.contactPhone || c.phone || ''
    form.deliveryAddress = c.deliveryAddress || c.address || ''
    if (c.creditLimit != null) form.creditLimit = c.creditLimit
    if (c.defaultSalerId || c.salesRepId) form.salerId = c.defaultSalerId || c.salesRepId
    // 客户默认税率带入明细行（2026-11-08：数值化比较，兼容输入框回传的字符串税率）
    const custTaxRate = Number(c.taxRate)
    if (custTaxRate > 0) {
      form.items.forEach(row => {
        const rowRate = Number(row.taxRate)
        if (!rowRate || rowRate === 13) {
          row.taxRate = custTaxRate
          calcRow(row)
        }
      })
    }
    if (c.paymentTermId) {
      form.paymentTermId = c.paymentTermId
      onPaymentTermChange(c.paymentTermId)
    }
    if (c.defaultPriceLevelId) {
      form.priceLevelId = c.defaultPriceLevelId
      onPriceLevelChange(c.defaultPriceLevelId)
    }
    if (c.currencyId) {
      form.currencyId = c.currencyId
      onCurrencyChange(c.currencyId)
    }
    calcTotal()
  }
}

// 价格等级联动：按 priceAdjustRate 调整所有明细行单价（baseUnitPrice 为基准售价）
function onPriceLevelChange(id) {
  const p = priceLevels.value.find(x => x.priceLevelId === id)
  const rate = p && p.priceAdjustRate ? Number(p.priceAdjustRate) : 0
  form.items.forEach(row => {
    if (row.baseUnitPrice != null) {
      row.unitPrice = Number((row.baseUnitPrice * (1 + rate / 100)).toFixed(4))
      calcRow(row)
    }
  })
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
    form.paymentMethod = s.settleModeName
  } else {
    form.settleName = ''
  }
}

function onWarehouseChange(id) {
  const w = warehouses.value.find(x => x.stockId === id)
  if (w) form.warehouseName = w.stockName
  // 表头仓库 = 统一选择器：选定后覆盖全部明细行（仍可逐行再改）
  ;(form.items || []).forEach(row => {
    row.stockId = id || null
    row.stockName = w ? w.stockName : ''
    row.binId = null
    row.binName = ''
  })
}

function onCurrencyChange(id) {
  const c = currencies.value.find(x => x.currencyId === id)
  if (c) {
    form.currencyCode = c.currencyCode
    if (c.exchangeRate) form.exchangeRate = c.exchangeRate
  }
}

async function onMaterialChange(row, id) {
  // 行级 BOM（2026-11-06）：选物料后按主 BOM 自动带出清单与版本
  fillRowBom(row)
  const m = materials.value.find(x => x.materialId === id)
  if (m) {
    row.materialCode = m.materialCode
    row.materialName = m.materialName
    row.materialMainImage = m.mainImage || ''
    row.specification = m.model
    // Material 实体没有 unitName 字段，根据 unitId 从计量单位列表查找单位名称
    const unitId = m.saleUnitId || m.baseUnitId || null
    row.unitId = unitId
    if (unitId && units.value.length > 0) {
      const u = units.value.find(x => x.unitId === unitId)
      row.unit = u ? u.unitName : ''
    } else {
      row.unit = ''
    }
    // 2026-09-20 价格策略修复：单价按设置中心「取价优先级」取价（价格策略 → 价格等级 → 物料默认售价）。
    // baseUnitPrice 仍取物料基础售价，保证「价格等级」切换时的重算口径不变。
    applyFetchedUnitPrice(row, {
      side: 'sale',
      partnerId: form.customerId,
      materialId: m.materialId,
      quantity: row.quantity,
      taxType: row.taxCalcMethod
    }, m.salePrice || 0, (r) => calcRow(r))
    row.taxRate = 13
    row.discountRate = 0
    row.discountAmount = 0
    row.stockId = form.warehouseId
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
  // 表头「计税方式」决定「单价」是含税价还是不含税价 → 换算另一列（含税价 / 不含税价）
  const method = taxEnabled.value ? (form.taxCalcMethod || '不含税价') : '不计税'
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
  // 折前金额一律按「不含税」口径参与折扣与税额计算
  const originalAmount = inclusive ? (price * qty) / factor : price * qty
  row.discountAmount = (originalAmount * discountRate / 100).toFixed(2)
  row.amount = (originalAmount - Number(row.discountAmount)).toFixed(2)
  // 税额：不计税恒 0；含税价按「折后含税合计 − 折后不含税金额」倒算（统一口径，见 utils/billTax）
  row.taxAmount = calcTaxAmount({ amount: row.amount, unitPrice: price, quantity: qty, taxRate, discountRate, inclusive, noTax }).toFixed(2)
  row.netAmount = (Number(row.amount) + Number(row.taxAmount)).toFixed(2)
  calcTotal()
}

/**
 * 行内单元格变更（BillItemsTable 的 @row-change）
 * 2026-09-20 价格策略修复：数量变了 → 若该行单价仍是"上次自动带出的价"（即用户没手改过），
 * 按新数量重新取价（价格策略支持数量区间档位）。用户手改过的行不覆盖其输入。
 */
function onRowPriceChange(row, prop) {
  calcRow(row)
  if (prop === 'quantity') scheduleRowReprice(row)
}

const _repriceTimers = new Map()
function scheduleRowReprice(row) {
  if (!row || !row.materialId) return
  const key = row.itemId || row.invoiceItemId || row.returnItemId || row.orderItemId || row.materialId
  if (_repriceTimers.has(key)) clearTimeout(_repriceTimers.get(key))
  _repriceTimers.set(key, setTimeout(async () => {
    _repriceTimers.delete(key)
    if (!row.materialId) return
    // 用户已手改单价 → 不覆盖
    if (row._autoUnitPrice !== undefined && Number(row.unitPrice) !== Number(row._autoUnitPrice)) return
    const priced = await resolveUnitPrice({
      side: 'sale',
      partnerId: form.customerId,
      materialId: row.materialId,
      quantity: row.quantity,
      taxType: row.taxCalcMethod
    })
    if (!priced) return
    row.unitPrice = priced.price
    row._autoUnitPrice = priced.price
    calcRow(row)
  }, 400))
}

function calcTotal() {
  let totalAmount = 0    // 折前合计
  let discountAmount = 0 // 折扣额合计
  let taxAmount = 0      // 税额合计
  form.items.forEach(i => {
    const amt = Number(i.amount || 0)
    const disc = Number(i.discountAmount || 0)
    totalAmount += amt + disc           // 折前金额 = 折后金额 + 折扣额
    discountAmount += disc
    taxAmount += Number(i.taxAmount || 0)
  })
  form.totalAmount = totalAmount.toFixed(2)
  form.discountAmount = discountAmount.toFixed(2)
  form.taxAmount = taxAmount.toFixed(2)
  // 价税合计 = 折前金额 - 折扣额 + 税额
  form.netAmount = (totalAmount - discountAmount + taxAmount).toFixed(2)
  // 本位币折算（按表头汇率，实时显示）
  const rate = Number(form.exchangeRate || 1)
  form.baseTotalAmount = (totalAmount * rate).toFixed(2)
  form.baseTaxAmount = (taxAmount * rate).toFixed(2)
  form.baseNetAmount = (Number(form.netAmount) * rate).toFixed(2)
}

// 合计数量/金额口径见 summaryItems 与 totalQty（表格合计行已由通用组件 BillItemsTable 的汇总区替代）

function addItem() {
  form.items.push({
    materialId: null, materialCode: '', materialName: '', specification: '', unitId: null, unit: '',
    quantity: 1, unitPrice: 0, priceWithTax: 0, priceWithoutTax: 0, taxRate: 13, discountRate: 0, discountAmount: 0,
    amount: 0, taxAmount: 0, netAmount: 0,
    stockId: form.warehouseId, binId: '', batchNo: '', returnReason: '',
    _isLotControl: false, _isExpiryControl: false, _expiryDays: 0, productionDate: '', expiryDate: ''
  })
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
 * - 本页未维护「已请求过的物料ID集合」，故不合并组件回传的 valueMap，沿用原有按需批量取值逻辑（与改造前一致）
 */
async function onMaterialPicked({ rows: picked, valueMap }) {
  if (!picked || picked.length === 0) return
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
      specification: m.model || '',
      unitId: m.saleUnitId || m.baseUnitId || null,
      unit: '',
      quantity: 1,
      unitPrice: m.salePrice || 0,
      baseUnitPrice: m.salePrice || 0,  // 保存基础单价，用于价格等级切换
      taxRate: m.taxRate || 13,
      discountRate: 0,
      discountAmount: 0,
      amount: 0,
      taxAmount: 0,
      netAmount: 0,
      stockId: form.warehouseId,
      binId: '',
      batchNo: '',
      returnReason: ''
    }
    if (row.unitId && units.value.length > 0) {
      const u = units.value.find(x => x.unitId === row.unitId)
      row.unit = u ? u.unitName : ''
    }
    if (itemCustomFields.value.length > 0) {
      injectMaterialCustomFields(row, m.materialId, materialCustomValueMap.value, itemCustomFields.value)
    }
    calcRow(row)
    form.items.push(row)
    applyFetchedUnitPrice(row, { side: 'sale', partnerId: form.customerId, materialId: m.materialId, quantity: row.quantity, taxType: row.taxCalcMethod }, m.salePrice || 0, (r) => calcRow(r))
  })
  pickerVisible.value = false
  ElMessage.success(`${tt("已添加 ")}${picked.length}${tt(" 条明细")}`)
}

function flushInputNumbers() {
  if (!form.items || form.items.length === 0) return
  form.items.forEach(row => {
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
  form.items = [...form.items]
}

async function onSave(silent, opts = {}) {
  flushInputNumbers()
  // 状态校验: 非草稿状态不允许保存
  if (form.status && !isDraft.value) {
    ElMessage.warning(`${tt("当前状态为「")}${form.status}${tt("」, 不可修改, 只有草稿状态可保存")}`)
    return false
  }
  // 宽松草稿保存：不校验必填，允许空客户、空物料、无明细（用户可以后续补全再提交）
  // 保存宽松：丢弃未选物料的空行（后端这些字段 NOT NULL，无法落库）
  form.items = (form.items || []).filter(r => r && r.materialId)
  // 按当前表头「计税方式」把明细含税价/不含税价/金额/税额/价税合计刷成最新
  form.items.forEach(row => calcRow(row))
  calcTotal()
  try {
    if (form.returnId) {
      await updateSaleReturn(form.returnId, form)
    } else {
      const res = await saveSaleReturn(form)
      if (res && res.returnId) form.returnId = res.returnId
      if (res && res.returnNo) form.returnNo = res.returnNo
      if (res && res.status) form.status = res.status
      if (res && res.approvalStatus) form.approvalStatus = res.approvalStatus
    
      if (!opts.skipRouteSync && !route.params.id && form.returnId) {
        try { await router.replace({ name: 'SaleReturnEditPage', params: { id: String(form.returnId) } }) } catch (_) {}
      }
    }
    if (headerCustomFieldMap.size > 0) {
      const headerValues = {}
      for (const key of headerCustomFieldMap.keys()) {
        if (key in form) headerValues[key] = form[key]
      }
      await saveCustomFieldValues('sale_return', form.returnId, headerValues, headerFormulaFields.value)
    }
    if (itemCustomFields.value.length > 0 && form.items && form.items.length > 0) {
      // 关键修复（2026-09-22）：保存主表时后端走"明细先删后插"，明细主键已变 →
      // 必须先按保存后的新主键重新对齐，否则明细级自定义字段会写到已删除的旧行上（报表永远空白）。
      try {
        const fresh = await getSaleReturn(form.returnId)
        remapItemIdsAfterSave(form.items, (fresh && fresh.items) || [], 'returnItemId')
      } catch (e) {
        console.warn('[saleReturn] 保存后刷新明细主键失败，明细级自定义字段可能挂到旧行', e)
      }
      const itemKeys = new Set(itemCustomFields.value.map(f => f.fieldKey))
      for (let i = 0; i < form.items.length; i++) {
        const row = form.items[i]
        if (!row.itemId && !row.returnItemId) continue
        const itemId = row.itemId || row.returnItemId
        const values = {}
        for (const key of itemKeys) {
          if (key in row) values[key] = row[key]
        }
        await saveCustomFieldValues('sale_return_item', itemId, values)
      }
    }
    triggerListRefresh('/sale/returns')
    if (!silent) ElMessage.success(tt('保存成功'))

    return true
  } catch (e) {
    ElMessage.error(e.message || tt('保存失败'))
  return false
  }
}

async function onSubmit() {
  flushInputNumbers()
  // 前端必填校验：统一校验头表单 + 明细内表单，失败时精确列出缺失字段名
  const missing = await validateBillRequiredFields({ headerFormRef, formRef, rules, $el: document, pageKey: 'sale_return', model: form })
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
      const target = (headerFormRef && headerFormRef.value) ? headerFormRef.value : (formRef && formRef.value ? formRef.value : null)
      if (target && typeof target.scrollToField === 'function') target.scrollToField(missing[0].prop)
    } catch(_) {}
    return
  }
  // 无论新建还是已有草稿，提交前一律先静默保存（把当前页面所见落库，避免提交旧数据）
  // 2026-11-07：入库以明细行仓库为准，逐行精确提示；2026-11-28：不回库时不需要仓库
  if (form.stockEffect !== 0) {
    for (let i = 0; i < form.items.length; i++) {
      const row = form.items[i]
      if (!row.stockId) {
        ElMessage.warning(`${tt('第')}${i + 1}${tt('行')}${row.materialCode || ''} ${row.materialName || ''}${tt('未选择仓库，请选择仓库')}`)
        return
      }
    }
  }
  const saved = await onSave(true, { skipRouteSync: true })
  if (!saved || !form.returnId) return
  try {
    const needApproval = await confirmSubmit({ billType: 'SALE_RETURN', billLabel: '销售退货单' })
    await submitSaleReturn(form.returnId)
    ElMessage.success(submitSuccessMessage(needApproval))
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('提交失败'))
  } finally {
    // 提交（或用户取消）后再把新单 id 写回地址栏：此时重建实例无副作用，新实例挂载时会重新拉取最新状态
    if (!route.params.id && form.returnId) {
      try { await router.replace({ name: 'SaleReturnEditPage', params: { id: String(form.returnId) } }) } catch (_) {}
    }
  }
}

async function confirmApprove() {
  try {
    const approveTip = form.stockEffect === 0
      ? tt('确认审核该退货单? 不回库只做单据与财务冲减，审核后不可修改')
      : tt('确认审核该退货单? 审核后将回库增加库存且不可修改')
    await ElMessageBox.confirm(approveTip, tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    if (!form.returnId) {
      ElMessage.warning(tt('请先保存退货单'))
      return
    }
    await approveSaleReturn(form.returnId)
    ElMessage.success(tt('审核成功'))
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('审核失败'))
  }
}

async function doCancel() {
  if (!form.returnId) {
    ElMessage.warning(tt('请先保存退货单'))
    return
  }
  if (!cancelReason.value || !cancelReason.value.trim()) {
    ElMessage.warning(tt('请输入取消原因'))
    return
  }
  try {
    await cancelSaleReturn(form.returnId, cancelReason.value)
    ElMessage.success(tt('取消成功'))
    cancelDialog.value = false
    cancelReason.value = ''
    await load()
  } catch (e) {
    ElMessage.error(e.message || tt('取消失败'))
  }
}

const sourcePickerRef = ref()
function handlePrint() {
  if (!form.returnId) {
    ElMessage.warning(tt('请先保存退货单再打印'))
    return
  }
  printVisible.value = true
}

async function load() {
  const __detailId = route.params.id || form.returnId || ''
  if (__detailId) {
    try {
      const res = await getSaleReturn(__detailId)
      Object.assign(form, res)
      // 历史单据没有「退货处理」列：按回库处理
      if (form.stockEffect === null || form.stockEffect === undefined) form.stockEffect = 1
      if (headerCustomFieldMap.size > 0 && res.returnId) {
        const headerValues = await loadCustomFieldValues('sale_return', res.returnId)
        Object.assign(form, headerValues)
      }
      // 结算方式名称回填：后端只落库 settle_mode_id，名称从结算方式下拉列表反查，保证只读/详情区能显示名称
      if (form.settleModeId != null) {
        const st = settleTypes.value.find(x => x.settleModeId === form.settleModeId)
        if (st) form.settleName = st.settleModeName
      }
      if (!form.items) form.items = []
      // 回填物料图片（加载已有单据时后端不返回图片字段，从物料列表回填）
      if (form.items && materials.value && materials.value.length > 0) {
        form.items.forEach(row => {
          if (!row.materialMainImage && row.materialId) {
            const m = materials.value.find(x => x.materialId === row.materialId)
            row.materialMainImage = m?.mainImage || ''
          }
        })
      }
      if (itemCustomFields.value.length > 0 && res.items && res.items.length > 0) {
        const itemIds = res.items.map(it => it.itemId || it.returnItemId).filter(Boolean)
        if (itemIds.length > 0) {
          const itemValuesMap = await batchLoadCustomFieldValues('sale_return_item', itemIds)
          form.items.forEach(row => {
            const itemId = row.itemId || row.returnItemId
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
    // 新建时默认当前用户为销售员（可修改）
    const me = await getCurrentEmployee()
    if (me && !form.salerId) form.salerId = me.employeeId
    // 新建：表头「计税方式」取设置中心「默认计税方式」
    await initTaxSettings()
  }
}

// 来源单据跳转映射
const SOURCE_BILL_ROUTE_MAP = {
  '销售报价单': '/sale/quotes',
  '销售合同': '/sale/contracts',
  '销售订单': '/sale/orders',
  '销售出库单': '/sale/deliveries',
  '销售退货单': '/sale/returns',
  '打样单': '/sample/orders'
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

// 选单回调: 从销售出库单选择后回填表单
function onSourcePicked(formData, items) {
  if (formData) {
    Object.keys(formData).forEach(k => {
      if (formData[k] !== null && formData[k] !== undefined) {
        form[k] = formData[k]
      }
    })
    if (headerCustomFieldMap.size > 0) {
      copyCustomFieldValues(formData, form, Array.from(headerCustomFieldMap.values()))
    }
  }
  if (items && items.length > 0) {
    // 2026-11-23：选单带入追加到现有明细末尾，不再覆盖原有明细
    const newRows = items.map(item => {
      const row = { ...item }
      calcRow(row)
      return row
    })
    if (itemCustomFields.value.length > 0) {
      newRows.forEach((row, idx) => {
        if (items[idx]) {
          copyCustomFieldValues(items[idx], row, itemCustomFields.value)
        }
      })
    }
    form.items = [...(form.items || []), ...newRows]
  }
  calcTotal()
  ElMessage.success(`${tt("已从源单带入 ")}${items.length}${tt(" 条明细")}`)
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
  getId: () => form.returnId,
  api: {
    approve: approveSaleReturn,
    reject: rejectSaleReturn,
    reverseApprove: reverseApproveSaleReturn,
    close: closeOrderSaleReturn,
    reopen: reopenSaleReturn,
    void: voidSaleReturn,
    unvoid: unvoidSaleReturn,
    delete: deleteSaleReturn
  },
  reload: load,
  router,
  listPath: '/sale/returns',
  newPath: '/sale/returns/new',
  billLabel: '销售退货单'
})

// ===== 复制功能 =====
const { applyCopy } = useCopyBill({
  route,
  loadFn: getSaleReturn,
  form,
  afterCopy: () => calcTotal(),
  noField: 'returnNo',
  customField: { entity: 'sale_return', itemEntity: 'sale_return_item', itemIdField: 'returnItemId' }
})

// 加载下拉基础数据——独立函数，供 onMounted 和切回标签页时复用
  async function loadBaseData() {
  try {
      const [custRes, matRes, whRes, empRes, curRes, ptRes, plRes, stRes, unitRes, locRes] = await Promise.all([
        listAllCustomer(),
        listAllMaterial(),
        listAllWarehouse(),
        listAllEmployee(),
        listAllCurrency(),
        listAllPaymentTerm(),
        listAllPriceLevel(),
        listAllSettle(),
        listAllUnit(),
        listAllLocation()
      ])
      customers.value = custRes || []
      materials.value = matRes || []
      // 行内下拉附加字段：物料到位后按批预取（不阻塞渲染）
      mcf.preloadAll(materials.value)
      warehouses.value = whRes || []
      employees.value = empRes || []
      currencies.value = curRes || []
      paymentTerms.value = ptRes || []
      priceLevels.value = plRes || []
      settleTypes.value = stRes || []
      units.value = unitRes || []
      locationOptions.value = locRes || []
      if (!form.currencyId && currencies.value.length > 0) {
        // 外币业务关闭：新建单据只用本位币、汇率锁 1（历史单据保持原币，仅供查看）
        let target = currencies.value[0]
        if (!foreignCurrencyEnabled.value && !form.returnId) {
          target = currencies.value.find(c => c.isBaseCurrency === 1 || c.isBaseCurrency === true) || currencies.value[0]
        }
        form.currencyId = target.currencyId
        onCurrencyChange(form.currencyId)
        if (!foreignCurrencyEnabled.value && !form.returnId) form.exchangeRate = 1
      }
    } catch (e) {
      console.warn('下拉数据加载失败:', e)
    }
}

// ===== 顶级页签：单据 / 来源单据 / 去向单据 =====
const topTab = ref('doc')
const billLinkRegistered = ref(false)

const { isFieldRequired } = useFieldPermission('sale_return')
useFieldRequiredMark('sale_return', '.sale-return-edit')

onMounted(async () => {
  // 全站商业口径总开关（启用含税业务 / 启用外币业务）——先装载，后续明细计算与币种默认值依赖它
  await ensureCommercialSwitches()
  // 物料自定义字段：定制开关 + 字段定义（失败静默降级）
  await mcf.init()
  loadCollapsed()

  // 该单据类型是否登记了「来源单据/去向单据」配置（未登记则不显示这两个页签）
  isBillLinkRegistered('SALE_RETURN')
    .then(v => { billLinkRegistered.value = !!v })
    .catch(() => { billLinkRegistered.value = false })

  await loadCustomFields()
  await loadBaseData()
  const copied = await applyCopy()
  if (!copied) {
    await load()
    // 检测是否为下推进入 (路由 query.srcType/srcId), 自动填充源单数据
    const applied = await applyPushSource(route, 'SaleReturn', form, (items) => {
      form.items = items
      calcTotal()
    })
    if (applied) ElMessage.info(tt('已从源单带入数据, 请确认后保存'))
  }
  if (!isEdit.value) {
    autoFillDocCodeOnCreate(form, 'returnNo')
  }
})

// 切回标签页时自动刷新下拉基础数据（只刷下拉，不动表单草稿），避免档案改过后这里还是旧数据
useBaseDataRefresh(loadBaseData)


  </script>

<style scoped lang="scss">
.sale-return-edit {
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
}

.compact-table {
  min-width: 1880px;
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
  :deep(.el-table__footer .el-table__cell) {
    background: #fafbfc;
    color: #303133;
    font-weight: 600;
    padding: 6px 4px;
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

.lot-flag {
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
  .base-form .section-fields { grid-template-columns: repeat(3, 1fr); }
}

@media (max-width: 1200px) {
  .base-form .section-fields { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 768px) {
  .sale-return-edit { padding: 8px; }
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
