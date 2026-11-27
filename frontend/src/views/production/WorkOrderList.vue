<template>
  <!-- 通用列表壳（页头/统计卡/搜索条/表格外壳/分页/打印/列设置）+ 声明式筛选维度
       原「状态页签」整块删除 —— 状态改用通用搜索栏的「单据状态」下拉（filters 声明 'status' 时壳自动隐藏页签）
       该页真实维度：状态 / 单据日期(order_date) / 负责人(manager_id) + 本页特有维度「优先级」 -->
  <BillListPage
    page-class="workorder-list-page"
    back-path="/production"
    :title="$tt('生产工单')"
    badge="WORK ORDER"
    sub-title="高效追踪生产进度 · 实时监控工单状态"
    :stats="stats"
    :active-stat-key="activeStatKey"
    search-placeholder="输入工单号/产品编码/产品名称搜索"
    keyword-label="工单号"
    date-label="单据日期"
    :filters="['status', 'date', 'employee']"
    :status-options="statusOptions"
    :filter-defs="filterDefs"
    :detail-filter-defs="detailFilterDefs"
    :filter-model="search"
    employee-label="负责人"
    :page-sizes="[20, 50, 100]"
    :table-data="tableData"
    :loading="loading"
    :pagination="pagination"
    storage-key="WorkOrderList"
    :columns="columns"
    :table-key="configVersion"
    bill-type="WORK_ORDER"
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
    <!-- 本页特有筛选维度（通用搜索栏未覆盖的维度）：优先级 普通/加急/紧急 -->
    <template #search-extra>
      <div class="filter-item">
        <span class="filter-label">{{ $tt('优先级') }}</span>
        <el-select v-model="search.priority" :placeholder="$tt('优先级')" clearable class="search-select" @change="onPriorityChange">
          <el-option :label="$tf('普通')" value="普通" />
          <el-option :label="$tf('加急')" value="加急" />
          <el-option :label="$tf('紧急')" value="紧急" />
        </el-select>
      </div>
    </template>

    <template #header-actions="{ selectedRows: sel }">
      <!-- 页头按钮统一走通用组件（2026-09-19）：按钮码由 perm-prefix 派生，权限控制一处生效 -->
      <BillHeaderActions
        perm-prefix="production:workorders"
        :add-text="$tt('新增工单')"
        :show-import="false"
        :print-disabled="!sel.length"
        @add="onAdd"
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
          <div v-if="col.type === 'orderNo'" class="order-no-cell">
            <el-tag v-if="row.isUrgent === 1" type="danger" size="small" effect="dark" style="margin-right:6px;">{{ $tt("急") }}</el-tag>
            <span class="order-no-text" @click="goDetail(row)">{{ row.orderNo }}</span>
            <el-icon class="link-icon"><Link /></el-icon>
          </div>
          <div v-else-if="col.type === 'materialName'">
            <div>{{ row.materialName }}</div>
            <div class="sub-text">{{ row.model }}</div>
          </div>
          <div v-else-if="col.type === 'progress'" class="progress-cell">
            <div class="progress-top">
              <span class="progress-num">{{ calcProgress(row) }}%</span>
              <span class="progress-qty">{{ row.completedQuantity || 0 }}/{{ row.quantity }}</span>
            </div>
            <el-progress
              :percentage="calcProgress(row)"
              :color="progressColor(row)"
              :stroke-width="10"
              :show-text="false"
            />
            <div class="progress-op">
              <span v-if="row.currentProcess || row.currentOperation" class="op-tag">
                <el-icon class="op-icon"><Clock /></el-icon>{{ row.currentProcess || row.currentOperation }}
              </span>
              <span v-else class="sub-text">—</span>
            </div>
          </div>
          <div v-else-if="col.type === 'planDate'" class="date-info">
            <div><span class="date-label">{{ $tt("开始:") }}</span> {{ row.planStartDate || '-' }}</div>
            <div><span class="date-label">{{ $tt("完工:") }}</span> {{ row.planEndDate || '-' }}</div>
          </div>
          <el-tag v-else-if="col.type === 'source'" size="small" :type="sourceType(row.sourceType)">{{ row.sourceType || '手工' }}</el-tag>
          <div v-else-if="col.type === 'status'" class="status-cell">
            <span :class="['status-dot', dotClass(row)]"></span>
            <el-tag size="small" :type="billTagType(row)" effect="light" class="status-tag">{{ $dt(statusLabel(row)) }}</el-tag>
          </div>
        </template>
      </el-table-column>
    </template>
    <AuditColumns />
  </BillListPage>

  <!-- 关闭工单弹窗（弹窗留在壳外） -->
  <el-dialog :title="$tf('关闭工单')" v-model="closeVisible" width="420px">
    <el-form label-width="80px">
      <el-form-item :label="$tf('关闭原因')">
        <el-input v-model="closeReason" type="textarea" :rows="3" :placeholder="$tt('请输入关闭原因')" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button v-permission="perm('dlgCancel')" @click="closeVisible = false">{{ $tt("取消") }}</el-button>
      <el-button v-permission="perm('dlgConfirm')" type="warning" :loading="acting" @click="doClose">{{ $tt("确认关闭") }}</el-button>
    </template>
  </el-dialog>
  <!-- 打印预览/列设置抽屉已由 BillListPage 壳统一渲染（bill-type / storage-key 传入） -->
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  pageWorkOrder,
  getWorkOrderDashboard,
  submitWorkOrder, approveWorkOrder, closeWorkOrder,
  reopenWorkOrder, voidWorkOrder, reverseApproveWorkOrder, deleteWorkOrder,
  getWorkOrderProgress, productionOrderDetailApi
} from '@/api/production/workOrder'
// 单据状态/结案口径统一走通用工具（status 只有 草稿/待审核/已生效/已作废；已结案 = close_status=1）
import * as BillStatus from '@/utils/bill-status'
import { exportListAsSeen, columnsToExportFields } from '@/utils/exportUtil'
import BillListPage from '@/components/bill/BillListPage.vue'
import BillHeaderActions from '@/components/bill/BillHeaderActions.vue'
import AuditColumns from '@/components/AuditColumns.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
// 自定义字段「可搜索」搜索项（设置中心 → 自定义字段配置里勾了「可搜索」的字段才生效）
import { useCustomFieldSearch } from '@/composables/useCustomFieldSearch'
// 筛选条件自定义共享工厂（2026-10-14 全面推广）：候选集拼装统一在工厂，页面只声明本单键位差异
import { stdDefs, fld, cfDefs, detailFilterDefsOf } from '@/config/billFilterDefs'
import { Plus, Download, Link, Clock } from '@element-plus/icons-vue'

