<template>
  <div class="logistics-dashboard">
    <!-- ===== 首次使用引导（授权码未配置时显示） ===== -->
    <el-alert
      v-if="showGuide"
      type="warning"
      :closable="false"
      class="setup-guide-alert"
    >
      <template #title>
        <div class="guide-title">
          <el-icon><WarningFilled /></el-icon>
          <span>{{ $tt("物流查询功能尚未配置，无法查询物流轨迹") }}</span>
        </div>
      </template>
      <div class="guide-body">
        <div class="guide-steps">
          <div class="guide-step">
            <div class="step-num">1</div>
            <div class="step-text">
              <strong>{{ $tt("注册百递云API账号") }}</strong><br />
              <span>{{ $tt("访问") }} </span>
              <el-link type="primary" href="https://api.kuaidi100.com" target="_blank" :underline="false">api.kuaidi100.com</el-link>
              <span> {{ $tt("注册免费账号（免费接口每日可查100次）") }}</span>
            </div>
          </div>
          <div class="guide-step">
            <div class="step-num">2</div>
            <div class="step-text">
              <strong>{{ $tt("获取授权信息") }}</strong><br />
              <span>{{ $tt("登录百递云API平台 → 控制台 → 我的授权 → 复制「授权码(customer)」+「签名密钥(key)」") }}</span>
            </div>
          </div>
          <div class="guide-step">
            <div class="step-num">3</div>
            <div class="step-text">
              <strong>{{ $tt("在系统中填入授权码") }}</strong><br />
              <span>点击下方按钮，进入"系统设置 → 物流配置"填入授权码并保存</span>
            </div>
          </div>
          <div class="guide-step">
            <div class="step-num">4</div>
            <div class="step-text">
              <strong>{{ $tt("在销售出库单中填写物流单号") }}</strong><br />
              <span>{{ $tt("出库单审核后，在出库单中填写承运商和物流单号，看板将自动展示轨迹") }}</span>
            </div>
          </div>
        </div>
        <div class="guide-actions">
          <el-button type="primary" :icon="Setting" v-permission="perm('view')" @click="goToConfig">{{ $tt("前往配置") }}</el-button>
          <el-button text v-permission="perm('dlgClose')" @click="showGuide = false">{{ $tt("稍后配置，先看看") }}</el-button>
        </div>
        <div style="margin-top:10px;font-size:12px;color:#909399;line-height:1.6;">
          提示：如需查询 <strong>{{ $tt("国际物流") }}</strong>（DHL / UPS / FedEx / USPS / 国际EMS 等），
          请在「物流配置」页同时填写 <strong>{{ $tt("17TRACK 密钥") }}</strong> 并开启国际物流查询功能。
        </div>
      </div>
    </el-alert>

    <!-- 功能未启用提示 -->
    <el-alert
      v-if="!logisticsEnabled && !showGuide"
      type="info"
      :closable="false"
      style="margin-bottom:12px;"
    >
      <template #title>
        <span>{{ $tt("物流查询功能已关闭，") }}</span>
        <el-link type="primary" @click="goToConfig" :underline="false">{{ $tt("前往开启 →") }}</el-link>
      </template>
    </el-alert>

    <!-- 顶部工具栏 -->
    <div class="dashboard-header">
      <div class="header-left">
        <el-button :icon="ArrowLeft" v-permission="perm('dlgCancel')" @click="$router.back()" text>{{ $tt("返回") }}</el-button>
        <h2 class="dashboard-title">
          <el-icon class="title-icon"><Van /></el-icon>
          物流监控看板
        </h2>
        <el-tag v-if="lastRefreshTime" size="small" type="info" effect="plain">
          最后刷新：{{ lastRefreshTime }}
        </el-tag>
      </div>
      <div class="header-right">
        <el-input
          v-model="keyword"
          :placeholder="$tt('搜索出库单号/物流单号/客户')"
          class="search-input"
          clearable
          :prefix-icon="Search"
          @keyup.enter="loadInTransit"
        />
        <el-button type="primary" v-permission="perm('refresh')" :loading="loading" @click="manualRefresh">
          <el-icon><Refresh /></el-icon> 手动刷新
        </el-button>
        <el-tooltip :content="autoRefresh ? '自动刷新中（每 ' + refreshInterval + ' 秒）' : '已暂停自动刷新'" placement="top">
          <el-switch
            v-model="autoRefresh"
            inline-prompt
            :active-text="$tt('自动')"
            :inactive-text="$tt('暂停')"
            @change="onAutoRefreshChange"
          />
        </el-tooltip>
      </div>
    </div>

    <!-- 状态统计卡片 -->
    <div class="status-cards">
      <div
        v-for="card in statusCards"
        :key="card.code"
        class="status-card"
        :class="card.color"
        @click="filterByStatus(card.code)"
      >
        <el-icon class="card-icon"><component :is="card.icon" /></el-icon>
        <div class="card-info">
          <div class="card-count">{{ card.count }}</div>
          <div class="card-label">{{ $tf(card.label) }}</div>
        </div>
      </div>
    </div>

    <!-- 在途出库单表格 -->
    <el-card shadow="never" class="dashboard-card">
      <template #header>
        <div class="card-header">
          <span>
            <el-icon><List /></el-icon>
            在途出库单（共 {{ tableData.length }} 单）
          </span>
          <div class="card-header-right" style="display:flex;align-items:center;gap:8px;">
            <el-tag v-if="currentFilter" type="warning" closable @close="clearStatusFilter">
              已筛选状态：{{ currentFilterText }}
            </el-tag>
            <el-tooltip :content="$tt('列设置')" placement="top">
              <el-button :icon="Setting" circle size="small" @click="openColumnSetting" />
            </el-tooltip>
          </div>
        </div>
      </template>

      <el-table
        ref="tableRef"
        :data="tableData"
        v-loading="loading"
        border
        element-loading-text="正在查询物流..."
        element-loading-background="rgba(255,255,255,0.7)"
        style="width: 100%"
        :row-class-name="rowClassName"
        @row-click="expandRow"
        @header-dragend="onHeaderDragend"
        row-key="deliveryId"
        :key="configVersion"
      >
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-content">
              <el-empty v-if="!row.logistics || !row.logistics.tracks || row.logistics.tracks.length === 0" :description="$tt('暂无物流轨迹')" :image-size="60" />
              <el-timeline v-else>
                <el-timeline-item
                  v-for="(track, idx) in (row.logistics.tracks || [])"
                  :key="idx"
                  :timestamp="track.time"
                  placement="top"
                  :type="idx === 0 ? 'primary' : 'default'"
                  :hollow="idx !== 0"
                >
                  <div>{{ track.context }}</div>
                </el-timeline-item>
              </el-timeline>
            </div>
          </template>
        </el-table-column>
        <!-- 动态列渲染：列顺序由列设置拖拽配置驱动（sortedColumns），单元格按 col.type 分支渲染 -->
        <template v-for="col in visibleSortedColumns" :key="col.prop">
          <el-table-column
            :prop="col.prop"
            :label="$tf(col.label)"
            :width="col.width"
            :min-width="col.minWidth"
            :align="col.align || 'left'"
            :show-overflow-tooltip="!!col.tooltip"
            :fixed="getColFixed(col.prop)"
          >
            <template v-if="col.type" #default="{ row }">
              <el-link v-if="col.type === 'deliveryNo'" type="primary" @click="openLogisticsDrawer(row)">{{ row.deliveryNo }}</el-link>
              <template v-else-if="col.type === 'customer'">{{ row.customerName || row.contactPerson || '-' }}</template>
              <span v-else-if="col.type === 'trackingNo'" class="tracking-no">{{ row.trackingNo || '-' }}</span>
              <template v-else-if="col.type === 'carrier'">{{ row.carrier || '-' }}</template>
              <template v-else-if="col.type === 'channel'">
                <el-tooltip
                  v-if="row.logistics && row.logistics.channelName"
                  :content="row.logistics.quotaNote || ('由 ' + row.logistics.channelName + ' 提供轨迹')"
                  placement="top"
                >
                  <el-tag
                    size="small"
                    effect="plain"
                    :type="row.logistics.channel === '17TRACK' ? 'success' : 'info'"
                  >
                    {{ row.logistics.channelName }}
                  </el-tag>
                </el-tooltip>
                <span v-else class="empty-text">-</span>
              </template>
              <template v-else-if="col.type === 'status'">
                <el-tag
                  v-if="row.logistics && row.logistics.statusCode"
                  :type="statusTagType(row.logistics.statusCode)"
                  effect="dark"
                  size="small"
                  round
                >
                  <el-icon class="status-icon"><component :is="statusIcon(row.logistics.statusCode)" /></el-icon>
                  {{ $dt(row.logistics.statusText || '未知') }}
                </el-tag>
                <el-tag v-else type="info" size="small" effect="plain">{{ $tt("未查询") }}</el-tag>
              </template>
              <template v-else-if="col.type === 'latestTrack'">
                <span v-if="row.logistics && row.logistics.tracks && row.logistics.tracks.length > 0" class="latest-track">
                  {{ row.logistics.tracks[0].time }} · {{ row.logistics.tracks[0].context }}
                </span>
                <span v-else class="empty-text">-</span>
              </template>
            </template>
          </el-table-column>
        </template>
      </el-table>

      <el-empty v-if="!loading && tableData.length === 0" :description="$tt('暂无在途出库单')" />
    </el-card>

    <!-- 物流详情抽屉 -->
    <LogisticsTracker
      v-model="drawerVisible"
      :tracking-no="drawerTrackingNo"
      :carrier="drawerCarrier"
      :phone="drawerPhone"
      :delivery-id="drawerDeliveryId"
    />

    <!-- 列设置抽屉 -->
    <ColumnSettingDrawer v-model="columnSettingVisible" :page-key="'LogisticsDashboard'" :columns="columns" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft, Search, Refresh, Van, List, View,
  Box, MapLocation, CircleCheck, Warning, Clock,
  WarningFilled, Setting
} from '@element-plus/icons-vue'
import LogisticsTracker from '@/components/LogisticsTracker.vue'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { listInTransit, batchQueryLogistics, getRefreshConfig, getCredentials } from '@/api/logistics/logistics'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('sale:logistics-dashboard')

