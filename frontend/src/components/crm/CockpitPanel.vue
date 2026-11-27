<template>
  <div class="ck-panel">
    <!-- 目标链 -->
    <div class="ck-chain" v-if="rows.length">
      <template v-for="(r, i) in rows" :key="r.level">
        <div class="ck-node">
          <div class="ck-node-label">{{ shortLevel(r.level) }}</div>
          <div class="ck-node-val">{{ money(r.target) }}</div>
        </div>
        <span v-if="i < rows.length - 1" class="ck-arrow">→</span>
      </template>
    </div>
    <div v-else class="ck-empty">{{ breakdownReason || $tt("暂无目标数据 —— 请先在销售目标里分配年度目标") }}</div>

    <!-- 四级业绩 -->
    <div class="ck-grid" v-if="rows.length">
      <div v-for="r in rows" :key="'k' + r.level" class="ck-card">
        <div class="ck-card-label">{{ r.level }}</div>
        <div class="ck-card-val">{{ money(r.actual) }}</div>
        <div class="ck-card-sub">{{ $tt("目标") }} {{ money(r.target) }}</div>
        <div class="ck-bar"><i :style="{ width: barW(r.rate), background: barColor(r.status) }"></i></div>
        <div class="ck-rate" :class="rateClass(r.status)">{{ r.rate }}%</div>
      </div>
    </div>

    <!-- 销售漏斗（复用「销售漏斗」页数据，简化为横向条形） -->
    <div class="ck-block">
      <div class="ck-block-title">{{ $tt("销售漏斗（商机阶段分布）") }}</div>
      <div v-if="funnelTotal > 0" class="ck-funnel">
        <div v-for="s in funnelStages" :key="s.name" class="ck-funnel-row">
          <div class="ck-funnel-label">{{ s.name }}</div>
          <div class="ck-funnel-bar">
            <i :style="{ width: funnelBarW(s.count), background: s.color }"></i>
            <span class="ck-funnel-count">{{ s.count }} 个</span>
          </div>
        </div>
      </div>
      <div v-else class="ck-empty">{{ $tt("暂无数据") }}</div>
    </div>

    <!-- 主管 / 总经理视角：人员排名 / 未达标人员 -->
    <div class="ck-block" v-if="isSupervisor">
      <div class="ck-block-title">{{ $tt("人员排名 / 未达标人员") }}</div>
      <div v-if="rankList.length" class="ck-rank">
        <div class="ck-rank-col">
          <div class="ck-sub-title">{{ $tt("完成率前 5 名") }}</div>
          <div v-for="(p, i) in topRankers" :key="'t' + i" class="ck-rank-row">
            <span class="ck-rank-no">{{ i + 1 }}</span>
            <span class="ck-rank-name">{{ p.name }}</span>
            <span class="ck-rank-target">{{ money(p.target) }}</span>
            <span class="ck-rank-rate" :class="rateClass(p.status)">{{ p.rateText }}</span>
          </div>
        </div>
        <div class="ck-rank-col">
          <div class="ck-sub-title">{{ $tt("未达标人员（完成率低于 60%）") }}</div>
          <template v-if="underperformers.length">
            <div v-for="(p, i) in underperformers" :key="'u' + i" class="ck-rank-row">
              <span class="ck-rank-name">{{ p.name }}</span>
              <span class="ck-rank-target">{{ money(p.target) }}</span>
              <span class="ck-rank-rate bad">{{ p.rateText }}</span>
            </div>
          </template>
          <div v-else class="ck-muted">{{ $tt("暂无未达标人员") }}</div>
        </div>
      </div>
      <div v-else class="ck-empty">{{ $tt("暂无数据") }}</div>
    </div>

    <!-- 收口一行：预警 + 在途运单/物流异常 + 奖金 -->
    <div class="ck-foot">
      <span>{{ $tt("待处理预警") }} <b>{{ warn['未处理'] || 0 }}</b> 条（{{ $tt("红") }} {{ warn['红'] || 0 }} / {{ $tt("橙") }} {{ warn['橙'] || 0 }} / {{ $tt("黄") }} {{ warn['黄'] || 0 }}）</span>
      <span class="ck-sep">|</span>
      <span>
        {{ $tt("我的在途运单") }}
        <template v-if="logistics.loaded && logistics.inTransit > 0">
          <b>{{ logistics.inTransit }}</b> {{ $tt("单") }}
          <span class="ck-sep">·</span>
          {{ $tt("物流异常") }} <b :class="{ bad: logistics.abnormal > 0 }">{{ logistics.abnormal }}</b> {{ $tt("单") }}
        </template>
        <span v-else class="ck-muted">{{ $tt("暂无数据") }}</span>
      </span>
      <span class="ck-sep">|</span>
      <span>{{ $tt("本月过程奖金预测") }} <b>{{ bonus.bonusAmount || 0 }}</b> 元</span>
      <div style="flex:1"></div>
      <el-button link type="primary" @click="$router.push('/crm/warning')">{{ $tt("去处理预警") }}</el-button>
      <el-button link type="primary" @click="$router.push({ path: '/crm/sales-activity', query: { tab: 'daily' } })">{{ $tt("看每日任务") }}</el-button>
    </div>

    <div class="ck-bonus" v-if="bonus.dailyRate !== undefined">
      <span>{{ $tt("每日") }} {{ showRate(bonus.dailyRate) }}</span>
      <span>{{ $tt("每周") }} {{ showRate(bonus.weeklyRate) }}</span>
      <span>{{ $tt("私海联系率") }} {{ showRate(bonus.privateContactRate) }}</span>
      <span>{{ $tt("RFID 覆盖") }} {{ showRate(bonus.rfidRate) }}</span>
      <span :class="bonus.allPass ? 'ok' : 'bad'">{{ bonus.allPass ? '四项全达标' : '未全达标（奖金 0 元）' }}</span>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { getBreakdown } from '@/api/crm/salesTargetBreakdown'
