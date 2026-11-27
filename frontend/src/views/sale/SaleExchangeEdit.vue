<template>
  <div class="sale-exchange-edit">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div class="header-left">
        <div class="back-btn" @click="$router.push('/sale/exchanges')">
          <el-icon><ArrowLeft /></el-icon>
          <span>{{ $tt("返回列表") }}</span>
        </div>
        <div class="title-area">
          <div class="title-row">
            <el-icon class="title-icon"><Document /></el-icon>
            <span class="page-title">{{ isEdit ? '销售换货单' : '新增销售换货单' }}</span>
            <span v-if="form.exchangeNo" class="order-no">
              <el-icon><Collection /></el-icon>
              {{ form.exchangeNo }}
            </span>
            <el-tag v-if="form.approvalStatus" size="small" :type="approvalStatusType(form.approvalStatus)" effect="plain" class="status-tag">{{ $dt(form.approvalStatus) }}</el-tag>
            <el-tag v-if="form.status && !isDraft" size="small" :type="statusType(form.status)" effect="plain" class="status-tag">{{ $dt(form.status) }}</el-tag>
          </div>
        </div>
      </div>
      <div class="header-right">
        <el-button v-permission="perm('view')" v-if="form.exchangeId" @click="changeRecordVisible = true">
          <el-icon><Memo /></el-icon>{{ $tt('操作日志') }}
        </el-button>
        <BillActions bill-type="SALE_EXCHANGE"
          :supported-actions="supportedActions"
          :status="form.status"
          :close-status="form.closeStatus"
          :is-new="!form.exchangeId"
          :show-copy="false"
          @save="onSave"
          @submit="onSubmit"
          @approve="onApprove"
          @reject="handleReject"
          @reverse-approve="handleReverseApprove"
          @void="handleVoid"
          @delete="handleDelete"
          @print="handlePrint"
        />
      </div>
    </div>

    <!-- 顶级页签：单据 / 来源单据 / 去向单据 -->
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
        <el-form :model="form" :rules="rules" ref="headerFormRef" label-width="86px" class="base-form">
          <!-- 单据信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>单据信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('换货单号')" v-if="isFieldVisible('exchangeNo')">
                <el-input v-model="form.exchangeNo" :disabled="!isDraft" placeholder="保存后自动生成" class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('换货日期')" prop="exchangeDate" v-if="isFieldVisible('exchangeDate')" :required="isFieldRequired('exchangeDate')">
                <el-date-picker v-model="form.exchangeDate" type="date" value-format="YYYY-MM-DD" class="field-input" :disabled="!isDraft || !isFieldEditable('exchangeDate')" />
              </el-form-item>
              <el-form-item :label="$tf('来源订单号')" prop="sourceBillNo" v-if="isFieldVisible('sourceBillNo')">
                <el-input v-model="form.sourceBillNo" readonly class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('已生成出库单')" v-if="form.generatedDeliveryNo">
                <el-link type="primary" :underline="false" @click="goToGeneratedDelivery">{{ form.generatedDeliveryNo }}</el-link>
              </el-form-item>
            </div>
          </div>

          <!-- 客户信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>客户信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('客户')" prop="customerId" v-if="isFieldVisible('customerId')" :required="isFieldRequired('customerId')">
                <el-select v-model="form.customerId" filterable :placeholder="$tt('选择客户')" class="field-input" disabled>
                  <el-option v-for="c in withFallbackOption(customers, 'customerId', 'customerName', form)" :key="c.customerId" :label="c.customerName" :value="c.customerId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('销售员')" prop="salerId" v-if="isFieldVisible('salerId')" :required="isFieldRequired('salerId')">
                <el-select v-model="form.salerId" filterable :placeholder="$tt('选择销售员')" class="field-input" :disabled="!isDraft || !isFieldEditable('salerId')" @change="onSalerChange">
                  <el-option v-for="e in employees" :key="e.employeeId" :label="e.employeeName" :value="e.employeeId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('收货地址')" class="span-all" v-if="isFieldVisible('deliveryAddress')" :required="isFieldRequired('deliveryAddress')">
                <el-input v-model="form.deliveryAddress" class="field-input" :disabled="!isDraft || !isFieldEditable('deliveryAddress')" />
              </el-form-item>
            </div>
          </div>

          <!-- 交易信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>交易信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('币种')" prop="currencyId" v-if="isFieldVisible('currencyId')" :required="isFieldRequired('currencyId')">
                <el-select v-model="form.currencyId" :placeholder="$tt('选择币种')" class="field-input" :disabled="!isDraft || !foreignCurrencyEnabled || !isFieldEditable('currencyId')" @change="onCurrencyChange">
                  <el-option v-for="c in currencies" :key="c.currencyId" :label="c.currencyName" :value="c.currencyId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('汇率')" v-if="isFieldVisible('exchangeRate')" :required="isFieldRequired('exchangeRate')">
                <el-input-number v-model="form.exchangeRate" :precision="6" :step="0.1" :min="0" class="field-input" :disabled="!isDraft || !foreignCurrencyEnabled || !isFieldEditable('exchangeRate')" />
              </el-form-item>
              <el-form-item v-if="taxEnabled && isFieldVisible('taxCalcMethod')" :label="$tf('计税方式')" :required="isFieldRequired('taxCalcMethod')">
                <el-select v-model="form.taxCalcMethod" class="field-input" :disabled="!isDraft || !isFieldEditable('taxCalcMethod')" @change="onTaxCalcMethodChange">
                  <el-option v-for="o in taxMethodOptions" :key="o" :label="$tf(o)" :value="o" />
                </el-select>
              </el-form-item>
            </div>
          </div>

          <!-- 自定义字段（来自设置中心自定义字段定义，按所属分区成组渲染） -->
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
              <el-form-item :label="$tf('备注')" class="span-all" v-if="isFieldVisible('remark')" :required="isFieldRequired('remark')">
                <el-input v-model="form.remark" type="textarea" :rows="2" class="field-input" :disabled="!isDraft || !isFieldEditable('remark')" :placeholder="$tt('请输入备注信息')" />
              </el-form-item>
            </div>
          </div>
        </el-form>
      </div>
    </div>

    <!-- 换货说明 -->
    <el-alert
      class="exchange-tip"
      type="info"
      :closable="false"
      show-icon
      title="本单由销售订单『销售替代出库』生成，替换数量回写原订单行；待回库数量见订单台账。"
    />

    <!-- 明细卡片（通用组件 BillItemsTable） -->
    <BillItemsTable
      v-model:collapsed="collapsed.items"
      :items="form.items"
      page-key="SaleExchangeEdit_items"
      title="换货明细"
      subtitle="原货品与替换货品"
      col-setting-title="换货明细列设置"
      :columns="detailColumns"
      :custom-columns="itemCustomColumns"
      :readonly="!isDraft"
      :total-qty="totalQty"
      :summary-items="summaryItems"
      @row-change="onRowPriceChange"
      @remove="calcTotal"
    >
      <template #actions>
        <el-button v-permission="perm('pick')" size="small" @click="pickerVisible = true" :disabled="!isDraft">
          <el-icon><Grid /></el-icon>从物料库选择
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
          </el-option>
        </el-select>
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
      </template>
    </BillItemsTable>

    <!-- 物料库选择弹窗（通用组件：内置自定义字段搜索、全字段/自定义字段列、列设置全局共享 MaterialPicker） -->
    <MaterialPickerDialog v-model="pickerVisible" @confirm="onMaterialPicked" />

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
          type="sale-exchange"
          :disabled="!isDraft"
          button-text="上传附件"
          tip-text="支持图片、PDF、Word、Excel 等文件"
        />
      </div>
    </div>

    <!-- 审核流转（真实审批进度，通用组件） -->
    <ApprovalFlowTimeline bill-type="SALE_EXCHANGE" :bill-id="form.exchangeId" :status="form.status" />

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
    <ChangeRecordDialog v-model="changeRecordVisible" bill-type="销售换货单" :bill-no="form.exchangeNo || ''" />

    <!-- 打印预览 -->
    <PrintPreviewDialog v-model="printVisible" bill-type="SALE_EXCHANGE" :bill-id="form.exchangeId" />

      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('来源单据')" name="source" :disabled="!form.exchangeId">
        <BillLinkPanel v-if="topTab === 'source'" bill-type="SALE_EXCHANGE" :bill-id="form.exchangeId" mode="source" />
      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('去向单据')" name="target" :disabled="!form.exchangeId">
        <BillLinkPanel v-if="topTab === 'target'" bill-type="SALE_EXCHANGE" :bill-id="form.exchangeId" mode="target" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch, h } from 'vue'
