<template>
  <div class="erp-page">
    <!-- 顶部工具栏 -->
    <el-card style="margin-bottom:16px;">
      <div class="toolbar">
        <div>
          <el-button v-permission="perm('dlgCancel')" text @click="goBack">{{ $tt("◀ 返回列表") }}</el-button>
          <span class="title">{{ $tt("免费样品订单") }}</span>
          <el-tag style="margin-left:10px;" :type="statusTagType(form.status)">{{ $dt(form.status || '草稿') }}</el-tag>
        </div>
        <div>
          <el-button v-permission="perm('view')" v-if="!(!form.orderId && !form.deliveryId && !form.inboundId && !form.invoiceId && !form.voucherId && !form.contractId && !form.requisitionId && !form.stockCheckId && !form.transferId && !form.bomId && !form.sampleId && !form.freeSampleId && !form.workOrderId && !form.billId && !form.id)" @click="changeRecordVisible = true">
            <el-icon><Memo /></el-icon>{{ $tt('操作日志') }}
          </el-button>
          <BillActions bill-type="FREE_SAMPLE"
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
    </el-card>

    <!-- 顶级页签：单据 / 来源单据 / 去向单据（来源去向为通用组件，未登记的单据类型不显示） -->
    <el-tabs v-model="topTab" class="bill-top-tabs">
      <el-tab-pane :label="$tf('单据')" name="doc">

    <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
      <!-- 表头信息 -->
      <el-card style="margin-bottom:16px;">
        <template #header>{{ $tt("订单信息") }}</template>
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item :label="$tf('单号')">
              <el-input v-model="form.sampleNo" :placeholder="docCodePlaceholder" :disabled="!isDraft" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="$tf('客户')" prop="customerId" required>
              <el-select v-model="form.customerId" filterable :placeholder="$tt('请选择客户')" style="width:100%" :disabled="!editable" @change="onCustomerChange">
                <el-option v-for="c in withFallbackOption(customerOptions, 'customerId', 'customerName', form)" :key="c.customerId" :label="c.customerName" :value="c.customerId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="$tf('日期')" prop="sampleDate" required>
              <el-date-picker v-model="form.sampleDate" type="date" value-format="YYYY-MM-DD" style="width:100%" :disabled="!editable" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="$tf('仓库')" prop="warehouseId" required>
              <el-select v-model="form.warehouseId" filterable :placeholder="$tt('请选择仓库')" style="width:100%" :disabled="!editable">
                <el-option v-for="w in withFallbackOption(warehouseOptions, 'warehouseId', 'warehouseName', form)" :key="w.stockId" :label="w.stockName" :value="w.stockId" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item :label="$tf('金额')">
              <el-input :model-value="'0.00'" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="$tf('快递公司')">
              <el-input v-model="form.expressCompany" :disabled="form.status !== '已生效' && form.status !== '已发货' && form.status !== '已完成'" :placeholder="$tt('发货时填写')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('快递单号')">
              <el-input v-model="form.expressNo" :disabled="form.status !== '已生效' && form.status !== '已发货' && form.status !== '已完成'" :placeholder="$tt('发货时填写')" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item :label="$tf('备注')">
              <el-input v-model="form.remark" type="textarea" :rows="2" :disabled="!editable" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-card>

      <!-- 自定义字段分区 -->
      <el-card v-for="section in headerCustomSections" :key="section.title" style="margin-bottom:16px;" v-if="headerCustomSections.length > 0">
        <template #header>{{ section.title }}</template>
        <el-row :gutter="20">
          <el-col :span="6" v-for="field in section.fields" :key="field.prop">
            <el-form-item :label="$tf(field.label)" :required="field.required || isFieldRequired(field.prop)">
              <el-select
                v-if="field.type === 'select'"
                v-model="form[field.prop]"
                :placeholder="field.placeholder || '请选择'"
                :filterable="field.filterable !== false"
                style="width:100%"
                :disabled="!editable"
              >
                <el-option v-for="opt in (field.options || [])" :key="opt.value" :label="$tf(opt.label)" :value="opt.value" />
              </el-select>
              <el-input-number
                v-else-if="field.type === 'number'"
                v-model="form[field.prop]"
                :precision="field.precision"
                :min="field.min !== undefined ? field.min : 0"
                :step="0.1"
                style="width:100%"
                :disabled="!editable"
              />
              <el-switch
                v-else-if="field.type === 'switch'"
                v-model="form[field.prop]"
                :active-value="field.activeValue !== undefined ? field.activeValue : 1"
                :inactive-value="field.inactiveValue !== undefined ? field.inactiveValue : 0"
                :disabled="!editable"
              />
              <el-date-picker
                v-else-if="field.type === 'date'"
                v-model="form[field.prop]"
                type="date"
                value-format="YYYY-MM-DD"
                :placeholder="field.placeholder || '选择日期'"
                style="width:100%"
                :disabled="!editable"
              />
              <el-input
                v-else-if="field.type === 'textarea'"
                v-model="form[field.prop]"
                type="textarea"
                :rows="field.rows || 3"
                :placeholder="field.placeholder || '请输入'"
                :disabled="!editable"
              />
              <el-input
                v-else
                v-model="form[field.prop]"
                :placeholder="field.placeholder || '请输入'"
                :disabled="!editable"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-card>

      <!-- 明细卡片（通用组件 BillItemsTable：卡片/列设置/动态列/自定义字段列/汇总 全站共用一套） -->
      <BillItemsTable
        v-model:collapsed="collapsed.items"
        :items="form.items"
        page-key="FreeSampleEdit_items"
        title="样品明细"
        subtitle="样品明细"
        col-setting-title="样品明细列设置"
        :columns="detailColumns"
        :custom-columns="itemCustomColumns"
        :readonly="!editable"
        :show-total-qty="false"
        :summary-items="summaryItems"
      >
        <template #actions>
          <el-button v-permission="perm('addRow')" type="primary" size="small" @click="addItem" :disabled="!editable">{{ $tt("+ 添加行") }}</el-button>
        </template>
        <template #cell-materialId="{ row }">
          <el-select v-model="row.materialId" filterable :placeholder="materialSelectPlaceholder" style="width:100%" :disabled="!editable" @change="(v) => onMaterialChange(row, v)" :filter-method="(val) => materialFilterMethod(val, row)" popper-class="material-select-popper">
            <el-option v-for="m in (row._filteredMaterials || materialOptions)" :key="m.materialId" :label="`${m.materialName} (${m.materialCode})`" :value="m.materialId">
              <span class="opt-code">{{ m.materialCode }}</span>
              <span class="opt-name">{{ m.materialName }}</span>
              <span v-for="cf in mcf.dropText(m)" :key="cf.key" class="opt-cf">{{ cf.label }}：{{ cf.value }}</span>
              <span v-if="m.model" class="opt-model">{{ m.model }}</span>
            </el-option>
          </el-select>
        </template>
        <template #cell-color="{ row }">
          <el-input v-model="row.color" size="small" :placeholder="$tt('颜色')" :disabled="!editable" />
        </template>
        <template #cell-remark="{ row }">
          <el-input v-model="row.remark" size="small" :placeholder="$tt('备注')" :disabled="!editable" />
        </template>
        <!-- 纯展示列（type: textDash / fixedZero）：组件无这两个展示分支，由页面插槽保持迁移前口径 -->
        <template #cell-materialCode="{ row }"><span>{{ row.materialCode || '-' }}</span></template>
        <template #cell-specification="{ row }"><span>{{ row.specification || '-' }}</span></template>
        <!-- 单位列由明细通用组件默认渲染（统一单位单元格：候选=该物料换算单位），2026-11-11 统一 -->
        <template #cell-amount><span>0.00</span></template>
      </BillItemsTable>
    </el-form>

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
          type="free-sample"
          :disabled="!editable"
          button-text="上传附件"
          tip-text="支持图片、PDF、Word、Excel 等文件"
        />
      </div>
    </div>

    <!-- 审核流转（真实审批进度，通用组件） -->
    <ApprovalFlowTimeline bill-type="FREE_SAMPLE" :bill-id="form.sampleId" :status="form.status" />

    <!-- 发货弹窗 -->
    <el-dialog v-model="shipVisible" :title="$tf('发货登记')" width="450px">
      <el-form label-width="100px">
        <el-form-item :label="$tf('单号')">
          <span>{{ form.sampleNo }}</span>
        </el-form-item>
        <el-form-item :label="$tf('快递公司')" required>
          <el-input v-model="shipForm.expressCompany" :placeholder="$tt('请输入快递公司')" />
        </el-form-item>
        <el-form-item :label="$tf('快递单号')" required>
          <el-input v-model="shipForm.expressNo" :placeholder="$tt('请输入快递单号')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="shipVisible = false">{{ $tt("取消") }}</el-button>
        <el-button v-permission="perm('dlgConfirm')" type="primary" @click="confirmShip" :loading="saving">{{ $tt("确认发货") }}</el-button>
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

    <!-- 操作日志弹窗 -->
    <ChangeRecordDialog v-model="changeRecordVisible" bill-type="免费样品" :bill-no="form.sampleNo || ''" />

    <!-- 打印预览弹窗 -->
    <PrintPreviewDialog v-model="printVisible" bill-type="FREE_SAMPLE" :bill-id="form.sampleId" />
      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('来源单据')" name="source" :disabled="!form.sampleId">
        <BillLinkPanel v-if="topTab === 'source'" bill-type="FREE_SAMPLE" :bill-id="form.sampleId" mode="source" />
      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('去向单据')" name="target" :disabled="!form.sampleId">
        <BillLinkPanel v-if="topTab === 'target'" bill-type="FREE_SAMPLE" :bill-id="form.sampleId" mode="target" />
      </el-tab-pane>
    </el-tabs>
  </div>
  </template>

