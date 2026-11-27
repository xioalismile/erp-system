import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { getPageLayout, savePageLayout, previewCard } from '@/api/board/customBoard'

/** 换数据源后挑一个「有业务意义」的数值字段：优先金额/数量，避免默认落到 订单ID 这类主键上（口径会变成没意义的值） */
const NUMERIC_PREFER = [
  'amount2', 'amount', 'totalAmount', 'netAmount', 'taxAmount', 'amountWithTax', 'totalWithTax',
  'quantity', 'qty', 'price', 'unitPrice', 'cost', 'totalPrice'
]
const isIdLike = field => /(^id$|Id$|_id$)/.test(String(field || ''))

/** 从字段清单里挑默认数值字段（主表 + 已选关联单一起挑） */
export function pickNumericField(fields) {
  const list = Array.isArray(fields) ? fields : []
  for (const k of NUMERIC_PREFER) {
    const hit = list.find(f => f.numeric && f.field === k)
    if (hit) return hit.field
  }
  const good = list.filter(f => f.numeric && !isIdLike(f.field))
  if (good.length) return good[0].field
  const any = list.filter(f => f.numeric)
  return any.length ? any[0].field : ''
}

/** 现有看板页（财务 / 销售 / 仓储 / 经营）的卡片式布局
 *
 *  每登录人每页一份配置，落库在「自定义看板」同一张记录里（页面键区分）。
 *  配置项统一结构：
 *    { id, builtin, hidden, size, order, title, override, card }
 *  - builtin=true  ：页面原有区块，只记显隐 / 尺寸 / 顺序 / 是否改用自己的取数
 *  - override=true ：该原有区块改用自己的数据源与公式取数（card 为卡片配置）
 *  - builtin=false ：新增的自定义卡片（card 为完整卡片配置）
 *
 *  未做过任何配置时（首访），items 等于区块默认清单 → 页面与改造前完全一致。
 */
