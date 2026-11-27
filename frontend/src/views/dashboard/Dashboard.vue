<template>
  <div class="my-workspace">
    <!-- 页头：首页设置（始终可见，不受卡片显隐影响） -->
    <div class="dash-toolbar">
      <el-button size="small" :icon="Setting" @click="cardSettingVisible = true">{{ $tt("首页设置") }}</el-button>
    </div>

    <div class="dash-grid">
    <!-- 欢迎条 -->
    <div v-if="isCardVisible('welcome')" class="dash-cell span-2" :style="cellStyle('welcome')">
      <el-card class="welcome-card" shadow="never">
        <div class="welcome-inner">
          <div class="welcome-text" v-show="!isCollapsed('welcome')">
            <div class="hello">{{ greeting }}，{{ displayName }}</div>
            <div class="sub">{{ todayText }} · 共 {{ totalTaskCount }} 项任务待处理</div>
          </div>
          <div class="welcome-right">
            <el-icon class="collapse-caret welcome-caret" @click="toggleCard('welcome')">
              <component :is="isCollapsed('welcome') ? ArrowDown : ArrowUp" />
            </el-icon>
            <div class="welcome-icon">
              <el-icon :size="48"><Bell /></el-icon>
            </div>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 4张个人任务统计卡片 -->
    <div v-if="isCardVisible('stats')" class="dash-cell span-2" :style="cellStyle('stats')">
      <el-icon class="collapse-caret float-caret" @click="toggleCard('stats')">
        <component :is="isCollapsed('stats') ? ArrowDown : ArrowUp" />
      </el-icon>
      <el-row v-show="!isCollapsed('stats')" :gutter="16" class="stats-row">
        <el-col :xs="12" :sm="12" :md="6" v-for="card in statCards" :key="card.type">
          <el-card shadow="hover" class="stat-card" :class="card.type"
                   v-loading="loading.overview"
                   @click="card.clickable && goToSection(card.type)">
            <div class="stat-content">
              <div class="stat-icon" :style="{ background: card.bgColor }">
                <el-icon :size="28" :color="card.iconColor">
                  <component :is="card.icon" />
                </el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value" :class="card.valueClass">{{ card.value }}</div>
                <div class="stat-label">{{ $tf(card.label) }}</div>
                <div v-if="card.hint" class="stat-hint">{{ $tt(card.hint) }}</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 我的业绩/采购/库存简报 -->
    <div v-if="isCardVisible('performance') && performanceCards.length" class="dash-cell span-2" :style="cellStyle('performance')">
      <el-icon class="collapse-caret float-caret" @click="toggleCard('performance')">
        <component :is="isCollapsed('performance') ? ArrowDown : ArrowUp" />
      </el-icon>
      <el-row v-show="!isCollapsed('performance')" :gutter="16" class="performance-row">
        <el-col :xs="24" :sm="12" :md="8" v-for="p in performanceCards" :key="p.label">
          <el-card shadow="hover" class="perf-card">
            <div class="perf-inner">
              <div class="perf-left">
                <div class="perf-label">{{ $tf(p.label) }}</div>
                <div class="perf-value">¥{{ formatNumber(p.value) }}</div>
              </div>
              <div class="perf-right">
                <el-tag :type="p.tagType" effect="plain" size="small">{{ p.tagText }}</el-tag>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 待我审批 -->
    <div v-if="isCardVisible('pending')" class="dash-cell span-2" :style="cellStyle('pending')">
    <el-card class="task-card" :class="{ 'card-collapsed': isCollapsed('pending') }" shadow="never" v-loading="loading.pending">
      <template #header>
        <div class="card-header">
          <div class="title-wrap" @click="toggleCard('pending')">
            <el-icon class="title-icon" color="#f56c6c"><DocumentChecked /></el-icon>
            <span class="card-title">{{ $tt("待我审批") }}</span>
            <el-badge :value="pendingApprovals.length" :hidden="!pendingApprovals.length" type="danger" />
          </div>
          <div class="header-right-wrap">
            <el-radio-group v-model="pendingFilter" size="small">
              <el-radio-button value="">{{ $tt("全部") }}</el-radio-button>
              <el-radio-button value="SALE">{{ $tt("销售") }}</el-radio-button>
              <el-radio-button value="PURCHASE">{{ $tt("采购") }}</el-radio-button>
              <el-radio-button value="INVENTORY">{{ $tt("库存") }}</el-radio-button>
              <el-radio-button value="PRODUCTION">{{ $tt("生产") }}</el-radio-button>
            </el-radio-group>
            <el-button v-if="filteredPendingApprovals.length"
                       type="success" size="small" :icon="Check"
                       :loading="approving.batch"
                       v-permission="perm('audit')"
                       @click="handleBatchApprove">
              一键审批
            </el-button>
            <el-icon class="collapse-caret" @click="toggleCard('pending')">
              <component :is="isCollapsed('pending') ? ArrowDown : ArrowUp" />
            </el-icon>
          </div>
        </div>
      </template>
      <div v-if="filteredPendingApprovals.length === 0" class="empty-tip">
        <el-empty :description="$tt('暂无待审批任务')" :image-size="80" />
      </div>
      <div v-else class="task-list">
        <div v-for="item in filteredPendingApprovals.slice(0, 8)" :key="item.type + '_' + item.id"
             class="task-item" :class="{ clickable: item.routePath }"
             @click="goToBill(item)">
          <div class="task-left">
            <el-tag :type="getTypeTagType(item.type)" size="small" effect="light">{{ $dt(item.typeName) }}</el-tag>
            <div class="task-content">
              <div class="task-title">{{ item.title }} <span class="bill-no">{{ item.billNo }}</span></div>
              <div class="task-meta">
                <span v-if="item.partyName">{{ item.partyName }}</span>
                <span v-if="item.partyName" class="meta-divider">·</span>
                <span>{{ item.creator || '系统' }}</span>
                <span class="meta-divider">·</span>
                <span>{{ formatTime(item.createTime) }}</span>
                <span v-if="item.amount" class="meta-divider">·</span>
                <span v-if="item.amount" class="amount">¥{{ formatNumber(item.amount) }}</span>
              </div>
            </div>
          </div>
          <div class="task-right">
            <el-tag type="warning" size="small">{{ $tt("待审批") }}</el-tag>
            <el-button type="success" size="small" :icon="Check"
                       :loading="approving.single && approving.processingId === item.id"
                       v-permission="perm('audit')"
                       @click="handleApprove(item, $event)">
              通过
            </el-button>
            <el-button type="danger" size="small" plain
                       :loading="approving.rejecting && approving.processingId === item.id"
                       v-permission="perm('reject')"
                       @click="handleReject(item, $event)">
              驳回
            </el-button>
          </div>
        </div>
        <div v-if="filteredPendingApprovals.length > 8" class="show-more"
             @click="goToList('/approval')">
          查看全部 {{ filteredPendingApprovals.length }} 条
        </div>
      </div>
    </el-card>
    </div>

    <!-- 即将超期 / 已超期 -->
    <div v-if="isCardVisible('overdue')" class="dash-cell span-2" :style="cellStyle('overdue')">
    <el-card class="task-card" :class="{ 'card-collapsed': isCollapsed('overdue') }" shadow="never" v-loading="loading.overdue">
      <template #header>
        <div class="card-header">
          <div class="title-wrap" @click="toggleCard('overdue')">
            <el-icon class="title-icon" color="#e6a23c"><Timer /></el-icon>
            <span class="card-title">{{ $tt("即将超期 / 已超期") }}</span>
            <el-badge :value="overdueTasks.length" :hidden="!overdueTasks.length" type="warning" />
          </div>
          <div class="header-right-wrap">
            <el-radio-group v-model="overdueFilter" size="small">
              <el-radio-button value="">{{ $tt("全部") }}</el-radio-button>
              <el-radio-button value="OVERDUE">{{ $tt("已超期") }}</el-radio-button>
              <el-radio-button value="URGENT">{{ $tt("紧急") }}</el-radio-button>
              <el-radio-button value="SOON">{{ $tt("即将到期") }}</el-radio-button>
            </el-radio-group>
            <el-icon class="collapse-caret" @click="toggleCard('overdue')">
              <component :is="isCollapsed('overdue') ? ArrowDown : ArrowUp" />
            </el-icon>
          </div>
        </div>
      </template>
      <div v-if="filteredOverdueTasks.length === 0" class="empty-tip">
        <el-empty :description="$tt('暂无超期任务')" :image-size="80" />
      </div>
      <div v-else class="task-list">
        <div v-for="item in filteredOverdueTasks.slice(0, 8)" :key="item.type + '_' + item.id"
             class="task-item" :class="{ clickable: item.routePath, [item.urgency?.toLowerCase()]: true }"
             @click="goToBill(item)">
          <div class="task-left">
            <el-tag :type="getUrgencyTagType(item.urgency)" size="small" effect="dark">
              {{ getUrgencyText(item.urgency, item.remainingDays) }}
            </el-tag>
            <el-tag :type="getTypeTagType(item.type)" size="small" effect="plain">{{ $dt(item.typeName) }}</el-tag>
            <div class="task-content">
              <div class="task-title">{{ item.title }} <span class="bill-no">{{ item.billNo }}</span></div>
              <div class="task-meta">
                <span>到期日: {{ formatDate(item.dueDate) }}</span>
                <span class="meta-divider">·</span>
                <span :class="getRemainingClass(item.remainingDays)">
                  {{ getRemainingText(item.remainingDays) }}
                </span>
                <span v-if="item.amount" class="meta-divider">·</span>
                <span v-if="item.amount" class="amount">¥{{ formatNumber(item.amount) }}</span>
              </div>
            </div>
          </div>
          <div class="task-right">
            <el-icon class="go-icon"><ArrowRight /></el-icon>
          </div>
        </div>
        <div v-if="filteredOverdueTasks.length > 8" class="show-more"
             @click="goToList('/dashboard/overdue')">
          查看全部 {{ filteredOverdueTasks.length }} 条
        </div>
      </div>
    </el-card>
    </div>

    <!-- 我的草稿 -->
    <div v-if="isCardVisible('drafts')" class="dash-cell span-1" :style="cellStyle('drafts')">
    <el-card class="task-card" :class="{ 'card-collapsed': isCollapsed('drafts') }" shadow="never" v-loading="loading.drafts">
      <template #header>
        <div class="card-header">
          <div class="title-wrap" @click="toggleCard('drafts')">
            <el-icon class="title-icon" color="#909399"><EditPen /></el-icon>
            <span class="card-title">{{ $tt("我的草稿") }}</span>
            <el-badge :value="myDrafts.length" :hidden="!myDrafts.length" type="info" />
          </div>
          <div class="header-right-wrap">
            <el-icon class="collapse-caret" @click="toggleCard('drafts')">
              <component :is="isCollapsed('drafts') ? ArrowDown : ArrowUp" />
            </el-icon>
          </div>
        </div>
      </template>
      <div v-if="myDrafts.length === 0" class="empty-tip">
        <el-empty :description="$tt('暂无草稿单据')" :image-size="80" />
      </div>
      <div v-else class="task-list compact">
        <div v-for="item in myDrafts.slice(0, 6)" :key="item.type + '_' + item.id"
             class="task-item" :class="{ clickable: item.routePath }"
             @click="goToBill(item)">
          <div class="task-left">
            <el-tag :type="getTypeTagType(item.type)" size="small" effect="plain">{{ $dt(item.typeName) }}</el-tag>
            <div class="task-content">
              <div class="task-title">{{ item.billNo }}</div>
              <div class="task-meta">
                <span>{{ formatTime(item.createTime) }}</span>
                <span v-if="item.amount" class="meta-divider">·</span>
                <span v-if="item.amount" class="amount">¥{{ formatNumber(item.amount) }}</span>
              </div>
            </div>
          </div>
          <div class="task-right">
            <el-tag type="info" size="small">{{ $tt("草稿") }}</el-tag>
            <el-icon class="go-icon"><ArrowRight /></el-icon>
          </div>
        </div>
      </div>
    </el-card>
    </div>

    <!-- 应收应付跟进 -->
    <div v-if="isCardVisible('arAp')" class="dash-cell span-1" :style="cellStyle('arAp')">
    <el-card class="task-card" :class="{ 'card-collapsed': isCollapsed('arAp') }" shadow="never" v-loading="loading.arAp">
      <template #header>
        <div class="card-header">
          <div class="title-wrap" @click="toggleCard('arAp')">
            <el-icon class="title-icon" color="#f56c6c"><Money /></el-icon>
            <span class="card-title">{{ $tt("应收应付跟进") }}</span>
            <el-badge :value="arApFollowUp.length" :hidden="!arApFollowUp.length" type="danger" />
          </div>
          <div class="header-right-wrap">
            <el-icon class="collapse-caret" @click="toggleCard('arAp')">
              <component :is="isCollapsed('arAp') ? ArrowDown : ArrowUp" />
            </el-icon>
          </div>
        </div>
      </template>
      <div v-if="arApFollowUp.length === 0" class="empty-tip">
        <el-empty :description="$tt('暂无应收应付逾期')" :image-size="80" />
      </div>
      <div v-else class="task-list compact">
        <div v-for="item in arApFollowUp.slice(0, 6)" :key="item.type + '_' + item.id"
             class="task-item overdue"
             :class="{ clickable: item.routePath }"
             @click="goToBill(item)">
          <div class="task-left">
            <el-tag :type="item.type === 'AR' ? 'danger' : 'warning'" size="small" effect="dark">
              {{ $dt(item.typeName) }}
            </el-tag>
            <div class="task-content">
              <div class="task-title">{{ item.partyName }} <span class="bill-no">{{ item.billNo }}</span></div>
              <div class="task-meta">
                <span>到期: {{ formatDate(item.dueDate) }}</span>
                <span class="meta-divider">·</span>
                <span class="amount-due">¥{{ formatNumber(item.amount) }}</span>
              </div>
            </div>
          </div>
          <div class="task-right">
            <el-icon class="go-icon"><ArrowRight /></el-icon>
          </div>
        </div>
      </div>
    </el-card>
    </div>

    <!-- 报表图表（默认仪表盘的汇总图表卡片，无可用图表时整块隐藏） -->
    <div v-if="isCardVisible('charts') && dashChartCards.length" class="dash-cell span-2" :style="cellStyle('charts')">
    <el-card class="task-card" :class="{ 'card-collapsed': isCollapsed('charts') }" shadow="never">
      <template #header>
        <div class="card-header">
          <div class="title-wrap" @click="toggleCard('charts')">
            <el-icon class="title-icon" color="#409eff"><TrendCharts /></el-icon>
            <span class="card-title">报表图表 · {{ defaultDashName }}</span>
          </div>
          <div class="header-right-wrap">
            <el-button v-if="defaultDashId" size="small" link type="primary" v-permission="perm('view')"
                       @click="goToList(`/report/dashboard/view/${defaultDashId}`)">
              查看仪表盘<el-icon><ArrowRight /></el-icon>
            </el-button>
            <el-icon class="collapse-caret" @click="toggleCard('charts')">
              <component :is="isCollapsed('charts') ? ArrowDown : ArrowUp" />
            </el-icon>
          </div>
        </div>
      </template>
      <div class="chart-grid">
        <div v-for="(c, idx) in dashChartCards" :key="c.reportId + '-' + idx" class="chart-cell">
          <div class="chart-cell-title">{{ c.title || c.reportName }}</div>
          <div class="chart-cell-body" :ref="el => setWbChartEl(el, idx)"></div>
        </div>
      </div>
    </el-card>
    </div>

    <!-- 我今天要做的工序（第 9 期新增卡片） -->
    <div v-if="isCardVisible('myOperations')" class="dash-cell span-2" :style="cellStyle('myOperations')">
      <MyOpenOperationsCard :collapsed="isCollapsed('myOperations')" @toggle-collapse="toggleCard('myOperations')" />
    </div>

    <!-- 销售过程（首页速览：与「客户管理 → 销售过程」同一接口同一数据，点击跳原页） -->
    <div v-if="isCardVisible('salesProcess') && salesProcessAllowed" class="dash-cell span-2" :style="cellStyle('salesProcess')">
      <SalesProcessCard :collapsed="isCollapsed('salesProcess')" @toggle-collapse="toggleCard('salesProcess')" />
    </div>

    <!-- 销售预警（首页速览：与「客户管理 → 销售预警」同一接口同一数据、全部状态，点击跳原页） -->
    <div v-if="isCardVisible('salesWarning') && salesWarningAllowed" class="dash-cell span-2" :style="cellStyle('salesWarning')">
      <SalesWarningCard :collapsed="isCollapsed('salesWarning')" @toggle-collapse="toggleCard('salesWarning')" />
    </div>

    <!-- 自定义卡片（2026-11-05：与数据看板同一套卡片能力，每人最多 6 张） -->
    <div
      v-for="(wc, idx) in visibleWbCards"
      :key="wc.id"
      class="dash-cell"
      :class="wc.size === 12 ? 'span-2' : 'span-1'"
      :style="{ order: wbCellOrder(idx) }"
    >
      <BoardCard :card="wbCardView(wc)" :data="wbData[wc.id]?.data || {}" :error="wbData[wc.id]?.error || ''">
        <template #extra>
          <el-icon class="collapse-caret wb-caret" @click="toggleWbCollapse(wc.id)">
            <component :is="wc.wbCollapsed ? ArrowDown : ArrowUp" />
          </el-icon>
        </template>
      </BoardCard>
      <div v-if="wc.wbCollapsed" class="wb-collapsed-mask" @click="toggleWbCollapse(wc.id)"></div>
    </div>
    </div>

    <!-- 首页设置：卡片显隐 + 拖动排序（样式与列设置抽屉一致） -->
    <DashboardCardSetting
      v-model="cardSettingVisible"
      :cards="settingCards"
      allow-add
      @save="onCardSettingSave"
      @reset="onCardSettingReset"
      @add="addCustomCard"
      @delete="deleteCustomCard"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import echarts from '@/utils/echartsCore'
