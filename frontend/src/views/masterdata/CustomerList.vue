<template>
  <el-card>
    <div style="display:flex;justify-content:space-between;margin-bottom:16px;">
      <el-input v-model="search" placeholder="客户编码/名称" style="width:200px" clearable @clear="load" />
      <el-button type="success" @click="dialogVisible = true; editForm = {}">+ 新增客户</el-button>
    </div>
    <el-table :data="list" stripe border>
      <el-table-column prop="customerCode" label="编码" width="120" />
      <el-table-column prop="customerName" label="名称" />
      <el-table-column prop="contactPerson" label="联系人" width="100" />
      <el-table-column prop="contactPhone" label="电话" width="140" />
      <el-table-column prop="address" label="地址" show-overflow-tooltip />
      <el-table-column prop="paymentMethod" label="付款方式" width="120" />
    </el-table>

    <el-dialog v-model="dialogVisible" title="新增客户" width="500px">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="编码"><el-input v-model="editForm.customerCode" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="editForm.customerName" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="editForm.contactPerson" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="editForm.contactPhone" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="editForm.address" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../../api'

const list = ref([])
const search = ref('')
const dialogVisible = ref(false)
const editForm = ref({})

const load = async () => {
  const res = await api.get('/masterdata/customers', { params: { keyword: search.value } })
  if (res.code === 200) list.value = res.data
}

const save = async () => {
  try {
    await api.post('/masterdata/customers', editForm.value)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } catch (e) {}
}

onMounted(load)
</script>
