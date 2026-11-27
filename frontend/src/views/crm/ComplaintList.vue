<template>
  <div class="erp-page">
    <!-- 投诉管理仪表盘 -->
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
                    :type="statusTagType(k)" size="large" class="dist-tag">
              {{ k }}: {{ v }}
            </el-tag>
          </div>
        </div>
        <div class="dist-block">
          <div class="dist-title">{{ $tt("投诉类型分布") }}</div>
          <div class="dist-tags">
            <el-tag v-for="(v, k) in dashboard.typeDist || {}" :key="k"
                    :type="complaintTypeTagType(k)" size="large" class="dist-tag">
              {{ k }}: {{ v }}
            </el-tag>
          </div>
        </div>
        <div class="dist-block">
          <div class="dist-title">{{ $tt("严重程度分布") }}</div>
          <div class="dist-tags">
            <el-tag v-for="(v, k) in dashboard.severityDist || {}" :key="k"
                    :type="severityTagType(k)" size="large" class="dist-tag">
              {{ k }}: {{ v }}
            </el-tag>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 筛选区 -->
    <el-card shadow="never" style="margin-top:12px;">
      <div class="filter-bar">
        <el-input v-model="search.keyword" :placeholder="$tt('投诉单号/客户名称/联系人')" style="width:240px;" clearable />
        <el-select v-model="search.handleStatus" :placeholder="$tt('处理状态')" style="width:130px;" clearable>
          <el-option v-for="s in statusOptions" :key="s" :label="s" :value="s" />
        </el-select>
        <el-select v-model="search.complaintType" :placeholder="$tt('投诉类型')" style="width:130px;" clearable>
          <el-option v-for="s in complaintTypeOptions" :key="s" :label="s" :value="s" />
        </el-select>
        <el-select v-model="search.severityLevel" :placeholder="$tt('严重程度')" style="width:120px;" clearable>
          <el-option v-for="s in severityOptions" :key="s" :label="s" :value="s" />
        </el-select>
        <el-button type="primary" v-permission="perm('query')" @click="handleSearch">{{ $tt("查询") }}</el-button>
        <el-button v-permission="perm('reset')" @click="handleReset">{{ $tt("重置") }}</el-button>
        <el-tooltip :content="$tt('列设置')" placement="top">
          <el-button :icon="Setting" circle v-permission="perm('columnSetting')" @click="openColumnSetting" />
        </el-tooltip>
        <div style="flex:1;"></div>
        <el-button type="primary" v-permission="perm('add')" @click="handleAdd">{{ $tt("+ 新增投诉") }}</el-button>
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

    <!-- 投诉列表 -->
    <el-card shadow="never" style="margin-top:12px;">
      <el-table :data="records" v-loading="loading" border stripe :key="configVersion" @sort-change="onSortChange">
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
              <el-tag v-else-if="col.type === 'tag'" :type="col.tagType ? col.tagType(row) : 'info'" size="small">{{ col.tagText ? col.tagText(row) : (row[col.prop] || '-') }}</el-tag>
              <span v-else-if="col.type === 'format'">{{ col.format(row) }}</span>
            </template>
          </el-table-column>
        </template>
        <AuditColumns />
        <!-- 操作列：编辑 + 更多（指派/处理/关闭/删除） -->
        <el-table-column :label="$tf('操作')" width="140" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" v-permission="perm('edit')" @click="handleEdit(row)">{{ $tt("编辑") }}</el-button>
            <el-dropdown trigger="click" @command="(cmd) => handleRowCommand(cmd, row)">
              <el-button type="primary" link size="small">{{ $tt("更多") }}<el-icon style="margin-left:2px;"><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="assign">{{ $tt("指派处理人") }}</el-dropdown-item>
                  <el-dropdown-item command="resolve" :disabled="row.handleStatus === '已解决' || row.handleStatus === '已关闭'">{{ $tt("处理") }}</el-dropdown-item>
                  <el-dropdown-item command="close" :disabled="row.handleStatus === '已关闭'">{{ $tt("关闭") }}</el-dropdown-item>
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
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑投诉' : '新增投诉'" width="780px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px">
        <el-divider content-position="left">{{ $tt("客户信息") }}</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('投诉单号')">
              <el-input v-model="formData.complaintNo" :disabled="docCodeDisabled && !isEdit" :placeholder="docCodePlaceholder" />
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
            <el-form-item :label="$tf('客户名称')" prop="customerName">
              <el-input v-model="formData.customerName" :placeholder="$tt('如 XX科技有限公司')" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('联系人')">
              <el-input v-model="formData.contactPerson" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('联系电话')">
              <el-input v-model="formData.contactPhone" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item :label="$tf('投诉日期')" prop="complaintDate">
              <el-date-picker v-model="formData.complaintDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">{{ $tt("投诉内容") }}</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('投诉类型')" prop="complaintType">
              <el-select v-model="formData.complaintType" style="width:100%">
                <el-option v-for="s in complaintTypeOptions" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('投诉渠道')" prop="complaintChannel">
              <el-select v-model="formData.complaintChannel" style="width:100%">
                <el-option v-for="s in complaintChannelOptions" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('严重程度')" prop="severityLevel">
              <el-select v-model="formData.severityLevel" style="width:100%">
                <el-option v-for="s in severityOptions" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item :label="$tf('投诉内容')" prop="complaintContent">
              <el-input v-model="formData.complaintContent" type="textarea" :rows="4" :placeholder="$tt('请详细描述投诉内容')" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('指派处理人ID')">
              <el-select v-model="formData.assignedHandlerId" :placeholder="$tt('请选择员工')" filterable clearable style="width:100%">
                <el-option v-for="opt in employeeOptions" :key="opt.value" :label="$tf(opt.label)" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item :label="$tf('备注')">
              <el-input v-model="formData.remark" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="dialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('save')" @click="handleSave" :loading="saving" v-if="!formData.handleStatus || !['已解决','已关闭'].includes(formData.handleStatus)">{{ $tt("保存") }}</el-button>
        <el-alert v-if="['已解决','已关闭'].includes(formData.handleStatus)" :title="$tt('当前状态不允许编辑')" type="warning" :closable="false" show-icon style="margin-right:auto;" />
      </template>
    </el-dialog>

    <!-- 指派弹窗 -->
    <el-dialog v-model="assignVisible" :title="$tf('指派处理人')" width="420px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item :label="$tf('投诉单号')">
          <span>{{ currentRow.complaintNo }}</span>
        </el-form-item>
        <el-form-item :label="$tf('处理人ID')">
          <el-select v-model="assignForm.handlerId" :placeholder="$tt('请选择员工')" filterable clearable style="width:100%">
            <el-option v-for="opt in employeeOptions" :key="opt.value" :label="$tf(opt.label)" :value="opt.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="assignVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('assign')" @click="confirmAssign" :loading="saving">{{ $tt("确认指派") }}</el-button>
      </template>
    </el-dialog>

    <!-- 处理弹窗 -->
    <el-dialog v-model="resolveVisible" :title="$tf('处理投诉')" width="500px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item :label="$tf('投诉单号')">
          <span>{{ currentRow.complaintNo }}</span>
        </el-form-item>
        <el-form-item :label="$tf('处理结果')">
          <el-input v-model="resolveForm.handleResult" type="textarea" :rows="4" :placeholder="$tt('请输入处理结果说明')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="resolveVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('dlgConfirm')" @click="confirmResolve" :loading="saving">{{ $tt("确认处理") }}</el-button>
      </template>
    </el-dialog>

    <!-- 关闭弹窗 -->
    <el-dialog v-model="closeVisible" :title="$tf('关闭投诉')" width="420px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item :label="$tf('投诉单号')">
          <span>{{ currentRow.complaintNo }}</span>
        </el-form-item>
        <el-form-item :label="$tf('满意度评分')">
          <el-rate v-model="closeForm.satisfactionScore" :max="5" show-score />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="closeVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('dlgConfirm')" @click="confirmClose" :loading="saving">{{ $tt("确认关闭") }}</el-button>
      </template>
    </el-dialog>

    <!-- 列设置抽屉 -->
    <ColumnSettingDrawer v-model="columnSettingVisible" :page-key="'crm-complaint'" :columns="allColumns" />
  </div>
