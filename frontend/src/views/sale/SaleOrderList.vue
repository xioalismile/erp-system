<template>
  <BillListPage
    page-class="sale-order-page"
    back-path="/sale"
    :title="$tt('销售订单')"
    badge="SALE ORDER"
    sub-title="高效管理销售全流程 · 实时追踪订单状态"
    create-path="/sale/orders/new"
    :stats="stats"
    :active-stat-key="activeStatKey"
    search-placeholder="输入订单号/客户名称搜索"
    keyword-label="单据编号"
    :filters="['status', 'date', 'customer', 'employee']"
    :filter-defs="filterDefs"
    :detail-filter-defs="detailFilterDefs"
    :status-options="statusOptions"
    :filter-model="search"
    employee-label="销售员"
    :table-data="tableData"
    :loading="loading"
    :pagination="pagination"
    storage-key="SaleOrderList"
    :columns="columns"
    :table-key="configVersion"
    bill-type="SALE_ORDER"
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
    :detail-enabled="true"
    :detail-columns="detailColumnDefs"
    :detail-fetcher="detailFetcher"
    :detail-exporter="detailExporter"
    :detail-link-builder="detailLinkBuilder"
    :detail-params-builder="detailParams"
    field-perm-key="sale_order"
  >
    <template #header-actions="{ selectedRows: sel }">
      <!-- 页头按钮统一走通用组件（2026-09-19）：按钮码由 perm-prefix 派生，权限控制一处生效；
           sel 已按视图模式统一（明细模式=勾选明细行按单据去重），打印/批量动作两种模式都可用 -->
      <BillHeaderActions
        perm-prefix="sale:orders"
        :add-text="$tt('新增订单')"
        :print-disabled="!sel.length"
        @add="$router.push('/sale/orders/new')"
        @print="handleBatchPrint(sel)"
        @export="handleExport"
        @import="handleImport"
      />
    </template>

    <!-- 业务列（插槽注入壳的 el-table） -->
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
            <span class="order-no-text" @click="$router.push(`/sale/orders/${row.orderId}`)">{{ row[col.prop] }}</span>
            <el-icon class="link-icon"><Link /></el-icon>
          </div>
          <div v-else-if="col.type === 'customer'" class="customer-cell">
            <div class="customer-avatar">{{ (row[col.prop] || '?').charAt(0) }}</div>
            <div class="customer-info">
              <div class="customer-name">{{ row[col.prop] }}</div>
            </div>
          </div>
          <span v-else-if="col.type === 'amount'" class="amount-text">¥{{ Number(row[col.prop] || 0).toFixed(2) }}</span>
          <span v-else-if="col.type === 'discount'" class="discount-text">- {{ Number(row[col.prop] || 0).toFixed(2) }}</span>
          <span v-else-if="col.type === 'netAmount'" class="net-amount">¥{{ Number(row[col.prop] || 0).toFixed(2) }}</span>
          <span v-else-if="col.type === 'percent'">{{ Number(row[col.prop] || 0) }}%</span>
          <div v-else-if="col.type === 'status'" class="status-cell">
            <span :class="['status-dot', statusDotClass(row.status)]"></span>
            <el-tag size="small" :type="statusType(row.status)" effect="light" class="status-tag">{{ $dt(row.status) }}</el-tag>
          </div>
          <el-tag v-else-if="col.type === 'approval'" size="small" :type="approvalStatusType(row.approvalStatus)" effect="plain">{{ $dt(row.approvalStatus || '-') }}</el-tag>
          <!-- 下推状态列（2026-10-10）：发货情况固定三态（未发货/发货中/发货完成）；开票/生产没下推显示空白；点击弹「下推详情」 -->
          <div v-else-if="col.type === 'pushDelivery'" class="push-cell">
            <el-tag size="small" :type="pushTagType(row, col.type)" effect="light" class="push-tag"
                    :title="pushDeliveryTitle(row)" @click="openPushDetail(row)">{{ pushText(row, col.type) }}</el-tag>
            <span v-if="pushDeliverySub(row)" class="push-sub">{{ pushDeliverySub(row) }}</span>
          </div>
          <!-- 收款情况列（2026-10-12）：三态（未收款/收款中/收款完成）+ 已收/订单额副行；点击弹「收款详情」 -->
          <div v-else-if="col.type === 'pushReceipt'" class="push-cell">
            <el-tag size="small" :type="receiptTagType(row)" effect="light" class="push-tag"
                    title="点击查看收款详情" @click="openReceiptDetail(row)">{{ receiptText(row) }}</el-tag>
            <span class="push-sub">{{ receiptSub(row) }}</span>
          </div>
          <div v-else-if="col.type === 'pushInvoice' || col.type === 'pushProduce'" class="push-cell">
            <el-tag v-if="pushText(row, col.type)" size="small" :type="pushTagType(row, col.type)" effect="light"
                    class="push-tag" @click="openPushDetail(row)">{{ pushText(row, col.type) }}</el-tag>
          </div>
        </template>
      </el-table-column>
    </template>
    <AuditColumns />
  </BillListPage>

  <!-- 导入弹窗 -->
  <el-dialog
    v-model="importDialogVisible"
    :title="$tf('销售订单导入')"
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

  <!-- 下推详情弹窗（2026-10-10）：复用编辑页「去向单据」通用面板，按下游单据类型分组展示 + 物料级勾稽 -->
  <el-dialog
    v-model="pushDetailVisible"
    :title="`下推详情${pushDetailNo ? '：' + pushDetailNo : ''}`"
    width="920px"
    :close-on-click-modal="false"
    class="tech-dialog"
    append-to-body
  >
    <BillLinkPanel
      v-if="pushDetailVisible"
      bill-type="SALE_ORDER"
      :bill-id="pushDetailBillId"
      mode="target"
    />
  </el-dialog>

  <!-- 收款详情弹窗（2026-10-12）：明细行级已收 + 收款记录（直接订单收款/发货收款两条路径） -->
  <el-dialog
    v-model="receiptDetailVisible"
    :title="`收款详情${receiptDetailNo ? '：' + receiptDetailNo : ''}`"
    width="860px"
    :close-on-click-modal="false"
    class="tech-dialog"
    append-to-body
  >
    <div v-loading="receiptDetailLoading">
      <div class="receipt-section-title">{{ $tt('明细行收款情况') }}</div>
      <el-table :data="receiptDetailLines" size="small" border max-height="240">
        <el-table-column type="index" :label="$tt('#')" width="50" />
        <el-table-column prop="materialName" :label="$tt('货品名称')" min-width="160" show-overflow-tooltip />
        <el-table-column prop="quantity" :label="$tt('数量')" width="90" align="right" />
        <el-table-column :label="$tt('行金额')" width="120" align="right">
          <template #default="{ row }"><span>¥{{ Number(row.netAmount || 0).toFixed(2) }}</span></template>
        </el-table-column>
        <el-table-column :label="$tt('已收')" width="120" align="right">
          <template #default="{ row }"><span class="net-amount">¥{{ Number(row.receivedAmount || 0).toFixed(2) }}</span></template>
        </el-table-column>
        <el-table-column :label="$tt('未收')" width="120" align="right">
          <template #default="{ row }"><span>¥{{ (Number(row.netAmount || 0) - Number(row.receivedAmount || 0)).toFixed(2) }}</span></template>
        </el-table-column>
      </el-table>
      <div class="receipt-section-title" style="margin-top:14px;">{{ $tt('收款记录') }}</div>
      <el-empty v-if="!receiptDetailLoading && receiptDetailRecords.length === 0" :description="$tt('该订单还没有收款记录')" :image-size="60" />
      <el-table v-else :data="receiptDetailRecords" size="small" border max-height="220">
        <el-table-column prop="receiptNo" :label="$tt('收款单号')" min-width="160" />
        <el-table-column prop="writeoffDate" :label="$tt('收款日期')" width="110" />
        <el-table-column :label="$tt('收款路径')" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.path === '发货收款' ? 'warning' : 'primary'" effect="plain">{{ row.path }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="arNo" :label="$tt('应收单号')" width="150">
          <template #default="{ row }"><span>{{ row.arNo || '' }}</span></template>
        </el-table-column>
        <el-table-column prop="itemName" :label="$tt('货品（明细收款）')" min-width="120">
          <template #default="{ row }"><span>{{ row.itemName || '' }}</span></template>
        </el-table-column>
        <el-table-column :label="$tt('收款金额')" width="120" align="right">
          <template #default="{ row }"><span class="net-amount">¥{{ Number(row.amount || 0).toFixed(2) }}</span></template>
        </el-table-column>
      </el-table>
    </div>
  </el-dialog>
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Download, Upload, CircleCheck, CircleClose, Printer, Link } from '@element-plus/icons-vue'
import { listSaleOrder, getSaleOrder, getSaleOrderDashboard, submitSaleOrder, approveSaleOrder, deleteSaleOrder, reverseApproveSaleOrder } from '@/api/sale/saleOrder'
// 列表页明细模式（2026-10-12）：列与取数复用「销售订单明细报表」引擎（同一套接口）
import { saleOrderDetailApi } from '@/api/report/billDetailReport'
// 收款详情弹窗：按来源订单号查收款记录（直接订单收款 + 发货收款两条路径）
import { listReceiptsBySourceOrder } from '@/api/finance/paymentReceipt'
import AuditColumns from '@/components/AuditColumns.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { statusType, statusDotClass, approvalStatusType } from '@/composables/useBillStatus'
import { exportListAsSeen, downloadTemplate as downloadTemplateUtil, importData } from '@/utils/exportUtil'
import BillListPage from '@/components/bill/BillListPage.vue'
// 页头按钮通用组件（2026-09-19）：新增/打印/导出/导入统一渲染 + 统一按钮权限
import BillHeaderActions from '@/components/bill/BillHeaderActions.vue'
// 下推详情弹窗复用编辑页「去向单据」面板 + 批量下推状态接口（2026-10-10）
import BillLinkPanel from '@/components/bill/BillLinkPanel.vue'
import { getBillPushStatus } from '@/api/billLink'
import { useTableSort } from '@/composables/useTableSort'
import { useCustomFieldSearch } from '@/composables/useCustomFieldSearch'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('sale:orders')
const { handleSortChange, getSortParams } = useTableSort()

