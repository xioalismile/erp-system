<template>
  <div class="custom-dashboard-view">
    <!-- 顶部标题栏 -->
    <div class="view-header">
      <div class="header-left">
        <el-button size="small" @click="goBack"><el-icon><Back /></el-icon> {{ $tt("返回") }}</el-button>
        <span class="title-text">{{ dashName }}</span>
      </div>
      <div class="header-right">
        <el-button size="small" :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>
    </div>

    <!-- 卡片网格 -->
    <div class="cards-grid" v-loading="loading">
      <el-empty v-if="!loading && !cards.length" :description="$tt('该仪表盘暂无卡片')" />
      <div v-for="(c, idx) in cards" :key="c.reportId + '-' + idx" class="dash-card">
        <div class="card-header">
          <span class="card-title">{{ c.title || c.reportName }}</span>
          <span class="card-meta">
            <el-tag size="small" :type="c.mode === 'summary' ? 'warning' : 'info'">
              {{ c.mode === 'summary' ? '汇总' : '明细' }}
            </el-tag>
            <el-tag v-if="c.error" size="small" type="danger">{{ $tt("不可用") }}</el-tag>
            <el-link v-if="!c.error" type="primary" :underline="false" @click="goReport(c)">
              查看报表<el-icon><Right /></el-icon>
            </el-link>
          </span>
        </div>

        <!-- 异常卡片：单独提示，不影响其它卡片 -->
        <div v-if="c.error" class="card-error">
          <el-empty :description="c.error" :image-size="60" />
        </div>

        <!-- 汇总模式 + 图表 -->
        <div v-else-if="c.mode === 'summary' && c.chartType" class="card-chart" :ref="el => setChartEl(el, idx)"></div>

        <!-- 迷你表格（明细 / 无图表汇总） -->
        <div v-else class="card-table">
          <el-table :data="miniRows(c)" border size="small" max-height="260">
            <el-table-column
              v-for="col in miniColumns(c)"
              :key="col.field"
              :prop="col.field"
              :label="$tf(col.label)"
              :min-width="miniColWidth(col)"
              :align="isNumeric(col) ? 'right' : 'left'"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                <span v-if="col.type === 'amount'" class="cell-amount">¥{{ formatNumber(row[col.field]) }}</span>
                <span v-else-if="isNumeric(col)" class="cell-number">{{ formatNumber(row[col.field]) }}</span>
                <span v-else>{{ row[col.field] ?? '' }}</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="card-footer" v-if="c.total != null">共 {{ c.total }} 行</div>
        </div>

        <!-- 汇总卡片合计信息（图表下方/无图表时表格底部） -->
        <div class="card-totals" v-if="!c.error && c.mode === 'summary' && totalsText(c)">
          {{ totalsText(c) }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Back, Refresh, Right } from '@element-plus/icons-vue'
import echarts from '@/utils/echartsCore'
import { getDashboardData } from '@/api/report/customDashboard'

const router = useRouter()
const route = useRoute()
const dashId = computed(() => Number(route.params.id))

const loading = ref(false)
const dashName = ref('')
const cards = ref([])

const NUMERIC_TYPES = ['amount', 'number', 'int']
const isNumeric = col => NUMERIC_TYPES.includes(col.type)

// ========== 图表 ==========
const chartEls = new Map()     // idx -> DOM 元素
const chartInstances = new Map() // idx -> echarts 实例

function setChartEl(el, idx) {
  if (el) chartEls.set(idx, el)
  else chartEls.delete(idx)
}

