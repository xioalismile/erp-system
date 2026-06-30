<template>
  <el-card>
    <div style="margin-bottom:16px;">
      <el-input v-model="search" placeholder="物料编码/名称" style="width:200px;margin-right:8px" clearable @clear="load" />
      <el-button type="primary" @click="load">查询</el-button>
    </div>
    <el-table :data="list" stripe border>
      <el-table-column prop="materialCode" label="物料编码" width="120" />
      <el-table-column prop="materialName" label="物料名称" />
      <el-table-column prop="warehouse" label="仓库" width="100" />
      <el-table-column prop="quantity" label="库存数量" width="120" />
      <el-table-column prop="safetyStock" label="安全库存" width="100" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.quantity < row.safetyStock ? 'danger' : 'success'" size="small">
            {{ row.quantity < row.safetyStock ? '不足' : '正常' }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '../../api'

const list = ref([])
const search = ref('')

const load = async () => {
  const res = await api.get('/inventory/list', { params: { materialName: search.value } })
  if (res.code === 200) list.value = res.data
}

onMounted(load)
</script>
