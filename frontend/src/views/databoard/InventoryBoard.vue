<template>
  <div class="board-page">
    <div class="board-header">
      <div class="board-title">
        <h2>{{ $tt("仓储数据看板") }}</h2>
        <span class="board-sub">口径：现存量(inventory_current) × 成本 / 库存台账(inventory_ledger)出库·入库 / 物料安全库存（真实单据汇总）。周转率分母=平均库存金额(期初+期末)/2；可销天数=当前库存数量÷日均销量</span>
      </div>
      <div class="board-actions">
        <el-radio-group v-model="period" size="default" @change="loadData">
          <el-radio-button value="day">{{ $tt("今日") }}</el-radio-button>
          <el-radio-button value="week">{{ $tt("本周") }}</el-radio-button>
          <el-radio-button value="month">{{ $tt("本月") }}</el-radio-button>
          <el-radio-button value="quarter">{{ $tt("本季") }}</el-radio-button>
          <el-radio-button value="year">{{ $tt("本年") }}</el-radio-button>
        </el-radio-group>
        <span class="auto-refresh">
          <el-switch v-model="autoRefresh" size="small" @change="toggleAutoRefresh" />
          <span class="auto-refresh-text">{{ $tt("自动刷新(30秒)") }}</span>
        </span>
        <el-button :icon="Refresh" circle v-permission="perm('refresh')" @click="loadData" :title="$tt('刷新')" />
        <el-button :icon="Setting" @click="settingVisible = true">{{ $tt("看板设置") }}</el-button>
      </div>
    </div>

    <!-- 核心层 -->
    <el-row :gutter="12" class="kpi-row">
      <el-col v-if="L.show('kpiStockAmount')" :xs="L.xsOf('kpiStockAmount', 6)" :sm="L.sizeOf('kpiStockAmount', 6)" :lg="L.sizeOf('kpiStockAmount', 6)">
        <BoardCard v-if="L.isOverride('kpiStockAmount')" :card="L.cardCfg('kpiStockAmount')" :data="L.dataOf('kpiStockAmount')" :error="L.errOf('kpiStockAmount')" />
        <div v-else class="kpi-card clickable" @click="go('/inventory/current')">
          <div class="kpi-label">{{ $tt("库存总金额") }} <span class="drill-arrow">›</span></div>
          <div class="kpi-value">{{ fmt(d.stockTotalAmount) }}</div>
          <div class="kpi-foot">总数量 {{ fmt(d.stockTotalQuantity) }}</div>
        </div>
      </el-col>
      <el-col v-if="L.show('kpiTurnover')" :xs="L.xsOf('kpiTurnover', 6)" :sm="L.sizeOf('kpiTurnover', 6)" :lg="L.sizeOf('kpiTurnover', 6)">
        <BoardCard v-if="L.isOverride('kpiTurnover')" :card="L.cardCfg('kpiTurnover')" :data="L.dataOf('kpiTurnover')" :error="L.errOf('kpiTurnover')" />
        <div v-else class="kpi-card clickable" @click="goLedger()">
          <div class="kpi-label">{{ $tt("库存周转率（年化）") }} <span class="drill-arrow">›</span></div>
          <div class="kpi-value">{{ fmt(d.turnoverRate) }}</div>
          <div class="kpi-foot">平均库存 {{ fmt(d.avgStockAmount) }} / {{ d.periodLabel }}出库 {{ fmt(d.periodOutAmount) }}</div>
        </div>
      </el-col>
      <el-col v-if="L.show('kpiTurnoverDays')" :xs="L.xsOf('kpiTurnoverDays', 6)" :sm="L.sizeOf('kpiTurnoverDays', 6)" :lg="L.sizeOf('kpiTurnoverDays', 6)">
        <BoardCard v-if="L.isOverride('kpiTurnoverDays')" :card="L.cardCfg('kpiTurnoverDays')" :data="L.dataOf('kpiTurnoverDays')" :error="L.errOf('kpiTurnoverDays')" />
        <div v-else class="kpi-card clickable" @click="goLedger()">
          <div class="kpi-label">{{ $tt("库存周转天数") }} <span class="drill-arrow">›</span></div>
          <div class="kpi-value">{{ fmt(d.turnoverDays) }} 天</div>
          <div class="kpi-foot">{{ $tt("365 / 年化周转率") }}</div>
        </div>
      </el-col>
      <el-col v-if="L.show('kpiSaleableDays')" :xs="L.xsOf('kpiSaleableDays', 6)" :sm="L.sizeOf('kpiSaleableDays', 6)" :lg="L.sizeOf('kpiSaleableDays', 6)">
        <BoardCard v-if="L.isOverride('kpiSaleableDays')" :card="L.cardCfg('kpiSaleableDays')" :data="L.dataOf('kpiSaleableDays')" :error="L.errOf('kpiSaleableDays')" />
        <div v-else class="kpi-card clickable" @click="goLedger()">
          <div class="kpi-label">{{ $tt("平均可销天数") }} <span class="drill-arrow">›</span></div>
          <div class="kpi-value">{{ fmt(d.avgSaleableDays) }} 天</div>
          <div class="kpi-foot">当前库存 ÷ 日均销量（{{ d.periodLabel }}销量 {{ fmt(d.periodSaleQty) }}）</div>
        </div>
      </el-col>
    </el-row>

    <!-- 预警层 -->
    <el-row :gutter="12" class="kpi-row">
      <el-col v-if="L.show('kpiLongAge')" :xs="L.xsOf('kpiLongAge', 6)" :sm="L.sizeOf('kpiLongAge', 6)" :lg="L.sizeOf('kpiLongAge', 6)">
        <BoardCard v-if="L.isOverride('kpiLongAge')" :card="L.cardCfg('kpiLongAge')" :data="L.dataOf('kpiLongAge')" :error="L.errOf('kpiLongAge')" />
        <div v-else class="kpi-card clickable" :class="{ warn: nvl(d.longAgeAmount) > 0 }" @click="go('/inventory/current', { ageOver: '90' })">
          <div class="kpi-label">{{ $tt("90天以上长库龄金额") }} <span class="drill-arrow">›</span></div>
          <div class="kpi-value" :class="{ 'warn-text': nvl(d.longAgeAmount) > 0 }">{{ fmt(d.longAgeAmount) }}</div>
          <div class="kpi-foot">涉及物料 {{ fmt(d.longAgeCount) }} 种</div>
        </div>
      </el-col>
      <el-col v-if="L.show('kpiStale')" :xs="L.xsOf('kpiStale', 6)" :sm="L.sizeOf('kpiStale', 6)" :lg="L.sizeOf('kpiStale', 6)">
        <BoardCard v-if="L.isOverride('kpiStale')" :card="L.cardCfg('kpiStale')" :data="L.dataOf('kpiStale')" :error="L.errOf('kpiStale')" />
        <div v-else class="kpi-card clickable" :class="{ warn: nvl(d.staleAmount) > 0 }" @click="go('/inventory/current', { staleOnly: '1' })">
          <div class="kpi-label">{{ $tt("呆滞物料金额（180天+未流动）") }} <span class="drill-arrow">›</span></div>
          <div class="kpi-value" :class="{ 'warn-text': nvl(d.staleAmount) > 0 }">{{ fmt(d.staleAmount) }}</div>
          <div class="kpi-foot">涉及物料 {{ fmt(d.staleCount) }} 种</div>
        </div>
      </el-col>
      <el-col v-if="L.show('kpiShortage')" :xs="L.xsOf('kpiShortage', 6)" :sm="L.sizeOf('kpiShortage', 6)" :lg="L.sizeOf('kpiShortage', 6)">
        <BoardCard v-if="L.isOverride('kpiShortage')" :card="L.cardCfg('kpiShortage')" :data="L.dataOf('kpiShortage')" :error="L.errOf('kpiShortage')" />
        <div v-else class="kpi-card clickable" :class="{ warn: nvl(d.shortageSkuCount) > 0 }" @click="go('/inventory/current', { belowMinStock: '1' })">
          <div class="kpi-label">{{ $tt("缺货SKU数量") }} <span class="drill-arrow">›</span></div>
          <div class="kpi-value" :class="{ 'warn-text': nvl(d.shortageSkuCount) > 0 }">{{ fmt(d.shortageSkuCount) }}</div>
          <div class="kpi-foot">{{ $tt("库存低于安全库存") }}</div>
        </div>
      </el-col>
      <el-col v-if="L.show('kpiRawVsFinished')" :xs="L.xsOf('kpiRawVsFinished', 6)" :sm="L.sizeOf('kpiRawVsFinished', 6)" :lg="L.sizeOf('kpiRawVsFinished', 6)">
        <BoardCard v-if="L.isOverride('kpiRawVsFinished')" :card="L.cardCfg('kpiRawVsFinished')" :data="L.dataOf('kpiRawVsFinished')" :error="L.errOf('kpiRawVsFinished')" />
        <div v-else class="kpi-card clickable" @click="go('/inventory/current')">
          <div class="kpi-label">{{ $tt("原材料 vs 成品占比") }} <span class="drill-arrow">›</span></div>
          <div class="kpi-value sm">{{ rawVsText }}</div>
          <div class="kpi-foot">{{ $tt("按库存金额拆分") }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 图表区 -->
    <el-row :gutter="12">
      <el-col v-if="L.show('chartTrend')" :xs="L.xsOf('chartTrend', 12)" :lg="L.sizeOf('chartTrend', 12)">
        <BoardCard v-if="L.isOverride('chartTrend')" :card="L.cardCfg('chartTrend')" :data="L.dataOf('chartTrend')" :error="L.errOf('chartTrend')" />
        <el-card v-else shadow="never" class="chart-card">
          <template #header>{{ $tt("库存金额 vs 销售趋势（近6月，点看库存流水）") }}</template>
          <div ref="trendRef" class="chart" />
        </el-card>
      </el-col>
      <el-col v-if="L.show('chartType')" :xs="L.xsOf('chartType', 12)" :lg="L.sizeOf('chartType', 12)">
        <BoardCard v-if="L.isOverride('chartType')" :card="L.cardCfg('chartType')" :data="L.dataOf('chartType')" :error="L.errOf('chartType')" />
        <el-card v-else shadow="never" class="chart-card">
          <template #header>{{ $tt("各品类库存结构（物料类型，点扇区看明细）") }}</template>
          <div ref="typeRef" class="chart" />
        </el-card>
      </el-col>
      <el-col v-if="L.show('chartLine')" :xs="L.xsOf('chartLine', 12)" :lg="L.sizeOf('chartLine', 12)">
        <BoardCard v-if="L.isOverride('chartLine')" :card="L.cardCfg('chartLine')" :data="L.dataOf('chartLine')" :error="L.errOf('chartLine')" />
        <el-card v-else shadow="never" class="chart-card">
          <template #header>{{ $tt("各产品线库存结构（点扇区看明细）") }}</template>
          <div ref="lineRef" class="chart" />
        </el-card>
      </el-col>
      <el-col v-if="L.show('tableShortage')" :xs="L.xsOf('tableShortage', 12)" :lg="L.sizeOf('tableShortage', 12)">
        <BoardCard v-if="L.isOverride('tableShortage')" :card="L.cardCfg('tableShortage')" :data="L.dataOf('tableShortage')" :error="L.errOf('tableShortage')" />
        <el-card v-else shadow="never" class="chart-card">
          <template #header>
            <div style="display:flex;align-items:center;justify-content:space-between;">
              <span>
                缺货预警（低于安全库存 TOP10）
                <el-tag v-if="nvl(d.shortageSkuCount) > 0" type="danger" size="small" style="margin-left:8px">{{ d.shortageSkuCount }} 种缺货</el-tag>
              </span>
              <el-tooltip :content="$tt('列设置')" placement="top">
                <el-button :icon="Setting" circle size="small" @click="openColumnSetting" />
              </el-tooltip>
            </div>
          </template>
          <el-table ref="tableRef" :data="d.shortageList || []" size="small" border max-height="320" :empty-text="$tt('暂无缺货物料')" :key="configVersion" @header-dragend="onHeaderDragend">
            <!-- 动态列渲染：列顺序由列设置拖拽配置驱动，单元格按 col.type 分支渲染 -->
            <template v-for="col in visibleSortedColumns" :key="col.prop">
              <el-table-column
                :prop="col.prop"
                :label="$tf(col.label)"
                :width="col.width"
                :min-width="col.minWidth"
                :align="col.align || 'left'"
                :show-overflow-tooltip="!!col.tooltip"
                :fixed="getColFixed(col.prop)"
              >
                <template v-if="col.type" #default="{ row }">
                  <template v-if="col.type === 'number'">{{ fmt(row[col.prop]) }}</template>
                  <span v-else-if="col.type === 'gap'" class="warn-text">{{ fmt(row.gap) }}</span>
                </template>
              </el-table-column>
            </template>
            <!-- 操作列保持静态（位于表格末尾，不入列设置） -->
            <el-table-column :label="$tf('操作')" width="90" align="center">
              <template #default="{ row }">
                <el-button link type="primary" size="small" v-permission="perm('view')" @click="goMaterial(row)">{{ $tt("查看") }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 本页新增的自定义卡片 -->
    <el-row v-if="L.customItems.value.length" :gutter="12" class="kpi-row">
      <el-col
        v-for="it in L.customItems.value"
        :key="it.id"
        :xs="L.xsOf(it.id, it.size)"
        :sm="L.sizeOf(it.id, it.size)"
        :lg="L.sizeOf(it.id, it.size)"
      >
        <BoardCard :card="it.card" :data="L.dataOf(it.id)" :error="L.errOf(it.id)" />
      </el-col>
    </el-row>

    <!-- 列设置抽屉 -->
    <ColumnSettingDrawer v-model="columnSettingVisible" :page-key="'InventoryBoard'" :columns="columns" />

    <!-- 看板设置：显隐 / 宽度 / 顺序 / 换数据源与公式 / 新增卡片 -->
    <BoardLayoutSetting v-model="settingVisible" :layout="L" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh, Setting } from '@element-plus/icons-vue'
import echarts from '@/utils/echartsCore'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { getInventoryBoard } from '@/api/dashboard/dataBoard'
import { usePagePerm } from '@/utils/pagePerm'
import BoardCard from '@/components/board/BoardCard.vue'
import BoardLayoutSetting from '@/components/board/BoardLayoutSetting.vue'
import { useBoardLayout } from '@/composables/useBoardLayout'

const perm = usePagePerm('databoard:inventory')

/** 本页原有区块清单：看板设置里可逐块显隐 / 调宽 / 换自己的数据源与公式 */
const BUILTINS = [
  { id: 'kpiStockAmount', title: '库存总金额', size: 6 },
  { id: 'kpiTurnover', title: '库存周转率', size: 6 },
  { id: 'kpiTurnoverDays', title: '库存周转天数', size: 6 },
  { id: 'kpiSaleableDays', title: '平均可销天数', size: 6 },
  { id: 'kpiLongAge', title: '长库龄金额', size: 6 },
  { id: 'kpiStale', title: '呆滞物料金额', size: 6 },
  { id: 'kpiShortage', title: '缺货SKU数量', size: 6 },
  { id: 'kpiRawVsFinished', title: '原材料 vs 成品占比', size: 6 },
  { id: 'chartTrend', title: '库存金额 vs 销售趋势', size: 12 },
  { id: 'chartType', title: '各品类库存结构', size: 12 },
  { id: 'chartLine', title: '各产品线库存结构', size: 12 },
  { id: 'tableShortage', title: '缺货预警', size: 12 }
]
const L = useBoardLayout('inventory', BUILTINS)
const settingVisible = ref(false)

const router = useRouter()
const period = ref('month')
const autoRefresh = ref(true)
const d = ref({})
// 列定义（列设置 + 动态渲染用）：type 标记单元格渲染分支；操作列保持静态（位于表格末尾，不入列设置）
const columns = [
  { prop: 'materialCode', label: '编码', width: 110, tooltip: true },
  { prop: 'materialName', label: '物料', minWidth: 140, tooltip: true },
  { prop: 'stockQuantity', label: '现存量', width: 90, align: 'right', type: 'number' },
  { prop: 'minStock', label: '安全库存', width: 90, align: 'right', type: 'number' },
  { prop: 'gap', label: '缺口', width: 90, align: 'right', type: 'gap' }
]
const { columnSettingVisible, openColumnSetting, isColVisible, getColFixed, configVersion, sortedColumns, onHeaderDragend, applySavedWidths } = useColumnSetting('InventoryBoard', columns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))
// 列宽持久化：表格带 border 才允许拖拽，拖完的宽度写入本页列配置，下次打开自动套用
const tableRef = ref(null)
const trendRef = ref()
const typeRef = ref()
const lineRef = ref()
const charts = []
let timer = null

