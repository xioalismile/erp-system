<template>
  <div class="inventory-list-page">
    <div class="page-header">
      <div class="header-left">
        <div class="title-group">
          <div class="page-title">
            库存查询
            <span class="title-badge">INVENTORY</span>
          </div>
          <div class="page-sub">{{ $tt("实时监控库存状态 · 智能预警库存不足") }}</div>
        </div>
      </div>
      <div class="header-actions">
        <el-button type="success" :icon="Download" v-permission="perm('export')" @click="handleExport">{{ $tt("导出") }}</el-button>
      </div>
    </div>

    <!-- 统计卡片（通用件）：带 filter 的卡片可点击 → 按该条件筛选下方列表；再点同一张卡＝取消 -->
    <StatCards :stats="stats" :active-key="activeStatKey" @stat-click="onStatClick" />

    <div class="content-card">
      <div class="search-bar">
        <div class="search-right">
          <el-input v-model="search" :placeholder="$tt('物料编码/名称')" clearable class="search-input" @keyup.enter="load">
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-select v-model="stockIdFilter" :placeholder="$tt('选择仓库')" clearable filterable class="search-select" @change="load">
            <el-option v-for="w in withFallbackOption(warehouses, 'stockIdFilter', 'warehouseName', stockIdFilter)" :key="w.stockId" :label="w.stockName" :value="w.stockId" />
          </el-select>
          <!-- 「只看有库存」勾选框已移除：该条件由上方「有库存」卡片承担（同一条件只留一个控件，避免两处控同一条件） -->
          <!-- 物料自定义字段「可搜索」项（通用件）：设置中心勾了「可搜索」的字段才渲染，未勾选时渲染 0 个节点 -->
          <CustomFieldSearchBar :fields="cf.searchFields.value" :model="cf.values" @change="cf.setValue" @search="load" />
          <el-button type="primary" v-permission="perm('query')" @click="load">{{ $tt("查询") }}</el-button>
          <el-button v-permission="perm('reset')" @click="reset">{{ $tt("重置") }}</el-button>
          <el-tooltip :content="$tt('列设置')" placement="top">
            <el-button :icon="Setting" circle v-permission="perm('columnSetting')" @click="openColumnSetting" />
          </el-tooltip>
        </div>
      </div>

      <el-table ref="tableRef" :data="list" v-loading="loading" class="tech-table" border :header-cell-style="headerCellStyle" @sort-change="onSortChange" @header-dragend="onHeaderDragend" :key="configVersion">
        <el-table-column :label="$tf('物料图片')" width="80" align="center">
          <template #default="{ row }">
            <el-image
              v-if="row.mainImage"
              :src="row.mainImage"
              :preview-src-list="[row.mainImage]"
              fit="cover"
              style="width:50px;height:50px;border-radius:4px;"
              :preview-teleported="true"
            />
            <span v-else style="color:#c0c4cc;">{{ $tt("无图") }}</span>
          </template>
        </el-table-column>
        <!-- 动态列渲染：列顺序由列设置拖拽配置驱动（sortedColumns），单元格按 col.type 分支渲染 -->
        <template v-for="col in visibleSortedColumns" :key="col.prop">
          <el-table-column
            :prop="col.prop"
            :label="$tf(col.label)"
            :width="col.width"
            :min-width="col.minWidth"
            :align="col.align || 'left'"
            :sortable="col.sortable ? 'custom' : false"
            :show-overflow-tooltip="!!col.tooltip"
            :fixed="getColFixed(col.prop)"
          >
            <template v-if="col.type" #default="{ row }">
              <span v-if="col.type === 'text'">{{ row[col.prop] }}</span>
              <span v-else-if="col.type === 'textDash'">{{ row[col.prop] || '-' }}</span>
              <span v-else-if="col.type === 'amount'" class="amount-text">{{ formatWith(row[col.prop], scaleFor('qty'), trimZero.value) }}</span>
              <span v-else-if="col.type === 'number4'">{{ formatWith(row[col.prop], scaleFor('qty'), trimZero.value) }}</span>
              <el-tooltip v-else-if="col.type === 'reserved'" placement="top" :content="$tt('生效销售订单未发货数量合计（按物料全局统计）')">
                <span :style="Number(row.reservedQuantity || 0) > 0 ? 'color:#E6A23C;font-weight:600;' : 'color:#c0c4cc;'">
                  {{ Number(row.reservedQuantity || 0).toFixed(4) }}
                </span>
              </el-tooltip>
              <el-tooltip v-else-if="col.type === 'virtual'" placement="top" :content="$tt('预计可用量 = 可用库存 - 订单占用，负数表示接单缺口')">
                <span :style="Number(row.virtualAvailable || 0) < 0 ? 'color:#F56C6C;font-weight:600;' : ''">
                  {{ formatWith(row.virtualAvailable != null ? row.virtualAvailable : (row.availableQuantity || 0), scaleFor('qty'), trimZero.value) }}
                </span>
              </el-tooltip>
              <span v-else-if="col.type === 'cost'">{{ row[col.prop] != null ? Number(row[col.prop]).toFixed(4) : '0.0000' }}</span>
              <span v-else-if="col.type === 'safety'">{{ row[col.prop] != null ? formatWith(row[col.prop], scaleFor('qty'), trimZero.value) : '-' }}</span>
              <div v-else-if="col.type === 'status'" class="status-cell">
                <span :class="['status-dot', statusDotClass(row)]"></span>
                <el-tag size="small" :type="statusType(row)" effect="light" class="status-tag">{{ $dt(statusText(row)) }}</el-tag>
              </div>
              <template v-else-if="col.type === 'custom'">
                <span v-if="col.fieldType === 'number'" class="cell-number">{{ formatCustomNumber(row[col.prop], col.precision) }}</span>
                <span v-else-if="col.fieldType === 'select'">{{ resolveCustomOption(col, row[col.prop]) }}</span>
                <span v-else-if="col.fieldType === 'switch'">{{ row[col.prop] === 1 ? '是' : (row[col.prop] === 0 ? '否' : '') }}</span>
                <span v-else-if="col.fieldType === 'date' || col.fieldType === 'datetime'">{{ row[col.prop] || '' }}</span>
                <span v-else>{{ row[col.prop] ?? '' }}</span>
              </template>
            </template>
          </el-table-column>
        </template>
        <AuditColumns />
      </el-table>

      <div class="pagination-wrap">
        <div class="pagination-info">共 {{ page.total }} 条记录</div>
        <el-pagination
          v-model:current-page="page.current"
          v-model:page-size="page.size"
          :page-sizes="[20, 50, 100]"
          :total="page.total"
          layout="prev, pager, next, sizes, jumper"
          @current-change="load"
          @size-change="load"
        />
      </div>
    </div>

    <!-- 列设置抽屉 -->
    <ColumnSettingDrawer v-model="columnSettingVisible" :page-key="'InventoryList'" :columns="columns" />
  </div>
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { withFallbackOption } from '@/utils/labelFallback'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Download, Document, EditPen, Clock, CircleCheck, Setting } from '@element-plus/icons-vue'
import { listInventory, getInventoryDashboard } from '@/api/inventory/inventory'
import { listAllWarehouse } from '@/api/basedata/warehouse'
import { exportDataWithPayload } from '@/utils/exportUtil'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
import AuditColumns from '@/components/AuditColumns.vue'
import StatCards from '@/components/bill/StatCards.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import {
  loadCustomFieldDefinitions,
  customFieldsToTableColumns,
  batchLoadCustomFieldValues
} from '@/composables/useCustomField'