</template>

<script setup>
import { withFallbackOption } from '@/utils/labelFallback'
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listComplaint, getComplaint, saveComplaint, updateComplaint, deleteComplaint,
  assignHandler, resolveComplaint, closeComplaint,
  complaintDashboard
} from '@/api/crm/complaint'
import { listAllCustomer } from '@/api/basedata/customer'
import { listAllEmployee } from '@/api/basedata/employee'
import { exportListAsSeen, importData } from '@/utils/exportUtil'
import { Setting, ArrowDown } from '@element-plus/icons-vue'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
import AuditColumns from '@/components/AuditColumns.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { useAutoDocCode } from '@/composables/useAutoDocCode'
import { useTableSort } from '@/composables/useTableSort'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('crm:complaint')
const { handleSortChange, getSortParams } = useTableSort()


const onSortChange = (e) => { handleSortChange(e); loadData() }// 列设置
// 列定义（用于列设置抽屉+动态渲染）：type 标记单元格渲染分支（link 单号链接 / tag 标签 / format 格式化文本）
const allColumns = [
  { prop: 'complaintNo', label: '投诉单号', width: 140, fixed: 'left', sortable: true, type: 'link', linkClick: (row) => handleEdit(row) },
  { prop: 'customerName', label: '客户名称', minWidth: 160, sortable: true, tooltip: true },
  { prop: 'contactPerson', label: '联系人', width: 100, sortable: true },
  { prop: 'complaintDate', label: '投诉日期', width: 120, sortable: true },
  { prop: 'complaintType', label: '投诉类型', width: 110, type: 'tag', tagType: (row) => complaintTypeTagType(row.complaintType) },
  { prop: 'complaintChannel', label: '投诉渠道', width: 110, sortable: true },
  { prop: 'severityLevel', label: '严重程度', width: 100, type: 'tag', tagType: (row) => severityTagType(row.severityLevel) },
  { prop: 'handleStatus', label: '处理状态', width: 110, type: 'tag', tagType: (row) => statusTagType(row.handleStatus), tagText: (row) => row.handleStatus || '新建' },
  { prop: 'satisfactionScore', label: '满意度', width: 90, sortable: true, type: 'format', format: (row) => row.satisfactionScore != null ? row.satisfactionScore + '分' : '-' },
  { prop: 'handleDate', label: '处理日期', width: 120, sortable: true }
]
const { columnSettingVisible, openColumnSetting, isColVisible, getColFixed, configVersion, sortedColumns } = useColumnSetting('crm-complaint', allColumns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))
const { docCodeDisabled, docCodePlaceholder, autoFillDocCodeOnCreate } = useAutoDocCode('COMPLAINT')

