import { ref, computed } from 'vue'
import { listAllPaymentTerm, savePaymentTerm, disablePaymentTerm, enablePaymentTerm } from '@/api/basedata/payment-term'

/**
 * 付款条件的**唯一一套**读写逻辑（档案页与单据编辑页共用，禁止各页自行调接口再写一份）。
 *
 * 口径（2026-09-28 用户确认）：
 *  - 数据只有一张表、一对接口；基础资料与单据共用同一套；
 *  - 新建：只填「名称 + 账期天数」（编码由后端按编号规则自动生成）；
 *  - 删除 = **停用**（status='停用'）：停用后不再出现在下拉，但历史单据/档案仍显示原值，保证"单据找得到源头"。
 *
 * 关于缓存的坑（历史教训）：模块级缓存不能缓存"空值"跨用户复用；这里在登出/切换身份时由
 * sessionCacheReset 调 clearPaymentTermCache() 清掉（见 utils/sessionCacheReset.js）。
 */
const terms = ref([])
const loading = ref(false)
let loadingPromise = null

export function clearPaymentTermCache () {
  terms.value = []
  loadingPromise = null
}

/** 是否"未停用"：后端停用写的是 status='停用'（字符串），历史数据 status 可能为 null/1 → 都视为启用 */
function isActiveTerm (t) {
  const s = t && t.status != null ? String(t.status) : ''
  return s !== '停用' && s !== '0' && s.toLowerCase() !== 'disabled'
}

export function usePaymentTermOptions () {
  async function loadTerms (force) {
    if (loadingPromise && !force) return loadingPromise
    loading.value = true
    loadingPromise = (async () => {
      try {
        const res = await listAllPaymentTerm()
        const arr = Array.isArray(res) ? res : (res?.records || res?.list || [])
        terms.value = arr
        return arr
      } finally {
        loading.value = false
        loadingPromise = null
      }
    })()
    return loadingPromise
  }

  /** 下拉可选项（仅未停用） */
  const activeOptions = computed(() => terms.value
    .filter(isActiveTerm)
    .map(t => ({
      value: t.paymentTermId,
      label: t.paymentTermName,
      days: t.paymentDays,
      discountRate: t.discountRate,
      advanceRate: t.advanceRate,
      raw: t
    })))

  /** 按主键取（含已停用 → 供历史单据回显） */
  function termById (id) {
    if (id === null || id === undefined) return null
    return terms.value.find(t => String(t.paymentTermId) === String(id)) || null
  }

  /**
   * 新建付款条件（编码自动生成）。
   * @param {{name: string, paymentDays: number}} payload
   * @returns {Promise<object|null>} 新建后的付款条件
   */
  async function createTerm ({ name, paymentDays }) {
    const trimmed = String(name || '').trim()
    if (!trimmed) throw new Error('请填写付款条件名称')
    const days = Number(paymentDays) || 0

    // 已存在"同名 + 同账期"的未停用项 → 直接复用，不再造重复数据（脏数据就是这么来的）
    const dup = terms.value.find(t => isActiveTerm(t)
      && String(t.paymentTermName || '').trim() === trimmed
      && (Number(t.paymentDays) || 0) === days)
    if (dup) return { ...dup, __reused: true }

    await savePaymentTerm({
      paymentTermName: trimmed,
      paymentDays: days,
      discountDays: 0,
      discountRate: 0,
      advanceRate: 0
    })
    const list = await loadTerms(true)
    // 取最新一条（主键最大）：历史库里可能存在同名行，按名称取到旧行会导致选中错的主键
    const sameName = list.filter(t => String(t.paymentTermName || '').trim() === trimmed)
    sameName.sort((a, b) => Number(b.paymentTermId) - Number(a.paymentTermId))
    return sameName[0] || null
  }

  /** 停用（= 下拉里"删除"的语义）：历史单据仍显示原值 */
  async function disableTerm (id) {
    await disablePaymentTerm(id)
    await loadTerms(true)
  }

  /** 启用（恢复可选） */
  async function enableTerm (id) {
    await enablePaymentTerm(id)
    await loadTerms(true)
  }

  return { terms, loading, loadTerms, activeOptions, termById, createTerm, disableTerm, enableTerm, isActiveTerm }
}
