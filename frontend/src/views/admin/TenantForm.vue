<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑客户' : '新增客户'"
    width="640px"
    :close-on-click-modal="false"
    destroy-on-close
    @closed="resetForm"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item :label="$tf('租户编码')" prop="tenantCode">
            <el-input
              v-model="form.tenantCode"
              :disabled="isEdit"
              :placeholder="$tt('如 BAIYUN（创建后不可改）')"
              maxlength="30"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item :label="$tf('客户名称')" prop="tenantName">
            <el-input v-model="form.tenantName" :placeholder="$tt('公司/客户名称')" maxlength="200" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item :label="$tf('版本')" prop="version">
            <el-select v-model="form.version" style="width: 100%">
              <el-option :label="$tf('标准版')" value="STANDARD" />
              <el-option :label="$tf('CRM获客版')" value="CRM" />
              <el-option :label="$tf('旗舰版')" value="ULTIMATE" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item v-if="!isEdit" :label="$tf('试用期')" prop="trialMode">
            <el-select v-model="form.trialMode" style="width: 100%" @change="onTrialModeChange">
              <el-option :label="$tf('30天试用')" :value="30" />
              <el-option :label="$tf('15天试用')" :value="15" />
              <el-option :label="$tf('7天试用')" :value="7" />
              <el-option :label="$tf('正式版（指定到期日）')" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item v-else :label="$tf('状态')">
            <el-tag :type="rowStatusType">{{ rowStatusText }}</el-tag>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item :label="$tf('到期日期')" prop="expireDate">
            <el-date-picker
              v-model="form.expireDate"
              type="date"
              value-format="YYYY-MM-DD"
              :disabled="!isEdit && form.trialMode > 0"
              :placeholder="$tt('试用期自动计算')"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item :label="$tf('最大用户数')" prop="maxUsers">
            <el-input-number v-model="form.maxUsers" :min="1" :max="9999" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item v-if="!isEdit" :label="$tf('所属行业')" prop="industry">
            <el-select v-model="form.industry" style="width: 100%" clearable :placeholder="$tt('不选：客户首次登录时由小楷引导选择')">
              <el-option v-for="ind in industries" :key="ind.code" :label="ind.name" :value="ind.code" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider v-if="!isEdit" content-position="left">{{ $tt("初始管理员账号") }}</el-divider>
      <el-row v-if="!isEdit" :gutter="16">
        <el-col :span="12">
          <el-form-item :label="$tf('管理员账号')" prop="adminUsername">
            <el-input v-model="form.adminUsername" :placeholder="adminUsernamePlaceholder" maxlength="30" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item :label="$tf('初始密码')" prop="adminPassword">
            <el-input v-model="form.adminPassword" autocomplete="new-password" :placeholder="$tt('默认 admin123')" show-password maxlength="50" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">{{ $tt("联系信息") }}</el-divider>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item :label="$tf('联系人')" prop="contactPerson">
            <el-input v-model="form.contactPerson" maxlength="100" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item :label="$tf('联系电话')" prop="contactPhone">
            <el-input v-model="form.contactPhone" maxlength="50" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item :label="$tf('邮箱')" prop="contactEmail">
            <el-input v-model="form.contactEmail" maxlength="200" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item :label="$tf('地址')" prop="address">
            <el-input v-model="form.address" maxlength="500" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item v-if="isEdit" :label="$tf('备注')" prop="remark">
        <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button v-permission="perm('dlgCancel')" @click="visible = false">{{ $tt("取消") }}</el-button>
      <el-button type="primary" v-permission="perm('save')" :loading="submitting" @click="handleSubmit">
        {{ isEdit ? '保存' : '创建' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createTenant, updateTenant } from '@/api/admin/tenant'
import { listIndustries } from '@/api/system/onboarding'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('platform:tenant-manage')

const emit = defineEmits(['success'])
const visible = ref(false)
const submitting = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const editingId = ref(null)

/** 行业清单（2026-10-11 开账引导）：选了则建完自动按行业预设，客户首登不再弹小楷向导 */
const industries = ref([])

const form = reactive({
  tenantCode: '',
  tenantName: '',
  version: 'STANDARD',
  trialMode: 30,
  expireDate: '',
  maxUsers: 50,
  adminUsername: '',
  adminPassword: '',
  contactPerson: '',
  contactPhone: '',
  contactEmail: '',
  address: '',
  remark: '',
  industry: ''
})

const rules = {
  tenantCode: [
    { required: true, message: tt('请输入租户编码'), trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: tt('只能是字母/数字/下划线'), trigger: 'blur' }
  ],
  tenantName: [{ required: true, message: tt('请输入客户名称'), trigger: 'blur' }],
  adminUsername: [{ pattern: /^[a-zA-Z0-9_]{3,30}$/, message: tt('3-30位字母/数字/下划线'), trigger: 'blur' }]
}

const adminUsernamePlaceholder = computed(() => {
  if (form.tenantCode) {
    return `默认 ${form.tenantCode.toLowerCase()}_admin`
  }
  return '请输入管理员账号'
})

watch(() => form.tenantCode, (newCode) => {
  if (newCode && !form.adminUsername) {
    form.adminUsername = newCode.toLowerCase() + '_admin'
  }
})

const rowStatusText = ref('')
const rowStatusType = ref('success')

function onTrialModeChange(val) {
  if (val > 0) form.expireDate = ''
}

function open(row) {
  visible.value = true
  if (!row && industries.value.length === 0) {
    listIndustries().then(list => { industries.value = Array.isArray(list) ? list : [] }).catch(() => {})
  }
  if (row) {
    isEdit.value = true
    editingId.value = row.tenant_id
    nextTick(() => {
      form.tenantCode = row.tenant_code
      form.tenantName = row.tenant_name
      form.version = row.version || 'STANDARD'
      form.expireDate = row.expire_date ? String(row.expire_date).slice(0, 10) : ''
      form.maxUsers = row.max_users || 50
      form.contactPerson = row.contact_person || ''
      form.contactPhone = row.contact_phone || ''
      form.contactEmail = row.contact_email || ''
      form.address = row.address || ''
      form.remark = row.remark || ''
      rowStatusText.value = statusText(row.status)
      rowStatusType.value = statusType(row.status)
    })
  } else {
    isEdit.value = false
    editingId.value = null
  }
}

function statusText(s) {
  if (s === 'disabled') return '已禁用'
  if (s === 'expired') return '已过期'
  return '正常'
}
function statusType(s) {
  if (s === 'disabled') return 'info'
  if (s === 'expired') return 'danger'
  return 'success'
}

function resetForm() {
  formRef.value?.resetFields()
  Object.assign(form, {
    tenantCode: '', tenantName: '', version: 'STANDARD', trialMode: 30,
    expireDate: '', maxUsers: 50, adminUsername: '', adminPassword: '',
    contactPerson: '', contactPhone: '', contactEmail: '', address: '', remark: '',
    industry: ''
  })
}

async function handleSubmit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateTenant(editingId.value, {
        tenantName: form.tenantName,
        contactPerson: form.contactPerson,
        contactPhone: form.contactPhone,
        contactEmail: form.contactEmail,
        address: form.address,
        maxUsers: form.maxUsers,
        version: form.version,
        expireDate: form.expireDate,
        remark: form.remark
      })
      ElMessage.success(tt('保存成功'))
    } else {
      const res = await createTenant({
        tenantCode: form.tenantCode,
        tenantName: form.tenantName,
        version: form.version,
        trialDays: form.trialMode > 0 ? form.trialMode : 0,
        expireDate: form.trialMode > 0 ? null : (form.expireDate || null),
        maxUsers: form.maxUsers,
        adminUsername: form.adminUsername || (form.tenantCode.toLowerCase() + '_admin'),
        adminPassword: form.adminPassword || '',
        contactPerson: form.contactPerson,
        contactPhone: form.contactPhone,
        contactEmail: form.contactEmail,
        address: form.address,
        industry: form.industry || undefined
      })
      ElMessage.success(`${tt("创建成功！数据库：")}${res.dbName}${tt("，管理员：")}${res.adminUsername}${tt("，到期：")}${res.expireDate}`)
    }
    visible.value = false
    emit('success')
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>
