<template>
  <div class="operation-report-edit">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div class="header-left">
        <div class="back-btn" @click="$router.push('/production/operation-report')">
          <el-icon><ArrowLeft /></el-icon>
          <span>{{ $tt("返回列表") }}</span>
        </div>
        <div class="title-area">
          <div class="title-row">
            <el-icon class="title-icon"><Document /></el-icon>
            <span class="page-title">{{ isEdit ? '作业汇报详情' : '新增作业汇报' }}</span>
            <span v-if="form.reportNo" class="order-no">
              <el-icon><Collection /></el-icon>
              {{ form.reportNo }}
            </span>
            <el-tag v-if="form.status" size="small" :type="statusTagType(form.status)" effect="plain" class="status-tag">{{ $dt(form.status) }}</el-tag>
          </div>
        </div>
      </div>
      <div class="header-right">
        <el-button v-permission="perm('view')" v-if="!(!form.orderId && !form.deliveryId && !form.inboundId && !form.invoiceId && !form.voucherId && !form.contractId && !form.requisitionId && !form.stockCheckId && !form.transferId && !form.bomId && !form.sampleId && !form.freeSampleId && !form.workOrderId && !form.billId && !form.id)" @click="changeRecordVisible = true">
          <el-icon><Memo /></el-icon>{{ $tt('操作日志') }}
        </el-button>
        <PushDownButton v-if="canPushDown(form.status, form.closeStatus)" source-type="OperationReport" :source-doc="form" />
        <BillActions bill-type="OPERATION_REPORT"
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

    <!-- 基本信息卡片 -->
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
              <el-form-item :label="$tf('汇报单号')" v-if="isFieldVisible('reportNo')">
                <el-input v-model="form.reportNo" :placeholder="docCodePlaceholder" :disabled="!isDraft" class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('汇报日期')" prop="reportDate" v-if="isFieldVisible('reportDate')" :required="isFieldRequired('reportDate')">
                <el-date-picker v-model="form.reportDate" type="date" value-format="YYYY-MM-DD" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('使用设备')" v-if="isFieldVisible('equipmentId')" :required="isFieldRequired('equipmentId')">
                <el-input v-model="form.equipmentId" :placeholder="$tt('请输入使用设备')" class="field-input" :disabled="!isDraft" />
              </el-form-item>
            </div>
          </div>

          <!-- 工单与工序 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>工单与工序
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('工单号')" prop="productionOrderNo" v-if="isFieldVisible('productionOrderNo')" :required="isFieldRequired('productionOrderNo')">
                <el-select v-model="form.productionOrderNo" filterable :placeholder="$tt('选择工单')" class="field-input" :disabled="!isDraft" @change="onWorkOrderChange">
                  <el-option v-for="w in workOrderOptions" :key="w.orderNo" :label="w.orderNo" :value="w.orderNo" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('工序号')" prop="processNo" v-if="isFieldVisible('processNo')" :required="isFieldRequired('processNo')">
                <el-select v-model="form.processNo" filterable :placeholder="$tt('选择工序')" class="field-input" :disabled="!isDraft" @change="onProcessChange">
                  <el-option v-for="o in processOptions" :key="o.processNo" :label="`${o.processNo} - ${o.processName || o.operationName}`" :value="o.processNo" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('工序名称')" v-if="isFieldVisible('operationName')" :required="isFieldRequired('operationName')">
                <el-input v-model="form.operationName" :placeholder="$tt('选择工序后自动带出')" readonly class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('工作中心')" prop="workCenterId" v-if="isFieldVisible('workCenterId')" :required="isFieldRequired('workCenterId')">
                <el-select v-model="form.workCenterId" filterable :placeholder="$tt('选择工作中心')" class="field-input" :disabled="!isDraft">
                  <el-option v-for="c in workCenterOptions" :key="c.workCenterId" :label="c.workCenterName" :value="c.workCenterId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('操作工')" prop="employeeId" v-if="isFieldVisible('employeeId')" :required="isFieldRequired('employeeId')">
                <el-select v-model="form.employeeId" filterable :placeholder="$tt('选择操作工')" class="field-input" :disabled="!isDraft">
                  <el-option v-for="e in employeeOptions" :key="e.employeeId" :label="e.employeeName" :value="e.employeeId" />
                </el-select>
              </el-form-item>
            </div>
          </div>

          <!-- 数量与工时 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>数量与工时
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('合格数量')" prop="qualifiedQuantity" v-if="isFieldVisible('qualifiedQuantity')" :required="isFieldRequired('qualifiedQuantity')">
                <el-input-number v-model="form.qualifiedQuantity" :min="0" :precision="4" controls-position="right" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('报废数量')" v-if="isFieldVisible('rejectedQuantity')" :required="isFieldRequired('rejectedQuantity')">
                <el-input-number v-model="form.rejectedQuantity" :min="0" :precision="4" controls-position="right" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('实际工时')" prop="workHours" v-if="isFieldVisible('workHours')" :required="isFieldRequired('workHours')">
                <el-input-number v-model="form.workHours" :min="0" :precision="4" controls-position="right" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('报工数量')" v-if="isFieldVisible('reportTotal')" :required="isFieldRequired('reportTotal')">
                <el-input-number v-model="reportTotal" :precision="4" controls-position="right" class="field-input" disabled />
              </el-form-item>
              <el-form-item :label="$tf('不合格数量')" v-if="isFieldVisible('unqualifiedQty')" :required="isFieldRequired('unqualifiedQty')">
                <el-input-number v-model="unqualifiedQty" :precision="4" controls-position="right" class="field-input" disabled />
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
                <el-input v-model="form.remark" type="textarea" :rows="2" class="field-input" :disabled="!isDraft" :placeholder="$tt('请输入备注信息')" />
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

    <!-- 附件卡片 -->
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
          type="operation-report"
          :disabled="!isDraft"
          button-text="上传附件"
          tip-text="支持图片、PDF、Word、Excel 等文件"
        />
      </div>
    </div>

    <!-- 审核流转（真实审批进度，通用组件） -->
    <ApprovalFlowTimeline bill-type="OPERATION_REPORT" :bill-id="form.id" :status="form.status" />

    <!-- 打印预览弹窗 -->
    <PrintPreviewDialog v-model="printVisible" bill-type="OPERATION_REPORT" :bill-id="form.id" />

    <!-- 操作日志弹窗 -->
    <ChangeRecordDialog v-model="changeRecordVisible" bill-type="OPERATION_REPORT" :bill-id="form.id" />

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

      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('来源单据')" name="source" :disabled="!form.id">
        <BillLinkPanel v-if="topTab === 'source'" bill-type="OPERATION_REPORT" :bill-id="form.id" mode="source" />
      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('去向单据')" name="target" :disabled="!form.id">
        <BillLinkPanel v-if="topTab === 'target'" bill-type="OPERATION_REPORT" :bill-id="form.id" mode="target" />
      </el-tab-pane>
    </el-tabs>
  </div>
  </template>

