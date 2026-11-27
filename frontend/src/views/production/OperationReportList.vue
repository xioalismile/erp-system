<template>
  <!-- 通用列表壳（页头/统计卡/搜索条/表格外壳/分页/打印/列设置）+ 声明式筛选维度
       注：本页不声明 'customer'/'supplier' 维度 —— 工序汇报单不涉及客户与供应商 -->
  <BillListPage
    page-class="operation-report-page"
    back-path="/production"
    :title="$tt('作业汇报')"
    badge="OPERATION REPORT"
    sub-title="记录生产工序作业汇报 · 追踪生产进度与质量"
    :stats="stats"
    :active-stat-key="activeStatKey"
    search-placeholder="输入汇报单号/工单号/工序名称/报工员工搜索"
    keyword-label="单据编号"
    :filters="['status', 'date', 'employee']"
    :status-options="statusOptions"
    :filter-defs="filterDefs"
    :detail-filter-defs="detailFilterDefs"
    :filter-model="search"
    employee-label="报工员工"
    date-label="报工日期"
    :page-sizes="[10, 20, 50, 100]"
    :table-data="tableData"
    :loading="loading"
    :pagination="pagination"
    storage-key="OperationReportList"
    :columns="columns"
    :table-key="configVersion"
    bill-type="OPERATION_REPORT"
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
      <!-- 页头按钮统一走通用组件（2026-09-19）：本页无菜单码，perm-prefix 传空串=不做权限判定 -->
      <BillHeaderActions
        perm-prefix=""
        :add-text="$tt('新增汇报单')"
        :show-import="false"
        :print-disabled="!sel.length"
        @add="onAdd"
        @print="handleBatchPrint(sel)"
        @export="handleExport"
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
            :fixed="getColFixed(col.prop, col.fixed)"
          >
            <template v-if="col.type" #default="{ row }">
              <div v-if="col.type === 'reportNo'" class="order-no-cell">
                <span class="order-no-text" @click="onDetail(row)">{{ row.reportNo }}</span>
                <el-icon class="link-icon"><Link /></el-icon>
              </div>
              <span v-else-if="col.type === 'qualified'" class="amount-text success">{{ row.qualifiedQuantity || 0 }}</span>
              <span v-else-if="col.type === 'rejected'" class="amount-text danger">{{ row.rejectedQuantity || 0 }}</span>
              <div v-else-if="col.type === 'status'" class="status-cell">
                <span :class="['status-dot', statusDotClass(row.status)]"></span>
                <el-tag size="small" :type="statusType(row.status)" effect="light" class="status-tag">{{ $dt(row.status) }}</el-tag>
              </div>
            </template>
          </el-table-column>
        </template>
        <AuditColumns />
  </BillListPage>
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listOperationReport,
  deleteOperationReport,
  submitOperationReport, approveOperationReport,
  getOperationReportDashboard, operationReportDetailApi
} from '@/api/production/operationReport'
import { exportListAsSeen, columnsToExportFields } from '@/utils/exportUtil'
import { confirmSubmit, submitSuccessMessage } from '@/utils/submit-confirm'
import BillListPage from '@/components/bill/BillListPage.vue'
import BillHeaderActions from '@/components/bill/BillHeaderActions.vue'
import AuditColumns from '@/components/AuditColumns.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
// 筛选条件自定义共享工厂（2026-10-14 全面推广）：候选集拼装统一在工厂，页面只声明本单键位差异
// （工序汇报已登记单据自定义字段实体 → 可搜索字段并入条件候选，查询参数透传 customSearch）
import { stdDefs, fld, detailFilterDefsOf, cfDefs } from '@/config/billFilterDefs'
import { useCustomFieldSearch } from '@/composables/useCustomFieldSearch'
import { Plus, Download, Link } from '@element-plus/icons-vue'

import { useTableSort } from '@/composables/useTableSort'
import { tt } from '@/i18n'
const { handleSortChange, getSortParams } = useTableSort()


/**
 * 批量操作声明（2026-09-20）：UI/确认/执行/结果面板全部由通用壳承担，这里只声明"怎么执行"。
 * 命名口径全系统统一：批量提交 / 批量审批 / 批量删除 / 批量导出。
 * 注：本单据在库里没有独立按钮权限码（生产模块只有 workorders / receipts / returns / supplements /
 * assemble / disassemble 等），**不声明 perm**——臆造不存在的码会被 v-permission 判定无权限从而隐藏按钮。
 */
