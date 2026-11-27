<template>
  <div class="collapsible-card" :class="{ collapsed: !expanded }">
    <div class="card-header" @click="toggle">
      <div class="header-left">
        <el-icon class="collapse-icon" :class="{ active: expanded }"><ArrowRight /></el-icon>
        <span class="card-title">{{ $tt("审核流转") }}</span>
        <el-tag v-if="summaryTag" size="small" :type="summaryTag.type" effect="plain" class="summary-tag">
          {{ summaryTag.text }}
        </el-tag>
      </div>
      <div class="header-right" @click.stop>
        <el-button v-if="loadError" size="small" type="primary" plain v-permission="perm('refresh')" @click="load">{{ $tt("重试") }}</el-button>
      </div>
    </div>

    <div v-show="expanded" class="card-body">
      <div v-if="loading" class="state-tip">{{ $tt("加载中…") }}</div>
      <div v-else-if="loadError" class="state-tip error">{{ loadError }}</div>
      <div v-else-if="steps.length === 0" class="state-tip">{{ emptyText }}</div>
      <el-timeline v-else class="flow-timeline">
        <el-timeline-item
          v-for="(s, i) in steps"
          :key="i"
          :type="s.dotType"
          :hollow="s.hollow"
          :timestamp="s.time"
          placement="top"
        >
          <div class="step-title">{{ s.title }}</div>
          <div v-if="s.candidates && s.candidates.length" class="step-line">
            <span class="step-label">{{ $tt("候选审批人：") }}</span>
            <span class="cand-wrap">
              <el-tag
                v-for="c in s.candidates"
                :key="c.name"
                size="small"
                :type="c.done ? 'success' : 'info'"
                :effect="c.done ? 'dark' : 'plain'"
                class="cand-tag"
              >{{ c.name }}{{ c.done ? ' 已审批' : ' 未审批' }}</el-tag>
            </span>
          </div>
          <div v-else-if="s.approver" class="step-line">
            <span class="step-label">{{ $tt("审批人：") }}</span>{{ s.approver }}
          </div>
          <div v-if="s.comment" class="step-line">
            <span class="step-label">{{ $tt("意见：") }}</span>{{ s.comment }}
          </div>
        </el-timeline-item>
      </el-timeline>
    </div>
  </div>
</template>

<script setup>
/**
 * 单据审批流转进度（通用组件，真实审批数据驱动）
 *
 * 用法：<ApprovalFlowTimeline :bill-type="'SALE_ORDER'" :bill-id="form.orderId" :status="form.status" />
 *
 * 数据来源（现成后端接口，只读）：
 *   GET /api/approval/tasks/bill/{billType}/{billId}  节点级任务（状态/当前审批人/候选审批人）
 *   GET /api/approval/logs/bill/{billType}/{billId}   审批历史（实际审批人/意见/时间）
 *   GET /api/approval/flows/{flowId}                  流程定义（节点名/审批模式/候选审批人）
 *
 * 边界：引擎未实现节点内会签计数，"同一节点多个候选中谁审了"只能标出"实际审批人=已审批，其余=未审批"。
 */
import { ref, computed, watch, onMounted } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'
import { getBillTasks, getBillApprovalLogs, getApprovalFlow } from '@/api/approval'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm()


const props = defineProps({
  /** 单据审批编码，与设置中心「单据审批开关」一致（如 SALE_ORDER / PURCHASE_ORDER） */
  billType: { type: String, required: true },
  /** 单据主键；为空表示未保存 */
  billId: { type: [Number, String], default: null },
  /** 单据状态（中文），用于无任务时区分"尚未提交"与"未启用审批流程" */
  status: { type: [String, Number], default: '' },
  /** 默认展开 */
  defaultExpanded: { type: Boolean, default: true }
})

const expanded = ref(props.defaultExpanded)
const loading = ref(false)
const loadError = ref('')
const steps = ref([])
const emptyText = ref('尚未提交，暂无审批进度')

const DRAFT_STATUSES = ['', '草稿', 'DRAFT', '已取消', '已作废', '已驳回', '驳回']

function toggle () {
  expanded.value = !expanded.value
}

const summaryTag = computed(() => {
  if (loading.value || loadError.value) return null
  if (steps.value.length === 0) return null
  const pending = steps.value.find(s => s.raw === 'pending')
  if (pending) return { text: `当前：${pending.nodeName}`, type: 'warning' }
  const rejected = steps.value.find(s => s.raw === 'rejected')
  if (rejected) return { text: `已驳回：${rejected.nodeName}`, type: 'danger' }
  return { text: '审批已完成', type: 'success' }
})

function normStatus (v) {
  return v === null || v === undefined ? '' : String(v)
}

function actionText (taskStatus) {
  switch (taskStatus) {
    case 'approved': return '已通过'
    case 'rejected': return '已驳回'
    case 'transferred': return '已转交'
    default: return '待审批'
  }
}

function dotTypeOf (taskStatus) {
  switch (taskStatus) {
    case 'approved': return 'success'
    case 'rejected': return 'danger'
    case 'transferred': return 'warning'
    default: return 'primary'
  }
}

function fmtTime (v) {
  if (!v) return ''
  const s = String(v).replace('T', ' ')
  return s.length > 16 ? s.slice(0, 16) : s
}

