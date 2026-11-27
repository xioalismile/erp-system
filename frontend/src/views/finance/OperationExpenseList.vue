<template>
  <!-- 通用列表壳（页头/统计卡/搜索条/表格外壳/分页/打印/列设置）+ 声明式筛选维度
       注：本页不声明 'customer' / 'supplier' 维度 —— operation_expense 表无客户/供应商列；
       声明 'employee' 维度 → operation_expense.applicant_id（申请人）；
       「费用类型」已随筛选条件自定义迁入 filterDefs（声明 filterDefs 后扩展搜索槽不再渲染） -->
  <BillListPage
    page-class="operation-expense-page"
    back-path="/finance"
    :title="$tt('费用分摊')"
    badge="OPERATION EXPENSE"
    sub-title="费用分摊 · 自动核算关联订单成本"
    create-path="/finance/operation-expense/new"
    :stats="stats"
    :active-stat-key="activeStatKey"
    search-placeholder="输入单号/申请人搜索"
    keyword-label="单据编号"
    :filters="['status', 'date', 'employee']"
    :employee-label="'申请人'"
    :status-options="statusOptions"
    :filter-model="search"
    date-label="费用日期"
    :filter-defs="filterDefs"
    :detail-filter-defs="detailFilterDefs"
    :table-data="tableData"
    :loading="loading"
    :pagination="pagination"
    storage-key="OperationExpenseList"
    :columns="columns"
    :table-key="configVersion"
    bill-type="OPERATION_EXPENSE"
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
        perm-prefix="finance:operation-expense"
        :add-text="$tt('新增费用')"
        :print-disabled="!sel.length"
        :show-refresh="true"
        @add="$router.push('/finance/operation-expense/new')"
        @print="handleBatchPrint(sel)"
        @export="handleExport"
        @import="handleImport"
        @refresh="fetchData"
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
            :fixed="getColFixed(col.prop) || col.fixed"
          >
            <template v-if="col.type" #default="{ row }">
              <div v-if="col.type === 'billNo'" class="order-no-cell">
                <span class="order-no-text" @click="$router.push(`/finance/operation-expense/${row.id}`)">{{ row[col.prop] }}</span>
                <el-icon class="link-icon"><Link /></el-icon>
              </div>
              <template v-else-if="col.type === 'expenseType'">
                <template v-if="row.expenseTypes && row.expenseTypes.length > 1">
                  <el-tag size="small" effect="light">{{ row.expenseTypes[0] }}等{{ row.expenseTypes.length }}种</el-tag>
                </template>
                <template v-else-if="row.expenseType">
                  <el-tag :type="typeTagType(row.expenseType)" size="small" effect="light">{{ row.expenseType }}</el-tag>
                </template>
                <span v-else></span>
              </template>
              <span v-else-if="col.type === 'net'" class="net-amount">¥{{ Number(row[col.prop] || 0).toFixed(2) }}</span>
              <div v-else-if="col.type === 'status'" class="status-cell">
                <span :class="['status-dot', statusDotClass(row.status)]"></span>
                <el-tag :type="statusTagType(row.status)" size="small" effect="light">{{ $dt(row.status) }}</el-tag>
              </div>
            </template>
          </el-table-column>
        </template>
    <AuditColumns />
  </BillListPage>

  <!-- 导入弹窗 -->
    <el-dialog
      v-model="importDialogVisible"
      :title="$tf('费用支出单导入')"
      width="560px"
      :close-on-click-modal="false"
      class="tech-dialog"
    >
      <div class="import-dialog-content">
        <div class="import-step">
          <div class="step-title">
            <el-icon><Download /></el-icon>
            <span>{{ $tt("第一步：下载导入模板") }}</span>
          </div>
          <div class="step-desc">{{ $tt("下载模板后，按模板格式填写数据，支持 .xlsx 格式") }}</div>
          <div class="step-desc" style="color:#f56c6c;">{{ $tt("提示：模板中带") }} <b>*</b> {{ $tt("标记的字段为必填字段，请务必填写") }}</div>
          <el-button v-permission="perm('downloadTemplate')" type="primary" @click="downloadTemplate" :loading="downloadingTemplate">
            <el-icon><Download /></el-icon> 下载精简模板（含自定义字段）
          </el-button>
          <el-collapse class="template-field-panel" v-model="fieldPanelActive" style="margin-top:12px;">
            <el-collapse-item name="custom">
              <template #title><span style="font-weight:500;">{{ $tt("自定义模板字段（可勾选额外列）") }}</span></template>
              <div class="field-custom-container">
                <div class="field-custom-actions" style="margin-bottom:8px;">
                  <el-button v-permission="perm('columnSetting')" size="small" @click="selectAllOptional">{{ $tt("全选可选列") }}</el-button>
                  <el-button v-permission="perm('clear')" size="small" @click="clearOptional">{{ $tt("清空可选列") }}</el-button>
                </div>
                <div class="field-group-title" style="margin-bottom:6px;">{{ $tt("必带列（锁定，含自定义字段）") }}</div>
                <div class="field-checkbox-group">
                  <el-tag v-for="f in templateFields.required" :key="f" size="small" effect="plain" style="margin:4px 8px 4px 0;">{{ f }}</el-tag>
                </div>
                <div class="field-group-title" style="margin:12px 0 6px;">{{ $tt("可选列（按需勾选）") }}</div>
                <div class="field-checkbox-group">
                  <el-checkbox v-for="f in templateFields.optional" :key="f" v-model="checkedOptionalFields" :label="f" :border="true" size="small" style="margin:6px 12px 6px 0;">{{ f }}</el-checkbox>
                </div>
                <div style="margin-top:12px;text-align:right;">
                  <el-button v-permission="perm('downloadTemplate')" type="success" size="small" @click="downloadCustomTemplate" :loading="downloadingCustomTemplate">
                    <el-icon><Download /></el-icon> 按勾选下载
                  </el-button>
                </div>
              </div>
            </el-collapse-item>
          </el-collapse>
        </div>
        <el-divider />
        <div class="import-step">
          <div class="step-title">
            <el-icon><Upload /></el-icon>
            <span>{{ $tt("第二步：上传数据文件") }}</span>
          </div>
          <div class="step-desc">{{ $tt("选择填写好的 Excel 文件上传导入") }}</div>
          <el-upload
            ref="uploadRef"
            action="#"
            :auto-upload="false"
            :on-change="handleFileChange"
            :show-file-list="true"
            accept=".xlsx,.xls,.csv"
            drag
            class="import-uploader"
          >
            <el-icon class="el-icon--upload"><Upload /></el-icon>
            <div class="el-upload__text">{{ $tt("将文件拖到此处，或") }}<em>{{ $tt("点击上传") }}</em></div>
            <template #tip>
              <div class="el-upload__tip">{{ $tt("请上传 .xlsx / .xls 格式文件，文件大小不超过 10MB") }}</div>
            </template>
          </el-upload>
        </div>
        <div v-if="importResult" class="import-result">
          <el-divider />
          <div class="result-title">{{ $tt("导入结果") }}</div>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-statistic :title="$tt('总记录数')" :value="importResult.totalCount || 0" />
            </el-col>
            <el-col :span="8">
              <el-statistic :title="$tt('成功')" :value="importResult.successCount || 0">
                <template #suffix><el-icon color="#67C23A"><CircleCheck /></el-icon></template>
              </el-statistic>
            </el-col>
            <el-col :span="8">
              <el-statistic :title="$tt('失败')" :value="importResult.failCount || 0">
                <template #suffix><el-icon color="#F56C6C"><CircleClose /></el-icon></template>
              </el-statistic>
            </el-col>
          </el-row>
          <el-alert
            v-if="importResult.failCount > 0"
            :title="`有 ${importResult.failCount} 条记录导入失败`"
            type="error"
            :closable="false"
            class="import-error-alert"
          >
            <div class="error-list">
              <div v-for="(err, idx) in (importResult.errors || []).slice(0, 10)" :key="idx" class="error-item">
                第 {{ err.rowIndex }} 行 {{ err.fieldName ? '[' + err.fieldName + ']' : '' }}: {{ err.message }}
              </div>
              <div v-if="(importResult.errors || []).length > 10" class="error-more">
                ... 还有 {{ importResult.errors.length - 10 }} 条错误
              </div>
            </div>
          </el-alert>
        </div>
      </div>
      <template #footer>
        <el-button v-permission="perm('dlgClose')" @click="importDialogVisible = false">{{ $tt("关闭") }}</el-button>
        <el-button v-permission="perm('import')" type="primary" @click="confirmImport" :loading="importing" :disabled="!selectedImportFile">
          <el-icon><Upload /></el-icon> 开始导入
        </el-button>
      </template>
    </el-dialog>
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Refresh, Link, Upload, Download, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import {
  pageExpense,
  getOperationExpenseDashboard,
  submitExpense,
  approveExpense,
  reverseApproveExpense,
  deleteExpense
} from '@/api/finance/operationExpense'
import BillListPage from '@/components/bill/BillListPage.vue'
import BillHeaderActions from '@/components/bill/BillHeaderActions.vue'
import AuditColumns from '@/components/AuditColumns.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { exportListAsSeen, downloadTemplate as downloadTemplateUtil, importData } from '@/utils/exportUtil'
// 筛选条件自定义（2026-10-14 全面推广）：条件定义共享工厂 + 明细报表 API（取明细模式专属条件元数据）
import { stdDefs, fld, detailFilterDefsOf } from '@/config/billFilterDefs'
import { createBillDetailReportApi } from '@/api/report/billDetailReport'

