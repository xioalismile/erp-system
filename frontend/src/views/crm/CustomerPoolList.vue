<template>
  <div class="erp-page">
    <!-- 公海池统计仪表盘 -->
    <el-card class="dashboard-card" shadow="never">
      <div class="dashboard-row">
        <div class="stat-item" v-for="item in statItems" :key="item.key" @click="handleStatClick(item.key)">
          <div class="stat-label">{{ item.label }}</div>
          <div class="stat-value" :style="{ color: item.color }">{{ item.value }}</div>
          <div class="stat-hint">{{ $tt("点击查看明细") }}</div>
        </div>
      </div>
      <div class="config-info" v-if="activeConfig">
        <el-tag type="success" size="large">
          <el-icon><Setting /></el-icon>
          当前规则：{{ activeConfig.noFollowDays || 90 }}天未跟进 / {{ activeConfig.noOrderDays || 180 }}天未开单 → 自动回收公海
        </el-tag>
      </div>
    </el-card>

    <!-- 操作工具栏 -->
    <el-card shadow="never" style="margin-top:12px;">
      <div class="erp-search-bar">
        <el-input v-model="search.keyword" :placeholder="$tt('客户编号/名称/联系人/电话')" clearable style="width:260px" />
        <el-button type="primary" v-permission="perm('query')" @click="handleSearch">{{ $tt("查询") }}</el-button>
        <el-button v-permission="perm('reset')" @click="handleReset">{{ $tt("重置") }}</el-button>
        <el-tooltip :content="$tt('列设置')" placement="top">
          <el-button :icon="Setting" circle v-permission="perm('columnSetting')" @click="openColumnSetting" />
        </el-tooltip>
        <div style="flex:1"></div>
        <el-button type="primary" v-permission="perm('add')" @click="handleAdd">{{ $tt("新增客户") }}</el-button>
        <el-button type="success" v-permission="perm('assign')" @click="handleBatchAssign" :disabled="selection.length === 0">
          批量分配({{ selection.length }})
        </el-button>
        <el-button type="primary" v-permission="perm('claim')" @click="handleBatchClaim" :disabled="selection.length === 0">
          批量领取({{ selection.length }})
        </el-button>
        <el-button type="primary" v-permission="perm('assign')" @click="handleAssignAll">{{ $tt("一键分配全部") }}</el-button>
        <el-button type="warning" v-permission="perm('recycle')" @click="handleAutoRecycle">{{ $tt("立即执行自动回收") }}</el-button>
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

    <!-- 公海客户列表 -->
    <el-card shadow="never" style="margin-top:12px;">
      <el-table :data="tableData" v-loading="loading" border stripe @selection-change="onSelectionChange" @sort-change="onSortChange" :key="configVersion">
        <el-table-column type="selection" width="50" />
        <el-table-column type="index" :label="$tf('序号')" width="60" />
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
              <span v-else-if="col.type === 'format'">{{ col.format(row) }}</span>
            </template>
          </el-table-column>
        </template>
        <AuditColumns />
        <el-table-column :label="$tf('操作')" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" v-permission="perm('claim')" @click="handleClaim(row)">{{ $tt("领取") }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page.current"
        v-model:page-size="page.size"
        :page-sizes="[10, 20, 50, 100]"
        :total="page.total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadData"
        @current-change="loadData"
        style="margin-top:16px;justify-content:flex-end;display:flex"
      />
    </el-card>

    <!-- 批量分配弹窗 -->
    <el-dialog v-model="assignVisible" :title="$tf('批量分配客户')" width="450px">
      <el-form label-width="100px">
        <el-form-item :label="$tf('选中数量')">
          <span>{{ assignMode === 'all' ? '全部 ' + page.total + ' ' : selection.length }} 个客户</span>
        </el-form-item>
        <el-form-item :label="$tf('分配销售员')" required>
          <el-select v-model="assignSalesId" :placeholder="$tt('请选择销售员')" filterable clearable style="width:100%">
            <el-option v-for="opt in employeeOptions" :key="opt.value" :label="$tf(opt.label)" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-alert v-if="assignMode === 'all'" type="warning" :closable="false" style="margin-top:8px;">
          一键分配将把当前公海池中所有客户分配给选定的销售员，请谨慎操作。
        </el-alert>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="assignVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('dlgConfirm')" @click="confirmBatchAssign" :loading="saving">{{ $tt("确认分配") }}</el-button>
      </template>
    </el-dialog>

    <!-- 公海池配置弹窗 -->
    <el-dialog v-model="configVisible" :title="$tf('公海回收规则设置')" width="600px">
      <el-alert type="info" :closable="false" style="margin-bottom:16px;">
        设置客户未跟进/未开单的天数阈值，超过阈值的私海客户将被系统自动回收到公海池。系统每日凌晨1:00自动执行检查。
      </el-alert>
      <el-form ref="configFormRef" :model="configForm" :rules="configRules" label-width="140px">
        <el-form-item :label="$tf('规则名称')">
          <el-input v-model="configForm.configName" :placeholder="$tt('如：默认公海规则')" />
        </el-form-item>
        <el-form-item :label="$tf('未跟进天数')" prop="noFollowDays">
          <el-input-number v-model="configForm.noFollowDays" :min="1" :max="365" style="width:100%" />
          <div class="form-tip">{{ $tt("超过此天数未跟进的客户，自动回收到公海") }}</div>
        </el-form-item>
        <el-form-item :label="$tf('未开单天数')" prop="noOrderDays">
          <el-input-number v-model="configForm.noOrderDays" :min="1" :max="999" style="width:100%" />
          <div class="form-tip">{{ $tt("超过此天数未开单的客户，自动回收到公海") }}</div>
        </el-form-item>
        <el-form-item :label="$tf('预警天数')" prop="warningDays">
          <el-input-number v-model="configForm.warningDays" :min="1" :max="365" style="width:100%" />
          <div class="form-tip">{{ $tt("提前多少天发出跟进预警提醒") }}</div>
        </el-form-item>
        <el-form-item :label="$tf('是否启用')" prop="isEnabled">
          <el-switch v-model="configForm.isEnabled" :active-value="1" :inactive-value="0" :active-text="$tt('启用')" :inactive-text="$tt('停用')" />
        </el-form-item>
        <el-form-item :label="$tf('备注')">
          <el-input v-model="configForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="configVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="warning" v-permission="perm('recycle')" @click="handleAutoRecycleFromConfig" :loading="recycling">
          保存并立即执行回收
        </el-button>
        <el-button type="primary" v-permission="perm('save')" @click="saveConfig" :loading="saving">{{ $tt("保存配置") }}</el-button>
      </template>
    </el-dialog>

    <!-- 新增公海客户弹窗 -->
    <el-dialog v-model="addVisible" :title="$tf('新增公海客户')" width="600px">
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="100px">
        <el-form-item :label="$tf('客户名称')" prop="customerName">
          <el-input v-model="addForm.customerName" :placeholder="$tt('请输入客户名称')" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item :label="$tf('联系人')" prop="contactPerson">
              <el-input v-model="addForm.contactPerson" :placeholder="$tt('请输入联系人')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('联系电话')" prop="contactPhone">
              <el-input v-model="addForm.contactPhone" :placeholder="$tt('请输入联系电话')" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item :label="$tf('客户类型')">
              <DictSelect type-code="customer_type" v-model="addForm.customerType" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('客户等级')">
              <DictSelect type-code="customer_level" v-model="addForm.customerLevel" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item :label="$tf('客户来源')">
              <DictSelect type-code="customer_source" v-model="addForm.sourceType" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('所属行业')">
              <DictSelect type-code="industry" v-model="addForm.industry" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item :label="$tf('意向等级')">
              <el-select v-model="addForm.intentionLevel" style="width:100%">
                <el-option :label="$tf('A级（高意向）')" value="A" />
                <el-option :label="$tf('B级（中意向）')" value="B" />
                <el-option :label="$tf('C级（低意向）')" value="C" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('CRM状态')">
              <el-select v-model="addForm.crmStatus" style="width:100%">
                <el-option :label="$tf('潜在客户')" value="潜在客户" />
                <el-option :label="$tf('意向客户')" value="意向客户" />
                <el-option :label="$tf('报价客户')" value="报价客户" />
                <el-option :label="$tf('成交客户')" value="成交客户" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="$tf('联系地址')">
          <el-input v-model="addForm.address" :placeholder="$tt('请输入联系地址')" />
        </el-form-item>
        <el-form-item :label="$tf('备注')">
          <el-input v-model="addForm.remark" type="textarea" :rows="2" :placeholder="$tt('备注信息')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="addVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('dlgConfirm')" @click="confirmAdd" :loading="saving">{{ $tt("确认新增") }}</el-button>
      </template>
    </el-dialog>

    <!-- 列设置抽屉 -->
    <ColumnSettingDrawer v-model="columnSettingVisible" :page-key="'crm-pool'" :columns="allColumns" />
  </div>
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Setting } from '@element-plus/icons-vue'
import {
  poolList, batchAssign, claimFromPool,
  listPoolConfig, savePoolConfig, getActivePoolConfig,
  triggerAutoRecycle
} from '@/api/crm/pool'
import { saveCustomer } from '@/api/crm/customer'
import { listAllEmployee } from '@/api/basedata/employee'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
import AuditColumns from '@/components/AuditColumns.vue'
import DictSelect from '@/components/DictSelect.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { downloadTemplate, exportData, importData } from '@/utils/exportUtil'
import { useTableSort } from '@/composables/useTableSort'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('crm:pool')
const { handleSortChange, getSortParams } = useTableSort()


