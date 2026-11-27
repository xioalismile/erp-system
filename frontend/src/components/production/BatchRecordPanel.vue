<template>
  <div v-loading="loading" class="brp-wrap">
    <!-- 基本信息 -->
    <el-card shadow="never" class="brp-card">
      <div class="brp-title">{{ tt('基本信息') }}</div>
      <div class="brp-grid">
        <div class="brp-item"><span class="lab">{{ tt('工单号') }}</span>{{ workOrder.orderNo || '' }}</div>
        <div class="brp-item"><span class="lab">{{ tt('产品') }}</span>{{ workOrder.materialName || '' }}</div>
        <div class="brp-item"><span class="lab">{{ tt('产品编码') }}</span>{{ workOrder.materialCode || '' }}</div>
        <div class="brp-item"><span class="lab">{{ tt('批号') }}</span>{{ header.batchNo || '' }}</div>
        <div class="brp-item"><span class="lab">{{ tt('工单日期') }}</span>{{ workOrder.orderDate || '' }}</div>
        <div class="brp-item"><span class="lab">{{ tt('生产数量') }}</span>{{ workOrder.quantity ?? '' }}</div>
        <div class="brp-item"><span class="lab">{{ tt('配方版本') }}</span>{{ bomVersion }}</div>
        <div class="brp-item"><span class="lab">{{ tt('工艺路线') }}</span>{{ routingVersion }}</div>
      </div>
    </el-card>

    <!-- 投料记录 -->
    <el-card shadow="never" class="brp-card">
      <div class="brp-title">{{ tt('投料记录') }}</div>
      <el-table v-if="materials.length" :data="materials" border stripe size="small">
        <el-table-column prop="requisitionNo" :label="tt('领料单号')" width="170" show-overflow-tooltip />
        <el-table-column prop="materialCode" :label="tt('原料编码')" width="130" show-overflow-tooltip />
        <el-table-column prop="materialName" :label="tt('原料名称')" min-width="150" show-overflow-tooltip />
        <el-table-column prop="batchNo" :label="tt('批号')" width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.batchNo || tt('历史数据未记录批次') }}</template>
        </el-table-column>
        <el-table-column prop="quantity" :label="tt('数量')" width="110" align="right" />
      </el-table>
      <div v-else class="brp-empty">{{ tt('暂无投料记录') }}</div>
    </el-card>

    <!-- 工序记录 -->
    <el-card shadow="never" class="brp-card">
      <div class="brp-title">{{ tt('工序记录') }}</div>
      <el-table v-if="operations.length" :data="operations" border stripe size="small" max-height="360">
        <el-table-column prop="operateTime" :label="tt('操作时间')" width="170" show-overflow-tooltip />
        <el-table-column prop="operationName" :label="tt('工序')" width="140" show-overflow-tooltip />
        <el-table-column prop="actionType" :label="tt('动作')" width="120" />
        <el-table-column prop="fromStatus" :label="tt('原状态')" width="120" />
        <el-table-column prop="toStatus" :label="tt('新状态')" width="120" />
        <el-table-column prop="quantity" :label="tt('数量')" width="100" align="right" />
        <el-table-column prop="operatorName" :label="tt('操作人')" width="120" />
        <el-table-column prop="remark" :label="tt('备注')" min-width="160" show-overflow-tooltip />
      </el-table>
      <div v-else class="brp-empty">{{ tt('暂无工序操作记录') }}</div>
    </el-card>

    <!-- 检验记录 -->
    <el-card shadow="never" class="brp-card">
      <div class="brp-title">{{ tt('检验记录') }}</div>
      <div v-if="qcRecords.length">
        <div v-for="(q, idx) in qcRecords" :key="idx" class="qc-block">
          <div class="qc-head-line">
            <span>{{ q.record?.recordNo }}</span>
            <span>{{ q.record?.qcStage || q.record?.qcType }}</span>
            <el-tag size="small" :type="q.record?.result === '合格' ? 'success' : (q.record?.result === '不合格' ? 'danger' : 'info')">
              {{ q.record?.result || tt('待检验') }}
            </el-tag>
            <span v-if="q.record?.inspectorName" class="muted">{{ tt('检验人') }}：{{ q.record.inspectorName }}</span>
            <span v-if="q.record?.reviewerName" class="muted">{{ tt('复核人') }}：{{ q.record.reviewerName }}</span>
            <el-button link type="primary" size="small" @click="openQcDetail(q)">{{ tt('查看详情') }}</el-button>
          </div>
          <el-table v-if="q.items?.length" :data="q.items" border size="small" style="margin-top: 6px">
            <el-table-column prop="itemName" :label="tt('检验项')" width="140" />
            <el-table-column prop="standardValue" :label="tt('标准要求')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="measuredValue" :label="tt('实测值')" width="130" />
            <el-table-column prop="result" :label="tt('单项判定')" width="110" />
          </el-table>
        </div>
      </div>
      <div v-else class="brp-empty">{{ tt('暂无检验记录') }}</div>
    </el-card>

    <!-- 环境记录 -->
    <el-card shadow="never" class="brp-card">
      <div class="brp-title">{{ tt('环境记录') }}</div>
      <div class="env-row">
        <span class="lab">{{ tt('温度') }}</span>
        <el-input v-model="env.temperature" :placeholder="tt('温度')" clearable style="width: 120px" />
        <span class="unit">℃</span>
        <span class="lab">{{ tt('湿度') }}</span>
        <el-input v-model="env.humidity" :placeholder="tt('湿度')" clearable style="width: 120px" />
        <span class="unit">%</span>
        <el-button type="primary" :loading="savingEnv" @click="saveEnv">{{ tt('保存环境记录') }}</el-button>
      </div>
      <div v-if="header.envRecordTime" class="muted env-meta">
        {{ tt('记录人') }}：{{ header.envRecorder || '' }} · {{ header.envRecordTime }}
      </div>
    </el-card>

    <!-- 记录更正 -->
    <el-card shadow="never" class="brp-card">
      <div class="brp-title">
        {{ tt('记录更正') }}
        <el-button link type="primary" @click="openCorrection">{{ tt('新增更正') }}</el-button>
      </div>
      <el-table v-if="corrections.length" :data="corrections" border stripe size="small">
        <el-table-column prop="correctedAt" :label="tt('更正时间')" width="180" show-overflow-tooltip />
        <el-table-column prop="targetTable" :label="tt('记录类型')" width="140" />
        <el-table-column prop="targetField" :label="tt('更正字段')" width="140" />
        <el-table-column prop="oldValue" :label="tt('改前')" min-width="130" show-overflow-tooltip />
        <el-table-column prop="newValue" :label="tt('改后')" min-width="130" show-overflow-tooltip />
        <el-table-column prop="reason" :label="tt('原因')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="correctedBy" :label="tt('更正人')" width="120" />
      </el-table>
      <div v-else class="brp-empty">{{ tt('暂无更正记录') }}</div>
    </el-card>

    <!-- 签名留痕 -->
    <el-card shadow="never" class="brp-card">
      <div class="brp-title">{{ tt('电子签名留痕') }}</div>
      <el-table v-if="signatures.length" :data="signatures" border stripe size="small">
        <el-table-column prop="signTime" :label="tt('签名时间')" width="180" show-overflow-tooltip />
        <el-table-column prop="actionType" :label="tt('签名动作')" width="140" />
        <el-table-column prop="signerName" :label="tt('签名人')" width="140" />
        <el-table-column prop="targetDesc" :label="tt('签名对象')" min-width="220" show-overflow-tooltip />
        <el-table-column prop="verifyResult" :label="tt('校验结果')" width="110" />
      </el-table>
      <div v-else class="brp-empty">{{ tt('暂无签名记录') }}</div>
    </el-card>

    <!-- 记录更正弹窗 -->
    <el-dialog v-model="correctionVisible" :title="tt('新增记录更正')" width="520px" append-to-body>
      <div class="corr-form">
        <div class="corr-row">
          <span class="lab">{{ tt('记录类型') }}</span>
          <el-select v-model="corr.targetTable" style="width: 220px">
            <el-option :label="tt('生产工单')" value="production_order" />
            <el-option :label="tt('检验记录')" value="qc_record" />
            <el-option :label="tt('工序操作记录')" value="work_operation_log" />
          </el-select>
        </div>
        <div class="corr-row">
          <span class="lab">{{ tt('记录编号') }}</span>
          <el-input v-model="corr.targetId" :placeholder="tt('请输入要更正的记录编号')" clearable style="width: 220px" />
        </div>
        <div class="corr-row">
          <span class="lab">{{ tt('更正字段') }}</span>
          <el-input v-model="corr.targetField" :placeholder="tt('请输入字段名')" clearable style="width: 220px" />
        </div>
        <div class="corr-row">
          <span class="lab">{{ tt('改前内容') }}</span>
          <el-input v-model="corr.oldValue" :placeholder="tt('改前内容')" clearable style="width: 220px" />
        </div>
        <div class="corr-row">
          <span class="lab">{{ tt('改后内容') }}</span>
          <el-input v-model="corr.newValue" :placeholder="tt('改后内容')" clearable style="width: 220px" />
        </div>
        <div class="corr-row">
          <span class="lab">{{ tt('更正原因') }}</span>
          <el-input v-model="corr.reason" type="textarea" :rows="2" :placeholder="tt('更正原因必填')" style="width: 300px" />
        </div>
      </div>
      <template #footer>
        <el-button @click="correctionVisible = false">{{ tt('取消') }}</el-button>
        <el-button type="primary" @click="submitCorrection">{{ tt('提交并签名') }}</el-button>
      </template>
    </el-dialog>

    <SignatureDialog
      v-model:visible="signVisible"
      action-type="记录更正"
      :target-desc="signDesc"
      @signed="doCorrection"
    />

    <!-- 检验记录只读详情（检验项 / 实测值 / 判定 / 检验人 / 复核人 / 手写签名图 / 更正链） -->
    <QcRecordDetailDialog v-model:visible="qcDetailVisible" :record-id="qcDetailId" :header="qcDetailHeader" />
  </div>
