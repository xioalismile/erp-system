<template>
  <div class="erp-page trace-page">
    <el-card shadow="never" class="erp-card">
      <div class="page-header">
        <div class="header-left">
          <el-button @click="goBack">{{ tt('返回') }}</el-button>
          <span class="page-title">{{ tt('批次追溯') }}</span>
          <el-tag v-if="batch.batchNo" effect="plain">{{ batch.batchNo }}</el-tag>
          <el-tag v-if="batch.qcStatus" :type="statusTagType(batch.qcStatus)" effect="light">{{ batch.qcStatus }}</el-tag>
        </div>
        <div class="header-actions">
          <el-radio-group v-model="direction" size="small" @change="load">
            <el-radio-button label="both">{{ tt('全链路') }}</el-radio-button>
            <el-radio-button label="up">{{ tt('只看上游') }}</el-radio-button>
            <el-radio-button label="down">{{ tt('只看下游') }}</el-radio-button>
          </el-radio-group>
          <el-button @click="exportReport">{{ tt('导出追溯报告') }}</el-button>
        </div>
      </div>

      <div v-loading="loading" class="trace-body">
        <div class="batch-summary">
          <div class="sum-item"><span class="sum-label">{{ tt('物料') }}</span>{{ batch.materialName || '' }}</div>
          <div class="sum-item"><span class="sum-label">{{ tt('物料编码') }}</span>{{ batch.materialCode || '' }}</div>
          <div class="sum-item"><span class="sum-label">{{ tt('规格型号') }}</span>{{ batch.specification || '' }}</div>
          <div class="sum-item"><span class="sum-label">{{ tt('数量') }}</span>{{ batch.quantity ?? '' }}</div>
          <div class="sum-item"><span class="sum-label">{{ tt('生产日期') }}</span>{{ batch.productionDate || '' }}</div>
          <div class="sum-item"><span class="sum-label">{{ tt('失效日期') }}</span>{{ batch.expiryDate || '' }}</div>
          <div class="sum-item"><span class="sum-label">{{ tt('供应商') }}</span>{{ batch.supplierName || '' }}</div>
          <div class="sum-item"><span class="sum-label">{{ tt('放行') }}</span>{{ batch.releasedBy ? batch.releasedBy + ' ' + (batch.releasedAt || '') : tt('未放行') }}</div>
        </div>

        <!-- 上游 -->
        <div v-if="showUp" class="trace-section">
          <div class="section-title"><span class="dot up"></span>{{ tt('上游：原料批次') }}</div>
          <div v-if="traceNote(upstream) && !upstream.materials?.length" class="empty-guide">{{ traceNote(upstream) }}</div>
          <el-table v-if="upstream.materials?.length" :data="upstream.materials" border stripe size="small">
            <el-table-column prop="requisitionNo" :label="tt('领料单号')" width="170" show-overflow-tooltip />
            <el-table-column prop="materialCode" :label="tt('原料编码')" width="130" show-overflow-tooltip />
            <el-table-column prop="materialName" :label="tt('原料名称')" min-width="150" show-overflow-tooltip />
            <el-table-column prop="batchNo" :label="tt('原料批号')" width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.batchNo || tt('历史数据未记录批次') }}</template>
            </el-table-column>
            <el-table-column prop="supplierBatchNo" :label="tt('供应商批号')" width="150" show-overflow-tooltip />
            <el-table-column prop="supplierName" :label="tt('供应商')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="quantity" :label="tt('数量')" width="100" align="right" />
            <el-table-column :label="tt('质量状态')" width="110">
              <template #default="{ row }">
                <el-tag v-if="row.qcStatus" size="small" effect="light">{{ row.qcStatus }}</el-tag>
                <span v-else class="muted">{{ '' }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 本批 -->
        <div v-if="showUp" class="trace-section">
          <div class="section-title"><span class="dot self"></span>{{ tt('本批：生产与质量') }}</div>
          <div class="self-grid">
            <div class="self-block">
              <div class="block-title">{{ tt('生产工单') }}</div>
              <div v-if="selfData.batchRecord?.workOrderNo" class="block-line">
                {{ selfData.batchRecord.workOrderNo }} · {{ selfData.batchRecord.productName || '' }}
              </div>
              <div v-else class="empty-guide">{{ traceNote(selfData) }}</div>
            </div>
            <div class="self-block">
              <div class="block-title">{{ tt('检验记录') }}</div>
              <div v-if="selfData.qcRecords?.length">
                <div v-for="(q, idx) in selfData.qcRecords" :key="idx" class="block-line">
                  {{ q.record?.recordNo }} · {{ q.record?.qcStage || q.record?.qcType }} · {{ q.record?.result || tt('待检验') }}
                  <span v-if="q.record?.reviewerName"> · {{ tt('复核') }}：{{ q.record.reviewerName }}</span>
                </div>
              </div>
              <div v-else class="empty-guide">{{ tt('暂无检验记录') }}</div>
            </div>
            <div class="self-block">
              <div class="block-title">{{ tt('放行记录') }}</div>
              <div v-if="selfData.releaseOrders?.length">
                <div v-for="r in selfData.releaseOrders" :key="r.releaseId" class="block-line">
                  {{ r.releaseNo }} · {{ r.status }}<span v-if="r.approvedBy"> · {{ r.approvedBy }}</span>
                </div>
              </div>
              <div v-else class="empty-guide">{{ tt('暂无放行记录') }}</div>
            </div>
          </div>
        </div>

        <!-- 下游 -->
        <div v-if="showDown" class="trace-section">
          <div class="section-title"><span class="dot down"></span>{{ tt('下游：出货去向') }}</div>
          <div v-if="downstream.deliveries?.length">
            <el-table :data="downstream.deliveries" border stripe size="small">
              <el-table-column prop="deliveryNo" :label="tt('出库单号')" width="180" show-overflow-tooltip />
              <el-table-column prop="deliveryDate" :label="tt('出库日期')" width="130" />
              <el-table-column prop="customerName" :label="tt('客户')" min-width="180" show-overflow-tooltip />
              <el-table-column prop="quantity" :label="tt('数量')" width="110" align="right" />
              <el-table-column prop="status" :label="tt('状态')" width="110" />
            </el-table>
          </div>
          <div v-else class="empty-guide">{{ tt('该批次暂无出库记录') }}</div>

          <div v-if="downstream.usedByProducts?.length" class="used-block">
            <div class="block-title">{{ tt('作为原料被以下成品批使用') }}</div>
            <el-table :data="downstream.usedByProducts" border stripe size="small">
              <el-table-column prop="batchNo" :label="tt('成品批号')" width="180" show-overflow-tooltip />
              <el-table-column prop="orderNo" :label="tt('生产工单')" min-width="180" show-overflow-tooltip />
              <el-table-column prop="receiptDate" :label="tt('入库日期')" width="130" />
            </el-table>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
/**
 * 批次追溯树（化妆品批记录）
 * 正查：成品批 → 用了哪些原料批 / 卖给了谁；反查：原料批 → 被哪些成品批使用（召回）。
 * 全部由现有单据实时推导；历史单据无批次时显示「历史数据未记录批次」，不报错。
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { tt } from '@/i18n'
import { getBatchTrace } from '@/api/production/batchRecord'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const direction = ref('both')
const batch = ref({})
const upstream = ref({})
const selfData = ref({})
const downstream = ref({})

const showUp = computed(() => direction.value === 'both' || direction.value === 'up')
const showDown = computed(() => direction.value === 'both' || direction.value === 'down')

function statusTagType(status) {
  if (status === '已放行') return 'success'
  if (status === '待检') return 'warning'
  if (status === '不合格') return 'danger'
  if (status === '已冻结') return 'info'
  return 'primary'
}

function traceNote(obj) {
  return obj?.note || ''
}

async function load() {
  const batchId = route.params.batchId
  if (!batchId) return
  loading.value = true
  try {
    const res = await getBatchTrace(batchId, direction.value)
    batch.value = res?.batch || {}
    upstream.value = res?.upstream || {}
    selfData.value = res?.self || {}
    downstream.value = res?.downstream || {}
  } catch (e) {
    ElMessage.error(e?.message || tt('追溯查询失败'))
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/production/batch-record-center')
}

/** 导出追溯报告：按当前页面可见内容导出为 CSV（页面看到什么就导什么） */
function exportReport() {
  const lines = []
  lines.push([tt('批次追溯报告')].join(','))
  lines.push([tt('批号'), batch.value.batchNo || ''].join(','))
  lines.push([tt('物料'), batch.value.materialName || ''].join(','))
  lines.push([tt('物料编码'), batch.value.materialCode || ''].join(','))
  lines.push([tt('批次状态'), batch.value.qcStatus || ''].join(','))
  lines.push('')
  lines.push([tt('上游原料')].join(','))
  lines.push([tt('领料单号'), tt('原料编码'), tt('原料名称'), tt('原料批号'), tt('供应商批号'), tt('供应商'), tt('数量')].join(','))
  ;(upstream.value.materials || []).forEach(m => {
    lines.push([m.requisitionNo, m.materialCode, m.materialName, m.batchNo || '', m.supplierBatchNo || '', m.supplierName || '', m.quantity ?? ''].join(','))
  })
  lines.push('')
  lines.push([tt('下游出库')].join(','))
  lines.push([tt('出库单号'), tt('出库日期'), tt('客户'), tt('数量')].join(','))
  ;(downstream.value.deliveries || []).forEach(d => {
    lines.push([d.deliveryNo, d.deliveryDate, d.customerName || '', d.quantity ?? ''].join(','))
  })
  const csv = '\uFEFF' + lines.join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${tt('批次追溯')}-${batch.value.batchNo || ''}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

onMounted(load)
</script>

<style scoped>
.trace-page .page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  gap: 12px;
  flex-wrap: wrap;
}
.trace-page .header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.trace-page .header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.page-title {
  font-size: 16px;
  font-weight: 600;
}
.batch-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 28px;
  padding: 12px;
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
  margin-bottom: 16px;
  font-size: 14px;
}
.sum-label {
  color: var(--el-text-color-secondary);
  margin-right: 6px;
}
.trace-section {
  margin-bottom: 20px;
}
.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  margin-bottom: 10px;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}
.dot.up {
  background: var(--el-color-warning);
}
.dot.self {
  background: var(--el-color-primary);
}
.dot.down {
  background: var(--el-color-success);
}
.self-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 12px;
}
.self-block {
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  padding: 12px;
}
.block-title {
  font-weight: 600;
  font-size: 13px;
  margin-bottom: 8px;
}
.block-line {
  font-size: 13px;
  line-height: 1.9;
}
.used-block {
  margin-top: 16px;
}
.empty-guide {
  padding: 16px 0;
  color: var(--el-text-color-secondary);
  text-align: center;
  font-size: 13px;
}
.muted {
  color: var(--el-text-color-placeholder);
}
</style>
