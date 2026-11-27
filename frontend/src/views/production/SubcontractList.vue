<template>
  <!-- 通用列表壳（页头/统计卡/搜索条/表格外壳/分页/打印/列设置）
       状态=「单据状态」下拉（数字状态 → 字符串 value，原因见 statusOptions 注释）；日期=委外日期区间；供应商=委外供应商；状态页签已删除 -->
  <BillListPage
    page-class="subcontract-page"
    back-path="/production"
    :title="$tt('委外加工单')"
    badge="SUBCONTRACT"
    sub-title="管理委外加工业务 · 追踪外协生产进度"
    create-path="/subcontract/orders/new"
    :stats="stats"
    :active-stat-key="activeStatKey"
    search-placeholder="输入委外单号/供应商搜索"
    keyword-label="单据编号"
    date-label="委外日期"
    :filters="['status', 'date', 'supplier']"
    :status-options="statusOptions"
    :filter-model="search"
    :filter-defs="filterDefs"
    :detail-filter-defs="detailFilterDefs"
    :table-data="tableData"
    :loading="loading"
    :pagination="pagination"
    storage-key="SubcontractList"
    :columns="columns"
    :table-key="configVersion"
    bill-type="SUBCONTRACT_ORDER"
    :page-sizes="[10, 20, 50, 100]"
    v-model:keyword="search.keyword"
    v-model:selected-rows="selectedRows"
    :batch-actions="batchActions"
    v-model:column-setting-visible="columnSettingVisible"
    v-model:print-visible="printVisible"
    v-model:print-bill-id="printBillId"
    @search="handleSearch"
    @reset="handleReset"
    @filter-change="onFilterChange"
    @filter-params="onHeaderFilterParams"
    @stat-click="onStatClick"
    @sort-change="onSortChange"
    @page-change="fetchData"
  >
    <template #header-actions="{ selectedRows: sel }">
      <!-- 页头按钮统一走通用组件（2026-09-19）：按钮码由 perm-prefix 派生，权限控制一处生效 -->
      <BillHeaderActions
        perm-prefix="subcontract:orders"
        :add-text="$tt('新增委外单')"
        :show-import="false"
        :print-disabled="!sel.length"
        @add="$router.push('/subcontract/orders/new')"
        @print="handleBatchPrint(sel)"
        @export="handleExport"
      />
    </template>

    <el-table-column type="selection" width="50" />
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
              <div v-if="col.type === 'billNo'" class="order-no-cell">
                <span class="order-no-text" @click="$router.push(`/subcontract/orders/${row.id}`)">{{ row.billNo }}</span>
                <el-icon class="link-icon"><Link /></el-icon>
              </div>
              <span v-else-if="col.type === 'amount2'" class="amount-text">{{ Number(row[col.prop] || 0).toFixed(2) }}</span>
              <div v-else-if="col.type === 'status'" class="status-cell">
                <span :class="['status-dot', statusDotClass(row.status)]"></span>
                <el-tag size="small" :type="statusType(row.status)" effect="light" class="status-tag">{{ $dt(statusText(row.status)) }}</el-tag>
              </div>
            </template>
          </el-table-column>
        </template>
        <AuditColumns />
  </BillListPage>

  <!-- 委外发料清单（子表）列设置抽屉：page-key=SubcontractList_lines，与列表壳自带的 SubcontractList 抽屉不是同一个，故保留 -->
  <ColumnSettingDrawer v-model="detailColVisible" :page-key="'SubcontractList_lines'" :columns="detailColumns" :title="$tt('委外发料清单列设置')" />
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Download, Link } from '@element-plus/icons-vue'
import {
  listSubcontract, deleteSubcontract, approveSubcontract,
  releaseSubcontract, completeSubcontract, closeSubcontract,
  pushPick, pushInbound, getSubcontractOrderDashboard
} from '@/api/production/subcontract'
import BillListPage from '@/components/bill/BillListPage.vue'
import BillHeaderActions from '@/components/bill/BillHeaderActions.vue'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
import AuditColumns from '@/components/AuditColumns.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { exportListAsSeen, columnsToExportFields } from '@/utils/exportUtil'
// 筛选条件自定义（2026-10-14 全面推广）：条件定义共享工厂 + 明细报表 API（取明细模式专属条件元数据）
import { stdDefs, fld, detailFilterDefsOf, cfDefs } from '@/config/billFilterDefs'
import { useCustomFieldSearch } from '@/composables/useCustomFieldSearch'
import { createBillDetailReportApi } from '@/api/report/billDetailReport'

