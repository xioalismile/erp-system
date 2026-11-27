<template>
  <div class="report-container">
    <!-- 顶部页面标题 -->
    <div class="page-header">
      <div class="header-left">
        <div class="page-title">
          销售报表中心
          <span class="title-badge">SALE REPORT</span>
        </div>
        <div class="page-sub">{{ $tt("销售目标 · 订单趋势 · 占比分析 · 毛利分析 · 提成计算") }}</div>
        <div class="nav-links">
          <el-button type="primary" link @click="$router.push('/sale/report/analysis')">
            <el-icon><DataLine /></el-icon>销售分析
          </el-button>
          <el-button type="primary" link @click="$router.push('/sale/report/invoice')">
            <el-icon><Document /></el-icon>销售发票报表
          </el-button>
        </div>
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
          :placeholder="$tt('搜索关键字（订单号/客户/销售员）')"
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
      <!-- 1. 销售目标完成率 -->
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover" class="report-card" v-loading="loading.target">
          <template #header>
            <div class="card-header">
              <div class="title-wrap">
                <span class="card-title">{{ $tt("销售目标完成率") }}</span>
                <el-button size="small" type="primary" plain @click="openDetail('target-completion')">
                  <el-icon><View /></el-icon> 查看明细
                </el-button>
              </div>
              <span class="card-sub">{{ targetData.year || filterYear }}年度</span>
            </div>
          </template>
          <el-row :gutter="12">
            <el-col :span="10">
              <div ref="gaugeChart" class="mini-chart" style="height: 240px"></div>
            </el-col>
            <el-col :span="14">
              <div ref="quarterChart" class="mini-chart" style="height: 240px"></div>
            </el-col>
          </el-row>
          <el-divider content-position="left">
            <span style="display:inline-flex;align-items:center;gap:8px;">
              {{ $tt("员工完成明细") }}
              <el-tooltip :content="$tt('列设置')" placement="top">
                <el-button :icon="Setting" circle size="small" @click="openTargetCol" />
              </el-tooltip>
            </span>
          </el-divider>
          <el-table ref="targetTableRef" :data="targetData.employees || []" size="small" border max-height="240" class="report-table" :key="targetVer" @header-dragend="onTargetDragend">
            <el-table-column type="index" label="#" width="50" />
            <!-- 动态列渲染：列顺序由列设置拖拽配置驱动，单元格按 col.type 分支渲染 -->
            <template v-for="col in targetVisible" :key="col.prop">
              <el-table-column
                :prop="col.prop"
                :label="$tf(col.label)"
                :width="col.width"
                :min-width="col.minWidth"
                :align="col.align || 'left'"
                :show-overflow-tooltip="!!col.tooltip"
                :fixed="getTargetFixed(col.prop)"
              >
                <template v-if="col.type" #default="{ row }">
                  <template v-if="col.type === 'money'">¥{{ fmt(row[col.prop]) }}</template>
                  <el-tag v-else-if="col.type === 'rate'" :type="rateType(row[col.prop])" size="small">{{ pct(row[col.prop]) }}</el-tag>
                  <span v-else-if="col.type === 'gap'" :style="{ color: row.gap >= 0 ? '#67c23a' : '#f56c6c' }">
                    {{ row.gap >= 0 ? '+' : '' }}¥{{ fmt(row.gap) }}
                  </span>
                </template>
              </el-table-column>
            </template>
          </el-table>
        </el-card>
      </el-col>

      <!-- 2. 订单趋势分析 -->
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover" class="report-card" v-loading="loading.trend">
          <template #header>
            <div class="card-header">
              <div class="title-wrap">
                <span class="card-title">{{ $tt("订单趋势分析") }}</span>
                <el-button size="small" type="primary" plain @click="openDetail('order-trend')">
                  <el-icon><View /></el-icon> 查看明细
                </el-button>
              </div>
              <span class="card-sub">共 {{ trendData.totalOrders || 0 }} 单 / ¥{{ fmt(trendData.totalAmount) }}</span>
            </div>
          </template>
          <div ref="trendChart" class="mini-chart" style="height: 360px"></div>
        </el-card>
      </el-col>

      <!-- 3. 销售额占比 -->
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover" class="report-card" v-loading="loading.share">
          <template #header>
            <div class="card-header">
              <div class="title-wrap">
                <span class="card-title">{{ $tt("销售额占比") }}</span>
                <el-button size="small" type="primary" plain @click="openDetail('sales-share')">
                  <el-icon><View /></el-icon> 查看明细
                </el-button>
              </div>
              <el-radio-group v-model="shareDimension" size="small" @change="loadShare">
                <el-radio-button value="customer">{{ $tt("客户") }}</el-radio-button>
                <el-radio-button value="material">{{ $tt("物料") }}</el-radio-button>
                <el-radio-button value="saler">{{ $tt("销售员") }}</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div ref="shareChart" class="mini-chart" style="height: 320px"></div>
          <div class="share-summary">
            <span>合计金额：¥{{ fmt(shareData.totalAmount) }}</span>
            <span>合计订单：{{ shareData.totalOrders || 0 }} 单</span>
          </div>
        </el-card>
      </el-col>

      <!-- 4. 毛利分析 -->
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover" class="report-card" v-loading="loading.profit">
          <template #header>
            <div class="card-header">
              <div class="title-wrap">
                <span class="card-title">{{ $tt("毛利分析") }}</span>
                <el-button size="small" type="primary" plain @click="openDetail('gross-profit')">
                  <el-icon><View /></el-icon> 查看明细
                </el-button>
              </div>
            </div>
          </template>
          <el-row :gutter="12" class="profit-kpi-row">
            <el-col :span="6">
              <div class="profit-kpi">
                <div class="profit-kpi-label">{{ $tt("销售收入") }}</div>
                <div class="profit-kpi-value">¥{{ fmt(profitData.totalRevenue) }}</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="profit-kpi">
                <div class="profit-kpi-label">{{ $tt("销售成本") }}</div>
                <div class="profit-kpi-value cost">¥{{ fmt(profitData.totalCost) }}</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="profit-kpi">
                <div class="profit-kpi-label">{{ $tt("毛利") }}</div>
                <div class="profit-kpi-value profit">¥{{ fmt(profitData.totalProfit) }}</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="profit-kpi">
                <div class="profit-kpi-label">{{ $tt("毛利率") }}</div>
                <div class="profit-kpi-value" :class="profitRateClass(profitData.totalProfitRate)">
                  {{ pct(profitData.totalProfitRate) }}
                </div>
              </div>
            </el-col>
          </el-row>
          <el-divider content-position="left">
            <span style="display:inline-flex;align-items:center;gap:8px;">
              低毛利订单（低于 {{ pct(profitData.minProfitRate) }}，共 {{ profitData.lowProfitCount || 0 }} 单）
              <el-tooltip :content="$tt('列设置')" placement="top">
                <el-button :icon="Setting" circle size="small" @click="openProfitCol" />
              </el-tooltip>
            </span>
          </el-divider>
          <el-table ref="profitTableRef" :data="profitData.lowProfitOrders || []" size="small" border max-height="240" class="report-table" :key="profitVer" @header-dragend="onProfitDragend">
            <el-table-column type="index" label="#" width="50" />
            <!-- 动态列渲染：列顺序由列设置拖拽配置驱动，单元格按 col.type 分支渲染 -->
            <template v-for="col in profitVisible" :key="col.prop">
              <el-table-column
                :prop="col.prop"
                :label="$tf(col.label)"
                :width="col.width"
                :min-width="col.minWidth"
                :align="col.align || 'left'"
                :show-overflow-tooltip="!!col.tooltip"
                :fixed="getProfitFixed(col.prop)"
              >
                <template v-if="col.type" #default="{ row }">
                  <template v-if="col.type === 'money'">¥{{ fmt(row[col.prop]) }}</template>
                  <el-tag v-else-if="col.type === 'profitRate'" :type="rateType(row.profitRate, true)" size="small">{{ pct(row.profitRate) }}</el-tag>
                </template>
              </el-table-column>
            </template>
          </el-table>
        </el-card>
      </el-col>

      <!-- 5. 提成计算表 -->
      <el-col :xs="24">
        <el-card shadow="hover" class="report-card" v-loading="loading.commission">
          <template #header>
            <div class="card-header commission-header">
              <div class="title-wrap">
                <span class="card-title">{{ $tt("提成计算表") }}</span>
                <el-button size="small" type="primary" plain @click="openDetail('commission')">
                  <el-icon><View /></el-icon> 查看明细
                </el-button>
              </div>
              <div class="commission-config">
                <span class="config-label">{{ $tt("月份：") }}</span>
                <el-date-picker
                  v-model="commissionMonth"
                  type="month"
                  :placeholder="$tt('选择月份')"
                  format="YYYY-MM"
                  value-format="YYYY-MM"
                  style="width: 130px"
                  @change="loadCommission"
                />
                <span class="config-label">{{ $tt("提成基数：") }}</span>
                <el-select v-model="commissionParams.basis" style="width: 110px" @change="loadCommission">
                  <el-option :label="$tf('按订单额')" value="order" />
                  <el-option :label="$tf('按回款')" value="payment" />
                  <el-option :label="$tf('按毛利')" value="profit" />
                </el-select>
                <el-button type="primary" size="small" @click="loadCommission">{{ $tt("计算") }}</el-button>
                <el-button size="small" plain @click="goToCommissionSettings">{{ $tt("配置提成率") }}</el-button>
                <el-tooltip :content="$tt('列设置')" placement="top">
                  <el-button :icon="Setting" circle size="small" @click="openCommissionCol" />
                </el-tooltip>
              </div>
            </div>
          </template>
          <!-- 区间内涉及的提成率版本（按订单日期自动匹配，留痕） -->
          <div class="rate-versions-bar" v-if="commissionData.rateVersions && commissionData.rateVersions.length">
            <span class="rv-label">{{ $tt("本区间生效版本：") }}</span>
            <el-tag
              v-for="rv in commissionData.rateVersions"
              :key="rv.versionNo"
              :type="rv.isCurrent ? 'success' : 'info'"
              size="small"
              class="rv-tag"
            >
              v{{ rv.versionNo }} · {{ rv.effectiveDate }}{{ rv.expiryDate ? ' ~ ' + rv.expiryDate : ' 至今' }} · 提成率 {{ pct(rv.baseRate) }}
            </el-tag>
            <span class="rv-hint">{{ $tt("（按订单下单日期自动匹配，无需手工传参）") }}</span>
          </div>
          <div class="rate-versions-bar warn" v-else>
            <el-icon><WarningFilled /></el-icon>
            <span>{{ $tt("未配置提成率，请在【设置中心 → 业务 → 提成率配置】新增版本。") }}</span>
          </div>
          <el-table ref="commissionTableRef" :data="commissionData.list || []" size="small" border max-height="380" class="report-table" show-summary :summary-method="commissionSummary" :key="commissionVer" @header-dragend="onCommissionDragend">
            <el-table-column type="index" label="#" width="50" />
            <!-- 动态列渲染：列顺序由列设置拖拽配置驱动，单元格按 col.type 分支渲染 -->
            <template v-for="col in commissionVisible" :key="col.prop">
              <el-table-column
                :prop="col.prop"
                :label="col.dynamicLabel ? basisLabel : $tf(col.label)"
                :width="col.width"
                :min-width="col.minWidth"
                :align="col.align || 'left'"
                :show-overflow-tooltip="!!col.tooltip"
                :fixed="getCommissionFixed(col.prop)"
              >
                <template v-if="col.type" #default="{ row }">
                  <template v-if="col.type === 'money'">¥{{ fmt(row[col.prop]) }}</template>
                  <el-tag v-else-if="col.type === 'rate'" :type="rateType(row[col.prop])" size="small">{{ pct(row[col.prop]) }}</el-tag>
                  <template v-else-if="col.type === 'percent'">{{ pct(row[col.prop]) }}</template>
                  <span v-else-if="col.type === 'over'" :style="{ color: row.overAchieved >= 0 ? '#67c23a' : '#909399' }">¥{{ fmt(row.overAchieved) }}</span>
                  <span v-else-if="col.type === 'commission'" class="commission-amount">¥{{ fmt(row.commission) }}</span>
                </template>
              </el-table-column>
            </template>
          </el-table>
          <div class="commission-total">
            提成合计：<span class="commission-amount">¥{{ fmt(commissionData.totalCommission) }}</span>
          </div>
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
          <el-button type="success" plain :loading="detailLoading" @click="handleExportDetail">
            <el-icon><Download /></el-icon>导出
          </el-button>
        </div>
        <el-table :data="detailData" v-loading="detailLoading" border stripe size="small"
          max-height="560" style="margin-top: 12px;">
          <template v-if="currentReportKey === 'target-completion'">
            <el-table-column prop="employeeName" :label="$tf('姓名')" min-width="100" />
            <el-table-column prop="deptName" :label="$tf('部门')" min-width="120" />
            <el-table-column :label="$tf('年度目标')" align="right" width="130">
              <template #default="{ row }">¥{{ fmt(row.annualTarget) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('实际达成')" align="right" width="130">
              <template #default="{ row }">¥{{ fmt(row.actualAmount) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('完成率')" align="center" width="100">
              <template #default="{ row }">
                <el-tag :type="rateType(row.completionRate)" size="small">{{ pct(row.completionRate) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('差距')" align="right" width="110">
              <template #default="{ row }">
                <span :style="{ color: row.gap >= 0 ? '#67c23a' : '#f56c6c' }">
                  {{ row.gap >= 0 ? '+' : '' }}¥{{ fmt(row.gap) }}
                </span>
              </template>
            </el-table-column>
          </template>
          <template v-else-if="currentReportKey === 'order-trend'">
            <el-table-column prop="month" :label="$tf('月份')" width="100" />
            <el-table-column prop="orderCount" :label="$tf('订单数')" width="100" align="right" />
            <el-table-column :label="$tf('订单金额')" align="right" width="150">
              <template #default="{ row }">¥{{ fmt(row.orderAmount) }}</template>
            </el-table-column>
          </template>
          <template v-else-if="currentReportKey === 'sales-share'">
            <el-table-column prop="dimensionName" :label="$tf('名称')" min-width="180" show-overflow-tooltip />
            <el-table-column prop="orderCount" :label="$tf('订单数')" width="100" align="right" />
            <el-table-column :label="$tf('金额')" align="right" width="140">
              <template #default="{ row }">¥{{ fmt(row.amount) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('占比')" width="100" align="right">
              <template #default="{ row }">{{ pct(row.percentage) }}</template>
            </el-table-column>
          </template>
          <template v-else-if="currentReportKey === 'gross-profit'">
            <el-table-column prop="orderNo" :label="$tf('订单号')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="customerName" :label="$tf('客户')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="orderDate" :label="$tf('订单日期')" width="110" />
            <el-table-column :label="$tf('销售金额')" align="right" width="130">
              <template #default="{ row }">¥{{ fmt(row.saleAmount) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('成本')" align="right" width="130">
              <template #default="{ row }">¥{{ fmt(row.costAmount) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('毛利')" align="right" width="130">
              <template #default="{ row }">
                <span :style="{ color: row.grossProfit >= 0 ? '#67c23a' : '#f56c6c' }">¥{{ fmt(row.grossProfit) }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('毛利率')" align="center" width="100">
              <template #default="{ row }">{{ pct(row.grossProfitRate) }}</template>
            </el-table-column>
          </template>
          <template v-else-if="currentReportKey === 'commission'">
            <el-table-column prop="salerName" :label="$tf('销售员')" min-width="100" />
            <el-table-column prop="orderNo" :label="$tf('订单号')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="orderDate" :label="$tf('订单日期')" width="110" />
            <el-table-column prop="rateVersionNo" :label="$tf('提成率版本')" width="110" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.rateVersionNo" type="warning" size="small">v{{ row.rateVersionNo }}</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="rateEffectiveDate" :label="$tf('版本生效日')" width="110" />
            <el-table-column :label="$tf('订单金额')" align="right" width="130">
              <template #default="{ row }">¥{{ fmt(row.amount) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('提成基数')" align="right" width="130">
              <template #default="{ row }">¥{{ fmt(row.basis) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('提成率')" align="center" width="100">
              <template #default="{ row }">{{ pct(row.commissionRate) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('提成金额')" align="right" width="130">
              <template #default="{ row }">¥{{ fmt(row.commission) }}</template>
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

    <!-- 列设置抽屉（页内三张明细表各一份配置） -->
    <ColumnSettingDrawer v-model="targetColVisible" :page-key="'SaleReportCenter-target'" :columns="targetColumns" />
    <ColumnSettingDrawer v-model="profitColVisible" :page-key="'SaleReportCenter-profit'" :columns="profitColumns" />
    <ColumnSettingDrawer v-model="commissionColVisible" :page-key="'SaleReportCenter-commission'" :columns="commissionColumns" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Money, ShoppingCart, TrendCharts, PieChart, Coin, View, Download, Search, WarningFilled, DataLine, Document, Setting } from '@element-plus/icons-vue'
import echarts from '@/utils/echartsCore'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import {
  saleTargetCompletion,
  saleOrderTrend,
  saleShare,
  saleGrossProfit,
  saleCommission,
  saleTargetCompletionDetails,
  saleOrderTrendDetails,
  saleShareDetails,
  saleGrossProfitDetails,
  saleCommissionDetails
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
  target: false,
  trend: false,
  share: false,
  profit: false,
  commission: false
})

// 顶部KPI卡片
const kpiCards = ref([
  { label: '本年销售总额', value: '¥0.00', type: 'primary', icon: 'Money', bgColor: 'rgba(64, 158, 255, 0.12)', iconColor: '#409eff' },
  { label: '目标完成率', value: '0%', type: 'success', icon: 'TrendCharts', bgColor: 'rgba(103, 194, 58, 0.12)', iconColor: '#67c23a' },
  { label: '本月订单数', value: '0', type: 'warning', icon: 'ShoppingCart', bgColor: 'rgba(230, 162, 60, 0.12)', iconColor: '#e6a23c' },
  { label: '本月毛利率', value: '0%', type: 'info', icon: 'Coin', bgColor: 'rgba(144, 147, 153, 0.12)', iconColor: '#909399' }
])

// 1. 销售目标完成率数据
const targetData = ref({})
// 2. 订单趋势数据
const trendData = ref({})
// 3. 销售额占比数据
const shareData = ref({})
const shareDimension = ref('customer')
// 4. 毛利分析数据
const profitData = ref({})
// 5. 提成计算数据
const commissionData = ref({})
const commissionMonth = ref(`${currentYear}-${String(new Date().getMonth() + 1).padStart(2, '0')}`)
// 提成率自 2026-07 起改为历史留痕（commission_rate 表），按订单 orderDate 自动匹配生效版本
// 此处仅保留 basis（提成基数）切换，baseRate/overAchievementMultiplier/overAchievementBonus 由后端配置驱动
const commissionParams = reactive({
  basis: 'order'
})

const router = useRouter()

// 列定义（列设置 + 动态渲染用）：type 标记单元格渲染分支
// 表1 员工完成明细 / 表2 低毛利订单 / 表3 提成明细 三张表各用独立 pageKey，互不影响
const targetColumns = [
  { prop: 'employeeName', label: '姓名', minWidth: 90 },
  { prop: 'deptName', label: '部门', minWidth: 100 },
  { prop: 'annualTarget', label: '年度目标', align: 'right', width: 110, type: 'money' },
  { prop: 'actualAmount', label: '实际达成', align: 'right', width: 110, type: 'money' },
  { prop: 'completionRate', label: '完成率', align: 'center', width: 100, type: 'rate' },
  { prop: 'gap', label: '差距', align: 'right', width: 100, type: 'gap' }
]
const profitColumns = [
  { prop: 'orderNo', label: '订单号', minWidth: 160 },
  { prop: 'customerName', label: '客户', minWidth: 140 },
  { prop: 'orderDate', label: '下单日期', width: 110 },
  { prop: 'revenue', label: '收入', align: 'right', width: 100, type: 'money' },
  { prop: 'cost', label: '成本', align: 'right', width: 100, type: 'money' },
  { prop: 'profit', label: '毛利', align: 'right', width: 100, type: 'money' },
  { prop: 'profitRate', label: '毛利率', align: 'center', width: 90, type: 'profitRate' }
]
const commissionColumns = [
  { prop: 'salerName', label: '销售员', minWidth: 100 },
  { prop: 'basisAmount', label: '提成基数', align: 'right', minWidth: 130, type: 'money', dynamicLabel: true },
  { prop: 'monthlyTarget', label: '月度目标', align: 'right', minWidth: 120, type: 'money' },
  { prop: 'completionRate', label: '完成率', align: 'center', minWidth: 100, type: 'rate' },
  { prop: 'baseRate', label: '提成率', align: 'center', minWidth: 90, type: 'percent' },
  { prop: 'overAchieved', label: '超额部分', align: 'right', minWidth: 120, type: 'over' },
  { prop: 'commission', label: '应发提成', align: 'right', minWidth: 130, type: 'commission' }
]
// 表1：员工完成明细
const { columnSettingVisible: targetColVisible, openColumnSetting: openTargetCol, isColVisible: isTargetCol, getColFixed: getTargetFixed, configVersion: targetVer, sortedColumns: targetSorted, onHeaderDragend: onTargetDragend, applySavedWidths: applyTargetWidths } = useColumnSetting('SaleReportCenter-target', targetColumns)
const targetTableRef = ref(null)
const targetVisible = computed(() => targetSorted.value.filter(c => isTargetCol(c.prop)))
// 表2：低毛利订单
const { columnSettingVisible: profitColVisible, openColumnSetting: openProfitCol, isColVisible: isProfitCol, getColFixed: getProfitFixed, configVersion: profitVer, sortedColumns: profitSorted, onHeaderDragend: onProfitDragend, applySavedWidths: applyProfitWidths } = useColumnSetting('SaleReportCenter-profit', profitColumns)
const profitTableRef = ref(null)
const profitVisible = computed(() => profitSorted.value.filter(c => isProfitCol(c.prop)))
// 表3：提成明细
const { columnSettingVisible: commissionColVisible, openColumnSetting: openCommissionCol, isColVisible: isCommissionCol, getColFixed: getCommissionFixed, configVersion: commissionVer, sortedColumns: commissionSorted, onHeaderDragend: onCommissionDragend, applySavedWidths: applyCommissionWidths } = useColumnSetting('SaleReportCenter-commission', commissionColumns)
const commissionTableRef = ref(null)
const commissionVisible = computed(() => commissionSorted.value.filter(c => isCommissionCol(c.prop)))

// ECharts 图表实例引用
const gaugeChart = ref(null)
const quarterChart = ref(null)
const trendChart = ref(null)
const shareChart = ref(null)

let gaugeChartInstance = null
let quarterChartInstance = null
let trendChartInstance = null
let shareChartInstance = null

// 基数列标签（根据 basis 显示）
const basisLabel = computed(() => {
  return { order: '订单额', payment: '回款额', profit: '毛利额' }[commissionParams.basis] || '基数'
})

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

// 工具函数：根据完成率返回 el-tag 类型
function rateType(rate, isProfit = false) {
  const v = Number(rate)
  const actual = Math.abs(v) > 1 ? v : v * 100
  if (isProfit) {
    if (actual >= 30) return 'success'
    if (actual >= 10) return 'warning'
    return 'danger'
  }
  if (actual >= 100) return 'success'
  if (actual >= 60) return 'warning'
  return 'danger'
}

// 工具函数：毛利率样式
function profitRateClass(rate) {
  const v = Number(rate)
  const actual = Math.abs(v) > 1 ? v : v * 100
  if (actual >= 30) return 'profit'
  if (actual >= 10) return 'cost'
  return 'danger'
}

// ========== 加载：销售目标完成率 ==========
async function loadTarget() {
  loading.target = true
  try {
    const data = await saleTargetCompletion({ year: filterYear.value })
    targetData.value = data || {}
    // 同步KPI：本年销售总额、目标完成率
    kpiCards.value[0].value = '¥' + fmt(data.companyActualAmount)
    kpiCards.value[1].value = pct(data.companyCompletionRate)
    await nextTick()
    renderGauge(data)
    renderQuarter(data.quarters || [])
  } catch (e) {
    ElMessage.error(e.message || tt('销售目标完成率加载失败'))
  } finally {
    loading.target = false
    // 数据到位后再套一次已保存的列宽（表格重建后列对象是新的）
    nextTick(() => applyTargetWidths(targetTableRef))
  }
}

// 渲染：公司年度目标完成率 gauge
function renderGauge(data) {
  if (!gaugeChartInstance) {
    gaugeChartInstance = echarts.init(gaugeChart.value)
  }
  const rate = Number(data.companyCompletionRate || 0)
  const rateVal = Math.abs(rate) > 1 ? rate : rate * 100
  const option = {
    tooltip: { formatter: '{b} <br/>完成率: {c}%' },
    series: [
      {
        name: '目标完成率',
        type: 'gauge',
        radius: '90%',
        center: ['50%', '60%'],
        startAngle: 200,
        endAngle: -20,
        min: 0,
        max: 150,
        progress: { show: true, width: 14 },
        axisLine: { lineStyle: { width: 14, color: [[0.6, '#f56c6c'], [1, '#e6a23c'], [1.5, '#67c23a']] } },
        pointer: { width: 5, length: '60%' },
        axisTick: { show: false },
        splitLine: { length: 10, lineStyle: { color: '#fff', width: 2 } },
        axisLabel: { distance: 18, color: '#909399', fontSize: 10 },
        detail: {
          valueAnimation: true,
          formatter: '{value}%',
          fontSize: 20,
          fontWeight: 700,
          color: '#303133',
          offsetCenter: [0, '30%']
        },
        title: { show: true, offsetCenter: [0, '60%'], fontSize: 12, color: '#909399' },
        data: [{ value: Number(rateVal.toFixed(2)), name: '公司年度完成率' }]
      }
    ]
  }
  gaugeChartInstance.setOption(option, true)
}

// 渲染：季度完成情况柱状图
function renderQuarter(quarters) {
  if (!quarterChartInstance) {
    quarterChartInstance = echarts.init(quarterChart.value)
  }
  const names = quarters.map(q => 'Q' + q.quarter)
  const targets = quarters.map(q => Number(q.target || 0))
  const actuals = quarters.map(q => Number(q.actual || 0))
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
    legend: { data: ['目标', '实际'], top: 0, right: 0, textStyle: { fontSize: 12 } },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '18%', containLabel: true },
    xAxis: {
      type: 'category',
      data: names,
      axisLine: { lineStyle: { color: '#e4e7ed' } },
      axisLabel: { color: '#606266' }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: '#f5f7fa' } },
      axisLabel: { color: '#909399', formatter: (v) => v >= 10000 ? (v / 10000).toFixed(1) + 'w' : v }
    },
    series: [
      {
        name: '目标',
        type: 'bar',
        barGap: 0,
        data: targets,
        itemStyle: { color: '#c0c4cc', borderRadius: [4, 4, 0, 0] }
      },
      {
        name: '实际',
        type: 'bar',
        data: actuals,
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
  quarterChartInstance.setOption(option, true)
}

// ========== 加载：订单趋势 ==========
async function loadTrend() {
  loading.trend = true
  try {
    // 默认拉取近12个月
    const data = await saleOrderTrend({ months: 12 })
    trendData.value = data || {}
    // 同步KPI：本月订单数（取数组最后一项）
    const counts = data.orderCounts || []
    kpiCards.value[2].value = String(counts[counts.length - 1] || 0)
    await nextTick()
    renderTrend(data)
  } catch (e) {
    ElMessage.error(e.message || tt('订单趋势加载失败'))
  } finally {
    loading.trend = false
  }
}

// 渲染：订单趋势折线图
function renderTrend(data) {
  if (!trendChartInstance) {
    trendChartInstance = echarts.init(trendChart.value)
  }
  const option = {
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        let html = params[0].name + '<br/>'
        params.forEach(p => {
          const suffix = p.seriesName === '订单金额' ? '¥' + fmt(p.value) : p.value + ' 单'
          html += `${p.marker}${p.seriesName}: ${suffix}<br/>`
        })
        return html
      }
    },
    legend: { data: ['订单数', '订单金额'], top: 0, right: 0, textStyle: { fontSize: 12 } },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '15%', containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: data.months || [],
      axisLine: { lineStyle: { color: '#e4e7ed' } },
      axisLabel: { color: '#909399' }
    },
    yAxis: [
      {
        type: 'value',
        name: '订单数',
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
        name: '订单数',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: data.orderCounts || [],
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
        name: '订单金额',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: data.orderAmounts || [],
        itemStyle: { color: '#67c23a' },
        lineStyle: { width: 3, color: '#67c23a' }
      }
    ]
  }
  trendChartInstance.setOption(option, true)
}

