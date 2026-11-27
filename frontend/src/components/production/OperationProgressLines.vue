<template>
  <div class="opt-scroll" data-order-scroll="1">
    <el-table
      ref="tableRef"
      :data="groups"
      class="opt-table"
      data-opt-table="1"
      size="small"
      border
      height="100%"
      :default-sort="defaultSort"
      @sort-change="onTableSort"
      @header-dragend="onHeaderDragend"
    >
      <el-table-column
        v-for="col in columns"
        :key="col.prop"
        :prop="col.prop"
        :label="$tf(col.label)"
        :width="col.width"
        :min-width="colMinWidth(col)"
        :align="col.align || 'left'"
        :sortable="col.sortable ? 'custom' : false"
      >
        <template #default="{ row: g, $index: idx }">
            <!-- 序号 -->
            <template v-if="col.type === 'index'">{{ idx + 1 }}</template>

            <!-- 工单编号（可点跳工单编辑页；超期挂「延期」标签） -->
            <template v-else-if="col.type === 'orderNo'">
              <a
                class="order-link"
                :data-order-id="orderIdOf(g)"
                :title="$tt('查看单据')"
                @click.stop="gotoOrder(orderIdOf(g))"
              >{{ orderNoOf(g) }}</a>
              <span v-if="lineOverdue(g)" class="ovd-tag">{{ $tt("延期") }}</span>
            </template>

            <!-- 生产进度：工序圆环链（黑湖小工单口径）。每道工序圆环下方依次显示：工序名 / 负责人 / 计划完成，
                 超期时追加「超期 N 天」（累积），实际完工后追加「实际 时间」。环与环之间为贯穿线。 -->
            <template v-else-if="col.type === 'progress'">
              <div class="ring-chain" data-ring-chain="1">
                <template v-for="(op, i) in mainOps(g)" :key="op.operationRowId || op.id">
                  <el-tooltip placement="top" effect="light" :show-after="180" popper-class="st-tip-popper" :popper-options="popperOptions">
                    <template #content>
                      <div class="st-tip">
                        <div class="st-tip-head">
                          <span class="st-tip-statusdot" :style="{ background: ringHex(op) }"></span>
                          <b>{{ op.operationName || $tt("未命名工序") }}</b>
                          <span class="st-tip-type">{{ $tf(op.operationType || '状态类型') }}</span>
                          <span v-if="childCountOf(op) > 0" class="st-tip-type">{{ $tt("父工序") }} · {{ childCountOf(op) }}</span>
                          <span v-if="op.isCritical === 1" class="st-tip-critical">★ {{ $tt("关键工序") }}</span>
                        </div>
                        <div class="st-tip-grid">
                          <span class="st-tip-k">{{ $tt("完成进度") }}</span>
                          <span class="st-tip-v">{{ ringPercent(op) }}%</span>
                          <span class="st-tip-k">{{ $tt("状态") }}</span>
                          <span class="st-tip-v">{{ $dt(op.operationStatus || '待派工') }}</span>
                          <template v-for="f in tooltipFieldsOf(op)" :key="f.prop">
                            <span class="st-tip-k">{{ $tf(f.label) }}</span>
                            <span class="st-tip-v">{{ f.value }}</span>
                          </template>
                          <template v-if="overdueDaysOf(op) > 0">
                            <span class="st-tip-k">{{ $tt("超期") }}</span>
                            <span class="st-tip-v st-tip-warn">{{ overdueDaysOf(op) }} {{ $tt("天") }}</span>
                          </template>
                        </div>
                      </div>
                    </template>

                    <!-- 连线用节点自身伪元素画（宽 = 整个节点），相邻节点首尾相接 = 一条贯穿线，不再一截一截断 -->
                    <div
                      class="ring-node"
                      :data-station="op.operationName"
                      :data-ring-state="ringClass(op)"
                      :style="linkStyle(g, i)"
                      @click="onRingClick(op)"
                    >
                      <span class="ring" :class="ringClass(op)">
                        <span v-if="isDone(op)" class="ring-chk"></span>
                        <span v-else class="ring-pct">{{ ringPercent(op) }}%</span>
                        <span v-if="childCountOf(op) > 0" class="ring-badge" :title="$tt('父工序：点开看子工序')">总·{{ childCountOf(op) }}</span>
                        <span v-if="op.isCritical === 1" class="ring-star" :title="$tt('关键工序')">★</span>
                      </span>
                      <span class="ring-cap" :title="op.operationName">{{ op.operationName }}</span>
                      <span v-if="op.assigneeName" class="ring-sub" :title="op.assigneeName">{{ op.assigneeName }}</span>
                      <span v-if="planShort(op)" class="ring-sub" :title="planFull(op)">{{ $tt("完成") }} {{ planShort(op) }}</span>
                      <span v-if="overdueDaysOf(op) > 0" class="ring-sub is-over" :title="$tt('超期天数（累积）')">{{ $tt("超期") }}{{ overdueDaysOf(op) }}{{ $tt("天") }}</span>
                      <span v-if="actualShort(op)" class="ring-sub is-actual" :title="actualFull(op)">{{ $tt("实际") }} {{ actualShort(op) }}</span>
                    </div>
                  </el-tooltip>
                </template>
              </div>
            </template>

            <!-- 整单状态：● 执行中 / ● 未开始 / ● 已完工 -->
            <template v-else-if="col.type === 'status'">
              <span class="line-state">
                <span class="line-dot" :class="lineStateClass(g)"></span>
                <span class="line-state-text" :class="lineStateClass(g)">{{ lineStateText(g) }}</span>
              </span>
            </template>

            <!-- 计划完工（取组内最早计划完工日，与后端排序口径一致） -->
            <template v-else-if="col.type === 'planEndDate'">{{ planEndOf(g) }}</template>

            <!-- 负责人：组内各工序负责人去重后顿号相连 -->
            <template v-else-if="col.prop === 'assigneeName'">{{ assigneesOf(g) }}</template>

            <!-- 工序数 -->
            <template v-else-if="col.prop === 'opCount'">{{ (g.ops || []).length }}</template>

            <!-- 普通文本列（物料编码 / 物料名称 / 规格 / 工作中心） -->
            <template v-else>{{ textOf(g, col.prop) }}</template>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
