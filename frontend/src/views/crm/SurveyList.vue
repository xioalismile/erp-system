<template>
  <div class="erp-page">
    <!-- 满意度调查仪表盘 -->
    <el-card class="dashboard-card" shadow="never">
      <div class="dashboard-row">
        <div class="stat-item" v-for="item in statItems" :key="item.key" @click="handleStatClick(item.key)">
          <div class="stat-label">{{ item.label }}</div>
          <div class="stat-value" :style="{ color: item.color }">{{ item.value }}</div>
          <div class="stat-hint">{{ $tt("点击查看明细") }}</div>
        </div>
      </div>
      <div class="dashboard-row" style="margin-top:12px;">
        <div class="dist-block">
          <div class="dist-title">{{ $tt("状态分布") }}</div>
          <div class="dist-tags">
            <el-tag v-for="(v, k) in dashboard.statusDist || {}" :key="k"
                    :type="statusType(k)" size="large" class="dist-tag">
              {{ k }}: {{ v }}
            </el-tag>
          </div>
        </div>
        <div class="dist-block">
          <div class="dist-title">{{ $tt("调查类型分布") }}</div>
          <div class="dist-tags">
            <el-tag v-for="(v, k) in dashboard.typeDist || {}" :key="k"
                    type="info" size="large" class="dist-tag">
              {{ k }}: {{ v }}
            </el-tag>
          </div>
        </div>
        <div class="dist-block">
          <div class="dist-title">{{ $tt("各项平均评分") }}</div>
          <div class="dist-tags">
            <el-tag v-for="item in scoreDistItems" :key="item.key"
                    :type="item.type" size="large" class="dist-tag">
              {{ item.label }}: {{ item.value }}
            </el-tag>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 筛选区 -->
    <el-card shadow="never" style="margin-top:12px;">
      <div class="filter-bar">
        <el-input v-model="search.keyword" :placeholder="$tt('调查单号/名称/客户/联系人')" style="width:260px;" clearable />
        <el-select v-model="search.status" :placeholder="$tt('状态')" style="width:130px;" clearable>
          <el-option v-for="s in statusOptions" :key="s" :label="s" :value="s" />
        </el-select>
        <el-select v-model="search.surveyType" :placeholder="$tt('调查类型')" style="width:140px;" clearable>
          <el-option v-for="s in surveyTypeOptions" :key="s" :label="s" :value="s" />
        </el-select>
        <el-button type="primary" v-permission="perm('query')" @click="handleSearch">{{ $tt("查询") }}</el-button>
        <el-button v-permission="perm('reset')" @click="handleReset">{{ $tt("重置") }}</el-button>
        <div style="flex:1;"></div>
        <el-tooltip :content="$tt('列设置')" placement="top">
          <el-button :icon="Setting" circle v-permission="perm('columnSetting')" @click="openColumnSetting" />
        </el-tooltip>
        <el-button type="primary" v-permission="perm('add')" @click="handleAdd">{{ $tt("+ 新增调查") }}</el-button>
        <el-button type="success" v-permission="perm('export')" @click="handleExport">{{ $tt("导出") }}</el-button>
        <el-upload
          :show-file-list="false"
          :before-upload="handleImport"
          accept=".xlsx,.xls"
          style="margin-left: 10px;"
        >
          <el-button type="warning" v-permission="perm('import')">{{ $tt("导入") }}</el-button>
        </el-upload>
      </div>
    </el-card>

    <!-- 调查列表 -->
    <el-card shadow="never" style="margin-top:12px;">
      <el-table :data="surveys" v-loading="loading" border stripe :key="configVersion" @sort-change="onSortChange">
        <!-- 动态列渲染：列顺序由列设置拖拽配置驱动（sortedColumns），单元格按 col.type 分支渲染 -->
        <template v-for="col in visibleSortedColumns" :key="col.prop">
          <el-table-column
            :prop="col.prop"
            :label="$tf(col.label)"
            :width="col.width"
            :min-width="col.minWidth"
            :align="col.align || 'left'"
            :sortable="col.sortable === 'local' ? true : (col.sortable ? 'custom' : false)"
            :show-overflow-tooltip="!!col.tooltip"
            :fixed="getColFixed(col.prop, col.fixed)"
          >
            <template v-if="col.type" #default="{ row }">
              <el-tag v-if="col.type === 'tag'" :type="col.tagType ? col.tagType(row) : 'info'" size="small">{{ col.tagText ? col.tagText(row) : (row[col.prop] || '-') }}</el-tag>
              <el-rate v-else-if="col.type === 'custom' && col.prop === 'overallScore'" :model-value="row.overallScore || 0" :max="5" disabled size="small" show-score />
            </template>
          </el-table-column>
        </template>
        <AuditColumns />
        <!-- 操作列：编辑 + 更多（状态变更/分析/删除） -->
        <el-table-column :label="$tf('操作')" width="140" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" v-permission="perm('edit')" @click="handleEdit(row)">{{ $tt("编辑") }}</el-button>
            <el-dropdown trigger="click" @command="(cmd) => handleRowCommand(cmd, row)">
              <el-button type="primary" link size="small">{{ $tt("更多") }}<el-icon style="margin-left:2px;"><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="status">{{ $tt("状态变更") }}</el-dropdown-item>
                  <el-dropdown-item command="analyze">{{ $tt("调查分析") }}</el-dropdown-item>
                  <el-dropdown-item command="delete" divided>{{ $tt("删除") }}</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑调查' : '新增调查'" width="900px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px">
        <el-divider content-position="left">{{ $tt("基础信息") }}</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('调查单号')">
              <el-input v-model="formData.surveyNo" :disabled="docCodeDisabled && !isEdit" :placeholder="docCodePlaceholder" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item :label="$tf('调查名称')" prop="surveyName">
              <el-input v-model="formData.surveyName" :placeholder="$tt('如 2026年二季度客户满意度调查')" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('调查类型')" prop="surveyType">
              <el-select v-model="formData.surveyType" style="width:100%">
                <el-option v-for="s in surveyTypeOptions" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('客户ID')" prop="customerId">
              <el-select v-model="formData.customerId" :placeholder="$tt('请选择客户')" filterable clearable style="width:100%">
                <el-option v-for="opt in withFallbackOption(customerOptions, 'customerId', 'customerName', formData)" :key="opt.value" :label="$tf(opt.label)" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('客户名称')">
              <el-input v-model="formData.customerName" :placeholder="$tt('客户名称')" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('联系人')" prop="contactPerson">
              <el-input v-model="formData.contactPerson" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('调查日期')">
              <el-date-picker v-model="formData.surveyDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('调查方式')" prop="surveyMethod">
              <el-select v-model="formData.surveyMethod" style="width:100%">
                <el-option v-for="s in surveyMethodOptions" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('状态')">
              <el-select v-model="formData.status" style="width:100%">
                <el-option v-for="s in statusOptions" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">{{ $tt("评分（1-5分）") }}</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('综合评分')" prop="overallScore">
              <el-rate v-model="formData.overallScore" :max="5" show-score />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('产品评分')">
              <el-rate v-model="formData.productScore" :max="5" show-score />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('服务评分')">
              <el-rate v-model="formData.serviceScore" :max="5" show-score />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('价格评分')">
              <el-rate v-model="formData.priceScore" :max="5" show-score />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('交期评分')">
              <el-rate v-model="formData.deliveryScore" :max="5" show-score />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('售后评分')">
              <el-rate v-model="formData.afterSalesScore" :max="5" show-score />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">{{ $tt("反馈内容") }}</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="$tf('表扬内容')">
              <el-input v-model="formData.praiseContent" type="textarea" :rows="3" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('批评内容')">
              <el-input v-model="formData.criticismContent" type="textarea" :rows="3" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="$tf('改进计划')">
              <el-input v-model="formData.improvementPlan" type="textarea" :rows="3" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('备注')">
              <el-input v-model="formData.remark" type="textarea" :rows="3" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="dialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('save')" @click="handleSave" :loading="saving">{{ $tt("保存") }}</el-button>
      </template>
    </el-dialog>

    <!-- 状态变更弹窗 -->
    <el-dialog v-model="statusDialogVisible" :title="$tf('状态变更')" width="400px">
      <el-form label-width="100px">
        <el-form-item :label="$tf('调查单号')">
          <span>{{ statusForm.surveyNo }}</span>
        </el-form-item>
        <el-form-item :label="$tf('新状态')">
          <el-select v-model="statusForm.status" style="width:100%">
            <el-option v-for="s in statusOptions" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="statusDialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('dlgConfirm')" @click="confirmStatusChange" :loading="saving">{{ $tt("确认") }}</el-button>
      </template>
    </el-dialog>

    <!-- 分析弹窗 -->
    <el-dialog v-model="analyzeDialogVisible" :title="$tf('调查分析')" width="600px">
      <el-form ref="analyzeFormRef" :model="analyzeForm" :rules="analyzeRules" label-width="100px">
        <el-form-item :label="$tf('调查单号')">
          <span>{{ analyzeForm.surveyNo }}</span>
        </el-form-item>
        <el-form-item :label="$tf('分析结果')" prop="analyzeResult">
          <el-input v-model="analyzeForm.analyzeResult" type="textarea" :rows="6"
                    :placeholder="$tt('请输入调查分析结果、改进建议等')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="analyzeDialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('dlgConfirm')" @click="confirmAnalyze" :loading="saving">{{ $tt("确认分析") }}</el-button>
      </template>
    </el-dialog>

    <!-- 列设置抽屉 -->
    <ColumnSettingDrawer v-model="columnSettingVisible" :page-key="'crm-survey'" :columns="allColumns" />
  </div>
