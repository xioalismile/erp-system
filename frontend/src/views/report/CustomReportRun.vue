<template>
  <div class="custom-report-run">
    <!-- 顶部标题栏 -->
    <div class="run-header">
      <div class="header-left">
        <el-button size="small" @click="goBack"><el-icon><Back /></el-icon> {{ $tt("返回") }}</el-button>
        <span class="title-text">{{ reportName }}</span>
        <el-tag v-if="dataSourceName" size="small" type="info">{{ dataSourceName }}</el-tag>
      </div>
      <div class="header-right">
        <el-button v-if="canEdit" size="small" type="primary" plain @click="goEdit">
          <el-icon><Edit /></el-icon> 编辑报表
        </el-button>
        <el-button size="small" type="success" :loading="exporting" :disabled="!tableData.length" @click="handleExport">
          <el-icon><Download /></el-icon> 导出Excel
        </el-button>
      </div>
    </div>

    <!-- 筛选区 -->
    <div class="filter-bar" v-if="hasAnyFilter">
      <el-date-picker
        v-if="hasDateFilter"
        v-model="filterDateRange"
        type="daterange"
        :range-separator="$tt('至')"
        :start-placeholder="$tt('开始日期')"
        :end-placeholder="$tt('结束日期')"
        value-format="YYYY-MM-DD"
        style="width: 260px"
        @change="loadData"
      />
      <el-input
        v-if="hasKeywordFilter"
        v-model="filterKeyword"
        :placeholder="$tt('关键字')"
        clearable
        style="width: 220px"
        @keyup.enter="loadData"
        @clear="loadData"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-input
        v-if="hasStatusFilter"
        v-model="filterStatus"
        :placeholder="$tt('状态')"
        clearable
        style="width: 160px"
        @keyup.enter="loadData"
        @clear="loadData"
      />
      <el-button size="small" type="primary" @click="loadData">{{ $tt("查询") }}</el-button>
      <el-button size="small" @click="resetFilter">{{ $tt("重置") }}</el-button>
    </div>

    <!-- 高级筛选：任意字段（含自定义字段）+ 任意比较方式；条件不落报表配置，仅本次查询生效 -->
    <div class="adv-filter">
      <div class="adv-head" @click="advOpen = !advOpen">
        <el-icon><ArrowDown v-if="advOpen" /><ArrowRight v-else /></el-icon>
        <span class="adv-title">{{ $tt("高级筛选") }}</span>
        <span v-if="activeConditionCount" class="adv-badge">{{ $tt("已启用") }} {{ activeConditionCount }} {{ $tt("个条件") }}</span>
        <span class="adv-hint">{{ $tt("任意字段都可作为过滤条件") }}</span>
      </div>
      <div v-show="advOpen" class="adv-body">
        <div v-for="(c, idx) in conditions" :key="idx" class="adv-row">
          <!-- 与上一条的连接符：且/或（连续「或」自动归入同一括号组，组间为「且」） -->
          <el-select v-if="idx > 0" v-model="c.logic" style="width: 82px">
            <el-option :label="$tt('并且')" value="AND" />
            <el-option :label="$tt('或者')" value="OR" />
          </el-select>
          <span v-else class="adv-logic-ph">{{ $tt("条件") }}</span>
          <el-select
            v-model="c.fieldRef"
            filterable
            clearable
            :placeholder="$tt('选择字段')"
            style="width: 260px"
            @change="onFieldChange(c)"
          >
            <el-option-group v-for="g in fieldGroups" :key="g.key" :label="g.name">
              <el-option
                v-for="f in g.fields"
                :key="f.field"
                :label="f.custom ? $tf(f.label) + '（自定义）' : $tf(f.label)"
                :value="`${g.key}.${f.field}`"
              />
            </el-option-group>
          </el-select>
          <el-select v-model="c.op" style="width: 120px" @change="onOpChange(c)">
            <el-option v-for="o in opOptions(c)" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <template v-if="c.op !== 'isNull' && c.op !== 'isNotNull'">
            <!-- in / notIn：多值（select 字段带候选选项，其余可手输回车） -->
            <el-select
              v-if="isInOp(c)"
              v-model="c.values"
              multiple
              filterable
              allow-create
              default-first-option
              :placeholder="$tt('回车逐个输入值（可多选）')"
              style="width: 300px"
            >
              <el-option v-for="o in optionsOf(c)" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
            <el-date-picker
              v-else-if="typeOf(c) === 'date'"
              v-model="c.value"
              type="date"
              value-format="YYYY-MM-DD"
              :placeholder="$tt('选择日期')"
              style="width: 170px"
            />
            <el-date-picker
              v-else-if="typeOf(c) === 'datetime'"
              v-model="c.value"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              :placeholder="$tt('选择日期时间')"
              style="width: 200px"
            />
            <el-input-number
              v-else-if="isNumType(typeOf(c))"
              v-model="c.value"
              :controls="false"
              :placeholder="$tt('数值')"
              style="width: 150px"
            />
            <el-select
              v-else-if="optionsOf(c).length"
              v-model="c.value"
              clearable
              filterable
              :placeholder="$tt('选择值')"
              style="width: 180px"
            >
              <el-option v-for="o in optionsOf(c)" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
            <el-input
              v-else
              v-model="c.value"
              clearable
              :placeholder="$tt('值')"
              style="width: 180px"
            />
            <!-- 区间：第二个值 -->
            <template v-if="c.op === 'between'">
              <span class="adv-eq">{{ $tt("至") }}</span>
              <el-date-picker
                v-if="typeOf(c) === 'date'"
                v-model="c.value2"
                type="date"
                value-format="YYYY-MM-DD"
                :placeholder="$tt('结束日期')"
                style="width: 170px"
              />
              <el-date-picker
                v-else-if="typeOf(c) === 'datetime'"
                v-model="c.value2"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                :placeholder="$tt('结束日期时间')"
                style="width: 200px"
              />
              <el-input-number
                v-else-if="isNumType(typeOf(c))"
                v-model="c.value2"
                :controls="false"
                :placeholder="$tt('结束值')"
                style="width: 150px"
              />
              <el-input v-else v-model="c.value2" clearable :placeholder="$tt('结束值')" style="width: 180px" />
            </template>
          </template>
          <span v-else class="adv-no-value">{{ $tt("无需填值") }}</span>
          <el-button link type="danger" @click="removeCondition(idx)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
        <div class="adv-actions">
          <el-button size="small" plain @click="addCondition">
            <el-icon><Plus /></el-icon> {{ $tt("添加条件") }}
          </el-button>
          <el-button size="small" type="primary" @click="loadData">{{ $tt("查询") }}</el-button>
          <el-button size="small" :disabled="!conditions.length" @click="clearConditions">{{ $tt("清空条件") }}</el-button>
          <span class="adv-tip">{{ $tt("多个条件之间为「并且」；未填值的条件不参与查询") }}</span>
          <span v-if="!hasCustomFields" class="adv-tip">{{ $tt("该数据源暂无自定义字段（可在 设置中心 → 自定义字段 添加）") }}</span>
        </div>
      </div>
    </div>

    <!-- 汇总+图表：图形/数据 双页签；其余模式：单表格 -->
    <el-tabs v-if="showTabs" v-model="activeTab" class="report-tabs" @tab-change="onTabChange">
      <el-tab-pane :label="$tf('图形')" name="chart">
        <div class="chart-panel">
          <div class="chart-box" ref="chartRef"></div>
        </div>
      </el-tab-pane>
      <el-tab-pane :label="$tf('数据')" name="data">
        <el-table
          :data="tableData"
          border
          size="small"
          v-loading="loading"
          :max-height="tableMaxHeight"
          :show-summary="!isSummary || !!totalsRow"
          :summary-method="summaryMethod"
          :row-style="drillTargets.length ? { cursor: 'pointer' } : undefined"
          @row-click="onRowClick"
        >
          <el-table-column type="index" label="#" width="50" fixed="left" align="center" />
          <el-table-column
            v-for="col in columns"
            :key="col.field"
            :prop="col.field"
            :label="$tf(col.label)"
            :min-width="colWidth(col)"
            :align="isNumeric(col) || col.type === 'compare' ? 'right' : 'left'"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <span v-if="col.type === 'compare'" :class="compareClass(row[col.field])">{{ formatCompare(row[col.field]) }}</span>
              <span v-else-if="col.type === 'amount'" class="cell-amount">{{ formatAmount(row[col.field]) }}</span>
              <span v-else-if="isNumeric(col)" class="cell-number">{{ formatNumber(row[col.field]) }}</span>
              <el-tag v-else-if="isStatusField(col)" :type="getStatusTagType(row[col.field])" size="small">
                {{ row[col.field] ?? '' }}
              </el-tag>
              <span v-else>{{ row[col.field] ?? '' }}</span>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          class="pagination"
          v-model:current-page="page.current"
          v-model:page-size="page.size"
          :page-sizes="[20, 50, 100, 200]"
          :total="page.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </el-tab-pane>
    </el-tabs>

    <!-- 无图表模式：单表格（明细 / 汇总但未配置图表） -->
    <template v-else>
      <el-table
        :data="tableData"
        border
        size="small"
        v-loading="loading"
        :max-height="tableMaxHeight"
        :show-summary="!isSummary || !!totalsRow"
        :summary-method="summaryMethod"
        :row-style="drillTargets.length ? { cursor: 'pointer' } : undefined"
        @row-click="onRowClick"
      >
        <el-table-column type="index" label="#" width="50" fixed="left" align="center" />
        <el-table-column
          v-for="col in columns"
          :key="col.field"
          :prop="col.field"
          :label="$tf(col.label)"
          :min-width="colWidth(col)"
          :align="isNumeric(col) || col.type === 'compare' ? 'right' : 'left'"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <span v-if="col.type === 'compare'" :class="compareClass(row[col.field])">{{ formatCompare(row[col.field]) }}</span>
            <span v-else-if="col.type === 'amount'" class="cell-amount">{{ formatAmount(row[col.field]) }}</span>
            <span v-else-if="isNumeric(col)" class="cell-number">{{ formatNumber(row[col.field]) }}</span>
            <el-tag v-else-if="isStatusField(col)" :type="getStatusTagType(row[col.field])" size="small">
              {{ row[col.field] ?? '' }}
            </el-tag>
            <span v-else>{{ row[col.field] ?? '' }}</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        class="pagination"
        v-model:current-page="page.current"
        v-model:page-size="page.size"
        :page-sizes="[20, 50, 100, 200]"
        :total="page.total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadData"
        @current-change="loadData"
      />
    </template>

    <!-- 行下钻：本报表配置了多个下钻目标时，先让用户选用哪一个 -->
    <el-dialog v-model="drillVisible" :title="$tf('查看明细')" width="380px" append-to-body>
      <div class="drill-buttons">
        <el-button
          v-for="(d, i) in drillTargets"
          :key="i"
          type="primary"
          plain
          style="margin: 0 8px 8px 0"
          @click="goDrill(d)">
          {{ d.label }}
        </el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import echarts from '@/utils/echartsCore'