const onSortChange = (e) => { handleSortChange(e); loadData() }
// 列定义（用于列设置抽屉+动态渲染）：type 标记单元格渲染分支
// tag=公海天数(超30天红色) format=日期/归属人格式化 纯文本列无 type
const allColumns = [
  { prop: 'customerCode', label: '客户编号', width: 140, sortable: true },
  { prop: 'customerName', label: '客户名称', minWidth: 180, sortable: true, tooltip: true },
  { prop: 'contactPerson', label: '联系人', width: 110, sortable: true },
  { prop: 'contactPhone', label: '联系电话', width: 140, sortable: true },
  { prop: 'poolEnterDate', label: '进入公海日期', width: 160, sortable: true, type: 'format', format: (row) => formatDate(row.poolEnterDate) },
  { prop: 'daysInPool', label: '公海天数', width: 100, type: 'tag', tagType: (row) => (getDaysInPool(row.poolEnterDate) > 30 ? 'danger' : 'info'), tagText: (row) => `${getDaysInPool(row.poolEnterDate)}天` },
  { prop: 'lastFollowUpDate', label: '最后跟进日期', width: 130, sortable: true, type: 'format', format: (row) => formatDate(row.lastFollowUpDate) },
  { prop: 'ownerUserId', label: '原归属人', width: 100, sortable: true, type: 'format', format: (row) => (row.ownerUserId ? '用户#' + row.ownerUserId : '—') }
]
const { columnSettingVisible, openColumnSetting, isColVisible, getColFixed, configVersion, sortedColumns } = useColumnSetting('crm-pool', allColumns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))

