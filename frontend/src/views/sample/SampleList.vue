<template>
  <!-- 通用列表壳（页头/搜索条/表格外壳/分页/列设置/批量操作条）
       筛选条件自定义（2026-10-14）：标准维度只声明「单据状态」（分页接口的既有入参）；
       其余表头字段走 f_* 白名单参数（后端 /page 已支持），自定义字段走 customSearch 既有通道 -->
  <BillListPage
    page-class="erp-page"
    back-path="/sample"
    :title="$tt('打样单')"
    sub-title="打样进度跟踪与转BOM管理"
    create-path="/sample/edit"
    :active-stat-key="activeStatKey"
    search-placeholder="打样单号/样品名称/客户/物料"
    keyword-label="单据编号"
    :filters="['status']"
    :status-options="statusOptions"
    :filter-model="search"
    :filter-defs="filterDefs"
    :detail-filter-defs="detailFilterDefs"
    :table-data="tableData"
    :loading="loading"
    :pagination="pagination"
    storage-key="SampleList"
    :columns="columns"
    :table-key="configVersion"
    :page-sizes="[10, 20, 50, 100]"
    v-model:keyword="search.keyword"
    v-model:selected-rows="selectedRows"
    :batch-actions="batchActions"
    v-model:column-setting-visible="columnSettingVisible"
    @search="handleSearch"
    @reset="onReset"
    @filter-change="onFilterChange"
    @filter-params="onHeaderFilterParams"
    @sort-change="onSortChange"
    @page-change="loadData"
  >

    <template #header-actions>
      <!-- 2026-11-07：与销售订单同款统一页头按钮组件 -->
      <BillHeaderActions
        perm-prefix=""
        :add-text="$tt('新增打样单')"
        :show-print="false"
        @add="onAdd"
        @export="handleExport"
        @import="importVisible = true"
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
          <span v-if="col.type === 'billNo'" style="color:#6366f1;cursor:pointer;" @click="onDetail(row)">{{ row[col.prop] }}</span>
          <!-- 打样进度：行内可编辑（选择或直接输入），成功才落地新值 -->
          <el-select
            v-else-if="col.type === 'progress'"
            :model-value="row.progress"
            filterable
            allow-create
            default-first-option
            :placeholder="$tt('选择/输入进度')"
            size="small"
            style="width: 100%"
            @change="(val) => onProgressChange(row, val)"
          >
            <el-option v-for="p in progressOptions" :key="p" :label="p" :value="p" />
          </el-select>
          <el-tag v-else-if="col.type === 'status'" :type="statusType(row.status)" size="small">{{ $dt(row.status) }}</el-tag>
        </template>
      </el-table-column>
    </template>
    <AuditColumns />
  </BillListPage>

  <!-- 导入（表头 + 子件明细，模板来自通用登记表） -->
  <BillImportDialog v-model="importVisible" entity-name="sampleOrder" :title="$tf('打样单导入')" @done="loadData" />
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageSample, deleteSample,
  submitSample, approveSample, reverseApproveSample,
  startSample, completeSample, confirmSample, cancelSample, convertToBom, updateSampleProgress
} from '@/api/sample/sample'
import BillListPage from '@/components/bill/BillListPage.vue'
import BillHeaderActions from '@/components/bill/BillHeaderActions.vue'
import BillImportDialog from '@/components/bill/BillImportDialog.vue'
import { exportListAsSeen } from '@/utils/exportUtil'
import AuditColumns from '@/components/AuditColumns.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { confirmSubmit, submitSuccessMessage } from '@/utils/submit-confirm'
import { useTableSort } from '@/composables/useTableSort'
import { useCustomFieldSearch } from '@/composables/useCustomFieldSearch'
// 筛选条件自定义（2026-10-14 全面推广）：条件定义共享工厂 + 明细报表 API（取明细模式专属条件元数据）
import { stdDefs, fld, cfDefs, detailFilterDefsOf } from '@/config/billFilterDefs'
import { createBillDetailReportApi } from '@/api/report/billDetailReport'
import { tt } from '@/i18n'
const { handleSortChange, getSortParams } = useTableSort()

// 自定义字段「可搜索」：设置中心勾了可搜索的字段才渲染搜索框（无字段时与改造前完全一致）
const cf = useCustomFieldSearch('sample_order')

const router = useRouter()

