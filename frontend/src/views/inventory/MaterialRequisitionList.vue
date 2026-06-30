<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card style="margin-bottom:16px;">
      <div style="display:flex;justify-content:space-between;align-items:center;">
        <div>
          <el-input v-model="search.keyword" placeholder="领料单号" style="width:200px;margin-right:8px" clearable @clear="loadData" @keyup.enter="loadData" />
          <el-select v-model="search.status" placeholder="状态" style="width:130px;margin-right:8px" clearable>
            <el-option label="草稿" value="草稿" />
            <el-option label="已审核" value="已审核" />
            <el-option label="已关闭" value="已关闭" />
            <el-option label="已取消" value="已取消" />
          </el-select>
          <el-button type="primary" @click="onSearch">查询</el-button>
        </div>
        <el-button type="success" @click="openCreate">+ 新增领料单</el-button>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card>
      <el-table :data="list" stripe border v-loading="loading">
        <el-table-column prop="requisitionNo" label="领料单号" width="170" />
        <el-table-column prop="requisitionDate" label="领料日期" width="110" />
        <el-table-column prop="workOrderNo" label="工单号" width="140" />
        <el-table-column prop="departmentId" label="部门ID" width="90" />
        <el-table-column prop="employeeId" label="领料人ID" width="100" />
        <el-table-column prop="warehouseId" label="仓库ID" width="90" />
        <el-table-column prop="totalAmount" label="合计金额" width="120" align="right" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="创建人" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status === '草稿'" text type="success" @click="onApprove(row)">审核</el-button>
            <el-button v-if="row.status === '已审核'" text type="warning" @click="onClose(row)">关闭</el-button>
            <el-button v-if="row.status === '草稿'" text type="danger" @click="onCancel(row)">取消</el-button>
            <el-button text type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page.current"
        :page-size="page.size"
        :total="page.total"
        layout="total, prev, pager, next"
        style="margin-top:16px;justify-content:flex-end;display:flex"
        @current-change="loadData"
      />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑领料单' : '新增领料单'" width="1100px" :close-on-click-modal="false">
      <el-form :model="form" label-width="100px" :inline="true">
        <el-form-item label="领料单号">
          <el-input v-model="form.requisitionNo" placeholder="保存时自动生成" style="width:180px" />
        </el-form-item>
        <el-form-item label="领料日期">
          <el-date-picker v-model="form.requisitionDate" type="date" value-format="YYYY-MM-DD" style="width:160px" />
        </el-form-item>
        <el-form-item label="工单号">
          <el-input v-model="form.workOrderNo" placeholder="工单号" style="width:180px" />
        </el-form-item>
        <el-form-item label="仓库">
          <el-select v-model="form.warehouseId" filterable placeholder="选择仓库" style="width:180px">
            <el-option v-for="w in warehouses" :key="w.stockId" :label="w.stockName" :value="w.stockId" />
          </el-select>
        </el-form-item>
        <el-form-item label="领料人">
          <el-select v-model="form.employeeId" filterable placeholder="选择领料人" style="width:160px" clearable>
            <el-option v-for="e in employees" :key="e.employeeId" :label="e.employeeName" :value="e.employeeId" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="form.departmentId" filterable placeholder="选择部门" style="width:160px" clearable>
            <el-option v-for="d in departments" :key="d.deptId" :label="d.deptName" :value="d.deptId" />
          </el-select>
        </el-form-item>
        <el-form-item label="币别">
          <el-select v-model="form.currencyId" filterable placeholder="选择币别" style="width:120px">
            <el-option v-for="c in currencies" :key="c.currencyId" :label="c.currencyCode" :value="c.currencyId" />
          </el-select>
        </el-form-item>
        <el-form-item label="汇率">
          <el-input-number v-model="form.exchangeRate" :precision="6" :step="0.1" :controls="false" style="width:130px" />
        </el-form-item>
        <el-form-item label="合计金额">
          <el-input-number v-model="form.totalAmount" :precision="2" :controls="false" disabled style="width:140px" />
        </el-form-item>
        <el-form-item label="备注" style="width:100%;">
          <el-input v-model="form.remark" type="textarea" :rows="2" style="width:600px" />
        </el-form-item>
      </el-form>

      <!-- 明细 -->
      <div style="margin-top:10px;">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;">
          <span style="font-weight:600;">领料明细</span>
          <el-button text type="primary" @click="addItem">+ 新增行</el-button>
        </div>
        <el-table :data="form.items" border size="small" :summary-method="getSummary" show-summary>
          <el-table-column type="index" label="行" width="45" />
          <el-table-column label="物料" width="200">
            <template #default="{ row }">
              <el-select v-model="row.materialId" filterable placeholder="选择物料" @change="(v) => onMaterialChange(row, v)">
                <el-option v-for="m in materials" :key="m.materialId" :label="m.materialName" :value="m.materialId" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column prop="materialCode" label="物料编码" width="120" />
          <el-table-column prop="materialName" label="物料名称" width="120" />
          <el-table-column prop="specification" label="规格" width="120" />
          <el-table-column label="单位ID" width="80">
            <template #default="{ row }">
              <el-input-number v-model="row.unitId" :controls="false" :min="0" style="width:70px" />
            </template>
          </el-table-column>
          <el-table-column label="数量" width="100">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :controls="false" :min="0" :precision="4" @change="calcRow(row)" style="width:90px" />
            </template>
          </el-table-column>
          <el-table-column label="单价" width="100">
            <template #default="{ row }">
              <el-input-number v-model="row.unitPrice" :controls="false" :min="0" :precision="4" @change="calcRow(row)" style="width:90px" />
            </template>
          </el-table-column>
          <el-table-column prop="amount" label="金额" width="110" align="right" />
          <el-table-column label="仓库" width="120">
            <template #default="{ row }">
              <el-select v-model="row.stockId" filterable placeholder="仓库" size="small">
                <el-option v-for="w in warehouses" :key="w.stockId" :label="w.stockName" :value="w.stockId" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="货位ID" width="90">
            <template #default="{ row }">
              <el-input-number v-model="row.binId" :controls="false" :min="0" style="width:80px" />
            </template>
          </el-table-column>
          <el-table-column label="批号" width="110">
            <template #default="{ row }">
              <el-input v-model="row.batchNo" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" fixed="right">
            <template #default="{ $index }">
              <el-button text type="danger" @click="form.items.splice($index, 1)">删</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listMaterialRequisition, getMaterialRequisition, saveMaterialRequisition, updateMaterialRequisition,
  deleteMaterialRequisition, approveMaterialRequisition, closeMaterialRequisition, cancelMaterialRequisition
} from '@/api/inventory/materialRequisition'
import { listAllWarehouse } from '@/api/basedata/warehouse'
import { listAllMaterial } from '@/api/basedata/material'
import { listAllCurrency } from '@/api/basedata/currency'
import { listAllDepartment } from '@/api/basedata/department'
import { listAllEmployee } from '@/api/basedata/employee'

