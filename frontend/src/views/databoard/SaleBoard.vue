<template>
  <div class="board-page">
    <div class="board-header">
      <div class="board-title">
        <h2>{{ $tt("销售数据看板") }}</h2>
        <span class="board-sub">口径：已生效销售订单 / 应收单 / 商机（数据均为真实单据汇总）；区域维度取自客户自定义字段「区域」，未维护的客户归入"未填写"</span>
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

    <!-- 战略层 -->
    <el-row :gutter="12" class="kpi-row">
      <el-col v-if="L.show('kpiSale')" :xs="L.xsOf('kpiSale', 6)" :sm="L.sizeOf('kpiSale', 6)" :lg="L.sizeOf('kpiSale', 6)">
        <BoardCard v-if="L.isOverride('kpiSale')" :card="L.cardCfg('kpiSale')" :data="L.dataOf('kpiSale')" :error="L.errOf('kpiSale')" />
        <div v-else class="kpi-card clickable" @click="drillOrders()">
          <div class="kpi-label">{{ d.periodLabel }}销售额 <span class="drill-arrow">›</span></div>
          <div class="kpi-value">{{ fmt(d.periodSale) }}</div>
          <div class="kpi-foot">今日 {{ fmt(d.todaySale) }} / 本月 {{ fmt(d.monthSale) }}</div>
        </div>
      </el-col>
      <el-col v-if="canSee('/crm/sales-target') && L.show('kpiTarget')" :xs="L.xsOf('kpiTarget', 6)" :sm="L.sizeOf('kpiTarget', 6)" :lg="L.sizeOf('kpiTarget', 6)">
        <BoardCard v-if="L.isOverride('kpiTarget')" :card="L.cardCfg('kpiTarget')" :data="L.dataOf('kpiTarget')" :error="L.errOf('kpiTarget')" />
        <div v-else class="kpi-card clickable" :class="{ warn: d.targetWarn }" @click="go('/crm/sales-target', { year: String(new Date().getFullYear()) })">
          <div class="kpi-label">{{ $tt("销售目标完成率") }} <span class="drill-arrow">›</span></div>
          <div class="kpi-value" :class="{ 'warn-text': d.targetWarn }">{{ fmtRate(d.targetRate) }}</div>
          <div class="kpi-foot">目标 {{ fmt(d.targetAmount) }}{{ d.targetWarn ? '（低于85%预警）' : '' }}</div>
        </div>
      </el-col>
      <el-col v-if="L.show('kpiOrder')" :xs="L.xsOf('kpiOrder', 6)" :sm="L.sizeOf('kpiOrder', 6)" :lg="L.sizeOf('kpiOrder', 6)">
        <BoardCard v-if="L.isOverride('kpiOrder')" :card="L.cardCfg('kpiOrder')" :data="L.dataOf('kpiOrder')" :error="L.errOf('kpiOrder')" />
        <div v-else class="kpi-card clickable" @click="drillOrders()">
          <div class="kpi-label">{{ $tt("订单量 / 平均客单价") }} <span class="drill-arrow">›</span></div>
          <div class="kpi-value">{{ fmt(d.orderCount) }}</div>
          <div class="kpi-foot">平均客单价 {{ fmt(d.avgOrderAmount) }}</div>
        </div>
      </el-col>
      <el-col v-if="L.show('kpiPayback')" :xs="L.xsOf('kpiPayback', 6)" :sm="L.sizeOf('kpiPayback', 6)" :lg="L.sizeOf('kpiPayback', 6)">
        <BoardCard v-if="L.isOverride('kpiPayback')" :card="L.cardCfg('kpiPayback')" :data="L.dataOf('kpiPayback')" :error="L.errOf('kpiPayback')" />
        <div v-else class="kpi-card clickable" @click="goAr()">
          <div class="kpi-label">{{ $tt("销售回款率") }} <span class="drill-arrow">›</span></div>
          <div class="kpi-value">{{ fmtRate(d.paybackRate) }}</div>
          <div class="kpi-foot" :class="{ 'warn-text': nvl(d.arOverdue) > 0 }">逾期应收 {{ fmt(d.arOverdue) }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 运营层 -->
    <el-row :gutter="12" class="kpi-row">
      <el-col v-if="canSee('/crm/opportunity') && L.show('kpiPipeline')" :xs="L.xsOf('kpiPipeline', 8)" :sm="L.sizeOf('kpiPipeline', 8)" :lg="L.sizeOf('kpiPipeline', 8)">
        <BoardCard v-if="L.isOverride('kpiPipeline')" :card="L.cardCfg('kpiPipeline')" :data="L.dataOf('kpiPipeline')" :error="L.errOf('kpiPipeline')" />
        <div v-else class="kpi-card clickable" @click="go('/crm/opportunity', { isClosed: '0' })">
          <div class="kpi-label">{{ $tt("商机储备（在谈）") }} <span class="drill-arrow">›</span></div>
          <div class="kpi-value">{{ fmt(d.pipelineAmount) }}</div>
          <div class="kpi-foot">未关闭商机 {{ fmt(d.pipelineCount) }} 条</div>
        </div>
      </el-col>
      <el-col v-if="canSee('/finance/operation-expense') && L.show('kpiExpense')" :xs="L.xsOf('kpiExpense', 8)" :sm="L.sizeOf('kpiExpense', 8)" :lg="L.sizeOf('kpiExpense', 8)">
        <BoardCard v-if="L.isOverride('kpiExpense')" :card="L.cardCfg('kpiExpense')" :data="L.dataOf('kpiExpense')" :error="L.errOf('kpiExpense')" />
        <div v-else class="kpi-card clickable" @click="go('/finance/operation-expense', { expenseType: '销售费用' })">
          <div class="kpi-label">{{ $tt("销售费用率") }} <span class="drill-arrow">›</span></div>
          <div class="kpi-value sm">{{ fmtRate(d.saleExpenseRate) }}</div>
          <div class="kpi-foot">{{ $tt("销售费用 / 营收（不含税）") }}</div>
        </div>
      </el-col>
      <el-col v-if="L.show('kpiNewCustomer')" :xs="L.xsOf('kpiNewCustomer', 8)" :sm="L.sizeOf('kpiNewCustomer', 8)" :lg="L.sizeOf('kpiNewCustomer', 8)">
        <BoardCard v-if="L.isOverride('kpiNewCustomer')" :card="L.cardCfg('kpiNewCustomer')" :data="L.dataOf('kpiNewCustomer')" :error="L.errOf('kpiNewCustomer')" />
        <div v-else class="kpi-card clickable" @click="go('/basedata/customer', customerCreatedQuery())">
          <div class="kpi-label">{{ $tt("新客户贡献") }} <span class="drill-arrow">›</span></div>
          <div class="kpi-value sm">{{ fmt(newOldAmount('新客户')) }}</div>
          <div class="kpi-foot">占比 {{ fmtRate(newCustomerRate) }} / 老客户 {{ fmt(newOldAmount('老客户')) }}（{{ d.periodLabel }}口径）</div>
        </div>
      </el-col>
    </el-row>

    <!-- 图表区 -->
    <el-row :gutter="12">
      <el-col v-if="L.show('chartTrend')" :xs="L.xsOf('chartTrend', 12)" :lg="L.sizeOf('chartTrend', 12)">
        <BoardCard v-if="L.isOverride('chartTrend')" :card="L.cardCfg('chartTrend')" :data="L.dataOf('chartTrend')" :error="L.errOf('chartTrend')" />
        <el-card v-else shadow="never" class="chart-card">
          <template #header>{{ $tt("销售趋势（近12月，点柱/线看当月订单）") }}</template>
          <div ref="trendRef" class="chart" />
        </el-card>
      </el-col>
      <el-col v-if="L.show('chartLine')" :xs="L.xsOf('chartLine', 12)" :lg="L.sizeOf('chartLine', 12)">
        <BoardCard v-if="L.isOverride('chartLine')" :card="L.cardCfg('chartLine')" :data="L.dataOf('chartLine')" :error="L.errOf('chartLine')" />
        <el-card v-else shadow="never" class="chart-card">
          <template #header>{{ $tt("各产品线销售额分布（点扇区看明细）") }}</template>
          <div ref="lineRef" class="chart" />
        </el-card>
      </el-col>
      <el-col v-if="L.show('chartLevel')" :xs="L.xsOf('chartLevel', 12)" :lg="L.sizeOf('chartLevel', 12)">
        <BoardCard v-if="L.isOverride('chartLevel')" :card="L.cardCfg('chartLevel')" :data="L.dataOf('chartLevel')" :error="L.errOf('chartLevel')" />
        <el-card v-else shadow="never" class="chart-card">
          <template #header>{{ $tt("各客户等级销售额（点扇区看明细）") }}</template>
          <div ref="levelRef" class="chart" />
        </el-card>
      </el-col>
      <el-col v-if="L.show('chartRegion')" :xs="L.xsOf('chartRegion', 12)" :lg="L.sizeOf('chartRegion', 12)">
        <BoardCard v-if="L.isOverride('chartRegion')" :card="L.cardCfg('chartRegion')" :data="L.dataOf('chartRegion')" :error="L.errOf('chartRegion')" />
        <el-card v-else shadow="never" class="chart-card">
          <template #header>{{ $tt("各区域销售额分布（点扇区看客户）") }}</template>
          <div ref="regionRef" class="chart" />
        </el-card>
      </el-col>
      <el-col v-if="L.show('rankTop')" :xs="L.xsOf('rankTop', 12)" :lg="L.sizeOf('rankTop', 12)">
        <BoardCard v-if="L.isOverride('rankTop')" :card="L.cardCfg('rankTop')" :data="L.dataOf('rankTop')" :error="L.errOf('rankTop')" />
        <el-card v-else shadow="never" class="chart-card">
          <template #header>
            <div class="rank-head">
              <span class="rank-title">{{ $tt("热销产品") }} TOP{{ rankCfg.count }}</span>
              <el-popover v-model:visible="rankCfgVisibleTop" placement="bottom-end" :width="380" trigger="click">
                <template #reference>
                  <button type="button" class="rank-gear" :title="$tt('展示设置')">
                    <el-icon><Setting /></el-icon>
                  </button>
                </template>
                <div class="rank-cfg">
                  <div class="cfg-head">
                    <span class="cfg-title">{{ $tt("展示设置") }}</span>
                    <span class="cfg-sub">{{ $tt("热销与滞销共用一份，按登录人 + 公司记住") }}</span>
                  </div>
                  <div class="cfg-line">
                    <span class="cfg-key">{{ $tt("显示数量") }}</span>
                    <button v-for="n in RANK_COUNT_OPTIONS" :key="n" type="button" class="cfg-pill"
                            :class="{ on: rankCfg.count === n }" @click="setRankCount(n)">{{ n }}</button>
                  </div>
                  <div class="cfg-line wrap">
                    <span class="cfg-key">{{ $tt("展示字段") }}</span>
                    <button v-for="f in rankFieldOptions" :key="f.key" type="button" class="cfg-chip"
                            :class="{ on: isRankFieldOn(f.key) }" @click="toggleRankField(f.key)">
                      <span class="cfg-box" />{{ $tt(f.label) }}
                    </button>
                  </div>
                </div>
              </el-popover>
            </div>
          </template>
          <div class="rank-note">{{ $tt("点柱子看明细") }}</div>
          <div ref="topRef" class="chart" :style="{ height: rankChartHeight }" />
        </el-card>
      </el-col>
      <el-col v-if="L.show('rankBottom')" :xs="L.xsOf('rankBottom', 12)" :lg="L.sizeOf('rankBottom', 12)">
        <BoardCard v-if="L.isOverride('rankBottom')" :card="L.cardCfg('rankBottom')" :data="L.dataOf('rankBottom')" :error="L.errOf('rankBottom')" />
        <el-card v-else shadow="never" class="chart-card">
          <template #header>
            <div class="rank-head">
              <span class="rank-title">{{ $tt("滞销产品") }} BOTTOM{{ rankCfg.count }}</span>
              <el-popover v-model:visible="rankCfgVisibleBottom" placement="bottom-end" :width="380" trigger="click">
                <template #reference>
                  <button type="button" class="rank-gear" :title="$tt('展示设置')">
                    <el-icon><Setting /></el-icon>
                  </button>
                </template>
                <div class="rank-cfg">
                  <div class="cfg-head">
                    <span class="cfg-title">{{ $tt("展示设置") }}</span>
                    <span class="cfg-sub">{{ $tt("热销与滞销共用一份，按登录人 + 公司记住") }}</span>
                  </div>
                  <div class="cfg-line">
                    <span class="cfg-key">{{ $tt("显示数量") }}</span>
                    <button v-for="n in RANK_COUNT_OPTIONS" :key="n" type="button" class="cfg-pill"
                            :class="{ on: rankCfg.count === n }" @click="setRankCount(n)">{{ n }}</button>
                  </div>
                  <div class="cfg-line wrap">
                    <span class="cfg-key">{{ $tt("展示字段") }}</span>
                    <button v-for="f in rankFieldOptions" :key="f.key" type="button" class="cfg-chip"
                            :class="{ on: isRankFieldOn(f.key) }" @click="toggleRankField(f.key)">
                      <span class="cfg-box" />{{ $tt(f.label) }}
                    </button>
                  </div>
                </div>
              </el-popover>
            </div>
          </template>
          <div class="rank-note">{{ $tt("有销售记录中金额最低") }}</div>
          <div ref="bottomRef" class="chart" :style="{ height: rankChartHeight }" />
        </el-card>
      </el-col>
      <el-col v-if="L.show('tableOverdue')" :xs="L.xsOf('tableOverdue', 12)" :lg="L.sizeOf('tableOverdue', 12)">
        <BoardCard v-if="L.isOverride('tableOverdue')" :card="L.cardCfg('tableOverdue')" :data="L.dataOf('tableOverdue')" :error="L.errOf('tableOverdue')" />
        <el-card v-else shadow="never" class="chart-card">
          <template #header>
            <div style="display:flex;align-items:center;justify-content:space-between;">
              <span>
                逾期应收账款（按客户 TOP10）
                <el-tag v-if="nvl(d.arOverdue) > 0" type="danger" size="small" style="margin-left:8px">逾期 {{ fmt(d.arOverdue) }}</el-tag>
              </span>
              <el-tooltip :content="$tt('列设置')" placement="top">
                <el-button :icon="Setting" circle size="small" @click="openColumnSetting" />
              </el-tooltip>
            </div>
          </template>
          <el-table ref="tableRef" :data="d.overdueByCustomer || []" size="small" border max-height="320" :empty-text="$tt('暂无逾期应收')" :key="configVersion" @header-dragend="onHeaderDragend">
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
                  <span v-if="col.type === 'money'" class="warn-text">{{ fmt(row.overdueAmount) }}</span>
                  <template v-else-if="col.type === 'number'">{{ fmt(row[col.prop]) }}</template>
                </template>
              </el-table-column>
            </template>
            <!-- 操作列保持静态（位于表格末尾，不入列设置） -->
            <el-table-column :label="$tf('操作')" width="90" align="center">
              <template #default="{ row }">
                <el-button link type="primary" size="small" v-permission="perm('view')" @click="goAr(true)">{{ $tt("查看") }}</el-button>
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
    <ColumnSettingDrawer v-model="columnSettingVisible" :page-key="'SaleBoard'" :columns="columns" />

    <!-- 看板设置：显隐 / 宽度 / 顺序 / 换数据源与公式 / 新增卡片 -->
    <BoardLayoutSetting v-model="settingVisible" :layout="L" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh, Setting } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import echarts from '@/utils/echartsCore'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { getSaleBoard } from '@/api/dashboard/dataBoard'
