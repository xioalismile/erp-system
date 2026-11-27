<template>
  <!--
    动态筛选控件通用渲染件（2026-10-14 列表页筛选条件自定义 一期）
    按字段定义类型渲染一种控件，值通过 v-model 双向绑定：
      text       → 文本框（模糊）
      select     → 下拉（静态 options 或远程主档 optionSource）
      dateRange  → 日期区间 [起, 止]
      numberRange→ 数值区间 { min, max }
    事件口径（父级决定何时查询）：
      - update:modelValue：值变化即回传（仅存值，不触发查询）
      - submit：值"提交"时回传 —— 下拉/日期=选中即提交；文本/数值=回车、失焦或点清除时提交
    口径：
      - 只渲染、只回传值；是否触发搜索由父级决定（与通用搜索栏 / 明细报表页一致）
      - 下拉选项按 optionSource 懒加载，**缓存只存在于本组件实例**（客户/员工等主档带数据权限，禁止跨用户全局缓存）
      - 非法值不拦截：由后端白名单忽略（前后端双兜底）
  -->
  <el-date-picker
    v-if="def.type === 'dateRange'"
    :model-value="rangeValue"
    type="daterange"
    unlink-panels
    clearable
    :range-separator="$tt('至')"
    :start-placeholder="$tt('开始')"
    :end-placeholder="$tt('结束')"
    value-format="YYYY-MM-DD"
    class="sfc-control"
    @update:model-value="onRangeChange"
  />
  <span v-else-if="def.type === 'numberRange'" class="sfc-num">
    <el-input
      :model-value="numValue.min"
      :placeholder="$tt('最小')"
      clearable
      class="sfc-num-input"
      @update:model-value="(v) => onNumChange('min', v)"
      @change="() => emitSubmit()"
      @clear="() => emitSubmit()"
    />
    <span class="sfc-num-sep">~</span>
    <el-input
      :model-value="numValue.max"
      :placeholder="$tt('最大')"
      clearable
      class="sfc-num-input"
      @update:model-value="(v) => onNumChange('max', v)"
      @change="() => emitSubmit()"
      @clear="() => emitSubmit()"
    />
  </span>
  <el-select
    v-else-if="def.type === 'select'"
    :model-value="scalarValue"
    filterable
    clearable
    remote
    reserve-keyword
    :remote-method="searchOptions"
    :loading="optionLoading"
    :placeholder="def.placeholder || $tt('请选择')"
    class="sfc-control"
    @update:model-value="onScalarCommit"
    @visible-change="(open) => open && ensureOptions()"
  >
    <el-option v-for="o in currentOptions" :key="String(o.value)" :label="o.label" :value="o.value" />
  </el-select>
  <el-input
    v-else
    :model-value="scalarValue"
    clearable
    :placeholder="def.placeholder || $tt('请输入')"
    class="sfc-control"
    @update:model-value="(v) => emitValue(v)"
    @change="() => emitSubmit()"
    @clear="() => emitSubmit()"
    @keyup.enter="() => emitSubmit()"
  />
</template>

<script setup>
import { computed, ref } from 'vue'
import { customerSelectOptions } from '@/api/basedata/customer'
import { supplierSelectOptions } from '@/api/basedata/supplier'
import { employeeSelectOptions } from '@/api/basedata/employee'
import { listAllDepartment } from '@/api/basedata/department'
import { listAllCurrency } from '@/api/basedata/currency'
import { listAllSettle } from '@/api/basedata/settle'
import { listAllPriceLevel } from '@/api/basedata/price-level'

const props = defineProps({
  /** 字段定义 { key, label, type, optionSource, options, placeholder } */
  def: { type: Object, required: true },
  modelValue: { type: [String, Number, Array, Object], default: '' }
})

const emit = defineEmits(['update:modelValue', 'submit'])

/** 标量值（text / select） */
const scalarValue = computed(() => (props.def.type === 'numberRange' ? '' : (props.modelValue ?? '')))

/** 日期区间值 [起, 止] */
const rangeValue = computed(() => (Array.isArray(props.modelValue) && props.modelValue.length === 2 ? props.modelValue : null))

/** 数值区间值 { min, max }（空值统一成空串，避免受控告警） */
const numValue = computed(() => {
  const v = props.modelValue && typeof props.modelValue === 'object' ? props.modelValue : {}
  return { min: v.min ?? '', max: v.max ?? '' }
})

function emitValue(v) {
  emit('update:modelValue', v === undefined || v === null ? '' : v)
}

/** 值提交（触发父级查询） */
function emitSubmit() {
  emit('submit')
}

function onScalarCommit(v) {
  emitValue(v)
  emitSubmit()
}

function onRangeChange(v) {
  emitValue(Array.isArray(v) ? v : [])
  emitSubmit()
}

function onNumChange(part, v) {
  emit('update:modelValue', { ...numValue.value, [part]: v })
}

// ==================== 下拉选项（按来源懒加载；实例级缓存，不跨用户共享） ====================
const optionList = ref([])
const optionLoading = ref(false)
let optionLoaded = false

/** 静态 options 优先（单据状态等枚举，后端/页面写死值域） */
const currentOptions = computed(() => {
  if (Array.isArray(props.def.options) && props.def.options.length) {
    return props.def.options.map(o => (typeof o === 'object' ? o : { value: o, label: o }))
  }
  return optionList.value
})

function ensureOptions() {
  if (optionLoaded || !props.def.optionSource) return
  searchOptions('')
}

async function searchOptions(kw) {
  const source = props.def.optionSource
  if (!source || (Array.isArray(props.def.options) && props.def.options.length)) return
  optionLoading.value = true
  try {
    if (source === 'customer') {
      const res = await customerSelectOptions(kw || '')
      optionList.value = norm(res, 'customerId', 'customerName')
    } else if (source === 'supplier') {
      const res = await supplierSelectOptions(kw || '')
      optionList.value = norm(res, 'supplierId', 'supplierName')
    } else if (source === 'employee') {
      const res = await employeeSelectOptions(kw || '')
      optionList.value = (Array.isArray(res) ? res : (res?.records || []))
        .map(e => ({ value: e.employeeId, label: e.employeeName + (e.employeeCode ? ` (${e.employeeCode})` : '') }))
    } else if (source === 'department') {
      const res = await listAllDepartment()
      optionList.value = norm(res, 'deptId', 'deptName')
    } else if (source === 'currency') {
      const res = await listAllCurrency()
      optionList.value = norm(res, 'currencyId', 'currencyCode')
    } else if (source === 'settleMode') {
      const res = await listAllSettle()
      optionList.value = norm(res, 'settleModeId', 'settleModeName')
    } else if (source === 'priceLevel') {
      const res = await listAllPriceLevel()
      optionList.value = norm(res, 'priceLevelId', 'priceLevelName')
    } else {
      optionList.value = []
    }
    optionLoaded = true
  } catch (e) {
    optionList.value = [] // 静默降级：不弹诊断卡、不阻断列表
  } finally {
    optionLoading.value = false
  }
}

function norm(res, valueKey, labelKey) {
  const list = Array.isArray(res) ? res : (res?.records || [])
  return list.map(x => ({ value: x[valueKey], label: x[labelKey] }))
}
</script>

<style scoped>
.sfc-control { width: 100%; }
.sfc-num { display: flex; align-items: center; gap: 4px; width: 100%; }
.sfc-num .sfc-num-input { flex: 1 1 0; min-width: 0; }
.sfc-num .sfc-num-sep { color: var(--el-text-color-secondary, #909399); flex: 0 0 auto; }
</style>
