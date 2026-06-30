<template>
  <el-card>
    <div style="display:flex;justify-content:space-between;margin-bottom:16px;">
      <div>
        <el-input v-model="search.customerName" placeholder="客户名称" style="width:180px;margin-right:8px" clearable />
        <el-select v-model="search.status" placeholder="状态" style="width:120px;margin-right:8px" clearable>
          <el-option label="草稿" value="草稿" />
          <el-option label="已确认" value="已确认" />
          <el-option label="已发货" value="已发货" />
          <el-option label="已开票" value="已开票" />
          <el-option label="已完成" value="已完成" />
        </el-select>
        <el-button type="primary" @click="loadData">查询</el-button>
      </div>
      <el-button type="primary" @click="$router.push('/sale/orders/new')">+ 新增订单</el-button>
    </div>

    <el-table :data="orders" stripe border>
      <el-table-column prop="orderNo" label="订单编号" width="180" />
      <el-table-column prop="customerName" label="客户名称" />
      <el-table-column prop="orderDate" label="订单日期" width="120" />
      <el-table-column prop="totalWithTax" label="价税合计" width="120" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sourceChannel" label="来源" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.sourceChannel === 'AI' ? 'success' : 'info'">{{ row.sourceChannel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" @click="$router.push(`/sale/orders/${row.id}`)">编辑</el-button>
          <el-button text type="success" v-if="row.status === '草稿'" @click="submitAudit(row.id)">提交</el-button>
          <el-button text type="warning" v-if="row.status === '已确认' || row.status === '已发货'" @click="generateWorkOrder(row)">生成工单</el-button>
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
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../../api'

const orders = ref([])
const search = ref({ customerName: '', status: '' })
const page = ref({ current: 1, size: 20, total: 0 })

const statusType = (s) => {
  const map = { '草稿': 'info', '已确认': '', '已发货': 'warning', '已开票': 'success', '已完成': 'success', '已取消': 'danger' }
  return map[s] || 'info'
}

const loadData = async () => {
  const res = await api.get('/sale/orders', { params: { ...search.value, current: page.value.current, size: page.value.size } })
  if (res.code === 200) {
    orders.value = res.data.records
    page.value.total = res.data.total
  }
}

const submitAudit = async (id) => {
  try {
    await api.post(`/sale/orders/${id}/submit`)
    ElMessage.success('已提交审核')
    loadData()
  } catch (e) {}
}

const generateWorkOrder = async (row) => {
  try {
    await ElMessageBox.confirm(`将为销售订单 ${row.orderNo} 的未发货物料生成生产工单，是否继续？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const res = await api.post('/production/workorders/from-sale-order', { saleOrderId: row.id })
    if (res.data && res.data.length > 0) {
      ElMessage.success(`已生成 ${res.data.length} 个生产工单`)
    } else {
      ElMessage.info('没有需要生成工单的物料')
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('生成工单失败')
  }
}

onMounted(loadData)
</script>
