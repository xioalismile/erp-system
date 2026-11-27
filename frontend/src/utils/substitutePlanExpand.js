// 销售替换发货指示 → 出库明细展开（2026-11-27）
// 输入：pushRelations / SourcePicker 映射后的出库行（行上带 substitutePlans）
// 规则：替代优先。本次发货数量先满足指示待执行量，剩余才是原货品。
// 输出：新数组，不修改源行对象；替代行带 replacePlanId / replaced_from_*。

function num(v) {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

function planPending(plan) {
  if (plan == null) return 0
  if (plan.pendingQuantity != null) return Math.max(0, num(plan.pendingQuantity))
  return Math.max(0, num(plan.quantity) - num(plan.turnedDeliveryQuantity))
}

function activePlans(row) {
  const list = Array.isArray(row && row.substitutePlans) ? row.substitutePlans : []
  return list.filter(p => p && (p.status == null || p.status === '有效') && planPending(p) > 0)
}

function makeSubstituteRow(row, plan, qty) {
  const originId = row.sourceMaterialId != null ? row.sourceMaterialId : row.materialId
  const originCode = row.sourceMaterialCode != null ? row.sourceMaterialCode : row.materialCode
  const originName = row.sourceMaterialName != null ? row.sourceMaterialName : row.materialName
  return {
    ...row,
    materialId: plan.substituteMaterialId,
    materialCode: plan.substituteMaterialCode || '',
    materialName: plan.substituteMaterialName || '',
    specification: plan.specification || '',
    materialMainImage: plan.mainImage || '',
    quantity: qty,
    unitPrice: plan.unitPrice != null ? num(plan.unitPrice) : row.unitPrice,
    replacedFromMaterialId: originId,
    replacedFromMaterialCode: originCode,
    replacedFromMaterialName: originName,
    replacePlanId: plan.planId,
    // 仓库/货位/批次由仓库按替代品重新选择
    stockId: null,
    binId: null,
    batchNo: null,
    serialNo: null,
    productionDate: null,
    expiryDate: null,
    substitutePlans: []
  }
}

/**
 * @param {Array} rows 出库明细行
 * @returns {Array} 展开后的明细行（原货品行 + 替代行）
 */
export function expandSubstitutePlans(rows) {
  if (!Array.isArray(rows) || rows.length === 0) return rows || []
  const out = []
  for (const row of rows) {
    if (!row) continue
    const plans = activePlans(row)
    let remaining = num(row.quantity)
    if (plans.length === 0 || remaining <= 0) {
      out.push({ ...row, substitutePlans: [] })
      continue
    }
    for (const plan of plans) {
      if (remaining <= 0) break
      const take = Math.min(planPending(plan), remaining)
      if (take <= 0) continue
      out.push(makeSubstituteRow(row, plan, take))
      remaining -= take
    }
    if (remaining > 0) {
      out.push({ ...row, quantity: remaining, substitutePlans: [] })
    }
  }
  return out
}
