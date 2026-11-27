<template>
  <div class="report-container">
    <!-- 顶部页面标题 -->
    <div class="page-header">
      <div class="header-left">
        <div class="page-title">
          销售发票报表
          <span class="title-badge">SALE INVOICE REPORT</span>
        </div>
        <div class="page-sub">{{ $tt("发票统计分析 · 税务合规监控 · 客户开票趋势") }}</div>
      </div>
      <div class="header-right">
        <el-select v-model="filterYear" :placeholder="$tt('年份')" style="width: 100px" @change="loadAll">
          <el-option v-for="y in yearOptions" :key="y" :label="y + '年'" :value="y" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          :range-separator="$tt('至')"
          :start-placeholder="$tt('开始日期')"
          :end-placeholder="$tt('结束日期')"
          value-format="YYYY-MM-DD"
          unlink-panels
          clearable
          style="width: 280px"
        />
        <el-input
          v-model="keyword"
          :placeholder="$tt('搜索关键字（客户/发票号/订单号）')"
          clearable
          style="width: 220px"
          @keyup.enter="loadAll"
        />
        <el-button type="primary" :loading="loading.all" @click="loadAll">
          <el-icon><Search /></el-icon>查询
        </el-button>
        <el-button @click="resetFilter">{{ $tt("重置") }}</el-button>
      </div>
    </div>

    <!-- 顶部KPI卡片行 -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :xs="24" :sm="12" :md="6" v-for="(card, idx) in kpiCards" :key="idx">
        <el-card shadow="hover" class="kpi-card" :class="card.type" v-loading="loading.kpi">
          <div class="kpi-content">
            <div class="kpi-icon" :style="{ background: card.bgColor, color: card.iconColor }">
              <el-icon :size="24"><component :is="card.icon" /></el-icon>
            </div>
            <div class="kpi-info">
              <div class="kpi-value">{{ card.value }}</div>
              <div class="kpi-label">{{ $tf(card.label) }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 报表网格 -->
    <el-row :gutter="16" class="report-row">
      <!-- 1. 发票统计概览 -->
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover" class="report-card" v-loading="loading.overview">
          <template #header>
            <div class="card-header">
              <div class="title-wrap">
                <span class="card-title">{{ $tt("发票统计概览") }}</span>
                <el-button size="small" type="primary" plain @click="openDetail('invoice-overview')">
                  <el-icon><View /></el-icon> 查看明细
                </el-button>
              </div>
              <span class="card-sub">{{ filterYear.value || '全年' }}年度</span>
            </div>
          </template>
          <el-row :gutter="12">
            <el-col :span="12" v-for="stat in overviewStats" :key="stat.label">
              <div class="overview-stat" :class="stat.type">
                <div class="stat-label">{{ $tf(stat.label) }}</div>
                <div class="stat-value">{{ stat.value }}</div>
                <div class="stat-trend" :class="stat.trend >= 0 ? 'up' : 'down'">
                  <el-icon><component :is="stat.trend >= 0 ? 'ArrowUp' : 'ArrowDown'" /></el-icon>
                  {{ Math.abs(stat.trend) }}%
                </div>
              </div>
            </el-col>
          </el-row>
          <el-divider content-position="left">{{ $tt("发票状态分布") }}</el-divider>
          <div ref="statusChartRef" class="mini-chart" style="height: 240px"></div>
        </el-card>
      </el-col>

      <!-- 2. 发票趋势分析 -->
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover" class="report-card" v-loading="loading.trend">
          <template #header>
            <div class="card-header">
              <div class="title-wrap">
                <span class="card-title">{{ $tt("发票趋势分析") }}</span>
                <el-button size="small" type="primary" plain @click="openDetail('invoice-trend')">
                  <el-icon><View /></el-icon> 查看明细
                </el-button>
              </div>
              <el-radio-group v-model="trendPeriod" size="small" @change="loadTrend">
                <el-radio-button value="month">{{ $tt("月度") }}</el-radio-button>
                <el-radio-button value="quarter">{{ $tt("季度") }}</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div ref="trendChartRef" class="mini-chart" style="height: 320px"></div>
        </el-card>
      </el-col>

      <!-- 3. 客户发票分析 -->
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover" class="report-card" v-loading="loading.customer">
          <template #header>
            <div class="card-header">
              <div class="title-wrap">
                <span class="card-title">{{ $tt("客户发票分析") }}</span>
                <el-button size="small" type="primary" plain @click="openDetail('customer-analysis')">
                  <el-icon><View /></el-icon> 查看明细
                </el-button>
              </div>
              <el-select v-model="customerTopN" size="small" style="width: 100px" @change="loadCustomer">
                <el-option label="Top 5" :value="5" />
                <el-option label="Top 10" :value="10" />
                <el-option label="Top 20" :value="20" />
              </el-select>
            </div>
          </template>
          <div ref="customerChartRef" class="mini-chart" style="height: 320px"></div>
          <div class="customer-summary">
            <span>合计金额：¥{{ fmt(customerData.totalAmount) }}</span>
            <span>合计发票：{{ customerData.totalInvoices || 0 }} 张</span>
          </div>
        </el-card>
      </el-col>

      <!-- 4. 税务分析 -->
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover" class="report-card" v-loading="loading.tax">
          <template #header>
            <div class="card-header">
              <div class="title-wrap">
                <span class="card-title">{{ $tt("税务分析") }}</span>
                <el-button size="small" type="primary" plain @click="openDetail('tax-analysis')">
                  <el-icon><View /></el-icon> 查看明细
                </el-button>
              </div>
            </div>
          </template>
          <el-row :gutter="12" class="tax-kpi-row">
            <el-col :span="6">
              <div class="tax-kpi">
                <div class="tax-kpi-label">{{ $tt("开票总额") }}</div>
                <div class="tax-kpi-value">¥{{ fmt(taxData.totalAmount) }}</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="tax-kpi">
                <div class="tax-kpi-label">{{ $tt("税额合计") }}</div>
                <div class="tax-kpi-value tax">¥{{ fmt(taxData.totalTax) }}</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="tax-kpi">
                <div class="tax-kpi-label">{{ $tt("平均税率") }}</div>
                <div class="tax-kpi-value">{{ pct(taxData.avgTaxRate) }}</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="tax-kpi">
                <div class="tax-kpi-label">{{ $tt("已收税额") }}</div>
                <div class="tax-kpi-value received">¥{{ fmt(taxData.receivedTax) }}</div>
              </div>
            </el-col>
          </el-row>
          <el-divider content-position="left">
            税率分布（共 {{ taxData.rateDistribution?.length || 0 }} 个税率档）
          </el-divider>
          <div ref="taxChartRef" class="mini-chart" style="height: 200px"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 共享明细抽屉 -->
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
          <el-button type="success" plain @click="handleExportDetail">
            <el-icon><Download /></el-icon>导出
          </el-button>
        </div>
        <el-table :data="detailData" v-loading="detailLoading" border stripe size="small"
          max-height="560" style="margin-top: 12px;">
          <template v-if="currentReportKey === 'invoice-overview'">
            <el-table-column prop="invoiceNo" :label="$tf('发票号')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="customerName" :label="$tf('客户名称')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="invoiceDate" :label="$tf('开票日期')" width="110" />
            <el-table-column prop="status" :label="$tf('状态')" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)" size="small">{{ $dt(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('开票金额')" align="right" width="130">
              <template #default="{ row }">¥{{ fmt(row.amount) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('税额')" align="right" width="100">
              <template #default="{ row }">¥{{ fmt(row.taxAmount) }}</template>
            </el-table-column>
            <el-table-column prop="taxRate" :label="$tf('税率')" align="center" width="80">
              <template #default="{ row }">{{ pct(row.taxRate) }}</template>
            </el-table-column>
          </template>
          <template v-else-if="currentReportKey === 'invoice-trend'">
            <el-table-column prop="period" :label="$tf('期间')" width="100" />
            <el-table-column prop="invoiceCount" :label="$tf('发票数')" width="100" align="right" />
            <el-table-column :label="$tf('开票金额')" align="right" width="150">
              <template #default="{ row }">¥{{ fmt(row.amount) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('税额')" align="right" width="120">
              <template #default="{ row }">¥{{ fmt(row.taxAmount) }}</template>
            </el-table-column>
          </template>
          <template v-else-if="currentReportKey === 'customer-analysis'">
            <el-table-column prop="customerName" :label="$tf('客户名称')" min-width="180" show-overflow-tooltip />
            <el-table-column prop="invoiceCount" :label="$tf('发票数')" width="100" align="right" />
            <el-table-column :label="$tf('开票金额')" align="right" width="140">
              <template #default="{ row }">¥{{ fmt(row.amount) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('占比')" width="100" align="right">
              <template #default="{ row }">{{ pct(row.percentage) }}</template>
            </el-table-column>
          </template>
          <template v-else-if="currentReportKey === 'tax-analysis'">
            <el-table-column prop="taxRate" :label="$tf('税率')" width="80" align="center">
              <template #default="{ row }">{{ pct(row.taxRate) }}</template>
            </el-table-column>
            <el-table-column prop="invoiceCount" :label="$tf('发票数')" width="100" align="right" />
            <el-table-column :label="$tf('开票金额')" align="right" width="130">
              <template #default="{ row }">¥{{ fmt(row.amount) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('税额')" align="right" width="120">
              <template #default="{ row }">¥{{ fmt(row.taxAmount) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('平均税率')" align="center" width="100">
              <template #default="{ row }">{{ pct(row.avgTaxRate) }}</template>
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
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Money, TrendCharts, PieChart, Coin, View, Download, Search, ArrowUp, ArrowDown } from '@element-plus/icons-vue'
import echarts from '@/utils/echartsCore'
import {
  saleInvoiceOverview,
  saleInvoiceTrend,
  saleInvoiceCustomerAnalysis,
  saleInvoiceTaxAnalysis,
  saleInvoiceDetails
} from '@/api/report'
import { exportCsv } from '@/utils/csvExport'
import { tt } from '@/i18n'

