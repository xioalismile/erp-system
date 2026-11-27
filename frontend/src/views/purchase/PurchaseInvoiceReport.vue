<template>
  <div class="report-container">
    <!-- 顶部页面标题 -->
    <div class="page-header">
      <h2 class="page-title">{{ $tt("采购发票报表") }}</h2>
      <p class="page-subtitle">{{ $tt("供应商发票分析 · 发票付款状态监控 · 采购税务统计") }}</p>
      <!-- 全局筛选栏：日期范围 + 关键字 -->
      <div class="filter-bar" style="margin-top: 12px;">
        <el-date-picker v-model="dateRange" type="daterange" :range-separator="$tt('至')"
          :start-placeholder="$tt('开始日期')" :end-placeholder="$tt('结束日期')"
          value-format="YYYY-MM-DD" unlink-panels clearable style="width: 280px" />
        <el-input v-model="keyword" :placeholder="$tt('搜索关键字（供应商/发票号/订单号）')"
          clearable style="width: 240px" @keyup.enter="loadAll" />
        <el-button type="primary" :loading="loadingAll" @click="loadAll">
          <el-icon><Search /></el-icon>查询
        </el-button>
        <el-button @click="resetFilter">{{ $tt("重置") }}</el-button>
      </div>
    </div>

    <!-- 报表1：供应商发票分析（饼图占比 + 表格） -->
    <el-card shadow="never" class="report-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $tt("供应商发票分析") }}</span>
          <div class="header-actions">
            <el-button type="primary" link :loading="loading.supplier" @click="loadSupplierAnalysis">{{ $tt("刷新") }}</el-button>
            <el-button size="small" type="primary" plain @click="openDetail('supplier-invoice')">
              <el-icon><View /></el-icon> 查看明细
            </el-button>
          </div>
        </div>
      </template>
      <el-row :gutter="12">
        <el-col :span="10">
          <!-- 饼图：各供应商发票金额占比 -->
          <div ref="supplierPieChartRef" class="chart-box" style="height: 360px;" />
        </el-col>
        <el-col :span="14">
          <el-table :data="supplierInvoiceList" stripe border size="small" max-height="360">
            <el-table-column prop="supplierName" :label="$tf('供应商')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="invoiceCount" :label="$tf('发票数')" width="90" align="right" />
            <el-table-column :label="$tf('发票金额')" width="140" align="right">
              <template #default="{ row }">¥{{ formatMoney(row.totalAmount) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('占比')" width="100" align="right">
              <template #default="{ row }">{{ (Number(row.percentage) || 0).toFixed(2) }}%</template>
            </el-table-column>
            <el-table-column :label="$tf('平均税率')" width="120" align="center">
              <template #default="{ row }">{{ (Number(row.avgTaxRate) || 0).toFixed(1) }}%</template>
            </el-table-column>
          </el-table>
        </el-col>
      </el-row>
    </el-card>

    <!-- 报表2：发票付款状态（KPI卡片 + 状态分布柱状图） -->
    <el-card shadow="never" class="report-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $tt("发票付款状态") }}</span>
          <div class="header-actions">
            <el-button type="primary" link :loading="loading.payment" @click="loadPaymentStatus">{{ $tt("刷新") }}</el-button>
            <el-button size="small" type="primary" plain @click="openDetail('payment-status')">
              <el-icon><View /></el-icon> 查看明细
            </el-button>
          </div>
        </div>
      </template>
      <!-- KPI卡片行：发票数/金额/未付款/已付款 -->
      <el-row :gutter="12" class="kpi-row">
        <el-col :span="6" v-for="kpi in paymentKpis" :key="kpi.label">
          <div class="kpi-card" :style="{ background: kpi.bg }">
            <div class="kpi-label">{{ $tf(kpi.label) }}</div>
            <div class="kpi-value" :style="{ color: kpi.color }">{{ kpi.value }}</div>
            <div class="kpi-hint">{{ $tt(kpi.hint) }}</div>
          </div>
        </el-col>
      </el-row>
      <!-- 状态分布柱状图 -->
      <div ref="paymentBarChartRef" class="chart-box" style="margin-top: 12px;" />
    </el-card>

    <!-- 报表3：采购税务分析（税率分布饼图 + KPI统计） -->
    <el-card shadow="never" class="report-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $tt("采购税务分析") }}</span>
          <div class="header-actions">
            <el-button type="primary" link :loading="loading.tax" @click="loadTaxAnalysis">{{ $tt("刷新") }}</el-button>
            <el-button size="small" type="primary" plain @click="openDetail('tax-analysis')">
              <el-icon><View /></el-icon> 查看明细
            </el-button>
          </div>
        </div>
      </template>
      <el-row :gutter="12">
        <el-col :span="12">
          <!-- 税率分布饼图 -->
          <div ref="taxPieChartRef" class="chart-box" style="height: 320px;" />
        </el-col>
        <el-col :span="12">
          <!-- 税务KPI统计 -->
          <div class="tax-stats">
            <div class="tax-stat-item" v-for="stat in taxStats" :key="stat.label">
              <div class="stat-label">{{ $tf(stat.label) }}</div>
              <div class="stat-value" :style="{ color: stat.color }">{{ stat.value }}</div>
              <div class="stat-hint">{{ $tt(stat.hint) }}</div>
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 共享明细数据抽屉 -->
    <el-drawer
      v-model="detailDrawerVisible"
      :title="$tt(currentReportTitle) + ' - ' + $tt('明细数据')"
      direction="rtl"
      size="70%"
      :close-on-click-modal="false"
    >
      <div class="detail-drawer">
        <div class="detail-filter">
          <el-date-picker v-model="detailDateRange" type="daterange" :range-separator="$tt('至')"
            :start-placeholder="$tt('开始日期')" :end-placeholder="$tt('结束日期')"
            value-format="YYYY-MM-DD" unlink-panels clearable style="width: 280px" />
          <el-input v-model="detailKeyword" :placeholder="$tt('搜索关键字')" clearable
            style="width: 220px" @keyup.enter="loadDetail" />
          <el-button type="primary" :loading="detailLoading" @click="loadDetail">
            <el-icon><Search /></el-icon>查询
          </el-button>
          <el-button @click="resetDetail">{{ $tt("重置") }}</el-button>
          <el-button type="success" plain :loading="detailLoading" @click="handleExportDetail">
            <el-icon><Download /></el-icon>导出
          </el-button>
        </div>
        <el-table :data="detailData" v-loading="detailLoading" border stripe size="small"
          max-height="560" style="margin-top: 12px;">
          <template v-if="currentReportKey === 'supplier-invoice'">
            <el-table-column prop="supplierName" :label="$tf('供应商名称')" min-width="180" show-overflow-tooltip />
            <el-table-column prop="invoiceCount" :label="$tf('发票数')" width="100" align="right" />
            <el-table-column :label="$tf('发票金额')" align="right" width="140">
              <template #default="{ row }">¥{{ fmt(row.totalAmount) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('平均税率')" align="right" width="120">
              <template #default="{ row }">{{ fmt(row.avgTaxRate) }}%</template>
            </el-table-column>
          </template>
          <template v-else-if="currentReportKey === 'payment-status'">
            <el-table-column prop="invoiceNo" :label="$tf('发票号')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="supplierName" :label="$tf('供应商')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="invoiceDate" :label="$tf('开票日期')" width="110" />
            <el-table-column prop="dueDate" :label="$tf('到期日期')" width="110" />
            <el-table-column prop="paymentStatus" :label="$tf('付款状态')" width="100" align="center" />
            <el-table-column :label="$tf('发票金额')" align="right" width="140">
              <template #default="{ row }">¥{{ fmt(row.amount) }}</template>
            </el-table-column>
          </template>
          <template v-else-if="currentReportKey === 'tax-analysis'">
            <el-table-column prop="taxRate" :label="$tf('税率')" width="100" align="right" />
            <el-table-column prop="invoiceCount" :label="$tf('发票数')" width="100" align="right" />
            <el-table-column :label="$tf('发票金额')" align="right" width="140">
              <template #default="{ row }">¥{{ fmt(row.totalAmount) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('税额')" align="right" width="120">
              <template #default="{ row }">¥{{ fmt(row.taxAmount) }}</template>
            </el-table-column>
            <el-table-column prop="avgAmount" :label="$tf('平均金额')" align="right" width="130">
              <template #default="{ row }">¥{{ fmt(row.avgAmount) }}</template>
            </el-table-column>
          </template>
        </el-table>
        <div class="detail-pagination">
          <el-pagination v-model:current-page="detailPage" v-model:page-size="detailSize"
            :page-sizes="[10, 20, 50, 100]" :total="detailTotal"
            layout="total, sizes, prev, pager, next, jumper" background
            @size-change="loadDetail" @current-change="loadDetail" />
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import echarts from '@/utils/echartsCore'
import { ElMessage } from 'element-plus'
import { View, Download, Search } from '@element-plus/icons-vue'
import { tt } from '@/i18n'
// 采购发票报表相关 API
import {
  purchaseSupplierInvoiceAnalysis,
  purchaseInvoicePaymentStatus,
  purchaseInvoiceTaxAnalysis,
  purchaseInvoiceDetails
} from '@/api/report'
import { exportCsv } from '@/utils/csvExport'

