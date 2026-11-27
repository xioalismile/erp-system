<template>
  <div class="erp-page">
    <!-- 筛选区 -->
    <el-card shadow="never">
      <div class="filter-bar">
        <el-input v-model="search.keyword" :placeholder="$tt('姓名/手机/邮箱/客户名称')" style="width:260px;" clearable />
        <el-select v-model="search.isPrimary" :placeholder="$tt('主联系人')" style="width:120px;" clearable>
          <el-option :label="$tf('主联系人')" :value="1" />
          <el-option :label="$tf('普通联系人')" :value="0" />
        </el-select>
        <el-select v-model="search.customerId" :placeholder="$tt('选择客户')" filterable clearable style="width:220px;">
          <el-option v-for="c in withFallbackOption(customerOptions, 'customerId', 'customerName', search)" :key="c.customerId"
                     :label="c.customerName" :value="c.customerId" />
        </el-select>
        <el-button type="primary" v-permission="perm('query')" @click="handleSearch">{{ $tt("查询") }}</el-button>
        <el-button v-permission="perm('reset')" @click="handleReset">{{ $tt("重置") }}</el-button>
        <el-tooltip :content="$tt('列设置')" placement="top">
          <el-button :icon="Setting" circle v-permission="perm('columnSetting')" @click="openColumnSetting" />
        </el-tooltip>
        <div style="flex:1;"></div>
        <el-button type="primary" v-permission="perm('add')" @click="handleAdd">{{ $tt("+ 新增联系人") }}</el-button>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" style="margin-top:12px;">
      <el-table :data="contacts" v-loading="loading" border stripe :key="configVersion" @sort-change="onSortChange">
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
              <span v-if="col.type === 'format'">{{ col.format(row) }}</span>
              <template v-else-if="col.type === 'custom'">
                <template v-if="col.prop === 'contactName'">
                  <el-link type="primary" :underline="false" @click="handleEdit(row)">{{ row.contactName }}</el-link>
                  <el-tag v-if="row.isPrimary === 1" type="success" size="small" style="margin-left:6px;">{{ $tt("主") }}</el-tag>
                </template>
              </template>
            </template>
          </el-table-column>
        </template>
        <AuditColumns />
        <!-- 操作列：编辑/设为主联系人/删除 -->
        <el-table-column :label="$tf('操作')" width="170" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" v-permission="perm('edit')" @click="handleEdit(row)">{{ $tt("编辑") }}</el-button>
            <el-button v-if="row.isPrimary !== 1" type="warning" link size="small" v-permission="perm('setPrimary')" @click="handleSetPrimary(row)">{{ $tt("设为主联系人") }}</el-button>
            <el-button type="danger" link size="small" v-permission="perm('delete')" @click="handleDelete(row)">{{ $tt("删除") }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:16px;display:flex;justify-content:flex-end;">
        <el-pagination
          v-model:current-page="page.current"
          v-model:page-size="page.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="page.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑联系人' : '新增联系人'" width="780px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-divider content-position="left">{{ $tt("基本信息") }}</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="$tf('联系人编号')">
              <el-input v-model="formData.contactNo" :disabled="docCodeDisabled && !isEdit" :placeholder="docCodePlaceholder" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('所属客户')" prop="customerId">
              <el-select v-model="formData.customerId" filterable :placeholder="$tt('选择客户')" style="width:100%" @change="onCustomerChange">
                <el-option v-for="c in withFallbackOption(customerOptions, 'customerId', 'customerName', formData)" :key="c.customerId"
                           :label="c.customerName" :value="c.customerId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('姓名')" prop="contactName">
              <el-input v-model="formData.contactName" :placeholder="$tt('联系人姓名')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('性别')">
              <el-radio-group v-model="formData.gender">
                <el-radio :label="$tf('男')" />
                <el-radio :label="$tf('女')" />
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('是否主联系人')">
              <el-switch v-model="formData.isPrimary" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">{{ $tt("职务信息") }}</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="$tf('部门')">
              <el-input v-model="formData.department" :placeholder="$tt('如：采购部')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('职务')">
              <el-input v-model="formData.position" :placeholder="$tt('如：采购经理')" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">{{ $tt("联系方式") }}</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="$tf('手机')">
              <el-input v-model="formData.mobile" :placeholder="$tt('手机号')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('座机')">
              <el-input v-model="formData.phone" :placeholder="$tt('如：010-12345678')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('邮箱')">
              <el-input v-model="formData.email" :placeholder="$tt('邮箱地址')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('微信')">
              <el-input v-model="formData.wechat" :placeholder="$tt('微信号')" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="$tf('联系地址')">
              <el-input v-model="formData.address" :placeholder="$tt('联系地址')" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">{{ $tt("其他") }}</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="$tf('生日')">
              <el-date-picker v-model="formData.birthday" type="date" value-format="YYYY-MM-DD"
                              :placeholder="$tt('选择生日')" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('兴趣爱好')">
              <el-input v-model="formData.hobby" :placeholder="$tt('兴趣爱好')" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="$tf('备注')">
              <el-input v-model="formData.remark" type="textarea" :rows="2" :placeholder="$tt('备注')" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="dialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" :loading="saving" v-permission="perm('save')" @click="handleSave">{{ $tt("保存") }}</el-button>
      </template>
    </el-dialog>

    <!-- 列设置抽屉 -->
    <ColumnSettingDrawer v-model="columnSettingVisible" :page-key="'crm-contact'" :columns="allColumns" />
  </div>
</template>

<script setup>
import { withFallbackOption } from '@/utils/labelFallback'
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listContact, saveContact, updateContact, deleteContact, setPrimaryContact
} from '@/api/crm/contact'
import { listCrmCustomer } from '@/api/crm/customer'
import { Setting } from '@element-plus/icons-vue'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
import AuditColumns from '@/components/AuditColumns.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { useAutoDocCode } from '@/composables/useAutoDocCode'
import { useTableSort } from '@/composables/useTableSort'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('crm:contact')
const { handleSortChange, getSortParams } = useTableSort()


