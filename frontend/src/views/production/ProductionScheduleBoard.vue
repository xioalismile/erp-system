<template>
  <div class="board-page">
    <!-- 页头 -->
    <div class="page-header">
      <div class="header-left">
        <div class="back-btn" @click="$router.push('/production')">
          <el-icon><ArrowLeft /></el-icon>
          <span>{{ $tt("返回") }}</span>
        </div>
        <div class="title-group">
          <div class="page-title">
            {{ $tt("车间排程看板") }}
            <span class="title-badge">APS BOARD</span>
          </div>
          <div class="page-sub">{{ $tt("按工作中心分泳道，逐日负载热力；块 = 某工单某道工序占用该中心的时段，未派工时标「未派」") }}</div>
        </div>
      </div>
      <div class="header-actions">
        <el-button :icon="Setting" v-permission="perm('columnSetting')" @click="stationSettingVisible = true">{{ $tt("站点设置") }}</el-button>
        <el-button :icon="Refresh" v-permission="perm('refresh')" :loading="loading" @click="loadData">{{ $tt("刷新") }}</el-button>
      </div>
    </div>

    <!-- HUD 顶栏 -->
    <div class="hud">
      <el-select
        v-model="workCenterId"
        clearable
        filterable
        class="wc-select"
        :placeholder="$tt('按工作中心筛选')"
        @change="loadData"
      >
        <el-option v-for="wc in workCenters" :key="wc.workCenterId" :label="wc.workCenterName" :value="wc.workCenterId" />
      </el-select>

      <span class="hud-label">{{ $tt("日期") }}</span>
      <el-radio-group v-model="dateRange" size="small" data-hud="daterange" @change="onDateRangeChange">
        <el-radio-button :value="7">{{ $tt("7天") }}</el-radio-button>
        <el-radio-button :value="14">{{ $tt("14天") }}</el-radio-button>
        <el-radio-button :value="30">{{ $tt("30天") }}</el-radio-button>
      </el-radio-group>

      <span class="hud-label">{{ $tt("缩放") }}</span>
      <el-radio-group v-model="zoom" size="small" data-hud="zoom">
        <el-radio-button value="day">{{ $tt("日") }}</el-radio-button>
        <el-radio-button value="week" disabled>{{ $tt("周") }}</el-radio-button>
      </el-radio-group>

      <span class="hud-label">{{ $tt("列宽") }}</span>
      <el-radio-group :model-value="columnPreset" size="small" data-hud="colwidth" @change="onPresetChange">
        <el-radio-button value="compact">{{ $tt("紧凑") }}</el-radio-button>
        <el-radio-button value="standard">{{ $tt("标准") }}</el-radio-button>
        <el-radio-button value="loose">{{ $tt("宽松") }}</el-radio-button>
        <el-radio-button v-if="columnPreset === 'custom'" value="custom" disabled>{{ $tt("自定义") }}</el-radio-button>
      </el-radio-group>

      <span class="hud-label">{{ $tt("派工") }}</span>
      <el-radio-group v-model="assigneeFilter" size="small" data-hud="assignee" @change="loadData">
        <el-radio-button value="all">{{ $tt("全部") }}</el-radio-button>
        <el-radio-button value="assigned">{{ $tt("已派工") }}</el-radio-button>
        <el-radio-button value="unassigned">{{ $tt("未派工") }}</el-radio-button>
      </el-radio-group>

      <el-checkbox v-model="includeFinished" @change="loadData">{{ $tt("含已完工/已取消") }}</el-checkbox>

      <div class="hud-metrics">
        <span class="metric" data-metric="orderCount">{{ $tt("在排工单") }} <b>{{ metrics.orderCount || 0 }}</b></span>
        <span class="metric" :class="{ warn: num(metrics.overloadedCenters) > 0 }" data-metric="overloadedCenters">{{ $tt("超载中心") }} <b>{{ metrics.overloadedCenters || 0 }}</b></span>
        <span class="metric" :class="{ unassigned: num(metrics.unassignedCount) > 0 }" data-metric="unassignedCount">{{ $tt("未派工") }} <b>{{ metrics.unassignedCount || 0 }}</b></span>
        <span class="metric" data-metric="avgLoadPercent">{{ $tt("平均负载") }} <b>{{ metrics.avgLoadPercent || 0 }}%</b></span>
      </div>

      <div class="hud-actions">
        <el-switch v-model="autoReschedule" :active-text="$tt('自动重排')" data-hud="auto-reschedule" />
        <el-button
          v-if="autoReschedule"
          type="primary"
          size="small"
          :loading="applying"
          data-act="apply-reschedule"
          @click="onApplyReschedule"
        >{{ $tt("应用重排") }}</el-button>
      </div>
    </div>

    <!-- 告警条 -->
    <div v-if="alertText" class="alert-bar" data-alert="1">
      <el-icon class="ab-icon"><WarningFilled /></el-icon>
      <span class="ab-text" :title="alertText">{{ alertText }}</span>
    </div>

    <!-- 车间排程看板：泳道 + 负载热力 + 任务块 -->
    <div v-loading="loading" class="aps-wrap" :style="{ '--col-w': colW + 'px' }">
    <div class="aps-body">
      <div class="aps-main">
      <!-- 吸顶日期轴（页面纵向滚动时钉在容器顶部；与线路体横向滚动同步，不错位） -->
      <div v-if="packedLanes.length" class="aps-sticky" data-aps-head="1">
        <div class="aps-head-clip">
          <div class="aps-head-track" :style="{ transform: 'translateX(' + (-scrollLeft) + 'px)' }">
            <div class="aps-head-corner" data-head-corner="1">
              {{ $tt("共") }} {{ packedLanes.length }} {{ $tt("条泳道") }} · {{ totalBlocks }} {{ $tt("道工序") }}
            </div>
            <div
              v-for="d in dayCells"
              :key="d.key"
              class="aps-head-cell"
              :class="{ 'is-today': d.isToday, 'is-weekend': d.weekend }"
              :data-day-index="d.index"
            >{{ d.label }}</div>
            <!-- 日期列边界拖拽手柄：鼠标移上变左右箭头（col-resize），按住左右拖动改列宽；双击恢复默认 104px -->
            <div
              v-for="d in dayCells"
              :key="'rz-' + d.key"
              class="aps-col-resizer"
              :data-col-resizer="d.index"
              :style="{ left: (LANE_W + (d.index + 1) * colW - 4) + 'px' }"
              @mousedown="onResizeDown($event)"
              @dblclick="onResizeReset"
            ></div>
          </div>
        </div>
      </div>

      <!-- 横滚主体：整页纵向滚动，左栏不冻结（横向滚动时跟随一起走） -->
      <div v-if="packedLanes.length" ref="scrollEl" class="aps-scroll" data-aps-scroll="1" @scroll="onScroll">
        <div class="aps-grid" :style="{ width: gridWidth + 'px' }">
          <div
            v-for="lane in packedLanes"
            :key="lane.key"
            class="aps-lane"
            :data-lane="lane.workCenterName"
            :data-lane-id="lane.workCenterId"
          >
            <!-- 左栏 168px：工作中心名 + 日产能/天 · 负载% -->
            <div class="aps-lane-label" data-lane-label="1" :style="{ borderLeftColor: lane.overloaded ? '#ef4444' : '#6366f1' }">
              <div class="lane-wc" :title="lane.workCenterName">{{ lane.workCenterName }}</div>
              <div class="lane-cap" :title="capText(lane)">
                <span class="mono">{{ num(lane.capacityPerDay) }}</span>{{ lane.capacityUnit || 'h' }}{{ $tt("/天") }}
                <span class="cap-sep">·</span>{{ $tt("负载") }}
                <span class="mono" :class="{ 'cap-warn': lane.overloaded }">{{ num(lane.loadPercent) }}%</span>
              </div>
              <!-- 建议效率系数（来自工时误差分析，仅出建议、不自动回填） -->
              <div v-if="hasSuggestion(lane)" class="lane-suggest">
                <div class="sug-main" :title="suggestText(lane)">{{ suggestText(lane) }}</div>
                <div class="sug-note" :title="$tt('建议值来自工时误差分析，确认后请自行填到工作中心「效率」里')">{{ $tt("建议值来自工时误差分析，确认后请自行填到工作中心「效率」里") }}</div>
              </div>
              <div v-if="lane.capacityMissing" class="lane-miss">{{ $tt("产能未维护") }}</div>
            </div>

            <!-- 时间轴 -->
            <div
              class="aps-track"
              :data-track="lane.workCenterName"
              :style="{ height: lane.trackHeight + 'px', width: gridWidth - LANE_W + 'px' }"
            >
              <!-- 负载热力（每天一格） -->
              <div class="aps-heat">
                <div
                  v-for="(d, di) in lane.days"
                  :key="di"
                  class="heat-cell"
                  :class="{ 'is-over': d.overloaded === true }"
                  :style="heatStyle(d)"
                  :data-heat="d.overloaded === true ? 'over' : (num(d.loadPercent) > 0 ? 'on' : 'off')"
                ></div>
              </div>

              <!-- 今天高亮竖线（青色光柱） -->
              <div
                v-if="todayIndex >= 0 && todayIndex < dayCount"
                class="today-line"
                data-today-line="1"
                :style="{ left: (todayIndex * colW) + 'px' }"
              ></div>

              <template v-for="b in lane._blocks" :key="b.operationRowId || (b.orderId + '-' + b.lineNo)">
                <el-tooltip placement="top" effect="dark" :show-after="150" popper-class="aps-tip-popper">
                  <template #content>
                    <div class="aps-tip">
                      <div class="aps-tip-h">
                        <span class="aps-tip-dot" :style="{ background: orderColor(b.orderId) }"></span>
                        <b>{{ b.operationName || $tt("未命名工序") }}</b>
                      </div>
                      <div class="aps-tip-r"><span>{{ $tt("工单号") }}</span><b>{{ b.orderNo || '' }}</b></div>
                      <div class="aps-tip-r"><span>{{ $tt("负责人") }}</span><b :class="{ 'warn': b.unassigned === true }">{{ b.unassigned === true ? $tt("未派") : (b.assigneeName || '') }}</b></div>
                      <div class="aps-tip-r"><span>{{ $tt("工序") }}</span><b>{{ $tt("第") }}{{ b.seqIndex }}{{ $tt("道") }} / {{ $tt("共") }}{{ b.seqTotal }}{{ $tt("道") }}</b></div>
                      <div class="aps-tip-r"><span>{{ $tt("计划开工") }}</span><b>{{ b.planStartDate || '' }}</b></div>
                      <div class="aps-tip-r"><span>{{ $tt("计划完工") }}</span><b>{{ b.planEndDate || '' }}</b></div>
                      <div class="aps-tip-r"><span>{{ $tt("占用") }}</span><b>{{ b.occupancyDays || 0 }} {{ $tt("天") }} · {{ b.hours || 0 }}h</b></div>
                      <div v-if="b.waitDays > 0" class="aps-tip-r"><span>{{ $tt("等料") }}</span><b>{{ b.waitDays }} {{ $tt("天") }}</b></div>
                      <div v-if="b.waitDays > 0 && b.kittingDate && b.kittingStatus !== 'EXPECTED'" class="aps-tip-r"><span>{{ $tt("预计齐套日") }}</span><b>{{ b.kittingDate }}</b></div>
                      <div v-if="b.kittingStatus" class="aps-tip-r"><span>{{ $tt("齐套") }}</span><b>{{ kittingText(b) }}</b></div>
                      <div v-if="b.overloaded === true" class="aps-tip-r"><span>{{ $tt("超载") }}</span><b class="warn">{{ b.overloadHours }}h</b></div>
                    </div>
                  </template>

                  <div
                    class="aps-block"
                    :class="{ 'is-unassigned': b.unassigned === true, 'is-over': b.overloaded === true }"
                    :style="blockStyleOf(b)"
                    :data-block="b.operationName"
                    :data-block-order="b.orderNo"
                    :data-unassigned="b.unassigned === true ? '1' : '0'"
                    @click="openPanel(b)"
                  >
                    <div class="blk-l1">{{ b.operationName }}</div>
                    <div class="blk-l2">
                      <span class="mono">{{ $tt("第") }}{{ b.seqIndex }}{{ $tt("道") }}</span>
                      <span class="blk-dot">·</span>
                      <span v-if="b.unassigned === true" class="blk-unassigned">{{ $tt("未派") }}</span>
                      <span v-else class="blk-assignee">{{ b.assigneeName || '' }}</span>
                    </div>
                    <!-- 块右上角角标区（同一套角标语言，互不重叠）：齐套绒绿小方块在左、等料天数数字在右；超载红三角贴最右上角时整组左移让位 -->
                    <span class="blk-corner" :class="{ 'is-over': b.overloaded === true }">
                      <span v-if="isKittingReady(b)" class="blk-kit-ico" data-kitting-ico="1"></span>
                      <span v-if="num(b.waitDays) > 0" class="blk-wait-ico" data-wait-ico="1">{{ num(b.waitDays) }}</span>
                    </span>
                    <span v-if="b.overloaded === true" class="blk-over-ico" data-over-ico="1"></span>
                  </div>
                </el-tooltip>
              </template>
            </div>
          </div>
        </div>
      </div>
      </div><!-- /aps-main -->

      <!-- 未排期：计划开工/完工日期都为空的工序统一收进右侧固定区（不占日期列、不计负载热力/排队/超载） -->
      <div v-if="unscheduledBlocks.length" class="aps-unsched" data-unsched="1">
        <div class="uns-head">
          <span class="uns-title">{{ $tt("未排期") }} <b class="mono" data-unsched-count="1">{{ unscheduledBlocks.length }}</b> {{ $tt("道") }}</span>
          <el-button size="small" :loading="applying" data-act="auto-schedule" @click="onAutoScheduleUnscheduled">{{ $tt("按产能自动排期") }}</el-button>
        </div>
        <div class="uns-list">
          <div
            v-for="(b, ui) in unscheduledBlocks"
            :key="b.operationRowId || (b.orderId + '-' + b.lineNo) || ui"
            class="uns-block"
            data-unsched-block="1"
            :title="unschedTip(b)"
            @click="openPanel(b)"
          >
            <span class="uns-name">{{ b.operationName }}</span>
            <span class="uns-dot">·</span>
            <span v-if="b.unassigned === true" class="uns-unassigned">{{ $tt("未派") }}</span>
            <span v-else class="uns-assignee">{{ b.assigneeName || '' }}</span>
          </div>
        </div>
      </div>
    </div><!-- /aps-body -->

      <el-empty v-if="!loading && !packedLanes.length && !unscheduledBlocks.length" class="board-empty" :description="emptyText" />

      <!-- 图例 -->
      <div v-if="packedLanes.length" class="aps-legend" data-aps-legend="1">
        <span v-for="lg in orderLegend" :key="lg.key" class="lg-item">
          <span class="lg-swatch" :style="{ background: lg.color }"></span>{{ lg.label }}
        </span>
        <span class="lg-item"><span class="lg-swatch lg-unassigned"></span>{{ $tt("未派（橙）") }}</span>
        <span class="lg-item"><span class="lg-swatch lg-over"></span>{{ $tt("超产能（红）") }}</span>
        <span class="lg-item"><span class="lg-wait-badge">3</span>{{ $tt("等料天数（块右上角数字）") }}</span>
        <span class="lg-item"><span class="lg-swatch lg-heat"></span>{{ $tt("负载热力（青色深浅）") }}</span>
      </div>
    </div>

    <!-- 站点设置：勾选显示/隐藏 + 拖动排序（按「登录人 + 所属公司」各自保存） -->
    <StationSettingPanel v-model="stationSettingVisible" :fields="stationFieldDefs" :page-key="stationPageKey" />

    <!-- 工序操作面板（点击任务块打开） -->
    <OperationPanel v-model="panelVisible" :operation="currentOp" @changed="onChanged" />
  </div>