// ============ 公共工具函数 ============
// 金额格式化（保留两位小数 + 千分位）
function formatMoney(num) {
  if (num === null || num === undefined || num === '') return '0.00'
  return Number(num).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
// 明细抽屉复用现有金额格式化
const fmt = formatMoney

// 各报表加载状态
const loading = reactive({ supplier: false, payment: false, tax: false })

// ============ 全局筛选栏（日期范围 + 关键字） ============
const dateRange = ref([])
const keyword = ref('')
const loadingAll = ref(false)

// 构造全局筛选参数（供 loadAll 合并进各报表查询）
function buildParams() {
  const p = {}
  if (dateRange.value && dateRange.value.length === 2) {
    p.startDate = dateRange.value[0]
    p.endDate = dateRange.value[1]
  }
  if (keyword.value) p.keyword = keyword.value
  return p
}

function resetFilter() {
  dateRange.value = []
  keyword.value = ''
  loadAll()
}

// 一键查询：并行加载三张报表，将全局筛选参数合并进各报表查询
async function loadAll() {
  loadingAll.value = true
  try {
    const params = buildParams()
    await Promise.allSettled([
      loadSupplierAnalysis(params),
      loadPaymentStatus(params),
      loadTaxAnalysis(params)
    ])
  } finally {
    loadingAll.value = false
  }
}

// ============ 报表1：供应商发票分析 ============
const supplierInvoiceList = ref([])
const supplierPieChartRef = ref(null)
let supplierPieChartInstance = null
const supplierSummary = reactive({ totalAmount: 0, totalInvoices: 0, totalSuppliers: 0 })

async function loadSupplierAnalysis(extraParams) {
  loading.supplier = true
  try {
    const params = extraParams || buildParams()
    const res = await purchaseSupplierInvoiceAnalysis(params)
    const dist = res.supplierDistribution || []
    // 排名表按名称对齐：发票数/平均税率取自 supplierRanking
    const rankMap = new Map((res.supplierRanking || []).map(r => [r.name, r]))
    supplierInvoiceList.value = dist.map(d => {
      const r = rankMap.get(d.name) || {}
      return {
        supplierName: d.name,
        invoiceCount: r.invoices || 0,
        totalAmount: d.value,
        percentage: d.percentage,
        avgTaxRate: r.avgTaxRate || 0
      }
    })
    supplierSummary.totalAmount = res.totalAmount || 0
    supplierSummary.totalInvoices = res.totalInvoices || 0
    supplierSummary.totalSuppliers = res.totalSuppliers || 0
    await nextTick()
    renderSupplierPieChart()
  } catch (e) {
    ElMessage.error(tt('加载供应商发票分析失败'))
  } finally {
    loading.supplier = false
  }
}

// 渲染供应商发票金额占比饼图
function renderSupplierPieChart() {
  if (!supplierPieChartRef.value) return
  if (!supplierPieChartInstance) {
    supplierPieChartInstance = echarts.init(supplierPieChartRef.value)
  }
  const data = supplierInvoiceList.value.map(s => ({
    name: s.supplierName || `供应商${s.supplierId}`,
    value: Number(s.totalAmount) || 0
  }))
  supplierPieChartInstance.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: ¥{c} ({d}%)' },
    legend: { type: 'scroll', orient: 'vertical', right: '2%', top: 'middle' },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['40%', '50%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        emphasis: { label: { show: true, fontSize: 14, fontWeight: 'bold' } },
        data
      }
    ]
  }, true)
}