const onSortChange = (e) => { handleSortChange(e); loadData() }
// 列定义（用于列设置抽屉+动态渲染）：type 标记单元格渲染分支（custom 姓名链接+主联系人标记 / format 格式化文本）
const allColumns = [
  { prop: 'contactNo', label: '编号', width: 150, sortable: true },
  { prop: 'contactName', label: '姓名', width: 100, sortable: true, type: 'custom' },
  { prop: 'gender', label: '性别', width: 70, type: 'format', format: (row) => row.gender || '-' },
  { prop: 'position', label: '职务', width: 120, sortable: true, tooltip: true },
  { prop: 'department', label: '部门', width: 120, sortable: true, tooltip: true },
  { prop: 'mobile', label: '手机', width: 130, sortable: true },
  { prop: 'phone', label: '座机', width: 130, sortable: true },
  { prop: 'email', label: '邮箱', minWidth: 180, sortable: true, tooltip: true },
  { prop: 'customerName', label: '所属客户', minWidth: 180, sortable: true, tooltip: true }
]
const { columnSettingVisible, openColumnSetting, isColVisible, getColFixed, configVersion, sortedColumns } = useColumnSetting('crm-contact', allColumns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))

const loading = ref(false)
const saving = ref(false)
const contacts = ref([])
const customerOptions = ref([])

const search = reactive({
  keyword: '',
  isPrimary: null,
  customerId: null
})

const page = reactive({ current: 1, size: 20, total: 0, ...getSortParams() })

const dialogVisible = ref(false)
const isEdit = ref(false)
const { docCodeDisabled, docCodePlaceholder, autoFillDocCodeOnCreate } = useAutoDocCode('CONTACT')
const formRef = ref()