/**
 * 车间进度跟踪「表格 + 工序圆环链」：一行 = 一条线路（工单 + 成品行），表头吸顶、列可配。
 * 样式口径 = 黑湖小工单的「生产进度」（实拍核对）：
 *   每道工序一个 28px 细圆环，环内 = 该道完成百分比（已完工换成对勾）；
 *   环下 = 工序名；环与环之间一小段短线，线色跟随左侧工序的环色；
 *   灰环 = 未开始，蓝环 = 进行中 / 已完成；超期红环、关键工序 ★（本项目增强）。
 *
 * 列由调用方下发（受「列设置」控制显隐与顺序）；表头排序点击后 emit('sort-change')，
 * 由调用方按后端 sortField/sortOrder 重新取数。
 * 共用逻辑来自 composables/useOperationBoard.js；悬停浮层字段来自调用方传入的 stationFieldsOf。
 */
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { useOperationBoard } from '@/composables/useOperationBoard'
import { useColWidth } from '@/composables/useColWidth'
import { tt } from '@/i18n'

const props = defineProps({
  /** 线路：[{ key, orderId, orderNo, materialCode, materialName, specification, workCenterName, ops }] */
  groups: { type: Array, default: () => [] },
  /** 可见且排好序的列（由父页「列设置」得出） */
  columns: { type: Array, default: () => [] },
  /** 当前排序列（后端 sortField：orderNo/materialCode/materialName/status/planEndDate） */
  sortField: { type: String, default: '' },
  /** 当前排序方向：asc / desc */
  sortOrder: { type: String, default: 'asc' },
  /** (op, view) => [{ prop, label, value }] 悬停浮层字段（受「站点设置」控制） */
  stationFieldsOf: { type: Function, required: true }
})
const emit = defineEmits(['open-panel', 'open-parent', 'sort-change'])