import { useTableSort } from '@/composables/useTableSort'
import { tt } from '@/i18n'
const { handleSortChange, getSortParams } = useTableSort()

/**
 * 批量操作声明（2026-09-20）：UI/确认/执行/结果面板全部由通用壳承担，这里只声明"怎么执行"。
 * 命名口径全系统统一：批量审批 / 批量删除 / 批量导出。
 * 本单据后端无提交入口（只有审核）→ 按立项登记表例外清单**不声明「批量提交」**。
 * 本页状态为数字：0 草稿 / 1 待审核 / 2 已生效 / 3 已下达 / 5 已完工 / 6 已结案 / 7 已作废。
 */
const batchActions = [
  {
    key: 'approve',
    label: tt('批量审批'),
    perm: 'subcontract:orders:audit',
    exec: (row) => approveSubcontract(row.id),
    skipReason: (row) => (row.status !== 1
      ? tt('当前状态：') + statusText(row.status) + tt('，不是待审核，无需审批') : '')
  },
  {
    key: 'delete',
    label: tt('批量删除'),
    type: 'danger',
    perm: 'subcontract:orders:delete',
    exec: (row) => deleteSubcontract(row.id),
    disabledReason: (rows) => (rows.some(r => r.status !== 0)
      ? tt('非草稿不能删除，请先重置草稿') : '')
  },
  {
    key: 'export',
    label: tt('批量导出'),
    perm: 'subcontract:orders:export',
    entity: 'subcontract',
    idOf: (row) => row.id,
    // 勾选导出同样按"页面当前可见列"（2026-09-24 全站口径）
    fields: () => columnsToExportFields(visibleSortedColumns.value)
  }
]

const onSortChange = (e) => { handleSortChange(e); fetchData() }
// 列定义（用于列设置抽屉+动态渲染）：type 标记单元格渲染分支
// billNo 单号链接、amount2 金额两位小数、status 状态标签，其余纯文本
const columns = [
  { prop: 'billNo', label: '委外单号', minWidth: 150, sortable: true, type: 'billNo' },
  { prop: 'orderDate', label: '委外日期', width: 120, sortable: true },
  { prop: 'materialCode', label: '物料编码', minWidth: 120, sortable: true },
  { prop: 'materialName', label: '物料名称', minWidth: 160, sortable: true },
  { prop: 'qty', label: '委外数量', width: 110, align: 'right', sortable: true },
  { prop: 'supplierName', label: '委外供应商', minWidth: 160, sortable: true },
  { prop: 'processNo', label: '委外工序号', width: 100, sortable: true },
  { prop: 'unitPrice', label: '加工单价', width: 110, align: 'right', sortable: true },
  { prop: 'totalPrice', label: '加工费总额', width: 120, align: 'right', sortable: true, type: 'amount2' },
  { prop: 'status', label: '状态', width: 110, sortable: true, type: 'status' }
]
const { columnSettingVisible, openColumnSetting, isColVisible, getColFixed, configVersion, sortedColumns } = useColumnSetting('SubcontractList', columns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))

// 委外发料清单列设置（子表）：抽屉保留 —— 它的 page-key 是 SubcontractList_lines，
// 与列表壳自带的 SubcontractList 抽屉不是同一个，不存在重复入口
const detailColumns = [
  { prop: 'materialName', label: '物料名称', minWidth: 160 },
  { prop: 'unitName', label: '单位', width: 90 },
  { prop: 'baseQty', label: '应发数量', width: 110 },
  { prop: 'actualQty', label: '实发数量', width: 110 },
  { prop: 'supplyType', label: '供应方式', width: 110 },
  { prop: 'remark', label: '备注', minWidth: 120 }
]
const {
  columnSettingVisible: detailColVisible,
  openColumnSetting: openDetailColSetting,
  isColVisible: isDetailColVisible
} = useColumnSetting('SubcontractList_lines', detailColumns)