import { getBonus } from '@/api/crm/salesActivity'
import { salesWarningSummary } from '@/api/crm/salesWarning'
import { opportunityDashboard } from '@/api/crm/dashboard'
import { pageEmployee } from '@/api/crm/salesTarget'
import { listInTransit, batchQueryLogistics } from '@/api/logistics/logistics'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const rows = ref([])
const breakdownReason = ref('')
const warn = reactive({})
const bonus = reactive({})
const thisYear = new Date().getFullYear()

// ===== 主管 / 总经理判断（口径同销售预警页：ADMIN / SUPER_ADMIN / SALES_MANAGER） =====
const isSupervisor = computed(() => {
  const roles = Array.isArray(userStore.roles) ? userStore.roles : []
  return roles.includes('SALES_MANAGER') || roles.includes('ADMIN') || roles.includes('SUPER_ADMIN')
})

// ===== 销售漏斗（阶段与颜色同「销售漏斗」页，数据走同一接口） =====
const FUNNEL_DEFS = [
  { name: '初步接触', color: '#409EFF' },
  { name: '需求确认', color: '#67C23A' },
  { name: '方案报价', color: '#E6A23C' },
  { name: '商务谈判', color: '#F56C6C' },
  { name: '成交', color: '#909399' }
]
const funnelStages = ref(FUNNEL_DEFS.map(s => ({ ...s, count: 0 })))
const funnelTotal = computed(() => funnelStages.value.reduce((s, x) => s + (Number(x.count) || 0), 0))
const maxFunnelCount = computed(() => Math.max(1, ...funnelStages.value.map(s => Number(s.count) || 0)))
function funnelBarW(count) {
  return Math.max(6, ((Number(count) || 0) / maxFunnelCount.value) * 100) + '%'
}

// ===== 我的在途运单与物流异常（复用物流看板同源取数） =====
const logistics = reactive({ loaded: false, inTransit: 0, abnormal: 0 })
const ABNORMAL_STATUS_CODES = ['2', '4', '6']