// 列宽持久化（2026-10-08）：pageKey 与父页「列设置」一致（车间进度跟踪页），
// 本表拖的列宽与父页列设置的列显隐/顺序共用同一条记录、互不覆盖（宽度存在 columns[prop].width）
const PAGE_KEY = 'ProductionProgressTracking'
const tableRef = ref(null)
const { onHeaderDragend, applySavedWidths } = useColWidth(PAGE_KEY)

const { num, clampPct, orderIdOf, orderNoOf, gotoOrder } = useOperationBoard()

/** 【第 19 期】直接子工序数（后端下发 childCount）：父工序显示「总·N」角标 */
function childCountOf (op) { return Math.max(0, num(op && op.childCount)) }
/**
 * 【第 19 期】主链工序 = 顶层工序（无父，或父不在本线路内 → 视为顶层，与后端汇总口径一致）。
 * 有父的工序统一收进父节点的「总」弹窗里，不再平铺到主链上。
 */
function mainOps (g) {
  const ops = (g && g.ops) || []
  const ids = new Set(ops.map(o => (o && (o.operationRowId != null ? o.operationRowId : o.id))))
  return ops.filter(o => {
    const pid = o && o.parentRowId
    if (pid == null) return true
    return !ids.has(Number(pid))
  })
}
/** 【第 19 期】点圆环：父工序（有下级）→ 弹子工序弹窗；其余 → 打开该工序操作面板 */
function onRingClick (op) {
  if (childCountOf(op) > 0) emit('open-parent', op)
  else emit('open-panel', op)
}

/** 悬停浮层：固定定位 + 视口边界避让（贴屏幕边缘自动翻转）；内容超高时浮层内部滚动 */
const popperOptions = {
  strategy: 'fixed',
  modifiers: [
    { name: 'offset', options: { offset: [0, 8] } },
    { name: 'flip', options: { fallbackPlacements: ['top', 'bottom', 'right', 'left'], padding: 8 } },
    { name: 'preventOverflow', options: { boundary: 'viewport', padding: 8, altAxis: true } }
  ]
}

/** 视为「在做」的状态（圆环蓝色）：已开工 / 进行中 / 暂停 / 完工待检 */
const RUN_STATES = ['已开工', '进行中', '暂停', '完工待检']
/** 整单「执行中」口径（与后端 boardLineStatusRank 及用户口径一致）：仅 已开工 / 进行中 */
const STATUS_RUN = ['已开工', '进行中']
/** 三色口径（写死，与黑湖对齐）：灰=未开始 / 蓝=进行中·已完成 / 红=超期 */
const RING_HEX = { idle: '#cbd2dc', run: '#2f8bf5', over: '#ef4444' }

function isDone (op) { return !!op && op.operationStatus === '已完工' }
function isCanceled (op) { return !!op && op.operationStatus === '已取消' }
/**
 * 【2026-10-09】超期天数（累积口径，界面与实际显示同源）：
 *   已取消 → 0；有计划完成日 → 未完工取「计划完成 → 今天」，已完工取「计划完成 → 实际完成」；
 *   日期缺任一 → 回退后端 overdueDays；两者都取不到 → 0。
 */
function overdueDaysOf (op) {
  if (!op || isCanceled(op)) return 0
  const plan = dayTs(op.planEndDate)
  const fallback = num(op.overdueDays)
  if (plan == null) return fallback > 0 ? fallback : 0
  let endTs
  if (isDone(op)) {
    endTs = dayTs(op.actualEndDate)
    if (endTs == null) return fallback > 0 ? fallback : 0
  } else {
    const t = new Date()
    t.setHours(0, 0, 0, 0)
    endTs = t.getTime()
  }
  const d = Math.round((endTs - plan) / 86400000)
  return d > 0 ? d : 0
}
/** 取 yyyy-MM-dd 的当天 0 点时间戳（无值/非法返回 null） */
function dayTs (v) {
  if (!v) return null
  const d = new Date(String(v).substring(0, 10) + 'T00:00:00')
  return Number.isNaN(d.getTime()) ? null : d.getTime()
}
/** 计划完成（短格式 MM-DD，列表窄，避免撑破节点） */
function planShort (op) { const v = op && op.planEndDate; return v ? String(v).substring(5, 10) : '' }
function planFull (op) { const v = op && op.planEndDate; return v ? String(v).substring(0, 10) : '' }
/** 实际完成（短格式）：仅已完工且后端有实际完工日期时出现 */
function actualShort (op) {
  if (!isDone(op)) return ''
  const v = op && op.actualEndDate
  return v ? String(v).substring(5, 10) : ''
}
function actualFull (op) { const v = op && op.actualEndDate; return v ? String(v).substring(0, 10) : '' }
/** 贯穿连线：本节点的两段颜色（本工序环色 → 下一道环色），由节点 ::after 的渐变画出 */
function linkStyle (g, i) {
  const list = mainOps(g)
  const cur = list[i]
  const next = list[i + 1] || cur
  return { '--lf': ringHex(cur), '--lt': ringHex(next) }
}

