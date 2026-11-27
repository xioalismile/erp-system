<template>
  <!-- 通用列表壳（页头/统计卡/搜索条/表格外壳/分页/打印/列设置）+ 声明式筛选维度
       注：本页不声明 'customer' / 'supplier' 维度 —— 补料单不涉及客户/供应商 -->
  <BillListPage
    page-class="production-supplement-page"
    back-path="/production"
    :title="$tt('生产补料单')"
    badge="SUPPLEMENT"
    sub-title="管理生产过程中的物料补料 · 确保生产顺利进行"
    :stats="stats"
    :active-stat-key="activeStatKey"
    search-placeholder="输入补料单号搜索"
    keyword-label="单据编号"
    :filters="['status', 'date', 'employee']"
    :status-options="statusOptions"
    :filter-defs="filterDefs"
    :detail-filter-defs="detailFilterDefs"
    :filter-model="search"
    date-label="补料日期"
    employee-label="领料人"
    :table-data="list"
    :loading="loading"
    :pagination="page"
    :page-sizes="[10, 20, 50, 100]"
    storage-key="ProductionSupplementList"
    :columns="columns"
    :table-key="configVersion"
    bill-type="MATERIAL_REQUISITION"
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
        perm-prefix="production:supplements"
        :add-text="$tt('新增补料单')"
        :print-disabled="!sel.length"
        @add="openCreate"
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
        :fixed="getColFixed(col.prop, col.fixed)"
      >
        <template v-if="col.type" #default="{ row }">
          <div v-if="col.type === 'billNo'" class="order-no-cell">
            <span class="order-no-text" @click="openEdit(row)">{{ row.requisitionNo }}</span>
            <el-icon class="link-icon"><Link /></el-icon>
          </div>
          <span v-else-if="col.type === 'amount2'" class="amount-text">{{ Number(row[col.prop] || 0).toFixed(2) }}</span>
          <div v-else-if="col.type === 'status'" class="status-cell">
            <span :class="['status-dot', statusDotClass(row.status)]"></span>
            <el-tag size="small" :type="statusType(row.status)" effect="light" class="status-tag">{{ $dt(row.status) }}</el-tag>
          </div>
          <span v-else-if="col.type === 'warehouse'">{{ getWarehouseName(row.warehouseId) }}</span>
        </template>
      </el-table-column>
    </template>
    <AuditColumns />
  </BillListPage>

  <BillImportDialog v-model="importVisible" entity-name="materialRequisition" bill-type="SUPPLEMENT" title="生产补料单导入" @done="loadData" />
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listMaterialRequisition, getMaterialRequisitionDashboard,
  submitMaterialRequisition, deleteMaterialRequisition, approveMaterialRequisition,
  closeMaterialRequisition, cancelMaterialRequisition,
  materialSupplementDetailApi
} from '@/api/inventory/materialRequisition'
import { exportListAsSeen, columnsToExportFields } from '@/utils/exportUtil'
import BillListPage from '@/components/bill/BillListPage.vue'
import BillHeaderActions from '@/components/bill/BillHeaderActions.vue'
import BillImportDialog from '@/components/bill/BillImportDialog.vue'
import AuditColumns from '@/components/AuditColumns.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { useCustomFieldSearch } from '@/composables/useCustomFieldSearch'
// 筛选条件自定义共享工厂（2026-10-14 全面推广）：候选集拼装统一在工厂，页面只声明本单键位差异
import { stdDefs, fld, cfDefs, detailFilterDefsOf } from '@/config/billFilterDefs'
import { Plus, Download, Link, Document, EditPen, Clock, CircleCheck } from '@element-plus/icons-vue'

import { useTableSort } from '@/composables/useTableSort'
import { listAllWarehouse } from '@/api/basedata/warehouse'
import { tt } from '@/i18n'
const { handleSortChange, getSortParams } = useTableSort()

// 仓库名称映射：列表只返回仓库主键，这里加载仓库档案按 id 映射成名称，避免表格里显示数字主键
const warehouseOptions = ref([])
const getWarehouseName = (warehouseId) => {
  if (warehouseId === null || warehouseId === undefined || warehouseId === '') return ''
  const hit = warehouseOptions.value.find(w => w.stockId === warehouseId)
  return hit ? hit.stockName : ''
}
async function loadWarehouseOptions() {
  try {
    const whList = await listAllWarehouse()
    warehouseOptions.value = Array.isArray(whList) ? whList : (whList?.records || [])
  } catch (e) { console.warn('加载仓库失败', e) }
}


/**
 * 批量操作声明（2026-09-20）：UI/确认/执行/结果面板全部由通用壳承担，这里只声明"怎么执行"。
 * 命名口径全系统统一：批量提交 / 批量审批 / 批量删除 / 批量导出。
 * 补料单与领料单/退料单共用同一套后端接口（/inventory/material-requisitions）。
 */
