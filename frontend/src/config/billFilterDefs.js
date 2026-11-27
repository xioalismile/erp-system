// ====================================================================
// 单据列表「筛选条件自定义」共享工厂（2026-10-14 全面推广 · 以销售订单为试点标准）
//
// 销售订单试点口径（已交付验证）：
//   条件候选 = 标准维度（状态/日期/客户/供应商/员工，std 标记 → 值走页面 search 通道，
//              与数据看板穿透/统计卡互通）
//            + 单据表头字段（f_<key> 参数化，后端白名单翻译，键与明细报表引擎同键）
//            + 单据自定义字段（设置中心勾「可搜索」，沿用 customSearch 既有通道）。
//   明细模式专属条件 = 明细报表 /columns 的 filters 元数据（键=引擎同键，直透明细接口）。
//
// 各页只声明「本单据的键位差异」：日期键 / 客户键 / 供应商键 / 员工键 + 表头字段清单；
// 拼装逻辑全部收敛在本文件，防止 37 个页面各写一份逐步漂移（守则 03：单点实现）。
//
// 默认勾选口径（守则 2.7.2-6：新增能力默认值对齐旧行为）：
//   标准维度 = 现状默认勾选（没保存过筛选设置时查询区与改版前完全一致）；
//   表头字段 = 默认不勾（用户在「筛选设置」里按需勾选）；
//   自定义字段 = 默认勾选（与原「自定义字段搜索栏」现状一致）。
// ====================================================================

/** 可搜索的单据自定义字段类型（开关/公式型值域不适合做搜索条件，不进候选） */
const CF_TYPES = ['text', 'textarea', 'number', 'date', 'datetime', 'select']

/**
 * 声明一个表头字段条件。
 * @param {'text'|'select'|'dateRange'|'numberRange'} type
 * @param {string} key  条件键（= 后端白名单键 = 明细报表引擎筛选键）
 * @param {string} label 界面名称
 * @param {string} [extra] select 型 = 主档选项来源（department/currency/priceLevel/settleMode…）；其余型可省
 */
export function fld(type, key, label, extra) {
  const def = { key, label, type, defaultChecked: false }
  def.group = type === 'numberRange' ? '金额信息' : '单据信息'
  if (type === 'select') def.optionSource = extra
  return def
}

/**
 * 标准维度条件（std 标记：值经壳走页面 search 对象，filter-change 通道）。
 * @param {object} opts 只传本单据存在的维度：
 *   status:  { options: [...] }                        下拉选项（单据状态机五态）
 *   date:    { key, label? }                           key = 本单据主日期在明细引擎的筛选键
 *   customer:{ key, label? }                           key = 客户筛选键（如 customerName）
 *   supplier:{ key, label? }                           key = 供应商筛选键（如 supplierName）
 *   employee:{ key, label? }                           key = 员工筛选键（如 salerName/buyerName）
 */
export function stdDefs(opts) {
  const out = []
  if (opts.status) {
    out.push({ key: 'status', label: '单据状态', group: '单据信息', type: 'select', options: opts.status.options, std: 'status' })
  }
  if (opts.date) {
    out.push({ key: opts.date.key, label: opts.date.label || '单据日期', group: '单据信息', type: 'dateRange', std: 'date' })
  }
  if (opts.customer) {
    out.push({ key: opts.customer.key, label: opts.customer.label || '客户', group: '单据信息', type: 'select', optionSource: 'customer', std: 'customer' })
  }
  if (opts.supplier) {
    out.push({ key: opts.supplier.key, label: opts.supplier.label || '供应商', group: '单据信息', type: 'select', optionSource: 'supplier', std: 'supplier' })
  }
  if (opts.employee) {
    out.push({ key: opts.employee.key, label: opts.employee.label || '员工', group: '单据信息', type: 'select', optionSource: 'employee', std: 'employee' })
  }
  return out
}

/**
 * 单据自定义字段条件（customSearch 通道，与改版前「自定义字段搜索栏」同一出口）。
 * @param {object} cf useCustomFieldSearch(entity) 返回值（searchFields ref）
 */
export function cfDefs(cf) {
  return ((cf && cf.searchFields && cf.searchFields.value) || [])
    .filter(f => f && f.fieldKey && CF_TYPES.includes(f.fieldType))
    .map(f => ({
      key: 'cf_doc_' + f.fieldKey,
      label: f.fieldLabel,
      group: '自定义字段',
      type: 'text',
      cf: f.fieldKey,
      defaultChecked: true
    }))
}

/** 明细报表引擎筛选类型 → 列表页条件控件类型 */
const DETAIL_TYPE_MAP = { text: 'text', cfText: 'text', select: 'select', dateRange: 'dateRange', numberRange: 'numberRange' }

/**
 * 明细模式专属条件定义（来自明细报表 /columns 的 filters 元数据）。
 * @param {Array} filtersMeta 明细接口 /columns 返回的 filters 数组
 * @param {object} stdByKey 引擎筛选键 → 标准维度（'status'|'date'|'customer'|'supplier'|'employee'）；
 *   命中 = 该条件为标准维度（默认勾选=现状、值走页面 search 通道）
 */
export function detailFilterDefsOf(filtersMeta, stdByKey) {
  return (filtersMeta || [])
    .filter(f => f && f.key)
    .map(f => {
      const std = stdByKey ? stdByKey[f.key] : undefined
      return {
        key: f.key,
        label: f.label,
        group: f.group || '筛选条件',
        // 报表 /columns 的 filters 元数据字段名是 type（不是 filterType）；
        // 读错会全部兜底成 text → 明细模式查询区下拉/日期控件退化为纯文本框（2026-11-27 修复）
        type: DETAIL_TYPE_MAP[f.type] || 'text',
        optionSource: f.optionSource,
        options: f.options,
        paramKeys: f.paramKeys,
        std,
        // 引擎自带的默认勾选是「明细报表页」口径；列表页明细模式的现状 = 标准维度四/五件套
        defaultChecked: !!std
      }
    })
}
