<template>
  <Teleport to="body">
    <!-- 居中模态：待审批=待办列表（每张单展开明细、独立通过/驳回）；其它类型=单卡 -->
    <div class="popup-notify-mask" v-if="current">
      <!-- 待审批：列表模式 -->
      <div v-if="current.isApprovalList" class="popup-card popup-card-list" style="border-top-color: #409EFF;">
        <div class="popup-header">
          <el-icon :size="18" color="#409EFF"><Document /></el-icon>
          <span class="popup-title">待审批提醒</span>
          <span class="popup-count">{{ current.bills.length }} 张待审批</span>
          <el-icon class="popup-close" :size="16" @click="closeCard(current)"><Close /></el-icon>
        </div>
        <div class="popup-body" v-if="current.text">{{ current.text }}</div>
        <div class="popup-bill-list">
          <div v-for="b in current.bills" :key="b.billType + '_' + b.billId" class="popup-bill">
            <div class="popup-bill-head">
              <div class="popup-bill-title">
                <span class="popup-bill-type">{{ b.typeName || '单据' }}</span>
                <span class="popup-bill-no">{{ b.billNo || '—' }}</span>
                <span class="popup-bill-amount" v-if="b.amount !== null && b.amount !== undefined">¥{{ formatAmount(b.amount) }}</span>
              </div>
              <div class="popup-bill-meta">
                <span v-if="b.partyName">{{ b.partyName }}</span>
                <span v-if="b.creator">提交人 {{ b.creator }}</span>
                <span v-if="b.time">{{ b.time }}</span>
              </div>
            </div>
            <!-- 明细行 -->
            <div class="popup-bill-items">
              <div v-if="b.summaryLoading" class="popup-items-loading">明细加载中…</div>
              <div v-else-if="b.summaryError" class="popup-items-error">{{ b.summaryError }}</div>
              <template v-else>
                <div v-for="(it, idx) in b.items" :key="idx" class="popup-item-row">
                  <span class="popup-item-name">{{ it.name }}<template v-if="it.spec">（{{ it.spec }}）</template></span>
                  <span class="popup-item-qty" v-if="it.qty !== null && it.qty !== undefined">×{{ formatAmount(it.qty) }}</span>
                  <span class="popup-item-price" v-if="it.price !== null && it.price !== undefined">@¥{{ formatAmount(it.price) }}</span>
                  <span class="popup-item-amount" v-if="it.amount !== null && it.amount !== undefined">¥{{ formatAmount(it.amount) }}</span>
                </div>
                <div v-if="b.items.length === 0" class="popup-items-loading">该单据无明细行</div>
              </template>
            </div>
            <!-- 驳回原因输入 -->
            <div v-if="b.rejecting" class="popup-reject-box">
              <el-input v-model="b.rejectReason" type="textarea" :rows="2" placeholder="请输入驳回原因" maxlength="200" />
              <div class="popup-reject-btns">
                <el-button size="small" @click="b.rejecting = false">取消</el-button>
                <el-button size="small" type="danger" :loading="b.actionLoading" @click="onBillAction(current, b, 'reject')">确认驳回</el-button>
              </div>
            </div>
            <div v-else class="popup-actions popup-actions-left">
              <el-button size="small" type="success" :loading="b.actionLoading" @click="onBillAction(current, b, 'approve')">通过</el-button>
              <el-button size="small" type="danger" plain @click="b.rejecting = true">驳回</el-button>
              <el-button v-if="b.routePath" size="small" text @click="onBillGo(current, b)">去处理</el-button>
            </div>
            <div v-if="b.actionError" class="popup-error">{{ b.actionError }}</div>
          </div>
          <div v-if="current.bills.length === 0" class="popup-all-done">全部处理完毕</div>
        </div>
      </div>

      <!-- 其它类型：单卡模式 -->
      <div v-else class="popup-card" :style="{ borderTopColor: current.color }">
        <div class="popup-header">
          <el-icon :size="18" :color="current.color"><component :is="current.icon" /></el-icon>
          <span class="popup-title">{{ current.typeLabel }}</span>
          <el-icon class="popup-close" :size="16" @click="closeCard(current)"><Close /></el-icon>
        </div>
        <div class="popup-body">{{ current.text }}</div>
        <div class="popup-detail">
          <div class="popup-detail-row">
            <span class="popup-detail-label">单据</span>
            <span class="popup-detail-value popup-detail-no">{{ current.detailBillNo || '—' }}</span>
          </div>
          <div class="popup-detail-row" v-if="current.detailContent">
            <span class="popup-detail-label">内容</span>
            <span class="popup-detail-value">{{ current.detailContent }}</span>
          </div>
        </div>
        <div class="popup-actions">
          <el-button v-if="current.routePath" size="small" type="primary" @click="onGo(current)">去处理</el-button>
          <el-button size="small" text @click="closeCard(current)">关闭</el-button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