import { useTableSort } from '@/composables/useTableSort'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('production:workorders')
const { handleSortChange, getSortParams } = useTableSort()


/**
 * 批量操作声明（2026-09-20 对齐后端新契约）：UI/确认/执行/结果面板全部由通用壳承担，这里只声明"怎么执行"。
 * 单据状态只有 4 个中文串：草稿 / 待审核 / 已生效 / 已作废（旧的业务执行态已全部退役）；
 * 「已结案」是 close_status=1 的独立闭环口径，不是 status 取值。
 * 动作与后端入口一一对应：提交=POST /submit、审批=POST /approve、结案=POST /close、
 * 反结案=POST /reopen、作废=POST /void、重置草稿=POST /reverse-approve、删除=DELETE /{id}。
 * 每条的"能不能做"一律复用 `@/utils/bill-status` 的 can*，本页不再自写状态判断。
 */
const batchActions = [
  {
    key: 'submit',
    label: tt('批量提交'),
    perm: 'production:workorders:submit',
    type: 'primary',
    exec: (row) => submitWorkOrder(row.orderId),
    // 非草稿自动跳过（待审核/已生效/已作废 无需重复提交）
    skipReason: (row) => (BillStatus.canSubmit(row.status, row.closeStatus)
      ? '' : tt('当前状态：') + (row.status || '-') + tt('，不是草稿，无需提交'))
  },
  {
    key: 'approve',
    label: tt('批量审批'),
    perm: 'production:workorders:audit',
    type: 'success',
    exec: (row) => approveWorkOrder(row.orderId),
    // 只有待审核（及草稿）可审批；已生效/已作废 自动跳过
    skipReason: (row) => (BillStatus.canApprove(row.status, row.closeStatus)
      ? '' : tt('当前状态：') + (row.status || '-') + tt('，不是待审核，无需审批'))
  },
  {
    key: 'close',
    label: tt('批量结案'),
    perm: 'production:workorders:close',
    exec: (row) => closeWorkOrder(row.orderId),
    // 只有已生效且未结案可结案
    skipReason: (row) => (BillStatus.canClose(row.status, row.closeStatus)
      ? '' : tt('当前状态：') + statusLabel(row) + tt('，不可结案'))
  },
  {
    key: 'reopen',
    label: tt('批量反结案'),
    perm: 'production:workorders:reopen',
    exec: (row) => reopenWorkOrder(row.orderId),
    // 只有已结案可反结案
    skipReason: (row) => (BillStatus.canReopen(row.status, row.closeStatus)
      ? '' : tt('当前状态：') + statusLabel(row) + tt('，不是已结案，无需反结案'))
  },
  {
    key: 'void',
    label: tt('批量作废'),
    type: 'danger',
    perm: 'production:workorders:void',
    exec: (row) => voidWorkOrder(row.orderId),
    // 已结案/已作废不可直接作废（须先反结案）
    skipReason: (row) => (BillStatus.canVoid(row.status, row.closeStatus)
      ? '' : tt('当前状态：') + statusLabel(row) + tt('，不可作废（已结案请先反结案）'))
  },
  {
    // 批量重置草稿：与编辑页「重置草稿」同一个后端入口（/reverse-approve），能否重置由后端判断
    key: 'reverse',
    label: tt('批量重置草稿'),
    perm: 'production:workorders:reverseApprove',
    exec: (row) => reverseApproveWorkOrder(row.orderId),
    skipReason: (row) => (BillStatus.canReverseApprove(row.status, row.closeStatus)
      ? '' : tt('当前状态：') + statusLabel(row) + tt('，不是已生效，不可重置草稿'))
  },
  {
    key: 'delete',
    label: tt('批量删除'),
    type: 'danger',
    perm: 'production:workorders:delete',
    exec: (row) => deleteWorkOrder(row.orderId),
    // 状态冲突：选中行含非草稿 → 按钮置灰并说明原因（只有草稿可删）
    disabledReason: (rows) => (rows.some(r => !BillStatus.canDelete(r.status, r.closeStatus))
      ? tt('非草稿不能删除，请先重置草稿') : '')
  },
  {
    key: 'export',
    label: tt('批量导出'),
    perm: 'production:workorders:export',
    entity: 'workOrder',
    idOf: (row) => row.orderId,
    // 勾选导出同样按"页面当前可见列"（2026-09-24 全站口径）
    fields: () => columnsToExportFields(visibleSortedColumns.value)
  }
]

