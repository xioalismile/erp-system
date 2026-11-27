<template>
  <el-dialog
    v-model="visible"
    :title="$tf('物料导入')"
    width="860px"
    :close-on-click-modal="false"
    @open="resetDialog"
  >
    <div class="import-step">
      <div class="step-title">
        <el-icon><Download /></el-icon>
        <span>{{ $tt("第一步：下载导入模板") }}</span>
      </div>
      <div class="step-desc">{{ $tt("下载模板后按模板格式填写数据，支持 .xlsx / .xls 格式") }}</div>
      <div class="step-desc" style="color:#f56c6c;">{{ $tt("提示：模板中带") }} <b>*</b> {{ $tt("标记的字段为系统必填字段，请务必填写") }}</div>
      <el-button type="primary" v-permission="perm('downloadTemplate')" @click="handleDownloadTemplate" :loading="downloadingTemplate">
        <el-icon><Download /></el-icon> 下载物料导入模板
      </el-button>
    </div>

    <!-- 物料图片导入指引 -->
    <div class="import-tip-box">
      <div class="tip-title">
        <el-icon><Picture /></el-icon>
        <span>{{ $tt("物料图片导入说明") }}</span>
      </div>
      <div class="tip-content">
        <p><b>{{ $tt("方式1（推荐）：") }}</b>在Excel中选中"物料图片"单元格 → 插入 → 图片 → 此设备，选择本地图片插入</p>
        <p><b>{{ $tt("方式2：") }}</b>{{ $tt("直接填写图片URL（多张用英文逗号分隔），如：") }}<code>https://example.com/1.jpg,https://example.com/2.jpg</code></p>
        <p class="tip-warning">{{ $tt("注意：不支持本地文件路径（如 C:\\images\\1.jpg），因为服务器无法访问您的本地文件") }}</p>
      </div>
    </div>

    <el-divider />

    <div class="import-step">
      <div class="step-title">
        <el-icon><Upload /></el-icon>
        <span>{{ $tt("第二步：上传数据文件（选好即自动检查）") }}</span>
      </div>
      <div class="step-desc">{{ $tt("选择文件后系统立即检查文件格式与必填项，并列出与物料库重复的物料，再决定如何处理") }}</div>
      <el-upload
        ref="uploadRef"
        action="#"
        :auto-upload="false"
        :on-change="handleFileChange"
        :show-file-list="true"
        :limit="1"
        accept=".xlsx,.xls"
        drag
        class="import-uploader"
      >
        <el-icon class="el-icon--upload"><Upload /></el-icon>
        <div class="el-upload__text">
          将文件拖到此处，或<em>{{ $tt("点击上传") }}</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            请上传 .xlsx / .xls 格式文件，文件大小不超过 10MB
          </div>
        </template>
      </el-upload>
    </div>

    <!-- 自动检查中 -->
    <div v-if="checking" class="checking-hint">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>{{ $tt("正在检查文件，请稍候…（数据行越多耗时越长）") }}</span>
    </div>

    <!-- 预检查结果 -->
    <div v-if="checkResult" class="check-result-section">
      <el-divider />

      <!-- 解析失败 -->
      <el-alert v-if="!checkResult.parseSuccess" type="error" :closable="false" show-icon>
        <template #title>{{ $tt("文件检查未通过") }}</template>
        <div>{{ checkResult.parseError || '文件格式不正确，请使用下载的模板重新填写' }}</div>
      </el-alert>

      <template v-else>
        <div class="check-summary">
          <el-row :gutter="16">
            <el-col :span="6"><el-statistic :title="$tt('文件数据行')" :value="checkResult.totalCount || 0" /></el-col>
            <el-col :span="6"><el-statistic :title="$tt('可新增')" :value="checkResult.newCount || 0" /></el-col>
            <el-col :span="6"><el-statistic :title="$tt('已存在(冲突)')" :value="checkResult.conflictCount || 0" /></el-col>
            <el-col :span="6"><el-statistic :title="$tt('校验失败行')" :value="checkResult.errorCount || 0" /></el-col>
          </el-row>
        </div>

        <!-- 行级校验错误 -->
        <el-alert
          v-if="checkResult.errorCount > 0"
          type="error"
          :closable="false"
          class="row-error-alert"
        >
          <template #title>有 {{ checkResult.errorCount }} 行未通过校验，不会被导入</template>
          <div class="error-list">
            <div v-for="(err, idx) in (checkResult.errors || []).slice(0, 10)" :key="idx" class="error-item">
              第 {{ err.rowIndex }} 行 {{ err.fieldName ? '[' + err.fieldName + ']' : '' }}: {{ err.message }}
            </div>
            <div v-if="(checkResult.errors || []).length > 10" class="error-more">
              ... 还有 {{ checkResult.errors.length - 10 }} 条错误
            </div>
          </div>
        </el-alert>

        <!-- 存在冲突 -->
        <div v-if="checkResult.hasConflict" class="conflict-section">
          <el-alert type="warning" :closable="false" show-icon>
            <template #title>
              <span class="alert-title">发现 {{ checkResult.conflictCount }} 个物料存在重复（含文件内重复）</span>
            </template>
            <template #default>
              <div class="alert-content">
                <p>{{ $tt("系统按设置中心「货品查重字段」逐字段比对（默认 物料编码、物料名称）。请选择本次导入对已存在物料的处理方式：") }}</p>
                <ul>
                  <li><b>{{ $tt("跳过已存在") }}</b>{{ $tt("（默认）：只导入新物料，已存在的物料不做任何修改") }}</li>
                  <li><b>{{ $tt("覆盖已存在") }}</b>{{ $tt("：用 Excel 中填写的内容更新已存在物料；") }}<b>{{ $tt("物料编码保持不变") }}</b>{{ $tt("，Excel 中留空的字段保留库中原值") }}</li>
                </ul>
              </div>
            </template>
          </el-alert>

          <div class="conflict-table-title">{{ $tt("冲突明细：") }}</div>
          <el-table :data="checkResult.conflicts" border size="small" max-height="220">
            <el-table-column prop="rowIndex" :label="$tf('所在行')" width="70" align="center" />
            <el-table-column prop="matchFieldLabel" :label="$tf('字段')" width="140" align="center">
              <template #default="{ row }">
                <el-tag :type="row.matchType === '文件内重复' ? 'info' : 'warning'" size="small">
                  {{ row.matchFieldLabel ? $dt(row.matchFieldLabel) : $dt(row.matchType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="excelValue" :label="$tf('Excel值')" min-width="140">
              <template #default="{ row }"><span>{{ row.excelValue || row.excelCode || row.excelName || '' }}</span></template>
            </el-table-column>
            <el-table-column :label="$tf('库中现有')" min-width="220">
              <template #default="{ row }">
                <span v-if="row.existingMaterialId">
                  {{ row.existingCode || '' }} / {{ row.existingName || '' }}
                  <span v-if="row.existingSpec"> / {{ row.existingSpec }}</span>
                </span>
                <span v-else style="color:#909399;">{{ $tt("（文件内重复，无库中记录）") }}</span>
              </template>
            </el-table-column>
          </el-table>

          <div class="action-buttons">
            <el-radio-group v-model="onExistingConflict" size="large">
              <el-radio-button label="SKIP">
                <el-icon><Minus /></el-icon>
                跳过已存在（推荐）
              </el-radio-button>
              <el-radio-button label="OVERWRITE">
                <el-icon><RefreshRight /></el-icon>
                覆盖已存在
              </el-radio-button>
            </el-radio-group>
          </div>
        </div>

        <!-- 无冲突 -->
        <el-alert v-else type="success" :closable="false" show-icon class="no-conflict-alert">
          <template #title>
            <span>{{ $tt("全部为新物料，可直接导入") }}</span>
          </template>
        </el-alert>
      </template>
    </div>

    <!-- 导入结果 -->
    <div v-if="importResult" class="import-result-section">
      <el-divider />
      <div class="result-title">{{ $tt("导入结果") }}</div>
      <el-row :gutter="16">
        <el-col :span="6"><el-statistic :title="$tt('文件数据行')" :value="importResult.totalCount || 0" /></el-col>
        <el-col :span="6">
          <el-statistic :title="$tt('新增')" :value="importResult.successCount || 0">
            <template #suffix><el-icon color="#67C23A"><CircleCheck /></el-icon></template>
          </el-statistic>
        </el-col>
        <el-col :span="6"><el-statistic :title="$tt('覆盖')" :value="importResult.updatedCount || 0" /></el-col>
        <el-col :span="6">
          <el-statistic :title="$tt('跳过已存在')" :value="importResult.skipCount || 0" />
        </el-col>
      </el-row>
      <el-alert
        v-if="importResult.message"
        type="info"
        :closable="false"
        show-icon
        style="margin-top:12px;"
      >
        <template #title>{{ importResult.message }}</template>
      </el-alert>
      <el-alert
        v-if="importResult.failCount > 0"
        type="error"
        :closable="false"
        class="import-error-alert"
      >
        <template #title>有 {{ importResult.failCount }} 条记录导入失败</template>
        <div class="error-list">
          <div v-for="(err, idx) in (importResult.errors || []).slice(0, 10)" :key="idx" class="error-item">
            第 {{ err.rowIndex }} 行 {{ err.fieldName ? '[' + err.fieldName + ']' : '' }}: {{ err.message }}
          </div>
        </div>
      </el-alert>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button v-permission="perm('dlgClose')" @click="visible = false">{{ $tt("关闭") }}</el-button>
        <el-button
          v-if="checkResult && checkResult.parseSuccess"
          v-permission="perm('import')"
          type="primary"
          @click="confirmImport"
          :loading="importing"
          :disabled="importing"
        >
          <el-icon><Upload /></el-icon>
          {{ (checkResult.hasConflict && onExistingConflict === 'OVERWRITE') ? '覆盖并导入' : '开始导入' }}
        </el-button>
        <el-button
          v-else
          v-permission="perm('import')"
          type="primary"
          @click="preCheckFile"
          :loading="checking"
          :disabled="!selectedFile || checking"
        >
          <el-icon><Search /></el-icon>
          {{ selectedFile ? $tt("重新检查") : $tt("请先选择文件") }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Download, Upload, CircleCheck, Search, Picture, Minus, RefreshRight, Loading
} from '@element-plus/icons-vue'
import {
  downloadTemplate,
  checkMaterialImport,
  confirmMaterialImport
} from '@/utils/exportUtil'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm()


const props = defineProps({
  modelValue: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'success'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const downloadingTemplate = ref(false)
const checking = ref(false)
const importing = ref(false)
const selectedFile = ref(null)
const checkResult = ref(null)
const importResult = ref(null)
/** 已存在物料处理策略：默认 SKIP=跳过已存在 */
const onExistingConflict = ref('SKIP')
const uploadRef = ref()

function resetDialog() {
  selectedFile.value = null
  checkResult.value = null
  importResult.value = null
  onExistingConflict.value = 'SKIP'
  if (uploadRef.value) {
    uploadRef.value.clearFiles()
  }
}

async function handleDownloadTemplate() {
  downloadingTemplate.value = true
  try {
    await downloadTemplate('material')
    ElMessage.success(tt('模板下载成功'))
  } catch (err) {
    ElMessage.error(tt('模板下载失败：') + (err.message || tt('未知错误')))
  } finally {
    downloadingTemplate.value = false
  }
}

function handleFileChange(file) {
  selectedFile.value = file.raw
  checkResult.value = null
  importResult.value = null
  onExistingConflict.value = 'SKIP'
  // 【2026-09-16】选好文件即自动预检查：原来是"必须先点检查文件才看得到重复与报错"，
  // 用户容易漏点 → 以为没有校验。此处改为选择即校验，报错/重复清单立刻可见。
  if (selectedFile.value) {
    preCheckFile()
  }
}

async function preCheckFile() {
  if (!selectedFile.value) {
    ElMessage.warning(tt('请先选择文件'))
    return
  }
  checking.value = true
  try {
    const result = await checkMaterialImport(selectedFile.value)
    checkResult.value = result
    if (!result.parseSuccess) {
      ElMessage.error(result.parseError || tt('文件解析失败'))
    } else if (result.hasConflict) {
      ElMessage.warning(`${tt("发现 ")}${result.conflictCount}${tt(" 个物料存在重复（含文件内重复）")}`)
    } else {
      ElMessage.success(tt('全部为新物料，可直接导入'))
    }
  } catch (err) {
    ElMessage.error(tt('文件检查失败：') + (err.message || tt('未知错误')))
  } finally {
    checking.value = false
  }
}

async function confirmImport() {
  if (!selectedFile.value) {
    ElMessage.warning(tt('请先选择文件'))
    return
  }
  importing.value = true
  try {
    const result = await confirmMaterialImport(selectedFile.value, onExistingConflict.value)
    importResult.value = result

    if (result.failCount === 0) {
      ElMessage.success(result.message || `${tt("导入成功：新增 ")}${result.successCount || 0}${tt(" 条")}`)
      emit('success')
      if (!(result.skipCount > 0 || result.updatedCount > 0)) {
        visible.value = false
      }
    } else {
      ElMessage.warning(result.message || `${tt("导入完成：失败 ")}${result.failCount}${tt(" 条")}`)
    }
  } catch (err) {
    ElMessage.error(tt('导入失败：') + (err.message || tt('未知错误')))
  } finally {
    importing.value = false
  }
}
</script>

<style scoped lang="scss">
.import-step {
  padding: 8px 0;

  .step-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 15px;
    font-weight: 600;
    color: #303133;
    margin-bottom: 8px;

    .el-icon {
      font-size: 18px;
      color: var(--erp-primary, #409EFF);
    }
  }

  .step-desc {
    font-size: 13px;
    color: #909399;
    margin-bottom: 8px;
  }
}

.import-tip-box {
  margin-top: 12px;
  padding: 10px 12px;
  background: #f4f8ff;
  border: 1px solid #dbe9ff;
  border-radius: 4px;

  .tip-title {
    display: flex;
    align-items: center;
    gap: 6px;
    font-weight: 600;
    font-size: 13px;
    color: #303133;
  }

  .tip-content {
    margin-top: 6px;
    font-size: 12px;
    color: #606266;

    p {
      margin: 4px 0;
    }

    code {
      background: #f0f2f5;
      padding: 1px 4px;
      border-radius: 3px;
    }

    .tip-warning {
      color: #e6a23c;
    }
  }
}

.import-uploader {
  :deep(.el-upload-dragger) {
    width: 100%;
    height: 120px;
  }
}

.checking-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding: 10px 12px;
  font-size: 13px;
  color: #606266;
  background: #f4f8ff;
  border: 1px solid #dbe9ff;
  border-radius: 4px;
}

.check-result-section {
  .check-summary {
    padding: 12px;
    background: #f5f7fa;
    border-radius: 4px;
    margin-bottom: 12px;
  }

  .row-error-alert {
    margin-bottom: 12px;
  }

  .conflict-section {
    .alert-title {
      font-weight: 600;
    }

    .alert-content {
      margin-top: 8px;
      font-size: 13px;

      ul {
        margin: 8px 0 0 16px;
        padding: 0;

        li {
          margin-bottom: 4px;
        }
      }
    }

    .conflict-table-title {
      font-size: 14px;
      font-weight: 600;
      margin: 12px 0 8px;
    }

    .action-buttons {
      margin-top: 16px;
      padding: 12px;
      background: #f5f7fa;
      border-radius: 4px;
    }
  }

  .no-conflict-alert {
    margin-top: 4px;
  }
}

.import-result-section {
  .result-title {
    font-size: 15px;
    font-weight: 600;
    margin-bottom: 12px;
  }

  .import-error-alert {
    margin-top: 12px;
  }
}

.error-list {
  margin-top: 6px;
  font-size: 12px;
  max-height: 160px;
  overflow: auto;

  .error-item {
    margin-bottom: 2px;
  }

  .error-more {
    color: #909399;
  }
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
