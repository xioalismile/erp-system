<template>
  <!-- 通用列表壳（页头/统计卡/搜索条/表格外壳/分页/打印/列设置）+ 声明式筛选维度 -->
  <BillListPage
    page-class="purchase-order-page"
    back-path="/purchase"
    :title="$tt('采购订单')"
    badge="PURCHASE ORDER"
    sub-title="智能采购管理 · 高效供应链协同"
    create-path="/purchase/orders/new"
    :stats="stats"
    :active-stat-key="activeStatKey"
    search-placeholder="输入订单号/供应商名称搜索"
    keyword-label="单据编号"
    :filters="['status', 'date', 'supplier', 'employee']"
    :status-options="statusOptions"
    :filter-model="search"
    :filter-defs="filterDefs"
    :detail-filter-defs="detailFilterDefs"
    employee-label="采购员"
    :table-data="orders"
    :loading="loading"
    :pagination="page"
    storage-key="PurchaseOrderList"
    :columns="columns"
    :table-key="configVersion"
    bill-type="PURCHASE_ORDER"
    field-perm-key="purchase_order"
    v-model:keyword="search.keyword"
    v-model:selected-rows="selectedRows"
    :batch-actions="batchActions"
    v-model:column-setting-visible="columnSettingVisible"
    v-model:print-visible="printVisible"
    v-model:print-bill-id="printBillId"
    @search="handleSearch"
    @reset="handleReset"
    @filter-change="onFilterChange"
    @filter-params="onHeaderFilterParams"
    @stat-click="onStatClick"
    @sort-change="onSortChange"
    @page-change="loadData"
  >
    <template #header-actions="{ selectedRows: sel }">
      <!-- 页头按钮统一走通用组件（2026-09-19）：按钮码由 perm-prefix 派生，权限控制一处生效 -->
      <BillHeaderActions
        perm-prefix="purchase:orders"
        :add-text="$tt('新增采购订单')"
        :print-disabled="!sel.length"
        @add="$router.push('/purchase/orders/new')"
        @print="handleBatchPrint(sel)"
        @export="handleExport"
        @import="handleImport"
      />
    </template>

    <el-table-column type="selection" width="50" />
    <el-table-column type="index" label="#" width="60" />
    <!-- 动态列渲染：列顺序由列设置拖拽配置驱动（sortedColumns），单元格按 col.type 分支渲染 -->
    <template v-for="col in visibleSortedColumns" :key="col.prop">
      <el-table-column
        :prop="col.prop"
        :label="$tf(col.label)"
        :width="col.width"
        :min-width="col.minWidth"
        :align="col.align || 'left'"
        :sortable="col.sortable ? 'custom' : false"
        :show-overflow-tooltip="!!col.tooltip"
        :fixed="getColFixed(col.prop)"
      >
        <template v-if="col.type" #default="{ row }">
          <div v-if="col.type === 'link'" class="order-no-cell">
            <span class="order-no-text" @click="$router.push(`/purchase/orders/${row.orderId}`)">{{ row.orderNo }}</span>
            <el-icon class="link-icon"><Link /></el-icon>
          </div>
          <div v-else-if="col.type === 'supplier'" class="customer-cell">
            <div class="customer-avatar">{{ (row.supplierName || '?').charAt(0) }}</div>
            <div class="customer-info">
              <div class="customer-name">{{ row.supplierName }}</div>
            </div>
          </div>
          <span v-else-if="col.type === 'amount2'" class="net-amount">¥{{ Number(row[col.prop] || 0).toFixed(2) }}</span>
          <div v-else-if="col.type === 'status'" class="status-cell">
            <span :class="['status-dot', statusDotClass(row.status)]"></span>
            <el-tag size="small" :type="statusType(row.status)" effect="light" class="status-tag">{{ $dt(row.status) }}</el-tag>
          </div>
          <el-tag v-else-if="col.type === 'inboundStatus'" size="small" :type="inboundStatusTagType(row[col.prop])" effect="plain">{{ $dt(row[col.prop]) }}</el-tag>
          <template v-else-if="col.type === 'sourceType'">
            <!-- 来源取值以后端真实写入的 source_bill_type 为准（采购申请单/MRP计划/销售订单），空=手工 -->
            <el-tag v-if="row.sourceBillType === '采购申请单'" size="small" type="primary">{{ $tt("采购申请") }}</el-tag>
            <el-tag v-else-if="row.sourceBillType === 'MRP计划'" size="small" type="danger">{{ $tt("MRP计划") }}</el-tag>
            <el-tag v-else-if="row.sourceBillType === '销售订单'" size="small" type="warning">{{ $tt("销售订单") }}</el-tag>
            <el-tag v-else-if="row.sourceBillType" size="small" type="info">{{ $dt(row.sourceBillType) }}</el-tag>
            <el-tag v-else size="small" type="info">{{ $tt("手动") }}</el-tag>
          </template>
        </template>
      </el-table-column>
    </template>
    <AuditColumns />
  </BillListPage>

    <el-dialog v-model="inbound.visible" :title="$tf('采购入库')" width="400px">
      <el-form label-width="80px">
        <el-form-item :label="$tf('入库仓库')"><el-input v-model="inbound.warehouse" :placeholder="$tt('主仓库')" /></el-form-item>
      </el-form>
      <el-alert type="success" :closable="false" style="margin:10px 0">{{ $tt("入库将自动增加库存") }}</el-alert>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="inbound.visible=false">{{ $tt("取消") }}</el-button>
        <el-button v-permission="perm('confirm')" type="primary" @click="doInbound">{{ $tt("确认入库") }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="importDialogVisible"
      :title="$tf('采购订单导入')"
      width="560px"
      :close-on-click-modal="false"
      class="tech-dialog"
    >
      <div class="import-dialog-content">
        <div class="import-step">
          <div class="step-title">
            <el-icon><Download /></el-icon>
            <span>{{ $tt("第一步：下载导入模板") }}</span>
          </div>
          <div class="step-desc">{{ $tt("下载模板后，按模板格式填写数据，支持 .xlsx 格式") }}</div>
          <div class="step-desc" style="color:#f56c6c;">{{ $tt("提示：模板中带") }} <b>*</b> {{ $tt("标记的字段为必填字段，请务必填写") }}</div>
          <el-button v-permission="perm('downloadTemplate')" type="primary" @click="downloadTemplate" :loading="downloadingTemplate">
            <el-icon><Download /></el-icon> 下载精简模板（含自定义字段）
          </el-button>
          <el-collapse class="template-field-panel" v-model="fieldPanelActive" style="margin-top:12px;">
            <el-collapse-item name="custom">
              <template #title><span style="font-weight:500;">{{ $tt("自定义模板字段（可勾选额外列）") }}</span></template>
              <div class="field-custom-container">
                <div class="field-custom-actions" style="margin-bottom:8px;">
                  <el-button v-permission="perm('columnSetting')" size="small" @click="selectAllOptional">{{ $tt("全选可选列") }}</el-button>
                  <el-button v-permission="perm('clear')" size="small" @click="clearOptional">{{ $tt("清空可选列") }}</el-button>
                </div>
                <div class="field-group-title" style="margin-bottom:6px;">{{ $tt("必带列（锁定，含自定义字段）") }}</div>
                <div class="field-checkbox-group">
                  <el-tag v-for="f in templateFields.required" :key="f" size="small" effect="plain" style="margin:4px 8px 4px 0;">{{ f }}</el-tag>
                </div>
                <div class="field-group-title" style="margin:12px 0 6px;">{{ $tt("可选列（按需勾选）") }}</div>
                <div class="field-checkbox-group">
                  <el-checkbox v-for="f in templateFields.optional" :key="f" v-model="checkedOptionalFields" :label="f" :border="true" size="small" style="margin:6px 12px 6px 0;">{{ f }}</el-checkbox>
                </div>
                <div style="margin-top:12px;text-align:right;">
                  <el-button v-permission="perm('downloadTemplate')" type="success" size="small" @click="downloadCustomTemplate" :loading="downloadingCustomTemplate">
                    <el-icon><Download /></el-icon> 按勾选下载
                  </el-button>
                </div>
              </div>
            </el-collapse-item>
          </el-collapse>
        </div>
        <el-divider />
        <div class="import-step">
          <div class="step-title">
            <el-icon><Upload /></el-icon>
            <span>{{ $tt("第二步：上传数据文件") }}</span>
          </div>
          <div class="step-desc">{{ $tt("选择填写好的 Excel 文件上传导入") }}</div>
          <el-upload
            ref="uploadRef"
            action="#"
            :auto-upload="false"
            :on-change="handleFileChange"
            :show-file-list="true"
            accept=".xlsx,.xls,.csv"
            drag
            class="import-uploader"
          >
            <el-icon class="el-icon--upload"><Upload /></el-icon>
            <div class="el-upload__text">{{ $tt("将文件拖到此处，或") }}<em>{{ $tt("点击上传") }}</em></div>
            <template #tip>
              <div class="el-upload__tip">{{ $tt("请上传 .xlsx / .xls 格式文件，文件大小不超过 10MB") }}</div>
            </template>
          </el-upload>
        </div>
        <div v-if="importResult" class="import-result">
          <el-divider />
          <div class="result-title">{{ $tt("导入结果") }}</div>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-statistic :title="$tt('总记录数')" :value="importResult.totalCount || 0" />
            </el-col>
            <el-col :span="8">
              <el-statistic :title="$tt('成功')" :value="importResult.successCount || 0">
                <template #suffix><el-icon color="#67C23A"><CircleCheck /></el-icon></template>
              </el-statistic>
            </el-col>
            <el-col :span="8">
              <el-statistic :title="$tt('失败')" :value="importResult.failCount || 0">
                <template #suffix><el-icon color="#F56C6C"><CircleClose /></el-icon></template>
              </el-statistic>
            </el-col>
          </el-row>
          <el-alert
            v-if="importResult.failCount > 0"
            :title="`有 ${importResult.failCount} 条记录导入失败`"
            type="error"
            :closable="false"
            class="import-error-alert"
          >
            <div class="error-list">
              <div v-for="(err, idx) in (importResult.errors || []).slice(0, 10)" :key="idx" class="error-item">
                第 {{ err.rowIndex }} 行 {{ err.fieldName ? '[' + err.fieldName + ']' : '' }}: {{ err.message }}
              </div>
              <div v-if="(importResult.errors || []).length > 10" class="error-more">
                ... 还有 {{ importResult.errors.length - 10 }} 条错误
              </div>
            </div>
          </el-alert>
        </div>
      </div>
      <template #footer>
        <el-button v-permission="perm('dlgClose')" @click="importDialogVisible = false">{{ $tt("关闭") }}</el-button>
        <el-button v-permission="perm('import')" type="primary" @click="confirmImport" :loading="importing" :disabled="!selectedImportFile">
          <el-icon><Upload /></el-icon> 开始导入
        </el-button>
      </template>
    </el-dialog>

    <!-- 打印预览/列设置抽屉已由 BillListPage 壳统一渲染（bill-type / storage-key 传入） -->
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Plus, Search, Download, Upload, CircleCheck, CircleClose, Printer, Edit, Link, Setting } from '@element-plus/icons-vue'
import api from '../../api'
import { getPurchaseOrderDashboard, submitPurchaseOrder, approvePurchaseOrder, deletePurchaseOrder, reverseApprovePurchaseOrder } from '@/api/purchase/purchaseOrder'
import BillListPage from '@/components/bill/BillListPage.vue'
// 页头按钮通用组件（2026-09-19）：新增/打印/导出/导入统一渲染 + 统一按钮权限
import BillHeaderActions from '@/components/bill/BillHeaderActions.vue'
import PrintPreviewDialog from '@/components/PrintPreviewDialog.vue'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
import AuditColumns from '@/components/AuditColumns.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { useCustomFieldSearch } from '@/composables/useCustomFieldSearch'
// 筛选条件自定义（2026-10-14 全面推广）：条件定义共享工厂 + 明细报表 API（取明细模式专属条件元数据）
import { stdDefs, fld, cfDefs, detailFilterDefsOf } from '@/config/billFilterDefs'
import { createBillDetailReportApi } from '@/api/report/billDetailReport'
import { exportListAsSeen, downloadTemplate as downloadTemplateUtil, importData } from '@/utils/exportUtil'

