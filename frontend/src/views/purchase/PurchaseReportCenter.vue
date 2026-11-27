<template>
  <div class="report-container">
    <!-- 顶部页面标题 -->
    <div class="page-header">
      <h2 class="page-title">{{ $tt("采购报表中心") }}</h2>
      <p class="page-subtitle">{{ $tt("采购价格走势、供应商分析及采购执行统计") }}</p>
      <div class="nav-links">
        <el-button type="primary" link @click="$router.push('/purchase/report/analysis')">
          <el-icon><DataLine /></el-icon>采购分析
        </el-button>
        <el-button type="primary" link @click="$router.push('/purchase/report/invoice')">
          <el-icon><Document /></el-icon>采购发票报表
        </el-button>
      </div>
      <!-- 全局筛选栏：日期范围 + 关键字 -->
      <div class="filter-bar" style="margin-top: 12px;">
        <el-date-picker v-model="dateRange" type="daterange" :range-separator="$tt('至')"
          :start-placeholder="$tt('开始日期')" :end-placeholder="$tt('结束日期')"
          value-format="YYYY-MM-DD" unlink-panels clearable style="width: 280px" />
        <el-input v-model="keyword" :placeholder="$tt('搜索关键字（物料/供应商/订单号）')"
          clearable style="width: 240px" @keyup.enter="loadAll" />
        <el-button type="primary" :loading="loadingAll" @click="loadAll">
          <el-icon><Search /></el-icon>查询
        </el-button>
        <el-button @click="resetFilter">{{ $tt("重置") }}</el-button>
      </div>
    </div>

    <!-- 报表1：采购价格走势（多物料折线图 + 物料表格） -->
    <el-card shadow="never" class="report-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $tt("采购价格走势") }}</span>
          <div class="header-actions">
            <el-input
              v-model="priceTrendQuery.materialId"
              :placeholder="$tt('物料ID（可选）')"
              clearable
              style="width: 160px"
              @keyup.enter="loadPriceTrend"
            />
            <el-input-number
              v-model="priceTrendQuery.months"
              :min="1"
              :max="24"
              controls-position="right"
              style="width: 130px"
            />
            <el-button type="primary" :loading="loading.priceTrend" @click="loadPriceTrend">{{ $tt("查询") }}</el-button>
            <el-button size="small" type="primary" plain @click="openDetail('price-trend')">
              <el-icon><View /></el-icon> 查看明细
            </el-button>
          </div>
        </div>
      </template>
      <!-- 多物料折线图：X轴月份，Y轴单价 -->
      <div ref="priceTrendChartRef" class="chart-box" />
      <el-table :data="priceTrendTable" stripe border size="small" style="margin-top:12px;" max-height="280">
        <el-table-column prop="materialCode" :label="$tf('物料编码')" min-width="140" show-overflow-tooltip />
        <el-table-column prop="materialName" :label="$tf('物料名称')" min-width="180" show-overflow-tooltip />
        <el-table-column
          v-for="month in priceTrendMonths"
          :key="month"
          :label="month"
          align="right"
          width="120"
        >
          <template #default="{ row }">
            <span>{{ row.monthPrices && row.monthPrices[month] != null ? '¥' + formatMoney(row.monthPrices[month]) : '-' }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 报表2：供应商分析（饼图占比 + 表格） -->
    <el-card shadow="never" class="report-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $tt("供应商分析") }}</span>
          <div class="header-actions">
            <el-button type="primary" link :loading="loading.supplier" @click="loadSupplierAnalysis">{{ $tt("刷新") }}</el-button>
            <el-button size="small" type="primary" plain @click="openDetail('supplier-analysis')">
              <el-icon><View /></el-icon> 查看明细
            </el-button>
          </div>
        </div>
      </template>
      <el-row :gutter="12">
        <el-col :span="10">
          <!-- 饼图：各供应商金额占比 -->
          <div ref="supplierPieChartRef" class="chart-box" style="height: 360px;" />
        </el-col>
        <el-col :span="14">
          <el-table :data="supplierList" stripe border size="small" max-height="360">
            <el-table-column prop="supplierName" :label="$tf('供应商')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="orderCount" :label="$tf('订单数')" width="90" align="right" />
            <el-table-column :label="$tf('金额')" width="140" align="right">
              <template #default="{ row }">¥{{ formatMoney(row.totalAmount) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('占比')" width="100" align="right">
              <template #default="{ row }">{{ (Number(row.percentage) || 0).toFixed(2) }}%</template>
            </el-table-column>
            <el-table-column :label="$tf('准时率')" width="120" align="center">
              <template #default="{ row }">
                <el-tag :type="onTimeTagType(row.onTimeRate)" size="small">
                  {{ (Number(row.onTimeRate) || 0).toFixed(1) }}%
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-col>
      </el-row>
    </el-card>

    <!-- 报表3：采购执行统计（KPI卡片 + 状态分布柱状图） -->
    <el-card shadow="never" class="report-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $tt("采购执行统计") }}</span>
          <div class="header-actions">
            <el-button type="primary" link :loading="loading.execution" @click="loadExecution">{{ $tt("刷新") }}</el-button>
            <el-button size="small" type="primary" plain @click="openDetail('execution')">
              <el-icon><View /></el-icon> 查看明细
            </el-button>
          </div>
        </div>
      </template>
      <!-- KPI卡片行：订单数/金额/入库率/发票率 -->
      <el-row :gutter="12" class="kpi-row">
        <el-col :span="6" v-for="kpi in executionKpis" :key="kpi.label">
          <div class="kpi-card" :style="{ background: kpi.bg }">
            <div class="kpi-label">{{ $tf(kpi.label) }}</div>
            <div class="kpi-value" :style="{ color: kpi.color }">{{ kpi.value }}</div>
            <div class="kpi-hint">{{ $tt(kpi.hint) }}</div>
          </div>
        </el-col>
      </el-row>
      <!-- 状态分布柱状图 -->
      <div ref="executionBarChartRef" class="chart-box" style="margin-top: 12px;" />
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
          <template v-if="currentReportKey === 'price-trend'">
            <el-table-column prop="materialCode" :label="$tf('物料编码')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="materialName" :label="$tf('物料名称')" min-width="180" show-overflow-tooltip />
            <el-table-column prop="month" :label="$tf('月份')" width="100" />
            <el-table-column prop="supplierName" :label="$tf('供应商')" min-width="160" show-overflow-tooltip />
            <el-table-column :label="$tf('单价')" align="right" width="120">
              <template #default="{ row }">¥{{ fmt(row.unitPrice) }}</template>
            </el-table-column>
            <el-table-column prop="quantity" :label="$tf('采购量')" width="100" align="right" />
          </template>
          <template v-else-if="currentReportKey === 'supplier-analysis'">
            <el-table-column prop="supplierName" :label="$tf('供应商名称')" min-width="180" show-overflow-tooltip />
            <el-table-column prop="orderCount" :label="$tf('采购单数')" width="100" align="right" />
            <el-table-column :label="$tf('采购金额')" align="right" width="140">
              <template #default="{ row }">¥{{ fmt(row.totalAmount) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('平均单价')" align="right" width="130">
              <template #default="{ row }">¥{{ fmt(row.avgPrice) }}</template>
            </el-table-column>
          </template>
          <template v-else-if="currentReportKey === 'execution'">
            <el-table-column prop="orderNo" :label="$tf('采购单号')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="supplierName" :label="$tf('供应商')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="orderDate" :label="$tf('下单日期')" width="110" />
            <el-table-column prop="arrivalDate" :label="$tf('到货日期')" width="110" />
            <el-table-column prop="status" :label="$tf('状态')" width="100" align="center">
              <template #default="{ row }">{{ $dt(row.status) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('采购金额')" align="right" width="140">
              <template #default="{ row }">¥{{ fmt(row.amount) }}</template>
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
import { View, Download, Search, DataLine, Document } from '@element-plus/icons-vue'
import { tt } from '@/i18n'
// 采购报表相关 API
import {
  purchasePriceTrend,
  purchaseSupplierAnalysis,
  purchaseExecution,
  purchasePriceTrendDetails,
  purchaseSupplierAnalysisDetails,
  purchaseExecutionDetails
} from '@/api/report'

// ============ 公共工具函数 ============
// 金额格式化（保留两位小数 + 千分位）
function formatMoney(num) {
  if (num === null || num === undefined || num === '') return '0.00'
  return Number(num).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
// 明细抽屉复用现有金额格式化
const fmt = formatMoney

// 准时率标签颜色：>=90 success，>=70 warning，否则 danger
function onTimeTagType(rate) {
  const v = Number(rate) || 0
  if (v >= 90) return 'success'
  if (v >= 70) return 'warning'
  return 'danger'
}

// 各报表加载状态
const loading = reactive({ priceTrend: false, supplier: false, execution: false })

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
      loadPriceTrend(params),
      loadSupplierAnalysis(params),
      loadExecution(params)
    ])
  } finally {
    loadingAll.value = false
  }
}

