<template>
  <BillListPage
    page-class="sale-repair-page"
    :class="{ 'repair-empty': !loading && !tableData.length }"
    back-path="/sale"
    :title="$tt('售后维修单')"
    badge="SALE REPAIR"
    sub-title="客户报修承接 · 收件入库 · 配件领用 · 返还客户"
    keyword-label="维修单号"
    search-placeholder="输入维修单号、来源单号或客户名称搜索"
    :filters="['status', 'date', 'customer']"
    :filter-defs="filterDefs"
    :detail-filter-defs="detailFilterDefs"
    :status-options="statusOptions"
    :filter-model="search"
    :table-data="tableData"
    :loading="loading"
    :pagination="pagination"
    storage-key="SaleRepairList"
    :columns="columns"
    :table-key="configVersion"
    bill-type="SALE_REPAIR"
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
    @page-change="fetchData"
  >
    <template #header-actions="{ selectedRows: sel }">
      <BillHeaderActions
        perm-prefix="sale:repairs"
        :add-text="$tt('新增维修单')"
        :print-disabled="!sel.length"
        @add="$router.push('/sale/repairs/new')"
        @print="handleBatchPrint(sel)"
        @export="handleExport"
        @import="importVisible = true"
      />
    </template>

    <el-table-column type="selection" width="50" />
    <el-table-column type="index" label="#" width="60" />
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
            <span class="order-no-text" @click="$router.push(`/sale/repairs/${row.repairId}`)">{{ row[col.prop] }}</span>
            <el-icon class="link-icon"><Link /></el-icon>
          </div>
          <div v-else-if="col.type === 'customer'" class="customer-cell">
            <div class="customer-avatar">{{ (row[col.prop] || '?').charAt(0) }}</div>
            <div class="customer-info">
              <div class="customer-name">{{ row[col.prop] }}</div>
            </div>
          </div>
          <span v-else-if="col.type === 'qty'" class="amount-text">{{ Number(row[col.prop] || 0).toFixed(2) }}</span>
          <span v-else-if="col.type === 'amount'" class="net-amount">¥{{ Number(row[col.prop] || 0).toFixed(2) }}</span>
          <div v-else-if="col.type === 'status'" class="status-cell">
            <span :class="['status-dot', statusDotClass(row.status)]"></span>
            <el-tag size="small" :type="statusType(row.status)" effect="light" class="status-tag">{{ $dt(row.status) }}</el-tag>
          </div>
          <el-tag v-else-if="col.type === 'approval'" size="small" :type="approvalStatusType(row.approvalStatus)" effect="plain">{{ $dt(row.approvalStatus || '-') }}</el-tag>
          <el-button v-else-if="col.type === 'action'" text type="primary" size="small" @click="$router.push(`/sale/repairs/${row.repairId}`)">{{ $tt('查看') }}</el-button>
        </template>
      </el-table-column>
    </template>
    <AuditColumns />
  </BillListPage>

  <!-- 导入弹窗（通用组件：两步式模板下载 + 文件上传，全站单据共用一份） -->
  <BillImportDialog v-model="importVisible" entity-name="saleRepair" :title="$tf('售后维修单导入')" @done="fetchData" />
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Link } from '@element-plus/icons-vue'
import { listRepairs, submitRepair, approveRepair, deleteRepair, reverseApproveRepair } from '@/api/sale/saleRepair'
import AuditColumns from '@/components/AuditColumns.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { statusType, statusDotClass, approvalStatusType } from '@/composables/useBillStatus'
import { exportListAsSeen } from '@/utils/exportUtil'

import BillListPage from '@/components/bill/BillListPage.vue'
import BillHeaderActions from '@/components/bill/BillHeaderActions.vue'
import BillImportDialog from '@/components/bill/BillImportDialog.vue'
import { useTableSort } from '@/composables/useTableSort'
import { useCustomFieldSearch } from '@/composables/useCustomFieldSearch'
import { stdDefs, fld, cfDefs, detailFilterDefsOf } from '@/config/billFilterDefs'
// 明细报表引擎（同一套接口）：取 /columns 的 filters 元数据作「明细模式专属条件」
import { saleRepairDetailApi } from '@/api/report/billDetailReport'
import { tt } from '@/i18n'