/**
 * 批量操作声明（2026-09-20）：UI/确认/执行/结果面板全部由通用壳承担，这里只声明"怎么执行"。
 * 本页状态为中文字符串：草稿 / 待审核 / 已生效 / 打样中 / 已完成 / 已确认 / 已转BOM / 已取消。
 * skipReason 口径取自后端 SampleOrderServiceImpl 的真实校验：
 *   提交=仅草稿可提交；审批=草稿或待审核可审批；重置草稿=仅已生效；删除无状态限制。
 * perm 一律不写：本页在 sys_menu 里没有独立权限码（见 utils/billPermCode.js 的 32 条登记表，
 * 打样单/免费样品单均未收录）→ 写了会在 v-permission 的严格模式判定下把按钮静默隐藏。
 */
const batchActions = [
  {
    key: 'submit',
    label: tt('批量提交'),
    type: 'primary',
    exec: (row) => submitSample(row.sampleId),
    // 只有草稿可提交，其余状态自动跳过（不报错，进"跳过清单"说明原因）
    skipReason: (row) => (row.status !== '草稿'
      ? tt('当前状态：') + row.status + tt('，不是草稿，无需提交') : '')
  },
  {
    key: 'approve',
    label: tt('批量审批'),
    exec: (row) => approveSample(row.sampleId),
    skipReason: (row) => (!['草稿', '待审核'].includes(row.status)
      ? tt('当前状态：') + row.status + tt('，不是待审核，无需审批') : '')
  },
  {
    key: 'reverse',
    label: tt('批量重置草稿'),
    exec: (row) => reverseApproveSample(row.sampleId),
    skipReason: (row) => (row.status !== '已生效'
      ? tt('当前状态：') + row.status + tt('，不是已生效，不可重置草稿') : '')
  },
  {
    key: 'delete',
    label: tt('批量删除'),
    type: 'danger',
    exec: (row) => deleteSample(row.sampleId)
  }
]

// 列定义（用于列设置抽屉+动态渲染）
// type 标记单元格渲染分支：billNo 单号链接、progress 行内进度编辑、status 状态标签
const columns = [
  { prop: 'sampleNo', label: '打样单号', minWidth: 160, sortable: true, type: 'billNo' },
  { prop: 'sampleName', label: '样品名称', minWidth: 160, sortable: true },
  { prop: 'sampleType', label: '样品类型', width: 100, sortable: true },
  { prop: 'sampleStage', label: '打样阶段', width: 90, sortable: true },
  { prop: 'customerName', label: '客户', minWidth: 140, sortable: true },
  { prop: 'materialCode', label: '物料编码', minWidth: 110, sortable: true },
  { prop: 'sampleQty', label: '打样数量', width: 100, align: 'right', sortable: true },
  { prop: 'demandDate', label: '需求交期', width: 110, sortable: true },
  { prop: 'planFinishDate', label: '计划完成', width: 110, sortable: true },
  { prop: 'costAmount', label: '成本金额', width: 100, align: 'right', sortable: true },
  { prop: 'status', label: '状态', width: 100, sortable: true, type: 'status' },
  { prop: 'progress', label: '打样进度', width: 140, type: 'progress' }
]
const { columnSettingVisible, isColVisible, getColFixed, configVersion, sortedColumns } = useColumnSetting('SampleList', columns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))
const importVisible = ref(false)

/** 导出当前筛选结果（与列表同口径，含当前可见列） */
async function handleExport() {
  try {
    await exportListAsSeen({
      entityName: 'sampleOrder',
      columns: visibleSortedColumns.value,
      fetchPage: pageSample,
      params: {
        keyword: search.keyword || undefined,
        status: search.status || undefined,
        ...getSortParams(),
        ...(headerFilterState.value.params || {}),
        ...(headerFilterState.value.customSearch ? { customSearch: headerFilterState.value.customSearch } : {})
      },
      idField: 'sampleId'
    })
    ElMessage.success(tt('导出成功'))
  } catch (e) { /* 失败原因已由 exportUtil 统一提示 */ }
}

const loading = ref(false)
const tableData = ref([])
// 统一入参名：keyword / status（状态为后端真实写入的中文字符串）
const search = reactive({ keyword: '', status: '' })
const pagination = reactive({ current: 1, size: 20, total: 0 })
const selectedRows = ref([])
// 统计卡片高亮（本页无统计卡，保留壳契约字段）
const activeStatKey = ref('')

// 单据状态下拉选项：value 直接用后端真实写入的中文字符串
const statusOptions = [
  { label: '草稿', value: '草稿' },
  { label: '待审核', value: '待审核' },
  { label: '已生效', value: '已生效' },
  { label: '打样中', value: '打样中' },
  { label: '已完成', value: '已完成' },
  { label: '已确认', value: '已确认' },
  { label: '已转BOM', value: '已转BOM' },
  { label: '已取消', value: '已取消' }
]

