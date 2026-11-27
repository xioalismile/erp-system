<template>
  <!-- 通用列表壳（页头/统计卡/搜索条/表格外壳/分页/列设置）
       筛选维度只声明「单据状态」：本页分页接口只接受 current/size/keyword/status/sortBy/sortOrder，
       没有日期/供应商入参 → 不声明 'date'/'supplier'，避免渲染出"点了没反应"的假筛选 -->
  <BillListPage
    page-class="subcontract-pick-page"
    back-path="/subcontract"
    :title="$tt('委外领料单')"
    badge="SUBCONTRACT PICK"
    sub-title="委外发料管理 · 实时库存扣减"
    create-path="/subcontract/picks/new"
    :stats="stats"
    :active-stat-key="activeStatKey"
    search-placeholder="输入领料单号/委外单号/供应商搜索"
    keyword-label="单据编号"
    :filters="['status']"
    :status-options="statusOptions"
    :filter-model="search"
    :filter-defs="filterDefs"
    :detail-filter-defs="detailFilterDefs"
    :table-data="tableData"
    :loading="loading"
    :pagination="pagination"
    storage-key="SubcontractPickList"
    bill-type="SUBCONTRACT_PICK"
    :columns="columns"
    :table-key="configVersion"
    :page-sizes="[20, 50, 100]"
    v-model:keyword="search.keyword"
    v-model:selected-rows="selectedRows"
    :batch-actions="batchActions"
    v-model:column-setting-visible="columnSettingVisible"
    v-model:print-visible="printVisible"
    v-model:print-bill-id="printBillId"
    @search="onSearch"
    @reset="onReset"
    @filter-change="onFilterChange"
    @filter-params="onHeaderFilterParams"
    @stat-click="onStatClick"
    @sort-change="onSortChange"
    @page-change="loadData"
  >
    <template #header-actions="{ selectedRows: sel }">
      <!-- 页头按钮统一走通用组件（2026-09-19）：按钮码由 perm-prefix 派生，权限控制一处生效 -->
      <BillHeaderActions
        perm-prefix="subcontract:picks"
        :add-text="$tt('新增发料单')"
        :show-import="false"
        :print-disabled="!sel.length"
        @add="$router.push('/subcontract/picks/new')"
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
          <div v-if="col.type === 'billNo'" class="order-no-cell">
            <span class="order-no-text" @click="$router.push(`/subcontract/picks/${row.id}`)">{{ row[col.prop] }}</span>
            <el-icon class="link-icon"><Link /></el-icon>
          </div>
          <div v-else-if="col.type === 'supplier'" class="customer-cell">
            <div class="customer-avatar">{{ (row[col.prop] || '?').charAt(0) }}</div>
            <div class="customer-info">
              <div class="customer-name">{{ row[col.prop] }}</div>
            </div>
          </div>
          <span v-else-if="col.type === 'quantity'" class="amount-text">{{ Number(row[col.prop] || 0) }}</span>
          <div v-else-if="col.type === 'status'" class="status-cell">
            <span :class="['status-dot', statusDotClass(row.status)]"></span>
            <el-tag size="small" :type="statusType(row.status)" effect="light" class="status-tag">{{ $dt(statusText(row.status)) }}</el-tag>
          </div>
        </template>
      </el-table-column>
    </template>
    <AuditColumns />
  </BillListPage>
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Link } from '@element-plus/icons-vue'
import {
  pageSubcontractPick, deleteSubcontractPick,
  submitSubcontractPick, approveSubcontractPick,
  reverseApproveSubcontractPick, getSubcontractPickDashboard
} from '@/api/production/subcontract'
import BillListPage from '@/components/bill/BillListPage.vue'
import BillHeaderActions from '@/components/bill/BillHeaderActions.vue'
import AuditColumns from '@/components/AuditColumns.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { exportListAsSeen, columnsToExportFields } from '@/utils/exportUtil'
// 筛选条件自定义（2026-10-14 全面推广）：条件定义共享工厂 + 明细报表 API（取明细模式专属条件元数据）
import { stdDefs, fld, detailFilterDefsOf, cfDefs } from '@/config/billFilterDefs'
import { useCustomFieldSearch } from '@/composables/useCustomFieldSearch'
import { createBillDetailReportApi } from '@/api/report/billDetailReport'

