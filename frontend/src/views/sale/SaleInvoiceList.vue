<template>
  <BillListPage
    page-class="sale-invoice-page"
    back-path="/sale"
    :title="$tt('销售发票')"
    badge="SALE INVOICE"
    sub-title="规范发票管理 · 确保税务合规"
    create-path="/sale/invoices/new"
    :stats="stats"
    :active-stat-key="activeStatKey"
    search-placeholder="输入发票号/来源单号/客户名称搜索"
    keyword-label="单据编号"
    :filters="['status', 'date', 'customer', 'employee']"
    :filter-defs="filterDefs"
    :detail-filter-defs="detailFilterDefs"
    :status-options="statusOptions"
    :filter-model="search"
    employee-label="销售员"
    :table-data="tableData"
    :loading="loading"
    :pagination="pagination"
    storage-key="SaleInvoiceList"
    :columns="columns"
    :table-key="configVersion"
    bill-type="SALE_INVOICE"
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
    @page-change="fetchData"
  >
    <template #header-actions="{ selectedRows: sel }">
      <!-- 页头按钮统一走通用组件（2026-09-19）：按钮码由 perm-prefix 派生，权限控制一处生效 -->
      <BillHeaderActions
        perm-prefix="sale:invoices"
        :add-text="$tt('新增发票')"
        :print-disabled="!sel.length"
        @add="$router.push('/sale/invoices/new')"
        @print="handleBatchPrint(sel)"
        @export="handleExport"
        @import="handleImport"
      />
    </template>

    <!-- 业务列（插槽注入壳的 el-table） -->
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
          <div v-if="col.type === 'billNo'" class="order-no-cell">
            <span class="order-no-text" @click="$router.push(`/sale/invoices/${row.invoiceId}`)">{{ row[col.prop] }}</span>
            <el-icon class="link-icon"><Link /></el-icon>
          </div>
          <div v-else-if="col.type === 'customer'" class="customer-cell">
            <div class="customer-avatar">{{ (row[col.prop] || '?').charAt(0) }}</div>
            <div class="customer-info">
              <div class="customer-name">{{ row[col.prop] }}</div>
            </div>
          </div>
          <span v-else-if="col.type === 'amount'" class="amount-text">¥{{ Number(row[col.prop] || 0).toFixed(2) }}</span>
          <span v-else-if="col.type === 'taxAmount'" class="discount-text">¥{{ Number(row[col.prop] || 0).toFixed(2) }}</span>
          <span v-else-if="col.type === 'netAmount'" class="net-amount">¥{{ Number(row[col.prop] || 0).toFixed(2) }}</span>
          <div v-else-if="col.type === 'status'" class="status-cell">
            <span :class="['status-dot', statusDotClass(row.status)]"></span>
            <el-tag size="small" :type="statusType(row.status)" effect="light" class="status-tag">{{ $dt(row.status) }}</el-tag>
          </div>
          <el-tag v-else-if="col.type === 'approval'" size="small" :type="approvalStatusType(row.approvalStatus)" effect="plain">{{ $dt(row.approvalStatus || '-') }}</el-tag>
        </template>
      </el-table-column>
    </template>
    <AuditColumns />
  </BillListPage>

  <!-- 导入弹窗 -->
  <el-dialog v-model="importDialogVisible" :title="$tf('销售发票导入')" width="560px" :close-on-click-modal="false" class="tech-dialog">
    <div class="import-dialog-content">
      <div class="import-step">
        <div class="step-title"><el-icon><Download /></el-icon><span>{{ $tt("第一步：下载导入模板") }}</span></div>
        <div class="step-desc">{{ $tt("下载模板后，按模板格式填写数据，支持 .xlsx 格式") }}</div>
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
        <div class="step-title"><el-icon><Upload /></el-icon><span>{{ $tt("第二步：上传数据文件") }}</span></div>
        <div class="step-desc">{{ $tt("选择填写好的 Excel 文件上传导入") }}</div>
        <el-upload ref="uploadRef" action="#" :auto-upload="false" :on-change="handleFileChange" :show-file-list="true" accept=".xlsx,.xls,.csv" drag class="import-uploader">
          <el-icon class="el-icon--upload"><Upload /></el-icon>
          <div class="el-upload__text">{{ $tt("将文件拖到此处，或") }}<em>{{ $tt("点击上传") }}</em></div>
          <template #tip><div class="el-upload__tip">{{ $tt("请上传 .xlsx / .xls 格式文件，文件大小不超过 10MB") }}</div></template>
        </el-upload>
      </div>
      <div v-if="importResult" class="import-result">
        <el-divider />
        <div class="result-title">{{ $tt("导入结果") }}</div>
        <el-row :gutter="16">
          <el-col :span="8"><el-statistic :title="$tt('总记录数')" :value="importResult.totalCount || 0" /></el-col>
          <el-col :span="8"><el-statistic :title="$tt('成功')" :value="importResult.successCount || 0"><template #suffix><el-icon color="#67C23A"><CircleCheck /></el-icon></template></el-statistic></el-col>
          <el-col :span="8"><el-statistic :title="$tt('失败')" :value="importResult.failCount || 0"><template #suffix><el-icon color="#F56C6C"><CircleClose /></el-icon></template></el-statistic></el-col>
        </el-row>
        <el-alert v-if="importResult.failCount > 0" :title="`有 ${importResult.failCount} 条记录导入失败`" type="error" :closable="false" class="import-error-alert">
          <div class="error-list">
            <div v-for="(err, idx) in (importResult.errors || []).slice(0, 10)" :key="idx" class="error-item">
              第 {{ err.rowIndex }} 行 {{ err.fieldName ? '[' + err.fieldName + ']' : '' }}: {{ err.message }}
            </div>
            <div v-if="(importResult.errors || []).length > 10" class="error-more">... 还有 {{ importResult.errors.length - 10 }} 条错误</div>
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
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Download, Printer, Link, Upload, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { listSaleInvoice, getSaleInvoiceDashboard, submitSaleInvoice, approveSaleInvoice, deleteSaleInvoice, reverseApproveSaleInvoice } from '@/api/sale/saleInvoice'
import AuditColumns from '@/components/AuditColumns.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { statusType, statusDotClass, approvalStatusType } from '@/composables/useBillStatus'
import { exportListAsSeen, downloadTemplate as downloadTemplateUtil, importData } from '@/utils/exportUtil'

