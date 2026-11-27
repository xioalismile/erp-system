<template>
  <el-card class="sales-warning-card" shadow="never" v-loading="loading">
    <template #header>
      <div class="card-header">
        <div class="title-wrap" @click="$emit('toggle-collapse')">
          <el-icon class="title-icon" color="#f56c6c"><Warning /></el-icon>
          <span class="card-title">{{ $tt("销售预警") }}</span>
          <el-badge :value="summary['未处理'] || 0" :hidden="!summary['未处理']" type="danger" />
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
      <!-- 汇总条（与「销售预警」页同接口同口径；点击跳销售预警页） -->
      <div class="sw-bar">
        <div class="sw-item clickable" @click="goPage()">
          <div class="sw-label">{{ $tt("未处理") }}</div>
          <div class="sw-val">{{ summary['未处理'] || 0 }}</div>
        </div>
        <div class="sw-item clickable" @click="goPage()">
          <div class="sw-label">{{ $tt("红色 · 严重") }}</div>
          <div class="sw-val red">{{ summary['红'] || 0 }}</div>
        </div>
        <div class="sw-item clickable" @click="goPage()">
          <div class="sw-label">{{ $tt("橙色 · 警告") }}</div>
          <div class="sw-val orange">{{ summary['橙'] || 0 }}</div>
        </div>
        <div class="sw-item clickable" @click="goPage()">
          <div class="sw-label">{{ $tt("黄色 · 提醒") }}</div>
          <div class="sw-val yellow">{{ summary['黄'] || 0 }}</div>
        </div>
        <div class="sw-item clickable" @click="goPage()">
          <div class="sw-label">{{ $tt("已处理待确认") }}</div>
          <div class="sw-val">{{ summary['已处理待确认'] || 0 }}</div>
        </div>
      </div>

      <!-- 预警列表（与「销售预警」页同一列表接口、默认口径=全部状态；点击行跳销售预警页处理） -->
      <el-table v-if="rows.length" :data="rows" size="small" border
                @row-click="goPage" class="sw-table row-clickable">
        <el-table-column :label="$tf('等级')" width="70" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="lvType(row.warningLevel)">{{ row.warningLevel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="warningType" :label="$tf('预警类型')" min-width="140" show-overflow-tooltip />
        <el-table-column prop="targetName" :label="$tf('对象')" min-width="130" show-overflow-tooltip />
        <el-table-column :label="$tf('处理时限')" width="140">
          <template #default="{ row }">
            <span :class="remainClass(row)">{{ deadlineText(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="$tf('状态')" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === '已关闭' ? 'info' : (row.status === '已处理待确认' ? 'success' : 'warning')">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-tip">{{ $tt("当前没有待处理的销售预警") }}</div>
        </template>
      </el-table>
      <div v-else class="empty-tip">
        <el-empty :description="$tt('当前没有待处理的销售预警')" :image-size="80" />
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Warning, ArrowRight, ArrowUp, ArrowDown } from '@element-plus/icons-vue'
import { listSalesWarning, salesWarningSummary } from '@/api/crm/salesWarning'

defineProps({
  collapsed: { type: Boolean, default: false }
})
defineEmits(['toggle-collapse'])

const router = useRouter()

const loading = ref(false)
const rows = ref([])
const summary = reactive({})

function lvType (l) {
  return l === '红' ? 'danger' : (l === '橙' ? 'warning' : 'info')
}
function remainClass (row) {
  if (row.status === '已关闭') return 'muted'
  if (row.remainSeconds === null || row.remainSeconds === undefined) return ''
  return row.remainSeconds < 0 ? 'overdue' : (row.remainSeconds < 3600 ? 'soon' : '')
}
function deadlineText (row) {
  if (row.status === '已关闭') return '已闭环'
  if (row.remainSeconds === null || row.remainSeconds === undefined) return ''
  if (row.remainSeconds < 0) return '已超时 ' + Math.abs(Math.round(row.remainSeconds / 3600)) + ' 小时'
  const h = Math.floor(row.remainSeconds / 3600)
  return h > 0 ? ('剩余 ' + h + ' 小时') : ('剩余 ' + Math.round(row.remainSeconds / 60) + ' 分钟')
}

function goPage () {
  router.push('/crm/warning')
}

async function load () {
  loading.value = true
  try {
    // 与「销售预警」页同一列表接口、同一默认口径（不筛状态，全部状态都显示）
    const res = await listSalesWarning({ current: 1, size: 8 })
    rows.value = res?.records || []
  } catch (e) {
    rows.value = []
  } finally {
    loading.value = false
  }
}

async function loadSummary () {
  try {
    const res = await salesWarningSummary()
    Object.assign(summary, res || {})
  } catch (e) {
    /* 汇总取不到不阻断列表展示 */
  }
}

onMounted(() => {
  load()
  loadSummary()
})
</script>

<style scoped lang="scss">
.sales-warning-card {
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
  .header-right-wrap { display: flex; align-items: center; gap: 12px; }
  .collapse-caret { cursor: pointer; color: #909399; font-size: 16px; }
  .collapse-caret:hover { color: #409eff; }

  .sw-bar { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 12px; }
  .sw-item {
    flex: 1; min-width: 90px;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 8px;
    padding: 8px 12px;
    text-align: center;
    &.clickable { cursor: pointer; }
    &.clickable:hover { border-color: #409eff; }
  }
  .sw-label { font-size: 12px; color: #909399; }
  .sw-val { font-size: 20px; font-weight: 600; font-variant-numeric: tabular-nums; margin-top: 2px; }
  .sw-val.red { color: #f56c6c; }
  .sw-val.orange { color: #e6a23c; }
  .sw-val.yellow { color: #909399; }

  .sw-table.row-clickable :deep(tbody tr) { cursor: pointer; }

  .overdue { color: #f56c6c; font-weight: 600; }
  .soon { color: #e6a23c; }
  .muted { color: #909399; }

  .empty-tip { padding: 12px 0; color: #909399; text-align: center; font-size: 13px; }
}
</style>
