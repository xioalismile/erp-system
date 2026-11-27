<template>
  <div class="erp-page dict-page">
    <div class="dict-layout">
      <!-- 左侧：字典类型 -->
      <div class="dict-left">
        <div class="filter-bar">
          <el-input
            v-model="typeQuery.keyword"
            :placeholder="$tt('字典类型编码/名称')"
            class="filter-input"
            clearable
            @keyup.enter="onTypeSearch"
          />
          <el-button v-permission="perm('query')" type="primary" @click="onTypeSearch">{{ $tt("查询") }}</el-button>
          <el-button v-permission="perm('reset')" @click="onTypeReset">{{ $tt("重置") }}</el-button>
          <el-button v-permission="perm('add')" type="success" @click="onTypeAdd">{{ $tt("新增") }}</el-button>
        </div>

        <el-table
          :data="typeTableData"
          border
          highlight-current-row
          v-loading="typeLoading"
          @row-click="onTypeRowClick"
          height="500"
        >
          <el-table-column prop="dictTypeCode" :label="$tf('类型编码')" min-width="130" />
          <el-table-column prop="dictTypeName" :label="$tf('类型名称')" min-width="140" />
          <el-table-column prop="dictCategory" :label="$tf('分类')" width="100" />
          <el-table-column prop="sortOrder" :label="$tf('排序')" width="80" align="right" />
          <el-table-column :label="$tf('状态')" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">
                {{ $dt(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <AuditColumns />
        </el-table>

        <el-pagination
          class="pagination"
          v-model:current-page="typeQuery.current"
          v-model:page-size="typeQuery.size"
          :total="typeTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, prev, pager, next"
          @size-change="loadTypeData"
          @current-change="loadTypeData"
        />
      </div>

      <!-- 右侧：字典数据 -->
      <div class="dict-right">
        <div class="filter-bar">
          <span class="dict-right-title">
            {{ currentType.dictTypeName ? `字典数据 - ${currentType.dictTypeName}` : '请选择左侧字典类型' }}
          </span>
          <el-button v-permission="perm('addRow')"
            type="success"
            :disabled="!currentType.typeId"
            @click="onDataAdd"
          >{{ $tt("新增数据") }}</el-button>
        </div>

        <el-table :data="dataTableData" border v-loading="dataLoading" height="500" @sort-change="onSortChange">
          <el-table-column prop="dictLabel" :label="$tf('字典标签')" min-width="140" sortable="custom" />
          <el-table-column prop="dictValue" :label="$tf('字典值')" min-width="140" sortable="custom" />
          <el-table-column prop="dictSort" :label="$tf('排序')" width="80" align="right" sortable="custom" />
          <el-table-column :label="$tf('默认')" width="80" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.isDefault" type="success" size="small">{{ $tt("默认") }}</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column :label="$tf('启用')" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.isEnabled ? 'success' : 'info'" size="small">
                {{ row.isEnabled ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <template #empty>
            <span>{{ $tt("请先选择左侧字典类型") }}</span>
          </template>
        </el-table>
      </div>
    </div>

    <!-- 字典类型 新增/编辑弹窗 -->
    <el-dialog
      :title="typeIsEdit ? '编辑字典类型' : '新增字典类型'"
      v-model="typeDialogVisible"
      width="560px"
      @close="onTypeDialogClose"
    >
      <el-form :model="typeForm" :rules="typeRules" ref="typeFormRef" label-width="100px">
        <el-form-item :label="$tf('类型编码')" prop="dictTypeCode">
          <el-input v-model="typeForm.dictTypeCode" />
        </el-form-item>
        <el-form-item :label="$tf('类型名称')" prop="dictTypeName">
          <el-input v-model="typeForm.dictTypeName" />
        </el-form-item>
        <el-form-item :label="$tf('分类')" prop="dictCategory">
          <el-input v-model="typeForm.dictCategory" :placeholder="$tt('如 系统级、业务级')" />
        </el-form-item>
        <el-form-item :label="$tf('排序')" prop="sortOrder">
          <el-input-number v-model="typeForm.sortOrder" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item :label="$tf('状态')" prop="status">
          <el-select v-model="typeForm.status" :placeholder="$tt('请选择')" style="width:100%">
            <el-option :label="$tf('启用')" value="启用" />
            <el-option :label="$tf('停用')" value="停用" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="typeDialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button v-permission="perm('save')" type="primary" @click="onTypeSubmit">{{ $tt("保存") }}</el-button>
      </template>
    </el-dialog>

    <!-- 字典数据 新增/编辑弹窗 -->
    <el-dialog
      :title="dataIsEdit ? '编辑字典数据' : '新增字典数据'"
      v-model="dataDialogVisible"
      width="520px"
      @close="onDataDialogClose"
    >
      <el-form :model="dataForm" :rules="dataRules" ref="dataFormRef" label-width="100px">
        <el-form-item :label="$tf('字典标签')" prop="dictLabel">
          <el-input v-model="dataForm.dictLabel" />
        </el-form-item>
        <el-form-item :label="$tf('字典值')" prop="dictValue">
          <el-input v-model="dataForm.dictValue" />
        </el-form-item>
        <el-form-item :label="$tf('排序')" prop="dictSort">
          <el-input-number v-model="dataForm.dictSort" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item :label="$tf('是否默认')" prop="isDefault">
          <el-switch v-model="dataForm.isDefault" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item :label="$tf('是否启用')" prop="isEnabled">
          <el-switch v-model="dataForm.isEnabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="dataDialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button v-permission="perm('save')" type="primary" @click="onDataSubmit">{{ $tt("保存") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { usePagePerm } from '@/utils/pagePerm'
const perm = usePagePerm('system:sys-param')
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  pageDictType, createDictType, updateDictType,
  listDictData, createDictData, updateDictData
} from '@/api/system/dict'
import AuditColumns from '@/components/AuditColumns.vue'
import { useTableSort } from '@/composables/useTableSort'
import { tt } from '@/i18n'
const { handleSortChange, getSortParams } = useTableSort()


const onSortChange = (e) => { handleSortChange(e); loadTypeData() }// ===== 字典类型 =====
const typeLoading = ref(false)
const typeTableData = ref([])
const typeTotal = ref(0)
const typeQuery = reactive({
  current: 1,
  size: 20,
  keyword: ''})

const typeDialogVisible = ref(false)
const typeIsEdit = ref(false)
const typeFormRef = ref(null)
const typeDefaultForm = {
  typeId: null,
  dictTypeCode: '',
  dictTypeName: '',
  dictCategory: '',
  sortOrder: 0,
  status: '启用'
}
const typeForm = reactive({ ...typeDefaultForm })

const typeRules = {
  dictTypeCode: [{ required: true, message: tt('请输入类型编码'), trigger: 'blur' }],
  dictTypeName: [{ required: true, message: tt('请输入类型名称'), trigger: 'blur' }]
}

async function loadTypeData() {
  typeLoading.value = true
  try {
    const res = await pageDictType({ ...typeQuery, ...getSortParams() })
    typeTableData.value = res.records || []
    typeTotal.value = res.total || 0
  } finally {
    typeLoading.value = false
  }
}

function onTypeSearch() {
  typeQuery.current = 1
  loadTypeData()
}

function onTypeReset() {
  typeQuery.keyword = ''
  typeQuery.current = 1
  loadTypeData()
}

function onTypeAdd() {
  typeIsEdit.value = false
  Object.assign(typeForm, typeDefaultForm)
  typeDialogVisible.value = true
}

function onTypeDialogClose() {
  typeFormRef.value && typeFormRef.value.resetFields()
  Object.assign(typeForm, typeDefaultForm)
}

async function onTypeSubmit() {
  if (!typeFormRef.value) return
  await typeFormRef.value.validate(async (valid) => {
    if (!valid) return
    if (typeIsEdit.value) {
      await updateDictType(typeForm.typeId, typeForm)
      ElMessage.success(tt('编辑成功'))
    } else {
      await createDictType(typeForm)
      ElMessage.success(tt('新增成功'))
    }
    typeDialogVisible.value = false
    loadTypeData()
  })
}

// ===== 字典数据 =====
const dataLoading = ref(false)
const dataTableData = ref([])
const currentType = reactive({ typeId: null, dictTypeName: '' })

const dataDialogVisible = ref(false)
const dataIsEdit = ref(false)
const dataFormRef = ref(null)
const dataDefaultForm = {
  dataId: null,
  typeId: null,
  dictLabel: '',
  dictValue: '',
  dictSort: 0,
  isDefault: 0,
  isEnabled: 1
}
const dataForm = reactive({ ...dataDefaultForm })

const dataRules = {
  dictLabel: [{ required: true, message: tt('请输入字典标签'), trigger: 'blur' }],
  dictValue: [{ required: true, message: tt('请输入字典值'), trigger: 'blur' }]
}

async function loadDictData(typeId) {
  dataLoading.value = true
  try {
    const res = await listDictData(typeId)
    dataTableData.value = Array.isArray(res) ? res : (res.records || [])
  } finally {
    dataLoading.value = false
  }
}

function onTypeRowClick(row) {
  Object.assign(currentType, { typeId: row.dictTypeId, dictTypeName: row.dictTypeName })
  loadDictData(row.dictTypeId)
}

function onDataAdd() {
  if (!currentType.typeId) {
    ElMessage.warning(tt('请先选择字典类型'))
    return
  }
  dataIsEdit.value = false
  Object.assign(dataForm, dataDefaultForm, { typeId: currentType.typeId })
  dataDialogVisible.value = true
}

function onDataDialogClose() {
  dataFormRef.value && dataFormRef.value.resetFields()
  Object.assign(dataForm, dataDefaultForm)
}

async function onDataSubmit() {
  if (!dataFormRef.value) return
  await dataFormRef.value.validate(async (valid) => {
    if (!valid) return
    if (dataIsEdit.value) {
      await updateDictData(dataForm.dataId, dataForm)
      ElMessage.success(tt('编辑成功'))
    } else {
      await createDictData(dataForm)
      ElMessage.success(tt('新增成功'))
    }
    dataDialogVisible.value = false
    loadDictData(currentType.typeId)
  })
}

onMounted(loadTypeData)
useAutoRefresh(loadTypeData)
</script>

<style scoped>
.dict-page {
  padding: 0;
}
.dict-layout {
  display: flex;
  gap: 16px;
}
.dict-left {
  flex: 1;
  min-width: 0;
}
.dict-right {
  flex: 1;
  min-width: 0;
}
.filter-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
  align-items: center;
}
.filter-input {
  width: 200px;
}
.dict-right-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-right: auto;
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
