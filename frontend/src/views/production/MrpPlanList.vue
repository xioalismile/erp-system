<template>
  <!-- 通用列表壳（页头/统计卡/搜索条/表格外壳/分页/列设置），2026-09-20 由自研页迁移而来
       状态 =「单据状态」下拉（本页状态为中文：草稿/已生效/已执行）；关键词 = 方案编号/方案名称 -->
  <BillListPage
    page-class="mrp-plan-page"
    back-path="/production"
    :title="$tt('MRP计划')"
    badge="MRP PLAN"
    sub-title="管理物料需求计划 · 优化生产物料分配"
    create-path="/production/mrp/new"
    :stats="stats"
    :active-stat-key="activeStatKey"
    search-placeholder="方案编号/方案名称"
    keyword-label="单据编号"
    :filters="['status']"
    :status-options="statusOptions"
    :filter-defs="filterDefs"
    :detail-filter-defs="detailFilterDefs"
    :filter-model="search"
    :table-data="tableData"
    :loading="loading"
    :pagination="pagination"
    storage-key="MrpPlanList"
    bill-type="MRP_PLAN"
    :columns="columns"
    :table-key="configVersion"
    :page-sizes="[10, 20, 50, 100]"
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
    @sort-change="onSortChange"
    @page-change="loadData"
  >
    <template #header-actions="{ selectedRows: sel }">
      <BillHeaderActions
        perm-prefix="production:mrp"
        :add-text="$tt('新增MRP计划')"
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
              <div v-if="col.type === 'planNo'" class="order-no-cell">
                <span class="order-no-text" @click="onDetail(row)">{{ row.planNo }}</span>
                <el-icon class="link-icon"><Link /></el-icon>
              </div>
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
  listMrpPlan,
  deleteMrpPlan,
  approveMrpPlan, executeMrpPlan,
  reverseApproveMrpPlan,
  mrpPlanDetailApi
} from '@/api/production/mrpPlan'
import { exportListAsSeen, columnsToExportFields } from '@/utils/exportUtil'
import BillListPage from '@/components/bill/BillListPage.vue'
import BillHeaderActions from '@/components/bill/BillHeaderActions.vue'
import AuditColumns from '@/components/AuditColumns.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
// 筛选条件自定义共享工厂（2026-10-14 全面推广）：候选集拼装统一在工厂，页面只声明本单键位差异
// （MRP 无单据自定义字段实体 → 不调 cfDefs、不透传 customSearch）
import { stdDefs, fld, detailFilterDefsOf } from '@/config/billFilterDefs'
import { Link } from '@element-plus/icons-vue'

import { useTableSort } from '@/composables/useTableSort'
import { tt } from '@/i18n'
const { handleSortChange, getSortParams } = useTableSort()


const onSortChange = (e) => { handleSortChange(e); loadData() }
// 列定义（用于列设置抽屉+动态渲染）= 表格全部数据列；type 标记单元格渲染分支，纯文本列不加 type
const columns = [
  { prop: 'planNo', label: '方案编号', minWidth: 160, sortable: true, type: 'planNo' },
  { prop: 'planName', label: '方案名称', minWidth: 200, sortable: true },
  { prop: 'planDate', label: '运算日期', width: 120, sortable: true },
  { prop: 'planHorizon', label: '计划展望期(天)', width: 130, align: 'right', sortable: true },
  { prop: 'demandSource', label: '需求来源', minWidth: 140, sortable: true },
  { prop: 'lotSizeType', label: '批量规则', width: 120, sortable: true },
  { prop: 'timeFenceDays', label: '时界(天)', width: 100, align: 'right', sortable: true },
  { prop: 'status', label: '状态', width: 110, sortable: true, type: 'status' }
]
const { columnSettingVisible, isColVisible, getColFixed, configVersion, sortedColumns } = useColumnSetting('MrpPlanList', columns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))

const router = useRouter()

const loading = ref(false)
const tableData = ref([])
// 壳契约：搜索条件 / 分页 / 勾选行 / 统计卡高亮
const search = reactive({ keyword: '', status: '' })
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
const activeStatKey = ref('')