const defaultForm = () => ({
  contactId: null,
  contactNo: '',
  customerId: null,
  customerName: '',
  contactName: '',
  gender: '男',
  position: '',
  department: '',
  mobile: '',
  phone: '',
  email: '',
  wechat: '',
  address: '',
  isPrimary: 0,
  birthday: null,
  hobby: '',
  remark: ''
})
const formData = reactive(defaultForm())

const formRules = {
  customerId: [{ required: true, message: tt('请选择所属客户'), trigger: 'change' }],
  contactName: [{ required: true, message: tt('请输入姓名'), trigger: 'blur' }]
}

async function loadData() {
  loading.value = true
  try {
    const res = await listContact({
      current: page.current,
      size: page.size,
      keyword: search.keyword || undefined,
      isPrimary: search.isPrimary,
      customerId: search.customerId || undefined,
      ...getSortParams()
    })
    // 拦截器已解包：res 即后端 Result.data（{records, total}）
    const data = res || {}
    contacts.value = data.records || []
    page.total = data.total || 0
  } catch (e) {
    ElMessage.error(tt('加载联系人列表失败'))
  } finally {
    loading.value = false
  }
}

async function loadCustomers() {
  try {
    const res = await listCrmCustomer({ current: 1, size: 1000, ...getSortParams() })
    // 拦截器已解包：res 即 {records, total}
    customerOptions.value = (res && (Array.isArray(res) ? res : (res.records || []))) || []
  } catch (e) { /* 忽略 */ }
}

function handleSearch() { page.current = 1; loadData() }
function handleReset() {
  search.keyword = ''
  search.isPrimary = null
  search.customerId = null
  page.current = 1
  loadData()
}

function handleAdd() {
  Object.assign(formData, defaultForm())
  isEdit.value = false
    autoFillDocCodeOnCreate(formData, 'contactNo')
  dialogVisible.value = true
}

function handleEdit(row) {
  Object.assign(formData, defaultForm(), row)
  isEdit.value = true
  dialogVisible.value = true
}

function onCustomerChange(customerId) {
  const c = customerOptions.value.find(c => c.customerId === customerId)
  if (c) formData.customerName = c.customerName
}

async function handleSave() {
  await formRef.value?.validate(async valid => {
    if (!valid) return
    saving.value = true
    try {
      if (isEdit.value) {
        await updateContact(formData.contactId, formData)
      } else {
        await saveContact(formData)
      }
      ElMessage.success(isEdit.value ? tt('修改成功') : tt('新增成功'))
      dialogVisible.value = false
      loadData()
    } catch (e) {
      // 拦截器已弹错误提示
    } finally {
      saving.value = false
    }
  })
}

// 设为主联系人
async function handleSetPrimary(row) {
  try {
    await ElMessageBox.confirm(
      `${tt("确认将【")}${row.contactName}${tt("】设为客户【")}${row.customerName || ''}${tt("】的主联系人？设为主联系人后原主联系人将被取消。")}`,
      tt('设为主联系人'),
      { type: 'warning', confirmButtonText: tt('确认'), cancelButtonText: tt('取消') }
    )
  } catch (e) {
    return
  }
  try {
    await setPrimaryContact(row.contactId)
    ElMessage.success(tt('设置成功'))
    loadData()
  } catch (e) {
    // 拦截器已弹错误提示
  }
}

// 删除联系人（被业务引用的联系人后端会拦截）
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `${tt("确认删除联系人【")}${row.contactName}${tt("】？删除后不可恢复。")}`,
      tt('删除确认'),
      { type: 'warning', confirmButtonText: tt('确认删除'), cancelButtonText: tt('取消') }
    )
  } catch (e) {
    return
  }
  try {
    await deleteContact(row.contactId)
    ElMessage.success(tt('删除成功'))
    loadData()
  } catch (e) {
    // 拦截器已弹错误提示
  }
}

onMounted(() => {
  loadCustomers()
  loadData()
})
useAutoRefresh(() => {
  loadCustomers()
  loadData()
})
</script>

<style scoped>
.filter-bar { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
</style>
