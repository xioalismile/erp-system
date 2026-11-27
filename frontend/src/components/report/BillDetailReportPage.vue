<template>
  <div class="bill-detail-report">
    <!-- 页头 -->
    <div class="page-header">
      <div class="header-left">
        <div v-if="backPath" class="back-btn" @click="$router.push(backPath)">
          <el-icon><ArrowLeft /></el-icon>
          <span>{{ $tt('返回') }}</span>
        </div>
        <div class="title-group">
          <div class="page-title">
            {{ $tf(title) }}
            <span v-if="badge" class="title-badge">{{ badge }}</span>
          </div>
          <div class="page-sub">{{ $tf(subTitle) }}</div>
        </div>
      </div>
      <div class="header-actions">
        <el-button type="primary" plain :loading="exporting" v-permission="perm('export')" @click="handleExport">
          <el-icon><Download /></el-icon>{{ $tt('导出 Excel') }}
        </el-button>
      </div>
    </div>

    <!-- 查询卡：筛选条件由「筛选设置」控制显隐与顺序，每个条件按字段类型渲染不同控件 -->
    <div class="content-card">
      <div class="search-bar">
        <div class="search-cols">
          <div class="search-filters">
            <div v-for="f in visibleFilters" :key="f.key" class="filter-item" :class="'fi-' + f.type">
              <span class="filter-label" :title="f.label">{{ $tf(f.label) }}</span>

              <!-- 日期区间 -->
              <el-date-picker
                v-if="f.type === 'dateRange'"
                v-model="filterValues[f.key]"
                type="daterange"
                :range-separator="$tt('至')"
                :start-placeholder="$tt('开始日期')"
                :end-placeholder="$tt('结束日期')"
                value-format="YYYY-MM-DD"
                unlink-panels
                clearable
                class="filter-control ctl-date"
                @change="onSearch"
              />

              <!-- 数值区间 -->
              <span v-else-if="f.type === 'numberRange'" class="num-range">
                <el-input
                  v-model="filterValues[f.key].min"
                  :placeholder="$tt('最小')"
                  clearable
                  class="num-input"
                  @keyup.enter="onSearch"
                  @clear="onSearch"
                />
                <span class="num-sep">~</span>
                <el-input
                  v-model="filterValues[f.key].max"
                  :placeholder="$tt('最大')"
                  clearable
                  class="num-input"
                  @keyup.enter="onSearch"
                  @clear="onSearch"
                />
              </span>

              <!-- 下拉（静态枚举 / 主档选项） -->
              <el-select
                v-else-if="f.type === 'select'"
                v-model="filterValues[f.key]"
                filterable
                clearable
                :loading="!!optionLoading[f.optionSource]"
                :placeholder="$tt('请选择')"
                class="filter-control"
                @change="onSearch"
                @visible-change="(open) => open && ensureOptions(f)"
              >
                <el-option v-for="o in optionsOf(f)" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>

              <!-- 文本模糊 -->
              <el-input
                v-else
                v-model="filterValues[f.key]"
                clearable
                :placeholder="$tt('请输入')"
                class="filter-control"
                @keyup.enter="onSearch"
                @clear="onSearch"
              />
            </div>
          </div>
          <div class="search-actions">
            <el-button type="primary" v-permission="perm('query')" :loading="loading" @click="onSearch">{{ $tt('搜索') }}</el-button>
            <el-button v-permission="perm('reset')" @click="onReset">{{ $tt('重置') }}</el-button>
            <el-tooltip :content="$tt('列设置')" placement="top">
              <el-button :icon="Setting" circle v-permission="perm('columnSetting')" @click="openColumnSetting" />
            </el-tooltip>
            <el-tooltip :content="$tt('筛选设置')" placement="top">
              <el-button :icon="Filter" circle v-permission="perm('filterSetting')" @click="openFilterSetting" />
            </el-tooltip>
          </div>
        </div>
      </div>
    </div>

    <!-- 统计卡（随筛选联动） -->
    <div class="stat-row">
      <div v-for="s in statCards" :key="s.key" class="stat-card">
        <div class="stat-label">{{ $tt(s.label) }}</div>
        <div class="stat-value">{{ s.value }}</div>
      </div>
    </div>

    <!-- 数据表（border 必开：Element Plus 只有带 border 的表格才允许拖拽列宽） -->
    <div class="content-card">
      <el-table
        ref="tableRef"
        :key="configVersion"
        :data="tableData"
        v-loading="loading"
        class="tech-table"
        border
        :header-cell-style="headerCellStyle"
        show-summary
        :summary-method="summaryMethod"
        :max-height="tableMaxHeight"
        @sort-change="onSortChange"
        @header-dragend="onHeaderDragend"
      >
        <el-table-column type="index" label="#" width="50" fixed="left" align="center" />
        <el-table-column
          v-for="col in visibleColumns"
          :key="col.prop"
          :prop="col.prop"
          :label="$tf(col.label)"
          :min-width="colMinWidth(col)"
          :fixed="getColFixed(col.prop) || false"
          :sortable="col.sortable ? 'custom' : false"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <el-tag v-if="col.type === 'status'" :type="statusTagType(row[col.prop])" size="small">{{ row[col.prop] ?? '' }}</el-tag>
            <span v-else-if="col.type === 'amount'">{{ fmtAmount(row[col.prop]) }}</span>
            <span v-else-if="col.type === 'number'">{{ fmtNumber(row[col.prop]) }}</span>
            <span v-else>{{ row[col.prop] ?? '' }}</span>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-guide">
            <el-icon class="empty-icon"><Document /></el-icon>
            <div class="empty-title">{{ $tf('当前筛选条件下没有' + title + '数据') }}</div>
            <div class="empty-sub">{{ $tt('请放宽单据日期区间，或清空客户 / 商品等条件后重试。') }}</div>
          </div>
        </template>
      </el-table>

      <div class="pagination-wrap">
        <div class="pagination-info">{{ $tt('共') }} {{ pagination.total }} {{ $tt('条明细行') }}</div>
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[20, 50, 100, 200]"
          layout="prev, pager, next, sizes, jumper"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </div>

    <!-- 列设置（复用全站通用件；保存后本页会自动重新取数） -->
    <ColumnSettingDrawer
      v-model="columnSettingVisible"
      :page-key="pageKey"
      :columns="columnDefs"
      :title="$tt('列设置')"
      @change="onColumnSettingChange"
    />

    <!-- 筛选设置（保存后本页会自动重新取数） -->
    <FilterSettingPanel
      v-model="filterSettingVisible"
      :page-key="pageKey"
      :filters="filterDefs"
      :title="$tt('筛选设置')"
      @save="onFilterSave"
      @reset="onFilterReset"
    />
  </div>