import { Back, Edit, Download, Search, Delete, Plus, ArrowDown, ArrowRight } from '@element-plus/icons-vue'
import { getCustomReport, runCustomReport, exportCustomReport, describeDataSource } from '@/api/report/customReport'
import { tt } from '@/i18n'

const router = useRouter()
const route = useRoute()
const reportId = computed(() => Number(route.params.id))

// 报表信息
const reportName = ref('')
const dataSourceName = ref('')
const reportDataSource = ref('')
const canEdit = ref(false)

// 配置（判断筛选可用性 + 状态列渲染）
const config = ref(null)
const defaultDateField = ref(null) // 后端推荐的主表日期字段（报表未配置 dateField 时兜底）
const hasDateFilter = computed(() => !!(config.value?.dateField || defaultDateField.value))
const hasKeywordFilter = computed(() => !!config.value?.keywordField)
const hasStatusFilter = computed(() => !!config.value?.statusField)
const hasAnyFilter = computed(() => hasDateFilter.value || hasKeywordFilter.value || hasStatusFilter.value)

// 筛选
const filterDateRange = ref([])
const filterKeyword = ref('')
const filterStatus = ref('')

// ========== 高级筛选：任意字段条件（含自定义字段），不落报表配置 ==========
const advOpen = ref(false)
// 字段树：[{key:'main'|joinKey, name, fields:[{field,label,type,custom,options}]}]
const fieldGroups = ref([])
// 字段元数据索引："source.field" -> {label,type,custom,options}
const fieldMetaMap = reactive({})
// 条件行：[{fieldRef:'source.field', op, value, value2, values}]
const conditions = ref([])
// 该数据源是否存在自定义字段（无则给空态引导文案）
const hasCustomFields = ref(false)

