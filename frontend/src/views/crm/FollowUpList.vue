<template>
  <div class="erp-page">
    <!-- 跟进记录仪表盘 -->
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
          <div class="dist-title">{{ $tt("跟进方式分布") }}</div>
          <div class="dist-tags">
            <el-tag v-for="(v, k) in dashboard.byType || {}" :key="k"
                    :type="followUpTypeTagType(k)" size="large" class="dist-tag">
              {{ k }}: {{ v }}
            </el-tag>
          </div>
        </div>
        <div class="dist-block">
          <div class="dist-title">{{ $tt("来源类型分布") }}</div>
          <div class="dist-tags">
            <el-tag v-for="(v, k) in dashboard.bySourceType || {}" :key="k"
                    :type="sourceTypeTagType(k)" size="large" class="dist-tag">
              {{ k }}: {{ v }}
            </el-tag>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 筛选区 -->
    <el-card shadow="never" style="margin-top:12px;">
      <div class="filter-bar">
        <el-input v-model="search.keyword" :placeholder="$tt('跟进单号/来源名称/联系人/摘要')" style="width:240px;" clearable />
        <el-select v-model="search.sourceType" :placeholder="$tt('来源类型')" style="width:130px;" clearable>
          <el-option v-for="s in sourceTypeOptions" :key="s" :label="s" :value="s" />
        </el-select>
        <el-select v-model="search.followUpType" :placeholder="$tt('跟进方式')" style="width:130px;" clearable>
          <el-option v-for="s in followUpTypeOptions" :key="s" :label="s" :value="s" />
        </el-select>
        <el-select v-model="search.importantFlag" :placeholder="$tt('重要程度')" style="width:120px;" clearable>
          <el-option :label="$tf('全部')" :value="null" />
          <el-option :label="$tf('普通')" :value="0" />
          <el-option :label="$tf('重要')" :value="1" />
        </el-select>
        <el-button type="primary" v-permission="perm('query')" @click="handleSearch">{{ $tt("查询") }}</el-button>
        <el-button v-permission="perm('reset')" @click="handleReset">{{ $tt("重置") }}</el-button>
        <el-tooltip :content="$tt('列设置')" placement="top">
          <el-button :icon="Setting" circle v-permission="perm('columnSetting')" @click="openColumnSetting" />
        </el-tooltip>
        <div style="flex:1;"></div>
        <el-button type="primary" v-permission="perm('add')" @click="handleAdd">{{ $tt("+ 新增跟进") }}</el-button>
      </div>
    </el-card>

    <!-- 跟进记录列表 -->
    <el-card shadow="never" style="margin-top:12px;">
      <el-table :data="records" v-loading="loading" border stripe @sort-change="onSortChange" :key="configVersion">
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
              <el-badge v-else-if="col.type === 'custom' && col.prop === 'attachment' && row.attachment" :value="parseAttachmentUrls(row.attachment).length" type="primary">
                <el-icon :size="18" color="#409EFF"><Paperclip /></el-icon>
              </el-badge>
              <span v-else-if="col.type === 'custom' && col.prop === 'attachment'" style="color:#c0c4cc;">-</span>
              <span v-else-if="col.type === 'custom' && col.prop === 'nextFollowUpDate'" :style="isToday(row.nextFollowUpDate) ? 'color:#f56c6c;font-weight:bold;' : ''">
                {{ row.nextFollowUpDate || '-' }}
              </span>
            </template>
          </el-table-column>
        </template>
        <AuditColumns />
        <!-- 操作列：编辑/删除 -->
        <el-table-column :label="$tf('操作')" width="110" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" v-permission="perm('edit')" @click="handleEdit(row)">{{ $tt("编辑") }}</el-button>
            <el-button type="danger" link size="small" v-permission="perm('delete')" @click="handleDelete(row)">{{ $tt("删除") }}</el-button>
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
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑跟进记录' : '新增跟进记录'" width="780px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px">
        <el-divider content-position="left">{{ $tt("来源信息") }}</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('跟进单号')">
              <el-input v-model="formData.followUpNo" :disabled="docCodeDisabled && !isEdit" :placeholder="docCodePlaceholder" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('来源类型')" prop="sourceType">
              <el-select v-model="formData.sourceType" style="width:100%">
                <el-option v-for="s in sourceTypeOptions" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('来源')" prop="sourceId">
              <el-select v-model="formData.sourceId" style="width:100%" filterable
                         :placeholder="$tt('请选择来源')"
                         @change="onSourceChange">
                <el-option v-for="s in sourceOptions" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('来源名称')" prop="sourceName">
              <el-input v-model="formData.sourceName" :placeholder="$tt('自动带出')" disabled />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">{{ $tt("跟进信息") }}</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('跟进日期')" prop="followUpDate">
              <el-date-picker v-model="formData.followUpDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('跟进方式')" prop="followUpType">
              <el-select v-model="formData.followUpType" style="width:100%">
                <el-option v-for="s in followUpTypeOptions" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('联系人')">
              <el-input v-model="formData.contactPerson" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item :label="$tf('跟进内容')" prop="content">
              <el-input v-model="formData.content" type="textarea" :rows="3" :placeholder="$tt('请输入详细的跟进内容（不少于10个字符）')" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item :label="$tf('跟进摘要')">
              <el-input v-model="formData.summary" :placeholder="$tt('一句话总结本次跟进')" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item :label="$tf('跟进结果')">
              <el-input v-model="formData.result" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item :label="$tf('下一步计划')">
              <el-input v-model="formData.nextPlan" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('下次跟进日期')">
              <el-date-picker v-model="formData.nextFollowUpDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('重要标记')">
              <el-switch v-model="formData.importantFlag" :active-value="1" :inactive-value="0" :active-text="$tt('重要')" :inactive-text="$tt('普通')" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('跟进人')">
              <el-select v-model="formData.followUpStaffId" style="width:100%" filterable :placeholder="$tt('请选择跟进人')">
                <el-option v-for="e in employeeOptions" :key="e.employeeId" :value="e.employeeId"
                           :label="e.employeeName + (e.deptName ? ' (' + e.deptName + ')' : '')" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <!-- 销售过程动作量化（2026-10-07）：回访与深入交流两条日任务的量化录入 -->
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('通话时长(秒)')">
              <el-input-number v-model="formData.callDurationSeconds" :min="0" :max="86400"
                               controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('微信交流条数')">
              <el-input-number v-model="formData.chatMessageCount" :min="0" :max="9999"
                               controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('是否深入交流')">
              <el-switch v-model="formData.isDeepCommunication" :active-value="1" :inactive-value="0"
                         :active-text="$tt('是')" :inactive-text="$tt('否')" />
              <div style="font-size:12px;color:#909399;line-height:1.4;margin-top:2px;">
                {{ $tt("不手动切换时，系统按「通话≥5分钟 或 微信≥10句」自动判定") }}
              </div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item :label="$tf('备注')">
              <el-input v-model="formData.remark" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item :label="$tf('附件')">
              <el-upload
                :action="uploadAction"
                :headers="uploadHeaders"
                :multiple="true"
                :auto-upload="false"
                :file-list="fileList"
                :on-change="handleFileChange"
                :on-remove="handleFileRemove"
                list-type="text"
              >
                <el-button size="small" type="primary" v-permission="perm('upload')">{{ $tt("点击上传") }}</el-button>
                <template #tip>
                  <div class="el-upload__tip">{{ $tt("支持图片、文档等格式，可多选") }}</div>
                </template>
              </el-upload>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="dialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('save')" @click="handleSave" :loading="saving">{{ $tt("保存") }}</el-button>
      </template>
    </el-dialog>

    <!-- 列设置抽屉 -->
    <ColumnSettingDrawer v-model="columnSettingVisible" :page-key="'crm-followup'" :columns="allColumns" />
  </div>
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Paperclip, Setting } from '@element-plus/icons-vue'
import {
  listFollowUp, getFollowUp, saveFollowUp, updateFollowUp, deleteFollowUp,
  followUpDashboard
} from '@/api/crm/followUp'
import { listAllEmployee } from '@/api/basedata/employee'
import { listAllCustomer } from '@/api/basedata/customer'
import { listLead } from '@/api/crm/lead'
import { listOpportunity } from '@/api/crm/opportunity'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
import AuditColumns from '@/components/AuditColumns.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { useAutoDocCode } from '@/composables/useAutoDocCode'
import { useTableSort } from '@/composables/useTableSort'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('crm:follow-up')
const { handleSortChange, getSortParams } = useTableSort()


