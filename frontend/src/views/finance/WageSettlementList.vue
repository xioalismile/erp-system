<template>
  <div class="wage-settlement-root">
    <!-- 主表格 = 按员工聚合的工资汇总（期间/部门/员工 筛选 + 生成/导出）
         筛选条件自定义（2026-10-14）：期间/部门/员工/状态迁入 filterDefs 条件定义，
         期间/部门的值经 f_period / f_deptName 通道，页面按 headerPeriod()/headerDeptId() 映射进汇总与核算单查询 -->
    <BillListPage
      page-class="wage-settlement-page"
      back-path="/finance"
      :title="$tt('工序工资核算')"
      badge="WAGE SETTLEMENT"
      sub-title="按已生效工序汇报单 · 归集计件工资与计时工资"
      :stats="stats"
      search-placeholder="输入员工姓名搜索"
      keyword-label="员工"
      :filter-defs="filterDefs"
      :detail-filter-defs="detailFilterDefs"
      :table-data="summaryRows"
      :loading="summaryLoading"
      :pagination="summaryPagination"
      storage-key="WageSettlementList"
      :columns="columns"
      :table-key="configVersion"
      :page-sizes="[20, 50, 100]"
      v-model:keyword="search.keyword"
      v-model:column-setting-visible="columnSettingVisible"
      @search="loadAll"
      @filter-params="onHeaderFilterParams"
      @reset="handleReset"
      @page-change="loadSummary"
    >

      <template #header-actions>
        <el-button type="primary" class="btn-primary-glow" @click="handleGenerate">
          <el-icon><Plus /></el-icon>{{ $tt('生成核算单') }}
        </el-button>
        <el-button type="warning" :icon="Download" @click="handleExport">{{ $tt('导出') }}</el-button>
        <el-button :icon="Refresh" @click="loadAll">{{ $tt('刷新') }}</el-button>
      </template>

      <el-table-column type="index" label="#" width="60" />
      <template v-for="col in visibleSortedColumns" :key="col.prop">
        <el-table-column
          :prop="col.prop"
          :label="$tf(col.label)"
          :width="col.width"
          :min-width="col.minWidth"
          :align="col.align || 'left'"
        >
          <template v-if="col.type" #default="{ row }">
            <span v-if="col.type === 'num4'">{{ formatNum(row[col.prop], 4) }}</span>
            <span v-else-if="col.type === 'num2'">{{ formatNum(row[col.prop], 2) }}</span>
            <span v-else-if="col.type === 'money'" class="net-amount">¥{{ formatNum(row[col.prop], 2) }}</span>
          </template>
        </el-table-column>
      </template>
    </BillListPage>

    <!-- 空数据中文引导 -->
    <el-alert
      v-if="!summaryLoading && summaryRows.length === 0"
      class="empty-tip"
      type="info"
      :closable="false"
      show-icon
      :title="$tt('该期间没有已生效的工序汇报数据')"
      :description="$tt('请先在「生产管理 → 工序汇报单」完成报工并提交生效，再回到本页查询或生成工资核算单。')"
    />

    <!-- 已生成工资核算单 -->
    <div class="content-card settlement-card">
      <div class="settlement-head">
        <div class="settlement-title">{{ $tt('已生成工资核算单') }}</div>
        <div class="settlement-actions">
          <el-select v-model="billSearch.status" clearable :placeholder="$tt('全部状态')" style="width: 140px" @change="loadBills">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
          <el-button type="primary" @click="loadBills">{{ $tt('查询') }}</el-button>
        </div>
      </div>

      <el-table :data="billRows" border v-loading="billLoading" class="tech-table">
        <el-table-column type="index" label="#" width="60" />
        <el-table-column :label="$tt('单号')" min-width="180">
          <template #default="{ row }">
            <span class="order-no-text link" @click="openDetail(row)">{{ row.settlementNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="period" :label="$tt('期间')" width="100" />
        <el-table-column :label="$tt('合格数量')" width="120" align="right">
          <template #default="{ row }">{{ formatNum(row.qualifiedQuantity, 4) }}</template>
        </el-table-column>
        <el-table-column :label="$tt('计件工资')" width="120" align="right">
          <template #default="{ row }">{{ formatNum(row.pieceAmount, 2) }}</template>
        </el-table-column>
        <el-table-column :label="$tt('计时工资')" width="120" align="right">
          <template #default="{ row }">{{ formatNum(row.timeAmount, 2) }}</template>
        </el-table-column>
        <el-table-column :label="$tt('合计')" width="130" align="right">
          <template #default="{ row }"><span class="net-amount">¥{{ formatNum(row.totalAmount, 2) }}</span></template>
        </el-table-column>
        <el-table-column prop="status" :label="$tt('状态')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small" effect="light">{{ $dt(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" :label="$tt('制单人')" width="110" />
        <el-table-column :label="$tt('制单时间')" width="170">
          <template #default="{ row }">{{ row.createdAt || '' }}</template>
        </el-table-column>
        <el-table-column :label="$tt('操作')" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === '草稿'" link type="primary" size="small" @click="handleSubmit(row)">{{ $tt('提交') }}</el-button>
            <el-button v-if="row.status === '已生效'" link type="danger" size="small" @click="handleVoid(row)">{{ $tt('作废') }}</el-button>
            <el-button v-if="row.status === '已作废'" link type="primary" size="small" @click="handleUnvoid(row)">{{ $tt('反作废') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <div class="pagination-info">{{ $tt('共') }} {{ billPagination.total }} {{ $tt('条记录') }}</div>
        <el-pagination
          v-model:current-page="billPagination.current"
          v-model:page-size="billPagination.size"
          :total="billPagination.total"
          :page-sizes="[10, 20, 50]"
          layout="prev, pager, next, sizes, jumper"
          @size-change="loadBills"
          @current-change="loadBills"
        />
      </div>
    </div>

    <!-- 明细弹窗 -->
    <el-dialog v-model="detailVisible" :title="$tf('工资核算明细')" width="1080px">
      <div v-if="detailHeader" class="detail-header">
        <span>{{ $tt('单号') }}：{{ detailHeader.settlementNo }}</span>
        <span>{{ $tt('期间') }}：{{ detailHeader.period }}</span>
        <span>{{ $tt('合计') }}：<b class="net-amount">¥{{ formatNum(detailHeader.totalAmount, 2) }}</b></span>
      </div>
      <el-table :data="detailItems" border max-height="440" class="tech-table">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="orderNo" :label="$tt('工单号')" min-width="150" />
        <el-table-column prop="operationCode" :label="$tt('工序编码')" width="120" />
        <el-table-column prop="operationName" :label="$tt('工序名称')" min-width="140" />
        <el-table-column prop="employeeName" :label="$tt('员工')" width="100" />
        <el-table-column :label="$tt('合格数量')" width="110" align="right">
          <template #default="{ row }">{{ formatNum(row.qualifiedQuantity, 4) }}</template>
        </el-table-column>
        <el-table-column :label="$tt('计件单价')" width="110" align="right">
          <template #default="{ row }">{{ formatNum(row.pieceRate, 4) }}</template>
        </el-table-column>
        <el-table-column :label="$tt('计件工资')" width="110" align="right">
          <template #default="{ row }">{{ formatNum(row.pieceAmount, 2) }}</template>
        </el-table-column>
        <el-table-column :label="$tt('工时')" width="100" align="right">
          <template #default="{ row }">{{ formatNum(row.workHours, 4) }}</template>
        </el-table-column>
        <el-table-column :label="$tt('工时费率')" width="110" align="right">
          <template #default="{ row }">{{ formatNum(row.hourlyRate, 4) }}</template>
        </el-table-column>
        <el-table-column :label="$tt('计时工资')" width="110" align="right">
          <template #default="{ row }">{{ formatNum(row.timeAmount, 2) }}</template>
        </el-table-column>
        <el-table-column :label="$tt('小计')" width="120" align="right">
          <template #default="{ row }"><span class="net-amount">¥{{ formatNum(row.amount, 2) }}</span></template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="detailVisible = false">{{ $tt('关闭') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Download, Refresh } from '@element-plus/icons-vue'
import {
  getWageSummary,
  getWageSettlementPage,
  getWageSettlementItems,
  getWageSettlementDetail,
  generateWageSettlement,
  submitWageSettlement,
  voidWageSettlement,
  unvoidWageSettlement
} from '@/api/finance/wageSettlement'
import BillListPage from '@/components/bill/BillListPage.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
// 筛选条件自定义（2026-10-14 全面推广）：条件定义共享工厂 + 明细报表 API（取明细模式专属条件元数据）
import { stdDefs, fld, detailFilterDefsOf } from '@/config/billFilterDefs'
import { createBillDetailReportApi } from '@/api/report/billDetailReport'
import { tt } from '@/i18n'

// 汇总列定义（列设置 + 动态渲染），每列都有值，不得空列
const columns = [
  { prop: 'period', label: '期间', width: 100 },
  { prop: 'deptName', label: '部门', minWidth: 120 },
  { prop: 'employeeName', label: '员工', minWidth: 120 },
  { prop: 'qualifiedQuantity', label: '合格数量', width: 120, align: 'right', type: 'num4' },
  { prop: 'pieceAmount', label: '计件工资', width: 120, align: 'right', type: 'money' },
  { prop: 'timeAmount', label: '计时工资', width: 120, align: 'right', type: 'money' },
  { prop: 'totalAmount', label: '合计', width: 140, align: 'right', type: 'money' }
]
const { columnSettingVisible, isColVisible, configVersion, sortedColumns } = useColumnSetting('WageSettlementList', columns)
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))

const statusOptions = [
  { label: '草稿', value: '草稿' },
  { label: '已生效', value: '已生效' },
  { label: '已作废', value: '已作废' }
]

const search = reactive({ keyword: '', period: '', employeeId: '' })
const billSearch = reactive({ status: '' })

// ============ 筛选条件自定义（2026-10-14 全面推广 · 按本页现状声明） ============
// 本页是「按员工聚合的汇总 + 已生成核算单列表」双表形态，无独立单据列表搜索栏，故此前的
// 期间/部门/员工扩展筛选自本批起迁入条件定义（声明 filterDefs 后扩展搜索槽不再渲染）：
//   状态（核算单）/ 制单时间区间 / 员工 = 标准维度（std → 值走页面 search 通道）；
//   核算期间 / 部门 = 表头字段（f_period / f_deptName 通道），由 headerPeriod()/headerDeptId()
//   映射进汇总查询（期间+部门）与核算单查询（期间，部门走后端白名单）。
// 键与「工序工资核算单明细报表」引擎筛选键同键 → 汇总/明细两种模式一套条件。
const filterDefs = computed(() => [
  ...stdDefs({
    status: { options: statusOptions },
    date: { key: 'createdAt', label: '制单时间' },
    employee: { key: 'employeeName', label: '员工' }
  }),
  fld('text', 'settlementNo', '核算单号'),
  fld('text', 'period', '核算期间'),
  fld('select', 'deptName', '部门', 'department'),
  fld('numberRange', 'qualifiedQuantity', '合格数量'),
  fld('numberRange', 'pieceAmount', '计件工资'),
  fld('numberRange', 'timeAmount', '计时工资'),
  fld('numberRange', 'totalAmount', '工资合计'),
  fld('text', 'createdBy', '制单人'),
  fld('text', 'remark', '备注')
])

// 明细模式专属条件 = 工序工资核算明细报表 /columns 的 filters 元数据（键=引擎同键）；
// STD_BY_KEY 声明引擎筛选键 → 标准维度（默认勾选=现状、值走页面 search 通道）
const detailFiltersMeta = ref([])
const STD_BY_KEY = { status: 'status', createdAt: 'date', employeeName: 'employee' }
const detailFilterDefs = computed(() => detailFilterDefsOf(detailFiltersMeta.value, STD_BY_KEY))
// 明细报表 API（与列表页明细模式双视图同一基础路径）
const wageSettlementDetailApi = createBillDetailReportApi('/report/finance/wage-settlement-detail')

/** 自定义条件参数（由通用壳经 filter-params 下发）：params 汇总用 / detailParams 明细用 */
const headerFilterState = ref({ params: {}, customSearch: undefined, detailParams: {} })
function onHeaderFilterParams(payload) {
  headerFilterState.value = payload || { params: {}, customSearch: undefined, detailParams: {} }
}

/** 核算期间取值：条件定义里的「核算期间」优先，未填回落到页面默认期间（进入页面 = 当前月） */
function headerPeriod() {
  const p = (headerFilterState.value.params || {}).f_period
  return (p && String(p).trim()) || search.period
}

/** 部门取值：条件定义里的「部门」（值 = 部门ID），供汇总查询使用 */
function headerDeptId() {
  return (headerFilterState.value.params || {}).f_deptName || undefined
}

const summaryRows = ref([])
const summaryLoading = ref(false)
// 汇总为聚合行、后端不分页；壳分页控件仅展示条数
const summaryPagination = reactive({ current: 1, size: 20, total: 0 })

const billRows = ref([])
const billLoading = ref(false)
const billPagination = reactive({ current: 1, size: 10, total: 0 })

const detailVisible = ref(false)
const detailHeader = ref(null)
const detailItems = ref([])

const stats = computed(() => {
  const rows = summaryRows.value
  const totalPiece = rows.reduce((s, r) => s + Number(r.pieceAmount || 0), 0)
  const totalTime = rows.reduce((s, r) => s + Number(r.timeAmount || 0), 0)
  const totalAmount = rows.reduce((s, r) => s + Number(r.totalAmount || 0), 0)
  return [
    { label: '核算员工数', value: rows.length, type: 'primary', icon: 'User' },
    { label: '计件工资合计', value: `¥${formatNum(totalPiece, 2)}`, type: 'info', icon: 'Coin' },
    { label: '计时工资合计', value: `¥${formatNum(totalTime, 2)}`, type: 'warning', icon: 'Clock' },
    { label: '工资合计', value: `¥${formatNum(totalAmount, 2)}`, type: 'success', icon: 'Money' }
  ]
})

function formatNum(val, decimals = 2) {
  return Number(val || 0).toFixed(decimals)
}

function statusTagType(s) {
  const map = { '草稿': 'info', '已生效': 'success', '已作废': 'danger' }
  return map[s] || 'info'
}

function currentMonth() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

async function loadSummary() {
  if (!search.period) {
    ElMessage.warning(tt('请选择核算期间'))
    return
  }
  summaryLoading.value = true
  try {
    const res = await getWageSummary({
      period: headerPeriod(),
      deptId: headerDeptId(),
      employeeId: search.employeeId || undefined
    })
    let rows = res || []
    const kw = (search.keyword || '').trim()
    if (kw) rows = rows.filter(r => (r.employeeName || '').includes(kw))
    summaryRows.value = rows
    summaryPagination.total = rows.length
    summaryPagination.current = 1
  } catch (e) {
    ElMessage.error(e.message || tt('加载工资汇总失败'))
  } finally {
    summaryLoading.value = false
  }
}

async function loadBills() {
  billLoading.value = true
  try {
    const res = await getWageSettlementPage({
      current: billPagination.current,
      size: billPagination.size,
      period: headerPeriod(),
      // 状态：区块内下拉（billSearch）优先，条件定义里的「单据状态」（std → search.status）兜底
      status: billSearch.status || search.status || undefined,
      // 筛选条件自定义（2026-10-14）：f_* 表头字段过滤参数由通用壳统一下发（后端白名单翻译）
      ...(headerFilterState.value.params || {})
    })
    billRows.value = res.records || []
    billPagination.total = res.total || 0
  } catch (e) {
    ElMessage.error(e.message || tt('加载工资核算单失败'))
  } finally {
    billLoading.value = false
  }
}

function handleReset() {
  search.period = currentMonth()
  search.employeeId = ''
  search.keyword = ''
  billSearch.status = ''
  summaryPagination.current = 1
  billPagination.current = 1
  // 条件定义里的非标准条件值（期间/部门/金额区间等）由通用壳在重置时统一清空并下发空参数
  loadSummary()
  loadBills()
}

function loadAll() {
  loadSummary()
  loadBills()
}

async function handleGenerate() {
  if (!search.period) {
    ElMessage.warning(tt('请先选择核算期间'))
    return
  }
  try {
    await ElMessageBox.confirm(
      `${tt('确认对 ')}${headerPeriod()}${tt(' 期间已生效的工序汇报数据生成工资核算单？（生成后为草稿状态）')}`,
      tt('生成工资核算单'),
      { type: 'warning', confirmButtonText: tt('确认生成'), cancelButtonText: tt('取消') }
    )
  } catch (e) {
    return
  }
  try {
    const res = await generateWageSettlement({
      period: headerPeriod(),
      deptId: headerDeptId(),
      employeeId: search.employeeId || undefined
    })
    ElMessage.success(`${tt('工资核算单已生成：')}${res.settlementNo || ''}`)
    loadAll()
  } catch (e) {
    ElMessage.error(e.message || tt('生成工资核算单失败'))
  }
}

async function handleSubmit(row) {
  try {
    await ElMessageBox.confirm(`${tt('确认提交工资核算单 ')}${row.settlementNo}${tt(' ？提交后即生效。')}`, tt('提交确认'), { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await submitWageSettlement(row.settlementId)
    ElMessage.success(tt('提交成功'))
    loadBills()
  } catch (e) {
    ElMessage.error(e.message || tt('提交失败'))
  }
}

async function handleVoid(row) {
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt(`${tt('请输入作废原因（单号 ')}${row.settlementNo}${tt('）')}`, tt('作废确认'), {
      type: 'warning',
      inputPlaceholder: tt('作废原因'),
      confirmButtonText: tt('确认作废'),
      cancelButtonText: tt('取消')
    })
    reason = value || ''
  } catch (e) {
    return
  }
  try {
    await voidWageSettlement(row.settlementId, reason)
    ElMessage.success(tt('已作废'))
    loadBills()
  } catch (e) {
    ElMessage.error(e.message || tt('作废失败'))
  }
}

async function handleUnvoid(row) {
  try {
    await ElMessageBox.confirm(`${tt('确认反作废工资核算单 ')}${row.settlementNo}${tt(' ？反作废后恢复为已生效。')}`, tt('反作废确认'), { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await unvoidWageSettlement(row.settlementId)
    ElMessage.success(tt('已反作废'))
    loadBills()
  } catch (e) {
    ElMessage.error(e.message || tt('反作废失败'))
  }
}

async function openDetail(row) {
  try {
    const [header, items] = await Promise.all([
      getWageSettlementDetail(row.settlementId),
      getWageSettlementItems(row.settlementId)
    ])
    detailHeader.value = header || row
    detailItems.value = items || []
    detailVisible.value = true
  } catch (e) {
    ElMessage.error(e.message || tt('加载明细失败'))
  }
}

function handleExport() {
  if (!summaryRows.value.length) {
    ElMessage.warning(tt('没有可导出的汇总数据'))
    return
  }
  const headers = ['期间', '部门', '员工', '合格数量', '计件工资', '计时工资', '合计']
  const rows = summaryRows.value.map(r => [
    r.period || '',
    r.deptName || '',
    r.employeeName || '',
    formatNum(r.qualifiedQuantity, 4),
    formatNum(r.pieceAmount, 2),
    formatNum(r.timeAmount, 2),
    formatNum(r.totalAmount, 2)
  ].join(','))
  const totalRow = [
    '合计', '', `${summaryRows.value.length} 人`,
    formatNum(summaryRows.value.reduce((s, r) => s + Number(r.qualifiedQuantity || 0), 0), 4),
    formatNum(summaryRows.value.reduce((s, r) => s + Number(r.pieceAmount || 0), 0), 2),
    formatNum(summaryRows.value.reduce((s, r) => s + Number(r.timeAmount || 0), 0), 2),
    formatNum(summaryRows.value.reduce((s, r) => s + Number(r.totalAmount || 0), 0), 2)
  ].join(',')
  const bom = '\uFEFF'
  const csvContent = bom + [headers.join(','), ...rows, totalRow].join('\n')
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `工序工资核算_${search.period}.csv`
  link.click()
  URL.revokeObjectURL(link.href)
  ElMessage.success(tt('导出成功'))
}

onMounted(() => {
  search.period = currentMonth()
  loadSummary()
  loadBills()
  // 明细模式专属筛选条件元数据（明细报表 /columns 的 filters；失败静默：明细条件集为空时明细模式沿用汇总条件集）
  wageSettlementDetailApi.getColumns()
    .then(res => { detailFiltersMeta.value = (res && res.filters) || [] })
    .catch(() => { /* 静默降级 */ })
})
useAutoRefresh(() => {
  loadSummary()
  loadBills()
})
</script>

<style scoped lang="scss">
.wage-settlement-root {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.empty-tip {
  border-radius: 10px;
}

.settlement-card {
  padding: 0;
}

.settlement-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #f3f4f6;

  .settlement-title {
    font-size: 15px;
    font-weight: 600;
    color: #1f2937;
  }

  .settlement-actions {
    display: flex;
    gap: 8px;
    align-items: center;
  }
}

.tech-table {
  :deep(.el-table__header th) {
    background: rgba(99, 102, 241, 0.04) !important;
  }
}

.order-no-text {
  color: #6366f1;
  font-weight: 500;
  font-family: 'SF Mono', Monaco, Consolas, monospace;

  &.link {
    cursor: pointer;
    &:hover { color: #8b5cf6; }
  }
}

.net-amount {
  color: #6366f1;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
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

.detail-header {
  display: flex;
  gap: 24px;
  padding: 0 4px 12px;
  font-size: 13px;
  color: #374151;
}
</style>