const batchActions = [
  {
    key: 'submit',
    label: tt('批量提交'),
    type: 'primary',
    exec: (row) => submitOperationReport(row.id),
    skipReason: (row) => (row.status && row.status !== '草稿'
      ? tt('当前状态：') + row.status + tt('，不是草稿，无需提交') : '')
  },
  {
    key: 'approve',
    label: tt('批量审批'),
    exec: (row) => approveOperationReport(row.id),
    skipReason: (row) => (row.status !== '待审核'
      ? tt('当前状态：') + (row.status || '-') + tt('，不是待审核，无需审批') : '')
  },
  {
    key: 'delete',
    label: tt('批量删除'),
    type: 'danger',
    exec: (row) => deleteOperationReport(row.id),
    disabledReason: (rows) => (rows.some(r => r.status && r.status !== '草稿' && r.status !== '已驳回' && r.status !== '已取消')
      ? tt('非草稿不能删除，请先重置草稿') : '')
  },
  {
    key: 'export',
    label: tt('批量导出'),
    entity: 'operationReport',
    idOf: (row) => row.id,
    // 勾选导出同样按"页面当前可见列"（2026-09-24 全站口径）
    fields: () => columnsToExportFields(visibleSortedColumns.value)
  }
]

const onSortChange = (e) => { handleSortChange(e); fetchData() }
// 列定义（用于列设置抽屉+动态渲染）= 表格全部数据列；type 标记单元格渲染分支，纯文本列不加 type
const columns = [
  { prop: 'reportNo', label: '汇报单号', minWidth: 150, sortable: true, type: 'reportNo' },
  { prop: 'reportDate', label: '汇报日期', width: 120, sortable: true },
  { prop: 'productionOrderNo', label: '工单号', minWidth: 140, sortable: true },
  { prop: 'processNo', label: '工序号', width: 90, sortable: true },
  { prop: 'operationName', label: '工序名称', minWidth: 120, sortable: true },
  { prop: 'workCenterName', label: '工作中心', minWidth: 120, sortable: true },
  { prop: 'employeeName', label: '操作工', minWidth: 100, sortable: true },
  { prop: 'qualifiedQuantity', label: '合格数量', width: 100, align: 'right', sortable: true, type: 'qualified' },
  { prop: 'rejectedQuantity', label: '报废数量', width: 100, align: 'right', sortable: true, type: 'rejected' },
  { prop: 'workHours', label: '实际工时', width: 100, align: 'right', sortable: true },
  { prop: 'status', label: '状态', width: 110, sortable: true, type: 'status' }
]
const { columnSettingVisible, openColumnSetting, isColVisible, getColFixed, configVersion, sortedColumns } = useColumnSetting('OperationReportList', columns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))

const router = useRouter()

const loading = ref(false)
const tableData = ref([])
const selectedRows = ref([])
// 统一入参名（docs/knowledge-base.md 6.1）：keyword / status / startDate / endDate / employeeId（报工员工）
// （本页不涉及客户 / 供应商维度，故不声明这两个筛选项）
const search = reactive({ keyword: '', status: '', startDate: '', endDate: '', employeeId: '' })
const cf = useCustomFieldSearch('operation_report')
const pagination = reactive({ current: 1, size: 20, total: 0, ...getSortParams() })
// 通用搜索栏「单据状态」下拉选项 = 后端状态机真实取值（草稿 / 待审核 / 已生效 / 已作废）
const statusOptions = [
  { label: '草稿', value: '草稿' },
  { label: '待审核', value: '待审核' },
  { label: '已生效', value: '已生效' },
  { label: '已作废', value: '已作废' }
]

