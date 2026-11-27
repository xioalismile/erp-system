<template>
  <div class="produce-assemble-edit">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div class="header-left">
        <div class="back-btn" @click="$router.push('/production/assemble')">
          <el-icon><ArrowLeft /></el-icon>
          <span>{{ $tt("返回列表") }}</span>
        </div>
        <div class="title-area">
          <div class="title-row">
            <el-icon class="title-icon"><Document /></el-icon>
            <span class="page-title">{{ isEdit ? '生产组装单' : '新增生产组装单' }}</span>
            <span v-if="form.serial_no" class="order-no">
              <el-icon><Collection /></el-icon>
              {{ form.serial_no }}
            </span>
            <el-tag v-if="form.id" size="small" :type="approvalStatusType(getStatusText(form.status))" effect="plain" class="status-tag">{{ $dt(getStatusText(form.status)) }}</el-tag>
          </div>
        </div>
      </div>
      <div class="header-right">
        <el-button v-permission="perm('view')" v-if="!(!form.orderId && !form.deliveryId && !form.inboundId && !form.invoiceId && !form.voucherId && !form.contractId && !form.requisitionId && !form.stockCheckId && !form.transferId && !form.bomId && !form.sampleId && !form.freeSampleId && !form.workOrderId && !form.billId && !form.id)" @click="changeRecordVisible = true">
          <el-icon><Memo /></el-icon>{{ $tt('操作日志') }}
        </el-button>
        <BillActions bill-type="PRODUCE_ASSEMBLE"
          :status="form.status"
          :close-status="form.close_status"
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

    <!-- 基本信息卡片（可折叠） -->
    <div class="collapsible-card" :class="{ collapsed: !collapsed.base }">
      <div class="card-header" @click="toggleCard('base')">
        <div class="header-left">
          <el-icon class="collapse-icon" :class="{ active: collapsed.base }"><ArrowRight /></el-icon>
          <span class="card-title">{{ $tt("基本信息") }}</span>
        </div>
      </div>
      <div v-show="collapsed.base" class="card-body">
        <el-form :model="form" :rules="rules" ref="formRef" label-width="80px" class="base-form">
          <!-- 单据信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>单据信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('单据编号')">
                <el-input v-model="form.serial_no" :placeholder="docCodePlaceholder" :disabled="!isDraft" class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('单据日期')">
                <el-date-picker v-model="form.dateline" type="date" value-format="YYYY-MM-DD" class="field-input" :disabled="!canEdit" />
              </el-form-item>
              <el-form-item :label="$tf('生产部门')">
                <el-input v-model="form.dept" readonly :placeholder="$tt('自动带出')" class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('成品仓库')" prop="wh_id">
                <el-select v-model="form.wh_id" filterable :placeholder="$tt('选择成品仓库')" class="field-input" :disabled="!canEdit" @change="onWarehouseChange">
                  <el-option v-for="w in withFallbackOption(warehouses, 'wh_id', 'warehouseName', form)" :key="w.stockId" :label="w.stockName" :value="w.stockId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('原料仓库')" prop="wh_id_bom">
                <el-select v-model="form.wh_id_bom" filterable :placeholder="$tt('选择原料仓库')" class="field-input" :disabled="!canEdit" @change="onWarehouseBomChange">
                  <el-option v-for="w in withFallbackOption(warehouses, 'wh_id_bom', 'warehouseName', form)" :key="w.stockId" :label="w.stockName" :value="w.stockId" />
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
              <!-- 计税方式（2026-10-09 · 全站统一三选口径）：表头唯一控件，驱动明细「含税价 / 不含税价」两列换算；旧「是否含税/两选」控件已废弃，由本项派生 is_tax/tax_type 照常提交 -->
              <el-form-item v-if="taxEnabled" :label="$tf('计税方式')">
                <el-select v-model="form.tax_calc_method" class="field-input" :disabled="!canEdit" @change="onTaxCalcMethodChange">
                  <el-option v-for="o in taxMethodOptions" :key="o" :label="$tf(o)" :value="o" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('交易币别')">
                <el-select v-model="form.currency_id" filterable :placeholder="$tt('选择币别')" class="field-input" :disabled="!canEdit || !foreignCurrencyEnabled">
                  <el-option v-for="c in currencies" :key="c.currencyId" :label="c.currencyCode" :value="c.currencyId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('汇率')">
                <el-input-number v-model="form.currency_rate" :precision="6" :min="0" class="field-input" :disabled="!canEdit || !foreignCurrencyEnabled" />
              </el-form-item>
              <el-form-item :label="$tf('单据金额')">
                <el-input-number v-model="form.amount" :precision="2" disabled class="field-input" />
              </el-form-item>
              <el-form-item v-if="taxEnabled" :label="$tf('税率%')">
                <el-select v-model="form.tax_rate" :placeholder="$tt('选择税率')" class="field-input" :disabled="!form.is_tax || form.tax_type === 0">
                  <el-option label="0%" :value="0" />
                  <el-option label="3%" :value="3" />
                  <el-option label="6%" :value="6" />
                  <el-option label="9%" :value="9" />
                  <el-option label="13%" :value="13" />
                </el-select>
              </el-form-item>
            </div>
          </div>

          <!-- 自定义字段分区 -->
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
                  :disabled="!canEdit"
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
                  :disabled="!canEdit"
                />
                <el-switch
                  v-else-if="field.type === 'switch'"
                  v-model="form[field.prop]"
                  :active-value="field.activeValue !== undefined ? field.activeValue : 1"
                  :inactive-value="field.inactiveValue !== undefined ? field.inactiveValue : 0"
                  :disabled="!canEdit"
                />
                <el-date-picker
                  v-else-if="field.type === 'date'"
                  v-model="form[field.prop]"
                  type="date"
                  value-format="YYYY-MM-DD"
                  :placeholder="field.placeholder || '选择日期'"
                  class="field-input"
                  :disabled="!canEdit"
                />
                <el-input
                  v-else-if="field.type === 'textarea'"
                  v-model="form[field.prop]"
                  type="textarea"
                  :rows="field.rows || 3"
                  :placeholder="field.placeholder || '请输入'"
                  class="field-input"
                  :disabled="!canEdit"
                />
                <el-input
                  v-else
                  v-model="form[field.prop]"
                  :placeholder="field.placeholder || '请输入'"
                  class="field-input"
                  :disabled="!canEdit"
                />
              </el-form-item>
            </div>
          </div>

          <!-- 单据信息（创建/修改） -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>单据追溯
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('创建人')">
                <el-input v-model="form.creator_str" readonly class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('创建时间')">
                <el-input v-model="form.ctime" readonly class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('修改人')">
                <el-input v-model="form.modifier_str" readonly class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('修改时间')">
                <el-input v-model="form.utime" readonly class="field-input" />
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
                <el-input v-model="form.notes" type="textarea" :rows="2" class="field-input" :disabled="!canEdit" :placeholder="$tt('请输入备注信息')" />
              </el-form-item>
            </div>
          </div>
        </el-form>
      </div>
    </div>

    <!-- 成品明细卡片（通用组件 BillItemsTable：卡片/列设置/动态列/汇总 全站共用一套） -->
    <BillItemsTable
      v-model:collapsed="collapsed.detail"
      :items="form.produce_assemble_detail"
      page-key="ProduceAssembleEdit_detail"
      title="成品明细（入库）"
      col-setting-title="成品明细列设置"
      :columns="productColumns"
      :custom-columns="[]"
      :readonly="!canEdit"
      :show-total-qty="false"
      :summary-items="productSummaryItems"
      @row-change="calcTotal"
      @remove="calcTotal"
    >
      <template #actions>
        <el-button v-permission="perm('pick')" size="small" @click="pickerVisible = true" :disabled="!canEdit">
          <el-icon><Grid /></el-icon>从物料库选择
        </el-button>
        <el-button v-permission="perm('addRow')" size="small" type="primary" @click="addProductRow" :disabled="!canEdit">
          <el-icon><Plus /></el-icon>添加成品
        </el-button>
      </template>
      <template #cell-product_id="{ row }">
        <el-select v-model="row.product_id" filterable :placeholder="materialSelectPlaceholder" :disabled="!canEdit" @change="(v) => onProductChange(row, v)" :filter-method="(val) => materialFilterMethod(val, row)" class="table-select" size="small" popper-class="material-select-popper">
          <el-option v-for="m in (row._filteredMaterials || materials)" :key="m.materialId" :label="m.materialCode" :value="m.materialId">
            <span class="opt-code">{{ m.materialCode }}</span>
            <span class="opt-name">{{ m.materialName }}</span>
            <span v-for="cf in mcf.dropText(m)" :key="cf.key" class="opt-cf">{{ cf.label }}：{{ cf.value }}</span>
          </el-option>
        </el-select>
      </template>
      <template #cell-basic_quantity="{ row }">
        <el-input-number v-model="row.basic_quantity" :controls="false" :min="0" :precision="4" style="width:100%" size="small" disabled />
      </template>
      <!-- 多单位 6 列由明细通用组件自动注入并默认渲染（与销售订单同源，2026-11-11 统一） -->
    </BillItemsTable>

    <!-- BOM原料明细卡片（通用组件 BillItemsTable：卡片/列设置/动态列/汇总 全站共用一套） -->
    <BillItemsTable
      v-model:collapsed="collapsed.bom"
      :items="form.produce_assemble_bom"
      page-key="ProduceAssembleEdit_bom"
      title="BOM原料明细（出库）"
      col-setting-title="BOM原料明细列设置"
      :columns="bomColumns"
      :custom-columns="[]"
      :readonly="!canEdit"
      :show-total-qty="false"
      :summary-items="bomSummaryItems"
    >
      <template #actions>
        <el-button v-permission="perm('addRow')" size="small" type="primary" @click="addBomRow" :disabled="!canEdit">
          <el-icon><Plus /></el-icon>添加原料
        </el-button>
      </template>
      <template #cell-product_id="{ row }">
        <el-select v-model="row.product_id" filterable :placeholder="materialSelectPlaceholder" :disabled="!canEdit" @change="(v) => onBomProductChange(row, v)" :filter-method="(val) => materialFilterMethod(val, row)" class="table-select" size="small">
          <el-option v-for="m in (row._filteredMaterials || materials)" :key="m.materialId" :label="m.materialCode" :value="m.materialId">
            <span class="opt-code">{{ m.materialCode }}</span>
            <span class="opt-name">{{ m.materialName }}</span>
            <span v-for="cf in mcf.dropText(m)" :key="cf.key" class="opt-cf">{{ cf.label }}：{{ cf.value }}</span>
          </el-option>
        </el-select>
      </template>
      <template #cell-basic_quantity="{ row }">
        <el-input-number v-model="row.basic_quantity" :controls="false" :min="0" :precision="4" style="width:100%" size="small" disabled />
      </template>
      <!-- 多单位 6 列由明细通用组件自动注入并默认渲染（与销售订单同源，2026-11-11 统一） -->
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
          v-model="form.files"
          type="produce-assemble"
          :disabled="!canEdit"
          button-text="上传附件"
          tip-text="支持图片、PDF、Word、Excel 等文件"
        />
      </div>
    </div>

    <!-- 审核流转（真实审批进度，通用组件） -->
    <ApprovalFlowTimeline bill-type="PRODUCE_ASSEMBLE" :bill-id="form.id" :status="form.status" />

    <!-- 审批弹窗 -->
    <el-dialog v-model="approveDialog" :title="$tf('审批')" width="420px">
      <el-form label-width="80px">
        <el-form-item :label="$tf('审批意见')">
          <el-radio-group v-model="approveAction">
            <el-radio value="approve">{{ $tt("同意") }}</el-radio>
            <el-radio value="reject">{{ $tt("驳回") }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="$tf('备注')">
          <el-input v-model="approveRemark" type="textarea" :rows="3" :placeholder="$tt('请输入审批意见')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="approveDialog = false">{{ $tt("取消") }}</el-button>
        <el-button v-permission="perm('dlgConfirm')" type="primary" @click="doApprove">{{ $tt("确定") }}</el-button>
      </template>
    </el-dialog>

    <!-- 作废弹窗 -->
    <el-dialog v-model="cancelDialog" :title="$tf('作废组装单')" width="420px">
      <el-form label-width="80px">
        <el-form-item :label="$tf('作废原因')">
          <el-input v-model="cancelReason" type="textarea" :rows="3" :placeholder="$tt('请输入作废原因')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="cancelDialog = false">{{ $tt("返回") }}</el-button>
        <el-button v-permission="perm('dlgConfirm')" type="danger" @click="doCancel">{{ $tt("确认作废") }}</el-button>
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
    <ChangeRecordDialog v-model="changeRecordVisible" bill-type="生产组装" :bill-no="form.serial_no || ''" />

    <!-- 打印预览弹窗 -->
    <PrintPreviewDialog v-model="printVisible" bill-type="PRODUCE_ASSEMBLE" :bill-id="form.id" />
  </div>

  </template>

<script setup>
import { ref, reactive, computed, onMounted, watch, h} from 'vue'
import { withFallbackOption } from '@/utils/labelFallback'
import { useBaseDataRefresh } from '@/composables/useBaseDataRefresh'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification} from 'element-plus'
import { ArrowLeft, Document, Collection, Plus, Grid, Printer, Link,
  Check, TopRight, ArrowDown, ArrowRight, CircleCheck, CircleClose, RefreshLeft,
  Share, Close, Upload, Edit, Clock, Memo
} from '@element-plus/icons-vue'
import {
  getProduceAssemble,
  createProduceAssemble,
  updateProduceAssemble,
  deleteProduceAssemble,
  submitProduceAssemble,
  approveProduceAssemble,
  rejectProduceAssemble,
  revokeProduceAssemble,
  cancelProduceAssemble,
  resetDraftProduceAssemble,
  copyProduceAssemble,
  saveProduceAssemble,
  reverseApproveProduceAssemble,
  closeOrderProduceAssemble,
  reopenProduceAssemble,
  unvoidProduceAssemble
} from '@/api/production/produceAssemble'
import { listAllMaterial } from '@/api/basedata/material'
import { listAllWarehouse } from '@/api/basedata/warehouse'
import { listAllCurrency } from '@/api/basedata/currency'
import { listAllBom, getBom } from '@/api/basedata/bom'
import {
  loadCustomFieldDefinitions,
  loadCustomFieldValues,
  saveCustomFieldValues
} from '@/composables/useCustomField'
import FileUploader from '@/components/FileUploader.vue'
import ChangeRecordDialog from '@/components/ChangeRecordDialog.vue'
import PrintPreviewDialog from '@/components/PrintPreviewDialog.vue'
import ApprovalFlowTimeline from '@/components/bill/ApprovalFlowTimeline.vue'
// 明细通用组件（卡片折叠/列设置/动态列/汇总 全站共用一套）
import BillItemsTable from '@/components/bill/BillItemsTable.vue'
// 多单位换算 5 列（2026-11-10）：列定义与取值逻辑只有通用模块一份，本页只做列注入 + 行字段对齐
import { useMultiUnitCells } from '@/components/bill/multiUnitColumns'
import { loadConversions, loadFloatUnitEnabled } from '@/composables/useUnitConvert'
import { useAutoDocCode } from '@/composables/useAutoDocCode'