const onSortChange = (e) => { handleSortChange(e); loadData() }// === 列设置 ===
// 列定义（用于列设置抽屉+动态渲染）：type 标记单元格渲染分支
// link=单号链接列 tag=标签列 format=纯文本格式化 custom=特殊插槽（attachment 附件徽标 / nextFollowUpDate 今日高亮）
const allColumns = [
  { prop: 'followUpNo', label: '跟进单号', width: 140, fixed: 'left', sortable: true, type: 'link', linkClick: (row) => handleEdit(row) },
  { prop: 'sourceType', label: '来源类型', width: 100, type: 'tag', tagType: (row) => sourceTypeTagType(row.sourceType) },
  { prop: 'sourceName', label: '来源名称', minWidth: 160, sortable: true, tooltip: true },
  { prop: 'followUpDate', label: '跟进日期', width: 120, sortable: true },
  { prop: 'followUpType', label: '跟进方式', width: 110, sortable: true, type: 'tag', tagType: (row) => followUpTypeTagType(row.followUpType), tagEffect: 'plain' },
  { prop: 'contactPerson', label: '联系人', width: 100, sortable: true },
  { prop: 'summary', label: '跟进摘要', minWidth: 200, sortable: true, tooltip: true },
  { prop: 'importantFlag', label: '重要程度', width: 90, type: 'tag', tagType: (row) => (row.importantFlag === 1 ? 'danger' : 'info'), tagText: (row) => (row.importantFlag === 1 ? '重要' : '普通') },
  { prop: 'attachment', label: '附件', width: 80, align: 'center', type: 'custom' },
  { prop: 'nextFollowUpDate', label: '下次跟进日期', width: 130, sortable: true, type: 'custom' },
  { prop: 'followUpStaffId', label: '跟进人', width: 120, type: 'format', format: (row) => getEmployeeName(row.followUpStaffId) }
]
const { columnSettingVisible, openColumnSetting, isColVisible, getColFixed, configVersion, sortedColumns } = useColumnSetting('crm-followup', allColumns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))

