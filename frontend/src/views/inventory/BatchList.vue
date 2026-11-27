<template>
  <div class="batch-list-page">
    <!-- 顶部标题 + 操作栏 -->
    <div class="page-header">
      <div class="header-left">
        <div class="title-group">
          <div class="page-title">
            批次档案
            <span class="title-badge">BATCH</span>
          </div>
          <div class="page-sub">{{ $tt("全程追溯物料批次 · 智能预警临期库存") }}</div>
        </div>
      </div>
      <div class="header-actions">
        <el-button type="warning" :icon="Warning" v-permission="perm('query')" @click="loadExpiring">{{ $tt("临期预警") }}</el-button>
        <el-button type="success" :icon="Download" :loading="exporting" v-permission="perm('export')" @click="handleExport">{{ $tt("导出") }}</el-button>
      </div>
    </div>

    <!-- 统计卡片（通用件）：带 filter 的卡片可点击 → 按该状态筛选下方列表；再点同一张卡＝取消 -->
    <StatCards :stats="stats" :active-key="activeStatKey" @stat-click="onStatClick" />

    <!-- 主内容区 -->
    <div class="content-card">
      <!-- 搜索 + 状态 tab -->
      <div class="search-bar">
        <div class="status-tabs">
          <div
            v-for="tab in statusTabs"
            :key="tab.value"
            :class="['status-tab', { active: activeTab === tab.value }]"
            @click="switchTab(tab.value)"
          >
            <span class="tab-text">{{ $tf(tab.label) }}</span>
            <span v-if="tab.count > 0" class="tab-count">{{ tab.count }}</span>
          </div>
        </div>
        <div class="search-right">
          <el-input v-model="batchNo" :placeholder="$tt('批次号')" clearable class="search-input" @keyup.enter="loadData" />
          <el-input v-model="keyword" :placeholder="$tt('物料编码/名称')" clearable class="search-input" @keyup.enter="loadData" />
          <el-input v-model="supplierName" :placeholder="$tt('供应商名称')" clearable class="search-input" @keyup.enter="loadData" />
          <!-- 物料自定义字段「可搜索」项（通用件）：设置中心勾了「可搜索」的字段才渲染，未勾选时渲染 0 个节点 -->
          <CustomFieldSearchBar :fields="cf.searchFields.value" :model="cf.values" @change="cf.setValue" @search="loadData" />
          <el-button type="primary" v-permission="perm('query')" @click="loadData">{{ $tt("查询") }}</el-button>
          <el-button v-permission="perm('reset')" @click="onReset">{{ $tt("重置") }}</el-button>
          <el-tooltip :content="$tt('列设置')" placement="top">
            <el-button :icon="Setting" circle v-permission="perm('columnSetting')" @click="openColumnSetting" />
          </el-tooltip>
        </div>
      </div>

      <!-- 表格（border 必开：Element Plus 只有带 border 的表格才允许拖拽列宽） -->
      <el-table ref="tableRef" :data="tableData" v-loading="loading" class="tech-table" border :header-cell-style="headerCellStyle" show-summary :summary-method="getSummary" @sort-change="onSortChange" @header-dragend="onHeaderDragend" :key="configVersion">
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
              <!-- 批次号可点：打开批次溯源弹窗（2026-11-06） -->
              <el-link
                v-if="col.type === 'code' && col.prop === 'batchNo'"
                type="primary"
                :underline="false"
                class="batch-no-link"
                @click="openTrace(row)"
              >{{ row[col.prop] }}</el-link>
              <span v-else-if="col.type === 'code'" class="order-no-text">{{ row[col.prop] }}</span>
              <span v-else-if="col.type === 'amount'" class="amount-text">{{ Number(row[col.prop] || 0).toFixed(4) }}</span>
              <span v-else-if="col.type === 'netAmount'" class="net-amount">{{ Number(row[col.prop] || 0).toFixed(4) }}</span>
              <span v-else-if="col.type === 'discount'" class="discount-text">{{ Number(row[col.prop] || 0).toFixed(4) }}</span>
              <span v-else-if="col.type === 'cost'">¥{{ Number(row[col.prop] || 0).toFixed(4) }}</span>
              <el-tag v-else-if="col.type === 'expiry'" :type="getExpiryTagType(row.expiryDate)" size="small" effect="light">
                {{ row.expiryDate || '-' }}
              </el-tag>
              <div v-else-if="col.type === 'status'" class="status-cell">
                <span :class="['status-dot', statusDotClass(row.status)]"></span>
                <el-tag size="small" :type="getStatusTagType(row.status)" effect="light" class="status-tag">{{ $dt(row.status || '正常') }}</el-tag>
              </div>
              <!-- 化妆品批记录：批次质量状态（待检/合格/不合格/已放行/已冻结） -->
              <el-tag v-else-if="col.type === 'qc'" size="small" effect="light" :type="getQcTagType(row.qcStatus)">
                {{ row.qcStatus || '' }}
              </el-tag>
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
        <!-- 化妆品批记录：批次追溯入口（正查下游客户 / 反查用了它的成品批） -->
        <el-table-column :label="$tf('操作')" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openTrace(row)">{{ $tt('追溯') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrap">
        <div class="pagination-info">共 {{ total }} 条记录</div>
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[20, 50, 100, 200]"
          layout="prev, pager, next, sizes, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </div>

    <!-- 临期预警弹窗 -->
    <el-dialog :title="$tf('临期预警（30天内到期）')" v-model="showExpiring" width="980px" class="tech-dialog">
      <el-table :data="expiringData" class="tech-table" border :header-cell-style="headerCellStyle">
        <el-table-column prop="batchNo" :label="$tf('批次号')" min-width="150" sortable="custom"/>
        <el-table-column prop="materialCode" :label="$tf('物料编码')" min-width="130" sortable="custom"/>
        <el-table-column prop="materialName" :label="$tf('物料名称')" min-width="180" sortable="custom"/>
        <el-table-column prop="quantity" :label="$tf('库存数量')" width="110" align="right" sortable="custom"/>
        <el-table-column prop="expiryDate" :label="$tf('到期日期')" width="120" sortable="custom"/>
        <el-table-column prop="daysLeft" :label="$tf('剩余天数')" width="110" sortable="custom">
          <template #default="{ row }">
            <el-tag :type="row.daysLeft <= 7 ? 'danger' : 'warning'" size="small">
              {{ row.daysLeft }} 天
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button v-permission="perm('dlgClose')" @click="showExpiring = false">{{ $tt("关闭") }}</el-button>
      </template>
    </el-dialog>

    <!-- 批次溯源弹窗（2026-11-06）：点批次号 / 追溯按钮打开 -->
    <BatchTraceDialog v-model="traceVisible" :batch-no="traceBatchNo" :material-id="traceMaterialId" />

    <!-- 列设置抽屉 -->
    <ColumnSettingDrawer v-model="columnSettingVisible" :page-key="'BatchList'" :columns="columns" />
  </div>
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Download, Warning, Setting, Document, EditPen, Clock, CircleCheck } from '@element-plus/icons-vue'
import { api } from '@/api'
import { getBatchInventoryStats } from '@/api/inventory/inventory'
import StatCards from '@/components/bill/StatCards.vue'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
import AuditColumns from '@/components/AuditColumns.vue'
import BatchTraceDialog from '@/components/inventory/BatchTraceDialog.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import {
  loadCustomFieldDefinitions,
  customFieldsToTableColumns,
  batchLoadCustomFieldValues
} from '@/composables/useCustomField'