// ============ 筛选条件自定义（2026-10-14 全面推广 · 销售订单试点标准） ============
// 条件候选 = 标准维度（仅单据状态，std 标记 → 值走页面 search 通道，与统计卡互通）
//          + 表头字段（f_<key> 参数化，后端白名单翻译，键与明细报表引擎同键）
//          + 单据自定义字段（设置中心勾「可搜索」，沿用 customSearch 既有通道）。
const filterDefs = computed(() => [
  ...stdDefs({ status: { options: statusOptions } }),
  fld('text', 'sampleNo', '打样单号'),
  fld('text', 'sampleName', '样品名称'),
  fld('text', 'sampleType', '样品类型'),
  fld('text', 'sampleStage', '打样阶段'),
  fld('select', 'customerName', '客户', 'customer'),
  fld('text', 'contactPerson', '联系人'),
  fld('text', 'contactPhone', '联系电话'),
  fld('text', 'materialCode', '物料编码'),
  fld('text', 'materialName', '物料名称'),
  fld('select', 'engineerName', '责任工程师', 'employee'),
  fld('select', 'deptName', '责任部门', 'department'),
  fld('text', 'priority', '优先级'),
  fld('text', 'progress', '打样进度'),
  fld('dateRange', 'demandDate', '需求交期'),
  fld('dateRange', 'planFinishDate', '计划完成'),
  fld('dateRange', 'actualFinishDate', '实际完成'),
  fld('numberRange', 'sampleQty', '打样数量'),
  fld('numberRange', 'costAmount', '成本金额'),
  fld('text', 'remark', '备注'),
  ...cfDefs(cf)
])

// 明细模式专属条件 = 打样单明细报表 /columns 的 filters 元数据（键=引擎同键）；
// STD_BY_KEY 声明引擎筛选键 → 标准维度（默认勾选=现状、值走页面 search 通道）
const detailFiltersMeta = ref([])
const STD_BY_KEY = { status: 'status' }
const detailFilterDefs = computed(() => detailFilterDefsOf(detailFiltersMeta.value, STD_BY_KEY))
// 明细报表 API（与明细报表页同一基础路径）
const sampleOrderDetailApi = createBillDetailReportApi('/report/sample/order-detail')

/** 自定义条件参数（由通用壳经 filter-params 下发）：params 汇总用 / customSearch 自定义字段 */
const headerFilterState = ref({ params: {}, customSearch: undefined, detailParams: {} })
function onHeaderFilterParams(payload) {
  headerFilterState.value = payload || { params: {}, customSearch: undefined, detailParams: {} }
}

const statusType = (s) => ({
  '草稿': 'info', '待审核': 'warning', '已生效': 'primary', '打样中': 'warning',
  '已完成': 'success', '已确认': 'success', '已转BOM': 'success', '已取消': 'danger'
}[s] || 'info')

const canCancel = (s) => s && !['已转BOM', '已取消'].includes(s)

const progressOptions = ['图纸设计', '物料准备', '加工中', '组装调试', '待验收', '已完成']

function hasActions(row) {
  return row.status !== '已取消' && row.status !== '已转BOM'
}

// 进度变化同步到后端：成功才落地新值；已生成BOM时后端拒绝并提示先删除BOM
async function onProgressChange(row, val) {
  try {
    const msg = await updateSampleProgress(row.sampleId, val)
    row.progress = val
    ElMessage.success(msg || tt('进度已更新'))
  } catch (e) {
    ElMessage.error(e.message || tt('进度更新失败'))
  }
}

function onAction(cmd, row) {
  switch (cmd) {
    case 'submit': onSubmit(row); break
    case 'approve': onApprove(row); break
    case 'reverseApprove': onReverseApprove(row); break
    case 'start': onStart(row); break
    case 'complete': onComplete(row); break
    case 'confirm': onConfirm(row); break
    case 'convertBom': onConvertBom(row); break
    case 'cancel': onCancel(row); break
    case 'delete': onDelete(row); break
  }
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageSample({
      current: pagination.current, size: pagination.size,
      keyword: search.keyword || undefined,
      status: search.status || undefined,
      ...getSortParams(),
      // 筛选条件自定义（2026-10-14）：f_* 表头字段过滤参数由通用壳统一下发
      ...(headerFilterState.value.params || {}),
      // 单据自定义字段搜索：沿用既有 customSearch 通道（值由通用壳从筛选条件统一打包）
      ...(headerFilterState.value.customSearch ? { customSearch: headerFilterState.value.customSearch } : {})
    })
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (e) { /* 拦截器已提示 */ } finally {
    loading.value = false
  }
}