// ============ 报表2：发票付款状态 ============
const paymentData = ref({})
const paymentBarChartRef = ref(null)
let paymentBarChartInstance = null

// KPI卡片配色（应付/已付/未付/逾期四张卡一一对应）
const kpiColors = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C']
const kpiBgs = ['#ECF5FF', '#F0F9EB', '#FDF6EC', '#FEF0F0']

// KPI卡片配置
const paymentKpis = ref([
  { label: '应付总额', value: '0.00', hint: '环比 +0.0%', color: '#409EFF', bg: '#ECF5FF' },
  { label: '已付款', value: '0.00', hint: '环比 +0.0%', color: '#67C23A', bg: '#F0F9EB' },
  { label: '未付款', value: '0.00', hint: '环比 +0.0%', color: '#E6A23C', bg: '#FDF6EC' },
  { label: '逾期金额', value: '0.00', hint: '环比 +0.0%', color: '#F56C6C', bg: '#FEF0F0' }
])

async function loadPaymentStatus(extraParams) {
  loading.payment = true
  try {
    const params = extraParams || buildParams()
    const res = await purchaseInvoicePaymentStatus(params)
    paymentData.value = res
    // KPI直接使用后端 kpiCards（value为千分位字符串直接展示，change为真实环比）
    paymentKpis.value = (res.kpiCards || []).map((c, i) => ({
      label: c.title,
      value: c.value,
      hint: '环比 ' + (c.change || '+0.0%'),
      color: kpiColors[i % kpiColors.length],
      bg: kpiBgs[i % kpiBgs.length]
    }))
    await nextTick()
    renderPaymentBarChart()
  } catch (e) {
    ElMessage.error(tt('加载发票付款状态失败'))
  } finally {
    loading.payment = false
  }
}

