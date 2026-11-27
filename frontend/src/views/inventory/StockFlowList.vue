<template>
  <!-- 通用列表壳（页头/统计卡/搜索条/表格外壳/分页/列设置）+ 声明式筛选维度
       库存流水是只读台账（无单据状态字段）→ 只声明 date 日期区间维度，不硬套 status -->
  <BillListPage
    page-class="stock-flow-list-page"
    back-path="/inventory"
    :title="$tt('库存流水')"
    badge="STOCK FLOW"
    sub-title="实时记录库存变动 · 全链路出入库追溯"
    :stats="stats"
    :active-stat-key="activeStatKey"
    search-placeholder="输入单据号/来源单号/备注搜索"
    keyword-label="单据编号"
    date-label="交易日期"
    :page-sizes="[20, 50, 100, 200]"
    :filters="['date']"
    :filter-model="query"
    :table-data="tableData"
    :loading="loading"
    :pagination="page"
    storage-key="StockFlowList"
    :columns="columns"
    :table-key="configVersion"
    :show-summary="true"
    :summary-method="getSummary"
    v-model:keyword="query.keyword"
    v-model:column-setting-visible="columnSettingVisible"
    @search="onSearch"
    @reset="onReset"
    @filter-change="onFilterChange"
    @stat-click="onStatClick"
    @page-change="loadData"
  >
    <!-- 台账页特有筛选（通用件未覆盖的维度）：单据类型 / 仓库 / 物料 / 批次号 / 来源单据类型 -->
    <template #search-extra>
      <div class="filter-item">
        <span class="filter-label">{{ $tt('单据类型') }}</span>
        <el-select v-model="query.transType" :placeholder="$tt('单据类型')" clearable filterable class="search-select" @change="onTransTypeChange">
          <el-option v-for="t in transTypeOptions" :key="t.value" :label="$tf(t.label)" :value="t.value" />
        </el-select>
      </div>
      <div class="filter-item">
        <span class="filter-label">{{ $tt('仓库') }}</span>
        <el-select v-model="query.stockId" :placeholder="$tt('仓库')" clearable filterable class="search-select" @change="onSearch">
          <el-option v-for="w in withFallbackOption(warehouseOptions, 'stockId', 'warehouseName', query)" :key="w.stockId" :label="w.stockName" :value="w.stockId" />
        </el-select>
      </div>
      <div class="filter-item">
        <span class="filter-label">{{ $tt('物料') }}</span>
        <el-input v-model="query.materialKeyword" :placeholder="$tt('物料编码/名称')" clearable class="search-input" @keyup.enter="onSearch" />
      </div>
      <div class="filter-item">
        <span class="filter-label">{{ $tt('批次号') }}</span>
        <el-input v-model="query.batchNo" :placeholder="$tt('批次号')" clearable class="search-input" @keyup.enter="onSearch" />
      </div>
      <div class="filter-item">
        <span class="filter-label">{{ $tt('来源单据') }}</span>
        <el-select v-model="query.sourceBillType" :placeholder="$tt('来源单据类型')" clearable filterable class="search-select" @change="onSearch">
          <el-option v-for="t in sourceBillTypeOptions" :key="t" :label="t" :value="t" />
        </el-select>
      </div>
      <!-- 物料自定义字段「可搜索」项（通用件）：设置中心勾了「可搜索」的字段才渲染，未勾选时渲染 0 个节点 -->
      <CustomFieldSearchBar :fields="cf.searchFields.value" :model="cf.values" @change="cf.setValue" @search="onSearch" />
    </template>

    <template #header-actions>
      <el-button type="success" :icon="Download" :loading="exporting" v-permission="perm('export')" @click="exportData">{{ $tt("导出") }}</el-button>
    </template>

    <el-table-column type="index" label="#" width="60" />
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
        :fixed="getColFixed(col.prop, col.fixed)"
      >
        <template v-if="col.type" #default="{ row }">
          <div v-if="col.type === 'transType'" class="status-cell">
            <span :class="['status-dot', transTypeDotClass(row.transType)]"></span>
            <el-tag :type="getTransTypeTag(row.transType)" size="small" effect="light" class="status-tag">{{ getTransTypeText(row.transType) }}</el-tag>
          </div>
          <!-- 单据号：可点则跳转到对应单据编辑页（billDocType/billId 由后端按单号反查下发） -->
          <div v-else-if="col.type === 'code'" class="order-no-cell">
            <span
              :class="['order-no-text', billJumpPath(row) ? 'is-link' : 'no-link']"
              @click="goBill(row)"
            >{{ row[col.prop] }}</span>
            <el-icon v-if="billJumpPath(row)" class="link-icon"><Link /></el-icon>
          </div>
          <template v-else-if="col.type === 'inQty'">
            <span v-if="row.inQuantity && row.inQuantity > 0" class="net-amount">+{{ Number(row.inQuantity).toFixed(4) }}</span>
            <span v-else class="text-muted">-</span>
          </template>
          <template v-else-if="col.type === 'inAmt'">
            <span v-if="row.inAmount > 0" class="net-amount">¥{{ Number(row.inAmount).toFixed(2) }}</span>
            <span v-else class="text-muted">-</span>
          </template>
          <template v-else-if="col.type === 'outQty'">
            <span v-if="row.outQuantity && row.outQuantity > 0" class="discount-text">-{{ Number(row.outQuantity).toFixed(4) }}</span>
            <span v-else class="text-muted">-</span>
          </template>
          <template v-else-if="col.type === 'outAmt'">
            <span v-if="row.outAmount > 0" class="discount-text">¥{{ Number(row.outAmount).toFixed(2) }}</span>
            <span v-else class="text-muted">-</span>
          </template>
          <span v-else-if="col.type === 'amount'" class="amount-text">{{ Number(row.balanceQuantity || 0).toFixed(4) }}</span>
          <span v-else-if="col.type === 'cost'">¥{{ Number(row.unitCost || 0).toFixed(4) }}</span>
          <!-- 物料自定义字段列（通用件）：number 按精度、select 翻译 label、switch 是/否、空值空白 -->
          <template v-else-if="col.type === 'custom'">
            <span v-if="col.fieldType === 'number'" class="amount-text">{{ formatCustomNumber(row[col.prop], col.precision) }}</span>
            <span v-else-if="col.fieldType === 'select'">{{ resolveCustomOption(col, row[col.prop]) }}</span>
            <span v-else-if="col.fieldType === 'switch'">{{ row[col.prop] === 1 ? '是' : (row[col.prop] === 0 ? '否' : '') }}</span>
            <span v-else>{{ row[col.prop] ?? '' }}</span>
          </template>
        </template>
      </el-table-column>
    </template>
    <AuditColumns />
  </BillListPage>
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, computed, onMounted } from 'vue'
import { withFallbackOption } from '@/utils/labelFallback'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Search, Download, Link, Setting, Document, CircleCheck, Warning, Sort } from '@element-plus/icons-vue'
import { listAllWarehouse } from '@/api/basedata/warehouse'
import { getStockFlowDashboard, getInventoryFlowList } from '@/api/inventory/inventory'
import BillListPage from '@/components/bill/BillListPage.vue'
import { listAllMaterial } from '@/api/basedata/material'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
import AuditColumns from '@/components/AuditColumns.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { resolveBillRoute } from '@/utils/billRoute'
import {
  loadCustomFieldDefinitions,
  customFieldsToTableColumns,
  batchLoadCustomFieldValues
} from '@/composables/useCustomField'
import { useCustomFieldSearch } from '@/composables/useCustomFieldSearch'
import CustomFieldSearchBar from '@/components/CustomFieldSearchBar.vue'
import { exportCsv } from '@/utils/csvExport'
import { collectFilteredRows } from '@/utils/exportUtil'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm()
const router = useRouter()