<script setup>
import { withFallbackOption } from '@/utils/labelFallback'
import { ref, reactive, computed, onMounted , watch, h} from 'vue'
import BillLinkPanel from '@/components/bill/BillLinkPanel.vue'
import ApprovalFlowTimeline from '@/components/bill/ApprovalFlowTimeline.vue'
import { isBillLinkRegistered } from '@/api/billLink'
import { useBaseDataRefresh } from '@/composables/useBaseDataRefresh'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification} from 'element-plus'
import { Clock, Memo, ArrowRight } from '@element-plus/icons-vue'
import {
  getFreeSample, saveFreeSample, updateFreeSample,
  submitFreeSample, approveFreeSample, shipFreeSample, completeFreeSample,
  generateFreeSampleNo,
  deleteFreeSample, rejectFreeSample, reverseApproveFreeSample,
  voidFreeSample, unvoidFreeSample, closeOrderFreeSample, reopenFreeSample
} from '@/api/freesample/freeSample'
import { listAllCustomer } from '@/api/basedata/customer'
import { listAllWarehouse } from '@/api/basedata/warehouse'
import { listAllMaterial } from '@/api/basedata/material'
import {
  loadCustomFieldDefinitions,
  loadCustomFieldValues,
  saveCustomFieldValues,
  batchLoadCustomFieldValues,
  customFieldsToTableColumns,
  injectMaterialCustomFields,
  validateItemCustomRequired,
  remapItemIdsAfterSave,
} from '@/composables/useCustomField'
import ChangeRecordDialog from '@/components/ChangeRecordDialog.vue'
// 明细通用组件（卡片折叠/列设置/动态列/自定义字段列/汇总 全站共用一套）
import BillItemsTable from '@/components/bill/BillItemsTable.vue'
import { useBillLineStock, BILL_STOCK_COLUMNS } from '@/composables/useBillLineStock'
import { triggerListRefresh } from '@/composables/useAutoRefresh'
import { useAutoDocCode } from '@/composables/useAutoDocCode'