// 选项常量 (严格遵循文档 7.5 定义)
const statusOptions = ['新建', '处理中', '已解决', '已关闭']
const complaintTypeOptions = ['产品质量', '发货延迟', '服务态度', '售后问题', '价格问题', '其他']
const complaintChannelOptions = ['电话', '邮件', '在线客服', '现场', '其他']
const severityOptions = ['高', '中', '低']

// 数据状态
const loading = ref(false)
const saving = ref(false)
const records = ref([])
const dashboard = ref({})
const search = reactive({ keyword: '', handleStatus: '', complaintType: '', severityLevel: '' })
const page = reactive({ current: 1, size: 20, total: 0, ...getSortParams() })

// 仪表盘统计项
const statItems = computed(() => [
  { key: 'total', label: '总投诉数', value: dashboard.value.totalComplaints || 0, color: '#409EFF' },
  { key: 'highPending', label: '高严重待处理', value: dashboard.value.highSeverityPending || 0, color: '#F56C6C' },
  { key: 'avgSatisfaction', label: '平均满意度', value: dashboard.value.avgSatisfaction || 0, color: '#67C23A' }
])

// 弹窗状态
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const defaultForm = () => ({
  complaintId: null, complaintNo: '',
  customerId: null, customerName: '', contactPerson: '', contactPhone: '',
  complaintDate: '', complaintType: '产品质量', complaintChannel: '电话',
  complaintContent: '', severityLevel: '中', assignedHandlerId: null,
  remark: ''
})
const formData = reactive(defaultForm())
const formRules = {
  customerId: [{ required: true, message: tt('请选择客户'), trigger: 'change' }],
  customerName: [{ required: true, message: tt('请输入客户名称'), trigger: 'blur' }],
  complaintDate: [{ required: true, message: tt('请选择投诉日期'), trigger: 'change' }],
  complaintType: [{ required: true, message: tt('请选择投诉类型'), trigger: 'change' }],
  complaintChannel: [{ required: true, message: tt('请选择投诉渠道'), trigger: 'change' }],
  severityLevel: [{ required: true, message: tt('请选择严重程度'), trigger: 'change' }],
  complaintContent: [{ required: true, message: tt('请输入投诉内容'), trigger: 'blur' }]
}