</template>

<script setup>
/**
 * 车间排程看板（APS 泳道板）：一条泳道 = 一个工作中心；块 = 某工单某道工序占用该中心的时段。
 *
 * 2026-09-28 第 16 期：与「车间进度跟踪」拆成两个独立页面 —— 本页只保留 APS 排程看板，
 * 原工具条上的「车间排程看板 / 车间进度跟踪」视图切换与进度跟踪视图已移出到
 * views/production/ProductionProgressTracking.vue。本页其余展示与交互保持不变。
 *
 * 共用代码（状态色 / 单据跳转 / 工序操作面板打开态 / 站点字段 / 站点设置面板）抽到
 * composables/useOperationBoard.js、composables/useStationFields.js、
 * components/production/StationSettingPanel.vue，两个页面都从这里 import。
 */
import { ref, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Refresh, Setting, WarningFilled } from '@element-plus/icons-vue'
import { getScheduleBoard, applyReschedule } from '@/api/production/workOrderOperation'
import { listAllWorkCenter } from '@/api/production/workCenter'
import { useOperationBoard, useOperationPanel } from '@/composables/useOperationBoard'
import { useStationFields } from '@/composables/useStationFields'
import { useColumnConfigStore } from '@/stores/columnConfig'
import { usePagePerm } from '@/utils/pagePerm'
import OperationPanel from '@/components/production/OperationPanel.vue'
import StationSettingPanel from '@/components/production/StationSettingPanel.vue'
import { tt } from '@/i18n'