import * as BillStatus from '@/utils/bill-status'
import BillActions from '@/components/bill/BillActions.vue'
import PrintPreviewDialog from '@/components/PrintPreviewDialog.vue'
import FileUploader from '@/components/FileUploader.vue'
import { useBillActions } from '@/composables/useBillActions'
import { useCopyBill } from '@/composables/useCopyBill'
import { validateBillRequiredFields } from '@/utils/billValidation'
import { useFieldPermission } from '@/composables/useFieldPermission'
import { useFieldRequiredMark } from '@/utils/fieldRequiredMark'
import { confirmSubmit, submitSuccessMessage } from '@/utils/submit-confirm'
import { tt } from '@/i18n'
import { useMaterialCustomFields } from '@/composables/useMaterialCustomFields'
// 物料下拉的输入过滤 + 背景提示文字（通用件）
import { filterMaterials, buildMaterialPlaceholder } from '@/utils/materialFilter'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('sample:freesample-list')

// 明细卡片折叠（通用组件 BillItemsTable 的 v-model:collapsed；原页 el-card 无折叠 → 默认展开）
const collapsed = reactive({ items: true, attach: true })
// 附件卡片折叠开关（本页原先只有明细折叠，无此函数 → 附件卡片需补一个）
function toggleCard(key) {
  collapsed[key] = !collapsed[key]
}
// 样品明细列定义（通用组件 BillItemsTable 消费，声明顺序 = 迁移前表格渲染顺序）：editor = 单元格控件；
// type 为纯展示语义（textDash = 空值显示 -、fixedZero = 固定显示 0.00，组件无这两个分支 → 由页面 #cell-<prop> 插槽渲染）；
// 自定义字段列改由 :custom-columns 传入（组件内部按 fieldType 渲染，并统一并入列设置）
const detailColumns = [
  { prop: 'materialId', label: '商品名称', minWidth: 220 },
  { prop: 'materialCode', label: '商品编号', width: 130, type: 'textDash' },
  { prop: 'quantity', label: '数量', width: 140, editor: 'number', precision: 2, change: () => calcTotal() },
  { prop: 'color', label: '颜色', width: 130 },
  { prop: 'remark', label: '备注', minWidth: 150 },
  ...BILL_STOCK_COLUMNS,
  { prop: 'specification', label: '规格', width: 130, type: 'textDash' },
  { prop: 'unitName', label: '单位', width: 90, type: 'textDash' },
  { prop: 'amount', label: '金额', width: 100, align: 'right', type: 'fixedZero' }
]
// 汇总区（通用组件按此渲染，口径与迁移前一致：总数量 = 明细数量合计、总金额固定 0.00）
const summaryItems = computed(() => [
  { label: '总数量：', value: form.totalQuantity },
  { label: '总金额：', value: '0.00' }
])
useBillLineStock(() => form.items)

