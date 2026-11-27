<template>
  <div :class="['bill-list-page', pageClass]">
    <!-- 页头 -->
    <div class="page-header">
      <div class="header-left">
        <div class="title-group">
          <div class="page-title">
            {{ $tf(title) }}
            <span v-if="badge" class="title-badge">{{ badge }}</span>
          </div>
          <div class="page-sub">{{ $tf(subTitle) }}</div>
        </div>
      </div>
      <div class="header-actions">
        <slot name="header-actions" :selected-rows="batchBarRows" :loading="loading">
          <el-button v-if="createPath" v-permission="perm('add')" type="primary" class="btn-primary-glow" @click="$router.push(createPath)">
            <el-icon><Plus /></el-icon>
            {{ $tt('新增') }}{{ $tf(title) }}
          </el-button>
        </slot>
      </div>
    </div>

    <!-- 统计卡片（通用件 StatCards）：带 filter 声明的卡片可点击 → 按下发的筛选条件过滤下方列表 -->
    <StatCards :stats="stats" :active-key="activeStatKey" @stat-click="onStatClick" />

    <!-- 内容卡 -->
    <div class="content-card">
      <div class="search-bar">
        <!-- 状态页签：页面把「状态」声明为筛选维度（filters 含 'status'）时自动隐藏，避免与下拉筛选重复 -->
        <div v-if="statusTabs.length && !filters.includes('status')" class="status-tabs">
          <div
            v-for="tab in statusTabs"
            :key="tab.value"
            :class="['status-tab', { active: activeTab === tab.value }]"
            @click="$emit('tab-change', tab.value)"
          >
            <span class="tab-text">{{ $tf(tab.label) }}</span>
            <span v-if="tab.count > 0" class="tab-count">{{ tab.count }}</span>
          </div>
        </div>

        <!-- ============ 自定义筛选区（2026-10-14 筛选条件自定义）：页面声明了 filterDefs 才启用 ============
             布局：右上角操作行（搜索/重置/筛选设置）+ 多行网格条件区（一行随宽度自动 3~5 个）；
             条件显隐与顺序由「筛选设置」控制（配置按登录人+公司隔离）；列设置移到表格右上角（它管表格列） -->
        <template v-if="customSearchActive">
          <div class="custom-search">
            <div class="filter-toolbar">
              <el-button type="primary" v-permission="perm('query')" @click="onSearchClick">{{ $tt('搜索') }}</el-button>
              <el-button v-permission="perm('reset')" @click="onResetClick">{{ $tt('重置') }}</el-button>
              <el-button v-permission="perm('filterSetting')" @click="filterSettingVisible = true">
                <el-icon style="margin-right: 4px;"><Filter /></el-icon>{{ $tt('筛选设置') }}
              </el-button>
            </div>
            <div class="search-grid">
              <div class="filter-item">
                <span class="filter-label">{{ $tt(keywordLabel) }}</span>
                <el-input v-model="keywordModel" :placeholder="$tt(searchPlaceholder)" clearable @keyup.enter="onSearchClick">
                  <template #prefix><el-icon><Search /></el-icon></template>
                </el-input>
              </div>
              <div v-for="f in visibleHeaderFilters" :key="f.key" class="filter-item">
                <span class="filter-label" :title="$tf(f.label)">{{ $tf(f.label) }}</span>
                <SearchFilterControl
                  :def="f"
                  :model-value="activeDynValues[f.key]"
                  @update:model-value="(v) => onDynValueChange(f, v)"
                  @submit="() => onDynFilterSubmit(f)"
                />
              </div>
            </div>
          </div>
        </template>

        <!-- ============ 默认查询区（未声明 filterDefs 的页面，与改版前完全一致） ============ -->
        <div v-else class="search-cols">
          <div class="search-filters">
            <div class="filter-item">
              <span class="filter-label">{{ $tt(keywordLabel) }}</span>
              <el-input v-model="keywordModel" :placeholder="$tt(searchPlaceholder)" clearable class="search-input" @keyup.enter="onSearchClick">
                <template #prefix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-input>
            </div>
            <!-- 通用搜索栏（标准筛选：状态 / 日期 / 客户 / 供应商 / 员工）：只渲染页面 filters 里声明的维度 -->
            <BillSearchBar
              v-if="filters.length"
              :filters="filters"
              :model="filterModel"
              :employee-label="employeeLabel"
              :date-label="dateLabel"
              :status-options="statusOptions"
              @change="(patch) => $emit('filter-change', patch)"
              @search="onSearchClick"
            />
            <!-- 扩展搜索项（如「自定义字段搜索」）：放在筛选区末尾，与其它筛选条件一起换行 -->
            <slot name="search-extra"></slot>
          </div>
          <div class="search-actions">
            <el-button type="primary" v-permission="perm('query')" @click="onSearchClick">{{ $tt('搜索') }}</el-button>
            <el-button v-permission="perm('reset')" @click="onResetClick">{{ $tt('重置') }}</el-button>
            <el-tooltip v-if="detailEnabledEff || (columns && columns.length)" :content="$tt('列设置')" placement="top">
              <el-button :icon="Setting" circle v-permission="perm('columnSetting')" @click="columnSettingVisibleModel = true" />
            </el-tooltip>
          </div>
        </div>
      </div>

      <!-- 列设置（自定义筛选区启用时从查询区移到这里：它管的是表格列，与管搜索条件的「筛选设置」分开） -->
      <div v-if="customSearchActive && (detailEnabledEff || (columns && columns.length))" class="table-tools">
        <el-tooltip :content="$tt('列设置')" placement="top">
          <el-button :icon="Setting" circle v-permission="perm('columnSetting')" @click="columnSettingVisibleModel = true" />
        </el-tooltip>
      </div>

      <!-- 批量操作条（2026-09-19）：页面传 batchActions 才渲染；未勾选时不占高度。
           2026-10-12 起明细模式同样渲染（用户口径：汇总有的按钮明细不能少）——
           明细勾选的是明细行，按单据去重后执行（batchBarRows） -->
      <BillBatchBar
        v-if="batchActions && batchActions.length"
        :selected-rows="batchBarRows"
        :actions="batchActions"
        :loading="batchRunning"
        :progress="batchProgressState"
        @action="onBatchAction"
        @select-all="onBatchSelectAll"
        @clear="onBatchClear"
      />

      <!-- ===== 明细模式（2026-10-12 列表页明细模式）：一行 = 一条商品明细，报表式展示 ===== -->
      <template v-if="isDetail">
        <!-- 明细勾选操作行：勾选 = 批量导出选中的明细行；不勾选时只给「导出当前筛选」 -->
        <div class="detail-select-bar">
          <template v-if="detailSelected.length">
            <span class="dsb-info">{{ $tt('已勾选') }} <b>{{ detailSelected.length }}</b> {{ $tt('条明细') }}</span>
            <el-button type="primary" size="small" :loading="detailExporting" @click="exportDetailSelected">{{ $tt('导出选中明细') }}</el-button>
          </template>
          <span v-else class="dsb-info">{{ $tt('明细视图：一行一条商品明细，勾选可导出选中明细行') }}</span>
          <el-button size="small" :loading="detailExporting" @click="exportDetailFiltered">{{ $tt('导出当前筛选明细') }}</el-button>
          <el-button v-if="detailSelected.length" text type="primary" @click="clearDetailSelection">{{ $tt('清除勾选') }}</el-button>
        </div>

        <el-table
          ref="detailTableRef"
          :key="detailTableKey"
          :data="detailData"
          v-loading="detailLoading"
          class="tech-table"
          border
          :header-cell-style="headerCellStyle"
          show-summary
          :summary-method="detailSummaryMethod"
          @selection-change="onDetailSelectionChange"
          @sort-change="onDetailSortChange"
          @header-dragend="detailColApi.onHeaderDragend"
        >
          <el-table-column type="selection" width="46" />
          <!-- 序号列（2026-10-14 用户口径）：紧排勾选列之后、单号之前，作为第一数据列 -->
          <el-table-column type="index" label="#" width="50" align="center" fixed="left" />
          <template v-for="col in visibleDetailColumns" :key="col.prop">
            <el-table-column
              :prop="col.prop"
              :label="$tf(col.label)"
              :width="detailColApi.getColWidth(col.prop) || col.width"
              :min-width="col.minWidth || 100"
              :align="col.align || 'left'"
              :sortable="col.sortable ? 'custom' : false"
              :fixed="detailColApi.getColFixed(col.prop, col.fixed) || false"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                <MaterialFieldCell v-if="col.type === 'image'" :row="row" :col="col" />
                <span v-else-if="col.type === 'billNo'" class="detail-link" @click="goDetailBill(row)">{{ row[col.prop] }}</span>
                <span v-else-if="col.type === 'status'">
                  <el-tag size="small" :type="detailStatusTagType(row[col.prop])">{{ row[col.prop] ?? '' }}</el-tag>
                </span>
                <span v-else-if="col.type === 'amount' || col.type === 'netAmount'" class="detail-amount">¥{{ amountCellText(row[col.prop]) }}</span>
                <span v-else-if="col.type === 'discount'" class="detail-amount">- {{ amountCellText(row[col.prop]) }}</span>
                <span v-else-if="col.type === 'writeback'" :class="['wb-text', writebackClass(row[col.prop])]">{{ row[col.prop] ?? '' }}</span>
                <span v-else-if="isQtyCol(col)">{{ qtyCellText(row, col) }}</span>
                <span v-else>{{ row[col.prop] ?? '' }}</span>
              </template>
            </el-table-column>
          </template>
          <template #empty>
            <div class="detail-empty">
              <div class="de-title">{{ $tt('当前筛选条件下没有明细数据') }}</div>
              <div class="de-sub">{{ $tt('请放宽单据日期区间，或清空客户 / 商品等条件后重试。') }}</div>
            </div>
          </template>
        </el-table>

        <div class="pagination-wrap">
          <div class="pagination-info">{{ $tt('共') }} {{ detailPagination.total }} {{ $tt('条明细行') }} · {{ $tt('每页') }} {{ detailPagination.size }} {{ $tt('条') }}</div>
          <el-pagination
            v-model:current-page="detailPagination.current"
            v-model:page-size="detailPagination.size"
            :total="detailPagination.total"
            :page-sizes="[20, 50, 100, 200]"
            layout="prev, pager, next, sizes, jumper"
            @size-change="onDetailPageChange"
            @current-change="onDetailPageChange"
          />
        </div>
      </template>

      <!-- ===== 汇总模式：与改版前完全一致 ===== -->
      <template v-else>
      <el-table
        ref="tableRef"
        :key="tableKey"
        :data="tableData"
        v-loading="loading"
        class="tech-table"
        border
        :header-cell-style="headerCellStyle"
        :show-summary="showSummary"
        :summary-method="summaryMethod"
        @selection-change="handleSelectionChange"
        @sort-change="(e) => $emit('sort-change', e)"
        @header-dragend="onHeaderDragend"
      >
        <slot></slot>
      </el-table>

      <div class="pagination-wrap">
        <div class="pagination-info">{{ $tt('共') }} {{ pagination.total }} {{ $tt('条记录') }}</div>
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="pageSizes"
          layout="prev, pager, next, sizes, jumper"
          @size-change="$emit('page-change')"
          @current-change="$emit('page-change')"
        />
      </div>
      </template>
    </div>

    <!-- 打印预览 -->
    <PrintPreviewDialog
      v-if="billType"
      v-model="printVisibleModel"
      :bill-type="billType"
      :bill-id="printBillIdModel"
    />

    <!-- 列设置抽屉（明细模式下切换到明细列配置；声明了明细能力的页面抽屉顶部出现「视图模式」切换） -->
    <ColumnSettingDrawer
      v-if="activeDrawerColumns.length || detailEnabledEff"
      v-model="columnSettingVisibleModel"
      :page-key="isDetail ? detailPageKey : storageKey"
      :columns="activeDrawerColumns"
      :show-view-mode="detailEnabledEff"
      v-model:view-mode="viewModeModel"
      @change="onColumnSettingChange"
    />

    <!-- 筛选设置面板（2026-10-14）：与列设置两套独立配置；明细模式用独立条件套件（storageKey::detail） -->
    <FilterSettingPanel
      v-if="customSearchActive"
      v-model="filterSettingVisible"
      :page-key="(isDetail && detailDefsAvailable) ? storageKey + '::detail' : storageKey"
      :filters="activeFilterDefs"
      :title="$tt('筛选设置')"
      @save="onHeaderFilterSave"
      @reset="onHeaderFilterReset"
    />

    <!-- 批量操作结果面板（成功/失败/跳过 + 失败清单可导出/可重试） -->
    <BatchResultDialog
      :model-value="batchResultVisible"
      :result="batchResult"
      :retrying="batchRetrying"
      @update:model-value="onBatchResultClose"
      @retry="onBatchRetry"
      @export-fail="onBatchExportFail"
    />
  </div>
