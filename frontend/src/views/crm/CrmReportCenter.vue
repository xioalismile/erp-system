<template>
  <div class="dashboard-container">
    <!-- 页面标题 + 全局时间筛选 -->
    <div class="page-header">
      <div class="page-title">
        <el-icon :size="22" color="#409EFF"><DataAnalysis /></el-icon>
        <span>{{ $tt("CRM报表中心") }}</span>
      </div>
      <div class="filter-bar">
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
          :placeholder="$tt('搜索关键字（客户/销售员/商机名称）')"
          clearable
          style="width: 240px"
          @keyup.enter="loadAll"
        />
        <el-button type="primary" :loading="loading" @click="loadAll">
          <el-icon><Search /></el-icon>查询
        </el-button>
        <el-button @click="resetFilter">{{ $tt("重置") }}</el-button>
      </div>
    </div>

    <!-- KPI 卡片：总商机数 / 成交商机数 / 赢单率 / 总销售金额 -->
    <el-row :gutter="12" class="kpi-row">
      <el-col :span="6" v-for="kpi in kpiCards" :key="kpi.key">
        <el-card shadow="hover" class="kpi-card" :body-style="{ padding: '18px 20px' }">
          <div class="kpi-inner">
            <div class="kpi-icon" :style="{ background: kpi.bg, color: kpi.color }">
              <el-icon :size="22"><component :is="kpi.icon" /></el-icon>
            </div>
            <div class="kpi-text">
              <div class="kpi-label">{{ $tf(kpi.label) }}</div>
              <div class="kpi-value" :style="{ color: kpi.color }">{{ kpi.value }}</div>
              <div class="kpi-hint">{{ $tt(kpi.hint) }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 报表 2x2 网格 -->
    <!-- 第一行：销售漏斗 + 销售业绩排行 -->
    <el-row :gutter="12">
      <el-col :span="12">
        <el-card shadow="never" class="report-card">
          <template #header>
            <div class="card-header">
              <span class="card-title"><el-icon><Filter /></el-icon>{{ $tt("销售漏斗分析") }}</span>
              <div class="header-actions">
                <el-tag size="small" type="info">{{ $tt("商机阶段分布") }}</el-tag>
                <el-button size="small" type="primary" plain @click="openDetail('funnel')">
                  <el-icon><View /></el-icon> 查看明细
                </el-button>
              </div>
            </div>
          </template>
          <!-- 漏斗图 -->
          <div ref="funnelChartRef" class="chart-box"></div>
          <!-- 阶段明细表 -->
          <el-table :data="funnelData.stages" size="small" border max-height="200" class="report-table">
            <el-table-column prop="stage" :label="$tf('阶段')" min-width="100" />
            <el-table-column prop="count" :label="$tf('商机数')" width="90" align="right" />
            <el-table-column :label="$tf('阶段金额')" align="right" width="140">
              <template #default="{ row }">¥{{ formatMoney(row.amount) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('转化率')" width="100" align="right">
              <template #default="{ row }">{{ formatRate(row.conversionRate) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="never" class="report-card">
          <template #header>
            <div class="card-header">
              <span class="card-title"><el-icon><Trophy /></el-icon>{{ $tt("销售业绩排行") }}</span>
              <div class="header-actions">
                <el-tag size="small" type="warning">{{ $tt("按订单金额") }}</el-tag>
                <el-button size="small" type="primary" plain @click="openDetail('sales-ranking')">
                  <el-icon><View /></el-icon> 查看明细
                </el-button>
              </div>
            </div>
          </template>
          <!-- 横向柱状图 -->
          <div ref="rankingChartRef" class="chart-box"></div>
          <!-- 排行表 -->
          <el-table :data="rankingData" size="small" border max-height="200" class="report-table">
            <el-table-column :label="$tf('排名')" width="70" align="center">
              <template #default="{ $index }">
                <el-tag v-if="$index < 3" :type="['danger', 'warning', 'info'][$index]" size="small">{{ $index + 1 }}</el-tag>
                <span v-else>{{ $index + 1 }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="salerName" :label="$tf('销售员')" min-width="120" show-overflow-tooltip />
            <el-table-column prop="orderCount" :label="$tf('订单数')" width="90" align="right" />
            <el-table-column prop="wonOpportunityCount" :label="$tf('赢单数')" width="90" align="right" />
            <el-table-column :label="$tf('订单金额')" align="right" min-width="130">
              <template #default="{ row }">¥{{ formatMoney(row.orderAmount) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第二行：客户分析 + 回款分析 -->
    <el-row :gutter="12" style="margin-top: 12px;">
      <el-col :span="12">
        <el-card shadow="never" class="report-card">
          <template #header>
            <div class="card-header">
              <span class="card-title"><el-icon><PieChart /></el-icon>{{ $tt("客户分析") }}</span>
              <div class="header-actions">
                <div class="mini-stats">
                  <el-tag size="small">客户总数 {{ customerData.totalCustomers }}</el-tag>
                  <el-tag size="small" type="success">复购率 {{ formatRate(customerData.repeatRate) }}</el-tag>
                </div>
                <el-button size="small" type="primary" plain @click="openDetail('customer-analysis')">
                  <el-icon><View /></el-icon> 查看明细
                </el-button>
              </div>
            </div>
          </template>
          <!-- 饼图 -->
          <div ref="customerChartRef" class="chart-box"></div>
          <!-- TOP客户表 -->
          <el-table :data="customerData.topCustomers" size="small" border max-height="200" class="report-table">
            <el-table-column :label="$tf('排名')" width="70" align="center">
              <template #default="{ $index }">{{ $index + 1 }}</template>
            </el-table-column>
            <el-table-column prop="customerName" :label="$tf('客户名称')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="orderCount" :label="$tf('订单数')" width="90" align="right" />
            <el-table-column :label="$tf('订单金额')" align="right" width="140">
              <template #default="{ row }">¥{{ formatMoney(row.totalAmount) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('占比')" width="90" align="right">
              <template #default="{ row }">{{ formatRate(row.percentage) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="never" class="report-card">
          <template #header>
            <div class="card-header">
              <span class="card-title"><el-icon><Money /></el-icon>{{ $tt("回款分析") }}</span>
              <div class="header-actions">
                <div class="mini-stats">
                  <el-tag size="small" type="success">回款率 {{ formatRate(paymentData.receiveRate) }}</el-tag>
                  <el-tag size="small" type="danger">逾期 ¥{{ formatMoney(paymentData.totalOverdue) }}</el-tag>
                </div>
                <el-button size="small" type="primary" plain @click="openDetail('payment-analysis')">
                  <el-icon><View /></el-icon> 查看明细
                </el-button>
              </div>
            </div>
          </template>
          <!-- 状态分布柱状图 -->
          <div ref="paymentChartRef" class="chart-box"></div>
          <!-- TOP应收客户表 -->
          <el-table :data="paymentData.topArCustomer" size="small" border max-height="200" class="report-table">
            <el-table-column prop="customerName" :label="$tf('客户名称')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="count" :label="$tf('应收单数')" width="100" align="right" />
            <el-table-column :label="$tf('应收金额')" align="right" width="140">
              <template #default="{ row }">¥{{ formatMoney(row.totalAmount) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('未回款')" align="right" width="140">
              <template #default="{ row }">
                <span :class="{ 'overdue-text': row.balanceAmount > 0 }">¥{{ formatMoney(row.balanceAmount) }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 明细数据抽屉 -->
    <el-drawer
      v-model="detailDrawerVisible"
      :title="$tt(currentReportTitle) + ' - ' + $tt('明细数据')"
      direction="rtl"
      size="70%"
      :close-on-click-modal="false"
    >
      <div class="detail-drawer">
        <!-- 抽屉内筛选 -->
        <div class="detail-filter">
          <el-date-picker
            v-model="detailDateRange"
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
            v-model="detailKeyword"
            :placeholder="$tt('搜索关键字')"
            clearable
            style="width: 220px"
            @keyup.enter="loadDetail"
          />
          <el-button type="primary" :loading="detailLoading" @click="loadDetail">
            <el-icon><Search /></el-icon>查询
          </el-button>
          <el-button @click="resetDetail">{{ $tt("重置") }}</el-button>
          <el-button type="success" plain :loading="detailLoading" @click="handleExportDetail">
            <el-icon><Download /></el-icon>导出
          </el-button>
        </div>
        <!-- 明细表格 -->
        <el-table
          :data="detailData"
          v-loading="detailLoading"
          border
          stripe
          size="small"
          max-height="560"
          style="margin-top: 12px;"
        >
          <!-- 动态列：根据 currentReportKey 渲染 -->
          <template v-if="currentReportKey === 'funnel'">
            <el-table-column prop="opportunityNo" :label="$tf('商机编号')" min-width="150" show-overflow-tooltip />
            <el-table-column prop="opportunityName" :label="$tf('商机名称')" min-width="180" show-overflow-tooltip />
            <el-table-column prop="customerName" :label="$tf('客户名称')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="salesStage" :label="$tf('销售阶段')" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.salesStage === '成交' ? 'success' : (row.salesStage === '失败' ? 'danger' : 'info')" size="small">
                  {{ row.salesStage }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('预计金额')" align="right" width="130">
              <template #default="{ row }">¥{{ formatMoney(row.estimatedAmount) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('实际成交金额')" align="right" width="140">
              <template #default="{ row }">
                <span v-if="row.actualAmount" style="color:#67c23a;font-weight:600;">¥{{ formatMoney(row.actualAmount) }}</span>
                <span v-else style="color:#c0c4cc;">-</span>
              </template>
            </el-table-column>
            <!-- CRM→销售 联动字段 -->
            <el-table-column prop="saleOrderNo" :label="$tf('关联销售订单')" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">
                <el-tag v-if="row.saleOrderNo" type="warning" size="small">{{ row.saleOrderNo }}</el-tag>
                <span v-else style="color:#c0c4cc;">{{ $tt("未转化") }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="actualCloseDate" :label="$tf('成交日期')" width="110" />
            <el-table-column prop="createDate" :label="$tf('创建日期')" width="110" />
          </template>
          <template v-else-if="currentReportKey === 'sales-ranking'">
            <el-table-column prop="orderNo" :label="$tf('销售订单号')" min-width="150" show-overflow-tooltip />
            <el-table-column prop="customerName" :label="$tf('客户名称')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="orderDate" :label="$tf('订单日期')" width="110" />
            <el-table-column :label="$tf('订单金额')" align="right" width="140">
              <template #default="{ row }">¥{{ formatMoney(row.totalAmount) }}</template>
            </el-table-column>
            <el-table-column prop="salerName" :label="$tf('销售员')" width="100" />
            <el-table-column prop="approvalStatus" :label="$tf('状态')" width="100" align="center" />
            <!-- 销售→CRM 联动字段 -->
            <el-table-column prop="opportunityNo" :label="$tf('关联商机号')" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <el-tag v-if="row.opportunityNo" type="success" size="small">{{ row.opportunityNo }}</el-tag>
                <span v-else style="color:#c0c4cc;">{{ $tt("无商机") }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="opportunityName" :label="$tf('商机名称')" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.opportunityName">{{ row.opportunityName }}</span>
                <span v-else style="color:#c0c4cc;">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="opportunityStage" :label="$tf('商机阶段')" width="100" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.opportunityStage" :type="row.opportunityStage === '成交' ? 'success' : 'info'" size="small">
                  {{ row.opportunityStage }}
                </el-tag>
                <span v-else style="color:#c0c4cc;">-</span>
              </template>
            </el-table-column>
          </template>
          <template v-else-if="currentReportKey === 'customer-analysis'">
            <el-table-column prop="orderNo" :label="$tf('销售订单号')" min-width="150" show-overflow-tooltip />
            <el-table-column prop="customerName" :label="$tf('客户名称')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="orderDate" :label="$tf('订单日期')" width="110" />
            <el-table-column :label="$tf('订单金额')" align="right" width="140">
              <template #default="{ row }">¥{{ formatMoney(row.totalAmount) }}</template>
            </el-table-column>
            <el-table-column prop="salerName" :label="$tf('销售员')" width="100" />
            <!-- 销售→CRM 联动字段 -->
            <el-table-column prop="opportunityNo" :label="$tf('关联商机号')" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <el-tag v-if="row.opportunityNo" type="success" size="small">{{ row.opportunityNo }}</el-tag>
                <span v-else style="color:#c0c4cc;">{{ $tt("无商机") }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="opportunityName" :label="$tf('商机名称')" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.opportunityName">{{ row.opportunityName }}</span>
                <span v-else style="color:#c0c4cc;">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="opportunityStage" :label="$tf('商机阶段')" width="100" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.opportunityStage" :type="row.opportunityStage === '成交' ? 'success' : 'info'" size="small">
                  {{ row.opportunityStage }}
                </el-tag>
                <span v-else style="color:#c0c4cc;">-</span>
              </template>
            </el-table-column>
          </template>
          <template v-else-if="currentReportKey === 'payment-analysis'">
            <el-table-column prop="arNo" :label="$tf('应收单号')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="customerName" :label="$tf('客户名称')" min-width="160" show-overflow-tooltip />
            <el-table-column :label="$tf('应收金额')" align="right" width="130">
              <template #default="{ row }">¥{{ formatMoney(row.totalAmount) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('已收金额')" align="right" width="130">
              <template #default="{ row }">¥{{ formatMoney(row.paidAmount) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('未收金额')" align="right" width="130">
              <template #default="{ row }">
                <span :class="{ 'overdue-text': row.balanceAmount > 0 }">¥{{ formatMoney(row.balanceAmount) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="status" :label="$tf('状态')" width="100" align="center">
              <template #default="{ row }">{{ $dt(row.status) }}</template>
            </el-table-column>
            <el-table-column prop="sourceBillNo" :label="$tf('来源单号')" min-width="140" show-overflow-tooltip />
            <!-- 应收→销售订单→CRM 联动字段 -->
            <el-table-column prop="saleOrderNo" :label="$tf('关联销售订单')" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <el-tag v-if="row.saleOrderNo" type="warning" size="small">{{ row.saleOrderNo }}</el-tag>
                <span v-else style="color:#c0c4cc;">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="opportunityNo" :label="$tf('关联商机号')" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <el-tag v-if="row.opportunityNo" type="success" size="small">{{ row.opportunityNo }}</el-tag>
                <span v-else style="color:#c0c4cc;">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="arDate" :label="$tf('应收日期')" width="110" />
          </template>
        </el-table>
        <!-- 分页 -->
        <div class="detail-pagination">
          <el-pagination
            v-model:current-page="detailPage"
            v-model:page-size="detailSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="detailTotal"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @size-change="loadDetail"
            @current-change="loadDetail"
          />
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import echarts from '@/utils/echartsCore'
import {
  DataAnalysis, Search, Filter, Trophy, PieChart, Money,
  TrendCharts, Finished, View, Download
} from '@element-plus/icons-vue'
import { tt } from '@/i18n'
// CRM报表中心 API（拦截器已解包 Result.data，await 结果即为业务数据）
import {
  crmFunnel, crmSalesRanking, crmCustomerAnalysis, crmPaymentAnalysis,
  crmFunnelDetails, crmSalesRankingDetails, crmCustomerAnalysisDetails, crmPaymentAnalysisDetails
} from '@/api/report'
import { exportCsv } from '@/utils/csvExport'

// ============ 全局时间筛选 ============
const dateRange = ref([])
const loading = ref(false)
const keyword = ref('')

// 构造查询参数
function buildParams() {
  const params = {}
  if (dateRange.value && dateRange.value.length === 2) {
    params.startDate = dateRange.value[0]
    params.endDate = dateRange.value[1]
  }
  if (keyword.value) params.keyword = keyword.value
  return params
}

// 重置筛选并重新加载
function resetFilter() {
  dateRange.value = []
  keyword.value = ''
  loadAll()
}

// ============ 报表数据 ============
// 1. 销售漏斗
const funnelData = reactive({
  stages: [],
  total: 0,
  totalAmount: 0,
  wonCount: 0,
  winRate: 0
})
// 2. 销售业绩排行（数组）
const rankingData = ref([])
// 3. 客户分析
const customerData = reactive({
  topCustomers: [],
  totalCustomers: 0,
  repeatCustomers: 0,
  repeatRate: 0,
  totalAmount: 0,
  totalOrders: 0
})
// 4. 回款分析
const paymentData = reactive({
  totalReceivable: 0,
  totalReceived: 0,
  totalOverdue: 0,
  receiveRate: 0,
  statusDistribution: [],
  topArCustomer: []
})

// KPI 卡片（取自漏斗接口汇总）
const kpiCards = computed(() => [
  {
    key: 'total', label: '总商机数', value: funnelData.total,
    hint: '全部商机', icon: TrendCharts, color: '#409EFF', bg: '#ECF5FF'
  },
  {
    key: 'won', label: '成交商机数', value: funnelData.wonCount,
    hint: '赢单商机', icon: Finished, color: '#67C23A', bg: '#F0F9EB'
  },
  {
    key: 'winRate', label: '赢单率', value: formatRate(funnelData.winRate),
    hint: '成交 / 总商机', icon: DataAnalysis, color: '#E6A23C', bg: '#FDF6EC'
  },
  {
    key: 'amount', label: '总销售金额', value: '¥' + formatMoney(funnelData.totalAmount),
    hint: '商机金额合计', icon: Money, color: '#F56C6C', bg: '#FEF0F0'
  }
])

// ============ 工具函数 ============
// 金额格式化（千分位 + 两位小数）
function formatMoney(num) {
  if (num === null || num === undefined || num === '') return '0.00'
  const n = Number(num)
  if (isNaN(n)) return '0.00'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 百分比格式化：兼容小数(0.25)与百分比(25)两种形式
function formatRate(rate) {
  if (rate === null || rate === undefined || rate === '') return '0%'
  let n = Number(rate)
  if (isNaN(n)) return '0%'
  if (n <= 1) n = n * 100
  return n.toFixed(1) + '%'
}

// 紧凑金额：用于坐标轴/标签，避免过长
function formatShort(num) {
  const n = Number(num) || 0
  if (Math.abs(n) >= 100000000) return '¥' + (n / 100000000).toFixed(1) + '亿'
  if (Math.abs(n) >= 10000) return '¥' + (n / 10000).toFixed(1) + '万'
  return '¥' + Math.round(n)
}

// ============ 图表实例 ============
const funnelChartRef = ref(null)
const rankingChartRef = ref(null)
const customerChartRef = ref(null)
const paymentChartRef = ref(null)

let funnelChartInstance = null
let rankingChartInstance = null
let customerChartInstance = null
let paymentChartInstance = null

// ============ 各报表数据加载 ============
async function loadFunnel() {
  try {
    const data = await crmFunnel(buildParams())
    const d = data || {}
    funnelData.stages = d.stages || []
    funnelData.total = d.total || 0
    funnelData.totalAmount = d.totalAmount || 0
    funnelData.wonCount = d.wonCount || 0
    funnelData.winRate = d.winRate || 0
    renderFunnelChart()
  } catch (e) {
    console.error('加载销售漏斗失败', e)
  }
}

async function loadRanking() {
  try {
    const data = await crmSalesRanking(buildParams())
    rankingData.value = Array.isArray(data) ? data : []
    renderRankingChart()
  } catch (e) {
    console.error('加载销售排行失败', e)
  }
}

async function loadCustomer() {
  try {
    const data = await crmCustomerAnalysis(buildParams())
    const d = data || {}
    customerData.topCustomers = d.topCustomers || []
    customerData.totalCustomers = d.totalCustomers || 0
    customerData.repeatCustomers = d.repeatCustomers || 0
    customerData.repeatRate = d.repeatRate || 0
    customerData.totalAmount = d.totalAmount || 0
    customerData.totalOrders = d.totalOrders || 0
    renderCustomerChart()
  } catch (e) {
    console.error('加载客户分析失败', e)
  }
}

async function loadPayment() {
  try {
    const data = await crmPaymentAnalysis(buildParams())
    const d = data || {}
    paymentData.totalReceivable = d.totalReceivable || 0
    paymentData.totalReceived = d.totalReceived || 0
    paymentData.totalOverdue = d.totalOverdue || 0
    paymentData.receiveRate = d.receiveRate || 0
    paymentData.statusDistribution = d.statusDistribution || []
    paymentData.topArCustomer = d.topArCustomer || []
    renderPaymentChart()
  } catch (e) {
    console.error('加载回款分析失败', e)
  }
}

// ============ 图表渲染 ============
// 1. 销售漏斗图（funnel）
function renderFunnelChart() {
  if (!funnelChartRef.value) return
  if (!funnelChartInstance) funnelChartInstance = echarts.init(funnelChartRef.value)
  const stages = funnelData.stages || []
  const colors = ['#5470C6', '#91CC75', '#FAC858', '#EE6666', '#73C0DE', '#3CA272', '#FC8452', '#9A60B4']
  const option = {
    tooltip: {
      trigger: 'item',
      formatter: (p) => {
        const stage = stages.find(s => s.stage === p.name) || {}
        return `${p.name}<br/>商机数：${p.value} 个<br/>阶段金额：¥${formatMoney(stage.amount)}<br/>转化率：${formatRate(stage.conversionRate)}`
      }
    },
    color: colors,
    series: [{
      type: 'funnel',
      left: '10%',
      top: 16,
      bottom: 16,
      width: '80%',
      min: 0,
      max: Math.max(1, ...stages.map(s => s.count || 0)),
      minSize: '20%',
      maxSize: '100%',
      sort: 'descending',
      gap: 2,
      label: { show: true, position: 'inside', formatter: '{b}\n{c} 个', color: '#fff', fontWeight: 'bold' },
      labelLine: { show: false },
      itemStyle: { borderColor: '#fff', borderWidth: 1 },
      emphasis: { label: { fontSize: 14 } },
      data: stages.map(s => ({ value: s.count || 0, name: s.stage }))
    }]
  }
  funnelChartInstance.setOption(option, true)
}

// 2. 销售业绩排行（横向柱状图 bar）
function renderRankingChart() {
  if (!rankingChartRef.value) return
  if (!rankingChartInstance) rankingChartInstance = echarts.init(rankingChartRef.value)
  const list = rankingData.value || []
  // 升序排列，使排名第一显示在柱状图顶部
  const sorted = [...list].sort((a, b) => (a.orderAmount || 0) - (b.orderAmount || 0))
  const names = sorted.map(r => r.salerName || '未知')
  const amounts = sorted.map(r => r.orderAmount || 0)
  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params) => {
        const p = params[0]
        const row = sorted[p.dataIndex] || {}
        return `${p.name}<br/>订单金额：¥${formatMoney(row.orderAmount)}<br/>订单数：${row.orderCount || 0}<br/>赢单数：${row.wonOpportunityCount || 0}`
      }
    },
    grid: { left: '3%', right: '12%', bottom: '3%', top: '8%', containLabel: true },
    xAxis: { type: 'value', axisLabel: { formatter: (v) => formatShort(v) } },
    yAxis: { type: 'category', data: names, axisLabel: { color: '#606266' } },
    series: [{
      type: 'bar',
      data: amounts,
      barWidth: '55%',
      label: { show: true, position: 'right', formatter: (p) => formatShort(p.value) },
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: '#73C0DE' },
          { offset: 1, color: '#5470C6' }
        ]),
        borderRadius: [0, 4, 4, 0]
      }
    }]
  }
  rankingChartInstance.setOption(option, true)
}

// 3. 客户分析（饼图 pie）
function renderCustomerChart() {
  if (!customerChartRef.value) return
  if (!customerChartInstance) customerChartInstance = echarts.init(customerChartRef.value)
  const list = customerData.topCustomers || []
  const colors = ['#5470C6', '#91CC75', '#FAC858', '#EE6666', '#73C0DE', '#3CA272', '#FC8452', '#9A60B4', '#EA7CCC']
  const option = {
    tooltip: {
      trigger: 'item',
      formatter: (p) => {
        const row = list.find(c => c.customerName === p.name) || {}
        return `${p.name}<br/>订单金额：¥${formatMoney(row.totalAmount)}<br/>订单数：${row.orderCount || 0}<br/>占比：${formatRate(row.percentage)}`
      }
    },
    legend: { orient: 'vertical', left: 'right', top: 'center', textStyle: { color: '#606266' } },
    color: colors,
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['40%', '50%'],
      avoidLabelOverlap: true,
      itemStyle: { borderColor: '#fff', borderWidth: 2, borderRadius: 6 },
      label: { formatter: '{b}\n{d}%' },
      data: list.map(c => ({ value: c.totalAmount || 0, name: c.customerName || '未知' }))
    }]
  }
  customerChartInstance.setOption(option, true)
}

// 4. 回款分析（状态分布柱状图 bar）
function renderPaymentChart() {
  if (!paymentChartRef.value) return
  if (!paymentChartInstance) paymentChartInstance = echarts.init(paymentChartRef.value)
  const dist = paymentData.statusDistribution || []
  const statuses = dist.map(d => d.status || '未知')
  const amounts = dist.map(d => d.amount || 0)
  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params) => {
        const idx = params[0].dataIndex
        const row = dist[idx] || {}
        return `${row.status || '未知'}<br/>单数：${row.count || 0}<br/>金额：¥${formatMoney(row.amount)}`
      }
    },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '12%', containLabel: true },
    xAxis: { type: 'category', data: statuses, axisLabel: { color: '#606266' } },
    yAxis: { type: 'value', name: '金额', axisLabel: { formatter: (v) => formatShort(v) } },
    series: [{
      type: 'bar',
      data: amounts,
      barWidth: '45%',
      label: { show: true, position: 'top', formatter: (p) => formatShort(p.value) },
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#5470C6' },
          { offset: 1, color: '#91CC75' }
        ]),
        borderRadius: [4, 4, 0, 0]
      }
    }]
  }
  paymentChartInstance.setOption(option, true)
}

