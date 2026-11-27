<template>
  <div class="ap-summary-page">
    <!-- 顶部工具栏 -->
    <div class="page-header">
      <div class="header-left">
        <div class="title-area">
          <el-icon class="title-icon"><Wallet /></el-icon>
          <span class="page-title">{{ $tt("应付账款汇总表") }}</span>
          <span class="page-subtitle">{{ $tt("按供应商汇总应付账款期初、本期发生及结余") }}</span>
        </div>
      </div>
      <div class="header-right">
        <el-button size="small" @click="loadData" :loading="loading">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
        <el-button size="small" type="success" :loading="exporting" @click="handleExport">
          <el-icon><Download /></el-icon>导出
        </el-button>
        <el-button size="small" type="primary" plain @click="openColumnSetting">
          <el-icon><Setting /></el-icon>列设置
        </el-button>
      </div>
    </div>

    <!-- 搜索区 -->
    <div class="filter-section">
      <div class="filter-row">
        <el-date-picker
          v-model="filters.dateRange"
          type="daterange"
          range-separator="~"
          :start-placeholder="$tt('开始日期')"
          :end-placeholder="$tt('结束日期')"
          value-format="YYYY-MM-DD"
          unlink-panels
          clearable
          style="width: 280px"
        />
        <el-select
          v-model="filters.currencyId"
          :placeholder="$tt('币别')"
          clearable
          style="width: 160px"
        >
          <el-option v-for="c in currencies" :key="c.currencyId" :label="c.currencyCode" :value="c.currencyId" />
        </el-select>
        <el-select
          v-model="filters.supplierId"
          :placeholder="$tt('供应商')"
          filterable
          clearable
          style="width: 220px"
        >
          <el-option v-for="s in suppliers" :key="s.supplierId" :label="s.supplierName" :value="s.supplierId" />
        </el-select>
        <el-select
          v-model="filters.supplierType"
          :placeholder="$tt('供应商类型')"
          clearable
          style="width: 160px"
        >
          <el-option :label="$tf('企业供应商')" value="企业供应商" />
          <el-option :label="$tf('个人供应商')" value="个人供应商" />
        </el-select>
        <el-button type="primary" @click="loadData">
          <el-icon><Search /></el-icon>查询
        </el-button>
        <el-button @click="resetFilter">{{ $tt("重置") }}</el-button>
      </div>
    </div>

    <!-- 数据区 -->
    <div class="data-section">
      <div class="data-header">
        <span class="report-title">{{ $tt("汇总明细") }}</span>
        <span class="data-tip">共 {{ tableData.length }} 条</span>
      </div>

      <el-table
        :data="tableData"
        border
        size="small"
        v-loading="loading"
        show-summary
        :summary-method="summaryMethod"
        :max-height="tableMaxHeight"
      >
        <el-table-column type="index" label="#" width="50" fixed="left" align="center" />
        <el-table-column
          v-for="col in visibleColumns"
          :key="col.prop"
          :prop="col.prop"
          :label="$tf(col.label)"
          :width="col.width"
          :min-width="col.minWidth || 110"
          :align="col.align || 'left'"
          :fixed="col.fixed || false"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <span v-if="col.type === 'amount'" class="cell-amount">¥{{ formatNumber(row[col.prop]) }}</span>
            <span v-else-if="col.type === 'number'" class="cell-number">{{ formatNumber(row[col.prop]) }}</span>
            <span v-else>{{ row[col.prop] ?? '-' }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 列设置抽屉 -->
    <ColumnSettingDrawer
      v-model="columnSettingVisible"
      :page-key="columnPageKey"
      :columns="columnDefs"
      :title="$tt('列设置')"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Wallet, Refresh, Download, Setting, Search } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { listAllSupplier } from '@/api/basedata/supplier'
import { listAllCurrency } from '@/api/basedata/currency'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
import { useColumnConfig } from '@/composables/useColumnConfig'
import { tt } from '@/i18n'

// ===== 列定义（按供应商汇总） =====
const columnDefs = [
  { prop: 'supplierCode', label: '供应商编号', width: 130, fixed: 'left' },
  { prop: 'supplierName', label: '供应商名称', minWidth: 160, fixed: 'left' },
  { prop: 'supplierType', label: '供应商类型', width: 100, align: 'center' },
  { prop: 'beginPayable', label: '期初应付', width: 130, align: 'right', type: 'amount' },
  { prop: 'beginPrePaid', label: '期初预付', width: 130, align: 'right', type: 'amount' },
  { prop: 'currentPayable', label: '本期应付', width: 130, align: 'right', type: 'amount' },
  { prop: 'currentPaid', label: '本期已付', width: 130, align: 'right', type: 'amount' },
  { prop: 'currentPrePaid', label: '本期预付', width: 130, align: 'right', type: 'amount' },
  { prop: 'endBalanceWithPre', label: '应付结余(含预付)', width: 150, align: 'right', type: 'amount' },
  { prop: 'endBalance', label: '应付结余', width: 130, align: 'right', type: 'amount' }
]

const columnPageKey = 'FinanceApSummary'
const { visibleColumns, columnSettingVisible, openSetting: openColumnSetting } = useColumnConfig(columnPageKey, columnDefs)

// ===== 筛选条件 =====
const filters = reactive({
  dateRange: [],
  currencyId: null,
  supplierId: null,
  supplierType: ''
})

// ===== 下拉数据 =====
const suppliers = ref([])
const currencies = ref([])

// ===== 表格数据 =====
const loading = ref(false)
const exporting = ref(false)
const tableData = ref([])
const tableMaxHeight = ref(500)

