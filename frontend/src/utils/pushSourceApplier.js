// ====================================================================
// 下推源单应用工具 - 在下游单据 Edit 页 onMounted 时调用
// 自动检测路由 query.srcType/srcId, 拉取源单详情并按映射规则填充表单
// 用法:
//   import { applyPushSource } from '@/utils/pushSourceApplier'
//   onMounted(async () => {
//     await loadDropdowns()
//     await loadDetail()
//     const applied = await applyPushSource(route, 'SaleOrder', form, (items) => { form.items = items })
//     if (applied) calcTotal()
//   })
// 第 5 个参数 itemsField（可选，默认 'items'）：源单「明细数组」所在字段名
//   生产工单详情返回的是 materials / products，没有 items，需要显式传入
// ====================================================================
import { getRelation } from '@/utils/pushRelations'
import { getGetApi } from '@/utils/pushApiRegistry'
import { listEnabledDefinition, getFieldValues, batchGetFieldValues } from '@/api/system/customField'
import { copyCustomFieldValues } from '@/composables/useCustomField'
import { expandSubstitutePlans } from '@/utils/substitutePlanExpand'

// 2026-11-14：下推带自定义字段（表头＋明细）
// 口径：源单与目标单都按设置中心的实体标识取字段定义与值，字段标识相同直接复制；
//       标识不同但字段名相同且类型一致时也复制（与「复制单据」同一套 copyCustomFieldValues）。
// 目标已有值（页面默认值/用户手填）不覆盖；任一环节失败静默降级，不影响下推本身。
const CF_ENTITY = {
  SaleQuote: { header: 'sale_quote', item: 'sale_quote_item' },
  SaleContract: { header: 'sale_contract', item: 'sale_contract_item' },
  SaleOrder: { header: 'sale_order', item: 'sale_order_item' },
  SaleDelivery: { header: 'sale_delivery', item: 'sale_delivery_item' },
  SaleReturn: { header: 'sale_return', item: 'sale_return_item' },
  SaleInvoice: { header: 'sale_invoice', item: 'sale_invoice_item' },
  PurchaseRequest: { header: 'purchase_request', item: 'purchase_request_item' },
  PurchaseContract: { header: 'purchase_contract', item: 'purchase_contract_item' },
  PurchaseOrder: { header: 'purchase_order', item: 'purchase_order_item' },
  PurchaseInbound: { header: 'purchase_inbound', item: 'purchase_inbound_item' },
  PurchaseReturn: { header: 'purchase_return', item: 'purchase_return_item' },
  PurchaseInvoice: { header: 'purchase_invoice', item: 'purchase_invoice_item' },
  StockTransfer: { header: 'stock_transfer', item: 'stock_transfer_item' },
  StockCheck: { header: 'stock_check', item: 'stock_check_item' },
  OtherReceipt: { header: 'other_receipt', item: 'other_receipt_item' },
  OtherIssue: { header: 'other_issue', item: 'other_issue_item' },
  MaterialRequisitions: { header: 'material_requisition', item: 'material_requisition_item' }
}

/** 明细行主键：取以 ItemId 结尾的字段（如 orderItemId / deliveryItemId） */
function itemIdOf (row) {
  const k = Object.keys(row || {}).find(x => /ItemId$/.test(x))
  return k ? row[k] : null
}

async function loadDefs (entity) {
  if (!entity) return []
  try {
    const fields = await listEnabledDefinition(entity)
    return (fields || []).filter(f => f && f.fieldKey && f.fieldType !== 'formula')
  } catch (e) {
    return []
  }
}

/**
 * 把源单自定义字段值复制到目标表单/明细行。
 * @returns 源单表头自定义字段值（供页面既有的 copyCustomFieldValues 二次使用）
 */
async function applyCustomFieldValues (sourceType, targetType, sourceId, form, itemPairs) {
  const src = CF_ENTITY[sourceType]
  const tgt = CF_ENTITY[targetType]
  if (!src || !tgt) return {}
  try {
    const [srcHeaderVals, srcHeaderDefs, srcItemDefs, tgtHeaderDefs, tgtItemDefs] = await Promise.all([
      getFieldValues(src.header, sourceId),
      loadDefs(src.header),
      loadDefs(src.item),
      loadDefs(tgt.header),
      loadDefs(tgt.item)
    ])
    if (srcHeaderVals && Object.keys(srcHeaderVals).length) {
      copyCustomFieldValues(srcHeaderVals, form, tgtHeaderDefs, srcHeaderDefs)
    }
    if (srcItemDefs.length && tgtItemDefs.length && Array.isArray(itemPairs) && itemPairs.length) {
      const ids = itemPairs.map(p => itemIdOf(p.src)).filter(v => v != null)
      if (ids.length) {
        const valMap = await batchGetFieldValues(src.item, ids) || {}
        itemPairs.forEach(({ src: srcRow, mapped }) => {
          const id = itemIdOf(srcRow)
          const vals = id != null ? valMap[id] : null
          if (vals && mapped) copyCustomFieldValues(vals, mapped, tgtItemDefs, srcItemDefs)
        })
      }
    }
    return srcHeaderVals || {}
  } catch (e) {
    console.warn('[pushSource] 自定义字段带入失败（已忽略）', e)
    return {}
  }
}

