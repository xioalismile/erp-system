<template>
  <!-- 通用列表壳（页头/统计卡/搜索条/表格外壳/分页/打印/列设置）+ 声明式筛选维度
       原「状态页签」整块删除 —— 状态改用通用搜索栏的「单据状态」下拉（filters 声明 'status' 时壳自动隐藏页签）
       该页真实维度：状态 / 委外供应商(supplier_id) / 负责人(manager_id)；本页无日期列，不声明 date 维度 -->
  <BillListPage
    page-class="work-center-page"
    back-path="/production"
    :title="$tt('工作中心')"
    badge="WORK CENTER"
    sub-title="管理生产工作中心 · 配置产能与费率"
    :stats="stats"
    :active-stat-key="activeStatKey"
    search-placeholder="输入工作中心编码/名称搜索"
    keyword-label="中心编码"
    :filters="['status', 'supplier', 'employee']"
    :status-options="statusOptions"
    :filter-model="search"
    employee-label="负责人"
    :page-sizes="[10, 20, 50, 100]"
    :table-data="tableData"
    :loading="loading"
    :pagination="pagination"
    storage-key="WorkCenterList"
    :columns="columns"
    :table-key="configVersion"
    v-model:keyword="search.keyword"
    v-model:selected-rows="selectedRows"
    v-model:column-setting-visible="columnSettingVisible"
    v-model:print-visible="printVisible"
    v-model:print-bill-id="printBillId"
    @search="handleSearch"
    @reset="handleReset"
    @filter-change="onFilterChange"
    @stat-click="onStatClick"
    @sort-change="onSortChange"
    @page-change="loadData"
  >
    <template #header-actions>
      <el-button type="primary" class="btn-primary-glow" v-permission="perm('add')" @click="onAdd">
        <el-icon><Plus /></el-icon>
        新增工作中心
      </el-button>
      <el-button type="success" :icon="Download" v-permission="perm('export')" @click="handleExport">{{ $tt("导出") }}</el-button>
    </template>

    <el-table-column type="index" label="#" width="60" />
    <!-- 动态列渲染：列顺序由列设置拖拽配置驱动（sortedColumns），单元格按 col.type 分支渲染 -->
    <template v-for="col in visibleSortedColumns" :key="col.prop">
      <el-table-column
        :prop="col.prop"
        :label="$tf(col.label)"
        :width="col.width"
        :min-width="col.minWidth"
        :align="col.align || 'left'"
        :sortable="col.sortable ? 'custom' : false"
        :show-overflow-tooltip="!!col.tooltip"
        :fixed="getColFixed(col.prop, col.fixed)"
      >
        <template v-if="col.type" #default="{ row }">
          <div v-if="col.type === 'code'" class="order-no-cell">
            <span class="order-no-text" @click="onEdit(row)">{{ row.workCenterCode }}</span>
            <el-icon class="link-icon"><Link /></el-icon>
          </div>
          <span v-else-if="col.type === 'amount2'" class="amount-text">{{ Number(row[col.prop] || 0).toFixed(2) }}</span>
          <template v-else-if="col.type === 'outsourced'">
            <el-tag v-if="row.isOutsourced" type="warning" size="small">{{ $tt("是") }}</el-tag>
            <span v-else>{{ $tt("否") }}</span>
          </template>
          <div v-else-if="col.type === 'status'" class="status-cell">
            <span :class="['status-dot', statusDotClass(row.status)]"></span>
            <el-tag size="small" :type="statusType(row.status)" effect="light" class="status-tag">{{ $dt(row.status) }}</el-tag>
          </div>
        </template>
      </el-table-column>
    </template>
    <AuditColumns />
  </BillListPage>

  <!-- 新增/编辑弹窗（780px 宽，留在壳外） -->
  <el-dialog :title="isEdit ? '编辑工作中心' : '新增工作中心'" v-model="dialogVisible" width="780px" @closed="resetForm">
    <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item :label="$tf('工作中心编码')" prop="workCenterCode">
            <el-input v-model="form.workCenterCode" :placeholder="$tt('如 WC001')" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item :label="$tf('工作中心名称')" prop="workCenterName">
            <el-input v-model="form.workCenterName" :placeholder="$tt('如 一车间装配中心')" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item :label="$tf('类型')" prop="workCenterType">
            <el-select v-model="form.workCenterType" :placeholder="$tt('请选择')" style="width: 100%">
              <el-option :label="$tf('设备')" value="设备" />
              <el-option :label="$tf('设备组')" value="设备组" />
              <el-option :label="$tf('生产线')" value="生产线" />
              <el-option :label="$tf('车间')" value="车间" />
              <el-option :label="$tf('工作单元')" value="工作单元" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item :label="$tf('部门')" prop="departmentId">
            <el-select v-model="form.departmentId" :placeholder="$tt('请选择')" filterable clearable style="width: 100%">
              <el-option v-for="opt in departmentOptions" :key="opt.value" :label="$tf(opt.label)" :value="opt.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item :label="$tf('负责人')" prop="managerId">
            <el-select v-model="form.managerId" :placeholder="$tt('请选择')" filterable clearable style="width: 100%">
              <el-option v-for="opt in employeeOptions" :key="opt.value" :label="$tf(opt.label)" :value="opt.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item :label="$tf('位置')" prop="location">
            <el-input v-model="form.location" :placeholder="$tt('如 A厂区1号楼')" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item :label="$tf('产能类型')" prop="capacityType">
            <el-select v-model="form.capacityType" :placeholder="$tt('请选择')" style="width: 100%">
              <el-option :label="$tf('人工工时')" value="人工工时" />
              <el-option :label="$tf('机器工时')" value="机器工时" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item :label="$tf('产能单位')" prop="capacityUnit">
            <el-select v-model="form.capacityUnit" :placeholder="$tt('请选择')" style="width: 100%">
              <el-option :label="$tf('小时')" value="小时" />
              <el-option :label="$tf('件数')" value="件数" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item :label="$tf('日产能')" prop="capacityPerDay">
            <el-input-number v-model="form.capacityPerDay" :min="0" :precision="4" controls-position="right" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item :label="$tf('效率(%)')" prop="efficiency">
            <el-input-number v-model="form.efficiency" :min="0" :precision="4" controls-position="right" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item :label="$tf('人工费率')" prop="laborRate">
            <el-input-number v-model="form.laborRate" :min="0" :precision="4" controls-position="right" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item :label="$tf('制造费用率')" prop="overheadRate">
            <el-input-number v-model="form.overheadRate" :min="0" :precision="4" controls-position="right" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item :label="$tf('最大人数')" prop="maxWorkers">
            <el-input-number v-model="form.maxWorkers" :min="0" controls-position="right" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item :label="$tf('是否委外')" prop="isOutsourced">
            <el-switch v-model="form.isOutsourced" :active-value="1" :inactive-value="0" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item v-if="form.isOutsourced === 1" :label="$tf('委外供应商')" prop="supplierId">
            <el-select v-model="form.supplierId" filterable :placeholder="$tt('选择供应商')" style="width: 100%">
              <el-option v-for="s in supplierOptions" :key="s.supplierId" :label="s.supplierName" :value="s.supplierId" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item :label="$tf('状态')" prop="status">
            <el-select v-model="form.status" :placeholder="$tt('请选择')" style="width: 100%">
              <el-option :label="$tf('启用')" value="启用" />
              <el-option :label="$tf('停用')" value="停用" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item :label="$tf('备注')" prop="remark">
        <el-input v-model="form.remark" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button v-permission="perm('dlgCancel')" @click="dialogVisible = false">{{ $tt("取消") }}</el-button>
      <el-button type="primary" v-permission="perm('save')" @click="onSubmit">{{ $tt("保存") }}</el-button>
    </template>
  </el-dialog>
  <!-- 打印预览/列设置抽屉已由 BillListPage 壳统一渲染（bill-type / storage-key 传入） -->
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Download, Link } from '@element-plus/icons-vue'
import {
  listWorkCenter, getWorkCenter, saveWorkCenter, updateWorkCenter, deleteWorkCenter, getWorkCenterDashboard
} from '@/api/production/workCenter'
import { listAllSupplier } from '@/api/basedata/supplier'
import { listAllDepartment } from '@/api/basedata/department'
import { listAllEmployee } from '@/api/basedata/employee'
import BillListPage from '@/components/bill/BillListPage.vue'
import AuditColumns from '@/components/AuditColumns.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { exportListAsSeen } from '@/utils/exportUtil'