/**
 * 单据号跳转路径：后端在每行流水上按「单据类型 + 单据号」反查了来源单据主键（billDocType/billId）。
 * 反查不到（单据已删、口径不符）返回 null → 该行单据号保持纯文本、不可点。
 */
function billJumpPath(row) {
  return resolveBillRoute(row && row.billDocType, row && row.billId)
}

/** 点单据号 → 打开对应单据编辑页；单据已删除/无编辑页时给出明确提示（不再"点了没反应"） */
function goBill(row) {
  const path = billJumpPath(row)
  if (!path) { ElMessage.warning(tt('该单据已删除或无法打开')); return }
  router.push(path)
}

// 本页列头均不可排序，且后端 /inventory/flow/list 不接收 sortBy/sortOrder，故不接入排序能力（避免死参数）

// 列定义（用于列设置抽屉+动态渲染）= 静态业务列 + 物料自定义字段列（isListShow=1）
// type 标记单元格渲染分支（分支内字段为硬编码绑定，prop 必须与分支字段一致）
// transType=单据类型标签 code=单据号列 inQty/inAmt=入库数量/金额(绿色) outQty/outAmt=出库数量/金额(警示色)
// amount=结存数量(4位) cost=单位成本(¥4位) custom=自定义字段 纯文本列无 type
const staticColumns = [
  { prop: 'transDate', label: '交易日期', width: 120 },
  { prop: 'transType', label: '单据类型', width: 140, type: 'transType' },
  { prop: 'transNo', label: '单据号', minWidth: 160, type: 'code' },
  { prop: 'materialCode', label: '物料编码', minWidth: 130 },
  { prop: 'materialName', label: '物料名称', minWidth: 180, tooltip: true },
  { prop: 'specification', label: '规格', minWidth: 120, tooltip: true },
  { prop: 'stockName', label: '仓库', minWidth: 120, tooltip: true },
  { prop: 'batchNo', label: '批次号', minWidth: 130, tooltip: true },
  { prop: 'inQuantity', label: '入库数量', width: 110, align: 'right', type: 'inQty' },
  { prop: 'inAmount', label: '入库金额', width: 120, align: 'right', type: 'inAmt' },
  { prop: 'outQuantity', label: '出库数量', width: 110, align: 'right', type: 'outQty' },
  { prop: 'outAmount', label: '出库金额', width: 120, align: 'right', type: 'outAmt' },
  { prop: 'balanceQuantity', label: '结存数量', width: 110, align: 'right', type: 'amount' },
  { prop: 'unitCost', label: '单位成本', width: 110, align: 'right', type: 'cost' },
  { prop: 'sourceBillType', label: '来源单据类型', minWidth: 140 },
  { prop: 'sourceBillNo', label: '来源单据号', minWidth: 160 },
  { prop: 'createdBy', label: '操作人', width: 120 },
  { prop: 'remark', label: '备注', minWidth: 140 }
]
// 自定义字段列定义（onMounted 后异步填充，空时退化为纯静态列）
const customColumns = ref([])
// columns 动态合并静态列+自定义列
const columns = computed(() => [...staticColumns, ...customColumns.value])
const { columnSettingVisible, openColumnSetting, isColVisible, getColFixed, configVersion, sortedColumns } = useColumnSetting('StockFlowList', columns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))