/**
 * 全局弹窗提醒（2026-11-19「弹窗设置」+ 三次迭代）：
 * - 待审批 = 居中待办列表：每张单显示 头信息+明细行，各自「通过/驳回」，处理完自动从列表移除，全部处理完自动关闭
 * - 明细来自后端统一摘要接口（单据类型白名单 + 参数化查询）
 * - 审批结果 / 工序通知 = 单卡 + 去处理跳转
 * - 即时性：提交/审批动作成功后由请求拦截器广播铃铛立即刷新
 * - 开关与弹窗文案在 设置中心→弹窗设置 维护；同一条通知本会话只弹一次
 * - 全程静默降级：配置/接口失败一律不影响业务页面
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Close, Document, CircleCheck, Promotion } from '@element-plus/icons-vue'
import { getNotifications, markRead } from '@/api/system/notification'
import { getSysConfigByGroup } from '@/api/system/sysConfig'
import { approveBill, rejectBill, getBillSummary, getMyPendingApprovals } from '@/api/dashboard'

const EVENT_NOTIFICATION_INCREASE = 'erp:notification-increase'
const EVENT_CONFIG_CHANGED = 'erp:popup-config-changed'
const SS_SHOWN = 'erp.popupNotify.shown'
const CONTENT_MAX = 120

const TYPE_META = {
  approval:          { label: '待审批提醒',   color: '#409EFF', icon: Document },
  approval_finish:   { label: '审批结果提醒', color: '#67C23A', icon: CircleCheck },
  operation_mention: { label: '工序通知',     color: '#E6A23C', icon: Promotion }
}

const router = useRouter()
const queue = ref([])
const current = computed(() => queue.value[0] || null)
let uidSeq = 0

// ===== 配置 =====
const popupEnabled = ref(false)
const typeEnabled = {}
const typeTemplate = {}

async function loadConfig() {
  try {
    const res = await getSysConfigByGroup('notification')
    const list = Array.isArray(res) ? res : (Array.isArray(res?.data) ? res.data : [])
    for (const item of list) {
      if (!item || !item.configKey) continue
      const k = item.configKey
      if (k === 'notification.popup.enabled') {
        popupEnabled.value = String(item.configValue).toLowerCase() === 'true'
      } else if (k.startsWith('notification.popup.type.') && k.endsWith('.enabled')) {
        const type = k.slice('notification.popup.type.'.length, -'.enabled'.length)
        typeEnabled[type] = String(item.configValue).toLowerCase() === 'true'
      } else if (k.startsWith('notification.popup.type.') && k.endsWith('.template')) {
        const type = k.slice('notification.popup.type.'.length, -'.template'.length)
        typeTemplate[type] = String(item.configValue || '')
      }
    }
  } catch (e) {
    popupEnabled.value = false
  }
}

// ===== 已弹通知 id（sessionStorage） =====
function loadShown() {
  try {
    const raw = sessionStorage.getItem(SS_SHOWN)
    return new Set(raw ? JSON.parse(raw) : [])
  } catch (e) { return new Set() }
}
function saveShown(set) {
  try { sessionStorage.setItem(SS_SHOWN, JSON.stringify(Array.from(set).slice(-200))) } catch (e) { /* 忽略 */ }
}