import { useBaseDataRefresh } from '@/composables/useBaseDataRefresh'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { ArrowLeft, Document, Collection, ArrowRight, Memo, Grid } from '@element-plus/icons-vue'
import { getExchangeDetail, saveExchange, updateExchange, submitExchange, approveExchange, rejectExchange, reverseApproveExchange, voidExchange, deleteExchange } from '@/api/sale/saleExchange'
import { listAllCustomer } from '@/api/basedata/customer'
import { listAllMaterial } from '@/api/basedata/material'
import { listAllEmployee } from '@/api/basedata/employee'
import { listAllCurrency } from '@/api/basedata/currency'
import { listAllUnit } from '@/api/basedata/unit'
import { withFallbackOption } from '@/utils/labelFallback'
import { calcTaxAmount } from '@/utils/billTax'
import { filterMaterials, buildMaterialPlaceholder } from '@/utils/materialFilter'
import { isBillLinkRegistered } from '@/api/billLink'
import {
  loadCustomFieldDefinitions,
  loadCustomFieldValues,
  saveCustomFieldValues,
  batchLoadCustomFieldValues,
  customFieldsToTableColumns,
  remapItemIdsAfterSave
} from '@/composables/useCustomField'
import FileUploader from '@/components/FileUploader.vue'
import ApprovalFlowTimeline from '@/components/bill/ApprovalFlowTimeline.vue'
import BillLinkPanel from '@/components/bill/BillLinkPanel.vue'
import BillItemsTable from '@/components/bill/BillItemsTable.vue'
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
import BillActions from '@/components/bill/BillActions.vue'
import { useBillActions } from '@/composables/useBillActions'
import ChangeRecordDialog from '@/components/ChangeRecordDialog.vue'
import PrintPreviewDialog from '@/components/PrintPreviewDialog.vue'
import { statusType, approvalStatusType } from '@/composables/useBillStatus'
import { validateBillRequiredFields } from '@/utils/billValidation'
import { useCommercialSwitches } from '@/composables/useCommercialSwitches'
import { useSysConfig } from '@/composables/useSysConfig'
import { confirmSubmit, submitSuccessMessage } from '@/utils/submit-confirm'
import { triggerListRefresh } from '@/composables/useAutoRefresh'
import { useMaterialCustomFields } from '@/composables/useMaterialCustomFields'
import * as BillStatus from '@/utils/bill-status'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'
import { useFieldPermission } from '@/composables/useFieldPermission'
import { useFieldRequiredMark } from '@/utils/fieldRequiredMark'

