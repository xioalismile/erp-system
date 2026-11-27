<template>
  <div class="m-page">
    <!-- 页头：标题 + 状态；操作按钮一律放在内容顶部的操作行，不做底部操作条 -->
    <div class="m-header">
      <span class="m-back" @click="goBack">‹</span>
      <span class="m-title">{{ op.operationName || '工序操作' }}</span>
      <span class="m-status" :class="statusClass">{{ op.operationStatus || '待派工' }}</span>
    </div>

    <div v-if="loading" class="m-loading">加载中…</div>

    <div v-else-if="op.id" class="m-body">
      <!-- 操作行（页头正下方紧贴内容） -->
      <div class="m-card m-actions-card">
        <div class="m-actions">
          <button
            v-for="a in actionButtons"
            :key="a.code"
            class="m-btn"
            :class="a.primary ? 'm-btn-primary' : ''"
            :disabled="acting"
            @click="doAction(a.code)"
          >{{ a.label }}</button>
        </div>
        <div class="m-hint">{{ actionHint }}</div>
      </div>

      <!-- 工序信息 -->
      <div class="m-card">
        <div class="m-card-title">工序信息</div>
        <div class="m-row"><span class="m-label">工单号</span><span>{{ op.orderNo || '' }}</span></div>
        <div class="m-row"><span class="m-label">工序</span><span>{{ (op.lineNo || '') + ' ' + (op.operationName || '') }}</span></div>
        <div class="m-row"><span class="m-label">类型</span><span>{{ op.operationType || '状态类型' }}</span></div>
        <div class="m-row"><span class="m-label">负责人</span><span>{{ op.assigneeName || '未派工' }}</span></div>
        <div class="m-row"><span class="m-label">计划完工</span><span>{{ op.planEndDate || '' }}</span></div>
        <div class="m-row">
          <span class="m-label">进度</span>
          <span>
            已报 {{ fmt(op.reportedQuantity) }} / 计划 {{ fmt(op.planQuantity) }}
            <template v-if="op.operationType === '入库类型'"> · 已入库 {{ fmt(op.receivedQty) }}</template>
          </span>
        </div>
      </div>

      <!-- 报工（仅数量类型） -->
      <div v-if="op.operationType === '数量类型'" class="m-card">
        <div class="m-card-title">报工</div>
        <div class="m-field">
          <span class="m-field-label">本次完成</span>
          <input v-model="reportForm.quantity" class="m-input" type="number" inputmode="decimal" placeholder="0" :disabled="!canReport" />
        </div>
        <div class="m-field">
          <span class="m-field-label">本次报废</span>
          <input v-model="reportForm.rejectedQuantity" class="m-input" type="number" inputmode="decimal" placeholder="0" :disabled="!canReport" />
        </div>
        <div class="m-field">
          <span class="m-field-label">备注</span>
          <input v-model="reportForm.remark" class="m-input" type="text" placeholder="选填" :disabled="!canReport" />
        </div>
        <div class="m-actions">
          <button class="m-btn m-btn-primary" :disabled="!canReport || reporting" @click="doReport">
            {{ reporting ? '提交中…' : '报工' }}
          </button>
        </div>
        <div v-if="!canReport" class="m-hint">当前状态不能报工</div>
      </div>

      <div v-if="op.operationStatus === '完工待检'" class="m-card m-hint-card">
        该工序需质检：请到电脑端工序操作面板「填质检」录入检验项后提交，检验通过才算完工。
      </div>

      <!-- 共同负责人 -->
      <div class="m-card">
        <div class="m-card-title">共同负责人</div>
        <div v-if="workers.length" class="m-workers">
          <span v-for="w in workers" :key="w.employeeId" class="m-tag">
            {{ w.employeeName || '' }}
            <template v-if="w.claimQuantity != null"> · 认领 {{ fmt(w.claimQuantity) }}</template>
          </span>
        </div>
        <div v-else class="m-hint">暂无共同负责人</div>
        <div class="m-row" v-if="op.myJoined">
          <span class="m-label">我</span>
          <span>认领 {{ op.myClaimQuantity == null ? '不限' : fmt(op.myClaimQuantity) }} · 已报 {{ fmt(op.myReportedQuantity) }}<template v-if="op.myRemainingQuantity != null"> · 还可报 {{ fmt(op.myRemainingQuantity) }}</template></span>
        </div>
        <template v-if="op.canClaim">
          <div class="m-field">
            <span class="m-field-label">认领数量</span>
            <input v-model="claimQty" class="m-input" type="number" inputmode="decimal" placeholder="可不填" />
          </div>
          <div class="m-actions">
            <button class="m-btn m-btn-primary" :disabled="claiming" @click="doClaim">我来做</button>
          </div>
        </template>
        <template v-else-if="op.myJoined">
          <div class="m-actions">
            <button class="m-btn" :disabled="claiming" @click="doUnclaim">退出</button>
          </div>
        </template>
        <div v-else-if="op.claimDisabledReason" class="m-hint">{{ op.claimDisabledReason }}</div>
      </div>

      <!-- 最近操作记录 -->
      <div class="m-card">
        <div class="m-card-title">最近操作</div>
        <div v-if="logs.length" class="m-logs">
          <div v-for="(l, i) in logs" :key="i" class="m-log">
            <span class="m-log-time">{{ fmtTime(l.operateTime) }}</span>
            <span class="m-log-action">{{ l.actionType }}</span>
            <span class="m-log-remark">{{ l.remark || '' }}</span>
          </div>
        </div>
        <div v-else class="m-hint">暂无操作记录</div>
      </div>
    </div>

    <div v-else class="m-loading">{{ errorMsg || '没有找到该工序' }}</div>
  </div>