<script setup>
import { ref, reactive, computed, onMounted, h} from 'vue'
import { useBaseDataRefresh } from '@/composables/useBaseDataRefresh'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification} from 'element-plus'
import {
  getOperationReport, saveOperationReport, updateOperationReport,
  deleteOperationReport, submitOperationReport, approveOperationReport,
  rejectOperationReport, reverseApproveOperationReport,
  closeOrderOperationReport, reopenOperationReport,
  voidOperationReport, unvoidOperationReport
} from '@/api/production/operationReport'
import { useBillActions } from '@/composables/useBillActions'
import { useCopyBill } from '@/composables/useCopyBill'
import { listAllWorkCenter } from '@/api/production/workCenter'
import { pageWorkOrder } from '@/api/production/workOrder'
import { listAllEmployee } from '@/api/basedata/employee'
import FileUploader from '@/components/FileUploader.vue'
import PrintPreviewDialog from '@/components/PrintPreviewDialog.vue'
import ApprovalFlowTimeline from '@/components/bill/ApprovalFlowTimeline.vue'
import BillLinkPanel from '@/components/bill/BillLinkPanel.vue'
import { isBillLinkRegistered } from '@/api/billLink'
import ChangeRecordDialog from '@/components/ChangeRecordDialog.vue'
import PushDownButton from '@/components/PushDownButton.vue'
import { canPushDown } from '@/utils/bill-status'
import {
  ArrowLeft, ArrowRight, ArrowDown, Document, Collection, Check, TopRight,
  CircleCheck, RefreshLeft, Delete, Printer, Clock, Memo
} from '@element-plus/icons-vue'
import { useAutoDocCode } from '@/composables/useAutoDocCode'

