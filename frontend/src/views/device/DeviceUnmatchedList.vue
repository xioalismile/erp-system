<template>
  <div class="device-page">
    <div class="dev-toolbar">
      <div class="dev-actions">
        <el-select v-model="filterDeviceId" :placeholder="$tt('设备')" size="small" clearable filterable style="width:200px" @change="reload">
          <el-option v-for="d in devices" :key="d.id" :label="`${d.deviceCode} · ${d.deviceName}`" :value="d.id" />
        </el-select>
        <el-select v-model="filterStatus" :placeholder="$tt('状态')" size="small" clearable style="width:110px" @change="reload">
          <el-option :label="$tf('未关联')" value="未关联" />
          <el-option :label="$tf('已认领')" value="已认领" />
          <el-option :label="$tf('已过期')" value="已过期" />
        </el-select>
        <el-button type="primary" size="small" @click="reload">{{ $tt('查询') }}</el-button>
        <el-button size="small" @click="reset">{{ $tt('重置') }}</el-button>
      </div>
    </div>

    <div class="dev-body">
      <el-table :data="rows" border size="small" v-loading="loading" style="width:100%">
        <el-table-column :label="$tf('时间')" width="150">
          <template #default="{ row }">{{ formatTime(row.periodStart) }}</template>
        </el-table-column>
        <el-table-column :label="$tf('设备')" min-width="150">
          <template #default="{ row }">{{ row.deviceCode }} · {{ row.deviceName }}</template>
        </el-table-column>
        <el-table-column prop="qualifiedCount" :label="$tf('合格计数')" width="100" align="right" />
        <el-table-column prop="rejectedCount" :label="$tf('报废计数')" width="100" align="right" />
        <el-table-column prop="reason" :label="$tf('未关联原因')" min-width="200" show-overflow-tooltip />
        <el-table-column :label="$tf('状态')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '未关联' ? 'warning' : (row.status === '已认领' ? 'success' : 'info')" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="claimedOperationName" :label="$tf('已认领工序')" min-width="120" />
        <el-table-column :label="$tf('操作')" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === '未关联'" v-permission="perm('claim')" type="primary" link size="small"
                       @click="openClaim(row)">{{ $tt('认领') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="empty-inline" v-if="!loading && rows.length === 0">
        {{ $tt('当前没有未关联数据。设备认不到工单工序且产生产量时，会进入这里等人工认领。') }}
      </div>
      <div class="table-pagination">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="total"
                       :page-sizes="[20, 50, 100]" layout="total, sizes, prev, pager, next" small background
                       @current-change="load" @size-change="load" />
      </div>
    </div>

    <el-dialog v-model="claimVisible" width="620px" :close-on-click-modal="false">
      <template #header>
        <div class="dlg-header">
          <span class="dlg-title">{{ $tt('认领到工单工序') }}</span>
          <span class="dlg-header-actions">
            <el-button size="small" @click="claimVisible = false">{{ $tt('取消') }}</el-button>
            <el-button size="small" type="primary" :loading="saving" @click="onClaim">{{ $tt('确认认领') }}</el-button>
          </span>
        </div>
      </template>
      <el-form label-width="90px">
        <el-form-item :label="$tf('工单工序')" required>
          <el-select v-model="claimOperationId" filterable remote :remote-method="searchOptions"
                     :loading="optionsLoading" style="width:100%" :placeholder="$tt('输入工单号或工序名搜索')">
            <el-option v-for="o in options" :key="o.operationRowId" :label="optionLabel(o)" :value="o.operationRowId" />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="hint">{{ $tt('认领后这段计数会建立设备任务并进入该工序；报工确认在后续阶段接入。') }}</div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'
import { listEnabledDevices } from '@/api/device/ledger'
import { operationOptions } from '@/api/device/task'
import { pageUnmatched, claimUnmatched } from '@/api/device/unmatched'

const perm = usePagePerm('device:unmatched')

const devices = ref([])
const rows = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const filterDeviceId = ref(null)
const filterStatus = ref(null)

const formatTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 19) : '')

async function load() {
  loading.value = true
  try {
    const params = { current: page.value, size: pageSize.value }
    if (filterDeviceId.value) params.deviceId = filterDeviceId.value
    if (filterStatus.value) params.status = filterStatus.value
    const res = await pageUnmatched(params)
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

// ==================== 认领 ====================
const claimVisible = ref(false)
const claimRow = ref(null)
const claimOperationId = ref(null)
const saving = ref(false)
const options = ref([])
const optionsLoading = ref(false)
const optionLabel = (o) => `${o.orderNo} · ${o.lineNo || ''} ${o.operationName || ''}`

async function searchOptions(keyword) {
  optionsLoading.value = true
  try { options.value = await operationOptions(keyword) || [] } catch (e) { options.value = [] } finally { optionsLoading.value = false }
}

function openClaim(row) {
  claimRow.value = row
  claimOperationId.value = null
  options.value = []
  searchOptions('')
  claimVisible.value = true
}

async function onClaim() {
  if (!claimOperationId.value) { ElMessage.warning(tt('请选择工单工序')); return }
  saving.value = true
  try {
    await claimUnmatched(claimRow.value.id, claimOperationId.value)
    ElMessage.success(tt('认领成功'))
    claimVisible.value = false
    load()
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
.hint { font-size:12px; color:#94a3b8; line-height:1.6; }
</style>
