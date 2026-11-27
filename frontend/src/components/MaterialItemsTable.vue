<template>
  <!-- 物料明细通用组件（全站唯一一份）
       调用即自带：物料自带列（图片在最前 / 规格 / 分类 / 单价…）+ 物料自定义字段列 + 列设置 + 添加货品（物料库弹窗）+ 删除行。
       各页只需 v-model 绑行数据 + 传 pageKey（列设置分桶）+（可选）业务附加列。 -->
  <div class="material-items">
    <div class="mi-head">
      <span class="mi-title">{{ $tt(title) }}</span>
      <span class="mi-count">{{ $tt("共") }} {{ items.length }} {{ $tt("个货品") }}</span>
      <span class="mi-btns">
        <slot name="actions" />
        <el-button v-if="!readonly" type="primary" size="small" @click="pickerVisible = true">{{ $tt("＋ 添加货品") }}</el-button>
        <el-tooltip :content="$tt('列设置')" placement="top">
          <el-button :icon="Setting" circle size="small" @click="openColumnSetting" />
        </el-tooltip>
      </span>
    </div>

    <el-table :data="items" border size="small" style="width:100%" :key="configVersion">
      <el-table-column v-if="showIndex" type="index" label="#" width="50" align="center" fixed="left" />
      <template v-for="col in sortedColumns" :key="col.prop">
        <el-table-column
          v-if="isColVisible(col.prop) && showCol(col)"
          :prop="col.prop"
          :label="$tf(col.label)"
          :width="col.width"
          :min-width="col.minWidth"
          :align="col.align || 'left'"
          :fixed="getColFixed(col.prop, col.fixed)"
          :show-overflow-tooltip="colTooltip(col)"
        >
          <template #default="{ row, $index }">
            <!-- 货品：编码 + 名称 -->
            <template v-if="col.prop === 'materialCode'">
              <div class="mi-code">{{ row.materialCode }}</div>
              <div class="mi-name">{{ row.materialName }}</div>
            </template>
            <!-- 多单位换算 5 列（2026-11-10 自动注入）：前 3 列只读 -->
            <span v-else-if="col.prop === 'subUnitName'">{{ unitCells.subUnitName(row) }}</span>
            <span v-else-if="col.prop === 'subQuantity'">{{ unitCells.subUnitQtyText(row) }}</span>
            <span v-else-if="col.prop === 'auxQuantityText'">{{ unitCells.auxQtyText(row) }}</span>
            <el-select
              v-else-if="col.prop === 'floatUnitId'"
              v-model="row.floatUnitId"
              :placeholder="$tt('浮动单位')"
              clearable
              size="small"
              style="width:100%"
              :disabled="readonly || unitCells.floatOptions(row).length === 0"
              @change="unitCells.onFloatUnitChange(row)"
            >
              <el-option v-for="o in unitCells.floatOptions(row)" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
            <el-input-number
              v-else-if="col.prop === 'floatQuantity'"
              v-model="row.floatQuantity"
              :controls="false"
              :min="0"
              :precision="precisionFor(col, 4)"
              size="small"
              style="width:100%"
              :disabled="readonly"
              @change="unitCells.onFloatQuantityChange(row)"
            />
            <!-- 浮动换算率：只读，由系统按「基本单位数量 ÷ 浮动数量」算出（2026-11-11） -->
            <span
              v-else-if="col.prop === 'floatConversionRate'"
              style="display:block;text-align:right;color:#606266"
            >{{ unitCells.floatRateText(row) }}</span>
            <!-- 业务附加列：下拉 -->
            <el-select
              v-else-if="col.editor === 'select'"
              v-model="row[col.prop]" size="small" clearable
              :placeholder="$tt(col.placeholder || '请选择')" style="width:100%" :disabled="readonly"
            >
              <el-option v-for="o in (col.options || [])" :key="o.value" :label="$tf(o.label)" :value="o.value" />
            </el-select>
            <!-- 业务附加列：数值 -->
            <el-input-number
              v-else-if="col.editor === 'number'"
              v-model="row[col.prop]" :controls="false" size="small" style="width:100%"
              :min="col.min == null ? 0 : col.min" :precision="precisionFor(col, col.precision == null ? 2 : col.precision)"
              :placeholder="$tt(col.placeholder || '')" :disabled="readonly"
              @change="onNumberCellChange(row, col)"
            />
            <el-button v-else-if="col.prop === 'action'" link type="danger" size="small" @click="removeRow($index)">{{ $tt("删除") }}</el-button>
            <!-- 物料自带列 / 物料自定义字段列：统一由 MaterialFieldCell 渲染（与物料库弹窗共用同一份逻辑） -->
            <MaterialFieldCell v-else :col="col" :row="row" />
          </template>
        </el-table-column>
      </template>
    </el-table>

    <div v-if="hint" class="mi-hint">{{ $tt(hint) }}</div>
    <slot name="footer" />

    <MaterialPickerDialog v-model="pickerVisible" :title="$tt('从物料库选择')" :exclude-fn="excludeFn" @confirm="onPicked" />
    <ColumnSettingDrawer v-model="columnSettingVisible" :page-key="pageKey" :columns="allColumns" :title="$tt(title + '列设置')" />
  </div>