const OP_LABELS = {
  eq: '等于', ne: '不等于', gt: '大于', ge: '大于等于', lt: '小于', le: '小于等于',
  like: '包含', notLike: '不包含', between: '区间', in: '属于', notIn: '不属于',
  isNull: '为空', isNotNull: '不为空'
}

// 已填值、会真正参与查询的条件数（用于标题角标）
const activeConditionCount = computed(() => serializeConditions().length)

async function loadFields() {
  hasCustomFields.value = false
  const ds = reportDataSource.value
  if (!ds) {
    fieldGroups.value = []
    return
  }
  try {
    const meta = await describeDataSource(ds)
    const groups = [{ key: 'main', name: (meta.name || '主表') + '·主表', fields: meta.fields || [] }]
    for (const j of meta.joins || []) {
      groups.push({ key: j.key, name: j.name, fields: j.fields || [] })
    }
    fieldGroups.value = groups
    Object.keys(fieldMetaMap).forEach(k => delete fieldMetaMap[k])
    for (const g of groups) {
      for (const f of g.fields || []) {
        fieldMetaMap[`${g.key}.${f.field}`] = f
        if (f.custom === true) hasCustomFields.value = true
      }
    }
  } catch (e) {
    fieldGroups.value = []
  }
}

function typeOf(c) {
  return fieldMetaMap[c.fieldRef]?.type || 'string'
}