const nvl = v => (v == null ? 0 : Number(v))
const fmt = v => (v == null || v === '' ? '' : Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 2 }))
const go = (p, query) => router.push(query ? { path: p, query } : p)

const rawVsText = computed(() => {
  const list = d.value.rawVsProduct || []
  if (!list.length) return ''
  const raw = nvl((list.find(x => x.name.includes('原材料')) || {}).value)
  const total = list.reduce((s, x) => s + nvl(x.value), 0)
  if (total <= 0) return ''
  return (raw * 100 / total).toFixed(1) + '% : ' + (100 - raw * 100 / total).toFixed(1) + '%'
})

/** 当前时间维度的起止日期（YYYY-MM-DD，含端点） */
function periodRange() {
  const now = new Date()
  const p2 = n => String(n).padStart(2, '0')
  const f = dt => `${dt.getFullYear()}-${p2(dt.getMonth() + 1)}-${p2(dt.getDate())}`
  let start = new Date(now), end = new Date(now)
  if (period.value === 'week') {
    const dow = (now.getDay() + 6) % 7
    start = new Date(now); start.setDate(now.getDate() - dow)
    end = new Date(start); end.setDate(start.getDate() + 6)
  } else if (period.value === 'month') {
    start = new Date(now.getFullYear(), now.getMonth(), 1)
    end = new Date(now.getFullYear(), now.getMonth() + 1, 0)
  } else if (period.value === 'quarter') {
    const qs = Math.floor(now.getMonth() / 3) * 3
    start = new Date(now.getFullYear(), qs, 1)
    end = new Date(now.getFullYear(), qs + 3, 0)
  } else if (period.value === 'year') {
    start = new Date(now.getFullYear(), 0, 1)
    end = new Date(now.getFullYear(), 11, 31)
  }
  return { startDate: f(start), endDate: f(end) }
}
/** 穿透到库存流水（带当前时间范围） */
function goLedger() {
  go('/inventory/ledger', periodRange())
}
function goMaterial(row) {
  go('/inventory/current', row && row.materialCode ? { materialCode: row.materialCode } : {})
}