// 单据编码自动生成
const { docCodeDisabled, autoFillDocCodeOnCreate } = useAutoDocCode('FOLLOW_UP')

// 选项常量 (严格遵循文档 7.4 定义)
const sourceTypeOptions = ['客户', '线索', '商机']
const followUpTypeOptions = ['电话', '邮件', '上门拜访', '微信', '视频会议', '其他']

// 下拉选项数据
const employeeOptions = ref([])
const sourceOptions = ref([])

// 员工ID到姓名映射
const employeeMap = computed(() => {
  const m = {}
  employeeOptions.value.forEach(e => { m[e.employeeId] = e.employeeName })
  return m
})
const getEmployeeName = (id) => employeeMap.value[id] || (id ? `#${id}` : '-')

// 根据来源类型加载来源下拉选项
const loadSourceOptions = async (sourceType) => {
  sourceOptions.value = []
  if (!sourceType) return
  try {
    if (sourceType === '客户') {
      const list = await listAllCustomer() || []
      sourceOptions.value = list.map(c => ({ id: c.customerId, name: c.customerName }))
    } else if (sourceType === '线索') {
      const res = await listLead({ page: 1, size: 100 }) || {}
      const list = res.records || []
      sourceOptions.value = list.map(l => ({ id: l.leadId, name: l.leadName + (l.companyName ? ' - ' + l.companyName : '') }))
    } else if (sourceType === '商机') {
      const res = await listOpportunity({ page: 1, size: 100 }) || {}
      const list = res.records || []
      sourceOptions.value = list.map(o => ({ id: o.opportunityId, name: o.opportunityName }))
    }
  } catch (e) {
    sourceOptions.value = []
  }
}

// 来源选择变化时同步 sourceName
const onSourceChange = (id) => {
  const opt = sourceOptions.value.find(s => s.id === id)
  formData.sourceName = opt ? opt.name : ''
}

// 数据状态
const loading = ref(false)
const saving = ref(false)
const records = ref([])
const dashboard = ref({})
const search = reactive({ keyword: '', sourceType: '', followUpType: '', importantFlag: null })
const page = reactive({ current: 1, size: 20, total: 0, ...getSortParams() })