import * as BillStatus from '@/utils/bill-status'
import BillActions from '@/components/bill/BillActions.vue'
import { validateBillRequiredFields } from '@/utils/billValidation'
import { useFieldRequiredMark } from '@/utils/fieldRequiredMark'
import { confirmSubmit, submitSuccessMessage } from '@/utils/submit-confirm'
import { triggerListRefresh } from '@/composables/useAutoRefresh'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'
import { useFieldPermission } from '@/composables/useFieldPermission'
// 自定义字段（表头）：加载定义/回显值/保存值
import {
  loadCustomFieldDefinitions,
  loadCustomFieldValues,
  saveCustomFieldValues
} from '@/composables/useCustomField'

const perm = usePagePerm('production:opreport')
const { isFieldVisible, isFieldRequired } = useFieldPermission('operation_report')

const route = useRoute()
const router = useRouter()

// 是否编辑模式（路由含 :id 参数）
const isEdit = computed(() => !!route.params.id)
// 草稿状态才能编辑
const { docCodeDisabled, docCodePlaceholder, autoFillDocCodeOnCreate } = useAutoDocCode('OPERATION_REPORT')

const saving = ref(false)
const headerFormRef = ref(null)
const printVisible = ref(false)
const changeRecordVisible = ref(false)

// 折叠卡片状态
const collapsed = reactive({
  base: true,
  attach: true,
  audit: true
})

function toggleCard(key) {
  collapsed[key] = !collapsed[key]
}

// 表单数据
const form = reactive({
  id: null,
  reportNo: '',
  reportDate: new Date().toISOString().slice(0, 10),
  productionOrderNo: '',
  processNo: '',
  operationName: '',
  workCenterId: null,
  workCenterName: '',
  employeeId: null,
  employeeName: '',
  qualifiedQuantity: 0,
  rejectedQuantity: 0,
  workHours: 0,
  equipmentId: '',
  status: '草稿',
  remark: '',
  attachmentUrls: '',
  createdAt: '',
  approvedAt: ''
})

const isDraft = computed(() => BillStatus.canEdit(form.status, form.closeStatus))

// ===== 自定义字段（表头 operation_report）=====
const headerCustomSections = ref([])
const headerCustomFieldMap = new Map()
const headerFormulaFields = ref([])

async function loadCustomFields() {
  const headerResult = await loadCustomFieldDefinitions('operation_report')
  headerCustomSections.value = headerResult.sections
  headerCustomFieldMap.clear()
  headerResult.fieldMap.forEach((v, k) => headerCustomFieldMap.set(k, v))
  headerFormulaFields.value = headerResult.formulaFields
}
// 选项数据
const workOrderOptions = ref([])
const workCenterOptions = ref([])
const employeeOptions = ref([])
const processOptions = ref([])

// 表单校验规则
const rules = {
  reportDate: [{ required: true, message: tt('请选择汇报日期'), trigger: 'change' }],
  productionOrderNo: [{ required: true, message: tt('请选择工单'), trigger: 'change' }],
  processNo: [{ required: true, message: tt('请选择工序'), trigger: 'change' }],
  workCenterId: [{ required: true, message: tt('请选择工作中心'), trigger: 'change' }],
  employeeId: [{ required: true, message: tt('请选择操作工'), trigger: 'change' }],
  qualifiedQuantity: [{ required: true, message: tt('请输入合格数量'), trigger: 'blur' }],
  workHours: [{ required: true, message: tt('请输入实际工时'), trigger: 'blur' }]
}

// 报工数量 = 合格 + 报废
const reportTotal = computed(() => Number(form.qualifiedQuantity || 0) + Number(form.rejectedQuantity || 0))
// 不合格数量 = 报废数量
const unqualifiedQty = computed(() => Number(form.rejectedQuantity || 0))

// 状态标签类型
function statusTagType(s) {
  return { '草稿': 'info', '待审核': 'warning', '已生效': 'success' }[s] || 'info'
}

