<template>
  <!-- 列表页通用搜索栏（2026-09-16 列表页查询口径通用化 P0）
       4 个标准筛选维度：日期区间 / 客户 / 供应商 / 员工（销售员、采购员…）
       —— 只渲染页面声明的维度（未声明的不渲染），从机制上杜绝「渲染了却点了没反应」
       仅透传/回传值，不改父组件数据：变更通过 change 事件回传（与 CustomFieldSearchBar 同约定） -->
  <div v-for="f in filters" :key="f" class="filter-item">
    <!-- 每个筛选控件都带标签：用户一眼看出这个框是搜什么的（用户 2026-09-16 口径） -->
    <span class="filter-label">{{ labelOf(f) }}</span>
    <el-select
      v-if="f === 'status'"
      v-model="local.status"
      clearable
      :placeholder="$tt('单据状态')"
      class="filter-select filter-status"
      style="width: 150px"
      @change="(v) => emitChange('status', !v || v === ALL_STATUS ? '' : v)"
    >
      <el-option :label="$tt('全部')" :value="ALL_STATUS" />
      <el-option v-for="s in statusOptions" :key="s.value" :label="$tf(s.label)" :value="s.value" />
    </el-select>
    <el-date-picker
      v-else-if="f === 'date'"
      v-model="localDateRange"
      type="daterange"
      unlink-panels
      clearable
      :range-separator="$tt('至')"
      :start-placeholder="$tt('开始日期')"
      :end-placeholder="$tt('结束日期')"
      value-format="YYYY-MM-DD"
      class="filter-date"
      style="width: 250px"
      @change="onDateChange"
    />
    <el-select
      v-else-if="f === 'customer'"
      v-model="local.customerId"
      filterable
      remote
      reserve-keyword
      clearable
      :remote-method="searchCustomers"
      :loading="loading.customer"
      :placeholder="$tt('客户名称（精准搜索，可输入）')"
      class="filter-select"
      style="width: 200px"
      @change="(v) => emitChange('customerId', v)"
      @visible-change="(open) => open && ensureOptions('customer')"
    >
      <el-option v-for="c in options.customers" :key="c.customerId" :label="c.customerName" :value="c.customerId" />
    </el-select>
    <el-select
      v-else-if="f === 'supplier'"
      v-model="local.supplierId"
      filterable
      remote
      reserve-keyword
      clearable
      :remote-method="searchSuppliers"
      :loading="loading.supplier"
      :placeholder="$tt('供应商名称')"
      class="filter-select"
      style="width: 200px"
      @change="(v) => emitChange('supplierId', v)"
      @visible-change="(open) => open && ensureOptions('supplier')"
    >
      <el-option v-for="s in options.suppliers" :key="s.supplierId" :label="s.supplierName" :value="s.supplierId" />
    </el-select>
    <el-select
      v-else-if="f === 'employee'"
      v-model="local.employeeId"
      filterable
      remote
      reserve-keyword
      clearable
      :remote-method="searchEmployees"
      :loading="loading.employee"
      :placeholder="employeeLabel"
      class="filter-select"
      style="width: 170px"
      @change="(v) => emitChange('employeeId', v)"
      @visible-change="(open) => open && ensureOptions('employee')"
    >
      <el-option
        v-for="e in options.employees"
        :key="e.employeeId"
        :label="e.employeeName + (e.employeeCode ? ' (' + e.employeeCode + ')' : '')"
        :value="e.employeeId"
      />
    </el-select>
  </div>
</template>

<script setup>
/**
 * 列表页通用搜索栏（4 维标准筛选）
 *
 * 用法（页面侧）：
 *   <BillSearchBar :filters="['date','customer','employee']" :model="search" employee-label="销售员"
 *                  @change="onFilterChange" @search="handleSearch" />
 *
 * 约定：
 * - 统一参数名：startDate / endDate / customerId / supplierId / employeeId（后端各页做列名映射）
 * - 只渲染 filters 里声明的维度；未声明的维度不渲染（不产生"点了没反应"的控件）
 * - 子组件不直接改父数据，一律 emit('change', {字段:值})，由页面写入自己的 search 对象
 */
import { reactive, ref, watch } from 'vue'
import { customerSelectOptions } from '@/api/basedata/customer'
import { supplierSelectOptions } from '@/api/basedata/supplier'
import { employeeSelectOptions } from '@/api/basedata/employee'