import { useTableSort } from '@/composables/useTableSort'
import { useCustomFieldSearch } from '@/composables/useCustomFieldSearch'
import CustomFieldSearchBar from '@/components/CustomFieldSearchBar.vue'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'
import { usePrecision } from '@/composables/usePrecision'

// 数量显示位数统一读设置中心「数量小数位数」（2026-11-10）
const { scaleFor, formatWith, trimZero } = usePrecision()
const { handleSortChange, getSortParams } = useTableSort()
const perm = usePagePerm('inventory:current')

// 物料自定义字段「可搜索」搜索项（通用件）：设置中心勾了「可搜索」的物料字段才渲染
const cf = useCustomFieldSearch('material')


const onSortChange = (e) => { handleSortChange(e); load() }

// 列定义（用于列设置抽屉+动态渲染）= 静态业务列 + 物料自定义字段列（isListShow=1）
// type 标记单元格渲染分支：text/plain 文本、textDash 空值显示-、amount/number4/cost/safety 数值、
// reserved/virtual 带提示、status 状态标签、custom 自定义字段
const staticColumns = [
  { prop: 'materialCode', label: '物料编码', width: 140, sortable: true },
  { prop: 'materialName', label: '物料名称', minWidth: 160, tooltip: true, sortable: true },
  { prop: 'model', label: '规格型号', width: 140, tooltip: true, sortable: true },
  { prop: 'stockName', label: '仓库', width: 120, tooltip: true, sortable: true, type: 'textDash' },
  { prop: 'batchNo', label: '批次号', width: 120, tooltip: true, sortable: true, type: 'textDash' },
  { prop: 'quantity', label: '库存数量', width: 130, align: 'right', type: 'amount' },
  { prop: 'availableQuantity', label: '可用库存', width: 130, align: 'right', type: 'number4' },
  { prop: 'reservedQuantity', label: '订单占用', width: 120, align: 'right', type: 'reserved' },
  { prop: 'virtualAvailable', label: '预计可用量', width: 130, align: 'right', type: 'virtual' },
  { prop: 'unitName', label: '单位', width: 80, align: 'center', sortable: true, type: 'textDash' },
  { prop: 'unitCost', label: '库存移动平均成本', width: 150, align: 'right', type: 'cost' },
  { prop: 'latestCalcCost', label: '最新核算成本', width: 140, align: 'right', type: 'cost' },
  { prop: 'safetyStock', label: '安全库存', width: 110, align: 'right', type: 'safety' },
  { prop: '_status', label: '状态', width: 90, align: 'center', type: 'status' }
]
// 自定义字段列定义（onMounted 后异步填充，空时退化为纯静态列）
const customColumns = ref([])
// columns 动态合并静态列+自定义列
const columns = computed(() => [...staticColumns, ...customColumns.value])
const { columnSettingVisible, openColumnSetting, isColVisible, getColFixed, configVersion, sortedColumns, onHeaderDragend, applySavedWidths } = useColumnSetting('InventoryList', columns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))
// 列宽持久化：表格带 border 才允许拖拽，拖完的宽度写入本页列配置，下次打开自动套用
const tableRef = ref(null)