import { useTableSort } from '@/composables/useTableSort'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('purchase:orders')
const { handleSortChange, getSortParams } = useTableSort()


const onSortChange = (e) => { handleSortChange(e); loadData() }

/**
 * 批量操作声明（2026-09-20）：批量操作条 UI / 确认 / 执行 / 结果面板由通用壳（BillListPage）承担，
 * 这里只声明"怎么执行"。命名口径全系统统一：批量提交 / 批量审批 / 批量重置草稿 / 批量删除 / 批量导出。
 */
const batchActions = [
  {
    key: 'submit',
    label: tt('批量提交'),
    type: 'primary',
    perm: 'purchase:orders:submit',
    exec: (row) => submitPurchaseOrder(row.orderId),
    // 已提交/已审核的自动跳过（不报错，进"跳过清单"说明原因）
    skipReason: (row) => (row.status && row.status !== '草稿'
      ? tt('当前状态：') + row.status + tt('，不是草稿，无需提交') : '')
  },
  {
    key: 'approve',
    label: tt('批量审批'),
    perm: 'purchase:orders:audit',
    exec: (row) => approvePurchaseOrder(row.orderId),
    skipReason: (row) => (row.status !== '待审核'
      ? tt('当前状态：') + (row.status || '-') + tt('，不是待审核，无需审批') : '')
  },
  {
    // 批量重置草稿：与编辑页「重置草稿」同一个后端入口（/reverse-approve），不在前端自造规则，
    // 能不能重置由后端判断，被拒的那条原话进"失败清单"
    key: 'reverse',
    label: tt('批量重置草稿'),
    perm: 'purchase:orders:reverseApprove',
    exec: (row) => reverseApprovePurchaseOrder(row.orderId)
  },
  {
    key: 'delete',
    label: tt('批量删除'),
    type: 'danger',
    perm: 'purchase:orders:delete',
    exec: (row) => deletePurchaseOrder(row.orderId),
    // 状态冲突：选中行含非草稿 → 按钮置灰并悬浮说明原因
    disabledReason: (rows) => (rows.some(r => r.status && r.status !== '草稿' && r.status !== '已驳回' && r.status !== '已取消')
      ? tt('非草稿不能删除，请先重置草稿') : '')
  },
  {
    key: 'export',
    label: tt('批量导出'),
    perm: 'purchase:orders:export',
    entity: 'purchaseOrder',
    idOf: (row) => row.orderId
  }
]
// 列定义（用于列设置抽屉+动态渲染）：表格全部数据列，type 标记单元格渲染分支
// link 单号链接、supplier 供应商头像、amount2 ¥金额2位、status 状态标签、
// inboundStatus 入库状态、sourceType 来源；纯文本列不加 type
const columns = [
  { prop: 'orderNo', label: '采购单号', minWidth: 180, sortable: true, type: 'link' },
  { prop: 'supplierName', label: '供应商', minWidth: 180, sortable: true, type: 'supplier' },
  { prop: 'orderDate', label: '日期', width: 120, sortable: true },
  { prop: 'netAmount', label: '价税合计', width: 130, align: 'right', sortable: true, type: 'amount2' },
  { prop: '_status', label: '状态', width: 100, type: 'status' },
  // 入库列为计算字段（非库列，排序无意义）→ 关闭列头排序，避免"点了没反应"
  { prop: 'inboundStatus', label: '入库', width: 100, type: 'inboundStatus' },
  // 来源列绑定实体真实字段 sourceBillType（真实库列 source_bill_type，已在后端排序白名单内，可排序）
  { prop: 'sourceBillType', label: '来源', width: 100, sortable: true, type: 'sourceType' },
  { prop: 'remark', label: '备注', minWidth: 120, tooltip: true, sortable: true }
]
const { columnSettingVisible, openColumnSetting, isColVisible, getColFixed, configVersion, sortedColumns } = useColumnSetting('PurchaseOrderList', columns, 'purchase_order')
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))

