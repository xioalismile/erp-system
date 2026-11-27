<template>
  <div class="device-page">
    <div class="dev-toolbar">
      <div class="dev-actions">
        <el-select v-model="filterDeviceId" :placeholder="$tt('设备')" size="small" clearable filterable style="width:180px" @change="reload">
          <el-option v-for="d in devices" :key="d.id" :label="`${d.deviceCode} · ${d.deviceName}`" :value="d.id" />
        </el-select>
        <el-select v-model="filterStatus" :placeholder="$tt('任务状态')" size="small" clearable style="width:120px" @change="reload">
          <el-option v-for="s in statuses" :key="s" :label="s" :value="s" />
        </el-select>
        <el-input v-model="filterOrderNo" :placeholder="$tt('工单号')" size="small" clearable style="width:160px" @keyup.enter="reload" />
        <el-button type="primary" size="small" @click="reload">{{ $tt('查询') }}</el-button>
        <el-button size="small" @click="reset">{{ $tt('重置') }}</el-button>
      </div>
      <div class="dev-actions">
        <el-button v-permission="perm('add')" type="primary" size="small" @click="openCreate">{{ $tt('新增关联') }}</el-button>
      </div>
    </div>

    <div class="dev-body">
      <el-table :data="rows" border size="small" v-loading="loading" style="width:100%">
        <el-table-column prop="taskNo" :label="$tf('任务号')" width="150" />
        <el-table-column :label="$tf('设备')" min-width="150">
          <template #default="{ row }">{{ row.deviceCode }} · {{ row.deviceName }}</template>
        </el-table-column>
        <el-table-column prop="orderNo" :label="$tf('工单号')" min-width="130" />
        <el-table-column :label="$tf('工序')" min-width="130">
          <template #default="{ row }">
            <span v-if="row.operationLineNo">{{ row.operationLineNo }} · </span>{{ row.operationName }}
          </template>
        </el-table-column>
        <el-table-column prop="materialName" :label="$tf('物料')" min-width="130" show-overflow-tooltip />
        <el-table-column :label="$tf('关联来源')" width="100">
          <template #default="{ row }">{{ sourceLabel(row.sourceType) }}</template>
        </el-table-column>
        <el-table-column :label="$tf('任务状态')" width="96" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.taskStatus)" size="small">{{ row.taskStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$tf('建立时间')" width="150">
          <template #default="{ row }">{{ formatTime(row.startTime || row.createTime) }}</template>
        </el-table-column>
        <el-table-column :label="$tf('操作')" width="260" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.taskStatus === '待下发' || row.taskStatus === '下发失败'" v-permission="perm('dispatch')"
                       type="primary" link size="small" @click="onDispatch(row)">{{ $tt('下发') }}</el-button>
            <el-button v-permission="perm('writeParams')" type="primary" link size="small"
                       @click="openParams(row)">{{ $tt('参数下发') }}</el-button>
            <el-button v-if="row.taskStatus !== '已完成' && row.taskStatus !== '已取消'" v-permission="perm('rebind')"
                       type="primary" link size="small" @click="openRebind(row)">{{ $tt('改绑') }}</el-button>
            <el-button v-if="row.taskStatus !== '已完成' && row.taskStatus !== '已取消'" v-permission="perm('unbind')"
                       type="danger" link size="small" @click="onRelease(row)">{{ $tt('解除') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="empty-inline" v-if="!loading && rows.length === 0">
        {{ $tt('还没有设备任务。设备上报数据后会自动关联；也可以点「新增关联」人工指定。') }}
      </div>
      <div class="table-pagination">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="total"
                       :page-sizes="[20, 50, 100]" layout="total, sizes, prev, pager, next" small background
                       @current-change="load" @size-change="load" />
      </div>
    </div>

    <!-- 新增关联 -->
    <el-dialog v-model="createVisible" width="620px" :close-on-click-modal="false">
      <template #header>
        <div class="dlg-header">
          <span class="dlg-title">{{ $tt('新增设备关联') }}</span>
          <span class="dlg-header-actions">
            <el-button size="small" @click="createVisible = false">{{ $tt('取消') }}</el-button>
            <el-button size="small" type="primary" :loading="saving" @click="onCreate">{{ $tt('保存') }}</el-button>
          </span>
        </div>
      </template>
      <el-form label-width="90px">
        <el-form-item :label="$tf('设备')" required>
          <el-select v-model="createForm.deviceId" filterable style="width:100%" :placeholder="$tt('请选择设备')">
            <el-option v-for="d in devices" :key="d.id" :label="`${d.deviceCode} · ${d.deviceName}`" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$tf('工单工序')" required>
          <el-select v-model="createForm.operationRowId" filterable remote :remote-method="searchOptions"
                     :loading="optionsLoading" style="width:100%" :placeholder="$tt('输入工单号或工序名搜索')">
            <el-option v-for="o in options" :key="o.operationRowId" :label="optionLabel(o)" :value="o.operationRowId" />
          </el-select>
        </el-form-item>
      </el-form>
    </el-dialog>

    <!-- 改绑 -->
    <el-dialog v-model="rebindVisible" width="620px" :close-on-click-modal="false">
      <template #header>
        <div class="dlg-header">
          <span class="dlg-title">{{ $tt('改绑工序') }}</span>
          <span class="dlg-header-actions">
            <el-button size="small" @click="rebindVisible = false">{{ $tt('取消') }}</el-button>
            <el-button size="small" type="primary" :loading="saving" @click="onRebind">{{ $tt('保存') }}</el-button>
          </span>
        </div>
      </template>
      <el-form label-width="90px">
        <el-form-item :label="$tf('工单工序')" required>
          <el-select v-model="rebindOperationId" filterable remote :remote-method="searchOptions"
                     :loading="optionsLoading" style="width:100%" :placeholder="$tt('输入工单号或工序名搜索')">
            <el-option v-for="o in options" :key="o.operationRowId" :label="optionLabel(o)" :value="o.operationRowId" />
          </el-select>
        </el-form-item>
      </el-form>
    </el-dialog>
    <!-- 参数下发 -->
    <el-dialog v-model="paramsVisible" width="620px" :close-on-click-modal="false">
      <template #header>
        <div class="dlg-header">
          <span class="dlg-title">{{ $tt('参数下发') }}</span>
          <span class="dlg-header-actions">
            <el-button size="small" @click="paramsVisible = false">{{ $tt('取消') }}</el-button>
            <el-button size="small" type="primary" :loading="saving" @click="onWriteParams">{{ $tt('下发') }}</el-button>
          </span>
        </div>
      </template>
      <div class="params-device">{{ paramsDeviceLabel }}</div>
      <el-table :data="writableRows" border size="small" style="width:100%">
        <el-table-column prop="pointKey" :label="$tf('点位标识')" width="150" />
        <el-table-column prop="pointName" :label="$tf('点位名称')" min-width="130" />
        <el-table-column :label="$tf('当前值')" width="110">
          <template #default="{ row }">{{ row.lastValue === null || row.lastValue === undefined ? '' : row.lastValue }}</template>
        </el-table-column>
        <el-table-column :label="$tf('下发值')" width="170">
          <template #default="{ row }">
            <el-input v-model="row.writeValue" size="small" :placeholder="$tt('请输入下发值')" />
          </template>
        </el-table-column>
      </el-table>
      <div class="empty-inline" v-if="!writableLoading && writableRows.length === 0">
        {{ $tt('该设备还没有可写点位。请先到接入管理里把需要下发的点位设为可写。') }}
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'
import { listEnabledDevices } from '@/api/device/ledger'
import { pageTasks, operationOptions, createTask, rebindTask, releaseTask } from '@/api/device/task'
import { dispatchTaskCommand, listWritablePoints, writeDeviceParams } from '@/api/device/command'

const perm = usePagePerm('device:task')

const devices = ref([])
const rows = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const filterDeviceId = ref(null)
const filterStatus = ref(null)
const filterOrderNo = ref('')
const statuses = ['待下发', '已下发', '执行中', '已完成', '已取消', '下发失败']

const formatTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 19) : '')
const sourceLabel = (v) => ({ MSG: tt('报文指定'), AUTO: tt('自动关联'), MANUAL: tt('人工认领'), DISPATCH: tt('任务下发'), SCAN: tt('扫码认领') }[v] || v || '')
const statusType = (v) => ({ 待下发: 'info', 已下发: 'warning', 执行中: 'success', 已完成: '', 已取消: 'info', 下发失败: 'danger' }[v] || 'info')

