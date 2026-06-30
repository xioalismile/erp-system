<template>
  <div class="erp-page dict-page">
    <div class="dict-layout">
      <!-- 左侧：字典类型 -->
      <div class="dict-left">
        <div class="filter-bar">
          <el-input
            v-model="typeQuery.keyword"
            placeholder="字典类型编码/名称"
            class="filter-input"
            clearable
            @keyup.enter="onTypeSearch"
          />
          <el-button type="primary" @click="onTypeSearch">查询</el-button>
          <el-button @click="onTypeReset">重置</el-button>
          <el-button type="success" @click="onTypeAdd">新增</el-button>
        </div>

        <el-table
          :data="typeTableData"
          border
          highlight-current-row
          v-loading="typeLoading"
          @row-click="onTypeRowClick"
          height="500"
        >
          <el-table-column prop="dictTypeCode" label="类型编码" min-width="130" />
          <el-table-column prop="dictTypeName" label="类型名称" min-width="140" />
          <el-table-column prop="dictCategory" label="分类" width="100" />
          <el-table-column prop="sortOrder" label="排序" width="80" align="right" />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click.stop="onTypeEdit(row)">编辑</el-button>
              <el-button size="small" type="danger" @click.stop="onTypeDelete(row)">删除</el-button>
            </template>
          </el-table-column>
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
          <el-button
            type="success"
            :disabled="!currentType.typeId"
            @click="onDataAdd"
          >新增数据</el-button>
        </div>

        <el-table :data="dataTableData" border v-loading="dataLoading" height="500">
          <el-table-column prop="dictLabel" label="字典标签" min-width="140" />
          <el-table-column prop="dictValue" label="字典值" min-width="140" />
          <el-table-column prop="dictSort" label="排序" width="80" align="right" />
          <el-table-column label="默认" width="80" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.isDefault" type="success" size="small">默认</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="启用" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.isEnabled ? 'success' : 'info'" size="small">
                {{ row.isEnabled ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="onDataEdit(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="onDataDelete(row)">删除</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <span>请先选择左侧字典类型</span>
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
        <el-form-item label="类型编码" prop="dictTypeCode">
          <el-input v-model="typeForm.dictTypeCode" />
        </el-form-item>
        <el-form-item label="类型名称" prop="dictTypeName">
          <el-input v-model="typeForm.dictTypeName" />
        </el-form-item>
        <el-form-item label="分类" prop="dictCategory">
          <el-input v-model="typeForm.dictCategory" placeholder="如 系统级、业务级" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="typeForm.sortOrder" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="typeForm.status" placeholder="请选择" style="width:100%">
            <el-option label="启用" value="启用" />
            <el-option label="停用" value="停用" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onTypeSubmit">保存</el-button>
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
        <el-form-item label="字典标签" prop="dictLabel">
          <el-input v-model="dataForm.dictLabel" />
        </el-form-item>
        <el-form-item label="字典值" prop="dictValue">
          <el-input v-model="dataForm.dictValue" />
        </el-form-item>
        <el-form-item label="排序" prop="dictSort">
          <el-input-number v-model="dataForm.dictSort" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="是否默认" prop="isDefault">
          <el-switch v-model="dataForm.isDefault" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="是否启用" prop="isEnabled">
          <el-switch v-model="dataForm.isEnabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dataDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onDataSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageDictType, getDictType, createDictType, updateDictType, deleteDictType,
  listDictData, createDictData, updateDictData, deleteDictData
} from '@/api/system/dict'

// ===== 字典类型 =====
const typeLoading = ref(false)
const typeTableData = ref([])
const typeTotal = ref(0)
const typeQuery = reactive({
  current: 1,
  size: 20,
  keyword: ''
})

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
  dictTypeCode: [{ required: true, message: '请输入类型编码', trigger: 'blur' }],
  dictTypeName: [{ required: true, message: '请输入类型名称', trigger: 'blur' }]
}

async function loadTypeData() {
  typeLoading.value = true
  try {
    const res = await pageDictType(typeQuery)
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

async function onTypeEdit(row) {
  typeIsEdit.value = true
  Object.assign(typeForm, typeDefaultForm)
  try {
    const detail = await getDictType(row.typeId)
    Object.assign(typeForm, detail)
  } catch (e) {
    Object.assign(typeForm, row)
  }
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
      ElMessage.success('编辑成功')
    } else {
      await createDictType(typeForm)
      ElMessage.success('新增成功')
    }
    typeDialogVisible.value = false
    loadTypeData()
  })
}

async function onTypeDelete(row) {
  await ElMessageBox.confirm(`确定删除字典类型 [${row.dictTypeName}] 及其所有数据吗？`, '提示', {
    type: 'warning'
  })
  await deleteDictType(row.typeId)
  ElMessage.success('删除成功')
  // 若删除的是当前选中的类型，清空右侧
  if (currentType.typeId === row.typeId) {
    Object.assign(currentType, { typeId: null, dictTypeName: '' })
    dataTableData.value = []
  }
  loadTypeData()
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
  dictLabel: [{ required: true, message: '请输入字典标签', trigger: 'blur' }],
  dictValue: [{ required: true, message: '请输入字典值', trigger: 'blur' }]
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
  Object.assign(currentType, { typeId: row.typeId, dictTypeName: row.dictTypeName })
  loadDictData(row.typeId)
}

function onDataAdd() {
  if (!currentType.typeId) {
    ElMessage.warning('请先选择字典类型')
    return
  }
  dataIsEdit.value = false
  Object.assign(dataForm, dataDefaultForm, { typeId: currentType.typeId })
  dataDialogVisible.value = true
}

async function onDataEdit(row) {
  dataIsEdit.value = true
  Object.assign(dataForm, dataDefaultForm, row)
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
      ElMessage.success('编辑成功')
    } else {
      await createDictData(dataForm)
      ElMessage.success('新增成功')
    }
    dataDialogVisible.value = false
    loadDictData(currentType.typeId)
  })
}

async function onDataDelete(row) {
  await ElMessageBox.confirm(`确定删除字典数据 [${row.dictLabel}] 吗？`, '提示', {
    type: 'warning'
  })
  await deleteDictData(row.dataId)
  ElMessage.success('删除成功')
  loadDictData(currentType.typeId)
}

onMounted(loadTypeData)
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