// ========== 加载：销售额占比 ==========
async function loadShare() {
  loading.share = true
  try {
    const data = await saleShare({ dimension: shareDimension.value })
    shareData.value = data || {}
    await nextTick()
    renderShare(data)
  } catch (e) {
    ElMessage.error(e.message || tt('销售额占比加载失败'))
  } finally {
    loading.share = false
  }
}

// 渲染：销售额占比饼图
function renderShare(data) {
  if (!shareChartInstance) {
    shareChartInstance = echarts.init(shareChart.value)
  }
  const items = (data.items || []).map(item => ({
    name: item.name || '未命名',
    value: Number(item.amount || 0),
    orderCount: item.orderCount,
    percentage: item.percentage
  }))
  const option = {
    tooltip: {
      trigger: 'item',
      formatter: (p) => {
        return `${p.name}<br/>金额: ¥${fmt(p.value)}<br/>订单: ${p.data.orderCount || 0} 单<br/>占比: ${p.percent}%`
      }
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
        name: '销售额占比',
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
        data: items
      }
    ]
  }
  shareChartInstance.setOption(option, true)
}

// ========== 加载：毛利分析 ==========
async function loadProfit() {
  loading.profit = true
  try {
    const data = await saleGrossProfit()
    profitData.value = data || {}
    // 同步KPI：本月毛利率
    kpiCards.value[3].value = pct(data.totalProfitRate)
  } catch (e) {
    ElMessage.error(e.message || tt('毛利分析加载失败'))
  } finally {
    loading.profit = false
    // 数据到位后再套一次已保存的列宽（表格重建后列对象是新的）
    nextTick(() => applyProfitWidths(profitTableRef))
  }
}