const onSortChange = (e) => { handleSortChange(e); fetchData() }
// 列定义（用于列设置抽屉+动态渲染）= 表格全部数据列；type 标记单元格渲染分支，纯文本列不加 type
const columns = [
  { prop: 'orderNo', label: '工单号', minWidth: 150, sortable: true, type: 'orderNo' },
  { prop: 'materialCode', label: '产品编码', minWidth: 110, sortable: true },
  { prop: 'materialName', label: '产品名称', minWidth: 160, sortable: true, type: 'materialName' },
  { prop: '_progress', label: '完成进度', width: 220, align: 'center', type: 'progress' },
  { prop: '_planDate', label: '计划日期', width: 200, type: 'planDate' },
  { prop: '_source', label: '来源', width: 100, type: 'source' },
  { prop: '_status', label: '状态', width: 160, type: 'status' }
]
const { columnSettingVisible, isColVisible, getColFixed, configVersion, sortedColumns } = useColumnSetting('WorkOrderList', columns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))

const router = useRouter()
const loading = ref(false)
const acting = ref(false)
const tableData = ref([])
const selectedRows = ref([])
const printVisible = ref(false)
const printBillId = ref(null)

// 页头「打印」：一次只能打印一张（与销售订单口径一致）
function handleBatchPrint(rows) {
  const list = (rows && rows.length ? rows : selectedRows.value) || []
  if (!list.length) { ElMessage.warning(tt('请先勾选要打印的单据')); return }
  if (list.length > 1) { ElMessage.warning(tt('一次只能打印一张单据，请仅勾选一行')); return }
  printBillId.value = list[0].orderId
  printVisible.value = true
}
// 统一入参名（docs/knowledge-base.md 6.1）：keyword / status / startDate / endDate / employeeId（负责人）
// + 本页特有维度 priority（普通/加急/紧急，与工单编辑页取值一致）
const search = reactive({ keyword: '', status: '', startDate: '', endDate: '', employeeId: '', priority: '' })
const pagination = reactive({ current: 1, size: 20, total: 0 })
// 通用搜索栏「单据状态」下拉选项：必须与后端状态机真实取值一致（status 只有 草稿/待审核/已生效/已作废）
// 注：不提供「已结案」选项 —— 结案走独立字段 close_status，而后端列表只接受 status 单字段等值过滤
//     （WorkOrderServiceImpl#pageQuery 无 close_status 入参），挂上去只会筛出空列表（假筛选），故不硬造
const statusOptions = [
  { label: '草稿', value: '草稿' },
  { label: '待审核', value: '待审核' },
  { label: '已生效', value: '已生效' },
  { label: '已作废', value: '已作废' }
]