// ============ 统一加载 ============
async function loadAll() {
  loading.value = true
  await nextTick()
  try {
    // 并行加载4张报表，任一失败不影响其余
    await Promise.allSettled([loadFunnel(), loadRanking(), loadCustomer(), loadPayment()])
  } catch (e) {
    ElMessage.error(tt('加载报表数据失败'))
  } finally {
    loading.value = false
  }
}

// 窗口尺寸变化时重绘图表
function handleResize() {
  funnelChartInstance?.resize()
  rankingChartInstance?.resize()
  customerChartInstance?.resize()
  paymentChartInstance?.resize()
}

onMounted(() => {
  loadAll()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  // 释放图表实例，避免内存泄漏
  funnelChartInstance?.dispose()
  rankingChartInstance?.dispose()
  customerChartInstance?.dispose()
  paymentChartInstance?.dispose()
})

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
  'funnel': '销售漏斗分析',
  'sales-ranking': '销售业绩排行',
  'customer-analysis': '客户分析',
  'payment-analysis': '回款分析'
}

const reportDetailApiMap = {
  'funnel': crmFunnelDetails,
  'sales-ranking': crmSalesRankingDetails,
  'customer-analysis': crmCustomerAnalysisDetails,
  'payment-analysis': crmPaymentAnalysisDetails
}