const loading = ref(false)
/** 导出中（拉全量需要多次请求，按钮给 loading 反馈，避免重复点） */
const exporting = ref(false)
const tableData = ref([])
// 分页对象（通用壳直接双向绑定 current/size/total）
const page = reactive({ current: 1, size: 20, total: 0 })
const warehouseOptions = ref([])
const materialOptions = ref([])

// 统一入参名（docs/knowledge-base.md 6.1）：keyword / startDate / endDate ...
// 注意：startDate/endDate 是数据看板穿透依赖的字段名，不许改名
const query = reactive({
  startDate: '',
  endDate: '',
  transType: '',
  stockId: null,
  materialKeyword: '',
  batchNo: '',
  sourceBillType: '',
  keyword: ''
})

// 物料自定义字段「可搜索」搜索项（通用件）：设置中心勾了「可搜索」的物料字段才渲染
const cf = useCustomFieldSearch('material')

// 单据类型映射（与后端 transType 枚举对齐）
const transTypeOptions = [
  { value: 'PURCHASE_IN', label: '采购入库', tag: 'success' },
  { value: 'PURCHASE_RETURN', label: '采购退货', tag: 'danger' },
  { value: 'SALE_OUT', label: '销售出库', tag: 'warning' },
  { value: 'SALE_RETURN_IN', label: '销售退货入库', tag: 'success' },
  { value: 'OTHER_IN', label: '其他入库', tag: 'success' },
  { value: 'OTHER_OUT', label: '其他出库', tag: 'danger' },
  { value: 'STOCK_TRANSFER', label: '库存调拨', tag: 'primary' },
  { value: 'STOCK_CHECK', label: '库存盘点', tag: 'info' },
  { value: 'PRODUCTION_IN', label: '生产入库', tag: 'success' },
  { value: 'PRODUCTION_OUT', label: '生产领料', tag: 'warning' },
  { value: 'SUBCONTRACT_INBOUND', label: '委外入库', tag: 'success' },
  { value: 'SUBCONTRACT_PICK', label: '委外发料', tag: 'warning' }
]

const sourceBillTypeOptions = [
  '采购订单', '采购入库单', '采购退货单', '销售订单', '销售出库单', '销售退货单',
  '其他入库单', '其他出库单', '库存调拨单', '库存盘点单', '生产工单', '生产入库单',
  '生产领料单', '委外加工单', '委外发料单', '委外入库单'
]

