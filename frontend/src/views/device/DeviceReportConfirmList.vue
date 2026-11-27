<template>
  <div class="device-page">
    <div class="dev-toolbar">
      <div class="dev-actions">
        <el-select v-model="filterDeviceId" :placeholder="$tt('设备')" size="small" clearable filterable style="width:200px" @change="reload">
          <el-option v-for="d in devices" :key="d.id" :label="`${d.deviceCode} · ${d.deviceName}`" :value="d.id" />
        </el-select>
        <el-select v-model="filterStatus" :placeholder="$tt('状态')" size="small" clearable style="width:120px" @change="reload">
          <el-option :label="$tf('待确认')" value="待确认" />
          <el-option :label="$tf('已确认')" value="已确认" />
          <el-option :label="$tf('已驳回')" value="已驳回" />
        </el-select>
        <el-button type="primary" size="small" @click="reload">{{ $tt('查询') }}</el-button>
        <el-button size="small" @click="reset">{{ $tt('重置') }}</el-button>
      </div>
    </div>

    <div class="dev-body">
      <el-table :data="rows" border size="small" v-loading="loading" style="width:100%">
        <el-table-column prop="pendingNo" :label="$tf('待确认单号')" width="150" />
        <el-table-column :label="$tf('设备')" min-width="150">
          <template #default="{ row }">{{ row.deviceCode }} · {{ row.deviceName }}</template>
        </el-table-column>
        <el-table-column prop="orderNo" :label="$tf('工单号')" min-width="130" />
        <el-table-column :label="$tf('工序')" min-width="120">
          <template #default="{ row }">
            <span v-if="row.operationLineNo">{{ row.operationLineNo }} · </span>{{ row.operationName }}
          </template>
        </el-table-column>
        <el-table-column prop="materialName" :label="$tf('物料')" min-width="130" show-overflow-tooltip />
        <el-table-column :label="$tf('统计期间')" width="230">
          <template #default="{ row }">{{ formatTime(row.periodStart) }} ~ {{ formatTime(row.periodEnd) }}</template>
        </el-table-column>
        <el-table-column prop="qualifiedCount" :label="$tf('合格')" width="90" align="right" />
        <el-table-column prop="rejectedCount" :label="$tf('报废')" width="90" align="right" />
        <el-table-column :label="$tf('计数起止')" width="140" align="right">
          <template #default="{ row }">{{ num(row.countStart) }} ~ {{ num(row.countEnd) }}</template>
        </el-table-column>
        <el-table-column :label="$tf('状态')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operationReportNo" :label="$tf('工序汇报单')" width="150" />
        <el-table-column prop="rejectReason" :label="$tf('驳回原因')" min-width="150" show-overflow-tooltip />
        <el-table-column :label="$tf('处理人')" width="120">
          <template #default="{ row }">{{ row.confirmBy || '' }}</template>
        </el-table-column>
        <el-table-column :label="$tf('处理时间')" width="150">
          <template #default="{ row }">{{ formatTime(row.confirmTime) }}</template>
        </el-table-column>
        <el-table-column :label="$tf('操作')" width="130" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === '待确认'" v-permission="perm('confirmReport')" type="primary" link size="small"
                       @click="onConfirm(row)">{{ $tt('确认') }}</el-button>
            <el-button v-if="row.status === '待确认'" v-permission="perm('rejectReport')" type="danger" link size="small"
                       @click="onReject(row)">{{ $tt('驳回') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="empty-inline" v-if="!loading && rows.length === 0">
        {{ $tt('当前没有待确认报工。设备在执行中的任务上累计产量达到阈值后，会自动生成这里的待确认单。') }}
      </div>
      <div class="table-pagination">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="total"
                       :page-sizes="[20, 50, 100]" layout="total, sizes, prev, pager, next" small background
                       @current-change="load" @size-change="load" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'
import { listEnabledDevices } from '@/api/device/ledger'
import { pageReportPending, confirmReportPending, rejectReportPending } from '@/api/device/report'

const perm = usePagePerm('device:report-confirm')

const devices = ref([])
const rows = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const filterDeviceId = ref(null)
const filterStatus = ref(null)

const formatTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 19) : '')
const num = (v) => (v === null || v === undefined || v === '' ? '0' : String(Number(v)))
const statusType = (v) => ({ 待确认: 'warning', 已确认: 'success', 已驳回: 'danger' }[v] || 'info')

async function load() {
  loading.value = true
  try {
    const params = { current: page.value, size: pageSize.value }
    if (filterDeviceId.value) params.deviceId = filterDeviceId.value
    if (filterStatus.value) params.status = filterStatus.value
    const res = await pageReportPending(params)
    rows.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    rows.value = []
  } finally {
    loading.value = false
  }
}
function reload() { page.value = 1; load() }
function reset() { filterDeviceId.value = null; filterStatus.value = null; reload() }

async function onConfirm(row) {
  try {
    await ElMessageBox.confirm(
      tt('确认后将按本单的合格与报废数量生成工序汇报单，并推进工序汇报。'),
      tt('确认报工'), { type: 'warning', confirmButtonText: tt('确认'), cancelButtonText: tt('取消') }
    )
  } catch (e) { return }
  try {
    const res = await confirmReportPending(row.id)
    ElMessage.success(tt('已确认，生成工序汇报单') + ' ' + (res.operationReportNo || ''))
    load()
  } catch (e) { /* 后端已给出原因 */ }
}

async function onReject(row) {
  let reason = ''
  try {
    const res = await ElMessageBox.prompt(tt('驳回后本单数量不会写入工序汇报，请填写驳回原因。'), tt('驳回报工'), {
      inputPlaceholder: tt('如：数量与实际不符、设备计数异常'),
      inputValidator: (v) => (v && v.trim() ? true : tt('请填写驳回原因')),
      type: 'warning'
    })
    reason = res.value
  } catch (e) { return }
  try {
    await rejectReportPending(row.id, reason)
    ElMessage.success(tt('已驳回'))
    load()
  } catch (e) { /* 后端已给出原因 */ }
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
</style>
