import { onActivated } from 'vue'

/**
 * 编辑页下拉基础数据自动刷新（切回标签页时生效）：
 *
 * 背景：编辑页被 keep-alive 缓存，物料/客户等下拉数据是首次打开时的快照；
 * 用户在档案里改过数据后切回旧标签页，下拉仍显示旧值（与档案对不上）。
 *
 * 与 useAutoRefresh 的区别：
 * - useAutoRefresh 用于列表页（重拉表格数据）
 * - 本 composable 用于编辑页，loadFn 必须只刷新下拉基础数据（物料/客户/仓库等），
 *   绝不能重新加载表单本身，否则会覆盖用户未保存的草稿。
 *
 * 用法（编辑页 script setup 中）：
 *   async function loadBaseData() { ... 重拉物料/客户等下拉数据，不含表单 ... }
 *   useBaseDataRefresh(loadBaseData)
 *
 * @param {Function} loadFn 下拉基础数据加载函数（不得触碰表单数据）
 */
export function useBaseDataRefresh(loadFn) {
  if (typeof loadFn !== 'function') return
  let firstActivate = true
  onActivated(() => {
    // 首次激活紧跟 onMounted（数据刚加载过），跳过避免重复请求
    if (firstActivate) {
      firstActivate = false
      return
    }
    try {
      Promise.resolve(loadFn()).catch(() => {})
    } catch (_) {
      // 刷新失败不打断用户操作
    }
  })
}