</template>

<script setup>
/**
 * 单据明细报表通用页（销售 / 采购 / 库存 等**全部**模块的单据明细报表共用这一个组件）
 *
 * 用户要求"各报表功能一模一样，复刻不能缺东西" —— 所以每个报表**不是各写一份近似代码**，
 * 而是同一个组件 + 各自的后端列配置（wrapper 只传 props）。任何功能改动天然全站同时生效。
 *
 * 口径（所有明细报表一致）：
 *   - 一行 = 一条商品明细（单据 ⋈ 明细），单据无明细不出现
 *   - 列清单 = 后端 /columns（系统字段 + 自定义字段），前端不另写一份
 *   - 默认勾选的搜索条件 = 后端 defaultFilterChecked（7 项：单号 / 对方单位 / 日期 / 业务员 /
 *     商品名称 / 商品编号 / 规格）
 *   - **默认表格列 = 全部列**（后端 defaultVisible 下发所有列，含自定义字段列）——
 *     用户 2026-09-22 口径：不需要的列由用户自己在「列设置」里关掉。
 *     （此前是"默认只显示 7 列"，导致自定义字段列被默认隐藏、用户以为"自定义字段的值没显示"）
 *   - **全部字段都可作为筛选条件**：按类型渲染文本 / 下拉 / 日期区间 / 数值区间
 *   - 首次打开默认「本月 1 日 ~ 今天」（后端用 defaultCurrentMonth 标记哪个条件）
 *   - 列设置 / 筛选设置保存后**立即重新取数**
 *   - 列宽可拖且持久化（表格带 border）
 *   - 合计行：明细列直接求和；单据级金额列由后端按单据去重后求和（防一单多行重复累加）
 *   - 数据权限：后端按角色数据范围过滤
 *   - **不再有页内快捷跳转页签**（2026-09-22 用户要求删除）：各报表已在左侧菜单「<模块> → 报表」
 *     下各自独立成项，页内再放一排跳转页签纯属多余
 *
 * 每个报表必须传**各自独立的 pageKey**（列设置与筛选设置的本地记忆按它隔离）。
 */
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Download, Setting, Filter, Document } from '@element-plus/icons-vue'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
import FilterSettingPanel from '@/components/FilterSettingPanel.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { useFilterSetting } from '@/composables/useFilterSetting'
import { usePagePerm } from '@/utils/pagePerm'
import { tt } from '@/i18n'
import { createBillDetailReportApi } from '@/api/report/billDetailReport'
import { customerSelectOptions } from '@/api/basedata/customer'
import { employeeSelectOptions } from '@/api/basedata/employee'
import { listAllWarehouse } from '@/api/basedata/warehouse'
import { listAllDepartment } from '@/api/basedata/department'
import { listAllCurrency } from '@/api/basedata/currency'
import { listAllSettle } from '@/api/basedata/settle'
import { listAllPriceLevel } from '@/api/basedata/price-level'
import { supplierSelectOptions } from '@/api/basedata/supplier'