const loading = ref(false)
const list = ref([])
const search = ref('')
const stockIdFilter = ref(null)
const onlyPositive = ref(false)
// 零库存筛选（后端 zeroOnly 参数）：由「零库存」卡片点击下发
const zeroOnly = ref(false)
// 数据看板穿透参数（无 query 时全部为空，行为与原先一致；belowMinStock 亦作为「库存不足」卡片的下发条件）
const drill = reactive({ materialType: '', lineId: '', ageOver: '', staleOnly: '', belowMinStock: '', materialCode: '', materialId: '' })
const warehouses = ref([])
const page = reactive({ current: 1, size: 20, total: 0 })

// 统计卡片：值一律取接口（/inventory/dashboard，与列表同数据权限口径）
// 不再用"当前页行"自行统计 —— 列表分页时那样算出来的是本页数，不是全量
const stats = ref([])
const activeStatKey = ref('')

async function loadStats() {
  try {
    const d = (await getInventoryDashboard()) || {}
    stats.value = [
      { key: 'all', label: '物料总数', value: d.materialCount ?? 0, type: 'primary', icon: 'Document', filter: {} },
      { key: 'positive', label: '有库存', value: d.positiveCount ?? 0, type: 'success', icon: 'CircleCheck', filter: { onlyPositive: true } },
      { key: 'zero', label: '零库存', value: d.zeroCount ?? 0, type: 'warning', icon: 'EditPen', filter: { zeroOnly: true } },
      { key: 'below', label: '库存不足', value: d.belowMinCount ?? 0, type: 'danger', icon: 'Clock', filter: { belowMinStock: true } }
    ]
  } catch (e) {
    // 静默降级：取数失败时卡片保持原值，不弹提示、不阻断列表
  }
}

