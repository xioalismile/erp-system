<template>
  <!-- 通用列表壳（页头/统计卡/搜索条/表格外壳/分页/打印/列设置）
       状态=「单据状态」下拉（数字状态 → 字符串 value，原因见 statusOptions 注释）；日期=单据日期区间；状态页签已删除 -->
  <BillListPage
    page-class="disassemble-list-page"
    back-path="/production"
    :title="$tt('生产拆卸单')"
    badge="DISASSEMBLE"
    sub-title="管理产品拆卸业务 · 回收原材料资源"
    create-path="/production/disassemble/new"
    :stats="stats"
    :active-stat-key="activeStatKey"
    search-placeholder="输入单据编号搜索"
    keyword-label="单据编号"
    :filters="['status', 'date']"
    :status-options="statusOptions"
    :filter-defs="filterDefs"
    :detail-filter-defs="detailFilterDefs"
    :filter-model="search"
    :table-data="tableData"
    :loading="loading"
    :pagination="pagination"
    storage-key="ProduceDisassembleList"
    :columns="columns"
    :table-key="configVersion"
    bill-type="PRODUCE_DISASSEMBLE"
    :page-sizes="[20, 50, 100]"
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
    <!-- 扩展搜索项：成品名称（走明细表，原能力保留）；
         自定义字段搜索已并入「筛选条件自定义」候选（cfDefs + customSearch 通道） -->
    <template #search-extra>
      <div class="erp-search-item">
        <span class="label">{{ $tt('成品名称') }}:</span>
        <el-input v-model="productName" :placeholder="$tt('请输入成品名称')" clearable style="width:180px" @keyup.enter="handleSearch" />
      </div>
    </template>

    <template #header-actions="{ selectedRows: sel }">
      <!-- 页头按钮统一走通用组件（2026-09-19）：按钮码由 perm-prefix 派生，权限控制一处生效 -->
      <BillHeaderActions
        perm-prefix="production:disassemble"
        :add-text="$tt('新增拆卸单')"
        @import="handleImport"
        :print-disabled="!sel.length"
        @add="$router.push('/production/disassemble/new')"
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
                <span class="order-no-text" @click="$router.push(`/production/disassemble/${row.id}`)">{{ row.serial_no }}</span>
                <el-icon class="link-icon"><Link /></el-icon>
              </div>
              <span v-else-if="col.type === 'amount2'" class="amount-text">{{ Number(row[col.prop] || 0).toFixed(2) }}</span>
              <div v-else-if="col.type === 'status'" class="status-cell">
                <span :class="['status-dot', statusDotClass(row.status)]"></span>
                <el-tag size="small" :color="getStatusColor(row.status)" effect="light" class="status-tag" style="color:#fff;">{{ $dt(getStatusText(row.status)) }}</el-tag>
              </div>
            </template>
          </el-table-column>
        </template>
        <AuditColumns />
  </BillListPage>

  <!-- 审批弹窗（业务弹窗留在壳外） -->
  <el-dialog v-model="approveDialog" :title="$tf('审批')" width="400px">
    <el-form label-width="80px">
      <el-form-item :label="$tf('审批意见')">
        <el-radio-group v-model="approveAction">
          <el-radio label="approve">{{ $tt("同意") }}</el-radio>
          <el-radio label="reject">{{ $tt("驳回") }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item :label="$tf('备注')">
        <el-input v-model="approveRemark" type="textarea" :rows="3" :placeholder="$tt('请输入审批意见')" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button v-permission="perm('dlgCancel')" @click="approveDialog = false">{{ $tt("取消") }}</el-button>
      <el-button v-permission="perm('dlgConfirm')" type="primary" @click="doApprove">{{ $tt("确定") }}</el-button>
    </template>
  </el-dialog>

  <!-- 打印预览/列设置抽屉已由 BillListPage 壳统一渲染（bill-type / storage-key 传入） -->
  <BillImportDialog v-model="importVisible" entity-name="produceDisassemble" title="生产拆卸单导入" @done="fetchData" />
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageProduceDisassemble, submitProduceDisassemble, approveProduceDisassemble, deleteProduceDisassemble, resetDraftProduceDisassemble, rejectProduceDisassemble, revokeProduceDisassemble, cancelProduceDisassemble, exportProduceDisassemble, getProduceDisassembleDashboard, produceDisassembleDetailApi } from '@/api/production/produceDisassemble'
import BillListPage from '@/components/bill/BillListPage.vue'
import BillHeaderActions from '@/components/bill/BillHeaderActions.vue'
import BillImportDialog from '@/components/bill/BillImportDialog.vue'
import AuditColumns from '@/components/AuditColumns.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { Plus, Download, Link } from '@element-plus/icons-vue'