// 指派/处理/关闭 弹窗
const assignVisible = ref(false)
const resolveVisible = ref(false)
const closeVisible = ref(false)
const currentRow = ref({})
const assignForm = reactive({ handlerId: null })
const resolveForm = reactive({ handleResult: '' })
const closeForm = reactive({ satisfactionScore: 5 })

// 颜色映射
const statusTagType = (s) => {
  const map = { '新建': 'info', '处理中': 'warning', '已解决': 'success', '已关闭': 'danger' }
  return map[s] || 'info'
}
const complaintTypeTagType = (s) => {
  const map = { '产品质量': 'danger', '发货延迟': 'warning', '服务态度': 'warning', '售后问题': '', '价格问题': 'info', '其他': 'info' }
  return map[s] || 'info'
}
const severityTagType = (s) => {
  const map = { '高': 'danger', '中': 'warning', '低': 'info' }
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
  handleStatus: search.handleStatus || undefined,
  complaintType: search.complaintType || undefined,
  severityLevel: search.severityLevel || undefined,
  ...getSortParams()
})

const loadData = async () => {
  loading.value = true
  try {
    const res = await listComplaint({ page: page.current, size: page.size, ...buildQueryParams() })
    records.value = res.records || []
    page.total = res.total || 0
  } finally {
    loading.value = false
  }
}

const loadDashboard = async () => {
  try {
    dashboard.value = await complaintDashboard() || {}
  } catch (e) { /* 忽略仪表盘加载错误 */ }
}

const handleSearch = () => { page.current = 1; loadData() }
const handleReset = () => {
  search.keyword = ''; search.handleStatus = ''; search.complaintType = ''
  search.severityLevel = ''
  page.current = 1; loadData()
}

const handleStatClick = (key) => {
  if (key === 'highPending') {
    search.handleStatus = '待处理'
    search.severityLevel = '高'
    search.complaintType = ''
    search.keyword = ''
    page.current = 1
    loadData()
    ElMessage.info(tt('已筛选高严重待处理投诉'))
  } else {
    search.keyword = ''; search.handleStatus = ''; search.complaintType = ''
    search.severityLevel = ''
    page.current = 1
    loadData()
    ElMessage.info(tt('已显示全部投诉'))
  }
}

// 导入导出
const handleExport = async () => {
  try {
    // 页面上看到什么列，导出就带什么列；导出行 = 当前筛选条件下的全部
    await exportListAsSeen({
      entityName: 'complaint',
      columns: visibleSortedColumns.value,
      fetchPage: listComplaint,
      params: buildQueryParams(),
      idField: 'complaintId'
    })
    ElMessage.success(tt('导出成功'))
  } catch (e) {
    // 失败原因由 exportUtil 统一提示
  }
}