const route = useRoute()
const router = useRouter()
const saving = ref(false)
const formRef = ref()
const changeRecordVisible = ref(false)
const { docCodeDisabled, docCodePlaceholder, autoFillDocCodeOnCreate } = useAutoDocCode('FREE_SAMPLE_ORDER')

const headerCustomSections = ref([])
const headerCustomFieldMap = new Map()
const headerFormulaFields = ref([])
const itemCustomFields = ref([])
const itemCustomColumns = ref([])
const materialCustomValueMap = ref({})

async function loadCustomFields() {
  const headerResult = await loadCustomFieldDefinitions('free_sample_order')
  headerCustomSections.value = headerResult.sections
  headerCustomFieldMap.clear()
  headerResult.fieldMap.forEach((v, k) => headerCustomFieldMap.set(k, v))
  headerFormulaFields.value = headerResult.formulaFields

  const itemResult = await loadCustomFieldDefinitions('free_sample_order_item')
  itemCustomFields.value = Array.from(itemResult.fieldMap.values())
  itemCustomColumns.value = customFieldsToTableColumns(itemCustomFields.value, { editable: isDraft.value, listShowOnly: false })
// 草稿态变化时重新生成明细自定义字段列（editable 控制）
watch(isDraft, () => {
  if (itemCustomFields.value && itemCustomFields.value.length > 0) {
    itemCustomColumns.value = customFieldsToTableColumns(itemCustomFields.value, { editable: isDraft.value, listShowOnly: false });
  }
});

}

const customerOptions = ref([])
const warehouseOptions = ref([])
const materialOptions = ref([])

// 物料自定义字段：行内下拉附加展示（逻辑集中在 composable）
const mcf = useMaterialCustomFields()

