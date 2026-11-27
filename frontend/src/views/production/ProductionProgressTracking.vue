<template>
  <div class="board-page">
    <!-- 页头 -->
    <div class="page-header">
      <div class="header-left">
        <div class="back-btn" @click="$router.push('/production')">
          <el-icon><ArrowLeft /></el-icon>
          <span>{{ $tt("返回") }}</span>
        </div>
        <div class="title-group">
          <div class="page-title">
            {{ $tt("车间进度跟踪") }}
            <span class="title-badge">PROGRESS</span>
          </div>
          <div class="page-sub">{{ $tt("按工单排线，每道工序一个圆环：环内显示完成百分比，完工显示对勾；点工序环即可就地报工/暂停/完工/回退/判废/下推补料") }}</div>
        </div>
      </div>
      <div class="header-actions">
        <el-button :icon="Setting" v-permission="perm('columnSetting')" @click="stationSettingVisible = true">{{ $tt("站点设置") }}</el-button>
        <el-button :icon="Refresh" v-permission="perm('refresh')" :loading="loading" @click="loadData">{{ $tt("刷新") }}</el-button>
      </div>
    </div>

    <!-- 工具条：派工筛选 / 含已完工 / 筛选 / 列设置 / 统计 -->
    <div class="hud">
      <span class="hud-label">{{ $tt("派工") }}</span>
      <el-radio-group v-model="assigneeFilter" size="small" data-hud="assignee" @change="loadData">
        <el-radio-button value="all">{{ $tt("全部") }}</el-radio-button>
        <el-radio-button value="assigned">{{ $tt("已派工") }}</el-radio-button>
        <el-radio-button value="unassigned">{{ $tt("未派工") }}</el-radio-button>
      </el-radio-group>

      <el-checkbox v-model="includeFinished" @change="loadData">{{ $tt("含已完工/已取消") }}</el-checkbox>

      <!-- 筛选：选字段 + 填条件，可叠加多条 -->
      <el-popover v-model:visible="filterVisible" placement="bottom-start" :width="430" trigger="click">
        <template #reference>
          <el-button :icon="Filter" size="small" class="filter-btn" :class="{ 'is-active': activeFilterCount > 0 }">
            {{ $tt("筛选") }}
            <span v-if="activeFilterCount > 0" class="filter-count">{{ activeFilterCount }}</span>
            <span class="filter-caret">▾</span>
          </el-button>
        </template>

        <div class="flt-panel">
          <div class="flt-head">
            <span class="flt-title">{{ $tt("筛选条件") }}</span>
            <span class="flt-hint">{{ $tt("多条件叠加生效") }}</span>
          </div>

          <div class="flt-rows">
            <div v-for="(c, idx) in filterConds" :key="c.id" class="flt-row">
              <el-select v-model="c.field" size="small" class="flt-field" @change="onFilterFieldChange(c)">
                <el-option :label="$tt('工单号')" value="orderNo" />
                <el-option :label="$tt('物料编码')" value="materialCode" />
                <el-option :label="$tt('物料名称')" value="materialName" />
                <el-option :label="$tt('状态')" value="status" />
                <el-option :label="$tt('计划完工')" value="planEnd" />
              </el-select>

              <template v-if="c.field === 'status'">
                <el-select v-model="c.value" size="small" clearable :placeholder="$tt('选择状态')" class="flt-val" @change="onFilterChange">
                  <el-option v-for="s in OP_STATUS_LIST" :key="s" :label="$tf(s)" :value="s" />
                </el-select>
              </template>

              <template v-else-if="c.field === 'planEnd'">
                <el-date-picker v-model="c.from" type="date" size="small" value-format="YYYY-MM-DD" :placeholder="$tt('开始')" class="flt-date" @change="onFilterChange" />
                <span class="flt-tilde">~</span>
                <el-date-picker v-model="c.to" type="date" size="small" value-format="YYYY-MM-DD" :placeholder="$tt('结束')" class="flt-date" @change="onFilterChange" />
              </template>

              <template v-else>
                <el-input v-model="c.value" size="small" clearable :placeholder="$tt('输入关键字')" class="flt-val" @change="onFilterChange" @keyup.enter="onFilterChange" />
              </template>

              <el-icon class="flt-del" :title="$tt('删除条件')" @click="removeFilter(idx)"><Close /></el-icon>
            </div>
          </div>

          <div class="flt-foot">
            <el-button text size="small" @click="addFilter">{{ $tt("添加条件") }}</el-button>
            <el-button text size="small" @click="clearFilters">{{ $tt("清空") }}</el-button>
            <el-button type="primary" size="small" @click="onFilterChange">{{ $tt("查询") }}</el-button>
          </div>
        </div>
      </el-popover>

      <!-- 列设置：勾选显示哪些列 + 拖动排序（按「登录人 + 所属公司」各自保存） -->
      <el-button :icon="Setting" size="small" v-permission="perm('columnSetting')" @click="columnSettingVisible = true">{{ $tt("列设置") }}</el-button>

      <span class="total-info" data-total="1">{{ $tt("共") }} {{ groups.length }} {{ $tt("条线路") }} · {{ totalOps }} {{ $tt("道工序") }}</span>
    </div>

    <div v-loading="loading" class="board-body">
      <OperationProgressLines
        v-if="groups.length"
        :groups="groups"
        :columns="visibleColumns"
        :sort-field="sortField"
        :sort-order="sortOrder"
        :station-fields-of="stationFieldsOf"
        @open-panel="openPanel"
        @open-parent="openParent"
        @sort-change="onSortChange"
      />

      <el-empty
        v-if="!loading && !groups.length"
        class="board-empty"
        :description="$tt('本看板只显示「已生效」工单的工序：请先把工单提交生效；若工单生效时没配工序，可到「生产工单 → 工序明细」里补选后点「保存工序」')"
      />
    </div>

    <!-- 站点设置：勾选显示/隐藏 + 拖动排序（按「登录人 + 所属公司」各自保存）；本页作用于工序环的悬停浮层 -->
    <StationSettingPanel
      v-model="stationSettingVisible"
      :fields="stationPanelFields"
      :page-key="stationPageKey"
      tip="拖动调整顺序；勾选控制在工序环的悬停浮层里显示哪些字段（值为空不占位）"
    />

    <!-- 列设置：勾选显示哪些列 + 拖动排序（与列表页同一套存储，按「登录人 + 所属公司」隔离） -->
    <ColumnSettingDrawer
      v-model="columnSettingVisible"
      :page-key="columnPageKey"
      :columns="columns"
      :title="$tt('列设置')"
    />

    <!-- 工序操作面板（点击站点打开） -->
    <OperationPanel v-model="panelVisible" :operation="currentOp" @changed="onChanged" />

    <!-- 【第 19 期】子工序圆环链弹窗（点父工序「总」节点打开；多层下钻、右上角 × 关闭） -->
    <OperationChildDialog
      v-model="childVisible"
      :parent-op="childParentOp"
      :all-ops="childAllOps"
      @open-panel="openPanel"
    />
  </div>