import { useTableSort } from '@/composables/useTableSort'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('finance:operation-expense')
const { handleSortChange, getSortParams } = useTableSort()

/**
 * 批量操作声明（2026-09-20）：UI/确认/执行/结果面板全部由通用壳承担，这里只声明"怎么执行"。
 * 命名口径全系统统一：批量提交 / 批量审批 / 批量重置草稿 / 批量删除。
 * 注 1：本页状态为中文枚举（草稿/待审核/已生效/已作废），故状态判断用中文字面量。
 * 注 2：财务模块在库里只有菜单级码 finance:expense:view 等，没有 :audit / :delete 按钮码，
 *       **已接入 perm**（2026-09-19）：按钮码与设置中心按钮权限同源（finance:xxx:动作），
 *       库中该模块菜单码为模块码形式（无 :view），故与本页按钮码同前缀、不存在臆造问题；
 *       默认口径 = 该对象下未配置任何按钮时命中菜单码即放行（能看见菜单就有全部按钮）。
 * 注 3：本页没有「导出」按钮，故不声明「批量导出」。
 */
const batchActions = [
  {
    key: 'submit',
    label: tt('批量提交'),
    type: 'primary',
    perm: 'finance:operation-expense:submit',
    exec: (row) => submitExpense(row.id),
    // 已提交/已生效的自动跳过（不报错，进"跳过清单"说明原因）
    skipReason: (row) => (row.status && row.status !== '草稿'
      ? tt('当前状态：') + row.status + tt('，不是草稿，无需提交') : '')
  },
  {
    key: 'approve',
    label: tt('批量审批'),
    perm: 'finance:operation-expense:audit',
    exec: (row) => approveExpense(row.id),
    skipReason: (row) => (row.status !== '待审核'
      ? tt('当前状态：') + (row.status || '-') + tt('，不是待审核，无需审批') : '')
  },
  {
    // 批量重置草稿：与编辑页「重置草稿」同一个后端入口（/reverse-approve），
    // 能不能重置全部由后端判断，被拒原话进"失败清单"，与单条操作口径一致
    key: 'reverse',
    label: tt('批量重置草稿'),
    perm: 'finance:operation-expense:reverseApprove',
    exec: (row) => reverseApproveExpense(row.id)
  },
  {
    key: 'delete',
    label: tt('批量删除'),
    type: 'danger',
    perm: 'finance:operation-expense:delete',
    exec: (row) => deleteExpense(row.id),
    // 状态冲突：选中行含非草稿 → 按钮置灰并说明原因（批量场景"隐藏"会让用户找不到入口，故用置灰+原因）
    disabledReason: (rows) => (rows.some(r => r.status && r.status !== '草稿' && r.status !== '已驳回' && r.status !== '已取消')
      ? tt('非草稿不能删除，请先重置草稿') : '')
  }
]