const loading = ref(false)
const saving = ref(false)
const recycling = ref(false)
const tableData = ref([])
const search = reactive({ keyword: '' })
const page = reactive({ current: 1, size: 20, total: 0, ...getSortParams() })
const selection = ref([])
const activeConfig = ref(null)

const employeeOptions = ref([])

// 统计项
const statItems = computed(() => [
  { key: 'total', label: '公海客户总数', value: page.total, color: '#409EFF' },
  { key: 'recycled', label: '今日回收数', value: todayRecycled.value, color: '#E6A23C' },
  { key: 'overdue', label: '超30天未领取', value: overdueCount.value, color: '#F56C6C' }
])

const todayRecycled = ref(0)
const overdueCount = ref(0)

function formatDate(d) {
  if (!d) return '—'
  if (typeof d === 'string') return d.replace('T', ' ').slice(0, 16)
  return String(d)
}

function getDaysInPool(dateStr) {
  if (!dateStr) return 0
  const d = new Date(dateStr)
  const now = new Date()
  return Math.floor((now - d) / (1000 * 60 * 60 * 24))
}

// 加载数据
async function loadData() {
  loading.value = true
  try {
    const res = await poolList({
      page: page.current,
      size: page.size,
      keyword: search.keyword || undefined
    ,
      ...getSortParams()})
    tableData.value = res.records || []
    page.total = res.total || 0
    // 计算统计
    todayRecycled.value = (res.records || []).filter(r => {
      if (!r.poolEnterDate) return false
      const d = new Date(r.poolEnterDate)
      const today = new Date()
      return d.toDateString() === today.toDateString()
    }).length
    overdueCount.value = (res.records || []).filter(r => getDaysInPool(r.poolEnterDate) > 30).length
  } catch (e) {
    ElMessage.error(e.message || tt('加载失败'))
  } finally {
    loading.value = false
  }
}

