<template>
  <!-- 通用列表壳（页头/搜索条/表格外壳/分页/列设置/批量操作条）
       筛选条件自定义（2026-10-14）：标准维度只声明「单据状态」（分页接口的既有入参）；
       其余表头字段走 f_* 白名单参数（后端 /page 已支持），自定义字段走 customSearch 既有通道 -->
  <BillListPage
    page-class="erp-page"
    back-path="/freesample"
    :title="$tt('免费样品单')"
    sub-title="免费样品登记与发货跟踪"
    create-path="/freesample/edit"
    :active-stat-key="activeStatKey"
    search-placeholder="单号/客户名称"
    keyword-label="单据编号"
    :filters="['status']"
    :status-options="statusOptions"
    :filter-model="search"
    :filter-defs="filterDefs"
    :detail-filter-defs="detailFilterDefs"
    :table-data="tableData"
    :loading="loading"
    :pagination="pagination"
    storage-key="FreeSampleList"
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
        :add-text="$tt('新增样品单')"
        :show-print="false"
        @add="handleAdd"
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
          <el-link v-if="col.type === 'billNo'" type="primary" @click="handleEdit(row)">{{ row[col.prop] }}</el-link>
          <span v-else-if="col.type === 'amount'">{{ Number(row[col.prop] || 0).toFixed(2) }}</span>
          <el-tag v-else-if="col.type === 'status'" :type="statusTagType(row.status)" size="small">{{ $dt(row.status) }}</el-tag>
        </template>
      </el-table-column>
    </template>
    <AuditColumns />
  </BillListPage>

  <!-- 导入（表头 + 明细，模板来自通用登记表） -->
  <BillImportDialog v-model="importVisible" entity-name="freeSampleOrder" :title="$tf('免费样品订单导入')" @done="loadData" />

  <!-- 发货弹窗 -->
  <el-dialog v-model="shipVisible" :title="$tf('发货登记')" width="450px">
    <el-form label-width="100px">
      <el-form-item :label="$tf('单号')">
        <span>{{ currentRow?.sampleNo }}</span>
      </el-form-item>
      <el-form-item :label="$tf('快递公司')" required>
        <el-input v-model="shipForm.expressCompany" :placeholder="$tt('请输入快递公司')" />
      </el-form-item>
      <el-form-item :label="$tf('快递单号')" required>
        <el-input v-model="shipForm.expressNo" :placeholder="$tt('请输入快递单号')" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button v-permission="perm('dlgCancel')" @click="shipVisible = false">{{ $tt("取消") }}</el-button>
      <el-button type="primary" v-permission="perm('ship')" @click="confirmShip" :loading="saving">{{ $tt("确认发货") }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  pageFreeSample, shipFreeSample,
  submitFreeSample, approveFreeSample, reverseApproveFreeSample, deleteFreeSample
} from '@/api/freesample/freeSample'
import BillListPage from '@/components/bill/BillListPage.vue'
import BillHeaderActions from '@/components/bill/BillHeaderActions.vue'
import BillImportDialog from '@/components/bill/BillImportDialog.vue'
import { exportListAsSeen } from '@/utils/exportUtil'
import AuditColumns from '@/components/AuditColumns.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { useTableSort } from '@/composables/useTableSort'
import { useCustomFieldSearch } from '@/composables/useCustomFieldSearch'
// 筛选条件自定义（2026-10-14 全面推广）：条件定义共享工厂 + 明细报表 API（取明细模式专属条件元数据）
import { stdDefs, fld, cfDefs, detailFilterDefsOf } from '@/config/billFilterDefs'
import { createBillDetailReportApi } from '@/api/report/billDetailReport'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'
const { handleSortChange, getSortParams } = useTableSort()
const perm = usePagePerm()

// 自定义字段「可搜索」：设置中心勾了可搜索的字段才渲染搜索框（无字段时与改造前完全一致）
const cf = useCustomFieldSearch('free_sample_order')

const router = useRouter()

/**
 * 批量操作声明（2026-09-20）：UI/确认/执行/结果面板全部由通用壳承担，这里只声明"怎么执行"。
 * 本页状态为中文字符串：草稿 / 待审核 / 已生效 / 已发货 / 已完成 / 已作废。
 * 主键：列表已把后端实体主键 orderId 映射为 sampleId（见 loadData），接口按主键取单，故批量动作传 row.sampleId。
 * skipReason/disabledReason 口径取自后端 FreeSampleOrderServiceImpl 的真实校验：
 *   提交=仅草稿；审批=草稿或待审核；重置草稿=仅已生效；删除=仅草稿。
 * perm 一律不写：本页在 sys_menu 里没有独立权限码（见 utils/billPermCode.js 的 32 条登记表，
 * 免费样品单/打样单均未收录）→ 写了会在 v-permission 的严格模式判定下把按钮静默隐藏。
 */
