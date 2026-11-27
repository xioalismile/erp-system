<template>
  <div class="mrp-plan-edit">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div class="header-left">
        <div class="back-btn" @click="$router.push('/production/mrp-plan')">
          <el-icon><ArrowLeft /></el-icon>
          <span>{{ $tt("返回列表") }}</span>
        </div>
        <div class="title-area">
          <div class="title-row">
            <el-icon class="title-icon"><Document /></el-icon>
            <span class="page-title">{{ isEdit ? 'MRP方案详情' : '新增MRP方案' }}</span>
            <span v-if="form.planNo" class="order-no">
              <el-icon><Collection /></el-icon>
              {{ form.planNo }}
            </span>
            <el-tag v-if="form.status" size="small" :type="statusTagType(form.status)" effect="plain" class="status-tag">{{ $dt(form.status) }}</el-tag>
          </div>
        </div>
      </div>
      <div class="header-right">
        <el-button v-permission="perm('view')" v-if="!(!form.orderId && !form.deliveryId && !form.inboundId && !form.invoiceId && !form.voucherId && !form.contractId && !form.requisitionId && !form.stockCheckId && !form.transferId && !form.bomId && !form.sampleId && !form.freeSampleId && !form.workOrderId && !form.billId && !form.id)" @click="changeRecordVisible = true">
          <el-icon><Memo /></el-icon>{{ $tt('操作日志') }}
        </el-button>
        <PushDownButton v-if="canPushDown(form.status, form.closeStatus)" source-type="MrpPlan" :source-doc="form" />
        <!-- 重新计算（2026-10-06）：回到 MRP 运算向导，从选物料开始重走一遍 -->
        <el-button v-if="canRecalc" v-permission="perm('recalc')" @click="onRecalc">
          <el-icon><RefreshLeft /></el-icon>{{ $tt('重新计算') }}
        </el-button>
        <BillActions bill-type="MRP_PLAN"
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
        <el-form :model="form" :rules="rules" ref="headerFormRef" label-width="130px" class="base-form">
          <!-- 方案信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>方案信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('方案编号')" v-if="isFieldVisible('planNo')">
                <el-input v-model="form.planNo" :placeholder="docCodePlaceholder" :disabled="!isDraft" class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('方案名称')" prop="planName" required v-if="isFieldVisible('planName')">
                <el-input v-model="form.planName" :placeholder="$tt('如 2026年6月周MRP运算')" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('运算日期')" prop="planDate" required v-if="isFieldVisible('planDate')">
                <el-date-picker v-model="form.planDate" type="date" value-format="YYYY-MM-DD" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('计划展望期(天)')" prop="planHorizon" required v-if="isFieldVisible('planHorizon')">
                <el-input-number v-model="form.planHorizon" :min="1" controls-position="right" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('需求来源')" prop="demandSource" required v-if="isFieldVisible('demandSource')">
                <el-select v-model="form.demandSource" :placeholder="$tt('选择需求来源')" class="field-input" :disabled="!isDraft">
                  <el-option :label="$tf('销售订单')" value="销售订单" />
                  <el-option :label="$tf('预测')" value="预测" />
                  <el-option :label="$tf('安全库存')" value="安全库存" />
                  <el-option :label="$tf('手工录入')" value="手工录入" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('时界(天)')" prop="timeFenceDays" v-if="isFieldVisible('timeFenceDays')" :required="isFieldRequired('timeFenceDays')">
                <el-input-number v-model="form.timeFenceDays" :min="0" controls-position="right" class="field-input" :disabled="!isDraft" />
              </el-form-item>
            </div>
          </div>

          <!-- 运算范围 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>运算范围
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('包含销售订单')" v-if="isFieldVisible('includeSo')" :required="isFieldRequired('includeSo')">
                <el-switch v-model="form.includeSo" :active-value="1" :inactive-value="0" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('包含预测')" v-if="isFieldVisible('includeForecast')" :required="isFieldRequired('includeForecast')">
                <el-switch v-model="form.includeForecast" :active-value="1" :inactive-value="0" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('考虑安全库存')" v-if="isFieldVisible('includeSafetyStock')" :required="isFieldRequired('includeSafetyStock')">
                <el-switch v-model="form.includeSafetyStock" :active-value="1" :inactive-value="0" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('考虑在途采购')" v-if="isFieldVisible('includeOpenPo')" :required="isFieldRequired('includeOpenPo')">
                <el-switch v-model="form.includeOpenPo" :active-value="1" :inactive-value="0" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('考虑在制工单')" v-if="isFieldVisible('includeOpenMo')" :required="isFieldRequired('includeOpenMo')">
                <el-switch v-model="form.includeOpenMo" :active-value="1" :inactive-value="0" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('考虑现有库存')" v-if="isFieldVisible('includeOnhandStock')" :required="isFieldRequired('includeOnhandStock')">
                <el-switch v-model="form.includeOnhandStock" :active-value="1" :inactive-value="0" :disabled="!isDraft" />
              </el-form-item>
            </div>
          </div>

          <!-- 批量规则 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>批量规则
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('批量规则')" prop="lotSizeType" required v-if="isFieldVisible('lotSizeType')">
                <el-select v-model="form.lotSizeType" :placeholder="$tt('选择批量规则')" class="field-input" :disabled="!isDraft">
                  <el-option :label="$tf('逐批法')" value="逐批法" />
                  <el-option :label="$tf('固定批量')" value="固定批量" />
                  <el-option :label="$tf('经济批量')" value="经济批量" />
                  <el-option :label="$tf('期间批量')" value="期间批量" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('固定批量')" v-if="isFieldVisible('lotSize')" :required="isFieldRequired('lotSize')">
                <el-input-number v-model="form.lotSize" :min="0" :precision="scaleFor('qty')" controls-position="right" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('最小订货量')" v-if="isFieldVisible('minOrderQty')" :required="isFieldRequired('minOrderQty')">
                <el-input-number v-model="form.minOrderQty" :min="0" :precision="scaleFor('qty')" controls-position="right" class="field-input" :disabled="!isDraft" />
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
          type="mrp-plan"
          :disabled="!isDraft"
          button-text="上传附件"
          tip-text="支持图片、PDF、Word、Excel 等文件"
        />
      </div>
    </div>

    <!-- 计划明细：统一到通用明细组件 BillItemsTable（多选 + 无删行 + 无汇总；保留原自定义卡片头按钮与空态） -->
    <BillItemsTable
      v-model:collapsed="collapsed.items"
      :items="form.items"
      page-key="MrpPlanEdit_items"
      title="计划明细"
      selectable
      :show-index="false"
      :operation-column="false"
      :show-summary="false"
      :columns="mrpItemColumns"
      @selection-change="onItemSelectionChange"
    >
      <template #actions>
        <el-button v-permission="perm('generatePurchase')" size="small" type="success" :disabled="!canGenerate" @click="handleGenerate('purchase')">{{ $tt("生成采购订单") }}</el-button>
        <el-button v-permission="perm('generateWorkOrder')" size="small" type="warning" :disabled="!canGenerate" @click="handleGenerate('produce')">{{ $tt("生成生产订单") }}</el-button>
        <el-button v-permission="perm('generateSubcontract')" size="small" type="info" :disabled="!canGenerate" @click="handleGenerate('subcontract')">{{ $tt("生成委外订单") }}</el-button>
      </template>
      <template #cell-suggestType="{ row }">
        <el-tag size="small" :type="suggestTagType(row.suggestType)" effect="light">{{ suggestTypeText(row.suggestType) }}</el-tag>
      </template>
      <template #empty>
        <el-empty :description="$tt('暂无计划明细')" :image-size="80" />
      </template>
    </BillItemsTable>

    <!-- 审核流转（真实审批进度，通用组件） -->
    <ApprovalFlowTimeline bill-type="MRP_PLAN" :bill-id="form.id" :status="form.status" />

    <!-- 打印预览弹窗 -->
    <PrintPreviewDialog v-model="printVisible" bill-type="MRP_PLAN" :bill-id="form.id" />

    <!-- 操作日志弹窗 -->
    <ChangeRecordDialog v-model="changeRecordVisible" bill-type="MRP_PLAN" :bill-id="form.id" />

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
        <BillLinkPanel v-if="topTab === 'source'" bill-type="MRP_PLAN" :bill-id="form.id" mode="source" />
      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('去向单据')" name="target" :disabled="!form.id">
        <BillLinkPanel v-if="topTab === 'target'" bill-type="MRP_PLAN" :bill-id="form.id" mode="target" />
      </el-tab-pane>
    </el-tabs>
  </div>
  </template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getMrpPlan, saveMrpPlan, updateMrpPlan,
  deleteMrpPlan, approveMrpPlan, executeMrpPlan,
  rejectMrpPlan, reverseApproveMrpPlan,
  closeOrderMrpPlan, reopenMrpPlan,
  voidMrpPlan, unvoidMrpPlan,
  generatePurchase, generateProduce, generateSubcontract
} from '@/api/production/mrpPlan'
import { useBillActions } from '@/composables/useBillActions'
import { useCopyBill } from '@/composables/useCopyBill'
import FileUploader from '@/components/FileUploader.vue'
import PrintPreviewDialog from '@/components/PrintPreviewDialog.vue'
import ApprovalFlowTimeline from '@/components/bill/ApprovalFlowTimeline.vue'
import BillItemsTable from '@/components/bill/BillItemsTable.vue'
import BillLinkPanel from '@/components/bill/BillLinkPanel.vue'
import { isBillLinkRegistered } from '@/api/billLink'
import ChangeRecordDialog from '@/components/ChangeRecordDialog.vue'
import PushDownButton from '@/components/PushDownButton.vue'
import { canPushDown } from '@/utils/bill-status'
import { confirmSubmit, submitSuccessMessage } from '@/utils/submit-confirm'
import {
  ArrowLeft, ArrowRight, ArrowDown, Document, Collection, Check,
  CircleCheck, RefreshLeft, Delete, Printer, Clock, VideoPlay, Memo
} from '@element-plus/icons-vue'
import { useAutoDocCode } from '@/composables/useAutoDocCode'

