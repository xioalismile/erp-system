<template>
  <div class="report-container">
    <!-- 顶部页面标题 -->
    <div class="page-header">
      <h2 class="page-title">{{ $tt("库存报表中心") }}</h2>
      <p class="page-subtitle">{{ $tt("库存周转率、安全库存预警、批次库龄分析及缺料分析") }}</p>
    </div>

    <!-- 全局筛选栏：日期范围 + 关键字搜索 -->
    <div class="filter-bar" style="margin-bottom: 12px; display: flex; gap: 8px; align-items: center; flex-wrap: wrap;">
      <el-date-picker v-model="dateRange" type="daterange" :range-separator="$tt('至')"
        :start-placeholder="$tt('开始日期')" :end-placeholder="$tt('结束日期')"
        value-format="YYYY-MM-DD" unlink-panels clearable style="width: 280px" />
      <el-input v-model="keyword" :placeholder="$tt('搜索关键字（物料编码/名称/批次）')"
        clearable style="width: 240px" @keyup.enter="loadAll" />
      <el-button type="primary" :loading="loadingAll" @click="loadAll">
        <el-icon><Search /></el-icon>查询
      </el-button>
      <el-button @click="resetFilter">{{ $tt("重置") }}</el-button>
    </div>

    <!-- 报表1：库存周转率（KPI卡片 + 表格） -->
    <el-card shadow="never" class="report-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $tt("库存周转率") }}</span>
          <div>
            <el-button size="small" type="primary" plain @click="openDetail('turnover')">
              <el-icon><View /></el-icon> 查看明细
            </el-button>
            <el-button type="primary" link :loading="loading.turnover" @click="loadTurnover">{{ $tt("刷新") }}</el-button>
          </div>
        </div>
      </template>
      <!-- KPI卡片行：库存总值/物料数/呆滞数/呆滞值 -->
      <el-row :gutter="12" class="kpi-row">
        <el-col :span="6" v-for="kpi in turnoverKpis" :key="kpi.label">
          <div class="kpi-card" :style="{ background: kpi.bg }">
            <div class="kpi-label">{{ $tf(kpi.label) }}</div>
            <div class="kpi-value" :style="{ color: kpi.color }">{{ kpi.value }}</div>
            <div class="kpi-hint">{{ $tt(kpi.hint) }}</div>
          </div>
        </el-col>
      </el-row>
      <!-- 表格：物料/数量/库存值/最后出库/呆滞标记 -->
      <el-table :data="turnoverItems" stripe border size="small" max-height="320" style="margin-top: 12px;">
        <el-table-column prop="materialCode" :label="$tf('物料编码')" min-width="140" show-overflow-tooltip />
        <el-table-column prop="materialName" :label="$tf('物料名称')" min-width="180" show-overflow-tooltip />
        <el-table-column :label="$tf('数量')" width="120" align="right">
          <template #default="{ row }">{{ Number(row.quantity || 0).toLocaleString('zh-CN') }}</template>
        </el-table-column>
        <el-table-column :label="$tf('库存值')" width="140" align="right">
          <template #default="{ row }">¥{{ formatMoney(row.inventoryValue) }}</template>
        </el-table-column>
        <el-table-column :label="$tf('最后出库日')" width="120" align="center">
          <template #default="{ row }">{{ row.lastOutDate || '-' }}</template>
        </el-table-column>
        <el-table-column :label="$tf('呆滞天数')" width="100" align="right">
          <template #default="{ row }">{{ row.daysSinceLastOut != null ? row.daysSinceLastOut : '-' }}</template>
        </el-table-column>
        <el-table-column :label="$tf('呆滞标记')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isSlow ? 'warning' : 'success'" size="small">
              {{ row.isSlow ? '呆滞' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 报表2：安全库存预警（KPI卡片 + 表格） -->
    <el-card shadow="never" class="report-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $tt("安全库存预警") }}</span>
          <div>
            <el-button size="small" type="primary" plain @click="openDetail('safety-warning')">
              <el-icon><View /></el-icon> 查看明细
            </el-button>
            <el-button type="primary" link :loading="loading.safety" @click="loadSafetyWarning">{{ $tt("刷新") }}</el-button>
          </div>
        </div>
      </template>
      <!-- KPI卡片行：低于安全库存数/超储数 -->
      <el-row :gutter="12" class="kpi-row">
        <el-col :span="12">
          <div class="kpi-card" style="background: #FEF0F0;">
            <div class="kpi-label">{{ $tt("低于安全库存数") }}</div>
            <div class="kpi-value" style="color: #F56C6C;">{{ safetyData.belowMinCount || 0 }}</div>
            <div class="kpi-hint">{{ $tt("需要补货的物料数量") }}</div>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="kpi-card" style="background: #FDF6EC;">
            <div class="kpi-label">{{ $tt("超储数") }}</div>
            <div class="kpi-value" style="color: #E6A23C;">{{ safetyData.aboveMaxCount || 0 }}</div>
            <div class="kpi-hint">{{ $tt("高于最大库存的物料数量") }}</div>
          </div>
        </el-col>
      </el-row>
      <!-- 表格：物料/当前库存/安全库存/缺口/预警级别（分页签区分低于/超储） -->
      <el-tabs v-model="safetyTab" style="margin-top: 8px;">
        <el-tab-pane :label="$tf('低于安全库存')" name="below">
          <el-table :data="safetyData.belowMin" stripe border size="small" max-height="320">
            <el-table-column prop="materialCode" :label="$tf('物料编码')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="materialName" :label="$tf('物料名称')" min-width="180" show-overflow-tooltip />
            <el-table-column :label="$tf('当前库存')" width="120" align="right">
              <template #default="{ row }">{{ Number(row.currentStock || 0).toLocaleString('zh-CN') }}</template>
            </el-table-column>
            <el-table-column :label="$tf('安全库存')" width="120" align="right">
              <template #default="{ row }">{{ Number(row.minStock || 0).toLocaleString('zh-CN') }}</template>
            </el-table-column>
            <el-table-column :label="$tf('缺口')" width="120" align="right">
              <template #default="{ row }">
                <span style="color:#F56C6C; font-weight: 600;">{{ Number(row.shortage || 0).toLocaleString('zh-CN') }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('预警级别')" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="warningTagType(row.warningLevel)" size="small">{{ row.warningLevel || '-' }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane :label="$tf('超储物料')" name="above">
          <el-table :data="safetyData.aboveMax" stripe border size="small" max-height="320">
            <el-table-column prop="materialCode" :label="$tf('物料编码')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="materialName" :label="$tf('物料名称')" min-width="180" show-overflow-tooltip />
            <el-table-column :label="$tf('当前库存')" width="120" align="right">
              <template #default="{ row }">{{ Number(row.currentStock || 0).toLocaleString('zh-CN') }}</template>
            </el-table-column>
            <el-table-column :label="$tf('安全库存')" width="120" align="right">
              <template #default="{ row }">{{ Number(row.minStock || 0).toLocaleString('zh-CN') }}</template>
            </el-table-column>
            <el-table-column :label="$tf('缺口')" width="120" align="right">
              <template #default="{ row }">{{ Number(row.shortage || 0).toLocaleString('zh-CN') }}</template>
            </el-table-column>
            <el-table-column :label="$tf('预警级别')" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="warningTagType(row.warningLevel)" size="small">{{ row.warningLevel || '-' }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 报表3：批次库龄分析（KPI卡片 + 表格） -->
    <el-card shadow="never" class="report-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $tt("批次库龄分析") }}</span>
          <div>
            <el-button size="small" type="primary" plain @click="openDetail('batch-aging')">
              <el-icon><View /></el-icon> 查看明细
            </el-button>
            <el-button type="primary" link :loading="loading.batchAging" @click="loadBatchAging">{{ $tt("刷新") }}</el-button>
          </div>
        </div>
      </template>
      <!-- KPI卡片行：总批次数/过期数/临期数 -->
      <el-row :gutter="12" class="kpi-row">
        <el-col :span="8">
          <div class="kpi-card" style="background: #ECF5FF;">
            <div class="kpi-label">{{ $tt("总批次数") }}</div>
            <div class="kpi-value" style="color: var(--erp-primary, #409EFF);">{{ batchAgingData.totalBatches || 0 }}</div>
            <div class="kpi-hint">{{ $tt("在库批次总数") }}</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="kpi-card" style="background: #FEF0F0;">
            <div class="kpi-label">{{ $tt("过期数") }}</div>
            <div class="kpi-value" style="color: #F56C6C;">{{ batchAgingData.expiredCount || 0 }}</div>
            <div class="kpi-hint">{{ $tt("已超过保质期的批次") }}</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="kpi-card" style="background: #FDF6EC;">
            <div class="kpi-label">{{ $tt("临期数") }}</div>
            <div class="kpi-value" style="color: #E6A23C;">{{ batchAgingData.nearExpiryCount || 0 }}</div>
            <div class="kpi-hint">{{ $tt("临近保质期的批次") }}</div>
          </div>
        </el-col>
      </el-row>
      <!-- 表格：批次号/物料/数量/入库日/库龄/到期日/预警级别 -->
      <el-table :data="batchAgingData.items" stripe border size="small" max-height="360" style="margin-top: 12px;">
        <el-table-column prop="batchNo" :label="$tf('批次号')" width="140" show-overflow-tooltip />
        <el-table-column :label="$tf('物料')" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.materialName || row.materialId }}</template>
        </el-table-column>
        <el-table-column :label="$tf('数量')" width="100" align="right">
          <template #default="{ row }">{{ Number(row.quantity || 0).toLocaleString('zh-CN') }}</template>
        </el-table-column>
        <el-table-column :label="$tf('入库日')" width="110" align="center">
          <template #default="{ row }">{{ row.lastInDate || '-' }}</template>
        </el-table-column>
        <el-table-column :label="$tf('库龄(天)')" width="100" align="right">
          <template #default="{ row }">{{ row.ageDays != null ? row.ageDays : '-' }}</template>
        </el-table-column>
        <el-table-column :label="$tf('到期日')" width="110" align="center">
          <template #default="{ row }">{{ row.expiryDate || '-' }}</template>
        </el-table-column>
        <el-table-column :label="$tf('预警级别')" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="warningTagType(row.warningLevel)" size="small">{{ row.warningLevel || '-' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 报表4：缺料分析（KPI卡片 + 表格） -->
    <el-card shadow="never" class="report-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $tt("缺料分析") }}</span>
          <div>
            <el-button size="small" type="primary" plain @click="openDetail('shortage')">
              <el-icon><View /></el-icon> 查看明细
            </el-button>
            <el-button type="primary" link :loading="loading.shortage" @click="loadShortage">{{ $tt("刷新") }}</el-button>
          </div>
        </div>
      </template>
      <!-- KPI卡片行：需求物料数/缺料数/工单数 -->
      <el-row :gutter="12" class="kpi-row">
        <el-col :span="8">
          <div class="kpi-card" style="background: #ECF5FF;">
            <div class="kpi-label">{{ $tt("需求物料数") }}</div>
            <div class="kpi-value" style="color: var(--erp-primary, #409EFF);">{{ shortageData.totalRequirements || 0 }}</div>
            <div class="kpi-hint">{{ $tt("存在需求的物料数") }}</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="kpi-card" style="background: #FEF0F0;">
            <div class="kpi-label">{{ $tt("缺料数") }}</div>
            <div class="kpi-value" style="color: #F56C6C;">{{ shortageData.shortageCount || 0 }}</div>
            <div class="kpi-hint">{{ $tt("当前库存不足的物料") }}</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="kpi-card" style="background: #F0F9EB;">
            <div class="kpi-label">{{ $tt("工单数") }}</div>
            <div class="kpi-value" style="color: #67C23A;">{{ shortageData.workOrderCount || 0 }}</div>
            <div class="kpi-hint">{{ $tt("关联工单数量") }}</div>
          </div>
        </el-col>
      </el-row>
      <!-- 表格：物料/需求量/当前库存/缺口 -->
      <el-table :data="shortageData.shortages" stripe border size="small" max-height="360" style="margin-top: 12px;">
        <el-table-column prop="materialCode" :label="$tf('物料编码')" min-width="140" show-overflow-tooltip />
        <el-table-column prop="materialName" :label="$tf('物料名称')" min-width="180" show-overflow-tooltip />
        <el-table-column :label="$tf('需求量')" width="120" align="right">
          <template #default="{ row }">{{ Number(row.requirement || 0).toLocaleString('zh-CN') }}</template>
        </el-table-column>
        <el-table-column :label="$tf('当前库存')" width="120" align="right">
          <template #default="{ row }">{{ Number(row.currentStock || 0).toLocaleString('zh-CN') }}</template>
        </el-table-column>
        <el-table-column :label="$tf('缺口')" width="120" align="right">
          <template #default="{ row }">
            <span style="color:#F56C6C; font-weight: 600;">{{ Number(row.shortage || 0).toLocaleString('zh-CN') }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$tf('建议')" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.suggestion || '-' }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 共享明细抽屉：根据 currentReportKey 动态切换列 -->
    <el-drawer
      v-model="detailDrawerVisible"
      :title="$tt(currentReportTitle) + ' - ' + $tt('明细数据')"
      direction="rtl"
      size="70%"
      :close-on-click-modal="false"
    >
      <div class="detail-drawer">
        <div class="detail-filter">
          <el-date-picker v-model="detailDateRange" type="daterange" :range-separator="$tt('至')"
            :start-placeholder="$tt('开始日期')" :end-placeholder="$tt('结束日期')"
            value-format="YYYY-MM-DD" unlink-panels clearable style="width: 280px" />
          <el-input v-model="detailKeyword" :placeholder="$tt('搜索关键字')" clearable
            style="width: 220px" @keyup.enter="loadDetail" />
          <el-button type="primary" :loading="detailLoading" @click="loadDetail">
            <el-icon><Search /></el-icon>查询
          </el-button>
          <el-button @click="resetDetail">{{ $tt("重置") }}</el-button>
          <el-button type="success" plain :loading="detailLoading" @click="handleExportDetail">
            <el-icon><Download /></el-icon>导出
          </el-button>
        </div>
        <el-table :data="detailData" v-loading="detailLoading" border stripe size="small"
          max-height="560" style="margin-top: 12px;">
          <template v-if="currentReportKey === 'turnover'">
            <el-table-column prop="materialCode" :label="$tf('物料编码')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="materialName" :label="$tf('物料名称')" min-width="180" show-overflow-tooltip />
            <el-table-column prop="outQty" :label="$tf('出库量')" width="110" align="right" />
            <el-table-column prop="inQty" :label="$tf('入库量')" width="110" align="right" />
            <el-table-column :label="$tf('平均库存')" align="right" width="120">
              <template #default="{ row }">{{ fmt(row.avgStock) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('周转率')" align="center" width="110">
              <template #default="{ row }">{{ pct(row.turnoverRate) }}</template>
            </el-table-column>
          </template>
          <template v-else-if="currentReportKey === 'safety-warning'">
            <el-table-column prop="materialCode" :label="$tf('物料编码')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="materialName" :label="$tf('物料名称')" min-width="180" show-overflow-tooltip />
            <el-table-column :label="$tf('当前库存')" align="right" width="120">
              <template #default="{ row }">{{ fmt(row.currentStock) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('安全库存')" align="right" width="120">
              <template #default="{ row }">{{ fmt(row.safetyStock) }}</template>
            </el-table-column>
            <el-table-column prop="status" :label="$tf('状态')" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === '紧急' ? 'danger' : (row.status === '预警' ? 'warning' : 'success')" size="small">
                  {{ $dt(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
          </template>
          <template v-else-if="currentReportKey === 'batch-aging'">
            <el-table-column prop="batchNo" :label="$tf('批次号')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="materialName" :label="$tf('物料')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="inboundDate" :label="$tf('入库日期')" width="120" />
            <el-table-column :label="$tf('库龄(天)')" align="right" width="110">
              <template #default="{ row }">
                <span :style="{ color: row.ageDays > 90 ? '#f56c6c' : (row.ageDays > 30 ? '#e6a23c' : '#67c23a') }">
                  {{ row.ageDays }}
                </span>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('数量')" align="right" width="110">
              <template #default="{ row }">{{ fmt(row.quantity) }}</template>
            </el-table-column>
          </template>
          <template v-else-if="currentReportKey === 'shortage'">
            <el-table-column prop="materialCode" :label="$tf('物料编码')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="materialName" :label="$tf('物料名称')" min-width="180" show-overflow-tooltip />
            <el-table-column :label="$tf('需求数量')" align="right" width="120">
              <template #default="{ row }">{{ fmt(row.demandQty) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('库存数量')" align="right" width="120">
              <template #default="{ row }">{{ fmt(row.stockQty) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('缺口')" align="right" width="110">
              <template #default="{ row }">
                <span style="color: #f56c6c; font-weight: 600;">{{ fmt(row.shortageQty) }}</span>
              </template>
            </el-table-column>
          </template>
        </el-table>
        <div class="detail-pagination">
          <el-pagination v-model:current-page="detailPage" v-model:page-size="detailSize"
            :page-sizes="[10, 20, 50, 100]" :total="detailTotal"
            layout="total, sizes, prev, pager, next, jumper" background
            @size-change="loadDetail" @current-change="loadDetail" />
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, View, Download } from '@element-plus/icons-vue'
import { tt } from '@/i18n'
// 库存报表相关 API（汇总 + 明细）
import {
  inventoryTurnover,
  inventorySafetyWarning,
  inventoryBatchAging,
  inventoryShortage,
  inventoryTurnoverDetails,
  inventorySafetyWarningDetails,
  inventoryBatchAgingDetails,
  inventoryShortageDetails
} from '@/api/report'
import { exportCsv } from '@/utils/csvExport'

// ============ 公共工具函数 ============
// 金额格式化（保留两位小数 + 千分位）
function formatMoney(num) {
  if (num === null || num === undefined || num === '') return '0.00'
  return Number(num).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 数量格式化（千分位，无小数）
function fmt(num) {
  if (num === null || num === undefined || num === '') return '0'
  return Number(num).toLocaleString('zh-CN')
}

// 百分比格式化（保留两位小数 + %）
function pct(num) {
  if (num === null || num === undefined || num === '') return '0.00%'
  return Number(num).toFixed(2) + '%'
}

// 预警级别 tag 颜色映射
// 已过期/缺货=danger，临期/严重=warning，关注/预警=info
function warningTagType(level) {
  if (!level) return 'info'
  const v = String(level)
  if (v.includes('已过期') || v.includes('缺货') || v === '过期') return 'danger'
  if (v.includes('临期') || v.includes('严重')) return 'warning'
  if (v.includes('关注') || v.includes('预警')) return 'info'
  return 'info'
}

// 各报表加载状态
const loading = reactive({ turnover: false, safety: false, batchAging: false, shortage: false })

// ============ 全局筛选栏 ============
const dateRange = ref([])
const keyword = ref('')
const loadingAll = ref(false)

// 构造全局查询参数（日期范围 + 关键字）
function buildParams() {
  const p = {}
  if (dateRange.value && dateRange.value.length === 2) {
    p.startDate = dateRange.value[0]
    p.endDate = dateRange.value[1]
  }
  if (keyword.value) p.keyword = keyword.value
  return p
}

// 重置全局筛选条件并重新加载
function resetFilter() {
  dateRange.value = []
  keyword.value = ''
  loadAll()
}

// 一键加载所有报表汇总数据
async function loadAll() {
  loadingAll.value = true
  try {
    await Promise.allSettled([loadTurnover(), loadSafetyWarning(), loadBatchAging(), loadShortage()])
  } finally {
    loadingAll.value = false
  }
}

// ============ 报表1：库存周转率 ============
const turnoverItems = ref([])
// KPI卡片配置：库存总值/物料数/呆滞数/呆滞值
const turnoverKpis = ref([
  { label: '库存总值', value: '¥0.00', hint: '所有物料库存价值合计', color: 'var(--erp-primary, #409EFF)', bg: '#ECF5FF' },
  { label: '物料数', value: '0', hint: '在库物料种类', color: '#67C23A', bg: '#F0F9EB' },
  { label: '呆滞数', value: '0', hint: '呆滞物料种类', color: '#E6A23C', bg: '#FDF6EC' },
  { label: '呆滞值', value: '¥0.00', hint: '呆滞物料价值合计', color: '#F56C6C', bg: '#FEF0F0' }
])

async function loadTurnover() {
  loading.turnover = true
  try {
    const data = await inventoryTurnover(buildParams())
    turnoverItems.value = data?.items || []
    turnoverKpis.value[0].value = '¥' + formatMoney(data?.totalInventoryValue || 0)
    turnoverKpis.value[1].value = String(data?.totalMaterialCount || 0)
    turnoverKpis.value[2].value = String(data?.slowMovingCount || 0)
    turnoverKpis.value[3].value = '¥' + formatMoney(data?.slowMovingValue || 0)
  } catch (e) {
    ElMessage.error(tt('加载库存周转率失败'))
  } finally {
    loading.turnover = false
  }
}

// ============ 报表2：安全库存预警 ============
const safetyData = reactive({
  belowMinCount: 0,
  belowMin: [],
  aboveMaxCount: 0,
  aboveMax: []
})
const safetyTab = ref('below')

async function loadSafetyWarning() {
  loading.safety = true
  try {
    const data = await inventorySafetyWarning(buildParams())
    safetyData.belowMinCount = data?.belowMinCount || 0
    safetyData.belowMin = data?.belowMin || []
    safetyData.aboveMaxCount = data?.aboveMaxCount || 0
    safetyData.aboveMax = data?.aboveMax || []
  } catch (e) {
    ElMessage.error(tt('加载安全库存预警失败'))
  } finally {
    loading.safety = false
  }
}

// ============ 报表3：批次库龄分析 ============
const batchAgingData = reactive({
  totalBatches: 0,
  expiredCount: 0,
  nearExpiryCount: 0,
  items: []
})

async function loadBatchAging() {
  loading.batchAging = true
  try {
    const data = await inventoryBatchAging(buildParams())
    batchAgingData.totalBatches = data?.totalBatches || 0
    batchAgingData.expiredCount = data?.expiredCount || 0
    batchAgingData.nearExpiryCount = data?.nearExpiryCount || 0
    batchAgingData.items = data?.items || []
  } catch (e) {
    ElMessage.error(tt('加载批次库龄分析失败'))
  } finally {
    loading.batchAging = false
  }
}

// ============ 报表4：缺料分析 ============
const shortageData = reactive({
  totalRequirements: 0,
  shortageCount: 0,
  workOrderCount: 0,
  shortages: []
})

async function loadShortage() {
  loading.shortage = true
  try {
    const data = await inventoryShortage(buildParams())
    shortageData.totalRequirements = data?.totalRequirements || 0
    shortageData.shortageCount = data?.shortageCount || 0
    shortageData.workOrderCount = data?.workOrderCount || 0
    shortageData.shortages = data?.shortages || []
  } catch (e) {
    ElMessage.error(tt('加载缺料分析失败'))
  } finally {
    loading.shortage = false
  }
}

// ============ 共享明细抽屉 ============
const detailDrawerVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref([])
const detailTotal = ref(0)
const detailPage = ref(1)
const detailSize = ref(20)
const detailKeyword = ref('')
const detailDateRange = ref([])
const currentReportKey = ref('')
const currentReportTitle = ref('')

// 报表 key -> 标题 映射
const reportTitleMap = {
  'turnover': '库存周转率',
  'safety-warning': '安全库存预警',
  'batch-aging': '批次库龄',
  'shortage': '缺料预警'
}

// 报表 key -> 明细 API 映射
const reportDetailApiMap = {
  'turnover': inventoryTurnoverDetails,
  'safety-warning': inventorySafetyWarningDetails,
  'batch-aging': inventoryBatchAgingDetails,
  'shortage': inventoryShortageDetails
}

// 打开明细抽屉：继承全局筛选条件
function openDetail(key) {
  currentReportKey.value = key
  currentReportTitle.value = reportTitleMap[key] || ''
  detailDateRange.value = dateRange.value && dateRange.value.length === 2 ? [...dateRange.value] : []
  detailKeyword.value = keyword.value || ''
  detailPage.value = 1
  detailSize.value = 20
  detailDrawerVisible.value = true
  loadDetail()
}

// 加载明细数据（兼容数组和分页对象两种返回结构）
async function loadDetail() {
  const api = reportDetailApiMap[currentReportKey.value]
  if (!api) return
  detailLoading.value = true
  try {
    const params = { page: detailPage.value, size: detailSize.value }
    if (detailDateRange.value && detailDateRange.value.length === 2) {
      params.startDate = detailDateRange.value[0]
      params.endDate = detailDateRange.value[1]
    }
    if (detailKeyword.value) params.keyword = detailKeyword.value
    const res = await api(params)
    if (Array.isArray(res)) {
      detailData.value = res
      detailTotal.value = res.length
    } else {
      detailData.value = res.records || []
      detailTotal.value = res.total || 0
    }
  } catch (e) {
    console.error('加载明细失败', e)
    ElMessage.error(tt('加载明细失败'))
    detailData.value = []
    detailTotal.value = 0
  } finally {
    detailLoading.value = false
  }
}

// 重置明细筛选条件
function resetDetail() {
  detailDateRange.value = []
  detailKeyword.value = ''
  detailPage.value = 1
  loadDetail()
}

// 明细抽屉导出列定义（与表格列一致）
const reportDetailColumnsMap = {
  'turnover': [
    { label: '物料编码', prop: 'materialCode' },
    { label: '物料名称', prop: 'materialName' },
    { label: '出库量', prop: 'outQty' },
    { label: '入库量', prop: 'inQty' },
    { label: '平均库存', prop: 'avgStock' },
    { label: '周转率', prop: 'turnoverRate' }
  ],
  'safety-warning': [
    { label: '物料编码', prop: 'materialCode' },
    { label: '物料名称', prop: 'materialName' },
    { label: '当前库存', prop: 'currentStock' },
    { label: '安全库存', prop: 'safetyStock' },
    { label: '状态', prop: 'status' }
  ],
  'batch-aging': [
    { label: '批次号', prop: 'batchNo' },
    { label: '物料', prop: 'materialName' },
    { label: '入库日期', prop: 'inboundDate' },
    { label: '库龄(天)', prop: 'ageDays' },
    { label: '数量', prop: 'quantity' }
  ],
  'shortage': [
    { label: '物料编码', prop: 'materialCode' },
    { label: '物料名称', prop: 'materialName' },
    { label: '需求数量', prop: 'demandQty' },
    { label: '库存数量', prop: 'stockQty' },
    { label: '缺口', prop: 'shortageQty' }
  ]
}

async function handleExportDetail() {
  const api = reportDetailApiMap[currentReportKey.value]
  const columns = reportDetailColumnsMap[currentReportKey.value]
  if (!api || !columns) { ElMessage.warning(tt('当前无可导出的报表数据')); return }
  try {
    detailLoading.value = true
    const params = { page: 1, size: 9999 }
    if (detailDateRange.value && detailDateRange.value.length === 2) {
      params.startDate = detailDateRange.value[0]
      params.endDate = detailDateRange.value[1]
    }
    if (detailKeyword.value) params.keyword = detailKeyword.value
    const res = await api(params)
    const rows = Array.isArray(res) ? res : (res.records || [])
    if (!rows.length) { ElMessage.warning(tt('没有可导出的数据')); return }
    exportCsv(columns, rows, currentReportTitle.value || '库存报表明细')
    ElMessage.success(`${tt("已导出 ")}${rows.length}${tt(" 条数据")}`)
  } catch (e) {
    console.error('导出失败', e)
    ElMessage.error(e.message || tt('导出失败'))
  } finally {
    detailLoading.value = false
  }
}

// ============ 生命周期 ============
onMounted(() => {
  loadAll()
})
</script>

<style scoped>
.report-container { padding: 20px; background: #f5f7fa; }
.page-header { margin-bottom: 16px; }
.page-title { margin: 0; font-size: 22px; font-weight: 600; color: #303133; }
.page-subtitle { margin: 6px 0 0; font-size: 13px; color: #909399; }

.report-card { margin-bottom: 16px; border-radius: 8px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-size: 16px; font-weight: 600; color: #303133; }

.kpi-row { margin-bottom: 4px; }
.kpi-card {
  border-radius: 8px;
  padding: 16px 18px;
  border: 1px solid #ebeef5;
}
.kpi-label { color: #909399; font-size: 13px; }
.kpi-value { font-size: 24px; font-weight: 600; margin: 4px 0; }
.kpi-hint { color: #C0C4CC; font-size: 12px; }

/* 明细抽屉 */
.detail-drawer { padding: 0 16px 16px; }
.detail-filter {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.detail-pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