// 当前年份与可选年份范围
const currentYear = new Date().getFullYear()
const filterYear = ref(currentYear)
const yearOptions = computed(() => {
  const arr = []
  for (let y = currentYear + 1; y >= currentYear - 3; y--) arr.push(y)
  return arr
})

// 顶部筛选：日期范围 + 关键字
const dateRange = ref([])
const keyword = ref('')
const trendPeriod = ref('month')
const customerTopN = ref(5)

// 构建通用查询参数（年份 + 日期范围 + 关键字）
function buildParams() {
  const params = { year: filterYear.value }
  if (dateRange.value && dateRange.value.length === 2) {
    params.startDate = dateRange.value[0]
    params.endDate = dateRange.value[1]
  }
  if (keyword.value) params.keyword = keyword.value
  return params
}

// 重置筛选条件并重新加载
function resetFilter() {
  filterYear.value = currentYear
  dateRange.value = []
  keyword.value = ''
  loadAll()
}

// 各报表加载状态
const loading = reactive({
  all: false,
  kpi: false,
  overview: false,
  trend: false,
  customer: false,
  tax: false
})

// 顶部KPI卡片
const kpiCards = ref([
  { label: '开票总额', value: '¥0.00', type: 'primary', icon: 'Money', bgColor: 'rgba(64, 158, 255, 0.12)', iconColor: '#409eff' },
  { label: '发票数量', value: '0', type: 'success', icon: 'TrendCharts', bgColor: 'rgba(103, 194, 58, 0.12)', iconColor: '#67c23a' },
  { label: '平均税率', value: '0%', type: 'warning', icon: 'Coin', bgColor: 'rgba(230, 162, 60, 0.12)', iconColor: '#e6a23c' },
  { label: '回款率', value: '0%', type: 'info', icon: 'PieChart', bgColor: 'rgba(144, 147, 153, 0.12)', iconColor: '#909399' }
])

