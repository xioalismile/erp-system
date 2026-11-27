<template>
  <el-dialog
    v-model="visible"
    :title="tt('批次溯源') + (currentBatchNo ? ' · ' + currentBatchNo : '')"
    width="1120px"
    top="6vh"
    append-to-body
    destroy-on-close
    class="tech-dialog batch-trace-dialog"
  >
    <div v-loading="loading" class="batch-trace">
      <!-- 溯源路径（下钻后可返回上级） -->
      <div v-if="stack.length > 1" class="trace-path">
        <el-button link type="primary" :icon="Back" @click="goBack">{{ tt('返回上级') }}</el-button>
        <span class="path-text">{{ pathText }}</span>
      </div>

      <!-- 批次概要 -->
      <el-descriptions v-if="batch" :column="3" border size="small" class="trace-summary">
        <el-descriptions-item :label="tt('批次号')">{{ text(batch.batchNo) }}</el-descriptions-item>
        <el-descriptions-item :label="tt('物料编码')">{{ text(batch.materialCode) }}</el-descriptions-item>
        <el-descriptions-item :label="tt('物料名称')">{{ text(batch.materialName) }}</el-descriptions-item>
        <el-descriptions-item :label="tt('规格型号')">{{ text(batch.specification) }}</el-descriptions-item>
        <el-descriptions-item :label="tt('单位')">{{ text(batch.unitName || batch.unit) }}</el-descriptions-item>
        <el-descriptions-item :label="tt('库存数量')">{{ num(batch.quantity) }}</el-descriptions-item>
        <el-descriptions-item :label="tt('可用数量')">{{ num(batch.availableQuantity) }}</el-descriptions-item>
        <el-descriptions-item :label="tt('生产日期')">{{ text(batch.productionDate) }}</el-descriptions-item>
        <el-descriptions-item :label="tt('有效期至')">{{ text(batch.expiryDate) }}</el-descriptions-item>
        <el-descriptions-item :label="tt('供应商')">{{ text(batch.supplierName) }}</el-descriptions-item>
        <el-descriptions-item :label="tt('批次质量状态')">{{ text(batch.qcStatus) }}</el-descriptions-item>
        <el-descriptions-item :label="tt('放行')">{{ batch.releasedBy ? text(batch.releasedBy) + ' ' + text(batch.releasedAt) : tt('未放行') }}</el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-for="(n, i) in notes"
        :key="'note-' + i"
        :title="n"
        type="info"
        :closable="false"
        show-icon
        class="trace-note"
      />

      <el-tabs v-model="tab" class="trace-tabs">
        <!-- 一、出入库单据 -->
        <el-tab-pane :label="tt('出入库单据')" name="docs">
          <div class="sub-title">{{ tt('入库单据') }}<span class="count">{{ inbound.length }}</span></div>
          <el-table :data="inbound" border size="small" class="trace-table" empty-text="">
            <el-table-column :label="tt('单据类型')" width="130">
              <template #default="{ row }">{{ text(row.docTypeName) }}</template>
            </el-table-column>
            <el-table-column :label="tt('单号')" min-width="170">
              <template #default="{ row }">
                <el-link v-if="canJump(row)" type="primary" :underline="false" @click="jump(row)">{{ text(row.docNo) }}</el-link>
                <span v-else>{{ text(row.docNo) }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="tt('单据日期')" width="115">
              <template #default="{ row }">{{ text(row.docDate) }}</template>
            </el-table-column>
            <el-table-column :label="tt('数量')" width="100" align="right">
              <template #default="{ row }">{{ num(row.quantity) }}</template>
            </el-table-column>
            <el-table-column :label="tt('仓库')" width="120">
              <template #default="{ row }">{{ text(row.warehouseName) }}</template>
            </el-table-column>
            <el-table-column :label="tt('货位')" width="100">
              <template #default="{ row }">{{ text(row.binName) }}</template>
            </el-table-column>
            <el-table-column :label="tt('供应商')" min-width="130">
              <template #default="{ row }">{{ text(row.supplierName) }}</template>
            </el-table-column>
            <el-table-column :label="tt('状态')" width="100">
              <template #default="{ row }">{{ text(row.docStatus) }}</template>
            </el-table-column>
          </el-table>
          <div v-if="!inbound.length" class="empty-guide">{{ tt('该批次暂无入库单据') }}</div>

          <div class="sub-title">{{ tt('出库单据') }}<span class="count">{{ outbound.length }}</span></div>
          <el-table :data="outbound" border size="small" class="trace-table" empty-text="">
            <el-table-column :label="tt('单据类型')" width="130">
              <template #default="{ row }">{{ text(row.docTypeName) }}</template>
            </el-table-column>
            <el-table-column :label="tt('单号')" min-width="170">
              <template #default="{ row }">
                <el-link v-if="canJump(row)" type="primary" :underline="false" @click="jump(row)">{{ text(row.docNo) }}</el-link>
                <span v-else>{{ text(row.docNo) }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="tt('单据日期')" width="115">
              <template #default="{ row }">{{ text(row.docDate) }}</template>
            </el-table-column>
            <el-table-column :label="tt('数量')" width="100" align="right">
              <template #default="{ row }">{{ num(row.quantity) }}</template>
            </el-table-column>
            <el-table-column :label="tt('仓库')" width="120">
              <template #default="{ row }">{{ text(row.warehouseName) }}</template>
            </el-table-column>
            <el-table-column :label="tt('货位')" width="100">
              <template #default="{ row }">{{ text(row.binName) }}</template>
            </el-table-column>
            <el-table-column :label="tt('客户')" min-width="130">
              <template #default="{ row }">{{ text(row.customerName) }}</template>
            </el-table-column>
            <el-table-column :label="tt('状态')" width="100">
              <template #default="{ row }">{{ text(row.docStatus) }}</template>
            </el-table-column>
          </el-table>
          <div v-if="!outbound.length" class="empty-guide">{{ tt('该批次暂无出库单据') }}</div>
        </el-tab-pane>

        <!-- 二、组成物料（多层） -->
        <el-tab-pane :label="tt('组成物料')" name="components">
          <div v-if="!components.length" class="empty-guide">{{ tt('该批次未关联生产入库记录，可能是外购原料或历史数据未记录批次') }}</div>
          <el-table v-else :data="flatComponents" row-key="__key" border size="small" class="trace-table">
            <el-table-column type="expand">
              <template #default="{ row }">
                <div class="expanded-box">
                  <div class="mini-title">{{ tt('该物料批次的相关单据') }}</div>
                  <el-table v-if="row.inbound && row.inbound.length" :data="row.inbound" border size="mini">
                    <el-table-column :label="tt('单据类型')" width="130">
                      <template #default="{ row: r }">{{ text(r.docTypeName) }}</template>
                    </el-table-column>
                    <el-table-column :label="tt('单号')" min-width="160">
                      <template #default="{ row: r }">{{ text(r.docNo) }}</template>
                    </el-table-column>
                    <el-table-column :label="tt('日期')" width="110">
                      <template #default="{ row: r }">{{ text(r.docDate) }}</template>
                    </el-table-column>
                    <el-table-column :label="tt('数量')" width="90" align="right">
                      <template #default="{ row: r }">{{ num(r.quantity) }}</template>
                    </el-table-column>
                    <el-table-column :label="tt('仓库')" width="110">
                      <template #default="{ row: r }">{{ text(r.warehouseName) }}</template>
                    </el-table-column>
                  </el-table>
                  <div v-else class="empty-guide">{{ tt('该物料批次暂无入库单据') }}</div>
                </div>
              </template>
            </el-table-column>
            <el-table-column :label="tt('层级')" width="70" align="center">
              <template #default="{ row }">
                <span :style="{ paddingLeft: (row.level - 1) * 10 + 'px' }">{{ row.level }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="tt('物料编码')" width="120">
              <template #default="{ row }">{{ text(row.materialCode) }}</template>
            </el-table-column>
            <el-table-column :label="tt('物料名称')" min-width="150">
              <template #default="{ row }">{{ text(row.materialName) }}</template>
            </el-table-column>
            <el-table-column :label="tt('规格型号')" width="120">
              <template #default="{ row }">{{ text(row.specification) }}</template>
            </el-table-column>
            <el-table-column :label="tt('用量')" width="90" align="right">
              <template #default="{ row }">{{ num(row.quantity) }}</template>
            </el-table-column>
            <el-table-column :label="tt('批次号')" width="150">
              <template #default="{ row }">
                <el-link v-if="row.batchNo" type="primary" :underline="false" @click="drill(row)">{{ text(row.batchNo) }}</el-link>
                <span v-else class="muted">{{ tt('未记录批次') }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="tt('供应商')" min-width="120">
              <template #default="{ row }">{{ text(row.batch && row.batch.supplierName) }}</template>
            </el-table-column>
            <el-table-column :label="tt('生产日期')" width="110">
              <template #default="{ row }">{{ text(row.batch && row.batch.productionDate) }}</template>
            </el-table-column>
            <el-table-column :label="tt('有效期至')" width="110">
              <template #default="{ row }">{{ text(row.batch && row.batch.expiryDate) }}</template>
            </el-table-column>
            <el-table-column :label="tt('来源领料单')" width="150">
              <template #default="{ row }">{{ text(row.requisitionNo) }}</template>
            </el-table-column>
            <el-table-column :label="tt('生产工单')" width="140">
              <template #default="{ row }">{{ text(row.workOrderNo) }}</template>
            </el-table-column>
            <el-table-column :label="tt('说明')" min-width="140">
              <template #default="{ row }">{{ text(row.note) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 三、下游去向 -->
        <el-tab-pane :label="tt('下游去向')" name="downstream">
          <div class="sub-title">{{ tt('被以下成品批使用') }}<span class="count">{{ (downstream.products || []).length }}</span></div>
          <el-table v-if="(downstream.products || []).length" :data="downstream.products" border size="small" class="trace-table">
            <el-table-column :label="tt('成品批号')" width="160">
              <template #default="{ row }">
                <el-link v-if="row.batchNo" type="primary" :underline="false" @click="drillProduct(row)">{{ text(row.batchNo) }}</el-link>
                <span v-else class="muted">{{ tt('未记录批次') }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="tt('成品编码')" width="120">
              <template #default="{ row }">{{ text(row.materialCode) }}</template>
            </el-table-column>
            <el-table-column :label="tt('成品名称')" min-width="160">
              <template #default="{ row }">{{ text(row.materialName) }}</template>
            </el-table-column>
            <el-table-column :label="tt('生产入库单')" width="160">
              <template #default="{ row }">{{ text(row.receiptNo) }}</template>
            </el-table-column>
            <el-table-column :label="tt('生产工单')" width="150">
              <template #default="{ row }">{{ text(row.workOrderNo) }}</template>
            </el-table-column>
            <el-table-column :label="tt('入库日期')" width="110">
              <template #default="{ row }">{{ text(row.receiptDate) }}</template>
            </el-table-column>
          </el-table>
          <div v-else class="empty-guide">{{ tt('该批次暂未被生产领用') }}</div>

          <div class="sub-title">{{ tt('销售出库去向') }}<span class="count">{{ (downstream.deliveries || []).length }}</span></div>
          <el-table v-if="(downstream.deliveries || []).length" :data="downstream.deliveries" border size="small" class="trace-table">
            <el-table-column :label="tt('出库单号')" min-width="170">
              <template #default="{ row }">{{ text(row.docNo) }}</template>
            </el-table-column>
            <el-table-column :label="tt('出库日期')" width="115">
              <template #default="{ row }">{{ text(row.docDate) }}</template>
            </el-table-column>
            <el-table-column :label="tt('客户')" min-width="180">
              <template #default="{ row }">{{ text(row.customerName) }}</template>
            </el-table-column>
            <el-table-column :label="tt('数量')" width="100" align="right">
              <template #default="{ row }">{{ num(row.quantity) }}</template>
            </el-table-column>
          </el-table>
          <div v-else class="empty-guide">{{ tt('该批次暂无销售出库记录') }}</div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <template #footer>
      <el-button @click="visible = false">{{ tt('关闭') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Back } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getBatchTrace } from '@/api/inventory/inventory'
import { canJumpBill, resolveBillRoute } from '@/utils/billRoute'
import { tt } from '@/i18n'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  batchNo: { type: String, default: '' },
  materialId: { type: [Number, String], default: null }
})
const emit = defineEmits(['update:modelValue'])

const router = useRouter()
const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const loading = ref(false)
const tab = ref('docs')
const batch = ref({})
const inbound = ref([])
const outbound = ref([])
const components = ref([])
const downstream = ref({ requisitions: [], products: [], deliveries: [] })
const notes = ref([])
const stack = ref([])

const currentBatchNo = computed(() => stack.value.length ? stack.value[stack.value.length - 1].batchNo : '')
const pathText = computed(() => stack.value.map(s => s.batchNo).join(' → '))

const flatComponents = computed(() => {
  const out = []
  let seq = 0
  const walk = (list, level) => {
    (list || []).forEach(c => {
      const row = { ...c, level, __key: (++seq) + '-' + (c.materialId || '') + '-' + (c.batchNo || '') }
      out.push(row)
      if (c.components && c.components.length) walk(c.components, level + 1)
    })
  }
  walk(components.value, 1)
  return out
})

watch(() => props.modelValue, (v) => {
  if (v && props.batchNo && props.materialId != null) {
    stack.value = [{ batchNo: props.batchNo, materialId: props.materialId }]
    tab.value = 'docs'
    load()
  }
})

async function load() {
  if (!stack.value.length) return
  const cur = stack.value[stack.value.length - 1]
  loading.value = true
  try {
    const res = await getBatchTrace({ batchNo: cur.batchNo, materialId: cur.materialId, depth: 5 })
    const d = res?.data || res || {}
    batch.value = d.batch || {}
    inbound.value = d.inbound || []
    outbound.value = d.outbound || []
    components.value = d.components || []
    downstream.value = d.downstream || { requisitions: [], products: [], deliveries: [] }
    notes.value = d.notes || []
  } catch (e) {
    ElMessage.error(e?.message || tt('批次溯源查询失败'))
    batch.value = {}
    inbound.value = []
    outbound.value = []
    components.value = []
    downstream.value = { requisitions: [], products: [], deliveries: [] }
    notes.value = []
  } finally {
    loading.value = false
  }
}

function drill(row) {
  if (!row || !row.batchNo || row.materialId == null) return
  stack.value = [...stack.value, { batchNo: row.batchNo, materialId: row.materialId }]
  tab.value = 'docs'
  load()
}

function drillProduct(row) {
  drill({ batchNo: row.batchNo, materialId: row.materialId })
}

function goBack() {
  if (stack.value.length <= 1) return
  stack.value = stack.value.slice(0, -1)
  tab.value = 'docs'
  load()
}

function canJump(row) {
  return row && row.docNo && canJumpBill(row.docType) && row.billId != null
}

function jump(row) {
  const path = resolveBillRoute(row.docType, row.billId)
  if (!path) return
  visible.value = false
  router.push(path)
}

function text(v) {
  if (v === null || v === undefined) return ''
  return String(v)
}

function num(v) {
  if (v === null || v === undefined || v === '') return ''
  const n = Number(v)
  return Number.isFinite(n) ? n.toFixed(4) : String(v)
}
</script>

<style scoped>
.batch-trace { min-height: 240px; }
.trace-path { margin-bottom: 8px; display: flex; align-items: center; gap: 8px; }
.path-text { color: #606266; font-size: 12px; }
.trace-summary { margin-bottom: 10px; }
.trace-note { margin-bottom: 8px; }
.trace-tabs { margin-top: 6px; }
.sub-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin: 10px 0 6px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.sub-title .count {
  color: #909399;
  font-weight: 400;
  font-size: 12px;
}
.trace-table { width: 100%; }
.empty-guide {
  padding: 14px 12px;
  color: #909399;
  font-size: 12px;
  background: #f7f8fa;
  border-radius: 4px;
  margin: 6px 0;
}
.expanded-box { padding: 6px 12px 10px; background: #fafbfc; }
.mini-title { font-size: 12px; font-weight: 600; color: #606266; margin-bottom: 6px; }
.muted { color: #909399; }
</style>