/**
 * 批量操作声明（2026-09-19）：UI/确认/执行/结果面板全部由通用壳承担，这里只声明"怎么执行"。
 * 命名口径全系统统一：批量提交 / 批量审批 / 批量删除 / 批量导出（不出现各单据自造的名字）。
 */
const batchActions = [
  {
    key: 'submit',
    label: tt('批量提交'),
    type: 'primary',
    perm: 'sale:orders:submit',
    exec: (row) => submitSaleOrder(row.orderId ?? row._docId),
    // 已提交/已审核的自动跳过（不报错，进"跳过清单"说明原因）
    skipReason: (row) => (row.status && row.status !== '草稿'
      ? tt('当前状态：') + row.status + tt('，不是草稿，无需提交') : '')
  },
  {
    key: 'approve',
    label: tt('批量审批'),
    perm: 'sale:orders:audit',
    exec: (row) => approveSaleOrder(row.orderId ?? row._docId),
    skipReason: (row) => (row.status !== '待审核'
      ? tt('当前状态：') + (row.status || '-') + tt('，不是待审核，无需审批') : '')
  },
  {
    // 批量重置草稿（2026-09-19 新增）：与编辑页「重置草稿」同一个后端入口（/reverse-approve），
    // **不在前端自造规则**——能不能重置（状态是否允许、是否已被下游引用）全部由后端判断，
    // 被拒的那条原话进"失败清单"，与单条操作口径完全一致
    key: 'reverse',
    label: tt('批量重置草稿'),
    perm: 'sale:orders:reverseApprove',
    exec: (row) => reverseApproveSaleOrder(row.orderId ?? row._docId)
  },
  {
    key: 'delete',
    label: tt('批量删除'),
    type: 'danger',
    perm: 'sale:orders:delete',
    exec: (row) => deleteSaleOrder(row.orderId ?? row._docId),
    // 状态冲突：选中行含非草稿 → 按钮置灰并说明原因（批量场景"隐藏"会让用户找不到入口，故用置灰+原因）
    disabledReason: (rows) => (rows.some(r => r.status && r.status !== '草稿' && r.status !== '已驳回' && r.status !== '已取消')
      ? tt('非草稿不能删除，请先重置草稿') : '')
  },
  {
    key: 'export',
    label: tt('批量导出'),
    perm: 'sale:orders:export',
    entity: 'saleOrder',
    idOf: (row) => row.orderId ?? row._docId
  }
]