// 1. 发票统计概览数据
const overviewStats = ref([])
const statusChartRef = ref(null)
let statusChartInstance = null

// 2. 发票趋势数据
const trendData = ref({})
const trendChartRef = ref(null)
let trendChartInstance = null

// 3. 客户发票分析数据
const customerData = ref({})
const customerChartRef = ref(null)
let customerChartInstance = null

// 4. 税务分析数据
const taxData = ref({})
const taxChartRef = ref(null)
let taxChartInstance = null

const router = useRouter()

// ECharts 图表实例引用
const charts = [
  { ref: statusChartRef, instance: statusChartInstance, name: 'status' },
  { ref: trendChartRef, instance: trendChartInstance, name: 'trend' },
  { ref: customerChartRef, instance: customerChartInstance, name: 'customer' },
  { ref: taxChartRef, instance: taxChartInstance, name: 'tax' }
]

// 工具函数：格式化金额
function fmt(num) {
  if (num === null || num === undefined || num === '') return '0.00'
  return Number(num).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 工具函数：格式化百分比（接受小数或已乘100的数）
function pct(num) {
  if (num === null || num === undefined || num === '') return '0%'
  const v = Number(num)
  // 若数值大于1，认为是已乘100的格式
  if (Math.abs(v) > 1) return v.toFixed(2) + '%'
  return (v * 100).toFixed(2) + '%'
}

// 工具函数：获取状态标签类型
function getStatusType(status) {
  const statusMap = {
    '已开票': 'success',
    '已作废': 'danger',
    '红冲': 'warning',
    '部分回款': 'info',
    '全部回款': 'success'
  }
  return statusMap[status] || 'info'
}

// ========== 加载：发票统计概览 ==========
async function loadOverview() {
  loading.overview = true
  try {
    const res = await saleInvoiceOverview(buildParams())
    // 月度概览：真实环比（与上月金额对比，首月或上月金额为0时记0）
    overviewStats.value = (res.monthData || []).map((m, idx, arr) => {
      const cur = Number(m.amount) || 0
      const prev = idx > 0 ? Number(arr[idx - 1].amount) || 0 : null
      const trend = prev === null || prev === 0 ? 0 : ((cur - prev) / prev) * 100
      return { label: m.month, value: '¥' + fmt(m.amount), trend: Number(trend.toFixed(2)) }
    })
    // 同步KPI：开票总额、发票数量、平均税率（税额合计/净额合计）、回款率（应收已收/应收合计）
    kpiCards.value[0].value = '¥' + fmt(res.totalAmount)
    kpiCards.value[1].value = String(res.totalInvoices || 0)
    const netAmount = Number(res.totalAmount || 0) - Number(res.totalTax || 0)
    const avgTaxRate = netAmount > 0 ? Number(res.totalTax || 0) / netAmount : 0
    kpiCards.value[2].value = pct(avgTaxRate)
    kpiCards.value[3].value = pct(res.paymentRate)
    await nextTick()
    renderStatusChart(res.statusDist || {})
  } catch (e) {
    ElMessage.error(e.message || tt('发票统计概览加载失败'))
  } finally {
    loading.overview = false
  }
}

// 渲染：发票状态分布饼图
function renderStatusChart(distribution) {
  if (!statusChartRef.value) return
  if (!statusChartInstance) {
    statusChartInstance = echarts.init(statusChartRef.value)
  }
  const data = Object.entries(distribution).map(([name, value]) => ({
    name,
    value: Number(value) || 0
  }))
  const option = {
    tooltip: {
      trigger: 'item',
      formatter: (p) => `${p.name}<br/>数量: ${p.value}<br/>占比: ${p.percent}%`
    },
    legend: {
      type: 'scroll',
      orient: 'vertical',
      right: 0,
      top: 'middle',
      textStyle: { fontSize: 12, color: '#606266' }
    },
    series: [
      {
        name: '发票状态',
        type: 'pie',
        radius: ['45%', '70%'],
        center: ['38%', '50%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#fff', borderWidth: 2, borderRadius: 6 },
        label: { show: false, position: 'center' },
        emphasis: {
          label: { show: true, fontSize: 16, fontWeight: 700, formatter: '{b}\n{d}%' }
        },
        labelLine: { show: false },
        data
      }
    ]
  }
  statusChartInstance.setOption(option, true)
}

// ========== 加载：发票趋势分析 ==========
async function loadTrend() {
  loading.trend = true
  try {
    const res = await saleInvoiceTrend(buildParams())
    const weekly = res.weeklyTrend || []
    trendData.value = weekly
    await nextTick()
    // 图表数据结构适配：periods/invoiceCounts/amounts
    renderTrendChart({
      periods: weekly.map(i => i.week),
      invoiceCounts: weekly.map(i => i.count),
      amounts: weekly.map(i => i.amount)
    })
  } catch (e) {
    ElMessage.error(e.message || tt('发票趋势分析加载失败'))
  } finally {
    loading.trend = false
  }
}

// 渲染：发票趋势折线图
function renderTrendChart(data) {
  if (!trendChartRef.value) return
  if (!trendChartInstance) {
    trendChartInstance = echarts.init(trendChartRef.value)
  }
  const option = {
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        let html = params[0].name + '<br/>'
        params.forEach(p => {
          const suffix = p.seriesName === '发票数' ? p.value + ' 张' : '¥' + fmt(p.value)
          html += `${p.marker}${p.seriesName}: ${suffix}<br/>`
        })
        return html
      }
    },
    legend: { data: ['发票数', '开票金额'], top: 0, right: 0, textStyle: { fontSize: 12 } },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '15%', containLabel: true },
    xAxis: {
      type: 'category',
      data: data.periods || [],
      axisLine: { lineStyle: { color: '#e4e7ed' } },
      axisLabel: { color: '#909399' }
    },
    yAxis: [
      {
        type: 'value',
        name: '发票数',
        position: 'left',
        axisLine: { show: false },
        axisTick: { show: false },
        splitLine: { lineStyle: { color: '#f5f7fa' } },
        axisLabel: { color: '#909399' }
      },
      {
        type: 'value',
        name: '金额',
        position: 'right',
        axisLine: { show: false },
        axisTick: { show: false },
        splitLine: { show: false },
        axisLabel: { color: '#909399', formatter: (v) => v >= 10000 ? (v / 10000).toFixed(1) + 'w' : v }
      }
    ],
    series: [
      {
        name: '发票数',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: data.invoiceCounts || [],
        itemStyle: { color: '#409eff' },
        lineStyle: { width: 3, color: '#409eff' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64, 158, 255, 0.3)' },
            { offset: 1, color: 'rgba(64, 158, 255, 0.02)' }
          ])
        }
      },
      {
        name: '开票金额',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: data.amounts || [],
        itemStyle: { color: '#67c23a' },
        lineStyle: { width: 3, color: '#67c23a' }
      }
    ]
  }
  trendChartInstance.setOption(option, true)
}

