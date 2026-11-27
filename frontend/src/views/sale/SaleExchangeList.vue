<template>
  <BillListPage
    page-class="sale-exchange-page"
    back-path="/sale"
    :title="$tt('销售换货单')"
    badge="SALE EXCHANGE"
    sub-title="由销售订单发起替代出库 · 替换货品回写原订单行"
    :filters="['status', 'date', 'customer', 'employee']"
    :filter-defs="filterDefs"
    :detail-filter-defs="detailFilterDefs"
    :status-options="statusOptions"
    :filter-model="search"
    employee-label="销售员"
    :table-data="tableData"
    :loading="loading"
    :pagination="pagination"
    storage-key="SaleExchangeList"
    :columns="columns"
    :table-key="configVersion"
    bill-type="SALE_EXCHANGE"
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
      <!-- 新增入口只有销售订单「销售替代出库」按钮，这里不提供新增/导入 -->
      <BillHeaderActions
        perm-prefix="sale:exchanges"
        :show-add="false"
        :show-import="false"
        :print-disabled="!sel.length"
        @print="handleBatchPrint(sel)"
        @export="handleExport"
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
            <span class="order-no-text" @click="$router.push(`/sale/exchanges/${row.exchangeId}`)">{{ row[col.prop] }}</span>
            <el-icon class="link-icon"><Link /></el-icon>
          </div>
          <div v-else-if="col.type === 'customer'" class="customer-cell">
            <div class="customer-avatar">{{ (row[col.prop] || '?').charAt(0) }}</div>
            <div class="customer-info">
              <div class="customer-name">{{ row[col.prop] }}</div>
            </div>
          </div>
          <span v-else-if="col.type === 'amount'" class="amount-text">{{ Number(row[col.prop] || 0).toFixed(2) }}</span>
          <span v-else-if="col.type === 'qty'" class="amount-text">{{ Number(row[col.prop] || 0).toFixed(2) }}</span>
          <span v-else-if="col.type === 'netAmount'" class="net-amount">¥{{ Number(row[col.prop] || 0).toFixed(2) }}</span>
          <div v-else-if="col.type === 'status'" class="status-cell">
            <span :class="['status-dot', statusDotClass(row.status)]"></span>
            <el-tag size="small" :type="statusType(row.status)" effect="light" class="status-tag">{{ $dt(row.status) }}</el-tag>
          </div>
          <el-tag v-else-if="col.type === 'approval'" size="small" :type="approvalStatusType(row.approvalStatus)" effect="plain">{{ $dt(row.approvalStatus || '-') }}</el-tag>
          <el-button v-else-if="col.type === 'action'" text type="primary" size="small" @click="$router.push(`/sale/exchanges/${row.exchangeId}`)">{{ $tt('查看') }}</el-button>
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
import { listExchanges, submitExchange, approveExchange, deleteExchange, reverseApproveExchange } from '@/api/sale/saleExchange'
import AuditColumns from '@/components/AuditColumns.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { statusType, statusDotClass, approvalStatusType } from '@/composables/useBillStatus'
import { exportListAsSeen } from '@/utils/exportUtil'

import BillListPage from '@/components/bill/BillListPage.vue'
import BillHeaderActions from '@/components/bill/BillHeaderActions.vue'
import { useTableSort } from '@/composables/useTableSort'
import { useCustomFieldSearch } from '@/composables/useCustomFieldSearch'
import { stdDefs, fld, cfDefs, detailFilterDefsOf } from '@/config/billFilterDefs'
// 明细报表引擎（同一套接口）：取 /columns 的 filters 元数据作「明细模式专属条件」
import { saleExchangeDetailApi } from '@/api/report/billDetailReport'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('sale:exchanges')
const { handleSortChange, getSortParams } = useTableSort()

/** 批量操作：批量提交 / 批量审批 / 批量重置草稿 / 批量删除 / 批量导出 */
const batchActions = [
  {
    key: 'submit',
    label: tt('批量提交'),
    type: 'primary',
    perm: 'sale:exchanges:submit',
    exec: (row) => submitExchange(row.exchangeId),
    skipReason: (row) => (row.status && row.status !== '草稿'
      ? tt('当前状态：') + row.status + tt('，不是草稿，无需提交') : '')
  },
  {
    key: 'approve',
    label: tt('批量审批'),
    perm: 'sale:exchanges:audit',
    exec: (row) => approveExchange(row.exchangeId),
    skipReason: (row) => (row.status !== '待审核'
      ? tt('当前状态：') + (row.status || '-') + tt('，不是待审核，无需审批') : '')
  },
  {
    key: 'reverse',
    label: tt('批量重置草稿'),
    perm: 'sale:exchanges:reverseApprove',
    exec: (row) => reverseApproveExchange(row.exchangeId)
  },
  {
    key: 'delete',
    label: tt('批量删除'),
    type: 'danger',
    perm: 'sale:exchanges:delete',
    exec: (row) => deleteExchange(row.exchangeId),
    disabledReason: (rows) => (rows.some(r => r.status && r.status !== '草稿' && r.status !== '已驳回' && r.status !== '已取消')
      ? tt('非草稿不能删除，请先重置草稿') : '')
  },
  {
    key: 'export',
    label: tt('批量导出'),
    perm: 'sale:exchanges:export',
    entity: 'saleExchange',
    idOf: (row) => row.exchangeId
  }
]

