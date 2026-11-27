<template>
  <div class="report-container">
    <!-- 顶部页面标题 -->
    <div class="page-header">
      <h2 class="page-title">{{ $tt("生产报表中心") }}</h2>
    </div>

    <!-- 全局筛选栏：日期范围 + 关键字搜索 -->
    <div class="filter-bar" style="margin-bottom: 12px; display: flex; gap: 8px; align-items: center; flex-wrap: wrap;">
      <el-date-picker v-model="dateRange" type="daterange" :range-separator="$tt('至')"
        :start-placeholder="$tt('开始日期')" :end-placeholder="$tt('结束日期')"
        value-format="YYYY-MM-DD" unlink-panels clearable style="width: 280px" />
      <el-input v-model="keyword" :placeholder="$tt('搜索关键字（工单号/产品/负责人）')"
        clearable style="width: 240px" @keyup.enter="loadAll" />
      <el-button type="primary" :loading="loading" @click="loadAll">
        <el-icon><Search /></el-icon>查询
      </el-button>
      <el-button @click="resetFilter">{{ $tt("重置") }}</el-button>
    </div>

    <!-- 报表1：工单完成率 -->
    <el-card shadow="never" class="report-card" v-loading="loadingCompletion">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $tt("工单完成率") }}</span>
          <div style="display: flex; align-items: center; gap: 8px;">
            <el-tag size="small" type="danger">延期工单 {{ completionData.delayedCount || 0 }} 笔</el-tag>
            <el-button size="small" type="primary" plain @click="openDetail('work-order-completion')">
              <el-icon><View /></el-icon> 查看明细
            </el-button>
          </div>
        </div>
      </template>
      <!-- KPI 卡片行 -->
      <div class="kpi-row">
        <div
          v-for="kpi in workOrderKpis"
          :key="kpi.label"
          class="kpi-box"
          :style="{ background: kpi.bg }"
        >
          <div class="kpi-label">{{ $tf(kpi.label) }}</div>
          <div class="kpi-value" :style="{ color: kpi.color }">{{ kpi.value }}</div>
        </div>
      </div>
      <!-- 延期工单表格 -->
      <div class="table-title">{{ $tt("延期工单明细") }}</div>
      <el-table :data="completionData.delayedOrders || []" size="small" border stripe max-height="320">
        <el-table-column prop="orderNo" :label="$tf('工单号')" min-width="140" />
        <el-table-column prop="materialName" :label="$tf('物料')" min-width="160" show-overflow-tooltip />
        <el-table-column prop="quantity" :label="$tf('数量')" width="100" align="right" />
        <el-table-column prop="planEndDate" :label="$tf('计划完成日')" width="130" />
        <el-table-column prop="actualEndDate" :label="$tf('实际完成日')" width="130" />
        <el-table-column :label="$tf('延期天数')" width="110" align="right">
          <template #default="{ row }">
            <el-tag type="danger" size="small">{{ row.delayDays }} 天</el-tag>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="$tt('暂无延期工单')" :image-size="80" />
        </template>
      </el-table>
    </el-card>

    <!-- 报表2：生产效率分析 -->
    <el-card shadow="never" class="report-card" v-loading="loadingEfficiency">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $tt("生产效率分析") }}</span>
          <el-button size="small" type="primary" plain @click="openDetail('efficiency')">
            <el-icon><View /></el-icon> 查看明细
          </el-button>
        </div>
      </template>
      <!-- KPI 卡片行 -->
      <div class="kpi-row">
        <div
          v-for="kpi in efficiencyKpis"
          :key="kpi.label"
          class="kpi-box"
          :style="{ background: kpi.bg }"
        >
          <div class="kpi-label">{{ $tf(kpi.label) }}</div>
          <div class="kpi-value" :style="{ color: kpi.color }">{{ kpi.value }}</div>
        </div>
      </div>
      <!-- 员工产量折线图 -->
      <div ref="employeeOutputChart" class="chart-box"></div>
      <!-- 员工报工明细 -->
      <div class="table-title">{{ $tt("员工报工明细") }}</div>
      <el-table :data="efficiencyData.byEmployee || []" size="small" border stripe max-height="360">
        <el-table-column prop="employeeName" :label="$tf('员工')" min-width="120" />
        <el-table-column prop="reportCount" :label="$tf('报工数')" width="100" align="right" />
        <el-table-column prop="totalQuantity" :label="$tf('总产量')" width="110" align="right" />
        <el-table-column prop="qualifiedQuantity" :label="$tf('合格量')" width="110" align="right" />
        <el-table-column :label="$tf('良率')" width="110" align="right">
          <template #default="{ row }">
            <el-tag :type="yieldRateType(row.yieldRate)" size="small">{{ row.yieldRate }}%</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="workHours" :label="$tf('工时(h)')" width="100" align="right" />
        <el-table-column :label="$tf('效率')" width="100" align="right">
          <template #default="{ row }">
            <span :style="{ color: row.efficiency >= 100 ? '#67c23a' : '#e6a23c', fontWeight: 600 }">
              {{ row.efficiency }}%
            </span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="$tt('暂无员工报工数据')" :image-size="80" />
        </template>
      </el-table>
    </el-card>

    <!-- 报表3：生产成本分析 -->
    <el-card shadow="never" class="report-card" v-loading="loadingCost">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $tt("生产成本分析") }}</span>
          <div style="display: flex; align-items: center; gap: 8px;">
            <el-tag size="small" type="info">核算单数 {{ costData.totalCalculations || 0 }} 笔</el-tag>
            <el-button size="small" type="primary" plain @click="openDetail('cost-analysis')">
              <el-icon><View /></el-icon> 查看明细
            </el-button>
          </div>
        </div>
      </template>
      <!-- KPI 卡片行 -->
      <div class="kpi-row">
        <div
          v-for="kpi in costKpis"
          :key="kpi.label"
          class="kpi-box"
          :style="{ background: kpi.bg }"
        >
          <div class="kpi-label">{{ $tf(kpi.label) }}</div>
          <div class="kpi-value" :style="{ color: kpi.color }">{{ kpi.value }}</div>
        </div>
      </div>
      <!-- 饼图 + 成本明细表格 -->
      <el-row :gutter="16">
        <el-col :xs="24" :md="10">
          <div ref="costPieChart" class="chart-box"></div>
        </el-col>
        <el-col :xs="24" :md="14">
          <div class="table-title">{{ $tt("成本核算明细") }}</div>
          <el-table :data="costData.items || []" size="small" border stripe max-height="380">
            <el-table-column prop="calcNo" :label="$tf('核算单号')" min-width="130" />
            <el-table-column prop="materialName" :label="$tf('物料')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="quantity" :label="$tf('数量')" width="90" align="right" />
            <el-table-column :label="$tf('材料成本')" width="120" align="right">
              <template #default="{ row }">¥{{ formatMoney(row.directMaterialCost) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('人工成本')" width="120" align="right">
              <template #default="{ row }">¥{{ formatMoney(row.directLaborCost) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('制造费用')" width="120" align="right">
              <template #default="{ row }">¥{{ formatMoney(row.manufacturingOverhead) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('总成本')" width="130" align="right">
              <template #default="{ row }">¥{{ formatMoney(row.totalCost) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('单位成本')" width="120" align="right">
              <template #default="{ row }">¥{{ formatMoney(row.unitCost) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('状态')" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">{{ $dt(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty :description="$tt('暂无成本核算数据')" :image-size="80" />
            </template>
          </el-table>
        </el-col>
      </el-row>
    </el-card>

    <!-- 报表4：工时与计件分析 -->
    <el-card shadow="never" class="report-card" v-loading="loadingWorkHours">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $tt("工时与计件分析") }}</span>
        </div>
      </template>
      <!-- 头部筛选：日期范围（默认本月）+ 分组维度 -->
      <div class="filter-bar" style="margin-bottom: 12px; display: flex; gap: 8px; align-items: center; flex-wrap: wrap;">
        <el-date-picker v-model="workHoursDateRange" type="daterange" :range-separator="$tt('至')"
          :start-placeholder="$tt('开始日期')" :end-placeholder="$tt('结束日期')"
          value-format="YYYY-MM-DD" unlink-panels clearable style="width: 280px" />
        <el-select v-model="workHoursGroupBy" style="width: 160px">
          <el-option :label="$tt('物料')" value="material" />
          <el-option :label="$tt('工序')" value="operation" />
          <el-option :label="$tt('工作中心')" value="workcenter" />
          <el-option :label="$tt('员工')" value="employee" />
        </el-select>
        <el-button type="primary" :loading="loadingWorkHours" @click="loadWorkHours">
          <el-icon><Search /></el-icon>查询
        </el-button>
      </div>
      <!-- KPI 卡片行 -->
      <div class="kpi-row">
        <div
          v-for="kpi in workHoursKpis"
          :key="kpi.label"
          class="kpi-box"
          :style="{ background: kpi.bg }"
        >
          <div class="kpi-label">{{ $tf(kpi.label) }}</div>
          <div class="kpi-value" :style="{ color: kpi.color }">{{ kpi.value }}</div>
        </div>
      </div>
      <!-- 表A：工时误差 -->
      <div class="table-title">{{ $tt("工时误差") }}</div>
      <el-table :data="workHoursGroups" size="small" border stripe max-height="360">
        <el-table-column prop="group" :label="$tf('分组')" min-width="150" show-overflow-tooltip />
        <el-table-column :label="$tf('样本数')" width="90" align="right">
          <template #default="{ row }">{{ whCount(row.sampleCount) }}</template>
        </el-table-column>
        <el-table-column :label="$tf('计划工时(h)')" width="120" align="right">
          <template #default="{ row }">{{ whNum(row.planHours) }}</template>
        </el-table-column>
        <el-table-column :label="$tf('实际工时(h)')" width="120" align="right">
          <template #default="{ row }">{{ whNum(row.actualHours) }}</template>
        </el-table-column>
        <el-table-column :label="$tf('误差(h)')" width="110" align="right">
          <template #default="{ row }">{{ whNum(row.variance) }}</template>
        </el-table-column>
        <el-table-column :label="$tf('误差率(%)')" width="110" align="right">
          <template #default="{ row }">{{ whRate(row.varianceRate) }}</template>
        </el-table-column>
        <el-table-column :label="$tf('中位数(%)')" width="110" align="right">
          <template #default="{ row }">{{ whRate(row.medianRate) }}</template>
        </el-table-column>
        <el-table-column :label="$tf('P90(%)')" width="100" align="right">
          <template #default="{ row }">{{ whRate(row.p90Rate) }}</template>
        </el-table-column>
        <el-table-column :label="$tf('常态区间(%)')" width="150" align="center">
          <template #default="{ row }">{{ whRange(row.normalLow, row.normalHigh) }}</template>
        </el-table-column>
        <el-table-column :label="$tf('判定')" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.verdict" :type="verdictTagType(row.verdict)" size="small">{{ row.verdict }}</el-tag>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="$tt('本期还没有工时数据：工序在生产工单里开工/完工后会自动累计')" :image-size="80" />
        </template>
      </el-table>
      <!-- 表B：计件与工时 -->
      <div class="table-title">{{ $tt("计件与工时") }}</div>
      <el-table :data="workHoursPiece" size="small" border stripe max-height="320">
        <el-table-column prop="group" :label="$tf('分组')" min-width="150" show-overflow-tooltip />
        <el-table-column :label="$tf('合格数量')" width="120" align="right">
          <template #default="{ row }">{{ whCount(row.qualifiedQty) }}</template>
        </el-table-column>
        <el-table-column :label="$tf('计件工资')" width="130" align="right">
          <template #default="{ row }">{{ whMoney(row.pieceAmount) }}</template>
        </el-table-column>
        <el-table-column :label="$tf('计划工时(h)')" width="120" align="right">
          <template #default="{ row }">{{ whNum(row.planHours) }}</template>
        </el-table-column>
        <el-table-column :label="$tf('实际工时(h)')" width="120" align="right">
          <template #default="{ row }">{{ whNum(row.actualHours) }}</template>
        </el-table-column>
        <template #empty>
          <el-empty :description="$tt('本期还没有计件数据：工序报工合格后会自动累计')" :image-size="80" />
        </template>
      </el-table>
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
          <template v-if="currentReportKey === 'work-order-completion'">
            <el-table-column prop="workOrderNo" :label="$tf('工单号')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="productName" :label="$tf('产品')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="plannedQty" :label="$tf('计划数量')" width="110" align="right" />
            <el-table-column prop="completedQty" :label="$tf('完成数量')" width="110" align="right" />
            <el-table-column :label="$tf('完成率')" align="center" width="110">
              <template #default="{ row }">{{ pct(row.completionRate) }}</template>
            </el-table-column>
            <el-table-column prop="status" :label="$tf('状态')" width="100" align="center">
              <template #default="{ row }">{{ $dt(row.status) }}</template>
            </el-table-column>
          </template>
          <template v-else-if="currentReportKey === 'efficiency'">
            <el-table-column prop="workOrderNo" :label="$tf('工单号')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="productName" :label="$tf('产品')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="plannedHours" :label="$tf('计划工时')" width="110" align="right" />
            <el-table-column prop="actualHours" :label="$tf('实际工时')" width="110" align="right" />
            <el-table-column :label="$tf('效率')" align="center" width="110">
              <template #default="{ row }">
                <span :style="{ color: row.efficiency >= 100 ? '#67c23a' : '#f56c6c' }">
                  {{ pct(row.efficiency) }}
                </span>
              </template>
            </el-table-column>
          </template>
          <template v-else-if="currentReportKey === 'cost-analysis'">
            <el-table-column prop="workOrderNo" :label="$tf('工单号')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="productName" :label="$tf('产品')" min-width="160" show-overflow-tooltip />
            <el-table-column :label="$tf('材料成本')" align="right" width="130">
              <template #default="{ row }">¥{{ fmt(row.materialCost) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('人工成本')" align="right" width="130">
              <template #default="{ row }">¥{{ fmt(row.laborCost) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('制造费用')" align="right" width="130">
              <template #default="{ row }">¥{{ fmt(row.overheadCost) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('总成本')" align="right" width="140">
              <template #default="{ row }">¥{{ fmt(row.totalCost) }}</template>
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
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import echarts from '@/utils/echartsCore'
import { ElMessage } from 'element-plus'
import { View, Download, Search } from '@element-plus/icons-vue'
import {
  productionWorkOrderCompletion,
  productionEfficiency,
  productionCostAnalysis,
  productionWorkHours,
  productionWorkOrderCompletionDetails,
  productionEfficiencyDetails,
  productionCostAnalysisDetails
} from '@/api/report'
import { exportCsv } from '@/utils/csvExport'
import { tt } from '@/i18n'

