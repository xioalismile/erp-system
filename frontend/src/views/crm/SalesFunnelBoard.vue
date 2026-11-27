<template>
  <div class="erp-page">
    <!-- 顶部统计 -->
    <el-card shadow="never" class="header-card">
      <div class="header-row">
        <div class="header-stat">
          <span class="label">{{ $tt("商机总数") }}</span>
          <span class="value">{{ totalOpportunities }}</span>
        </div>
        <div class="header-stat">
          <span class="label">{{ $tt("已成交") }}</span>
          <span class="value success">{{ wonCount }}</span>
        </div>
        <div class="header-stat">
          <span class="label">{{ $tt("未关闭") }}</span>
          <span class="value primary">{{ totalOpportunities - closedCount }}</span>
        </div>
        <div class="header-stat">
          <span class="label">{{ $tt("预估金额合计") }}</span>
          <span class="value warning">¥{{ formatMoney(totalEstimatedAmount) }}</span>
        </div>
        <div class="header-stat">
          <span class="label">{{ $tt("逾期跟进") }}</span>
          <span class="value danger">{{ overdueFollowUps }}</span>
        </div>
        <div style="flex:1;"></div>
        <el-button v-permission="perm('refresh')" @click="loadData" :loading="loading">{{ $tt("刷新") }}</el-button>
        <el-button type="primary" v-permission="perm('view')" @click="$router.push('/crm/opportunity')">{{ $tt("进入商机管理") }}</el-button>
      </div>
    </el-card>

    <!-- 看板视图 -->
    <div class="kanban-wrap" v-loading="loading">
      <div v-for="col in stages" :key="col.stage" class="kanban-col">
        <div class="kanban-header" :style="{ borderTopColor: col.color }">
          <div class="col-title">
            <el-tag :color="col.color" effect="dark" size="small">{{ col.stage }}</el-tag>
            <span class="col-count">{{ col.list.length }} 个</span>
          </div>
          <div class="col-amount">¥{{ formatMoney(col.totalAmount) }}</div>
        </div>
        <div class="kanban-body">
          <div v-for="opp in col.list" :key="opp.opportunityId" class="opp-card"
               @click="handleViewDetail(opp)">
            <div class="opp-name">{{ opp.opportunityName }}</div>
            <div class="opp-customer">{{ opp.customerName || '-' }}</div>
            <div class="opp-row">
              <span class="opp-amount">¥{{ formatMoney(opp.estimatedAmount) }}</span>
              <span class="opp-prob">{{ opp.probability || 0 }}%</span>
            </div>
            <div class="opp-footer">
              <el-tag v-if="isOverdue(opp.nextFollowUpDate)" type="danger" size="small">{{ $tt("逾期跟进") }}</el-tag>
              <span v-if="opp.nextFollowUpDate" class="opp-date">下次: {{ opp.nextFollowUpDate }}</span>
              <span v-else class="opp-date">{{ $tt("未计划跟进") }}</span>
            </div>
          </div>
          <el-empty v-if="col.list.length === 0" :description="$tt('暂无商机')" :image-size="60" />
        </div>
      </div>
    </div>

    <!-- 商机详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="$tf('商机详情')" width="640px">
      <el-descriptions v-if="currentOpp" :column="2" border>
        <el-descriptions-item :label="$tf('商机编号')">{{ currentOpp.opportunityNo }}</el-descriptions-item>
        <el-descriptions-item :label="$tf('商机名称')">{{ currentOpp.opportunityName }}</el-descriptions-item>
        <el-descriptions-item :label="$tf('客户名称')">{{ currentOpp.customerName }}</el-descriptions-item>
        <el-descriptions-item :label="$tf('联系人')">{{ currentOpp.contactPerson || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$tf('预估金额')">¥{{ formatMoney(currentOpp.estimatedAmount) }}</el-descriptions-item>
        <el-descriptions-item :label="$tf('成交概率')">{{ currentOpp.probability || 0 }}%</el-descriptions-item>
        <el-descriptions-item :label="$tf('销售阶段')">
          <el-tag size="small">{{ currentOpp.salesStage }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="$tf('预计成交')">{{ currentOpp.expectedCloseDate || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$tf('下次跟进')">{{ currentOpp.nextFollowUpDate || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$tf('最后跟进')">{{ currentOpp.lastFollowUpDate || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$tf('竞争对手')" :span="2">{{ currentOpp.competitor || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$tf('备注')" :span="2">{{ currentOpp.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button v-permission="perm('dlgClose')" @click="detailVisible = false">{{ $tt("关闭") }}</el-button>
        <el-button type="primary" v-permission="perm('edit')" @click="goEdit">{{ $tt("编辑商机") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { listOpportunity, opportunityDashboard, getOpportunity } from '@/api/crm/opportunity'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('crm:funnel')

const router = useRouter()
const loading = ref(false)
const totalOpportunities = ref(0)
const wonCount = ref(0)
const closedCount = ref(0)
const totalEstimatedAmount = ref(0)
const overdueFollowUps = ref(0)

const STAGE_DEFS = [
  { stage: '初步接触', color: '#409EFF' },
  { stage: '需求确认', color: '#67C23A' },
  { stage: '方案报价', color: '#E6A23C' },
  { stage: '商务谈判', color: '#F56C6C' },
  { stage: '成交',     color: '#909399' },
  { stage: '已关闭',   color: '#C0C4CC' }
]

const stages = ref(STAGE_DEFS.map(s => ({ ...s, list: [], totalAmount: 0 })))

const detailVisible = ref(false)
const currentOpp = ref(null)

function formatMoney(num) {
  if (num === null || num === undefined) return '0.00'
  return Number(num).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function isOverdue(dateStr) {
  if (!dateStr) return false
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const d = new Date(dateStr)
  d.setHours(0, 0, 0, 0)
  return d < today
}

async function loadData() {
  loading.value = true
  try {
    // 并行加载：仪表盘统计 + 全量商机（取前 500 条作为看板数据）
    // 注意：request 响应拦截器已把 {code,message,data} 解包，返回值就是业务数据本身
    const [dashRes, listRes] = await Promise.all([
      opportunityDashboard().catch(() => ({})),
      listOpportunity({ current: 1, size: 500 })
    ])

    const dash = dashRes || {}
    totalOpportunities.value = dash.totalOpportunities || 0
    wonCount.value = dash.wonCount || 0
    closedCount.value = dash.closedCount || 0
    totalEstimatedAmount.value = dash.totalEstimatedAmount || 0
    overdueFollowUps.value = dash.overdueFollowUps || 0

    const allOpps = listRes?.records || []
    // 按销售阶段分桶
    const buckets = {}
    STAGE_DEFS.forEach(s => { buckets[s.stage] = [] })
    allOpps.forEach(opp => {
      const stage = opp.salesStage || '初步接触'
      if (buckets[stage]) {
        buckets[stage].push(opp)
      } else {
        // 未知阶段归入"已关闭"
        buckets['已关闭'].push(opp)
      }
    })

    stages.value = STAGE_DEFS.map(s => {
      const list = buckets[s.stage] || []
      const totalAmount = list.reduce(
        (sum, o) => sum + (Number(o.estimatedAmount) || 0), 0
      )
      return { ...s, list, totalAmount }
    })
  } catch (e) {
    ElMessage.error(tt('加载销售漏斗失败'))
  } finally {
    loading.value = false
  }
}

async function handleViewDetail(opp) {
  try {
    const res = await getOpportunity(opp.opportunityId)
    currentOpp.value = res || opp
    detailVisible.value = true
  } catch (e) {
    currentOpp.value = opp
    detailVisible.value = true
  }
}

function goEdit() {
  if (!currentOpp.value) return
  detailVisible.value = false
  router.push('/crm/opportunity')
}

onMounted(loadData)
</script>

<style scoped>
.header-card { margin-bottom: 12px; }
.header-row { display: flex; align-items: center; gap: 32px; flex-wrap: wrap; }
.header-stat { display: flex; flex-direction: column; }
.header-stat .label { color: #909399; font-size: 12px; }
.header-stat .value { font-size: 22px; font-weight: 600; color: #303133; margin-top: 2px; }
.header-stat .value.success { color: #67C23A; }
.header-stat .value.primary { color: #409EFF; }
.header-stat .value.warning { color: #E6A23C; }
.header-stat .value.danger  { color: #F56C6C; }

.kanban-wrap {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding-bottom: 12px;
  min-height: calc(100vh - 240px);
}
.kanban-col {
  flex: 0 0 280px;
  background: #F5F7FA;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  border-top: 3px solid #409EFF;
}
.kanban-header {
  padding: 12px;
  background: #fff;
  border-bottom: 1px solid #EBEEF5;
  border-radius: 8px 8px 0 0;
}
.col-title { display: flex; align-items: center; gap: 8px; }
.col-count { color: #909399; font-size: 12px; }
.col-amount { color: #E6A23C; font-weight: 600; margin-top: 4px; font-size: 14px; }
.kanban-body {
  flex: 1;
  padding: 8px;
  overflow-y: auto;
  max-height: calc(100vh - 320px);
}
.opp-card {
  background: #fff;
  padding: 10px 12px;
  border-radius: 6px;
  margin-bottom: 8px;
  cursor: pointer;
  border-left: 3px solid #409EFF;
  transition: box-shadow 0.2s;
}
.opp-card:hover {
  box-shadow: 0 2px 8px rgba(0,0,0,0.12);
}
.opp-name {
  font-weight: 500;
  color: #303133;
  font-size: 14px;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.opp-customer {
  color: #606266;
  font-size: 12px;
  margin-bottom: 6px;
}
.opp-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.opp-amount { color: #E6A23C; font-weight: 500; font-size: 13px; }
.opp-prob { color: #909399; font-size: 12px; }
.opp-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11px;
  color: #C0C4CC;
}
.opp-date { font-size: 11px; }
</style>