</template>

<script setup>
/**
 * 批生产记录页签（化妆品批记录）
 * 数据来源：后端聚合接口（头 + 投料 + 工序 + 检验 + 更正 + 签名）。
 * 记录只增不改：更正通过「新增更正 + 电子签名」完成，原记录永不修改。
 */
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { tt } from '@/i18n'
import { getBatchRecordByWorkOrder, saveBatchRecordEnv, addRecordCorrection } from '@/api/production/batchRecord'
import SignatureDialog from '@/components/SignatureDialog.vue'
import QcRecordDetailDialog from '@/components/production/QcRecordDetailDialog.vue'

const props = defineProps({
  workOrderId: { type: [String, Number], default: null }
})

const loading = ref(false)
const savingEnv = ref(false)
const header = ref({})
const workOrder = ref({})
const materials = ref([])
const operations = ref([])
const qcRecords = ref([])
const corrections = ref([])
const signatures = ref([])

const env = reactive({ temperature: '', humidity: '' })

const correctionVisible = ref(false)
const signVisible = ref(false)
const signDesc = ref('')
const corr = reactive({ targetTable: 'production_order', targetId: '', targetField: '', oldValue: '', newValue: '', reason: '' })

/** 检验记录只读详情弹窗 */
const qcDetailVisible = ref(false)
const qcDetailId = ref(null)
const qcDetailHeader = ref({})
function openQcDetail (q) {
  qcDetailHeader.value = q?.record || {}
  qcDetailId.value = q?.record?.recordId || null
  qcDetailVisible.value = true
}