// ============ 报表1：采购价格走势 ============
const priceTrendQuery = reactive({ materialId: '', months: 6 })
const priceTrendData = ref({ materials: [], startDate: '', endDate: '' })
const priceTrendChartRef = ref(null)
let priceTrendChartInstance = null

// 表格月份列（合并所有物料的月份并排序）
const priceTrendMonths = ref([])
const priceTrendTable = ref([])

async function loadPriceTrend(extraParams) {
  loading.priceTrend = true
  try {
    const params = { months: priceTrendQuery.months }
    if (priceTrendQuery.materialId) params.materialId = priceTrendQuery.materialId
    // 合并全局筛选参数（startDate/endDate/keyword），不破坏原有物料ID/月数筛选
    Object.assign(params, extraParams || {})
    const data = await purchasePriceTrend(params)
    priceTrendData.value = data || { materials: [], startDate: '', endDate: '' }
    // 合并所有物料的月份
    const monthSet = new Set()
    ;(priceTrendData.value.materials || []).forEach(m => {
      Object.keys(m.monthPrices || {}).forEach(k => monthSet.add(k))
    })
    priceTrendMonths.value = Array.from(monthSet).sort()
    priceTrendTable.value = priceTrendData.value.materials || []
    await nextTick()
    renderPriceTrendChart()
  } catch (e) {
    ElMessage.error(tt('加载采购价格走势失败'))
  } finally {
    loading.priceTrend = false
  }
}