const loading = ref(false)
const orders = ref([])
// 统一入参名（docs/knowledge-base.md 6.1）：keyword / status / startDate / endDate / supplierId / employeeId
// （原实现发的是 supplierName，后端只认 keyword → 搜索框填了没反应；本次修正为 keyword）
const search = reactive({ keyword: '', status: '', startDate: '', endDate: '', supplierId: '', employeeId: '' })
// 自定义字段「可搜索」搜索项：设置中心勾了「可搜索」的字段才渲染，未勾选时搜索栏与改造前一致
const cf = useCustomFieldSearch('purchase_order')
const page = ref({ current: 1, size: 20, total: 0 })
// 通用搜索栏「单据状态」下拉选项：取值沿用单据状态机（草稿/待审核/已生效/已关闭/已取消）。
// 原状态页签里的「已确认/已入库/已完成」在本单据状态机中不存在（点了永远 0 条）
const statusOptions = [
  { label: '草稿', value: '草稿' },
  { label: '待审核', value: '待审核' },
  { label: '已生效', value: '已生效' },
  { label: '已关闭', value: '已关闭' },
  { label: '已取消', value: '已取消' }
]

// ============ 筛选条件自定义（2026-10-14 全面推广 · 销售订单试点标准） ============
// 条件候选 = 标准维度（状态/订单日期/供应商/采购员，std 标记 → 值走页面 search 通道，与统计卡互通）
//          + 表头字段（f_<key> 参数化，后端白名单翻译，键与明细报表引擎同键）
//          + 单据自定义字段（设置中心勾「可搜索」，沿用 customSearch 既有通道）。
// 键与「采购订单明细报表」引擎筛选键同键 → 汇总/明细两种模式一套条件。
const filterDefs = computed(() => [
  ...stdDefs({
    status: { options: statusOptions },
    date: { key: 'orderDate', label: '订单日期' },
    supplier: { key: 'supplierName', label: '供应商' },
    employee: { key: 'buyerName', label: '采购员' }
  }),
  fld('text', 'orderNo', '采购单号'),
  fld('select', 'deptName', '采购部门', 'department'),
  fld('select', 'currencyCode', '币种', 'currency'),
  fld('select', 'settleModeName', '结算方式', 'settleMode'),
  fld('dateRange', 'deliveryDate', '交货日期'),
  fld('text', 'contactPerson', '联系人'),
  fld('text', 'contactPhone', '联系电话'),
  fld('text', 'deliveryPlace', '交货地点'),
  fld('text', 'requestNo', '申请单号'),
  fld('text', 'sourceBillType', '来源类型'),
  fld('text', 'sourceBillNo', '来源单号'),
  fld('text', 'accountName', '预付款账户'),
  fld('text', 'remark', '备注'),
  fld('numberRange', 'totalAmount', '订单金额'),
  fld('numberRange', 'discountAmount', '折扣金额'),
  fld('numberRange', 'taxAmount', '税额'),
  fld('numberRange', 'netAmount', '价税合计'),
  fld('numberRange', 'prepaymentAmount', '预付金额'),
  fld('numberRange', 'taxRate', '税率%'),
  fld('numberRange', 'exchangeRate', '汇率'),
  ...cfDefs(cf)
])