import { useTableSort } from '@/composables/useTableSort'
import { useCustomFieldSearch } from '@/composables/useCustomFieldSearch'
// 筛选条件自定义共享工厂（2026-10-14 全面推广）：候选集拼装统一在工厂，页面只声明本单键位差异
import { stdDefs, fld, cfDefs, detailFilterDefsOf } from '@/config/billFilterDefs'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('production:disassemble')
const importVisible = ref(false)
function handleImport() { importVisible.value = true }
const { handleSortChange, getSortParams } = useTableSort()

/**
 * 批量操作声明（2026-09-20）：UI/确认/执行/结果面板全部由通用壳承担，这里只声明"怎么执行"。
 * 命名口径全系统统一：批量提交 / 批量审批 / 批量重置草稿 / 批量删除。
 * **不声明「批量导出」**：该单据未接入通用"按 ids 导出"端点（/api/export/data/{entity}/selected），点了会失败。
 * 本页状态为数字：0 草稿 / 1 待审核 / 2 已生效 / 7 已作废。
 */
const batchActions = [
  {
    key: 'submit',
    label: tt('批量提交'),
    perm: 'production:disassemble:submit',
    type: 'primary',
    exec: (row) => submitProduceDisassemble(row.id),
    // 非草稿自动跳过（状态用本页 statusMap 文案，避免给用户看数字）
    skipReason: (row) => (row.status !== 0
      ? tt('当前状态：') + getStatusText(row.status) + tt('，不是草稿，无需提交') : '')
  },
  {
    key: 'approve',
    label: tt('批量审批'),
    perm: 'production:disassemble:audit',
    exec: (row) => approveProduceDisassemble(row.id, { remark: '' }),
    skipReason: (row) => (row.status !== 1
      ? tt('当前状态：') + getStatusText(row.status) + tt('，不是待审核，无需审批') : '')
  },
  {
    // 批量重置草稿：与列表行内「重置草稿」同一个后端入口（/reset），能否重置由后端判断
    key: 'reverse',
    label: tt('批量重置草稿'),
    perm: 'production:disassemble:reverseApprove',
    exec: (row) => resetDraftProduceDisassemble(row.id)
  },
  {
    key: 'delete',
    label: tt('批量删除'),
    type: 'danger',
    perm: 'production:disassemble:delete',
    exec: (row) => deleteProduceDisassemble(row.id),
    disabledReason: (rows) => (rows.some(r => r.status !== 0)
      ? tt('非草稿不能删除，请先重置草稿') : '')
  }
]

const onSortChange = (e) => { handleSortChange(e); fetchData() }
// 列定义（用于列设置抽屉+动态渲染）= 表格全部数据列；type 标记单元格渲染分支，纯文本列不加 type
const columns = [
  { prop: 'serial_no', label: '单据编号', minWidth: 180, sortable: true, type: 'billNo' },
  { prop: 'dateline', label: '单据日期', width: 120, sortable: true },
  { prop: 'dept', label: '生产部门', width: 120, sortable: true },
  { prop: 'wh_name', label: '成品仓库', width: 120, sortable: true },
  { prop: 'wh_name_bom', label: '原料仓库', width: 120, sortable: true },
  { prop: 'amount', label: '单据金额', width: 130, align: 'right', sortable: true, type: 'amount2' },
  { prop: 'status', label: '状态', width: 100, sortable: true, type: 'status' },
  { prop: 'creator_str', label: '创建人', width: 100, sortable: true },
  { prop: 'ctime', label: '创建时间', width: 160, sortable: true }
]
const { columnSettingVisible, openColumnSetting, isColVisible, getColFixed, configVersion, sortedColumns } = useColumnSetting('ProduceDisassembleList', columns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))

