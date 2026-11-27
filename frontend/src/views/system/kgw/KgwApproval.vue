<template>
  <div class="kgw-approval">
    <!-- 顶部工具栏 + 子菜单切换 -->
    <div class="kgw-toolbar">
      <div class="kgw-sub-tabs">
        <div
          v-for="tab in subTabs"
          :key="tab.key"
          class="kgw-sub-tab"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key"
        >
          <el-icon class="tab-icon"><component :is="tab.icon" /></el-icon>
          <span>{{ $tf(tab.label) }}</span>
          <el-badge v-if="tab.badge && tab.badge.value > 0" :value="tab.badge.value" type="danger" class="tab-badge" />
        </div>
      </div>
      <div class="kgw-actions">
        <el-button v-permission="perm('refresh')" size="small" @click="refreshAll">{{ $tt("刷新") }}</el-button>
      </div>
    </div>

    <!-- ====== 子页面：单据审批开关 ====== -->
    <div v-show="activeTab === 'switch'" class="kgw-body">
      <!-- 顶部：全局审批总开关 -->
      <div class="kgw-global-switch">
        <div class="global-switch-left">
          <div class="global-switch-title">
            <el-icon class="gs-icon"><Switch /></el-icon>
            <span>{{ $tt("全局审批总开关") }}</span>
            <el-tag size="small" type="warning" effect="plain" style="margin-left:8px">{{ $tt("最高优先级") }}</el-tag>
          </div>
          <div class="global-switch-desc">
            关闭后：所有单据提交将直接生效（自动到「已生效」），不进入任何审批流程，单据级开关不再起作用。<br>
            开启后：按每个单据的开关决定是否需要审批（开关 OFF 的单据仍然免审批）。
          </div>
        </div>
        <div class="global-switch-right">
          <el-switch
            v-model="globalApprovalEnabled"
            :loading="globalApprovalLoading"
            :inline-prompt="true"
            :active-text="$tt('开')"
            :inactive-text="$tt('关')"
            style="--el-switch-on-color:#67C23A; --el-switch-off-color:#C0C4CC"
            @change="onToggleGlobal"
          />
        </div>
      </div>

      <div class="kgw-layout-split">
        <!-- 左侧：可展开的业务模块 -->
        <div class="kgw-side">
          <el-input v-model="catKeyword" :placeholder="$tt('输入关键字搜索')" size="small" clearable class="kgw-side-search" />
          <div class="kgw-side-list">
            <div v-for="g in billTree" :key="g.key" class="kgw-side-group">
              <div class="kgw-side-group-title" @click="toggleGroup(g.key)">
                <el-icon class="arrow" :class="{ expanded: g.expanded }"><ArrowRight /></el-icon>
                <span>{{ $tf(g.label) }}</span>
                <el-tag size="small" type="info" class="count-tag">{{ g.items.length }}</el-tag>
              </div>
              <div v-show="g.expanded" class="kgw-side-children">
                <div
                  v-for="item in g.items"
                  :key="item.billType"
                  class="kgw-side-item"
                  :class="{ active: currentBillType === item.billType }"
                  @click="selectBill(item)"
                >
                  <span>{{ item.billName }}</span>
                  <el-tag v-if="!globalApprovalEnabled" size="small" type="info" effect="plain">{{ $tt("全局关") }}</el-tag>
                  <el-tag v-else-if="item.enabled" type="success" size="small" effect="plain">{{ $tt("已启用") }}</el-tag>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 右侧：当前单据审批配置 -->
        <div class="kgw-main">
          <div v-if="currentBill" class="bill-config-panel">
            <div class="panel-header">
              <h3>{{ currentBill.billName }} 审批设置</h3>
              <div class="header-actions">
                <span class="switch-label">{{ globalApprovalEnabled ? '是否需要审批：' : '全局审批总开关已关闭：' }}</span>
                <el-switch
                  v-model="currentBill.enabled"
                  @change="onToggleBill(currentBill)"
                  :loading="billSwitching"
                  :disabled="!globalApprovalEnabled"
                />
              </div>
            </div>

            <el-alert v-if="!globalApprovalEnabled" type="warning" :closable="false" show-icon style="margin-bottom:12px">
              当前「全局审批总开关」为关闭状态：<strong>{{ $tt("所有单据提交后直接生效，无需人工审批") }}</strong>，此处单据级开关不生效。
            </el-alert>
            <el-alert v-else-if="!currentBill.enabled" type="info" :closable="false" show-icon>
              当前单据未启用审批。开启后，提交此单据时将进入审批流程。
            </el-alert>

            <div v-else class="bill-flow-info">
              <el-empty v-if="!currentBill.flowId" :description="$tt('尚未配置审批流程')">
                <el-button v-permission="perm('add')" type="primary" @click="goToFlowEditor(null)">{{ $tt("新建审批流程") }}</el-button>
              </el-empty>
              <div v-else class="flow-summary">
                <el-descriptions :column="2" border size="small">
                  <el-descriptions-item :label="$tf('审批流程')">{{ currentBill.flowName || '—' }}</el-descriptions-item>
                  <el-descriptions-item :label="$tf('流程编码')">{{ currentBill.flowCode || '—' }}</el-descriptions-item>
                  <el-descriptions-item :label="$tf('节点数量')">{{ currentBill.nodeCount || 0 }} 级</el-descriptions-item>
                  <el-descriptions-item :label="$tf('条件模板')">{{ currentBill.hasCondition ? '已配置' : '未配置' }}</el-descriptions-item>
                </el-descriptions>
                <el-button v-permission="perm('edit')" type="primary" size="small" plain @click="goToFlowEditor(currentBill.flowId)">
                  编辑审批流程
                </el-button>
              </div>
            </div>
          </div>
          <el-empty v-else :description="$tt('请从左侧选择单据类型')" />
        </div>
      </div>
    </div>

    <!-- ====== 内联：审批流程编辑弹窗 ====== -->
    <el-dialog v-model="flowDialogVisible" :title="flowIsEdit ? '编辑审批流程' : '新建审批流程'" width="95%" top="2vh"
               :close-on-click-modal="false" destroy-on-close>
      <div class="flow-edit-wrap">
        <!-- 基础信息 -->
        <div class="flow-info-bar">
          <el-form :model="flowFormData" label-width="90px" inline>
            <el-form-item :label="$tf('流程编码')">
              <el-input v-model="flowFormData.flowCode" :placeholder="$tt('如 sale_order_flow')" style="width: 200px" :disabled="flowDocCodeDisabled" />
            </el-form-item>
            <el-form-item :label="$tf('流程名称')" required>
              <el-input v-model="flowFormData.flowName" :placeholder="$tt('如 销售订单审批流程')" style="width: 220px" />
            </el-form-item>
            <el-form-item :label="$tf('单据类型')" required>
              <el-select v-model="flowFormData.billType" :placeholder="$tt('选择单据类型')" style="width: 160px">
                <el-option v-for="t in flowBillTypes" :key="t.billType" :label="t.billName" :value="t.billType" />
              </el-select>
            </el-form-item>
            <el-form-item :label="$tf('启用')">
              <el-switch v-model="flowFormData.enabled" :active-value="1" :inactive-value="0" />
            </el-form-item>
            <el-form-item :label="$tf('描述')">
              <el-input v-model="flowFormData.description" :placeholder="$tt('流程描述')" style="width: 260px" />
            </el-form-item>
          </el-form>
        </div>

        <!-- 抄送人：审批通过/驳回后，这些同事 + 发起人 自动收到小铃铛通知 -->
        <div class="notify-users-bar">
          <div class="notify-title">
            <el-icon><Bell /></el-icon>
            <span>{{ $tt("审批结果通知人（抄送人）") }}</span>
            <el-tag size="small" type="info" effect="plain" class="notify-hint">{{ $tt("发起人自动接收，无需手动添加") }}</el-tag>
          </div>
          <div class="notify-body">
            <el-tag
              v-for="(u, ui) in (flowFormData.notifyUsers || [])"
              :key="ui"
              closable
              @close="removeNotifyUser(ui)"
              class="approver-tag">
              <span class="approver-tag-name">{{ u.approverName }}</span>
              <span v-if="u.employeeCode" class="approver-tag-sub">{{ u.employeeCode }}</span>
              <span v-if="u.departmentName" class="approver-tag-sub">{{ u.departmentName }}</span>
            </el-tag>
            <el-select
              multiple
              filterable
              remote
              reserve-keyword
              :placeholder="$tt('搜索员工，选择审批结束后要通知的同事（支持多选）')"
              :remote-method="(kw) => remoteSearchApprovers('-notify', kw)"
              :loading="(approverSearchLoading['-notify'] || false)"
              value-key="value"
              :model-value="notifyUserSelectedKeys()"
              @update:model-value="onNotifyUsersSelected"
              @focus="() => onApproverSelectFocus('-notify')"
              style="min-width: 400px; flex: 1 1 400px;"
              popper-class="kgw-approver-select-popper"
            >
              <el-option
                v-for="opt in (approverOptions['-notify'] || [])"
                :key="opt.value"
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
        </div>

        <!-- 节点编辑器 -->
        <div class="nodes-editor">
          <div class="nodes-header">
            <span class="title">{{ $tt("审批节点（按顺序流转，支持无限级）") }}</span>
            <el-button v-permission="perm('addRow')" type="primary" :icon="Plus" size="small" @click="addFlowNode">{{ $tt("添加节点") }}</el-button>
          </div>
          <div class="nodes-list">
            <div v-for="(node, idx) in flowFormData.nodes" :key="idx" class="node-card">
              <div class="node-header">
                <span class="node-index">{{ idx + 1 }}</span>
                <el-input v-model="node.nodeName" :placeholder="$tt('节点名称，如 部门经理审批')" style="width: 240px" />
                <el-select v-model="node.approverMode" :placeholder="$tt('审批模式')" style="width: 160px">
                  <el-option :label="$tf('单人审批')" value="single" />
                  <el-option :label="$tf('任选一人')" value="any" />
                  <el-option :label="$tf('全部审批')" value="all" />
                  <el-option :label="$tf('依次审批')" value="sequence" />
                  <el-option :label="$tf('系统自动通过')" value="auto" />
                </el-select>
                <span class="node-order">顺序: {{ node.nodeOrder }}</span>
                <el-button :icon="ArrowUp" size="small" circle @click="moveNodeUp(idx)" :disabled="idx === 0" />
                <el-button :icon="ArrowDown" size="small" circle @click="moveNodeDown(idx)"
                           :disabled="idx === flowFormData.nodes.length - 1" />
                <el-button v-permission="perm('deleteRow')" type="danger" :icon="Delete" size="small" circle @click="removeFlowNode(idx)" />
              </div>
              <div class="node-approvers" v-if="node.approverMode !== 'auto'">
                <div class="approvers-label">{{ $tt("候选审批人（支持搜索，关键字可匹配姓名/工号/部门/岗位）:") }}</div>
                <div class="approvers-list">
                  <el-tag v-for="(a, ai) in node.approvers" :key="ai" closable
                          @close="removeNodeApprover(idx, ai)" class="approver-tag">
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
                    :loading="(approverSearchLoading[idx] || false)"
                    value-key="value"
                    :model-value="nodeApproverSelectedKeys(idx)"
                    @update:model-value="(vals) => onApproversSelected(idx, vals)"
                    @focus="onApproverSelectFocus(idx)"
                    style="min-width: 300px; flex: 1 1 300px;"
                    popper-class="kgw-approver-select-popper"
                  >
                    <el-option
                      v-for="opt in (approverOptions[idx] || [])"
                      :key="opt.value"
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
            <el-empty v-if="flowFormData.nodes.length === 0" :description="$tt('暂无节点，请点击「添加节点」创建')" />
          </div>
        </div>
      </div>

      <template #footer>
        <div style="display:flex;justify-content:space-between;align-items:center;width:100%">
          <div style="display:flex;gap:8px">
            <el-button v-permission="perm('view')" type="warning" plain @click="openConditionTemplate">{{ $tt("条件模板") }}</el-button>
            <el-button v-permission="perm('delete')" v-if="flowIsEdit && flowFormData.flowId" type="danger" plain :icon="Delete"
                       @click="handleDeleteFlow">{{ $tt("删除流程") }}</el-button>
          </div>
          <div>
            <el-button v-permission="perm('dlgCancel')" @click="flowDialogVisible = false">{{ $tt("取消") }}</el-button>
            <el-button v-permission="perm('save')" type="primary" :loading="flowSaving" @click="handleSaveFlow">{{ $tt("保存") }}</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- 条件模板弹窗（通用字段编辑：表头全部字段 + 自定义字段，按类型切换操作符/值控件） -->
    <el-dialog v-model="conditionDialogVisible" :title="$tf('条件审批模板')" width="960px" append-to-body>
      <div class="conditions-wrap">
        <div class="conditions-tip">
          条件审批：根据单据表头字段值决定起始审批节点，可用全部表头字段与自定义字段。
          例如：金额 ≥ 50000 直接走总经理审批节点；「联系人」等于「张三」从财务节点开始审批。
        </div>
        <el-table :data="flowConditions" border style="width: 100%">
          <el-table-column :label="$tf('字段')" min-width="220">
            <template #default="{ row }">
              <el-select v-model="row.fieldKey" filterable :placeholder="$tt('选择字段（可搜索）')" size="small"
                         style="width: 100%" @change="onCondFieldChange(row)">
                <el-option-group :label="$tf('标准字段')">
                  <el-option v-for="f in condStandardFields" :key="f.fieldKey"
                             :label="`${f.label}（${f.fieldKey}）`" :value="f.fieldKey" />
                </el-option-group>
                <el-option-group v-if="condCustomFields.length" :label="$tf('自定义字段')">
                  <el-option v-for="f in condCustomFields" :key="f.fieldKey"
                             :label="`${f.label}（${f.fieldKey}）`" :value="f.fieldKey" />
                </el-option-group>
              </el-select>
            </template>
          </el-table-column>
          <el-table-column :label="$tf('操作符')" width="140">
            <template #default="{ row }">
              <el-select v-model="row.operator" :placeholder="$tt('操作符')" size="small" style="width: 100%">
                <el-option v-for="o in operatorsFor(row)" :key="o.value" :label="$tf(o.label)" :value="o.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column :label="$tf('条件值')" min-width="230">
            <template #default="{ row }">
              <!-- 数字 between：区间双值 -->
              <div v-if="condFieldType(row) === 'number' && row.operator === 'between'"
                   style="display:flex;gap:4px;align-items:center">
                <el-input-number v-model="row.valueLow" :precision="2" size="small" controls-position="right"
                                 style="flex:1" />
                <span>~</span>
                <el-input-number v-model="row.valueHigh" :precision="2" size="small" controls-position="right"
                                 style="flex:1" />
              </div>
              <!-- 数字：单值 -->
              <el-input-number v-else-if="condFieldType(row) === 'number'" v-model="row.valueLow" :precision="2"
                               size="small" controls-position="right" style="width: 100%" />
              <!-- select/bool：下拉选值 -->
              <el-select v-else-if="condFieldOptions(row).length" v-model="row.valueText" filterable
                         allow-create :placeholder="$tt('选择或输入值')" size="small" style="width: 100%">
                <el-option v-for="o in condFieldOptions(row)" :key="o.value" :label="$tf(o.label)" :value="o.value" />
              </el-select>
              <!-- 日期 -->
              <el-date-picker v-else-if="condFieldType(row) === 'date'" v-model="row.valueText" type="date"
                              value-format="YYYY-MM-DD" :placeholder="$tt('选择日期')" size="small" style="width: 100%" />
              <!-- 文本 -->
              <el-input v-else v-model="row.valueText" :placeholder="$tt('条件值')" size="small" />
            </template>
          </el-table-column>
          <el-table-column :label="$tf('跳转到节点序号')" width="150">
            <template #default="{ row }">
              <el-input-number v-model="row.targetNodeOrder" :min="1" :max="20" size="small"
                               controls-position="right" />
            </template>
          </el-table-column>
          <el-table-column :label="$tf('操作')" width="80">
            <template #default="{ $index }">
              <el-button v-permission="perm('deleteRow')" link type="danger" @click="flowConditions.splice($index, 1)">{{ $tt("删除") }}</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button v-permission="perm('addRow')" type="primary" plain :icon="Plus" size="small" @click="addConditionRow"
                  style="margin-top: 10px;">{{ $tt("添加条件") }}</el-button>
      </div>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="conditionDialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button v-permission="perm('save')" type="primary" @click="saveFlowConditions">{{ $tt("保存条件") }}</el-button>
      </template>
    </el-dialog>

    <!-- ====== 子页面：我的待办 ====== -->
    <div v-show="activeTab === 'tasks'" class="kgw-body">
      <ApprovalTasks />
    </div>

    <!-- ====== 子页面：审批通知 ====== -->
    <div v-show="activeTab === 'notify'" class="kgw-body">
      <ApprovalNotifications />
    </div>
  </div>