const onSortChange = (e) => { handleSortChange(e); fetchData() }

// 列定义（列设置 + 动态渲染共用）
const columns = [
  { prop: 'exchangeNo', label: '换货单号', minWidth: 180, sortable: true, type: 'billNo' },
  { prop: 'exchangeDate', label: '换货日期', width: 120, sortable: true },
  { prop: 'customerName', label: '客户', minWidth: 180, type: 'customer' },
  { prop: 'sourceBillNo', label: '来源订单号', minWidth: 160 },
  { prop: 'totalQuantity', label: '数量合计', width: 110, align: 'right', type: 'qty' },
  { prop: 'netAmount', label: '价税合计', width: 130, align: 'right', sortable: true, type: 'netAmount' },
  { prop: 'status', label: '状态', width: 160, type: 'status' },
  { prop: 'approvalStatus', label: '审核', width: 100, type: 'approval' },
  { prop: 'action', label: '操作', width: 90, align: 'center', type: 'action' }
]
const { columnSettingVisible, isColVisible, getColFixed, configVersion, sortedColumns } = useColumnSetting('SaleExchangeList', columns)
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))

const loading = ref(false)
const tableData = ref([])
const search = reactive({ keyword: '', status: '', startDate: '', endDate: '', customerId: '', employeeId: '' })
const cf = useCustomFieldSearch('sale_exchange')

const pagination = reactive({ current: 1, size: 20, total: 0, ...getSortParams() })
const selectedRows = ref([])

const printVisible = ref(false)
const printBillId = ref(null)

const statusOptions = [
  { label: '草稿', value: '草稿' },
  { label: '待审核', value: '待审核' },
  { label: '已生效', value: '已生效' },
  { label: '已驳回', value: '已驳回' },
  { label: '已作废', value: '已作废' }
]

const filterDefs = computed(() => [
  ...stdDefs({
    status: { options: statusOptions },
    date: { key: 'exchangeDate', label: '换货日期' },
    customer: { key: 'customerName' },
    employee: { key: 'salerName', label: '销售员' }
  }),
  fld('text', 'exchangeNo', '换货单号'),
  fld('text', 'sourceBillNo', '来源订单号'),
  fld('text', 'generatedDeliveryNo', '已生成出库单'),
  fld('text', 'deliveryAddress', '收货地址'),
  fld('text', 'remark', '备注'),
  fld('select', 'currencyCode', '币种', 'currency'),
  fld('numberRange', 'totalQuantity', '数量合计'),
  fld('numberRange', 'totalAmount', '金额'),
  fld('numberRange', 'taxAmount', '税额'),
  fld('numberRange', 'netAmount', '价税合计'),
  ...cfDefs(cf)
])

const headerFilterState = ref({ params: {}, customSearch: undefined, detailParams: {} })
function onHeaderFilterParams(payload) {
  headerFilterState.value = payload || { params: {}, customSearch: undefined, detailParams: {} }
}

// 明细模式专属筛选条件：标准维度（状态/日期/客户/销售员）沿用页面 search 通道，其余条件走明细接口
const detailFiltersMeta = ref([])
const STD_BY_KEY = { status: 'status', exchangeDate: 'date', customerName: 'customer', salerName: 'employee' }
const detailFilterDefs = computed(() => detailFilterDefsOf(detailFiltersMeta.value, STD_BY_KEY))

function handlePrint(row) {
  printBillId.value = row.exchangeId
  printVisible.value = true
}

function handleBatchPrint(rows) {
  // 以插槽实时勾选为准（明细/汇总两种视图模式都可用），页面级缓存仅兜底
  const list = (rows && rows.length ? rows : selectedRows.value) || []
  if (!list.length) {
    ElMessage.warning(tt('请先勾选要打印的换货单'))
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
    const res = await listExchanges({ current: pagination.current, size: pagination.size, ...buildQueryParams() })
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
  Object.assign(search, { keyword: '', status: '', startDate: '', endDate: '', customerId: '', employeeId: '' })
  pagination.current = 1
  fetchData()
}

async function handleExport() {
  try {
    await exportListAsSeen({
      entityName: 'saleExchange',
      columns: visibleSortedColumns.value,
      fetchPage: listExchanges,
      params: buildQueryParams(),
      idField: 'exchangeId'
    })
    ElMessage.success(tt('导出成功'))
  } catch (e) {
    // 失败原因已由 exportUtil 统一提示
  }
}

onMounted(async () => {
  cf.load()
  // 明细模式专属筛选条件元数据（明细报表 /columns 一次带回 filters），失败静默：不影响汇总列表
  try {
    const res = await saleExchangeDetailApi.getColumns()
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
</style>