const loading = ref(false)
const tableData = ref([])
// 统一入参名：keyword / status / startDate / endDate（该页没有客户/供应商/员工维度）
// 「成品名称」走明细表（product_name 入参），单独持有，不混进 search
const search = reactive({ keyword: '', status: '', startDate: '', endDate: '' })
const productName = ref('')
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
// 单据状态下拉选项：value 用中文文案（与后端状态机取值逐字一致）——
// 后端列表 status 入参已收字符串（中文文案/数字串均可，服务层转数字码等值过滤）；
// 明细模式的 std 状态条件也走同一 search 通道（明细引擎按 CASE 转中文后的表达式等值比较），
// 中文 value 使汇总/明细两种模式一套取值，不再出现"数字串 vs 中文"两套口径。
// 取值来自后端状态机真实写入值：0 草稿 / 1 待审核 / 2 已生效 / 7 已作废
const statusOptions = [
  { label: '草稿', value: '草稿' },
  { label: '待审核', value: '待审核' },
  { label: '已生效', value: '已生效' },
  { label: '已作废', value: '已作废' }
]

// ============ 筛选条件自定义（2026-10-14 全面推广 · 销售订单试点标准） ============
// 条件候选 = 标准维度（状态/单据日期，std 标记 → 值走页面 search 通道，与统计卡互通）
//          + 本单表头字段（f_<key> 参数化，后端白名单翻译）
//          + 单据自定义字段（设置中心勾「可搜索」，沿用 customSearch 既有通道）。
// 键与「生产拆卸单明细报表」引擎筛选键同键 → 汇总/明细两种模式一套条件参数。
const cf = useCustomFieldSearch('produce_disassemble')
const filterDefs = computed(() => [
  ...stdDefs({
    status: { options: statusOptions },
    date: { key: 'dateline', label: '单据日期' }
  }),
  fld('text', 'serialNo', '单据编号'),
  fld('text', 'dept', '生产部门'),
  fld('select', 'whName', '成品仓库', 'warehouse'),
  fld('select', 'whNameBom', '原料仓库', 'warehouse'),
  fld('select', 'currencyCode', '币种', 'currency'),
  fld('text', 'taxCalcMethod', '计税方式'),
  fld('text', 'sourceType', '来源类型'),
  fld('text', 'salesmanStr', '业务员'),
  fld('text', 'approvalStatus', '审批状态'),
  fld('text', 'notes', '备注'),
  fld('numberRange', 'amount', '单据金额'),
  fld('numberRange', 'baseAmount', '本位币金额'),
  fld('numberRange', 'taxAmount', '税额'),
  fld('numberRange', 'taxRate', '税率%'),
  fld('numberRange', 'currencyRate', '汇率'),
  ...cfDefs(cf)
])

// 明细模式专属筛选条件（来源 = 明细报表 /columns 的 filters 元数据，键与引擎同键；
// STD_BY_KEY 声明引擎筛选键 → 标准维度（默认勾选=现状、值走页面 search 通道）。
// 引擎状态筛选键 = statusText（CASE 转中文后等值），与页面 search.status 中文取值同口径）
const detailFiltersMeta = ref([])
const STD_BY_KEY = { dateline: 'date', statusText: 'status' }
const detailFilterDefs = computed(() => detailFilterDefsOf(detailFiltersMeta.value, STD_BY_KEY))