// 参与搜索的物料自定义字段：勾了「可搜索」的 ∪（下拉显示开关开启且勾了「下拉显示」的）
const materialSearchableFields = computed(() => (mcf.allFields.value || []).filter(f =>
  f && (f.isSearch === 1 || (mcf.enabled.value && f.isDropShow === 1))))
// 物料下拉的背景提示文字：声明可搜索范围（没有可搜索的自定义字段时原样返回「选择物料」）
const materialSelectPlaceholder = computed(() => buildMaterialPlaceholder(tt('选择物料'), materialSearchableFields.value))

/** 明细行物料下拉：输入即过滤（编码/名称/规格 + 自定义字段值/字段名） */
function materialFilterMethod(val, row) {
  if (!val) { row._filteredMaterials = null; return }
  // 用「已预取全量值」的 mcf.valueMap，不能只用已选中物料的值表，否则搜不到
  row._filteredMaterials = filterMaterials(materialOptions.value, val, mcf.valueMap.value, materialSearchableFields.value)
}

const isEdit = computed(() => !!route.query.id)

// 仅草稿态允许编辑（单号/业务字段/自定义字段）；已生效/待审批等只读
const editable = computed(() => form.status === '草稿' || !form.status)

const form = reactive({
  sampleId: null,
  sampleNo: '',
  customerId: null,
  customerName: '',
  sampleDate: new Date().toISOString().slice(0, 10),
  warehouseId: null,
  totalQuantity: 0,
  totalAmount: 0,
  expressCompany: '',
  expressNo: '',
  status: '草稿',
  remark: '',
  attachmentUrls: '',
  items: []
})


const isDraft = computed(() => BillStatus.canEdit(form.status, form.closeStatus))
const rules = {
  customerId: [{ required: true, message: tt('请选择客户'), trigger: 'change' }],
  sampleDate: [{ required: true, message: tt('请选择日期'), trigger: 'change' }],
  warehouseId: [{ required: true, message: tt('请选择仓库'), trigger: 'change' }]
}

function statusTagType(s) {
  const map = { '草稿': 'info', '已提交': 'warning', '已生效': 'primary', '已发货': 'warning', '已完成': 'success', '已取消': 'danger' }
  return map[s] || 'info'
}

function goBack() {
  router.push('/freesample/list')
}

// 加载下拉
async function loadDropdownOptions() {
  try {
    const list = await listAllCustomer()
    customerOptions.value = Array.isArray(list) ? list : (list?.records || [])
  } catch (e) { console.warn('加载客户失败', e) }
  try {
    const list = await listAllWarehouse()
    warehouseOptions.value = Array.isArray(list) ? list : (list?.records || [])
  } catch (e) { console.warn('加载仓库失败', e) }
  try {
    const list = await listAllMaterial()
    materialOptions.value = Array.isArray(list) ? list : (list?.records || [])
    // 行内下拉附加字段：物料到位后按批预取（不阻塞渲染）
    mcf.preloadAll(materialOptions.value)
  } catch (e) { console.warn('加载物料失败', e) }
}

function onCustomerChange(v) {
  const c = customerOptions.value.find(x => x.customerId === v)
  form.customerName = c ? c.customerName : ''
}

function onMaterialChange(row, v) {
  const m = materialOptions.value.find(x => x.materialId === v)
  if (m) {
    row.materialCode = m.materialCode
    row.materialName = m.materialName
    row.specification = m.model
    // 单位主键随物料带出（默认基本单位，明细行可改为辅单位；库存按折算后的基本单位记账）
    row.unitId = m.baseUnitId
    row.unitName = m.baseUnitName
    if (m && m.materialId && itemCustomFields.value.length > 0) {
      if (!materialCustomValueMap.value[m.materialId]) {
        batchLoadCustomFieldValues('material', [m.materialId]).then(map => {
          Object.assign(materialCustomValueMap.value, map)
          injectMaterialCustomFields(row, m.materialId, materialCustomValueMap.value, itemCustomFields.value)
        }).catch(() => {})
      } else {
        injectMaterialCustomFields(row, m.materialId, materialCustomValueMap.value, itemCustomFields.value)
      }
    }
  }
  calcTotal()
}