import { usePagePerm } from '@/utils/pagePerm'
import { useUserStore } from '@/stores/user'
import { isRouteVisible } from '@/utils/versionGate'
import { scopedGetJSON, scopedSetJSON } from '@/utils/userScopedStorage'
import BoardCard from '@/components/board/BoardCard.vue'
import BoardLayoutSetting from '@/components/board/BoardLayoutSetting.vue'
import { useBoardLayout } from '@/composables/useBoardLayout'

const perm = usePagePerm('databoard:sale')
const userStore = useUserStore()
// 版本隔离（2026-10-10）：卡片下钻目标不属于当前账号可见菜单 → 整张卡不显示（顺带避免空数据卡）
const canSee = (path) => isRouteVisible(path, userStore.menuRoutePaths || [])

/** 本页原有区块清单：看板设置里可逐块显隐 / 调宽 / 换自己的数据源与公式 */
const BUILTINS = [
  { id: 'kpiSale', title: '销售额', size: 6 },
  { id: 'kpiTarget', title: '销售目标完成率', size: 6 },
  { id: 'kpiOrder', title: '订单量 / 平均客单价', size: 6 },
  { id: 'kpiPayback', title: '销售回款率', size: 6 },
  { id: 'kpiPipeline', title: '商机储备', size: 8 },
  { id: 'kpiExpense', title: '销售费用率', size: 8 },
  { id: 'kpiNewCustomer', title: '新客户贡献', size: 8 },
  { id: 'chartTrend', title: '销售趋势', size: 12 },
  { id: 'chartLine', title: '各产品线销售额分布', size: 12 },
  { id: 'chartLevel', title: '各客户等级销售额', size: 12 },
  { id: 'chartRegion', title: '各区域销售额分布', size: 12 },
  { id: 'rankTop', title: '热销产品', size: 12 },
  { id: 'rankBottom', title: '滞销产品', size: 12 },
  { id: 'tableOverdue', title: '逾期应收账款', size: 12 }
]
const L = useBoardLayout('sale', BUILTINS)
const settingVisible = ref(false)

