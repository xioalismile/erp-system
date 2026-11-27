<template>
  <div class="prod-dashboard">
    <div class="dashboard-header">
      <div class="header-left">
        <h1 class="main-title">
          <el-icon :size="32" color="#409eff"><Monitor /></el-icon>
          智能生产看板
        </h1>
      </div>
      <div class="header-center">
        <div class="current-time">{{ currentTime }}</div>
        <div class="current-date">{{ currentDate }}</div>
      </div>
      <div class="header-right">
        <el-select v-model="selectedShift" class="shift-select" size="default">
          <el-option :label="$tf('白班')" value="day" />
          <el-option :label="$tf('夜班')" value="night" />
          <el-option :label="$tf('全部')" value="all" />
        </el-select>
        <el-select v-model="selectedWorkshop" class="workshop-select" size="default">
          <el-option :label="$tf('全部车间')" value="" />
          <el-option
            v-for="ws in workshopList"
            :key="ws.workshopId"
            :label="ws.workshopName"
            :value="ws.workshopId"
          />
        </el-select>
      </div>
    </div>

    <div class="overview-section">
      <div class="overview-card" v-for="(card, index) in overviewCards" :key="index">
        <div class="card-icon" :style="{ background: card.iconBg }">
          <el-icon :size="28" :color="card.iconColor"><component :is="card.icon" /></el-icon>
        </div>
        <div class="card-content">
          <div class="card-value" :style="{ color: card.valueColor }">
            {{ card.value }}
            <span class="card-unit">{{ card.unit }}</span>
          </div>
          <div class="card-label">{{ $tf(card.label) }}</div>
          <div v-if="card.progress !== undefined" class="card-progress">
            <el-progress
              :percentage="card.progress"
              :show-text="false"
              :stroke-width="6"
              :color="card.progressColor"
            />
            <span class="progress-text">目标完成 {{ card.progress }}%</span>
          </div>
        </div>
      </div>
    </div>

    <div class="middle-section">
      <div class="section-left">
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">
              <el-icon><List /></el-icon>
              生产进度
            </span>
            <span class="panel-subtitle">{{ $tt("实时更新") }}</span>
          </div>
          <div class="progress-table-container" ref="progressTableRef">
            <div class="progress-table">
              <div class="table-header">
                <div class="th order-no">{{ $tt("生产单号") }}</div>
                <div class="th product-name">{{ $tt("产品名称") }}</div>
                <div class="th plan-qty">{{ $tt("计划数量") }}</div>
                <div class="th finished-qty">{{ $tt("已完成") }}</div>
                <div class="th progress">{{ $tt("进度") }}</div>
                <div class="th status">{{ $tt("状态") }}</div>
              </div>
              <div class="table-body" ref="tableBodyRef">
                <div
                  v-for="(item, index) in progressList"
                  :key="item.orderId"
                  class="table-row"
                  :class="{ 'even': index % 2 === 1 }"
                >
                  <div class="td order-no">{{ item.orderNo }}</div>
                  <div class="td product-name">{{ item.productName }}</div>
                  <div class="td plan-qty">{{ item.planQty }}</div>
                  <div class="td finished-qty">{{ item.finishedQty }}</div>
                  <div class="td progress">
                    <div class="progress-bar-wrapper">
                      <div
                        class="progress-bar"
                        :style="{
                          width: item.progressRate + '%',
                          background: getProgressColor(item.progressRate)
                        }"
                      ></div>
                    </div>
                    <span class="progress-text-inline">{{ item.progressRate }}%</span>
                  </div>
                  <div class="td status">
                    <span class="status-tag" :class="'status-' + getStatusClass(item.status)">
                      {{ $dt(item.status) }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="section-right">
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">
              <el-icon><PieChart /></el-icon>
              生产单状态统计
            </span>
          </div>
          <div ref="orderStatusChart" class="chart-container"></div>
        </div>
      </div>
    </div>

    <div class="bottom-section">
      <div class="bottom-panel">
        <div class="panel-header">
          <span class="panel-title">
            <el-icon><TrendCharts /></el-icon>
            今日产量趋势
          </span>
        </div>
        <div ref="todayTrendChart" class="chart-container"></div>
      </div>

      <div class="bottom-panel">
        <div class="panel-header">
          <span class="panel-title">
            <el-icon><Histogram /></el-icon>
            车间产量统计
          </span>
        </div>
        <div ref="workshopChart" class="chart-container"></div>
      </div>

      <div class="bottom-panel">
        <div class="panel-header">
          <span class="panel-title">
            <el-icon><Trophy /></el-icon>
            员工产量排行
          </span>
        </div>
        <div class="ranking-list">
          <div
            v-for="(item, index) in employeeRanking"
            :key="item.employeeId"
            class="ranking-item"
          >
            <div class="rank-badge" :class="'rank-' + (index + 1)">{{ index + 1 }}</div>
            <div class="rank-info">
              <div class="rank-name">{{ item.employeeName || '未知' }}</div>
              <div class="rank-hours">工时: {{ item.workHours || 0 }}h</div>
            </div>
            <div class="rank-data">
              <div class="rank-qty">{{ item.outputQty || 0 }} 件</div>
              <div class="rank-eff">效率 {{ item.efficiency || 0 }}%</div>
            </div>
          </div>
          <div v-if="employeeRanking.length === 0" class="empty-ranking">
            <el-empty :description="$tt('暂无数据')" :image-size="60" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick, computed } from 'vue'
