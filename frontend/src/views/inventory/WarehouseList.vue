<template>
  <div class="app-container">
    <div class="filter-bar">
      <el-input v-model="warehouseCode" placeholder="仓库编码" class="filter-input" @keyup.enter="loadData" />
      <el-input v-model="warehouseName" placeholder="仓库名称" class="filter-input" @keyup.enter="loadData" />
      <el-select v-model="status" placeholder="状态">
        <el-option label="全部" value="" />
        <el-option label="启用" value="启用" />
        <el-option label="停用" value="停用" />
      </el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button type="success" @click="showAddModal = true">新增仓库</el-button>
    </div>

    <el-table :data="tableData" border>
      <el-table-column prop="warehouseCode" label="仓库编码" />
      <el-table-column prop="warehouseName" label="仓库名称" />
      <el-table-column prop="address" label="地址" />
      <el-table-column prop="contactPerson" label="联系人" />
      <el-table-column prop="contactPhone" label="联系电话" />
      <el-table-column prop="type" label="类型">
        <template #default="scope">
          <el-tag :type="scope.row.type === '普通仓' ? 'primary' : scope.row.type === '成品仓' ? 'success' : 'warning'">
            {{ scope.row.type }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态">
        <template #default="scope">
          <el-tag :type="scope.row.status === '启用' ? 'success' : 'danger'">
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

    <el-dialog :title="isEdit ? '编辑仓库' : '新增仓库'" :visible.sync="showAddModal">
      <el-form :model="formData" label-width="100px">
        <el-form-item label="仓库编码" prop="warehouseCode">
          <el-input v-model="formData.warehouseCode" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="仓库名称" prop="warehouseName">
          <el-input v-model="formData.warehouseName" />
        </el-form-item>
        <el-form-item label="地址" prop="address">
          <el-input v-model="formData.address" />
        </el-form-item>
        <el-form-item label="联系人" prop="contactPerson">
          <el-input v-model="formData.contactPerson" />
        </el-form-item>
        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="formData.contactPhone" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="formData.type">
            <el-option label="普通仓" value="普通仓" />
            <el-option label="成品仓" value="成品仓" />
            <el-option label="原料仓" value="原料仓" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="formData.status">
            <el-option label="启用" value="启用" />
            <el-option label="停用" value="停用" />
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
import { ref, reactive } from 'vue'
import { api } from '@/api'

const warehouseCode = ref('')
const warehouseName = ref('')
const status = ref('')
const tableData = ref([])
const showAddModal = ref(false)
const isEdit = ref(false)

const formData = reactive({
  id: null,
  warehouseCode: '',
  warehouseName: '',
  address: '',
  contactPerson: '',
  contactPhone: '',
  type: '普通仓',
  status: '启用',
  remark: ''
})

const loadData = async () => {
  const res = await api.get('/api/warehouse/list', {
    params: {
      warehouseCode: warehouseCode.value,
      warehouseName: warehouseName.value,
      status: status.value
    }
  })
  tableData.value = res.data
}

const editRow = (row) => {
  isEdit.value = true
  Object.assign(formData, row)
  showAddModal.value = true
}

const deleteRow = async (row) => {
  if (await confirm(`确定删除仓库 ${row.warehouseName} 吗？`)) {
    await api.delete(`/api/warehouse/${row.id}`)
    loadData()
  }
}

const saveData = async () => {
  if (isEdit.value) {
    await api.put(`/api/warehouse/${formData.id}`, formData)
  } else {
    await api.post('/api/warehouse', formData)
  }
  showAddModal.value = false
  loadData()
}

loadData()
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