const batchActions = [
  {
    key: 'submit',
    label: tt('批量提交'),
    perm: 'production:supplements:submit',
    type: 'primary',
    exec: (row) => submitMaterialRequisition(row.requisitionId),
    skipReason: (row) => (row.status && row.status !== '草稿'
      ? tt('当前状态：') + row.status + tt('，不是草稿，无需提交') : '')
  },
  {
    key: 'approve',
    label: tt('批量审批'),
    perm: 'production:supplements:audit',
    exec: (row) => approveMaterialRequisition(row.requisitionId),
    skipReason: (row) => (row.status !== '待审核'
      ? tt('当前状态：') + (row.status || '-') + tt('，不是待审核，无需审批') : '')
  },
  {
    key: 'delete',
    label: tt('批量删除'),
    type: 'danger',
    perm: 'production:supplements:delete',
    exec: (row) => deleteMaterialRequisition(row.requisitionId),
    disabledReason: (rows) => (rows.some(r => r.status && r.status !== '草稿' && r.status !== '已驳回' && r.status !== '已取消')
      ? tt('非草稿不能删除，请先重置草稿') : '')
  },
  {
    key: 'export',
    label: tt('批量导出'),
    perm: 'production:supplements:export',
    entity: 'materialRequisition',
    idOf: (row) => row.requisitionId,
    // 勾选导出同样按"页面当前可见列"（2026-09-24 全站口径）
    fields: () => columnsToExportFields(visibleSortedColumns.value)
  }
]

const onSortChange = (e) => { handleSortChange(e); loadData() }
const router = useRouter()

// 列定义（用于列设置抽屉+动态渲染）：type 标记单元格渲染分支（列与顺序与改造前一致）
// billNo 单号链接、amount2 金额两位小数、status 状态标签，其余纯文本
const columns = [
  { prop: 'requisitionNo', label: '补料单号', minWidth: 160, sortable: true, type: 'billNo' },
  { prop: 'requisitionDate', label: '补料日期', width: 120, sortable: true },
  { prop: 'workOrderNo', label: '工单号', width: 140, sortable: true },
  { prop: 'warehouseId', label: '仓库', width: 140, sortable: true, type: 'warehouse' },
  { prop: 'totalAmount', label: '合计金额', width: 120, align: 'right', sortable: true, type: 'amount2' },
  { prop: 'status', label: '状态', width: 110, sortable: true, type: 'status' },
  { prop: 'createdBy', label: '创建人', width: 100, sortable: true },
  { prop: 'createdAt', label: '创建时间', width: 160, sortable: true }
]
const { columnSettingVisible, isColVisible, getColFixed, configVersion, sortedColumns } = useColumnSetting('ProductionSupplementList', columns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))

const loading = ref(false)
const list = ref([])
// 单据类型隔离：本页固定只显示「补料单」(bill_type=SUPPLEMENT)，领料(PICK)/退料(RETURN)走各自页面
const BILL_TYPE = 'SUPPLEMENT'
// 统一入参名（docs/knowledge-base.md 6.1）：keyword / status / startDate / endDate / employeeId（领料人）
const search = reactive({ keyword: '', status: '', startDate: '', endDate: '', employeeId: '' })
const page = reactive({ current: 1, size: 20, total: 0, ...getSortParams() })
const selectedRows = ref([])
// 通用搜索栏「单据状态」下拉选项（按后端状态机真实取值：草稿/待审核/已生效/已关闭/已取消/已作废）
const statusOptions = [
  { label: '草稿', value: '草稿' },
  { label: '待审核', value: '待审核' },
  { label: '已生效', value: '已生效' },
  { label: '已关闭', value: '已关闭' },
  { label: '已取消', value: '已取消' },
  { label: '已作废', value: '已作废' }
]

// ============ 筛选条件自定义（2026-10-14 全面推广 · 库存模块领料单同款） ============
// 条件候选 = 标准维度（状态/补料日期/领料人，std 标记 → 值走页面 search 通道，与统计卡互通）
//          + 本单表头字段（f_<key> 参数化，键与后端 materialRequisition 白名单一致）
//          + 单据自定义字段（设置中心勾「可搜索」，沿用 customSearch 既有通道）。
// 补料单与领料单/退料单共用同一实体与后端白名单注册（键一致，勿重复注册）；
// 键与「生产补料单明细报表」引擎筛选键同键 → 汇总/明细两种模式一套条件参数。
const cf = useCustomFieldSearch('material_requisition')
const filterDefs = computed(() => [
  ...stdDefs({
    status: { options: statusOptions },
    date: { key: 'requisitionDate', label: '补料日期' },
    employee: { key: 'employeeName', label: '领料人' }
  }),
  fld('text', 'requisitionNo', '补料单号'),
  fld('select', 'deptName', '领用部门', 'department'),
  fld('select', 'currencyCode', '币种', 'currency'),
  fld('text', 'workOrderNo', '工单号'),
  fld('text', 'sourceBillType', '来源单据类型'),
  fld('text', 'sourceBillNo', '来源单号'),
  fld('text', 'taxCalcMethod', '计税方式'),
  fld('text', 'remark', '备注'),
  fld('text', 'approvedBy', '审核人'),
  fld('numberRange', 'totalAmount', '合计金额'),
  fld('numberRange', 'exchangeRate', '汇率'),
  ...cfDefs(cf)
])

