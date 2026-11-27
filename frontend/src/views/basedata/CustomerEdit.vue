<template>
  <BaseEdit
    :title="$tt('客户档案')"
    :sections="sections"
    :fetch-data="fetchData"
    :on-save="onSave"
    :option-loaders="optionLoaders"
    :disabled-fields="disabledFields"
    :on-create-init="onCreateInit"
    id-field="customerId"
    list-route="/basedata/customer"
    custom-field-entity="customer"
    :show-change-record="true"
    change-record-bill-type="客户"
    change-record-bill-field="customerCode"
    auto-doc-type="MD_CUSTOMER"
    auto-doc-code-field="customerCode"
    page-key="customer"
  >
    <!-- 信用额度/授信只读信息条（编辑态展示；新增态隐藏） -->
    <template #header-extras>
      <div v-if="creditInfo" class="credit-bar">
        <el-tag :type="creditStatusType(creditInfo.creditApprovalStatus)" size="small" effect="light">
          授信：{{ creditInfo.creditApprovalStatus || '未申请' }}
        </el-tag>
        <span class="credit-item">{{ $tt("额度") }} <b>{{ fmtMoney(creditInfo.creditLimit) }}</b></span>
        <span class="credit-item">{{ $tt("已用") }} <b>{{ fmtMoney(creditInfo.usedCredit) }}</b></span>
        <span class="credit-item" :class="{ 'credit-over': creditOver }">
          剩余 <b>{{ fmtMoney(creditInfo.availableCredit) }}</b>
          <span v-if="creditOver" class="credit-warn">{{ $tt("已超额，收款前禁止提交销售合同/订单") }}</span>
        </span>
      </div>
    </template>
  </BaseEdit>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import BaseEdit from '@/components/BaseEdit.vue'
import PaymentTermSelect from '@/components/PaymentTermSelect.vue'
import { getCustomer, saveCustomer, updateCustomer, getCustomerCredit } from '@/api/basedata/customer'
import { listAllPaymentTerm } from '@/api/basedata/payment-term'
import { listAllCurrency } from '@/api/basedata/currency'
import { listAllEmployee } from '@/api/basedata/employee'
import { listAllWarehouse } from '@/api/basedata/warehouse'
import { useCommercialSwitches } from '@/composables/useCommercialSwitches'

const router = useRouter()

// 全站商业口径总开关（设置中心：启用外币业务）→ 关闭时「币种」锁定为本位币
const { foreignCurrencyEnabled, ensureLoaded: ensureCommercialSwitches } = useCommercialSwitches()
// 外币业务关闭时禁用「币种」下拉（BaseEdit 通过 disabledFields 控制控件禁用）
const disabledFields = computed(() => (!foreignCurrencyEnabled.value ? { currencyId: true } : {}))
onMounted(async () => { await ensureCommercialSwitches() })

// 新建模式初始化：外币业务关闭时，币种默认取本位币
async function onCreateInit(form) {
  if (foreignCurrencyEnabled.value) return
  try {
    const list = await listAllCurrency()
    const arr = Array.isArray(list) ? list : (list?.records || [])
    const base = arr.find(c => c.isBaseCurrency === 1 || c.isBaseCurrency === true) || arr[0]
    if (base && !form.currencyId) form.currencyId = base.currencyId
  } catch (e) {
    console.warn('[CustomerEdit] 加载币种失败:', e)
  }
}

/** 信用额度/授信信息（编辑态加载，只读展示，不参与保存） */
const creditInfo = ref(null)

const optionLoaders = {
  paymentTermId: async () => {
    const list = await listAllPaymentTerm()
    const arr = Array.isArray(list) ? list : (list?.records || [])
    return arr.map(p => ({ value: p.paymentTermId, label: `${p.paymentTermCode} - ${p.paymentTermName}` }))
  },
  currencyId: async () => {
    const list = await listAllCurrency()
    const arr = Array.isArray(list) ? list : (list?.records || [])
    return arr.map(c => ({ value: c.currencyId, label: `${c.currencyCode} - ${c.currencyName}` }))
  },
  defaultSalerId: async () => {
    const list = await listAllEmployee()
    const arr = Array.isArray(list) ? list : (list?.records || [])
    return arr.map(e => ({ value: e.employeeId, label: `${e.employeeCode} - ${e.employeeName}` }))
  },
  defaultDeliveryWarehouseId: async () => {
    const list = await listAllWarehouse()
    const arr = Array.isArray(list) ? list : (list?.records || [])
    return arr.map(w => ({ value: w.stockId, label: `${w.stockCode ? w.stockCode + ' - ' : ''}${w.stockName}` }))
  }
}