</template>

<script setup>
/**
 * 车间进度跟踪（独立页面）：一行 = 一张工单（+成品行），工序按工序号从左到右串联成圆环链。
 * 表格化改造（2026-10-07）：表头吸顶 + 列设置 + 筛选下拉 + 列头排序，行高与页面留白收紧。
 * 与「车间排程看板」拆成两页；两个页面共用 composables/useOperationBoard.js
 * （状态色 / 单据跳转 / 工序操作面板）、composables/useStationFields.js（站点字段）、
 * components/production/OperationProgressLines.vue（表格+圆环链渲染）、
 * components/production/StationSettingPanel.vue（站点设置，控制悬停浮层字段）。
 */
import { ref, computed, onMounted, nextTick } from 'vue'
import { ArrowLeft, Refresh, Setting, Filter, Close } from '@element-plus/icons-vue'
import { getOperationBoard } from '@/api/production/workOrderOperation'
import { useOperationPanel } from '@/composables/useOperationBoard'
import { useStationFields } from '@/composables/useStationFields'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { OP_STATUS_LIST } from '@/utils/operationStatus'
import { usePagePerm } from '@/utils/pagePerm'
import OperationPanel from '@/components/production/OperationPanel.vue'
import OperationProgressLines from '@/components/production/OperationProgressLines.vue'
import OperationChildDialog from '@/components/production/OperationChildDialog.vue'
import StationSettingPanel from '@/components/production/StationSettingPanel.vue'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'

const perm = usePagePerm()
const loading = ref(false)

/** 进度跟踪线路：[{ key, orderId, orderNo, materialCode, materialName, specification, workCenterId, workCenterName, ops }] */
const groups = ref([])
const includeFinished = ref(false)
/** 派工状态筛选：all(默认) / assigned / unassigned —— 当次查询条件，不持久化 */
const assigneeFilter = ref('all')