</template>

<script setup>
import { withFallbackOption } from '@/utils/labelFallback'
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Setting, ArrowDown } from '@element-plus/icons-vue'
import {
  listSurvey, getSurvey, saveSurvey, updateSurvey, deleteSurvey,
  updateSurveyStatus, analyzeSurvey, surveyDashboard
} from '@/api/crm/survey'
import { listAllCustomer } from '@/api/basedata/customer'
import { exportListAsSeen, importData } from '@/utils/exportUtil'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
import AuditColumns from '@/components/AuditColumns.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { useAutoDocCode } from '@/composables/useAutoDocCode'
import { useTableSort } from '@/composables/useTableSort'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('crm:survey')
const { handleSortChange, getSortParams } = useTableSort()


const onSortChange = (e) => { handleSortChange(e); loadData() }
// === 列设置 ===
// 列定义（用于列设置抽屉+动态渲染）：type 标记单元格渲染分支
const allColumns = [
  { prop: 'surveyNo', label: '调查单号', width: 140, fixed: 'left', sortable: true, type: 'link', linkClick: (row) => handleEdit(row) },
  { prop: 'surveyName', label: '调查名称', minWidth: 180, tooltip: true, sortable: true },
  { prop: 'customerName', label: '客户名称', minWidth: 160, tooltip: true, sortable: true },
  { prop: 'contactPerson', label: '联系人', width: 100, sortable: true },
  { prop: 'surveyDate', label: '调查日期', width: 110, sortable: true },
  { prop: 'surveyType', label: '调查类型', width: 110, type: 'tag', tagType: () => 'info', tagText: (row) => row.surveyType || '-' },
  { prop: 'surveyMethod', label: '调查方式', width: 100, sortable: true },
  { prop: 'overallScore', label: '综合评分', width: 160, type: 'custom' },
  { prop: 'status', label: '状态', width: 100, type: 'tag', tagType: (row) => statusType(row.status), tagText: (row) => row.status || '草稿' },
  { prop: 'analyzeResult', label: '分析结果', minWidth: 200, tooltip: true, sortable: true }
]
const { columnSettingVisible, openColumnSetting, isColVisible, getColFixed, configVersion, sortedColumns } = useColumnSetting('crm-survey', allColumns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))

