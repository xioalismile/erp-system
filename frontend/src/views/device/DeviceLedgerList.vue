<template>
  <div class="device-page">
    <div class="dev-toolbar">
      <div class="dev-actions">
        <el-button v-permission="perm('add')" type="primary" size="small" @click="openAdd">{{ $tt('新增设备') }}</el-button>
        <el-button v-permission="perm('refresh')" size="small" @click="loadDevices">{{ $tt('刷新') }}</el-button>
      </div>
      <div class="dev-search">
        <el-input v-model="keyword" :placeholder="$tt('设备编码 / 名称 / 品牌 / 型号 / 出厂编号')" size="small" clearable
                  style="width: 260px" @keyup.enter="reload">
          <template #suffix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filterStatus" :placeholder="$tt('设备状态')" size="small" clearable style="width: 110px" @change="reload">
          <el-option :label="$tf('启用')" value="启用" />
          <el-option :label="$tf('停用')" value="停用" />
          <el-option :label="$tf('报废')" value="报废" />
        </el-select>
        <el-select v-model="filterWorkCenterId" :placeholder="$tt('工作中心')" size="small" clearable filterable
                   style="width: 150px" @change="reload">
          <el-option v-for="wc in workCenters" :key="wc.workCenterId" :label="wc.workCenterName" :value="wc.workCenterId" />
        </el-select>
        <el-button v-permission="perm('query')" type="primary" size="small" @click="reload">{{ $tt('搜索') }}</el-button>
        <el-button v-permission="perm('reset')" size="small" @click="resetSearch">{{ $tt('重置') }}</el-button>
      </div>
    </div>

    <div class="dev-body">
      <aside class="dev-tree">
        <div class="tree-head">
          <span>{{ $tt('设备分类') }}</span>
          <el-button v-permission="perm('add')" link type="primary" size="small" @click="openCategoryAdd(null)">{{ $tt('新增分类') }}</el-button>
        </div>
        <div class="tree-all" :class="{ active: filterCategoryId === null }" @click="selectAllCategory">{{ $tt('全部设备') }}</div>
        <el-tree ref="treeRef" :data="categoryTree" node-key="id" :props="{ label: 'categoryName', children: 'children' }"
                 highlight-current :expand-on-click-node="false" default-expand-all @node-click="onCategoryClick">
          <template #default="{ data }">
            <span class="tree-node">
              <span class="tree-label">{{ data.categoryName }}<em v-if="data.deviceCount"> ({{ data.deviceCount }})</em></span>
              <span class="tree-ops">
                <el-button link size="small" @click.stop="openCategoryAdd(data)">+</el-button>
                <el-button link size="small" @click.stop="openCategoryEdit(data)">改</el-button>
                <el-button link type="danger" size="small" @click.stop="removeCategory(data)">删</el-button>
              </span>
            </span>
          </template>
        </el-tree>
      </aside>

      <section class="dev-main">
        <div v-if="!loading && tableData.length === 0" class="empty-guide">
          <div class="empty-title">{{ $tt('还没有设备') }}</div>
          <div class="empty-desc">
            {{ $tt('先「新增设备」建立台账，再到「接入管理」生成凭证并配置协议与点位，设备数据才能进入系统。') }}
          </div>
          <el-button type="primary" size="small" @click="openAdd">{{ $tt('新增第一台设备') }}</el-button>
        </div>

        <el-table v-show="tableData.length > 0" :data="tableData" border size="small" style="width:100%" v-loading="loading">
          <el-table-column type="index" label="#" width="48" align="center" />
          <el-table-column prop="deviceCode" :label="$tf('设备编码')" min-width="110" />
          <el-table-column prop="deviceName" :label="$tf('设备名称')" min-width="140" show-overflow-tooltip />
          <el-table-column prop="categoryName" :label="$tf('设备分类')" width="100" />
          <el-table-column :label="$tf('品牌 / 型号')" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ [row.brand, row.model].filter(Boolean).join(' / ') }}</template>
          </el-table-column>
          <el-table-column prop="workCenterName" :label="$tf('工作中心')" min-width="120" show-overflow-tooltip />
          <el-table-column prop="workstationName" :label="$tf('工位')" min-width="110" show-overflow-tooltip />
          <el-table-column prop="ownerEmployeeName" :label="$tf('责任人')" width="90" />
          <el-table-column :label="$tf('状态')" width="80" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.status === '启用'" type="success" size="small">{{ $tt('启用') }}</el-tag>
              <el-tag v-else-if="row.status === '报废'" type="danger" size="small">{{ $tt('报废') }}</el-tag>
              <el-tag v-else type="info" size="small">{{ $tt('停用') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$tf('操作')" width="230" align="center" fixed="right">
            <template #default="{ row }">
              <el-button v-permission="perm('qrcode')" type="primary" link size="small" @click="openQr(row)">{{ $tt('二维码') }}</el-button>
              <el-button v-permission="perm('edit')" type="primary" link size="small" @click="openEdit(row)">{{ $tt('编辑') }}</el-button>
              <el-button v-if="row.status === '启用'" v-permission="perm('disable')" type="warning" link size="small"
                         @click="changeStatus(row, '停用')">{{ $tt('停用') }}</el-button>
              <el-button v-else v-permission="perm('enable')" type="success" link size="small"
                         @click="changeStatus(row, '启用')">{{ $tt('启用') }}</el-button>
              <el-button v-permission="perm('delete')" type="danger" link size="small" @click="removeDevice(row)">{{ $tt('删除') }}</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="table-pagination">
          <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="total"
                         :page-sizes="[20, 50, 100]" layout="total, sizes, prev, pager, next, jumper"
                         small background @current-change="loadDevices" @size-change="loadDevices" />
        </div>
      </section>
    </div>

    <!-- 设备新增 / 编辑 -->
    <el-dialog v-model="dialogVisible" width="880px" :close-on-click-modal="false" top="5vh">
      <template #header>
        <div class="dlg-header">
          <span class="dlg-title">{{ isEdit ? $tt('编辑设备') : $tt('新增设备') }}</span>
          <span class="dlg-header-actions">
            <el-button size="small" @click="dialogVisible = false">{{ $tt('取消') }}</el-button>
            <el-button size="small" type="primary" :loading="saving" @click="onSave">{{ $tt('保存') }}</el-button>
          </span>
        </div>
      </template>

      <el-form label-width="96px" class="dlg-form">
        <div class="form-section">{{ $tt('基本信息') }}</div>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="$tf('设备编码')">
              <el-input v-model="form.deviceCode" :placeholder="isEdit ? $tt('可修改，租户内唯一') : $tt('留空自动生成，如 DEV0001')" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('设备名称')" required>
              <el-input v-model="form.deviceName" :placeholder="$tt('如：一号数控雕刻机')" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('设备分类')" required>
              <el-tree-select v-model="form.categoryId" :data="categoryTree" :props="{ label: 'categoryName', children: 'children' }"
                              node-key="id" check-strictly :render-after-expand="false" style="width:100%" :placeholder="$tt('请选择设备分类')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('启用状态')">
              <el-select v-model="form.status" style="width:100%">
                <el-option :label="$tf('启用')" value="启用" />
                <el-option :label="$tf('停用')" value="停用" />
                <el-option :label="$tf('报废')" value="报废" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('品牌')"><el-input v-model="form.brand" maxlength="100" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('型号')"><el-input v-model="form.model" maxlength="100" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('出厂编号')"><el-input v-model="form.serialNo" maxlength="100" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('资产编号')"><el-input v-model="form.assetNo" maxlength="100" /></el-form-item>
          </el-col>
        </el-row>

        <div class="form-section">{{ $tt('归属与能力') }}</div>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="$tf('工作中心')">
              <el-select v-model="form.workCenterId" clearable filterable style="width:100%" :placeholder="$tt('与工位至少填一项')"
                         @change="onWorkCenterChange">
                <el-option v-for="wc in workCenters" :key="wc.workCenterId" :label="wc.workCenterName" :value="wc.workCenterId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('工位')">
              <el-select v-model="form.workstationId" clearable filterable style="width:100%" :placeholder="$tt('跟随工作中心过滤')">
                <el-option v-for="ws in workstationOptions" :key="ws.workstationId" :label="ws.workstationName" :value="ws.workstationId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="$tf('可执行工序')">
              <el-select v-model="form.allowedOperationIds" multiple filterable collapse-tags collapse-tags-tooltip
                         style="width:100%" :placeholder="$tt('不选 = 该设备不参与自动关联，只能人工认领')">
                <el-option v-for="ws in workstations" :key="ws.workstationId" :label="ws.workstationName" :value="ws.workstationId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('责任人')">
              <el-select v-model="form.ownerEmployeeId" clearable filterable style="width:100%">
                <el-option v-for="e in employees" :key="e.employeeId" :label="e.employeeName" :value="e.employeeId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('安装位置')"><el-input v-model="form.location" maxlength="200" /></el-form-item>
          </el-col>
        </el-row>

        <div class="form-section">{{ $tt('运行与报工') }}</div>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="$tf('额定节拍')">
              <el-input-number v-model="form.ratedSpeed" :min="0" :precision="2" :step="1" controls-position="right" style="width:100%" />
              <div class="hint">{{ $tt('件/小时，用于计算性能稼动率与 OEE；不填则 OEE 显示「—」') }}</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('报工阈值')">
              <el-input-number v-model="form.reportThreshold" :min="1" :step="10" controls-position="right" style="width:100%" />
              <div class="hint">{{ $tt('设备累计多少件生成一张待确认报工单') }}</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('计数合并口径')">
              <el-select v-model="form.countMergeMode" style="width:100%">
                <el-option :label="$tf('按设备分别求和')" value="SUM" />
                <el-option :label="$tf('只认主设备')" value="PRIMARY_ONLY" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('关键设备')">
              <el-switch v-model="form.isKeyDevice" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('自动开工')">
              <el-switch v-model="form.autoStart" :active-value="1" :inactive-value="0" />
              <div class="hint">{{ $tt('设备运行信号持续达到设定时长后，自动把已派工工序置为已开工') }}</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('自动暂停')">
              <el-switch v-model="form.autoPause" :active-value="1" :inactive-value="0" />
              <div class="hint">{{ $tt('设备停机超时后是否自动暂停工序') }}</div>
            </el-form-item>
          </el-col>
        </el-row>

        <div class="form-section">{{ $tt('其他') }}</div>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="$tf('采购日期')">
              <el-date-picker v-model="form.purchaseDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('保修到期')">
              <el-date-picker v-model="form.warrantyEndDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="$tf('备注')">
              <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-dialog>

    <!-- 分类新增 / 编辑 -->
    <el-dialog v-model="categoryDialogVisible" width="460px" :close-on-click-modal="false">
      <template #header>
        <div class="dlg-header">
          <span class="dlg-title">{{ categoryEditId ? $tt('编辑分类') : $tt('新增分类') }}</span>
          <span class="dlg-header-actions">
            <el-button size="small" @click="categoryDialogVisible = false">{{ $tt('取消') }}</el-button>
            <el-button size="small" type="primary" :loading="categorySaving" @click="onSaveCategory">{{ $tt('保存') }}</el-button>
          </span>
        </div>
      </template>
      <el-form label-width="90px">
        <el-form-item :label="$tf('分类名称')" required>
          <el-input v-model="categoryForm.categoryName" maxlength="100" :placeholder="$tt('如：数控设备')" />
        </el-form-item>
        <el-form-item :label="$tf('分类编码')">
          <el-input v-model="categoryForm.categoryCode" maxlength="50" :placeholder="$tt('留空自动生成')" />
        </el-form-item>
        <el-form-item :label="$tf('上级分类')">
          <el-tree-select v-model="categoryForm.parentId" :data="categoryTree" :props="{ label: 'categoryName', children: 'children' }"
                          node-key="id" check-strictly clearable :render-after-expand="false" style="width:100%" :placeholder="$tt('不选 = 一级分类')" />
        </el-form-item>
        <el-form-item :label="$tf('排序')">
          <el-input-number v-model="categoryForm.sortOrder" :min="0" controls-position="right" style="width:100%" />
        </el-form-item>
        <el-form-item :label="$tf('状态')">
          <el-select v-model="categoryForm.status" style="width:100%">
            <el-option :label="$tf('启用')" value="启用" />
            <el-option :label="$tf('停用')" value="停用" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$tf('备注')">
          <el-input v-model="categoryForm.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
    </el-dialog>

    <!-- 设备二维码 -->
    <el-dialog v-model="qrVisible" width="320px" :title="$tt('设备二维码')">
      <div class="qr-box">
        <img v-if="qrData" :src="qrData" class="qr-img" :alt="$tt('设备二维码')" />
        <div class="qr-code">{{ qrDeviceCode }}</div>
        <div class="qr-tip">{{ $tt('扫码可快速定位设备；后续接入调试与现场点检也会用到。') }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { usePagePerm } from '@/utils/pagePerm'
import { tt } from '@/i18n'
import {
  listDevices, getDevice, createDevice, updateDevice, deleteDevice,
  enableDevice, disableDevice, getDeviceQrcode
} from '@/api/device/ledger'
import { getCategoryTree, createCategory, updateCategory, deleteCategory } from '@/api/device/category'
import { listAllWorkCenter } from '@/api/production/workCenter'
import { listAllWorkstation } from '@/api/production/workstation'
import { listAllEmployee } from '@/api/basedata/employee'

const perm = usePagePerm('device:ledger')

// ==================== 列表 ====================
const keyword = ref('')
const filterStatus = ref(null)
const filterWorkCenterId = ref(null)
const filterCategoryId = ref(null)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const tableData = ref([])
const treeRef = ref(null)

const categoryTree = ref([])
const workCenters = ref([])
const workstations = ref([])
const employees = ref([])

async function loadDevices() {
  loading.value = true
  try {
    const params = { current: page.value, size: pageSize.value }
    if (keyword.value) params.keyword = keyword.value
    if (filterStatus.value) params.status = filterStatus.value
    if (filterWorkCenterId.value) params.workCenterId = filterWorkCenterId.value
    if (filterCategoryId.value) params.categoryId = filterCategoryId.value
    const res = await listDevices(params)
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 1
  loadDevices()
}

function resetSearch() {
  keyword.value = ''
  filterStatus.value = null
  filterWorkCenterId.value = null
  filterCategoryId.value = null
  if (treeRef.value) treeRef.value.setCurrentKey(null)
  reload()
}

async function loadCategories() {
  try {
    categoryTree.value = await getCategoryTree() || []
  } catch (e) {
    categoryTree.value = []
  }
}

function onCategoryClick(data) {
  filterCategoryId.value = data.id
  reload()
}

function selectAllCategory() {
  filterCategoryId.value = null
  if (treeRef.value) treeRef.value.setCurrentKey(null)
  reload()
}

// ==================== 设备表单 ====================
const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const editId = ref(null)

function emptyForm() {
  return {
    deviceCode: '', deviceName: '', categoryId: null, brand: '', model: '', serialNo: '', assetNo: '',
    workCenterId: null, workstationId: null, allowedOperationIds: [], ownerEmployeeId: null,
    location: '', purchaseDate: '', warrantyEndDate: '', ratedSpeed: null, reportThreshold: 50,
    countMergeMode: 'SUM', autoStart: 0, autoPause: 0, isKeyDevice: 0, status: '启用', remark: ''
  }
}
const form = reactive(emptyForm())

const workstationOptions = computed(() => {
  if (!form.workCenterId) return workstations.value
  // 兼容历史工位未绑定工作中心（workCenterId 为空）的情况，否则选了工作中心后工位全被过滤掉
  return workstations.value.filter(w => !w.workCenterId || String(w.workCenterId) === String(form.workCenterId))
})

function onWorkCenterChange() {
  if (form.workstationId && !workstationOptions.value.some(w => String(w.workstationId) === String(form.workstationId))) {
    form.workstationId = null
  }
}

function openAdd() {
  isEdit.value = false
  editId.value = null
  Object.assign(form, emptyForm())
  if (filterCategoryId.value) form.categoryId = filterCategoryId.value
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  editId.value = row.id
  const ids = (row.allowedOperationIds || '').split(',').map(s => s.trim()).filter(Boolean).map(Number).filter(n => !Number.isNaN(n))
  Object.assign(form, {
    deviceCode: row.deviceCode || '',
    deviceName: row.deviceName || '',
    categoryId: row.categoryId,
    brand: row.brand || '',
    model: row.model || '',
    serialNo: row.serialNo || '',
    assetNo: row.assetNo || '',
    workCenterId: row.workCenterId,
    workstationId: row.workstationId,
    allowedOperationIds: ids,
    ownerEmployeeId: row.ownerEmployeeId,
    location: row.location || '',
    purchaseDate: row.purchaseDate || '',
    warrantyEndDate: row.warrantyEndDate || '',
    ratedSpeed: row.ratedSpeed,
    reportThreshold: row.reportThreshold || 50,
    countMergeMode: row.countMergeMode || 'SUM',
    autoStart: row.autoStart ?? 0,
    autoPause: row.autoPause ?? 0,
    isKeyDevice: row.isKeyDevice ?? 0,
    status: row.status || '启用',
    remark: row.remark || ''
  })
  dialogVisible.value = true
}

async function onSave() {
  if (!form.deviceName || !form.deviceName.trim()) { ElMessage.warning(tt('请填写设备名称')); return }
  if (!form.categoryId) { ElMessage.warning(tt('请选择设备分类')); return }
  if (!form.workCenterId && !form.workstationId) { ElMessage.warning(tt('请至少选择工作中心或工位')); return }
  saving.value = true
  try {
    const payload = {
      ...form,
      allowedOperationIds: (form.allowedOperationIds || []).join(',')
    }
    if (isEdit.value) {
      await updateDevice(editId.value, payload)
    } else {
      await createDevice(payload)
    }
    ElMessage.success(tt('保存成功'))
    dialogVisible.value = false
    await Promise.all([loadDevices(), loadCategories()])
  } catch (e) { /* 后端已给出中文原因 */ } finally {
    saving.value = false
  }
}

async function changeStatus(row, status) {
  const label = status === '启用' ? tt('启用') : status === '停用' ? tt('停用') : tt('报废')
  try {
    await ElMessageBox.confirm(tt(`确定把设备「${row.deviceName}」置为${label}？`), tt('提示'), { type: 'warning' })
  } catch (e) { return }
  try {
    if (status === '启用') await enableDevice(row.id)
    else if (status === '停用') await disableDevice(row.id)
    else return
    ElMessage.success(tt('操作成功'))
    loadDevices()
  } catch (e) { /* 后端已给出原因 */ }
}

async function removeDevice(row) {
  try {
    await ElMessageBox.confirm(
      tt(`确定删除设备「${row.deviceName}」？已有接入凭证、设备任务或维保记录的设备不能删除。`),
      tt('提示'), { type: 'warning' })
  } catch (e) { return }
  try {
    await deleteDevice(row.id)
    ElMessage.success(tt('删除成功'))
    await Promise.all([loadDevices(), loadCategories()])
  } catch (e) { /* 后端已给出原因 */ }
}

// ==================== 二维码 ====================
const qrVisible = ref(false)
const qrData = ref('')
const qrDeviceCode = ref('')

async function openQr(row) {
  try {
    qrData.value = await getDeviceQrcode(row.id)
    qrDeviceCode.value = row.deviceCode
    qrVisible.value = true
  } catch (e) {
    ElMessage.error(tt('二维码生成失败'))
  }
}

// ==================== 分类 ====================
const categoryDialogVisible = ref(false)
const categorySaving = ref(false)
const categoryEditId = ref(null)
const categoryForm = reactive({ categoryName: '', categoryCode: '', parentId: null, sortOrder: 0, status: '启用', remark: '' })

function openCategoryAdd(parent) {
  categoryEditId.value = null
  Object.assign(categoryForm, {
    categoryName: '', categoryCode: '', parentId: parent ? parent.id : null,
    sortOrder: 0, status: '启用', remark: ''
  })
  categoryDialogVisible.value = true
}

function openCategoryEdit(data) {
  categoryEditId.value = data.id
  Object.assign(categoryForm, {
    categoryName: data.categoryName,
    categoryCode: data.categoryCode || '',
    parentId: data.parentId ?? null,
    sortOrder: data.sortOrder ?? 0,
    status: data.status || '启用',
    remark: data.remark || ''
  })
  categoryDialogVisible.value = true
}

async function onSaveCategory() {
  if (!categoryForm.categoryName || !categoryForm.categoryName.trim()) {
    ElMessage.warning(tt('请填写分类名称'))
    return
  }
  categorySaving.value = true
  try {
    if (categoryEditId.value) await updateCategory(categoryEditId.value, { ...categoryForm })
    else await createCategory({ ...categoryForm })
    ElMessage.success(tt('保存成功'))
    categoryDialogVisible.value = false
    await loadCategories()
  } catch (e) { /* 后端已给出原因 */ } finally {
    categorySaving.value = false
  }
}

async function removeCategory(data) {
  try {
    await ElMessageBox.confirm(tt(`确定删除分类「${data.categoryName}」？系统预置分类、有子分类或有设备的分类不能删除。`),
      tt('提示'), { type: 'warning' })
  } catch (e) { return }
  try {
    await deleteCategory(data.id)
    ElMessage.success(tt('删除成功'))
    if (String(filterCategoryId.value) === String(data.id)) {
      filterCategoryId.value = null
      reload()
    }
    await loadCategories()
  } catch (e) { /* 后端已给出原因 */ }
}

// ==================== 初始化 ====================
onMounted(async () => {
  loadDevices()
  loadCategories()
  try { workCenters.value = await listAllWorkCenter() || [] } catch (e) { workCenters.value = [] }
  try { workstations.value = await listAllWorkstation() || [] } catch (e) { workstations.value = [] }
  try { employees.value = await listAllEmployee() || [] } catch (e) { employees.value = [] }
})

// 防止未使用告警：getDevice 预留给详情页
void getDevice
</script>

<style scoped lang="scss">
.device-page {
  background: #fff;
  min-height: 100%;
  padding: 10px 12px 16px;
  font-family: Tahoma, 'Microsoft YaHei', sans-serif;
  box-sizing: border-box;
}
.dev-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 0 10px;
  border-bottom: 1px solid #eef1f5;
  flex-wrap: wrap;
}
.dev-actions,
.dev-search {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.dev-body {
  display: flex;
  gap: 12px;
  padding-top: 10px;
  align-items: flex-start;
}
.dev-tree {
  width: 220px;
  flex: 0 0 220px;
  border: 1px solid #eef1f5;
  border-radius: 6px;
  padding: 8px;
  max-height: calc(100vh - 190px);
  overflow: auto;
}
.tree-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  padding: 2px 4px 8px;
  border-bottom: 1px solid #f1f4f9;
  margin-bottom: 6px;
}
.tree-all {
  font-size: 13px;
  padding: 5px 8px;
  border-radius: 4px;
  cursor: pointer;
  color: #475569;
  margin-bottom: 4px;
}
.tree-all.active {
  background: #eaf1ff;
  color: #1f6feb;
}
.tree-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  font-size: 13px;
  padding-right: 4px;
}
.tree-label em {
  font-style: normal;
  color: #94a3b8;
  font-size: 12px;
}
.tree-ops {
  display: none;
  gap: 2px;
}
.tree-node:hover .tree-ops {
  display: inline-flex;
}
.dev-main {
  flex: 1;
  min-width: 0;
}
.empty-guide {
  border: 1px dashed #d7dee9;
  border-radius: 8px;
  padding: 28px 20px;
  text-align: center;
  color: #64748b;
  background: #fbfcfe;
}
.empty-title {
  font-size: 15px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 6px;
}
.empty-desc {
  font-size: 13px;
  margin-bottom: 14px;
  line-height: 1.7;
}
.table-pagination {
  display: flex;
  justify-content: flex-end;
  padding: 10px 0 0;
}
.dlg-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.dlg-title {
  font-size: 15px;
  font-weight: 600;
}
.dlg-header-actions {
  display: inline-flex;
  gap: 8px;
}
.form-section {
  font-size: 13px;
  font-weight: 600;
  color: #1f6feb;
  padding: 10px 0 6px;
  border-bottom: 1px solid #eef1f5;
  margin-bottom: 10px;
}
.hint {
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.6;
  margin-top: 2px;
}
.qr-box {
  text-align: center;
  padding: 6px 0 4px;
}
.qr-img {
  width: 180px;
  height: 180px;
}
.qr-code {
  font-size: 14px;
  font-weight: 600;
  margin-top: 6px;
}
.qr-tip {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 6px;
  line-height: 1.6;
}
</style>
