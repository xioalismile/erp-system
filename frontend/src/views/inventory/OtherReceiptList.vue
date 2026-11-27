<template>
  <!-- 通用列表壳（页头/统计卡/搜索条/表格外壳/分页/打印/列设置）+ 声明式筛选维度 -->
  <BillListPage
    page-class="other-receipt-list-page"
    back-path="/inventory"
    :title="$tt('其他入库')"
    badge="RECEIPT"
    sub-title="非采购/生产类入库 · 灵活记录各类入库业务"
    :stats="stats"
    :active-stat-key="activeStatKey"
    search-placeholder="输入入库单号搜索"
    keyword-label="单据编号"
    :filters="['status', 'date', 'employee']"
    :filter-defs="filterDefs"
    :detail-filter-defs="detailFilterDefs"
    :status-options="statusOptions"
    :filter-model="search"
    employee-label="经办人"
    :table-data="tableData"
    :loading="loading"
    :pagination="pagination"
    storage-key="OtherReceiptList"
    :columns="columns"
    :table-key="configVersion"
    bill-type="OTHER_RECEIPT"
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
        perm-prefix="inventory:other-receipts"
        :add-text="$tt('新增入库单')"
        :print-disabled="!sel.length"
        @add="handleAdd"
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
          <div v-if="col.type === 'code'" class="order-no-cell">
            <span class="order-no-text" @click="handleEdit(row)">{{ row[col.prop] }}</span>
            <el-icon class="link-icon"><Link /></el-icon>
          </div>
          <span v-else-if="col.type === 'warehouse'">{{ getWarehouseName(row.warehouseId) }}</span>
          <span v-else-if="col.type === 'amount2'" class="amount-text">{{ Number(row[col.prop] || 0).toFixed(2) }}</span>
          <div v-else-if="col.type === 'status'" class="status-cell">
            <span :class="['status-dot', statusDotClass(row.status)]"></span>
            <el-tag size="small" :type="statusTagType(row.status)" effect="light" class="status-tag">{{ $dt(row.status) }}</el-tag>
          </div>
        </template>
      </el-table-column>
    </template>
    <AuditColumns />
  </BillListPage>

    <el-dialog
      v-model="importDialogVisible"
      :title="$tf('其他入库单导入')"
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
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listOtherReceipt, getOtherReceiptDashboard,
  submitOtherReceipt, approveOtherReceipt, reverseApproveOtherReceipt,
  deleteOtherReceipt, closeOtherReceipt, cancelOtherReceipt,
  otherReceiptDetailApi
} from '@/api/inventory/otherReceipt'
// 筛选条件自定义共享工厂（2026-10-14 全面推广）：候选集拼装统一在工厂，页面只声明本单键位差异
import { stdDefs, fld, cfDefs, detailFilterDefsOf } from '@/config/billFilterDefs'
import BillListPage from '@/components/bill/BillListPage.vue'
// 页头按钮通用组件（2026-09-19）：新增/打印/导出/导入统一渲染 + 统一按钮权限
import BillHeaderActions from '@/components/bill/BillHeaderActions.vue'
import { listAllWarehouse } from '@/api/basedata/warehouse'
import { Printer, ArrowLeft, Search, Plus, Link, EditPen, CircleCheck, Lock, Close, Delete, Document, Clock, Setting, Upload, Download, CircleClose } from '@element-plus/icons-vue'
import PrintPreviewDialog from '@/components/PrintPreviewDialog.vue'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
import AuditColumns from '@/components/AuditColumns.vue'
import { exportListAsSeen, downloadTemplate as downloadTemplateUtil, importData } from '@/utils/exportUtil'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { useTableSort } from '@/composables/useTableSort'
import { useCustomFieldSearch } from '@/composables/useCustomFieldSearch'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('inventory:other-receipts')
const { handleSortChange, getSortParams } = useTableSort()
// 自定义字段「可搜索」搜索项（设置中心 → 自定义字段配置里勾了「可搜索」的字段才渲染）
const cf = useCustomFieldSearch('other_receipt')


/**
 * 批量操作声明（2026-09-20）：与销售订单等单据同一口径，UI/确认/执行/结果面板全部由通用壳承担。
 * 命名统一：批量提交 / 批量审批 / 批量重置草稿 / 批量删除 / 批量导出。
 */