// 渲染多物料折线图
function renderPriceTrendChart() {
  if (!priceTrendChartRef.value) return
  if (!priceTrendChartInstance) {
    priceTrendChartInstance = echarts.init(priceTrendChartRef.value)
  }
  const months = priceTrendMonths.value
  const materials = priceTrendData.value.materials || []
  const palette = ['#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de', '#3ba272', '#fc8452', '#9a60b4', '#ea7ccc']
  const series = materials.map((m, idx) => ({
    name: m.materialName || m.materialCode || `物料${m.materialId ?? idx + 1}`,
    type: 'line',
    smooth: true,
    symbol: 'circle',
    symbolSize: 6,
    connectNulls: true,
    data: months.map(mon => (m.monthPrices && m.monthPrices[mon] != null ? Number(m.monthPrices[mon]) : null)),
    itemStyle: { color: palette[idx % palette.length] }
  }))
  priceTrendChartInstance.setOption({
    tooltip: { trigger: 'axis' },
    legend: { top: 0, type: 'scroll' },
    grid: { left: '3%', right: '4%', bottom: '3%', top: 40, containLabel: true },
    xAxis: { type: 'category', data: months, boundaryGap: false },
    yAxis: { type: 'value', name: '单价', axisLabel: { formatter: '¥{value}' } },
    series
  }, true)
}

// ============ 报表2：供应商分析 ============
const supplierList = ref([])
const supplierPieChartRef = ref(null)
let supplierPieChartInstance = null
const supplierSummary = reactive({ totalAmount: 0, totalSuppliers: 0, totalOrders: 0 })

async function loadSupplierAnalysis(extraParams) {
  loading.supplier = true
  try {
    const data = await purchaseSupplierAnalysis(extraParams || {})
    supplierList.value = data?.suppliers || []
    supplierSummary.totalAmount = data?.totalAmount || 0
    supplierSummary.totalSuppliers = data?.totalSuppliers || 0
    supplierSummary.totalOrders = data?.totalOrders || 0
    await nextTick()
    renderSupplierPieChart()
  } catch (e) {
    ElMessage.error(tt('加载供应商分析失败'))
  } finally {
    loading.supplier = false
  }
}