const loading = ref(false)
const tableData = ref([])
// 统一入参名：keyword / status / startDate / endDate / supplierId
const search = reactive({ keyword: '', status: '', startDate: '', endDate: '', supplierId: '' })
const cf = useCustomFieldSearch('subcontract_order')
const pagination = reactive({ current: 1, size: 20, total: 0 })
const selectedRows = ref([])
const printVisible = ref(false)
const printBillId = ref(null)

// 页头「打印」：一次只能打印一张（与销售订单口径一致）
function handleBatchPrint(rows) {
  const list = (rows && rows.length ? rows : selectedRows.value) || []
  if (!list.length) { ElMessage.warning(tt('请先勾选要打印的单据')); return }
  if (list.length > 1) { ElMessage.warning(tt('一次只能打印一张单据，请仅勾选一行')); return }
  printBillId.value = list[0].id
  printVisible.value = true
}
// 统计卡片高亮（点卡片筛选列表时由 onStatClick 控制）
const activeStatKey = ref('')
// 单据状态下拉选项：后端状态入参是 Integer，但下拉 value 必须用「非空字符串」——
// 通用搜索栏以 `!v` 判空，传数字 0 会被当成"未选择"从而清空筛选；传 '0' 时 HTTP 请求上仍是 status=0
// 取值来自后端状态机真实写入值：0 草稿 / 1 待审核 / 2 已生效 / 3 已下达 / 5 已完工 / 6 已结案 / 7 已作废
// 4（原页签的"执行中"）后端从未写入过，属"永远 0 条"的假选项，已从下拉剔除
const statusOptions = [
  { label: '草稿', value: '0' },
  { label: '待审核', value: '1' },
  { label: '已生效', value: '2' },
  { label: '已下达', value: '3' },
  { label: '已完工', value: '5' },
  { label: '已结案', value: '6' },
  { label: '已作废', value: '7' }
]

// ============ 筛选条件自定义（2026-10-14 委外模块推广批次） ============
// 条件候选 = 标准维度（状态/委外日期/委外供应商，std 标记 → 值走页面 search 通道，与统计卡筛选互通）
//          + 本单表头字段（f_<key> 参数化，后端白名单翻译）。
// 键与「委外加工单明细报表」引擎筛选键同键 → 汇总/明细两种模式一套条件参数。
// std 条件 = 现状默认勾选：没保存过筛选设置时查询区与改版前完全一致（守则 2.7.2-6）。
const filterDefs = computed(() => [
  ...stdDefs({
    status: { options: statusOptions },
    date: { key: 'orderDate', label: '委外日期' },
    supplier: { key: 'supplierName', label: '委外供应商' }
  }),
  fld('text', 'orderNo', '委外单号'),
  fld('text', 'processItem', '委外工序号'),
  fld('text', 'materialCode', '物料编码'),
  fld('text', 'materialName', '物料名称'),
  fld('dateRange', 'expectDate', '计划开工日期'),
  fld('dateRange', 'actualDate', '计划完工日期'),
  fld('numberRange', 'totalQuantity', '委外数量'),
  fld('numberRange', 'finishedQuantity', '已完工数量'),
  fld('numberRange', 'unitPrice', '加工单价'),
  fld('numberRange', 'totalAmount', '加工费总额'),
  fld('numberRange', 'taxRate', '税率%'),
  fld('numberRange', 'taxAmount', '税额'),
  fld('numberRange', 'netAmount', '价税合计'),
  fld('text', 'currencyCode', '币种'),
  fld('numberRange', 'exchangeRate', '汇率'),
  fld('text', 'sourceBillType', '来源单据类型'),
  fld('text', 'sourceBillNo', '来源单号'),
  fld('text', 'remark', '备注'),
  ...cfDefs(cf)
])