const onSortChange = (e) => { handleSortChange(e); fetchData() }// 列定义（用于列设置抽屉+动态渲染）= 表格全部数据列
// type 标记单元格渲染分支：billNo 单号链接、customer 客户头像、amount/discount/netAmount 金额、status 状态、approval 审核；纯文本列不设 type
const columns = [
  { prop: 'orderNo', label: '订单编号', minWidth: 180, sortable: true, type: 'billNo' },
  { prop: 'orderDate', label: '单据日期', width: 120, sortable: true },
  { prop: 'customerName', label: '客户', minWidth: 180, sortable: true, type: 'customer' },
  { prop: 'salerName', label: '销售员', width: 100, sortable: true },
  { prop: 'totalAmount', label: '合计金额', width: 120, align: 'right', sortable: true, type: 'amount' },
  { prop: 'discountAmount', label: '折扣额', width: 100, align: 'right', sortable: true, type: 'discount' },
  { prop: 'netAmount', label: '价税合计', width: 130, align: 'right', sortable: true, type: 'netAmount' },
  { prop: 'overshipRatio', label: '超量发货比例', width: 130, align: 'right', type: 'percent' },
  { prop: 'status', label: '状态', width: 160, sortable: true, type: 'status' },
  { prop: 'approvalStatus', label: '审核', width: 100, sortable: true, type: 'approval' },
  // 下推状态列（2026-10-10 新增）：按整单下推数据实时判定；没下推的类型显示空白
  { prop: 'pushDelivery', label: '发货情况', width: 150, type: 'pushDelivery' },
  // 收款情况列（2026-10-12 新增）：三态 + 已收/订单额，点击弹收款详情
  { prop: 'pushReceipt', label: '收款情况', width: 150, type: 'pushReceipt' },
  { prop: 'pushInvoice', label: '开票情况', width: 110, type: 'pushInvoice' },
  { prop: 'pushProduce', label: '生产情况', width: 110, type: 'pushProduce' },
  { prop: 'remark', label: '备注', minWidth: 120 }
]
const { columnSettingVisible, isColVisible, getColFixed, configVersion, sortedColumns } = useColumnSetting('SaleOrderList', columns, 'sale_order')
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))

// ============ 明细视图（2026-10-12 列表页明细模式） ============
// 列与取数复用「销售订单明细报表」引擎（/report/sale/order-detail）：一行 = 一条商品明细，
// 数据 = 编辑页保存的全部字段（表头 + 明细行 + 自定义字段）+ 行级回写（已发/已收/已开/已产）。
const detailMeta = ref([])
// 明细模式默认布局（2026-10-12 用户口径）：
// 单号 → 序号（序号列在单号后面，由壳渲染）→ 单据日期/客户 → 图片（在商品编号前面）→ 商品编号 → 商品名称
// → 规格 → 物料自定义字段（紧跟规格后面）→ 单位/数量/仓库/单价/金额（一组相邻）
// → 行级回写（发货/收款/开票/生产）→ 单据自定义字段（放后面）→ 单据状态
const DETAIL_DEFAULT_VISIBLE = ['orderNo', 'orderDate', 'customerName', 'image', 'materialCode', 'materialName',
  'specification', 'unitName', 'quantity', 'warehouseName', 'unitPrice', 'amount',
  'rowDeliveryStatus', 'rowReceiptStatus', 'rowInvoiceStatus', 'rowProduceStatus', 'rowReturnStatus', 'status']