// 统计卡：沿用原页「按当前列表数据现算」的口径（该单据无 dashboard 接口，不臆造接口）；
// 卡片不带 filter —— 本页搜索栏只有 status 维度，带 filter 会渲染出"点了没反应"的假下钻
const stats = computed(() => [
  { key: 'total', label: '方案总数', value: tableData.value.length, type: 'primary', icon: 'Document' },
  { key: 'draft', label: '草稿', value: tableData.value.filter(r => r.status === '草稿').length, type: 'warning', icon: 'Clock' },
  { key: 'approved', label: '已生效', value: tableData.value.filter(r => r.status === '已生效').length, type: 'info', icon: 'CircleCheck' },
  { key: 'executed', label: '已执行', value: tableData.value.filter(r => r.status === '已执行').length, type: 'success', icon: 'VideoPlay' }
])

// 单据状态下拉：本页状态是中文，value 直接用中文（「全部」由通用搜索栏内置）
const statusOptions = [
  { label: '草稿', value: '草稿' },
  { label: '已生效', value: '已生效' },
  { label: '已执行', value: '已执行' }
]

// ============ 筛选条件自定义（2026-10-14 全面推广 · 销售订单试点标准） ============
// 条件候选 = 标准维度（仅状态，本页搜索栏现状只有状态维度）
//          + 本单表头字段（f_<key> 参数化，后端白名单翻译）。
// 本单据无单据自定义字段实体 → 不调 cfDefs、不透传 customSearch。
// 键与「MRP 运算计划明细报表」引擎筛选键同键 → 汇总/明细两种模式一套条件参数。
// 状态值中文（与后端 planStatusCode 文案转换一致）；明细引擎状态键 = statusText（CASE 转中文后等值）。
const filterDefs = computed(() => [
  ...stdDefs({ status: { options: statusOptions } }),
  fld('text', 'planNo', '方案编号'),
  fld('text', 'planName', '方案名称'),
  fld('dateRange', 'planDate', '运算日期'),
  fld('text', 'demandSource', '需求来源'),
  fld('text', 'lotSizeType', '批量规则'),
  fld('numberRange', 'planHorizon', '计划展望期(天)'),
  fld('numberRange', 'timeFenceDays', '时界(天)'),
  fld('numberRange', 'totalNetDemand', '净需求总量'),
  fld('numberRange', 'totalPurchaseSuggestion', '采购建议总量'),
  fld('numberRange', 'totalProduceSuggestion', '生产建议总量'),
  fld('text', 'remark', '备注')
])

// 明细模式专属筛选条件（来源 = 明细报表 /columns 的 filters 元数据，键与引擎同键；
// STD_BY_KEY 声明引擎筛选键 → 标准维度（默认勾选=现状、值走页面 search 通道）。
// 引擎状态筛选键 = statusText（CASE 转中文后等值），与页面 search.status 中文取值同口径）
const detailFiltersMeta = ref([])
const STD_BY_KEY = { statusText: 'status' }
const detailFilterDefs = computed(() => detailFilterDefsOf(detailFiltersMeta.value, STD_BY_KEY))

/** 自定义条件参数（由通用壳经 filter-params 下发）：params 汇总用 / detailParams 明细用 */
const headerFilterState = ref({ params: {}, customSearch: undefined, detailParams: {} })
function onHeaderFilterParams(payload) {
  headerFilterState.value = payload || { params: {}, customSearch: undefined, detailParams: {} }
}

/**
 * 批量操作声明（2026-09-20）：UI/确认/执行/结果面板由通用壳承担，这里只声明"怎么执行"。
 * 本单据**没有提交入口**（api 无 submit 函数）→ 按例外清单不声明「批量提交」。
 * perm 前缀用菜单码 production:mrp（BILL_TYPE_MENU_CODE 里 MRP_PLAN → production:mrp，取自 sys_menu，非臆造）。
 * 状态链：草稿 → （审批）已生效 → （执行）已执行。
 */
const batchActions = [
  {
    key: 'approve',
    label: tt('批量审批'),
    perm: 'production:mrp:audit',
    exec: (row) => approveMrpPlan(row.id),
    skipReason: (row) => (row.status !== '草稿'
      ? tt('当前状态：') + row.status + tt('，不是待审核，无需审批') : '')
  },
  {
    key: 'reverse',
    label: tt('批量重置草稿'),
    perm: 'production:mrp:reverseApprove',
    exec: (row) => reverseApproveMrpPlan(row.id)
  },
  {
    key: 'delete',
    label: tt('批量删除'),
    type: 'danger',
    perm: 'production:mrp:delete',
    exec: (row) => deleteMrpPlan(row.id)
  },
  {
    key: 'export',
    label: tt('批量导出'),
    perm: 'production:mrp:export',
    entity: 'mrpPlan',
    idOf: (row) => row.id,
    // 勾选导出同样按"页面当前可见列"（2026-09-24 全站口径）
    fields: () => columnsToExportFields(visibleSortedColumns.value)
  }
]