import { useTableSort } from '@/composables/useTableSort'
import { useCustomFieldSearch } from '@/composables/useCustomFieldSearch'
import CustomFieldSearchBar from '@/components/CustomFieldSearchBar.vue'
import { exportCsv } from '@/utils/csvExport'
import { collectFilteredRows } from '@/utils/exportUtil'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'
const { handleSortChange, getSortParams } = useTableSort()
const perm = usePagePerm()

// 批次溯源弹窗（2026-11-06）：点批次号 / 追溯按钮都打开同一个弹窗
const traceVisible = ref(false)
const traceBatchNo = ref('')
const traceMaterialId = ref(null)

// 物料自定义字段「可搜索」搜索项（通用件）：设置中心勾了「可搜索」的物料字段才渲染
const cf = useCustomFieldSearch('material')


const onSortChange = (e) => { handleSortChange(e); loadData() }

// 列定义（用于列设置抽屉+动态渲染）= 静态业务列 + 物料自定义字段列（isListShow=1）
// type 标记单元格渲染分支
// code=批次号链接列 amount=数量(4位) netAmount=可用数量(绿色) discount=冻结数量(警示色) cost=单位成本(¥4位)
// expiry=有效期临期标签 status=状态标签 custom=自定义字段 纯文本列无 type
const staticColumns = [
  { prop: 'batchNo', label: '批次号', minWidth: 150, type: 'code' },
  { prop: 'materialCode', label: '物料编码', minWidth: 130 },
  { prop: 'materialName', label: '物料名称', minWidth: 180, tooltip: true },
  { prop: 'manufacturingNo', label: '制造编号', minWidth: 130 },
  { prop: 'quantity', label: '数量', width: 110, align: 'right', type: 'amount' },
  { prop: 'availableQuantity', label: '可用数量', width: 110, align: 'right', type: 'netAmount' },
  { prop: 'frozenQuantity', label: '冻结数量', width: 110, align: 'right', type: 'discount' },
  { prop: 'unitCost', label: '单位成本', width: 120, align: 'right', type: 'cost' },
  { prop: 'incomingDate', label: '入库日期', width: 120 },
  { prop: 'productionDate', label: '生产日期', width: 120 },
  { prop: 'expiryDate', label: '有效期至', width: 120, type: 'expiry' },
  { prop: 'supplierName', label: '供应商', minWidth: 160, tooltip: true },
  // 化妆品批记录：供应商批号（追溯树上游展示）+ 批次质量状态（待检/合格/不合格/已放行/已冻结）
  { prop: 'supplierBatchNo', label: '供应商批号', minWidth: 140, tooltip: true },
  { prop: 'qcStatus', label: '批次质量状态', width: 130, type: 'qc' },
  { prop: 'status', label: '状态', width: 120, type: 'status' },
  { prop: 'createdAt', label: '创建时间', width: 160 }
]
// 自定义字段列定义（onMounted 后异步填充，空时退化为纯静态列）
const customColumns = ref([])
// columns 动态合并静态列+自定义列
const columns = computed(() => [...staticColumns, ...customColumns.value])
const { columnSettingVisible, openColumnSetting, isColVisible, getColFixed, configVersion, sortedColumns, onHeaderDragend, applySavedWidths } = useColumnSetting('BatchList', columns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))
// 列宽持久化（2026-09-24）：表格带 border 才允许拖拽，拖完的宽度写入本页列配置，下次打开自动套用
const tableRef = ref(null)

