<template>
  <div class="standard-report-page statement-page">
    <!-- 顶部筛选区 -->
    <div class="filter-section">
      <div class="filter-row">
        <div class="filter-item date-range-item">
          <el-date-picker
            v-model="filters.dateRange"
            type="daterange"
            range-separator="~"
            :start-placeholder="$tt('开始日期')"
            :end-placeholder="$tt('结束日期')"
            value-format="YYYY-MM-DD"
            clearable
          />
        </div>
        <div class="filter-item">
          <el-input v-model="filters.keyword" :placeholder="$tt('单据编号')" clearable />
        </div>
        <div class="filter-item">
          <el-input v-model="filters.supplierCode" :placeholder="$tt('供应商编号')" clearable />
        </div>
        <div class="filter-item">
          <el-input v-model="filters.supplierName" :placeholder="$tt('供应商名称')" clearable />
        </div>
      </div>
      <div class="filter-actions">
        <el-button type="primary" :loading="loading" @click="loadAll">
          <el-icon><Search /></el-icon>查询
        </el-button>
        <el-button @click="resetFilter">{{ $tt("重置") }}</el-button>
        <el-button type="primary" plain @click="handleExport">
          <el-icon><Download /></el-icon>导出Excel
        </el-button>
      </div>
    </div>

    <!-- 数据区 -->
    <div class="data-section">
      <div class="data-header">
        <span class="report-title">{{ $tt("供应商对账单") }}</span>
        <span class="report-sub">{{ $tt("按供应商汇总应付/已付/余额及业务来源金额") }}</span>
      </div>

      <el-tabs v-model="activeTab" type="border-card" @tab-change="onTabChange">
        <!-- 汇总统计页签 -->
        <el-tab-pane :label="$tf('汇总统计')" name="summary">
          <el-table
            :data="summaryData"
            border
            size="small"
            v-loading="loading"
            :max-height="tableMaxHeight"
            show-summary
            :summary-method="summaryMethod"
            highlight-current-row
            @row-click="onSummaryRowClick"
          >
            <el-table-column type="index" label="#" width="40" fixed="left" align="center" />
            <el-table-column prop="supplierCode" :label="$tf('供应商编号')" width="110" fixed="left" show-overflow-tooltip />
            <el-table-column prop="supplierName" :label="$tf('供应商名称')" min-width="160" fixed="left" show-overflow-tooltip />
            <el-table-column prop="beginBalance" :label="$tf('期初余额')" width="110" align="right" sortable>
              <template #default="{ row }">
                <span class="cell-amount">¥{{ formatNumber(row.beginBalance) }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('本期发生')" align="center">
              <el-table-column prop="purchaseOrderAmount" :label="$tf('订单金额')" width="110" align="right" sortable>
                <template #default="{ row }">
                  <span class="cell-amount">¥{{ formatNumber(row.purchaseOrderAmount) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="purchaseOrderPaidAmount" :label="$tf('订单已付')" width="110" align="right" sortable>
                <template #default="{ row }">
                  <span class="cell-amount">¥{{ formatNumber(row.purchaseOrderPaidAmount) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="purchaseOrderUnpaidAmount" :label="$tf('订单未付')" width="110" align="right" sortable>
                <template #default="{ row }">
                  <span class="cell-amount">¥{{ formatNumber(row.purchaseOrderUnpaidAmount) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="purchaseReceiptAmount" :label="$tf('入库金额')" width="110" align="right" sortable>
                <template #default="{ row }">
                  <span class="cell-amount">¥{{ formatNumber(row.purchaseReceiptAmount) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="purchaseReceiptPaidAmount" :label="$tf('入库已付')" width="110" align="right" sortable>
                <template #default="{ row }">
                  <span class="cell-amount">¥{{ formatNumber(row.purchaseReceiptPaidAmount) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="purchaseReceiptUnpaidAmount" :label="$tf('入库未付')" width="110" align="right" sortable>
                <template #default="{ row }">
                  <span class="cell-amount">¥{{ formatNumber(row.purchaseReceiptUnpaidAmount) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="purchaseInvoiceAmount" :label="$tf('发票金额')" width="110" align="right" sortable>
                <template #default="{ row }">
                  <span class="cell-amount">¥{{ formatNumber(row.purchaseInvoiceAmount) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="purchaseInvoicePaidAmount" :label="$tf('发票已付')" width="110" align="right" sortable>
                <template #default="{ row }">
                  <span class="cell-amount">¥{{ formatNumber(row.purchaseInvoicePaidAmount) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="purchaseInvoiceUnpaidAmount" :label="$tf('发票未付')" width="110" align="right" sortable>
                <template #default="{ row }">
                  <span class="cell-amount">¥{{ formatNumber(row.purchaseInvoiceUnpaidAmount) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="purchaseReturnAmount" :label="$tf('退货金额')" width="110" align="right" sortable>
                <template #default="{ row }">
                  <span class="cell-amount">¥{{ formatNumber(row.purchaseReturnAmount) }}</span>
                </template>
              </el-table-column>
            </el-table-column>
            <el-table-column prop="currentPayable" :label="$tf('应付合计')" width="110" align="right" sortable>
              <template #default="{ row }">
                <span class="cell-amount">¥{{ formatNumber(row.currentPayable) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="currentPaid" :label="$tf('本期付款')" width="110" align="right" sortable>
              <template #default="{ row }">
                <span class="cell-amount">¥{{ formatNumber(row.currentPaid) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="currentUnpaid" :label="$tf('本期未付')" width="110" align="right" sortable>
              <template #default="{ row }">
                <span class="cell-amount">¥{{ formatNumber(row.currentUnpaid) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="endBalance" :label="$tf('剩余未付')" width="110" align="right" sortable>
              <template #default="{ row }">
                <span class="cell-amount" :class="row.endBalance > 0 ? 'text-danger' : ''">¥{{ formatNumber(row.endBalance) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="documentCount" :label="$tf('单据数')" width="80" align="right" sortable>
              <template #default="{ row }">
                <span class="cell-number">{{ formatInt(row.documentCount) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="lastTransactionDate" :label="$tf('最后交易')" width="110" align="center" />
          </el-table>
        </el-tab-pane>

        <!-- 明细统计页签 -->
        <el-tab-pane :label="$tf('明细统计')" name="detail">
          <el-table
            :data="detailData"
            border
            size="small"
            v-loading="loading"
            :max-height="tableMaxHeight"
            show-summary
            :summary-method="detailSummaryMethod"
          >
            <el-table-column type="index" label="#" width="40" fixed="left" align="center" />
            <el-table-column prop="billDate" :label="$tf('日期')" width="75" fixed="left" align="center" sortable />
            <el-table-column prop="billNo" :label="$tf('订单号')" width="120" show-overflow-tooltip>
              <template #default="{ row }">
                <!-- 订单编号可直接打开采购订单（billDocType/billId 由后端下发，统一走 utils/billRoute.js） -->
                <span
                  :class="['bill-no-text', billJumpPath(row) ? 'is-link' : 'no-link']"
                  @click="goToBill(row)"
                >{{ row.billNo }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="supplierCode" :label="$tf('供应商编号')" width="70" />
            <el-table-column prop="supplierName" :label="$tf('供应商名称')" min-width="90" show-overflow-tooltip />
            <el-table-column prop="orderAmount" :label="$tf('订单金额')" width="80" align="right" sortable>
              <template #default="{ row }">
                <span class="cell-amount">¥{{ formatNumber(row.orderAmount) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="paidAmount" :label="$tf('订单已付')" width="80" align="right" sortable>
              <template #default="{ row }">
                <span class="cell-amount">¥{{ formatNumber(row.paidAmount) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="unpaidAmount" :label="$tf('订单未付')" width="80" align="right" sortable>
              <template #default="{ row }">
                <span class="cell-amount">¥{{ formatNumber(row.unpaidAmount) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="receiptAmount" :label="$tf('入库金额')" width="80" align="right" sortable>
              <template #default="{ row }">
                <span class="cell-amount">¥{{ formatNumber(row.receiptAmount) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="receiptPaidAmount" :label="$tf('入库已付')" width="80" align="right" sortable>
              <template #default="{ row }">
                <span class="cell-amount">¥{{ formatNumber(row.receiptPaidAmount) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="receiptUnpaidAmount" :label="$tf('入库未付')" width="80" align="right" sortable>
              <template #default="{ row }">
                <span class="cell-amount">¥{{ formatNumber(row.receiptUnpaidAmount) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="invoiceAmount" :label="$tf('发票金额')" width="80" align="right" sortable>
              <template #default="{ row }">
                <span class="cell-amount">¥{{ formatNumber(row.invoiceAmount) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="invoicePaidAmount" :label="$tf('发票已付')" width="80" align="right" sortable>
              <template #default="{ row }">
                <span class="cell-amount">¥{{ formatNumber(row.invoicePaidAmount) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="invoiceUnpaidAmount" :label="$tf('发票未付')" width="80" align="right" sortable>
              <template #default="{ row }">
                <span class="cell-amount">¥{{ formatNumber(row.invoiceUnpaidAmount) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="returnAmount" :label="$tf('退货金额')" width="80" align="right" sortable>
              <template #default="{ row }">
                <span class="cell-amount">¥{{ formatNumber(row.returnAmount) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="remark" :label="$tf('备注')" min-width="60" show-overflow-tooltip />
          </el-table>

          <!-- 分页 -->
          <el-pagination
            class="pagination"
            v-model:current-page="detailPage.current"
            v-model:page-size="detailPage.size"
            :page-sizes="[50, 100, 200, 500]"
            :total="detailPage.total"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadDetail"
            @current-change="loadDetail"
          />
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Download } from '@element-plus/icons-vue'
import { querySupplierStatementSummary, querySupplierStatementDetail, exportSupplierStatement } from '@/api/report/standardReport'
import { resolveBillRoute } from '@/utils/billRoute'
import { tt } from '@/i18n'

const router = useRouter()

/** 对账行 → 目标单据编辑页路由（后端已下发 billDocType/billId；无独立编辑页者返回 null） */
function billJumpPath(row) {
  return resolveBillRoute(row && row.billDocType, row && row.billId)
}

function goToBill(row) {
  const path = billJumpPath(row)
  // 单据已删除/无独立编辑页时给明确提示（2026-09-28：不再"点了没反应"）
  if (!path) { ElMessage.warning(tt('该单据已删除或无法打开')); return }
  router.push(path)
}

const filters = reactive({
  dateRange: [],
  keyword: '',
  supplierCode: '',
  supplierName: ''
})

const loading = ref(false)
const activeTab = ref('summary')
const tableMaxHeight = ref(500)

const summaryData = ref([])
const detailData = ref([])
const detailPage = reactive({ current: 1, size: 100, total: 0 })

const summaryFields = [
  'beginBalance',
  'purchaseOrderAmount', 'purchaseOrderPaidAmount', 'purchaseOrderUnpaidAmount',
  'purchaseReceiptAmount', 'purchaseReceiptPaidAmount', 'purchaseReceiptUnpaidAmount',
  'purchaseInvoiceAmount', 'purchaseInvoicePaidAmount', 'purchaseInvoiceUnpaidAmount',
  'purchaseReturnAmount', 'currentPayable', 'currentPaid', 'currentUnpaid',
  'endBalance', 'documentCount'
]

function getParams() {
  const params = {}
  if (filters.dateRange?.length === 2) {
    params.startDate = filters.dateRange[0]
    params.endDate = filters.dateRange[1]
  }
  if (filters.keyword) params.keyword = filters.keyword
  if (filters.supplierCode) params.supplierCode = filters.supplierCode
  if (filters.supplierName) params.supplierName = filters.supplierName
  return params
}

function loadSummary() {
  loading.value = true
  const params = getParams()
  querySupplierStatementSummary(params)
    .then(res => {
      const payload = res || {}
      summaryData.value = payload.records || []
    })
    .catch(() => {
      // 接口不可用/网络异常：清空数据，不展示虚假数据，仅提示用户
      summaryData.value = []
      ElMessage.error(tt('数据加载失败，请检查网络连接或联系管理员'))
    })
    .finally(() => { loading.value = false })
}

function loadDetail() {
  loading.value = true
  const params = {
    page: detailPage.current,
    size: detailPage.size,
    ...getParams()
  }
  querySupplierStatementDetail(params)
    .then(res => {
      const payload = res || {}
      detailData.value = payload.records || []
      detailPage.total = payload.total || 0
    })
    .catch(() => {
      // 清空数据，不显示模拟数据
      detailData.value = []
      detailPage.total = 0
      
      // 显示友好的错误提示
      ElMessage.error(tt('数据加载失败，请检查网络连接或联系管理员'))
    })
    .finally(() => { loading.value = false })
}

function loadAll() {
  detailPage.current = 1
  if (activeTab.value === 'summary') {
    loadSummary()
  } else {
    loadDetail()
  }
}

function onTabChange(tab) {
  if (tab === 'summary') {
    loadSummary()
  } else {
    loadDetail()
  }
}

function resetFilter() {
  filters.dateRange = []
  filters.keyword = ''
  filters.supplierCode = ''
  filters.supplierName = ''
  detailPage.current = 1
  loadAll()
}

function onSummaryRowClick(row) {
  filters.supplierCode = row.supplierCode || ''
  filters.supplierName = row.supplierName || ''
  activeTab.value = 'detail'
  detailPage.current = 1
  loadDetail()
  ElMessage.info(`${tt("已切换至明细统计：")}${row.supplierName || row.supplierCode || ''}`)
}

function handleExport() {
  const params = getParams()
  exportSupplierStatement(params)
    .then(res => {
      const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = '供应商对账单.xlsx'
      a.click()
      URL.revokeObjectURL(url)
      ElMessage.success(tt('导出成功'))
    })
    .catch(e => ElMessage.error(e.message || tt('导出失败')))
}

function formatNumber(val) {
  if (val === null || val === undefined) return '-'
  if (typeof val === 'number') return val.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  return val.toString()
}

function formatInt(val) {
  if (val === null || val === undefined) return '-'
  return Number(val).toLocaleString('zh-CN')
}

function summaryMethod({ columns, data }) {
  const sums = []
  columns.forEach((col, idx) => {
    if (idx === 0) { sums[idx] = '合计'; return }
    const field = col.property
    if (summaryFields.includes(field)) {
      const sum = data.reduce((acc, row) => acc + Number(row[field] || 0), 0)
      sums[idx] = field === 'documentCount' ? sum.toLocaleString('zh-CN') : `¥${sum.toLocaleString('zh-CN', { minimumFractionDigits: 2 })}`
    } else {
      sums[idx] = ''
    }
  })
  return sums
}

const detailSummaryFields = [
  'orderAmount', 'paidAmount', 'unpaidAmount',
  'receiptAmount', 'receiptPaidAmount', 'receiptUnpaidAmount',
  'invoiceAmount', 'invoicePaidAmount', 'invoiceUnpaidAmount',
  'returnAmount'
]
function detailSummaryMethod({ columns, data }) {
  const sums = []
  columns.forEach((col, idx) => {
    if (idx === 0) { sums[idx] = '合计'; return }
    const field = col.property
    if (detailSummaryFields.includes(field)) {
      const sum = data.reduce((acc, row) => acc + Number(row[field] || 0), 0)
      sums[idx] = `¥${sum.toLocaleString('zh-CN', { minimumFractionDigits: 2 })}`
    } else {
      sums[idx] = ''
    }
  })
  return sums
}

function handleResize() {
  tableMaxHeight.value = window.innerHeight - 380
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
  handleResize()
  loadSummary()
})

onUnmounted(() => window.removeEventListener('resize', handleResize))
</script>

<style scoped lang="scss">
/* 单据编号：能跳到目标单据时是链接样式，否则普通文本（单据已删/无独立编辑页） */
.bill-no-text {
  &.is-link {
    color: #6366f1;
    font-weight: 500;
    cursor: pointer;
    &:hover { color: #8b5cf6; text-decoration: underline; }
  }
  &.no-link { color: #374151; cursor: default; }
}
.statement-page {
  .filter-section {
    background: #fff;
    border-radius: 8px;
    padding: 16px;
    margin-bottom: 16px;
    box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  }
  .filter-row {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 12px;
    margin-bottom: 16px;
  }
  .filter-item {
    flex: 0 0 auto;
    display: flex;
    align-items: center;
    :deep(.el-input),
    :deep(.el-select),
    :deep(.el-date-editor) {
      width: 180px;
    }
    &.date-range-item {
      :deep(.el-date-editor) { width: 280px; }
    }
  }
  .filter-actions {
    display: flex;
    justify-content: flex-start;
    align-items: center;
    gap: 8px;
  }

  .data-header {
    display: flex;
    justify-content: flex-start;
    align-items: baseline;
    gap: 12px;
    margin-bottom: 16px;
    padding-bottom: 12px;
    border-bottom: 1px solid #e4e7ed;
  }
  .report-sub {
    font-size: 13px;
    color: #909399;
  }
  .cell-amount {
    color: #f56c6c;
    font-family: 'Microsoft YaHei', sans-serif;
  }
  .cell-number {
    color: #303133;
    font-family: 'Microsoft YaHei', sans-serif;
  }
  .text-danger {
    color: #f56c6c;
    font-weight: 600;
  }
  .pagination {
    margin-top: 16px;
    justify-content: flex-end;
  }
  :deep(.el-tabs__header) {
    margin-bottom: 0;
  }
}
</style>
