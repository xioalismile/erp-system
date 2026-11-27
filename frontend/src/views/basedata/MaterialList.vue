<template>
  <div class="erp-page">
    <CrudTable ref="tableRef"
      :hide-buttons="['import']"
      print-bill-type="MATERIAL"
      :title="$tt('物料')"
      custom-field-entity="material"
      :columns="columns"
      :fetch-data="fetchData"
      :on-save="onSave"
      :on-delete="onDelete"
      :on-batch-delete-check="onBatchDeleteCheck"
      :on-batch-delete="onBatchDelete"
      :search-fields="searchFields"
      :form-fields="formFields"
      id-field="materialId"
      perm-add="basedata:material:add"
      perm-edit="basedata:material:edit"
      perm-delete="basedata:material:delete"
      add-route="/basedata/material/new"
      edit-route="/basedata/material/edit"
      entity-name="material"
    >
      <template #toolbar-extra>
        <el-button type="warning" v-permission="perm('import')" @click="showMaterialImportDialog">
          <el-icon><Upload /></el-icon> 物料导入
        </el-button>
        <!-- 批量打印标签：多选物料 → 份数 + 连排 + 条码/二维码双打（2026-09-27） -->
        <el-button type="primary" v-permission="perm('print')" @click="openBatchLabelPrint">
          <el-icon><Printer /></el-icon> 批量打印标签
        </el-button>
      </template>
      <!-- 图片列：放于物料编码前，无图片显示占位，有图片可点击放大 -->
      <template #imageSlot="{ row }">
        <div class="img-cell" @click="previewImage(row)">
          <el-image
            v-if="getMainImage(row)"
            :src="getMainImage(row)"
            fit="cover"
            class="thumb"
            :preview-src-list="getAllImages(row)"
            :initial-index="0"
            preview-teleported
            hide-on-click-modal
            :preview-teleported="true"
            @click.stop
          />
          <div v-else class="img-placeholder">
            <el-icon><Picture /></el-icon>
            <span>{{ $tt("暂无") }}</span>
          </div>
        </div>
      </template>
      <template #statusSlot="{ row }">
        <el-tag :type="row.status === '正常' ? 'success' : 'info'" size="small">{{ $dt(row.status) }}</el-tag>
      </template>
    </CrudTable>

    <!-- 物料导入弹窗（已存在检测 + 覆盖/跳过） -->
    <MaterialImportDialog v-model="materialImportVisible" @success="refreshTable" />

    <!-- 批量打印标签（商品二维码 + 一维条码） -->
    <BatchLabelPrintDialog v-model="labelDialogVisible" type="MATERIAL" :ids="labelIds" />

    <!-- 图片放大弹窗（备用：当 el-image 预览不生效时） -->
    <el-dialog v-model="bigImageVisible" :title="$tf('物料图片')" width="600px" append-to-body>
      <div style="text-align:center;">
        <img :src="bigImageUrl" alt="物料图片" style="max-width:100%;max-height:500px;" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
const tableRef = ref(null)
useAutoRefresh(() => tableRef.value?.loadData?.())
import CrudTable from '@/components/CrudTable.vue'
import MaterialImportDialog from '@/components/MaterialImportDialog.vue'
import { listMaterial, saveMaterial, updateMaterial, deleteMaterial, batchDeleteMaterial, checkBatchDeleteMaterial } from '@/api/basedata/material'
import { Picture, Upload, Printer } from '@element-plus/icons-vue'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { tt } from '@/i18n'
import BatchLabelPrintDialog from '@/components/BatchLabelPrintDialog.vue'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm()

// 批量打印标签（商品二维码 + 一维条码，2026-09-27）
const labelDialogVisible = ref(false)
const labelIds = ref([])
function openBatchLabelPrint() {
  const rows = tableRef.value?.selectedRows || []
  if (!rows.length) {
    ElMessage.warning(tt('请先勾选要打印的物料'))
    return
  }
  if (rows.length > 200) {
    ElMessage.warning(tt('一次最多打印 200 项，请分批打印'))
    return
  }
  labelIds.value = rows.map(r => r.materialId).filter(v => v != null)
  labelDialogVisible.value = true
}

// 物料导入弹窗（已存在检测 + 覆盖/跳过）
const materialImportVisible = ref(false)
function showMaterialImportDialog() {
  materialImportVisible.value = true
}
function refreshTable() {
  tableRef.value?.loadData?.()
}

const searchFields = [
  { prop: 'keyword', label: '关键字', placeholder: '编码/名称/全称' }
]