// ===== 人员排名 / 未达标人员（复用销售目标 · 销售员目标列表接口） =====
const rankList = ref([])
const topRankers = computed(() => rankList.value.slice(0, 5))
const underperformers = computed(() => rankList.value.filter(p => p.rate < 60))

function money(v) {
  if (v === null || v === undefined || v === '') return '0'
  const n = Number(v)
  if (!isFinite(n)) return '0'
  if (Math.abs(n) >= 10000) return (n / 10000).toFixed(2) + ' 万'
  return n.toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}
function shortLevel(l) {
  if (!l) return ''
  if (l.startsWith('年')) return '年'
  if (l.startsWith('季')) return '季'
  if (l.startsWith('月')) return '月'
  if (l.startsWith('周')) return '周'
  if (l.startsWith('日')) return '日'
  return l
}
function barW(rate) { return Math.min(100, Number(rate || 0)) + '%' }
function barColor(s) { return s === '绿' ? '#67c23a' : (s === '黄' ? '#e6a23c' : '#f56c6c') }
function rateClass(s) { return s === '绿' ? 'ok' : (s === '黄' ? 'warn' : 'bad') }
function showRate(v) {
  if (v === null || v === undefined || v < 0) return '暂无数据'
  return v + '%'
}

async function load() {
  try {
    const d = await getBreakdown({ year: thisYear })
    rows.value = d?.rows || []
    if (d && d.available === false) breakdownReason.value = d.reason || ''
  } catch (e) { /* 忽略 */ }
  try { Object.assign(warn, await salesWarningSummary() || {}) } catch (e) { /* 忽略 */ }
  try {
    Object.assign(bonus, await getBonus({ year: thisYear, month: new Date().getMonth() + 1 }) || {})
  } catch (e) { /* 忽略 */ }
  try {
    const opp = await opportunityDashboard() || {}
    const stageDist = opp.stageDist || {}
    funnelStages.value = FUNNEL_DEFS.map(s => ({ ...s, count: Number(stageDist[s.name]) || 0 }))
  } catch (e) { /* 忽略 */ }
  // 排名与物流单独加载，互不阻塞
  loadRanking()
  loadLogistics()
}

/** 人员排名：取年度目标金额与已完成金额算完成率，倒序取前 5，并列出低于 60% 的未达标人员 */
async function loadRanking() {
  if (!isSupervisor.value) { rankList.value = []; return }
  try {
    const res = await pageEmployee({ current: 1, size: 500, year: thisYear })
    const records = (res && res.records) || []
    const list = []
    records.forEach(r => {
      const name = r.employeeName || ''
      if (!name) return
      const target = Number(r.annualTargetAmount)
      const actual = Number(r.actualAmount)
      let rate = null
      if (isFinite(target) && target > 0 && isFinite(actual)) {
        rate = (actual / target) * 100
      } else {
        const cr = Number(r.completionRate)
        if (isFinite(cr)) rate = cr
      }
      // 目标为 0 或拿不到完成率的一律跳过，不编造数字
      if (rate === null || !isFinite(rate)) return
      list.push({ name, target: isFinite(target) ? target : null, rate })
    })
    list.sort((a, b) => b.rate - a.rate)
    rankList.value = list.map(p => ({
      ...p,
      rateText: Math.round(p.rate) + '%',
      status: p.rate >= 100 ? '绿' : (p.rate >= 60 ? '黄' : '红')
    }))
  } catch (e) {
    rankList.value = []
  }
}

/** 在途运单数量 = 在途出库单列表长度；物流异常数量 = 轨迹状态命中异常的单数 */
async function loadLogistics() {
  try {
    const res = await listInTransit({ limit: 50 })
    const list = Array.isArray(res) ? res : (res?.data || [])
    logistics.inTransit = Array.isArray(list) ? list.length : 0
    const items = (list || [])
      .filter(r => r && r.trackingNo)
      .map(r => ({ trackingNo: r.trackingNo, carrier: r.carrier || '', phone: r.contactPhone || '' }))
    let abnormal = 0
    // 分批查询（每批最多 20 单，与物流看板同源），单批失败不影响其他批
    for (let i = 0; i < items.length; i += 20) {
      const batch = items.slice(i, i + 20)
      try {
        const br = await batchQueryLogistics(batch)
        const results = Array.isArray(br) ? br : (br?.data || [])
        ;(results || []).forEach(t => {
          if (t && ABNORMAL_STATUS_CODES.includes(t.statusCode)) abnormal++
        })
      } catch (e) { /* 忽略单批失败 */ }
    }
    logistics.abnormal = abnormal
    logistics.loaded = true
  } catch (e) {
    logistics.loaded = false
  }
}

