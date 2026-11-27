<template>
  <div class="cost-config-page">
    <!-- 顶部标题 + 操作栏 -->
    <div class="page-header">
      <div class="header-left">
        <div class="title-group">
          <div class="page-title">
            成本配置
            <span class="title-badge">COST CONFIG</span>
          </div>
          <div class="page-sub">{{ $tt("管理核算方法 · 灵活配置成本策略") }}</div>
        </div>
      </div>
      <div class="header-actions">
        <el-button type="primary" class="btn-primary-glow" v-permission="perm('add')" @click="handleAdd">
          <el-icon><Plus /></el-icon>
          新增配置
        </el-button>
        <el-button :icon="Refresh" v-permission="perm('refresh')" @click="loadData">{{ $tt("刷新") }}</el-button>
      </div>
    </div>

    <!-- 数据概览卡片 -->
    <div class="stat-cards">
      <div class="stat-card" v-for="stat in stats" :key="stat.label">
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
          <el-input v-model="search.keyword" :placeholder="$tt('输入配置名搜索')" clearable class="search-input" @keyup.enter="handleSearch">
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button type="primary" v-permission="perm('query')" @click="handleSearch">{{ $tt("搜索") }}</el-button>
          <el-button v-permission="perm('reset')" @click="handleReset">{{ $tt("重置") }}</el-button>
          <el-tooltip :content="$tt('列设置')" placement="top">
            <el-button :icon="Setting" circle v-permission="perm('columnSetting')" @click="openColumnSetting" />
          </el-tooltip>
        </div>
      </div>

      <!-- 列表（border 必开：Element Plus 只有带 border 的表格才允许拖拽列宽） -->
      <el-table ref="tableRef" :data="filteredTableData" v-loading="loading" class="tech-table" border :header-cell-style="headerCellStyle" :key="configVersion" @header-dragend="onHeaderDragend">
        <el-table-column type="index" label="#" width="60" />
        <!-- 动态列渲染：列顺序由列设置拖拽配置驱动（sortedColumns），单元格按 col.type 分支渲染 -->
        <template v-for="col in visibleSortedColumns" :key="col.prop">
          <el-table-column
            :prop="col.prop"
            :label="$tf(col.label)"
            :width="col.width"
            :min-width="col.minWidth"
            :align="col.align || 'left'"
            :sortable="col.sortable || false"
            :show-overflow-tooltip="!!col.tooltip"
            :fixed="getColFixed(col.prop)"
          >
            <template v-if="col.type" #default="{ row }">
              <div v-if="col.type === 'billNo'" class="order-no-cell">
                <span class="order-no-text" @click="handleEdit(row)">{{ row[col.prop] }}</span>
                <el-icon class="link-icon"><Link /></el-icon>
              </div>
              <el-tag v-else-if="col.type === 'method'" :type="methodTagType(row[col.prop])" size="small" effect="light">{{ row[col.prop] }}</el-tag>
              <div v-else-if="col.type === 'enabled'" class="status-cell">
                <span :class="['status-dot', row.enabled ? 'dot-success' : 'dot-info']"></span>
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small" effect="light">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </div>
            </template>
          </el-table-column>
        </template>
        <AuditColumns />
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrap">
        <div class="pagination-info">共 {{ filteredTableData.length }} 条记录</div>
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="550px" @close="resetForm" class="tech-dialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item :label="$tf('配置名')" prop="configName">
          <el-input v-model="form.configName" :placeholder="$tt('请输入配置名')" />
        </el-form-item>
        <el-form-item :label="$tf('默认方法')" prop="defaultMethod">
          <el-select v-model="form.defaultMethod" :placeholder="$tt('请选择方法')" style="width:100%">
            <el-option v-for="m in methodOptions" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$tf('是否启用')" prop="enabled">
          <el-switch v-model="form.enabled" />
        </el-form-item>
        <el-form-item :label="$tf('备注')">
          <el-input v-model="form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="dialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('save')" @click="handleSave" :loading="saving">{{ $tt("保存") }}</el-button>
      </template>
    </el-dialog>

    <!-- 列设置抽屉 -->
    <ColumnSettingDrawer v-model="columnSettingVisible" :page-key="'CostConfigList'" :columns="columns" />
  </div>
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search, Refresh, Setting, Link } from '@element-plus/icons-vue'
import { listCostConfig, saveCostConfig } from '@/api/finance/costConfig'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
import AuditColumns from '@/components/AuditColumns.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('finance:cost-config')