const props = defineProps({
  /** 后端基础路径，如 /report/sale/order-detail */
  apiBase: { type: String, required: true },
  /** 列设置 / 筛选设置的本地记忆 key（三个报表必须各不相同） */
  pageKey: { type: String, required: true },
  /** 报表名，如「销售订单明细报表」（也用于空态文案） */
  title: { type: String, required: true },
  /** 页头英文角标 */
  badge: { type: String, default: '' },
  /** 页头副标题 */
  subTitle: { type: String, default: '' },
  /** 返回目标列表页 */
  backPath: { type: String, default: '' },
  /** 导出文件名前缀（不含日期） */
  exportName: { type: String, required: true }
})

const perm = usePagePerm()
const api = createBillDetailReportApi(props.apiBase)

/** 本月区间（单据日期的默认筛选口径） */
function monthRange() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return [`${d.getFullYear()}-${pad(d.getMonth() + 1)}-01`, `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`]
}

// ==================== 元数据（列 / 筛选条件） ====================

const columnMeta = ref([])
const filterMeta = ref([])

/** 列设置用的列定义（prop/label/type/fixed/defaultHidden） */
const columnDefs = computed(() => columnMeta.value
  .map(c => ({
    prop: c.key,
    label: c.label,
    type: c.type,
    fixed: c.fixed || false,
    sortable: !!c.sortable,
    defaultHidden: c.visible === false
  }))
  .filter(c => c.prop))

/** 筛选条件定义（key/label/group/type/optionSource/options/paramKeys/defaultChecked） */
const filterDefs = computed(() => filterMeta.value.map(f => ({
  key: f.key,
  label: f.label,
  group: f.group,
  type: f.type,
  optionSource: f.optionSource,
  options: f.options,
  paramKeys: f.paramKeys,
  defaultChecked: f.defaultChecked !== false,
  defaultCurrentMonth: !!f.defaultCurrentMonth
})))

const {
  columnSettingVisible,
  openColumnSetting,
  isColVisible,
  getColFixed,
  configVersion,
  sortedColumns,
  onHeaderDragend,
  applySavedWidths,
  resetColWidths
} = useColumnSetting(props.pageKey, columnDefs)

const {
  filterSettingVisible,
  openFilterSetting,
  visibleFilters,
  saveFilterConfig,
  resetFilterConfig
} = useFilterSetting(props.pageKey, filterDefs)

/** 可见且按用户顺序排列的列 */
const visibleColumns = computed(() => sortedColumns.value.filter(c => isColVisible(c.prop)))

// ==================== 查询状态 ====================