// 明细模式专属筛选条件（来源 = 明细报表 /columns 的 filters 元数据，键与引擎同键；
// STD_BY_KEY 声明引擎筛选键 → 标准维度（默认勾选=现状、值走页面 search 通道））
const detailFiltersMeta = ref([])
const STD_BY_KEY = { status: 'status', requisitionDate: 'date', employeeName: 'employee' }
const detailFilterDefs = computed(() => detailFilterDefsOf(detailFiltersMeta.value, STD_BY_KEY))

/** 自定义条件参数（由通用壳经 filter-params 下发）：params 汇总用 / detailParams 明细用 / customSearch 自定义字段 */
const headerFilterState = ref({ params: {}, customSearch: undefined, detailParams: {} })
function onHeaderFilterParams(payload) {
  headerFilterState.value = payload || { params: {}, customSearch: undefined, detailParams: {} }
}
// 统计卡片高亮（点卡片筛选列表时由 onStatClick 控制）
const activeStatKey = ref('')

const printVisible = ref(false)
const printBillId = ref(null)

// 页头「导入」：模板下载与上传导入统一由 BillImportDialog 处理
const importVisible = ref(false)
function handleImport() { importVisible.value = true }

// 页头「打印」：一次只能打印一张（与销售订单口径一致）
function handleBatchPrint(rows) {
  const list = (rows && rows.length ? rows : selectedRows.value) || []
  if (!list.length) { ElMessage.warning(tt('请先勾选要打印的单据')); return }
  if (list.length > 1) { ElMessage.warning(tt('一次只能打印一张单据，请仅勾选一行')); return }
  printBillId.value = list[0].requisitionId
  printVisible.value = true
}

// 顶部统计卡片：数据来自后端 /api/inventory/material-requisitions/dashboard（与列表同数据权限口径）
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
  { key: 'month', label: '本月补料', value: 0, type: 'primary', icon: 'Document', filter: monthRange() },
  { key: 'draft', label: '草稿', value: 0, type: 'warning', icon: 'EditPen', filter: { status: '草稿' } },
  { key: 'pending', label: '待审核', value: 0, type: 'info', icon: 'Clock', filter: { status: '待审核' } },
  { key: 'approved', label: '已生效', value: 0, type: 'success', icon: 'CircleCheck', filter: { status: '已生效' } }
])

