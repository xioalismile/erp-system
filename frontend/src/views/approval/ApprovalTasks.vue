<template>
  <div class="approval-tasks-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <span class="title">{{ $tt("我的待办") }}</span>
            <el-tag type="warning" size="small">{{ tasks.length }} 条待办</el-tag>
          </div>
          <el-button :icon="Refresh" v-permission="perm('refresh')" @click="loadData">{{ $tt("刷新") }}</el-button>
        </div>
      </template>

      <el-table :data="tasks" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="billNo" :label="$tf('单据编号')" width="180" sortable>
          <template #default="{ row }">
            <!-- 单据编号可直接打开目标单据（统一走 utils/billRoute.js；无独立编辑页的单据退化为纯文本） -->
            <span
              :class="['bill-no-text', billJumpPath(row) ? 'is-link' : 'no-link']"
              @click="goToBill(row)"
            >{{ row.billNo }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$tf('单据类型')" width="140">
          <template #default="{ row }">
            <el-tag size="small">{{ row.billType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="nodeName" :label="$tf('当前节点')" width="160" sortable />
        <el-table-column prop="nodeOrder" :label="$tf('节点序号')" width="100" align="center" sortable />
        <el-table-column prop="currentApprover" :label="$tf('指定审批人')" width="120" sortable>
          <template #default="{ row }">
            <span v-if="row.currentApprover">{{ row.currentApprover }}</span>
            <el-tag v-else type="warning" size="small">{{ $tt("待选择") }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="submitBy" :label="$tf('提交人')" width="120" sortable />
        <el-table-column prop="submitTime" :label="$tf('提交时间')" width="160" sortable>
          <template #default="{ row }">{{ formatTime(row.submitTime) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 审批弹窗 -->
    <el-dialog v-model="approveDialogVisible" :title="$tf('审批通过')" width="500px">
      <el-form label-width="80px">
        <el-form-item :label="$tf('单据')">{{ currentTask?.billNo }}</el-form-item>
        <el-form-item :label="$tf('节点')">{{ currentTask?.nodeName }}</el-form-item>
        <el-form-item :label="$tf('审批意见')">
          <el-input v-model="approveComment" type="textarea" :rows="3" :placeholder="$tt('请输入审批意见')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="approveDialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="success" v-permission="perm('audit')" @click="confirmApprove">{{ $tt("确认通过") }}</el-button>
      </template>
    </el-dialog>

    <!-- 驳回弹窗 -->
    <el-dialog v-model="rejectDialogVisible" :title="$tf('审批驳回')" width="500px">
      <el-form label-width="80px">
        <el-form-item :label="$tf('单据')">{{ currentTask?.billNo }}</el-form-item>
        <el-form-item :label="$tf('节点')">{{ currentTask?.nodeName }}</el-form-item>
        <el-form-item :label="$tf('驳回原因')">
          <el-input v-model="rejectComment" type="textarea" :rows="3" :placeholder="$tt('请输入驳回原因')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="rejectDialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="danger" v-permission="perm('reject')" @click="confirmReject">{{ $tt("确认驳回") }}</el-button>
      </template>
    </el-dialog>

    <!-- 转交弹窗 -->
    <el-dialog v-model="transferDialogVisible" :title="$tf('转交审批')" width="500px">
      <el-form label-width="80px">
        <el-form-item :label="$tf('单据')">{{ currentTask?.billNo }}</el-form-item>
        <el-form-item :label="$tf('转交给')">
          <el-select
            v-model="transferTo"
            :placeholder="$tt('搜索员工姓名/工号/部门')"
            filterable
            remote
            reserve-keyword
            :remote-method="remoteSearchTransferTarget"
            :loading="transferLoading"
            style="width: 100%"
            popper-class="approval-transfer-popper"
          >
            <el-option
              v-for="opt in transferOptions"
              :key="opt.value"
              :label="$tf(opt.label)"
              :value="opt.value"
            >
              <div class="transfer-opt-row">
                <span class="transfer-opt-name">{{ opt.raw.employeeName }}</span>
                <span v-if="opt.raw.employeeCode" class="transfer-opt-sub">{{ opt.raw.employeeCode }}</span>
                <span v-if="opt.raw.departmentName" class="transfer-opt-sub">{{ opt.raw.departmentName }}</span>
                <span v-if="opt.raw.positionName" class="transfer-opt-sub">{{ opt.raw.positionName }}</span>
              </div>
            </el-option>
          </el-select>
          <div class="transfer-tip">
            提示：值使用员工姓名（与运行时审批匹配字段保持一致），与当前"候选人姓名数组"策略兼容
          </div>
        </el-form-item>
        <el-form-item :label="$tf('转交说明')">
          <el-input v-model="transferComment" type="textarea" :rows="3" :placeholder="$tt('转交说明')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="transferDialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="warning" v-permission="perm('assign')" @click="confirmTransfer">{{ $tt("确认转交") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { tt } from '@/i18n'
import { resolveBillRoute } from '@/utils/billRoute'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('system:approval')

const router = useRouter()

const loading = ref(false)
const tasks = ref([])

/** 待办单据编号 → 目标编辑页路由（后端下发 billType/billId；无独立编辑页者返回 null = 纯文本） */
function billJumpPath(row) {
  return resolveBillRoute(row && row.billType, row && row.billId)
}

function goToBill(row) {
  const path = billJumpPath(row)
  // 单据已删除/无独立编辑页时给明确提示（2026-09-28：不再"点了没反应"）
  if (!path) { ElMessage.warning(tt('该单据已删除或无法打开')); return }
  router.push(path)
}

const api = {
  myPending: () => request.get('/approval/tasks/my-pending'),
  approve: (taskId, comment) => request.post(`/approval/tasks/${taskId}/approve`, { comment }),
  reject: (taskId, comment) => request.post(`/approval/tasks/${taskId}/reject`, { comment }),
  transfer: (taskId, to, comment) => request.post(`/approval/tasks/${taskId}/transfer`, { toApprover: to, comment }),
  listEmployeeOptions: (params) => request.get('/basedata/employee/select-options', { params })
}

const approveDialogVisible = ref(false)
const rejectDialogVisible = ref(false)
const transferDialogVisible = ref(false)
const currentTask = ref(null)
const approveComment = ref('')
const rejectComment = ref('')
const transferTo = ref('')
const transferComment = ref('')
const transferLoading = ref(false)
const transferOptions = ref([])

async function loadData() {
  loading.value = true
  try {
    const res = await api.myPending()
    // 拦截器已解包：res 即数组本体
    tasks.value = Array.isArray(res) ? res : (res?.data || [])
  } catch {
    ElMessage.error(tt('加载待办失败'))
  } finally {
    loading.value = false
  }
}

function formatTime(t) {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 16)
}

let _transferReqId = 0
async function remoteSearchTransferTarget(kw) {
  transferLoading.value = true
  const reqId = ++_transferReqId
  try {
    const res = await api.listEmployeeOptions({ keyword: (kw || '').trim() || undefined })
    if (reqId !== _transferReqId) return
    // 拦截器已解包：res 即数组本体
    const list = Array.isArray(res) ? res : (Array.isArray(res?.data) ? res.data : [])
    transferOptions.value = list
      .filter(e => e && String(e.employeeName || '').trim())
      .map(e => {
        const raw = {
          employeeId: e.employeeId,
          userId: e.userId,
          employeeName: String(e.employeeName).trim(),
          employeeCode: e.employeeCode || '',
          departmentName: e.departmentName || '',
          positionName: e.positionName || ''
        }
        const parts = []
        if (raw.employeeName) parts.push(raw.employeeName)
        if (raw.employeeCode) parts.push(raw.employeeCode)
        if (raw.departmentName) parts.push(raw.departmentName)
        return {
          label: parts.join(' · '),
          // 运行时 transfer 匹配用 employeeName 与 assigneeIds 保持一致
          value: raw.employeeName,
          raw
        }
      })
  } catch (e) {
    console.warn('[ApprovalTasks] 搜索转交员工失败', e)
  } finally {
    if (reqId === _transferReqId) transferLoading.value = false
  }
}

async function confirmApprove() {
  try {
    await api.approve(currentTask.value.taskId, approveComment.value)
    ElMessage.success(tt('审批通过'))
    approveDialogVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || tt('审批失败'))
  }
}

async function confirmReject() {
  try {
    await api.reject(currentTask.value.taskId, rejectComment.value)
    ElMessage.success(tt('已驳回'))
    rejectDialogVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || tt('驳回失败'))
  }
}

async function confirmTransfer() {
  if (!transferTo.value) { ElMessage.warning(tt('请输入转交人')); return }
  try {
    await api.transfer(currentTask.value.taskId, transferTo.value, transferComment.value)
    ElMessage.success(tt('已转交'))
    transferDialogVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || tt('转交失败'))
  }
}

onMounted(() => { loadData() })
useAutoRefresh(() => { loadData() })
</script>

<style scoped lang="scss">
.approval-tasks-page { padding: 0; }
/* 单据编号：可跳时是链接样式，不可跳（无独立编辑页）时保持普通文本 */
.bill-no-text {
  &.is-link {
    color: #6366f1;
    font-weight: 500;
    cursor: pointer;
    &:hover { color: #8b5cf6; text-decoration: underline; }
  }
  &.no-link { color: #374151; cursor: default; }
}
.card-header {
  display: flex; justify-content: space-between; align-items: center;
  .header-left { display: flex; align-items: center; gap: 12px; }
  .title { font-size: 16px; font-weight: 600; color: #303133; }
}
.transfer-tip {
  margin-top: 6px;
  font-size: 12px; color: #909399; line-height: 1.5;
}
/* 转交下拉选项（在 popper 中展示） */
.transfer-opt-row {
  display: flex; flex-wrap: wrap; gap: 8px; align-items: center; line-height: 1.4;
  .transfer-opt-name { color: #303133; font-weight: 500; font-size: 13px; }
  .transfer-opt-sub  { color: #909399; font-size: 12px; }
}
:deep(.approval-transfer-popper) {
  .transfer-opt-row {
    display: flex; flex-wrap: wrap; gap: 8px; align-items: center; line-height: 1.4;
    .transfer-opt-name { color: #303133; font-weight: 500; font-size: 13px; }
    .transfer-opt-sub  { color: #909399; font-size: 12px; }
  }
}
</style>