import { useTableSort } from '@/composables/useTableSort'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'
const { handleSortChange, getSortParams } = useTableSort()
const perm = usePagePerm('production:routings')


const onSortChange = (e) => { handleSortChange(e); loadData() }
// 列定义（用于列设置抽屉+动态渲染）= 表格全部数据列；type 标记单元格渲染分支，纯文本列不加 type
const columns = [
  { prop: 'workCenterCode', label: '工作中心编码', minWidth: 130, sortable: true, type: 'code' },
  { prop: 'workCenterName', label: '工作中心名称', minWidth: 160, sortable: true },
  { prop: 'workCenterType', label: '类型', width: 100, sortable: true },
  { prop: 'capacityType', label: '产能类型', width: 100, sortable: true },
  { prop: 'capacityPerDay', label: '日产能', width: 100, align: 'right', sortable: true },
  { prop: 'capacityUnit', label: '产能单位', width: 90, sortable: true },
  { prop: 'laborRate', label: '人工费率', width: 100, align: 'right', sortable: true, type: 'amount2' },
  { prop: 'overheadRate', label: '制造费用率', width: 110, align: 'right', sortable: true, type: 'amount2' },
  { prop: 'maxWorkers', label: '最大人数', width: 90, align: 'right', sortable: true },
  { prop: 'isOutsourced', label: '委外', width: 80, align: 'center', sortable: true, type: 'outsourced' },
  { prop: 'efficiency', label: '效率(%)', width: 90, align: 'right', sortable: true },
  { prop: 'status', label: '状态', width: 100, sortable: true, type: 'status' }
]
const { columnSettingVisible, isColVisible, getColFixed, configVersion, sortedColumns } = useColumnSetting('WorkCenterList', columns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))