// ---------------- 列设置（全站统一一套：columnConfig store + ColumnSettingDrawer） ----------------
/** 列设置持久化 pageKey（与「站点设置」的 ProductionScheduleBoardStation 分开，互不覆盖） */
const columnPageKey = 'ProductionProgressTracking'
/**
 * 列定义：默认列 = 序号 / 工单编号 / 物料编码 / 物料名称 / 生产进度 / 状态 / 计划完工
 * （2026-10-09 回退：状态 / 规格 / 工作中心 / 负责人 恢复为独立列；
 *   负责人与各工序的时间信息改为在「生产进度」每个工序圆环下方逐道显示）。
 * 可选列（defaultHidden）= 规格 / 工作中心 / 负责人 / 工序数。
 * prop 与后端排序字段一一对应（orderNo/materialCode/materialName/status/planEndDate）。
 */
const columns = [
  { prop: 'index', label: '序号', width: 56, align: 'center', type: 'index' },
  { prop: 'orderNo', label: '工单编号', minWidth: 150, sortable: true, type: 'orderNo' },
  { prop: 'materialCode', label: '物料编码', minWidth: 120, sortable: true },
  { prop: 'materialName', label: '物料名称', minWidth: 160, sortable: true },
  { prop: 'progress', label: '生产进度', minWidth: 320, type: 'progress' },
  { prop: 'status', label: '状态', width: 96, align: 'center', sortable: true, type: 'status' },
  { prop: 'planEndDate', label: '计划完工', width: 110, align: 'center', sortable: true, type: 'planEndDate' },
  { prop: 'specification', label: '规格', minWidth: 120, defaultHidden: true },
  { prop: 'workCenterName', label: '工作中心', minWidth: 140, defaultHidden: true },
  { prop: 'assigneeName', label: '负责人', minWidth: 120, defaultHidden: true },
  { prop: 'opCount', label: '工序数', width: 80, align: 'center', defaultHidden: true }
]
const { columnSettingVisible, isColVisible, sortedColumns } = useColumnSetting(columnPageKey, columns)
/** 可见且按列设置顺序排好的列（表格渲染用） */
const visibleColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))

// ---------------- 列头排序（交后端 sortField/sortOrder） ----------------
const sortField = ref('')
const sortOrder = ref('asc')
function onSortChange ({ field, order }) {
  sortField.value = field
  sortOrder.value = order
  loadData()
}

// ---------------- 筛选下拉（选字段 + 填条件，可叠加多条） ----------------
const filterVisible = ref(false)
let condSeq = 1
function newCond () { return { id: condSeq++, field: 'orderNo', value: '', from: '', to: '' } }
const filterConds = ref([newCond()])
/** 关键字类字段（走后端 keyword，模糊匹配 工单号/物料编码/物料名称） */
const KEYWORD_FIELDS = ['orderNo', 'materialCode', 'materialName']

const activeFilterCount = computed(() => filterConds.value.filter(c => c.field === 'planEnd'
  ? !!(c.from || c.to)
  : !!String(c.value || '').trim()).length)

function addFilter () { filterConds.value.push(newCond()) }
function removeFilter (idx) {
  filterConds.value.splice(idx, 1)
  if (!filterConds.value.length) filterConds.value.push(newCond())
  onFilterChange()
}
function clearFilters () {
  filterConds.value = [newCond()]
  onFilterChange()
}
/** 切换字段时清掉旧值，避免串条件 */
function onFilterFieldChange (c) {
  c.value = ''
  c.from = ''
  c.to = ''
  onFilterChange()
}
function onFilterChange () { loadData() }

/** 已填关键字条件（用于前端按「所选字段」精确匹配线路，支持多条叠加） */
function keywordConds () {
  return filterConds.value
    .filter(c => KEYWORD_FIELDS.includes(c.field) && String(c.value || '').trim())
    .map(c => ({ field: c.field, value: String(c.value).trim().toLowerCase() }))
}

/** 组装 board 入参：老条件（派工/含已完工）与筛选条叠加，互不覆盖 */
function buildParams () {
  const kw = keywordConds()
  const statusCond = filterConds.value.find(c => c.field === 'status' && c.value)
  const dateCond = filterConds.value.find(c => c.field === 'planEnd')
  const params = {
    view: 'order',
    includeFinished: includeFinished.value,
    assigneeFilter: assigneeFilter.value || 'all'
  }
  // 后端 keyword 为单个模糊串：取第一条关键字条件；其余条件在前端按所选字段精确叠加
  if (kw.length) params.keyword = kw[0].value
  if (statusCond) params.status = statusCond.value
  if (dateCond) {
    if (dateCond.from) params.planEndFrom = dateCond.from
    if (dateCond.to) params.planEndTo = dateCond.to
  }
  if (sortField.value) {
    params.sortField = sortField.value
    params.sortOrder = sortOrder.value
  }
  return params
}

