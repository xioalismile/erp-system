<template>
  <div class="erp-page">
    <div class="filter-bar">
      <el-input
        v-model="query.keyword"
        placeholder="角色名称关键词"
        class="filter-input"
        clearable
        @keyup.enter="onSearch"
      />
      <el-button type="primary" @click="onSearch">查询</el-button>
      <el-button @click="onReset">重置</el-button>
      <el-button type="success" @click="onAdd">新增</el-button>
    </div>

    <el-table :data="tableData" border v-loading="loading">
      <el-table-column prop="roleCode" label="角色编码" min-width="130" />
      <el-table-column prop="roleName" label="角色名称" min-width="140" />
      <el-table-column prop="roleType" label="角色类型" min-width="110" />
      <el-table-column prop="dataScope" label="数据范围" min-width="110" />
      <el-table-column prop="sortOrder" label="排序" width="90" align="right" />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="onEdit(row)">编辑</el-button>
          <el-button size="small" type="warning" @click="onAssignPermissions(row)">分配权限</el-button>
          <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      :title="isEdit ? '编辑角色' : '新增角色'"
      v-model="dialogVisible"
      width="600px"
      @close="onDialogClose"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="form.roleCode" placeholder="如 ROLE_ADMIN" />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" />
        </el-form-item>
        <el-form-item label="角色类型" prop="roleType">
          <el-select v-model="form.roleType" placeholder="请选择" style="width:100%">
            <el-option label="业务角色" value="业务角色" />
            <el-option label="系统角色" value="系统角色" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据范围" prop="dataScope">
          <el-select v-model="form.dataScope" placeholder="请选择" style="width:100%">
            <el-option label="全部数据" value="全部数据" />
            <el-option label="本部门数据" value="本部门数据" />
            <el-option label="本部门及子部门" value="本部门及子部门" />
            <el-option label="仅本人数据" value="仅本人数据" />
            <el-option label="自定义" value="自定义" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" placeholder="请选择" style="width:100%">
            <el-option label="启用" value="启用" />
            <el-option label="停用" value="停用" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配权限弹窗 -->
    <el-dialog title="分配权限" v-model="permDialogVisible" width="520px">
      <div style="margin-bottom:8px;color:#666">
        当前角色：{{ currentRole.roleName }} ({{ currentRole.roleCode }})
      </div>
      <el-tree
        ref="permTreeRef"
        :data="menuTree"
        show-checkbox
        node-key="menuId"
        :props="{ label: 'menuName', children: 'children' }"
        :default-expand-all="true"
      />
      <template #footer>
        <el-button @click="permDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmitPermissions">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageRole, getRole, createRole, updateRole, deleteRole, assignPermissions
} from '@/api/system/role'
import { getMenuTree } from '@/api/system/menu'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({
  current: 1,
  size: 20,
  keyword: ''
})

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const defaultForm = {
  roleId: null,
  roleCode: '',
  roleName: '',
  roleType: '业务角色',
  dataScope: '全部数据',
  sortOrder: 0,
  status: '启用',
  remark: ''
}
const form = reactive({ ...defaultForm })

const rules = {
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleType: [{ required: true, message: '请选择角色类型', trigger: 'change' }],
  dataScope: [{ required: true, message: '请选择数据范围', trigger: 'change' }]
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageRole(query)
    tableData.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
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
  dialogVisible.value = true
}

async function onEdit(row) {
  isEdit.value = true
  resetForm()
  try {
    const detail = await getRole(row.roleId)
    Object.assign(form, detail)
  } catch (e) {
    Object.assign(form, row)
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
      await updateRole(form.roleId, form)
      ElMessage.success('编辑成功')
    } else {
      await createRole(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  })
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确定删除角色 [${row.roleName}] 吗？`, '提示', {
    type: 'warning'
  })
  await deleteRole(row.roleId)
  ElMessage.success('删除成功')
  loadData()
}

// ===== 分配权限 =====
const permDialogVisible = ref(false)
const permTreeRef = ref(null)
const currentRole = reactive({ roleId: null, roleCode: '', roleName: '' })
const menuTree = ref([])

async function loadMenuTree() {
  try {
    menuTree.value = (await getMenuTree()) || []
  } catch (e) {
    menuTree.value = []
  }
}

async function onAssignPermissions(row) {
  Object.assign(currentRole, {
    roleId: row.roleId,
    roleCode: row.roleCode,
    roleName: row.roleName
  })
  if (menuTree.value.length === 0) {
    await loadMenuTree()
  }
  permDialogVisible.value = true
  // 等待树渲染后设置已选节点
  setTimeout(() => {
    if (permTreeRef.value) {
      permTreeRef.value.setCheckedKeys(Array.isArray(row.menuIds) ? row.menuIds : [])
    }
  }, 50)
}

async function onSubmitPermissions() {
  const checkedKeys = permTreeRef.value
    ? permTreeRef.value.getCheckedKeys(false)
    : []
  const halfCheckedKeys = permTreeRef.value
    ? permTreeRef.value.getHalfCheckedKeys()
    : []
  const menuIds = [...checkedKeys, ...halfCheckedKeys]
  await assignPermissions(currentRole.roleId, menuIds)
  ElMessage.success('分配权限成功')
  permDialogVisible.value = false
  loadData()
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
</style>