// 渲染发票付款状态分布柱状图
function renderPaymentBarChart() {
  if (!paymentBarChartRef.value) return
  if (!paymentBarChartInstance) {
    paymentBarChartInstance = echarts.init(paymentBarChartRef.value)
  }
  const list = paymentData.value.paymentStatus || []
  const names = list.map(i => i.status)
  const values = list.map(i => Number(i.count) || 0)
  paymentBarChartInstance.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '12%', containLabel: true },
    xAxis: {
      type: 'category',
      data: names,
      axisLabel: { interval: 0, rotate: names.length > 6 ? 30 : 0 }
    },
    yAxis: { type: 'value', name: '发票数' },
    series: [
      {
        type: 'bar',
        data: values,
        barWidth: '50%',
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#409EFF' },
            { offset: 1, color: '#79bbff' }
          ]),
          borderRadius: [4, 4, 0, 0]
        }
      }
    ]
  }, true)
}

// ============ 报表3：采购税务分析 ============
const taxData = ref({})
const taxPieChartRef = ref(null)
let taxPieChartInstance = null

// 税务KPI统计
const taxStats = ref([
  { label: '总税额', value: '¥0.00', hint: '累计税额', color: '#409EFF' },
  { label: '平均税率', value: '0%', hint: '所有发票平均税率', color: '#67C23A' },
  { label: '税额占比', value: '0%', hint: '税额占总金额比例', color: '#E6A23C' },
  { label: '最高税率', value: '0%', hint: '最高税率档位', color: '#F56C6C' }
])

async function loadTaxAnalysis(extraParams) {
  loading.tax = true
  try {
    const params = extraParams || buildParams()
    const res = await purchaseInvoiceTaxAnalysis(params)
    taxData.value = res
    const dist = res.taxDistribution || []
    // 税务KPI：全部由真实分布数据计算
    const totalTax = dist.reduce((sum, item) => sum + (Number(item.tax) || 0), 0)
    const totalAmount = dist.reduce((sum, item) => sum + (Number(item.amount) || 0), 0)
    taxStats.value[0].value = '¥' + formatMoney(totalTax)
    // 平均税率 = 税额合计/金额合计（不含税）
    taxStats.value[1].value = totalAmount > 0 ? (totalTax / totalAmount * 100).toFixed(1) + '%' : '0%'
    // 税额占比 = 税额/价税合计
    const gross = totalAmount + totalTax
    taxStats.value[2].value = gross > 0 ? (totalTax / gross * 100).toFixed(1) + '%' : '0%'
    // 最高税率档
    const maxRate = dist.reduce((m, item) => {
      const v = parseFloat(item.rate)
      return isNaN(v) ? m : Math.max(m, v)
    }, 0)
    taxStats.value[3].value = maxRate > 0 ? maxRate + '%' : '0%'
    await nextTick()
    renderTaxPieChart()
  } catch (e) {
    ElMessage.error(tt('加载采购税务分析失败'))
  } finally {
    loading.tax = false
  }
}

// 渲染税率分布饼图（按税额占比）
function renderTaxPieChart() {
  if (!taxPieChartRef.value) return
  if (!taxPieChartInstance) {
    taxPieChartInstance = echarts.init(taxPieChartRef.value)
  }
  const dist = taxData.value.taxDistribution || []
  const data = dist.map(d => ({
    name: d.rate,
    value: Number(d.tax) || 0
  }))
  taxPieChartInstance.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { type: 'scroll', orient: 'vertical', right: '2%', top: 'middle' },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['40%', '50%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        emphasis: { label: { show: true, fontSize: 14, fontWeight: 'bold' } },
        data
      }
    ]
  }, true)
}