const batchActions = [
  {
    key: 'submit',
    label: tt('批量提交'),
    type: 'primary',
    exec: (row) => submitFreeSample(row.sampleId),
    // 只有草稿可提交，其余状态自动跳过（不报错，进"跳过清单"说明原因）
    skipReason: (row) => (row.status !== '草稿'
      ? tt('当前状态：') + row.status + tt('，不是草稿，无需提交') : '')
  },
  {
    key: 'approve',
    label: tt('批量审批'),
    exec: (row) => approveFreeSample(row.sampleId),
    skipReason: (row) => (!['草稿', '待审核'].includes(row.status)
      ? tt('当前状态：') + row.status + tt('，不是待审核，无需审批') : '')
  },
  {
    key: 'reverse',
    label: tt('批量重置草稿'),
    exec: (row) => reverseApproveFreeSample(row.sampleId),
    skipReason: (row) => (row.status !== '已生效'
      ? tt('当前状态：') + row.status + tt('，不是已生效，不可重置草稿') : '')
  },
  {
    key: 'delete',
    label: tt('批量删除'),
    type: 'danger',
    exec: (row) => deleteFreeSample(row.sampleId),
    // 状态冲突：选中行含非草稿 → 按钮置灰并说明原因（后端仅允许草稿删除）
    disabledReason: (rows) => (rows.some(r => r.status !== '草稿' && r.status !== '已驳回' && r.status !== '已取消')
      ? tt('非草稿不能删除，请先重置草稿') : '')
  }
]

// 列定义（用于列设置抽屉+动态渲染）
// type 标记单元格渲染分支：billNo 单号链接、amount 金额、status 状态标签
const columns = [
  { prop: 'sampleNo', label: '单号', width: 170, fixed: 'left', sortable: true, type: 'billNo' },
  { prop: 'customerName', label: '客户', minWidth: 160, tooltip: true, sortable: true },
  { prop: 'sampleDate', label: '日期', width: 120, sortable: true },
  { prop: 'totalQuantity', label: '总数量', width: 100, align: 'right', sortable: true },
  { prop: 'totalAmount', label: '金额', width: 120, align: 'right', type: 'amount' },
  { prop: 'expressNo', label: '快递单号', width: 160, tooltip: true, sortable: true },
  { prop: 'status', label: '状态', width: 100, type: 'status' },
  { prop: 'remark', label: '备注', minWidth: 140, tooltip: true, sortable: true }
]
const { columnSettingVisible, isColVisible, getColFixed, configVersion, sortedColumns } = useColumnSetting('FreeSampleList', columns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))
const importVisible = ref(false)