// 查询条件
const dateRange = ref([])
const keyword = ref('')
const loading = ref(false)
const loadingCompletion = ref(false)
const loadingEfficiency = ref(false)
const loadingCost = ref(false)
const loadingWorkHours = ref(false)

// 四张报表的数据
const completionData = ref({})
const efficiencyData = ref({})
const costData = ref({})
// 第四张报表：工时与计件分析（日期默认本月，分组维度默认物料）
const workHoursData = ref({})
const workHoursGroupBy = ref('material')
const workHoursDateRange = ref(currentMonthRange())

// 图表 DOM 引用
const employeeOutputChart = ref(null)
const costPieChart = ref(null)
// 图表实例
let employeeOutputChartInstance = null
let costPieChartInstance = null

// 工单完成率 KPI
const workOrderKpis = computed(() => {
  const d = completionData.value || {}
  return [
    { label: '总工单', value: d.totalOrders ?? 0, color: '#409eff', bg: '#ecf5ff' },
    { label: '已完成', value: d.completed ?? 0, color: '#67c23a', bg: '#f0f9eb' },
    { label: '进行中', value: d.inProgress ?? 0, color: '#e6a23c', bg: '#fdf6ec' },
    { label: '完成率', value: (d.completionRate ?? 0) + '%', color: '#409eff', bg: '#ecf5ff' },
    { label: '准时完成率', value: (d.onTimeCompletion ?? 0) + '%', color: '#67c23a', bg: '#f0f9eb' }
  ]
})