function handleSearch() { page.current = 1; loadData() }
function handleReset() { search.keyword = ''; page.current = 1; loadData() }

const handleStatClick = (key) => {
  if (key === 'recycled') {
    search.keyword = ''
    page.current = 1
    loadData()
    ElMessage.info('已筛选今日回收客户（请查看tt("进入公海日期")为今天的记录）')
  } else if (key === 'overdue') {
    search.keyword = ''
    page.current = 1
    loadData()
    ElMessage.info('已筛选超30天未领取客户（请查看tt("公海天数")列）')
  } else {
    search.keyword = ''
    page.current = 1
    loadData()
    ElMessage.info(tt('已显示全部公海客户'))
  }
}

function onSelectionChange(rows) { selection.value = rows }

// 批量分配
const assignVisible = ref(false)
const assignSalesId = ref(null)
const assignMode = ref('selected') // 'selected' | 'all'

function handleBatchAssign() {
  if (selection.value.length === 0) {
    ElMessage.warning(tt('请先选择客户'))
    return
  }
  assignMode.value = 'selected'
  assignSalesId.value = null
  assignVisible.value = true
}

function handleAssignAll() {
  if (page.total === 0) {
    ElMessage.warning(tt('公海池中没有客户'))
    return
  }
  ElMessageBox.confirm(
    `${tt("确认将公海池中全部 ")}${page.total}${tt(" 个客户一键分配给同一销售员？")}`,
    tt('一键分配确认'),
    { type: 'warning', confirmButtonText: tt('继续'), cancelButtonText: tt('取消') }
  ).then(() => {
    assignMode.value = 'all'
    assignSalesId.value = null
    assignVisible.value = true
  }).catch(() => {})
}

async function confirmBatchAssign() {
  if (!assignSalesId.value) {
    ElMessage.warning(tt('请选择销售员'))
    return
  }
  saving.value = true
  try {
    let ids
    if (assignMode.value === 'all') {
      // 一键分配：先获取全部客户ID
      const allRes = await poolList({ page: 1, size: 9999, keyword: search.keyword || undefined })
      ids = (allRes.records || []).map(r => r.customerId || r.id)
      if (ids.length === 0) {
        ElMessage.warning(tt('没有可分配的客户'))
        saving.value = false
        return
      }
    } else {
      ids = selection.value.map(r => r.customerId || r.id)
    }
    await batchAssign(ids, assignSalesId.value)
    ElMessage.success(`${tt("成功分配 ")}${ids.length}${tt(" 个客户")}`)
    assignVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error(e.message || tt('分配失败'))
  } finally {
    saving.value = false
  }
}