// 明细模式专属条件 = 采购订单明细报表 /columns 的 filters 元数据（键=引擎同键）；
// STD_BY_KEY 声明引擎筛选键 → 标准维度（默认勾选=现状、值走页面 search 通道）
const detailFiltersMeta = ref([])
const STD_BY_KEY = { status: 'status', orderDate: 'date', supplierName: 'supplier', buyerName: 'employee' }
const detailFilterDefs = computed(() => detailFilterDefsOf(detailFiltersMeta.value, STD_BY_KEY))
// 明细报表 API（与列表页明细模式双视图同一基础路径）
const purchaseOrderDetailApi = createBillDetailReportApi('/report/purchase/order-detail')

/** 自定义条件参数（由通用壳经 filter-params 下发）：params 汇总用 / detailParams 明细用 / customSearch 自定义字段 */
const headerFilterState = ref({ params: {}, customSearch: undefined, detailParams: {} })
function onHeaderFilterParams(payload) {
  headerFilterState.value = payload || { params: {}, customSearch: undefined, detailParams: {} }
}

// 明细条件参数由通用壳统一收集并合并进明细取数（lastDetailParams），页面零代码

const inbound = ref({ visible: false, id: null, warehouse: '主仓库' })
const selectedRows = ref([])
// 统计卡片高亮（点卡片筛选列表时由 onStatClick 控制）
const activeStatKey = ref('')