import BillListPage from '@/components/bill/BillListPage.vue'
// 页头按钮通用组件（2026-09-19）：新增/打印/导出/导入统一渲染 + 统一按钮权限
import BillHeaderActions from '@/components/bill/BillHeaderActions.vue'
import { useTableSort } from '@/composables/useTableSort'
import { useCustomFieldSearch } from '@/composables/useCustomFieldSearch'
// 筛选条件自定义（2026-10-14 全面推广）：条件候选拼装共享工厂（与销售订单试点同源，禁手写 def 数组）
import { stdDefs, fld, cfDefs, detailFilterDefsOf } from '@/config/billFilterDefs'
// 明细报表引擎（同一套接口）：取 /columns 的 filters 元数据作「明细模式专属条件」
import { saleInvoiceDetailApi } from '@/api/report/billDetailReport'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('sale:invoices')
const { handleSortChange, getSortParams } = useTableSort()

/**
 * 批量操作（2026-09-19）：UI/确认/执行/结果面板全部由通用壳承担，这里只声明"怎么执行"。
 * 命名口径全系统统一：批量提交 / 批量审批 / 批量重置草稿 / 批量删除 / 批量导出。
 */
const batchActions = [
  {
    key: 'submit',
    label: tt('批量提交'),
    type: 'primary',
    perm: 'sale:invoices:submit',
    exec: (row) => submitSaleInvoice(row.invoiceId),
    // 已提交/已审核的自动跳过（不报错，进"跳过清单"说明原因）
    skipReason: (row) => (row.status && row.status !== '草稿'
      ? tt('当前状态：') + row.status + tt('，不是草稿，无需提交') : '')
  },
  {
    key: 'approve',
    label: tt('批量审批'),
    perm: 'sale:invoices:audit',
    exec: (row) => approveSaleInvoice(row.invoiceId),
    skipReason: (row) => (row.status !== '待审核'
      ? tt('当前状态：') + (row.status || '-') + tt('，不是待审核，无需审批') : '')
  },
  {
    // 批量重置草稿（2026-09-19）：与编辑页「重置草稿」同一个后端入口（/reverse-approve），
    // 能否重置由后端判断，被拒原话进"失败清单"，与单条操作口径一致
    key: 'reverse',
    label: tt('批量重置草稿'),
    perm: 'sale:invoices:reverseApprove',
    exec: (row) => reverseApproveSaleInvoice(row.invoiceId)
  },
  {
    key: 'delete',
    label: tt('批量删除'),
    type: 'danger',
    perm: 'sale:invoices:delete',
    exec: (row) => deleteSaleInvoice(row.invoiceId),
    // 状态冲突：选中行含非草稿 → 按钮置灰并说明原因
    disabledReason: (rows) => (rows.some(r => r.status && r.status !== '草稿' && r.status !== '已驳回' && r.status !== '已取消')
      ? tt('非草稿不能删除，请先重置草稿') : '')
  },
  {
    key: 'export',
    label: tt('批量导出'),
    perm: 'sale:invoices:export',
    entity: 'saleInvoice',
    idOf: (row) => row.invoiceId
  }
]