async function loadStats() {
  try {
    // 卡片数字按单据类型统计（只统计补料单），与下方列表口径一致
    const d = (await getMaterialRequisitionDashboard({ billType: BILL_TYPE })) || {}
    stats.value = [
      { key: 'month', label: '本月补料', value: d.monthCount ?? 0, type: 'primary', icon: 'Document', filter: monthRange() },
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
  page.current = 1
  loadData()
}

const headerCellStyle = {
  background: 'rgba(99, 102, 241, 0.04)',
  color: '#1f2937',
  fontWeight: 600,
  borderBottom: '2px solid #e0e7ff'
}

const statusType = (s) => ({
  '草稿': 'info',
  '已生效': 'success',
  '已关闭': 'warning',
  '已取消': 'danger'
}[s] || 'info')

function statusDotClass(s) {
  return {
    '草稿': 'dot-info',
    '已生效': 'dot-success',
    '已关闭': 'dot-warning',
    '已取消': 'dot-danger'
  }[s] || 'dot-info'
}

// 通用搜索栏（状态/日期/领料人）变更：先把值写回本页 search
// 注：BillSearchBar 变更后会紧接着抛 search 事件（壳已转发 → handleSearch），这里不重复发起查询
function onFilterChange(patch) {
  Object.assign(search, patch || {})
}

function handleSearch() {
  page.current = 1
  loadData()
}

function handleReset() {
  // 重置 = 回到未筛选状态：关键词 + 通用搜索栏全部维度一并清空（单据类型固定 SUPPLEMENT，不参与重置）
  // 自定义筛选条件（f_*/自定义字段）的值由通用壳在重置时统一清空并下发空参数
  Object.assign(search, { keyword: '', status: '', startDate: '', endDate: '', employeeId: '' })
  activeStatKey.value = ''
  page.current = 1
  loadData()
}

/**
 * 列表与导出**共用**的查询条件（2026-09-24 全站口径）：
 * 导出必须与列表用同一套条件（含 billType 单据类型隔离），
 * 抽成一个函数避免两处各写一份导致口径漂移
 * （用户要求"页面上看到什么列，导出就带什么列；导出行 = 当前筛选条件下的全部"）。
 */
function buildQueryParams() {
  return {
    keyword: search.keyword,
    status: search.status,
    startDate: search.startDate,
    endDate: search.endDate,
    employeeId: search.employeeId,
    billType: BILL_TYPE,
    ...getSortParams(),
    // 筛选条件自定义（2026-10-14）：f_* 表头字段过滤参数由通用壳统一下发
    ...(headerFilterState.value.params || {}),
    // 单据自定义字段搜索：沿用既有 customSearch 通道（值由通用壳从筛选条件统一打包）
    ...(headerFilterState.value.customSearch ? { customSearch: headerFilterState.value.customSearch } : {})
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await listMaterialRequisition({ current: page.current, size: page.size, ...buildQueryParams() })
    list.value = res.records || []
    page.total = res.total || 0
  } finally {
    loading.value = false
  }
}

async function handleExport() {
  try {
    // 页面上看到什么列，导出就带什么列；导出行 = 当前筛选条件下的全部（含 billType=SUPPLEMENT 单据类型隔离）
    await exportListAsSeen({
      entityName: 'materialRequisition',
      columns: visibleSortedColumns.value,
      fetchPage: listMaterialRequisition,
      params: buildQueryParams(),
      idField: 'requisitionId'
    })
    ElMessage.success(tt('导出成功'))
  } catch (e) {
    // 失败原因由 exportUtil 统一提示
  }
}

const openCreate = () => {
  router.push('/production/supplements/edit')
}

const openEdit = (row) => {
  router.push('/production/supplements/edit/' + row.requisitionId)
}

const onApprove = async (row) => {
  try {
    await ElMessageBox.confirm(tt('审核补料单后将扣减库存。是否继续？'), tt('审核确认'), {
      confirmButtonText: tt('确定审核'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    const approver = localStorage.getItem('username') || 'system'
    await approveMaterialRequisition(row.requisitionId, approver)
    ElMessage.success(tt('审核成功'))
    loadData()
  } catch (e) { if (e !== 'cancel') { /* 业务错误已由拦截器处理 */ } }
}

const onClose = async (row) => {
  try {
    const { value } = await ElMessageBox.prompt(tt('请输入关闭原因（可选）'), tt('关闭补料单'), {
      confirmButtonText: tt('确定'), cancelButtonText: tt('取消'), inputType: 'textarea'
    })
    await closeMaterialRequisition(row.requisitionId, value || '')
    ElMessage.success(tt('已关闭'))
    loadData()
  } catch (e) { if (e !== 'cancel') { /* 业务错误已由拦截器处理 */ } }
}

const onCancel = async (row) => {
  try {
    const { value } = await ElMessageBox.prompt(tt('请输入取消原因（可选）'), tt('取消补料单'), {
      confirmButtonText: tt('确定'), cancelButtonText: tt('取消'), inputType: 'textarea'
    })
    await cancelMaterialRequisition(row.requisitionId, value || '')
    ElMessage.success(tt('已取消'))
    loadData()
  } catch (e) { if (e !== 'cancel') { /* 业务错误已由拦截器处理 */ } }
}

const onDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`${tt("确定删除补料单 ")}${row.requisitionNo}${tt(" 吗？")}`, tt('提示'), {
      confirmButtonText: tt('确定'), cancelButtonText: tt('取消'), type: 'warning'
    })
    await deleteMaterialRequisition(row.requisitionId)
    ElMessage.success(tt('删除成功'))
    loadData()
  } catch (e) { if (e !== 'cancel') { /* 业务错误已由拦截器处理 */ } }
}

onMounted(() => {
  loadWarehouseOptions()
  cf.load()
  loadStats()
  loadData()
  // 明细模式专属筛选条件元数据（复用生产补料单明细报表 /columns 同一次元数据；失败静默：仅筛选设置里少明细条件）
  materialSupplementDetailApi.getColumns().then(res => {
    detailFiltersMeta.value = (res && res.filters) || []
  }).catch(() => { /* 静默降级 */ })
})
useAutoRefresh(() => {
  loadData()
})
</script>

<style scoped>
.production-supplement-page {
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
  color: #1f2937;
  font-family: 'SF Mono', Consolas, monospace;
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
  .production-supplement-page { padding: 12px; }
  .stat-cards { grid-template-columns: 1fr; }
  .search-bar {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }
}
</style>