// 列定义（用于列设置抽屉+动态渲染）= 表格全部数据列
// type 标记单元格渲染分支：billNo 配置名链接、method 方法标签、enabled 启用状态标签
// 注意：本页 sortable 为本地排序（非 custom 后端排序），绑定 :sortable="col.sortable || false" 保留原行为
const columns = [
  { prop: 'configName', label: '配置名', minWidth: 180, sortable: true, tooltip: true, type: 'billNo' },
  { prop: 'defaultMethod', label: '默认方法', width: 160, sortable: true, type: 'method' },
  { prop: 'enabled', label: '是否启用', width: 100, type: 'enabled' },
  { prop: 'remark', label: '备注', minWidth: 200, sortable: true, tooltip: true },
  { prop: 'createdAt', label: '创建时间', width: 170, sortable: true }
]
const { columnSettingVisible, openColumnSetting, isColVisible, getColFixed, configVersion, sortedColumns, onHeaderDragend, applySavedWidths } = useColumnSetting('CostConfigList', columns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))
// 列宽持久化（2026-09-24）：表格带 border 才允许拖拽，拖完的宽度写入本页列配置，下次打开自动套用
const tableRef = ref(null)

const loading = ref(false)
const saving = ref(false)
const tableData = ref([])
const activeTab = ref('all')
const search = reactive({ keyword: '' })

const methodOptions = ['移动加权平均', '月末加权平均', '固定成本']

function methodTagType(m) {
  const map = { '移动加权平均': 'primary', '月末加权平均': 'success', '固定成本': 'warning' }
  return map[m] || 'info'
}

const statusTabs = computed(() => [
  { label: '全部', value: 'all', count: tableData.value.length },
  { label: '启用', value: 'enabled', count: tableData.value.filter(r => r.enabled).length },
  { label: '停用', value: 'disabled', count: tableData.value.filter(r => !r.enabled).length }
])

const filteredTableData = computed(() => {
  let list = tableData.value
  if (activeTab.value === 'enabled') list = list.filter(r => r.enabled)
  else if (activeTab.value === 'disabled') list = list.filter(r => !r.enabled)
  if (search.keyword) {
    const kw = search.keyword.toLowerCase()
    list = list.filter(r => (r.configName || '').toLowerCase().includes(kw))
  }
  return list
})

const stats = computed(() => [
  { label: '配置总数', value: tableData.value.length, type: 'primary', icon: 'Document' },
  { label: '启用配置', value: tableData.value.filter(r => r.enabled).length, type: 'success', icon: 'CircleCheck' },
  { label: '停用配置', value: tableData.value.filter(r => !r.enabled).length, type: 'info', icon: 'CircleClose' },
  { label: '核算方法数', value: new Set(tableData.value.map(r => r.defaultMethod).filter(Boolean)).size, type: 'warning', icon: 'Tools' }
])

const headerCellStyle = {
  background: 'rgba(99, 102, 241, 0.04)',
  color: '#1f2937',
  fontWeight: 600,
  borderBottom: '2px solid #e0e7ff'
}

function switchTab(value) {
  activeTab.value = value
}

async function loadData() {
  loading.value = true
  try {
    const res = await listCostConfig()
    tableData.value = Array.isArray(res) ? res : (res?.records || [])
  } catch (e) {
    ElMessage.error(e.message || tt('加载失败'))
  } finally {
    loading.value = false
    // 数据到位后再套一次已保存的列宽（表格重建后列对象是新的）
    nextTick(() => applySavedWidths(tableRef))
  }
}

function handleSearch() {
  // 前端过滤，无需重新拉数据
}

function handleReset() {
  search.keyword = ''
  activeTab.value = 'all'
}

// 新增/编辑
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const isEdit = ref(false)
const form = reactive({
  id: null,
  configName: '',
  defaultMethod: '移动加权平均',
  enabled: true,
  remark: ''
})
const rules = {
  configName: [{ required: true, message: tt('请输入配置名'), trigger: 'blur' }],
  defaultMethod: [{ required: true, message: tt('请选择默认方法'), trigger: 'change' }]
}

function handleAdd() {
  isEdit.value = false
  Object.assign(form, { id: null, configName: '', defaultMethod: '移动加权平均', enabled: true, remark: '' })
  dialogTitle.value = '新增成本核算配置'
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  Object.assign(form, row)
  dialogTitle.value = '编辑成本核算配置'
  dialogVisible.value = true
}

async function handleSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    await saveCostConfig({ ...form })
    ElMessage.success(tt('保存成功'))
    dialogVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error(e.message || tt('保存失败'))
  } finally {
    saving.value = false
  }
}

function resetForm() {
  formRef.value && formRef.value.resetFields()
}

onMounted(() => {
  loadData()
})
useAutoRefresh(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.cost-config-page {
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

  .header-actions { display: flex; gap: 8px; }
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

  .search-right {
    display: flex;
    gap: 8px;
    align-items: center;
  }

  .search-input {
    width: 280px;
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
  .cost-config-page { padding: 12px; }
  .stat-cards { grid-template-columns: 1fr; }
  .search-bar {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }
  .status-tabs { flex-wrap: wrap; }
}
</style>