import {
  getMyOverview,
  getMyPendingApprovals,
  getMyOverdueTasks,
  getMyDrafts,
  getMyArApFollowUp,
  approveBill,
  rejectBill,
  batchApproveBills
} from '@/api/dashboard'
import { listDashboards, getDashboardData } from '@/api/report/customDashboard'
import {
  Bell,
  DocumentChecked,
  Timer,
  EditPen,
  Money,
  ArrowRight,
  ArrowUp,
  ArrowDown,
  Check,
  Setting,
  TrendCharts
} from '@element-plus/icons-vue'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'
import { scopedGetJSON, scopedSetJSON } from '@/utils/userScopedStorage'
import DashboardCardSetting from '@/components/dashboard/DashboardCardSetting.vue'
import BoardCard from '@/components/board/BoardCard.vue'
import MyOpenOperationsCard from '@/components/dashboard/MyOpenOperationsCard.vue'
import SalesProcessCard from '@/components/dashboard/SalesProcessCard.vue'
import SalesWarningCard from '@/components/dashboard/SalesWarningCard.vue'
import { getWorkbenchCards, saveWorkbenchCards, previewCard } from '@/api/board/customBoard'

const perm = usePagePerm('dashboard:dashboard')

const router = useRouter()
const userStore = useUserStore()