</template>

<script setup>
import { usePagePerm } from '@/utils/pagePerm'
const perm = usePagePerm('system:approval')
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowRight, Switch, List, Bell, Plus, Delete, ArrowUp, ArrowDown } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { getSysConfigByGroup, batchUpdateSysConfig, updateSysConfig, getSysConfigByKey } from '@/api/system/sysConfig'
import { clearApprovalGateCache } from '@/utils/approval-gate'
import { useAutoDocCode } from '@/composables/useAutoDocCode'
import { isBillHidden } from '@/config/hiddenBills'
import ApprovalTasks from '@/views/approval/ApprovalTasks.vue'
import ApprovalNotifications from '@/views/approval/ApprovalNotifications.vue'
import { tt } from '@/i18n'

const activeTab = ref('switch')
const catKeyword = ref('')
const currentBillType = ref('')
const billSwitching = ref(false)
const myPendingCount = ref(0)
const unreadNotifCount = ref(0)

const subTabs = computed(() => [
  { key: 'switch', label: '单据审批开关', icon: Switch },
  { key: 'tasks', label: '我的待办', icon: List, badge: { value: myPendingCount.value } },
  { key: 'notify', label: '审批通知', icon: Bell, badge: { value: unreadNotifCount.value } }
])

// 所有单据类型，按业务模块分组
const billTree = reactive([
  {
    key: 'sale', label: '销售管理', expanded: true, items: [
      { billType: 'SALE_QUOTE', billName: '销售报价单', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'SALE_ORDER', billName: '销售订单', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'SALE_CONTRACT', billName: '销售合同', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'SALE_DELIVERY', billName: '销售出库单', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'SALE_RETURN', billName: '销售退货', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'SALE_EXCHANGE', billName: '销售换货', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'SALE_REPAIR', billName: '售后维修单', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'SALE_INVOICE', billName: '销售发票', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false }
    ]
  },
  {
    key: 'purchase', label: '采购管理', expanded: false, items: [
      { billType: 'PURCHASE_REQUEST', billName: '采购申请单', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'PURCHASE_ORDER', billName: '采购订单', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'PURCHASE_CONTRACT', billName: '采购合同', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'PURCHASE_INBOUND', billName: '采购入库单', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'PURCHASE_RETURN', billName: '采购退货单', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'PURCHASE_INVOICE', billName: '采购发票', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false }
    ]
  },
  {
    key: 'production', label: '生产管理', expanded: false, items: [
      { billType: 'PRODUCTION_ORDER', billName: '生产工单', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'PRODUCE_ASSEMBLE', billName: '生产组装单', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'PRODUCE_DISASSEMBLE', billName: '生产拆卸单', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'PRODUCTION_RECEIPT', billName: '生产入库单', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'MATERIAL_REQUISITION', billName: '生产领料单', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'MATERIAL_RETURN', billName: '生产退料单', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'MATERIAL_SUPPLEMENT', billName: '生产补料单', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false }
    ]
  },
  {
    key: 'subcontract', label: '委外管理', expanded: false, items: [
      { billType: 'SUBCONTRACT_ORDER', billName: '委外加工单', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'SUBCONTRACT_DELIVERY', billName: '委外发料单', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'SUBCONTRACT_RECEIPT', billName: '委外入库单', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'SUBCONTRACT_RETURN', billName: '委外退料单', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'SUBCONTRACT_SUPPLEMENT', billName: '委外补料单', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false }
    ]
  },
  {
    key: 'sample', label: '打样/样品', expanded: false, items: [
      { billType: 'SAMPLE_ORDER', billName: '打样单', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'FREE_SAMPLE', billName: '免费样品订单', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false }
    ]
  },
  {
    key: 'inventory', label: '库存管理', expanded: false, items: [
      { billType: 'STOCK_TRANSFER', billName: '库存调拨单', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'STOCK_CHECK', billName: '库存盘点单', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'OTHER_RECEIPT', billName: '其他入库单', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'OTHER_ISSUE', billName: '其他出库单', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false }
    ]
  },
  {
    key: 'finance', label: '财务管理', expanded: false, items: [
      { billType: 'PURCHASE_EXPENSE', billName: '采购费用单', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'OPERATION_EXPENSE', billName: '费用分摊', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'PURCHASE_PRE_PAYMENT', billName: '预付款单', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'SALE_PRE_RECEIPT', billName: '销售预收款单', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'PAYMENT_RECEIPT', billName: '收款单', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'PAYMENT_VOUCHER', billName: '付款单', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'WAGE_SETTLEMENT', billName: '工序工资核算', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false }
    ]
  },
  {
    key: 'other', label: '其他单据', expanded: false, items: [
      { billType: 'BOM', billName: 'BOM', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false },
      { billType: 'CUSTOMER_CREDIT', billName: '客户授信(月结一次性审批)', enabled: false, flowId: null, flowName: '', flowCode: '', nodeCount: 0, hasCondition: false }
    ]
  }
])