const loading = ref(false)
const tableData = ref([])
const selectedRows = ref([])
const printVisible = ref(false)
const printBillId = ref(null)
// 统一入参名（docs/knowledge-base.md 6.1）：keyword / status / supplierId（委外供应商）/ employeeId（负责人）
// 注：本页无日期列，不声明 startDate/endDate 维度
const search = reactive({ keyword: '', status: '', supplierId: null, employeeId: null })
const pagination = reactive({ current: 1, size: 20, total: 0 })
// 通用搜索栏「单据状态」下拉选项：与后端真实取值一致（启用 / 停用）
const statusOptions = [
  { label: '启用', value: '启用' },
  { label: '停用', value: '停用' }
]
// 统计卡片高亮（点卡片筛选列表时由 onStatClick 控制）
const activeStatKey = ref('')

// 顶部统计卡片：数据来自后端 /api/production/work-center/dashboard（基础资料类，与列表一致不做数据权限过滤）
// 原来「启用中 / 已停用 / 委外类型」是写死的 0，现改为真实计数
// 注：「委外类型」卡在搜索条里没有对应筛选维度（供应商维度 ≠ 委外标记），故 filter = null（不可点击）
const stats = ref([
  { key: 'total', label: '工作中心总数', value: 0, type: 'primary', icon: 'OfficeBuilding', filter: { status: '' } },
  { key: 'enabled', label: '启用中', value: 0, type: 'success', icon: 'CircleCheck', filter: { status: '启用' } },
  { key: 'disabled', label: '已停用', value: 0, type: 'warning', icon: 'Warning', filter: { status: '停用' } },
  { key: 'outsourced', label: '委外类型', value: 0, type: 'info', icon: 'EditPen', filter: null }
])