</template>

<script setup>
/**
 * 物料明细通用组件
 *
 * 为什么必须抽公共：物料库弹窗（MaterialPickerDialog）是组件，所以它自动带图片/自定义字段/列设置；
 * 而"物料明细表"此前没有组件 → 各页各写一份 → 有的没图片、有的没自定义字段、有的没列设置，
 * 用户反复看到"缝缝补补"。本组件把这份能力收成唯一一份：
 *   ① 物料自带列（复用 MATERIAL_PICKER_BASE_COLUMNS，**图片在最前**）
 *   ② 物料自定义字段列（复用 useMaterialCustomFields，与物料库弹窗同源）
 *   ③ 列设置（复用 useColumnSetting + ColumnSettingDrawer，按用户+公司隔离）
 *   ④ 添加货品（复用 MaterialPickerDialog，多选/搜索/分类/自定义字段搜索全都有）
 *   ⑤ 编辑回显自动补齐物料自带字段与自定义字段值
 *   ⑥ 单元格渲染**交给 MaterialFieldCell**（与物料库弹窗同一份），本组件不再自带渲染分支
 *
 * 各页用法：
 *   <MaterialItemsTable v-model="lines" page-key="XxxDetail"
 *     :extra-columns="[{ prop:'warehouseId', label:'仓库', width:170, editor:'select', options: whOptions }]"
 *     :row-defaults="{ warehouseId: null, qty: null, status: 1 }">
 *     <template #actions><el-button ...>批量填充</el-button></template>
 *   </MaterialItemsTable>
 */
import { ref, computed, onMounted, onActivated, watch } from 'vue'
import { Setting } from '@element-plus/icons-vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { useMaterialCustomFields, MATERIAL_PICKER_BASE_COLUMNS } from '@/composables/useMaterialCustomFields'
import { usePrecision } from '@/composables/usePrecision'
import { getMaterial } from '@/api/basedata/material'
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
import MaterialFieldCell from '@/components/MaterialFieldCell.vue'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
// 多单位换算 5 列（2026-11-10）：附加列里有「单位」列时自动注入，与单据明细组件同一份列定义与取值逻辑
import { useMultiUnitCells, multiUnitColumnsFor, mergeMultiUnitColumns } from '@/components/bill/multiUnitColumns'
import { loadFloatUnitEnabled, floatUnitEnabled } from '@/composables/useUnitConvert'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  /** 列设置分桶键（每页唯一，如 'SaleOrderDetail'） */
  pageKey: { type: String, required: true },
  title: { type: String, default: '物料明细' },
  hint: { type: String, default: '' },
  showIndex: { type: Boolean, default: true },
  showAction: { type: Boolean, default: true },
  readonly: { type: Boolean, default: false },
  /**
   * 业务附加列（排在物料列之后）：
   * { prop, label, width, minWidth, editor:'select'|'number'|'text', options:[{value,label}], precision, min, placeholder }
   */
  extraColumns: { type: Array, default: () => [] },
  /** 新增行默认值（如 { warehouseId:null, minQuantity:null, status:1 }） */
  rowDefaults: { type: Object, default: () => ({}) },
  /** 物料库弹窗排除规则（透传） */
  excludeFn: { type: Function, default: null }
})
const emit = defineEmits(['update:modelValue', 'change'])

// 行数据用 computed 代理，组件内不做深拷贝（父页是唯一数据源）
const items = computed({
  get: () => props.modelValue,
  set: (v) => { emit('update:modelValue', v); emit('change', v) }
})

const mcf = useMaterialCustomFields()
// 数值列位数：由设置中心「小数位数设置」统一控制（显式 col.scaleKey > 字段名推断 > 旧值回落）
const { precisionFor } = usePrecision()

/** 物料自带列：去掉 物料编码/物料名称（本组件用「货品」合并列）与 物料状态（多与业务状态字段同名冲突）；图片改叫「图片」 */
const MATERIAL_BASE_COLS = MATERIAL_PICKER_BASE_COLUMNS
  .filter(c => c.prop !== 'materialCode' && c.prop !== 'materialName' && c.prop !== 'status')
  .map(c => (c.type === 'image' ? { ...c, label: '图片', width: 70 } : { ...c }))
/** 图片列固定在**最前面**（用户口径）；「货品」紧随其后，两者都在最左侧固定 */
const IMAGE_COL = MATERIAL_BASE_COLS.filter(c => c.type === 'image')
const OTHER_BASE_COLS = MATERIAL_BASE_COLS.filter(c => c.type !== 'image')