// 客户包隐藏单据（VITE_HIDDEN_BILLS）：121 不上换货/维修，审批设置里这两行整行不出现
billTree.forEach(g => {
  if (!g.items) return
  g.items = g.items.filter(it => !isBillHidden(it.billType))
})

const currentBill = computed(() => {
  for (const g of billTree) {
    const found = g.items.find(i => i.billType === currentBillType.value)
    if (found) return found
  }
  return null
})

function toggleGroup(key) {
  const g = billTree.find(x => x.key === key)
  if (g) g.expanded = !g.expanded
}

function selectBill(item) {
  currentBillType.value = item.billType
  loadBillFlowInfo(item)
}

async function loadBillFlowInfo(item) {
  try {
    const res = await request.get('/approval/flows', { params: { billType: item.billType } })
    const list = Array.isArray(res) ? res : (res?.data || [])
    if (list.length > 0) {
      const f = list[0]
      item.flowId = f.flowId
      item.flowName = f.flowName
      item.flowCode = f.flowCode
      item.nodeCount = f.nodeCount || 0
      item.hasCondition = !!f.hasCondition
    } else {
      item.flowId = null
      item.flowName = ''
      item.flowCode = ''
      item.nodeCount = 0
      item.hasCondition = false
    }
    // 单据级审批开关从 sys_config 的 approval.bill.{billType}.enabled 读取，
    // 不能用审批流程的 enabled 字段回显，否则关闭开关后刷新会因流程仍启用而变回开启
    try {
      const switchRes = await request.get(`/approval/bill-enabled/${item.billType}`)
      item.enabled = !!(switchRes?.data ?? switchRes)
    } catch (e2) {
      // 读取开关失败时保留默认值
    }
  } catch (e) {
    // 静默忽略
  }
}

