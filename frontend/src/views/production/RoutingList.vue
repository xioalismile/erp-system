<template>
  <div class="erp-page">
    <el-card>
      <!-- 搜索栏 -->
      <div class="erp-search-bar">
        <el-input v-model="search.keyword" placeholder="工艺路线编码/名称" clearable style="width:220px" />
        <el-select v-model="search.status" placeholder="状态" clearable style="width:120px">
          <el-option label="已审核" value="已审核" />
          <el-option label="草稿" value="草稿" />
          <el-option label="已关闭" value="已关闭" />
        </el-select>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
        <el-button type="primary" @click="handleAdd">新增</el-button>
      </div>
      <!-- 表格 -->
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column prop="routingCode" label="工艺路线编码" width="170" />
        <el-table-column prop="routingName" label="工艺路线名称" min-width="180" />
        <el-table-column prop="routingVersion" label="版本" width="100" />
        <el-table-column prop="materialId" label="物料ID" width="100" />
        <el-table-column prop="totalWorkHours" label="总工时" width="120" align="right" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{row}">
            <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="制单人" width="100" />
        <el-table-column prop="createdAt" label="制单时间" width="160" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{row}">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="1400px" @close="resetForm">
      <el-form :model="form" label-width="110px" :rules="rules" ref="formRef">
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="工艺路线编码" prop="routingCode">
              <el-input v-model="form.routingCode" placeholder="留空自动生成" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="工艺路线名称" prop="routingName">
              <el-input v-model="form.routingName" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="版本">
              <el-input v-model="form.routingVersion" placeholder="如 V1.0" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="是否主工艺">
              <el-select v-model="form.isPrimary" style="width:100%">
                <el-option label="主工艺路线" :value="1" />
                <el-option label="替代工艺路线" :value="0" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="物料ID">
              <el-input-number v-model="form.materialId" :controls="false" style="width:100%" placeholder="物料ID" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="BOM ID">
              <el-input-number v-model="form.bomId" :controls="false" style="width:100%" placeholder="BOM ID" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="生效日期">
              <el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="失效日期">
              <el-date-picker v-model="form.expiryDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="总工时">
              <el-input-number v-model="form.totalWorkHours" :controls="false" :precision="4" disabled style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="18">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="1" />
            </el-form-item>
          </el-col>
        </el-row>
        <!-- 工序明细 -->
        <el-divider content-position="left">工序明细</el-divider>
        <el-button type="primary" size="small" @click="addOperation" style="margin-bottom:8px">添加工序</el-button>
        <el-table :data="form.operations" border size="small" style="width:100%">
          <el-table-column type="index" label="序号" width="55" fixed />
          <el-table-column label="工序编码" width="120">
            <template #default="{row}"><el-input v-model="row.operationCode" size="small" /></template>
          </el-table-column>
          <el-table-column label="工序名称" min-width="140">
            <template #default="{row}"><el-input v-model="row.operationName" size="small" /></template>
          </el-table-column>
          <el-table-column label="工作中心类型" width="130">
            <template #default="{row}"><el-input v-model="row.workstationType" size="small" /></template>
          </el-table-column>
          <el-table-column label="工位ID" width="100">
            <template #default="{row}"><el-input-number v-model="row.workstationId" :controls="false" size="small" /></template>
          </el-table-column>
          <el-table-column label="工作中心ID" width="110">
            <template #default="{row}"><el-input-number v-model="row.workCenterId" :controls="false" size="small" /></template>
          </el-table-column>
          <el-table-column label="准备工时" width="110">
            <template #default="{row}"><el-input-number v-model="row.setupHours" :controls="false" :precision="4" :min="0" size="small" @change="calcRow(row)" /></template>
          </el-table-column>
          <el-table-column label="加工工时" width="110">
            <template #default="{row}"><el-input-number v-model="row.processHours" :controls="false" :precision="4" :min="0" size="small" @change="calcRow(row)" /></template>
          </el-table-column>
          <el-table-column label="等待工时" width="110">
            <template #default="{row}"><el-input-number v-model="row.waitHours" :controls="false" :precision="4" :min="0" size="small" @change="calcRow(row)" /></template>
          </el-table-column>
          <el-table-column label="移动工时" width="110">
            <template #default="{row}"><el-input-number v-model="row.moveHours" :controls="false" :precision="4" :min="0" size="small" @change="calcRow(row)" /></template>
          </el-table-column>
          <el-table-column label="合计工时" width="100" align="right">
            <template #default="{row}"><span>{{ row.totalHours }}</span></template>
          </el-table-column>
          <el-table-column label="批量" width="90">
            <template #default="{row}"><el-input-number v-model="row.batchSize" :controls="false" :min="1" size="small" /></template>
          </el-table-column>
          <el-table-column label="人数" width="90">
            <template #default="{row}"><el-input-number v-model="row.workersRequired" :controls="false" :min="1" size="small" /></template>
          </el-table-column>
          <el-table-column label="需质检" width="80" align="center">
            <template #default="{row}">
              <el-switch v-model="row.isQcRequired" :active-value="1" :inactive-value="0" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="关键工序" width="90" align="center">
            <template #default="{row}">
              <el-switch v-model="row.isCritical" :active-value="1" :inactive-value="0" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="备注" width="140">
            <template #default="{row}"><el-input v-model="row.remark" size="small" /></template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{$index}">
              <el-button size="small" type="danger" @click="form.operations.splice($index,1); calcTotal()">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div style="text-align:right;margin-top:8px">
          <span>总工时合计: <b>{{ form.totalWorkHours }}</b></span>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listRouting, getRouting, saveRouting, updateRouting, deleteRouting
} from '@/api/production/routing'