import * as BillStatus from '@/utils/bill-status'
import BillActions from '@/components/bill/BillActions.vue'
import { validateBillRequiredFields } from '@/utils/billValidation'
import { useFieldPermission } from '@/composables/useFieldPermission'
import { useFieldRequiredMark } from '@/utils/fieldRequiredMark'
import { confirmSubmit, submitSuccessMessage } from '@/utils/submit-confirm'
import { triggerListRefresh } from '@/composables/useAutoRefresh'
import { useCopyBill } from '@/composables/useCopyBill'
import { tt } from '@/i18n'
import { useMaterialCustomFields } from '@/composables/useMaterialCustomFields'
// 物料库选择弹窗（通用件）：内置自定义字段搜索、全字段/自定义字段列、列设置全局共享 MaterialPicker
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
// 物料下拉的输入过滤 + 背景提示文字（通用件）
import { filterMaterials, buildMaterialPlaceholder } from '@/utils/materialFilter'
import { createAutoFillToken, hasFilledRows } from '@/utils/bomAutoFill'
import { usePagePerm } from '@/utils/pagePerm'
// 全站商业口径总开关（设置中心：启用含税业务 / 启用外币业务）
import { useCommercialSwitches } from '@/composables/useCommercialSwitches'
// 设置中心参数读取（「默认计税方式」）
import { useSysConfig } from '@/composables/useSysConfig'