async function onToggleBill(item) {
  billSwitching.value = true
  try {
    await request.put(`/approval/bill-enabled/${item.billType}`, null, { params: { enabled: item.enabled } })
    clearApprovalGateCache(item.billType)
    ElMessage.success(`${item.billName}${tt(" 审批已")}${item.enabled ? tt('开启') : tt('关闭')}${tt("（页面已即时生效）")}`)
  } catch (e) {
    item.enabled = !item.enabled
    ElMessage.error(tt('切换失败'))
  } finally {
    billSwitching.value = false
  }
}

// ============ 内联：审批流程编辑器 ============
const { docCodeDisabled: flowDocCodeDisabled, autoFillDocCodeOnCreate } = useAutoDocCode('WF_APPROVAL_FLOW')

const flowDialogVisible = ref(false)
const flowIsEdit = ref(false)
const flowSaving = ref(false)
const flowBillTypes = ref([])
const flowConditions = ref([])
const currentConditionFlowId = ref(null)

// ======== 审批全局总开关 (approval.enabled) ========
const globalApprovalEnabled = ref(true) // 默认为 true，onMounted 会按 DB 真实值修正
const globalApprovalLoading = ref(false)

async function loadGlobalApprovalSwitch() {
  const cfg = await getSysConfigByKey('approval.enabled')
  // cfg: { configKey, configValue }；缺失记录时视为 true（与后端 SysConfigService 默认一致）
  const v = cfg?.configValue
  globalApprovalEnabled.value = !(v === false || v === 'false')
}

async function onToggleGlobal(val) {
  globalApprovalLoading.value = true
  try {
    await updateSysConfig('approval.enabled', val ? 'true' : 'false')
    // 清缓存：全局+所有单据
    clearApprovalGateCache()
    ElMessage.success(`${tt("全局审批总开关已")}${val ? tt('开启') : tt('关闭')}${tt("（页面已即时生效）")}`)
    // 提示联动：全局关会覆盖所有单据级开关
    if (!val) {
      ElMessage({
        message: tt('提示：全局审批总开关关闭后，所有单据提交将直接生效，无需人工审批。'),
        type: 'info',
        duration: 3500
      })
    }
  } catch (e) {
    // 回滚 UI
    globalApprovalEnabled.value = !val
    ElMessage.error(tt('切换全局审批总开关失败：') + (e?.message || e || ''))
  } finally {
    globalApprovalLoading.value = false
  }
}

// 审批人远程搜索
const approverOptions = reactive({})      // { [nodeIdx]: [{label,value,raw}, ...] }
const approverSearchLoading = reactive({}) // { [nodeIdx]: boolean }
const approverHasDefaultLoaded = reactive({}) // { [nodeIdx]: true }
let _lastApproverReqId = 0

function ensureApproverOptionsArray(idx) {
  if (!Array.isArray(approverOptions[idx])) {
    approverOptions[idx] = []
  }
  return approverOptions[idx]
}

function buildOptionLabel(raw) {
  const parts = []
  if (raw.employeeName) parts.push(raw.employeeName)
  if (raw.employeeCode) parts.push(raw.employeeCode)
  if (raw.departmentName) parts.push(raw.departmentName)
  if (raw.positionName) parts.push(raw.positionName)
  return parts.join(' · ')
}

function buildOptionValue(raw) {
  // 用姓名+工号组合作为唯一 value，避免重名
  const name = String(raw.employeeName || '').trim()
  const code = String(raw.employeeCode || '').trim()
  return code ? `${name}｜${code}` : name
}

function seedExistingApproversIntoOptions(idx) {
  const node = flowFormData.nodes[idx]
  if (!node) return
  const arr = ensureApproverOptionsArray(idx)
  const existed = new Set(arr.map(o => o.value))
  for (const a of (node.approvers || [])) {
    const raw = {
      employeeId: a.employeeId,
      userId: a.userId,
      employeeName: a.approverName || '',
      employeeCode: a.employeeCode || '',
      departmentName: a.departmentName || '',
      positionName: a.positionName || ''
    }
    if (!raw.employeeName) continue
    const value = buildOptionValue(raw)
    if (!existed.has(value)) {
      arr.unshift({ label: buildOptionLabel(raw), value, raw })
      existed.add(value)
    }
  }
}

async function remoteSearchApprovers(nodeIdx, kw) {
  const idx = nodeIdx
  approverSearchLoading[idx] = true
  const reqId = ++_lastApproverReqId
  try {
    const res = await request.get('/basedata/employee/select-options', {
      params: { keyword: (kw || '').trim() || undefined, activeOnly: true }
    })
    if (reqId !== _lastApproverReqId) return
    // 注意：request 响应拦截器已解包 Result（res 即数组本体），res.data 为 undefined
    const list = Array.isArray(res) ? res : (Array.isArray(res?.data) ? res.data : [])
    const arr = ensureApproverOptionsArray(idx)
    arr.splice(0, arr.length)
    for (const e of list) {
      const name = String(e.employeeName || '').trim()
      if (!name) continue
      const raw = {
        employeeId: e.employeeId,
        userId: e.userId,
        employeeName: name,
        employeeCode: e.employeeCode || '',
        departmentName: e.departmentName || '',
        positionName: e.positionName || ''
      }
      arr.push({
        label: buildOptionLabel(raw),
        value: buildOptionValue(raw),
        raw
      })
    }
    seedExistingApproversIntoOptions(idx)
    approverHasDefaultLoaded[idx] = true
  } catch (e) {
    console.warn('[KgwApproval] 搜索员工失败', e)
    ElMessage.error(tt('搜索员工失败'))
  } finally {
    if (reqId === _lastApproverReqId) approverSearchLoading[idx] = false
  }
}

async function onApproverSelectFocus(idx) {
  if (!approverHasDefaultLoaded[idx]) {
    await remoteSearchApprovers(idx, '')
  }
}

function nodeApproverSelectedKeys(idx) {
  const node = flowFormData.nodes[idx]
  if (!node) return []
  return (node.approvers || []).map(a => {
    const raw = {
      employeeName: a.approverName || '',
      employeeCode: a.employeeCode || '',
      departmentName: a.departmentName || ''
    }
    return buildOptionValue(raw)
  })
}

function findOptionRawByValue(idx, value) {
  const arr = approverOptions[idx] || []
  const opt = arr.find(o => o.value === value)
  return opt ? opt.raw : null
}