// 顶部统计卡片：数据来自后端 /api/inventory/flow/dashboard（与 /flow/list 同"可见仓库"数据权限口径）
// 带 filter 的卡片可点击 → 直接筛选下方列表（再点同一张卡 = 取消）
const pad2 = (n) => String(n).padStart(2, '0')
function monthRange() {
  const d = new Date()
  const y = d.getFullYear()
  const m = d.getMonth() + 1
  const lastDay = new Date(y, m, 0).getDate()
  return { startDate: `${y}-${pad2(m)}-01`, endDate: `${y}-${pad2(m)}-${pad2(lastDay)}` }
}
const stats = ref([
  { key: 'total', label: '流水总数', value: 0, type: 'primary', icon: 'Document', filter: { startDate: '', endDate: '' } },
  { key: 'in', label: '入库笔数', value: 0, type: 'success', icon: 'CircleCheck' },
  { key: 'out', label: '出库笔数', value: 0, type: 'warning', icon: 'Sort' },
  { key: 'monthInAmount', label: '本月入库金额', value: 0, type: 'info', icon: 'Warning', filter: monthRange() }
])
// 统计卡片高亮（点卡片筛选列表时由 onStatClick 控制）
const activeStatKey = ref('')

async function loadStats() {
  try {
    const d = (await getStockFlowDashboard()) || {}
    stats.value = [
      { key: 'total', label: '流水总数', value: d.totalCount ?? 0, type: 'primary', icon: 'Document', filter: { startDate: '', endDate: '' } },
      { key: 'in', label: '入库笔数', value: d.inCount ?? 0, type: 'success', icon: 'CircleCheck' },
      { key: 'out', label: '出库笔数', value: d.outCount ?? 0, type: 'warning', icon: 'Sort' },
      { key: 'monthInAmount', label: '本月入库金额', value: Number(d.monthInAmount ?? 0).toFixed(2), type: 'info', icon: 'Warning', filter: monthRange() }
    ]
  } catch (e) {
    // 静默降级：取数失败时卡片保持原值，不弹诊断卡、不阻断列表
  }
}

// 卡片点击：流水总数卡 = 清空日期区间（看全部）；本月入库金额卡 = 套用本月区间；再点同一张卡 = 取消
function onStatClick({ key, filter }) {
  if (activeStatKey.value === key) {
    activeStatKey.value = ''
    Object.assign(query, { startDate: '', endDate: '' })
  } else {
    activeStatKey.value = key
    Object.assign(query, { startDate: '', endDate: '' }, filter || {})
  }
  page.current = 1
  loadData()
}

const headerCellStyle = {
  background: 'rgba(99, 102, 241, 0.04)',
  color: '#1f2937',
  fontWeight: 600,
  borderBottom: '2px solid #e0e7ff'
}

// 通用搜索栏（日期区间）变更：先把值写回本页 query
// 注：BillSearchBar 变更后会紧接着抛 search 事件（壳已转发 → onSearch），这里不重复发起查询
function onFilterChange(patch) {
  Object.assign(query, patch || {})
}

function onTransTypeChange(value) {
  // 单据类型下拉变更：与卡片高亮互斥（清掉卡片高亮）后查询
  activeStatKey.value = ''
  page.current = 1
  loadData()
}

function getTransTypeText(t) {
  const o = transTypeOptions.find(x => x.value === t)
  return o ? o.label : (t || '-')
}

function getTransTypeTag(t) {
  const o = transTypeOptions.find(x => x.value === t)
  return o ? o.tag : 'info'
}

