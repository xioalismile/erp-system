<template>
  <el-dialog
    v-model="visible"
    :title="$tf(title)"
    :width="width"
    :close-on-click-modal="false"
  >
    <!-- 搜索区：左侧搜索条件自己换行；右侧按钮固定在第一行、贴右边框，高度与搜索框一致 -->
    <div class="picker-search-bar">
      <div class="erp-search-bar picker-search-conds">
        <div class="erp-search-item">
          <el-input
            v-model="keyword"
            :placeholder="$tt('输入物料编码/名称/规格搜索')"
            clearable
            style="width:320px;"
            @keyup.enter="doSearch(true)"
          />
        </div>
        <div class="erp-search-item">
          <el-select v-model="categoryId" :placeholder="$tt('物料分类')" clearable style="width:160px;" @change="doSearch(true)">
            <el-option v-for="c in categories" :key="c.categoryId" :label="c.categoryName" :value="c.categoryId" />
          </el-select>
        </div>
        <!-- 物料自定义字段：勾了「可搜索」的字段 → 各一个输入框（无字段时渲染 0 个节点） -->
        <CustomFieldSearchBar :fields="cf.searchFields.value" :model="cf.values" @change="cf.setValue" @search="doSearch(true)" />
      </div>
      <div class="picker-search-actions">
        <el-button type="primary" v-permission="perm('query')" @click="doSearch(true)">{{ $tt("搜索") }}</el-button>
        <el-tooltip :content="$tt('列设置')" placement="top">
          <el-button :icon="Setting" circle v-permission="perm('columnSetting')" @click="openColumnSetting" />
        </el-tooltip>
      </div>
    </div>

    <el-table
      :data="visibleRows"
      :key="'material-picker-' + configVersion"
      height="380"
      border
      row-key="materialId"
      @selection-change="onSelectionChange"
    >
      <el-table-column type="selection" width="50" fixed="left" />
      <!-- 动态列：物料全部字段 + 全部自定义字段；默认精简列，其余在「列设置」里勾出 -->
      <template v-for="col in visibleColumns" :key="col.prop">
        <el-table-column
          :prop="col.prop"
          :label="$tf(col.label)"
          :width="col.width"
          :min-width="col.minWidth"
          :align="col.align || 'left'"
          :fixed="getColFixed(col.prop, col.fixed)"
        >
          <!-- 单元格统一由 MaterialFieldCell 渲染（图片/是-否/金额/文本），与「物料明细表」共用同一份逻辑 -->
          <template #default="{ row }">
            <MaterialFieldCell :col="col" :row="row" />
          </template>
        </el-table-column>
      </template>
    </el-table>

    <el-pagination
      v-model:current-page="page.current"
      v-model:page-size="page.size"
      :total="page.total"
      layout="total, prev, pager, next"
      style="margin-top:12px;justify-content:flex-end;display:flex;"
      @current-change="doSearch(false)"
    />

    <template #footer>
      <el-button v-permission="perm('dlgCancel')" @click="visible = false">{{ $tt("取消") }}</el-button>
      <el-button type="primary" v-permission="perm('dlgConfirm')" :disabled="selected.length === 0" @click="handleConfirm">
        {{ $tt("确认添加") }}（{{ selected.length }} {{ $tt("项") }}）
      </el-button>
    </template>
  </el-dialog>

  <!-- 列设置抽屉（page-key 全局共享：配一次，所有单据页的物料库弹窗都生效） -->
  <ColumnSettingDrawer
    v-model="columnSettingVisible"
    page-key="MaterialPicker"
    :columns="pickerColumnsWithCustom"
    :title="$tt('物料库列设置')"
  />
</template>

<script setup>
/**
 * 物料库选择弹窗（通用件）
 *
 * 为什么抽公共：这段弹窗在 20+ 个单据编辑页里各复制了一份（各自一份分页、取值、列设置、图片渲染），
 * 改一处要改 20 多遍且极易漂移。本组件把「搜索 + 全字段/自定义字段列 + 列设置 + 分页 + 多选确认」
 * 收成一份，各页只需：
 *   <MaterialPickerDialog v-model="pickerVisible" @confirm="onMaterialPicked" />
 *
 * 单元格渲染：**统一交给 MaterialFieldCell**（与「物料明细表」同一份），本组件不再自带渲染分支，
 *   避免"图片/是-否/金额"两处各写一遍、新增列类型要改两处导致的漂移。
 *
 * 搜索区布局（用户 2026-09-15 口径）：
 *   - 每个勾了「可搜索」的物料自定义字段 → 一个搜索输入框（CustomFieldSearchBar）
 *   - 搜索区用全局 .erp-search-bar（已带 flex-wrap）→ 字段多/窗口窄时**自动换行**
 *   - 「搜索」「列设置」两个按钮用 spacer 占满剩余宽度 → **靠右贴边框**
 *
 * 取值：物料自定义字段值由本组件统一批量取（≤500/批），合并进当前页行对象，
 *   动态列（prop = fieldKey）直接绑定即可渲染；确认时把值表一并回传，父页可复用（不再二次请求）。
 *
 * 容错：任何一步失败都静默降级，**绝不因自定义字段问题阻塞选料或弹窗操作**。
 */
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Setting } from '@element-plus/icons-vue'
import { pageMaterial } from '@/api/basedata/material'
import { listAllMaterialCategory } from '@/api/basedata/material-category'
import { useCustomFieldSearch } from '@/composables/useCustomFieldSearch'
import { useMaterialCustomFields, materialPickerColumns } from '@/composables/useMaterialCustomFields'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('basedata:material')
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
import CustomFieldSearchBar from '@/components/CustomFieldSearchBar.vue'
import MaterialFieldCell from '@/components/MaterialFieldCell.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '从物料库选择' },
  width: { type: String, default: '1200px' },
  /**
   * 客户端排除规则：返回 true 的物料不展示、不可选。
   * 为什么放客户端：这类规则是"不等于某值"的黑名单（如 BOM 子件要排除「成品」），
   * 后端分页接口没有对应入参；放在这里可让各页复用同一个弹窗，而不必各写一份选料逻辑。
   * 例：:exclude-fn="(m) => m.materialType === '成品'"
   */
  excludeFn: { type: Function, default: null },
  /**
   * 物料属性过滤（2026-11-10）：只列指定属性的物料，如 ['成品', '半成品', '自制件']。
   * 走后端分页过滤 → 翻页结果与总数都准确；不传（默认空）= 不过滤，所有已有调用页行为零变化。
   * 与 excludeFn 的分工：属性白名单用本参数（分页正确），零散黑名单规则仍可用 excludeFn。
   */
  materialTypes: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue', 'confirm'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