export function useBoardLayout(pageKey, builtins) {
  const items = ref(defaults())
  const dataMap = ref({})
  const saving = ref(false)
  const period = ref('month')

  function defaults() {
    return (builtins || []).map((b, i) => ({
      id: b.id,
      builtin: true,
      hidden: false,
      size: b.size || 6,
      order: i,
      title: b.title,
      override: false,
      card: null
    }))
  }

  function itemOf(id) {
    return items.value.find(it => it.id === id) || null
  }

  function merge(saved) {
    const byId = new Map()
    ;(saved || []).forEach(c => { if (c && c.id) byId.set(String(c.id), c) })
    const base = (builtins || []).map((b, i) => {
      const s = byId.get(b.id)
      if (!s) {
        return { id: b.id, builtin: true, hidden: false, size: b.size || 6, title: b.title, override: false, card: null, order: i }
      }
      return {
        id: b.id,
        builtin: true,
        hidden: !!s.hidden,
        size: Number(s.size) || b.size || 6,
        title: s.title || b.title,
        override: !!s.override,
        card: s.card || null,
        order: Number.isFinite(Number(s.order)) ? Number(s.order) : i
      }
    })
    const customs = (saved || [])
      .filter(c => c && !c.builtin && c.type)
      .map((c, i) => ({
        id: c.id,
        builtin: false,
        hidden: !!c.hidden,
        size: Number(c.size) || 6,
        title: c.title || '未命名卡片',
        card: c,
        override: false,
        order: Number.isFinite(Number(c.order)) ? Number(c.order) : (builtins || []).length + i
      }))
    items.value = [...base, ...customs]
      .sort((a, b) => a.order - b.order)
      .map((it, i) => ({ ...it, order: i }))
  }

  /** 读取布局并取数（首访无记录 = 默认布局） */
  async function load(p) {
    if (p) period.value = p
    try {
      const res = await getPageLayout(pageKey)
      merge(res && Array.isArray(res.cards) ? res.cards : [])
    } catch (e) {
      merge([])
    }
    await refreshData()
  }

  // ---------- 供页面模板判断 ----------
  function show(id) {
    const it = itemOf(id)
    return !it || !it.hidden
  }
  function sizeOf(id, dflt) {
    const it = itemOf(id)
    return it && Number(it.size) ? Number(it.size) : (dflt || 6)
  }
  function xsOf(id, dflt) {
    return sizeOf(id, dflt) >= 12 ? 24 : 12
  }
  function isOverride(id) {
    const it = itemOf(id)
    return !!(it && it.builtin && it.override && it.card && it.card.type)
  }
  function cardCfg(id) {
    const it = itemOf(id)
    return it && it.card ? it.card : {}
  }
  function dataOf(id) {
    const m = dataMap.value[id]
    return (m && m.data) || {}
  }
  function errOf(id) {
    const m = dataMap.value[id]
    return (m && m.error) || ''
  }
  const customItems = computed(() => items.value.filter(it => !it.builtin && it.card && it.card.type))

  // ---------- 编辑 ----------
  function move(id, dir) {
    const idx = items.value.findIndex(it => it.id === id)
    const to = idx + dir
    if (idx < 0 || to < 0 || to >= items.value.length) return
    const list = [...items.value]
    const [m] = list.splice(idx, 1)
    list.splice(to, 0, m)
    items.value = list.map((it, i) => ({ ...it, order: i }))
  }
  function removeItem(id) {
    items.value = items.value.filter(it => it.id !== id)
  }
  /** 新增自定义卡片（已含 type / source 等完整配置） */
  function addCustomCard(card) {
    if (!card || !card.type) return
    const c = { ...card, id: card.id || ('c' + Date.now() + Math.floor(Math.random() * 1000)) }
    items.value = [...items.value, {
      id: c.id, builtin: false, hidden: false, size: Number(c.size) || 6,
      order: items.value.length, title: c.title || '未命名卡片', override: false, card: c
    }].map((it, i) => ({ ...it, order: i }))
  }

  // ---------- 取数 ----------
  function needsData(it) {
    return !!((it.builtin && it.override && it.card && it.card.type) || (!it.builtin && it.card && it.card.type))
  }
  async function refreshData(p) {
    if (p) period.value = p
    const list = items.value.filter(needsData)
    if (!list.length) {
      dataMap.value = {}
      return
    }
    const map = { ...dataMap.value }
    await Promise.all(list.map(async it => {
      try {
        const res = await previewCard(it.card, period.value)
        map[it.id] = { data: res || {}, error: '' }
      } catch (e) {
        map[it.id] = { data: {}, error: e?.message || '数据暂时无法获取' }
      }
    }))
    dataMap.value = map
  }

  // ---------- 保存 / 恢复默认 ----------
  function payload() {
    return items.value.map((it, i) => {
      if (it.builtin) {
        const o = { id: it.id, builtin: true, title: it.title, size: it.size, order: i, hidden: !!it.hidden }
        if (it.override && it.card && it.card.type) {
          o.override = true
          o.card = it.card
        }
        return o
      }
      const c = { ...it.card, title: it.title, size: it.size, order: i }
      if (it.hidden) c.hidden = true
      else delete c.hidden
      return c
    })
  }
  async function save() {
    saving.value = true
    try {
      await savePageLayout(pageKey, payload())
      await refreshData()
      ElMessage.success('看板设置已保存：刷新、重开、重新登录后均保持')
      return true
    } catch (e) {
      ElMessage.error(e?.message || '保存失败')
      return false
    } finally {
      saving.value = false
    }
  }
  async function reset() {
    items.value = defaults()
    dataMap.value = {}
    saving.value = true
    try {
      await savePageLayout(pageKey, [])
      ElMessage.success('已恢复默认布局')
    } catch (e) {
      ElMessage.error(e?.message || '恢复默认失败')
    } finally {
      saving.value = false
    }
  }

  return {
    items,
    customItems,
    saving,
    period,
    load,
    show,
    sizeOf,
    xsOf,
    isOverride,
    cardCfg,
    dataOf,
    errOf,
    move,
    removeItem,
    addCustomCard,
    refreshData,
    save,
    reset
  }
}