// 加载选项数据
async function loadOptions() {
  try {
    const [wcs, wos, emps] = await Promise.all([
      listAllWorkCenter(),
      pageWorkOrder({ current: 1, size: 1000 }),
      listAllEmployee()
    ])
    workCenterOptions.value = wcs || []
    workOrderOptions.value = (wos && wos.records) ? wos.records : (wos || [])
    employeeOptions.value = emps || []
  } catch (e) { /* 忽略选项加载失败 */ }
}

// 工单变更时加载工艺路线下的工序
async function onWorkOrderChange(orderNo) {
  form.processNo = ''
  form.operationName = ''
  processOptions.value = []
  try {
    const w = workOrderOptions.value.find(x => x.orderNo === orderNo)
    if (w && w.routingId) {
      const routingDetail = await import('@/api/production/routing').then(m => m.getRouting(w.routingId))
      if (routingDetail && routingDetail.operations) {
        processOptions.value = routingDetail.operations.map(op => ({
          processNo: op.processNo || op.operationCode,
          processName: op.processName || op.operationName,
          operationName: op.processName || op.operationName,
          workCenterId: op.workCenterId
        }))
      }
    }
  } catch (e) { /* 工序加载失败忽略 */ }
}

// 工序变更时同步工序名称和工作中心
function onProcessChange(processNo) {
  const p = processOptions.value.find(x => x.processNo === processNo)
  if (p) {
    form.operationName = p.operationName
    if (p.workCenterId) form.workCenterId = p.workCenterId
  }
}

// 保存（新建/编辑）
async function onSave(silent, opts = {}) {
  // 宽松草稿保存：不校验必填，允许核心字段为空（可后续补全再提交）
  saving.value = true
  try {
    const payload = { ...form }
    const wc = workCenterOptions.value.find(x => x.workCenterId === form.workCenterId)
    if (wc) payload.workCenterName = wc.workCenterName
    const emp = employeeOptions.value.find(x => x.employeeId === form.employeeId)
    if (emp) payload.employeeName = emp.employeeName
    if (isEdit.value) {
      await updateOperationReport(form.id, payload)
    } else {
      const res = await saveOperationReport(payload)
      if (res && (res.id || res.reportId)) {
        const newId = res.id || res.reportId
        form.id = newId
        if (!opts.skipRouteSync) router.replace(`/production/operation-reports/edit/${newId}`)
      } else {
        if (!opts.skipRouteSync) router.replace('/production/operation-report')
      }
    }
    if (headerCustomFieldMap.size > 0 && form.id) {
      const headerValues = {}
      for (const key of headerCustomFieldMap.keys()) {
        if (key in form) headerValues[key] = form[key]
      }
      await saveCustomFieldValues('operation_report', form.id, headerValues, headerFormulaFields.value)
    }
    triggerListRefresh('/production/op-reports')
    if (!silent) ElMessage.success(tt('保存成功'))
    return true
  } catch (e) {
    ElMessage.error(e.message || tt('保存失败'))
    return false
  } finally {
    saving.value = false
  }
}

// 提交审核
async function onSubmit() {
    // 前端必填校验（B功能）
    // 统一校验：头表单 + 明细内表单 + 任何 formRef；失败时精确列出缺失字段名
  const missing = await validateBillRequiredFields({ headerFormRef, rules, $el: document, pageKey: 'operation_report', model: form })
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
    const needApproval = await confirmSubmit({ billType: 'OPERATION_REPORT', billLabel: '工序汇报单' })
    await submitOperationReport(form.id)
    ElMessage.success(submitSuccessMessage(needApproval))
    triggerListRefresh('/production/op-reports')
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('提交失败'))
  } finally {
    // 提交（或用户取消）后再把新单 id 写回地址栏：此时重建实例无副作用，新实例挂载时会重新拉取最新状态
    if (!route.params.id && form.id) {
      try { await router.replace(`/production/operation-reports/edit/${form.id}`) } catch (_) {}
    }
  }
}