async function load() {
  loading.value = true
  try {
    const params = { current: page.value, size: pageSize.value }
    if (filterDeviceId.value) params.deviceId = filterDeviceId.value
    if (filterStatus.value) params.status = filterStatus.value
    if (filterOrderNo.value) params.orderNo = filterOrderNo.value
    const res = await pageTasks(params)
    rows.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    rows.value = []
  } finally {
    loading.value = false
  }
}

function reload() { page.value = 1; load() }
function reset() {
  filterDeviceId.value = null
  filterStatus.value = null
  filterOrderNo.value = ''
  reload()
}

// ==================== 工序下拉 ====================
const options = ref([])
const optionsLoading = ref(false)
const optionLabel = (o) => `${o.orderNo} · ${o.lineNo || ''} ${o.operationName || ''}${o.materialName ? ' · ' + o.materialName : ''}`
async function searchOptions(keyword) {
  optionsLoading.value = true
  try {
    options.value = await operationOptions(keyword) || []
  } catch (e) {
    options.value = []
  } finally {
    optionsLoading.value = false
  }
}

// ==================== 新增 ====================
const createVisible = ref(false)
const saving = ref(false)
const createForm = reactive({ deviceId: null, operationRowId: null })
function openCreate() {
  createForm.deviceId = filterDeviceId.value || null
  createForm.operationRowId = null
  options.value = []
  searchOptions('')
  createVisible.value = true
}
async function onCreate() {
  if (!createForm.deviceId) { ElMessage.warning(tt('请选择设备')); return }
  if (!createForm.operationRowId) { ElMessage.warning(tt('请选择工单工序')); return }
  saving.value = true
  try {
    await createTask({ ...createForm })
    ElMessage.success(tt('保存成功'))
    createVisible.value = false
    load()
  } catch (e) { /* 后端已给出原因 */ } finally {
    saving.value = false
  }
}