// 选项常量 (严格遵循文档 7.6 定义)
const statusOptions = ['草稿', '进行中', '已完成', '已分析']
const surveyTypeOptions = ['交易后', '定期回访', '新品反馈', '服务评价', '其他']
const surveyMethodOptions = ['电话', '问卷', '在线评价', '现场面谈', '其他']

// 数据状态
const loading = ref(false)
const saving = ref(false)
const surveys = ref([])
const dashboard = ref({})
const search = reactive({ keyword: '', status: '', surveyType: '' })
const page = reactive({ current: 1, size: 20, total: 0, ...getSortParams() })

// 仪表盘统计项
const statItems = computed(() => [
  { key: 'total', label: '调查总数', value: dashboard.value.totalSurveys || 0, color: '#409EFF' },
  { key: 'avgOverall', label: '平均综合评分', value: (Number(dashboard.value.avgOverall || 0)).toFixed(1), color: '#67C23A' },
  { key: 'lowScore', label: '低分调查(≤3分)', value: dashboard.value.lowScoreCount || 0, color: '#F56C6C' }
])

// 各项平均评分分布
const scoreDistItems = computed(() => [
  { key: 'product', label: '产品', value: Number(dashboard.value.avgProductScore || 0).toFixed(1), type: '' },
  { key: 'service', label: '服务', value: Number(dashboard.value.avgServiceScore || 0).toFixed(1), type: 'success' },
  { key: 'price', label: '价格', value: Number(dashboard.value.avgPriceScore || 0).toFixed(1), type: 'warning' },
  { key: 'delivery', label: '交期', value: Number(dashboard.value.avgDeliveryScore || 0).toFixed(1), type: 'info' },
  { key: 'afterSales', label: '售后', value: Number(dashboard.value.avgAfterSalesScore || 0).toFixed(1), type: 'danger' }
])