const printVisible = ref(false)
const printBillId = ref(null)

const importDialogVisible = ref(false)
const downloadingTemplate = ref(false)
const downloadingCustomTemplate = ref(false)
const importing = ref(false)
const selectedImportFile = ref(null)
const importResult = ref(null)
const uploadRef = ref()
const fieldPanelActive = ref('')
const templateFields = reactive({ required: [], optional: [] })
const checkedOptionalFields = ref([])

async function loadTemplateFields() {
  try {
    const resp = await fetch(`/api/export/template/purchaseOrder/fields`, { headers: { Authorization: `Bearer ${localStorage.getItem('token') || ''}` } })
    const json = await resp.json()
    if (json.code === 200 && json.data) {
      templateFields.required = json.data.required || []
      templateFields.optional = json.data.optional || []
      checkedOptionalFields.value = []
    }
  } catch (e) { /* 静默失败 */ }
}

function selectAllOptional() { checkedOptionalFields.value = [...templateFields.optional] }
function clearOptional() { checkedOptionalFields.value = [] }

async function downloadCustomTemplate() {
  downloadingCustomTemplate.value = true
  try {
    await downloadTemplateUtil('purchaseOrder', checkedOptionalFields.value)
    ElMessage.success(tt('自定义模板下载成功'))
  } catch (e) {
    ElMessage.error(tt('自定义模板下载失败'))
  } finally {
    downloadingCustomTemplate.value = false
  }
}

// 顶部统计卡片：数据来自后端 /api/purchase/dashboard（与列表同数据权限口径；原来是硬编码 0 → 卡片永远是 0）
// 带 filter 的卡片可点击 → 直接筛选下方列表（再点同一张卡 = 取消）
const pad2 = (n) => String(n).padStart(2, '0')
function monthRange() {
  const d = new Date()
  const y = d.getFullYear()
  const m = d.getMonth() + 1
  const lastDay = new Date(y, m, 0).getDate()
  return { startDate: `${y}-${pad2(m)}-01`, endDate: `${y}-${pad2(m)}-${pad2(lastDay)}` }
}
const stats = ref([
  { key: 'month', label: '本月采购', value: 0, type: 'primary', icon: 'Document', filter: monthRange() },
  { key: 'draft', label: '草稿订单', value: 0, type: 'warning', icon: 'EditPen', filter: { status: '草稿' } },
  { key: 'pending', label: '待审核', value: 0, type: 'info', icon: 'Clock', filter: { status: '待审核' } },
  { key: 'approved', label: '已生效', value: 0, type: 'success', icon: 'CircleCheck', filter: { status: '已生效' } }
])