async function loadData() {
  // 注意：request 响应拦截器已把 {code,message,data} 解包为 data，这里直接就是业务数据
  const res = await getInventoryBoard(period.value)
  d.value = res || {}
  await nextTick()
  renderCharts()
  // 数据到位后再套一次已保存的列宽（表格重建后列对象是新的）
  nextTick(() => applySavedWidths(tableRef))
  // 本页被改成自定义取数的区块与新增卡片，跟着时间维度一起刷新
  L.refreshData(period.value)
}

function renderCharts() {
  disposeCharts()
  const data = d.value
  if (trendRef.value) {
    const c = echarts.init(trendRef.value)
    const t = data.stockVsSaleTrend || []
    c.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['库存出库金额', '销售金额'] },
      grid: { left: 60, right: 20, top: 40, bottom: 30 },
      xAxis: { type: 'category', data: t.map(x => x.month) },
      yAxis: { type: 'value' },
      series: [
        { name: '库存出库金额', type: 'bar', data: t.map(x => nvl(x.stockOutAmount)) },
        { name: '销售金额', type: 'line', smooth: true, data: t.map(x => nvl(x.saleAmount)) }
      ]
    })
    c.on('click', p => {
      const row = t[p.dataIndex]
      if (!row || !row.month) return
      const [y, m] = String(row.month).split('-')
      const last = new Date(Number(y), Number(m), 0).getDate()
      go('/inventory/ledger', { startDate: `${row.month}-01`, endDate: `${row.month}-${String(last).padStart(2, '0')}` })
    })
    charts.push(c)
  }
  const pie = (refEl, list, onPick) => {
    if (!refEl.value) return
    const c = echarts.init(refEl.value)
    c.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      series: [{
        type: 'pie', radius: ['35%', '65%'],
        label: { formatter: '{b}\n{d}%' },
        data: (list || []).map(x => ({ name: x.name, value: nvl(x.value) }))
      }]
    })
    c.on('click', p => onPick && onPick(p.name))
    charts.push(c)
  }
  pie(typeRef, data.typeDist, name => go('/inventory/current', { materialType: name }))
  pie(lineRef, data.lineDist, name => {
    const ref = (data.productLineRef || []).find(x => x.name === name)
    go('/inventory/current', ref && ref.categoryId ? { lineId: String(ref.categoryId) } : {})
  })
}