const perm = usePagePerm()
const loading = ref(false)
const applying = ref(false)

// ---------------- 尺寸硬预算（守则：左栏 168px、时间列宽三档） ----------------
const LANE_W = 168
const COLUMN_WIDTHS = { compact: 84, standard: 104, loose: 132 }
const ROW_H = 40
const TRACK_PAD_TOP = 14

// ---------------- 筛选 ----------------
const workCenterId = ref(null)
const includeFinished = ref(false)
/** 派工状态筛选：all(默认) / assigned / unassigned —— 当次查询条件，不持久化 */
const assigneeFilter = ref('all')
/** 日期轴跨度（仅排程看板）：7 / 14 / 30 天，默认 7 */
const dateRange = ref(7)
/** 缩放（本期只做「日」，「周」仅留入口、禁用） */
const zoom = ref('day')
/**
 * 时间列宽：三档预设（紧凑84 / 标准104 / 宽松132）作为快捷预设，
 * 另支持鼠标拖动列边界自由调宽（范围 56~360px，双击恢复默认 104px），
 * 拖好的宽度按「登录人 + 所属公司」隔离持久化（复用 columnConfig store）。
 */
const COL_W_KEY = 'ProductionScheduleBoardColWidth'
const COL_W_PROP = 'timeColumn'
const MIN_COL_W = 56
const MAX_COL_W = 360
const DEFAULT_COL_W = COLUMN_WIDTHS.standard
const columnStore = useColumnConfigStore()
const colW = ref(DEFAULT_COL_W)
/** 当前档位：命中某预设返回其 key，否则返回 custom（拖动后档位显示为「自定义」） */
const columnPreset = computed(() => Object.keys(COLUMN_WIDTHS).find(k => COLUMN_WIDTHS[k] === colW.value) || 'custom')
function clampColW (w) { return Math.max(MIN_COL_W, Math.min(MAX_COL_W, Math.round(w))) }
function persistColW (w) { columnStore.setColumnWidth(COL_W_KEY, COL_W_PROP, w) }
function loadColW () {
  const saved = Number(columnStore.getPageConfig(COL_W_KEY)?.[COL_W_PROP]?.width)
  if (Number.isFinite(saved) && saved >= MIN_COL_W && saved <= MAX_COL_W) colW.value = clampColW(saved)
}
/** 三档快捷预设：点一下直接设为 84/104/132 并落盘；与拖动互为同步 */
function onPresetChange (v) {
  if (!COLUMN_WIDTHS[v]) return
  colW.value = COLUMN_WIDTHS[v]
  persistColW(colW.value)
}
/* ---------------- 列边界鼠标拖动调宽（拖动期间不触发页面其它点击，松手才落盘） ---------------- */
let resizeState = null
function onResizeDown (e) {
  e.preventDefault()
  e.stopPropagation()
  resizeState = { startX: e.clientX, startW: colW.value }
  document.addEventListener('mousemove', onResizeMove)
  document.addEventListener('mouseup', onResizeUp)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}