// ========== 菜单权限显示控制（用户拍板：无客户管理菜单权限的账号，首页不出销售过程/销售预警卡） ==========
/** 与路由守卫同口径判断能否进入某页面：menuRoutePaths 精确/前缀命中；无数据时按角色白名单降级 */
function canVisitPath (path) {
  let menuRoutePaths = []
  try { menuRoutePaths = JSON.parse(localStorage.getItem('menuRoutePaths') || '[]') } catch (e) { /* 解析失败按无数据处理 */ }
  if (!Array.isArray(menuRoutePaths) || !menuRoutePaths.length) {
    const roles = Array.isArray(userStore.roles) ? userStore.roles : []
    return ['ADMIN', 'SUPER_ADMIN', 'SALES_MANAGER', 'SALES'].some(r => roles.includes(r))
  }
  const allowed = new Set()
  for (const rp of menuRoutePaths) {
    allowed.add(rp)
    const parts = String(rp).split('/')
    if (parts.length >= 2) allowed.add('/' + parts[1])
  }
  if (allowed.has(path)) return true
  let prefix = ''
  for (const seg of path.split('/').filter(Boolean)) {
    prefix += '/' + seg
    if (allowed.has(prefix)) return true
  }
  return false
}
const salesProcessAllowed = canVisitPath('/crm/sales-activity')
const salesWarningAllowed = canVisitPath('/crm/warning')

