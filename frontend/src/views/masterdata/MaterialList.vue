<template>
  <el-card>
    <div style="display:flex;justify-content:space-between;margin-bottom:16px;">
      <el-input v-model="search" placeholder="物料编码/名称" style="width:200px" clearable @clear="load" />
      <div>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button type="success" @click="dialogVisible = true; editForm = {}">+ 新增物料</el-button>
      </div>
    </div>
    <el-table :data="list" stripe border>
      <el-table-column prop="materialCode" label="编码" width="120" />
      <el-table-column prop="materialName" label="名称" />
      <el-table-column prop="specification" label="规格" width="120" />
      <el-table-column prop="unit" label="单位" width="60" />
      <el-table-column prop="category" label="类别" width="80" />
      <el-table-column prop="salePrice" label="销售单价" width="100" />
      <el-table-column prop="safetyStock" label="安全库存" width="100" />
    </el-table>

    <el-dialog v-model="dialogVisible" title="新增物料" width="500px">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="编码"><el-input v-model="editForm.materialCode" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="editForm.materialName" /></el-form-item>
        <el-form-item label="规格"><el-input v-model="editForm.specification" /></el-form-item>
        <el-form-item label="单位"><el-input v-model="editForm.unit" /></el-form-item>
        <el-form-item label="单价"><el-input-number v-model="editForm.salePrice" :precision="2" /></el-form-item>
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
  const res = await api.get('/masterdata/materials', { params: { keyword: search.value } })
  if (res.code === 200) list.value = res.data
}

const save = async () => {
  try {
    await api.post('/masterdata/materials', editForm.value)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } catch (e) {}
}

onMounted(load)
</script>