const loading = ref(false)
const exporting = ref(false)
const tableData = ref([])
const stats = ref({})
const totals = ref({})
const pagination = reactive({ current: 1, size: 50, total: 0 })
const sortState = reactive({ sortBy: '', sortOrder: '' })
const tableMaxHeight = ref(Math.max(320, window.innerHeight - 420))
const tableRef = ref(null)

/**
 * 筛选值容器：key = 筛选条件 key；按类型存不同结构
 *   text / select → 标量；dateRange → [起,止]；numberRange → { min, max }
 */
const filterValues = reactive({})

/**
 * 自定义报表下钻带来的固定条件（2026-11-12）：直接作为后端 f_* 参数随查询发送，
 * 不依赖"该条件是否被用户勾选在查询区"，保证从报表点行进来就能看到该物料的明细。
 */
const route = useRoute()
const drillParams = reactive({})

function applyDrillQuery() {
  const q = route.query || {}
  let hit = false
  // 物料编码：报表按物料汇总后下钻，落到明细行
  if (q.materialCode) {
    drillParams.f_materialCode = String(q.materialCode)
    hit = true
  }
  // 其它参数：已按后端入参名（f_ 前缀）传入的原样透传
  Object.keys(q).forEach(k => {
    if (k.startsWith('f_') && q[k] !== undefined && q[k] !== null && String(q[k]) !== '') {
      drillParams[k] = String(q[k])
      hit = true
    }
  })
  if (hit) ElMessage.info(tt('已应用报表下钻筛选条件'))
}

/** 下钻进来时清掉"默认本月"日期条件：用户要看的是该物料全部明细，不能被默认月份截断 */
function clearDefaultDateRanges() {
  if (!Object.keys(drillParams).length) return
  for (const f of filterDefs.value) {
    if (f.type === 'dateRange') filterValues[f.key] = []
  }
}

/** 按筛选条件类型初始化空值（已有值的保留，避免保存筛选设置后被清空） */
function ensureFilterValues() {
  for (const f of filterDefs.value) {
    const cur = filterValues[f.key]
    if (f.type === 'numberRange') {
      if (!cur || typeof cur !== 'object') filterValues[f.key] = { min: '', max: '' }
    } else if (f.type === 'dateRange') {
      if (!Array.isArray(cur)) filterValues[f.key] = f.defaultCurrentMonth ? monthRange() : []
    } else if (cur === undefined || cur === null) {
      filterValues[f.key] = ''
    }
  }
}

/** 拼后端查询参数（只带"已勾选且在查询区可见"的条件） */
function buildParams(forExport = false) {
  const p = {}
  if (!forExport) {
    p.page = pagination.current
    p.size = pagination.size
  }
  const visibleKeys = new Set(visibleFilters.value.map(f => f.key))
  for (const f of filterDefs.value) {
    if (!visibleKeys.has(f.key)) continue
    const v = filterValues[f.key]
    const pk = f.paramKeys || {}
    if (f.type === 'dateRange') {
      if (Array.isArray(v) && v.length === 2) {
        if (v[0]) p[pk.from] = v[0]
        if (v[1]) p[pk.to] = v[1]
      }
    } else if (f.type === 'numberRange') {
      const min = v && v.min !== undefined ? String(v.min).trim() : ''
      const max = v && v.max !== undefined ? String(v.max).trim() : ''
      if (min !== '') p[pk.min] = min
      if (max !== '') p[pk.max] = max
    } else {
      if (v !== null && v !== undefined && String(v).trim() !== '') p[pk.value] = v
    }
  }
  if (sortState.sortBy) {
    p.sortBy = sortState.sortBy
    p.sortOrder = sortState.sortOrder
  }
  p.cols = visibleColumns.value.map(c => c.prop).join(',')
  // 报表下钻条件最后覆盖（用户从报表点行进来时以报表条件为准）
  Object.assign(p, drillParams)
  return p
}

async function loadMeta() {
  try {
    const res = await api.getColumns()
    columnMeta.value = res?.columns || []
    filterMeta.value = res?.filters || []
    statsMeta.value = res?.stats || []
    ensureFilterValues()
  } catch (e) {
    if (e?.message) ElMessage.error(e.message)
  }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await api.query(buildParams())
    tableData.value = res?.records || []
    pagination.total = res?.total || 0
    stats.value = res?.stats || {}
    totals.value = res?.totals || {}
  } catch (e) {
    tableData.value = []
    pagination.total = 0
    stats.value = {}
    totals.value = {}
    if (e?.message) ElMessage.error(e.message)
  } finally {
    loading.value = false
    nextTick(() => applySavedWidths(tableRef))
  }
}

