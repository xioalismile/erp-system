<template>
  <div class="app-container">
    <div class="filter-bar">
      <el-select v-model="warehouseCode" placeholder="选择仓库" class="filter-input">
        <el-option label="全部" value="" />
        <el-option v-for="w in warehouses" :key="w.warehouseCode" :label="w.warehouseName" :value="w.warehouseCode" />
      </el-select>
      <el-input v-model="locationCode" placeholder="储位编码" class="filter-input" @keyup.enter="loadData" />
      <el-select v-model="status" placeholder="状态">
        <el-option label="全部" value="" />
        <el-option label="可用" value="可用" />
        <el-option label="占用" value="占用" />
        <el-option label="锁定" value="锁定" />
      </el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button type="success" @click="showAddModal = true">新增储位</el-button>
    </div>

    <el-table :data="tableData" border>
      <el-table-column prop="warehouseCode" label="仓库编码" />
      <el-table-column prop="warehouseName" label="仓库名称" />
      <el-table-column prop="locationCode" label="储位编码" />
      <el-table-column prop="zoneCode" label="区域" />
      <el-table-column prop="rackCode" label="货架" />
      <el-table-column prop="shelfCode" label="层" />
      <el-table-column prop="tierCode" label="位" />
      <el-table-column prop="usageType" label="用途">
        <template #default="scope">
          <el-tag :type="scope.row.usageType === '普通' ? 'primary' : scope.row.usageType === '贵重' ? 'warning' : 'danger'">
            {{ scope.row.usageType }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态">
        <template #default="scope">
          <el-tag :type="scope.row.status === '可用' ? 'success' : scope.row.status === '占用' ? 'warning' : 'danger'">
            {{ scope.row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作">
        <template #default="scope">
          <el-button size="small" @click="editRow(scope.row)">编辑</el-button>
          <el-button size="small" type="danger" @click="deleteRow(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog :title="isEdit ? '编辑储位' : '新增储位'" :visible.sync="showAddModal">
      <el-form :model="formData" label-width="100px">
        <el-form-item label="所属仓库" prop="warehouseCode">
          <el-select v-model="formData.warehouseCode">
            <el-option v-for="w in warehouses" :key="w.warehouseCode" :label="w.warehouseName" :value="w.warehouseCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="储位编码" prop="locationCode">
          <el-input v-model="formData.locationCode" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="区域" prop="zoneCode">
          <el-input v-model="formData.zoneCode" />
        </el-form-item>
        <el-form-item label="货架" prop="rackCode">
          <el-input v-model="formData.rackCode" />
        </el-form-item>
        <el-form-item label="层" prop="shelfCode">
          <el-input v-model="formData.shelfCode" />
        </el-form-item>
        <el-form-item label="位" prop="tierCode">
          <el-input v-model="formData.tierCode" />
        </el-form-item>
        <el-form-item label="用途" prop="usageType">
          <el-select v-model="formData.usageType">
            <el-option label="普通" value="普通" />
            <el-option label="贵重" value="贵重" />
            <el-option label="危险品" value="危险品" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="formData.status">
            <el-option label="可用" value="可用" />
            <el-option label="占用" value="占用" />
            <el-option label="锁定" value="锁定" />
          </el-select>
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
const locationCode = ref('')
const status = ref('')
const tableData = ref([])
const showAddModal = ref(false)
const isEdit = ref(false)

const formData = reactive({
  id: null,
  warehouseCode: '',
  locationCode: '',
  zoneCode: '',
  rackCode: '',
  shelfCode: '',
  tierCode: '',
  status: '可用',
  usageType: '普通',
  remark: ''
})

const loadWarehouses = async () => {
  const res = await api.get('/api/warehouse/list')
  warehouses.value = res.data
}

const loadData = async () => {
  const res = await api.get('/api/location/list', {
    params: {
      warehouseCode: warehouseCode.value,
      locationCode: locationCode.value,
      status: status.value
    }
  })
  tableData.value = res.data.map(item => {
    const warehouse = warehouses.value.find(w => w.warehouseCode === item.warehouseCode)
    return { ...item, warehouseName: warehouse?.warehouseName || '' }
  })
}

const editRow = (row) => {
  isEdit.value = true
  Object.assign(formData, row)
  showAddModal.value = true
}

const deleteRow = async (row) => {
  if (await confirm(`确定删除储位 ${row.locationCode} 吗？`)) {
    await api.delete(`/api/location/${row.id}`)
    loadData()
  }
}

const saveData = async () => {
  if (isEdit.value) {
    await api.put(`/api/location/${formData.id}`, formData)
  } else {
    await api.post('/api/location', formData)
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