function addItem() {
  form.items.push({
    materialId: null,
    materialCode: '',
    materialName: '',
    specification: '',
    quantity: 0,
    unitId: null,
    unitName: '',
    color: '',
    remark: ''
  })
}

function calcTotal() {
  form.totalQuantity = form.items.reduce((s, r) => s + Number(r.quantity || 0), 0).toFixed(2)
  form.totalAmount = 0
}

// 加载详情
async function loadDetail() {
  const id = route.query.id
  if (!id || id === 'undefined') {
    autoFillDocCodeOnCreate(form, 'sampleNo')
    return
  }
  try {
    const data = await getFreeSample(id)
    Object.assign(form, data)
    // 后端实体字段为 orderId/orderNo，前端统一映射为 sampleId/sampleNo
    form.sampleId = data.orderId ?? data.sampleId ?? null
    form.sampleNo = data.orderNo ?? data.sampleNo ?? ''
    // 日期同理：后端实体字段为 orderDate，前端为 sampleDate（2026-09-20 补映射，此前日期不落库）
    form.sampleDate = data.orderDate ?? data.sampleDate ?? form.sampleDate
    if (!form.items) form.items = []
      if (itemCustomFields.value.length > 0 && data.items && data.items.length > 0) {
        const itemIds = data.items.map(it => it.itemId || it.sampleOrderItemId).filter(Boolean)
        if (itemIds.length > 0) {
          const itemValuesMap = await batchLoadCustomFieldValues('free_sample_order_item', itemIds)
          form.items.forEach(row => {
            const itemId = row.itemId || row.sampleOrderItemId
            const values = itemValuesMap[itemId]
            if (values) Object.assign(row, values)
          })
        }
      }
    if (headerCustomFieldMap.size > 0 && form.sampleId) {
      const headerValues = await loadCustomFieldValues('free_sample_order', form.sampleId)
      Object.assign(form, headerValues)
    }
    if (itemCustomFields.value.length > 0 && form.items && form.items.length > 0) {
      const itemIds = form.items.map(it => it.itemId || it.id).filter(Boolean)
      if (itemIds.length > 0) {
        const itemValuesMap = await batchLoadCustomFieldValues('free_sample_order_item', itemIds)
        form.items.forEach(row => {
          const itemId = row.itemId || row.id
          const values = itemValuesMap[itemId]
          if (values) Object.assign(row, values)
        })
      }
    }
    calcTotal()
  } catch (e) {
    ElMessage.error(e.message || tt('加载详情失败'))
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

// 保存
async function onSave(silent, opts = {}) {
  flushInputNumbers()
  // 宽松草稿保存：不校验必填，允许无明细（可后续补全再提交）
  // 保存宽松：丢弃未选物料的空行（后端这些字段 NOT NULL，无法落库）
  form.items = (form.items || []).filter(r => r && r.materialId)
  saving.value = true
  try {
    form.totalAmount = 0
    let res
    // 提交前映射：后端实体字段为 orderDate/orderNo，前端为 sampleDate/sampleNo（此前未映射 → 录单日期/单号不落库）
    const payload = { ...form, orderDate: form.sampleDate, orderNo: form.sampleNo }
    if (form.sampleId) {
      res = await updateFreeSample(form.sampleId, payload)
    } else {
      res = await saveFreeSample(payload)
      if (res && (res.orderId || res.sampleId || res.id)) {
        // 后端实体字段为 orderId/orderNo，前端统一映射为 sampleId/sampleNo
        form.sampleId = res.orderId || res.sampleId || res.id
        form.sampleNo = res.orderNo || res.sampleNo || form.sampleNo
        if (!opts.skipRouteSync && !route.query.id && form.sampleId) {
          try { await router.replace({ query: { ...route.query, id: String(form.sampleId) } }) } catch (_) {}
        }
      }
    }
    if (headerCustomFieldMap.size > 0 && form.sampleId) {
      const headerValues = {}
      for (const key of headerCustomFieldMap.keys()) {
        if (key in form) headerValues[key] = form[key]
      }
      await saveCustomFieldValues('free_sample_order', form.sampleId, headerValues, headerFormulaFields.value)
    }
    if (itemCustomFields.value.length > 0 && form.items && form.items.length > 0) {
      // 保存主表时后端走"明细先删后插"，明细主键每次保存都会变（实测首保存无主键、再保存主键换新）。
      // 必须先按保存后的**新主键**回填行主键，再用新主键写明细级自定义字段值，否则值会写到
      // 已删除的旧行上 → 编辑页再打开读不到、报表里那一列永远空白（与打样单同源问题）。
      try {
        const fresh = await getFreeSample(form.sampleId)
        remapItemIdsAfterSave(form.items, (fresh && fresh.items) || [], 'itemId')
      } catch (e) {
        console.warn('[freeSample] 保存后刷新明细主键失败，明细级自定义字段可能挂到旧行', e)
      }
      const itemKeys = new Set(itemCustomFields.value.map(f => f.fieldKey))
      for (let i = 0; i < form.items.length; i++) {
        const row = form.items[i]
        const itemId = row.itemId || row.id
        if (!itemId) continue
        const values = {}
        for (const key of itemKeys) {
          if (key in row) values[key] = row[key]
        }
        await saveCustomFieldValues('free_sample_order_item', itemId, values)
      }
    }
    triggerListRefresh('/freesample/samples')
    if (!silent) ElMessage.success(tt('保存成功'))

    await loadDetail()
    return true
  } catch (e) {
    ElMessage.error(e.message || tt('保存失败'))
  return false
  } finally {
    saving.value = false
  }
}

async function onSubmit() {
  flushInputNumbers()
    // 前端必填校验（B功能）
    // 统一校验头表单（本页仅有 formRef 一个 el-form）；失败时精确列出缺失字段名
  const missing = await validateBillRequiredFields({ formRef, rules, $el: document, pageKey: 'free_sample_order', model: form })
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
      const target = formRef && formRef.value ? formRef.value : null
      if (target && typeof target.scrollToField === 'function') target.scrollToField(missing[0].prop)
    } catch(_) {}
    return
  }

  // 无论新建还是已有草稿，提交前一律先静默保存（把当前页面所见落库，避免提交旧数据）
  const saved = await onSave(true, { skipRouteSync: true })
  if (!saved || !form.sampleId) return

  try {
    const needApproval = await confirmSubmit({ billType: 'FREE_SAMPLE', billLabel: '样品单' })
    await submitFreeSample(form.sampleId)
    ElMessage.success(submitSuccessMessage(needApproval))
    triggerListRefresh('/freesample/samples')
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('提交失败'))
  } finally {
    // 提交（或用户取消）后再把新单 id 写回地址栏：此时重建实例无副作用，新实例挂载时会重新拉取最新状态
    if (!route.query.id && form.sampleId) {
      try { await router.replace({ query: { ...route.query, id: String(form.sampleId) } }) } catch (_) {}
    }
  }
}

