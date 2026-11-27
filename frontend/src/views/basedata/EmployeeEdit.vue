<template>
  <div class="employee-edit-wrap">
    <BaseEdit
      ref="baseEditRef"
      :title="$tt('员工档案')"
      :sections="sections"
      :fetch-data="fetchData"
      :on-save="onSave"
      id-field="employeeId"
      list-route="/basedata/employee"
      :option-loaders="optionLoaders"
      custom-field-entity="employee"
      :show-change-record="true"
      change-record-bill-type="员工"
      change-record-bill-field="employeeCode"
      auto-doc-type="BD_EMPLOYEE"
      auto-doc-code-field="employeeCode"
      :on-create-init="onCreateInit"
      page-key="employee"
    >
      <template #header-extras>
        <el-button
          v-if="employeeForm.userId"
          v-permission="perm('resetPassword')"
          size="small"
          type="warning"
          @click="openResetPwdDialog"
        >
          <el-icon><Key /></el-icon>重置密码
        </el-button>
        <el-button
          v-if="employeeForm.userId"
          v-permission="perm('edit')"
          size="small"
          @click="openChangePwdDialog"
        >
          <el-icon><Lock /></el-icon>修改密码
        </el-button>
      </template>
    </BaseEdit>

    <el-dialog v-model="resetPwdVisible" :title="$tf('重置用户密码')" width="420px" :close-on-click-modal="false">
      <el-alert
        :title="$tt('重置后密码将被设置为默认密码：123456789，员工登录后可自行修改。')"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 18px;"
      />
      <div style="padding: 0 6px 10px; color: #4b5563; font-size: 13px;">
        目标用户：<b>{{ usernameLabel }}</b>
        <span v-if="employeeForm.employeeName" style="margin-left: 8px; color: #9ca3af;">
          （员工：{{ employeeForm.employeeName }}）
        </span>
      </div>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="resetPwdVisible = false">{{ $tt("取消") }}</el-button>
        <el-button v-permission="perm('dlgConfirm')" type="warning" :loading="resetting" @click="doResetPwd">
          确认重置为 123456789
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="changePwdVisible" :title="$tf('修改用户密码')" width="420px" :close-on-click-modal="false">
      <el-form :model="pwdForm" :rules="pwdRules" ref="pwdFormRef" label-width="90px">
        <el-form-item :label="$tf('用户名')">
          <el-input :model-value="employeeForm.username" disabled />
        </el-form-item>
        <el-form-item :label="$tf('当前密码')" prop="oldPassword" required>
          <el-input v-model="pwdForm.oldPassword" type="password" show-password autocomplete="off" :placeholder="$tt('请输入该用户的当前密码')" maxlength="20" />
        </el-form-item>
        <el-form-item :label="$tf('新密码')" prop="newPassword" required>
          <el-input v-model="pwdForm.newPassword" type="password" show-password autocomplete="new-password" :placeholder="$tt('请输入新密码（6-20位）')" maxlength="20" />
        </el-form-item>
        <el-form-item :label="$tf('确认密码')" prop="confirmPassword" required>
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password autocomplete="new-password" :placeholder="$tt('请再次输入新密码')" maxlength="20" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="changePwdVisible = false">{{ $tt("取消") }}</el-button>
        <el-button v-permission="perm('save')" type="primary" :loading="changing" @click="doChangePwd">{{ $tt("保存新密码") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Key, Lock } from '@element-plus/icons-vue'
import BaseEdit from '@/components/BaseEdit.vue'
import {
  getEmployee, saveEmployee, updateEmployee,
  resetEmployeePassword, changeEmployeePassword, checkEmployeeUsername
} from '@/api/basedata/employee'
import { listAllDepartment } from '@/api/basedata/department'
import { listAllRole } from '@/api/system/role'
import { usePagePerm } from '@/utils/pagePerm'
import { tt } from '@/i18n'
// 按钮权限：员工编辑属 /basedata/employee 子页面，显式指定对象码
const perm = usePagePerm('basedata:employee')

const DEFAULT_PASSWORD = '123456789'

const baseEditRef = ref(null)
const route = useRoute()

const employeeForm = reactive({})
const usernameLabel = computed(() => employeeForm.username || '（未绑定账号）')
const usernameChecking = ref(false)
const usernameChecked = ref(false) // 是否已过查重

function syncFrom(f) {
  if (!f || typeof f !== 'object') return
  // 先清空所有旧字段，避免编辑后新增时残留上次数据
  Object.keys(employeeForm).forEach(k => { delete employeeForm[k] })
  Object.keys(f).forEach(k => { employeeForm[k] = f[k] })
}

const optionLoaders = {
  deptId: async () => {
    const list = await listAllDepartment()
    const arr = Array.isArray(list) ? list : (list?.records || [])
    return arr.map(d => ({ value: d.deptId, label: d.deptName + ' (' + d.deptCode + ')' }))
  },
  roleId: async () => {
    const list = await listAllRole()
    const arr = Array.isArray(list) ? list : (list?.records || [])
    return arr.map(r => ({ value: r.roleId, label: r.roleName + ' (' + r.roleCode + ')' }))
  }
}

const sections = [
  {
    title: '基本信息',
    subtitle: '员工的标识和归属',
    fields: [
      { prop: 'employeeCode', label: '员工编码', placeholder: '如 E001' },
      { prop: 'employeeName', label: '员工姓名', required: true, placeholder: '如 张三' },
      { prop: 'deptId', label: '部门', type: 'select', required: true, filterable: true, placeholder: '请选择部门' },
      { prop: 'position', label: '职位', placeholder: '如 经理' },
      // 第5期：工时费率 —— 「财务 → 工序工资核算」的计时工资 = 报工工时 × 此费率（留空 = 不计计时工资）
      { prop: 'hourlyRate', label: '工时费率(元/小时)', type: 'number', placeholder: '用于工序计时工资核算，留空=不计' }
    ]
  },
  {
    title: '联系方式',
    subtitle: '员工的联系信息',
    fields: [
      { prop: 'gender', label: '性别', type: 'select', default: '男', options: [
        { value: '男', label: '男' },
        { value: '女', label: '女' },
        { value: '未知', label: '未知' }
      ] },
      { prop: 'mobilePhone', label: '手机号码', placeholder: '手机号码' },
      { prop: 'email', label: '邮箱', placeholder: '邮箱' },
      { prop: 'entryDate', label: '入职日期', type: 'date', placeholder: '入职日期' }
    ]
  },
  {
    title: '系统账号',
    subtitle: '员工的系统登录账号（填写用户名后自动创建/更新系统用户）',
    fields: [
      {
        prop: 'username',
        label: '用户名',
        placeholder: '填写系统登录用户名（不填则不创建账号，自动查重）',
        maxlength: 50,
        clearable: true
      },
      {
        prop: 'password',
        label: '密码',
        type: 'password',
        placeholder: '留空则使用默认密码 123456789'
      },
      { prop: 'roleId', label: '角色', type: 'select', filterable: true, placeholder: '请选择系统角色' }
    ]
  },
  {
    title: '状态',
    subtitle: '在职状态',
    fields: [
      { prop: 'status', label: '状态', type: 'select', required: true, default: '在职', options: [
        { value: '在职', label: '在职' }, { value: '离职', label: '离职' }
      ] },
      { prop: 'remark', label: '备注', type: 'textarea', rows: 2, placeholder: '请输入备注' }
    ]
  }
]

async function onCreateInit(f, isEdit) {
  if (!isEdit) {
    if (!f.username) f.username = ''
    const u = String(f.username || '').trim().toLowerCase()
    if (u === 'admin') f.username = ''
    f.password = ''
  }
  syncFrom(f)
  usernameChecked.value = false
}

async function fetchData(id) {
  const data = await getEmployee(id)
  if (data) {
    syncFrom(data)
    employeeForm.password = ''
  }
  usernameChecked.value = false
  return data
}

// 用户名查重：空用户名视为通过
let _checkTimer = null
async function checkUsername(val) {
  const v = String(val || '').trim()
  if (!v) {
    usernameChecked.value = true
    return true
  }
  if (v.toLowerCase() === 'admin') {
    throw new Error('不允许使用「admin」作为员工用户名，它是系统超级管理员保留账号')
  }
  usernameChecking.value = true
  try {
    const ok = await checkEmployeeUsername(v, employeeForm.employeeId || null)
    if (ok === false || ok?.available === false) {
      throw new Error(`用户名「${v}」已被占用，请更换`)
    }
    usernameChecked.value = true
    return true
  } finally {
    usernameChecking.value = false
  }
}

// 监听用户名变化：防抖自动查重
watch(
  () => employeeForm.username,
  (v, old) => {
    usernameChecked.value = false
    if (_checkTimer) clearTimeout(_checkTimer)
    if (v === undefined || v === null) return
    const val = String(v).trim()
    if (!val) { usernameChecked.value = true; return }
    if (old !== undefined && String(old).trim() === val) return
    _checkTimer = setTimeout(async () => {
      try {
        await checkUsername(val)
      } catch (e) {
        ElMessage.warning(e.message || tt('用户名不可用'))
      }
    }, 450)
  }
)

async function onSave(data, isEdit) {
  if (data && typeof data === 'object') {
    const u = String(data.username || '').trim().toLowerCase()
    if (u === 'admin') {
      throw new Error('不允许使用「admin」作为员工用户名，它是系统超级管理员保留账号。')
    }
    if (!isEdit && !data.password) data.password = ''
    // 保存前强制查重（防抖还没来得及触发也没关系）
    if (String(data.username || '').trim()) {
      await checkUsername(data.username)
    }
    syncFrom(data)
  }
  if (isEdit) {
    await updateEmployee(data)
  } else {
    // 后端回传新员工 employeeId，同步到表单后二次保存查重才能排除自身
    const newId = await saveEmployee(data)
    if (newId) {
      data.employeeId = newId
      employeeForm.employeeId = newId
    }
  }
  if (data) syncFrom(data)
}

const resetPwdVisible = ref(false)
const changePwdVisible = ref(false)
const resetting = ref(false)
const changing = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const pwdFormRef = ref(null)
const pwdRules = {
  oldPassword: [
    { required: true, message: tt('请输入当前密码'), trigger: 'blur' },
    { min: 6, max: 20, message: tt('当前密码长度 6-20 位'), trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: tt('请输入新密码'), trigger: 'blur' },
    { min: 6, max: 20, message: tt('密码长度 6-20 位'), trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: tt('请再次输入新密码'), trigger: 'blur' },
    {
      validator: (r, v, cb) => {
        if (v !== pwdForm.newPassword) return cb(new Error('两次输入的密码不一致'))
        cb()
      },
      trigger: 'blur'
    }
  ]
}

function openResetPwdDialog() {
  if (!employeeForm.userId) {
    ElMessage.warning(tt('该员工尚未绑定系统账号，请先填写「用户名」并保存后再操作。'))
    return
  }
  resetPwdVisible.value = true
}
async function doResetPwd() {
  try {
    resetting.value = true
    await resetEmployeePassword(employeeForm.employeeId)
    ElMessage.success(tt('密码已重置为 123456789，请尽快通知员工登录并修改'))
    resetPwdVisible.value = false
  } finally {
    resetting.value = false
  }
}

function openChangePwdDialog() {
  if (!employeeForm.userId) {
    ElMessage.warning(tt('该员工尚未绑定系统账号，请先填写「用户名」并保存后再操作。'))
    return
  }
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  changePwdVisible.value = true
}
async function doChangePwd() {
  if (!pwdFormRef.value) return
  try {
    await pwdFormRef.value.validate()
  } catch (e) {
    return
  }
  try {
    changing.value = true
    await changeEmployeePassword(
      employeeForm.employeeId,
      pwdForm.oldPassword,
      pwdForm.newPassword
    )
    ElMessage.success(tt('密码修改成功'))
    changePwdVisible.value = false
  } finally {
    changing.value = false
  }
}
</script>

<style scoped lang="scss">
.employee-edit-wrap { min-height: 100%; }
</style>