async function loadStats() {
  try {
    const d = (await getWorkCenterDashboard()) || {}
    stats.value = [
      { key: 'total', label: '工作中心总数', value: d.totalCount ?? 0, type: 'primary', icon: 'OfficeBuilding', filter: { status: '' } },
      { key: 'enabled', label: '启用中', value: d.enabledCount ?? 0, type: 'success', icon: 'CircleCheck', filter: { status: '启用' } },
      { key: 'disabled', label: '已停用', value: d.disabledCount ?? 0, type: 'warning', icon: 'Warning', filter: { status: '停用' } },
      { key: 'outsourced', label: '委外类型', value: d.outsourcedCount ?? 0, type: 'info', icon: 'EditPen', filter: null }
    ]
  } catch (e) {
    // 静默降级：取数失败时卡片保持原值，不弹诊断卡、不阻断列表
  }
}

// 卡片点击 → 应用该卡筛选条件（状态先清空再套用，避免上一张卡残留）；再点同一张卡 = 取消
function onStatClick({ key, filter }) {
  if (activeStatKey.value === key) {
    activeStatKey.value = ''
    Object.assign(search, { status: '' })
  } else {
    activeStatKey.value = key
    Object.assign(search, { status: '' }, filter || {})
  }
  pagination.current = 1
  loadData()
}

const headerCellStyle = {
  background: 'rgba(99, 102, 241, 0.04)',
  color: '#1f2937',
  fontWeight: 600,
  borderBottom: '2px solid #e0e7ff'
}

function statusType(s) {
  return s === '启用' ? 'success' : 'info'
}

function statusDotClass(s) {
  return s === '启用' ? 'dot-success' : 'dot-info'
}

// 通用搜索栏（状态/委外供应商/负责人）变更：先把值写回本页 search
// 注：BillSearchBar 变更后会紧接着抛 search 事件（壳已转发 → handleSearch），这里不重复发起查询
function onFilterChange(patch) {
  Object.assign(search, patch || {})
}

const supplierOptions = ref([])
const departmentOptions = ref([])
const employeeOptions = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const form = reactive({
  workCenterId: null,
  workCenterCode: '',
  workCenterName: '',
  workCenterType: '生产线',
  departmentId: null,
  managerId: null,
  location: '',
  capacityType: '人工工时',
  capacityUnit: '小时',
  capacityPerDay: 8,
  efficiency: 100,
  laborRate: 0,
  overheadRate: 0,
  maxWorkers: 0,
  isOutsourced: 0,
  supplierId: null,
  status: '启用',
  remark: ''
})

const rules = {
  workCenterCode: [{ required: true, message: tt('请输入工作中心编码'), trigger: 'blur' }],
  workCenterName: [{ required: true, message: tt('请输入工作中心名称'), trigger: 'blur' }],
  workCenterType: [{ required: true, message: tt('请选择类型'), trigger: 'change' }],
  capacityType: [{ required: true, message: tt('请选择产能类型'), trigger: 'change' }],
  capacityUnit: [{ required: true, message: tt('请选择产能单位'), trigger: 'change' }]
}

/**
 * 列表与导出**共用**的查询条件（2026-09-24 全站口径）：
 * 导出必须与列表用同一套条件，抽成一个函数避免两处各写一份导致口径漂移
 * （用户要求"页面上看到什么列，导出就带什么列；导出行 = 当前筛选条件下的全部"）。
 */
function buildQueryParams() {
  return {
    keyword: search.keyword, status: search.status,
    supplierId: search.supplierId, employeeId: search.employeeId,
    ...getSortParams()
  }
}

async function loadData() {
  loading.value = true
  try {
    const res = await listWorkCenter({ current: pagination.current, size: pagination.size, ...buildQueryParams() })
    tableData.value = res.records
    pagination.total = res.total
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    supplierOptions.value = (await listAllSupplier()) || []
  } catch (e) { /* 忽略选项加载失败 */ }
  try {
    const depList = await listAllDepartment()
    departmentOptions.value = (Array.isArray(depList) ? depList : (depList?.records || [])).map(d => ({ value: d.deptId, label: d.deptName }))
  } catch (e) { console.warn('加载部门失败', e) }
  try {
    const empList = await listAllEmployee()
    employeeOptions.value = (Array.isArray(empList) ? empList : (empList?.records || [])).map(e => ({ value: e.employeeId, label: e.employeeName }))
  } catch (e) { console.warn('加载员工失败', e) }
}