const handleImport = async (file) => {
  try {
    const result = await importData('complaint', file)
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
  autoFillDocCodeOnCreate(formData, 'complaintNo')
  dialogVisible.value = true
}

const handleEdit = async (row) => {
  isEdit.value = true
  try {
    const detail = await getComplaint(row.complaintId)
    Object.assign(formData, defaultForm(), detail)
    dialogVisible.value = true
  } catch (e) { /* 忽略 */ }
}

const handleSave = async () => {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (isEdit.value) {
      await updateComplaint(formData.complaintId, formData)
    } else {
      await saveComplaint(formData)
    }
    ElMessage.success(isEdit.value ? tt('修改成功') : tt('新增成功'))
    dialogVisible.value = false
    loadData()
    loadDashboard()
  } finally {
    saving.value = false
  }
}

const confirmAssign = async () => {
  if (!assignForm.handlerId) {
    ElMessage.warning(tt('请输入处理人ID'))
    return
  }
  saving.value = true
  try {
    await assignHandler(currentRow.value.complaintId, assignForm.handlerId)
    ElMessage.success(tt('指派成功'))
    assignVisible.value = false
    loadData()
    loadDashboard()
  } finally {
    saving.value = false
  }
}

const confirmResolve = async () => {
  if (!resolveForm.handleResult) {
    ElMessage.warning(tt('请输入处理结果'))
    return
  }
  saving.value = true
  try {
    await resolveComplaint(currentRow.value.complaintId, resolveForm.handleResult)
    ElMessage.success(tt('处理成功'))
    resolveVisible.value = false
    loadData()
    loadDashboard()
  } finally {
    saving.value = false
  }
}

const confirmClose = async () => {
  saving.value = true
  try {
    await closeComplaint(currentRow.value.complaintId, closeForm.satisfactionScore)
    ElMessage.success(tt('关闭成功'))
    closeVisible.value = false
    loadData()
    loadDashboard()
  } finally {
    saving.value = false
  }
}

// 行内"更多"下拉分发：指派/处理/关闭/删除
const handleRowCommand = async (cmd, row) => {
  currentRow.value = row
  if (cmd === 'assign') {
    assignForm.handlerId = row.assignedHandlerId || null
    assignVisible.value = true
  } else if (cmd === 'resolve') {
    resolveForm.handleResult = ''
    resolveVisible.value = true
  } else if (cmd === 'close') {
    closeForm.satisfactionScore = row.satisfactionScore || 5
    closeVisible.value = true
  } else if (cmd === 'delete') {
    handleDelete(row)
  }
}

// 删除投诉
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `${tt("确认删除投诉【")}${row.complaintNo}${tt("】？删除后不可恢复。")}`,
      tt('删除确认'),
      { type: 'warning', confirmButtonText: tt('确认删除'), cancelButtonText: tt('取消') }
    )
  } catch (e) {
    return
  }
  try {
    await deleteComplaint(row.complaintId)
    ElMessage.success(tt('删除成功'))
    loadData()
    loadDashboard()
  } catch (e) {
    // 拦截器已弹错误提示
  }
}

// 外键下拉选项
const customerOptions = ref([])
const employeeOptions = ref([])
async function loadDropdownOptions() {
  try {
    const custList = await listAllCustomer()
    customerOptions.value = (Array.isArray(custList) ? custList : (custList?.records || []))
      .map(c => ({ value: c.customerId, label: c.customerName }))
  } catch (e) { console.warn('加载客户失败', e) }
  try {
    const empList = await listAllEmployee()
    employeeOptions.value = (Array.isArray(empList) ? empList : (empList?.records || []))
      .map(e => ({ value: e.employeeId, label: e.employeeName + (e.deptName ? ' (' + e.deptName + ')' : '') }))
  } catch (e) { console.warn('加载员工失败', e) }
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