const batchActions = [
  {
    key: 'submit',
    label: tt('批量提交'),
    perm: 'inventory:other-receipts:submit',
    type: 'primary',
    exec: (row) => submitOtherReceipt(row.receiptId),
    // 已提交/已审核的自动跳过（不报错，进"跳过清单"说明原因）
    skipReason: (row) => (row.status && row.status !== '草稿'
      ? tt('当前状态：') + row.status + tt('，不是草稿，无需提交') : '')
  },
  {
    key: 'approve',
    label: tt('批量审批'),
    perm: 'inventory:other-receipts:audit',
    exec: (row) => approveOtherReceipt(row.receiptId),
    skipReason: (row) => (row.status !== '待审核'
      ? tt('当前状态：') + (row.status || '-') + tt('，不是待审核，无需审批') : '')
  },
  {
    // 批量重置草稿：与编辑页「重置草稿」同一个后端入口（/reverse-approve），能否重置由后端判断
    key: 'reverse',
    label: tt('批量重置草稿'),
    perm: 'inventory:other-receipts:reverseApprove',
    exec: (row) => reverseApproveOtherReceipt(row.receiptId)
  },
  {
    key: 'delete',
    label: tt('批量删除'),
    type: 'danger',
    perm: 'inventory:other-receipts:delete',
    exec: (row) => deleteOtherReceipt(row.receiptId),
    // 状态冲突：选中行含非草稿 → 按钮置灰并说明原因
    disabledReason: (rows) => (rows.some(r => r.status && r.status !== '草稿' && r.status !== '已驳回' && r.status !== '已取消')
      ? tt('非草稿不能删除，请先重置草稿') : '')
  },
  {
    key: 'export',
    label: tt('批量导出'),
    perm: 'inventory:other-receipts:export',
    entity: 'otherReceipt',
    idOf: (row) => row.receiptId
  }
]

const onSortChange = (e) => { handleSortChange(e); fetchData() }
const router = useRouter()

// 列定义（用于列设置抽屉+动态渲染）：type 标记单元格渲染分支
// code=单号链接列(点击编辑) warehouse=仓库名称翻译列 amount2=金额(2位) status=状态标签 纯文本列无 type
const columns = [
  { prop: 'receiptNo', label: '入库单号', minWidth: 160, sortable: true, type: 'code' },
  { prop: 'receiptDate', label: '入库日期', width: 120, sortable: true },
  { prop: 'receiptType', label: '入库类型', width: 120, sortable: true },
  { prop: 'warehouseId', label: '仓库', width: 140, tooltip: true, type: 'warehouse' },
  { prop: 'totalAmount', label: '总金额', width: 120, align: 'right', sortable: true, type: 'amount2' },
  { prop: 'status', label: '状态', width: 100, align: 'center', type: 'status' },
  { prop: 'createdBy', label: '制单人', width: 100, sortable: true },
  { prop: 'createdAt', label: '制单时间', width: 160, sortable: true }
]
const { columnSettingVisible, openColumnSetting, isColVisible, getColFixed, configVersion, sortedColumns } = useColumnSetting('OtherReceiptList', columns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))

const loading = ref(false)
const tableData = ref([])
const search = reactive({ keyword: '', status: '', startDate: '', endDate: '', employeeId: '' })
const pagination = reactive({ current: 1, size: 20, total: 0, ...getSortParams() })
const selectedRows = ref([])
const printVisible = ref(false)
const printBillId = ref(null)
// 通用搜索栏「单据状态」下拉选项（沿单据状态机取值）
const statusOptions = [
  { label: '草稿', value: '草稿' },
  { label: '待审核', value: '待审核' },
  { label: '已生效', value: '已生效' },
  { label: '已关闭', value: '已关闭' },
  { label: '已取消', value: '已取消' }
]