function onResizeMove (e) {
  if (!resizeState) return
  colW.value = clampColW(resizeState.startW + (e.clientX - resizeState.startX))
}
function onResizeUp () {
  document.removeEventListener('mousemove', onResizeMove)
  document.removeEventListener('mouseup', onResizeUp)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  if (resizeState) persistColW(colW.value)
  resizeState = null
}
/** 双击列边界恢复默认列宽 104px */
function onResizeReset () {
  colW.value = DEFAULT_COL_W
  persistColW(colW.value)
}
/** 自动重排开关：默认关（关=只预警、不发任何写请求） */
const autoReschedule = ref(false)

// ---------------- 数据 ----------------
/** APS 排程看板数据：{ rangeStart, days, metrics, alerts, lanes } */
const scheduleData = ref({})
const workCenters = ref([])
const boardFailed = ref(false)

/** 线路标签滚动同步（日期轴与线路体横向滚动不错位） */
const scrollEl = ref(null)
const scrollLeft = ref(0)

// ---------------- 共用逻辑（两页同一份） ----------------
const { num } = useOperationBoard()
const stationSettingVisible = ref(false)
const { pageKey: stationPageKey, fieldDefs: stationFieldDefs, init: initStationFields } = useStationFields()
const { panelVisible, currentOp, openPanel, onChanged } = useOperationPanel(() => loadData())

// ---------------- 数值与文本工具 ----------------
function pad2 (n) { return String(n).padStart(2, '0') }
/** 日期字符串 → 当天 00:00 的时间戳（解析失败返回 null）；一律取整到日，避免时分/时区导致落列偏移一天 */
function msDay (s) {
  if (!s) return null
  const d = new Date(String(s).substring(0, 10) + 'T00:00:00')
  if (Number.isNaN(d.getTime())) return null
  d.setHours(0, 0, 0, 0)
  return d.getTime()
}
function todayStartMs () {
  const d = new Date()
  d.setHours(0, 0, 0, 0)
  return d.getTime()
}
function round2 (v) { return Math.round(num(v) * 100) / 100 }

// ---------------- APS 泳道：时间轴 / 热力 ----------------
const lanes = computed(() => (Array.isArray(scheduleData.value.lanes) ? scheduleData.value.lanes : []))
const alerts = computed(() => (Array.isArray(scheduleData.value.alerts) ? scheduleData.value.alerts : []))
const metrics = computed(() => scheduleData.value.metrics || {})
const dayCount = computed(() => num(scheduleData.value.days) > 0 ? num(scheduleData.value.days) : dateRange.value)
const rangeStartMs = computed(() => {
  const d = msDay(scheduleData.value.rangeStart)
  return d == null ? todayStartMs() : d
})
const todayIndex = computed(() => Math.round((todayStartMs() - rangeStartMs.value) / 86400000))
const gridWidth = computed(() => LANE_W + dayCount.value * colW.value)

const dayCells = computed(() => {
  const out = []
  for (let i = 0; i < dayCount.value; i++) {
    const d = new Date(rangeStartMs.value + i * 86400000)
    const md = pad2(d.getMonth() + 1) + '-' + pad2(d.getDate())
    const isToday = i === todayIndex.value
    out.push({
      key: 'd' + i,
      index: i,
      label: isToday ? (md + ' ' + tt('今天')) : md,
      isToday,
      weekend: d.getDay() === 0 || d.getDay() === 6
    })
  }
  return out
})

/** 日期字符串 → 距 rangeStart 的天数（无效返回 null） */
function dayIdx (dateStr) {
  const d = msDay(dateStr)
  if (d == null) return null
  return Math.round((d - rangeStartMs.value) / 86400000)
}

/** 负载热力底纹（深色科技风）：无占用=透明 → 青 rgba(53,230,255,.07/.15)；超载格 → 玫红 .17 + 顶部红线 */
function heatStyle (d) {
  if (d && d.overloaded === true) return { background: 'rgba(255,77,141,0.17)', boxShadow: 'inset 0 2px 0 #FF4D8D' }
  const p = num(d && d.loadPercent)
  if (!(p > 0)) return { background: 'transparent' }
  if (p < 50) return { background: 'rgba(53,230,255,0.07)' }
  return { background: 'rgba(53,230,255,0.15)' }
}