// ============ 筛选条件自定义（2026-10-14 全面推广 · 销售订单试点标准） ============
// 条件候选 = 标准维度（状态/工单日期/负责人，std 标记 → 值走页面 search 通道，与统计卡互通）
//          + 本单表头字段（f_<key> 参数化，后端白名单翻译）
//          + 单据自定义字段（设置中心勾「可搜索」，沿用 customSearch 既有通道）。
// 键与「生产工单明细报表」引擎筛选键同键 → 汇总/明细两种模式一套条件参数。
// std 条件 = 现状默认勾选：没保存过筛选设置时查询区与改版前完全一致。
const cf = useCustomFieldSearch('production_order')
const filterDefs = computed(() => [
  ...stdDefs({
    status: { options: statusOptions },
    date: { key: 'orderDate', label: '工单日期' },
    employee: { key: 'managerName', label: '负责人' }
  }),
  fld('text', 'orderNo', '工单号'),
  fld('text', 'materialCode', '产品编码'),
  fld('text', 'materialName', '产品名称'),
  fld('text', 'model', '规格型号'),
  fld('select', 'deptName', '生产部门', 'department'),
  fld('select', 'warehouseName', '仓库', 'warehouse'),
  fld('dateRange', 'planStartDate', '计划开工日期'),
  fld('dateRange', 'planEndDate', '计划完工日期'),
  fld('text', 'sourceType', '来源类型'),
  fld('text', 'sourceNo', '来源单号'),
  fld('text', 'salesOrderNo', '销售订单号'),
  fld('numberRange', 'quantity', '生产数量'),
  fld('numberRange', 'completedQuantity', '完工数量'),
  fld('text', 'approvedBy', '审核人'),
  fld('text', 'remark', '备注'),
  ...cfDefs(cf)
])

// 明细模式专属筛选条件（来源 = 明细报表 /columns 的 filters 元数据，键与引擎同键；
// STD_BY_KEY 声明引擎筛选键 → 标准维度（默认勾选=现状、值走页面 search 通道））
const detailFiltersMeta = ref([])
const STD_BY_KEY = { status: 'status', orderDate: 'date', managerName: 'employee' }
const detailFilterDefs = computed(() => detailFilterDefsOf(detailFiltersMeta.value, STD_BY_KEY))

/** 自定义条件参数（由通用壳经 filter-params 下发）：params 汇总用 / detailParams 明细用 / customSearch 自定义字段 */
const headerFilterState = ref({ params: {}, customSearch: undefined, detailParams: {} })
function onHeaderFilterParams(payload) {
  headerFilterState.value = payload || { params: {}, customSearch: undefined, detailParams: {} }
}
// 统计卡片高亮（点卡片筛选列表时由 onStatClick 控制）
const activeStatKey = ref('')

