/**
 * 物料自定义字段：①单据明细「物料」下拉附加展示 ②「从物料库选择」弹窗全字段展示
 *
 * 为什么抽公共：这套能力要铺到 20+ 个单据编辑页。若各页各写一份，后续改一处要改 20 多遍
 * 且极易漏改/行为漂移。本文件是「销售订单页（SaleOrderEdit.vue）已验收实现」的等价抽取，
 * 各页只需：取开关/加载定义（init）、物料到位后预取（preloadAll）、下拉文本（dropText）、
 * 弹窗取值（mergeIntoRows），模板只加一个 <span> 与 popper-class。
 *
 * 定制开关：sys_config 键 ui.custom_field.drop_display.enabled（设置中心→系统参数→界面显示）。
 *   关闭时 dropFields 为空 → 行内下拉不追加任何自定义字段内容；
 *   物料库弹窗的"全字段 + 全部自定义字段"**不受该开关控制**（恒定生效）。
 *
 * 容错原则：任何一步失败都静默降级（回落默认开启/空数据），
 *   **绝不因自定义字段问题阻塞选料、弹窗操作或单据保存**。
 */
import { ref, computed } from 'vue'
import { loadCustomFieldDefinitions, batchLoadCustomFieldValues } from '@/composables/useCustomField'
import { getDropDisplayEnabled } from '@/api/system/customField'

/** 批量预取上限：/custom-field/batch-value 是 GET + ids 逗号拼接，一次性全量提交会导致 URL 超长(414) */
export const MATERIAL_CF_BATCH = 500

/**
 * 物料库弹窗的「物料全部字段」列定义（全站统一一份）
 * - 默认显示 11 列关键信息（图片在最前）；`defaultHidden: true` 的 15 列需在「列设置」抽屉里勾出
 * - 不含无业务意义的技术列：materialId / 5 个 unitId / mainImage / 审计列
 * - `type` 供统一渲染件 MaterialFieldCell 分支渲染：text / flag(0/1→是/否) / amount2 / amount4 / image
 * - 物料分类、基本单位、最新成本由后端 /page 按外键实时解析回填（只读载体）
 * - **图片固定在最前面**（用户 2026-10-10 口径：第一眼就要看到，既不许默认隐藏、也不许排到后面）
 */
export const MATERIAL_PICKER_BASE_COLUMNS = [
  { prop: 'imageUrls', label: '物料图片', width: 80, align: 'center', type: 'image', fixed: 'left' },
  { prop: 'materialCode', label: '物料编码', width: 140, fixed: 'left' },
  { prop: 'materialName', label: '物料名称', minWidth: 160, fixed: 'left' },
  { prop: 'model', label: '规格型号', width: 140, type: 'text' },
  { prop: 'categoryName', label: '物料分类', width: 140, type: 'text' },
  { prop: 'baseUnitName', label: '基本单位', width: 90, align: 'center', type: 'text' },
  { prop: 'salePrice', label: '销售单价', width: 110, align: 'right', type: 'amount2' },
  { prop: 'purchasePrice', label: '采购单价', width: 110, align: 'right', type: 'amount2' },
  { prop: 'taxRate', label: '税率%', width: 80, align: 'right', type: 'amount2' },
  { prop: 'avgCost', label: '最新成本', width: 110, align: 'right', type: 'amount4' },
  { prop: 'status', label: '状态', width: 90, align: 'center', type: 'text' },
  { prop: 'fullName', label: '全称', minWidth: 160, type: 'text', defaultHidden: true },
  { prop: 'materialType', label: '物料类型', width: 110, align: 'center', type: 'text', defaultHidden: true },
  { prop: 'supplyType', label: '供应类型', width: 110, align: 'center', type: 'text', defaultHidden: true },
  { prop: 'defaultSupplierName', label: '默认供应商', minWidth: 150, type: 'text', defaultHidden: true },
  { prop: 'unitConversion', label: '单位换算率', width: 110, align: 'right', type: 'amount4', scaleKey: 'qty', defaultHidden: true },
  { prop: 'isLotControl', label: '批次管理', width: 90, align: 'center', type: 'flag', defaultHidden: true },
  { prop: 'isSnControl', label: '序列号管理', width: 100, align: 'center', type: 'flag', defaultHidden: true },
  { prop: 'isBinControl', label: '库位管理', width: 90, align: 'center', type: 'flag', defaultHidden: true },
  { prop: 'isQcRequired', label: '是否质检', width: 90, align: 'center', type: 'flag', defaultHidden: true },
  { prop: 'isExpiryControl', label: '有效期管理', width: 100, align: 'center', type: 'flag', defaultHidden: true },
  { prop: 'expiryDays', label: '有效期天数', width: 100, align: 'right', type: 'text', defaultHidden: true },
  { prop: 'minStock', label: '最小库存', width: 100, align: 'right', type: 'amount4', scaleKey: 'qty', defaultHidden: true },
  { prop: 'maxStock', label: '最大库存', width: 100, align: 'right', type: 'amount4', scaleKey: 'qty', defaultHidden: true },
  { prop: 'isConfidential', label: '保密物料', width: 90, align: 'center', type: 'flag', defaultHidden: true },
  { prop: 'remark', label: '备注', minWidth: 160, type: 'text', defaultHidden: true }
]