/**
 * 卡片点击：
 * 「物料总数」＝清空统计筛选（看全部）；「有库存 / 零库存 / 库存不足」＝套用对应条件；再点同一张卡＝取消
 */
function onStatClick({ key }) {
  const off = activeStatKey.value === key || key === 'all'
  activeStatKey.value = off ? '' : key
  onlyPositive.value = !off && key === 'positive'
  zeroOnly.value = !off && key === 'zero'
  drill.belowMinStock = (!off && key === 'below') ? 'true' : ''
  page.current = 1
  load()
}

const headerCellStyle = {
  background: 'rgba(99, 102, 241, 0.04)',
  color: '#1f2937',
  fontWeight: 600,
  borderBottom: '2px solid #e0e7ff'
}

/**
 * 列表与导出**共用**的查询条件（2026-09-22）。
 *
 * 为什么必须共用：用户要求"报表里看到什么，导出就要带什么"。原先导出走的是后端另一条
 * "固定 12 列 + 全量"链路（不套任何筛选）→ 必然漂移（导出出现列表看不到的行、页面上的
 * 物料自定义字段列一个都不带）。抽成一个函数后，列表与导出用的是同一套条件。
 */
function buildQueryParams() {
  const params = {
    onlyPositive: onlyPositive.value,
    zeroOnly: zeroOnly.value,
    ...getSortParams(),
    // 物料自定义字段搜索（通用件；无有效条件时返回 {}，与改造前一致）
    ...cf.buildParams()
  }
  // 物料关键词（列表页通用口径）：一个框同时匹配 物料编码 / 物料名称，由后端成组匹配
  // （2026-09-16 修复：此前只发 materialName 且后端未使用该参数，搜索完全无效）
  if (search.value) params.materialKeyword = search.value
  if (stockIdFilter.value) params.stockId = stockIdFilter.value
  // 数据看板穿透过滤（只看有值的项）
  for (const k of ['materialType', 'lineId', 'ageOver', 'staleOnly', 'belowMinStock', 'materialCode', 'materialId']) {
    if (drill[k] !== '' && drill[k] != null) params[k] = drill[k]
  }
  return params
}

const load = async () => {
  loading.value = true
  const params = { current: page.current, size: page.size, ...buildQueryParams() }

  try {
    const res = await listInventory(params)
    if (Array.isArray(res)) {
      list.value = res
      page.total = res.length
    } else if (res?.records) {
      list.value = res.records
      page.total = res.total || res.records.length
    } else if (res?.data?.records) {
      list.value = res.data.records
      page.total = res.data.total || res.data.records.length
    } else {
      list.value = []
      page.total = 0
    }
    // 补带物料自定义字段值：按 materialId 批量取值后合并到每行（仅当存在自定义列时）
    if (customColumns.value.length > 0 && list.value.length > 0) {
      const materialIds = [...new Set(list.value.map(r => r.materialId).filter(Boolean))]
      if (materialIds.length > 0) {
        const valueMap = await batchLoadCustomFieldValues('material', materialIds)
        list.value.forEach(row => {
          const vals = valueMap[row.materialId]
          if (vals) {
            for (const k in vals) {
              if (!(k in row)) row[k] = vals[k]
            }
          }
        })
      }
    }
  } catch (e) {
    ElMessage.error(e.message || tt('查询失败'))
  } finally {
    loading.value = false
    // 数据到位后再套一次已保存的列宽（表格重建后列对象是新的）
    nextTick(() => applySavedWidths(tableRef))
  }
}

const reset = () => {
  search.value = ''
  stockIdFilter.value = null
  onlyPositive.value = false
  zeroOnly.value = false
  // 穿透条件必须整组清空：此前只清了 belowMinStock，
  // 导致从数据看板带 ageOver / staleOnly 穿透进来后点「重置」清不掉，看起来像"数据是空的"
  clearDrill()
  // 卡片选中态一并清空（通用件）
  activeStatKey.value = ''
  // 自定义字段搜索项一并清空（通用件）
  cf.reset()
  page.current = 1
  load()
}

/** 清空全部穿透条件（单个条件也用这个，保证口径一致） */
function clearDrill(only) {
  const keys = only ? [only] : ['materialType', 'lineId', 'ageOver', 'staleOnly', 'belowMinStock', 'materialCode', 'materialId']
  for (const k of keys) drill[k] = ''
  if (!only) activeStatKey.value = ''
}

