<template>
  <div class="approval-notifications-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <span class="title">{{ $tt("审批通知") }}</span>
            <el-badge v-if="unreadCount > 0" :value="unreadCount" type="danger" />
          </div>
          <div>
            <el-button type="primary" plain size="small" v-permission="perm('markRead')" @click="markAllRead">{{ $tt("全部标记已读") }}</el-button>
            <el-button :icon="Refresh" v-permission="perm('refresh')" @click="loadData">{{ $tt("刷新") }}</el-button>
          </div>
        </div>
      </template>

      <div class="filter-bar">
        <el-radio-group v-model="filterRead" @change="loadData">
          <el-radio-button label="">{{ $tt("全部") }}</el-radio-button>
          <el-radio-button label="0">{{ $tt("未读") }}</el-radio-button>
          <el-radio-button label="1">{{ $tt("已读") }}</el-radio-button>
        </el-radio-group>
      </div>

      <div class="notifications-list" v-loading="loading">
        <div v-for="n in notifications" :key="n.notificationId"
             class="notification-item" :class="{ unread: n.isRead === 0 }"
             @click="handleClick(n)">
          <div class="notify-icon">
            <el-icon v-if="n.notifyType === 'task'" color="#409eff"><Bell /></el-icon>
            <el-icon v-else-if="n.notifyType === 'result'" color="#67c23a"><CircleCheck /></el-icon>
            <el-icon v-else-if="n.notifyType === 'timeout'" color="#f56c6c"><Warning /></el-icon>
            <el-icon v-else color="#909399"><InfoFilled /></el-icon>
          </div>
          <div class="notify-body">
            <div class="notify-title">{{ n.title }}</div>
            <div class="notify-content">{{ n.content }}</div>
            <div class="notify-meta">
              <span>{{ formatTime(n.createTime) }}</span>
              <span v-if="n.billNo" class="bill-no">{{ n.billNo }}</span>
              <el-tag v-if="n.notifyType === 'task'" type="primary" size="small">{{ $tt("待办") }}</el-tag>
              <el-tag v-else-if="n.notifyType === 'result'" type="success" size="small">{{ $tt("结果") }}</el-tag>
              <el-tag v-else-if="n.notifyType === 'timeout'" type="danger" size="small">{{ $tt("超时") }}</el-tag>
            </div>
          </div>
          <div class="notify-status">
            <el-tag v-if="n.isRead === 0" type="danger" size="small">{{ $tt("未读") }}</el-tag>
            <el-tag v-else type="info" size="small">{{ $tt("已读") }}</el-tag>
          </div>
        </div>
        <el-empty v-if="!loading && notifications.length === 0" :description="$tt('暂无通知')" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Bell, CircleCheck, Warning, InfoFilled } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { tt } from '@/i18n'
import { resolveBillRoute } from '@/utils/billRoute'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('system:notification-setting')

const router = useRouter()

const loading = ref(false)
const notifications = ref([])
const unreadCount = ref(0)
const filterRead = ref('')

const api = {
  list: (isRead) => request.get('/approval/notifications', { params: { isRead } }),
  markRead: (id) => request.put(`/approval/notifications/${id}/read`),
  unreadCount: () => request.get('/approval/notifications/unread-count')
}

async function loadData() {
  loading.value = true
  try {
    const isRead = filterRead.value === '' ? null : Number(filterRead.value)
    const res = await api.list(isRead)
    // 拦截器已解包：res 即数组本体
    notifications.value = Array.isArray(res) ? res : (res?.data || [])
    await loadUnread()
  } catch {
    ElMessage.error(tt('加载通知失败'))
  } finally {
    loading.value = false
  }
}

async function loadUnread() {
  try {
    const res = await api.unreadCount()
    // 拦截器已解包：res 即数字本体
    unreadCount.value = (typeof res === 'number') ? res : (res?.data || 0)
  } catch {}
}

/** 通知对应单据 → 编辑页路由（后端下发 billType/billId；无独立编辑页者返回 null） */
function billJumpPath(n) {
  return resolveBillRoute(n && n.billType, n && n.billId)
}

async function handleClick(n) {
  if (n.isRead === 0) {
    try {
      await api.markRead(n.notificationId)
      n.isRead = 1
      loadUnread()
    } catch {}
  }
  // 点通知即打开对应单据（原先只标记已读、不跳转 —— 用户 2026-09-21 反馈）
  // 单据已删除/无独立编辑页时给明确提示（2026-09-28：不再"点了没反应"）
  const path = billJumpPath(n)
  if (path) router.push(path)
  else ElMessage.warning(tt('该单据已删除或无法打开'))
}

async function markAllRead() {
  for (const n of notifications.value) {
    if (n.isRead === 0) {
      try { await api.markRead(n.notificationId); n.isRead = 1 } catch {}
    }
  }
  loadUnread()
  ElMessage.success(tt('已全部标记为已读'))
}

function formatTime(t) {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 16)
}

onMounted(() => { loadData() })
</script>

<style scoped lang="scss">
.approval-notifications-page { padding: 0; }
.card-header {
  display: flex; justify-content: space-between; align-items: center;
  .header-left { display: flex; align-items: center; gap: 12px;
    .title { font-size: 16px; font-weight: 600; color: #303133; }
  }
}
.filter-bar { margin-bottom: 16px; }
.notifications-list {
  display: flex; flex-direction: column; gap: 8px;
  max-height: 70vh; overflow-y: auto;
}
.notification-item {
  display: flex; gap: 12px; padding: 12px; background: #fff;
  border: 1px solid #ebeef5; border-radius: 6px; cursor: pointer;
  transition: all 0.2s;
  &:hover { box-shadow: 0 2px 8px rgba(0,0,0,0.06); border-color: #c0c4cc; }
  &.unread {
    background: #f0f7ff; border-color: #409eff;
    .notify-title { font-weight: 600; }
  }
}
.notify-icon {
  width: 36px; height: 36px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  background: #f5f7fa; border-radius: 50%;
}
.notify-body { flex: 1; min-width: 0;
  .notify-title { font-size: 14px; color: #303133; margin-bottom: 4px; }
  .notify-content { font-size: 13px; color: #606266; margin-bottom: 6px; }
  .notify-meta {
    display: flex; gap: 12px; font-size: 12px; color: #909399; align-items: center;
    .bill-no { color: #409eff; }
  }
}
.notify-status { flex-shrink: 0; }
</style>