import echarts from '@/utils/echartsCore'
import {
  Monitor,
  List,
  PieChart,
  TrendCharts,
  Histogram,
  Trophy,
  Goods,
  Money,
  Document,
  Clock,
  Odometer,
  Finished
} from '@element-plus/icons-vue'
import {
  getOverview,
  getOrderStatusStats,
  getProductionProgress,
  getWorkshopOutputStats,
  getEmployeeRanking,
  getTodayOutputTrend,
  getWeekOutputTrend
} from '@/api/production/productionDashboard'

const currentTime = ref('')
const currentDate = ref('')
const selectedShift = ref('all')
const selectedWorkshop = ref('')
const workshopList = ref([])
const progressList = ref([])
const employeeRanking = ref([])

const progressTableRef = ref(null)
const tableBodyRef = ref(null)
const orderStatusChart = ref(null)
const todayTrendChart = ref(null)
const workshopChart = ref(null)

let orderStatusChartInstance = null
let todayTrendChartInstance = null
let workshopChartInstance = null
let timeInterval = null
let refreshInterval = null
let scrollInterval = null

const overviewCards = reactive([
  {
    label: '今日产量',
    value: 0,
    unit: '件',
    icon: 'Goods',
    iconBg: 'linear-gradient(135deg, rgba(64, 158, 255, 0.2), rgba(64, 158, 255, 0.05))',
    iconColor: '#409eff',
    valueColor: '#409eff',
    progress: 0,
    progressColor: '#409eff'
  },
  {
    label: '今日产值',
    value: 0,
    unit: '万元',
    icon: 'Money',
    iconBg: 'linear-gradient(135deg, rgba(103, 194, 58, 0.2), rgba(103, 194, 58, 0.05))',
    iconColor: '#67c23a',
    valueColor: '#67c23a'
  },
  {
    label: '进行中生产单',
    value: 0,
    unit: '单',
    icon: 'Document',
    iconBg: 'linear-gradient(135deg, rgba(230, 162, 60, 0.2), rgba(230, 162, 60, 0.05))',
    iconColor: '#e6a23c',
    valueColor: '#e6a23c'
  },
  {
    label: '待生产单',
    value: 0,
    unit: '单',
    icon: 'Clock',
    iconBg: 'linear-gradient(135deg, rgba(245, 108, 108, 0.2), rgba(245, 108, 108, 0.05))',
    iconColor: '#f56c6c',
    valueColor: '#f56c6c'
  },
  {
    label: '整体效率',
    value: 0,
    unit: '%',
    icon: 'Odometer',
    iconBg: 'linear-gradient(135deg, rgba(155, 89, 182, 0.2), rgba(155, 89, 182, 0.05))',
    iconColor: '#9b59b6',
    valueColor: '#9b59b6'
  },
  {
    label: '准时交付率',
    value: 0,
    unit: '%',
    icon: 'Finished',
    iconBg: 'linear-gradient(135deg, rgba(26, 188, 156, 0.2), rgba(26, 188, 156, 0.05))',
    iconColor: '#1abc9c',
    valueColor: '#1abc9c'
  }
])

