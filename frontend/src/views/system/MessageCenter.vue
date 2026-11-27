<template>
  <div class="erp-page message-center">
    <el-card shadow="never">
      <template #header>
        <div class="mc-header">
          <div class="mc-header-left">
            <span class="mc-title">{{ $tt('消息中心') }}</span>
            <el-tag v-if="unreadTotal > 0" type="danger" size="small">{{ unreadTotal }} {{ $tt('未读') }}</el-tag>
          </div>
          <div class="mc-actions">
            <el-button size="small" :icon="Select" v-permission="perm('markRead')" :disabled="unreadTotal === 0" @click="onMarkAllRead">
              {{ $tt('全部已读') }}
            </el-button>
            <el-button size="small" :icon="Refresh" v-permission="perm('refresh')" @click="loadAll">{{ $tt('刷新') }}</el-button>
          </div>
        </div>
      </template>

      <!-- 类型页签（与铃铛同一套类型；草稿已下线，不再出现） -->
      <div class="mc-tabs">
        <div
          v-for="t in TYPE_TABS"
          :key="t.key"
          class="mc-tab"
          :class="{ active: activeType === t.key }"
          @click="onTypeChange(t.key)"
        >
          <span>{{ $tf(t.label) }}</span>
          <span v-if="countOf(t.key) > 0" class="mc-tab-count">{{ countOf(t.key) }}</span>
        </div>
      </div>

      <!-- 批量操作条：复用单据列表页的通用件（勾选后出现，含进度与置灰原因） -->
      <BillBatchBar
        :selected-rows="selectedRows"
        :actions="batchActions"
        :loading="batchRunning"
        :progress="batchProgress"
        @action="onBatchApprove"
        @clear="clearSelection"
        @select-all="selectAllPage"
      />

      <el-table
        ref="tableRef"
        :data="rows"
        v-loading="loading"
        border
        row-key="id"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="46" />
        <el-table-column :label="$tf('类型')" width="120">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.type)" size="small" effect="light">{{ typeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$tf('消息')" min-width="360">
          <template #default="{ row }">
            <div class="mc-item-title">{{ row.title }}</div>
            <div class="mc-item-content">{{ row.content }}</div>
            <div class="mc-item-meta">
              <span v-if="row.sourceNo" class="mc-no">{{ row.sourceNo }}</span>
              <span class="mc-time">{{ formatTime(row.createdAt) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="$tf('操作')" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-permission="perm('view')" @click="viewBill(row)">{{ $tt('查看单据') }}</el-button>
            <el-button
              v-if="row.type === 'approval'"
              link
              type="primary"
              v-permission="perm('audit')"
              @click="openApprove(row)"
            >{{ $tt('审批') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && rows.length === 0" :description="$tt('暂无未读消息')" :image-size="70" />
    </el-card>

    <!-- 单个审批（通过 / 驳回，复用审批引擎的同一套端点） -->
    <el-dialog v-model="approveVisible" :title="approveTitle" width="520px" :close-on-click-modal="false">
      <el-input
        v-model="approveComment"
        type="textarea"
        :rows="3"
        :placeholder="$tt('请输入审批意见（可选）')"
      />
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="approveVisible = false">{{ $tt('取消') }}</el-button>
        <el-button type="danger" v-permission="perm('reject')" :loading="approving" @click="doApprove(false)">{{ $tt('驳回') }}</el-button>
        <el-button type="primary" v-permission="perm('audit')" :loading="approving" @click="doApprove(true)">{{ $tt('审批通过') }}</el-button>
      </template>
    </el-dialog>

    <!-- 批量结果面板：复用通用件（成功/失败/跳过 + 失败清单 + 重试） -->
    <BatchResultDialog v-model="resultVisible" :result="batchResult" :retrying="batchRunning" @retry="retryFailed" />
  </div>
</template>

<script setup>
/**
 * 消息中心（2026-09-22 新增）
 *
 * 为什么要有这个页面：铃铛的「查看全部」此前只跳工作台（没有任何通知列表页），用户反馈
 * "查看全部你也没弹全部信息出来"；且审批只能去「设置中心→审批设置→我的待办」单条办。
 *
 * 口径（用户 2026-09-22 明确）：
 *   1. 只显示"当前账号"的消息（后端按 receiver_id 过滤）+ 被列为抄送/推送人的消息照常接收；
 *   2. 只显示**未读**：已读信息不再出现（已读行为在库中保留 → "点击过已读之后永久已读"）；
 *   3. 类型 = 待审批 / 审批结果 / 已超期 / 即将超期 / 应收应付（**草稿已下线**）；
 *   4. 待审批消息支持**单个审批**与**勾选批量审批** —— 批量不复造轮子：
 *      直接复用 `batchApproveBills`（后端 BillApprovalService：逐条判断走审批引擎还是模块审批，
 *      逐条返回成功/失败原因）+ `BillBatchBar` + `BatchResultDialog` 三个现成通用件。
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Select } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'
import { getNotifications, getUnreadCount, markRead, markAllRead, batchMarkRead } from '@/api/system/notification'
import { batchApproveBills } from '@/api/dashboard'
import BillBatchBar from '@/components/bill/BillBatchBar.vue'
import BatchResultDialog from '@/components/bill/BatchResultDialog.vue'

const perm = usePagePerm('system:notification-setting')
const router = useRouter()

/** 类型页签：与铃铛一致（草稿已下线） */
const TYPE_TABS = [
  { key: 'all',             label: '全部' },
  { key: 'approval',        label: '待审批' },
  { key: 'approval_finish', label: '审批结果' },
  { key: 'operation_mention', label: '工序通知' },
  { key: 'overdue',         label: '已超期' },
  { key: 'soon_overdue',    label: '即将超期' },
  { key: 'ar_ap',           label: '应收应付' }
]
const TYPE_LABEL = {
  approval: '待审批',
  approval_finish: '审批结果',
  operation_mention: '工序通知',
  overdue: '已超期',
  soon_overdue: '即将超期',
  ar_ap: '应收应付',
  draft: '草稿'
}

const loading = ref(false)
const rows = ref([])
const activeType = ref('all')
const unreadTotal = ref(0)
const unreadByType = ref({})
const tableRef = ref(null)
const selectedRows = ref([])

// ===== 批量审批（复用现成接口 + 通用件）=====
const batchActions = [
  {
    key: 'approve',
    label: tt('批量审批'),
    type: 'primary',
    perm: 'system:notification-setting:audit',
    // 只有"待审批"类消息能批量审批；勾到别的类型时按钮置灰并说明原因（批量场景隐藏入口会让用户找不到）
    disabledReason: (list) => (list && list.length && list.every(r => r.type === 'approval')
      ? ''
      : tt('只有「待审批」类型的消息可以批量审批，请只勾选待审批消息'))
  }
]
const batchRunning = ref(false)
const batchProgress = ref({ done: 0, total: 0 })
const batchResult = ref(null)
const resultVisible = ref(false)
const lastFailedRows = ref([])

// ===== 单个审批 =====
const approveVisible = ref(false)
const approving = ref(false)
const approveComment = ref('')
const approveRow = ref(null)
/** `${单据类型大写}|${单据ID}` → 审批引擎 taskId（仅"我的待办"，用于把意见/驳回送到引擎） */
const taskMap = ref({})

const approveTitle = computed(() => {
  const r = approveRow.value
  if (!r) return tt('审批')
  return tt('审批：') + (r.title || '') + (r.sourceNo ? '（' + r.sourceNo + '）' : '')
})

function upperType(row) {
  return String((row && row.sourceType) || '').toUpperCase()
}
function taskKey(row) {
  return upperType(row) + '|' + (row && row.sourceId)
}

// ===== 数据加载 =====
async function loadList() {
  loading.value = true
  try {
    const params = { current: 1, size: 200, isRead: 0 }
    if (activeType.value !== 'all') params.type = activeType.value
    const data = await getNotifications(params)
    rows.value = data.records || []
  } catch (e) {
    rows.value = []
    ElMessage.error(tt('加载消息失败'))
  } finally {
    loading.value = false
  }
}

async function loadUnread() {
  try {
    const data = await getUnreadCount()
    unreadTotal.value = data.total || 0
    unreadByType.value = data.byType || {}
  } catch (e) {
    // 静默：未读数失败不影响列表
  }
}

/** 拉"我的待办"，建立 单据→taskId 映射（单个审批要带意见/驳回时必须走审批引擎的 task 端点） */
async function loadMyTasks() {
  try {
    const list = await request.get('/approval/tasks/my-pending')
    const map = {}
    for (const t of (Array.isArray(list) ? list : [])) {
      if (!t || t.billId == null || !t.billType) continue
      map[String(t.billType).toUpperCase() + '|' + t.billId] = t.taskId
    }
    taskMap.value = map
  } catch (e) {
    taskMap.value = {}
  }
}

async function loadAll() {
  await Promise.all([loadList(), loadUnread(), loadMyTasks()])
}

function countOf(key) {
  if (key === 'all') return unreadTotal.value || 0
  return unreadByType.value[key] || 0
}
function onTypeChange(key) {
  activeType.value = key
  clearSelection()
  loadList()
}
function typeLabel(type) {
  return tt(TYPE_LABEL[type] || type || '')
}
function typeTag(type) {
  if (type === 'approval') return 'primary'
  if (type === 'approval_finish') return 'success'
  if (type === 'operation_mention') return 'warning'
  if (type === 'overdue') return 'danger'
  if (type === 'soon_overdue') return 'warning'
  if (type === 'ar_ap') return 'info'
  return 'info'
}
function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').substring(0, 16)
}

// ===== 表格勾选（复用通用批量条的入参约定）=====
function onSelectionChange(sel) {
  selectedRows.value = sel || []
}
function clearSelection() {
  selectedRows.value = []
  if (tableRef.value && typeof tableRef.value.clearSelection === 'function') tableRef.value.clearSelection()
}
function selectAllPage() {
  if (tableRef.value && typeof tableRef.value.toggleAllSelection === 'function') tableRef.value.toggleAllSelection()
}

// ===== 行操作 =====
/** 查看单据：先标记已读（永久已读），再按后端下发的路由跳转 */
async function viewBill(row) {
  await markReadSafe(row)
  if (row.routePath) {
    router.push(row.routePath)
  } else {
    ElMessage.info(tt('该消息未配置跳转路径'))
  }
}

async function markReadSafe(row) {
  if (!row || row.isRead !== 0) return
  try {
    await markRead(row.id)
    row.isRead = 1
  } catch (e) {
    // 标记失败不阻塞主流程
  }
}

function openApprove(row) {
  approveRow.value = row
  approveComment.value = ''
  approveVisible.value = true
}

/**
 * 单条审批（通过 pass=true / 驳回 pass=false）。
 * 优先走审批引擎 task 端点（能带意见、能驳回）；引擎里查不到任务时，
 * "通过"回退到统一的批量审批接口（内部同样会判断是否走引擎），"驳回"则如实提示。
 */
async function approveOne(row, pass, comment) {
  const tKey = taskKey(row)
  const taskId = taskMap.value[tKey]
  if (taskId) {
    if (pass) {
      await request.post(`/approval/tasks/${taskId}/approve`, { comment: comment || '' })
    } else {
      await request.post(`/approval/tasks/${taskId}/reject`, { comment: comment || '' })
    }
    return
  }
  if (!pass) {
    throw new Error(tt('未找到该单据的待办审批任务（可能已被处理），无法驳回'))
  }
  const results = await batchApproveBills([{ type: upperType(row), id: row.sourceId }])
  const r = (results || [])[0]
  if (r && r.success === false) throw new Error(r.errorMessage || tt('审批失败'))
}

async function doApprove(pass) {
  const row = approveRow.value
  if (!row) return
  approving.value = true
  try {
    await approveOne(row, pass, approveComment.value)
    await markReadSafe(row)
    approveVisible.value = false
    ElMessage.success(pass ? tt('审批通过') : tt('已驳回'))
    await Promise.all([loadList(), loadUnread(), loadMyTasks()])
  } catch (e) {
    ElMessage.error((e && e.message) || tt('审批失败'))
  } finally {
    approving.value = false
  }
}

// ===== 批量审批（一次请求，逐条结果，失败可重试）=====
async function runBatchApprove(list) {
  batchRunning.value = true
  batchProgress.value = { done: 0, total: list.length }
  const result = { actionLabel: tt('批量审批'), ok: [], fail: [], skip: [] }
  try {
    const approvable = []
    for (const row of list) {
      if (row.type !== 'approval') {
        result.skip.push({ row, reason: tt('非「待审批」类型消息，已跳过') })
      } else {
        approvable.push(row)
      }
    }
    if (approvable.length) {
      const res = await batchApproveBills(approvable.map(r => ({ type: upperType(r), id: r.sourceId })))
      const byKey = {}
      for (const r of (res || [])) byKey[String(r.type).toUpperCase() + '|' + r.id] = r
      for (const row of approvable) {
        const r = byKey[taskKey(row)]
        if (r && r.success === false) {
          result.fail.push({ row, message: r.errorMessage || tt('审批失败') })
        } else {
          result.ok.push({ row })
          await markReadSafe(row)
        }
        batchProgress.value = { done: batchProgress.value.done + 1, total: batchProgress.value.total }
      }
      // 成功的消息一次批量置已读（保持铃铛红点与列表同步）
      const okIds = result.ok.map(o => o.row.id).filter(id => id != null)
      if (okIds.length) {
        try { await batchMarkRead(okIds) } catch (e) { /* 已读标记失败不影响审批结果 */ }
      }
    }
    return result
  } finally {
    batchRunning.value = false
  }
}

async function onBatchApprove() {
  const list = [...(selectedRows.value || [])]
  if (!list.length) return
  const preview = list.slice(0, 10).map(r => r.sourceNo || r.title).join('、')
  const more = list.length > 10 ? tt('…共 ') + list.length + tt(' 条') : ''
  try {
    await ElMessageBox.confirm(
      tt('将对已勾选的 ') + list.length + tt(' 条待审批消息执行「审批通过」：') + preview + more,
      tt('批量审批确认'),
      { type: 'info', confirmButtonText: tt('确认执行'), cancelButtonText: tt('取消') }
    )
  } catch (e) {
    return // 用户取消
  }
  const back = await runBatchApprove(list)
  onBatchDone(back)
}

/** 重试失败项：只重跑失败的行，成功的不重复执行 */
async function retryFailed() {
  const list = lastFailedRows.value || []
  if (!list.length) return
  const back = await runBatchApprove(list)
  const merged = {
    actionLabel: tt('批量审批'),
    ok: [...(batchResult.value?.ok || []), ...back.ok],
    fail: back.fail,
    skip: [...(batchResult.value?.skip || []), ...back.skip]
  }
  onBatchDone(merged)
}

function onBatchDone(result) {
  batchResult.value = result
  lastFailedRows.value = result.fail.map(f => f.row)
  resultVisible.value = true
  if (!result.fail.length && !result.skip.length) {
    ElMessage.success(tt('批量审批完成：成功 ') + result.ok.length + tt(' 条'))
  }
  clearSelection()
  Promise.all([loadList(), loadUnread(), loadMyTasks()])
}

async function onMarkAllRead() {
  try {
    const n = await markAllRead()
    ElMessage.success(tt('已标记 ') + n + tt(' 条为已读'))
    clearSelection()
    await Promise.all([loadList(), loadUnread()])
  } catch (e) {
    ElMessage.error(tt('标记全部已读失败'))
  }
}

onMounted(() => { loadAll() })
</script>

<style scoped lang="scss">
.message-center { padding: 16px; }
.mc-header {
  display: flex; align-items: center; justify-content: space-between;
  .mc-header-left { display: flex; align-items: center; gap: 12px; }
  .mc-title { font-size: 16px; font-weight: 600; color: #303133; }
}
.mc-tabs {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 12px;
  .mc-tab {
    display: inline-flex; align-items: center; gap: 6px;
    padding: 6px 14px; border: 1px solid #ebeef5; border-radius: 16px;
    font-size: 13px; color: #606266; cursor: pointer; user-select: none;
    transition: all 0.2s;
    &:hover { color: var(--erp-primary, #409EFF); border-color: #c6e2ff; }
    &.active { color: #fff; background: var(--erp-primary, #409EFF); border-color: var(--erp-primary, #409EFF); }
  }
  .mc-tab-count {
    min-width: 18px; height: 18px; line-height: 18px; text-align: center;
    padding: 0 4px; border-radius: 9px; background: #f56c6c; color: #fff; font-size: 12px;
  }
}
.mc-item-title { font-size: 14px; color: #303133; margin-bottom: 4px; }
.mc-item-content { font-size: 13px; color: #606266; margin-bottom: 4px; }
.mc-item-meta {
  display: flex; align-items: center; gap: 12px; font-size: 12px; color: #909399;
  .mc-no { color: #409eff; }
}
</style>