// 生产效率 KPI
const efficiencyKpis = computed(() => {
  const d = efficiencyData.value || {}
  return [
    { label: '报工总数', value: d.totalReports ?? 0, color: '#409eff', bg: '#ecf5ff' },
    { label: '总良率', value: (d.totalYieldRate ?? 0) + '%', color: '#67c23a', bg: '#f0f9eb' }
  ]
})

// 生产成本 KPI
const costKpis = computed(() => {
  const d = costData.value || {}
  return [
    { label: '总成本', value: '¥' + formatMoney(d.totalCost), color: '#f56c6c', bg: '#fef0f0' },
    { label: '材料成本', value: '¥' + formatMoney(d.totalMaterialCost), color: '#409eff', bg: '#ecf5ff' },
    { label: '人工成本', value: '¥' + formatMoney(d.totalLaborCost), color: '#67c23a', bg: '#f0f9eb' },
    { label: '制造费用', value: '¥' + formatMoney(d.totalOverheadCost), color: '#e6a23c', bg: '#fdf6ec' }
  ]
})

// 工时与计件分析 KPI
const workHoursKpis = computed(() => {
  const k = (workHoursData.value && workHoursData.value.kpi) || {}
  return [
    { label: '总计划工时(h)', value: whNum(k.planHours), color: '#409eff', bg: '#ecf5ff' },
    { label: '总实际工时(h)', value: whNum(k.actualHours), color: '#67c23a', bg: '#f0f9eb' },
    { label: '总误差率', value: whRate(k.varianceRate), color: '#e6a23c', bg: '#fdf6ec' },
    { label: '异常分组数', value: whCount(k.abnormalGroups), color: '#f56c6c', bg: '#fef0f0' }
  ]
})

