<template>
  <div class="erp-page">
    <div class="ob-head">
      <div class="ob-title">
        <el-icon style="margin-right:8px;color:#409EFF;"><TrendCharts /></el-icon>
        {{ $tt("经营看板") }}
      </div>
      <div class="ob-sub">{{ $tt("总经理视角 · 一屏看全公司：销售 / 利润 / 回款 / 生产 / 采购 / 库存 / 质量 / 人力") }}</div>
      <div style="flex:1"></div>
      <el-radio-group v-model="period" @change="load">
        <el-radio-button label="day">{{ $tt("今日") }}</el-radio-button>
        <el-radio-button label="week">{{ $tt("本周") }}</el-radio-button>
        <el-radio-button label="month">{{ $tt("本月") }}</el-radio-button>
        <el-radio-button label="quarter">{{ $tt("本季") }}</el-radio-button>
        <el-radio-button label="year">{{ $tt("本年") }}</el-radio-button>
      </el-radio-group>
      <el-button @click="load">{{ $tt("刷新") }}</el-button>
      <el-button @click="settingVisible = true">{{ $tt("看板设置") }}</el-button>
    </div>

    <div class="ob-grid">
      <template v-for="blk in blocks" :key="blk.key">
        <div
          v-if="L.show(blk.key)"
          class="ob-card"
          :class="{ clickable: !!blk.route }"
          :style="{ gridColumn: `span ${gridSpan(blk.key)}` }"
          @click="go(blk.route, blk.query)"
        >
          <BoardCard v-if="L.isOverride(blk.key)" :card="L.cardCfg(blk.key)" :data="L.dataOf(blk.key)" :error="L.errOf(blk.key)" />
          <template v-else>
            <div class="ob-card-head">
              <span class="ob-card-title">{{ blk.title }}</span>
              <el-tag v-if="blk.tag" size="small" :type="blk.tagType || 'info'" effect="plain">{{ blk.tag }}</el-tag>
              <span v-if="blk.route" class="drill-arrow">›</span>
            </div>
            <div v-for="row in blk.rows" :key="row.label" class="ob-row">
              <span class="ob-row-label">{{ row.label }}</span>
              <span class="ob-row-val">{{ row.value }}</span>
            </div>
            <div v-if="!blk.rows.length" class="ob-empty">{{ $tt("暂无数据") }}</div>
            <div v-if="blk.trend" class="ob-trend">
              <div class="ob-trend-head">
                <span>{{ blk.trend.label }}</span>
                <span class="ob-trend-range">{{ blk.trend.from }} ~ {{ blk.trend.to }}</span>
              </div>
              <svg class="ob-spark" :viewBox="`0 0 ${SPARK_W} ${SPARK_H}`" preserveAspectRatio="none" aria-hidden="true">
                <polyline :points="blk.trend.polyline" />
              </svg>
            </div>
          </template>
        </div>
      </template>

      <!-- 本页新增的自定义卡片 -->
      <div
        v-for="it in L.customItems.value"
        :key="it.id"
        class="ob-card"
        :style="{ gridColumn: `span ${gridSpan(it.id, it.size)}` }"
      >
        <BoardCard :card="it.card" :data="L.dataOf(it.id)" :error="L.errOf(it.id)" />
      </div>
    </div>

    <div class="ob-note">
      {{ $tt("口径说明：销售/利润/回款/库存四块复用「数据看板」既有口径，生产/采购/质量/人力四块为经营看板新增；人力块只展示在职人数与部门分布（系统无招聘、培训模块，不展示这两项）") }}
    </div>

    <!-- 看板设置：显隐 / 宽度 / 顺序 / 换数据源与公式 / 新增卡片 -->
    <BoardLayoutSetting v-model="settingVisible" :layout="L" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { TrendCharts } from '@element-plus/icons-vue'
import { getSaleBoard, getFinanceBoard, getInventoryBoard } from '@/api/dashboard/dataBoard'
import { getOperationBoard } from '@/api/dashboard/operationBoard'
import BoardCard from '@/components/board/BoardCard.vue'
import BoardLayoutSetting from '@/components/board/BoardLayoutSetting.vue'
import { useBoardLayout } from '@/composables/useBoardLayout'

const router = useRouter()

