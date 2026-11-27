<template>
  <div class="approval-flow-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <span class="title">{{ $tt("审批流程管理") }}</span>
            <el-tag type="info" size="small">{{ $tt("支持无限级节点 · 多审批人选其一 · 条件审批") }}</el-tag>
          </div>
          <el-button type="primary" :icon="Plus" v-permission="perm('add')" @click="handleAdd">{{ $tt("新建流程") }}</el-button>
        </div>
      </template>

      <div class="erp-search-bar">
        <div class="erp-search-item">
          <span class="label">{{ $tt("单据类型:") }}</span>
          <el-select v-model="searchForm.billType" :placeholder="$tt('全部')" clearable style="width: 180px" @change="loadData">
            <el-option v-for="t in billTypes" :key="t.billType" :label="t.billName" :value="t.billType" />
          </el-select>
        </div>
        <div class="erp-search-item">
          <span class="label">{{ $tt("关键词:") }}</span>
          <el-input v-model="searchForm.keyword" :placeholder="$tt('流程编码/名称')" clearable style="width: 200px"
                    @keyup.enter="loadData" />
        </div>
        <el-button type="primary" :icon="Search" v-permission="perm('query')" @click="loadData">{{ $tt("查询") }}</el-button>
        <el-button :icon="RefreshRight" v-permission="perm('reset')" @click="handleReset">{{ $tt("重置") }}</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="flowCode" :label="$tf('流程编码')" width="200" sortable />
        <el-table-column prop="flowName" :label="$tf('流程名称')" min-width="180" sortable />
        <el-table-column :label="$tf('单据类型')" width="140">
          <template #default="{ row }">{{ getBillTypeName(row.billType) }}</template>
        </el-table-column>
        <el-table-column prop="nodeCount" :label="$tf('节点数')" width="80" align="center" sortable />
        <el-table-column :label="$tf('优先级')" width="90" align="center" sortable sort-by="priority">
          <template #default="{ row }">{{ row.priority == null ? 99 : row.priority }}</template>
        </el-table-column>
        <el-table-column :label="$tf('触发条件')" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-if="row.conditionExpr" type="warning" size="small">{{ formatConditionExpr(row.conditionExpr) }}</el-tag>
            <el-tag v-else type="info" size="small">{{ $tt("兜底流程") }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$tf('启用')" width="80" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" :active-value="1" :inactive-value="0"
                       @change="handleToggleEnabled(row)" />
          </template>
        </el-table-column>
        <el-table-column prop="description" :label="$tf('描述')" min-width="200" show-overflow-tooltip sortable />
        <el-table-column prop="createTime" :label="$tf('创建时间')" width="160" sortable>
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 流程编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑审批流程' : '新建审批流程'" width="95%" top="2vh"
               :close-on-click-modal="false" destroy-on-close>
      <div class="flow-edit-wrap">
        <!-- 基础信息 -->
        <div class="flow-info-bar">
          <el-form :model="formData" label-width="90px" inline>
            <el-form-item :label="$tf('流程编码')">
              <el-input v-model="formData.flowCode" :placeholder="$tt('如 sale_order_flow')" style="width: 200px" :disabled="docCodeDisabled" />
            </el-form-item>
            <el-form-item :label="$tf('流程名称')" required>
              <el-input v-model="formData.flowName" :placeholder="$tt('如 销售订单审批流程')" style="width: 220px" />
            </el-form-item>
            <el-form-item :label="$tf('单据类型')" required>
              <el-select v-model="formData.billType" :placeholder="$tt('选择单据类型')" style="width: 160px">
                <el-option v-for="t in billTypes" :key="t.billType" :label="t.billName" :value="t.billType" />
              </el-select>
            </el-form-item>
            <el-form-item :label="$tf('启用')">
              <el-switch v-model="formData.enabled" :active-value="1" :inactive-value="0" />
            </el-form-item>
            <el-form-item :label="$tf('描述')">
              <el-input v-model="formData.description" :placeholder="$tt('流程描述')" style="width: 260px" />
            </el-form-item>
            <el-form-item :label="$tf('优先级')">
              <el-input-number v-model="formData.priority" :min="1" :max="999" controls-position="right"
                               style="width: 130px" />
              <span class="form-tip">{{ $tt("数字越小优先级越高（同单据类型多流程时按优先级匹配）") }}</span>
            </el-form-item>
          </el-form>
        </div>

        <!-- 流程路由条件（conditionExpr 可视化：任意表头字段 + 自定义字段） -->
        <div class="route-condition-bar">
          <div class="route-header">
            <span class="title">{{ $tt("流程路由条件") }}</span>
            <el-tag type="info" size="small">{{ $tt("可选 · 不填则作为该单据类型的兜底流程") }}</el-tag>
            <el-button size="small" type="primary" plain :icon="Plus" class="route-add-btn"
                       :disabled="!formData.billType" v-permission="perm('addRow')" @click="addRouteRule">{{ $tt("添加条件") }}</el-button>
          </div>
          <div class="route-tip">
            同一单据类型可创建多个启用流程，系统按「优先级」依次匹配「触发条件」，全部满足则走该流程；无条件的流程作为兜底。
            条件可使用单据表头的任意字段（含自定义字段），例如：自定义字段「单据类型」等于「特殊销售单」→ 财务+老板审批。
          </div>
          <div v-if="!formData.billType" class="route-tip route-warn">{{ $tt("请先选择单据类型，再配置触发条件") }}</div>
          <el-table v-else-if="routeRules.length" :data="routeRules" size="small" border>
            <el-table-column :label="$tf('字段')" min-width="220">
              <template #default="{ row }">
                <el-select v-model="row.field" filterable :placeholder="$tt('选择字段（可搜索）')" style="width: 100%"
                           @change="onRuleFieldChange(row)">
                  <el-option-group :label="$tf('标准字段')">
                    <el-option v-for="f in standardFields" :key="f.fieldKey"
                               :label="`${f.label}（${f.fieldKey}）`" :value="f.fieldKey" />
                  </el-option-group>
                  <el-option-group v-if="customFields.length" :label="$tf('自定义字段')">
                    <el-option v-for="f in customFields" :key="f.fieldKey"
                               :label="`${f.label}（${f.fieldKey}）`" :value="f.fieldKey" />
                  </el-option-group>
                </el-select>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('运算符')" width="130">
              <template #default="{ row }">
                <el-select v-model="row.op" style="width: 100%">
                  <el-option v-for="(lbl, op) in OP_LABELS" :key="op" :label="lbl" :value="op" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('值')" min-width="200">
              <template #default="{ row }">
                <el-select v-if="ruleField(row) && ruleField(row).type === 'select' && ruleField(row).options"
                           v-model="row.value" filterable allow-create :placeholder="$tt('选择或输入值')" style="width: 100%">
                  <el-option v-for="o in ruleField(row).options" :key="o.value" :label="$tf(o.label)" :value="o.value" />
                </el-select>
                <el-input-number v-else-if="ruleField(row) && ruleField(row).type === 'number'"
                                 v-model="row.value" controls-position="right" style="width: 100%" />
                <el-date-picker v-else-if="ruleField(row) && ruleField(row).type === 'date'"
                                v-model="row.value" type="date" value-format="YYYY-MM-DD"
                                :placeholder="$tt('选择日期')" style="width: 100%" />
                <el-input v-else v-model="row.value" :placeholder="$tt('条件值')" />
              </template>
            </el-table-column>
            <el-table-column :label="$tf('操作')" width="70" align="center">
              <template #default="{ $index }">
                <el-button link type="danger" v-permission="perm('deleteRow')" @click="routeRules.splice($index, 1)">{{ $tt("删除") }}</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="route-preview">
            <span class="preview-label">{{ $tt("生成条件:") }}</span>
            <code>{{ conditionPreview || '（无条件，作为兜底流程）' }}</code>
          </div>
        </div>

        <!-- 节点编辑器 -->
        <div class="nodes-editor">
          <div class="nodes-header">
            <span class="title">{{ $tt("审批节点（按顺序流转，支持无限级）") }}</span>
            <el-button type="primary" :icon="Plus" size="small" v-permission="perm('addRow')" @click="addNode">{{ $tt("添加节点") }}</el-button>
          </div>
          <div class="nodes-list">
            <div v-for="(node, idx) in formData.nodes" :key="idx" class="node-card">
              <div class="node-header">
                <span class="node-index">{{ idx + 1 }}</span>
                <el-input v-model="node.nodeName" :placeholder="$tt('节点名称，如 部门经理审批')" style="width: 240px" />
                <el-select v-model="node.approverMode" :placeholder="$tt('审批模式')" style="width: 160px">
                  <el-option :label="$tf('单人审批')" value="single" />
                  <el-option :label="$tf('任选一人')" value="any" />
                  <el-option :label="$tf('全部审批')" value="all" />
                  <el-option :label="$tf('依次审批')" value="sequence" />
                </el-select>
                <span class="node-order">顺序: {{ node.nodeOrder }}</span>
                <el-button :icon="ArrowUp" size="small" circle v-permission="perm('edit')" @click="moveUp(idx)" :disabled="idx === 0" />
                <el-button :icon="ArrowDown" size="small" circle v-permission="perm('edit')" @click="moveDown(idx)"
                           :disabled="idx === formData.nodes.length - 1" />
                <el-button type="danger" :icon="Delete" size="small" circle v-permission="perm('deleteRow')" @click="removeNode(idx)" />
              </div>
              <div class="node-approvers">
                <div class="approvers-label">{{ $tt("候选审批人（支持搜索，关键字可匹配姓名/工号/部门/岗位）:") }}</div>
                <div class="approvers-list">
                  <el-tag v-for="(a, ai) in node.approvers" :key="ai" closable
                          @close="removeApprover(idx, ai)" class="approver-tag">
                    <span class="approver-tag-name">{{ a.approverName }}</span>
                    <span v-if="a.employeeCode" class="approver-tag-sub">{{ a.employeeCode }}</span>
                    <span v-if="a.departmentName" class="approver-tag-sub">{{ a.departmentName }}</span>
                  </el-tag>
                  <el-select
                    multiple
                    filterable
                    remote
                    reserve-keyword
                    :placeholder="$tt('搜索员工并选择审批人')"
                    :remote-method="(kw) => remoteSearchApprovers(idx, kw)"
                    :loading="approverSearchLoading[idx] || false"
                    :options="approverOptions[idx] || []"
                    value-key="label"
                    :model-value="nodeApproverSelectedLabels(node)"
                    @update:model-value="(vals) => onApproversSelected(idx, vals)"
                    @focus="onApproverSelectFocus(idx)"
                    style="min-width: 280px; flex: 1 1 280px;"
                    popper-class="approver-select-popper"
                  >
                    <el-option
                      v-for="opt in approverOptions[idx] || []"
                      :key="opt.label"
                      :label="$tf(opt.label)"
                      :value="opt.value"
                    >
                      <div class="approver-opt-row">
                        <span class="approver-opt-name">{{ opt.raw.employeeName }}</span>
                        <span v-if="opt.raw.employeeCode" class="approver-opt-sub">{{ opt.raw.employeeCode }}</span>
                        <span v-if="opt.raw.departmentName" class="approver-opt-sub">{{ opt.raw.departmentName }}</span>
                        <span v-if="opt.raw.positionName" class="approver-opt-sub">{{ opt.raw.positionName }}</span>
                      </div>
                    </el-option>
                  </el-select>
                </div>
                <div class="approver-tip" v-if="node.approverMode === 'any'">
                  提示：任选一人模式，运行时由提交人从候选中选择一个审批人
                </div>
                <div class="approver-tip" v-else-if="node.approverMode === 'sequence'">
                  提示：依次审批模式，所有候选人按顺序都需要审批
                </div>
              </div>
              <div class="node-approvers" v-if="node.approverMode === 'auto'">
                <div class="approver-tip">
                  提示：系统自动通过节点，无需人工审批。流程路由条件命中该流程时单据直接放行生效（如配置条件「客户付款管控分类 = 现结」即可实现现结免审）
                </div>
              </div>
            </div>
            <el-empty v-if="formData.nodes.length === 0" :description="$tt('暂无节点，请点击「添加节点」创建')" />
          </div>
        </div>
      </div>

      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="dialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('save')" :loading="saving" @click="handleSave">{{ $tt("保存") }}</el-button>
      </template>
    </el-dialog>

    <!-- 条件模板弹窗 -->
    <el-dialog v-model="conditionDialogVisible" :title="$tf('条件审批模板')" width="800px" append-to-body>
      <div class="conditions-wrap">
        <div class="conditions-tip">
          条件审批：根据单据字段值决定起始审批节点。例如：金额 ≥ 50000 直接走总经理审批节点
        </div>
        <el-table :data="conditions" border style="width: 100%">
          <el-table-column :label="$tf('字段')" width="160">
            <template #default="{ row }">
              <el-select v-model="row.fieldKey" :placeholder="$tt('选择字段')" size="small">
                <el-option :label="$tf('金额(totalAmount)')" value="totalAmount" />
                <el-option :label="$tf('净金额(netAmount)')" value="netAmount" />
                <el-option :label="$tf('税额(taxAmount)')" value="taxAmount" />
                <el-option :label="$tf('折扣金额(discountAmount)')" value="discountAmount" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column :label="$tf('操作符')" width="120">
            <template #default="{ row }">
              <el-select v-model="row.operator" :placeholder="$tt('操作符')" size="small">
                <el-option label=">=" value=">=" />
                <el-option label=">" value=">" />
                <el-option label="<=" value="<=" />
                <el-option label="<" value="<" />
                <el-option label="==" value="==" />
                <el-option label="between" value="between" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column :label="$tf('值1')" width="130">
            <template #default="{ row }">
              <el-input-number v-model="row.valueLow" :precision="2" size="small" controls-position="right" />
            </template>
          </el-table-column>
          <el-table-column :label="$tf('值2')" width="130">
            <template #default="{ row }">
              <el-input-number v-model="row.valueHigh" :precision="2" size="small" controls-position="right"
                               :disabled="row.operator !== 'between'" />
            </template>
          </el-table-column>
          <el-table-column :label="$tf('跳转到节点序号')" width="160">
            <template #default="{ row }">
              <el-input-number v-model="row.targetNodeOrder" :min="1" :max="20" size="small"
                               controls-position="right" />
            </template>
          </el-table-column>
          <el-table-column :label="$tf('操作')" width="80">
            <template #default="{ $index }">
              <el-button link type="danger" v-permission="perm('deleteRow')" @click="conditions.splice($index, 1)">{{ $tt("删除") }}</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button type="primary" plain :icon="Plus" size="small" v-permission="perm('addRow')" @click="conditions.push({})"
                  style="margin-top: 10px;">{{ $tt("添加条件") }}</el-button>
      </div>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="conditionDialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('save')" @click="saveConditions">{{ $tt("保存条件") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search, RefreshRight, Delete, ArrowUp, ArrowDown } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { useAutoDocCode } from '@/composables/useAutoDocCode'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('system:approval')

// ============ 数据 ============
const loading = ref(false)
const saving = ref(false)
const tableData = ref([])
const billTypes = ref([])
const approverOptions = reactive({})      // nodeIdx => [{ label, value, raw: {...emp} }]
const approverSearchLoading = reactive({}) // nodeIdx => boolean

const { docCodeDisabled, autoFillDocCodeOnCreate } = useAutoDocCode('WF_APPROVAL_FLOW')

const searchForm = reactive({ billType: '', keyword: '' })
const formData = reactive({
  flowId: null, flowCode: '', flowName: '', billType: '', enabled: 1,
  description: '', priority: 99, conditionExpr: '', nodes: []
})
// 流程路由条件（通用规则编辑器：任意表头字段 + 自定义字段，自动合成到 conditionExpr）
const routeRules = ref([])            // [{ field, op, value }]
const conditionFields = ref([])       // 后端 condition-fields 接口返回的字段元数据
const conditionFieldsLoading = ref(false)
const standardFields = computed(() => conditionFields.value.filter(f => !f.custom))
const customFields = computed(() => conditionFields.value.filter(f => f.custom))
// 运算符（与后端 ApprovalFlowService.matchSingleRule 一一对应）
const OP_LABELS = {
  eq: '等于', ne: '不等于', contains: '包含',
  gt: '大于', ge: '大于等于', lt: '小于', le: '小于等于'
}
const conditionPreview = computed(() => buildConditionExpr())
const isEdit = ref(false)
const dialogVisible = ref(false)

// 审批人选择
// （节点卡片内行内 ElSelect 远程搜索多选，不再弹对话框）

// 条件模板
const conditionDialogVisible = ref(false)
const conditions = ref([])
const currentConditionFlowId = ref(null)

// ============ API ============
const api = {
  listFlows: (params) => request.get('/approval/flows', { params }),
  getFlow: (id) => request.get(`/approval/flows/${id}`),
  saveFlow: (data) => request.post('/approval/flows', data),
  updateFlow: (id, data) => request.put(`/approval/flows/${id}`, data),
  setEnabled: (id, enabled) => request.put(`/approval/flows/${id}/enabled`, null, { params: { enabled } }),
  saveConditions: (id, data) => request.post(`/approval/flows/${id}/conditions`, data),
  listBillTypes: () => request.get('/approval/bill-types'),
  listEmployeeOptions: (params) => request.get('/basedata/employee/select-options', { params })
}

// ============ 方法 ============
async function loadData() {
  loading.value = true
  try {
    const res = await api.listFlows({ billType: searchForm.billType, keyword: searchForm.keyword })
    // 拦截器已解包：res 即数组本体
    const list = Array.isArray(res) ? res : (res?.data || [])
    tableData.value = list.map(f => ({ ...f, nodeCount: f.nodeCount || 0 }))
  } catch (e) {
    ElMessage.error(tt('加载流程列表失败'))
  } finally {
    loading.value = false
  }
}

async function loadBillTypes() {
  try {
    const res = await api.listBillTypes()
    // 拦截器已解包：res 即数组本体
    billTypes.value = Array.isArray(res) ? res : (res?.data || [])
  } catch {}
}

function getBillTypeName(billType) {
  const t = billTypes.value.find(b => b.billType === billType)
  return t ? t.billName : billType
}

function formatTime(t) {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 16)
}

function handleReset() {
  searchForm.billType = ''
  searchForm.keyword = ''
  loadData()
}

async function handleAdd() {
  isEdit.value = false
  Object.assign(formData, {
    flowId: null, flowCode: '', flowName: '', billType: '', enabled: 1,
    description: '', priority: 99, conditionExpr: '', nodes: []
  })
  parseConditionExpr('')
  dialogVisible.value = true
  await autoFillDocCodeOnCreate(formData, 'flowCode')
}

async function handleToggleEnabled(row) {
  try {
    await api.setEnabled(row.flowId, row.enabled)
    ElMessage.success(row.enabled === 1 ? tt('已启用') : tt('已停用'))
  } catch {
    row.enabled = row.enabled === 1 ? 0 : 1
  }
}

// ============ 节点操作 ============
function addNode() {
  const order = formData.nodes.length + 1
  formData.nodes.push({
    nodeId: null, nodeName: '', nodeOrder: order, approverMode: 'single',
    approvers: []
  })
}

function removeNode(idx) {
  formData.nodes.splice(idx, 1)
  formData.nodes.forEach((n, i) => n.nodeOrder = i + 1)
}

function moveUp(idx) {
  if (idx === 0) return
  const tmp = formData.nodes[idx - 1]
  formData.nodes[idx - 1] = formData.nodes[idx]
  formData.nodes[idx] = tmp
  formData.nodes.forEach((n, i) => n.nodeOrder = i + 1)
}

function moveDown(idx) {
  if (idx === formData.nodes.length - 1) return
  const tmp = formData.nodes[idx + 1]
  formData.nodes[idx + 1] = formData.nodes[idx]
  formData.nodes[idx] = tmp
  formData.nodes.forEach((n, i) => n.nodeOrder = i + 1)
}

// ============ 审批人选择（行内可搜索下拉 + 远程搜索） ============
/**
 * 后端 /select-options 返回轻量员工；我们统一用 employeeName 作为唯一键（运行时匹配也是用姓名）
 * 若员工重名，再拼工号后缀确保不冲突
 */
function safeApproverKey(emp) {
  if (!emp) return ''
  const name = emp.employeeName || ''
  const code = emp.employeeCode || ''
  return code ? `${name}（${code}）` : name
}

function ensureApproverOptionsArray(nodeIdx) {
  if (!Array.isArray(approverOptions[nodeIdx])) approverOptions[nodeIdx] = []
  return approverOptions[nodeIdx]
}

/**
 * 节点打开后首次聚焦搜索框时，把当前已经选上的审批人补充进选项里（保证下拉里能看到已选的）
 */
function seedExistingApproversIntoOptions(nodeIdx) {
  const node = formData.nodes[nodeIdx]
  if (!node || !Array.isArray(node.approvers)) return
  const arr = ensureApproverOptionsArray(nodeIdx)
  const existing = new Set(arr.map(o => o.value))
  for (const a of node.approvers) {
    const key = a.approverName || ''
    if (!key || existing.has(key)) continue
    arr.push({
      label: buildOptionLabel(a),
      value: key,
      raw: {
        employeeName: a.approverName,
        employeeCode: a.employeeCode || '',
        employeeId: a.employeeId,
        userId: a.userId,
        departmentName: a.departmentName || '',
        positionName: a.positionName || ''
      }
    })
    existing.add(key)
  }
}

function buildOptionLabel(raw) {
  const parts = []
  if (raw.employeeName) parts.push(raw.employeeName)
  if (raw.employeeCode) parts.push(raw.employeeCode)
  if (raw.departmentName) parts.push(raw.departmentName)
  return parts.join(' · ')
}

let _lastRemoteReqId = 0
/** 节点 idx 是否已首次预加载过下拉候选（保证点开下拉直接看到员工列表，而不是只有搜索） */
const approverHasDefaultLoaded = reactive({})

/** 首次聚焦时预加载前 50 个在职员工（后端 keyword 为空返回全量前 50） */
async function onApproverSelectFocus(nodeIdx) {
  if (!approverHasDefaultLoaded[nodeIdx]) {
    await remoteSearchApprovers(nodeIdx, '')
  }
}

async function remoteSearchApprovers(nodeIdx, kw) {
  const idx = nodeIdx
  approverSearchLoading[idx] = true
  const reqId = ++_lastRemoteReqId
  try {
    const res = await api.listEmployeeOptions({ keyword: (kw || '').trim() || undefined })
    if (reqId !== _lastRemoteReqId) return
    // 拦截器已解包：res 即数组本体
    const list = Array.isArray(res) ? res : (Array.isArray(res?.data) ? res.data : [])
    const arr = ensureApproverOptionsArray(idx)
    arr.splice(0, arr.length)
    for (const e of list) {
      const name = String(e.employeeName || '').trim()
      if (!name) continue
      const empRaw = {
        employeeId: e.employeeId,
        userId: e.userId,
        employeeName: name,
        employeeCode: e.employeeCode || '',
        departmentName: e.departmentName || '',
        positionName: e.positionName || ''
      }
      arr.push({
        label: buildOptionLabel(empRaw),
        value: name,
        raw: empRaw
      })
    }
    seedExistingApproversIntoOptions(idx)
    approverHasDefaultLoaded[idx] = true
  } catch (e) {
    console.warn('[ApprovalFlow] 搜索员工失败', e)
  } finally {
    if (reqId === _lastRemoteReqId) approverSearchLoading[idx] = false
  }
}

function nodeApproverSelectedLabels(node) {
  if (!node || !Array.isArray(node.approvers)) return []
  return node.approvers.map(a => a.approverName).filter(Boolean)
}

/**
 * 用户在下拉勾选/取消时同步到 node.approvers
 */
function onApproversSelected(nodeIdx, values) {
  const node = formData.nodes[nodeIdx]
  if (!node) return
  const selectedNames = new Set((values || []).map(v => String(v || '').trim()).filter(Boolean))
  const options = ensureApproverOptionsArray(nodeIdx)
  const rawByKey = new Map(options.map(o => [String(o.value || '').trim(), o.raw]))

  const newApprovers = []
  // 保留已存在并仍在选中里的项
  for (const a of (node.approvers || [])) {
    const name = String(a.approverName || '').trim()
    if (selectedNames.has(name)) {
      newApprovers.push(a)
      selectedNames.delete(name)
    }
  }
  // 新增项（用搜索选项的 raw）
  for (const name of selectedNames) {
    const raw = rawByKey.get(name)
    if (!raw) continue
    newApprovers.push({
      userId: raw.userId,
      employeeId: raw.employeeId,
      approverName: raw.employeeName,
      employeeCode: raw.employeeCode,
      departmentName: raw.departmentName,
      positionName: raw.positionName
    })
  }
  node.approvers = newApprovers
}

function removeApprover(nodeIdx, approverIdx) {
  const node = formData.nodes[nodeIdx]
  if (!node) return
  node.approvers.splice(approverIdx, 1)
}

// ============ 流程路由条件（conditionExpr 可视化合成/解析：通用规则） ============
/**
 * 加载指定单据类型的条件字段元数据（表头字段 + 自定义字段）
 */
async function loadConditionFields(billType) {
  conditionFields.value = []
  if (!billType) return
  conditionFieldsLoading.value = true
  try {
    const res = await request.get(`/approval/bill-types/${billType}/condition-fields`)
    // 拦截器已解包：res 即数组本体
    conditionFields.value = Array.isArray(res) ? res : (res?.data || [])
  } catch (e) {
    console.warn('[ApprovalFlow] 加载条件字段失败', e)
  } finally {
    conditionFieldsLoading.value = false
  }
}

/** 规则行当前选中的字段元数据 */
function ruleField(row) {
  if (!row || !row.field) return null
  return conditionFields.value.find(f => f.fieldKey === row.field) || null
}

/** 添加一条条件规则 */
function addRouteRule() {
  routeRules.value.push({ field: '', op: 'eq', value: null })
}

/** 切换字段后清空已填的值（不同字段类型值控件不同） */
function onRuleFieldChange(row) {
  row.value = null
}

/**
 * 把规则列表合成 conditionExpr JSON 字符串（与后端 matchCondition 新格式对应）
 * 例：{"rules":[{"field":"totalAmount","op":"ge","value":50000}]}
 * 无任何条件时返回空字符串（流程作为兜底）
 */
function buildConditionExpr() {
  const rules = routeRules.value
    .filter(r => r.field && r.op && r.value !== null && r.value !== undefined && r.value !== '')
    .map(r => ({ field: r.field, op: r.op, value: r.value }))
  return rules.length ? JSON.stringify({ rules }) : ''
}

/** 打开编辑时把 conditionExpr 解析回填到规则列表（兼容旧格式自动迁移） */
function parseConditionExpr(expr) {
  routeRules.value = []
  if (!expr) return
  try {
    const obj = JSON.parse(expr)
    if (Array.isArray(obj.rules)) {
      // 新格式
      routeRules.value = obj.rules
        .filter(r => r && r.field)
        .map(r => ({ field: String(r.field), op: OP_LABELS[r.op] ? r.op : 'eq', value: r.value ?? null }))
    } else {
      // 旧格式迁移：payment_category → eq；min_amount → ge；max_amount → le；其余 key → eq
      const migrated = []
      if (obj.payment_category != null) migrated.push({ field: 'paymentCategory', op: 'eq', value: obj.payment_category })
      if (obj.min_amount != null) migrated.push({ field: 'totalAmount', op: 'ge', value: Number(obj.min_amount) })
      if (obj.max_amount != null) migrated.push({ field: 'totalAmount', op: 'le', value: Number(obj.max_amount) })
      for (const [k, v] of Object.entries(obj)) {
        if (['payment_category', 'min_amount', 'max_amount'].includes(k)) continue
        migrated.push({ field: k, op: 'eq', value: v })
      }
      routeRules.value = migrated
    }
  } catch {
    // 非 JSON（旧数据/手填），保留原样不解析
  }
}

/** 列表「触发条件」列的可读化展示 */
function formatConditionExpr(expr) {
  if (!expr) return ''
  try {
    const obj = JSON.parse(expr)
    if (Array.isArray(obj.rules)) {
      return obj.rules.map(r => `${r.field} ${OP_LABELS[r.op] || r.op} ${r.value ?? '空'}`).join(' 且 ')
    }
    return Object.entries(obj).map(([k, v]) => `${k}=${v}`).join(' 且 ')
  } catch {
    return expr
  }
}

// ============ 条件模板 ============
async function saveConditions() {
  try {
    await api.saveConditions(currentConditionFlowId.value, conditions.value)
    ElMessage.success(tt('条件保存成功'))
    conditionDialogVisible.value = false
  } catch {
    ElMessage.error(tt('保存失败'))
  }
}

// ============ 保存 ============
async function handleSave() {
  if (!formData.flowName) { ElMessage.warning(tt('请输入流程名称')); return }
  if (!formData.billType) { ElMessage.warning(tt('请选择单据类型')); return }
  if (!formData.nodes || formData.nodes.length === 0) {
    ElMessage.warning(tt('请至少添加一个节点'))
    return
  }
  for (const n of formData.nodes) {
    if (!n.nodeName) { ElMessage.warning(tt('请填写所有节点名称')); return }
    // 系统自动通过(auto)节点无需审批人
    if (n.approverMode !== 'auto' && (!n.approvers || n.approvers.length === 0)) {
      ElMessage.warning(`${tt("节点\"")}${n.nodeName}${tt("\"未配置审批人")}`)
      return
    }
  }
  saving.value = true
  try {
    // 合成流程路由条件
    formData.conditionExpr = buildConditionExpr()
    if (formData.priority == null) formData.priority = 99
    if (isEdit.value) {
      await api.updateFlow(formData.flowId, formData)
    } else {
      await api.saveFlow(formData)
    }
    ElMessage.success(isEdit.value ? tt('更新成功') : tt('创建成功'))
    dialogVisible.value = false
    loadData()
    // 通知父组件刷新（避免前端页面割裂）
    window.dispatchEvent(new CustomEvent('approval-flow-saved'))
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || tt('保存失败'))
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadData()
  loadBillTypes()
})
// 单据类型变化时加载该单据的条件字段元数据（弹窗内选字段用）
watch(() => formData.billType, (bt) => { loadConditionFields(bt) })
useAutoRefresh(() => {
  loadData()
  loadBillTypes()
})