async function loadStats() {
  try {
    const d = (await getPurchaseOrderDashboard()) || {}
    stats.value = [
      { key: 'month', label: '本月采购', value: d.monthOrders ?? 0, type: 'primary', icon: 'Document', filter: monthRange() },
      { key: 'draft', label: '草稿订单', value: d.draftOrders ?? 0, type: 'warning', icon: 'EditPen', filter: { status: '草稿' } },
      { key: 'pending', label: '待审核', value: d.pendingOrders ?? 0, type: 'info', icon: 'Clock', filter: { status: '待审核' } },
      { key: 'approved', label: '已生效', value: d.approvedOrders ?? 0, type: 'success', icon: 'CircleCheck', filter: { status: '已生效' } }
    ]
  } catch (e) {
    // 静默降级：取数失败时卡片保持原值，不弹诊断卡、不阻断列表
  }
}

// 卡片点击 → 应用该卡筛选条件（状态/日期先清空再套用，避免上一张卡残留）；再点同一张卡 = 取消
function onStatClick({ key, filter }) {
  if (activeStatKey.value === key) {
    activeStatKey.value = ''
    Object.assign(search, { status: '', startDate: '', endDate: '' })
  } else {
    activeStatKey.value = key
    Object.assign(search, { status: '', startDate: '', endDate: '' }, filter || {})
  }
  page.value.current = 1
  loadData()
}

const headerCellStyle = {
  background: 'rgba(99, 102, 241, 0.04)',
  color: '#1f2937',
  fontWeight: 600,
  borderBottom: '2px solid #e0e7ff'
}

const statusType = (s) => ({'草稿':'info','已确认':'','已入库':'warning','已完成':'success','已取消':'danger'}[s]||'info')

function statusDotClass(s) {
  return { '草稿': 'dot-info', '已确认': 'dot-success', '已入库': 'dot-warning', '已完成': 'dot-success', '已取消': 'dot-danger' }[s] || 'dot-info'
}

// 「入库」列标签色：后端按订单明细已入库数量与订单数量比较回填（未入库/部分入库/已入库）
const inboundStatusTagType = (s) => ({ '已入库': 'success', '部分入库': 'warning', '未入库': 'info' }[s] || 'info')

function handleSelectionChange(rows) {
  selectedRows.value = rows
}

function handlePrint(row) {
  printBillId.value = row.orderId
  printVisible.value = true
}

function handleBatchPrint(rows) {
  // 以插槽实时勾选为准（明细/汇总两种视图模式都可用），页面级缓存仅兜底
  const list = (rows && rows.length ? rows : selectedRows.value) || []
  if (!list.length) {
    ElMessage.warning(tt('请先勾选要打印的单据'))
    return
  }
  if (list.length > 1) {
    ElMessage.warning(tt('一次只能打印一张单据，请仅勾选一行'))
    return
  }
  handlePrint(list[0])
}

// 通用搜索栏（状态/日期/供应商/采购员）变更：先把值写回本页 search
// 注：BillSearchBar 变更后会紧接着抛 search 事件（壳已转发 → handleSearch），这里不重复发起查询
function onFilterChange(patch) {
  Object.assign(search, patch || {})
}

function handleSearch() {
  page.value.current = 1
  loadData()
}

// 列表查询参数（列表加载与导出共用同一套，避免两处口径漂移）
function buildQueryParams() {
  return {
    ...search,
    // 筛选条件自定义（2026-10-14）：f_* 表头字段过滤参数由通用壳统一下发
    ...(headerFilterState.value.params || {}),
    // 单据自定义字段搜索：沿用既有 customSearch 通道（值由通用壳从筛选条件统一打包）
    ...(headerFilterState.value.customSearch ? { customSearch: headerFilterState.value.customSearch } : {}),
    ...getSortParams()
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await api.get('/purchase/orders', { params: { current: page.value.current, size: page.value.size, ...buildQueryParams() } })
    orders.value = res.records || []
    page.value.total = res.total || 0
  } catch (e) {
    ElMessage.error(e.message || tt('查询失败'))
  } finally {
    loading.value = false
  }
}

const inboundDialog = (id) => { inbound.value = { visible: true, id, warehouse: '主仓库' } }

const doInbound = async () => {
  try {
    await api.post(`/purchase/orders/${inbound.value.id}/inbound`, { warehouse: inbound.value.warehouse })
    ElMessage.success(tt('入库成功，库存已增加'))
    inbound.value.visible = false
    loadData()
  } catch(e) {}
}

const handleExport = async () => {
  try {
    // 页面上看到什么列，导出就带什么列；行 = 当前筛选条件下的全部
    await exportListAsSeen({
      entityName: 'purchaseOrder',
      columns: visibleSortedColumns.value,
      fetchPage: (params) => api.get('/purchase/orders', { params }),
      params: buildQueryParams(),
      idField: 'orderId'
    })
    ElMessage.success(tt('导出成功'))
  } catch (e) {
    // 失败原因已由 exportUtil 统一提示
  }
}