const route = useRoute()

const onSortChange = (e) => { handleSortChange(e); fetchData() }
// 列定义（用于列设置抽屉+动态渲染）= 表格全部数据列
// type 标记单元格渲染分支：billNo 单号链接、expenseType 费用类型标签、net 金额、status 状态标签
const columns = [
  { prop: 'expenseNo', label: '费用单号', minWidth: 170, sortable: true, fixed: 'left', type: 'billNo' },
  { prop: 'expenseType', label: '费用类型', width: 120, type: 'expenseType' },
  { prop: 'expenseDate', label: '日期', width: 120, sortable: true },
  { prop: 'amount', label: '费用合计', width: 140, align: 'right', type: 'net' },
  { prop: 'applicantName', label: '申请人', width: 100, sortable: true },
  { prop: 'departmentName', label: '部门', width: 110, sortable: true },
  { prop: 'allocationMethod', label: '分摊方式', width: 120 },
  { prop: 'status', label: '状态', width: 110, type: 'status' },
  { prop: 'remark', label: '备注', minWidth: 160, sortable: true, tooltip: true }
]
const { columnSettingVisible, isColVisible, getColFixed, configVersion, sortedColumns } = useColumnSetting('OperationExpenseList', columns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))

const loading = ref(false)
const tableData = ref([])
// 统一入参名：keyword / status / startDate / endDate / employeeId（申请人 applicant_id）
// （本页不涉及客户/供应商维度，不声明；expenseType 费用类型 → 保留在 #search-extra 扩展槽）
const search = reactive({ keyword: '', status: '', startDate: '', endDate: '', expenseType: '', employeeId: '' })
const pagination = reactive({ current: 1, size: 20, total: 0, ...getSortParams() })
const selectedRows = ref([])
// 通用搜索栏「单据状态」下拉选项：与后端真实写入的状态值逐字一致（草稿/待审核/已生效/已作废）
const statusOptions = [
  { label: '草稿', value: '草稿' },
  { label: '待审核', value: '待审核' },
  { label: '已生效', value: '已生效' },
  { label: '已作废', value: '已作废' }
]
// 统计卡片高亮（点卡片筛选列表时由 onStatClick 控制）
const activeStatKey = ref('')