// ============ 筛选条件自定义（2026-10-14 库存模块推广批次） ============
// 条件候选 = 标准维度（状态/入库日期/经办人，std 标记 → 值走页面 search 通道，与统计卡筛选互通）
//          + 本单表头字段（f_<key> 参数化，后端白名单翻译）
//          + 单据自定义字段（设置中心勾「可搜索」，沿用 customSearch 既有通道）。
// 键与「其他入库单明细报表」引擎筛选键同键 → 汇总/明细两种模式一套条件参数。
// std 条件 = 现状默认勾选：没保存过筛选设置时查询区与改版前完全一致（守则 2.7.2-6）。
const filterDefs = computed(() => [
  ...stdDefs({
    status: { options: statusOptions },
    date: { key: 'receiptDate', label: '入库日期' },
    employee: { key: 'handlerName', label: '经办人' }
  }),
  fld('text', 'receiptNo', '入库单号'),
  fld('text', 'receiptType', '入库类型'),
  fld('select', 'currencyCode', '币种', 'currency'),
  fld('text', 'sourceBillNo', '来源单号'),
  fld('text', 'taxCalcMethod', '计税方式'),
  fld('text', 'remark', '备注'),
  fld('text', 'approvedBy', '审核人'),
  fld('numberRange', 'totalAmount', '入库总额'),
  fld('numberRange', 'discountAmount', '折扣额'),
  fld('numberRange', 'taxAmount', '税额'),
  fld('numberRange', 'netAmount', '价税合计'),
  fld('numberRange', 'exchangeRate', '汇率'),
  ...cfDefs(cf)
])

// 明细模式专属筛选条件（来源 = 明细报表 /columns 的 filters 元数据，键与引擎同键；
// 默认勾选 = 现状三件套：状态/日期/经办人，明细行字段默认不勾）
const detailFiltersMeta = ref([])
const STD_BY_KEY = { status: 'status', receiptDate: 'date', handlerName: 'employee' }
const detailFilterDefs = computed(() => detailFilterDefsOf(detailFiltersMeta.value, STD_BY_KEY))

/** 自定义条件参数（由通用壳经 filter-params 下发）：params 汇总用 / detailParams 明细用 / customSearch 自定义字段 */
const headerFilterState = ref({ params: {}, customSearch: undefined, detailParams: {} })
function onHeaderFilterParams(payload) {
  headerFilterState.value = payload || { params: {}, customSearch: undefined, detailParams: {} }
}
// 统计卡片高亮（点卡片筛选列表时由 onStatClick 控制）
const activeStatKey = ref('')

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

const warehouseOptions = ref([])

// 顶部统计卡片：数据来自后端 /api/inventory/other-receipts/dashboard（与列表同数据权限口径）
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
  { key: 'month', label: '本月入库单', value: 0, type: 'primary', icon: 'Document', filter: monthRange() },
  { key: 'draft', label: '草稿', value: 0, type: 'warning', icon: 'EditPen', filter: { status: '草稿' } },
  { key: 'pending', label: '待审核', value: 0, type: 'info', icon: 'Clock', filter: { status: '待审核' } },
  { key: 'approved', label: '已生效', value: 0, type: 'success', icon: 'CircleCheck', filter: { status: '已生效' } }
])