// ========== 加载：客户发票分析 ==========
async function loadCustomer() {
  loading.customer = true
  try {
    const res = await saleInvoiceCustomerAnalysis({ ...buildParams(), topN: customerTopN.value })
    const dist = res.customerDistribution || []
    customerData.value = {
      distribution: dist,
      totalAmount: res.totalAmount,
      totalInvoices: res.totalInvoices
    }
    await nextTick()
    renderCustomerChart(dist.map(d => ({ customerName: d.name, amount: d.value })))
  } catch (e) {
    ElMessage.error(e.message || tt('客户发票分析加载失败'))
  } finally {
    loading.customer = false
  }
}

// 渲染：客户发票分析柱状图
function renderCustomerChart(customers) {
  if (!customerChartRef.value) return
  if (!customerChartInstance) {
    customerChartInstance = echarts.init(customerChartRef.value)
  }
  const names = customers.map(c => c.customerName || '未知客户')
  const amounts = customers.map(c => Number(c.amount) || 0)
  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params) => {
        let html = params[0].name + '<br/>'
        params.forEach(p => {
          html += `${p.marker}${p.seriesName}: ¥${fmt(p.value)}<br/>`
        })
        return html
      }
    },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '15%', containLabel: true },
    xAxis: {
      type: 'category',
      data: names,
      axisLine: { lineStyle: { color: '#e4e7ed' } },
      axisLabel: { 
        color: '#909399',
        interval: 0,
        rotate: names.length > 8 ? 45 : 0,
        fontSize: 11
      }
    },
    yAxis: {
      type: 'value',
      name: '金额',
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: '#f5f7fa' } },
      axisLabel: { color: '#909399', formatter: (v) => v >= 10000 ? (v / 10000).toFixed(1) + 'w' : v }
    },
    series: [
      {
        name: '开票金额',
        type: 'bar',
        data: amounts,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#409eff' },
            { offset: 1, color: '#79bbff' }
          ]),
          borderRadius: [4, 4, 0, 0]
        }
      }
    ]
  }
  customerChartInstance.setOption(option, true)
}

