<template>
  <!--
    BOM导入弹窗组件 - 支持预检查缺失物料并自动创建
  -->
  <el-dialog
    v-model="visible"
    :title="$tf('BOM多级导入')"
    width="800px"
    :close-on-click-modal="false"
    @closed="resetDialog"
  >
    <!-- 步骤 1: 下载模板 -->
    <div class="import-step">
      <div class="step-title">
        <el-icon><Download /></el-icon>
        <span>{{ $tt("第一步：下载导入模板") }}</span>
      </div>
      <div class="step-desc">
        推荐使用【精简18列统一模板】：左半 9 列为 BOM 主属性（同一母件只需在第1行填写，后续行自动继承），右半 9 列为子件明细，同时兼容一级 BOM 和多级 BOM 导入。
      </div>
      <div class="step-desc" style="color:#f56c6c;">{{ $tt("提示：模板中带") }} <b>*</b> {{ $tt("标记的字段为系统必填字段（锁定不可取消，下载模板时自动带上），其余字段可按需勾选") }}</div>
      <div class="template-download-group">
        <el-button type="primary" v-permission="perm('downloadTemplate')" @click="handleDownloadUnifiedTemplate" :loading="downloadingTemplateUnified">
          <el-icon><Star /></el-icon> 下载精简18列统一模板（推荐）
        </el-button>
        <el-button v-permission="perm('downloadTemplate')" @click="handleDownloadLegacyTemplate" :loading="downloadingTemplateLegacy">
          <el-icon><Download /></el-icon> 下载旧版兼容模板（11列树形）
        </el-button>
      </div>

      <!-- 自定义模板字段折叠面板 -->
      <el-collapse class="template-field-panel" v-model="fieldPanelActiveName" style="margin-top:16px;">
        <el-collapse-item name="custom">
          <template #title>
            <span style="font-weight:500;">
              <el-icon style="vertical-align:-2px;margin-right:4px;"><Setting /></el-icon>
              自定义模板字段（已选 {{ selectedFieldLabels.length }} 列，其中锁定必填 3 列）
            </span>
          </template>
          <div class="field-custom-container">
            <div class="field-custom-actions">
              <el-button size="small" v-permission="perm('resetDefault')" @click="selectDefault16Columns">{{ $tt("恢复默认18列") }}</el-button>
              <el-button size="small" v-permission="perm('columnSetting')" @click="selectOnlyLockedColumns">{{ $tt("仅保留必填列") }}</el-button>
              <el-button size="small" v-permission="perm('columnSetting')" @click="selectAllColumns">{{ $tt("全选（含扩展列）") }}</el-button>
            </div>

            <el-row :gutter="24">
              <!-- BOM主属性列 -->
              <el-col :span="12">
                <div class="field-group-title">{{ $tt("BOM主属性列（左侧表头继承）") }}</div>
                <div class="field-checkbox-group">
                  <el-checkbox
                    v-for="col in mainPropsColumns"
                    :key="col.label"
                    v-model="columnCheckedMap"
                    :label="$tf(col.label)"
                    :disabled="col.locked"
                    :border="true"
                    style="margin:6px 12px 6px 0;"
                    size="small"
                  >
                    {{ $tf(col.label) }}
                    <el-tag v-if="col.locked" type="danger" size="small" effect="dark" style="margin-left:6px;">{{ $tt("必填锁定") }}</el-tag>
                  </el-checkbox>
                </div>
              </el-col>
              <!-- 子件明细列 -->
              <el-col :span="12">
                <div class="field-group-title">{{ $tt("子件明细列（右侧每行一条）") }}</div>
                <div class="field-checkbox-group">
                  <el-checkbox
                    v-for="col in itemPropsColumns"
                    :key="col.label"
                    v-model="columnCheckedMap"
                    :label="$tf(col.label)"
                    :disabled="col.locked"
                    :border="true"
                    style="margin:6px 12px 6px 0;"
                    size="small"
                  >
                    {{ $tf(col.label) }}
                    <el-tag v-if="col.locked" type="danger" size="small" effect="dark" style="margin-left:6px;">{{ $tt("必填锁定") }}</el-tag>
                  </el-checkbox>
                </div>
              </el-col>
            </el-row>

            <div class="field-custom-download" style="margin-top:16px;text-align:right;">
              <el-button type="success" v-permission="perm('download')" @click="handleDownloadCustomTemplate" :loading="downloadingTemplateCustom">
                <el-icon><Download /></el-icon> 按勾选下载（含3个必填锁定列自动兜底）
              </el-button>
            </div>
          </div>
        </el-collapse-item>
      </el-collapse>
    </div>

    <el-divider />

    <!-- 步骤 2: 上传文件 -->
    <div class="import-step">
      <div class="step-title">
        <el-icon><Upload /></el-icon>
        <span>{{ $tt("第二步：上传BOM文件") }}</span>
      </div>
      <div class="step-desc">{{ $tt("上传文件后，系统会自动检查物料是否存在") }}</div>
      <el-upload
        ref="uploadRef"
        action="#"
        :auto-upload="false"
        :on-change="handleFileChange"
        :show-file-list="true"
        accept=".xlsx,.xls"
        drag
        class="import-uploader"
        :limit="1"
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

    <!-- 预检查结果：缺失物料提示 -->
    <div v-if="checkResult && checkResult.parseSuccess" class="check-result-section">
      <el-divider />

      <div class="check-summary">
        <el-row :gutter="16">
          <el-col :span="6">
            <el-statistic :title="$tt('文件总行数')" :value="checkResult.totalRows" />
          </el-col>
          <el-col :span="6">
            <el-statistic :title="$tt('已存在物料')" :value="checkResult.existingMaterialCount">
              <template #suffix>
                <el-icon color="#67C23A"><CircleCheck /></el-icon>
              </template>
            </el-statistic>
          </el-col>
          <el-col :span="6">
            <el-statistic :title="$tt('缺失物料')" :value="checkResult.missingMaterialCount">
              <template #suffix>
                <el-icon :color="checkResult.missingMaterialCount > 0 ? '#E6A23C' : '#67C23A'">
                  <Warning v-if="checkResult.missingMaterialCount > 0" />
                  <CircleCheck v-else />
                </el-icon>
              </template>
            </el-statistic>
          </el-col>
          <el-col :span="6">
            <el-statistic :title="$tt('父件数量')" :value="checkResult.parentMaterialCodes?.length || 0" />
          </el-col>
        </el-row>
      </div>

      <!-- 冲突母件提示：同一母件数据库里已存在 BOM -->
      <div v-if="checkResult.conflictBoms && checkResult.conflictBoms.length > 0" class="conflict-section">
        <el-alert type="warning" :closable="false" show-icon class="conflict-alert">
          <template #title>
            <span class="alert-title">
              发现 <b>{{ checkResult.conflictBoms.length }}</b> 个母件在系统中已存在 BOM
            </span>
          </template>
          <template #default>
            <div class="alert-content">
              <p>{{ $tt("同一母件可保存多条 BOM，但") }}<b>{{ $tt("默认 BOM（用于生产/MRP展开）只能有一个") }}</b>{{ $tt("。请选择处理方式：") }}</p>
              <ul>
                <li><strong>{{ $tt("覆盖现有 BOM") }}</strong>：保留原有 BOM 编号和"默认 BOM"状态，删除原明细后写入新明细</li>
                <li><strong>{{ $tt("新建该母件的另一版本 BOM") }}</strong>{{ $tt("（推荐）：新增一条非默认 BOM，不影响现有默认，后续可在 BOM 列表手动切换默认") }}</li>
                <li><strong>{{ $tt("跳过冲突母件") }}</strong>{{ $tt("：忽略这些母件下的所有行，其它母件仍正常导入") }}</li>
              </ul>
            </div>
          </template>
        </el-alert>

        <div class="conflict-table-title">{{ $tt("冲突母件详情：") }}</div>
        <el-table :data="checkResult.conflictBoms" border size="small" max-height="200">
          <el-table-column prop="materialCode" :label="$tf('母件编号')" width="140" />
          <el-table-column prop="materialName" :label="$tf('母件名称')" min-width="140" />
          <el-table-column prop="bomCode" :label="$tf('现有BOM编号')" width="140">
            <template #default="{ row }"><span>{{ row.bomCode || '-' }}</span></template>
          </el-table-column>
          <el-table-column prop="bomVersion" :label="$tf('现有版本')" width="100">
            <template #default="{ row }"><span>{{ row.bomVersion || '-' }}</span></template>
          </el-table-column>
          <el-table-column prop="isDefault" :label="$tf('是否默认')" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.isDefault ? 'success' : 'info'" size="small">
                {{ row.isDefault ? '默认BOM' : '非默认' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="itemCount" :label="$tf('明细行数')" width="80" align="center" />
        </el-table>

        <div class="action-buttons">
          <el-radio-group v-model="conflictPolicy" size="large">
            <el-radio-button label="COVER">
              <el-icon><RefreshRight /></el-icon>
              覆盖现有 BOM
            </el-radio-button>
            <el-radio-button label="NEW_VERSION">
              <el-icon><Plus /></el-icon>
              新建该母件的另一版本 BOM
            </el-radio-button>
            <el-radio-button label="SKIP">
              <el-icon><Minus /></el-icon>
              跳过冲突母件
            </el-radio-button>
          </el-radio-group>
        </div>
      </div>

      <!-- 缺失物料列表 -->
      <div v-if="checkResult.missingMaterialCount > 0" class="missing-materials-section">
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          class="missing-alert"
        >
          <template #title>
            <span class="alert-title">
              发现 {{ checkResult.missingMaterialCount }} 个物料在基础资料库中不存在
            </span>
          </template>
          <template #default>
            <div class="alert-content">
              <p>{{ $tt("以下物料编码在系统中未找到对应记录，您可以选择：") }}</p>
              <ul>
                <li><strong>{{ $tt("自动创建") }}</strong>{{ $tt("：系统将自动创建缺失的物料记录（基础信息使用Excel中的数据）") }}</li>
                <li><strong>{{ $tt("仅导入已有物料") }}</strong>{{ $tt("：跳过缺失物料的BOM行，只导入已存在物料的BOM数据") }}</li>
                <li><strong>{{ $tt("取消导入") }}</strong>{{ $tt("：先去基础资料模块补录物料后再导入") }}</li>
              </ul>
            </div>
          </template>
        </el-alert>

        <el-alert
          v-if="hasMissingCategory"
          type="error"
          :closable="false"
          show-icon
          class="missing-category-alert"
        >
          <template #title>
            <span class="alert-title">{{ $tt("物料分类编码缺失或不存在，本次导入已中止") }}</span>
          </template>
          <template #default>
            <div class="alert-content">
              <p>{{ $tt("以下物料缺少分类编码（或系统中不存在该分类），自动生成物料编码需要用到分类编码，请先在「基础资料 - 物料分类」补录后再导入：") }}</p>
              <div class="missing-category-list">
                <el-tag
                  v-for="cc in (checkResult.missingCategoryCodes || [])"
                  :key="cc"
                  type="danger"
                  size="small"
                  effect="plain"
                >{{ cc }}</el-tag>
              </div>
            </div>
          </template>
        </el-alert>

        <div class="sync-materials-bar">
          <el-button
            type="primary"
            :loading="syncingMaterials"
            :disabled="hasMissingCategory"
            v-permission="perm('import')"
            @click="syncMissingMaterials"
          >
            <el-icon><Plus /></el-icon>
            一键同步 {{ checkResult.missingMaterialCount }} 个缺失物料到物料库
          </el-button>
          <span class="sync-tip">{{ $tt("同步后可在「基础资料 - 物料管理」查看/补充信息，无需先单独导入物料，再导入BOM") }}</span>
        </div>

        <div class="missing-table-title">{{ $tt("缺失物料详情：") }}</div>
        <el-table :data="checkResult.missingMaterials" border size="small" max-height="200">
          <el-table-column prop="materialCode" :label="$tf('物料编码')" width="120" />
          <el-table-column prop="materialName" :label="$tf('物料名称')" min-width="120">
            <template #default="{ row }">
              <span>{{ row.materialName || '(未填写)' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="specification" :label="$tf('规格')" width="100">
            <template #default="{ row }">
              <span>{{ row.specification || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="materialRole" :label="$tf('角色')" width="80">
            <template #default="{ row }">
              <el-tag :type="row.materialRole === '父件' ? 'success' : 'info'" size="small">
                {{ row.materialRole }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="level" :label="$tf('层级')" width="60" align="center" />
          <el-table-column prop="suggestedType" :label="$tf('建议类型')" width="100">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ $dt(row.suggestedType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="suggestedUnit" :label="$tf('单位')" width="80">
            <template #default="{ row }">
              <span>{{ row.suggestedUnit || '个' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="categoryCode" :label="$tf('分类编码')" width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.categoryCode" size="small" effect="plain">{{ row.categoryCode }}</el-tag>
              <el-tag v-else type="danger" size="small" effect="plain">{{ $tt("缺失") }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="rowIndex" :label="$tf('所在行')" width="80" align="center" />
        </el-table>

        <div class="action-buttons">
          <el-radio-group v-model="handleMissingMode" size="large">
            <el-radio-button label="autoCreate">
              <el-icon><Plus /></el-icon>
              自动创建缺失物料
            </el-radio-button>
            <el-radio-button label="skip">
              <el-icon><Minus /></el-icon>
              仅导入已有物料
            </el-radio-button>
            <el-radio-button label="cancel">
              <el-icon><Close /></el-icon>
              取消导入
            </el-radio-button>
          </el-radio-group>
        </div>
      </div>

      <!-- 无缺失物料 -->
      <div v-else class="no-missing-section">
        <el-alert type="success" :closable="false" show-icon>
          <template #title>
            <span>{{ $tt("所有物料均已存在，可以直接导入") }}</span>
          </template>
        </el-alert>
      </div>
    </div>

    <!-- 导入结果 -->
    <div v-if="importResult" class="import-result-section">
      <el-divider />
      <div class="result-title">{{ $tt("导入结果") }}</div>
      <el-row :gutter="16">
        <el-col :span="6">
          <el-statistic :title="$tt('总记录数')" :value="importResult.totalCount || 0" />
        </el-col>
        <el-col :span="6">
          <el-statistic :title="$tt('成功')" :value="importResult.successCount || 0">
            <template #suffix>
              <el-icon color="#67C23A"><CircleCheck /></el-icon>
            </template>
          </el-statistic>
        </el-col>
        <el-col :span="6">
          <el-statistic :title="$tt('失败')" :value="importResult.failCount || 0">
            <template #suffix>
              <el-icon color="#F56C6C"><CircleClose /></el-icon>
            </template>
          </el-statistic>
        </el-col>
        <el-col :span="6">
          <el-statistic :title="$tt('创建物料')" :value="importResult.createdMaterialCount || 0">
            <template #suffix>
              <el-icon color="var(--erp-primary, #409EFF)"><Plus /></el-icon>
            </template>
          </el-statistic>
        </el-col>
      </el-row>
      <el-row v-if="importResult.skipCount" :gutter="16" style="margin-top: 12px;">
        <el-col :span="24">
          <el-alert type="info" :closable="false" show-icon>
            <template #title>
              已跳过 {{ importResult.skipCount }} 行{{ importResult.message ? '：' + importResult.message : '' }}
            </template>
          </el-alert>
        </el-col>
      </el-row>

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

      <el-alert
        v-if="importResult.createdMaterialCount > 0"
        type="info"
        :closable="false"
        class="created-material-alert"
      >
        <template #title>已自动创建 {{ importResult.createdMaterialCount }} 个新物料</template>
        <div>您可以在"基础资料 - 物料管理"中补充这些物料的详细信息（价格、供应商、库存等）</div>
      </el-alert>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button v-permission="perm('dlgClose')" @click="visible = false">{{ $tt("关闭") }}</el-button>
        <el-button
          v-if="checkResult && checkResult.parseSuccess && (checkResult.missingMaterialCount > 0 || hasConflictBoms)"
          type="primary"
          v-permission="perm('import')"
          @click="confirmImport"
          :loading="importing"
          :disabled="handleMissingMode === 'cancel' || hasMissingCategory"
        >
          <el-icon><Upload /></el-icon>
          {{ handleMissingMode === 'autoCreate' ? '自动创建并导入' : '确认导入' }}
        </el-button>
        <el-button
          v-else-if="checkResult && checkResult.parseSuccess && checkResult.missingMaterialCount === 0"
          type="success"
          v-permission="perm('import')"
          @click="confirmImport"
          :loading="importing"
          :disabled="hasMissingCategory"
        >
          <el-icon><Upload /></el-icon>
          开始导入
        </el-button>
        <el-button
          v-else
          type="primary"
          v-permission="perm('import')"
          @click="preCheckFile"
          :loading="checking"
          :disabled="!selectedFile"
        >
          <el-icon><Search /></el-icon>
          检查文件
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Download, Upload, CircleCheck, CircleClose, Warning, Star,
  Plus, Minus, Close, Search, RefreshRight, Setting
} from '@element-plus/icons-vue'
import {
  downloadBomTemplate,
  downloadBomUnifiedTemplate,
  downloadBomLegacyTemplate,
  getBomTemplateFields,
  checkBomImport,
  confirmBomImport,
  syncBomMissingMaterials
} from '@/utils/exportUtil'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('masterdata:bom')

const props = defineProps({
  modelValue: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'success'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const downloadingTemplateLegacy = ref(false) // 旧版11列兼容模板下载中
const downloadingTemplateUnified = ref(false)// 默认18列精简模板下载中
const downloadingTemplateCustom = ref(false) // 按勾选字段的自定义模板下载中
const checking = ref(false)
const importing = ref(false)
const syncingMaterials = ref(false)
const selectedFile = ref(null)
const checkResult = ref(null)
const importResult = ref(null)
const handleMissingMode = ref('autoCreate')
/** 冲突母件处理策略（COVER 覆盖 / NEW_VERSION 新增另一版本 / SKIP 跳过） */
const conflictPolicy = ref('NEW_VERSION')
const uploadRef = ref()

// ========== 模板字段自定义（步骤1折叠面板） ==========
/** 折叠面板展开状态 */
const fieldPanelActiveName = ref([])  // 空=默认收起，用户点开才加载字段元数据
/** 后端返回的 columns 元数据 [{label, group, locked, defaultChecked}] */
const templateColumns = ref([])
/** 字段勾选值：true-value/false-value 用 label 本身（el-checkbox v-model="columnCheckedMap" :label="$tf('列名')" 即作为组选中list模型写法） */
const columnCheckedMap = ref([])
/** 是否已加载过字段元数据（避免每次展开都请求） */
let templateFieldsLoaded = false

const mainPropsColumns = computed(() => templateColumns.value.filter(c => c.group === 'mainProps'))
const itemPropsColumns = computed(() => templateColumns.value.filter(c => c.group === 'itemProps'))
/** 当前勾选的所有列标签（含锁定列） */
const selectedFieldLabels = computed(() => Array.isArray(columnCheckedMap.value) ? columnCheckedMap.value : [])

/** 确保展开折叠面板时，首次加载字段元数据 */
import { watch as vueWatch } from 'vue'
vueWatch(fieldPanelActiveName, async (newVal) => {
  if (Array.isArray(newVal) && newVal.includes('custom') && !templateFieldsLoaded) {
    try {
      const data = await getBomTemplateFields()
      templateColumns.value = Array.isArray(data?.columns) ? data.columns : []
      // 初始化勾选状态：按 defaultChecked=true 的列 + 锁定列强制勾上
      const init = []
      templateColumns.value.forEach(c => {
        if (c.defaultChecked || c.locked) init.push(c.label)
      })
      columnCheckedMap.value = init
      templateFieldsLoaded = true
    } catch (e) {
      ElMessage.error(tt('加载模板字段清单失败：') + (e.message || tt('请稍后重试')))
    }
  }
})

function selectDefault16Columns() {
  if (!templateColumns.value.length) return
  const arr = templateColumns.value.filter(c => c.defaultChecked || c.locked).map(c => c.label)
  columnCheckedMap.value = arr
}
function selectOnlyLockedColumns() {
  if (!templateColumns.value.length) return
  columnCheckedMap.value = templateColumns.value.filter(c => c.locked).map(c => c.label)
}
function selectAllColumns() {
  if (!templateColumns.value.length) return
  columnCheckedMap.value = templateColumns.value.map(c => c.label)
}

async function handleDownloadCustomTemplate() {
  downloadingTemplateCustom.value = true
  try {
    await downloadBomUnifiedTemplate(selectedFieldLabels.value)
    ElMessage.success(tt('自定义模板下载成功（锁定列已自动带上）'))
  } catch (err) {
    ElMessage.error(tt('自定义模板下载失败：') + (err.message || tt('未知错误')))
  } finally {
    downloadingTemplateCustom.value = false
  }
}

/** 是否存在冲突母件（用于 footer 按钮展示） */
const hasConflictBoms = computed(() =>
  checkResult.value &&
  checkResult.value.conflictBoms &&
  checkResult.value.conflictBoms.length > 0
)

/** 是否存在缺失物料分类编码（缺失时禁止同步与导入，直接提示退出） */
const hasMissingCategory = computed(() =>
  !!(checkResult.value && checkResult.value.hasMissingCategory)
)

function resetDialog() {
  selectedFile.value = null
  checkResult.value = null
  importResult.value = null
  handleMissingMode.value = 'autoCreate'
  conflictPolicy.value = 'NEW_VERSION'
  fieldPanelActiveName.value = []
  if (uploadRef.value) {
    uploadRef.value.clearFiles()
  }
}

async function handleDownloadLegacyTemplate() {
  downloadingTemplateLegacy.value = true
  try {
    await downloadBomLegacyTemplate()
    ElMessage.success(tt('旧版兼容模板（11列树形）下载成功'))
  } catch (err) {
    ElMessage.error(tt('模板下载失败：') + (err.message || tt('未知错误')))
  } finally {
    downloadingTemplateLegacy.value = false
  }
}

async function handleDownloadUnifiedTemplate() {
  downloadingTemplateUnified.value = true
  try {
    // 不传字段 → 默认 18 列
    await downloadBomUnifiedTemplate(null)
    ElMessage.success(tt('精简18列统一模板下载成功'))
  } catch (err) {
    ElMessage.error(tt('模板下载失败：') + (err.message || tt('未知错误')))
  } finally {
    downloadingTemplateUnified.value = false
  }
}

function handleFileChange(file) {
  selectedFile.value = file.raw
  checkResult.value = null
  importResult.value = null
}

async function preCheckFile() {
  if (!selectedFile.value) {
    ElMessage.warning(tt('请先选择文件'))
    return
  }

  checking.value = true
  try {
    const result = await checkBomImport(selectedFile.value)
    checkResult.value = result

    if (!result.parseSuccess) {
      ElMessage.error(result.parseError || tt('文件解析失败'))
    } else if (result.missingMaterialCount === 0) {
      ElMessage.success(tt('所有物料均已存在，可以直接导入'))
    } else {
      ElMessage.warning(`${tt("发现 ")}${result.missingMaterialCount}${tt(" 个缺失物料")}`)
    }
  } catch (err) {
    ElMessage.error(tt('文件检查失败：') + (err.message || tt('未知错误')))
  } finally {
    checking.value = false
  }
}

/**
 * 一键同步缺失物料到物料库（不创建BOM），同步后自动重新预检查
 */
async function syncMissingMaterials() {
  if (!selectedFile.value) {
    ElMessage.warning(tt('请先选择文件'))
    return
  }
  if (hasMissingCategory.value) {
    ElMessage.warning(tt('存在物料分类编码缺失或不存在，请先在「基础资料 - 物料分类」补录后再同步'))
    return
  }
  syncingMaterials.value = true
  try {
    const result = await syncBomMissingMaterials(selectedFile.value)
    if (result && result.terminated) {
      ElMessage.error(result.message || tt('同步物料中止，请检查后再试'))
    } else {
      const created = result?.createdCount ?? 0
      const skipped = result?.skippedCount ?? 0
      ElMessage.success(`同步完成：新建 ${created} 个物料${skipped ? `${tt("，已存在跳过 ")}${skipped}${tt(" 个")}` : ''}`)
    }
    // 同步后重新预检查，刷新缺失物料列表
    await preCheckFile()
  } catch (err) {
    ElMessage.error(tt('同步物料失败：') + (err.message || tt('未知错误')))
  } finally {
    syncingMaterials.value = false
  }
}

async function confirmImport() {
  if (!selectedFile.value) {
    ElMessage.warning(tt('请先选择文件'))
    return
  }

  importing.value = true
  try {
    const autoCreate = handleMissingMode.value === 'autoCreate'
    const result = await confirmBomImport(selectedFile.value, autoCreate, conflictPolicy.value)
    importResult.value = result

    // 记录自动创建的物料数量（兼容后端未返回 exact 数量的情况）
    if (autoCreate && checkResult.value?.missingMaterialCount > 0 && !result.createdMaterialCount) {
      importResult.value.createdMaterialCount = checkResult.value.missingMaterialCount
    }

    if (result.failCount === 0) {
      const skipInfo = result.skipCount ? `（已跳过 ${result.skipCount} 行）` : ''
      ElMessage.success(`${tt("BOM导入成功：共 ")}${result.successCount || result.totalCount || 0}${tt(" 条记录")}${skipInfo}`)
      emit('success')
      // 存在跳过或失败时不自动关闭弹窗，让用户查看详情
      if (!(result.skipCount > 0 || (result.message && result.failCount === 0))) {
        visible.value = false
      }
    } else {
      ElMessage.warning(`${tt("导入完成：成功 ")}${result.successCount}${tt(" 条，失败 ")}${result.failCount}${tt(" 条")}`)
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

  .template-download-group {
    display: flex;
    gap: 12px;
    flex-wrap: wrap;
  }

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
    margin-bottom: 12px;
  }
}

.import-uploader {
  :deep(.el-upload-dragger) {
    width: 100%;
    height: 120px;
  }
}

.check-result-section {
  .check-summary {
    padding: 12px;
    background: #f5f7fa;
    border-radius: 4px;
    margin-bottom: 16px;
  }

  .missing-materials-section {
    .missing-alert {
      margin-bottom: 12px;

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
    }

    .missing-table-title {
      font-size: 14px;
      font-weight: 600;
      margin-bottom: 8px;
    }

    .sync-materials-bar {
      display: flex;
      align-items: center;
      gap: 12px;
      flex-wrap: wrap;
      margin: 12px 0;
      padding: 10px 12px;
      background: #f0f9eb;
      border: 1px solid #e1f3d8;
      border-radius: 4px;

      .sync-tip {
        font-size: 12px;
        color: #909399;
      }
    }

    .missing-category-alert {
      margin-bottom: 12px;

      .alert-title {
        font-weight: 600;
      }

      .alert-content {
        margin-top: 8px;
        font-size: 13px;

        .missing-category-list {
          display: flex;
          flex-wrap: wrap;
          gap: 8px;
          margin-top: 8px;
        }
      }
    }

    .action-buttons {
      margin-top: 16px;
      padding: 12px;
      background: #f5f7fa;
      border-radius: 4px;
    }
  }

  .conflict-section {
    margin: 16px 0 0;

    .conflict-alert {
      margin-bottom: 12px;

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
    }

    .conflict-table-title {
      font-size: 14px;
      font-weight: 600;
      margin-bottom: 8px;
    }
  }

  .no-missing-section {
    margin-top: 8px;
  }
}

.import-result-section {
  .result-title {
    font-size: 15px;
    font-weight: 600;
    color: #303133;
    margin-bottom: 12px;
  }

  .import-error-alert,
  .created-material-alert {
    margin-top: 12px;

    .error-list {
      max-height: 120px;
      overflow-y: auto;

      .error-item {
        font-size: 12px;
        padding: 2px 0;
        color: #F56C6C;
      }
    }
  }
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

/* ========== 模板字段自定义面板样式 ========== */
.template-field-panel {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fafbfc;
  :deep(.el-collapse-item__header) {
    padding: 0 16px;
    font-weight: 500;
  }
  :deep(.el-collapse-item__wrap) {
    border-top: 1px solid #ebeef5;
  }
}
.field-custom-container {
  padding: 8px 16px 16px;
}
.field-custom-actions {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  align-items: center;
  flex-wrap: wrap;
}
.field-group-title {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  margin: 8px 0 8px;
  padding-left: 6px;
  border-left: 3px solid var(--erp-primary, #409EFF);
}
.field-checkbox-group {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  padding: 4px 2px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  min-height: 44px;
}
</style>