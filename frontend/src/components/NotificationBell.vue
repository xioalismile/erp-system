<template>
  <el-popover
    v-model:visible="popoverVisible"
    placement="bottom-end"
    :width="420"
    trigger="manual"
    popper-class="notification-bell-popover"
    :offset="8"
  >
    <template #reference>
      <el-badge :value="unreadTotal" :hidden="unreadTotal === 0" :max="99" class="bell-badge">
        <el-icon class="header-icon-btn" :size="18" @click="togglePopover">
          <Bell />
        </el-icon>
      </el-badge>
    </template>

    <div class="bell-panel">
      <!-- 头部 -->
      <div class="bell-header">
        <div class="bell-title">
          <span>{{ $tt("消息通知") }}</span>
          <el-tag v-if="unreadTotal > 0" size="small" type="danger" class="unread-tag">{{ unreadTotal }} 未读</el-tag>
        </div>
        <div class="bell-actions">
          <el-button text size="small" v-permission="perm('markRead')" :disabled="unreadTotal === 0" @click="onMarkAllRead">
            <el-icon><Select /></el-icon> 全部已读
          </el-button>
          <el-button text size="small" v-permission="perm('view')" @click="openListPage">
            <el-icon><ArrowRight /></el-icon> 查看全部
          </el-button>
        </div>
      </div>

      <!-- 类型筛选 Tab -->
      <div class="bell-tabs">
        <div
          v-for="tab in tabs"
          :key="tab.key"
          class="bell-tab"
          :class="{ active: activeTab === tab.key }"
          @click="onTabChange(tab.key)"
        >
          <span class="bell-tab-label">{{ $tf(tab.label) }}</span>
          <span v-if="unreadByType[tab.key] > 0" class="bell-tab-count">{{ unreadByType[tab.key] }}</span>
        </div>
      </div>

      <!-- 通知列表 -->
      <div v-loading="loading" class="bell-list">
        <template v-if="filteredList.length > 0">
          <div
            v-for="item in filteredList"
            :key="item.id"
            class="bell-item"
            :class="{ unread: item.isRead === 0 }"
            @click="onClickItem(item)"
          >
            <div class="bell-item-icon" :class="iconClass(item)">
              <el-icon><component :is="iconComp(item)" /></el-icon>
            </div>
            <div class="bell-item-body">
              <div class="bell-item-title">
                <span class="title-text">{{ item.title }}</span>
                <el-tag v-if="item.urgency === 'OVERDUE'" size="small" type="danger" effect="dark">{{ $tt("已超期") }}</el-tag>
                <el-tag v-else-if="item.urgency === 'URGENT'" size="small" type="warning" effect="dark">{{ $tt("紧急") }}</el-tag>
                <el-tag v-else-if="item.urgency === 'SOON'" size="small" type="warning">{{ $tt("即将到期") }}</el-tag>
              </div>
              <div class="bell-item-content">{{ item.content }}</div>
              <div class="bell-item-meta">
                <span class="bell-item-no">{{ item.sourceNo }}</span>
                <span class="bell-item-time">{{ formatTime(item.createdAt) }}</span>
              </div>
            </div>
            <div v-if="item.isRead === 0" class="unread-dot"></div>
          </div>
        </template>
        <el-empty v-else :description="$tt('暂无通知')" :image-size="60" />
      </div>
    </div>
  </el-popover>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Bell, Select, ArrowRight, Document, WarningFilled, Timer, Edit, Coin, CircleCheck, Promotion
} from '@element-plus/icons-vue'
import {
  getNotifications, getUnreadCount, markRead, markAllRead
} from '@/api/system/notification'
import { EVENT_APPROVAL_INCREASE } from '@/utils/voiceNotify'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('system:notification-setting')

const router = useRouter()
const popoverVisible = ref(false)
const loading = ref(false)
const list = ref([])
const unreadTotal = ref(0)
const unreadByType = ref({})
const activeTab = ref('all')
let pollTimer = null
// 上一次后端返回的「待审批」未读数（增量检测基线；null = 首次加载尚未建立基线，不播报）
let lastServerApproval = null
// 上一次后端返回的各类型未读数基线（null = 首次加载尚未建立基线）——
// 供通用增量事件 erp:notification-increase 使用（全局弹窗提醒 components/PopupNotify.vue 消费）
let lastServerByType = null

// Tab 配置（2026-09-22 用户口径：去掉「草稿」—— 铃铛不再做草稿提醒）
// 2026-09-28 新增「工序通知」（工序完成后 @ 下道工序负责人）
const tabs = [
  { key: 'all',               label: '全部' },
  { key: 'approval',          label: '待审批' },
  { key: 'approval_finish',   label: '审批结果' },
  { key: 'operation_mention', label: '工序通知' },
  { key: 'overdue',           label: '已超期' },
  { key: 'soon_overdue',      label: '即将超期' },
  { key: 'ar_ap',             label: '应收应付' }
]