// ========== 加载：税务分析 ==========
async function loadTax() {
  loading.tax = true
  try {
    const res = await saleInvoiceTaxAnalysis(buildParams())
    const totalAmount = Number(res.totalAmount || 0)
    const totalTax = Number(res.totalTax || 0)
    const netAmount = totalAmount - totalTax
    taxData.value = {
      taxDistribution: res.taxDistribution || [],
      monthlyTax: res.monthlyTax || [],
      totalAmount: totalAmount,
      totalTax: totalTax,
      // 平均税率 = 税额合计/净额合计（无净额数据为0）
      avgTaxRate: netAmount > 0 ? totalTax / netAmount : 0,
      receivedTax: 0 // 无回款税额数据源，显示0（不编造）
    }
    await nextTick()
    renderTaxChart(res.taxDistribution || [])
  } catch (e) {
    ElMessage.error(e.message || tt('税务分析加载失败'))
  } finally {
    loading.tax = false
  }
}

// 渲染：税率分布柱状图
function renderTaxChart(distribution) {
  if (!taxChartRef.value) return
  if (!taxChartInstance) {
    taxChartInstance = echarts.init(taxChartRef.value)
  }
  // 税率标签直接取后端返回的百分比字符串（如'13%'），空归'未税率'
  const rates = distribution.map(d => d.rate || '未税率')
  const amounts = distribution.map(d => Number(d.amount) || 0)
  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params) => {
        let html = params[0].name + '<br/>'
        params.forEach(p => {
          html += `${p.marker}开票金额: ¥${fmt(p.value)}<br/>`
        })
        return html
      }
    },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '10%', containLabel: true },
    xAxis: {
      type: 'category',
      data: rates,
      axisLine: { lineStyle: { color: '#e4e7ed' } },
      axisLabel: { color: '#909399', interval: 0 }
    },
    yAxis: {
      type: 'value',
      name: '金额',
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: '#f5f7fa' } },
      axisLabel: { color: '#909399', formatter: (v) => v >= 10000 ? (v / 10000).toFixed(1) + 'w' : v }
    },
    series: [
      {
        name: '开票金额',
        type: 'bar',
        data: amounts,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#e6a23c' },
            { offset: 1, color: '#f3d19e' }
          ]),
          borderRadius: [4, 4, 0, 0]
        }
      }
    ]
  }
  taxChartInstance.setOption(option, true)
}