async function load () {
  loadError.value = ''
  steps.value = []
  if (!props.billId) {
    emptyText.value = '保存后可查看审批进度'
    return
  }
  loading.value = true
  try {
    const [tasks, logs] = await Promise.all([
      getBillTasks(props.billType, props.billId).catch(() => []),
      getBillApprovalLogs(props.billType, props.billId).catch(() => [])
    ])
    const taskList = Array.isArray(tasks) ? tasks : []
    const logList = Array.isArray(logs) ? logs : []

    if (taskList.length === 0) {
      const st = normStatus(props.status)
      emptyText.value = DRAFT_STATUSES.includes(st)
        ? '尚未提交，暂无审批进度'
        : '当前未启用审批流程（提交即生效）'
      return
    }

    // 流程定义（用于节点名与候选审批人）
    let nodes = []
    try {
      const flowId = taskList[0].flowId
      if (flowId) {
        const flow = await getApprovalFlow(flowId).catch(() => null)
        nodes = flow && Array.isArray(flow.nodes) ? flow.nodes : []
      }
    } catch (e) { nodes = [] }

    const sorted = [...taskList].sort((a, b) => (a.nodeOrder || 0) - (b.nodeOrder || 0))
    const list = []

    // 提交步骤
    const first = sorted[0]
    list.push({
      title: '提交申请',
      time: fmtTime(first.submitTime),
      dotType: 'primary',
      hollow: false,
      raw: 'submit',
      nodeName: '提交申请',
      candidates: [],
      approver: first.submitBy || '',
      comment: ''
    })

    sorted.forEach(t => {
      const node = nodes.find(n => Number(n.nodeOrder) === Number(t.nodeOrder)) || null
      const nodeName = (node && node.nodeName) || t.nodeName || `第${t.nodeOrder}级审批`
      const log = [...logList].reverse().find(l => Number(l.nodeId) === Number(t.nodeId))
        || [...logList].reverse().find(l => Number(l.nodeOrder) === Number(t.nodeOrder))
      const approver = (log && log.approver) || (t.taskStatus && t.taskStatus !== 'pending' ? t.currentApprover : '')
      const comment = (log && log.comment) || t.comment || ''
      const time = (log && log.actionTime) || t.finishTime || t.submitTime

      // 候选审批人（来自流程定义；带出"谁已审/谁未审"）
      const candidates = []
      if (node && Array.isArray(node.approvers)) {
        node.approvers.forEach(a => {
          const name = a.approverName || a.name
          if (!name) return
          if (!candidates.some(c => c.name === name)) {
            candidates.push({ name, done: !!approver && name === approver })
          }
        })
      }
      // 无流程定义时退化用 assignee_ids（实际存的是姓名列表）
      if (candidates.length === 0 && t.assigneeIds) {
        String(t.assigneeIds).split(',').map(s => s.trim()).filter(Boolean).forEach(name => {
          if (!candidates.some(c => c.name === name)) {
            candidates.push({ name, done: !!approver && name === approver })
          }
        })
      }
      // 单人模式：把当前/实际审批人作为唯一候选展示
      if (candidates.length === 0 && (approver || t.currentApprover)) {
        const nm = approver || t.currentApprover
        candidates.push({ name: nm, done: !!approver })
      }

      list.push({
        nodeName,
        title: `${nodeName} · ${actionText(t.taskStatus)}`,
        time: fmtTime(time),
        dotType: dotTypeOf(t.taskStatus),
        hollow: t.taskStatus === 'pending',
        raw: t.taskStatus || 'pending',
        candidates,
        approver,
        comment
      })
    })

    steps.value = list
  } catch (e) {
    loadError.value = '审批进度加载失败：' + (e && e.message ? e.message : tt('未知错误'))
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => [props.billType, props.billId], () => { load() })
</script>

<style scoped lang="scss">
.collapsible-card {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  margin-bottom: 10px;

  &.collapsed {
    .card-header { border-bottom: none; }
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 14px;
    background: #fafbfc;
    border-bottom: 1px solid #e4e7ed;
    cursor: pointer;
    user-select: none;

    .header-left {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .header-right { display: flex; gap: 6px; align-items: center; }

    .collapse-icon {
      color: #909399;
      font-size: 12px;
      transition: transform .25s ease;
      &.active { transform: rotate(90deg); color: #409eff; }
    }

    .card-title { font-size: 13px; font-weight: 600; color: #303133; }
    .summary-tag { margin-left: 2px; }
  }

  .card-body { padding: 12px 14px; }
}

.state-tip {
  font-size: 12px;
  color: #909399;
  padding: 6px 0;

  &.error { color: #f56c6c; }
}

.flow-timeline {
  padding-left: 2px;

  :deep(.el-timeline-item__timestamp) {
    font-size: 11px;
    color: #909399;
  }

  .step-title {
    font-size: 12px;
    font-weight: 600;
    color: #303133;
  }

  .step-line {
    font-size: 12px;
    color: #606266;
    margin-top: 4px;
    line-height: 20px;

    .step-label { color: #909399; }
  }

  .cand-wrap {
    display: inline-flex;
    flex-wrap: wrap;
    gap: 6px;
    vertical-align: middle;
  }

  .cand-tag { font-size: 11px; }
}
</style>
