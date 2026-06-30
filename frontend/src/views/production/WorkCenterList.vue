<template>
  <div class="erp-page">
    <div class="filter-bar">
      <el-input v-model="query.keyword" placeholder="编码/名称" class="filter-input" clearable @keyup.enter="loadData" />
      <el-select v-model="query.status" placeholder="状态" clearable class="filter-select">
        <el-option label="启用" value="启用" />
        <el-option label="停用" value="停用" />
      </el-select>
      <el-button type="primary" @click="onSearch">查询</el-button>
      <el-button @click="onReset">重置</el-button>
      <el-button type="success" @click="onAdd">新增</el-button>
    </div>

    <el-table :data="tableData" border v-loading="loading">
      <el-table-column prop="workCenterCode" label="工作中心编码" min-width="130" />
      <el-table-column prop="workCenterName" label="工作中心名称" min-width="140" />
      <el-table-column prop="capacityType" label="产能类型" min-width="110" />
      <el-table-column prop="capacityPerDay" label="日产能" min-width="110" />
      <el-table-column prop="efficiency" label="效率(%)" min-width="100" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="onEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pagination"
      v-model:current-page="query.current"
      v-model:page-size="query.size"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      @size-change="loadData"
      @current-change="loadData"
    />

    <el-dialog :title="isEdit ? '编辑工作中心' : '新增工作中心'" v-model="dialogVisible" width="640px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
        <el-form-item label="工作中心编码" prop="workCenterCode">
          <el-input v-model="form.workCenterCode" placeholder="如 WC001" />
        </el-form-item>
        <el-form-item label="工作中心名称" prop="workCenterName">
          <el-input v-model="form.workCenterName" placeholder="如 一车间装配中心" />
        </el-form-item>
        <el-form-item label="部门ID" prop="departmentId">
          <el-input-number v-model="form.departmentId" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="负责人ID" prop="managerId">
          <el-input-number v-model="form.managerId" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="位置" prop="location">
          <el-input v-model="form.location" placeholder="如 A厂区1号楼" />
        </el-form-item>
        <el-form-item label="产能类型" prop="capacityType">
          <el-select v-model="form.capacityType" placeholder="请选择">
            <el-option label="人工工时" value="人工工时" />
            <el-option label="机器工时" value="机器工时" />
          </el-select>
        </el-form-item>
        <el-form-item label="日产能" prop="capacityPerDay">
          <el-input-number v-model="form.capacityPerDay" :min="0" :precision="4" controls-position="right" />
        </el-form-item>
        <el-form-item label="效率(%)" prop="efficiency">
          <el-input-number v-model="form.efficiency" :min="0" :precision="4" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" placeholder="请选择">
            <el-option label="启用" value="启用" />
            <el-option label="停用" value="停用" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listWorkCenter, saveWorkCenter, updateWorkCenter, deleteWorkCenter
} from '@/api/production/workCenter'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({
  current: 1,
  size: 20,
  keyword: '',
  status: ''
})

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const form = reactive({
  workCenterId: null,
  workCenterCode: '',
  workCenterName: '',
  departmentId: null,
  managerId: null,
  location: '',
  capacityType: '人工工时',
  capacityPerDay: 0,
  efficiency: 100,
  status: '启用'
})

const rules = {
  workCenterCode: [{ required: true, message: '请输入工作中心编码', trigger: 'blur' }],
  workCenterName: [{ required: true, message: '请输入工作中心名称', trigger: 'blur' }]
}

async function loadData() {
  loading.value = true
  try {
    const res = await listWorkCenter(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.current = 1
  loadData()
}

function onReset() {
  query.keyword = ''
  query.status = ''
  query.current = 1
  loadData()
}

function resetForm() {
  Object.assign(form, {
    workCenterId: null,
    workCenterCode: '',
    workCenterName: '',
    departmentId: null,
    managerId: null,
    location: '',
    capacityType: '人工工时',
    capacityPerDay: 0,
    efficiency: 100,
    status: '启用'
  })
}

function onAdd() {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

function onEdit(row) {
  isEdit.value = true
  resetForm()
  Object.assign(form, row)
  dialogVisible.value = true
}

async function onSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (isEdit.value) {
      await updateWorkCenter(form.workCenterId, form)
      ElMessage.success('编辑成功')
    } else {
      await saveWorkCenter(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  })
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确定删除工作中心 [${row.workCenterCode}] 吗？`, '提示', {
    type: 'warning'
  })
  await deleteWorkCenter(row.workCenterId)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.filter-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.filter-input {
  width: 200px;
}
.filter-select {
  width: 130px;
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