const perm = usePagePerm('production:assemble')

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

const route = useRoute()
const router = useRouter()
const formRef = ref()

// 是否编辑模式
const isEdit = computed(() => !!route.params.id)

// 仅草稿态允许编辑（单号/业务字段/自定义字段）；已生效/待审批等只读
// 折叠状态记忆
const STORAGE_KEY = 'produce-assemble-edit-collapsed'
const { docCodeDisabled, docCodePlaceholder, autoFillDocCodeOnCreate } = useAutoDocCode('PRODUCE_ASSEMBLE')
const collapsed = reactive({
  base: true,
  detail: true,
  bom: true,
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

// 两张明细卡片的折叠由通用组件 v-model 驱动，这里监听后持久化（与页头卡片共用同一份 localStorage）
watch(() => [collapsed.detail, collapsed.bom], () => saveCollapsed())

const headerCustomSections = ref([])
const headerCustomFieldMap = new Map()
const headerFormulaFields = ref([])

async function loadCustomFields() {
  const headerResult = await loadCustomFieldDefinitions('produce_assemble')
  headerCustomSections.value = headerResult.sections
  headerCustomFieldMap.clear()
  headerResult.fieldMap.forEach((v, k) => headerCustomFieldMap.set(k, v))
  headerFormulaFields.value = headerResult.formulaFields
}

const warehouses = ref([])
const materials = ref([])
// BOM 全量列表：选完成品物料后按「默认BOM」自动带出原料明细（2026-10-26）
const bomOptions = ref([])
// 物料自定义字段：行内下拉附加展示 + 物料库弹窗全字段（逻辑集中在 composable）
const mcf = useMaterialCustomFields()
const currencies = ref([])

const pickerVisible = ref(false)

const approveDialog = ref(false)
const approveAction = ref('approve')
const approveRemark = ref('')

const cancelDialog = ref(false)
const cancelReason = ref('')
const changeRecordVisible = ref(false)

const form = reactive({
  id: null,
  serial_no: '',
  dateline: new Date().toISOString().slice(0, 10),
  dept: '',
  wh_id: null,
  wh_name: '',
  wh_id_bom: null,
  wh_name_bom: '',
  is_tax: true,
  tax_type: 1,
  tax_calc_method: '不含税价',
  currency_id: null,
  currency_rate: 1,
  amount: 0,
  tax_amount: 0,
  tax_rate: 13,
  notes: '',
  status: 0,
  creator_str: '',
  ctime: '',
  modifier_str: '',
  utime: '',
  etime: '',
  files: '',
  produce_assemble_detail: [],
  produce_assemble_bom: []
})


const isDraft = computed(() => BillStatus.canEdit(form.status, form.close_status))
const rules = {
  wh_id: [{ required: true, message: tt('请选择成品仓库'), trigger: 'change' }],
  wh_id_bom: [{ required: true, message: tt('请选择原料仓库'), trigger: 'change' }]
}

// 仅草稿态允许编辑（单号/业务字段/自定义字段）；已生效/待审批等只读
// 2026-09-20 修复（既有缺陷）：原实现额外要求 route.query.edit === '1'，但列表页「新增」跳转是
//   /production/assemble/new（不带该参数），且页面内没有"编辑"入口去补它 →
//   新增页与草稿页的主表字段、明细按钮全部被禁用（本页单号字段用 !isDraft 却是可编辑的，两者自相矛盾）。
//   去掉该合取项后口径与本页注释、以及 isDraft 一致：新增/草稿可编辑，已生效/已作废只读。
const canEdit = computed(() => !form.id || form.status === 0)

const canApprove = computed(() => form.status === 1)
const canCopy = computed(() => form.id && form.status !== 0)

const statusMap = {
  0: { text: '草稿', color: '#d9d9d9' },
  1: { text: '待审核', color: '#1890ff' },
  2: { text: '已生效', color: '#52c41a' },
  7: { text: '已作废', color: '#f5222d' }
}

function getStatusText(status) {
  return statusMap[status]?.text || '未知'
}

function getStatusColor(status) {
  return statusMap[status]?.color || '#d9d9d9'
}

// 审核状态标签类型
function approvalStatusType(s) {
  return {
    '待审核': 'warning',
    '已生效': 'success',
    '已取消': 'danger',
    '草稿': 'info',
    '待审批': 'warning',
    '已作废': 'danger'
  }[s] || 'info'
}

// 成品合计数量
const totalQty = computed(() => {
  return form.produce_assemble_detail.reduce((s, r) => s + (Number(r.quantity) || 0), 0).toFixed(2)
})

// 原料合计数量
const bomTotalQty = computed(() => {
  return form.produce_assemble_bom.reduce((s, r) => s + (Number(r.quantity) || 0), 0).toFixed(2)
})

function onWarehouseChange(id) {
  const w = warehouses.value.find(x => x.stockId === id)
  if (w) {
    form.wh_name = w.stockName
    form.produce_assemble_detail.forEach(row => {
      row.wh_id = w.stockId
      row.wh_name = w.stockName
    })
  }
}

function onWarehouseBomChange(id) {
  const w = warehouses.value.find(x => x.stockId === id)
  if (w) {
    form.wh_name_bom = w.stockName
    form.produce_assemble_bom.forEach(row => {
      row.wh_id = w.stockId
      row.wh_name = w.stockName
    })
  }
}

function onProductChange(row, id) {
  const m = materials.value.find(x => x.materialId === id)
  if (m) {
    row.product_code = m.materialCode
    row.product_name = m.materialName
    row.specification = m.model || ''
    // 单位与单位ID：一律取物料档案的基本单位（组装单库存按基本单位记账；副单位/副单位数量由通用能力实时折算）
    row.unit = m.baseUnitName || ''
    row.unitId = m.baseUnitId || null
    row.price = m.salePrice || 0
    row.wh_id = form.wh_id
    row.wh_name = form.wh_name
    calcRow(row)
  }
  // 跟着物料自动带出默认BOM的原料明细，并自动展开（2026-10-26）
  applyDefaultBomForProduct(row)
}

function onBomProductChange(row, id) {
  const m = materials.value.find(x => x.materialId === id)
  if (m) {
    row.product_code = m.materialCode
    row.product_name = m.materialName
    row.specification = m.model || ''
    row.unit = m.baseUnitName || ''
    row.unitId = m.baseUnitId || null
    row.wh_id = form.wh_id_bom
    row.wh_name = form.wh_name_bom
    // 原料出库按成本价带出单价（物料 /list 只回填 avgCost）
    row.price = m.avgCost != null ? Number(m.avgCost) : 0
    calcBomRow(row)
  }
}

/** 表头「计税方式」归一：含税业务关闭 → 不计税；否则取 tax_calc_method（非法值兜底不含税价） */
function currentTaxMethod() {
  if (!taxEnabled.value) return '不计税'
  const m = form.tax_calc_method
  if (m === '含税价' || m === '不计税') return m
  return '不含税价'
}

/** 从设置中心取「默认计税方式」与两个总开关（含税 / 外币） */
async function initTaxSettings() {
  try {
    await ensureCommercialSwitches()
    await loadSysConfig()
  } catch (e) { /* 读取失败保持默认（含税=开、外币=关） */ }
  if (!taxEnabled.value) {
    // 含税业务关闭：表头「计税方式」整项隐藏，一律按不计税计算
    form.tax_calc_method = '不计税'
    form.is_tax = 0
    form.tax_type = 0
  } else if (!form.id) {
    form.tax_calc_method = normalizeTaxMethod(getConfig('sys.tax.method'))
    form.is_tax = form.tax_calc_method === '不计税' ? 0 : 1
    form.tax_type = form.tax_calc_method === '含税价' ? 1 : 0
  } else {
    form.tax_calc_method = normalizeTaxMethod(form.tax_calc_method)
  }
}

/** 表头「计税方式」切换 → 两张明细区按新口径重算（并同步老口径字段，避免两个表头控件互相矛盾） */
function onTaxCalcMethodChange() {
  form.is_tax = form.tax_calc_method === '不计税' ? 0 : 1
  form.tax_type = form.tax_calc_method === '含税价' ? 1 : 0
  form.produce_assemble_detail.forEach(row => calcRow(row))
  form.produce_assemble_bom.forEach(row => calcBomRow(row))
  calcTotal()
}

/** 按表头「计税方式」把「单价」换算成 含税价 / 不含税价 */
function convertPrice(price, taxRate) {
  const p = Number(price || 0)
  const method = currentTaxMethod()
  const factor = 1 + (Number(taxRate) || 0) / 100
  if (method === '含税价') return { withTax: p, withoutTax: factor ? Number((p / factor).toFixed(6)) : p }
  if (method === '不计税') return { withTax: p, withoutTax: p }
  return { withoutTax: p, withTax: Number((p * factor).toFixed(6)) }
}

function calcRow(row) {
  const qty = Number(row.quantity || 0)
  const price = Number(row.price || 0)
  const taxRate = Number(row.tax_rate != null ? row.tax_rate : (form.tax_rate || 0))
  const method = currentTaxMethod()
  const factor = 1 + taxRate / 100
  row.tax_rate = taxRate
  const conv = convertPrice(price, taxRate)
  row.price_with_tax = conv.withTax
  row.price_without_tax = conv.withoutTax
  // 折前金额按「不含税」口径；不计税时税额恒为 0
  const base = method === '含税价' ? (price * qty) / factor : price * qty
  row.amount = base.toFixed(2)
  row.tax_amount = (method === '不计税' ? 0 : Number(row.amount) * taxRate / 100).toFixed(2)
  calcTotal()
}

function calcBomRow(row) {
  const qty = Number(row.quantity || 0)
  const price = Number(row.price || 0)
  const taxRate = Number(row.tax_rate != null ? row.tax_rate : (form.tax_rate || 0))
  const method = currentTaxMethod()
  const factor = 1 + taxRate / 100
  row.tax_rate = taxRate
  const conv = convertPrice(price, taxRate)
  row.price_with_tax = conv.withTax
  row.price_without_tax = conv.withoutTax
  const base = method === '含税价' ? (price * qty) / factor : price * qty
  row.amount = base.toFixed(2)
  row.tax_amount = (method === '不计税' ? 0 : Number(row.amount) * taxRate / 100).toFixed(2)
}

function calcTotal() {
  let total = 0
  let tax = 0
  form.produce_assemble_detail.forEach(item => {
    total += Number(item.amount || 0)
    tax += Number(item.tax_amount || 0)
  })
  form.amount = total.toFixed(2)
  form.tax_amount = tax.toFixed(2)
}

// ===== 多单位换算（2026-11-11 统一）：6 列由明细通用组件按「单位列 + 物料列(product_id)」自动注入 =====
// 页面不再手写列定义与单元格渲染；本页只保留物料实体缓存（避免组件逐行请求）与行字段 camel/snake 回显。

/** 单元格取值能力：物料实体 / 单位名 / 换算行缓存都在通用能力里，本页只声明物料字段名与行数组 */
const productUnitCells = useMultiUnitCells({ getIdProp: () => 'product_id', getRows: () => form.produce_assemble_detail })
const bomUnitCells = useMultiUnitCells({ getIdProp: () => 'product_id', getRows: () => form.produce_assemble_bom })

/** 物料实体已由本页 listAllMaterial() 一次取全 → 直接喂给通用能力（副单位/浮动单位要靠它），不再逐行请求 */
function rememberMaterialEntities() {
  ;(materials.value || []).forEach(m => {
    productUnitCells.remember(m.materialId, m)
    bomUnitCells.remember(m.materialId, m)
  })
}

/** 行字段回显：后端 snake 的单位三项打到行上 camel，供通用能力读取（其余业务字段一字不动） */
function hydrateUnitFields(rows) {
  ;(rows || []).forEach(r => {
    if (!r) return
    if (r.unitId == null && r.unit_id != null) r.unitId = r.unit_id
    if (r.floatUnitId == null && r.float_unit_id != null) r.floatUnitId = r.float_unit_id
    if (r.floatQuantity == null && r.float_quantity != null) r.floatQuantity = r.float_quantity
  })
}

/** 提交口径：行上 camel 的单位三项写回后端 snake 字段（其余业务字段一字不动） */
function toSubmitUnitRows(rows) {
  return (rows || []).map(r => {
    if (!r) return r
    const out = { ...r }
    out.unit_id = r.unitId != null ? r.unitId : null
    out.float_unit_id = r.floatUnitId != null ? r.floatUnitId : null
    out.float_quantity = r.floatQuantity != null ? r.floatQuantity : null
    delete out.unitId
    delete out.floatUnitId
    delete out.floatQuantity
    return out
  })
}

/** 整单刷新：换料先清掉旧物料的浮动单位/数量 → 换算行按整单一次性批量取数 */
function refreshMultiUnit() {
  const idOf = r => (r && r.product_id != null ? r.product_id : null)
  const ids = [...form.produce_assemble_detail.map(idOf), ...form.produce_assemble_bom.map(idOf)].filter(id => id != null)
  productUnitCells.syncRowMaterials()
  bomUnitCells.syncRowMaterials()
  ;[productUnitCells, bomUnitCells].forEach(c => {
    c.ensureMaterials(ids).catch(() => {})
    c.loadUnits().catch(() => {})
  })
  if (ids.length) loadConversions(ids)
}

// 明细行物料变化（选料 / 换料 / 增删行）→ 副单位三列与浮动两列跟着刷新
watch(() => form.produce_assemble_detail.map(r => (r && r.product_id) || '').join('|')
  + '#' + form.produce_assemble_bom.map(r => (r && r.product_id) || '').join('|'), () => refreshMultiUnit())

// 成品明细列定义（通用组件 BillItemsTable 消费）：本页字段沿用 snake_case 口径（product_id/quantity/basic_quantity/price/...）
// editor = 单元格控件，type = 纯展示格式；物料编码与主单位数量由页面提供插槽（#cell-<prop>）
const productColumns = [
  { prop: 'product_id', label: '物料编码', width: 160 },
  { prop: 'product_name', label: '物料名称', minWidth: 140 },
  { prop: 'specification', label: '规格型号', width: 120 },
  { prop: 'unit', label: '单位', width: 60, align: 'center' },
  { prop: 'quantity', label: '数量', width: 100, editor: 'number', precision: 4, change: (row) => calcRow(row) },
  { prop: 'basic_quantity', label: '主单位数量', width: 110 },
  { prop: 'price', label: '单价', width: 100, editor: 'number', precision: 4, change: (row) => calcRow(row) },
  { prop: 'price_with_tax', label: '含税价', width: 95, align: 'right', type: 'amount4' },
  { prop: 'price_without_tax', label: '不含税价', width: 95, align: 'right', type: 'amount4' },
  { prop: 'amount', label: '金额', width: 100, align: 'right', type: 'netAmount' },
  { prop: 'wh_name', label: '仓库', width: 110 },
  { prop: 'notes', label: '备注', width: 150, editor: 'text' }
]

// BOM原料明细列定义：与成品表同结构、无 price/amount（字段口径同样 snake_case）
const bomColumns = [
  { prop: 'product_id', label: '物料编码', width: 160 },
  { prop: 'product_name', label: '物料名称', minWidth: 140 },
  { prop: 'specification', label: '规格型号', width: 120 },
  { prop: 'unit', label: '单位', width: 60, align: 'center' },
  { prop: 'quantity', label: '数量', width: 100, editor: 'number', precision: 4, change: (row) => calcBomRow(row) },
  { prop: 'basic_quantity', label: '主单位数量', width: 110 },
  // 计税口径（2026-10-09）：BOM 原料明细补「单价 / 含税价 / 不含税价 / 金额」列（原页不展示金额）
  { prop: 'price', label: '单价', width: 100, editor: 'number', precision: 4, change: (row) => calcBomRow(row) },
  { prop: 'price_with_tax', label: '含税价', width: 95, align: 'right', type: 'amount4' },
  { prop: 'price_without_tax', label: '不含税价', width: 95, align: 'right', type: 'amount4' },
  { prop: 'amount', label: '金额', width: 100, align: 'right', type: 'amount2' },
  { prop: 'wh_name', label: '仓库', width: 110 },
  { prop: 'notes', label: '备注', width: 150, editor: 'text' }
]

// 汇总区（通用组件 BillItemsTable 按此渲染）：把原页「表格底部合计行（getProductSummary/getBomSummary）」
// 与「卡片底部单据金额汇总块」的口径逐项搬进来 —— 成品卡放「成品合计数量」「单据金额」（单据金额 = 成品金额合计），
// BOM卡放「原料合计数量」；原页没有组件内置的「合计数量」行 → show-total-qty=false
const productSummaryItems = computed(() => [
  { label: '成品合计数量：', value: totalQty.value },
  { label: '单据金额：', value: `¥${Number(form.amount || 0).toFixed(2)}`, total: true }
])
const bomSummaryItems = computed(() => [
  { label: '原料合计数量：', value: bomTotalQty.value }
])

function addProductRow() {
  const row = {
    product_id: null,
    product_code: '',
    product_name: '',
    specification: '',
    unit: '',
    unitId: null,
    floatUnitId: null,
    floatQuantity: null,
    quantity: 1,
    basic_quantity: 0,
    price: 0,
    price_with_tax: 0,
    price_without_tax: 0,
    tax_rate: form.tax_rate || 0,
    tax_amount: 0,
    amount: 0,
    wh_id: form.wh_id,
    wh_name: form.wh_name,
    notes: ''
  }
  calcRow(row)
  form.produce_assemble_detail.push(row)
}

function createBomRow() {
  return {
    product_id: null,
    product_code: '',
    product_name: '',
    specification: '',
    unit: '',
    unitId: null,
    floatUnitId: null,
    floatQuantity: null,
    quantity: 1,
    basic_quantity: 0,
    price: 0,
    price_with_tax: 0,
    price_without_tax: 0,
    tax_rate: form.tax_rate || 0,
    tax_amount: 0,
    amount: 0,
    wh_id: form.wh_id_bom,
    wh_name: form.wh_name_bom,
    notes: ''
  }
}

function addBomRow() {
  const row = createBomRow()
  calcBomRow(row)
  form.produce_assemble_bom.push(row)
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
 * 选完成品物料（2026-10-26）：按该物料的默认BOM自动带出「BOM原料明细」，并自动展开两张明细。
 * 只在原料明细为空时带出，不覆盖/不删除用户已维护的行。
 */
async function applyDefaultBomForProduct(row) {
  if (!row || row.product_id == null) return
  if (hasFilledRows(form.produce_assemble_bom, 'product_id')) return
  const productId = row.product_id
  const bom = pickDefaultBom(productId)
  if (!bom) return
  const token = bomAutoFill.begin()
  try {
    const detail = await getBom(bom.bomId)
    // 并发防护（2026-10-26）：等BOM期间又改选了成品 / 成品已被清空 → 丢弃本次结果，
    // 否则两套BOM会先后追加，重复行在组装单生效时会重复扣料。
    if (!bomAutoFill.isLatest(token)) return
    if (row.product_id == null || String(row.product_id) !== String(productId)) return
    if (hasFilledRows(form.produce_assemble_bom, 'product_id')) return
    const items = (detail && detail.items) || []
    if (!items.length) return
    if (!form.produce_assemble_bom) form.produce_assemble_bom = []
    items.forEach(it => {
      const r = createBomRow()
      const m = materials.value.find(x => x.materialId === it.materialId)
      r.product_id = it.materialId
      r.product_code = it.materialCode || ''
      r.product_name = it.materialName || ''
      r.specification = it.specification || ''
      r.unit = m?.baseUnitName || ''
      r.unitId = it.unitId || m?.baseUnitId || null
      r.quantity = it.quantity != null ? Number(it.quantity) : 1
      r.basic_quantity = r.quantity
      r.price = m && m.avgCost != null ? Number(m.avgCost) : 0
      calcBomRow(r)
      form.produce_assemble_bom.push(r)
    })
    collapsed.detail = true
    collapsed.bom = true
  } catch (e) { /* 带出失败不影响已选物料 */ }
}

async function onMaterialPicked({ rows: picked, valueMap }) {
  if (!picked || picked.length === 0) return
  if (valueMap) {
    Object.assign(mcf.valueMap.value, valueMap)
    Object.keys(valueMap).forEach(id => mcf.loadedIds.value.add(Number(id)))
  }
  picked.forEach(m => {
    const row = {
      product_id: m.materialId,
      product_code: m.materialCode || '',
      product_name: m.materialName || '',
      specification: m.model || '',
      unit: m.baseUnitName || '',
      unitId: m.baseUnitId || null,
      floatUnitId: null,
      floatQuantity: null,
      quantity: 1,
      basic_quantity: 0,
      price: m.salePrice || 0,
      amount: 0,
      wh_id: form.wh_id,
      wh_name: form.wh_name,
      notes: ''
    }
    calcRow(row)
    form.produce_assemble_detail.push(row)
  })
  pickerVisible.value = false
  ElMessage.success(`${tt("已添加 ")}${picked.length}${tt(" 条成品明细")}`)
  // 物料库批量带入后，按第一条成品的默认BOM自动带出原料明细（2026-10-26）
  if (picked[0]) applyDefaultBomForProduct({ product_id: picked[0].materialId })
}

async function handleSubmit() {
    // 前端必填校验（B功能）
    // 统一校验：头表单 + 明细内表单 + 任何 formRef；失败时精确列出缺失字段名
  const missing = await validateBillRequiredFields({ formRef, rules, $el: document, pageKey: 'produce_assemble', model: form })
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
      const target = (formRef && formRef.value) ? formRef.value : null
      if (target && typeof target.scrollToField === 'function') target.scrollToField(missing[0].prop)
    } catch(_) {}
    return
  }

  // 无论新建还是已有草稿，提交前一律先静默保存（把当前页面所见落库，避免提交旧数据）
  const saved = await onSave(true, { skipRouteSync: true })
  if (!saved || !form.id) return
  try {
    const needApproval = await confirmSubmit({ billType: 'PRODUCE_ASSEMBLE', billLabel: '生产组装单' })
    await submitProduceAssemble(form.id)
    ElMessage.success(submitSuccessMessage(needApproval))
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('提交失败'))
  }
}