// 暴露方法供父组件直接调用（避免前端页面割裂）
async function openEditorForFlow(flowId) {
  await loadBillTypes()
  if (flowId) {
    // 编辑已有流程
    isEdit.value = true
    try {
      const res = await api.getFlow(flowId)
      // 拦截器已解包：res 即流程对象本体
      Object.assign(formData, res?.data || res || {})
      if (!formData.nodes) formData.nodes = []
      if (formData.priority == null) formData.priority = 99
      if (!formData.conditionExpr) formData.conditionExpr = ''
      parseConditionExpr(formData.conditionExpr)
      dialogVisible.value = true
    } catch {
      ElMessage.error(tt('获取流程详情失败'))
    }
  } else {
    ElMessage.warning(tt('请先创建审批流程'))
  }
}

async function openEditorForNewFlow(billType) {
  await loadBillTypes()
  isEdit.value = false
  Object.assign(formData, {
    flowId: null, flowCode: '', flowName: '', billType: billType || '', enabled: 1,
    description: '', priority: 99, conditionExpr: '', nodes: []
  })
  parseConditionExpr('')
  dialogVisible.value = true
  await autoFillDocCodeOnCreate(formData, 'flowCode')
}

defineExpose({
  openEditorForFlow,
  openEditorForNewFlow,
  dialogVisible
})
</script>