function optionsOf(c) {
  return fieldMetaMap[c.fieldRef]?.options || []
}

function isNumType(t) {
  return t === 'number' || t === 'amount' || t === 'int'
}

function isInOp(c) {
  return c.op === 'in' || c.op === 'notIn'
}

// 操作符按字段类型动态给出：数值/日期可比较大小，文本支持包含，下拉/开关给属于/不属于
function opOptions(c) {
  const t = typeOf(c)
  let ops
  if (isNumType(t) || t === 'date' || t === 'datetime') {
    ops = ['eq', 'ne', 'gt', 'ge', 'lt', 'le', 'between']
  } else if (t === 'select' || t === 'bool') {
    ops = ['eq', 'ne', 'in', 'notIn']
  } else {
    ops = ['eq', 'ne', 'like', 'notLike', 'in', 'notIn']
  }
  ops = ops.concat(['isNull', 'isNotNull'])
  return ops.map(v => ({ value: v, label: OP_LABELS[v] || v }))
}

function onFieldChange(c) {
  c.op = opOptions(c)[0]?.value || 'eq'
  resetValues(c)
}

function onOpChange(c) {
  resetValues(c)
}

function resetValues(c) {
  c.value = undefined
  c.value2 = undefined
  c.values = []
}

function addCondition() {
  advOpen.value = true
  if (conditions.value.length >= 20) {
    ElMessage.warning(tt('最多 20 个条件'))
    return
  }
  conditions.value.push({ fieldRef: '', op: 'eq', value: undefined, value2: undefined, values: [], logic: 'AND' })
}

function removeCondition(idx) {
  conditions.value.splice(idx, 1)
}

function clearConditions() {
  conditions.value = []
}

/**
 * 条件行 → 请求体条件数组；未选字段 / 未填值的行直接丢弃（与后端"空值条件不生效"口径一致）
 */