const perm = usePagePerm('sale:exchanges')

// 字段权限：按「设置中心 → 角色权限 → 字段权限」隐藏 / 只读 / 必填星号（与销售订单、销售退货同口径）
const { isFieldVisible, isFieldRequired, isFieldEditable } = useFieldPermission('sale_exchange')
useFieldRequiredMark('sale_exchange', '.sale-exchange-edit')

const { taxEnabled, foreignCurrencyEnabled, ensureLoaded: ensureCommercialSwitches } = useCommercialSwitches()
const { loadSysConfig, getConfig } = useSysConfig()

// ===== 计税方式（含税价 / 不含税价 / 不计税）=====
const taxMethodOptions = ['不含税价', '含税价', '不计税']

function normalizeTaxMethod(v) {
  if (v === '含税价' || v === '价内税') return '含税价'
  if (v === '不计税') return '不计税'
  return '不含税价'
}

async function initTaxSettings() {
  try {
    await ensureCommercialSwitches()
    await loadSysConfig()
  } catch (e) {
    // 读取失败保持默认
  }
  if (!taxEnabled.value) {
    form.taxCalcMethod = '不计税'
  } else if (!form.exchangeId) {
    form.taxCalcMethod = normalizeTaxMethod(getConfig('sys.tax.method'))
  } else {
    form.taxCalcMethod = normalizeTaxMethod(form.taxCalcMethod)
  }
}