// ============ 筛选条件自定义（2026-10-14 全面推广 · 销售订单试点标准） ============
// 条件候选 = 标准维度（状态/报工日期/报工员工，std 标记 → 值走页面 search 通道，与统计卡互通）
//          + 本单表头字段（f_<key> 参数化，后端白名单翻译）。
// 本单已登记单据自定义字段实体 → 可搜索字段并入条件候选，查询参数透传 customSearch。
// 键与「工序汇报单明细报表」引擎筛选键同键 → 汇总/明细两种模式一套条件参数。
const filterDefs = computed(() => [
  ...stdDefs({
    status: { options: statusOptions },
    date: { key: 'reportDate', label: '报工日期' },
    employee: { key: 'employeeName', label: '报工员工' }
  }),
  fld('text', 'reportNo', '汇报单号'),
  fld('text', 'productionOrderNo', '工单号'),
  fld('text', 'processNo', '工序号'),
  fld('text', 'operationName', '工序名称'),
  fld('text', 'workCenterName', '工作中心'),
  fld('text', 'reportType', '汇报类型'),
  fld('text', 'shiftType', '班次'),
  fld('text', 'sourceBillType', '来源单据类型'),
  fld('text', 'sourceBillNo', '来源单号'),
  fld('text', 'approvedBy', '审核人'),
  fld('text', 'remark', '备注'),
  fld('numberRange', 'totalQuantity', '报工数量'),
  fld('numberRange', 'qualifiedQuantity', '合格数量'),
  fld('numberRange', 'rejectedQuantity', '报废数量'),
  fld('numberRange', 'workHours', '实际工时'),
  fld('numberRange', 'overtimeHours', '加班工时'),
  ...cfDefs(cf)
])

// 明细模式专属筛选条件（来源 = 明细报表 /columns 的 filters 元数据，键与引擎同键；
// STD_BY_KEY 声明引擎筛选键 → 标准维度（默认勾选=现状、值走页面 search 通道））
const detailFiltersMeta = ref([])
const STD_BY_KEY = { status: 'status', reportDate: 'date', employeeName: 'employee' }
const detailFilterDefs = computed(() => detailFilterDefsOf(detailFiltersMeta.value, STD_BY_KEY))

/** 自定义条件参数（由通用壳经 filter-params 下发）：params 汇总用 / detailParams 明细用 */
const headerFilterState = ref({ params: {}, customSearch: undefined, detailParams: {} })
function onHeaderFilterParams(payload) {
  headerFilterState.value = payload || { params: {}, customSearch: undefined, detailParams: {} }
}
// 统计卡片高亮（点卡片筛选列表时由 onStatClick 控制）
const activeStatKey = ref('')

const printVisible = ref(false)
const printBillId = ref(null)

// 页头「打印」：一次只能打印一张（与销售订单口径一致）
function handleBatchPrint(rows) {
  const list = (rows && rows.length ? rows : selectedRows.value) || []
  if (!list.length) { ElMessage.warning(tt('请先勾选要打印的单据')); return }
  if (list.length > 1) { ElMessage.warning(tt('一次只能打印一张单据，请仅勾选一行')); return }
  printBillId.value = list[0].reportId
  printVisible.value = true
}

// 顶部统计卡片：数据来自后端 /api/production/operation-report/dashboard（与列表同数据权限口径）
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
  { key: 'month', label: '本月报工', value: 0, type: 'primary', icon: 'Document', filter: monthRange() },
  { key: 'draft', label: '草稿', value: 0, type: 'warning', icon: 'EditPen', filter: { status: '草稿' } },
  { key: 'pending', label: '待审核', value: 0, type: 'info', icon: 'Clock', filter: { status: '待审核' } },
  { key: 'approved', label: '已生效', value: 0, type: 'success', icon: 'CircleCheck', filter: { status: '已生效' } }
])

