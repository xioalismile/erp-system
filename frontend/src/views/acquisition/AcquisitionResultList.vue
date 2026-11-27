<template>
  <div class="erp-page">
    <!-- 顶部统计 -->
    <el-card class="dashboard-card" shadow="never">
      <div class="dashboard-row">
        <div class="stat-item" v-for="item in statItems" :key="item.key" @click="handleStatClick(item.key)">
          <div class="stat-label">{{ item.label }}</div>
          <div class="stat-value" :style="{ color: item.color }">{{ item.value }}</div>
        </div>
      </div>
    </el-card>

    <!-- 筛选区 -->
    <el-card shadow="never" style="margin-top:12px;">
      <div class="filter-bar">
        <el-select v-model="search.taskId" :placeholder="$tt('选择任务')" style="width:240px;" clearable filterable>
          <el-option v-for="t in taskOptions" :key="t.taskId" :label="(t.taskName || '') + (t.taskNo ? ` (${t.taskNo})` : '')" :value="t.taskId" />
        </el-select>
        <el-input v-model="search.keyword" :placeholder="$tt('公司名称关键词')" style="width:220px;" clearable @keyup.enter="handleSearch" />
        <el-select v-model="search.reviewStatus" :placeholder="$tt('审核状态')" style="width:130px;" clearable>
          <el-option v-for="s in reviewStatusOptions" :key="s.value" :label="$tf(s.label)" :value="s.value" />
        </el-select>
        <el-input v-model="search.industry" :placeholder="$tt('行业')" style="width:140px;" clearable />
        <el-button type="primary" v-permission="perm('query')" @click="handleSearch">{{ $tt("查询") }}</el-button>
        <el-button v-permission="perm('reset')" @click="handleReset">{{ $tt("重置") }}</el-button>
        <div style="flex:1;"></div>
        <el-tag type="info" size="large" effect="plain" style="padding:6px 12px;">
          审批通过后将自动写入公海池
        </el-tag>
      </div>
    </el-card>

    <!-- 结果列表 -->
    <el-card shadow="never" style="margin-top:12px;">
      <div style="margin-bottom:8px;">
        <el-button type="primary" size="small" :disabled="!selectedRows.length" v-permission="perm('audit')" @click="handleBatchReview('approve')">{{ $tt("批量通过") }}</el-button>
        <el-button type="warning" size="small" :disabled="!selectedRows.length" v-permission="perm('reject')" @click="handleBatchReview('reject')">{{ $tt("批量驳回") }}</el-button>
        <el-button type="danger" size="small" :disabled="!selectedRows.length" v-permission="perm('delete')" @click="handleBatchDelete">{{ $tt("批量删除") }}</el-button>
      </div>
      <el-table :data="results" v-loading="loading" border stripe @selection-change="handleSelectionChange" @sort-change="onSortChange">
        <el-table-column type="selection" width="50" />
        <el-table-column prop="companyName" :label="$tf('公司名称')" min-width="180" show-overflow-tooltip fixed="left" sortable="custom">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="handleViewDetail(row)" style="font-weight:500;">{{ row.companyName }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="creditCode" :label="$tf('统一社会信用代码')" width="180" show-overflow-tooltip sortable="custom"/>
        <el-table-column prop="legalPerson" :label="$tf('法人代表')" width="110" sortable="custom"/>
        <el-table-column prop="establishDate" :label="$tf('成立日期')" width="110" sortable="custom"/>
        <el-table-column prop="registeredCapital" :label="$tf('注册资本')" width="120" sortable="custom"/>
        <el-table-column prop="area" :label="$tf('所属地区')" width="100" sortable="custom"/>
        <el-table-column prop="industry" :label="$tf('所属行业')" width="120" show-overflow-tooltip sortable="custom"/>
        <el-table-column prop="registeredAddress" :label="$tf('注册地址')" width="200" show-overflow-tooltip sortable="custom"/>
        <el-table-column prop="contactAddress" :label="$tf('通信地址')" width="200" show-overflow-tooltip sortable="custom"/>
        <el-table-column prop="contactPhone" :label="$tf('联系电话')" width="130" sortable="custom"/>
        <el-table-column prop="contactEmail" :label="$tf('邮箱')" width="170" show-overflow-tooltip sortable="custom"/>
        <el-table-column prop="businessScope" :label="$tf('经营范围')" width="220" show-overflow-tooltip sortable="custom"/>
        <el-table-column :label="$tf('审核状态')" width="100">
          <template #default="{ row }">
            <el-tag :type="reviewStatusType(row.reviewStatus)" size="small">{{ reviewStatusLabel(row.reviewStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$tf('截图')" width="80" align="center">
          <template #default="{ row }">
            <el-image
              v-if="getFirstScreenshot(row.screenshotPath)"
              :src="getFirstScreenshot(row.screenshotPath)"
              :preview-src-list="[getFirstScreenshot(row.screenshotPath)]"
              fit="cover"
              style="width:40px;height:40px;border-radius:4px;cursor:pointer;"
              :preview-teleported="true"
            />
            <span v-else style="color:#c0c4cc;">-</span>
          </template>
        </el-table-column>
        <AuditColumns />
      </el-table>

      <div style="margin-top:16px;display:flex;justify-content:flex-end;">
        <el-pagination
          v-model:current-page="page.current"
          v-model:page-size="page.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="page.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="$tf('采集结果详情')" width="900px" append-to-body>
      <el-tabs v-model="detailTab">
        <el-tab-pane :label="$tf('基本信息')" name="basic">
          <el-descriptions :column="2" border>
            <el-descriptions-item :label="$tf('公司名称')">{{ detail.companyName }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('统一社会信用代码')">{{ detail.creditCode }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('法人代表')">{{ detail.legalPerson }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('成立日期')">{{ detail.establishDate || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('注册资本')">{{ detail.registeredCapital }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('所属地区')">{{ detail.area || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('所属行业')">{{ detail.industry }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('联系电话')">{{ detail.contactPhone }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('邮箱')">{{ detail.contactEmail }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('企业状态')">{{ detail.companyStatus || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('注册地址')" :span="2">{{ detail.registeredAddress }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('通信地址')" :span="2">{{ detail.contactAddress || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('经营范围')" :span="2">{{ detail.businessScope }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('审核状态')">
              <el-tag :type="reviewStatusType(detail.reviewStatus)" size="small">{{ reviewStatusLabel(detail.reviewStatus) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="$tf('驳回原因')">{{ detail.rejectReason || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('关联线索ID')">{{ detail.importedLeadId || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('来源URL')">{{ detail.sourceUrl || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('采集时间')" :span="2">{{ detail.createDate || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
        <el-tab-pane :label="$tf('采集截图')" name="screenshot">
          <div v-if="getFirstScreenshot(detail.screenshotPath)" class="screenshot-wrap">
            <el-image
              :src="getFirstScreenshot(detail.screenshotPath)"
              :preview-src-list="getAllScreenshots(detail.screenshotPath)"
              fit="contain"
              style="max-width:100%;max-height:520px;"
              :preview-teleported="true"
            />
          </div>
          <div v-else class="empty-tip">{{ $tt("暂无截图") }}</div>
        </el-tab-pane>
        <el-tab-pane :label="$tf('AI识别结果')" name="ai">
          <pre class="ai-json">{{ formatAiResult(detail.aiRawResult) }}</pre>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button v-permission="perm('dlgClose')" @click="detailVisible = false">{{ $tt("关闭") }}</el-button>
      </template>
    </el-dialog>

    <!-- 驳回原因弹窗 -->
    <el-dialog v-model="rejectVisible" :title="$tf('驳回原因')" width="500px" append-to-body>
      <el-form label-width="80px">
        <el-form-item :label="$tf('驳回原因')" required>
          <el-input v-model="rejectReason" type="textarea" :rows="3" :placeholder="$tt('请输入驳回原因')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="rejectVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('reject')" @click="confirmReject" :loading="saving">{{ $tt("确认驳回") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter, useRoute } from 'vue-router'
import {
  listResult, getResult, reviewResult, batchImportToLeads, pendingCount,
  listTask, batchDeleteResult
} from '@/api/acquisition'
import { listAllEmployee } from '@/api/basedata/employee'
import AuditColumns from '@/components/AuditColumns.vue'
import { useTableSort } from '@/composables/useTableSort'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('acquisition:result')
const { handleSortChange, getSortParams } = useTableSort()


const onSortChange = (e) => { handleSortChange(e); loadData() }
const router = useRouter()
const route = useRoute()

// 审核状态选项（前端显示中文，后端存储英文）
const reviewStatusOptions = [
  { value: 'pending', label: '待审核' },
  { value: 'approved', label: '已通过' },
  { value: 'rejected', label: '已驳回' },
  { value: 'imported', label: '已导入' }
]
const reviewStatusLabel = (v) => reviewStatusOptions.find(s => s.value === v)?.label || '待审核'
const reviewStatusType = (s) => ({ pending: 'warning', approved: 'success', rejected: 'danger', imported: 'primary' }[s] || 'warning')

// 数据状态
const loading = ref(false)
const saving = ref(false)
const results = ref([])
const taskOptions = ref([])
const employeeOptions = ref([])
const search = reactive({
  taskId: route.query.taskId ? Number(route.query.taskId) : null,
  keyword: '',
  reviewStatus: '',
  industry: ''
})
const page = reactive({ current: 1, size: 20, total: 0, ...getSortParams() })

// 统计
const stat = reactive({ pending: 0, approved: 0, rejected: 0, imported: 0 })
const statItems = computed(() => [
  { key: 'pending', label: '待审核', value: stat.pending, color: '#F56C6C' },
  { key: 'approved', label: '已通过', value: stat.approved, color: '#67C23A' },
  { key: 'rejected', label: '已驳回', value: stat.rejected, color: '#909399' },
  { key: 'imported', label: '已导入', value: stat.imported, color: '#409EFF' }
])

// 多选
const selectedRows = ref([])
const batchAssignSalesId = ref(null)

// 详情弹窗
const detailVisible = ref(false)
const detail = ref({})
const detailTab = ref('basic')

// 驳回弹窗
const rejectVisible = ref(false)
const rejectReason = ref('')
const rejectTarget = ref(null)

// 截图路径解析（后端存JSON数组字符串或单一路径）
const getAllScreenshots = (path) => {
  if (!path) return []
  if (Array.isArray(path)) return path
  if (path.startsWith('[')) {
    try { return JSON.parse(path) } catch { /* fallthrough */ }
  }
  return [path]
}
const getFirstScreenshot = (path) => {
  const list = getAllScreenshots(path)
  return list[0] || ''
}

// AI结果格式化
const formatAiResult = (raw) => {
  if (!raw) return '暂无AI识别结果'
  try {
    if (typeof raw === 'string') {
      return JSON.stringify(JSON.parse(raw), null, 2)
    }
    return JSON.stringify(raw, null, 2)
  } catch (e) {
    return String(raw)
  }
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const params = {
      current: page.current, size: page.size,
      taskId: search.taskId || undefined,
      keyword: search.keyword || undefined,
      reviewStatus: search.reviewStatus || undefined,
      industry: search.industry || undefined,
      ...getSortParams()
    }
    const res = await listResult(params)
    // request拦截器已解包 Result.data，这里res直接是PageResult
    const data = res || {}
    results.value = data.records || []
    page.total = data.total || 0
  } catch (e) {
    console.error('加载采集结果失败', e)
    ElMessage.error(tt('加载采集结果失败'))
  } finally {
    loading.value = false
  }
}

const loadStat = async () => {
  try {
    const res = await pendingCount()
    // request拦截器已解包，res直接是数据（数字）
    stat.pending = Number(res) || 0
  } catch (e) { /* 忽略 */ }
}

const loadTaskOptions = async () => {
  try {
    const res = await listTask({ current: 1, size: 200, ...getSortParams() })
    // request拦截器已解包，res直接是PageResult
    const data = res || {}
    taskOptions.value = data.records || []
  } catch (e) { /* 忽略 */ }
}

const loadEmployeeOptions = async () => {
  try {
    const empList = await listAllEmployee()
    employeeOptions.value = (Array.isArray(empList) ? empList : (empList?.records || []))
      .map(e => ({ value: e.employeeId, label: e.employeeName + (e.deptName ? ' (' + e.deptName + ')' : '') }))
  } catch (e) { console.warn('加载员工失败', e) }
}

const handleSearch = () => { page.current = 1; loadData() }
const handleReset = () => {
  search.taskId = null; search.keyword = ''; search.reviewStatus = ''; search.industry = ''
  page.current = 1; loadData()
}

const handleStatClick = (key) => {
  search.reviewStatus = key
  page.current = 1
  loadData()
}

// 多选
const handleSelectionChange = (rows) => {
  selectedRows.value = rows
}

const handleBatchReview = (action) => {
  if (!selectedRows.value.length) {
    ElMessage.warning(tt('请先选择数据'))
    return
  }
  if (action === 'approve') {
    ElMessageBox.confirm(`${tt("确认批量通过 ")}${selectedRows.value.length}${tt(" 条数据？\\n\\n审核通过后将自动写入公海池。")}`, tt('批量审核'), { type: 'success' })
      .then(async () => {
        saving.value = true
        try {
          await Promise.all(selectedRows.value.map(r => reviewResult(r.resultId, { reviewStatus: 'approved' })))
          ElMessage.success(`${tt("已批量通过 ")}${selectedRows.value.length}${tt(" 条，已自动写入公海池")}`)
          loadData(); loadStat()
        } finally { saving.value = false }
      })
      .catch(() => {})
  } else {
    rejectTarget.value = { type: 'batch', ids: selectedRows.value.map(r => r.resultId) }
    rejectReason.value = ''
    rejectVisible.value = true
  }
}

const confirmReject = async () => {
  if (!rejectReason.value.trim()) {
    ElMessage.warning(tt('请输入驳回原因'))
    return
  }
  saving.value = true
  try {
    const target = rejectTarget.value
    if (target.type === 'single') {
      await reviewResult(target.row.resultId, { reviewStatus: 'rejected', rejectReason: rejectReason.value })
    } else {
      await Promise.all(target.ids.map(id => reviewResult(id, { reviewStatus: 'rejected', rejectReason: rejectReason.value })))
    }
    ElMessage.success(tt('已驳回'))
    rejectVisible.value = false
    loadData(); loadStat()
  } finally {
    saving.value = false
  }
}

const handleBatchImport = async () => {
  if (!selectedRows.value.length) {
    ElMessage.warning(tt('请先选择数据'))
    return
  }
  if (!batchAssignSalesId.value) {
    ElMessage.warning(tt('请选择销售员'))
    return
  }
  try {
    await ElMessageBox.confirm(`${tt("确认将选中的 ")}${selectedRows.value.length}${tt(" 条数据导入CRM线索库？")}`, tt('批量导入确认'), { type: 'warning' })
    const ids = selectedRows.value.map(r => r.resultId)
    const res = await batchImportToLeads({ resultIds: ids, assignedSalesId: batchAssignSalesId.value })
    const count = Number(res) || ids.length
    ElMessage.success(`${tt("批量导入完成，共 ")}${count}${tt(" 条")}`)
    loadData(); loadStat()
  } catch (e) { /* 取消 */ }
}

// 批量删除
const handleBatchDelete = async () => {
  if (!selectedRows.value.length) {
    ElMessage.warning(tt('请先选择数据'))
    return
  }
  try {
    await ElMessageBox.confirm(`${tt("确认删除选中的 ")}${selectedRows.value.length}${tt(" 条采集结果？删除后无法恢复。")}`, tt('批量删除确认'), { type: 'warning' })
    const ids = selectedRows.value.map(r => r.resultId)
    await batchDeleteResult(ids)
    ElMessage.success(`${tt("已删除 ")}${ids.length}${tt(" 条")}`)
    loadData(); loadStat()
  } catch (e) { /* 取消 */ }
}

// 详情
const handleViewDetail = async (row) => {
  try {
    const res = await getResult(row.resultId)
    // request拦截器已解包，res直接是数据对象
    detail.value = { ...row, ...(res || {}) }
  } catch (e) {
    detail.value = { ...row }
  }
  detailTab.value = 'basic'
  detailVisible.value = true
}

onMounted(() => {
  loadData()
  loadStat()
  loadTaskOptions()
  loadEmployeeOptions()
})
useAutoRefresh(() => {
  loadData()
  loadStat()
  loadTaskOptions()
  loadEmployeeOptions()
})
</script>

<style scoped lang="scss">
.erp-page { padding: 0; }
.dashboard-card { background: linear-gradient(135deg, #f5f7fa 0%, #ffffff 100%); }
.dashboard-row { display: flex; gap: 16px; flex-wrap: wrap; align-items: stretch; }
.stat-item {
  flex: 1; min-width: 140px;
  background: #fff; border-radius: 8px; padding: 14px 20px;
  border: 1px solid #ebeef5;
  display: flex; flex-direction: column; justify-content: center;
  cursor: pointer; transition: all 0.2s;
}
.stat-item:hover { box-shadow: 0 2px 12px rgba(0,0,0,0.1); transform: translateY(-2px); }
.stat-label { font-size: 13px; color: #909399; margin-bottom: 6px; }
.stat-value { font-size: 26px; font-weight: 700; }
.filter-bar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.empty-tip { text-align: center; padding: 40px 0; color: #909399; font-size: 14px; }
.screenshot-wrap { text-align: center; padding: 12px; }
.ai-json {
  background: #f5f7fa; padding: 12px; border-radius: 4px;
  max-height: 480px; overflow: auto;
  font-family: Consolas, Monaco, "Courier New", monospace;
  font-size: 12px; color: #303133; white-space: pre-wrap;
  word-break: break-all;
}
</style>