</template>

<script setup>
/**
 * 单据列表页通用壳组件（批次A）
 * 职责：页头/统计卡/搜索条/表格外壳/分页/打印/列设置 骨架
 * 业务列与行操作通过默认插槽注入；页面业务逻辑（fetch/提交/审批等）留在页面
 * 样式逐字迁移自 SaleQuoteList.vue，保证重构后像素级一致
 */
import { computed, onMounted, onActivated, onDeactivated, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Plus, Search, Setting, Filter } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { tt } from '@/i18n'
import { nextTick } from 'vue'
import { useColWidth } from '@/composables/useColWidth'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { useColumnConfigStore } from '@/stores/columnConfig'
// 明细模式自动接线（2026-10-12 铺开批次）：登记表里有本页配置 → 零页面代码获得明细能力
import { BILL_DETAIL_VIEW_CONFIGS } from '@/config/billDetailView'
import { createAutoDetailView } from '@/composables/useBillDetailView'
import PrintPreviewDialog from '@/components/PrintPreviewDialog.vue'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
// 自定义字段列（2026-10-09）：按页面登记的业务对象，把勾了「列表显示」的自定义字段出成列并回填值
import { listCustomColumnsRef, resolveListCustomField } from '@/config/listCustomFieldEntities'
import { batchGetFieldValues } from '@/api/system/customField'
import BillSearchBar from '@/components/bill/BillSearchBar.vue'
import FilterSettingPanel from '@/components/FilterSettingPanel.vue'
import SearchFilterControl from '@/components/SearchFilterControl.vue'
import { useFilterSetting } from '@/composables/useFilterSetting'
import StatCards from '@/components/bill/StatCards.vue'
import BillBatchBar from '@/components/bill/BillBatchBar.vue'
import BatchResultDialog from '@/components/bill/BatchResultDialog.vue'
// 物料列单元格统一渲染件（2026-10-12）：明细模式的图片列复用它，不再自造 <el-image> 分支（守则 03：单元格渲染只有一份）
import MaterialFieldCell from '@/components/MaterialFieldCell.vue'
import { usePrecision } from '@/composables/usePrecision'
import { useBillBatch } from '@/composables/useBillBatch'
import { exportSelectedRows, exportAsSeen } from '@/utils/exportUtil'
// 设置中心系统参数读取（2026-10-14 双视图二期）：「列表默认视图」全局默认值的装载来源
import { useSysConfig } from '@/composables/useSysConfig'
// 批量动作完成后刷新本页（列表 + 顶部统计卡）：复用页面注册的软刷新，页面零改动
import { triggerListRefresh } from '@/composables/useAutoRefresh'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm()