const onSortChange = (e) => { handleSortChange(e); fetchData() }// 列定义（用于列设置抽屉+动态渲染）= 表格全部数据列
// type 标记单元格渲染分支：billNo 单号链接、customer 客户头像、amount/taxAmount/netAmount 金额、status 状态、approval 审核；纯文本列不设 type
const columns = [
  { prop: 'invoiceNo', label: '发票号', minWidth: 180, sortable: true, type: 'billNo' },
  { prop: 'taxInvoiceNo', label: '税票号码', width: 150 },
  { prop: 'invoiceDate', label: '开票日期', width: 120, sortable: true },
  // 客户为展示字段（非库列，后端排序白名单无对应列）→ 关闭列头排序，避免"点了没反应"
  { prop: 'customerName', label: '客户', minWidth: 180, type: 'customer' },
  { prop: 'invoiceType', label: '发票类型', width: 150, sortable: true },
  { prop: 'totalAmount', label: '发票金额', width: 120, align: 'right', sortable: true, type: 'amount' },
  { prop: 'taxAmount', label: '开票税额', width: 110, align: 'right', sortable: true, type: 'taxAmount' },
  { prop: 'netAmount', label: '开票价税合计', width: 130, align: 'right', sortable: true, type: 'netAmount' },
  { prop: 'status', label: '状态', width: 160, type: 'status' },
  { prop: 'approvalStatus', label: '审核', width: 100, type: 'approval' }
]
const { columnSettingVisible, isColVisible, getColFixed, configVersion, sortedColumns } = useColumnSetting('SaleInvoiceList', columns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))

const loading = ref(false)
const tableData = ref([])
// 统一入参名（docs/knowledge-base.md 6.1）：keyword / status / startDate / endDate / customerId / employeeId
const search = reactive({ keyword: '', status: '', startDate: '', endDate: '', customerId: '', employeeId: '' })
// 自定义字段搜索（设置中心勾了「可搜索」的字段 → 列表搜索项）
const cf = useCustomFieldSearch('sale_invoice')

const pagination = reactive({ current: 1, size: 20, total: 0, ...getSortParams() })
const selectedRows = ref([])

const printVisible = ref(false)
const printBillId = ref(null)

// 通用搜索栏「单据状态」下拉选项 = 本单据状态机真实取值
// 依据 SaleInvoiceServiceImpl + AbstractBillService 状态写入语句：草稿/待审核/已生效/已取消/已作废
// （原状态页签的「已关闭」本单据无写入路径 → 属于筛不出数据的假选项，已移除）
const statusOptions = [
  { label: '草稿', value: '草稿' },
  { label: '待审核', value: '待审核' },
  { label: '已生效', value: '已生效' },
  { label: '已取消', value: '已取消' },
  { label: '已作废', value: '已作废' }
]