export async function applyPushSource(route, targetType, form, itemsSetter, itemsField = 'items') {
  const { srcType, srcId } = route.query
  if (!srcType || !srcId) return false

  const relation = getRelation(srcType, targetType)
  if (!relation) {
    console.warn('[pushSource] 未找到下推关系配置', srcType, '->', targetType)
    return false
  }

  const getApi = getGetApi(srcType)
  if (!getApi) {
    console.warn('[pushSource] 源单详情 API 未注册', srcType)
    return false
  }

  try {
    const fullDoc = await getApi(srcId)
    const mappedForm = relation.fieldMapper(fullDoc)
    // 将映射后的表头字段合并到 form (不覆盖 null/undefined)
    Object.keys(mappedForm).forEach(k => {
      if (mappedForm[k] !== null && mappedForm[k] !== undefined) {
        form[k] = mappedForm[k]
      }
    })
    // 计税方式随单下推（2026-10-09）：销售/采购类单据的「计税方式」必须随下推带过去，
    // 否则下游表头为空会按设置中心默认口径重算 → 含税价来源被重复计税、金额虚增。
    const TAX_FLOW_DOCS = ['SaleQuote', 'SaleContract', 'SaleOrder', 'SaleDelivery', 'SaleReturn', 'SaleInvoice',
      'PurchaseRequest', 'PurchaseContract', 'PurchaseOrder', 'PurchaseInbound', 'PurchaseReturn', 'PurchaseInvoice']
    if (TAX_FLOW_DOCS.includes(relation.sourceType) && TAX_FLOW_DOCS.includes(relation.targetType)
        && fullDoc && fullDoc.taxCalcMethod) {
      form.taxCalcMethod = fullDoc.taxCalcMethod
    }
    // 映射明细行（itemsField 未传时 = 'items'，与旧行为一致）
    const sourceItems = fullDoc[itemsField] || []
    // itemMapper 未配置的链路（pushRelations 里有 4 条 itemMapper = null）降级为「原样带过源行」：
    // 原实现在这里会抛 TypeError，被下方 try/catch 吞掉 → 表现为"静默不预填"，很难排查
    // 行备注集中补带：映射里没带 remark 的（pushRelations 多数 itemMapper 没带），
    // 用源明细的备注补上（源明细备注优先 remark，其次 rowRemark），口径与 SourcePicker.vue 一致
    const mappedPairs = sourceItems.map(it => {
      const mapped = typeof relation.itemMapper === 'function'
        ? relation.itemMapper(it, fullDoc)
        : { ...it }
      if (mapped && (mapped.remark == null || mapped.remark === '')) {
        const srcRemark = it && it.remark != null && it.remark !== ''
          ? it.remark
          : ((it && it.rowRemark) || '')
        if (srcRemark) mapped.remark = srcRemark
      }
      return { src: it, mapped }
    }).filter(p => p.mapped) // itemMapper 返回 null = 该行无可下推数量（如已全部开票），跳过不带入
    // 替换发货（2026-11-27）：销售订单→销售出库时，按行上的替换发货指示展开成 原货品行 + 替代行；
    // 自定义字段仍按源订单行复制到每个拆分行。
    let itemPairs = mappedPairs
    if (relation.sourceType === 'SaleOrder' && relation.targetType === 'SaleDelivery') {
      itemPairs = []
      for (const pair of mappedPairs) {
        for (const mapped of expandSubstitutePlans([pair.mapped])) {
          itemPairs.push({ src: pair.src, mapped })
        }
      }
    }
    const mappedItems = itemPairs.map(p => p.mapped)
    // 2026-11-14：表头＋明细自定义字段按「字段标识 → 字段名」复制（失败静默降级）
    const sourceHeaderCf = await applyCustomFieldValues(relation.sourceType, relation.targetType, srcId, form, itemPairs)
    itemsSetter(mappedItems, sourceHeaderCf)
    return true
  } catch (e) {
    console.error('[pushSource] 应用源单数据失败', e)
    return false
  }
}