const router = useRouter()
const period = ref('month')
const autoRefresh = ref(true)
const d = ref({})
// 列定义（列设置 + 动态渲染用）：type 标记单元格渲染分支；操作列保持静态（位于表格末尾，不入列设置）
const columns = [
  { prop: 'customerName', label: '客户', minWidth: 140, tooltip: true },
  { prop: 'overdueAmount', label: '逾期金额', width: 130, align: 'right', type: 'money' },
  { prop: 'overdueDays', label: '最长逾期天数', width: 110, align: 'right', type: 'number' }
]
const { columnSettingVisible, openColumnSetting, isColVisible, getColFixed, configVersion, sortedColumns, onHeaderDragend, applySavedWidths } = useColumnSetting('SaleBoard', columns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))
// 列宽持久化：表格带 border 才允许拖拽，拖完的宽度写入本页列配置，下次打开自动套用
const tableRef = ref(null)
const trendRef = ref()
const lineRef = ref()
const levelRef = ref()
const regionRef = ref()
const topRef = ref()
const bottomRef = ref()
const charts = []
let timer = null

// ===== 产品排行图（热销 / 滞销）展示设置：显示数量 + 展示字段，热销与滞销共用一份 =====
const RANK_COUNT_OPTIONS = [10, 15, 20, 30]
const RANK_CFG_KEY = 'saleBoardRankCfg'
const RANK_FIELD_FALLBACK = [
  { key: 'materialName', label: '物料名称', custom: false },
  { key: 'materialCode', label: '物料编码', custom: false },
  { key: 'model', label: '规格型号', custom: false },
  { key: 'categoryName', label: '物料分类', custom: false },
  { key: 'baseUnitName', label: '基本单位', custom: false }
]
/** 用户是否手动改过展示设置：没改过才自动勾上「颜色」这类自定义字段（与设计图默认态一致） */
let rankCfgTouched = false
const rankCfg = ref(loadRankCfg())
const rankCfgVisibleTop = ref(false)
const rankCfgVisibleBottom = ref(false)
// 展示字段候选清单：后端返回固定字段 + 物料档案已启用的自定义字段
const rankFieldOptions = computed(() => {
  const opts = d.value.materialFieldOptions
  return Array.isArray(opts) && opts.length ? opts : RANK_FIELD_FALLBACK
})
// 图高随条数走：每行 26px，未满 6 行按 6 行撑住，两张图取较高的一边保持对齐
const rankChartHeight = computed(() => {
  const n = Math.max(6,
    Math.min(rankCfg.value.count, (d.value.topMaterials || []).length),
    Math.min(rankCfg.value.count, (d.value.bottomMaterials || []).length))
  return `${n * 26 + 52}px`
})