const list = ref([])
const loading = ref(false)
const search = reactive({ keyword: '', status: '' })
const page = reactive({ current: 1, size: 20, total: 0 })

const dialogVisible = ref(false)
const isEdit = ref(false)
const form = ref(createEmptyForm())

const warehouses = ref([])
const materials = ref([])
const currencies = ref([])
const departments = ref([])
const employees = ref([])

function createEmptyForm() {
  return {
    requisitionId: null,
    requisitionNo: '',
    requisitionDate: new Date().toISOString().slice(0, 10),
    workOrderNo: '',
    departmentId: null,
    employeeId: null,
    warehouseId: null,
    currencyId: null,
    exchangeRate: 1.0,
    totalAmount: 0,
    remark: '',
    attachmentUrls: '',
    items: []
  }
}

const statusType = (s) => ({
  '草稿': 'info',
  '已审核': 'success',
  '已关闭': 'warning',
  '已取消': 'danger'
}[s] || 'info')

const loadData = async () => {
  loading.value = true
  try {
    const res = await listMaterialRequisition({
      current: page.current,
      size: page.size,
      keyword: search.keyword,
      status: search.status
    })
    list.value = res.records || []
    page.total = res.total || 0
  } finally {
    loading.value = false
  }
}

const onSearch = () => {
  page.current = 1
  loadData()
}