import * as BillStatus from '@/utils/bill-status'
import BillActions from '@/components/bill/BillActions.vue'
import { triggerListRefresh } from '@/composables/useAutoRefresh'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'
import { useFieldPermission } from '@/composables/useFieldPermission'
import { usePrecision } from '@/composables/usePrecision'

// 数量输入/显示的位数统一读设置中心「数量小数位数」（2026-11-10）
const { scaleFor } = usePrecision()

const perm = usePagePerm('production:mrp')
const { isFieldVisible, isFieldRequired } = useFieldPermission('mrp_plan')

const route = useRoute()
const router = useRouter()

// 是否编辑模式
const isEdit = computed(() => !!route.params.id)
// 草稿状态才能编辑
// 单据编码自动生成
const { docCodeDisabled, docCodePlaceholder, autoFillDocCodeOnCreate } = useAutoDocCode('MRP_PLAN')

const saving = ref(false)
const headerFormRef = ref(null)
const printVisible = ref(false)
const changeRecordVisible = ref(false)

// 折叠卡片状态
const collapsed = reactive({
  base: true,
  items: true,
  attach: true,
  audit: true
})

function toggleCard(key) {
  collapsed[key] = !collapsed[key]
}

// 表单数据
const form = reactive({
  id: null,
  planNo: '',
  planName: '',
  planDate: new Date().toISOString().slice(0, 10),
  planHorizon: 90,
  demandSource: '销售订单',
  includeSo: 1,
  includeForecast: 0,
  includeSafetyStock: 1,
  includeOpenPo: 1,
  includeOpenMo: 1,
  includeOnhandStock: 1,
  timeFenceDays: 7,
  lotSizeType: '逐批法',
  lotSize: 0,
  minOrderQty: 0,
  status: '草稿',
  remark: '',
  attachmentUrls: '',
  items: [],
  createdAt: '',
  approvedAt: '',
  executedAt: ''
})