// 工单配色（深色科技风）：块用渐变 + 辉光；不同工单实测颜色不同
const ORDER_STYLES = [
  { from: '#8B6BFF', to: '#5A3DFF', glow: 'rgba(122,92,255,.55)', solid: '#6D53FF' },
  { from: '#FFB020', to: '#FF7A45', glow: 'rgba(255,176,32,.50)', solid: '#FF9520' },
  { from: '#35E6FF', to: '#0EA5E9', glow: 'rgba(53,230,255,.45)', solid: '#23C6F5' },
  { from: '#34D399', to: '#059669', glow: 'rgba(52,211,153,.45)', solid: '#10B981' },
  { from: '#FF6FA5', to: '#EC4899', glow: 'rgba(236,72,153,.45)', solid: '#F45B9B' },
  { from: '#FBBF24', to: '#F59E0B', glow: 'rgba(245,158,11,.42)', solid: '#F5B02B' },
  { from: '#60A5FA', to: '#2563EB', glow: 'rgba(96,165,250,.45)', solid: '#3B82F6' },
  { from: '#F87171', to: '#DC2626', glow: 'rgba(239,68,68,.42)', solid: '#EF4444' }
]
const orderStyleMap = computed(() => {
  const map = {}
  let i = 0
  for (const lane of lanes.value) {
    for (const b of (lane.blocks || [])) {
      const k = b.orderId == null ? 'na' : String(b.orderId)
      if (!(k in map)) { map[k] = ORDER_STYLES[i % ORDER_STYLES.length]; i++ }
    }
  }
  return map
})
function orderStyle (orderId) {
  return orderStyleMap.value[orderId == null ? 'na' : String(orderId)] || ORDER_STYLES[0]
}
function orderColor (orderId) {
  return orderStyle(orderId).solid
}

/** 块起始列（无计划开工时按 计划完工 − 占用天数 推算，不写库） */
function blockStartIndex (b) {
  let s = dayIdx(b.planStartDate)
  if (s == null) {
    const e = dayIdx(b.planEndDate)
    const span = num(b.occupancyDays) || 1
    s = e == null ? 0 : (e - span + 1)
  }
  return Math.max(0, s)
}
function blockSpanDays (b) {
  const oc = num(b.occupancyDays)
  if (oc > 0) return oc
  const s = dayIdx(b.planStartDate)
  const e = dayIdx(b.planEndDate)
  if (s != null && e != null && e >= s) return e - s + 1
  return 1
}

/** 齐套已齐（实际入库）→ 块右上角绒绿小方块；其它齐套状态不占块面（只在悬停浮层体现） */
function isKittingReady (b) {
  return !!(b && b.kittingStatus === 'READY')
}
/** 未排期判定：计划开工与计划完工日期都为空（缺失）→ 归入右侧「未排期」区，不落日期轴 */
function isUnscheduled (b) {
  if (!b) return true
  return !b.planStartDate && !b.planEndDate
}
/** 全看板的未排期工序块（附带所属工作中心名，便于悬停提示） */
const unscheduledBlocks = computed(() => {
  const out = []
  for (const lane of lanes.value) {
    for (const b of (lane.blocks || [])) {
      if (isUnscheduled(b)) out.push({ ...b, workCenterName: lane.workCenterName })
    }
  }
  return out
})
function kittingText (b) {
  const d = b && b.kittingDate ? b.kittingDate : ''
  switch (b && b.kittingStatus) {
    case 'READY': return tt('齐套') + ' ' + d + '（' + tt('实际入库') + '）'
    case 'EXPECTED': return tt('预计齐套') + ' ' + d
    case 'SHORT': return tt('未齐套 缺') + ' ' + num(b.kittingShortageCount) + ' ' + tt('项')
    case 'NO_BOM': return tt('无 BOM')
    default: return ''
  }
}

/**
 * 泳道打包：把块按 planStartDate~planEndDate 换算成列位置；
 * 行内多块时间重叠时上下叠起（区间调度求最小行号）。
 */
const packedLanes = computed(() => lanes.value.map((lane, li) => {
  // 日期轴只画「有排期」的块；未排期块（计划开工/完工都为空）单独收进右侧「未排期」区
  const list = (lane.blocks || []).filter(b => !isUnscheduled(b)).map(b => {
    const s = blockStartIndex(b)
    const span = Math.max(1, Math.min(blockSpanDays(b), Math.max(1, dayCount.value - s)))
    return { ...b, _startIdx: s, _span: span, _endIdx: s + span, _row: 0 }
  })
  const rows = []
  // 重叠排布只按真实占位区间 [_startIdx, _endIdx)；等料天数只在块右上角显示一个数字，不再参与排布、不占日期格
  for (const b of [...list].sort((a, b2) => (a._startIdx - b2._startIdx) || (a._span - b2._span))) {
    let r = 0
    while (rows[r] && rows[r].some(x => !(b._endIdx <= x._startIdx || b._startIdx >= x._endIdx))) r++
    b._row = r
    if (!rows[r]) rows[r] = []
    rows[r].push(b)
  }
  const rowCount = Math.max(1, rows.length)
  const overloaded = (lane.days || []).some(d => d.overloaded === true) || list.some(b => b.overloaded === true)
  return {
    ...lane,
    key: (lane.workCenterId == null ? 'wc-na-' + li : 'wc-' + lane.workCenterId) + '-' + li,
    overloaded,
    trackHeight: TRACK_PAD_TOP + rowCount * ROW_H + 8,
    _blocks: [...list].sort((a, b2) => (a._startIdx - b2._startIdx) || (a._row - b2._row))
  }
}))
const totalBlocks = computed(() => packedLanes.value.reduce((n, l) => n + l._blocks.length, 0))

const orderLegend = computed(() => {
  const seen = {}
  const out = []
  for (const lane of lanes.value) {
    for (const b of (lane.blocks || [])) {
      const k = b.orderId == null ? 'na' : String(b.orderId)
      if (seen[k]) continue
      seen[k] = 1
      out.push({ key: k, label: b.orderNo || '', color: orderStyleMap.value[k].solid })
    }
  }
  return out
})

