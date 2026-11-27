<template>
  <el-dialog
    v-model="visible"
    :title="$tf('替代出库')"
    width="min(720px, 94vw)"
    :close-on-click-modal="false"
    @open="onOpen"
  >
    <div class="replace-tip">
      仓库按实际库存在这里换替代品；系统保持与原销售订单的关联，不产生退货，单价默认订单价、可手工修改。
    </div>

    <el-descriptions :column="2" size="small" border class="replace-desc">
      <el-descriptions-item label="原货品">{{ originLabel }}</el-descriptions-item>
      <el-descriptions-item label="本行数量">{{ fmt(row && row.quantity) }}</el-descriptions-item>
      <el-descriptions-item label="来源订单">{{ row && row.sourceBillNo }}</el-descriptions-item>
      <el-descriptions-item label="原货可用">{{ fmt(originStock) }}</el-descriptions-item>
    </el-descriptions>

    <el-form :model="form" label-width="86px" class="replace-form">
      <el-form-item :label="$tt('替换货品')" required>
        <el-select v-model="form.materialId" filterable clearable :placeholder="$tt('选择替换货品')" style="width:320px">
          <el-option
            v-for="c in candidates"
            :key="c.materialId"
            :label="candidateLabel(c)"
            :value="c.materialId"
            :disabled="candidateDisabled(c)"
          />
        </el-select>
        <el-button size="small" text type="primary" @click="manualVisible = true">{{ $tt('手动选择其它货品') }}</el-button>
      </el-form-item>
      <el-form-item :label="$tt('替换数量')" required>
        <el-radio-group v-model="mode" size="small">
          <el-radio-button label="all">{{ $tt('全部') }}</el-radio-button>
          <el-radio-button label="part">{{ $tt('部分') }}</el-radio-button>
        </el-radio-group>
        <el-input-number
          v-model="form.quantity"
          :min="0"
          :max="maxQty"
          :precision="4"
          :controls="false"
          :disabled="mode === 'all'"
          style="width:120px;margin-left:8px"
        />
        <span class="qty-hint">{{ $tt('最多 ') }}{{ fmt(maxQty) }}</span>
      </el-form-item>
      <el-form-item :label="$tt('单价')">
        <el-input-number v-model="form.unitPrice" :min="0" :precision="4" :controls="false" style="width:140px" />
        <span class="qty-hint">{{ $tt('默认订单价，改价请备注原因') }}</span>
      </el-form-item>
      <el-form-item :label="$tt('备注')">
        <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" :placeholder="$tt('改价/现场替代原因')" />
      </el-form-item>
    </el-form>

    <MaterialPickerDialog v-model="manualVisible" @confirm="onManualPicked" />

    <template #footer>
      <el-button @click="visible = false">{{ $tt('取消') }}</el-button>
      <el-button type="primary" @click="confirm">{{ $tt('确定') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
import { listSubstitutes } from '@/api/basedata/materialSubstitute'
import { getMaterialStockSummary } from '@/api/inventory/inventory'
import { tt } from '@/i18n'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  row: { type: Object, default: () => ({}) },
  materials: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue', 'confirm'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const candidates = ref([])
const stockMap = ref({})
const manualVisible = ref(false)
const mode = ref('all')
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

const originMaterialId = computed(() => {
  const r = props.row || {}
  return r.sourceMaterialId || r.replacedFromMaterialId || r.materialId || null
})
const originMaterial = computed(() => (props.materials || [])
  .find(m => m.materialId === originMaterialId.value) || null)
const originBaseUnitId = computed(() => originMaterial.value && originMaterial.value.baseUnitId)
const originLabel = computed(() => {
  const r = props.row || {}
  const code = r.sourceMaterialCode || r.replacedFromMaterialCode || r.materialCode || ''
  const name = r.sourceMaterialName || r.replacedFromMaterialName || r.materialName || ''
  return `${code} ${name}`.trim()
})
const originStock = computed(() => stockMap.value[originMaterialId.value])
const maxQty = computed(() => num(props.row && props.row.quantity))

watch(mode, (m) => {
  if (m === 'all') form.quantity = maxQty.value
})

function candidateLabel(c) {
  const base = `${c.materialCode || ''} ${c.materialName || ''}`.trim()
  const stock = stockMap.value[c.materialId]
  if (stock == null) return base
  return `${base}（可用 ${fmt(stock)}）`
}
function candidateDisabled(c) {
  return !!(originBaseUnitId.value && c.baseUnitId && c.baseUnitId !== originBaseUnitId.value)
}

async function loadCandidates() {
  if (!originMaterialId.value) {
    candidates.value = []
    return
  }
  const rows = (await listSubstitutes(originMaterialId.value)) || []
  candidates.value = rows.map(r => ({
    materialId: r.substituteMaterialId,
    materialCode: r.materialCode,
    materialName: r.materialName,
    specification: r.specification,
    baseUnitId: r.baseUnitId,
    mainImage: r.mainImage
  }))
  await loadStock([originMaterialId.value, ...candidates.value.map(c => c.materialId)])
}

async function loadStock(ids) {
  const list = (ids || []).filter(Boolean)
  if (list.length === 0) {
    stockMap.value = {}
    return
  }
  try {
    const rows = (await getMaterialStockSummary(list)) || []
    const map = {}
    rows.forEach(r => { map[r.materialId] = r.availableQuantity })
    stockMap.value = map
  } catch (e) {
    stockMap.value = {}
  }
}

function onOpen() {
  mode.value = 'all'
  form.materialId = null
  form.quantity = maxQty.value
  form.unitPrice = num(props.row && props.row.unitPrice)
  form.remark = ''
  loadCandidates().catch(e => ElMessage.warning(e.message || tt('加载替代清单失败')))
}

function onManualPicked({ rows }) {
  if (!rows || rows.length === 0) return
  const m = rows[0]
  if (originBaseUnitId.value && m.baseUnitId && m.baseUnitId !== originBaseUnitId.value) {
    ElMessage.warning(tt('替换货品与原货品基本单位不一致，不能使用'))
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

function confirm() {
  if (!form.materialId) {
    ElMessage.warning(tt('请选择替换货品'))
    return
  }
  const qty = num(form.quantity)
  if (!(qty > 0)) {
    ElMessage.warning(tt('替换数量必须大于 0'))
    return
  }
  if (qty > maxQty.value + 0.0001) {
    ElMessage.warning(`${tt('最多可替换 ')}${fmt(maxQty.value)}`)
    return
  }
  const material = candidates.value.find(c => c.materialId === form.materialId)
  if (!material) {
    ElMessage.warning(tt('替换货品不存在，请重新选择'))
    return
  }
  emit('confirm', {
    material,
    quantity: qty,
    unitPrice: num(form.unitPrice),
    remark: form.remark || ''
  })
  visible.value = false
}
</script>

<style scoped lang="scss">
.replace-tip {
  margin-bottom: 10px;
  padding: 8px 12px;
  background: #f4f8ff;
  border: 1px solid #d9ecff;
  border-radius: 4px;
  color: var(--el-color-primary);
  font-size: 12px;
  line-height: 1.6;
}
.replace-desc { margin-bottom: 12px; }
.qty-hint { margin-left: 8px; color: var(--el-text-color-secondary); font-size: 12px; }
</style>