const openCreate = () => {
  isEdit.value = false
  form.value = createEmptyForm()
  dialogVisible.value = true
}

const openEdit = async (row) => {
  isEdit.value = true
  const data = await getMaterialRequisition(row.requisitionId)
  form.value = { ...createEmptyForm(), ...data }
  if (!form.value.items) form.value.items = []
  dialogVisible.value = true
}

const onMaterialChange = (row, id) => {
  const m = materials.value.find(x => x.materialId === id)
  if (m) {
    row.materialCode = m.materialCode
    row.materialName = m.materialName
    row.specification = m.model || ''
    row.unitId = m.baseUnitId
    row.unitPrice = m.purchasePrice || 0
    if (!row.stockId && form.value.warehouseId) row.stockId = form.value.warehouseId
    calcRow(row)
  }
}

const calcRow = (row) => {
  const qty = Number(row.quantity) || 0
  const price = Number(row.unitPrice) || 0
  row.amount = (qty * price).toFixed(2)
  calcTotal()
}

const calcTotal = () => {
  const total = form.value.items.reduce((s, i) => s + (Number(i.amount) || 0), 0)
  form.value.totalAmount = total.toFixed(2)
}

const getSummary = () => {
  const qty = form.value.items.reduce((s, i) => s + (Number(i.quantity) || 0), 0)
  const amt = form.value.items.reduce((s, i) => s + (Number(i.amount) || 0), 0)
  return ['合计', '', '', '', '', '', qty, '', amt, '', '', '', '']
}

const addItem = () => {
  form.value.items.push({
    materialId: null,
    materialCode: '',
    materialName: '',
    specification: '',
    unitId: null,
    quantity: 1,
    unitPrice: 0,
    amount: 0,
    stockId: form.value.warehouseId || null,
    binId: null,
    batchNo: '',
    remark: ''
  })
  calcTotal()
}

const onSave = async () => {
  try {
    if (isEdit.value) {
      await updateMaterialRequisition(form.value.requisitionId, form.value)
    } else {
      await saveMaterialRequisition(form.value)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch (e) {
    // 错误已由拦截器处理
  }
}

const onApprove = async (row) => {
  try {
    await ElMessageBox.confirm('审核生产领料单后将扣减库存，库存不足将报错。是否继续审核？', '审核确认', {
      confirmButtonText: '确定审核',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const approver = localStorage.getItem('username') || 'system'
    await approveMaterialRequisition(row.requisitionId, approver)
    ElMessage.success('审核成功，库存已扣减')
    loadData()
  } catch (e) {
    if (e !== 'cancel') {
      // 业务错误已由拦截器处理
    }
  }
}

const onClose = async (row) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入关闭原因（可选）', '关闭领料单', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputType: 'textarea'
    })
    await closeMaterialRequisition(row.requisitionId, value || '')
    ElMessage.success('已关闭')
    loadData()
  } catch (e) {
    if (e !== 'cancel') {
      // 业务错误已由拦截器处理
    }
  }
}

const onCancel = async (row) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入取消原因（可选）', '取消领料单', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputType: 'textarea'
    })
    await cancelMaterialRequisition(row.requisitionId, value || '')
    ElMessage.success('已取消')
    loadData()
  } catch (e) {
    if (e !== 'cancel') {
      // 业务错误已由拦截器处理
    }
  }
}

const onDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除领料单 ${row.requisitionNo} 吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteMaterialRequisition(row.requisitionId)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    if (e !== 'cancel') {
      // 业务错误已由拦截器处理
    }
  }
}

const loadOptions = async () => {
  const [wh, mat, cur, dep, emp] = await Promise.all([
    listAllWarehouse(),
    listAllMaterial(),
    listAllCurrency(),
    listAllDepartment(),
    listAllEmployee()
  ])
  warehouses.value = wh || []
  materials.value = mat || []
  currencies.value = cur || []
  departments.value = dep || []
  employees.value = emp || []
}

onMounted(async () => {
  await loadOptions()
  loadData()
})
</script>

<style scoped>
.app-container {
  padding: 16px;
}
</style>
