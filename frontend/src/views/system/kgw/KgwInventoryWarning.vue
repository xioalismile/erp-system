<template>
  <div class="kgw-inventory-warning">
    <div class="kgw-toolbar">
      <div class="kgw-actions">
        <el-button v-permission="perm('addRow')" type="primary" size="small" @click="openAdd">{{ $tt("新增预警规则") }}</el-button>
        <el-button v-permission="perm('import')" size="small" @click="openImport">{{ $tt("导入") }}</el-button>
        <el-button v-permission="perm('enable')" type="warning" size="small" @click="onBatchToggle" :disabled="!hasSelection">{{ $tt("批量启用/停用") }}</el-button>
        <el-button v-permission="perm('delete')" type="danger" size="small" @click="onBatchDelete" :disabled="!hasSelection">{{ $tt("批量删除") }}</el-button>
        <el-button v-permission="perm('start')" type="success" size="small" @click="onCheckNow">{{ $tt("立即检测预警") }}</el-button>
      </div>
      <div class="kgw-search">
        <el-input v-model="searchText" :placeholder="$tt('货品编号/名称')" size="small" clearable @keyup.enter="loadRules">
          <template #suffix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filterStatus" :placeholder="$tt('状态')" size="small" clearable style="width: 100px" @change="loadRules">
          <el-option :label="$tf('启用')" :value="1" />
          <el-option :label="$tf('停用')" :value="0" />
        </el-select>
        <el-button v-permission="perm('query')" type="primary" size="small" @click="loadRules">{{ $tt("搜索") }}</el-button>
      </div>
    </div>

    <div class="kgw-body">
      <el-table :data="tableData" border size="small" style="width:100%" v-loading="loading" @selection-change="onSelectionChange" @sort-change="onSortChange">
        <el-table-column type="selection" width="40" />
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="materialCode" :label="$tf('货品编号')" min-width="130" sortable="custom" />
        <el-table-column prop="materialName" :label="$tf('货品名称')" min-width="160" sortable="custom" />
        <el-table-column :label="$tf('仓库')" min-width="110">
          <template #default="{ row }">{{ row.warehouseName || $tt('全部仓库') }}</template>
        </el-table-column>
        <el-table-column prop="minQuantity" :label="$tf('最低库存')" width="110" align="right" sortable="custom">
          <template #default="{ row }">{{ row.minQuantity == null ? '' : row.minQuantity }}</template>
        </el-table-column>
        <el-table-column prop="maxQuantity" :label="$tf('最高库存')" width="110" align="right" sortable="custom">
          <template #default="{ row }">{{ row.maxQuantity == null ? '' : row.maxQuantity }}</template>
        </el-table-column>
        <el-table-column :label="$tf('通知方式')" min-width="110">
          <template #default="{ row }">{{ notifyLabel(row.notifyType) }}</template>
        </el-table-column>
        <el-table-column :label="$tf('状态')" width="80" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.status" :active-value="1" :inactive-value="0" @change="onToggle(row)" />
          </template>
        </el-table-column>
        <el-table-column :label="$tf('操作')" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="perm('edit')" type="primary" link size="small" @click="openEdit(row)">{{ $tt("编辑") }}</el-button>
            <el-button v-permission="perm('delete')" type="danger" link size="small" @click="onDelete(row)">{{ $tt("删除") }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          small
          background
          @current-change="loadRules"
          @size-change="loadRules"
        />
      </div>
    </div>

    <!-- 新增 / 编辑：批量预警规则（操作按钮在弹窗标题栏，不置底） -->
    <el-dialog v-model="dialogVisible" width="1200px" :close-on-click-modal="false" class="warning-dialog" top="6vh">
      <template #header>
        <div class="dlg-header">
          <span class="dlg-title">{{ isEdit ? $tt('编辑预警规则') : $tt('新增预警规则') }}</span>
          <span class="dlg-header-actions">
            <el-button size="small" @click="dialogVisible = false">{{ $tt("取消") }}</el-button>
            <el-button size="small" type="primary" :loading="saving" @click="onSave">{{ $tt("保存") }}</el-button>
          </span>
        </div>
      </template>

      <div class="dlg-body">
        <el-form label-width="80px" class="dlg-form">
          <el-form-item :label="$tf('通知方式')">
            <el-checkbox-group v-model="headerForm.notifyMethods">
              <el-checkbox :value="1">{{ $tt("系统消息") }}</el-checkbox>
              <el-checkbox :value="2" disabled>{{ $tt("弹窗") }}</el-checkbox>
              <el-checkbox :value="4" disabled>{{ $tt("邮件") }}</el-checkbox>
              <el-checkbox :value="8" disabled>{{ $tt("短信") }}</el-checkbox>
            </el-checkbox-group>
            <div class="kgw-hint">{{ $tt("弹窗提醒、邮件、短信为后续功能预留，暂不可用") }}</div>
          </el-form-item>
          <el-form-item :label="$tf('通知人')">
            <el-select v-model="headerForm.notifyUsers" multiple filterable clearable :placeholder="$tt('不选则默认通知创建人')" style="width:100%">
              <el-option v-for="e in employeeList" :key="e.employeeId" :label="e.employeeName" :value="e.employeeName" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$tf('备注')">
            <el-input v-model="headerForm.remark" :placeholder="$tt('选填')" />
          </el-form-item>
        </el-form>

        <!-- 物料明细：调用通用组件（自动带物料自带列 / 图片 / 自定义字段 / 列设置 / 添加货品） -->
        <MaterialItemsTable
          v-model="lines"
          page-key="InventoryWarningDetail"
          title="预警货品明细"
          hint="每个货品一行，各自填上下限：填了按对应方向预警，填 0 或留空表示该方向不预警；保存时每行生成一条规则。"
          :extra-columns="detailExtraColumns"
          :row-defaults="detailRowDefaults"
        >
          <template #actions>
            <el-button size="small" :disabled="!lines.length" @click="fillVisible = true">{{ $tt("批量填充上下限") }}</el-button>
          </template>
        </MaterialItemsTable>
      </div>
    </el-dialog>

    <!-- 批量填充上下限 -->
    <el-dialog v-model="fillVisible" width="420px" append-to-body :close-on-click-modal="false">
      <template #header>
        <div class="dlg-header">
          <span class="dlg-title">{{ $tt('批量填充上下限') }}</span>
          <span class="dlg-header-actions">
            <el-button size="small" @click="fillVisible = false">{{ $tt("取消") }}</el-button>
            <el-button size="small" type="primary" @click="applyFill">{{ $tt("应用到全部") }}</el-button>
          </span>
        </div>
      </template>
      <el-form label-width="90px">
        <el-form-item :label="$tf('最低库存')">
          <el-input-number v-model="fillForm.minQuantity" :min="0" :precision="2" :controls="false" style="width:100%" :placeholder="$tt('留空则不改')" />
        </el-form-item>
        <el-form-item :label="$tf('最高库存')">
          <el-input-number v-model="fillForm.maxQuantity" :min="0" :precision="2" :controls="false" style="width:100%" :placeholder="$tt('留空则不改')" />
        </el-form-item>
      </el-form>
      <div class="kgw-hint">{{ $tt("把同一个上下限套到明细里的全部货品；不想统一的货品，可在明细里逐行改。") }}</div>
    </el-dialog>

    <!-- 导入预警规则 -->
    <el-dialog v-model="importVisible" width="760px" append-to-body :close-on-click-modal="false">
      <template #header>
        <div class="dlg-header">
          <span class="dlg-title">{{ $tt('导入预警规则') }}</span>
          <span class="dlg-header-actions">
            <el-button size="small" @click="importVisible = false">{{ $tt("关闭") }}</el-button>
            <el-button v-if="checkResult && checkResult.parseSuccess" size="small" type="primary" :loading="importing" :disabled="importing" @click="confirmImport">
              {{ (checkResult.hasConflict && onExistingConflict === 'OVERWRITE') ? $tt('覆盖并导入') : $tt('开始导入') }}
            </el-button>
          </span>
        </div>
      </template>

      <div class="import-step">
        <div class="step-title">{{ $tt("第一步：下载导入模板") }}</div>
        <div class="step-desc">{{ $tt("模板列：货品编码、货品名称、仓库、最低库存、最高库存、通知方式、状态、备注；一行填一个货品。") }}</div>
        <el-button type="primary" size="small" @click="downloadTemplate">{{ $tt("下载导入模板") }}</el-button>
      </div>

      <el-divider />

      <div class="import-step">
        <div class="step-title">{{ $tt("第二步：上传数据文件（选好即自动检查）") }}</div>
        <el-upload
          ref="uploadRef"
          action="#"
          :auto-upload="false"
          :on-change="onFileChange"
          :show-file-list="true"
          :limit="1"
          accept=".xlsx,.xls"
          drag
        >
          <div class="el-upload__text">{{ $tt("将文件拖到此处，或点击上传") }}</div>
          <template #tip><div class="el-upload__tip">{{ $tt("支持 .xlsx / .xls，文件不超过 10MB") }}</div></template>
        </el-upload>
      </div>

      <div v-if="checking" class="checking-hint">{{ $tt("正在检查文件，请稍候…") }}</div>

      <div v-if="checkResult" class="check-result">
        <el-divider />
        <el-alert v-if="!checkResult.parseSuccess" type="error" :closable="false" show-icon :title="$tt('文件检查未通过')">
          <div>{{ checkResult.parseError || $tt('文件格式不正确，请使用下载的模板重新填写') }}</div>
        </el-alert>
        <template v-else>
          <div class="check-summary">
            <span>{{ $tt("文件数据行") }}：<b>{{ checkResult.totalCount || 0 }}</b></span>
            <span>{{ $tt("可新增") }}：<b>{{ checkResult.newCount || 0 }}</b></span>
            <span>{{ $tt("已存在(冲突)") }}：<b>{{ checkResult.conflictCount || 0 }}</b></span>
            <span>{{ $tt("校验失败") }}：<b>{{ checkResult.errorCount || 0 }}</b></span>
          </div>

          <el-alert v-if="checkResult.errorCount > 0" type="error" :closable="false" class="row-error-alert">
            <template #title>{{ $tt("有") }} {{ checkResult.errorCount }} {{ $tt("行未通过校验，不会被导入") }}</template>
            <div class="error-list">
              <div v-for="(err, idx) in (checkResult.errors || []).slice(0, 10)" :key="idx" class="error-item">
                {{ $tt("第") }} {{ err.rowIndex }} {{ $tt("行") }} {{ err.fieldName ? '[' + err.fieldName + ']' : '' }}: {{ err.message }}
              </div>
              <div v-if="(checkResult.errors || []).length > 10" class="error-more">… {{ $tt("还有") }} {{ checkResult.errors.length - 10 }} {{ $tt("条错误") }}</div>
            </div>
          </el-alert>

          <div v-if="checkResult.hasConflict" class="conflict-section">
            <el-alert type="warning" :closable="false" show-icon :title="$tt('发现') + ' ' + checkResult.conflictCount + ' ' + $tt('个货品已配置同仓库预警规则')">
              <div class="conflict-tip">{{ $tt("请选择本次导入对已存在规则的处理方式") }}</div>
            </el-alert>
            <el-table :data="checkResult.conflicts" border size="small" max-height="200" style="margin-top:8px">
              <el-table-column prop="rowIndex" :label="$tf('所在行')" width="70" align="center" />
              <el-table-column prop="materialCode" :label="$tf('货品编码')" width="140" />
              <el-table-column prop="materialName" :label="$tf('货品名称')" min-width="150" />
              <el-table-column :label="$tf('仓库')" width="120">
                <template #default="{ row }">{{ row.warehouseName || $tt('全部仓库') }}</template>
              </el-table-column>
            </el-table>
            <el-radio-group v-model="onExistingConflict" style="margin-top:12px">
              <el-radio-button label="SKIP">{{ $tt("跳过已存在（保留原规则）") }}</el-radio-button>
              <el-radio-button label="OVERWRITE">{{ $tt("覆盖已存在（用表格内容更新）") }}</el-radio-button>
            </el-radio-group>
          </div>
          <el-alert v-else type="success" :closable="false" show-icon :title="$tt('全部为新规则，可直接导入')" class="no-conflict-alert" />
        </template>
      </div>

      <div v-if="importResult" class="import-result">
        <el-divider />
        <div class="result-title">{{ $tt("导入结果") }}</div>
        <div class="result-summary">
          <span>{{ $tt("新增") }}：<b>{{ importResult.successCount || 0 }}</b></span>
          <span>{{ $tt("覆盖") }}：<b>{{ importResult.updatedCount || 0 }}</b></span>
          <span>{{ $tt("跳过") }}：<b>{{ importResult.skipCount || 0 }}</b></span>
          <span>{{ $tt("失败") }}：<b>{{ importResult.failCount || 0 }}</b></span>
        </div>
        <el-alert v-if="importResult.message" type="info" :closable="false" show-icon :title="importResult.message" style="margin-top:8px" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { usePagePerm } from '@/utils/pagePerm'
import { useTableSort } from '@/composables/useTableSort'
import { tt } from '@/i18n'
import {
  listWarningRules, getWarningRule, saveWarningRuleBatch, updateWarningRule,
  deleteWarningRule, toggleWarningRule, checkWarningNow,
  downloadWarningRuleTemplate, checkWarningRuleImport, confirmWarningRuleImport
} from '@/api/inventory/warningRule'
import { listAllWarehouse } from '@/api/basedata/warehouse'
import { listAllEmployee } from '@/api/basedata/employee'
// 物料明细通用组件（自动带物料自带列 / 图片 / 自定义字段 / 列设置 / 添加货品）
import MaterialItemsTable from '@/components/MaterialItemsTable.vue'

const perm = usePagePerm('system:inventory-warning')
const { handleSortChange, getSortParams } = useTableSort()
const onSortChange = (e) => { handleSortChange(e); loadRules() }

const notifyLabel = (n) => {
  const v = (n == null || n === 0) ? 1 : n
  const arr = []
  if (v & 1) arr.push(tt('系统消息'))
  if (v & 2) arr.push(tt('弹窗'))
  if (v & 4) arr.push(tt('邮件'))
  if (v & 8) arr.push(tt('短信'))
  return arr.join('、')
}

// ==================== 列表 ====================
const searchText = ref('')
const filterStatus = ref(null)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const tableData = ref([])
const selectedRows = ref([])
const hasSelection = computed(() => selectedRows.value.length > 0)

async function loadRules() {
  loading.value = true
  try {
    const params = { current: page.value, size: pageSize.value, ...getSortParams() }
    if (searchText.value) params.keyword = searchText.value
    if (filterStatus.value !== null && filterStatus.value !== '') params.status = filterStatus.value
    const res = await listWarningRules(params)
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

function onSelectionChange(rows) { selectedRows.value = rows }

async function onToggle(row) {
  try {
    await toggleWarningRule(row.id)
    ElMessage.success(tt('操作成功'))
  } catch (e) {
    row.status = row.status === 1 ? 0 : 1
    ElMessage.error(tt('操作失败'))
  }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(tt('确定删除该预警规则？'), tt('提示'), { type: 'warning' })
  } catch (e) { return }
  try {
    await deleteWarningRule(row.id)
    ElMessage.success(tt('删除成功'))
    loadRules()
  } catch (e) {
    ElMessage.error(tt('删除失败'))
  }
}

async function onBatchDelete() {
  if (!selectedRows.value.length) return
  try {
    await ElMessageBox.confirm(`${tt('确定删除选中的')} ${selectedRows.value.length} ${tt('条规则？')}`, tt('提示'), { type: 'warning' })
  } catch (e) { return }
  try {
    await Promise.all(selectedRows.value.map(r => deleteWarningRule(r.id)))
    ElMessage.success(tt('批量删除成功'))
    loadRules()
  } catch (e) {
    ElMessage.error(tt('部分删除失败'))
    loadRules()
  }
}

async function onBatchToggle() {
  if (!selectedRows.value.length) return
  try {
    await Promise.all(selectedRows.value.map(r => toggleWarningRule(r.id)))
    ElMessage.success(tt('批量操作成功'))
    loadRules()
  } catch (e) {
    ElMessage.error(tt('部分操作失败'))
    loadRules()
  }
}

async function onCheckNow() {
  try {
    await checkWarningNow()
    ElMessage.success(tt('预警检测完成'))
  } catch (e) {
    ElMessage.error(tt('检测失败'))
  }
}

// ==================== 新增 / 编辑弹窗 ====================
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(null)
const saving = ref(false)
const headerForm = reactive({ notifyMethods: [1], notifyUsers: [], remark: '' })
const lines = ref([])
const warehouseList = ref([])
const employeeList = ref([])

async function loadOptions() {
  try {
    const res = await listAllWarehouse()
    warehouseList.value = res.records || res || []
  } catch (e) { warehouseList.value = [] }
  try {
    const res = await listAllEmployee({})
    employeeList.value = res.records || res || []
  } catch (e) { employeeList.value = [] }
}

function openAdd() {
  isEdit.value = false
  editId.value = null
  headerForm.notifyMethods = [1]
  headerForm.notifyUsers = []
  headerForm.remark = ''
  lines.value = []
  dialogVisible.value = true
}

async function openEdit(row) {
  try {
    const res = await getWarningRule(row.id)
    isEdit.value = true
    editId.value = res.id
    headerForm.notifyMethods = notifyBitsToArray(res.notifyType)
    headerForm.notifyUsers = res.notifyUsers ? String(res.notifyUsers).split(',').filter(Boolean) : []
    headerForm.remark = res.remark || ''
    lines.value = [{
      materialId: res.materialId,
      materialCode: res.materialCode,
      materialName: res.materialName,
      warehouseId: res.warehouseId,
      minQuantity: res.minQuantity,
      maxQuantity: res.maxQuantity,
      status: res.status == null ? 1 : res.status
    }]
    // 物料自带字段（图片/规格/分类…）与物料自定义字段值，由「物料明细通用组件」自动补齐
    dialogVisible.value = true
  } catch (e) {
    ElMessage.error(tt('获取详情失败'))
  }
}

function notifyBitsToArray(n) {
  const v = (n == null || n === 0) ? 1 : n
  const arr = []
  if (v & 1) arr.push(1)
  if (v & 2) arr.push(2)
  if (v & 4) arr.push(4)
  if (v & 8) arr.push(8)
  return arr.length ? arr : [1]
}
const notifyTypeValue = computed(() => headerForm.notifyMethods.reduce((s, v) => s + v, 0))

function validateLines() {
  if (!lines.value.length) {
    ElMessage.warning(tt('请至少添加一个货品'))
    return false
  }
  for (let i = 0; i < lines.value.length; i++) {
    const r = lines.value[i]
    const name = r.materialName || r.materialCode || tt('货品')
    const hasMin = r.minQuantity != null && Number(r.minQuantity) > 0
    const hasMax = r.maxQuantity != null && Number(r.maxQuantity) > 0
    if (!hasMin && !hasMax) { ElMessage.warning(`${name}：${tt('请至少填写最低库存或最高库存')}`); return false }
  }
  return true
}

async function onSave() {
  if (!validateLines()) return
  saving.value = true
  try {
    const notifyUsers = headerForm.notifyUsers.join(',')
    if (isEdit.value) {
      const r = lines.value[0]
      await updateWarningRule({
        id: editId.value,
        materialId: r.materialId,
        warehouseId: r.warehouseId,
        minQuantity: r.minQuantity,
        maxQuantity: r.maxQuantity,
        notifyType: notifyTypeValue.value,
        notifyUsers,
        remark: headerForm.remark,
        status: r.status
      })
      ElMessage.success(tt('更新成功'))
    } else {
      await saveWarningRuleBatch({
        notifyType: notifyTypeValue.value,
        notifyUsers,
        remark: headerForm.remark,
        lines: lines.value.map(r => ({
          materialId: r.materialId,
          warehouseId: r.warehouseId,
          minQuantity: r.minQuantity,
          maxQuantity: r.maxQuantity,
          status: 1
        }))
      })
      ElMessage.success(tt('新增成功'))
    }
    dialogVisible.value = false
    loadRules()
  } catch (e) {
    // 后端已给出可读错误提示
  } finally {
    saving.value = false
  }
}

// ==================== 物料明细：业务附加列 + 新增行默认值（物料列 / 图片 / 自定义字段 / 列设置 / 添加货品由通用组件自动带） ====================
const detailExtraColumns = computed(() => [
  {
    prop: 'warehouseId', label: '仓库', width: 170, editor: 'select', placeholder: '全部仓库',
    options: warehouseList.value.map(w => ({ value: w.stockId, label: w.stockName }))
  },
  { prop: 'minQuantity', label: '最低库存', width: 170, editor: 'number', placeholder: '填了才按此预警' },
  { prop: 'maxQuantity', label: '最高库存', width: 170, editor: 'number', placeholder: '填了才按此预警' }
])
const detailRowDefaults = { warehouseId: null, minQuantity: null, maxQuantity: null, status: 1 }

// ==================== 批量填充 ====================
const fillVisible = ref(false)
const fillForm = reactive({ minQuantity: null, maxQuantity: null })
function applyFill() {
  lines.value.forEach(r => {
    if (fillForm.minQuantity != null) r.minQuantity = fillForm.minQuantity
    if (fillForm.maxQuantity != null) r.maxQuantity = fillForm.maxQuantity
  })
  fillVisible.value = false
  ElMessage.success(tt('已应用到全部货品'))
}

// ==================== 导入 ====================
const importVisible = ref(false)
const uploadRef = ref(null)
const selectedFile = ref(null)
const checking = ref(false)
const importing = ref(false)
const checkResult = ref(null)
const importResult = ref(null)
const onExistingConflict = ref('SKIP')

function openImport() {
  importVisible.value = true
  resetImport()
}
function resetImport() {
  selectedFile.value = null
  checkResult.value = null
  importResult.value = null
  onExistingConflict.value = 'SKIP'
  if (uploadRef.value) uploadRef.value.clearFiles()
}
async function downloadTemplate() {
  try {
    await downloadWarningRuleTemplate()
    ElMessage.success(tt('模板下载成功'))
  } catch (e) {
    ElMessage.error(tt('模板下载失败'))
  }
}
function onFileChange(file) {
  selectedFile.value = file.raw
  checkResult.value = null
  importResult.value = null
  onExistingConflict.value = 'SKIP'
  if (selectedFile.value) doCheck()
}
async function doCheck() {
  if (!selectedFile.value) return
  checking.value = true
  try {
    checkResult.value = await checkWarningRuleImport(selectedFile.value)
    if (checkResult.value && checkResult.value.parseSuccess === false) {
      ElMessage.error(checkResult.value.parseError || tt('文件解析失败'))
    }
  } catch (e) {
    checkResult.value = null
  } finally {
    checking.value = false
  }
}
async function confirmImport() {
  if (!selectedFile.value) { ElMessage.warning(tt('请先选择文件')); return }
  importing.value = true
  try {
    importResult.value = await confirmWarningRuleImport(selectedFile.value, onExistingConflict.value)
    ElMessage.success(importResult.value.message || tt('导入完成'))
    loadRules()
  } catch (e) {
    // 后端已给出可读错误提示
  } finally {
    importing.value = false
  }
}

onMounted(() => {
  loadRules()
  loadOptions()
})
</script>

<style scoped lang="scss">
.kgw-inventory-warning {
  background: #fff;
  min-height: 100%;
  font-family: Tahoma, 'Microsoft YaHei', sans-serif;
  display: flex;
  flex-direction: column;
}
.kgw-toolbar {
  position: sticky;
  top: 0;
  z-index: 10;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #ebeef5;
  padding: 0 16px;
  height: 48px;
  flex-shrink: 0;
  gap: 12px;
}
.kgw-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.kgw-search { display: flex; gap: 8px; align-items: center; :deep(.el-input) { width: 180px; } }
.kgw-body { padding: 12px; flex: 1; }
.table-pagination { display: flex; justify-content: flex-end; padding: 12px 0 4px 0; }
.kgw-hint { font-size: 12px; color: #909399; margin-top: 4px; line-height: 1.5; }

// 弹窗：操作按钮在标题栏右侧（不置底）
:deep(.warning-dialog .el-dialog__header) { margin-right: 0; }
.dlg-header { display: flex; align-items: center; justify-content: space-between; }
.dlg-title { font-size: 15px; font-weight: 600; color: #303133; }
.dlg-header-actions { display: flex; gap: 8px; }
.dlg-body { padding: 0 2px; }
.dlg-form { margin-bottom: 4px; }



.import-step { padding: 6px 0; }
.step-title { font-size: 14px; font-weight: 600; color: #303133; margin-bottom: 6px; }
.step-desc { font-size: 12px; color: #909399; margin-bottom: 10px; }
.checking-hint { margin-top: 12px; font-size: 13px; color: #606266; }
.check-summary { display: flex; gap: 24px; padding: 10px 12px; background: #f5f7fa; border-radius: 4px; font-size: 13px; color: #606266; }
.check-summary b { color: #303133; }
.row-error-alert { margin-top: 10px; }
.error-list { margin-top: 6px; font-size: 12px; max-height: 150px; overflow: auto; }
.error-more { color: #909399; }
.conflict-section { margin-top: 12px; }
.conflict-tip { margin-top: 6px; font-size: 13px; }
.no-conflict-alert { margin-top: 10px; }
.import-result .result-title { font-size: 14px; font-weight: 600; margin-bottom: 8px; }
.result-summary { display: flex; gap: 24px; font-size: 13px; color: #606266; }
.result-summary b { color: #303133; }

:deep(.el-button--primary) { background-color: #1890ff; border-color: #1890ff; }
:deep(.el-button--primary:hover) { background-color: #40a9ff; border-color: #40a9ff; }
:deep(.el-button--success) { background-color: #52c41a; border-color: #52c41a; }
:deep(.el-button--warning) { background-color: #faad14; border-color: #faad14; }
:deep(.el-button--danger) { background-color: #f5222d; border-color: #f5222d; }
</style>