const props = defineProps({
  /** 维度清单：'status' 单据状态 / 'date' 日期区间 / 'customer' 客户 / 'supplier' 供应商 / 'employee' 员工 */
  filters: { type: Array, default: () => [] },
  /** 页面 search 对象（只读回显用；变更通过 change 事件回传） */
  model: { type: Object, default: () => ({}) },
  /** 员工维度文案（销售员 / 采购员 / 经手人…） */
  employeeLabel: { type: String, default: '员工' },
  /** 单据状态下拉选项：[{label, value}]；首位固定「全部」(value='')，由本组件内置 */
  statusOptions: { type: Array, default: () => [] },
  /** 日期维度标签（默认「单据日期」；台账类页面可传「交易日期」等真实口径） */
  dateLabel: { type: String, default: '单据日期' }
})

const emit = defineEmits(['change', 'search'])

/** 维度标签：让用户一眼看出每个筛选框是搜什么的（用户 2026-09-16 口径） */
const LABEL_MAP = { status: '单据状态', customer: '客户', supplier: '供应商' }

function labelOf(f) {
  if (f === 'employee') return props.employeeLabel || '员工'
  if (f === 'date') return props.dateLabel || '单据日期'
  return LABEL_MAP[f] || ''
}

/** 「全部」哨兵值：让下拉把「全部」显示为已选中项（用空串会退化成灰色占位提示）；对外仍回传空串 */
const ALL_STATUS = '__ALL__'
const local = reactive({ status: ALL_STATUS, customerId: null, supplierId: null, employeeId: null })
const options = reactive({ customers: [], suppliers: [], employees: [] })
const loading = reactive({ customer: false, supplier: false, employee: false })
const localDateRange = ref(null)
const loaded = reactive({ customer: false, supplier: false, employee: false })

/** 回显：支持数据看板穿透等外部注入（父对象变化时同步到本组件） */
watch(
  () => [props.model?.startDate, props.model?.endDate],
  ([sd, ed]) => {
    localDateRange.value = sd && ed ? [sd, ed] : null
  },
  { immediate: true }
)
watch(() => props.model?.status, (v) => { local.status = v || ALL_STATUS }, { immediate: true })
watch(() => props.model?.customerId, (v) => { local.customerId = v || null }, { immediate: true })
watch(() => props.model?.supplierId, (v) => { local.supplierId = v || null }, { immediate: true })
watch(() => props.model?.employeeId, (v) => { local.employeeId = v || null }, { immediate: true })

function emitChange(field, value) {
  emit('change', { [field]: value === undefined || value === null ? '' : value })
  emit('search')
}

function onDateChange(v) {
  emit('change', { startDate: v && v[0] ? v[0] : '', endDate: v && v[1] ? v[1] : '' })
  emit('search')
}

/** 下拉打开时先加载一批默认选项（remote 只在输入时触发，避免打开是空列表） */
function ensureOptions(kind) {
  if (loaded[kind]) return
  if (kind === 'customer') searchCustomers('')
  if (kind === 'supplier') searchSuppliers('')
  if (kind === 'employee') searchEmployees('')
}

async function searchCustomers(kw) {
  loading.customer = true
  try {
    const res = await customerSelectOptions(kw)
    options.customers = Array.isArray(res) ? res : (res?.records || [])
    loaded.customer = true
  } catch (e) {
    options.customers = [] // 静默降级：不弹诊断卡、不阻断列表
  } finally {
    loading.customer = false
  }
}

async function searchSuppliers(kw) {
  loading.supplier = true
  try {
    const res = await supplierSelectOptions(kw)
    options.suppliers = Array.isArray(res) ? res : (res?.records || [])
    loaded.supplier = true
  } catch (e) {
    options.suppliers = []
  } finally {
    loading.supplier = false
  }
}

async function searchEmployees(kw) {
  loading.employee = true
  try {
    const res = await employeeSelectOptions(kw)
    options.employees = Array.isArray(res) ? res : (res?.records || [])
    loaded.employee = true
  } catch (e) {
    options.employees = []
  } finally {
    loading.employee = false
  }
}
</script>

<style scoped>
/* 每个筛选维度：标签 + 控件（用户一眼看出这个框搜什么）
   统一宽度 + 标签固定列宽 → 筛选条件换行成两行/三行时，列与列严格对齐（用户 2026-09-16 口径）
   宽度取自系统「界面尺寸设置」的变量，改设置时所有筛选框同步变化 */
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
  text-align: right;                                          /* 标签右对齐 → 所有控件左边缘成一条线 */
  font-size: 13px;
  color: #6b7280;
  white-space: nowrap;
}

/* 控件撑满标签之后剩余宽度（模板里的内联宽度只作默认值，这里统一接管以保证列对齐） */
.filter-item > .el-select,
.filter-item > .el-input,
.filter-item > .el-date-editor {
  flex: 1 1 auto;
  width: auto !important;
}
</style>