const headerCellStyle = {
  background: 'rgba(99, 102, 241, 0.04)',
  color: '#1f2937',
  fontWeight: 600,
  borderBottom: '2px solid #e0e7ff'
}

const closeVisible = ref(false)
const closeReason = ref('')
const closeOrderId = ref(null)
const closeOrderNo = ref('')

// 顶部统计卡片：数据来自后端 /api/production/dashboard（与 /workorders 同数据权限口径；原来是前端按本页数据自算）
// 带 filter 的卡片可点击 → 直接筛选下方列表（再点同一张卡 = 取消）
const pad2 = (n) => String(n).padStart(2, '0')
function monthRange() {
  const d = new Date()
  const y = d.getFullYear()
  const m = d.getMonth() + 1
  const lastDay = new Date(y, m, 0).getDate()
  return { startDate: `${y}-${pad2(m)}-01`, endDate: `${y}-${pad2(m)}-${pad2(lastDay)}` }
}
// 5 张卡（宽屏一行）：本月工单 / 草稿 / 待审核 / 已生效(在制) / 已结案
// 前 3 张可点击钻取（日期区间 / status 等值，后端均支持）；
// 「已生效(在制)」与「已结案」口径含 close_status，后端列表无该入参 → 不挂 filter（不可点击，杜绝筛错）
const stats = ref([
  { key: 'month', label: '本月工单', value: 0, type: 'primary', icon: 'Document', filter: monthRange() },
  { key: 'draft', label: '草稿', value: 0, type: 'warning', icon: 'EditPen', filter: { status: '草稿' } },
  { key: 'pending', label: '待审核', value: 0, type: 'info', icon: 'Clock', filter: { status: '待审核' } },
  { key: 'approved', label: '已生效(在制)', value: 0, type: 'success', icon: 'CircleCheck' },
  { key: 'closed', label: '已结案', value: 0, type: 'info', icon: 'Finished' }
])