const handleReset = () => {
  // 重置 = 回到未筛选状态：关键词 + 通用搜索栏全部维度一并清空
  Object.assign(search, { keyword: '', status: '', startDate: '', endDate: '', supplierId: '', employeeId: '' })
  activeStatKey.value = ''   // 同时取消统计卡片的高亮
  page.value.current = 1
  // 自定义筛选条件（f_*/自定义字段）的值由通用壳在重置时统一清空并下发空参数
  loadData()
}

const handleImport = () => {
  importDialogVisible.value = true
  importResult.value = null
  selectedImportFile.value = null
  if (uploadRef.value) {
    uploadRef.value.clearFiles()
  }
  loadTemplateFields()
}

const downloadTemplate = async () => {
  downloadingTemplate.value = true
  try {
    await downloadTemplateUtil('purchaseOrder')
    ElMessage.success(tt('模板下载成功'))
  } catch (err) {
    ElMessage.error(tt('模板下载失败'))
  } finally {
    downloadingTemplate.value = false
  }
}

const handleFileChange = (file) => {
  selectedImportFile.value = file.raw
  importResult.value = null
}

const confirmImport = async () => {
  if (!selectedImportFile.value) {
    ElMessage.warning(tt('请先选择文件'))
    return
  }

  importing.value = true
  try {
    const result = await importData('purchaseOrder', selectedImportFile.value)
    importResult.value = result

    if (result.failCount === 0) {
      ElMessage.success(`${tt("导入成功：共 ")}${result.successCount}${tt(" 条记录")}`)
      importDialogVisible.value = false
      loadData()
    } else {
      ElMessage.warning(`${tt("导入完成：成功 ")}${result.successCount}${tt(" 条，失败 ")}${result.failCount}${tt(" 条")}`)
      if (result.errors && result.errors.length > 0) {
        const detailHtml = result.errors.slice(0, 8).map(e => `<div style="margin:2px 0;padding:4px 8px;background:#fdf6ec;border-radius:3px;">第${e.rowIndex}行${e.fieldName ? '[' + e.fieldName + ']' : ''}: ${e.message}</div>`).join('')
        ElMessageBox.alert(`<div style="max-height:360px;overflow:auto;">${detailHtml}</div>`, tt('导入失败详情'), { dangerouslyUseHTMLString: true, confirmButtonText: tt('我知道了') })
      }
    }
  } catch (err) {
    ElMessage.error(tt('导入失败：') + (err.message || tt('未知错误')))
  } finally {
    importing.value = false
  }
}

onMounted(async () => {
  cf.load()
  loadStats()
  loadData()
  // 明细模式专属筛选条件元数据（明细报表 /columns 的 filters；失败静默：明细条件集为空时明细模式沿用汇总条件集）
  try {
    const res = await purchaseOrderDetailApi.getColumns()
    detailFiltersMeta.value = (res && res.filters) || []
  } catch (e) { /* 静默降级 */ }
})
useAutoRefresh(() => { loadData(); loadStats() })
</script>