import { useTableSort } from '@/composables/useTableSort'
import { tt } from '@/i18n'
const { handleSortChange, getSortParams } = useTableSort()

/**
 * 批量操作声明（2026-09-20）：UI/确认/执行/结果面板全部由通用壳承担，这里只声明"怎么执行"。
 * 命名口径全系统统一：批量提交 / 批量审批 / 批量重置草稿 / 批量删除 / 批量导出。
 * 本页状态为数字：0 草稿 / 1 待审核 / 2 已生效。
 */
const batchActions = [
  {
    key: 'submit',
    label: tt('批量提交'),
    perm: 'subcontract:picks:submit',
    type: 'primary',
    exec: (row) => submitSubcontractPick(row.id),
    // 只有草稿可提交，其余状态自动跳过（不报错，进"跳过清单"说明原因）
    skipReason: (row) => (row.status !== 0
      ? tt('当前状态：') + statusText(row.status) + tt('，不是草稿，无需提交') : '')
  },
  {
    key: 'approve',
    label: tt('批量审批'),
    perm: 'subcontract:picks:audit',
    exec: (row) => approveSubcontractPick(row.id, localStorage.getItem('username') || 'system'),
    skipReason: (row) => (row.status !== 1
      ? tt('当前状态：') + statusText(row.status) + tt('，不是待审核，无需审批') : '')
  },
  {
    // 批量重置草稿：与页头「重置草稿」同一个后端入口（/reverse-approve），能否重置由后端判断
    key: 'reverse',
    label: tt('批量重置草稿'),
    perm: 'subcontract:picks:reverseApprove',
    exec: (row) => reverseApproveSubcontractPick(row.id)
  },
  {
    key: 'delete',
    label: tt('批量删除'),
    type: 'danger',
    perm: 'subcontract:picks:delete',
    exec: (row) => deleteSubcontractPick(row.id),
    // 状态冲突：选中行含非草稿 → 按钮置灰并说明原因
    disabledReason: (rows) => (rows.some(r => r.status !== 0)
      ? tt('非草稿不能删除，请先重置草稿') : '')
  },
  {
    key: 'export',
    label: tt('批量导出'),
    perm: 'subcontract:picks:export',
    entity: 'subcontractPick',
    idOf: (row) => row.id,
    // 勾选导出同样按"页面当前可见列"（2026-09-24 全站口径）
    fields: () => columnsToExportFields(visibleSortedColumns.value)
  }
]

const onSortChange = (e) => { handleSortChange(e); loadData() }
// 列定义（用于列设置抽屉+动态渲染）
// type 标记单元格渲染分支：billNo 单号链接、supplier 供应商头像、quantity 数量、status 状态标签
const columns = [
  { prop: 'pickNo', label: '领料单号', minWidth: 180, sortable: true, type: 'billNo' },
  { prop: 'orderNo', label: '委外单号', minWidth: 150, sortable: true },
  { prop: 'supplierName', label: '供应商', minWidth: 180, sortable: true, type: 'supplier' },
  { prop: 'pickDate', label: '领料日期', width: 120, sortable: true },
  { prop: 'totalQuantity', label: '总数量', width: 120, align: 'right', sortable: true, type: 'quantity' },
  { prop: 'status', label: '状态', width: 160, sortable: true, type: 'status' }
]
const { columnSettingVisible, isColVisible, getColFixed, configVersion, sortedColumns } = useColumnSetting('SubcontractPickList', columns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))

const loading = ref(false)
const tableData = ref([])
// 统一入参名：keyword / status（本页分页接口只有 current/size/keyword/status/sortBy/sortOrder）
const search = reactive({ keyword: '', status: '' })
const cf = useCustomFieldSearch('subcontract_pick')
const pagination = reactive({ current: 1, size: 20, total: 0 })
const selectedRows = ref([])
const printVisible = ref(false)
const printBillId = ref(null)

// 页头「打印」：一次只能打印一张（与销售订单口径一致）
function handleBatchPrint(rows) {
  const list = (rows && rows.length ? rows : selectedRows.value) || []
  if (!list.length) { ElMessage.warning(tt('请先勾选要打印的单据')); return }
  if (list.length > 1) { ElMessage.warning(tt('一次只能打印一张单据，请仅勾选一行')); return }
  printBillId.value = list[0].id
  printVisible.value = true
}
// 统计卡片高亮（点卡片筛选列表时由 onStatClick 控制）
const activeStatKey = ref('')

