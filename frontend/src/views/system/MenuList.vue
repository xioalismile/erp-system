<template>
  <div class="erp-page">
    <div class="filter-bar">
      <el-input
        v-model="filterKeyword"
        placeholder="菜单名称过滤"
        class="filter-input"
        clearable
      />
      <el-button type="primary" @click="loadData">刷新</el-button>
      <el-button type="success" @click="onAdd(null)">新增顶级菜单</el-button>
    </div>

    <el-table
      :data="tableData"
      border
      row-key="menuId"
      :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
      :default-expand-all="false"
      v-loading="loading"
    >
      <el-table-column prop="menuName" label="菜单名称" min-width="180" />
      <el-table-column prop="menuCode" label="菜单编码" min-width="140" />
      <el-table-column label="菜单类型" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="menuTypeTag(row.menuType)" size="small">{{ row.menuType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="routePath" label="路由路径" min-width="140" />
      <el-table-column prop="componentPath" label="组件路径" min-width="160" />
      <el-table-column prop="permissionCode" label="权限标识" min-width="160" />
      <el-table-column prop="sortOrder" label="排序" width="80" align="right" />
      <el-table-column label="是否启用" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.isEnabled ? 'success' : 'info'" size="small">
            {{ row.isEnabled ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="onAdd(row)">新增子菜单</el-button>
          <el-button size="small" @click="onEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      :title="isEdit ? '编辑菜单' : '新增菜单'"
      v-model="dialogVisible"
      width="680px"
      @close="onDialogClose"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="110px">
        <el-form-item label="上级菜单" prop="parentMenuId">
          <el-tree-select
            v-model="form.parentMenuId"
            :data="menuTreeSelectData"
            :props="{ label: 'menuName', children: 'children', value: 'menuId' }"
            check-strictly
            clearable
            placeholder="留空表示顶级菜单"
            style="width:100%"
          />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="菜单名称" prop="menuName">
              <el-input v-model="form.menuName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="菜单编码" prop="menuCode">
              <el-input v-model="form.menuCode" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="菜单类型" prop="menuType">
              <el-select v-model="form.menuType" placeholder="请选择" style="width:100%">
                <el-option label="目录" value="目录" />
                <el-option label="菜单" value="菜单" />
                <el-option label="按钮" value="按钮" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="图标" prop="icon">
              <el-input v-model="form.icon" placeholder="如图标名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="路由路径" prop="routePath">
              <el-input v-model="form.routePath" placeholder="如 /system/user" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="组件路径" prop="componentPath">
              <el-input v-model="form.componentPath" placeholder="如 system/UserList" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="权限标识" prop="permissionCode">
              <el-input v-model="form.permissionCode" placeholder="如 system:user:add" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序" prop="sortOrder">
              <el-input-number v-model="form.sortOrder" :min="0" controls-position="right" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="是否可见" prop="isVisible">
              <el-switch v-model="form.isVisible" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否启用" prop="isEnabled">
              <el-switch v-model="form.isEnabled" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listMenu, getMenuTree, getMenu, createMenu, updateMenu, deleteMenu
} from '@/api/system/menu'

const loading = ref(false)
const rawTableData = ref([])
const filterKeyword = ref('')

const tableData = computed(() => {
  if (!filterKeyword.value) return rawTableData.value
  const kw = filterKeyword.value.toLowerCase()
  function filterNodes(nodes) {
    const result = []
    nodes.forEach(node => {
      const children = node.children ? filterNodes(node.children) : []
      const matched = (node.menuName || '').toLowerCase().includes(kw)
        || (node.menuCode || '').toLowerCase().includes(kw)
      if (matched || children.length > 0) {
        result.push({ ...node, children })
      }
    })
    return result
  }
  return filterNodes(rawTableData.value)
})

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const menuTreeSelectData = ref([])
const defaultForm = {
  menuId: null,
  parentMenuId: null,
  menuCode: '',
  menuName: '',
  menuType: '菜单',
  icon: '',
  routePath: '',
  componentPath: '',
  permissionCode: '',
  sortOrder: 0,
  isVisible: 1,
  isEnabled: 1
}
const form = reactive({ ...defaultForm })

const rules = {
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  menuCode: [{ required: true, message: '请输入菜单编码', trigger: 'blur' }],
  menuType: [{ required: true, message: '请选择菜单类型', trigger: 'change' }]
}

function menuTypeTag(type) {
  return { '目录': 'info', '菜单': 'success', '按钮': 'warning' }[type] || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const data = await getMenuTree()
    rawTableData.value = data || []
    menuTreeSelectData.value = [
      { menuId: null, menuName: '顶级菜单', children: data || [] }
    ]
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, defaultForm)
}

function onAdd(parent) {
  isEdit.value = false
  resetForm()
  form.parentMenuId = parent ? parent.menuId : null
  if (menuTreeSelectData.value.length === 0) {
    menuTreeSelectData.value = [
      { menuId: null, menuName: '顶级菜单', children: [] }
    ]
  }
  dialogVisible.value = true
}

async function onEdit(row) {
  isEdit.value = true
  resetForm()
  try {
    const detail = await getMenu(row.menuId)
    Object.assign(form, detail)
  } catch (e) {
    Object.assign(form, row)
  }
  if (menuTreeSelectData.value.length === 0) {
    menuTreeSelectData.value = [
      { menuId: null, menuName: '顶级菜单', children: [] }
    ]
  }
  dialogVisible.value = true
}

function onDialogClose() {
  formRef.value && formRef.value.resetFields()
  resetForm()
}

async function onSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (isEdit.value) {
      await updateMenu(form.menuId, form)
      ElMessage.success('编辑成功')
    } else {
      await createMenu(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  })
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确定删除菜单 [${row.menuName}] 吗？子菜单也会被一并删除。`, '提示', {
    type: 'warning'
  })
  await deleteMenu(row.menuId)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.filter-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.filter-input {
  width: 240px;
}
</style>