function onTaxCalcMethodChange() {
  form.items.forEach(row => calcRow(row))
  calcTotal()
}

const route = useRoute()
const router = useRouter()
const headerFormRef = ref()

const isEdit = computed(() => !!route.params.id)

const STORAGE_KEY = 'sale-exchange-edit-collapsed'
const collapsed = reactive({ base: true, items: true, attach: false })
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
watch(() => [collapsed.items, collapsed.attach], () => saveCollapsed())

const customers = ref([])
const materials = ref([])
const employees = ref([])
const currencies = ref([])
const units = ref([])

const mcf = useMaterialCustomFields()

/** 物料库选择弹窗显隐（弹窗本体 = 通用组件 MaterialPickerDialog） */
const pickerVisible = ref(false)

const changeRecordVisible = ref(false)
const printVisible = ref(false)

const form = reactive({
  exchangeId: null,
  exchangeNo: '',
  exchangeDate: new Date().toISOString().slice(0, 10),
  customerId: null,
  customerName: '',
  contactPerson: '',
  contactPhone: '',
  salerId: null,
  salerName: '',
  currencyId: null,
  currencyCode: 'CNY',
  exchangeRate: 1.0,
  taxCalcMethod: '不含税价',
  sourceBillType: '销售订单',
  sourceBillNo: '',
  sourceId: null,
  sourceDocId: null,
  deliveryAddress: '',
  remark: '',
  attachmentUrls: '',
  totalQuantity: 0,
  totalAmount: 0,
  taxAmount: 0,
  discountAmount: 0,
  netAmount: 0,
  generatedDeliveryId: null,
  generatedDeliveryNo: '',
  status: '草稿',
  approvalStatus: '',
  closeStatus: 0,
  items: []
})

const isDraft = computed(() => BillStatus.canEdit(form.status, form.closeStatus))

// ===== 自定义字段（表头 sale_exchange / 明细 sale_exchange_item，与后端实体登记同口径）=====
const headerCustomSections = ref([])
const headerCustomFieldMap = new Map()
const headerFormulaFields = ref([])
const itemCustomFields = ref([])
const itemCustomColumns = ref([])

async function loadCustomFields() {
  const headerResult = await loadCustomFieldDefinitions('sale_exchange')
  headerCustomSections.value = headerResult.sections
  headerCustomFieldMap.clear()
  headerResult.fieldMap.forEach((v, k) => headerCustomFieldMap.set(k, v))
  headerFormulaFields.value = headerResult.formulaFields

  const itemResult = await loadCustomFieldDefinitions('sale_exchange_item')
  itemCustomFields.value = Array.from(itemResult.fieldMap.values())
  itemCustomColumns.value = customFieldsToTableColumns(itemCustomFields.value, { editable: isDraft.value, listShowOnly: false })
}

// 草稿态变化时重新生成明细自定义字段列（editable 控制）
watch(isDraft, () => {
  if (itemCustomFields.value.length > 0) {
    itemCustomColumns.value = customFieldsToTableColumns(itemCustomFields.value, { editable: isDraft.value, listShowOnly: false })
  }
})

const totalQty = computed(() => form.items.reduce((sum, row) => sum + Number(row.quantity || 0), 0).toFixed(2))

const rules = {
  exchangeDate: [{ required: true, message: tt('请选择换货日期'), trigger: 'change' }],
  customerId: [{ required: true, message: tt('请选择客户'), trigger: 'change' }]
}

