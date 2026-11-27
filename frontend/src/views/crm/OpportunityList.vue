<template>
  <div class="opp-page">
    <!-- 顶部标题 + 操作栏 -->
    <div class="page-header">
      <div class="header-left">
        <div class="title-group">
          <div class="page-title">
            商机管理
            <span class="title-badge">OPPORTUNITY</span>
          </div>
          <div class="page-sub">{{ $tt("销售机会全流程跟踪 · 阶段化推进提升赢单率") }}</div>
        </div>
      </div>
      <div class="header-actions">
        <el-button type="primary" class="btn-primary-glow" v-permission="perm('add')" @click="handleAdd">
          <el-icon><Plus /></el-icon> 新增商机
        </el-button>
        <el-button type="success" :icon="Download" v-permission="perm('export')" @click="handleExport">{{ $tt("导出") }}</el-button>
        <el-upload
          :show-file-list="false"
          :before-upload="handleImport"
          accept=".xlsx,.xls"
          style="margin-left: 10px;"
        >
          <el-button type="warning" :icon="Upload" v-permission="perm('import')">{{ $tt("导入") }}</el-button>
        </el-upload>
      </div>
    </div>

    <!-- 数据概览卡片 -->
    <div class="stat-cards">
      <div class="stat-card" v-for="stat in statItems" :key="stat.key" @click="handleStatClick(stat.key)">
        <div class="stat-icon" :class="stat.type">
          <el-icon><component :is="stat.icon" /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stat.value }}</div>
          <div class="stat-label">{{ $tf(stat.label) }}</div>
        </div>
        <div class="stat-glow" :class="stat.type"></div>
      </div>
    </div>

    <!-- 主内容区 -->
    <div class="content-card">
      <!-- 搜索 + 状态 tab -->
      <div class="search-bar">
        <div class="status-tabs">
          <div
            v-for="tab in statusTabs"
            :key="tab.value"
            :class="['status-tab', { active: activeTab === tab.value }]"
            @click="switchTab(tab.value)"
          >
            <span class="tab-text">{{ $tf(tab.label) }}</span>
            <span v-if="tab.count > 0" class="tab-count">{{ tab.count }}</span>
          </div>
        </div>
        <div class="search-right">
          <el-input v-model="search.keyword" :placeholder="$tt('商机编号/名称/客户名称')" clearable class="search-input" @keyup.enter="handleSearch">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="search.salesStage" :placeholder="$tt('销售阶段')" style="width:130px;" clearable @change="handleSearch">
            <el-option v-for="s in salesStageOptions" :key="s" :label="s" :value="s" />
          </el-select>
          <!-- 自定义字段：勾了「可搜索」的字段 → 各一个搜索框（无字段时渲染 0 个节点） -->
          <CustomFieldSearchBar :fields="cf.searchFields.value" :model="cf.values" @change="cf.setValue" @search="handleSearch" />
          <el-button type="primary" v-permission="perm('query')" @click="handleSearch">{{ $tt("搜索") }}</el-button>
          <el-button v-permission="perm('reset')" @click="handleReset">{{ $tt("重置") }}</el-button>
          <el-tooltip :content="$tt('列设置')" placement="top">
            <el-button :icon="Setting" circle v-permission="perm('columnSetting')" @click="openColumnSetting" />
          </el-tooltip>
        </div>
      </div>

      <!-- 表格（border 必开：Element Plus 只有带 border 的表格才允许拖拽列宽） -->
      <el-table ref="tableRef" :data="opportunities" v-loading="loading" class="tech-table" border :header-cell-style="headerCellStyle" :key="configVersion" @sort-change="onSortChange" @header-dragend="onHeaderDragend">
        <el-table-column type="index" label="#" width="55" />
        <!-- 动态列渲染：列顺序由列设置拖拽配置驱动（sortedColumns），单元格按 col.type 分支渲染 -->
        <template v-for="col in visibleSortedColumns" :key="col.prop">
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
              <div v-else-if="col.type === 'custom' && col.prop === 'opportunityNo'" class="order-no-cell">
                <span class="order-no-text" @click="handleEdit(row)">{{ row.opportunityNo }}</span>
                <el-icon class="link-icon"><Link /></el-icon>
              </div>
              <div v-else-if="col.type === 'custom' && col.prop === 'customerName'" class="customer-cell">
                <div class="customer-avatar">{{ (row.customerName || '?').charAt(0) }}</div>
                <div class="customer-info">
                  <div class="customer-name">{{ row.customerName }}</div>
                </div>
              </div>
              <span v-else-if="col.type === 'custom' && col.prop === 'estimatedAmount'" class="amount-text">¥{{ formatMoney(row.estimatedAmount) }}</span>
              <div v-else-if="col.type === 'custom' && col.prop === 'probability'">
                <el-progress :percentage="row.probability || 0" :show-text="false" :stroke-width="6" :color="probColor(row.probability)" style="width:50px;display:inline-block;" />
                <span style="margin-left:4px;font-size:12px;color:#606266;">{{ row.probability || 0 }}%</span>
              </div>
              <div v-else-if="col.type === 'custom' && col.prop === 'closeStatus'" class="status-cell">
                <span :class="['status-dot', row.isClosed === 1 ? 'dot-info' : 'dot-success']"></span>
                <el-tag size="small" :type="row.isClosed === 1 ? 'info' : 'success'" effect="plain">
                  {{ row.isClosed === 1 ? '已关闭' : '进行中' }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
        </template>
        <AuditColumns />
        <!-- 操作列：编辑/转销售订单/更改阶段/关闭/删除 -->
        <el-table-column :label="$tf('操作')" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" v-permission="perm('edit')" @click="handleEdit(row)">{{ $tt("编辑") }}</el-button>
            <el-button type="success" link size="small" :disabled="row.isClosed === 1" v-permission="perm('convert')" @click="handleToSaleOrder(row)">{{ $tt("转销售订单") }}</el-button>
            <el-dropdown trigger="click" @command="(cmd) => handleRowCommand(cmd, row)">
              <el-button type="primary" link size="small">{{ $tt("更多") }}<el-icon style="margin-left:2px;"><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="stage" :disabled="row.isClosed === 1">{{ $tt("更改阶段") }}</el-dropdown-item>
                  <el-dropdown-item command="close" :disabled="row.isClosed === 1">{{ $tt("关闭商机") }}</el-dropdown-item>
                  <el-dropdown-item command="delete" divided>{{ $tt("删除") }}</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrap">
        <div class="pagination-info">共 {{ page.total }} 条记录</div>
        <el-pagination
          v-model:current-page="page.current"
          v-model:page-size="page.size"
          :page-sizes="[20, 50, 100]"
          :total="page.total"
          layout="prev, pager, next, sizes, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑商机' : '新增商机'" width="1100px" :close-on-click-modal="false" class="tech-dialog">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px">
        <el-divider content-position="left">{{ $tt("基本信息") }}</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('商机编号')">
              <el-input v-model="formData.opportunityNo" :disabled="docCodeDisabled && !isEdit" :placeholder="docCodePlaceholder" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item :label="$tf('商机名称')" prop="opportunityName">
              <el-input v-model="formData.opportunityName" :placeholder="$tt('如 XX产品采购项目')" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('销售阶段')">
              <el-select v-model="formData.salesStage" style="width:100%" @change="onStageChange">
                <el-option v-for="s in salesStageOptions" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('客户')" prop="customerId">
              <el-select v-model="formData.customerId" :placeholder="$tt('请选择客户')" filterable clearable style="width:100%" @change="onCustomerChange">
                <el-option v-for="opt in withFallbackOption(customerOptions, 'customerId', 'customerName', formData)" :key="opt.value" :label="$tf(opt.label)" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item :label="$tf('客户名称')">
              <el-input v-model="formData.customerName" :placeholder="$tt('选择客户后自动填充')" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="$tf('客户行业')">
              <el-tag v-if="formData.customerIndustry" type="info" size="default">{{ formData.customerIndustry }}</el-tag>
              <span v-else style="color:#999;">{{ $tt("选择客户后带出") }}</span>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('联系人')">
              <el-input v-model="formData.contactPerson" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('联系电话')">
              <el-input v-model="formData.contactPhone" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('预计成交日期')">
              <el-date-picker v-model="formData.expectedCloseDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('产品大类')">
              <el-select v-model="formData.productCategory" filterable clearable :placeholder="$tt('请选择产品大类')" style="width:100%">
                <el-option v-for="c in materialCategoryOptions" :key="c.categoryId" :label="c.categoryName" :value="c.categoryName" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('预计金额')">
              <el-input-number v-model="formData.estimatedAmount" :min="0" :precision="2" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('赢率(%)')">
              <el-input-number v-model="formData.probability" :min="0" :max="100" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('分配销售员')">
              <el-select v-model="formData.assignedSalesId" :placeholder="$tt('请选择员工')" filterable clearable style="width:100%">
                <el-option v-for="opt in employeeOptions" :key="opt.value" :label="$tf(opt.label)" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('分配部门')">
              <el-select v-model="formData.assignedDeptId" :placeholder="$tt('请选择部门')" filterable clearable style="width:100%">
                <el-option v-for="opt in deptOptions" :key="opt.value" :label="$tf(opt.label)" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('下次跟进日期')">
              <el-date-picker v-model="formData.nextFollowUpDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('竞争对手')">
              <el-input v-model="formData.competitor" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">{{ $tt("涉及物料明细") }}</el-divider>
        <div style="margin-bottom:12px;display:flex;justify-content:space-between;align-items:center;">
          <span style="font-size:12px;color:#909399;">{{ $tt("选择商机涉及的物料，可填写预计数量与单价，金额自动累计到「预计金额」") }}</span>
          <div>
            <el-button type="primary" size="small" v-permission="perm('pick')" @click="openMaterialPicker" :disabled="!isEditable">
              <el-icon><Plus /></el-icon> 从物料库选择
            </el-button>
            <el-button text type="primary" size="small" v-permission="perm('addRow')" @click="addMaterialRow" :disabled="!isEditable">{{ $tt("+ 手动新增行") }}</el-button>
          </div>
        </div>
        <el-table :data="formData.materialItems" border size="small" show-summary :summary-method="getMaterialSummary">
          <el-table-column type="index" :label="$tf('行')" width="45" />
          <el-table-column :label="$tf('图片')" width="70" align="center">
            <template #default="{ row }">
              <el-image
                v-if="row.materialMainImage"
                :src="row.materialMainImage"
                :preview-src-list="[row.materialMainImage]"
                fit="cover"
                preview-teleported
                hide-on-click-modal
                style="width:40px;height:40px;border-radius:4px;"
              />
              <span v-else style="color:#c0c4cc;font-size:12px;">{{ $tt("无图") }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="$tf('物料')" min-width="220">
            <template #default="{ row }">
              <el-select
                v-model="row.materialId"
                filterable
                :placeholder="materialSelectPlaceholder"
                :disabled="!isEditable"
                style="width:100%"
                :filter-method="(val) => materialFilterMethod(val, row)"
                class="table-select"
                size="small"
                :teleported="true"
                :popper-options="{ strategy: 'fixed' }"
                popper-class="material-select-popper"
                @change="(v) => onMaterialSelect(row, v)"
              >
                <el-option
                  v-for="m in row._filteredMaterials || allMaterials"
                  :key="m.materialId"
                  :label="`${m.materialCode} ${m.materialName}`"
                  :value="m.materialId"
                >
                  <span class="opt-code">{{ m.materialCode }}</span>
                  <span class="opt-name">{{ m.materialName }}</span>
                  <span v-for="cf in mcf.dropText(m)" :key="cf.key" class="opt-cf">{{ cf.label }}：{{ cf.value }}</span>
                  <span v-if="m.model" class="opt-model">{{ m.model }}</span>
                </el-option>
              </el-select>
            </template>
          </el-table-column>
          <el-table-column prop="specification" :label="$tf('规格')" width="110" sortable="custom"/>
          <el-table-column prop="unit" :label="$tf('单位')" width="60" sortable="custom"/>
          <el-table-column :label="$tf('数量')" width="100">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :controls="false" :min="0" :disabled="!isEditable" style="width:100%" @change="calcMaterialRow(row)" />
            </template>
          </el-table-column>
          <el-table-column :label="$tf('单价')" width="110">
            <template #default="{ row }">
              <el-input-number v-model="row.unitPrice" :controls="false" :precision="4" :min="0" :disabled="!isEditable" style="width:100%" @change="calcMaterialRow(row)" />
            </template>
          </el-table-column>
          <el-table-column :label="$tf('金额')" width="110" align="right">
            <template #default="{ row }">{{ Number(row.amount || 0).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column :label="$tf('备注')" width="140">
            <template #default="{ row }">
              <el-input v-model="row.remark" size="small" :disabled="!isEditable" :placeholder="$tt('备注')" />
            </template>
          </el-table-column>
          <el-table-column :label="$tf('操作')" width="60" v-if="isEditable">
            <template #default="{ $index }">
              <el-button text type="danger" size="small" v-permission="perm('deleteRow')" @click="formData.materialItems.splice($index, 1)">{{ $tt("删") }}</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-divider content-position="left">{{ $tt("其他") }}</el-divider>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item :label="$tf('备注')">
              <el-input v-model="formData.remark" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="dialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('save')" @click="handleSave" :loading="saving" v-if="isEditable">{{ $tt("保存") }}</el-button>
        <el-alert v-if="!isEditable" :title="$tt('当前状态不允许编辑')" type="warning" :closable="false" show-icon style="margin-right:auto;" />
      </template>
    </el-dialog>

    <!-- 物料库选择弹窗（通用组件：内置自定义字段搜索、全字段/自定义字段列、列设置全局共享 MaterialPicker） -->
    <MaterialPickerDialog v-model="materialPickerVisible" @confirm="onMaterialPicked" />

    <!-- 更改阶段弹窗 -->
    <el-dialog v-model="stageDialogVisible" :title="$tf('更改销售阶段')" width="500px">
      <el-form :model="stageForm" label-width="100px">
        <el-form-item :label="$tf('商机名称')">
          <span>{{ stageForm.opportunityName }}</span>
        </el-form-item>
        <el-form-item :label="$tf('销售阶段')">
          <el-select v-model="stageForm.salesStage" style="width:100%" @change="onStageDialogChange">
            <el-option v-for="s in salesStageOptions" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="stageForm.salesStage === '成交'" :label="$tf('赢单原因')">
          <el-input v-model="stageForm.winReason" type="textarea" :rows="3" :placeholder="$tt('请输入赢单原因')" />
        </el-form-item>
        <template v-if="stageForm.salesStage === '已关闭'">
          <el-form-item :label="$tf('输赢状态')">
            <el-radio-group v-model="stageForm.won">
              <el-radio :label="true">{{ $tt("赢单") }}</el-radio>
              <el-radio :label="false">{{ $tt("丢单") }}</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item :label="$tf('原因')">
            <el-input v-model="stageForm.reason" type="textarea" :rows="3" :placeholder="$tt('请输入原因')" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="stageDialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('dlgConfirm')" @click="confirmStageChange" :loading="saving">{{ $tt("确认") }}</el-button>
      </template>
    </el-dialog>

    <!-- 关闭商机弹窗 -->
    <el-dialog v-model="closeDialogVisible" :title="$tf('关闭商机')" width="500px">
      <el-form :model="closeForm" label-width="100px">
        <el-form-item :label="$tf('商机名称')">
          <span>{{ closeForm.opportunityName }}</span>
        </el-form-item>
        <el-form-item :label="$tf('输赢状态')">
          <el-radio-group v-model="closeForm.won">
            <el-radio :label="true">{{ $tt("赢单") }}</el-radio>
            <el-radio :label="false">{{ $tt("丢单") }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="$tf('原因')">
          <el-input v-model="closeForm.reason" type="textarea" :rows="3" :placeholder="$tt('请输入关闭原因')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="closeDialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('dlgConfirm')" @click="confirmClose" :loading="saving">{{ $tt("确认关闭") }}</el-button>
      </template>
    </el-dialog>

    <!-- 列设置抽屉 -->
    <ColumnSettingDrawer v-model="columnSettingVisible" :page-key="'crm-opportunity'" :columns="allColumns" />
  </div>
</template>

<script setup>
import { withFallbackOption } from '@/utils/labelFallback'
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Download, Upload, Link, Setting, ArrowDown } from '@element-plus/icons-vue'
import {
  listOpportunity, getOpportunity, saveOpportunity, updateOpportunity, deleteOpportunity,
  updateOppStage, closeOpportunity, opportunityDashboard
} from '@/api/crm/opportunity'
import { listCrmCustomer } from '@/api/crm/customer'
import { listAllEmployee } from '@/api/basedata/employee'
import { listAllDepartment } from '@/api/basedata/department'
import { pageMaterial } from '@/api/basedata/material'
import { listAllMaterialCategory } from '@/api/basedata/material-category'
import { exportListAsSeen, importData } from '@/utils/exportUtil'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
import { useAutoDocCode } from '@/composables/useAutoDocCode'
import AuditColumns from '@/components/AuditColumns.vue'
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { useTableSort } from '@/composables/useTableSort'
import { useCustomFieldSearch } from '@/composables/useCustomFieldSearch'
import CustomFieldSearchBar from '@/components/CustomFieldSearchBar.vue'
import { useMaterialCustomFields } from '@/composables/useMaterialCustomFields'
import { filterMaterials, buildMaterialPlaceholder } from '@/utils/materialFilter'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('crm:opportunity')
const { handleSortChange, getSortParams } = useTableSort()

// 自定义字段「可搜索」：设置中心勾了可搜索的字段才渲染搜索框（无字段时与改造前完全一致）
const cf = useCustomFieldSearch('opportunity')
// 物料自定义字段：明细行「物料」下拉附加展示（字段名：值），逻辑集中在 composable
const mcf = useMaterialCustomFields()
const router = useRouter()


const onSortChange = (e) => { handleSortChange(e); loadData() }
// === 列设置 ===
// 列定义（用于列设置抽屉+动态渲染）：type 标记单元格渲染分支
const allColumns = [
  { prop: 'opportunityNo', label: '商机编号', minWidth: 140, sortable: true, fixed: 'left', type: 'custom' },
  { prop: 'opportunityName', label: '商机名称', minWidth: 180, tooltip: true, sortable: true },
  { prop: 'customerName', label: '客户', minWidth: 160, sortable: true, type: 'custom' },
  { prop: 'estimatedAmount', label: '预计金额', width: 130, align: 'right', type: 'custom' },
  { prop: 'probability', label: '赢率', width: 80, align: 'center', type: 'custom' },
  { prop: 'salesStage', label: '销售阶段', width: 110, type: 'tag', tagType: (row) => salesStageType(row.salesStage) },
  { prop: 'expectedCloseDate', label: '预计成交日期', width: 120, sortable: true },
  { prop: 'followUpCount', label: '跟进次数', width: 90, align: 'center', sortable: true },
  { prop: 'lastFollowUpDate', label: '最后跟进', width: 110, sortable: true },
  { prop: 'closeStatus', label: '关闭状态', width: 100, type: 'custom' }
]
const { columnSettingVisible, openColumnSetting, isColVisible, getColFixed, configVersion, sortedColumns, onHeaderDragend, applySavedWidths } = useColumnSetting('crm-opportunity', allColumns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))
// 列宽持久化（2026-09-24）：表格带 border 才允许拖拽，拖完的宽度写入本页列配置，下次打开自动套用
const tableRef = ref(null)

// 选项常量 (严格遵循文档 7.3 定义)
const salesStageOptions = ['初步接触', '需求确认', '方案报价', '商务谈判', '成交', '已关闭']
const stageProbabilityMap = { '初步接触': 10, '需求确认': 25, '方案报价': 45, '商务谈判': 70, '成交': 100, '已关闭': 0 }

// 数据状态
const loading = ref(false)
const saving = ref(false)
const opportunities = ref([])
const dashboard = ref({})
const search = reactive({ keyword: '', salesStage: '', isClosed: null })
const page = reactive({ current: 1, size: 20, total: 0, ...getSortParams() })
const activeTab = ref('all')

// 金额格式化
const formatMoney = (val) => {
  if (val == null) return '0.00'
  return Number(val).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 仪表盘统计项
const statItems = computed(() => [
  { key: 'total', label: '总商机数', value: dashboard.value.totalOpportunities || 0, type: 'primary', icon: 'Document' },
  { key: 'open', label: '进行中', value: (dashboard.value.totalOpportunities || 0) - (dashboard.value.closedCount || 0), type: 'success', icon: 'CircleCheck' },
  { key: 'won', label: '赢单数', value: dashboard.value.wonCount || 0, type: 'warning', icon: 'Trophy' },
  { key: 'amount', label: '预计总金额', value: '¥' + formatMoney(dashboard.value.totalEstimatedAmount), type: 'info', icon: 'Money' }
])

const statusTabs = computed(() => [
  { label: '全部', value: 'all', count: dashboard.value.totalOpportunities || 0 },
  { label: '进行中', value: 'open', count: (dashboard.value.totalOpportunities || 0) - (dashboard.value.closedCount || 0) },
  { label: '已成交', value: 'won', count: dashboard.value.wonCount || 0 },
  { label: '已关闭', value: 'closed', count: dashboard.value.closedCount || 0 }
])

// 弹窗状态
const dialogVisible = ref(false)
const isEdit = ref(false)
const { docCodeDisabled, docCodePlaceholder, autoFillDocCodeOnCreate } = useAutoDocCode('OPPORTUNITY')
const formRef = ref(null)
const isEditable = computed(() => !formData.salesStage || !['成交', '已关闭'].includes(formData.salesStage))

const defaultForm = () => ({
  opportunityId: null, opportunityNo: '', opportunityName: '',
  customerId: null, customerName: '', customerIndustry: '', contactPerson: '', contactPhone: '',
  productCategory: '', productDetail: '', estimatedAmount: 0,
  probability: 10, expectedCloseDate: null, salesStage: '初步接触',
  competitor: '', assignedSalesId: null, assignedDeptId: null,
  nextFollowUpDate: null, remark: '', isClosed: 0,
  materialItems: []
})
const formData = reactive(defaultForm())
const formRules = {
  opportunityName: [{ required: true, message: tt('请输入商机名称'), trigger: 'blur' }],
  customerId: [{ required: true, message: tt('请选择客户'), trigger: 'change' }]
}

/** 选择客户后自动带出客户名称、行业、联系人、联系电话 */
function onCustomerChange(customerId) {
  if (!customerId) {
    formData.customerName = ''
    formData.customerIndustry = ''
    return
  }
  const selected = customerOptions.value.find(c => c.value === customerId)
  if (selected) {
    formData.customerName = selected.label
    formData.customerIndustry = selected.industry || ''
    if (selected.contactPerson && !formData.contactPerson) formData.contactPerson = selected.contactPerson
    if (selected.contactPhone && !formData.contactPhone) formData.contactPhone = selected.contactPhone
  }
}

// 阶段变更时自动同步赢率
const onStageChange = (stage) => {
  if (stage && stageProbabilityMap[stage] !== undefined) {
    formData.probability = stageProbabilityMap[stage]
  }
}

// 更改阶段弹窗
const stageDialogVisible = ref(false)
const stageForm = reactive({
  opportunityId: null, opportunityName: '', salesStage: '',
  winReason: '', won: null, reason: ''
})
const onStageDialogChange = (stage) => {
  if (stage !== '成交') stageForm.winReason = ''
  if (stage !== '已关闭') { stageForm.won = null; stageForm.reason = '' }
}

// 关闭商机弹窗
const closeDialogVisible = ref(false)
const closeForm = reactive({
  opportunityId: null, opportunityName: '', won: true, reason: ''
})

// 颜色映射
const salesStageType = (s) => {
  const map = { '初步接触': 'info', '需求确认': '', '方案报价': 'warning', '商务谈判': 'warning', '成交': 'success', '已关闭': 'danger' }
  return map[s] || 'info'
}
const probColor = (p) => {
  if (p >= 70) return '#10b981'
  if (p >= 45) return '#f59e0b'
  if (p >= 25) return '#3b82f6'
  return '#909399'
}

// 加载数据
/**
 * 列表与导出**共用**的查询条件（2026-09-24 全站口径）：
 * 导出必须与列表用同一套条件，抽成一个函数避免两处各写一份导致口径漂移
 * （用户要求"页面上看到什么列，导出就带什么列；导出行 = 当前筛选条件下的全部"）。
 */
const buildQueryParams = () => ({
  keyword: search.keyword || undefined,
  salesStage: search.salesStage || undefined,
  isClosed: search.isClosed ?? undefined,
  ...getSortParams(), ...cf.buildParams()
})

const loadData = async () => {
  loading.value = true
  try {
    const res = await listOpportunity({ page: page.current, size: page.size, ...buildQueryParams() })
    opportunities.value = res.records || []
    page.total = res.total || 0
  } finally {
    loading.value = false
    // 数据到位后再套一次已保存的列宽（表格重建后列对象是新的）
    nextTick(() => applySavedWidths(tableRef))
  }
}

const loadDashboard = async () => {
  try {
    dashboard.value = await opportunityDashboard() || {}
  } catch (e) { /* 忽略仪表盘加载错误 */ }
}

const handleSearch = () => { page.current = 1; loadData() }
const handleReset = () => {
  search.keyword = ''; search.salesStage = ''; search.isClosed = null
  activeTab.value = 'all'
  cf.reset()
  page.current = 1; loadData()
}

const switchTab = (value) => {
  activeTab.value = value
  if (value === 'all') { search.isClosed = null; search.salesStage = '' }
  else if (value === 'open') { search.isClosed = 0; search.salesStage = '' }
  else if (value === 'won') { search.isClosed = null; search.salesStage = '成交' }
  else if (value === 'closed') { search.isClosed = 1; search.salesStage = '' }
  page.current = 1
  loadData()
}

const handleStatClick = (key) => {
  if (key === 'total') {
    search.isClosed = null; search.salesStage = ''; activeTab.value = 'all'
  } else if (key === 'open') {
    search.isClosed = 0; search.salesStage = ''; activeTab.value = 'open'
  } else if (key === 'won') {
    search.salesStage = '成交'; search.isClosed = null; activeTab.value = 'won'
  } else if (key === 'closed') {
    search.isClosed = 1; search.salesStage = ''; activeTab.value = 'closed'
  }
  page.current = 1
  loadData()
}

// 导入导出
const handleExport = async () => {
  try {
    // 页面上看到什么列，导出就带什么列；导出行 = 当前筛选条件下的全部
    await exportListAsSeen({
      entityName: 'opportunity',
      columns: visibleSortedColumns.value,
      fetchPage: listOpportunity,
      params: buildQueryParams(),
      idField: 'opportunityId'
    })
    ElMessage.success(tt('导出成功'))
  } catch (e) {
    // 失败原因由 exportUtil 统一提示
  }
}

const handleImport = async (file) => {
  try {
    const result = await importData('opportunity', file)
    ElMessage.success(`${tt("导入成功: ")}${result.successCount}${tt("条, 失败: ")}${result.failCount}${tt("条")}`)
    loadData()
    loadDashboard()
  } catch (e) {
    ElMessage.error(tt('导入失败'))
  }
  return false
}

// === 物料明细相关 ===
const allMaterials = ref([])
const materialPickerVisible = ref(false)

/** 参与「物料」下拉搜索的自定义字段：勾了「下拉显示」或勾了「可搜索」的字段 */
const materialSearchableFields = computed(() => (mcf.allFields.value || []).filter(f =>
  f && (f.isSearch === 1 || (mcf.enabled.value && f.isDropShow === 1))
))

/** 物料下拉的背景提示文字：声明可搜索范围；没有可搜索的自定义字段时即「选择物料」（零回归） */
const materialSelectPlaceholder = computed(() => buildMaterialPlaceholder(tt('选择物料'), materialSearchableFields.value))

/** 明细行「物料」下拉的输入过滤：编码/名称/规格 ＋ 自定义字段值/字段名（详见 utils/materialFilter.js） */
function materialFilterMethod(val, row) {
  if (!val) { row._filteredMaterials = null; return }
  row._filteredMaterials = filterMaterials(allMaterials.value, val, mcf.valueMap.value, materialSearchableFields.value)
}

const openMaterialPicker = () => {
  materialPickerVisible.value = true
}

/**
 * 物料库弹窗「确认添加」回调（弹窗本体 = 通用组件 MaterialPickerDialog）
 * 本页物料明细不涉及自定义字段，故只用回传的选中物料行
 */
const onMaterialPicked = ({ rows: picked }) => {
  if (!picked || picked.length === 0) return
  picked.forEach(m => {
    // 去重
    if (formData.materialItems.some(it => it.materialId === m.materialId)) return
    formData.materialItems.push({
      materialId: m.materialId,
      materialCode: m.materialCode || '',
      materialName: m.materialName || '',
      specification: m.model || m.specification || '',
      unit: m.baseUnitName || m.purchaseUnitName || '',
      materialMainImage: m.mainImage || m.materialImage || '',
      quantity: 1,
      unitPrice: Number(m.salePrice || m.purchasePrice || 0),
      amount: Number(m.salePrice || m.purchasePrice || 0),
      remark: ''
    })
  })
  calcEstimatedFromMaterials()
  materialPickerVisible.value = false
  ElMessage.success(`${tt("已添加 ")}${picked.length}${tt(" 条物料明细")}`)
}

const addMaterialRow = () => {
  formData.materialItems.push({
    materialId: null,
    materialCode: '',
    materialName: '',
    specification: '',
    unit: '',
    quantity: 1,
    unitPrice: 0,
    amount: 0,
    remark: ''
  })
}

const onMaterialSelect = (row, materialId) => {
  const m = allMaterials.value.find(x => x.materialId === materialId)
  if (m) {
    row.materialCode = m.materialCode || ''
    row.materialName = m.materialName || ''
    row.specification = m.model || m.specification || ''
    row.unit = m.baseUnitName || m.purchaseUnitName || ''
    row.materialMainImage = m.mainImage || m.materialImage || ''
    if (!row.unitPrice || row.unitPrice === 0) {
      row.unitPrice = Number(m.salePrice || m.purchasePrice || 0)
    }
    calcMaterialRow(row)
  }
}

const calcMaterialRow = (row) => {
  const qty = Number(row.quantity || 0)
  const price = Number(row.unitPrice || 0)
  row.amount = Number((qty * price).toFixed(2))
  calcEstimatedFromMaterials()
}

const calcEstimatedFromMaterials = () => {
  const total = formData.materialItems.reduce((sum, it) => sum + Number(it.amount || 0), 0)
  formData.estimatedAmount = Number(total.toFixed(2))
}

const getMaterialSummary = ({ columns, data }) => {
  const sums = []
  columns.forEach((col, idx) => {
    if (idx === 0) { sums[idx] = '合计'; return }
    if (col.label === '金额') {
      const total = data.reduce((sum, it) => sum + Number(it.amount || 0), 0)
      sums[idx] = Number(total).toFixed(2)
    } else {
      sums[idx] = ''
    }
  })
  return sums
}

// 新增/编辑
const handleAdd = () => {
  isEdit.value = false
  Object.assign(formData, defaultForm())
  autoFillDocCodeOnCreate(formData, 'opportunityNo')
  dialogVisible.value = true
}

const handleEdit = async (row) => {
  isEdit.value = true
  try {
    const detail = await getOpportunity(row.opportunityId)
    Object.assign(formData, defaultForm(), detail)
    // 解析 productDetail 中的 JSON 物料明细
    if (formData.productDetail) {
      try {
        const parsed = JSON.parse(formData.productDetail)
        if (Array.isArray(parsed)) {
          formData.materialItems = parsed
        }
      } catch (e) {
        // 非 JSON 格式，保留原文本作为单行备注
        formData.materialItems = []
      }
    }
    if (formData.customerId) onCustomerChange(formData.customerId)
    dialogVisible.value = true
  } catch (e) { /* 忽略 */ }
}

const handleSave = async () => {
  await formRef.value?.validate()
  saving.value = true
  try {
    // 物料明细序列化为 JSON 存到 productDetail 字段
    const payload = { ...formData }
    if (formData.materialItems && formData.materialItems.length > 0) {
      payload.productDetail = JSON.stringify(formData.materialItems.map(it => ({
        materialId: it.materialId,
        materialCode: it.materialCode,
        materialName: it.materialName,
        specification: it.specification,
        unit: it.unit,
        materialMainImage: it.materialMainImage || '',
        quantity: Number(it.quantity || 0),
        unitPrice: Number(it.unitPrice || 0),
        amount: Number(it.amount || 0),
        remark: it.remark || ''
      })))
      // 金额累计已在 calcMaterialRow 中自动同步
    } else {
      payload.productDetail = ''
    }
    delete payload.materialItems
    if (isEdit.value) {
      await updateOpportunity(formData.opportunityId, payload)
    } else {
      await saveOpportunity(payload)
    }
    ElMessage.success(isEdit.value ? tt('修改成功') : tt('新增成功'))
    dialogVisible.value = false
    loadData()
    loadDashboard()
  } finally {
    saving.value = false
  }
}

const confirmStageChange = async () => {
  if (!stageForm.salesStage) {
    ElMessage.warning(tt('请选择销售阶段'))
    return
  }
  const data = { salesStage: stageForm.salesStage }
  if (stageForm.salesStage === '成交') {
    data.winReason = stageForm.winReason
  } else if (stageForm.salesStage === '已关闭') {
    if (stageForm.won === null) {
      ElMessage.warning(tt('请选择输赢状态'))
      return
    }
    data.won = stageForm.won
    data.reason = stageForm.reason
  }
  saving.value = true
  try {
    await updateOppStage(stageForm.opportunityId, data)
    ElMessage.success(tt('阶段更新成功'))
    stageDialogVisible.value = false
    loadData()
    loadDashboard()
  } finally {
    saving.value = false
  }
}

const confirmClose = async () => {
  saving.value = true
  try {
    await closeOpportunity(closeForm.opportunityId, {
      won: closeForm.won,
      reason: closeForm.reason
    })
    ElMessage.success(tt('关闭成功'))
    closeDialogVisible.value = false
    loadData()
    loadDashboard()
  } finally {
    saving.value = false
  }
}

// 行内"更多"下拉分发：更改阶段/关闭/删除
const handleRowCommand = (cmd, row) => {
  if (cmd === 'stage') {
    Object.assign(stageForm, {
      opportunityId: row.opportunityId,
      opportunityName: row.opportunityName,
      salesStage: row.salesStage || '初步接触',
      winReason: '', won: null, reason: ''
    })
    stageDialogVisible.value = true
  } else if (cmd === 'close') {
    Object.assign(closeForm, {
      opportunityId: row.opportunityId,
      opportunityName: row.opportunityName,
      won: true, reason: ''
    })
    closeDialogVisible.value = true
  } else if (cmd === 'delete') {
    handleDelete(row)
  }
}

// 转销售订单：物料明细经 sessionStorage 带入销售订单编辑页
const handleToSaleOrder = async (row) => {
  try {
    // 拉取商机详情解析物料明细（存于 productDetail JSON）
    const detail = await getOpportunity(row.opportunityId)
    let items = []
    if (detail && detail.productDetail) {
      try {
        const parsed = JSON.parse(detail.productDetail)
        if (Array.isArray(parsed)) items = parsed
      } catch (e) { /* 非JSON忽略 */ }
    }
    if (items.length === 0) {
      ElMessage.warning(tt('该商机没有物料明细，请在编辑商机时维护物料明细后再转销售订单'))
      return
    }
    sessionStorage.setItem('oppToSaleOrderItems', JSON.stringify(items))
    router.push({
      path: '/sale/orders/new',
      query: {
        opportunityId: row.opportunityId,
        opportunityNo: row.opportunityNo,
        customerId: row.customerId || '',
        customerName: row.customerName || ''
      }
    })
  } catch (e) {
    // 拦截器已弹错误提示
  }
}

// 删除商机
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `${tt("确认删除商机【")}${row.opportunityName}${tt("】？删除后不可恢复。")}`,
      tt('删除确认'),
      { type: 'warning', confirmButtonText: tt('确认删除'), cancelButtonText: tt('取消') }
    )
  } catch (e) {
    return
  }
  try {
    await deleteOpportunity(row.opportunityId)
    ElMessage.success(tt('删除成功'))
    loadData()
    loadDashboard()
  } catch (e) {
    // 拦截器已弹错误提示
  }
}