async function loadStats() {
  try {
    const d = (await getOperationReportDashboard()) || {}
    stats.value = [
      { key: 'month', label: '本月报工', value: d.monthCount ?? 0, type: 'primary', icon: 'Document', filter: monthRange() },
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

// 通用搜索栏（状态 / 日期 / 报工员工）变更：先把值写回本页 search
// 注：BillSearchBar 变更后会紧接着抛 search 事件（壳已转发 → handleSearch），这里不重复发起查询
function onFilterChange(patch) {
  Object.assign(search, patch || {})
}

const statusType = (s) => ({
  '草稿': 'info', '待审核': 'warning', '已生效': 'success', '已作废': 'danger'
}[s] || 'info')

function statusDotClass(s) {
  return {
    '草稿': 'dot-info',
    '待审核': 'dot-warning',
    '已生效': 'dot-success',
    '已作废': 'dot-danger'
  }[s] || 'dot-info'
}

/**
 * 列表与导出**共用**的查询条件（2026-09-24 全站口径）：
 * 导出必须与列表用同一套条件，抽成一个函数避免两处各写一份导致口径漂移
 * （用户要求"页面上看到什么列，导出就带什么列；导出行 = 当前筛选条件下的全部"）。
 */
function buildQueryParams() {
  return {
    ...search,
    ...getSortParams(),
    // 筛选条件自定义（2026-10-14）：f_* 表头字段过滤参数由通用壳统一下发
    ...(headerFilterState.value.params || {}),
    // 单据自定义字段（勾选「可搜索」）→ customSearch 透传后端
    ...(headerFilterState.value.customSearch ? { customSearch: headerFilterState.value.customSearch } : {})
  }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await listOperationReport({ current: pagination.current, size: pagination.size, ...buildQueryParams() })
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (e) { /* 拦截器已提示 */ } finally {
    loading.value = false
  }
}

function handleSearch() { pagination.current = 1; fetchData() }

function handleReset() {
  // 重置 = 回到未筛选状态：关键词 + 通用搜索栏全部维度一并清空
  Object.assign(search, { keyword: '', status: '', startDate: '', endDate: '', employeeId: '' })
  activeStatKey.value = ''   // 同时取消统计卡片的高亮
  pagination.current = 1
  fetchData()
}

async function handleExport() {
  try {
    // 页面上看到什么列，导出就带什么列；导出行 = 当前筛选条件下的全部
    await exportListAsSeen({
      entityName: 'operationReport',
      columns: visibleSortedColumns.value,
      fetchPage: listOperationReport,
      params: buildQueryParams(),
      idField: 'reportId'
    })
    ElMessage.success(tt('导出成功'))
  } catch (e) {
    // 失败原因由 exportUtil 统一提示
  }
}

function onAdd() {
  router.push('/production/operation-reports/edit')
}

function onEdit(row) {
  router.push(`/production/operation-reports/edit/${row.id}`)
}

function onDetail(row) {
  router.push(`/production/operation-reports/edit/${row.id}`)
}

async function onSubmit(row) {
  try {
    const needApproval = await confirmSubmit({ billType: 'OPERATION_REPORT', billLabel: '汇报单', billNo: row.reportNo })
    await submitOperationReport(row.id)
    ElMessage.success(submitSuccessMessage(needApproval))
    fetchData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('提交失败'))
  }
}

async function onApprove(row) {
  await ElMessageBox.confirm(`${tt("确认审核汇报单 ")}${row.reportNo}？`, tt('确认'), { type: 'warning' })
  try {
    await approveOperationReport(row.id)
    ElMessage.success(tt('已生效'))
    fetchData()
  } catch (e) { /* 拦截器已提示 */ }
}

async function onDelete(row) {
  await ElMessageBox.confirm(`${tt("确认删除汇报单 ")}${row.reportNo}？`, tt('确认'), { type: 'warning' })
  try {
    await deleteOperationReport(row.id)
    ElMessage.success(tt('删除成功'))
    fetchData()
  } catch (e) { /* 拦截器已提示 */ }
}

onMounted(() => {
  cf.load()
  loadStats()
  fetchData()
  // 明细模式专属筛选条件元数据（明细报表 /columns 的 filters；失败静默：明细条件集为空时明细模式沿用汇总条件集）
  operationReportDetailApi.getColumns().then(res => {
    detailFiltersMeta.value = (res && res.filters) || []
  }).catch(() => { /* 静默降级 */ })
})
useAutoRefresh(() => { fetchData(); loadStats() })
</script>

<style scoped lang="scss">
.operation-report-page {
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

.status-tabs {
  display: flex;
  gap: 4px;

  .status-tab {
    padding: 8px 16px;
    font-size: 13px;
    color: #6b7280;
    cursor: pointer;
    border-radius: 8px;
    display: flex;
    align-items: center;
    gap: 6px;
    transition: all .2s;

    &:hover { color: #6366f1; background: #f5f3ff; }

    &.active {
      color: #6366f1;
      background: linear-gradient(135deg, #eef2ff, #f5f3ff);
      font-weight: 600;
    }

    .tab-count {
      font-size: 11px;
      background: #e5e7eb;
      color: #6b7280;
      padding: 1px 6px;
      border-radius: 10px;
    }

    &.active .tab-count {
      background: #6366f1;
      color: #fff;
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

.amount-text {
  font-weight: 600;
  font-family: 'SF Mono', Consolas, monospace;
  &.success { color: #10b981; }
  &.danger { color: #ef4444; }
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
  .operation-report-page { padding: 12px; }
  .stat-cards { grid-template-columns: 1fr; }
  .search-bar {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }
  .status-tabs { flex-wrap: wrap; }
}
</style>
