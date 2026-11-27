<template>
  <el-dialog
    :model-value="visible"
    :title="tt('检验记录详情')"
    width="860px"
    append-to-body
    @update:model-value="v => emit('update:visible', v)"
    @open="load"
  >
    <div v-loading="loading" class="qrd-body">
      <!-- 表头信息（只读，不提供任何修改入口） -->
      <div class="qrd-head">
        <div class="qrd-item"><span class="lab">{{ tt('检验单号') }}</span>{{ record.recordNo || '' }}</div>
        <div class="qrd-item"><span class="lab">{{ tt('工单号') }}</span>{{ record.orderNo || '' }}</div>
        <div class="qrd-item"><span class="lab">{{ tt('工序') }}</span>{{ record.operationName || '' }}</div>
        <div class="qrd-item"><span class="lab">{{ tt('检验环节') }}</span>{{ record.qcStage || record.qcType || '' }}</div>
        <div class="qrd-item"><span class="lab">{{ tt('检验数量') }}</span>{{ fmtQty(record.inspectQty) }}</div>
        <div class="qrd-item"><span class="lab">{{ tt('合格数') }}</span>{{ fmtQty(record.qualifiedQty) }}</div>
        <div class="qrd-item"><span class="lab">{{ tt('不合格数') }}</span>{{ fmtQty(record.defectQty) }}</div>
        <div class="qrd-item">
          <span class="lab">{{ tt('判定结果') }}</span>
          <el-tag v-if="record.result" size="small" effect="light" :type="record.result === '不合格' ? 'danger' : 'success'">
            {{ $tf(record.result) }}
          </el-tag>
        </div>
        <div class="qrd-item"><span class="lab">{{ tt('检验人') }}</span>{{ record.inspectorName || '' }}</div>
        <div class="qrd-item"><span class="lab">{{ tt('检验时间') }}</span>{{ record.inspectTime || '' }}</div>
        <div class="qrd-item"><span class="lab">{{ tt('复核人') }}</span>{{ record.reviewerName || '' }}</div>
        <div class="qrd-item qrd-item-wide"><span class="lab">{{ tt('备注') }}</span>{{ record.remark || '' }}</div>
      </div>

      <!-- 检验项明细 -->
      <div class="qrd-section-title">{{ tt('检验项明细') }}</div>
      <el-table :data="items" border size="small" max-height="300" :empty-text="tt('该记录没有检验项明细')">
        <el-table-column type="index" :label="$tf('行号')" width="60" />
        <el-table-column prop="itemName" :label="$tf('检验项')" min-width="130" show-overflow-tooltip />
        <el-table-column prop="standardValue" :label="$tf('标准值')" width="120" show-overflow-tooltip />
        <el-table-column :label="$tf('下限/上限')" width="120" align="center">
          <template #default="{ row }">{{ row.lowerLimit ?? '' }} / {{ row.upperLimit ?? '' }}</template>
        </el-table-column>
        <el-table-column prop="measuredValue" :label="$tf('实测值')" width="110" show-overflow-tooltip />
        <el-table-column :label="$tf('判定')" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.result" size="small" effect="light" :type="row.result === '不合格' ? 'danger' : 'success'">{{ $tf(row.result) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$tf('留证')" min-width="150">
          <template #default="{ row }">
            <div class="ev-list">
              <img
                v-for="(u, i) in parseUrls(row.images)"
                :key="u + i"
                :src="u"
                class="ev-img"
                @click="previewEv(parseUrls(row.images), i)"
              />
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 签名留痕（手写签名图） -->
      <div class="qrd-section-title">{{ tt('签名留痕') }}</div>
      <div v-if="signatures.length" class="sig-list">
        <div v-for="s in signatures" :key="s.signatureId" class="sig-card">
          <div class="sig-meta">
            <span class="sig-action">{{ s.actionType }}</span>
            <span>{{ tt('签名人') }}：{{ s.signerName || '' }}</span>
            <span>{{ tt('签名时间') }}：{{ s.signTime || '' }}</span>
            <span>{{ tt('签名方式') }}：{{ s.signMethod || tt('密码') }}</span>
          </div>
          <div v-if="s.targetDesc" class="sig-desc">{{ s.targetDesc }}</div>
          <el-image
            v-if="s.signImage"
            :src="s.signImage"
            :preview-src-list="[s.signImage]"
            fit="contain"
            class="sig-img"
            preview-teleported
          />
          <div v-else class="sig-none">{{ tt('该笔签名为历史密码签名，无签名图') }}</div>
        </div>
      </div>
      <div v-else class="qrd-empty">{{ tt('暂无签名记录') }}</div>

      <!-- 更正链（只增不改） -->
      <div class="qrd-section-title">{{ tt('记录更正链') }}</div>
      <el-table :data="corrections" border size="small" max-height="220" :empty-text="tt('暂无更正记录')">
        <el-table-column prop="correctedAt" :label="$tf('更正时间')" width="170" show-overflow-tooltip />
        <el-table-column prop="targetField" :label="$tf('更正字段')" width="130" show-overflow-tooltip />
        <el-table-column prop="oldValue" :label="$tf('改前')" min-width="120" show-overflow-tooltip />
        <el-table-column prop="newValue" :label="$tf('改后')" min-width="120" show-overflow-tooltip />
        <el-table-column prop="reason" :label="$tf('原因')" min-width="160" show-overflow-tooltip />
        <el-table-column prop="correctedBy" :label="$tf('更正人')" width="110" show-overflow-tooltip />
      </el-table>
    </div>

    <el-image-viewer v-if="evViewer" :url-list="evList" :initial-index="evIndex" @close="evViewer = false" />

    <template #footer>
      <el-button @click="emit('update:visible', false)">{{ tt('关闭') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 检验记录只读详情（化妆品批记录）
 * 用法：<QcRecordDetailDialog v-model:visible="show" :record-id="id" :header="row" />
 *      或 <QcRecordDetailDialog v-model:visible="show" :batch-id="batchId" />（自动取该批次最近一条检验）
 * 口径：检验提交后不可重填；本弹窗只读展示检验项 / 实测值 / 判定 / 检验人 / 复核人 / 手写签名图 / 更正链，
 *      录错走「记录更正」（原因必填 + 手写签名），原记录永不修改。
 */
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { tt } from '@/i18n'
import { parseUrls } from '@/api/common/upload'
import { getQcRecordItems } from '@/api/qc/qcRecord'
import { listRecordCorrections, listSignatures, getBatchInspectionByBatch } from '@/api/production/batchRecord'

const props = defineProps({
  visible: { type: Boolean, default: false },
  recordId: { type: [String, Number], default: null },
  batchId: { type: [String, Number], default: null },
  header: { type: Object, default: null }
})
const emit = defineEmits(['update:visible'])

const loading = ref(false)
const record = ref({})
const items = ref([])
const signatures = ref([])
const corrections = ref([])
const evViewer = ref(false)
const evList = ref([])
const evIndex = ref(0)

watch(
  () => props.visible,
  v => {
    if (!v) return
    record.value = props.header ? { ...props.header } : {}
    items.value = []
    signatures.value = []
    corrections.value = []
  }
)

async function load () {
  let rid = props.recordId
  loading.value = true
  try {
    if (!rid && props.batchId) {
      const res = await getBatchInspectionByBatch(props.batchId)
      record.value = res?.record || {}
      items.value = res?.items || []
      rid = record.value?.recordId
    } else if (props.header && props.header.recordId === rid) {
      record.value = { ...props.header }
    }
    if (!rid) {
      items.value = props.header?.items || []
      return
    }
    const [itemRes, sigRes, corrRes] = await Promise.all([
      getQcRecordItems(rid).catch(() => []),
      listSignatures('qc_record', rid).catch(() => []),
      listRecordCorrections('qc_record', rid).catch(() => [])
    ])
    items.value = Array.isArray(itemRes) ? itemRes : (props.header?.items || [])
    signatures.value = Array.isArray(sigRes) ? sigRes : []
    corrections.value = Array.isArray(corrRes) ? corrRes : []
  } catch (e) {
    if (e?.message && !/^Request/.test(e.message)) ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function fmtQty (v) {
  if (v === null || v === undefined || v === '') return ''
  const n = Number(v)
  return Number.isFinite(n) ? String(Number(n.toFixed(4))) : String(v)
}

function previewEv (list, idx) {
  evList.value = list || []
  evIndex.value = idx
  evViewer.value = true
}
</script>

<style scoped>
.qrd-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.qrd-head {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 6px 20px;
  padding: 10px 12px;
  background: #fafbfc;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  font-size: 13px;
}
.qrd-item .lab {
  color: var(--el-text-color-secondary);
  margin-right: 8px;
}
.qrd-item-wide {
  grid-column: 1 / -1;
}
.qrd-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  margin-top: 4px;
}
.qrd-empty {
  padding: 12px 0;
  text-align: center;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.ev-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.ev-img {
  width: 40px;
  height: 40px;
  object-fit: cover;
  border-radius: 4px;
  border: 1px solid var(--el-border-color-lighter);
  cursor: zoom-in;
}
.sig-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.sig-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 8px 10px;
  background: #fff;
}
.sig-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.sig-action {
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.sig-desc {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.sig-img {
  margin-top: 6px;
  width: 260px;
  height: 110px;
  border: 1px dashed var(--el-border-color-lighter);
  border-radius: 4px;
  cursor: zoom-in;
}
.sig-none {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
</style>