// 工时误差分组 / 计件与工时明细
const workHoursGroups = computed(() => (workHoursData.value && workHoursData.value.groups) || [])
const workHoursPiece = computed(() => (workHoursData.value && workHoursData.value.piece) || [])

// 当前自然月日期范围（yyyy-MM-dd ~ yyyy-MM-dd），作为工时分析默认查询区间
function currentMonthRange() {
  const now = new Date()
  const fmt = (d) => d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0')
  const first = new Date(now.getFullYear(), now.getMonth(), 1)
  const last = new Date(now.getFullYear(), now.getMonth() + 1, 0)
  return [fmt(first), fmt(last)]
}

// 工时/金额格式化：保留 2 位小数，空值/非法值留空（不显示 NaN/Infinity/占位符）
function whNum(val) {
  if (val === null || val === undefined || val === '') return ''
  const n = Number(val)
  if (Number.isNaN(n) || !Number.isFinite(n)) return ''
  return n.toFixed(2)
}

// 百分比格式化：2 位小数 + %，空值留空
function whRate(val) {
  const s = whNum(val)
  return s === '' ? '' : s + '%'
}

// 计数格式化：空值留空，其余原样展示
function whCount(val) {
  if (val === null || val === undefined || val === '') return ''
  const n = Number(val)
  if (Number.isNaN(n) || !Number.isFinite(n)) return ''
  return String(n)
}