function serializeConditions() {
  const out = []
  for (const c of conditions.value) {
    if (!c.fieldRef) continue
    const dot = c.fieldRef.indexOf('.')
    if (dot <= 0) continue
    const item = {
      source: c.fieldRef.slice(0, dot),
      field: c.fieldRef.slice(dot + 1),
      op: c.op || 'eq'
    }
    if (isInOp(c)) {
      const vals = (c.values || []).filter(v => v !== null && v !== undefined && String(v).trim() !== '')
      if (!vals.length) continue
      item.values = vals.map(v => String(v))
    } else if (item.op === 'isNull' || item.op === 'isNotNull') {
      // 无需值
    } else if (item.op === 'between') {
      if (isEmptyVal(c.value) || isEmptyVal(c.value2)) continue
      item.value = String(c.value)
      item.value2 = String(c.value2)
    } else {
      if (isEmptyVal(c.value)) continue
      item.value = String(c.value)
    }
    // 连接符：默认 AND 可省略；OR 表示"与上一条是或关系"
    if (c.logic === 'OR') item.logic = 'OR'
    out.push(item)
  }
  return out
}

function isEmptyVal(v) {
  return v === null || v === undefined || String(v).trim() === ''
}

// 数据
const loading = ref(false)
const exporting = ref(false)
const tableData = ref([])
const columns = ref([])
const page = reactive({ current: 1, size: 20, total: 0 })
const tableMaxHeight = ref(window.innerHeight - 300)

// 汇总模式（批次2）：mode/chart/totals 由后端 run 返回
const mode = ref('detail')

// ===== 行下钻：按报表配置的目标单据跳转，并带上本行对应列的值 =====
const drillVisible = ref(false)
const drillRow = ref(null)
const drillTargets = computed(() => (Array.isArray(config.value?.drill) ? config.value.drill : []))

function onRowClick(row) {
  if (!drillTargets.value.length) return
  drillRow.value = row
  if (drillTargets.value.length === 1) {
    goDrill(drillTargets.value[0])
    return
  }
  drillVisible.value = true
}

function goDrill(d) {
  if (!d || !d.route) return
  const row = drillRow.value || {}
  const val = row[d.keyField]
  const query = { ...(d.extraParams || {}) }
  if (val !== undefined && val !== null && val !== '') query[d.param] = String(val)
  drillVisible.value = false
  router.push({ path: d.route, query })
}
const chartType = ref('')
const totalsRow = ref(null)
const isSummary = computed(() => mode.value === 'summary')
const groupCount = computed(() => config.value?.groupBy?.length || 0)

// 双页签：仅汇总+配置了图表类型时才用 tabs（图形/数据），其余单表格
const showTabs = computed(() => isSummary.value && !!chartType.value)
const activeTab = ref('chart')

// 切到图形页签时 echarts 需 resize：tab 隐藏时容器宽高为 0，
// 不 resize 会出现图表挤压/空白（echarts+el-tabs 经典坑）
function onTabChange(name) {
  if (name === 'chart') {
    nextTick(() => {
      if (!chartRef.value) return
      if (!chartInstance) {
        chartInstance = echarts.init(chartRef.value)
        renderChart()
      } else {
        chartInstance.resize()
      }
    })
  }
}

// 图表
const chartRef = ref(null)
let chartInstance = null
let chartRows = []

async function loadReport() {
  try {
    const detail = await getCustomReport(reportId.value)
    reportName.value = detail.reportName || ''
    dataSourceName.value = detail.dataSourceName || ''
    reportDataSource.value = detail.dataSource || ''
    try {
      config.value = detail.configJson ? JSON.parse(detail.configJson) : null
    } catch (_) {
      config.value = null
    }
    // 默认筛选条件预填（报表配置里的 filters）：预填成可编辑行，改/清都生效。
    // 后端口径一致：用户没自带条件时用配置的默认值，自带条件则以用户的为准。
    if (Array.isArray(config.value?.filters) && config.value.filters.length && conditions.value.length === 0) {
      conditions.value = config.value.filters.slice(0, 20).map(f => ({
        fieldRef: (f && f.source && f.field) ? `${f.source}.${f.field}` : '',
        op: (f && f.op) ? f.op : 'eq',
        value: f ? f.value : undefined,
        value2: f ? f.value2 : undefined,
        values: (f && Array.isArray(f.values)) ? f.values.slice() : [],
        logic: (f && String(f.logic).toUpperCase() === 'OR') ? 'OR' : 'AND'
      }))
      if (conditions.value.length) advOpen.value = true
    }
    // 创建人可编辑（ADMIN/MANAGER 由后端判断，前端仅按钮展示，后端二次校验）
    const username = localStorage.getItem('username')
    canEdit.value = username === detail.createdBy
    // 报表未配置 dateField 时，用后端推荐的主表日期字段兜底（仅运行时筛选，不改报表配置）
    if (!config.value?.dateField && detail.defaultDateField) {
      defaultDateField.value = detail.defaultDateField
    }
  } catch (e) {
    if (e?.message) ElMessage.error(e.message)
  }
}