// 默认顺序骨架：物料自定义字段（cf_item_*）插在规格后面、单据自定义字段（cf_doc_*）插在状态前面，
// 其余列按此数组顺序，不在清单里的列排最后（保持后端返回顺序）
const DETAIL_DEFAULT_ORDER = [
  'orderNo', 'orderDate', 'customerName', 'salerName', 'image', 'materialCode', 'materialName', 'specification',
  'unitName', 'quantity', 'warehouseName', 'unitPrice', 'amount',
  'rowDeliveryStatus', 'rowReceiptStatus', 'rowInvoiceStatus', 'rowProduceStatus', 'rowReturnStatus', 'status'
]
function detailDefaultVisible(key) {
  if (!key) return false
  // 物料/单据自定义字段默认显示（用户 2026-10-12 口径：物料自定义字段排规格后、单据自定义字段放后面）
  if (key.indexOf('cf_item_') === 0 || key.indexOf('cf_doc_') === 0) return true
  return DETAIL_DEFAULT_VISIBLE.includes(key)
}
function detailDefaultOrder(key, fallbackIdx) {
  if (key) {
    if (key.indexOf('cf_item_') === 0) return 7.5  // 规格在下标 7，物料自定义字段整体排其后（组内保持后端顺序）
    if (key.indexOf('cf_doc_') === 0) return 16.5  // 生产回写在下标 16、状态在 17，单据自定义字段排两者之间（组内保持后端顺序）
    const i = DETAIL_DEFAULT_ORDER.indexOf(key)
    if (i >= 0) return i
  }
  return 100 + fallbackIdx
}

function detailColType(c) {
  if (c.key === 'orderNo') return 'billNo'
  if (c.key === 'status') return 'status'
  if (c.type === 'image') return 'image'
  if (c.key && c.key.indexOf('row') === 0 && c.key.indexOf('Status') > 0) return 'writeback'
  if (c.type === 'amount') return 'amount'
  if (c.type === 'status') return 'status'
  // 数量/整数列：合计行按数值列回值（此前返回空类型 → 数量合计空白）
  if (c.type === 'number' || c.type === 'int') return 'number'
  return ''
}

function detailColMinWidth(c) {
  if (c.key === 'orderNo') return 150
  if (c.type === 'image') return 80
  if (c.key === 'customerName' || c.key === 'materialName') return 140
  if (c.key && c.key.indexOf('row') === 0) return 110
  if (c.type === 'amount' || c.type === 'number' || c.type === 'int') return 100
  return 100
}

const detailColumnDefs = computed(() => detailMeta.value.map((c, idx) => ({
  prop: c.key,
  label: c.label,
  type: detailColType(c),
  minWidth: detailColMinWidth(c),
  align: c.type === 'image' ? 'center' : (['amount', 'number', 'int'].includes(c.type) ? 'right' : 'left'),
  sortable: !!c.sortable,
  fixed: c.key === 'orderNo' ? 'left' : (c.key === 'status' ? 'right' : false),
  defaultHidden: !detailDefaultVisible(c.key),
  _order: detailDefaultOrder(c.key, idx)
})).filter(c => c.prop)
  .sort((a, b) => a._order - b._order)
  .map(({ _order, ...c }) => c))

/** 汇总页筛选条件 → 明细取数入参（明细报表的 f_* 口径，保持两模式同条件） */
function detailParams() {
  const p = {}
  if (search.keyword && search.keyword.trim()) p.f_orderNo = search.keyword.trim()
  if (search.status) p.f_status = search.status
  if (search.startDate) p.f_orderDateFrom = search.startDate
  if (search.endDate) p.f_orderDateTo = search.endDate
  if (search.customerId) p.f_customerName = search.customerId
  if (search.employeeId) p.f_salerName = search.employeeId
  // 筛选条件自定义（2026-10-14）：非标准维度的 f_* 与自定义字段（f_cf_doc_*）由通用壳统一打包，
  // 键与明细报表引擎同键 → 原样透传即可，两模式一套条件
  Object.assign(p, headerFilterState.value.detailParams || {})
  return p
}

async function detailFetcher({ page, size, params }) {
  return await saleOrderDetailApi.query({ page, size, ...params })
}

function detailLinkBuilder(row) {
  return row && row._docId ? `/sale/orders/${row._docId}` : ''
}

/** 导出明细：勾选行按 _itemIds 精确导出；未勾选按当前筛选导出（与报表同一条导出链路）
 *  壳透传 params（已合并明细模式筛选条件 + 当前排序），优先使用，保证导出与屏幕所见一致 */
async function detailExporter({ cols, itemIds, params }) {
  const p = { cols, ...(params || detailParams()) }
  if (itemIds && itemIds.length) p._itemIds = itemIds.join(',')
  const blob = await saleOrderDetailApi.exportFile(p)
  const url = window.URL.createObjectURL(new Blob([blob]))
  const a = document.createElement('a')
  a.href = url
  a.download = `${tt('销售订单明细')}_${new Date().toISOString().slice(0, 10)}.xlsx`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  window.URL.revokeObjectURL(url)
}

// 明细列清单 = 后端 /columns（系统字段 + 自定义字段），失败静默：明细模式列清单为空时列设置不出现明细块
onMounted(async () => {
  try {
    const res = await saleOrderDetailApi.getColumns()
    detailMeta.value = (res && res.columns) || []
    // 明细模式专属筛选条件元数据（同一次接口：filters + 默认勾选清单）
    detailFiltersMeta.value = (res && res.filters) || []
  } catch (e) { /* 静默降级 */ }
})

const loading = ref(false)
const tableData = ref([])
const search = reactive({
  keyword: '', status: '',
  // 数据看板穿透参数（默认空=不受影响；由 URL query 注入）
  startDate: '', endDate: '', customerId: '', customerLevel: '', materialId: '', productLineId: '',
  // 通用搜索栏「销售员」维度（统一入参名 employeeId，后端映射到 sale_order.saler_id）
  employeeId: ''
})
// 自定义字段搜索（设置中心勾了「可搜索」的字段 → 列表搜索项）
const cf = useCustomFieldSearch('sale_order')
const pagination = reactive({ current: 1, size: 20, total: 0, ...getSortParams() })
const selectedRows = ref([])
// 通用搜索栏「单据状态」下拉选项（首位「全部」由搜索栏内置，value=''）
// 与其它单据一致：状态取值沿用单据状态机，不做新增枚举
const statusOptions = [
  { label: '草稿', value: '草稿' },
  { label: '待审核', value: '待审核' },
  { label: '已生效', value: '已生效' },
  { label: '已取消', value: '已取消' },
  // 作废由底层统一写入"已作废"（AbstractBillService 的作废方法），漏了会没法筛出已作废单据
  { label: '已作废', value: '已作废' }
]