const printVisible = ref(false)
const printBillId = ref(null)

// 页头「打印」：一次只能打印一张（与销售订单口径一致）
function handleBatchPrint(rows) {
  const picked = (rows && rows.length ? rows : selectedRows.value) || []
  if (!picked.length) { ElMessage.warning(tt('请先勾选要打印的单据')); return }
  if (picked.length > 1) { ElMessage.warning(tt('一次只能打印一张单据，请仅勾选一行')); return }
  printBillId.value = picked[0].expenseId
  printVisible.value = true
}

// 导入相关
const importDialogVisible = ref(false)
const downloadingTemplate = ref(false)
const downloadingCustomTemplate = ref(false)
const importing = ref(false)
const selectedImportFile = ref(null)
const importResult = ref(null)
const uploadRef = ref()
const fieldPanelActive = ref('')
const templateFields = reactive({ required: [], optional: [] })
const checkedOptionalFields = ref([])

// 常量
const expenseTypeOptions = ['人工费用', '制造费用', '水电费', '办公费', '出差', '宴请', '快递', '财务费用', '其他']

// ============ 筛选条件自定义（2026-10-14 全面推广 · 销售订单试点标准） ============
// 条件候选 = 标准维度（状态/费用日期/申请人，std 标记 → 值走页面 search 通道，与统计卡互通）
//          + 表头字段（f_<key> 参数化，后端白名单翻译，键与明细报表引擎同键）。
// 「费用类型」自本批起迁入条件定义（原扩展搜索槽在声明 filterDefs 后不再渲染），
// 值经 f_expenseType 通道走后端白名单；数据看板穿透写入的 search.expenseType 旧通道保持不变。
const filterDefs = computed(() => [
  ...stdDefs({
    status: { options: statusOptions },
    date: { key: 'expenseDate', label: '费用日期' },
    employee: { key: 'applicantName', label: '申请人' }
  }),
  fld('text', 'expenseNo', '费用单号'),
  { ...fld('select', 'expenseType', '费用类型'), options: expenseTypeOptions.map(t => ({ value: t, label: t })) },
  fld('text', 'allocationMethod', '分摊方式'),
  fld('text', 'relatedOrderType', '关联单据类型'),
  fld('text', 'relatedOrderNo', '关联单据编号'),
  fld('select', 'deptName', '部门', 'department'),
  { ...fld('select', 'closeStatus', '结案状态'), options: [{ value: 0, label: '未结案' }, { value: 1, label: '已结案' }] },
  fld('text', 'remark', '备注'),
  fld('numberRange', 'amount', '费用合计')
])