// ========== 加载：提成计算 ==========
async function loadCommission() {
  loading.commission = true
  try {
    const [year, month] = (commissionMonth.value || '').split('-')
    // 提成率由后端 commission_rate 表按订单 orderDate 自动匹配生效版本，无需前端传参
    const params = {
      year: Number(year) || currentYear,
      month: Number(month) || new Date().getMonth() + 1,
      basis: commissionParams.basis
    }
    const data = await saleCommission(params)
    commissionData.value = data || {}
  } catch (e) {
    ElMessage.error(e.message || tt('提成计算加载失败'))
  } finally {
    loading.commission = false
    // 数据到位后再套一次已保存的列宽（表格重建后列对象是新的）
    nextTick(() => applyCommissionWidths(commissionTableRef))
  }
}

// 跳转到设置中心 → 业务 → 提成率配置
function goToCommissionSettings() {
  router.push('/system/settings?group=business&item=commission-rate')
}

// 提成表格合计行
function commissionSummary({ data }) {
  const sum = (key) => (data || []).reduce((acc, cur) => acc + Number(cur[key] || 0), 0)
  return [
    '合计',
    '',
    '¥' + fmt(sum('basisAmount')),
    '¥' + fmt(sum('monthlyTarget')),
    '',
    '',
    '¥' + fmt(sum('overAchieved')),
    '¥' + fmt(sum('commission'))
  ]
}