const props = defineProps({
  pageClass: { type: String, default: '' },        // 页面根类名（如 sale-quote-page，用于页面级背景等差异样式）
  backPath: { type: String, default: '/sale' },    // 返回目标路径
  title: { type: String, required: true },          // 页面标题（如 销售报价单）
  badge: { type: String, default: '' },             // 标题英文徽标
  subTitle: { type: String, default: '' },         // 副标题
  createPath: { type: String, default: '' },        // 新增按钮跳转路径；空则不显示默认新增按钮
  stats: { type: Array, default: () => [] },        // 统计卡片 [{label,value,type,icon}]
  statusTabs: { type: Array, default: () => [] },   // 状态tab [{label,value,count}]
  activeTab: { type: String, default: 'all' },      // 当前激活tab
  searchPlaceholder: { type: String, default: '输入单号/名称搜索' },
  keywordLabel: { type: String, default: '单据编号' },     // 关键词框左侧标签（让用户知道这个框搜什么）
  dateLabel: { type: String, default: '单据日期' },        // 日期维度标签（台账类页面可传「交易日期」等真实口径）
  pageSizes: { type: Array, default: () => [20, 50, 100] }, // 每页条数选项（页面原本有 200/页 能力时按原样传入，避免功能收敛）
  filters: { type: Array, default: () => [] },           // 通用搜索栏维度：'status' / 'date' / 'customer' / 'supplier' / 'employee'
  filterModel: { type: Object, default: () => ({}) },    // 页面 search 对象（供筛选控件回显）
  statusOptions: { type: Array, default: () => [] },     // 单据状态下拉选项 [{label,value}]（「全部」由搜索栏内置）
  employeeLabel: { type: String, default: '员工' },       // 员工维度文案（销售员 / 采购员 / 经手人…）
  keyword: { type: String, default: '' },           // 搜索关键字（v-model:keyword）
  selectedRows: { type: Array, default: () => [] }, // 表格选中行（v-model:selected-rows）
  columnSettingVisible: { type: Boolean, default: false }, // 列设置抽屉显隐（v-model:column-setting-visible）
  tableData: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  pagination: { type: Object, required: true },     // {current,size,total}（直接双向绑定页面对象）
  storageKey: { type: String, default: '' },        // 列设置持久化key
  columns: { type: Array, default: () => [] },      // 列定义（列设置抽屉）
  tableKey: { type: [String, Number], default: undefined }, // 列配置版本号（绑定 el-table :key，配置变化后强制重建表格使列顺序生效）
  billType: { type: String, default: '' },          // 打印预览单据类型；空则不渲染打印组件
  printVisible: { type: Boolean, default: false },  // 打印弹窗显隐（v-model:print-visible）
  printBillId: { type: [Number, String], default: null }, // 打印单据id（v-model:print-bill-id）
  activeStatKey: { type: String, default: '' },            // 当前激活的统计卡片 key（高亮用，由页面控制）
  showSummary: { type: Boolean, default: false },          // 是否显示表格合计行（默认关闭；库存流水等台账页用）
  summaryMethod: { type: Function, default: undefined },     // 合计行取值方法（showSummary=true 时由页面提供）
  // ===== 批量操作（2026-09-19）=====
  // 动作声明数组：[{ key, label, type, perm, entity, exec(row), idOf(row), labelOf(row), fields(), skipReason(row), disabledReason(rows) }]
  // fields() 仅供导出动作使用：返回页面当前可见列（columnsToExportFields 结果），声明后勾选导出按"页面可见列"输出
  // 空数组 = 本页不启用批量条；确认弹窗、执行、结果面板、失败清单全部由壳统一处理，页面只声明"怎么执行"
  batchActions: { type: Array, default: () => [] },
  // ===== 明细视图（2026-10-12 列表页明细模式）=====
  // detailEnabled：本页是否提供明细模式（提供后列设置抽屉顶部出现「视图模式」切换）；
  // detailColumns：明细列定义（一般来自后端明细报表 /columns，含自定义字段列）；
  // detailFetcher：async ({ page, size, params }) => { records, total, totals }（params 已含 cols/sortBy/f_*）；
  // detailExporter：async ({ cols, itemIds }) => 下载文件（itemIds=null 表示按当前筛选导出全部）；
  // detailLinkBuilder：row => 路由串（明细"单号"列点击跳单据）；
  // detailParamsBuilder：() => 当前页筛选条件映射成明细查询入参（f_*）。
  detailEnabled: { type: Boolean, default: false },
  detailColumns: { type: Array, default: () => [] },
  detailFetcher: { type: Function, default: null },
  detailExporter: { type: Function, default: null },
  detailLinkBuilder: { type: Function, default: null },
  detailParamsBuilder: { type: Function, default: null },
  // ===== 筛选条件自定义（2026-10-14 一期：销售订单试点）=====
  // 筛选条件定义：[{ key, label, group, type: text|select|dateRange|numberRange, optionSource?, options?,
  //                  defaultChecked?, std?: 'status'|'date'|'customer'|'supplier'|'employee', cf?: 自定义字段fieldKey }]
  // std = 该条件与「通用搜索栏标准维度」同一口径：值走页面 search 对象（filter-change 通道），
  //       与数据看板穿透 / 统计卡筛选天然互通；其余条件走 f_* 参数（filter-params 事件下发页面）。
  // cf  = 单据自定义字段：汇总模式打包进 customSearch（既有通道），明细模式走 f_cf_doc_*。
  // 声明了非空 filterDefs 才启用自定义筛选区；未声明的页面渲染与改版前完全一致（零回归）。
  filterDefs: { type: Array, default: () => [] },
  // 明细模式专用条件定义（2026-10-14 同日增强：切换到明细模式后查询区跟随切换）：
  // 一般来自明细报表 /columns 的 filters 元数据（含明细行字段，如 商品名称/商品编号/规格），
  // 键 = 明细报表筛选键（f_<key> 直通明细接口）。声明后汇总/明细各用一套条件、各自记忆
  // （配置键 = storageKey 与 storageKey::detail，与列设置双配置同先例）；未声明则明细模式沿用 filterDefs。
  detailFilterDefs: { type: Array, default: () => [] },
  // 字段级权限键（= 单据表名/自定义字段 entity，如 sale_delivery）：与页面汇总列表 useColumnSetting 的第三参一致。
  // 声明后明细列同样按「设置中心-字段权限」隐藏被禁字段（此前明细模式绕过字段权限）
  fieldPermKey: { type: String, default: '' }
})

const emit = defineEmits(['tab-change', 'search', 'reset', 'sort-change', 'page-change', 'selection-change', 'print', 'filter-change', 'filter-params', 'stat-click', 'update:keyword', 'update:selectedRows', 'update:columnSettingVisible', 'update:printVisible', 'update:printBillId', 'batch-action', 'batch-done'])

/**
 * 地址栏筛选条件 → 页内筛选（「带条件进入列表」统一支持，2026-09-18）
 *
 * 背景（用户实测）：小助手 open_page 会把筛选条件拼到地址栏（如 /sale/contracts?status=草稿），
 * 但全项目 88 个列表页里此前只有 9 个自己写了 applyDrillQuery 读 route.query，其余壳化页面一律忽略
 * → 「打开销售合同并只看草稿」进去仍是全量数据（销售订单因自带 applyDrillQuery 才是好的）。
 * 现统一在壳里做，页面零改动：
 *   ① 只认本页 filters 已声明的维度（未声明的不应用，与「未声明不渲染」同口径）+ 关键词；
 *   ② 通过既有 filter-change 事件交给页面写回自己的 search（页面均已实现 onFilterChange）；
 *   ③ 不额外 emit('search')：首查由页面自身 onMounted 的 fetchData 完成，避免同一页发两次请求。
 * 已自带 applyDrillQuery 的 9 个页面（销售订单/客户/即时库存/收款单/费用分摊/商机/销售目标/库存流水/获客结果）
 * 重复赋值幂等，行为不变。
 */
const route = useRoute()
const ownPath = route.path          // 本页自身路径：路由切换瞬间 watcher 可能先触发，用路径挡住
// 维度 → 页面 search 字段名（全系统统一入参名，勿改）
const DIM_FIELDS = {
  status: ['status'],
  date: ['startDate', 'endDate'],
  customer: ['customerId'],
  supplier: ['supplierId'],
  employee: ['employeeId']
}

function applyIncomingQuery() {
  if (route.path !== ownPath) return false
  const q = route.query || {}
  const patch = {}
  props.filters.forEach((dim) => {
    (DIM_FIELDS[dim] || []).forEach((k) => {
      const v = q[k]
      if (v !== undefined && v !== null && String(v).trim() !== '') patch[k] = String(v)
    })
  })
  const kw = q.keyword                                  // 关键词框所有列表页都有
  if (kw !== undefined && kw !== null && String(kw).trim() !== '') patch.keyword = String(kw)
  if (!Object.keys(patch).length) return false
  emit('filter-change', patch)
  return true
}

onMounted(() => { applyIncomingQuery() })
// 同一页再次被带条件进入（看板卡片下钻 / 小助手再次打开同一列表）：只有 query 变化、组件不重建 → 也要应用
watch(() => route.query, () => { applyIncomingQuery() })

/**
 * 统计卡片点击：只有声明了 filter 的卡片才响应，把筛选条件交给页面
 * （页面负责写进自己的 search 并重新查询；卡片高亮由页面的 activeStatKey 控制）
 */
function onStatClick(stat) {
  if (!stat || !stat.filter) return
  emit('stat-click', { key: stat.key || stat.label, filter: stat.filter })
}

const keywordModel = computed({
  get: () => props.keyword,
  set: (v) => emit('update:keyword', v)
})

const printVisibleModel = computed({
  get: () => props.printVisible,
  set: (v) => emit('update:printVisible', v)
})

const printBillIdModel = computed(() => props.printBillId)

const headerCellStyle = {
  background: 'rgba(99, 102, 241, 0.04)',
  color: '#1f2937',
  fontWeight: 600,
  borderBottom: '2px solid #e0e7ff'
}

// 选中行双向透传（v-model:selected-rows）
const selectedRowsModel = computed({
  get: () => props.selectedRows,
  set: (v) => emit('update:selectedRows', v)
})

function handleSelectionChange(rows) {
  selectedRowsModel.value = rows
  emit('selection-change', rows)
}