// 金额格式化：有值加 ¥ 前缀，空值留空
function whMoney(val) {
  const s = whNum(val)
  return s === '' ? '' : '¥' + s
}

// 常态区间：下界 ~ 上界（%），任一端为空则整格留空
function whRange(low, high) {
  const l = whNum(low)
  const h = whNum(high)
  if (l === '' || h === '') return ''
  return l + '% ~ ' + h + '%'
}

// 判定标签颜色：正常=中性默认，异常偏高/偏低=警示色，样本不足=灰
function verdictTagType(verdict) {
  if (verdict === '异常偏高') return 'danger'
  if (verdict === '异常偏低') return 'warning'
  if (verdict === '样本不足') return 'info'
  return ''
}

// 加载工时与计件分析
async function loadWorkHours() {
  loadingWorkHours.value = true
  try {
    const params = { groupBy: workHoursGroupBy.value }
    if (workHoursDateRange.value && workHoursDateRange.value.length === 2) {
      params.startDate = workHoursDateRange.value[0]
      params.endDate = workHoursDateRange.value[1]
    }
    const data = await productionWorkHours(params)
    workHoursData.value = data || {}
  } catch (e) {
    console.error('加载工时与计件分析失败', e)
    workHoursData.value = {}
  } finally {
    loadingWorkHours.value = false
  }
}