/** 超期且未完工/未取消（口径与界面显示的「超期 N 天」一致） */
function isOverdue (op) { return !!op && overdueDaysOf(op) > 0 && !isDone(op) && !isCanceled(op) }

/** 环的样式类：is-over / is-done / is-run / is-idle */
function ringClass (op) {
  if (isOverdue(op)) return 'is-over'
  if (isDone(op)) return 'is-done'
  if (op && RUN_STATES.includes(op.operationStatus)) return 'is-run'
  return 'is-idle'
}
/** 环色（同时用于环间短线与悬停浮层的状态点） */
function ringHex (op) {
  const c = ringClass(op)
  if (c === 'is-over') return RING_HEX.over
  if (c === 'is-idle') return RING_HEX.idle
  return RING_HEX.run
}

/**
 * 该道工序完成百分比（写死口径）：
 * 父工序→子项加权完工率；入库类型→已入库量÷计划数量；金额类型→已确认金额÷计划金额；
 * 其余→已报工数量÷计划数量（计划为 0 时：已完工=100，否则=0）。分母为 0 不算，不出现 NaN。
 */
function ringPercent (op) {
  if (!op) return 0
  if (num(op.childCount) > 0) return Math.round(clampPct(op.childProgress))
  if (op.operationType === '入库类型') {
    const out = num(op.outputQuantity)
    return out > 0 ? Math.round(clampPct(num(op.receivedQty) / out * 100)) : 0
  }
  if (op.operationType === '金额类型') {
    const pl = num(op.planAmount)
    return pl > 0 ? Math.round(clampPct(num(op.confirmedAmount) / pl * 100)) : 0
  }
  const plan = num(op.planQuantity)
  if (plan > 0) return Math.round(clampPct(num(op.reportedQuantity) / plan * 100))
  return isDone(op) ? 100 : 0
}

/** 悬停浮层字段（受「站点设置」勾选 + 排序控制） */
function tooltipFieldsOf (op) {
  try {
    return props.stationFieldsOf(op, 'progress') || []
  } catch (e) {
    return []
  }
}

/** 该线路是否有超期未完工的工序 */
function lineOverdue (g) { return (g.ops || []).some(isOverdue) }
/** 整单状态：全部工序已完工=已完工；有任一工序处于 已开工/进行中=执行中；否则未开始 */
function lineStateClass (g) {
  const ops = (g.ops || []).filter(o => !isCanceled(o))
  if (!ops.length) return 'is-idle'
  if (ops.every(isDone)) return 'is-done'
  if (ops.some(o => STATUS_RUN.includes(o.operationStatus))) return 'is-run'
  return 'is-idle'
}
function lineStateText (g) {
  const c = lineStateClass(g)
  if (c === 'is-done') return tt('已完工')
  if (c === 'is-run') return tt('执行中')
  return tt('未开始')
}

/** 计划完工：与后端排序口径一致，取组内最早计划完工日（空则留空，不出现占位符） */
function planEndOf (g) {
  const op = (g.ops || [])[0]
  const v = op && op.planEndDate
  if (!v) return ''
  return String(v).substring(0, 10)
}

/** 负责人：组内各工序负责人去重（空值不占位） */
function assigneesOf (g) {
  const names = []
  ;(g.ops || []).forEach(o => {
    const n = o && o.assigneeName
    if (n && !names.includes(n)) names.push(n)
  })
  return names.join('、')
}