function openDetail(key) {
  currentReportKey.value = key
  currentReportTitle.value = reportTitleMap[key] || ''
  // 继承页面顶部筛选
  detailDateRange.value = dateRange.value && dateRange.value.length === 2 ? [...dateRange.value] : []
  detailKeyword.value = keyword.value || ''
  detailPage.value = 1
  detailSize.value = 20
  detailDrawerVisible.value = true
  loadDetail()
}

async function loadDetail() {
  const api = reportDetailApiMap[currentReportKey.value]
  if (!api) return
  detailLoading.value = true
  try {
    const params = {
      page: detailPage.value,
      size: detailSize.value
    }
    if (detailDateRange.value && detailDateRange.value.length === 2) {
      params.startDate = detailDateRange.value[0]
      params.endDate = detailDateRange.value[1]
    }
    if (detailKeyword.value) params.keyword = detailKeyword.value
    const res = await api(params)
    // 兼容 {records,total} 或数组
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
  'funnel': [
    { label: '商机编号', prop: 'opportunityNo' },
    { label: '商机名称', prop: 'opportunityName' },
    { label: '客户名称', prop: 'customerName' },
    { label: '销售阶段', prop: 'salesStage' },
    { label: '预计金额', prop: 'estimatedAmount' },
    { label: '实际成交金额', prop: 'actualAmount' },
    { label: '关联销售订单', prop: 'saleOrderNo' },
    { label: '成交日期', prop: 'actualCloseDate' },
    { label: '创建日期', prop: 'createDate' }
  ],
  'sales-ranking': [
    { label: '销售订单号', prop: 'orderNo' },
    { label: '客户名称', prop: 'customerName' },
    { label: '订单日期', prop: 'orderDate' },
    { label: '订单金额', prop: 'totalAmount' },
    { label: '销售员', prop: 'salerName' },
    { label: '状态', prop: 'approvalStatus' },
    { label: '关联商机号', prop: 'opportunityNo' },
    { label: '商机名称', prop: 'opportunityName' },
    { label: '商机阶段', prop: 'opportunityStage' }
  ],
  'customer-analysis': [
    { label: '销售订单号', prop: 'orderNo' },
    { label: '客户名称', prop: 'customerName' },
    { label: '订单日期', prop: 'orderDate' },
    { label: '订单金额', prop: 'totalAmount' },
    { label: '销售员', prop: 'salerName' },
    { label: '关联商机号', prop: 'opportunityNo' },
    { label: '商机名称', prop: 'opportunityName' },
    { label: '商机阶段', prop: 'opportunityStage' }
  ],
  'payment-analysis': [
    { label: '应收单号', prop: 'arNo' },
    { label: '客户名称', prop: 'customerName' },
    { label: '应收金额', prop: 'totalAmount' },
    { label: '已收金额', prop: 'paidAmount' },
    { label: '未收金额', prop: 'balanceAmount' },
    { label: '状态', prop: 'status' },
    { label: '来源单号', prop: 'sourceBillNo' },
    { label: '关联销售订单', prop: 'saleOrderNo' },
    { label: '关联商机号', prop: 'opportunityNo' },
    { label: '应收日期', prop: 'arDate' }
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
    exportCsv(columns, rows, currentReportTitle.value || 'CRM报表明细')
    ElMessage.success(`${tt("已导出 ")}${rows.length}${tt(" 条数据")}`)
  } catch (e) {
    console.error('导出失败', e)
    ElMessage.error(e.message || tt('导出失败'))
  } finally {
    detailLoading.value = false
  }
}
</script>

<style scoped>
.dashboard-container {
  padding: 20px;
  background: #f5f7fa;
  min-height: 100%;
  box-sizing: border-box;
}

/* 页面标题 + 筛选栏 */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}
.page-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}
.filter-bar {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* KPI 卡片 */
.kpi-row { margin-bottom: 12px; }
.kpi-card { border-radius: 8px; }
.kpi-inner { display: flex; align-items: center; gap: 14px; }
.kpi-icon {
  width: 52px;
  height: 52px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.kpi-text { flex: 1; min-width: 0; }
.kpi-label { color: #909399; font-size: 13px; }
.kpi-value { font-size: 24px; font-weight: 600; margin: 2px 0; }
.kpi-hint { color: #C0C4CC; font-size: 12px; }

/* 报表卡片 */
.report-card { border-radius: 8px; }
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 500;
}
.card-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.card-title .el-icon { color: #409EFF; }
.mini-stats {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

/* 图表区域 */
.chart-box {
  width: 100%;
  height: 320px;
  margin-bottom: 8px;
}
.report-table { width: 100%; }

/* 未回款高亮 */
.overdue-text {
  color: #F56C6C;
  font-weight: 600;
}

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