const columns = [
  // 图片列放于物料编码之前
  { prop: 'mainImage', label: '图片', width: 80, slot: 'imageSlot', noLink: true },
  { prop: 'materialCode', label: '物料编码', minWidth: 140 },
  { prop: 'materialName', label: '物料名称', minWidth: 160 },
  { prop: 'model', label: '规格型号', minWidth: 140 },
  { prop: 'materialType', label: '物料类型', minWidth: 120 },
  { prop: 'salePrice', label: '销售单价', minWidth: 120 },
  { prop: 'purchasePrice', label: '采购单价', minWidth: 120 },
  // 2026-11-07：物料日产能（件/天），参与 APS 排程
  { prop: 'dailyCapacity', label: '日产能', minWidth: 110 },
  { prop: 'status', label: '状态', width: 100, slot: 'statusSlot' },
  { prop: 'remark', label: '备注', minWidth: 150, showOverflowTooltip: true }
]

// 校验是否为有效图片URL（过滤 [object Object] 等脏数据）
const isValidImageUrl = (url) => {
  if (!url || typeof url !== 'string') return false
  const u = url.trim()
  return u.startsWith('/uploads/') || u.startsWith('http://') || u.startsWith('https://') || u.startsWith('data:image/')
}

// 解析 imageUrls 字段为有效URL数组（过滤无效值）
const parseValidUrls = (imageUrls) => {
  if (!imageUrls) return []
  const urls = typeof imageUrls === 'string' ? imageUrls.split(',').filter(Boolean) : imageUrls
  return Array.isArray(urls) ? urls.filter(isValidImageUrl) : []
}

// 获取主图URL：优先 mainImage，其次 imageUrls 第一张有效URL
const getMainImage = (row) => {
  if (isValidImageUrl(row.mainImage)) return row.mainImage
  const urls = parseValidUrls(row.imageUrls)
  return urls.length > 0 ? urls[0] : ''
}

// 获取所有图片URL（用于预览时翻页，过滤无效URL）
const getAllImages = (row) => {
  const list = []
  if (isValidImageUrl(row.mainImage)) list.push(row.mainImage)
  parseValidUrls(row.imageUrls).forEach(u => { if (!list.includes(u)) list.push(u) })
  return list
}

// 备用放大弹窗
const bigImageVisible = ref(false)
const bigImageUrl = ref('')
const previewImage = (row) => {
  const url = getMainImage(row)
  if (!url) return
  bigImageUrl.value = url
  bigImageVisible.value = true
}

const formFields = [
  { prop: 'materialCode', label: '物料编码', required: true, placeholder: '如 M001' },
  { prop: 'materialName', label: '物料名称', required: true, placeholder: '如 螺丝' }
]

async function fetchData(params) {
  const res = await listMaterial(params)
  return { records: res.records, total: res.total }
}
async function onSave(data, isEdit) {
  if (isEdit) await updateMaterial(data)
  else await saveMaterial(data)
}
async function onDelete(row) {
  await deleteMaterial(row.materialId)
}

/**
 * 批量删除预检（只看不删）：把后端"可删/不可删"清单映射回表格行，交给通用组件弹窗展示。
 * 返回 { deletable: Row[], blocked: [{ id, label, reason }] }
 */
async function onBatchDeleteCheck(rows) {
  const ids = rows.map(r => r.materialId)
  const data = (await checkBatchDeleteMaterial(ids)) || {}
  const deletableIds = new Set(data.deletable || [])
  const rowOf = (id) => rows.find(r => r.materialId === id)
  return {
    deletable: rows.filter(r => deletableIds.has(r.materialId)),
    blocked: (data.blocked || []).map(b => {
      const row = rowOf(b.id)
      return {
        id: b.id,
        label: row ? [row.materialCode, row.materialName].filter(Boolean).join(' / ') : ('#' + b.id),
        reason: b.reason
      }
    })
  }
}

/** 批量删除执行：只传用户确认要删的那些行（后端仍会重新校验） */
async function onBatchDelete(rows) {
  await batchDeleteMaterial(rows.map(r => r.materialId))
}
</script>

<style scoped lang="scss">
/* 图片列样式 */
.img-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.thumb {
  width: 44px;
  height: 44px;
  border-radius: 6px;
  border: 1px solid #e5e7eb;
  object-fit: cover;
  transition: transform .2s, box-shadow .2s;
}
.thumb:hover {
  transform: scale(1.15);
  box-shadow: 0 4px 12px rgba(99, 102, 241, .25);
  border-color: #6366f1;
}
.img-placeholder {
  width: 44px;
  height: 44px;
  border-radius: 6px;
  border: 1px dashed #d1d5db;
  background: #f9fafb;
  color: #9ca3af;
  font-size: 10px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
}
.img-placeholder .el-icon {
  font-size: 14px;
}
</style>
