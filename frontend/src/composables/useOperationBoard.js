/**
 * 车间看板公共逻辑（车间排程看板 + 车间进度跟踪 两页共用，禁止各写一套）
 *
 * 抽出来的东西（守则：先找现成、不重复造轮子；两页共用代码抽共享位置）：
 *   ① 状态色 —— 复用 utils/operationStatus 的 opStatusColor（全站唯一一份 8 值配色）
 *   ② 单据跳转 —— 复用 utils/billRoute 的 resolveBillRoute（全站唯一一份跳转映射）
 *   ③ 工序操作面板 —— 见下方 useOperationPanel（点站点/块打开面板的打开态逻辑）
 *   ④ 进度环与健康度计算（数量/入库/金额/父工序汇总环）
 *
 * 纯函数，无副作用；在 setup 中调用一次即可。
 */
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { opStatusColor } from '@/utils/operationStatus'
import { resolveBillRoute } from '@/utils/billRoute'
import { tt } from '@/i18n'

/** 进度环半径与周长（两个视图共用同一尺寸，避免同一工序在两页环大小不一致） */
export const RING_R = 18
export const RING_CIRC = +(2 * Math.PI * RING_R).toFixed(1)

export function useOperationBoard () {
  const router = useRouter()

  function num (v) {
    const n = Number(v)
    return Number.isFinite(n) ? n : 0
  }
  function clampPct (v) {
    return Math.max(0, Math.min(100, num(v)))
  }

  /** 状态主色（工序 8 状态） */
  function opColor (op) { return opStatusColor(op && op.operationStatus) }
  /** 是否「在做」（呼吸动效） */
  function isLive (op) {
    const s = op && op.operationStatus
    return s === '已开工' || s === '进行中'
  }
  /** 站点间连线：上一道状态色 → 本道状态色 渐变 */
  function connectorStyle (prev, cur) {
    return { background: `linear-gradient(90deg, ${opColor(prev)}, ${opColor(cur)})` }
  }

  /** 是否画进度环（数量/入库/金额类型且有计划值，或父工序） */
  function hasRing (op) {
    if (!op) return false
    if (num(op.childCount) > 0) return true
    switch (op.operationType) {
      case '数量类型': return num(op.planQuantity) > 0
      case '入库类型': return num(op.outputQuantity) > 0
      case '金额类型': return num(op.planAmount) > 0
      default: return false
    }
  }
  /** 完成百分比（父工序 = 子项加权完工率） */
  function progressPercent (op) {
    if (!op) return 0
    if (num(op.childCount) > 0) return clampPct(op.childProgress)
    const plan = num(op.planQuantity)
    switch (op.operationType) {
      case '数量类型':
        return plan > 0 ? clampPct(num(op.reportedQuantity) / plan * 100) : 0
      case '入库类型': {
        const out = num(op.outputQuantity)
        return out > 0 ? clampPct(num(op.receivedQty) / out * 100) : 0
      }
      case '金额类型': {
        const pl = num(op.planAmount)
        return pl > 0 ? clampPct(num(op.confirmedAmount) / pl * 100) : 0
      }
      default:
        return 0
    }
  }
  function ringText (op) { return `${Math.round(progressPercent(op))}%` }
  function ringOffset (op) {
    return +(RING_CIRC * (1 - progressPercent(op) / 100)).toFixed(1)
  }

  /** 距计划完工还剩几天（终态返回 null） */
  function daysLeft (op) {
    if (!op || !op.planEndDate) return null
    const s = op.operationStatus
    if (s === '已完工' || s === '已取消') return null
    const d = new Date(String(op.planEndDate).substring(0, 10) + 'T00:00:00')
    if (Number.isNaN(d.getTime())) return null
    const today = new Date()
    today.setHours(0, 0, 0, 0)
    return Math.round((d.getTime() - today.getTime()) / 86400000)
  }
  /** 健康度色（环色）：超期红 / 临期橙 / 计划内绿 */
  function healthColor (op) {
    if (num(op && op.overdueDays) > 0) return '#ef4444'
    const left = daysLeft(op)
    if (left !== null && left <= 2) return '#f59e0b'
    return '#10b981'
  }
  function healthLabel (op) {
    if (num(op && op.overdueDays) > 0) return '超期'
    const left = daysLeft(op)
    if (left === null) return '未排期'
    return left <= 2 ? '临期' : '计划内'
  }

  // ---- 线路（一条线 = 一个工单 + 成品行）取数兜底 ----
  function orderIdOf (g) {
    if (!g) return null
    if (g.orderId != null) return g.orderId
    return (g.ops && g.ops[0] && g.ops[0].orderId != null) ? g.ops[0].orderId : null
  }
  function orderNoOf (g) {
    if (!g) return ''
    return g.orderNo || (g.ops && g.ops[0] && g.ops[0].orderNo) || ''
  }
  function materialOf (g) {
    if (!g) return ''
    return g.productName || (g.ops && g.ops[0] && g.ops[0].materialName) || ''
  }
  /** 打开该线路所属工单的编辑页（走全站统一跳转映射） */
  function gotoOrder (orderId) {
    const route = resolveBillRoute('PRODUCTION_ORDER', orderId)
    if (!route) {
      ElMessage.warning(tt('该工单没有可打开的详情页'))
      return
    }
    router.push(route)
  }

  return {
    num, clampPct,
    opColor, isLive, connectorStyle,
    hasRing, progressPercent, ringText, ringOffset,
    daysLeft, healthColor, healthLabel,
    orderIdOf, orderNoOf, materialOf, gotoOrder
  }
}

/**
 * 工序操作面板打开态（两页共用；点站点/块即打开）。
 * @param {Function} onChanged 面板里动作成功后回调（一般是重新拉数据）
 */
export function useOperationPanel (onChanged) {
  const panelVisible = ref(false)
  const currentOp = ref(null)

  function openPanel (op) {
    const id = op.operationRowId != null ? op.operationRowId : op.id
    currentOp.value = { ...op, id }
    panelVisible.value = true
  }
  function handleChanged () {
    if (typeof onChanged === 'function') onChanged()
  }

  return { panelVisible, currentOp, openPanel, onChanged: handleChanged }
}