// ========== 首页卡片管理（显隐 + 排序 + 折叠，按「登录人 + 所属公司」隔离保存） ==========
// 卡片登记表：id 稳定不变（持久化用它），title 供设置面板展示
const CARD_DEFS = [
  { id: 'welcome', title: '欢迎条' },
  { id: 'stats', title: '个人任务统计' },
  { id: 'performance', title: '业绩 / 采购 / 库存简报' },
  { id: 'pending', title: '待我审批' },
  { id: 'overdue', title: '即将超期 / 已超期' },
  { id: 'drafts', title: '我的草稿' },
  { id: 'arAp', title: '应收应付跟进' },
  { id: 'charts', title: '报表图表' },
  { id: 'myOperations', title: '我今天要做的工序' },
  { id: 'salesProcess', title: '销售过程' },
  { id: 'salesWarning', title: '销售预警' }
]
// 存储基名；真实 key = dashboardCards::v1::u<用户>@t<公司>（与列设置同一隔离口径）
const CARDS_STORAGE = 'dashboardCards'
const CARD_TITLE = Object.fromEntries(CARD_DEFS.map(c => [c.id, c.title]))

/** 默认布局：全部显示、不折叠，顺序 = 改造前首页顺序 */
function defaultCardLayout () {
  return CARD_DEFS.map(c => ({ id: c.id, visible: true, collapsed: false }))
}

/** 归一化：按已存顺序取，丢弃已下线的卡片，新增卡片补到末尾（默认显示） */
function normalizeCardLayout (saved) {
  const defIds = CARD_DEFS.map(c => c.id)
  const out = []
  if (Array.isArray(saved)) {
    saved.forEach(s => {
      if (s && defIds.includes(s.id) && !out.some(x => x.id === s.id)) {
        out.push({ id: s.id, visible: s.visible !== false, collapsed: s.collapsed === true })
      }
    })
  }
  CARD_DEFS.forEach(d => {
    if (!out.some(x => x.id === d.id)) out.push({ id: d.id, visible: true, collapsed: false })
  })
  return out
}

const cardLayout = ref(normalizeCardLayout(scopedGetJSON(CARDS_STORAGE, null)))
const cardSettingVisible = ref(false)

function persistCardLayout () {
  scopedSetJSON(CARDS_STORAGE, cardLayout.value.map(c => ({ id: c.id, visible: c.visible, collapsed: c.collapsed })))
}

/** 卡片是否显示（未登记的卡片默认显示） */
function isCardVisible (id) {
  const c = cardLayout.value.find(x => x.id === id)
  return c ? c.visible !== false : true
}
/** 卡片是否折叠 */
function isCollapsed (id) {
  const c = cardLayout.value.find(x => x.id === id)
  return c ? c.collapsed === true : false
}
/** 网格排序：order = 设置里的顺序（排序即时改变首页卡片顺序） */
function cellStyle (id) {
  const i = cardLayout.value.findIndex(c => c.id === id)
  return { order: i < 0 ? 99 : i }
}
/** 折叠 / 展开一张卡片（状态记住） */
function toggleCard (id) {
  cardLayout.value = cardLayout.value.map(c => (c.id === id ? { ...c, collapsed: !c.collapsed } : c))
  persistCardLayout()
}

/** 设置面板数据源（按当前顺序；无菜单权限的卡片不进面板，避免"勾了也不显示"的摆设项） */
const settingCards = computed(() => {
  const fixed = cardLayout.value
    .filter(c => (c.id !== 'salesProcess' || salesProcessAllowed) && (c.id !== 'salesWarning' || salesWarningAllowed))
    .map(c => ({
      id: c.id, title: CARD_TITLE[c.id] || c.id, visible: c.visible, collapsed: c.collapsed
    }))
  // 自定义卡片（2026-11-05）：与固定卡同一套显隐 / 排序 / 删除
  const custom = wbCards.value.map(c => ({
    id: 'wb-' + c.id,
    title: c.title || '未命名卡片',
    visible: c.wbVisible !== false,
    custom: true
  }))
  return [...fixed, ...custom]
})

// ========== 工作台自定义卡片（2026-11-05：与数据看板同一套卡片能力） ==========
const wbCards = ref([])
const wbData = ref({})
const WB_LIMIT = 6

const visibleWbCards = computed(() => wbCards.value.filter(c => c.wbVisible !== false))

/** 自定义卡片渲染用视图对象（颜色 / 图标 / 尺寸沿用卡片配置） */
function wbCardView (c) {
  return {
    ...c,
    title: c.title || '未命名卡片',
    color: c.color || '#2563eb',
    icon: c.icon || 'target',
    size: c.size || 6
  }
}
/** 自定义卡片的排序位（排在 11 张固定卡之后，按自身顺序） */
function wbCellOrder (idx) {
  return 100 + idx
}

function toggleWbCollapse (id) {
  wbCards.value = wbCards.value.map(c => (c.id === id ? { ...c, wbCollapsed: !c.wbCollapsed } : c))
  persistWbCards()
}

async function loadWbCards () {
  try {
    const res = await getWorkbenchCards()
    wbCards.value = res?.cards || []
    await refreshWbData()
  } catch (e) {
    // 无权限或无记录时按空处理，不影响首页其它卡片
    wbCards.value = []
  }
}

async function refreshWbData () {
  if (!wbCards.value.length) {
    wbData.value = {}
    return
  }
  const map = {}
  for (const c of wbCards.value) {
    try {
      const d = await previewCard(c, 'month')
      map[c.id] = { data: d || {}, error: '' }
    } catch (e) {
      map[c.id] = { data: {}, error: e?.message || '数据暂时无法获取' }
    }
  }
  wbData.value = map
}