const router = useRouter()

// 列定义（列设置 + 动态渲染用）：type 标记单元格渲染分支；_ 前缀为无对应字段的纯展示列
const columns = [
  { prop: 'deliveryNo', label: '出库单号', minWidth: 130, type: 'deliveryNo' },
  { prop: 'customerName', label: '客户', minWidth: 140, tooltip: true, type: 'customer' },
  { prop: 'trackingNo', label: '物流单号', minWidth: 160, type: 'trackingNo' },
  { prop: 'carrier', label: '承运商', width: 100, type: 'carrier' },
  { prop: '_channel', label: '查询通道', width: 110, align: 'center', type: 'channel' },
  { prop: '_status', label: '当前状态', width: 120, align: 'center', type: 'status' },
  { prop: '_latestTrack', label: '最新轨迹', minWidth: 280, tooltip: true, type: 'latestTrack' },
  { prop: 'deliveryDate', label: '出库日期', width: 110 }
]
const { columnSettingVisible, openColumnSetting, isColVisible, getColFixed, configVersion, sortedColumns, onHeaderDragend, applySavedWidths } = useColumnSetting('LogisticsDashboard', columns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))
// 列宽持久化：表格带 border 才允许拖拽，拖完的宽度写入本页列配置，下次打开自动套用
const tableRef = ref(null)