/** 列定义 = 图片 + 货品 + 物料自带列 + 物料自定义字段列 + 业务附加列 + 操作 */
const baseDetailColumns = computed(() => [
  ...IMAGE_COL,
  { prop: 'materialCode', label: '货品', minWidth: 200, fixed: 'left' },
  ...OTHER_BASE_COLS,
  ...(mcf.allFields.value || []).map(f => ({ prop: f.fieldKey, label: f.fieldLabel, minWidth: 140, customField: true })),
  ...props.extraColumns,
  ...(props.showAction ? [{ prop: 'action', label: '操作', width: 70, align: 'center' }] : [])
])
/** 多单位换算（2026-11-10）：附加列里有「单位」列才注入 5 列；没有则整块不工作（旧行为零变化） */
const multiUnitCols = computed(() => multiUnitColumnsFor(baseDetailColumns.value))
const detailColumns = computed(() => mergeMultiUnitColumns(baseDetailColumns.value))
const unitCells = useMultiUnitCells({
  getIdProp: () => 'materialId',
  getRows: () => props.modelValue
})

/** 浮动单位/浮动数量两列：全局开关关闭时整列不渲染 */
const showCol = (col) => !(col && col.floatOnly && !floatUnitEnabled.value)

const {
  columnSettingVisible, openColumnSetting, isColVisible, getColFixed,
  configVersion, sortedColumns, allColumns
} = useColumnSetting(props.pageKey, detailColumns)

/** 控件/图片列不用文本溢出提示 */
const colTooltip = (col) => col.type !== 'image' && !col.editor
  && col.prop !== 'materialCode' && col.prop !== 'action'

const pickerVisible = ref(false)

/** 去重键：同货品 + 同仓库（仓库为空 = 全部仓库） */
const rowKey = (r) => String(r.materialId) + '#' + (r.warehouseId == null ? 'all' : r.warehouseId)

async function onPicked({ rows }) {
  if (!rows || !rows.length) return
  const next = [...props.modelValue]
  const seen = new Set(next.map(rowKey))
  const added = []
  rows.forEach(m => {
    const line = { ...m, ...props.rowDefaults }
    const k = rowKey(line)
    if (seen.has(k)) return
    seen.add(k)
    next.push(line)
    added.push(line)
  })
  if (added.length) {
    await mcf.mergeIntoRows(added).catch(() => {})
    items.value = next
  }
}

function removeRow(idx) {
  const next = [...props.modelValue]
  next.splice(idx, 1)
  items.value = next
}

/** 数值列变化：主数量（数量/基本单位数量）改了，填了浮动单位时同步重算浮动换算率（2026-11-11） */
function onNumberCellChange(row, col) {
  if (/^(quantity|qty)$/i.test(String(col.prop || ''))) unitCells.onMainQtyChange(row)
}

/** 编辑回显：补齐物料自带字段（图片/规格/分类…）+ 自定义字段值 */
async function enrich(list) {
  const rows = (list || []).filter(r => r && r.materialId != null)
  if (!rows.length) return
  await mcf.mergeIntoRows(rows).catch(() => {})
  const missing = rows.filter(r => !r.imageUrls && !r.mainImage)
  if (!missing.length) return
  await Promise.all(missing.map(async (r) => {
    try {
      const m = await getMaterial(r.materialId)
      if (m) Object.keys(m).forEach(k => { if (r[k] === undefined || r[k] === null || r[k] === '') r[k] = m[k] })
    } catch (e) { /* 静默降级：取不到就显示已有内容 */ }
  }))
}

/** 多单位换算（2026-11-10）：物料实体 + 单位名 + 换算行整单一次预取 */
function initMultiUnitRows() {
  // 开关无条件读（列定义可能晚于挂载就绪，否则浮动两列永远不显示，2026-11-11）
  loadFloatUnitEnabled().catch(() => {})
  if (!multiUnitCols.value.length) return
  unitCells.syncRowMaterials()
  unitCells.ensureLoaded()
}
watch(() => multiUnitCols.value.length, (n) => { if (n) initMultiUnitRows() })

onMounted(async () => {
  await mcf.init()
  await enrich(props.modelValue)
  initMultiUnitRows()
})
// 页签切回（keep-alive 复用实例）：重新按档案取换算行（2026-11-11），理由同单据明细组件
onActivated(() => {
  initMultiUnitRows()
})

// 行物料变化（选货品/换货品）：清掉旧物料的浮动单位与浮动数量，并按整单批量补换算行
watch(() => (props.modelValue || []).map(r => (r && r.materialId) || '').join('|'), () => {
  initMultiUnitRows()
})
</script>

<style scoped lang="scss">
.material-items { width: 100%; }
.mi-head { display: flex; align-items: center; gap: 10px; margin: 8px 0; }
.mi-title { font-size: 14px; font-weight: 600; color: #303133; }
.mi-count { font-size: 12px; color: #909399; }
.mi-btns { margin-left: auto; display: flex; gap: 8px; }
.mi-code { font-weight: 500; color: #303133; }
.mi-name { font-size: 12px; color: #909399; }
.mi-hint { font-size: 12px; color: #909399; margin-top: 4px; line-height: 1.5; }
/* 单元格（图片/是-否/金额/文本）的样式统一在 MaterialFieldCell 里，本组件不再重复 */
</style>