// 明细模式专属条件 = 费用分摊明细报表 /columns 的 filters 元数据（键=引擎同键）；
// STD_BY_KEY 声明引擎筛选键 → 标准维度（默认勾选=现状、值走页面 search 通道）
const detailFiltersMeta = ref([])
const STD_BY_KEY = { status: 'status', expenseDate: 'date', applicantName: 'employee' }
const detailFilterDefs = computed(() => detailFilterDefsOf(detailFiltersMeta.value, STD_BY_KEY))
// 明细报表 API（与列表页明细模式双视图同一基础路径）
const operationExpenseDetailApi = createBillDetailReportApi('/report/finance/operation-expense-detail')

/** 自定义条件参数（由通用壳经 filter-params 下发）：params 汇总用 / detailParams 明细用 */
const headerFilterState = ref({ params: {}, customSearch: undefined, detailParams: {} })
function onHeaderFilterParams(payload) {
  headerFilterState.value = payload || { params: {}, customSearch: undefined, detailParams: {} }
}

// 顶部统计卡片：数据来自后端 /api/finance/operation-expense/dashboard（与列表逐字同数据权限口径）
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
  { key: 'month', label: '本月费用', value: 0, type: 'primary', icon: 'Document', filter: monthRange() },
  { key: 'draft', label: '草稿', value: 0, type: 'warning', icon: 'EditPen', filter: { status: '草稿' } },
  { key: 'pending', label: '待审核', value: 0, type: 'info', icon: 'Clock', filter: { status: '待审核' } },
  { key: 'approved', label: '已生效', value: 0, type: 'success', icon: 'CircleCheck', filter: { status: '已生效' } }
])