// 类型 → 图标映射
const TYPE_ICON_MAP = {
  approval:          { comp: Document,    cls: 'icon-approval' },
  approval_finish:   { comp: CircleCheck, cls: 'icon-finish' },
  operation_mention: { comp: Promotion,   cls: 'icon-mention' },
  overdue:           { comp: WarningFilled,cls: 'icon-overdue' },
  soon_overdue:      { comp: Timer,       cls: 'icon-soon' },
  draft:             { comp: Edit,        cls: 'icon-draft' },
  ar_ap:             { comp: Coin,        cls: 'icon-ar-ap' }
}

function iconComp(item) {
  return (TYPE_ICON_MAP[item.type] || TYPE_ICON_MAP.approval).comp
}
function iconClass(item) {
  return (TYPE_ICON_MAP[item.type] || TYPE_ICON_MAP.approval).cls
}

// 当前 Tab 过滤后的列表
const filteredList = computed(() => list.value)

async function loadUnreadCount() {
  try {
    const data = await getUnreadCount()
    unreadTotal.value = data.total || 0
    unreadByType.value = data.byType || {}
    // 待审批新增检测 → 派发事件给语音提醒模块（只做检测，播报由 voiceNotify 负责）
    // 基线只认「后端返回值」之间的增长：本地点击已读会先 -1，用本地值比较会误判为新增
    const curApproval = Number((data.byType || {}).approval || 0)
    if (lastServerApproval !== null && curApproval > lastServerApproval) {
      try {
        window.dispatchEvent(new CustomEvent(EVENT_APPROVAL_INCREASE, {
          detail: { count: curApproval, delta: curApproval - lastServerApproval }
        }))
      } catch (e) { /* 事件派发失败不影响铃铛本身 */ }
    }
    lastServerApproval = curApproval

    // 通用增量事件（2026-11-19 弹窗设置）：对全部类型比较基线，只上报真实新增的类型。
    // 消费方 = components/PopupNotify.vue（事件名两端一致，铃铛是 script setup 无法导出常量，改动时须同步）。
    const curByType = {}
    for (const [t, n] of Object.entries(data.byType || {})) curByType[t] = Number(n)
    if (lastServerByType !== null) {
      const deltaByType = {}
      for (const [t, n] of Object.entries(curByType)) {
        const prev = Number(lastServerByType[t] || 0)
        if (n > prev) deltaByType[t] = { count: n, delta: n - prev }
      }
      if (Object.keys(deltaByType).length > 0) {
        try {
          window.dispatchEvent(new CustomEvent('erp:notification-increase', { detail: { byType: deltaByType } }))
        } catch (e) { /* 事件派发失败不影响铃铛本身 */ }
      }
    }
    lastServerByType = curByType
  } catch (e) {
    // 静默失败（防轮询报错刷屏）
  }
}