// ===== 批量操作条联动（2026-09-19）=====
/** el-table 实例：用于"全选本页/清空"两个批量条的辅助操作 */
const tableRef = ref(null)
/** 批量执行器（统一：上限 200 / 串行逐条 / 逐条独立 / 失败原因取后端 message） */
const {
  running: batchRunning,
  progress: batchProgressState,
  result: batchResult,
  runBatch,
  clearResult: clearBatchResult,
  exportFailList
} = useBillBatch()
const batchResultVisible = ref(false)
const batchRetrying = ref(false)

/**
 * 批量动作完成后：① 通知页面（供特殊处理）；② 壳级兜底刷新本页。
 *
 * 背景（用户实测 BUG）：用顶部统计卡点出"草稿"筛选 → 批量提交后 → 卡片数字不更新。
 * 原因是批量执行完既没重查列表、也没重查统计，而统计卡的数字只在页面 onMounted/切回标签页时拉取。
 * 这里复用页面已注册的软刷新函数（useAutoRefresh 注册的那个，通常 = 列表 + 统计一起刷），
 * 因此不需要 35 个页面各写一遍刷新逻辑。
 */
function notifyBatchDone(key, result) {
  emit('batch-done', { key, result })
  try {
    triggerListRefresh([ownPath])
  } catch (e) {
    // 刷新失败不影响批量结果展示
  }
  // 明细数据在本壳内部（页面刷新只刷汇总列表/统计卡）→ 批量动作后必须重取，否则行状态停留在操作前
  if (isDetail.value) scheduleFetchDetail(false)
}

/** 行标识：优先常见单号字段，兜底主键（确认框与失败清单都用它）；明细模式 _docNo 也兜底 */
function defaultRowLabel(row) {
  if (!row || typeof row !== 'object') return String(row || '')
  return row.billNo || row.orderNo || row.contractNo || row.quoteNo || row.deliveryNo || row.returnNo
    || row.invoiceNo || row.requestNo || row.inboundNo || row.outboundNo || row.transferNo
    || row.receiptNo || row.voucherNo || row.expenseNo || row.workOrderNo || row.no || row.code
    || row._docNo || row.id || row._docId || ''
}
/** 行主键（批量导出用；找不到时返回 undefined，由后端忽略）；明细模式 _docId 兜底 */
function defaultRowId(row) {
  if (!row || typeof row !== 'object') return row
  return row.id ?? row.orderId ?? row.contractId ?? row.quoteId ?? row.deliveryId ?? row.returnId
    ?? row.invoiceId ?? row.requestId ?? row.inboundId ?? row.outboundId ?? row.transferId
    ?? row.receiptId ?? row.voucherId ?? row.expenseId ?? row.workOrderId ?? row._docId ?? null
}

/** 点批量按钮：二次确认（列出将处理的单号）→ 执行 → 弹结果面板 */
async function onBatchAction(key) {
  const action = (props.batchActions || []).find(a => a && a.key === key)
  if (!action) return
  const rows = batchBarRows.value || []
  if (rows.length === 0) return
  // 页面自己接管（未在声明里给 exec/entity）→ 交给页面处理
  if (!action.exec && !action.entity) {
    emit('batch-action', key)
    return
  }
  const labelOf = typeof action.labelOf === 'function' ? action.labelOf : defaultRowLabel
  const actionLabel = action.label || key
  // ① 二次确认：列出将处理的单号（最多 10 条 + 总数）
  const preview = rows.slice(0, 10).map(r => labelOf(r)).join('、')
  const more = rows.length > 10 ? tt('…共 ') + rows.length + tt(' 条') : ''
  const isDanger = action.type === 'danger'
  try {
    await ElMessageBox.confirm(
      tt('将对已勾选的 ') + rows.length + tt(' 条执行「') + actionLabel + tt('」：') + preview + more,
      actionLabel + tt('确认'),
      {
        type: isDanger ? 'warning' : 'info',
        confirmButtonText: tt('确认执行'),
        cancelButtonText: tt('取消'),
        dangerouslyUseHTMLString: false
      }
    )
  } catch (e) {
    return // 用户取消
  }
  // ② 批量导出：一次请求（后端按 ids 取数，仍套数据权限）
  if (action.entity) {
    try {
      const idOf = typeof action.idOf === 'function' ? action.idOf : defaultRowId
      const ids = rows.map(idOf).filter(v => v !== null && v !== undefined)
      // 2026-09-24 全站口径：勾选导出同样"页面看到什么列就导出什么列" ——
      // 页面对 batchAction 声明 fields() 时走「按页面可见列导出」；未声明则保持原行为（向后兼容）
      const fields = typeof action.fields === 'function' ? action.fields() : null
      if (fields && fields.length) {
        await exportAsSeen(action.entity, fields, ids)
      } else {
        await exportSelectedRows(action.entity, ids)
      }
      ElMessage.success(tt('已导出所选 ') + ids.length + tt(' 条'))
      notifyBatchDone(key, { ok: ids.map(id => ({ row: { id } })), fail: [], skip: [] })
      onBatchClear()
    } catch (e) {
      ElMessage.error((e && e.message) || tt('批量导出失败'))
    }
    return
  }
  // ③ 逐条执行
  try {
    const res = await runBatch({
      actionLabel,
      rows,
      exec: action.exec,
      skipReason: action.skipReason
    })
    // 无论成功与否都刷新：失败/跳过的行状态也可能已被后端改动（如"已被下游引用"）
    notifyBatchDone(key, res)
    batchResultVisible.value = true
    if (res && res.fail.length === 0 && res.skip.length === 0) {
      ElMessage.success(actionLabel + tt('完成：成功 ') + res.ok.length + tt(' 条'))
    }
  } catch (e) {
    // 上限超限等前置错误：只提示，不弹结果面板
    ElMessage.error((e && e.message) || tt('批量操作失败'))
  }
}

/** 重试失败项（只重跑失败的，成功的不重复执行） */
async function onBatchRetry() {
  const r = batchResult.value
  if (!r || r.fail.length === 0) return
  const action = (props.batchActions || []).find(a => a && a.label === r.actionLabel)
  if (!action || typeof action.exec !== 'function') return
  const rows = r.fail.map(f => f.row)
  batchRetrying.value = true
  try {
    const res = await runBatch({ actionLabel: r.actionLabel, rows, exec: action.exec, skipReason: action.skipReason })
    notifyBatchDone(action.key || r.actionLabel, res)
    batchResultVisible.value = true
    if (res && res.fail.length === 0) ElMessage.success(tt('重试全部成功')) 
  } finally {
    batchRetrying.value = false
  }
}

function onBatchExportFail() {
  exportFailList(tt('批量操作失败清单'))
}

function onBatchResultClose(v) {
  batchResultVisible.value = v
  if (!v) {
    clearBatchResult()
    onBatchClear()
  }
}
/** 全选本页（表头复选框的同效操作） */
function onBatchSelectAll() {
  // 明细模式下全选/全不选作用于明细表格（2026-10-12：明细模式也有批量操作条）
  const ref = isDetail.value ? detailTableRef.value : tableRef.value
  if (ref && typeof ref.toggleAllSelection === 'function') {
    ref.toggleAllSelection()
  }
}
/** 清空选择（会触发 selection-change，页面 selectedRows 随之清空） */
function onBatchClear() {
  const ref = isDetail.value ? detailTableRef.value : tableRef.value
  if (ref && typeof ref.clearSelection === 'function') {
    ref.clearSelection()
  }
}

// 列设置抽屉显隐双向透传（v-model:column-setting-visible）
// ===== 自定义字段列（2026-10-09）=====
// 列设置抽屉需含自定义字段列（否则用户在抽屉里看不到、无法排序/隐藏）；
// 行数据里没有自定义字段值（值存在 EAV 值表），这里按当前页行主键批量取回并写回行对象。
const customFieldColumns = listCustomColumnsRef(props.storageKey)
const allColumnsForDrawer = computed(() => [...(props.columns || []), ...customFieldColumns.value])
const customFieldTarget = resolveListCustomField(props.storageKey)
const cfValueCache = new Map()
async function fillCustomFieldValues(rows) {
  if (!customFieldTarget || !customFieldColumns.value.length) return
  const list = Array.isArray(rows) ? rows : []
  const ids = [...new Set(list.map(r => r && r[customFieldTarget.idKey]).filter(v => v !== undefined && v !== null))]
  if (!ids.length) return
  const missing = ids.filter(id => !cfValueCache.has(id))
  if (missing.length) {
    try {
      const map = await batchGetFieldValues(customFieldTarget.entity, missing)
      missing.forEach(id => cfValueCache.set(id, (map && (map[id] || map[String(id)])) || {}))
    } catch (e) { return } // 取失败静默降级：列仍在、值空，不影响列表
  }
  list.forEach(row => {
    const vals = row ? cfValueCache.get(row[customFieldTarget.idKey]) : null
    if (vals) Object.keys(vals).forEach(k => { row[k] = vals[k] })
  })
}
watch(() => props.tableData, (v) => { fillCustomFieldValues(v) }, { immediate: true })

