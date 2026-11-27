<template>
  <el-dialog
    v-model="visible"
    :title="$tf('替换发货指示')"
    width="min(980px, 94vw)"
    :close-on-click-modal="false"
    @open="onOpen"
  >
    <div class="plan-tip">
      销售在这里指定"这一行发多少、用哪个替代品"；仓库下推或选单时自动按指示展开，不改变订单行的货品、数量和金额。
    </div>

    <el-descriptions :column="4" size="small" border class="plan-desc">
      <el-descriptions-item label="订单行">{{ item ? item.lineNo : '' }}</el-descriptions-item>
      <el-descriptions-item label="原货品">{{ originLabel }}</el-descriptions-item>
      <el-descriptions-item label="订购数量">{{ fmt(item && item.quantity) }}</el-descriptions-item>
      <el-descriptions-item label="已发 / 已退">{{ fmt(item && item.turnedOutputQuantity) }} / {{ fmt(item && item.turnedReturnQuantity) }}</el-descriptions-item>
      <el-descriptions-item label="待发数量">{{ fmt(undelivered) }}</el-descriptions-item>
      <el-descriptions-item label="待执行指示">{{ fmt(pendingTotal) }}</el-descriptions-item>
      <el-descriptions-item label="可再指定">{{ fmt(available) }}</el-descriptions-item>
      <el-descriptions-item label="订单号">{{ order && order.orderNo }}</el-descriptions-item>
    </el-descriptions>

    <el-table :data="activePlans" size="small" border class="plan-table" v-loading="loading">
      <el-table-column label="替代货品" min-width="240">
        <template #default="{ row }">
          <span class="opt-code">{{ row.substituteMaterialCode }}</span>
          <span class="opt-name">{{ row.substituteMaterialName }}</span>
        </template>
      </el-table-column>
      <el-table-column label="指示数量" width="100" align="right">
        <template #default="{ row }">{{ fmt(row.quantity) }}</template>
      </el-table-column>
      <el-table-column label="已执行" width="100" align="right">
        <template #default="{ row }">{{ fmt(row.turnedDeliveryQuantity) }}</template>
      </el-table-column>
      <el-table-column label="待执行" width="100" align="right">
        <template #default="{ row }">{{ fmt(pendingOf(row)) }}</template>
      </el-table-column>
      <el-table-column label="单价" width="100" align="right">
        <template #default="{ row }">{{ fmt(row.unitPrice) }}</template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="140" align="center">
        <template #default="{ row }">
          <el-button text type="primary" size="small" :disabled="num(row.turnedDeliveryQuantity) > 0" @click="editPlan(row)">编辑</el-button>
          <el-button text type="warning" size="small" @click="cancelPlan(row)">取消剩余</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-divider content-position="left">{{ editingId ? '编辑指示' : '新增指示' }}</el-divider>

    <el-form :model="form" inline label-width="76px" class="plan-form">
      <el-form-item label="替代货品" required>
        <el-select v-model="form.materialId" filterable clearable :placeholder="$tt('选择替代货品')" style="width:260px">
          <el-option
            v-for="c in candidates"
            :key="c.materialId"
            :label="candidateLabel(c)"
            :value="c.materialId"
            :disabled="candidateDisabled(c)"
          />
        </el-select>
        <el-button size="small" text type="primary" @click="manualVisible = true">手动选择其它货品</el-button>
      </el-form-item>
      <el-form-item label="数量" required>
        <el-input-number v-model="form.quantity" :min="0" :max="available" :precision="4" :controls="false" style="width:130px" />
      </el-form-item>
      <el-form-item label="单价">
        <el-input-number v-model="form.unitPrice" :min="0" :precision="4" :controls="false" style="width:130px" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" placeholder="改价时建议写明原因" style="width:260px" maxlength="500" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="save">保存指示</el-button>
        <el-button v-if="editingId" @click="resetForm">取消编辑</el-button>
      </el-form-item>
    </el-form>

    <MaterialPickerDialog v-model="manualVisible" @confirm="onManualPicked" />

    <template #footer>
      <el-button @click="visible = false">{{ $tt('关闭') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
import { listSubstitutes } from '@/api/basedata/materialSubstitute'
import { getMaterialStockSummary } from '@/api/inventory/inventory'
import {
  listSubstitutePlans,
  saveSubstitutePlan,
  updateSubstitutePlan,
  cancelSubstitutePlan
} from '@/api/sale/saleOrderSubstitutePlan'
import { tt } from '@/i18n'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  order: { type: Object, default: () => ({}) },
  item: { type: Object, default: () => ({}) },
  materials: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue', 'changed'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const loading = ref(false)
const saving = ref(false)
const plans = ref([])
const candidates = ref([])
const stockMap = ref({})
const manualVisible = ref(false)
const editingId = ref(null)
const form = reactive({
  materialId: null,
  quantity: 0,
  unitPrice: 0,
  remark: ''
})

const num = (v) => {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}
const fmt = (v) => {
  const n = Number(v)
  return Number.isFinite(n) ? String(Number(n.toFixed(4))) : ''
}
const pendingOf = (p) => {
  if (p == null) return 0
  if (p.pendingQuantity != null) return Math.max(0, num(p.pendingQuantity))
  return Math.max(0, num(p.quantity) - num(p.turnedDeliveryQuantity))
}
const originMaterial = computed(() => {
  if (!props.item) return null
  return (props.materials || []).find(m => m.materialId === props.item.materialId) || null
})
const originBaseUnitId = computed(() => originMaterial.value && originMaterial.value.baseUnitId)
const originLabel = computed(() => {
  if (!props.item) return ''
  return `${props.item.materialCode || ''} ${props.item.materialName || ''}`.trim()
})
const itemPlans = computed(() => (props.item
  ? (plans.value || []).filter(p => p.orderItemId === props.item.orderItemId)
  : []))
const activePlans = computed(() => itemPlans.value.filter(p =>
  p.status === '有效' && pendingOf(p) > 0
))
const undelivered = computed(() => {
  if (!props.item) return 0
  return Math.max(0, num(props.item.quantity) - num(props.item.turnedOutputQuantity) + num(props.item.turnedReturnQuantity))
})
const pendingTotal = computed(() => activePlans.value.reduce((s, p) => s + pendingOf(p), 0))
const otherPending = computed(() => activePlans.value
  .filter(p => p.planId !== editingId.value)
  .reduce((s, p) => s + pendingOf(p), 0))
const available = computed(() => Math.max(0, undelivered.value - otherPending.value))

function candidateLabel(c) {
  const base = `${c.materialCode || ''} ${c.materialName || ''}`.trim()
  const stock = stockMap.value[c.materialId]
  if (stock == null) return base
  return `${base}（可用 ${fmt(stock)}）`
}
function candidateDisabled(c) {
  return !!(originBaseUnitId.value && c.baseUnitId && c.baseUnitId !== originBaseUnitId.value)
}

async function loadPlans() {
  if (!props.order || !props.order.orderId) return
  plans.value = (await listSubstitutePlans(props.order.orderId)) || []
}

async function loadCandidates() {
  if (!props.item || !props.item.materialId) return
  const rows = (await listSubstitutes(props.item.materialId)) || []
  candidates.value = rows.map(r => ({
    materialId: r.substituteMaterialId,
    materialCode: r.materialCode,
    materialName: r.materialName,
    specification: r.specification,
    baseUnitId: r.baseUnitId,
    mainImage: r.mainImage
  }))
  await loadStock(candidates.value.map(c => c.materialId))
}

async function loadStock(materialIds) {
  const ids = (materialIds || []).filter(Boolean)
  if (ids.length === 0) {
    stockMap.value = {}
    return
  }
  try {
    const rows = (await getMaterialStockSummary(ids)) || []
    const map = {}
    rows.forEach(r => { map[r.materialId] = r.availableQuantity })
    stockMap.value = map
  } catch (e) {
    stockMap.value = {}
  }
}

function resetForm() {
  editingId.value = null
  form.materialId = candidates.value.length === 1 ? candidates.value[0].materialId : null
  form.quantity = available.value
  form.unitPrice = props.item ? num(props.item.unitPrice) : 0
  form.remark = ''
}

function editPlan(plan) {
  if (num(plan.turnedDeliveryQuantity) > 0) {
    ElMessage.warning(tt('该指示已执行，只能取消剩余，不能修改'))
    return
  }
  editingId.value = plan.planId
  form.materialId = plan.substituteMaterialId
  form.quantity = num(plan.quantity)
  form.unitPrice = plan.unitPrice != null ? num(plan.unitPrice) : num(props.item.unitPrice)
  form.remark = plan.remark || ''
}

async function save() {
  if (!form.materialId) {
    ElMessage.warning(tt('请选择替代货品'))
    return
  }
  if (!(num(form.quantity) > 0)) {
    ElMessage.warning(tt('替换数量必须大于 0'))
    return
  }
  if (num(form.quantity) > available.value + 0.0001) {
    ElMessage.warning(`${tt('最多可指定 ')}${fmt(available.value)}`)
    return
  }
  const body = {
    orderId: props.order.orderId,
    orderItemId: props.item.orderItemId,
    substituteMaterialId: form.materialId,
    quantity: num(form.quantity),
    unitPrice: num(form.unitPrice),
    remark: form.remark || ''
  }
  saving.value = true
  try {
    if (editingId.value) await updateSubstitutePlan(editingId.value, body)
    else await saveSubstitutePlan(body)
    ElMessage.success(tt('替换发货指示已保存'))
    await loadPlans()
    resetForm()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message || tt('保存失败'))
  } finally {
    saving.value = false
  }
}

async function cancelPlan(plan) {
  try {
    await ElMessageBox.confirm(
      `${tt('取消该指示的剩余数量？已执行 ')}${fmt(plan.turnedDeliveryQuantity)}${tt(' 保留，不再执行。')}`,
      tt('取消替换发货指示'),
      { type: 'warning', confirmButtonText: tt('确认取消'), cancelButtonText: tt('返回') }
    )
  } catch (e) {
    return
  }
  try {
    await cancelSubstitutePlan(plan.planId)
    ElMessage.success(tt('已取消剩余指示'))
    await loadPlans()
    resetForm()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message || tt('取消失败'))
  }
}