const { handleSortChange, getSortParams } = useTableSort()

/** 批量操作：批量提交 / 批量审批 / 批量重置草稿 / 批量删除 / 批量导出 */
const batchActions = [
  {
    key: 'submit',
    label: tt('批量提交'),
    type: 'primary',
    perm: 'sale:repairs:submit',
    exec: (row) => submitRepair(row.repairId),
    skipReason: (row) => (!row.status || row.status !== '草稿' && row.status !== '已驳回'
      ? tt('当前状态：') + (row.status || '-') + tt('，不是草稿，无需提交') : '')
  },
  {
    key: 'approve',
    label: tt('批量审批'),
    perm: 'sale:repairs:audit',
    exec: (row) => approveRepair(row.repairId),
    skipReason: (row) => (row.status !== '待审核'
      ? tt('当前状态：') + (row.status || '-') + tt('，不是待审核，无需审批') : '')
  },
  {
    key: 'reverse',
    label: tt('批量重置草稿'),
    perm: 'sale:repairs:reverseApprove',
    exec: (row) => reverseApproveRepair(row.repairId)
  },
  {
    key: 'delete',
    label: tt('批量删除'),
    type: 'danger',
    perm: 'sale:repairs:delete',
    exec: (row) => deleteRepair(row.repairId),
    disabledReason: (rows) => (rows.some(r => r.status && r.status !== '草稿' && r.status !== '已驳回')
      ? tt('非草稿不能删除，请先重置草稿') : '')
  },
  {
    key: 'export',
    label: tt('批量导出'),
    perm: 'sale:repairs:export',
    entity: 'saleRepair',
    idOf: (row) => row.repairId
  }
]

const onSortChange = (e) => { handleSortChange(e); fetchData() }

// 列定义（列设置 + 动态渲染共用）
const columns = [
  { prop: 'repairNo', label: '维修单号', minWidth: 180, sortable: true, type: 'billNo' },
  { prop: 'repairDate', label: '维修日期', width: 120, sortable: true },
  { prop: 'customerName', label: '客户', minWidth: 180, type: 'customer' },
  { prop: 'sourceBillNo', label: '来源单据号', minWidth: 160 },
  { prop: 'serviceType', label: '服务方式', width: 100 },
  { prop: 'warrantyStatus', label: '保修判定', width: 100 },
  { prop: 'totalQuantity', label: '数量', width: 100, align: 'right', type: 'qty' },
  { prop: 'totalFee', label: '合计费用', width: 130, align: 'right', sortable: true, type: 'amount' },
  { prop: 'status', label: '状态', width: 140, type: 'status' },
  { prop: 'approvalStatus', label: '审核状态', width: 100, type: 'approval' },
  { prop: 'action', label: '操作', width: 90, align: 'center', type: 'action' }
]
const { columnSettingVisible, isColVisible, getColFixed, configVersion, sortedColumns } = useColumnSetting('SaleRepairList', columns)
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))

const loading = ref(false)
const tableData = ref([])
const search = reactive({ keyword: '', status: '', startDate: '', endDate: '', customerId: '' })
const cf = useCustomFieldSearch('sale_repair')

const pagination = reactive({ current: 1, size: 20, total: 0, ...getSortParams() })
const selectedRows = ref([])

const printVisible = ref(false)
const printBillId = ref(null)
/** 导入弹窗显隐（弹窗本体 = 通用组件 BillImportDialog） */
const importVisible = ref(false)