// 弹窗状态
const dialogVisible = ref(false)
const isEdit = ref(false)
const { docCodeDisabled, docCodePlaceholder, autoFillDocCodeOnCreate } = useAutoDocCode('SURVEY')
const formRef = ref(null)
const defaultForm = () => ({
  surveyId: null, surveyNo: '', surveyName: '', surveyType: '交易后',
  customerId: null, customerName: '', contactPerson: '',
  surveyDate: '', surveyMethod: '电话', status: '草稿',
  overallScore: 0, productScore: 0, serviceScore: 0, priceScore: 0,
  deliveryScore: 0, afterSalesScore: 0,
  praiseContent: '', criticismContent: '', improvementPlan: '',
  analyzeResult: '', remark: ''
})
const formData = reactive(defaultForm())
const formRules = {
  surveyName: [{ required: true, message: tt('请输入调查名称'), trigger: 'blur' }],
  surveyType: [{ required: true, message: tt('请选择调查类型'), trigger: 'change' }],
  customerId: [{ required: true, message: tt('请选择客户'), trigger: 'change' }],
  contactPerson: [{ required: true, message: tt('请输入联系人'), trigger: 'blur' }],
  surveyMethod: [{ required: true, message: tt('请选择调查方式'), trigger: 'change' }],
  overallScore: [{ required: true, message: tt('请选择综合评分'), trigger: 'change' }]
}

// 状态变更弹窗
const statusDialogVisible = ref(false)
const statusForm = reactive({ surveyId: null, surveyNo: '', status: '' })

// 分析弹窗
const analyzeDialogVisible = ref(false)
const analyzeFormRef = ref(null)
const analyzeForm = reactive({ surveyId: null, surveyNo: '', analyzeResult: '' })
const analyzeRules = {
  analyzeResult: [{ required: true, message: tt('请输入分析结果'), trigger: 'blur' }]
}

// 状态颜色映射
const statusType = (s) => {
  const map = { '草稿': 'info', '进行中': 'warning', '已完成': 'success', '已分析': 'danger' }
  return map[s] || 'info'
}

// 加载数据
/**
 * 列表与导出**共用**的查询条件（2026-09-24 全站口径）：
 * 导出必须与列表用同一套条件，抽成一个函数避免两处各写一份导致口径漂移
 * （用户要求"页面上看到什么列，导出就带什么列；导出行 = 当前筛选条件下的全部"）。
 */
const buildQueryParams = () => ({
  keyword: search.keyword || undefined,
  status: search.status || undefined,
  surveyType: search.surveyType || undefined,
  ...getSortParams()
})

const loadData = async () => {
  loading.value = true
  try {
    const res = await listSurvey({ page: page.current, size: page.size, ...buildQueryParams() })
    surveys.value = res.records || []
    page.total = res.total || 0
  } finally {
    loading.value = false
  }
}

const loadDashboard = async () => {
  try {
    dashboard.value = await surveyDashboard() || {}
  } catch (e) { /* 忽略仪表盘加载错误 */ }
}

const handleSearch = () => { page.current = 1; loadData() }
const handleReset = () => {
  search.keyword = ''; search.status = ''; search.surveyType = ''
  page.current = 1; loadData()
}

const handleStatClick = (key) => {
  if (key === 'lowScore') {
    search.status = ''
    search.surveyType = ''
    search.keyword = ''
    page.current = 1
    loadData()
    ElMessage.info(tt('已筛选低分调查（综合评分≤3分）'))
  } else {
    search.keyword = ''; search.status = ''; search.surveyType = ''
    page.current = 1
    loadData()
    ElMessage.info(tt('已显示全部调查'))
  }
}

// 导入导出
const handleExport = async () => {
  try {
    // 页面上看到什么列，导出就带什么列；导出行 = 当前筛选条件下的全部
    await exportListAsSeen({
      entityName: 'survey',
      columns: visibleSortedColumns.value,
      fetchPage: listSurvey,
      params: buildQueryParams(),
      idField: 'surveyId'
    })
    ElMessage.success(tt('导出成功'))
  } catch (e) {
    // 失败原因由 exportUtil 统一提示
  }
}

const handleImport = async (file) => {
  try {
    const result = await importData('survey', file)
    ElMessage.success(`${tt("导入成功: ")}${result.successCount}${tt("条, 失败: ")}${result.failCount}${tt("条")}`)
    loadData()
  } catch (e) {
    ElMessage.error(tt('导入失败'))
  }
  return false
}