// 仪表盘统计项
const statItems = computed(() => [
  { key: 'total', label: '总跟进数', value: dashboard.value.totalFollowUps || 0, color: '#409EFF' },
  { key: 'today', label: '今日待跟进', value: dashboard.value.todayTasks || 0, color: '#E6A23C' },
  { key: 'important', label: '重要跟进数', value: dashboard.value.importantCount || 0, color: '#F56C6C' }
])

// 弹窗状态
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const defaultForm = () => ({
  followUpId: null, followUpNo: '',
  sourceType: '客户', sourceId: null, sourceName: '',
  followUpDate: '', followUpType: '电话', contactPerson: '',
  content: '', summary: '', result: '', nextPlan: '',
  nextFollowUpDate: '', importantFlag: 0,
  followUpStaffId: null, remark: '', attachment: ''
})
const formData = reactive(defaultForm())

// 监听来源类型变化，重新加载来源选项 (必须在 formData 声明之后)
watch(() => formData.sourceType, (newType) => {
  loadSourceOptions(newType)
  // 类型变化时清空已选来源
  formData.sourceId = null
  formData.sourceName = ''
})
const formRules = {
  sourceType: [{ required: true, message: tt('请选择来源类型'), trigger: 'change' }],
  sourceId: [{ required: true, message: tt('请选择来源'), trigger: 'change' }],
  sourceName: [{ required: true, message: tt('来源名称将自动带出'), trigger: 'blur' }],
  followUpDate: [{ required: true, message: tt('请选择跟进日期'), trigger: 'change' }],
  followUpType: [{ required: true, message: tt('请选择跟进方式'), trigger: 'change' }],
  content: [
    { required: true, message: tt('请输入跟进内容'), trigger: 'blur' },
    { min: 10, message: tt('跟进内容不少于10个字符'), trigger: 'blur' }
  ]
}

// 颜色映射
const sourceTypeTagType = (s) => {
  const map = { '客户': 'success', '线索': 'warning', '商机': '' }
  return map[s] || 'info'
}
const followUpTypeTagType = (s) => {
  const map = { '电话': '', '邮件': 'info', '上门拜访': 'success', '微信': 'warning', '视频会议': 'warning', '其他': 'info' }
  return map[s] || 'info'
}

const isToday = (dateStr) => {
  if (!dateStr) return false
  const today = new Date()
  const todayStr = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
  return dateStr === todayStr
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const params = {
      page: page.current, size: page.size,
      keyword: search.keyword || undefined,
      sourceType: search.sourceType || undefined,
      followUpType: search.followUpType || undefined,
      importantFlag: search.importantFlag ?? undefined,
    
      ...getSortParams()}
    const res = await listFollowUp(params)
    records.value = res.records || []
    page.total = res.total || 0
  } finally {
    loading.value = false
  }
}

const loadDashboard = async () => {
  try {
    dashboard.value = await followUpDashboard() || {}
  } catch (e) { /* 忽略仪表盘加载错误 */ }
}

const handleSearch = () => { page.current = 1; loadData() }
const handleReset = () => {
  search.keyword = ''; search.sourceType = ''; search.followUpType = ''
  search.importantFlag = null
  page.current = 1; loadData()
}

const handleStatClick = (key) => {
  if (key === 'today') {
    search.keyword = ''; search.sourceType = ''; search.followUpType = ''
    search.importantFlag = null
    page.current = 1
    loadData()
    ElMessage.info('已筛选今日待跟进（请查看tt("下次跟进日期")为今天的记录）')
  } else if (key === 'important') {
    search.importantFlag = 1
    search.keyword = ''; search.sourceType = ''; search.followUpType = ''
    page.current = 1
    loadData()
    ElMessage.info(tt('已筛选重要跟进记录'))
  } else {
    search.keyword = ''; search.sourceType = ''; search.followUpType = ''
    search.importantFlag = null
    page.current = 1
    loadData()
    ElMessage.info(tt('已显示全部跟进记录'))
  }
}

// 附件上传
const uploadAction = '/api/files/upload'
const uploadHeaders = computed(() => ({ Authorization: 'Bearer ' + localStorage.getItem('token') }))
const fileList = ref([])
const uploadedUrls = ref([])