/** 普通文本列取值（值为空留空，不出现「-」/「[object Object]」） */
function textOf (g, prop) {
  const v = g ? g[prop] : ''
  if (v === null || v === undefined) return ''
  const t = typeof v
  return (t === 'string' || t === 'number') ? String(v) : ''
}

/** 表头排序（el-table sortable="custom"）：点击列头 → 交父页按后端 sortField/sortOrder 重新取数 */
function onTableSort ({ prop, order }) {
  if (!prop) return
  emit('sort-change', { field: prop, order: order === 'descending' ? 'desc' : 'asc' })
}

/** 当前排序列（表头默认排序箭头；el-table default-sort 只认 ascending/descending） */
const defaultSort = computed(() => (
  props.sortField
    ? { prop: props.sortField, order: props.sortOrder === 'desc' ? 'descending' : 'ascending' }
    : undefined
))

/** 圆环链最宽线路的工序数：用于给「生产进度」列一个够宽的最小宽度，避免圆环被单元格裁掉 */
const maxRingCount = computed(() => props.groups.reduce((m, g) => Math.max(m, (g.ops || []).length), 0))

/** 列最小宽度：生产进度列按「每道工序 68px 节点 + 首尾留白」估算，其余列沿用列定义 */
function colMinWidth (col) {
  if (col && col.type === 'progress') return Math.max(col.minWidth || 0, maxRingCount.value * 80 + 28)
  return col ? col.minWidth : undefined
}

/** 明细表列宽恢复：挂载后 + 列变化后 + 数据加载后各套一次已保存的宽度 */
function restoreColumnWidths () {
  nextTick(() => applySavedWidths(tableRef))
}
onMounted(restoreColumnWidths)
watch(() => props.columns, restoreColumnWidths)
watch(() => props.groups, restoreColumnWidths)
</script>

<style scoped lang="scss">
/* 铺满父容器 + 内部滚动（父容器 .board-body 已 flex:1）；去掉旧的 max-height，避免表格下方留一大片空白 */
.opt-scroll {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: #fff;
  border-radius: 12px;
  border: 1px solid #eef2ff;
  box-shadow: 0 2px 10px rgba(99, 102, 241, .06);
}