// ============ 筛选条件自定义（2026-10-14 全面推广：销售发票） ============
// 条件候选 = 本单表头业务字段 + 单据自定义字段（设置中心勾「可搜索」的，沿用 customSearch 既有通道）。
// 条件键 = 后端白名单键（SaleInvoiceServiceImpl 静态块登记）；std 四件套键与明细报表引擎同键。
// 默认勾选口径（守则 2.7.2-6）：std = 现状默认勾选、表头字段默认不勾、自定义字段默认勾选（工厂已内置）。
const filterDefs = computed(() => [
  ...stdDefs({
    status: { options: statusOptions },
    date: { key: 'invoiceDate', label: '开票日期' },
    customer: { key: 'customerName' },
    employee: { key: 'salerName', label: '销售员' }
  }),
  // —— 表头字段（默认不勾，用户在「筛选设置」里按需勾选）——
  fld('text', 'invoiceNo', '发票号'),
  fld('text', 'taxInvoiceNo', '税票号码'),
  fld('text', 'invoiceType', '发票类型'),
  fld('text', 'taxNo', '税号'),
  fld('text', 'address', '地址'),
  fld('text', 'bankName', '开户银行'),
  fld('text', 'bankAccount', '银行账号'),
  fld('text', 'arNo', '应收单号'),
  fld('text', 'deliveryNo', '来源出库单号'),
  fld('text', 'originalInvoiceNo', '原蓝字发票号'),
  fld('text', 'sourceBillNo', '源单据号'),
  fld('text', 'opportunityNo', '商机编号'),
  fld('text', 'remark', '备注'),
  fld('select', 'currencyCode', '币种', 'currency'),
  fld('select', 'settleModeName', '结算方式', 'settleMode'),
  fld('numberRange', 'totalAmount', '发票金额'),
  fld('numberRange', 'discountAmount', '折扣额'),
  fld('numberRange', 'taxAmount', '税额'),
  fld('numberRange', 'netAmount', '价税合计'),
  fld('numberRange', 'discountRate', '折扣率%'),
  fld('numberRange', 'exchangeRate', '汇率'),
  fld('numberRange', 'taxRateAvg', '平均税率%'),
  fld('numberRange', 'prepaidRate', '预付比例%'),
  fld('numberRange', 'prepaidAmount', '预付款金额'),
  // —— 单据自定义字段（沿用既有 customSearch 通道）——
  ...cfDefs(cf)
])

// ============ 明细模式专属筛选条件（与销售订单试点同口径） ============
// 来源 = 销售发票明细报表 /columns 的 filters 元数据（键与引擎同键）；
// std 四件套默认勾选、值走页面 search 通道，其余默认不勾。
const detailFiltersMeta = ref([])
const STD_BY_KEY = { status: 'status', invoiceDate: 'date', customerName: 'customer', salerName: 'employee' }
const detailFilterDefs = computed(() => detailFilterDefsOf(detailFiltersMeta.value, STD_BY_KEY))

/** 自定义条件参数（由通用壳经 filter-params 下发）：params 汇总用 / customSearch 自定义字段 / detailParams 明细用 */
const headerFilterState = ref({ params: {}, customSearch: undefined, detailParams: {} })
function onHeaderFilterParams(payload) {
  headerFilterState.value = payload || { params: {}, customSearch: undefined, detailParams: {} }
}

// 顶部统计卡片：数据来自后端仪表盘接口（与列表同数据权限口径；原来 4 张卡片硬编码 0 → 永远显示 0）
// 带 filter 的卡片可点击 → 直接筛选下方列表（再点同一张卡 = 取消）
const activeStatKey = ref('')
const pad2 = (n) => String(n).padStart(2, '0')
function monthRange() {
  const d = new Date()
  const y = d.getFullYear()
  const m = d.getMonth() + 1
  const lastDay = new Date(y, m, 0).getDate()
  return { startDate: `${y}-${pad2(m)}-01`, endDate: `${y}-${pad2(m)}-${pad2(lastDay)}` }
}
const stats = ref([
  { key: 'month', label: '本月开票', value: 0, type: 'primary', icon: 'Document', filter: monthRange() },
  { key: 'draft', label: '草稿发票', value: 0, type: 'warning', icon: 'EditPen', filter: { status: '草稿' } },
  { key: 'pending', label: '待审核', value: 0, type: 'info', icon: 'Clock', filter: { status: '待审核' } },
  { key: 'approved', label: '已生效', value: 0, type: 'success', icon: 'CircleCheck', filter: { status: '已生效' } }
])