// 重置草稿（重置为草稿）
async function onReverseApprove() {
  await ElMessageBox.confirm(tt('确认重置草稿？单据将恢复为草稿状态'), tt('确认'), { type: 'warning' })
  try {
    // 复用 update 接口，状态置为草稿
    await updateOperationReport(form.id, { ...form, status: '草稿' })
    ElMessage.success(tt('已重置草稿'))
    await loadDetail()
  } catch (e) { /* 拦截器已提示 */ }
}

// 删除
async function onDelete() {
  await ElMessageBox.confirm(tt('确认删除该汇报单？删除后不可恢复'), tt('确认'), { type: 'warning' })
  try {
    await deleteOperationReport(form.id)
    ElMessage.success(tt('删除成功'))
    router.replace('/production/operation-report')
  } catch (e) { /* 拦截器已提示 */ }
}

// 更多操作
// 加载详情
async function loadDetail() {
  if (!isEdit.value) return
  try {
    const data = await getOperationReport(route.params.id)
    Object.assign(form, {
      // 接口主键字段是 reportId（无 id）→ 必须兼容映射，否则 form.id 恒空：
      // 表现为复制提示"请先保存"、保存走新建、提交/审核/作废按钮全部不出现（2026-09-28 实测）
      id: data.id || data.reportId,
      reportNo: data.reportNo || '',
      reportDate: data.reportDate || new Date().toISOString().slice(0, 10),
      productionOrderNo: data.productionOrderNo || '',
      processNo: data.processNo || '',
      operationName: data.operationName || '',
      workCenterId: data.workCenterId || null,
      workCenterName: data.workCenterName || '',
      employeeId: data.employeeId || null,
      employeeName: data.employeeName || '',
      qualifiedQuantity: data.qualifiedQuantity || 0,
      rejectedQuantity: data.rejectedQuantity || 0,
      workHours: data.workHours || 0,
      equipmentId: data.equipmentId || '',
      status: data.status || '草稿',
      remark: data.remark || '',
      attachmentUrls: data.attachmentUrls || '',
      createdAt: data.createdAt || data.createTime || '',
      approvedAt: data.approvedAt || data.approveTime || ''
    })
    if (headerCustomFieldMap.size > 0 && form.id) {
      const headerValues = await loadCustomFieldValues('operation_report', form.id)
      Object.assign(form, headerValues)
    }
    // 若已有工单号，自动加载工序选项
    if (form.productionOrderNo) {
      await onWorkOrderChange(form.productionOrderNo)
      // 重新设置回当前工序号（onWorkOrderChange 会清空）
      form.processNo = data.processNo || ''
    }
  } catch (e) { /* 拦截器已提示 */ }
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
  getId: () => form.id || form.reportId,
  api: {
    approve: approveOperationReport,
    reject: rejectOperationReport,
    reverseApprove: reverseApproveOperationReport,
    close: closeOrderOperationReport,
    reopen: reopenOperationReport,
    void: voidOperationReport,
    unvoid: unvoidOperationReport,
    delete: deleteOperationReport
  },
  reload: loadDetail,
  router,
  listPath: '/production/operation-report',
  newPath: '/production/operation-reports/edit',
  billLabel: '报工单'
})

// ===== 复制功能 =====
const { applyCopy } = useCopyBill({
  route,
  loadFn: getOperationReport,
  form,
  noField: 'reportNo'
})

function handlePrint() {
  printVisible.value = true
}

// ===== 顶级页签：单据 / 来源单据 / 去向单据 =====
const topTab = ref('doc')
const billLinkRegistered = ref(false)

useFieldRequiredMark('operation_report', '.operation-report-edit')

onMounted(async () => {
  // 该单据类型是否登记了「来源单据/去向单据」配置（未登记则不显示这两个页签）
  isBillLinkRegistered('OPERATION_REPORT')
    .then(v => { billLinkRegistered.value = !!v })
    .catch(() => { billLinkRegistered.value = false })

  await loadCustomFields()
  await loadOptions()
  if (!isEdit.value) {
    autoFillDocCodeOnCreate(form, 'reportNo')
  }
  const copied = await applyCopy()
  if (!copied) {
    await loadDetail()
  }
})

// 切回标签页时自动刷新下拉基础数据（只刷下拉，不动表单草稿），避免档案改过后这里还是旧数据
useBaseDataRefresh(loadOptions)

  </script>