onMounted(load)
</script>

<style scoped>
.ck-panel { padding-bottom: 4px; }
.ck-chain { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 14px; }
.ck-node { flex: 1 1 0; min-width: 110px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; padding: 10px 12px; background: var(--el-fill-color-blank); }
.ck-node-label { font-size: 12px; color: #909399; margin-bottom: 4px; }
.ck-node-val { font-size: 16px; font-weight: 600; font-variant-numeric: tabular-nums; }
.ck-arrow { color: #c0c4cc; }
.ck-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.ck-card { border: 1px solid var(--el-border-color-lighter); border-radius: 8px; padding: 12px; }
.ck-card-label { font-size: 12px; color: #909399; }
.ck-card-val { font-size: 18px; font-weight: 600; margin: 6px 0 2px; font-variant-numeric: tabular-nums; }
.ck-card-sub { font-size: 12px; color: #909399; }
.ck-bar { height: 4px; border-radius: 2px; background: var(--el-border-color-lighter); margin-top: 8px; overflow: hidden; }
.ck-bar i { display: block; height: 100%; border-radius: 2px; }
.ck-rate { font-size: 12px; font-weight: 600; margin-top: 6px; }
.ck-foot { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-top: 14px; padding-top: 12px; border-top: 1px solid var(--el-border-color-lighter); font-size: 13px; }
.ck-foot b { font-size: 16px; color: var(--el-color-primary); }
.ck-sep { color: #dcdfe6; }
.ck-bonus { display: flex; gap: 16px; flex-wrap: wrap; margin-top: 8px; font-size: 12px; color: #909399; }
.ck-empty { padding: 20px 0; text-align: center; color: #909399; font-size: 13px; }
.ck-muted { color: #909399; }
.ok { color: #67c23a; }
.warn { color: #e6a23c; }
.bad { color: #f56c6c; }

/* 销售漏斗 */
.ck-block { margin-top: 14px; padding-top: 12px; border-top: 1px solid var(--el-border-color-lighter); }
.ck-block-title { font-size: 13px; font-weight: 600; color: #303133; margin-bottom: 10px; }
.ck-funnel { display: flex; flex-direction: column; gap: 8px; }
.ck-funnel-row { display: flex; align-items: center; gap: 10px; }
.ck-funnel-label { width: 84px; font-size: 12px; color: #606266; }
.ck-funnel-bar { flex: 1; height: 22px; background: #F5F7FA; border-radius: 4px; position: relative; overflow: hidden; }
.ck-funnel-bar i { display: block; height: 100%; border-radius: 4px; transition: width 0.4s ease; }
.ck-funnel-count { position: absolute; left: 8px; top: 50%; transform: translateY(-50%); font-size: 12px; color: #fff; text-shadow: 0 1px 2px rgba(0, 0, 0, 0.3); }

/* 人员排名 */
.ck-rank { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.ck-rank-col { min-width: 0; }
.ck-sub-title { font-size: 12px; color: #909399; margin-bottom: 8px; }
.ck-rank-row { display: flex; align-items: center; gap: 8px; font-size: 13px; padding: 4px 0; }
.ck-rank-no { width: 18px; height: 18px; line-height: 18px; text-align: center; border-radius: 50%; background: var(--el-fill-color); color: #606266; font-size: 12px; flex-shrink: 0; }
.ck-rank-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ck-rank-target { color: #909399; font-size: 12px; font-variant-numeric: tabular-nums; }
.ck-rank-rate { font-weight: 600; font-variant-numeric: tabular-nums; }
</style>