async function onApprove() {
  try {
    await ElMessageBox.confirm(tt('确认审核该样品单？'), tt('提示'), { type: 'warning' })
    await approveFreeSample(form.sampleId)
    ElMessage.success(tt('审核成功'))
    triggerListRefresh('/freesample/samples')
    await loadDetail()
  } catch (e) { /* 取消 */ }
}

// 发货
const shipVisible = ref(false)
const shipForm = reactive({ expressCompany: '', expressNo: '' })

function onShip() {
  shipForm.expressCompany = form.expressCompany || ''
  shipForm.expressNo = form.expressNo || ''
  shipVisible.value = true
}

async function confirmShip() {
  if (!shipForm.expressCompany || !shipForm.expressNo) {
    ElMessage.warning(tt('请填写快递公司和快递单号'))
    return
  }
  saving.value = true
  try {
    await shipFreeSample(form.sampleId, shipForm.expressNo, shipForm.expressCompany)
    ElMessage.success(tt('发货成功'))
    shipVisible.value = false
    await loadDetail()
  } catch (e) {
    ElMessage.error(e.message || tt('发货失败'))
  } finally {
    saving.value = false
  }
}

async function onComplete() {
  try {
    await ElMessageBox.confirm(tt('确认完成该样品单？'), tt('提示'), { type: 'warning' })
    await completeFreeSample(form.sampleId)
    ElMessage.success(tt('完成成功'))
    await loadDetail()
  } catch (e) { /* 取消 */ }
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
  getId: () => form.sampleId || route.query.id || route.params.id,
  api: {
    approve: approveFreeSample,
    reject: rejectFreeSample,
    reverseApprove: reverseApproveFreeSample,
    void: voidFreeSample,
    unvoid: unvoidFreeSample,
    close: closeOrderFreeSample,
    reopen: reopenFreeSample,
    delete: deleteFreeSample
  },
  reload: loadDetail,
  router,
  listPath: '/freesample/list',
  newPath: '/freesample/edit',
  billLabel: '免费样品单'
})

