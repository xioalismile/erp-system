<template>
  <div class="erp-page">
    <CrudTable ref="crudTableRef"
      title="BOM"
      custom-field-entity="bom"
      :columns="columns"
      :fetch-data="fetchData"
      :on-save="onSave"
      :on-delete="onDelete"
      :on-batch-delete="onBatchDelete"
      :search-fields="searchFields"
      :form-fields="formFields"
      id-field="bomId"
      entity-name="bom"
      add-route="/masterdata/bom/new"
      edit-route="/masterdata/bom"
      :hide-buttons="['import']"
      print-bill-type="BOM"
    >
      <template #toolbar-extra>
        <el-button v-permission="perm('import')" type="warning" @click="showBomImportDialog">
          <el-icon><Upload /></el-icon> BOM导入
        </el-button>
      </template>
      <template #bomCategorySlot="{ row }">
        <span>{{ row.bomCategory }}</span>
      </template>
      <template #isDefaultSlot="{ row }">
        <el-tag v-if="row && row.isDefault === 1" type="success" size="small">{{ $tt("是") }}</el-tag>
        <span v-else style="color:#c0c4cc;">{{ $tt("否") }}</span>
      </template>
      <template #isEnabledSlot="{ row }">
        <el-tag v-if="row && row.isEnabled === 1" type="success" size="small">{{ $tt("启用") }}</el-tag>
        <el-tag v-else type="info" size="small">{{ $tt("禁用") }}</el-tag>
      </template>
      <template #statusSlot="{ row }">
        <el-tag :type="statusType(row.status)" size="small">{{ $dt(row.status) }}</el-tag>
      </template>
    </CrudTable>

    <!-- BOM导入弹窗 -->
    <BomImportDialog v-model="bomImportVisible" @success="refreshTable" />
  </div>
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
useAutoRefresh(() => crudTableRef.value?.loadData?.())
import CrudTable from '@/components/CrudTable.vue'
import BomImportDialog from '@/components/BomImportDialog.vue'
import { ref } from 'vue'
import { Upload } from '@element-plus/icons-vue'
import { api } from '@/api'
import { batchDeleteBom } from '@/api/basedata/bom'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('masterdata:bom')

const bomImportVisible = ref(false)
const crudTableRef = ref()

function showBomImportDialog() {
  bomImportVisible.value = true
}

function refreshTable() {
  // 刷新列表数据
  if (crudTableRef.value) {
    crudTableRef.value.loadData()
  }
}

const searchFields = [
  { prop: 'keyword', label: '单据编号/母件', placeholder: '单据编号/母件编号/名称' }
]

const columns = [
  { prop: 'bomCode', label: '单据编号', width: 180 },
  { prop: 'bomDate', label: '单据日期', width: 120 },
  { prop: 'bomCategory', label: 'BOM分类', width: 110, slot: 'bomCategorySlot' },
  { prop: 'materialCode', label: '母件编号', width: 130 },
  { prop: 'materialName', label: '母件名称', minWidth: 140 },
  { prop: 'specification', label: '规格', width: 120 },
  { prop: 'bomVersion', label: '版本', width: 90 },
  { prop: 'quantity', label: '数量', width: 90 },
  { prop: 'isDefault', label: '默认BOM', width: 90, slot: 'isDefaultSlot' },
  { prop: 'isEnabled', label: '启用状态', width: 90, slot: 'isEnabledSlot' },
  { prop: 'status', label: '单据状态', width: 100, slot: 'statusSlot' }
]

const formFields = [
  { prop: 'bomCode', label: '单据编号', required: true },
  { prop: 'bomDate', label: '单据日期', type: 'date' },
  { prop: 'bomCategory', label: 'BOM分类', type: 'select', options: [
    { label: '标准BOM', value: '标准BOM' },
    { label: '工程BOM', value: '工程BOM' },
    { label: '生产BOM', value: '生产BOM' },
    { label: '成本BOM', value: '成本BOM' },
    { label: '委外BOM', value: '委外BOM' }
  ]},
  { prop: 'materialCode', label: '母件编号', required: true },
  { prop: 'materialName', label: '母件名称' },
  { prop: 'specification', label: '规格' },
  { prop: 'bomVersion', label: '版本' },
  { prop: 'quantity', label: '数量', type: 'number', precision: 2 },
  { prop: 'isDefault', label: '默认BOM', type: 'switch' },
  { prop: 'isEnabled', label: '启用状态', type: 'switch' }
]

function statusType(s) {
  return { '草稿': 'info', '待审批': 'warning', '已生效': 'success', '已停用': 'danger' }[s] || 'info'
}

async function fetchData(params) {
  const res = await api.get('/masterdata/bom/page', { params })
  return { records: res.records || [], total: res.total || 0 }
}

async function onSave(data, isEdit) {
  // BOM使用独立编辑页面，这里不需要实现
}

async function onDelete(row) {
  await api.delete(`/masterdata/bom/${row.bomId}`)
}
/** 批量删除（2026-09-20 接入：CrudTable 一次请求批量删，替代原先逐条循环） */
async function onBatchDelete(rows) {
  await batchDeleteBom(rows.map(r => r.bomId))
}
</script>