/** 当前生效的穿透条件（用于界面展示成可删除的标签，用户操作以界面为准） */
const drillTags = computed(() => {
  const map = {
    ageOver: v => tt('库龄超过 ') + v + tt(' 天'),
    staleOnly: () => tt('仅看呆滞物料'),
    belowMinStock: () => tt('仅看库存不足'),
    materialType: v => tt('物料类型：') + v,
    lineId: v => tt('产线：') + v,
    materialCode: v => tt('物料编码：') + v,
    materialId: v => tt('指定物料')
  }
  const out = []
  for (const k of ['materialType', 'lineId', 'ageOver', 'staleOnly', 'belowMinStock', 'materialCode', 'materialId']) {
    if (drill[k] !== '' && drill[k] != null) out.push({ key: k, label: (map[k] || (() => k))(drill[k]) })
  }
  return out
})

function statusType(row) {
  if (row.quantity == null) return 'info'
  if (row.safetyStock != null && Number(row.quantity) < Number(row.safetyStock)) return 'danger'
  if (Number(row.quantity) === 0) return 'warning'
  return 'success'
}

function statusDotClass(row) {
  if (row.quantity == null) return 'dot-info'
  if (row.safetyStock != null && Number(row.quantity) < Number(row.safetyStock)) return 'dot-danger'
  if (Number(row.quantity) === 0) return 'dot-warning'
  return 'dot-success'
}

function statusText(row) {
  if (row.quantity == null) return '未知'
  if (row.safetyStock != null && Number(row.quantity) < Number(row.safetyStock)) return '不足'
  if (Number(row.quantity) === 0) return '零库存'
  return '正常'
}

// 自定义字段格式化：number 按精度、select 翻译 label、空值显示空白（非占位符）
function formatCustomNumber(val, precision) {
  if (val == null || val === '') return ''
  const p = precision != null ? precision : 2
  return Number(val).toFixed(p)
}
function resolveCustomOption(col, val) {
  if (val == null || val === '') return ''
  if (!col.options || !col.options.length) return val
  const hit = col.options.find(o => String(o.value) === String(val))
  return hit ? hit.label : val
}

async function handleExport() {
  try {
    // "看到什么导出就带什么"：带当前筛选条件 + 当前可见列（含物料自定义字段列，按页面列顺序）
    const fields = visibleSortedColumns.value.map(c => ({
      key: c.prop,
      label: c.label,
      custom: c.type === 'custom'
    }))
    const today = new Date().toISOString().slice(0, 10).replace(/-/g, '')
    await exportDataWithPayload('/api/inventory/export', { ...buildQueryParams(), fields }, `即时库存数据_${today}.xlsx`)
    ElMessage.success(tt('导出成功'))
  } catch (e) {
    // 失败原因已由 exportDataWithPayload 统一弹出（含后端可读提示），此处不重复提示
  }
}

onMounted(async () => {
  // 数据看板穿透：把 URL query 注入过滤条件（无 query 时行为完全不变）
  const route = useRoute()
  const q = route.query || {}
  let drillHit = false
  for (const k of ['materialType', 'lineId', 'ageOver', 'staleOnly', 'belowMinStock', 'materialCode', 'materialId']) {
    if (q[k]) { drill[k] = String(q[k]); drillHit = true }
  }
  if (drillHit) ElMessage.info(tt('已应用数据看板穿透筛选条件'))
  try {
    warehouses.value = (await listAllWarehouse()) || []
  } catch (e) {
    warehouses.value = []
  }
  // 加载物料自定义字段定义，转成表格列（仅 isListShow=1 的字段），失败降级为纯静态列
  try {
    const result = await loadCustomFieldDefinitions('material')
    const fields = Array.from(result.fieldMap.values())
    if (fields.length > 0) {
      customColumns.value = customFieldsToTableColumns(fields, { editable: false, listShowOnly: true })
        .map(c => ({ ...c, type: 'custom' }))
    }
    // 同一份字段定义喂给「自定义字段搜索」通用件（复用已加载结果，不重复请求同一接口）
    await cf.load(fields)
  } catch (e) {
    console.warn('[InventoryList] 加载物料自定义字段定义失败:', e)
  }
  await load()
  await loadStats()
})
// 切回本标签页 / 点标签栏「刷新当前页」时：列表与统计卡片一起重拉。
// （原写法 useAutoRefresh(() => { async () => {...} }) 传进去的函数从未被执行 → 这两个入口一直空转）
useAutoRefresh(async () => {
  try {
    warehouses.value = (await listAllWarehouse()) || []
  } catch (e) {
    warehouses.value = []
  }
  await load()
  await loadStats()
})
</script>