// 明细模式专属筛选条件（来源 = 明细报表 /columns 的 filters 元数据，键与引擎同键；
// 默认勾选 = 现状三件套：状态/委外日期/供应商，明细行字段默认不勾。
// 状态选项页内覆写：引擎静态枚举是通用中文五态，委外状态是数字状态机（0~7），选项必须用本页 statusOptions）
const detailFiltersMeta = ref([])
const STD_BY_KEY = { status: 'status', orderDate: 'date', supplierName: 'supplier' }
const detailFilterDefs = computed(() => detailFilterDefsOf(detailFiltersMeta.value, STD_BY_KEY)
  .map(d => (d.key === 'status' ? { ...d, options: statusOptions } : d)))
// 明细报表 API（与列表页明细模式双视图同一基础路径）
const subcontractOrderDetailApi = createBillDetailReportApi('/report/subcontract/order-detail')

/** 自定义条件参数（由通用壳经 filter-params 下发）：params 汇总用 */
const headerFilterState = ref({ params: {} })
function onHeaderFilterParams(payload) {
  headerFilterState.value = payload || { params: {} }
}

// 顶部统计卡片：原 4 张卡全部写死 '0'，改为后端 /api/subcontract/orders/dashboard（与列表同数据权限口径）
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
  { key: 'month', label: '本月委外单', value: 0, type: 'primary', icon: 'Document', filter: monthRange() },
  { key: 'draft', label: '草稿', value: 0, type: 'warning', icon: 'EditPen', filter: { status: '0' } },
  { key: 'released', label: '已下达', value: 0, type: 'info', icon: 'Clock', filter: { status: '3' } },
  { key: 'completed', label: '已完工', value: 0, type: 'success', icon: 'CircleCheck', filter: { status: '5' } }
])

async function loadStats() {
  try {
    const d = (await getSubcontractOrderDashboard()) || {}
    stats.value = [
      { key: 'month', label: '本月委外单', value: d.monthOrders ?? 0, type: 'primary', icon: 'Document', filter: monthRange() },
      { key: 'draft', label: '草稿', value: d.draftOrders ?? 0, type: 'warning', icon: 'EditPen', filter: { status: '0' } },
      { key: 'released', label: '已下达', value: d.releasedOrders ?? 0, type: 'info', icon: 'Clock', filter: { status: '3' } },
      { key: 'completed', label: '已完工', value: d.completedOrders ?? 0, type: 'success', icon: 'CircleCheck', filter: { status: '5' } }
    ]
  } catch (e) {
    // 静默降级：取数失败时卡片保持原值，不弹诊断卡、不阻断列表
  }
}

const statusType = (s) => ({
  0: 'info', 1: 'warning', 2: 'primary',
  3: 'warning', 4: 'primary',
  5: 'success', 6: 'info', 7: 'danger'
}[s] || 'info')

const statusText = (s) => ({
  0: '草稿', 1: '待审核', 2: '已生效',
  3: '已下达', 4: '执行中',
  5: '已完工', 6: '已结案', 7: '已作废'
}[s] || String(s))

function statusDotClass(s) {
  const n = Number(s)
  return {
    0: 'dot-info', 1: 'dot-warning', 2: 'dot-primary',
    3: 'dot-warning', 4: 'dot-primary',
    5: 'dot-success', 6: 'dot-info', 7: 'dot-danger'
  }[n] || 'dot-info'
}

// 卡片点击 → 应用该卡筛选条件（状态/日期/供应商先清空再套用，避免上一张卡残留）；再点同一张卡 = 取消
function onStatClick({ key, filter }) {
  if (activeStatKey.value === key) {
    activeStatKey.value = ''
    Object.assign(search, { status: '', startDate: '', endDate: '', supplierId: '' })
  } else {
    activeStatKey.value = key
    Object.assign(search, { status: '', startDate: '', endDate: '', supplierId: '' }, filter || {})
  }
  pagination.current = 1
  fetchData()
}

