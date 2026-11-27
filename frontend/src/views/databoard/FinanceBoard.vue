<template>
  <div class="board-page">
    <div class="board-header">
      <div class="board-title">
        <h2>{{ $tt("财务数据看板") }}</h2>
        <span class="board-sub">
          口径：已生效销售订单 / 应收单 / 收付款单 / 费用单（真实单据汇总）。
          营收卡片=含税口径，毛利率/费用率分母=不含税营收；成本为完全成本法（含已核算的人工/制造费用分摊），
          故净利润不再重复扣减费用分摊。系统无"暂估应收""其他业务收入"单据，故未纳入；区域维度取自客户自定义字段「区域」。
        </span>
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

    <!-- 战略层：核心经营成果 -->
    <el-row :gutter="12" class="kpi-row">
      <el-col v-if="L.show('kpiRevenue')" :xs="L.xsOf('kpiRevenue', 6)" :sm="L.sizeOf('kpiRevenue', 6)" :lg="L.sizeOf('kpiRevenue', 6)">
        <BoardCard v-if="L.isOverride('kpiRevenue')" :card="L.cardCfg('kpiRevenue')" :data="L.dataOf('kpiRevenue')" :error="L.errOf('kpiRevenue')" />
        <div v-else class="kpi-card clickable" @click="drillOrders()">
          <div class="kpi-label">{{ d.periodLabel }}营收（含税） <span class="drill-arrow">›</span></div>
          <div class="kpi-value">{{ fmt(d.periodRevenue) }}</div>
          <div class="kpi-foot">今日 {{ fmt(d.todayRevenue) }} / 本月 {{ fmt(d.monthRevenue) }}</div>
        </div>
      </el-col>
      <el-col v-if="L.show('kpiGross')" :xs="L.xsOf('kpiGross', 6)" :sm="L.sizeOf('kpiGross', 6)" :lg="L.sizeOf('kpiGross', 6)">
        <BoardCard v-if="L.isOverride('kpiGross')" :card="L.cardCfg('kpiGross')" :data="L.dataOf('kpiGross')" :error="L.errOf('kpiGross')" />
        <div v-else class="kpi-card clickable" @click="drillOrders()">
          <div class="kpi-label">
            {{ $tt("毛利率（不含税口径）") }} <span class="drill-arrow">›</span>
            <span class="detail-link" @click.stop="breakdownVisible = true">{{ $tt("构成明细") }}</span>
          </div>
          <div class="kpi-value" :class="{ 'warn-text': costStats.allMissing }">
            {{ costStats.allMissing ? $tt("成本缺失") : fmtRate(d.grossMarginRate) }}
          </div>
          <div class="kpi-foot">{{ grossFoot }}</div>
        </div>
      </el-col>
      <el-col v-if="L.show('kpiNet')" :xs="L.xsOf('kpiNet', 6)" :sm="L.sizeOf('kpiNet', 6)" :lg="L.sizeOf('kpiNet', 6)">
        <BoardCard v-if="L.isOverride('kpiNet')" :card="L.cardCfg('kpiNet')" :data="L.dataOf('kpiNet')" :error="L.errOf('kpiNet')" />
        <div v-else class="kpi-card clickable" @click="drillOrders()">
          <div class="kpi-label">
            {{ $tt("净利润") }} <span class="drill-arrow">›</span>
            <span class="detail-link" @click.stop="breakdownVisible = true">{{ $tt("构成明细") }}</span>
          </div>
          <div class="kpi-value" :class="{ negative: nvl(d.netProfit) < 0, 'warn-text': costStats.allMissing }">
            {{ costStats.allMissing ? $tt("成本缺失") : fmt(d.netProfit) }}
          </div>
          <div class="kpi-foot" :title="netProfitTip">{{ netProfitText }}{{ costMissingSuffix }}</div>
        </div>
      </el-col>
      <el-col v-if="canSee('/crm/sales-target') && L.show('kpiTarget')" :xs="L.xsOf('kpiTarget', 6)" :sm="L.sizeOf('kpiTarget', 6)" :lg="L.sizeOf('kpiTarget', 6)">
        <BoardCard v-if="L.isOverride('kpiTarget')" :card="L.cardCfg('kpiTarget')" :data="L.dataOf('kpiTarget')" :error="L.errOf('kpiTarget')" />
        <div v-else class="kpi-card clickable" :class="{ warn: d.targetWarn }" @click="go('/crm/sales-target', { year: String(new Date().getFullYear()) })">
          <div class="kpi-label">{{ $tt("营收目标达成率") }} <span class="drill-arrow">›</span></div>
          <div class="kpi-value" :class="{ 'warn-text': d.targetWarn }">{{ fmtRate(d.targetRate) }}</div>
          <div class="kpi-foot">目标 {{ fmt(d.targetAmount) }}{{ d.targetWarn ? '（低于85%预警）' : '' }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 运营层：现金流与应收 -->
    <el-row :gutter="12" class="kpi-row">
      <el-col v-if="L.show('kpiArBalance')" :xs="L.xsOf('kpiArBalance', 6)" :sm="L.sizeOf('kpiArBalance', 6)" :lg="L.sizeOf('kpiArBalance', 6)">
        <BoardCard v-if="L.isOverride('kpiArBalance')" :card="L.cardCfg('kpiArBalance')" :data="L.dataOf('kpiArBalance')" :error="L.errOf('kpiArBalance')" />
        <div v-else class="kpi-card clickable" @click="goAr()">
          <div class="kpi-label">{{ $tt("应收账款余额") }} <span class="drill-arrow">›</span></div>
          <div class="kpi-value">{{ fmt(d.arBalance) }}</div>
          <div class="kpi-foot">周转天数 {{ fmt(d.arTurnoverDays) }} 天（仅财务应收，不含暂估应收）</div>
        </div>
      </el-col>
      <el-col v-if="L.show('kpiArOverdue')" :xs="L.xsOf('kpiArOverdue', 6)" :sm="L.sizeOf('kpiArOverdue', 6)" :lg="L.sizeOf('kpiArOverdue', 6)">
        <BoardCard v-if="L.isOverride('kpiArOverdue')" :card="L.cardCfg('kpiArOverdue')" :data="L.dataOf('kpiArOverdue')" :error="L.errOf('kpiArOverdue')" />
        <div v-else class="kpi-card clickable" :class="{ warn: nvl(d.arOverdue) > 0 }" @click="goAr(true)">
          <div class="kpi-label">{{ $tt("逾期应收金额") }} <span class="drill-arrow">›</span></div>
          <div class="kpi-value" :class="{ 'warn-text': nvl(d.arOverdue) > 0 }">{{ fmt(d.arOverdue) }}</div>
          <div class="kpi-foot">{{ $tt("逾期即现金流风险") }}</div>
        </div>
      </el-col>
      <el-col v-if="L.show('kpiCashFlow')" :xs="L.xsOf('kpiCashFlow', 6)" :sm="L.sizeOf('kpiCashFlow', 6)" :lg="L.sizeOf('kpiCashFlow', 6)">
        <BoardCard v-if="L.isOverride('kpiCashFlow')" :card="L.cardCfg('kpiCashFlow')" :data="L.dataOf('kpiCashFlow')" :error="L.errOf('kpiCashFlow')" />
        <div v-else class="kpi-card clickable" @click="go('/finance/receipt', periodRange())">
          <div class="kpi-label">{{ $tt("经营现金流净额") }} <span class="drill-arrow">›</span></div>
          <div class="kpi-value" :class="{ negative: nvl(d.cashFlowNet) < 0 }">{{ fmt(d.cashFlowNet) }}</div>
          <div class="kpi-foot">流入 {{ fmt(d.cashIn) }} / 流出 {{ fmt(d.cashOut) }}</div>
        </div>
      </el-col>
      <el-col v-if="L.show('kpiRisk')" :xs="L.xsOf('kpiRisk', 6)" :sm="L.sizeOf('kpiRisk', 6)" :lg="L.sizeOf('kpiRisk', 6)">
        <BoardCard v-if="L.isOverride('kpiRisk')" :card="L.cardCfg('kpiRisk')" :data="L.dataOf('kpiRisk')" :error="L.errOf('kpiRisk')" />
        <div v-else class="kpi-card clickable" :class="{ warn: nvl(d.riskAmount) > 0 }" @click="go('/finance/report/ar-summary', { agingOver: '180' })">
          <div class="kpi-label">{{ $tt("坏账风险预警（180天+）") }} <span class="drill-arrow">›</span></div>
          <div class="kpi-value" :class="{ 'warn-text': nvl(d.riskAmount) > 0 }">{{ fmt(d.riskAmount) }}</div>
          <div class="kpi-foot">占应收 {{ fmtRate(d.riskRate) }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 费用结构（费用分摊属旗舰版 → 标准版/CRM版整块隐藏） -->
    <el-row v-if="canSeeExpenseSplit" :gutter="12" class="kpi-row">
      <el-col v-if="L.show('kpiExpSale')" :xs="L.xsOf('kpiExpSale', 6)" :sm="L.sizeOf('kpiExpSale', 6)" :lg="L.sizeOf('kpiExpSale', 6)">
        <BoardCard v-if="L.isOverride('kpiExpSale')" :card="L.cardCfg('kpiExpSale')" :data="L.dataOf('kpiExpSale')" :error="L.errOf('kpiExpSale')" />
        <div v-else class="kpi-card clickable" @click="go('/finance/operation-expense', { expenseType: '销售费用', ...periodRange() })">
          <div class="kpi-label">{{ $tt("销售费用 / 费用率") }} <span class="drill-arrow">›</span></div>
          <div class="kpi-value sm">{{ fmt(d.expenseSale) }}</div>
          <div class="kpi-foot">费用率 {{ fmtRate(d.saleExpenseRate) }}</div>
        </div>
      </el-col>
      <el-col v-if="L.show('kpiExpManage')" :xs="L.xsOf('kpiExpManage', 6)" :sm="L.sizeOf('kpiExpManage', 6)" :lg="L.sizeOf('kpiExpManage', 6)">
        <BoardCard v-if="L.isOverride('kpiExpManage')" :card="L.cardCfg('kpiExpManage')" :data="L.dataOf('kpiExpManage')" :error="L.errOf('kpiExpManage')" />
        <div v-else class="kpi-card clickable" @click="go('/finance/operation-expense', periodRange())">
          <div class="kpi-label">{{ $tt("管理费用 / 费用率") }} <span class="drill-arrow">›</span></div>
          <div class="kpi-value sm">{{ fmt(d.expenseManage) }}</div>
          <div class="kpi-foot">费用率 {{ fmtRate(d.manageExpenseRate) }}</div>
        </div>
      </el-col>
      <el-col v-if="L.show('kpiExpFinance')" :xs="L.xsOf('kpiExpFinance', 6)" :sm="L.sizeOf('kpiExpFinance', 6)" :lg="L.sizeOf('kpiExpFinance', 6)">
        <BoardCard v-if="L.isOverride('kpiExpFinance')" :card="L.cardCfg('kpiExpFinance')" :data="L.dataOf('kpiExpFinance')" :error="L.errOf('kpiExpFinance')" />
        <div v-else class="kpi-card clickable" @click="go('/finance/operation-expense', { expenseType: '财务费用', ...periodRange() })">
          <div class="kpi-label">{{ $tt("财务费用 / 费用率") }} <span class="drill-arrow">›</span></div>
          <div class="kpi-value sm">{{ fmt(d.financeExpense) }}</div>
          <div class="kpi-foot">费用率 {{ fmtRate(d.financeExpenseRate) }}（费用分摊单按"财务费用"类型统计）</div>
        </div>
      </el-col>
      <el-col v-if="L.show('kpiExpPurchase')" :xs="L.xsOf('kpiExpPurchase', 6)" :sm="L.sizeOf('kpiExpPurchase', 6)" :lg="L.sizeOf('kpiExpPurchase', 6)">
        <BoardCard v-if="L.isOverride('kpiExpPurchase')" :card="L.cardCfg('kpiExpPurchase')" :data="L.dataOf('kpiExpPurchase')" :error="L.errOf('kpiExpPurchase')" />
        <div v-else class="kpi-card clickable" @click="go('/finance/operation-expense', periodRange())">
          <div class="kpi-label">{{ $tt("采购费用 / 税金") }} <span class="drill-arrow">›</span></div>
          <div class="kpi-value sm">{{ fmt(d.expensePurchase) }} / {{ fmt(d.taxAmount) }}</div>
          <div class="kpi-foot">费用合计 {{ fmt(d.expenseTotal) }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 图表区 -->
    <el-row :gutter="12">
      <el-col v-if="L.show('chartTrend')" :xs="L.xsOf('chartTrend', 12)" :lg="L.sizeOf('chartTrend', 12)">
        <BoardCard v-if="L.isOverride('chartTrend')" :card="L.cardCfg('chartTrend')" :data="L.dataOf('chartTrend')" :error="L.errOf('chartTrend')" />
        <el-card v-else shadow="never" class="chart-card">
          <template #header>{{ $tt("近12月营收 / 成本 / 毛利趋势（点看当月订单）") }}</template>
          <div ref="trendRef" class="chart" />
        </el-card>
      </el-col>
      <el-col v-if="L.show('chartLineGross')" :xs="L.xsOf('chartLineGross', 12)" :lg="L.sizeOf('chartLineGross', 12)">
        <BoardCard v-if="L.isOverride('chartLineGross')" :card="L.cardCfg('chartLineGross')" :data="L.dataOf('chartLineGross')" :error="L.errOf('chartLineGross')" />
        <el-card v-else shadow="never" class="chart-card">
          <template #header>{{ $tt("产品线毛利贡献（点柱看订单明细）") }}</template>
          <div ref="lineGrossRef" class="chart" />
        </el-card>
      </el-col>
      <el-col v-if="canSeeExpenseSplit && L.show('chartExpense')" :xs="L.xsOf('chartExpense', 12)" :lg="L.sizeOf('chartExpense', 12)">
        <BoardCard v-if="L.isOverride('chartExpense')" :card="L.cardCfg('chartExpense')" :data="L.dataOf('chartExpense')" :error="L.errOf('chartExpense')" />
        <el-card v-else shadow="never" class="chart-card">
          <template #header>{{ $tt("费用结构（按费用类型，点扇区看费用单）") }}</template>
          <div ref="expenseRef" class="chart" />
        </el-card>
      </el-col>
      <el-col v-if="L.show('chartAging')" :xs="L.xsOf('chartAging', 12)" :lg="L.sizeOf('chartAging', 12)">
        <BoardCard v-if="L.isOverride('chartAging')" :card="L.cardCfg('chartAging')" :data="L.dataOf('chartAging')" :error="L.errOf('chartAging')" />
        <el-card v-else shadow="never" class="chart-card">
          <template #header>{{ $tt("应收账款账龄分布（点柱看应收明细）") }}</template>
          <div ref="agingRef" class="chart" />
        </el-card>
      </el-col>
      <el-col v-if="L.show('chartRegion')" :xs="L.xsOf('chartRegion', 12)" :lg="L.sizeOf('chartRegion', 12)">
        <BoardCard v-if="L.isOverride('chartRegion')" :card="L.cardCfg('chartRegion')" :data="L.dataOf('chartRegion')" :error="L.errOf('chartRegion')" />
        <el-card v-else shadow="never" class="chart-card">
          <template #header>{{ $tt("各区域营收分布（点扇区看客户）") }}</template>
          <div ref="regionRef" class="chart" />
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

    <!-- 毛利与净利润构成明细：口径、成本来源、缺失与异常行数 -->
    <el-dialog v-model="breakdownVisible" :title="$tt('毛利与净利润构成')" width="640px" top="8vh" append-to-body>
      <table class="breakdown-table">
        <tbody>
          <tr><td>不含税营收</td><td>{{ fmt(d.grossBaseNet) }}</td></tr>
          <tr><td>销售成本</td><td>{{ fmt(d.periodCost) }}</td></tr>
          <tr class="strong"><td>毛利</td><td>{{ fmt(d.grossProfit) }}</td></tr>
          <tr><td>销售费用</td><td>{{ fmt(d.expenseSale) }}</td></tr>
          <tr><td>采购费用单金额</td><td>{{ fmt(d.expensePurchase) }}</td></tr>
          <tr><td>税金</td><td>{{ fmt(d.taxAmount) }}</td></tr>
          <tr class="strong"><td>净利润</td><td>{{ fmt(d.netProfit) }}</td></tr>
        </tbody>
      </table>
      <div class="breakdown-cost">
        <div>成本明细行数：{{ nvl(costStats.totalRows) }}</div>
        <div :class="{ 'warn-text': nvl(costStats.missingRows) > 0 }">成本缺失：{{ nvl(costStats.missingRows) }} 行</div>
        <div :class="{ 'warn-text': nvl(costStats.abnormalRows) > 0 }">成本异常：{{ nvl(costStats.abnormalRows) }} 行</div>
        <div v-if="costSourceText">成本来源：{{ costSourceText }}</div>
        <div class="breakdown-tip">毛利按已取得成本的明细计算，成本缺失的明细不参与。</div>
        <div class="breakdown-tip">成本顺序：成本核算单、库存移动平均成本、最近采购价。</div>
      </div>
    </el-dialog>

    <!-- 看板设置：显隐 / 宽度 / 顺序 / 换数据源与公式 / 新增卡片 -->
    <BoardLayoutSetting v-model="settingVisible" :layout="L" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh, Setting } from '@element-plus/icons-vue'
import echarts from '@/utils/echartsCore'
import { getFinanceBoard } from '@/api/dashboard/dataBoard'
import { usePagePerm } from '@/utils/pagePerm'
import { useUserStore } from '@/stores/user'
import { isRouteVisible } from '@/utils/versionGate'
import BoardCard from '@/components/board/BoardCard.vue'
import BoardLayoutSetting from '@/components/board/BoardLayoutSetting.vue'
import { useBoardLayout } from '@/composables/useBoardLayout'

const perm = usePagePerm('databoard:finance')
const userStore = useUserStore()
// 版本隔离（2026-10-10）：费用分摊属旗舰版，标准版/CRM版不展示"费用结构"块与下钻
const canSeeExpenseSplit = computed(() => (userStore.tenantVersion || '').toUpperCase() === 'ULTIMATE')
// 卡片下钻目标不属于当前账号可见菜单 → 整张卡不显示
const canSee = (path) => isRouteVisible(path, userStore.menuRoutePaths || [])

/** 本页原有区块清单：看板设置里可逐块显隐 / 调宽 / 换自己的数据源与公式 */
const BUILTINS = [
  { id: 'kpiRevenue', title: '营收（含税）', size: 6 },
  { id: 'kpiGross', title: '毛利率', size: 6 },
  { id: 'kpiNet', title: '净利润', size: 6 },
  { id: 'kpiTarget', title: '营收目标达成率', size: 6 },
  { id: 'kpiArBalance', title: '应收账款余额', size: 6 },
  { id: 'kpiArOverdue', title: '逾期应收金额', size: 6 },
  { id: 'kpiCashFlow', title: '经营现金流净额', size: 6 },
  { id: 'kpiRisk', title: '坏账风险预警', size: 6 },
  { id: 'kpiExpSale', title: '销售费用 / 费用率', size: 6 },
  { id: 'kpiExpManage', title: '管理费用 / 费用率', size: 6 },
  { id: 'kpiExpFinance', title: '财务费用 / 费用率', size: 6 },
  { id: 'kpiExpPurchase', title: '采购费用 / 税金', size: 6 },
  { id: 'chartTrend', title: '营收成本毛利趋势', size: 12 },
  { id: 'chartLineGross', title: '产品线毛利贡献', size: 12 },
  { id: 'chartExpense', title: '费用结构', size: 12 },
  { id: 'chartAging', title: '应收账龄分布', size: 12 },
  { id: 'chartRegion', title: '各区域营收分布', size: 12 }
]
const L = useBoardLayout('finance', BUILTINS)
const settingVisible = ref(false)

const router = useRouter()
const period = ref('month')
const autoRefresh = ref(true)
const d = ref({})
const trendRef = ref()
const lineGrossRef = ref()
const expenseRef = ref()
const agingRef = ref()
const regionRef = ref()
const charts = []
let timer = null

const nvl = v => (v == null ? 0 : Number(v))
const fmt = v => (v == null || v === '' ? '' : Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 2 }))
const fmtRate = v => (v == null || v === '' ? '' : Number(v).toFixed(1) + '%')
const go = (p, query) => router.push(query ? { path: p, query } : p)
const goAr = (overdueOnly) => go('/finance/report/ar-summary', overdueOnly ? { overdueOnly: '1' } : {})

