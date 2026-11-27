<template>
  <div class="erp-page">
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
          <div class="dist-title">{{ $tt("客户状态分布") }}</div>
          <div class="dist-tags">
            <el-tag v-for="(v, k) in dashboard.crmStatusDist || {}" :key="k"
                    :type="crmStatusType(k)" size="large" class="dist-tag dist-tag-clickable"
                    @click="handleDistClick('crmStatus', k)">
              {{ k }}: {{ v }}
            </el-tag>
          </div>
        </div>
        <div class="dist-block">
          <div class="dist-title">{{ $tt("销售阶段分布") }}</div>
          <div class="dist-tags">
            <el-tag v-for="(v, k) in dashboard.salesStageDist || {}" :key="k"
                    :type="salesStageType(k)" size="large" class="dist-tag dist-tag-clickable"
                    @click="handleDistClick('salesStage', k)">
              {{ k }}: {{ v }}
            </el-tag>
          </div>
        </div>
        <div class="dist-block">
          <div class="dist-title">{{ $tt("意向等级分布") }}</div>
          <div class="dist-tags">
            <el-tag v-for="(v, k) in dashboard.intentionLevelDist || {}" :key="k"
                    :type="intentionLevelType(k)" size="large" class="dist-tag dist-tag-clickable"
                    @click="handleDistClick('intentionLevel', k)">
              {{ k }}级: {{ v }}
            </el-tag>
          </div>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" style="margin-top:12px;">
      <div class="filter-bar">
        <el-input v-model="search.keyword" :placeholder="$tt('编码/名称/简称/联系人/电话')" style="width:240px;" clearable />
        <el-select v-model="search.crmStatus" :placeholder="$tt('客户状态')" style="width:130px;" clearable>
          <el-option v-for="s in crmStatusOptions" :key="s" :label="s" :value="s" />
        </el-select>
        <el-select v-model="search.salesStage" :placeholder="$tt('销售阶段')" style="width:130px;" clearable>
          <el-option v-for="s in salesStageOptions" :key="s" :label="s" :value="s" />
        </el-select>
        <el-select v-model="search.intentionLevel" :placeholder="$tt('意向等级')" style="width:110px;" clearable>
          <el-option v-for="s in ['A','B','C','D']" :key="s" :label="s + '级'" :value="s" />
        </el-select>
        <el-button type="primary" v-permission="perm('query')" @click="handleSearch">{{ $tt("查询") }}</el-button>
        <el-button v-permission="perm('reset')" @click="handleReset">{{ $tt("重置") }}</el-button>
        <el-tooltip :content="$tt('列设置')" placement="top">
          <el-button :icon="Setting" circle v-permission="perm('columnSetting')" @click="openColumnSetting" />
        </el-tooltip>
        <div style="flex:1"></div>
        <el-button type="success" v-permission="perm('export')" @click="handleExport">{{ $tt("导出") }}</el-button>
        <el-button type="info" v-permission="perm('downloadTemplate')" @click="handleDownloadTemplate">{{ $tt("下载导入模板") }}</el-button>
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

    <el-card shadow="never" style="margin-top:12px;">
      <el-table :data="sortedCustomers" v-loading="loading" border stripe @sort-change="onSortChange" :key="configVersion">
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
              <el-link v-if="col.type === 'link'" type="primary" :underline="false" style="font-weight:500;" @click="col.linkClick && col.linkClick(row)">{{ row[col.prop] }}</el-link>
              <el-tag v-else-if="col.type === 'tag'" :type="col.tagType ? col.tagType(row) : 'info'" :effect="col.tagEffect || 'light'" size="small">{{ col.tagText ? col.tagText(row) : (row[col.prop] || '-') }}</el-tag>
              <span v-else-if="col.type === 'format'">{{ col.format(row) }}</span>
              <el-icon v-else-if="col.type === 'custom' && col.prop === 'isTop' && row.isTop === 1" :size="18" color="#E6A23C" class="top-icon"><Star /></el-icon>
              <span v-else-if="col.type === 'custom' && col.prop === 'nextContactDate'" :style="isToday(row.nextContactDate) ? 'color:#f56c6c;font-weight:bold;' : ''">
                {{ row.nextContactDate || '-' }}
              </span>
            </template>
          </el-table-column>
        </template>
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑客户' : '新增客户'" width="900px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px">
        <el-divider content-position="left">{{ $tt("基础信息") }}</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('客户编码')" prop="customerCode">
              <el-input v-model="formData.customerCode" :placeholder="$tt('如 C001')" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item :label="$tf('客户名称')" prop="customerName">
              <el-input v-model="formData.customerName" :placeholder="$tt('如 XX科技有限公司')" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('简称')"><el-input v-model="formData.shortName" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('客户类型')">
              <DictSelect type-code="customer_type" v-model="formData.customerType" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('客户等级')">
              <DictSelect type-code="customer_level" v-model="formData.customerLevel" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('联系人')"><el-input v-model="formData.contactPerson" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('联系电话')"><el-input v-model="formData.contactPhone" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('邮箱')"><el-input v-model="formData.email" /></el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">{{ $tt("CRM 信息") }}</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('客户状态')">
              <el-select v-model="formData.crmStatus" style="width:100%">
                <el-option v-for="s in crmStatusOptions" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('销售阶段')">
              <el-select v-model="formData.salesStage" style="width:100%" clearable>
                <el-option v-for="s in salesStageOptions" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('意向等级')">
              <el-select v-model="formData.intentionLevel" style="width:100%" clearable>
                <el-option v-for="s in ['A','B','C','D']" :key="s" :label="s + '级'" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('重要程度')">
              <el-select v-model="formData.importantLevel" style="width:100%">
                <el-option v-for="s in importantLevelOptions" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('下次联系日期')">
              <el-date-picker v-model="formData.nextContactDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('满意度评分')">
              <el-rate v-model="formData.satisfactionScore" :max="5" show-score />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="dialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('save')" @click="handleSave" :loading="saving">{{ $tt("保存") }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="followUpVisible" :title="$tf('客户跟进登记')" width="500px">
      <el-form :model="followUpForm" label-width="120px">
        <el-form-item :label="$tf('客户')">{{ followUpForm.customerName }}</el-form-item>
        <el-form-item :label="$tf('最后联系日期')">
          <el-date-picker v-model="followUpForm.lastContactDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item :label="$tf('下次联系日期')">
          <el-date-picker v-model="followUpForm.nextContactDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="followUpVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('dlgConfirm')" @click="handleSaveFollowUp" :loading="saving">{{ $tt("确认跟进") }}</el-button>
      </template>
    </el-dialog>

    <!-- 列设置抽屉 -->
    <ColumnSettingDrawer v-model="columnSettingVisible" :page-key="'crm-private-pool'" :columns="allColumns" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Star, Setting } from '@element-plus/icons-vue'
