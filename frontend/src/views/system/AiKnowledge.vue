<!-- @feature FEAT-AIKB-001 -->
<template>
  <div class="ai-knowledge">
    <div class="page-header">
      <h2 class="page-title">{{ $tt("AI 知识库管理") }}</h2>
      <p class="page-desc">{{ $tt("上传业务文档（PDF/Word/Excel/txt/md），系统自动解析并向量化。AI助手处理请求时会自动检索相关知识，提升业务语义理解能力。") }}</p>
    </div>

    <el-card shadow="never" class="upload-card">
      <template #header>
        <div class="card-header">
          <span>{{ $tt("上传文档") }}</span>
          <el-tag size="small" type="info">{{ $tt("支持 pdf/docx/xlsx/txt/md，单文件≤20MB") }}</el-tag>
        </div>
      </template>
      <el-form :inline="true" :model="uploadForm">
        <el-form-item :label="$tf('文档标题')">
          <el-input v-model="uploadForm.title" :placeholder="$tt('留空则使用文件名')" style="width: 240px" />
        </el-form-item>
        <el-form-item :label="$tf('所属模块')">
          <el-select v-model="uploadForm.module" :placeholder="$tt('选择模块')" style="width: 160px">
            <el-option :label="$tf('通用')" value="general" />
            <el-option :label="$tf('销售')" value="sale" />
            <el-option :label="$tf('采购')" value="purchase" />
            <el-option :label="$tf('库存')" value="inventory" />
            <el-option :label="$tf('财务')" value="finance" />
            <el-option :label="$tf('生产')" value="production" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            accept=".pdf,.docx,.xlsx,.txt,.md"
            :file-list="fileList"
          >
            <el-button v-permission="perm('upload')" type="primary">{{ $tt("选择文件") }}</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item>
          <el-button v-permission="perm('upload')" type="success" :loading="uploading" @click="doUpload">{{ $tt("开始上传") }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="list-card">
      <template #header>
        <div class="card-header">
          <span>{{ $tt("文档列表") }}</span>
          <div style="display:flex;align-items:center;gap:8px;">
            <el-button v-permission="perm('refresh')" size="small" @click="loadList">{{ $tt("刷新") }}</el-button>
            <el-tooltip :content="$tt('列设置')" placement="top">
              <el-button :icon="Setting" circle size="small" @click="openColumnSetting" />
            </el-tooltip>
          </div>
        </div>
      </template>
      <el-table ref="tableRef" :data="docs" v-loading="loading" stripe border :key="configVersion" @header-dragend="onHeaderDragend">
        <!-- 动态列渲染：列顺序由列设置拖拽配置驱动，单元格按 col.type 分支渲染 -->
        <template v-for="col in visibleSortedColumns" :key="col.prop">
          <el-table-column
            :prop="col.prop"
            :label="$tf(col.label)"
            :width="col.width"
            :min-width="col.minWidth"
            :align="col.align || 'left'"
            :show-overflow-tooltip="!!col.tooltip"
            :fixed="getColFixed(col.prop)"
          >
            <template v-if="col.type" #default="{ row }">
              <el-tag v-if="col.type === 'module'" size="small">{{ moduleLabel(row.module) }}</el-tag>
              <template v-else-if="col.type === 'fileSize'">{{ fmtSize(row.fileSize) }}</template>
              <template v-else-if="col.type === 'status'">
                <el-tooltip v-if="row.status === 'failed' && row.errorMsg" :content="row.errorMsg" placement="top">
                  <el-tag :type="statusType(row.status)" size="small">{{ $dt(statusLabel(row.status)) }}</el-tag>
                </el-tooltip>
                <el-tag v-else :type="statusType(row.status)" size="small">{{ $dt(statusLabel(row.status)) }}</el-tag>
              </template>
            </template>
          </el-table-column>
        </template>
        <!-- 操作列保持静态（位于表格末尾，不入列设置） -->
        <el-table-column :label="$tf('操作')" width="130" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="perm('refresh')" v-if="row.status === 'failed'" type="primary" size="small" link @click="doRetry(row)">{{ $tt("重试") }}</el-button>
            <el-button v-permission="perm('delete')" type="danger" size="small" link @click="doDelete(row)">{{ $tt("删除") }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && docs.length === 0" :description="$tt('暂无文档，上传后AI助手将自动学习')" />
    </el-card>

    <!-- 列设置抽屉 -->
    <ColumnSettingDrawer v-model="columnSettingVisible" :page-key="'AiKnowledge'" :columns="columns" />
  </div>
</template>

<script setup>
import { usePagePerm } from '@/utils/pagePerm'
const perm = usePagePerm('system:ai-knowledge')
import { ref, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Setting } from '@element-plus/icons-vue'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { listKnowledgeDocs, uploadKnowledgeDoc, deleteKnowledgeDoc, retryKnowledgeDoc } from '@/api/ai/knowledge'
import { tt } from '@/i18n'

// 列定义（列设置 + 动态渲染用）：type 标记单元格渲染分支；操作列保持静态（位于表格末尾，不入列设置）
const columns = [
  { prop: 'title', label: '标题', minWidth: 200 },
  { prop: 'module', label: '模块', width: 100, type: 'module' },
  { prop: 'fileType', label: '类型', width: 80 },
  { prop: 'fileSize', label: '大小', width: 100, type: 'fileSize' },
  { prop: 'chunkCount', label: '切片数', width: 90 },
  { prop: 'status', label: '状态', width: 110, type: 'status' },
  { prop: 'createdAt', label: '上传时间', width: 170 }
]
const { columnSettingVisible, openColumnSetting, isColVisible, getColFixed, configVersion, sortedColumns, onHeaderDragend, applySavedWidths } = useColumnSetting('AiKnowledge', columns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))
// 列宽持久化：表格带 border 才允许拖拽，拖完的宽度写入本页列配置，下次打开自动套用
const tableRef = ref(null)

const docs = ref([])
const loading = ref(false)
const uploading = ref(false)
const fileList = ref([])
const selectedFile = ref(null)
const uploadForm = ref({ title: '', module: 'general' })

const moduleLabel = (m) => ({ general: '通用', sale: '销售', purchase: '采购', inventory: '库存', finance: '财务', production: '生产' }[m] || m)
const statusLabel = (s) => ({ parsing: '解析中', ready: '就绪', failed: '失败' }[s] || s)
const statusType = (s) => ({ parsing: 'warning', ready: 'success', failed: 'danger' }[s] || 'info')
const fmtSize = (b) => {
  if (!b) return '-'
  if (b < 1024) return b + ' B'
  if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB'
  return (b / 1024 / 1024).toFixed(1) + ' MB'
}

const handleFileChange = (file) => { selectedFile.value = file.raw }
const handleFileRemove = () => { selectedFile.value = null }

const loadList = async () => {
  loading.value = true
  try {
    const res = await listKnowledgeDocs()
    // 注意：request 响应拦截器已把 {code,message,data} 解包，res 就是文档列表本身
    docs.value = Array.isArray(res) ? res : []
  } finally {
    loading.value = false
    // 数据到位后再套一次已保存的列宽（表格重建后列对象是新的）
    nextTick(() => applySavedWidths(tableRef))
  }
}

const doUpload = async () => {
  if (!selectedFile.value) { ElMessage.warning(tt('请先选择文件')); return }
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', selectedFile.value)
    if (uploadForm.value.title) fd.append('title', uploadForm.value.title)
    fd.append('module', uploadForm.value.module)
    await uploadKnowledgeDoc(fd)
    ElMessage.success(tt('文档已提交，正在解析中...'))
    fileList.value = []
    selectedFile.value = null
    uploadForm.value = { title: '', module: 'general' }
    setTimeout(loadList, 1000)
  } catch (e) {
    ElMessage.error(e.message || tt('上传失败'))
  } finally {
    uploading.value = false
  }
}

const doDelete = async (row) => {
  await ElMessageBox.confirm(`${tt("确认删除文档「")}${row.title}」？`, tt('提示'), { type: 'warning' })
  await deleteKnowledgeDoc(row.id)
  ElMessage.success(tt('已删除'))
  loadList()
}

const doRetry = async (row) => {
  try {
    await retryKnowledgeDoc(row.id)
    ElMessage.success(tt('已重新提交解析'))
    setTimeout(loadList, 1000)
  } catch (e) {
    ElMessage.error(e.message || tt('重试失败'))
  }
}

onMounted(loadList)
</script>

<style scoped>
.ai-knowledge { padding: 20px; }
.page-header { margin-bottom: 16px; }
.page-title { margin: 0 0 6px; font-size: 20px; }
.page-desc { margin: 0; color: #909399; font-size: 13px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.upload-card, .list-card { margin-bottom: 16px; }
</style>
