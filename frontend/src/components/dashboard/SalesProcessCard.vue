<template>
  <el-card class="sales-process-card" shadow="never" v-loading="loading">
    <template #header>
      <div class="card-header">
        <div class="title-wrap" @click="$emit('toggle-collapse')">
          <el-icon class="title-icon" color="#409eff"><DataLine /></el-icon>
          <span class="card-title">{{ $tt("销售过程") }}</span>
          <span v-if="statStartDate" class="stat-start">{{ $tt("过程达标统计自") }} {{ statStartDate }} {{ $tt("起算") }}</span>
        </div>
        <div class="header-right-wrap">
          <el-button link type="primary" size="small" @click="goPage()">
            {{ $tt("查看全部") }}<el-icon><ArrowRight /></el-icon>
          </el-button>
          <el-icon class="collapse-caret" @click.stop="$emit('toggle-collapse')">
            <component :is="collapsed ? ArrowDown : ArrowUp" />
          </el-icon>
        </div>
      </div>
    </template>

    <div v-show="!collapsed">
      <!-- 今日任务（与「销售过程」页每日任务页签同一接口、同一口径） -->
      <template v-if="dailyMetrics.length">
        <div class="sec-label">{{ $tt("今日任务") }}</div>
        <div class="metric-grid">
          <div v-for="m in dailyMetrics" :key="m.key" class="metric-card clickable" @click="goPage()">
            <div class="metric-label">{{ m.label }}</div>
            <div class="metric-value" :class="metricClass(m)">
              {{ m.done }}<span class="metric-unit">/{{ m.target }} {{ m.unit }}</span>
            </div>
            <div class="metric-bar"><i :style="{ width: barWidth(m), background: barColor(m) }"></i></div>
          </div>
        </div>
      </template>
      <template v-if="weeklyMetrics.length">
        <div class="sec-label" style="margin-top:12px;">{{ $tt("本周任务") }}</div>
        <div class="metric-grid c3">
          <div v-for="m in weeklyMetrics" :key="m.key" class="metric-card clickable" @click="goPage()">
            <div class="metric-label">{{ m.label }}</div>
            <div class="metric-value" :class="metricClass(m)">
              {{ m.done }}<span class="metric-unit">/{{ m.target }} {{ m.unit }}</span>
            </div>
            <div class="metric-bar"><i :style="{ width: barWidth(m), background: barColor(m) }"></i></div>
          </div>
        </div>
      </template>

      <div v-if="!dailyMetrics.length && !weeklyMetrics.length" class="empty-tip">
        <el-empty :description="$tt('暂无销售过程数据')" :image-size="80" />
      </div>

      <!-- 今日任务清单（前 5 条，点行带条件跳「跟进记录」录入，与原页「录入」同口径） -->
      <template v-if="tasks.length">
        <div class="sec-label" style="margin-top:12px;">{{ $tt("今日任务清单") }}</div>
        <el-table :data="tasks.slice(0, 5)" size="small" border>
          <el-table-column :label="$tf('客户')" min-width="160">
            <template #default="{ row }">{{ row.sourceName || '' }}</template>
          </el-table-column>
          <el-table-column :label="$tf('任务类型')" width="110">
            <template #default="{ row }">{{ row.taskType || row.followUpType || '' }}</template>
          </el-table-column>
          <el-table-column :label="$tf('建议方式')" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.suggestMethod || row.suggestWay || row.content || '' }}</template>
          </el-table-column>
          <el-table-column :label="$tf('截止时间')" width="100">
            <template #default="{ row }">{{ row.dueDate || '' }}</template>
          </el-table-column>
          <el-table-column :label="$tf('状态')" width="90">
            <template #default="{ row }">
              <el-tag v-if="row.status" size="small" effect="plain">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$tf('操作')" width="70" align="center">
            <template #default="{ row }">
              <el-button type="primary" link size="small" v-permission="perm('add')" @click="goFollowUp(row)">{{ $tt("录入") }}</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <div class="empty-tip">{{ $tt("今日暂无待办任务") }}</div>
          </template>
        </el-table>
        <div v-if="tasks.length > 5" class="show-more" @click="goPage()">
          {{ $tt("查看全部") }} {{ tasks.length }} {{ $tt("条") }}
        </div>
      </template>

      <!-- 本月过程奖金（与原页底部同口径：四项全部 100% 才发放） -->
      <div v-if="bonusLoaded" class="bonus-line" :class="monthBonus.allPass ? 'pass' : 'fail'">
        <span class="bonus-title">{{ $tt("本月过程奖金") }}</span>
        <span class="bonus-amount">{{ monthBonus.bonusAmount || 0 }} {{ $tt("元") }}</span>
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
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { DataLine, ArrowRight, ArrowUp, ArrowDown } from '@element-plus/icons-vue'
import { getDaily, getBonus } from '@/api/crm/salesActivity'
import { usePagePerm } from '@/utils/pagePerm'

defineProps({
  collapsed: { type: Boolean, default: false }
})
defineEmits(['toggle-collapse'])

// 与「销售过程」页同一对象码（按钮权限默认口径：能看见菜单即全部按钮可见）
const perm = usePagePerm('crm:sales-activity')
const router = useRouter()