function disposeCharts() {
  charts.forEach(c => c.dispose())
  charts.length = 0
}

function toggleAutoRefresh(v) {
  if (timer) { clearInterval(timer); timer = null }
  if (v) timer = setInterval(loadData, 30000)
}

const onResize = () => charts.forEach(c => c.resize())

onMounted(async () => {
  await L.load(period.value)
  await loadData()
  window.addEventListener('resize', onResize)
  if (autoRefresh.value) timer = setInterval(loadData, 30000)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  if (timer) { clearInterval(timer); timer = null }
  disposeCharts()
})
</script>

<style scoped>
.board-page { padding: 12px; }
.board-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; margin-bottom: 12px; }
.board-title h2 { margin: 0; font-size: 18px; }
.board-sub { font-size: 12px; color: #909399; }
.board-actions { display: flex; align-items: center; gap: 12px; }
.auto-refresh { display: inline-flex; align-items: center; gap: 6px; }
.auto-refresh-text { font-size: 12px; color: #909399; }
.kpi-row { margin-bottom: 12px; }
.kpi-card { background: #fff; border: 1px solid #ebeef5; border-radius: 8px; padding: 14px 16px; min-height: 88px; transition: box-shadow .2s; }
.kpi-card.clickable { cursor: pointer; }
.kpi-card:hover { box-shadow: 0 2px 12px rgba(0,0,0,.08); }
.kpi-label { font-size: 13px; color: #909399; margin-bottom: 6px; }
.drill-arrow { color: #c0c4cc; font-size: 13px; }
.kpi-value { font-size: 22px; font-weight: 600; color: #303133; }
.kpi-value.sm { font-size: 18px; }
.kpi-foot { font-size: 12px; color: #b0b3b8; margin-top: 6px; }
.kpi-card.warn { border-color: #fde2e2; background: #fef0f0; }
.warn-text { color: #F56C6C !important; }
.chart-card { margin-bottom: 12px; }
.chart { width: 100%; height: 320px; }
</style>