function capText (lane) {
  return num(lane.capacityPerDay) + (lane.capacityUnit || 'h') + '/天 · ' + tt('负载') + ' ' + num(lane.loadPercent) + '%'
}
/** 是否展示建议效率：建议值非空 且 样本数 ≥ 5（否则不占位、不显示空白行） */
function hasSuggestion (lane) {
  if (!lane) return false
  const eff = lane.suggestedEfficiency
  if (eff === null || eff === undefined || eff === '') return false
  return num(lane.suggestSampleSize) >= 5
}
/** 建议效率文案：建议 0.85（近 12 单实测，实际是计划的 1.18 倍） */
function suggestText (lane) {
  if (!hasSuggestion(lane)) return ''
  const eff = Number(lane.suggestedEfficiency)
  let s = tt('建议') + ' ' + (Number.isFinite(eff) ? eff.toFixed(2) : '')
  const extras = []
  const n = num(lane.suggestSampleSize)
  if (n > 0) extras.push(tt('近') + ' ' + n + ' ' + tt('单实测'))
  const rate = lane.suggestActualRate
  if (rate !== null && rate !== undefined && rate !== '' && Number.isFinite(Number(rate))) {
    extras.push(tt('实际是计划的') + ' ' + Number(rate).toFixed(2) + ' ' + tt('倍'))
  }
  if (extras.length) s += '（' + extras.join('，') + '）'
  return s
}
function blockStyleOf (b) {
  const s = orderStyle(b.orderId)
  return {
    left: (b._startIdx * colW.value) + 'px',
    width: Math.max(Math.round(colW.value * 0.55), b._span * colW.value - 6) + 'px',
    top: (TRACK_PAD_TOP + b._row * ROW_H) + 'px',
    backgroundImage: `linear-gradient(135deg, ${s.from}, ${s.to})`,
    boxShadow: `0 0 16px ${s.glow}`
  }
}
/** 告警条：按「工作中心 + 连续日期」合并成区间 */
const alertText = computed(() => {
  const arr = alerts.value
  if (!arr.length) return ''
  const byCenter = new Map()
  for (const a of arr) {
    const k = a.workCenterId != null ? String(a.workCenterId) : String(a.workCenterName || '')
    if (!byCenter.has(k)) byCenter.set(k, [])
    byCenter.get(k).push(a)
  }
  const segs = []
  for (const list of byCenter.values()) {
    const sorted = [...list].sort((x, y) => String(x.date).localeCompare(String(y.date)))
    let cur = null
    for (const a of sorted) {
      const prevEnd = cur ? msDay(cur.end) : null
      const curDate = msDay(a.date)
      const contiguous = cur && prevEnd != null && curDate != null && Math.round((curDate - prevEnd) / 86400000) === 1
      if (contiguous) {
        cur.end = a.date
        cur.hours += num(a.hours)
        cur.count += 1
        cur.capacity = num(a.capacity)
      } else {
        if (cur) segs.push(cur)
        cur = { center: a.workCenterName || '', start: a.date, end: a.date, hours: num(a.hours), count: 1, capacity: num(a.capacity) }
      }
    }
    if (cur) segs.push(cur)
  }
  const fmt = (s) => {
    const d = msDay(s)
    return d == null ? String(s || '') : pad2(new Date(d).getMonth() + 1) + '-' + pad2(new Date(d).getDate())
  }
  const texts = segs.slice(0, 4).map(s =>
    s.center + ' ' + fmt(s.start) + '～' + fmt(s.end) + ' ' + tt('撞车') + '：'
    + tt('占用') + ' ' + round2(s.hours) + 'h > ' + tt('日产能') + ' ' + round2(s.capacity * s.count) + 'h'
  )
  const more = segs.length > 4 ? ('；' + tt('等共') + segs.length + tt('段')) : ''
  return texts.join('；') + more
})

const emptyText = computed(() => boardFailed.value
  ? tt('排程看板暂不可用：排程数据服务未就绪，请稍后重试或联系管理员')
  : tt('本看板只显示「已生效」工单的工序；工单生效并按工艺路线/工单工序生成后，这里会出现排程泳道'))

// ---------------- 自动重排（默认关；开着才发写请求） ----------------
async function onApplyReschedule () {
  try {
    await ElMessageBox.confirm(
      tt('将按「最近空档日」重排所选工单的工序计划开工/完工日期并回写单据，是否继续？'),
      tt('应用重排'),
      { type: 'warning', confirmButtonText: tt('确认重排'), cancelButtonText: tt('取消') }
    )
  } catch (e) {
    return
  }
  applying.value = true
  try {
    const orderIds = [...new Set(lanes.value.flatMap(l => (l.blocks || []).map(b => b.orderId)).filter(id => id != null))]
    const res = await applyReschedule({ orderIds, days: dateRange.value })
    ElMessage.success(tt('已重排') + ' ' + ((res && res.updated) || 0) + ' ' + tt('条工单'))
    await loadData()
  } catch (e) {
    // 失败提示由请求拦截器统一处理
  } finally {
    applying.value = false
  }
}

/** 未排期块的悬停提示：工单号 · 工作中心 · 负责人 */
function unschedTip (b) {
  const parts = [b.orderNo, b.workCenterName]
  parts.push(b.unassigned === true ? tt('未派') : (b.assigneeName || ''))
  return parts.filter(Boolean).join(' · ')
}

/**
 * 右侧「未排期」区的「按产能自动排期」：把未排期工序所在工单交给后端，按「最近空档日」排入
 * 计划开工/完工日期并回写（调同一 apply-reschedule 端点，带二次确认）。
 */
async function onAutoScheduleUnscheduled () {
  const orderIds = [...new Set(unscheduledBlocks.value.map(b => b.orderId).filter(id => id != null))]
  if (!orderIds.length) {
    ElMessage.warning(tt('没有可自动排期的工单'))
    return
  }
  try {
    await ElMessageBox.confirm(
      tt('将把未排期工序按「最近空档日」排入计划开工/完工日期并回写单据，是否继续？'),
      tt('按产能自动排期'),
      { type: 'warning', confirmButtonText: tt('确认排期'), cancelButtonText: tt('取消') }
    )
  } catch (e) {
    return
  }
  applying.value = true
  try {
    const res = await applyReschedule({ orderIds, days: dateRange.value })
    ElMessage.success(tt('已排期') + ' ' + ((res && res.updated) || 0) + ' ' + tt('条工序'))
    await loadData()
  } catch (e) {
    // 失败提示由请求拦截器统一处理
  } finally {
    applying.value = false
  }
}

// ---------------- 数据加载 ----------------
async function loadData () {
  loading.value = true
  try {
    boardFailed.value = false
    const data = await getScheduleBoard({
      days: dateRange.value,
      workCenterId: workCenterId.value || undefined,
      includeFinished: includeFinished.value,
      assigneeFilter: assigneeFilter.value || 'all'
    })
    scheduleData.value = data || {}
  } catch (e) {
    scheduleData.value = {}
    boardFailed.value = true
  } finally {
    loading.value = false
    scrollLeft.value = 0
    await nextTick()
  }
}

async function loadWorkCenters () {
  try {
    workCenters.value = (await listAllWorkCenter()) || []
  } catch (e) {
    workCenters.value = []
  }
}