const loading = ref(false)
const tableData = ref([])
const search = reactive({ keyword: '', status: '' })
const pagination = reactive({ current: 1, size: 20, total: 0 })

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const form = reactive({
  routingId: null, routingCode: '', routingName: '', routingVersion: '',
  materialId: null, bomId: null, isPrimary: 1, effectiveDate: null, expiryDate: null,
  totalWorkHours: 0, remark: '', operations: []
})
const rules = {
  routingName: [{ required: true, message: '请输入工艺路线名称', trigger: 'blur' }]
}

function statusTagType(s) {
  return { '草稿': 'info', '已审核': 'success', '已关闭': 'warning' }[s] || 'info'
}

async function fetchData() {
  loading.value = true
  try {
    const res = await listRouting({ current: pagination.current, size: pagination.size, ...search })
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (e) { ElMessage.error(e.message) }
  finally { loading.value = false }
}

function handleSearch() { pagination.current = 1; fetchData() }
function handleReset() { search.keyword = ''; search.status = ''; pagination.current = 1; fetchData() }

function handleAdd() {
  Object.assign(form, {
    routingId: null, routingCode: '', routingName: '', routingVersion: '',
    materialId: null, bomId: null, isPrimary: 1, effectiveDate: null, expiryDate: null,
    totalWorkHours: 0, remark: '', operations: []
  })
  dialogTitle.value = '新增工艺路线'
  dialogVisible.value = true
}

async function handleEdit(row) {
  const data = await getRouting(row.routingId)
  Object.assign(form, data)
  if (!form.operations) form.operations = []
  form.operations.forEach(op => {
    if (op.isQcRequired == null) op.isQcRequired = 0
    if (op.isCritical == null) op.isCritical = 0
  })
  dialogTitle.value = '编辑工艺路线'
  dialogVisible.value = true
}

function addOperation() {
  form.operations.push({
    operationCode: '', operationName: '', workstationType: '',
    workstationId: null, workCenterId: null,
    setupHours: 0, processHours: 0, waitHours: 0, moveHours: 0, totalHours: 0,
    batchSize: 1, workersRequired: 1, isQcRequired: 0, isCritical: 0, remark: ''
  })
  calcTotal()
}

function calcRow(row) {
  row.totalHours = (
    Number(row.setupHours || 0) +
    Number(row.processHours || 0) +
    Number(row.waitHours || 0) +
    Number(row.moveHours || 0)
  ).toFixed(4)
  calcTotal()
}

function calcTotal() {
  form.totalWorkHours = form.operations.reduce(
    (sum, r) => sum + Number(r.totalHours || 0), 0
  ).toFixed(4)
}

async function handleSave() {
  await formRef.value.validate()
  const payload = { ...form }
  if (form.routingId) {
    await updateRouting(form.routingId, payload)
  } else {
    await saveRouting(payload)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  fetchData()
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除工艺路线 ${row.routingCode}?`, '提示', { type: 'warning' })
  await deleteRouting(row.routingId)
  ElMessage.success('删除成功')
  fetchData()
}

function resetForm() {
  formRef.value && formRef.value.resetFields()
}

onMounted(fetchData)
</script>

<style scoped>
.erp-page { padding: 16px; }
.erp-search-bar { margin-bottom: 16px; display: flex; gap: 8px; flex-wrap: wrap; }
</style>