async function loadStats() {
  try {
    const d = (await getSaleInvoiceDashboard()) || {}
    stats.value = [
      { key: 'month', label: '本月开票', value: d.monthInvoices ?? 0, type: 'primary', icon: 'Document', filter: monthRange() },
      { key: 'draft', label: '草稿发票', value: d.draftInvoices ?? 0, type: 'warning', icon: 'EditPen', filter: { status: '草稿' } },
      { key: 'pending', label: '待审核', value: d.pendingInvoices ?? 0, type: 'info', icon: 'Clock', filter: { status: '待审核' } },
      { key: 'approved', label: '已生效', value: d.approvedInvoices ?? 0, type: 'success', icon: 'CircleCheck', filter: { status: '已生效' } }
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
  pagination.current = 1
  fetchData()
}

function handlePrint(row) {
  printBillId.value = row.invoiceId
  printVisible.value = true
}

function handleBatchPrint(rows) {
  // 以插槽实时勾选为准（明细/汇总两种视图模式都可用），页面级缓存仅兜底
  const list = (rows && rows.length ? rows : selectedRows.value) || []
  if (!list.length) {
    ElMessage.warning(tt('请先勾选要打印的发票'))
    return
  }
  if (list.length > 1) {
    ElMessage.warning(tt('一次只能打印一张单据，请仅勾选一行'))
    return
  }
  handlePrint(list[0])
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

async function fetchData() {
  loading.value = true
  try {
    const res = await listSaleInvoice({ current: pagination.current, size: pagination.size, ...buildQueryParams() })
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (e) {
    ElMessage.error(e.message || tt('查询失败'))
  } finally {
    loading.value = false
  }
}

// 通用搜索栏（状态/日期/客户/销售员）变更：先把值写回本页 search
// 注：BillSearchBar 变更后会紧接着抛 search 事件（壳已转发 → handleSearch），这里不重复发起查询
function onFilterChange(patch) {
  Object.assign(search, patch || {})
}

function handleSearch() {
  pagination.current = 1
  fetchData()
}

function handleReset() {
  // 重置 = 回到未筛选状态：关键词 + 通用搜索栏全部维度一并清空
  Object.assign(search, { keyword: '', status: '', startDate: '', endDate: '', customerId: '', employeeId: '' })
  activeStatKey.value = ''
  pagination.current = 1
  // 自定义筛选条件（f_*/自定义字段）的值由通用壳在重置时统一清空并下发空参数
  fetchData()
}

async function handleExport() {
  try {
    // 页面上看到什么列，导出就带什么列；行 = 当前筛选条件下的全部
    await exportListAsSeen({
      entityName: 'saleInvoice',
      columns: visibleSortedColumns.value,
      fetchPage: listSaleInvoice,
      params: buildQueryParams(),
      idField: 'invoiceId'
    })
    ElMessage.success(tt('导出成功'))
  } catch (e) {
    // 失败原因已由 exportUtil 统一提示
  }
}

// ========== 导入功能 ==========
const importDialogVisible = ref(false)
const downloadingTemplate = ref(false)
const downloadingCustomTemplate = ref(false)
const importing = ref(false)
const selectedImportFile = ref(null)
const importResult = ref(null)
const uploadRef = ref(null)
const fieldPanelActive = ref('')
const templateFields = reactive({ required: [], optional: [] })
const checkedOptionalFields = ref([])

async function loadTemplateFields() {
  try {
    const resp = await fetch(`/api/export/template/saleInvoice/fields`, { headers: { Authorization: `Bearer ${localStorage.getItem('token') || ''}` } })
    const json = await resp.json()
    if (json.code === 200 && json.data) {
      templateFields.required = json.data.required || []
      templateFields.optional = json.data.optional || []
      checkedOptionalFields.value = []
    }
  } catch (e) { /* 静默失败 */ }
}

function handleImport() {
  importDialogVisible.value = true
  importResult.value = null
  selectedImportFile.value = null
  if (uploadRef.value) uploadRef.value.clearFiles()
  loadTemplateFields()
}

function handleFileChange(file) {
  selectedImportFile.value = file.raw
  importResult.value = null
}

async function downloadTemplate() {
  downloadingTemplate.value = true
  try {
    await downloadTemplateUtil('saleInvoice')
    ElMessage.success(tt('模板下载成功'))
  } catch (e) {
    ElMessage.error(tt('模板下载失败'))
  } finally {
    downloadingTemplate.value = false
  }
}

function selectAllOptional() { checkedOptionalFields.value = [...templateFields.optional] }
function clearOptional() { checkedOptionalFields.value = [] }

async function downloadCustomTemplate() {
  downloadingCustomTemplate.value = true
  try {
    await downloadTemplateUtil('saleInvoice', checkedOptionalFields.value)
    ElMessage.success(tt('自定义模板下载成功'))
  } catch (e) {
    ElMessage.error(tt('自定义模板下载失败'))
  } finally {
    downloadingCustomTemplate.value = false
  }
}

async function confirmImport() {
  if (!selectedImportFile.value) {
    ElMessage.warning(tt('请先选择文件'))
    return
  }
  importing.value = true
  try {
    const result = await importData('saleInvoice', selectedImportFile.value)
    importResult.value = result
    if (result.failCount === 0) {
      ElMessage.success(`${tt("导入成功：共 ")}${result.successCount}${tt(" 条记录")}`)
      importDialogVisible.value = false
      fetchData()
    } else {
      const errList = (result.errors || []).slice(0, 20)
      const errHtml = errList.map(e => `第${e.rowIndex}行${e.fieldName ? '[' + e.fieldName + ']' : ''}：${e.message}`).join('<br/>')
      ElMessageBox.alert(
        `<div style="max-height:400px;overflow-y:auto;font-size:13px;line-height:1.8;">${errHtml || '未知错误'}</div>`,
        `${tt("导入失败：成功 ")}${result.successCount}${tt(" 条，失败 ")}${result.failCount}${tt(" 条")}`,
        { dangerouslyUseHTMLString: true, confirmButtonText: tt('我知道了') }
      ).catch(() => {})
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
  // 明细模式专属筛选条件元数据（明细报表 /columns 一次带回 filters），失败静默：不影响汇总列表
  try {
    const res = await saleInvoiceDetailApi.getColumns()
    detailFiltersMeta.value = (res && res.filters) || []
  } catch (e) { /* 静默降级 */ }
  fetchData()
})
useAutoRefresh(() => { fetchData(); loadStats() })
</script>

<style scoped lang="scss">
/* 页面骨架样式（页头/统计卡/搜索条/表格壳/分页/打印/列设置）已由 BillListPage 壳组件接管 */
/* 以下仅保留：插槽内业务单元格样式 + header-actions 插槽按钮样式（作用于本页作用域） */

.btn-primary-glow {
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  border: none;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
  &:hover {
    box-shadow: 0 6px 20px rgba(99, 102, 241, 0.4);
    transform: translateY(-1px);
  }
}

/* 插槽内业务单元格样式（保留本页作用域） */
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

.amount-text { color: #374151; font-variant-numeric: tabular-nums; }
.discount-text { color: #f59e0b; font-variant-numeric: tabular-nums; }
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

/* 导入弹窗样式 */
.import-dialog-content {
  .import-step {
    .step-title {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 15px;
      font-weight: 600;
      color: #1f2937;
      margin-bottom: 8px;
    }
    .step-desc {
      font-size: 13px;
      color: #6b7280;
      margin-bottom: 12px;
    }
  }
  .import-uploader {
    margin-top: 8px;
  }
  .import-result {
    .result-title {
      font-size: 15px;
      font-weight: 600;
      margin-bottom: 12px;
      color: #1f2937;
    }
    .import-error-alert {
      margin-top: 12px;
      .error-list {
        margin-top: 8px;
        max-height: 180px;
        overflow-y: auto;
        .error-item {
          font-size: 12px;
          line-height: 1.8;
          color: #f56c6c;
        }
        .error-more {
          font-size: 12px;
          color: #909399;
          margin-top: 4px;
        }
      }
    }
  }
}
</style>