function loadRankCfg() {
  const saved = scopedGetJSON(RANK_CFG_KEY, null)
  const fallback = { count: 20, fields: ['materialName', 'model'] }
  if (!saved || typeof saved !== 'object') return fallback
  const count = RANK_COUNT_OPTIONS.includes(Number(saved.count)) ? Number(saved.count) : fallback.count
  const fields = Array.isArray(saved.fields)
    ? saved.fields.filter(k => typeof k === 'string' && k)
    : []
  rankCfgTouched = true
  return { count, fields: fields.length ? fields : fallback.fields }
}
function saveRankCfg() {
  rankCfgTouched = true
  scopedSetJSON(RANK_CFG_KEY, { count: rankCfg.value.count, fields: [...rankCfg.value.fields] })
}
const isRankFieldOn = key => rankCfg.value.fields.includes(key)
function setRankCount(n) {
  if (rankCfg.value.count === n) return
  rankCfg.value.count = n
  saveRankCfg()
  nextTick(renderCharts)
}
function toggleRankField(key) {
  const fields = rankCfg.value.fields
  const idx = fields.indexOf(key)
  if (idx >= 0) {
    if (fields.length === 1) { ElMessage.warning('至少保留一个展示字段'); return }
    fields.splice(idx, 1)
  } else {
    fields.push(key)
  }
  saveRankCfg()
  nextTick(renderCharts)
}
/** 数据回来后校准展示字段：去掉后端已不存在的字段；从未配置过则默认勾上「颜色」自定义字段 */
function normalizeRankFields() {
  const opts = d.value.materialFieldOptions
  if (!Array.isArray(opts) || !opts.length) return
  const valid = new Set(opts.map(o => o.key))
  const kept = rankCfg.value.fields.filter(k => valid.has(k))
  let changed = kept.length !== rankCfg.value.fields.length
  rankCfg.value.fields = kept.length ? kept : ['materialName']
  if (!rankCfgTouched) {
    const color = opts.find(o => o.custom && String(o.label || '').includes('颜色'))
    if (color && !rankCfg.value.fields.includes(color.key)) {
      rankCfg.value.fields.push(color.key)
      changed = true
    }
    rankCfgTouched = true
  }
  if (changed) scopedSetJSON(RANK_CFG_KEY, { count: rankCfg.value.count, fields: [...rankCfg.value.fields] })
}