async function loadStats() {
  try {
    const d = (await getWorkOrderDashboard()) || {}
    // key 与后端 /production/dashboard 一一对应：monthOrders / draft / pending / approved(已生效且未结案) / closed(close_status=1)
    stats.value = [
      { key: 'month', label: '本月工单', value: d.monthOrders ?? 0, type: 'primary', icon: 'Document', filter: monthRange() },
      { key: 'draft', label: '草稿', value: d.draft ?? 0, type: 'warning', icon: 'EditPen', filter: { status: '草稿' } },
      { key: 'pending', label: '待审核', value: d.pending ?? 0, type: 'info', icon: 'Clock', filter: { status: '待审核' } },
      { key: 'approved', label: '已生效(在制)', value: d.approved ?? 0, type: 'success', icon: 'CircleCheck' },
      { key: 'closed', label: '已结案', value: d.closed ?? 0, type: 'info', icon: 'Finished' }
    ]
  } catch (e) {
    // 静默降级：取数失败时卡片保持原值，不弹诊断卡、不阻断列表
  }
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

function calcProgress(row) {
  if (!row.quantity || Number(row.quantity) === 0) return 0
  const pct = Math.round((row.completedQuantity || 0) / row.quantity * 100)
  return Math.min(pct, 100)
}

// 进度颜色：只按完工百分比着色；已结案（close_status=1）= 闭环终态 → 灰色
// （旧的业务执行态状态名已退役，不再按状态名判断）
function progressColor(row) {
  if (BillStatus.isClosed(row.closeStatus)) return '#909399'
  const pct = calcProgress(row)
  if (pct >= 100) return '#15803d' // 100% 深绿色
  if (pct >= 80) return '#67c23a'  // 80-99% 绿色
  if (pct >= 50) return '#409eff'  // 50-80% 蓝色
  return '#909399'                  // <50% 灰色
}

// 状态相关展示全部复用通用工具 @/utils/bill-status，本页不再维护重复的状态映射表
function billTagType(row) {
  return BillStatus.statusTagType(row.status, row.closeStatus)
}
// 已生效 + close_status=1 → 展示为「已结案」（结案是独立字段，不是 status 取值）
function statusLabel(row) {
  return BillStatus.isClosed(row.closeStatus)
    ? BillStatus.CLOSE_STATUS.CLOSED_LABEL
    : (row.status || '')
}
// 状态圆点色由标签类型派生（纯颜色映射，不含状态判断）
function dotClass(row) {
  const t = billTagType(row)
  if (t === 'success') return 'dot-success'
  if (t === 'warning') return 'dot-warning'
  if (t === 'danger') return 'dot-danger'
  return 'dot-info'
}

function sourceType(t) {
  if (!t) return 'info'
  if (t.includes('MRP')) return 'warning'
  if (t.includes('销售')) return 'success'
  return 'info'
}

// 通用搜索栏（状态/日期/负责人）变更：先把值写回本页 search
// 注：BillSearchBar 变更后会紧接着抛 search 事件（壳已转发 → handleSearch），这里不重复发起查询
function onFilterChange(patch) {
  Object.assign(search, patch || {})
}

/**
 * 列表与导出**共用**的查询条件（2026-09-24 全站口径）：
 * 导出必须与列表用同一套条件，抽成一个函数避免两处各写一份导致口径漂移
 * （用户要求"页面上看到什么列，导出就带什么列；导出行 = 当前筛选条件下的全部"）。
 */
function buildQueryParams() {
  return {
    keyword: search.keyword, status: search.status,
    startDate: search.startDate, endDate: search.endDate,
    employeeId: search.employeeId, priority: search.priority,
    // 筛选条件自定义（2026-10-14）：f_* 表头字段过滤参数由通用壳统一下发
    ...(headerFilterState.value.params || {}),
    // 单据自定义字段搜索：沿用既有 customSearch 通道（值由通用壳从筛选条件统一打包）
    ...(headerFilterState.value.customSearch ? { customSearch: headerFilterState.value.customSearch } : {}),
    ...getSortParams()
  }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await pageWorkOrder({ current: pagination.current, size: pagination.size, ...buildQueryParams() })
    tableData.value = res.records || []
    pagination.total = res.total || 0
    // 列表若未返回进度/当前工序数据，懒加载调用进度接口补充
    lazyLoadProgress()
  } catch (e) { /* 拦截器已提示 */ } finally {
    loading.value = false
  }
}

// 对缺少进度数据的工单行懒加载进度详情
function lazyLoadProgress() {
  tableData.value.forEach(row => {
    // 缺少完工量或当前工序时，调用进度接口补充
    const needProgress = (row.completedQuantity == null) && !row.currentProcess && !row.currentOperation
    if (!needProgress) return
    getWorkOrderProgress(row.orderId).then(res => {
      if (!res) return
      if (res.workOrder) {
        if (res.workOrder.completedQuantity != null) row.completedQuantity = res.workOrder.completedQuantity
        if (res.workOrder.quantity != null) row.quantity = res.workOrder.quantity
      }
      if (res.currentOperation) row.currentProcess = res.currentOperation
      if (res.totalProgress != null && (!row.completedQuantity || !row.quantity)) {
        // 若后端只给百分比，回填一个虚拟完工量用于展示
        if (row.quantity && !row.completedQuantity) {
          row.completedQuantity = Math.round(Number(row.quantity) * (Number(res.totalProgress) / 100) * 100) / 100
        }
      }
    }).catch(() => {})
  })
}

function handleSearch() { pagination.current = 1; fetchData() }

function handleReset() {
  // 重置 = 回到未筛选状态：关键词 + 通用搜索栏 + 本页特有筛选一并清空
  Object.assign(search, { keyword: '', status: '', startDate: '', endDate: '', employeeId: '', priority: '' })
  activeStatKey.value = ''   // 同时取消统计卡片的高亮
  pagination.current = 1
  fetchData()
}

// 优先级下拉变更：与卡片高亮互斥后查询
function onPriorityChange() {
  activeStatKey.value = ''
  pagination.current = 1
  fetchData()
}

function goDetail(row) {
  router.push(`/production/workorders/${row.orderId}`)
}

