<template>
  <el-dialog
    v-model="visible"
    :title="dlgTitle"
    width="760px"
    append-to-body
    class="op-child-dialog"
    @closed="onClosed"
  >
    <div class="ocd">
      <!-- 下钻层级面包屑 + 返回上一层（仅下钻后出现） -->
      <div v-if="path.length" class="ocd-bar">
        <el-button link size="small" :icon="Back" @click="goBack">{{ $tt("返回上一层") }}</el-button>
        <span class="ocd-crumb">{{ crumbText }}</span>
      </div>

      <div v-if="children.length" class="ocd-chain" data-child-chain="1">
        <template v-for="(op, i) in children" :key="op.operationRowId || op.id">
          <span v-if="i > 0" class="ocd-link" :style="{ background: hexOf(children[i - 1]) }"></span>
          <div class="ocd-node" :data-station="op.operationName" @click="openNode(op)">
            <span class="ocd-ring" :class="clsOf(op)">
              <span v-if="isDone(op)" class="ocd-chk"></span>
              <span v-else class="ocd-pct">{{ pctOf(op) }}%</span>
              <span v-if="childCountOf(op) > 0" class="ocd-badge">总·{{ childCountOf(op) }}</span>
              <span v-if="op.isCritical === 1" class="ocd-star" :title="$tt('关键工序')">★</span>
            </span>
            <span class="ocd-cap" :title="op.operationName">{{ op.operationName }}</span>
          </div>
        </template>
      </div>
      <div v-else class="ocd-empty">{{ $tt("该工序没有子工序") }}</div>

      <div class="ocd-tip">{{ $tt("点圆环：还有下级就再下一层，没有下级就打开该子工序的操作面板") }}</div>
    </div>
  </el-dialog>
</template>

<script setup>
/**
 * 子工序圆环链弹窗（第 19 期）：点父工序「总」节点后弹出。
 * 视觉与「车间进度跟踪」的工序圆环链完全一致（灰=未开始 / 蓝=进行中·完工 / 红=超期；环下工序名、环间短线）。
 * 多层下钻：点子环若自身还有下级（childCount>0）→ 进入下一层；否则 emit('open-panel', 子工序)。
 * 右上角 el-dialog 自带 × 关闭；下钻后顶部出现「返回上一层」。
 */
import { ref, computed, watch } from 'vue'
import { Back } from '@element-plus/icons-vue'
import { tt } from '@/i18n'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 被点击的父工序（根节点） */
  parentOp: { type: Object, default: null },
  /** 该线路（工单+成品行）的全部工序行，用于按 parentRowId 归组子工序 */
  allOps: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue', 'open-panel'])

const visible = computed({ get: () => props.modelValue, set: (v) => emit('update:modelValue', v) })

/** 下钻路径（不含根父工序）；最后一项 = 当前展示层的父节点 */
const path = ref([])
const rootOp = computed(() => props.parentOp)
const currentNode = computed(() => (path.value.length ? path.value[path.value.length - 1] : rootOp.value))
const children = computed(() => childrenOf(currentNode.value))
const crumbText = computed(() => [rootOp.value?.operationName, ...path.value.map(o => o.operationName)].filter(Boolean).join(' › '))
const dlgTitle = computed(() => `${tt('子工序')} · ${rootOp.value?.operationName || ''}`)

function idOf (op) {
  if (!op) return null
  return op.operationRowId != null ? op.operationRowId : op.id
}
/** 子工序 = 本线路内 parentRowId == 当前节点 id（排除自引用） */
function childrenOf (parent) {
  const pid = idOf(parent)
  if (pid == null) return []
  return props.allOps.filter(o => idOf(o) != null && Number(o.parentRowId) === Number(pid) && idOf(o) !== Number(pid))
}
function childCountOf (op) { return Math.max(0, num(op && op.childCount)) }

/** 点节点：有下级 → 下钻；无下级 → 打开该子工序操作面板并关闭弹窗 */
function openNode (op) {
  if (childCountOf(op) > 0) {
    path.value = [...path.value, op]
    return
  }
  emit('open-panel', op)
  visible.value = false
}
function goBack () { path.value = path.value.slice(0, -1) }
function reset () { path.value = [] }
watch(() => props.modelValue, (v) => { if (v) reset() })
function onClosed () { reset() }

// ===== 圆环口径（与 OperationProgressLines 逐条一致：三色 / 百分比 / 超期 / 关键工序） =====
const RUN_STATES = ['已开工', '进行中', '暂停', '完工待检']
const RING_HEX = { idle: '#cbd2dc', run: '#2f8bf5', over: '#ef4444' }

function num (v) {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}
function clampPct (v) { return Math.max(0, Math.min(100, num(v))) }
function isDone (op) { return !!op && op.operationStatus === '已完工' }
function isCanceled (op) { return !!op && op.operationStatus === '已取消' }
function isOverdue (op) { return !!op && num(op.overdueDays) > 0 && !isDone(op) && !isCanceled(op) }

function clsOf (op) {
  if (isOverdue(op)) return 'is-over'
  if (isDone(op)) return 'is-done'
  if (op && RUN_STATES.includes(op.operationStatus)) return 'is-run'
  return 'is-idle'
}
function hexOf (op) {
  const c = clsOf(op)
  if (c === 'is-over') return RING_HEX.over
  if (c === 'is-idle') return RING_HEX.idle
  return RING_HEX.run
}
function pctOf (op) {
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
</script>

<style scoped lang="scss">
.ocd { display: flex; flex-direction: column; gap: 12px; }

.ocd-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  .ocd-crumb { font-size: 12px; color: #6b7280; }
}

.ocd-chain {
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
  padding: 6px 0;
}

/* 环间短线：宽 16px、与圆心同一水平线；颜色跟随左侧工序环色 */
.ocd-link {
  flex: 0 0 16px;
  width: 16px;
  height: 2px;
  border-radius: 1px;
  margin-top: 13px;
  opacity: .9;
}

.ocd-node {
  flex: 0 0 auto;
  width: 56px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  cursor: pointer;

  &:hover .ocd-ring { transform: translateY(-2px); }
  &:hover .ocd-cap { color: #4f46e5; }
}

.ocd-ring {
  position: relative;
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

.ocd-pct {
  font-size: 10px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: #9aa3b2;
  .is-run & { color: #2f8bf5; }
  .is-over & { color: #ef4444; }
}

.ocd-chk {
  width: 9px;
  height: 5px;
  margin-top: -3px;
  border-left: 2px solid #2f8bf5;
  border-bottom: 2px solid #2f8bf5;
  border-radius: 1px;
  transform: rotate(-45deg);
}

/* 父工序「总·N」角标（右上角） */
.ocd-badge {
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

.ocd-star {
  position: absolute;
  top: -7px;
  left: -6px;
  font-size: 9px;
  line-height: 1;
  color: #f59e0b;
  text-shadow: 0 0 2px #fff;
}

.ocd-cap {
  max-width: 56px;
  font-size: 11px;
  line-height: 1.1;
  color: #6b7280;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ocd-empty { padding: 12px 0; font-size: 12px; color: #9ca3af; }
.ocd-tip { font-size: 11px; color: #9ca3af; }
</style>