function onApproversSelected(idx, values) {
  const node = flowFormData.nodes[idx]
  if (!node) return
  const currentKeys = new Set(nodeApproverSelectedKeys(idx))
  const newKeys = new Set(Array.isArray(values) ? values : [])
  // 移除去掉的
  for (let i = node.approvers.length - 1; i >= 0; i--) {
    const a = node.approvers[i]
    const k = buildOptionValue({
      employeeName: a.approverName || '',
      employeeCode: a.employeeCode || '',
      departmentName: a.departmentName || ''
    })
    if (!newKeys.has(k)) node.approvers.splice(i, 1)
  }
  // 增加新增的
  for (const v of newKeys) {
    if (currentKeys.has(v)) continue
    const raw = findOptionRawByValue(idx, v)
    if (!raw) continue
    node.approvers.push({
      userId: raw.userId || null,
      employeeId: raw.employeeId || null,
      approverName: raw.employeeName,
      employeeCode: raw.employeeCode || '',
      departmentName: raw.departmentName || '',
      positionName: raw.positionName || ''
    })
  }
}

// ============ 抄送人选择（独立于节点审批人，用虚拟idx='-notify'） ============
/** 返回当前抄送人对应的 option.value 数组（供 el-select 已选值回显） */
function notifyUserSelectedKeys() {
  return (flowFormData.notifyUsers || []).map(u => buildOptionValue({
    employeeName: u.approverName || '',
    employeeCode: u.employeeCode || '',
    departmentName: u.departmentName || ''
  }))
}
/** 抄送人标签关闭按钮：删除第几个 */
function removeNotifyUser(index) {
  if (!flowFormData.notifyUsers) return
  flowFormData.notifyUsers.splice(index, 1)
}
/** el-select 多选回调：按差集增删 notifyUsers 列表 */
function onNotifyUsersSelected(values) {
  if (!Array.isArray(flowFormData.notifyUsers)) flowFormData.notifyUsers = []
  const currentKeys = new Set(notifyUserSelectedKeys())
  const newKeys = new Set(Array.isArray(values) ? values : [])
  // 移除去掉的
  for (let i = flowFormData.notifyUsers.length - 1; i >= 0; i--) {
    const a = flowFormData.notifyUsers[i]
    const k = buildOptionValue({
      employeeName: a.approverName || '',
      employeeCode: a.employeeCode || '',
      departmentName: a.departmentName || ''
    })
    if (!newKeys.has(k)) flowFormData.notifyUsers.splice(i, 1)
  }
  // 增加新增的
  for (const v of newKeys) {
    if (currentKeys.has(v)) continue
    const raw = findOptionRawByValue('-notify', v)
    if (!raw) continue
    flowFormData.notifyUsers.push({
      userId: raw.userId || null,
      employeeId: raw.employeeId || null,
      approverName: raw.employeeName,
      employeeCode: raw.employeeCode || '',
      departmentName: raw.departmentName || '',
      positionName: raw.positionName || ''
    })
  }
}

// 保存前对已选审批人回溯匹配真实ID（如果之前存的只是姓名字符串）
function ensureApproversHydrated() {
  if (!flowFormData.nodes) return
  for (const node of flowFormData.nodes) {
    if (!node.approvers || node.approvers.length === 0) continue
    for (const a of node.approvers) {
      if ((a.userId || a.employeeId) && a.approverName) continue
      const name = String(a.approverName || '').trim()
      if (!name) continue
      let hit = null
      for (const idx in approverOptions) {
        const o = (approverOptions[idx] || []).find(opt =>
          opt.raw.employeeName === name ||
          buildOptionValue(opt.raw) === name ||
          opt.value === name)
        if (o) { hit = o.raw; break }
      }
      if (hit) {
        if (!a.userId) a.userId = hit.userId || null
        if (!a.employeeId) a.employeeId = hit.employeeId || null
        if (!a.approverName) a.approverName = hit.employeeName
        if (!a.employeeCode) a.employeeCode = hit.employeeCode || ''
        if (!a.departmentName) a.departmentName = hit.departmentName || ''
        if (!a.positionName) a.positionName = hit.positionName || ''
      }
    }
  }
}

const flowFormData = reactive({
  flowId: null, flowCode: '', flowName: '', billType: '', enabled: 1,
  description: '', nodes: [],
  // 抄送人前端状态：[{approverName,userId,employeeCode,departmentName,positionName}]
  // 保存时会抽 userId 数组 JSON.stringify 覆盖 notifyUserIds（发给后端）
  notifyUsers: [],
  // 后端持久化列："[1,2,3]" 形式的 JSON 字符串（抄送人 userId 列表）
  notifyUserIds: null
})

// 审批人选择
const currentFlowNodeIdx = ref(-1)
const selectedFlowUsers = ref([])

const conditionDialogVisible = ref(false)

const flowApi = {
  listFlows: (params) => request.get('/approval/flows', { params }),
  getFlow: (id) => request.get(`/approval/flows/${id}`),
  saveFlow: (data) => request.post('/approval/flows', data),
  updateFlow: (id, data) => request.put(`/approval/flows/${id}`, data),
  deleteFlow: (id) => request.delete(`/approval/flows/${id}`),
  setEnabled: (id, enabled) => request.put(`/approval/flows/${id}/enabled`, null, { params: { enabled } }),
  getConditions: (id) => request.get(`/approval/flows/${id}/conditions`),
  saveConditions: (id, data) => request.post(`/approval/flows/${id}/conditions`, data),
  listBillTypes: () => request.get('/approval/bill-types')
}

async function loadFlowBillTypes() {
  try {
    const res = await flowApi.listBillTypes()
    // 拦截器已解包：res 即数组本体
    flowBillTypes.value = Array.isArray(res) ? res : (res?.data || [])
  } catch {}
}

function goToFlowEditor(flowId) {
  // 直接打开内联弹窗
  if (flowId) {
    // 编辑已有流程
    openFlowEditorForEdit(flowId)
  } else {
    openFlowEditorForNew(currentBillType.value)
  }
}

async function openFlowEditorForNew(billType) {
  await loadFlowBillTypes()
  flowIsEdit.value = false
  Object.assign(flowFormData, {
    flowId: null, flowCode: '', flowName: '', billType: billType || '', enabled: 1,
    description: '', nodes: [], notifyUsers: [], notifyUserIds: null
  })
  // 重置每个节点的缓存状态
  Object.keys(approverOptions).forEach(k => delete approverOptions[k])
  Object.keys(approverSearchLoading).forEach(k => delete approverSearchLoading[k])
  Object.keys(approverHasDefaultLoaded).forEach(k => delete approverHasDefaultLoaded[k])
  flowDialogVisible.value = true
  await autoFillDocCodeOnCreate(flowFormData, 'flowCode')
}

async function openFlowEditorForEdit(flowId) {
  await loadFlowBillTypes()
  flowIsEdit.value = true
  try {
    const res = await flowApi.getFlow(flowId)
    // 拦截器已解包：res 即流程对象本体（含 nodes/approvers）
    Object.assign(flowFormData, res?.data || res || {})
    if (!flowFormData.nodes) flowFormData.nodes = []
    // 给已有审批人补齐子字段空字符串，避免标签和 value 计算出空
    for (const n of flowFormData.nodes) {
      (n.approvers || []).forEach(a => {
        if (a.approverName === undefined) a.approverName = ''
        if (a.employeeCode === undefined) a.employeeCode = ''
        if (a.departmentName === undefined) a.departmentName = ''
        if (a.positionName === undefined) a.positionName = ''
      })
    }
    // 抄送人：notifyUserIds JSON字符串 → notifyUsers 对象数组
    flowFormData.notifyUsers = flowFormData.notifyUsers || []
    await hydrateNotifyUsersFromIds()
    Object.keys(approverOptions).forEach(k => delete approverOptions[k])
    Object.keys(approverSearchLoading).forEach(k => delete approverSearchLoading[k])
    Object.keys(approverHasDefaultLoaded).forEach(k => delete approverHasDefaultLoaded[k])
    // 把已选抄送人注入 options 以便 el-select 回显
    seedNotifyUsersIntoOptions()
    flowDialogVisible.value = true
  } catch {
    ElMessage.error(tt('获取流程详情失败'))
  }
}