// ============ 筛选条件自定义（2026-10-14 一期） ============
// 条件候选 = 销售订单编辑页表头全部字段 + 单据自定义字段（设置中心勾「可搜索」的，沿用既有搜索通道口径）。
// 键与「销售订单明细报表」引擎筛选键同键 → 汇总/明细两种模式一套条件参数。
// std 条件（状态/日期/客户/销售员）= 现状五件套默认勾选：没保存过筛选设置时查询区与改版前完全一致。
const filterDefs = computed(() => {
  const defs = [
    // —— 默认勾选（= 现状）：单据编号关键词框独立常驻，不占筛选设置名额 ——
    { key: 'status', label: '单据状态', group: '单据信息', type: 'select', options: statusOptions, std: 'status' },
    { key: 'orderDate', label: '单据日期', group: '单据信息', type: 'dateRange', std: 'date' },
    { key: 'customerName', label: '客户', group: '单据信息', type: 'select', optionSource: 'customer', std: 'customer' },
    { key: 'salerName', label: '销售员', group: '单据信息', type: 'select', optionSource: 'employee', std: 'employee' },
    { key: 'orderNo', label: '单据编号', group: '单据信息', type: 'text', defaultChecked: false },
    // —— 其余表头字段（默认不勾，用户在「筛选设置」里按需勾选；默认勾选=现状口径，见守则 2.7.2-6）——
    { key: 'deptName', label: '部门', group: '单据信息', type: 'select', optionSource: 'department', defaultChecked: false },
    { key: 'currencyCode', label: '币种', group: '单据信息', type: 'select', optionSource: 'currency', defaultChecked: false },
    { key: 'priceLevelName', label: '价格等级', group: '单据信息', type: 'select', optionSource: 'priceLevel', defaultChecked: false },
    { key: 'deliveryDate', label: '交货日期', group: '单据信息', type: 'dateRange', defaultChecked: false },
    { key: 'customerNo', label: '客户编号', group: '单据信息', type: 'text', defaultChecked: false },
    { key: 'contactPerson', label: '联系人', group: '单据信息', type: 'text', defaultChecked: false },
    { key: 'contactPhone', label: '联系电话', group: '单据信息', type: 'text', defaultChecked: false },
    { key: 'receiveAddress', label: '收货地址', group: '单据信息', type: 'text', defaultChecked: false },
    { key: 'projectNo', label: '项目号', group: '单据信息', type: 'text', defaultChecked: false },
    { key: 'customerOrderNo', label: '客户订单号', group: '单据信息', type: 'text', defaultChecked: false },
    { key: 'businessType', label: '业务类型', group: '单据信息', type: 'text', defaultChecked: false },
    { key: 'quoteNo', label: '来源报价单号', group: '单据信息', type: 'text', defaultChecked: false },
    { key: 'orderSourceBillNo', label: '来源单号', group: '单据信息', type: 'text', defaultChecked: false },
    { key: 'accountName', label: '收款账户', group: '单据信息', type: 'text', defaultChecked: false },
    { key: 'transportMethod', label: '运输方式', group: '单据信息', type: 'text', defaultChecked: false },
    { key: 'paymentMethod', label: '付款方式', group: '单据信息', type: 'text', defaultChecked: false },
    { key: 'logisticsCompany', label: '物流公司', group: '单据信息', type: 'text', defaultChecked: false },
    { key: 'logisticsNo', label: '物流单号', group: '单据信息', type: 'text', defaultChecked: false },
    { key: 'remark', label: '备注', group: '单据信息', type: 'text', defaultChecked: false },
    { key: 'totalAmount', label: '合计金额', group: '金额信息', type: 'numberRange', defaultChecked: false },
    { key: 'discountAmount', label: '折扣额', group: '金额信息', type: 'numberRange', defaultChecked: false },
    { key: 'taxAmount', label: '税额', group: '金额信息', type: 'numberRange', defaultChecked: false },
    { key: 'netAmount', label: '价税合计', group: '金额信息', type: 'numberRange', defaultChecked: false },
    { key: 'discountRate', label: '折扣率%', group: '金额信息', type: 'numberRange', defaultChecked: false },
    { key: 'exchangeRate', label: '汇率', group: '金额信息', type: 'numberRange', defaultChecked: false },
    { key: 'depositAmount', label: '定金', group: '金额信息', type: 'numberRange', defaultChecked: false }
  ]
  // 单据自定义字段：沿用既有 customSearch 通道（与原「自定义字段搜索栏」同为文本输入，口径一致）；
  // 开关/公式型字段不进候选（值域与只读语义不适合做搜索条件）
  const CF_TYPES = ['text', 'textarea', 'number', 'date', 'datetime', 'select']
  const cfDefs = (cf.searchFields.value || [])
    .filter(f => f && f.fieldKey && CF_TYPES.includes(f.fieldType))
    .map(f => ({
      key: 'cf_doc_' + f.fieldKey,
      label: f.fieldLabel,
      group: '自定义字段',
      type: 'text',
      cf: f.fieldKey,
      defaultChecked: true // 现状：可搜索自定义字段默认显示为搜索项
    }))
  return [...defs, ...cfDefs]
})