function onAdd() {
  router.push('/production/workorders/new')
}

function onEdit(row) {
  router.push(`/production/workorders/${row.orderId}`)
}

// 行内「下达/开始执行/完工」已随后端删除的三个动作端点（release / start / complete）一并删除，
// 状态机收敛为 草稿/待审核/已生效/已作废 + close_status，统一改为批量条上的通用动作集
function onClose(row) {
  closeReason.value = ''
  closeOrderId.value = row.orderId
  closeOrderNo.value = row.orderNo
  closeVisible.value = true
}

async function doClose() {
  acting.value = true
  try {
    await closeWorkOrder(closeOrderId.value, closeReason.value)
    ElMessage.success(tt('工单已关闭'))
    closeVisible.value = false
    fetchData()
  } catch (e) { /* 拦截器已提示 */ } finally {
    acting.value = false
  }
}

async function handleExport() {
  try {
    // 页面上看到什么列，导出就带什么列；导出行 = 当前筛选条件下的全部
    await exportListAsSeen({
      entityName: 'workOrder',
      columns: visibleSortedColumns.value,
      fetchPage: pageWorkOrder,
      params: buildQueryParams(),
      idField: 'orderId'
    })
    ElMessage.success(tt('导出成功'))
  } catch (e) {
    // 失败原因由 exportUtil 统一提示
  }
}

onMounted(() => {
  cf.load()
  loadStats()
  fetchData()
  // 明细模式专属筛选条件元数据（明细报表 /columns 的 filters；失败静默：明细条件集为空时明细模式沿用汇总条件集）
  productionOrderDetailApi.getColumns().then(res => {
    detailFiltersMeta.value = (res && res.filters) || []
  }).catch(() => { /* 静默降级 */ })
})
useAutoRefresh(() => { fetchData(); loadStats() })
</script>

<style scoped lang="scss">
.workorder-list-page {
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

/* 本页统计卡为 5 张（壳内通用件 StatCards 默认 4 列栅格）：宽屏一行放下 5 张；
   窄屏仍走 StatCards 自带的 2 列 / 1 列响应式断点，不改变小屏布局 */
@media (min-width: 1200px) {
  .workorder-list-page :deep(.stat-cards) {
    grid-template-columns: repeat(5, 1fr);
  }
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

  .search-select {
    width: 130px;
    :deep(.el-select__wrapper) {
      border-radius: 8px;
    }
  }
}

/* 状态页签样式已删除：状态改为通用搜索栏的「单据状态」下拉（filters 声明 'status'，壳自动隐藏页签） */

/* 搜索区扩展筛选项（优先级下拉）：与通用搜索栏的标签列对齐 */
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

.sub-text {
  font-size: 12px;
  color: #909399;
}

.progress-cell {
  .progress-top {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 4px;
    padding: 0 2px;
  }
  .progress-num {
    font-size: 13px;
    font-weight: 600;
    color: #6366f1;
  }
  .progress-qty {
    font-size: 12px;
    color: #909399;
  }
  .progress-op {
    margin-top: 4px;
    .op-tag {
      display: inline-flex;
      align-items: center;
      gap: 3px;
      font-size: 11px;
      color: #6366f1;
      background: #eef2ff;
      padding: 1px 8px;
      border-radius: 10px;
      max-width: 100%;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .op-icon {
      font-size: 11px;
      flex-shrink: 0;
    }
  }
}

.date-info {
  font-size: 12px;
  line-height: 1.7;
  .date-label {
    color: #909399;
    margin-right: 4px;
  }
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

.picker-bar {
  display: flex; align-items: center; gap: 12px;
  padding: 8px 12px; margin-bottom: 12px;
  background: #f0f9ff; border: 1px dashed #409eff; border-radius: 6px;
  .picker-hint { font-size: 12px; color: #909399; }
  .picker-source { font-size: 12px; color: #409eff; font-weight: 600; }
}

@media (max-width: 1200px) {
  .stat-cards { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 768px) {
  .workorder-list-page { padding: 12px; }
  .stat-cards { grid-template-columns: 1fr; }
  .search-bar {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }
}
</style>
