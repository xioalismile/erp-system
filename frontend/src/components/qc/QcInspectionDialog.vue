<template>
  <el-dialog v-model="visible" :title="dialogTitle" width="1180px" top="6vh" destroy-on-close>
    <div v-loading="loading" class="qc-inspection">
      <div class="qc-toolbar">
        <span class="qc-label">质检模板</span>
        <el-select v-model="templateId" style="width: 280px" placeholder="选择单据质检模板" @change="onTemplateChange">
          <el-option v-for="t in templates" :key="t.templateId" :label="t.templateName" :value="t.templateId" />
        </el-select>
        <span class="qc-summary">
          送检 {{ totalQty }} · 合格 {{ totalQualified }} · 不合格 {{ totalRejected }}
        </span>
      </div>

      <el-table :data="rows" border size="small" max-height="460" class="qc-table">
        <el-table-column prop="lineNo" label="行号" width="60" align="center" />
        <el-table-column prop="materialCode" label="物料编码" width="130" show-overflow-tooltip />
        <el-table-column prop="materialName" label="物料名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="specification" label="规格型号" width="120" show-overflow-tooltip />
        <el-table-column label="送检数量" width="110" align="right">
          <template #default="{ row }">{{ formatWith(row.quantity, scaleFor('qty'), trimZero.value) }}</template>
        </el-table-column>
        <el-table-column label="合格数量" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.qualifiedQuantity" :min="0" :max="row.quantity" :precision="scaleFor('qty')"
                             :controls="false" size="small" style="width:100%"
                             @change="v => onQualifiedChange(row, v)" />
          </template>
        </el-table-column>
        <el-table-column label="不合格数量" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.rejectedQuantity" :min="0" :max="row.quantity" :precision="scaleFor('qty')"
                             :controls="false" size="small" style="width:100%"
                             @change="v => onRejectedChange(row, v)" />
          </template>
        </el-table-column>
        <el-table-column label="不合格处理" width="150">
          <template #default="{ row }">
            <el-select v-model="row.handlingType" size="small" style="width:100%"
                       :disabled="Number(row.rejectedQuantity) <= 0">
              <el-option label="放行" value="RELEASE" />
              <el-option label="自动退货" value="RETURN" />
              <el-option label="进报废仓" value="SCRAP" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="报废仓" width="160">
          <template #default="{ row }">
            <el-select v-model="row.scrapWarehouseId" size="small" style="width:100%" clearable
                       :disabled="row.handlingType !== 'SCRAP'"
                       :placeholder="row.handlingType === 'SCRAP' ? '选择报废仓' : ''">
              <el-option v-for="w in warehouses" :key="w.stockId" :label="w.stockName" :value="w.stockId" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column v-for="f in templateItems" :key="f.itemName" :label="fieldLabel(f)" min-width="140">
          <template #default="{ row }">
            <el-input v-model="row.extData[f.itemName]" size="small" :placeholder="f.standardValue || ''" />
          </template>
        </el-table-column>
        <el-table-column label="不合格原因" min-width="150">
          <template #default="{ row }">
            <el-input v-model="row.rejectReason" size="small" placeholder="填写不合格原因" />
          </template>
        </el-table-column>
      </el-table>

      <el-input v-model="remark" type="textarea" :rows="2" class="qc-remark" placeholder="整单质检备注" />
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">保存质检</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { tt } from '@/i18n'
import { getInboundQcContext, saveInboundQc } from '@/api/qc/inboundQc'
import { getQcTemplate } from '@/api/qc/qcTemplate'
import { listAllWarehouse } from '@/api/basedata/warehouse'
import { usePrecision } from '@/composables/usePrecision'

// 数量输入/显示的位数统一读设置中心「数量小数位数」（2026-11-10）
const { scaleFor, formatWith, trimZero } = usePrecision()

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  billType: { type: String, default: '' },
  billId: { type: [Number, String], default: null },
  billNo: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const visible = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v)
})
const dialogTitle = computed(() => `${tt('入库质检')}${props.billNo ? ' · ' + props.billNo : ''}`)

const loading = ref(false)
const saving = ref(false)
const templates = ref([])
const templateId = ref(null)
const templateItems = ref([])
const rows = ref([])
const warehouses = ref([])
const defaultScrapId = ref(null)
const remark = ref('')

const totalQty = computed(() => sum(rows.value, 'quantity'))
const totalQualified = computed(() => sum(rows.value, 'qualifiedQuantity'))
const totalRejected = computed(() => sum(rows.value, 'rejectedQuantity'))

function sum (list, key) {
  return (list || []).reduce((acc, r) => acc + (Number(r[key]) || 0), 0)
}
function num (v) { return v == null || v === '' ? 0 : Number(v) }
function fieldLabel (f) {
  return f.isRequired === 1 ? `${f.itemName} *` : f.itemName
}

function onQualifiedChange (row, v) {
  const q = num(v)
  row.qualifiedQuantity = q
  row.rejectedQuantity = Math.max(0, Number((num(row.quantity) - q).toFixed(4)))
  if (row.rejectedQuantity > 0 && !row.handlingType) row.handlingType = 'RELEASE'
}
function onRejectedChange (row, v) {
  const r = num(v)
  row.rejectedQuantity = r
  row.qualifiedQuantity = Math.max(0, Number((num(row.quantity) - r).toFixed(4)))
  if (r <= 0) row.handlingType = 'RELEASE'
}

async function loadWarehouses () {
  try {
    const list = await listAllWarehouse({ status: '启用' })
    const arr = Array.isArray(list) ? list : (list?.records || [])
    warehouses.value = arr.filter(w => w && w.stockId != null)
  } catch (e) { warehouses.value = [] }
}