// 单据状态下拉选项：后端状态入参是 Integer，但下拉 value 必须用「非空字符串」——
// 通用搜索栏以 `!v` 判空，传数字 0 会被当成"未选择"从而清空筛选；传 '0' 时 HTTP 请求上仍是 status=0
// 取值来自状态机真实写入值：0 草稿 / 1 待审核 / 2 已生效
const statusOptions = [
  { label: '草稿', value: '0' },
  { label: '待审核', value: '1' },
  { label: '已生效', value: '2' }
]

// ============ 筛选条件自定义（2026-10-14 委外模块推广批次） ============
// 条件候选 = 标准维度（状态，std 标记 → 值走页面 search 通道，与统计卡筛选互通）
//          + 本单表头字段（f_<key> 参数化，后端白名单翻译）。
// 现状 filters 只声明 status（汇总接口无日期/供应商入参）→ stdDefs 只声明 status；
// 主日期/供应商作为普通表头字段进候选（不标 std，f_* 参数走后端白名单通道）。
// 键与「委外发料单明细报表」引擎筛选键同键 → 汇总/明细两种模式一套条件参数。
const filterDefs = computed(() => [
  ...stdDefs({ status: { options: statusOptions } }),
  fld('text', 'pickNo', '领料单号'),
  fld('text', 'orderNo', '委外单号'),
  fld('select', 'supplierName', '供应商', 'supplier'),
  fld('dateRange', 'pickDate', '领料日期'),
  fld('numberRange', 'totalQuantity', '总数量'),
  fld('text', 'remark', '备注'),
  ...cfDefs(cf)
])

// 明细模式专属筛选条件（来源 = 明细报表 /columns 的 filters 元数据，键与引擎同键；
// 默认勾选 = 现状：状态默认勾选，引擎已有的领料日期/供应商筛选自动成为明细模式候选（默认不勾）。
// 状态选项页内覆写：引擎静态枚举是通用中文五态，委外状态是数字状态机（0~3），选项必须用本页 statusOptions）
const detailFiltersMeta = ref([])
const STD_BY_KEY = { status: 'status' }
const detailFilterDefs = computed(() => detailFilterDefsOf(detailFiltersMeta.value, STD_BY_KEY)
  .map(d => (d.key === 'status' ? { ...d, options: statusOptions } : d)))
// 明细报表 API（与列表页明细模式双视图同一基础路径）
const subcontractPickDetailApi = createBillDetailReportApi('/report/subcontract/pick-detail')

/** 自定义条件参数（由通用壳经 filter-params 下发）：params 汇总用 */
const headerFilterState = ref({ params: {} })
function onHeaderFilterParams(payload) {
  headerFilterState.value = payload || { params: {} }
}

// 顶部统计卡片：原 4 张卡全部写死 '0'，改为后端 /api/subcontract/picks/dashboard（与列表同数据权限口径）
// 带 filter 的卡片可点击 → 直接筛选下方列表（再点同一张卡 = 取消）
// 注：本页 filters 只声明了 status（分页接口无日期入参），故「本月」卡不带 filter，避免点了没反应
const stats = ref([
  { key: 'month', label: '本月发料', value: 0, type: 'primary', icon: 'Document' },
  { key: 'draft', label: '草稿发料', value: 0, type: 'warning', icon: 'EditPen', filter: { status: '0' } },
  { key: 'pending', label: '待审核', value: 0, type: 'info', icon: 'Clock', filter: { status: '1' } },
  { key: 'effective', label: '已生效', value: 0, type: 'success', icon: 'CircleCheck', filter: { status: '2' } }
])

async function loadStats() {
  try {
    const d = (await getSubcontractPickDashboard()) || {}
    stats.value = [
      { key: 'month', label: '本月发料', value: d.monthPicks ?? 0, type: 'primary', icon: 'Document' },
      { key: 'draft', label: '草稿发料', value: d.draftPicks ?? 0, type: 'warning', icon: 'EditPen', filter: { status: '0' } },
      { key: 'pending', label: '待审核', value: d.pendingPicks ?? 0, type: 'info', icon: 'Clock', filter: { status: '1' } },
      { key: 'effective', label: '已生效', value: d.effectivePicks ?? 0, type: 'success', icon: 'CircleCheck', filter: { status: '2' } }
    ]
  } catch (e) {
    // 静默降级：取数失败时卡片保持原值，不弹诊断卡、不阻断列表
  }
}