async function loadStats() {
  try {
    const d = (await getOtherReceiptDashboard()) || {}
    stats.value = [
      { key: 'month', label: '本月入库单', value: d.monthCount ?? 0, type: 'primary', icon: 'Document', filter: monthRange() },
      { key: 'draft', label: '草稿', value: d.draftCount ?? 0, type: 'warning', icon: 'EditPen', filter: { status: '草稿' } },
      { key: 'pending', label: '待审核', value: d.pendingCount ?? 0, type: 'info', icon: 'Clock', filter: { status: '待审核' } },
      { key: 'approved', label: '已生效', value: d.approvedCount ?? 0, type: 'success', icon: 'CircleCheck', filter: { status: '已生效' } }
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

const headerCellStyle = {
  background: 'rgba(99, 102, 241, 0.04)',
  color: '#1f2937',
  fontWeight: 600,
  borderBottom: '2px solid #e0e7ff'
}

const getWarehouseName = (warehouseId) => {
  const warehouse = warehouseOptions.value.find(w => w.value === warehouseId)
  return warehouse ? warehouse.label : '-'
}

async function loadDropdownOptions() {
  try {
    const whList = await listAllWarehouse()
    warehouseOptions.value = (Array.isArray(whList) ? whList : (whList?.records || [])).map(w => ({ value: w.stockId, label: w.stockName }))
  } catch (e) { console.warn('加载仓库失败', e) }
}

function statusTagType(s) {
  return { '草稿': 'info', '已生效': 'success', '已关闭': 'warning', '已取消': 'danger' }[s] || 'info'
}

function statusDotClass(s) {
  return { '草稿': 'dot-info', '已生效': 'dot-success', '已关闭': 'dot-warning', '已取消': 'dot-danger' }[s] || 'dot-info'
}

function handleSelectionChange(rows) {
  selectedRows.value = rows
}

function handlePrint(row) {
  printBillId.value = row.receiptId
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

// 通用搜索栏（状态/日期/经办人）变更：先把值写回本页 search
// 注：BillSearchBar 变更后会紧接着抛 search 事件（壳已转发 → handleSearch），这里不重复发起查询
function onFilterChange(patch) {
  Object.assign(search, patch || {})
}

// 列表查询参数（列表加载与导出共用同一套，避免两处口径漂移）
function buildQueryParams() {
  return {
    ...search,
    ...getSortParams(),
    // 筛选条件自定义（2026-10-14）：f_* 表头字段过滤参数由通用壳统一下发
    ...(headerFilterState.value.params || {}),
    // 单据自定义字段搜索：沿用既有 customSearch 通道（值由通用壳从筛选条件统一打包）
    ...(headerFilterState.value.customSearch ? { customSearch: headerFilterState.value.customSearch } : {})
  }
}

async function fetchData() {
  loading.value = true
  try {
    const params = { current: pagination.current, size: pagination.size, ...buildQueryParams() }
    const res = await listOtherReceipt(params)
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (e) { ElMessage.error(e.message) }
  finally { loading.value = false }
}

function handleSearch() { pagination.current = 1; fetchData() }
function handleReset() {
  // 重置 = 回到未筛选状态：关键词 + 通用搜索栏全部维度一并清空
  // 自定义筛选条件（f_*/自定义字段）的值由通用壳在重置时统一清空并下发空参数
  Object.assign(search, { keyword: '', status: '', startDate: '', endDate: '', employeeId: '' })
  activeStatKey.value = ''
  pagination.current = 1
  fetchData()
}

function handleAdd() {
  router.push('/inventory/other-receipts/edit')
}

function handleEdit(row) {
  router.push(`/inventory/other-receipts/edit/${row.receiptId}`)
}

async function handleApprove(row) {
  try {
    await ElMessageBox.confirm(`${tt("确认审核入库单 ")}${row.receiptNo}${tt("? 审核后将增加库存")}`, tt('提示'), { type: 'warning' })
    if (row.status === '草稿') {
      await submitOtherReceipt(row.receiptId)
    }
    await approveOtherReceipt(row.receiptId)
    ElMessage.success(tt('审核成功'))
    fetchData()
  } catch (e) {
    if (e !== 'cancel') {
      // 错误已由拦截器提示
    }
  }
}

async function handleClose(row) {
  try {
    const { value } = await ElMessageBox.prompt(tt('请输入关闭原因'), tt('关闭'), { type: 'warning' })
    await closeOtherReceipt(row.receiptId, value)
    ElMessage.success(tt('关闭成功'))
    fetchData()
  } catch (e) {
    if (e !== 'cancel') {
      // 错误已由拦截器提示
    }
  }
}

async function handleCancel(row) {
  try {
    const { value } = await ElMessageBox.prompt(tt('请输入取消原因'), tt('取消'), { type: 'warning' })
    await cancelOtherReceipt(row.receiptId, value)
    ElMessage.success(tt('取消成功'))
    fetchData()
  } catch (e) {
    if (e !== 'cancel') {
      // 错误已由拦截器提示
    }
  }
}

async function loadTemplateFields() {
  try {
    const resp = await fetch(`/api/export/template/otherReceipt/fields`, { headers: { Authorization: `Bearer ${localStorage.getItem('token') || ''}` } })
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
    await downloadTemplateUtil('otherReceipt', checkedOptionalFields.value)
    ElMessage.success(tt('自定义模板下载成功'))
  } catch (e) {
    ElMessage.error(tt('自定义模板下载失败'))
  } finally {
    downloadingCustomTemplate.value = false
  }
}

// 导出（与其它单据一致：表头 + 物料明细，含表头/明细自定义字段）
async function handleExport() {
  try {
    // 页面上看到什么列，导出就带什么列；行 = 当前筛选条件下的全部
    await exportListAsSeen({
      entityName: 'otherReceipt',
      columns: visibleSortedColumns.value,
      fetchPage: listOtherReceipt,
      params: buildQueryParams(),
      idField: 'receiptId'
    })
    ElMessage.success(tt('导出成功'))
  } catch (e) {
    // 失败原因已由 exportUtil 统一提示
  }
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
    await downloadTemplateUtil('otherReceipt')
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
    const result = await importData('otherReceipt', selectedImportFile.value)
    importResult.value = result
    if (result.failCount === 0) {
      ElMessage.success(`${tt("导入成功：共 ")}${result.successCount}${tt(" 条记录")}`)
      importDialogVisible.value = false
      fetchData()
    } else {
      ElMessage.warning(`${tt("导入完成：成功 ")}${result.successCount}${tt(" 条，失败 ")}${result.failCount}${tt(" 条")}`)
    }
  } catch (err) {
    ElMessage.error(tt('导入失败：') + (err.message || tt('未知错误')))
  } finally {
    importing.value = false
  }
}

onMounted(async () => {
  loadDropdownOptions()
  cf.load()
  loadStats()
  fetchData()
  // 明细模式专属筛选条件元数据（复用其他入库单明细报表 /columns 同一次元数据；失败静默：仅筛选设置里少明细条件）
  try {
    const res = await otherReceiptDetailApi.getColumns()
    detailFiltersMeta.value = (res && res.filters) || []
  } catch (e) { /* 静默降级 */ }
})
useAutoRefresh(() => { async () => {
  loadDropdownOptions()
  fetchData()
} })
</script>

<style scoped lang="scss">
.other-receipt-list-page {
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

/* 状态页签样式已删除：状态改为通用搜索栏的「单据状态」下拉（filters 声明 'status'，壳自动隐藏页签） */

.content-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.search-bar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  flex-wrap: wrap;
  padding: 16px 20px;
  border-bottom: 1px solid #f3f4f6;
  gap: 8px;

  .search-input {
    width: 220px;
    :deep(.el-input__wrapper) {
      border-radius: 8px;
      box-shadow: 0 0 0 1px #e5e7eb inset;
      &:hover { box-shadow: 0 0 0 1px #c7d2fe inset; }
      &.is-focus { box-shadow: 0 0 0 1px #6366f1 inset; }
    }
  }
}

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
  cursor: pointer;

  .order-no-text {
    color: #6366f1;
    font-weight: 500;
    text-decoration: underline;
    text-underline-offset: 2px;
  }

  .link-icon {
    font-size: 12px;
    color: #9ca3af;
  }
}

.amount-text { color: #374151; font-variant-numeric: tabular-nums; font-weight: 600; }

.status-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  justify-content: center;

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

@media (max-width: 1200px) {
  .stat-cards { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 768px) {
  .other-receipt-list-page { padding: 12px; }
  .stat-cards { grid-template-columns: 1fr; }
  .search-bar {
    flex-direction: column;
    align-items: stretch;
  }
}

.import-dialog-content {
  padding: 4px 0;
}
.import-step {
  margin-bottom: 8px;
}
.step-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 8px;
}
.step-desc {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 6px;
  line-height: 1.6;
}
.import-uploader {
  margin-top: 8px;
}
.import-result {
  margin-top: 16px;
}
.result-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 12px;
}
.import-error-alert {
  margin-top: 12px;
}
.error-list {
  max-height: 200px;
  overflow-y: auto;
  margin-top: 8px;
}
.error-item {
  padding: 4px 8px;
  background: #fef0f0;
  border-radius: 3px;
  margin-bottom: 4px;
  font-size: 13px;
  color: #f56c6c;
}
.error-more {
  padding: 4px 8px;
  font-size: 12px;
  color: #909399;
  text-align: center;
}
</style>