const bomVersion = computed(() => {
  try {
    const snap = header.value?.bomSnapshot
    if (!snap) return ''
    const obj = JSON.parse(snap)
    return obj.bomVersion || obj.version || obj.bomCode || ''
  } catch (_) {
    return ''
  }
})

const routingVersion = computed(() => {
  try {
    const snap = header.value?.routingSnapshot
    if (!snap) return ''
    const obj = JSON.parse(snap)
    return obj.routing?.routingName || ''
  } catch (_) {
    return ''
  }
})

async function load() {
  if (!props.workOrderId) return
  loading.value = true
  try {
    const res = await getBatchRecordByWorkOrder(props.workOrderId)
    header.value = res?.header || {}
    workOrder.value = res?.workOrder || {}
    materials.value = res?.materials || []
    operations.value = res?.operations || []
    qcRecords.value = res?.qcRecords || []
    corrections.value = res?.corrections || []
    signatures.value = res?.signatures || []
    env.temperature = header.value?.envTemperature ?? ''
    env.humidity = header.value?.envHumidity ?? ''
  } catch (e) {
    ElMessage.error(e?.message || tt('批生产记录加载失败'))
  } finally {
    loading.value = false
  }
}

async function saveEnv() {
  savingEnv.value = true
  try {
    await saveBatchRecordEnv(props.workOrderId, {
      temperature: env.temperature === '' ? null : Number(env.temperature),
      humidity: env.humidity === '' ? null : Number(env.humidity)
    })
    ElMessage.success(tt('环境记录已保存'))
    load()
  } catch (e) {
    ElMessage.error(e?.message || tt('保存失败'))
  } finally {
    savingEnv.value = false
  }
}

