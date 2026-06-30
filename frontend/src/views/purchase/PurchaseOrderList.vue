<template>
  <el-card>
    <div style="display:flex;justify-content:space-between;margin-bottom:16px;">
      <div>
        <el-input v-model="search.supplierName" placeholder="供应商名称" style="width:180px;margin-right:8px" clearable />
        <el-select v-model="search.status" placeholder="状态" style="width:120px;margin-right:8px" clearable>
          <el-option label="草稿" value="草稿" />
          <el-option label="已确认" value="已确认" />
          <el-option label="已入库" value="已入库" />
          <el-option label="已完成" value="已完成" />
          <el-option label="已取消" value="已取消" />
        </el-select>
        <el-button type="primary" @click="loadData">查询</el-button>
      </div>
      <el-button type="primary" @click="$router.push('/purchase/orders/new')">+ 新增采购订单</el-button>
    </div>

    <el-table :data="orders" stripe border>
      <el-table-column prop="orderNo" label="采购单号" width="180" />
      <el-table-column prop="supplierName" label="供应商" />
      <el-table-column prop="orderDate" label="日期" width="120" />
      <el-table-column prop="totalWithTax" label="价税合计" width="120" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="inboundStatus" label="入库" width="80">
        <template #default="{ row }"><el-tag size="small" :type="row.inboundStatus==='已入库'?'success':'info'">{{ row.inboundStatus }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="sourceType" label="来源" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.sourceType==='SALE_ORDER'" size="small" type="warning">销售转采购</el-tag>
          <el-tag v-else-if="row.sourceType==='MRP'" size="small" type="danger">MRP生成</el-tag>
          <el-tag v-else size="small" type="info">手动</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" @click="$router.push(`/purchase/orders/${row.id}`)">编辑</el-button>
          <el-button text type="success" v-if="row.status==='草稿'" @click="submit(row.id)">提交</el-button>
          <el-button text type="warning" v-if="row.status==='已确认'" @click="inboundDialog(row.id)">入库</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination v-model:current-page="page.current" :page-size="page.size" :total="page.total"
      layout="total, prev, pager, next" style="margin-top:16px;justify-content:flex-end;display:flex" @current-change="loadData" />

    <!-- 入库弹窗 -->
    <el-dialog v-model="inbound.visible" title="采购入库" width="400px">
      <el-form label-width="80px">
        <el-form-item label="入库仓库"><el-input v-model="inbound.warehouse" placeholder="主仓库" /></el-form-item>
      </el-form>
      <el-alert type="success" :closable="false" style="margin:10px 0">入库将自动增加库存</el-alert>
      <template #footer>
        <el-button @click="inbound.visible=false">取消</el-button>
        <el-button type="primary" @click="doInbound">确认入库</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../../api'

const orders = ref([])
const search = ref({ supplierName: '', status: '' })
const page = ref({ current: 1, size: 20, total: 0 })
const inbound = ref({ visible: false, id: null, warehouse: '主仓库' })

const statusType = (s) => ({'草稿':'info','已确认':'','已入库':'warning','已完成':'success','已取消':'danger'}[s]||'info')

const loadData = async () => {
  const res = await api.get('/purchase/orders', { params: { ...search.value, current: page.value.current, size: page.value.size } })
  if (res.code === 200) { orders.value = res.data.records; page.value.total = res.data.total }
}

const submit = async (id) => {
  try { await api.post(`/purchase/orders/${id}/submit`); ElMessage.success('已提交'); loadData() } catch(e) {}
}

const inboundDialog = (id) => { inbound.value = { visible: true, id, warehouse: '主仓库' } }

const doInbound = async () => {
  try {
    await api.post(`/purchase/orders/${inbound.value.id}/inbound`, { warehouse: inbound.value.warehouse })
    ElMessage.success('入库成功，库存已增加')
    inbound.value.visible = false
    loadData()
  } catch(e) {}
}

onMounted(loadData)
</script>