const loading = ref(false)
/** 导出中（拉全量需要多次请求，按钮给 loading 反馈，避免重复点） */
const exporting = ref(false)
const tableData = ref([])
const total = ref(0)
const expiringData = ref([])
const showExpiring = ref(false)

const batchNo = ref('')
// 物料关键词（列表页通用口径）：一个框同时匹配 物料编码 / 物料名称（后端成组匹配）
const keyword = ref('')
const supplierName = ref('')
const statusFilter = ref('')
const activeTab = ref('all')

const query = reactive({ current: 1, size: 20, ...getSortParams() })

// 统计卡片：值一律取接口（/inventory/batch/stats，独立请求）
// 不再用"当前页行"自行统计 —— 列表分页时那样算出来的是本页数，不是全量
const stats = ref([])
const activeStatKey = ref('')

// 卡片 key ↔ 批次状态值（卡片与状态页签共用同一状态源，避免两处控件控同一条件）
const STAT_KEY_TO_STATUS = { normal: '正常', frozen: '冻结', usedUp: '已用完' }
const STAT_STATUS_TO_KEY = { 正常: 'normal', 冻结: 'frozen', 已用完: 'usedUp' }

async function loadStats() {
  try {
    const d = (await getBatchInventoryStats()) || {}
    stats.value = [
      { key: 'all', label: '批次总数', value: d.totalCount ?? 0, type: 'primary', icon: 'Document', filter: {} },
      { key: 'normal', label: '正常批次', value: d.normalCount ?? 0, type: 'success', icon: 'CircleCheck', filter: { status: '正常' } },
      { key: 'frozen', label: '冻结批次', value: d.frozenCount ?? 0, type: 'warning', icon: 'EditPen', filter: { status: '冻结' } },
      { key: 'usedUp', label: '已用完', value: d.usedUpCount ?? 0, type: 'info', icon: 'Clock', filter: { status: '已用完' } }
    ]
  } catch (e) {
    // 静默降级：取数失败时卡片保持原值，不弹提示、不阻断列表
  }
}

/** 卡片点击：套用对应状态（与状态页签同一状态源）；「批次总数」/再点同一张卡＝回到全部 */
function onStatClick({ key }) {
  if (key === 'all') {
    switchTab('all')
    return
  }
  const status = STAT_KEY_TO_STATUS[key] || 'all'
  switchTab(activeTab.value === status ? 'all' : status)
}