async function loadTemplate () {
  templateItems.value = []
  if (!templateId.value) return
  try {
    const data = await getQcTemplate(templateId.value)
    templateItems.value = (data?.items || []).filter(f => f && f.itemName)
    // 新模板：为每行补齐自定义字段容器
    rows.value.forEach(r => {
      if (!r.extData) r.extData = {}
      templateItems.value.forEach(f => {
        if (r.extData[f.itemName] == null) r.extData[f.itemName] = ''
      })
    })
  } catch (e) {
    templateItems.value = []
  }
}

function onTemplateChange () {
  loadTemplate()
}

function buildRows (billItems, savedItems) {
  const savedByLine = {}
  ;(savedItems || []).forEach(it => { savedByLine[it.lineNo] = it })
  rows.value = (billItems || []).map(it => {
    const saved = savedByLine[it.lineNo] || {}
    let ext = {}
    if (saved.extData) {
      try { ext = JSON.parse(saved.extData) } catch (e) { ext = {} }
    }
    const quantity = num(it.quantity)
    const qualified = saved.qualifiedQuantity != null ? num(saved.qualifiedQuantity) : quantity
    const rejected = saved.rejectedQuantity != null ? num(saved.rejectedQuantity) : Math.max(0, quantity - qualified)
    return {
      lineNo: it.lineNo,
      sourceItemId: it.sourceItemId,
      materialId: it.materialId,
      materialCode: it.materialCode,
      materialName: it.materialName,
      specification: it.specification,
      unitId: it.unitId,
      quantity,
      qualifiedQuantity: qualified,
      rejectedQuantity: rejected,
      handlingType: saved.handlingType || 'RELEASE',
      scrapWarehouseId: saved.scrapWarehouseId || null,
      rejectReason: saved.remark || '',
      extData: ext
    }
  })
}

async function load () {
  if (!props.billType || !props.billId) return
  loading.value = true
  try {
    await loadWarehouses()
    const data = await getInboundQcContext({ billType: props.billType, billId: props.billId })
    templates.value = data?.templates || []
    defaultScrapId.value = data?.defaultScrapWarehouseId || null
    const saved = data?.inspection
    templateId.value = saved?.templateId
      || data?.qcTemplateId
      || (templates.value.find(t => t.isDefault === 1)?.templateId)
      || templates.value[0]?.templateId
      || null
    await loadTemplate()
    buildRows(data?.billItems, saved?.items)
    // 报废仓为空时默认选系统默认报废仓
    rows.value.forEach(r => {
      if (r.handlingType === 'SCRAP' && !r.scrapWarehouseId) r.scrapWarehouseId = defaultScrapId.value
    })
    remark.value = saved?.remark || ''
  } catch (e) {
    ElMessage.error(e?.message || tt('加载质检数据失败'))
  } finally {
    loading.value = false
  }
}

function validate () {
  if (!templateId.value) return tt('请选择质检模板')
  for (const r of rows.value) {
    const q = num(r.qualifiedQuantity)
    const rejected = num(r.rejectedQuantity)
    const qty = num(r.quantity)
    if (Math.abs(q + rejected - qty) > 0.0001) {
      return `第${r.lineNo}行${r.materialName || ''}合格数量加不合格数量必须等于送检数量`
    }
    if (rejected > 0 && !r.handlingType) {
      return `第${r.lineNo}行${r.materialName || ''}有不合格数量，请选择处理方式`
    }
    if (r.handlingType === 'SCRAP' && rejected > 0 && !r.scrapWarehouseId && !defaultScrapId.value) {
      return `第${r.lineNo}行${r.materialName || ''}请选择报废仓`
    }
    if (templateItems.value.some(f => f.isRequired === 1 && !String(r.extData?.[f.itemName] ?? '').trim())) {
      const miss = templateItems.value.find(f => f.isRequired === 1 && !String(r.extData?.[f.itemName] ?? '').trim())
      return `第${r.lineNo}行质检项${miss.itemName}不能为空`
    }
  }
  return ''
}

async function handleSave () {
  const err = validate()
  if (err) { ElMessage.warning(err); return }
  const items = rows.value.map(r => ({
    sourceItemId: r.sourceItemId,
    lineNo: r.lineNo,
    materialId: r.materialId,
    materialCode: r.materialCode,
    materialName: r.materialName,
    specification: r.specification,
    unitId: r.unitId,
    quantity: r.quantity,
    qualifiedQuantity: num(r.qualifiedQuantity),
    rejectedQuantity: num(r.rejectedQuantity),
    handlingType: num(r.rejectedQuantity) > 0 ? r.handlingType : 'RELEASE',
    scrapWarehouseId: r.handlingType === 'SCRAP' ? (r.scrapWarehouseId || defaultScrapId.value) : null,
    rejectReason: r.rejectReason,
    extData: JSON.stringify(r.extData || {})
  }))
  saving.value = true
  try {
    await saveInboundQc({
      billType: props.billType,
      billId: props.billId,
      templateId: templateId.value,
      remark: remark.value,
      items
    })
    ElMessage.success(tt('质检已保存'))
    emit('saved')
    visible.value = false
  } catch (e) {
    ElMessage.error(e?.message || tt('质检保存失败'))
  } finally {
    saving.value = false
  }
}

watch(() => props.modelValue, v => { if (v) load() })
</script>

<style scoped>
.qc-inspection { min-height: 220px; }
.qc-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.qc-label { font-size: 13px; color: var(--el-text-color-regular); }
.qc-summary { margin-left: auto; font-size: 13px; color: var(--el-text-color-secondary); }
.qc-remark { margin-top: 12px; }
</style>
