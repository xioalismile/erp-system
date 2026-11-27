<template>
  <div class="erp-page">
    <div class="filter-bar">
      <el-input
        v-model="query.keyword"
        :placeholder="$tt('用户名/真实姓名')"
        class="filter-input"
        clearable
        @keyup.enter="onSearch"
      />
      <el-select v-model="query.isEnabled" :placeholder="$tt('状态')" clearable class="filter-select">
        <el-option :label="$tf('启用')" :value="1" />
        <el-option :label="$tf('禁用')" :value="0" />
      </el-select>
      <el-button v-permission="perm('query')" type="primary" @click="onSearch">{{ $tt("查询") }}</el-button>
      <el-button v-permission="perm('reset')" @click="onReset">{{ $tt("重置") }}</el-button>
      <el-button v-permission="perm('add')" type="success" @click="onAdd">{{ $tt("新增") }}</el-button>
    </div>

    <el-table :data="tableData" border v-loading="loading" @sort-change="onSortChange">
      <el-table-column prop="userCode" :label="$tf('用户编码')" min-width="120" sortable="custom"/>
      <el-table-column prop="username" :label="$tf('用户名')" min-width="120" sortable="custom"/>
      <el-table-column prop="realName" :label="$tf('真实姓名')" min-width="120" sortable="custom"/>
      <el-table-column prop="email" :label="$tf('邮箱')" min-width="180" sortable="custom"/>
      <el-table-column prop="mobilePhone" :label="$tf('手机号')" min-width="130" sortable="custom"/>
      <el-table-column :label="$tf('是否管理员')" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.isAdmin ? 'danger' : 'info'" size="small">
            {{ row.isAdmin ? '管理员' : '普通' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$tf('状态')" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.isEnabled ? 'success' : 'info'" size="small">
            {{ row.isEnabled ? '启用' : '禁用' }}
          </el-tag>
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
            <el-form-item :label="$tf('用户编码')" prop="userCode">
              <el-input v-model="form.userCode" :placeholder="$tt('如 U001')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('用户名')" prop="username">
              <el-input v-model="form.username" :placeholder="$tt('登录用户名')" :disabled="isEdit" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="$tf('真实姓名')" prop="realName">
              <el-input v-model="form.realName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('用户类型')" prop="userType">
              <el-select v-model="form.userType" :placeholder="$tt('请选择')" style="width:100%">
                <el-option :label="$tf('内部用户')" value="内部用户" />
                <el-option :label="$tf('外部用户')" value="外部用户" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="$tf('邮箱')" prop="email">
              <el-input v-model="form.email" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('手机号')" prop="mobilePhone">
              <el-input v-model="form.mobilePhone" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="$tf('是否启用')" prop="isEnabled">
              <el-switch v-model="form.isEnabled" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('是否管理员')" prop="isAdmin">
              <el-switch v-model="form.isAdmin" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="$tf('备注')" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <!-- 账号 ↔ 员工档案联动说明（2026-09-21）：保存时自动在员工档案生成并绑定对应员工 -->
        <div style="margin-left:110px;color:#909399;font-size:12px;line-height:18px;">
          {{ $tt('保存时会自动在【基础资料 → 员工档案】创建并绑定对应员工（员工编码自动生成），部门/岗位/电话可在员工档案里补充。') }}
        </div>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="dialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button v-permission="perm('save')" type="primary" @click="onSubmit">{{ $tt("保存") }}</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色弹窗 -->
    <el-dialog :title="$tf('分配角色')" v-model="roleDialogVisible" width="560px">
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
        <el-button v-permission="perm('dlgCancel')" @click="roleDialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button v-permission="perm('save')" type="primary" @click="onSubmitRoles">{{ $tt("保存") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { usePagePerm } from '@/utils/pagePerm'
const perm = usePagePerm('system:role-perm')
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  pageUser, createUser, updateUser, assignRoles
} from '@/api/system/user'
import AuditColumns from '@/components/AuditColumns.vue'
import { useTableSort } from '@/composables/useTableSort'
import { tt } from '@/i18n'
const { handleSortChange, getSortParams } = useTableSort()


const onSortChange = (e) => { handleSortChange(e); loadData() }
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({
  current: 1,
  size: 20,
  keyword: '',
  isEnabled: '',
  ...getSortParams()
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
  userCode: [{ required: true, message: tt('请输入用户编码'), trigger: 'blur' }],
  username: [{ required: true, message: tt('请输入用户名'), trigger: 'blur' }],
  realName: [{ required: true, message: tt('请输入真实姓名'), trigger: 'blur' }]
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageUser({ ...query, ...getSortParams() })
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
      ElMessage.success(tt('编辑成功'))
    } else {
      await createUser(form)
      ElMessage.success(tt('新增成功'))
    }
    dialogVisible.value = false
    loadData()
  })
}

// ===== 分配角色 =====
const roleDialogVisible = ref(false)
const currentUser = reactive({ userId: null, username: '', realName: '' })
const allRoles = ref([])
const selectedRoleIds = ref([])

async function onSubmitRoles() {
  await assignRoles(currentUser.userId, selectedRoleIds.value)
  ElMessage.success(tt('分配角色成功'))
  roleDialogVisible.value = false
  loadData()
}

onMounted(loadData)
useAutoRefresh(loadData)
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