function onSearch() {
  pagination.current = 1
  fetchData()
}

function onReset() {
  for (const f of filterDefs.value) {
    if (f.type === 'numberRange') filterValues[f.key] = { min: '', max: '' }
    else if (f.type === 'dateRange') filterValues[f.key] = f.defaultCurrentMonth ? monthRange() : []
    else filterValues[f.key] = ''
  }
  sortState.sortBy = ''
  sortState.sortOrder = ''
  onSearch()
}

function onSortChange({ prop, order }) {
  if (!prop || !order) {
    sortState.sortBy = ''
    sortState.sortOrder = ''
  } else {
    sortState.sortBy = prop
    sortState.sortOrder = order === 'ascending' ? 'asc' : 'desc'
  }
  fetchData()
}

// ==================== 列设置 / 筛选设置联动 ====================
/**
 * 列设置保存 / 恢复默认 / 恢复默认列宽之后：
 *   ① 列宽 → 重新套用
 *   ② 可见列变了 → 查询参数里的 cols 变了 → **必须重新取数**，否则新勾的列只有表头没有数据
 */
function onColumnSettingChange(e) {
  const type = e && e.type
  if (type === 'resetWidths') {
    nextTick(() => resetColWidths(tableRef))
    return
  }
  nextTick(() => applySavedWidths(tableRef))
  pagination.current = 1
  fetchData()
}

/** 筛选设置保存：查询区条件变了 → 立刻按新条件取数 */
function onFilterSave(cfg) {
  saveFilterConfig(cfg)
  ensureFilterValues()
  onSearch()
}

/** 筛选设置恢复默认：同上 */
function onFilterReset() {
  resetFilterConfig()
  ensureFilterValues()
  onReset()
}

// ==================== 下拉选项 ====================

const options = reactive({})
const optionLoading = reactive({})
const optionLoaded = reactive({})

/** 静态枚举（后端已在 filter def 里给了 options）；其余来源按实体拉主档 */
function optionsOf(f) {
  if (Array.isArray(f.options) && f.options.length) {
    return f.options.map(v => ({ value: v, label: v }))
  }
  return options[f.optionSource] || []
}

async function ensureOptions(f) {
  const source = f && f.optionSource
  if (!source) return
  if (Array.isArray(f.options) && f.options.length) return
  if (optionLoaded[source]) return
  optionLoading[source] = true
  try {
    if (source === 'customer') {
      const res = await customerSelectOptions('')
      options.customer = (Array.isArray(res) ? res : (res?.records || []))
        .map(c => ({ value: c.customerId, label: c.customerName }))
    } else if (source === 'employee') {
      const res = await employeeSelectOptions('')
      options.employee = (Array.isArray(res) ? res : (res?.records || []))
        .map(e => ({ value: e.employeeId, label: e.employeeName + (e.employeeCode ? ` (${e.employeeCode})` : '') }))
    } else if (source === 'warehouse') {
      const res = await listAllWarehouse()
      options.warehouse = (Array.isArray(res) ? res : (res?.records || []))
        .map(w => ({ value: w.stockId || w.warehouseId, label: w.stockName || w.warehouseName }))
    } else if (source === 'department') {
      const res = await listAllDepartment()
      options.department = (Array.isArray(res) ? res : (res?.records || []))
        .map(d => ({ value: d.deptId, label: d.deptName }))
    } else if (source === 'currency') {
      const res = await listAllCurrency()
      options.currency = (Array.isArray(res) ? res : (res?.records || []))
        .map(c => ({ value: c.currencyId, label: c.currencyCode }))
    } else if (source === 'settleMode') {
      const res = await listAllSettle()
      options.settleMode = (Array.isArray(res) ? res : (res?.records || []))
        .map(s => ({ value: s.settleModeId, label: s.settleModeName }))
    } else if (source === 'supplier') {
      const res = await supplierSelectOptions('')
      options.supplier = (Array.isArray(res) ? res : (res?.records || []))
        .map(s => ({ value: s.supplierId, label: s.supplierName }))
    } else if (source === 'priceLevel') {
      // 价格等级（2026-10-14 列表页筛选条件自定义：明细报表同键筛选补齐选项来源）
      const res = await listAllPriceLevel()
      options.priceLevel = (Array.isArray(res) ? res : (res?.records || []))
        .map(p => ({ value: p.priceLevelId, label: p.priceLevelName }))
    }
    optionLoaded[source] = true
  } catch (e) {
    options[source] = []
  } finally {
    optionLoading[source] = false
  }
}