/** 本页原有区块清单：看板设置里可逐块显隐 / 调宽 / 换自己的数据源与公式 */
const BUILTINS = [
  { id: 'sale', title: '销售', size: 3 },
  { id: 'profit', title: '利润', size: 3 },
  { id: 'receipt', title: '回款', size: 3 },
  { id: 'production', title: '生产', size: 3 },
  { id: 'purchase', title: '采购', size: 3 },
  { id: 'inventory', title: '库存', size: 3 },
  { id: 'quality', title: '质量', size: 3 },
  { id: 'hr', title: '人力', size: 3 }
]
const L = useBoardLayout('operation', BUILTINS)
const settingVisible = ref(false)
/** 经营看板是 4 列网格：宽度 3→1 列、6→2 列、8→3 列、12→4 列 */
function gridSpan(id, dflt) {
  const s = Number(L.sizeOf(id, dflt || 3))
  return Math.max(1, Math.min(4, Math.round(s / 3)))
}
/** 各块 KPI 下钻：跳目标页并带初始筛选条件（是否应用/重置以目标页自身筛选为准） */
function go(path, query) {
  if (!path) return
  router.push(query ? { path, query } : path)
}

const period = ref('month')
const sale = ref({})
const finance = ref({})
const inventory = ref({})
const op = ref({})