// 全部加载
async function loadAll() {
  loading.all = true
  loading.kpi = true
  try {
    await Promise.all([loadTarget(), loadTrend(), loadShare(), loadProfit(), loadCommission()])
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
  'target-completion': '销售目标完成率',
  'order-trend': '订单趋势分析',
  'sales-share': '销售额占比',
  'gross-profit': '毛利分析',
  'commission': '提成计算'
}

const reportDetailApiMap = {
  'target-completion': saleTargetCompletionDetails,
  'order-trend': saleOrderTrendDetails,
  'sales-share': saleShareDetails,
  'gross-profit': saleGrossProfitDetails,
  'commission': saleCommissionDetails
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
  'target-completion': [
    { label: '姓名', prop: 'employeeName' },
    { label: '部门', prop: 'deptName' },
    { label: '年度目标', prop: 'annualTarget' },
    { label: '实际达成', prop: 'actualAmount' },
    { label: '完成率', prop: 'completionRate' },
    { label: '差距', prop: 'gap' }
  ],
  'order-trend': [
    { label: '月份', prop: 'month' },
    { label: '订单数', prop: 'orderCount' },
    { label: '订单金额', prop: 'orderAmount' }
  ],
  'sales-share': [
    { label: '名称', prop: 'dimensionName' },
    { label: '订单数', prop: 'orderCount' },
    { label: '金额', prop: 'amount' },
    { label: '占比', prop: 'percentage' }
  ],
  'gross-profit': [
    { label: '订单号', prop: 'orderNo' },
    { label: '客户', prop: 'customerName' },
    { label: '订单日期', prop: 'orderDate' },
    { label: '销售金额', prop: 'saleAmount' },
    { label: '成本', prop: 'costAmount' },
    { label: '毛利', prop: 'grossProfit' },
    { label: '毛利率', prop: 'grossProfitRate' }
  ],
  'commission': [
    { label: '销售员', prop: 'salerName' },
    { label: '订单号', prop: 'orderNo' },
    { label: '订单日期', prop: 'orderDate' },
    { label: '提成率版本', prop: 'rateVersionNo' },
    { label: '版本生效日', prop: 'rateEffectiveDate' },
    { label: '订单金额', prop: 'amount' },
    { label: '提成基数', prop: 'basis' },
    { label: '提成率', prop: 'commissionRate' },
    { label: '提成金额', prop: 'commission' }
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
    exportCsv(columns, rows, currentReportTitle.value || '销售报表明细')
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
  gaugeChartInstance?.resize()
  quarterChartInstance?.resize()
  trendChartInstance?.resize()
  shareChartInstance?.resize()
}

onMounted(async () => {
  await nextTick()
  loadAll()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  gaugeChartInstance?.dispose()
  quarterChartInstance?.dispose()
  trendChartInstance?.dispose()
  shareChartInstance?.dispose()
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

  .nav-links {
    display: flex;
    gap: 8px;
    margin-top: 8px;
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

.report-table {
  :deep(.el-table__header th) {
    background: rgba(64, 158, 255, 0.04) !important;
    color: #1f2937;
    font-weight: 600;
  }
}

/* 占比汇总 */
.share-summary {
  display: flex;
  justify-content: flex-end;
  gap: 24px;
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #ebeef5;
}

/* 毛利 KPI */
.profit-kpi-row {
  margin-bottom: 8px;
}

.profit-kpi {
  background: #f8fafc;
  border-radius: 8px;
  padding: 12px;
  text-align: center;

  .profit-kpi-label {
    font-size: 12px;
    color: #909399;
    margin-bottom: 6px;
  }

  .profit-kpi-value {
    font-size: 16px;
    font-weight: 700;
    color: #1f2937;

    &.cost { color: #f56c6c; }
    &.profit { color: #67c23a; }
    &.danger { color: #f56c6c; }
  }
}

/* 提成表头配置区 */
.commission-header {
  flex-wrap: wrap;
  gap: 8px;
}

.commission-config {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;

  .config-label {
    font-size: 12px;
    color: #606266;
    margin-left: 4px;
  }
}

.commission-amount {
  color: #f56c6c;
  font-weight: 700;
}

.commission-total {
  text-align: right;
  font-size: 14px;
  color: #303133;
  padding: 12px 8px 0;
  font-weight: 600;
}

/* 提成率版本提示条 */
.rate-versions-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 12px;
  margin-bottom: 10px;
  background: #f0f9ff;
  border: 1px solid #d0e8ff;
  border-radius: 4px;
  font-size: 12px;
  color: #303133;

  &.warn {
    background: #fff7e6;
    border-color: #ffd591;
    color: #d46b08;
    gap: 6px;
  }

  .rv-label {
    color: #606266;
    font-weight: 600;
  }

  .rv-tag {
    margin: 0;
  }

  .rv-hint {
    color: #909399;
    font-size: 11px;
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
  .commission-config { width: 100%; }
}
</style>