const sections = [
  {
    title: '基本信息',
    subtitle: '客户的标识和分类',
    fields: [
      { prop: 'customerCode', label: '客户编码', placeholder: '如 C001' },
      { prop: 'customerName', label: '客户名称', required: true, placeholder: '如 XX科技有限公司' },
      { prop: 'shortName', label: '简称', placeholder: '客户简称' },
      { prop: 'customerType', label: '客户类型', type: 'select', default: '企业客户', options: [
        { value: '企业客户', label: '企业客户' },
        { value: '个人客户', label: '个人客户' }
      ] },
      { prop: 'customerLevel', label: '客户等级', type: 'select', default: '普通客户', options: [
        { value: '普通客户', label: '普通客户' },
        { value: 'VIP客户', label: 'VIP客户' },
        { value: '战略客户', label: '战略客户' }
      ] }
    ]
  },
  {
    title: '联系方式',
    subtitle: '客户的联系信息',
    fields: [
      { prop: 'contactPerson', label: '联系人', placeholder: '联系人' },
      { prop: 'contactPhone', label: '联系电话', placeholder: '联系电话' },
      { prop: 'email', label: '邮箱', placeholder: '邮箱' },
      { prop: 'address', label: '地址', type: 'textarea', rows: 2, placeholder: '地址' }
    ]
  },
  {
    title: '财务信息',
    subtitle: '客户的财务资料',
    fields: [
      { prop: 'taxNo', label: '税号', placeholder: '税号' },
      { prop: 'bankName', label: '开户行', placeholder: '开户行' },
      { prop: 'bankAccount', label: '银行账号', placeholder: '银行账号' },
      { prop: 'creditLimit', label: '信用额度', type: 'number', precision: 2, default: 0 },
      { prop: 'taxRate', label: '默认税率', type: 'number', precision: 2, placeholder: '如 13' },
      { prop: 'paymentCategory', label: '付款管控分类', type: 'select', default: '现结', options: [
        { value: '现结', label: '现结' },
        { value: '月结', label: '月结' }
      ] }
    ]
  },
  {
    title: '关联与状态',
    subtitle: '关联档案和状态',
    fields: [
      { prop: 'paymentTermId', label: '付款条件', type: 'custom', component: PaymentTermSelect, placeholder: '请选择付款条件（输入新名称后回车即可新增）' },
      { prop: 'creditDays', label: '账期天数', type: 'number', default: 0, tip: '应收到期日 = 基准日 + 账期天数（单据上另有付款条件时以付款条件为准）' },
      { prop: 'currencyId', label: '币种', type: 'select', filterable: true, placeholder: '请选择币种' },
      { prop: 'defaultSalerId', label: '默认业务员', type: 'select', filterable: true, placeholder: '请选择业务员' },
      { prop: 'defaultDeliveryWarehouseId', label: '默认发货仓', type: 'select', filterable: true, placeholder: '请选择默认发货仓' },
      { prop: 'status', label: '状态', type: 'select', required: true, default: '正常', options: [
        { value: '正常', label: '正常' }, { value: '停用', label: '停用' }
      ] },
      { prop: 'remark', label: '备注', type: 'textarea', rows: 2, placeholder: '请输入备注' }
    ]
  }
]

async function fetchData(id) {
  // 编辑态：加载授信/额度信息（只读条展示，不走保存载荷）
  if (id !== undefined && id !== null) {
    loadCredit(id)
  } else {
    creditInfo.value = null
  }
  return await getCustomer(id)
}

async function loadCredit(id) {
  try {
    const res = await getCustomerCredit(id)
    const d = Array.isArray(res) ? res[0] : (res || {})
    creditInfo.value = {
      creditLimit: Number(d.creditLimit ?? 0),
      usedCredit: Number(d.usedCredit ?? 0),
      availableCredit: Number(d.availableCredit ?? 0),
      creditApprovalStatus: d.creditApprovalStatus || '未申请',
      creditApprovedBy: d.creditApprovedBy || '',
      creditApprovedAt: d.creditApprovedAt || ''
    }
  } catch (e) {
    console.warn('[CustomerEdit] 加载客户授信/额度信息失败', e)
    creditInfo.value = null
  }
}

/** 是否已超额（额度>0 且 剩余额度<=0） */
const creditOver = computed(() => {
  const info = creditInfo.value
  if (!info) return false
  const limit = info.creditLimit || 0
  return limit > 0 && info.availableCredit <= 0
})

/** 授信状态标签色：未申请=灰，审批中=黄，已通过=绿，已驳回=红，已失效=灰 */
function creditStatusType(status) {
  return { '未申请': 'info', '审批中': 'warning', '已通过': 'success', '已驳回': 'danger', '已失效': 'info' }[status] || 'info'
}

function fmtMoney(v) {
  if (v === null || v === undefined || v === '') return '0.00'
  const n = Number(v)
  if (Number.isNaN(n)) return '0.00'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function onSave(data, isEdit) {
  // 账期天数清空即视为 0（0 与"无账期"在到期日计算里等价）：否则清空后后端忽略空值，值改不掉
  if (data) data.creditDays = Number(data.creditDays) || 0
  if (isEdit) await updateCustomer(data)
  else await saveCustomer(data)
  router.push('/basedata/customer')
}
</script>

<style scoped lang="scss">
.credit-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: #606266;
  flex-wrap: wrap;
  .credit-item {
    white-space: nowrap;
    b { color: #303133; font-weight: 600; }
    &.credit-over b { color: #f56c6c; }
  }
  .credit-warn { color: #f56c6c; font-size: 12px; margin-left: 2px; }
}
</style>