// ============ 明细模式专属筛选条件（2026-10-14 同日增强：切换明细模式查询区跟随切换） ============
// 来源 = 销售订单明细报表 /columns 的 filters 元数据（表头字段 + 明细行字段 + 自定义字段，键与引擎同键）；
// 配置与汇总各自独立记忆（storageKey::detail）。默认勾选 = 现状四件套；商品名称/规格等明细条件默认不勾。
const detailFiltersMeta = ref([])
const STD_BY_KEY = { status: 'status', orderDate: 'date', customerName: 'customer', salerName: 'employee' }
const DETAIL_TYPE_MAP = { text: 'text', cfText: 'text', select: 'select', dateRange: 'dateRange', numberRange: 'numberRange' }
const detailFilterDefs = computed(() => (detailFiltersMeta.value || [])
  .filter(f => f && f.key)
  .map(f => {
    const std4 = STD_BY_KEY[f.key]
    return {
      key: f.key,
      label: f.label,
      group: f.group || '筛选条件',
      // 报表 /columns 的 filters 元数据字段名是 type（不是 filterType）；读错会全部退化成文本框
      type: DETAIL_TYPE_MAP[f.type] || 'text',
      optionSource: f.optionSource,
      options: f.options,
      paramKeys: f.paramKeys,
      std: std4,
      // 引擎自带的默认勾选口径是「明细报表页」的（含商品三件套）；列表页明细模式的现状 = 现状四件套
      defaultChecked: !!std4
    }
  }))

/** 自定义条件参数（由通用壳经 filter-params 下发）：params 汇总用 / detailParams 明细用 / customSearch 自定义字段 */
const headerFilterState = ref({ params: {}, customSearch: undefined, detailParams: {} })
function onHeaderFilterParams(payload) {
  headerFilterState.value = payload || { params: {}, customSearch: undefined, detailParams: {} }
}

const printVisible = ref(false)
const printBillId = ref(null)

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

async function loadTemplateFields() {
  try {
    const resp = await fetch(`/api/export/template/saleOrder/fields`, { headers: { Authorization: `Bearer ${localStorage.getItem('token') || ''}` } })
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
    await downloadTemplateUtil('saleOrder', checkedOptionalFields.value)
    ElMessage.success(tt('自定义模板下载成功'))
  } catch (e) {
    ElMessage.error(tt('自定义模板下载失败'))
  } finally {
    downloadingCustomTemplate.value = false
  }
}

// 顶部统计卡片：数据来自后端仪表盘接口（与列表同数据权限口径；原来是硬编码 0 → 卡片永远是 0）
// 带 filter 的卡片可点击 → 直接筛选下方列表（用户 2026-09-16 口径）
const activeStatKey = ref('')
const pad2 = (n) => String(n).padStart(2, '0')
function monthRange() {
  const d = new Date()
  const y = d.getFullYear()
  const m = d.getMonth() + 1
  const lastDay = new Date(y, m, 0).getDate()
  return { startDate: `${y}-${pad2(m)}-01`, endDate: `${y}-${pad2(m)}-${pad2(lastDay)}` }
}
const stats = ref([
  { key: 'month', label: '本月订单', value: 0, type: 'primary', icon: 'Document', filter: monthRange() },
  { key: 'draft', label: '草稿订单', value: 0, type: 'warning', icon: 'EditPen', filter: { status: '草稿' } },
  { key: 'pending', label: '待审核', value: 0, type: 'info', icon: 'Clock', filter: { status: '待审核' } },
  { key: 'approved', label: '已生效', value: 0, type: 'success', icon: 'CircleCheck', filter: { status: '已生效' } }
])

async function loadStats() {
  try {
    const d = (await getSaleOrderDashboard()) || {}
    stats.value = [
      { key: 'month', label: '本月订单', value: d.monthOrders ?? 0, type: 'primary', icon: 'Document', filter: monthRange() },
      { key: 'draft', label: '草稿订单', value: d.draftOrders ?? 0, type: 'warning', icon: 'EditPen', filter: { status: '草稿' } },
      { key: 'pending', label: '待审核', value: d.pendingOrders ?? 0, type: 'info', icon: 'Clock', filter: { status: '待审核' } },
      { key: 'approved', label: '已生效', value: d.approvedOrders ?? 0, type: 'success', icon: 'CircleCheck', filter: { status: '已生效' } }
    ]
  } catch (e) {
    // 静默降级：取数失败时卡片保持原值，不弹诊断卡、不阻断列表
  }
}

// 卡片点击 → 应用该卡筛选条件（状态/日期先清空再套用，避免上一张卡残留）；再点同一张卡 = 取消
// 2026-09-19 修复：点卡片时顺带刷新统计数字——历史现象"批量提交后再点卡片，草稿数还是旧值"
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
  loadStats()
}

function handlePrint(row) {
  printBillId.value = row.orderId ?? row._docId
  printVisible.value = true
}

function handleBatchPrint(rows) {
  // rows = 页头插槽传入的统一勾选行（汇总=汇总勾选；明细=勾选明细行按单据去重）
  const list = (rows && rows.length ? rows : selectedRows.value) || []
  if (!list.length) {
    ElMessage.warning(tt('请先勾选要打印的订单'))
    return
  }
  if (list.length > 1) {
    ElMessage.warning(tt('一次只能打印一张单据，请仅勾选一行'))
    return
  }
  handlePrint(list[0])
}

// 列表查询参数（列表加载与导出共用同一套，避免两处口径漂移）
function buildQueryParams() {
  return {
    ...search,
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
    const res = await listSaleOrder({ current: pagination.current, size: pagination.size, ...buildQueryParams() })
    tableData.value = res.records || []
    pagination.total = res.total || 0
    // 下推状态列（2026-10-10）：本页一批查询，失败不阻断列表
    loadPushStatus(tableData.value)
  } catch (e) {
    ElMessage.error(e.message || tt('查询失败'))
  } finally {
    loading.value = false
  }
}