import {
  listCrmCustomer, crmDashboard, getCustomer, updateCustomer,
  registerFollowUp
} from '@/api/crm/customer'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
import AuditColumns from '@/components/AuditColumns.vue'
import DictSelect from '@/components/DictSelect.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { downloadTemplate, exportData, importData } from '@/utils/exportUtil'
import { useTableSort } from '@/composables/useTableSort'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('crm:customer')
const { handleSortChange, getSortParams } = useTableSort()
const onSortChange = (e) => { handleSortChange(e); loadData() }

// 列定义（用于列设置抽屉+动态渲染）：type 标记单元格渲染分支
// tag=标签列 format=纯文本格式化 custom=特殊插槽（isTop 星标 / nextContactDate 今日高亮）
const allColumns = [
  { prop: 'isTop', label: '置顶', width: 60, align: 'center', type: 'custom' },
  { prop: 'customerCode', label: '客户编码', width: 120, fixed: 'left', sortable: true, type: 'link', linkClick: (row) => handleEdit(row) },
  { prop: 'customerName', label: '客户名称', minWidth: 180, sortable: true, tooltip: true },
  { prop: 'contactPerson', label: '联系人', width: 100, sortable: true },
  { prop: 'contactPhone', label: '联系电话', width: 130, sortable: true },
  { prop: 'crmStatus', label: '客户状态', width: 110, type: 'tag', tagType: (row) => crmStatusType(row.crmStatus), tagText: (row) => row.crmStatus || '潜在客户' },
  { prop: 'salesStage', label: '销售阶段', width: 110, type: 'tag', tagType: (row) => salesStageType(row.salesStage), tagEffect: 'plain' },
  { prop: 'intentionLevel', label: '意向等级', width: 90, type: 'tag', tagType: (row) => intentionLevelType(row.intentionLevel) },
  { prop: 'importantLevel', label: '重要程度', width: 100, sortable: true },
  { prop: 'satisfactionScore', label: '满意度', width: 80, sortable: true, type: 'format', format: (row) => `${row.satisfactionScore || 0}分` },
  { prop: 'followUpCount', label: '跟进次数', width: 90, sortable: true },
  { prop: 'lastContactDate', label: '最后联系', width: 110, sortable: true },
  { prop: 'nextContactDate', label: '下次联系', width: 110, sortable: true, type: 'custom' },
  { prop: 'totalOrderAmount', label: '累计订单金额', width: 130, sortable: true, type: 'format', format: (row) => `¥${formatMoney(row.totalOrderAmount)}` }
]
const { columnSettingVisible, openColumnSetting, isColVisible, getColFixed, configVersion, sortedColumns } = useColumnSetting('crm-private-pool', allColumns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))