const isDraft = computed(() => BillStatus.canEdit(form.status, form.closeStatus))
// 表单校验规则
const rules = {
  planName: [{ required: true, message: tt('请输入方案名称'), trigger: 'blur' }],
  planDate: [{ required: true, message: tt('请选择运算日期'), trigger: 'change' }],
  planHorizon: [{ required: true, message: tt('请输入计划展望期'), trigger: 'blur' }],
  demandSource: [{ required: true, message: tt('请选择需求来源'), trigger: 'change' }],
  lotSizeType: [{ required: true, message: tt('请选择批量规则'), trigger: 'change' }]
}

// 状态标签类型
function statusTagType(s) {
  return { '草稿': 'info', '已生效': 'success', '已执行': 'success' }[s] || 'info'
}

// ===== 计划明细：建议类型映射（兼容英文枚举与历史中文值） =====
const SUGGEST_TYPE_MAP = {
  PRODUCE: { text: '生产', tag: 'warning' },
  '自制件': { text: '生产', tag: 'warning' },
  PURCHASE: { text: '采购', tag: 'success' },
  '外购件': { text: '采购', tag: 'success' },
  SUBCONTRACT: { text: '委外', tag: 'info' },
  '委外件': { text: '委外', tag: 'info' }
}
function suggestTypeText(t) {
  return SUGGEST_TYPE_MAP[t]?.text || t || ''
}
function suggestTagType(t) {
  return SUGGEST_TYPE_MAP[t]?.tag || 'info'
}