// ===== 加载数据 =====
async function loadData() {
  loading.value = true
  const params = {
    startDate: filters.dateRange?.[0] || undefined,
    endDate: filters.dateRange?.[1] || undefined,
    currencyId: filters.currencyId || undefined,
    supplierId: filters.supplierId || undefined,
    supplierType: filters.supplierType || undefined
  }
  Object.keys(params).forEach(k => {
    if (params[k] === undefined || params[k] === null || params[k] === '') delete params[k]
  })
  try {
    // 调用专用 /finance/payable/summary 接口，后端按供应商聚合返回
    const res = await request.get('/finance/payable/summary', { params })
    const list = Array.isArray(res) ? res : (res?.records || [])
    tableData.value = list
  } catch (e) {
    // 后端API异常，使用空数据，避免阻塞页面
    tableData.value = []
    if (e?.message && !/404|Network/.test(e.message)) {
      ElMessage.warning(e.message || tt('汇总数据加载失败'))
    }
  } finally {
    loading.value = false
  }
}

function resetFilter() {
  filters.dateRange = []
  filters.currencyId = null
  filters.supplierId = null
  filters.supplierType = ''
  loadData()
}

// ===== 导出 =====
async function handleExport() {
  if (tableData.value.length === 0) {
    ElMessage.warning(tt('暂无数据可导出'))
    return
  }
  exporting.value = true
  try {
    const params = {
      startDate: filters.dateRange?.[0] || undefined,
      endDate: filters.dateRange?.[1] || undefined,
      currencyId: filters.currencyId || undefined,
      supplierId: filters.supplierId || undefined,
      supplierType: filters.supplierType || undefined
    }
    Object.keys(params).forEach(k => {
      if (params[k] === undefined || params[k] === null || params[k] === '') delete params[k]
    })
    try {
      const blob = await request.get('/finance/payable/summary/export', { params, responseType: 'blob' })
      downloadBlob(blob, '应付账款汇总表.xlsx')
      ElMessage.success(tt('导出成功'))
    } catch (e) {
      // 后端导出非 blob 时，使用前端CSV兜底
      exportCsvFallback()
    }
  } finally {
    exporting.value = false
  }
}

function exportCsvFallback() {
  const headers = visibleColumns.value.map(c => c.label)
  const rows = tableData.value.map(row => visibleColumns.value.map(c => row[c.prop] ?? ''))
  const csv = [headers, ...rows].map(r => r.map(v => `"${String(v).replace(/"/g, '""')}"`).join(',')).join('\n')
  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' })
  downloadBlob(blob, '应付账款汇总表.csv')
  ElMessage.success(tt('已导出 CSV'))
}

function downloadBlob(blob, fileName) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  a.click()
  URL.revokeObjectURL(url)
}

// ===== 工具函数 =====
function formatNumber(val) {
  if (val === null || val === undefined || val === '') return '0.00'
  const num = Number(val)
  if (isNaN(num)) return '0.00'
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function summaryMethod({ columns, data }) {
  const sums = []
  columns.forEach((col, idx) => {
    if (idx === 0) { sums[idx] = '合计'; return }
    // el-table 回传的列对象字段名是 property（不是 prop）；此前取 col.prop 恒为 undefined → 合计行全空
    const field = col.property
    const cfg = columnDefs.find(c => c.prop === field)
    if (cfg?.type === 'amount' || cfg?.type === 'number') {
      const sum = data.reduce((acc, row) => {
        const v = row[field]
        if (v === null || v === undefined) return acc
        return acc + Number(v)
      }, 0)
      sums[idx] = cfg.type === 'amount' ? `¥${formatNumber(sum)}` : formatNumber(sum)
    } else {
      sums[idx] = ''
    }
  })
  return sums
}

function handleResize() {
  tableMaxHeight.value = window.innerHeight - 320
}

onMounted(async () => {
  window.addEventListener('resize', handleResize)
  handleResize()
  try {
    const [supRes, curRes] = await Promise.all([
      listAllSupplier(),
      listAllCurrency()
    ])
    suppliers.value = Array.isArray(supRes) ? supRes : (supRes?.records || [])
    currencies.value = Array.isArray(curRes) ? curRes : (curRes?.records || [])
  } catch (e) {
    // 下拉数据加载失败不阻塞
  }
  await loadData()
})

onUnmounted(() => window.removeEventListener('resize', handleResize))
</script>

<style scoped lang="scss">
.ap-summary-page {
  padding: 12px 16px;
  background: #f5f7fa;
  min-height: calc(100vh - 90px);
  font-size: 12px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding: 10px 14px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .title-area {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .title-icon {
    color: #409eff;
    font-size: 16px;
  }

  .page-title {
    font-size: 15px;
    font-weight: 600;
    color: #303133;
  }

  .page-subtitle {
    font-size: 11px;
    color: #909399;
    margin-left: 6px;
  }

  .header-right {
    display: flex;
    gap: 6px;
  }
}

.filter-section {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 12px 14px;
  margin-bottom: 10px;

  .filter-row {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
  }
}

.data-section {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 12px 14px;

  .data-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
    padding-bottom: 6px;
    border-bottom: 1px solid #ebeef5;

    .report-title {
      font-size: 13px;
      font-weight: 600;
      color: #303133;
    }

    .data-tip {
      font-size: 11px;
      color: #909399;
    }
  }
}

.cell-amount {
  color: #e6a23c;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

.cell-number {
  font-family: 'Monaco', 'Menlo', monospace;
  font-variant-numeric: tabular-nums;
}

:deep(.el-table__header th) {
  background: #f5f7fa !important;
  color: #606266;
  font-weight: 600;
  font-size: 12px;
}

:deep(.el-table__body td) {
  font-size: 12px;
}

:deep(.el-table__footer .el-table__cell) {
  font-weight: 600;
  color: #409eff;
  background: #fafbfc;
}
</style>