const parseAttachmentUrls = (str) => {
  if (!str) return []
  return str.split(',').filter(u => u.trim())
}

const handleFileChange = (file, list) => {
  fileList.value = list.filter(f => f.status !== 'removed')
}

const handleFileRemove = (file, list) => {
  fileList.value = list
  if (file.url) {
    uploadedUrls.value = uploadedUrls.value.filter(u => u !== file.url)
  }
}

// 从已有attachment回显文件列表
const buildFileListFromUrls = (attachmentStr) => {
  const urls = parseAttachmentUrls(attachmentStr)
  return urls.map(url => ({
    name: url.split('/').pop(),
    url: url,
    status: 'success'
  }))
}

// 新增/编辑
const handleAdd = () => {
  isEdit.value = false
  Object.assign(formData, defaultForm())
  autoFillDocCodeOnCreate(formData, 'followUpNo')
  fileList.value = []
  uploadedUrls.value = []
  dialogVisible.value = true
}

const handleEdit = async (row) => {
  isEdit.value = true
  try {
    const detail = await getFollowUp(row.followUpId)
    Object.assign(formData, defaultForm(), detail)
    // 编辑时根据来源类型加载来源选项
    await loadSourceOptions(formData.sourceType)
    // 回显附件
    uploadedUrls.value = parseAttachmentUrls(detail.attachment)
    fileList.value = buildFileListFromUrls(detail.attachment)
    dialogVisible.value = true
  } catch (e) { /* 忽略 */ }
}

const handleSave = async () => {
  await formRef.value?.validate()
  saving.value = true
  try {
    // 上传待传文件
    const pendingFiles = fileList.value.filter(f => f.status === 'ready')
    if (pendingFiles.length > 0) {
      for (const file of pendingFiles) {
        const formD = new FormData()
        formD.append('file', file.raw)
        const res = await fetch(uploadAction, {
          method: 'POST',
          headers: { ...uploadHeaders.value },
          body: formD
        })
        const data = await res.json()
        if (data.code === 200) {
          uploadedUrls.value.push(data.data)
        }
      }
    }
    formData.attachment = uploadedUrls.value.join(',')
    if (isEdit.value) {
      await updateFollowUp(formData.followUpId, formData)
    } else {
      await saveFollowUp(formData)
    }
    ElMessage.success(isEdit.value ? tt('修改成功') : tt('新增成功'))
    dialogVisible.value = false
    loadData()
    loadDashboard()
  } catch (e) {
    ElMessage.error(e.message || tt('保存失败'))
  } finally {
    saving.value = false
  }
}

// 删除跟进记录
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `${tt("确认删除跟进记录【")}${row.followUpNo}${tt("】？删除后不可恢复。")}`,
      tt('删除确认'),
      { type: 'warning', confirmButtonText: tt('确认删除'), cancelButtonText: tt('取消') }
    )
  } catch (e) {
    return
  }
  try {
    await deleteFollowUp(row.followUpId)
    ElMessage.success(tt('删除成功'))
    loadData()
    loadDashboard()
  } catch (e) {
    // 拦截器已弹错误提示
  }
}

/**
 * 带条件打开新增（供「销售过程 → 每日任务清单」的「录入」直达）：
 * 预填来源类型=客户 + 来源名称/ID，避免跳过来只有空列表、还要用户自己找客户。
 */
async function openAddWithSource(name, id) {
  handleAdd()
  if (!name) return
  formData.sourceType = '客户'
  await loadSourceOptions('客户')
  if (id) formData.sourceId = id
  formData.sourceName = name
}

// 2026-10-09：支持带条件跳入 —— kw=客户名、sourceId=客户ID、add=1 直接打开新增
const _route = useRoute()
const _q = _route.query
if (_q.kw) search.keyword = String(_q.kw)

onMounted(() => {
  loadData()
  loadDashboard()
  // 加载员工下拉数据
  listAllEmployee().then(list => { employeeOptions.value = list || [] }).catch(() => {})
  if (_q.add === '1') openAddWithSource(_q.kw ? String(_q.kw) : '', _q.sourceId ? Number(_q.sourceId) : null)
})
useAutoRefresh(() => {
  loadData()
  loadDashboard()
  // 加载员工下拉数据
  listAllEmployee().then(list => { employeeOptions.value = list || [] }).catch(() => {})
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