<style scoped lang="scss">
.purchase-order-page {
  padding: 20px 24px;
  background: linear-gradient(135deg, #f8faff 0%, #f0f4ff 50%, #f8fcff 100%);
  min-height: calc(100vh - 60px);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;

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
    background: #fff;
    border: 1px solid #e5e7eb;
    transition: all .2s;
    &:hover {
      color: #6366f1;
      border-color: #c7d2fe;
    }
  }

  .title-group { display: flex; flex-direction: column; gap: 2px; }

  .page-title {
    font-size: 20px;
    font-weight: 700;
    color: #111827;
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .title-badge {
    font-size: 10px;
    font-weight: 600;
    color: #6366f1;
    background: linear-gradient(135deg, #e0e7ff, #f0f4ff);
    padding: 2px 8px;
    border-radius: 4px;
    letter-spacing: 1px;
  }

  .page-sub { font-size: 12px; color: #9ca3af; }

  .header-actions { display: flex; gap: 8px; }
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

.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  position: relative;
  overflow: hidden;
  transition: all .3s;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 24px rgba(99, 102, 241, 0.1);
    border-color: #c7d2fe;
  }

  .stat-icon {
    width: 48px;
    height: 48px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 24px;
    color: #fff;
    position: relative;
    z-index: 1;

    &.primary { background: linear-gradient(135deg, #6366f1, #8b5cf6); }
    &.warning { background: linear-gradient(135deg, #f59e0b, #f97316); }
    &.info { background: linear-gradient(135deg, #3b82f6, #60a5fa); }
    &.success { background: linear-gradient(135deg, #10b981, #34d399); }
    &.danger { background: linear-gradient(135deg, #ef4444, #f87171); }
  }

  .stat-info { flex: 1; }
  .stat-value {
    font-size: 24px;
    font-weight: 700;
    color: #111827;
    line-height: 1.2;
  }
  .stat-label {
    font-size: 12px;
    color: #9ca3af;
    margin-top: 4px;
  }

  .stat-glow {
    position: absolute;
    right: -20px;
    top: -20px;
    width: 100px;
    height: 100px;
    border-radius: 50%;
    opacity: 0.1;
    filter: blur(20px);

    &.primary { background: #6366f1; }
    &.warning { background: #f59e0b; }
    &.info { background: #3b82f6; }
    &.success { background: #10b981; }
    &.danger { background: #ef4444; }
  }
}

.content-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.search-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #f3f4f6;

  .search-right {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
  }

  .search-input {
    width: 280px;
    :deep(.el-input__wrapper) {
      border-radius: 8px;
      box-shadow: 0 0 0 1px #e5e7eb inset;
      &:hover { box-shadow: 0 0 0 1px #c7d2fe inset; }
      &.is-focus { box-shadow: 0 0 0 1px #6366f1 inset; }
    }
  }
}

/* 状态页签样式已删除：状态改为通用搜索栏的「单据状态」下拉（filters 声明 'status'，壳自动隐藏页签） */

.tech-table {
  :deep(.el-table__header th) {
    background: rgba(99, 102, 241, 0.04) !important;
  }

  :deep(.el-table__row:hover > td) {
    background: rgba(99, 102, 241, 0.03) !important;
  }

  :deep(.el-table td) {
    border-bottom: 1px solid #f3f4f6;
  }
}

.order-no-cell {
  display: flex;
  align-items: center;
  gap: 6px;

  .order-no-text {
    color: #6366f1;
    font-weight: 500;
    cursor: pointer;
    &:hover { color: #8b5cf6; }
  }

  .link-icon {
    color: #c7d2fe;
    font-size: 12px;
    opacity: 0;
    transition: opacity .2s;
  }
  &:hover .link-icon { opacity: 1; }
}

.customer-cell {
  display: flex;
  align-items: center;
  gap: 10px;

  .customer-avatar {
    width: 32px;
    height: 32px;
    border-radius: 8px;
    background: linear-gradient(135deg, #6366f1, #8b5cf6);
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 13px;
    font-weight: 600;
    flex-shrink: 0;
  }

  .customer-name {
    font-size: 13px;
    color: #374151;
    font-weight: 500;
  }
}

.net-amount {
  color: #6366f1;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.status-cell {
  display: flex;
  align-items: center;
  gap: 6px;

  .status-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;

    &.dot-success { background: #10b981; box-shadow: 0 0 6px rgba(16, 185, 129, 0.5); }
    &.dot-warning { background: #f59e0b; box-shadow: 0 0 6px rgba(245, 158, 11, 0.5); }
    &.dot-info { background: #3b82f6; box-shadow: 0 0 6px rgba(59, 130, 246, 0.5); }
    &.dot-danger { background: #ef4444; box-shadow: 0 0 6px rgba(239, 68, 68, 0.5); }
  }

  .status-tag {
    border-radius: 4px;
    font-weight: 500;
  }
}

.action-cell {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.pagination-wrap {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-top: 1px solid #f3f4f6;

  .pagination-info {
    font-size: 13px;
    color: #6b7280;
  }
}

:deep(.el-button) {
  border-radius: 8px;
  font-weight: 500;
}

:deep(.el-dialog) {
  border-radius: 12px;
}

.import-dialog-content {
  .import-step {
    padding: 8px 0;
    .step-title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 15px;
      font-weight: 600;
      color: #303133;
      margin-bottom: 8px;
      .el-icon { font-size: 18px; color: #409EFF; }
    }
    .step-desc { font-size: 13px; color: #909399; margin-bottom: 12px; }
  }
  .import-uploader {
    :deep(.el-upload-dragger) { width: 100%; height: 140px; }
  }
  .import-result {
    .result-title { font-size: 15px; font-weight: 600; color: #303133; margin-bottom: 12px; }
    .import-error-alert {
      margin-top: 12px;
      .error-list {
        max-height: 160px; overflow-y: auto;
        .error-item { font-size: 12px; padding: 2px 0; color: #F56C6C; }
        .error-more { font-size: 12px; color: #909399; padding-top: 4px; }
      }
    }
  }
}

@media (max-width: 1200px) {
  .stat-cards { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 768px) {
  .purchase-order-page { padding: 12px; }
  .stat-cards { grid-template-columns: 1fr; }
  .search-bar {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }
}
</style>