const updateTime = () => {
  const now = new Date()
  currentTime.value = now.toLocaleTimeString('zh-CN', { hour12: false })
  const options = { year: 'numeric', month: '2-digit', day: '2-digit', weekday: 'long' }
  currentDate.value = now.toLocaleDateString('zh-CN', options)
}

const getProgressColor = (rate) => {
  if (rate >= 80) return '#67c23a'
  if (rate >= 50) return '#e6a23c'
  return '#f56c6c'
}

const getStatusClass = (status) => {
  const statusMap = {
    '草稿': 'draft',
    '待审核': 'pending',
    '已生效': 'approved',
    '已作废': 'voided',
    '已结案': 'closed'
  }
  return statusMap[status] || 'default'
}

const loadOverview = async () => {
  try {
    const data = await getOverview()
    overviewCards[0].value = data.todayOutputQty || 0
    overviewCards[0].progress = Math.min(95, Math.round((data.todayOutputQty || 0) / 10))
    overviewCards[1].value = data.todayOutputValue || 0
    overviewCards[2].value = data.inProgressOrderCount || 0
    overviewCards[3].value = data.pendingOrderCount || 0
    overviewCards[4].value = data.totalEfficiency || 0
    overviewCards[5].value = data.onTimeDeliveryRate || 0
  } catch (e) {
    console.error('加载概览数据失败', e)
  }
}

const loadOrderStatusStats = async () => {
  try {
    const data = await getOrderStatusStats()
    renderOrderStatusChart(data)
  } catch (e) {
    console.error('加载状态统计失败', e)
  }
}

const loadProgressList = async () => {
  try {
    const data = await getProductionProgress(1, 20)
    progressList.value = data.records || []
  } catch (e) {
    console.error('加载进度列表失败', e)
  }
}

const loadWorkshopOutput = async () => {
  try {
    const data = await getWorkshopOutputStats()
    workshopList.value = data || []
    renderWorkshopChart(data)
  } catch (e) {
    console.error('加载车间产量失败', e)
  }
}

const loadEmployeeRanking = async () => {
  try {
    const now = new Date()
    const month = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
    const data = await getEmployeeRanking(month)
    employeeRanking.value = data || []
  } catch (e) {
    console.error('加载员工排行失败', e)
  }
}

const loadTodayTrend = async () => {
  try {
    const data = await getTodayOutputTrend()
    renderTodayTrendChart(data)
  } catch (e) {
    console.error('加载今日趋势失败', e)
  }
}

const renderOrderStatusChart = (data) => {
  if (!orderStatusChartInstance) {
    orderStatusChartInstance = echarts.init(orderStatusChart.value)
  }

  const colors = ['#909399', '#409eff', '#e6a23c', '#67c23a', '#f56c6c']

  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)',
      backgroundColor: 'rgba(0, 0, 0, 0.8)',
      textStyle: { color: '#fff' }
    },
    legend: {
      orient: 'vertical',
      right: '5%',
      top: 'center',
      textStyle: { color: '#c0c4cc', fontSize: 12 },
      itemWidth: 12,
      itemHeight: 12,
      gap: 12
    },
    series: [
      {
        type: 'pie',
        radius: ['45%', '70%'],
        center: ['35%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 4,
          borderColor: '#0d1117',
          borderWidth: 2
        },
        label: {
          show: false
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 14,
            fontWeight: 'bold',
            color: '#fff'
          },
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        },
        labelLine: {
          show: false
        },
        data: (data || []).map((item, index) => ({
          value: item.count,
          name: item.statusName,
          itemStyle: { color: colors[index % colors.length] }
        }))
      }
    ]
  }

  orderStatusChartInstance.setOption(option)
}

const renderTodayTrendChart = (data) => {
  if (!todayTrendChartInstance) {
    todayTrendChartInstance = echarts.init(todayTrendChart.value)
  }

  const option = {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(0, 0, 0, 0.8)',
      textStyle: { color: '#fff' },
      axisPointer: {
        type: 'line',
        lineStyle: { color: 'rgba(64, 158, 255, 0.5)' }
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '10%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: data.hours || [],
      axisLine: { lineStyle: { color: '#303133' } },
      axisLabel: { color: '#909399', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.06)' } },
      axisLabel: { color: '#909399', fontSize: 11 }
    },
    series: [
      {
        name: '产量',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: data.quantities || [],
        itemStyle: { color: '#409eff' },
        lineStyle: { width: 2, color: '#409eff' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64, 158, 255, 0.4)' },
            { offset: 1, color: 'rgba(64, 158, 255, 0.02)' }
          ])
        }
      }
    ]
  }

  todayTrendChartInstance.setOption(option)
}