function handleSearch() {
  pagination.current = 1
  loadData()
}

function handleReset() {
  // 重置 = 回到未筛选状态：关键词 + 通用搜索栏全部维度一并清空
  Object.assign(search, { keyword: '', status: '', supplierId: null, employeeId: null })
  activeStatKey.value = ''   // 同时取消统计卡片的高亮
  pagination.current = 1
  loadData()
}

function resetForm() {
  Object.assign(form, {
    workCenterId: null,
    workCenterCode: '',
    workCenterName: '',
    workCenterType: '生产线',
    departmentId: null,
    managerId: null,
    location: '',
    capacityType: '人工工时',
    capacityUnit: '小时',
    capacityPerDay: 8,
    efficiency: 100,
    laborRate: 0,
    overheadRate: 0,
    maxWorkers: 0,
    isOutsourced: 0,
    supplierId: null,
    status: '启用',
    remark: ''
  })
}

function onAdd() {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

function onEdit(row) {
  isEdit.value = true
  resetForm()
  Object.assign(form, row)
  if (form.isOutsourced == null) form.isOutsourced = 0
  dialogVisible.value = true
}

async function onSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (isEdit.value) {
      await updateWorkCenter(form.workCenterId, form)
      ElMessage.success(tt('编辑成功'))
    } else {
      await saveWorkCenter(form)
      ElMessage.success(tt('新增成功'))
    }
    dialogVisible.value = false
    loadData()
  })
}

async function onToggleStatus(row) {
  const target = row.status === '启用' ? '停用' : '启用'
  await ElMessageBox.confirm(`${tt("确认")}${target}${tt("工作中心 [")}${row.workCenterCode}${tt("] 吗？")}`, tt('提示'), { type: 'warning' })
  try {
    await updateWorkCenter(row.workCenterId, { ...row, status: target })
    ElMessage.success(`${target}${tt("成功")}`)
    loadData()
  } catch (e) { /* 拦截器已提示 */ }
}

async function onDelete(row) {
  await ElMessageBox.confirm(`${tt("确定删除工作中心 [")}${row.workCenterCode}${tt("] 吗？")}`, tt('提示'), {
    type: 'warning'
  })
  await deleteWorkCenter(row.workCenterId)
  ElMessage.success(tt('删除成功'))
  loadData()
}

async function handleExport() {
  try {
    // 页面上看到什么列，导出就带什么列；导出行 = 当前筛选条件下的全部
    await exportListAsSeen({
      entityName: 'workCenter',
      columns: visibleSortedColumns.value,
      fetchPage: listWorkCenter,
      params: buildQueryParams(),
      idField: 'workCenterId'
    })
    ElMessage.success(tt('导出成功'))
  } catch (e) {
    // 失败原因由 exportUtil 统一提示
  }
}

onMounted(() => {
  loadStats()
  loadData()
  loadOptions()
})
useAutoRefresh(() => { loadData(); loadStats() })
</script>