<style scoped lang="scss">
.operation-report-edit {
  padding: 16px 20px;
  background: linear-gradient(135deg, #f8faff 0%, #f0f4ff 50%, #f8fcff 100%);
  min-height: calc(100vh - 60px);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  padding: 14px 20px;
  border-radius: 10px;
  border: 1px solid #e5e7eb;
  margin-bottom: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);

  .header-left {
    display: flex;
    align-items: center;
    gap: 16px;
  }

  .back-btn {
    display: flex;
    align-items: center;
    gap: 4px;
    color: #6b7280;
    font-size: 13px;
    cursor: pointer;
    padding: 6px 12px;
    border-radius: 6px;
    background: #f9fafb;
    border: 1px solid #e5e7eb;
    transition: all .2s;
    &:hover {
      color: #6366f1;
      border-color: #c7d2fe;
      background: #eef2ff;
    }
  }

  .title-area { flex: 1; }

  .title-row {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  .title-icon {
    font-size: 20px;
    color: #6366f1;
  }

  .page-title {
    font-size: 18px;
    font-weight: 700;
    color: #111827;
  }

  .order-no {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 13px;
    color: #6366f1;
    background: linear-gradient(135deg, #eef2ff, #f5f3ff);
    padding: 3px 10px;
    border-radius: 6px;
    border: 1px solid #c7d2fe;
  }

  .status-tag {
    border-radius: 4px;
    font-weight: 600;
  }

  .header-right {
    display: flex;
    gap: 8px;
    align-items: center;
  }

  .more-btn {
    background: #f9fafb;
    border: 1px solid #e5e7eb;
    color: #4b5563;
    &:hover {
      background: #eef2ff;
      border-color: #c7d2fe;
      color: #6366f1;
    }
  }
}

.btn-primary-glow {
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  border: none;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
  &:hover {
    box-shadow: 0 6px 20px rgba(99, 102, 241, 0.4);
    transform: translateY(-1px);
  }
}

.collapsible-card {
  background: #fff;
  border-radius: 10px;
  border: 1px solid #e5e7eb;
  margin-bottom: 12px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  transition: all .2s;

  &:hover {
    border-color: #d1d5db;
  }

  &.collapsed .card-body {
    padding: 0;
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 18px;
    background: linear-gradient(90deg, #fafbff 0%, #f5f7ff 100%);
    border-bottom: 1px solid #f3f4f6;
    cursor: pointer;
    user-select: none;
    transition: background .2s;

    &:hover {
      background: linear-gradient(90deg, #f0f4ff 0%, #eef2ff 100%);
    }

    .header-left {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .collapse-icon {
      font-size: 14px;
      color: #9ca3af;
      transition: transform .25s;
      &.active {
        transform: rotate(90deg);
      }
    }

    .card-title {
      font-size: 14px;
      font-weight: 600;
      color: #1f2937;
    }

    .item-count {
      font-size: 12px;
      color: #6366f1;
      background: #eef2ff;
      padding: 2px 8px;
      border-radius: 10px;
    }
  }

  .card-body {
    padding: 18px 20px;
    transition: padding .2s;

    &.no-padding {
      padding: 0;
    }
  }
}

.base-form {
  .info-section {
    margin-bottom: 20px;

    &:last-child { margin-bottom: 0; }

    .section-title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 13px;
      font-weight: 600;
      color: #374151;
      margin-bottom: 14px;
      padding-bottom: 8px;
      border-bottom: 1px dashed #e5e7eb;

      .section-bar {
        display: inline-block;
        width: 3px;
        height: 14px;
        background: linear-gradient(180deg, #6366f1, #8b5cf6);
        border-radius: 2px;
      }
    }

    .section-fields {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 0 24px;

      :deep(.el-form-item) {
        margin-bottom: 14px;
      }

      .span-all {
        grid-column: 1 / -1;
      }

      .field-input {
        width: 100%;
      }
    }
  }
}

:deep(.el-button) {
  border-radius: 6px;
  font-weight: 500;
}

@media (max-width: 1024px) {
  .base-form .info-section .section-fields {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .operation-report-edit { padding: 12px; }
  .page-header {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
  }
  .base-form .info-section .section-fields {
    grid-template-columns: 1fr;
  }
}
</style>