const renderWorkshopChart = (data) => {
  if (!workshopChartInstance) {
    workshopChartInstance = echarts.init(workshopChart.value)
  }

  const names = (data || []).map(item => item.workshopName)
  const values = (data || []).map(item => item.outputQty || 0)

  const option = {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(0, 0, 0, 0.8)',
      textStyle: { color: '#fff' },
      axisPointer: { type: 'shadow' }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: names,
      axisLine: { lineStyle: { color: '#303133' } },
      axisLabel: { color: '#909399', fontSize: 11, rotate: names.length > 5 ? 30 : 0 }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.06)' } },
      axisLabel: { color: '#909399', fontSize: 11 }
    },
    series: [
      {
        type: 'bar',
        data: values,
        barWidth: '50%',
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#67c23a' },
            { offset: 1, color: '#85ce61' }
          ]),
          borderRadius: [4, 4, 0, 0]
        }
      }
    ]
  }

  workshopChartInstance.setOption(option)
}

const startScroll = () => {
  if (scrollInterval) clearInterval(scrollInterval)
  
  scrollInterval = setInterval(() => {
    if (tableBodyRef.value) {
      const container = tableBodyRef.value
      const scrollTop = container.scrollTop
      const scrollHeight = container.scrollHeight
      const clientHeight = container.clientHeight
      
      if (scrollTop + clientHeight >= scrollHeight - 1) {
        container.scrollTop = 0
      } else {
        container.scrollTop += 1
      }
    }
  }, 50)
}

const handleResize = () => {
  orderStatusChartInstance?.resize()
  todayTrendChartInstance?.resize()
  workshopChartInstance?.resize()
}

const loadAllData = () => {
  loadOverview()
  loadOrderStatusStats()
  loadProgressList()
  loadWorkshopOutput()
  loadEmployeeRanking()
  loadTodayTrend()
}

const init = async () => {
  updateTime()
  timeInterval = setInterval(updateTime, 1000)
  
  await nextTick()
  loadAllData()
  
  refreshInterval = setInterval(loadAllData, 30000)
  
  setTimeout(() => {
    startScroll()
  }, 2000)
}

onMounted(() => {
  init()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (timeInterval) clearInterval(timeInterval)
  if (refreshInterval) clearInterval(refreshInterval)
  if (scrollInterval) clearInterval(scrollInterval)
  orderStatusChartInstance?.dispose()
  todayTrendChartInstance?.dispose()
  workshopChartInstance?.dispose()
})
</script>

