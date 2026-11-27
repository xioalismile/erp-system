<template>
  <div class="erp-page">
    <div class="page-title-bar">
      <div class="page-title">
        <el-icon style="margin-right:8px;color:#409EFF;"><DataLine /></el-icon>
        {{ $tt("销售过程") }}
      </div>
      <div class="page-sub">
        {{ $tt("日任务 / 推广记录 / 过程奖金 · 任务量与预警阈值见【设置中心 → CRM 预警设置】") }}
      </div>
    </div>

    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <!-- ==================== 页签 1：每日任务 ==================== -->
        <el-tab-pane :label="$tt('每日任务')" name="daily">
          <div class="toolbar">
            <el-date-picker v-model="daily.date" type="date" value-format="YYYY-MM-DD"
              :clearable="false" style="width:150px;" @change="loadDaily" />
            <el-button type="primary" @click="loadDaily">{{ $tt("刷新") }}</el-button>
            <el-button @click="handleSupplement">{{ $tt("补录前一天") }}</el-button>
            <el-select v-if="canSwitchStaff" v-model="daily.staffId" :placeholder="$tt('全部人员')"
              clearable filterable style="width:180px;" @change="loadDaily">
              <el-option v-for="e in staffOptions" :key="e.value" :label="e.label" :value="e.value" />
            </el-select>
            <el-tag v-if="daily.weekStart" size="small" type="info" effect="plain">
              {{ $tt("本周") }} {{ daily.weekStart }} ~ {{ daily.weekEnd }}
            </el-tag>
            <div style="flex:1"></div>
            <el-tag v-if="daily.statStartDate" size="small" type="warning" effect="plain">
              {{ $tt("过程达标统计自") }} {{ daily.statStartDate }} {{ $tt("起算，之前的历史记录不参与达标") }}
            </el-tag>
          </div>

          <div class="sec-title">{{ $tt("今日任务") }}</div>
          <div class="metric-grid">
            <div v-for="m in daily.daily" :key="m.key" class="metric-card">
              <div class="metric-label">{{ m.label }}</div>
              <div class="metric-value" :class="metricClass(m)">
                {{ m.done }}<span class="metric-unit">/{{ m.target }} {{ m.unit }}</span>
              </div>
              <div class="metric-bar"><i :style="{ width: barWidth(m), background: barColor(m) }"></i></div>
            </div>
          </div>

          <div class="sec-title" style="margin-top:16px;">{{ $tt("本周任务") }}</div>
          <div class="metric-grid c3">
            <div v-for="m in daily.weekly" :key="m.key" class="metric-card">
              <div class="metric-label">{{ m.label }}</div>
              <div class="metric-value" :class="metricClass(m)">
                {{ m.done }}<span class="metric-unit">/{{ m.target }} {{ m.unit }}</span>
              </div>
              <div class="metric-bar"><i :style="{ width: barWidth(m), background: barColor(m) }"></i></div>
            </div>
          </div>

          <div class="sec-title" style="margin-top:16px;">{{ $tt("今日任务清单") }}</div>
          <el-table :data="daily.tasks" border stripe size="small">
            <el-table-column :label="$tf('客户')" min-width="180">
              <template #default="{ row }">{{ row.sourceName || '' }}</template>
            </el-table-column>
            <el-table-column :label="$tf('任务类型')" width="120">
              <template #default="{ row }">{{ row.taskType || row.followUpType || '' }}</template>
            </el-table-column>
            <el-table-column :label="$tf('建议方式')" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.suggestMethod || row.suggestWay || row.content || '' }}</template>
            </el-table-column>
            <el-table-column :label="$tf('截止时间')" width="120">
              <template #default="{ row }">{{ row.dueDate || '' }}</template>
            </el-table-column>
            <el-table-column :label="$tf('状态')" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.status" size="small" effect="plain">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('操作')" width="90" align="center">
              <template #default="{ row }">
                <el-button type="primary" link size="small" v-permission="perm('add')" @click="goFollowUp(row)">{{ $tt("录入") }}</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <div class="empty-tip">{{ $tt("今日暂无待办任务") }}</div>
            </template>
          </el-table>

          <div class="bonus-line" :class="monthBonus.allPass ? 'pass' : 'fail'">
            <span class="bonus-title">{{ $tt("本月过程奖金") }}</span>
            <span class="bonus-amount">{{ monthBonus.bonusAmount || 0 }} 元</span>
            <el-tag size="small" :type="condOk(monthBonus.dailyRate) ? 'success' : 'info'" effect="plain">
              {{ $tt("每日") }} {{ condOk(monthBonus.dailyRate) ? $tt("达标") : $tt("未达标") }}
            </el-tag>
            <el-tag size="small" :type="condOk(monthBonus.weeklyRate) ? 'success' : 'info'" effect="plain">
              {{ $tt("每周") }} {{ condOk(monthBonus.weeklyRate) ? $tt("达标") : $tt("未达标") }}
            </el-tag>
            <el-tag size="small" :type="condOk(monthBonus.privateContactRate) ? 'success' : 'info'" effect="plain">
              {{ $tt("私海联系率") }} {{ condOk(monthBonus.privateContactRate) ? $tt("达标") : $tt("未达标") }}
            </el-tag>
            <el-tag size="small" :type="condOk(monthBonus.rfidRate) ? 'success' : 'info'" effect="plain">
              {{ $tt("RFID覆盖") }} {{ condOk(monthBonus.rfidRate) ? $tt("达标") : $tt("未达标") }}
            </el-tag>
            <span class="bonus-cond">{{ $tt("四项全部 100% 才发放，任一不足即为 0 元") }}</span>
          </div>
        </el-tab-pane>

        <!-- ==================== 页签 2：推广记录 ==================== -->
        <el-tab-pane :label="$tt('推广记录')" name="promotion" lazy>
          <PromotionRecordPanel v-if="activeTab === 'promotion'" />
        </el-tab-pane>

        <!-- ==================== 页签 3：过程奖金 ==================== -->
        <el-tab-pane :label="$tt('过程奖金')" name="bonus" lazy>
          <div class="toolbar">
            <el-date-picker v-model="bonusMonth" type="month" value-format="YYYY-MM"
              :clearable="false" style="width:150px;" @change="loadBonusRows" />
            <el-button type="primary" @click="loadBonusRows">{{ $tt("刷新") }}</el-button>
            <div style="flex:1"></div>
            <el-button v-permission="perm('export')" @click="handleExport">{{ $tt("导出") }}</el-button>
          </div>

          <el-table ref="tableRef" :data="bonusRows" v-loading="bonusLoading" border stripe>
            <el-table-column type="expand">
              <template #default="{ row }">
                <div class="day-grid" style="padding:8px 16px;">
                  <div v-for="(ok, d) in row.dayDetail" :key="d" class="day-cell" :class="ok ? 'day-ok' : 'day-no'">
                    <div class="day-date">{{ String(d).slice(5) }}</div>
                    <div class="day-flag">{{ ok ? '达标' : '未达标' }}</div>
                  </div>
                  <div v-if="!row.dayDetail || Object.keys(row.dayDetail).length === 0" class="empty-tip">
                    {{ $tt("该月暂无每日达标明细") }}
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('销售')" min-width="120">
              <template #default="{ row }">{{ row.staffName || '' }}</template>
            </el-table-column>
            <el-table-column :label="$tf('每日完成率')" width="130" align="center">
              <template #default="{ row }">
                <span class="rate-click" :class="rateClass(row.dailyRate)" @click="toggleExpand(row)">
                  {{ showRate(row.dailyRate) }}
                </span>
                <div class="rate-sub">{{ $tt("达标") }} {{ row.passDays || 0 }}/{{ row.workDays || 0 }} {{ $tt("天") }}</div>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('每周完成率')" width="130" align="center">
              <template #default="{ row }">
                <span :class="rateClass(row.weeklyRate)">{{ showRate(row.weeklyRate) }}</span>
                <div class="rate-sub">{{ $tt("达标") }} {{ row.passWeeks || 0 }}/{{ row.weeks || 0 }} {{ $tt("周") }}</div>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('私海联系率')" width="120" align="center">
              <template #default="{ row }">
                <span :class="rateClass(row.privateContactRate)">{{ showRate(row.privateContactRate) }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('RFID覆盖率')" width="120" align="center">
              <template #default="{ row }">
                <span :class="rateClass(row.rfidRate)">{{ showRate(row.rfidRate) }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('是否全部完成')" width="120" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.allPass ? 'success' : 'danger'">
                  {{ row.allPass ? $tt("是") : $tt("否") }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('奖金')" width="110" align="right">
              <template #default="{ row }">{{ row.bonusAmount || 0 }} 元</template>
            </el-table-column>
            <template #empty>
              <div class="empty-tip">{{ $tt("暂无数据") }}</div>
            </template>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { DataLine } from '@element-plus/icons-vue'
import { getDaily, getBonus } from '@/api/crm/salesActivity'
import { listAllEmployee } from '@/api/basedata/employee'
import { useUserStore } from '@/stores/user'
import { exportCsv } from '@/utils/csvExport'
import { tt } from '@/i18n'
import PromotionRecordPanel from '@/components/crm/PromotionRecordPanel.vue'
import { usePagePerm } from '@/utils/pagePerm'

// 页面对象 crm:sales-activity（按钮权限，默认口径：能看见菜单即全部按钮可见）
const perm = usePagePerm('crm:sales-activity')

// 支持从「销售预警 → 去处理」直达指定页签，例如 /crm/sales-activity?tab=promotion
const activeTab = ref(['daily', 'promotion', 'bonus'].includes(useRoute().query.tab)
  ? useRoute().query.tab : 'daily')

const router = useRouter()
const userStore = useUserStore()

// 主管 / 管理员可切换查看某个员工；普通销售不渲染人员切换
const canSwitchStaff = computed(() => {
  const roles = Array.isArray(userStore.roles) ? userStore.roles : []
  return roles.includes('ADMIN') || roles.includes('SUPER_ADMIN') || roles.includes('SALES_MANAGER')
})

const today = new Date().toISOString().slice(0, 10)
const thisMonth = today.slice(0, 7)

const daily = reactive({ date: today, staffId: null, daily: [], weekly: [], tasks: [], weekStart: '', weekEnd: '', statStartDate: '' })
// 页签 1 底部「本月过程奖金」汇总速览（始终取当前月，与页签 3 的月份选择互不影响）
const monthBonus = reactive({})

// 页签 3：多销售过程奖金列表
const bonusMonth = ref(thisMonth)
const bonusRows = ref([])
const bonusLoading = ref(false)
const tableRef = ref(null)

// 员工下拉（仅主管/管理员加载）
const staffOptions = ref([])
let staffPromise = null
function loadStaffOptions() {
  if (!canSwitchStaff.value) return Promise.resolve([])
  if (!staffPromise) {
    staffPromise = listAllEmployee()
      .then(list => {
        const arr = Array.isArray(list) ? list : (list?.records || [])
        staffOptions.value = arr
          .map(e => ({ value: e.employeeId ?? e.id, label: e.employeeName ?? e.name ?? '' }))
          .filter(o => o.value != null)
        return staffOptions.value
      })
      .catch(() => { staffOptions.value = []; return [] })
  }
  return staffPromise
}

async function loadDaily() {
  const res = await getDaily({ date: daily.date, staffId: daily.staffId || undefined })
  Object.assign(daily, res || {})
}

async function loadMonthBonus() {
  const [y, m] = thisMonth.split('-')
  const res = await getBonus({ year: Number(y), month: Number(m) })
  Object.assign(monthBonus, res || {})
}

async function loadBonusRows() {
  bonusLoading.value = true
  try {
    const [y, m] = String(bonusMonth.value || thisMonth).split('-')
    const year = Number(y)
    const month = Number(m)
    let staffList
    if (canSwitchStaff.value) {
      const opts = await loadStaffOptions()
      staffList = opts.map(o => ({ staffId: o.value, staffName: o.label }))
    } else {
      staffList = [{ staffId: null, staffName: userStore.realName || userStore.username || '' }]
    }
    const results = await Promise.all(staffList.map(async s => {
      const res = await getBonus({ year, month, staffId: s.staffId || undefined })
      return { staffId: s.staffId, staffName: s.staffName, ...(res || {}) }
    }))
    bonusRows.value = results
  } finally {
    bonusLoading.value = false
  }
}

/** 「补录前一天」入口：按口径次日 12:00 前可补录，补录不计入当天达标、只计入当月总量 */
function handleSupplement() {
  ElMessage.warning(tt('补录入口：请到「跟进记录」按实际日期补录；补录记录不计入当天达标，只计入当月总量'))
  router.push('/crm/follow-up')
}

/**
 * 清单行「录入」：带上该客户跳「跟进记录」，并自动打开新增、预填来源。
 * 2026-10-09 用户口径：跳过去必须带条件，不能只给一个空列表让用户自己找客户。
 */
function goFollowUp(row) {
  const query = { add: '1' }
  if (row && row.sourceName) query.kw = row.sourceName
  if (row && row.targetId != null) query.sourceId = String(row.targetId)
  router.push({ path: '/crm/follow-up', query })
}

/** 展开/收起该销售的当月每日日历 */
function toggleExpand(row) {
  tableRef.value?.toggleRowExpansion(row)
}

/** 导出当前表格为 CSV（纯前端，文件名含所选月份） */
function handleExport() {
  if (!bonusRows.value.length) {
    ElMessage.warning(tt('暂无可导出的数据'))
    return
  }
  const columns = [
    { label: '销售', prop: 'staffName' },
    { label: '每日完成率', prop: 'dailyRateText' },
    { label: '每周完成率', prop: 'weeklyRateText' },
    { label: '私海联系率', prop: 'privateContactRateText' },
    { label: 'RFID覆盖率', prop: 'rfidRateText' },
    { label: '是否全部完成', prop: 'allPassText' },
    { label: '奖金', prop: 'bonusAmountText' }
  ]
  const rows = bonusRows.value.map(r => ({
    staffName: r.staffName || '',
    dailyRateText: rateText(r.dailyRate),
    weeklyRateText: rateText(r.weeklyRate),
    privateContactRateText: rateText(r.privateContactRate),
    rfidRateText: rateText(r.rfidRate),
    allPassText: r.allPass ? '是' : '否',
    bonusAmountText: (r.bonusAmount || 0) + ' 元'
  }))
  exportCsv(columns, rows, `销售过程奖金-${bonusMonth.value}`)
}

/** 私海超期是"越少越好"，达标线为 0；其余为"越多越好" */
function isReach(m) {
  if (m.key === 'privateOverdue') return (m.done || 0) <= 0
  if (m.target === 0) return true
  return (m.done || 0) >= m.target
}
function metricClass(m) {
  if (m.key === 'privateOverdue') return (m.done || 0) > 0 ? 'bad' : 'ok'
  return isReach(m) ? 'ok' : 'warn'
}
function barWidth(m) {
  if (!m.target) return '100%'
  return Math.min(100, Math.round((m.done || 0) * 100 / m.target)) + '%'
}
function barColor(m) {
  if (m.key === 'privateOverdue') return (m.done || 0) > 0 ? '#f56c6c' : '#67c23a'
  return isReach(m) ? '#67c23a' : '#e6a23c'
}
function rateClass(v) {
  if (v === null || v === undefined || v < 0) return 'muted'
  return v >= 100 ? 'ok' : (v >= 60 ? 'warn' : 'bad')
}
function showRate(v) {
  if (v === null || v === undefined || v < 0) return '暂无数据'
  return v + '%'
}
/** 导出用：无数据留空（不显示 -1 / NaN） */
function rateText(v) {
  if (v === null || v === undefined || v < 0) return ''
  return v + '%'
}
/** 奖金四项条件是否达标（四项全部 100% 才发放） */
function condOk(v) {
  return v !== null && v !== undefined && v >= 100
}

onMounted(() => {
  loadDaily()
  loadMonthBonus()
  if (canSwitchStaff.value) loadStaffOptions()
  loadBonusRows()
})
</script>

<style scoped>
.erp-page { padding: 12px; }
.page-title-bar { margin-bottom: 8px; }
.page-title { font-size: 16px; font-weight: 600; display: flex; align-items: center; }
.page-sub { font-size: 12px; color: #909399; margin-top: 4px; }
.toolbar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 12px; }
.sec-title { font-size: 13px; font-weight: 600; color: #303133; margin-bottom: 8px; }
.metric-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.metric-grid.c3 { grid-template-columns: repeat(3, 1fr); }
.metric-card { border: 1px solid var(--el-border-color-lighter); border-radius: 8px; padding: 12px; background: var(--el-fill-color-blank); }
.metric-label { font-size: 12px; color: #909399; margin-bottom: 8px; }
.metric-value { font-size: 20px; font-weight: 600; font-variant-numeric: tabular-nums; }
.metric-value.ok { color: #67c23a; }
.metric-value.warn { color: #e6a23c; }
.metric-value.bad { color: #f56c6c; }
.metric-value.muted { color: #909399; font-size: 14px; }
.metric-unit { font-size: 12px; font-weight: 400; color: #909399; margin-left: 2px; }
.metric-sub { font-size: 12px; color: #909399; margin-top: 6px; }
.metric-bar { height: 4px; border-radius: 2px; background: var(--el-border-color-lighter); margin-top: 8px; overflow: hidden; }
.metric-bar i { display: block; height: 100%; border-radius: 2px; }
.bonus-line { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-top: 16px; padding: 14px 16px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; }
.bonus-line.pass { border-color: #67c23a; }
.bonus-line.fail { border-color: #f56c6c; }
.bonus-title { font-size: 13px; color: #606266; }
.bonus-amount { font-size: 22px; font-weight: 700; color: #409EFF; }
.bonus-cond { font-size: 12px; color: #909399; }
.day-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.day-cell { width: 72px; border-radius: 6px; padding: 6px 8px; text-align: center; border: 1px solid var(--el-border-color-lighter); }
.day-ok { border-color: #b3e19d; background: #f0f9eb; }
.day-no { border-color: #fab6b6; background: #fef0f0; }
.day-date { font-size: 12px; color: #606266; }
.day-flag { font-size: 11px; margin-top: 2px; }
.day-ok .day-flag { color: #67c23a; }
.day-no .day-flag { color: #f56c6c; }
.empty-tip { padding: 20px 0; color: #909399; text-align: center; font-size: 13px; width: 100%; }
.hint { font-size: 12px; color: #909399; }
.rate-click { cursor: pointer; text-decoration: underline dotted; font-weight: 600; }
.rate-sub { font-size: 11px; color: #909399; margin-top: 2px; }
.ok { color: #67c23a; }
.warn { color: #e6a23c; }
.bad { color: #f56c6c; }
.muted { color: #909399; }
</style>