/**
 * 弹窗完整列定义 = 物料全部字段 + 全部自定义字段（自定义字段默认显示，可在列设置里隐藏）
 * 供各页 useColumnSetting('MaterialPicker', computed(() => materialPickerColumns(allFields.value))) 使用。
 * 说明：page-key 全站统一为 'MaterialPicker' —— 列设置**配一次全单据生效**，避免每页各配一遍。
 */
export function materialPickerColumns(customFields) {
  return [
    ...MATERIAL_PICKER_BASE_COLUMNS,
    ...(customFields || []).map(f => ({
      prop: f.fieldKey,
      label: f.fieldLabel,
      width: 140,
      customField: true,
      defaultHidden: false
    }))
  ]
}

/**
 * 「物料档案全部字段」并入单据明细列设置用（2026-11-03 全站统一口径：带物料明细的地方，物料档案字段全都有）：
 * - 基础取自 MATERIAL_PICKER_BASE_COLUMNS（与物料库弹窗同一份，保证口径一致）；
 * - 去掉 图片/物料编码/物料名称（各单据明细自带这三列）；
 * - 去掉 状态/供应类型/备注（这三名是单据明细行的常见业务字段，并进去会跟行数据撞车）；
 * - 全部 defaultHidden: true —— 默认展示 = 各页现状，其它字段经「列设置」开启。
 */
export const MATERIAL_ARCHIVE_COLUMNS = MATERIAL_PICKER_BASE_COLUMNS
  .filter(c => !['imageUrls', 'materialCode', 'materialName', 'status', 'supplyType', 'remark'].includes(c.prop))
  .map(c => ({ ...c, defaultHidden: true }))

/**
 * 把档案列并入页面既有列定义：按 prop 去重。
 * 页面业务列与档案列同名 → 以业务列为准；页面列带 materialArchive: true 标记（本身就是档案列，
 * 如 BOM 子件的「成本」列 = 档案最新成本）→ 视为已提供，同样去重，不再出第二份。
 */
export function mergeMaterialArchiveColumns(existingColumns) {
  const business = new Set(
    (existingColumns || []).filter(c => c && c.prop && !c.materialArchive).map(c => c.prop)
  )
  const provided = new Set((existingColumns || []).filter(c => c && c.prop && c.materialArchive).map(c => c.prop))
  return MATERIAL_ARCHIVE_COLUMNS.filter(c => !business.has(c.prop) && !provided.has(c.prop))
}

/**
 * 行数据需要补齐的档案字段清单：排除「页面业务列同名」（业务字段不碰）；
 * 注意 materialArchive 标记列（页面自带的档案列，如 BOM「成本」、打样单「规格型号」）本身
 * 就是档案字段 → 必须纳入补齐，否则行上永远取不到值（2026-11-03 修复：成本列恒为 0 的漏网）。
 */
export function materialArchiveRowFields(existingColumns) {
  const business = new Set(
    (existingColumns || []).filter(c => c && c.prop && !c.materialArchive).map(c => c.prop)
  )
  return MATERIAL_ARCHIVE_COLUMNS
    .filter(c => !business.has(c.prop))
    .map(c => c.prop)
}

/** 物料图片：imageUrls 为逗号分隔的多图 */
export function pickerImageList(raw) {
  if (!raw) return []
  return String(raw).split(',').map(s => s.trim()).filter(Boolean)
}
/** 取第一张图（列表缩略图用） */
export function pickerFirstImage(raw) {
  const list = pickerImageList(raw)
  return list.length ? list[0] : ''
}

/** 物料 ID：兼容 camelCase 与 snake_case（组装单/拆卸单等页面整页用 snake_case） */
function matId(m) {
  if (!m) return null
  return m.materialId != null ? m.materialId : (m.material_id != null ? m.material_id : null)
}