const loading = ref(false)
const keyword = ref('')
const lastRefreshTime = ref('')

// ===== 首次使用引导 =====
const showGuide = ref(false)
const logisticsEnabled = ref(true)

function goToConfig() {
  router.push('/system/settings?group=business&item=logistics-config')
}

// 自动刷新相关
const autoRefresh = ref(true)
const refreshInterval = ref(30)  // 秒
const maxItems = ref(50)
let refreshTimer = null

// 状态筛选
const currentFilter = ref('')

// 抽屉
const drawerVisible = ref(false)
const drawerTrackingNo = ref('')
const drawerCarrier = ref('')
const drawerPhone = ref('')
const drawerDeliveryId = ref(null)

// 状态统计卡片
const statusCards = computed(() => {
  const counts = { '1': 0, '0': 0, '5': 0, '3': 0, 'error': 0 }
  tableData.value.forEach(row => {
    if (!row.logistics) return
    const code = row.logistics.statusCode
    if (code === '1') counts['1']++
    else if (code === '0' || code === '8' || code === '10') counts['0']++
    else if (code === '5') counts['5']++
    else if (code === '3') counts['3']++
    else if (code === '2' || code === '4' || code === '6') counts.error++
  })
  return [
    { code: '1', label: '已揽收', count: counts['1'], color: 'primary', icon: Box },
    { code: '0', label: '在途', count: counts['0'], color: 'warning', icon: Van },
    { code: '5', label: '派送中', count: counts['5'], color: 'info', icon: MapLocation },
    { code: '3', label: '已签收', count: counts['3'], color: 'success', icon: CircleCheck },
    { code: 'error', label: '异常', count: counts.error, color: 'danger', icon: Warning }
  ]
})

const currentFilterText = computed(() => {
  if (!currentFilter.value) return ''
  const card = statusCards.value.find(c => c.code === currentFilter.value)
  return card ? card.label : ''
})

const tableData = computed(() => {
  let list = rawData.value
  // 状态筛选
  if (currentFilter.value) {
    list = list.filter(row => {
      if (!row.logistics) return false
      const code = row.logistics.statusCode
      if (currentFilter.value === 'error') {
        return ['2', '4', '6'].includes(code)
      }
      return code === currentFilter.value
    })
  }
  return list
})

