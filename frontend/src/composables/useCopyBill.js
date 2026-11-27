/**
 * 单据复制通用逻辑
 *
 * 用法：
 *   const { applyCopy } = useCopyBill({
 *     route,                                  // useRoute() 返回的路由对象
 *     loadFn: getSaleContract,                // 加载详情的接口函数 (id) => Promise<data>
 *     form,                                   // 响应式表单对象（reactive）
 *     idField: 'contractId',                  // 表头主键字段名（不传则自动检测）
 *     noField: 'contractNo',                  // 单号字段名（不传则自动检测）
 *     itemIdField: 'contractItemId',          // 明细主键字段名（不传则用通用列表）
 *     itemsField: 'items',                    // 明细数组字段名，默认 'items'
 *     afterCopy: (data) => calcTotal()        // 复制完成后的回调（可选），用于重算金额等
 *   })
 *
 * 在 onMounted 中：
 *   const copied = await applyCopy()
 *   if (!copied) {
 *     await loadDetail()   // 非复制模式，走正常加载逻辑
 *   }
 *
 * 边界（2026-10-13 口径：复制 = 百分百同步上一张单，用户确认）：
 * - 仅当 route.query.copy 存在时执行
 * - 深拷贝源单数据，避免引用联动
 * - 清空（唯一不继承项）：表头主键、明细主键、唯一编号（单号/税票号码——同号即假数据）、
 *   状态(重置草稿)、审批状态、结案状态、审计时间（系统记录，非业务内容）
 * - 随复制同步：附件URL、来源单信息（表头+明细）、全部业务字段、自定义字段值
 */
import { ElMessage } from 'element-plus'
import { tt } from '@/i18n'
import { loadCustomFieldValues, batchLoadCustomFieldValues } from '@/composables/useCustomField'

// 通用明细主键字段名，用于自动清空
const COMMON_ITEM_ID_FIELDS = ['id', 'itemId', 'detailId', 'lineId', 'contractItemId', 'orderItemId', 'receiptItemId', 'returnItemId', 'invoiceItemId', 'deliveryItemId', 'quoteItemId', 'requestItemId', 'transferItemId', 'checkItemId', 'requisitionItemId', 'sampleItemId']

// 外键字段名（不以这些结尾的才是单号）
const FOREIGN_KEY_SUFFIXES = ['customerNo', 'supplierNo', 'employeeNo', 'materialNo', 'departmentNo', 'warehouseNo']

// 复制单永不继承的「唯一编号」类字段（与单号同级：每张单/每张税票各自唯一，复制=开新单/新票）
const UNIQUE_NO_FIELDS = ['taxInvoiceNo']