const nvl = v => (v == null ? 0 : Number(v))
const fmt = v => (v == null || v === '' ? '' : Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 2 }))
const fmtRate = v => (v == null || v === '' ? '' : Number(v).toFixed(1) + '%')
const go = (p, query) => router.push(query ? { path: p, query } : p)
const goAr = (overdueOnly) => go('/finance/report/ar-summary', overdueOnly ? { overdueOnly: '1' } : {})

const newOldAmount = k => {
  const row = (d.value.newOldCustomer || []).find(x => x.name === k)
  return row ? row.amount : ''
}
const newCustomerRate = computed(() => {
  const list = d.value.newOldCustomer || []
  const total = list.reduce((s, x) => s + nvl(x.amount), 0)
  if (total <= 0) return ''
  const nw = nvl((list.find(x => x.name === '新客户') || {}).amount)
  return nw * 100 / total
})

/** 当前时间维度的起止日期（YYYY-MM-DD，含端点），用于穿透到明细列表 */
function periodRange() {
  const now = new Date()
  const p2 = n => String(n).padStart(2, '0')
  const f = dt => `${dt.getFullYear()}-${p2(dt.getMonth() + 1)}-${p2(dt.getDate())}`
  let start = new Date(now), end = new Date(now)
  if (period.value === 'week') {
    const dow = (now.getDay() + 6) % 7 // 周一=0
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

/** 穿透到销售订单列表（带当前时间范围） */
function drillOrders(extra) {
  go('/sale/orders', { status: '已生效', ...periodRange(), ...(extra || {}) })
}
/** 穿透到客户档案：建档时间落在当前期间 = 新客户 */
function customerCreatedQuery() {
  const r = periodRange()
  return { createdFrom: r.startDate, createdTo: r.endDate }
}

async function loadData() {
  // 注意：request 响应拦截器已把 {code,message,data} 解包为 data，这里直接就是业务数据
  const res = await getSaleBoard(period.value)
  d.value = res || {}
  normalizeRankFields()
  await nextTick()
  renderCharts()
  // 数据到位后再套一次已保存的列宽（表格重建后列对象是新的）
  nextTick(() => applySavedWidths(tableRef))
  // 本页被改成自定义取数的区块与新增卡片，跟着时间维度一起刷新
  L.refreshData(period.value)
}

/** 排行图标签：按用户勾选的展示字段拼「名称 · 颜色 · 规格」，空值不显示、重复值去重 */
function rankLabel(row) {
  const picked = rankFieldOptions.value.filter(f => rankCfg.value.fields.includes(f.key))
  const parts = []
  for (const f of picked) {
    const v = row && row.fields ? row.fields[f.key] : null
    const text = v == null ? '' : String(v).trim()
    if (text && !parts.includes(text)) parts.push(text)
  }
  if (!parts.length) return row && row.materialName ? row.materialName : '未知物料'
  return parts.join(' · ')
}
/** 金额轴刻度：上万走「万」，避免 12,800 这种长刻度把图挤满 */
function rankAxisFmt(v) {
  const n = Number(v) || 0
  if (Math.abs(n) >= 10000) return (n / 10000).toFixed(n % 10000 === 0 ? 0 : 1) + '万'
  return String(n)
}
/**
 * 产品排行横向条形图（热销 / 滞销共用）：
 * - 横向条形 + 金额刻度轴：柱长按金额真实比例，同图内可直接比长短，不只是看数字
 * - 左侧标签取用户勾选的展示字段，超长省略，悬浮看全量
 */
function renderRankChart(el, list, color) {
  if (!el) return
  const rows = (list || []).slice(0, rankCfg.value.count)
  const c = echarts.init(el)
  const labelWidth = Math.max(110, Math.min(230, Math.round(el.clientWidth * 0.42)))
  const labels = rows.map(rankLabel)
  c.setOption({
    animationDuration: 180,
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params) => {
        const p = Array.isArray(params) ? params[0] : params
        const row = rows[p.dataIndex]
        if (!row) return ''
        const picked = rankFieldOptions.value.filter(f => rankCfg.value.fields.includes(f.key))
        const lines = picked
          .map(f => {
            const v = row.fields ? row.fields[f.key] : null
            return v == null || String(v).trim() === '' ? '' : `${f.label}：${v}`
          })
          .filter(Boolean)
        lines.push(`销售金额：${fmt(row.amount)}`)
        lines.push(`销售数量：${fmt(row.quantity)}`)
        return lines.map(t => `<div style="line-height:20px">${t}</div>`).join('')
      }
    },
    grid: { left: 4, right: 68, top: 6, bottom: 22, containLabel: true },
    xAxis: {
      type: 'value',
      splitNumber: 4,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { fontSize: 11, color: '#909399', formatter: rankAxisFmt },
      splitLine: { lineStyle: { color: '#f0f0f0' } }
    },
    yAxis: {
      type: 'category',
      inverse: true,
      data: labels,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { fontSize: 11, color: '#606266', width: labelWidth, overflow: 'truncate', margin: 8 }
    },
    series: [{
      type: 'bar',
      data: rows.map(r => nvl(r.amount)),
      barMaxWidth: 14,
      showBackground: true,
      backgroundStyle: { color: '#f5f7fa', borderRadius: 7 },
      itemStyle: { color, borderRadius: [0, 7, 7, 0] },
      label: { show: true, position: 'right', fontSize: 11, color: '#909399', formatter: p => fmt(p.value) }
    }]
  })
  c.on('click', p => drillMaterial(rows[p.dataIndex]))
  charts.push(c)
}

