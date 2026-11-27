<template>
  <div class="device-debug">
    <div class="dev-toolbar">
      <div class="dev-actions">
        <span class="label">{{ $tt('当前设备') }}</span>
        <el-select v-model="deviceId" filterable clearable style="width: 260px" :placeholder="$tt('请选择设备')" @change="loadAll">
          <el-option v-for="d in devices" :key="d.id" :label="`${d.deviceCode} · ${d.deviceName}`" :value="d.id" />
        </el-select>
        <el-button size="small" @click="loadAll">{{ $tt('刷新') }}</el-button>
      </div>
      <div class="dev-actions">
        <span class="engine-line">
          {{ $tt('接入引擎') }}：MQTT {{ engineStatus.mqtt || 0 }} · TCP {{ engineStatus.tcp || 0 }} · {{ $tt('轮询') }} {{ engineStatus.poll || 0 }}
        </span>
        <el-button size="small" @click="onReconcile">{{ $tt('重新扫描通道') }}</el-button>
        <el-button v-permission="perm('simulate')" type="primary" size="small" :disabled="!deviceId" @click="openSimulate">{{ $tt('模拟上报') }}</el-button>
      </div>
    </div>

    <div v-if="!deviceId" class="empty-guide">
      <div class="empty-title">{{ $tt('请先选择一台设备') }}</div>
      <div class="empty-desc">{{ $tt('调试台用于确认设备能不能连上、报文能不能解析、点位值有没有进来。建议先在「接入管理」生成凭证并配置点位。') }}</div>
      <el-button type="primary" size="small" @click="goAccess">{{ $tt('去接入管理') }}</el-button>
    </div>

    <template v-else>
      <div class="stat-row">
        <div class="stat-card">
          <div class="stat-label">{{ $tt('凭证状态') }}</div>
          <div class="stat-value">
            <el-tag :type="overview.credentialStatus === '启用' ? 'success' : (overview.credentialStatus === '未生成' ? 'info' : 'danger')" size="small">
              {{ overview.credentialStatus || $tt('未生成') }}
            </el-tag>
          </div>
          <div class="stat-sub mono">{{ overview.credentialNo || '—' }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">{{ $tt('点位数量') }}</div>
          <div class="stat-value">{{ overview.pointCount || 0 }}</div>
          <div class="stat-sub">{{ $tt('已配置的数据点') }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">{{ $tt('接入通道') }}</div>
          <div class="stat-value">{{ (overview.channels || []).length }}</div>
          <div class="stat-sub">{{ $tt('已配置通道数') }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">{{ $tt('最近报文') }}</div>
          <div class="stat-value small-value">{{ formatTime(overview.lastMessageTime) || '—' }}</div>
          <div class="stat-sub">{{ $tt('最后一次收到数据的时间') }}</div>
        </div>
      </div>

      <div class="debug-body">
        <section class="panel left">
          <div class="panel-head">
            <span class="panel-title">{{ $tt('报文流') }}</span>
            <span class="panel-sub">{{ $tt('最近 50 条，含模拟上报') }}</span>
          </div>
          <el-table :data="messages" border size="small" height="420" v-loading="loading" @row-click="openMessage">
            <el-table-column :label="$tf('时间')" width="150">
              <template #default="{ row }">{{ formatTime(row.receivedAt) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('结果')" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.parseStatus === '成功' ? 'success' : (row.parseStatus === '部分成功' ? 'warning' : 'danger')" size="small">
                  {{ row.parseStatus }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('来源')" width="70" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.simulated === 1" type="info" size="small">{{ $tt('模拟') }}</el-tag>
                <span v-else class="muted">{{ row.channelType || 'HTTP' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="messageKey" :label="$tf('消息标识')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="errorMsg" :label="$tf('异常')" min-width="150" show-overflow-tooltip />
          </el-table>
          <div class="empty-inline" v-if="!loading && messages.length === 0">
            {{ $tt('还没有收到报文。可先用「模拟上报」验证解析链路。') }}
          </div>
        </section>

        <section class="panel right">
          <div class="panel-head">
            <span class="panel-title">{{ $tt('点位实时值') }}</span>
            <span class="panel-sub">{{ $tt('模拟上报不写实时值') }}</span>
          </div>
          <el-table :data="values" border size="small" height="420" v-loading="loading">
            <el-table-column prop="pointKey" :label="$tf('点位标识')" min-width="120" />
            <el-table-column prop="pointName" :label="$tf('名称')" min-width="110" />
            <el-table-column :label="$tf('用途')" width="100">
              <template #default="{ row }">{{ purposeLabel(row.purpose) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('值')" min-width="110">
              <template #default="{ row }">
                <span v-if="row.lastValue !== null && row.lastValue !== undefined">{{ row.lastValue }} {{ row.unit || '' }}</span>
                <span v-else class="muted">{{ $tt('暂无数据') }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('时间')" width="150">
              <template #default="{ row }">{{ formatTime(row.lastValueTime) || '—' }}</template>
            </el-table-column>
          </el-table>
        </section>
      </div>
    </template>

    <!-- 报文详情 -->
    <el-dialog v-model="messageVisible" width="760px" :title="$tt('报文详情')" top="6vh" append-to-body>
      <div class="msg-meta">
        <span>{{ $tt('时间') }}：{{ formatTime(currentMessage.receivedAt) }}</span>
        <span>{{ $tt('结果') }}：{{ currentMessage.parseStatus }}</span>
        <span>{{ $tt('消息标识') }}：<span class="mono">{{ currentMessage.messageKey }}</span></span>
        <span v-if="currentMessage.simulated === 1" class="sim-tag">{{ $tt('模拟上报') }}</span>
      </div>
      <div class="msg-grid">
        <div>
          <div class="block-title">{{ $tt('原始报文') }}</div>
          <pre class="code-block">{{ pretty(currentMessage.rawContent) }}</pre>
        </div>
        <div>
          <div class="block-title">{{ $tt('解析结果') }}</div>
          <pre class="code-block">{{ pretty(currentMessage.parsedJson) }}</pre>
        </div>
      </div>
      <el-alert v-if="currentMessage.errorMsg" type="warning" :closable="false" show-icon :title="currentMessage.errorMsg" />
    </el-dialog>

    <!-- 模拟上报 -->
    <el-dialog v-model="simulateVisible" width="680px" :close-on-click-modal="false" top="6vh" append-to-body>
      <template #header>
        <div class="dlg-header">
          <span class="dlg-title">{{ $tt('模拟上报') }}</span>
          <span class="dlg-header-actions">
            <el-button size="small" @click="simulateVisible = false">{{ $tt('取消') }}</el-button>
            <el-button size="small" type="primary" :loading="simulating" @click="onSimulate">{{ $tt('发送') }}</el-button>
          </span>
        </div>
      </template>
      <div class="hint" style="margin-bottom:8px">
        {{ $tt('模拟上报会走完整解析链路，但报文标记为模拟：不写点位实时值、不参与生产联动。') }}
      </div>
      <el-input v-model="simulateBody" type="textarea" :rows="10" class="mono" />
      <div class="block-title" style="margin-top:12px">{{ $tt('解析结果') }}</div>
      <pre class="code-block result-block">{{ simulateResult }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'
import { listEnabledDevices } from '@/api/device/ledger'
import { listPoints } from '@/api/device/point'
import { getDebugOverview, listDebugMessages, listDebugValues, simulateReport, getEngineStatus, reconcileEngine } from '@/api/device/debug'

const router = useRouter()
const perm = usePagePerm('device:debug')

const devices = ref([])
const deviceId = ref(null)
const loading = ref(false)
const overview = ref({})
const engineStatus = ref({})
const messages = ref([])
const values = ref([])
const points = ref([])

const purposes = {
  START_SIGNAL: tt('开工信号'), STOP_SIGNAL: tt('停机信号'), QUALIFIED_COUNT: tt('合格计数'),
  REJECTED_COUNT: tt('报废计数'), ALARM_CODE: tt('报警码'), FAULT_BIT: tt('故障位'),
  PARAM: tt('工艺参数'), WRITABLE_PARAM: tt('可写参数'), ORDER_NO: tt('工单号'),
  OPERATION_NO: tt('工序号'), MONITOR: tt('普通监控')
}
const purposeLabel = (v) => purposes[v] || v || ''
const formatTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 19) : '')
const pretty = (t) => {
  if (!t) return ''
  try { return JSON.stringify(JSON.parse(t), null, 2) } catch (e) { return t }
}

async function loadDevices() {
  try {
    devices.value = await listEnabledDevices() || []
    if (!deviceId.value && devices.value.length > 0) deviceId.value = devices.value[0].id
  } catch (e) {
    devices.value = []
  }
}

async function loadAll() {
  loadEngine()
  if (!deviceId.value) return
  loading.value = true
  try {
    const [ov, msgs, vals, pts] = await Promise.all([
      getDebugOverview(deviceId.value),
      listDebugMessages(deviceId.value, 50),
      listDebugValues(deviceId.value),
      listPoints(deviceId.value)
    ])
    overview.value = ov || {}
    messages.value = msgs || []
    values.value = vals || []
    points.value = pts || []
  } catch (e) { /* 后端已给出原因 */ } finally {
    loading.value = false
  }
}

async function loadEngine() {
  try {
    engineStatus.value = await getEngineStatus() || {}
  } catch (e) {
    engineStatus.value = {}
  }
}

async function onReconcile() {
  try {
    engineStatus.value = await reconcileEngine() || {}
    ElMessage.success(tt('已重新扫描接入通道'))
    await loadAll()
  } catch (e) {
    // 后端已给出原因
  }
}

function goAccess() {
  router.push('/device/access')
}

// ==================== 报文详情 ====================
const messageVisible = ref(false)
const currentMessage = reactive({})
function openMessage(row) {
  Object.assign(currentMessage, row)
  messageVisible.value = true
}

// ==================== 模拟上报 ====================
const simulateVisible = ref(false)
const simulating = ref(false)
const simulateBody = ref('')
const simulateResult = ref('')

function openSimulate() {
  const sample = {}
  const usable = points.value.filter(p => p.enabled === 1).slice(0, 3)
  if (usable.length === 0) {
    simulateBody.value = JSON.stringify({ messageKey: `SIM-${Date.now()}`, points: {} }, null, 2)
  } else {
    usable.forEach((p, idx) => {
      sample[p.pointKey] = p.dataType === 'TEXT' ? 'OK' : (idx === 0 ? 1 : 10 + idx)
    })
    simulateBody.value = JSON.stringify({ messageKey: `SIM-${Date.now()}`, points: sample }, null, 2)
  }
  simulateResult.value = ''
  simulateVisible.value = true
}

async function onSimulate() {
  if (!simulateBody.value || !simulateBody.value.trim()) {
    ElMessage.warning(tt('请填写模拟报文'))
    return
  }
  simulating.value = true
  try {
    const res = await simulateReport(deviceId.value, simulateBody.value)
    simulateResult.value = JSON.stringify(res, null, 2)
    ElMessage.success(tt('模拟上报已接收'))
  } catch (e) {
    // 后端返回的中文原因已在拦截器提示
  } finally {
    simulating.value = false
  }
  // 弹窗内不等待列表刷新，避免表格加载遮罩影响弹窗操作
  loadAll()
}

onMounted(async () => {
  await loadDevices()
  await loadAll()
})
</script>

<style scoped lang="scss">
.device-debug {
  background: #fff;
  min-height: 100%;
  padding: 10px 12px 20px;
  box-sizing: border-box;
  font-family: Tahoma, 'Microsoft YaHei', sans-serif;
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
.dev-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.dev-actions .label {
  font-size: 13px;
  color: #475569;
}
.engine-line {
  font-size: 12px;
  color: #64748b;
  margin-right: 4px;
}
.empty-guide {
  border: 1px dashed #d7dee9;
  border-radius: 8px;
  padding: 26px 20px;
  text-align: center;
  color: #64748b;
  margin-top: 16px;
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
.empty-inline {
  font-size: 12.5px;
  color: #94a3b8;
  padding: 10px 2px;
}
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  padding: 12px 0;
}
.stat-card {
  border: 1px solid #eef1f5;
  border-radius: 8px;
  padding: 10px 14px;
  background: #fbfcfe;
}
.stat-label {
  font-size: 12px;
  color: #94a3b8;
}
.stat-value {
  font-size: 20px;
  font-weight: 600;
  color: #1c2430;
  margin: 4px 0 2px;
}
.small-value {
  font-size: 13px;
  font-weight: 500;
}
.stat-sub {
  font-size: 12px;
  color: #94a3b8;
}
.debug-body {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.panel {
  border: 1px solid #eef1f5;
  border-radius: 8px;
  padding: 10px 12px;
}
.panel.left {
  flex: 1.15;
  min-width: 0;
}
.panel.right {
  flex: 1;
  min-width: 0;
}
.panel-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding-bottom: 8px;
}
.panel-title {
  font-size: 13.5px;
  font-weight: 600;
  color: #334155;
}
.panel-sub {
  font-size: 12px;
  color: #94a3b8;
}
.mono {
  font-family: Consolas, 'Courier New', monospace;
  font-size: 12.5px;
}
.muted {
  color: #94a3b8;
  font-size: 12.5px;
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
.msg-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  font-size: 12.5px;
  color: #64748b;
  margin-bottom: 10px;
}
.sim-tag {
  color: #6d28d9;
  background: #f1ebff;
  border-radius: 4px;
  padding: 0 6px;
}
.msg-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.block-title {
  font-size: 12.5px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 6px;
}
.code-block {
  background: #0f172a;
  color: #d7e2f0;
  border-radius: 6px;
  padding: 12px;
  font-family: Consolas, 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.6;
  max-height: 320px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}
.result-block {
  max-height: 180px;
}
.hint {
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.6;
}
@media (max-width: 1200px) {
  .stat-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .debug-body {
    flex-direction: column;
  }
  .panel.left,
  .panel.right {
    width: 100%;
  }
  .msg-grid {
    grid-template-columns: 1fr;
  }
}
</style>
