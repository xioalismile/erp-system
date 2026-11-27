<template>
  <div class="erp-page">
    <!-- 仪表盘统计卡片 -->
    <el-card class="dashboard-card" shadow="never">
      <div class="dashboard-row">
        <div class="stat-item" v-for="item in statItems" :key="item.key">
          <div class="stat-label">{{ item.label }}</div>
          <div class="stat-value" :style="{ color: item.color }">{{ item.value }}</div>
        </div>
      </div>
    </el-card>

    <!-- 采集任务撞风控/验证页：需要用户到服务器浏览器画面上手动验证（远程浏览器就是为这个做的） -->
    <el-alert
      v-for="r in riskAlerts"
      :key="r.platform"
      type="warning"
      show-icon
      :closable="false"
      style="margin-top:12px;"
    >
      <template #title>
        <span>{{ r.label }}{{ $tt('采集任务正停在验证/风控页面，需要你在服务器浏览器上完成验证') }}</span>
        <el-button type="primary" link @click="goHandleRisk(r)">{{ $tt('立即去处理') }}</el-button>
      </template>
    </el-alert>

    <!-- 筛选区 -->
    <el-card shadow="never" style="margin-top:12px;">
      <div class="filter-bar">
        <el-input v-model="search.keyword" :placeholder="$tt('任务名称/编号')" style="width:200px;" clearable @keyup.enter="handleSearch" />
        <el-select v-model="search.platform" :placeholder="$tt('采集平台')" style="width:140px;" clearable>
          <el-option v-for="p in platformOptions" :key="p.value" :label="$tf(p.label)" :value="p.value" />
        </el-select>
        <el-select v-model="search.status" :placeholder="$tt('任务状态')" style="width:120px;" clearable>
          <el-option v-for="s in statusOptions" :key="s.value" :label="$tf(s.label)" :value="s.value" />
        </el-select>
        <el-button type="primary" v-permission="perm('query')" @click="handleSearch">{{ $tt("查询") }}</el-button>
        <el-button v-permission="perm('reset')" @click="handleReset">{{ $tt("重置") }}</el-button>
        <div style="flex:1;"></div>
        <el-button type="primary" v-permission="perm('add')" @click="handleAdd">{{ $tt("+ 新增任务") }}</el-button>
        <el-button v-permission="perm('refresh')" @click="loadData">{{ $tt("刷新") }}</el-button>
      </div>
    </el-card>

    <!-- 任务列表 -->
    <el-card shadow="never" style="margin-top:12px;">
      <el-table :data="tasks" v-loading="loading" border stripe @sort-change="onSortChange">
        <el-table-column prop="taskNo" :label="$tf('任务编号')" width="160" fixed="left" sortable="custom">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="handleEdit(row)" style="font-weight:500;">{{ row.taskNo }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="taskName" :label="$tf('任务名称')" min-width="160" show-overflow-tooltip sortable="custom"/>
        <el-table-column :label="$tf('采集平台')" width="110">
          <template #default="{ row }">
            <el-tag :type="platformType(row.platform)" size="small">{{ platformLabel(row.platform) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$tf('关键词')" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-for="(kw, idx) in parseKeywords(row.keywords)" :key="idx" size="small" style="margin-right:4px;margin-bottom:2px;">{{ kw }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$tf('状态')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ $dt(statusLabel(row.status)) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$tf('已采集/已导入')" width="130" align="center">
          <template #default="{ row }">
            <span style="color:#409EFF;font-weight:600;">{{ row.capturedCount || 0 }}</span>
            <span style="color:#c0c4cc;"> / </span>
            <span style="color:#67C23A;font-weight:600;">{{ row.importedCount || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createDate" :label="$tf('创建时间')" width="160" sortable="custom"/>
        <el-table-column prop="lastRunTime" :label="$tf('最后执行时间')" width="160" sortable="custom">
          <template #default="{ row }">{{ row.lastRunTime || '-' }}</template>
        </el-table-column>
        <el-table-column :label="$tf('失败原因')" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.status === 'failed' && row.errorMessage" style="color:#F56C6C;">{{ row.errorMessage }}</span>
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

    <!-- 新增/编辑任务弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑任务' : '新增任务'" width="780px" :close-on-click-modal="false">
      <div style="display:flex;justify-content:flex-end;gap:8px;margin-bottom:12px;">
        <el-button v-permission="perm('dlgCancel')" @click="dialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button v-if="isEdit" type="danger" v-permission="perm('delete')" @click="handleDeleteInDialog" :loading="deleting" :disabled="saving">{{ $tt("删除任务") }}</el-button>
        <el-button v-if="isEdit && formData.status === 'running'" type="warning" v-permission="perm('disable')" @click="handlePauseInDialog" :loading="actioning" :disabled="saving">{{ $tt("暂停") }}</el-button>
        <el-button v-if="isEdit && formData.status !== 'running'" type="success" v-permission="perm('start')" @click="handleStartInDialog" :loading="actioning" :disabled="saving">{{ $tt("执行") }}</el-button>
        <el-button v-if="isEdit && formData.status === 'failed' && loginPlatformOf(formData.platform)" type="primary" plain @click="handleRelogin" :disabled="saving">{{ $tt("重新登录") }}</el-button>
        <el-button type="primary" v-permission="perm('save')" @click="handleSave" :loading="saving">{{ $tt("保存") }}</el-button>
      </div>
      <el-alert
        v-if="isEdit && formData.status === 'failed' && formData.errorMessage"
        :title="`${$tt('失败原因')}：${formData.errorMessage}`"
        type="error"
        :closable="false"
        show-icon
        style="margin-bottom:12px;"
      />
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px">
        <el-divider content-position="left">{{ $tt("基本信息") }}</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="$tf('任务编号')">
              <el-input v-model="formData.taskNo" :disabled="docCodeDisabled && !isEdit" :placeholder="docCodePlaceholder" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('任务名称')" prop="taskName">
              <el-input v-model="formData.taskName" :placeholder="$tt('请输入任务名称')" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="$tf('采集平台')" prop="platform">
              <el-select v-model="formData.platform" style="width:100%" :placeholder="$tt('请选择采集平台')">
                <el-option v-for="p in platformOptions" :key="p.value" :label="$tf(p.label)" :value="p.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="$tf('搜索关键词')" prop="keywords" :required="true">
          <div class="keyword-input-wrap">
            <el-tag
              v-for="(kw, idx) in formData.keywords"
              :key="idx"
              closable
              size="large"
              @close="removeKeyword(idx)"
              style="margin-right:6px;margin-bottom:6px;"
            >{{ kw }}</el-tag>
            <el-input
              v-model="keywordInput"
              :placeholder="$tt('输入关键词后回车添加')"
              style="width:240px;"
              @keyup.enter.stop="addKeyword"
            />
          </div>
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="$tf('行业筛选')">
              <el-input v-model="formData.industry" :placeholder="$tt('如：制造业（可选）')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('地区筛选')">
              <el-cascader
                v-model="areaPath"
                :options="regionOptions"
                :props="areaCascaderProps"
                :placeholder="areaPlaceholder"
                clearable
                style="width:100%"
                @change="onAreaChange"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="$tf('最大采集页数')">
              <el-input-number v-model="formData.maxPages" :min="1" :max="100" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('最大企业数')">
              <el-input-number v-model="formData.maxCompanies" :min="1" :max="1000" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="$tf('定时执行')" prop="cronExpression">
          <div class="cron-builder">
            <!-- 预设快捷按钮 -->
            <div class="cron-presets">
              <span class="cron-label">{{ $tt("快捷选择：") }}</span>
              <el-button size="small" v-permission="perm('apply')" @click="applyPreset('everyday_9')">{{ $tt("每天 09:00") }}</el-button>
              <el-button size="small" v-permission="perm('apply')" @click="applyPreset('everyday_18')">{{ $tt("每天 18:00") }}</el-button>
              <el-button size="small" v-permission="perm('apply')" @click="applyPreset('workday_9')">{{ $tt("工作日 09:00") }}</el-button>
              <el-button size="small" v-permission="perm('apply')" @click="applyPreset('hourly')">{{ $tt("每小时整点") }}</el-button>
              <el-button size="small" v-permission="perm('apply')" @click="applyPreset('monthly_1')">{{ $tt("每月 1 日 09:00") }}</el-button>
              <el-button size="small" type="info" v-permission="perm('apply')" @click="applyPreset('once')">{{ $tt("立即执行一次") }}</el-button>
            </div>

            <!-- 6 个字段独立选择 -->
            <div class="cron-fields">
              <div class="cron-field">
                <div class="field-label">{{ $tt("秒") }}</div>
                <el-select v-model="cronParts.second" size="small" @change="onCronPartChange">
                  <el-option :label="$tf('每秒（*）')" value="*" />
                  <el-option :label="$tf('0 秒')" value="0" />
                  <el-option :label="$tf('15 秒')" value="15" />
                  <el-option :label="$tf('30 秒')" value="30" />
                  <el-option :label="$tf('45 秒')" value="45" />
                  <el-option :label="$tf('每 10 秒')" value="0/10" />
                  <el-option :label="$tf('每 15 秒')" value="0/15" />
                  <el-option :label="$tf('每 30 秒')" value="0/30" />
                </el-select>
              </div>

              <div class="cron-field">
                <div class="field-label">{{ $tt("分") }}</div>
                <el-select v-model="cronParts.minute" size="small" @change="onCronPartChange">
                  <el-option :label="$tf('每分（*）')" value="*" />
                  <el-option v-for="m in 60" :key="'min-'+m" :label="`${m-1} 分`" :value="String(m-1)" />
                  <el-option :label="$tf('每 5 分')" value="0/5" />
                  <el-option :label="$tf('每 10 分')" value="0/10" />
                  <el-option :label="$tf('每 15 分')" value="0/15" />
                  <el-option :label="$tf('每 30 分')" value="0/30" />
                </el-select>
              </div>

              <div class="cron-field">
                <div class="field-label">{{ $tt("时") }}</div>
                <el-select v-model="cronParts.hour" size="small" @change="onCronPartChange">
                  <el-option :label="$tf('每小时（*）')" value="*" />
                  <el-option v-for="h in 24" :key="'hour-'+h" :label="`${String(h-1).padStart(2,'0')} 时`" :value="String(h-1)" />
                  <el-option :label="$tf('每 2 小时')" value="0/2" />
                  <el-option :label="$tf('每 6 小时')" value="0/6" />
                  <el-option :label="$tf('每 12 小时')" value="0/12" />
                </el-select>
              </div>

              <div class="cron-field">
                <div class="field-label">{{ $tt("日") }}</div>
                <el-select v-model="cronParts.day" size="small" @change="onCronPartChange">
                  <el-option :label="$tf('每日（*）')" value="*" />
                  <el-option :label="$tf('不指定（?）')" value="?" />
                  <el-option v-for="d in 31" :key="'day-'+d" :label="`${d} 号`" :value="String(d)" />
                  <el-option :label="$tf('每 5 日')" value="1/5" />
                  <el-option :label="$tf('每 10 日')" value="1/10" />
                  <el-option :label="$tf('每月最后一天')" value="L" />
                  <el-option :label="$tf('每月最后一个工作日')" value="LW" />
                </el-select>
              </div>

              <div class="cron-field">
                <div class="field-label">{{ $tt("月") }}</div>
                <el-select v-model="cronParts.month" size="small" @change="onCronPartChange">
                  <el-option :label="$tf('每月（*）')" value="*" />
                  <el-option v-for="(name, idx) in monthNames" :key="'mon-'+idx" :label="name" :value="String(idx+1)" />
                  <el-option :label="$tf('每 3 月（季）')" value="1/3" />
                  <el-option :label="$tf('每 6 月（半年）')" value="1/6" />
                </el-select>
              </div>

              <div class="cron-field">
                <div class="field-label">{{ $tt("周") }}</div>
                <el-select v-model="cronParts.week" size="small" @change="onCronPartChange">
                  <el-option :label="$tf('每周（*）')" value="*" />
                  <el-option :label="$tf('不指定（?）')" value="?" />
                  <el-option :label="$tf('周日')" value="SUN" />
                  <el-option :label="$tf('周一')" value="MON" />
                  <el-option :label="$tf('周二')" value="TUE" />
                  <el-option :label="$tf('周三')" value="WED" />
                  <el-option :label="$tf('周四')" value="THU" />
                  <el-option :label="$tf('周五')" value="FRI" />
                  <el-option :label="$tf('周六')" value="SAT" />
                  <el-option :label="$tf('周一至周五')" value="MON-FRI" />
                  <el-option :label="$tf('周六和周日')" value="SAT,SUN" />
                </el-select>
              </div>
            </div>

            <!-- 实时预览 -->
            <div class="cron-preview">
              <div class="preview-label">
                <el-icon><InfoFilled /></el-icon>
                <span>{{ $tt("执行频率：") }}</span>
              </div>
              <div class="preview-content">
                <el-tag type="success" size="large">{{ scheduleHumanReadable(formData.cronExpression) || '未设置（立即执行一次）' }}</el-tag>
                <span class="preview-cron">{{ $tt("Cron 表达式：") }}<code>{{ formData.cronExpression || '(空)' }}</code></span>
              </div>
            </div>

            <!-- 提示 -->
            <div class="cron-tip">
              <el-icon><WarningFilled /></el-icon>
              <span>{{ $tt("说明：日和周不能同时为 *，建议其中一个选 ?（不指定）；秒通常选 0") }}</span>
            </div>
          </div>
        </el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, InfoFilled, WarningFilled } from '@element-plus/icons-vue'
import AuditColumns from '@/components/AuditColumns.vue'
import { useAutoDocCode } from '@/composables/useAutoDocCode'
import { useRouter } from 'vue-router'
import {
  listTask, getTask, saveTask, updateTask, deleteTask,
  startTask, pauseTask, taskDashboard, getCaptureSession
} from '@/api/acquisition'
import { useTableSort } from '@/composables/useTableSort'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'
import { loadRegionOptions, parseAreaPath, joinAreaPath } from '@/constants/regionData'

const perm = usePagePerm('acquisition:task')
const { handleSortChange, getSortParams } = useTableSort()


const onSortChange = (e) => { handleSortChange(e); loadData() }
const router = useRouter()

// 平台选项（前端显示中文，后端存储英文）
const platformOptions = [
  { value: 'qichacha', label: '企查查' },
  { value: 'fengniao', label: '风鸟' },
  { value: 'tianyancha', label: '天眼查' },
  { value: 'gsxt', label: '工商系统' }
]
// 状态选项
const statusOptions = [
  { value: 'pending', label: '待执行' },
  { value: 'running', label: '运行中' },
  { value: 'completed', label: '已完成' },
  { value: 'failed', label: '失败' },
  { value: 'paused', label: '已暂停' }
]

// 平台/状态显示映射
const platformLabel = (v) => platformOptions.find(p => p.value === v)?.label || v || '-'
const statusLabel = (v) => statusOptions.find(s => s.value === v)?.label || '待执行'
const platformType = (p) => ({ qichacha: 'primary', fengniao: 'success', tianyancha: 'warning' }[p] || 'info')
const statusType = (s) => ({ pending: 'warning', running: 'primary', completed: 'success', failed: 'danger', paused: 'info' }[s] || 'info')

// 任务平台 -> 登录页平台标识（登录页支持 qcc/fnb/tyc；工商系统 gsxt 无登录页）
// 爱企查 aiqicha 已下线（百度反自动化硬拦截），故不再映射到登录页
const PLATFORM_TO_LOGIN = { qichacha: 'qcc', fengniao: 'fnb', tianyancha: 'tyc' }
const loginPlatformOf = (p) => PLATFORM_TO_LOGIN[p] || ''

// 数据状态
const loading = ref(false)
const saving = ref(false)
const deleting = ref(false)
const actioning = ref(false)
const tasks = ref([])
const dashboard = ref({})
const search = reactive({ keyword: '', platform: '', status: '' })
const page = reactive({ current: 1, size: 20, total: 0, ...getSortParams() })

// 仪表盘统计项
const statItems = computed(() => [
  { key: 'total', label: '总任务数', value: dashboard.value.totalTasks || 0, color: '#409EFF' },
  { key: 'completed', label: '已完成', value: dashboard.value.completedTasks || 0, color: '#67C23A' },
  { key: 'running', label: '运行中', value: dashboard.value.runningTasks || 0, color: '#409EFF' },
  { key: 'failed', label: '失败', value: dashboard.value.failedTasks || 0, color: '#F56C6C' },
  { key: 'collected', label: '已采集企业数', value: dashboard.value.capturedCount || 0, color: '#E6A23C' },
  { key: 'imported', label: '已导入线索数', value: dashboard.value.importedCount || 0, color: '#909399' }
])

// 弹窗状态
const dialogVisible = ref(false)
const isEdit = ref(false)
const { docCodeDisabled, docCodePlaceholder, autoFillDocCodeOnCreate } = useAutoDocCode('ACQUISITION_TASK')
const formRef = ref(null)
const keywordInput = ref('')
const defaultForm = () => ({
  taskId: null,
  taskNo: '',
  taskName: '',
  platform: 'qichacha',
  keywords: [],
  industry: '',
  area: '',
  maxPages: 5,
  maxCompanies: 50,
  cronExpression: '',
  status: '',
  errorMessage: ''
})
const formData = reactive(defaultForm())
const formRules = {
  taskName: [{ required: true, message: tt('请输入任务名称'), trigger: 'blur' }],
  platform: [{ required: true, message: tt('请选择采集平台'), trigger: 'change' }],
  keywords: [{
    validator: (rule, value, callback) => {
      if (!value || !Array.isArray(value) || value.length === 0) {
        callback(new Error('请至少添加一个关键词'))
      } else {
        callback()
      }
    },
    trigger: 'change'
  }]
}

// 地区筛选：省/市/区三级级联（2026-09-23 由自由文本输入改下拉）
// 起因：手输"广州白云区"这类连写，后端拆词后要求地址含连续串"广州白云"，而地址是"广东省广州市白云区"→被误剔除。
// 约定：存储仍是字符串（formData.area），只在用户操作级联时才回写；初始化赋值不触发 change，
//       因此历史手输值若解析不出三级路径，会原样保留（占位处提示"原地区：xxx"），绝不被清空。
const regionOptions = ref([])
const areaPath = ref([])
const areaFallback = ref('')
const areaCascaderProps = { expandTrigger: 'hover' }
const areaPlaceholder = computed(() =>
  areaFallback.value
    ? `${tt('原地区：')}${areaFallback.value}${tt('（可重新选择）')}`
    : tt('请选择省/市/区（可选）')
)

const onAreaChange = (val) => {
  areaFallback.value = ''
  formData.area = joinAreaPath(val || [])
}

// 关键词处理
const parseKeywords = (kw) => {
  if (!kw) return []
  if (Array.isArray(kw)) return kw
  // 兼容后端JSON数组字符串 ["a","b"] 和逗号分隔
  if (kw.startsWith('[')) {
    try { return JSON.parse(kw) } catch { /* fallthrough */ }
  }
  return kw.split(',').map(k => k.trim()).filter(Boolean)
}

const addKeyword = () => {
  const val = keywordInput.value.trim()
  if (!val) return
  if (!formData.keywords.includes(val)) {
    formData.keywords.push(val)
  }
  keywordInput.value = ''
}

const removeKeyword = (idx) => {
  formData.keywords.splice(idx, 1)
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const params = {
      current: page.current, size: page.size,
      keyword: search.keyword || undefined,
      platform: search.platform || undefined,
      status: search.status || undefined,
      ...getSortParams()
    }
    const res = await listTask(params)
    // request拦截器已解包 Result.data，这里res直接是PageResult
    const data = res || {}
    tasks.value = data.records || []
    page.total = data.total || 0
    checkRiskSessions()
  } catch (e) {
    console.error('加载任务列表失败', e)
    ElMessage.error(tt('加载任务列表失败'))
  } finally {
    loading.value = false
  }
}

const loadDashboard = async () => {
  try {
    const res = await taskDashboard()
    // request拦截器已解包，res直接是数据对象
    dashboard.value = res || {}
  } catch (e) { /* 忽略仪表盘加载错误 */ }
}

// ===== 采集任务撞风控：顶部提示「立即去处理」 =====
// 采集服务撞到风控/验证页时，会把任务挂成"等待人工验证"并**保持采集浏览器存活**（不再直接失败关浏览器），
// 这里定时问一次（只问运行中任务涉及的平台），命中就在列表顶部给入口 —— 点进去画面已经自动接好
const riskAlerts = ref([])
let riskTimer = null
const checkRiskSessions = async () => {
  const running = tasks.value.filter((t) => t.status === 'running')
  const plats = [...new Set(running.map((t) => loginPlatformOf(t.platform)).filter(Boolean))]
  const alerts = []
  for (const p of plats) {
    try {
      const d = await getCaptureSession(p)
      if (d && d.success && d.source === 'capture' && d.status === 'blocked') {
        const t = running.find((x) => loginPlatformOf(x.platform) === p)
        alerts.push({ platform: p, label: t ? platformLabel(t.platform) : '', taskNo: t ? t.taskNo : '' })
      }
    } catch (e) { /* 采集服务不可用时忽略，不影响列表 */ }
  }
  riskAlerts.value = alerts
}
const goHandleRisk = (r) => {
  router.push({ path: '/acquisition/login', query: { platform: r.platform } })
}

const handleSearch = () => { page.current = 1; loadData() }
const handleReset = () => {
  search.keyword = ''; search.platform = ''; search.status = ''
  page.current = 1; loadData()
}

// 新增/编辑
const handleAdd = () => {
  isEdit.value = false
  Object.assign(formData, defaultForm())
  autoFillDocCodeOnCreate(formData, 'taskNo')
  // 重置 cron 下拉为默认值
  Object.assign(cronParts, defaultCronParts())
  // 重置地区级联
  areaPath.value = []
  areaFallback.value = ''
  keywordInput.value = ''
  dialogVisible.value = true
}

const handleEdit = async (row) => {
  isEdit.value = true
  try {
    const detail = await getTask(row.taskId)
    const d = detail || {}
    const merged = { ...defaultForm(), ...d }
    merged.keywords = parseKeywords(merged.keywords)
    // cronExpression 直接绑定到下拉选择，无需反向同步
    Object.assign(formData, merged)
    // 地区回显：解析成三级级联路径；解析成功顺带把历史手输文本规范化为"省市区"拼接（采集端拆词更准），
    // 解析不出则保留原文本并在占位处提示，绝不清空
    if (!regionOptions.value.length) regionOptions.value = await loadRegionOptions()
    areaPath.value = parseAreaPath(merged.area, regionOptions.value)
    if (areaPath.value.length) {
      formData.area = joinAreaPath(areaPath.value)
      areaFallback.value = ''
    } else {
      areaFallback.value = merged.area || ''
    }
    // 同步 cron 表达式到 6 个下拉
    Object.assign(cronParts, parseCronToParts(merged.cronExpression || ''))
    keywordInput.value = ''
    dialogVisible.value = true
  } catch (e) {
    ElMessage.error(tt('加载任务详情失败'))
  }
}

// cron 表达式转人类可读描述
// 支持：0 0 9 * * ? / 0 0 14 * * ? / 0 0 * * * ? / 0 0 9 ? * MON-FRI / 0 0 9 ? * MON / 0 0 9 1 * ? 等
// 用户不熟悉 cron 语法，所以把表达式翻译成中文描述展示
const WEEKDAY_MAP = { SUN: '周日', MON: '周一', TUE: '周二', WED: '周三', THU: '周四', FRI: '周五', SAT: '周六' }

// 月份名称
const monthNames = ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月', '12月']

// cron 6 字段的状态（秒/分/时/日/月/周）
// 每个字段独立选择，最后组合成完整 cron 表达式
const defaultCronParts = () => ({ second: '0', minute: '0', hour: '*', day: '*', month: '*', week: '?' })
const cronParts = reactive(defaultCronParts())

// 从 cron 字符串解析回 6 个字段（用于编辑任务时回填下拉）
const parseCronToParts = (cron) => {
  if (!cron) return defaultCronParts()
  const parts = cron.split(/\s+/)
  if (parts.length !== 6) return defaultCronParts()
  return {
    second: parts[0],
    minute: parts[1],
    hour: parts[2],
    day: parts[3],
    month: parts[4],
    week: parts[5]
  }
}

// 任意一个下拉变化时，重新组合 cron 表达式
const onCronPartChange = () => {
  // 自动修正：日和周不能同时为 *（Quartz 规则）
  // 如果用户都选了 *，强制把周改成 ?
  if (cronParts.day === '*' && cronParts.week === '*') {
    cronParts.week = '?'
  }
  // 自动修正：日和周不能同时为 ?（至少要有一个指定）
  if (cronParts.day === '?' && cronParts.week === '?') {
    cronParts.day = '*'
  }
  formData.cronExpression = [cronParts.second, cronParts.minute, cronParts.hour, cronParts.day, cronParts.month, cronParts.week].join(' ')
}

// 快捷预设
const applyPreset = (key) => {
  const presets = {
    once: '',                          // 立即执行一次（空）
    everyday_9: '0 0 9 * * ?',         // 每天 09:00
    everyday_18: '0 0 18 * * ?',       // 每天 18:00
    workday_9: '0 0 9 ? * MON-FRI',    // 工作日 09:00
    hourly: '0 0 * * * ?',              // 每小时整点
    monthly_1: '0 0 9 1 * ?'           // 每月 1 日 09:00
  }
  const cron = presets[key] ?? ''
  formData.cronExpression = cron
  // 同步到下拉
  Object.assign(cronParts, parseCronToParts(cron || '0 0 * * * ?'))
  // 立即执行一次时清空表达式
  if (key === 'once') {
    formData.cronExpression = ''
  }
}

const scheduleHumanReadable = (cron) => {
  if (!cron) return ''
  const parts = cron.split(/\s+/)
  if (parts.length !== 6) return cron
  const [sec, min, hour, day, month, week] = parts

  // 时间格式化（补 0）
  const pad = (v) => String(v).padStart(2, '0')
  const isNumeric = (v) => /^\d+$/.test(v)
  const timeStr = isNumeric(hour) && isNumeric(min) ? `${pad(hour)}:${pad(min)}` : null

  // 每小时整点（分秒为0，小时为*）
  if (sec === '0' && min === '0' && hour === '*') return '每小时整点'

  // 每天
  if (day === '*' && month === '*' && week === '?') {
    return timeStr ? `每天 ${timeStr}` : `每天（小时=${hour}, 分=${min}）`
  }

  // 每周
  if (week !== '?') {
    if (/^[A-Z]{3}$/.test(week)) return `每${WEEKDAY_MAP[week]} ${timeStr || ''}`.trim()
    if (/^[A-Z]{3}-[A-Z]{3}$/.test(week)) {
      const [s, e] = week.split('-')
      return `每${WEEKDAY_MAP[s]}至${WEEKDAY_MAP[e]} ${timeStr || ''}`.trim()
    }
    if (/^[A-Z]{3}(,[A-Z]{3})*$/.test(week)) {
      const days = week.split(',').map(d => WEEKDAY_MAP[d] || d).join('、')
      return `每${days} ${timeStr || ''}`.trim()
    }
  }

  // 每月某日
  if (day !== '*' && day !== '?' && month === '*' && week === '?') {
    return `每月${day}日 ${timeStr || ''}`.trim()
  }

  // 间隔执行（如 0/5）
  if (sec.includes('/')) return `每 ${sec.split('/')[1]} 秒执行一次`
  if (min.includes('/')) return `每 ${min.split('/')[1]} 分钟执行一次`
  if (hour.includes('/')) return `每 ${hour.split('/')[1]} 小时执行一次`

  // 兜底
  return cron
}

const handleSave = async () => {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  if (formData.keywords.length === 0) {
    ElMessage.warning(tt('请至少添加一个关键词'))
    return
  }
  saving.value = true
  try {
    // 后端keywords字段存为JSON数组字符串
    const payload = { ...formData, keywords: JSON.stringify(formData.keywords) }
    if (isEdit.value) {
      await updateTask(formData.taskId, payload)
    } else {
      await saveTask(payload)
    }
    ElMessage.success(isEdit.value ? tt('修改成功') : tt('新增成功'))
    dialogVisible.value = false
    loadData()
    loadDashboard()
  } catch (e) {
    ElMessage.error(e?.message || tt('保存失败'))
  } finally {
    saving.value = false
  }
}

// 编辑弹窗内删除任务
const handleDeleteInDialog = async () => {
  if (!formData.taskId) return
  try {
    await ElMessageBox.confirm(
      `${tt("确认删除任务【")}${formData.taskName}${tt("】？删除后不可恢复。")}`,
      tt('删除确认'),
      { type: 'warning', confirmButtonText: tt('确认删除'), cancelButtonText: tt('取消') }
    )
  } catch {
    return
  }
  deleting.value = true
  try {
    await deleteTask(formData.taskId)
    ElMessage.success(tt('删除成功'))
    dialogVisible.value = false
    loadData()
    loadDashboard()
  } catch (e) {
    ElMessage.error(e?.message || tt('删除失败'))
  } finally {
    deleting.value = false
  }
}

// 编辑弹窗内执行任务
const handleStartInDialog = async () => {
  if (!formData.taskId) return
  actioning.value = true
  try {
    await startTask(formData.taskId)
    ElMessage.success(tt('任务已启动'))
    formData.status = 'running'
    loadData()
    loadDashboard()
  } catch (e) {
    ElMessage.error(e?.message || tt('启动失败'))
  } finally {
    actioning.value = false
  }
}

// 编辑弹窗内暂停任务
const handlePauseInDialog = async () => {
  if (!formData.taskId) return
  actioning.value = true
  try {
    await pauseTask(formData.taskId)
    ElMessage.success(tt('任务已暂停'))
    formData.status = 'paused'
    loadData()
    loadDashboard()
  } catch (e) {
    ElMessage.error(e?.message || tt('暂停失败'))
  } finally {
    actioning.value = false
  }
}

// 编辑弹窗内重新登录：跳转到采集平台登录页并预选该任务对应平台
// 失败原因多为登录态失效，用户可直接在登录页重新扫码
const handleRelogin = () => {
  const lp = loginPlatformOf(formData.platform)
  if (!lp) return
  dialogVisible.value = false
  router.push({ path: '/acquisition/login', query: { platform: lp } })
}

onMounted(() => {
  loadData()
  loadDashboard()
  // 预加载省市区数据（懒加载，避免弹窗首次打开时级联下拉空白）
  loadRegionOptions().then((opts) => { regionOptions.value = opts })
  // 风控提示要"等得及"：列表本身有自动刷新，这里再兜一层 8 秒轮询
  checkRiskSessions()
  riskTimer = setInterval(checkRiskSessions, 8000)
})
onBeforeUnmount(() => {
  if (riskTimer) {
    clearInterval(riskTimer)
    riskTimer = null
  }
})
useAutoRefresh(() => {
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
  background: #fff; border-radius: 8px; padding: 14px 20px;
  border: 1px solid #ebeef5;
  display: flex; flex-direction: column; justify-content: center;
  transition: all 0.2s;
}
.stat-item:hover { box-shadow: 0 2px 12px rgba(0,0,0,0.1); transform: translateY(-2px); }
.stat-label { font-size: 13px; color: #909399; margin-bottom: 6px; }
.stat-value { font-size: 26px; font-weight: 700; }
.filter-bar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.keyword-input-wrap {
  display: flex; flex-wrap: wrap; align-items: center;
  border: 1px solid #dcdfe6; border-radius: 4px; padding: 6px 8px;
  min-height: 40px;
}
.keyword-input-wrap :deep(.el-input) { width: 240px; }
.keyword-input-wrap :deep(.el-input__wrapper) { box-shadow: none; }

/* Cron 可视化构建器 */
.cron-builder {
  width: 100%;
  background: #fafbfc;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px;
}
.cron-presets {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding-bottom: 12px;
  border-bottom: 1px dashed #dcdfe6;
  margin-bottom: 12px;
}
.cron-presets .cron-label {
  font-size: 13px;
  color: #606266;
  font-weight: 500;
}
.cron-fields {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 10px;
  margin-bottom: 12px;
}
.cron-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.cron-field .field-label {
  font-size: 12px;
  color: #909399;
  font-weight: 600;
  text-align: center;
  padding: 2px 0;
  background: #ecf5ff;
  border-radius: 4px;
  color: #409eff;
}
.cron-field :deep(.el-select) {
  width: 100%;
}
.cron-preview {
  background: #f0f9eb;
  border: 1px solid #e1f3d8;
  border-radius: 6px;
  padding: 10px 14px;
  margin-bottom: 8px;
}
.cron-preview .preview-label {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #67c23a;
  margin-bottom: 6px;
  font-weight: 500;
}
.cron-preview .preview-content {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.cron-preview .preview-cron {
  font-size: 12px;
  color: #909399;
}
.cron-preview .preview-cron code {
  background: #f4f4f5;
  padding: 2px 6px;
  border-radius: 3px;
  font-family: 'Consolas', monospace;
  color: #303133;
}
.cron-tip {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #e6a23c;
}
.cron-tip .el-icon {
  color: #e6a23c;
}
</style>