// ============ 明细数据抽屉（三张报表共享） ============
const detailDrawerVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref([])
const detailTotal = ref(0)
const detailPage = ref(1)
const detailSize = ref(20)
const detailKeyword = ref('')
const detailDateRange = ref([])
const currentReportKey = ref('')
const currentReportTitle = ref('')

const reportTitleMap = {
  'supplier-invoice': '供应商发票分析',
  'payment-status': '发票付款状态',
  'tax-analysis': '采购税务分析'
}

// 打开明细抽屉：继承全局筛选条件，重置分页后加载
function openDetail(key) {
  currentReportKey.value = key
  currentReportTitle.value = reportTitleMap[key] || ''
  detailDateRange.value = dateRange.value && dateRange.value.length === 2 ? [...dateRange.value] : []
  detailKeyword.value = keyword.value || ''
  detailPage.value = 1
  detailSize.value = 20
  detailDrawerVisible.value = true
  loadDetail()
}

// 加载明细数据：发票付款状态用后端分页，其余报表数据量小走客户端分页
async function loadDetail() {
  detailLoading.value = true
  try {
    // 明细抽屉筛选参数
    const base = {}
    if (detailDateRange.value && detailDateRange.value.length === 2) {
      base.startDate = detailDateRange.value[0]
      base.endDate = detailDateRange.value[1]
    }
    if (detailKeyword.value) base.keyword = detailKeyword.value

    if (currentReportKey.value === 'payment-status') {
      // 发票明细（含付款状态/到期日）：后端分页
      const res = await purchaseInvoiceDetails({ ...base, current: detailPage.value, size: detailSize.value })
      detailData.value = (res.list || []).map(item => ({
        invoiceNo: item.invoiceNo,
        supplierName: item.supplierName,
        invoiceDate: item.date,
        dueDate: item.dueDate,
        paymentStatus: item.paymentStatus,
        amount: item.total
      }))
      detailTotal.value = res.pagination ? res.pagination.total : 0
      return
    }

    let rows = []
    if (currentReportKey.value === 'supplier-invoice') {
      const res = await purchaseSupplierInvoiceAnalysis(base)
      rows = (res.supplierRanking || []).map(item => ({
        supplierId: item.rank,
        supplierName: item.name,
        invoiceCount: item.invoices,
        totalAmount: item.amount,
        avgTaxRate: item.avgTaxRate || 0
      }))
    } else if (currentReportKey.value === 'tax-analysis') {
      const res = await purchaseInvoiceTaxAnalysis(base)
      rows = (res.taxDistribution || []).map(item => ({
        taxRate: item.rate,
        invoiceCount: item.count,
        totalAmount: item.amount,
        taxAmount: item.tax,
        // 平均金额 = 金额/发票数（真实计算）
        avgAmount: Number(item.count) > 0 ? (Number(item.amount) || 0) / Number(item.count) : 0
      }))
    }
    detailTotal.value = rows.length
    const start = (detailPage.value - 1) * detailSize.value
    detailData.value = rows.slice(start, start + detailSize.value)
  } catch (e) {
    console.error('加载明细失败', e)
    ElMessage.error(tt('加载明细失败'))
    detailData.value = []
    detailTotal.value = 0
  } finally {
    detailLoading.value = false
  }
}

function resetDetail() {
  detailDateRange.value = []
  detailKeyword.value = ''
  detailPage.value = 1
  loadDetail()
}