const columnSettingVisibleModel = computed({
  get: () => props.columnSettingVisible,
  set: (v) => emit('update:columnSettingVisible', v)
})

/**
 * 列宽持久化（2026-09-22 新增，全站列表页统一由本壳接管）
 *
 * 为什么放在壳里：列定义虽然在页面里，但 el-table 实例在壳里 —— 用 @header-dragend 统一收宽度，
 * 再用 el-table expose 的 columns/doLayout 统一恢复，页面零改动（否则要改 35+ 个页面）。
 * pageKey 用 storageKey（与列设置抽屉完全一致），列宽与显隐/顺序存在同一条记录里、互不覆盖。
 */
const { onHeaderDragend, applySavedWidths, resetColWidths } = useColWidth(props.storageKey)

// ==================== 明细视图（2026-10-12 列表页明细模式） ====================
// 汇总/明细两种模式共用本壳：切换入口在列设置抽屉顶部「视图模式」（默认汇总）；
// 明细列配置存在独立 key（storageKey::detail）下，与汇总列配置互不影响、各自记忆。
const colCfgStore = useColumnConfigStore()
const detailRouter = useRouter()
// ===== 明细能力来源（二选一，手动声明优先）=====
//   手动：页面传 detail-enabled/detail-columns/... （销售订单试点，自行映射筛选）
//   自动：本页 storageKey 在 billDetailView.js 登记表里 → 用明细报表 /columns 自动接线（铺开批次，页面零代码）
const dvCfg = BILL_DETAIL_VIEW_CONFIGS[props.storageKey] || null
const autoView = dvCfg ? createAutoDetailView(dvCfg, () => props.keyword, () => props.filterModel) : null
// 全站默认视图装载（2026-10-14 双视图二期）：设置中心「列表默认视图」→ 个人从未切换过的页面跟随全局默认。
// 每次进入页面强制取后端最新（设置中心改完保存回来立即生效）；读取失败保持上次缓存（无缓存=汇总，与升级前一致）。
// 【2026-10-18 修复】原先只对「登记表自动接线」的页面装载默认值，**手动声明明细能力的页面（销售订单等试点页）
// 被漏掉** → 这些页面刷新后永远回汇总（用户实报）。现在自动接线与手动声明两类都装载。
if (dvCfg || props.detailEnabled) {
  const { loadSysConfig, getConfig } = useSysConfig()
  loadSysConfig(true).then(() => {
    colCfgStore.setGlobalDefaultView(
      getConfig('sys.list.default.view', '汇总') === '明细' ? 'detail' : 'summary')
  }).catch(() => {})
}
const detailEnabledEff = computed(() => props.detailEnabled || !!autoView)
const detailColumnsEff = computed(() => (props.detailEnabled ? props.detailColumns : (autoView ? autoView.columns.value : [])))
function detailFetcherEff () { return props.detailEnabled ? props.detailFetcher : (autoView ? autoView.fetcher : null) }
function detailExporterEff () { return props.detailEnabled ? props.detailExporter : (autoView ? autoView.exporter : null) }
function detailLinkEff (row) {
  if (props.detailEnabled) return props.detailLinkBuilder ? props.detailLinkBuilder(row) : ''
  return autoView ? autoView.linkBuilder(row) : ''
}
function detailParamsEff () {
  const base = props.detailEnabled
    ? (props.detailParamsBuilder ? props.detailParamsBuilder() : {})
    : (autoView ? autoView.paramsBuilder() : {})
  // 明细条件合并（2026-10-14 全面推广）：明细模式专属条件（f_* 同键）由本壳统一收集下发，
  // 自动接线页面零代码也能在明细模式带上非标准条件（标准维度仍走 filterModel 通道，二者键不重叠）
  return { ...(lastDetailParams.value || {}), ...base }
}
const viewModeModel = computed({
  get: () => colCfgStore.getViewMode(props.storageKey),
  set: (m) => colCfgStore.setViewMode(props.storageKey, m)
})
const detailPageKey = (props.storageKey || 'BillListPage') + '::detail'
const detailColumnsRef = detailColumnsEff
const detailColApi = useColumnSetting(detailPageKey, detailColumnsRef, props.fieldPermKey || undefined)
const isDetail = computed(() => detailEnabledEff.value && viewModeModel.value === 'detail')

// 数量列显示精度（2026-11-10）：明细模式的列清单由报表接口下发（没有列类型），
// 按「字段名 / 列标题」识别数量语义，统一读设置中心「数量小数位数」显示，不再直接显示库里的原始值。
const { scaleFor: qtyScaleFor, trimZero: qtyTrimZero, formatWith: qtyFormatWith, keyForProp: qtyKeyForProp } = usePrecision()
function isQtyCol(col) {
  if (!col) return false
  if (col.type === 'quantity') return true
  return qtyKeyForProp(col.prop) === 'qty' || qtyKeyForProp(col.label) === 'qty'
}
function qtyCellText(row, col) {
  const v = row ? row[col.prop] : null
  if (v == null || v === '') return ''
  return qtyFormatWith(v, qtyScaleFor('qty'), qtyTrimZero.value)
}

// ==================== 筛选条件自定义（2026-10-14 一期：销售订单试点） ====================
// 页面声明 filterDefs 即启用自定义筛选区；显隐与顺序由「筛选设置」控制（按登录人+公司隔离），
// 与列设置两套独立配置。参数下发口径：
//   - std 条件（状态/日期/客户/销售员）：值走页面 search 对象（filter-change 通道），与看板穿透/统计卡互通；
//   - 其余条件：以 f_<key>（区间 f_<key>From/To、f_<key>Min/Max）经 filter-params 事件下发页面，
//     键与单据明细报表引擎一致 → 明细模式把同一批参数原样透传即可，两模式一套条件；
//   - 单据自定义字段（cf）：汇总模式打包进 customSearch（既有通道），明细模式走 f_cf_doc_<fieldKey>。
// 2026-10-14 同日增强：声明 detailFilterDefs 后，明细模式使用独立一套条件（含明细行字段），
// 配置键 storageKey::detail（与列设置双配置同先例），切换视图模式查询区跟随切换。
// 注意：本块必须在 isDetail 声明之后（值容器联动 watch 依赖它）。
const customSearchActive = computed(() => Array.isArray(props.filterDefs) && props.filterDefs.length > 0)
const detailDefsAvailable = computed(() => Array.isArray(props.detailFilterDefs) && props.detailFilterDefs.length > 0)
const filterSettingVisible = ref(false)
const summaryFilterSetting = useFilterSetting(props.storageKey, () => props.filterDefs)
const detailFilterSetting = useFilterSetting(props.storageKey + '::detail', () => props.detailFilterDefs)
/** 当前生效的条件集合：明细模式且有明细专属定义 → 明细套件，否则汇总套件 */
const activeFilterDefs = computed(() => (isDetail.value && detailDefsAvailable.value) ? props.detailFilterDefs : props.filterDefs)
const visibleHeaderFilters = computed(() => {
  const setting = (isDetail.value && detailDefsAvailable.value) ? detailFilterSetting : summaryFilterSetting
  return setting.visibleFilters.value
})

/** 动态条件值容器（汇总 / 明细各一份）：text/select → 标量，dateRange → [起,止]，numberRange → {min,max} */
const dynValues = reactive({})
const detailDynValues = reactive({})
const activeDynValues = computed(() => (isDetail.value && detailDefsAvailable.value) ? detailDynValues : dynValues)

function emptyValueOf(def) {
  if (def.type === 'dateRange') return []
  if (def.type === 'numberRange') return { min: '', max: '' }
  return ''
}

/** 可见条件集合变化（保存/恢复筛选设置/切换模式）后补齐值容器，避免 v-model 指向不存在的属性 */
watch([summaryFilterSetting.visibleFilters, detailFilterSetting.visibleFilters, isDetail], () => {
  for (const f of summaryFilterSetting.visibleFilters.value) {
    if (dynValues[f.key] === undefined) dynValues[f.key] = emptyValueOf(f)
  }
  for (const f of detailFilterSetting.visibleFilters.value) {
    if (detailDynValues[f.key] === undefined) detailDynValues[f.key] = emptyValueOf(f)
  }
}, { immediate: true })

