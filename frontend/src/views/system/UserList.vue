<template>
  <div class="erp-page">
    <div class="filter-bar">
      <el-input
        v-model="query.keyword"
        placeholder="用户名/真实姓名"
        class="filter-input"
        clearable
        @keyup.enter="onSearch"
      />
      <el-select v-model="query.isEnabled" placeholder="状态" clearable class="filter-select">
        <el-option label="启用" :value="1" />
        <el-option label="禁用" :value="0" />
      </el-select>
      <el-button type="primary" @click="onSearch">查询</el-button>
      <el-button @click="onReset">重置</el-button>
      <el-button type="success" @click="onAdd">新增</el-button>
    </div>

    <el-table :data="tableData" border v-loading="loading">
      <el-table-column prop="userCode" label="用户编码" min-width="120" />
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="realName" label="真实姓名" min-width="120" />
      <el-table-column prop="email" label="邮箱" min-width="180" />
      <el-table-column prop="mobilePhone" label="手机号" min-width="130" />
      <el-table-column label="是否管理员" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.isAdmin ? 'danger' : 'info'" size="small">
            {{ row.isAdmin ? '管理员' : '普通' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.isEnabled ? 'success' : 'info'" size="small">
            {{ row.isEnabled ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="onEdit(row)">编辑</el-button>
          <el-button size="small" type="warning" @click="onAssignRoles(row)">分配角色</el-button>
          <el-button size="small" type="primary" @click="onResetPassword(row)">重置密码</el-button>
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
      :title="isEdit ? '编辑用户' : '新增用户'"
      v-model="dialogVisible"
      width="680px"
      @close="onDialogClose"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="110px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="用户编码" prop="userCode">
              <el-input v-model="form.userCode" placeholder="如 U001" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="form.username" placeholder="登录用户名" :disabled="isEdit" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="真实姓名" prop="realName">
              <el-input v-model="form.realName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="用户类型" prop="userType">
              <el-select v-model="form.userType" placeholder="请选择" style="width:100%">
                <el-option label="内部用户" value="内部用户" />
                <el-option label="外部用户" value="外部用户" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="form.email" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号" prop="mobilePhone">
              <el-input v-model="form.mobilePhone" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="是否启用" prop="isEnabled">
              <el-switch v-model="form.isEnabled" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否管理员" prop="isAdmin">
              <el-switch v-model="form.isAdmin" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色弹窗 -->
    <el-dialog title="分配角色" v-model="roleDialogVisible" width="560px">
      <div style="margin-bottom:8px;color:#666">
        当前用户：{{ currentUser.username }} ({{ currentUser.realName }})
      </div>
      <el-checkbox-group v-model="selectedRoleIds">
        <div v-for="r in allRoles" :key="r.roleId" style="line-height:32px">
          <el-checkbox :label="r.roleId">
            {{ r.roleName }} ({{ r.roleCode }})
          </el-checkbox>
        </div>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmitRoles">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageUser, getUser, createUser, updateUser, deleteUser, resetPassword, assignRoles
} from '@/api/system/user'
import { listAllRole } from '@/api/system/role'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({
  current: 1,
  size: 20,
  keyword: '',
  isEnabled: ''
})

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const defaultForm = {
  userId: null,
  userCode: '',
  username: '',
  realName: '',
  email: '',
  mobilePhone: '',
  userType: '内部用户',
  isEnabled: 1,
  isAdmin: 0,
  remark: ''
}
const form = reactive({ ...defaultForm })

const rules = {
  userCode: [{ required: true, message: '请输入用户编码', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }]
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageUser(query)
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
  query.isEnabled = ''
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
    const detail = await getUser(row.userId)
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
      await updateUser(form.userId, form)
      ElMessage.success('编辑成功')
    } else {
      await createUser(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  })
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确定删除用户 [${row.username}] 吗？`, '提示', {
    type: 'warning'
  })
  await deleteUser(row.userId)
  ElMessage.success('删除成功')
  loadData()
}

async function onResetPassword(row) {
  await ElMessageBox.confirm(`确定重置用户 [${row.username}] 的密码吗？`, '提示', {
    type: 'warning'
  })
  await resetPassword(row.userId)
  ElMessage.success('密码已重置')
}

// ===== 分配角色 =====
const roleDialogVisible = ref(false)
const currentUser = reactive({ userId: null, username: '', realName: '' })
const allRoles = ref([])
const selectedRoleIds = ref([])

async function onAssignRoles(row) {
  Object.assign(currentUser, { userId: row.userId, username: row.username, realName: row.realName })
  selectedRoleIds.value = Array.isArray(row.roleIds) ? [...row.roleIds] : []
  if (allRoles.value.length === 0) {
    try {
      allRoles.value = (await listAllRole()) || []
    } catch (e) {
      allRoles.value = []
    }
  }
  roleDialogVisible.value = true
}

async function onSubmitRoles() {
  await assignRoles(currentUser.userId, selectedRoleIds.value)
  ElMessage.success('分配角色成功')
  roleDialogVisible.value = false
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
  width: 220px;
}
.filter-select {
  width: 130px;
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