// ===== 工具 =====
function parseBillType(title) {
  if (!title) return ''
  const m = /^(?:待办审批|待审批|审批结果)[:：]\s*(\S+)/.exec(String(title).trim())
  if (m) return m[1]
  const m2 = /^【(?:审批通过|审批驳回)】\s*(\S+)/.exec(String(title).trim())
  return m2 ? m2[1] : ''
}
function clipContent(text) {
  const t = String(text || '').trim()
  if (t.length <= CONTENT_MAX) return t
  return t.slice(0, CONTENT_MAX) + '…'
}
function formatAmount(v) {
  const n = Number(v)
  if (!isFinite(n)) return String(v)
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function shortTime(s) {
  if (!s) return ''
  const d = new Date(String(s).replace(' ', 'T'))
  if (isNaN(d.getTime())) return String(s).slice(0, 16)
  const p = x => String(x).padStart(2, '0')
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}
function fillTemplate(tpl, ctx) {
  return String(tpl || '')
    .replaceAll('{数量}', String(ctx.count > 0 ? ctx.count : 1))
    .replaceAll('{单据类型}', ctx.billTypeName || '')
    .replaceAll('{单号}', ctx.sourceNo || '')
    .replaceAll('{内容}', ctx.content || '')
}

// ===== 增量事件处理 =====
async function onIncrease(event) {
  try {
    if (!popupEnabled.value) return
    const byType = (event && event.detail && event.detail.byType) || {}
    for (const type of Object.keys(byType)) {
      const meta = TYPE_META[type]
      if (!meta) continue
      if (!typeEnabled[type]) continue
      const info = byType[type] || {}
      if (Number(info.delta || 0) <= 0) continue

      let latest = null
      try {
        const page = await getNotifications({ current: 1, size: 1, type, isRead: 0 })
        latest = (page && page.records && page.records[0]) || null
      } catch (e) { /* 降级 */ }
      if (latest && latest.id != null) {
        const shown = loadShown()
        if (shown.has(String(latest.id))) continue
        shown.add(String(latest.id))
        saveShown(shown)
      }

      if (type === 'approval') {
        // 待审批 → 待办列表模式：拉全部待我审批的单据，每张单独立明细与审批
        const bills = await loadPendingBills(latest)
        if (!bills.length) continue // 待办可能已被处理完
        const ctx = {
          count: bills.length,
          billTypeName: parseBillType(latest && latest.title),
          sourceNo: (latest && latest.sourceNo) || '',
          content: clipContent(latest && latest.content)
        }
        const container = {
          uid: ++uidSeq,
          type,
          isApprovalList: true,
          typeLabel: meta.label,
          color: meta.color,
          icon: meta.icon,
          text: fillTemplate(typeTemplate[type] || '', ctx),
          count: ctx.count,
          id: latest ? latest.id : null,
          bills
        }
        queue.value.push(container)
        // 明细懒加载（每张单并行拉摘要）。注意：必须用队列里的响应式代理与其中的深层代理对象，
        // 直接改 push 前的原始对象不会触发渲染（Vue3 响应式口径，实测踩坑）
        const cproxy = queue.value[queue.value.length - 1]
        for (const b of cproxy.bills) loadBillItems(b)
      } else {
        const ctx = {
          count: Number(info.count || 0),
          billTypeName: parseBillType(latest && latest.title),
          sourceNo: (latest && latest.sourceNo) || '',
          content: clipContent(latest && latest.content)
        }
        queue.value.push({
          uid: ++uidSeq,
          type,
          isApprovalList: false,
          typeLabel: meta.label,
          color: meta.color,
          icon: meta.icon,
          text: fillTemplate(typeTemplate[type] || '', ctx),
          count: ctx.count,
          id: latest ? latest.id : null,
          routePath: (latest && latest.routePath) || '',
          detailBillNo: (latest && latest.sourceNo) || '',
          detailContent: clipContent(latest && latest.content)
        })
      }
    }
  } catch (e) {
    // 弹窗提醒失败绝不影响任何业务
  }
}

/** 拉取「我的待办」并映射为弹窗单据列表 */
async function loadPendingBills(latest) {
  try {
    const res = await getMyPendingApprovals()
    const rows = Array.isArray(res) ? res : (Array.isArray(res?.data) ? res.data : [])
    return rows.filter(t => t && t.id != null).map(t => ({
      billId: t.id,
      billType: String(t.type || '').toUpperCase(),
      typeName: t.typeName || '',
      billNo: t.billNo || '',
      partyName: t.partyName || '',
      amount: (t.amount === null || t.amount === undefined || t.amount === '') ? null : t.amount,
      creator: t.creator || '',
      time: shortTime(t.createTime),
      routePath: t.routePath || '',
      items: [],
      summaryLoading: true,
      summaryError: '',
      rejecting: false,
      rejectReason: '',
      actionLoading: false,
      actionError: ''
    }))
  } catch (e) {
    // 待办拉取失败 → 退化为单张（用通知自带信息构造，只能看不能直接操作缺单据ID时仍可跳转）
    if (latest && latest.sourceId != null && latest.sourceType) {
      return [{
        billId: latest.sourceId,
        billType: String(latest.sourceType).toUpperCase(),
        typeName: parseBillType(latest && latest.title),
        billNo: (latest && latest.sourceNo) || '',
        partyName: '', amount: null, creator: '', time: '', routePath: (latest && latest.routePath) || '',
        items: [], summaryLoading: true, summaryError: '', rejecting: false, rejectReason: '', actionLoading: false, actionError: ''
      }]
    }
    return []
  }
}

/** 拉某张单的明细（摘要接口失败则显示错误，不影响其它单）。b 必须是响应式代理对象 */
async function loadBillItems(b) {
  try {
    const s = await getBillSummary(b.billType, b.billId)
    b.items = (s && s.items) || []
    if (s && (b.amount === null || b.amount === undefined) && s.amount != null) b.amount = s.amount
    if (s && !b.partyName && s.partyName) b.partyName = s.partyName
    if (s && !b.creator && s.creator) b.creator = s.creator
    if (s && !b.time && s.createTime) b.time = shortTime(s.createTime)
    b.summaryLoading = false
  } catch (e) {
    b.summaryLoading = false
    b.summaryError = '明细加载失败，可点「去处理」查看'
  }
}

// ===== 卡片操作 =====

/** 待办列表中某张单：approve=通过 / reject=驳回回草稿（引擎做审批人校验） */
async function onBillAction(container, b, action) {
  if (action === 'reject' && (!b.rejectReason || !b.rejectReason.trim())) {
    b.actionError = '请输入驳回原因'
    return
  }
  b.actionLoading = true
  b.actionError = ''
  try {
    const res = action === 'approve'
      ? await approveBill(b.billType, b.billId)
      : await rejectBill(b.billType, b.billId, b.rejectReason.trim())
    if (res && res.success) {
      ElMessage.success(action === 'approve' ? `${b.billNo || ''} 已审批通过`.trim() : `${b.billNo || ''} 已驳回，单据退回草稿`.trim())
      const idx = container.bills.findIndex(x => x.billId === b.billId && x.billType === b.billType)
      if (idx >= 0) container.bills.splice(idx, 1)
      if (container.bills.length === 0) {
        // 全部处理完毕：自动关闭
        const qidx = queue.value.findIndex(c => c.uid === container.uid)
        if (qidx >= 0) queue.value.splice(qidx, 1)
      }
      try { window.dispatchEvent(new CustomEvent('erp:notification-refresh')) } catch (e) { /* 忽略 */ }
    } else {
      b.actionError = (res && res.errorMessage) || '操作失败'
    }
  } catch (e) {
    b.actionError = (e && e.message) || '操作失败'
  } finally {
    b.actionLoading = false
  }
}

/** 列表里某张单跳转：标记触发通知已读（失败不阻塞） */
async function onBillGo(container, b) {
  if (container.id != null) {
    try { await markRead(container.id) } catch (e) { /* 忽略 */ }
  }
  dismissCard(container)
  if (b.routePath) router.push(b.routePath)
}

/** 单卡去处理 */
async function onGo(card) {
  dismissCard(card)
  if (card.id != null) {
    try { await markRead(card.id) } catch (e) { /* 忽略 */ }
  }
  if (card.routePath) router.push(card.routePath)
}

function dismissCard(card) {
  const idx = queue.value.findIndex(c => c.uid === card.uid)
  if (idx >= 0) queue.value.splice(idx, 1)
}

function closeCard(card) {
  dismissCard(card)
}

function onConfigChanged() {
  loadConfig()
}

onMounted(() => {
  loadConfig()
  try {
    window.addEventListener(EVENT_NOTIFICATION_INCREASE, onIncrease)
    window.addEventListener(EVENT_CONFIG_CHANGED, onConfigChanged)
  } catch (e) { /* 忽略 */ }
})

onUnmounted(() => {
  try {
    window.removeEventListener(EVENT_NOTIFICATION_INCREASE, onIncrease)
    window.removeEventListener(EVENT_CONFIG_CHANGED, onConfigChanged)
  } catch (e) { /* 忽略 */ }
})
</script>

<style scoped>
.popup-notify-mask {
  position: fixed;
  inset: 0;
  z-index: 4000;
  background: rgba(0, 0, 0, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
}
.popup-card {
  width: 460px;
  max-width: calc(100vw - 48px);
  max-height: 76vh;
  overflow-y: auto;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-top: 3px solid #409EFF;
  border-radius: 8px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.18);
  padding: 16px 18px;
  animation: popup-notify-in 0.22s ease-out;
}
.popup-card-list {
  width: 560px;
}
@keyframes popup-notify-in {
  from { opacity: 0; transform: translateY(-12px) scale(0.98); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}
.popup-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.popup-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  flex: 1;
}
.popup-count {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
}
.popup-close {
  cursor: pointer;
  color: #909399;
  flex-shrink: 0;
}
.popup-close:hover {
  color: #606266;
}
.popup-body {
  margin-top: 10px;
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
  word-break: break-all;
}
.popup-bill-list {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.popup-bill {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px 12px;
  background: #fafbfc;
}
.popup-bill-head {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.popup-bill-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 13px;
}
.popup-bill-type {
  color: #606266;
  flex-shrink: 0;
}
.popup-bill-no {
  color: var(--erp-primary, #409EFF);
  font-weight: 600;
  flex: 1;
}
.popup-bill-amount {
  color: #f56c6c;
  font-weight: 600;
}
.popup-bill-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  font-size: 12px;
  color: #909399;
}
.popup-bill-items {
  margin-top: 8px;
  background: #fff;
  border: 1px dashed #e4e7ed;
  border-radius: 4px;
  padding: 6px 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.popup-item-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: 12px;
  color: #606266;
  line-height: 1.5;
}
.popup-item-name {
  flex: 1;
  color: #303133;
  word-break: break-all;
}
.popup-item-qty, .popup-item-price, .popup-item-amount {
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}
.popup-item-amount {
  color: #303133;
  font-weight: 600;
}
.popup-items-loading, .popup-items-error {
  font-size: 12px;
  color: #909399;
}
.popup-items-error {
  color: #f56c6c;
}
.popup-all-done {
  text-align: center;
  color: #67C23A;
  font-size: 13px;
  padding: 16px 0;
}
.popup-actions {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.popup-actions-left {
  justify-content: flex-start;
}
.popup-reject-box {
  margin-top: 8px;
}
.popup-reject-btns {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.popup-error {
  margin-top: 6px;
  font-size: 12px;
  color: #f56c6c;
}
.popup-detail {
  margin-top: 10px;
  background: #f5f7fa;
  border-radius: 6px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.popup-detail-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
  font-size: 13px;
  line-height: 1.5;
}
.popup-detail-label {
  color: #909399;
  flex-shrink: 0;
  width: 62px;
  font-size: 12px;
}
.popup-detail-value {
  color: #303133;
  word-break: break-all;
}
.popup-detail-no {
  font-weight: 600;
  color: var(--erp-primary, #409EFF);
}
</style>