// 全部加载
async function loadAll() {
  loading.all = true
  loading.kpi = true
  try {
    await Promise.all([loadOverview(), loadTrend(), loadCustomer(), loadTax()])
  } finally {
    loading.all = false
    loading.kpi = false
  }
}

// ============ 明细抽屉 ============
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
  'invoice-overview': '发票统计概览',
  'invoice-trend': '发票趋势分析',
  'customer-analysis': '客户发票分析',
  'tax-analysis': '税务分析'
}



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

async function loadDetail() {
  detailLoading.value = true
  try {
    // 明细抽屉筛选参数（日期范围优先，否则按年份）
    const base = {}
    if (detailDateRange.value && detailDateRange.value.length === 2) {
      base.startDate = detailDateRange.value[0]
      base.endDate = detailDateRange.value[1]
    } else {
      base.year = filterYear.value
    }
    if (detailKeyword.value) base.keyword = detailKeyword.value

    if (currentReportKey.value === 'invoice-overview') {
      // 发票明细：后端分页
      const res = await saleInvoiceDetails({ ...base, current: detailPage.value, size: detailSize.value })
      detailData.value = (res.list || []).map(item => ({
        invoiceNo: item.invoiceNo,
        customerName: item.customerName,
        invoiceDate: item.date,
        status: item.status,
        amount: item.amount,
        taxAmount: item.tax
      }))
      detailTotal.value = res.pagination ? res.pagination.total : 0
      return
    }

    // 其余报表数据量小：拉全量后客户端分页
    let rows = []
    if (currentReportKey.value === 'invoice-trend') {
      const res = await saleInvoiceTrend(base)
      rows = (res.weeklyTrend || []).map(item => ({
        period: item.week,
        invoiceCount: item.count,
        amount: item.amount,
        taxAmount: item.tax
      }))
    } else if (currentReportKey.value === 'customer-analysis') {
      const res = await saleInvoiceCustomerAnalysis(base)
      const total = Number(res.totalAmount) || 0
      rows = (res.customerRanking || []).map(item => ({
        customerName: item.name,
        invoiceCount: item.invoices,
        amount: item.amount,
        // 占比 = 客户金额/全部客户金额合计（真实计算）
        percentage: total > 0 ? (Number(item.amount) || 0) / total * 100 : 0
      }))
    } else if (currentReportKey.value === 'tax-analysis') {
      const res = await saleInvoiceTaxAnalysis(base)
      rows = (res.taxDistribution || []).map(item => ({
        // '13%'→13（供pct格式化），'未税率'解析为0
        taxRate: parseFloat(item.rate) || 0,
        invoiceCount: item.count,
        amount: item.amount,
        taxAmount: item.tax,
        // 平均税率 = 税额/金额（真实计算，金额为0时为0）
        avgTaxRate: Number(item.amount) > 0 ? (Number(item.tax) || 0) / Number(item.amount) : 0
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

async function handleExportDetail() {
  try {
    detailLoading.value = true
    // 与明细抽屉一致的筛选参数（日期范围优先，否则按年份）
    const base = {}
    if (detailDateRange.value && detailDateRange.value.length === 2) {
      base.startDate = detailDateRange.value[0]
      base.endDate = detailDateRange.value[1]
    } else {
      base.year = filterYear.value
    }
    if (detailKeyword.value) base.keyword = detailKeyword.value

    let rows = []
    let columns = []
    if (currentReportKey.value === 'invoice-overview') {
      // 后端分页报表：导出时拉全量
      const res = await saleInvoiceDetails({ ...base, current: 1, size: 9999 })
      rows = (res.list || []).map(item => ({
        invoiceNo: item.invoiceNo,
        customerName: item.customerName,
        invoiceDate: item.date,
        status: item.status,
        amount: item.amount,
        taxAmount: item.tax
      }))
      columns = [
        { label: '发票号', prop: 'invoiceNo' },
        { label: '客户名称', prop: 'customerName' },
        { label: '开票日期', prop: 'invoiceDate' },
        { label: '状态', prop: 'status' },
        { label: '开票金额', prop: 'amount' },
        { label: '税额', prop: 'taxAmount' }
      ]
    } else if (currentReportKey.value === 'invoice-trend') {
      const res = await saleInvoiceTrend(base)
      rows = (res.weeklyTrend || []).map(item => ({
        period: item.week,
        invoiceCount: item.count,
        amount: item.amount,
        taxAmount: item.tax
      }))
      columns = [
        { label: '期间', prop: 'period' },
        { label: '发票数', prop: 'invoiceCount' },
        { label: '开票金额', prop: 'amount' },
        { label: '税额', prop: 'taxAmount' }
      ]
    } else if (currentReportKey.value === 'customer-analysis') {
      const res = await saleInvoiceCustomerAnalysis(base)
      const total = Number(res.totalAmount) || 0
      rows = (res.customerRanking || []).map(item => ({
        customerName: item.name,
        invoiceCount: item.invoices,
        amount: item.amount,
        percentage: total > 0 ? (Number(item.amount) || 0) / total * 100 : 0
      }))
      columns = [
        { label: '客户名称', prop: 'customerName' },
        { label: '发票数', prop: 'invoiceCount' },
        { label: '开票金额', prop: 'amount' },
        { label: '占比(%)', prop: 'percentage' }
      ]
    } else if (currentReportKey.value === 'tax-analysis') {
      const res = await saleInvoiceTaxAnalysis(base)
      rows = (res.taxDistribution || []).map(item => ({
        taxRate: item.rate,
        invoiceCount: item.count,
        amount: item.amount,
        taxAmount: item.tax
      }))
      columns = [
        { label: '税率', prop: 'taxRate' },
        { label: '发票数', prop: 'invoiceCount' },
        { label: '开票金额', prop: 'amount' },
        { label: '税额', prop: 'taxAmount' }
      ]
    } else {
      ElMessage.warning(tt('当前无可导出的报表数据'))
      return
    }

    if (!rows.length) { ElMessage.warning(tt('没有可导出的数据')); return }
    exportCsv(columns, rows, currentReportTitle.value || '销售发票明细')
    ElMessage.success(`${tt("已导出 ")}${rows.length}${tt(" 条数据")}`)
  } catch (e) {
    console.error('导出失败', e)
    ElMessage.error(e.message || tt('导出失败'))
  } finally {
    detailLoading.value = false
  }
}

// 窗口尺寸变化时统一 resize 所有图表
function handleResize() {
  statusChartInstance?.resize()
  trendChartInstance?.resize()
  customerChartInstance?.resize()
  taxChartInstance?.resize()
}

onMounted(async () => {
  await nextTick()
  loadAll()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  statusChartInstance?.dispose()
  trendChartInstance?.dispose()
  customerChartInstance?.dispose()
  taxChartInstance?.dispose()
})
</script>

<style scoped lang="scss">
.report-container {
  padding: 20px;
  background: #f5f7fa;
  min-height: calc(100vh - 60px);
}

/* 顶部标题 */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;

  .page-title {
    font-size: 20px;
    font-weight: 700;
    color: #1f2937;
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

  .page-sub {
    font-size: 12px;
    color: #9ca3af;
    margin-top: 4px;
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
    justify-content: flex-end;
  }
}

/* KPI 卡片 */
.kpi-row {
  margin-bottom: 16px;
}

.kpi-card {
  border-radius: 12px;
  border: none;
  transition: transform 0.3s ease, box-shadow 0.3s ease;

  &:hover {
    transform: translateY(-3px);
  }

  :deep(.el-card__body) {
    padding: 18px 20px;
  }

  .kpi-content {
    display: flex;
    align-items: center;
    gap: 14px;
  }

  .kpi-icon {
    width: 48px;
    height: 48px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  .kpi-info {
    flex: 1;
    min-width: 0;
  }

  .kpi-value {
    font-size: 22px;
    font-weight: 700;
    color: #1f2937;
    line-height: 1.2;
  }

  .kpi-label {
    font-size: 12px;
    color: #909399;
    margin-top: 4px;
  }
}

/* 报表卡片 */
.report-row {
  margin-bottom: 16px;
}

.report-card {
  border-radius: 12px;
  border: none;
  margin-bottom: 16px;

  :deep(.el-card__header) {
    padding: 14px 18px;
    border-bottom: 1px solid #f3f4f6;
  }

  :deep(.el-card__body) {
    padding: 16px 18px;
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .title-wrap {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .card-title {
    font-size: 15px;
    font-weight: 600;
    color: #1f2937;
  }

  .card-sub {
    font-size: 12px;
    color: #909399;
  }
}

.mini-chart {
  width: 100%;
}

/* 发票统计概览 */
.overview-stat {
  background: #f8fafc;
  border-radius: 8px;
  padding: 16px;
  text-align: center;
  position: relative;
  overflow: hidden;

  &.primary {
    background: linear-gradient(135deg, #ecf5ff, #f0f9ff);
  }

  &.success {
    background: linear-gradient(135deg, #f0f9eb, #f6ffed);
  }

  &.warning {
    background: linear-gradient(135deg, #fdf6ec, #fffbf0);
  }

  &.danger {
    background: linear-gradient(135deg, #fef0f0, #fff1f0);
  }

  .stat-label {
    font-size: 12px;
    color: #909399;
    margin-bottom: 8px;
  }

  .stat-value {
    font-size: 20px;
    font-weight: 700;
    color: #1f2937;
    margin-bottom: 8px;
  }

  .stat-trend {
    font-size: 11px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 2px;

    &.up {
      color: #67c23a;
    }

    &.down {
      color: #f56c6c;
    }

    .el-icon {
      font-size: 12px;
    }
  }
}

/* 客户汇总 */
.customer-summary {
  display: flex;
  justify-content: flex-end;
  gap: 24px;
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #ebeef5;
}

/* 税务KPI */
.tax-kpi-row {
  margin-bottom: 8px;
}

.tax-kpi {
  background: #f8fafc;
  border-radius: 8px;
  padding: 12px;
  text-align: center;

  .tax-kpi-label {
    font-size: 12px;
    color: #909399;
    margin-bottom: 6px;
  }

  .tax-kpi-value {
    font-size: 16px;
    font-weight: 700;
    color: #1f2937;

    &.tax {
      color: #f56c6c;
    }

    &.received {
      color: #67c23a;
    }
  }
}

/* 明细抽屉 */
.detail-drawer {
  padding: 0 16px 16px;
}

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

/* 响应式 */
@media (max-width: 768px) {
  .report-container { padding: 12px; }
  .page-header { flex-direction: column; align-items: flex-start; gap: 10px; }
  .customer-config { width: 100%; }
}
</style>