// 明细列定义：替换货品可编辑，原货品与原发货单号只读
const detailColumns = [
  { prop: 'materialMainImage', label: '图片', width: 60, align: 'center', type: 'image' },
  { prop: 'materialId', label: '替换货品', minWidth: 260 },
  { prop: 'materialCode', label: '替换货品编码', width: 140 },
  { prop: 'originMaterialCode', label: '原货品编码', width: 140, type: 'text' },
  { prop: 'originMaterialName', label: '原货品名称', minWidth: 180, type: 'text' },
  { prop: 'sourceDeliveryNo', label: '原发货单号', minWidth: 150, type: 'text' },
  { prop: 'specification', label: '规格', width: 110, type: 'text' },
  { prop: 'unit', label: '单位', width: 60, align: 'center', type: 'text' },
  { prop: 'quantity', label: '数量', width: 90, editor: 'number', precision: 4 },
  { prop: 'unitPrice', label: '单价', width: 90, editor: 'number', precision: 4 },
  { prop: 'taxRate', label: '税率%', width: 70, editor: 'number', precision: 0, min: 0, max: 100 },
  { prop: 'discountRate', label: '折扣率%', width: 80, editor: 'number', precision: 2, min: 0, max: 100 },
  { prop: 'discountAmount', label: '折扣额', width: 85, align: 'right', type: 'amount2', cellClass: 'discount-text' },
  { prop: 'amount', label: '金额', width: 85, align: 'right', type: 'amount2' },
  { prop: 'taxAmount', label: '税额', width: 85, align: 'right', type: 'amount2' },
  { prop: 'netAmount', label: '价税合计', width: 95, align: 'right', type: 'amount2', cellClass: 'net-amount' },
  { prop: 'remark', label: '备注', minWidth: 140, editor: 'text' }
]

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

// 物料下拉的过滤与提示（与其余单据同一份工具）
const materialSearchableFields = computed(() => (mcf.allFields.value || []).filter(f => f && (f.isSearch === 1 || (mcf.enabled.value && f.isDropShow === 1))))
const materialSelectPlaceholder = computed(() => buildMaterialPlaceholder(tt('选择替换货品'), materialSearchableFields.value))
function materialFilterMethod(val, row) {
  if (!val) {
    row._filteredMaterials = null
    return
  }
  row._filteredMaterials = filterMaterials(materials.value, val, mcf.valueMap.value, materialSearchableFields.value)
}

function onSalerChange(id) {
  const e = employees.value.find(x => x.employeeId === id)
  if (e) form.salerName = e.employeeName
}

function onCurrencyChange(id) {
  const c = currencies.value.find(x => x.currencyId === id)
  if (c) {
    form.currencyCode = c.currencyCode
    if (c.exchangeRate) form.exchangeRate = c.exchangeRate
  }
}

/** 替换货品变更：回填编码/名称/规格/单位/图片，并校验与原货品基本单位一致 */
function onMaterialChange(row, id) {
  const m = materials.value.find(x => x.materialId === id)
  if (!m) return
  row.materialCode = m.materialCode
  row.materialName = m.materialName
  row.materialMainImage = m.mainImage || ''
  row.specification = m.model
  row.unitId = m.baseUnitId || null
  const u = units.value.find(x => x.unitId === row.unitId)
  row.unit = u ? u.unitName : ''
  // 与原货品基本单位一致性校验（最终以后端拦截为准）
  const origin = materials.value.find(x => x.materialId === row.originMaterialId)
  if (origin && origin.baseUnitId && m.baseUnitId && origin.baseUnitId !== m.baseUnitId) {
    ElMessage.warning(`${tt('替换货品「')}${m.materialName}${tt('」与原货品基本单位不一致，保存时将被拒绝')}`)
  }
  calcRow(row)
}