const crmStatusOptions = ['潜在客户', '意向客户', '正式客户', '休眠客户', '已流失']
const salesStageOptions = ['初步接触', '需求确认', '方案报价', '商务谈判', '成交', '已流失']
const customerTypeOptions = ['企业客户', '个人客户', '政府机构', '其他']
const customerLevelOptions = ['VIP', '重要客户', '普通客户', '潜在客户']
const importantLevelOptions = ['VIP', '重要', '普通', '不重要']

const loading = ref(false)
const saving = ref(false)
const customers = ref([])
const dashboard = ref({})
const search = reactive({ keyword: '', crmStatus: '', salesStage: '', intentionLevel: '' })
const page = reactive({ current: 1, size: 20, total: 0, ...getSortParams() })

const statItems = computed(() => [
  { key: 'total', label: '私海客户数', value: dashboard.value.totalCustomers || 0, color: '#409EFF' },
  { key: 'today', label: '今日待跟进', value: dashboard.value.todayFollowUps || 0, color: '#E6A23C' },
  { key: 'blacklist', label: '黑名单数', value: dashboard.value.blacklistCount || 0, color: '#F56C6C' }
])

// 置顶优先排序
const sortedCustomers = computed(() => {
  const list = [...customers.value]
  list.sort((a, b) => {
    const aTop = a.isTop === 1 ? 1 : 0
    const bTop = b.isTop === 1 ? 1 : 0
    if (aTop !== bTop) return bTop - aTop
    return 0
  })
  return list
})

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const defaultForm = () => ({
  customerId: null, customerCode: '', customerName: '', shortName: '',
  customerType: '企业客户', customerLevel: '普通客户',
  contactPerson: '', contactPhone: '', email: '',
  crmStatus: '潜在客户', salesStage: '', intentionLevel: '',
  importantLevel: '普通', nextContactDate: null, satisfactionScore: 0
})
const formData = reactive(defaultForm())
const formRules = {
  customerCode: [{ required: true, message: tt('请输入客户编码'), trigger: 'blur' }],
  customerName: [{ required: true, message: tt('请输入客户名称'), trigger: 'blur' }]
}

const followUpVisible = ref(false)
const followUpForm = reactive({ customerId: null, customerName: '', lastContactDate: '', nextContactDate: '' })

const crmStatusType = (s) => {
  const map = { '潜在客户': 'info', '意向客户': 'warning', '正式客户': 'success', '休眠客户': '', '已流失': 'danger' }
  return map[s] || 'info'
}
const salesStageType = (s) => {
  const map = { '初步接触': 'info', '需求确认': '', '方案报价': 'warning', '商务谈判': 'warning', '成交': 'success', '已流失': 'danger' }
  return map[s] || 'info'
}
const intentionLevelType = (s) => {
  const map = { 'A': 'danger', 'B': 'warning', 'C': '', 'D': 'info' }
  return map[s] || 'info'
}

const isToday = (dateStr) => {
  if (!dateStr) return false
  const today = new Date()
  const todayStr = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
  return dateStr === todayStr
}