/**
 * 打开编辑弹窗时：根据 notifyUserIds JSON（例如 "[1,2,3]"）拉到对应员工档案
 * → 转成 notifyUsers 对象数组结构（approverName/userId/...），供标签显示用
 * 只拉一次失败或空都兜底不崩
 */
async function hydrateNotifyUsersFromIds() {
  const raw = flowFormData.notifyUserIds
  if (!raw || typeof raw !== 'string' || raw.trim() === '' || raw === '[]') {
    flowFormData.notifyUsers = []
    return
  }
  let ids = []
  try {
    const parsed = JSON.parse(raw)
    ids = Array.isArray(parsed) ? parsed.filter(x => x != null && x !== '') : []
  } catch {
    flowFormData.notifyUsers = []
    return
  }
  if (ids.length === 0) {
    flowFormData.notifyUsers = []
    return
  }
  // 借用 /basedata/employee/select-options 接口：空 keyword 会返回activeOnly的全量员工列表
  // 过滤命中已选ID即可（抄送人通常<10人，内存过滤足够）
  try {
    const res = await request.get('/basedata/employee/select-options', { params: { activeOnly: true } })
    const list = Array.isArray(res) ? res : (Array.isArray(res?.data) ? res.data : [])
    const idSet = new Set(ids.map(Number))
    flowFormData.notifyUsers = list
      .filter(e => e.userId != null && idSet.has(Number(e.userId)))
      .map(e => ({
        approverName: e.employeeName || '',
        userId: e.userId,
        employeeId: e.employeeId || null,
        employeeCode: e.employeeCode || '',
        departmentName: e.departmentName || '',
        positionName: e.positionName || ''
      }))
    // 补齐剩余找不到名字的（员工可能已删除离职）：只显示 userId
    const foundIds = new Set(flowFormData.notifyUsers.map(u => Number(u.userId)))
    for (const id of ids) {
      if (!foundIds.has(Number(id))) {
        flowFormData.notifyUsers.push({
          approverName: `已删除用户(ID:${id})`,
          userId: Number(id),
          employeeCode: '', departmentName: '', positionName: ''
        })
      }
    }
  } catch (e) {
    // 查不到就退化成用userId占位（不阻塞编辑）
    console.warn('[KgwApproval] 抄送人信息拉取失败，降级按ID显示', e)
    flowFormData.notifyUsers = ids.map(id => ({
      approverName: `用户ID:${id}`,
      userId: Number(id),
      employeeCode: '', departmentName: '', positionName: ''
    }))
  }
}

/** 把当前 flowFormData.notifyUsers 已经选过的人，注入 approverOptions['-notify'] → el-select 能正确显示已选值 */
function seedNotifyUsersIntoOptions() {
  if (!Array.isArray(approverOptions['-notify'])) approverOptions['-notify'] = []
  const arr = approverOptions['-notify']
  const existed = new Set(arr.map(o => o.value))
  for (const u of (flowFormData.notifyUsers || [])) {
    const raw = {
      employeeId: u.employeeId,
      userId: u.userId,
      employeeName: u.approverName || '',
      employeeCode: u.employeeCode || '',
      departmentName: u.departmentName || '',
      positionName: u.positionName || ''
    }
    const v = buildOptionValue(raw)
    if (existed.has(v)) continue
    arr.push({ label: buildOptionLabel(raw), value: v, raw })
    existed.add(v)
  }
  approverHasDefaultLoaded['-notify'] = true
}

// 节点操作
function addFlowNode() {
  const order = flowFormData.nodes.length + 1
  flowFormData.nodes.push({
    nodeId: null, nodeName: '', nodeOrder: order, approverMode: 'single',
    approvers: []
  })
}
function removeFlowNode(idx) {
  flowFormData.nodes.splice(idx, 1)
  flowFormData.nodes.forEach((n, i) => n.nodeOrder = i + 1)
}
function moveNodeUp(idx) {
  if (idx === 0) return
  const tmp = flowFormData.nodes[idx - 1]
  flowFormData.nodes[idx - 1] = flowFormData.nodes[idx]
  flowFormData.nodes[idx] = tmp
  flowFormData.nodes.forEach((n, i) => n.nodeOrder = i + 1)
}
function moveNodeDown(idx) {
  if (idx === flowFormData.nodes.length - 1) return
  const tmp = flowFormData.nodes[idx + 1]
  flowFormData.nodes[idx + 1] = flowFormData.nodes[idx]
  flowFormData.nodes[idx] = tmp
  flowFormData.nodes.forEach((n, i) => n.nodeOrder = i + 1)
}

function removeNodeApprover(nodeIdx, approverIdx) {
  flowFormData.nodes[nodeIdx].approvers.splice(approverIdx, 1)
}

// ============ 条件模板（通用字段编辑：表头全部字段 + 自定义字段） ============
const conditionFields = ref([]) // condition-fields 接口返回的字段元数据
const condStandardFields = computed(() => conditionFields.value.filter(f => !f.custom))
const condCustomFields = computed(() => conditionFields.value.filter(f => f.custom))

/** 加载指定单据类型的条件字段元数据 */
async function loadConditionFields(billType) {
  conditionFields.value = []
  if (!billType) return
  try {
    const res = await request.get(`/approval/bill-types/${billType}/condition-fields`)
    // 拦截器已解包：res 即数组本体
    conditionFields.value = Array.isArray(res) ? res : (res?.data || [])
  } catch (e) {
    console.warn('[KgwApproval] 加载条件字段失败', e)
  }
}

/** 规则行当前选中的字段元数据 */
function condFieldMeta(row) {
  if (!row || !row.fieldKey) return null
  return conditionFields.value.find(f => f.fieldKey === row.fieldKey) || null
}

/** 字段条件类型：number/date/select/string（bool 归一为 select 固定 是/否 选项） */
function condFieldType(row) {
  const m = condFieldMeta(row)
  if (!m) return 'string'
  const t = m.type
  if (['number', 'amount', 'int'].includes(t)) return 'number'
  if (['date', 'datetime'].includes(t)) return 'date'
  if (t === 'select' || t === 'bool') return 'select'
  return 'string'
}

/** select/bool 字段的候选值 */
function condFieldOptions(row) {
  const m = condFieldMeta(row)
  if (!m) return []
  if (m.type === 'bool') return [{ label: '是', value: 'true' }, { label: '否', value: 'false' }]
  return Array.isArray(m.options) ? m.options : []
}

/** 各类型可用操作符（与后端 ApprovalRuntimeService 归一后的操作符一一对应） */
const OPERATORS_BY_TYPE = {
  number: [
    { value: 'ge', label: '大于等于' }, { value: 'gt', label: '大于' },
    { value: 'le', label: '小于等于' }, { value: 'lt', label: '小于' },
    { value: 'eq', label: '等于' }, { value: 'ne', label: '不等于' },
    { value: 'between', label: '介于' }
  ],
  string: [
    { value: 'eq', label: '等于' }, { value: 'ne', label: '不等于' }, { value: 'contains', label: '包含' }
  ],
  select: [
    { value: 'eq', label: '等于' }, { value: 'ne', label: '不等于' }
  ],
  date: [
    { value: 'eq', label: '等于(当天)' }, { value: 'gt', label: '晚于' },
    { value: 'ge', label: '不早于' }, { value: 'lt', label: '早于' }, { value: 'le', label: '不晚于' }
  ]
}