function onDateRangeChange () {
  scrollLeft.value = 0
  loadData()
}
function onScroll (e) {
  scrollLeft.value = e.target.scrollLeft
}

onMounted(async () => {
  await loadWorkCenters()
  await initStationFields()
  loadColW()
  await loadData()
})
</script>

<style scoped lang="scss">
@import '@/styles/productionBoard.scss';

/* ---------------- 告警条 ---------------- */
.alert-bar {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 8px 12px;
  margin-bottom: 10px;
  border-radius: 8px;
  background: rgba(255, 77, 141, .12);
  border: 1px solid rgba(255, 77, 141, .4);
  color: #FFB0C8;
  font-size: 11px;
  line-height: 1.5;
  white-space: normal;
  word-break: break-word;

  .ab-icon { flex-shrink: 0; margin-top: 2px; }
}

/* ---------------- APS 泳道（深色科技风面板） ---------------- */
.aps-wrap {
  position: relative;
  border-radius: 13px;
  padding: 8px;
  /* 荧光网格底纹（两层 linear-gradient，26px 栅格）+ 半透明深色底 */
  background-color: rgba(9, 16, 36, .72);
  background-image:
    linear-gradient(rgba(130, 170, 255, .09) 1px, transparent 1px),
    linear-gradient(90deg, rgba(130, 170, 255, .09) 1px, transparent 1px);
  background-size: 26px 26px, 26px 26px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, .35);
}
/* 面板外框：渐变描边（不改系统全局主题，仅本面板） */
.aps-wrap::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 13px;
  padding: 1px;
  background: linear-gradient(135deg, rgba(53, 230, 255, .55), rgba(122, 92, 255, .45) 45%, rgba(255, 77, 141, .40));
  -webkit-mask: linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0);
  -webkit-mask-composite: xor;
  mask: linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0);
  mask-composite: exclude;
  pointer-events: none;
  z-index: 7;
}

/* 主体两栏：左 = 可横滚的日期轴泳道（.aps-main）；右 = 固定宽度「未排期」区（.aps-unsched） */
.aps-body { display: flex; align-items: stretch; gap: 8px; }
.aps-main { flex: 1 1 auto; min-width: 0; }

.aps-sticky { position: sticky; top: 0; z-index: 6; }

.aps-head-clip {
  overflow: hidden;
  background: rgba(9, 16, 36, .88);
  border: 1px solid rgba(130, 170, 255, .16);
  border-bottom: 1px solid rgba(130, 170, 255, .14);
  border-radius: 10px 10px 0 0;
}
.aps-head-track { display: flex; will-change: transform; position: relative; }

/* 日期列边界拖拽手柄：鼠标移上变左右箭头（col-resize）；按住左右拖动实时改列宽，双击恢复默认 104px */
.aps-col-resizer {
  position: absolute;
  top: 0;
  height: 100%;
  width: 8px;
  cursor: col-resize;
  z-index: 3;

  &::after {
    content: '';
    position: absolute;
    left: 3px;
    top: 8px;
    bottom: 8px;
    width: 2px;
    border-radius: 1px;
    background: rgba(130, 170, 255, .45);
    opacity: 0;
    transition: opacity .15s;
  }
  &:hover::after { opacity: 1; }
}

.aps-head-corner {
  flex: 0 0 168px;
  width: 168px;
  height: 40px;
  display: flex;
  align-items: center;
  padding: 0 10px;
  font-size: 11px;
  color: #8FA4C8;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  border-right: 1px solid rgba(130, 170, 255, .12);
}