function transTypeDotClass(t) {
  const tag = getTransTypeTag(t)
  if (tag === 'success') return 'dot-success'
  if (tag === 'warning') return 'dot-warning'
  if (tag === 'danger') return 'dot-danger'
  return 'dot-info'
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

async function loadOptions() {
  try {
    const [whs, mats] = await Promise.all([
      listAllWarehouse(),
      listAllMaterial()
    ])
    warehouseOptions.value = whs || []
    materialOptions.value = mats || []
  } catch (e) { /* 拦截器已提示 */ }
}

/**
 * 查询入参（加载与导出**共用**，保证"导出的行 = 页面筛选后的行"）。
 * @param {boolean} includePaging 是否带分页参数；导出时传 false 拉全量
 */
function buildParams(includePaging = true) {
  // 物料关键词交给后端（列表页通用口径）：编码/名称成组模糊匹配。
  // 2026-09-16 修复：原先在前端用"已缓存的物料列表 find 第一个命中"解析 materialId，
  // 物料缓存不全或同名多命中就会漏数据；后端此前也不认 materialKeyword（点了没反应）
  const params = {
    transType: query.transType || undefined,
    stockId: query.stockId || undefined,
    materialKeyword: query.materialKeyword || undefined,
    batchNo: query.batchNo || undefined,
    sourceBillType: query.sourceBillType || undefined,
    keyword: query.keyword || undefined,
    startDate: query.startDate || undefined,
    endDate: query.endDate || undefined,
    // 物料自定义字段搜索（通用件；无有效条件时返回 {}）
    ...cf.buildParams()
  }
  if (includePaging) {
    params.current = page.current
    params.size = page.size
  }
  return params
}

async function loadData() {
  loading.value = true
  try {
    const res = await getInventoryFlowList(buildParams(true))
    tableData.value = res?.records || []
    page.total = res?.total || 0
    // 补带物料自定义字段值：按 materialId 批量取值后合并到每行（仅当存在自定义列时，不逐行查库）
    if (customColumns.value.length > 0 && tableData.value.length > 0) {
      const materialIds = [...new Set(tableData.value.map(r => r.materialId).filter(Boolean))]
      if (materialIds.length > 0) {
        const valueMap = await batchLoadCustomFieldValues('material', materialIds)
        tableData.value.forEach(row => {
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
    ElMessage.error(tt('加载库存流水失败'))
    tableData.value = []
    page.total = 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.current = 1
  loadData()
}

function onReset() {
  // 重置 = 回到未筛选状态：关键词 + 通用搜索栏 + 台账特有筛选一并清空
  Object.assign(query, {
    startDate: '', endDate: '', transType: '', stockId: null,
    materialKeyword: '', batchNo: '', sourceBillType: '', keyword: ''
  })
  // 自定义字段搜索项一并清空（通用件）
  cf.reset()
  activeStatKey.value = ''
  page.current = 1
  loadData()
}

function getSummary({ columns, data }) {
  // 按列 prop 取值（列可被列设置拖动顺序，写死下标会错位）
  const rows = data || []
  const sumOf = (prop) => rows.reduce((s, r) => s + (Number(r[prop]) || 0), 0)
  const sums = []
  columns.forEach((col, i) => {
    if (i === 0) { sums[i] = '合计(本页)'; return }
    const prop = col.property
    if (prop === 'inQuantity') sums[i] = sumOf('inQuantity').toFixed(4)
    else if (prop === 'inAmount') sums[i] = sumOf('inAmount').toFixed(2)
    else if (prop === 'outQuantity') sums[i] = sumOf('outQuantity').toFixed(4)
    else if (prop === 'outAmount') sums[i] = sumOf('outAmount').toFixed(2)
    else sums[i] = ''
  })
  return sums
}

/**
 * 导出 = 当前筛选条件下的**全部**数据（不是"当前页那几条"）。
 *
 * 历史 BUG：这里直接 `exportCsv(cols, tableData.value)` —— tableData 只是当页数据，
 * 用户看到的现象就是"导出只有当前页"。现改为按页循环拉全量（每页 500，上限 20000 行），
 * 与其余列表页（exportListAsSeen / collectFilteredRows）统一口径。
 */
async function exportData() {
  if (exporting.value) return
  exporting.value = true
  try {
    const cols = visibleSortedColumns.value.map(c => ({ label: c.label, prop: c.prop }))
    const rows = await collectFilteredRows((params) => getInventoryFlowList(params), buildParams(false))
    if (!rows.length) { ElMessage.warning(tt('没有可导出的数据')); return }
    exportCsv(cols, rows, '库存流水')
    ElMessage.success(`${tt("已导出 ")}${rows.length}${tt(" 条数据")}`)
  } catch (e) {
    ElMessage.error(e?.message || tt('导出失败'))
  } finally {
    exporting.value = false
  }
}

onMounted(async () => {
  // 数据看板穿透：URL query 的 startDate/endDate 注入日期区间（无 query 时行为完全不变；
  // 字段名 startDate/endDate 是看板跳转约定，不许改名）
  const route = useRoute()
  const q = route.query || {}
  if (q.startDate && q.endDate) {
    query.startDate = String(q.startDate)
    query.endDate = String(q.endDate)
    ElMessage.info(tt('已应用数据看板穿透筛选条件'))
  }
  // 加载物料自定义字段定义：一份数据同时喂给「表格自定义字段列」与「自定义字段搜索」通用件
  // （只请求一次接口；失败静默降级 → 退化为纯静态列，不阻塞列表加载）
  try {
    const result = await loadCustomFieldDefinitions('material')
    const fields = Array.from(result.fieldMap.values())
    if (fields.length > 0) {
      customColumns.value = customFieldsToTableColumns(fields, { editable: false, listShowOnly: true })
        .map(c => ({ ...c, type: 'custom' }))
    }
    await cf.load(fields)
  } catch (e) {
    console.warn('[StockFlowList] 加载物料自定义字段定义失败:', e)
  }
  loadOptions()
  loadStats()
  loadData()
})
useAutoRefresh(() => {
  loadOptions()
  loadData()
})
</script>

<style scoped lang="scss">
.stock-flow-list-page {
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

.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  position: relative;
  overflow: hidden;
  transition: all .3s;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 24px rgba(99, 102, 241, 0.1);
    border-color: #c7d2fe;
  }

  .stat-icon {
    width: 48px;
    height: 48px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 24px;
    color: #fff;
    position: relative;
    z-index: 1;

    &.primary { background: linear-gradient(135deg, #6366f1, #8b5cf6); }
    &.warning { background: linear-gradient(135deg, #f59e0b, #f97316); }
    &.info { background: linear-gradient(135deg, #3b82f6, #60a5fa); }
    &.success { background: linear-gradient(135deg, #10b981, #34d399); }
    &.danger { background: linear-gradient(135deg, #ef4444, #f87171); }
  }

  .stat-info { flex: 1; }
  .stat-value {
    font-size: 24px;
    font-weight: 700;
    color: #111827;
    line-height: 1.2;
  }
  .stat-label {
    font-size: 12px;
    color: #9ca3af;
    margin-top: 4px;
  }

  .stat-glow {
    position: absolute;
    right: -20px;
    top: -20px;
    width: 100px;
    height: 100px;
    border-radius: 50%;
    opacity: 0.1;
    filter: blur(20px);

    &.primary { background: #6366f1; }
    &.warning { background: #f59e0b; }
    &.info { background: #3b82f6; }
    &.success { background: #10b981; }
    &.danger { background: #ef4444; }
  }
}

.content-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.search-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #f3f4f6;
  gap: 12px;
  flex-wrap: wrap;

  .search-right {
    display: flex;
    gap: 8px;
    align-items: center;
    flex-wrap: wrap;
  }

  .search-input {
    width: 200px;
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

  .search-date {
    :deep(.el-range-editor.el-input__wrapper) {
      border-radius: 8px;
    }
  }
}

/* 台账页特有筛选项：与通用壳/BillSearchBar 同款「标签 + 控件」对齐规则
   （标签固定 60px 右对齐；控件撑满剩余宽度，保证多行筛选逐列对齐） */
.filter-item {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
  width: calc(var(--erp-list-search-width, 280px) + 68px);

  .filter-label {
    flex: 0 0 60px;
    width: 60px;
    text-align: right;
    font-size: 13px;
    color: #6b7280;
    white-space: nowrap;
  }

  > .el-select,
  > .el-input,
  > .el-date-editor {
    flex: 1 1 auto;
    width: auto !important;
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

.order-no-cell {
  display: flex;
  align-items: center;
  gap: 6px;

  .order-no-text {
    color: #6366f1;
    font-weight: 600;

    &.is-link { cursor: pointer; }
    &.is-link:hover { text-decoration: underline; }
    /* 反查不到单据（已删除/无独立编辑页）：退化为普通文本，避免"看起来能点、点了没反应" */
    &.no-link { color: #374151; font-weight: 500; cursor: default; }
  }

  .link-icon {
    color: #c7d2fe;
    font-size: 12px;
    opacity: 0;
    transition: opacity .2s;
  }
  &:hover .link-icon { opacity: 1; }
}

.amount-text { color: #374151; font-variant-numeric: tabular-nums; font-weight: 600; }
.discount-text { color: #f59e0b; font-variant-numeric: tabular-nums; font-weight: 600; }
.net-amount {
  color: #10b981;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.text-muted { color: #c0c4cc; }

.status-cell {
  display: flex;
  align-items: center;
  gap: 6px;

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

@media (max-width: 1200px) {
  .stat-cards { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 768px) {
  .stock-flow-list-page { padding: 12px; }
  .stat-cards { grid-template-columns: 1fr; }
  .search-bar {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }
}
</style>