// 导出当前明细抽屉数据（与 loadDetail 同源映射，全量拉取）
async function handleExportDetail() {
  try {
    detailLoading.value = true
    const base = {}
    if (detailDateRange.value && detailDateRange.value.length === 2) {
      base.startDate = detailDateRange.value[0]
      base.endDate = detailDateRange.value[1]
    }
    if (detailKeyword.value) base.keyword = detailKeyword.value

    let rows = []
    let columns = []
    if (currentReportKey.value === 'payment-status') {
      // 发票明细（含付款状态/到期日）：后端分页，导出拉全量
      const res = await purchaseInvoiceDetails({ ...base, current: 1, size: 9999 })
      rows = (res.list || []).map(item => ({
        invoiceNo: item.invoiceNo,
        supplierName: item.supplierName,
        invoiceDate: item.date,
        dueDate: item.dueDate,
        paymentStatus: item.paymentStatus,
        amount: item.total
      }))
      columns = [
        { label: '发票号', prop: 'invoiceNo' },
        { label: '供应商', prop: 'supplierName' },
        { label: '开票日期', prop: 'invoiceDate' },
        { label: '到期日期', prop: 'dueDate' },
        { label: '付款状态', prop: 'paymentStatus' },
        { label: '发票金额', prop: 'amount' }
      ]
    } else if (currentReportKey.value === 'supplier-invoice') {
      const res = await purchaseSupplierInvoiceAnalysis(base)
      rows = (res.supplierRanking || []).map(item => ({
        supplierId: item.rank,
        supplierName: item.name,
        invoiceCount: item.invoices,
        totalAmount: item.amount,
        avgTaxRate: item.avgTaxRate || 0
      }))
      columns = [
        { label: '供应商名称', prop: 'supplierName' },
        { label: '发票数', prop: 'invoiceCount' },
        { label: '发票金额', prop: 'totalAmount' },
        { label: '平均税率(%)', prop: 'avgTaxRate' }
      ]
    } else if (currentReportKey.value === 'tax-analysis') {
      const res = await purchaseInvoiceTaxAnalysis(base)
      rows = (res.taxDistribution || []).map(item => ({
        taxRate: item.rate,
        invoiceCount: item.count,
        totalAmount: item.amount,
        taxAmount: item.tax,
        avgAmount: Number(item.count) > 0 ? (Number(item.amount) || 0) / Number(item.count) : 0
      }))
      columns = [
        { label: '税率', prop: 'taxRate' },
        { label: '发票数', prop: 'invoiceCount' },
        { label: '发票金额', prop: 'totalAmount' },
        { label: '税额', prop: 'taxAmount' },
        { label: '平均金额', prop: 'avgAmount' }
      ]
    } else {
      ElMessage.warning(tt('当前无可导出的报表数据'))
      return
    }

    if (!rows.length) { ElMessage.warning(tt('没有可导出的数据')); return }
    exportCsv(columns, rows, currentReportTitle.value || '采购发票明细')
    ElMessage.success(`${tt("已导出 ")}${rows.length}${tt(" 条数据")}`)
  } catch (e) {
    console.error('导出失败', e)
    ElMessage.error(e.message || tt('导出失败'))
  } finally {
    detailLoading.value = false
  }
}

// ============ 窗口 resize 与生命周期管理 ============
function handleResize() {
  supplierPieChartInstance?.resize()
  paymentBarChartInstance?.resize()
  taxPieChartInstance?.resize()
}

onMounted(() => {
  loadSupplierAnalysis()
  loadPaymentStatus()
  loadTaxAnalysis()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  // 销毁所有图表实例，避免内存泄漏
  supplierPieChartInstance?.dispose()
  paymentBarChartInstance?.dispose()
  taxPieChartInstance?.dispose()
  supplierPieChartInstance = null
  paymentBarChartInstance = null
  taxPieChartInstance = null
})
</script>

<style scoped>
.report-container { padding: 20px; background: #f5f7fa; }
.page-header { margin-bottom: 16px; }
.page-title { margin: 0; font-size: 22px; font-weight: 600; color: #303133; }
.page-subtitle { margin: 6px 0 0; font-size: 13px; color: #909399; }

.report-card { margin-bottom: 16px; border-radius: 8px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-size: 16px; font-weight: 600; color: #303133; }
.header-actions { display: flex; gap: 8px; align-items: center; }

.chart-box { width: 100%; height: 320px; }

.kpi-row { margin-bottom: 4px; }
.kpi-card {
  border-radius: 8px;
  padding: 16px 18px;
  border: 1px solid #ebeef5;
}
.kpi-label { color: #909399; font-size: 13px; }
.kpi-value { font-size: 24px; font-weight: 600; margin: 4px 0; }
.kpi-hint { color: #C0C4CC; font-size: 12px; }

/* 税务统计样式 */
.tax-stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  height: 320px;
}
.tax-stat-item {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  border: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.stat-label { color: #909399; font-size: 13px; margin-bottom: 8px; }
.stat-value { font-size: 20px; font-weight: 600; margin-bottom: 4px; }
.stat-hint { color: #C0C4CC; font-size: 12px; }

/* 全局筛选栏 */
.filter-bar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }

/* 明细抽屉 */
.detail-drawer { padding: 0 16px 16px; }
.detail-filter {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.detail-pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>