.aps-head-cell {
  flex: 0 0 var(--col-w, 104px);
  width: var(--col-w, 104px);
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: #C7D4EE;
  font-variant-numeric: tabular-nums;
  border-right: 1px solid rgba(130, 170, 255, .08);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;

  &.is-weekend { background: rgba(130, 170, 255, .03); color: #6E82A8; }
  &.is-today { background: rgba(53, 230, 255, .12); color: #35E6FF; font-weight: 700; }
}

.aps-scroll {
  overflow-x: auto;
  overflow-y: hidden;
  background: transparent;
  border: 1px solid rgba(130, 170, 255, .16);
  border-top: none;
  border-radius: 0 0 10px 10px;

  &::-webkit-scrollbar { height: 9px; }
  &::-webkit-scrollbar-thumb { background: rgba(130, 170, 255, .28); border-radius: 5px; }
}

.aps-grid { display: block; }

.aps-lane {
  display: flex;
  align-items: stretch;
  border-bottom: 1px solid rgba(130, 170, 255, .08);
  &:last-child { border-bottom: none; }
}

.aps-lane-label {
  flex: 0 0 168px;
  width: 168px;
  padding: 8px 10px;
  border-left: 4px solid #6366f1;
  border-right: 1px solid rgba(130, 170, 255, .10);
  background: rgba(130, 170, 255, .04);
  display: flex;
  flex-direction: column;
  gap: 3px;
  justify-content: center;
  min-width: 0;

  .lane-wc {
    font-size: 11px;
    font-weight: 600;
    color: #E8F0FF;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .lane-cap {
    font-size: 11px;
    color: #8FA4C8;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    .cap-sep { margin: 0 3px; color: #4A5C82; }
    .cap-warn { color: #FF4D8D; font-weight: 700; }
  }
  /* 建议效率系数（仅出建议、不自动回填）：主行青色、说明行次要灰，均为 11px */
  .lane-suggest {
    display: flex;
    flex-direction: column;
    gap: 1px;
    min-width: 0;
    .sug-main {
      font-size: 11px;
      color: #7DD3FC;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .sug-note {
      font-size: 11px;
      color: #6E82A8;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  }
  .lane-miss {
    font-size: 11px;
    color: #FFC53D;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

.aps-track { position: relative; flex: 0 0 auto; }

.aps-heat { position: absolute; inset: 0; display: flex; pointer-events: none; }
.heat-cell {
  flex: 0 0 var(--col-w, 104px);
  width: var(--col-w, 104px);
  border-right: 1px solid rgba(130, 170, 255, .05);
  transition: background .25s;
}
/* 超载格呼吸脉冲：仅当允许动效时启用 */
@media (prefers-reduced-motion: no-preference) {
  .heat-cell.is-over { animation: heatPulse 2.6s ease-in-out infinite; }
}
@keyframes heatPulse { 0%, 100% { opacity: .85; } 50% { opacity: 1; } }

.today-line {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 2px;
  background: #35E6FF;
  box-shadow: 0 0 12px rgba(53, 230, 255, .85);
  z-index: 1;
}

.aps-block {
  position: absolute;
  height: 30px;
  z-index: 2;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 2px 6px;
  /* 设计图：块无左边框，只保留 1px 半透明白描边（不再有 3px 左侧色条） */
  border: 1px solid rgba(255, 255, 255, .18);
  border-radius: 5px;
  overflow: hidden;
  box-sizing: border-box;
  cursor: pointer;
  transition: box-shadow .2s, filter .2s;

  &:hover { filter: brightness(1.12); z-index: 4; }
  /* 未派工：整圈虚线描边（其余视觉与已派工一致） */
  &.is-unassigned { border: 1px dashed rgba(255, 165, 60, .85); }
}
/* 超载块呼吸脉冲：仅当允许动效时启用 */
@media (prefers-reduced-motion: no-preference) {
  .aps-block.is-over { animation: blkPulse 2.4s ease-in-out infinite; }
}
@keyframes blkPulse {
  0%, 100% { box-shadow: inset 0 0 0 1px rgba(255, 77, 141, .9), 0 0 16px rgba(122, 92, 255, .55); }
  50% { box-shadow: inset 0 0 0 2px rgba(255, 77, 141, 1), 0 0 22px rgba(255, 77, 141, .6); }
}

.blk-l1 {
  font-size: 11px;
  font-weight: 600;
  color: #F2F6FF;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.blk-l2 {
  font-size: 11px;
  color: rgba(255, 255, 255, .82);
  overflow: hidden;
  white-space: nowrap;
  display: flex;
  align-items: center;
  gap: 3px;
  .blk-dot { color: rgba(255, 255, 255, .55); }
  .blk-unassigned { color: #FFC061; font-weight: 700; }
  .blk-assignee { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
}
/* 块右上角角标区（同一套角标语言，互不重叠）：齐套小方块在左、等料天数数字在右；超载红三角贴最右上角时整组左移让位 */
.blk-corner {
  position: absolute;
  top: 3px;
  right: 3px;
  display: flex;
  align-items: center;
  gap: 3px;
  z-index: 3;
  pointer-events: none;
  &.is-over { right: 13px; }
}
/* 齐套已齐（实际入库）：绒绿小方块（7×7，圆角 1px） */
.blk-kit-ico {
  width: 7px;
  height: 7px;
  border-radius: 1px;
  background: #34D399;
  box-shadow: 0 0 5px rgba(52, 211, 153, .85);
}
/* 等料天数：块右上角琥珀色数字小角标（取代原按天数撑宽的斜纹段；不占日期格、不影响负载、不参与重叠排布） */
.blk-wait-ico {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 12px;
  height: 13px;
  padding: 0 3px;
  border-radius: 3px;
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
  color: #FFC53D;
  background: rgba(255, 197, 61, .18);
  border: 1px solid rgba(255, 197, 61, .55);
  font-variant-numeric: tabular-nums;
}
/* 超载：块右上角红色三角（与全站告警红一致），明细进悬停浮层 */
.blk-over-ico {
  position: absolute;
  top: 0;
  right: 0;
  width: 0;
  height: 0;
  border-top: 11px solid #FF4D8D;
  border-left: 11px solid transparent;
  z-index: 4;
}

/* ---------------- 未排期区（时间轴最右侧，固定宽度独立列） ---------------- */
.aps-unsched {
  flex: 0 0 208px;
  width: 208px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px;
  border-radius: 10px;
  border: 1px dashed rgba(255, 197, 61, .45);
  background: rgba(255, 197, 61, .06);

  .uns-head { display: flex; align-items: center; justify-content: space-between; gap: 6px; }
  .uns-title {
    font-size: 11px;
    color: #FFE0A3;
    white-space: nowrap;
    b { font-family: $mono; color: #FFD166; }
  }
  .uns-list { display: flex; flex-direction: column; gap: 6px; }
}
.uns-block {
  display: flex;
  align-items: center;
  gap: 3px;
  padding: 4px 8px;
  border-radius: 5px;
  border: 1px dashed rgba(255, 197, 61, .6);
  background: rgba(255, 197, 61, .10);
  color: #FFE0A3;
  font-size: 11px;
  cursor: pointer;
  transition: background .2s, filter .2s;
  &:hover { filter: brightness(1.15); background: rgba(255, 197, 61, .18); }
  .uns-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .uns-dot { color: rgba(255, 224, 163, .55); }
  .uns-unassigned { color: #FFA53C; font-weight: 700; white-space: nowrap; }
  .uns-assignee { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
}

/* ---------------- 图例 ---------------- */
.aps-legend {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  padding: 10px 14px;
  margin-top: 10px;
  background: rgba(130, 170, 255, .06);
  border: 1px solid rgba(130, 170, 255, .14);
  border-radius: 10px;
  font-size: 11px;
  color: #8FA4C8;

  .lg-item { display: inline-flex; align-items: center; gap: 5px; white-space: nowrap; }
  .lg-swatch { width: 12px; height: 12px; border-radius: 3px; display: inline-block; }
  .lg-unassigned { background: #FFA53C; }
  .lg-over { background: #FF4D8D; }
  /* 等料图例色块必须与实现完全一致（同一个琥珀色数字角标） */
  .lg-wait-badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 12px;
    height: 13px;
    padding: 0 3px;
    border-radius: 3px;
    font-size: 11px;
    font-weight: 700;
    line-height: 1;
    color: #FFC53D;
    background: rgba(255, 197, 61, .18);
    border: 1px solid rgba(255, 197, 61, .55);
  }
  .lg-heat { background: rgba(53, 230, 255, .15); }
}

/* 排程看板块悬停浮层 */
.aps-tip { font-size: 11px; line-height: 1.5; min-width: 180px; }
.aps-tip-h { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; font-size: 11px; }
.aps-tip-dot { width: 9px; height: 9px; border-radius: 50%; flex-shrink: 0; }
.aps-tip-r {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  span { color: #9ca3af; white-space: nowrap; }
  b { color: #f3f4f6; font-weight: 600; text-align: right; }
  b.warn { color: #fca5a5; }
}

:deep(.el-button) { border-radius: 8px; font-weight: 500; }

@media (max-width: 768px) {
  .board-page { padding: 12px; }
  .hud { flex-direction: column; align-items: stretch; }
}
</style>
