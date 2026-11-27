<template>
  <div class="erp-page">
    <!-- 我的驾驶舱（2026-10-07）：目标链 年→月→周→日 + 四级业绩 + 预警/奖金收口 -->
    <el-card shadow="never" style="margin-bottom:12px;">
      <CockpitPanel />
    </el-card>

    <!-- 顶部 KPI 卡片 -->
    <el-row :gutter="12">
      <el-col :span="6" v-for="kpi in kpiCards" :key="kpi.key">
        <el-card shadow="hover" class="kpi-card" :body-style="{ padding: '18px 20px' }">
          <div class="kpi-row">
            <div class="kpi-icon" :style="{ background: kpi.bg, color: kpi.color }">
              <el-icon :size="22"><component :is="kpi.icon" /></el-icon>
            </div>
            <div class="kpi-text">
              <div class="kpi-label">{{ $tf(kpi.label) }}</div>
              <div class="kpi-value" :style="{ color: kpi.color }">{{ kpi.value }}</div>
              <div class="kpi-hint">{{ $tt(kpi.hint) }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 中部图表 -->
    <el-row :gutter="12" style="margin-top:12px;">
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>{{ $tt("销售漏斗（商机阶段分布）") }}</span>
              <el-button link type="primary" v-permission="perm('view')" @click="$router.push('/crm/funnel')">{{ $tt("进入看板") }}</el-button>
            </div>
          </template>
          <div class="funnel-wrap">
            <div v-for="(stage, idx) in funnelStages" :key="stage.name" class="funnel-row">
              <div class="funnel-label">{{ stage.name }}</div>
              <div class="funnel-bar-bg">
                <div class="funnel-bar-fill"
                     :style="{ width: barWidth(stage.count) + '%', background: stage.color }">
                </div>
                <span class="funnel-count">{{ stage.count }} 个 · ¥{{ formatMoney(stage.amount) }}</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>
            <div class="card-header"><span>{{ $tt("客户 CRM 状态分布") }}</span></div>
          </template>
          <div class="status-list">
            <div v-for="row in customerStatusDist" :key="row.name" class="status-row">
              <el-tag :type="row.type" size="small" effect="plain">{{ row.name }}</el-tag>
              <div class="status-bar-bg">
                <div class="status-bar-fill" :style="{ width: row.pct + '%', background: row.color }"></div>
              </div>
              <span class="status-count">{{ row.count }}</span>
            </div>
            <el-empty v-if="customerStatusDist.length === 0" :description="$tt('暂无客户数据')" :image-size="80" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 底部：今日待办 + 近期回款 -->
    <el-row :gutter="12" style="margin-top:12px;">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>{{ $tt("跟进待办（逾期/今日）") }}</span>
              <el-button link type="primary" v-permission="perm('view')" @click="$router.push('/crm/warning')">{{ $tt("查看全部") }}</el-button>
            </div>
          </template>
          <el-table :data="followUpTodos" size="small" max-height="320" border>
            <el-table-column prop="customerName" :label="$tf('客户')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="nextFollowUpDate" :label="$tf('计划日期')" width="110" />
            <el-table-column :label="$tf('逾期')" width="80">
              <template #default="{ row }">
                <el-tag v-if="isOverdue(row.nextFollowUpDate)" type="danger" size="small">{{ $tt("逾期") }}</el-tag>
                <el-tag v-else type="warning" size="small">{{ $tt("今日") }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="assignedSalesName" :label="$tf('负责人')" width="100" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>{{ $tt("近期应收") }}</span>
              <el-button link type="primary" v-permission="perm('view')" @click="$router.push('/finance/report/ar-summary')">{{ $tt("查看全部") }}</el-button>
            </div>
          </template>
          <el-table :data="recentPayments" size="small" max-height="320" border>
            <el-table-column prop="paymentNo" :label="$tf('应收编号')" width="150" />
            <el-table-column prop="customerName" :label="$tf('客户')" min-width="140" show-overflow-tooltip />
            <el-table-column :label="$tf('计划金额')" width="110">
              <template #default="{ row }">¥{{ formatMoney(row.planAmount) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('状态')" width="90">
              <template #default="{ row }">
                <el-tag :type="paymentStatusType(row.status)" size="small">{{ $dt(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="planDate" :label="$tf('计划日期')" width="110" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import CockpitPanel from '@/components/crm/CockpitPanel.vue'
import { ElMessage } from 'element-plus'
import { User, TrendCharts, Document, Money } from '@element-plus/icons-vue'
import { customerDashboard, opportunityDashboard } from '@/api/crm/dashboard'
import { listCrmCustomer } from '@/api/crm/customer'
import { listSaleOrder } from '@/api/sale/saleOrder'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('crm:dashboard')

const loading = ref(false)

// KPI 数据
const kpiData = reactive({
  customerTotal: 0,
  opportunityTotal: 0,
  opportunityWonAmount: 0,
  paymentTotalAmount: 0
})

const kpiCards = computed(() => [
  {
    key: 'customer', label: '私海客户数', value: kpiData.customerTotal,
    hint: '我的私海中的客户', icon: User, color: '#409EFF', bg: '#ECF5FF'
  },
  {
    key: 'opportunity', label: '进行中商机', value: kpiData.opportunityTotal,
    hint: '未关闭商机总数', icon: TrendCharts, color: '#67C23A', bg: '#F0F9EB'
  },
  {
    key: 'wonAmount', label: '预估成交金额', value: '¥' + formatMoney(kpiData.opportunityWonAmount),
    hint: '进行中商机合计', icon: Document, color: '#E6A23C', bg: '#FDF6EC'
  },
  {
    key: 'payment', label: '待回款金额', value: '¥' + formatMoney(kpiData.paymentTotalAmount),
    hint: '待回款+部分回款合计', icon: Money, color: '#F56C6C', bg: '#FEF0F0'
  }
])

// 销售漏斗（商机阶段）
const funnelStages = ref([
  { name: '初步接触', count: 0, amount: 0, color: '#409EFF' },
  { name: '需求确认', count: 0, amount: 0, color: '#67C23A' },
  { name: '方案报价', count: 0, amount: 0, color: '#E6A23C' },
  { name: '商务谈判', count: 0, amount: 0, color: '#F56C6C' },
  { name: '成交',     count: 0, amount: 0, color: '#909399' }
])
const maxFunnelCount = computed(() => Math.max(1, ...funnelStages.value.map(s => s.count)))
function barWidth(count) {
  return Math.max(8, (count / maxFunnelCount.value) * 100)
}

// 客户 CRM 状态分布
const customerStatusDist = ref([])
const STATUS_COLOR_MAP = {
  '潜在客户': { type: 'info',    color: '#909399' },
  '意向客户': { type: 'warning', color: '#E6A23C' },
  '正式客户': { type: 'success', color: '#67C23A' },
  '休眠客户': { type: '',        color: '#C0C4CC' },
  '已流失':   { type: 'danger',  color: '#F56C6C' }
}

// 跟进待办
const followUpTodos = ref([])

// 近期回款
const recentPayments = ref([])

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

function paymentStatusType(status) {
  const map = { '待回款': 'warning', '部分回款': 'primary', '已回款': 'success', '已逾期': 'danger', '待收款': 'warning', '部分收款': 'primary', '已收款': 'success' }
  return map[status] || 'info'
}

async function loadDashboard() {
  loading.value = true
  try {
    // 并行加载：客户仪表盘 + 商机仪表盘
    // 拦截器已解包：返回的就是后端 Result.data
    const [custRes, oppRes] = await Promise.all([
      customerDashboard().catch(e => ({})),
      opportunityDashboard().catch(e => ({}))
    ])

    const custData = custRes || {}
    const oppData = oppRes || {}

    // 客户 CRM 状态分布
    const statusDist = custData.statusDist || custData.crmStatusDist || {}
    const total = Object.values(statusDist).reduce((s, v) => s + (Number(v) || 0), 0) || 1
    customerStatusDist.value = Object.entries(statusDist).map(([name, count]) => {
      const meta = STATUS_COLOR_MAP[name] || { type: 'info', color: '#909399' }
      return {
        name,
        count: Number(count) || 0,
        pct: ((Number(count) || 0) / total) * 100,
        ...meta
      }
    })

    // 商机数据
    kpiData.opportunityTotal = oppData.totalOpportunities || 0
    kpiData.opportunityWonAmount = oppData.totalEstimatedAmount || 0

    // 商机阶段分布到漏斗
    const stageDist = oppData.stageDist || {}
    funnelStages.value = funnelStages.value.map(s => ({
      ...s,
      count: stageDist[s.name] || 0,
      amount: 0 // 简化, 暂不细分阶段金额
    }))

    // 加载私海客户数（poolType=0 只统计私海，第1页 size=1 拿 total）
    const custListRes = await listCrmCustomer({ page: 1, size: 1, poolType: 0 }).catch(e => ({ total: 0 }))
    kpiData.customerTotal = custListRes.total || 0

    // 加载待回款订单（【2026-10-19 设计图 v3】系统无应收单：改按销售订单未收口径——
    // 已生效且未收 > 0，未收金额倒序取前 10；不再读应收单）
    const soRes = await listSaleOrder({ current: 1, size: 200, status: '已生效' }).catch(e => ({ records: [], total: 0 }))
    const soRecords = (soRes?.records || [])
      .map(o => ({ ...o, remain: Number(o.netAmount || 0) - Number(o.receivedAmount || 0) }))
      .filter(o => o.remain > 0)
      .sort((a, b) => b.remain - a.remain)
      .slice(0, 10)
    recentPayments.value = soRecords.map(o => ({
      paymentNo: o.orderNo,
      customerName: o.customerName,
      planAmount: o.remain,
      status: '未收完',
      planDate: o.deliveryDate || o.orderDate
    }))
    kpiData.paymentTotalAmount = (soRes?.records || []).reduce((s, o) => {
      const remain = Number(o.netAmount || 0) - Number(o.receivedAmount || 0)
      return s + (remain > 0 ? remain : 0)
    }, 0)

    // 跟进待办：取待跟进客户（简化：用客户列表里 nextFollowUpDate <= 今天的）
    const todoRes = await listCrmCustomer({ page: 1, size: 10 }).catch(e => ({ records: [] }))
    const today = new Date().toISOString().slice(0, 10)
    followUpTodos.value = (todoRes.records || [])
      .filter(c => c.nextFollowUpDate && c.nextFollowUpDate <= today)
      .slice(0, 8)
  } catch (e) {
    ElMessage.error(tt('加载仪表盘失败'))
  } finally {
    loading.value = false
  }
}

onMounted(loadDashboard)
</script>

<style scoped>
.kpi-card { border-radius: 8px; }
.kpi-row { display: flex; align-items: center; gap: 14px; }
.kpi-icon {
  width: 52px; height: 52px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
}
.kpi-text { flex: 1; }
.kpi-label { color: #909399; font-size: 13px; }
.kpi-value { font-size: 24px; font-weight: 600; margin: 2px 0; }
.kpi-hint { color: #C0C4CC; font-size: 12px; }

.card-header { display: flex; justify-content: space-between; align-items: center; font-weight: 500; }

.funnel-wrap { padding: 8px 0; }
.funnel-row { display: flex; align-items: center; margin-bottom: 14px; }
.funnel-label { width: 90px; color: #606266; font-size: 13px; }
.funnel-bar-bg {
  flex: 1; height: 32px; background: #F5F7FA; border-radius: 4px;
  position: relative; overflow: hidden;
}
.funnel-bar-fill {
  height: 100%; border-radius: 4px;
  transition: width 0.4s ease;
}
.funnel-count {
  position: absolute; left: 10px; top: 50%; transform: translateY(-50%);
  color: #fff; font-size: 12px; font-weight: 500;
  text-shadow: 0 1px 2px rgba(0,0,0,0.3);
}

.status-list { padding: 8px 0; }
.status-row { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.status-row .el-tag { width: 80px; text-align: center; }
.status-bar-bg { flex: 1; height: 18px; background: #F5F7FA; border-radius: 9px; overflow: hidden; }
.status-bar-fill { height: 100%; border-radius: 9px; transition: width 0.4s ease; }
.status-count { width: 40px; text-align: right; font-weight: 500; color: #606266; }
</style>