// 复制：URL 带 ?copy=id 打开时自动把源单填入当前表单（复制按钮 handleCopy 由 useBillActions 负责跳到该地址）
const { applyCopy } = useCopyBill({
  route,
  loadFn: getFreeSample,
  form,
  idField: 'orderId',
  noField: 'orderNo',
  itemIdField: 'itemId',
  itemsField: 'items',
  afterCopy: () => calcTotal(),
  customField: { entity: 'free_sample_order', itemEntity: 'free_sample_order_item', itemIdField: 'itemId' }
})

// ===== 顶级页签：单据 / 来源单据 / 去向单据 =====
const topTab = ref('doc')
const billLinkRegistered = ref(false)

const { isFieldRequired } = useFieldPermission('free_sample_order')
useFieldRequiredMark('free_sample_order', '.erp-page')

onMounted(async () => {
  // 物料自定义字段：定制开关 + 字段定义（失败静默降级）
  await mcf.init()
  // 该单据类型是否登记了「来源单据/去向单据」配置（未登记则不显示这两个页签）
  isBillLinkRegistered('FREE_SAMPLE')
    .then(v => { billLinkRegistered.value = !!v })
    .catch(() => { billLinkRegistered.value = false })
  await loadCustomFields()
  loadDropdownOptions()
  const copied = await applyCopy()
  if (!copied) {
    loadDetail()
  }
})

// 切回标签页时自动刷新下拉基础数据（只刷下拉，不动表单草稿），避免档案改过后这里还是旧数据
useBaseDataRefresh(loadDropdownOptions)


  </script>

<style scoped>
.erp-page { padding: 16px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.title { font-size: 16px; font-weight: 600; margin-left: 10px; }
.detail-header { display: flex; justify-content: space-between; align-items: center; }
.summary-bar { margin-top: 12px; text-align: right; font-size: 14px; color: #606266; }

.detail-subtable-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  padding: 0 4px;
}
.detail-subtable-header .subtable-title {
  font-size: 14px;
  font-weight: bold;
  color: #303133;
}
.detail-subtable-header .subtable-title::before {
  content: '';
  display: inline-block;
  width: 3px;
  height: 14px;
  background: #409eff;
  margin-right: 6px;
  vertical-align: middle;
}

/* 附件卡片（可折叠）：与其它单据页 .collapsible-card 同口径；本页样式为纯 CSS，故写得平铺 */
.collapsible-card { background: #fff; border: 1px solid #e4e7ed; border-radius: 6px; margin-bottom: 10px; }
.collapsible-card.collapsed .card-header { border-bottom: none; }
.collapsible-card .card-header { display: flex; justify-content: space-between; align-items: center; padding: 8px 14px; background: #fafbfc; border-bottom: 1px solid #e4e7ed; cursor: pointer; user-select: none; }
.collapsible-card .card-header .header-left { display: flex; align-items: center; gap: 8px; }
.collapsible-card .card-header .collapse-icon { color: #909399; font-size: 12px; transition: transform .25s ease; }
.collapsible-card .card-header .collapse-icon.active { transform: rotate(90deg); color: #409eff; }
.collapsible-card .card-header .card-title { font-size: 13px; font-weight: 600; color: #303133; }
.collapsible-card .card-body { padding: 12px 14px; }
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