// 计划明细列定义（迁入通用明细组件 BillItemsTable 后由组件渲染；列设置 pageKey = MrpPlanEdit_items）
const mrpItemColumns = [
  { prop: 'materialCode', label: '物料编码', minWidth: 120 },
  { prop: 'materialName', label: '物料名称', minWidth: 140 },
  { prop: 'specification', label: '规格', minWidth: 120 },
  { prop: 'grossDemand', label: '毛需求', width: 100, align: 'right' },
  { prop: 'availableStock', label: '可用库存', width: 100, align: 'right' },
  { prop: 'safetyStock', label: '安全库存', width: 100, align: 'right' },
  { prop: 'netDemand', label: '净需求', width: 100, align: 'right' },
  { prop: 'planQuantity', label: '计划数量', width: 100, align: 'right' },
  { prop: 'suggestType', label: '建议类型', width: 100, align: 'center' },
  { prop: 'suggestDate', label: '建议日期', width: 120 }
]

// 计划明细选中行
const selectedItems = ref([])
function onItemSelectionChange(rows) {
  selectedItems.value = rows || []
}
// 仅"已生效"(planStatus=2)且已保存的计划可生成下游单据，与后端校验口径一致
const canGenerate = computed(() => !!form.id && form.status === '已生效')

// 生成下游单据（采购/生产/委外）
const generateApiMap = {
  purchase: generatePurchase,
  produce: generateProduce,
  subcontract: generateSubcontract
}
async function handleGenerate(kind) {
  const itemIds = selectedItems.value.map(r => r.id)
  if (itemIds.length === 0) {
    try {
      await ElMessageBox.confirm(tt('未选择明细，将按建议类型生成全部'), tt('提示'), { type: 'warning' })
    } catch (e) {
      return
    }
  }
  try {
    await generateApiMap[kind](form.id, itemIds)
    ElMessage.success(tt('生成成功'))
    await loadDetail()
  } catch (e) {
    ElMessage.error(e.message || tt('生成失败'))
  }
}

// 保存
async function onSave(silent, opts = {}) {
  // 宽松草稿保存：不校验必填，允许核心字段为空（可后续补全再提交）
  saving.value = true
  try {
    if (isEdit.value) {
      await updateMrpPlan(form.id, form)
    } else {
      const res = await saveMrpPlan(form)
      if (res && (res.id || res.planId)) {
        const newId = res.id || res.planId
        form.id = newId
        if (!opts.skipRouteSync) router.replace(`/production/mrp-plans/edit/${newId}`)
      } else {
        if (!opts.skipRouteSync) router.replace('/production/mrp-plan')
      }
    }
    triggerListRefresh('/production/mrp-plans')
    if (!silent) ElMessage.success(tt('保存成功'))
    return true
  } catch (e) {
    ElMessage.error(e.message || tt('保存失败'))
    return false
  } finally {
    saving.value = false
  }
}

async function onSubmit() {
  // 无论新建还是已有草稿，提交前一律先静默保存（把当前页面所见落库，避免提交旧数据）
  const saved = await onSave(true, { skipRouteSync: true })
  if (!saved || !form.id) return
  try {
    const needApproval = await confirmSubmit({ billType: '', billLabel: 'MRP计划', billNo: form.planNo })
    // MRP无独立提交接口：提交即审核生效（与后端草稿可直审设计一致）
    await approveMrpPlan(form.id)
    ElMessage.success(submitSuccessMessage(needApproval))
    triggerListRefresh('/production/mrp-plans')
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('提交失败'))
  } finally {
    // 提交（或用户取消）后再把新单 id 写回地址栏：此时重建实例无副作用，新实例挂载时会重新拉取最新状态
    if (!route.params.id && form.id) {
      try { await router.replace(`/production/mrp-plans/edit/${form.id}`) } catch (_) {}
    }
  }
}

// 重置草稿
async function onReverseApprove() {
  await ElMessageBox.confirm(tt('确认重置草稿？单据将恢复为草稿状态'), tt('确认'), { type: 'warning' })
  try {
    // 复用 update 接口，状态置为草稿
    await updateMrpPlan(form.id, { ...form, status: '草稿' })
    ElMessage.success(tt('已重置草稿'))
    await loadDetail()
  } catch (e) { /* 拦截器已提示 */ }
}

// 执行方案
async function onExecute() {
  await ElMessageBox.confirm(tt('确认执行MRP方案？执行后将生成计划采购订单和计划生产工单'), tt('确认'), { type: 'warning' })
  try {
    await executeMrpPlan(form.id)
    ElMessage.success(tt('执行成功'))
    await loadDetail()
  } catch (e) { /* 拦截器已提示 */ }
}