// 通用搜索栏（状态/日期/供应商）变更：先把值写回本页 search
// 注：BillSearchBar 变更后会紧接着抛 search 事件（壳已转发 → handleSearch），这里不重复发起查询
function onFilterChange(patch) {
  Object.assign(search, patch || {})
}

async function handleExport() {
  try {
    // 页面上看到什么列，导出就带什么列；导出行 = 当前筛选条件下的全部
    // （原先注释里提到的 poolType 是客户公海/私海专用，本页不涉及，故不再传第二个参数）
    await exportListAsSeen({
      entityName: 'subcontract',
      columns: visibleSortedColumns.value,
      fetchPage: listSubcontract,
      params: buildQueryParams(),
      idField: 'id'
    })
    ElMessage.success(tt('导出成功'))
  } catch (e) {
    // 失败原因由 exportUtil 统一提示
  }
}

// 状态为已下达(3)或执行中(4)
function isReleasedOrRunning(s) {
  const n = Number(s)
  return n === 3 || n === 4
}

/**
 * 列表与导出**共用**的查询条件（2026-09-24 全站口径）：
 * 导出必须与列表用同一套条件，抽成一个函数避免两处各写一份导致口径漂移
 * （用户要求"页面上看到什么列，导出就带什么列；导出行 = 当前筛选条件下的全部"）。
 */
function buildQueryParams() {
  return {
    // 统一入参名（对应后端 keyword/status/startDate/endDate/supplierId）
    keyword: search.keyword || undefined,
    status: search.status || undefined,
    startDate: search.startDate || undefined,
    endDate: search.endDate || undefined,
    supplierId: search.supplierId || undefined,
    // 筛选条件自定义（2026-10-14）：f_* 表头字段过滤参数由通用壳统一下发
    ...(headerFilterState.value.params || {}),
    // 单据自定义字段（勾选「可搜索」）→ customSearch 透传后端
    ...(headerFilterState.value.customSearch ? { customSearch: headerFilterState.value.customSearch } : {}),
    ...getSortParams()
  }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await listSubcontract({ current: pagination.current, size: pagination.size, ...buildQueryParams() })
    // 后端字段映射到前端表格显示字段
    tableData.value = (res.records || []).map(r => ({
      ...r,
      id: r.id,
      billNo: r.orderNo || r.billNo || '',
      qty: r.totalQuantity != null ? r.totalQuantity : r.qty,
      totalPrice: r.totalAmount != null ? r.totalAmount : r.totalPrice,
      processNo: r.processItem || r.processNo || '',
      planStartDate: r.expectDate || r.planStartDate || '',
      planFinishDate: r.actualDate || r.planFinishDate || ''
    }))
    pagination.total = res.total || 0
  } catch (e) { /* 拦截器已提示 */ } finally {
    loading.value = false
  }
}

function handleSearch() { pagination.current = 1; fetchData() }
function handleReset() {
  // 重置 = 回到未筛选状态：关键词 + 通用搜索栏（状态/日期/供应商）一并清空
  Object.assign(search, { keyword: '', status: '', startDate: '', endDate: '', supplierId: '' })
  activeStatKey.value = ''
  pagination.current = 1
  fetchData()
}

async function onRelease(row) {
  await ElMessageBox.confirm(`${tt("确认下达委外单 ")}${row.billNo}${tt("？下达后不可修改")}`, tt('确认'), { type: 'warning' })
  try {
    await releaseSubcontract(row.id)
    ElMessage.success(tt('已下达'))
    fetchData()
  } catch (e) { /* 拦截器已提示 */ }
}

async function onComplete(row) {
  await ElMessageBox.confirm(`${tt("确认完工委外单 ")}${row.billNo}？`, tt('确认'), { type: 'warning' })
  try {
    await completeSubcontract(row.id)
    ElMessage.success(tt('已完工'))
    fetchData()
  } catch (e) { /* 拦截器已提示 */ }
}

async function onClose(row) {
  await ElMessageBox.confirm(`${tt("确认关闭委外单 ")}${row.billNo}？`, tt('确认'), { type: 'warning' })
  try {
    await closeSubcontract(row.id)
    ElMessage.success(tt('已关闭'))
    fetchData()
  } catch (e) { /* 拦截器已提示 */ }
}

