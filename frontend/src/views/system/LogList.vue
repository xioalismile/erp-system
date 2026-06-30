<template>
  <div class="erp-page">
    <div class="filter-bar">
      <el-input
        v-model="query.keyword"
        placeholder="关键词"
        class="filter-input"
        clearable
        @keyup.enter="onSearch"
      />
      <el-select v-model="query.operationType" placeholder="操作类型" clearable class="filter-select">
        <el-option label="新增" value="新增" />
        <el-option label="编辑" value="编辑" />
        <el-option label="删除" value="删除" />
        <el-option label="审核" value="审核" />
        <el-option label="反审核" value="反审核" />
        <el-option label="登录" value="登录" />
        <el-option label="登出" value="登出" />
        <el-option label="导出" value="导出" />
      </el-select>
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DD"
        class="filter-date"
      />
      <el-button type="primary" @click="onSearch">查询</el-button>
      <el-button @click="onReset">重置</el-button>
    </div>

    <el-table :data="tableData" border v-loading="loading">
      <el-table-column prop="operationTime" label="操作时间" min-width="160" />
      <el-table-column prop="operator" label="操作人" min-width="110" />
      <el-table-column prop="department" label="部门" min-width="120" />
      <el-table-column label="操作类型" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="operationTagType(row.operationType)" size="small">
            {{ row.operationType }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="moduleName" label="模块名称" min-width="120" />
      <el-table-column prop="operationContent" label="操作内容" min-width="220" show-overflow-tooltip />
      <el-table-column label="操作结果" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.operationResult === '成功' ? 'success' : 'danger'" size="small">
            {{ row.operationResult }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="ipAddress" label="IP地址" min-width="130" />
      <el-table-column prop="duration" label="时长(ms)" width="110" align="right" />
    </el-table>

    <el-pagination
      class="pagination"
      v-model:current-page="query.current"
      v-model:page-size="query.size"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      @size-change="loadData"
      @current-change="loadData"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { pageLog } from '@/api/system/log'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({
  current: 1,
  size: 20,
  keyword: '',
  operationType: '',
  startDate: '',
  endDate: ''
})
const dateRange = ref([])

function operationTagType(type) {
  return {
    '新增': 'success',
    '编辑': 'primary',
    '删除': 'danger',
    '审核': 'warning',
    '反审核': 'warning',
    '登录': 'info',
    '登出': 'info',
    '导出': 'success'
  }[type] || 'info'
}

async function loadData() {
  loading.value = true
  try {
    if (dateRange.value && dateRange.value.length === 2) {
      query.startDate = dateRange.value[0]
      query.endDate = dateRange.value[1]
    } else {
      query.startDate = ''
      query.endDate = ''
    }
    const res = await pageLog(query)
    tableData.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.current = 1
  loadData()
}

function onReset() {
  query.keyword = ''
  query.operationType = ''
  query.startDate = ''
  query.endDate = ''
  query.current = 1
  dateRange.value = []
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.filter-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
  align-items: center;
}
.filter-input {
  width: 200px;
}
.filter-select {
  width: 140px;
}
.filter-date {
  width: 260px;
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