<style scoped lang="scss">
.work-center-page {
  padding: 20px 24px;
  background: linear-gradient(135deg, #f8faff 0%, #f0f4ff 50%, #f8fcff 100%);
  min-height: calc(100vh - 60px);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;

  .header-left {
    display: flex;
    align-items: center;
    gap: 16px;
  }

  .back-btn {
    display: flex;
    align-items: center;
    gap: 4px;
    color: #6b7280;
    font-size: 13px;
    cursor: pointer;
    padding: 6px 12px;
    border-radius: 6px;
    background: #fff;
    border: 1px solid #e5e7eb;
    transition: all .2s;
    &:hover {
      color: #6366f1;
      border-color: #c7d2fe;
    }
  }

  .title-group { display: flex; flex-direction: column; gap: 2px; }

  .page-title {
    font-size: 20px;
    font-weight: 700;
    color: #111827;
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .title-badge {
    font-size: 10px;
    font-weight: 600;
    color: #6366f1;
    background: linear-gradient(135deg, #e0e7ff, #f0f4ff);
    padding: 2px 8px;
    border-radius: 4px;
    letter-spacing: 1px;
  }

  .page-sub { font-size: 12px; color: #9ca3af; }

  .header-actions { display: flex; gap: 8px; }
}

.btn-primary-glow {
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  border: none;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
  &:hover {
    box-shadow: 0 6px 20px rgba(99, 102, 241, 0.4);
    transform: translateY(-1px);
  }
}

.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  position: relative;
  overflow: hidden;
  transition: all .3s;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 24px rgba(99, 102, 241, 0.1);
    border-color: #c7d2fe;
  }

  .stat-icon {
    width: 48px;
    height: 48px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 24px;
    color: #fff;
    position: relative;
    z-index: 1;

    &.primary { background: linear-gradient(135deg, #6366f1, #8b5cf6); }
    &.warning { background: linear-gradient(135deg, #f59e0b, #f97316); }
    &.info { background: linear-gradient(135deg, #3b82f6, #60a5fa); }
    &.success { background: linear-gradient(135deg, #10b981, #34d399); }
    &.danger { background: linear-gradient(135deg, #ef4444, #f87171); }
  }

  .stat-info { flex: 1; }
  .stat-value {
    font-size: 24px;
    font-weight: 700;
    color: #111827;
    line-height: 1.2;
  }
  .stat-label {
    font-size: 12px;
    color: #9ca3af;
    margin-top: 4px;
  }

  .stat-glow {
    position: absolute;
    right: -20px;
    top: -20px;
    width: 100px;
    height: 100px;
    border-radius: 50%;
    opacity: 0.1;
    filter: blur(20px);

    &.primary { background: #6366f1; }
    &.warning { background: #f59e0b; }
    &.info { background: #3b82f6; }
    &.success { background: #10b981; }
    &.danger { background: #ef4444; }
  }
}

.content-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.search-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #f3f4f6;

  .search-right {
    display: flex;
    gap: 8px;
    align-items: center;
  }

  .search-input {
    width: 280px;
    :deep(.el-input__wrapper) {
      border-radius: 8px;
      box-shadow: 0 0 0 1px #e5e7eb inset;
      &:hover { box-shadow: 0 0 0 1px #c7d2fe inset; }
      &.is-focus { box-shadow: 0 0 0 1px #6366f1 inset; }
    }
  }
}

/* 状态页签样式已删除：状态改为通用搜索栏的「单据状态」下拉（filters 声明 'status'，壳自动隐藏页签） */

.tech-table {
  :deep(.el-table__header th) {
    background: rgba(99, 102, 241, 0.04) !important;
  }

  :deep(.el-table__row:hover > td) {
    background: rgba(99, 102, 241, 0.03) !important;
  }

  :deep(.el-table td) {
    border-bottom: 1px solid #f3f4f6;
  }
}

.order-no-cell {
  display: flex;
  align-items: center;
  gap: 6px;

  .order-no-text {
    color: #6366f1;
    font-weight: 500;
  }

  .link-icon {
    color: #c7d2fe;
    font-size: 12px;
    opacity: 0;
    transition: opacity .2s;
  }
  &:hover .link-icon { opacity: 1; }
}

.status-cell {
  display: flex;
  align-items: center;
  gap: 6px;

  .status-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;

    &.dot-info { background: #909399; }
    &.dot-warning { background: #e6a23c; }
    &.dot-primary { background: #409eff; }
    &.dot-success { background: #67c23a; }
    &.dot-danger { background: #f56c6c; }
  }

  .status-tag {
    border: none;
    --el-tag-bg-color: transparent;
  }
}

.amount-text {
  font-weight: 600;
  color: #1f2937;
  font-family: 'SF Mono', Monaco, 'Courier New', monospace;
}

.action-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

.pagination-wrap {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-top: 1px solid #f3f4f6;

  .pagination-info {
    font-size: 13px;
    color: #9ca3af;
  }
}
</style>