<style scoped lang="scss">
.inventory-list-page {
  padding: 20px 24px;
  background: linear-gradient(135deg, #f8faff 0%, #f0f4ff 50%, #f8fcff 100%);
  min-height: calc(100vh - 60px);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;

  .header-left {
    display: flex;
    align-items: center;
    gap: 16px;
  }

  .back-btn {
    display: flex;
    align-items: center;
    gap: 4px;
    color: #6b7280;
    font-size: 13px;
    cursor: pointer;
    padding: 6px 12px;
    border-radius: 6px;
    background: #fff;
    border: 1px solid #e5e7eb;
    transition: all .2s;
    &:hover {
      color: #6366f1;
      border-color: #c7d2fe;
    }
  }

  .title-group { display: flex; flex-direction: column; gap: 2px; }

  .page-title {
    font-size: 20px;
    font-weight: 700;
    color: #111827;
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .title-badge {
    font-size: 10px;
    font-weight: 600;
    color: #6366f1;
    background: linear-gradient(135deg, #e0e7ff, #f0f4ff);
    padding: 2px 8px;
    border-radius: 4px;
    letter-spacing: 1px;
  }

  .page-sub { font-size: 12px; color: #9ca3af; }

  .header-actions { display: flex; gap: 8px; }
}

/* 统计卡片样式已随通用件迁到 components/bill/StatCards.vue（壳与手写页共用一套，此处不再重复） */

.content-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.search-bar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #f3f4f6;

  .search-right {
    display: flex;
    gap: 8px;
    align-items: center;
    /* 自定义字段搜索项会随勾选数量增加，允许换行避免挤压查询按钮 */
    flex-wrap: wrap;
    justify-content: flex-end;
  }

  .search-input {
    width: 220px;
    :deep(.el-input__wrapper) {
      border-radius: 8px;
      box-shadow: 0 0 0 1px #e5e7eb inset;
      &:hover { box-shadow: 0 0 0 1px #c7d2fe inset; }
      &.is-focus { box-shadow: 0 0 0 1px #6366f1 inset; }
    }
  }

  .search-select {
    width: 180px;
    :deep(.el-select__wrapper) {
      border-radius: 8px;
    }
  }
}

.tech-table {
  :deep(.el-table__header th) {
    background: rgba(99, 102, 241, 0.04) !important;
  }

  :deep(.el-table__row:hover > td) {
    background: rgba(99, 102, 241, 0.03) !important;
  }

  :deep(.el-table td) {
    border-bottom: 1px solid #f3f4f6;
  }
}

.amount-text { color: #374151; font-variant-numeric: tabular-nums; font-weight: 600; }

.status-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  justify-content: center;

  .status-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;

    &.dot-success { background: #10b981; box-shadow: 0 0 6px rgba(16, 185, 129, 0.5); }
    &.dot-warning { background: #f59e0b; box-shadow: 0 0 6px rgba(245, 158, 11, 0.5); }
    &.dot-info { background: #3b82f6; box-shadow: 0 0 6px rgba(59, 130, 246, 0.5); }
    &.dot-danger { background: #ef4444; box-shadow: 0 0 6px rgba(239, 68, 68, 0.5); }
  }

  .status-tag {
    border-radius: 4px;
    font-weight: 500;
  }
}

.pagination-wrap {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-top: 1px solid #f3f4f6;

  .pagination-info {
    font-size: 13px;
    color: #6b7280;
  }
}

:deep(.el-button) {
  border-radius: 8px;
  font-weight: 500;
}

@media (max-width: 768px) {
  .inventory-list-page { padding: 12px; }
  .search-bar {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }
}
</style>