// ==================== 展示格式 ====================

const headerCellStyle = {
  background: 'rgba(99, 102, 241, 0.04)',
  color: '#1f2937',
  fontWeight: 600,
  borderBottom: '2px solid #e0e7ff'
}

function fmtNumber(v) {
  if (v === null || v === undefined || v === '') return ''
  const n = Number(v)
  if (Number.isNaN(n)) return v
  return n.toFixed(2)
}

/** 金额：空值留空（不出现孤零零的 ¥ 占位符） */
function fmtAmount(v) {
  if (v === null || v === undefined || v === '') return ''
  const n = Number(v)
  if (Number.isNaN(n)) return v
  return '¥' + n.toFixed(2)
}

function statusTagType(status) {
  if (!status) return 'info'
  if (status.includes('已生效') || status.includes('已完成')) return 'success'
  if (status.includes('草稿') || status.includes('待')) return 'warning'
  if (status.includes('作废') || status.includes('取消')) return 'danger'
  return 'info'
}

function colMinWidth(col) {
  if (col.type === 'amount') return 140
  if (col.type === 'number') return 110
  if (col.type === 'date') return 110
  if (col.type === 'datetime') return 160
  if (col.fixed === 'left') return 160
  if (col.prop === 'customerName' || col.prop === 'materialName') return 150
  return 120
}

/** 合计行：数值列按后端 totals 填充（单据级金额已按单去重） */
function summaryMethod({ columns, data }) {
  if (!data || !data.length) return columns.map(() => '')
  const sums = []
  columns.forEach((col, idx) => {
    if (idx === 0) { sums[idx] = tt('合计'); return }
    const def = columnMeta.value.find(c => c.key === col.property)
    if (!def || (def.type !== 'amount' && def.type !== 'number')) { sums[idx] = ''; return }
    const v = totals.value[col.property]
    if (v === null || v === undefined) { sums[idx] = ''; return }
    sums[idx] = def.type === 'amount' ? fmtAmount(v) : fmtNumber(v)
  })
  return sums
}

/** 统计卡指标由后端 /columns 的 stats 声明（各单据实际有哪些可统计列不同，例如入库单没有"明细价税合计"） */
const statsMeta = ref([])

const statCards = computed(() => statsMeta.value.map(s => ({
  key: s.key,
  label: s.label,
  value: s.type === 'amount'
    ? fmtAmount(stats.value[s.key] ?? 0)
    : (s.type === 'number' ? fmtNumber(stats.value[s.key] ?? 0) : String(stats.value[s.key] ?? 0))
})))

// ==================== 导出 ====================

async function handleExport() {
  if (!tableData.value.length) {
    ElMessage.warning(tt('当前没有可导出的数据'))
    return
  }
  exporting.value = true
  try {
    const blob = await api.exportFile(buildParams(true))
    const url = window.URL.createObjectURL(new Blob([blob]))
    const a = document.createElement('a')
    a.href = url
    a.download = `${tt(props.exportName)}_${new Date().toISOString().slice(0, 10)}.xlsx`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    // 延后回收 objectURL（防御性写法）：避免 click 后立即 revoke 与浏览器读取 blob 竞争
    window.setTimeout(() => window.URL.revokeObjectURL(url), 4000)
    ElMessage.success(tt('导出成功'))
  } catch (e) {
    if (e?.message) ElMessage.error(e.message)
  } finally {
    exporting.value = false
  }
}

// ==================== 生命周期 ====================

function handleResize() {
  tableMaxHeight.value = Math.max(320, window.innerHeight - 420)
}