function renderCharts() {
  cards.value.forEach((c, idx) => {
    if (c.error || c.mode !== 'summary' || !c.chartType) return
    const el = chartEls.get(idx)
    if (!el) return
    const cols = c.columns || []
    const records = c.records || []
    const numericCols = cols.filter(isNumeric)
    const dimCol = cols.find(col => !isNumeric(col))
    if (!numericCols.length || !dimCol) return

    let inst = chartInstances.get(idx)
    if (inst) inst.dispose()
    inst = echarts.init(el)
    chartInstances.set(idx, inst)

    const dimVals = records.map(r => r[dimCol.field] ?? '')
    if (c.chartType === 'pie') {
      const agg = numericCols[0]
      inst.setOption({
        tooltip: { trigger: 'item', formatter: '{b}<br/>{c} ({d}%)' },
        legend: { type: 'scroll', orient: 'vertical', left: 'left', top: 'middle' },
        series: [{
          type: 'pie',
          radius: ['35%', '62%'],
          center: ['55%', '55%'],
          data: records.map(r => ({ name: r[dimCol.field] ?? '', value: Number(r[agg.field]) || 0 }))
        }]
      }, true)
      return
    }
    inst.setOption({
      tooltip: { trigger: 'axis' },
      legend: { top: 4, data: numericCols.map(col => col.label), type: 'scroll' },
      grid: { left: 56, right: 20, top: 40, bottom: 46 },
      xAxis: { type: 'category', data: dimVals, axisLabel: { rotate: 30, interval: 0, hideOverlap: true } },
      yAxis: { type: 'value' },
      series: numericCols.map(col => ({
        name: col.label,
        type: c.chartType === 'line' ? 'line' : 'bar',
        smooth: c.chartType === 'line',
        data: records.map(r => Number(r[col.field]) || 0)
      }))
    }, true)
  })
}

function handleResize() {
  chartInstances.forEach(inst => inst.resize())
}

// ========== 迷你表格 ==========
/** 迷你表格最多展示 6 列（数值列优先保留），避免卡片横向溢出 */
function miniColumns(c) {
  const cols = c.columns || []
  if (cols.length <= 6) return cols
  const head = cols.slice(0, 2)
  const rest = cols.slice(2)
  const numeric = rest.filter(isNumeric)
  const other = rest.filter(col => !isNumeric(col))
  return [...head, ...numeric, ...other].slice(0, 6)
}

function miniRows(c) {
  const rows = (c.records || []).slice(0, 10)
  // 汇总模式追加整体合计行（与运行页口径一致）
  if (c.mode === 'summary' && c.totals) return [...rows, c.totals]
  return rows
}

function miniColWidth(col) {
  return isNumeric(col) ? 110 : 140
}

/** 汇总卡片合计摘要（最多 3 个数值指标） */
function totalsText(c) {
  if (!c.totals) return ''
  const numericCols = (c.columns || []).filter(isNumeric).slice(0, 3)
  if (!numericCols.length) return ''
  return '合计：' + numericCols.map(col => `${col.label} ${formatNumber(c.totals[col.field]) || 0}`).join('　')
}

// ========== 数据加载 ==========
async function loadData() {
  loading.value = true
  try {
    const res = await getDashboardData(dashId.value)
    dashName.value = res?.dashName || ''
    cards.value = res?.cards || []
    await nextTick()
    renderCharts()
  } catch (e) {
    cards.value = []
    if (e?.message) ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function goReport(c) {
  router.push(`/report/custom/run/${c.reportId}`)
}

function goBack() {
  router.push('/report/dashboard')
}

function formatNumber(v) {
  if (v === null || v === undefined || v === '') return ''
  const n = Number(v)
  if (isNaN(n)) return v
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

onMounted(() => {
  loadData()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chartInstances.forEach(inst => inst.dispose())
  chartInstances.clear()
  chartEls.clear()
})
</script>

<style scoped lang="scss">
.custom-dashboard-view {
  background: #f5f7fa;
  min-height: calc(100vh - 90px);
  display: flex;
  flex-direction: column;
}
.view-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  padding: 10px 16px;
  .header-left {
    display: flex;
    align-items: center;
    gap: 10px;
    .title-text { font-size: 16px; font-weight: 600; color: #303133; }
  }
}
.cards-grid {
  flex: 1;
  padding: 16px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  align-content: start;
}
.dash-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  min-height: 300px;
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 10px;
    .card-title { font-size: 14px; font-weight: 600; color: #303133; }
    .card-meta { display: flex; align-items: center; gap: 8px; }
  }
  .card-chart { flex: 1; height: 320px; }
  .card-table { flex: 1; display: flex; flex-direction: column; }
  .card-footer { font-size: 12px; color: #909399; margin-top: 6px; }
  .card-totals {
    margin-top: 8px;
    padding: 6px 10px;
    background: #fafafa;
    border-radius: 4px;
    font-size: 12px;
    color: #606266;
  }
  .card-error { flex: 1; display: flex; align-items: center; justify-content: center; }
}
.cell-amount { color: #f56c6c; font-weight: 500; }
.cell-number { color: #409eff; font-weight: 500; }
@media (max-width: 1200px) {
  .cards-grid { grid-template-columns: 1fr; }
}
</style>