export function useCopyBill(opts) {
  const {
    route,
    loadFn,
    form,
    idField,
    noField,
    itemIdField,
    itemsField = 'items',
    afterCopy,
    // 自定义字段复制（可选）：{ entity: 表头实体名, itemEntity: 明细实体名, itemIdField: 明细主键字段名 }
    // 不传 = 不复制自定义字段值（向后兼容，行为与旧版完全一致）
    customField
  } = opts

  // 兼容 ref 和 reactive 类型的 form
  function getFormTarget() {
    return form && form.value !== undefined ? form.value : form
  }

  async function applyCopy() {
    const copyId = route?.query?.copy
    if (!copyId) return false

    try {
      const res = await loadFn(copyId)
      if (!res) return false

      // 深拷贝，避免与源单共用引用
      const data = JSON.parse(JSON.stringify(res))
      const target = getFormTarget()

      // 1. 清空表头主键
      if (idField) {
        data[idField] = null
      } else {
        // 自动检测：找到值等于 copyId 的 Id 结尾字段
        const copyIdNum = Number(copyId)
        Object.keys(data).forEach(key => {
          if (key.toLowerCase().endsWith('id') && (data[key] == copyId || data[key] == copyIdNum)) {
            data[key] = null
          }
        })
      }
      data.id = null

      // 2. 清空单号
      if (noField) {
        data[noField] = ''
      } else {
        // 自动检测：找到以 No 结尾且不是外键的字段
        Object.keys(data).forEach(key => {
          if (key.toLowerCase().endsWith('no') && !FOREIGN_KEY_SUFFIXES.includes(key)) {
            // 排除明显的外键/引用单号
            if (!['sourceBillNo', 'sourceNo', 'orderNo', 'deliveryNo', 'returnNo', 'pickNo', 'invoiceNo', 'receiptNo', 'batchNo', 'documentNo', 'taxNo', 'expressNo', 'trackingNo', 'logisticsNo', 'projectNo', 'customerOrderNo', 'workOrderNo', 'salesOrderNo', 'inspectionNo', 'relatedOrderNo'].includes(key)) {
              data[key] = ''
            }
          }
        })
      }
      // 唯一编号类字段复制永不继承（税票号码等：复制=开新单/新票，号码不能沿用）
      UNIQUE_NO_FIELDS.forEach(k => {
        if (k in data) data[k] = ''
      })

      // 2.5 自定义字段值复制（在清明细主键之前先按源主键取值；失败降级为不带值，不阻塞复制）
      //     背景（2026-10-13 用户反馈）：表头/明细自定义字段值存在独立值表，详情接口不返回，
      //     旧复制链路只深拷贝详情数据 → 复制单自定义字段全部为空。
      let headerCfValues = {}
      // 行对象 → 该行自定义字段值（行对象是深拷贝产物，清主键前后引用稳定）
      const rowCfValues = new Map()
      if (customField && customField.entity) {
        try {
          headerCfValues = await loadCustomFieldValues(customField.entity, copyId) || {}
          const srcItems = Array.isArray(data[itemsField]) ? data[itemsField] : []
          if (customField.itemEntity && srcItems.length > 0) {
            const idKey = customField.itemIdField
            const ids = srcItems
              .map(r => (idKey ? r[idKey] : (r.itemId ?? r.id)))
              .filter(v => v !== undefined && v !== null)
            if (ids.length > 0) {
              const map = await batchLoadCustomFieldValues(customField.itemEntity, ids) || {}
              srcItems.forEach(r => {
                const srcId = idKey ? r[idKey] : (r.itemId ?? r.id)
                const values = srcId != null ? map[srcId] : null
                if (values && Object.keys(values).length > 0) rowCfValues.set(r, values)
              })
            }
          }
        } catch (e) {
          console.warn('[useCopyBill] 自定义字段值加载失败，复制单将不带自定义字段值', e)
          headerCfValues = {}
          rowCfValues.clear()
        }
      }

      // 3. 重置状态为草稿
      //    按"源单自身的状态口径"写草稿值：数字状态单据（委外 5 张、财务预收/预付等）写 0，
      //    字符串状态单据写「草稿」。否则数字单据被写成「草稿」→ 页面判定"当前状态不可修改"、
      //    输入框全灰、保存直接被拦（2026-09-28 委外加工单复制实测踩到）。
      const numericStatus = typeof data.status === 'number'
      data.status = numericStatus ? 0 : '草稿'
      data.approvalStatus = typeof data.approvalStatus === 'number' ? null : ''
      data.closeStatus = (numericStatus || typeof data.closeStatus === 'number') ? 0 : ''

      // 4. 清空不可复用的关联字段（2026-10-13 百分百同步口径：附件与来源随复制带出，不再清空）
      data.auditTime = ''
      data.approvalTime = ''
      data.createTime = ''

      // 5. 清空明细主键行（来源勾稽字段按百分百同步口径随复制保留）
      const items = data[itemsField]
      if (Array.isArray(items)) {
        items.forEach(row => {
          // 调用方指定的明细主键
          if (itemIdField) row[itemIdField] = null
          // 通用主键字段兜底清空
          COMMON_ITEM_ID_FIELDS.forEach(f => {
            if (row[f] !== undefined) row[f] = null
          })
          // 自动检测：清空所有 Id 结尾的字段（排除外键）
          // 2026-11-12：明细行的「仓库」外键也必须排除 —— 历史实锤：复制其他入库单/采购入库单后
          // 明细仓库被清空，保存成草稿时看不出来，一提交就报"第1行未选择仓库"（用户侧等于提交失败）。
          Object.keys(row).forEach(key => {
            if (key.toLowerCase().endsWith('id') && !['materialId', 'unitId', 'warehouseId', 'stockId', 'scrapWarehouseId', 'customerId', 'supplierId', 'employeeId', 'departmentId', 'currencyId', 'priceLevelId', 'paymentTermId', 'categoryId', 'locationId', 'taxRateId', 'targetId'].includes(key)) {
              row[key] = null
            }
          })
          // 回填明细自定义字段值（行对象引用稳定，取 2.5 步按源主键抓到的值）
          const cfValues = rowCfValues.get(row)
          if (cfValues) Object.assign(row, cfValues)
        })
      }

      // 6. 填入表单
      //    注意：源单接口里为 null 的字段不能把表单里已有的「数组」覆盖成 null —— 明细表组件会读
      //    items.length 直接渲染崩溃（2026-09-28 费用分摊复制页实测：Cannot read properties of null）。
      Object.keys(data).forEach(key => {
        if (data[key] === null && Array.isArray(target[key])) return
        target[key] = data[key]
      })

      // 6.5 回填表头自定义字段值（详情接口不返回，第 6 步覆盖后补上；页面保存时随既有落库逻辑写入）
      if (headerCfValues && Object.keys(headerCfValues).length > 0) {
        Object.keys(headerCfValues).forEach(key => {
          target[key] = headerCfValues[key]
        })
      }

      // 7. 回调（重算金额等）
      if (typeof afterCopy === 'function') {
        await afterCopy(data)
      }

      ElMessage.success(tt('已复制源单数据，请确认后保存'))
      return true
    } catch (e) {
      console.warn('[useCopyBill] 复制加载失败', e)
      ElMessage.warning(tt('复制源单数据失败：') + (e?.message || e))
      return false
    }
  }

  return { applyCopy }
}

