<template>
  <div class="erp-page employee-perm-page">
    <div class="perm-layout">
      <!-- 左侧：员工列表 -->
      <div class="emp-list-panel">
        <div class="panel-header">
          <span class="panel-title">{{ $tt("员工列表") }}</span>
          <el-input
            v-model="empKeyword"
            :placeholder="$tt('搜索员工')"
            clearable
            size="small"
            @keyup.enter="loadEmployees"
            style="width: 140px"
          />
        </div>
        <el-table
          :data="employeeList"
          border
          highlight-current-row
          v-loading="empLoading"
          height="calc(100vh - 220px)"
          @row-click="onSelectEmployee"
          size="small"
        >
          <el-table-column prop="employeeCode" :label="$tf('工号')" width="90" />
          <el-table-column prop="employeeName" :label="$tf('姓名')" min-width="90" />
          <el-table-column prop="departmentName" :label="$tf('部门')" min-width="100" />
          <el-table-column :label="$tf('已配置')" width="70" align="center">
            <template #default="{ row }">
              <el-tag v-if="hasConfig(row.employeeId)" type="success" size="small">{{ $tt("是") }}</el-tag>
              <el-tag v-else type="info" size="small">{{ $tt("否") }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          small
          layout="prev, pager, next"
          :total="empTotal"
          :current-page="empQuery.current"
          :page-size="empQuery.size"
          @current-change="onEmpPageChange"
          style="margin-top: 8px; justify-content: center;"
        />
      </div>

      <!-- 右侧：权限配置 -->
      <div class="perm-config-panel">
        <template v-if="currentEmployee.employeeId">
          <div class="config-header">
            <span class="config-title">
              {{ currentEmployee.employeeName }} - 权限补充设置
            </span>
            <div>
              <el-button v-permission="perm('save')" type="primary" @click="onSave" :loading="saving">{{ $tt("保存配置") }}</el-button>
              <el-button v-permission="perm('clear')" @click="onClear">{{ $tt("清空配置") }}</el-button>
            </div>
          </div>

          <el-tabs v-model="activeTab" class="perm-tabs">
            <!-- 指定仓库 -->
            <el-tab-pane :label="$tf('指定仓库')" name="warehouse">
              <div class="tab-toolbar">
                <el-input v-model="filterMap.warehouse" :placeholder="$tt('搜索仓库')" clearable size="small" style="width:200px" />
                <el-button v-permission="perm('columnSetting')" size="small" @click="onSelectAll('warehouse')">{{ $tt("全选") }}</el-button>
                <el-button v-permission="perm('invertSelect')" size="small" @click="onInvertSelect('warehouse')">{{ $tt("反选") }}</el-button>
                <el-button v-permission="perm('clear')" size="small" @click="onClearSelect('warehouse')">{{ $tt("清空") }}</el-button>
                <span class="select-count">已选 {{ permData.warehouseIds.length }} 个</span>
              </div>
              <el-checkbox-group v-model="permData.warehouseIds" class="checkbox-grid">
                <el-checkbox
                  v-for="w in filteredWarehouses"
                  :key="w.stockId"
                  :label="w.stockId"
                  class="checkbox-item"
                >
                  {{ w.stockCode }} - {{ w.stockName }}
                </el-checkbox>
              </el-checkbox-group>
              <el-empty v-if="filteredWarehouses.length === 0" :description="$tt('暂无仓库数据')" />
            </el-tab-pane>

            <!-- 指定客户 -->
            <el-tab-pane :label="$tf('指定客户')" name="customer">
              <div class="tab-toolbar">
                <el-input v-model="filterMap.customer" :placeholder="$tt('搜索客户')" clearable size="small" style="width:200px" />
                <el-button v-permission="perm('columnSetting')" size="small" @click="onSelectAll('customer')">{{ $tt("全选") }}</el-button>
                <el-button v-permission="perm('invertSelect')" size="small" @click="onInvertSelect('customer')">{{ $tt("反选") }}</el-button>
                <el-button v-permission="perm('clear')" size="small" @click="onClearSelect('customer')">{{ $tt("清空") }}</el-button>
                <span class="select-count">已选 {{ permData.customerIds.length }} 个</span>
              </div>
              <el-checkbox-group v-model="permData.customerIds" class="checkbox-grid">
                <el-checkbox
                  v-for="c in filteredCustomers"
                  :key="c.customerId"
                  :label="c.customerId"
                  class="checkbox-item"
                >
                  {{ c.customerCode }} - {{ c.customerName }}
                </el-checkbox>
              </el-checkbox-group>
              <el-empty v-if="filteredCustomers.length === 0" :description="$tt('暂无客户数据')" />
            </el-tab-pane>

            <!-- 指定供应商 -->
            <el-tab-pane :label="$tf('指定供应商')" name="supplier">
              <div class="tab-toolbar">
                <el-input v-model="filterMap.supplier" :placeholder="$tt('搜索供应商')" clearable size="small" style="width:200px" />
                <el-button v-permission="perm('columnSetting')" size="small" @click="onSelectAll('supplier')">{{ $tt("全选") }}</el-button>
                <el-button v-permission="perm('invertSelect')" size="small" @click="onInvertSelect('supplier')">{{ $tt("反选") }}</el-button>
                <el-button v-permission="perm('clear')" size="small" @click="onClearSelect('supplier')">{{ $tt("清空") }}</el-button>
                <span class="select-count">已选 {{ permData.supplierIds.length }} 个</span>
              </div>
              <el-checkbox-group v-model="permData.supplierIds" class="checkbox-grid">
                <el-checkbox
                  v-for="s in filteredSuppliers"
                  :key="s.supplierId"
                  :label="s.supplierId"
                  class="checkbox-item"
                >
                  {{ s.supplierCode }} - {{ s.supplierName }}
                </el-checkbox>
              </el-checkbox-group>
              <el-empty v-if="filteredSuppliers.length === 0" :description="$tt('暂无供应商数据')" />
            </el-tab-pane>

            <!-- 指定货品类别 -->
            <el-tab-pane :label="$tf('指定货品类别')" name="category">
              <div class="tab-toolbar">
                <el-input v-model="filterMap.category" :placeholder="$tt('搜索类别')" clearable size="small" style="width:200px" />
                <div>
                  <el-button v-permission="perm('expand')" size="small" @click="onExpandAll">{{ $tt("展开全部") }}</el-button>
                  <el-button v-permission="perm('collapse')" size="small" @click="onCollapseAll">{{ $tt("收起全部") }}</el-button>
                </div>
                <el-button v-permission="perm('selectAll')" size="small" @click="onSelectAll('category')">{{ $tt("全选") }}</el-button>
                <el-button v-permission="perm('invertSelect')" size="small" @click="onInvertSelect('category')">{{ $tt("反选") }}</el-button>
                <span class="select-count">已选 {{ permData.categoryIds.length }} 个</span>
              </div>
              <el-tree
                ref="categoryTreeRef"
                :data="categoryTree"
                show-checkbox
                node-key="categoryId"
                :props="{ label: 'categoryName', children: 'children' }"
                :default-expand-all="false"
                :filter-node-method="filterCategoryNode"
                @check="onCategoryCheck"
                class="category-tree"
              />
              <el-empty v-if="categoryTree.length === 0" :description="$tt('暂无类别数据')" />
            </el-tab-pane>
          </el-tabs>

          <el-alert
            type="info"
            :closable="false"
            style="margin-top: 12px"
            :title="$tt('说明：员工权限补充用于限制员工只能查看/操作指定的仓库、客户、供应商和货品类别。不选则表示不限制（可查看全部）。')"
          />
        </template>
        <el-empty v-else :description="$tt('请从左侧选择一位员工')" style="margin-top: 120px" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { usePagePerm } from '@/utils/pagePerm'
const perm = usePagePerm('system:employee-perm')
import { ref, reactive, computed, watch, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listEmployee } from '@/api/basedata/employee'
import { listAllWarehouse } from '@/api/basedata/warehouse'
import { listAllCustomer } from '@/api/basedata/customer'
import { listAllSupplier } from '@/api/basedata/supplier'
import { treeMaterialCategory } from '@/api/basedata/material-category'
import {
  getSysConfigByKey,
  batchUpdateSysConfig
} from '@/api/system/sysConfig'
import { tt } from '@/i18n'

// ===== 员工列表 =====
const empLoading = ref(false)
const employeeList = ref([])
const empTotal = ref(0)
const empKeyword = ref('')
const empQuery = reactive({ current: 1, size: 20 })
const currentEmployee = reactive({ employeeId: null, employeeName: '' })
const configMap = ref({}) // employeeId -> hasConfig

async function loadEmployees() {
  empLoading.value = true
  try {
    const res = await listEmployee({ current: empQuery.current, size: empQuery.size, keyword: empKeyword.value })
    employeeList.value = res.records || []
    empTotal.value = res.total || 0
  } finally {
    empLoading.value = false
  }
}

function onEmpPageChange(page) {
  empQuery.current = page
  loadEmployees()
}

function hasConfig(empId) {
  return configMap.value[empId] === true
}

// ===== 基础数据 =====
const warehouseList = ref([])
const customerList = ref([])
const supplierList = ref([])
const categoryTree = ref([])
const categoryTreeRef = ref(null)

async function loadBaseData() {
  const [w, c, s, cat] = await Promise.all([
    listAllWarehouse(),
    listAllCustomer(),
    listAllSupplier(),
    treeMaterialCategory()
  ])
  warehouseList.value = w || []
  customerList.value = c || []
  supplierList.value = s || []
  categoryTree.value = cat || []
}

// ===== 筛选 =====
const filterMap = reactive({ warehouse: '', customer: '', supplier: '', category: '' })

const filteredWarehouses = computed(() => {
  if (!filterMap.warehouse) return warehouseList.value
  const kw = filterMap.warehouse.toLowerCase()
  return warehouseList.value.filter(w =>
    (w.stockCode || '').toLowerCase().includes(kw) ||
    (w.stockName || '').toLowerCase().includes(kw)
  )
})
const filteredCustomers = computed(() => {
  if (!filterMap.customer) return customerList.value
  const kw = filterMap.customer.toLowerCase()
  return customerList.value.filter(c =>
    (c.customerCode || '').toLowerCase().includes(kw) ||
    (c.customerName || '').toLowerCase().includes(kw)
  )
})
const filteredSuppliers = computed(() => {
  if (!filterMap.supplier) return supplierList.value
  const kw = filterMap.supplier.toLowerCase()
  return supplierList.value.filter(s =>
    (s.supplierCode || '').toLowerCase().includes(kw) ||
    (s.supplierName || '').toLowerCase().includes(kw)
  )
})

watch(() => filterMap.category, (val) => {
  if (categoryTreeRef.value) categoryTreeRef.value.filter(val)
})

function filterCategoryNode(value, data) {
  if (!value) return true
  return (data.categoryName || '').toLowerCase().includes(value.toLowerCase())
}

function onExpandAll() {
  const tree = categoryTreeRef.value
  if (!tree) return
  const expand = (nodes) => {
    nodes.forEach(n => {
      tree.store.nodesMap[n.categoryId] && (tree.store.nodesMap[n.categoryId].expanded = true)
      if (n.children) expand(n.children)
    })
  }
  expand(categoryTree.value)
}
function onCollapseAll() {
  const tree = categoryTreeRef.value
  if (!tree) return
  Object.values(tree.store.nodesMap).forEach(n => { n.expanded = false })
}

// ===== 权限数据 =====
const activeTab = ref('warehouse')
const permData = reactive({
  warehouseIds: [],
  customerIds: [],
  supplierIds: [],
  categoryIds: []
})
const saving = ref(false)

function resetPermData() {
  permData.warehouseIds = []
  permData.customerIds = []
  permData.supplierIds = []
  permData.categoryIds = []
}

async function onSelectEmployee(row) {
  currentEmployee.employeeId = row.employeeId
  currentEmployee.employeeName = row.employeeName
  resetPermData()
  await loadPermConfig(row.employeeId)
}

async function loadPermConfig(empId) {
  try {
    const raw = await getSysConfigByKey(`employee_perm_${empId}`)
    if (raw && raw.configValue) {
      const parsed = JSON.parse(raw.configValue)
      // 过滤历史脏数据（旧bug勾选undefined保存为null）
      permData.warehouseIds = (parsed.warehouseIds || []).filter(v => v != null)
      permData.customerIds = (parsed.customerIds || []).filter(v => v != null)
      permData.supplierIds = (parsed.supplierIds || []).filter(v => v != null)
      permData.categoryIds = (parsed.categoryIds || []).filter(v => v != null)
      configMap.value[empId] = true
      await nextTick()
      if (categoryTreeRef.value && permData.categoryIds.length > 0) {
        categoryTreeRef.value.setCheckedKeys(permData.categoryIds)
      }
    } else {
      configMap.value[empId] = false
      if (categoryTreeRef.value) categoryTreeRef.value.setCheckedKeys([])
    }
  } catch {
    configMap.value[empId] = false
  }
}

/**
 * 货品类别树的全部叶子节点 id。
 * 全选/反选都以「叶子」为操作单位：父节点由 el-tree 的级联自动算出；
 * 若直接对"全部节点集合"取反，原本勾中的父节点会级联把其下所有子节点一起勾上 → 反选结果失真成"全选"。
 */
const categoryLeafIds = computed(() => {
  const ids = []
  const walk = (nodes) => {
    (nodes || []).forEach(n => {
      if (n.children && n.children.length) walk(n.children)
      else ids.push(n.categoryId)
    })
  }
  walk(categoryTree.value)
  return ids
})

/** 回写树的勾选结果（含被级联勾满的父节点：保存后挂在父类别下的物料同样可见） */
function syncCategoryIds() {
  const tree = categoryTreeRef.value
  permData.categoryIds = tree ? tree.getCheckedKeys() : []
}

function onCategoryCheck() {
  syncCategoryIds()
}

function onSelectAll(type) {
  if (type === 'warehouse') permData.warehouseIds = warehouseList.value.map(w => w.stockId)
  if (type === 'customer') permData.customerIds = customerList.value.map(c => c.customerId)
  if (type === 'supplier') permData.supplierIds = supplierList.value.map(s => s.supplierId)
  if (type === 'category' && categoryTreeRef.value) {
    categoryTreeRef.value.setCheckedKeys(categoryLeafIds.value)
    syncCategoryIds()
  }
}

/**
 * 反选：原本勾上的取消、原本没勾的勾上。
 * 与「全选」同口径 —— 作用于全部数据（不受上方搜索框过滤影响），
 * 保证"全选 → 反选"两次点击后回到空选，不因过滤残留出现"看着反选了其实没变"。
 */
function onInvertSelect(type) {
  if (type === 'warehouse') {
    const sel = new Set(permData.warehouseIds)
    permData.warehouseIds = warehouseList.value.map(w => w.stockId).filter(id => !sel.has(id))
  }
  if (type === 'customer') {
    const sel = new Set(permData.customerIds)
    permData.customerIds = customerList.value.map(c => c.customerId).filter(id => !sel.has(id))
  }
  if (type === 'supplier') {
    const sel = new Set(permData.supplierIds)
    permData.supplierIds = supplierList.value.map(s => s.supplierId).filter(id => !sel.has(id))
  }
  if (type === 'category' && categoryTreeRef.value) {
    const checked = new Set(categoryTreeRef.value.getCheckedKeys())
    categoryTreeRef.value.setCheckedKeys(categoryLeafIds.value.filter(id => !checked.has(id)))
    syncCategoryIds()
  }
}

function onClearSelect(type) {
  if (type === 'warehouse') permData.warehouseIds = []
  if (type === 'customer') permData.customerIds = []
  if (type === 'supplier') permData.supplierIds = []
  if (type === 'category' && categoryTreeRef.value) categoryTreeRef.value.setCheckedKeys([])
}

async function onSave() {
  if (!currentEmployee.employeeId) return
  saving.value = true
  try {
    const configValue = JSON.stringify({
      warehouseIds: permData.warehouseIds,
      customerIds: permData.customerIds,
      supplierIds: permData.supplierIds,
      categoryIds: permData.categoryIds
    })
    await batchUpdateSysConfig([{
      configKey: `employee_perm_${currentEmployee.employeeId}`,
      configValue,
      configName: `员工权限补充-${currentEmployee.employeeName}`,
      configGroup: 'employee_perm',
      configType: 'string'
    }])
    configMap.value[currentEmployee.employeeId] = true
    ElMessage.success(tt('保存成功'))
  } catch (e) {
    ElMessage.error(tt('保存失败'))
  } finally {
    saving.value = false
  }
}

async function onClear() {
  try {
    await ElMessageBox.confirm(tt('确定清空当前员工的所有权限补充配置吗？'), tt('提示'), { type: 'warning' })
    resetPermData()
    if (categoryTreeRef.value) categoryTreeRef.value.setCheckedKeys([])
    await batchUpdateSysConfig([{
      configKey: `employee_perm_${currentEmployee.employeeId}`,
      configValue: '',
      configName: `员工权限补充-${currentEmployee.employeeName}`,
      configGroup: 'employee_perm',
      configType: 'string'
    }])
    configMap.value[currentEmployee.employeeId] = false
    ElMessage.success(tt('已清空'))
  } catch {}
}

onMounted(async () => {
  await loadBaseData()
  await loadEmployees()
})
</script>

<style scoped lang="scss">
.employee-perm-page { padding: 16px; }
.perm-layout {
  display: flex;
  gap: 16px;
  height: calc(100vh - 120px);
}
.emp-list-panel {
  width: 360px;
  flex-shrink: 0;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  padding: 12px;
  display: flex;
  flex-direction: column;
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  .panel-title {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
  }
}
.perm-config-panel {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  padding: 16px;
  overflow-y: auto;
}
.config-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
  .config-title {
    font-size: 15px;
    font-weight: 600;
    color: #303133;
  }
}
.perm-tabs {
  margin-top: 8px;
}
.tab-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
  .select-count {
    margin-left: auto;
    font-size: 13px;
    color: #909399;
  }
}
.checkbox-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 8px;
  max-height: calc(100vh - 360px);
  overflow-y: auto;
  padding: 8px;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
}
.checkbox-item {
  margin-right: 0;
  padding: 6px 8px;
  border-radius: 4px;
  &:hover {
    background: #f5f7fa;
  }
}
.category-tree {
  max-height: calc(100vh - 360px);
  overflow-y: auto;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  padding: 8px;
}
:deep(.el-table) {
  cursor: pointer;
}
</style>