function operatorsFor(row) {
  return OPERATORS_BY_TYPE[condFieldType(row)] || OPERATORS_BY_TYPE.string
}

/** 旧操作符归一：'>='→ge、'=='→eq、历史大写GT→gt（回填与保存统一新口径） */
const OPERATOR_LEGACY = { '>=': 'ge', '>': 'gt', '<=': 'le', '<': 'lt', '==': 'eq' }
function normalizeCondOperator(op) {
  if (!op) return 'eq'
  const low = String(op).trim().toLowerCase()
  return OPERATOR_LEGACY[low] || low
}

/** 切换字段：清空旧值并重置为该类型的默认操作符 */
function onCondFieldChange(row) {
  const ops = operatorsFor(row)
  row.operator = ops[0]?.value || 'eq'
  row.valueLow = null
  row.valueHigh = null
  row.valueText = null
}

function addConditionRow() {
  flowConditions.value.push({
    fieldKey: '', operator: 'eq',
    valueLow: null, valueHigh: null, valueText: null,
    targetNodeOrder: 1, description: ''
  })
}

/** 回填归一：操作符换新口径；非数字字段上的旧数字值搬到 valueText（兼容历史数据） */
function normalizeConditionRow(c) {
  const row = {
    fieldKey: c.fieldKey || '',
    operator: normalizeCondOperator(c.operator),
    valueLow: c.valueLow ?? null,
    valueHigh: c.valueHigh ?? null,
    valueText: c.valueText ?? null,
    targetNodeOrder: c.targetNodeOrder ?? 1,
    description: c.description || ''
  }
  if (condFieldType(row) !== 'number' && row.valueText == null && row.valueLow != null) {
    row.valueText = String(row.valueLow)
    row.valueLow = null
  }
  return row
}

// 条件模板
async function openConditionTemplate() {
  if (!flowFormData.flowId && !flowIsEdit.value) {
    ElMessage.warning(tt('请先保存流程后再设置条件模板'))
    return
  }
  currentConditionFlowId.value = flowFormData.flowId
  // 先加载字段元数据再回填（行类型判断依赖元数据）
  await loadConditionFields(flowFormData.billType)
  try {
    const res = await flowApi.getConditions(currentConditionFlowId.value)
    // 拦截器已解包：res 即数组本体
    const list = Array.isArray(res) ? res : (res?.data || [])
    flowConditions.value = list.map(normalizeConditionRow)
  } catch {
    flowConditions.value = []
  }
  conditionDialogVisible.value = true
}
async function saveFlowConditions() {
  // 数据卫生：按字段类型分流条件值列（number→valueLow/valueHigh，其余→valueText），过滤未配置完整的行
  const payload = flowConditions.value
    .filter(r => r.fieldKey && r.operator && r.targetNodeOrder != null)
    .filter(r => {
      if (condFieldType(r) === 'number') return r.valueLow != null
      return r.valueText != null && r.valueText !== ''
    })
    .map(r => {
      const isNumber = condFieldType(r) === 'number'
      return {
        fieldKey: r.fieldKey,
        operator: r.operator,
        valueLow: isNumber ? (r.valueLow ?? null) : null,
        valueHigh: (isNumber && r.operator === 'between') ? (r.valueHigh ?? null) : null,
        valueText: !isNumber ? (r.valueText ?? null) : null,
        targetNodeOrder: r.targetNodeOrder,
        description: r.description || ''
      }
    })
  try {
    await flowApi.saveConditions(currentConditionFlowId.value, payload)
    ElMessage.success(tt('条件保存成功'))
    conditionDialogVisible.value = false
  } catch {
    ElMessage.error(tt('保存失败'))
  }
}

// 删除流程（后端级联删除节点/审批人/条件；有运行中审批任务时任务一并失效，二次确认强提醒）
async function handleDeleteFlow() {
  const flowId = flowFormData.flowId
  if (!flowId) return
  try {
    await ElMessageBox.confirm(
      `${tt("确定删除流程「")}${flowFormData.flowName || flowFormData.flowCode}${tt("」吗？\\n删除后该单据类型将回到「未配置审批流程」状态，操作不可恢复；若存在运行中的审批任务，相关任务将一并失效。")}`,
      tt('删除审批流程'),
      { type: 'warning', confirmButtonText: tt('确认删除'), cancelButtonText: tt('取消') }
    )
  } catch (e) { return } // 用户取消/关闭确认框，一律中止删除
  try {
    await flowApi.deleteFlow(flowId)
    ElMessage.success(tt('删除成功'))
    flowDialogVisible.value = false
    // 与保存一致：广播事件刷新单据流程信息
    window.dispatchEvent(new CustomEvent('approval-flow-saved'))
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || tt('删除失败'))
  }
}

// 保存流程
async function handleSaveFlow() {
  if (!flowFormData.flowName) { ElMessage.warning(tt('请输入流程名称')); return }
  if (!flowFormData.billType) { ElMessage.warning(tt('请选择单据类型')); return }
  if (!flowFormData.nodes || flowFormData.nodes.length === 0) {
    ElMessage.warning(tt('请至少添加一个节点'))
    return
  }
  for (const n of flowFormData.nodes) {
    if (!n.nodeName) { ElMessage.warning(tt('请填写所有节点名称')); return }
    // 系统自动通过(auto)节点无需审批人
    if (n.approverMode !== 'auto' && (!n.approvers || n.approvers.length === 0)) {
      ElMessage.warning(`${tt("节点\"")}${n.nodeName}${tt("\"未配置审批人")}`)
      return
    }
  }
  flowSaving.value = true
  try {
    ensureApproversHydrated()
    // 抄送人：从 notifyUsers 抽取 userId 数组 → JSON字符串写入后端持久化列 notifyUserIds
    const uids = (flowFormData.notifyUsers || [])
      .map(u => Number(u?.userId)).filter(n => Number.isFinite(n) && n > 0)
    flowFormData.notifyUserIds = (uids.length > 0) ? JSON.stringify(uids) : null
    // 清前端临时字段，不发给后端（MP实体没有 notifyUsers 列，传过去会被Jackson忽略，但为了干净主动删掉）
    const sendData = { ...flowFormData }
    delete sendData.notifyUsers
    if (flowIsEdit.value) {
      await flowApi.updateFlow(flowFormData.flowId, sendData)
    } else {
      await flowApi.saveFlow(sendData)
    }
    ElMessage.success(flowIsEdit.value ? tt('更新成功') : tt('创建成功'))
    flowDialogVisible.value = false
    // 通知父组件刷新
    window.dispatchEvent(new CustomEvent('approval-flow-saved'))
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || tt('保存失败'))
  } finally {
    flowSaving.value = false
  }
}

// 监听审批流程编辑器保存事件，自动刷新单据列表
onMounted(async () => {
  window.addEventListener('approval-flow-saved', () => {
    loadAllBillFlowInfo()
  })
  await loadAllBillFlowInfo()
  await loadGlobalApprovalSwitch()
  await Promise.all([loadMyPendingCount(), loadUnreadNotifCount()])
  // 默认选中第一个单据
  if (!currentBillType.value && billTree[0].items.length > 0) {
    selectBill(billTree[0].items[0])
  }
})