// 新增/编辑
const handleAdd = () => {
  isEdit.value = false
  Object.assign(formData, defaultForm())
  autoFillDocCodeOnCreate(formData, 'surveyNo')
  dialogVisible.value = true
}

const handleEdit = async (row) => {
  isEdit.value = true
  try {
    const detail = await getSurvey(row.surveyId)
    Object.assign(formData, defaultForm(), detail)
    dialogVisible.value = true
  } catch (e) { /* 忽略 */ }
}

const handleSave = async () => {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (isEdit.value) {
      await updateSurvey(formData.surveyId, formData)
    } else {
      await saveSurvey(formData)
    }
    ElMessage.success(isEdit.value ? tt('修改成功') : tt('新增成功'))
    dialogVisible.value = false
    loadData()
    loadDashboard()
  } finally {
    saving.value = false
  }
}

const confirmStatusChange = async () => {
  if (!statusForm.status) {
    ElMessage.warning(tt('请选择状态'))
    return
  }
  saving.value = true
  try {
    await updateSurveyStatus(statusForm.surveyId, statusForm.status)
    ElMessage.success(tt('状态变更成功'))
    statusDialogVisible.value = false
    loadData()
    loadDashboard()
  } finally {
    saving.value = false
  }
}

const confirmAnalyze = async () => {
  await analyzeFormRef.value?.validate()
  saving.value = true
  try {
    await analyzeSurvey(analyzeForm.surveyId, analyzeForm.analyzeResult)
    ElMessage.success(tt('分析提交成功'))
    analyzeDialogVisible.value = false
    loadData()
    loadDashboard()
  } finally {
    saving.value = false
  }
}

// 行内"更多"下拉分发：状态变更/分析/删除
const handleRowCommand = (cmd, row) => {
  if (cmd === 'status') {
    statusForm.surveyId = row.surveyId
    statusForm.surveyNo = row.surveyNo
    statusForm.status = row.status || '草稿'
    statusDialogVisible.value = true
  } else if (cmd === 'analyze') {
    analyzeForm.surveyId = row.surveyId
    analyzeForm.surveyNo = row.surveyNo
    analyzeForm.analyzeResult = ''
    analyzeDialogVisible.value = true
  } else if (cmd === 'delete') {
    handleDelete(row)
  }
}

// 删除调查
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `${tt("确认删除调查【")}${row.surveyNo}${tt("】？删除后不可恢复。")}`,
      tt('删除确认'),
      { type: 'warning', confirmButtonText: tt('确认删除'), cancelButtonText: tt('取消') }
    )
  } catch (e) {
    return
  }
  try {
    await deleteSurvey(row.surveyId)
    ElMessage.success(tt('删除成功'))
    loadData()
    loadDashboard()
  } catch (e) {
    // 拦截器已弹错误提示
  }
}

// 外键下拉选项
const customerOptions = ref([])
async function loadDropdownOptions() {
  try {
    const custList = await listAllCustomer()
    customerOptions.value = (Array.isArray(custList) ? custList : (custList?.records || []))
      .map(c => ({ value: c.customerId, label: c.customerName }))
  } catch (e) { console.warn('加载客户失败', e) }
}

onMounted(() => {
  loadData()
  loadDashboard()
  loadDropdownOptions()
})
useAutoRefresh(() => {
  loadData()
  loadDashboard()
  loadDropdownOptions()
})
</script>

<style scoped lang="scss">
.erp-page { padding: 0; }
.dashboard-card { background: linear-gradient(135deg, #f5f7fa 0%, #ffffff 100%); }
.dashboard-row { display: flex; gap: 16px; flex-wrap: wrap; align-items: stretch; }
.stat-item {
  flex: 1; min-width: 140px;
  background: #fff; border-radius: 8px; padding: 12px 20px;
  border: 1px solid #ebeef5;
  display: flex; flex-direction: column; justify-content: center;
  cursor: pointer; transition: all 0.2s;
}
.stat-item:hover { box-shadow: 0 2px 12px rgba(0,0,0,0.1); transform: translateY(-2px); }
.stat-label { font-size: 13px; color: #909399; margin-bottom: 4px; }
.stat-value { font-size: 26px; font-weight: 700; }
.stat-hint { font-size: 11px; color: #c0c4cc; margin-top: 4px; }
.dist-block {
  flex: 1; min-width: 280px;
  background: #fff; border-radius: 8px; padding: 10px 16px;
  border: 1px solid #ebeef5;
}
.dist-title { font-size: 13px; color: #606266; margin-bottom: 8px; font-weight: 600; }
.dist-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.dist-tag { margin: 0; }
.filter-bar {
  display: flex; gap: 8px; align-items: center; flex-wrap: wrap;
}
</style>