/** 物料库弹窗「确认添加」回调（弹窗本体 = 通用组件 MaterialPickerDialog） */
function onMaterialPicked({ rows: picked, valueMap }) {
  if (!picked || picked.length === 0) return
  if (valueMap) Object.assign(mcf.valueMap.value, valueMap)
  picked.forEach(m => {
    const row = {
      materialId: m.materialId,
      materialCode: m.materialCode || '',
      materialName: m.materialName || '',
      materialMainImage: m.mainImage || '',
      specification: m.model || '',
      unitId: m.baseUnitId || null,
      unit: m.baseUnitName || '',
      quantity: 1,
      unitPrice: Number(m.salePrice || 0),
      taxRate: m.taxRate != null ? Number(m.taxRate) : 13,
      discountRate: 0,
      discountAmount: 0,
      amount: 0,
      taxAmount: 0,
      netAmount: 0,
      originMaterialId: null,
      originMaterialCode: '',
      originMaterialName: '',
      originSpecification: '',
      originUnit: '',
      originUnitPrice: 0,
      sourceDeliveryNo: '',
      remark: ''
    }
    form.items.push(row)
    calcRow(row)
  })
  pickerVisible.value = false
  ElMessage.success(`${tt('已添加 ')}${picked.length}${tt(' 条明细')}`)
}

function calcRow(row) {
  const qty = Number(row.quantity || 0)
  const price = Number(row.unitPrice || 0)
  const taxRate = Number(row.taxRate || 0)
  const discountRate = Number(row.discountRate || 0)
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
  const originalAmount = inclusive ? (price * qty) / factor : price * qty
  row.discountAmount = (originalAmount * discountRate / 100).toFixed(2)
  row.amount = (originalAmount - Number(row.discountAmount)).toFixed(2)
  row.taxAmount = calcTaxAmount({ amount: row.amount, unitPrice: price, quantity: qty, taxRate, discountRate, inclusive, noTax }).toFixed(2)
  row.netAmount = (Number(row.amount) + Number(row.taxAmount)).toFixed(2)
  calcTotal()
}

function onRowPriceChange(row) {
  calcRow(row)
}

function calcTotal() {
  let totalAmount = 0
  let discountAmount = 0
  let taxAmount = 0
  let totalQuantity = 0
  form.items.forEach(i => {
    totalQuantity += Number(i.quantity || 0)
    const amt = Number(i.amount || 0)
    const disc = Number(i.discountAmount || 0)
    totalAmount += amt + disc
    discountAmount += disc
    taxAmount += Number(i.taxAmount || 0)
  })
  form.totalQuantity = totalQuantity.toFixed(2)
  form.totalAmount = totalAmount.toFixed(2)
  form.discountAmount = discountAmount.toFixed(2)
  form.taxAmount = taxAmount.toFixed(2)
  form.netAmount = (totalAmount - discountAmount + taxAmount).toFixed(2)
}

function flushInputNumbers() {
  if (!form.items || form.items.length === 0) return
  form.items.forEach(row => {
    if (row.quantity !== undefined) row.quantity = Number(row.quantity || 0)
    if (row.unitPrice !== undefined) row.unitPrice = Number(row.unitPrice || 0)
    if (row.taxRate !== undefined) row.taxRate = Number(row.taxRate || 0)
    if (row.discountRate !== undefined) row.discountRate = Number(row.discountRate || 0)
    if (row.discountAmount !== undefined) row.discountAmount = Number(row.discountAmount || 0)
    if (row.amount !== undefined) row.amount = Number(row.amount || 0)
    if (row.taxAmount !== undefined) row.taxAmount = Number(row.taxAmount || 0)
    if (row.netAmount !== undefined) row.netAmount = Number(row.netAmount || 0)
  })
  form.items = [...form.items]
}

function goToGeneratedDelivery() {
  if (form.generatedDeliveryId) router.push(`/sale/deliveries/${form.generatedDeliveryId}`)
}