function num(v) {
  if (v === null || v === undefined || v === '') return null
  return Number(v)
}
function money(v) {
  const n = num(v)
  if (n === null) return null
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function count(v) {
  const n = num(v)
  if (n === null) return null
  return n.toLocaleString('zh-CN')
}
function pct(v) {
  const n = num(v)
  if (n === null) return null
  return n + '%'
}
/** 从数据看板返回体里按候选字段名取第一个有值的（字段名不匹配时返回 null → 页面显示暂无数据） */
function pick(obj, names) {
  for (const n of names) {
    if (obj && obj[n] !== null && obj[n] !== undefined && obj[n] !== '') return obj[n]
  }
  return null
}
function rowsOf(list) {
  return list.filter(r => r.value !== null && r.value !== undefined && r.value !== '')
      .map(r => ({ label: r.label, value: r.value }))
}

/** 当前时间维度的起止日期（YYYY-MM-DD，含端点），作为下钻目标列表的初始筛选条件 */
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

// 趋势折线画布尺寸（简易 SVG，等比拉伸铺满卡片宽度，不依赖任何图表库）
const SPARK_W = 100
const SPARK_H = 32
/** 把序列值映射成 polyline 坐标串 */
function sparkline(points) {
  const vals = points.map(p => p.value)
  let min = Math.min(...vals)
  let max = Math.max(...vals)
  if (max === min) max = min + 1
  const stepX = SPARK_W / (points.length - 1)
  return points.map((p, i) => {
    const x = i * stepX
    const y = SPARK_H - ((p.value - min) / (max - min)) * (SPARK_H - 2) - 1
    return `${x.toFixed(1)},${y.toFixed(1)}`
  }).join(' ')
}
/** 从看板返回体的趋势数组构建折线数据；无数据源或不足两个点时返回 null（该块不显示趋势行） */
function buildTrend(arr, valFn, label) {
  if (!Array.isArray(arr) || arr.length < 2) return null
  const points = arr
    .filter(r => r && r.month !== null && r.month !== undefined && r.month !== '')
    .map(r => ({ label: String(r.month), value: num(valFn(r)) }))
    .filter(p => p.value !== null && Number.isFinite(p.value))
  if (points.length < 2) return null
  return {
    polyline: sparkline(points),
    label,
    from: points[0].label.replace('-', '/'),
    to: points[points.length - 1].label.replace('-', '/')
  }
}

const blocks = computed(() => [
  {
    key: 'sale', title: '销售', tag: periodLabel(),
    route: '/databoard/sale',
    rows: rowsOf([
      { label: '销售额', value: money(pick(sale.value, ['periodSale', 'saleAmount', 'periodAmount', 'totalSale'])) },
      { label: '订单量', value: count(pick(sale.value, ['periodOrderCount', 'orderCount', 'periodOrders'])) },
      { label: '目标达成率', value: pct(pick(sale.value, ['targetRate', 'periodTargetRate', 'achievementRate'])) }
    ]),
    trend: buildTrend(sale.value.saleTrend, r => r.amount, '销售额走势（近12月）')
  },
  {
    key: 'profit', title: '利润', tag: periodLabel(),
    route: '/databoard/finance',
    rows: rowsOf([
      { label: '净利润', value: money(pick(finance.value, ['netProfit'])) },
      { label: '毛利率', value: pct(pick(finance.value, ['grossMarginRate'])) }
    ]),
    trend: buildTrend(finance.value.revenueTrend, r => r.gross, '毛利走势（近12月）')
  },
  {
    key: 'receipt', title: '回款', tag: periodLabel(),
    route: '/databoard/finance',
    rows: rowsOf([
      { label: '回款率', value: pct(pick(sale.value, ['paybackRate'])) },
      { label: '应收余额', value: money(pick(finance.value, ['arBalance'])) },
      { label: '逾期金额', value: money(pick(finance.value, ['arOverdue'])) }
    ])
  },
  {
    key: 'production', title: '生产', tag: '新增',
    route: '/production/workorders', query: periodRange(),
    rows: rowsOf([
      { label: '在制工单', value: count(op.value.production?.inProgress) },
      { label: '已完工', value: count(op.value.production?.finished) },
      { label: '延期工单', value: count(op.value.production?.delayed) }
    ])
  },
  {
    key: 'purchase', title: '采购', tag: '新增',
    route: '/purchase/orders', query: periodRange(),
    rows: rowsOf([
      { label: '订单数', value: count(op.value.purchase?.orderCount) },
      { label: '下单金额', value: money(op.value.purchase?.orderAmount) },
      { label: '未完结', value: count(op.value.purchase?.pendingCount) }
    ])
  },
  {
    key: 'inventory', title: '库存', tag: periodLabel(),
    route: '/inventory/current',
    rows: rowsOf([
      { label: '库存金额', value: money(pick(inventory.value, ['stockTotalAmount'])) },
      { label: '周转率', value: num(pick(inventory.value, ['turnoverRate'])) },
      { label: '缺货SKU', value: count(pick(inventory.value, ['shortageSkuCount'])) }
    ]),
    trend: buildTrend(inventory.value.stockVsSaleTrend, r => r.stockOutAmount, '出库金额走势（近6月）')
  },
  {
    key: 'quality', title: '质量', tag: '新增',
    route: '/production/qc-record', query: periodRange(),
    rows: rowsOf([
      { label: '检验批次', value: count(op.value.quality?.inspectCount) },
      { label: '不合格批次', value: count(op.value.quality?.defectCount) },
      { label: '合格率', value: pct(op.value.quality?.passRate) }
    ])
  },
  {
    key: 'hr', title: '人力', tag: '新增',
    route: '/basedata/employee',
    rows: rowsOf([
      { label: '在职人数', value: count(op.value.hr?.headcount) },
      { label: '部门数', value: count(op.value.hr?.deptCount) }
    ]).concat((op.value.hr?.deptDist || []).slice(0, 3).map(d => ({
      label: d.name || '未分部门', value: count(d.value)
    })))
  }
])

function periodLabel() {
  return { day: '今日', week: '本周', month: '本月', quarter: '本季', year: '本年' }[period.value] || '本月'
}

async function load() {
  const p = period.value
  try { sale.value = await getSaleBoard(p) || {} } catch (e) { sale.value = {} }
  try { finance.value = await getFinanceBoard(p) || {} } catch (e) { finance.value = {} }
  try { inventory.value = await getInventoryBoard(p) || {} } catch (e) { inventory.value = {} }
  try { op.value = await getOperationBoard(p) || {} } catch (e) { op.value = {} }
  // 本页被改成自定义取数的区块与新增卡片，跟着时间维度一起刷新
  L.refreshData(p)
}

onMounted(async () => {
  await L.load(period.value)
  load()
})
</script>

<style scoped>
.erp-page { padding: 12px; }
.ob-head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 12px; }
.ob-title { font-size: 16px; font-weight: 600; display: flex; align-items: center; }
.ob-sub { font-size: 12px; color: #909399; }
.ob-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.ob-card { border: 1px solid var(--el-border-color-lighter); border-radius: 8px; padding: 14px; background: var(--el-fill-color-blank); transition: box-shadow .2s; }
.ob-card.clickable { cursor: pointer; }
.ob-card.clickable:hover { box-shadow: 0 2px 12px rgba(0, 0, 0, .08); }
.ob-card-head { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.ob-card-title { font-size: 14px; font-weight: 600; }
.drill-arrow { color: #c0c4cc; font-size: 14px; margin-left: auto; }
.ob-row { display: flex; justify-content: space-between; gap: 8px; padding: 5px 0; font-size: 13px; }
.ob-row-label { color: #909399; }
.ob-row-val { font-weight: 600; font-variant-numeric: tabular-nums; }
.ob-empty { color: #c0c4cc; font-size: 13px; padding: 8px 0; }
.ob-trend { margin-top: 10px; padding-top: 8px; border-top: 1px dashed var(--el-border-color-lighter); }
.ob-trend-head { display: flex; justify-content: space-between; align-items: center; font-size: 12px; color: #909399; margin-bottom: 4px; }
.ob-trend-range { color: #c0c4cc; }
.ob-spark { display: block; width: 100%; height: 34px; }
.ob-spark polyline { fill: none; stroke: #409EFF; stroke-width: 1.5; vector-effect: non-scaling-stroke; }
.ob-note { margin-top: 12px; font-size: 12px; color: #909399; line-height: 1.6; }
</style>