function onManualPicked({ rows }) {
  if (!rows || rows.length === 0) return
  const m = rows[0]
  if (originBaseUnitId.value && m.baseUnitId && m.baseUnitId !== originBaseUnitId.value) {
    ElMessage.warning(tt('替代货品与原货品基本单位不一致，不能使用'))
    return
  }
  if (!candidates.value.some(c => c.materialId === m.materialId)) {
    candidates.value.push({
      materialId: m.materialId,
      materialCode: m.materialCode,
      materialName: m.materialName,
      specification: m.model || m.specification,
      baseUnitId: m.baseUnitId,
      mainImage: m.mainImage
    })
  }
  form.materialId = m.materialId
  manualVisible.value = false
  loadStock([m.materialId])
}

async function onOpen() {
  loading.value = true
  try {
    await Promise.all([loadPlans(), loadCandidates()])
    resetForm()
  } catch (e) {
    ElMessage.error(e.message || tt('加载替换发货指示失败'))
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.plan-tip {
  margin-bottom: 10px;
  padding: 8px 12px;
  background: #f4f8ff;
  border: 1px solid #d9ecff;
  border-radius: 4px;
  color: var(--el-color-primary);
  font-size: 12px;
  line-height: 1.6;
}
.plan-desc { margin-bottom: 12px; }
.plan-table { margin-bottom: 8px; }
.plan-form { margin-top: 4px; }
.opt-code { color: var(--el-text-color-secondary); margin-right: 6px; }
.opt-name { color: var(--el-text-color-primary); }
</style>
