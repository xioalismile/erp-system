<template>
  <!-- 通用列表壳（页头/统计卡/搜索条/表格外壳/分页/打印/列设置）+ 声明式筛选维度
       筛选维度：状态（下拉，值为字符串数字）/ 单据日期区间 / 客户 / 员工（cashier_id → 收款人） -->
  <BillListPage
    page-class="sale-pre-receipt-page"
    back-path="/finance"
    :title="$tt('销售预收款单')"
    badge="PRE RECEIPT"
    sub-title="管理客户预收款单据 · 实时跟踪核销状态"
    create-path="/finance/sale-pre-receipts/new"
    :stats="stats"
    :active-stat-key="activeStatKey"
    search-placeholder="输入单号/客户名称搜索"
    keyword-label="单据编号"
    :filters="['status', 'date', 'customer', 'employee']"
    employee-label="收款人"
    :status-options="statusOptions"
    :filter-model="search"
    :filter-defs="filterDefs"
    :detail-filter-defs="detailFilterDefs"
    :table-data="tableData"
    :loading="loading"
    :pagination="pagination"
    :page-sizes="[20, 50, 100]"
    storage-key="SalePreReceiptList"
    :columns="columns"
    :table-key="configVersion"
    bill-type="SALE_PRE_RECEIPT"
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
        perm-prefix="finance:sale-pre-receipts"
        :add-text="$tt('新增预收款单')"
        :print-disabled="!sel.length"
        :show-export="false"
        @add="$router.push('/finance/sale-pre-receipts/new')"
        @print="handleBatchPrint(sel)"
        @import="handleImport"
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
            :fixed="getColFixed(col.prop)"
          >
            <template v-if="col.type" #default="{ row }">
              <div v-if="col.type === 'billNo'" class="order-no-cell">
                <span class="order-no-text" @click="$router.push(`/finance/sale-pre-receipts/edit/${row.id}`)">
                  {{ row[col.prop] ? 'YSK' + row[col.prop] : row[col.prop] }}
                </span>
                <el-icon class="link-icon"><Link /></el-icon>
              </div>
              <div v-else-if="col.type === 'customer'" class="customer-cell">
                <div class="customer-avatar">{{ (row[col.prop] || '?').charAt(0) }}</div>
                <div class="customer-info">
                  <div class="customer-name">{{ row[col.prop] }}</div>
                </div>
              </div>
              <span v-else-if="col.type === 'amount'" class="amount-text">¥{{ Number(row[col.prop] || 0).toFixed(2) }}</span>
              <span v-else-if="col.type === 'used'" class="used-text">¥{{ Number(row[col.prop] || 0).toFixed(2) }}</span>
              <span v-else-if="col.type === 'remain'" class="remain-amount">¥{{ Number(row[col.prop] || 0).toFixed(2) }}</span>
              <div v-else-if="col.type === 'status'" class="status-cell">
                <span :class="['status-dot', statusDotClass(row.status)]"></span>
                <el-tag size="small" :type="statusType(row.status)" effect="light" class="status-tag">{{ $dt(statusLabel(row.status)) }}</el-tag>
              </div>
            </template>
          </el-table-column>
        </template>
        <AuditColumns />
  </BillListPage>

    <!-- 核销弹窗 -->
    <el-dialog v-model="writeOffDialog" :title="$tf('核销预收款单')" width="700px" :close-on-click-modal="false">
      <div class="write-off-info">
        <div class="info-line"><span class="label">{{ $tt("单号：") }}</span>{{ currentRow.receiptNo }}</div>
        <div class="info-line"><span class="label">{{ $tt("客户：") }}</span>{{ currentRow.customerName }}</div>
        <div class="info-line"><span class="label">{{ $tt("未核销金额：") }}</span><span class="remain-amount">¥{{ Number(currentRow.remainAmount || 0).toFixed(2) }}</span></div>
      </div>
      <el-table :data="writeOffItems" border size="small" class="write-off-table">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column :label="$tf('来源单号')" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.sourceNo" :placeholder="$tt('来源单号')" size="small" />
          </template>
        </el-table-column>
        <el-table-column :label="$tf('来源类型')" width="120">
          <template #default="{ row }">
            <el-select v-model="row.sourceType" :placeholder="$tt('选择类型')" size="small" style="width:100%">
              <el-option :label="$tf('销售订单')" value="sale_order" />
              <el-option :label="$tf('销售出库单')" value="sale_delivery" />
              <el-option :label="$tf('销售发票')" value="sale_invoice" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column :label="$tf('核销金额')" width="140">
          <template #default="{ row }">
            <el-input-number v-model="row.amount" :min="0" :precision="2" size="small" style="width:100%" />
          </template>
        </el-table-column>
      </el-table>
      <el-button v-permission="perm('addRow')" size="small" type="primary" plain style="margin-top:8px;" @click="addWriteOffItem">
        <el-icon><Plus /></el-icon>添加核销行
      </el-button>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="writeOffDialog = false">{{ $tt("取消") }}</el-button>
        <el-button v-permission="perm('dlgConfirm')" type="primary" @click="confirmWriteOff">{{ $tt("确认核销") }}</el-button>
      </template>
    </el-dialog>

    <!-- 打印预览/列设置抽屉已由 BillListPage 壳统一渲染（bill-type / storage-key 传入） -->

    <!-- 导入弹窗 -->
    <el-dialog
      v-model="importDialogVisible"
      :title="$tf('销售预收款单导入')"
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
import { ElMessage } from 'element-plus'
import { Plus, Link, Upload, Download, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { listSalePreReceipt, writeOffSalePreReceipt, getSalePreReceiptDashboard, submitSalePreReceipt, auditSalePreReceipt, reverseApproveSalePreReceipt, deleteSalePreReceipt } from '@/api/finance/salePreReceipt'
import BillListPage from '@/components/bill/BillListPage.vue'
import BillHeaderActions from '@/components/bill/BillHeaderActions.vue'
import AuditColumns from '@/components/AuditColumns.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { downloadTemplate as downloadTemplateUtil, importData } from '@/utils/exportUtil'
// 筛选条件自定义（2026-10-14 全面推广）：条件定义共享工厂 + 明细报表 API（取明细模式专属条件元数据）
import { stdDefs, fld, detailFilterDefsOf } from '@/config/billFilterDefs'
import { createBillDetailReportApi } from '@/api/report/billDetailReport'

import { useTableSort } from '@/composables/useTableSort'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('finance:sale-pre-receipts')
const { handleSortChange, getSortParams } = useTableSort()

/**
 * 批量操作声明（2026-09-20）：UI/确认/执行/结果面板全部由通用壳承担，这里只声明"怎么执行"。
 * 命名口径全系统统一：批量提交 / 批量审批 / 批量重置草稿 / 批量删除。
 * 注 1：本页状态是**数字**（0草稿/1待审核/2已生效/3已核销/7已作废，后端 Integer），
 *       故状态判断用数字字面量；提示语用本页既有的 statusLabel() 转中文，避免弹"当前状态：2"。
 * 注 2：财务模块在库里只有菜单级码 finance:saleprepay:view 等，没有 :audit / :delete 按钮码，
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
    perm: 'finance:sale-pre-receipts:submit',
    exec: (row) => submitSalePreReceipt(row.id),
    // 仅草稿（0）可提交；已提交/已生效/已核销的自动跳过（不报错，进"跳过清单"说明原因）
    skipReason: (row) => (row.status !== null && row.status !== undefined && Number(row.status) !== 0
      ? tt('当前状态：') + statusLabel(row.status) + tt('，不是草稿，无需提交') : '')
  },
  {
    key: 'approve',
    label: tt('批量审批'),
    perm: 'finance:sale-pre-receipts:audit',
    exec: (row) => auditSalePreReceipt(row.id),
    skipReason: (row) => (Number(row.status) !== 1
      ? tt('当前状态：') + statusLabel(row.status) + tt('，不是待审核，无需审批') : '')
  },
  {
    // 批量重置草稿：与编辑页「重置草稿」同一个后端入口（/reverse-approve），
    // 能不能重置全部由后端判断，被拒原话进"失败清单"，与单条操作口径一致
    key: 'reverse',
    label: tt('批量重置草稿'),
    perm: 'finance:sale-pre-receipts:reverseApprove',
    exec: (row) => reverseApproveSalePreReceipt(row.id)
  },
  {
    key: 'delete',
    label: tt('批量删除'),
    type: 'danger',
    perm: 'finance:sale-pre-receipts:delete',
    exec: (row) => deleteSalePreReceipt(row.id),
    // 状态冲突：选中行含非草稿（非 0）→ 按钮置灰并说明原因（批量场景"隐藏"会让用户找不到入口，故用置灰+原因）
    disabledReason: (rows) => (rows.some(r => r.status !== null && r.status !== undefined && Number(r.status) !== 0)
      ? tt('非草稿不能删除，请先重置草稿') : '')
  }
]


const onSortChange = (e) => { handleSortChange(e); fetchData() }
// 列定义（用于列设置抽屉+动态渲染）= 表格全部数据列
// type 标记单元格渲染分支：billNo 单号链接、customer 客户头像、amount/used/remain 金额、status 状态标签
const columns = [
  { prop: 'receiptDate', label: '单据日期', width: 120, sortable: true },
  { prop: 'receiptNo', label: '单号', minWidth: 180, sortable: true, type: 'billNo' },
  { prop: 'customerName', label: '客户', minWidth: 180, sortable: true, type: 'customer' },
  { prop: 'cashierName', label: '收款人', width: 100 },
  { prop: 'accountName', label: '收款账户', minWidth: 140, sortable: true },
  { prop: 'amount', label: '收款金额', width: 120, align: 'right', sortable: true, type: 'amount' },
  { prop: 'usedAmount', label: '已核销', width: 110, align: 'right', sortable: true, type: 'used' },
  { prop: 'remainAmount', label: '未核销', width: 110, align: 'right', sortable: true, type: 'remain' },
  { prop: 'status', label: '状态', width: 130, type: 'status' },
  { prop: 'createBy', label: '创建人', width: 100, sortable: true },
  { prop: 'createTime', label: '创建时间', width: 160, sortable: true }
]
const { columnSettingVisible, isColVisible, getColFixed, configVersion, sortedColumns } = useColumnSetting('SalePreReceiptList', columns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))

const loading = ref(false)
const tableData = ref([])
// 统一入参名：keyword / status / startDate / endDate / customerId（客户维度映射 customer_id 列）/ employeeId（收款人映射 cashier_id 列）
const search = reactive({ keyword: '', status: '', startDate: '', endDate: '', customerId: '', employeeId: '' })
const pagination = reactive({ current: 1, size: 20, total: 0, ...getSortParams() })
const selectedRows = ref([])

const printVisible = ref(false)
const printBillId = ref(null)

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

// 核销弹窗
const writeOffDialog = ref(false)
const currentRow = reactive({})
const writeOffItems = ref([])

// 通用搜索栏「单据状态」下拉选项：后端 status 存数字（0草稿/1待审核/2已生效/3已核销/7已作废），
// 下拉 value 用字符串数字 —— 数字 0 会被通用搜索栏当作「未选择」直接清空筛选
const statusOptions = [
  { label: '草稿', value: '0' },
  { label: '待审核', value: '1' },
  { label: '已生效', value: '2' },
  { label: '已核销', value: '3' },
  { label: '已作废', value: '7' }
]
// 统计卡片高亮（点卡片筛选列表时由 onStatClick 控制）
const activeStatKey = ref('')

// ============ 筛选条件自定义（2026-10-14 全面推广 · 销售订单试点标准） ============
// 条件候选 = 标准维度（状态/收款日期/客户/收款人，std 标记 → 值走页面 search 通道，与统计卡互通）
//          + 表头字段（f_<key> 参数化，后端白名单翻译，键与明细报表引擎同键）。
// 键与「销售预收款单明细报表」引擎筛选键同键 → 汇总/明细两种模式一套条件。
// 注意：本单状态下拉 value 是字符串数字（后端 status 存数字，见 statusOptions 注释）。
const filterDefs = computed(() => [
  ...stdDefs({
    status: { options: statusOptions },
    date: { key: 'receiptDate', label: '收款日期' },
    customer: { key: 'customerName', label: '客户' },
    employee: { key: 'cashierName', label: '收款人' }
  }),
  fld('text', 'receiptNo', '预收款单号'),
  fld('text', 'orderNo', '关联订单号'),
  fld('text', 'accountName', '账户'),
  fld('text', 'paymentMethod', '收款方式'),
  fld('text', 'sourceType', '来源标记'),
  fld('text', 'remark', '备注'),
  { ...fld('select', 'closeStatus', '结案状态'), options: [{ value: 0, label: '未结案' }, { value: 1, label: '已结案' }] },
  fld('numberRange', 'exchangeRate', '汇率'),
  fld('numberRange', 'amount', '收款金额'),
  fld('numberRange', 'baseAmount', '本位币金额'),
  fld('numberRange', 'usedAmount', '已用金额'),
  fld('numberRange', 'remainAmount', '剩余金额')
])

// 明细模式专属条件 = 销售预收款明细报表 /columns 的 filters 元数据（键=引擎同键）；
// STD_BY_KEY 声明引擎筛选键 → 标准维度（默认勾选=现状、值走页面 search 通道）
const detailFiltersMeta = ref([])
const STD_BY_KEY = { status: 'status', receiptDate: 'date', customerName: 'customer', cashierName: 'employee' }
const detailFilterDefs = computed(() => detailFilterDefsOf(detailFiltersMeta.value, STD_BY_KEY))
// 明细报表 API（与列表页明细模式双视图同一基础路径）
const salePreReceiptDetailApi = createBillDetailReportApi('/report/finance/sale-pre-receipt-detail')

/** 自定义条件参数（由通用壳经 filter-params 下发）：params 汇总用 / detailParams 明细用 */
const headerFilterState = ref({ params: {}, customSearch: undefined, detailParams: {} })
function onHeaderFilterParams(payload) {
  headerFilterState.value = payload || { params: {}, customSearch: undefined, detailParams: {} }
}

// 顶部统计卡片：数据来自后端 /api/sale-pre-receipt/dashboard（与列表同数据权限口径）
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
  { key: 'month', label: '本月新增', value: 0, type: 'primary', icon: 'Document', filter: monthRange() },
  { key: 'pending', label: '待审核', value: 0, type: 'warning', icon: 'Clock', filter: { status: '1' } },
  { key: 'approved', label: '已生效', value: 0, type: 'info', icon: 'CircleCheck', filter: { status: '2' } },
  { key: 'writtenOff', label: '已核销', value: 0, type: 'success', icon: 'CircleCheck', filter: { status: '3' } }
])

