<template>
  <BaseEdit
    :title="$tt('银行账户')"
    :sections="sections"
    :fetch-data="fetchData"
    :on-save="onSave"
    :option-loaders="optionLoaders"
    :disabled-fields="disabledFields"
    :on-create-init="onCreateInit"
    id-field="accountId"
    list-route="/basedata/bank-account"
    auto-doc-type="BANK_ACCOUNT"
    auto-doc-code-field="accountNo"
    :show-change-record="true"
    change-record-bill-type="银行账户"
    change-record-bill-field="accountNo"
    page-key="bank_account"
  />
</template>

<script setup>
import BaseEdit from '@/components/BaseEdit.vue'
import { computed, onMounted } from 'vue'
import { getBankAccount, saveBankAccount, updateBankAccount } from '@/api/basedata/bankAccount'
import { listAllCurrency } from '@/api/basedata/currency'
import { useCommercialSwitches } from '@/composables/useCommercialSwitches'

// 全站商业口径总开关（设置中心：启用外币业务）→ 关闭时「币种」锁定为本位币
const { foreignCurrencyEnabled, ensureLoaded: ensureCommercialSwitches } = useCommercialSwitches()
// 外币业务关闭时禁用「币种」下拉（BaseEdit 通过 disabledFields 控制控件禁用）
const disabledFields = computed(() => (!foreignCurrencyEnabled.value ? { currencyId: true } : {}))
onMounted(async () => { await ensureCommercialSwitches() })

const optionLoaders = {
  currencyId: async () => {
    const list = await listAllCurrency()
    const arr = Array.isArray(list) ? list : (list?.records || [])
    return arr.map(c => ({ value: c.currencyId, label: `${c.currencyName} (${c.currencyCode})` }))
  }
}

const sections = [
  {
    title: '基本信息',
    subtitle: '账户的标识和开户信息',
    fields: [
      { prop: 'accountNo', label: '账户编码', required: true, placeholder: '自动生成' },
      { prop: 'accountName', label: '账户名称', required: true, placeholder: '如 工商银行基本户' },
      { prop: 'bankName', label: '开户银行', required: true, placeholder: '如 中国工商银行深圳分行' },
      { prop: 'accountType', label: '账户类型', type: 'select', required: true, default: '基本户', options: [
        { value: '基本户', label: '基本户' },
        { value: '一般户', label: '一般户' },
        { value: '专用户', label: '专用户' },
        { value: '临时户', label: '临时户' },
        { value: '现金账户', label: '现金账户' },
        { value: '支付宝', label: '支付宝' },
        { value: '微信', label: '微信' }
      ] }
    ]
  },
  {
    title: '币种与余额',
    subtitle: '账户币种和余额信息',
    fields: [
      { prop: 'currencyId', label: '币种', type: 'select', required: true, filterable: true, placeholder: '选择币种' },
      { prop: 'openingBalance', label: '期初余额', type: 'number', default: 0, precision: 2 },
      { prop: 'currentBalance', label: '当前余额', type: 'number', default: 0, precision: 2 },
      { prop: 'isDefault', label: '默认账户', type: 'switch', activeValue: 1, inactiveValue: 0, default: 0 }
    ]
  },
  {
    title: '状态与备注',
    subtitle: '账户的管理状态',
    fields: [
      { prop: 'status', label: '状态', type: 'select', required: true, default: '启用', options: [
        { value: '启用', label: '启用' }, { value: '停用', label: '停用' }
      ] },
      { prop: 'remark', label: '备注', type: 'textarea', rows: 2, placeholder: '请输入备注' }
    ]
  }
]

// 新建模式初始化：默认选中本位币
async function onCreateInit(form) {
  try {
    const list = await listAllCurrency()
    const arr = Array.isArray(list) ? list : (list?.records || [])
    if (arr.length > 0 && !form.currencyId) {
      const base = arr.find(c => c.isBaseCurrency === 1) || arr[0]
      form.currencyId = base.currencyId
    }
  } catch (e) {
    console.warn('[BankAccountEdit] 加载币种失败:', e)
  }
}

async function fetchData(id) {
  return await getBankAccount(id)
}
async function onSave(data, isEdit) {
  if (isEdit) await updateBankAccount(data)
  else await saveBankAccount(data)
}
</script>