async function persistWbCards () {
  try {
    await saveWorkbenchCards(wbCards.value)
  } catch (e) {
    ElMessage.error(e?.message || '自定义卡片保存失败')
  }
}

/** 添加入口：进入与数据看板同一个设计器（type=workbench） */
function addCustomCard () {
  if (wbCards.value.length >= WB_LIMIT) {
    ElMessage.warning(`工作台自定义卡片最多 ${WB_LIMIT} 张`)
    return
  }
  cardSettingVisible.value = false
  router.push({ path: '/databoard/custom/designer', query: { type: 'workbench' } })
}

/** 删除自定义卡片（设置面板二次确认后触发） */
async function deleteCustomCard (settingId) {
  const cardId = String(settingId).replace(/^wb-/, '')
  wbCards.value = wbCards.value.filter(c => c.id !== cardId)
  const m = { ...wbData.value }
  delete m[cardId]
  wbData.value = m
  await persistWbCards()
  ElMessage.success('自定义卡片已删除')
}

/** 保存设置：顺序 + 显隐立即生效（折叠状态保留） */
function onCardSettingSave (list) {
  const prev = new Map(cardLayout.value.map(c => [c.id, c]))
  // 固定卡：只认 11 张登记表内的 id；自定义卡（wb- 前缀）单独处理顺序与显隐
  const fixedList = list.filter(l => CARD_DEFS.some(d => d.id === l.id))
  cardLayout.value = fixedList.map(l => ({
    id: l.id,
    visible: l.visible !== false,
    collapsed: prev.get(l.id)?.collapsed === true
  }))
  CARD_DEFS.forEach(d => {
    if (!cardLayout.value.some(c => c.id === d.id)) cardLayout.value.push({ id: d.id, visible: true, collapsed: false })
  })
  persistCardLayout()

  // 自定义卡：按面板里的顺序重排 + 记住显隐，并落库（按登录人隔离）
  const customOrder = list.filter(l => String(l.id).startsWith('wb-'))
  if (customOrder.length || wbCards.value.length) {
    const byId = new Map(wbCards.value.map(c => ['wb-' + c.id, c]))
    const next = []
    customOrder.forEach(l => {
      const c = byId.get(String(l.id))
      if (c) next.push({ ...c, wbVisible: l.visible !== false })
    })
    // 面板里没有的（异常兜底）追加在末尾，不丢卡片
    wbCards.value.forEach(c => {
      if (!next.some(x => x.id === c.id)) next.push({ ...c, wbVisible: c.wbVisible !== false })
    })
    wbCards.value = next
    persistWbCards()
  }
  ElMessage.success(tt('首页设置已保存'))
}

/** 恢复默认：固定卡全部显示、默认顺序、全部展开；自定义卡清空（回到升级前状态） */
function onCardSettingReset () {
  cardLayout.value = defaultCardLayout()
  persistCardLayout()
  if (wbCards.value.length) {
    wbCards.value = []
    wbData.value = {}
    persistWbCards()
  }
  ElMessage.success(tt('已恢复默认首页'))
}

const loading = reactive({
  overview: false,
  pending: false,
  overdue: false,
  drafts: false,
  arAp: false
})

const overview = ref({})
const pendingApprovals = ref([])
const overdueTasks = ref([])
const myDrafts = ref([])
const arApFollowUp = ref([])

const pendingFilter = ref('')
const overdueFilter = ref('')

