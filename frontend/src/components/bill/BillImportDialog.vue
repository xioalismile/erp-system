<template>
  <!-- 单据导入通用弹窗（2026-11-07）：与销售订单同款两步式，全站单据共用一份 -->
  <el-dialog
    v-model="visible"
    :title="$tf(title || '导入')"
    width="560px"
    :close-on-click-modal="false"
    append-to-body
  >
    <div class="bill-import-body">
      <div class="import-step">
        <div class="step-title">第一步：下载导入模板</div>
        <div class="step-desc">下载模板后按模板格式填写，支持 .xlsx / .xls 格式</div>
        <el-button type="primary" :loading="downloading" @click="onDownload">
          {{ $tt("下载导入模板") }}
        </el-button>
      </div>
      <el-divider />
      <div class="import-step">
        <div class="step-title">第二步：上传数据文件</div>
        <el-upload
          :auto-upload="false"
          :on-change="onFileChange"
          :show-file-list="true"
          accept=".xlsx,.xls,.csv"
          drag
          class="import-uploader"
        >
          <div class="el-upload__text">{{ $tt("将文件拖到此处，或") }}<em>{{ $tt("点击上传") }}</em></div>
          <template #tip>
            <div class="el-upload__tip">{{ $tt("文件大小不超过 10MB") }}</div>
          </template>
        </el-upload>
      </div>
    </div>
    <template #footer>
      <el-button @click="visible = false">{{ $tt("关闭") }}</el-button>
      <el-button type="primary" :loading="importing" :disabled="!file" @click="onImport">
        {{ $tt("开始导入") }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { downloadTemplate, importData } from '@/utils/exportUtil'
import { tt } from '@/i18n'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 后端 BillExcelRegistry 登记的实体名，如 workOrder / productionReceipt */
  entityName: { type: String, required: true },
  title: { type: String, default: '' },
  /** 共用同一张表的单据类型：生产退料 RETURN / 补料 SUPPLEMENT；其它单据留空 */
  billType: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'done'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})
const file = ref(null)
const downloading = ref(false)
const importing = ref(false)

function onFileChange(uploadFile) {
  file.value = uploadFile && uploadFile.raw ? uploadFile.raw : null
}

async function onDownload() {
  downloading.value = true
  try {
    await downloadTemplate(props.entityName, undefined, props.billType)
  } catch (_) {
    // downloadTemplate 内部已提示
  } finally {
    downloading.value = false
  }
}

async function onImport() {
  if (!file.value) {
    ElMessage.warning(tt('请先选择要导入的文件'))
    return
  }
  importing.value = true
  try {
    const res = await importData(props.entityName, file.value, undefined, props.billType)
    const d = res && res.data ? res.data : (res || {})
    const success = d.successCount ?? d.success ?? d.importedCount ?? 0
    const failed = d.failCount ?? d.failed ?? d.errorCount ?? 0
    ElMessage.success(`${tt('导入完成')}：${tt('成功')} ${success}，${tt('失败')} ${failed}`)
    emit('done')
    visible.value = false
  } catch (e) {
    ElMessage.error(e && e.message ? e.message : tt('导入失败'))
  } finally {
    importing.value = false
  }
}
</script>

<style scoped>
.bill-import-body { padding: 4px 2px; }
.import-step { padding: 4px 0; }
.step-title { font-size: 14px; font-weight: 600; color: #303133; margin-bottom: 6px; }
.step-desc { font-size: 12px; color: #909399; margin-bottom: 10px; }
.import-uploader { width: 100%; }
</style>