/** 标准维度双向同步：页面 search 对象被外部写入（看板穿透 / 统计卡 / 重置）时回填两套套件的控件显示 */
watch(() => props.filterModel, () => {
  if (!customSearchActive.value) return
  const m = props.filterModel || {}
  for (const defs of [props.filterDefs, props.detailFilterDefs]) {
    for (const f of (defs || [])) {
      if (!f.std) continue
      let v
      if (f.std === 'date') v = (m.startDate || m.endDate) ? [m.startDate || '', m.endDate || ''] : []
      else if (f.std === 'status') v = m.status || ''
      else if (f.std === 'customer') v = m.customerId || ''
      else if (f.std === 'employee') v = m.employeeId || ''
      else continue
      dynValues[f.key] = v
      detailDynValues[f.key] = v
    }
  }
}, { deep: true, immediate: true })

/** 标准维度：控件值写回页面 search（filter-change 通道，保持单一路径） */
function emitStdPatch(def) {
  const v = activeDynValues.value[def.key]
  let patch = null
  if (def.std === 'status') patch = { status: v || '' }
  else if (def.std === 'date') patch = { startDate: (Array.isArray(v) && v[0]) || '', endDate: (Array.isArray(v) && v[1]) || '' }
  else if (def.std === 'customer') patch = { customerId: v || '' }
  else if (def.std === 'supplier') patch = { supplierId: v || '' }
  else if (def.std === 'employee') patch = { employeeId: v || '' }
  if (patch) emit('filter-change', patch)
}

/** 默认参数键（与明细报表引擎同键：f_<key> / From/To / Min/Max） */
function paramKeysOf(def) {
  if (def.paramKeys) return def.paramKeys
  if (def.type === 'dateRange') return { from: 'f_' + def.key + 'From', to: 'f_' + def.key + 'To' }
  if (def.type === 'numberRange') return { min: 'f_' + def.key + 'Min', max: 'f_' + def.key + 'Max' }
  return { value: 'f_' + def.key }
}

/** 遍历一套条件把已填值收集进参数目标（std 条件跳过——由页面 search 通道提供） */
function collectFilterParams(defs, container, toParams, toCf) {
  for (const f of defs) {
    if (f.std) continue
    const v = container[f.key]
    const pk = paramKeysOf(f)
    if (f.type === 'dateRange') {
      if (Array.isArray(v)) {
        if (v[0]) toParams[pk.from] = v[0]
        if (v[1]) toParams[pk.to] = v[1]
      }
    } else if (f.type === 'numberRange') {
      const min = v && v.min !== undefined ? String(v.min).trim() : ''
      const max = v && v.max !== undefined ? String(v.max).trim() : ''
      if (min !== '') toParams[pk.min] = min
      if (max !== '') toParams[pk.max] = max
    } else {
      const s = v === undefined || v === null ? '' : String(v).trim()
      if (s !== '') {
        if (toCf && f.cf) toCf[f.cf] = s
        else toParams[pk.value] = s
      }
    }
  }
}

/** 明细条件参数留底：自动接线页面的明细取数合并用（detailParamsEff），与下发页面的同一份 */
const lastDetailParams = ref({})

/**
 * 汇总当前自定义条件的查询参数并下发页面（两套套件同时收集，互不影响）：
 * payload = { params: {f_*...汇总用}, customSearch: 'json'|undefined, detailParams: {f_*...明细用} }
 */
function emitFilterParams() {
  if (!customSearchActive.value) return
  const params = {}
  const cfVals = {}
  collectFilterParams(summaryFilterSetting.visibleFilters.value, dynValues, params, cfVals)
  const detailParams = {}
  // 明细条件全部直接 f_<key>（明细行字段/自定义字段键=引擎同键，直通明细接口），不走 customSearch
  collectFilterParams(detailFilterSetting.visibleFilters.value, detailDynValues, detailParams, null)
  lastDetailParams.value = detailParams
  const customSearch = Object.keys(cfVals).length ? JSON.stringify(cfVals) : undefined
  emit('filter-params', { params, customSearch, detailParams })
}

/** 控件值变化：只存值（不触发查询；文本/数值在回车/失焦提交，下拉/日期在选中提交） */
function onDynValueChange(def, v) {
  activeDynValues.value[def.key] = v
}

/** 控件值提交：同步标准维度、发参数、触发查询（明细模式查明细，与右上角「搜索」按钮同分支） */
function onDynFilterSubmit(def) {
  if (def.std) emitStdPatch(def)
  emitFilterParams()
  onSearchClick()
}

/** 筛选设置保存/恢复默认：写入当前模式的配置套件 → 补值、发参数、按新条件重新取数 */
function onHeaderFilterSave(cfg) {
  ;(isDetail.value && detailDefsAvailable.value ? detailFilterSetting : summaryFilterSetting).saveFilterConfig(cfg)
  emitFilterParams()
  onSearchClick()
}
function onHeaderFilterReset() {
  ;(isDetail.value && detailDefsAvailable.value ? detailFilterSetting : summaryFilterSetting).resetFilterConfig()
  emitFilterParams()
  onSearchClick()
}

// 首次挂载也发一次参数（空参数 = 与旧接口入参一致，页面合并零差异）
onMounted(() => { emitFilterParams() })
/** 列设置抽屉当前该展示哪套列（跟随视图模式） */
const activeDrawerColumns = computed(() => (isDetail.value ? detailColApi.allColumns.value : allColumnsForDrawer.value))
const visibleDetailColumns = computed(() => detailColApi.sortedColumns.value.filter(c => detailColApi.isColVisible(c.prop)))
const detailColDefByProp = computed(() => {
  const m = {}
  // 用「生效的明细列」建索引：手动声明页面取 props.detailColumns，
  // 自动接线页面取明细报表 /columns 映射结果（此前只取 props → 自动接线页合计行全部取不到列定义 → 合计空白）
  ;(detailColumnsEff.value || []).forEach(c => { m[c.prop] = c })
  return m
})

const detailData = ref([])
const detailLoading = ref(false)
const detailTotals = ref({})
const detailPagination = reactive({ current: 1, size: 50, total: 0 })
const detailSortState = reactive({ sortBy: '', sortOrder: '' })
const detailSelected = ref([])
const detailExporting = ref(false)
const detailTableRef = ref(null)

/**
 * 批量操作/页头按钮用的"勾选行"（2026-10-12 统一口径）：
 * 汇总模式 = 汇总表格勾选（单据维度，原样）；
 * 明细模式 = 明细勾选行按单据去重（一行 = 一条明细，同一张单勾了 3 行也只算 1 张单据）。
 * 2026-10-22 修复：明细报表 API 只返回 _docId（单据主键），不返回汇总表格行的实体 ID 字段
 * （如 contractId / orderId / quoteId / inboundId / returnId …）——页面 batchActions.exec 硬写
 * row.contractId 在明细模式下取到 undefined，批量提交/审批/删除全部静默失败。
 * 修法：去重后把 _docId 镜像成所有"单据自身 ID 字段名"（只填 null/undefined 的，
 * 不覆盖明细行本来就有的外键 customerId/materialId 等），页面 exec 零改动即可同时兼容两种模式。
 */
const BILL_SELF_ID_FIELDS = [
  'id',                                  // 通用兜底（ProduceAssemble/Disassemble/MrpPlan/OperationReport/Subcontract/Finance 等 20+ 页 exec 用 row.id）
  'contractId', 'orderId', 'quoteId', 'returnId', 'deliveryId', 'inboundId',
  'requestId', 'invoiceId', 'transferId', 'checkId', 'outboundId',
  'receiptId', 'voucherId', 'expenseId', 'workOrderId', 'requisitionId',
  'supplementId', 'pickId', 'sampleId', 'freeSampleId',
  'subcontractOrderId', 'subcontractReturnId', 'subcontractSupplementId',
  'assembleId', 'disassembleId', 'operationReportId', 'mrpPlanId',
  'salePreReceiptId', 'purchasePrePaymentId', 'wageSettlementId', 'productionReceiptId',
  // 旧名兜底（壳原本写 materialRequisitionId，但页面 exec 全用 requisitionId；此处保留兼容，互不冲突）
  'materialRequisitionId'
]
const batchBarRows = computed(() => {
  if (!isDetail.value) return props.selectedRows
  const seen = new Set()
  const out = []
  for (const r of (detailSelected.value || [])) {
    const key = r?._docId
    if (key == null || seen.has(key)) continue
    seen.add(key)
    // 明细报表 API 只给 _docId，补镜像字段让页面 exec/idOf/skipReason 零改动可跑
    for (const f of BILL_SELF_ID_FIELDS) {
      if (r[f] == null) r[f] = r._docId
    }
    out.push(r)
  }
  return out
})