// 原始数据（不经过状态筛选，用于统计卡片）
const rawData = ref([])

// ============= 生命周期 =============

// 2026-10-09：从「销售预警 → 去处理」（物流三类）跳入时带条件（kw = 出库单号 / 物流单号 / 客户）
const _route = useRoute()
if (_route.query.kw) keyword.value = String(_route.query.kw)

onMounted(async () => {
  await loadRefreshConfig()
  await checkCredentialsStatus()
  await loadInTransit()
  startAutoRefresh()
})

// 检查快递100授权状态，未配置时显示引导
async function checkCredentialsStatus() {
  try {
    const res = await getCredentials()
    const data = res.data !== undefined ? res.data : res
    logisticsEnabled.value = data.enabled !== false
    // 未配置授权码时显示引导
    if (!data.configured) {
      showGuide.value = true
    }
  } catch (e) {
    console.warn('检查授权状态失败:', e.message)
  }
}

onUnmounted(() => {
  stopAutoRefresh()
})

// ============= 数据加载 =============

async function loadRefreshConfig() {
  try {
    const res = await getRefreshConfig()
    const data = res.data !== undefined ? res.data : res
    if (data) {
      refreshInterval.value = data.refreshInterval || 30
      maxItems.value = data.maxItems || 50
      autoRefresh.value = data.autoRefresh !== false
    }
  } catch (e) {
    console.warn('加载看板配置失败，使用默认值:', e.message)
  }
}

async function loadInTransit() {
  loading.value = true
  try {
    const res = await listInTransit({ keyword: keyword.value, limit: maxItems.value })
    const list = res.data !== undefined ? res.data : res
    rawData.value = Array.isArray(list) ? list : []
    // 初始化每个 row 的 logistics 字段
    rawData.value.forEach(row => { row.logistics = null })
    // 同步更新时间
    lastRefreshTime.value = formatNow()
    // 异步批量查询物流（分批，每批最多20单，避免频率限制）
    await batchQueryLogisticsAsync()
  } catch (e) {
    console.error('加载在途出库单失败:', e)
    ElMessage.error(tt('加载在途出库单失败：') + (e.message || tt('未知错误')))
  } finally {
    loading.value = false
    // 数据到位后再套一次已保存的列宽（表格重建后列对象是新的）
    nextTick(() => applySavedWidths(tableRef))
  }
}

/**
 * 批量查询物流，分批避免频率限制
 */
async function batchQueryLogisticsAsync() {
  const batches = []
  const items = rawData.value
    .filter(r => r.trackingNo)
    .map(r => ({
      trackingNo: r.trackingNo,
      carrier: r.carrier || '',
      phone: r.contactPhone || '',
      deliveryId: r.deliveryId
    }))

  // 每批最多 20 单
  for (let i = 0; i < items.length; i += 20) {
    batches.push(items.slice(i, i + 20))
  }

  for (const batch of batches) {
    try {
      const res = await batchQueryLogistics(
        batch.map(b => ({ trackingNo: b.trackingNo, carrier: b.carrier, phone: b.phone }))
      )
      const results = res.data !== undefined ? res.data : res
      if (Array.isArray(results)) {
        results.forEach((track, idx) => {
          const deliveryId = batch[idx]?.deliveryId
          if (deliveryId) {
            const row = rawData.value.find(r => r.deliveryId === deliveryId)
            if (row) {
              row.logistics = track
            }
          }
        })
      }
    } catch (e) {
      console.warn('批量查询物流失败:', e)
    }
  }
}

async function manualRefresh() {
  await loadInTransit()
  ElMessage.success(tt('已刷新物流信息'))
}

// ============= 自动刷新 =============

function startAutoRefresh() {
  stopAutoRefresh()
  if (!autoRefresh.value) return
  const interval = Math.max(refreshInterval.value, 10) * 1000
  refreshTimer = setInterval(async () => {
    console.log('[物流看板] 自动刷新触发')
    await loadInTransit()
  }, interval)
}

function stopAutoRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

function onAutoRefreshChange(val) {
  if (val) {
    startAutoRefresh()
    ElMessage.success(`${tt("已开启自动刷新（每 ")}${refreshInterval.value}${tt(" 秒）")}`)
  } else {
    stopAutoRefresh()
    ElMessage.info(tt('已暂停自动刷新'))
  }
}

// ============= 交互 =============

function filterByStatus(code) {
  currentFilter.value = currentFilter.value === code ? '' : code
}