<style scoped lang="scss">
.prod-dashboard {
  width: 100%;
  min-height: 100vh;
  background: linear-gradient(135deg, #0d1117 0%, #161b22 50%, #0d1117 100%);
  padding: 16px 20px;
  box-sizing: border-box;
  color: #fff;
  font-family: 'Microsoft YaHei', -apple-system, BlinkMacSystemFont, sans-serif;

  .dashboard-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 20px;
    background: linear-gradient(90deg, rgba(64, 158, 255, 0.1), transparent, rgba(64, 158, 255, 0.1));
    border-radius: 8px;
    margin-bottom: 16px;
    border: 1px solid rgba(64, 158, 255, 0.2);

    .header-left {
      .main-title {
        font-size: 28px;
        font-weight: 700;
        margin: 0;
        background: linear-gradient(135deg, #409eff, #66b1ff);
        -webkit-background-clip: text;
        -webkit-text-fill-color: transparent;
        background-clip: text;
        display: flex;
        align-items: center;
        gap: 12px;
        letter-spacing: 2px;
      }
    }

    .header-center {
      text-align: center;

      .current-time {
        font-size: 32px;
        font-weight: 700;
        color: #409eff;
        font-family: 'Courier New', monospace;
        letter-spacing: 3px;
      }

      .current-date {
        font-size: 14px;
        color: #909399;
        margin-top: 4px;
      }
    }

    .header-right {
      display: flex;
      gap: 12px;

      :deep(.el-select__wrapper) {
        background: rgba(255, 255, 255, 0.05);
        border-color: rgba(64, 158, 255, 0.3);
        color: #fff;

        .el-select__placeholder,
        .el-select__selected-item {
          color: #c0c4cc;
        }
      }
    }
  }

  .overview-section {
    display: grid;
    grid-template-columns: repeat(6, 1fr);
    gap: 16px;
    margin-bottom: 16px;

    .overview-card {
      background: linear-gradient(135deg, rgba(255, 255, 255, 0.06), rgba(255, 255, 255, 0.02));
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 12px;
      padding: 20px;
      display: flex;
      align-items: center;
      gap: 16px;
      position: relative;
      overflow: hidden;
      transition: all 0.3s ease;

      &:hover {
        transform: translateY(-2px);
        border-color: rgba(64, 158, 255, 0.4);
        box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
      }

      .card-icon {
        width: 56px;
        height: 56px;
        border-radius: 12px;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
      }

      .card-content {
        flex: 1;
        min-width: 0;

        .card-value {
          font-size: 28px;
          font-weight: 700;
          line-height: 1.2;
          margin-bottom: 6px;

          .card-unit {
            font-size: 14px;
            font-weight: 400;
            color: #909399;
            margin-left: 4px;
          }
        }

        .card-label {
          font-size: 13px;
          color: #909399;
          margin-bottom: 8px;
        }

        .card-progress {
          display: flex;
          align-items: center;
          gap: 8px;

          :deep(.el-progress) {
            flex: 1;
          }

          .progress-text {
            font-size: 11px;
            color: #606266;
            white-space: nowrap;
          }
        }
      }
    }
  }

  .middle-section {
    display: grid;
    grid-template-columns: 1.5fr 1fr;
    gap: 16px;
    margin-bottom: 16px;

    .section-left,
    .section-right {
      .panel {
        background: linear-gradient(135deg, rgba(255, 255, 255, 0.04), rgba(255, 255, 255, 0.02));
        border: 1px solid rgba(255, 255, 255, 0.08);
        border-radius: 12px;
        height: 320px;
        display: flex;
        flex-direction: column;

        .panel-header {
          display: flex;
          align-items: center;
          justify-content: space-between;
          padding: 14px 20px;
          border-bottom: 1px solid rgba(255, 255, 255, 0.06);

          .panel-title {
            font-size: 16px;
            font-weight: 600;
            color: #fff;
            display: flex;
            align-items: center;
            gap: 8px;

            .el-icon {
              color: #409eff;
            }
          }

          .panel-subtitle {
            font-size: 12px;
            color: #67c23a;
            display: flex;
            align-items: center;
            gap: 4px;

            &::before {
              content: '';
              width: 6px;
              height: 6px;
              background: #67c23a;
              border-radius: 50%;
              animation: pulse 2s infinite;
            }
          }
        }

        .chart-container {
          flex: 1;
          width: 100%;
        }
      }
    }

    .progress-table-container {
      flex: 1;
      overflow: hidden;
      padding: 10px 20px 20px;

      .progress-table {
        width: 100%;

        .table-header {
          display: flex;
          background: rgba(64, 158, 255, 0.1);
          border-radius: 6px;
          margin-bottom: 8px;

          .th {
            padding: 10px 12px;
            font-size: 13px;
            font-weight: 600;
            color: #409eff;
          }
        }

        .table-body {
          max-height: 220px;
          overflow: hidden;
        }

        .table-row {
          display: flex;
          align-items: center;
          padding: 8px 12px;
          border-radius: 4px;
          transition: background-color 0.2s;

          &:hover,
          &.even {
            background: rgba(255, 255, 255, 0.03);
          }

          .td {
            font-size: 13px;
            color: #c0c4cc;
          }

          .order-no {
            flex: 0 0 120px;
            color: #409eff;
            font-family: 'Courier New', monospace;
          }

          .product-name {
            flex: 1;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
          }

          .plan-qty,
          .finished-qty {
            flex: 0 0 80px;
            text-align: right;
          }

          .progress {
            flex: 0 0 150px;
            display: flex;
            align-items: center;
            gap: 8px;

            .progress-bar-wrapper {
              flex: 1;
              height: 8px;
              background: rgba(255, 255, 255, 0.1);
              border-radius: 4px;
              overflow: hidden;

              .progress-bar {
                height: 100%;
                border-radius: 4px;
                transition: width 0.5s ease;
              }
            }

            .progress-text-inline {
              font-size: 12px;
              color: #909399;
              width: 40px;
              text-align: right;
            }
          }

          .status {
            flex: 0 0 80px;
            text-align: center;

            .status-tag {
              display: inline-block;
              padding: 2px 10px;
              border-radius: 10px;
              font-size: 12px;

              &.status-draft {
                background: rgba(144, 147, 153, 0.2);
                color: #909399;
              }

              &.status-pending {
                background: rgba(230, 162, 60, 0.2);
                color: #e6a23c;
              }

              &.status-approved {
                background: rgba(64, 158, 255, 0.2);
                color: #409eff;
              }

              &.status-closed {
                background: rgba(103, 194, 58, 0.2);
                color: #67c23a;
              }

              &.status-voided {
                background: rgba(245, 108, 108, 0.2);
                color: #f56c6c;
              }

              &.status-default {
                background: rgba(144, 147, 153, 0.2);
                color: #909399;
              }
            }
          }
        }
      }
    }
  }

  .bottom-section {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 16px;

    .bottom-panel {
      background: linear-gradient(135deg, rgba(255, 255, 255, 0.04), rgba(255, 255, 255, 0.02));
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 12px;
      height: 280px;
      display: flex;
      flex-direction: column;

      .panel-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 14px 20px;
        border-bottom: 1px solid rgba(255, 255, 255, 0.06);

        .panel-title {
          font-size: 16px;
          font-weight: 600;
          color: #fff;
          display: flex;
          align-items: center;
          gap: 8px;

          .el-icon {
            color: #67c23a;
          }
        }
      }

      .chart-container {
        flex: 1;
        width: 100%;
      }

      .ranking-list {
        flex: 1;
        padding: 10px 16px;
        overflow-y: auto;

        .ranking-item {
          display: flex;
          align-items: center;
          gap: 12px;
          padding: 10px 8px;
          border-radius: 6px;
          transition: background-color 0.2s;

          &:hover {
            background: rgba(255, 255, 255, 0.04);
          }

          .rank-badge {
            width: 28px;
            height: 28px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 13px;
            font-weight: 700;
            color: #fff;
            background: rgba(255, 255, 255, 0.1);
            flex-shrink: 0;

            &.rank-1 {
              background: linear-gradient(135deg, #ffd700, #ffb800);
              box-shadow: 0 0 12px rgba(255, 215, 0, 0.4);
            }

            &.rank-2 {
              background: linear-gradient(135deg, #c0c4cc, #909399);
              box-shadow: 0 0 10px rgba(192, 196, 204, 0.3);
            }

            &.rank-3 {
              background: linear-gradient(135deg, #e6a23c, #d4923a);
              box-shadow: 0 0 10px rgba(230, 162, 60, 0.3);
            }
          }

          .rank-info {
            flex: 1;
            min-width: 0;

            .rank-name {
              font-size: 14px;
              color: #fff;
              font-weight: 500;
              margin-bottom: 2px;
              overflow: hidden;
              text-overflow: ellipsis;
              white-space: nowrap;
            }

            .rank-hours {
              font-size: 12px;
              color: #909399;
            }
          }

          .rank-data {
            text-align: right;
            flex-shrink: 0;

            .rank-qty {
              font-size: 15px;
              font-weight: 600;
              color: #67c23a;
              margin-bottom: 2px;
            }

            .rank-eff {
              font-size: 12px;
              color: #909399;
            }
          }
        }

        .empty-ranking {
          height: 100%;
          display: flex;
          align-items: center;
          justify-content: center;

          :deep(.el-empty__description) {
            color: #606266;
          }
        }
      }
    }
  }
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}

@media (max-width: 1600px) {
  .prod-dashboard {
    .overview-section {
      grid-template-columns: repeat(3, 1fr);
    }
  }
}

@media (max-width: 1200px) {
  .prod-dashboard {
    .middle-section {
      grid-template-columns: 1fr;
    }

    .bottom-section {
      grid-template-columns: 1fr;
    }
  }
}
</style>