function showApproveDialog(action) {
  approveAction.value = action
  approveRemark.value = ''
  approveDialog.value = true
}

async function doApprove() {
  try {
    if (approveAction.value === 'approve') {
      await approveProduceAssemble(form.id, { remark: approveRemark.value })
      ElMessage.success(tt('审批通过'))
    } else {
      await rejectProduceAssemble(form.id, { remark: approveRemark.value })
      ElMessage.success(tt('已驳回'))
    }
    approveDialog.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message || tt('审批失败'))
  }
}

async function handleRevoke() {
  try {
    await ElMessageBox.confirm(tt('确认撤销该组装单?'), tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await revokeProduceAssemble(form.id)
    ElMessage.success(tt('撤销成功'))
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('撤销失败'))
  }
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm(tt('确认删除该组装单?'), tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await deleteProduceAssemble(form.id)
    ElMessage.success(tt('删除成功'))
    router.push('/production/assemble')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('删除失败'))
  }
}

function showCancelDialog() {
  cancelReason.value = ''
  cancelDialog.value = true
}

async function doCancel() {
  if (!cancelReason.value.trim()) {
    ElMessage.warning(tt('请输入作废原因'))
    return
  }
  try {
    await cancelProduceAssemble(form.id, cancelReason.value)
    ElMessage.success(tt('作废成功'))
    cancelDialog.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message || tt('作废失败'))
  }
}