function clearStatusFilter() {
  currentFilter.value = ''
}

function expandRow(row, column, event) {
  // 行点击展开（el-table type="expand" 触发）
}

function rowClassName({ row }) {
  if (!row.logistics) return ''
  const code = row.logistics.statusCode
  if (['2', '4', '6'].includes(code)) return 'row-error'
  if (code === '3') return 'row-success'
  return ''
}

function openLogisticsDrawer(row) {
  drawerTrackingNo.value = row.trackingNo
  drawerCarrier.value = row.carrier || ''
  drawerPhone.value = row.contactPhone || ''
  drawerDeliveryId.value = row.deliveryId ?? null
  drawerVisible.value = true
}

// ============= 工具函数 =============

function formatNow() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function statusTagType(state) {
  if (!state) return 'info'
  const map = {
    '1': 'primary',
    '0': 'warning',
    '8': 'warning',
    '10': 'warning',
    '5': 'info',
    '3': 'success',
    '2': 'danger',
    '4': 'danger',
    '6': 'danger',
    '7': 'info'
  }
  return map[state] || 'info'
}

function statusIcon(state) {
  if (!state) return Clock
  const map = {
    '1': Box,
    '0': Van,
    '8': Van,
    '10': Box,
    '5': MapLocation,
    '3': CircleCheck,
    '2': Warning,
    '4': Warning,
    '6': Warning,
    '7': Refresh
  }
  return map[state] || Search
}
</script>

<style scoped>
.logistics-dashboard {
  padding: 16px;
  background: #f5f7fa;
  min-height: 100vh;
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  padding: 12px 20px;
  border-radius: 8px;
  margin-bottom: 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.dashboard-title {
  margin: 0;
  font-size: 18px;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 6px;
}

.title-icon {
  color: #409eff;
  font-size: 22px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.search-input {
  width: 280px;
}

/* 状态统计卡片 */
.status-cards {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.status-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  transition: all 0.2s;
  border-left: 4px solid #909399;
}

.status-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.status-card.primary { border-left-color: #409eff; }
.status-card.warning { border-left-color: #e6a23c; }
.status-card.info { border-left-color: #909399; }
.status-card.success { border-left-color: #67c23a; }
.status-card.danger { border-left-color: #f56c6c; }

.card-icon {
  font-size: 28px;
  padding: 8px;
  border-radius: 50%;
  background: #f5f7fa;
}

.status-card.primary .card-icon { color: #409eff; }
.status-card.warning .card-icon { color: #e6a23c; }
.status-card.info .card-icon { color: #909399; }
.status-card.success .card-icon { color: #67c23a; }
.status-card.danger .card-icon { color: #f56c6c; }

.card-count {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  line-height: 1.2;
}

.card-label {
  font-size: 12px;
  color: #909399;
}

/* 主表格卡片 */
.dashboard-card {
  border-radius: 8px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.expand-content {
  padding: 16px 24px;
  background: #fafafa;
}

.tracking-no {
  font-family: 'Consolas', 'Monaco', monospace;
  color: #303133;
  font-weight: 500;
}

.status-icon {
  margin-right: 4px;
  vertical-align: middle;
}

.latest-track {
  color: #606266;
  font-size: 12px;
}

.empty-text {
  color: #c0c4cc;
}

/* 行状态颜色 */
:deep(.row-error) {
  background: #fef0f0 !important;
}

:deep(.row-error:hover > td) {
  background: #fde2e2 !important;
}

:deep(.row-success) {
  background: #f0f9eb !important;
}

:deep(.row-success:hover > td) {
  background: #e4f1da !important;
}

/* 响应式 */
@media (max-width: 1200px) {
  .status-cards {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 768px) {
  .status-cards {
    grid-template-columns: repeat(2, 1fr);
  }
  .header-right {
    flex-wrap: wrap;
  }
  .search-input {
    width: 100%;
  }
}

/* ===== 首次使用引导样式 ===== */
.setup-guide-alert {
  margin-bottom: 16px;
}
.setup-guide-alert :deep(.el-alert__content) {
  width: 100%;
}
.guide-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
}
.guide-body {
  margin-top: 12px;
}
.guide-steps {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  margin-bottom: 16px;
}
.guide-step {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  flex: 1;
  min-width: 260px;
}
.step-num {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #e6a23c;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 14px;
  flex-shrink: 0;
}
.step-text {
  font-size: 13px;
  line-height: 1.6;
  color: #606266;
}
.step-text strong {
  color: #303133;
}
.guide-actions {
  display: flex;
  gap: 12px;
  margin-top: 8px;
}
</style>
