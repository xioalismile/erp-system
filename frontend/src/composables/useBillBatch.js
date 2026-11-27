/**
 * 列表页批量操作通用执行器（2026-09-19）
 *
 * 设计口径（与《列表页批量操作通用组件_立项登记表》一致）：
 *  1. **串行逐条**执行（不做并发）：失败原因能精确落到每一条，服务端压力可控；
 *  2. **单次上限 200 条**：超限直接拒绝并提示分批（防误操作、防打爆服务）；
 *  3. **逐条独立**：一条失败不影响其余，不做整体回滚（业务上"部分成功"比"全回滚"更符合预期）；
 *  4. **失败原因**取后端 message（BusinessException 的中文提示，如"只有草稿状态可删除…"）；
 *  5. **跳过**（skipReason 返回非空字符串）不计入失败，单独展示原因（如"已在审批中，无需重复提交"）。
 */
import { ref } from 'vue'
import { tt } from '@/i18n'

/** 单次批量上限（写死：安全阈值，不做用户配置） */
export const MAX_BATCH_SIZE = 200

export function useBillBatch() {
  const running = ref(false)
  const progress = ref({ done: 0, total: 0 })
  /** 执行结果：{ actionLabel, ok:[{row}], fail:[{row,message}], skip:[{row,reason}] } */
  const result = ref(null)

  /**
   * 执行一批批量操作。
   *
   * @param {Object}   opts
   * @param {string}   opts.actionLabel 动作名（用于确认框/结果面板标题，如"批量提交"）
   * @param {Array}    opts.rows        选中的行
   * @param {Function} opts.exec        单条执行函数（row) => Promise，通常是 api/xxx 里的单条接口
   * @param {Function} [opts.skipReason] (row) => string，返回非空表示该条跳过并给出原因
   * @returns {Promise<Object|null>} 结果对象；未选中返回 null
   */
  async function runBatch({ actionLabel, rows, exec, skipReason }) {
    const list = Array.isArray(rows) ? rows.slice() : []
    if (list.length === 0) return null
    if (list.length > MAX_BATCH_SIZE) {
      throw new Error(
        tt('一次最多批量处理 ') + MAX_BATCH_SIZE + tt(' 条，当前已选 ') + list.length + tt(' 条，请分批操作')
      )
    }
    running.value = true
    progress.value = { done: 0, total: list.length }
    const ok = []
    const fail = []
    const skip = []
    try {
      for (const row of list) {
        let reason = ''
        try {
          reason = skipReason ? String(skipReason(row) || '') : ''
        } catch (e) {
          reason = ''
        }
        if (reason) {
          skip.push({ row, reason })
          progress.value.done++
          continue
        }
        try {
          await exec(row)
          ok.push({ row })
        } catch (e) {
          fail.push({ row, message: (e && e.message) ? e.message : tt('未知错误') })
        }
        progress.value.done++
      }
    } finally {
      running.value = false
    }
    result.value = { actionLabel, ok, fail, skip }
    return result.value
  }

  /** 清空上一次结果（关闭结果面板时调用） */
  function clearResult() {
    result.value = null
    progress.value = { done: 0, total: 0 }
  }

  /**
   * 失败清单导出成 CSV（前端生成，不占后端接口）；失败清单通常只有几条，无需 xlsx。
   */
  function exportFailList(fileName) {
    const r = result.value
    if (!r || r.fail.length === 0) return
    const lines = [tt('序号,单据标识,失败原因')]
    r.fail.forEach((f, i) => {
      const label = String(f.label || f.id || i + 1).replace(/,/g, ' ')
      const msg = String(f.message || '').replace(/,/g, ' ').replace(/\r?\n/g, ' ')
      lines.push(`${i + 1},${label},${msg}`)
    })
    const csv = '\uFEFF' + lines.join('\r\n')
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = (fileName || tt('批量操作失败清单')) + '.csv'
    a.click()
    URL.revokeObjectURL(a.href)
  }

  return { running, progress, result, runBatch, clearResult, exportFailList, MAX_BATCH_SIZE }
}
