<template>
  <div class="erp-page">
    <!-- 两个页签：目标分配在前、五级分解在后（文档 4.4 + 第二节入口清单第 6 条） -->
    <el-tabs v-model="mainTab">
      <el-tab-pane :label="$tt('目标分配')" name="assign">

    <!-- 顶部看板 -->
    <el-card class="dashboard-card" shadow="never">
      <div class="dashboard-row">
        <div class="stat-item">
          <div class="stat-label">{{ $tt("年度") }}</div>
          <div class="stat-value">{{ dashboard.year || '-' }}</div>
        </div>
        <div class="stat-item">
          <div class="stat-label">{{ $tt("公司目标") }}</div>
          <div class="stat-value" style="color:#409EFF">{{ fmtMoney(dashboard.companyTarget) }}</div>
        </div>
        <div class="stat-item">
          <div class="stat-label">{{ $tt("已分配") }}</div>
          <div class="stat-value" style="color:#67C23A">{{ fmtMoney(dashboard.allocatedAmount) }}</div>
        </div>
        <div class="stat-item">
          <div class="stat-label">{{ $tt("未分配") }}</div>
          <div class="stat-value" :style="{color: dashboard.unallocatedAmount > 0 ? '#F56C6C' : '#67C23A'}">
            {{ fmtMoney(dashboard.unallocatedAmount) }}
          </div>
        </div>
        <div class="stat-item">
          <div class="stat-label">{{ $tt("分配率") }}</div>
          <div class="stat-value">{{ dashboard.allocationRate }}%</div>
        </div>
        <div class="stat-item">
          <div class="stat-label">{{ $tt("已分配人数") }}</div>
          <div class="stat-value">{{ dashboard.allocatedCount || 0 }}</div>
        </div>
        <div class="stat-item">
          <div class="stat-label">{{ $tt("缺口人数") }}</div>
          <div class="stat-value" :style="{color: dashboard.shortageCount > 0 ? '#F56C6C' : '#67C23A'}">
            {{ dashboard.shortageCount || 0 }}
          </div>
        </div>
      </div>
      <el-alert v-if="dashboard.needRecruit" type="warning" :closable="false" style="margin-top:12px;">
        <template #title>
          <el-icon><Warning /></el-icon>
          {{ dashboard.message }} —— 建议在 {{ dashboard.year }} 年开始前完成目标分配与销售招聘
        </template>
      </el-alert>
      <el-alert v-else-if="dashboard.hasCompanyTarget" type="success" :closable="false" style="margin-top:12px;">
        <template #title>{{ $tt("目标分配充足") }}</template>
      </el-alert>
    </el-card>

    <!-- Tab 切换 -->
    <el-card shadow="never" style="margin-top:12px;">
      <el-tabs v-model="activeTab">
        <!-- 公司年度目标 -->
        <el-tab-pane :label="$tf('公司年度目标')" name="company">
          <div class="toolbar">
            <el-button type="primary" v-permission="perm('add')" @click="openCompanyDialog()">
              <el-icon><Plus /></el-icon>新增年度目标
            </el-button>
            <el-input v-model="companyQuery.year" :placeholder="$tt('年度')" style="width:120px;margin-left:8px;" clearable />
            <el-button v-permission="perm('query')" @click="loadCompany">{{ $tt("查询") }}</el-button>
            <el-tooltip :content="$tt('列设置')" placement="top">
              <el-button :icon="Setting" circle v-permission="perm('columnSetting')" @click="openColumnSetting" />
            </el-tooltip>
          </div>
          <el-table :data="companyList" v-loading="companyLoading" border stripe :key="configVersion" @sort-change="handleSortChange">
            <!-- 动态列渲染：列顺序由列设置拖拽配置驱动（sortedColumns），单元格按 col.type 分支渲染 -->
            <template v-for="col in companyVisibleColumns" :key="col.prop">
              <el-table-column
                :prop="col.prop"
                :label="$tf(col.label)"
                :width="col.width"
                :min-width="col.minWidth"
                :align="col.align || 'left'"
                :sortable="col.sortable === 'local' ? true : (col.sortable ? 'custom' : false)"
                :show-overflow-tooltip="!!col.tooltip"
                :fixed="getColFixed(col.prop, col.fixed)"
              >
                <template v-if="col.type" #default="{ row }">
                  <el-tag v-if="col.type === 'tag'" :type="col.tagType ? col.tagType(row) : 'info'" size="small">{{ col.tagText ? col.tagText(row) : (row[col.prop] || '-') }}</el-tag>
                  <span v-else-if="col.type === 'format'">{{ col.format(row) }}</span>
                  <span v-else-if="col.type === 'custom' && col.prop === 'unallocatedAmount'" :style="{color: row.unallocatedAmount > 0 ? '#F56C6C' : '#67C23A'}">
                    {{ fmtMoney(row.unallocatedAmount) }}
                  </span>
                </template>
              </el-table-column>
            </template>
            <AuditColumns />
            <!-- 操作列：编辑/生成下年度/删除 -->
            <el-table-column :label="$tf('操作')" width="180" fixed="right" align="center">
              <template #default="{ row }">
                <el-button type="primary" link size="small" v-permission="perm('edit')" @click="openCompanyDialog(row)">{{ $tt("编辑") }}</el-button>
                <el-button type="success" link size="small" v-permission="perm('copy')" @click="handleGenerateNextYear(row)">{{ $tt("生成下年度") }}</el-button>
                <el-button type="danger" link size="small" v-permission="perm('delete')" @click="handleDeleteCompany(row)">{{ $tt("删除") }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 销售员目标分配 -->
        <el-tab-pane :label="$tf('销售员目标分配')" name="employee">
          <div class="toolbar">
            <el-button type="primary" v-permission="perm('assign')" @click="openEmployeeDialog()">
              <el-icon><Plus /></el-icon>分配销售目标
            </el-button>
            <el-input v-model="empQuery.year" :placeholder="$tt('年度')" style="width:120px;margin-left:8px;" clearable />
            <el-select v-model="empQuery.quarter" :placeholder="$tt('季度')" style="width:110px;margin-left:8px;" clearable @change="loadEmployee">
              <el-option label="Q1" :value="1" />
              <el-option label="Q2" :value="2" />
              <el-option label="Q3" :value="3" />
              <el-option label="Q4" :value="4" />
            </el-select>
            <span v-if="empQuery.quarter" class="quarter-hint">实际完成指标按 Q{{ empQuery.quarter }} 口径统计</span>
            <el-select v-model="empQuery.allocationStatus" :placeholder="$tt('分配状态')" style="width:140px;margin-left:8px;" clearable>
              <el-option :label="$tf('已分配')" value="已分配" />
              <el-option :label="$tf('待分配')" value="待分配" />
            </el-select>
            <el-button v-permission="perm('query')" @click="loadEmployee">{{ $tt("查询") }}</el-button>
            <el-tooltip :content="$tt('列设置')" placement="top">
              <el-button :icon="Setting" circle v-permission="perm('columnSetting')" @click="openColumnSetting" />
            </el-tooltip>
          </div>
          <el-table :data="employeeList" v-loading="empLoading" border stripe :key="configVersion" @sort-change="handleEmpSortChange"
                    show-summary :summary-method="employeeSummary" :summary-row-style="{fontWeight: 600, background: '#f0f9eb'}">
            <!-- 动态列渲染：列顺序由列设置拖拽配置驱动（sortedColumns），单元格按 col.type 分支渲染 -->
            <template v-for="col in employeeVisibleColumns" :key="col.prop">
              <el-table-column
                :prop="col.prop"
                :label="$tf(col.label)"
                :width="col.width"
                :min-width="col.minWidth"
                :align="col.align || 'left'"
                :sortable="col.sortable === 'local' ? true : (col.sortable ? 'custom' : false)"
                :show-overflow-tooltip="!!col.tooltip"
                :fixed="getColFixed(col.prop, col.fixed)"
              >
                <template v-if="col.type" #default="{ row }">
                  <el-tag v-if="col.type === 'tag'" :type="col.tagType ? col.tagType(row) : 'info'" size="small">{{ col.tagText ? col.tagText(row) : (row[col.prop] || '-') }}</el-tag>
                  <span v-else-if="col.type === 'format'">{{ col.format(row) }}</span>
                  <span v-else-if="col.type === 'custom' && col.prop === 'actualAmount'" :style="{color: (dispActualAmount(row)) >= (dispTargetAmount(row)) ? '#67C23A' : '#E6A23C'}">
                    {{ fmtMoney(dispActualAmount(row)) }}
                  </span>
                  <el-progress v-else-if="col.type === 'custom' && col.prop === 'completionRate'" :percentage="Number(dispRate(row) || 0)" :color="progressColor(dispRate(row))" />
                </template>
              </el-table-column>
            </template>
            <AuditColumns />
            <!-- 操作列：编辑/刷新完成/删除 -->
            <el-table-column :label="$tf('操作')" width="150" fixed="right" align="center">
              <template #default="{ row }">
                <el-button type="primary" link size="small" v-permission="perm('edit')" @click="openEmployeeDialog(row)">{{ $tt("编辑") }}</el-button>
                <el-button type="success" link size="small" v-permission="perm('refresh')" @click="handleRefreshActual(row)">{{ $tt("刷新完成") }}</el-button>
                <el-button type="danger" link size="small" v-permission="perm('delete')" @click="handleDeleteEmployee(row)">{{ $tt("删除") }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 完成情况排名 -->
        <el-tab-pane :label="$tf('完成情况排名')" name="ranking">
          <div class="toolbar">
            <el-input v-model="rankingYear" :placeholder="$tt('年度')" style="width:120px;" clearable />
            <el-button type="primary" v-permission="perm('query')" @click="loadRanking">{{ $tt("查询排名（自动刷新实际值）") }}</el-button>
            <el-tooltip :content="$tt('列设置')" placement="top">
              <el-button :icon="Setting" circle v-permission="perm('columnSetting')" @click="openColumnSetting" />
            </el-tooltip>
          </div>
          <el-table :data="rankingList" v-loading="rankingLoading" border stripe :key="configVersion"
                    show-summary :summary-method="rankingSummary" :summary-row-style="{fontWeight: 600, background: '#ecf5ff'}">
            <!-- 动态列渲染：列顺序由列设置拖拽配置驱动（sortedColumns），单元格按 col.type 分支渲染 -->
            <template v-for="col in rankingVisibleColumns" :key="col.prop">
              <el-table-column
                :prop="col.prop"
                :label="$tf(col.label)"
                :width="col.width"
                :min-width="col.minWidth"
                :align="col.align || 'left'"
                :sortable="col.sortable === 'local' ? true : (col.sortable ? 'custom' : false)"
                :show-overflow-tooltip="!!col.tooltip"
                :fixed="getColFixed(col.prop, col.fixed)"
              >
                <template v-if="col.type" #default="{ row }">
                  <el-tag v-if="col.type === 'tag'" :type="col.tagType ? col.tagType(row) : 'info'" size="small">{{ col.tagText ? col.tagText(row) : (row[col.prop] || '-') }}</el-tag>
                  <span v-else-if="col.type === 'format'">{{ col.format(row) }}</span>
                  <el-progress v-else-if="col.type === 'custom' && col.prop === 'completionRate'" :percentage="Number(row.completionRate || 0)" :color="progressColor(row.completionRate)" />
                </template>
              </el-table-column>
            </template>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
      </el-tab-pane>

      <el-tab-pane :label="$tt('五级分解')" name="breakdown">
        <el-card shadow="never">
          <TargetBreakdownPanel v-if="mainTab === 'breakdown'" />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 公司年度目标弹窗 -->
    <el-dialog v-model="companyDialog.visible" :title="companyDialog.id ? '编辑年度目标' : '新增年度目标'" width="520px">
      <el-form :model="companyDialog.form" label-width="120px">
        <el-form-item :label="$tf('年度')" required>
          <el-input-number v-model="companyDialog.form.targetYear" :min="2020" :max="2099" />
        </el-form-item>
        <el-form-item :label="$tf('年度目标金额')" required>
          <el-input-number v-model="companyDialog.form.annualAmount" :precision="2" :step="100000" :min="0" />
          <div style="color:#909399;font-size:12px;">单位：元（如 2000万 = 20000000）</div>
        </el-form-item>
        <el-form-item :label="$tf('增长率(%)')">
          <el-input-number v-model="companyDialog.form.growthRate" :precision="2" :step="5" :min="0" :max="500" />
          <div style="color:#909399;font-size:12px;">{{ $tt("默认 30% —— 设置后下年度目标会自动按此增长率递增") }}</div>
        </el-form-item>
        <el-form-item :label="$tf('自动生成下年')">
          <el-switch v-model="companyDialog.form.autoGenerateNextYear" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item :label="$tf('状态')">
          <el-radio-group v-model="companyDialog.form.status">
            <el-radio value="启用">{{ $tt("启用") }}</el-radio>
            <el-radio value="停用">{{ $tt("停用") }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="$tf('备注')">
          <el-input v-model="companyDialog.form.description" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="companyDialog.visible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('save')" @click="saveCompany">{{ $tt("保存") }}</el-button>
      </template>
    </el-dialog>

    <!-- 销售员目标分配弹窗 -->
    <el-dialog v-model="empDialog.visible" :title="empDialog.id ? '编辑销售目标' : '分配销售目标'" width="560px">
      <el-form :model="empDialog.form" label-width="120px">
        <el-form-item :label="$tf('年度')" required>
          <el-input-number v-model="empDialog.form.targetYear" :min="2020" :max="2099" />
        </el-form-item>
        <el-form-item :label="$tf('销售员')" required>
          <el-select v-model="empDialog.form.employeeId" :placeholder="$tt('请选择员工')" filterable style="width:100%" @change="onEmployeeChange">
            <el-option
              v-for="emp in employeeOptions"
              :key="emp.employeeId"
              :label="emp.employeeName + (emp.deptName ? ' (' + emp.deptName + ')' : '')"
              :value="emp.employeeId"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$tf('销售员姓名')">
          <el-input v-model="empDialog.form.employeeName" disabled />
        </el-form-item>
        <el-form-item :label="$tf('部门')">
          <el-input v-model="empDialog.form.deptName" disabled />
        </el-form-item>
        <el-form-item :label="$tf('年度目标金额')" required>
          <el-input-number v-model="empDialog.form.annualTargetAmount" :precision="2" :step="100000" :min="0" />
        </el-form-item>
        <el-form-item :label="$tf('Q1目标')">
          <el-input-number v-model="empDialog.form.q1Target" :precision="2" :step="50000" :min="0" />
        </el-form-item>
        <el-form-item :label="$tf('Q2目标')">
          <el-input-number v-model="empDialog.form.q2Target" :precision="2" :step="50000" :min="0" />
        </el-form-item>
        <el-form-item :label="$tf('Q3目标')">
          <el-input-number v-model="empDialog.form.q3Target" :precision="2" :step="50000" :min="0" />
        </el-form-item>
        <el-form-item :label="$tf('Q4目标')">
          <el-input-number v-model="empDialog.form.q4Target" :precision="2" :step="50000" :min="0" />
        </el-form-item>
        <el-divider content-position="left" style="margin: 8px 0;">{{ $tt("多指标目标") }}</el-divider>
        <el-form-item :label="$tf('新客户数目标(个)')">
          <el-input-number v-model="empDialog.form.newCustomerTarget" :step="5" :min="0" />
          <span style="color:#909399;font-size:12px;margin-left:8px;">
            年度内由该销售员负责的新增客户数量目标
          </span>
        </el-form-item>
        <el-form-item :label="$tf('订单数目标(单)')">
          <el-input-number v-model="empDialog.form.orderCountTarget" :step="5" :min="0" />
          <span style="color:#909399;font-size:12px;margin-left:8px;">
            年度内该销售员已生效/已结案的销售订单数量目标
          </span>
        </el-form-item>
        <el-form-item :label="$tf('备注')">
          <el-input v-model="empDialog.form.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="empDialog.visible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('save')" @click="saveEmployee">{{ $tt("保存") }}</el-button>
      </template>
    </el-dialog>

    <!-- 列设置抽屉（按当前 tab 显示对应表的列） -->
    <ColumnSettingDrawer v-model="columnSettingVisible" :page-key="'crm-sales-target'" :columns="drawerColumns" />
  </div>
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import TargetBreakdownPanel from '@/components/crm/TargetBreakdownPanel.vue'
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Warning, Setting } from '@element-plus/icons-vue'
import * as salesTargetApi from '@/api/crm/salesTarget'
import { listAllEmployee } from '@/api/basedata/employee'
import { listAllDepartment } from '@/api/basedata/department'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
import AuditColumns from '@/components/AuditColumns.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { useTableSort } from '@/composables/useTableSort'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('crm:sales-target')
// 公司目标 / 销售员目标两个表格各自独立排序状态，互不干扰
const { handleSortChange, getSortParams: getCompanySortParams } = useTableSort()
const { handleSortChange: handleEmpSortChange, getSortParams: getEmpSortParams } = useTableSort()

// === 列设置（三个 tab 各自的表格列定义，共用一个 pageKey，同 prop 列共享显隐/固定/顺序配置） ===
// 公司年度目标表
const companyColumns = [
  { prop: 'targetYear', label: '年度', width: 100, sortable: true },
  { prop: 'annualAmount', label: '年度目标', width: 160, type: 'format', format: (row) => fmtMoney(row.annualAmount) },
  { prop: 'growthRate', label: '增长率(%)', width: 120, sortable: true },
  { prop: 'autoGenerateNextYear', label: '自动生成下年', width: 130, type: 'tag', tagType: (row) => row.autoGenerateNextYear === 1 ? 'success' : 'info', tagText: (row) => row.autoGenerateNextYear === 1 ? '是' : '否' },
  { prop: 'allocatedAmount', label: '已分配', width: 140, type: 'format', format: (row) => fmtMoney(row.allocatedAmount) },
  { prop: 'unallocatedAmount', label: '未分配', width: 140, type: 'custom' },
  { prop: 'allocatedCount', label: '分配人数', width: 100 },
  { prop: 'status', label: '状态', width: 100, sortable: true, type: 'tag', tagType: (row) => row.status === '启用' ? 'success' : 'info' }
]
// 销售员目标分配表（含方案A：新增4项指标；实际列 label 随季度筛选动态变化，label 用函数惰性求值）
const employeeColumns = [
  { prop: 'targetYear', label: '年度', width: 100, sortable: true },
  { prop: 'employeeName', label: '销售员', width: 120, sortable: true },
  { prop: 'deptName', label: '部门', width: 120, sortable: true },
  { prop: 'annualTargetAmount', label: '年度目标', width: 140, sortable: true, type: 'format', format: (row) => fmtMoney(row.annualTargetAmount) },
  { prop: 'q1Target', label: 'Q1', width: 90, sortable: true, type: 'format', format: (row) => fmtMoney(row.q1Target) },
  { prop: 'q2Target', label: 'Q2', width: 90, sortable: true, type: 'format', format: (row) => fmtMoney(row.q2Target) },
  { prop: 'q3Target', label: 'Q3', width: 90, sortable: true, type: 'format', format: (row) => fmtMoney(row.q3Target) },
  { prop: 'q4Target', label: 'Q4', width: 90, sortable: true, type: 'format', format: (row) => fmtMoney(row.q4Target) },
  { prop: 'newCustomerTarget', label: '新客户目标', width: 110, align: 'center', sortable: true, type: 'format', format: (row) => row.newCustomerTarget ?? 0 },
  { prop: 'newCustomerActual', label: () => `新客户实际${quarterSuffix.value}`, width: 115, align: 'center', type: 'tag', tagType: (row) => (dispNewActual(row)) >= (row.newCustomerTarget ?? 1) ? 'success' : 'info', tagText: (row) => dispNewActual(row) },
  { prop: 'orderCountTarget', label: '订单数目标', width: 110, align: 'center', sortable: true, type: 'format', format: (row) => row.orderCountTarget ?? 0 },
  { prop: 'orderCountActual', label: () => `订单数实际${quarterSuffix.value}`, width: 115, align: 'center', type: 'tag', tagType: (row) => (dispOrderActual(row)) >= (row.orderCountTarget ?? 1) ? 'success' : 'warning', tagText: (row) => dispOrderActual(row) },
  { prop: 'actualAmount', label: () => `实际完成${quarterSuffix.value}`, width: 150, type: 'custom' },
  { prop: 'completionRate', label: () => `完成率${quarterSuffix.value}`, width: 140, type: 'custom' },
  { prop: 'allocationStatus', label: '状态', width: 90, type: 'tag', tagType: (row) => row.allocationStatus === '已分配' ? 'success' : 'warning' }
]
// 完成情况排名表
const rankingColumns = [
  { prop: 'rank', label: '名次', width: 80, type: 'tag', tagType: (row) => rankType(row.rank) },
  { prop: 'employeeName', label: '销售员', width: 120 },
  { prop: 'deptName', label: '部门', width: 120 },
  { prop: 'annualTarget', label: '年度目标', width: 140, type: 'format', format: (row) => fmtMoney(row.annualTarget) },
  { prop: 'newCustomerTarget', label: '新客户目标', width: 110, align: 'center', type: 'format', format: (row) => row.newCustomerTarget ?? 0 },
  { prop: 'newCustomerActual', label: '新客户实际', width: 110, align: 'center', type: 'tag', tagType: (row) => (row.newCustomerActual ?? 0) >= (row.newCustomerTarget ?? 1) ? 'success' : 'info', tagText: (row) => row.newCustomerActual ?? 0 },
  { prop: 'orderCountTarget', label: '订单数目标', width: 110, align: 'center', type: 'format', format: (row) => row.orderCountTarget ?? 0 },
  { prop: 'orderCountActual', label: '订单数实际', width: 110, align: 'center', type: 'tag', tagType: (row) => (row.orderCountActual ?? 0) >= (row.orderCountTarget ?? 1) ? 'success' : 'warning', tagText: (row) => row.orderCountActual ?? 0 },
  { prop: 'actualAmount', label: '实际完成', width: 140, type: 'format', format: (row) => fmtMoney(row.actualAmount) },
  { prop: 'completionRate', label: '完成率', width: 180, type: 'custom' },
  { prop: 'allocationStatus', label: '状态', width: 90 }
]
const { columnSettingVisible, openColumnSetting, isColVisible, getColFixed, configVersion } = useColumnSetting('crm-sales-target', companyColumns)
const { sortedColumns: companySortedColumns } = useColumnSetting('crm-sales-target', companyColumns)
const { sortedColumns: employeeSortedColumns } = useColumnSetting('crm-sales-target', employeeColumns)
const { sortedColumns: rankingSortedColumns } = useColumnSetting('crm-sales-target', rankingColumns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）；label 为函数时求值为字符串
const resolveLabel = (col) => typeof col.label === 'function' ? col.label() : col.label
const companyVisibleColumns = computed(() => companySortedColumns.value.filter(col => isColVisible(col.prop)).map(col => ({ ...col, label: resolveLabel(col) })))
const employeeVisibleColumns = computed(() => employeeSortedColumns.value.filter(col => isColVisible(col.prop)).map(col => ({ ...col, label: resolveLabel(col) })))
const rankingVisibleColumns = computed(() => rankingSortedColumns.value.filter(col => isColVisible(col.prop)).map(col => ({ ...col, label: resolveLabel(col) })))
// 抽屉列集随当前 tab 切换（抽屉打开时按当前列集初始化）
const drawerColumns = computed(() => {
  const cols = activeTab.value === 'company' ? companyColumns : activeTab.value === 'employee' ? employeeColumns : rankingColumns
  return cols.map(col => ({ ...col, label: resolveLabel(col) }))
})

const activeTab = ref('company')
// 顶层页签：目标分配（原有内容，含公司目标/销售员分配/排名） / 五级分解
const mainTab = ref('assign')
const currentYear = new Date().getFullYear()

// 员工档案下拉选项 + 部门映射
const employeeOptions = ref([])
const deptMap = ref({})
async function loadEmployeeOptions() {
  try {
    // 先加载部门列表，建立 deptId → deptName 映射
    const deptRes = await listAllDepartment()
    const deptList = Array.isArray(deptRes) ? deptRes : (deptRes?.records || [])
    const map = {}
    deptList.forEach(d => { map[d.deptId] = d.deptName })
    deptMap.value = map

    const res = await listAllEmployee({ status: '在职' })
    const list = Array.isArray(res) ? res : (res?.records || res?.list || [])
    // 为每个员工补充 deptName（Employee实体只有deptId，无deptName）
    list.forEach(emp => {
      emp.deptName = deptMap.value[emp.deptId] || ''
    })
    employeeOptions.value = list
  } catch (e) {
    console.warn('加载员工档案失败', e)
    employeeOptions.value = []
  }
}
function onEmployeeChange(empId) {
  const emp = employeeOptions.value.find(e => e.employeeId === empId)
  if (emp) {
    empDialog.form.employeeName = emp.employeeName
    empDialog.form.deptName = emp.deptName || ''
    empDialog.form.deptId = emp.deptId || null
  }
}

// 顶部看板
const dashboard = ref({})
async function loadDashboard() {
  try {
    const res = await salesTargetApi.getDashboard(currentYear)
    // 拦截器已解包，res 即为 data 字段
    dashboard.value = res || {}
  } catch (e) {
    console.warn('加载看板失败', e)
  }
}

// 公司年度目标
const companyList = ref([])
const companyLoading = ref(false)
const companyQuery = reactive({ year: null })
async function loadCompany() {
  companyLoading.value = true
  try {
    const res = await salesTargetApi.pageCompany({ current: 1, size: 100, year: companyQuery.year, ...getCompanySortParams()})
    // 拦截器已解包，res 即为 { records, total, current, size }
    companyList.value = (res && res.records) || []
  } catch (e) {
    console.warn('加载公司目标失败', e)
    companyList.value = []
  } finally {
    companyLoading.value = false
  }
}

const companyDialog = reactive({
  visible: false,
  id: null,
  form: {
    targetYear: currentYear,
    annualAmount: 20000000,
    growthRate: 30,
    autoGenerateNextYear: 1,
    status: '启用',
    description: ''
  }
})
function openCompanyDialog(row) {
  if (row) {
    companyDialog.id = row.id
    companyDialog.form = { ...row }
  } else {
    companyDialog.id = null
    companyDialog.form = {
      targetYear: currentYear,
      annualAmount: 20000000,
      growthRate: 30,
      autoGenerateNextYear: 1,
      status: '启用',
      description: ''
    }
  }
  companyDialog.visible = true
}
async function saveCompany() {
  if (companyDialog.id) {
    await salesTargetApi.updateCompany(companyDialog.id, companyDialog.form)
  } else {
    await salesTargetApi.createCompany(companyDialog.form)
  }
  ElMessage.success(tt('保存成功'))
  companyDialog.visible = false
  loadCompany()
  loadDashboard()
}

// 生成下年度目标（按增长率递增）
async function handleGenerateNextYear(row) {
  try {
    await ElMessageBox.confirm(
      `${tt("确认按【")}${row.targetYear}${tt("】年度目标和增长率 ")}${row.growthRate}${tt("% 自动生成 ")}${Number(row.targetYear) + 1}${tt(" 年度目标？")}`,
      tt('生成下年度'),
      { type: 'warning', confirmButtonText: tt('确认生成'), cancelButtonText: tt('取消') }
    )
  } catch (e) {
    return
  }
  try {
    await salesTargetApi.generateNextYear(row.id)
    ElMessage.success(tt('生成成功'))
    loadCompany()
    loadDashboard()
  } catch (e) {
    // 拦截器已弹错误提示
  }
}

// 删除公司年度目标
async function handleDeleteCompany(row) {
  try {
    await ElMessageBox.confirm(
      `${tt("确认删除【")}${row.targetYear}${tt("】年度目标？删除后不可恢复。")}`,
      tt('删除确认'),
      { type: 'warning', confirmButtonText: tt('确认删除'), cancelButtonText: tt('取消') }
    )
  } catch (e) {
    return
  }
  try {
    await salesTargetApi.deleteCompany(row.id)
    ElMessage.success(tt('删除成功'))
    loadCompany()
    loadDashboard()
  } catch (e) {
    // 拦截器已弹错误提示
  }
}

// 销售员目标
const employeeList = ref([])
const empLoading = ref(false)
const empQuery = reactive({ year: null, quarter: null, allocationStatus: null })
async function loadEmployee() {
  empLoading.value = true
  try {
    const res = await salesTargetApi.pageEmployee({
      current: 1, size: 500, year: empQuery.year, allocationStatus: empQuery.allocationStatus,
      quarter: empQuery.quarter, ...getEmpSortParams()
    })
    employeeList.value = (res && res.records) || []
  } catch (e) {
    console.warn('加载销售员目标失败', e)
    employeeList.value = []
  } finally {
    empLoading.value = false
  }
}

// ---- 季度口径展示：选中 Q1-Q4 时列表/汇总展示季度区间统计值，否则展示年度快照 ----
const quarterSuffix = computed(() => (empQuery.quarter ? `(Q${empQuery.quarter})` : ''))
function dispActualAmount(row) {
  return empQuery.quarter ? (row.quarterActualAmount ?? 0) : row.actualAmount
}
function dispOrderActual(row) {
  return empQuery.quarter ? (row.quarterOrderCountActual ?? 0) : (row.orderCountActual ?? 0)
}
function dispNewActual(row) {
  return empQuery.quarter ? (row.quarterNewCustomerActual ?? 0) : (row.newCustomerActual ?? 0)
}
function dispRate(row) {
  return empQuery.quarter ? (row.quarterCompletionRate ?? 0) : row.completionRate
}
function dispTargetAmount(row) {
  return empQuery.quarter ? (row.quarterTargetAmount ?? 0) : (row.annualTargetAmount ?? 0)
}

const empDialog = reactive({
  visible: false,
  id: null,
  form: {
    targetYear: currentYear,
    employeeId: null,
    employeeName: '',
    deptName: '',
    annualTargetAmount: 1000000,
    q1Target: 0,
    q2Target: 0,
    q3Target: 0,
    q4Target: 0,
    newCustomerTarget: 10,
    newCustomerActual: 0,
    orderCountTarget: 20,
    orderCountActual: 0,
    remark: ''
  }
})
function openEmployeeDialog(row) {
  if (row) {
    empDialog.id = row.id
    empDialog.form = { ...row }
  } else {
    empDialog.id = null
    empDialog.form = {
      targetYear: currentYear,
      employeeId: null,
      employeeName: '',
      deptName: '',
      annualTargetAmount: 1000000,
      q1Target: 0,
      q2Target: 0,
      q3Target: 0,
      q4Target: 0,
      newCustomerTarget: 10,
      newCustomerActual: 0,
      orderCountTarget: 20,
      orderCountActual: 0,
      remark: ''
    }
  }
  empDialog.visible = true
}

// 刷新实际完成指标（年度口径写回快照）
async function handleRefreshActual(row) {
  try {
    await salesTargetApi.refreshActual(row.targetYear, row.employeeId, empQuery.quarter || null)
    ElMessage.success(tt('实际完成指标已刷新'))
    loadEmployee()
    loadDashboard()
  } catch (e) {
    // 拦截器已弹错误提示
  }
}

// 删除销售员目标分配
async function handleDeleteEmployee(row) {
  try {
    await ElMessageBox.confirm(
      `${tt("确认删除【")}${row.targetYear}${tt("】年【")}${row.employeeName}${tt("】的目标分配？删除后不可恢复。")}`,
      tt('删除确认'),
      { type: 'warning', confirmButtonText: tt('确认删除'), cancelButtonText: tt('取消') }
    )
  } catch (e) {
    return
  }
  try {
    await salesTargetApi.deleteEmployee(row.id)
    ElMessage.success(tt('删除成功'))
    loadEmployee()
    loadDashboard()
  } catch (e) {
    // 拦截器已弹错误提示
  }
}
async function saveEmployee() {
  // 提交前空值保护：int 列 null→0
  const form = empDialog.form
  if (form.newCustomerTarget == null) form.newCustomerTarget = 0
  if (form.newCustomerActual == null) form.newCustomerActual = 0
  if (form.orderCountTarget == null) form.orderCountTarget = 0
  if (form.orderCountActual == null) form.orderCountActual = 0
  if (empDialog.id) {
    await salesTargetApi.updateEmployee(empDialog.id, form)
  } else {
    await salesTargetApi.allocateEmployee(form)
  }
  ElMessage.success(tt('保存成功'))
  empDialog.visible = false
  loadEmployee()
  loadDashboard()
}

// ---- 列表底部汇总行（操作手册 §16：按筛选条件汇总所有销售员的目标/实际；选中季度时实际列按季度口径汇总） ----
function employeeSummary({ columns, data }) {
  if (!data || !data.length) return columns.map(() => '')
  const q = !!empQuery.quarter
  // 季度口径下实际值取 quarter* 字段；目标金额取对应季度目标
  const fieldMap = {
    actualAmount: q ? 'quarterActualAmount' : 'actualAmount',
    newCustomerActual: q ? 'quarterNewCustomerActual' : 'newCustomerActual',
    orderCountActual: q ? 'quarterOrderCountActual' : 'orderCountActual'
  }
  const sums = []
  columns.forEach((col, index) => {
    const prop = col.property
    if (index === 0) { sums[index] = `合计（${data.length} 人）`; return }
    const moneyProps = ['annualTargetAmount', 'q1Target', 'q2Target', 'q3Target', 'q4Target', 'actualAmount']
    if (moneyProps.includes(prop)) {
      const key = fieldMap[prop] || prop
      const total = data.reduce((s, r) => s + (Number(r[key]) || 0), 0)
      sums[index] = fmtMoney(total)
      return
    }
    const countProps = ['newCustomerTarget', 'newCustomerActual', 'orderCountTarget', 'orderCountActual']
    if (countProps.includes(prop)) {
      const key = fieldMap[prop] || prop
      sums[index] = data.reduce((s, r) => s + (Number(r[key]) || 0), 0)
      return
    }
    if (prop === 'completionRate') {
      const tKey = q ? 'quarterTargetAmount' : 'annualTargetAmount'
      const aKey = q ? 'quarterActualAmount' : 'actualAmount'
      const ta = data.reduce((s, r) => s + (Number(r[tKey]) || 0), 0)
      const aa = data.reduce((s, r) => s + (Number(r[aKey]) || 0), 0)
      const rate = ta > 0 ? (aa * 100 / ta).toFixed(2) + '%' : '-'
      sums[index] = rate
      return
    }
    sums[index] = ''
  })
  return sums
}
function rankingSummary({ columns, data }) {
  if (!data || !data.length) return columns.map(() => '')
  const sums = []
  columns.forEach((col, index) => {
    const prop = col.property
    if (index === 0) { sums[index] = `TOP ${data.length} 合计`; return }
    if (prop === 'annualTarget' || prop === 'actualAmount') {
      const total = data.reduce((s, r) => s + (Number(r[prop]) || 0), 0)
      sums[index] = fmtMoney(total)
      return
    }
    if (['newCustomerTarget', 'newCustomerActual', 'orderCountTarget', 'orderCountActual'].includes(prop)) {
      sums[index] = data.reduce((s, r) => s + (Number(r[prop]) || 0), 0)
      return
    }
    if (prop === 'completionRate') {
      const ta = data.reduce((s, r) => s + (Number(r.annualTarget) || 0), 0)
      const aa = data.reduce((s, r) => s + (Number(r.actualAmount) || 0), 0)
      sums[index] = ta > 0 ? (aa * 100 / ta).toFixed(2) + '%' : '-'
      return
    }
    sums[index] = ''
  })
  return sums
}

// 完成情况排名
const rankingList = ref([])
const rankingLoading = ref(false)
const rankingYear = ref(currentYear)
async function loadRanking() {
  rankingLoading.value = true
  try {
    const res = await salesTargetApi.getRanking(rankingYear.value)
    rankingList.value = res || []
  } catch (e) {
    console.warn('加载排名失败', e)
    rankingList.value = []
  } finally {
    rankingLoading.value = false
  }
}

// 工具函数
function fmtMoney(val) {
  if (val == null || val === '') return '-'
  const num = Number(val)
  if (isNaN(num)) return '-'
  // 万元展示
  if (Math.abs(num) >= 10000) {
    return (num / 10000).toFixed(2) + ' 万'
  }
  return num.toFixed(2)
}
function progressColor(rate) {
  const r = Number(rate || 0)
  if (r >= 100) return '#67C23A'
  if (r >= 60) return '#E6A23C'
  return '#F56C6C'
}
function rankType(rank) {
  if (rank === 1) return 'danger'
  if (rank === 2) return 'warning'
  if (rank === 3) return 'success'
  return 'info'
}

onMounted(() => {
  // 数据看板穿透：URL query year 注入年度筛选（无 query 时行为完全不变）
  const route = useRoute()
  const q = route.query || {}
  if (q.year) {
    companyQuery.year = Number(q.year)
    empQuery.year = Number(q.year)
    ElMessage.info(tt('已应用数据看板穿透筛选条件'))
  }
  loadDashboard()
  loadCompany()
  loadEmployee()
  loadRanking()
  loadEmployeeOptions()
})
useAutoRefresh(() => {
  loadDashboard()
  loadCompany()
  loadEmployee()
  loadRanking()
  loadEmployeeOptions()
})
</script>

<style scoped lang="scss">
.dashboard-card { background: linear-gradient(135deg, #f5f7fa 0%, #e4edf5 100%); }
.quarter-hint {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
}
.dashboard-row {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}
.stat-item {
  flex: 1;
  min-width: 130px;
  text-align: center;
  padding: 8px;
  border-right: 1px dashed #dcdfe6;
  &:last-child { border-right: none; }
}
.stat-label { color: #909399; font-size: 13px; margin-bottom: 4px; }
.stat-value { font-size: 20px; font-weight: 600; color: #303133; }
.toolbar { margin-bottom: 12px; display: flex; align-items: center; gap: 8px; }
</style>