// ===== 物料自定义字段：字段定义（列 + 取值）与可搜索字段（搜索项） =====
const mcf = useMaterialCustomFields()
const cf = useCustomFieldSearch('material')

// 弹窗列定义 = 物料全部字段 + 全部自定义字段（列设置 page-key 全局共享，配一次全单据生效）
const pickerColumnsWithCustom = computed(() => materialPickerColumns(mcf.allFields.value))
const { columnSettingVisible, openColumnSetting, isColVisible, getColFixed, configVersion, sortedColumns } = useColumnSetting('MaterialPicker', pickerColumnsWithCustom)
const visibleColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))
/** 表格实际展示的行：应用调用方传入的排除规则（如 BOM 子件排除「成品」） */
const visibleRows = computed(() => (props.excludeFn ? rows.value.filter(r => !props.excludeFn(r)) : rows.value))

// ===== 弹窗自身状态 =====
const keyword = ref('')
const categoryId = ref(null)
const categories = ref([])
const rows = ref([])
const selected = ref([])
const page = reactive({ current: 1, size: 20, total: 0 })
const inited = ref(false)

/** 打开弹窗：懒加载字段定义 + 分类，并出首屏数据 */
watch(visible, async (v) => {
  if (!v) return
  if (!inited.value) {
    inited.value = true
    // mcf.init() 拿到物料全部字段定义；可搜索字段直接复用同一份，不再重复请求接口
    await mcf.init().catch(() => {})
    cf.load(mcf.allFields.value)
    listAllMaterialCategory().then(list => {
      categories.value = list || []
    }).catch(() => {})
  }
  if (rows.value.length === 0) doSearch(true)
})

/** 查询（resetPage=true 时回到第 1 页） */
async function doSearch(resetPage) {
  if (resetPage) page.current = 1
  try {
    const res = await pageMaterial({
      current: page.current,
      size: page.size,
      keyword: keyword.value || undefined,
      categoryId: categoryId.value || undefined,
      // 物料属性白名单（不传 = 不过滤，保持其它调用页原行为）
      materialTypes: (props.materialTypes && props.materialTypes.length) ? props.materialTypes.join(',') : undefined,
      status: '正常',
      // 自定义字段搜索条件（无有效条件时不传，保证旧行为零变化）
      ...cf.buildParams()
    })
    rows.value = res.records || res || []
    page.total = res.total || rows.value.length
    // 当前页物料的自定义字段值合并进行对象，动态列即可直接渲染（每页条数有限，一次批量查询）
    await mcf.mergeIntoRows(rows.value)
  } catch (e) {
    console.warn('[material-picker] 物料查询失败:', e)
    ElMessage.error(tt('物料查询失败，请稍后重试'))
    rows.value = []
    page.total = 0
  }
}

function onSelectionChange(sel) {
  selected.value = sel
}

/** 确认添加：把选中的物料行 + 已取到的自定义字段值一并回传父页 */
async function handleConfirm() {
  if (selected.value.length === 0) {
    ElMessage.warning(tt('请选择至少一个物料'))
    return
  }
  // 兜底：确保选中项的值已取到（已取过的不会重复请求）
  await mcf.ensureValues(selected.value.map(m => m.materialId)).catch(() => {})
  emit('confirm', { rows: [...selected.value], valueMap: mcf.valueMap.value })
  visible.value = false
}

/** 供父页在需要时重置搜索条件（可选） */
function resetSearch() {
  keyword.value = ''
  categoryId.value = null
  cf.reset()
}
defineExpose({ resetSearch, doSearch })
</script>

<style scoped lang="scss">
/* 搜索区：左条件 + 右按钮
   - align-items: flex-start → 按钮固定贴在第一行，不会被换行后的行高带到第二行
   - 按钮与搜索框同高：el-input / el-button 默认尺寸均为 32px（列设置按钮不再用 size="small"） */
.picker-search-bar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
}

.picker-search-conds {
  flex: 1 1 auto;
  min-width: 0;
  margin-bottom: 0; /* 覆盖全局 .erp-search-bar 的 margin-bottom */
}

.picker-search-actions {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 8px;

  /* 与第一行搜索框严格对齐：显式锁死高度，避免不同尺寸按钮把这一行撑高/压低 */
  :deep(.el-button) {
    height: 32px;
  }
}
/* 单元格（图片/是-否/金额/文本）的样式统一在 MaterialFieldCell 里，本组件不再重复 */
</style>