/** 导出当前筛选结果（与列表同口径，含当前可见列）；行字段先按列表口径映射（orderNo→sampleNo） */
async function handleExport() {
  const mapRows = (res) => ({
    ...res,
    records: (res.records || []).map(r => ({
      ...r,
      sampleId: r.orderId ?? r.sampleId ?? r.id,
      sampleNo: r.orderNo ?? r.sampleNo ?? ''
    }))
  })
  try {
    await exportListAsSeen({
      entityName: 'freeSampleOrder',
      columns: visibleSortedColumns.value,
      fetchPage: async (params) => mapRows(await pageFreeSample(params)),
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
const saving = ref(false)
const tableData = ref([])
// 统一入参名：keyword / status（状态为后端真实写入的中文字符串）
const search = reactive({ keyword: '', status: '' })
const pagination = reactive({ current: 1, size: 20, total: 0 })
const selectedRows = ref([])
// 统计卡片高亮（本页无统计卡，保留壳契约字段）
const activeStatKey = ref('')

// 单据状态下拉选项：value 直接用后端真实写入的中文字符串（沿用本页原有选项清单）
const statusOptions = [
  { label: '草稿', value: '草稿' },
  { label: '已提交', value: '已提交' },
  { label: '已生效', value: '已生效' },
  { label: '已发货', value: '已发货' },
  { label: '已完成', value: '已完成' },
  { label: '已取消', value: '已取消' }
]

// ============ 筛选条件自定义（2026-10-14 全面推广 · 销售订单试点标准） ============
// 条件候选 = 标准维度（仅单据状态，std 标记 → 值走页面 search 通道）
//          + 表头字段（f_<key> 参数化，后端白名单翻译，键与明细报表引擎同键）
//          + 单据自定义字段（设置中心勾「可搜索」，沿用 customSearch 既有通道）。
// 仓库为文本模糊（实体只有 warehouse_id 外键，通用壳无仓库主档下拉来源）
const filterDefs = computed(() => [
  ...stdDefs({ status: { options: statusOptions } }),
  fld('text', 'orderNo', '单号'),
  fld('select', 'customerName', '客户', 'customer'),
  fld('select', 'salerName', '业务员', 'employee'),
  fld('text', 'warehouseName', '仓库'),
  fld('dateRange', 'orderDate', '单据日期'),
  fld('dateRange', 'deliveryDate', '发货日期'),
  fld('text', 'expressCompany', '快递公司'),
  fld('text', 'expressNo', '快递单号'),
  fld('numberRange', 'totalQuantity', '总数量'),
  fld('numberRange', 'totalAmount', '金额'),
  fld('text', 'remark', '备注'),
  ...cfDefs(cf)
])

// 明细模式专属条件 = 免费样品订单明细报表 /columns 的 filters 元数据（键=引擎同键）；
// STD_BY_KEY 声明引擎筛选键 → 标准维度（默认勾选=现状、值走页面 search 通道）
const detailFiltersMeta = ref([])
const STD_BY_KEY = { status: 'status' }
const detailFilterDefs = computed(() => detailFilterDefsOf(detailFiltersMeta.value, STD_BY_KEY))
// 明细报表 API（与明细报表页同一基础路径）
const freeSampleDetailApi = createBillDetailReportApi('/report/sample/free-sample-detail')

/** 自定义条件参数（由通用壳经 filter-params 下发）：params 汇总用 / customSearch 自定义字段 */
const headerFilterState = ref({ params: {}, customSearch: undefined, detailParams: {} })
function onHeaderFilterParams(payload) {
  headerFilterState.value = payload || { params: {}, customSearch: undefined, detailParams: {} }
}

function statusTagType(s) {
  const map = { '草稿': 'info', '已提交': 'warning', '已生效': 'primary', '已发货': 'warning', '已完成': 'success', '已取消': 'danger' }
  return map[s] || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageFreeSample({
      current: pagination.current,
      size: pagination.size,
      keyword: search.keyword || undefined,
      status: search.status || undefined,
      ...getSortParams(),
      // 筛选条件自定义（2026-10-14）：f_* 表头字段过滤参数由通用壳统一下发
      ...(headerFilterState.value.params || {}),
      // 单据自定义字段搜索：沿用既有 customSearch 通道（值由通用壳从筛选条件统一打包）
      ...(headerFilterState.value.customSearch ? { customSearch: headerFilterState.value.customSearch } : {})
    })
    // 后端实体字段为 orderId/orderNo，前端统一映射为 sampleId/sampleNo
    tableData.value = (res.records || []).map(r => ({
      ...r,
      sampleId: r.orderId ?? r.sampleId ?? r.id,
      sampleNo: r.orderNo ?? r.sampleNo ?? ''
    }))
    pagination.total = res.total || 0
  } catch (e) {
    ElMessage.error(e.message || tt('加载失败'))
  } finally {
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

function handleAdd() {
  router.push('/freesample/edit')
}

function handleEdit(row) {
  router.push(`/freesample/edit?id=${row.sampleId || row.id}`)
}

// 发货
const shipVisible = ref(false)
const currentRow = ref(null)
const shipForm = reactive({ expressCompany: '', expressNo: '' })

function handleShip(row) {
  currentRow.value = row
  shipForm.expressCompany = row.expressCompany || ''
  shipForm.expressNo = row.expressNo || ''
  shipVisible.value = true
}

async function confirmShip() {
  if (!shipForm.expressCompany || !shipForm.expressNo) {
    ElMessage.warning(tt('请填写快递公司和快递单号'))
    return
  }
  saving.value = true
  try {
    await shipFreeSample(currentRow.value.sampleId || currentRow.value.id, shipForm.expressNo, shipForm.expressCompany)
    ElMessage.success(tt('发货成功'))
    shipVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error(e.message || tt('发货失败'))
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  cf.load().catch(() => {})
  loadData()
  // 明细模式专属筛选条件元数据（明细报表 /columns 的 filters；失败静默：明细条件集为空时明细模式沿用汇总条件集）
  try {
    const res = await freeSampleDetailApi.getColumns()
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