async function fetchDetail() {
  const fetcher = detailFetcherEff()
  if (!fetcher) return
  detailLoading.value = true
  try {
    const params = { ...detailParamsEff() }
    if (detailSortState.sortBy) { params.sortBy = detailSortState.sortBy; params.sortOrder = detailSortState.sortOrder }
    // status 始终请求（2026-10-12）：明细模式批量提交/审批/删除的"状态跳过判断"依赖它，
    // 用户把"单据状态"列藏掉也不能让批量动作把全部行误判成"状态不明"而跳过
    const colKeys = visibleDetailColumns.value.map(c => c.prop)
    if (detailColDefByProp.value.status && !colKeys.includes('status')) colKeys.push('status')
    params.cols = colKeys.join(',')
    const res = await fetcher({ page: detailPagination.current, size: detailPagination.size, params })
    detailData.value = res?.records || []
    detailPagination.total = res?.total || 0
    detailTotals.value = res?.totals || {}
  } catch (e) {
    detailData.value = []
    detailPagination.total = 0
    detailTotals.value = {}
    ElMessage.error((e && e.message) || tt('明细查询失败'))
  } finally {
    detailLoading.value = false
    nextTick(() => detailColApi.applySavedWidths(detailTableRef))
  }
}

// 进入明细模式自动取数（immediate：上次离开时存了"明细"，本次打开页面直接落在明细模式也要查）；
// 退回汇总清掉明细勾选（两种模式的勾选互不干扰）。
// 模式切换会同时触发 isDetail 与列配置版本两个 watcher → 用 50ms 去抖合并成一次请求。
let detailFetchTimer = null
function scheduleFetchDetail(resetPage) {
  if (resetPage) detailPagination.current = 1
  if (detailFetchTimer) clearTimeout(detailFetchTimer)
  detailFetchTimer = setTimeout(() => fetchDetail(), 50)
}
watch(isDetail, (v, old) => {
  if (v) scheduleFetchDetail(true)
  else {
    detailSelected.value = []
    // 切回汇总：明细模式改过的标准条件已写进页面 search，汇总列表若还是旧结果会与查询区对不上 → 重取一次
    if (old === true) emit('search')
  }
}, { immediate: true })
// 页面筛选条件变化（统计卡片点击 / 看板下钻带条件进入 / 状态页签 / 标准维度写回）→ 明细模式重新取数。
// （2026-10-19 修复用户实报：明细模式下点统计卡片，页面只刷新汇总列表，下方明细数据不动——
//  明细取数此前只挂在「进入明细模式 / 列设置变化 / 点搜索按钮」三个触发点上，
//  漏了"页面查询对象被外部写入"这条链路。50ms 去抖会与搜索按钮等触发点合并成一次请求。）
// 2026-11-27 修复：关键词框与筛选模型是同一个对象（v-model:keyword="search.keyword"），
// deep watch 会把逐字输入也当成"外部条件变化"→ 明细模式边打字边请求（汇总模式只在搜索/回车时查）。
// 因此按「去掉 keyword 后的条件指纹」监听：关键词仍由搜索/回车提交。
const filterSignature = computed(() => {
  const m = props.filterModel || {}
  const rest = {}
  for (const k of Object.keys(m)) {
    if (k !== 'keyword') rest[k] = m[k]
  }
  return JSON.stringify(rest)
})
watch(filterSignature, () => {
  if (isDetail.value) scheduleFetchDetail(true)
})
// 明细列配置变化（列设置保存/恢复默认）→ 重新取数（新勾的列必须带数据，与报表页同口径）
// 【2026-11-27 多页签修复】原实现监听 store.version（全局版本号）：随便哪个页签保存列设置，
// 都会让本页明细表重建并跳回第一页重查。改为监听「本页明细列的显隐/顺序/固定」指纹，
// 两个明细页签各存各的配置（storageKey::detail），互不干扰。
const detailTableKey = computed(() => {
  const columns = colCfgStore.config[detailPageKey]?.columns || {}
  return Object.keys(columns).sort().map((k) => {
    const c = columns[k] || {}
    return k + ':' + (c.visible !== false ? 1 : 0) + ':' + (c.order ?? '') + ':' + (c.fixed ?? '')
  }).join('|')
})
watch(detailTableKey, () => {
  if (isDetail.value) scheduleFetchDetail(true)
})

// 【2026-11-09 明细模式切回菜单刷新】汇总列表由页面自己的 useAutoRefresh 负责；
// 明细表数据在本组件内部，必须在页面被重新激活时同步重取一次，否则切回菜单后明细仍是旧数据。
let detailEverDeactivated = false
onDeactivated(() => { detailEverDeactivated = true })
onActivated(() => {
  if (!detailEverDeactivated) return
  if (isDetail.value) scheduleFetchDetail(false)
})

function onDetailPageChange() { fetchDetail() }

function onDetailSortChange({ prop, order }) {
  if (!prop || !order) { detailSortState.sortBy = ''; detailSortState.sortOrder = '' }
  else { detailSortState.sortBy = prop; detailSortState.sortOrder = order === 'ascending' ? 'asc' : 'desc' }
  fetchDetail()
}

function onDetailSelectionChange(rows) { detailSelected.value = rows || [] }

function clearDetailSelection() {
  if (detailTableRef.value && typeof detailTableRef.value.clearSelection === 'function') detailTableRef.value.clearSelection()
}

/** 明细"单号"点击 → 跳单据（手动声明用 detailLinkBuilder；自动接线用登记表 linkPrefix） */
function goDetailBill(row) {
  const path = detailLinkEff(row)
  if (path) detailRouter.push(path)
}

/** 回写列着色：「已发 3 / 5」（有比值：齐=绿、部分=橙、0=灰）；「已收 100.00」（金额：>0=橙） */
function writebackClass(v) {
  const s = String(v ?? '')
  const m = s.match(/([\d.]+)\s*\/\s*([\d.]+)/)
  if (m) {
    const done = Number(m[1])
    const total = Number(m[2])
    if (total > 0 && done >= total) return 'wb-full'
    if (done > 0) return 'wb-part'
    return 'wb-none'
  }
  const a = s.match(/([\d.]+)/)
  return a && Number(a[1]) > 0 ? 'wb-part' : 'wb-none'
}

/** 明细状态列着色（通用中文状态口径） */
function detailStatusTagType(v) {
  const s = String(v || '')
  if (s.indexOf('已生效') >= 0) return 'success'
  if (s.indexOf('待审核') >= 0 || s.indexOf('待审批') >= 0) return 'warning'
  if (s.indexOf('驳回') >= 0) return 'danger'
  return 'info'
}

/** 金额单元格：跟随设置中心「金额小数位数 / 省略多余的0」（此前固定两位） */
function amountCellText(v) {
  return qtyFormatWith(v ?? 0, qtyScaleFor('amount'), qtyTrimZero.value)
}

/** 参与合计行的列类型（数量/金额类） */
const DETAIL_SUM_TYPES = ['amount', 'netAmount', 'number', 'quantity', 'int', 'discount']
const DETAIL_AMOUNT_TYPES = ['amount', 'netAmount', 'discount']

/** 合计行：后端 totals 按当前筛选全量合计（明细列直接求和、单据级列按单据去重求和）；只给金额/数量列回值 */
function detailSummaryMethod({ columns, data }) {
  const result = []
  const hasRows = Array.isArray(data) && data.length > 0
  columns.forEach((column, index) => {
    // 无数据时不出合计行（与明细报表页同口径，避免空列表下显示一屏 0.00）
    if (!hasRows) { result[index] = ''; return }
    // 第 0 列是勾选列，第 1 列才是序号列 —— 「合计」落在序号列上，不挤进 46px 的勾选列
    if (index === 1) { result[index] = tt('合计'); return }
    const prop = column.property
    const def = prop ? detailColDefByProp.value[prop] : null
    if (!def || !DETAIL_SUM_TYPES.includes(def.type)) { result[index] = ''; return }
    const v = detailTotals.value?.[prop]
    if (v === undefined || v === null || v === '') { result[index] = ''; return }
    // 金额类按「金额小数位数」、数量类按「数量小数位数」显示（此前一律 toFixed(2)）
    const scaleKey = DETAIL_AMOUNT_TYPES.includes(def.type) ? 'amount' : 'qty'
    result[index] = qtyFormatWith(v, qtyScaleFor(scaleKey), qtyTrimZero.value)
  })
  return result
}