onMounted(async () => {
  window.addEventListener('resize', handleResize)
  applyDrillQuery()
  await loadMeta()
  clearDefaultDateRanges()
  // 预取已勾选下拉条件的选项，保证回显名称而不是 id
  visibleFilters.value.filter(f => f.type === 'select').forEach(f => ensureOptions(f))
  await fetchData()
  nextTick(() => applySavedWidths(tableRef))
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped lang="scss">
.bill-detail-report {
  padding: 20px 24px;
  background: linear-gradient(135deg, #f8faff 0%, #f0f4ff 50%, #f8fcff 100%);
  min-height: calc(100vh - 60px);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;

  .header-left { display: flex; align-items: center; gap: 16px; }
  .header-actions { display: flex; gap: 8px; }

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
    &:hover { color: #6366f1; border-color: #c7d2fe; }
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
}

.content-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  overflow: hidden;
  margin-bottom: 12px;
}

.search-bar {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 16px;

  .search-cols {
    display: flex;
    align-items: flex-start;
    gap: 12px;
    flex: 1 1 auto;
    min-width: 0;
  }

  /* 查询区用 grid：每格 = 统一宽度标签 + 自适应控件。
     之前用 flex + 各类型标签宽度不一（60/76px），同行控件左边缘对不齐；
     改成 grid 后同行控件严格对齐，并按可用宽度自动排 3~4 个一行（最少 3 个）。 */
  .search-filters {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
    align-items: center;
    gap: 10px 16px;
    flex: 1 1 auto;
    min-width: 0;

    .filter-item {
      display: flex;
      align-items: center;
      gap: 8px;
      min-width: 0;
    }

    /* 所有类型标签统一 80px（放不下时省略号 + title 提示），保证控件列对齐 */
    .filter-label {
      flex: 0 0 80px;
      width: 80px;
      text-align: right;
      font-size: 13px;
      color: #6b7280;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .filter-control {
      flex: 1 1 auto;
      min-width: 0;
      width: auto;
    }

    .num-range {
      display: flex;
      align-items: center;
      gap: 4px;
      flex: 1 1 auto;
      min-width: 0;

      .num-input { flex: 1 1 0; min-width: 60px; }
      .num-sep { color: #c0c4cc; flex: 0 0 auto; }
    }
  }

  .search-actions {
    display: flex;
    align-items: center;
    align-self: flex-start;
    /* 按钮间距收紧（12→8），把横向空间让给查询区 */
    gap: 8px;
    flex: 0 0 auto;
    margin-left: 4px;
    padding-left: 12px;
    border-left: 1px solid #f3f4f6;
  }
}

.stat-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;

  .stat-card {
    flex: 1 1 160px;
    background: #fff;
    border: 1px solid #e5e7eb;
    border-radius: 10px;
    padding: 12px 16px;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .stat-label { font-size: 12px; color: #909399; }
  .stat-value {
    font-size: 16px;
    font-weight: 600;
    color: #303133;
    font-variant-numeric: tabular-nums;
  }
}

.tech-table {
  :deep(.el-table__header th) { background: rgba(99, 102, 241, 0.04) !important; }
  :deep(.el-table__row:hover > td) { background: rgba(99, 102, 241, 0.03) !important; }
  :deep(.el-table td) { border-bottom: 1px solid #f3f4f6; }
  :deep(.el-table__footer-wrapper td) {
    background: #fafbfc;
    font-weight: 600;
    color: #303133;
  }
}

.empty-guide {
  padding: 28px 16px;
  text-align: center;

  .empty-icon { font-size: 32px; color: #c0c4cc; }
  .empty-title { margin-top: 8px; font-size: 14px; color: #606266; }
  .empty-sub { margin-top: 4px; font-size: 12px; color: #909399; }
}

.pagination-wrap {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-top: 1px solid #f3f4f6;

  .pagination-info { font-size: 13px; color: #6b7280; }
}

:deep(.el-button) { border-radius: 8px; font-weight: 500; }

@media (max-width: 768px) {
  .bill-detail-report { padding: 12px; }
  .search-bar { flex-direction: column; align-items: stretch; }
}
</style>