// 下推发料单
async function onPushPick(row) {
  await ElMessageBox.confirm(`${tt("确认将委外单 ")}${row.billNo}${tt(" 下推生成发料单？")}`, tt('确认'), { type: 'warning' })
  try {
    await pushPick(row.id)
    ElMessage.success(tt('已下推发料单'))
    fetchData()
  } catch (e) { /* 拦截器已提示 */ }
}

// 下推入库单
async function onPushInbound(row) {
  await ElMessageBox.confirm(`${tt("确认将委外单 ")}${row.billNo}${tt(" 下推生成入库单？")}`, tt('确认'), { type: 'warning' })
  try {
    await pushInbound(row.id)
    ElMessage.success(tt('已下推入库单'))
    fetchData()
  } catch (e) { /* 拦截器已提示 */ }
}

async function onDelete(row) {
  await ElMessageBox.confirm(`${tt("确认删除委外单 ")}${row.billNo}？`, tt('确认'), { type: 'warning' })
  try {
    await deleteSubcontract(row.id)
    ElMessage.success(tt('删除成功'))
    fetchData()
  } catch (e) { /* 拦截器已提示 */ }
}

onMounted(async () => {
  cf.load()
  loadStats()
  fetchData()
  // 明细模式专属筛选条件元数据（明细报表 /columns 的 filters；失败静默：明细条件集为空时明细模式沿用汇总条件集）
  try {
    const res = await subcontractOrderDetailApi.getColumns()
    detailFiltersMeta.value = (res && res.filters) || []
  } catch (e) { /* 静默降级 */ }
})
useAutoRefresh(() => { fetchData(); loadStats() })
</script>

<style scoped lang="scss">
.subcontract-page {
  padding: 20px 24px;
  background: linear-gradient(135deg, #f8faff 0%, #f0f4ff 50%, #f8fcff 100%);
  min-height: calc(100vh - 60px);
}
.detail-subtable-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 16px 0 8px;
  h4 {
    margin: 0;
    font-size: 14px;
    font-weight: bold;
    color: #303133;
  }
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

.btn-primary-glow {
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  border: none;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
  &:hover {
    box-shadow: 0 6px 20px rgba(99, 102, 241, 0.4);
    transform: translateY(-1px);
  }
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

  .search-right {
    display: flex;
    gap: 8px;
    align-items: center;
  }

  .search-input {
    width: 280px;
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

.order-no-cell {
  display: flex;
  align-items: center;
  gap: 6px;

  .order-no-text {
    color: #6366f1;
    font-weight: 500;
    cursor: pointer;
    &:hover { color: #8b5cf6; }
  }

  .link-icon {
    color: #c7d2fe;
    font-size: 12px;
    opacity: 0;
    transition: opacity .2s;
  }
  &:hover .link-icon { opacity: 1; }
}

.status-cell {
  display: flex;
  align-items: center;
  gap: 6px;

  .status-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;

    &.dot-info { background: #909399; }
    &.dot-warning { background: #e6a23c; }
    &.dot-primary { background: #409eff; }
    &.dot-success { background: #67c23a; }
    &.dot-danger { background: #f56c6c; }
  }

  .status-tag {
    border: none;
    --el-tag-bg-color: transparent;
  }
}

.amount-text {
  font-weight: 600;
  color: #1f2937;
  font-family: 'SF Mono', Monaco, 'Courier New', monospace;
}

.action-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

.pagination-wrap {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-top: 1px solid #f3f4f6;

  .pagination-info {
    font-size: 13px;
    color: #9ca3af;
  }
}

.picker-bar {
  display: flex; align-items: center; gap: 12px;
  padding: 10px 12px; margin-bottom: 12px;
  background: #f4f8ff; border: 1px dashed #409eff; border-radius: 4px;
  .picker-hint { color: #909399; font-size: 12px; }
  .picker-source { color: #409eff; font-size: 12px; font-weight: 600; }
}
</style>