async function handleResetDraft() {
  try {
    await ElMessageBox.confirm(tt('确认重置为草稿?'), tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await resetDraftProduceAssemble(form.id)
    ElMessage.success(tt('重置成功'))
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('重置失败'))
  }
}

async function handleCopy() {
  try {
    await ElMessageBox.confirm(tt('确认复制该组装单?'), tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'info'
    })
    const res = await copyProduceAssemble(form.id)
    if (res && res.id) {
      ElMessage.success(tt('复制成功'))
      router.push(`/production/assemble/${res.id}?edit=1`)
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('复制失败'))
  }
}

// 处理更多操作下拉


const printVisible = ref(false)
function handlePrint() {
  if (!form.id) { ElMessage.warning(tt('请先保存单据再打印')); return }
  printVisible.value = true
}

async function load() {
  const __detailId = route.params.id || form.id || ''
  if (__detailId) {
    try {
      const res = await getProduceAssemble(__detailId)
      Object.assign(form, res)
      // 回显侧：dateline 是「日期+时间」列，后端返回 2026-09-20T00:00:00，而日期控件 value-format 只认 YYYY-MM-DD
      // → 截到日期，否则控件解析不了会显示空白；保存侧再补回 00:00:00（两侧必须成对，缺一侧就丢值）
      if (form.dateline) form.dateline = String(form.dateline).slice(0, 10)
      if (!form.produce_assemble_detail) form.produce_assemble_detail = []
      if (!form.produce_assemble_bom) form.produce_assemble_bom = []
      // 多单位换算：后端 snake 的 unit_id / float_* 打到行上 camel（副单位三列与浮动两列取值用）
      hydrateUnitFields(form.produce_assemble_detail)
      hydrateUnitFields(form.produce_assemble_bom)
      if (headerCustomFieldMap.size > 0 && form.id) {
        const headerValues = await loadCustomFieldValues('produce_assemble', form.id)
        Object.assign(form, headerValues)
      }
      // 计税方式：老单据按设置中心默认/总开关归一，再按口径回算两张明细的含税价/不含税价
      await initTaxSettings()
      form.produce_assemble_detail.forEach(row => calcRow(row))
      form.produce_assemble_bom.forEach(row => calcBomRow(row))
      calcTotal()
    } catch (e) {
      ElMessage.error(e.message || tt('加载失败'))
    }
  }
}