function renderCharts() {
  disposeCharts()
  const data = d.value
  if (trendRef.value) {
    const c = echarts.init(trendRef.value)
    const t = data.saleTrend || []
    c.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['销售额', '订单量'] },
      grid: { left: 60, right: 50, top: 40, bottom: 30 },
      xAxis: { type: 'category', data: t.map(x => x.month) },
      yAxis: [{ type: 'value' }, { type: 'value', name: '订单量', minInterval: 1 }],
      series: [
        { name: '销售额', type: 'line', smooth: true, data: t.map(x => nvl(x.amount)) },
        { name: '订单量', type: 'bar', yAxisIndex: 1, barWidth: 12, data: t.map(x => nvl(x.orderCount)) }
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
  pie(lineRef, data.productLineDist, name => drillProductLine(name))
  pie(levelRef, data.customerLevelDist, name => drillOrders({ customerLevel: name }))
  pie(regionRef, data.regionDist, name => go('/basedata/customer', { region: name }))
  renderRankChart(topRef.value, data.topMaterials, '#409EFF')
  renderRankChart(bottomRef.value, data.bottomMaterials, '#E6A23C')
}

/** 产品线名 → 穿透（按分类名无法直接匹配ID时，退化为订单列表全量+时间范围） */
function drillProductLine(lineName) {
  const cats = d.value.productLineRef || []
  const hit = cats.find(x => x.name === lineName)
  drillOrders(hit && hit.categoryId ? { productLineId: String(hit.categoryId) } : {})
}
function drillMaterial(row) {
  if (!row) return
  drillOrders(row.materialId ? { materialId: String(row.materialId) } : {})
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
/* 产品排行图（热销 / 滞销）：标题 + 展示设置 + 口径说明 */
.rank-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.rank-title { font-size: 16px; font-weight: 500; color: #303133; }
.rank-gear { display: inline-flex; align-items: center; justify-content: center; width: 24px; height: 24px; padding: 0; border: 1px solid #dcdfe6; border-radius: 8px; background: #f5f7fa; color: #909399; cursor: pointer; }
.rank-gear:hover { color: #409eff; border-color: #c6e2ff; background: #ecf5ff; }
.rank-note { font-size: 12px; color: #909399; margin: -4px 0 8px; }
.rank-cfg { display: flex; flex-direction: column; gap: 10px; }
.cfg-head { display: flex; align-items: baseline; justify-content: space-between; gap: 8px; }
.cfg-title { font-size: 14px; font-weight: 500; color: #303133; }
.cfg-sub { font-size: 12px; color: #909399; }
.cfg-line { display: flex; align-items: center; gap: 8px; }
.cfg-line.wrap { flex-wrap: wrap; }
.cfg-key { font-size: 12px; color: #909399; min-width: 56px; }
.cfg-pill { font-size: 12px; color: #606266; border: 1px solid #dcdfe6; border-radius: 999px; padding: 2px 10px; background: #f5f7fa; cursor: pointer; }
.cfg-pill.on { color: #409eff; border-color: #409eff; background: #ecf5ff; }
.cfg-chip { display: inline-flex; align-items: center; gap: 4px; font-size: 12px; color: #303133; border: 1px solid #dcdfe6; border-radius: 8px; padding: 3px 8px; background: #f5f7fa; cursor: pointer; }
.cfg-chip.on { color: #409eff; border-color: #409eff; background: #ecf5ff; }
.cfg-box { width: 12px; height: 12px; border: 1px solid #dcdfe6; border-radius: 3px; background: #fff; position: relative; flex: none; }
.cfg-chip.on .cfg-box { border-color: #409eff; background: #409eff; }
.cfg-chip.on .cfg-box::after { content: ''; position: absolute; left: 3px; top: 0; width: 4px; height: 7px; border-right: 1.5px solid #fff; border-bottom: 1.5px solid #fff; transform: rotate(40deg); }
</style>