const statusOptions = [
  { label: '草稿', value: '草稿' },
  { label: '待审核', value: '待审核' },
  { label: '已受理', value: '已受理' },
  { label: '维修中', value: '维修中' },
  { label: '维修完成', value: '维修完成' },
  { label: '已返还', value: '已返还' },
  { label: '已驳回', value: '已驳回' },
  { label: '已作废', value: '已作废' }
]

const filterDefs = computed(() => [
  ...stdDefs({
    status: { options: statusOptions },
    date: { key: 'repairDate', label: '维修日期' },
    customer: { key: 'customerName' }
  }),
  fld('text', 'repairNo', '维修单号'),
  fld('text', 'sourceBillNo', '来源单号'),
  fld('text', 'transferredBillNo', '转出单据号'),
  ...cfDefs(cf)
])

const headerFilterState = ref({ params: {}, customSearch: undefined, detailParams: {} })
function onHeaderFilterParams(payload) {
  headerFilterState.value = payload || { params: {}, customSearch: undefined, detailParams: {} }
}

// 明细模式专属筛选条件：标准维度（状态/日期/客户）沿用页面 search 通道，其余条件走明细接口
const detailFiltersMeta = ref([])
const STD_BY_KEY = { status: 'status', repairDate: 'date', customerName: 'customer' }
const detailFilterDefs = computed(() => detailFilterDefsOf(detailFiltersMeta.value, STD_BY_KEY))

function handlePrint(row) {
  printBillId.value = row.repairId
  printVisible.value = true
}

function handleBatchPrint(rows) {
  // 以插槽实时勾选为准（明细/汇总两种视图模式都可用），页面级缓存仅兜底
  const list = (rows && rows.length ? rows : selectedRows.value) || []
  if (!list.length) {
    ElMessage.warning(tt('请先勾选要打印的维修单'))
    return
  }
  if (list.length > 1) {
    ElMessage.warning(tt('一次只能打印一张单据，请仅勾选一行'))
    return
  }
  handlePrint(list[0])
}

function buildQueryParams() {
  return {
    ...search,
    ...(headerFilterState.value.params || {}),
    ...(headerFilterState.value.customSearch ? { customSearch: headerFilterState.value.customSearch } : {}),
    ...getSortParams()
  }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await listRepairs({ current: pagination.current, size: pagination.size, ...buildQueryParams() })
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (e) {
    ElMessage.error(e.message || tt('查询失败'))
  } finally {
    loading.value = false
  }
}

function onFilterChange(patch) {
  Object.assign(search, patch || {})
}

function handleSearch() {
  pagination.current = 1
  fetchData()
}

function handleReset() {
  Object.assign(search, { keyword: '', status: '', startDate: '', endDate: '', customerId: '' })
  pagination.current = 1
  fetchData()
}

async function handleExport() {
  try {
    await exportListAsSeen({
      entityName: 'saleRepair',
      columns: visibleSortedColumns.value,
      fetchPage: listRepairs,
      params: buildQueryParams(),
      idField: 'repairId'
    })
    ElMessage.success(tt('导出成功'))
  } catch (e) {
    // 失败原因已由 exportUtil 统一提示
  }
}

onMounted(async () => {
  cf.load()
  // 明细模式专属筛选条件元数据（全明细报表 /columns 一次带回 filters），失败静默：不影响汇总列表
  try {
    const res = await saleRepairDetailApi.getColumns()
    detailFiltersMeta.value = (res && res.filters) || []
  } catch (e) { /* 静默降级 */ }
  fetchData()
})
useAutoRefresh(() => { fetchData() })
</script>

<style scoped lang="scss">
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

// 空列表引导：列表页壳未开放空态插槽，这里只覆盖壳内表格空文案，不改壳组件
.sale-repair-page.repair-empty {
  :deep(.el-table__empty-text) {
    font-size: 0;

    &::after {
      content: '还没有售后维修单，可在销售订单或销售出库单点「售后报修」，或点新增';
      font-size: 13px;
      color: #909399;
      line-height: 1.8;
    }
  }
}
</style>