const statusTabs = computed(() => [
  { label: '全部', value: 'all', count: total.value },
  { label: '正常', value: '正常', count: tableData.value.filter(r => !r.status || r.status === '正常').length },
  { label: '冻结', value: '冻结', count: tableData.value.filter(r => r.status === '冻结').length },
  { label: '已用完', value: '已用完', count: tableData.value.filter(r => r.status === '已用完').length },
  { label: '已过期', value: '已过期', count: tableData.value.filter(r => r.status === '已过期').length }
])

const headerCellStyle = {
  background: 'rgba(99, 102, 241, 0.04)',
  color: '#1f2937',
  fontWeight: 600,
  borderBottom: '2px solid #e0e7ff'
}

function switchTab(value) {
  activeTab.value = value
  statusFilter.value = value === 'all' ? '' : value
  // 卡片高亮与页签共用同一状态源：点页签也会点亮对应卡片（同一条件只留一处状态）
  activeStatKey.value = STAT_STATUS_TO_KEY[value] || ''
  query.current = 1
  loadData()
}

/**
 * 查询入参（加载与导出**共用**，保证"导出的行 = 页面筛选后的行"）。
 * @param {boolean} includePaging 是否带分页参数；导出时传 false 拉全量
 */
function buildBatchParams(includePaging = true) {
  const params = {
    batchNo: batchNo.value,
    // 列表页通用口径：关键词（物料编码/名称）+ 供应商 + 状态 + 分页 + 排序 + 物料自定义字段
    keyword: keyword.value,
    supplierName: supplierName.value,
    status: statusFilter.value,
    ...getSortParams(),
    // 物料自定义字段搜索（通用件；无有效条件时返回 {}，与改造前一致）
    ...cf.buildParams()
  }
  if (includePaging) {
    params.current = query.current
    params.size = query.size
  }
  return params
}

async function loadData() {
  loading.value = true
  try {
    const res = await api.get('/inventory/batch/list', { params: buildBatchParams(true) })
    // 兼容后端返回结构
    if (Array.isArray(res)) {
      tableData.value = res
      total.value = res.length
    } else if (res?.records) {
      tableData.value = res.records
      total.value = res.total || 0
    } else if (res?.data?.records) {
      tableData.value = res.data.records
      total.value = res.data.total || 0
    } else if (res?.data && Array.isArray(res.data)) {
      tableData.value = res.data
      total.value = res.data.length
    } else {
      tableData.value = []
      total.value = 0
    }
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
    ElMessage.error(tt('加载批次库存失败'))
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
    // 数据到位后再套一次已保存的列宽（表格重建后列对象是新的）
    nextTick(() => applySavedWidths(tableRef))
  }
}