// 外键下拉选项
const customerOptions = ref([])
const employeeOptions = ref([])
const deptOptions = ref([])
const materialCategoryOptions = ref([])
async function loadDropdownOptions() {
  // 物料自定义字段：定制开关 + 字段定义（失败静默降级）
  await mcf.init()
  try {
    // 客户下拉只取"我的私海客户"（pool_type=0），后端按数据权限过滤 owner_user_id
    const custResp = await listCrmCustomer({ poolType: 0, page: 1, size: 9999 })
    const list = custResp?.records || []
    customerOptions.value = list.map(c => ({ value: c.customerId, label: c.customerName, industry: c.industry, contactPerson: c.contactPerson, contactPhone: c.contactPhone }))
  } catch (e) { console.warn('加载私海客户失败', e) }
  try {
    const empList = await listAllEmployee()
    employeeOptions.value = (Array.isArray(empList) ? empList : (empList?.records || []))
      .map(e => ({ value: e.employeeId, label: e.employeeName + (e.deptName ? ' (' + e.deptName + ')' : '') }))
  } catch (e) { console.warn('加载员工失败', e) }
  try {
    const depList = await listAllDepartment()
    deptOptions.value = (Array.isArray(depList) ? depList : (depList?.records || []))
      .map(d => ({ value: d.deptId, label: d.deptName }))
  } catch (e) { console.warn('加载部门失败', e) }
  try {
    const catList = await listAllMaterialCategory()
    materialCategoryOptions.value = (Array.isArray(catList) ? catList : (catList?.records || []))
  } catch (e) { console.warn('加载物料分类失败', e) }
  // 加载全量物料用于手动选择下拉
  try {
    const matList = await pageMaterial({ current: 1, size: 1000, ...getSortParams() })
    allMaterials.value = matList?.records || matList || []
    // 行内下拉附加字段：物料到位后按批预取（不阻塞渲染）
    mcf.preloadAll(allMaterials.value)
  } catch (e) { console.warn('加载物料失败', e) }
}