function openCorrection() {
  corr.targetTable = 'production_order'
  corr.targetId = String(props.workOrderId || '')
  corr.targetField = ''
  corr.oldValue = ''
  corr.newValue = ''
  corr.reason = ''
  correctionVisible.value = true
}

function submitCorrection() {
  if (!corr.reason || !corr.reason.trim()) {
    ElMessage.warning(tt('更正必须填写原因'))
    return
  }
  if (!corr.targetId) {
    ElMessage.warning(tt('请填写记录编号'))
    return
  }
  signDesc.value = `${corr.targetTable} #${corr.targetId}（${corr.targetField || tt('字段')}）`
  correctionVisible.value = false
  signVisible.value = true
}

async function doCorrection(sign) {
  try {
    await addRecordCorrection({ ...corr, signImage: sign?.signImage || '' })
    ElMessage.success(tt('更正已提交并签名'))
    load()
  } catch (e) {
    ElMessage.error(e?.message || tt('更正失败'))
  }
}

watch(() => props.workOrderId, load)
onMounted(load)
</script>

<style scoped>
.brp-wrap {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.brp-card {
  border: 1px solid var(--el-border-color-light);
}
.brp-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 10px;
}
.brp-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 8px 24px;
  font-size: 13px;
}
.lab {
  color: var(--el-text-color-secondary);
  margin-right: 8px;
}
.brp-empty {
  padding: 16px 0;
  text-align: center;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.qc-block {
  padding: 10px 0;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}
.qc-block:last-child {
  border-bottom: none;
}
.qc-head-line {
  display: flex;
  gap: 12px;
  align-items: center;
  font-size: 13px;
  flex-wrap: wrap;
}
.env-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 13px;
}
.unit {
  color: var(--el-text-color-secondary);
}
.env-meta {
  margin-top: 8px;
  font-size: 12px;
}
.corr-form {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.corr-row {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}
.corr-row .lab {
  min-width: 72px;
}
.muted {
  color: var(--el-text-color-secondary);
}
</style>