const statusType = (s) => ({
  '草稿': 'info', '已生效': 'warning', '已执行': 'success'
}[s] || 'info')

function statusDotClass(s) {
  return {
    '草稿': 'dot-info',
    '已生效': 'dot-warning',
    '已执行': 'dot-success'
  }[s] || 'dot-info'
}

/**
 * 列表与导出**共用**的查询条件（2026-09-24 全站口径）：
 * 导出必须与列表用同一套条件，抽成一个函数避免两处各写一份导致口径漂移
 * （用户要求"页面上看到什么列，导出就带什么列；导出行 = 当前筛选条件下的全部"）。
 */
function buildQueryParams() {
  return {
    keyword: search.keyword, status: search.status,
    ...getSortParams(),
    // 筛选条件自定义（2026-10-14）：f_* 表头字段过滤参数由通用壳统一下发
    ...(headerFilterState.value.params || {})
  }
}

async function loadData() {
  loading.value = true
  try {
    const res = await listMrpPlan({ current: pagination.current, size: pagination.size, ...buildQueryParams() })
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (e) { /* 拦截器已提示 */ } finally {
    loading.value = false
  }
}

function handleSearch() { pagination.current = 1; loadData() }
function handleReset() {
  search.keyword = ''
  search.status = ''
  pagination.current = 1
  activeStatKey.value = ''
  loadData()
}
// 通用搜索栏条件变化：壳写入后紧接着会抛 search（见 BillSearchBar），这里只写回条件，避免重复请求
function onFilterChange(patch) {
  Object.assign(search, patch || {})
}

async function handleExport() {
  try {
    // 页面上看到什么列，导出就带什么列；导出行 = 当前筛选条件下的全部
    await exportListAsSeen({
      entityName: 'mrpPlan',
      columns: visibleSortedColumns.value,
      fetchPage: listMrpPlan,
      params: buildQueryParams(),
      idField: 'id'
    })
    ElMessage.success(tt('导出成功'))
  } catch (e) {
    // 失败原因由 exportUtil 统一提示
  }
}

// 跳转到新增MRP计划页面
function onAdd() {
  // 2026-09-20 入口改造：新增 = 进入 MRP 运算卡片向导（5 步），不再直接开空白计划单
  router.push('/production/mrp/new')
}

// 跳转到编辑MRP计划页面
function onEdit(row) {
  router.push(`/production/mrp-plans/edit/${row.id}`)
}

// 跳转到MRP计划详情页面（复用编辑页）
function onDetail(row) {
  router.push(`/production/mrp-plans/edit/${row.id}`)
}

async function onApprove(row) {
  await ElMessageBox.confirm(`${tt("确认审核方案 ")}${row.planNo}${tt("？审核后不可修改")}`, tt('确认'), { type: 'warning' })
  try {
    await approveMrpPlan(row.id)
    ElMessage.success(tt('已生效'))
    loadData()
  } catch (e) { /* 拦截器已提示 */ }
}

async function onExecute(row) {
  await ElMessageBox.confirm(`${tt("确认执行MRP方案 ")}${row.planNo}${tt("？执行后将生成计划采购订单和计划生产工单")}`, tt('确认'), { type: 'warning' })
  try {
    await executeMrpPlan(row.id)
    ElMessage.success(tt('执行成功'))
    loadData()
  } catch (e) { /* 拦截器已提示 */ }
}

async function onDelete(row) {
  await ElMessageBox.confirm(`${tt("确认删除方案 ")}${row.planNo}？`, tt('确认'), { type: 'warning' })
  try {
    await deleteMrpPlan(row.id)
    ElMessage.success(tt('删除成功'))
    loadData()
  } catch (e) { /* 拦截器已提示 */ }
}

onMounted(() => {
  loadData()
  // 明细模式专属筛选条件元数据（明细报表 /columns 的 filters；失败静默：明细条件集为空时明细模式沿用汇总条件集）
  mrpPlanDetailApi.getColumns().then(res => {
    detailFiltersMeta.value = (res && res.filters) || []
  }).catch(() => { /* 静默降级 */ })
})
useAutoRefresh(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.mrp-plan-page {
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
  .mrp-plan-page { padding: 12px; }
  .stat-cards { grid-template-columns: 1fr; }
  .search-bar {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }
  .status-tabs { flex-wrap: wrap; }
}
</style>
