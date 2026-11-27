<template>
  <div class="erp-page" :class="{ 'embedded': embedded }">
    <el-card>
      <div class="erp-search-bar">
        <el-input v-model="search.keyword" :placeholder="$tt('策略编码')" clearable style="width:240px" @keyup.enter="handleSearch" />
        <el-select v-model="search.status" :placeholder="$tt('状态')" clearable style="width:140px">
          <el-option :label="$tf('启用')" value="启用" />
          <el-option :label="$tf('禁用')" value="禁用" />
        </el-select>
        <el-button type="primary" v-permission="perm('query')" @click="handleSearch">{{ $tt("查询") }}</el-button>
        <el-button v-permission="perm('reset')" @click="handleReset">{{ $tt("重置") }}</el-button>
        <el-button type="primary" v-permission="perm('add')" @click="handleAdd">{{ $tt("+ 新增策略") }}</el-button>
        <el-button type="success" v-permission="perm('export')" @click="handleExport">{{ $tt("导出") }}</el-button>
        <el-button v-permission="perm('import')" @click="handleImport">{{ $tt("导入") }}</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border stripe @sort-change="onSortChange">
        <el-table-column type="index" :label="$tf('序号')" width="60" />
        <el-table-column prop="strategyCode" :label="$tf('策略编码')" min-width="200" sortable="custom">
          <template #default="{ row }">
            <span class="strategy-name-link" @click="handleEdit(row)">{{ row.strategyCode }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="targetType" :label="$tf('适用范围')" width="130" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="targetTypeTag(row.targetType)">{{ targetTypeLabel(row.targetType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="priority" :label="$tf('优先级')" width="100" align="center" sortable="custom"/>
        <el-table-column prop="effectiveDate" :label="$tf('生效日期')" width="120" sortable="custom"/>
        <el-table-column prop="expiryDate" :label="$tf('失效日期')" width="120" sortable="custom"/>
        <el-table-column prop="status" :label="$tf('状态')" width="100" sortable="custom">
          <template #default="{ row }">
            <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">{{ $dt(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <AuditColumns />
      </el-table>

      <el-pagination
        v-model:current-page="pagination.current"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="fetchData"
        @current-change="fetchData"
        style="margin-top:16px;justify-content:flex-end;display:flex"
      />
    </el-card>

    <!-- 导入弹窗（照销售订单列表页结构：下载模板 → 上传 → 结果统计） -->
    <el-dialog
      v-model="importDialogVisible"
      :title="$tf('采购价格策略导入')"
      width="560px"
      :close-on-click-modal="false"
      class="tech-dialog"
    >
      <div class="import-dialog-content">
        <div class="import-step">
          <div class="step-title">
            <el-icon><Download /></el-icon>
            <span>{{ $tt("第一步：下载导入模板") }}</span>
          </div>
          <div class="step-desc">{{ $tt("下载模板后，按模板格式填写数据，支持 .xlsx 格式") }}</div>
          <el-button v-permission="perm('downloadTemplate')" type="primary" @click="handleDownloadTemplate" :loading="downloadingTemplate">
            <el-icon><Download /></el-icon> {{ $tt("下载导入模板") }}
          </el-button>
        </div>
        <el-divider />
        <div class="import-step">
          <div class="step-title">
            <el-icon><Upload /></el-icon>
            <span>{{ $tt("第二步：上传数据文件") }}</span>
          </div>
          <div class="step-desc">{{ $tt("选择填写好的 Excel 文件上传导入") }}</div>
          <el-upload
            ref="uploadRef"
            action="#"
            :auto-upload="false"
            :on-change="handleFileChange"
            :show-file-list="true"
            accept=".xlsx,.xls"
            drag
            class="import-uploader"
          >
            <el-icon class="el-icon--upload"><Upload /></el-icon>
            <div class="el-upload__text">{{ $tt("将文件拖到此处，或") }}<em>{{ $tt("点击上传") }}</em></div>
            <template #tip>
              <div class="el-upload__tip">{{ $tt("请上传 .xlsx / .xls 格式文件，文件大小不超过 10MB") }}</div>
            </template>
          </el-upload>
        </div>
        <div v-if="importResult" class="import-result">
          <el-divider />
          <div class="result-title">{{ $tt("导入结果") }}</div>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-statistic :title="$tt('总记录数')" :value="importResult.totalCount || 0" />
            </el-col>
            <el-col :span="8">
              <el-statistic :title="$tt('成功')" :value="importResult.successCount || 0">
                <template #suffix><el-icon color="#67C23A"><CircleCheck /></el-icon></template>
              </el-statistic>
            </el-col>
            <el-col :span="8">
              <el-statistic :title="$tt('失败')" :value="importResult.failCount || 0">
                <template #suffix><el-icon color="#F56C6C"><CircleClose /></el-icon></template>
              </el-statistic>
            </el-col>
          </el-row>
          <el-alert
            v-if="importResult.failCount > 0"
            :title="`有 ${importResult.failCount} 条记录导入失败`"
            type="error"
            :closable="false"
            class="import-error-alert"
          >
            <div class="error-list">
              <div v-for="(err, idx) in (importResult.errors || []).slice(0, 10)" :key="idx" class="error-item">
                第 {{ err.rowIndex }} 行 {{ err.fieldName ? '[' + err.fieldName + ']' : '' }}: {{ err.message }}
              </div>
              <div v-if="(importResult.errors || []).length > 10" class="error-more">
                ... 还有 {{ importResult.errors.length - 10 }} 条错误
              </div>
            </div>
          </el-alert>
        </div>
      </div>
      <template #footer>
        <el-button v-permission="perm('dlgClose')" @click="importDialogVisible = false">{{ $tt("关闭") }}</el-button>
        <el-button v-permission="perm('import')" type="primary" @click="confirmImport" :loading="importing" :disabled="!selectedImportFile">
          <el-icon><Upload /></el-icon> 开始导入
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Upload, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import {
  listPurchasePriceStrategy,
  enablePurchasePriceStrategy,
  disablePurchasePriceStrategy
} from '@/api/priceStrategy'
import { downloadTemplate as downloadTemplateUtil, importData, exportData } from '@/utils/exportUtil'
import AuditColumns from '@/components/AuditColumns.vue'
import { useTableSort } from '@/composables/useTableSort'
import { usePagePerm } from '@/utils/pagePerm'
import { tt } from '@/i18n'
const { handleSortChange, getSortParams } = useTableSort()
// 按钮权限：前缀按当前路由自动解析（嵌入式页面 = 设置中心子项 system:purchase-price）
const perm = usePagePerm()


const onSortChange = (e) => { handleSortChange(e); fetchData() }
const props = defineProps({
  embedded: { type: Boolean, default: false }
})

const router = useRouter()
const loading = ref(false)
const tableData = ref([])
const search = reactive({ keyword: '', status: '' })
const pagination = reactive({ current: 1, size: 20, total: 0, ...getSortParams() })

// 导入/导出/下载模板：统一走 @/utils/exportUtil，实体名 = 后端登记表的 code
const ENTITY = 'purchasePriceStrategy'
const importDialogVisible = ref(false)
const downloadingTemplate = ref(false)
const importing = ref(false)
const selectedImportFile = ref(null)
const importResult = ref(null)
const uploadRef = ref()

// 适用范围：material 按物料 / category 按商品分类 / supplier 按供应商
const TARGET_TYPE_LABELS = { material: '按物料', category: '按商品分类', supplier: '按供应商' }
function targetTypeLabel(type) {
  return TARGET_TYPE_LABELS[type] || TARGET_TYPE_LABELS.material
}
function targetTypeTag(type) {
  const map = { material: 'primary', category: 'success', supplier: 'warning' }
  return map[type] || 'info'
}

function buildEditRoute(id) {
  const path = id ? `/purchase/price-strategy/${id}` : `/purchase/price-strategy/new`
  if (props.embedded) {
    return { path, query: { from: 'settings' } }
  }
  return path
}

function handleAdd() {
  router.push(buildEditRoute(null))
}

function handleEdit(row) {
  router.push(buildEditRoute(row.strategyId))
}

async function fetchData() {
  loading.value = true
  try {
    const res = await listPurchasePriceStrategy({ current: pagination.current, size: pagination.size, ...search, ...getSortParams() })
    // 后端实体字段(id/status:Integer/startDate/endDate)映射为前端列表字段(strategyId/status:String/effectiveDate/expiryDate)
    tableData.value = (res.records || []).map(r => ({
      ...r,
      strategyId: r.id,
      status: r.status === 1 ? '启用' : '禁用',
      effectiveDate: r.startDate || r.effectiveDate || '',
      expiryDate: r.endDate || r.expiryDate || ''
    }))
    pagination.total = res.total || 0
  } catch (e) {
    ElMessage.error(e.message || tt('查询失败'))
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.current = 1
  fetchData()
}

function handleReset() {
  search.keyword = ''
  search.status = ''
  pagination.current = 1
  fetchData()
}

async function handleEnable(row) {
  try {
    await ElMessageBox.confirm(`${tt("确认启用策略「")}${row.strategyCode}」?`, tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await enablePurchasePriceStrategy(row.strategyId)
    ElMessage.success(tt('已启用'))
    fetchData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('操作失败'))
  }
}

async function handleDisable(row) {
  try {
    await ElMessageBox.confirm(`${tt("确认禁用策略「")}${row.strategyCode}」?`, tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await disablePurchasePriceStrategy(row.strategyId)
    ElMessage.success(tt('已禁用'))
    fetchData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('操作失败'))
  }
}

async function handleDownloadTemplate() {
  downloadingTemplate.value = true
  try {
    await downloadTemplateUtil(ENTITY)
    ElMessage.success(tt('模板下载成功'))
  } catch (e) {
    ElMessage.error(tt('模板下载失败'))
  } finally {
    downloadingTemplate.value = false
  }
}

async function handleExport() {
  try {
    await exportData(ENTITY)
    ElMessage.success(tt('导出成功'))
  } catch (e) {
    // 失败原因已由 exportUtil 统一提示
  }
}

function handleImport() {
  importDialogVisible.value = true
  importResult.value = null
  selectedImportFile.value = null
  if (uploadRef.value) uploadRef.value.clearFiles()
}

function handleFileChange(file) {
  selectedImportFile.value = file.raw
  importResult.value = null
}

async function confirmImport() {
  if (!selectedImportFile.value) {
    ElMessage.warning(tt('请先选择文件'))
    return
  }
  importing.value = true
  try {
    const result = await importData(ENTITY, selectedImportFile.value)
    importResult.value = result
    if (result.failCount === 0) {
      ElMessage.success(`${tt("导入成功：共 ")}${result.successCount}${tt(" 条记录")}`)
      importDialogVisible.value = false
      fetchData()
    } else {
      ElMessage.warning(`${tt("导入完成：成功 ")}${result.successCount}${tt(" 条，失败 ")}${result.failCount}${tt(" 条")}`)
    }
  } catch (err) {
    ElMessage.error(tt('导入失败：') + (err.message || tt('未知错误')))
  } finally {
    importing.value = false
  }
}

onMounted(fetchData)
useAutoRefresh(fetchData)
</script>

<style scoped>
.erp-page { padding: 16px; }
.erp-page.embedded { padding: 12px; }
.erp-search-bar { margin-bottom: 16px; display: flex; gap: 8px; flex-wrap: wrap; }
.strategy-name-link { color: #6366f1; cursor: pointer; font-weight: 500; }
.strategy-name-link:hover { color: #8b5cf6; text-decoration: underline; }
/* 导入弹窗样式（类名与销售订单列表页一致；本页为纯 CSS，故用扁平选择器而非 SCSS 嵌套） */
.import-dialog-content .import-step { padding: 8px 0; }
.import-dialog-content .import-step .step-title { display: flex; align-items: center; gap: 8px; font-size: 15px; font-weight: 600; color: #303133; margin-bottom: 8px; }
.import-dialog-content .import-step .step-title .el-icon { font-size: 18px; color: #6366f1; }
.import-dialog-content .import-step .step-desc { font-size: 13px; color: #909399; margin-bottom: 12px; }
.import-dialog-content .import-uploader :deep(.el-upload-dragger) { width: 100%; height: 140px; border-radius: 8px; }
.import-dialog-content .import-result .result-title { font-size: 15px; font-weight: 600; color: #303133; margin-bottom: 12px; }
.import-dialog-content .import-result .import-error-alert { margin-top: 12px; }
.import-dialog-content .import-result .import-error-alert .error-list { max-height: 160px; overflow-y: auto; }
.import-dialog-content .import-result .import-error-alert .error-list .error-item { font-size: 12px; padding: 2px 0; color: #F56C6C; }
.import-dialog-content .import-result .import-error-alert .error-list .error-more { font-size: 12px; color: #909399; padding-top: 4px; }
</style>