const displayName = computed(() => {
  // 取当前登录账号的姓名：真实姓名 → 用户名 → 本地缓存兜底
  // 注意：用户信息直接存在 userStore 上（非 userStore.userInfo），写错取不到会一直显示「同事」
  return userStore.realName
      || localStorage.getItem('realName')
      || userStore.username
      || localStorage.getItem('username')
      || '同事'
})

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 11) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const todayText = computed(() => {
  const d = new Date()
  const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 ${weekdays[d.getDay()]}`
})

const totalTaskCount = computed(() => {
  // 优先使用 overview 的统计数（API 返回的精确值）
  const ov = overview.value
  if (ov && (ov.pendingApprovalCount != null || ov.overdueCount != null
      || ov.myDraftCount != null || ov.arApFollowUpCount != null)) {
    return (ov.pendingApprovalCount || 0)
        + (ov.overdueCount || 0)
        + (ov.myDraftCount || 0)
        + (ov.arApFollowUpCount || 0)
  }
  // 后备：使用本地列表长度
  return pendingApprovals.value.length
      + overdueTasks.value.length
      + myDrafts.value.length
      + arApFollowUp.value.length
})

const statCards = computed(() => [
  {
    type: 'pending',
    label: '待我审批',
    value: overview.value.pendingApprovalCount || 0,
    icon: 'DocumentChecked',
    bgColor: 'rgba(245, 108, 108, 0.12)',
    iconColor: '#f56c6c',
    valueClass: 'val-danger',
    hint: '需尽快处理',
    clickable: true
  },
  {
    type: 'overdue',
    label: '即将超期 / 已超期',
    value: overview.value.overdueCount || 0,
    icon: 'Timer',
    bgColor: 'rgba(230, 162, 60, 0.12)',
    iconColor: '#e6a23c',
    valueClass: 'val-warning',
    hint: '优先级最高',
    clickable: true
  },
  {
    type: 'drafts',
    label: '我的草稿',
    value: overview.value.myDraftCount || 0,
    icon: 'EditPen',
    bgColor: 'rgba(144, 147, 153, 0.12)',
    iconColor: '#909399',
    valueClass: 'val-info',
    hint: '继续完善',
    clickable: true
  },
  {
    type: 'arAp',
    label: '应收应付跟进',
    value: overview.value.arApFollowUpCount || 0,
    icon: 'Money',
    bgColor: 'rgba(64, 158, 255, 0.12)',
    iconColor: '#409eff',
    valueClass: 'val-primary',
    hint: '已过到期日',
    clickable: true
  }
])

const performanceCards = computed(() => {
  const list = []
  if (overview.value.mySaleAmount != null) {
    list.push({
      label: '本月我的销售额',
      value: overview.value.mySaleAmount,
      tagType: 'success',
      tagText: '销售业绩'
    })
  }
  if (overview.value.myPurchaseAmount != null) {
    list.push({
      label: '本月我的采购额',
      value: overview.value.myPurchaseAmount,
      tagType: 'primary',
      tagText: '采购金额'
    })
  }
  if (overview.value.inventoryWarningCount != null) {
    list.push({
      label: '库存预警物料数',
      value: overview.value.inventoryWarningCount,
      tagType: 'warning',
      tagText: '需补货'
    })
  }
  return list
})

const filteredPendingApprovals = computed(() => {
  if (!pendingFilter.value) return pendingApprovals.value
  const map = {
    SALE: ['SALE_ORDER', 'SALE_DELIVERY', 'SALE_RETURN', 'SALE_INVOICE'],
    PURCHASE: ['PURCHASE_ORDER', 'PURCHASE_INBOUND', 'PURCHASE_RETURN', 'PURCHASE_INVOICE'],
    INVENTORY: ['STOCK_TRANSFER', 'STOCK_CHECK'],
    PRODUCTION: ['WORK_ORDER', 'OPERATION_REPORT', 'PRODUCTION_RECEIPT']
  }
  const types = map[pendingFilter.value] || []
  return pendingApprovals.value.filter(i => types.includes(i.type))
})

const filteredOverdueTasks = computed(() => {
  if (!overdueFilter.value) return overdueTasks.value
  return overdueTasks.value.filter(i => i.urgency === overdueFilter.value)
})

const formatNumber = (num) => {
  if (!num && num !== 0) return '0.00'
  return Number(num).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const formatTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now - date
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`
  return date.toLocaleDateString('zh-CN')
}

const formatDate = (date) => {
  if (!date) return ''
  return String(date).substring(0, 10)
}

const getTypeTagType = (type) => {
  if (type?.startsWith('SALE')) return 'primary'
  if (type?.startsWith('PURCHASE')) return 'success'
  if (type?.startsWith('STOCK')) return 'warning'
  if (type?.startsWith('WORK') || type?.startsWith('OPERATION')
      || type?.startsWith('PROCESS') || type?.startsWith('PRODUCTION')) return 'info'
  if (type === 'AR') return 'danger'
  if (type === 'AP') return 'warning'
  return 'info'
}

const getUrgencyTagType = (urgency) => {
  return { OVERDUE: 'danger', URGENT: 'warning', SOON: 'primary', NORMAL: 'info' }[urgency] || 'info'
}

const getUrgencyText = (urgency, days) => {
  if (urgency === 'OVERDUE') return `已超期 ${Math.abs(days)} 天`
  if (urgency === 'URGENT') return `${days} 天内到期`
  if (urgency === 'SOON') return `${days} 天内到期`
  return '正常'
}

const getRemainingText = (days) => {
  if (days < 0) return `已超期 ${Math.abs(days)} 天`
  if (days === 0) return '今日到期'
  return `还剩 ${days} 天`
}

const getRemainingClass = (days) => {
  if (days < 0) return 'text-danger'
  if (days <= 3) return 'text-warning'
  return 'text-info'
}

const goToBill = (item) => {
  if (item.routePath) router.push(item.routePath)
}

// ===== 工作台审批 =====
const approving = reactive({
  single: false,
  batch: false,
  rejecting: false,
  processingId: null
})

/** 单条审批 */
const handleApprove = async (item, event) => {
  if (event) {
    event.stopPropagation()
  }
  try {
    await ElMessageBox.confirm(
      `${tt("确认审批通过 ")}${item.typeName} ${item.billNo}${tt(" 吗？")}`,
      tt('审批确认'),
      { confirmButtonText: tt('通过'), cancelButtonText: tt('取消'), type: 'warning' }
    )
  } catch (e) {
    return // 用户取消
  }
  approving.single = true
  approving.processingId = item.id
  try {
    const res = await approveBill(item.type, item.id)
    if (res.success) {
      ElMessage.success(`${item.typeName} ${item.billNo}${tt(" 审批通过")}`)
      // 从列表移除
      pendingApprovals.value = pendingApprovals.value.filter(
        i => !(i.type === item.type && i.id === item.id)
      )
      // 重新加载概览
      loadOverview()
      // 即时刷新铃铛（审批动作后待办/结果提醒立刻变化，不等 30 秒轮询）
      try { window.dispatchEvent(new CustomEvent('erp:notification-refresh')) } catch (e2) { /* 忽略 */ }
    } else {
      ElMessage.error(res.errorMessage || tt('审批失败'))
    }
  } catch (e) {
    ElMessage.error(tt('审批失败：') + (e.message || e))
  } finally {
    approving.single = false
    approving.processingId = null
  }
}

/** 单条驳回（2026-11-19：与「通过」对齐；要求输入驳回原因，成功后单据退回草稿） */
const handleReject = async (item, event) => {
  if (event) {
    event.stopPropagation()
  }
  let reason = ''
  try {
    const r = await ElMessageBox.prompt(
      `${tt("确认驳回 ")}${item.typeName} ${item.billNo}${tt(" 吗？")} ${tt("单据将退回草稿。")}`,
      tt('审批驳回'),
      {
        confirmButtonText: tt('确认驳回'),
        cancelButtonText: tt('取消'),
        inputPlaceholder: tt('请输入驳回原因'),
        inputValidator: (v) => (v && String(v).trim() ? true : tt('请输入驳回原因'))
      }
    )
    reason = String(r.value || '').trim()
  } catch (e) {
    return // 用户取消
  }
  approving.rejecting = true
  approving.processingId = item.id
  try {
    const res = await rejectBill(item.type, item.id, reason)
    if (res.success) {
      ElMessage.success(`${item.typeName} ${item.billNo}${tt(" 已驳回，单据退回草稿")}`)
      pendingApprovals.value = pendingApprovals.value.filter(
        i => !(i.type === item.type && i.id === item.id)
      )
      loadOverview()
      // 即时刷新铃铛（驳回后待办与结果提醒立刻变化，不等 30 秒轮询）
      try { window.dispatchEvent(new CustomEvent('erp:notification-refresh')) } catch (e2) { /* 忽略 */ }
    } else {
      ElMessage.error(res.errorMessage || tt('驳回失败'))
    }
  } catch (e) {
    ElMessage.error(tt('驳回失败：') + (e.message || e))
  } finally {
    approving.rejecting = false
    approving.processingId = null
  }
}

/** 一键审批（批量审批当前过滤后的所有任务） */
const handleBatchApprove = async () => {
  const items = filteredPendingApprovals.value.map(i => ({ type: i.type, id: i.id }))
  if (!items.length) {
    ElMessage.warning(tt('当前没有可审批的任务'))
    return
  }
  try {
    await ElMessageBox.confirm(
      `${tt("确认一键审批当前 ")}${items.length}${tt(" 条待办任务吗？")}`,
      tt('一键审批确认'),
      { confirmButtonText: tt('全部通过'), cancelButtonText: tt('取消'), type: 'warning' }
    )
  } catch (e) {
    return
  }
  approving.batch = true
  try {
    const results = await batchApproveBills(items) || []
    const successCount = results.filter(r => r.success).length
    const failCount = results.length - successCount
    if (successCount > 0) {
      ElMessage.success(`${tt("成功审批 ")}${successCount}${tt(" 条")}` + (failCount > 0 ? `${tt("，失败 ")}${failCount}${tt(" 条")}` : ''))
    } else {
      ElMessage.error(tt('审批全部失败'))
    }
    // 重新加载列表
    await loadPending()
    await loadOverview()
    // 即时刷新铃铛（一键审批后待办/结果提醒立刻变化）
    try { window.dispatchEvent(new CustomEvent('erp:notification-refresh')) } catch (e2) { /* 忽略 */ }
  } catch (e) {
    ElMessage.error(tt('批量审批失败：') + (e.message || e))
  } finally {
    approving.batch = false
  }
}

const goToSection = (type) => {
  const map = {
    pending: '/approval',
    overdue: '/dashboard/overdue',
    drafts: '/dashboard/drafts',
    arAp: '/finance/ar'
  }
  if (map[type]) router.push(map[type])
}

const goToList = (path) => router.push(path)

const loadOverview = async () => {
  loading.overview = true
  try { overview.value = await getMyOverview() || {} }
  catch (e) { console.error('加载个人概览失败', e) }
  finally { loading.overview = false }
}

const loadPending = async () => {
  loading.pending = true
  try { pendingApprovals.value = await getMyPendingApprovals() || [] }
  catch (e) { console.error('加载待审批失败', e) }
  finally { loading.pending = false }
}

const loadOverdue = async () => {
  loading.overdue = true
  try { overdueTasks.value = await getMyOverdueTasks() || [] }
  catch (e) { console.error('加载超期任务失败', e) }
  finally { loading.overdue = false }
}

const loadDrafts = async () => {
  loading.drafts = true
  try { myDrafts.value = await getMyDrafts() || [] }
  catch (e) { console.error('加载草稿失败', e) }
  finally { loading.drafts = false }
}

const loadArAp = async () => {
  loading.arAp = true
  try { arApFollowUp.value = await getMyArApFollowUp() || [] }
  catch (e) { console.error('加载应收应付跟进失败', e) }
  finally { loading.arAp = false }
}

// ========== 报表图表（默认仪表盘嵌入工作台） ==========
const defaultDashId = ref(null)
const defaultDashName = ref('')
const dashChartCards = ref([])
const wbChartEls = new Map()      // idx -> DOM 元素
const wbChartInstances = new Map() // idx -> echarts 实例

const NUMERIC_COL_TYPES = ['amount', 'number', 'int']
const isNumericCol = col => NUMERIC_COL_TYPES.includes(col.type)

/** 加载默认仪表盘：优先取"默认仪表盘"，否则取第一个启用的仪表盘；只展示汇总+图表卡片（最多4张） */
async function loadDefaultDashboard() {
  try {
    const dashList = await listDashboards({ page: 1, size: 50 })
    const records = dashList?.records || []
    const dash = records.find(d => d.dashName === '默认仪表盘' && d.status === '启用')
      || records.find(d => d.status === '启用')
    if (!dash) return
    defaultDashId.value = dash.dashId
    defaultDashName.value = dash.dashName
    const dashData = await getDashboardData(dash.dashId)
    dashChartCards.value = (dashData?.cards || [])
      .filter(c => !c.error && c.mode === 'summary' && c.chartType)
      .slice(0, 4)
    await nextTick()
    renderWbCharts()
  } catch (e) {
    // 仪表盘加载失败不影响工作台其它模块（整块隐藏即可）
    console.error('加载默认仪表盘失败', e)
  }
}

function setWbChartEl(el, idx) {
  if (el) wbChartEls.set(idx, el)
  else wbChartEls.delete(idx)
}

/** 图表渲染逻辑与 CustomDashboardView.vue 保持一致 */
function renderWbCharts() {
  dashChartCards.value.forEach((c, idx) => {
    const el = wbChartEls.get(idx)
    if (!el || !c.chartType) return
    const cols = c.columns || []
    const records = c.records || []
    const numericCols = cols.filter(isNumericCol)
    const dimCol = cols.find(col => !isNumericCol(col))
    if (!numericCols.length || !dimCol) return

    let inst = wbChartInstances.get(idx)
    if (inst) inst.dispose()
    inst = echarts.init(el)
    wbChartInstances.set(idx, inst)

    const dimVals = records.map(r => r[dimCol.field] ?? '')
    if (c.chartType === 'pie') {
      const agg = numericCols[0]
      inst.setOption({
        tooltip: { trigger: 'item', formatter: '{b}<br/>{c} ({d}%)' },
        legend: { type: 'scroll', orient: 'vertical', left: 'left', top: 'middle' },
        series: [{
          type: 'pie',
          radius: ['35%', '62%'],
          center: ['55%', '55%'],
          data: records.map(r => ({ name: r[dimCol.field] ?? '', value: Number(r[agg.field]) || 0 }))
        }]
      }, true)
      return
    }
    inst.setOption({
      tooltip: { trigger: 'axis' },
      legend: { top: 4, data: numericCols.map(col => col.label), type: 'scroll' },
      grid: { left: 56, right: 20, top: 40, bottom: 46 },
      xAxis: { type: 'category', data: dimVals, axisLabel: { rotate: 30, interval: 0, hideOverlap: true } },
      yAxis: { type: 'value' },
      series: numericCols.map(col => ({
        name: col.label,
        type: c.chartType === 'line' ? 'line' : 'bar',
        smooth: c.chartType === 'line',
        data: records.map(r => Number(r[col.field]) || 0)
      }))
    }, true)
  })
}

function handleWbResize() {
  wbChartInstances.forEach(inst => inst.resize())
}

onMounted(() => {
  loadOverview()
  loadPending()
  loadOverdue()
  loadDrafts()
  loadArAp()
  loadDefaultDashboard()
  loadWbCards()
  window.addEventListener('resize', handleWbResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleWbResize)
  wbChartInstances.forEach(inst => inst.dispose())
  wbChartInstances.clear()
  wbChartEls.clear()
})
</script>

<style scoped lang="scss">
.my-workspace {
  padding: 16px;
  background: #f5f7fa;
  min-height: calc(100vh - 60px);

  .welcome-card {
    border-radius: 12px;
    border: none;
    margin-bottom: 16px;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    color: #fff;

    :deep(.el-card__body) { padding: 20px 24px; }

    .welcome-inner {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .welcome-text {
      .hello { font-size: 22px; font-weight: 600; margin-bottom: 6px; }
      .sub { font-size: 13px; opacity: 0.9; }
    }

    .welcome-icon { opacity: 0.5; }
  }

  .stats-row {
    margin-bottom: 16px;

    .stat-card {
      border-radius: 12px;
      border: none;
      cursor: pointer;
      transition: transform 0.2s ease, box-shadow 0.2s ease;

      &:hover {
        transform: translateY(-2px);
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
      }

      :deep(.el-card__body) { padding: 18px 20px; }

      .stat-content {
        display: flex;
        align-items: center;
        gap: 14px;

        .stat-icon {
          width: 52px; height: 52px;
          border-radius: 10px;
          display: flex; align-items: center; justify-content: center;
          flex-shrink: 0;
        }

        .stat-info {
          flex: 1; min-width: 0;

          .stat-value {
            font-size: 26px; font-weight: 700; line-height: 1.2; margin-bottom: 4px;

            &.val-danger { color: #f56c6c; }
            &.val-warning { color: #e6a23c; }
            &.val-info { color: #909399; }
            &.val-primary { color: #409eff; }
          }

          .stat-label { font-size: 13px; color: #606266; margin-bottom: 4px; }
          .stat-hint { font-size: 11px; color: #909399; }
        }
      }
    }
  }

  .performance-row {
    margin-bottom: 16px;

    .perf-card {
      border-radius: 12px;
      border: none;

      :deep(.el-card__body) { padding: 16px 20px; }

      .perf-inner {
        display: flex; align-items: center; justify-content: space-between;

        .perf-left {
          .perf-label { font-size: 12px; color: #909399; margin-bottom: 4px; }
          .perf-value { font-size: 20px; font-weight: 600; color: #303133; }
        }
      }
    }
  }

  .task-card {
    border-radius: 12px;
    border: none;
    margin-bottom: 16px;

    :deep(.el-card__header) {
      padding: 14px 20px;
      border-bottom: 1px solid #f5f7fa;
    }

    :deep(.el-card__body) { padding: 8px 0; }

    .card-header {
      display: flex; align-items: center; justify-content: space-between;

      .title-wrap {
        display: flex; align-items: center; gap: 8px;

        .title-icon { font-size: 18px; }
        .card-title { font-size: 16px; font-weight: 600; color: #303133; }
      }

      .header-right-wrap {
        display: flex; align-items: center; gap: 12px;
      }
    }

    .empty-tip { padding: 30px 20px; }

    .task-list {
      .task-item {
        display: flex; align-items: center; justify-content: space-between;
        padding: 12px 20px;
        border-bottom: 1px solid #fafafa;
        transition: background-color 0.2s ease;

        &:last-child { border-bottom: none; }
        &:hover { background-color: #f5f7fa; }

        &.clickable { cursor: pointer; }

        &.overdue .task-title { color: #f56c6c; }
        &.urgent .task-title { color: #e6a23c; }
        &.soon .task-title { color: #409eff; }

        .task-left {
          display: flex; align-items: flex-start; gap: 10px;
          flex: 1; min-width: 0;

          .task-content {
            flex: 1; min-width: 0;

            .task-title {
              font-size: 14px; color: #303133; margin-bottom: 4px;
              white-space: nowrap; overflow: hidden; text-overflow: ellipsis;

              .bill-no { color: #909399; font-weight: normal; font-size: 12px; margin-left: 8px; }
            }

            .task-meta {
              font-size: 12px; color: #909399;
              display: flex; align-items: center; gap: 6px;

              .meta-divider { color: #dcdfe6; }
              .amount { color: #67c23a; font-weight: 500; }
              .amount-due { color: #f56c6c; font-weight: 500; }
              .text-danger { color: #f56c6c; font-weight: 500; }
              .text-warning { color: #e6a23c; font-weight: 500; }
              .text-info { color: #909399; }
            }
          }
        }

        .task-right {
          display: flex; align-items: center; gap: 6px; flex-shrink: 0;

          .go-icon { color: #c0c4cc; }
        }

        &.clickable:hover .go-icon { color: #409eff; }
      }

      &.compact .task-item { padding: 10px 20px; }

      .show-more {
        padding: 10px;
        text-align: center;
        font-size: 13px;
        color: #409eff;
        cursor: pointer;
        border-top: 1px solid #f5f7fa;

        &:hover { background-color: #f5f7fa; }
      }
    }
  }

  // 报表图表（默认仪表盘嵌入）
  .chart-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;

    .chart-cell {
      border: 1px solid #ebeef5;
      border-radius: 6px;
      padding: 10px 12px;

      .chart-cell-title {
        font-size: 13px;
        font-weight: 600;
        color: #303133;
        margin-bottom: 8px;
      }

      .chart-cell-body { height: 260px; }
    }
  }

  @media (max-width: 1200px) {
    .chart-grid { grid-template-columns: 1fr; }
  }
}

/* ===== 首页卡片管理（网格排序 + 折叠） ===== */
.dash-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.dash-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  align-items: start;
}

.dash-cell {
  position: relative;
  min-width: 0;

  &.span-2 { grid-column: span 2; }
  &.span-1 { grid-column: span 1; }

  /* 卡片自身下外边距交给网格 gap，避免出现双层间距 */
  :deep(.el-card),
  .welcome-card,
  .stats-row,
  .performance-row,
  .task-card { margin-bottom: 0; }
}

/* 折叠按钮：有标题栏的卡片放在标题栏右侧，无标题栏的卡片浮动在右上角 */
.collapse-caret {
  cursor: pointer;
  color: #909399;
  font-size: 16px;
  flex-shrink: 0;
  transition: color 0.2s;

  &:hover { color: #409eff; }
}

.card-header .title-wrap { cursor: pointer; }

/* 无标题栏卡片（统计卡/简报）的折叠按钮：悬停才显现，保证默认外观与改造前一致 */
.float-caret {
  position: absolute;
  top: 6px;
  right: 6px;
  z-index: 2;
  padding: 2px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.9);
  opacity: 0;
  transition: opacity 0.2s;
}

.dash-cell:hover .float-caret { opacity: 1; }
.wb-caret { margin-left: auto; cursor: pointer; color: #94a3b8; }
.wb-caret:hover { color: #409eff; }
.wb-collapsed-mask {
  position: absolute;
  inset: 0;
  background: rgba(255, 255, 255, 0.55);
  cursor: pointer;
}

.welcome-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.welcome-caret { color: rgba(255, 255, 255, 0.85); }
.welcome-caret:hover { color: #fff; }

/* 折叠态：只隐藏卡片内容区，标题栏保留 */
.task-card.card-collapsed :deep(.el-card__body) { display: none; }

@media (max-width: 768px) {
  .dash-grid { grid-template-columns: 1fr; }

  .my-workspace {
    padding: 12px;

    .stats-row .stat-card :deep(.el-card__body) { padding: 14px; }

    .task-card :deep(.el-card__body) { padding: 4px 0; }

    .task-list .task-item { padding: 10px 14px; }
  }
}
</style>