</template>

<script setup>
/**
 * 移动端工序操作页（/m/operation/:id）：扫码进入后在本页完成 开工/暂停/恢复/完工/报工/认领。
 * 口径：动作是否可执行完全取后端状态元数据（不前端自算），页面只在内容顶部排一条操作行，不做底部操作条。
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getOperationWorkerDetail, getOperationStatusMeta, getOperationLogs,
  actOperation, reportOperation, claimOperation, unclaimOperation
} from '@/api/production/workOrderOperation'

const route = useRoute()
const router = useRouter()

const op = ref({})
const meta = ref({ states: [] })
const logs = ref([])
const loading = ref(true)
const acting = ref(false)
const reporting = ref(false)
const claiming = ref(false)
const errorMsg = ref('')
const claimQty = ref('')

const reportForm = reactive({ quantity: '', rejectedQuantity: '', remark: '' })

const workers = computed(() => op.value?.workers || [])

/** 当前状态允许的动作（后端下发） */
const currentActions = computed(() => {
  const st = op.value?.operationStatus || '待派工'
  const row = (meta.value.states || []).find(s => s.status === st)
  return row ? (row.actions || []) : []
})

/** 只保留移动端需要的推进动作；不可执行的不渲染 */
const actionButtons = computed(() => {
  const defs = [
    { code: '开工', label: '开工' },
    { code: '暂停', label: '暂停' },
    { code: '恢复', label: '恢复' },
    { code: '完工', label: '完工', primary: true }
  ]
  return defs.filter(d => currentActions.value.includes(d.code))
})

const actionHint = computed(() => {
  if (!currentActions.value.length) {
    return op.value?.operationStatus === '已取消' ? '该工序已取消' : '该工序已结束'
  }
  if (!actionButtons.value.length) return '本道工序在手机端没有可执行动作'
  return '按现场进度点对应按钮，系统自动记工时与操作日志'
})

const canReport = computed(() => {
  if ((op.value?.operationType || '') !== '数量类型') return false
  const st = op.value?.operationStatus
  if (['已派工', '已开工', '进行中'].includes(st)) return true
  return st === '待派工' && op.value?.myJoined === true
})

const statusClass = computed(() => {
  const st = op.value?.operationStatus
  if (st === '已完工') return 'is-done'
  if (st === '已取消') return 'is-cancel'
  if (st === '完工待检') return 'is-wait'
  return ''
})

function fmt (v) {
  if (v === null || v === undefined || v === '') return '0'
  const n = Number(v)
  return Number.isFinite(n) ? String(Number(n.toFixed(4))) : String(v)
}

function fmtTime (t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(5, 16)
}

async function load () {
  const id = route.params.id
  loading.value = true
  errorMsg.value = ''
  try {
    const detail = await getOperationWorkerDetail(id)
    op.value = detail || {}
    if (detail?.operationType) {
      meta.value = (await getOperationStatusMeta(detail.operationType)) || { states: [] }
    }
    logs.value = (await getOperationLogs(id).catch(() => [])) || []
    logs.value = logs.value.slice(0, 6)
  } catch (e) {
    errorMsg.value = e?.message || '工序加载失败'
  } finally {
    loading.value = false
  }
}

