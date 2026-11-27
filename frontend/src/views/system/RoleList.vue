<template>
  <div class="erp-page">
    <div class="filter-bar">
      <el-input
        v-model="query.keyword"
        :placeholder="$tt('角色名称关键词')"
        class="filter-input"
        clearable
        @keyup.enter="onSearch"
      />
      <el-button type="primary" @click="onSearch">{{ $tt("查询") }}</el-button>
      <el-button @click="onReset">{{ $tt("重置") }}</el-button>
      <el-button type="success" @click="onAdd">{{ $tt("新增") }}</el-button>
    </div>

    <el-table :data="tableData" border v-loading="loading" @sort-change="onSortChange">
      <el-table-column prop="roleCode" :label="$tf('角色编码')" min-width="130" sortable="custom"/>
      <el-table-column :label="$tf('角色名称')" min-width="160">
        <template #default="{ row }">
          <el-link type="primary" @click="onEditRole(row)">
            {{ $dt(toRoleNameLabel(row.roleName, row)) }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column :label="$tf('数据范围')" min-width="120">
        <template #default="{ row }">{{ $dt(toDataScopeLabel(row.dataScope)) }}</template>
      </el-table-column>
      <el-table-column prop="sortOrder" :label="$tf('排序')" width="90" align="right" sortable="custom"/>
      <el-table-column :label="$tf('状态')" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">
            {{ $dt(toStatusLabel(row.status)) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$tf('操作')" width="90" align="center">
        <template #default="{ row }">
          <!-- 系统内置角色不可删除：直接隐藏按钮（与后端 is_system 守卫一致） -->
          <el-button
            v-if="canDeleteRole(row)"
            v-permission="'system:role:delete'"
            type="danger"
            link
            @click="onDeleteRole(row)"
          >{{ $tt('删除') }}</el-button>
        </template>
      </el-table-column>
      <AuditColumns />
    </el-table>

    <el-pagination
      class="pagination"
      v-model:current-page="query.current"
      v-model:page-size="query.size"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      @size-change="loadData"
      @current-change="loadData"
    />

    <!-- 角色编辑弹窗：4 Tab -->
    <el-dialog
      :title="isEdit ? '编辑角色' : '新增角色'"
      v-model="dialogVisible"
      width="920px"
      @close="onDialogClose"
    >
      <el-tabs v-model="activeTab" type="card" class="role-tabs">
        <!-- Tab1: 基本信息 -->
        <el-tab-pane :label="$tf('基本信息')" name="info">
          <el-form :model="form" :rules="rules" ref="formRef" label-width="100px" class="role-form">
            <el-form-item :label="$tf('角色编码')" prop="roleCode">
              <el-input v-model="form.roleCode" :placeholder="$tt('如 ROLE_ADMIN')" :disabled="docCodeDisabled" />
            </el-form-item>
            <el-form-item :label="$tf('角色名称')" prop="roleName">
              <el-input v-model="form.roleName" :placeholder="$tt('请输入角色名称')" />
            </el-form-item>
            <el-form-item :label="$tf('数据范围')" prop="dataScope">
              <el-select v-model="form.dataScope" :placeholder="$tt('请选择数据范围')" style="width:100%">
                <el-option :label="$tf('全部数据')" value="全部数据" />
                <el-option :label="$tf('本部门及子部门')" value="本部门及子部门" />
                <el-option :label="$tf('本部门数据')" value="本部门数据" />
                <el-option :label="$tf('仅本人数据')" value="仅本人数据" />
                <el-option :label="$tf('自定义')" value="自定义" />
              </el-select>
            </el-form-item>
            <div class="form-row">
              <el-form-item :label="$tf('状态')" prop="status">
                <el-select v-model="form.status" :placeholder="$tt('请选择')" style="width:100%">
                  <el-option :label="$tf('启用')" value="启用" />
                  <el-option :label="$tf('停用')" value="停用" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('排序')" prop="sortOrder">
                <el-input-number v-model="form.sortOrder" :min="0" controls-position="right" style="width:100%" />
              </el-form-item>
            </div>
            <el-form-item :label="$tf('备注')" prop="remark">
              <el-input v-model="form.remark" type="textarea" :rows="2" :placeholder="$tt('选填')" />
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- Tab2: 菜单权限 - 分组卡片式 -->
        <el-tab-pane :label="$tf('菜单权限')" name="menu">
          <div class="perm-role-info">
            <el-icon><Folder /></el-icon>
            <span>{{ currentRole.roleName || form.roleName }}（{{ currentRole.roleCode || form.roleCode }}）</span>
            <div class="perm-actions">
              <el-button size="small" @click="selectAllMenus(true)">{{ $tt("全选") }}</el-button>
              <el-button size="small" @click="selectAllMenus(false)">{{ $tt("清空") }}</el-button>
            </div>
          </div>
          <div class="menu-group-grid">
            <div v-for="group in menuGroups" :key="group.key" class="menu-group-card">
              <div class="group-header" @click="toggleGroup(group.key)">
                <span class="group-title">
                  <el-icon class="group-arrow" :class="{ 'rotate': isGroupOpen(group.key) }"><Document /></el-icon>
                  {{ $tf(group.label) }}
                </span>
                <span class="group-count">{{ groupCheckedCount(group) }}/{{ groupTotalCount(group) }}</span>
                <div class="group-actions" @click.stop>
                  <el-button link type="primary" size="small" @click="selectGroupMenus(group, true)">{{ $tt("全选") }}</el-button>
                  <el-button link type="info" size="small" @click="selectGroupMenus(group, false)">{{ $tt("清空") }}</el-button>
                </div>
              </div>
              <div v-show="isGroupOpen(group.key)" class="group-body">
                <template v-for="item in group.items" :key="item.itemKey || item.path">
                  <!-- 二级分组 -->
                  <div v-if="item.children && item.children.length" class="sub-group">
                    <div class="sub-group-title">{{ $t('menu.' + item.label, item.label) }}</div>
                    <div class="sub-group-items">
                      <label class="menu-item" v-for="child in item.children" :key="child.path">
                        <el-checkbox
                          :model-value="menuChecked.has(child.path)"
                          @change="(v)=>toggleMenu(child.path, v, group.key)"
                        />
                        <span class="item-label">{{ $tf(child.label) }}</span>
                      </label>
                    </div>
                  </div>
                  <!-- 普通项 -->
                  <label v-else class="menu-item">
                    <el-checkbox
                      :model-value="menuChecked.has(item.path)"
                      @change="(v)=>toggleMenu(item.path, v, group.key)"
                    />
                    <span class="item-label">{{ $t('menu.' + item.label, item.label) }}</span>
                  </label>
                </template>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- Tab3: 按钮权限 - 双栏布局（2026-10-19 PRD 改版：左页面导航 + 右动作卡片） -->
        <el-tab-pane :label="$tf('按钮权限')" name="button">
          <div class="btnp-toolbar">
            <span class="btnp-role-name">{{ currentRole.roleName || form.roleName }} · 按钮显隐控制</span>
            <el-checkbox v-model="showAllActions" size="small" class="btnp-showall">
              {{ $tt("显示全部动作（含弹窗按钮 / 业务扩展）") }}
            </el-checkbox>
            <div class="btnp-toolbar-spacer"></div>
            <el-button size="small" :disabled="!btnActiveModuleGroup" @click="setModuleAll(true)">{{ $tt("本模块全部开启") }}</el-button>
            <el-button size="small" :disabled="!btnActiveModuleGroup" @click="setModuleAll(false)">{{ $tt("本模块全部关闭") }}</el-button>
          </div>
          <div class="btnp-layout">
            <!-- 左栏：模块导航（2026-11-06 重设计：不再铺开页面，页面行在右侧矩阵里） -->
            <div class="btnp-left">
              <el-input
                v-model="btnModuleSearch"
                size="small"
                :placeholder="$tt('搜索模块或页面...')"
                clearable
                class="btnp-search"
              >
                <template #prefix><el-icon><Search /></el-icon></template>
              </el-input>
              <div class="btnp-module-list">
                <div
                  v-for="group in btnFilteredModules"
                  :key="group.key"
                  class="btnp-module-item"
                  :class="{ active: btnActiveModule === group.key }"
                  @click="selectModule(group.key)"
                >
                  <span class="btnp-module-name">{{ $tf(group.label) }}</span>
                  <span class="btnp-module-cnt">{{ group.items.length }}</span>
                </div>
                <div v-if="!btnFilteredModules.length" class="btnp-group-label">{{ $tt('未找到匹配模块') }}</div>
              </div>
            </div>
            <!-- 右栏：权限矩阵（行=页面，列=动作） -->
            <div class="btnp-right">
              <template v-if="btnActiveModuleGroup">
                <div class="btnp-matrix-head">
                  <b class="btnp-matrix-title">{{ $tf(btnActiveModuleGroup.label) }}</b>
                  <span class="btnp-matrix-sub">{{ btnActiveModuleGroup.items.length }} {{ $tt('个页面') }} · {{ $tt('已配置') }} {{ btnModuleConfiguredCount }}</span>
                  <div class="btnp-matrix-tabs">
                    <button
                      v-for="t in MATRIX_TABS"
                      :key="t.key"
                      type="button"
                      class="btnp-matrix-tab"
                      :class="{ on: btnMatrixTab === t.key }"
                      @click="btnMatrixTab = t.key"
                    >{{ $tt(t.label) }}</button>
                  </div>
                </div>
                <div class="btnp-matrix-tips">
                  <span>{{ $tt("列头开关 = 整个模块这个按钮一起开/关") }}</span>
                  <span>{{ $tt("行首开关 = 这一页所有按钮一起开/关") }}</span>
                  <span>{{ $tt("格子里 = 单个按钮") }}</span>
                </div>
                <div class="btnp-matrix-wrap">
                  <table class="btnp-matrix">
                    <thead>
                      <tr>
                        <th class="btnp-mx-page">{{ $tt('页面') }}</th>
                        <th v-for="col in btnMatrixColumns" :key="col.code" class="btnp-mx-col">
                          <div class="btnp-mx-colhead btnp-mx-clickable" @click="toggleColumn(col.code)">
                            <span class="btnp-mx-colname">{{ $tf(col.label) }}</span>
                            <el-switch size="small" class="btnp-mx-sw" :model-value="colAllOn(col.code)" />
                            <span class="btnp-mx-colcnt">{{ colOnCount(col.code) }}/{{ colApplicableCount(col.code) }}</span>
                          </div>
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="row in btnMatrixRows" :key="row.key">
                        <td class="btnp-mx-page">
                          <div class="btnp-mx-pagename btnp-mx-clickable" @click="toggleRow(row)">
                            <el-switch size="small" class="btnp-mx-sw" :model-value="rowAllOn(row)" />
                            <span class="btnp-mx-label" :title="row.label">{{ $t('menu.' + row.label, row.label) }}</span>
                            <span v-if="!row.registered" class="btnp-mx-tag">{{ $tt('未登记') }}</span>
                            <span v-else-if="pageOffCount(row.moduleKey, row.itemKey) > 0" class="btnp-mx-tag off">{{ $tt('关') }} {{ pageOffCount(row.moduleKey, row.itemKey) }}</span>
                          </div>
                        </td>
                        <td
                          v-for="col in btnMatrixColumns"
                          :key="col.code"
                          class="btnp-mx-cell"
                          :class="{ 'btnp-mx-clickable': row.actions.includes(col.code) }"
                          @click="row.actions.includes(col.code) && toggleCell(row, col.code)"
                        >
                          <el-switch
                            v-if="row.actions.includes(col.code)"
                            size="small"
                            class="btnp-mx-sw"
                            :model-value="buttonChecked.has(row.key + ':' + col.code)"
                          />
                          <span v-else class="btnp-mx-dash">—</span>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                  <el-empty v-if="!btnMatrixColumns.length" :description="$tt('该模块页面没有可配置的按钮')" />
                </div>
              </template>
              <el-empty v-else :description="$tt('请在左侧选择模块')" />
            </div>
          </div>
        </el-tab-pane>

        <!-- Tab4: 字段权限 - 左右布局 -->
        <el-tab-pane :label="$tf('字段权限')" name="field">
          <div class="perm-role-info">
            <el-icon><Lock /></el-icon>
            <span>{{ currentRole.roleName || form.roleName }} · 字段可见/可编辑/必填</span>
          </div>
          <div class="field-perm-layout">
            <!-- 左：页面树 -->
            <div class="field-left-nav">
              <el-input
                v-model="fieldNavSearch"
                size="small"
                :placeholder="$tt('搜索页面...')"
                clearable
                class="nav-search"
              >
                <template #prefix><el-icon><Search /></el-icon></template>
              </el-input>
              <el-tree
                ref="fieldNavTreeRef"
                :key="fieldTreeVersion"
                class="page-tree"
                :data="fieldPageTree"
                node-key="key"
                :expand-on-click-node="false"
                :default-expanded-keys="fieldExpandedKeys"
                :highlight-current="true"
                :filter-node-method="filterFieldNavNode"
                @node-click="onFieldPageClick"
              >
                <template #default="{ data }">
                  <span class="tree-node-label" :class="{'group-label': data.isGroup}">
                    {{ data.label }}
                    <el-tag v-if="data.isGroup" effect="plain" size="small" type="info" style="margin-left:6px">
                      {{ data.children ? data.children.length : 0 }}
                    </el-tag>
                  </span>
                </template>
              </el-tree>
            </div>
            <!-- 右：字段表格 -->
            <div class="field-right-main">
              <div v-if="!fieldSelectedPageKey" class="field-empty">
                <el-empty :description="$tt('请先在左侧选择页面')" :image-size="100" />
              </div>
              <div v-else v-loading="fieldLoading">
                <div class="field-quick-actions">
                  <el-button size="small" @click="fieldAllVisible(true)">{{ $tt("全部可见") }}</el-button>
                  <el-button size="small" @click="fieldAllVisible(false)">{{ $tt("全部隐藏") }}</el-button>
                  <el-button size="small" @click="fieldAllEditable(true)">{{ $tt("全部可编辑") }}</el-button>
                  <el-button size="small" @click="fieldAllEditable(false)">{{ $tt("全部只读") }}</el-button>
                  <el-button size="small" type="primary" v-permission="perm('apply')" @click="onSaveFieldPermission" :disabled="fieldDirtyCount===0">{{ $tt("保存字段权限") }}</el-button>
                </div>
                <el-table :data="filteredFieldList" border size="small" class="field-table">
                  <el-table-column :label="$tf('字段名称')" min-width="150">
                    <template #default="{ row }">
                      <span :class="{'default-req': row.defaultRequired}">
                        <el-icon v-if="row.defaultRequired" class="lock-icon"><Lock /></el-icon>
                        {{ row.fieldName }}
                      </span>
                    </template>
                  </el-table-column>
                  <el-table-column :label="$tf('可见')" width="90" align="center">
                    <template #default="{ row }">
                      <el-switch v-model="row.visible" :disabled="row.defaultRequired" size="small" />
                    </template>
                  </el-table-column>
                  <el-table-column :label="$tf('可编辑')" width="90" align="center">
                    <template #default="{ row }">
                      <el-switch v-model="row.editable" :disabled="row.defaultRequired || !row.visible" size="small" />
                    </template>
                  </el-table-column>
                  <el-table-column :label="$tf('必填')" width="90" align="center">
                    <template #default="{ row }">
                      <el-switch v-model="row.required" :disabled="row.defaultRequired || !row.visible || !row.editable" size="small" />
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <el-button @click="dialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button v-if="activeTab === 'info'" type="primary" v-permission="perm('save')" @click="onSubmit">{{ $tt("保存") }}</el-button>
        <el-button v-else-if="activeTab === 'menu'" type="primary" v-permission="perm('apply')" @click="onSubmitMenuPermissions">{{ $tt("保存菜单权限") }}</el-button>
        <el-button v-else-if="activeTab === 'button'" type="primary" :loading="btnPermSaving" v-permission="perm('apply')" @click="onSubmitButtonPermissions">{{ $tt("保存按钮权限") }}</el-button>
        <el-button v-else-if="activeTab === 'field'" type="primary" v-permission="perm('apply')" @click="onSaveFieldPermission">{{ $tt("保存字段权限") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick, watch } from 'vue'
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { tt } from '@/i18n'
useAutoRefresh(() => loadData())
onMounted(loadData)
import { ElMessage, ElMessageBox } from 'element-plus'
import { User, Folder, Document, Lock, Search } from '@element-plus/icons-vue'
import {
  pageRole, createRole, updateRole, assignPermissions, getRole,
  getButtonPermissions, assignButtonPermissions, deleteRole
} from '@/api/system/role'
import {
  getRoleFieldPermission, saveRoleFieldPermission, getFieldMetaList, getAvailablePages, getRoleConfiguredPages
} from '@/api/system/fieldPermission'
import { MENU_GROUPS } from '@/config/menuConfig'
import { usePagePerm } from '@/utils/pagePerm'
import { BUTTON_ACTION_GROUPS, SETTINGS_ACTION_CODES, ACTION_LIMITED_TO_PREFIXES, PRIVILEGED_ACTIONS, DEFAULT_VISIBLE_ACTIONS, defaultVisibleOffCode, isDefaultVisibleOffCode } from '@/utils/pagePerm'
// 页面动作注册表：由 scripts/gen-page-actions.cjs 在 dev/build 启动时自动扫描生成
import { PAGE_ACTIONS } from '@/config/pageActions.generated.js'
import { useUserStore } from '@/stores/user'
import AuditColumns from '@/components/AuditColumns.vue'
import { useAutoDocCode } from '@/composables/useAutoDocCode'
import { useTableSort } from '@/composables/useTableSort'
const { handleSortChange, getSortParams } = useTableSort()
const userStore = useUserStore()
// 按钮权限对象码：角色权限设置页（保存角色/保存菜单/按钮/字段权限均受「角色权限设置」卡片控制）
const perm = usePagePerm('system:role-perm')


const onSortChange = (e) => { handleSortChange(e); loadData() }
const { docCodeDisabled, autoFillDocCodeOnCreate } = useAutoDocCode('SYS_ROLE')

// ===== 字段值映射（兼容英文枚举、历史简写、完整中文） =====
const ROLE_NAME_MAP_BY_CODE = {
  'ADMIN': '超级管理员',
  'GENERAL_MANAGER': '总经理',
  'SALES_MANAGER': '销售主管',
  'SALES': '销售员',
  'PURCHASE_MANAGER': '采购主管',
  'PURCHASE': '采购员',
  'WAREHOUSE_MANAGER': '仓库主管',
  'WAREHOUSE': '仓管员',
  'PRODUCTION_MANAGER': '生产主管',
  'PRODUCTION': '生产员',
  'FINANCE_MANAGER': '财务主管',
  'FINANCE': '会计',
  'VIEWER': '查看者'
}
const ROLE_NAME_MAP_BY_NAME = {
  'SuperAdmin': '超级管理员', 'superadmin': '超级管理员', 'Super Admin': '超级管理员', 'super_admin': '超级管理员',
  'GeneralManager': '总经理', 'General Manager': '总经理', 'general_manager': '总经理',
  'SalesManager': '销售主管', 'Sales Manager': '销售主管', 'sales_manager': '销售主管',
  'Sales': '销售员', 'SALES': '销售员',
  'PurchaseManager': '采购主管', 'Purchase Manager': '采购主管', 'purchase_manager': '采购主管',
  'Purchase': '采购员', 'PURCHASE': '采购员',
  'WarehouseManager': '仓库主管', 'Warehouse Manager': '仓库主管', 'warehouse_manager': '仓库主管',
  'Warehouse': '仓管员', 'WAREHOUSE': '仓管员',
  'ProductionManager': '生产主管', 'Production Manager': '生产主管', 'production_manager': '生产主管',
  'Production': '生产员', 'PRODUCTION': '生产员',
  'FinanceManager': '财务主管', 'Finance Manager': '财务主管', 'finance_manager': '财务主管',
  'Finance': '会计', 'FINANCE': '会计',
  'Viewer': '查看者', 'VIEWER': '查看者'
}
const DATA_SCOPE_MAP = {
  '全部数据': '全部数据', 'ALL': '全部数据', '全部': '全部数据', 'all': '全部数据',
  '本部门数据': '本部门数据', 'DEPT': '本部门数据', '本部门': '本部门数据', 'dept': '本部门数据',
  '本部门及子部门': '本部门及子部门', 'DEPT_AND_CHILD': '本部门及子部门', 'dept_and_child': '本部门及子部门',
  '仅本人数据': '仅本人数据', 'SELF': '仅本人数据', '本人': '仅本人数据', 'self': '仅本人数据',
  '自定义': '自定义', 'CUSTOM': '自定义', 'custom': '自定义'
}
const STATUS_MAP = {
  '启用': '启用', 'ACTIVE': '启用', 'active': '启用', 'ENABLE': '启用', 'enable': '启用', '1': '启用',
  '停用': '停用', 'INACTIVE': '停用', 'inactive': '停用', 'DISABLE': '停用', 'disable': '停用', '0': '停用'
}
// 兜底：role_type 已废弃，前端不再展示和编辑
function getRoleCodeFromRow(row) {
  return (row && row.roleCode ? row.roleCode : (row && row.role_code ? row.role_code : '')).toString().trim().toUpperCase()
}
function toRoleNameLabel(v, row) {
  const code = getRoleCodeFromRow(row)
  if (code && ROLE_NAME_MAP_BY_CODE[code]) return ROLE_NAME_MAP_BY_CODE[code]
  const t = (typeof v === 'string' ? v.trim() : (v == null ? '' : String(v)))
  if (ROLE_NAME_MAP_BY_NAME[t]) return ROLE_NAME_MAP_BY_NAME[t]
  return t || '未设置'
}
function normalizeRoleName(v, row) {
  const code = getRoleCodeFromRow(row)
  if (code && ROLE_NAME_MAP_BY_CODE[code]) return ROLE_NAME_MAP_BY_CODE[code]
  const t = (typeof v === 'string' ? v.trim() : (v == null ? '' : String(v)))
  if (ROLE_NAME_MAP_BY_NAME[t]) return ROLE_NAME_MAP_BY_NAME[t]
  return v
}
function toDataScopeLabel(v) {
  const t = (typeof v === 'string' ? v.trim() : (v == null ? '' : String(v)))
  if (DATA_SCOPE_MAP[t]) return DATA_SCOPE_MAP[t]
  return t || '未设置'
}
function toStatusLabel(v) {
  const t = (typeof v === 'string' ? v.trim() : (v == null ? '' : String(v)))
  if (STATUS_MAP[t]) return STATUS_MAP[t]
  return t || '未知'
}
function normalizeDataScope(v) {
  const t = (typeof v === 'string' ? v.trim() : (v == null ? '' : String(v)))
  if (DATA_SCOPE_MAP[t]) return DATA_SCOPE_MAP[t]
  return v || '全部数据'
}
function normalizeStatus(v) {
  const t = (typeof v === 'string' ? v.trim() : (v == null ? '' : String(v)))
  if (STATUS_MAP[t]) return STATUS_MAP[t]
  return v || '启用'
}

// ===== 菜单权限：基于 MENU_GROUPS 生成分组卡片 =====
const menuGroups = computed(() => MENU_GROUPS.filter(g => g.items && g.items.length))
const menuChecked = reactive(new Set())
const openGroups = reactive(new Set())

function isGroupOpen(key) { return openGroups.has(key) }
function toggleGroup(key) {
  if (openGroups.has(key)) openGroups.delete(key)
  else openGroups.add(key)
}
function groupCheckedCount(group) {
  let total = 0, checked = 0
  for (const it of (group.items || [])) {
    if (it.children && it.children.length) {
      for (const c of it.children) {
        total++
        if (menuChecked.has(c.path)) checked++
      }
    } else {
      total++
      if (menuChecked.has(it.path)) checked++
    }
  }
  return checked
}
function groupTotalCount(group) {
  let total = 0
  for (const it of (group.items || [])) {
    if (it.children && it.children.length) total += it.children.length
    else total++
  }
  return total
}
function toggleMenu(path, checked, groupKey) {
  if (checked) menuChecked.add(path)
  else menuChecked.delete(path)
}
function selectAllMenus(checked) {
  for (const group of menuGroups.value) {
    selectGroupMenus(group, checked)
  }
}
function selectGroupMenus(group, checked) {
  for (const it of (group.items || [])) {
    if (it.children && it.children.length) {
      for (const c of it.children) {
        if (checked) menuChecked.add(c.path)
        else menuChecked.delete(c.path)
      }
    } else {
      if (checked) menuChecked.add(it.path)
      else menuChecked.delete(it.path)
    }
  }
}
async function onSubmitMenuPermissions() {
  if (!currentRole.roleId) { ElMessage.warning(tt('请先保存角色基本信息')); return }
  // 等待接口真实返回后再提示成功；失败由统一请求拦截器弹出中文原因，
  // 杜绝"接口失败仍弹保存成功"的假成功（与按钮/字段权限保存同口径）
  try {
    await assignPermissions(currentRole.roleId, Array.from(menuChecked))
    ElMessage.success(tt('菜单权限保存成功'))
  } catch (e) { /* 失败提示已由拦截器处理 */ }
}

// ===== 按钮权限配置 =====
// 2026-09-19：动作清单从 7 个扩展到 33 个，与后端 BillActionRegistry.ACTION_BUTTON_SUFFIX 对齐，
// 使「编辑页动作按钮 / 列表页页头按钮 / 批量操作按钮」全部可在此勾选控制（三方同一套按钮码）。
// 默认口径（2026-09-20 用户明确要求）：**任何按钮默认都是可见的，只有主动取消勾选并保存才会隐藏**。
// 页面级分组：该页已有已保存按钮码 → 严格按已保存的来；该页无任何已保存按钮码 → 界面预勾该页全部动作（默认可见）。
// （由 v-permission 规则③保证：该对象下没有显式按钮配置时命中菜单码即放行，故"整页全勾"的页面保存时不写库。）
const buttonActionGroups = BUTTON_ACTION_GROUPS
/** 扁平化后的动作清单（供"整页全选/清空"与其它既有逻辑复用） */
const buttonActions = buttonActionGroups.flatMap(g => g.actions)

/**
 * 设置中心（模块 key = 'system'，含各设置子页）专用动作集。
 * 用户 2026-10-08 反馈：设置中心的按钮不应和单据一模一样（不应出现提交/审核/作废/分配…）。
 */
const settingsActionGroups = computed(() => BUTTON_ACTION_GROUPS
  .map(g => ({ ...g, actions: g.actions.filter(a => SETTINGS_ACTION_CODES.includes(a.code)) }))
  .filter(g => g.actions.length))
function actionsForGroup (groupKey) {
  return groupKey === 'system' ? settingsActionGroups.value : buttonActionGroups
}
function flatActionsForGroup (groupKey, itemKey) {
  const groups = actionsForGroup(groupKey)
  // 未指定具体页面（跨页面批量口径）时返回该模块全量动作；指定页面时走单页过滤
  if (!itemKey) return groups.flatMap(g => g.actions)
  return groups.flatMap(g => actionsForPage(groupKey, itemKey, g.actions))
}
/**
 * 单页动作过滤（2026-10-13 初版 / 2026-11-06 改版）：
 * ① 优先用扫描生成的页面动作注册表（PAGE_ACTIONS），只显示该页面**实际用到**的动作，
 *    避免客户档案页冒出提交/审核/作废/结案等该页根本没有的开关；
 * ② 该页面未登记（纯展示页 / 权限码动态拼接）时走降级：
 *    「显示全部动作」开关打开 → 显示全部；否则仍按限定动作口径过滤。
 * 限定动作（重置密码/分配角色）只对真正有该按钮的页面出现，此规则保留。
 */
function isPageActionRegistered (groupKey, itemKey) {
  return !!PAGE_ACTIONS[`${groupKey}:${itemKey}`]
}
function actionsForPage (groupKey, itemKey, actions) {
  const objectCode = `${groupKey}:${itemKey}`
  const registered = PAGE_ACTIONS[objectCode]
  if (registered) {
    return (actions || []).filter(a => registered.includes(a.code))
  }
  if (showAllActions.value) return actions || []
  return (actions || []).filter(a => {
    const limited = ACTION_LIMITED_TO_PREFIXES[a.code]
    return !limited || limited.includes(objectCode)
  })
}

/**
 * 按钮权限页面清单：**含子菜单项**（2026-09-20 补齐）。
 * 原先只列顶级菜单项，导致「客户对账单 / 供应商对账单 / 应收汇总表 / 设置中心 20 多个子项」
 * 在按钮权限界面里根本没有勾选行 —— 这些页面的按钮码无处配置。
 * 子项权限码前缀 = `<模块>:<子项 itemKey>`（与前端 pagePerm.js 的路由解析口径一致）。
 */
const buttonGroups = computed(() => MENU_GROUPS
  .filter(g => g.items && g.items.length)
  .map(g => ({
    key: g.key,
    label: g.label,
    items: flattenMenuItems(g.items)
  }))
  .filter(g => g.items.length))
const buttonChecked = reactive(new Set())
/** 按钮权限保存进行中标记（防重复提交：保存后几秒内再点会用旧勾选覆盖，把刚取消的权限加回去） */
const btnPermSaving = ref(false)

/** 菜单项拍平：children 提升为与父项平级的可配置页面（父项本身若只是分组容器则跳过） */
function flattenMenuItems (items) {
  const out = []
  for (const it of (items || [])) {
    if (it.children && it.children.length) {
      for (const c of it.children) {
        if (c.itemKey) out.push({ ...c, path: c.path || `${it.path || ''}#${c.itemKey}` })
      }
      // 分组容器本身（如「报表」「系统」）不再单独占一行，避免出现无意义的勾选行
      continue
    }
    if (it.itemKey) out.push(it)
  }
  return out
}

/** ===== 2026-10-19 按钮权限页签改版（PRD FR-01~FR-06）：双栏布局状态 ===== */

/** 已存按钮码快照（用于"已配置/未配置"徽标：该对象在库里是否有任何显式按钮码） */
const savedButtonCodes = ref([])

/** 左栏：模块导航（2026-11-06 重设计）。搜索词同时匹配模块名与页面名，命中页面的模块也保留 */
const btnModuleSearch = ref('')
/** 当前选中的模块 key（如 sale） */
const btnActiveModule = ref('')

/** 动作分组页签（列太多时分页看） */
const MATRIX_TABS = [
  { key: 'base', label: '基础操作' },
  { key: 'edit', label: '编辑保存' },
  { key: 'dialog', label: '弹窗按钮' },
  { key: 'flow', label: '单据流转' },
  { key: 'biz', label: '业务扩展' },
  { key: 'all', label: '全部' }
]
const btnMatrixTab = ref('base')

/** 显示全部动作（未登记页面默认只给限定动作，打开后按全量词表展示） */
const showAllActions = ref(false)

/** 左栏模块列表：按搜索词过滤（模块名或页面名命中即保留） */
const btnFilteredModules = computed(() => {
  const kw = (btnModuleSearch.value || '').trim()
  if (!kw) return buttonGroups.value
  return buttonGroups.value.filter(g =>
    (g.label + '').includes(kw) ||
    g.items.some(it => (it.label + '').includes(kw) || (it.itemKey + '').includes(kw))
  )
})

/** 当前模块分组 */
const btnActiveModuleGroup = computed(() => buttonGroups.value.find(g => g.key === btnActiveModule.value) || null)

/** 当前模块下已配置的页面数 */
const btnModuleConfiguredCount = computed(() => {
  const g = btnActiveModuleGroup.value
  if (!g) return 0
  return g.items.filter(it => isPageConfigured(g.key, it.itemKey)).length
})

/** 当前模块的动作词表（设置中心用专用动作集，其它模块用全量） */
const btnRightGroups = computed(() => actionsForGroup(btnActiveModule.value))

/** 矩阵行：当前模块下每个页面 + 该页实际可配置的动作 */
const btnMatrixRows = computed(() => {
  const g = btnActiveModuleGroup.value
  if (!g) return []
  return g.items.map(it => ({
    key: `${g.key}:${it.itemKey}`,
    moduleKey: g.key,
    itemKey: it.itemKey,
    label: it.label,
    registered: isPageActionRegistered(g.key, it.itemKey),
    actions: flatActionsForGroup(g.key, it.itemKey).map(a => a.code)
  }))
})

/** 矩阵列：该模块所有页面动作的并集，按当前页签与词表顺序排列 */
const btnMatrixColumns = computed(() => {
  const union = new Set()
  for (const r of btnMatrixRows.value) for (const a of r.actions) union.add(a)
  const out = []
  for (const g of btnRightGroups.value) {
    if (btnMatrixTab.value !== 'all' && g.key !== btnMatrixTab.value) continue
    for (const a of g.actions) if (union.has(a.code)) out.push(a)
  }
  return out
})

/** 列头分母：该动作适用于当前模块多少个页面 */
function colApplicableCount (actionCode) {
  return btnMatrixRows.value.filter(r => r.actions.includes(actionCode)).length
}
/** 列头分子：该动作当前开着的页面数 */
function colOnCount (actionCode) {
  return btnMatrixRows.value.filter(r => r.actions.includes(actionCode) && buttonChecked.has(`${r.key}:${actionCode}`)).length
}
/** 列头是否全开 */
function colAllOn (actionCode) {
  const total = colApplicableCount(actionCode)
  return total > 0 && colOnCount(actionCode) === total
}
/** 列头开关：非全开 → 整模块全部开启；已全开 → 全部关闭 */
function toggleColumn (actionCode) {
  const rows = btnMatrixRows.value.filter(r => r.actions.includes(actionCode))
  if (!rows.length) return
  const target = !colAllOn(actionCode)
  for (const r of rows) {
    toggleButton(`${r.key}:${actionCode}`, target)
    btnTouchedPages.add(r.key)
  }
}

/** 行首是否全开 */
function rowAllOn (row) {
  return row.actions.length > 0 && row.actions.every(a => buttonChecked.has(`${row.key}:${a}`))
}
/** 行首开关：非全开 → 该页可见动作全部开启；已全开 → 全部关闭 */
function toggleRow (row) {
  if (!row.actions.length) return
  const target = !rowAllOn(row)
  for (const a of row.actions) toggleButton(`${row.key}:${a}`, target)
  btnTouchedPages.add(row.key)
}

/** 单个格子开关 */
function toggleCell (row, actionCode) {
  toggleButton(`${row.key}:${actionCode}`, !buttonChecked.has(`${row.key}:${actionCode}`))
  btnTouchedPages.add(row.key)
}

/** 本模块全部开启/关闭（只对该模块页面当前可见的动作生效） */
function setModuleAll (checked) {
  for (const r of btnMatrixRows.value) {
    for (const a of r.actions) toggleButton(`${r.key}:${a}`, checked)
    btnTouchedPages.add(r.key)
  }
}

/** 切换模块：自动落到该模块第一个有动作的分组页签 */
function selectModule (key) {
  btnActiveModule.value = key
  const union = new Set()
  const g = buttonGroups.value.find(x => x.key === key)
  if (g) for (const it of g.items) for (const a of flatActionsForGroup(key, it.itemKey)) union.add(a.code)
  const words = actionsForGroup(key)
  const first = MATRIX_TABS.find(t => t.key !== 'all' && words.some(ag => ag.key === t.key && ag.actions.some(a => union.has(a.code))))
  btnMatrixTab.value = first ? first.key : 'base'
}

function resetButtonPermUi () {
  btnModuleSearch.value = ''
  btnActiveModule.value = ''
  btnMatrixTab.value = 'base'
  btnTouchedPages.clear()
  showAllActions.value = false
}

/** 本会话在界面上扳动过开关的页面（未保存时也让徽标即时反映改动，对齐 PRD 原型） */
const btnTouchedPages = reactive(new Set())

/** 该页面在库里是否已有显式按钮配置（"已配置"徽标 / 右栏模式徽标）；本会话扳过开关的页面也按已配置展示 */
function isPageConfigured (groupKey, itemKey) {
  return btnTouchedPages.has(`${groupKey}:${itemKey}`) ||
    hasExplicitButtons(`${groupKey}:${itemKey}`, savedButtonCodes.value)
}
/** 已配置页面当前处于"关"的动作数（矩阵行首"关 N"徽标） */
function pageOffCount (groupKey, itemKey) {
  if (!isPageConfigured(groupKey, itemKey)) return 0
  const objectCode = `${groupKey}:${itemKey}`
  let n = 0
  for (const act of flatActionsForGroup(groupKey, itemKey)) {
    if (!buttonChecked.has(`${objectCode}:${act.code}`)) n++
  }
  return n
}

function buttonCode(moduleKey, itemKey, action) {
  return `${moduleKey}:${itemKey}:${action}`
}

/** 该「模块:对象」在**已保存的按钮码集合**里是否已有任何显式按钮码 */
function hasExplicitButtons(objectCode, savedCodes) {
  for (const code of (savedCodes || [])) {
    if (typeof code === 'string' && code.startsWith(objectCode + ':')) return true
  }
  return false
}

/**
 * 默认全部可见回显（2026-09-20 用户口径：任何按钮默认都是可见的，只有主动取消勾选才不可见）：
 * 加载已保存按钮码后，**按「页面（模块:对象）」分组判断** ——
 *   ① 该页**已有至少 1 个已保存按钮码** → 勾选态严格 = 已保存集合（未保存的保持不勾，不补勾，尊重既有意图）；
 *   ② 该页**一个已保存按钮码都没有** → 把该页所有动作预勾上（体现"默认可见"），使界面所见 = 实际生效状态
 *      （v-permission 规则③：该对象下无显式配置 → 放行）。
 * 注意：`savedCodes` 只传**库里读回来的已保存按钮码**（不是界面勾选集合），避免把预勾项当成"已保存"。
 */
function applyDefaultAllChecked(savedCodes) {
  const saved = savedCodes || []
  for (const g of buttonGroups.value) {
    for (const item of g.items) {
      const objectCode = `${g.key}:${item.itemKey}`
      if (!hasExplicitButtons(objectCode, saved)) {
        for (const act of flatActionsForGroup(g.key, item.itemKey)) {
          // 提权动作（重置密码/分配角色）默认不预勾：默认勾上 = 默认放权，违背"我有没有给权限"口径
          if (PRIVILEGED_ACTIONS.includes(act.code)) continue
          buttonChecked.add(`${objectCode}:${act.code}`)
        }
      }
      // 默认可见动作（筛选设置）：未写"关闭标记"就预勾 —— 与其它按钮同处一套开/关链路；
      // 写了关闭标记则保持不勾，保存后即真正隐藏（v-permission 规则⑥）
      for (const action of DEFAULT_VISIBLE_ACTIONS) {
        const off = defaultVisibleOffCode(objectCode, action)
        if (!saved.includes(off)) buttonChecked.add(`${objectCode}:${action}`)
      }
    }
  }
}

/**
 * 保存前构造提交列表：**所见即所得（2026-10-14 用户口径：设置必须真的有用）**。
 * 废除原"整页全勾不写库"优化——它导致界面勾选与库中存储永远不一致：
 * 勾满一页被静默丢弃（靠"无配置默认放行"兜底）、取消勾选又被写入，用户勾了没效果/取消了还在。
 * 现在界面勾什么就存什么，保存后回显 = 库 = 实际生效，三方永远一致。
 */
function buildSubmitButtonCodes() {
  const codes = new Set(buttonChecked)
  // 默认可见动作：界面未勾选 → 写"关闭标记"，保存后真正隐藏（与其它按钮取消勾选同效）
  for (const g of buttonGroups.value) {
    for (const item of g.items) {
      const objectCode = `${g.key}:${item.itemKey}`
      for (const action of DEFAULT_VISIBLE_ACTIONS) {
        if (!buttonChecked.has(`${objectCode}:${action}`)) {
          codes.add(defaultVisibleOffCode(objectCode, action))
        }
      }
    }
  }
  return Array.from(codes)
}
function toggleButton(code, checked) {
  if (checked) buttonChecked.add(code)
  else buttonChecked.delete(code)
}
async function onSubmitButtonPermissions() {
  if (!currentRole.roleId) { ElMessage.warning(tt('请先保存角色基本信息')); return }
  // 防重复提交（2026-10-14 实锤：保存后几秒内再次点击会用旧勾选状态覆盖，把刚取消的权限加回去）
  if (btnPermSaving.value) return
  btnPermSaving.value = true
  try {
  // 跳过"整页动作全勾"的页面：全勾 = 该页等同于不做配置（不写库照样放行，见 v-permission 规则③），
  // 否则会为每个角色写入「页面数 × 33」条冗余码，拖慢 /auth/userinfo 之后全站 v-permission 判定。
  const codes = buildSubmitButtonCodes()
  await assignButtonPermissions(currentRole.roleId, codes)
  // 保存后回读一次并以此为准刷新界面：避免"本地以为保存了、库里其实没写成功"的错觉
  let savedCount = codes.length
  try {
    const back = await getButtonPermissions(currentRole.roleId)
    buttonChecked.clear()
    // 关闭标记（xxxOff）只作为判定依据，不能当成勾选项回显
    for (const code of (back || [])) { if (!isDefaultVisibleOffCode(code)) buttonChecked.add(code) }
    // 未写库的"全勾页面"回读时并不存在 → 重新按"默认可见"预勾，保持界面所见 = 实际生效
    applyDefaultAllChecked(back || [])
    savedButtonCodes.value = back || []
    savedCount = (back || []).length
    // 用户口径（2026-10-14）：提示「保存成功」，不暴露"显式配置 N 项"这类内部数字；
    // 2026-10-19 PRD FR-07：补充生效时效提示（30 秒自动同步 + 旧页签需重开或刷新）
    ElMessage.success(tt('保存成功 · 权限将在 30 秒内自动同步，已打开的单据页签请关闭后重开或刷新浏览器'))
  } catch (e) {
    // 回读失败不阻断（保存已成功）；拦截器已对真正的保存失败弹明确错误
    console.warn('[按钮权限] 保存后回读失败', e)
  }
  // 关键修复（2026-10-12 用户口径：开关开了就能用、关了就不能用，不允许重新登录才生效）：
  // 保存成功后**立即**主动刷新当前登录用户的本地 permissions，不再等 30 秒轮询。
  // 场景：员工自己进设置中心改自己所在角色的按钮权限 → 保存 → 回菜单 → 删除按钮应立即消失/或一点即被拒。
  // 后端在 assignButtonPermissions 内已调用 authInterceptor.evictAll() 清空鉴权缓存+自增版本号，
  // 此处刷一下本地 userinfo 即可与后端同步：操作者本人本地权限秒级更新。
  // 若操作者是 admin（管理员全放行），刷新一次也无害。
  try {
    await userStore.fetchUserinfo()
  } catch (e) { /* 刷新失败不阻断保存成功提示 */ }
  } catch (e) {
    // 保存请求本身失败：拦截器已弹明确错误，这里只复位按钮状态
    console.warn('[按钮权限] 保存失败', e)
  } finally {
    btnPermSaving.value = false
  }
}

// ===== 字段权限 =====
const fieldNavTreeRef = ref(null)
const fieldNavSearch = ref('')
watch(fieldNavSearch, (v) => { fieldNavTreeRef.value && fieldNavTreeRef.value.filter(v) })
const fieldPageTree = ref([])
const fieldExpandedKeys = ref([])
/** 该角色已配置过字段权限的页面 key 集合（决定左栏默认展开哪些模块） */
const fieldConfiguredPages = ref(new Set())
/** 树重建版本号：default-expanded-keys 只在首次渲染生效，重载后靠换 key 强制重建 */
const fieldTreeVersion = ref(0)
const fieldSelectedPageKey = ref('')
const fieldLoading = ref(false)
const fieldList = ref([])
const fieldOriginalList = ref([])

const fieldDirtyCount = computed(() => {
  if (fieldList.value.length !== fieldOriginalList.value.length) return fieldList.value.length
  const orig = new Map(fieldOriginalList.value.map(x => [x.fieldKey, x]))
  let cnt = 0
  for (const row of fieldList.value) {
    const o = orig.get(row.fieldKey)
    if (!o) { cnt++; continue }
    if (!!row.visible !== !!o.visible || !!row.editable !== !!o.editable || !!row.required !== !!o.required) cnt++
  }
  return cnt
})

const filteredFieldList = computed(() => fieldList.value)

function filterFieldNavNode(value, data) {
  if (!value) return true
  if (data.isGroup) return data.children && data.children.some(c => (c.label + '').includes(value))
  return (data.label + '').includes(value) || (data.key + '').includes(value)
}

async function loadFieldPageTree() {
  try {
    const [list, configured] = await Promise.all([
      getAvailablePages(),
      currentRole.roleId ? getRoleConfiguredPages(currentRole.roleId).catch(() => []) : Promise.resolve([])
    ])
    const byModule = new Map()
    for (const p of (list || [])) {
      if (!p || !p.key) continue
      const m = p.module || '其他'
      if (!byModule.has(m)) byModule.set(m, [])
      byModule.get(m).push(p)
    }
    const moduleLabel = {
      basedata: '基础资料', sale: '销售', purchase: '采购', inventory: '库存',
      production: '生产', subcontract: '委外', finance: '财务', crm: 'CRM',
      sample: '打样', engineering: '工程数据'
    }
    fieldPageTree.value = [...byModule.entries()].map(([mk, pages]) => ({
      key: 'group_' + mk, label: moduleLabel[mk] || mk, isGroup: true,
      children: pages.map(p => ({ key: p.key, label: p.name || p.key, isGroup: false }))
    }))
    fieldConfiguredPages.value = new Set(Array.isArray(configured) ? configured : [])
    // 左栏默认只展开「该角色已配置过字段权限」的模块，其余收起，避免整棵树全部铺开
    fieldExpandedKeys.value = fieldPageTree.value
      .filter(g => (g.children || []).some(c => fieldConfiguredPages.value.has(c.key)))
      .map(g => g.key)
    // default-expanded-keys 只在首次渲染生效，换版本号强制重建树让展开集合落地
    fieldTreeVersion.value++
  } catch (e) { fieldPageTree.value = [] }
}

function onFieldPageClick(node) {
  if (node.isGroup) return
  fieldSelectedPageKey.value = node.key
  loadFieldPermission()
}

async function loadFieldPermission() {
  if (!currentRole.roleId || !fieldSelectedPageKey.value) { fieldList.value = []; return }
  fieldLoading.value = true
  try {
    const [metaList, permList] = await Promise.all([
      getFieldMetaList(fieldSelectedPageKey.value).catch(() => []),
      getRoleFieldPermission(currentRole.roleId, fieldSelectedPageKey.value).catch(() => [])
    ])
    const permMap = {}
    if (Array.isArray(permList)) permList.forEach(it => { permMap[it.fieldKey] = it })
    const asBool = (v, def) => (v === undefined || v === null) ? def : Number(v) === 1
    const list = []
    if (Array.isArray(metaList) && metaList.length > 0) {
      metaList.forEach(meta => {
        const perm = permMap[meta.fieldKey] || {}
        const isDef = asBool(meta.defaultRequired, false)
        list.push({
          fieldKey: meta.fieldKey, fieldName: meta.fieldName || meta.fieldKey,
          visible: isDef ? true : asBool(perm.visible, true),
          editable: isDef ? true : asBool(perm.editable, true),
          required: isDef ? true : asBool(perm.required, false),
          defaultRequired: isDef
        })
      })
    } else if (Array.isArray(permList)) {
      permList.forEach(item => {
        list.push({
          fieldKey: item.fieldKey, fieldName: item.fieldName || item.fieldKey,
          visible: asBool(item.visible, true), editable: asBool(item.editable, true),
          required: asBool(item.required, false), defaultRequired: false
        })
      })
    }
    fieldList.value = list
    fieldOriginalList.value = JSON.parse(JSON.stringify(list))
  } finally { fieldLoading.value = false }
}

function fieldAllVisible(v) {
  fieldList.value.forEach(it => {
    if (it.defaultRequired) return
    it.visible = v
    if (!v) { it.editable = false; it.required = false }
  })
}
function fieldAllEditable(v) {
  fieldList.value.forEach(it => {
    if (it.defaultRequired) return
    if (it.visible) { it.editable = v; if (!v) it.required = false }
  })
}
async function onSaveFieldPermission() {
  if (!currentRole.roleId || !fieldSelectedPageKey.value) return
  if (fieldList.value.length === 0) return
  const payload = fieldList.value.map(f => ({
    fieldKey: f.fieldKey,
    visible: (f.defaultRequired ? true : f.visible) ? 1 : 0,
    editable: (f.defaultRequired ? true : f.editable) ? 1 : 0,
    required: (f.defaultRequired ? true : f.required) ? 1 : 0
  }))
  fieldLoading.value = true
  try {
    await saveRoleFieldPermission(currentRole.roleId, fieldSelectedPageKey.value, payload)
    fieldOriginalList.value = JSON.parse(JSON.stringify(fieldList.value))
    // 保存后把该页所属模块加入默认展开集合并重建树，避免"配完的模块在左栏仍是收起状态"
    fieldConfiguredPages.value = new Set([...fieldConfiguredPages.value, fieldSelectedPageKey.value])
    const owner = fieldPageTree.value.find(g => (g.children || []).some(c => c.key === fieldSelectedPageKey.value))
    if (owner && !fieldExpandedKeys.value.includes(owner.key)) fieldExpandedKeys.value = [...fieldExpandedKeys.value, owner.key]
    fieldTreeVersion.value++
    ElMessage.success(tt('字段权限保存成功'))
  } finally { fieldLoading.value = false }
}

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({
  current: 1,
  size: 20,
  keyword: '',
  ...getSortParams()
})

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const defaultForm = {
  roleId: null,
  roleCode: '',
  roleName: '',
  dataScope: '全部数据',
  sortOrder: 0,
  status: '启用',
  remark: ''
}
const form = reactive({ ...defaultForm })

const rules = {
  roleCode: [],
  roleName: [{ required: true, message: tt('请输入角色名称'), trigger: 'blur' }],
  dataScope: [{ required: true, message: tt('请选择数据范围'), trigger: 'change' }]
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageRole({ ...query, ...getSortParams() })
    tableData.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

/** 系统内置角色(is_system=1)不可删除 → 隐藏删除按钮 */
function canDeleteRole(row) {
  return Number(row?.isSystem) !== 1
}

/** 删除角色：二次确认 → 调接口 → 刷新列表。失败提示由 request 拦截器统一弹出，这里不重复弹 */
async function onDeleteRole(row) {
  const name = toRoleNameLabel(row.roleName, row)
  try {
    await ElMessageBox.confirm(
      tt('确定删除角色「{0}」吗？删除后不可恢复。').replace('{0}', name),
      tt('删除确认'),
      { type: 'warning', confirmButtonText: tt('确定删除'), cancelButtonText: tt('取消') }
    )
  } catch (e) {
    return // 用户取消
  }
  try {
    await deleteRole(row.roleId)
    ElMessage.success(tt('删除成功'))
    loadData()
  } catch (e) {
    // 已由 request 拦截器提示（如"该角色已分配给 N 个用户"/"系统内置角色不允许删除"）
  }
}

function onSearch() {
  query.current = 1
  loadData()
}

function onReset() {
  query.keyword = ''
  query.current = 1
  loadData()
}

function resetForm() {
  Object.assign(form, defaultForm)
}

function onAdd() {
  isEdit.value = false
  resetForm()
  autoFillDocCodeOnCreate(form, 'roleCode')
  Object.assign(currentRole, { roleId: null, roleCode: '', roleName: '' })
  activeTab.value = 'info'
  buttonChecked.clear()
  savedButtonCodes.value = []
  resetButtonPermUi()
  dialogVisible.value = true
}

function onDialogClose() {
  formRef.value && formRef.value.resetFields()
  resetForm()
  Object.assign(currentRole, { roleId: null, roleCode: '', roleName: '' })
}

async function onSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    // 提交前规范化：保证后端入库的值是完整中文枚举
    const payload = {
      ...form,
      roleName: normalizeRoleName(form.roleName, form),
      dataScope: normalizeDataScope(form.dataScope),
      status: normalizeStatus(form.status)
    }
    if (isEdit.value) {
      await updateRole(form.roleId, payload)
      ElMessage.success(tt('编辑成功'))
    } else {
      await createRole(payload)
      ElMessage.success(tt('新增成功'))
    }
    dialogVisible.value = false
    loadData()
  })
}

// ===== 角色编辑弹窗：4 Tab =====
const activeTab = ref('info')
const currentRole = reactive({ roleId: null, roleCode: '', roleName: '' })
/** 首次切到「按钮权限」页签时默认选中第一个模块（避免右栏空态） */
watch(activeTab, (t) => {
  if (t === 'button' && !btnActiveModule.value && buttonGroups.value.length) {
    selectModule(buttonGroups.value[0].key)
  }
})

/** 点击角色名称：打开编辑弹窗，加载基本信息+菜单+按钮+字段权限回显 */
async function onEditRole(row) {
  isEdit.value = true
  Object.assign(form, {
    roleId: row.roleId, roleCode: row.roleCode || '', roleName: row.roleName || '',
    dataScope: row.dataScope || '全部数据', sortOrder: row.sortOrder ?? 0,
    status: row.status || '启用', remark: row.remark || ''
  })
  currentRole.roleId = row.roleId
  currentRole.roleCode = row.roleCode || ''
  currentRole.roleName = toRoleNameLabel(row.roleName, row)
  activeTab.value = 'info'
  // 重置权限选中状态
  menuChecked.clear()
  buttonChecked.clear()
  fieldList.value = []
  fieldOriginalList.value = []
  fieldSelectedPageKey.value = ''
  openGroups.clear()
  savedButtonCodes.value = []
  resetButtonPermUi()
  dialogVisible.value = true

  // 并行回显菜单权限 + 按钮权限
  try {
    const [role, btnCodes] = await Promise.all([
      getRole(row.roleId).catch(() => ({ menuIds: [] })),
      // 失败返回 null（区别于"确实 0 条"），避免把"加载失败"显示成"勾选全丢"
      getButtonPermissions(row.roleId).catch(() => null)
    ])
    // 菜单权限回显：只认菜单路径。历史遗留的纯数字菜单编号是旧版授权格式，
    // 不参与勾选/保存——否则"取消勾选保存后"旧编号仍在库里，登录下发时经编号转换照样把菜单发下去，
    // 表现为"改了菜单权限不生效"（2026-11-05 8 服务器实锤）。保存由后端"先删后插"把旧编号一并清除。
    for (const key of (role.menuIds || [])) {
      if (!/^\d+$/.test(key)) menuChecked.add(key)
    }
    // 默认展开第一个有选中的分组
    for (const g of menuGroups.value) {
      if (groupCheckedCount(g) > 0) { openGroups.add(g.key); break }
    }
    // 按钮权限回显
    if (btnCodes === null) {
      ElMessage.warning(tt('按钮权限加载失败（当前显示为空，请关闭后重试）'))
    } else {
      // 关闭标记（xxxOff）只作为判定依据，不能当成勾选项回显
      for (const code of (btnCodes || [])) { if (!isDefaultVisibleOffCode(code)) buttonChecked.add(code) }
      // 默认全部可见：该页没有任何已保存按钮码 → 界面预勾该页全部动作（与 v-permission 规则③一致）
      applyDefaultAllChecked(btnCodes || [])
      savedButtonCodes.value = btnCodes || []
    }
  } catch (e) { /* 回显失败不阻塞 */ }
  // 加载字段权限页面树
  loadFieldPageTree()
}
</script>

<style scoped>
.filter-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.filter-input {
  width: 220px;
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
/* 角色编辑弹窗 */
.role-form :deep(.el-form-item) {
  margin-bottom: 18px;
}
.role-form .form-row {
  display: flex;
  gap: 16px;
}
.role-form .form-row .el-form-item {
  flex: 1;
}
.perm-role-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  margin-bottom: 12px;
  background: #f5f7fa;
  border-radius: 6px;
  color: #606266;
  font-size: 14px;
}
.perm-role-info .el-icon {
  color: #409eff;
}
.perm-actions { margin-left: auto; display: flex; gap: 6px; }
/* 弹窗内容滚动区 */
.role-tabs { max-height: 65vh; }
.role-tabs :deep(.el-tabs__content) { overflow-y: auto; max-height: calc(65vh - 42px); padding-right: 4px; }

/* ===== 菜单权限：分组卡片 ===== */
.menu-group-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 10px;
}
.menu-group-card {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}
.group-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f5f7fa;
  cursor: pointer;
  user-select: none;
}
.group-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}
.group-arrow { transition: transform .2s; font-size: 12px; color: #909399; }
.group-arrow.rotate { transform: rotate(90deg); }
.group-count { font-size: 12px; color: #909399; }
.group-actions { display: flex; gap: 4px; margin-left: 8px; }
.group-actions .el-button { font-size: 11px; padding: 0; }
.group-body { padding: 8px 12px; display: flex; flex-wrap: wrap; gap: 6px 14px; }
.sub-group { width: 100%; margin-bottom: 6px; }
.sub-group-title {
  font-size: 12px;
  font-weight: 600;
  color: #909399;
  padding: 4px 0;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 6px;
}
.sub-group-items { display: flex; flex-wrap: wrap; gap: 6px 14px; }
.menu-item {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
}

/* ===== 按钮权限（2026-10-19 PRD 改版：左页面导航 + 右动作卡片，间距 12px、垂直居中） ===== */
.btnp-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.btnp-role-name { font-size: 13px; font-weight: 600; color: #303133; }
.btnp-toolbar-spacer { flex: 1; }
.btnp-layout {
  display: flex;
  gap: 12px;
  height: 480px;
}
.btnp-left {
  width: 230px;
  flex-shrink: 0;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.btnp-search { margin: 10px 10px 6px; width: auto; }
/* ===== 2026-11-06 重设计：左模块导航 + 右权限矩阵 ===== */
.btnp-module-list { flex: 1; overflow-y: auto; padding: 4px 6px 8px; }
.btnp-module-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  color: #606266;
  border-right: 2px solid transparent;
}
.btnp-module-item:hover { background: #f5f9ff; }
.btnp-module-item.active {
  background: #e6f4ff;
  color: #1890ff;
  font-weight: 600;
  border-right-color: #1890ff;
}
.btnp-module-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.btnp-module-cnt {
  flex: none;
  font-size: 11px;
  line-height: 16px;
  color: #909399;
  background: #f4f4f5;
  border-radius: 8px;
  padding: 0 6px;
}
.btnp-module-item.active .btnp-module-cnt { background: #d9ecff; color: #1890ff; }
.btnp-matrix-head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 6px; }
.btnp-matrix-title { font-size: 15px; color: #303133; }
.btnp-matrix-sub { font-size: 12px; color: #909399; }
.btnp-matrix-tabs { margin-left: auto; display: flex; gap: 4px; background: #f4f4f5; padding: 3px; border-radius: 6px; }
.btnp-matrix-tab {
  border: 0;
  background: transparent;
  color: #606266;
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 4px;
  cursor: pointer;
}
.btnp-matrix-tab.on { background: #fff; color: #1890ff; font-weight: 600; box-shadow: 0 1px 2px rgba(0,0,0,.06); }
.btnp-matrix-tips { display: flex; gap: 14px; flex-wrap: wrap; font-size: 11px; color: #909399; margin-bottom: 8px; }
.btnp-matrix-wrap { border: 1px solid #ebeef5; border-radius: 8px; background: #fff; overflow: auto; max-height: 380px; }
.btnp-matrix { width: 100%; border-collapse: separate; border-spacing: 0; }
.btnp-matrix th, .btnp-matrix td {
  border-bottom: 1px solid #f0f0f0;
  padding: 6px 8px;
  text-align: center;
  font-size: 12px;
  white-space: nowrap;
}
.btnp-matrix thead th { position: sticky; top: 0; background: #fafafa; z-index: 2; }
.btnp-matrix tbody tr:hover td { background: #f7fbff; }
.btnp-mx-page {
  position: sticky;
  left: 0;
  background: #fff;
  text-align: left !important;
  min-width: 156px;
  z-index: 1;
}
.btnp-matrix thead .btnp-mx-page { z-index: 3; background: #fafafa; }
.btnp-matrix tbody tr:hover .btnp-mx-page { background: #f7fbff; }
.btnp-mx-pagename { display: flex; align-items: center; gap: 6px; }
.btnp-mx-label { font-size: 12px; color: #303133; overflow: hidden; text-overflow: ellipsis; }
.btnp-mx-tag {
  font-size: 10px;
  line-height: 15px;
  border-radius: 8px;
  padding: 0 5px;
  background: #f4f4f5;
  color: #909399;
}
.btnp-mx-tag.off { background: #fdf6ec; color: #b88230; }
.btnp-mx-colhead { display: flex; flex-direction: column; align-items: center; gap: 4px; }
.btnp-mx-colname { font-size: 12px; color: #303133; font-weight: 500; }
.btnp-mx-colcnt { font-size: 10px; color: #c0c4cc; }
.btnp-mx-cell { min-width: 56px; }
/* 点击热区：整格 / 整列头 / 整行首都可点，开关本身只作指示，避免 16px 控件点不中 */
.btnp-mx-clickable { cursor: pointer; }
.btnp-mx-cell.btnp-mx-clickable:hover { background: #eef6ff; }
.btnp-mx-colhead.btnp-mx-clickable { padding: 2px 4px; border-radius: 4px; }
.btnp-mx-colhead.btnp-mx-clickable:hover { background: #eef6ff; }
.btnp-mx-pagename.btnp-mx-clickable { padding: 2px 4px; border-radius: 4px; }
.btnp-mx-pagename.btnp-mx-clickable:hover { background: #eef6ff; }
.btnp-mx-sw { pointer-events: none; }
.btnp-mx-dash { color: #dcdfe6; }
.btnp-page-list { flex: 1; overflow-y: auto; padding: 4px 0 8px; }
.btnp-group-label {
  padding: 8px 14px 2px;
  font-size: 12px;
  color: #909399;
  display: flex;
  align-items: center;
  gap: 6px;
}
.btnp-group-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
/* 勾选热区：把可点击范围从 12×12 的方块扩大到方块及其四周，避免点不中 */
.btnp-check-hit {
  display: inline-flex;
  align-items: center;
  padding: 4px 6px;
  margin: -4px 0 -4px -4px;
  cursor: pointer;
  flex-shrink: 0;
}
.btnp-check-hit :deep(.el-checkbox) { height: auto; margin-right: 0; pointer-events: none; }
.btnp-check-hit :deep(.el-checkbox__label) { display: none; }
.btnp-page-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 14px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  border-right: 2px solid transparent;
}
.btnp-page-item:hover { background: #f5f9ff; }
.btnp-page-item.checked { background: #f0f9eb; }
.btnp-page-item.active {
  background: #e6f4ff;
  color: #1890ff;
  font-weight: 600;
  border-right-color: #1890ff;
}
.btnp-page-item.active.checked { background: #e6f4ff; }
.btnp-page-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.btnp-multi-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 12px;
  border-top: 1px solid #ebeef5;
  background: #fafafa;
  font-size: 12px;
  color: #606266;
}
.btnp-multi-info b { color: #1890ff; }
.btnp-fallback-tip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
  padding: 8px 12px;
  border-left: 3px solid #e6a23c;
  background: #fdf6ec;
  border-radius: 0 6px 6px 0;
  font-size: 12px;
  color: #6c4a12;
}
.btnp-act-batch { display: flex; gap: 6px; }
.btnp-act-batch .el-button { flex: 1; margin-left: 0; }
.btnp-page-badge {
  flex-shrink: 0;
  font-size: 10px;
  line-height: 16px;
  padding: 0 6px;
  border-radius: 8px;
  background: #f4f4f5;
  color: #909399;
}
.btnp-page-badge.off { background: #fdf6ec; color: #b88230; }
.btnp-right {
  flex: 1;
  min-width: 0;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fafbfc;
  padding: 12px 14px;
  overflow-y: auto;
}
.btnp-page-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 4px;
}
.btnp-page-title { font-size: 15px; color: #303133; }
.btnp-mode { font-size: 11px; border-radius: 4px; padding: 1px 8px; flex-shrink: 0; }
.btnp-mode.strict { background: #e6f4ff; color: #1890ff; }
.btnp-mode.loose { background: #f4f4f5; color: #909399; }
.btnp-page-ops { margin-left: auto; display: flex; align-items: center; gap: 6px; }
.btnp-hint {
  font-size: 12px;
  color: #909399;
  margin-bottom: 12px;
}
.btnp-act-group {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  margin-bottom: 12px;
  overflow: hidden;
}
.btnp-ag-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: #fafafa;
  border-bottom: 1px solid #ebeef5;
  cursor: pointer;
  user-select: none;
}
.btnp-ag-head b { font-size: 13px; color: #303133; }
.btnp-ag-cnt { font-size: 11px; color: #909399; }
.btnp-ag-caret { transition: transform 0.15s ease; color: #909399; }
.btnp-ag-caret.open { transform: rotate(90deg); }
.btnp-ag-ops { margin-left: auto; display: flex; align-items: center; gap: 6px; }
.btnp-ag-cards {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 12px;
}
.btnp-act-card {
  width: 168px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fff;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.btnp-act-card:hover { border-color: #b8d4f5; box-shadow: 0 2px 8px rgba(24, 144, 255, 0.10); }
.btnp-act-row1 { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.btnp-act-name { font-size: 13px; color: #303133; font-weight: 500; }
.btnp-act-state {
  align-self: flex-start;
  font-size: 10px;
  line-height: 16px;
  padding: 0 6px;
  border-radius: 8px;
  background: #f4f4f5;
  color: #909399;
}
.btnp-act-state.on-cfg { background: #f0f9eb; color: #3d8b2f; }
.btnp-act-state.off { background: #fde2e2; color: #c0392b; }
.btnp-act-state.mixed { background: #fdf6ec; color: #b88230; }
.btnp-act-desc { font-size: 11px; color: #909399; line-height: 1.4; }
.btnp-batch-panel { display: flex; flex-direction: column; gap: 10px; }
.btnp-batch-title { font-size: 13px; font-weight: 600; color: #303133; }
.btnp-batch-row { display: flex; align-items: center; gap: 12px; font-size: 12px; color: #606266; }
.btnp-batch-row > span:first-child { width: 36px; flex-shrink: 0; }
.btnp-batch-foot { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 2px; }
.btnp-batch-impact { font-size: 11px; color: #909399; }

/* ===== 字段权限 ===== */
.field-perm-layout {
  display: flex;
  gap: 12px;
  height: 480px;
}
.field-left-nav {
  width: 220px;
  flex-shrink: 0;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 10px;
  display: flex;
  flex-direction: column;
}
.nav-search { margin-bottom: 8px; }
.page-tree { flex: 1; overflow-y: auto; font-size: 13px; }
.tree-node-label { font-size: 13px; }
.tree-node-label.group-label { font-weight: 600; color: #303133; }
.field-right-main {
  flex: 1;
  min-width: 0;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 12px;
  overflow-y: auto;
}
.field-empty { display: flex; align-items: center; justify-content: center; height: 100%; }
.field-quick-actions { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 10px; }
.default-req { color: #7c3aed; font-weight: 600; }
.lock-icon { color: #7c3aed; vertical-align: middle; margin-right: 4px; }
</style>