// 重新计算（2026-10-06）：不做“一键原地重算”，而是回到 MRP 运算向导，
// 带出原方案的需求来源与运算参数，由用户重新选物料 → 重新配置 → 重新执行运算。
const canRecalc = computed(() => !!form.id
  && ['草稿', '待审核', '已生效'].includes(form.status)
  && Number(form.closeStatus) !== 1)

async function onRecalc() {
  await ElMessageBox.confirm(
    tt('确认重新计算？将回到「MRP 运算」向导，带出本方案的需求来源与运算参数，从选择物料开始重新运算一遍。'),
    tt('确认'), { type: 'warning' })
  router.push(`/production/mrp/new?from=${form.id}`)
}

// 删除
async function onDelete() {
  await ElMessageBox.confirm(tt('确认删除该方案？删除后不可恢复'), tt('确认'), { type: 'warning' })
  try {
    await deleteMrpPlan(form.id)
    ElMessage.success(tt('删除成功'))
    router.replace('/production/mrp-plan')
  } catch (e) { /* 拦截器已提示 */ }
}

// 更多操作


// 加载详情
async function loadDetail() {
  if (!isEdit.value) return
  try {
    const data = await getMrpPlan(route.params.id)
    Object.assign(form, {
      id: data.id,
      planNo: data.planNo || '',
      planName: data.planName || '',
      planDate: data.planDate || new Date().toISOString().slice(0, 10),
      planHorizon: data.planHorizon ?? 90,
      demandSource: data.demandSource || '销售订单',
      includeSo: data.includeSo ?? 1,
      includeForecast: data.includeForecast ?? 0,
      includeSafetyStock: data.includeSafetyStock ?? 1,
      includeOpenPo: data.includeOpenPo ?? 1,
      includeOpenMo: data.includeOpenMo ?? 1,
      includeOnhandStock: data.includeOnhandStock ?? 1,
      timeFenceDays: data.timeFenceDays ?? 7,
      lotSizeType: data.lotSizeType || '逐批法',
      lotSize: data.lotSize ?? 0,
      minOrderQty: data.minOrderQty ?? 0,
      status: data.status || '草稿',
      closeStatus: data.closeStatus ?? 0,
      remark: data.remark || '',
      attachmentUrls: data.attachmentUrls || '',
      items: data.items || [],
      createdAt: data.createdAt || data.createTime || '',
      approvedAt: data.approvedAt || data.approveTime || '',
      executedAt: data.executedAt || data.executeTime || ''
    })
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
  getId: () => form.id,
  api: {
    approve: approveMrpPlan,
    reject: rejectMrpPlan,
    reverseApprove: reverseApproveMrpPlan,
    close: closeOrderMrpPlan,
    reopen: reopenMrpPlan,
    void: voidMrpPlan,
    unvoid: unvoidMrpPlan,
    delete: deleteMrpPlan
  },
  reload: loadDetail,
  router,
  listPath: '/production/mrp',
  newPath: '/production/mrp-plans/edit',
  billLabel: 'MRP方案'
})

// ===== 复制功能 =====
const { applyCopy } = useCopyBill({
  route,
  loadFn: getMrpPlan,
  form,
  noField: 'planNo'
})

function handlePrint() {
  printVisible.value = true
}

// ===== 顶级页签：单据 / 来源单据 / 去向单据 =====
const topTab = ref('doc')
const billLinkRegistered = ref(false)

onMounted(async () => {
  // 该单据类型是否登记了「来源单据/去向单据」配置（未登记则不显示这两个页签）
  isBillLinkRegistered('MRP_PLAN')
    .then(v => { billLinkRegistered.value = !!v })
    .catch(() => { billLinkRegistered.value = false })

  if (!isEdit.value) {
    autoFillDocCodeOnCreate(form, 'planNo')
  }
  const copied = await applyCopy()
  if (!copied) {
    await loadDetail()
  }
})

  </script>

<style scoped lang="scss">
.mrp-plan-edit {
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

  &:hover { border-color: #d1d5db; }
  &.collapsed .card-body { padding: 0; }

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
      &.active { transform: rotate(90deg); }
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

    .header-right {
      display: flex;
      align-items: center;
      gap: 8px;
    }
  }

  .card-body {
    padding: 18px 20px;
    transition: padding .2s;
    &.no-padding { padding: 0; }
  }
}

.table-wrap {
  padding: 12px 16px;

  .compact-table {
    :deep(.el-table__header th) {
      background: rgba(99, 102, 241, 0.04) !important;
      color: #1f2937;
      font-weight: 600;
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

      :deep(.el-form-item) { margin-bottom: 14px; }
      .span-all { grid-column: 1 / -1; }
      .field-input { width: 100%; }
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
  .mrp-plan-edit { padding: 12px; }
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