/** 前端按「所选字段」叠加关键字条件（后端只有一个 keyword 入口，多条时在此补足） */
function applyKeywordFilter (list) {
  const kw = keywordConds()
  if (!kw.length) return list
  return list.filter(g => kw.every(k => {
    const v = g && g[k.field] != null ? String(g[k.field]).toLowerCase() : ''
    return v.includes(k.value)
  }))
}

const stationSettingVisible = ref(false)
const {
  pageKey: stationPageKey,
  fieldDefs: stationFieldDefs,
  stationFieldsOf,
  init: initStationFields,
  preloadValues
} = useStationFields({ allVisibleByDefault: true })

/**
 * 「站点设置」面板候选字段：本页圆环上不再挂字段，站点设置改为控制**悬停浮层**显示哪些字段；
 * 默认全勾选（= 悬停浮层显示全部非空字段，与改造前一致，不丢信息）。
 */
const stationPanelFields = computed(() => stationFieldDefs.value.map(f => ({ ...f, defaultHidden: false })))

const { panelVisible, currentOp, openPanel, onChanged } = useOperationPanel(() => loadData())

// 【第 19 期】子工序弹窗：点父工序「总」节点打开，展示该父工序的子工序圆环链（可下钻到更下一层）
const childVisible = ref(false)
const childParentOp = ref(null)
const childAllOps = ref([])
function opIdOf (op) { return op ? (op.operationRowId != null ? op.operationRowId : op.id) : null }
function openParent (op) {
  const pid = opIdOf(op)
  const g = groups.value.find(x => (x.ops || []).some(o => opIdOf(o) != null && Number(opIdOf(o)) === Number(pid)))
  childAllOps.value = (g && g.ops) || []
  childParentOp.value = op
  childVisible.value = true
}

const totalOps = computed(() => groups.value.reduce((n, g) => n + ((g.ops && g.ops.length) || 0), 0))

function allOps () {
  return groups.value.flatMap(g => (g.ops || []))
}

async function loadData () {
  loading.value = true
  try {
    const list = await getOperationBoard(buildParams())
    groups.value = applyKeywordFilter(Array.isArray(list) ? list : [])
    preloadValues(allOps())
  } catch (e) {
    groups.value = []
  } finally {
    loading.value = false
    await nextTick()
  }
}

onMounted(async () => {
  await initStationFields()
  await loadData()
})
</script>

<style scoped lang="scss">
@import '@/styles/productionBoard.scss';

/* 行高与留白收紧：页壳左右 24px → 12px，页头/工具条压扁 */
.board-page { padding: 10px 12px 20px; }
.page-header { margin-bottom: 8px; }

/* flex:1 + min-height:0：把页头之后剩余的高度全部吃掉交给表格区，避免表格下方留一大块空白 div */
.board-body { flex: 1; min-height: 0; display: flex; flex-direction: column; gap: 8px; }

/* ---------------- 筛选下拉面板 ---------------- */
.filter-btn {
  .filter-count {
    display: inline-block;
    min-width: 16px;
    height: 16px;
    line-height: 16px;
    margin-left: 4px;
    padding: 0 4px;
    border-radius: 8px;
    background: #4f46e5;
    color: #fff;
    font-size: 10px;
    text-align: center;
  }
  .filter-caret { margin-left: 3px; font-size: 10px; }
  &.is-active { border-color: #4f46e5; color: #4f46e5; }
}

.flt-panel { font-size: 11px; }
.flt-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 6px;
  .flt-title { font-size: 12px; font-weight: 600; color: #1f2937; }
  .flt-hint { font-size: 11px; color: #9ca3af; }
}
.flt-rows { max-height: 300px; overflow-y: auto; }
.flt-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  /* 让两个日期选择器在行内等分（Element Plus 的日期编辑器默认 220px，靠 CSS 变量放开） */
  --el-date-editor-width: 100%;
  .flt-field { width: 96px; flex: 0 0 96px; }
  .flt-val { flex: 1 1 auto; min-width: 0; }
  .flt-date { flex: 1 1 0; min-width: 0; }
  .flt-tilde { color: #9ca3af; }
  .flt-del {
    flex: 0 0 auto;
    color: #b6bfd4;
    cursor: pointer;
    &:hover { color: #ef4444; }
  }
}
.flt-foot {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  margin-top: 4px;
  padding-top: 8px;
  border-top: 1px solid #f0f0f0;
}

@media (max-width: 768px) {
  .board-page { padding: 10px 12px 20px; }
  .hud { flex-direction: column; align-items: stretch; }
}
</style>