async function loadStats() {
  try {
    const d = (await getOperationExpenseDashboard()) || {}
    stats.value = [
      { key: 'month', label: '本月费用', value: d.monthCount ?? 0, type: 'primary', icon: 'Document', filter: monthRange() },
      { key: 'draft', label: '草稿', value: d.draftCount ?? 0, type: 'warning', icon: 'EditPen', filter: { status: '草稿' } },
      { key: 'pending', label: '待审核', value: d.pendingCount ?? 0, type: 'info', icon: 'Clock', filter: { status: '待审核' } },
      { key: 'approved', label: '已生效', value: d.approvedCount ?? 0, type: 'success', icon: 'CircleCheck', filter: { status: '已生效' } }
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

function typeTagType(t) {
  const map = {
    '人工费用': '',
    '制造费用': 'warning',
    '水电费': 'warning',
    '办公费': 'info',
    '出差': 'success',
    '宴请': 'success',
    '快递': 'success',
    '其他': 'info'
  }
  return map[t] !== undefined ? map[t] : 'info'
}

function statusTagType(s) {
  const map = { '草稿': 'info', '待审核': 'warning', '已生效': 'success', '已作废': 'danger' }
  return map[s] || 'info'
}

function statusDotClass(s) {
  const map = { '草稿': 'dot-info', '待审核': 'dot-warning', '已生效': 'dot-success', '已作废': 'dot-danger' }
  return map[s] || 'dot-info'
}

// 列表查询参数（列表加载与导出共用同一套，避免两处口径漂移）
function buildQueryParams() {
  return {
    ...search,
    // 筛选条件自定义（2026-10-14）：f_* 表头字段过滤参数由通用壳统一下发
    ...(headerFilterState.value.params || {}),
    ...getSortParams()
  }
}

async function fetchData() {
  loading.value = true
  try {
    // 统一入参 + 筛选条件自定义（2026-10-14）：f_* 表头字段过滤参数由通用壳统一下发
    const res = await pageExpense({ current: pagination.current, size: pagination.size, ...buildQueryParams() })
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (e) {
    ElMessage.error(e.message || tt('加载失败'))
  } finally {
    loading.value = false
  }
}

async function handleExport() {
  try {
    // 页面上看到什么列，导出就带什么列；行 = 当前筛选条件下的全部
    await exportListAsSeen({
      entityName: 'operationExpense',
      columns: visibleSortedColumns.value,
      fetchPage: pageExpense,
      params: buildQueryParams(),
      idField: 'expenseId'
    })
    ElMessage.success(tt('导出成功'))
  } catch (e) {
    // 失败原因已由 exportUtil 统一提示
  }
}

// 通用搜索栏（状态/日期）变更：先把值写回本页 search
// 注：BillSearchBar 变更后会紧接着抛 search 事件（壳已转发 → handleSearch），这里不重复发起查询
function onFilterChange(patch) {
  Object.assign(search, patch || {})
}

function handleSearch() { pagination.current = 1; fetchData() }

function handleReset() {
  Object.assign(search, { keyword: '', status: '', startDate: '', endDate: '', expenseType: '', employeeId: '' })
  activeStatKey.value = ''   // 同时取消统计卡片的高亮
  pagination.current = 1
  fetchData()
}

async function loadTemplateFields() {
  try {
    const resp = await fetch(`/api/export/template/operationExpense/fields`, { headers: { Authorization: `Bearer ${localStorage.getItem('token') || ''}` } })
    const json = await resp.json()
    if (json.code === 200 && json.data) {
      templateFields.required = json.data.required || []
      templateFields.optional = json.data.optional || []
      checkedOptionalFields.value = []
    }
  } catch (e) { /* 静默失败 */ }
}

function selectAllOptional() { checkedOptionalFields.value = [...templateFields.optional] }
function clearOptional() { checkedOptionalFields.value = [] }

async function downloadCustomTemplate() {
  downloadingCustomTemplate.value = true
  try {
    await downloadTemplateUtil('operationExpense', checkedOptionalFields.value)
    ElMessage.success(tt('自定义模板下载成功'))
  } catch (e) {
    ElMessage.error(tt('自定义模板下载失败'))
  } finally {
    downloadingCustomTemplate.value = false
  }
}

const handleImport = () => {
  importDialogVisible.value = true
  importResult.value = null
  selectedImportFile.value = null
  if (uploadRef.value) {
    uploadRef.value.clearFiles()
  }
  loadTemplateFields()
}

const downloadTemplate = async () => {
  downloadingTemplate.value = true
  try {
    await downloadTemplateUtil('operationExpense')
    ElMessage.success(tt('模板下载成功'))
  } catch (err) {
    ElMessage.error(tt('模板下载失败'))
  } finally {
    downloadingTemplate.value = false
  }
}

const handleFileChange = (file) => {
  selectedImportFile.value = file.raw
  importResult.value = null
}

const confirmImport = async () => {
  if (!selectedImportFile.value) {
    ElMessage.warning(tt('请先选择文件'))
    return
  }
  importing.value = true
  try {
    const result = await importData('operationExpense', selectedImportFile.value)
    importResult.value = result
    if (result.failCount === 0) {
      ElMessage.success(`${tt("导入成功：共 ")}${result.successCount}${tt(" 条记录")}`)
      importDialogVisible.value = false
      fetchData()
    } else {
      ElMessage.warning(`${tt("导入完成：成功 ")}${result.successCount}${tt(" 条，失败 ")}${result.failCount}${tt(" 条")}`)
    }
  } catch (err) {
    ElMessage.error(tt('导入失败：') + (err.message || tt('未知错误')))
  } finally {
    importing.value = false
  }
}

onMounted(() => {
  // 数据看板穿透：URL query 注入费用类型/日期区间（无 query 时行为完全不变）
  const q = route.query || {}
  let hit = false
  if (q.expenseType) { search.expenseType = String(q.expenseType); hit = true }
  if (q.startDate && q.endDate) { search.startDate = String(q.startDate); search.endDate = String(q.endDate); hit = true }
  if (hit) ElMessage.info(tt('已应用数据看板穿透筛选条件'))
  loadStats()
  fetchData()
  // 明细模式专属筛选条件元数据（明细报表 /columns 的 filters；失败静默：明细条件集为空时明细模式沿用汇总条件集）
  operationExpenseDetailApi.getColumns()
    .then(res => { detailFiltersMeta.value = (res && res.filters) || [] })
    .catch(() => { /* 静默降级 */ })
})
useAutoRefresh(() => { fetchData(); loadStats() })
</script>

<style scoped lang="scss">
.operation-expense-page {
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
  gap: 12px;
  flex-wrap: wrap;

  .search-right {
    display: flex;
    gap: 8px;
    align-items: center;
    flex-wrap: wrap;
  }

  .search-input {
    width: 240px;
    :deep(.el-input__wrapper) {
      border-radius: 8px;
      box-shadow: 0 0 0 1px #e5e7eb inset;
      &:hover { box-shadow: 0 0 0 1px #c7d2fe inset; }
      &.is-focus { box-shadow: 0 0 0 1px #6366f1 inset; }
    }
  }

  .search-select {
    :deep(.el-select__wrapper) {
      border-radius: 8px;
    }
  }

  /* 「费用类型」扩展槽（#search-extra）：与壳内其它筛选维度同样式（标签 60px + 控件撑满） */
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

    > .el-select {
      flex: 1 1 auto;
      width: auto !important;
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

.net-amount {
  color: #6366f1;
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
  .operation-expense-page { padding: 12px; }
  .stat-cards { grid-template-columns: 1fr; }
  .search-bar {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }
  .status-tabs { flex-wrap: wrap; }
}

.import-dialog-content {
  padding: 4px 0;
}
.import-step {
  margin-bottom: 8px;
}
.step-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 8px;
}
.step-desc {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 6px;
  line-height: 1.6;
}
.import-uploader {
  margin-top: 8px;
}
.import-result {
  margin-top: 16px;
}
.result-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 12px;
}
.import-error-alert {
  margin-top: 12px;
}
.error-list {
  max-height: 200px;
  overflow-y: auto;
  margin-top: 8px;
}
.error-item {
  padding: 4px 8px;
  background: #fef0f0;
  border-radius: 3px;
  margin-bottom: 4px;
  font-size: 13px;
  color: #f56c6c;
}
.error-more {
  padding: 4px 8px;
  font-size: 12px;
  color: #909399;
  text-align: center;
}
</style>