async function loadList() {
  loading.value = true
  try {
    // 只看未读（2026-09-22 用户口径：已读信息不再显示；"已读"本身在库里保留，保证永久已读、不复活）
    const params = { current: 1, size: 50, isRead: 0 }
    // 按当前Tab类型筛选加载
    if (activeTab.value !== 'all') {
      params.type = activeTab.value
    }
    const data = await getNotifications(params)
    list.value = data.records || []
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function loadAllData() {
  await Promise.all([loadList(), loadUnreadCount()])
}

function togglePopover() {
  popoverVisible.value = !popoverVisible.value
  if (popoverVisible.value) {
    activeTab.value = 'all'
    loadAllData()
  }
}

/** 切换Tab时重新加载对应类型数据 */
function onTabChange(key) {
  activeTab.value = key
  loadList()
}

/** 点击外部关闭弹窗 */
function handleOutsideClick(e) {
  if (!popoverVisible.value) return
  const popover = document.querySelector('.notification-bell-popover')
  const bell = document.querySelector('.bell-badge')
  if (popover && !popover.contains(e.target) && bell && !bell.contains(e.target)) {
    popoverVisible.value = false
  }
}

/** 点击通知项：标记已读 + 跳转 */
async function onClickItem(item) {
  if (item.isRead === 0) {
    try {
      await markRead(item.id)
      item.isRead = 1
      // 更新未读数
      unreadTotal.value = Math.max(0, unreadTotal.value - 1)
      if (unreadByType.value[item.type]) {
        unreadByType.value[item.type] = Math.max(0, unreadByType.value[item.type] - 1)
      }
    } catch (e) {
      // 标记失败不阻塞跳转
    }
  }
  popoverVisible.value = false
  if (item.routePath) {
    router.push(item.routePath)
  } else {
    ElMessage.info(tt('该通知未配置跳转路径'))
  }
}

async function onMarkAllRead() {
  try {
    const n = await markAllRead()
    ElMessage.success(`${tt("已标记 ")}${n}${tt(" 条为已读")}`)
    await loadAllData()
  } catch (e) {
    ElMessage.error(tt('标记全部已读失败'))
  }
}

function openListPage() {
  popoverVisible.value = false
  // 2026-09-22：改为进入真正独立的「消息中心」页（此前只跳工作台 → 用户反馈"查看全部没弹全部信息"）
  router.push('/system/message-center')
}

function formatTime(t) {
  if (!t) return ''
  const date = new Date(t.replace(' ', 'T'))
  if (isNaN(date.getTime())) return t
  const now = new Date()
  const diff = (now - date) / 1000  // 秒
  if (diff < 60) return '刚刚'
  if (diff < 3600) return Math.floor(diff / 60) + ' 分钟前'
  if (diff < 86400) return Math.floor(diff / 3600) + ' 小时前'
  if (diff < 86400 * 7) return Math.floor(diff / 86400) + ' 天前'
  // 超过 7 天显示日期
  const m = (date.getMonth() + 1).toString().padStart(2, '0')
  const d = date.getDate().toString().padStart(2, '0')
  return `${date.getFullYear()}-${m}-${d}`
}

onMounted(() => {
  loadUnreadCount()
  // 每 30 秒轮询一次未读数 (轻量接口)
  pollTimer = setInterval(loadUnreadCount, 30000)
  // 即时刷新（2026-11-19 弹窗提醒）：提交/审批类动作成功后由请求拦截器广播本事件，
  // 铃铛立即拉取未读 → 增量事件 → 弹窗 1~2 秒内出现，不再等 30 秒轮询
  try { window.addEventListener('erp:notification-refresh', loadUnreadCount) } catch (e) { /* 忽略 */ }
  // 点击外部关闭弹窗
  document.addEventListener('click', handleOutsideClick, true)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
  try { window.removeEventListener('erp:notification-refresh', loadUnreadCount) } catch (e) { /* 忽略 */ }
  document.removeEventListener('click', handleOutsideClick, true)
})
</script>

<style scoped lang="scss">
.header-icon-btn {
  cursor: pointer;
  color: #606266;
  transition: color 0.2s;
  &:hover {
    color: var(--erp-primary, #409EFF);
  }
}
.bell-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.bell-panel {
  margin: -12px;
  font-family: Tahoma, 'Microsoft YaHei', sans-serif;
}

.bell-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
}
.bell-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  .unread-tag {
    font-size: 11px;
  }
}
.bell-actions {
  display: flex;
  gap: 4px;
}

.bell-tabs {
  display: flex;
  padding: 8px 12px;
  gap: 4px;
  border-bottom: 1px solid #f0f0f0;
  overflow-x: auto;
}
.bell-tab {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  font-size: 12px;
  color: #606266;
  cursor: pointer;
  border-radius: 12px;
  white-space: nowrap;
  transition: all 0.2s;
  &:hover {
    background: #f5f7fa;
  }
  &.active {
    background: var(--erp-primary, #409EFF);
    color: #fff;
    .bell-tab-count {
      background: rgba(255, 255, 255, 0.3);
      color: #fff;
    }
  }
}
.bell-tab-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  font-size: 10px;
  background: #f56c6c;
  color: #fff;
  border-radius: 8px;
}

.bell-list {
  max-height: 360px;
  overflow-y: auto;
}

.bell-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
  position: relative;
  transition: background 0.2s;
  &:hover {
    background: #f9fafc;
  }
  &.unread {
    background: #f0f7ff;
    &:hover {
      background: #e6f1ff;
    }
  }
}

.bell-item-icon {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 16px;
  &.icon-approval     { background: var(--erp-primary, #409EFF); }
  &.icon-mention      { background: #e6a23c; }
  &.icon-overdue      { background: #f56c6c; }
  &.icon-soon         { background: #e6a23c; }
  &.icon-draft        { background: #909399; }
  &.icon-ar-ap        { background: #67c23a; }
}

.bell-item-body {
  flex: 1;
  min-width: 0;
}
.bell-item-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
  .title-text {
    font-size: 13px;
    font-weight: 600;
    color: #303133;
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
.bell-item-content {
  font-size: 12px;
  color: #606266;
  margin-bottom: 4px;
  line-height: 1.4;
}
.bell-item-meta {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #909399;
}
.bell-item-no {
  font-family: Consolas, monospace;
}

.unread-dot {
  position: absolute;
  top: 16px;
  right: 12px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f56c6c;
}
</style>