// 单个领取
async function handleClaim(row) {
  try {
    await ElMessageBox.confirm(
      `${tt("确认领取客户「")}${row.customerName}${tt("」到自己的私海？")}`,
      tt('领取确认'),
      { type: 'info', confirmButtonText: tt('领取'), cancelButtonText: tt('取消') }
    )
    await claimFromPool(row.customerId || row.id)
    ElMessage.success(tt('领取成功'))
    loadData()
  } catch (e) {
    if (e && e.message) ElMessage.error(e.message)
  }
}

// 批量领取
async function handleBatchClaim() {
  if (selection.value.length === 0) {
    ElMessage.warning(tt('请先选择客户'))
    return
  }
  try {
    await ElMessageBox.confirm(
      `${tt("确认批量领取 ")}${selection.value.length}${tt(" 个客户到自己的私海？")}`,
      tt('批量领取确认'),
      { type: 'info', confirmButtonText: tt('领取'), cancelButtonText: tt('取消') }
    )
    saving.value = true
    let success = 0, fail = 0
    for (const row of selection.value) {
      try {
        await claimFromPool(row.customerId || row.id)
        success++
      } catch (e) {
        fail++
      }
    }
    ElMessage.success(`批量领取完成：成功 ${success} 个${fail > 0 ? `${tt("，失败 ")}${fail}${tt(" 个")}` : ''}`)
    loadData()
  } catch (e) {
    if (e && e.message) ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

// 手动触发自动回收
async function handleAutoRecycle() {
  try {
    await ElMessageBox.confirm(
      tt('确认立即执行公海自动回收？系统将根据当前回收规则，将未跟进/未开单的私海客户自动回收到公海。'),
      tt('自动回收确认'),
      { type: 'warning', confirmButtonText: tt('执行回收'), cancelButtonText: tt('取消') }
    )
    recycling.value = true
    const res = await triggerAutoRecycle()
    const count = res?.recycled || 0
    ElMessage.success(`${tt("自动回收完成，共回收 ")}${count}${tt(" 个客户到公海")}`)
    loadData()
  } catch (e) {
    if (e && e.message) ElMessage.error(e.message)
  } finally {
    recycling.value = false
  }
}

// 公海池配置
const configVisible = ref(false)
const configFormRef = ref()
const configForm = reactive({
  configId: null,
  configName: '默认公海规则',
  noFollowDays: 90,
  noOrderDays: 180,
  warningDays: 7,
  isEnabled: 1,
  remark: ''
})
const configRules = {
  noFollowDays: [{ required: true, message: tt('请输入未跟进天数'), trigger: 'blur' }],
  noOrderDays: [{ required: true, message: tt('请输入未开单天数'), trigger: 'blur' }],
  warningDays: [{ required: true, message: tt('请输入预警天数'), trigger: 'blur' }]
}

async function loadActiveConfig() {
  try {
    const config = await getActivePoolConfig()
    if (config) {
      activeConfig.value = config
    }
  } catch (e) { /* 忽略 */ }
}

async function handleConfig() {
  try {
    const list = await listPoolConfig()
    const arr = Array.isArray(list) ? list : (list?.records || [])
    const active = arr.find(c => c.isEnabled === 1) || arr[0]
    if (active) {
      Object.assign(configForm, {
        configId: active.configId,
        configName: active.configName || '默认公海规则',
        noFollowDays: active.noFollowDays || 90,
        noOrderDays: active.noOrderDays || 180,
        warningDays: active.warningDays || 7,
        isEnabled: active.isEnabled != null ? active.isEnabled : 1,
        remark: active.remark || ''
      })
    } else {
      Object.assign(configForm, {
        configId: null, configName: '默认公海规则',
        noFollowDays: 90, noOrderDays: 180, warningDays: 7,
        isEnabled: 1, remark: ''
      })
    }
  } catch (e) { /* 忽略 */ }
  configVisible.value = true
}

async function saveConfig() {
  await configFormRef.value?.validate()
  saving.value = true
  try {
    await savePoolConfig({ ...configForm })
    ElMessage.success(tt('保存配置成功'))
    configVisible.value = false
    loadActiveConfig()
  } catch (e) {
    ElMessage.error(e.message || tt('保存失败'))
  } finally {
    saving.value = false
  }
}

// 保存并立即执行回收
async function handleAutoRecycleFromConfig() {
  await configFormRef.value?.validate()
  saving.value = true
  try {
    await savePoolConfig({ ...configForm })
    saving.value = false
    recycling.value = true
    const res = await triggerAutoRecycle()
    const count = res?.recycled || 0
    ElMessage.success(`${tt("配置已保存，自动回收完成，共回收 ")}${count}${tt(" 个客户")}`)
    configVisible.value = false
    loadActiveConfig()
    loadData()
  } catch (e) {
    if (e && e.message) ElMessage.error(e.message)
  } finally {
    saving.value = false
    recycling.value = false
  }
}

// 加载员工下拉
async function loadEmployeeOptions() {
  try {
    const list = await listAllEmployee()
    employeeOptions.value = (Array.isArray(list) ? list : (list?.records || []))
      .map(e => ({ value: e.employeeId, label: e.employeeName + (e.deptName ? ' (' + e.deptName + ')' : '') }))
  } catch (e) { console.warn('加载员工失败', e) }
}

// 新增公海客户
const addVisible = ref(false)
const addFormRef = ref()
const addForm = reactive({
  customerName: '', contactPerson: '', contactPhone: '',
  customerType: '企业客户', customerLevel: '', sourceType: '', industry: '',
  intentionLevel: 'C', crmStatus: '潜在客户',
  address: '', remark: ''
})
const addRules = {
  customerName: [{ required: true, message: tt('请输入客户名称'), trigger: 'blur' }],
  contactPerson: [{ required: true, message: tt('请输入联系人'), trigger: 'blur' }],
  contactPhone: [{ required: true, message: tt('请输入联系电话'), trigger: 'blur' }]
}

function handleAdd() {
  Object.assign(addForm, {
    customerName: '', contactPerson: '', contactPhone: '',
    customerType: '企业客户', customerLevel: '', sourceType: '', industry: '',
    intentionLevel: 'C', crmStatus: '潜在客户',
    address: '', remark: ''
  })
  addVisible.value = true
}

async function confirmAdd() {
  await addFormRef.value?.validate()
  saving.value = true
  try {
    await saveCustomer({
      ...addForm,
      poolType: 1,
      poolEnterDate: new Date().toISOString().slice(0, 19).replace('T', ' ')
    })
    ElMessage.success(tt('新增公海客户成功'))
    addVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error(e.message || tt('新增失败'))
  } finally {
    saving.value = false
  }
}

// 导入导出
async function handleExport() {
  try {
    await exportData('customer', 1)
    ElMessage.success(tt('导出成功'))
  } catch (e) {
    ElMessage.error(tt('导出失败'))
  }
}

async function handleDownloadTemplate() {
  try {
    await downloadTemplate('customer')
    ElMessage.success(tt('模板下载成功'))
  } catch (e) {
    ElMessage.error(tt('模板下载失败'))
  }
}

async function handleImport(file) {
  try {
    const result = await importData('customer', file, 1)
    ElMessage.success(`${tt("导入成功: ")}${result.successCount}${tt("条, 失败: ")}${result.failCount}${tt("条")}`)
    loadData()
  } catch (e) {
    ElMessage.error(tt('导入失败：') + (e.message || tt('未知错误')))
  }
  return false
}

onMounted(() => {
  loadData()
  loadEmployeeOptions()
  loadActiveConfig()
})
useAutoRefresh(() => {
  loadData()
  loadEmployeeOptions()
  loadActiveConfig()
})
</script>

<style scoped>
.erp-page { padding: 0; }
.dashboard-card { background: linear-gradient(135deg, #f5f7fa 0%, #ffffff 100%); }
.dashboard-row { display: flex; gap: 16px; flex-wrap: wrap; }
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
.config-info {
  margin-top: 12px; display: flex; align-items: center; gap: 12px;
}
.erp-search-bar { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.form-tip { font-size: 12px; color: #909399; line-height: 1.4; margin-top: 2px; }
</style>