async function onSave(silent, opts = {}) {
  flushInputNumbers()
  if (form.status && !isDraft.value) {
    ElMessage.warning(`${tt("当前状态为「")}${form.status}${tt("」, 不可修改, 只有草稿状态可保存")}`)
    return false
  }
  form.items = (form.items || []).filter(r => r && r.materialId)
  // 保存前按当前表头计税方式刷算（与后端落库口径一致）
  form.items.forEach(row => calcRow(row))
  calcTotal()
  try {
    if (form.exchangeId) {
      await updateExchange(form.exchangeId, form)
    } else {
      const res = await saveExchange(form)
      if (res && res.exchangeId) form.exchangeId = res.exchangeId
      if (res && res.exchangeNo) form.exchangeNo = res.exchangeNo
      if (res && res.status) form.status = res.status
      if (res && res.approvalStatus) form.approvalStatus = res.approvalStatus
      if (!opts.skipRouteSync && !route.params.id && form.exchangeId) {
        try { await router.replace({ name: 'SaleExchangeEdit', params: { id: String(form.exchangeId) } }) } catch (_) {}
      }
    }
    if (headerCustomFieldMap.size > 0 && form.exchangeId) {
      const headerValues = {}
      for (const key of headerCustomFieldMap.keys()) {
        if (key in form) headerValues[key] = form[key]
      }
      await saveCustomFieldValues('sale_exchange', form.exchangeId, headerValues, headerFormulaFields.value)
    }
    if (itemCustomFields.value.length > 0 && form.items.length > 0) {
      // 保存主表时后端走「明细先删后插」，明细主键已变，必须先按新主键对齐再写明细自定义字段
      try {
        const fresh = await getExchangeDetail(form.exchangeId)
        remapItemIdsAfterSave(form.items, (fresh && fresh.items) || [], 'exchangeItemId')
      } catch (e) {
        console.warn('[saleExchange] 保存后刷新明细主键失败，明细级自定义字段可能挂到旧行', e)
      }
      const itemKeys = new Set(itemCustomFields.value.map(f => f.fieldKey))
      for (const row of form.items) {
        if (!row.exchangeItemId) continue
        const values = {}
        for (const key of itemKeys) {
          if (key in row) values[key] = row[key]
        }
        await saveCustomFieldValues('sale_exchange_item', row.exchangeItemId, values)
      }
    }
    triggerListRefresh('/sale/exchanges')
    if (!silent) ElMessage.success(tt('保存成功'))
    return true
  } catch (e) {
    ElMessage.error(e.message || tt('保存失败'))
    return false
  }
}

async function onSubmit() {
  flushInputNumbers()
  const missing = await validateBillRequiredFields({ headerFormRef, rules, $el: document, pageKey: 'sale_exchange', model: form })
  if (missing.length) {
    ElNotification.error({
      title: tt('请完善必填字段后再提交'),
      message: h('div', { style: { fontSize: '13px', lineHeight: '1.6' } }, [
        h('div', { style: { marginBottom: '6px', fontWeight: 600, color: '#f56c6c' } }, tt('共 ') + missing.length + tt(' 个字段未填写：')),
        h('ul', { style: { margin: 0, paddingLeft: '18px', color: '#606266' } },
          missing.map(m => h('li', { style: { marginBottom: '2px' } }, m.label || m.prop || tt('未命名字段')))
        )
      ]),
      duration: 5000,
      offset: 60
    })
    return
  }
  if (!form.items.length) {
    ElMessage.warning(tt('请至少保留一行换货明细'))
    return
  }
  const saved = await onSave(true, { skipRouteSync: true })
  if (!saved || !form.exchangeId) return
  try {
    const needApproval = await confirmSubmit({ billType: 'SALE_EXCHANGE', billLabel: '销售换货单' })
    await submitExchange(form.exchangeId)
    ElMessage.success(submitSuccessMessage(needApproval))
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('提交失败'))
  } finally {
    if (!route.params.id && form.exchangeId) {
      try { await router.replace({ name: 'SaleExchangeEdit', params: { id: String(form.exchangeId) } }) } catch (_) {}
    }
  }
}

function handlePrint() {
  if (!form.exchangeId) {
    ElMessage.warning(tt('请先保存换货单再打印'))
    return
  }
  printVisible.value = true
}