// ============ 下推状态列（2026-10-10 新增） ============
// 口径：按整单下推数据实时判定（后端聚合）；已生效下游数量计入进度，草稿/待审核只提示"未生效"；
// 发货情况固定三态（未发货/发货中/发货完成），开票/生产没下推显示空白。
const pushDetailVisible = ref(false)
const pushDetailBillId = ref(null)
const pushDetailNo = ref('')

function pushOf (row, kind) {
  return (row.pushStatus && row.pushStatus[kind]) || null
}

function pushText (row, colType) {
  const kind = { pushDelivery: 'delivery', pushInvoice: 'invoice', pushProduce: 'production' }[colType]
  const s = pushOf(row, kind)
  if (colType === 'pushDelivery') {
    return ({ NONE: '未发货', PARTIAL: '发货中', FULL: '发货完成' })[s ? s.state : 'NONE'] || '未发货'
  }
  if (!s || s.state === 'NONE') return ''
  if (colType === 'pushInvoice') return ({ PARTIAL: '开票中', FULL: '开票完成' })[s.state] || ''
  return ({ PARTIAL: '生产中', FULL: '生产完成' })[s.state] || ''
}

function pushTagType (row, colType) {
  const kind = { pushDelivery: 'delivery', pushInvoice: 'invoice', pushProduce: 'production' }[colType]
  const s = pushOf(row, kind)
  return ({ NONE: 'info', PARTIAL: 'warning', FULL: 'success' })[s ? s.state : 'NONE'] || 'info'
}

function trimQty (n) { return String(Number(Number(n || 0).toFixed(4))) }

function pushDeliverySub (row) {
  const s = pushOf(row, 'delivery')
  if (!s) return ''
  return `${trimQty(s.effectiveQty)}/${trimQty(s.orderQty)}`
}

function pushDeliveryTitle (row) {
  const s = pushOf(row, 'delivery')
  if (!s) return '未下推销售出库单'
  const base = `已生效出库 ${trimQty(s.effectiveQty)} / 订单 ${trimQty(s.orderQty)}`
  return Number(s.pendingCount || 0) > 0 ? `${base}；含未生效出库单 ${s.pendingCount} 张` : base
}

function openPushDetail (row) {
  pushDetailBillId.value = row.orderId
  pushDetailNo.value = row.orderNo
  pushDetailVisible.value = true
}

async function loadPushStatus (rows) {
  const ids = (rows || []).map(r => r.orderId).filter(v => v !== undefined && v !== null)
  if (!ids.length) return
  try {
    const map = (await getBillPushStatus('SALE_ORDER', ids)) || {}
    rows.forEach(r => { r.pushStatus = map[String(r.orderId)] || null })
  } catch (e) {
    // 状态列取数失败：保持空白，不弹诊断卡、不阻断列表
  }
}

// ============ 收款情况列（2026-10-12 新增） ============
// 口径：订单已收金额 vs 价税合计三态；已收金额 = 直接订单收款 + 发货收款回写（收款单确认后自动累计）
function receiptState (row) {
  const net = Number(row.netAmount || 0)
  const rec = Number(row.receivedAmount || 0)
  if (rec <= 0) return 'NONE'
  if (rec + 0.01 < net) return 'PARTIAL'
  return 'FULL'
}

function receiptText (row) {
  return ({ NONE: '未收款', PARTIAL: '收款中', FULL: '收款完成' })[receiptState(row)]
}

function receiptTagType (row) {
  return ({ NONE: 'info', PARTIAL: 'warning', FULL: 'success' })[receiptState(row)]
}

function receiptSub (row) {
  return `${Number(row.receivedAmount || 0).toFixed(2)}/${Number(row.netAmount || 0).toFixed(2)}`
}

// 收款详情弹窗：明细行收款情况 + 收款记录（两条路径）
const receiptDetailVisible = ref(false)
const receiptDetailLoading = ref(false)
const receiptDetailNo = ref('')
const receiptDetailLines = ref([])
const receiptDetailRecords = ref([])

async function openReceiptDetail (row) {
  receiptDetailNo.value = row.orderNo
  receiptDetailVisible.value = true
  receiptDetailLoading.value = true
  receiptDetailLines.value = []
  receiptDetailRecords.value = []
  try {
    const [detailRes, recordRes] = await Promise.all([
      getSaleOrder(row.orderId),
      listReceiptsBySourceOrder(row.orderNo)
    ])
    const detail = (detailRes && (detailRes.data || detailRes)) || {}
    receiptDetailLines.value = detail.items || detail.orderItems || []
    const records = (recordRes && (recordRes.data || recordRes)) || []
    receiptDetailRecords.value = Array.isArray(records) ? records : []
  } catch (e) {
    // 取数失败：保持空态，不弹诊断卡、不阻断列表
  } finally {
    receiptDetailLoading.value = false
  }
}

// 通用搜索栏（日期区间 / 客户 / 销售员）变更：先把值写回本页 search
// 注：BillSearchBar 会紧接着抛 search 事件（壳已转发 → handleSearch），所以这里不重复发起查询
function onFilterChange(patch) {
  Object.assign(search, patch || {})
}

function handleSearch() {
  pagination.current = 1
  fetchData()
}

