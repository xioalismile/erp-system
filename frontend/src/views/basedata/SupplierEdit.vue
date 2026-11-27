<template>
  <BaseEdit
    :title="$tt('供应商档案')"
    :sections="sections"
    :fetch-data="fetchData"
    :on-save="onSave"
    :option-loaders="optionLoaders"
    :disabled-fields="disabledFields"
    :on-create-init="onCreateInit"
    id-field="supplierId"
    list-route="/basedata/supplier"
    custom-field-entity="supplier"
    :show-change-record="true"
    change-record-bill-type="供应商"
    change-record-bill-field="supplierCode"
    auto-doc-type="MD_SUPPLIER"
    auto-doc-code-field="supplierCode"
    page-key="supplier"
  />
</template>

<script setup>
import BaseEdit from '@/components/BaseEdit.vue'
import PaymentTermSelect from '@/components/PaymentTermSelect.vue'
import { getSupplier, saveSupplier, updateSupplier } from '@/api/basedata/supplier'
import { listAllPaymentTerm } from '@/api/basedata/payment-term'
import { listAllCurrency } from '@/api/basedata/currency'
import { computed, onMounted } from 'vue'
import { listAllEmployee } from '@/api/basedata/employee'
import { useCommercialSwitches } from '@/composables/useCommercialSwitches'

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
    console.warn('[SupplierEdit] 加载币种失败:', e)
  }
}

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
  defaultBuyerId: async () => {
    const list = await listAllEmployee()
    const arr = Array.isArray(list) ? list : (list?.records || [])
    return arr.map(e => ({ value: e.employeeId, label: `${e.employeeCode} - ${e.employeeName}` }))
  }
}

const sections = [
  {
    title: '基本信息',
    subtitle: '供应商的标识和分类',
    fields: [
      { prop: 'supplierCode', label: '供应商编码', placeholder: '如 S001' },
      { prop: 'supplierName', label: '供应商名称', required: true, placeholder: '如 XX材料有限公司' },
      { prop: 'shortName', label: '简称', placeholder: '供应商简称' },
      { prop: 'supplierType', label: '供应商类型', type: 'select', default: '一般供应商', options: [
        { value: '一般供应商', label: '一般供应商' },
        { value: '战略供应商', label: '战略供应商' }
      ] },
      { prop: 'supplierLevel', label: '供应商等级', type: 'select', default: 'C级', options: [
        { value: 'A级', label: 'A级' },
        { value: 'B级', label: 'B级' },
        { value: 'C级', label: 'C级' }
      ] }
    ]
  },
  {
    title: '联系方式',
    subtitle: '供应商的联系信息',
    fields: [
      { prop: 'contactPerson', label: '联系人', placeholder: '联系人' },
      { prop: 'contactPhone', label: '联系电话', placeholder: '联系电话' },
      { prop: 'email', label: '邮箱', placeholder: '邮箱' },
      { prop: 'address', label: '地址', type: 'textarea', rows: 2, placeholder: '地址' }
    ]
  },
  {
    title: '财务信息',
    subtitle: '供应商的财务资料',
    fields: [
      { prop: 'taxNo', label: '税号', placeholder: '税号' },
      { prop: 'bankName', label: '开户行', placeholder: '开户行' },
      { prop: 'bankAccount', label: '银行账号', placeholder: '银行账号' },
      { prop: 'taxRate', label: '默认税率', type: 'number', precision: 2, placeholder: '如 13' },
      { prop: 'paymentMethod', label: '默认付款方式', type: 'select', placeholder: '请选择默认付款方式', options: [
        { value: '款到发货', label: '款到发货' },
        { value: '月结30天', label: '月结30天' },
        { value: '月结60天', label: '月结60天' },
        { value: '货到付款', label: '货到付款' }
      ] }
    ]
  },
  {
    title: '关联与状态',
    subtitle: '关联档案和状态',
    fields: [
      { prop: 'paymentTermId', label: '付款条件', type: 'custom', component: PaymentTermSelect, placeholder: '请选择付款条件（输入新名称后回车即可新增）' },
      { prop: 'creditDays', label: '账期天数', type: 'number', default: 0, tip: '应付到期日 = 基准日 + 账期天数（单据上另有付款条件时以付款条件为准）' },
      { prop: 'currencyId', label: '币种', type: 'select', filterable: true, placeholder: '请选择币种' },
      { prop: 'defaultBuyerId', label: '默认采购员', type: 'select', filterable: true, placeholder: '请选择采购员' },
      { prop: 'deliveryDays', label: '交货天数', type: 'number', default: 0 },
      { prop: 'status', label: '状态', type: 'select', required: true, default: '正常', options: [
        { value: '正常', label: '正常' }, { value: '停用', label: '停用' }
      ] },
      { prop: 'remark', label: '备注', type: 'textarea', rows: 2, placeholder: '请输入备注' }
    ]
  }
]

async function fetchData(id) {
  return await getSupplier(id)
}
async function onSave(data, isEdit) {
  // 账期天数清空即视为 0（0 与"无账期"在到期日计算里等价）：否则清空后后端忽略空值，值改不掉
  if (data) data.creditDays = Number(data.creditDays) || 0
  if (isEdit) await updateSupplier(data)
  else await saveSupplier(data)
}
</script>