/** 导出选中明细行 / 当前筛选全部明细（blob 下载由页面 detailExporter 完成） */
async function exportDetailSelected() {
  const ids = (detailSelected.value || []).map(r => r._itemId).filter(v => v !== undefined && v !== null)
  if (!ids.length) { ElMessage.warning(tt('请先勾选要导出的明细行')); return }
  await doDetailExport(ids)
}
async function exportDetailFiltered() { await doDetailExport(null) }
async function doDetailExport(itemIds) {
  const exporter = detailExporterEff()
  if (!exporter) return
  detailExporting.value = true
  try {
    // 与明细取数同一份参数（含明细模式筛选条件 f_* 与当前排序）：
    // 此前只传列和行主键 → 导出当前筛选时漏掉明细条件，导出的行比屏幕上多（2026-11-27 修复）
    const params = { ...detailParamsEff() }
    if (detailSortState.sortBy) { params.sortBy = detailSortState.sortBy; params.sortOrder = detailSortState.sortOrder }
    await exporter({ cols: visibleDetailColumns.value.map(c => c.prop).join(','), itemIds, params })
    ElMessage.success(tt('明细导出成功'))
  } catch (e) {
    ElMessage.error((e && e.message) || tt('明细导出失败'))
  } finally { detailExporting.value = false }
}

/** 搜索/重置按钮：明细模式下查明细（汇总行为不变）；重置时同时清空自定义条件的值 */
function onSearchClick() {
  if (isDetail.value) scheduleFetchDetail(true)
  else emit('search')
}
function onResetClick() {
  if (customSearchActive.value) {
    // 清空非标准维度条件值（标准维度由页面 handleReset 清 search 后经 watcher 回填控件显示）。
    // 明细模式用自己的条件套件（detailDynValues）——此前只清汇总那套，
    // 导致明细模式点重置后条件框与列表筛选都还在（2026-11-27 修复）
    for (const f of (props.filterDefs || [])) {
      if (!f.std) dynValues[f.key] = emptyValueOf(f)
    }
    for (const f of (props.detailFilterDefs || [])) {
      if (!f.std) detailDynValues[f.key] = emptyValueOf(f)
    }
    emitFilterParams()
  }
  emit('reset')
  if (isDetail.value) nextTick(() => scheduleFetchDetail(true))
}

function restoreColumnWidths() {
  nextTick(() => applySavedWidths(tableRef))
}

onMounted(restoreColumnWidths)
// 列配置版本变化（列设置保存/恢复默认）会让 el-table 重建 → 需要重新套用列宽
watch(() => props.tableKey, restoreColumnWidths)
// 每次数据加载完成后再套一次：列对象在重渲染后可能被重置
watch(() => props.loading, (v) => { if (!v) restoreColumnWidths() })

/** 列设置抽屉的 change：恢复默认列宽需要壳重排表格；明细模式下保存列配置后必须重新取数（cols 变了） */
function onColumnSettingChange(e) {
  if (e && e.type === 'resetWidths') {
    if (isDetail.value) nextTick(() => detailColApi.resetColumnWidths(detailTableRef))
    else nextTick(() => resetColWidths(tableRef))
    return
  }
  if (isDetail.value) {
    // 重新取数由 detailTableKey 指纹 watcher 统一触发（配置未变时不重复请求）
    nextTick(() => detailColApi.applySavedWidths(detailTableRef))
  }
}
</script>

<style scoped lang="scss">
/* ===== 以下样式逐字迁移自 SaleQuoteList.vue（批次A壳组件） ===== */
.bill-list-page {
  // 列表页外层留白随「界面密度」联动；内容区本身已有留白，这里只补一档，避免左右双份空白
  padding: var(--erp-content-padding, 8px);
  background: linear-gradient(135deg, #f8faff 0%, #f0f4ff 50%, #f8fcff 100%);
  min-height: calc(100vh - 60px);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--erp-card-gap, 8px);

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

/* 统计卡片的样式已随通用件迁到 components/bill/StatCards.vue（壳与手写页共用一套，避免多处各写一份） */

/* ===== 明细视图（2026-10-12 列表页明细模式） ===== */
.detail-select-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  margin-bottom: 8px;
  background: #f0f7ff;
  border: 1px dashed #91caff;
  border-radius: 6px;

  .dsb-info {
    font-size: 12px;
    color: #606266;
    b { color: #1890ff; }
  }
}

.detail-link {
  color: #1890ff;
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 2px;
  &:hover { color: #40a9ff; }
}

.detail-amount { font-variant-numeric: tabular-nums; }

.wb-text {
  font-size: 12px;
  &.wb-full { color: #52c41a; }
  &.wb-part { color: #fa8c16; }
  &.wb-none { color: #909399; }
}

.detail-empty {
  padding: 28px 0 12px;
  text-align: center;
  .de-title { font-size: 14px; color: #606266; margin-bottom: 6px; }
  .de-sub { font-size: 12px; color: #909399; }
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
  align-items: flex-start;   /* 筛选区换行成多行时，右栏仍固定在「第一行的高度」，不做垂直居中 */
  gap: 16px;
  padding: 16px 20px;
  border-bottom: 1px solid #f3f4f6;

  /* 两栏固定：左栏筛选区（可换行，以后新增搜索框都在这栏里长） + 右栏操作区（固定第一行高度、齿轮贴右边框） */
  .search-cols {
    display: flex;
    align-items: flex-start;
    gap: 16px;
    flex: 1 1 auto;
    min-width: 0;
  }

  .search-filters {
    display: flex;
    align-items: center;
    align-content: flex-start;
    flex-wrap: wrap;
    gap: 12px;
    flex: 1 1 auto;
    min-width: 0;

    /* 每个筛选控件都带标签，用户一眼看出这个框搜什么；
       统一宽度 + 标签固定 60px 列宽 → 换行后两行/三行列严格对齐（用户 2026-09-16 口径） */
    .filter-item {
      display: flex;
      align-items: center;
      gap: 8px;
      flex: 0 0 auto;
      width: calc(var(--erp-list-search-width, 280px) + 68px);   /* 标签列 60 + 间距 8 */
    }

    .filter-label {
      flex: 0 0 60px;
      width: 60px;
      text-align: right;                                          /* 标签右对齐 → 控件左边缘成一条线 */
      font-size: 13px;
      color: #6b7280;
      white-space: nowrap;
    }

    /* 关键词框撑满标签之后剩余宽度（仍随「界面尺寸设置」的变量缩放） */
    .filter-item .search-input {
      flex: 1 1 auto;
      width: auto;
    }
  }

  .search-actions {
    display: flex;
    align-items: center;
    align-self: flex-start;   /* 始终与筛选区第一行同高 */
    gap: 12px;
    flex: 0 0 auto;
    margin-left: auto;
    padding-left: 16px;
    border-left: 1px solid #f3f4f6;
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

// 界面尺寸可调（设置中心 → 自定义 → 界面尺寸设置）
// 本期只对销售订单列表生效（.sale-order-page 由销售订单列表传入 page-class）；
// 其余使用本壳的列表页保持搜索框 280px、表头/单元格 14px 原值不变
.bill-list-page.sale-order-page {
  .search-input { width: var(--erp-list-search-width, 280px); }
  :deep(.el-table th.el-table__cell),
  :deep(.el-table td.el-table__cell) { font-size: var(--erp-list-font-size, 14px); }
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

/* ===== 自定义筛选区（2026-10-14 筛选条件自定义）：操作行 + 多行网格条件区 =====
   外层必须是块级容器（查询区 .search-bar 是横向 flex，工具行与网格直接放里面会被挤成一行一个） */
.custom-search {
  width: 100%;
  min-width: 0;
}

.filter-toolbar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f3f4f6;
  margin-bottom: 12px;
}

.search-grid {
  display: grid;
  /* 与确认版设计图一致：常规屏一行 4 个，窄屏退 3 列 / 2 列（用户口径：一行两个太少） */
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px 16px;
  align-items: center;

  .filter-item {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
  }

  /* 标签统一 64px 右对齐（放不下省略号 + title 提示），保证多行控件左缘成一条线 */
  .filter-label {
    flex: 0 0 64px;
    width: 64px;
    text-align: right;
    font-size: 13px;
    color: #6b7280;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .filter-item > .el-input,
  .filter-item > .el-select,
  .filter-item > .el-date-editor {
    flex: 1 1 auto;
    min-width: 0;
    width: auto;
  }

  /* 日期区间默认固定宽（350px），在网格列内必须撑满列宽，否则列对齐被破坏 */
  .filter-item > .el-date-editor.el-input__wrapper,
  .filter-item > .el-date-editor {
    width: 100% !important;
  }
}

@media (max-width: 1500px) {
  .search-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
@media (max-width: 1150px) {
  .search-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

/* 列设置（自定义筛选区启用时移到表格右上角：它管表格列，与管搜索条件的筛选设置分开） */
.table-tools {
  display: flex;
  justify-content: flex-end;
  padding: 8px 12px 0;
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
  .bill-list-page { padding: 12px; }
  .search-bar {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }
  .status-tabs { flex-wrap: wrap; }
}
</style>