/** 自定义条件参数（由通用壳经 filter-params 下发）：params 汇总用 / detailParams 明细用 / customSearch 自定义字段 */
const headerFilterState = ref({ params: {}, customSearch: undefined, detailParams: {} })
function onHeaderFilterParams(payload) {
  headerFilterState.value = payload || { params: {}, customSearch: undefined, detailParams: {} }
}

// 顶部统计卡片：数据来自后端 /api/produce/disassemble/dashboard（与列表同数据权限口径；原「本月拆卸」写死 0）
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
  { key: 'month', label: '本月拆卸', value: 0, type: 'primary', icon: 'Document', filter: monthRange() },
  { key: 'approved', label: '已生效', value: 0, type: 'success', icon: 'CircleCheck', filter: { status: '已生效' } },
  { key: 'pending', label: '待审核', value: 0, type: 'warning', icon: 'Clock', filter: { status: '待审核' } },
  { key: 'draft', label: '草稿', value: 0, type: 'info', icon: 'EditPen', filter: { status: '草稿' } }
])

async function loadStats() {
  try {
    const d = (await getProduceDisassembleDashboard()) || {}
    stats.value = [
      { key: 'month', label: '本月拆卸', value: d.monthDisassembles ?? 0, type: 'primary', icon: 'Document', filter: monthRange() },
      { key: 'approved', label: '已生效', value: d.approvedDisassembles ?? 0, type: 'success', icon: 'CircleCheck', filter: { status: '已生效' } },
      { key: 'pending', label: '待审核', value: d.pendingDisassembles ?? 0, type: 'warning', icon: 'Clock', filter: { status: '待审核' } },
      { key: 'draft', label: '草稿', value: d.draftDisassembles ?? 0, type: 'info', icon: 'EditPen', filter: { status: '草稿' } }
    ]
  } catch (e) {
    // 静默降级：取数失败时卡片保持原值，不弹诊断卡、不阻断列表
  }
}

const approveDialog = ref(false)
const approveRow = ref(null)
const approveAction = ref('approve')
const approveRemark = ref('')

const statusMap = {
  0: { text: '草稿', color: '#d9d9d9' },
  1: { text: '待审核', color: '#1890ff' },
  2: { text: '已生效', color: '#52c41a' },
  7: { text: '已作废', color: '#f5222d' }
}

function getStatusText(status) {
  return statusMap[status]?.text || '未知'
}

function getStatusColor(status) {
  return statusMap[status]?.color || '#d9d9d9'
}

function statusDotClass(status) {
  return {
    0: 'dot-info',
    1: 'dot-warning',
    2: 'dot-success',
    7: 'dot-danger'
  }[status] || 'dot-info'
}

// 卡片点击 → 应用该卡筛选条件（状态/日期先清空再套用，避免上一张卡残留）；再点同一张卡 = 取消
function onStatClick({ key, filter }) {
  if (activeStatKey.value === key) {
    activeStatKey.value = ''
    Object.assign(search, { status: '', startDate: '', endDate: '' })
  } else {
    activeStatKey.value = key
    Object.assign(search, { status: '', startDate: '', endDate: '' }, filter || {})
  }
  pagination.current = 1
  fetchData()
}

// 通用搜索栏（状态/日期）变更：先把值写回本页 search
// 注：BillSearchBar 变更后会紧接着抛 search 事件（壳已转发 → handleSearch），这里不重复发起查询
function onFilterChange(patch) {
  Object.assign(search, patch || {})
}