async function load() {
  const detailId = route.params.id || form.exchangeId || ''
  if (!detailId) return
  try {
    const res = await getExchangeDetail(detailId)
    Object.assign(form, res)
    if (!form.items) form.items = []
    if (headerCustomFieldMap.size > 0 && res.exchangeId) {
      const headerValues = await loadCustomFieldValues('sale_exchange', res.exchangeId)
      Object.assign(form, headerValues)
    }
    if (itemCustomFields.value.length > 0 && form.items.length > 0) {
      const itemIds = form.items.map(it => it.exchangeItemId).filter(Boolean)
      if (itemIds.length > 0) {
        const itemValuesMap = await batchLoadCustomFieldValues('sale_exchange_item', itemIds)
        form.items.forEach(row => {
          const values = itemValuesMap[row.exchangeItemId]
          if (values) Object.assign(row, values)
        })
      }
    }
    await initTaxSettings()
    // 打开单据按数据库原值回显：只汇总，不逐行重算
    calcTotal()
  } catch (e) {
    ElMessage.error(e.message || tt('加载失败'))
  }
}

// ===== 统一单据操作（审核/驳回/重置草稿/作废/删除）=====
const {
  supportedActions, rejectDialog, rejectReason, doReject,
  voidDialog, voidReason, doVoid,
  onApprove, handleReject, handleReverseApprove, handleVoid, handleDelete
} = useBillActions({
  form,
  getId: () => form.exchangeId,
  api: {
    approve: approveExchange,
    reject: rejectExchange,
    reverseApprove: reverseApproveExchange,
    void: voidExchange,
    delete: deleteExchange
  },
  reload: load,
  router,
  listPath: '/sale/exchanges',
  billLabel: '销售换货单'
})

async function loadBaseData() {
  try {
    const [custRes, matRes, empRes, curRes, unitRes] = await Promise.all([
      listAllCustomer(),
      listAllMaterial(),
      listAllEmployee(),
      listAllCurrency(),
      listAllUnit()
    ])
    customers.value = custRes || []
    materials.value = matRes || []
    mcf.preloadAll(materials.value)
    employees.value = empRes || []
    currencies.value = curRes || []
    units.value = unitRes || []
    if (!form.currencyId && currencies.value.length > 0) {
      let target = currencies.value[0]
      if (!foreignCurrencyEnabled.value) {
        target = currencies.value.find(c => c.isBaseCurrency === 1 || c.isBaseCurrency === true) || currencies.value[0]
      }
      form.currencyId = target.currencyId
      onCurrencyChange(form.currencyId)
      if (!foreignCurrencyEnabled.value) form.exchangeRate = 1
    }
  } catch (e) {
    console.warn('下拉数据加载失败:', e)
  }
}

// ===== 顶级页签 =====
const topTab = ref('doc')
const billLinkRegistered = ref(false)

onMounted(async () => {
  await ensureCommercialSwitches()
  await mcf.init()
  await loadCustomFields()
  loadCollapsed()
  isBillLinkRegistered('SALE_EXCHANGE')
    .then(v => { billLinkRegistered.value = !!v })
    .catch(() => { billLinkRegistered.value = false })
  await loadBaseData()
  await load()
})

useBaseDataRefresh(loadBaseData)
</script>

<style scoped lang="scss">
.sale-exchange-edit {
  padding: 12px 16px;
  background: #f5f7fa;
  min-height: calc(100vh - 60px);
  font-size: 12px;
}

.exchange-tip {
  margin-bottom: 10px;
}

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

  .title-area .title-row {
    display: flex;
    align-items: center;
    gap: 6px;
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
  }

  .card-body { padding: 12px 14px; }
}

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

  :deep(.el-form-item) { margin-bottom: 6px; }
  :deep(.el-form-item__label) { font-size: 12px; color: #606266; line-height: 28px; }
  :deep(.el-form-item__content) { line-height: 28px; }

  .span-all { grid-column: span 4; }
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
  :deep(.el-input-number) {
    width: 100%;
    .el-input__wrapper { min-height: 28px; padding: 0 6px; }
  }
  :deep(.el-date-editor) { width: 100%; }
}

.table-select { width: 100%; }
.opt-code { color: #909399; margin-right: 6px; }
.opt-name { color: #303133; }
</style>
