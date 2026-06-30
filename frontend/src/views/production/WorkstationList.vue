<template>
  <div class="erp-page">
    <div class="filter-bar">
      <el-input v-model="query.keyword" placeholder="编码/名称" class="filter-input" clearable @keyup.enter="loadData" />
      <el-select v-model="query.workCenterId" placeholder="工作中心" clearable class="filter-select">
        <el-option
          v-for="wc in workCenterOptions"
          :key="wc.workCenterId"
          :label="wc.workCenterName"
          :value="wc.workCenterId"
        />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" clearable class="filter-select">
        <el-option label="启用" value="启用" />
        <el-option label="停用" value="停用" />
      </el-select>
      <el-button type="primary" @click="onSearch">查询</el-button>
      <el-button @click="onReset">重置</el-button>
      <el-button type="success" @click="onAdd">新增</el-button>
    </div>

    <el-table :data="tableData" border v-loading="loading">
      <el-table-column prop="workstationCode" label="工序编码" min-width="130" />
      <el-table-column prop="workstationName" label="工序名称" min-width="140" />
      <el-table-column label="所属工作中心" min-width="140">
        <template #default="{ row }">
          {{ workCenterNameMap[row.workCenterId] || row.workCenterId || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="workstationType" label="工序类型" min-width="110" />
      <el-table-column prop="location" label="位置" min-width="120" />
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

    <el-dialog :title="isEdit ? '编辑工序' : '新增工序'" v-model="dialogVisible" width="600px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
        <el-form-item label="工序编码" prop="workstationCode">
          <el-input v-model="form.workstationCode" placeholder="如 WS001" />
        </el-form-item>
        <el-form-item label="工序名称" prop="workstationName">
          <el-input v-model="form.workstationName" placeholder="如 装配工序" />
        </el-form-item>
        <el-form-item label="所属工作中心" prop="workCenterId">
          <el-select v-model="form.workCenterId" placeholder="请选择" clearable class="form-select">
            <el-option
              v-for="wc in workCenterOptions"
              :key="wc.workCenterId"
              :label="wc.workCenterName"
              :value="wc.workCenterId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="工序类型" prop="workstationType">
          <el-input v-model="form.workstationType" placeholder="如 加工/装配/检验" />
        </el-form-item>
        <el-form-item label="位置" prop="location">
          <el-input v-model="form.location" placeholder="如 A厂区1号工位" />
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
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listWorkstation, saveWorkstation, updateWorkstation, deleteWorkstation
} from '@/api/production/workstation'
import { listAllWorkCenter } from '@/api/production/workCenter'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({
  current: 1,
  size: 20,
  keyword: '',
  status: '',
  workCenterId: null
})

const workCenterOptions = ref([])
const workCenterNameMap = computed(() => {
  const map = {}
  for (const wc of workCenterOptions.value) {
    map[wc.workCenterId] = wc.workCenterName
  }
  return map
})

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const form = reactive({
  workstationId: null,
  workstationCode: '',
  workstationName: '',
  workCenterId: null,
  workstationType: '',
  location: '',
  status: '启用'
})

const rules = {
  workstationCode: [{ required: true, message: '请输入工序编码', trigger: 'blur' }],
  workstationName: [{ required: true, message: '请输入工序名称', trigger: 'blur' }]
}

async function loadData() {
  loading.value = true
  try {
    const res = await listWorkstation(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function loadWorkCenterOptions() {
  const res = await listAllWorkCenter()
  workCenterOptions.value = res || []
}

function onSearch() {
  query.current = 1
  loadData()
}

function onReset() {
  query.keyword = ''
  query.status = ''
  query.workCenterId = null
  query.current = 1
  loadData()
}

function resetForm() {
  Object.assign(form, {
    workstationId: null,
    workstationCode: '',
    workstationName: '',
    workCenterId: null,
    workstationType: '',
    location: '',
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
      await updateWorkstation(form.workstationId, form)
      ElMessage.success('编辑成功')
    } else {
      await saveWorkstation(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  })
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确定删除工序 [${row.workstationCode}] 吗？`, '提示', {
    type: 'warning'
  })
  await deleteWorkstation(row.workstationId)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(() => {
  loadWorkCenterOptions()
  loadData()
})
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
  width: 160px;
}
.form-select {
  width: 100%;
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