const netProfitText = computed(() => {
  const n = d.value.netProfitDetail
  if (!n) return '毛利−销售费用−采购费用−税金'
  return `毛利${fmt(n.grossProfit)} − 销${fmt(n.saleExpense)} − 采${fmt(n.purchaseExpense)} − 税${fmt(n.taxAmount)}`
})
const netProfitTip = computed(() => {
  const n = d.value.netProfitDetail
  if (!n) return ''
  return `净利润 = 毛利(${fmt(n.grossProfit)}) − 销售费用(${fmt(n.saleExpense)}) − 采购费用(${fmt(n.purchaseExpense)}) − 税金(${fmt(n.taxAmount)})；费用分摊(${fmt(n.manageExpenseInCost)})已通过完全成本法计入销售成本，不再重复扣减`
})

// ==================== 成本口径与构成明细 ====================
const breakdownVisible = ref(false)
const costStats = computed(() => d.value.costStats || {})
const costMissingSuffix = computed(() => {
  const n = nvl(costStats.value.missingRows)
  return n > 0 ? ` · 成本缺失 ${n} 行` : ''
})
const grossFoot = computed(() => {
  if (costStats.value.allMissing) return '全部明细缺少成本，暂不计算毛利'
  const base = `毛利 ${fmt(d.value.grossProfit)} / 成本 ${fmt(d.value.periodCost)}`
  const n = nvl(costStats.value.missingRows)
  return n > 0 ? `${base} · 部分成本缺失 ${n} 行` : base
})
const costSourceText = computed(() => {
  const m = costStats.value.sourceCounts || {}
  return Object.entries(m).map(([k, v]) => `${k} ${v} 行`).join(' / ')
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
function drillOrders(extra) {
  go('/sale/orders', { status: '已生效', ...periodRange(), ...(extra || {}) })
}

async function loadData() {
  // 注意：request 响应拦截器已把 {code,message,data} 解包为 data，这里直接就是业务数据
  const res = await getFinanceBoard(period.value)
  d.value = res || {}
  await nextTick()
  renderCharts()
  // 本页被改成自定义取数的区块与新增卡片，跟着时间维度一起刷新
  L.refreshData(period.value)
}

function renderCharts() {
  disposeCharts()
  const data = d.value
  if (trendRef.value) {
    const c = echarts.init(trendRef.value)
    const t = data.revenueTrend || []
    c.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['营收', '成本', '毛利'] },
      grid: { left: 60, right: 20, top: 40, bottom: 30 },
      xAxis: { type: 'category', data: t.map(x => x.month) },
      yAxis: { type: 'value' },
      series: [
        { name: '营收', type: 'line', smooth: true, data: t.map(x => nvl(x.revenue)) },
        { name: '成本', type: 'line', smooth: true, data: t.map(x => nvl(x.cost)) },
        { name: '毛利', type: 'line', smooth: true, data: t.map(x => nvl(x.gross)) }
      ]
    })
    c.on('click', p => {
      const row = t[p.dataIndex]
      if (!row || !row.month) return
      const [y, m] = String(row.month).split('-')
      const last = new Date(Number(y), Number(m), 0).getDate()
      drillOrders({ startDate: `${row.month}-01`, endDate: `${row.month}-${String(last).padStart(2, '0')}` })
    })
    charts.push(c)
  }
  if (lineGrossRef.value) {
    const c = echarts.init(lineGrossRef.value)
    const g = data.productLineGross || []
    c.setOption({
      tooltip: { trigger: 'axis', valueFormatter: v => fmt(v) },
      grid: { left: 60, right: 20, top: 20, bottom: 50 },
      xAxis: { type: 'category', data: g.map(x => x.name), axisLabel: { rotate: 20 } },
      yAxis: { type: 'value' },
      series: [
        { name: '毛利', type: 'bar', data: g.map(x => nvl(x.gross)) },
        { name: '营收(不含税)', type: 'bar', data: g.map(x => nvl(x.netRevenue)) }
      ]
    })
    c.on('click', p => {
      const ref = (data.productLineRef || []).find(x => x.name === g[p.dataIndex]?.name)
      drillOrders(ref && ref.categoryId ? { productLineId: String(ref.categoryId) } : {})
    })
    charts.push(c)
  }
  if (expenseRef.value) {
    const c = echarts.init(expenseRef.value)
    const e = (data.expenseStructure || []).filter(x => nvl(x.value) > 0)
    c.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      series: [{
        type: 'pie', radius: ['35%', '65%'], center: ['50%', '50%'],
        label: { formatter: '{b}\n{d}%' },
        data: e.map(x => ({ name: x.name, value: nvl(x.value) }))
      }]
    })
    c.on('click', p => { if (canSeeExpenseSplit.value) go('/finance/operation-expense', { expenseType: p.name, ...periodRange() }) })
    charts.push(c)
  }
  if (agingRef.value) {
    const c = echarts.init(agingRef.value)
    const a = data.arAging || []
    c.setOption({
      tooltip: { trigger: 'axis', valueFormatter: v => fmt(v) },
      grid: { left: 60, right: 20, top: 20, bottom: 60 },
      xAxis: { type: 'category', data: a.map(x => x.name), axisLabel: { rotate: 20 } },
      yAxis: { type: 'value' },
      series: [{
        name: '金额', type: 'bar', data: a.map(x => nvl(x.value)),
        itemStyle: { color: p => (p.name.includes('180') ? '#F56C6C' : '#409EFF') }
      }]
    })
    c.on('click', p => {
      const name = a[p.dataIndex]?.name || ''
      if (name.includes('180')) go('/finance/report/ar-summary', { agingOver: '180' })
      else if (name.includes('61')) go('/finance/report/ar-summary', { agingOver: '61' })
      else if (name.includes('31')) go('/finance/report/ar-summary', { agingOver: '31' })
      else if (name.includes('1-30')) go('/finance/report/ar-summary', { agingOver: '1' })
      else goAr()
    })
    charts.push(c)
  }
  if (regionRef.value) {
    const c = echarts.init(regionRef.value)
    const list = data.regionDist || []
    c.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      series: [{
        type: 'pie', radius: ['35%', '65%'],
        label: { formatter: '{b}\n{d}%' },
        data: list.map(x => ({ name: x.name, value: nvl(x.value) }))
      }]
    })
    c.on('click', p => go('/basedata/customer', { region: p.name }))
    charts.push(c)
  }
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
.detail-link { float: right; font-size: 12px; color: #1890ff; cursor: pointer; }
.detail-link:hover { text-decoration: underline; }
.breakdown-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.breakdown-table td { border-bottom: 1px solid #ebeef5; padding: 7px 8px; }
.breakdown-table td:last-child { text-align: right; }
.breakdown-table tr.strong td { font-weight: 600; color: #1f2329; }
.breakdown-cost { margin-top: 12px; font-size: 12.5px; color: #606266; line-height: 1.8; }
.breakdown-tip { color: #909399; }
.drill-arrow { color: #c0c4cc; font-size: 13px; }
.kpi-value { font-size: 22px; font-weight: 600; color: #303133; }
.kpi-value.sm { font-size: 18px; }
.kpi-foot { font-size: 12px; color: #b0b3b8; margin-top: 6px; }
.kpi-card.warn { border-color: #fde2e2; background: #fef0f0; }
.warn-text { color: #F56C6C !important; }
.negative { color: #F56C6C !important; }
.chart-card { margin-bottom: 12px; }
.chart { width: 100%; height: 320px; }
</style>