function onReset() {
  batchNo.value = ''
  keyword.value = ''
  supplierName.value = ''
  statusFilter.value = ''
  activeTab.value = 'all'
  // 卡片选中态一并清空（与页签同一状态源）
  activeStatKey.value = ''
  // 自定义字段搜索项一并清空（通用件）
  cf.reset()
  query.current = 1
  loadData()
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

function getExpiryTagType(expiryDate) {
  if (!expiryDate) return 'info'
  const expiry = new Date(expiryDate)
  const now = new Date()
  const diffDays = Math.ceil((expiry - now) / (1000 * 60 * 60 * 24))
  if (diffDays < 0) return 'danger'
  if (diffDays <= 7) return 'warning'
  if (diffDays <= 30) return 'warning'
  return 'success'
}

function getStatusTagType(s) {
  if (!s || s === '正常') return 'success'
  if (s === '冻结') return 'warning'
  if (s === '已用完') return 'info'
  if (s === '已过期') return 'danger'
  return 'info'
}

/** 化妆品批记录：批次质量状态标签色 */
function getQcTagType(s) {
  if (s === '已放行') return 'success'
  if (s === '待检') return 'warning'
  if (s === '不合格') return 'danger'
  if (s === '已冻结') return 'info'
  return 'primary'
}

/** 化妆品批记录：跳批次追溯树 */
function openTrace(row) {
  if (!row || !row.batchNo) {
    ElMessage.warning(tt('该批次缺少批次编号，无法追溯'))
    return
  }
  traceBatchNo.value = row.batchNo
  traceMaterialId.value = row.materialId ?? null
  if (traceMaterialId.value == null) {
    ElMessage.warning(tt('该批次缺少物料信息，无法追溯'))
    return
  }
  traceVisible.value = true
}

function statusDotClass(s) {
  if (!s || s === '正常') return 'dot-success'
  if (s === '冻结') return 'dot-warning'
  if (s === '已用完') return 'dot-info'
  if (s === '已过期') return 'dot-danger'
  return 'dot-info'
}

async function loadExpiring() {
  try {
    const res = await api.get('/inventory/batch/expiring', { params: { days: 30 } })
    const list = Array.isArray(res) ? res : (res?.data || res?.records || [])
    const now = new Date()
    expiringData.value = list.map(item => {
      const expiry = new Date(item.expiryDate)
      return {
        ...item,
        daysLeft: Math.ceil((expiry - now) / (1000 * 60 * 60 * 24))
      }
    })
    showExpiring.value = true
    if (expiringData.value.length === 0) {
      ElMessage.success(tt('30天内无临期批次'))
    }
  } catch (e) {
    ElMessage.error(tt('加载临期预警失败'))
  }
}

function getSummary({ columns, data }) {
  // 按列 prop 取值（列可被列设置拖动顺序/增删自定义字段，写死下标会错位）
  const sumFields = ['quantity', 'availableQuantity', 'frozenQuantity']
  const sums = []
  columns.forEach((col, i) => {
    if (i === 0) { sums[i] = '合计'; return }
    const prop = col.property
    if (sumFields.includes(prop)) {
      const total = (data || []).reduce((s, r) => s + (Number(r[prop]) || 0), 0)
      sums[i] = total.toFixed(4)
    } else {
      sums[i] = ''
    }
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
async function handleExport() {
  if (exporting.value) return
  exporting.value = true
  try {
    const cols = visibleSortedColumns.value.map(c => ({ label: c.label, prop: c.prop }))
    const rows = await collectFilteredRows((params) => api.get('/inventory/batch/list', { params }), buildBatchParams(false))
    if (!rows.length) { ElMessage.warning(tt('没有可导出的数据')); return }
    exportCsv(cols, rows, '批次台账')
    ElMessage.success(`${tt("已导出 ")}${rows.length}${tt(" 条数据")}`)
  } catch (e) {
    ElMessage.error(e?.message || tt('导出失败'))
  } finally {
    exporting.value = false
  }
}

onMounted(async () => {
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
    console.warn('[BatchList] 加载物料自定义字段定义失败:', e)
  }
  await loadStats()
  loadData()
})
// 切回本标签页 / 点标签栏「刷新当前页」时：列表与统计卡片一起重拉。
// （原写法 useAutoRefresh(() => { () => {...} }) 传进去的函数从未被执行 → 这两个入口一直空转）
useAutoRefresh(async () => {
  loadData()
  await loadStats()
})
</script>

<style scoped lang="scss">
.batch-list-page {
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
}

.status-tabs {
  display: flex;
  gap: 4px;

  .status-tab {
    padding: 8px 16px;
    font-size: 13px;
    color: #6b7280;
    cursor: pointer;
    border-radius: 8px;
    display: flex;
    align-items: center;
    gap: 6px;
    transition: all .2s;

    &:hover { color: #6366f1; background: #f5f3ff; }

    &.active {
      color: #6366f1;
      background: linear-gradient(135deg, #eef2ff, #f5f3ff);
      font-weight: 600;
    }

    .tab-count {
      font-size: 11px;
      background: #e5e7eb;
      color: #6b7280;
      padding: 1px 6px;
      border-radius: 10px;
    }

    &.active .tab-count {
      background: #6366f1;
      color: #fff;
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

/* 非批次号编码列：普通文本色 */
.order-no-text {
  color: #374151;
  font-weight: 600;
}

/* 批次号：明确的可点样式（蓝色 + 悬停下划线），避免用户看不出能点开溯源（2026-11-06） */
.batch-no-link {
  color: #1890ff !important;
  font-weight: 600;
  cursor: pointer;
}
.batch-no-link:hover {
  color: #40a9ff !important;
  text-decoration: underline;
}

.amount-text { color: #374151; font-variant-numeric: tabular-nums; font-weight: 600; }
.discount-text { color: #f59e0b; font-variant-numeric: tabular-nums; }
.net-amount {
  color: #10b981;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

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

:deep(.el-dialog) {
  border-radius: 12px;
}

@media (max-width: 768px) {
  .batch-list-page { padding: 12px; }
  .search-bar {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }
  .status-tabs { flex-wrap: wrap; }
}
</style>