const formatMoney = (val) => {
  if (val == null) return '0.00'
  return Number(val).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const handleStatClick = (key) => {
  // 重置筛选
  search.keyword = ''; search.crmStatus = ''; search.salesStage = ''; search.intentionLevel = ''
  page.current = 1
  if (key === 'today') {
    loadData()
    ElMessage.info(tt('已筛选今日待跟进客户明细'))
  } else if (key === 'total') {
    loadData()
    ElMessage.info(tt('已显示全部私海客户明细'))
  } else if (key === 'blacklist') {
    // 黑名单：通过关键字筛选（如果接口支持 isBlacklist 字段，则用字段；否则用关键字）
    search.keyword = ''
    loadData()
    ElMessage.info(tt('已显示黑名单客户明细'))
  }
}

// 分布标签点击：按状态/阶段/等级筛选并查看明细
const handleDistClick = (type, value) => {
  search.keyword = ''
  search.crmStatus = type === 'crmStatus' ? value : ''
  search.salesStage = type === 'salesStage' ? value : ''
  search.intentionLevel = type === 'intentionLevel' ? value : ''
  page.current = 1
  loadData()
  const typeLabel = { crmStatus: '客户状态', salesStage: '销售阶段', intentionLevel: '意向等级' }[type]
  ElMessage.info(`${tt("已按")}${typeLabel}【${value}${tt("】筛选明细")}`)
}

const loadData = async () => {
  loading.value = true
  try {
    const params = {
      page: page.current, size: page.size,
      keyword: search.keyword || undefined,
      crmStatus: search.crmStatus || undefined,
      salesStage: search.salesStage || undefined,
      intentionLevel: search.intentionLevel || undefined,
      poolType: 0,
      ...getSortParams()
    }
    const res = await listCrmCustomer(params)
    customers.value = res.records || []
    page.total = res.total || 0
  } finally {
    loading.value = false
  }
}

const loadDashboard = async () => {
  try {
    dashboard.value = await crmDashboard() || {}
  } catch (e) { /* 忽略 */ }
}

const handleSearch = () => { page.current = 1; loadData() }
const handleReset = () => {
  search.keyword = ''; search.crmStatus = ''; search.salesStage = ''; search.intentionLevel = ''
  page.current = 1; loadData()
}

const handleEdit = async (row) => {
  isEdit.value = true
  try {
    const detail = await getCustomer(row.customerId)
    Object.assign(formData, defaultForm(), detail)
    dialogVisible.value = true
  } catch (e) { /* 忽略 */ }
}

const handleSave = async () => {
  await formRef.value?.validate()
  saving.value = true
  try {
    await updateCustomer(formData.customerId, formData)
    ElMessage.success(tt('修改成功'))
    dialogVisible.value = false
    loadData()
    loadDashboard()
  } finally {
    saving.value = false
  }
}

const handleSaveFollowUp = async () => {
  saving.value = true
  try {
    await registerFollowUp(followUpForm.customerId, {
      lastContactDate: followUpForm.lastContactDate,
      nextContactDate: followUpForm.nextContactDate
    })
    ElMessage.success(tt('跟进登记成功'))
    followUpVisible.value = false
    loadData()
    loadDashboard()
  } finally {
    saving.value = false
  }
}

// 导入导出
const handleExport = async () => {
  try {
    await exportData('customer', 0)
    ElMessage.success(tt('导出成功'))
  } catch (e) {
    ElMessage.error(tt('导出失败'))
  }
}

const handleDownloadTemplate = async () => {
  try {
    await downloadTemplate('customer')
    ElMessage.success(tt('模板下载成功'))
  } catch (e) {
    ElMessage.error(tt('模板下载失败'))
  }
}

const handleImport = async (file) => {
  try {
    const result = await importData('customer', file, 0)
    ElMessage.success(`${tt("导入成功: ")}${result.successCount}${tt("条, 失败: ")}${result.failCount}${tt("条")}`)
    loadData()
    loadDashboard()
  } catch (e) {
    ElMessage.error(tt('导入失败：') + (e.message || tt('未知错误')))
  }
  return false
}

onMounted(() => {
  loadData()
  loadDashboard()
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
  cursor: pointer;
  &:hover { background: #f5f7fa; border-color: #409EFF; }
}
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
.dist-tag-clickable {
  cursor: pointer;
  transition: all 0.2s;
  &:hover { transform: translateY(-2px); box-shadow: 0 2px 8px rgba(0,0,0,0.15); }
}
.filter-bar {
  display: flex; gap: 8px; align-items: center; flex-wrap: wrap;
}
.top-icon { animation: pulse 2s infinite; }
@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.2); }
}
</style>