async function loadData() {
  loading.value = true
  try {
    const body = { page: page.current, size: page.size }
    if (hasDateFilter.value && filterDateRange.value?.length === 2) {
      body.startDate = filterDateRange.value[0]
      body.endDate = filterDateRange.value[1]
      // 报表未配置 dateField 时传兜底字段（后端仅本次请求生效）
      if (defaultDateField.value && !config.value?.dateField) {
        body.dateSource = defaultDateField.value.source
        body.dateField = defaultDateField.value.field
      }
    }
    if (hasKeywordFilter.value && filterKeyword.value) body.keyword = filterKeyword.value
    if (hasStatusFilter.value && filterStatus.value) body.status = filterStatus.value
    // 高级筛选：任意字段条件（含自定义字段）。**始终显式带上（含空数组）**：
    // 空数组 = 用户"清空条件"，后端据此不再回落到报表配置的默认筛选（否则默认条件清不掉）
    body.conditions = serializeConditions()
    const res = await runCustomReport(reportId.value, body)
    tableData.value = res?.records || []
    columns.value = res?.columns || []
    page.total = res?.total || 0
    // 汇总模式：模式/图表/整体合计行（后端整体重算，非当前页求和）
    mode.value = res?.mode || 'detail'
    chartType.value = res?.chart?.type || ''
    totalsRow.value = res?.totals || null
    if (isSummary.value && chartType.value) {
      await loadChartData(body)
    }
  } catch (e) {
    tableData.value = []
    page.total = 0
    if (e?.message) ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

// 图表数据：取前 500 条分组结果（后端 MAX_PAGE_SIZE），保证跨页时图表完整
async function loadChartData(filterBody) {
  try {
    const res = await runCustomReport(reportId.value, { ...filterBody, page: 1, size: 500 })
    chartRows = res?.records || []
  } catch (_) {
    chartRows = []
  }
  await nextTick()
  renderChart()
}

function resetFilter() {
  filterDateRange.value = []
  filterKeyword.value = ''
  filterStatus.value = ''
  clearConditions()
  page.current = 1
  loadData()
}

async function handleExport() {
  exporting.value = true
  try {
    const params = {}
    if (hasDateFilter.value && filterDateRange.value?.length === 2) {
      params.startDate = filterDateRange.value[0]
      params.endDate = filterDateRange.value[1]
      // 与 loadData 同口径：报表未配置 dateField 时传兜底字段
      if (defaultDateField.value && !config.value?.dateField) {
        params.dateSource = defaultDateField.value.source
        params.dateField = defaultDateField.value.field
      }
    }
    if (hasKeywordFilter.value && filterKeyword.value) params.keyword = filterKeyword.value
    if (hasStatusFilter.value && filterStatus.value) params.status = filterStatus.value
    // 高级筛选条件随导出一起带出（GET 参数，与服务端同一套白名单校验；空数组=明确无条件）
    params.conditions = JSON.stringify(serializeConditions())
    const blob = await exportCustomReport(reportId.value, params)
    const url = window.URL.createObjectURL(new Blob([blob]))
    const a = document.createElement('a')
    a.href = url
    a.download = `${reportName.value || '自定义报表'}_${new Date().toISOString().slice(0, 10)}.xlsx`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
    ElMessage.success(tt('导出成功'))
  } catch (e) {
    if (e?.message) ElMessage.error(e.message)
  } finally {
    exporting.value = false
  }
}

function isNumeric(col) {
  return col.type === 'amount' || col.type === 'number' || col.type === 'int'
}

// 环比列（较上期%）：正=涨（红）、负=跌（绿）、空/首行=空白
function formatCompare(v) {
  if (v === null || v === undefined || v === '') return ''
  const n = Number(v)
  if (isNaN(n)) return v
  return (n > 0 ? '+' : '') + n.toFixed(2) + '% ' + (n > 0 ? '↑' : n < 0 ? '↓' : '')
}

function compareClass(v) {
  const n = Number(v)
  if (v === null || v === undefined || v === '' || isNaN(n) || n === 0) return 'cell-compare-flat'
  return n > 0 ? 'cell-compare-up' : 'cell-compare-down'
}

function isStatusField(col) {
  return (col.label || '').includes('状态') && !isNumeric(col)
}

function colWidth(col) {
  if (col.type === 'compare') return 120
  if (col.type === 'amount') return 130
  if (col.type === 'number' || col.type === 'int') return 110
  if (col.type === 'date') return 110
  if (col.type === 'datetime') return 160
  return 120
}

// 合计行：汇总模式用后端整体重算值（totalsSql），明细模式保留当前页求和
function summaryMethod({ columns: cols, data }) {
  const sums = []
  if (isSummary.value) {
    cols.forEach((col, idx) => {
      if (idx === 0) { sums[idx] = '合计'; return }
      const colDef = columns.value.find(c => c.field === col.property)
      const v = totalsRow.value ? totalsRow.value[col.property] : null
      if (colDef && isNumeric(colDef) && v !== null && v !== undefined) {
        sums[idx] = colDef.type === 'amount' ? `¥${formatNumber(v)}` : formatNumber(v)
      } else {
        sums[idx] = ''
      }
    })
    return sums
  }
  cols.forEach((col, idx) => {
    if (idx === 0) { sums[idx] = '合计'; return }
    const colDef = columns.value.find(c => c.field === col.property)
    if (colDef && isNumeric(colDef)) {
      const total = data.reduce((s, row) => {
        const v = Number(row[col.property])
        return isNaN(v) ? s : s + v
      }, 0)
      sums[idx] = colDef.type === 'amount' ? `¥${formatNumber(total)}` : formatNumber(total)
    } else {
      sums[idx] = ''
    }
  })
  return sums
}

// ==================== 图表渲染（汇总模式，批次2） ====================
// 列顺序契约：分组列在前、聚合列在后（与后端 parseSummarySpec/queryTotalsRow 一致）
function groupLabel(row) {
  const gCols = columns.value.slice(0, groupCount.value)
  return gCols.map(g => row[g.field] ?? '').join(' / ') || '(空)'
}

function renderChart() {
  if (!chartRef.value || !chartType.value) return
  if (!chartInstance) chartInstance = echarts.init(chartRef.value)
  const gCount = groupCount.value
  const aggCols = columns.value.slice(gCount).filter(c => isNumeric(c))
  if (!aggCols.length) { chartInstance.clear(); return }

  if (chartType.value === 'pie') {
    // 饼图：首分组列为维度，第一个聚合指标为数值
    const agg = aggCols[0]
    chartInstance.setOption({
      title: { text: `${agg.label}占比`, left: 'center', textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'item', formatter: '{b}<br/>{c} ({d}%)' },
      legend: { type: 'scroll', orient: 'vertical', left: 'left', top: 'middle' },
      series: [{
        type: 'pie',
        radius: ['35%', '62%'],
        center: ['55%', '55%'],
        data: chartRows.map(r => ({ name: groupLabel(r), value: Number(r[agg.field]) || 0 }))
      }]
    }, true)
    return
  }

  // 柱状图 / 折线图：首分组列为 X 轴，全部聚合指标为数值系列
  chartInstance.setOption({
    title: { text: reportName.value, left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis' },
    legend: { top: 30, data: aggCols.map(c => c.label) },
    grid: { left: 60, right: 30, top: 70, bottom: 60 },
    xAxis: {
      type: 'category',
      data: chartRows.map(r => groupLabel(r)),
      axisLabel: { rotate: 30, interval: 0, hideOverlap: true }
    },
    yAxis: { type: 'value' },
    series: aggCols.map(c => ({
      name: c.label,
      type: chartType.value === 'line' ? 'line' : 'bar',
      smooth: chartType.value === 'line',
      data: chartRows.map(r => Number(r[c.field]) || 0)
    }))
  }, true)
}

function formatNumber(v) {
  if (v === null || v === undefined || v === '') return ''
  const n = Number(v)
  if (isNaN(n)) return v
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 金额列：空值显示纯空白（不显示孤零零的¥占位符），有值才带¥
function formatAmount(v) {
  if (v === null || v === undefined || v === '') return ''
  return '¥' + formatNumber(v)
}

function getStatusTagType(status) {
  if (!status) return 'info'
  if (status.includes('已生效') || status.includes('已完成') || status.includes('已结清') || status.includes('生效')) return 'success'
  if (status.includes('草稿') || status.includes('待')) return 'warning'
  if (status.includes('作废') || status.includes('关闭') || status.includes('终止')) return 'danger'
  return 'info'
}

function goBack() {
  router.push('/report/custom')
}

function goEdit() {
  router.push({ path: '/report/custom/designer', query: { id: reportId.value } })
}

function handleResize() {
  tableMaxHeight.value = window.innerHeight - 300
  chartInstance?.resize()
}

onMounted(async () => {
  window.addEventListener('resize', handleResize)
  await loadReport()
  // 高级筛选字段树（含自定义字段）来自数据源元数据，与报表数据并行加载
  loadFields()
  await loadData()
})
onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
})
</script>

<style scoped lang="scss">
.custom-report-run {
  display: flex;
  flex-direction: column;
  background: #fff;
  padding: 0 0 16px;
  min-height: calc(100vh - 90px);
}
.run-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  border-bottom: 1px solid #e4e7ed;
  .header-left {
    display: flex;
    align-items: center;
    gap: 10px;
    .title-text { font-size: 16px; font-weight: 600; color: #303133; }
  }
  .header-right { display: flex; gap: 8px; }
}
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 16px 0;
  padding: 10px 12px;
  background: #fafafa;
  border-radius: 4px;
}
/* 高级筛选：任意字段条件区 */
.adv-filter {
  margin: 8px 16px 0;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #fff;
}
.adv-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  user-select: none;
  font-size: 13px;
  color: #303133;
}
.adv-title { font-weight: 600; }
.adv-badge {
  padding: 0 6px;
  height: 18px;
  line-height: 18px;
  border-radius: 9px;
  background: #ecf5ff;
  color: #409eff;
  font-size: 12px;
}
.adv-hint { color: #909399; font-size: 12px; }
.adv-body {
  padding: 4px 12px 12px;
  border-top: 1px dashed #ebeef5;
}
.adv-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}
.adv-eq { color: #909399; font-size: 12px; }
.adv-no-value { color: #909399; font-size: 12px; }
/* 首行占位：与后续行的「且/或」下拉对齐 */
.adv-logic-ph {
  display: inline-block;
  width: 82px;
  text-align: center;
  font-size: 12px;
  color: #c0c4cc;
}
.adv-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}
.adv-tip { color: #909399; font-size: 12px; }
.chart-panel {
  margin: 12px 16px 0;
  padding: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #fff;
  .chart-box { width: 100%; height: 320px; }
}
.el-table {
  margin: 12px 16px 0;
}
.pagination {
  margin-top: 12px;
  padding: 0 16px;
  text-align: right;
}
.cell-amount { color: #f56c6c; font-weight: 500; }
.cell-number { color: #409eff; font-weight: 500; }
.cell-compare-up { color: #f56c6c; font-weight: 600; }
.cell-compare-down { color: #67c23a; font-weight: 600; }
.cell-compare-flat { color: #909399; }
</style>