async function refreshAll() {
  await Promise.all([
    loadAllBillFlowInfo(),
    loadMyPendingCount(),
    loadUnreadNotifCount(),
    loadGlobalApprovalSwitch()
  ])
  ElMessage.success(tt('刷新成功'))
}

async function loadAllBillFlowInfo() {
  for (const g of billTree) {
    for (const item of g.items) {
      await loadBillFlowInfo(item)
    }
  }
}

async function loadMyPendingCount() {
  try {
    const res = await request.get('/approval/tasks/my-pending')
    const list = Array.isArray(res) ? res : (res?.data || [])
    myPendingCount.value = list.length
  } catch (e) { myPendingCount.value = 0 }
}

async function loadUnreadNotifCount() {
  try {
    const res = await request.get('/approval/notifications/unread-count')
    unreadNotifCount.value = (typeof res === 'number') ? res : (res?.data || 0)
  } catch (e) { unreadNotifCount.value = 0 }
}

</script>

<style scoped lang="scss">
.kgw-approval {
  background: #fff;
  min-height: 100%;
  font-family: Tahoma, 'Microsoft YaHei', sans-serif;
  display: flex;
  flex-direction: column;
}

.kgw-toolbar {
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  padding: 0 16px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}

.kgw-sub-tabs {
  display: flex;
  gap: 4px;
  flex: 1;
  overflow-x: auto;
}

.kgw-sub-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  cursor: pointer;
  border-radius: 4px;
  font-size: 13px;
  color: #606266;
  background: transparent;
  border: 1px solid transparent;
  transition: all 0.2s;
  position: relative;
  white-space: nowrap;
  &:hover {
    color: #1890ff;
    background: #e6f7ff;
  }
  &.active {
    color: #fff;
    background: #1890ff;
    border-color: #1890ff;
    font-weight: 500;
  }
  .tab-icon { font-size: 14px; }
  .tab-badge {
    margin-left: 4px;
    :deep(.el-badge__content) {
      font-size: 11px;
      height: 16px;
      line-height: 16px;
      padding: 0 4px;
    }
  }
}

.kgw-actions { display: flex; gap: 8px; }

.kgw-body { padding: 12px; flex: 1; overflow: auto; }

/* ============ 全局审批总开关 ============ */
.kgw-global-switch {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 20px;
  margin-bottom: 12px;
  border-radius: 10px;
  background: linear-gradient(135deg, #fff 0%, #f5f9ff 100%);
  border: 1px solid #d9e8ff;
  box-shadow: 0 2px 6px rgba(80,130,200,0.06);
}
.global-switch-left { display: flex; flex-direction: column; gap: 6px; flex: 1; }
.global-switch-title {
  display: flex; align-items: center;
  font-size: 15px; font-weight: 600; color: #303133;
}
.gs-icon { color: #409eff; margin-right: 6px; font-size: 16px; }
.global-switch-desc {
  font-size: 12.5px; line-height: 1.7; color: #606266;
}
.global-switch-right { display: flex; align-items: center; min-width: 120px; justify-content: flex-end; }

.kgw-layout-split { display: flex; min-height: 600px; gap: 12px; }

.kgw-side {
  width: 240px;
  border: 1px solid #ebeef5;
  background: #f5f7fa;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}
.kgw-side-search { padding: 8px; }
.kgw-side-list { flex: 1; overflow: auto; padding: 4px 0; }

.kgw-side-group { margin-bottom: 4px; }
.kgw-side-group-title {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  background: #e8eaf0;
  cursor: pointer;
  user-select: none;
  &:hover { background: #d8dce6; }
  .arrow {
    transition: transform 0.2s;
    font-size: 12px;
    &.expanded { transform: rotate(90deg); }
  }
  .count-tag {
    margin-left: auto;
    transform: scale(0.85);
  }
}

.kgw-side-children {
  border-top: 1px solid #ebeef5;
}

.kgw-side-item {
  padding: 8px 16px 8px 28px;
  cursor: pointer;
  font-size: 13px;
  color: #303133;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  &:hover { background: #e6f7ff; color: #1890ff; }
  &.active {
    background: #1890ff;
    color: #fff;
    font-weight: 600;
    :deep(.el-tag) {
      background: rgba(255,255,255,0.2);
      border-color: rgba(255,255,255,0.3);
      color: #fff;
    }
  }
}

.kgw-main {
  flex: 1;
  background: #fff;
  border: 1px solid #ebeef5;
  padding: 16px 24px;
  overflow: auto;
}

.bill-config-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #ebeef5;
  padding-bottom: 12px;
  h3 {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
    color: #303133;
  }
  .header-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    .switch-label {
      font-size: 13px;
      color: #606266;
    }
  }
}

.bill-flow-info {
  .flow-summary {
    display: flex;
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;
  }
}

:deep(.el-button--primary), :deep(.el-button--success) {
  background-color: #1890ff;
  border-color: #1890ff;
}
:deep(.el-switch.is-checked .el-switch__core) {
  background-color: #1890ff;
  border-color: #1890ff;
}

/* ====== 审批流程编辑器（内联弹窗）样式 ====== */
:deep(.flow-edit-wrap) {
  background: #fafbfc;
  padding: 16px;
  border-radius: 4px;
}
.flow-info-bar {
  background: #fff;
  border: 1px solid #ebeef5;
  padding: 16px;
  border-radius: 4px;
  margin-bottom: 16px;
}
.notify-users-bar {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 14px 16px;
  margin-bottom: 16px;
  .notify-title {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 10px;
    font-size: 14px;
    font-weight: 600;
    color: #303133;
    .el-icon { color: #1890ff; }
    .notify-hint { margin-left: 6px; font-weight: normal; }
  }
  .notify-body {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
  }
}
.nodes-editor {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 16px;
  min-height: 300px;
}
.nodes-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
  .title {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
  }
}
.nodes-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  position: relative;
}
.node-card {
  background: #f5f9ff;
  border: 1px solid #d6e8ff;
  border-radius: 6px;
  padding: 12px 16px;
  position: relative;
  &::before {
    content: '';
    position: absolute;
    left: 20px;
    top: -16px;
    width: 2px;
    height: 16px;
    background: #91caff;
  }
  &:first-child::before {
    display: none;
  }
}
.node-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}
.node-index {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #1890ff;
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 13px;
  flex-shrink: 0;
}
.node-order {
  font-size: 12px;
  color: #909399;
  margin-right: auto;
}
.node-approvers {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-left: 38px;
}
.approvers-label {
  font-size: 12px;
  color: #606266;
}
.approvers-list {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.approver-tag {
  background: #e6f7ff;
  border-color: #91caff;
  color: #0958d9;
  .approver-tag-name { font-weight: 500; margin-right: 4px; }
  .approver-tag-sub {
    font-size: 11px; opacity: 0.75; margin-right: 3px;
    &:last-child { margin-right: 0; }
  }
}
.approver-opt-row {
  display: flex; flex-wrap: wrap; align-items: center; gap: 6px;
  line-height: 1.4;
  .approver-opt-name { font-weight: 600; color: #303133; }
  .approver-opt-sub { font-size: 12px; color: #909399; }
}
.approver-tip {
  font-size: 12px;
  color: #e6a23c;
  background: #fdf6ec;
  padding: 4px 8px;
  border-radius: 4px;
}
.conditions-wrap {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.conditions-tip {
  background: #e6f7ff;
  color: #0958d9;
  padding: 8px 12px;
  border-radius: 4px;
  font-size: 13px;
}
</style>