export function useMaterialCustomFields() {
  /** 定制开关（关闭后行内下拉不追加自定义字段；弹窗全字段不受影响） */
  const enabled = ref(true)
  /** 物料实体全部启用的自定义字段（物料库弹窗展示用） */
  const allFields = ref([])
  /** 其中勾选了「下拉显示」的字段（行内下拉附加展示用） */
  const dropFields = ref([])
  /** { materialId: { fieldKey: value } } */
  const valueMap = ref({})
  /**
   * 需要预取「字段值」的字段 = 勾了「可搜索」的 ∪ 勾了「下拉显示」的。
   * 与各页 materialSearchableFields 同口径：可搜索字段即使关闭了下拉显示开关也必须预取。
   */
  const valueFields = computed(() => (allFields.value || []).filter(f =>
    f && (f.isSearch === 1 || (enabled.value && f.isDropShow === 1))))
  /** 已请求过自定义字段值的物料 ID 集合（判重：该物料确实没有值时接口返回空对象，不能靠 map 是否命中判断，否则每次重复请求） */
  const loadedIds = ref(new Set())

  /**
   * 初始化：读定制开关 + 加载物料自定义字段定义。失败一律回落（开关 true / 空字段），不抛错。
   * 放在各页 onMounted 里、加载基础数据之前调用。
   */
  async function init() {
    try {
      enabled.value = !!(await getDropDisplayEnabled())
    } catch (e) {
      enabled.value = true
    }
    try {
      const r = await loadCustomFieldDefinitions('material')
      allFields.value = Array.from(r.fieldMap.values())
      dropFields.value = enabled.value ? allFields.value.filter(f => f.isDropShow === 1) : []
    } catch (e) {
      console.warn('[material-cf] 加载物料自定义字段定义失败:', e)
      allFields.value = []
      dropFields.value = []
    }
  }

  /**
   * 按批预取指定物料的自定义字段值（≤500/批、顺序执行、已请求过的不再重复请求）
   * @param {Array<number>} ids
   */
  async function ensureValues(ids) {
    const list = (ids || []).filter(id => id != null && !loadedIds.value.has(id))
    if (!list.length) return
    for (let i = 0; i < list.length; i += MATERIAL_CF_BATCH) {
      const batch = list.slice(i, i + MATERIAL_CF_BATCH)
      batch.forEach(id => loadedIds.value.add(id))
      try {
        const map = await batchLoadCustomFieldValues('material', batch)
        Object.assign(valueMap.value, map)
      } catch (e) {
        console.warn('[material-cf] 批量取值失败（已降级为只显示原有内容）:', e)
      }
    }
  }

  /**
   * 全量预取（基础数据加载完物料列表后调用；**不要 await**，不阻塞页面渲染）
   * 只要存在「下拉显示」或「可搜索」的自定义字段就需要值表：
   *   - 下拉显示：选项里要渲染「字段名：值」
   *   - 可搜索：过滤要拿全量物料的值才能命中（此前只按 dropFields 判断，
   *     管理员只勾「可搜索」未勾「下拉显示」时完全不预取 → 输入自定义字段值搜不到）
   * @param {Array<Object>} materials 物料列表
   */
  function preloadAll(materials) {
    if (!valueFields.value.length) return
    const ids = (materials || []).map(matId).filter(id => id != null)
    if (!ids.length) return
    return ensureValues(ids)
  }

  /**
   * 行内下拉要追加展示的「字段名：值」列表；值为空的字段不渲染
   * （不留空、不显示 `-`、不显示 `[object Object]`）
   */
  function dropText(m) {
    if (!m || !dropFields.value.length) return []
    const id = matId(m)
    if (id == null) return []
    const vals = valueMap.value[id]
    if (!vals) return []
    return dropFields.value
      .map(f => ({ key: f.fieldKey, label: f.fieldLabel, value: vals[f.fieldKey] }))
      .filter(x => x.value !== undefined && x.value !== null && x.value !== '')
  }

  /**
   * 物料库弹窗：把当前页物料的自定义字段值合并进行对象，动态列（prop = fieldKey）即可直接绑定渲染
   * @param {Array<Object>} rows pickerMaterials（会被就地修改）
   */
  async function mergeIntoRows(rows) {
    const list = rows || []
    if (!list.length || !allFields.value.length) return
    await ensureValues(list.map(matId))
    list.forEach(r => {
      const vals = valueMap.value[matId(r)]
      if (vals) Object.assign(r, vals)
    })
  }

  return {
    enabled,
    allFields,
    dropFields,
    valueMap,
    loadedIds,
    init,
    ensureValues,
    preloadAll,
    dropText,
    mergeIntoRows,
    // 供模板使用的纯函数（与 composable 状态无关）
    pickerImageList,
    pickerFirstImage
  }
}
