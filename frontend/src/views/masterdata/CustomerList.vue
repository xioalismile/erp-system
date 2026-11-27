<template>
  <div class="erp-page">
    <!-- 筛选区 -->
    <el-card shadow="never">
      <div class="filter-bar">
        <el-input v-model="search.keyword" :placeholder="$tt('客户编码/名称')" style="width:220px;" clearable @keyup.enter="handleSearch" />
        <el-select v-model="search.crmStatus" :placeholder="$tt('CRM状态')" style="width:140px;" clearable filterable>
          <el-option v-for="s in crmStatusOptions" :key="s" :label="s" :value="s" />
        </el-select>
        <el-select v-model="search.industry" :placeholder="$tt('所属行业')" style="width:140px;" clearable filterable>
          <el-option v-for="s in industryOptions" :key="s" :label="s" :value="s" />
        </el-select>
        <el-button type="primary" v-permission="perm('query')" @click="handleSearch">{{ $tt("查询") }}</el-button>
        <el-button v-permission="perm('reset')" @click="handleReset">{{ $tt("重置") }}</el-button>
        <div style="flex:1;"></div>
        <el-button type="primary" v-permission="perm('add')" @click="handleAdd">{{ $tt("+ 新增客户") }}</el-button>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" style="margin-top:12px;">
      <el-table :data="list" v-loading="loading" stripe border @sort-change="onSortChange">
        <el-table-column prop="customerCode" :label="$tf('客户编码')" width="130" sortable="custom">
          <template #default="{ row }">
            <el-link type="primary" @click="handleEdit(row)">{{ row.customerCode }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="customerName" :label="$tf('客户名称')" min-width="180" show-overflow-tooltip sortable="custom" />
        <el-table-column prop="industry" :label="$tf('所属行业')" width="110" sortable="custom">
          <template #default="{ row }">
            <el-tag v-if="row.industry" size="small" type="info">{{ row.industry }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="crmStatus" :label="$tf('CRM状态')" width="100" sortable="custom">
          <template #default="{ row }">
            <el-tag v-if="row.crmStatus" :type="crmStatusTagType(row.crmStatus)" size="small">{{ $dt(row.crmStatus) }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="contactPerson" :label="$tf('联系人')" width="100" sortable="custom" />
        <el-table-column prop="contactPhone" :label="$tf('联系电话')" width="140" sortable="custom" />
        <el-table-column prop="customerLevel" :label="$tf('等级')" width="90" sortable="custom" />
        <el-table-column prop="assignedSalesName" :label="$tf('销售员')" width="100" show-overflow-tooltip sortable="custom" />
        <el-table-column prop="address" :label="$tf('地址')" min-width="200" show-overflow-tooltip sortable="custom" />
        <AuditColumns />
      </el-table>

      <div style="display:flex;justify-content:flex-end;margin-top:12px;">
        <el-pagination
          v-model:current-page="page.current"
          v-model:page-size="page.size"
          :total="page.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="load"
          @current-change="load"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editForm.customerId ? '编辑客户' : '新增客户'" width="680px" @close="resetForm">
      <el-form :model="editForm" :rules="rules" ref="formRef" label-width="100px">
        <el-divider content-position="left">{{ $tt("基础信息") }}</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="$tf('客户编码')" prop="customerCode">
              <el-input v-model="editForm.customerCode" :placeholder="$tt('如 C20260001')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('客户名称')" prop="customerName">
              <el-input v-model="editForm.customerName" :placeholder="$tt('客户全称')" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="$tf('简称')">
              <el-input v-model="editForm.shortName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('所属行业')">
              <el-select v-model="editForm.industry" :placeholder="$tt('请选择行业')" filterable clearable style="width:100%">
                <el-option v-for="s in industryOptions" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="$tf('客户类型')">
              <el-select v-model="editForm.customerType" style="width:100%">
                <el-option :label="$tf('企业客户')" value="企业客户" />
                <el-option :label="$tf('个人客户')" value="个人客户" />
                <el-option :label="$tf('政府机构')" value="政府机构" />
                <el-option :label="$tf('其他')" value="其他" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('客户等级')">
              <el-select v-model="editForm.customerLevel" style="width:100%">
                <el-option label="VIP" value="VIP" />
                <el-option :label="$tf('重要客户')" value="重要客户" />
                <el-option :label="$tf('普通客户')" value="普通客户" />
                <el-option :label="$tf('潜在客户')" value="潜在客户" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="$tf('联系人')">
              <el-input v-model="editForm.contactPerson" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('联系电话')">
              <el-input v-model="editForm.contactPhone" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="$tf('邮箱')">
              <el-input v-model="editForm.email" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('税号')">
              <el-input v-model="editForm.taxNo" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="$tf('地址')">
          <el-input v-model="editForm.address" />
        </el-form-item>

        <el-divider content-position="left">{{ $tt("CRM信息") }}</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="$tf('CRM状态')">
              <el-select v-model="editForm.crmStatus" style="width:100%">
                <el-option v-for="s in crmStatusOptions" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('客户来源')">
              <el-select v-model="editForm.sourceType" style="width:100%" clearable>
                <el-option :label="$tf('线上推广')" value="线上推广" />
                <el-option :label="$tf('线下展会')" value="线下展会" />
                <el-option :label="$tf('客户转介绍')" value="客户转介绍" />
                <el-option :label="$tf('市场活动')" value="市场活动" />
                <el-option :label="$tf('其他')" value="其他" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="$tf('备注')">
          <el-input v-model="editForm.tag" type="textarea" :rows="2" :placeholder="$tt('客户标签/备注')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="dialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" :loading="saving" v-permission="perm('save')" @click="save">{{ $tt("保存") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listCrmCustomer, getCustomer, saveCustomer, updateCustomer, deleteCustomer } from '@/api/crm/customer'
import AuditColumns from '@/components/AuditColumns.vue'
import { useTableSort } from '@/composables/useTableSort'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'
const { handleSortChange, getSortParams } = useTableSort()
const perm = usePagePerm()


const onSortChange = (e) => { handleSortChange(e); load() }
const crmStatusOptions = ['潜在客户', '意向客户', '正式客户', '休眠客户', '已流失']
const industryOptions = ['制造业', '零售业', '服务业', 'IT', '金融', '医疗', '教育', '建筑', '物流', '农业', '能源', '其他']

const list = ref([])
const loading = ref(false)
const search = reactive({ keyword: '', crmStatus: '', industry: '' })
const page = reactive({ current: 1, size: 20, total: 0 })

const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref()
const editForm = ref({
  customerCode: '', customerName: '', shortName: '', industry: '',
  customerType: '企业客户', customerLevel: '普通客户',
  contactPerson: '', contactPhone: '', email: '', taxNo: '', address: '',
  crmStatus: '潜在客户', sourceType: '', tag: ''
})
const rules = {
  customerCode: [{ required: true, message: tt('请输入客户编码'), trigger: 'blur' }],
  customerName: [{ required: true, message: tt('请输入客户名称'), trigger: 'blur' }]
}

function crmStatusTagType(s) {
  const m = { '正式客户': 'success', '意向客户': 'warning', '潜在客户': 'info', '休眠客户': 'danger', '已流失': 'danger' }
  return m[s] || 'info'
}

async function load() {
  loading.value = true
  try {
    const res = await listCrmCustomer({
      current: page.current,
      size: page.size,
      keyword: search.keyword || undefined,
      crmStatus: search.crmStatus || undefined,
      ...getSortParams()
    })
    // 拦截器已解包：res 即 {records, total}
    const data = res || {}
    list.value = data.records || []
    page.total = data.total || 0
    // 前端二次筛选行业（后端 /crm 接口暂不支持 industry 参数过滤）
    if (search.industry) {
      list.value = list.value.filter(c => c.industry === search.industry)
      page.total = list.value.length
    }
  } catch (e) {
    ElMessage.error(tt('加载客户列表失败'))
  } finally {
    loading.value = false
  }
}

function handleSearch() { page.current = 1; load() }
function handleReset() {
  search.keyword = ''; search.crmStatus = ''; search.industry = ''
  page.current = 1; load()
}

function handleAdd() {
  resetForm()
  editForm.value = {
    customerCode: '', customerName: '', shortName: '', industry: '',
    customerType: '企业客户', customerLevel: '普通客户',
    contactPerson: '', contactPhone: '', email: '', taxNo: '', address: '',
    crmStatus: '潜在客户', sourceType: '', tag: ''
  }
  dialogVisible.value = true
}

async function handleEdit(row) {
  resetForm()
  try {
    const detail = await getCustomer(row.customerId)
    editForm.value = detail || row
    dialogVisible.value = true
  } catch (e) {
    ElMessage.error(tt('加载客户详情失败'))
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`${tt("确认删除客户「")}${row.customerName}」？`, tt('提示'), { type: 'warning' })
    await deleteCustomer(row.customerId)
    ElMessage.success(tt('删除成功'))
    load()
  } catch (e) { /* 取消 */ }
}

function resetForm() {
  if (formRef.value) formRef.value.clearValidate()
}

async function save() {
  try {
    await formRef.value.validate()
  } catch { return }
  saving.value = true
  try {
    if (editForm.value.customerId) {
      await updateCustomer(editForm.value.customerId, editForm.value)
    } else {
      await saveCustomer(editForm.value)
    }
    ElMessage.success(tt('保存成功'))
    dialogVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message || tt('保存失败'))
  } finally {
    saving.value = false
  }
}

onMounted(load)
useAutoRefresh(load)
</script>

<style scoped>
.erp-page { padding: 12px; }
.filter-bar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
</style>
