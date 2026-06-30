<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <div class="filter-bar">
      <el-input-number v-model="filters.periodYear" :min="2000" :max="2100" placeholder="会计年度" class="filter-input" />
      <el-input-number v-model="filters.periodMonth" :min="1" :max="12" placeholder="会计月份" class="filter-input" />
      <el-input v-model="filters.accountCode" placeholder="科目编码(模糊)" class="filter-input" @keyup.enter="loadList" />
      <el-button type="primary" @click="loadList">查询</el-button>
    </div>

    <!-- 余额表格 -->
    <el-table :data="list" border show-summary :summary-method="getSummary">
      <el-table-column label="期间" width="110">
        <template #default="{ row }">{{ row.periodYear }}-{{ String(row.periodMonth).padStart(2, '0') }}</template>
      </el-table-column>
      <el-table-column prop="accountCode" label="科目编码" width="140" />
      <el-table-column prop="accountName" label="科目名称" min-width="180" />
      <el-table-column prop="accountDirection" label="方向" width="80">
        <template #default="{ row }">
          <el-tag :type="row.accountDirection === '借' ? 'primary' : 'success'" size="small">{{ row.accountDirection }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="期初借方" width="140">
        <template #default="{ row }">{{ formatMoney(row.beginDebitBalance) }}</template>
      </el-table-column>
      <el-table-column label="期初贷方" width="140">
        <template #default="{ row }">{{ formatMoney(row.beginCreditBalance) }}</template>
      </el-table-column>
      <el-table-column label="本期借方" width="140">
        <template #default="{ row }">{{ formatMoney(row.currentDebit) }}</template>
      </el-table-column>
      <el-table-column label="本期贷方" width="140">
        <template #default="{ row }">{{ formatMoney(row.currentCredit) }}</template>
      </el-table-column>
      <el-table-column label="期末借方" width="140">
        <template #default="{ row }">{{ formatMoney(row.endDebitBalance) }}</template>
      </el-table-column>
      <el-table-column label="期末贷方" width="140">
        <template #default="{ row }">{{ formatMoney(row.endCreditBalance) }}</template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > 0"
      :total="total"
      v-model:current-page="page"
      :page-size="20"
      layout="total, prev, pager, next"
      @current-change="loadList"
      style="margin-top: 10px"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { pageBalance } from '@/api/finance/voucher'

const filters = reactive({
  periodYear: new Date().getFullYear(),
  periodMonth: new Date().getMonth() + 1,
  accountCode: ''
})
const list = ref([])
const total = ref(0)
const page = ref(1)

const loadList = async () => {
  const params = { current: page.value, size: 20, ...filters }
  Object.keys(params).forEach(k => {
    if (params[k] === '' || params[k] === null) delete params[k]
  })
  try {
    const res = await pageBalance(params)
    list.value = res.records
    total.value = res.total
  } catch (e) {
    ElMessage.error('查询失败')
  }
}

const formatMoney = (val) => {
  const num = Number(val || 0)
  return num.toFixed(2)
}

// 合计行
const getSummary = ({ columns, data }) => {
  const sums = []
  columns.forEach((col, idx) => {
    if (idx === 0) { sums[idx] = '合计'; return }
    const fields = ['beginDebitBalance', 'beginCreditBalance', 'currentDebit', 'currentCredit', 'endDebitBalance', 'endCreditBalance']
    // 通过列的label匹配
    const labelMap = {
      4: 'beginDebitBalance', 5: 'beginCreditBalance',
      6: 'currentDebit', 7: 'currentCredit',
      8: 'endDebitBalance', 9: 'endCreditBalance'
    }
    const field = labelMap[idx]
    if (field) {
      const total = data.reduce((s, r) => s + Number(r[field] || 0), 0)
      sums[idx] = formatMoney(total)
    } else {
      sums[idx] = ''
    }
  })
  return sums
}

onMounted(() => {
  loadList()
})
</script>

<style scoped>
.filter-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 15px;
  flex-wrap: wrap;
  align-items: center;
}
.filter-input {
  width: 160px;
}
</style>