async function doAction (action) {
  if (acting.value) return
  if (action === '完工' && !window.confirm('确认本道工序已完工？')) return
  acting.value = true
  try {
    await actOperation(route.params.id, { action })
    await load()
  } catch (e) {
    window.alert(e?.message || '操作失败')
  } finally {
    acting.value = false
  }
}

async function doReport () {
  const qty = Number(reportForm.quantity || 0)
  if (!(qty > 0)) {
    window.alert('请填写本次完成数量')
    return
  }
  reporting.value = true
  try {
    await reportOperation(route.params.id, {
      quantity: qty,
      rejectedQuantity: Number(reportForm.rejectedQuantity || 0),
      remark: reportForm.remark || ''
    })
    reportForm.quantity = ''
    reportForm.rejectedQuantity = ''
    reportForm.remark = ''
    await load()
    window.alert('报工成功')
  } catch (e) {
    window.alert(e?.message || '报工失败')
  } finally {
    reporting.value = false
  }
}

async function doClaim () {
  claiming.value = true
  try {
    const qty = claimQty.value === '' ? null : Number(claimQty.value)
    await claimOperation(route.params.id, { claimQuantity: qty })
    claimQty.value = ''
    await load()
  } catch (e) {
    window.alert(e?.message || '认领失败')
  } finally {
    claiming.value = false
  }
}

async function doUnclaim () {
  claiming.value = true
  try {
    await unclaimOperation(route.params.id)
    await load()
  } catch (e) {
    window.alert(e?.message || '退出失败')
  } finally {
    claiming.value = false
  }
}

function goBack () {
  if (window.history.length > 1) router.back()
  else router.push('/m/scan')
}

onMounted(load)
</script>

<style scoped>
.m-page {
  min-height: 100vh;
  background: #f5f6f8;
  font-size: 14px;
  color: #1f2937;
}
.m-header {
  position: sticky;
  top: 0;
  z-index: 5;
  display: flex;
  align-items: center;
  gap: 8px;
  height: 46px;
  padding: 0 12px;
  background: #fff;
  border-bottom: 1px solid #eef0f3;
}
.m-back {
  width: 26px;
  font-size: 22px;
  line-height: 1;
  color: #4b5563;
}
.m-title {
  flex: 1;
  font-size: 17px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.m-status {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  background: #eff6ff;
  color: #2563eb;
}
.m-status.is-done { background: #ecfdf5; color: #059669; }
.m-status.is-cancel { background: #f3f4f6; color: #6b7280; }
.m-status.is-wait { background: #fff7ed; color: #d97706; }
.m-body {
  padding: 10px 12px 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.m-card {
  background: #fff;
  border-radius: 8px;
  padding: 12px;
}
.m-actions-card { padding: 10px 12px; }
.m-card-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
}
.m-actions {
  display: flex;
  gap: 8px;
}
.m-btn {
  flex: 1;
  height: 36px;
  border-radius: 6px;
  border: 1px solid #dbe2ea;
  background: #fff;
  font-size: 14px;
  color: #1f2937;
}
.m-btn-primary {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}
.m-btn[disabled] {
  opacity: 0.55;
}
.m-row {
  display: flex;
  gap: 10px;
  padding: 3px 0;
  font-size: 13px;
}
.m-label {
  flex: none;
  width: 60px;
  color: #9ca3af;
}
.m-field {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 0;
}
.m-field-label {
  flex: none;
  width: 60px;
  font-size: 13px;
  color: #9ca3af;
}
.m-input {
  flex: 1;
  height: 36px;
  padding: 0 10px;
  font-size: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  outline: none;
  background: #fafbfc;
}
.m-hint {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: #9ca3af;
}
.m-hint-card { font-size: 13px; color: #d97706; }
.m-workers {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.m-tag {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  background: #f3f4f6;
  color: #4b5563;
}
.m-logs {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.m-log {
  display: flex;
  gap: 8px;
  font-size: 12px;
  color: #6b7280;
}
.m-log-time { flex: none; width: 84px; }
.m-log-action { flex: none; width: 64px; color: #1f2937; }
.m-log-remark { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.m-loading {
  padding: 40px 12px;
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
}
</style>