// 加载下拉基础数据——独立函数，供 onMounted 和切回标签页时复用
  async function loadBaseData() {
  // 全站商业口径总开关（启用含税业务 / 启用外币业务）：优先拉取最新值
  await ensureCommercialSwitches()
  try {
      const [whRes, matRes, curRes, bomRes] = await Promise.all([
        listAllWarehouse(),
        listAllMaterial(),
        listAllCurrency(),
        listAllBom()
      ])
      warehouses.value = whRes || []
      materials.value = matRes || []
      // 物料实体就地喂给多单位能力（副单位/浮动单位要读基本单位与辅计量开关），避免逐行再请求物料
      rememberMaterialEntities()
      bomOptions.value = bomRes || []
      // 行内下拉附加字段：物料到位后按批预取（不阻塞渲染）
      mcf.preloadAll(materials.value)
      currencies.value = curRes || []
      if (currencies.value.length > 0 && !form.currency_id) {
        // 外币业务关闭且为新增单据：默认本位币、汇率锁 1（老单据保持原币，仅供查看）
        const defaultCur = (!foreignCurrencyEnabled.value && !isEdit.value)
          ? (currencies.value.find(c => c.isBaseCurrency === 1 || c.isBaseCurrency === true) || currencies.value[0])
          : (currencies.value.find(c => c.isDefault) || currencies.value[0])
        form.currency_id = defaultCur.currencyId
        form.currency_rate = Number(defaultCur.exchangeRate || 1) || 1
      }
    } catch (e) {
      console.warn('下拉数据加载失败:', e)
    }
}

