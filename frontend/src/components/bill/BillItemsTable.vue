<template>
  <!-- 单据明细通用组件：卡片（可折叠）+ 列设置 + 动态列 / 自定义字段列 + 汇总区
       各单据页只传「列定义 + 插槽」，结构、列设置、自定义字段列、汇总呈现全站共用一套 -->
  <div class="bill-items">
    <div class="collapsible-card" :class="{ collapsed: !collapsed }">
      <div class="card-header" @click="$emit('update:collapsed', !collapsed)">
        <div class="header-left">
          <el-icon class="collapse-icon" :class="{ active: collapsed }"><ArrowRight /></el-icon>
          <span class="card-title">{{ $tt(title) }}</span>
          <span class="item-count">共 {{ items.length }} 行</span>
        </div>
        <div class="header-right" :data-onboarding="onboardingHeader || undefined" @click.stop>
          <slot name="actions" />
          <!-- 扫码加行：扫商品二维码 → 自动加一行并带入商品（2026-09-27 D4） -->
          <el-tooltip v-if="scanAdd && !readonly" :content="$tt('扫商品二维码，自动加一行并带入商品')" placement="top">
            <el-button size="small" type="primary" plain :icon="Aim" @click="openScanAdd">
              {{ $tt("扫码加行") }}
            </el-button>
          </el-tooltip>
          <!-- 列设置齿轮：2026-11-03 从第二行小标题移到标题行右侧（用户口径：一个明细只留一个标签，
               按钮统一 = 标题在左、按钮全在标题行右侧；onboarding-subheader 锚点随之落在齿轮上） -->
          <el-tooltip :content="$tt('列设置')" placement="top">
            <el-button :icon="Setting" circle size="small" v-permission="perm('columnSetting')" :data-onboarding="onboardingSubheader || undefined" @click="openColumnSetting" />
          </el-tooltip>
        </div>
      </div>
      <div v-show="collapsed" class="card-body no-padding">
        <div class="table-wrap">
          <!-- 表头正下方、表格之前的扩展区（如「原料明细」里的 BOM 信息），仅传了插槽才渲染 -->
          <slot name="before-table" />
          <el-table ref="tableRef" :data="items" :key="'detail-' + detailConfigVersion + '-' + batchColKey" class="compact-table" :data-onboarding="onboardingTable || undefined" size="small" border :row-class-name="rowClassName" @row-click="onRowClick" @selection-change="onSelectionChange" @header-dragend="onHeaderDragend">
            <!-- 展开行（可选）：BOM 子件等「行内还有下级结构」的明细页传 expandable + #expand 插槽；
                 默认 false = 不渲染该列，已接入本组件的所有单据页观感与行为完全不变 -->
            <el-table-column v-if="expandable" type="expand" width="44">
              <template #default="{ row }">
                <slot name="expand" :row="row" />
              </template>
            </el-table-column>
            <!-- 多选列（MRP 计划等"运算结果多选"页用）：默认不渲染，保持既有页面零影响 -->
            <el-table-column v-if="selectable" type="selection" width="44" />
            <el-table-column v-if="showIndex" type="index" label="#" width="40" />
            <template v-for="col in visibleColumns" :key="col.prop">
              <!-- 自定义字段列：与基础列走同一个循环 → 列设置的拖拽顺序同样生效；按 fieldType 渲染控件 -->
              <el-table-column
                v-if="col.customField"
                :label="$tf(col.label)"
                :width="col.width"
                align="center"
                :fixed="getDetailColFixed(col.prop)"
              >
                <template #default="{ row }">
                  <el-select v-if="col.fieldType === 'select'" v-model="row[col.prop]" :placeholder="$tt('请选择')" filterable size="small" style="width:100%" :disabled="readonly">
                    <el-option v-for="opt in (col.options || [])" :key="opt.value" :label="$tf(opt.label)" :value="opt.value" />
                  </el-select>
                  <el-input-number
                    v-else-if="col.fieldType === 'number'"
                    v-model="row[col.prop]"
                    :precision="col.precision"
                    :min="0"
                    :step="0.1"
                    controls-position="right"
                    size="small"
                    style="width:100%"
                    :disabled="readonly"
                  />
                  <el-switch v-else-if="col.fieldType === 'switch'" v-model="row[col.prop]" :active-value="1" :inactive-value="0" :disabled="readonly" />
                  <el-date-picker v-else-if="col.fieldType === 'date'" v-model="row[col.prop]" type="date" value-format="YYYY-MM-DD" size="small" style="width:100%" :disabled="readonly" />
                  <el-input v-else v-model="row[col.prop]" size="small" :placeholder="$tt('请输入')" style="width:100%" :disabled="readonly" />
                </template>
              </el-table-column>
              <el-table-column
                v-else
                :prop="col.prop"
                :label="$tf(col.label)"
                :width="col.width"
                :min-width="col.minWidth"
                align="center"
                :show-overflow-tooltip="!!col.tooltip"
                :fixed="getDetailColFixed(col.prop)"
              >
                <template #default="{ row, $index }">
                  <slot :name="'cell-' + col.prop" :row="row" :index="$index" :col="col">
                    <!-- 单位列默认渲染（2026-11-11 统一）：能取到行物料时用统一单位单元格（候选=该物料换算行），
                         与销售订单同源；页面自己写了 #cell-unit / #cell-unitId / #cell-unitName 插槽时以页面为准 -->
                    <UnitPickerCell
                      v-if="isUnitColumn(col.prop) && unitCells.materialOf(row)"
                      :model-value="rowUnitId(row)"
                      :material="unitCells.materialOf(row)"
                      :units="unitCells.units.value"
                      :disabled="readonly"
                      @change="onUnitPicked(row, $event)"
                    />
                    <!-- 多单位换算 5 列（2026-11-10 自动注入）：前 3 列只读，取值随行数量/单位实时算出 -->
                    <!-- 无固定换算行时副单位不可选，按默认规则显示单位名称 -->
                    <span v-if="col.prop === 'subUnitName' && unitCells.subOptions(row).length === 0">{{ unitCells.subUnitName(row) }}</span>
                    <!-- 副单位（2026-11-10 可按行手选）：候选 = 该物料档案里的固定换算单位；清空回落默认规则 -->
                    <el-select
                      v-else-if="col.prop === 'subUnitName'"
                      v-model="row.subUnitId"
                      :placeholder="$tt('副单位')"
                      clearable
                      size="small"
                      style="width:100%"
                      :disabled="readonly || unitCells.subOptions(row).length === 0"
                    >
                      <el-option v-for="o in unitCells.subOptions(row)" :key="o.value" :label="o.label" :value="o.value" />
                    </el-select>
                    <span v-else-if="col.prop === 'subQuantity'">{{ unitCells.subUnitQtyText(row) }}</span>
                    <span v-else-if="col.prop === 'auxQuantityText'">{{ unitCells.auxQtyText(row) }}</span>
                    <!-- 浮动单位：候选 = 该物料换算行里方式为浮动的单位；没有候选不可选 -->
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
                      @change="onFloatQuantityChange(row, col)"
                    />
                    <el-select
                      v-else-if="col.editor === 'select'"
                      v-model="row[col.prop]"
                      filterable
                      :clearable="col.clearable !== false"
                      :placeholder="$tt(col.placeholder || '请选择')"
                      size="small"
                      style="width:100%"
                      :disabled="readonly"
                      @change="onCellChange(row, col)"
                    >
                      <el-option v-for="o in optionsOf(col, row)" :key="o.value" :label="$tf(o.label)" :value="o.value" />
                    </el-select>
                    <el-date-picker
                      v-else-if="col.editor === 'date'"
                      v-model="row[col.prop]"
                      type="date"
                      value-format="YYYY-MM-DD"
                      size="small"
                      style="width:100%"
                      :disabled="readonly"
                      @change="onCellChange(row, col)"
                    />
                    <!-- 浮动换算率：只读，由系统按「基本单位数量 ÷ 浮动数量」算出（2026-11-11，填单人不用自己算） -->
                    <span v-else-if="col.prop === 'floatConversionRate'" class="auto-qty">{{ unitCells.floatRateText(row) }}</span>
                    <el-input-number
                      v-else-if="col.editor === 'number'"
                      v-model="row[col.prop]"
                      :controls="false"
                      :min="col.min === undefined ? 0 : col.min"
                      :max="col.max"
                      :precision="precisionFor(col, col.precision === undefined ? 4 : col.precision)"
                      style="width:100%"
                      size="small"
                      :disabled="readonly"
                      @change="onNumberCellChange(row, col)"
                    />
                    <el-switch
                      v-else-if="col.editor === 'switch'"
                      v-model="row[col.prop]"
                      :active-value="1"
                      :inactive-value="0"
                      :disabled="readonly"
                      @change="onCellChange(row, col)"
                    />
                    <el-input
                      v-else-if="col.editor === 'text'"
                      v-model="row[col.prop]"
                      size="small"
                      :disabled="col.prop === 'batchNo' ? batchDisabled(row, readonly) : readonly"
                      @change="onCellChange(row, col)"
                    />
                    <!-- 图片列：统一交给 MaterialFieldCell（全站唯一一份渲染），此处不再自写分支 -->
                    <MaterialFieldCell v-else-if="col.type === 'image'" :col="col" :row="row" />
                    <!-- 来源单号列（type:'sourceLink'）：与销售合同/销售订单模板同一写法，点击跳来源单；空值显示空白（用户口径：禁占位符） -->
                    <el-link v-else-if="col.type === 'sourceLink' && row[col.prop]" type="primary" :underline="false" @click="onSourceLinkClick(row)">{{ row[col.prop] }}</el-link>
                    <span v-else-if="col.type === 'netAmount'" class="net-amount">{{ fmtAmount(row[col.prop]) }}</span>
                    <!-- 金额列：统一交给 MaterialFieldCell（与物料列同一份口径：空值 0.00 / 0.0000，保留 col.cellClass） -->
                    <MaterialFieldCell v-else-if="col.type === 'amount2' || col.type === 'amount4'" :col="col" :row="row" />
                    <span v-else-if="col.type === 'billStock'" :style="Number(row[col.prop]) < 0 ? 'color:#F56C6C;font-weight:600;' : ''">{{ row[col.prop] != null ? formatWith(row[col.prop], scaleFor('qty'), trimZero) : '' }}</span>
                    <!-- 其余带类型列（是-否/文本…，含物料档案列）：统一交给 MaterialFieldCell（全站唯一一份渲染） -->
                    <MaterialFieldCell v-else-if="col.type" :col="col" :row="row" />
                    <span v-else>{{ row[col.prop] }}</span>
                  </slot>
                </template>
              </el-table-column>
            </template>
            <!-- 操作列：默认渲染内置「删行」按钮；页面传 :operation-column="false" 可整列隐藏，
                 或用 #operation 插槽自定义（如按状态切换成"删除/进度流程"） -->
            <el-table-column
              v-if="operationColumn || $slots.operation"
              :label="$tf('操作')"
              :width="operationWidth"
              align="center"
              fixed="right"
            >
              <template #default="{ row, $index }">
                <slot name="operation" :row="row" :index="$index">
                  <el-button text type="danger" size="small" v-permission="perm('deleteRow')" :disabled="readonly" @click="removeRow($index)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </slot>
              </template>
            </el-table-column>
            <!-- 空态：仅当页面显式传 #empty 时接管；否则保持 el-table 原生空态，
                 避免改变已迁移的 30 个页面的既有观感 -->
            <template v-if="$slots.empty" #empty>
              <slot name="empty" />
            </template>
          </el-table>
        </div>

        <div v-if="showSummary" class="summary-area">
          <div class="summary-block">
            <div v-if="showTotalQty" class="sum-line">
              <span class="label">{{ $tt("合计数量：") }}</span>
              <span class="value">{{ qtyText }}</span>
            </div>
            <div v-for="(s, i) in summaryItems" :key="i" class="sum-line" :class="{ total: !!s.total }">
              <span class="label">{{ $tt(s.label) }}</span>
              <span class="value" :class="s.class">{{ s.value }}</span>
            </div>
          </div>
          <slot name="summary-extra" />
        </div>
      </div>
    </div>

    <ColumnSettingDrawer
      v-model="columnSettingVisible"
      :page-key="pageKey"
      :columns="allColumns"
      :title="$tt(colSettingTitle || (title + '列设置'))"
      @change="onColumnSettingChange"
    />

    <!-- 扫码加行：电脑端扫码枪 / 粘贴码内容（2026-09-27 D4） -->
    <el-dialog v-model="scanAddVisible" :title="$tf('扫码加行')" width="460px" append-to-body>
      <div class="scan-tip">{{ $tt("用扫码枪扫商品二维码，或粘贴码内容后回车") }}</div>
      <el-input
        ref="scanAddInputRef"
        v-model="scanAddCode"
        :placeholder="$tt('扫码 / 粘贴商品二维码内容')"
        clearable
        @keyup.enter="doScanAdd"
      />
      <div class="scan-tip scan-tip--muted">
        {{ $tt("扫到的商品会自动新增一行并带入商品信息，数量默认 1，请再补充仓库等信息") }}
      </div>
      <template #footer>
        <el-button @click="scanAddVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" @click="doScanAdd">{{ $tt("确定") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onActivated, ref, watch, nextTick } from 'vue'
import { ArrowRight, Setting, Delete, Aim } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
import MaterialFieldCell from '@/components/MaterialFieldCell.vue'
// 统一单位单元格（2026-11-11）：单位列默认渲染用它 —— 候选=该物料档案的换算单位，选完显示「1 单位 = X 基本单位」
import UnitPickerCell from '@/components/bill/UnitPickerCell.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { useBatchField } from '@/composables/useBatchField'
import { usePagePerm } from '@/utils/pagePerm'
import { withFallbackOption } from '@/utils/labelFallback'
import { parseScanCode } from '@/utils/billScan'
import scanCodeApi from '@/api/system/scanCode'
import { tt } from '@/i18n'
import { useCommercialSwitches, TAX_RELATED_COLUMN_PROPS } from '@/composables/useCommercialSwitches'
import { usePrecision } from '@/composables/usePrecision'
// 物料档案全字段并入列设置 + 行数据补齐（2026-11-03 全站统一口径：带物料明细的地方，物料档案字段全都有）
import { getMaterial } from '@/api/basedata/material'
import { mergeMaterialArchiveColumns, materialArchiveRowFields } from '@/composables/useMaterialCustomFields'
// 多单位换算 5 列（2026-11-10）：明细列定义里有「单位」列就自动注入，页面零改动；取值逻辑只在本处一份
import { useMultiUnitCells, multiUnitColumnsFor, mergeMultiUnitColumns } from '@/components/bill/multiUnitColumns'
import { loadConversions, loadFloatUnitEnabled, floatUnitEnabled } from '@/composables/useUnitConvert'

const perm = usePagePerm()
// 数值列位数/格式：由设置中心「小数位数设置」统一控制
const { precisionFor, fmtAmount, scaleFor, trimZero, formatWith } = usePrecision()
// 全站商业口径总开关：含税业务关闭 → 整列隐藏税率相关列（税率%/税额/计税方式/含税价/不含税价）
const { taxEnabled, ensureLoaded: ensureCommercialSwitches } = useCommercialSwitches()


const props = defineProps({
  items: { type: Array, required: true },
  pageKey: { type: String, required: true },
  title: { type: String, default: '明细' },
  // 已停用渲染（2026-11-03 用户口径：一个明细只留一个标签）；参数保留兼容各页既有传参，不再显示
  subtitle: { type: String, default: '' },
  colSettingTitle: { type: String, default: '' },
  columns: { type: Array, default: () => [] },
  customColumns: { type: Array, default: () => [] },
  readonly: { type: Boolean, default: false },
  collapsed: { type: Boolean, default: true },
  summaryItems: { type: Array, default: () => [] },
  totalQty: { type: [String, Number], default: null },
  /** 汇总区是否显示「合计数量」行（盘点单等原本没有该行 → 传 false 保持原样） */
  showTotalQty: { type: Boolean, default: true },
  /** 来源单号列(type:'sourceLink')点击回调（销售合同模板口径：跳转到来源单） */
  sourceLinkClick: { type: Function, default: null },
  /** 新手引导锚点（可选，原样透传 data-onboarding） */
  onboardingHeader: { type: String, default: '' },
  onboardingSubheader: { type: String, default: '' },
  onboardingTable: { type: String, default: '' },
  /** 是否渲染多选列（MRP 计划等"运算结果多选"页）。默认 false = 旧行为不变 */
  selectable: { type: Boolean, default: false },
  /** 是否渲染「展开行」列（BOM 子件等带下级结构的明细页用）。默认 false = 旧行为不变 */
  expandable: { type: Boolean, default: false },
  /** 是否渲染内置「操作」列（含删行按钮）。默认 true = 旧行为不变；false 时若提供了 #operation 插槽仍会渲染 */
  operationColumn: { type: Boolean, default: true },
  /** 「操作」列宽度（部分页面需要并排放两个按钮时调宽） */
  operationWidth: { type: [Number, String], default: 55 },
  /** 是否渲染底部汇总区（MRP 运算结果等无汇总的页传 false） */
  showSummary: { type: Boolean, default: true },
  /** 是否渲染行号列（MRP 运算结果原表无行号列 → 传 false） */
  showIndex: { type: Boolean, default: true },
  /** 是否显示「扫码加行」按钮（扫商品二维码自动加一行，2026-09-27 D4）；只读页可传 false */
  scanAdd: { type: Boolean, default: true },
  /** 行点击回调（2026-11-10）：页面传 true 才向外抛 row-click，默认关闭 = 其它单据页行为零变化 */
  rowClickable: { type: Boolean, default: false },
  /** 行高亮 class 计算函数（可选，原样透传给 el-table 的 row-class-name；默认不设 = 旧观感不变） */
  rowClassName: { type: [Function, String], default: undefined }
})
const emit = defineEmits(['update:collapsed', 'row-change', 'remove', 'selection-change', 'scan-material', 'row-click'])

/** 行点击：只有页面显式开启 rowClickable 才向外抛，保证其余明细页点击行无任何副作用 */
function onRowClick(row, column, event) {
  if (!props.rowClickable) return
  emit('row-click', row, column, event)
}

// 批次字段联动：明细里没有批次管理物料时整列不渲染；规则不允许修改时单元格只读
const { loadBatchSetting, batchDisabled, batchColumnHidden } = useBatchField()

// 含税业务总开关关闭时：税率相关列整列不渲染（税率%/税额/计税方式/含税价/不含税价）
function passCommercialColumnFilter(c) {
  if (!c || !c.prop) return true
  if (!taxEnabled.value && TAX_RELATED_COLUMN_PROPS.includes(c.prop)) return false
  return true
}

// 列设置用的完整列清单 = 业务列 + 物料档案列 + 自定义字段列（自定义字段列展开原始定义，保留 fieldType/options 等渲染信息）
// 物料档案列（2026-11-03）：明细列定义里有物料（materialId 列）才并入；全部默认隐藏（默认展示 = 各页现状），
// 排在业务列之后、自定义字段之前；页面已有同名列自动去重（业务列优先）。
// 「物料」列识别口径：prop 以 materialId 结尾（忽略大小写）——标准物料列（materialId）与"子件物料"（childMaterialId，打样单等同族列）都算
const materialIdProp = computed(() => {
  const c = (props.columns || []).find(c => c && c.prop && /materialId$/i.test(c.prop))
  return c ? c.prop : null
})
const materialArchiveCols = computed(() => (materialIdProp.value ? mergeMaterialArchiveColumns(props.columns) : []))

// 多单位换算列（2026-11-10）：明细列定义里有「单位」列（unitId / unit / unitName）就注入 5 列，
// 插在单位列之后；页面自己已有同名列则不重复加（幂等）。没有单位列的明细自动不注入。
const multiUnitCols = computed(() => multiUnitColumnsFor(props.columns))

const allColumns = computed(() => [
  ...mergeMultiUnitColumns(props.columns).filter(passCommercialColumnFilter),
  ...materialArchiveCols.value.filter(passCommercialColumnFilter),
  ...props.customColumns.map(c => ({ ...c, customField: true, defaultHidden: !c.isListShow }))
])

const {
  columnSettingVisible,
  openColumnSetting,
  isColVisible: isDetailColVisible,
  getColFixed: getDetailColFixed,
  configVersion: detailConfigVersion,
  sortedColumns,
  // 列宽持久化（2026-09-22 新增）：明细表本来就有 border（可拖），这里补"拖完记住 + 下次恢复"
  onHeaderDragend,
  applySavedWidths,
  resetColWidths
} = useColumnSetting(props.pageKey, allColumns)

/** 明细表列宽恢复：挂载后 + 列配置变化后 + 数据加载完成后各套一次 */
const tableRef = ref(null)
function restoreColumnWidths() {
  nextTick(() => applySavedWidths(tableRef))
}
// 先装载商业口径开关（含税/外币），再恢复列宽：保证"关闭含税"时税率列一开始就不渲染
onMounted(() => {
  loadBatchSetting()
  ensureCommercialSwitches().finally(restoreColumnWidths)
  enrichMaterialArchive()
  initMultiUnit()
})
// 页签切回（keep-alive 复用实例）：重新按档案取换算行（2026-11-11）
// 档案改完切回单据页签时不刷新浏览器，缓存已被作废 → 这里补一次整单取数，避免仍显示旧单位
onActivated(() => {
  refreshUnitRows()
})
watch(() => detailConfigVersion.value, restoreColumnWidths)
watch(() => props.items, () => restoreColumnWidths(), { deep: false })
// 列设置抽屉里「恢复默认列宽」需要组件重排表格
function onColumnSettingChange(e) {
  if (e && e.type === 'resetWidths') nextTick(() => resetColWidths(tableRef))
}

// 可见列 = 基础列 + 自定义字段列，统一按列设置顺序排列
// （自定义字段列也跟随列设置拖拽顺序，模板中按 col.customField 分支渲染控件）
const visibleColumns = computed(() =>
  sortedColumns.value
    .filter(c => isDetailColVisible(c.prop))
    .filter(passCommercialColumnFilter)
    // 明细全部为非批次管理物料 → 批次号列整列不渲染（按物料，不再有全局开关）
    .filter(c => !(c.prop === 'batchNo' && batchColumnHidden(props.items)))
    // 全局开关「启用浮动单位」关闭 → 浮动单位/浮动数量两列整列不渲染（2026-11-10）
    .filter(c => !(c.floatOnly && !floatUnitEnabled.value))
)

/**
 * 批次号列的显隐会随「选中/移除批次管理物料」变化，而 el-table 对列集合的动态增减
 * 不会可靠重渲染（表头不刷新）。这里把显隐状态并进表格 key：状态翻转时整表重挂载一次，
 * 保证表头与单元格一致（只在第一次选中批次物料/全部移除时发生一次）。
 */
const batchColKey = computed(() => (!props.items || props.items.length === 0 ? 'b' : (batchColumnHidden(props.items) ? 'nb' : 'b')))

const qtyText = computed(() =>
  props.totalQty != null
    ? props.totalQty
    : formatWith(props.items.reduce((sum, r) => sum + Number(r.quantity || 0), 0), scaleFor('qty'), trimZero.value)
)

/** 原始档案对象（{stockId,stockName} 等）→ el-option 需要的 {value,label}，避免明细里露出数字主键 */
const RAW_OPTION_KEYS = [
  ['stockId', 'stockName'], ['warehouseId', 'warehouseName'], ['binId', 'binName'],
  ['unitId', 'unitName'], ['employeeId', 'employeeName'], ['customerId', 'customerName'],
  ['supplierId', 'supplierName'], ['materialId', 'materialName'], ['productId', 'productName'],
  ['deptId', 'deptName'], ['settleModeId', 'settleModeName'], ['accountId', 'accountName']
]
function normalizeOptions (list) {
  return (list || []).map(o => {
    if (!o || typeof o !== 'object') return o
    if (o.value !== undefined) return o
    for (const [idKey, nameKey] of RAW_OPTION_KEYS) {
      if (o[idKey] !== undefined) {
        return { ...o, value: o[idKey], label: o[nameKey] !== undefined && o[nameKey] !== null ? o[nameKey] : String(o[idKey]) }
      }
    }
    return o
  })
}

function optionsOf(col, row) {
  let list
  if (typeof col.optionsFn === 'function') list = col.optionsFn(row) || []
  else if (typeof col.options === 'function') list = col.options(row) || []
  else list = col.options || []
  // 兜底：当前值不在候选项里时，用行上的名称字段临时补一个显示项（避免明细里露出数字主键）
  return withFallbackOption(normalizeOptions(list), col.prop, col.nameProp, row)
}

function onSourceLinkClick(row) {
  if (typeof props.sourceLinkClick === 'function') props.sourceLinkClick(row)
}

function onCellChange(row, col) {
  if (typeof col.change === 'function') col.change(row)
  emit('row-change', row, col.prop)
}

/** 主数量列（quantity / qty）：填了浮动单位时，它变了要同步重算浮动换算率 */
function isMainQtyProp(prop) {
  return /^(quantity|qty)$/i.test(String(prop || ''))
}

/** 浮动数量变化：重算基本单位数量快照与浮动换算率（= 基本单位数量 ÷ 浮动数量），再走页面原有联动 */
function onFloatQuantityChange(row, col) {
  unitCells.onFloatQuantityChange(row)
  onCellChange(row, col)
}

/** 普通数值列变化：主数量（数量/基本单位数量）改了也要重算浮动换算率 */
function onNumberCellChange(row, col) {
  if (isMainQtyProp(col.prop)) unitCells.onMainQtyChange(row)
  onCellChange(row, col)
}

function onSelectionChange(rows) {
  emit('selection-change', rows || [])
}

function removeRow(index) {
  props.items.splice(index, 1)
  emit('remove', index)
}

// ===== 多单位换算单元格能力（2026-11-10）：物料实体 / 单位名 / 换算行缓存 + 5 列取值 =====
// 物料列识别：标准物料列 materialId / 子件物料 childMaterialId / 组装拆卸明细 product_id（2026-11-11 统一）
const UNIT_MATERIAL_RE = /(materialId|product_?id|childMaterialId)$/i
const unitMaterialIdProp = computed(() => {
  const c = (props.columns || []).find(c => c && c.prop && UNIT_MATERIAL_RE.test(c.prop))
  return c ? c.prop : null
})
const unitCells = useMultiUnitCells({
  getIdProp: () => unitMaterialIdProp.value || materialIdProp.value,
  getRows: () => props.items
})

/** 单位列识别（与多单位列注入同口径；页面自定义插槽优先，不受这里影响） */
const UNIT_CELL_PROPS = ['unitId', 'unit', 'unitName']
function isUnitColumn(prop) {
  return UNIT_CELL_PROPS.includes(String(prop || ''))
}

/** 行当前单位主键（兼容下划线命名，如组装单 unit_id） */
function rowUnitId(row) {
  if (!row) return null
  return row.unitId != null ? row.unitId : (row.unit_id != null ? row.unit_id : null)
}

/** 统一单位单元格选择后：写回单位主键/名称 + 换算率快照，然后走页面原有行联动（金额重算等） */
function onUnitPicked(row, e) {
  if (!row) return
  const unitId = e && e.unitId != null ? e.unitId : null
  row.unitId = unitId
  if ('unit_id' in row) row.unit_id = unitId
  const u = (unitCells.units.value || []).find(x => x.unitId === unitId)
  const name = u ? u.unitName : ''
  if ('unit' in row) row.unit = name
  if ('unitName' in row) row.unitName = name
  row.conversionRate = e && e.rate != null ? e.rate : null
  emit('row-change', row, 'unitId')
}

// ===== 物料档案字段补齐（2026-11-03 全站统一口径）=====
// 勾出的档案列（规格型号/物料分类/单价/最新成本…）要有值：按行物料ID取档案补到行上。
// 保护口径：只接管「本轮自己补过的字段」和「行上尚未声明的字段」——页面自己声明的业务字段
// （如行备注、行税率）一律不碰；换料后按 _archFor 标记自动重取刷新。
const matArchiveCache = new Map()
const matArchiving = ref(false)
async function enrichMaterialArchive() {
  const idProp = materialIdProp.value
  if (!idProp || matArchiving.value) return
  const rows = (props.items || []).filter(r => r && r[idProp] != null && r._archFor !== String(r[idProp]))
  if (!rows.length) return
  matArchiving.value = true
  try {
    const fields = materialArchiveRowFields(props.columns)
    await Promise.all(rows.map(async r => {
      const id = r[idProp]
      let m = matArchiveCache.get(id)
      if (m === undefined) {
        try { m = (await getMaterial(id)) || null } catch (e) { m = null }
        matArchiveCache.set(id, m)
      }
      // 同一份物料实体回填给多单位单元格（副单位/副单位数量/浮动单位要用基本单位与是否辅计量），避免二次请求
      unitCells.remember(id, m)
      r._archFor = String(id)
      const taken = Array.isArray(r._archKeys) ? r._archKeys.slice() : []
      fields.forEach(k => {
        if (taken.includes(k)) {
          if (m && m[k] !== undefined) r[k] = m[k]
          return
        }
        if (!(k in r) && m && m[k] !== undefined) {
          r[k] = m[k]
          taken.push(k)
        }
      })
      r._archKeys = taken
    }))
  } finally {
    matArchiving.value = false
  }
}
watch(() => {
  const idProp = unitMaterialIdProp.value || materialIdProp.value
  return (props.items || []).map(r => (r && idProp ? r[idProp] : '') || '').join('|')
}, () => {
  enrichMaterialArchive()
  refreshUnitRows()
})

// ===== 多单位换算（2026-11-10）：整单一次预取 + 换料清空浮动字段 =====
// 只在注入了多单位列时工作（没有「单位」列的明细 = 完全不动，旧行为零变化）
function hasMultiUnitColumns() {
  return multiUnitCols.value.length > 0
}

/** 挂载时：读浮动单位总开关 + 单位名 + 行上已有的换算行 */
function initMultiUnit() {
  // 开关必须无条件读：列定义可能晚于挂载就绪（自定义字段/档案列异步），
  // 若被 hasMultiUnitColumns 挡住，浮动三列会永远不渲染（2026-11-11 实测复现）
  loadFloatUnitEnabled().catch(() => {})
  if (!hasMultiUnitColumns()) return
  unitCells.loadUnits()
  refreshUnitRows()
}
// 列定义异步就绪后再补一次预取（挂载时列还不存在的情况）
watch(() => multiUnitCols.value.length, (n) => { if (n) initMultiUnit() })

/** 行物料变化后：清掉旧物料的浮动单位/数量，并按整单一次性批量取换算行（禁止逐行请求） */
function refreshUnitRows() {
  if (!hasMultiUnitColumns()) return
  unitCells.syncRowMaterials()
  const idProp = unitMaterialIdProp.value || materialIdProp.value
  if (!idProp) return
  const ids = (props.items || [])
    .map(r => (r && r[idProp] != null ? r[idProp] : null))
    .filter(id => id != null)
  if (ids.length) {
    // 物料实体 = 副单位/浮动单位候选与折算率的计算依据（2026-11-11）：组件内部按需批量取一次并缓存
    unitCells.ensureMaterials(ids)
    loadConversions(ids)
  }
}

// ===== 扫码加行（2026-09-27 D4）：扫商品二维码 → 新增一行并带入商品 =====
const scanAddVisible = ref(false)
const scanAddCode = ref('')
const scanAddInputRef = ref(null)

function openScanAdd() {
  scanAddCode.value = ''
  scanAddVisible.value = true
  nextTick(() => scanAddInputRef.value?.focus())
}

async function doScanAdd() {
  const parsed = parseScanCode(scanAddCode.value)
  if (!parsed) {
    ElMessage.warning(tt('不是本系统的二维码'))
    return
  }
  if (parsed.type !== 'MAT') {
    ElMessage.warning(tt('请扫商品二维码（当前不是商品码）'))
    return
  }
  if (parsed.id == null) {
    ElMessage.warning(tt('二维码内容不完整，无法识别商品'))
    return
  }
  try {
    const res = await scanCodeApi.resolve('MAT', null, parsed.id, parsed.signature)
    // 服务端校验（P1）：签名防伪造 / 商品停用 / 落地权限
    if (res && res.valid === false) {
      ElMessage.error(res.invalidReason || tt('该二维码不可用'))
      return
    }
    const m = res && res.material
    if (!m) {
      ElMessage.warning(tt('没找到该商品，可能已被删除'))
      return
    }
    const row = buildScannedRow(m)
    // 与组件的删行保持同一套"由父级持有数组"的口径（避免重复声明 props 引用）
    const itemsRef = props.items
    itemsRef.push(row)
    emit('scan-material', m, row)
    scanAddVisible.value = false
    ElMessage.success(`${tt('已带入')}：${m.materialName || m.materialCode || ''}`)
  } catch (e) {
    ElMessage.error(tt('扫码加行失败'))
    console.error('doScanAdd error:', e)
  }
}

function setFirstKey(row, keys, value) {
  for (const k of keys) {
    if (k in row) {
      row[k] = value
      return true
    }
  }
  return false
}

/** 造新行：优先复制上一行的字段结构（继承各页自己的行形状），清掉主键/来源并数值归零，再带入商品 */
function buildScannedRow(m) {
  const rows = props.items || []
  const row = {}
  if (rows.length) {
    const tpl = rows[rows.length - 1] || {}
    Object.keys(tpl).forEach(k => {
      if (k.startsWith('_') || k === 'rowKey') return
      row[k] = tpl[k]
    })
    Object.keys(row).forEach(k => {
      if (/^(id|.*ItemId|.*DetailId|lineNo)$/i.test(k) || /^source/i.test(k)) {
        delete row[k]
        return
      }
      if (typeof row[k] === 'number') row[k] = 0
    })
  } else {
    ;(props.columns || []).forEach(c => {
      if (!c || !c.prop || String(c.prop).startsWith('__')) return
      row[c.prop] = c.defaultValue !== undefined ? c.defaultValue : ''
    })
  }
  setFirstKey(row, ['materialId'], m.materialId)
  setFirstKey(row, ['materialCode'], m.materialCode)
  setFirstKey(row, ['materialName'], m.materialName)
  setFirstKey(row, ['specification', 'spec', 'model'], m.model || m.specification)
  setFirstKey(row, ['unit', 'unitName', 'baseUnitName'], m.baseUnitName)
  setFirstKey(row, ['baseUnitId'], m.baseUnitId)
  setFirstKey(row, ['quantity', 'qty', 'planQuantity', 'requiredQuantity'], 1)
  return row
}
</script>

<style scoped lang="scss">
// 扫码加行弹窗（2026-09-27）
.scan-tip {
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
}
.scan-tip--muted {
  margin-top: 8px;
  margin-bottom: 0;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
.bill-items {
  .collapsible-card {
    background: #fff;
    border: 1px solid #e4e7ed;
    border-radius: 6px;
    // 区块间距可调（设置中心 → 界面尺寸设置 → 区块间距）；未配置时 = 10px 原值
    margin-bottom: var(--erp-form-section-gap, 10px);

    &.collapsed .card-header { border-bottom: none; }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: var(--erp-table-cell-pad-y, 5px) var(--erp-card-padding, 10px);
      background: #fafbfc;
      border-bottom: 1px solid #e4e7ed;
      cursor: pointer;
      user-select: none;

      .header-left { display: flex; align-items: center; gap: 8px; }
      .header-right { display: flex; gap: 6px; }

      .collapse-icon {
        color: #909399;
        font-size: 12px;
        transition: transform .25s ease;
        &.active { transform: rotate(90deg); color: #409eff; }
      }

      .card-title { font-size: 13px; font-weight: 600; color: #303133; }
      .item-count { font-size: 11px; color: #909399; font-weight: 400; }
    }

    .card-body {
      padding: var(--erp-card-padding, 10px);
      &.no-padding { padding: 0; }
    }
  }

  // .table-wrap / .detail-subtable-header 由全局 styles/index.scss 统一定义（不设外层 overflow-x，
  // 只用 el-table 内置的 body-wrapper 滚动条 → 避免出现"两个滑动托盘"），这里不再覆盖。

  .compact-table {
    // 不设 min-width（同全局口径）：el-table 宽度 = 父容器 100%，列总宽超出时由 table 自身出 1 条滚动条
    font-size: 12px;

    :deep(.el-table__header th) {
      background: #f5f7fa !important;
      color: #606266;
      font-weight: 600;
      font-size: 12px;
      padding: 6px 4px;
    }
    :deep(.el-table__body td) { padding: 6px 4px; }
    :deep(.el-table__row:hover > td) { background: #f5f7fa !important; }
    // 明细内的输入框/下拉一并居中（2026-11-10 用户口径：整个明细组件与数值都居中）
    :deep(.el-input__inner),
    :deep(.el-select__selected-item),
    :deep(.el-select__placeholder) {
      text-align: center;
    }
  }

  // 浮动数量折算出来的数量：只读展示，与输入框观感区分为普通文字
  .auto-qty {
    display: block;
    text-align: center;
    color: #606266;
  }

  .material-thumb {
    width: 32px;
    height: 32px;
    border-radius: 4px;
    border: 1px solid #ebeef5;
    cursor: pointer;
  }

  .thumb-placeholder { color: #c0c4cc; font-size: 12px; }
  .discount-text { color: #e6a23c; }
  .net-amount { color: #409eff; font-weight: 600; }

  .summary-area {
    display: flex;
    flex-wrap: wrap;
    gap: 16px;
    padding: 12px 14px;
    background: #fafbfc;
    border-top: 1px solid #ebeef5;
  }

  .summary-block {
    flex: 1;
    min-width: 200px;

    .block-title {
      font-size: 11px;
      font-weight: 600;
      color: #409eff;
      margin-bottom: 6px;
      padding-bottom: 4px;
      border-bottom: 1px solid #ebeef5;
    }

    .sum-line {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 3px 0;
      font-size: 12px;

      .label { color: #606266; }
      .value {
        color: #303133;
        font-weight: 500;
        font-variant-numeric: tabular-nums;
        &.discount { color: #e6a23c; }
      }

      &.total {
        padding-top: 5px;
        margin-top: 3px;
        border-top: 1px dashed #dcdfe6;
        font-size: 13px;

        .label { font-weight: 600; color: #303133; }
        .value { color: #409eff; font-weight: 700; font-size: 14px; }
      }
    }
  }
}
</style>
