<template>
  <div class="app-container">
    <div class="filter-bar">
      <el-select v-model="warehouseCode" placeholder="选择仓库" class="filter-input">
        <el-option label="全部" value="" />
        <el-option v-for="w in warehouses" :key="w.warehouseCode" :label="w.warehouseName" :value="w.warehouseCode" />
      </el-select>
      <el-input v-model="materialCode" placeholder="物料编码" class="filter-input" @keyup.enter="loadData" />
      <el-input v-model="batchNo" placeholder="批次号" class="filter-input" @keyup.enter="loadData" />
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button type="warning" @click="showExpiring = true">临期预警</el-button>
      <el-button type="success" @click="showAddModal = true">新增批次</el-button>
    </div>

    <el-table :data="tableData" border>
      <el-table-column prop="batchNo" label="批次号" />
      <el-table-column prop="materialCode" label="物料编码" />
      <el-table-column prop="materialName" label="物料名称" />
      <el-table-column prop="warehouseCode" label="仓库编码" />
      <el-table-column prop="locationCode" label="储位" />
      <el-table-column prop="quantity" label="数量" />
      <el-table-column prop="lockedQuantity" label="锁定数量" />
      <el-table-column prop="productionDate" label="生产日期" />
      <el-table-column prop="expiryDate" label="有效期">
        <template #default="scope">
          <el-tag :type="getExpiryTagType(scope.row.expiryDate)">
            {{ scope.row.expiryDate }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="supplierName" label="供应商" />
      <el-table-column label="操作">
        <template #default="scope">
          <el-button size="small" @click="editRow(scope.row)">编辑</el-button>
          <el-button size="small" type="danger" @click="deleteRow(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog title="临期预警" :visible.sync="showExpiring">
      <el-table :data="expiringData" border>
        <el-table-column prop="batchNo" label="批次号" />
        <el-table-column prop="materialName" label="物料名称" />
        <el-table-column prop="warehouseCode" label="仓库" />
        <el-table-column prop="quantity" label="数量" />
        <el-table-column prop="expiryDate" label="到期日期" />
        <el-table-column prop="daysLeft" label="剩余天数">
          <template #default="scope">
            <el-tag :type="scope.row.daysLeft <= 7 ? 'danger' : 'warning'">
              {{ scope.row.daysLeft }} 天
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div slot="footer">
        <el-button @click="showExpiring = false">关闭</el-button>
      </div>
    </el-dialog>

    <el-dialog :title="isEdit ? '编辑批次' : '新增批次'" :visible.sync="showAddModal">
      <el-form :model="formData" label-width="100px">
        <el-form-item label="批次号" prop="batchNo">
          <el-input v-model="formData.batchNo" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="物料编码" prop="materialCode">
          <el-input v-model="formData.materialCode" />
        </el-form-item>
        <el-form-item label="物料名称" prop="materialName">
          <el-input v-model="formData.materialName" />
        </el-form-item>
        <el-form-item label="所属仓库" prop="warehouseCode">
          <el-select v-model="formData.warehouseCode">
            <el-option v-for="w in warehouses" :key="w.warehouseCode" :label="w.warehouseName" :value="w.warehouseCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="储位" prop="locationCode">
          <el-input v-model="formData.locationCode" />
        </el-form-item>
        <el-form-item label="数量" prop="quantity">
          <el-input type="number" v-model="formData.quantity" />
        </el-form-item>
        <el-form-item label="生产日期" prop="productionDate">
          <el-date-picker v-model="formData.productionDate" type="date" />
        </el-form-item>
        <el-form-item label="有效期" prop="expiryDate">
          <el-date-picker v-model="formData.expiryDate" type="date" />
        </el-form-item>
        <el-form-item label="供应商" prop="supplierCode">
          <el-input v-model="formData.supplierCode" />
        </el-form-item>
        <el-form-item label="供应商名称" prop="supplierName">
          <el-input v-model="formData.supplierName" />
        </el-form-item>
        <el-form-item label="批号" prop="lotNo">
          <el-input v-model="formData.lotNo" />
        </el-form-item>
        <el-form-item label="等级" prop="grade">
          <el-input v-model="formData.grade" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input type="textarea" v-model="formData.remark" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="showAddModal = false">取消</el-button>
        <el-button type="primary" @click="saveData">保存</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { api } from '@/api'

const warehouses = ref([])
const warehouseCode = ref('')
const materialCode = ref('')
const batchNo = ref('')
const tableData = ref([])
const expiringData = ref([])
const showExpiring = ref(false)
const showAddModal = ref(false)
const isEdit = ref(false)

const formData = reactive({
  id: null,
  batchNo: '',
  materialCode: '',
  materialName: '',
  warehouseCode: '',
  locationCode: '',
  quantity: 0,
  lockedQuantity: 0,
  productionDate: '',
  expiryDate: '',
  supplierCode: '',
  supplierName: '',
  lotNo: '',
  grade: '',
  remark: ''
})

const loadWarehouses = async () => {
  const res = await api.get('/api/warehouse/list')
  warehouses.value = res.data
}

const loadData = async () => {
  const res = await api.get('/api/inventory/batch/list', {
    params: {
      warehouseCode: warehouseCode.value,
      materialCode: materialCode.value,
      batchNo: batchNo.value
    }
  })
  tableData.value = res.data
}

const getExpiryTagType = (expiryDate) => {
  if (!expiryDate) return 'info'
  const expiry = new Date(expiryDate)
  const now = new Date()
  const diffDays = Math.ceil((expiry - now) / (1000 * 60 * 60 * 24))
  if (diffDays < 0) return 'danger'
  if (diffDays <= 7) return 'warning'
  return 'success'
}

const loadExpiring = async () => {
  const res = await api.get('/api/inventory/batch/expiring', { params: { days: 30 } })
  expiringData.value = res.data.map(item => {
    const expiry = new Date(item.expiryDate)
    const now = new Date()
    return {
      ...item,
      daysLeft: Math.ceil((expiry - now) / (1000 * 60 * 60 * 24))
    }
  })
  showExpiring.value = true
}

const editRow = (row) => {
  isEdit.value = true
  Object.assign(formData, row)
  showAddModal.value = true
}

const deleteRow = async (row) => {
  if (await confirm(`确定删除批次 ${row.batchNo} 吗？`)) {
    await api.delete(`/api/inventory/batch/${row.id}`)
    loadData()
  }
}

const saveData = async () => {
  if (isEdit.value) {
    await api.put(`/api/inventory/batch/${formData.id}`, formData)
  } else {
    await api.post('/api/inventory/batch', formData)
  }
  showAddModal.value = false
  loadData()
}

onMounted(async () => {
  await loadWarehouses()
  loadData()
})
</script>

<style scoped>
.filter-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}
.filter-input {
  width: 200px;
}
</style>