function handleReset() {
  search.keyword = ''
  search.status = ''
  // 通用搜索栏 + 数据看板穿透的筛选维度一并清空（「重置」应回到未筛选状态）
  search.startDate = ''
  search.endDate = ''
  search.customerId = ''
  search.customerLevel = ''
  search.materialId = ''
  search.productLineId = ''
  search.employeeId = ''
  activeStatKey.value = ''   // 重置时同时取消统计卡片的高亮
  pagination.current = 1
  // 自定义筛选条件（f_*/自定义字段）的值由通用壳在重置时统一清空并下发空参数
  fetchData()
}

async function handleExport() {
  try {
    // 页面上看到什么列，导出就带什么列；行 = 当前筛选条件下的全部
    await exportListAsSeen({
      entityName: 'saleOrder',
      columns: visibleSortedColumns.value,
      fetchPage: listSaleOrder,
      params: buildQueryParams(),
      idField: 'orderId'
    })
    ElMessage.success(tt('导出成功'))
  } catch (e) {
    // 失败原因已由 exportUtil 统一提示
  }
}

function handleImport() {
  importDialogVisible.value = true
  importResult.value = null
  selectedImportFile.value = null
  if (uploadRef.value) {
    uploadRef.value.clearFiles()
  }
  loadTemplateFields()
}

async function downloadTemplate() {
  downloadingTemplate.value = true
  try {
    await downloadTemplateUtil('saleOrder')
    ElMessage.success(tt('模板下载成功'))
  } catch (err) {
    ElMessage.error(tt('模板下载失败'))
  } finally {
    downloadingTemplate.value = false
  }
}

function handleFileChange(file) {
  selectedImportFile.value = file.raw
  importResult.value = null
}

async function confirmImport() {
  if (!selectedImportFile.value) {
    ElMessage.warning(tt('请先选择文件'))
    return
  }

  importing.value = true
  try {
    const result = await importData('saleOrder', selectedImportFile.value)
    importResult.value = result

    if (result.failCount === 0) {
      ElMessage.success(`${tt("导入成功：共 ")}${result.successCount}${tt(" 条记录")}`)
      importDialogVisible.value = false
      fetchData()
    } else {
      ElMessage.warning(`${tt("导入完成：成功 ")}${result.successCount}${tt(" 条，失败 ")}${result.failCount}${tt(" 条")}`)
      if (result.errors && result.errors.length > 0) {
        ElMessageBox.alert(
          result.errors.map(e => `${tt("第 ")}${e.rowIndex}${tt(" 行")}${e.fieldName ? ' [' + e.fieldName + ']' : ''}：${e.message}`).join('<br/>'),
          tt('导入失败详情'),
          { dangerouslyUseHTMLString: true, type: 'warning' }
        )
      }
    }
  } catch (err) {
    ElMessage.error(tt('导入失败：') + (err.message || tt('未知错误')))
  } finally {
    importing.value = false
  }
}

// ===== 数据看板穿透：把 URL query 注入筛选条件（无 query 时行为完全不变）=====
const route = useRoute()
function applyDrillQuery() {
  const q = route.query || {}
  let hit = false
  for (const k of ['startDate', 'endDate', 'customerId', 'customerLevel', 'materialId', 'productLineId']) {
    if (q[k]) { search[k] = String(q[k]); hit = true }
  }
  if (q.status) { search.status = String(q.status) }
  // 物料维度下钻由「销售订单明细报表」承接（列表按单筛选无法按物料过滤明细行）
  if (hit) ElMessage.info(tt('已应用穿透筛选条件'))
}

onMounted(async () => {
  applyDrillQuery()
  cf.load()
  loadStats()
  await fetchData()
})
useAutoRefresh(() => { fetchData(); loadStats() })
</script>

<style scoped lang="scss">
/* 页面骨架样式（页头/统计卡/搜索条/表格壳/分页）已由 BillListPage 壳组件接管 */
/* 以下仅保留：插槽内业务单元格样式 + header-actions 插槽按钮样式 + 导入弹窗样式 */

.btn-primary-glow {
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  border: none;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
  &:hover {
    box-shadow: 0 6px 20px rgba(99, 102, 241, 0.4);
    transform: translateY(-1px);
  }
}

/* 插槽内业务单元格样式（保留本页作用域） */
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
.discount-text { color: #f59e0b; font-variant-numeric: tabular-nums; }
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

/* 导入弹窗样式 */
.import-dialog-content {
  .import-step {
    padding: 8px 0;
    .step-title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 15px;
      font-weight: 600;
      color: #303133;
      margin-bottom: 8px;
      .el-icon { font-size: 18px; color: #6366f1; }
    }
    .step-desc { font-size: 13px; color: #909399; margin-bottom: 12px; }
  }
  .import-uploader {
    :deep(.el-upload-dragger) {
      width: 100%;
      height: 140px;
      border-radius: 8px;
    }
  }
  .import-result {
    .result-title { font-size: 15px; font-weight: 600; color: #303133; margin-bottom: 12px; }
    .import-error-alert {
      margin-top: 12px;
      .error-list {
        max-height: 160px;
        overflow-y: auto;
        .error-item { font-size: 12px; padding: 2px 0; color: #F56C6C; }
        .error-more { font-size: 12px; color: #909399; padding-top: 4px; }
      }
    }
  }
}

:deep(.el-dialog) {
  border-radius: 12px;
}

// 下推状态列（2026-10-10）：标签 + 已发数量副文本，样式对齐全站标签口径
.push-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  .push-tag {
    cursor: pointer;
  }
  .push-sub {
    font-size: 12px;
    color: #909399;
    white-space: nowrap;
  }
}

// 收款详情弹窗（2026-10-12）：分区标题
.receipt-section-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
  padding-left: 8px;
  border-left: 3px solid #1890ff;
}
</style>