// 兼容状态值可能为字符串或数字
const statusType = (s) => ({
  0: 'info', 1: 'warning', 2: 'primary'
}[Number(s)] || 'info')

const statusText = (s) => ({
  0: '草稿', 1: '待审核', 2: '已生效'
}[Number(s)] || String(s))

function statusDotClass(s) {
  return { 0: 'dot-info', 1: 'dot-warning', 2: 'dot-info' }[Number(s)] || 'dot-info'
}

// 卡片点击 → 应用该卡筛选条件（状态先清空再套用，避免上一张卡残留）；再点同一张卡 = 取消
function onStatClick({ key, filter }) {
  if (activeStatKey.value === key) {
    activeStatKey.value = ''
    Object.assign(search, { status: '' })
  } else {
    activeStatKey.value = key
    Object.assign(search, { status: '' }, filter || {})
  }
  pagination.current = 1
  loadData()
}

// 通用搜索栏（状态）变更：先把值写回本页 search
// 注：BillSearchBar 变更后会紧接着抛 search 事件（壳已转发 → onSearch），这里不重复发起查询
function onFilterChange(patch) {
  Object.assign(search, patch || {})
}

/**
 * 列表与导出**共用**的查询条件（2026-09-24 全站口径）：
 * 导出必须与列表用同一套条件，抽成一个函数避免两处各写一份导致口径漂移
 * （用户要求"页面上看到什么列，导出就带什么列；导出行 = 当前筛选条件下的全部"）。
 */
function buildQueryParams() {
  return {
    // 统一入参名（对应后端 keyword/status）
    keyword: search.keyword || undefined,
    status: search.status || undefined,
    // 筛选条件自定义（2026-10-14）：f_* 表头字段过滤参数由通用壳统一下发
    ...(headerFilterState.value.params || {}),
    // 单据自定义字段（勾选「可搜索」）→ customSearch 透传后端
    ...(headerFilterState.value.customSearch ? { customSearch: headerFilterState.value.customSearch } : {}),
    ...getSortParams()
  }
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageSubcontractPick({ current: pagination.current, size: pagination.size, ...buildQueryParams() })
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (e) { /* 拦截器已提示 */ } finally {
    loading.value = false
  }
}

function onSearch() { pagination.current = 1; loadData() }
function onReset() {
  // 重置 = 回到未筛选状态：关键词 + 通用搜索栏（状态）一并清空
  Object.assign(search, { keyword: '', status: '' })
  activeStatKey.value = ''
  pagination.current = 1
  loadData()
}

async function handleExport() {
  try {
    // 页面上看到什么列，导出就带什么列；导出行 = 当前筛选条件下的全部
    await exportListAsSeen({
      entityName: 'subcontractPick',
      columns: visibleSortedColumns.value,
      fetchPage: pageSubcontractPick,
      params: buildQueryParams(),
      idField: 'id'
    })
    ElMessage.success(tt('导出成功'))
  } catch (e) {
    // 失败原因由 exportUtil 统一提示
  }
}

onMounted(async () => {
  cf.load()
  loadStats()
  loadData()
  // 明细模式专属筛选条件元数据（明细报表 /columns 的 filters；失败静默：明细条件集为空时明细模式沿用汇总条件集）
  try {
    const res = await subcontractPickDetailApi.getColumns()
    detailFiltersMeta.value = (res && res.filters) || []
  } catch (e) { /* 静默降级 */ }
})
useAutoRefresh(() => {
  loadData()
  loadStats()
})
</script>

<style scoped lang="scss">
.subcontract-pick-page {
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

@media (max-width: 1200px) {
  .stat-cards { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 768px) {
  .subcontract-pick-page { padding: 12px; }
  .stat-cards { grid-template-columns: 1fr; }
  .search-bar {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }
  .status-tabs { flex-wrap: wrap; }
}
</style>