const loading = ref(false)
const dailyMetrics = ref([])
const weeklyMetrics = ref([])
const tasks = ref([])
const statStartDate = ref('')
const monthBonus = reactive({})
const bonusLoaded = ref(false)

/** 私海超期是"越少越好"，达标线为 0；其余为"越多越好"（与原页同口径） */
function isReach (m) {
  if (m.key === 'privateOverdue') return (m.done || 0) <= 0
  if (m.target === 0) return true
  return (m.done || 0) >= m.target
}
function metricClass (m) {
  if (m.key === 'privateOverdue') return (m.done || 0) > 0 ? 'bad' : 'ok'
  return isReach(m) ? 'ok' : 'warn'
}
function barWidth (m) {
  if (!m.target) return '100%'
  return Math.min(100, Math.round((m.done || 0) * 100 / m.target)) + '%'
}
function barColor (m) {
  if (m.key === 'privateOverdue') return (m.done || 0) > 0 ? '#f56c6c' : '#67c23a'
  return isReach(m) ? '#67c23a' : '#e6a23c'
}
/** 奖金四项条件是否达标（四项全部 100% 才发放） */
function condOk (v) {
  return v !== null && v !== undefined && v >= 100
}

function goPage () {
  router.push('/crm/sales-activity')
}

/** 与原页「录入」同口径：带客户条件跳「跟进记录」并自动打开新增 */
function goFollowUp (row) {
  const query = { add: '1' }
  if (row && row.sourceName) query.kw = row.sourceName
  if (row && row.targetId != null) query.sourceId = String(row.targetId)
  router.push({ path: '/crm/follow-up', query })
}

async function load () {
  loading.value = true
  try {
    const today = new Date().toISOString().slice(0, 10)
    const res = await getDaily({ date: today })
    dailyMetrics.value = res?.daily || []
    weeklyMetrics.value = res?.weekly || []
    tasks.value = res?.tasks || []
    statStartDate.value = res?.statStartDate || ''
  } catch (e) {
    dailyMetrics.value = []
    weeklyMetrics.value = []
    tasks.value = []
  } finally {
    loading.value = false
  }
}

async function loadBonus () {
  try {
    const ym = new Date().toISOString().slice(0, 7)
    const [y, m] = ym.split('-')
    const res = await getBonus({ year: Number(y), month: Number(m) })
    Object.assign(monthBonus, res || {})
  } catch (e) {
    /* 奖金取不到不阻断任务指标展示 */
  } finally {
    bonusLoaded.value = true
  }
}

onMounted(() => {
  load()
  loadBonus()
})
</script>

<style scoped lang="scss">
.sales-process-card {
  border-radius: 12px;
  border: none;
  margin-bottom: 0;

  :deep(.el-card__header) {
    padding: 14px 20px;
    border-bottom: 1px solid #f5f7fa;
  }

  :deep(.el-card__body) { padding: 12px 20px 16px; }

  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .title-wrap {
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
  }

  .title-icon { font-size: 18px; }
  .card-title { font-size: 16px; font-weight: 600; color: #303133; }
  .stat-start { font-size: 12px; color: #e6a23c; }
  .header-right-wrap { display: flex; align-items: center; gap: 12px; }
  .collapse-caret { cursor: pointer; color: #909399; font-size: 16px; }
  .collapse-caret:hover { color: #409eff; }

  .sec-label { font-size: 13px; font-weight: 600; color: #303133; margin-bottom: 8px; }

  .metric-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
  .metric-grid.c3 { grid-template-columns: repeat(3, 1fr); }
  .metric-card {
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 8px;
    padding: 12px;
    background: var(--el-fill-color-blank);
    &.clickable { cursor: pointer; }
    &.clickable:hover { border-color: #409eff; }
  }
  .metric-label { font-size: 12px; color: #909399; margin-bottom: 8px; }
  .metric-value { font-size: 20px; font-weight: 600; font-variant-numeric: tabular-nums; }
  .metric-value.ok { color: #67c23a; }
  .metric-value.warn { color: #e6a23c; }
  .metric-value.bad { color: #f56c6c; }
  .metric-unit { font-size: 12px; font-weight: 400; color: #909399; margin-left: 2px; }
  .metric-bar { height: 4px; border-radius: 2px; background: var(--el-border-color-lighter); margin-top: 8px; overflow: hidden; }
  .metric-bar i { display: block; height: 100%; border-radius: 2px; }

  .bonus-line {
    display: flex; align-items: center; gap: 12px; flex-wrap: wrap;
    margin-top: 12px; padding: 10px 14px;
    border: 1px solid var(--el-border-color-lighter); border-radius: 8px;
    &.pass { border-color: #67c23a; }
    &.fail { border-color: #f56c6c; }
  }
  .bonus-title { font-size: 13px; color: #606266; }
  .bonus-amount { font-size: 20px; font-weight: 700; color: #409eff; }

  .empty-tip { padding: 12px 0; color: #909399; text-align: center; font-size: 13px; }
  .show-more {
    margin-top: 8px; text-align: center; font-size: 13px; color: #409eff;
    cursor: pointer;
    &:hover { text-decoration: underline; }
  }
}
</style>