<style scoped lang="scss">
.approval-flow-page {
  padding: 0;
}
.card-header {
  display: flex; justify-content: space-between; align-items: center;
  .header-left { display: flex; align-items: center; gap: 12px; }
  .title { font-size: 16px; font-weight: 600; color: #303133; }
}
.erp-search-bar {
  display: flex; gap: 12px; align-items: center; flex-wrap: wrap; margin-bottom: 16px;
  .erp-search-item { display: flex; align-items: center; gap: 6px;
    .label { color: #606266; font-size: 13px; }
  }
}
.flow-edit-wrap {
  display: flex; flex-direction: column; gap: 16px;
}
.flow-info-bar {
  background: #f5f7fa; padding: 12px; border-radius: 6px;
  border: 1px solid #ebeef5;
  .form-tip { margin-left: 8px; font-size: 12px; color: #909399; }
}
.route-condition-bar {
  background: #fff; border: 1px solid #ebeef5; border-radius: 6px; padding: 12px;
  .route-header {
    display: flex; align-items: center; gap: 12px; margin-bottom: 8px;
    .title { font-size: 14px; font-weight: 600; color: #303133; }
  }
  .route-tip {
    font-size: 12px; color: #909399; line-height: 1.6; margin-bottom: 12px;
    background: #f5f7fa; padding: 8px 10px; border-radius: 4px;
  }
  .route-form { margin-bottom: 0; }
  .route-warn { background: #fdf6ec; color: #e6a23c; }
  .route-add-btn { margin-left: auto; }
  .route-preview {
    margin-top: 10px; padding: 8px 10px; background: #ecf5ff; border-radius: 4px;
    display: flex; align-items: center; gap: 8px;
    .preview-label { font-size: 12px; color: #606266; }
    code { font-size: 12px; color: #e6a23c; word-break: break-all; }
  }
}
.nodes-editor {
  background: #fff; border: 1px solid #ebeef5; border-radius: 6px; padding: 12px;
}
.nodes-header {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;
  .title { font-size: 14px; font-weight: 600; color: #303133; }
}
.nodes-list {
  display: flex; flex-direction: column; gap: 12px;
  max-height: 60vh; overflow-y: auto; padding-right: 4px;
}
.node-card {
  background: #fafbfc; border: 1px solid #ebeef5; border-radius: 6px; padding: 12px;
  &:hover { box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
}
.node-header {
  display: flex; align-items: center; gap: 8px; margin-bottom: 10px;
  .node-index {
    width: 28px; height: 28px; background: #409eff; color: #fff;
    border-radius: 50%; display: flex; align-items: center; justify-content: center;
    font-weight: 600; font-size: 13px;
  }
  .node-order {
    font-size: 12px; color: #909399; margin-left: 8px;
  }
}
.node-approvers {
  background: #fff; padding: 10px; border-radius: 4px; border: 1px dashed #dcdfe6;
  .approvers-label { font-size: 12px; color: #909399; margin-bottom: 6px; }
  .approvers-list {
    display: flex; flex-wrap: wrap; gap: 6px; align-items: center;
  }
  .approver-tag {
    margin: 0;
    display: inline-flex; align-items: center; gap: 4px;
    :deep(.el-tag__content) { display: inline-flex; align-items: center; gap: 6px; flex-wrap: wrap; }
    .approver-tag-name { font-weight: 500; color: #303133; }
    .approver-tag-sub  { font-size: 11px; color: #606266; opacity: 0.85; }
  }
  .approver-tip {
    font-size: 12px; color: #67c23a; margin-top: 6px; padding: 4px 8px;
    background: #f0f9eb; border-radius: 4px;
  }
}
.approver-picker {
  .picker-search { margin-bottom: 12px; }
}
/* 审批人下拉选项（全局 popper 样式，必须用非 scoped 类名：popper-class="approver-select-popper"） */
:deep(.approver-select-popper) {
  .approver-opt-row {
    display: flex; flex-wrap: wrap; align-items: center; gap: 8px; line-height: 1.4;
    .approver-opt-name { color: #303133; font-weight: 500; font-size: 13px; }
    .approver-opt-sub  { color: #909399; font-size: 12px; }
  }
}
.approver-opt-row {
  display: flex; flex-wrap: wrap; align-items: center; gap: 8px; line-height: 1.4;
  .approver-opt-name { color: #303133; font-weight: 500; font-size: 13px; }
  .approver-opt-sub  { color: #909399; font-size: 12px; }
}
.conditions-wrap {
  .conditions-tip {
    background: #ecf5ff; color: #409eff; padding: 10px; border-radius: 4px;
    font-size: 13px; margin-bottom: 12px;
  }
}
</style>
