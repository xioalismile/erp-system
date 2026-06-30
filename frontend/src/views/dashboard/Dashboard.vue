<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="6" v-for="card in stats" :key="card.label">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-icon" :style="{ background: card.color }">{{ card.icon }}</div>
            <div>
              <div class="stat-value">{{ card.value }}</div>
              <div class="stat-label">{{ card.label }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top:20px">
      <template #header>近期订单</template>
      <el-table :data="recentOrders" stripe size="small">
        <el-table-column prop="orderNo" label="订单编号" width="180" />
        <el-table-column prop="customerName" label="客户" />
        <el-table-column prop="orderDate" label="日期" width="120" />
        <el-table-column prop="totalAmount" label="金额" width="120" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="sourceChannel" label="来源" width="80" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '../../api'

const stats = ref([
  { label: '订单总数', value: 0, icon: '📋', color: '#3b82f6' },
  { label: '草稿', value: 0, icon: '📝', color: '#f59e0b' },
  { label: '已确认', value: 0, icon: '✓', color: '#10b981' },
  { label: '已发货', value: 0, icon: '🚚', color: '#8b5cf6' },
])

const recentOrders = ref([])

onMounted(async () => {
  try {
    const res = await api.get('/sale/dashboard')
    if (res.code === 200) {
      stats.value[0].value = res.data.totalOrders
      stats.value[1].value = res.data.draftOrders
      stats.value[2].value = res.data.confirmedOrders
      stats.value[3].value = res.data.shippedOrders
    }
    const ordersRes = await api.get('/sale/orders', { params: { size: 10 } })
    if (ordersRes.code === 200) recentOrders.value = ordersRes.data.records
  } catch (e) {}
})
</script>

<style scoped>
.stat-card { display: flex; align-items: center; gap: 16px; }
.stat-icon { width: 50px; height: 50px; border-radius: 10px; display: flex; align-items: center; justify-content: center; font-size: 24px; }
.stat-value { font-size: 28px; font-weight: 700; color: #1e293b; }
.stat-label { font-size: 13px; color: #6b7280; }
</style>
