/**
 * 路由 → 字段权限页面键（= 目标表名）的**纯函数模块**（无任何 import/别名依赖）。
 *
 * 【为什么单独一个文件】
 * 这段映射既要给运行时用（前端自动标注星号），也要给**发布闸门**离线校验用
 * （闸门跑在 Node 里，无法解析 '@/...' 别名）。放成纯模块 → 运行时与闸门**共享同一份逻辑**，
 * 不会出现"闸门一套、运行时另一套"的口径漂移。
 *
 * 推导规则：
 *   ① 例外表（页面名叫"入库/出库"但表名不同的少数几个）优先；
 *   ② 其余按"路径片段（kebab-case / 复数）→ snake_case 单数"生成候选；
 *   ③ 候选必须命中"已知页面键集合（后端元数据注册表）"才算成功。
 * 推导不出来 = 该页面不标注 → 由发布闸门报错（不允许靠人肉发现）。
 */

export const PATH_PAGE_KEY_EXCEPTIONS = {
  // 基础资料：页面名与表名不同
  '/basedata/unit': 'unit_of_measure',
  '/basedata/settle': 'settlement_mode',
  // 单据：页面名（模块习惯叫法）与目标表名不同
  '/purchase/inbounds': 'purchase_receipt',        // 采购入库单 → purchase_receipt
  '/sale/deliveries': 'sale_delivery',
  '/inventory/other-receipts': 'other_receipt',
  '/inventory/other-issues': 'other_issue',
  '/inventory/stock-transfers': 'stock_transfer',
  '/inventory/stock-checks': 'stock_check',
  '/inventory/material-requisitions': 'material_requisition',
  '/production/workorders': 'production_order',    // 生产工单 → production_order
  '/production/receipts': 'production_receipt',
  '/production/returns': 'material_requisition',   // 生产退料单与领料单共表
  '/production/supplements': 'material_requisition', // 生产补料单与领料单共表
  '/production/operation-reports': 'operation_report',

  '/subcontract/orders': 'subcontract_order',
  '/subcontract/picks': 'subcontract_pick',
  '/subcontract/inbounds': 'subcontract_inbound',
  '/subcontract/returns': 'subcontract_return',
  '/subcontract/supplements': 'subcontract_supplement',
  '/sample': 'sample_order',                       // 打样单
  '/freesample': 'free_sample_order',              // 免费样品单
  '/finance/receipt': 'cash_receipt',              // 收款单
  '/finance/payment': 'cash_payment'               // 付款单
}

/**
 * @param {string} path 路由路径（如 /sale/orders/123）
 * @param {Set<string>|string[]} [knownKeys] 后端注册表已知页面键（不传=只按规则推导，不做有效性校验）
 * @returns {string|null}
 */
export function resolveFieldPageKey (path, knownKeys) {
  if (!path) return null
  const clean = String(path).split('?')[0]
  const known = knownKeys ? new Set(knownKeys) : null

  // ① 例外表：最长前缀匹配
  const hits = Object.keys(PATH_PAGE_KEY_EXCEPTIONS)
    .filter(p => clean === p || clean.startsWith(p + '/'))
    .sort((a, b) => b.length - a.length)
  if (hits.length) {
    const key = PATH_PAGE_KEY_EXCEPTIONS[hits[0]]
    if (!known || known.has(key)) return key
  }

  // ② 自动推导
  const segs = clean.split('/').filter(Boolean)
  const candidates = []
  for (const seg of segs) {
    if (['edit', 'new', 'add'].includes(seg)) continue
    const snake = seg.replace(/-/g, '_')
    candidates.push(snake)
    if (snake.endsWith('ies')) candidates.push(snake.slice(0, -3) + 'y')
    if (snake.endsWith('s')) candidates.push(snake.slice(0, -1))
  }
  if (segs.length >= 2) {
    const mod = segs[0].replace(/-/g, '_')
    for (const c of [...candidates]) candidates.push(mod + '_' + c)
  }
  for (const c of candidates) {
    if (known && known.has(c)) return c
  }
  return null
}