// ==================== 改绑 / 下发 / 解除 ====================
const rebindVisible = ref(false)
const rebindOperationId = ref(null)
const rebindTaskId = ref(null)
function openRebind(row) {
  rebindTaskId.value = row.id
  rebindOperationId.value = row.operationRowId
  options.value = []
  searchOptions(row.orderNo || '')
  rebindVisible.value = true
}
async function onRebind() {
  if (!rebindOperationId.value) { ElMessage.warning(tt('请选择工单工序')); return }
  saving.value = true
  try {
    await rebindTask(rebindTaskId.value, rebindOperationId.value)
    ElMessage.success(tt('改绑成功'))
    rebindVisible.value = false
    load()
  } catch (e) { /* 后端已给出原因 */ } finally {
    saving.value = false
  }
}

async function onDispatch(row) {
  try {
    const res = await dispatchTaskCommand(row.id)
    ElMessage.success(tt('已下发') + (res && res.message ? '：' + res.message : ''))
    load()
  } catch (e) { /* 后端已给出原因 */ }
}

async function onRelease(row) {
  let reason = ''
  try {
    const res = await ElMessageBox.prompt(tt('解除后该设备的新数据不再计入此工序。请填写解除原因。'), tt('解除关联'), {
      inputPlaceholder: tt('如：换线、工单结束、设备故障'),
      inputValidator: (v) => (v && v.trim() ? true : tt('请填写原因')),
      type: 'warning'
    })
    reason = res.value
  } catch (e) { return }
  try {
    await releaseTask(row.id, reason)
    ElMessage.success(tt('已解除'))
    load()
  } catch (e) { /* 后端已给出原因 */ }
}

// ==================== 参数下发 ====================
const paramsVisible = ref(false)
const paramsDeviceId = ref(null)
const paramsDeviceLabel = ref('')
const writableRows = ref([])
const writableLoading = ref(false)

async function openParams(row) {
  paramsDeviceId.value = row.deviceId
  paramsDeviceLabel.value = `${row.deviceCode || ''} · ${row.deviceName || ''}`
  writableRows.value = []
  paramsVisible.value = true
  writableLoading.value = true
  try {
    const list = await listWritablePoints(row.deviceId) || []
    writableRows.value = list.map(p => ({ ...p, writeValue: '' }))
  } catch (e) {
    writableRows.value = []
  } finally {
    writableLoading.value = false
  }
}

async function onWriteParams() {
  const filled = writableRows.value.filter(r => r.writeValue !== '' && r.writeValue !== null && r.writeValue !== undefined)
  if (filled.length === 0) { ElMessage.warning(tt('请至少填写一个下发值')); return }
  saving.value = true
  try {
    const res = await writeDeviceParams(paramsDeviceId.value, filled.map(r => ({ pointKey: r.pointKey, value: r.writeValue })))
    if (res && res.failCount > 0) ElMessage.warning(tt('部分参数下发失败') + '：' + res.message)
    else ElMessage.success(tt('下发成功') + (res && res.channel === 'QUEUE' ? '，等待设备拉取' : ''))
    paramsVisible.value = false
  } catch (e) { /* 后端已给出原因 */ } finally {
    saving.value = false
  }
}

onMounted(async () => {
  load()
  try { devices.value = await listEnabledDevices() || [] } catch (e) { devices.value = [] }
})
</script>

<style scoped lang="scss">
.device-page { background:#fff; min-height:100%; padding:10px 12px 20px; box-sizing:border-box; font-family:Tahoma,'Microsoft YaHei',sans-serif; }
.dev-toolbar { display:flex; align-items:center; justify-content:space-between; gap:12px; padding:6px 0 10px; border-bottom:1px solid #eef1f5; flex-wrap:wrap; }
.dev-actions { display:flex; align-items:center; gap:8px; flex-wrap:wrap; }
.dev-body { padding-top:10px; }
.empty-inline { font-size:12.5px; color:#94a3b8; padding:10px 2px; line-height:1.7; }
.table-pagination { display:flex; justify-content:flex-end; padding:10px 0 0; }
.dlg-header { display:flex; align-items:center; justify-content:space-between; gap:12px; }
.dlg-title { font-size:15px; font-weight:600; }
.dlg-header-actions { display:inline-flex; gap:8px; }
.params-device { font-size:12.5px; color:#475569; padding:0 2px 8px; }
</style>