async function fetchData() {
  loading.value = true
  try {
    const res = await pageProduceDisassemble({
      current: pagination.current,
      size: pagination.size,
      // 统一入参名（后端 keyword/status/startDate/endDate）；旧入参 serial_no/dateline_* 后端仍兼容
      keyword: search.keyword || undefined,
      status: search.status || undefined,
      startDate: search.startDate || undefined,
      endDate: search.endDate || undefined,
      // 成品名称走明细表（后端 product_name → 先查明细拿主单id再 in）
      product_name: productName.value || undefined,
      ...getSortParams(),
      // 筛选条件自定义（2026-10-14）：f_* 表头字段过滤参数由通用壳统一下发
      ...(headerFilterState.value.params || {}),
      // 单据自定义字段搜索：沿用既有 customSearch 通道（值由通用壳从筛选条件统一打包）
      ...(headerFilterState.value.customSearch ? { customSearch: headerFilterState.value.customSearch } : {})
    })
    tableData.value = res.records || res || []
    pagination.total = res.total || tableData.value.length
  } catch (e) {
    ElMessage.error(e.message || tt('查询失败'))
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.current = 1
  fetchData()
}

function handleReset() {
  // 重置 = 回到未筛选状态：关键词 + 通用搜索栏 + 成品名称一并清空
  // 自定义筛选条件（f_*/自定义字段）的值由通用壳在重置时统一清空并下发空参数
  Object.assign(search, { keyword: '', status: '', startDate: '', endDate: '' })
  productName.value = ''
  activeStatKey.value = ''
  pagination.current = 1
  fetchData()
}

function handleApprove(row) {
  approveRow.value = row
  approveAction.value = 'approve'
  approveRemark.value = ''
  approveDialog.value = true
}

async function doApprove() {
  try {
    if (approveAction.value === 'approve') {
      await approveProduceDisassemble(approveRow.value.id, { remark: approveRemark.value })
      ElMessage.success(tt('审批通过'))
    } else {
      await rejectProduceDisassemble(approveRow.value.id, { remark: approveRemark.value })
      ElMessage.success(tt('已驳回'))
    }
    approveDialog.value = false
    fetchData()
  } catch (e) {
    ElMessage.error(e.message || tt('审批失败'))
  }
}

async function handleRevoke(row) {
  try {
    await ElMessageBox.confirm(`${tt("确认撤销拆卸单 ")}${row.serial_no}?`, tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await revokeProduceDisassemble(row.id)
    ElMessage.success(tt('撤销成功'))
    fetchData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('撤销失败'))
  }
}

async function handleCancel(row) {
  try {
    const { value } = await ElMessageBox.prompt(tt('请输入作废原因'), tt('作废拆卸单'), {
      confirmButtonText: tt('确认作废'),
      cancelButtonText: tt('返回'),
      type: 'warning',
      inputType: 'textarea',
      inputPlaceholder: tt('请输入作废原因'),
      inputValidator: (v) => (v && v.trim() ? true : '请输入作废原因')
    })
    await cancelProduceDisassemble(row.id, value)
    ElMessage.success(tt('作废成功'))
    fetchData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('作废失败'))
  }
}

async function handleExport() {
  // 2026-09-20：改走通用导出链路（原 /api/produce/disassemble/export 后端无该映射，点了必 404）。
  // 通用导出口径 = 按数据权限导全量（不再按列表当前筛选条件过滤），与销售订单等 30 个单据一致；
  // 文件名、无权限/空数据提示均由 exportUtil 统一处理，此处不再重复提示。
  try {
    await exportProduceDisassemble()
  } catch (e) {
    // 失败提示已由 exportUtil 内部统一弹出
  }
}

onMounted(() => {
  cf.load().catch(() => {})
  loadStats()
  fetchData()
  // 明细模式专属筛选条件元数据（明细报表 /columns 的 filters；失败静默：明细条件集为空时明细模式沿用汇总条件集）
  produceDisassembleDetailApi.getColumns().then(res => {
    detailFiltersMeta.value = (res && res.filters) || []
  }).catch(() => { /* 静默降级 */ })
})
useAutoRefresh(() => { fetchData(); loadStats() })
</script>

<style scoped>
.disassemble-list-page {
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

  .search-left {
    display: flex;
    gap: 8px;
    align-items: center;
    flex-wrap: wrap;
  }

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

.amount-text {
  font-weight: 600;
  color: #111827;
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

.action-cell {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
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
  .disassemble-list-page { padding: 12px; }
  .stat-cards { grid-template-columns: 1fr; }
  .search-bar {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }
  .status-tabs { flex-wrap: wrap; }
}
</style>