// ===== 复制功能 =====
const { applyCopy } = useCopyBill({
  route,
  loadFn: getProduceAssemble,
  form,
  afterCopy: () => {
    form.produce_assemble_detail.forEach(row => calcRow(row))
    form.produce_assemble_bom.forEach(row => calcBomRow(row))
    calcTotal()
    // 复制装入的行来自接口快照（snake）：先对齐单位字段，再按整单批量取换算行
    hydrateUnitFields(form.produce_assemble_detail)
    hydrateUnitFields(form.produce_assemble_bom)
    refreshMultiUnit()
  },
  customField: { entity: 'produce_assemble' }
})

// ===== 明细行「物料」下拉：输入过滤（编码/名称/规格 ＋ 自定义字段值）与背景提示声明 =====
/** 参与「物料」下拉搜索的自定义字段：勾了「下拉显示」（受定制总开关控制）或勾了「可搜索」的字段 */
const materialSearchableFields = computed(() => (mcf.allFields.value || []).filter(f =>
  f && (f.isSearch === 1 || (mcf.enabled.value && f.isDropShow === 1))
))

/** 物料下拉的背景提示文字：声明可搜索范围；没有可搜索的自定义字段时原样返回「选择物料」（零回归） */
const materialSelectPlaceholder = computed(() => buildMaterialPlaceholder(tt('选择物料'), materialSearchableFields.value))

/** 下拉输入过滤：编码/名称/规格 ＋ 该物料的自定义字段值（详见 utils/materialFilter.js） */
function materialFilterMethod(val, row) {
  if (!val) { row._filteredMaterials = null; return }
  row._filteredMaterials = filterMaterials(materials.value, val, mcf.valueMap.value, materialSearchableFields.value)
}
const { isFieldRequired } = useFieldPermission('produce_assemble')
useFieldRequiredMark('produce_assemble', '.produce-assemble-edit')

onMounted(async () => {
  // 物料自定义字段：定制开关 + 字段定义（失败静默降级）
  await mcf.init()
  loadCollapsed()
  await loadCustomFields()
  await loadBaseData()
  if (!isEdit.value) {
    autoFillDocCodeOnCreate(form, 'serial_no')
  }
  // 新建/复制：表头「计税方式」取设置中心「默认计税方式」（编辑态由 load 内部归一）
  await initTaxSettings()
  // 多单位换算：浮动单位全局开关 + 整单换算行（与明细一起就绪）
  loadFloatUnitEnabled().catch(() => {})
  const copied = await applyCopy()
  if (!copied) {
    await load()
  }
  refreshMultiUnit()
})