// 金额格式化：¥ + 千分位 + 2位小数
function formatMoney(num) {
  if (num === null || num === undefined || num === '') return '0.00'
  return Number(num).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 明细抽屉用：金额格式化（复用 formatMoney，无 ¥ 前缀）
function fmt(num) {
  return formatMoney(num)
}

// 百分比格式化
function pct(val) {
  if (val === null || val === undefined || val === '') return '0%'
  return Number(val).toFixed(2) + '%'
}

// 良率 Tag 颜色：>=95 success，>=80 warning，否则 danger
function yieldRateType(rate) {
  if (rate === null || rate === undefined) return 'info'
  if (rate >= 95) return 'success'
  if (rate >= 80) return 'warning'
  return 'danger'
}

// 状态 Tag 颜色：已生效/已完成/正常=success，进行中/生产中=warning，延期/逾期/已过期=danger
function statusTagType(status) {
  if (['已生效', '已完成', '正常'].includes(status)) return 'success'
  if (['进行中', '生产中'].includes(status)) return 'warning'
  if (['延期', '逾期', '已过期'].includes(status)) return 'danger'
  return 'info'
}

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

// 重置筛选条件并重新加载
function resetFilter() {
  dateRange.value = []
  keyword.value = ''
  workHoursDateRange.value = currentMonthRange()
  workHoursGroupBy.value = 'material'
  loadAll()
}

// 加载工单完成率
async function loadCompletion() {
  loadingCompletion.value = true
  try {
    const data = await productionWorkOrderCompletion(buildParams())
    completionData.value = data || {}
  } catch (e) {
    console.error('加载工单完成率失败', e)
  } finally {
    loadingCompletion.value = false
  }
}

// 加载生产效率
async function loadEfficiency() {
  loadingEfficiency.value = true
  try {
    const data = await productionEfficiency(buildParams())
    efficiencyData.value = data || {}
    await nextTick()
    renderEmployeeOutputChart()
  } catch (e) {
    console.error('加载生产效率失败', e)
  } finally {
    loadingEfficiency.value = false
  }
}

// 加载生产成本
async function loadCost() {
  loadingCost.value = true
  try {
    const data = await productionCostAnalysis(buildParams())
    costData.value = data || {}
    await nextTick()
    renderCostPieChart()
  } catch (e) {
    console.error('加载生产成本失败', e)
  } finally {
    loadingCost.value = false
  }
}

// 渲染员工产量折线图
function renderEmployeeOutputChart() {
  if (!employeeOutputChart.value) return
  if (!employeeOutputChartInstance) {
    employeeOutputChartInstance = echarts.init(employeeOutputChart.value)
  }
  const list = efficiencyData.value.byEmployee || []
  const names = list.map(it => it.employeeName || '未知')
  const quantities = list.map(it => Number(it.totalQuantity) || 0)
  const qualified = list.map(it => Number(it.qualifiedQuantity) || 0)
  employeeOutputChartInstance.setOption({
    title: { text: '员工产量对比', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis' },
    legend: { data: ['总产量', '合格量'], bottom: 0 },
    grid: { left: '3%', right: '4%', bottom: '14%', top: '16%', containLabel: true },
    xAxis: { type: 'category', data: names, axisLabel: { rotate: names.length > 6 ? 30 : 0 } },
    yAxis: { type: 'value' },
    series: [
      {
        name: '总产量',
        type: 'line',
        smooth: true,
        data: quantities,
        itemStyle: { color: '#409eff' },
        areaStyle: { color: 'rgba(64,158,255,0.1)' }
      },
      {
        name: '合格量',
        type: 'line',
        smooth: true,
        data: qualified,
        itemStyle: { color: '#67c23a' },
        areaStyle: { color: 'rgba(103,194,58,0.1)' }
      }
    ]
  })
}

// 渲染成本构成饼图
function renderCostPieChart() {
  if (!costPieChart.value) return
  if (!costPieChartInstance) {
    costPieChartInstance = echarts.init(costPieChart.value)
  }
  const d = costData.value || {}
  costPieChartInstance.setOption({
    title: { text: '成本构成', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'item', formatter: '{b}: ¥{c} ({d}%)' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['40%', '65%'],
      center: ['50%', '45%'],
      label: { formatter: '{b}\n{d}%' },
      data: [
        { name: '材料成本', value: Number(d.totalMaterialCost) || 0, itemStyle: { color: '#409eff' } },
        { name: '人工成本', value: Number(d.totalLaborCost) || 0, itemStyle: { color: '#67c23a' } },
        { name: '制造费用', value: Number(d.totalOverheadCost) || 0, itemStyle: { color: '#e6a23c' } }
      ]
    }]
  })
}

// 加载全部报表数据
async function loadAll() {
  loading.value = true
  try {
    await Promise.allSettled([loadCompletion(), loadEfficiency(), loadCost(), loadWorkHours()])
  } finally {
    loading.value = false
  }
}

// ==================== 明细抽屉 ====================
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

// 报表 key 与标题映射
const reportTitleMap = {
  'work-order-completion': '工单完成率',
  'efficiency': '生产效率',
  'cost-analysis': '成本分析'
}

// 报表 key 与明细 API 映射
const reportDetailApiMap = {
  'work-order-completion': productionWorkOrderCompletionDetails,
  'efficiency': productionEfficiencyDetails,
  'cost-analysis': productionCostAnalysisDetails
}

// 打开明细抽屉：继承全局筛选条件
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

// 加载明细数据
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

// 重置明细筛选条件并重新加载
function resetDetail() {
  detailDateRange.value = []
  detailKeyword.value = ''
  detailPage.value = 1
  loadDetail()
}

// 明细抽屉导出列定义（与表格列一致）
const reportDetailColumnsMap = {
  'work-order-completion': [
    { label: '工单号', prop: 'workOrderNo' },
    { label: '产品', prop: 'productName' },
    { label: '计划数量', prop: 'plannedQty' },
    { label: '完成数量', prop: 'completedQty' },
    { label: '完成率', prop: 'completionRate' },
    { label: '状态', prop: 'status' }
  ],
  'efficiency': [
    { label: '工单号', prop: 'workOrderNo' },
    { label: '产品', prop: 'productName' },
    { label: '计划工时', prop: 'plannedHours' },
    { label: '实际工时', prop: 'actualHours' },
    { label: '效率', prop: 'efficiency' }
  ],
  'cost-analysis': [
    { label: '工单号', prop: 'workOrderNo' },
    { label: '产品', prop: 'productName' },
    { label: '材料成本', prop: 'materialCost' },
    { label: '人工成本', prop: 'laborCost' },
    { label: '制造费用', prop: 'overheadCost' },
    { label: '总成本', prop: 'totalCost' }
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
    exportCsv(columns, rows, currentReportTitle.value || '生产报表明细')
    ElMessage.success(`${tt("已导出 ")}${rows.length}${tt(" 条数据")}`)
  } catch (e) {
    console.error('导出失败', e)
    ElMessage.error(e.message || tt('导出失败'))
  } finally {
    detailLoading.value = false
  }
}

// 窗口尺寸变化时重绘图表
function handleResize() {
  employeeOutputChartInstance?.resize()
  costPieChartInstance?.resize()
}

onMounted(() => {
  loadAll()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  employeeOutputChartInstance?.dispose()
  costPieChartInstance?.dispose()
})
</script>

<style scoped>
.report-container { padding: 20px; background: #f5f7fa; min-height: 100%; }
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}
.page-title { margin: 0; font-size: 20px; font-weight: 600; color: #303133; }
.header-actions { display: flex; gap: 10px; align-items: center; }
.report-card { margin-bottom: 16px; border-radius: 8px; }
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 500;
}
.card-title { font-size: 15px; font-weight: 600; color: #303133; }
.kpi-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 8px;
}
.kpi-box {
  flex: 1;
  min-width: 140px;
  padding: 16px 18px;
  border-radius: 8px;
}
.kpi-label { font-size: 13px; color: #606266; margin-bottom: 8px; }
.kpi-value { font-size: 22px; font-weight: 600; word-break: break-all; }
.table-title {
  font-size: 13px;
  color: #606266;
  font-weight: 500;
  margin: 12px 0 8px;
}
.chart-box { width: 100%; height: 320px; margin-top: 10px; }

/* 明细抽屉样式 */
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