async function loadStats() {
  try {
    const d = (await getSalePreReceiptDashboard()) || {}
    stats.value = [
      { key: 'month', label: '本月新增', value: d.monthCount ?? 0, type: 'primary', icon: 'Document', filter: monthRange() },
      { key: 'pending', label: '待审核', value: d.pendingCount ?? 0, type: 'warning', icon: 'Clock', filter: { status: '1' } },
      { key: 'approved', label: '已生效', value: d.approvedCount ?? 0, type: 'info', icon: 'CircleCheck', filter: { status: '2' } },
      { key: 'writtenOff', label: '已核销', value: d.writtenOffCount ?? 0, type: 'success', icon: 'CircleCheck', filter: { status: '3' } }
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

// 状态映射
function statusLabel(s) {
  return { 0: '草稿', 1: '待审核', 2: '已生效', 3: '已核销', 7: '已作废' }[Number(s)] || '未知'
}
function statusType(s) {
  return { 0: 'info', 1: 'warning', 2: 'success', 3: 'success', 7: 'danger' }[Number(s)] || 'info'
}
function statusDotClass(s) {
  return { 0: 'dot-info', 1: 'dot-warning', 2: 'dot-success', 3: 'dot-success', 7: 'dot-danger' }[Number(s)] || 'dot-info'
}

// 通用搜索栏（状态/日期/客户）变更：先把值写回本页 search
// 注：BillSearchBar 变更后会紧接着抛 search 事件（壳已转发 → handleSearch），这里不重复发起查询
function onFilterChange(patch) {
  Object.assign(search, patch || {})
}

async function fetchData() {
  loading.value = true
  try {
    // 统一入参 + 筛选条件自定义（2026-10-14）：f_* 表头字段过滤参数由通用壳统一下发
    const res = await listSalePreReceipt({ current: pagination.current, size: pagination.size, ...search, ...(headerFilterState.value.params || {}), ...getSortParams() })
    tableData.value = res.records || []
    pagination.total = res.total || 0
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
  // 重置 = 回到未筛选状态：关键词 + 通用搜索栏全部维度一并清空
  Object.assign(search, { keyword: '', status: '', startDate: '', endDate: '', customerId: '', employeeId: '' })
  activeStatKey.value = ''   // 同时取消统计卡片的高亮
  pagination.current = 1
  fetchData()
}

function handleWriteOff(row) {
  Object.assign(currentRow, row)
  writeOffItems.value = [{ sourceId: null, sourceNo: '', sourceType: 'sale_order', amount: Number(row.remainAmount || 0) }]
  writeOffDialog.value = true
}

function addWriteOffItem() {
  writeOffItems.value.push({ sourceId: null, sourceNo: '', sourceType: 'sale_order', amount: 0 })
}

async function confirmWriteOff() {
  const totalWriteOff = writeOffItems.value.reduce((s, i) => s + Number(i.amount || 0), 0)
  if (totalWriteOff > Number(currentRow.remainAmount || 0)) {
    ElMessage.warning(tt('核销总额不能超过未核销金额'))
    return
  }
  try {
    await writeOffSalePreReceipt(currentRow.id, writeOffItems.value)
    ElMessage.success(tt('核销成功'))
    writeOffDialog.value = false
    fetchData()
  } catch (e) {
    ElMessage.error(e.message || tt('核销失败'))
  }
}

function handlePrint(row) {
  printBillId.value = row.id
  printVisible.value = true
}

// 页头「打印」：一次只能打印一张（与销售订单口径一致）
function handleBatchPrint(rows) {
  const picked = (rows && rows.length ? rows : selectedRows.value) || []
  if (!picked.length) { ElMessage.warning(tt('请先勾选要打印的单据')); return }
  if (picked.length > 1) { ElMessage.warning(tt('一次只能打印一张单据，请仅勾选一行')); return }
  handlePrint(picked[0])
}

async function loadTemplateFields() {
  try {
    const resp = await fetch(`/api/export/template/salePreReceipt/fields`, { headers: { Authorization: `Bearer ${localStorage.getItem('token') || ''}` } })
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
    await downloadTemplateUtil('salePreReceipt', checkedOptionalFields.value)
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
    await downloadTemplateUtil('salePreReceipt')
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
    const result = await importData('salePreReceipt', selectedImportFile.value)
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
  loadStats()
  fetchData()
  // 明细模式专属筛选条件元数据（明细报表 /columns 的 filters；失败静默：明细条件集为空时明细模式沿用汇总条件集）
  salePreReceiptDetailApi.getColumns()
    .then(res => { detailFiltersMeta.value = (res && res.filters) || [] })
    .catch(() => { /* 静默降级 */ })
})
useAutoRefresh(() => { fetchData(); loadStats() })
</script>

<style scoped lang="scss">
.sale-pre-receipt-page {
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

.customer-cell {
  display: flex;
  align-items: center;
  gap: 10px;

  .customer-avatar {
    width: 32px;
    height: 32px;
    border-radius: 8px;
    background: linear-gradient(135deg, #6366f1, #8b5cf6);
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 13px;
    font-weight: 600;
    flex-shrink: 0;
  }

  .customer-name {
    font-size: 13px;
    color: #374151;
    font-weight: 500;
  }
}

.amount-text { color: #374151; font-variant-numeric: tabular-nums; }
.used-text { color: #6b7280; font-variant-numeric: tabular-nums; }
.remain-amount {
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

:deep(.el-dialog) {
  border-radius: 12px;
}

.write-off-info {
  background: #f8faff;
  border: 1px solid #e0e7ff;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 12px;
  display: flex;
  gap: 24px;
  flex-wrap: wrap;

  .info-line {
    font-size: 13px;
    color: #374151;
    .label { color: #6b7280; }
  }
}

.write-off-table {
  font-size: 12px;
}

@media (max-width: 1200px) {
  .stat-cards { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 768px) {
  .sale-pre-receipt-page { padding: 12px; }
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