/* el-table 口径对齐原手写表：字号 11px、表头浅蓝底、行高 48px、行悬停高亮 */
.opt-table {
  --el-table-border-color: #e6ebf7;
  font-size: 11px;

  :deep(.el-table__header th) {
    background: #f5f8ff !important;
    color: #4b5563;
    font-size: 11px;
    font-weight: 600;
    white-space: nowrap;
  }
  /* 行高改为按内容自适应：圆环下方多了一行关键信息，不能再钉死 48px（否则会裁掉内容与「总·N」角标） */
  :deep(.el-table__body td) {
    padding-top: 6px;
    padding-bottom: 6px;
    vertical-align: middle;
    color: #374151;
  }
  :deep(.el-table__row:hover > td) { background: #fafbff !important; }
}

.order-link {
  font-size: 11px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: #4f46e5;
  text-decoration: underline;
  text-underline-offset: 2px;
  cursor: pointer;
  white-space: nowrap;
  &:hover { color: #4338ca; }
}

.ovd-tag {
  display: inline-block;
  margin-left: 5px;
  font-size: 10px;
  line-height: 1;
  font-weight: 600;
  color: #fff;
  background: #ef4444;
  border-radius: 3px;
  padding: 2px 4px;
  vertical-align: middle;
}

/* 整单状态：点 + 文案 */
.line-state { display: inline-flex; align-items: center; gap: 5px; }
.line-dot {
  flex: 0 0 auto;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #cbd2dc;

  &.is-run { background: #2f8bf5; }
  &.is-done { background: #10b981; }
}
.line-state-text {
  font-size: 11px;
  color: #6b7280;
  &.is-run { color: #2f8bf5; }
  &.is-done { color: #10b981; }
}

/* ---------------- 工序圆环链 ---------------- */
/* 【2026-10-09】环间连线改为「节点伪元素贯穿线」：相邻节点首尾相接 → 视觉上是一条连续线（不再是断开的小短线） */
.ring-chain {
  display: flex;
  align-items: flex-start;
  /* 上方给父工序「总·N」角标留空间；右侧给最后一个节点的角标留空间（否则被单元格 overflow:hidden 裁掉） */
  padding: 12px 14px 2px 0;
}

.ring-node {
  position: relative;
  flex: 0 0 auto;
  width: 68px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  cursor: pointer;

  &:hover .ring { transform: translateY(-2px); }
  &:hover .ring-cap { color: #4f46e5; }
  &:hover .ring-sub { color: #4b5563; }
}

/* 贯穿连线：从「本节点圆心」连到「下一节点圆心」（宽度 = 整个节点宽 → 相邻两段首尾相接 = 一条线）。
   颜色 = 本工序环色 → 下一道环色的渐变（--lf / --lt 由行内 style 给定）。 */
.ring-node:not(:last-child)::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 13px;
  width: 100%;
  height: 2px;
  border-radius: 1px;
  background: linear-gradient(90deg, var(--lf, #cbd2dc), var(--lt, #cbd2dc));
  z-index: 0;
}

/* 圆环下方逐条信息（负责人 / 完成 / 超期 / 实际）：9px 灰字；超期红、实际蓝 */
.ring-sub {
  max-width: 68px;
  font-size: 9px;
  line-height: 1.15;
  color: #9aa3b2;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;

  &.is-over { color: #ef4444; font-weight: 600; }
  &.is-actual { color: #2f8bf5; }
}

/* 工序圆环：28px 细环（z-index 抬高，让贯穿连线从环「背后」穿过，看着是一条连续的线） */
.ring {
  position: relative;
  z-index: 1;
  box-sizing: border-box;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 2px solid #cbd2dc;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform .2s;

  &.is-run, &.is-done { border-color: #2f8bf5; }
  &.is-over { border-color: #ef4444; }
}

.ring-pct {
  font-size: 10px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: #9aa3b2;

  .is-run & { color: #2f8bf5; }
  .is-over & { color: #ef4444; }
}

/* 已完工：环内对勾 */
.ring-chk {
  width: 9px;
  height: 5px;
  margin-top: -3px;
  border-left: 2px solid #2f8bf5;
  border-bottom: 2px solid #2f8bf5;
  border-radius: 1px;
  transform: rotate(-45deg);
}

/* 【第 19 期】父工序「总·N」角标（环右上角；与关键工序星标分别占左右两侧，避免重叠） */
.ring-badge {
  position: absolute;
  top: -9px;
  right: -8px;
  padding: 0 4px;
  font-size: 9px;
  line-height: 13px;
  font-weight: 600;
  color: #fff;
  background: #4f46e5;
  border-radius: 7px;
  white-space: nowrap;
}

/* 关键工序：环左上角小星（本项目增强） */
.ring-star {
  position: absolute;
  top: -7px;
  left: -6px;
  font-size: 9px;
  line-height: 1;
  color: #f59e0b;
  text-shadow: 0 0 2px #fff;
}

.ring-cap {
  max-width: 68px;
  font-size: 11px;
  line-height: 1.1;
  color: #6b7280;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---------------- 悬停浮层 ---------------- */
:global(.st-tip-popper) {
  max-width: 380px;
  padding: 8px 10px !important;
}

/* 浮层内容超高时在浮层内部滚动（字段一个不删，保证每一项都能看到） */
.st-tip {
  max-height: 60vh;
  overflow-y: auto;
  overscroll-behavior: contain;

  &::-webkit-scrollbar { width: 6px; }
  &::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 3px; }
}

.st-tip-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  font-size: 11px;

  .st-tip-statusdot { width: 9px; height: 9px; border-radius: 50%; flex-shrink: 0; }
  .st-tip-type { font-size: 11px; color: #6b7280; background: #f3f4f6; border-radius: 4px; padding: 0 5px; }
  .st-tip-critical { font-size: 11px; color: #f59e0b; }
}

.st-tip-grid {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 3px 10px;
  font-size: 11px;

  .st-tip-k { color: #9ca3af; white-space: nowrap; }
  .st-tip-v { color: #374151; word-break: break-all; }
  .st-tip-warn { color: #ef4444; font-weight: 600; }
}
</style>
