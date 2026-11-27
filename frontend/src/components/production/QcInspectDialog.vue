<template>
  <el-dialog
    :model-value="visible"
    :title="tt('批次检验')"
    width="760px"
    append-to-body
    :close-on-click-modal="false"
    @update:model-value="v => emit('update:visible', v)"
  >
    <div v-if="batchRow" class="qc-head">
      <span class="qc-item"><b>{{ tt('批号') }}</b>{{ batchRow.batchNo }}</span>
      <span class="qc-item"><b>{{ tt('物料') }}</b>{{ batchRow.materialName }}</span>
      <span class="qc-item">
        <b>{{ tt('批次状态') }}</b>
        <el-tag size="small" effect="light">{{ batchRow.qcStatus || tt('未设置') }}</el-tag>
      </span>
    </div>

    <div class="qc-toolbar">
      <el-select v-model="stage" :placeholder="tt('检验环节')" style="width: 160px" :disabled="recordId != null">
        <el-option :label="tt('来料检')" value="来料检" />
        <el-option :label="tt('成品检')" value="成品检" />
      </el-select>
      <el-button type="primary" :loading="creating" :disabled="recordId != null" @click="startInspect">
        {{ tt('开始检验') }}
      </el-button>
      <el-button v-if="recordId != null" @click="reload"> {{ tt('刷新') }} </el-button>
    </div>

    <el-table v-if="items.length" :data="items" border stripe size="small" style="width: 100%">
      <el-table-column type="index" :label="tt('序号')" width="60" />
      <el-table-column prop="itemName" :label="tt('检验项')" min-width="120" />
      <el-table-column prop="inspectMethod" :label="tt('检验方法')" width="120" show-overflow-tooltip />
      <el-table-column prop="standardValue" :label="tt('标准要求')" min-width="140" show-overflow-tooltip />
      <el-table-column :label="tt('实测值')" min-width="140">
        <template #default="{ row }">
          <el-input v-model="row.measuredValue" :placeholder="tt('请输入实测值')" clearable />
        </template>
      </el-table-column>
      <el-table-column :label="tt('单项判定')" width="120">
        <template #default="{ row }">
          <el-select v-model="row.result" :placeholder="tt('判定')" clearable>
            <el-option :label="tt('合格')" value="合格" />
            <el-option :label="tt('不合格')" value="不合格" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column :label="tt('备注')" min-width="130">
        <template #default="{ row }">
          <el-input v-model="row.remark" :placeholder="tt('备注')" clearable />
        </template>
      </el-table-column>
    </el-table>

    <div v-if="recordId != null" class="qc-result">
      <span class="qc-item"><b>{{ tt('检验结论') }}</b></span>
      <el-radio-group v-model="result">
        <el-radio label="合格" />
        <el-radio label="不合格" />
        <el-radio label="让步接收" />
      </el-radio-group>
      <el-input v-model="remark" :placeholder="tt('检验备注')" clearable style="width: 220px" />
    </div>

    <div v-if="existingResult" class="qc-done">
      {{ tt('该批次检验结论') }}：{{ existingResult }}
      <span v-if="reviewerName">；{{ tt('复核人') }}：{{ reviewerName }}</span>
    </div>

    <div v-if="!batchRow" class="empty-guide">{{ tt('请选择要检验的批次') }}</div>

    <template #footer>
      <el-button @click="emit('update:visible', false)">{{ tt('关闭') }}</el-button>
      <el-button v-if="recordId != null && !existingResult" type="primary" @click="openSign">
        {{ tt('提交并签名') }}
      </el-button>
    </template>

    <SignatureDialog
      v-model:visible="signVisible"
      action-type="检验提交"
      :target-desc="signDesc"
      @signed="doSubmit"
    />
  </el-dialog>
</template>

<script setup>
/**
 * 批次检验录入弹窗（化妆品批记录）
 * 流程：选环节 → 开始检验（按批次建检并匹配质检模板）→ 填实测值/结论 → 提交并签名。
 * 检项来源是现有质检模板（基础资料 → 质检模板），不在这里重复维护。
 */
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { tt } from '@/i18n'
import { createBatchInspection, getBatchInspection, submitBatchInspection } from '@/api/production/batchRecord'
import SignatureDialog from '@/components/SignatureDialog.vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  batchRow: { type: Object, default: null }
})
const emit = defineEmits(['update:visible', 'done'])

const stage = ref('成品检')
const creating = ref(false)
const recordId = ref(null)
const items = ref([])
const result = ref('合格')
const remark = ref('')
const existingResult = ref('')
const reviewerName = ref('')
const signVisible = ref(false)
const signDesc = ref('')

watch(
  () => props.visible,
  v => {
    if (!v) return
    recordId.value = null
    items.value = []
    result.value = '合格'
    remark.value = ''
    existingResult.value = ''
    reviewerName.value = ''
  }
)

async function startInspect() {
  if (!props.batchRow?.batchId) return
  creating.value = true
  try {
    const id = await createBatchInspection(props.batchRow.batchId, stage.value)
    recordId.value = id
    await reload()
    ElMessage.success(tt('已按模板生成检验项，请填写实测值'))
  } catch (e) {
    ElMessage.error(e?.message || tt('建检失败'))
  } finally {
    creating.value = false
  }
}

async function reload() {
  if (recordId.value == null) return
  try {
    const res = await getBatchInspection(recordId.value)
    items.value = res?.items || []
    const rec = res?.record || {}
    existingResult.value = rec.result || ''
    reviewerName.value = rec.reviewerName || ''
    if (rec.result) result.value = rec.result
  } catch (e) {
    ElMessage.error(e?.message || tt('加载检验单失败'))
  }
}

function openSign() {
  if (!result.value) {
    ElMessage.warning(tt('请选择检验结论'))
    return
  }
  signDesc.value = `${props.batchRow?.batchNo || ''}（${tt('结论')}：${result.value}）`
  signVisible.value = true
}

async function doSubmit(sign) {
  try {
    await submitBatchInspection(
      recordId.value,
      {
        result: result.value,
        remark: remark.value,
        signImage: sign?.signImage || '',
        items: items.value.map(i => ({
          itemId: i.itemId,
          measuredValue: i.measuredValue,
          result: i.result,
          remark: i.remark
        }))
      }
    )
    ElMessage.success(tt('检验已提交并签名'))
    emit('done')
    emit('update:visible', false)
  } catch (e) {
    ElMessage.error(e?.message || tt('提交失败'))
  }
}
</script>

<style scoped>
.qc-head {
  display: flex;
  gap: 24px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 12px;
  font-size: 14px;
}
.qc-toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
}
.qc-item {
  display: inline-flex;
  gap: 8px;
  align-items: center;
}
.qc-result {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-top: 16px;
  flex-wrap: wrap;
}
.qc-done {
  margin-top: 16px;
  color: var(--el-color-success);
  font-size: 14px;
}
.empty-guide {
  padding: 24px 0;
  text-align: center;
  color: var(--el-text-color-secondary);
}
</style>