// 切回标签页时自动刷新下拉基础数据（只刷下拉，不动表单草稿），避免档案改过后这里还是旧数据
useBaseDataRefresh(loadBaseData)


  // ======== 状态流转：通用占位 handler（样板：与 SaleOrderEdit 对齐） ========
  // 重要：请将下方 TODO_* 占位函数替换为当前模块实际 API 调用（或在上方 API 导入后重命名为这些函数）
  // 通用：取单据ID（覆盖各模块常见字段，兜底 form.id）
  const billId = computed(() => {
    return form.orderId || form.deliveryId || form.inboundId || form.invoiceId || form.contractId
        || form.voucherId || form.requisitionId || form.stockCheckId || form.transferId
        || form.bomId || form.sampleId || form.freeSampleId || form.workOrderId || form.billId || form.id
  })

  // 弹窗与 reason
  const rejectDialog = ref(false)
  const rejectReason = ref('')
  const voidDialog = ref(false)
  const voidReason = ref('')

  /** 单据日期是「日期+时间」列的双向映射：日期控件只产出 YYYY-MM-DD，后端要 ISO 的 yyyy-MM-ddTHH:mm:ss（实测：空格分隔会被后端拒绝） */
  function normalizeBillDate(v) {
    if (!v) return v
    const s = String(v)
    return s.length === 10 ? s + 'T00:00:00' : s
  }
  async function onSave(silent, opts = {}) {
    if (form.status && !isDraft.value) { ElMessage.warning(`${tt("当前状态为「")}${form.status}${tt("」, 不可修改")}`); return false }
    // 按当前表头「计税方式」把两张明细的含税价/不含税价/金额/税额刷成最新
    form.produce_assemble_detail.forEach(row => calcRow(row))
    form.produce_assemble_bom.forEach(row => calcBomRow(row))
    calcTotal()
    try {
      // 保存侧：日期控件只给 YYYY-MM-DD，直接提交会被后端按「日期+时间」解析失败（HTTP 400 请求数据格式错误）
      const payload = {
        ...form,
        dateline: normalizeBillDate(form.dateline),
        // 多单位换算：行上 camel 的单位三项写回后端 snake 字段（其余业务字段原样提交）
        produce_assemble_detail: toSubmitUnitRows(form.produce_assemble_detail),
        produce_assemble_bom: toSubmitUnitRows(form.produce_assemble_bom)
      }
      if (billId.value) {
        await updateProduceAssemble(billId.value, payload)
      } else {
        const res = await saveProduceAssemble(payload)
        if (res) {
          const idKeys = ['orderId','deliveryId','inboundId','invoiceId','contractId','voucherId','requisitionId','stockCheckId','transferId','bomId','sampleId','freeSampleId','workOrderId','billId','id']
          for (const k of idKeys) if (res[k] != null && form[k] == null) form[k] = res[k]
          const noKeys = ['orderNo','deliveryNo','inboundNo','invoiceNo','contractNo','voucherNo','requisitionNo','stockCheckNo','transferNo','sampleNo','billNo']
          for (const k of noKeys) if (res[k] != null) form[k] = res[k]
          if (res.status) form.status = res.status
          if (res.approvalStatus) form.approvalStatus = res.approvalStatus
        }
        // 新建保存后必须把 id 写回地址栏：否则 load() 拿不到 id → 提交/保存后页面状态不刷新（历史缺陷）
        if (!opts.skipRouteSync && form.id) { try { await router.replace({ name: 'ProduceAssembleEdit', params: { id: String(form.id) } }) } catch (_) {} }
      }
      triggerListRefresh('/production/assembles')
    if (!silent) ElMessage.success(tt('保存成功'))
      await load()
      return true
    } catch (e) { ElMessage.error(e.message || tt('保存失败')); return false }
  }

  async function onSubmit() {
    // 无论新建还是已有草稿，提交前一律先静默保存（把当前页面所见落库，避免提交旧数据）
    const saved = await onSave(true, { skipRouteSync: true })
    if (!saved || !billId.value) return
    try {
      const needApproval = await confirmSubmit({ billType: 'PRODUCE_ASSEMBLE', billLabel: '生产组装单' })
      await submitProduceAssemble(billId.value)
      ElMessage.success(submitSuccessMessage(needApproval))
      await load()
    } catch (e) {
      if (e !== 'cancel') ElMessage.error(e.message || tt('提交失败'))
    } finally {
      // 提交（或用户取消）后再把新单 id 写回地址栏：此时重建实例无副作用，新实例挂载时会重新拉取最新状态
      if (!route.params.id && billId.value) {
        try { await router.replace({ name: 'ProduceAssembleEdit', params: { id: String(billId.value) } }) } catch (_) {}
      }
    }
  }

  async function onApprove() {
    if (!billId.value) { ElMessage.warning(tt('请先保存单据')); return }
    try {
      await ElMessageBox.confirm(tt('确认审核？审核后将不可直接修改'), tt('提示'), { confirmButtonText: tt('确定'), cancelButtonText: tt('取消'), type: 'warning' })
      await approveProduceAssemble(billId.value)
      ElMessage.success(tt('审核成功'))
      await load()
    } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || tt('审核失败')) }
  }

  function handleReject() { rejectReason.value = ''; rejectDialog.value = true }
  async function doReject() {
    try {
      await rejectProduceAssemble(billId.value, { reason: rejectReason.value })
      ElMessage.success(tt('已驳回')); rejectDialog.value = false
      await load()
    } catch (e) { ElMessage.error(e.message || tt('驳回失败')) }
  }

  async function handleReverseApprove() {
    try {
      await ElMessageBox.confirm(tt('确认重置草稿？重置草稿后单据回到草稿态可修改，需下游未生效'), tt('提示'), { confirmButtonText: tt('确定'), cancelButtonText: tt('取消'), type: 'warning' })
      await reverseApproveProduceAssemble(billId.value)
      ElMessage.success(tt('重置草稿成功'))
      await load()
    } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || tt('重置草稿失败')) }
  }

  async function handleClose() {
    try {
      await ElMessageBox.confirm(tt('确认结案？结案后进入完结态不可修改'), tt('提示'), { confirmButtonText: tt('确定'), cancelButtonText: tt('取消'), type: 'warning' })
      await closeOrderProduceAssemble(billId.value)
      ElMessage.success(tt('已结案'))
      await load()
    } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || tt('结案失败')) }
  }

  async function handleReopen() {
    try {
      await ElMessageBox.confirm(tt('确认反结案？反结案后返回已生效态'), tt('提示'), { confirmButtonText: tt('确定'), cancelButtonText: tt('取消'), type: 'warning' })
      await reopenProduceAssemble(billId.value)
      ElMessage.success(tt('反结案成功'))
      await load()
    } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || tt('反结案失败')) }
  }

  function handleVoid() { voidReason.value = ''; voidDialog.value = true }
  async function doVoid() {
    try {
      await cancelProduceAssemble(billId.value, voidReason.value)
      ElMessage.success(tt('已作废')); voidDialog.value = false
      await load()
    } catch (e) { ElMessage.error(e.message || tt('作废失败')) }
  }

  async function handleUnvoid() {
    try {
      await ElMessageBox.confirm(tt('确认反作废？将恢复到作废前状态'), tt('提示'), { confirmButtonText: tt('确定'), cancelButtonText: tt('取消'), type: 'warning' })
      await unvoidProduceAssemble(billId.value)
      ElMessage.success(tt('反作废成功'))
      await load()
    } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || tt('反作废失败')) }
  }

  // ======== 状态流转 END ========

</script>

<style scoped lang="scss">
.produce-assemble-edit {
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

// ===== 明细表格 =====
.detail-section-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 14px;
  background: #fafbfc;
  border-bottom: 1px solid #ebeef5;

  .bar-title {
    font-size: 13px;
    font-weight: 600;
    color: #303133;
    position: relative;
    padding-left: 10px;

    &::before {
      content: '';
      position: absolute;
      left: 0;
      top: 50%;
      transform: translateY(-50%);
      width: 3px;
      height: 12px;
      background: linear-gradient(180deg, #409eff, #66b1ff);
      border-radius: 2px;
    }
  }

  .bar-actions {
    display: flex;
    gap: 6px;
  }
}

.table-wrap {
  overflow-x: auto;
  padding: 10px 12px 0;
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

.net-amount { color: #409eff; font-weight: 600; }

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

@media (max-width: 1400px) {
  .base-form .section-fields { grid-template-columns: repeat(3, 1fr); }
}

@media (max-width: 1200px) {
  .base-form .section-fields { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 768px) {
  .produce-assemble-edit { padding: 8px; }
  .page-header { flex-direction: column; gap: 8px; align-items: stretch; }
  .base-form .section-fields { grid-template-columns: 1fr; }
  .summary-area { flex-direction: column; }
}
</style>


<!-- 非 scoped：物料下拉的 popper 被 teleport 到 body，scoped 选择器无法命中，只能配全局类名（popper-class="material-select-popper"） -->
<style>
/* 面板加宽：Element Plus 给 .el-select-dropdown 设的是行内 min-width = 触发框宽度-2px，覆盖不了，
   因此在 popper 根节点设 min-width；选项改为换行，避免内容被 ellipsis 截断 */
.opt-cf { margin-left: 8px; color: #909399; font-size: 11px; white-space: normal; word-break: break-word; }
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