function handleSearch() { pagination.current = 1; loadData() }
function onReset() {
  // 重置 = 回到未筛选状态：关键词 + 通用搜索栏（状态）一并清空；
  // 自定义筛选条件（f_*/自定义字段）的值由通用壳在重置时统一清空并下发空参数
  Object.assign(search, { keyword: '', status: '' })
  activeStatKey.value = ''
  pagination.current = 1
  loadData()
}
// 通用搜索栏（状态）变更：先把值写回本页 search
// 注：BillSearchBar 变更后会紧接着抛 search 事件（壳已转发 → handleSearch），这里不重复发起查询
function onFilterChange(patch) {
  Object.assign(search, patch || {})
}
const onSortChange = (e) => { handleSortChange(e); loadData() }

function onAdd() {
  router.push('/sample/edit')
}

function onEdit(row) {
  router.push('/sample/edit/' + row.sampleId)
}

function onDetail(row) {
  router.push('/sample/edit/' + row.sampleId)
}

async function onSubmit(row) {
  try {
    const needApproval = await confirmSubmit({ billType: 'SAMPLE_ORDER', billLabel: '打样单', billNo: row.sampleNo })
    await submitSample(row.sampleId)
    ElMessage.success(submitSuccessMessage(needApproval))
    loadData()
  } catch (e) {}
}

async function onApprove(row) {
  await ElMessageBox.confirm(`${tt("确认审核通过 ")}${row.sampleNo}？`, tt('确认'), { type: 'warning' })
  try { await approveSample(row.sampleId); ElMessage.success(tt('已生效')); loadData() } catch (e) {}
}

async function onReverseApprove(row) {
  await ElMessageBox.confirm(`${tt("确认重置草稿 ")}${row.sampleNo}？`, tt('确认'), { type: 'warning' })
  try { await reverseApproveSample(row.sampleId); ElMessage.success(tt('已重置草稿')); loadData() } catch (e) {}
}

async function onStart(row) {
  await ElMessageBox.confirm(`${tt("确认开始打样 ")}${row.sampleNo}？`, tt('确认'), { type: 'warning' })
  try { await startSample(row.sampleId); ElMessage.success(tt('已开始打样')); loadData() } catch (e) {}
}

async function onComplete(row) {
  await ElMessageBox.confirm(`${tt("确认完成打样 ")}${row.sampleNo}？`, tt('确认'), { type: 'warning' })
  try { await completeSample(row.sampleId); ElMessage.success(tt('已完成')); loadData() } catch (e) {}
}

async function onConfirm(row) {
  await ElMessageBox.confirm(`${tt("确认样品 ")}${row.sampleNo}${tt(" 合格？确认后可转BOM")}`, tt('确认'), { type: 'warning' })
  try { await confirmSample(row.sampleId); ElMessage.success(tt('已确认')); loadData() } catch (e) {}
}

async function onCancel(row) {
  const { value } = await ElMessageBox.prompt(tt('请输入取消原因'), tt('取消打样单'), { type: 'warning' })
  try { await cancelSample(row.sampleId, value); ElMessage.success(tt('已取消')); loadData() } catch (e) {}
}

/** 打样转BOM：核心功能，将已确认的打样单推送生成正式BOM */
async function onConvertBom(row) {
  await ElMessageBox.confirm(
    `${tt("确认将打样单 ")}${row.sampleNo}${tt(" 转化为正式BOM？\\n\\n转化后：\\n1. 系统将以样品为父项，子件清单为BOM子项生成新BOM（草稿状态）\\n2. 打样单状态变更为\"已转BOM\"\\n3. 该操作不可重复执行")}`,
    tt('打样转BOM'), { type: 'warning', confirmButtonText: tt('确认转BOM') }
  )
  try {
    const res = await convertToBom(row.sampleId)
    ElMessage.success(`${tt("转BOM成功！BOM编号: ")}${res.bomCode}`)
    loadData()
  } catch (e) { /* 拦截器已提示 */ }
}

async function onDelete(row) {
  await ElMessageBox.confirm(`${tt("确认删除打样单 ")}${row.sampleNo}？`, tt('确认'), { type: 'warning' })
  try { await deleteSample(row.sampleId); ElMessage.success(tt('删除成功')); loadData() } catch (e) {}
}

onMounted(async () => {
  cf.load().catch(() => {})
  loadData()
  // 明细模式专属筛选条件元数据（明细报表 /columns 的 filters；失败静默：明细条件集为空时明细模式沿用汇总条件集）
  try {
    const res = await sampleOrderDetailApi.getColumns()
    detailFiltersMeta.value = (res && res.filters) || []
  } catch (e) { /* 静默降级 */ }
})
useAutoRefresh(() => {
  loadData()
})
</script>

<style scoped>
.erp-page { padding: 16px; }
</style>