// 渲染供应商金额占比饼图
function renderSupplierPieChart() {
  if (!supplierPieChartRef.value) return
  if (!supplierPieChartInstance) {
    supplierPieChartInstance = echarts.init(supplierPieChartRef.value)
  }
  const data = supplierList.value.map(s => ({
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

// ============ 报表3：采购执行统计 ============
const executionData = ref({})
const executionBarChartRef = ref(null)
let executionBarChartInstance = null

// KPI卡片配置
const executionKpis = ref([
  { label: '采购订单数', value: '0', hint: '累计订单数', color: '#409EFF', bg: '#ECF5FF' },
  { label: '采购金额', value: '¥0.00', hint: '累计订单金额', color: '#67C23A', bg: '#F0F9EB' },
  { label: '入库率', value: '0%', hint: '入库单数/订单数', color: '#E6A23C', bg: '#FDF6EC' },
  { label: '发票率', value: '0%', hint: '发票单数/订单数', color: '#F56C6C', bg: '#FEF0F0' }
])

async function loadExecution(extraParams) {
  loading.execution = true
  try {
    const data = await purchaseExecution(extraParams || {})
    executionData.value = data || {}
    executionKpis.value[0].value = String(data?.totalOrders || 0)
    executionKpis.value[1].value = '¥' + formatMoney(data?.totalOrderAmount || 0)
    executionKpis.value[2].value = (Number(data?.inboundRate) || 0).toFixed(1) + '%'
    executionKpis.value[3].value = (Number(data?.invoiceRate) || 0).toFixed(1) + '%'
    await nextTick()
    renderExecutionBarChart()
  } catch (e) {
    ElMessage.error(tt('加载采购执行统计失败'))
  } finally {
    loading.execution = false
  }
}

// 渲染订单状态分布柱状图
function renderExecutionBarChart() {
  if (!executionBarChartRef.value) return
  if (!executionBarChartInstance) {
    executionBarChartInstance = echarts.init(executionBarChartRef.value)
  }
  const dist = executionData.value.statusDistribution || {}
  const names = Object.keys(dist)
  const values = names.map(n => Number(dist[n]) || 0)
  executionBarChartInstance.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '12%', containLabel: true },
    xAxis: {
      type: 'category',
      data: names,
      axisLabel: { interval: 0, rotate: names.length > 6 ? 30 : 0 }
    },
    yAxis: { type: 'value', name: '订单数' },
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
  'price-trend': '采购价格走势',
  'supplier-analysis': '供应商分析',
  'execution': '采购执行'
}

const reportDetailApiMap = {
  'price-trend': purchasePriceTrendDetails,
  'supplier-analysis': purchaseSupplierAnalysisDetails,
  'execution': purchaseExecutionDetails
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

// 加载明细数据：兼容数组与分页对象两种返回结构
async function loadDetail() {
  const api = reportDetailApiMap[currentReportKey.value]
  if (!api) return
  detailLoading.value = true
  try {
    const params = { page: detailPage.value, size: detailSize.value }
    if (detailDateRange.value && detailDateRange.value.length === 2) {
      params.startDate = detailDateRange.value[0]
      params.endDate = detailDateRange.value[1]
    }
    if (detailKeyword.value) params.keyword = detailKeyword.value
    const res = await api(params)
    if (Array.isArray(res)) {
      detailData.value = res
      detailTotal.value = res.length
    } else {
      detailData.value = res.records || []
      detailTotal.value = res.total || 0
    }
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

// 明细抽屉导出列定义（与表格列一致）
const reportDetailColumnsMap = {
  'price-trend': [
    { label: '物料编码', prop: 'materialCode' },
    { label: '物料名称', prop: 'materialName' },
    { label: '月份', prop: 'month' },
    { label: '供应商', prop: 'supplierName' },
    { label: '单价', prop: 'unitPrice' },
    { label: '采购量', prop: 'quantity' }
  ],
  'supplier-analysis': [
    { label: '供应商名称', prop: 'supplierName' },
    { label: '采购单数', prop: 'orderCount' },
    { label: '采购金额', prop: 'totalAmount' },
    { label: '平均单价', prop: 'avgPrice' }
  ],
  'execution': [
    { label: '采购单号', prop: 'orderNo' },
    { label: '供应商', prop: 'supplierName' },
    { label: '下单日期', prop: 'orderDate' },
    { label: '到货日期', prop: 'arrivalDate' },
    { label: '状态', prop: 'status' },
    { label: '采购金额', prop: 'amount' }
  ]
}

async function handleExportDetail() {
  const api = reportDetailApiMap[currentReportKey.value]
  const columns = reportDetailColumnsMap[currentReportKey.value]
  if (!api || !columns) { ElMessage.warning(tt('当前无可导出的报表数据')); return }
  try {
    detailLoading.value = true
    const params = { page: 1, size: 9999 }
    if (detailDateRange.value && detailDateRange.value.length === 2) {
      params.startDate = detailDateRange.value[0]
      params.endDate = detailDateRange.value[1]
    }
    if (detailKeyword.value) params.keyword = detailKeyword.value
    const res = await api(params)
    const rows = Array.isArray(res) ? res : (res.records || [])
    if (!rows.length) { ElMessage.warning(tt('没有可导出的数据')); return }
    exportCsv(columns, rows, currentReportTitle.value || '采购报表明细')
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
  priceTrendChartInstance?.resize()
  supplierPieChartInstance?.resize()
  executionBarChartInstance?.resize()
}

onMounted(() => {
  loadPriceTrend()
  loadSupplierAnalysis()
  loadExecution()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  // 销毁所有图表实例，避免内存泄漏
  priceTrendChartInstance?.dispose()
  supplierPieChartInstance?.dispose()
  executionBarChartInstance?.dispose()
  priceTrendChartInstance = null
  supplierPieChartInstance = null
  executionBarChartInstance = null
})
</script>

<style scoped>
.report-container { padding: 20px; background: #f5f7fa; }
.page-header { margin-bottom: 16px; }
.page-title { margin: 0; font-size: 22px; font-weight: 600; color: #303133; }
.page-subtitle { margin: 6px 0 0; font-size: 13px; color: #909399; }

.nav-links {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

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
