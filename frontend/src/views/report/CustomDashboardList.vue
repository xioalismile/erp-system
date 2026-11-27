<template>
  <div class="custom-dashboard-list">
    <!-- 顶部筛选区 -->
    <div class="filter-section">
      <div class="filter-row">
        <el-input
          v-model="filters.keyword"
          :placeholder="$tt('仪表盘名称/编码')"
          clearable
          style="width: 220px"
          @keyup.enter="loadData"
          @clear="loadData"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" @click="loadData">{{ $tt("查询") }}</el-button>
        <el-button @click="resetFilter">{{ $tt("重置") }}</el-button>
      </div>
      <div class="filter-actions">
        <el-button type="primary" @click="goCreate">
          <el-icon><Plus /></el-icon> 新建仪表盘
        </el-button>
      </div>
    </div>

    <!-- 数据表格 -->
    <el-table :data="tableData" border size="small" v-loading="loading">
      <el-table-column type="index" label="#" width="50" align="center" />
      <el-table-column prop="dashCode" :label="$tf('仪表盘编码')" width="140" show-overflow-tooltip />
      <el-table-column prop="dashName" :label="$tf('仪表盘名称')" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" @click="goView(row)">{{ row.dashName }}</el-link>
        </template>
      </el-table-column>
      <el-table-column prop="cardCount" :label="$tf('卡片数')" width="90" align="center" />
      <el-table-column prop="status" :label="$tf('状态')" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">{{ $dt(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="shareScope" :label="$tf('共享范围')" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.shareScope === '共享' ? 'warning' : 'info'" size="small">{{ row.shareScope }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdBy" :label="$tf('创建人')" width="100" show-overflow-tooltip />
      <el-table-column prop="createdAt" :label="$tf('创建时间')" width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <el-pagination
      class="pagination"
      v-model:current-page="page.current"
      v-model:page-size="page.size"
      :page-sizes="[20, 50, 100]"
      :total="page.total"
      layout="total, sizes, prev, pager, next, jumper"
      @size-change="loadData"
      @current-change="loadData"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAutoRefresh, triggerListRefresh } from '@/composables/useAutoRefresh'
import { Search, Plus } from '@element-plus/icons-vue'
import { listDashboards } from '@/api/report/customDashboard'

const router = useRouter()

const filters = ref({ keyword: '' })
const loading = ref(false)
const tableData = ref([])
const page = reactive({ current: 1, size: 20, total: 0 })

async function loadData() {
  loading.value = true
  try {
    const params = { page: page.current, size: page.size }
    if (filters.value.keyword) params.keyword = filters.value.keyword
    const res = await listDashboards(params)
    const payload = res || {}
    tableData.value = payload.records || []
    page.total = payload.total || 0
  } catch (e) {
    tableData.value = []
    page.total = 0
  } finally {
    loading.value = false
  }
}

function resetFilter() {
  filters.value.keyword = ''
  page.current = 1
  loadData()
}

function goCreate() {
  router.push('/report/dashboard/designer')
}

function goView(row) {
  router.push(`/report/dashboard/view/${row.dashId}`)
}

function formatTime(v) {
  if (!v) return ''
  return String(v).replace('T', ' ').slice(0, 19)
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.custom-dashboard-list {
  padding: 16px;
  background: #fff;
  min-height: calc(100vh - 100px);
}
.filter-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  .filter-row {
    display: flex;
    align-items: center;
    gap: 12px;
  }
}
.pagination {
  margin-top: 12px;
  text-align: right;
}
</style>