const headerCellStyle = {
  background: 'rgba(99, 102, 241, 0.04)',
  color: '#1f2937',
  fontWeight: 600,
  borderBottom: '2px solid #e0e7ff'
}

onMounted(() => {
  // 数据看板穿透：URL query isClosed 注入筛选（无 query 时行为完全不变）
  const route = useRoute()
  const q = route.query || {}
  if (q.isClosed !== undefined && q.isClosed !== '') {
    search.isClosed = Number(q.isClosed)
    ElMessage.info(tt('已应用数据看板穿透筛选条件'))
  }
  cf.load().catch(() => {})
  loadData()
  loadDashboard()
  loadDropdownOptions()
})
useAutoRefresh(() => {
  loadData()
  loadDashboard()
  loadDropdownOptions()
})
</script>

<style scoped lang="scss">
.opp-page {
  padding: 20px 24px;
  background: linear-gradient(135deg, #f8faff 0%, #f0f4ff 50%, #f8fcff 100%);
  min-height: calc(100vh - 60px);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;

  .header-left {
    display: flex;
    align-items: center;
    gap: 16px;
  }

  .back-btn {
    display: flex;
    align-items: center;
    gap: 4px;
    color: #6b7280;
    font-size: 13px;
    cursor: pointer;
    padding: 6px 12px;
    border-radius: 6px;
    background: #fff;
    border: 1px solid #e5e7eb;
    transition: all .2s;
    &:hover {
      color: #6366f1;
      border-color: #c7d2fe;
    }
  }

  .title-group { display: flex; flex-direction: column; gap: 2px; }

  .page-title {
    font-size: 20px;
    font-weight: 700;
    color: #111827;
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .title-badge {
    font-size: 10px;
    font-weight: 600;
    color: #6366f1;
    background: linear-gradient(135deg, #e0e7ff, #f0f4ff);
    padding: 2px 8px;
    border-radius: 4px;
    letter-spacing: 1px;
  }

  .page-sub { font-size: 12px; color: #9ca3af; }

  .header-actions { display: flex; gap: 8px; align-items: center; }
}

.btn-primary-glow {
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  border: none;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
  &:hover {
    box-shadow: 0 6px 20px rgba(99, 102, 241, 0.4);
    transform: translateY(-1px);
  }
}

.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  position: relative;
  overflow: hidden;
  transition: all .3s;
  cursor: pointer;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 24px rgba(99, 102, 241, 0.1);
    border-color: #c7d2fe;
  }

  .stat-icon {
    width: 48px;
    height: 48px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 24px;
    color: #fff;
    position: relative;
    z-index: 1;

    &.primary { background: linear-gradient(135deg, #6366f1, #8b5cf6); }
    &.warning { background: linear-gradient(135deg, #f59e0b, #f97316); }
    &.info { background: linear-gradient(135deg, #3b82f6, #60a5fa); }
    &.success { background: linear-gradient(135deg, #10b981, #34d399); }
    &.danger { background: linear-gradient(135deg, #ef4444, #f87171); }
  }

  .stat-info { flex: 1; }
  .stat-value {
    font-size: 24px;
    font-weight: 700;
    color: #111827;
    line-height: 1.2;
  }
  .stat-label {
    font-size: 12px;
    color: #9ca3af;
    margin-top: 4px;
  }

  .stat-glow {
    position: absolute;
    right: -20px;
    top: -20px;
    width: 100px;
    height: 100px;
    border-radius: 50%;
    opacity: 0.1;
    filter: blur(20px);

    &.primary { background: #6366f1; }
    &.warning { background: #f59e0b; }
    &.info { background: #3b82f6; }
    &.success { background: #10b981; }
    &.danger { background: #ef4444; }
  }
}

.content-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.search-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #f3f4f6;
  flex-wrap: wrap;
  gap: 12px;

  .search-right {
    display: flex;
    gap: 8px;
    align-items: center;
    flex-wrap: wrap;
  }

  .search-input {
    width: 260px;
    :deep(.el-input__wrapper) {
      border-radius: 8px;
      box-shadow: 0 0 0 1px #e5e7eb inset;
      &:hover { box-shadow: 0 0 0 1px #c7d2fe inset; }
      &.is-focus { box-shadow: 0 0 0 1px #6366f1 inset; }
    }
  }
}

.status-tabs {
  display: flex;
  gap: 4px;

  .status-tab {
    padding: 8px 16px;
    font-size: 13px;
    color: #6b7280;
    cursor: pointer;
    border-radius: 8px;
    display: flex;
    align-items: center;
    gap: 6px;
    transition: all .2s;

    &:hover { color: #6366f1; background: #f5f3ff; }

    &.active {
      color: #6366f1;
      background: linear-gradient(135deg, #eef2ff, #f5f3ff);
      font-weight: 600;
    }

    .tab-count {
      font-size: 11px;
      background: #e5e7eb;
      color: #6b7280;
      padding: 1px 6px;
      border-radius: 10px;
    }

    &.active .tab-count {
      background: #6366f1;
      color: #fff;
    }
  }
}

.tech-table {
  :deep(.el-table__header th) {
    background: rgba(99, 102, 241, 0.04) !important;
  }

  :deep(.el-table__row:hover > td) {
    background: rgba(99, 102, 241, 0.03) !important;
  }

  :deep(.el-table td) {
    border-bottom: 1px solid #f3f4f6;
  }
}

.order-no-cell {
  display: flex;
  align-items: center;
  gap: 6px;

  .order-no-text {
    color: #6366f1;
    font-weight: 500;
    cursor: pointer;
    &:hover { color: #8b5cf6; }
  }

  .link-icon {
    color: #c7d2fe;
    font-size: 12px;
    opacity: 0;
    transition: opacity .2s;
  }
  &:hover .link-icon { opacity: 1; }
}

.customer-cell {
  display: flex;
  align-items: center;
  gap: 10px;

  .customer-avatar {
    width: 32px;
    height: 32px;
    border-radius: 8px;
    background: linear-gradient(135deg, #6366f1, #8b5cf6);
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 13px;
    font-weight: 600;
    flex-shrink: 0;
  }

  .customer-name {
    font-size: 13px;
    color: #374151;
    font-weight: 500;
  }
}

.amount-text { color: #374151; font-variant-numeric: tabular-nums; }

.status-cell {
  display: flex;
  align-items: center;
  gap: 6px;

  .status-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;

    &.dot-success { background: #10b981; box-shadow: 0 0 6px rgba(16, 185, 129, 0.5); }
    &.dot-warning { background: #f59e0b; box-shadow: 0 0 6px rgba(245, 158, 11, 0.5); }
    &.dot-info { background: #3b82f6; box-shadow: 0 0 6px rgba(59, 130, 246, 0.5); }
    &.dot-danger { background: #ef4444; box-shadow: 0 0 6px rgba(239, 68, 68, 0.5); }
  }
}

.action-cell {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.pagination-wrap {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-top: 1px solid #f3f4f6;

  .pagination-info {
    font-size: 13px;
    color: #6b7280;
  }
}

:deep(.el-button) {
  border-radius: 8px;
  font-weight: 500;
}

:deep(.el-dialog) {
  border-radius: 12px;
}

@media (max-width: 1200px) {
  .stat-cards { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 768px) {
  .opp-page { padding: 12px; }
  .stat-cards { grid-template-columns: 1fr; }
  .search-bar {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }
  .status-tabs { flex-wrap: wrap; }
}

/* 明细行「物料」下拉：与全站标准物料下拉一致（下拉本身即搜索框，选项内附自定义字段） */
.table-select {
  :deep(.el-select__wrapper) { box-shadow: none; }
  :deep(.el-input__inner) { font-size: 12px; }
}
.opt-code {
  color: #409eff;
  font-weight: 600;
  font-family: 'SF Mono', Monaco, Consolas, monospace;
  font-size: 11px;
  margin-right: 6px;
}
.opt-name { color: #303133; font-size: 12px; }
.opt-model { float: right; color: #909399; font-size: 11px; }
.opt-cf { margin-left: 8px; color: #909399; font-size: 11px; white-space: normal; word-break: break-word; }
</style>

<!-- 非 scoped：物料下拉的 popper 被 teleport 到 body，scoped 选择器无法命中，只能配全局类名（popper-class="material-select-popper"） -->
<style>
.material-select-popper {
  min-width: 640px;
  max-width: 90vw;
}
.material-select-popper .el-select-dropdown__item {
  height: auto;
  min-height: 34px;
  line-height: 1.6;
  padding-top: 6px;
  padding-bottom: 6px;
  white-space: normal;
  overflow: visible;
}
</style>
