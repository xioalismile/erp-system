<template>
  <el-dialog
    v-model="visible"
    :title="$tf('销售替代出库')"
    width="min(1180px, 94vw)"
    :close-on-click-modal="false"
    class="sale-exchange-dialog"
    @open="handleOpen"
  >
    <div class="dialog-tip">
      勾选需要替换的订单行，填写替换数量与替换货品；替换数量回写原订单行，已发货部分形成待退回数量。
    </div>

    <el-table
      ref="tableRef"
      :data="rows"
      size="small"
      border
      v-loading="loading"
      max-height="460"
      row-key="orderItemId"
      @selection-change="onSelectionChange"
    >
      <el-table-column type="selection" width="46" :selectable="rowSelectable" reserve-selection />
      <el-table-column prop="lineNo" label="行号" width="60" align="center" />
      <el-table-column label="原货品" min-width="200">
        <template #default="{ row }">
          <span class="opt-code">{{ row.materialCode }}</span>
          <span class="opt-name">{{ row.materialName }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="orderQuantity" label="订单数量" width="90" align="right" />
      <el-table-column prop="deliveredQuantity" label="已发货" width="80" align="right" />
      <el-table-column prop="returnedQuantity" label="已退货" width="80" align="right" />
      <el-table-column prop="exchangedQuantity" label="已换货" width="80" align="right" />
      <el-table-column prop="pendingReturnQuantity" label="待退回" width="80" align="right" />
      <el-table-column prop="replaceableQuantity" label="可替换" width="80" align="right" />
      <el-table-column label="替换数量" width="120" align="center">
        <template #default="{ row }">
          <el-input-number
            v-model="row._quantity"
            :precision="4"
            :min="0"
            :max="Number(row.replaceableQuantity || 0)"
            :controls="false"
            size="small"
            :disabled="!row._selected || Number(row.replaceableQuantity || 0) <= 0"
            style="width:100%"
            @change="(v) => onQuantityChange(row, v)"
          />
        </template>
      </el-table-column>
      <el-table-column label="替换货品" min-width="240">
        <template #default="{ row }">
          <div class="substitute-cell">
            <el-select
              v-model="row._substituteMaterialId"
              filterable
              clearable
              :placeholder="$tt('选择替换货品')"
              size="small"
              style="flex:1;min-width:140px"
              :disabled="!row._selected || Number(row.replaceableQuantity || 0) <= 0"
              @change="() => onSubstituteChange(row)"
            >
              <el-option
                v-for="s in (row.substituteOptions || [])"
                :key="s.materialId"
                :label="`${s.materialCode} ${s.materialName}`"
                :value="s.materialId"
              >
                <span class="opt-code">{{ s.materialCode }}</span>
                <span class="opt-name">{{ s.materialName }}</span>
              </el-option>
            </el-select>
            <el-button
              size="small"
              text
              type="primary"
              :disabled="!row._selected || Number(row.replaceableQuantity || 0) <= 0"
              @click="openManualPicker(row)"
            >手动选择其它货品</el-button>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="单价" width="110" align="center">
        <template #default="{ row }">
          <el-input-number
            v-model="row._unitPrice"
            :precision="4"
            :min="0"
            :controls="false"
            size="small"
            :disabled="!row._selected || Number(row.replaceableQuantity || 0) <= 0"
            style="width:100%"
            @change="() => onUnitPriceChange(row)"
          />
        </template>
      </el-table-column>
      <el-table-column label="金额" width="110" align="right">
        <template #default="{ row }">
          <span class="amount-text">{{ amountText(row) }}</span>
        </template>
      </el-table-column>
    </el-table>

    <!-- 手动选择其它货品（全库选料，复用通用物料库弹窗） -->
    <MaterialPickerDialog v-model="pickerVisible" @confirm="onManualPicked" />

    <template #footer>
      <el-button @click="visible = false">{{ $tt('取消') }}</el-button>
      <el-button type="primary" :loading="submitting" @click="handleConfirm">{{ $tt('确定并生成换货单') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getReplaceableLines, createExchangeFromOrder } from '@/api/sale/saleExchange'
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
import { tt } from '@/i18n'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  order: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['update:modelValue', 'created'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const loading = ref(false)
const submitting = ref(false)
const rows = ref([])
const tableRef = ref()
const selectedRows = ref([])

/** 可替换为 0 的行禁用勾选 */
function rowSelectable(row) {
  return Number(row.replaceableQuantity || 0) > 0
}

function onSelectionChange(list) {
  selectedRows.value = list
  rows.value.forEach(r => {
    r._selected = list.includes(r)
    if (!r._selected) {
      r._quantity = 0
    } else if (!(Number(r._quantity) > 0)) {
      r._quantity = Number(r.replaceableQuantity || 0)
    }
  })
}

async function handleOpen() {
  rows.value = []
  selectedRows.value = []
  const orderId = props.order && (props.order.orderId || props.order.id)
  if (!orderId) {
    ElMessage.warning(tt('缺少销售订单信息，无法发起替代出库'))
    return
  }
  loading.value = true
  try {
    const list = (await getReplaceableLines(orderId)) || []
    const hasAny = list.some(r => Number(r.replaceableQuantity || 0) > 0)
    if (!hasAny) {
      // 2026-11-18：替换发货已完成（全部行可替换额度=0）时不再打开空弹窗，明确提示并关闭
      ElMessage.warning(tt('该订单的替换发货已完成，没有可替换的货品'))
      visible.value = false
      return
    }
    rows.value = list.map(r => ({
      ...r,
      _selected: Number(r.replaceableQuantity || 0) > 0,
      _quantity: Number(r.replaceableQuantity || 0),
      _substituteMaterialId: (r.substituteOptions && r.substituteOptions.length === 1) ? r.substituteOptions[0].materialId : null,
      _unitPrice: r.unitPrice != null ? Number(r.unitPrice) : 0
    }))
    await nextTick()
    // 默认勾选全部可替换行
    if (tableRef.value) {
      tableRef.value.clearSelection()
      rows.value.forEach(r => {
        if (Number(r.replaceableQuantity || 0) > 0) tableRef.value.toggleRowSelection(r, true)
      })
    }
  } catch (e) {
    ElMessage.error(e.message || tt('加载可替换明细失败'))
  } finally {
    loading.value = false
  }
}

function onQuantityChange(row, v) {
  const max = Number(row.replaceableQuantity || 0)
  if (Number(v || 0) > max) {
    row._quantity = max
    ElMessage.warning(`${tt('第')}${row.lineNo}${tt('行替换数量不能超过可替换数量 ')}${max}`)
  }
}

function onSubstituteChange(row) {
  const opt = (row.substituteOptions || []).find(s => s.materialId === row._substituteMaterialId)
  if (opt && row.originMaterialId && opt.materialId === row.originMaterialId) {
    ElMessage.warning(tt('替换货品不能与原货品相同'))
  }
}

function onUnitPriceChange(row) {
  if (row._unitPrice == null || Number(row._unitPrice) < 0) row._unitPrice = 0
}

function amountText(row) {
  return (Number(row._quantity || 0) * Number(row._unitPrice || 0)).toFixed(2)
}

// ===== 手动选择其它货品（全库选料）=====
const pickerVisible = ref(false)
const pickingRow = ref(null)

function openManualPicker(row) {
  pickingRow.value = row
  pickerVisible.value = true
}

function onManualPicked({ rows: picked }) {
  if (!picked || picked.length === 0) return
  const m = picked[0]
  const row = pickingRow.value
  if (!row) return
  // 记入行内候选，保证下拉回显（同一货品去重）
  if (!row.substituteOptions) row.substituteOptions = []
  if (!row.substituteOptions.some(s => s.materialId === m.materialId)) {
    row.substituteOptions.push({
      materialId: m.materialId,
      materialCode: m.materialCode,
      materialName: m.materialName,
      specification: m.model,
      baseUnitId: m.baseUnitId,
      mainImage: m.mainImage
    })
  }
  row._substituteMaterialId = m.materialId
  row._substituteMaterialCode = m.materialCode
  row._substituteMaterialName = m.materialName
  pickerVisible.value = false
  pickingRow.value = null
}

function substituteOf(row) {
  return (row.substituteOptions || []).find(s => s.materialId === row._substituteMaterialId) || null
}

async function handleConfirm() {
  const chosen = rows.value.filter(r => r._selected)
  if (chosen.length === 0) {
    ElMessage.warning(tt('请至少勾选一行需要替换的货品'))
    return
  }
  const lines = []
  for (const row of chosen) {
    const qty = Number(row._quantity || 0)
    const max = Number(row.replaceableQuantity || 0)
    if (!(qty > 0)) {
      ElMessage.warning(`${tt('第')}${row.lineNo}${tt('行请填写替换数量')}`)
      return
    }
    if (qty > max) {
      ElMessage.warning(`${tt('第')}${row.lineNo}${tt('行替换数量超过可替换数量 ')}${max}`)
      return
    }
    if (!row._substituteMaterialId) {
      ElMessage.warning(`${tt('第')}${row.lineNo}${tt('行请选择替换货品')}`)
      return
    }
    if (row._substituteMaterialId === row.originMaterialId) {
      ElMessage.warning(`${tt('第')}${row.lineNo}${tt('行替换货品不能与原货品相同')}`)
      return
    }
    // 前端提示：已选候选里带基本单位且与原货品候选单位不一致时提醒（最终以后端校验为准）
    const sub = substituteOf(row)
    if (sub && sub.baseUnitId && row.baseUnitId && sub.baseUnitId !== row.baseUnitId) {
      const ok = await new Promise(resolve => {
        ElMessageBox.confirm(tt('替换货品与原货品基本单位不一致，保存时将被拒绝，是否继续？'), tt('提示'), {
          confirmButtonText: tt('继续'),
          cancelButtonText: tt('取消'),
          type: 'warning'
        }).then(() => resolve(true)).catch(() => resolve(false))
      })
      if (!ok) return
    }
    lines.push({
      orderItemId: row.orderItemId,
      quantity: qty,
      substituteMaterialId: row._substituteMaterialId,
      unitPrice: Number(row._unitPrice || 0)
    })
  }
  const orderId = props.order && (props.order.orderId || props.order.id)
  submitting.value = true
  try {
    const res = await createExchangeFromOrder({ orderId, lines })
    const exchangeId = res && (res.exchangeId || res.id)
    ElMessage.success(tt('换货单已生成'))
    visible.value = false
    emit('created', exchangeId)
  } catch (e) {
    ElMessage.error(e.message || tt('生成换货单失败'))
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped lang="scss">
.dialog-tip {
  margin-bottom: 10px;
  padding: 8px 12px;
  background: #f4f8ff;
  border: 1px solid #d9ecff;
  border-radius: 4px;
  color: #409eff;
  font-size: 12px;
  line-height: 1.6;
}

.substitute-cell {
  display: flex;
  align-items: center;
  gap: 4px;
}

.opt-code { color: #909399; margin-right: 6px; }
.opt-name { color: #303133; }
.amount-text { color: #6366f1; font-weight: 600; font-variant-numeric: tabular-nums; }
</style>
